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
 * {@code core.mc.structure}: salvare, piazzare e confrontare template di struttura (voci ST-1..ST-4
 * dell'inventario).
 *
 * <p>Sono gli stessi template del blocco struttura di vanilla, e vivono nello stesso posto
 * ({@code <mondo>/generated/<namespace>/structure/<nome>.nbt}): quello che uno script salva si apre
 * con un blocco struttura, e viceversa. Non è un dettaglio implementativo — è il motivo per cui
 * queste funzioni valgono la pena rispetto a copiare blocchi a mano.
 *
 * <h2>L'ancoraggio</h2>
 *
 * <p>Rotazione e specchiatura fanno ruotare la struttura <em>attorno</em> al punto di ancoraggio,
 * quindi con l'ancora ingenua una struttura ruotata di 90° finisce da un'altra parte. Qui la
 * posizione chiesta resta sempre l'angolo minimo del risultato trasformato
 * ({@link #anchorOf}): è quello che uno si aspetta guardando il mondo, e rende
 * {@code structure_load} e {@code structure_matches} reciproci.
 */
public final class Structures
{
    /** i simboli esportati da {@code core.mc.structure} */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "structure_save", "structure_load", "structure_matches", "structure_size");

    private Structures()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * structure_save(nome, da, a, con_entita'?) - salva un volume come template.
         *
         * Passa da StructureBlockEntity.saveStructure, cioe' esattamente quello che fa il blocco
         * struttura: il file che esce e' indistinguibile da uno salvato a mano.
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
            // scrive un file dentro il mondo: va fatto sul thread del server, come ogni modifica
            cc.server().executeBlocking(() -> result[0] = BooleanValue.of(
                    StructureBlockEntity.saveStructure(level, structure, origin, size, !includeEntities, "", true, List.of())));
            return result[0];
        });

        /*
         * structure_load(nome, pos, rotazione?, specchio?, con_entita'?, integrita'?, seme?)
         *
         * 'integrita'' sotto 1 toglie blocchi a caso, come il blocco struttura: con un 'seme' la
         * stessa rovina si puo' ricostruire uguale, che e' l'unico modo di rendere ripetibile una
         * generazione casuale.
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
         * structure_matches(nome, da, a, rotazione?, specchio?) - la regione combacia col template?
         *
         * Un template puo' avere piu' palette, cioe' varianti dello stesso volume: combacia se ne
         * combacia almeno una. I blocchi 'structure_void' non contano, come in vanilla.
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
                return Value.NULL; // il template non esiste: diverso da "non combacia"
            }
            BlockPos from = fromLocator.block.getPos();
            BlockPos to = toLocator.block.getPos();
            BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()),
                    Math.min(from.getZ(), to.getZ()));
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
            BoundingBox box = template.get().getBoundingBox(settings, BlockPos.ZERO);
            // una regione di dimensioni diverse non puo' combaciare, e controllarlo prima evita di
            // leggere migliaia di blocchi per arrivare alla stessa conclusione
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

        /** structure_size(nome, rotazione?) - le dimensioni del template, ruotato se serve. */
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
     * Dove ancorare il template perche' {@code pos} resti l'angolo minimo del risultato.
     *
     * <p>Rotazione e specchiatura girano la struttura attorno all'ancora: senza questo spostamento,
     * la stessa chiamata con rotazione diversa piazzerebbe la struttura in un punto diverso, e
     * nessuno lo troverebbe ovvio.
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
                continue; // il vuoto strutturale non dice niente su cosa ci sia li'
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
