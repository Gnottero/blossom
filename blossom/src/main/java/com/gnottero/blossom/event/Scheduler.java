package com.gnottero.blossom.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import com.gnottero.blossom.ScriptContext;
import blossom.value.FunctionValue;
import blossom.value.Value;

/**
 * Le chiamate rimandate a un tick futuro ({@code schedule}).
 *
 * <p>La coda viene guardata una volta per tick e i tempi sono in tick, non in millisecondi: uno
 * script che aspetta "venti tick" aspetta lo stesso tempo di gioco anche se il server rallenta, che
 * e' quasi sempre quello che chi scrive intende.
 */
public final class Scheduler
{
    private record Call(long dueTick, String app, Interpreter interpreter, CommandSourceStack source,
            FunctionValue function, List<Value> arguments)
    {
    }

    private final List<Call> queue = new ArrayList<>();
    private long tick = 0;

    /** Mette in coda {@code function} fra {@code delay} tick. */
    public synchronized void schedule(String app, Interpreter interpreter, CommandSourceStack source,
            FunctionValue function, List<Value> arguments, long delay)
    {
        queue.add(new Call(tick + Math.max(0, delay), app, interpreter, source, function, List.copyOf(arguments)));
    }

    /** Toglie tutto quello che un'app aveva in coda: si chiama a ricaricamento e a scaricamento. */
    public synchronized void unregisterApp(String app)
    {
        queue.removeIf(call -> call.app().equals(app));
    }

    public synchronized void clear()
    {
        queue.clear();
    }

    /** Chiamata una volta per tick dal server: esegue quello che era in scadenza. */
    public void tick()
    {
        List<Call> due;
        synchronized (this)
        {
            tick++;
            if (queue.isEmpty())
            {
                return;
            }
            due = new ArrayList<>();
            queue.removeIf(call ->
            {
                if (call.dueTick() <= tick)
                {
                    due.add(call);
                    return true;
                }
                return false;
            });
        }
        for (Call call : due)
        {
            try
            {
                Context context = new ScriptContext(call.interpreter().host(), call.source());
                call.function().callInContext(context, Context.Type.NONE, call.arguments()).evalValue(context);
            }
            catch (RuntimeException | StackOverflowError error)
            {
                com.gnottero.blossom.util.AppErrors.report(call.app(), "a scheduled call", error);
            }
        }
    }
}
