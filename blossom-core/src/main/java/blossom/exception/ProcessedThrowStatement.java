package blossom.exception;

import java.util.List;

import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Token;
import blossom.value.FunctionValue;
import blossom.value.Value;

public class ProcessedThrowStatement extends ExpressionException
{
    public final Throwables thrownExceptionType;
    public final Value data;

    public ProcessedThrowStatement(Context c, Expression e, Token token, List<FunctionValue> stack, Throwables thrownExceptionType, Value data)
    {
        super(c, e, token, () -> "Unhandled " + thrownExceptionType.getId() + " exception: " + data.getString(), stack);
        this.thrownExceptionType = thrownExceptionType;
        this.data = data;
    }
}
