package blossom.exception;

import blossom.value.Value;

import org.jspecify.annotations.Nullable;

public class BreakStatement extends ExitStatement
{
    public BreakStatement(@Nullable Value value)
    {
        super(value);
    }
}
