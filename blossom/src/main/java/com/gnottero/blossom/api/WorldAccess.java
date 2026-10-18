package com.gnottero.blossom.api;

import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.ScriptRuntime;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Callables;
import com.gnottero.blossom.access.Host;
import com.gnottero.blossom.access.Vanilla;
import com.gnottero.blossom.util.Colors;
import com.gnottero.blossom.util.FeatureGenerator;
import com.gnottero.blossom.argument.BlockArgument;
import com.gnottero.blossom.argument.Vector3Argument;
import com.gnottero.blossom.event.Event;
import blossom.exception.InternalExpressionException;
import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import com.gnottero.blossom.util.BiomeInfo;
import com.gnottero.blossom.util.InputValidator;
import com.gnottero.blossom.util.WorldTools;
import com.gnottero.blossom.value.BlockValue;
import blossom.value.BooleanValue;
import com.gnottero.blossom.value.EntityValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import com.gnottero.blossom.value.NBTSerializableValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;
import com.gnottero.blossom.value.ValueConversions;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.TagValueInput;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.sounds.BlockSoundSet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import static com.gnottero.blossom.util.WorldTools.canHasChunk;

public class WorldAccess
{
    private static final Map<String, Direction> DIRECTION_MAP = Arrays.stream(Direction.values()).collect(Collectors.toMap(Direction::getName, Function.identity()));

    static
    {
        DIRECTION_MAP.put("y", Direction.UP);
        DIRECTION_MAP.put("z", Direction.SOUTH);
        DIRECTION_MAP.put("x", Direction.EAST);
    }

    private static final Map<String, TicketType> ticketTypes = Map.of(
            "portal", TicketType.PORTAL,
            "teleport", TicketType.ENDER_PEARL,
            "unknown", TicketType.UNKNOWN
    );
    private static Value booleanStateTest(
            Context c,
            String name,
            List<Value> params,
            BiPredicate<BlockState, BlockPos> test
    )
    {
        ScriptContext cc = (ScriptContext) c;
        if (params.isEmpty())
        {
            throw new InternalExpressionException("'" + name + "' requires at least one parameter");
        }
        if (params.get(0) instanceof final BlockValue bv)
        {
            return BooleanValue.of(test.test(bv.getBlockState(), bv.getPos()));
        }
        BlockValue block = BlockArgument.findIn(cc, params, 0).block;
        return BooleanValue.of(test.test(block.getBlockState(), block.getPos()));
    }

    private static Value stateStringQuery(
            Context c,
            String name,
            List<Value> params,
            BiFunction<BlockState, BlockPos, String> test
    )
    {
        ScriptContext cc = (ScriptContext) c;
        if (params.isEmpty())
        {
            throw new InternalExpressionException("'" + name + "' requires at least one parameter");
        }
        if (params.get(0) instanceof final BlockValue bv)
        {
            return StringValue.of(test.apply(bv.getBlockState(), bv.getPos()));
        }
        BlockValue block = BlockArgument.findIn(cc, params, 0).block;
        return StringValue.of(test.apply(block.getBlockState(), block.getPos()));
    }

    private static Value genericStateTest(
            Context c,
            String name,
            List<Value> params,
            Callables.TriFunction<BlockState, BlockPos, Level, Value> test
    )
    {
        ScriptContext cc = (ScriptContext) c;
        if (params.isEmpty())
        {
            throw new InternalExpressionException("'" + name + "' requires at least one parameter");
        }
        if (params.get(0) instanceof final BlockValue bv)
        {
            try
            {
                return test.apply(bv.getBlockState(), bv.getPos(), cc.level());
            }
            catch (NullPointerException ignored)
            {
                throw new InternalExpressionException("'" + name + "' function requires a block that is positioned in the world");
            }
        }
        BlockValue block = BlockArgument.findIn(cc, params, 0).block;
        return test.apply(block.getBlockState(), block.getPos(), cc.level());
    }

    private static <T extends Comparable<T>> BlockState setProperty(Property<T> property, String name, String value,
                                                                    BlockState bs)
    {
        Optional<T> optional = property.getValue(value);
        if (optional.isEmpty())
        {
            throw new InternalExpressionException(value + " is not a valid value for property " + name);
        }
        return bs.setValue(property, optional.get());
    }

