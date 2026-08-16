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
 * Le forme che il server ha mandato a questo client, e il loro disegno.
 *
 * <p>La geometria non sta qui: le classi di {@link Shape} sono le stesse che il server usa per
 * costruire le forme, e sanno già di quali segmenti e facce sono fatte. Il client le ricostruisce
 * dal NBT, conta i tick, e le passa alle primitive di disegno del gioco. Server e client non possono
 * divergere su cosa sia una sfera, perché è la stessa classe.
 *
 * <p>Quello che invece sta solo qui è il testo: un'etichetta ha una posa nello spazio - guarda da
 * qualche parte, è inclinata, è alta così — e quella posa esiste soltanto nel momento in cui c'è una
 * camera. Il server non ce l'ha e non può averla, per questo a chi non ha il client il testo arriva
 * in chat invece che nel mondo.
 *
 * <p>Il tempo si conta in tick del client: dalla rete arriva una durata, non un istante (vedi
 * {@link Shape#toTag}), quindi non c'è nessun orologio da sincronizzare.
 */
@Environment(EnvType.CLIENT)
public final class ClientShapes
{
    /**
     * Quante forme si tengono al massimo.
     *
     * <p>Un limite serve: dall'altra parte c'è uno script, e uno script sbagliato che disegna in un
     * ciclo riempirebbe la memoria del client. Oltre il limite si buttano le più vecchie, che sono
     * anche quelle che scadranno prima.
     */
    private static final int MAX_SHAPES = 4096;

    /** la scala fra le unità del font e i blocchi */
    private static final float FONT_SCALE = 0.0025F;

    private final List<Shape> shapes = new CopyOnWriteArrayList<>();

    /**
     * Quello che una forma è, tradotto una volta sola.
     *
     * <p>Dal server arriva del testo: il nome di un blocco, l'NBT di un item, un componente
     * serializzato. Tradurlo costa - c'è di mezzo un parser e i registri - e va fatto una volta e non
     * a ogni frame. Le chiavi sono le forme stesse, che non definiscono {@code equals}: due forme
     * uguali restano due voci, ed è quello che si vuole, perché muoiono in momenti diversi.
     */
    private final Map<Shape, Model> models = new ConcurrentHashMap<>();
    private final Map<Shape, Component> texts = new ConcurrentHashMap<>();

    private long tick = 0;

    /** il disegnatore di liquidi e i modelli con cui è stato costruito, vedi {@link #fluidRenderer} */
    private @org.jspecify.annotations.Nullable FluidRenderer fluidRenderer = null;
    private @org.jspecify.annotations.Nullable FluidStateModelSet fluidModels = null;

    /**
     * Un blocco o un item pronto da disegnare.
     *
     * @param state       lo stato del blocco, o {@code null} se la forma è un item o il blocco non
     *                    esiste su questo client
     * @param stack       l'item, o {@code null} se la forma è un blocco
     * @param blockEntity la parte del blocco che ha un renderer suo (un baule, un cartello), se ce l'ha
     */
    private record Model(net.minecraft.world.level.block.state.@org.jspecify.annotations.Nullable BlockState state,
            net.minecraft.world.item.@org.jspecify.annotations.Nullable ItemStack stack,
            net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity)
    {
        static final Model NONE = new Model(null, null, null);
    }

    /** Legge un pacchetto di forme. Chiamata dal thread di rete, quindi la lista è concorrente. */
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
            // null vuol dire un tipo che questo client non conosce: un server piu' nuovo. Si salta
            // quella forma e le altre arrivano lo stesso
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

    /** Da chiamare a ogni tick del client. */
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

    /** Butta tutto: si fa uscendo da un mondo, perché le forme appartengono alla partita. */
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
     * Mette le forme fra le primitive del frame.
     *
     * <p>Le coordinate delle linee restano quelle del mondo: è la primitiva a sapere dov'è la camera
     * in questo frame. Prendere la posizione della camera qui - come faceva la prima versione nel
     * fork - significa campionarla in un momento leggermente diverso da quello del resto della
     * scena, e il disegno insegue la camera invece di stare fermo. Il testo invece la posizione
     * della camera ce l'ha per forza, perché la sua posa dipende da dove si guarda: lì si usa quella
     * dello stato di rendering del frame, che è la stessa che vede tutto il resto.
     */
    public void submit(DrawableGizmoPrimitives primitives, SubmitNodeStorage collector,
            CameraRenderState camera, float partialTick)
    {
        Minecraft client = Minecraft.getInstance();
        boolean debugVisible = client.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
        for (Shape shape : shapes)
        {
            if (shape.debug() && !debugVisible)
            {
                // una forma 'debug' si vede solo quando si guardano le hitbox: e' fatta per stare
                // accesa a lungo senza sporcare la vista di chi sta giocando
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
            if (shape.hasLines())
            {
                // alfa zero nel colore vuol dire "niente reticolo": una forma piena e liscia
                int color = argb(shape.color());
                float width = shape.lineWidth();
                for (Shape.Segment segment : shape.segments())
                {
                    primitives.addLine(shape.anchored(segment.from(), followPos),
                            shape.anchored(segment.to(), followPos), color, width);
                }
            }
            if (!shape.hasFill())
            {
                continue;
            }
            List<Vec3[]> faces = anchoredFaces(shape, followPos);
            if (shape.hasOpaqueFill())
            {
                // le primitive del gioco non sanno disegnare una faccia che copre: vedi
                // BlossomRenderTypes
                submitFill(faces, argb(shape.fillColor()), BlossomRenderTypes.opaqueFill(), collector, camera);
                continue;
            }
            // e non sanno disegnarne una che mescola quello che ha dietro nella giusta proporzione:
            // vedi AfterCloudsSubmits
            submitTranslucentFill(faces, argb(shape.fillColor()), collector, camera);
        }
    }

    /** Le facce della forma, portate dove sta adesso l'entità seguita. */
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
     * Il riempimento, col modo di disegnare che gli tocca.
     *
     * <p>Un poligono di più di quattro vertici diventa un ventaglio di triangoli, e ogni triangolo un
     * quadrilatero con l'ultimo vertice ripetuto: la pipeline disegna quadrilateri, e ripetere un
     * vertice è il modo di darle un triangolo senza cambiarle topologia.
     */
    private static void submitFill(List<Vec3[]> faces, int color, RenderType renderType,
            SubmitNodeStorage collector, CameraRenderState camera)
    {
        PoseStack pose = new PoseStack();
        pose.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
        collector.submitCustomGeometry(pose, renderType, geometry(faces, color));
    }

    /**
     * Il riempimento traslucido, disegnato dopo le nuvole.
     *
     * <p>Le facce sono le stesse e il modo di disegnarle pure - {@code debugQuads} è quello che
     * userebbero le primitive del gioco - ma il momento no: consegnate nella fase normale
     * finirebbero prima delle nuvole, che le coprirebbero del tutto invece che in proporzione
     * all'alfa. Vedi {@link AfterCloudsSubmits}.
     *
     * <p>Con la trasparenza migliorata (OIT) accesa il problema non esiste, perché lì l'ordine di
     * consegna non conta: si passa dalla via normale, che in quel caso è anche l'unica che il gioco
     * esegue.
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
     * Il testo, con la posa che gli è stata chiesta.
     *
     * <p>L'ordine delle trasformazioni non è libero: prima si va nel punto, poi si gira verso quello
     * che si deve guardare, poi si scala nelle unità del font, e solo dopo si applicano inclinazioni
     * e spostamenti — che sono espressi in quelle unità. Girare dopo aver spostato vorrebbe dire far
     * ruotare anche lo spostamento, che non è quello che chi scrive intende.
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
            pose.mulPose(Axis.ZP.rotationDegrees(style.tilt()));
        }
        if (style.lean() != 0.0F)
        {
            pose.mulPose(Axis.XP.rotationDegrees(style.lean()));
        }
        if (style.turn() != 0.0F)
        {
            pose.mulPose(Axis.YP.rotationDegrees(style.turn()));
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
        collector.submitText(pose, x, 0.0F, text.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT, color, background, 0);
        if (style.doublesided())
        {
            // le primitive del gioco non lasciano spegnere il culling: la seconda faccia si ottiene
            // scrivendo di nuovo lo stesso testo girato, che e' quello che si vuole vedere da dietro
            pose.mulPose(Axis.YP.rotationDegrees(180));
            collector.submitText(pose, x, 0.0F, text.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                    LightCoordsUtil.FULL_BRIGHT, color, background, 0);
        }
    }

    /**
     * Il testo dell'etichetta, con lo stile che aveva nello script.
     *
     * <p>Chi ha disegnato l'etichetta con un testo di {@code core.mc.text} si aspetta di rivedere i
     * suoi colori: quel testo viaggia serializzato e si decodifica qui, una volta sola. Una stringa e
     * basta non ha niente da decodificare, e diventa il testo che è.
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
     * Un blocco o un item, disegnato dove non ce n'è nessuno.
     *
     * <p>Le trasformazioni sono nello stesso ordine dell'etichetta e per lo stesso motivo: prima il
     * punto, poi il verso, poi la scala, e solo alla fine lo spostamento che porta l'origine dove
     * deve stare. Un blocco gira attorno al proprio centro e non attorno al suo angolo — è quello che
     * uno si aspetta guardandolo ruotare - e per questo l'ultimo mezzo blocco si toglie dopo.
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
            return; // un blocco o un item che questo client non conosce: non c'e' niente da disegnare
        }
        Shape.Sprite.Pose style = sprite.pose();
        Vec3 pos = sprite.anchored(sprite.pos(), followPos);
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - camera.pos.x, pos.y - camera.pos.y, pos.z - camera.pos.z);
        // un blocco occupa lo spazio dal suo angolo in avanti: e' il suo centro che deve guardare
        // chi legge, non lo spigolo
        face(pose, style.facing(), sprite.isItem() ? pos : pos.add(0.5, 0.5, 0.5), camera);
        if (style.tilt() != 0.0F)
        {
            pose.mulPose(Axis.ZP.rotationDegrees(-style.tilt()));
        }
        if (style.lean() != 0.0F)
        {
            pose.mulPose(Axis.XP.rotationDegrees(-style.lean()));
        }
        if (style.turn() != 0.0F)
        {
            pose.mulPose(Axis.YP.rotationDegrees(style.turn()));
        }
        pose.scale(style.scaleX(), style.scaleY(), style.scaleZ());
        if (sprite.isItem())
        {
            // gli item il gioco li disegna guardando dall'altra parte
            pose.mulPose(Axis.YP.rotationDegrees(180));
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
     * L'acqua (o la lava) di un blocco disegnato a mezz'aria.
     *
     * <p>Il gioco non la disegna insieme al modello: il liquido non è un modello, è una superficie
     * che si calcola guardando i vicini - quanto è alto qui, quanto di là, quale faccia è nascosta -
     * e questo lo fa solo chi costruisce le mesh dei chunk. Un blocco disegnato dove non c'è nessun
     * chunk quel passaggio non ce l'ha, e un {@code water} o un baule {@code waterlogged=true}
     * arriverebbero all'occhio asciutti.
     *
     * <p>Quindi si chiama lo stesso {@link FluidRenderer} del gioco, ma su un mondo finto fatto di
     * un blocco solo: il nostro all'origine, aria tutt'intorno. Le coordinate che scrive sono
     * relative alla sezione ({@code pos & 15}), e dall'origine vengono in {@code [0,1]}, che è lo
     * stesso spazio del modello del blocco. Il colore del biome invece si chiede al mondo vero, alla
     * posizione vera: l'acqua di una palude deve restare verde anche a mezz'aria.
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
     * Il disegnatore di liquidi, tenuto finché i modelli sono quelli.
     *
     * <p>Costruirlo a ogni frame non costerebbe molto, ma non costa niente non farlo; e dopo un
     * reload delle risorse i modelli sono altri, quindi la chiave è l'insieme stesso.
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
     * Un mondo di un blocco solo: il nostro all'origine, aria dappertutto.
     *
     * <p>Serve a {@link FluidRenderer}, che di un mondo chiede i vicini e la luce. I vicini qui non
     * ci sono per scelta - il liquido deve avere tutte le sue facce, come ce l'ha un blocco isolato -
     * mentre la luce è quella che la forma ha chiesto, e il colore quello del mondo vero.
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
     * Un buffer di vertici che porta con sé una posa.
     *
     * <p>{@link FluidRenderer} scrive coordinate crude, come farebbe dentro un chunk; qui invece il
     * blocco sta dove la forma ha detto, girato e scalato come ha detto. La posa la applica questo.
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
     * La parte di un blocco che ha un renderer suo: il coperchio di un baule, il testo di un
     * cartello, la testa di uno scheletro. Senza questa, un baule disegnato a mezz'aria sarebbe una
     * cassa vuota senza coperchio.
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

    /** In che posa si disegna un item: {@code none} è come lo si vede appoggiato per terra. */
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
     * Dal testo che è arrivato dalla rete al modello che il gioco sa disegnare.
     *
     * <p>Si fa una volta per forma: parsare il nome di un blocco e costruire la sua block entity a
     * ogni frame vorrebbe dire pagare a sessanta hertz una cosa che non cambia mai.
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
            // un blocco che questo client non conosce: un server con dei mod che qui non ci sono
            return Model.NONE;
        }
        return new Model(state, null, blockEntityOf(state, sprite, client));
    }

    /** La block entity del blocco, coi dati che il server ha mandato, se il blocco ne ha una. */
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

    /** Gira la posa verso quello che l'etichetta deve guardare. */
    private static void face(PoseStack pose, String facing, Vec3 pos, CameraRenderState camera)
    {
        switch (facing)
        {
            case "north" ->
            {
            }
            case "south" -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case "east" -> pose.mulPose(Axis.YP.rotationDegrees(270));
            case "west" -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case "up" -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case "down" -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case "camera" -> pose.mulPose(camera.orientation);
            // 'player': guarda chi legge, ma restando in piedi - il testo non si inclina insieme
            // alla testa di chi guarda, che e' la differenza con 'camera'
            default ->
            {
                Vec3 toCamera = pos.subtract(camera.pos);
                double flat = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
                pose.mulPose(Axis.YP.rotation((float) Math.atan2(toCamera.x, toCamera.z)));
                pose.mulPose(Axis.XP.rotation((float) -Math.atan2(toCamera.y, flat)));
            }
        }
    }

    /** Dov'è adesso l'entità seguita, interpolata nel frame, o {@code null} se non c'è più. */
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
     * Da {@code 0xRRGGBBAA} - come si scrivono i colori nello script - a {@code 0xAARRGGBB}, che è
     * quello che le primitive del gioco si aspettano.
     */
    private static int argb(int rgba)
    {
        return (rgba >>> 8) | (rgba << 24);
    }
}
