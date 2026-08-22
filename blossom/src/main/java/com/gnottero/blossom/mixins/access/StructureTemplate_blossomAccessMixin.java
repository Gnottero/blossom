package com.gnottero.blossom.mixins.access;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A template's palettes, which vanilla keeps private (inventory entry ST-5).
 *
 * <p>Needed by {@code structure_matches}: a template can have several variants of the same volume,
 * and "matches" means at least one of them matches. Without access to the palettes, the only way to
 * compare would be to place the structure somewhere and see what comes out, which modifies the
 * world just to answer a question.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplate_blossomAccessMixin
{
    @Accessor("palettes")
    List<StructureTemplate.Palette> blossom$palettes();
}
