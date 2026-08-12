package com.gnottero.blossom.client;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Quello che un frame già preparato sa fare in più, per noi: eseguire la fase di
 * {@link AfterCloudsSubmits}.
 */
@Environment(EnvType.CLIENT)
public interface AfterCloudsFrame
{
    void blossom$executeAfterClouds(RenderPass renderPass);
}
