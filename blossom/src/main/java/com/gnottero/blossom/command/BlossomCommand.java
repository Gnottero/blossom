package com.gnottero.blossom.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import blossom.exception.BlossomError;
import blossom.exception.ExpressionException;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptRuntime;
import blossom.value.Value;

/**
 * The tree of the {@code /blossom} command.
 *
 * <p>{@code script} runs and manages apps ({@code run}, {@code in}, {@code load}, {@code reload},
 * {@code unload}, {@code list}) and carries Scarpet's tools for working on the world and on an app:
 * {@code scan}, {@code fill}, {@code outline}, {@code invoke}, {@code globals}, {@code event},
 * {@code stop}, {@code resume} and {@code explain}, implemented in {@link ScriptTools}. {@code pack},
 * {@code data} and {@code profile} are the rest. The apps' own commands are not here: each lives
 * under its own name, see {@link AppCommands}.
 */
public final class BlossomCommand
{
    private static final SuggestionProvider<CommandSourceStack> LOADED_APPS = (context, builder) ->
            SharedSuggestionProvider.suggest(withRuntime(context.getSource(), ScriptRuntime::loadedApps), builder);

    private static final SuggestionProvider<CommandSourceStack> AVAILABLE_APPS = (context, builder) ->
            SharedSuggestionProvider.suggest(withRuntime(context.getSource(), ScriptRuntime::availableApps), builder);

    private BlossomCommand()
    {
    }

    private static final SuggestionProvider<CommandSourceStack> APP_FUNCTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(withRuntime(context.getSource(), runtime -> {
                blossom.interpreter.Interpreter interpreter = runtime.interpreterOf(appOf(context));
                return interpreter == null ? List.of() : ScriptTools.functionNames(interpreter.host());
            }), builder);

    private static final SuggestionProvider<CommandSourceStack> EVENTS = (context, builder) ->
            SharedSuggestionProvider.suggest(com.gnottero.blossom.event.Event.names(), builder);

    private static final SuggestionProvider<CommandSourceStack> EXPLAIN_STYLES = (context, builder) ->
            SharedSuggestionProvider.suggest(ScriptTools.EXPLAIN_STYLES, builder);

