package com.gnottero.blossom.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * Fires {@code piston_extends} and {@code piston_retracts}, carried over from the fork (entries BE-1
 * and BE-2).
 *
 * <p>Both arrive <b>before</b> the blocks move and pass the list of the ones that would move, so a
 * handler can decide knowing what is about to happen. Returning a true value cancels the movement.
 *
 * <p>The two hooks are asymmetric because the game is: on extension the resolver has already
 * computed the blocks and it is enough to read it, on retraction the decision comes before the
 * resolver exists, so the list has to be rebuilt, see {@link #blossom$previewPulledBlocks}.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlock_blossomEventMixin extends DirectionalBlock
{
    @Shadow
    @Final
    private boolean isSticky;

    protected PistonBaseBlock_blossomEventMixin(Properties settings)
    {
        super(settings);
    }

    @Inject(method = "moveBlocks", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;",
            shift = At.Shift.BEFORE))
    private void blossom$onPistonExtend(Level level, BlockPos pistonPos, Direction direction, boolean extending,
            CallbackInfoReturnable<Boolean> cir, BlockPos armPos, PistonStructureResolver resolver)
    {
        if (!extending || !Event.PISTON_EXTENDS.isListened() || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }
        if (Fire.piston(Event.PISTON_EXTENDS, serverLevel, pistonPos, direction, resolver.getToPush()))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "triggerEvent", cancellable = true, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;",
            ordinal = 0,
            shift = At.Shift.BEFORE))
    private void blossom$onPistonRetract(BlockState state, Level level, BlockPos pos, int b0, int b1,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (!Event.PISTON_RETRACTS.isListened() || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }
        Direction direction = state.getValue(FACING);
        List<BlockPos> toMove = blossom$willPullBlocks(level, pos, direction, b0)
                ? blossom$previewPulledBlocks(level, pos, direction)
                : List.of();
        if (Fire.piston(Event.PISTON_RETRACTS, serverLevel, pos, direction, toMove))
        {
            cir.setReturnValue(false);
        }
    }

    /**
     * Mirrors the condition under which {@code triggerEvent}, on the retraction branch, really ends
     * up calling {@code moveBlocks}: sticky piston, retract only signal, attached block non empty
     * and pullable, and no extension already in progress.
     *
     * <p>Without this check the event would fire on retractions that move nothing too, and the block
     * list would be a lie.
     */
    @Unique
    private boolean blossom$willPullBlocks(Level level, BlockPos pos, Direction direction, int b0)
    {
        if (!isSticky || b0 != 1)
        {
            return false;
        }
        BlockPos twoPos = pos.relative(direction, 2);
        BlockState movingState = level.getBlockState(twoPos);
        if (movingState.is(Blocks.MOVING_PISTON)
                && level.getBlockEntity(twoPos) instanceof PistonMovingBlockEntity entity
                && entity.getDirection() == direction && entity.isExtending())
        {
            return false;
        }
        return !movingState.isAir()
                && PistonBaseBlock.isPushable(movingState, level, twoPos, direction.getOpposite(), false, direction)
                && (movingState.getPistonPushReaction() == PushReaction.PUSH_PULL
                        || movingState.is(Blocks.PISTON) || movingState.is(Blocks.STICKY_PISTON));
    }

    /**
     * Computes the blocks the retraction would pull, before the game does it.
     *
     * <p>The piston head would obstruct the resolver: {@code moveBlocks} removes it before
     * resolving, and the same is done here, with the same silent flags, putting it straight back.
     * It is the only way to hand the handler a real list instead of an empty one.
     */
    @Unique
    private List<BlockPos> blossom$previewPulledBlocks(Level level, BlockPos pos, Direction direction)
    {
        BlockPos armPos = pos.relative(direction);
        BlockState armState = level.getBlockState(armPos);
        boolean headSwapped = armState.is(Blocks.PISTON_HEAD);
        if (headSwapped)
        {
            level.setBlock(armPos, Blocks.AIR.defaultBlockState(), 276);
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, direction, false);
        List<BlockPos> toMove = resolver.resolve() ? List.copyOf(resolver.getToPush()) : List.of();
        if (headSwapped)
        {
            level.setBlock(armPos, armState, 276);
        }
        return toMove;
    }
}
