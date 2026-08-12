package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import com.gnottero.blossom.client.BlossomClient;

/**
 * Fa scorrere il tempo delle forme sul client, e le butta quando si cambia mondo.
 *
 * <p>Il tempo si conta qui e non sul server: dalla rete arriva una durata, non un istante, quindi
 * l'unico orologio che conta è questo. Uscendo da un mondo si azzera tutto — le forme appartengono
 * alla partita in cui sono state disegnate, e tenerle vorrebbe dire vederle riapparire altrove.
 */
@Mixin(Minecraft.class)
public abstract class Minecraft_blossomClientTickMixin
{
    @Inject(method = "tick", at = @At("RETURN"))
    private void blossom$onClientTick(CallbackInfo ci)
    {
        BlossomClient.shapes().onTick();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void blossom$onLevelChange(ClientLevel level, CallbackInfo ci)
    {
        BlossomClient.shapes().clear();
    }
}
