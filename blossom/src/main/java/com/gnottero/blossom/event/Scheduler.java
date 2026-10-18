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
 * The calls deferred to a future tick ({@code schedule}).
 *
 * <p>The queue is looked at once per tick and the times are in ticks, not milliseconds: a script
 * waiting "twenty ticks" waits the same game time even if the server slows down, which is almost
 * always what the author means.
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

    /** Drops everything an app had queued: called on reload and on unload. */
    public synchronized void unregisterApp(String app)
    {
        queue.removeIf(call -> call.app().equals(app));
    }

    public synchronized void clear()
    {
        queue.clear();
    }

    /** Called once per tick by the server: runs whatever was due. */
    public void tick()
    {
        if (blossom.interpreter.ExecutionBudget.isHalted())
        {
            return; // stopped: the calls wait, and run when the scripts are resumed
        }
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
                // a call of its own, with its own watchdog: without it an endless loop in a
                // scheduled function stopped the server for good
                Context context = new ScriptContext(call.interpreter().host(), call.source()).beginExecution();
                call.function().callInContext(context, Context.Type.NONE, call.arguments()).evalValue(context);
            }
            catch (RuntimeException | StackOverflowError error)
            {
                com.gnottero.blossom.util.AppErrors.report(call.app(), "a scheduled call", error);
            }
        }
    }
}
