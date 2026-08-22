package blossom.language;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.value.BooleanValue;
import blossom.value.ListValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * {@code core.io}: the disk, inside declared boundaries.
 *
 * <p>Every function starts with the name of a root, and the rest of the path is always relative
 * to it: {@code io.read_text('shared', 'note/today.txt')}. Roots are declared by an administrator
 * (see {@link IoRoots}); the game layer adds two that always exist, {@code data} and
 * {@code shared}, and that is where an app keeps its own data.
 *
 * <p>This is the language's only file API: everything that can be opened is here, no exceptions.
 * Three functions ({@code read_nbt}, {@code write_nbt}, {@code create_datapack}) are implemented
 * in the game layer, because NBT is a binary Minecraft format and a datapack is a game concept,
 * but they are imported from here like all the others (see {@code StdLib.extendModule}). There
 * used to also be a {@code core.mc.file} that read and wrote inside a per-app folder,
 * {@code <world>/blossom/<app>.data/}: two ways of opening a file is one extra question on every
 * call, and whoever wants to keep their own data private uses a subfolder with their own name.
 *
 * <p>IO errors are thrown as {@code io_exception}, so they are caught with
 * {@code try(..., 'io_exception', ...)}: a missing file or a full disk are things a script can
 * reasonably want to handle, unlike a boundary violation, which is a programming error and is not
 * caught.
 */
