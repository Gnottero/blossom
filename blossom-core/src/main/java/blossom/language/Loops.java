package blossom.language;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.exception.BreakStatement;
import blossom.exception.ContinueStatement;
import blossom.value.AbstractListValue;
import blossom.value.FunctionValue;
import blossom.value.ListValue;
import blossom.value.NumericValue;
import blossom.value.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Loops
{
    /**
     * The body of a loop, which can be written in two ways.
     *
     * <p>The short form is an expression with {@code _} bound to the element, {@code _i} to its
     * index, and {@code _a} to the accumulator: it is also the fastest, because it creates no
     * call frame. The long form is a lambda, {@code fn(x) -> ...}, which has its own names in
     * place of the three implicit ones, and there the names can no longer collide between a loop
     * and whatever contains it.
     *
     * <p>Which of the two it is is decided by the token, so the decision is made while building
     * the tree and not on every element. A body that is not a lambda is returned as it arrived,
     * at no cost.
     *
     * @param accumulatorFirst for {@code reduce}, where a two-argument lambda reads
     *                         {@code fn(accumulator, element)} rather than {@code fn(element, index)}
     */
    private static LazyValue loopBody(Context c, LazyValue expr, boolean accumulatorFirst)
    {
        if (!(expr instanceof LazyValue.Lambda))
        {
            return expr;
        }
        FunctionValue function = (FunctionValue) expr.evalValue(c, Context.NONE);
        int arity = function.getArguments().size();
        // qui dentro ci sono esattamente due valori impliciti da passare, e un parametro in piu' o in
        // meno non e' un dettaglio: prima si riempiva di null quello che avanzava e si lasciava fuori
        // quello che mancava, cioe' si rispondeva un numero sbagliato invece di dire cos'era storto
        String shape = accumulatorFirst ? "(accumulator, element)" : "(element, index)";
        if (arity > 2 && function.getVarArgs() == null)
        {
            throw new ArityError(SourcePosition.UNKNOWN, "The function given here is called with "
                    + shape + ", so it takes at most two parameters: " + function.getString()
                    + " declares " + arity + ".");
        }
        if (accumulatorFirst && arity == 1)
        {
            throw new ArityError(SourcePosition.UNKNOWN, "The function given to 'reduce' is called with "
                    + shape + ": " + function.getString() + " declares only one parameter, so it would"
                    + " never see the element.");
        }
        return (cc, tt) ->
        {
            List<Value> args = new ArrayList<>(arity);
            if (accumulatorFirst && arity > 0)
            {
                args.add(cc.implicitAccumulator().evalValue(cc, Context.NONE));
                if (arity > 1)
                {
                    args.add(cc.implicitValue().evalValue(cc, Context.NONE));
                }
            }
            else
            {
                if (arity > 0)
                {
                    args.add(cc.implicitValue().evalValue(cc, Context.NONE));
                }
                if (arity > 1)
                {
                    args.add(cc.implicitIndex().evalValue(cc, Context.NONE));
                }
            }
            while (args.size() < arity)
            {
                args.add(Value.NULL);
            }
            return function.callInContext(cc, Context.NONE, args).evalValue(cc, Context.NONE);
        };
    }

    private static LazyValue loopBody(Context c, LazyValue expr)
    {
        return loopBody(c, expr, false);
    }

    /** Same as above, for callers outside this file: the key of {@code sort}, for instance. */
    public static LazyValue keyOf(Context c, LazyValue expr)
    {
        return loopBody(c, expr, false);
    }

    public static void apply(Expression expression)
    {
        // condition and expression will get a bound '_i'
        // returns last successful expression or false
        // while(cond, limit, expr) => ??
        expression.addImpureFunction("break", lv ->
        {
            if (lv.isEmpty())
            {
                throw new BreakStatement(null);
            }
            if (lv.size() == 1)
            {
                throw new BreakStatement(lv.get(0));
            }
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "'break' can only be called with zero or one argument");
        });

        expression.addImpureFunction("continue", lv ->
        {
            if (lv.isEmpty())
            {
                throw new ContinueStatement(null);
            }
            if (lv.size() == 1)
            {
                throw new ContinueStatement(lv.get(0));
            }
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "'continue' can only be called with zero or one argument");
        });

        // lazy
        expression.addLazyFunction("while", -1, (c, t, lv) ->
        {
            if (lv.size() == 2) { // lets do nasty way so performance is not affected (might be super unnecessary, but hey)
                LazyValue condition = lv.get(0);
                LazyValue expr = loopBody(c, lv.get(1));
                long i = 0;
                Value lastOne = Value.NULL;
                //scoping
                LazyValue defaultVal = c.implicitValue;
                c.implicitValue = (cc, tt) -> new NumericValue(0);
                final blossom.interpreter.ExecutionBudget budget = c.budget;
                while (condition.evalValue(c, Context.BOOLEAN).getBoolean())
                {
                    budget.tick();
                    try
                    {
                        lastOne = expr.evalValue(c, t);
                    }
                    catch (BreakStatement | ContinueStatement stmt)
                    {
                        if (stmt.retval != null)
                        {
                            lastOne = stmt.retval;
                        }
                        if (stmt instanceof BreakStatement)
                        {
                            break;
                        }
                    }
                    i++;
                    long seriously = i;
                    c.implicitValue = (cc, tt) -> new NumericValue(seriously);
                }
                //revering scope
                c.implicitValue = defaultVal;
                Value lastValueNoKidding = lastOne;
                return (cc, tt) -> lastValueNoKidding;
            }
            long limit = NumericValue.asNumber(lv.get(1).evalValue(c)).getLong();
            LazyValue condition = lv.get(0);
            LazyValue expr = loopBody(c, lv.get(2));
            long i = 0;
            Value lastOne = Value.NULL;
            //scoping
            LazyValue defaultVal = c.implicitValue;
            c.implicitValue = (cc, tt) -> new NumericValue(0);
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            while (i < limit && condition.evalValue(c, Context.BOOLEAN).getBoolean())
            {
                budget.tick();
                try
                {
                    lastOne = expr.evalValue(c, t);
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null)
                    {
                        lastOne = stmt.retval;
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
                i++;
                long seriously = i;
                c.implicitValue = (cc, tt) -> new NumericValue(seriously);
            }
            //revering scope
            c.implicitValue = defaultVal;
            Value lastValueNoKidding = lastOne;
            return (cc, tt) -> lastValueNoKidding;
        });

        // loop(Num, expr) => lastdefaultValue
        // expr receives bounded variable '_' indicating iteration
        expression.addLazyFunction("loop", 2, (c, t, lv) ->
        {
            long limit = NumericValue.asNumber(lv.get(0).evalValue(c, Context.NONE)).getLong();
            Value lastOne = Value.NULL;
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            // the watchdog is read once and kept in a local: inside the language's tightest loop
            // even just re-reading the field shows up in the benchmark
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            for (long i = 0; i < limit; i++)
            {
                budget.tick();
                long whyYouAsk = i;
                c.implicitValue = (cc, tt) -> new NumericValue(whyYouAsk);
                try
                {
                    lastOne = expr.evalValue(c, t);
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null)
                    {
                        lastOne = stmt.retval;
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
            }
            //revering scope
            c.implicitValue = defaultVal;
            Value trulyLastOne = lastOne;
            return (cc, tt) -> trulyLastOne;
        });

        // map(list or Num, expr) => list_results
        // receives bounded variable '_' with the expression
        expression.addLazyFunction("map", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return ListValue.lazyEmpty();
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'map' function should be a list or iterator");
            }
            Iterator<Value> iterator = alv.iterator();
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue iterVal = c.implicitIndex;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            List<Value> result = new ArrayList<>();
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                int doYouReally = i;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(doYouReally);
                try
                {
                    result.add(expr.evalValue(c, t));
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null)
                    {
                        result.add(stmt.retval);
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
            }
            ((AbstractListValue) rval).fatality();
            Value ret = ListValue.wrap(result);
            //revering scope
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;
            return (cc, tt) -> ret;
        });

        // grep(list or num, expr) => list
        // receives bounded variable '_' with the expression, and "_i" with index
        // produces list of values for which the expression is true
        expression.addLazyFunction("filter", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return ListValue.lazyEmpty();
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'filter' function should be a list or iterator");
            }
            Iterator<Value> iterator = alv.iterator();
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue iterVal = c.implicitIndex;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            List<Value> result = new ArrayList<>();
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                int seriously = i;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(seriously);
                try
                {
                    if (expr.evalValue(c, Context.BOOLEAN).getBoolean())
                    {
                        result.add(next);
                    }
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null && stmt.retval.getBoolean())
                    {
                        result.add(next);
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
            }
            ((AbstractListValue) rval).fatality();
            Value ret = ListValue.wrap(result);
            //revering scope
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;
            return (cc, tt) -> ret;
        });

        // first(list, expr) => elem or null
        // receives bounded variable '_' with the expression, and "_i" with index
        // returns first element on the list for which the expr is true
        expression.addLazyFunction("first", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return LazyValue.NULL;
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'first' function should be a list or iterator");
            }
            Iterator<Value> iterator = alv.iterator();
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue iterVal = c.implicitIndex;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            Value result = Value.NULL;
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                int seriously = i;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(seriously);
                try
                {
                    if (expr.evalValue(c, Context.BOOLEAN).getBoolean())
                    {
                        result = next;
                        break;
                    }
                }
                catch (BreakStatement stmt)
                {
                    result = stmt.retval == null ? next : stmt.retval;
                    break;
                }
                catch (ContinueStatement ignored)
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "'continue' inside 'first' function has no sense");
                }
            }
            //revering scope
            ((AbstractListValue) rval).fatality();
            Value whyWontYouTrustMeJava = result;
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;
            return (cc, tt) -> whyWontYouTrustMeJava;
        });

        // all(list, expr) => boolean
        // receives bounded variable '_' with the expression, and "_i" with index
        // returns true if expr is true for all items
        expression.addLazyFunction("all", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return LazyValue.TRUE;
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'all' function should be a list or iterator");
            }
            Iterator<Value> iterator = alv.iterator();
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue iterVal = c.implicitIndex;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            LazyValue result = LazyValue.TRUE;
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                int seriously = i;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(seriously);
                if (!expr.evalValue(c, Context.BOOLEAN).getBoolean())
                {
                    result = LazyValue.FALSE;
                    break;
                }
            }
            //revering scope
            ((AbstractListValue) rval).fatality();
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;
            return result;
        });

        // runs traditional for(init, condition, increment, body) tri-argument for loop with body in between
        expression.addLazyFunction("c_for", 4, (c, t, lv) ->
        {
            LazyValue initial = lv.get(0);
            LazyValue condition = lv.get(1);
            LazyValue increment = lv.get(2);
            LazyValue body = lv.get(3);
            int iterations = 0;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            for (initial.evalValue(c, Context.VOID); condition.evalValue(c, Context.BOOLEAN).getBoolean(); increment.evalValue(c, Context.VOID))
            {
                budget.tick();
                try
                {
                    body.evalValue(c, Context.VOID);
                }
                catch (BreakStatement stmt)
                {
                    break;
                }
                catch (ContinueStatement ignored)
                {
                }
                iterations++;
            }
            int finalIterations = iterations;
            return (cc, tt) -> new NumericValue(finalIterations);
        });

        // similar to map, but returns total number of successes
        // for(list, expr) => success_count
        // can be substituted for first and all, but first is more efficient and all doesn't require knowing list size
        expression.addLazyFunction("for", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return LazyValue.ZERO;
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'for' function should be a list or iterator");
            }
            Iterator<Value> iterator = alv.iterator();
            LazyValue expr = loopBody(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue iterVal = c.implicitIndex;
            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            int successCount = 0;
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                int seriously = i;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(seriously);
                Value result = Value.FALSE;
                try
                {
                    result = expr.evalValue(c, t);
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null)
                    {
                        result = stmt.retval;
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
                if (t != Context.VOID && result.getBoolean())
                {
                    successCount++;
                }
            }
            //revering scope
            ((AbstractListValue) rval).fatality();
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;
            long promiseWontChange = successCount;
            return (cc, tt) -> new NumericValue(promiseWontChange);
        });


        // reduce(list, expr, ?acc) => value
        // reduces values in the list with expression that gets accumulator
        // each iteration expr receives acc - accumulator, and '_' - current list value
        // returned value is substituted to the accumulator
        expression.addLazyFunction("reduce", 3, (c, t, lv) ->
        {

            Value rval = lv.get(0).evalValue(c, Context.NONE);
            if (rval.isNull())
            {
                return ListValue.lazyEmpty();
            }
            if (!(rval instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "loop", "First argument of 'reduce' should be a list or iterator");
            }
            LazyValue expr = loopBody(c, lv.get(1), true);
            Value acc = lv.get(2).evalValue(c, Context.NONE);
            Iterator<Value> iterator = alv.iterator();

            if (!iterator.hasNext())
            {
                Value seriouslyWontChange = acc;
                return (cc, tt) -> seriouslyWontChange;
            }

            //scoping
            LazyValue defaultVal = c.implicitValue;
            LazyValue accumulatorVal = c.implicitAccumulator;
            LazyValue iterVal = c.implicitIndex;

            // the watchdog is read once, outside the loop: see the comment in 'loop'
            final blossom.interpreter.ExecutionBudget budget = c.budget;
            for (int i = 0; iterator.hasNext(); i++)
            {
                budget.tick();
                Value next = iterator.next();
                if(next == Value.EOL) {
                    continue;
                }
                Value promiseWontChangeYou = acc;
                int seriously = i;
                c.implicitAccumulator = (cc, tt) -> promiseWontChangeYou;
                c.implicitValue = (cc, tt) -> next;
                c.implicitIndex = (cc, tt) -> new NumericValue(seriously);
                try
                {
                    acc = expr.evalValue(c, t);
                }
                catch (BreakStatement | ContinueStatement stmt)
                {
                    if (stmt.retval != null)
                    {
                        acc = stmt.retval;
                    }
                    if (stmt instanceof BreakStatement)
                    {
                        break;
                    }
                }
            }
            //reverting scope
            ((AbstractListValue) rval).fatality();
            c.implicitAccumulator = accumulatorVal;
            c.implicitValue = defaultVal;
            c.implicitIndex = iterVal;

            Value hopeItsEnoughPromise = acc;
            return (cc, tt) -> hopeItsEnoughPromise;
        });
    }
}
