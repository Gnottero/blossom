package blossom.language;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.interpreter.ModuleSymbols;
import blossom.value.FunctionValue;
import blossom.value.Value;

/**
 * Le parole chiave delle dichiarazioni: {@code fn}, {@code let}, {@code let mut}, {@code pub}.
 *
 * <p>Sono prefissi, non funzioni: legano meno di {@code ->} e di {@code =}, quindi prendono tutta la
 * definizione che li segue e la registrano nella tabella dei simboli del modulo. La registrazione
 * avviene una volta, mentre il modulo si carica; a runtime non resta nulla da decidere.
 *
 * <p>Perché prefissi e non convenzioni sui nomi: è il principio §1.1 del piano. Che un simbolo sia
 * di modulo o esportato è una proprietà dichiarata, non qualcosa che si deduce da come inizia il suo
 * nome.
 */
public class Modules
{
    /** i nomi riservati: non possono più essere usati come variabili o funzioni */
    public static final java.util.Set<String> KEYWORDS = java.util.Set.of("fn", "let", "mut", "pub", "module", "import", "as");

    public static void apply(Expression expression)
    {
        int precedence = Operators.precedence.get("declaration");

        // fn <definizione> - dichiara una funzione del modulo, privata salvo 'pub'
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

        // let <nome> = <valore> - binding di modulo immutabile
        declareBinding(expression, "let", precedence, false);
        // let mut <nome> = <valore> - binding di modulo riassegnabile
        declareBinding(expression, "let_mut", precedence, true);

        // pub <dichiarazione> - esporta il simbolo appena dichiarato
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
            String name = assigned.boundVariable;
            if (name == null)
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "'" + keyword.replace('_', ' ') + "' wants a name to bind, e.g. "
                                + keyword.replace('_', ' ') + " counter = 0;");
            }
            ModuleSymbols symbols = c.host.symbolsOf(expression.module);
            symbols.declareBinding(name, mutable);
            // l'assegnamento interno ha creato una variabile locale al corpo del modulo: il valore
            // appartiene al modulo, quindi va spostato dove le funzioni possono vederlo
            c.delVariable(name);
            LazyValue stored = (cc, tt) -> assigned;
            c.host.setGlobalVariable(expression.module, name, stored);
            return stored;
        });
    }

    private static String nameOf(Value declared)
    {
        if (declared instanceof final FunctionValue function)
        {
            return function.getString();
        }
        return declared.boundVariable;
    }
}
