package blossom.language;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.ModuleSymbols;
import blossom.value.FunctionValue;
import blossom.value.VariableReferenceValue;
import blossom.value.Value;

/**
 * The declaration keywords: {@code fn}, {@code let}, {@code let mut}, {@code pub}.
 *
 * <p>These are prefixes, not functions: they bind more loosely than {@code ->} and {@code =}, so
 * they take the entire definition that follows them and register it in the module's symbol
 * table. Registration happens once, while the module is loading; at runtime there is nothing left
 * to decide.
 *
 * <p>Why prefixes and not naming conventions: nothing implicit. Whether a
 * symbol is module-private or exported is a declared property, not something deduced from how its
 * name begins.
 */
public class Modules
{
    /** The reserved names: they can no longer be used as variables or functions. */
    public static final java.util.Set<String> KEYWORDS = java.util.Set.of("fn", "let", "mut", "pub", "module", "import", "as");

    public static void apply(Expression expression)
    {
        int precedence = Operators.precedence.get("declaration");

        // fn <definition> - declares a module function, private unless marked 'pub'
        expression.addLazyUnaryOperator("fn", "declare_function", precedence, false, false, t -> Context.Type.NONE, (c, t, lv) ->
        {
            Value defined = lv.evalValue(c, Context.Type.NONE);
            if (!(defined instanceof FunctionValue))
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "'fn' wants a function definition, e.g. fn name(a, b) -> a + b;");
            }
            return (cc, tt) -> defined;
        });

        // let <name> = <value> - immutable module binding
        declareBinding(expression, "let", precedence, false);
        // let mut <name> = <value> - reassignable module binding
        declareBinding(expression, "let_mut", precedence, true);

        // pub <declaration> - exports the symbol just declared
        expression.addLazyUnaryOperator("pub", "export", precedence, false, false, t -> Context.Type.NONE, (c, t, lv) ->
        {
            Value declared = lv.evalValue(c, Context.Type.NONE);
            String name = nameOf(declared);
            if (name == null)
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "'pub' wants a named declaration, e.g. pub fn name(...) -> ...; or pub let name = ...;");
            }
            c.host.symbolsOf(expression.module).export(name);
            return (cc, tt) -> declared;
        });
    }

    private static void declareBinding(Expression expression, String keyword, int precedence, boolean mutable)
    {
        expression.addLazyUnaryOperator(keyword, "declare_" + keyword, precedence, false, false, t -> Context.Type.LVALUE, (c, t, lv) ->
        {
            Value assigned = lv.evalValue(c, Context.Type.LVALUE);
            if (!(assigned instanceof final VariableReferenceValue reference))
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "'" + keyword.replace('_', ' ') + "' wants a name to bind, e.g. "
                                + keyword.replace('_', ' ') + " counter = 0;");
            }
            String name = reference.name();
            Value value = reference.current();
            ModuleSymbols symbols = c.host.symbolsOf(expression.module);
            symbols.declareBinding(name, mutable);
            // the inner assignment created a variable local to the module body: the value
            // belongs to the module, so it must be moved where functions can see it
            c.delVariable(name);
            c.host.setGlobalVariable(expression.module, name, value);
            // the declaration evaluates to the reference, not the value: 'pub' sits on top of it and wants the name
            return new VariableReferenceValue(name, value);
        });
    }

    private static String nameOf(Value declared)
    {
        if (declared instanceof final FunctionValue function)
        {
            return function.getString();
        }
        return declared instanceof final VariableReferenceValue reference ? reference.name() : null;
    }
}
