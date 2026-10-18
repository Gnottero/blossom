package com.gnottero.blossom.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

import blossom.exception.ArityError;
import blossom.exception.BlossomError;
import blossom.exception.BlossomRuntimeError;
import blossom.exception.ExpressionException;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.value.FunctionValue;
import blossom.value.NumericValue;
import blossom.value.Value;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptRuntime;
import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.value.EntityValue;

/**
 * The commands of an app: {@code /<app> ...}, declared from its code with {@code core.mc.command}.
 *
 * <p>An app declares while it loads: every {@code command.register} collects a path, and when the
 * load is over the whole tree is checked and built at once. Checked at once because two paths that
 * take different arguments at the same point ({@code give <player>} and {@code give <item>}) can
 * only be seen side by side, and they are a load error: brigadier would accept them and then suggest
 * badly, which is the worst kind of failure, a command that seems to work.
 *
 * <p>The tree lives in the server's dispatcher under the app's name, and it leaves with the app: on
 * unload, on reload (to come back with the new code) and on {@code /reload}, which rebuilds the
 * dispatcher from scratch and finds it again here.
 */
public final class AppCommands
{
    /** the declarations of every app that has some, by app name */
    private static final Map<String, AppCommands> APPS = new ConcurrentHashMap<>();

    /** A piece of a path: a word to type as it is, or a {@code <name>} argument. */
    record Token(String name, boolean argument)
    {
        String surface()
        {
            return argument ? "<" + name + ">" : name;
        }
    }

    /** A path, and what it runs. */
    record Entry(String pattern, List<Token> tokens, FunctionValue function, List<Value> fixed)
    {
        List<String> arguments()
        {
            return tokens.stream().filter(Token::argument).map(Token::name).toList();
        }
    }

    private final String app;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, CommandArgument> types = new LinkedHashMap<>();
    private Predicate<CommandSourceStack> permission = source -> true;
    private boolean allowConflicts = false;
    /** whether the tree is in the dispatcher: a declaration made after the load rebuilds it at once */
    private boolean installed = false;

    private AppCommands(String app)
    {
        this.app = app;
    }

    /** The declarations of an app, created on first use. */
    public static AppCommands of(String app)
    {
        return APPS.computeIfAbsent(app, AppCommands::new);
    }

    Map<String, CommandArgument> types()
    {
        return types;
    }

    // --- what the app declares ---

