package com.riftwatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The director. It owns the hidden DREAD meter of every player, decides when something wrong happens,
 * and decides when the Watcher is allowed to show up.
 *
 * DREAD is never shown on a bar. You feel it: calm (0) -> unease (stage 1) -> dread (stage 2) -> terror (stage 3).
 *  - it rises in the dark, underground, at low health, and while the Watcher is looking at you
 *  - it falls in bright light and near other players
 *  - stage 1: the world starts making sounds it should not
 *  - stage 2: rifts open and the Watcher stands in the dark, watching
 *  - stage 3: the Watcher hunts
 */
public final class Haunting {
    private static final int MAX_DREAD = 1000;

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static final List<Delayed> DELAYED = new ArrayList<>();
    private static final List<Scar> SCARS = new ArrayList<>();
    private static MinecraftServer server;
    private static long now;

    private static final String[] WHISPERS = {
            "it can see you",
            "don't turn around",
            "you are not alone down here",
            "look behind you",
            "it is closer than the last time",
            "keep your eyes on the dark",
            "it learned how you walk",
    };

    private Haunting() {
    }

    // ------------------------------------------------------------------ state

    private static final class PlayerState {
        int dread;
        int scareCooldown = 25;
        int hauntCooldown = 20;
        int hauntLevel;
        UUID watcher;
    }

    private record Delayed(long at, Runnable task) {
    }

    private static final class Scar {
        final ServerLevel level;
        final Vec3 pos;
        int ticks;

        Scar(ServerLevel level, Vec3 pos, int ticks) {
            this.level = level;
            this.pos = pos;
            this.ticks = ticks;
        }
    }

    private enum Scare {
        FOOTSTEPS(1, 22),
        DOOR(1, 10),
        MUSIC_BOX(1, 7),
        HISS(1, 9),
        WHISPER(1, 12),
        BREATH(2, 12),
        DARK_DIP(2, 12),
        GLIMPSE(2, 10),
        TEAR(2, 11),
        ACTIONBAR(2, 5);

        final int minStage;
        final int weight;

        Scare(int minStage, int weight) {
            this.minStage = minStage;
            this.weight = weight;
        }
    }

