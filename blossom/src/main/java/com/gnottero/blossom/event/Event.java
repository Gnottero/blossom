package com.gnottero.blossom.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptContext;
import blossom.value.FunctionValue;
import blossom.value.Value;

/**
 * Un evento del gioco a cui uno script si puo' agganciare.
 *
 * <p>Ogni evento e' una costante di questa classe, con il suo nome, quanti argomenti passa e se puo'
 * essere annullato. Il catalogo e' chiuso: registrarsi a un nome che non e' qui e' un errore di
 * caricamento, non un handler che non scatta mai e nessuno capisce perche'.
 *
 * <p>Un evento tiene la propria lista di handler, quindi generarne uno che nessuno ascolta costa una
 * lettura di campo e un {@code isEmpty()}: i mixin chiamano {@link #isListened()} prima ancora di
 * costruire gli argomenti, che e' la parte cara.
 *
 * <p>Un handler che restituisce un valore vero <b>annulla</b> l'azione, se l'evento e' annullabile.
 * Non serve nessuna stringa magica: il valore di ritorno e' gia' una risposta.
 */
public final class Event
{
    /** tutti gli eventi, nell'ordine in cui vale la pena leggerli */
    private static final Map<String, Event> BY_NAME = new LinkedHashMap<>();

    // --- ciclo di vita del server ---

    /** {@code ()} - il server ha finito di partire */
    public static final Event SERVER_STARTS = new Event("server_starts", 0, false);
    /** {@code ()} - il server sta per chiudere; e' l'ultimo momento utile per salvare */
    public static final Event SERVER_SHUTS_DOWN = new Event("server_shuts_down", 0, false);
    /**
     * {@code (app)} - un'app sta per essere ricaricata, e non e' ancora stata smontata
     *
     * <p>E' l'ultimo momento in cui gli handler della versione vecchia esistono ancora: chi vuole
     * salvare qualcosa prima di sparire lo salva qui.
     */
    public static final Event APP_RELOADING = new Event("app_reloading", 1, false);
    /**
     * {@code (app)} - un'app e' stata ricaricata, e il suo codice nuovo ha gia' girato
     *
     * <p>Scatta dopo il corpo dei moduli, quindi la versione nuova puo' ascoltare il proprio
     * ricaricamento: registra l'handler nel preambolo e lo trova gia' registrato quando l'evento
     * arriva.
     */
    public static final Event APP_RELOADED = new Event("app_reloaded", 1, false);
    /** {@code ()} - un tick dell'overworld */
    public static final Event TICK = new Event("tick", 0, false);
    /** {@code ()} - un tick, con il contesto del nether */
    public static final Event TICK_NETHER = new Event("tick_nether", 0, false);
    /** {@code ()} - un tick, con il contesto dell'end */
    public static final Event TICK_ENDER = new Event("tick_ender", 0, false);

    // --- giocatore: movimento e stato ---

