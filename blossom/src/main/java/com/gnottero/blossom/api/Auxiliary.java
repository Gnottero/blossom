package com.gnottero.blossom.api;

import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.AppHost;
import com.gnottero.blossom.value.Nbt;

import com.gnottero.blossom.access.Vanilla;
import com.gnottero.blossom.util.FeatureGenerator;
import com.gnottero.blossom.argument.FileArgument;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.AppHost;
import com.gnottero.blossom.ScriptRuntime;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import com.gnottero.blossom.argument.BlockArgument;
import blossom.interpreter.argument.FunctionArgument;
import com.gnottero.blossom.argument.Vector3Argument;
import blossom.exception.ExitStatement;
import blossom.exception.InternalExpressionException;
import com.gnottero.blossom.access.Host;
import com.gnottero.blossom.util.SnoopyCommandSource;
import com.gnottero.blossom.util.SystemInfo;
import com.gnottero.blossom.util.InputValidator;
import blossom.utils.ScarpetJsonDeserializer;
import com.gnottero.blossom.util.Particles;
import com.gnottero.blossom.util.WorldTools;
import blossom.value.BooleanValue;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.FormattedTextValue;
import blossom.value.LazyListValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import com.gnottero.blossom.value.NBTSerializableValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;
import com.gnottero.blossom.value.ValueConversions;
import com.google.common.collect.Lists;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.file.PathUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import org.jspecify.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Auxiliary
{
    public static final String MARKER_STRING = "__scarpet_marker";
    private static final Map<String, SoundSource> mixerMap = Arrays.stream(SoundSource.values()).collect(Collectors.toMap(SoundSource::getName, k -> k));
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().registerTypeAdapter(Value.class, new ScarpetJsonDeserializer()).create();

    @Deprecated
    public static String recognizeResource(Value value, boolean isFloder)
    {
        String origfile = value.getString();
        String file = origfile.toLowerCase(Locale.ROOT).replaceAll("[^A-Za-z0-9\\-+_/]", "");
        file = Arrays.stream(file.split("/+")).filter(s -> !s.isEmpty()).collect(Collectors.joining("/"));
        if (file.isEmpty() && !isFloder)
        {
            throw new InternalExpressionException("Cannot use " + origfile + " as resource name - must have some letters and numbers");
        }
        return file;
    }

    public static void apply(Expression expression)
    {
        /*
         * post_effect(giocatori, azione, effetto?) - i post-processing shader di un giocatore
         * (voce BE-6 dell'inventario).
         *
         * Rispecchia uno a uno il comando vanilla /posteffect, e passa dal suo stesso pacchetto:
         * funziona quindi su qualunque client, anche senza Blossom installato, e sopravvive al
         * rientro nel mondo. Nel fork la prima versione aveva un pacchetto e un renderer propri, che
         * funzionavano solo con la mod anche sul client: quella strada e' stata abbandonata li' e non
         * viene ripresa qui.
         */
        expression.addContextFunction("post_effect", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new blossom.exception.ArityError(blossom.exception.SourcePosition.UNKNOWN,
                        "'post_effect' wants at least a player and an action among 'add', 'remove', 'clear' and 'list'.");
            }
            ScriptContext cc = (ScriptContext) c;
            List<ServerPlayer> players = new ArrayList<>();
            Value target = lv.get(0);
            for (Value each : target instanceof final ListValue list ? list.getItems() : List.of(target))
            {
                ServerPlayer player = EntityValue.getPlayerByValue(cc.server(), each);
                if (player == null)
                {
                    throw new blossom.exception.TypeMismatchError(blossom.exception.SourcePosition.UNKNOWN,
                            "'post_effect' wants a connected player, not " + each.getString() + ".");
                }
                players.add(player);
            }
            String action = lv.get(1).getString();
            switch (action)
            {
                case "add", "remove" ->
                {
                    if (lv.size() < 3)
                    {
                        throw new blossom.exception.ArityError(blossom.exception.SourcePosition.UNKNOWN,
                                "'post_effect' with '" + action + "' also wants the effect id, e.g. 'minecraft:invert'.");
                    }
                    Identifier effect = InputValidator.identifierOf(lv.get(2).getString());
                    int changed = 0;
                    for (ServerPlayer player : players)
                    {
                        boolean applied = action.equals("add") ? player.addPostEffect(effect) : player.removePostEffect(effect);
                        if (applied)
                        {
                            changed++;
                        }
                    }
                    return new NumericValue(changed);
                }
                case "clear" ->
                {
                    int changed = 0;
                    for (ServerPlayer player : players)
                    {
                        if (player.clearPostEffects())
                        {
                            changed++;
                        }
                    }
                    return new NumericValue(changed);
                }
                case "list" ->
                {
                    if (players.size() != 1)
                    {
                        throw new blossom.exception.TypeMismatchError(blossom.exception.SourcePosition.UNKNOWN,
                                "'post_effect' with 'list' wants a single player: two players have two different lists.");
                    }
                    return ListValue.wrap(players.getFirst().getPostEffects().stream()
                            .map(id -> StringValue.of(id.toString())));
                }
                default -> throw new blossom.exception.TypeMismatchError(blossom.exception.SourcePosition.UNKNOWN,
                        "Unknown action for 'post_effect': '" + action
                                + "'. The actions are 'add', 'remove', 'clear' and 'list'.",
                        blossom.exception.Suggestions.closestTo(action, List.of("add", "remove", "clear", "list")));
            }
        });

        expression.addContextFunction("sound", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                return ListValue.wrap(cc.registry(Registries.SOUND_EVENT).listElements().map(soundEventReference -> ValueConversions.of(soundEventReference.key().identifier())));
            }
            String rawString = lv.get(0).getString();
            Identifier soundName = InputValidator.identifierOf(rawString);
            Vector3Argument locator = Vector3Argument.findIn(lv, 1);

            Holder<SoundEvent> soundHolder = Holder.direct(SoundEvent.createVariableRangeEvent(soundName));
            float volume = 1.0F;
            float pitch = 1.0F;
            SoundSource mixer = SoundSource.MASTER;
            if (lv.size() > locator.offset)
            {
                volume = (float) NumericValue.asNumber(lv.get(locator.offset)).getDouble();
                if (lv.size() > 1 + locator.offset)
                {
                    pitch = (float) NumericValue.asNumber(lv.get(1 + locator.offset)).getDouble();
                    if (lv.size() > 2 + locator.offset)
                    {
                        String mixerName = lv.get(2 + locator.offset).getString();
                        mixer = mixerMap.get(mixerName.toLowerCase(Locale.ROOT));
                        if (mixer == null)
                        {
                            throw new InternalExpressionException(mixerName + " is not a valid mixer name");
                        }
                    }
                }
            }
            Vec3 vec = locator.vec;
            double d0 = Math.pow(volume > 1.0F ? (double) (volume * 16.0F) : 16.0D, 2.0D);
            int count = 0;
            ServerLevel level = cc.level();
            long seed = level.getRandom().nextLong();
            for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(vec) < d0))
            {
                count++;
                player.connection.send(new ClientboundSoundPacket(soundHolder, mixer, vec.x, vec.y, vec.z, volume, pitch, seed));
            }
            return new NumericValue(count);
        });

        expression.addContextFunction("particle", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                return ListValue.wrap(cc.registry(Registries.PARTICLE_TYPE).listElements().map(particleTypeReference -> ValueConversions.of(particleTypeReference.key().identifier())));
            }
            MinecraftServer ms = cc.server();
            ServerLevel world = cc.level();
            Vector3Argument locator = Vector3Argument.findIn(lv, 1);
            String particleName = lv.get(0).getString();
            int count = 10;
            double speed = 0;
            float spread = 0.5f;
            ServerPlayer player = null;
            if (lv.size() > locator.offset)
            {
                count = (int) NumericValue.asNumber(lv.get(locator.offset)).getLong();
                if (lv.size() > 1 + locator.offset)
                {
                    spread = (float) NumericValue.asNumber(lv.get(1 + locator.offset)).getDouble();
                    if (lv.size() > 2 + locator.offset)
                    {
                        speed = NumericValue.asNumber(lv.get(2 + locator.offset)).getDouble();
                        if (lv.size() > 3 + locator.offset) // should accept entity as well as long as it is player
                        {
                            player = ms.getPlayerList().getPlayerByName(lv.get(3 + locator.offset).getString());
                        }
                    }
                }
            }
            ParticleOptions particle = Particles.byName(particleName, world.registryAccess());
            Vec3 vec = locator.vec;
            if (player == null)
            {
                for (ServerPlayer p : (world.players()))
                {
                    world.sendParticles(p, particle, true, true, vec.x, vec.y, vec.z, count,
                            spread, spread, spread, speed);
                }
            }
            else
            {
                world.sendParticles(player,
                        particle, true, true, vec.x, vec.y, vec.z, count,
                        spread, spread, spread, speed);
            }

            return Value.TRUE;
        });

        expression.addContextFunction("particle_line", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerLevel world = cc.level();
            String particleName = lv.get(0).getString();
            ParticleOptions particle = Particles.byName(particleName, world.registryAccess());
            Vector3Argument pos1 = Vector3Argument.findIn(lv, 1);
            Vector3Argument pos2 = Vector3Argument.findIn(lv, pos1.offset);
            double density = 1.0;
            ServerPlayer player = null;
            if (lv.size() > pos2.offset)
            {
                density = NumericValue.asNumber(lv.get(pos2.offset)).getDouble();
                if (density <= 0)
                {
                    throw new InternalExpressionException("Particle density should be positive");
                }
                if (lv.size() > pos2.offset + 1)
                {
                    Value playerValue = lv.get(pos2.offset + 1);
                    if (playerValue instanceof EntityValue entityValue)
                    {
                        Entity e = entityValue.getEntity();
                        if (!(e instanceof final ServerPlayer sp))
                        {
                            throw new InternalExpressionException("'particle_line' player argument has to be a player");
                        }
                        player = sp;
                    }
                    else
                    {
                        player = cc.server().getPlayerList().getPlayerByName(playerValue.getString());
                    }
                }
            }

            return new NumericValue(Particles.drawParticleLine(
                    (player == null) ? world.players() : Collections.singletonList(player),
                    particle, pos1.vec, pos2.vec, density
            ));
        });

        expression.addContextFunction("item_display_name", 1, (c, t, lv) -> new FormattedTextValue(ValueConversions.getItemStackFromValue(lv.get(0), false, ((ScriptContext) c).registryAccess()).getHoverName()));

        expression.addContextFunction("particle_box", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerLevel world = cc.level();
            String particleName = lv.get(0).getString();
            ParticleOptions particle = Particles.byName(particleName, world.registryAccess());
            Vector3Argument pos1 = Vector3Argument.findIn(lv, 1);
            Vector3Argument pos2 = Vector3Argument.findIn(lv, pos1.offset);

            double density = 1.0;
            ServerPlayer player = null;
            if (lv.size() > pos2.offset)
            {
                density = NumericValue.asNumber(lv.get(pos2.offset)).getDouble();
                if (density <= 0)
                {
                    throw new InternalExpressionException("Particle density should be positive");
                }
                if (lv.size() > pos2.offset + 1)
                {
                    Value playerValue = lv.get(pos2.offset + 1);
                    if (playerValue instanceof EntityValue entityValue)
                    {
                        Entity e = entityValue.getEntity();
                        if (!(e instanceof final ServerPlayer sp))
                        {
                            throw new InternalExpressionException("'particle_box' player argument has to be a player");
                        }
                        player = sp;
                    }
                    else
                    {
                        player = cc.server().getPlayerList().getPlayerByName(playerValue.getString());
                    }
                }
            }
            Vec3 a = pos1.vec;
            Vec3 b = pos2.vec;
            Vec3 from = new Vec3(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z));
            Vec3 to = new Vec3(max(a.x, b.x), max(a.y, b.y), max(a.z, b.z));
            int particleCount = Particles.particleMesh(
                    player == null ? world.players() : Collections.singletonList(player),
                    particle, density, from, to
            );
            return new NumericValue(particleCount);
        });


        expression.addContextFunction("create_marker", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            BlockState targetBlock = null;
            Vector3Argument pointLocator;
            boolean interactable = true;
            Component name;
            try
            {
                Value nameValue = lv.get(0);
                name = nameValue.isNull() ? null : FormattedTextValue.getTextByValue(nameValue);
                pointLocator = Vector3Argument.findIn(lv, 1, true, false);
                if (lv.size() > pointLocator.offset)
                {
                    BlockArgument blockLocator = BlockArgument.findIn(cc, lv, pointLocator.offset, true, true, false);
                    if (!(blockLocator instanceof BlockArgument.MissingBlockArgument))
                    {
                        targetBlock = blockLocator.block.getBlockState();
                    }
                    if (lv.size() > blockLocator.offset)
                    {
                        interactable = lv.get(blockLocator.offset).getBoolean();
                    }
                }
            }
            catch (IndexOutOfBoundsException e)
            {
                throw new InternalExpressionException("'create_marker' requires a name and three coordinates, with optional direction, and optional block on its head");
            }
            Level level = cc.level();
            ArmorStand armorstand = new ArmorStand(EntityTypes.ARMOR_STAND, level);
            double yoffset;
            if (targetBlock == null && name == null)
            {
                yoffset = 0.0;
            }
            else if (!interactable && targetBlock == null)
            {
                yoffset = -0.41;
            }
            else
            {
                if (targetBlock == null)
                {
                    yoffset = -armorstand.getBbHeight() - 0.41;
                }
                else
                {
                    yoffset = -armorstand.getBbHeight() + 0.3;
                }
            }
            armorstand.snapTo(
                    pointLocator.vec.x,
                    //pointLocator.vec.y - ((!interactable && targetBlock == null)?0.41f:((targetBlock==null)?(armorstand.getHeight()+0.41):(armorstand.getHeight()-0.3))),
                    pointLocator.vec.y + yoffset,
                    pointLocator.vec.z,
                    (float) pointLocator.yaw,
                    (float) pointLocator.pitch
            );
            armorstand.addTag(MARKER_STRING + "_" + ((cc.host.getName() == null) ? "" : cc.host.getName()));
            armorstand.addTag(MARKER_STRING);
            if (targetBlock != null)
            {
                armorstand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(targetBlock.getBlock().asItem()));
            }
            if (name != null)
            {
                armorstand.setCustomName(name);
                armorstand.setCustomNameVisible(true);
            }
            armorstand.setHeadPose(new Rotations((int) pointLocator.pitch, 0, 0));
            armorstand.setNoGravity(true);
            armorstand.setInvisible(true);
            armorstand.setInvulnerable(true);
            armorstand.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) (interactable ? 8 : 16 | 8));
            level.addFreshEntity(armorstand);
            return new EntityValue(armorstand);
        });

        expression.addContextFunction("remove_all_markers", 0, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            int total = 0;
            String markerName = MARKER_STRING + "_" + ((cc.host.getName() == null) ? "" : cc.host.getName());
            for (Entity e : cc.level().getEntities(EntityTypes.ARMOR_STAND, as -> as.entityTags().contains(markerName)))
            {
                total++;
                e.discard();
            }
            return new NumericValue(total);
        });

        expression.addUnaryFunction("nbt", NBTSerializableValue::fromValue);

        expression.addUnaryFunction("escape_nbt", v -> new StringValue(StringTag.quoteAndEscape(v.getString())));

        expression.addUnaryFunction("parse_nbt", v -> {
            if (v instanceof final NBTSerializableValue nbtsv)
            {
                return nbtsv.toValue();
            }
            NBTSerializableValue ret = NBTSerializableValue.parseString(v.getString());
            return ret == null ? Value.NULL : ret.toValue();
        });

        expression.addFunction("tag_matches", lv -> {
            int numParam = lv.size();
            if (numParam != 2 && numParam != 3)
            {
                throw new InternalExpressionException("'tag_matches' requires 2 or 3 arguments");
            }
            if (lv.get(1).isNull())
            {
                return Value.TRUE;
            }
            if (lv.get(0).isNull())
            {
                return Value.FALSE;
            }
            Tag source = ((NBTSerializableValue) (NBTSerializableValue.fromValue(lv.get(0)))).getTag();
            Tag match = ((NBTSerializableValue) (NBTSerializableValue.fromValue(lv.get(1)))).getTag();
            return BooleanValue.of(NbtUtils.compareNbt(match, source, numParam == 2 || lv.get(2).getBoolean()));
        });

        expression.addContextFunction("encode_nbt", -1, (c, t, lv) -> {
            int argSize = lv.size();
            if (argSize == 0 || argSize > 2)
            {
                throw new InternalExpressionException("'encode_nbt' requires 1 or 2 parameters");
            }
            Value v = lv.get(0);
            boolean force = (argSize > 1) && lv.get(1).getBoolean();
            Tag tag;
            try
            {
                tag = Nbt.toTag(v, force, ((ScriptContext) c).registryAccess());
            }
            catch (NBTSerializableValue.IncompatibleTypeException exception)
            {
                throw new InternalExpressionException("cannot reliably encode to a tag the value of '" + exception.val.getPrettyString() + "'");
            }
            return new NBTSerializableValue(tag);
        });

        //"overridden" native call that prints to stderr
        expression.addContextFunction("print", -1, (c, t, lv) ->
        {
            if (lv.isEmpty() || lv.size() > 2)
            {
                throw new InternalExpressionException("'print' takes one or two arguments");
            }
            ScriptContext cc = (ScriptContext) c;
            CommandSourceStack s = cc.source();
            MinecraftServer server = s.getServer();
            Value res = lv.get(0);
            List<CommandSourceStack> targets = null;
            if (lv.size() == 2)
            {
                List<Value> playerValues = (res instanceof ListValue list) ? list.getItems() : Collections.singletonList(res);
                List<CommandSourceStack> playerTargets = new ArrayList<>();
                playerValues.forEach(pv -> {
                    ServerPlayer player = EntityValue.getPlayerByValue(server, pv);
                    if (player == null)
                    {
                        throw new InternalExpressionException("Cannot target player " + pv.getString() + " in print");
                    }
                    playerTargets.add(player.createCommandSourceStack());
                });
                targets = playerTargets;
                res = lv.get(1);
            } else if (c.host.user != null) {
                ServerPlayer player = cc.server().getPlayerList().getPlayerByName(cc.host.user);
                if (player != null) {
                    targets = Collections.singletonList(player.createCommandSourceStack());
                }
            } // optionally retrieve from CC.host.responsibleSource to print?
            Component message = FormattedTextValue.getTextByValue(res);
            if (targets == null)
            {
                s.sendSuccess(() -> message, false);
            }
            else
            {
                targets.forEach(p -> p.sendSuccess(() -> message, false));
            }
            return res; // pass through for variables
        });

        expression.addContextFunction("display_title", -1, (c, t, lv) -> {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'display_title' needs at least a target, type and message, and optionally times");
            }
            Value pVal = lv.get(0);
            if (!(pVal instanceof ListValue))
            {
                pVal = ListValue.of(pVal);
            }
            MinecraftServer server = ((ScriptContext) c).server();
            Stream<ServerPlayer> targets = ((ListValue) pVal).getItems().stream().map(v ->
            {
                ServerPlayer player = EntityValue.getPlayerByValue(server, v);
                if (player == null)
                {
                    throw new InternalExpressionException("'display_title' requires a valid online player or a list of players as first argument. " + v.getString() + " is not a player.");
                }
                return player;
            });
            Function<Component, Packet<?>> packetGetter = null;
            String actionString = lv.get(1).getString().toLowerCase(Locale.ROOT);
            switch (actionString)
            {
                case "title":
                    packetGetter = ClientboundSetTitleTextPacket::new;
                    if (lv.size() < 3)
                    {
                        throw new InternalExpressionException("Third argument of 'display_title' must be present except for 'clear' type");
                    }

                    break;
                case "subtitle":
                    packetGetter = ClientboundSetSubtitleTextPacket::new;
                    if (lv.size() < 3)
                    {
                        throw new InternalExpressionException("Third argument of 'display_title' must be present except for 'clear' type");
                    }

                    break;
                case "actionbar":
                    packetGetter = ClientboundSetActionBarTextPacket::new;
                    if (lv.size() < 3)
                    {
                        throw new InternalExpressionException("Third argument of 'display_title' must be present except for 'clear' type");
                    }

                    break;
                case "clear":
                    packetGetter = x -> new ClientboundClearTitlesPacket(true); // resetting default fade
                    break;
                case "player_list_header", "player_list_footer":
                    break;
                default:
                    throw new InternalExpressionException("'display_title' requires 'title', 'subtitle', 'actionbar', 'player_list_header', 'player_list_footer' or 'clear' as second argument");
            }
            Component title;
            boolean soundsTrue = false;
            if (lv.size() > 2)
            {
                pVal = lv.get(2);
                title = FormattedTextValue.getTextByValue(pVal);
                soundsTrue = pVal.getBoolean();
            }
            else
            {
                title = null; // Will never happen, just to make lambda happy
            }
            if (packetGetter == null)
            {
                boolean isHeader = actionString.equals("player_list_header");
                final Component tabText = soundsTrue ? title : null;
                AtomicInteger total = new AtomicInteger(0);
                List<ServerPlayer> targetList = targets.collect(Collectors.toList());
                // intestazione e pie' della lista giocatori sono vanilla: bastano al client normale,
                // senza nessun protocollo nostro
                targetList.forEach(target ->
                {
                    Host.setTabList(target, isHeader, tabText);
                    total.getAndIncrement();
                });
                return NumericValue.of(total.get());
            }
            ClientboundSetTitlesAnimationPacket timesPacket; // TimesPacket
            if (lv.size() > 3)
            {
                if (lv.size() != 6)
                {
                    throw new InternalExpressionException("'display_title' needs all fade-in, stay and fade-out times");
                }
                int in = NumericValue.asNumber(lv.get(3), "fade in for display_title").getInt();
                int stay = NumericValue.asNumber(lv.get(4), "stay for display_title").getInt();
                int out = NumericValue.asNumber(lv.get(5), "fade out for display_title").getInt();
                timesPacket = new ClientboundSetTitlesAnimationPacket(in, stay, out);
            }
            else
            {
                timesPacket = null;
            }

            Packet<?> packet = packetGetter.apply(title);
            AtomicInteger total = new AtomicInteger(0);
            targets.forEach(p -> {
                if (timesPacket != null)
                {
                    p.connection.send(timesPacket);
                }
                p.connection.send(packet);
                total.getAndIncrement();
            });
            return NumericValue.of(total.get());
        });

        expression.addFunction("format", values -> {
            if (values.isEmpty())
            {
                throw new InternalExpressionException("'format' requires at least one component");
            }
            if (values.get(0) instanceof final ListValue list && values.size() == 1)
            {
                values = list.getItems();
            }
            return new FormattedTextValue(Host.Messenger_compose(values.stream().map(Value::getString).toArray()));
        });

        expression.addContextFunction("run", 1, (c, t, lv) ->
        {
            CommandSourceStack s = ((ScriptContext) c).source();
            try
            {
                Component[] error = {null};
                OptionalLong[] returnValue = {OptionalLong.empty()};
                List<Component> output = new ArrayList<>();
                s.getServer().getCommands().performPrefixedCommand(
                        new SnoopyCommandSource(s, error, output, returnValue),
                        lv.get(0).getString());
                if (returnValue[0].isEmpty())
                {
                    return Value.NULL;
                }
                return ListValue.of(
                        NumericValue.of(returnValue[0].getAsLong()),
                        ListValue.wrap(output.stream().map(FormattedTextValue::new)),
                        FormattedTextValue.of(error[0])
                );
            }
            catch (Exception exc)
            {
                return ListValue.of(Value.NULL, ListValue.of(), new FormattedTextValue(Component.literal(exc.getMessage())));
            }
        });

        expression.addContextFunction("save", 0, (c, t, lv) ->
        {
            CommandSourceStack s = ((ScriptContext) c).source();
            s.getServer().getPlayerList().saveAll();
            s.getServer().saveAllChunks(true, true, true);
            for (ServerLevel world : s.getServer().getAllLevels())
            {
                world.getChunkSource().tick(() -> true, false);
            }
            ScriptRuntime.LOG.warn("Saved chunks");
            return Value.TRUE;
        });

        expression.addContextFunction("tick_time", 0, (c, t, lv) ->
                new NumericValue(((ScriptContext) c).server().getTickCount()));

        expression.addContextFunction("world_time", 0, (c, t, lv) -> {
            c.host.issueDeprecation("world_time()");
            return new NumericValue(((ScriptContext) c).level().getGameTime());
        });

        expression.addContextFunction("day_time", -1, (c, t, lv) ->
        {
            Value time = new NumericValue(((ScriptContext) c).level().getOverworldClockTime());
            if (!lv.isEmpty())
            {
                long newTime = NumericValue.asNumber(lv.get(0)).getLong();
                if (newTime < 0)
                {
                    newTime = 0;
                }
                ((ScriptContext) c).level().clockManager().setTotalTicks(((ScriptContext) c).registryAccess().getOrThrow(WorldClocks.OVERWORLD), newTime);
            }
            return time;
        });

        expression.addContextFunction("last_tick_times", -1, (c, t, lv) ->
        {
            c.host.issueDeprecation("last_tick_times()");
            return SystemInfo.get("server_last_tick_times", (ScriptContext) c);
        });


        expression.addContextFunction("game_tick", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            MinecraftServer server = cc.server();
            ScriptRuntime scriptServer = BlossomMod.runtime();
            if (scriptServer == null)
            {
                return Value.NULL;
            }
            if (!server.isSameThread())
            {
                throw new InternalExpressionException("Unable to run ticks from threads");
            }
            if (scriptServer.tickDepth > 16)
            {
                throw new InternalExpressionException("'game_tick' function caused other 'game_tick' functions to run. You should not allow that.");
            }
            try
            {
                scriptServer.tickDepth++;
                Vanilla.MinecraftServer_forceTick(server, () -> System.nanoTime() - scriptServer.tickStart < 50000000L);
                if (!lv.isEmpty())
                {
                    long msTotal = NumericValue.asNumber(lv.get(0)).getLong();
                    long endExpected = scriptServer.tickStart + msTotal * 1000000L;
                    long wait = endExpected - System.nanoTime();
                    if (wait > 0L)
                    {
                        try
                        {
                            Thread.sleep(wait / 1000000L);
                        }
                        catch (InterruptedException ignored)
                        {
                        }
                    }
                }
                scriptServer.tickStart = System.nanoTime(); // for the next tick
                Thread.yield();
            }
            finally
            {
                if (!scriptServer.stopAll)
                {
                    scriptServer.tickDepth--;
                }
            }
            if (scriptServer.stopAll)
            {
                throw new ExitStatement(Value.NULL);
            }
            return Value.TRUE;
        });

        expression.addContextFunction("seed", -1, (c, t, lv) -> {
            CommandSourceStack s = ((ScriptContext) c).source();
            c.host.issueDeprecation("seed()");
            return new NumericValue(s.getLevel().getSeed());
        });

        expression.addContextFunction("relight", -1, (c, t, lv) ->
        {
            return Value.NULL;
            /*
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockPos pos = locator.block.getPos();
            ServerLevel world = cc.level();
            Vanilla.ChunkMap_relightChunk(world.getChunkSource().chunkMap, new ChunkPos(pos));
            WorldTools.forceChunkUpdate(pos, world);
            return Value.TRUE;

             */
        });

        // Should this be deprecated for system_info('source_dimension')?
        expression.addContextFunction("current_dimension", 0, (c, t, lv) ->
                ValueConversions.of(((ScriptContext) c).level()));

        expression.addContextFunction("view_distance", 0, (c, t, lv) -> {
            c.host.issueDeprecation("view_distance()");
            return new NumericValue(((ScriptContext) c).server().getPlayerList().getViewDistance());
        });

        // lazy due to passthrough and context changing ability
        expression.addLazyFunction("in_dimension", 2, (c, t, lv) -> {
            CommandSourceStack outerSource = ((ScriptContext) c).source();
            Value dimensionValue = lv.get(0).evalValue(c);
            Level world = ValueConversions.dimFromValue(dimensionValue, outerSource.getServer());
            if (world == outerSource.getLevel())
            {
                return lv.get(1);
            }
            CommandSourceStack innerSource = outerSource.withLevel((ServerLevel) world);
            Context newCtx = c.recreate();
            ((ScriptContext) newCtx).swapSource(innerSource);
            newCtx.variables = c.variables;
            Value retval = lv.get(1).evalValue(newCtx);
            return (cc, tt) -> retval;
        });

        expression.addContextFunction("plop", -1, (c, t, lv) -> {
            if (lv.isEmpty())
            {
                Map<Value, Value> plopData = new HashMap<>();
                ScriptContext cc = (ScriptContext) c;
                plopData.put(StringValue.of("scarpet_custom"),
                        ListValue.wrap(FeatureGenerator.featureMap.keySet().stream().sorted().map(StringValue::of))
                );
                plopData.put(StringValue.of("feature_types"),
                        ListValue.wrap(cc.registry(Registries.FEATURE_TYPE).keySet().stream().sorted().map(ValueConversions::of))
                );
                plopData.put(StringValue.of("features"),
                        ListValue.wrap(cc.registry(Registries.FEATURE).keySet().stream().sorted().map(ValueConversions::of))
                );
                plopData.put(StringValue.of("structure_types"),
                        ListValue.wrap(cc.registry(Registries.STRUCTURE_TYPE).keySet().stream().sorted().map(ValueConversions::of))
                );
                plopData.put(StringValue.of("structures"),
                        ListValue.wrap(cc.registry(Registries.STRUCTURE).keySet().stream().sorted().map(ValueConversions::of))
                );
                return MapValue.wrap(plopData);
            }
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 0);
            if (lv.size() <= locator.offset)
            {
                throw new InternalExpressionException("'plop' needs extra argument indicating what to plop");
            }
            String what = lv.get(locator.offset).getString();
            Value[] result = new Value[]{Value.NULL};
            ((ScriptContext) c).server().executeBlocking(() ->
            {
                Boolean res = FeatureGenerator.plop(what, ((ScriptContext) c).level(), locator.block.getPos());

                if (res == null)
                {
                    return;
                }
                result[0] = BooleanValue.of(res);
            });
            return result[0];
        });

        // schedule(ritardo_in_tick, funzione, argomenti...)
        expression.addContextFunction("schedule", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'schedule' wants at least the delay and the function to call");
            }
            long delay = NumericValue.asNumber(lv.get(0)).getLong();
            FunctionArgument functionArgument = FunctionArgument.findIn(c, expression.module, lv, 1, false, false);
            AppHost host = AppHost.of(c);
            BlossomMod.runtime().scheduler.schedule(host.name(), host.interpreter(), ((ScriptContext) c).source(),
                    functionArgument.function, functionArgument.checkedArgs(), delay);
            return Value.TRUE;
        });

        expression.addImpureFunction("logger", lv ->
        {
            Value res;

            if (lv.size() == 1)
            {
                res = lv.get(0);
                ScriptRuntime.LOG.info(res.getString());
            }
            else if (lv.size() == 2)
            {
                String level = lv.get(0).getString().toLowerCase(Locale.ROOT);
                res = lv.get(1);
                switch (level)
                {
                    case "debug" -> ScriptRuntime.LOG.debug(res.getString());
                    case "warn" -> ScriptRuntime.LOG.warn(res.getString());
                    case "info" -> ScriptRuntime.LOG.info(res.getString());
                    // Somehow issue deprecation
                    case "fatal", "error" -> ScriptRuntime.LOG.error(res.getString());
                    default -> throw new InternalExpressionException("Unknown log level for 'logger': " + level);
                }
            }
            else
            {
                throw new InternalExpressionException("logger takes 1 or 2 arguments");
            }

            return res; // pass through for variables
        });

        expression.addContextFunction("list_files", 2, (c, t, lv) ->
        {
            FileArgument fdesc = FileArgument.from(c, lv, true, FileArgument.Reason.READ);
            Stream<String> files = AppHost.of(c).listFolder(fdesc);
            return files == null ? Value.NULL : ListValue.wrap(files.map(StringValue::of));
        });

        expression.addContextFunction("read_file", 2, (c, t, lv) ->
        {
            FileArgument fdesc = FileArgument.from(c, lv, false, FileArgument.Reason.READ);
            if (fdesc.type == FileArgument.Type.NBT)
            {
                Tag state = AppHost.of(c).readFileTag(fdesc);
                return state == null ? Value.NULL : new NBTSerializableValue(state);
            }
            else if (fdesc.type == FileArgument.Type.JSON)
            {
                JsonElement json;
                json = AppHost.of(c).readJsonFile(fdesc);
                Value parsedJson = GSON.fromJson(json, Value.class);
                return parsedJson == null ? Value.NULL : parsedJson;
            }
            else
            {
                List<String> content = AppHost.of(c).readTextResource(fdesc);
                return content == null ? Value.NULL : ListValue.wrap(content.stream().map(StringValue::new));
            }
        });

        expression.addContextFunction("delete_file", 2, (c, t, lv) ->
                BooleanValue.of(AppHost.of(c).removeResourceFile(FileArgument.from(c, lv, false, FileArgument.Reason.DELETE))));

        expression.addContextFunction("write_file", -1, (c, t, lv) -> {
            if (lv.size() < 3)
            {
                throw new InternalExpressionException("'write_file' requires three or more arguments");
            }
            FileArgument fdesc = FileArgument.from(c, lv, false, FileArgument.Reason.CREATE);

            boolean success;
            if (fdesc.type == FileArgument.Type.NBT)
            {
                Value val = lv.get(2);
                NBTSerializableValue tagValue = (val instanceof final NBTSerializableValue nbtsv)
                        ? nbtsv
                        : new NBTSerializableValue(val.getString());
                Tag tag = tagValue.getTag();
                success = AppHost.of(c).writeTagFile(tag, fdesc);
            }
            else if (fdesc.type == FileArgument.Type.JSON)
            {
                List<String> data = Collections.singletonList(GSON.toJson(lv.get(2).toJson()));
                AppHost.of(c).removeResourceFile(fdesc);
                success = AppHost.of(c).appendLogFile(fdesc, data);
            }
            else
            {
                List<String> data = new ArrayList<>();
                if (lv.size() == 3)
                {
                    Value val = lv.get(2);
                    if (val instanceof final ListValue list)
                    {
                        List<Value> lval = list.getItems();
                        lval.forEach(v -> data.add(v.getString()));
                    }
                    else
                    {
                        data.add(val.getString());
                    }
                }
                else
                {
                    for (int i = 2; i < lv.size(); i++)
                    {
                        data.add(lv.get(i).getString());
                    }
                }
                success = AppHost.of(c).appendLogFile(fdesc, data);
            }
            return BooleanValue.of(success);
        });

        expression.addContextFunction("load_app_data", -1, (c, t, lv) ->
        {
            FileArgument fdesc = new FileArgument(null, FileArgument.Type.NBT, null, false, false, FileArgument.Reason.READ, c.host);
            if (!lv.isEmpty())
            {
                c.host.issueDeprecation("load_app_data(...) with arguments");
                String resource = recognizeResource(lv.get(0), false);
                boolean shared = lv.size() > 1 && lv.get(1).getBoolean();
                fdesc = new FileArgument(resource, FileArgument.Type.NBT, null, false, shared, FileArgument.Reason.READ, c.host);
            }
            return NBTSerializableValue.of(AppHost.of(c).readFileTag(fdesc));
        });

        expression.addContextFunction("store_app_data", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("'store_app_data' needs NBT tag and an optional file");
            }
            Value val = lv.get(0);
            FileArgument fdesc = new FileArgument(null, FileArgument.Type.NBT, null, false, false, FileArgument.Reason.CREATE, c.host);
            if (lv.size() > 1)
            {
                c.host.issueDeprecation("store_app_data(...) with more than one argument");
                String resource = recognizeResource(lv.get(1), false);
                boolean shared = lv.size() > 2 && lv.get(2).getBoolean();
                fdesc = new FileArgument(resource, FileArgument.Type.NBT, null, false, shared, FileArgument.Reason.CREATE, c.host);
            }
            NBTSerializableValue tagValue = (val instanceof final NBTSerializableValue nbtsv)
                    ? nbtsv
                    : new NBTSerializableValue(val.getString());
            return BooleanValue.of(AppHost.of(c).writeTagFile(tagValue.getTag(), fdesc));
        });

        expression.addContextFunction("statistic", 3, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerPlayer player = EntityValue.getPlayerByValue(cc.server(), lv.get(0));
            if (player == null)
            {
                return Value.NULL;
            }
            Identifier category;
            Identifier statName;
            category = InputValidator.identifierOf(lv.get(1).getString());
            statName = InputValidator.identifierOf(lv.get(2).getString());
            StatType<?> type = cc.registry(Registries.STAT_TYPE).getValue(category);
            if (type == null)
            {
                return Value.NULL;
            }
            Stat<?> stat = getStat(type, statName);
            if (stat == null)
            {
                return Value.NULL;
            }
            return new NumericValue(player.getStats().getValue(stat));
        });

        // handle_event(evento, funzione, argomenti_fissi...)
        expression.addContextFunction("handle_event", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'handle_event' wants the event name and a function");
            }
            String eventName = lv.get(0).getString();
            FunctionArgument callback = FunctionArgument.findIn(c, expression.module, lv, 1, true, false);
            AppHost host = AppHost.of(c);
            com.gnottero.blossom.event.Event event = com.gnottero.blossom.event.Event.byName(eventName);
            if (callback.function == null)
            {
                if (event == null)
                {
                    return Value.FALSE;
                }
                event.removeApp(host.name());
                return Value.TRUE;
            }
            if (event == null)
            {
                // un evento che il gioco non conosce e' un evento di applicazione: lo dichiara chi
                // lo ascolta per primo, e la sua arieta' e' quella della funzione registrata
                event = com.gnottero.blossom.event.Event.custom(eventName,
                        callback.function.getArguments().size() - callback.args.size());
            }
            event.add(new com.gnottero.blossom.event.Event.Handler(host.name(), host.interpreter(),
                    ((ScriptContext) c).source(), callback.function, List.copyOf(callback.args), null));
            return Value.TRUE;
        });

        // signal_event(evento, giocatore_o_null, argomenti...) -> quante app hanno risposto
        expression.addContextFunction("signal_event", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("'signal_event' wants at least the event name");
            }
            ScriptContext cc = (ScriptContext) c;
            String eventName = lv.get(0).getString();
            com.gnottero.blossom.event.Event event = com.gnottero.blossom.event.Event.byName(eventName);
            if (event == null)
            {
                return Value.NULL;
            }
            ServerPlayer player = null;
            List<Value> args = Collections.emptyList();
            if (lv.size() > 1)
            {
                player = EntityValue.getPlayerByValue(cc.server(), lv.get(1));
                if (lv.size() > 2)
                {
                    args = lv.subList(2, lv.size());
                }
            }
            int listeners = event.handlers().size();
            List<Value> arguments = List.copyOf(args);
            if (player == null)
            {
                event.fire(cc.source(), () -> arguments);
            }
            else
            {
                event.fireForPlayer(player, () -> arguments);
            }
            return new NumericValue(listeners);
        });

        // nbt_storage()
        // nbt_storage(key)
        // nbt_storage(key, nbt)
        expression.addContextFunction("nbt_storage", -1, (c, t, lv) -> {
            if (lv.size() > 2)
            {
                throw new InternalExpressionException("'nbt_storage' requires 0, 1 or 2 arguments.");
            }
            ScriptContext cc = (ScriptContext) c;
            CommandStorage storage = cc.server().getCommandStorage();
            if (lv.isEmpty())
            {
                return ListValue.wrap(storage.keys().map(NBTSerializableValue::nameFromRegistryId));
            }
            String key = lv.get(0).getString();
            CompoundTag oldNbt = storage.get(InputValidator.identifierOf(key));
            if (lv.size() == 2)
            {
                Value nbt = lv.get(1);
                NBTSerializableValue newNbt = (nbt instanceof final NBTSerializableValue nbtsv)
                        ? nbtsv
                        : NBTSerializableValue.parseStringOrFail(nbt.getString());
                storage.set(InputValidator.identifierOf(key), newNbt.getCompoundTag());
            }
            return NBTSerializableValue.of(oldNbt);
        });

        // script run create_datapack('foo', {'foo' -> {'bar.json' -> {'c' -> true,'d' -> false,'e' -> {'foo' -> [1,2,3]},'a' -> 'foobar','b' -> 5}}})
        expression.addContextFunction("create_datapack", 2, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            String origName = lv.get(0).getString();
            String name = InputValidator.validateSimpleString(origName, true);
            MinecraftServer server = cc.server();
            for (String dpName : server.getPackRepository().getAvailableIds())
            {
                if (dpName.equalsIgnoreCase("file/" + name + ".zip") ||
                        dpName.equalsIgnoreCase("file/" + name))
                {
                    return Value.NULL;
                }

            }
            Value dpdata = lv.get(1);
            if (!(dpdata instanceof final MapValue dpMap))
            {
                throw new InternalExpressionException("datapack data needs to be a valid map type");
            }
            PackRepository packManager = server.getPackRepository();
            Path dbFloder = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path packFloder = dbFloder.resolve(name + ".zip");
            if (Files.exists(packFloder) || Files.exists(dbFloder.resolve(name)))
            {
                return Value.NULL;
            }
            Boolean[] successful = new Boolean[]{true};
            server.executeBlocking(() ->
            {
                try
                {
                    try (FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + packFloder.toUri()), Map.of("create", "true")))
                    {
                        Path zipRoot = zipfs.getPath("/");
                        zipValueToJson(zipRoot.resolve("pack.mcmeta"), MapValue.wrap(
                                Map.of(StringValue.of("pack"), MapValue.wrap(Map.of(
                                        StringValue.of("min_format"), new NumericValue(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major()),
                                        StringValue.of("max_format"), new NumericValue(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major()),
                                        StringValue.of("description"), StringValue.of(name),
                                        StringValue.of("source"), StringValue.of("scarpet")
                                )))
                        ));
                        walkTheDPMap(dpMap, zipRoot);
                    }
                    packManager.reload();
                    Pack resourcePackProfile = packManager.getPack("file/" + name + ".zip");
                    if (resourcePackProfile == null || packManager.getSelectedPacks().contains(resourcePackProfile))
                    {
                        throw new IOException();
                    }
                    List<Pack> list = Lists.newArrayList(packManager.getSelectedPacks());
                    resourcePackProfile.getDefaultPosition().insert(list, resourcePackProfile, Pack::selectionConfig, false);


                    server.reloadResources(list.stream().map(Pack::getId).collect(Collectors.toList())).
                            exceptionally(exc -> {
                                successful[0] = false;
                                return null;
                            }).join();
                    if (!successful[0])
                    {
                        throw new IOException();
                    }
                }
                catch (IOException e)
                {
                    successful[0] = false;
                    try
                    {
                        PathUtils.delete(packFloder);
                    }
                    catch (IOException ignored)
                    {
                        throw new InternalExpressionException("Failed to install a datapack and failed to clean up after it");
                    }

                }
            });
            return BooleanValue.of(successful[0]);
        });

        expression.addContextFunction("enable_hidden_dimensions", 0, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            cc.host.issueDeprecation("enable_hidden_dimensions in 1.18.2 and 1.19+");
            return Value.NULL;
        });
    }

    private static void zipValueToJson(Path path, Value output) throws IOException
    {
        JsonElement element = output.toJson();
        if (element == null)
        {
            throw new InternalExpressionException("Cannot interpret " + output.getPrettyString() + " as a json object");
        }
        String string = GSON.toJson(element);
        Files.createDirectories(path.getParent());
        BufferedWriter bufferedWriter = Files.newBufferedWriter(path);
        Throwable incident = null;
        try
        {
            bufferedWriter.write(string);
        }
        catch (Throwable shitHappened)
        {
            incident = shitHappened;
            throw shitHappened;
        }
        finally
        {
            if (incident != null)
            {
                try
                {
                    bufferedWriter.close();
                }
                catch (Throwable otherShitHappened)
                {
                    incident.addSuppressed(otherShitHappened);
                }
            }
            else
            {
                bufferedWriter.close();
            }
        }
    }

    private static void zipValueToText(Path path, Value output) throws IOException
    {
        List<Value> toJoin;
        String string;
        String delimiter = System.lineSeparator();
        // i dont know it shoule be \n or System.lineSeparator
        if (output instanceof LazyListValue lazyListValue)
        {
            toJoin = lazyListValue.unroll();
            string = toJoin.stream().map(Value::getString).collect(Collectors.joining(delimiter));
        }
        else if (output instanceof ListValue listValue)
        {
            toJoin = listValue.getItems();
            string = toJoin.stream().map(Value::getString).collect(Collectors.joining(delimiter));
        }
        else
        {
            string = output.getString();
        }


        Files.createDirectories(path.getParent());
        BufferedWriter bufferedWriter = Files.newBufferedWriter(path);
        Throwable incident = null;
        try
        {
            bufferedWriter.write(string);
        }
        catch (Throwable shitHappened)
        {
            incident = shitHappened;
            throw shitHappened;
        }
        finally
        {
            if (incident != null)
            {
                try
                {
                    bufferedWriter.close();
                }
                catch (Throwable otherShitHappened)
                {
                    incident.addSuppressed(otherShitHappened);
                }
            }
            else
            {
                bufferedWriter.close();
            }
        }
    }

    private static void zipValueToNBT(Path path, Value output) throws IOException
    {
        NBTSerializableValue tagValue = (output instanceof NBTSerializableValue nbtSerializableValue)
                ? nbtSerializableValue
                : new NBTSerializableValue(output.getString());
        Tag tag = tagValue.getTag();
        Files.createDirectories(path.getParent());
        if (tag instanceof final CompoundTag cTag)
        {
            NbtIo.writeCompressed(cTag, Files.newOutputStream(path));
        }
    }

    private static void walkTheDPMap(MapValue node, Path path) throws IOException
    {
        Map<Value, Value> items = node.getMap();
        for (Map.Entry<Value, Value> entry : items.entrySet())
        {
            Value val = entry.getValue();
            String strkey = entry.getKey().getString();
            Path child = path.resolve(strkey);
            if (strkey.endsWith(".json"))
            {
                zipValueToJson(child, val);
            }
            else if (strkey.endsWith(".mcfunction") || strkey.endsWith(".txt") || strkey.endsWith(".mcmeta"))
            {
                zipValueToText(child, val);
            }
            else if (strkey.endsWith(".nbt"))
            {
                zipValueToNBT(child, val);
            }
            else
            {
                if (!(val instanceof final MapValue map))
                {
                    throw new InternalExpressionException("Value of " + strkey + " should be a map");
                }
                Files.createDirectory(child);
                walkTheDPMap(map, child);
            }
        }
    }

    @Nullable
    private static <T> Stat<T> getStat(StatType<T> type, Identifier id)
    {
        T key = type.getRegistry().getValue(id);
        if (key == null || !type.contains(key))
        {
            return null;
        }
        return type.get(key);
    }
}
