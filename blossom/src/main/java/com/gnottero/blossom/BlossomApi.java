package com.gnottero.blossom;

import java.util.Set;

import blossom.interpreter.Expression;
import blossom.language.StdLib;

/**
 * L'API di gioco: quali moduli {@code core.mc.*} esistono e cosa esporta ciascuno.
 *
 * <p>Le implementazioni vivono nelle classi di {@code com.gnottero.blossom.api} e restano registrate una
 * volta sola su ogni espressione, in una mappa piatta: e' la *visibilita'* a passare per i moduli,
 * come per la libreria del core. Chi scrive vede {@code block.set(...)} e sa da dove viene, ma la
 * chiamata non paga niente in piu', perche' il legame simbolo → implementazione si risolve al
 * caricamento e non ad ogni invocazione.
 *
 * <p>I nomi sono quelli di Scarpet dove il nome era gia' buono: chi arriva da li' non deve
 * reimparare l'API, e la documentazione di Scarpet resta leggibile come riferimento.
 */
public final class BlossomApi
{
    /** {@code core.mc.block} - leggere e scrivere blocchi, e quello che si sa di un blocco */
    public static final Set<String> BLOCK = Set.of(
            "block", "block_data", "block_state", "block_properties", "block_tags", "block_list",
            "set", "destroy", "harvest", "place_item",
            "update", "block_tick", "random_tick", "without_updates",
            "solid", "air", "liquid", "flammable", "transparent", "suffocates",
            "blocks_movement", "ticks_randomly", "power",
            "light", "block_light", "sky_light", "effective_light", "emitted_light", "see_sky", "brightness",
            "hardness", "blast_resistance", "block_sound", "material", "map_colour", "property",
            "create_explosion", "pos", "pos_offset");

    /** {@code core.mc.world} - il mondo intorno: tempo, chunk, biomi, strutture, dimensioni */
    public static final Set<String> WORLD = Set.of(
            "weather", "top", "biome", "set_biome", "seed", "save",
            "loaded", "loaded_ep", "loaded_status", "is_chunk_generated", "generation_status",
            "chunk_tickets", "add_chunk_ticket", "reload_chunk", "inhabited_time",
            "in_slime_chunk", "spawn_potential", "sample_noise",
            "structures", "set_structure", "structure_references", "structure_eligibility",
            "poi", "set_poi", "plop",
            "current_dimension", "in_dimension", "view_distance", "enable_hidden_dimensions",
            "world_time", "day_time", "tick_time", "last_tick_times", "game_tick");

    /** {@code core.mc.scan} - percorrere volumi di blocchi senza scriversi tre cicli annidati */
    public static final Set<String> SCAN = Set.of("scan", "volume", "neighbours", "rect", "diamond");

    /** {@code core.mc.entity} - trovare, creare, interrogare e modificare entita' */
    public static final Set<String> ENTITY = Set.of(
            "player", "spawn", "entity_id", "entity_list", "entity_area", "entity_selector",
            "entity_types", "query", "modify", "entity_event", "entity_load_handler",
            "create_marker", "remove_all_markers", "has_client");