    /** {@code (player)} */
    public static final Event PLAYER_JUMPS = new Event("player_jumps", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_DEPLOYS_ELYTRA = new Event("player_deploys_elytra", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_WAKES_UP = new Event("player_wakes_up", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_ESCAPES_SLEEP = new Event("player_escapes_sleep", 1, false);
    /** {@code (player, strafe, forward, jumping, sneaking)} - comandi dati alla cavalcatura */
    public static final Event PLAYER_RIDES = new Event("player_rides", 5, false);
    /** {@code (player)} */
    public static final Event PLAYER_STARTS_SNEAKING = new Event("player_starts_sneaking", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_STOPS_SNEAKING = new Event("player_stops_sneaking", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_STARTS_SPRINTING = new Event("player_starts_sprinting", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_STOPS_SPRINTING = new Event("player_stops_sprinting", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_SWAPS_HANDS = new Event("player_swaps_hands", 1, true);
    /** {@code (player, hand)} */
    public static final Event PLAYER_SWINGS_HAND = new Event("player_swings_hand", 2, false);
    /** {@code (player, from, to)} - lo slot della hotbar selezionato */
    public static final Event PLAYER_SWITCHES_SLOT = new Event("player_switches_slot", 3, false);

    // --- giocatore: blocchi e oggetti ---

    /** {@code (player, item, hand)} */
    public static final Event PLAYER_USES_ITEM = new Event("player_uses_item", 3, true);
    /** {@code (player, block, facing)} - click sinistro su un blocco */
    public static final Event PLAYER_CLICKS_BLOCK = new Event("player_clicks_block", 3, true);
    /** {@code (player, item, hand, block, face, hitvec)} - prima che il blocco decida */
    public static final Event PLAYER_RIGHT_CLICKS_BLOCK = new Event("player_right_clicks_block", 6, true);
    /** {@code (player, hand, block, face, hitvec)} - il blocco ha risposto */
    public static final Event PLAYER_INTERACTS_WITH_BLOCK = new Event("player_interacts_with_block", 5, false);
    /** {@code (player, item, hand, block)} - prima che il blocco venga piazzato */
    public static final Event PLAYER_PLACING_BLOCK = new Event("player_placing_block", 4, true);
    /** {@code (player, item, hand, block)} - il blocco e' stato piazzato */
    public static final Event PLAYER_PLACES_BLOCK = new Event("player_places_block", 4, false);
    /** {@code (player, block)} */
    public static final Event PLAYER_BREAKS_BLOCK = new Event("player_breaks_block", 2, true);
    /** {@code (player, item, hand)} */
    public static final Event PLAYER_RELEASES_ITEM = new Event("player_releases_item", 3, true);
    /** {@code (player, item, hand)} */
    public static final Event PLAYER_FINISHES_USING_ITEM = new Event("player_finishes_using_item", 3, true);
    /** {@code (player, item)} */
    public static final Event PLAYER_PICKS_UP_ITEM = new Event("player_picks_up_item", 2, false);
    /** {@code (player)} - ha lasciato cadere un oggetto */
    public static final Event PLAYER_DROPS_ITEM = new Event("player_drops_item", 1, true);
    /** {@code (player)} - ha lasciato cadere una pila intera */
    public static final Event PLAYER_DROPS_STACK = new Event("player_drops_stack", 1, true);
    /** {@code (player, recipe, full_stack)} */
    public static final Event PLAYER_CHOOSES_RECIPE = new Event("player_chooses_recipe", 3, true);

    // --- giocatore: entita' e danni ---

    /** {@code (player, entity, hand)} */
    public static final Event PLAYER_INTERACTS_WITH_ENTITY = new Event("player_interacts_with_entity", 3, true);
    /** {@code (player, entity)} */
    public static final Event PLAYER_ATTACKS_ENTITY = new Event("player_attacks_entity", 2, true);
    /** {@code (player, entity)} */
    public static final Event PLAYER_COLLIDES_WITH_ENTITY = new Event("player_collides_with_entity", 2, false);
    /** {@code (player, merchant, buy, buy_b, sell)} */
    public static final Event PLAYER_TRADES = new Event("player_trades", 5, false);
    /** {@code (player, amount, source, source_entity)} */
    public static final Event PLAYER_TAKES_DAMAGE = new Event("player_takes_damage", 4, true);
    /** {@code (player, amount, entity)} */
    public static final Event PLAYER_DEALS_DAMAGE = new Event("player_deals_damage", 3, true);
    /** {@code (player)} */
    public static final Event PLAYER_DIES = new Event("player_dies", 1, false);
    /** {@code (player)} */
    public static final Event PLAYER_RESPAWNS = new Event("player_respawns", 1, false);
    /** {@code (player, from_pos, to_pos, from_dimension, to_dimension)} */
    public static final Event PLAYER_CHANGES_DIMENSION = new Event("player_changes_dimension", 5, false);

    // --- giocatore: sessione e chat ---

    /** {@code (player)} */
    public static final Event PLAYER_CONNECTS = new Event("player_connects", 1, false);
    /** {@code (player, reason)} */
    public static final Event PLAYER_DISCONNECTS = new Event("player_disconnects", 2, false);
    /** {@code (player, message)} */
    public static final Event PLAYER_MESSAGE = new Event("player_message", 2, true);
    /**
     * {@code (player, command)} - un comando e' stato inviato dalla chat
     *
     * <p>Non annullabile: scatta a comando gia' accettato, e restituire vero da qui non lo ferma.
     * Era dichiarato annullabile e non lo era: una bandiera che non fa niente e' peggio di una
     * assente, perche' chi la legge scrive codice che crede di funzionare.
     */
    public static final Event PLAYER_COMMAND = new Event("player_command", 2, false);
    /** {@code (player, category, event, value)} */
    public static final Event STATISTIC = new Event("statistic", 4, false);

    // --- mondo ---

    /** {@code (pos, skeleton_horse)} - un fulmine e' caduto; il secondo argomento e' un booleano */
    public static final Event LIGHTNING = new Event("lightning", 2, false);
    /** {@code (pos, power, source, attacker, block_interaction, create_fire)} - prima dello scoppio */
    public static final Event EXPLOSION = new Event("explosion", 6, true);
    /** {@code (pos, power, source, attacker, block_interaction, create_fire, blocks, entities)} */
    public static final Event EXPLOSION_OUTCOME = new Event("explosion_outcome", 8, false);
    /** {@code (pos, item)} - un dispenser sta per attivarsi, con l'oggetto che sta per lanciare */
    public static final Event BLOCK_DISPENSES = new Event("block_dispenses", 2, true);
    /** {@code (x, z)} - un chunk e' stato scaricato */
    public static final Event CHUNK_UNLOADED = new Event("chunk_unloaded", 2, false);
    /** {@code (x, z)} - un chunk e' stato generato per la prima volta */
    public static final Event CHUNK_GENERATED = new Event("chunk_generated", 2, false);
    /** {@code (x, z, generato)} - un chunk e' entrato in memoria, appena creato o riletto dal disco */
    public static final Event CHUNK_LOADED = new Event("chunk_loaded", 3, false);
    /** {@code (entity)} - un'entita' e' comparsa nel mondo: caricata da disco, generata o evocata */
    public static final Event ENTITY_LOADS = new Event("entity_loads", 1, false);
    /** {@code (pos, direction, entity)} - una campana e' stata suonata; l'entita' e' null se e' stata la redstone */
    public static final Event BELL_RINGS = new Event("bell_rings", 3, true);
    /** {@code (piston, facing, blocks)} - un pistone sta per estendersi, con i blocchi che spingera' */
    public static final Event PISTON_EXTENDS = new Event("piston_extends", 3, true);
    /** {@code (piston, facing, blocks)} - un pistone appiccicoso sta per ritrarsi, con quelli che tirera' */
    public static final Event PISTON_RETRACTS = new Event("piston_retracts", 3, true);
    /** {@code (previous_block, new_block)} - cemento, ostruzione, pietra o basalto stanno per formarsi */
    public static final Event BLOCK_FORMS = new Event("block_forms", 2, true);

    private final String name;
    private final int arity;
    private final boolean cancellable;
    /** un evento che non e' del gioco ma di un'app: nasce con handle_event, scatta con signal_event */
    private boolean custom = false;
    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<>();

    private Event(String name, int arity, boolean cancellable)
    {
        this.name = name;
        this.arity = arity;
        this.cancellable = cancellable;
        BY_NAME.put(name, this);
    }

    /**
     * Un handler registrato: la funzione, l'app a cui appartiene, e gli argomenti fissi in coda.
     *
     * @param filter se non {@code null}, decide guardando gli argomenti se questo handler va
     *               chiamato. Serve a chi ascolta un evento frequente ma solo per una parte dei casi
     *               - {@code entity_load_handler} su un tipo di entita' - e fa il lavoro in java,
     *               dove costa un confronto, invece che dentro l'handler, dove costa una chiamata
     */
    public record Handler(String app, Interpreter interpreter, CommandSourceStack source, FunctionValue function,
            List<Value> extraArguments, @Nullable String targetPlayer,
            java.util.function.@Nullable Predicate<List<Value>> filter)
    {
        public Handler(String app, Interpreter interpreter, CommandSourceStack source, FunctionValue function,
                List<Value> extraArguments, @Nullable String targetPlayer)
        {
            this(app, interpreter, source, function, extraArguments, targetPlayer, null);
        }
    }

    public String name()
    {
        return name;
    }

    /** Quanti argomenti l'evento passa alla funzione, esclusi quelli fissi dati alla registrazione. */
    public int arity()
    {
        return arity;
    }

    /** Se restituire un valore vero da un handler ferma l'azione del gioco. */
    public boolean cancellable()
    {
        return cancellable;
    }

    public static @Nullable Event byName(String name)
    {
        return BY_NAME.get(name);
    }

    /**
     * Dichiara un evento di applicazione, o restituisce quello gia' dichiarato.
     *
     * <p>Il catalogo del gioco resta chiuso; questi eventi invece li inventa chi scrive gli script,
     * e servono a far parlare due app senza che l'una conosca le funzioni dell'altra.
     *
     * @param arity quanti argomenti chi lo genera promette di passare
     */
    public static Event custom(String name, int arity)
    {
        Event existing = BY_NAME.get(name);
        if (existing != null)
        {
            return existing;
        }
        Event event = new Event(name, arity, false);
        event.custom = true;
        return event;
    }

    public static Collection<Event> all()
    {
        return BY_NAME.values();
    }

    public static Collection<String> names()
    {
        return BY_NAME.keySet();
    }

    public void add(Handler handler)
    {
        handlers.add(handler);
    }

    public void removeApp(String app)
    {
        handlers.removeIf(handler -> handler.app().equals(app));
    }

    public void removeApp(String app, @Nullable String targetPlayer)
    {
        handlers.removeIf(handler -> handler.app().equals(app)
                && (targetPlayer == null || targetPlayer.equals(handler.targetPlayer())));
    }

    public List<Handler> handlers()
    {
        return handlers;
    }

    /** @return se qualcuno ascolta: i mixin lo chiedono prima di costruire gli argomenti */
    public boolean isListened()
    {
        return !handlers.isEmpty();
    }

    /**
     * Chiama tutti gli handler.
     *
     * @param source chi "esegue" l'evento, per le funzioni che hanno bisogno di un mondo
     * @param arguments costruiti solo se c'e' davvero qualcuno che ascolta
     * @return se qualcuno ha chiesto di annullare l'azione, e l'evento e' annullabile
     */
    public boolean fire(CommandSourceStack source, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        List<Value> base = arguments.get();
        // l'arieta' dichiarata e' quella su cui si valida la firma di un handler: se chi genera
        // l'evento passa un numero diverso di argomenti, la validazione ha mentito e nessun handler
        // corretto puo' essere registrato. E' successo davvero con 'bell_rings', dichiarato con un
        // argomento mentre il mixin ne passava tre
        if (base.size() != arity)
        {
            BlossomMod.LOG.error("Event {} declares {} arguments but produces {}: this is a bug in the mod,"
                    + " not in the app", name, arity, base.size());
        }
        boolean cancelled = false;
        for (Handler handler : handlers)
        {
            if (handler.targetPlayer() != null && !isFor(handler.targetPlayer(), source))
            {
                continue;
            }
            if (handler.filter() != null && !handler.filter().test(base))
            {
                continue;
            }
            List<Value> call = handler.extraArguments().isEmpty() ? base : concat(base, handler.extraArguments());
            CommandSourceStack callSource = handler.source() == null ? source : source;
            try
            {
                // ogni evento e' un'esecuzione a se': il watchdog riparte da qui, altrimenti
                // un'app viva da ore avrebbe finito il budget al primo giorno
                Context context = new ScriptContext(handler.interpreter().host(), callSource).beginExecution();
                Value result = handler.function()
                        .callInContext(context, Context.Type.NONE, call)
                        .evalValue(context);
                cancelled |= cancellable && result != null && result.getBoolean();
            }
            catch (RuntimeException | StackOverflowError error)
            {
                com.gnottero.blossom.util.AppErrors.report(handler.app(), "event " + name, error);
            }
        }
        return cancelled;
    }

    /** Genera l'evento con la sorgente del giocatore: l'handler vede il suo mondo e la sua posizione. */
    public boolean fireForPlayer(ServerPlayer player, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        return fire(player.createCommandSourceStack(), arguments);
    }

    /** Genera l'evento con la sorgente del mondo dato. */
    public boolean fireInLevel(ServerLevel level, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        return fire(level.getServer().createCommandSourceStack().withLevel(level), arguments);
    }

    /** Genera l'evento con la sorgente del server. */
    public boolean fireOnServer(MinecraftServer server, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        return fire(server.createCommandSourceStack(), arguments);
    }

    private static boolean isFor(String playerName, CommandSourceStack source)
    {
        return source.getEntity() instanceof final ServerPlayer player
                && player.getScoreboardName().equals(playerName);
    }

    private static List<Value> concat(List<Value> base, List<Value> extra)
    {
        List<Value> all = new ArrayList<>(base.size() + extra.size());
        all.addAll(base);
        all.addAll(extra);
        return all;
    }

    /** Toglie da ogni evento gli handler dell'app: si chiama a ricaricamento e a scaricamento. */
    public static void unregisterApp(String app)
    {
        BY_NAME.values().forEach(event -> event.removeApp(app));
    }

    public static void clearAll()
    {
        BY_NAME.values().forEach(event -> event.handlers.clear());
    }
}