public final class Io
{
    /** The symbols exported by {@code core.io}, declared in the library catalog. */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "roots", "writable", "exists", "is_directory", "list",
            "read_text", "read_lines", "read_json",
            "write_text", "append_text", "write_json",
            "make_directory", "delete_file", "size", "modified");

    private Io()
    {
    }

    public static void apply(Expression expression)
    {
        // which roots exist, and which are writable: a script that wants to be polite can check
        // this before failing
        expression.addContextFunction("roots", 0, (c, t, lv) ->
                ListValue.wrap(roots(c).names().stream().map(StringValue::of)));

        expression.addContextFunction("writable", 1, (c, t, lv) ->
                BooleanValue.of(roots(c).isWritable(lv.get(0).getString())));

        expression.addContextFunction("exists", 2, (c, t, lv) ->
                BooleanValue.of(Files.exists(path(c, lv, false))));

        expression.addContextFunction("is_directory", 2, (c, t, lv) ->
                BooleanValue.of(Files.isDirectory(path(c, lv, false))));

        /*
         * The listing covers a single folder and is not recursive: recursing into a large root
         * would block the tick without whoever wrote it noticing, and anyone who wants to descend
         * does it with one call per level, visible, and interruptible by the watchdog.
         */
        expression.addContextFunction("list", 2, (c, t, lv) ->
        {
            Path directory = path(c, lv, false);
            if (!Files.isDirectory(directory))
            {
                return ListValue.of();
            }
            try (Stream<Path> entries = Files.list(directory))
            {
                List<Value> names = new ArrayList<>();
                entries.map(entry -> entry.getFileName().toString()).sorted()
                        .forEach(name -> names.add(StringValue.of(name)));
                return ListValue.wrap(names);
            }
            catch (IOException e)
            {
                throw failure("list", lv, e);
            }
        });

        expression.addContextFunction("read_text", 2, (c, t, lv) ->
        {
            try
            {
                return StringValue.of(Files.readString(path(c, lv, false), StandardCharsets.UTF_8));
            }
            catch (IOException e)
            {
                throw failure("read", lv, e);
            }
        });

        expression.addContextFunction("read_lines", 2, (c, t, lv) ->
        {
            try
            {
                return ListValue.wrap(Files.readAllLines(path(c, lv, false), StandardCharsets.UTF_8)
                        .stream().map(StringValue::of));
            }
            catch (IOException e)
            {
                throw failure("read", lv, e);
            }
        });

        expression.addContextFunction("read_json", 2, (c, t, lv) ->
        {
            try
            {
                String content = Files.readString(path(c, lv, false), StandardCharsets.UTF_8);
                return blossom.utils.JsonSupport.GSON.fromJson(content, Value.class);
            }
            catch (IOException e)
            {
                throw failure("read", lv, e);
            }
            catch (com.google.gson.JsonParseException e)
            {
                throw new ThrowStatement(StringValue.of(e.getMessage()), Throwables.JSON_ERROR);
            }
        });

        expression.addContextFunction("write_text", 3, (c, t, lv) ->
                write(c, lv, false));

        expression.addContextFunction("append_text", 3, (c, t, lv) ->
                write(c, lv, true));

        expression.addContextFunction("write_json", 3, (c, t, lv) ->
        {
            List<Value> asText = List.of(lv.get(0), lv.get(1), StringValue.of(lv.get(2).toJson().toString()));
            return write(c, asText, false);
        });

        expression.addContextFunction("make_directory", 2, (c, t, lv) ->
        {
            try
            {
                Files.createDirectories(path(c, lv, true));
                return Value.TRUE;
            }
            catch (IOException e)
            {
                throw failure("create", lv, e);
            }
        });

        /*
         * Deletes a file or an empty directory, never a tree. A recursive deletion behind a short
         * name is the easiest way to lose data over a single wrong character in a path: whoever
         * wants that writes it, one level at a time, seeing it happen as they go.
         *
         * <p>It is called {@code delete_file} rather than {@code delete} because built-in
         * implementations live in a flat map (visibility is what goes through modules, not the
         * calls themselves), and {@code delete} is already the name for removing an element from
         * a list or a map. Two implementations under the same name would mean the last one
         * registered wins, silently.
         */
        expression.addContextFunction("delete_file", 2, (c, t, lv) ->
        {
            try
            {
                return BooleanValue.of(Files.deleteIfExists(path(c, lv, true)));
            }
            catch (java.nio.file.DirectoryNotEmptyException e)
            {
                throw new ThrowStatement(StringValue.of("directory not empty: " + lv.get(1).getString()),
                        Throwables.IO_EXCEPTION);
            }
            catch (IOException e)
            {
                throw failure("delete", lv, e);
            }
        });

        expression.addContextFunction("size", 2, (c, t, lv) ->
        {
            Path file = path(c, lv, false);
            try
            {
                return Files.exists(file) ? NumericValue.of(Files.size(file)) : Value.NULL;
            }
            catch (IOException e)
            {
                throw failure("read", lv, e);
            }
        });

        expression.addContextFunction("modified", 2, (c, t, lv) ->
        {
            Path file = path(c, lv, false);
            try
            {
                return Files.exists(file) ? NumericValue.of(Files.getLastModifiedTime(file).toMillis()) : Value.NULL;
            }
            catch (IOException e)
            {
                throw failure("read", lv, e);
            }
        });
    }

    private static Value write(Context c, List<Value> lv, boolean append)
    {
        Path file = path(c, lv, true);
        try
        {
            if (file.getParent() != null)
            {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, lv.get(2).getString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            return Value.TRUE;
        }
        catch (IOException e)
        {
            throw failure("write", lv, e);
        }
    }

    private static IoRoots roots(Context c)
    {
        return c.host.scriptServer().ioRoots();
    }

    private static Path path(Context c, List<Value> lv, boolean forWriting)
    {
        return resolve(c, lv.get(0).getString(), lv.get(1).getString(), forWriting);
    }

    /**
     * The same path resolution used by the functions in this module, for anyone adding one outside it.
     *
     * <p>Used by the game layer: {@code core.mc.file} reads and writes NBT, which is a Minecraft
     * format and cannot live here, but it must respect the same boundaries (same roots, same
     * write permission, same symbolic link checks). Going through here means not rewriting them,
     * and therefore not getting them wrong.
     *
     * @param forWriting whether the operation modifies something: a read-only root fails here
     */
    public static Path resolve(Context c, String root, String relative, boolean forWriting)
    {
        return roots(c).resolve(root, relative, forWriting);
    }

    private static ThrowStatement failure(String what, List<Value> lv, IOException cause)
    {
        return new ThrowStatement(StringValue.of("cannot " + what + " '" + lv.get(1).getString()
                + "' in root '" + lv.get(0).getString() + "': " + cause.getMessage()), Throwables.IO_EXCEPTION);
    }
}
