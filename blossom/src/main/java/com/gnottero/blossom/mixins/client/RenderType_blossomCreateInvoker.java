package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Apre {@code RenderType.create}, che il gioco tiene visibile solo dentro il suo pacchetto.
 *
 * <p>Non c'è nessun altro modo di costruire un modo di disegnare che non sia già nell'elenco di
 * vanilla, e a noi ne serve uno: vedi {@code BlossomRenderTypes} per il perché.
 */
@Mixin(RenderType.class)
public interface RenderType_blossomCreateInvoker
{
    @Invoker("create")
    static RenderType blossom$create(String name, RenderSetup setup)
    {
        throw new AssertionError();
    }
}
