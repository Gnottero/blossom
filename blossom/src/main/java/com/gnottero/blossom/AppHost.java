package com.gnottero.blossom;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import blossom.value.FunctionValue;
import blossom.value.Value;
import net.minecraft.nbt.Tag;
import com.google.gson.JsonElement;
import java.util.stream.Stream;
import com.gnottero.blossom.argument.FileArgument;

/**
 * Un'app caricata, vista da chi deve richiamarla da fuori: eventi, schermate, callback.
 *
 * <p>In Scarpet la stessa cosa era un host di script con dentro anche il caricamento, i permessi e i
 * comandi. Qui e' solo il minimo che serve per chiamare una funzione dentro un'app sapendo chi l'ha
 * chiesto - il resto lo fa {@link ScriptRuntime}.
 */
public record AppHost(String name, Interpreter interpreter)
{
    /** @return l'app caricata con quel nome, o {@code null} se non e' caricata */
    public static @Nullable AppHost byName(String name)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (runtime == null)
        {
            return null;
        }
        Interpreter interpreter = runtime.interpreterOf(name);
        return interpreter == null ? null : new AppHost(name, interpreter);
    }

    /**
     * L'app a cui appartiene il codice in esecuzione.
     *
     * @throws blossom.exception.TypeMismatchError se il codice non gira dentro un'app caricata:
     *         succede a chi prova a usare i file o gli eventi da {@code /blossom script run}
     */
    public static AppHost of(blossom.interpreter.Context context)
    {
        String name = context.host.getName();
        AppHost host = name == null ? null : byName(name);
        if (host == null)
        {
            throw new blossom.exception.TypeMismatchError(blossom.exception.SourcePosition.UNKNOWN,
                    "This function needs a loaded app: run it from a script loaded with "
                            + "'/blossom script load', not from a one-off expression.");
        }
        return host;
    }

    /** L'host del core sotto questa app: da qui passano storage e moduli. */
    public blossom.interpreter.ScriptHost host()
    {
        return interpreter.host();
    }

    /** Il modulo principale dell'app: e' lui a dare il nome alla cartella dei dati. */
    public blossom.interpreter.Module main()
    {
        return interpreter.host().main;
    }

    // --- file dell'app ---
    // Ogni app scrive sotto <mondo>/blossom/<app>.data/, o sotto shared/ se il file e' condiviso:
    // due app non si vedono i dati per sbaglio.

    @Nullable
    public Tag readFileTag(FileArgument file)
    {
        return file.getNbtData(main());
    }

    public boolean writeTagFile(Tag tag, FileArgument file)
    {
        return file.saveNbtData(main(), tag);
    }

    public boolean removeResourceFile(FileArgument file)
    {
        return file.dropExistingFile(main());
    }

    public boolean appendLogFile(FileArgument file, List<String> lines)
    {
        return file.appendToTextFile(main(), lines);
    }

    @Nullable
    public List<String> readTextResource(FileArgument file)
    {
        return file.listFile(main());
    }

    @Nullable
    public JsonElement readJsonFile(FileArgument file)
    {
        return file.readJsonFile(main());
    }

    @Nullable
    public Stream<String> listFolder(FileArgument file)
    {
        return file.listFolder(main());
    }

    public boolean applyActionForResource(String path, boolean shared, java.util.function.Consumer<java.nio.file.Path> action)
    {
        FileArgument file = FileArgument.resourceFromPath(
                host(), path, FileArgument.Reason.CREATE, shared);
        return file.findPathAndApply(main(), action);
    }

    /**
     * Chiama una funzione dell'app.
     *
     * <p>Un errore dentro la funzione viene riportato e non risale al gioco: chi chiama e' quasi
     * sempre un mixin dentro il tick loop, e un'app rotta non deve fermare il server.
     *
     * @return il valore restituito, o {@code null} se la chiamata e' fallita
     */
    public @Nullable Value call(CommandSourceStack source, FunctionValue function, List<Value> arguments)
    {
        try
        {
            // ogni callback e' un'esecuzione a se': il watchdog riparte, altrimenti un'app viva da
            // ore avrebbe finito il budget al primo giorno
            Context context = new ScriptContext(interpreter.host(), source).beginExecution();
            return function.callInContext(context, Context.Type.NONE, arguments).evalValue(context);
        }
        catch (RuntimeException | StackOverflowError error)
        {
            com.gnottero.blossom.util.AppErrors.report(name, "a callback", error);
            return null;
        }
    }
}
