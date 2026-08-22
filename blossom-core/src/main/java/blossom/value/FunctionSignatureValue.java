package blossom.value;

import java.util.List;

public class FunctionSignatureValue extends FrameworkValue
{
    private final String identifier;
    private final List<String> arguments;
    private final List<String> globals;
    private final String varArgs;
    /**
     * The canonical type declared for each parameter, {@code null} where none was declared.
     * Same length as {@link #arguments}, so the runtime check is an index lookup rather than a
     * search by name.
     */
    private final List<String> argumentTypes;
    /**
     * The fallback value for each parameter, {@code null} where there isn't one. Same length as
     * {@link #arguments}, so at runtime it's an index lookup rather than a search by name.
     */
    private List<blossom.interpreter.LazyValue> defaults = java.util.Collections.emptyList();

    public FunctionSignatureValue(String name, List<String> args, String varArgs, List<String> globals)
    {
        this(name, args, java.util.Collections.nCopies(args.size(), null), varArgs, globals);
    }

    public FunctionSignatureValue(String name, List<String> args, List<String> argumentTypes, String varArgs,
            List<String> globals)
    {
        this.identifier = name;
        this.arguments = args;
        this.argumentTypes = argumentTypes;
        this.varArgs = varArgs;
        this.globals = globals;
    }

    public void withDefaults(List<blossom.interpreter.LazyValue> values)
    {
        this.defaults = values;
    }

    /** @return the declared defaults, or {@code null} if no parameter has one */
    public List<blossom.interpreter.LazyValue> defaults()
    {
        return defaults.stream().anyMatch(java.util.Objects::nonNull) ? defaults : null;
    }

    /** @return the declared types, or {@code null} if no parameter has one */
    public List<String> argumentTypes()
    {
        return argumentTypes.stream().anyMatch(java.util.Objects::nonNull) ? argumentTypes : null;
    }
    public String identifier()
    {
        return identifier;
    }
    public List<String> arguments()
    {
        return arguments;
    }
    public List<String> globals() {return globals;}
    public String varArgs() { return varArgs;}
}
