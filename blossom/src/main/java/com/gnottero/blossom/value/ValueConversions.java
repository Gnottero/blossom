package com.gnottero.blossom.value;

import blossom.value.BooleanValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

import blossom.exception.InternalExpressionException;
import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import com.gnottero.blossom.util.Colors;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class ValueConversions
{
    public static Value of(BlockPos pos)
    {
        return ListValue.of(new NumericValue(pos.getX()), new NumericValue(pos.getY()), new NumericValue(pos.getZ()));
    }

    public static Value of(Vec3 vec)
    {
        return ListValue.of(new NumericValue(vec.x), new NumericValue(vec.y), new NumericValue(vec.z));
    }

    public static Value of(ColumnPos cpos)
    {
        return ListValue.of(new NumericValue(cpos.x()), new NumericValue(cpos.z()));
    }

    public static Value of(ServerLevel world)
    {
        return of(world.dimension().identifier());
    }

    public static Value of(MapColor color)
    {
        return ListValue.of(StringValue.of(Colors.mapColourName.get(color)), ofRGB(color.col));
    }

    public static <T extends Number & Comparable<T>> Value of(MinMaxBounds<T> range)
    {
        return ListValue.of(
                range.min().map(NumericValue::of).orElse(Value.NULL),
                range.max().map(NumericValue::of).orElse(Value.NULL)
        );
    }

    public static Value of(ItemStack stack, RegistryAccess regs)
    {
        return stack == null || stack.isEmpty() ? Value.NULL : ListValue.of(
                of(stack.getItem(), regs),
                new NumericValue(stack.getCount()),
                NBTSerializableValue.fromStack(stack, regs)
        );
    }

    public static Value of(Item item, RegistryAccess regs)
    {
        return of(regs.lookupOrThrow(Registries.ITEM).getKey(item));
    }

    public static Value of(Objective objective)
    {
        return ListValue.of(
                StringValue.of(objective.getName()),
                StringValue.of(objective.getCriteria().getName())
        );
    }


    public static Value of(ObjectiveCriteria criteria)
    {
        return ListValue.of(
                StringValue.of(criteria.getName()),
                BooleanValue.of(criteria.isReadOnly())
        );
    }


    public static Value of(ParticleOptions particle, RegistryAccess regs)
    {
        String repr = ParticleTypes.CODEC.encodeStart(regs.createSerializationContext(NbtOps.INSTANCE), particle).toString();
        return StringValue.of(repr.startsWith("minecraft:") ? repr.substring(10) : repr);
    }

    /**
     * A slot of a vanilla slot argument ({@code weapon.offhand}, {@code enderchest.3}) as
     * {@code [inventory, index]}: the inventory is {@code null} for the default one of whoever is
     * targeted, and the index is the one {@code core.mc.inventory} uses for it.
     */
    public static Value ofVanillaSlotResult(int slot)
    {
        if (slot >= 0 && slot < 54)
        {
            return ListValue.of(Value.NULL, new NumericValue(slot)); // a block, or a player's inventory
        }
        if (slot >= 200 && slot < 227)
        {
            return ListValue.of(StringValue.of("enderchest"), new NumericValue(slot - 200));
        }
        if (slot >= 300 && slot < 308)
        {
            return ListValue.of(Value.NULL, new NumericValue(slot - 300)); // a villager
        }
        if (slot >= 500 && slot < 515)
        {
            return ListValue.of(Value.NULL, new NumericValue(slot - 500 + 2)); // after saddle and armour
        }
        return switch (slot)
        {
            case 98 -> ListValue.of(StringValue.of("equipment"), new NumericValue(0));
            case 99 -> ListValue.of(StringValue.of("equipment"), new NumericValue(5));
            case 100, 101, 102, 103 -> ListValue.of(StringValue.of("equipment"), new NumericValue(slot - 99));
            case 400 -> ListValue.of(Value.NULL, new NumericValue(0)); // saddle
            case 401 -> ListValue.of(Value.NULL, new NumericValue(1)); // body armour
            default -> ListValue.of(Value.NULL, new NumericValue(slot));
        };
    }

    /** A block predicate as {@code [block, tag, properties, nbt]}: block or tag is null, never both. */
    public static Value ofBlockPredicate(RegistryAccess registryAccess,
            java.util.function.Predicate<net.minecraft.world.level.block.state.pattern.BlockInWorld> predicate)
    {
        com.gnottero.blossom.access.Vanilla.BlockPredicatePayload payload =
                com.gnottero.blossom.access.Vanilla.BlockPredicatePayload.of(predicate);
        net.minecraft.core.Registry<net.minecraft.world.level.block.Block> blocks =
                registryAccess.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        return ListValue.of(
                payload.state() == null ? Value.NULL : of(blocks.getKey(payload.state().getBlock())),
                payload.tagKey() == null ? Value.NULL : of(payload.tagKey()),
                MapValue.wrap(payload.properties()),
                payload.tag() == null ? Value.NULL : new NBTSerializableValue(payload.tag()));
    }

    public static Value ofRGB(int value)
    {
        return new NumericValue(value * 256 + 255);
    }

    /** the same 0xRRGGBBAA as {@link #ofRGB(int)}, for the colours the game now keeps as vectors */
    public static Value ofRGB(Vector3fc value)
    {
        return ofRGB(ARGB.colorFromVector3f(value) & 0xFFFFFF);
    }

    public static Level dimFromValue(Value dimensionValue, MinecraftServer server)
    {
        if (dimensionValue instanceof EntityValue)
        {
            return ((EntityValue) dimensionValue).getEntity().level();
        }
        else if (dimensionValue instanceof BlockValue bv)
        {
            if (bv.getWorld() != null)
            {
                return bv.getWorld();
            }
            else
            {
                throw new InternalExpressionException("dimension argument accepts only world-localized block arguments");
            }
        }
        else
        {
            String dimString = dimensionValue.getString().toLowerCase(Locale.ROOT);
            return switch (dimString) {
                case "nether", "the_nether" -> server.getLevel(Level.NETHER);
                case "end", "the_end" -> server.getLevel(Level.END);
                case "overworld", "over_world" -> server.getLevel(Level.OVERWORLD);
                default -> {
                    ResourceKey<Level> dim = null;
                    Identifier id = Identifier.parse(dimString);
                    // not using RegistryKey.of since that one creates on check
                    for (ResourceKey<Level> world : (server.levelKeys()))
                    {
                        if (id.equals(world.identifier()))
                        {
                            dim = world;
                            break;
                        }
                    }
                    if (dim == null)
                    {
                        throw new ThrowStatement(dimString, Throwables.UNKNOWN_DIMENSION);
                    }
                    yield server.getLevel(dim);
                }
            };
        }
    }

    public static Value of(ResourceKey<?> dim)
    {
        return of(dim.identifier());
    }

    public static Value of(TagKey<?> tagKey)
    {
        return of(tagKey.location());
    }

    public static Value of(HolderSet.Named<?> tagKey)
    {
        return of(tagKey.key().location());
    }

    public static Value of(@Nullable Identifier id)
    {
        if (id == null) // should be Value.NULL
        {
            return Value.NULL;
        }
        return new StringValue(simplify(id));
    }

    public static String simplify(Identifier id)
    {
        if (id == null) // should be Value.NULL
        {
            return "";
        }
        if (id.getNamespace().equals("minecraft"))
        {
            return id.getPath();
        }
        return id.toString();
    }

    public static Value of(GlobalPos pos)
    {
        return ListValue.of(
                ValueConversions.of(pos.dimension()),
                ValueConversions.of(pos.pos())
        );
    }

    public static Value fromPath(ServerLevel world, Path path)
    {
        List<Value> nodes = new ArrayList<>();
        for (int i = 0, len = path.getNodeCount(); i < len; i++)
        {
            Node node = path.getNode(i);
            nodes.add(ListValue.of(
                    new BlockValue(null, world, node.asBlockPos()),
                    new StringValue(node.type.name().toLowerCase(Locale.ROOT)),
                    new NumericValue(node.costMalus),
                    BooleanValue.of(node.closed)
            ));
        }
        return ListValue.wrap(nodes);
    }

    public static Value fromTimedMemory(Entity e, long expiry, Object v)
    {
        Value ret = fromEntityMemory(e, v);
        return ret.isNull() || expiry == Long.MAX_VALUE ? ret : ListValue.of(ret, new NumericValue(expiry));
    }

    private static Value fromEntityMemory(Entity e, Object v)
    {
        if (v instanceof GlobalPos pos)
        {
            return of(pos);
        }
        if (v instanceof final Entity entity)
        {
            return new EntityValue(entity);
        }
        if (v instanceof final BlockPos pos)
        {
            return new BlockValue(null, (ServerLevel) e.level(), pos);
        }
        if (v instanceof final Number number)
        {
            return new NumericValue(number.doubleValue());
        }
        if (v instanceof final Boolean bool)
        {
            return BooleanValue.of(bool);
        }
        if (v instanceof final UUID uuid)
        {
            return ofUUID((ServerLevel) e.level(), uuid);
        }
        if (v instanceof final DamageSource source)
        {
            return ListValue.of(
                    new StringValue(source.getMsgId()),
                    source.getEntity() == null ? Value.NULL : new EntityValue(source.getEntity())
            );
        }
        if (v instanceof final Path path)
        {
            return fromPath((ServerLevel) e.level(), path);
        }
        if (v instanceof final PositionTracker tracker)
        {
            return new BlockValue(null, (ServerLevel) e.level(), tracker.currentBlockPosition());
        }
        if (v instanceof final WalkTarget target)
        {
            return ListValue.of(
                    new BlockValue(null, (ServerLevel) e.level(), target.getTarget().currentBlockPosition()),
                    new NumericValue(target.getSpeedModifier()),
                    new NumericValue(target.getCloseEnoughDist())
            );
        }
        if (v instanceof final NearestVisibleLivingEntities nvle)
        {
            v = StreamSupport.stream(nvle.findAll(entity -> true).spliterator(), false).toList();
        }
        if (v instanceof final Set<?> set)
        {
            v = new ArrayList<>(set);
        }
        if (v instanceof final List<?> l)
        {
            if (l.isEmpty())
            {
                return ListValue.of();
            }
            Object el = l.get(0);
            if (el instanceof final Entity entity)
            {
                return ListValue.wrap(l.stream().map(o -> new EntityValue(entity)));
            }
            if (el instanceof final GlobalPos pos)
            {
                return ListValue.wrap(l.stream().map(o -> of(pos)));
            }
        }
        return Value.NULL;
    }

    private static Value ofUUID(ServerLevel entityWorld, UUID uuid)
    {
        Entity current = entityWorld.getEntity(uuid);
        return ListValue.of(
                current == null ? Value.NULL : new EntityValue(current),
                new StringValue(uuid.toString())
        );
    }

    public static Value of(AABB box)
    {
        return ListValue.of(
                ListValue.fromTriple(box.minX, box.minY, box.minZ),
                ListValue.fromTriple(box.maxX, box.maxY, box.maxZ)
        );
    }

    public static Value of(BoundingBox box)
    {
        return ListValue.of(
                ListValue.fromTriple(box.minX(), box.minY(), box.minZ()),
                ListValue.fromTriple(box.maxX(), box.maxY(), box.maxZ())
        );
    }

    public static Value of(StructureStart structure, RegistryAccess regs)
    {
        if (structure == null)
        {
            return Value.NULL;
        }
        BoundingBox boundingBox = structure.getBoundingBox();
        if (boundingBox.maxX() < boundingBox.minX() || boundingBox.maxY() < boundingBox.minY() || boundingBox.maxZ() < boundingBox.minZ())
        {
            return Value.NULL;
        }
        Map<Value, Value> ret = new HashMap<>();
        ret.put(new StringValue("box"), of(boundingBox));
        List<Value> pieces = new ArrayList<>();
        for (StructurePiece piece : structure.getPieces())
        {
            BoundingBox box = piece.getBoundingBox();
            if (box.maxX() >= box.minX() && box.maxY() >= box.minY() && box.maxZ() >= box.minZ())
            {
                pieces.add(ListValue.of(
                        NBTSerializableValue.nameFromRegistryId(regs.lookupOrThrow(Registries.STRUCTURE_PIECE).getKey(piece.getType())),
                        (piece.getOrientation() == null) ? Value.NULL : new StringValue(piece.getOrientation().getName()),
                        ListValue.fromTriple(box.minX(), box.minY(), box.minZ()),
                        ListValue.fromTriple(box.maxX(), box.maxY(), box.maxZ())
                ));
            }
        }
        ret.put(new StringValue("pieces"), ListValue.wrap(pieces));
        return MapValue.wrap(ret);
    }

    public static Value of(final ScoreHolder scoreHolder)
    {
        return FormattedTextValue.of(scoreHolder.getFeedbackDisplayName());
    }

    public static Value fromProperty(BlockState state, Property<?> p)
    {
        Comparable<?> object = state.getValue(p);
        if (object instanceof Boolean || object instanceof Number)
        {
            return StringValue.of(object.toString());
        }
        if (object instanceof final StringRepresentable stringRepresentable)
        {
            return StringValue.of(stringRepresentable.getSerializedName());
        }
        throw new InternalExpressionException("Unknown property type: " + p.getName());
    }

    public static ItemStack getItemStackFromValue(Value value, boolean withCount, RegistryAccess regs)
    {
        if (value.isNull())
        {
            return ItemStack.EMPTY;
        }
        String name;
        int count = 1;
        CompoundTag nbtTag = null;
        if (value instanceof ListValue list)
        {
            if (list.length() != 3)
            {
                throw new ThrowStatement("item definition from list of size " + list.length(), Throwables.UNKNOWN_ITEM);
            }
            List<Value> items = list.getItems();
            name = items.get(0).getString();
            if (withCount)
            {
                count = NumericValue.asNumber(items.get(1)).getInt();
            }
            Value nbtValue = items.get(2);
            if (!nbtValue.isNull())
            {
                nbtTag = ((NBTSerializableValue) NBTSerializableValue.fromValue(nbtValue)).getCompoundTag();
            }
        }
        else
        {
            name = value.getString();
        }
        ItemStack itemInput = NBTSerializableValue.parseItem(name, nbtTag, regs);
        itemInput.setCount(count);
        return itemInput;
    }

    public static Value guess(ServerLevel serverWorld, Object o)
    {
        if (o == null)
        {
            return Value.NULL;
        }
        if (o instanceof final List<?> list)
        {
            return ListValue.wrap(list.stream().map(oo -> guess(serverWorld, oo)));
        }
        if (o instanceof final BlockPos pos)
        {
            return new BlockValue(null, serverWorld, pos);
        }
        if (o instanceof final Entity e)
        {
            return EntityValue.of(e);
        }
        if (o instanceof final Vec3 vec3)
        {
            return of(vec3);
        }
        if (o instanceof final Vec3i vec3i)
        {
            return of(new BlockPos(vec3i.getX(), vec3i.getY(), vec3i.getZ()));
        }
        if (o instanceof final AABB aabb)
        {
            return of(aabb);
        }
        if (o instanceof final BoundingBox bb)
        {
            return of(bb);
        }
        if (o instanceof final ItemStack itemStack)
        {
            return of(itemStack, serverWorld.registryAccess());
        }
        if (o instanceof final Boolean bool)
        {
            return BooleanValue.of(bool);
        }
        if (o instanceof final Number number)
        {
            return NumericValue.of(number);
        }
        if (o instanceof final Identifier resourceLocation)
        {
            return of(resourceLocation);
        }
        return StringValue.of(o.toString());
    }
}
