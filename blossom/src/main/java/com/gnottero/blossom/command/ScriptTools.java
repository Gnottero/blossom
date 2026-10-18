package com.gnottero.blossom.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Interpreter;
import blossom.interpreter.LazyValue;
import blossom.interpreter.ScriptHost;
import blossom.interpreter.Token;
import blossom.value.FunctionValue;
import blossom.value.NumericValue;
import blossom.value.Value;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.ScriptRuntime;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.value.BlockValue;

/**
 * What {@code /blossom script} does besides running code: evaluating an expression over a volume,
 * calling a function of an app from the command line, looking inside a loaded app, wiring events by
 * hand, stopping everything, and showing how the parser reads an expression.
 *
 * <p>These are Scarpet's {@code /script scan|fill|outline|invoke|globals|event|stop|explain}, kept
 * with their semantics: someone used to them finds them where they expect.
 */
public final class ScriptTools
{
    private ScriptTools()
    {
    }

    // --- scan, fill, outline ---

    /**
     * The expression of a volume command, compiled once and evaluated block after block.
     *
     * <p>{@code x}, {@code y} and {@code z} are relative to the origin, {@code _} is the block. No
     * event fires while it runs: a fill that set off the apps listening for placed blocks would be
     * a command whose cost nobody can predict.
     */
    private static final class AreaExpression
    {
        private final Expression expression;
        private final ScriptContext context;
        private final ScriptHost host;
        private final BlockPos origin;

        AreaExpression(ScriptRuntime runtime, CommandSourceStack source, @Nullable String app, String code, BlockPos origin)
        {
            Interpreter interpreter = runtime.interpreterFor(app);
            this.host = interpreter.host();
            this.origin = origin;
            this.context = new ScriptContext(host, source, origin);
            context.beginExecution(runtime.interactiveWatchdog(host));
            this.expression = interpreter.compileIn(context, code);
        }

        boolean test(int x, int y, int z)
        {
            context.setVariable("x", LazyValue.ofConstant(new NumericValue(x - origin.getX())));
            context.setVariable("y", LazyValue.ofConstant(new NumericValue(y - origin.getY())));
            context.setVariable("z", LazyValue.ofConstant(new NumericValue(z - origin.getZ())));
            BlockValue block = BlockValue.fromCoords(context, x, y, z);
            context.implicitValue = (c, t) -> block;
            return expression.executeAndEvaluate(context, true, host.loadOverrides, null).getLeft().getBoolean();
        }
    }

    private static int volumeOf(BoundingBox area)
    {
        return area.getXSpan() * area.getYSpan() * area.getZSpan();
    }

    /** The game's own limit for /fill: a script over a volume is held to the same one. */
    private static boolean tooLarge(CommandSourceStack source, BoundingBox area)
    {
        int limit = source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
        if (volumeOf(area) > limit)
        {
            source.sendFailure(Component.literal("Too many blocks: " + volumeOf(area) + ", the limit is " + limit
                    + " (game rule max_block_modifications)"));
            return true;
        }
        return false;
    }

    /** {@code scan}: how many blocks of the volume the expression is true for. */
    public static int scan(ScriptRuntime runtime, CommandSourceStack source, @Nullable String app, BlockPos origin,
            BlockPos from, BlockPos to, String code)
    {
        BoundingBox area = BoundingBox.fromCorners(from, to);
        if (tooLarge(source, area))
        {
            return 0;
        }
        AreaExpression expression = new AreaExpression(runtime, source, app, code, origin);
        int[] successes = {0};
        Event.suppressing(() ->
        {
            forEach(area, (x, y, z) ->
            {
                if (expression.test(x, y, z))
                {
                    successes[0]++;
                }
            });
            return true;
        });
        source.sendSuccess(() -> Component.literal("The expression is true for " + successes[0] + " of "
                + volumeOf(area) + " blocks"), false);
        return successes[0];
    }

