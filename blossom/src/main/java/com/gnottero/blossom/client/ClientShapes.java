package com.gnottero.blossom.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.math.Axis;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.net.BlossomPayloads;
import com.gnottero.blossom.shape.Shape;

/**
 * The shapes the server has sent to this client, and how they get drawn.
 *
 * <p>The geometry does not live here: the {@link Shape} classes are the same ones the server uses
 * to build the shapes, and already know what segments and faces they are made of. The client
 * rebuilds them from NBT, counts ticks, and hands them to the game's drawing primitives. Server
 * and client cannot disagree on what a sphere is, because it is the same class.
 *
 * <p>What only lives here is the text: a label has a pose in space (it looks somewhere, it is
 * tilted, it sits at a certain height), and that pose only exists at the moment there is a camera.
 * The server does not have one and cannot have one, which is why players without the client see
 * the text in chat instead of in the world.
 *
 * <p>Time is counted in client ticks: a duration arrives over the network, not an instant (see
 * {@link Shape#toTag}), so there is no clock to synchronize.
 */
@Environment(EnvType.CLIENT)
public final class ClientShapes
{
    /**
     * The maximum number of shapes kept at once.
     *
     * <p>A limit is needed: on the other end there is a script, and a buggy script drawing in a
     * loop would fill up the client's memory. Past the limit, the oldest shapes are dropped, which
     * are also the ones about to expire anyway.
     */
    private static final int MAX_SHAPES = 4096;

    /** The scale between font units and blocks. */
    private static final float FONT_SCALE = 0.0025F;

    private final List<Shape> shapes = new CopyOnWriteArrayList<>();

    /**
     * What a shape actually is, translated once.
     *
     * <p>What arrives from the server is text: a block's name, an item's NBT, a serialized
     * component. Translating it is costly (a parser and the registries are involved), and it needs
     * to happen once, not every frame. The keys are the shapes themselves, which do not define
     * {@code equals}: two equal shapes remain two entries, which is what is wanted, since they die
     * at different times.
     */
    private final Map<Shape, Model> models = new ConcurrentHashMap<>();
    private final Map<Shape, Component> texts = new ConcurrentHashMap<>();

    private long tick = 0;

    /** The fluid renderer, and the models it was built with, see {@link #fluidRenderer}. */
    private @org.jspecify.annotations.Nullable FluidRenderer fluidRenderer = null;
    private @org.jspecify.annotations.Nullable FluidStateModelSet fluidModels = null;

    /**
     * A block or item ready to be drawn.
     *
     * @param state       the block state, or {@code null} if the shape is an item or the block
     *                    does not exist on this client
     * @param stack       the item, or {@code null} if the shape is a block
     * @param blockEntity the part of the block that has its own renderer (a chest, a sign), if it
     *                    has one
     */
    private record Model(net.minecraft.world.level.block.state.@org.jspecify.annotations.Nullable BlockState state,
            net.minecraft.world.item.@org.jspecify.annotations.Nullable ItemStack stack,
            net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity)
    {
        static final Model NONE = new Model(null, null, null);
    }