    /** {@code core.mc.item} - inventari, oggetti, ricette e data component */
    public static final Set<String> ITEM = java.util.stream.Stream.concat(
            Set.of("stack_limit", "item_category", "item_list", "item_tags", "item_display_name",
                    "recipe_data", "crafting_remaining_item", "drop_item",
                    "inventory_size", "inventory_has_items", "inventory_get", "inventory_set",
                    "inventory_find", "inventory_remove").stream(),
            com.gnottero.blossom.api.Components.MODULE.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** {@code core.mc.structure} - salvare, piazzare e confrontare template di struttura */
    public static final Set<String> STRUCTURE = com.gnottero.blossom.api.Structures.MODULE;

    /** {@code core.mc.shape} - far vedere quello che uno script sta calcolando */
    public static final Set<String> SHAPE = com.gnottero.blossom.api.ShapeApi.MODULE;

    /** {@code core.mc.screen} - aprire una schermata a un giocatore e reagire ai suoi click */
    public static final Set<String> SCREEN = Set.of("create_screen", "close_screen", "screen_property");

    /** {@code core.mc.scoreboard} - obiettivi, squadre e barre del boss */
    public static final Set<String> SCOREBOARD = Set.of(
            "scoreboard", "scoreboard_add", "scoreboard_remove", "scoreboard_property", "scoreboard_display",
            "team_list", "team_add", "team_remove", "team_leave", "team_property",
            "bossbar");

    /** {@code core.mc.nbt} - leggere e scrivere i dati grezzi del gioco */
    public static final Set<String> NBT = Set.of(
            "nbt", "encode_nbt", "parse_nbt", "escape_nbt", "tag_matches", "nbt_storage");

    /** {@code core.mc.text} - parlare a chi gioca */
    public static final Set<String> TEXT = java.util.stream.Stream.concat(
            Set.of("print", "display_title", "format").stream(),
            com.gnottero.blossom.hud.HudApi.MODULE.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** {@code core.mc.effect} - suoni e particelle */
    public static final Set<String> EFFECT = Set.of(
            "sound", "particle", "particle_line", "particle_box", "post_effect");

    /** {@code core.mc.event} - reagire a quello che succede, e farlo succedere fra app */
    public static final Set<String> EVENT = Set.of(
            "on", "off", "available", "listeners", "describe",
            "handle_event", "signal_event", "schedule");

    /** {@code core.mc.file} - i dati dell'app su disco */
    public static final Set<String> FILE = Set.of(
            "list_files", "read_file", "write_file", "delete_file",
            "load_app_data", "store_app_data", "create_datapack");

    /** {@code core.mc.system} - il server visto da fuori: comandi, statistiche, diagnostica */
    public static final Set<String> SYSTEM = Set.of(
            "system_info", "get_mob_counts", "logger", "run", "statistic", "task_join");

    private BlossomApi()
    {
    }

    /**
     * Dichiara i moduli del layer di gioco al catalogo del core.
     *
     * <p>Va chiamata all'avvio, prima che qualunque script venga caricato: il resolver degli import
     * consulta il catalogo mentre legge il preambolo, cioe' prima che le funzioni vengano registrate.
     */
    public static void declareModules()
    {
        StdLib.registerModule("core.mc.block", BLOCK);
        StdLib.registerModule("core.mc.world", WORLD);
        StdLib.registerModule("core.mc.scan", SCAN);
        StdLib.registerModule("core.mc.entity", ENTITY);
        StdLib.registerModule("core.mc.item", ITEM);
        StdLib.registerModule("core.mc.structure", STRUCTURE);
        StdLib.registerModule("core.mc.shape", SHAPE);
        StdLib.registerModule("core.mc.screen", SCREEN);
        StdLib.registerModule("core.mc.scoreboard", SCOREBOARD);
        StdLib.registerModule("core.mc.nbt", NBT);
        StdLib.registerModule("core.mc.text", TEXT);
        StdLib.registerModule("core.mc.effect", EFFECT);
        StdLib.registerModule("core.mc.event", EVENT);
        StdLib.registerModule("core.mc.file", FILE);
        StdLib.registerModule("core.mc.system", SYSTEM);
        declareValueTypes();
    }

    /**
     * Rende i tipi del gioco scrivibili nelle firme, es. {@code fn colpisci(e: entity, p: block)}.
     *
     * <p>Il core non sa che esistono: sa solo confrontare un nome con quello che il valore dice di
     * essere. Registrarli qui è tutto quello che serve perché il controllo dei parametri valga anche
     * per loro.
     */
    private static void declareValueTypes()
    {
        // solo i tipi che esistono davvero: dichiarare un nome che nessun valore restituisce
        // significherebbe offrire un vincolo che non passa mai
        for (String type : new String[] {"entity", "block", "text", "nbt", "screen"})
        {
            blossom.value.ValueTypes.register(type, type);
        }
    }

    /** Registra su {@code expression} tutta l'API di gioco, per conto dell'app {@code appName}. */
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
        com.gnottero.blossom.api.Auxiliary.apply(expression);
        com.gnottero.blossom.api.ModThreading.apply(expression);
        com.gnottero.blossom.api.EventApi.apply(expression, appName);
        return expression;
    }
}