    private static void nullCheck(Value v, String name)
    {
        if (v.isNull())
        {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    private static float numberGetOrThrow(Value v)
    {
        double num = v.readDoubleNumber();
        if (Double.isNaN(num))
        {
            throw new IllegalArgumentException(v.getString() + " needs to be a numeric value");
        }
        return (float) num;
    }

    private static void theBooYah(ServerLevel level)
    {
        synchronized (level)
        {
            level.getChunkSource().getGeneratorState().ensureStructuresGenerated();
        }
    }

    public static void apply(Expression expression)
    {
        expression.addContextFunction("block", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("Block requires at least one parameter");
            }
            BlockValue retval = BlockArgument.findIn(cc, lv, 0, true).block;
            // fixing block state and data
            retval.getBlockState();
            retval.getData();
            return retval;
        });

        expression.addContextFunction("block_data", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("Block requires at least one parameter");
            }
            return NBTSerializableValue.of(BlockArgument.findIn((ScriptContext) c, lv, 0, true).block.getData());
        });

        // poi_get(pos, radius?, type?, occupation?, column_mode?)
        expression.addContextFunction("poi", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("'poi' requires at least one parameter");
            }
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0, false);
            BlockPos pos = locator.block.getPos();
            PoiManager store = cc.level().getPoiManager();
            Registry<PoiType> poiReg = cc.registry(Registries.POINT_OF_INTEREST_TYPE);
            if (lv.size() == locator.offset)
            {
                Optional<Holder<PoiType>> foo = store.getType(pos);
                if (foo.isEmpty())
                {
                    return Value.NULL;
                }
                PoiType poiType = foo.get().value();

                // this feels wrong, but I don't want to mix-in more than I really need to.
                // also distance adds 0.5 to each point which screws up accurate distance calculations
                // you shoudn't be using POI with that in mind anyways, so I am not worried about it.
                PoiRecord poi = store.getInRange(
                        type -> type.value() == poiType,
                        pos,
                        1,
                        PoiManager.Occupancy.ANY
                ).filter(p -> p.getPos().equals(pos)).findFirst().orElse(null);
                return poi == null ? Value.NULL : ListValue.of(
                        ValueConversions.of(poiReg.getKey(poi.getPoiType().value())),
                        new NumericValue(poiType.maxTickets() - Vanilla.PoiRecord_getFreeTickets(poi))
                );
            }
            int radius = NumericValue.asNumber(lv.get(locator.offset)).getInt();
            if (radius < 0)
            {
                return ListValue.of();
            }
            Predicate<Holder<PoiType>> condition = p -> true;
            PoiManager.Occupancy status = PoiManager.Occupancy.ANY;
            boolean inColumn = false;
            if (locator.offset + 1 < lv.size())
            {
                String poiType = lv.get(locator.offset + 1).getString().toLowerCase(Locale.ROOT);
                if (!"any".equals(poiType))
                {
                    PoiType type = poiReg.getOptional(InputValidator.identifierOf(poiType))
                            .orElseThrow(() -> new ThrowStatement(poiType, Throwables.UNKNOWN_POI));
                    condition = tt -> tt.value() == type;
                }
                if (locator.offset + 2 < lv.size())
                {
                    String statusString = lv.get(locator.offset + 2).getString().toLowerCase(Locale.ROOT);
                    if ("occupied".equals(statusString))
                    {
                        status = PoiManager.Occupancy.IS_OCCUPIED;
                    }
                    else if ("available".equals(statusString))
                    {
                        status = PoiManager.Occupancy.HAS_SPACE;
                    }
                    else if (!("any".equals(statusString)))
                    {
                        throw new InternalExpressionException(
                                "Incorrect POI occupation status " + status + " use `any`, " + "`occupied` or `available`"
                        );
                    }
                    if (locator.offset + 3 < lv.size())
                    {
                        inColumn = lv.get(locator.offset + 3).getBoolean();
                    }
                }
            }
            Stream<PoiRecord> pois = inColumn ?
                    store.getInSquare(condition, pos, radius, status) :
                    store.getInRange(condition, pos, radius, status);
            return ListValue.wrap(pois.sorted(Comparator.comparingDouble(p -> p.getPos().distSqr(pos))).map(p ->
                    ListValue.of(
                            ValueConversions.of(poiReg.getKey(p.getPoiType().value())),
                            new NumericValue(p.getPoiType().value().maxTickets() - Vanilla.PoiRecord_getFreeTickets(p)),
                            ValueConversions.of(p.getPos())
                    )
            ));
        });

        //poi_set(pos, null) poi_set(pos, type, occupied?,
        expression.addContextFunction("set_poi", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("'set_poi' requires at least one parameter");
            }
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0, false);
            BlockPos pos = locator.block.getPos();
            if (lv.size() < locator.offset)
            {
                throw new InternalExpressionException("'set_poi' requires the new poi type or null, after position argument");
            }
            Value poi = lv.get(locator.offset);
            PoiManager store = cc.level().getPoiManager();
            if (poi.isNull())
            {   // clear poi information
                if (store.getType(pos).isEmpty())
                {
                    return Value.FALSE;
                }
                store.remove(pos);
                return Value.TRUE;
            }
            String poiTypeString = poi.getString().toLowerCase(Locale.ROOT);
            Identifier resource = InputValidator.identifierOf(poiTypeString);
            Registry<PoiType> poiReg = cc.registry(Registries.POINT_OF_INTEREST_TYPE);
            PoiType type = poiReg.getOptional(resource)
                    .orElseThrow(() -> new ThrowStatement(poiTypeString, Throwables.UNKNOWN_POI));
            Holder<PoiType> holder = poiReg.getOrThrow(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, resource));

            int occupancy = 0;
            if (locator.offset + 1 < lv.size())
            {
                occupancy = (int) NumericValue.asNumber(lv.get(locator.offset + 1)).getLong();
                if (occupancy < 0)
                {
                    throw new InternalExpressionException("Occupancy cannot be negative");
                }
            }
            if (store.getType(pos).isPresent())
            {
                store.remove(pos);
            }
            store.add(pos, holder);
            // setting occupancy for a
            // again - don't want to mix in unnecessarily - peeps not gonna use it that often so not worries about it.
            if (occupancy > 0)
            {
                int finalO = occupancy;
                store.getInSquare(tt -> tt.value() == type, pos, 1, PoiManager.Occupancy.ANY
                ).filter(p -> p.getPos().equals(pos)).findFirst().ifPresent(p -> {
                    for (int i = 0; i < finalO; i++)
                    {
                        Vanilla.PoiRecord_callAcquireTicket(p);
                    }
                });
            }
            return Value.TRUE;
        });


        expression.addContextFunction("weather", -1, (c, t, lv) -> {
            ServerLevel world = ((ScriptContext) c).level();
            MinecraftServer server = world.getServer();

            if (lv.isEmpty())//cos it can thunder when raining or when clear.
            {
                return new StringValue(world.isThundering() ? "thunder" : (world.isRaining() ? "rain" : "clear"));
            }

            Value weather = lv.get(0);
            WeatherData worldProperties = server.getWeatherData();
            if (lv.size() == 1)
            {
                return new NumericValue(switch (weather.getString().toLowerCase(Locale.ROOT))
                {
                    case "clear" -> worldProperties.getClearWeatherTime();
                    case "rain" -> world.isRaining() ? worldProperties.getRainTime() : 0;//cos if not it gives 1 for some reason
                    case "thunder" -> world.isThundering() ? worldProperties.getThunderTime() : 0;//same dealio here
                    default -> throw new InternalExpressionException("Weather can only be 'clear', 'rain' or 'thunder'");
                });
            }
            if (lv.size() == 2)
            {
                int ticks = NumericValue.asNumber(lv.get(1), "tick_time in 'weather'").getInt();
                switch (weather.getString().toLowerCase(Locale.ROOT))
                {
                    case "clear" -> server.setWeatherParameters(ticks, 0, false, false);
                    case "rain" -> server.setWeatherParameters(0, ticks, true, false);
                    case "thunder" -> server.setWeatherParameters(
                            0,
                            ticks,//this is used to set thunder time, idk why...
                            true,
                            true
                    );
                    default -> throw new InternalExpressionException("Weather can only be 'clear', 'rain' or 'thunder'");
                }
                return NumericValue.of(ticks);
            }
            throw new InternalExpressionException("'weather' requires 0, 1 or 2 arguments");
        });

        expression.addUnaryFunction("pos", v ->
        {
            if (v instanceof final BlockValue bv)
            {
                BlockPos pos = bv.getPos();
                if (pos == null)
                {
                    throw new InternalExpressionException("Cannot fetch position of an unrealized block");
                }
                return ValueConversions.of(pos);
            }
            if (v instanceof final EntityValue ev)
            {
                Entity e = ev.getEntity();
                if (e == null)
                {
                    throw new InternalExpressionException("Null entity");
                }
                return ValueConversions.of(e.position());
            }
            throw new InternalExpressionException("'pos' works only with a block or an entity type");
        });

        expression.addContextFunction("pos_offset", -1, (c, t, lv) ->
        {
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 0);
            BlockPos pos = locator.block.getPos();
            if (lv.size() <= locator.offset)
            {
                throw new InternalExpressionException("'pos_offset' needs at least position, and direction");
            }
            String directionString = lv.get(locator.offset).getString();
            Direction dir = DIRECTION_MAP.get(directionString);
            if (dir == null)
            {
                throw new InternalExpressionException("Unknown direction: " + directionString);
            }
            int howMuch = 1;
            if (lv.size() > locator.offset + 1)
            {
                howMuch = (int) NumericValue.asNumber(lv.get(locator.offset + 1)).getLong();
            }
            return ValueConversions.of(pos.relative(dir, howMuch));
        });

        expression.addContextFunction("solid", -1, (c, t, lv) ->
                genericStateTest(c, "solid", lv, (s, p, w) -> BooleanValue.of(s.isRedstoneConductor(w, p)))); // isSimpleFullBlock

        expression.addContextFunction("air", -1, (c, t, lv) ->
                booleanStateTest(c, "air", lv, (s, p) -> s.isAir()));

        expression.addContextFunction("liquid", -1, (c, t, lv) ->
                booleanStateTest(c, "liquid", lv, (s, p) -> !s.getFluidState().isEmpty()));

        expression.addContextFunction("flammable", -1, (c, t, lv) ->
                booleanStateTest(c, "flammable", lv, (s, p) -> s.ignitedByLava()));

        expression.addContextFunction("transparent", -1, (c, t, lv) ->
                booleanStateTest(c, "transparent", lv, (s, p) -> !s.isSolid()));

        expression.addContextFunction("opacity", -1, (c, t, lv) ->
                genericStateTest(c, "opacity", lv, (s, p, w) -> new NumericValue(s.getLightDampening())));

        // 'blocks_daylight' is the negation of 'propagatesSkylightDown': the name says what the block
        // does to the light, not what the light does to it, which is the question a script author asks
        expression.addContextFunction("blocks_daylight", -1, (c, t, lv) ->
                booleanStateTest(c, "blocks_daylight", lv, (s, p) -> !s.propagatesSkylightDown()));

        /*
         * light(pos): the light present there; light(pos, kind): one of its components.
         *
         * These used to be six functions for the same question, with the answer only changing
         * based on which layer is looked at. The kind comes last, after the position, and the
         * position can be a list or three separate numbers like everywhere else: the locator tells
         * how many arguments it consumed, so the two forms never get confused.
         */
        expression.addContextFunction("light", -1, (c, t, lv) ->
        {
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 0);
            String kind = lv.size() > locator.offset ? lv.get(locator.offset).getString().toLowerCase(Locale.ROOT) : "total";
            BlockPos pos = locator.block.getPos();
            Level level = ((ScriptContext) c).level();
            return switch (kind)
            {
                case "total" -> new NumericValue(Math.max(
                        level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos)));
                case "block" -> new NumericValue(level.getBrightness(LightLayer.BLOCK, pos));
                case "sky" -> new NumericValue(level.getBrightness(LightLayer.SKY, pos));
                case "effective" -> new NumericValue(level.getMaxLocalRawBrightness(pos));
                case "emitted" -> new NumericValue(locator.block.getBlockState().getLightEmission());
                case "brightness" -> new NumericValue(level.getLightLevelDependentMagicValue(pos));
                default -> throw new InternalExpressionException("Unknown light kind: '" + kind
                        + "'. They are total, block, sky, effective, emitted, brightness.");
            };
        });

        expression.addContextFunction("see_sky", -1, (c, t, lv) ->
                genericStateTest(c, "see_sky", lv, (s, p, w) -> BooleanValue.of(w.canSeeSky(p))));

        expression.addContextFunction("brightness", -1, (c, t, lv) ->
                genericStateTest(c, "brightness", lv, (s, p, w) -> new NumericValue(w.getLightLevelDependentMagicValue(p))));

        expression.addContextFunction("hardness", -1, (c, t, lv) ->
                genericStateTest(c, "hardness", lv, (s, p, w) -> new NumericValue(s.getDestroySpeed(w, p))));

        expression.addContextFunction("blast_resistance", -1, (c, t, lv) ->
                genericStateTest(c, "blast_resistance", lv, (s, p, w) -> new NumericValue(s.getBlock().getExplosionResistance())));

        expression.addContextFunction("in_slime_chunk", -1, (c, t, lv) ->
        {
            BlockPos pos = BlockArgument.findIn((ScriptContext) c, lv, 0).block.getPos();
            ChunkPos chunkPos = ChunkPos.containing(pos);
            return BooleanValue.of(WorldgenRandom.seedSlimeChunk(
                    chunkPos.x(), chunkPos.z(),
                    ((ScriptContext) c).level().getSeed(),
                    987234911L
            ).nextInt(10) == 0);
        });

        expression.addContextFunction("top", -1, (c, t, lv) ->
        {
            String type = lv.get(0).getString().toLowerCase(Locale.ROOT);
            Heightmap.Types htype = switch (type)
            {
                //case "light": htype = Heightmap.Type.LIGHT_BLOCKING; break;  //investigate
                case "motion" -> Heightmap.Types.MOTION_BLOCKING;
                case "terrain" -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
                case "ocean_floor" -> Heightmap.Types.OCEAN_FLOOR;
                case "surface" -> Heightmap.Types.WORLD_SURFACE;
                default -> throw new InternalExpressionException("Unknown heightmap type: " + type);
            };
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 1);
            BlockPos pos = locator.block.getPos();
            int x = pos.getX();
            int z = pos.getZ();
            return new NumericValue(((ScriptContext) c).level().getChunk(x >> 4, z >> 4).getHeight(htype, x & 15, z & 15) + 1);
        });

        expression.addContextFunction("loaded", -1, (c, t, lv) ->
                BooleanValue.of((((ScriptContext) c).level().hasChunkAt(BlockArgument.findIn((ScriptContext) c, lv, 0).block.getPos()))));

        // Deprecated, use loaded_status as more indicative
        expression.addContextFunction("loaded_ep", -1, (c, t, lv) ->
        {
            BlockPos pos = BlockArgument.findIn((ScriptContext) c, lv, 0).block.getPos();
            return BooleanValue.of(((ScriptContext) c).level().isPositionEntityTicking(pos));
        });

        expression.addContextFunction("loaded_status", -1, (c, t, lv) ->
        {
            BlockPos pos = BlockArgument.findIn((ScriptContext) c, lv, 0).block.getPos();
            LevelChunk chunk = ((ScriptContext) c).level().getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
            return chunk == null ? Value.ZERO : new NumericValue(chunk.getFullStatus().ordinal());
        });

        expression.addContextFunction("is_chunk_generated", -1, (c, t, lv) ->
        {
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 0);
            BlockPos pos = locator.block.getPos();
            boolean force = false;
            if (lv.size() > locator.offset)
            {
                force = lv.get(locator.offset).getBoolean();
            }
            return BooleanValue.of(canHasChunk(((ScriptContext) c).level(), ChunkPos.containing(pos), null, force));
        });

        expression.addContextFunction("generation_status", -1, (c, t, lv) ->
        {
            BlockArgument blockArgument = BlockArgument.findIn((ScriptContext) c, lv, 0);
            BlockPos pos = blockArgument.block.getPos();
            boolean forceLoad = false;
            if (lv.size() > blockArgument.offset)
            {
                forceLoad = lv.get(blockArgument.offset).getBoolean();
            }
            ChunkAccess chunk = ((ScriptContext) c).level().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY, forceLoad);
            return chunk == null ? Value.NULL : ValueConversions.of(BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()));
        });

        expression.addContextFunction("chunk_tickets", -1, (c, t, lv) ->
        {
            ServerLevel world = ((ScriptContext) c).level();
            DistanceManager foo = world.getChunkSource().chunkMap.getDistanceManager();
            Long2ObjectOpenHashMap<List<Ticket>> levelTickets = Vanilla.ChunkTicketManager_getTicketsByPosition(foo);

            List<Value> res = new ArrayList<>();
            if (lv.isEmpty())
            {
                for (long key : levelTickets.keySet())
                {
                    ChunkPos chpos = ChunkPos.unpack(key);
                    for (Ticket ticket : levelTickets.get(key))
                    {
                        res.add(ListValue.of(
                                new StringValue(ticket.getType().toString()),
                                new NumericValue(33 - ticket.getTicketLevel()),
                                new NumericValue(chpos.x()),
                                new NumericValue(chpos.z())
                        ));
                    }
                }
            }
            else
            {
                BlockArgument blockArgument = BlockArgument.findIn((ScriptContext) c, lv, 0);
                BlockPos pos = blockArgument.block.getPos();
                List<Ticket> tickets = levelTickets.get(ChunkPos.containing(pos).pack());
                if (tickets != null)
                {
                    for (Ticket ticket : tickets)
                    {
                        res.add(ListValue.of(
                                new StringValue(ticket.getType().toString()),
                                new NumericValue(33 - ticket.getTicketLevel())
                        ));
                    }
                }
            }
            res.sort(Comparator.comparing(e -> ((ListValue) e).getItems().get(1)).reversed());
            return ListValue.wrap(res);
        });

        expression.addContextFunction("suffocates", -1, (c, t, lv) ->
                genericStateTest(c, "suffocates", lv, (s, p, w) -> BooleanValue.of(s.isSuffocating(w, p)))); // canSuffocate

        expression.addContextFunction("power", -1, (c, t, lv) ->
                genericStateTest(c, "power", lv, (s, p, w) -> new NumericValue(w.getBestNeighborSignal(p))));

        expression.addContextFunction("ticks_randomly", -1, (c, t, lv) ->
                booleanStateTest(c, "ticks_randomly", lv, (s, p) -> s.isRandomlyTicking()));

        expression.addContextFunction("update", -1, (c, t, lv) ->
                booleanStateTest(c, "update", lv, (s, p) ->
                {
                    ((ScriptContext) c).level().neighborChanged(p, s.getBlock(), null);
                    return true;
                }));

        expression.addContextFunction("block_tick", -1, (c, t, lv) ->
                booleanStateTest(c, "block_tick", lv, (s, p) ->
                {
                    ServerLevel w = ((ScriptContext) c).level();
                    s.randomTick(w, p, w.getRandom());
                    return true;
                }));

        expression.addContextFunction("random_tick", -1, (c, t, lv) ->
                booleanStateTest(c, "random_tick", lv, (s, p) ->
                {
                    ServerLevel w = ((ScriptContext) c).level();
                    if (s.isRandomlyTicking() || s.getFluidState().isRandomlyTicking())
                    {
                        s.randomTick(w, p, w.getRandom());
                    }
                    return true;
                }));

        // lazy cause its parked execution
        expression.addLazyFunction("without_updates", 1, (c, t, lv) ->
        {
            if (Host.getImpendingFillSkipUpdates().get())
            {
                return lv.get(0);
            }
            Value[] result = new Value[]{Value.NULL};
            ((ScriptContext) c).server().executeBlocking(() ->
            {
                ThreadLocal<Boolean> skipUpdates = Host.getImpendingFillSkipUpdates();
                boolean previous = skipUpdates.get();
                try
                {
                    skipUpdates.set(true);
                    result[0] = lv.get(0).evalValue(c, t);
                }
                finally
                {
                    skipUpdates.set(previous);
                }
            });
            return (cc, tt) -> result[0];
        });

        expression.addContextFunction("set", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerLevel world = cc.level();
            BlockArgument targetLocator = BlockArgument.findIn(cc, lv, 0);
            BlockArgument sourceLocator = BlockArgument.findIn(cc, lv, targetLocator.offset, true);
            BlockState sourceBlockState = sourceLocator.block.getBlockState();
            BlockState targetBlockState = world.getBlockState(targetLocator.block.getPos());
            CompoundTag data = null;
            if (lv.size() > sourceLocator.offset)
            {
                List<Value> args = new ArrayList<>();
                for (int i = sourceLocator.offset, m = lv.size(); i < m; i++)
                {
                    args.add(lv.get(i));
                }
                if (args.get(0) instanceof final ListValue list)
                {
                    if (args.size() == 2 && NBTSerializableValue.fromValue(args.get(1)) instanceof final NBTSerializableValue nbtsv)
                    {
                        data = nbtsv.getCompoundTag();
                    }
                    args = list.getItems();
                }
                else if (args.get(0) instanceof final MapValue map)
                {
                    if (args.size() == 2 && NBTSerializableValue.fromValue(args.get(1)) instanceof final NBTSerializableValue nbtsv)
                    {
                        data = nbtsv.getCompoundTag();
                    }
                    Map<Value, Value> state = map.getMap();
                    List<Value> mapargs = new ArrayList<>();
                    state.forEach((k, v) -> {
                        mapargs.add(k);
                        mapargs.add(v);
                    });
                    args = mapargs;
                }
                else
                {
                    if ((args.size() & 1) == 1 && NBTSerializableValue.fromValue(args.get(args.size() - 1)) instanceof final NBTSerializableValue nbtsv)
                    {
                        data = nbtsv.getCompoundTag();
                    }
                }
                StateDefinition<Block, BlockState> states = sourceBlockState.getBlock().getStateDefinition();
                for (int i = 0; i < args.size() - 1; i += 2)
                {
                    String paramString = args.get(i).getString();
                    Property<?> property = states.getProperty(paramString);
                    if (property == null)
                    {
                        throw new InternalExpressionException("Property " + paramString + " doesn't apply to " + sourceLocator.block.getString());
                    }
                    String paramValue = args.get(i + 1).getString();
                    sourceBlockState = setProperty(property, paramString, paramValue, sourceBlockState);
                }
            }

            if (data == null)
            {
                data = sourceLocator.block.getData();
            }
            CompoundTag finalData = data;

            if (sourceBlockState == targetBlockState && data == null)
            {
                return Value.FALSE;
            }
            BlockState finalSourceBlockState = sourceBlockState;
            BlockPos targetPos = targetLocator.block.getPos();
            Boolean[] result = new Boolean[]{true};
            cc.server().executeBlocking(() ->
            {
                boolean success = world.setBlock(targetPos, finalSourceBlockState, Block.UPDATE_CLIENTS  | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS );
                if (finalData != null)
                {
                    BlockEntity be = world.getBlockEntity(targetPos);
                    if (be != null)
                    {
                        CompoundTag destTag = finalData.copy();
                        destTag.putInt("x", targetPos.getX());
                        destTag.putInt("y", targetPos.getY());
                        destTag.putInt("z", targetPos.getZ());
                        try (final ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), ScriptRuntime.LOG)) {
                            be.loadWithComponents(TagValueInput.create(reporter, world.registryAccess(), destTag));
                        }
                        be.setChanged();
                        success = true;
                    }
                }
                result[0] = success;
            });
            return !result[0] ? Value.FALSE : new BlockValue(finalSourceBlockState, world, targetLocator.block.getPos());
        });

        expression.addContextFunction("destroy", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            RegistryAccess regs = cc.registryAccess();
            ServerLevel world = cc.level();
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockState state = locator.block.getBlockState();
            if (state.isAir())
            {
                return Value.FALSE;
            }
            BlockPos where = locator.block.getPos();
            BlockEntity be = world.getBlockEntity(where);
            long how = 0;
            Item item = Items.DIAMOND_PICKAXE;
            boolean playerBreak = false;
            if (lv.size() > locator.offset)
            {
                Value val = lv.get(locator.offset);
                if (val instanceof final NumericValue number)
                {
                    how = number.getLong();
                }
                else
                {
                    playerBreak = true;
                    String itemString = val.getString();
                    item = cc.registry(Registries.ITEM).getOptional(InputValidator.identifierOf(itemString))
                            .orElseThrow(() -> new ThrowStatement(itemString, Throwables.UNKNOWN_ITEM));
                }
            }
            CompoundTag tag = null;
            if (lv.size() > locator.offset + 1)
            {
                if (!playerBreak)
                {
                    throw new InternalExpressionException("tag is not necessary with 'destroy' with no item");
                }
                Value tagValue = lv.get(locator.offset + 1);
                if (!tagValue.isNull())
                {
                    tag = tagValue instanceof final NBTSerializableValue nbtsv
                            ? nbtsv.getCompoundTag()
                            : NBTSerializableValue.parseStringOrFail(tagValue.getString()).getCompoundTag();
                }
            }
            ItemStack tool;
            if (tag != null)
            {
                tool = ItemStack.CODEC.parse(regs.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow(s -> new InternalExpressionException("Failed to parse item stack data: " + s));
            }
            else
            {
                tool = new ItemStack(item, 1);
            }
            if (playerBreak && state.getDestroySpeed(world, where) < 0.0)
            {
                return Value.FALSE;
            }
            boolean removed = world.removeBlock(where, false);
            if (!removed)
            {
                return Value.FALSE;
            }
            world.levelEvent(null, 2001, where, Block.getId(state));

            final MutableBoolean toolBroke = new MutableBoolean(false);
            boolean dropLoot = true;
            if (playerBreak)
            {
                boolean isUsingEffectiveTool = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
                //postMine() durability from item classes
                float hardness = state.getDestroySpeed(world, where);
                int damageAmount = 0;
                if ((tool.is(ItemTags.PICKAXES) && hardness > 0.0) || item instanceof ShearsItem)
                {
                    damageAmount = 1;
                }
                else if (item instanceof TridentItem || tool.is(ItemTags.SWORDS))
                {
                    damageAmount = 2;
                }
                final int finalDamageAmount = damageAmount;
                tool.hurtAndBreak(damageAmount, world, null, (i) ->  { if (finalDamageAmount > 0) toolBroke.setTrue(); } );
                if (!isUsingEffectiveTool)
                {
                    dropLoot = false;
                }
            }

            if (dropLoot)
            {
                if (how < 0 || (tag != null && EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0))
                {
                    Block.popResource(world, where, new ItemStack(state.getBlock()));
                }
                else
                {
                    if (how > 0)
                    {
                        tool.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), (int) how);
                    }
                    // no breaker: an entity built without a level crashed the loot tables that read it
                    Block.dropResources(state, world, where, be, null, tool);
                }
            }
            if (!playerBreak) // no tool info - block brokwn
            {
                return Value.TRUE;
            }
            if (toolBroke.booleanValue())
            {
                return Value.NULL;
            }
            return new NBTSerializableValue(() -> ItemStack.CODEC.encodeStart(regs.createSerializationContext(NbtOps.INSTANCE), tool).getOrThrow(s -> new InternalExpressionException("Failed to parse item stack data: " + s)));

        });

        expression.addContextFunction("harvest", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'harvest' takes at least 2 parameters: entity and block, or position, to harvest");
            }
            ScriptContext cc = (ScriptContext) c;
            if (!(lv.get(0) instanceof final EntityValue ev) || !(ev.getEntity() instanceof final ServerPlayer player))
            {
                return Value.FALSE;
            }
            BlockArgument locator = BlockArgument.findIn(cc, lv, 1);
            BlockPos where = locator.block.getPos();
            BlockState state = locator.block.getBlockState();
            Block block = state.getBlock();
            if ((block == Blocks.BEDROCK || block == Blocks.BARRIER) && player.gameMode.isSurvival())
            {
                return Value.FALSE;
            }
            // rottura artificiale: passa per il gioco, quindi drop e statistiche restano quelli
            // veri, ma non è un'azione del giocatore e nessun evento deve partire
            if (!Event.suppressing(() -> player.gameMode.destroyBlock(where)))
            {
                return Value.FALSE;
            }
            cc.level().levelEvent(null, 2001, where, Block.getId(state));
            return Value.TRUE;
        });

        // from ServerLevel = don't wanna mixin this in, as it is PITA
        WeightedList<ExplosionParticleInfo> DEFAULT_EXPLOSION_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
                .add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
                .add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
                .build();

        expression.addContextFunction("create_explosion", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new InternalExpressionException("'create_explosion' requires at least a position to explode");
            }
            ScriptContext cc = (ScriptContext) c;
            float powah = 4.0f;
            Explosion.BlockInteraction mode = Explosion.BlockInteraction.DESTROY; // should probably read the gamerule for default behaviour
            boolean createFire = false;
            Entity source = null;
            LivingEntity attacker = null;
            Vector3Argument location = Vector3Argument.findIn(lv, 0, false, true);
            Vec3 pos = location.vec;
            if (lv.size() > location.offset)
            {
                powah = NumericValue.asNumber(lv.get(location.offset), "explosion power").getFloat();
                if (powah < 0)
                {
                    throw new InternalExpressionException("Explosion power cannot be negative");
                }
                if (lv.size() > location.offset + 1)
                {
                    String strval = lv.get(location.offset + 1).getString();
                    try
                    {
                        mode = Explosion.BlockInteraction.valueOf(strval.toUpperCase(Locale.ROOT));
                    }
                    catch (IllegalArgumentException ile)
                    {
                        throw new InternalExpressionException("Illegal explosions block behaviour: " + strval);
                    }
                    if (lv.size() > location.offset + 2)
                    {
                        createFire = lv.get(location.offset + 2).getBoolean();
                        if (lv.size() > location.offset + 3)
                        {
                            Value enVal = lv.get(location.offset + 3);
                            if (!enVal.isNull())
                            {
                                if (enVal instanceof final EntityValue ev)
                                {
                                    source = ev.getEntity();
                                }
                                else
                                {
                                    throw new InternalExpressionException("Fourth parameter of the explosion has to be an entity, not " + enVal.getTypeString());
                                }
                            }
                            if (lv.size() > location.offset + 4)
                            {
                                enVal = lv.get(location.offset + 4);
                                if (!enVal.isNull())
                                {
                                    if (enVal instanceof final EntityValue ev)
                                    {
                                        Entity attackingEntity = ev.getEntity();
                                        if (attackingEntity instanceof final LivingEntity le)
                                        {
                                            attacker = le;
                                        }
                                        else
                                        {
                                            throw new InternalExpressionException("Attacking entity needs to be a living thing, " +
                                                    ValueConversions.of(cc.registry(Registries.ENTITY_TYPE).getKey(attackingEntity.getType())).getString() + " ain't it.");
                                        }
                                    }
                                    else
                                    {
                                        throw new InternalExpressionException("Fifth parameter of the explosion has to be a living entity, not " + enVal.getTypeString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LivingEntity theAttacker = attacker;

            // copy of ServerWorld.createExplosion #TRACK#
            ServerExplosion serverExplosion = new ServerExplosion(cc.level(), source, null, null, pos, powah, createFire, mode)
            {
                @Override
                @Nullable
                public
                LivingEntity getIndirectSourceEntity()
                {
                    return theAttacker;
                }
            };
            int i = serverExplosion.explode();
            ParticleOptions particleOptions3 = serverExplosion.isSmall() ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;

            for (ServerPlayer serverPlayer : cc.level().players()) {
                if (serverPlayer.distanceToSqr(pos) < 4096.0) {
                    Optional<Vec3> optional = Optional.ofNullable((Vec3)serverExplosion.getHitPlayers().get(serverPlayer));
                    serverPlayer.connection.send(new ClientboundExplodePacket(pos, powah, i, optional, Optional.of(particleOptions3), Optional.of(SoundEvents.GENERIC_EXPLODE), DEFAULT_EXPLOSION_BLOCK_PARTICLES));
                }
            }
            return Value.TRUE;
        });

        // TODO rename to use_item
        expression.addContextFunction("place_item", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'place_item' takes at least 2 parameters: item and block, or position, to place onto");
            }
            ScriptContext cc = (ScriptContext) c;
            String itemString = lv.get(0).getString();
            Vector3Argument locator = Vector3Argument.findIn(lv, 1);
            ItemStack stackArg = NBTSerializableValue.parseItem(itemString, cc.registryAccess());
            BlockPos where = BlockPos.containing(locator.vec);
            // Paintings throw an exception if their direction is vertical, therefore we change the default here
            String facing = lv.size() > locator.offset
                    ? lv.get(locator.offset).getString()
                    : stackArg.getItem() != Items.PAINTING ? "up" : "north";
            boolean sneakPlace = false;
            if (lv.size() > locator.offset + 1)
            {
                sneakPlace = lv.get(locator.offset + 1).getBoolean();
            }

            BlockValue.PlacementContext ctx = BlockValue.PlacementContext.from(cc.level(), where, facing, sneakPlace, stackArg);

            if (!(stackArg.getItem() instanceof final BlockItem blockItem))
            {
                InteractionResult useResult = ctx.getItemInHand().useOn(ctx);
                if (useResult == InteractionResult.CONSUME || useResult == InteractionResult.SUCCESS)
                {
                    return Value.TRUE;
                }
            }
            else
            { // not sure we need special case for block items, since useOnBlock can do that as well
                if (!ctx.canPlace())
                {
                    return Value.FALSE;
                }
                BlockState placementState = blockItem.getBlock().getStateForPlacement(ctx);
                if (placementState != null)
                {
                    Level level = ctx.getLevel();
                    if (placementState.canSurvive(level, where))
                    {
                        level.setBlock(where, placementState, 2);
                        BlockSoundSet sounds = placementState.getSounds(level);
                        sounds.placeSound().ifPresent(sound -> level.playSound(null, where, sound.value(), SoundSource.BLOCKS, (sounds.volume() + 1.0F) / 2.0F, sounds.pitch() * 0.8F));
                        return Value.TRUE;
                    }
                }
            }
            return Value.FALSE;
        });

        expression.addContextFunction("blocks_movement", -1, (c, t, lv) ->
                booleanStateTest(c, "blocks_movement", lv, (s, p) ->
                        !s.isPathfindable(PathComputationType.LAND)));

        expression.addContextFunction("block_sound", -1, (c, t, lv) ->
                stateStringQuery(c, "block_sound", lv, (s, p) ->
                        s.getSounds().map(key -> NBTSerializableValue.nameFromResource(key.identifier())).orElse(null)));

        expression.addContextFunction("map_colour", -1, (c, t, lv) ->
                stateStringQuery(c, "map_colour", lv, (s, p) ->
                        Colors.mapColourName.get(s.getMapColor(((ScriptContext) c).level(), p))));

        expression.addContextFunction("block_state", -1, (c, t, lv) ->
        {
            BlockArgument locator = BlockArgument.findIn((ScriptContext) c, lv, 0, true);
            BlockState state = locator.block.getBlockState();
            StateDefinition<Block, BlockState> states = state.getBlock().getStateDefinition();
            if (locator.offset == lv.size())
            {
                Map<Value, Value> properties = new HashMap<>();
                for (Property<?> p : states.getProperties())
                {
                    properties.put(StringValue.of(p.getName()), ValueConversions.fromProperty(state, p));
                }
                return MapValue.wrap(properties);
            }
            String tag = lv.get(locator.offset).getString();
            Property<?> property = states.getProperty(tag);
            return property == null ? Value.NULL : ValueConversions.fromProperty(state, property);
        });

        expression.addContextFunction("block_list", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            Registry<Block> blocks = cc.registry(Registries.BLOCK);
            if (lv.isEmpty())
            {
                return ListValue.wrap(blocks.listElements().map(blockReference -> ValueConversions.of(blockReference.key().identifier())));
            }
            Identifier tag = InputValidator.identifierOf(lv.get(0).getString());
            Optional<HolderSet.Named<Block>> tagset = blocks.get(TagKey.create(Registries.BLOCK, tag));
            return tagset.isEmpty() ? Value.NULL : ListValue.wrap(tagset.get().stream().map(b -> ValueConversions.of(blocks.getKey(b.value()))));
        });

        expression.addContextFunction("block_tags", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            Registry<Block> blocks = cc.registry(Registries.BLOCK);
            if (lv.isEmpty())
            {
                return ListValue.wrap(blocks.getTags().map(ValueConversions::of));
            }
            BlockArgument blockLocator = BlockArgument.findIn(cc, lv, 0, true);
            if (blockLocator.offset == lv.size())
            {
                Block target = blockLocator.block.getBlockState().getBlock();
                return ListValue.wrap(blocks.getTags().filter(e -> e.stream().anyMatch(h -> (h.value() == target))).map(ValueConversions::of));
            }
            String tag = lv.get(blockLocator.offset).getString();
            Optional<HolderSet.Named<Block>> tagSet = blocks.get(TagKey.create(Registries.BLOCK, InputValidator.identifierOf(tag)));
            return tagSet.isEmpty() ? Value.NULL : BooleanValue.of(blockLocator.block.getBlockState().is(tagSet.get()));
        });

        expression.addContextFunction("biome", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            ServerLevel world = cc.level();
            if (lv.isEmpty())
            {
                return ListValue.wrap(cc.registry(Registries.BIOME).listElements().map(biomeReference -> ValueConversions.of(biomeReference.key().identifier())));
            }

            Biome biome;
            BiomeSource biomeSource = world.getChunkSource().getGenerator().getBiomeSource();
            if (lv.size() == 1
                    && lv.get(0) instanceof final MapValue map
                    && biomeSource instanceof final MultiNoiseBiomeSource mnbs
            )
            {
                Value temperature = map.get(new StringValue("temperature"));
                nullCheck(temperature, "temperature");

                Value humidity = map.get(new StringValue("humidity"));
                nullCheck(humidity, "humidity");

                Value continentalness = map.get(new StringValue("continentalness"));
                nullCheck(continentalness, "continentalness");

                Value erosion = map.get(new StringValue("erosion"));
                nullCheck(erosion, "erosion");

                Value depth = map.get(new StringValue("depth"));
                nullCheck(depth, "depth");

                Value weirdness = map.get(new StringValue("weirdness"));
                nullCheck(weirdness, "weirdness");

                Climate.TargetPoint point = new Climate.TargetPoint(
                        Climate.quantizeCoord(numberGetOrThrow(temperature)),
                        Climate.quantizeCoord(numberGetOrThrow(humidity)),
                        Climate.quantizeCoord(numberGetOrThrow(continentalness)),
                        Climate.quantizeCoord(numberGetOrThrow(erosion)),
                        Climate.quantizeCoord(numberGetOrThrow(depth)),
                        Climate.quantizeCoord(numberGetOrThrow(weirdness))
                );
                biome = mnbs.getNoiseBiome(point).value();
                Identifier biomeId = cc.registry(Registries.BIOME).getKey(biome);
                return NBTSerializableValue.nameFromRegistryId(biomeId);
            }

            BlockArgument locator = BlockArgument.findIn(cc, lv, 0, false, false, true);

            if (locator.replacement != null)
            {
                biome = world.registryAccess().lookupOrThrow(Registries.BIOME).getValue(InputValidator.identifierOf(locator.replacement));
                if (biome == null)
                {
                    throw new ThrowStatement(locator.replacement, Throwables.UNKNOWN_BIOME);
                }
            }
            else
            {
                biome = world.getBiome(locator.block.getPos()).value();
            }
            // in locatebiome
            if (locator.offset == lv.size())
            {
                Identifier biomeId = cc.registry(Registries.BIOME).getKey(biome);
                return NBTSerializableValue.nameFromRegistryId(biomeId);
            }
            String biomeFeature = lv.get(locator.offset).getString();
            BiFunction<ServerLevel, Biome, Value> featureProvider = BiomeInfo.biomeFeatures.get(biomeFeature);
            if (featureProvider == null)
            {
                throw new InternalExpressionException("Unknown biome feature: " + biomeFeature);
            }
            return featureProvider.apply(world, biome);
        });

        expression.addContextFunction("set_biome", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            if (lv.size() == locator.offset)
            {
                throw new InternalExpressionException("'set_biome' needs a biome name as an argument");
            }
            String biomeName = lv.get(locator.offset).getString();
            // from locatebiome command code
            Holder<Biome> biome = cc.registry(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, InputValidator.identifierOf(biomeName)))
                    .orElseThrow(() -> new ThrowStatement(biomeName, Throwables.UNKNOWN_BIOME));
            boolean doImmediateUpdate = true;
            if (lv.size() > locator.offset + 1)
            {
                doImmediateUpdate = lv.get(locator.offset + 1).getBoolean();
            }
            ServerLevel world = cc.level();
            BlockPos pos = locator.block.getPos();
            ChunkAccess chunk = world.getChunk(pos); // getting level chunk instead of protochunk with biomes
            // since 26.4 the biome is stored per block, no longer in cells of 4x4x4
            try
            {
                int y = Math.clamp(pos.getY(), chunk.getMinY(), chunk.getMaxY());
                // accessing outside of the interface - might be dangerous in the future.
                ((PalettedContainer<Holder<Biome>>) chunk.getSection(chunk.getSectionIndex(y)).getBiomes()).set(
                        SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(y), SectionPos.sectionRelative(pos.getZ()), biome);
            }
            catch (Throwable var8)
            {
                return Value.FALSE;
            }
            if (doImmediateUpdate)
            {
                // the game has its own packet for biomes: no need to resend the whole chunk
                world.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
            }
            chunk.markUnsaved();
            return Value.TRUE;
        });

        expression.addContextFunction("reload_chunk", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            BlockPos pos = BlockArgument.findIn(cc, lv, 0).block.getPos();
            ServerLevel world = cc.level();
            cc.server().executeBlocking(() -> WorldTools.forceChunkUpdate(pos, world));
            return Value.TRUE;
        });

        expression.addContextFunction("structure_references", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            ServerLevel world = cc.level();
            BlockPos pos = locator.block.getPos();
            Map<Structure, LongSet> references = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
            Registry<Structure> reg = cc.registry(Registries.STRUCTURE);
            if (lv.size() == locator.offset)
            {
                return ListValue.wrap(references.entrySet().stream().
                        filter(e -> e.getValue() != null && !e.getValue().isEmpty()).
                        map(e -> NBTSerializableValue.nameFromRegistryId(reg.getKey(e.getKey())))
                );
            }
            String simpleStructureName = lv.get(locator.offset).getString().toLowerCase(Locale.ROOT);
            Structure structureName = reg.getValue(InputValidator.identifierOf(simpleStructureName));
            if (structureName == null)
            {
                return Value.NULL;
            }
            LongSet structureReferences = references.get(structureName);
            if (structureReferences == null || structureReferences.isEmpty())
            {
                return ListValue.of();
            }
            return ListValue.wrap(structureReferences.longStream().mapToObj(l -> ListValue.of(
                    new NumericValue(16L * ChunkPos.getX(l)),
                    Value.ZERO,
                    new NumericValue(16L * ChunkPos.getZ(l)))));
        });

        expression.addContextFunction("structure_eligibility", -1, (c, t, lv) ->
        {// TODO rename structureName to class
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);

            ServerLevel world = cc.level();

            // well, because
            theBooYah(world);

            BlockPos pos = locator.block.getPos();
            List<Structure> structure = new ArrayList<>();
            boolean needSize = false;
            boolean singleOutput = false;
            Registry<Structure> reg = cc.registry(Registries.STRUCTURE);
            if (lv.size() > locator.offset)
            {
                Value requested = lv.get(locator.offset);
                if (!requested.isNull())
                {
                    String reqString = requested.getString();
                    Identifier id = InputValidator.identifierOf(reqString);
                    Structure requestedStructure = reg.getValue(id);
                    if (requestedStructure != null)
                    {
                        singleOutput = true;
                        structure.add(requestedStructure);
                    }
                    else
                    {
                        StructureType<?> sss = cc.registry(Registries.STRUCTURE_TYPE).getValue(id);
                        reg.entrySet().stream().filter(e -> e.getValue().type() == sss).forEach(e -> structure.add(e.getValue()));
                    }
                    if (structure.isEmpty())
                    {
                        throw new ThrowStatement(reqString, Throwables.UNKNOWN_STRUCTURE);
                    }

                }
                else
                {
                    structure.addAll(reg.entrySet().stream().map(Map.Entry::getValue).toList());
                }
                if (lv.size() > locator.offset + 1)
                {
                    needSize = lv.get(locator.offset + 1).getBoolean();
                }
            }
            else
            {
                structure.addAll(reg.entrySet().stream().map(Map.Entry::getValue).toList());
            }
            if (singleOutput)
            {
                if (!needSize)
                {
                    return FeatureGenerator.canStructureStartAt(world, pos, structure.get(0)) ? Value.TRUE : Value.NULL;
                }
                StructureStart start = FeatureGenerator.structureStartAt(world, pos, structure.get(0));
                return start == null ? Value.NULL : ValueConversions.of(start, cc.registryAccess());
            }
            Map<Value, Value> ret = new HashMap<>();
            for (Structure str : structure)
            {
                Value found;
                try
                {
                    if (needSize)
                    {
                        StructureStart start = FeatureGenerator.structureStartAt(world, pos, str);
                        found = start == null ? null : ValueConversions.of(start, cc.registryAccess());
                    }
                    else
                    {
                        found = FeatureGenerator.canStructureStartAt(world, pos, str) ? Value.NULL : null;
                    }
                }
                catch (NullPointerException npe)
                {
                    ScriptRuntime.LOG.error("Failed to detect structure: " + reg.getKey(str));
                    found = null;
                }
                if (found != null)
                {
                    ret.put(NBTSerializableValue.nameFromRegistryId(reg.getKey(str)), found);
                }
            }
            return MapValue.wrap(ret);
        });

        expression.addContextFunction("structures", -1, (c, t, lv) -> {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);

            ServerLevel world = cc.level();
            BlockPos pos = locator.block.getPos();
            Map<Structure, StructureStart> structures = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getAllStarts();
            Registry<Structure> reg = cc.registry(Registries.STRUCTURE);
            if (lv.size() == locator.offset)
            {
                Map<Value, Value> structureList = new HashMap<>();
                for (Map.Entry<Structure, StructureStart> entry : structures.entrySet())
                {
                    StructureStart start = entry.getValue();
                    BoundingBox box = start.getBoundingBox();
                    structureList.put(
                            NBTSerializableValue.nameFromRegistryId(reg.getKey(entry.getKey())),
                            ValueConversions.of(box)
                    );
                }
                return MapValue.wrap(structureList);
            }
            String structureName = lv.get(locator.offset).getString().toLowerCase(Locale.ROOT);
            return ValueConversions.of(structures.get(reg.getValue(InputValidator.identifierOf(structureName))), cc.registryAccess());
        });

        expression.addContextFunction("set_structure", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);

            ServerLevel world = cc.level();
            BlockPos pos = locator.block.getPos();

            if (lv.size() == locator.offset)
            {
                throw new InternalExpressionException("'set_structure requires at least position and a structure name");
            }
            String structureName = lv.get(locator.offset).getString().toLowerCase(Locale.ROOT);
            Structure configuredStructure = FeatureGenerator.resolveConfiguredStructure(structureName, world, pos);
            if (configuredStructure == null)
            {
                throw new ThrowStatement(structureName, Throwables.UNKNOWN_STRUCTURE);
            }
            // good 'ol pointer
            Value[] result = new Value[]{Value.NULL};
            // technically a world modification. Even if we could let it slide, we will still park it
            ((ScriptContext) c).server().executeBlocking(() ->
            {
                Map<Structure, StructureStart> structures = world.getChunk(pos).getAllStarts();
                if (lv.size() == locator.offset + 1)
                {
                    boolean res = FeatureGenerator.plopGrid(configuredStructure, ((ScriptContext) c).level(), locator.block.getPos());
                    result[0] = res ? Value.TRUE : Value.FALSE;
                    return;
                }
                Value newValue = lv.get(locator.offset + 1);
                if (newValue.isNull()) // remove structure
                {
                    if (!structures.containsKey(configuredStructure))
                    {
                        return;
                    }
                    StructureStart start = structures.get(configuredStructure);
                    ChunkPos structureChunkPos = start.getChunkPos();
                    BoundingBox box = start.getBoundingBox();
                    for (int chx = box.minX() / 16; chx <= box.maxX() / 16; chx++)  // minx maxx
                    {
                        for (int chz = box.minZ() / 16; chz <= box.maxZ() / 16; chz++) //minZ maxZ
                        {
                            ChunkPos chpos = new ChunkPos(chx, chz);
                            // getting a chunk will convert it to full, allowing to modify references
                            Map<Structure, LongSet> references =
                                    world.getChunk(chpos.getWorldPosition()).getAllReferences();
                            if (references.containsKey(configuredStructure) && references.get(configuredStructure) != null)
                            {
                                references.get(configuredStructure).remove(structureChunkPos.pack());
                            }
                        }
                    }
                    structures.remove(configuredStructure);
                    result[0] = Value.TRUE;
                }
            });
            return result[0]; // preventing from lazy evaluating of the result in case a future completes later
        });

        expression.addContextFunction("inhabited_time", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockPos pos = locator.block.getPos();
            return new NumericValue(cc.level().getChunk(pos).getInhabitedTime());
        });

        expression.addContextFunction("spawn_potential", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockPos pos = locator.block.getPos();
            double requiredCharge = 1;
            if (lv.size() > locator.offset)
            {
                requiredCharge = NumericValue.asNumber(lv.get(locator.offset)).getDouble();
            }
            NaturalSpawner.SpawnState charger = cc.level().getChunkSource().getLastSpawnState();
            return charger == null ? Value.NULL : new NumericValue(Vanilla.SpawnState_getPotentialCalculator(charger).getPotentialEnergyChange(pos, requiredCharge)
            );
        });

        expression.addContextFunction("add_chunk_ticket", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockPos pos = locator.block.getPos();
            if (lv.size() != locator.offset + 2)
            {
                throw new InternalExpressionException("'add_chunk_ticket' requires block position, ticket type and radius");
            }
            String type = lv.get(locator.offset).getString();
            TicketType ticket = ticketTypes.get(type.toLowerCase(Locale.ROOT));
            if (ticket == null)
            {
                throw new InternalExpressionException("Unknown ticket type: " + type);
            }
            int radius = NumericValue.asNumber(lv.get(locator.offset + 1)).getInt();
            if (radius < 1 || radius > 32)
            {
                throw new InternalExpressionException("Ticket radius should be between 1 and 32 chunks");
            }
            // due to types we will wing it:
            ChunkPos target = ChunkPos.containing(pos);
            if (ticket == TicketType.PORTAL) // portal
            {
                cc.level().getChunkSource().addTicketWithRadius(TicketType.PORTAL, target, radius);
            }
            else if (ticket == TicketType.ENDER_PEARL) // post teleport
            {
                cc.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, target, radius);
            }
            else
            {
                cc.level().getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, target, radius);
            }
            return new NumericValue(ticket.timeout());
        });

        expression.addContextFunction("sample_noise", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.isEmpty())
            {
                return ListValue.wrap(cc.registry(Registries.DENSITY_FUNCTION).keySet().stream().map(ValueConversions::of));
            }
            ServerLevel level = cc.level();
            BlockArgument locator = BlockArgument.findIn(cc, lv, 0);
            BlockPos pos = locator.block.getPos();
            String[] densityFunctionQueries = lv.stream().skip(locator.offset).map(Value::getString).toArray(String[]::new);
            if (densityFunctionQueries.length == 0)
            {
                return ListValue.wrap(cc.registry(Registries.DENSITY_FUNCTION).keySet().stream().map(ValueConversions::of));
            }
            return densityFunctionQueries.length == 1
                    ? NumericValue.of(sampleNoise(level, densityFunctionQueries[0], pos))
                    : ListValue.wrap(Arrays.stream(densityFunctionQueries).map(s -> NumericValue.of(sampleNoise(level, s, pos))));
        });
    }

    public static double sampleNoise(ServerLevel level, String what, BlockPos pos)
    {
        DensityFunction function = densityFunction(level, what);
        // a generator without noise (flat, debug) has nothing to sample: zero, as before
        return function == null ? 0 : level.getChunkSource().randomState().sampleBlockValueUncached(function, pos.getX(), pos.getY(), pos.getZ());
    }

    /** the router of the generator of this very level, so the nether samples the nether */
    @Nullable
    private static DensityFunction densityFunction(ServerLevel level, String what)
    {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            return null;
        }
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseRouter router = settings.noiseRouter();
        Aquifer.Config aquifers = settings.aquifers().orElse(null);
        DensityFunction routed = switch (what)
        {
            case "temperature" -> router.temperature();
            case "vegetation" -> router.vegetation();
            case "continents" -> router.continents();
            case "erosion" -> router.erosion();
            case "depth" -> router.depth();
            case "ridges" -> router.ridges();
            case "chunk_surface_level" -> router.chunkSurfaceLevel();
            case "final_density" -> router.finalDensity();
            case "barrier_noise" -> aquifers == null ? null : aquifers.barrierNoise();
            case "fluid_level_floodedness_noise" -> aquifers == null ? null : aquifers.fluidLevelFloodednessNoise();
            case "fluid_level_spread_noise" -> aquifers == null ? null : aquifers.fluidLevelSpreadNoise();
            case "lava_noise" -> aquifers == null ? null : aquifers.lavaNoise();
            default -> level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION).getValue(InputValidator.identifierOf(what));
        };
        if (routed == null && !AQUIFER_NOISES.contains(what))
        {
            throw new InternalExpressionException("Density function '" + what + "' is not defined in the registries");
        }
        return routed;
    }

    /** a level without aquifers simply has none of these: zero, not an error */
    private static final Set<String> AQUIFER_NOISES = Set.of("barrier_noise", "fluid_level_floodedness_noise", "fluid_level_spread_noise", "lava_noise");
}
