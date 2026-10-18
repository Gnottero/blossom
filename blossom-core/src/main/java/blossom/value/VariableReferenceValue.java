package blossom.value;

import org.jspecify.annotations.Nullable;

/**
 * A reference to a variable: the name on the left of an assignment, and the value it currently
 * holds.
 *
 * <p>It's the l-value of variables, the way {@link LContainerValue} is the l-value of a container's
 * elements. It's only produced when a name is evaluated in {@link
 * blossom.interpreter.Context#LVALUE} context, where something is about to be assigned to it, or in
 * {@link blossom.interpreter.Context#LOCALIZATION}, i.e. inside a signature, where an identifier is
 * the name of a parameter and not a variable to read.
 *
 * <p>The name used to live in a mutable field on every {@link Value} ({@code boundVariable}):
 * l-value information stuck onto the r-value, which forced every value entering a variable to be
 * copied so the caller wouldn't see it get renamed out from under it. The name belongs to the node
 * that reads the variable instead, fixed from parsing and never changing, so that node is the one
 * that reports it, and only when needed.
 */
public final class VariableReferenceValue extends FrameworkValue
{
    private final String name;
    private final @Nullable Value current;

    /** A reference inside a signature: there, the name doesn't have any value behind it yet. */
    public VariableReferenceValue(String name)
    {
        this(name, null);
    }

    public VariableReferenceValue(String name, @Nullable Value current)
    {
        this.name = name;
        this.current = current;
    }

    public String name()
    {
        return name;
    }

    /** The value the variable currently holds, or {@code null} inside a signature. */
    public @Nullable Value current()
    {
        return current;
    }

    /**
     * The value behind {@code value} if it's a reference, {@code value} itself otherwise.
     *
     * <p>Used by code that evaluates its argument in l-value context in order to write to it
     * ({@code put}, {@code get}, {@code delete}) and ends up with a name when the argument was a
     * variable rather than an access to an element.
     */
    public static Value dereference(Value value)
    {
        if (value instanceof final VariableReferenceValue reference && reference.current != null)
        {
            return reference.current;
        }
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
        return name;
    }

    @Override
    public String getTypeString()
    {
        return "variable";
    }
}
