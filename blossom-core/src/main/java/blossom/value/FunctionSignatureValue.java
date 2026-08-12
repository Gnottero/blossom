package blossom.value;

import java.util.List;

public class FunctionSignatureValue extends FrameworkValue
{
    private final String identifier;
    private final List<String> arguments;
    private final List<String> globals;
    private final String varArgs;
    /**
     * il tipo canonico dichiarato per ogni parametro, {@code null} dove non e' stato dichiarato.
     * Stessa lunghezza di {@link #arguments}, cosi' il controllo a runtime e' un indice e non una
     * ricerca per nome
     */
    private final List<String> argumentTypes;

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

    /** @return i tipi dichiarati, o {@code null} se nessun parametro ne ha uno */
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
