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
 * <p>I nomi sono quelli che descrivono l'operazione nel modo piu' corto che resta chiaro:
 * {@code set}, {@code block_state}, {@code entity_load}. Chi legge uno script deve capire cosa fa
 * senza aprire la documentazione.
 */
public final class BlossomApi
{
    /** {@code core.mc.block} - leggere e scrivere blocchi, e quello che si sa di un blocco */
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
            // una domanda sola, con lo strato come argomento: vedi WorldAccess
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

    /** {@code core.mc.world} - il mondo intorno: tempo, chunk, biomi, strutture, dimensioni */
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

    /** {@code core.mc.scan} - percorrere volumi di blocchi senza scriversi tre cicli annidati */
    public static final java.util.Map<String, String> SCAN = java.util.Map.of(
            "scan", "scan", "volume", "volume", "neighbors", "neighbours",
            "rect", "rect", "diamond", "diamond");

    /** {@code core.mc.entity} - trovare, creare, interrogare e modificare entita' */
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

    /** {@code core.mc.item} - gli oggetti e i loro data component */
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

    /** {@code core.mc.inventory} - gli inventari, di chi li porta e di cosa li contiene */
    public static final java.util.Map<String, String> INVENTORY = java.util.Map.ofEntries(
            java.util.Map.entry("size", "inventory_size"),
            java.util.Map.entry("has", "inventory_has_items"),
            java.util.Map.entry("get", "inventory_get"),
            java.util.Map.entry("set", "inventory_set"),
            java.util.Map.entry("find", "inventory_find"),
            java.util.Map.entry("remove", "inventory_remove"),
            java.util.Map.entry("drop", "drop_item"));

    /** {@code core.mc.structure} - salvare, piazzare e confrontare template di struttura */
    public static final java.util.Map<String, String> STRUCTURE = java.util.Map.of(
            "save", "structure_save",
            "load", "structure_load",
            "matches", "structure_matches",
            "size", "structure_size",
            // erano in core.mc.world: sono le strutture del generatore, ma il dominio e' questo
            "at", "structures",
            "place", "set_structure",
            "references", "structure_references",
            "eligibility", "structure_eligibility");

    /** {@code core.mc.shape} - far vedere quello che uno script sta calcolando */
    public static final java.util.Map<String, String> SHAPE = java.util.Map.of(
            "draw", "draw_shape",
            "clear", "clear_shapes",
            "types", "shape_types");

    /** {@code core.mc.screen} - aprire una schermata a un giocatore e reagire ai suoi click */
    public static final java.util.Map<String, String> SCREEN = java.util.Map.of(
            "open", "create_screen",
            "close", "close_screen",
            "property", "screen_property");

    /** {@code core.mc.score} - gli obiettivi e i punteggi */
    public static final java.util.Map<String, String> SCORE = java.util.Map.of(
            "score", "scoreboard",
            "add", "scoreboard_add",
            "remove", "scoreboard_remove",
            "property", "scoreboard_property",
            "display", "scoreboard_display");

    /** {@code core.mc.team} - le squadre */
    public static final java.util.Map<String, String> TEAM = java.util.Map.of(
            "list", "team_list",
            "add", "team_add",
            "remove", "team_remove",
            "leave", "team_leave",
            "property", "team_property");

    /** {@code core.mc.bossbar} - le barre del boss */
    public static final java.util.Map<String, String> BOSSBAR = java.util.Map.of("bar", "bossbar");

    /** {@code core.mc.nbt} - leggere e scrivere i dati grezzi del gioco */
    public static final java.util.Map<String, String> NBT = java.util.Map.of(
            "nbt", "nbt",
            "encode", "encode_nbt",
            "decode", "parse_nbt",
            "escape", "escape_nbt",
            "matches", "tag_matches",
            "storage", "nbt_storage");

    /** {@code core.mc.text} - parlare a chi gioca */
    public static final java.util.Map<String, String> TEXT = java.util.Map.of(
            "send", "print",
            "title", "display_title",
            "format", "format",
            "hud", "hud");

    /** {@code core.mc.effect} - suoni e particelle */
    public static final java.util.Map<String, String> EFFECT = java.util.Map.of(
            "sound", "sound",
            "particle", "particle",
            "particle_line", "particle_line",
            "particle_box", "particle_box",
            "post", "post_effect");

    /** {@code core.mc.event} - reagire a quello che succede, e farlo succedere fra app */
    /**
     * {@code core.mc.event} - reagire a quello che succede, e farlo succedere fra app.
     *
     * <p>{@code on} e {@code declare} restano due funzioni e non una: {@code on} ascolta un evento
     * del gioco, e il suo catalogo e' chiuso apposta - un nome scritto male e' un errore di
     * caricamento con il suggerimento, invece di un handler che non scatta mai. {@code declare}
     * fa nascere un evento dell'applicazione, che per definizione un catalogo non ce l'ha, e fa
     * coppia con {@code emit}. Fonderle vorrebbe dire perdere il controllo sui nomi, che e' la cosa
     * piu' utile delle due.
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
     * I simboli che il layer di gioco aggiunge a {@code core.io}.
     *
     * <p>Non e' un modulo suo: leggere e scrivere file e' un lavoro solo, e chi lo fa lo importa da
     * un posto solo. Queste tre stanno qui e non in {@code blossom-core} perche' NBT e' un formato
     * binario di Minecraft e un datapack e' un concetto del gioco - non perche' siano un'altra
     * categoria di operazione. C'era un {@code core.mc.file} e non c'e' piu': era un secondo modo di
     * aprire un file, e un secondo modo di aprire un file e' una domanda in piu' ad ogni chiamata.
     */
    public static final java.util.Map<String, String> IO = java.util.Map.of(
            "read_nbt", "read_nbt",
            "write_nbt", "write_nbt",
            "create_datapack", "create_datapack");

    /** {@code core.mc.data} - scrivere il contenuto di gioco di un pack, generandolo */
    public static final java.util.Map<String, String> DATA = java.util.Map.of(
            "write", "data_write",
            "write_text", "data_write_text");

    /** {@code core.mc.system} - il server visto da fuori: comandi, statistiche, diagnostica */
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
        // non un modulo nuovo: i file si aprono da 'core.io', anche quando l'implementazione
        // ha bisogno di Minecraft
        StdLib.extendModule("core.io", IO);
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
        com.gnottero.blossom.api.ContentApi.apply(expression);
        com.gnottero.blossom.api.Auxiliary.apply(expression);
        com.gnottero.blossom.api.ModThreading.apply(expression);
        com.gnottero.blossom.api.EventApi.apply(expression, appName);
        return expression;
    }
}