    /**
     * {@code fill} and {@code outline}: sets {@code block} where the expression is true, or only on
     * the surface of that shape, that is where it is true and false for a neighbour.
     */
    public static int fill(ScriptRuntime runtime, CommandSourceStack source, @Nullable String app, BlockPos origin,
            BlockPos from, BlockPos to, String code, BlockInput block, @Nullable Predicate<BlockInWorld> replace,
            boolean outline)
    {
        BoundingBox area = BoundingBox.fromCorners(from, to);
        if (tooLarge(source, area))
        {
            return 0;
        }
        AreaExpression expression = new AreaExpression(runtime, source, app, code, origin);
        int sx = area.getXSpan();
        int sy = area.getYSpan();
        int sz = area.getZSpan();
        boolean[][][] inside = new boolean[sx][sy][sz];
        Event.suppressing(() ->
        {
            forEach(area, (x, y, z) -> inside[x - area.minX()][y - area.minY()][z - area.minZ()] = expression.test(x, y, z));
            return true;
        });
        boolean[][][] chosen = outline ? surfaceOf(inside) : inside;
        ServerLevel level = source.getLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> placed = new ArrayList<>();
        List<BlockState> previous = new ArrayList<>();
        for (int x = 0; x < sx; x++)
        {
            for (int y = 0; y < sy; y++)
            {
                for (int z = 0; z < sz; z++)
                {
                    if (!chosen[x][y][z])
                    {
                        continue;
                    }
                    pos.set(x + area.minX(), y + area.minY(), z + area.minZ());
                    if (replace != null && !replace.test(new BlockInWorld(level, pos, true)))
                    {
                        continue;
                    }
                    BlockState old = level.getBlockState(pos);
                    // the flags of the vanilla /fill: the clients see it, the neighbours update afterwards
                    if (block.place(level, pos, 2 | 256))
                    {
                        placed.add(pos.immutable());
                        previous.add(old);
                    }
                }
            }
        }
        for (int i = 0; i < placed.size(); i++)
        {
            level.updateNeighboursOnBlockSet(placed.get(i), previous.get(i));
        }
        int count = placed.size();
        source.sendSuccess(() -> Component.literal("Changed " + count + " blocks in a volume of " + volumeOf(area)), true);
        return count;
    }

    /** the blocks of the shape that touch the outside: at least one neighbour that is not in it */
    private static boolean[][][] surfaceOf(boolean[][][] inside)
    {
        int mx = inside.length - 1;
        int my = inside[0].length - 1;
        int mz = inside[0][0].length - 1;
        boolean[][][] surface = new boolean[mx + 1][my + 1][mz + 1];
        for (int x = 0; x <= mx; x++)
        {
            for (int y = 0; y <= my; y++)
            {
                for (int z = 0; z <= mz; z++)
                {
                    surface[x][y][z] = inside[x][y][z] && (
                            (x != 0 && !inside[x - 1][y][z]) || (x != mx && !inside[x + 1][y][z])
                                    || (y != 0 && !inside[x][y - 1][z]) || (y != my && !inside[x][y + 1][z])
                                    || (z != 0 && !inside[x][y][z - 1]) || (z != mz && !inside[x][y][z + 1]));
                }
            }
        }
        return surface;
    }

    @FunctionalInterface
    private interface BlockAction
    {
        void at(int x, int y, int z);
    }

    private static void forEach(BoundingBox area, BlockAction action)
    {
        for (int x = area.minX(); x <= area.maxX(); x++)
        {
            for (int y = area.minY(); y <= area.maxY(); y++)
            {
                for (int z = area.minZ(); z <= area.maxZ(); z++)
                {
                    action.at(x, y, z);
                }
            }
        }
    }

    // --- invoke ---

    /**
     * Calls a function of the app with arguments typed on the command line.
     *
     * <p>{@code invokepoint} and {@code invokearea} pass the coordinates first, as plain numbers,
     * then the rest. The arguments are separated by spaces, and each is an expression of the app:
     * {@code 3}, {@code 'oak'}, {@code [1, 2]} and a module binding all work.
     */
    public static Value invoke(ScriptRuntime runtime, CommandSourceStack source, String app, String name,
            List<BlockPos> positions, String arguments)
    {
        Interpreter interpreter = runtime.requireApp(app);
        ScriptHost host = interpreter.host();
        FunctionValue function = host.getFunction(host.main, name);
        if (function == null)
        {
            throw new UndefinedSymbolError(SourcePosition.UNKNOWN, "'" + app + "' has no function '" + name + "'.",
                    blossom.exception.Suggestions.closestTo(name, functionNames(host)));
        }
        List<Value> args = new ArrayList<>();
        for (BlockPos pos : positions)
        {
            args.add(new NumericValue(pos.getX()));
            args.add(new NumericValue(pos.getY()));
            args.add(new NumericValue(pos.getZ()));
        }
        for (String piece : splitArguments(arguments))
        {
            args.add(interpreter.evalIn(new ScriptContext(host, source), piece, runtime.interactiveWatchdog(host)));
        }
        if (function.getVarArgs() == null ? function.getArguments().size() != args.size() : function.getArguments().size() > args.size())
        {
            throw new ArityError(SourcePosition.UNKNOWN, function.getString() + " takes " + function.getArguments().size()
                    + " arguments " + function.getArguments() + ", and it was given " + args.size() + ".");
        }
        return runtime.call(app, source, function, args);
    }

