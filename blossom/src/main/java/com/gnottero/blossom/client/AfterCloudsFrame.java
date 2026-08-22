package com.gnottero.blossom.client;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The extra thing an already-prepared frame can do for us: run the
 * {@link AfterCloudsSubmits} phase.
 */
@Environment(EnvType.CLIENT)
public interface AfterCloudsFrame
{
    void blossom$executeAfterClouds(RenderPass renderPass);
}
