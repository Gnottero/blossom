package blossom.value;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractListValue extends Value implements Iterable<Value>
{
    public List<Value> unpack()
    {
        ArrayList<Value> retVal = new ArrayList<>();
        for (Value value : this)
        {
            if (value != Value.EOL)
            {
                retVal.add(value);
            }
        }
        fatality();
        return retVal;
    }

    public void fatality()
    {
    }

    public void append(Value v)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "list", "Cannot append a value to an abstract list");
    }

    @Override
    public Value fromConstant()
    {
        return this.deepcopy();
    }
}
