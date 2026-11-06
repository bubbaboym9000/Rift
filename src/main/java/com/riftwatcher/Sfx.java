package com.riftwatcher;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Sounds that only ONE player hears. Everything in the mod goes through here so
 * other players on a server are not dragged into somebody else's haunting.
 */
public final class Sfx {
    private Sfx() {
    }

    /** A positional sound: it really comes from that spot in the world. */
    public static void at(ServerPlayer player, SoundEvent sound, SoundSource source,
                          double x, double y, double z, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        player.connection.send(new ClientboundSoundPacket(
                holder, source, x, y, z, volume, pitch, player.getRandom().nextLong()));
    }

    public static void at(ServerPlayer player, SoundEvent sound, SoundSource source,
                          Vec3 pos, float volume, float pitch) {
        at(player, sound, source, pos.x, pos.y, pos.z, volume, pitch);
    }

    /** A sound with no direction, as if it is inside the player's own head. */
    public static void inHead(ServerPlayer player, SoundEvent sound, SoundSource source,
                              float volume, float pitch) {
        player.playNotifySound(sound, source, volume, pitch);
    }
}
