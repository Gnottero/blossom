package blossom.exception;

import blossom.value.Value;

import org.jspecify.annotations.Nullable;

/** Exception thrown to terminate execution mid expression (aka return statement). */
public class ExitStatement extends StacklessRuntimeException
{
    @Nullable
    public final Value retval;

    public ExitStatement(@Nullable Value value)
    {
        retval = value;
    }
}
