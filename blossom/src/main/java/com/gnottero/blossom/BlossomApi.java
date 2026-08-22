package com.gnottero.blossom;

import java.util.Set;

import blossom.interpreter.Expression;
import blossom.language.StdLib;

/**
 * The game API: which {@code core.mc.*} modules exist and what each one exports.
 *
 * <p>The implementations live in classes under {@code com.gnottero.blossom.api} and get registered
 * once on each expression, in a flat map: it is *visibility* that goes through modules, just like
 * for the core library. Code that writes {@code block.set(...)} sees where it comes from, but the
 * call pays nothing extra, because the symbol-to-implementation binding is resolved at load time
 * and not on every invocation.
 *
 * <p>Names are chosen to describe the operation in the shortest way that stays clear:
 * {@code set}, {@code block_state}, {@code entity_load}. Someone reading a script should understand
 * what it does without opening the documentation.
 */
public final class BlossomApi
{
    /** {@code core.mc.block}: reading and writing blocks, and what is known about a block. */
    public static final java.util.Map<String, String> BLOCK = java.util.Map.ofEntries(
            java.util.Map.entry("block", "block"),
            java.util.Map.entry("data", "block_data"),
            java.util.Map.entry("state", "block_state"),
            java.util.Map.entry("tags", "block_tags"),
            java.util.Map.entry("catalog", "block_list"),
            java.util.Map.entry("set", "set"),
            java.util.Map.entry("destroy", "destroy"),
            java.util.Map.entry("harvest", "harvest"),
            java.util.Map.entry("place_item", "place_item"),
            java.util.Map.entry("update", "update"),
            java.util.Map.entry("tick", "block_tick"),
            java.util.Map.entry("random_tick", "random_tick"),
            java.util.Map.entry("without_updates", "without_updates"),
            java.util.Map.entry("solid", "solid"),
            java.util.Map.entry("air", "air"),
            java.util.Map.entry("liquid", "liquid"),
            java.util.Map.entry("flammable", "flammable"),
            java.util.Map.entry("transparent", "transparent"),
            java.util.Map.entry("suffocates", "suffocates"),
            java.util.Map.entry("blocks_movement", "blocks_movement"),
            java.util.Map.entry("ticks_randomly", "ticks_randomly"),
            java.util.Map.entry("power", "power"),
            // a single query, with the layer as an argument: see WorldAccess
            java.util.Map.entry("light", "light"),
            java.util.Map.entry("see_sky", "see_sky"),
            java.util.Map.entry("opacity", "opacity"),
            java.util.Map.entry("blocks_daylight", "blocks_daylight"),
            java.util.Map.entry("hardness", "hardness"),
            java.util.Map.entry("blast_resistance", "blast_resistance"),
            java.util.Map.entry("sound", "block_sound"),
            java.util.Map.entry("map_color", "map_colour"),
            java.util.Map.entry("explosion", "create_explosion"),
            java.util.Map.entry("pos", "pos"),
            java.util.Map.entry("pos_offset", "pos_offset"));

    /** {@code core.mc.world}: the world around, time, chunks, biomes, structures, dimensions. */
    public static final java.util.Map<String, String> WORLD = java.util.Map.ofEntries(
            java.util.Map.entry("weather", "weather"),
            java.util.Map.entry("top", "top"),
            java.util.Map.entry("biome", "biome"),
            java.util.Map.entry("set_biome", "set_biome"),
            java.util.Map.entry("save", "save"),
            java.util.Map.entry("loaded", "loaded"),
            java.util.Map.entry("loaded_for_entities", "loaded_ep"),
            java.util.Map.entry("load_status", "loaded_status"),
            java.util.Map.entry("chunk_generated", "is_chunk_generated"),
            java.util.Map.entry("generation_status", "generation_status"),
            java.util.Map.entry("chunk_tickets", "chunk_tickets"),
            java.util.Map.entry("add_chunk_ticket", "add_chunk_ticket"),
            java.util.Map.entry("reload_chunk", "reload_chunk"),
            java.util.Map.entry("inhabited_time", "inhabited_time"),
            java.util.Map.entry("in_slime_chunk", "in_slime_chunk"),
            java.util.Map.entry("spawn_potential", "spawn_potential"),
            java.util.Map.entry("sample_noise", "sample_noise"),
            java.util.Map.entry("poi", "poi"),
            java.util.Map.entry("set_poi", "set_poi"),
            java.util.Map.entry("place_feature", "plop"),
            java.util.Map.entry("in_dimension", "in_dimension"),
            java.util.Map.entry("day_time", "day_time"),
            java.util.Map.entry("tick_time", "tick_time"),
            java.util.Map.entry("tick", "game_tick"));

    /** {@code core.mc.scan}: walking block volumes without writing three nested loops. */
    public static final java.util.Map<String, String> SCAN = java.util.Map.of(
            "scan", "scan", "volume", "volume", "neighbors", "neighbours",
            "rect", "rect", "diamond", "diamond");