    /**
     * Adds a path: words to type as they are, and {@code <name>} arguments, separated by spaces.
     *
     * @param fixed values passed after the typed arguments, the same for every call: the way to
     *              point several paths at one function that tells them apart
     */
    public void register(String pattern, FunctionValue function, List<Value> fixed)
    {
        List<Token> tokens = parse(pattern);
        String normalized = tokens.stream().map(Token::surface).collect(Collectors.joining(" "));
        if (entries.stream().anyMatch(entry -> entry.tokens().equals(tokens)))
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "/" + app + (normalized.isEmpty() ? "" : " " + normalized) + " is already registered.");
        }
        int typed = (int) tokens.stream().filter(Token::argument).count();
        int declared = function.getArguments().size();
        if (function.getVarArgs() == null ? declared != typed + fixed.size() : declared > typed + fixed.size())
        {
            throw new ArityError(SourcePosition.UNKNOWN,
                    "'" + pattern + "' gives " + typed + " typed argument" + (typed == 1 ? "" : "s")
                            + (fixed.isEmpty() ? "" : " and " + fixed.size() + " fixed") + ", but "
                            + function.getString() + " takes " + declared + ".");
        }
        entries.add(new Entry(normalized, tokens, function, List.copyOf(fixed)));
        reinstallIfLive();
    }

    /** Declares the type of the arguments called {@code name} (or ending in {@code _name}). */
    public void argument(String name, Map<String, Value> spec)
    {
        if (!name.matches("[_a-zA-Z][_a-zA-Z0-9]*"))
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'" + name + "' cannot be an argument name: letters, digits and '_', not starting with a digit.");
        }
        types.put(name, CommandArgument.custom(name, spec, this));
        reinstallIfLive();
    }

    /**
     * Who can use the command: {@code 'all'}, {@code 'ops'}, {@code 'players'} (only from chat),
     * {@code 'server'} (only from console and command blocks), a level from 1 to 4, or a function
     * that takes the player (or {@code null} from the console) and says yes or no.
     */
    public void permission(Value declared)
    {
        if (declared instanceof final FunctionValue function)
        {
            if (function.getArguments().size() != 1 && function.getVarArgs() == null)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "The permission function takes one argument, the player running the command (null from the console).");
            }
            permission = source -> {
                try
                {
                    return call(source, function, List.of(source.getEntity() instanceof ServerPlayer player
                            ? new EntityValue(player) : Value.NULL)).getBoolean();
                }
                catch (RuntimeException e)
                {
                    com.gnottero.blossom.util.AppErrors.report(app, "command permission", e);
                    return false;
                }
            };
        }
        else if (declared instanceof final NumericValue number)
        {
            int level = (int) number.getLong();
            permission = switch (level)
            {
                case 1 -> source -> check(source, Commands.LEVEL_MODERATORS);
                case 2 -> source -> check(source, Commands.LEVEL_GAMEMASTERS);
                case 3 -> source -> check(source, Commands.LEVEL_ADMINS);
                case 4 -> source -> check(source, Commands.LEVEL_OWNERS);
                default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "A permission level goes from 1 to 4, not " + level + ".");
            };
        }
        else
        {
            String name = declared.getString().toLowerCase(java.util.Locale.ROOT);
            permission = switch (name)
            {
                case "all" -> source -> true;
                case "ops" -> source -> check(source, Commands.LEVEL_GAMEMASTERS);
                case "players" -> source -> source.getEntity() instanceof ServerPlayer;
                case "server" -> source -> !(source.getEntity() instanceof ServerPlayer);
                default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "Unknown command permission '" + name + "'. It takes 'all', 'ops', 'players', 'server', a level from 1 to 4, or a function.",
                        blossom.exception.Suggestions.closestTo(name, List.of("all", "ops", "players", "server")));
            };
        }
        reinstallIfLive();
    }

    private static boolean check(CommandSourceStack source, PermissionCheck level)
    {
        return level.check(source.permissions());
    }

    /** Lets two paths take different arguments at the same point: brigadier copes, suggestions do not. */
    public void allowConflicts(boolean allow)
    {
        allowConflicts = allow;
        reinstallIfLive();
    }

    /** The paths declared so far, as they are typed. */
    public List<String> patterns()
    {
        return entries.stream().map(entry -> "/" + app + (entry.pattern().isEmpty() ? "" : " " + entry.pattern())).toList();
    }

    private static List<Token> parse(String pattern)
    {
        List<Token> tokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String trimmed = pattern.trim();
        if (trimmed.isEmpty())
        {
            return tokens;
        }
        for (String piece : trimmed.split("\\s+"))
        {
            boolean argument = piece.startsWith("<") && piece.endsWith(">");
            String name = argument ? piece.substring(1, piece.length() - 1) : piece;
            boolean valid = argument ? name.matches("[_a-zA-Z][_a-zA-Z0-9]*") : name.matches("[-_a-zA-Z0-9.]+");
            if (!valid)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'" + piece + "' in '" + pattern + "' is neither a word nor an <argument>: a word is letters, digits,"
                                + " '_', '-' and '.', an argument is a name between < and >.");
            }
            if (argument && !seen.add(name))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'<" + name + ">' appears twice in '" + pattern + "': the arguments of a path need different names.");
            }
            tokens.add(new Token(name, argument));
        }
        return tokens;
    }

    // --- the tree ---

    /** The root of the tree, ready for the dispatcher. */
    private LiteralArgumentBuilder<CommandSourceStack> tree(CommandBuildContext context)
    {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Entry::tokens, AppCommands::compare));
        if (!allowConflicts)
        {
            checkConflicts(sorted);
        }
        Predicate<CommandSourceStack> allowed = permission;
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(app).requires(allowed);
        for (Entry entry : sorted)
        {
            if (entry.tokens().isEmpty())
            {
                root.executes(c -> execute(c, entry));
                continue;
            }
            List<Token> tokens = entry.tokens();
            ArgumentBuilder<CommandSourceStack, ?> chain = node(tokens.getLast(), context).executes(c -> execute(c, entry));
            for (int i = tokens.size() - 2; i >= 0; i--)
            {
                chain = node(tokens.get(i), context).then(chain);
            }
            root.then(chain);
        }
        return root;
    }

    private ArgumentBuilder<CommandSourceStack, ?> node(Token token, CommandBuildContext context)
    {
        return token.argument() ? CommandArgument.node(token.name(), this, context) : Commands.literal(token.name());
    }

    /** Words before arguments, then alphabetical: the order in which a conflict shows up between neighbours. */
    private static int compare(List<Token> first, List<Token> other)
    {
        for (int i = 0; i < Math.min(first.size(), other.size()); i++)
        {
            Token a = first.get(i);
            Token b = other.get(i);
            int c = a.argument() != b.argument() ? (a.argument() ? 1 : -1) : a.name().compareTo(b.name());
            if (c != 0)
            {
                return c;
            }
        }
        return Integer.compare(first.size(), other.size());
    }

    private void checkConflicts(List<Entry> sorted)
    {
        for (int i = 0; i + 1 < sorted.size(); i++)
        {
            List<Token> first = sorted.get(i).tokens();
            List<Token> other = sorted.get(i + 1).tokens();
            for (int t = 0; t < Math.min(first.size(), other.size()); t++)
            {
                Token a = first.get(t);
                Token b = other.get(t);
                if (a.argument() && b.argument() && !a.name().equals(b.name()))
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "command",
                            "Two commands of '" + app + "' take different arguments at the same point:\n"
                                    + "  /" + app + " " + sorted.get(i).pattern() + "\n"
                                    + "  /" + app + " " + sorted.get(i + 1).pattern() + "\n"
                                    + "Give the argument the same name in both, or call command.allow_conflicts() if it is wanted.");
                }
                if (!a.equals(b))
                {
                    break;
                }
            }
        }
    }

    // --- in the dispatcher ---

    /**
     * Puts the app's tree in the server's dispatcher and tells the players.
     *
     * <p>Called when the load is over. A name already taken by the game or by another mod is a load
     * error: the app would hide that command, or be hidden by it.
     */
    public static void install(MinecraftServer server, String app)
    {
        AppCommands commands = APPS.get(app);
        if (commands == null || commands.entries.isEmpty())
        {
            return;
        }
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        commands.installInto(dispatcher, CommandBuildContext.simple(server.registryAccess(), server.getWorldData().enabledFeatures()));
        notifyPlayers(server);
    }

    private void installInto(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context)
    {
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild(app);
        if (existing != null && !installed)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "command",
                    "There is already a /" + app + " command, from the game or another mod: the app cannot take its name.");
        }
        LiteralArgumentBuilder<CommandSourceStack> tree = tree(context);
        ((Access.CommandTree) dispatcher.getRoot()).blossom$removeChild(app);
        dispatcher.register(tree);
        installed = true;
    }

    /**
     * Puts every app's tree in a dispatcher that was just built.
     *
     * <p>{@code /reload} makes a new dispatcher with the game's commands only: without this, an app
     * loaded before it would lose its command and keep running without it. The players get the new
     * tree from the game itself, when the reload is over.
     */
    public static void installAll(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context)
    {
        for (AppCommands commands : APPS.values())
        {
            if (commands.entries.isEmpty())
            {
                continue;
            }
            commands.installed = false;
            try
            {
                commands.installInto(dispatcher, context);
            }
            catch (RuntimeException e)
            {
                com.gnottero.blossom.util.AppErrors.report(commands.app, "registering its command", e);
            }
        }
    }

    /** Takes the app's command away: on unload, and before a reload brings it back with the new code. */
    public static void uninstall(@Nullable MinecraftServer server, String app)
    {
        AppCommands commands = APPS.remove(app);
        if (commands == null || !commands.installed || server == null)
        {
            return;
        }
        ((Access.CommandTree) server.getCommands().getDispatcher().getRoot()).blossom$removeChild(app);
        notifyPlayers(server);
    }

    /** Forgets everything: the server is stopping. */
    public static void clear()
    {
        APPS.clear();
    }

    private void reinstallIfLive()
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (installed && runtime != null)
        {
            install(runtime.server(), app);
        }
    }

    private static void notifyPlayers(MinecraftServer server)
    {
        // at the next tick, not now: a load can happen inside a command, and resending the tree
        // while the dispatcher is still executing would hand the client a half-built one
        server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player)));
    }

    // --- running ---

    private int execute(CommandContext<CommandSourceStack> context, Entry entry) throws CommandSyntaxException
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (runtime == null || runtime.stopAll)
        {
            throw CommandArgument.error("Scripts are stopped on this server: /blossom script resume starts them again");
        }
        List<Value> args = new ArrayList<>();
        for (String name : entry.arguments())
        {
            args.add(CommandArgument.value(context, name, this));
        }
        args.addAll(entry.fixed());
        CommandSourceStack source = context.getSource();
        try
        {
            Value result = call(source, entry.function(), args);
            if (result instanceof final NumericValue number)
            {
                return (int) number.getLong();
            }
            return result.getBoolean() ? 1 : 0;
        }
        catch (BlossomError e)
        {
            source.sendFailure(Component.literal(e.format()));
        }
        catch (ExpressionException e)
        {
            source.sendFailure(Component.literal(e.getMessage()));
        }
        catch (ArithmeticException | IllegalArgumentException e)
        {
            source.sendFailure(Component.literal(String.valueOf(e.getMessage())));
        }
        catch (StackOverflowError e)
        {
            source.sendFailure(Component.literal("The command of '" + app + "' went too deep"));
        }
        return 0;
    }

    /** Calls a function of the app on behalf of {@code source}, with the limits of a command typed by hand. */
    Value call(CommandSourceStack source, FunctionValue function, List<Value> args)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (runtime == null)
        {
            throw new IllegalArgumentException("Blossom is not ready on this server yet");
        }
        return runtime.call(app, source, function, args);
    }
}
