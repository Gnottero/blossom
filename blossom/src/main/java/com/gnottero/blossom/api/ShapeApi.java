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
 * {@code core.mc.shape}: show what a script is computing.
 *
 * <pre>
 * import "core.mc.shape" as shape;
 *
 * shape.draw_shape('box', 100, {'from' -&gt; [0, 64, 0], 'to' -&gt; [8, 70, 8], 'color' -&gt; 'red'});
 * </pre>
 *
 * <p>A player with Blossom on the client sees the real drawing; a vanilla player sees the same
 * geometry as particles. The script author doesn't need to know who has what. Attributes all live
 * in one map, and a name that doesn't exist for that shape is an error, not a silent no-op.
 */
public final class ShapeApi
{
    /** The symbols exported by {@code core.mc.shape}. */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "draw_shape", "clear_shapes", "shape_types");

    /** Named colors, because nobody wants to type 0xff0000ff by hand. */
    private static final Map<String, Integer> NAMED_COLORS = Map.of(
            "white", 0xFFFFFFFF, "black", 0x000000FF, "red", 0xFF3B30FF, "green", 0x34C759FF,
            "blue", 0x2F7BFFFF, "yellow", 0xFFD60AFF, "cyan", 0x30D5C8FF, "magenta", 0xFF2D95FF,
            "orange", 0xFF9500FF, "grey", 0x8E8E93FF);

    /** Attributes valid for any shape. */
    private static final Set<String> COMMON_ATTRIBUTES =
            Set.of("color", "fill", "line", "player", "follow", "snap", "debug", "seethrough", "dim");

    /** Attributes valid for a single shape type, in addition to {@link #COMMON_ATTRIBUTES}. */
    private static final Map<String, Set<String>> ATTRIBUTES_BY_TYPE = Map.of(
            "line", Set.of("from", "to"),
            "box", Set.of("from", "to"),
            "sphere", Set.of("center", "radius", "level"),
            "cylinder", Set.of("center", "radius", "height", "axis", "level"),
            "polygon", Set.of("points", "mode", "inner", "relative"),
            "label", Set.of("pos", "text", "value", "size", "align", "indent", "height", "raise",
                    "tilt", "lean", "turn", "facing", "doublesided"),
            "block", Set.of("pos", "block", "facing", "tilt", "lean", "turn", "scale",
                    "blocklight", "skylight"),
            "item", Set.of("pos", "item", "variant", "facing", "tilt", "lean", "turn", "scale",
                    "blocklight", "skylight"));

    /** Values accepted by {@code snap}: which axes follow the entity, and which snap to the block. */
    private static final Set<String> SNAP_VALUES = Set.of(
            "xyz", "xz", "yz", "xy", "x", "y", "z",
            "dxdydz", "dxdz", "dydz", "dxdy", "dx", "dy", "dz",
            "xdz", "dxz", "ydz", "dyz", "xdy", "dxy",
            "xydz", "xdyz", "xdydz", "dxyz", "dxydz", "dxdyz");

    /** What a label, or a block drawn in mid-air, can face towards. */
    private static final Set<String> FACINGS = Set.of(
            "player", "camera", "north", "south", "east", "west", "up", "down");

    /** The pose an item can be drawn in: the ways the game already shows it by itself. */
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
         * draw_shape(type, duration_in_ticks, attributes)
         *
         * Only one way to call it, with the attributes in a map: spread across positional
         * arguments it wouldn't be clear where the geometry ends and the options begin.
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

            checkAttributes(type, attributes);
            ServerLevel level = levelOf(cc, attributes);
            long expires = BlossomMod.shapes().tick() + Math.max(1L, duration);
            Shape shape = build(cc, type, attributes, expires, level);
            List<ServerPlayer> audience = audienceOf(cc, attributes, level);
            if (shape instanceof final Shape.Label label)
            {
                // Text can't be approximated with particles: it's sent once in chat to those
                // without the client, so the information isn't lost.
                Component message = label.component() == null
                        ? Component.literal(label.text())
                        : FormattedTextValue.deserialize(label.component(), cc.registryAccess()).getText();
                ServerNetwork.withoutClient(audience)
                        .forEach(player -> player.sendSystemMessage(message, true));
            }
            return new NumericValue(BlossomMod.shapes().draw(shape, level, audience));
        });

        // clear_shapes() - forget the shapes still waiting to be redrawn.
        expression.addContextFunction("clear_shapes", 0, (c, t, lv) ->
        {
            BlossomMod.shapes().clear();
            return Value.TRUE;
        });

        // shape_types() - the types draw_shape accepts.
        expression.addContextFunction("shape_types", 0, (c, t, lv) ->
                ListValue.wrap(Shape.TYPES.stream().map(StringValue::of)));
    }

    /**
     * An attribute name that doesn't exist is an error, not a silent no-op.
     *
     * <p>A misspelled {@code 'radiu'}, accepted silently, would make the shape come out with the
     * default value: something wrong would show up without any clue why. Here it's flagged right
     * away, with the right name suggested.
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

    private static Shape build(ScriptContext cc, String type, Map<Value, Value> attributes, long expires,
            ServerLevel level)
    {
        Shape.Common common = new Shape.Common(
                color(attributes, "color", 0xFFFFFFFF),
                color(attributes, "fill", 0x00000000),
                (float) number(attributes, "line", 2.0),
                expires,
                follow(attributes),
                choice(attributes, "snap", SNAP_VALUES, "xyz"),
                bool(attributes, "debug"),
                bool(attributes, "seethrough"),
                level.dimension().identifier().toString());
        return switch (type)
        {
            case "line" -> new Shape.Line(vec(attributes, "from"), vec(attributes, "to"), common);
            case "box" -> new Shape.Box(vec(attributes, "from"), vec(attributes, "to"), common);
            case "sphere" -> new Shape.Sphere(vec(attributes, "center"), positive(attributes, "radius"),
                    (int) number(attributes, "level", 0), common);
            case "cylinder" -> new Shape.Cylinder(vec(attributes, "center"), positive(attributes, "radius"),
                    number(attributes, "height", 0.0), choice(attributes, "axis", Set.of("x", "y", "z"), "y"),
                    (int) number(attributes, "level", 0), common);
            case "polygon" -> polygon(attributes, common);
            case "label" -> new Shape.Label(vec(attributes, "pos"), text(attributes).getString(),
                    component(cc, attributes), style(attributes), common);
            case "block" -> block(cc, attributes, common);
            case "item" -> item(cc, attributes, common);
            // The type has already been validated in checkAttributes: unreachable.
            default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Unknown shape type: '" + type + "'. The types are " + Shape.TYPES + ".",
                    Suggestions.closestTo(type, Shape.TYPES));
        };
    }

    /**
     * Faces through arbitrary points.
     *
     * <p>{@code relative} says which points follow the entity of {@code follow}: one boolean for all
     * of them, or one per point. Left out, they all follow, which is what {@code follow} means for
     * every other shape.
     */
    private static Shape polygon(Map<Value, Value> attributes, Shape.Common common)
    {
        Value declared = attributes.get(StringValue.of("points"));
        if (!(declared instanceof final ListValue list) || list.getItems().size() < 2)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "A 'polygon' wants its 'points', at least two: 'points' -> [[0, 64, 0], [4, 64, 0], [0, 68, 0]].");
        }
        List<Vec3> points = new ArrayList<>();
        for (Value point : list.getItems())
        {
            points.add(vec(Map.of(StringValue.of("points"), point), "points"));
        }
        String mode = choice(attributes, "mode", Shape.Polygon.MODES, "polygon");
        if (mode.equals("triangles") && points.size() % 3 != 0)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "With 'mode' -> 'triangles' the points come three by three, and there are " + points.size() + ".");
        }
        boolean[] fixed = new boolean[points.size()];
        Value relative = attributes.get(StringValue.of("relative"));
        if (relative instanceof final ListValue flags)
        {
            List<Value> items = flags.getItems();
            if (items.size() != points.size())
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'relative' has " + items.size() + " values for " + points.size() + " points: one per point, or a single one for all.");
            }
            for (int i = 0; i < items.size(); i++)
            {
                fixed[i] = !items.get(i).getBoolean();
            }
        }
        else if (relative != null && !relative.isNull() && !relative.getBoolean())
        {
            java.util.Arrays.fill(fixed, true);
        }
        return new Shape.Polygon(points, mode, bool(attributes, "inner"), fixed, common);
    }

    /**
     * The block to draw in mid-air.
     *
     * <p>Accepts a block written out ({@code 'chest[facing=north]'}) or a block value taken from
     * the world; in the latter case it comes with its data (a chest's contents, a sign's text) and
     * the client needs that to draw it properly.
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
     * The item to draw in mid-air.
     *
     * <p>The whole stack travels, not just the name: enchantments, custom name and data components
     * change how the item looks, and whoever set them expects to see them.
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
        // The name is only for the particle fallback, where the id is enough; the whole stack is in 'data'.
        String name = cc.registry(net.minecraft.core.registries.Registries.ITEM)
                .getKey(stack.getItem()).toString();
        return new Shape.Sprite("item", vec(attributes, "pos"), name, data,
                pose(attributes, "north"), common);
    }

    /** How a block or item drawn in mid-air is positioned in space. */
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

    /** {@code scale} is either a single number, or one per axis. */
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

    /** A fixed light level, or {@code -1} for whatever light there is where the shape stands. */
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

    /** {@code -1} for left, {@code 0} for centered, {@code 1} for right. */
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
     * The entity the shape is attached to.
     *
     * <p>Accepts either the entity itself or its id: the former is what you have on hand inside an
     * event, the latter is what you may have saved somewhere.
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
     * The dimension the shape is drawn in: {@code dim}, or the one the script is running in.
     *
     * <p>It takes what the rest of the API takes for a dimension: a name, an entity or a block.
     */
    private static ServerLevel levelOf(ScriptContext cc, Map<Value, Value> attributes)
    {
        Value declared = attributes.get(StringValue.of("dim"));
        if (declared == null || declared.isNull())
        {
            return cc.level();
        }
        return (ServerLevel) com.gnottero.blossom.value.ValueConversions.dimFromValue(declared, cc.server());
    }

    /**
     * Who sees the shape.
     *
     * <p>Without {@code player}, everyone in the dimension of the shape. That's the useful
     * default: whoever is drawing is almost always looking at what happens there. A player named
     * explicitly but standing in another dimension gets it anyway, and sees it once they get there.
     */
    private static List<ServerPlayer> audienceOf(ScriptContext cc, Map<Value, Value> attributes, ServerLevel level)
    {
        Value declared = attributes.get(StringValue.of("player"));
        if (declared == null || declared.isNull())
        {
            return List.copyOf(level.players());
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

    /** An attribute that can only be one of a few words. */
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
     * The text with its style, if whoever is drawing passed one.
     *
     * <p>A {@code core.mc.text} text carries colors and formatting, and it would be a shame to
     * drop them along the way: it travels serialized alongside the plain version, which stays the
     * one that reaches chat for those without the client. A plain string has nothing to
     * serialize, so nothing is sent.
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
     * The color, as a name or as a {@code 0xRRGGBBAA} number.
     *
     * <p>Names aren't just a convenience: a hand-typed hex color is the mistake that happens most
     * often, and the alpha channel almost always ends up at zero, meaning the shape doesn't show
     * up and it looks like the function didn't work.
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