    /** {@code core.mc.entity}: finding, spawning, querying, and modifying entities. */
    public static final java.util.Map<String, String> ENTITY = java.util.Map.ofEntries(
            java.util.Map.entry("player", "player"),
            java.util.Map.entry("spawn", "spawn"),
            java.util.Map.entry("by_id", "entity_id"),
            java.util.Map.entry("list", "entity_list"),
            java.util.Map.entry("area", "entity_area"),
            java.util.Map.entry("select", "entity_selector"),
            java.util.Map.entry("types", "entity_types"),
            java.util.Map.entry("query", "query"),
            java.util.Map.entry("modify", "modify"),
            java.util.Map.entry("on", "entity_event"),
            java.util.Map.entry("on_load", "entity_load_handler"),
            java.util.Map.entry("has_client", "has_client"));

    /** {@code core.mc.item}: items and their data components. */
    public static final java.util.Map<String, String> ITEM = java.util.Map.ofEntries(
            java.util.Map.entry("max_stack", "stack_limit"),
            java.util.Map.entry("catalog", "item_list"),
            java.util.Map.entry("tags", "item_tags"),
            java.util.Map.entry("name", "item_display_name"),
            java.util.Map.entry("recipes", "recipe_data"),
            java.util.Map.entry("crafting_remainder", "crafting_remaining_item"),
            java.util.Map.entry("components", "item_components"),
            java.util.Map.entry("component", "item_component"),
            java.util.Map.entry("set_component", "set_item_component"),
            java.util.Map.entry("component_catalog", "component_list"));

    /** {@code core.mc.inventory}: inventories, who carries them, and what they hold. */
    public static final java.util.Map<String, String> INVENTORY = java.util.Map.ofEntries(
            java.util.Map.entry("size", "inventory_size"),
            java.util.Map.entry("has", "inventory_has_items"),
            java.util.Map.entry("get", "inventory_get"),
            java.util.Map.entry("set", "inventory_set"),
            java.util.Map.entry("find", "inventory_find"),
            java.util.Map.entry("remove", "inventory_remove"),
            java.util.Map.entry("drop", "drop_item"));

    /** {@code core.mc.structure}: saving, placing, and comparing structure templates. */
    public static final java.util.Map<String, String> STRUCTURE = java.util.Map.of(
            "save", "structure_save",
            "load", "structure_load",
            "matches", "structure_matches",
            "size", "structure_size",
            // used to be in core.mc.world: these are the generator's structures, but this is their domain
            "at", "structures",
            "place", "set_structure",
            "references", "structure_references",
            "eligibility", "structure_eligibility");

    /** {@code core.mc.shape}: showing what a script is computing. */
    public static final java.util.Map<String, String> SHAPE = java.util.Map.of(
            "draw", "draw_shape",
            "clear", "clear_shapes",
            "types", "shape_types");

    /** {@code core.mc.screen}: opening a screen for a player and reacting to their clicks. */
    public static final java.util.Map<String, String> SCREEN = java.util.Map.of(
            "open", "create_screen",
            "close", "close_screen",
            "property", "screen_property");

    /** {@code core.mc.score}: objectives and scores. */
    public static final java.util.Map<String, String> SCORE = java.util.Map.of(
            "score", "scoreboard",
            "add", "scoreboard_add",
            "remove", "scoreboard_remove",
            "property", "scoreboard_property",
            "display", "scoreboard_display");

    /** {@code core.mc.team}: teams. */
    public static final java.util.Map<String, String> TEAM = java.util.Map.of(
            "list", "team_list",
            "add", "team_add",
            "remove", "team_remove",
            "leave", "team_leave",
            "property", "team_property");

    /** {@code core.mc.bossbar}: boss bars. */
    public static final java.util.Map<String, String> BOSSBAR = java.util.Map.of("bar", "bossbar");

    /** {@code core.mc.nbt}: reading and writing the raw data of the game. */
    public static final java.util.Map<String, String> NBT = java.util.Map.of(
            "nbt", "nbt",
            "encode", "encode_nbt",
            "decode", "parse_nbt",
            "escape", "escape_nbt",
            "matches", "tag_matches",
            "storage", "nbt_storage");

    /** {@code core.mc.text}: talking to the players. */
    public static final java.util.Map<String, String> TEXT = java.util.Map.of(
            "send", "print",
            "title", "display_title",
            "format", "format",
            "hud", "hud");

    /** {@code core.mc.effect}: sounds and particles. */
    public static final java.util.Map<String, String> EFFECT = java.util.Map.of(
            "sound", "sound",
            "particle", "particle",
            "particle_line", "particle_line",
            "particle_box", "particle_box",
            "post", "post_effect");

