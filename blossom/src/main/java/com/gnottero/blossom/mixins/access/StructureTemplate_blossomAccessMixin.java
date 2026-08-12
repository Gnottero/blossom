package com.gnottero.blossom.mixins.access;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Le palette di un template, che vanilla tiene private (voce ST-5 dell'inventario).
 *
 * <p>Servono a {@code structure_matches}: un template puo' avere piu' varianti dello stesso volume,
 * e "combacia" vuol dire che ne combacia almeno una. Senza accesso alle palette l'unico modo di
 * confrontare sarebbe piazzare la struttura da qualche parte e guardare cosa esce, che modifica il
 * mondo per rispondere a una domanda.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplate_blossomAccessMixin
{
    @Accessor("palettes")
    List<StructureTemplate.Palette> blossom$palettes();
}