    /** The function names of the app's main module, for suggestions. */
    public static List<String> functionNames(ScriptHost host)
    {
        return host.globalFunctionNames(host.main, name -> true).distinct().sorted().toList();
    }

    /** Space separated, but not inside quotes or brackets: {@code 1 'two words' [3, 4]} is three. */
    static List<String> splitArguments(String text)
    {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (quote != 0)
            {
                current.append(ch);
                if (ch == '\\' && i + 1 < text.length())
                {
                    current.append(text.charAt(++i));
                }
                else if (ch == quote)
                {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"')
            {
                quote = ch;
            }
            else if (ch == '[' || ch == '{' || ch == '(')
            {
                depth++;
            }
            else if (ch == ']' || ch == '}' || ch == ')')
            {
                depth--;
            }
            else if (Character.isWhitespace(ch) && depth == 0)
            {
                if (!current.isEmpty())
                {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty())
        {
            pieces.add(current.toString());
        }
        return pieces;
    }

    // --- globals ---

    /** The functions and the module bindings of the app's main module, with where they are defined. */
    public static int globals(ScriptRuntime runtime, CommandSourceStack source, String app)
    {
        ScriptHost host = runtime.requireApp(app).host();
        List<String> functions = functionNames(host);
        source.sendSuccess(() -> Component.literal("Functions of '" + app + "':").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (String name : functions)
        {
            FunctionValue function = host.getFunction(host.main, name);
            if (function == null)
            {
                continue;
            }
            Token token = function.getToken();
            String where = function.getModule() == null || function.getModule().equals(host.main)
                    ? "line " + (token.lineno + 1)
                    : function.getModule().name() + ", line " + (token.lineno + 1);
            source.sendSuccess(() -> Component.literal("  " + name + "(" + String.join(", ", function.getArguments()) + ")")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  " + where).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), false);
        }
        Map<String, LazyValue> bindings = new TreeMap<>();
        host.globalVariableNames(host.main, name -> true).forEach(name -> {
            LazyValue value = host.getGlobalVariable(host.main, name);
            if (value != null)
            {
                bindings.put(name, value);
            }
        });
        source.sendSuccess(() -> Component.literal("Bindings of '" + app + "':").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        ScriptContext context = new ScriptContext(host, source);
        bindings.forEach((name, value) -> source.sendSuccess(() -> Component.literal("  " + name + " = ")
                .append(Component.literal(value.evalValue(context).getPrettyString()).withStyle(ChatFormatting.GRAY)), false));
        return functions.size() + bindings.size();
    }

    // --- events ---

    /** Every handler of every event, with the app it belongs to. */
    public static int listEvents(CommandSourceStack source)
    {
        int count = 0;
        for (String name : Event.names())
        {
            Event event = Event.byName(name);
            if (event == null || event.handlers().isEmpty())
            {
                continue;
            }
            source.sendSuccess(() -> Component.literal(name + ":").withStyle(ChatFormatting.AQUA), false);
            for (Event.Handler handler : event.handlers())
            {
                count++;
                source.sendSuccess(() -> Component.literal("  " + handler.function().getString() + "  ")
                        .append(Component.literal("(" + handler.app() + (handler.targetPlayer() == null ? "" : ", for " + handler.targetPlayer()) + ")")
                                .withStyle(ChatFormatting.GRAY)), false);
            }
        }
        if (count == 0)
        {
            source.sendSuccess(() -> Component.literal("No event has a handler"), false);
        }
        return count;
    }

    /** Attaches a function of the app to an event, as {@code event.on} would from the code. */
    public static void addEvent(ScriptRuntime runtime, CommandSourceStack source, String eventName, String app, String name)
    {
        Event event = com.gnottero.blossom.event.EventBus.eventOrThrow(eventName);
        Interpreter interpreter = runtime.requireApp(app);
        FunctionValue function = interpreter.host().getFunction(interpreter.host().main, name);
        if (function == null)
        {
            throw new UndefinedSymbolError(SourcePosition.UNKNOWN, "'" + app + "' has no function '" + name + "'.",
                    blossom.exception.Suggestions.closestTo(name, functionNames(interpreter.host())));
        }
        if (function.getArguments().size() != event.arity())
        {
            throw new ArityError(SourcePosition.UNKNOWN, "The handler of '" + event.name() + "' takes " + event.arity()
                    + " arguments, and " + function.getString() + " takes " + function.getArguments().size() + ".");
        }
        event.add(new Event.Handler(app, interpreter, source, function, List.of(), null));
    }

    // --- explain ---

    /**
     * How the parser reads an expression, colored by kind of token.
     *
     * @param style {@code raw} (the tokens as written), {@code clean} (after the tokenizer's rewriting),
     *              {@code canonical} (from the tree), {@code optimized}, {@code functional} or
     *              {@code functional_optimized} (after the optimizer)
     */
    public static int explain(ScriptRuntime runtime, CommandSourceStack source, @Nullable String app,
            @Nullable String code, @Nullable String function, @Nullable String style)
    {
        Interpreter interpreter = runtime.interpreterFor(app);
        ScriptHost host = interpreter.host();
        Context context = new ScriptContext(host, source);
        Expression expression = new Expression("");
        com.gnottero.blossom.BlossomApi.apply(expression, app == null ? "<comando>" : app);
        String text = code != null ? code : (function == null && host.main != null ? host.main.code() : null);
        if (text != null)
        {
            // the preamble (module, import) is not an expression: the parser reads what comes after it
            text = blossom.project.ModuleSource.parse(host.main == null ? "<code>" : host.main.name(), text).body();
        }
        List<Token> tokens = expression.explain(context, text, function, style);
        print(source, tokens);
        return tokens.size();
    }

    private static void print(CommandSourceStack source, List<Token> tokens)
    {
        if (tokens.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("Nothing to explain"), false);
            return;
        }
        MutableComponent line = Component.empty();
        int lineno = tokens.getFirst().lineno;
        for (Token token : tokens)
        {
            if (token.lineno != lineno)
            {
                MutableComponent finished = line;
                source.sendSuccess(() -> finished, false);
                line = Component.empty();
                lineno = token.lineno;
            }
            String surface = token.display.isEmpty() ? token.surface : token.display;
            MutableComponent piece = Component.literal(surface).withStyle(colorOf(token.type));
            if (!token.comment.isEmpty())
            {
                piece = piece.withStyle(ChatFormatting.UNDERLINE)
                        .withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(token.comment))));
            }
            line.append(piece);
        }
        MutableComponent finished = line;
        source.sendSuccess(() -> finished, false);
    }

    private static ChatFormatting colorOf(Token.@Nullable TokenType type)
    {
        if (type == null)
        {
            return ChatFormatting.RED;
        }
        return switch (type)
        {
            case LITERAL -> ChatFormatting.GOLD;
            case HEX_LITERAL -> ChatFormatting.LIGHT_PURPLE;
            case CONSTANT -> ChatFormatting.DARK_AQUA;
            case OPEN_PAREN, CLOSE_PAREN, COMMA, OPERATOR, UNARY_OPERATOR -> ChatFormatting.WHITE;
            case FUNCTION -> ChatFormatting.AQUA;
            case VARIABLE -> ChatFormatting.YELLOW;
            case STRINGPARAM, INTERPOLATION -> ChatFormatting.GREEN;
            default -> ChatFormatting.RED;
        };
    }

    /** For the suggestions: the styles of explain. */
    public static final List<String> EXPLAIN_STYLES =
            Collections.unmodifiableList(List.of("raw", "clean", "canonical", "optimized", "functional", "functional_optimized"));
}