    /**
     * {@code core.mc.event}: reacting to what happens, and making it happen between apps.
     *
     * <p>{@code on} and {@code declare} stay two functions and not one: {@code on} listens to a game
     * event, and its catalog is closed on purpose, so a misspelled name is a load error with a
     * suggestion instead of a handler that never fires. {@code declare} brings an application event
     * into existence, which by definition has no catalog, and pairs with {@code emit}. Merging them
     * would mean losing the control over names, which is the more useful half of the two.
     */
    public static final java.util.Map<String, String> EVENT = java.util.Map.of(
            "on", "on",
            "off", "off",
            "list", "available",
            "listeners", "listeners",
            "describe", "describe",
            "declare", "handle_event",
            "emit", "signal_event",
            "schedule", "schedule");

    /**
     * The symbols the game layer adds to {@code core.io}.
     *
     * <p>Not a module of its own: reading and writing files is one job, and whoever does it imports
     * it from one place. These three live here and not in {@code blossom-core} because NBT is a
     * Minecraft binary format and a datapack is a game concept, not because they are another
     * category of operation. There used to be a {@code core.mc.file} and there is not any more: it
     * was a second way to open a file, and a second way to open a file is one more question at every
     * call.
     */
    public static final java.util.Map<String, String> IO = java.util.Map.of(
            "read_nbt", "read_nbt",
            "write_nbt", "write_nbt",
            "create_datapack", "create_datapack");

    /** {@code core.mc.data}: writing the game content of a pack by generating it. */
    public static final java.util.Map<String, String> DATA = java.util.Map.of(
            "write", "data_write",
            "write_text", "data_write_text");

    /** {@code core.mc.system}: the server seen from outside: commands, statistics, diagnostics. */
    public static final java.util.Map<String, String> SYSTEM = java.util.Map.of(
            "info", "system_info",
            "mob_counts", "get_mob_counts",
            "logger", "logger",
            "run", "run",
            "statistic", "statistic");

    private BlossomApi()
    {
    }

    /**
     * Declares the modules of the game layer to the core catalog.
     *
     * <p>To be called at startup, before any script is loaded: the import resolver consults the
     * catalog while reading the preamble, that is before the functions get registered.
     */
    public static void declareModules()
    {
        StdLib.registerModule("core.mc.block", BLOCK);
        StdLib.registerModule("core.mc.world", WORLD);
        StdLib.registerModule("core.mc.scan", SCAN);
        StdLib.registerModule("core.mc.entity", ENTITY);
        StdLib.registerModule("core.mc.item", ITEM);
        StdLib.registerModule("core.mc.inventory", INVENTORY);
        StdLib.registerModule("core.mc.structure", STRUCTURE);
        StdLib.registerModule("core.mc.shape", SHAPE);
        StdLib.registerModule("core.mc.screen", SCREEN);
        StdLib.registerModule("core.mc.score", SCORE);
        StdLib.registerModule("core.mc.team", TEAM);
        StdLib.registerModule("core.mc.bossbar", BOSSBAR);
        StdLib.registerModule("core.mc.nbt", NBT);
        StdLib.registerModule("core.mc.text", TEXT);
        StdLib.registerModule("core.mc.effect", EFFECT);
        StdLib.registerModule("core.mc.event", EVENT);
        StdLib.registerModule("core.mc.system", SYSTEM);
        StdLib.registerModule("core.mc.data", DATA);
        // not a new module: files are opened from 'core.io', even when the implementation needs
        // Minecraft
        StdLib.extendModule("core.io", IO);
        declareValueTypes();
    }

    /**
     * Makes the game types writable in signatures, e.g. {@code fn hit(e: entity, p: block)}.
     *
     * <p>The core does not know they exist: it only compares a name with what the value says it is.
     * Registering them here is all it takes for parameter checking to cover them too.
     */
    private static void declareValueTypes()
    {
        // only the types that really exist: declaring a name no value ever returns would offer a
        // constraint that never passes
        for (String type : new String[] {"entity", "block", "text", "nbt", "screen"})
        {
            blossom.value.ValueTypes.register(type, type);
        }
    }

    /** Registers the whole game API on {@code expression}, on behalf of app {@code appName}. */
    public static Expression apply(Expression expression, String appName)
    {
        com.gnottero.blossom.api.WorldAccess.apply(expression);
        com.gnottero.blossom.api.BlockIterators.apply(expression);
        com.gnottero.blossom.api.Entities.apply(expression);
        com.gnottero.blossom.api.Inventories.apply(expression);
        com.gnottero.blossom.api.Structures.apply(expression);
        com.gnottero.blossom.api.Components.apply(expression);
        com.gnottero.blossom.api.ShapeApi.apply(expression);
        com.gnottero.blossom.hud.HudApi.apply(expression);
        com.gnottero.blossom.api.Scoreboards.apply(expression);
        com.gnottero.blossom.api.Monitoring.apply(expression);
        com.gnottero.blossom.api.ContentApi.apply(expression);
        com.gnottero.blossom.api.Auxiliary.apply(expression);
        com.gnottero.blossom.api.ModThreading.apply(expression);
        com.gnottero.blossom.api.EventApi.apply(expression, appName);
        return expression;
    }
}
