package com.gnottero.blossom;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import blossom.value.FunctionValue;
import blossom.value.Value;

/**
 * A loaded app, seen from code that needs to call back into it from outside: events, screens,
 * callbacks.
 *
 * <p>This is just the minimum needed to call a function inside an app while knowing who asked for
 * it: loading, permissions, and commands live in {@link ScriptRuntime}, which is a separate object
 * on purpose.
 */
public record AppHost(String name, Interpreter interpreter)
{
    /** @return the app loaded under that name, or {@code null} if it is not loaded */
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
     * The app that the currently running code belongs to.
     *
     * @throws blossom.exception.TypeMismatchError if the code is not running inside a loaded app:
     *         this happens when trying to use files or events from {@code /blossom script run}
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

    /** The core host beneath this app: storage and modules go through here. */
    public blossom.interpreter.ScriptHost host()
    {
        return interpreter.host();
    }

    /** The app's main module. */
    public blossom.interpreter.Module main()
    {
        return interpreter.host().main;
    }

    /**
     * Calls a function of the app.
     *
     * <p>An error inside the function is reported and does not propagate back to the game: the
     * caller is almost always a mixin inside the tick loop, and a broken app must not stop the
     * server.
     *
     * @return the returned value, or {@code null} if the call failed
     */
    public @Nullable Value call(CommandSourceStack source, FunctionValue function, List<Value> arguments)
    {
        try
        {
            // each callback is its own execution: the watchdog restarts, otherwise an app that has
            // been alive for hours would run out of budget on the first day
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
