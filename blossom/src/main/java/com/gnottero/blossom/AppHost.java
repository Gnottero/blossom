package com.gnottero.blossom;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import blossom.value.FunctionValue;
import blossom.value.Value;

/**
 * Un'app caricata, vista da chi deve richiamarla da fuori: eventi, schermate, callback.
 *
 * <p>E' solo il minimo che serve per chiamare una funzione dentro un'app sapendo chi l'ha chiesto:
 * caricamento, permessi e comandi stanno in {@link ScriptRuntime}, che e' un oggetto diverso apposta.
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

    /** Il modulo principale dell'app. */
    public blossom.interpreter.Module main()
    {
        return interpreter.host().main;
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
