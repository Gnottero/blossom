package blossom.value;

import org.jspecify.annotations.Nullable;

/**
 * A value whose internal state is mutable, and that can therefore be handed a {@link ValueGuard}
 * when it becomes reachable from more than one thread.
 *
 * <p>Values are unguarded while they are local to a frame, which is the common case, and get a
 * guard attached when they are stored into a {@code global_} variable of an app that shares its
 * globals - see {@link ValueGuard#attach(Value, ValueGuard)}.
 */
public interface Guardable
{
    /**
     * The guard protecting this value, or {@code null} while the value is unshared.
     */
    @Nullable
    ValueGuard guard();

    /**
     * Marks this value as protected by {@code guard}. Only called from
     * {@link ValueGuard#attach(Value, ValueGuard)} and from container mutations that adopt new
     * elements into an already guarded structure.
     */
    void setGuard(ValueGuard guard);
}
