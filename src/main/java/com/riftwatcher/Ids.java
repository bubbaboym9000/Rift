package com.riftwatcher;

// Minecraft 1.21.11 calls this class `Identifier`. If your build says it cannot find it,
// your mappings still use the older name: swap this import and the type below for
// `net.minecraft.resources.ResourceLocation` (RiftWatcherRenderer needs the same swap).
import net.minecraft.resources.Identifier;

/** The only place (besides the renderer) that names the resource-id type, so a rename is a two-file fix. */
public final class Ids {
    public static final String MOD_ID = "riftwatcher";

    public static final Identifier WATCHER_TEXTURE = of("textures/entity/rift_watcher.png");

    private Ids() {
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