    /** Reads a packet of shapes. Called from the network thread, so the list is concurrent. */
    public void accept(CompoundTag data)
    {
        ListTag list = data.getList(BlossomPayloads.SHAPES_KEY).orElse(null);
        if (list == null)
        {
            return;
        }
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag tag = list.getCompound(i).orElse(null);
            if (tag == null)
            {
                continue;
            }
            Shape shape = Shape.fromTag(tag, tick);
            // null means a type this client doesn't know: a newer server. That shape is skipped
            // and the others still arrive
            if (shape != null)
            {
                shapes.add(shape);
            }
        }
        while (shapes.size() > MAX_SHAPES)
        {
            shapes.removeFirst();
        }
    }

    /** To be called on every client tick. */
    public void onTick()
    {
        tick++;
        if (!shapes.isEmpty())
        {
            shapes.removeIf(shape -> !shape.isAliveAt(tick));
        }
        if (!models.isEmpty())
        {
            models.keySet().removeIf(shape -> !shape.isAliveAt(tick));
        }
        if (!texts.isEmpty())
        {
            texts.keySet().removeIf(shape -> !shape.isAliveAt(tick));
        }
    }

    /** Drops everything: done when leaving a world, since shapes belong to the session. */
    public void clear()
    {
        shapes.clear();
        models.clear();
        texts.clear();
        tick = 0;
    }

    public boolean isEmpty()
    {
        return shapes.isEmpty();
    }

    /**
     * Whether some shape has to show through the blocks: the game only runs its always-on-top pass
     * when it has something to draw there itself, and this is how it learns that we do.
     */
    public boolean hasSeethrough()
    {
        for (Shape shape : shapes)
        {
            if (shape.seethrough())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the shapes among the frame's primitives.
     *
     * <p>Line coordinates stay in world space: it is the primitive that knows where the camera is
     * in this frame. Grabbing the camera position here, as the first version in the fork did,
     * means sampling it at a slightly different moment than the rest of the scene, and the drawing
     * ends up chasing the camera instead of staying still. Text, on the other hand, needs the
     * camera position no matter what, since its pose depends on what it is facing: there it uses
     * the one from the frame's render state, the same one everything else sees.
     */
    public void submit(DrawableGizmoPrimitives primitives, DrawableGizmoPrimitives onTop,
            SubmitNodeStorage collector, CameraRenderState camera, float partialTick)
    {
        Minecraft client = Minecraft.getInstance();
        boolean debugVisible = client.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
        String here = client.level == null ? "" : client.level.dimension().identifier().toString();
        for (Shape shape : shapes)
        {
            if (!shape.dimension().isEmpty() && !shape.dimension().equals(here))
            {
                // drawn in another dimension: kept, and shown again when the player goes back
                continue;
            }
            if (shape.debug() && !debugVisible)
            {
                // a 'debug' shape is only visible when hitboxes are shown: it is meant to stay on
                // for a long time without cluttering the view of someone actually playing
                continue;
            }
            Vec3 followPos = followPositionOf(shape, client, partialTick);
            if (shape instanceof final Shape.Label label)
            {
                submitLabel(label, followPos, collector, camera, client.font);
                continue;
            }
            if (shape instanceof final Shape.Sprite sprite)
            {
                submitSprite(sprite, followPos, collector, camera, partialTick, client);
                continue;
            }
            // a seethrough shape goes among the primitives the game draws over everything else
            DrawableGizmoPrimitives target = shape.seethrough() ? onTop : primitives;
            if (shape.hasLines())
            {
                // zero alpha in the color means "no wireframe": a solid, smooth shape
                int color = argb(shape.color());
                float width = shape.lineWidth();
                for (Shape.Segment segment : shape.segments())
                {
                    target.addLine(shape.anchored(segment.from(), followPos),
                            shape.anchored(segment.to(), followPos), color, width);
                }
            }
            if (!shape.hasFill())
            {
                continue;
            }
            List<Vec3[]> faces = anchoredFaces(shape, followPos);
            if (shape.seethrough())
            {
                // over everything there is nothing to cover or to blend in the right order: the
                // primitives of the game are enough, opaque or not
                submitFillAsPrimitives(faces, argb(shape.fillColor()), onTop);
                continue;
            }
            if (shape.hasOpaqueFill())
            {
                // the game's primitives don't know how to draw an opaque covering face: see
                // BlossomRenderTypes
                submitFill(faces, argb(shape.fillColor()), BlossomRenderTypes.opaqueFill(), collector, camera);
                continue;
            }
            // nor one that blends with what's behind it in the right proportion: see
            // AfterCloudsSubmits
            submitTranslucentFill(faces, argb(shape.fillColor()), collector, camera);
        }
    }

    private static void submitFillAsPrimitives(List<Vec3[]> faces, int color, DrawableGizmoPrimitives primitives)
    {
        for (Vec3[] face : faces)
        {
            if (face.length == 4)
            {
                primitives.addQuad(face[0], face[1], face[2], face[3], color);
            }
            else if (face.length > 2)
            {
                primitives.addTriangleFan(face, color);
            }
        }
    }

    /** The shape's faces, moved to wherever the followed entity is now. */
    private static List<Vec3[]> anchoredFaces(Shape shape, @org.jspecify.annotations.Nullable Vec3 followPos)
    {
        List<Vec3[]> faces = shape.faces();
        List<Vec3[]> anchored = new java.util.ArrayList<>(faces.size());
        for (Vec3[] face : faces)
        {
            Vec3[] moved = new Vec3[face.length];
            for (int i = 0; i < face.length; i++)
            {
                moved[i] = shape.anchored(face[i], followPos);
            }
            anchored.add(moved);
        }
        return anchored;
    }

    /**
     * The fill, with the draw mode it deserves.
     *
     * <p>A polygon with more than four vertices becomes a triangle fan, and every triangle a quad
     * with its last vertex repeated: the pipeline draws quads, and repeating a vertex is the way to
     * hand it a triangle without changing its topology.
     */
    private static void submitFill(List<Vec3[]> faces, int color, RenderType renderType,
            SubmitNodeStorage collector, CameraRenderState camera)
    {
        PoseStack pose = new PoseStack();
        pose.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
        collector.submitCustomGeometry(pose, renderType, geometry(faces, color));
    }

    /**
     * The translucent fill, drawn after the clouds.
     *
     * <p>The faces are the same and so is the draw mode, since {@code debugQuads} is what the game
     * primitives would use, but the moment is not: submitted in the normal phase they would land
     * before the clouds, which would cover them entirely instead of in proportion to alpha. See
     * {@link AfterCloudsSubmits}.
     *
     * <p>With improved transparency (OIT) on the problem does not exist, because there submission
     * order does not matter: the normal path is taken, which in that case is also the only one the
     * game runs.
     */
    private static void submitTranslucentFill(List<Vec3[]> faces, int color, SubmitNodeStorage collector,
            CameraRenderState camera)
    {
        PoseStack pose = new PoseStack();
        pose.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
        RenderType renderType = RenderTypes.debugQuads();
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency())
        {
            collector.submitCustomGeometry(pose, renderType, geometry(faces, color));
            return;
        }
        ((AfterCloudsSubmits) collector.order(0)).blossom$submitAfterClouds(pose.last().copy(), renderType,
                geometry(faces, color));
    }

    /** Le facce, pronte da scrivere in un buffer di vertici. */
    private static SubmitNodeCollector.CustomGeometryRenderer geometry(List<Vec3[]> faces, int color)
    {
        return (last, consumer) ->
        {
            for (Vec3[] face : faces)
            {
                if (face.length < 3)
                {
                    continue;
                }
                if (face.length == 4)
                {
                    quad(consumer, last, face[0], face[1], face[2], face[3], color);
                    continue;
                }
                for (int i = 1; i < face.length - 1; i++)
                {
                    quad(consumer, last, face[0], face[i], face[i + 1], face[i + 1], color);
                }
            }
        };
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int color)
    {
        vertex(consumer, pose, a, color);
        vertex(consumer, pose, b, color);
        vertex(consumer, pose, c, color);
        vertex(consumer, pose, d, color);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, int color)
    {
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }

    /**
     * The text, in the pose it was asked for.
     *
     * <p>The order of the transformations is not free: first move to the point, then turn towards
     * what has to be faced, then scale into font units, and only afterwards apply tilts and
     * offsets, which are expressed in those units. Turning after moving would rotate the offset
     * too, which is not what the script means.
     */
    private void submitLabel(Shape.Label label, Vec3 followPos, SubmitNodeCollector collector,
            CameraRenderState camera, Font font)
    {
        Shape.Label.Text style = label.style();
        Vec3 pos = label.anchored(label.pos(), followPos);
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - camera.pos.x, pos.y - camera.pos.y, pos.z - camera.pos.z);
        face(pose, style.facing(), pos, camera);
        pose.scale(style.size() * FONT_SCALE, -style.size() * FONT_SCALE, style.size() * FONT_SCALE);
        if (style.tilt() != 0.0F)
        {
            pose.rotate(Axis.ZP.rotationDegrees(style.tilt()));
        }
        if (style.lean() != 0.0F)
        {
            pose.rotate(Axis.XP.rotationDegrees(style.lean()));
        }
        if (style.turn() != 0.0F)
        {
            pose.rotate(Axis.YP.rotationDegrees(style.turn()));
        }
        pose.translate(-10 * style.indent(), -10 * style.height() - 9, -10 * style.raise());
        pose.scale(-1, 1, 1);

        Component text = textOf(label);
        float x = switch (style.align())
        {
            case 0 -> -font.width(text) / 2.0F;
            case 1 -> -font.width(text);
            default -> 0.0F;
        };
        int color = argb(label.color());
        int background = label.hasFill() ? argb(label.fillColor()) : 0;
        Font.DisplayMode mode = label.seethrough() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        collector.submitText(pose, x, 0.0F, text.getVisualOrderText(), false, mode,
                LightCoordsUtil.FULL_BRIGHT, color, background, 0);
        if (style.doublesided())
        {
            // the game primitives do not allow culling to be turned off: the second face is drawn
            // by writing the same text again, flipped, which is what one wants to see from behind
            pose.rotate(Axis.YP.rotationDegrees(180));
            collector.submitText(pose, x, 0.0F, text.getVisualOrderText(), false, mode,
                    LightCoordsUtil.FULL_BRIGHT, color, background, 0);
        }
    }

    /**
     * The label text, with the style it had in the script.
     *
     * <p>Whoever drew the label with a {@code core.mc.text} text expects their colors back: that
     * text travels serialized and is decoded here, once. A plain string has nothing to decode, and
     * becomes the text it is.
     */
    private Component textOf(Shape.Label label)
    {
        if (label.component() == null)
        {
            return Component.literal(label.text());
        }
        return texts.computeIfAbsent(label, shape ->
        {
            Minecraft client = Minecraft.getInstance();
            RegistryAccess registries = client.level == null
                    ? RegistryAccess.EMPTY
                    : client.level.registryAccess();
            return ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), label.component())
                    .result()
                    .orElseGet(() -> Component.literal(label.text()));
        });
    }

    /**
     * A block or an item, drawn where there is none.
     *
     * <p>The transformations are in the same order as the label and for the same reason: first the
     * point, then the facing, then the scale, and only at the end the offset that puts the origin
     * where it belongs. A block turns around its own center and not around its corner, which is
     * what one expects watching it rotate, and that is why the last half block is subtracted last.
     */
    private void submitSprite(Shape.Sprite sprite, @org.jspecify.annotations.Nullable Vec3 followPos,
            SubmitNodeStorage collector, CameraRenderState camera, float partialTick, Minecraft client)
    {
        if (client.level == null)
        {
            return;
        }
        Model model = models.computeIfAbsent(sprite, shape -> resolve((Shape.Sprite) shape, client));
        if (model.state() == null && model.stack() == null)
        {
            return; // a block or an item this client does not know: there is nothing to draw
        }
        Shape.Sprite.Pose style = sprite.pose();
        Vec3 pos = sprite.anchored(sprite.pos(), followPos);
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - camera.pos.x, pos.y - camera.pos.y, pos.z - camera.pos.z);
        // a block occupies space from its corner onwards: it is its center that has to face the
        // viewer, not the edge
        face(pose, style.facing(), sprite.isItem() ? pos : pos.add(0.5, 0.5, 0.5), camera);
        if (style.tilt() != 0.0F)
        {
            pose.rotate(Axis.ZP.rotationDegrees(-style.tilt()));
        }
        if (style.lean() != 0.0F)
        {
            pose.rotate(Axis.XP.rotationDegrees(-style.lean()));
        }
        if (style.turn() != 0.0F)
        {
            pose.rotate(Axis.YP.rotationDegrees(style.turn()));
        }
        pose.scale(style.scaleX(), style.scaleY(), style.scaleZ());
        if (sprite.isItem())
        {
            // the game draws items facing the other way
            pose.rotate(Axis.YP.rotationDegrees(180));
        }
        else
        {
            pose.translate(-0.5, -0.5, -0.5);
        }

        BlockPos blockPos = BlockPos.containing(pos);
        int light = LightCoordsUtil.pack(
                style.blockLight() < 0 ? client.level.getBrightness(LightLayer.BLOCK, blockPos) : style.blockLight(),
                style.skyLight() < 0 ? client.level.getBrightness(LightLayer.SKY, blockPos) : style.skyLight());

        if (sprite.isItem())
        {
            ItemStackRenderState state = new ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, model.stack(), displayContext(style.variant()),
                    client.level, null, 0);
            state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
            return;
        }
        if (model.state().getRenderShape() == RenderShape.MODEL)
        {
            BlockModelRenderState renderState = new BlockModelRenderState();
            client.getBlockEntityRenderDispatcher().blockModelResolver.update(renderState, model.state(),
                    DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
            renderState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
        }
        submitFluid(model.state(), pose, collector, light, blockPos, client);
        submitBlockEntity(model.blockEntity(), pose, collector, camera, partialTick, client);
    }

    /**
     * The water (or lava) of a block drawn in mid air.
     *
     * <p>The game does not draw it together with the model: a fluid is not a model, it is a surface
     * computed by looking at the neighbours, how high it is here, how high over there, which face is
     * hidden, and only the chunk mesh builder does that. A block drawn where there is no chunk has
     * no such pass, and a {@code water} or a {@code waterlogged=true} chest would reach the eye dry.
     *
     * <p>So the game's own {@link FluidRenderer} is called, but on a fake world made of a single
     * block: ours at the origin, air all around. The coordinates it writes are relative to the
     * section ({@code pos & 15}), and from the origin they come out in {@code [0,1]}, the same space
     * as the block model. The biome color instead is asked of the real world at the real position:
     * the water of a swamp has to stay green in mid air too.
     */
    private void submitFluid(BlockState state, PoseStack pose, SubmitNodeStorage collector, int light,
            BlockPos worldPos, Minecraft client)
    {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty() || client.level == null)
        {
            return;
        }
        FluidStateModelSet models = client.getModelManager().getFluidStateModelSet();
        ChunkSectionLayer layer = models.get(fluid).layer();
        RenderType renderType = switch (layer)
        {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
        FluidRenderer renderer = fluidRenderer(models);
        BlockAndTintGetter view = new SingleBlockView(client.level, state, worldPos, light);
        collector.submitCustomGeometry(pose, renderType, (last, consumer) -> renderer.tesselate(view,
                BlockPos.ZERO, requested -> new Posed(consumer, last), state, fluid));
    }

    /**
     * The fluid renderer, kept as long as the models stay the same.
     *
     * <p>Building it every frame would not cost much, but not building it costs nothing; and after a
     * resource reload the models are different ones, so the key is the model set itself.
     */
    private FluidRenderer fluidRenderer(FluidStateModelSet models)
    {
        FluidRenderer renderer = fluidRenderer;
        if (renderer == null || fluidModels != models)
        {
            renderer = new FluidRenderer(models);
            fluidModels = models;
            fluidRenderer = renderer;
        }
        return renderer;
    }

    /**
     * A world of a single block: ours at the origin, air everywhere else.
     *
     * <p>Needed by {@link FluidRenderer}, which asks a world for neighbours and light. There are no
     * neighbours here by choice, since the fluid has to keep all of its faces the way an isolated
     * block does, while the light is the one the shape asked for and the color is the real world's.
     */
    private record SingleBlockView(net.minecraft.client.multiplayer.ClientLevel level, BlockState state,
            BlockPos worldPos, int light) implements BlockAndTintGetter
    {
        @Override
        public BlockState getBlockState(BlockPos pos)
        {
            return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos)
        {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity getBlockEntity(
                BlockPos pos)
        {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos)
        {
            return layer == LightLayer.SKY ? LightCoordsUtil.sky(light) : LightCoordsUtil.block(light);
        }

        @Override
        public LevelLightEngine getLightEngine()
        {
            return level.getLightEngine();
        }

        @Override
        public CardinalLighting cardinalLighting()
        {
            return level.cardinalLighting();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver)
        {
            return level.getBlockTint(worldPos, resolver);
        }

        @Override
        public int getHeight()
        {
            return level.getHeight();
        }

        @Override
        public int getMinY()
        {
            return level.getMinY();
        }
    }

    /**
     * A vertex buffer that carries a pose with it.
     *
     * <p>{@link FluidRenderer} writes raw coordinates, as it would inside a chunk; here instead the
     * block sits where the shape said, turned and scaled as it said. This applies the pose.
     */
    private record Posed(VertexConsumer delegate, PoseStack.Pose pose) implements VertexConsumer
    {
        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            org.joml.Vector3f point = pose.pose().transformPosition(x, y, z, new org.joml.Vector3f());
            delegate.addVertex(point.x, point.y, point.z);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            org.joml.Vector3f normal = pose.transformNormal(x, y, z, new org.joml.Vector3f());
            delegate.setNormal(normal.x, normal.y, normal.z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha)
        {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color)
        {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v)
        {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v)
        {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v)
        {
            delegate.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width)
        {
            delegate.setLineWidth(width);
            return this;
        }
    }

    /**
     * The part of a block that has a renderer of its own: the lid of a chest, the text of a sign,
     * the head of a skeleton. Without this, a chest drawn in mid air would be an empty box with no
     * lid.
     */
    private static void submitBlockEntity(net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity,
            PoseStack pose, SubmitNodeStorage collector, CameraRenderState camera, float partialTick,
            Minecraft client)
    {
        if (blockEntity == null)
        {
            return;
        }
        BlockEntityRenderer<net.minecraft.world.level.block.entity.BlockEntity, BlockEntityRenderState> renderer =
                client.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null)
        {
            return;
        }
        BlockEntityRenderState state =
                client.getBlockEntityRenderDispatcher().tryExtractRenderState(blockEntity, partialTick, null, true);
        if (state != null)
        {
            renderer.submit(state, pose, collector, camera);
        }
    }

    /** The pose an item is drawn in: {@code none} is how it looks lying on the ground. */
    private static ItemDisplayContext displayContext(String variant)
    {
        try
        {
            return ItemDisplayContext.valueOf(variant.toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException ignored)
        {
            return ItemDisplayContext.NONE;
        }
    }

    /**
     * From the text that arrived over the network to the model the game can draw.
     *
     * <p>Done once per shape: parsing a block name and building its block entity every frame would
     * mean paying at sixty hertz for something that never changes.
     */
    private static Model resolve(Shape.Sprite sprite, Minecraft client)
    {
        if (client.level == null)
        {
            return Model.NONE;
        }
        RegistryAccess registries = client.level.registryAccess();
        if (sprite.isItem())
        {
            CompoundTag data = sprite.data();
            if (data == null)
            {
                return Model.NONE;
            }
            return ItemStack.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), data)
                    .result()
                    .map(stack -> new Model(null, stack, null))
                    .orElse(Model.NONE);
        }
        BlockState state;
        try
        {
            state = BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK),
                    new StringReader(sprite.name()), true).blockState();
        }
        catch (CommandSyntaxException unknown)
        {
            // a block this client does not know: a server with mods that are not here
            return Model.NONE;
        }
        return new Model(state, null, blockEntityOf(state, sprite, client));
    }

    /** The block entity of the block, with the data the server sent, if the block has one. */
    private static net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntityOf(
            BlockState state, Shape.Sprite sprite, Minecraft client)
    {
        if (!(state.getBlock() instanceof final EntityBlock entityBlock) || client.level == null)
        {
            return null;
        }
        BlockPos pos = BlockPos.containing(sprite.pos());
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null)
        {
            return null;
        }
        blockEntity.setLevel(client.level);
        CompoundTag data = sprite.data();
        if (data != null)
        {
            try (ProblemReporter.ScopedCollector problems =
                    new ProblemReporter.ScopedCollector(blockEntity.problemPath(), BlossomMod.LOG))
            {
                blockEntity.loadWithComponents(
                        TagValueInput.create(problems, client.level.registryAccess(), data));
            }
        }
        return blockEntity;
    }

    /** Turns the pose towards what the label has to face. */
    private static void face(PoseStack pose, String facing, Vec3 pos, CameraRenderState camera)
    {
        switch (facing)
        {
            case "north" ->
            {
            }
            case "south" -> pose.rotate(Axis.YP.rotationDegrees(180));
            case "east" -> pose.rotate(Axis.YP.rotationDegrees(270));
            case "west" -> pose.rotate(Axis.YP.rotationDegrees(90));
            case "up" -> pose.rotate(Axis.XP.rotationDegrees(90));
            case "down" -> pose.rotate(Axis.XP.rotationDegrees(-90));
            case "camera" -> pose.rotate(camera.orientation);
            // 'player': faces the viewer, but stays upright: the text does not tilt along with the
            // viewer's head, which is the difference from 'camera'
            default ->
            {
                Vec3 toCamera = pos.subtract(camera.pos);
                double flat = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
                pose.rotate(Axis.YP.rotation((float) Math.atan2(toCamera.x, toCamera.z)));
                pose.rotate(Axis.XP.rotation((float) -Math.atan2(toCamera.y, flat)));
            }
        }
    }

    /** Where the followed entity is now, interpolated within the frame, or {@code null} if gone. */
    private static @org.jspecify.annotations.Nullable Vec3 followPositionOf(Shape shape, Minecraft client,
            float partialTick)
    {
        if (shape.follow() < 0 || client.level == null)
        {
            return null;
        }
        Entity entity = client.level.getEntity(shape.follow());
        return entity == null ? null : entity.getPosition(partialTick);
    }

    /**
     * From {@code 0xRRGGBBAA}, the way colors are written in a script, to {@code 0xAARRGGBB}, which
     * is what the game primitives expect.
     */
    private static int argb(int rgba)
    {
        return (rgba >>> 8) | (rgba << 24);
    }
}
