package com.gnottero.blossom.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.value.EntityValue;
import blossom.value.StringValue;
import blossom.value.Value;

/** Genera gli eventi di sessione: connessione, uscita, respawn e chat. */
@Mixin(PlayerList.class)
public abstract class PlayerList_blossomEventsMixin
{
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void blossom$onJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci)
    {
        // il saluto va mandato comunque, anche se nessuna app ascolta la connessione: e' cosi'
        // che si scopre chi ha il client, molto prima che qualcuno provi a disegnare
        com.gnottero.blossom.net.ServerNetwork.onPlayerJoin(player);
        Event.PLAYER_CONNECTS.fireForPlayer(player, () -> List.of(new EntityValue(player)));
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void blossom$onLeave(ServerPlayer player, CallbackInfo ci)
    {
        com.gnottero.blossom.net.ServerNetwork.onPlayerLeave(player);
        com.gnottero.blossom.BlossomMod.hud().onPlayerLeave(player);
        Event.PLAYER_DISCONNECTS.fireForPlayer(player,
                () -> List.of(new EntityValue(player), StringValue.of("disconnected")));
    }

    @Inject(method = "respawn", at = @At("HEAD"))
    private void blossom$onRespawn(ServerPlayer player, boolean alive, Entity.RemovalReason reason,
            CallbackInfoReturnable<ServerPlayer> cir)
    {
        Event.PLAYER_RESPAWNS.fireForPlayer(player, () -> List.of(new EntityValue(player)));
    }

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"), cancellable = true)
    private void blossom$onChat(PlayerChatMessage message, ServerPlayer player, ChatType.Bound params, CallbackInfo ci)
    {
        if (Event.PLAYER_MESSAGE.isListened())
        {
            List<Value> arguments = List.of(new EntityValue(player), StringValue.of(message.signedContent()));
            if (Event.PLAYER_MESSAGE.fireForPlayer(player, () -> arguments))
            {
                ci.cancel();
            }
        }
    }
}
