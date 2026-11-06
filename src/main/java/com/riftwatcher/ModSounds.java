package com.riftwatcher;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent HEARTBEAT = register("heartbeat");
    public static final SoundEvent DRONE = register("drone");
    public static final SoundEvent RIFT_TEAR = register("rift_tear");
    public static final SoundEvent STARE = register("stare");
    public static final SoundEvent SCREAM = register("scream");
    public static final SoundEvent WHISPER = register("whisper");
    public static final SoundEvent BREATH = register("breath");
    public static final SoundEvent MUSIC_BOX = register("music_box");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        var id = Ids.of(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Touching this class registers the sounds; call once from the mod initializer. */
    public static void init() {
    }
}
