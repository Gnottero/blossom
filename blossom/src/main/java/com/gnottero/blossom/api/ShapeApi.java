package com.gnottero.blossom.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.net.ServerNetwork;
import com.gnottero.blossom.shape.Shape;
import com.gnottero.blossom.value.BlockValue;
import com.gnottero.blossom.value.FormattedTextValue;
import com.gnottero.blossom.value.NBTSerializableValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * {@code core.mc.shape}: far vedere quello che uno script sta calcolando.
 *
 * <pre>
 * import "core.mc.shape" as shape;
 *
 * shape.draw_shape('box', 100, {'from' -&gt; [0, 64, 0], 'to' -&gt; [8, 70, 8], 'color' -&gt; 'red'});
 * </pre>
 *
 * <p>Chi ha Blossom anche sul client vede il disegno vero, chi gioca vanilla la stessa geometria a
 * particelle: chi scrive lo script non deve sapere chi ha cosa. Le attribute sono quelle di Scarpet,
 * con gli stessi nomi, ma stanno in una mappa invece che sparse fra gli argomenti, e un nome che non
 * esiste per quella forma e' un errore invece di un silenzio.
 */
public final class ShapeApi
{
    /** i simboli esportati da {@code core.mc.shape} */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "draw_shape", "clear_shapes", "shape_types");

    /** i colori con un nome, perché scrivere 0xff0000ff a mano non lo fa nessuno volentieri */
    private static final Map<String, Integer> NAMED_COLORS = Map.of(
            "white", 0xFFFFFFFF, "black", 0x000000FF, "red", 0xFF3B30FF, "green", 0x34C759FF,
            "blue", 0x2F7BFFFF, "yellow", 0xFFD60AFF, "cyan", 0x30D5C8FF, "magenta", 0xFF2D95FF,
            "orange", 0xFF9500FF, "grey", 0x8E8E93FF);

    /** quello che vale per qualunque forma */
    private static final Set<String> COMMON_ATTRIBUTES =
            Set.of("color", "fill", "line", "player", "follow", "snap", "debug");

    /** quello che vale per una forma sola, oltre a {@link #COMMON_ATTRIBUTES} */
    private static final Map<String, Set<String>> ATTRIBUTES_BY_TYPE = Map.of(
            "line", Set.of("from", "to"),
            "box", Set.of("from", "to"),
            "sphere", Set.of("center", "radius", "level"),
            "cylinder", Set.of("center", "radius", "height", "axis", "level"),
            "label", Set.of("pos", "text", "value", "size", "align", "indent", "height", "raise",
                    "tilt", "lean", "turn", "facing", "doublesided"),
            "block", Set.of("pos", "block", "facing", "tilt", "lean", "turn", "scale",
                    "blocklight", "skylight"),
            "item", Set.of("pos", "item", "variant", "facing", "tilt", "lean", "turn", "scale",
                    "blocklight", "skylight"));

    /** i valori che {@code snap} accetta: quali assi seguono l'entità, e quali al blocco */
    private static final Set<String> SNAP_VALUES = Set.of(
            "xyz", "xz", "yz", "xy", "x", "y", "z",
            "dxdydz", "dxdz", "dydz", "dxdy", "dx", "dy", "dz",
            "xdz", "dxz", "ydz", "dyz", "xdy", "dxy",
            "xydz", "xdyz", "xdydz", "dxyz", "dxydz", "dxdyz");

    /** verso cosa può guardare un'etichetta, o un blocco disegnato a mezz'aria */
    private static final Set<String> FACINGS = Set.of(
            "player", "camera", "north", "south", "east", "west", "up", "down");

    /** in che posa si può disegnare un item: sono i modi in cui il gioco lo mostra già da sé */
    private static final Set<String> VARIANTS = Set.of(
            "none", "gui", "ground", "fixed", "head",
            "first_person_left_hand", "first_person_right_hand",
            "third_person_left_hand", "third_person_right_hand");

    private ShapeApi()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * draw_shape(tipo, durata_in_tick, attributi)
         *
         * Un solo modo di chiamarla, con gli attributi in una mappa: in Scarpet erano coppie
         * chiave-valore sparse negli argomenti, il che rendeva impossibile capire dove finiva la
         * geometria e cominciavano le opzioni.
         */
        expression.addContextFunction("draw_shape", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "'draw_shape' wants the type, the duration in ticks and the attributes:"
                                + " draw_shape('box', 100, {'from' -> ..., 'to' -> ...}).");
            }
            ScriptContext cc = (ScriptContext) c;
            String type = lv.get(0).getString().toLowerCase(Locale.ROOT);
            long duration = NumericValue.asNumber(lv.get(1)).getLong();
            Map<Value, Value> attributes = lv.size() > 2 && lv.get(2) instanceof final MapValue map
                    ? map.getMap()
                    : Map.of();

            ServerLevel level = cc.level();
            long expires = BlossomMod.shapes().tick() + Math.max(1L, duration);
            checkAttributes(type, attributes);
            Shape shape = build(cc, type, attributes, expires);
            List<ServerPlayer> audience = audienceOf(cc, attributes);
            if (shape instanceof final Shape.Label label)
            {
                // il testo non si puo' approssimare con le particelle: a chi non ha il client
                // arriva una volta in chat, cosi' l'informazione non va persa
                Component message = label.component() == null
                        ? Component.literal(label.text())
                        : FormattedTextValue.deserialize(label.component(), cc.registryAccess()).getText();
                ServerNetwork.withoutClient(audience)
                        .forEach(player -> player.sendSystemMessage(message, true));
            }
            return new NumericValue(BlossomMod.shapes().draw(shape, level, audience));
        });

        /** clear_shapes() - dimentica le forme ancora in attesa di essere ridisegnate. */
        expression.addContextFunction("clear_shapes", 0, (c, t, lv) ->
        {
            BlossomMod.shapes().clear();
            return Value.TRUE;
        });

        /** shape_types() - i tipi che draw_shape accetta. */
        expression.addContextFunction("shape_types", 0, (c, t, lv) ->
                ListValue.wrap(Shape.TYPES.stream().map(StringValue::of)));
    }

    /**
     * Un nome di attributo che non esiste è un errore, non un silenzio.
     *
     * <p>Un {@code 'radiu'} scritto male in Scarpet passava inosservato e la forma usciva col
     * default: si vedeva qualcosa di sbagliato e non si capiva perché. Qui si sa subito, e con il
     * nome giusto suggerito.
     */
    private static void checkAttributes(String type, Map<Value, Value> attributes)
    {
        Set<String> byType = ATTRIBUTES_BY_TYPE.get(type);
        if (byType == null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Unknown shape type: '" + type + "'. The types are " + Shape.TYPES + ".",
                    Suggestions.closestTo(type, Shape.TYPES));
        }
        List<String> known = new ArrayList<>(byType);
        known.addAll(COMMON_ATTRIBUTES);
        for (Value key : attributes.keySet())
        {
            String name = key.getString();
            if (!known.contains(name))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'" + type + "' has no attribute '" + name + "'. It takes " + known.stream().sorted().toList() + ".",
                        Suggestions.closestTo(name, known));
            }
        }
    }

    private static Shape build(ScriptContext cc, String type, Map<Value, Value> attributes, long expires)
    {
        Shape.Common common = new Shape.Common(
                color(attributes, "color", 0xFFFFFFFF),
                color(attributes, "fill", 0x00000000),
                (float) number(attributes, "line", 2.0),
                expires,
                follow(attributes),
                choice(attributes, "snap", SNAP_VALUES, "xyz"),
                bool(attributes, "debug"));
        return switch (type)
        {
            case "line" -> new Shape.Line(vec(attributes, "from"), vec(attributes, "to"), common);
            case "box" -> new Shape.Box(vec(attributes, "from"), vec(attributes, "to"), common);
            case "sphere" -> new Shape.Sphere(vec(attributes, "center"), positive(attributes, "radius"),
                    (int) number(attributes, "level", 0), common);
            case "cylinder" -> new Shape.Cylinder(vec(attributes, "center"), positive(attributes, "radius"),
                    number(attributes, "height", 0.0), choice(attributes, "axis", Set.of("x", "y", "z"), "y"),
                    (int) number(attributes, "level", 0), common);
            case "label" -> new Shape.Label(vec(attributes, "pos"), text(attributes).getString(),
                    component(cc, attributes), style(attributes), common);
            case "block" -> block(cc, attributes, common);
            case "item" -> item(cc, attributes, common);
            // il tipo e' gia' stato validato in checkAttributes: qui non ci si arriva
            default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Unknown shape type: '" + type + "'. The types are " + Shape.TYPES + ".",
                    Suggestions.closestTo(type, Shape.TYPES));
        };
    }

    /**
     * Il blocco da disegnare a mezz'aria.
     *
     * <p>Si accetta il blocco come lo si scrive ({@code 'chest[facing=north]'}) o un valore blocco
     * preso dal mondo: nel secondo caso viene con i suoi dati - il contenuto di un baule, il testo di
     * un cartello - e quelli servono al client per disegnarlo come si deve.
     */
    private static Shape block(ScriptContext cc, Map<Value, Value> attributes, Shape.Common common)
    {
        Value declared = attributes.get(StringValue.of("block"));
        if (declared == null || declared.isNull())
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "A 'block' shape wants a 'block' to show, e.g. 'block' -> 'chest[facing=north]'.");
        }
        BlockValue block = declared instanceof final BlockValue value
                ? value
                : BlockValue.fromString(declared.getString(), cc.level());
        CompoundTag data = block.getData();
        return new Shape.Sprite("block", vec(attributes, "pos"),
                BlockStateParser.serialize(block.getBlockState()),
                data == null || data.isEmpty() ? null : data,
                pose(attributes, "north"), common);
    }

    /**
     * L'item da disegnare a mezz'aria.
     *
     * <p>Viaggia lo stack intero e non il solo nome: incantesimi, nome personalizzato e data component
     * cambiano come l'item si vede, e chi li ha messi si aspetta di vederli.
     */
    private static Shape item(ScriptContext cc, Map<Value, Value> attributes, Shape.Common common)
    {
        Value declared = attributes.get(StringValue.of("item"));
        if (declared == null || declared.isNull())
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "An 'item' shape wants an 'item' to show, e.g. 'item' -> 'diamond_sword'.");
        }
        ItemStack stack = declared instanceof final NBTSerializableValue nbt
                ? NBTSerializableValue.parseItem("air", nbt.getCompoundTag(), cc.registryAccess())
                : NBTSerializableValue.parseItem(declared.getString(), cc.registryAccess());
        CompoundTag data = (CompoundTag) ItemStack.CODEC
                .encodeStart(cc.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow(message -> new TypeMismatchError(SourcePosition.UNKNOWN,
                        "That item cannot be drawn: " + message));
        // il nome serve solo al ripiego a particelle, e li' l'id basta: lo stack intero e' in 'data'
        String name = cc.registry(net.minecraft.core.registries.Registries.ITEM)
                .getKey(stack.getItem()).toString();
        return new Shape.Sprite("item", vec(attributes, "pos"), name, data,
                pose(attributes, "north"), common);
    }

    /** Come sta nello spazio un blocco o un item disegnato a mezz'aria. */
    private static Shape.Sprite.Pose pose(Map<Value, Value> attributes, String defaultFacing)
    {
        Vec3 scale = scale(attributes);
        return new Shape.Sprite.Pose(
                choice(attributes, "facing", FACINGS, defaultFacing),
                (float) number(attributes, "tilt", 0.0),
                (float) number(attributes, "lean", 0.0),
                (float) number(attributes, "turn", 0.0),
                (float) scale.x, (float) scale.y, (float) scale.z,
                light(attributes, "blocklight"),
                light(attributes, "skylight"),
                choice(attributes, "variant", VARIANTS, "none"));
    }

    /** {@code scale} è un numero solo, o uno per asse. */
    private static Vec3 scale(Map<Value, Value> attributes)
    {
        Value value = attributes.get(StringValue.of("scale"));
        if (value == null || value.isNull())
        {
            return new Vec3(1, 1, 1);
        }
        if (value instanceof final ListValue list)
        {
            List<Value> items = list.getItems();
            if (items.size() < 3)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'scale' wants one number, or one per axis: 'scale' -> [2, 1, 2].");
            }
            return new Vec3(NumericValue.asNumber(items.get(0)).getDouble(),
                    NumericValue.asNumber(items.get(1)).getDouble(),
                    NumericValue.asNumber(items.get(2)).getDouble());
        }
        double uniform = NumericValue.asNumber(value).getDouble();
        return new Vec3(uniform, uniform, uniform);
    }

    /** Un livello di luce fisso, o {@code -1} per quella che c'è nel posto dove sta la forma. */
    private static int light(Map<Value, Value> attributes, String key)
    {
        int light = (int) number(attributes, key, -1.0);
        if (light > 15)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'" + key + "' goes from 0 to 15, or is left out for the light of the place.");
        }
        return light;
    }

    private static Shape.Label.Text style(Map<Value, Value> attributes)
    {
        return new Shape.Label.Text(
                (float) number(attributes, "size", Shape.Label.DEFAULT_SIZE),
                align(attributes),
                (float) number(attributes, "indent", 0.0),
                (float) number(attributes, "height", 0.0),
                (float) number(attributes, "raise", 0.0),
                (float) number(attributes, "tilt", 0.0),
                (float) number(attributes, "lean", 0.0),
                (float) number(attributes, "turn", 0.0),
                choice(attributes, "facing", FACINGS, "player"),
                bool(attributes, "doublesided"));
    }

    /** {@code -1} a sinistra, {@code 0} centrato, {@code 1} a destra: come in Scarpet. */
    private static int align(Map<Value, Value> attributes)
    {
        String align = choice(attributes, "align", Set.of("center", "left", "right"), "center");
        return switch (align)
        {
            case "left" -> -1;
            case "right" -> 1;
            default -> 0;
        };
    }

    /**
     * L'entità a cui la forma sta attaccata.
     *
     * <p>Si accetta l'entità stessa o il suo id: il primo è quello che si ha in mano dentro un
     * evento, il secondo è quello che si è magari salvato da qualche parte.
     */
    private static int follow(Map<Value, Value> attributes)
    {
        Value value = attributes.get(StringValue.of("follow"));
        if (value == null || value.isNull())
        {
            return -1;
        }
        if (value instanceof final com.gnottero.blossom.value.EntityValue entity)
        {
            return entity.getEntity().getId();
        }
        if (value instanceof NumericValue number)
        {
            return (int) number.getLong();
        }
        throw new TypeMismatchError(SourcePosition.UNKNOWN,
                "'follow' wants an entity, or its id.");
    }

    /**
     * Chi vede la forma.
     *
     * <p>Senza {@code player}, tutti quelli nella dimensione in cui lo script sta girando. È il
     * default utile: chi disegna sta quasi sempre guardando quello che succede lì.
     */
    private static List<ServerPlayer> audienceOf(ScriptContext cc, Map<Value, Value> attributes)
    {
        Value declared = attributes.get(StringValue.of("player"));
        if (declared == null || declared.isNull())
        {
            return List.copyOf(cc.level().players());
        }
        List<Value> names = declared instanceof final ListValue list ? list.getItems() : List.of(declared);
        List<ServerPlayer> players = new ArrayList<>();
        for (Value name : names)
        {
            ServerPlayer player = com.gnottero.blossom.value.EntityValue.getPlayerByValue(cc.server(), name);
            if (player != null)
            {
                players.add(player);
            }
        }
        return players;
    }

    private static Vec3 vec(Map<Value, Value> attributes, String key)
    {
        Value value = attributes.get(StringValue.of(key));
        if (!(value instanceof final ListValue list) || list.getItems().size() < 3)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "The shape wants '" + key + "' as a position, e.g. '" + key + "' -> [10, 64, 10].");
        }
        List<Value> items = list.getItems();
        return new Vec3(NumericValue.asNumber(items.get(0)).getDouble(),
                NumericValue.asNumber(items.get(1)).getDouble(),
                NumericValue.asNumber(items.get(2)).getDouble());
    }

    private static double number(Map<Value, Value> attributes, String key, double fallback)
    {
        Value value = attributes.get(StringValue.of(key));
        return value == null || value.isNull() ? fallback : NumericValue.asNumber(value).getDouble();
    }

    private static double positive(Map<Value, Value> attributes, String key)
    {
        double value = number(attributes, key, -1.0);
        if (value <= 0)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "The shape wants '" + key + "' greater than zero.");
        }
        return value;
    }

    private static boolean bool(Map<Value, Value> attributes, String key)
    {
        Value value = attributes.get(StringValue.of(key));
        return value != null && !value.isNull() && value.getBoolean();
    }

    /** Un attributo che può valere solo una fra poche parole. */
    private static String choice(Map<Value, Value> attributes, String key, Set<String> allowed, String fallback)
    {
        Value value = attributes.get(StringValue.of(key));
        if (value == null || value.isNull())
        {
            return fallback;
        }
        String choice = value.getString().toLowerCase(Locale.ROOT);
        if (!allowed.contains(choice))
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'" + key + "' cannot be '" + choice + "'. It takes one of " + allowed.stream().sorted().toList() + ".",
                    Suggestions.closestTo(choice, List.copyOf(allowed)));
        }
        return choice;
    }

    private static Value text(Map<Value, Value> attributes)
    {
        Value value = attributes.get(StringValue.of("value"));
        if (value == null || value.isNull())
        {
            value = attributes.get(StringValue.of("text"));
        }
        if (value == null || value.isNull())
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN, "A label wants a 'text' to show.");
        }
        return value;
    }

    /**
     * Il testo con il suo stile, se chi disegna ne ha passato uno.
     *
     * <p>Un testo di {@code core.mc.text} porta colori e formattazione, e sarebbe un peccato buttarli
     * per la strada: viaggia serializzato accanto alla versione senza stile, che resta quella che
     * arriva in chat a chi non ha il client. Una stringa e basta non ha niente da serializzare, e
     * allora non si manda niente.
     */
    private static net.minecraft.nbt.@org.jspecify.annotations.Nullable Tag component(
            ScriptContext cc, Map<Value, Value> attributes)
    {
        Value value = text(attributes);
        return value instanceof final FormattedTextValue formatted
                ? formatted.serialize(cc.registryAccess())
                : null;
    }

    /**
     * Il colore, come nome o come numero {@code 0xRRGGBBAA}.
     *
     * <p>I nomi non sono una comodità e basta: un colore scritto a mano in esadecimale è la cosa che
     * si sbaglia più spesso, e quasi sempre il canale alfa finisce a zero — cioè la forma non si
     * vede, e sembra che la funzione non abbia funzionato.
     */
    private static int color(Map<Value, Value> attributes, String key, int fallback)
    {
        Value value = attributes.get(StringValue.of(key));
        if (value == null || value.isNull())
        {
            return fallback;
        }
        if (value instanceof NumericValue number)
        {
            return (int) number.getLong();
        }
        String name = value.getString().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(name);
        if (named == null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Unknown colour: '" + name + "'. Use one of " + NAMED_COLORS.keySet()
                            + " or a 0xRRGGBBAA number.",
                    Suggestions.closestTo(name, List.copyOf(NAMED_COLORS.keySet())));
        }
        return named;
    }
}
