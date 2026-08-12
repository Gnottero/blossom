package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * Genera {@code entity_loads}: un'entità è comparsa nel mondo.
 *
 * <p>L'aggancio è {@code onCreated}, cioè il punto in cui l'entità entra nella memoria del livello.
 * Copre tutti e tre i modi in cui una compare — riletta dal disco insieme al suo chunk, generata dal
 * mondo, o evocata da qualcuno — e sono la stessa cosa per chi ascolta: l'entità prima non c'era e
 * adesso c'è.
 *
 * <p>Non {@code onTrackingStart}, che sembrerebbe il candidato naturale: quello scatta quando
 * l'entità diventa visibile a qualcuno, quindi su un server senza giocatori vicini non scatta mai.
 * Verificato in gioco: con {@code onTrackingStart} un'entità evocata non generava nessun evento.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerLevelEntityCallbacks_blossomEventsMixin
{
    @Shadow
    @Final
    ServerLevel this$0;

    @Inject(method = "onCreated(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 1)
    private void blossom$onEntityLoads(Entity entity, CallbackInfo ci)
    {
        if (Event.ENTITY_LOADS.isListened())
        {
            Fire.entityLoads(this$0, entity);
        }
    }
}
