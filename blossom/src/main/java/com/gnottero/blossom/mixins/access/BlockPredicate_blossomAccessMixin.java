package com.gnottero.blossom.mixins.access;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.StringValue;
import blossom.value.Value;

/** Legge il predicato scritto in un argomento comando ({@code stone[facing=north]}). */
@Mixin(targets = "net.minecraft.commands.arguments.blocks.BlockPredicateArgument$BlockPredicate")
public class BlockPredicate_blossomAccessMixin implements Access.BlockPredicate
{
    @Shadow
    @Final
    private BlockState state;

    @Shadow
    @Final
    private CompoundTag nbt;

    @Shadow
    @Final
    private Set<Property<?>> properties;

    @Override
    public BlockState blossom$state()
    {
        return state;
    }

    @Override
    public TagKey<Block> blossom$tagKey()
    {
        return null;
    }

    @Override
    public Map<Value, Value> blossom$properties()
    {
        return properties.stream().collect(Collectors.toMap(
                property -> StringValue.of(property.getName()),
                property -> ValueConversions.fromProperty(state, property),
                (a, b) -> b));
    }

    @Override
    public CompoundTag blossom$dataTag()
    {
        return nbt;
    }
}
