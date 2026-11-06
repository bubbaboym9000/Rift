package com.riftwatcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Plain Gson config, written to config/riftwatcher.json on first launch. */
public final class RiftConfig {
    private static RiftConfig instance = new RiftConfig();

    /** Master switch. */
    public boolean enabled = true;
    /** false = only the ambient scares and Dread; no Watcher entity is ever spawned. */
    public boolean watcherEnabled = true;
    /** Creative players are left alone unless this is true (useful for testing). */
    public boolean affectCreative = false;
    /** At full terror the Watcher snuffs out torches near you (they drop as items). */
    public boolean snuffTorches = true;
    /** Enables the /riftwatcher test commands. Turn off on public servers. */
    public boolean debugCommands = true;
    /** Damage dealt when the Watcher catches you looking away (2 per heart). */
    public float strikeDamage = 8.0F;
    /** Multiplies how fast Dread rises. 0.5 = gentler, 2.0 = much worse. */
    public double dreadGainMultiplier = 1.0D;
    public int stage1Dread = 150;
    public int stage2Dread = 350;
    public int stage3Dread = 650;

    public static RiftConfig get() {
        return instance;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("riftwatcher.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    RiftConfig loaded = gson.fromJson(reader, RiftConfig.class);
                    if (loaded != null) {
                        instance = loaded;
                    }
                }
            }
            // Rewrite so any options added in newer versions show up in the file.
            Files.writeString(file, gson.toJson(instance));
        } catch (IOException | RuntimeException e) {
            RiftWatcherMod.LOGGER.warn("Could not read or write riftwatcher.json, using defaults", e);
        }
    }
}
