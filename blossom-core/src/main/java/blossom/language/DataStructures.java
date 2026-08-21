package blossom.language;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.ArityError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.utils.JsonSupport;
import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import blossom.value.BooleanValue;
import blossom.value.ContainerValueInterface;
import blossom.value.LContainerValue;
import blossom.value.LazyListValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;
import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DataStructures
{
    public static void apply(Expression expression)
    {
        // pigra per via dell'assegnamento multiplo: in 'l(a, b) = l(1, 2)' gli elementi di sinistra
        // sono nomi da legare, non variabili da leggere, e la valutazione eager di AbstractFunction
        // impone Context.NONE ai propri argomenti. Il contesto di l-value deve arrivare fino a loro
        expression.addLazyFunction("l", (c, t, lv) ->
        {
            Context.Type inner = t == Context.LVALUE ? Context.LVALUE : Context.NONE;
            List<Value> params = new ArrayList<>(lv.size());
            for (LazyValue element : lv)
            {
                Value value = element.evalValue(c, inner);
                // '...rest' dentro una lista si spiana: lo faceva la valutazione eager di
                // AbstractFunction, che qui non c'e' piu'
                if (value instanceof final blossom.value.FunctionUnpackedArgumentsValue unpacked)
                {
                    params.addAll(unpacked.getItems());
                }
                else
                {
                    params.add(value);
                }
            }
            Value ret = params.size() == 1 && params.get(0) instanceof final LazyListValue llv
                    ? ListValue.wrap(llv.unroll())
                    : new ListValue.ListConstructorValue(params);
            return (cc, tt) -> ret;
        });

        expression.addFunction("join", lv ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'join' takes at least 2 arguments");
            }
            String delimiter = lv.get(0).getString();
            List<Value> toJoin;
            if (lv.size() == 2 && lv.get(1) instanceof final LazyListValue llv)
            {
                toJoin = llv.unroll();
            }
            else if (lv.size() == 2 && lv.get(1) instanceof final ListValue llv)
            {
                toJoin = new ArrayList<>(llv.getItems());
            }
            else
            {
                toJoin = lv.subList(1, lv.size());
            }
            return new StringValue(toJoin.stream().map(Value::getString).collect(Collectors.joining(delimiter)));
        });

        expression.addFunction("split", lv ->
        {
            Value delimiter;
            Value hwat;
            if (lv.size() == 1)
            {
                hwat = lv.get(0);
                delimiter = null;
            }
            else if (lv.size() == 2)
            {
                delimiter = lv.get(0);
                hwat = lv.get(1);
            }
            else
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'split' takes 1 or 2 arguments");
            }
            return hwat.split(delimiter);
        });

        expression.addFunction("slice", lv ->
        {
            if (lv.size() != 2 && lv.size() != 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'slice' takes 2 or 3 arguments");
            }
            Value hwat = lv.get(0);
            long from = NumericValue.asNumber(lv.get(1)).getLong();
            Long to = null;
            if (lv.size() == 3)
            {
                to = NumericValue.asNumber(lv.get(2)).getLong();
            }
            return hwat.slice(from, to);
        });

        expression.addUnaryFunction("reverse", v ->
        {
            List<Value> items;
            if (v instanceof final LazyListValue llv)
            {
                items = new ArrayList<>(llv.unroll());
            }
            else if (v instanceof final ListValue list)
            {
                items = new ArrayList<>(list.getItems());
            }
            else
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "'reverse' wants a list, got a " + v.getTypeString());
            }
            Collections.reverse(items);
            return ListValue.wrap(items);
        });

        /*
         * sort(lista) - per il valore; sort(lista, chiave) - per quello che la chiave calcola.
         *
         * Erano due funzioni, 'sort' e 'sort_key'. E 'sort_key' con un argomento solo non ordinava
         * niente: mescolava la lista, che e' un'altra operazione con lo stesso nome. Ora un
         * argomento vuol dire l'ordine naturale, che e' quello che il nome promette.
         *
         * Pigra perche' la chiave e' un'espressione con '_', o una lambda.
         */
        expression.addLazyFunction("sort_key", (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "First argument for 'sort' should be a List");
            }
            Value v = lv.get(0).evalValue(c);
            if (!(v instanceof final ListValue list))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "First argument for 'sort' should be a List");
            }
            List<Value> toSort = new ArrayList<>(list.getItems());
            if (lv.size() == 1)
            {
                Collections.sort(toSort);
                Value ret = ListValue.wrap(toSort);
                return (ct, tt) -> ret;
            }
            LazyValue sortKey = Loops.keyOf(c, lv.get(1));
            //scoping
            LazyValue defaultVal = c.implicitValue;
            toSort.sort((v1, v2) -> {
                c.implicitValue = (cc, tt) -> v1;
                Value ev1 = sortKey.evalValue(c);
                c.implicitValue = (cc, tt) -> v2;
                Value ev2 = sortKey.evalValue(c);
                return ev1.compareTo(ev2);
            });
            //revering scope
            c.implicitValue = defaultVal;
            Value ret = ListValue.wrap(toSort);
            return (cc, tt) -> ret;
        });

        expression.addFunction("range", lv ->
        {
            NumericValue from = Value.ZERO;
            NumericValue to;
            NumericValue step = Value.ONE;
            int argsize = lv.size();
            if (argsize == 0 || argsize > 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'range' accepts from 1 to 3 arguments, not " + argsize);
            }
            to = NumericValue.asNumber(lv.get(0));
            if (lv.size() > 1)
            {
                from = to;
                to = NumericValue.asNumber(lv.get(1));
                if (lv.size() > 2)
                {
                    step = NumericValue.asNumber(lv.get(2));
                }
            }
            return (from.isInteger() && to.isInteger() && step.isInteger())
                    ? LazyListValue.rangeLong(from.getLong(), to.getLong(), step.getLong())
                    : LazyListValue.rangeDouble(from.getDouble(), to.getDouble(), step.getDouble());
        });

        expression.addTypedContextFunction("m", -1, Context.MAPDEF, (c, t, lv) ->
                lv.size() == 1 && lv.get(0) instanceof final LazyListValue llv
                        ? new MapValue(llv.unroll())
                        : new MapValue(lv)
        );

        expression.addUnaryFunction("keys", v ->
                v instanceof final MapValue map
                        ? new ListValue(map.getMap().keySet())
                        : Value.NULL
        );

        expression.addUnaryFunction("values", v ->
                v instanceof final MapValue map
                        ? new ListValue(map.getMap().values())
                        : Value.NULL
        );

        expression.addUnaryFunction("pairs", v ->
                v instanceof final MapValue map
                        ? ListValue.wrap(map.getMap().entrySet().stream().map(p -> ListValue.of(p.getKey(), p.getValue())))
                        : Value.NULL);

        expression.addBinaryContextOperator(":", "element",  Operators.precedence.get("attribute~:"), true, true, false, (ctx, t, container, address) ->
        {
            // dentro una firma, 'a: num' non e' un accesso a un elemento ma un tipo dichiarato.
            // Non serve grammatica nuova: in contesto di firma un identificatore sconosciuto
            // diventa gia' un valore legato al proprio nome, quindi qui arrivano due nomi
            if (t == Context.LOCALIZATION)
            {
                if (!(container instanceof final blossom.value.VariableReferenceValue parameter)
                        || !(address instanceof final blossom.value.VariableReferenceValue declaredType))
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function",
                            "Inside a signature ':' declares a parameter type, e.g. fn f(a: num) -> ...");
                }
                return new blossom.value.TypedParameterValue(parameter.name(), declaredType.name());
            }
            // fuori da una firma il nome non serve: se il contenitore e' arrivato come riferimento
            // a variabile - accade quando l'argomento viene valutato in contesto di l-value - a
            // interessare e' il valore che ci sta dietro
            container = blossom.value.VariableReferenceValue.dereference(container);
            if (container instanceof final LContainerValue lcv)
            {
                ContainerValueInterface outerContainer = lcv.container();
                if (outerContainer == null)
                {
                    return LContainerValue.NULL_CONTAINER;
                }
                return outerContainer.get(lcv.address()) instanceof final ContainerValueInterface cvi
                        ? new LContainerValue(cvi, address)
                        : LContainerValue.NULL_CONTAINER;
            }
            if (!(container instanceof final ContainerValueInterface cvi))
            {
                return t == Context.LVALUE ? LContainerValue.NULL_CONTAINER : Value.NULL;
            }
            return t != Context.LVALUE ? cvi.get(address) : new LContainerValue(cvi, address);
        });

        // lazy cause conditional typing - questionable
        expression.addLazyFunction("get", (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "'get' requires parameters");
            }
            if (lv.size() == 1)
            {
                Value v = lv.get(0).evalValue(c, Context.LVALUE);
                if (!(v instanceof final LContainerValue lcv))
                {
                    return LazyValue.NULL;
                }
                ContainerValueInterface container = lcv.container();
                if (container == null)
                {
                    return LazyValue.NULL;
                }
                Value ret = container.get(lcv.address());
                return (cc, tt) -> ret;
            }
            Value container = lv.get(0).evalValue(c);
            for (int i = 1; i < lv.size(); i++)
            {
                if (!(container instanceof final ContainerValueInterface cvi))
                {
                    return (cc, tt) -> Value.NULL;
                }
                container = cvi.get(lv.get(i).evalValue(c));
            }
            if (container == null)
            {
                return (cc, tt) -> Value.NULL;
            }
            Value finalContainer = container;
            return (cc, tt) -> finalContainer;
        });

        // same as `get`
        expression.addLazyFunction("has", (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "'has' requires parameters");
            }
            if (lv.size() == 1)
            {
                Value v = lv.get(0).evalValue(c, Context.LVALUE);
                if (!(v instanceof final LContainerValue lcv))
                {
                    return LazyValue.NULL;
                }
                ContainerValueInterface container = lcv.container();
                if (container == null)
                {
                    return LazyValue.NULL;
                }
                Value ret = BooleanValue.of(container.has(lcv.address()));
                return (cc, tt) -> ret;
            }
            Value container = lv.get(0).evalValue(c);
            for (int i = 1; i < lv.size() - 1; i++)
            {
                if (!(container instanceof final ContainerValueInterface cvi))
                {
                    return LazyValue.NULL;
                }
                container = cvi.get(lv.get(i).evalValue(c));
            }
            if (!(container instanceof final ContainerValueInterface cvi))
            {
                return LazyValue.NULL;
            }
            Value ret = BooleanValue.of(cvi.has(lv.get(lv.size() - 1).evalValue(c)));
            return (cc, tt) -> ret;
        });

        // same as `get`
        expression.addLazyFunction("put", (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'put' takes at least three arguments, a container, address, and values to insert at that index");
            }
            // in contesto di l-value una variabile si valuta al proprio riferimento: qui il nome
            // non serve, serve il contenitore che ci sta dietro. Su un LContainerValue e' un no-op
            Value container = blossom.value.VariableReferenceValue.dereference(lv.get(0).evalValue(c, Context.LVALUE));
            if (container instanceof final LContainerValue lcv)
            {
                ContainerValueInterface internalContainer = lcv.container();
                if (internalContainer == null)
                {
                    return LazyValue.NULL;
                }
                Value address = lcv.address();
                Value what = lv.get(1).evalValue(c);
                Value retVal = BooleanValue.of((lv.size() > 2)
                        ? internalContainer.put(address, what, lv.get(2).evalValue(c))
                        : internalContainer.put(address, what));
                return (cc, tt) -> retVal;

            }
            if (lv.size() < 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'put' takes at least three arguments, a container, address, and values to insert at that index");
            }
            if (!(container instanceof final ContainerValueInterface cvi))
            {
                return LazyValue.NULL;
            }
            Value where = lv.get(1).evalValue(c);
            Value what = lv.get(2).evalValue(c);
            Value retVal = BooleanValue.of((lv.size() > 3)
                    ? cvi.put(where, what, lv.get(3).evalValue(c))
                    : cvi.put(where, what));
            return (cc, tt) -> retVal;
        });

        // same as `get`
        expression.addLazyFunction("delete", (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "collection", "'delete' requires parameters");
            }
            if (lv.size() == 1)
            {
                Value v = lv.get(0).evalValue(c, Context.LVALUE);
                if (!(v instanceof final LContainerValue lcv))
                {
                    return LazyValue.NULL;
                }
                ContainerValueInterface container = lcv.container();
                if (container == null)
                {
                    return LazyValue.NULL;
                }
                Value ret = BooleanValue.of(container.delete(lcv.address()));
                return (cc, tt) -> ret;
            }
            Value container = lv.get(0).evalValue(c);
            for (int i = 1; i < lv.size() - 1; i++)
            {
                if (!(container instanceof final ContainerValueInterface cvi))
                {
                    return LazyValue.NULL;
                }
                container = cvi.get(lv.get(i).evalValue(c));
            }
            if (!(container instanceof final ContainerValueInterface cvi))
            {
                return LazyValue.NULL;
            }
            Value ret = BooleanValue.of(cvi.delete(lv.get(lv.size() - 1).evalValue(c)));
            return (cc, tt) -> ret;
        });

        expression.addUnaryFunction("encode_b64", v -> StringValue.of(Base64.getEncoder().encodeToString(v.getString().getBytes(StandardCharsets.UTF_8))));
        expression.addUnaryFunction("decode_b64", v -> {
            try
            {
                return StringValue.of(new String(Base64.getDecoder().decode(v.getString()), StandardCharsets.UTF_8));
            }
            catch (IllegalArgumentException iae)
            {
                throw new ThrowStatement("Invalid b64 string: " + v.getString(), Throwables.B64_ERROR);
            }
        });

        expression.addUnaryFunction("encode_json", v -> StringValue.of(v.toJson().toString()));
        expression.addUnaryFunction("decode_json", v -> {
            try
            {
                return JsonSupport.GSON.fromJson(v.getString(), Value.class);
            }
            catch (JsonParseException jpe)
            {
                throw new ThrowStatement("Invalid json string: " + v.getString(), Throwables.JSON_ERROR);
            }
        });
    }
}
