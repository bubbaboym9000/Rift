package com.riftwatcher;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.EnderMan;

public final class ModEntities {
    public static final EntityType<RiftWatcherEntity> RIFT_WATCHER = registerWatcher();

    private ModEntities() {
    }

    private static EntityType<RiftWatcherEntity> registerWatcher() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Ids.of("rift_watcher"));
        EntityType<RiftWatcherEntity> type = EntityType.Builder
                .of(RiftWatcherEntity::new, MobCategory.MONSTER)
                .sized(0.6F, 2.9F)
                .eyeHeight(2.55F)
                .clientTrackingRange(12)
                .updateInterval(2)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    public static void init() {
        // The Watcher is built on the Enderman body, so it borrows its attribute set (tall, fast, 40 hp).
        FabricDefaultAttributeRegistry.register(RIFT_WATCHER, EnderMan.createAttributes());
    }
}