    private static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }

    // ------------------------------------------------------------------ public API

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Haunting::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            STATES.clear();
            DELAYED.clear();
            SCARS.clear();
            server = null;
        });
    }

    public static int stageOf(int dread) {
        RiftConfig cfg = RiftConfig.get();
        if (dread >= cfg.stage3Dread) {
            return 3;
        }
        if (dread >= cfg.stage2Dread) {
            return 2;
        }
        if (dread >= cfg.stage1Dread) {
            return 1;
        }
        return 0;
    }

    public static int getDread(ServerPlayer player) {
        return state(player).dread;
    }

    public static void setDread(ServerPlayer player, int value) {
        state(player).dread = Mth.clamp(value, 0, MAX_DREAD);
    }

    public static void addDread(ServerPlayer player, int amount) {
        PlayerState st = state(player);
        st.dread = Mth.clamp(st.dread + amount, 0, MAX_DREAD);
    }

    /** Called by the Watcher when it leaves so the director can schedule its next visit. */
    public static void watcherLeft(UUID playerId, boolean escalate) {
        PlayerState st = STATES.get(playerId);
        if (st == null) {
            return;
        }
        st.watcher = null;
        if (escalate) {
            st.hauntLevel = Math.min(st.hauntLevel + 1, 6);
        }
        st.hauntCooldown = Math.max(35, 110 - st.hauntLevel * 12);
    }

    public static void later(int ticks, Runnable task) {
        DELAYED.add(new Delayed(now + Math.max(1, ticks), task));
    }

    // ------------------------------------------------------------------ main loop

    public static void tick(MinecraftServer srv) {
        server = srv;
        now++;
        RiftConfig cfg = RiftConfig.get();
        if (!cfg.enabled) {
            return;
        }
        runDelayed();
        tickScars();
        if (now % 20 == 0) {
            for (ServerPlayer player : new ArrayList<>(srv.getPlayerList().getPlayers())) {
                secondTick(player, cfg);
            }
        }
    }

    private static void runDelayed() {
        if (DELAYED.isEmpty()) {
            return;
        }
        List<Delayed> due = new ArrayList<>();
        DELAYED.removeIf(d -> {
            if (d.at() <= now) {
                due.add(d);
                return true;
            }
            return false;
        });
        for (Delayed d : due) {
            d.task().run();
        }
    }

    private static void tickScars() {
        Iterator<Scar> it = SCARS.iterator();
        while (it.hasNext()) {
            Scar scar = it.next();
            if (--scar.ticks <= 0) {
                it.remove();
                continue;
            }
            if (scar.ticks % 2 != 0) {
                continue;
            }
            // A vertical slit in the air, a little taller than a person.
            for (int k = 0; k < 9; k++) {
                scar.level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        scar.pos.x, scar.pos.y + 0.1 + k * 0.38, scar.pos.z, 1, 0.03, 0.03, 0.03, 0.02);
            }
            if (scar.ticks % 6 == 0) {
                scar.level.sendParticles(ParticleTypes.END_ROD,
                        scar.pos.x, scar.pos.y + 1.6, scar.pos.z, 1, 0.05, 1.3, 0.05, 0.0);
            }
        }
    }

    private static void secondTick(ServerPlayer player, RiftConfig cfg) {
        ServerLevel level = (ServerLevel) player.level();
        PlayerState st = state(player);

        if (!player.isAlive()) { // dying lets you wake up from it
            st.dread = 0;
            st.watcher = null;
            return;
        }
        if (player.isSpectator() || (player.isCreative() && !cfg.affectCreative)
                || level.dimension() != Level.OVERWORLD) {
            st.dread = Math.max(0, st.dread - 5);
            return;
        }

        // ---- what feeds Dread, once a second
        BlockPos pos = player.blockPosition();
        int light = level.getMaxLocalRawBrightness(pos); // night outside and caves both read as dark here
        double gain = -1.0;
        if (light <= 3) {
            gain += 4.0;
        } else if (light <= 7) {
            gain += 2.0;
        } else if (light >= 12) {
            gain -= 4.0;
        }
        if (!level.canSeeSky(pos) && player.getY() < 55.0) {
            gain += 1.0;
        }
        if (player.getHealth() <= 6.0F) {
            gain += 1.0;
        }
        if (companyNearby(level, player)) {
            gain -= 2.0;
        }
        if (gain > 0.0) {
            gain *= cfg.dreadGainMultiplier;
        }
        st.dread = Mth.clamp(st.dread + (int) Math.round(gain), 0, MAX_DREAD);

        int stage = stageOf(st.dread);
        if (st.scareCooldown > 0) {
            st.scareCooldown--;
        }
        if (st.hauntCooldown > 0) {
            st.hauntCooldown--;
        }

        // ---- keep the Watcher informed, or notice it is gone
        if (st.watcher != null) {
            if (level.getEntity(st.watcher) instanceof RiftWatcherEntity watcher && !watcher.isRemoved()) {
                watcher.setDreadStage(stage);
            } else {
                st.watcher = null;
                st.hauntCooldown = Math.max(st.hauntCooldown, 30);
            }
        }

        // ---- ambient wrongness
        if (stage >= 1 && st.scareCooldown <= 0) {
            runScare(level, player, stage);
            int base = stage == 1 ? 18 : (stage == 2 ? 11 : 6);
            st.scareCooldown = base + level.getRandom().nextInt(base);
        }

        // ---- the low note under everything
        if (stage >= 2 && (now / 20) % 7 == 0) {
            Sfx.inHead(player, ModSounds.DRONE, SoundSource.AMBIENT, 0.25F + stage * 0.2F, 1.0F);
        }

        // ---- the Watcher
        if (stage >= 2 && cfg.watcherEnabled && st.watcher == null && st.hauntCooldown <= 0
                && level.getDifficulty() != Difficulty.PEACEFUL) {
            st.hauntCooldown = summon(level, player, false, stage) ? 25 : 5;
        }
    }

    private static boolean companyNearby(ServerLevel level, ServerPlayer player) {
        for (ServerPlayer other : level.players()) {
            if (other != player && other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) < 20.0 * 20.0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the Watcher appears

    /**
     * Opens a rift 26-40 blocks away, somewhere you are facing, in the dark, with a clear line of sight to you.
     * {@code debug} (from the command) skips the dark and line-of-sight rules and spawns it close.
     */
    public static boolean summon(ServerLevel level, ServerPlayer player, boolean debug, int stage) {
        PlayerState st = state(player);
        RandomSource r = level.getRandom();
        Vec3 flat = flatLook(player);
        double base = Math.atan2(flat.z, flat.x);

        RiftWatcherEntity watcher = ModEntities.RIFT_WATCHER.create(level, EntitySpawnReason.EVENT);
        if (watcher == null) {
            return false;
        }
        for (int attempt = 0; attempt < 48; attempt++) {
            boolean needView = !debug && attempt < 32; // later attempts take any dark spot rather than give up
            double angle = base + Math.toRadians((r.nextDouble() - 0.5) * 70.0);
            double dist = debug ? 12 + r.nextInt(8) : 26 + r.nextInt(15);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos stand = findStand(level, x, z, player.blockPosition().getY());
            if (stand == null) {
                continue;
            }
            if (!debug && level.getMaxLocalRawBrightness(stand) > 7) {
                continue;
            }
            watcher.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (needView && !player.hasLineOfSight(watcher)) {
                continue;
            }
            watcher.bindTo(player, st.hauntLevel, debug, stage);
            level.addFreshEntity(watcher);
            st.watcher = watcher.getUUID();
            tearBurst(level, watcher.position());
            scar(level, watcher.position(), 60);
            Sfx.at(player, ModSounds.RIFT_TEAR, SoundSource.HOSTILE, watcher.position(), 1.6F, 0.7F);
            return true;
        }
        return false;
    }

    /** Removes every Watcher in every dimension (command + cleanup). Returns how many were removed. */
    public static int banishAll(MinecraftServer srv) {
        int count = 0;
        for (ServerLevel level : srv.getAllLevels()) {
            for (RiftWatcherEntity w : level.getEntities(ModEntities.RIFT_WATCHER, e -> true)) {
                w.discard();
                count++;
            }
        }
        for (PlayerState st : STATES.values()) {
            st.watcher = null;
        }
        return count;
    }

    // ------------------------------------------------------------------ ambient scares

    private static void runScare(ServerLevel level, ServerPlayer player, int stage) {
        int total = 0;
        for (Scare s : Scare.values()) {
            if (s.minStage <= stage) {
                total += s.weight;
            }
        }
        int roll = level.getRandom().nextInt(total);
        Scare chosen = Scare.FOOTSTEPS;
        for (Scare s : Scare.values()) {
            if (s.minStage > stage) {
                continue;
            }
            roll -= s.weight;
            if (roll < 0) {
                chosen = s;
                break;
            }
        }
        switch (chosen) {
            case FOOTSTEPS -> footsteps(level, player);
            case DOOR -> doorCreak(level, player);
            case MUSIC_BOX -> musicBox(level, player);
            case HISS -> hiss(level, player);
            case WHISPER -> whisper(level, player);
            case BREATH -> breath(level, player);
            case DARK_DIP -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, false));
            case GLIMPSE -> glimpse(level, player);
            case TEAR -> distantTear(level, player);
            case ACTIONBAR -> whisperText(level, player);
        }
    }

    /** Steps close in behind you. The instant you turn to face them, they stop. */
    private static void footsteps(ServerLevel level, ServerPlayer player) {
        final UUID id = player.getUUID();
        final Vec3 behind = flatLook(player).scale(-1.0);
        final double side = (level.getRandom().nextDouble() - 0.5) * 2.0;
        BlockState under = level.getBlockState(player.blockPosition().below());
        final SoundEvent step = under.getSoundType().getStepSound();
        int count = 4 + level.getRandom().nextInt(4);
        for (int i = 0; i < count; i++) {
            final int n = i;
            later(9 * (i + 1), () -> {
                ServerPlayer p = playerById(id);
                if (p == null) {
                    return;
                }
                if (flatLook(p).dot(behind) > 0.5) {
                    return; // you turned around. nothing is there. the steps just ended.
                }
                double dist = Math.max(2.5, 9.0 - n * 1.3);
                Vec3 lateral = new Vec3(-behind.z, 0.0, behind.x).scale(side);
                Vec3 at = p.position().add(behind.scale(dist)).add(lateral);
                Sfx.at(p, step, SoundSource.HOSTILE, at.x, at.y, at.z, 1.1F,
                        0.78F + p.getRandom().nextFloat() * 0.15F);
            });
        }
    }

    private static void doorCreak(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        Vec3 at = around(player, 6 + r.nextInt(5), r.nextDouble() * Math.PI * 2.0);
        Sfx.at(player, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.HOSTILE, at.x, at.y, at.z, 1.0F, 0.85F);
        final UUID id = player.getUUID();
        final Vec3 spot = at;
        later(25 + r.nextInt(30), () -> {
            ServerPlayer p = playerById(id);
            if (p != null) {
                Sfx.at(p, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.HOSTILE, spot.x, spot.y, spot.z, 1.0F, 0.85F);
            }
        });
    }

    private static void musicBox(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        Vec3 at = around(player, 16 + r.nextInt(10), r.nextDouble() * Math.PI * 2.0);
        Sfx.at(player, ModSounds.MUSIC_BOX, SoundSource.AMBIENT, at.x, at.y, at.z, 1.6F, 1.0F);
    }

    private static void hiss(ServerLevel level, ServerPlayer player) {
        Vec3 at = player.position().add(flatLook(player).scale(-2.6));
        Sfx.at(player, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, at.x, at.y, at.z, 1.0F, 0.5F);
    }

    private static void whisper(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        Vec3 at = player.getEyePosition().add(around(player, 1.4, r.nextDouble() * Math.PI * 2.0).subtract(player.position()));
        Sfx.at(player, ModSounds.WHISPER, SoundSource.HOSTILE, at.x, at.y, at.z, 1.0F, 0.85F + r.nextFloat() * 0.1F);
    }

    private static void breath(ServerLevel level, ServerPlayer player) {
        Vec3 at = player.getEyePosition().add(flatLook(player).scale(-1.0));
        Sfx.at(player, ModSounds.BREATH, SoundSource.HOSTILE, at.x, at.y - 0.2, at.z, 1.3F, 0.95F);
    }

    /** A rift rips open somewhere far off. You hear it. If you look, you might see it. */
    private static void distantTear(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        double angle = r.nextDouble() * Math.PI * 2.0;
        double dist = 28 + r.nextInt(14);
        int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos stand = findStand(level, x, z, player.blockPosition().getY());
        Vec3 at = stand == null
                ? around(player, dist, angle)
                : new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        Sfx.at(player, ModSounds.RIFT_TEAR, SoundSource.HOSTILE, at.x, at.y + 1.0, at.z, 2.0F, 0.9F + r.nextFloat() * 0.2F);
        if (stand != null) {
            scar(level, at, 70);
        }
    }

    /** A column of black smoke at the edge of your vision. For a second it almost looks like someone. Then it is gone. */
    private static void glimpse(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        Vec3 flat = flatLook(player);
        double angle = Math.atan2(flat.z, flat.x) + Math.toRadians((r.nextDouble() - 0.5) * 70.0);
        double dist = 14 + r.nextInt(9);
        int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos stand = findStand(level, x, z, player.blockPosition().getY());
        if (stand == null || level.getMaxLocalRawBrightness(stand) > 9) {
            return;
        }
        final Vec3 base = Vec3.atBottomCenterOf(stand);
        for (int i = 0; i < 12; i++) {
            later(i * 2, () -> {
                for (int k = 0; k < 6; k++) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + 0.2 + k * 0.5, base.z,
                            1, 0.1, 0.12, 0.1, 0.0);
                }
            });
        }
        later(26, () -> tearBurst(level, base));
    }

    private static void whisperText(ServerLevel level, ServerPlayer player) {
        String line = WHISPERS[level.getRandom().nextInt(WHISPERS.length)];
        player.displayClientMessage(
                Component.literal(line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
    }

    /** At full terror the light you built goes out, one torch at a time. The torch drops, you can put it back. */
    public static void snuffTorch(ServerLevel level, ServerPlayer player) {
        BlockPos c = player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -4, -10), c.offset(10, 6, 10))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)) {
                found.add(p.immutable());
            }
        }
        if (found.isEmpty()) {
            return;
        }
        BlockPos target = found.get(level.getRandom().nextInt(found.size()));
        level.destroyBlock(target, true);
        Sfx.at(player, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 0.9F, 0.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + 0.5, target.getY() + 0.7, target.getZ() + 0.5,
                6, 0.1, 0.1, 0.1, 0.01);
    }

    // ------------------------------------------------------------------ rifts (visual)

    public static void tearBurst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.4, at.z, 60, 0.5, 1.3, 0.5, 0.6);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.4, at.z, 24, 0.3, 1.2, 0.3, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.4, at.z, 6, 0.15, 1.2, 0.15, 0.02);
    }

    /** A wound in the air that lingers for {@code ticks} after something passes through it. */
    public static void scar(ServerLevel level, Vec3 at, int ticks) {
        SCARS.add(new Scar(level, at, ticks));
    }

    // ------------------------------------------------------------------ helpers

    /** Finds ground to stand on (3 blocks of headroom, no water) at x/z, nearest to refY. */
    public static BlockPos findStand(ServerLevel level, int x, int z, int refY) {
        for (int i = 0; i <= 20; i++) {
            int dy = (i % 2 == 0) ? i / 2 : -((i + 1) / 2);
            BlockPos feet = new BlockPos(x, refY + dy, z);
            if (!level.hasChunkAt(feet)) {
                return null;
            }
            if (!level.getBlockState(feet.below()).blocksMotion()) {
                continue;
            }
            if (isClear(level, feet) && isClear(level, feet.above()) && isClear(level, feet.above(2))) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private static Vec3 around(ServerPlayer player, double dist, double angle) {
        return player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
    }

    private static ServerPlayer playerById(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
