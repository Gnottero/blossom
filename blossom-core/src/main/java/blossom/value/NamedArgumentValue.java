package blossom.value;

import blossom.interpreter.LazyValue;

/**
 * A name attached to a value inside an argument list: {@code f(pos, update = false)}.
 *
 * <p>It is produced at parse time, an {@code =} with a name on its left among the arguments of a
 * call, and is read in two different places depending on where it ends up:
 *
 * <ul>
 *   <li>in a <b>signature</b>, {@code fn place(pos, update = true)}, it says that parameter has a
 *       fallback value for when the caller doesn't pass it;
 *   <li>in a <b>call</b>, {@code place(p, update = false)}, it says which parameter the argument
 *       goes to, regardless of order.
 * </ul>
 *
 * <p>The value stays lazy on purpose: a parameter's default must be evaluated on every call that
 * uses it, otherwise {@code fn f(x = {})} would hand everyone the same map.
 */
public final class NamedArgumentValue extends FrameworkValue
{
    private final String name;
    private final LazyValue value;

    public NamedArgumentValue(String name, LazyValue value)
    {
        this.name = name;
        this.value = value;
    }

    public String name()
    {
        return name;
    }

    public LazyValue value()
    {
        return value;
    }

    @Override
    public String getString()
    {
        return name;
    }

    @Override
    public String getPrettyString()
    {
        return name + " = ...";
    }

    @Override
    public String getTypeString()
    {
        return "named_argument";
    }
}
