package com.riftwatcher.client;

import com.riftwatcher.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class RiftWatcherClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.RIFT_WATCHER, RiftWatcherRenderer::new);
    }
}
