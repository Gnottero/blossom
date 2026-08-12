package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.util.Mth;

import com.gnottero.blossom.access.Access;

/** Quale blocco un giocatore sta rompendo, e a che punto e' la crepa. */
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
        lastSentState = Mth.clamp(progress, -1, 10);
        level.destroyBlockProgress(-player.getId(), destroyPos, lastSentState);
    }
}
