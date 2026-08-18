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
 * {@code core.io}: il disco, dentro confini dichiarati.
 *
 * <p>Ogni funzione comincia con il nome di una radice, e il resto del percorso è sempre relativo a
 * quella: {@code io.read_text('shared', 'note/oggi.txt')}. Le radici le dichiara un amministratore
 * (vedi {@link IoRoots}); il layer di gioco ne aggiunge due che ci sono sempre, {@code data} e
 * {@code shared}, ed è lì che un'app tiene i propri dati.
 *
 * <p>È l'unica API di file del linguaggio: qui c'è tutto quello che si può aprire, senza eccezioni.
 * Tre funzioni - {@code read_nbt}, {@code write_nbt}, {@code create_datapack} - sono implementate nel
 * layer di gioco, perché NBT è un formato binario di Minecraft e un datapack è un concetto del gioco,
 * ma si importano da qui come tutte le altre (vedi {@code StdLib.extendModule}). C'era anche un
 * {@code core.mc.file} che leggeva e scriveva dentro una cartella per app,
 * {@code <mondo>/blossom/<app>.data/}: due modi di aprire un file sono una domanda in più ad ogni
 * chiamata, e chi vuole tenere i propri dati per sé usa una sottocartella con il proprio nome.
 *
 * <p>Gli errori di io sono lanciati come {@code io_exception}, quindi si catturano con
 * {@code try(..., 'io_exception', ...)}: un file che non c'è o un disco pieno sono cose che uno
 * script può ragionevolmente voler gestire, al contrario di una violazione dei confini - quella è un
 * errore del programma e non si cattura.
 */
public final class Io
{
    /** i simboli esportati da {@code core.io}, dichiarati al catalogo della libreria */
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
        // quali radici esistono, e quali si possono scrivere: uno script che vuole essere gentile
        // puo' dirlo prima di fallire
        expression.addContextFunction("roots", 0, (c, t, lv) ->
                ListValue.wrap(roots(c).names().stream().map(StringValue::of)));

        expression.addContextFunction("writable", 1, (c, t, lv) ->
                BooleanValue.of(roots(c).isWritable(lv.get(0).getString())));

        expression.addContextFunction("exists", 2, (c, t, lv) ->
                BooleanValue.of(Files.exists(path(c, lv, false))));

        expression.addContextFunction("is_directory", 2, (c, t, lv) ->
                BooleanValue.of(Files.isDirectory(path(c, lv, false))));

        /*
         * L'elenco è di una cartella sola e non ricorsivo: una ricorsione su una radice grande
         * bloccherebbe il tick senza che chi l'ha scritta se ne accorga, e chi vuole scendere lo fa
         * con una chiamata per livello - visibile, e interrompibile dal watchdog.
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
         * Cancella un file o una cartella vuota, mai un albero. Una cancellazione ricorsiva dietro
         * un nome corto è il modo più facile di perdere dati per un percorso sbagliato di un
         * carattere: chi la vuole la scrive, un livello alla volta, e mentre lo fa la vede.
         *
         * <p>Si chiama {@code delete_file} e non {@code delete} perché le implementazioni delle
         * built-in vivono in una mappa piatta - è la visibilità a passare per i moduli, non le
         * chiamate - e {@code delete} è già quello che toglie un elemento da una lista o da una
         * mappa. Due implementazioni sotto lo stesso nome vorrebbe dire che l'ultima registrata
         * vince, silenziosamente.
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
     * La stessa risoluzione che usano le funzioni di questo modulo, per chi ne aggiunge una fuori.
     *
     * <p>Serve al layer di gioco: {@code core.mc.file} legge e scrive NBT, che e' un formato di
     * Minecraft e qui non puo' stare, ma deve rispettare gli stessi confini - stesse radici, stesso
     * permesso di scrittura, stesso controllo sui link simbolici. Passare da qui e' il modo di non
     * riscriverli, e quindi di non sbagliarli.
     *
     * @param forWriting se l'operazione modifica qualcosa: una radice di sola lettura fallisce qui
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
