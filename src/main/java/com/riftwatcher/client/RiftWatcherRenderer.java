package com.riftwatcher.client;

import com.riftwatcher.Ids;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
// 1.21.11 name. If your build cannot find it, use net.minecraft.resources.ResourceLocation (see Ids.java).
import net.minecraft.resources.Identifier;

/**
 * Draws the Watcher with the vanilla Enderman model (long limbs, dropping jaw, glowing eye layer)
 * wearing the shattered-rift skin. The vanilla eye layer is emissive, so in a dark cave or on a
 * night plain you see two violet points staring at you long before you can make out the body.
 */
public class RiftWatcherRenderer extends EndermanRenderer {
    public RiftWatcherRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return Ids.WATCHER_TEXTURE;
    }
}
