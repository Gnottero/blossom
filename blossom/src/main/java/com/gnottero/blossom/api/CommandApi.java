package com.gnottero.blossom.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import blossom.value.FunctionValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.StringValue;
import blossom.value.Value;
import com.gnottero.blossom.command.AppCommands;
import com.gnottero.blossom.command.CommandArgument;

/**
 * {@code core.mc.command}: the app's own command, {@code /<app> ...}.
 *
 * <pre>
 * import "core.mc.command" as command;
 *
 * command.register('plant &lt;pos&gt; &lt;block&gt;', fn(pos, block) -&gt; ...);
 * command.argument('radius', {'type' -&gt; 'int', 'min' -&gt; 1, 'max' -&gt; 32});
 * command.permission('ops');
 * </pre>
 *
 * <p>Declared from the code, like the events: the function is passed as a value, so it stays bound
 * to the module that defined it, and its number of parameters is checked against the path now, not
 * when someone types the command.
 */
public final class CommandApi
{
    private CommandApi()
    {
    }

    public static void apply(Expression expression, String appName)
    {
        // register(pattern, function) or register(pattern, function, [fixed arguments])
        expression.addContextFunction("command_register", -1, (c, t, lv) ->
        {
            if (lv.size() < 2 || lv.size() > 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "command.register(...) wants the path and the function: register('add <pos>', fn(pos) -> ...), and optionally a list of fixed arguments.");
            }
            if (!(lv.get(1) instanceof final FunctionValue function))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "The second argument of command.register(...) is the function to run: pass it by name or write it in place, fn(...) -> ....");
            }
            List<Value> fixed = List.of();
            if (lv.size() == 3 && !lv.get(2).isNull())
            {
                if (!(lv.get(2) instanceof final ListValue list))
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN, "The fixed arguments of command.register(...) go in a list.");
                }
                fixed = list.getItems();
            }
            commandsOf(appName).register(lv.get(0).getString(), function, fixed);
            return Value.TRUE;
        });

        // argument(name, {'type' -> ..., modifiers...})
        expression.addContextFunction("command_argument", 2, (c, t, lv) ->
        {
            if (!(lv.get(1) instanceof final MapValue spec))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "command.argument(name, spec) wants a map as spec, e.g. {'type' -> 'int', 'min' -> 1}.");
            }
            Map<String, Value> byName = new LinkedHashMap<>();
            spec.getMap().forEach((key, value) -> byName.put(key.getString(), value));
            commandsOf(appName).argument(lv.get(0).getString(), byName);
            return Value.TRUE;
        });

        expression.addContextFunction("command_permission", 1, (c, t, lv) ->
        {
            commandsOf(appName).permission(lv.get(0));
            return Value.TRUE;
        });

        expression.addContextFunction("command_allow_conflicts", -1, (c, t, lv) ->
        {
            commandsOf(appName).allowConflicts(lv.isEmpty() || lv.get(0).getBoolean());
            return Value.TRUE;
        });

        // commands() - the paths declared so far; types() - the built-in argument types
        expression.addContextFunction("command_list", 0, (c, t, lv) ->
                ListValue.wrap(commandsOf(appName).patterns().stream().map(StringValue::of)));
        expression.addContextFunction("command_types", 0, (c, t, lv) ->
                ListValue.wrap(CommandArgument.builtInNames().stream().map(StringValue::of)));
    }

    private static AppCommands commandsOf(String appName)
    {
        if (appName.startsWith("<"))
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Commands belong to a loaded app: code run with /blossom script run has no command to add them to.");
        }
        return AppCommands.of(appName);
    }
}
