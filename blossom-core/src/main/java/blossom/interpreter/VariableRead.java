package blossom.interpreter;

/**
 * The compiled node that reads a variable, and can tell its name.
 *
 * <p>The name of a variable belongs to the node and not to the value: it comes from parsing and
 * never changes. Whatever assigns, {@code =}, {@code +=}, {@code <>}, {@code let}, asks here instead
 * of evaluating the left side and reading it off what comes out. That way an assignment does not
 * read the old value to find out what the variable is called, and no value has to carry the name of
 * where it ended up. See DECISIONI.md D-093.
 */
public interface VariableRead extends LazyValue
{
    /** The name of the variable being read, already interned. */
    String variableName();
}
