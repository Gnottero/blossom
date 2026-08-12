package blossom.exception;

import blossom.value.Value;

import org.jspecify.annotations.Nullable;

public class ContinueStatement extends ExitStatement
{
    public ContinueStatement(@Nullable Value value)
    {
        super(value);
    }
}
