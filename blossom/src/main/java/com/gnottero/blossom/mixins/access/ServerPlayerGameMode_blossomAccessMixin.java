package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

import com.gnottero.blossom.access.Access;

/** Which block a player is breaking, and how far the crack has progressed. */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameMode_blossomAccessMixin implements Access.GameMode
{
    @Shadow
    public ServerPlayer player;

    @Shadow
    public ServerLevel level;

    @Shadow
    private boolean isDestroyingBlock;

    @Shadow
    private BlockPos destroyPos;

    @Shadow
    private int lastSentState;

    @Override
    public BlockPos blossom$breakingBlock()
    {
        return isDestroyingBlock ? destroyPos : null;
    }

    @Override
    public int blossom$breakingProgress()
    {
        return isDestroyingBlock ? lastSentState : -1;
    }

    @Override
    public void blossom$setBreakingProgress(int progress)
    {
        if (destroyPos == null)
        {
            return; // no block under the cursor: there's nothing to draw the crack on
        }
        lastSentState = Math.clamp(progress, -1, 10);
        // The id is the player's id with the sign flipped: this keeps the animation separate from
        // the one the game sends on its own, which would otherwise overwrite it the next tick
        level.destroyBlockProgress(-player.getId(), destroyPos, lastSentState);
    }
}
