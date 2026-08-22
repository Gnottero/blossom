package com.gnottero.blossom.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.argument.BlockArgument;
import com.gnottero.blossom.util.InputValidator;
import blossom.value.BooleanValue;
import blossom.value.NumericValue;
import blossom.value.Value;
import com.gnottero.blossom.value.ValueConversions;

/**
 * {@code core.mc.structure}: saving, placing and comparing structure templates (inventory entries
 * ST-1..ST-4).
 *
 * <p>These are the same templates used by vanilla's structure block, and they live in the same place
 * ({@code <world>/generated/<namespace>/structure/<name>.nbt}): whatever a script saves opens with a
 * structure block, and vice versa. That's not an implementation detail, it's the reason these
 * functions are worth using instead of copying blocks by hand.
 *
 * <h2>Anchoring</h2>
 *
 * <p>Rotation and mirroring rotate the structure <em>around</em> the anchor point, so with a naive
 * anchor a structure rotated by 90 degrees ends up somewhere else. Here the requested position always
 * stays the minimum corner of the transformed result ({@link #anchorOf}): that's what one expects
 * looking at the world, and it makes {@code structure_load} and {@code structure_matches} inverses of
 * each other.
 */
public final class Structures
{
    /** The symbols exported by {@code core.mc.structure} */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "structure_save", "structure_load", "structure_matches", "structure_size");

    private Structures()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * structure_save(name, from, to, include_entities?) - saves a volume as a template.
         *
         * Goes through StructureBlockEntity.saveStructure, i.e. exactly what the structure block
         * does: the resulting file is indistinguishable from one saved by hand.
         */
        expression.addContextFunction("structure_save", -1, (c, t, lv) ->
        {
            if (lv.size() < 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "'structure_save' wants a name and the two corners of the volume to save.");
            }
            ScriptContext cc = (ScriptContext) c;
            Identifier structure = identifier(lv.get(0));
            BlockArgument fromLocator = BlockArgument.findIn(cc, lv, 1);
            BlockArgument toLocator = BlockArgument.findIn(cc, lv, fromLocator.offset);
            boolean includeEntities = lv.size() > toLocator.offset && lv.get(toLocator.offset).getBoolean();

            BlockPos from = fromLocator.block.getPos();
            BlockPos to = toLocator.block.getPos();
            BlockPos origin = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()),
                    Math.min(from.getZ(), to.getZ()));
            Vec3i size = new Vec3i(Math.abs(from.getX() - to.getX()) + 1, Math.abs(from.getY() - to.getY()) + 1,
                    Math.abs(from.getZ() - to.getZ()) + 1);

            ServerLevel level = cc.level();
            Value[] result = {Value.FALSE};
            // writes a file inside the world: must happen on the server thread, like any change
            cc.server().executeBlocking(() -> result[0] = BooleanValue.of(
                    StructureBlockEntity.saveStructure(level, structure, origin, size, !includeEntities, "", true, List.of())));
            return result[0];
        });

        /*
         * structure_load(name, pos, rotation?, mirror?, include_entities?, integrity?, seed?)
         *
         * 'integrity' below 1 removes blocks at random, like the structure block: with a 'seed' the
         * same ruin can be rebuilt identically, which is the only way to make a random generation
         * repeatable.
         */
        expression.addContextFunction("structure_load", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN, "'structure_load' wants a name and a position.");
            }
            ScriptContext cc = (ScriptContext) c;
            Identifier structure = identifier(lv.get(0));
            BlockArgument locator = BlockArgument.findIn(cc, lv, 1);
            Rotation rotation = rotationOf(argument(lv, locator.offset));
            Mirror mirror = mirrorOf(argument(lv, locator.offset + 1));
            boolean includeEntities = lv.size() <= locator.offset + 2 || lv.get(locator.offset + 2).getBoolean();
            float integrity = lv.size() > locator.offset + 3
                    ? Mth.clamp(NumericValue.asNumber(lv.get(locator.offset + 3)).getFloat(), 0.0F, 1.0F)
                    : 1.0F;
            long seed = lv.size() > locator.offset + 4 ? NumericValue.asNumber(lv.get(locator.offset + 4)).getLong() : 0L;

            ServerLevel level = cc.level();
            BlockPos pos = locator.block.getPos();
            Value[] result = {Value.NULL};
            cc.server().executeBlocking(() -> level.getStructureManager().get(structure).ifPresent(template ->
            {
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .setRotation(rotation)
                        .setMirror(mirror)
                        .setIgnoreEntities(!includeEntities);
                RandomSource random = seed == 0L ? RandomSource.create() : RandomSource.create(seed);
                if (integrity < 1.0F)
                {
                    settings.clearProcessors().addProcessor(new BlockRotProcessor(integrity)).setRandom(random);
                }
                BlockPos anchor = anchorOf(template, settings, pos);
                result[0] = BooleanValue.of(template.placeInWorld(level, anchor, anchor, settings, random, Block.UPDATE_CLIENTS));
            }));
            return result[0];
        });

        /*
         * structure_matches(name, from, to, rotation?, mirror?) - does the region match the template?
         *
         * A template can have several palettes, i.e. variants of the same volume: it matches if at
         * least one of them matches. 'structure_void' blocks don't count, same as in vanilla.
         */
        expression.addContextFunction("structure_matches", -1, (c, t, lv) ->
        {
            if (lv.size() < 3)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "'structure_matches' wants a name and the two corners of the region to compare.");
            }
            ScriptContext cc = (ScriptContext) c;
            Identifier structure = identifier(lv.get(0));
            BlockArgument fromLocator = BlockArgument.findIn(cc, lv, 1);
            BlockArgument toLocator = BlockArgument.findIn(cc, lv, fromLocator.offset);
            Rotation rotation = rotationOf(argument(lv, toLocator.offset));
            Mirror mirror = mirrorOf(argument(lv, toLocator.offset + 1));

            ServerLevel level = cc.level();
            Optional<StructureTemplate> template = level.getStructureManager().get(structure);
            if (template.isEmpty())
            {
                return Value.NULL; // the template doesn't exist: different from "doesn't match"
            }
            BlockPos from = fromLocator.block.getPos();
            BlockPos to = toLocator.block.getPos();
            BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()),
                    Math.min(from.getZ(), to.getZ()));
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
            BoundingBox box = template.get().getBoundingBox(settings, BlockPos.ZERO);
            // a region of different size can never match, and checking this first avoids reading
            // thousands of blocks just to reach the same conclusion
            if (Math.abs(from.getX() - to.getX()) + 1 != box.getXSpan()
                    || Math.abs(from.getY() - to.getY()) + 1 != box.getYSpan()
                    || Math.abs(from.getZ() - to.getZ()) + 1 != box.getZSpan())
            {
                return Value.FALSE;
            }
            BlockPos anchor = min.offset(-box.minX(), -box.minY(), -box.minZ());
            for (StructureTemplate.Palette palette : com.gnottero.blossom.access.Vanilla.StructureTemplate_palettes(template.get()))
            {
                if (paletteMatches(level, palette, settings, anchor))
                {
                    return Value.TRUE;
                }
            }
            return Value.FALSE;
        });

        /** structure_size(name, rotation?) - the template's dimensions, rotated if needed. */
        expression.addContextFunction("structure_size", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new ArityError(SourcePosition.UNKNOWN, "'structure_size' wants a structure name.");
            }
            ScriptContext cc = (ScriptContext) c;
            Rotation rotation = rotationOf(argument(lv, 1));
            return cc.level().getStructureManager().get(identifier(lv.get(0)))
                    .map(template -> template.getSize(rotation))
                    .map(size -> ValueConversions.of(new BlockPos(size.getX(), size.getY(), size.getZ())))
                    .orElse(Value.NULL);
        });
    }

    private static Identifier identifier(Value name)
    {
        return InputValidator.identifierOf(name.getString().toLowerCase(Locale.ROOT));
    }

    private static Value argument(List<Value> arguments, int index)
    {
        return index < arguments.size() ? arguments.get(index) : null;
    }

    private static Rotation rotationOf(Value value)
    {
        if (value == null || value.isNull())
        {
            return Rotation.NONE;
        }
        return switch (value.getString().toLowerCase(Locale.ROOT))
        {
            case "none", "0" -> Rotation.NONE;
            case "90", "clockwise_90" -> Rotation.CLOCKWISE_90;
            case "180", "-180", "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "270", "-90", "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Invalid rotation: '" + value.getString() + "'. It must be 0, 90, 180 or 270.");
        };
    }

    private static Mirror mirrorOf(Value value)
    {
        if (value == null || value.isNull())
        {
            return Mirror.NONE;
        }
        String name = value.getString().toLowerCase(Locale.ROOT);
        return switch (name)
        {
            case "none" -> Mirror.NONE;
            case "left_right" -> Mirror.LEFT_RIGHT;
            case "front_back" -> Mirror.FRONT_BACK;
            default -> throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "Invalid mirror: '" + value.getString() + "'.",
                    Suggestions.closestTo(name, List.of("none", "left_right", "front_back")));
        };
    }

    /**
     * Where to anchor the template so that {@code pos} stays the minimum corner of the result.
     *
     * <p>Rotation and mirroring turn the structure around the anchor: without this offset, the same
     * call with a different rotation would place the structure at a different point, and nobody would
     * find that obvious.
     */
    private static BlockPos anchorOf(StructureTemplate template, StructurePlaceSettings settings, BlockPos pos)
    {
        BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
        return pos.offset(-box.minX(), -box.minY(), -box.minZ());
    }

    private static boolean paletteMatches(ServerLevel level, StructureTemplate.Palette palette,
            StructurePlaceSettings settings, BlockPos origin)
    {
        for (StructureTemplate.StructureBlockInfo info : palette.blocks())
        {
            BlockState expected = info.state();
            if (expected.is(Blocks.STRUCTURE_VOID))
            {
                continue; // structure void doesn't say anything about what's actually there
            }
            BlockPos worldPos = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
            if (level.getBlockState(worldPos) != expected.mirror(settings.getMirror()).rotate(settings.getRotation()))
            {
                return false;
            }
        }
        return true;
    }
}
