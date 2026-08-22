package com.gnottero.blossom.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import blossom.exception.BlossomError;
import blossom.exception.ExpressionException;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptRuntime;
import blossom.value.Value;

/**
 * The tree of the {@code /blossom} command.
 *
 * <p>Phase 2: the minimum needed for a pipeline that goes from the command to the evaluator and back
 * with a result, that is {@code script run}, {@code script load}, {@code script reload},
 * {@code script list} and {@code script unload}. The rest (profiling, multi file projects) arrives
 * with the phases that make it meaningful.
 */
public final class BlossomCommand
{
    private static final SuggestionProvider<CommandSourceStack> LOADED_APPS = (context, builder) ->
            SharedSuggestionProvider.suggest(withRuntime(context.getSource(), ScriptRuntime::loadedApps), builder);

    private static final SuggestionProvider<CommandSourceStack> AVAILABLE_APPS = (context, builder) ->
            SharedSuggestionProvider.suggest(withRuntime(context.getSource(), ScriptRuntime::availableApps), builder);

    private BlossomCommand()
    {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("blossom")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("script")
                        .then(Commands.literal("run")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> run(context, null))))
                        .then(Commands.literal("in")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                                .executes(context -> run(context, StringArgumentType.getString(context, "app"))))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::load)))
                        .then(Commands.literal("reload")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::load)))
                        .then(Commands.literal("unload")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::unload)))
                        .then(Commands.literal("list")
                                .executes(BlossomCommand::list)))
                .then(Commands.literal("pack")
                        .then(Commands.literal("update")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::packUpdate))))
                .then(Commands.literal("data")
                        .then(Commands.literal("generate")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::dataGenerate)))
                        .then(Commands.literal("list")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::dataList)))
                        .then(Commands.literal("export")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::dataExport))))
                .then(Commands.literal("profile")
                        .then(Commands.literal("run")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> profileRun(context, null))))
                        .then(Commands.literal("in")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                                .executes(context -> profileRun(context, StringArgumentType.getString(context, "app"))))))
                        .then(Commands.literal("start")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::profileStart)))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::profileStop)))));
    }

    /**
     * Resolves the dependency versions again and reloads the pack.
     *
     * <p>It is the only way to make {@code blossom.lock} change its mind: without it a constraint
     * like {@code ^1.0.0} is resolved once and the choice stays, which is exactly why the lock
     * exists.
     */
    /**
     * Generates the game content of an app, by calling its {@code generate()}.
     *
     * <p>A convention instead of a manifest field: the generator is ordinary app code, and all the
     * command needs to know is where to start. An app without {@code generate()} says so, instead of
     * silently doing nothing.
     */
    private static int dataGenerate(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        java.nio.file.Path root = runtime.scriptDirectory().resolve(app);
        if (!java.nio.file.Files.isDirectory(root))
        {
            context.getSource().sendFailure(Component.literal(
                    "'" + app + "' is a single file, and content lives in a pack's data/ folder."));
            return 0;
        }
        try
        {
            com.gnottero.blossom.content.Generators.Report report =
                    com.gnottero.blossom.content.Generators.generate(root,
                            () -> runtime.runIn(context.getSource(), app, "generate()"));
            context.getSource().sendSuccess(() -> Component.literal(
                    report.written() + " resources written in data/"
                            + (report.removed() > 0 ? ", " + report.removed() + " stale ones removed" : "")
                            + ". Run /reload to apply them; a new registry entry or worldgen needs a restart."), true);
            return report.written();
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (Exception e)
        {
            return fail(context, "Could not generate the content of '" + app + "': " + e.getMessage());
        }
    }

    /** What is in the data/ of a pack, and who wrote it. */
    private static int dataList(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            java.util.List<com.gnottero.blossom.content.ContentExport.Entry> entries =
                    com.gnottero.blossom.content.ContentExport.list(runtime.scriptDirectory().resolve(app));
            if (entries.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.literal(
                        "'" + app + "' carries no game content: it has no data/ folder."), false);
                return 0;
            }
            long generated = entries.stream().filter(
                    com.gnottero.blossom.content.ContentExport.Entry::generated).count();
            context.getSource().sendSuccess(() -> Component.literal(
                    entries.size() + " files in the data/ of '" + app + "', "
                            + generated + " of them generated:"), false);
            for (com.gnottero.blossom.content.ContentExport.Entry entry : entries)
            {
                // the reader has to tell at a glance what the next generation will rewrite and
                // what they wrote themselves
                context.getSource().sendSuccess(() -> Component.literal(
                        "  " + (entry.generated() ? "generated  " : "by hand    ") + entry.path()), false);
            }
            return entries.size();
        }
        catch (Exception e)
        {
            return fail(context, "Could not read the content of '" + app + "': " + e.getMessage());
        }
    }

    /** The zip of the content, for whoever does not have the mod. */
    private static int dataExport(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            java.nio.file.Path zip =
                    com.gnottero.blossom.content.ContentExport.export(runtime.scriptDirectory(), app);
            java.nio.file.Path shown = runtime.scriptDirectory().getParent().relativize(zip);
            context.getSource().sendSuccess(() -> Component.literal(
                    "The content of '" + app + "' is a datapack in " + shown
                            + ": it works on a server without Blossom."), true);
            return 1;
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int packUpdate(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.update(context.getSource(), app);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Dependencies of '" + app + "' re-resolved and " + blossom.project.LockFile.FILE_NAME + " updated"), true);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    // --- profiling ---

    private static int profileRun(CommandContext<CommandSourceStack> context, String app)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String code = StringArgumentType.getString(context, "code");
        try
        {
            blossom.interpreter.Profiler profiler = runtime.profileRun(context.getSource(), app, code);
            String what = app == null ? "an expression" : "'" + app + "'";
            context.getSource().sendSuccess(() -> Component.literal(ProfileReport.format(profiler, what)), false);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int profileStart(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.startProfiling(app);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Profiling started on '" + app + "'. From now on it counts everything the app does, events"
                            + " included. Close it with /blossom profile stop " + app), false);
            return 1;
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int profileStop(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            blossom.interpreter.Profiler profiler = runtime.stopProfiling(app);
            context.getSource().sendSuccess(() -> Component.literal(ProfileReport.format(profiler, "'" + app + "'")), false);
            return 1;
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int run(CommandContext<CommandSourceStack> context, String app)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String code = StringArgumentType.getString(context, "code");
        try
        {
            Value result = app == null
                    ? runtime.runOneShot(context.getSource(), code)
                    : runtime.runIn(context.getSource(), app, code);
            context.getSource().sendSuccess(() -> Component.literal(" -> " + result.getPrettyString()), false);
            return result.getBoolean() ? 1 : 0;
        }
        catch (BlossomError e)
        {
            // format() carries category, position, cross module stack and suggestion: that is the
            // whole point of typed errors, and in chat it matters more than anywhere else
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (IllegalArgumentException | ArithmeticException e)
        {
            return fail(context, e.getMessage());
        }
        catch (StackOverflowError e)
        {
            return fail(context, "the script went too deep");
        }
    }

    private static int load(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.load(context.getSource(), app);
            context.getSource().sendSuccess(() -> Component.literal("Loaded app '" + app + "'"), true);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static int unload(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        if (!runtime.unload(app))
        {
            return fail(context, "App '" + app + "' is not loaded");
        }
        context.getSource().sendSuccess(() -> Component.literal("Unloaded app '" + app + "'"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        List<String> loaded = runtime.loadedApps();
        List<String> available = runtime.availableApps();
        context.getSource().sendSuccess(() -> Component.literal(
                "Loaded: " + (loaded.isEmpty() ? "none" : String.join(", ", loaded))
                        + "\nOn disk: " + (available.isEmpty() ? "none" : String.join(", ", available))), false);
        return loaded.size();
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message)
    {
        context.getSource().sendFailure(Component.literal(message == null ? "unknown error" : message));
        return 0;
    }

    private static ScriptRuntime requireRuntime(CommandSourceStack source)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (runtime == null)
        {
            throw new IllegalStateException("Blossom is not ready on this server yet");
        }
        return runtime;
    }

    private static <T> List<T> withRuntime(CommandSourceStack source, java.util.function.Function<ScriptRuntime, List<T>> query)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        return runtime == null ? List.of() : query.apply(runtime);
    }
}
