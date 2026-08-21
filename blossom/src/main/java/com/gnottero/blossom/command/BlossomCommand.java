package com.gnottero.blossom.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import blossom.exception.BlossomError;
import blossom.exception.ExpressionException;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptRuntime;
import blossom.value.Value;

/**
 * L'albero del comando {@code /blossom}.
 *
 * <p>Fase 2: il minimo per avere una pipeline che va dal comando al valutatore e torna indietro con
 * un risultato - {@code script run}, {@code script load}, {@code script reload}, {@code script list}
 * e {@code script unload}. Il resto (profiling, progetti multi-file) arriva con le fasi che lo
 * rendono sensato.
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("blossom")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("script")
                        .then(Commands.literal("run")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> run(context, null))))
                        .then(Commands.literal("in")
                                .then(Commands.argument("app", StringArgumentType.word())
                                        .suggests(LOADED_APPS)
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
                                        .executes(BlossomCommand::dataGenerate))))
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

    /**
     * Rifà la scelta delle versioni delle dipendenze e ricarica il pack.
     *
     * <p>È l'unico modo di far cambiare idea al {@code blossom.lock}: senza, un vincolo come
     * {@code ^1.0.0} viene risolto una volta e poi la scelta resta, che è appunto il motivo per cui
     * il lock esiste.
     */
    /**
     * Genera il contenuto di gioco di un'app, chiamando la sua {@code generate()}.
     *
     * <p>La convenzione al posto di un campo nel manifesto: il generatore e' codice normale
     * dell'app, e quello che il comando deve sapere e' solo da dove cominciare. Un'app senza
     * {@code generate()} lo dice, invece di non fare niente in silenzio.
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
            // format() porta categoria, posizione, stack fra i moduli e suggerimento: e' tutto il
            // punto degli errori tipizzati, e in chat serve piu' che altrove
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