    private static String appOf(CommandContext<CommandSourceStack> context)
    {
        try
        {
            return StringArgumentType.getString(context, "app");
        }
        catch (IllegalArgumentException none)
        {
            return null;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext)
    {
        dispatcher.register(Commands.literal("blossom")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("script")
                        .then(Commands.literal("run")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> run(context, null))))
                        .then(areaCommands(Commands.literal("scan"), "scan", buildContext))
                        .then(areaCommands(Commands.literal("fill"), "fill", buildContext))
                        .then(areaCommands(Commands.literal("outline"), "outline", buildContext))
                        .then(explainCommands(Commands.literal("explain")))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context, true)))
                        .then(Commands.literal("resume")
                                .executes(context -> stop(context, false)))
                        .then(Commands.literal("event")
                                .executes(context -> attempt(context, () -> ScriptTools.listEvents(context.getSource())))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                                                .then(Commands.argument("app", StringArgumentType.word()).suggests(LOADED_APPS)
                                                        .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                                                .executes(BlossomCommand::addEvent)))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                                                .then(Commands.argument("app", StringArgumentType.word()).suggests(LOADED_APPS)
                                                        .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                                                .executes(BlossomCommand::removeEvent))))))
                        .then(Commands.literal("in")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        // the words below win over the code when they are typed alone: an
                                        // expression that starts with 'scan ' followed by a space has to be
                                        // written with /blossom script run inside the app instead
                                        .then(areaCommands(Commands.literal("scan"), "scan", buildContext))
                                        .then(areaCommands(Commands.literal("fill"), "fill", buildContext))
                                        .then(areaCommands(Commands.literal("outline"), "outline", buildContext))
                                        .then(explainCommands(Commands.literal("explain")))
                                        .then(Commands.literal("globals")
                                                .executes(context -> attempt(context, () -> ScriptTools.globals(
                                                        requireRuntime(context.getSource()), context.getSource(), appOf(context)))))
                                        .then(Commands.literal("invoke")
                                                .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                                        .executes(context -> invoke(context, List.of(), ""))
                                                        .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                                                .executes(context -> invoke(context, List.of(),
                                                                        StringArgumentType.getString(context, "arguments"))))))
                                        .then(Commands.literal("invokepoint")
                                                .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                                        .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                                                .executes(context -> invoke(context, List.of(pos(context, "origin")), ""))
                                                                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                                                        .executes(context -> invoke(context, List.of(pos(context, "origin")),
                                                                                StringArgumentType.getString(context, "arguments")))))))
                                        .then(Commands.literal("invokearea")
                                                .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                        .executes(context -> invoke(context,
                                                                                List.of(pos(context, "from"), pos(context, "to")), ""))
                                                                        .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                                                                .executes(context -> invoke(context,
                                                                                        List.of(pos(context, "from"), pos(context, "to")),
                                                                                        StringArgumentType.getString(context, "arguments"))))))))
                                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                                .executes(context -> run(context, StringArgumentType.getString(context, "app"))))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::load)))
                        .then(Commands.literal("reload")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::load)))
                        .then(Commands.literal("unload")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::unload)))
                        .then(Commands.literal("list")
                                .executes(BlossomCommand::list)))
                .then(Commands.literal("pack")
                        .then(Commands.literal("update")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::packUpdate))))
                .then(Commands.literal("data")
                        .then(Commands.literal("generate")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::dataGenerate)))
                        .then(Commands.literal("list")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::dataList)))
                        .then(Commands.literal("export")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(AVAILABLE_APPS)
                                        .executes(BlossomCommand::dataExport))))
                .then(Commands.literal("profile")
                        .then(Commands.literal("run")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> profileRun(context, null))))
                        .then(Commands.literal("in")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                                .executes(context -> profileRun(context, StringArgumentType.getString(context, "app"))))))
                        .then(Commands.literal("start")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::profileStart)))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
                                        .executes(BlossomCommand::profileStop)))));
    }

    // --- scan, fill, outline, invoke, events, stop, explain ---

    /** {@code <origin> <from> <to> <expression>}, and for fill and outline {@code <block> [replace <filter>]}. */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> areaCommands(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal, String mode, CommandBuildContext buildContext)
    {
        var to = Commands.argument("to", BlockPosArgument.blockPos());
        if (mode.equals("scan"))
        {
            to.then(Commands.argument("expression", StringArgumentType.greedyString())
                    .executes(context -> attempt(context, () -> ScriptTools.scan(requireRuntime(context.getSource()),
                            context.getSource(), appOf(context), pos(context, "origin"), pos(context, "from"), pos(context, "to"),
                            StringArgumentType.getString(context, "expression")))));
        }
        else
        {
            boolean outline = mode.equals("outline");
            to.then(Commands.argument("expression", StringArgumentType.string())
                    .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                            .executes(context -> fill(context, null, outline))
                            .then(Commands.literal("replace")
                                    .then(Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext))
                                            .executes(context -> fill(context,
                                                    BlockPredicateArgument.getBlockPredicate(context, "filter"), outline))))));
        }
        return literal.then(Commands.argument("origin", BlockPosArgument.blockPos())
                .then(Commands.argument("from", BlockPosArgument.blockPos()).then(to)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> explainCommands(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal)
    {
        return literal
                // without arguments: the app's own source, as the parser reads it
                .executes(context -> explain(context, null, null, null))
                .then(Commands.literal("expression")
                        .then(Commands.argument("style", StringArgumentType.word()).suggests(EXPLAIN_STYLES)
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> explain(context, StringArgumentType.getString(context, "code"), null,
                                                StringArgumentType.getString(context, "style"))))))
                .then(Commands.literal("function")
                        .then(Commands.argument("function", StringArgumentType.word()).suggests(APP_FUNCTIONS)
                                .executes(context -> explain(context, null, StringArgumentType.getString(context, "function"), null))))
                .then(Commands.literal("source")
                        .then(Commands.argument("style", StringArgumentType.word()).suggests(EXPLAIN_STYLES)
                                .executes(context -> explain(context, null, null, StringArgumentType.getString(context, "style")))));
    }

    private static net.minecraft.core.BlockPos pos(CommandContext<CommandSourceStack> context, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        return BlockPosArgument.getSpawnablePos(context, name);
    }

    private static int fill(CommandContext<CommandSourceStack> context,
            java.util.function.@org.jspecify.annotations.Nullable Predicate<net.minecraft.world.level.block.state.pattern.BlockInWorld> filter,
            boolean outline) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        net.minecraft.core.BlockPos origin = pos(context, "origin");
        net.minecraft.core.BlockPos from = pos(context, "from");
        net.minecraft.core.BlockPos to = pos(context, "to");
        return attempt(context, () -> ScriptTools.fill(requireRuntime(context.getSource()), context.getSource(), appOf(context),
                origin, from, to, StringArgumentType.getString(context, "expression"),
                BlockStateArgument.getBlock(context, "block"), filter, outline));
    }

    private static int invoke(CommandContext<CommandSourceStack> context, List<net.minecraft.core.BlockPos> positions, String arguments)
    {
        return attempt(context, () -> {
            Value result = ScriptTools.invoke(requireRuntime(context.getSource()), context.getSource(), appOf(context),
                    StringArgumentType.getString(context, "function"), positions, arguments);
            context.getSource().sendSuccess(() -> Component.literal(" -> " + result.getPrettyString()), false);
            return result instanceof final blossom.value.NumericValue number ? (int) number.getLong() : result.getBoolean() ? 1 : 0;
        });
    }

    private static int explain(CommandContext<CommandSourceStack> context, String code, String function, String style)
    {
        return attempt(context, () -> ScriptTools.explain(requireRuntime(context.getSource()), context.getSource(),
                appOf(context), code, function, style));
    }

    private static int addEvent(CommandContext<CommandSourceStack> context)
    {
        String event = StringArgumentType.getString(context, "event");
        String app = appOf(context);
        String function = StringArgumentType.getString(context, "function");
        return attempt(context, () -> {
            ScriptTools.addEvent(requireRuntime(context.getSource()), context.getSource(), event, app, function);
            context.getSource().sendSuccess(() -> Component.literal(function + " of '" + app + "' now handles " + event), true);
            return 1;
        });
    }

    private static int removeEvent(CommandContext<CommandSourceStack> context)
    {
        String event = StringArgumentType.getString(context, "event");
        String app = appOf(context);
        String function = StringArgumentType.getString(context, "function");
        return attempt(context, () -> {
            int removed = com.gnottero.blossom.event.EventBus.eventOrThrow(event).removeHandler(app, function);
            if (removed == 0)
            {
                return fail(context, function + " of '" + app + "' does not handle " + event);
            }
            context.getSource().sendSuccess(() -> Component.literal(function + " of '" + app + "' no longer handles " + event), true);
            return removed;
        });
    }

    private static int stop(CommandContext<CommandSourceStack> context, boolean stop)
    {
        requireRuntime(context.getSource()).stop(stop);
        context.getSource().sendSuccess(() -> Component.literal(stop
                ? "Every script on the server is stopped: running code ends at its next check, events and scheduled"
                        + " calls wait. /blossom script resume lets them run again."
                : "Scripts run again"), true);
        return 1;
    }

    /** Runs a command body, turning the errors of a script into a message instead of a stack trace. */
    private static int attempt(CommandContext<CommandSourceStack> context, CommandBody body)
    {
        try
        {
            return body.run();
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e)
        {
            return fail(context, e.getMessage());
        }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException e)
        {
            return fail(context, e.getMessage());
        }
        catch (StackOverflowError e)
        {
            return fail(context, "the script went too deep");
        }
    }

    @FunctionalInterface
    private interface CommandBody
    {
        int run() throws com.mojang.brigadier.exceptions.CommandSyntaxException;
    }

    /**
     * Resolves the dependency versions again and reloads the pack.
     *
     * <p>It is the only way to make {@code blossom.lock} change its mind: without it a constraint
     * like {@code ^1.0.0} is resolved once and the choice stays, which is exactly why the lock
     * exists.
     */
    /**
     * Generates the game content of an app, by calling its {@code generate()}.
     *
     * <p>A convention instead of a manifest field: the generator is ordinary app code, and all the
     * command needs to know is where to start. An app without {@code generate()} says so, instead of
     * silently doing nothing.
     */
    private static int dataGenerate(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        java.nio.file.Path root = runtime.scriptDirectory().resolve(app);
        if (!java.nio.file.Files.isDirectory(root))
        {
            context.getSource().sendFailure(Component.literal(
                    "'" + app + "' is a single file, and content lives in a pack's data/ folder."));
            return 0;
        }
        try
        {
            com.gnottero.blossom.content.Generators.Report report =
                    com.gnottero.blossom.content.Generators.generate(root,
                            () -> runtime.runIn(context.getSource(), app, "generate()"));
            context.getSource().sendSuccess(() -> Component.literal(
                    report.written() + " resources written in data/"
                            + (report.removed() > 0 ? ", " + report.removed() + " stale ones removed" : "")
                            + ". Run /reload to apply them; a new registry entry or worldgen needs a restart."), true);
            return report.written();
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (Exception e)
        {
            return fail(context, "Could not generate the content of '" + app + "': " + e.getMessage());
        }
    }

    /** What is in the data/ of a pack, and who wrote it. */
    private static int dataList(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            java.util.List<com.gnottero.blossom.content.ContentExport.Entry> entries =
                    com.gnottero.blossom.content.ContentExport.list(runtime.scriptDirectory().resolve(app));
            if (entries.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.literal(
                        "'" + app + "' carries no game content: it has no data/ folder."), false);
                return 0;
            }
            long generated = entries.stream().filter(
                    com.gnottero.blossom.content.ContentExport.Entry::generated).count();
            context.getSource().sendSuccess(() -> Component.literal(
                    entries.size() + " files in the data/ of '" + app + "', "
                            + generated + " of them generated:"), false);
            for (com.gnottero.blossom.content.ContentExport.Entry entry : entries)
            {
                // the reader has to tell at a glance what the next generation will rewrite and
                // what they wrote themselves
                context.getSource().sendSuccess(() -> Component.literal(
                        "  " + (entry.generated() ? "generated  " : "by hand    ") + entry.path()), false);
            }
            return entries.size();
        }
        catch (Exception e)
        {
            return fail(context, "Could not read the content of '" + app + "': " + e.getMessage());
        }
    }

    /** The zip of the content, for whoever does not have the mod. */
    private static int dataExport(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            java.nio.file.Path zip =
                    com.gnottero.blossom.content.ContentExport.export(runtime.scriptDirectory(), app);
            java.nio.file.Path shown = runtime.scriptDirectory().getParent().relativize(zip);
            context.getSource().sendSuccess(() -> Component.literal(
                    "The content of '" + app + "' is a datapack in " + shown
                            + ": it works on a server without Blossom."), true);
            return 1;
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int packUpdate(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.update(context.getSource(), app);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Dependencies of '" + app + "' re-resolved and " + blossom.project.LockFile.FILE_NAME + " updated"), true);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    // --- profiling ---

    private static int profileRun(CommandContext<CommandSourceStack> context, String app)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String code = StringArgumentType.getString(context, "code");
        try
        {
            blossom.interpreter.Profiler profiler = runtime.profileRun(context.getSource(), app, code);
            String what = app == null ? "an expression" : "'" + app + "'";
            context.getSource().sendSuccess(() -> Component.literal(ProfileReport.format(profiler, what)), false);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int profileStart(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.startProfiling(app);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Profiling started on '" + app + "'. From now on it counts everything the app does, events"
                            + " included. Close it with /blossom profile stop " + app), false);
            return 1;
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int profileStop(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            blossom.interpreter.Profiler profiler = runtime.stopProfiling(app);
            context.getSource().sendSuccess(() -> Component.literal(ProfileReport.format(profiler, "'" + app + "'")), false);
            return 1;
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return fail(context, e.getMessage());
        }
    }

    private static int run(CommandContext<CommandSourceStack> context, String app)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String code = StringArgumentType.getString(context, "code");
        try
        {
            Value result = app == null
                    ? runtime.runOneShot(context.getSource(), code)
                    : runtime.runIn(context.getSource(), app, code);
            context.getSource().sendSuccess(() -> Component.literal(" -> " + result.getPrettyString()), false);
            return result.getBoolean() ? 1 : 0;
        }
        catch (BlossomError e)
        {
            // format() carries category, position, cross module stack and suggestion: that is the
            // whole point of typed errors, and in chat it matters more than anywhere else
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (IllegalArgumentException | ArithmeticException e)
        {
            return fail(context, e.getMessage());
        }
        catch (StackOverflowError e)
        {
            return fail(context, "the script went too deep");
        }
    }

    private static int load(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        try
        {
            runtime.load(context.getSource(), app);
            context.getSource().sendSuccess(() -> Component.literal("Loaded app '" + app + "'"), true);
            return 1;
        }
        catch (BlossomError e)
        {
            return fail(context, e.format());
        }
        catch (ExpressionException e)
        {
            return fail(context, e.getMessage());
        }
        catch (Exception e)
        {
            return fail(context, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static int unload(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        String app = StringArgumentType.getString(context, "app");
        if (!runtime.unload(app))
        {
            return fail(context, "App '" + app + "' is not loaded");
        }
        context.getSource().sendSuccess(() -> Component.literal("Unloaded app '" + app + "'"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context)
    {
        ScriptRuntime runtime = requireRuntime(context.getSource());
        List<String> loaded = runtime.loadedApps();
        List<String> available = runtime.availableApps();
        context.getSource().sendSuccess(() -> Component.literal(
                "Loaded: " + (loaded.isEmpty() ? "none" : String.join(", ", loaded))
                        + "\nOn disk: " + (available.isEmpty() ? "none" : String.join(", ", available))), false);
        return loaded.size();
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message)
    {
        context.getSource().sendFailure(Component.literal(message == null ? "unknown error" : message));
        return 0;
    }

    private static ScriptRuntime requireRuntime(CommandSourceStack source)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        if (runtime == null)
        {
            throw new IllegalStateException("Blossom is not ready on this server yet");
        }
        return runtime;
    }

    private static <T> List<T> withRuntime(CommandSourceStack source, java.util.function.Function<ScriptRuntime, List<T>> query)
    {
        ScriptRuntime runtime = BlossomMod.runtime();
        return runtime == null ? List.of() : query.apply(runtime);
    }
}
