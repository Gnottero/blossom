package blossom.value;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The lock that protects a mutable value shared between the threads of an app.
 *
 * <p>Containers are plain java collections - a list is an {@code ArrayList}, a map a
 * {@code HashMap} - so two threads mutating the same one can lose entries or corrupt the table.
 *
 * <p>The lock travels with the value, not with the variable name: everything reachable from a shared
 * global is marked by {@link #attach(Value, ValueGuard)}, and every mutation or bulk read of a
 * marked container runs through {@link #guarded}. Unmarked values keep a {@code null} guard, so the
 * common path only pays a null check. A host owns one guard for all of its globals, which makes
 * aliasing ({@code global_a = global_b}) harmless and rules out lock ordering deadlocks.
 *
 * <p><b>Invariant: arbitrary script code is never evaluated while a guard is held.</b> Only the
 * container operation runs inside, so a critical section cannot re-enter the interpreter and take a
 * second guard.
 */
public final class ValueGuard
{
    /**
     * fair, like the locks behind synchronize(): tasks queueing on a hot global are served in
     * arrival order rather than one of them starving
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    /**
     * Runs {@code op} while holding this guard and returns its result.
     */
    public <T> T call(Supplier<T> op)
    {
        lock.lock();
        try
        {
            return op.get();
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Runs {@code op} while holding this guard.
     */
    public void run(Runnable op)
    {
        lock.lock();
        try
        {
            op.run();
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Runs {@code op} under {@code guard}, or directly when the value is not shared.
     */
    public static <T> T guarded(@Nullable ValueGuard guard, Supplier<T> op)
    {
        return guard == null ? op.get() : guard.call(op);
    }

    /**
     * Runs {@code op} under {@code guard}, or directly when the value is not shared.
     */
    public static void guardedRun(@Nullable ValueGuard guard, Runnable op)
    {
        if (guard == null)
        {
            op.run();
        }
        else
        {
            guard.run(op);
        }
    }

    /**
     * Marks {@code root} and everything mutable reachable from it as protected by {@code guard}.
     *
     * <p>Called when a value is stored into a shared global, and when an element is added to an
     * already guarded container, so that a structure nested inside a shared global is guarded as
     * well as the global itself.
     *
     * <p>Structures may be cyclic - {@code global_l += global_l} is legal - hence the
     * identity based visited set. A container that already carries this very guard is left alone
     * together with its contents: it was walked when it got the guard, and anything added to it
     * since was adopted on the way in.
     */
    public static void attach(Value root, ValueGuard guard)
    {
        if (!(root instanceof Guardable))
        {
            return;
        }
        Set<Value> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Value> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty())
        {
            Value value = pending.pop();
            if (!(value instanceof final Guardable guardable) || !visited.add(value))
            {
                continue;
            }
            ValueGuard previous = guardable.guard();
            if (previous == guard)
            {
                continue; // already part of this guarded graph, and so are its elements
            }
            guardable.setGuard(guard);
            // the children are read under whatever guard the value had before, in case another
            // thread is mutating it through an alias while it gets adopted
            guardedRun(previous, () -> pending.addAll(childrenOf(value)));
        }
    }

    private static List<Value> childrenOf(Value value)
    {
        if (value instanceof final ListValue list)
        {
            return new ArrayList<>(list.rawItems());
        }
        if (value instanceof final MapValue map)
        {
            List<Value> children = new ArrayList<>(2 * map.rawMap().size());
            map.rawMap().forEach((key, entry) ->
            {
                children.add(key);
                children.add(entry);
            });
            return children;
        }
        return List.of(); // nbt values hold tags, not language values
    }
}
