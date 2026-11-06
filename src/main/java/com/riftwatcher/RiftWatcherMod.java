package com.riftwatcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RiftWatcherMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("riftwatcher");

    @Override
    public void onInitialize() {
        RiftConfig.load();
        ModSounds.init();
        ModEntities.init();
        Haunting.init();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> WatcherCommand.register(dispatcher));
        LOGGER.info("Something is watching.");
    }
}
