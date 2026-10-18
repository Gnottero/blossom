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
 * A game event a script can hook into.
 *
 * <p>Each event is a constant of this class, with its name, how many arguments it passes and
 * whether it can be cancelled. The catalog is closed: registering on a name that is not here is a
 * loading error, not a handler that never fires and nobody understands why.
 *
 * <p>An event keeps its own list of handlers, so firing one that nobody listens to costs a field
 * read and an {@code isEmpty()}: the mixins call {@link #isListened()} before even building the
 * arguments, which is the expensive part.
 *
 * <p>A handler that returns a true value <b>cancels</b> the action, if the event is cancellable.
 * No magic string is needed: the return value is already an answer.
 */
public final class Event
{
    /** All events, in the order in which it is worth reading them. */
    private static final Map<String, Event> BY_NAME = new LinkedHashMap<>();

    // --- server lifecycle ---

    /** {@code ()} - the server has finished starting */
    public static final Event SERVER_STARTS = new Event("server_starts", 0, false);
    /** {@code ()} - the server is about to shut down; the last moment it is safe to save */
    public static final Event SERVER_SHUTS_DOWN = new Event("server_shuts_down", 0, false);
    /**
     * {@code (app)} - an app is about to be reloaded, and has not been torn down yet
     *
     * <p>This is the last moment the old version's handlers still exist: whoever wants to save
     * something before it disappears saves it here.
     */
    public static final Event APP_RELOADING = new Event("app_reloading", 1, false);
    /**
     * {@code (app)} - an app has been reloaded, and its new code has already run
     *
     * <p>Fires after the body of the modules, so the new version can listen for its own reload:
     * it registers the handler in the preamble and finds it already registered by the time the
     * event arrives.
     */
    public static final Event APP_RELOADED = new Event("app_reloaded", 1, false);
    /**
     * {@code (app)} - an app is about to be unloaded with {@code /blossom script unload}, and its
     * handlers still exist: what Scarpet's {@code __on_close} was, for an unload
     */
    public static final Event APP_UNLOADING = new Event("app_unloading", 1, false);
    /** {@code ()} - an overworld tick */
    public static final Event TICK = new Event("tick", 0, false);
    /** {@code ()} - a tick, with the nether's context */
    public static final Event TICK_NETHER = new Event("tick_nether", 0, false);
    /** {@code ()} - a tick, with the end's context */
    public static final Event TICK_ENDER = new Event("tick_ender", 0, false);

    // --- player: movement and state ---

    /** {@code (player)} */
    public static final Event PLAYER_JUMPS = new Event("player_jumps", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_DEPLOYS_ELYTRA = new Event("player_deploys_elytra", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_WAKES_UP = new Event("player_wakes_up", 1, true);
    /**
     * {@code (player)} - the leave-bed key while not actually asleep
     *
     * <p>Cancelling drops the leave-bed action. Said plainly: in this branch the game does nothing
     * today, so there is nothing visible to stop - the hook is real, the branch is empty.
     */
    public static final Event PLAYER_ESCAPES_SLEEP = new Event("player_escapes_sleep", 1, true);
    /** {@code (player, strafe, forward, jumping, sneaking)} - commands given to the mount */
    public static final Event PLAYER_RIDES = new Event("player_rides", 5, true);
    /** {@code (player)} */
    public static final Event PLAYER_STARTS_SNEAKING = new Event("player_starts_sneaking", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_STOPS_SNEAKING = new Event("player_stops_sneaking", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_STARTS_SPRINTING = new Event("player_starts_sprinting", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_STOPS_SPRINTING = new Event("player_stops_sprinting", 1, true);
    /** {@code (player)} */
    public static final Event PLAYER_SWAPS_HANDS = new Event("player_swaps_hands", 1, true);
    /** {@code (player, hand)} */
    public static final Event PLAYER_SWINGS_HAND = new Event("player_swings_hand", 2, true);
    /** {@code (player, from, to)} - the selected hotbar slot */
    public static final Event PLAYER_SWITCHES_SLOT = new Event("player_switches_slot", 3, true);

    // --- player: blocks and items ---

    /** {@code (player, item, hand)} */
    public static final Event PLAYER_USES_ITEM = new Event("player_uses_item", 3, true);
    /** {@code (player, block, facing)} - left click on a block */
    public static final Event PLAYER_CLICKS_BLOCK = new Event("player_clicks_block", 3, true);
    /** {@code (player, item, hand, block, face, hitvec)} - before the block decides */
    public static final Event PLAYER_RIGHT_CLICKS_BLOCK = new Event("player_right_clicks_block", 6, true);
    /**
     * {@code (player, hand, block, face, hitvec)} - the block has responded
     *
     * <p>Cancelling cannot take back the block's answer, which has already been given: what it
     * stops is the tail of the interaction, the advancement and the arm swing everybody else sees.
     * To stop the interaction itself there is {@link #PLAYER_RIGHT_CLICKS_BLOCK}.
     */
    public static final Event PLAYER_INTERACTS_WITH_BLOCK = new Event("player_interacts_with_block", 5, true);
    /** {@code (player, item, hand, block)} - before the block is placed */
    public static final Event PLAYER_PLACING_BLOCK = new Event("player_placing_block", 4, true);
    /**
     * {@code (player, item, hand, block)} - the block has been placed
     *
     * <p>Cancelling puts back whatever was there before and stops everything the placement had
     * left to do: no sound, no advancement, and the item is not consumed.
     */
    public static final Event PLAYER_PLACES_BLOCK = new Event("player_places_block", 4, true);
    /** {@code (player, block)} */
    public static final Event PLAYER_BREAKS_BLOCK = new Event("player_breaks_block", 2, true);
    /** {@code (player, item, hand)} */
    public static final Event PLAYER_RELEASES_ITEM = new Event("player_releases_item", 3, true);
    /** {@code (player, item, hand)} */
    public static final Event PLAYER_FINISHES_USING_ITEM = new Event("player_finishes_using_item", 3, true);
    /**
     * {@code (player, item)} - an item is about to enter the inventory
     *
     * <p>Fires before the stack is put away, so that cancelling can leave it on the ground: the
     * item is therefore what is being offered, not what would have fit.
     */
    public static final Event PLAYER_PICKS_UP_ITEM = new Event("player_picks_up_item", 2, true);
    /** {@code (player)} - dropped an item */
    public static final Event PLAYER_DROPS_ITEM = new Event("player_drops_item", 1, true);
    /** {@code (player)} - dropped an entire stack */
    public static final Event PLAYER_DROPS_STACK = new Event("player_drops_stack", 1, true);
    /** {@code (player, recipe, full_stack)} */
    public static final Event PLAYER_CHOOSES_RECIPE = new Event("player_chooses_recipe", 3, true);
    /**
     * {@code (player, carried, other, slot, action)} - an item clicked onto another item in a
     * container, the gesture a bundle answers to, here for every item
     *
     * <p>Fires at the point where the game asks the two stacks whether they want to handle the
     * click themselves, before the ordinary swap: cancelling it means the click was handled and
     * nothing else happens, which is exactly what a bundle does when it swallows an item.
     */
    public static final Event PLAYER_CLICKS_ITEM_ON_ITEM = new Event("player_clicks_item_on_item", 5, true);

    // --- player: entities and damage ---

    /** {@code (player, entity, hand)} */
    public static final Event PLAYER_INTERACTS_WITH_ENTITY = new Event("player_interacts_with_entity", 3, true);
    /** {@code (player, entity)} */
    public static final Event PLAYER_ATTACKS_ENTITY = new Event("player_attacks_entity", 2, true);
    /** {@code (player, entity)} */
    public static final Event PLAYER_COLLIDES_WITH_ENTITY = new Event("player_collides_with_entity", 2, true);
    /**
     * {@code (player, merchant, buy, buy_b, sell)} - a trade is being taken from the result slot
     *
     * <p>Cancelling gives nothing to the player and leaves the cost items in place: the villager
     * is not notified and gains no experience, so the trade simply never happened.
     */
    public static final Event PLAYER_TRADES = new Event("player_trades", 5, true);
    /** {@code (player, amount, source, source_entity)} */
    public static final Event PLAYER_TAKES_DAMAGE = new Event("player_takes_damage", 4, true);
    /** {@code (player, amount, entity)} */
    public static final Event PLAYER_DEALS_DAMAGE = new Event("player_deals_damage", 3, true);
    /**
     * {@code (player)} - the player is dying
     *
     * <p>Not cancellable: the health is already at zero when this fires, so stopping the death
     * would mean also deciding how much life to hand back, which is a decision the event has no
     * business making. To keep somebody alive, cancel {@link #PLAYER_TAKES_DAMAGE}, which is asked
     * before the blow lands.
     */
    public static final Event PLAYER_DIES = new Event("player_dies", 1, false);
    /**
     * {@code (player)} - the player is coming back
     *
     * <p>Cancelling means they do not: they stay on the death screen and can ask again. It also
     * covers the return from the end, where cancelling keeps them there.
     */
    public static final Event PLAYER_RESPAWNS = new Event("player_respawns", 1, true);
    /**
     * {@code (player, from_pos, to_pos, from_dimension, to_dimension)} - before the move
     *
     * <p>{@code to_pos} is the destination the game has decided on, not a position read after the
     * fact: cancelling leaves the player exactly where they are.
     */
    public static final Event PLAYER_CHANGES_DIMENSION = new Event("player_changes_dimension", 5, true);

    // --- player: session and chat ---

    /**
     * {@code (player)} - the player has entered the world, with their data already loaded
     *
     * <p>Not cancellable, like {@link #PLAYER_DISCONNECTS}: a session is not an action of the
     * game to be stopped. Somebody who must not enter is turned away by whatever guards the door,
     * not by an event that lets them in and then throws them out.
     */
    public static final Event PLAYER_CONNECTS = new Event("player_connects", 1, false);
    /** {@code (player, reason)} */
    public static final Event PLAYER_DISCONNECTS = new Event("player_disconnects", 2, false);
    /** {@code (player, message)} */
    public static final Event PLAYER_MESSAGE = new Event("player_message", 2, true);
    /**
     * {@code (player, command)} - a command has been sent from chat
     *
     * <p>Fires on the server thread, on the body that runs the command and before it runs:
     * cancelling means the command is never carried out. The signature has already been checked by
     * then, which is the part that cannot be undone; the command itself has not started.
     */
    public static final Event PLAYER_COMMAND = new Event("player_command", 2, true);
    /** {@code (player, category, event, value)} - cancelling means the statistic is not counted */
    public static final Event STATISTIC = new Event("statistic", 4, true);

    // --- world ---

    /**
     * {@code (pos, skeleton_horse)} - lightning is about to strike; the second argument is a boolean
     *
     * <p>Cancelling means neither the bolt nor the skeleton horse trap that comes with it.
     */
    public static final Event LIGHTNING = new Event("lightning", 2, true);
    /** {@code (pos, power, source, attacker, block_interaction, create_fire)} - before the blast */
    public static final Event EXPLOSION = new Event("explosion", 6, true);
    /**
     * {@code (pos, power, source, attacker, block_interaction, create_fire, blocks, entities)}
     *
     * <p>It fires between the two halves of a blast: the entities have been hurt, the blocks have
     * only been chosen. Cancelling therefore leaves every one of them standing, and lights no
     * fires - which is a blast that is heard and felt but breaks nothing.
     */
    public static final Event EXPLOSION_OUTCOME = new Event("explosion_outcome", 8, true);
    /** {@code (pos, item)} - a dispenser is about to activate, with the item it is about to launch */
    public static final Event BLOCK_DISPENSES = new Event("block_dispenses", 2, true);
    /** {@code (x, z)} - a chunk has been unloaded */
    public static final Event CHUNK_UNLOADED = new Event("chunk_unloaded", 2, false);
    /** {@code (x, z)} - a chunk has been generated for the first time */
    public static final Event CHUNK_GENERATED = new Event("chunk_generated", 2, false);
    /** {@code (x, z, generated)} - a chunk has entered memory, either just created or read back from disk */
    public static final Event CHUNK_LOADED = new Event("chunk_loaded", 3, false);
    /**
     * {@code (entity)} - an entity has appeared in the world: loaded from disk, spawned or summoned
     *
     * <p>Cancelling discards the entity right after it appeared, which is the only "it must not be
     * here" available at this point. It is ignored for players, which cannot be discarded.
     */
    public static final Event ENTITY_LOADS = new Event("entity_loads", 1, true);
    /** {@code (pos, direction, entity)} - a bell has been rung; the entity is null if it was redstone */
    public static final Event BELL_RINGS = new Event("bell_rings", 3, true);
    /** {@code (piston, facing, blocks)} - a piston is about to extend, with the blocks it will push */
    public static final Event PISTON_EXTENDS = new Event("piston_extends", 3, true);
    /** {@code (piston, facing, blocks)} - a sticky piston is about to retract, with the blocks it will pull */
    public static final Event PISTON_RETRACTS = new Event("piston_retracts", 3, true);
    /** {@code (previous_block, new_block)} - concrete, obsidian, stone or basalt is about to form */
    public static final Event BLOCK_FORMS = new Event("block_forms", 2, true);

    private final String name;
    private final int arity;
    private final boolean cancellable;
    /** An event that is not from the game but from an app: born with handle_event, fired with signal_event. */
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
     * A registered handler: the function, the app it belongs to, and the fixed trailing arguments.
     *
     * @param filter if not {@code null}, decides by looking at the arguments whether this handler
     *               should be called. Used by whoever listens to a frequent event but only for
     *               part of the cases (e.g. {@code entity_load_handler} on a specific entity type),
     *               doing the work in java, where it costs a comparison, instead of inside the
     *               handler, where it costs a call
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

    /** How many arguments the event passes to the function, excluding the fixed ones given at registration. */
    public int arity()
    {
        return arity;
    }

    /** Whether returning a true value from a handler stops the game's action. */
    public boolean cancellable()
    {
        return cancellable;
    }

    public static @Nullable Event byName(String name)
    {
        return BY_NAME.get(name);
    }

    /**
     * Declares an application event, or returns the one already declared.
     *
     * <p>The game's catalog stays closed; these events instead are invented by whoever writes the
     * scripts, and let two apps talk to each other without one knowing the other's functions.
     *
     * @param arity how many arguments whoever fires it promises to pass
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

    /** The {@code event_priority} of each app that declared one in its manifest: 0 for the others. */
    private static final java.util.Map<String, Double> PRIORITY = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setPriority(String app, double priority)
    {
        if (priority == 0)
        {
            PRIORITY.remove(app);
        }
        else
        {
            PRIORITY.put(app, priority);
        }
    }

    private static double priorityOf(String app)
    {
        return PRIORITY.getOrDefault(app, 0.0);
    }

    /**
     * Adds a handler after those of equal or higher priority: the highest priority hears the event
     * first, and among equals the order is the one they were registered in.
     */
    public void add(Handler handler)
    {
        double priority = priorityOf(handler.app());
        int index = 0;
        while (index < handlers.size() && priorityOf(handlers.get(index).app()) >= priority)
        {
            index++;
        }
        handlers.add(index, handler);
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

    /**
     * Silenced events: {@code harvest} breaks the block the way the game does, so drops and
     * statistics stay the real ones, but it is not an action of the player and no event must
     * fire. Server thread only, with guaranteed restore.
     */
    private static boolean suppressed = false;

    /** Runs {@code action} without firing any event. */
    public static boolean suppressing(java.util.function.BooleanSupplier action)
    {
        boolean previous = suppressed;
        suppressed = true;
        try
        {
            return action.getAsBoolean();
        }
        finally
        {
            suppressed = previous;
        }
    }

    /** @return whether someone is listening: the mixins ask this before building the arguments */
    public boolean isListened()
    {
        return !suppressed && !handlers.isEmpty();
    }

    /**
     * Calls all the handlers.
     *
     * @param source who "runs" the event, for functions that need a world
     * @param arguments built only if someone is actually listening
     * @return whether someone asked to cancel the action, and the event is cancellable
     */
    public boolean fire(CommandSourceStack source, Supplier<List<Value>> arguments)
    {
        if (suppressed || handlers.isEmpty() || blossom.interpreter.ExecutionBudget.isHalted())
        {
            return false;
        }
        List<Value> base = arguments.get();
        // the declared arity is what a handler's signature is validated against: if whoever fires
        // the event passes a different number of arguments, validation has lied and no correct
        // handler can be registered. This actually happened with 'bell_rings', declared with one
        // argument while the mixin passed three
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
                // each event is its own execution: the watchdog restarts from here, otherwise an
                // app alive for hours would run out of budget on the first day
                Context context = new ScriptContext(handler.interpreter().host(), callSource).beginExecution();
                Value result = handler.function()
                        .callInContext(context, Context.Type.NONE, call)
                        .evalValue(context);
                cancelled |= cancellable && result != null && result.getBoolean();
                if (cancelled)
                {
                    // the action will not happen: who comes after (a lower priority) does not hear it
                    break;
                }
            }
            catch (RuntimeException | StackOverflowError error)
            {
                com.gnottero.blossom.util.AppErrors.report(handler.app(), "event " + name, error);
            }
        }
        return cancelled;
    }

    /** Fires the event with the player's source: the handler sees their world and position. */
    public boolean fireForPlayer(ServerPlayer player, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        return fire(player.createCommandSourceStack(), arguments);
    }

    /** Fires the event with the source of the given world. */
    public boolean fireInLevel(ServerLevel level, Supplier<List<Value>> arguments)
    {
        if (handlers.isEmpty())
        {
            return false;
        }
        return fire(level.getServer().createCommandSourceStack().withLevel(level), arguments);
    }

    /** Fires the event with the server's source. */
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

    /**
     * Removes the handlers of one function of an app: {@code /blossom script event remove}.
     *
     * @return how many were removed
     */
    public int removeHandler(String app, String function)
    {
        int before = handlers.size();
        handlers.removeIf(handler -> handler.app().equals(app) && handler.function().getString().equals(function));
        return before - handlers.size();
    }

    /** Removes the app's handlers from every event: called on reload and on unload. */
    public static void unregisterApp(String app)
    {
        BY_NAME.values().forEach(event -> event.removeApp(app));
    }

    public static void clearAll()
    {
        BY_NAME.values().forEach(event -> event.handlers.clear());
    }
}
