package com.riftwatcher;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Watcher.
 *
 * It wears the Enderman body (tall silhouette, glowing eyes, screaming jaw) but none of its brain:
 * all vanilla goals are removed and a small state machine runs in {@link #serverTick}.
 *
 * THE RULES (these are what make it frightening, so they are strict):
 *  1. It only ever hunts the one player it was bound to.
 *  2. It never moves while you are looking at it. The moment you look away it blinks closer through a tear.
 *  3. Looking at it is not free: eye contact feeds your Dread, and after ~5 seconds your eyes give out
 *     (blindness + darkness) and it closes the distance while you cannot see.
 *  4. It cannot exist in light. Anywhere it would stand with light level 8+ it dissolves, and it cannot
 *     step into torchlight, so a ring of torches is a shelter, with the thing standing at the edge of it.
 *  5. Close behind you it winds up for one second. Turn and look at it in that second and it recoils.
 *     Do not, and it strikes.
 *  6. It cannot be killed by normal means. Hitting it only makes it scream.
 */
public class RiftWatcherEntity extends EnderMan {
    private static final double COS_STARE = Math.cos(Math.toRadians(14.0));
    private static final double COS_VIEW = Math.cos(Math.toRadians(42.0));
    private static final int MAX_LIGHT = 7;
    private static final int GAZE_BREAKS_AT = 110;
    private static final int MAX_AGE_TICKS = 20 * 60 * 5;

    private UUID targetId;
    private int dreadStage = 2;
    private int lowStageTicks;
    private int gaze;
    private float gazeDreadCarry;
    private int moveCooldown = 80;
    private int windup;
    private int heartTimer = 20;
    private int snuffTimer = 200;
    private int ageTicks;
    private int hauntLevel;
    private boolean leaving;
    /** Set by /riftwatcher summon: ignores light, ignores Dread, never times out. */
    private boolean debug;

    public RiftWatcherEntity(EntityType<? extends EnderMan> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setSilent(true); // every sound it makes is ours, sent only to the player it is hunting
    }

    // ------------------------------------------------------------------ binding / director hooks

    public void bindTo(ServerPlayer player, int hauntLevel, boolean debug, int stage) {
        this.targetId = player.getUUID();
        this.hauntLevel = hauntLevel;
        this.debug = debug;
        this.dreadStage = stage;
        this.moveCooldown = Math.max(30, 90 - hauntLevel * 12);
        this.faceEyes(player.getEyePosition());
    }

    /** Called by the director once a second so the Watcher knows how afraid its prey is. */
    public void setDreadStage(int stage) {
        if (!debug) {
            this.dreadStage = stage;
        }
    }

    // ------------------------------------------------------------------ vanilla behaviour removed

    @Override
    protected void registerGoals() {
        // No goals. No wandering, no picking up blocks, no vanilla enderman aggro. Everything is in serverTick.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // /kill, the void and creative players still work, so it never gets stuck in a world.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isCreativePlayer()) {
            return super.hurtServer(level, source, amount);
        }
        if (!leaving && source.getEntity() instanceof ServerPlayer attacker && attacker.getUUID().equals(this.targetId)) {
            // It does not flinch. It screams, and it stops waiting.
            Sfx.at(attacker, ModSounds.SCREAM, SoundSource.HOSTILE,
                    this.getX(), this.getEyeY(), this.getZ(), 1.2F, 1.25F);
            Haunting.addDread(attacker, 30);
            this.moveCooldown = Math.min(this.moveCooldown, 8);
            this.hauntLevel++;
        }
        return false;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        } else {
            this.clientTick();
        }
    }

    private void clientTick() {
        if (this.tickCount % 3 != 0) {
            return;
        }
        RandomSource r = this.random;
        double x = this.getX() + (r.nextDouble() - 0.5) * 0.8;
        double y = this.getY() + r.nextDouble() * 2.9;
        double z = this.getZ() + (r.nextDouble() - 0.5) * 0.8;
        this.level().addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z,
                (r.nextDouble() - 0.5) * 0.04, -0.01, (r.nextDouble() - 0.5) * 0.04);
        if (r.nextInt(5) == 0) {
            this.level().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0, -0.04, 0.0);
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.leaving || this.isRemoved()) {
            return;
        }
        this.ageTicks++;

        ServerPlayer player = this.targetId == null ? null : level.getServer().getPlayerList().getPlayer(this.targetId);
        if (player == null || !player.isAlive() || player.level() != level || player.isSpectator()
                || level.getDifficulty() == Difficulty.PEACEFUL) {
            this.leave(level, false, false);
            return;
        }
        if (!this.debug) {
            if (this.dreadStage < 2) {
                if (++this.lowStageTicks > 200) { // calm again: it loses interest
                    this.leave(level, false, false);
                    return;
                }
            } else {
                this.lowStageTicks = 0;
            }
            if (this.ageTicks > MAX_AGE_TICKS) {
                this.leave(level, false, false);
                return;
            }
        }

        Vec3 eye = player.getEyePosition();
        Vec3 toMe = this.getEyePosition().subtract(eye);
        double dist = toMe.length();
        if (dist > 110.0) {
            this.leave(level, false, false);
            return;
        }
        Vec3 dir = toMe.scale(1.0 / Math.max(dist, 1.0E-4));
        double dot = player.getLookAngle().dot(dir);
        boolean observed = dot > COS_STARE && player.hasLineOfSight(this);
        boolean inCone = dot > COS_VIEW;

        // Rule 4: light dissolves it.
        if (!this.debug && level.getMaxLocalRawBrightness(this.blockPosition()) > MAX_LIGHT) {
            this.leave(level, true, true);
            return;
        }

        this.faceEyes(eye);

        // ---- Rule 3: the cost of staring
        if (observed && dist < 64.0) {
            this.gaze++;
            this.gazeDreadCarry += 0.55F;
            if (this.gazeDreadCarry >= 1.0F) {
                int add = (int) this.gazeDreadCarry;
                this.gazeDreadCarry -= add;
                Haunting.addDread(player, add);
            }
            if (this.gaze == 12) {
                Sfx.at(player, ModSounds.STARE, SoundSource.HOSTILE,
                        this.getX(), this.getEyeY(), this.getZ(), 1.6F, 1.0F);
            }
            if (this.gaze == 30) {
                this.setTarget(player); // vanilla: the jaw drops and the arms rise
            }
            if (this.gaze >= 40 && this.gaze % 45 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, false));
            }
            if (this.gaze >= GAZE_BREAKS_AT) {
                this.eyesGiveOut(level, player);
            }
        } else {
            if (this.gaze > 0) {
                this.gaze = Math.max(0, this.gaze - 3);
            }
            if (this.gaze < 30 && this.getTarget() != null) {
                this.setTarget(null);
            }
        }

        // ---- Rule 2: it moves when you do not look
        if (this.dreadStage >= 3 && this.windup == 0) {
            if (this.moveCooldown > 0) {
                this.moveCooldown--;
            } else if (!observed && !inCone) {
                double step = dist > 20.0 ? 8.0 : (dist > 10.0 ? 5.0 : 3.0);
                if (this.blinkToward(level, player, step, 2.4, false)) {
                    int base = Math.max(18, 60 - this.hauntLevel * 8 - (dist < 12.0 ? 20 : 0));
                    this.moveCooldown = base + this.random.nextInt(25);
                } else {
                    this.moveCooldown = 10;
                }
            }
        }

        // ---- Rule 5: the strike
        if (this.dreadStage >= 3 && this.windup == 0 && dist <= 3.6 && !observed) {
            this.windup = 22;
            Sfx.at(player, ModSounds.BREATH, SoundSource.HOSTILE,
                    this.getX(), this.getEyeY(), this.getZ(), 1.5F, 0.9F);
        }
        if (this.windup > 0) {
            if (observed) {
                this.windup = 0;
                this.recoil(level, player);
            } else if (--this.windup == 0) {
                this.strike(level, player);
                return;
            }
        }

        // ---- torches in your shelter go out, one at a time
        if (this.dreadStage >= 3 && RiftConfig.get().snuffTorches && dist < 30.0 && --this.snuffTimer <= 0) {
            this.snuffTimer = 120 + this.random.nextInt(140);
            Haunting.snuffTorch(level, player);
        }

        // ---- the heartbeat speeds up as it gets closer
        if (--this.heartTimer <= 0) {
            float vol = Mth.clamp(1.25F - (float) dist / 70.0F, 0.3F, 1.25F);
            Sfx.inHead(player, ModSounds.HEARTBEAT, SoundSource.HOSTILE, vol, 1.0F);
            this.heartTimer = Mth.clamp((int) (dist * 1.1) + 12, 14, 55);
        }
    }

    // ------------------------------------------------------------------ moments

    private void eyesGiveOut(ServerLevel level, ServerPlayer player) {
        this.gaze = 0;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false, false));
        Sfx.at(player, ModSounds.SCREAM, SoundSource.HOSTILE,
                this.getX(), this.getEyeY(), this.getZ(), 1.5F, 0.9F);
        Haunting.addDread(player, 40);
        boolean hunting = this.dreadStage >= 3;
        this.blinkToward(level, player, hunting ? 9.0 : 7.0, hunting ? 2.6 : 14.0, true);
    }

    private void recoil(ServerLevel level, ServerPlayer player) {
        Sfx.at(player, ModSounds.SCREAM, SoundSource.HOSTILE,
                this.getX(), this.getEyeY(), this.getZ(), 1.3F, 1.2F);
        Haunting.addDread(player, 25);
        this.moveCooldown = 90 + this.random.nextInt(60);
        if (!this.blinkAway(level, player, 11.0)) {
            this.leave(level, true, true);
        }
    }

    private void strike(ServerLevel level, ServerPlayer player) {
        Sfx.at(player, ModSounds.SCREAM, SoundSource.HOSTILE,
                this.getX(), this.getEyeY(), this.getZ(), 1.6F, 0.8F);
        player.hurtServer(level, level.damageSources().mobAttack(this), RiftConfig.get().strikeDamage);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false, false));
        Haunting.addDread(player, 60);
        this.leave(level, false, true); // it got what it came for, and it remembers how
    }

    /** Dissolve back into the rift. {@code escalate} makes its next visit come sooner and move faster. */
    private void leave(ServerLevel level, boolean scream, boolean escalate) {
        if (this.leaving) {
            return;
        }
        this.leaving = true;
        Vec3 at = this.position();
        Haunting.scar(level, at, 50);
        Haunting.tearBurst(level, at);
        if (this.targetId != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.targetId);
            if (player != null) {
                if (scream) {
                    Sfx.at(player, ModSounds.SCREAM, SoundSource.HOSTILE, at.x, at.y + 2.0, at.z, 1.4F, 1.15F);
                } else {
                    Sfx.at(player, ModSounds.RIFT_TEAR, SoundSource.HOSTILE, at.x, at.y + 1.0, at.z, 1.0F, 0.8F);
                }
            }
            Haunting.watcherLeft(this.targetId, escalate);
        }
        this.discard();
    }

    // ------------------------------------------------------------------ movement through the rift

    /** Blink toward the player along a slightly jittered line. Never into light. Never into view unless blind. */
    private boolean blinkToward(ServerLevel level, ServerPlayer player, double step, double minDist, boolean ignoreView) {
        Vec3 me = this.position();
        Vec3 pp = player.position();
        double dx = pp.x - me.x;
        double dz = pp.z - me.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= minDist) {
            return false;
        }
        double move = Math.min(step, horizontal - minDist);
        double base = Mth.atan2(dz, dx);
        for (int i = 0; i < 10; i++) {
            double angle = base + Math.toRadians((this.random.nextDouble() - 0.5) * 44.0);
            int nx = Mth.floor(me.x + Math.cos(angle) * move);
            int nz = Mth.floor(me.z + Math.sin(angle) * move);
            BlockPos stand = Haunting.findStand(level, nx, nz, Mth.floor(me.y));
            if (stand == null || !this.canStandAt(level, stand)) {
                continue;
            }
            if (!ignoreView) {
                Vec3 there = new Vec3(stand.getX() + 0.5, stand.getY() + 1.6, stand.getZ() + 0.5)
                        .subtract(player.getEyePosition());
                if (player.getLookAngle().dot(there.normalize()) > COS_VIEW) {
                    continue;
                }
            }
            this.relocate(level, player, stand);
            return true;
        }
        return false;
    }

    /** Blink straight away from the player. */
    private boolean blinkAway(ServerLevel level, ServerPlayer player, double distance) {
        Vec3 me = this.position();
        Vec3 pp = player.position();
        double base = Mth.atan2(me.z - pp.z, me.x - pp.x);
        for (int i = 0; i < 10; i++) {
            double angle = base + Math.toRadians((this.random.nextDouble() - 0.5) * 70.0);
            int nx = Mth.floor(me.x + Math.cos(angle) * distance);
            int nz = Mth.floor(me.z + Math.sin(angle) * distance);
            BlockPos stand = Haunting.findStand(level, nx, nz, Mth.floor(me.y));
            if (stand != null && this.canStandAt(level, stand)) {
                this.relocate(level, player, stand);
                return true;
            }
        }
        return false;
    }

    private boolean canStandAt(ServerLevel level, BlockPos stand) {
        return this.debug || level.getMaxLocalRawBrightness(stand) <= MAX_LIGHT;
    }

    private void relocate(ServerLevel level, ServerPlayer player, BlockPos stand) {
        Haunting.scar(level, this.position(), 36); // a wound hangs in the air where it was
        Haunting.tearBurst(level, this.position());
        this.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        this.setDeltaMovement(Vec3.ZERO);
        Haunting.tearBurst(level, this.position());
        // You can always HEAR it move, even when you cannot see it.
        Sfx.at(player, ModSounds.RIFT_TEAR, SoundSource.HOSTILE, this.position(),
                0.7F, 1.1F + this.random.nextFloat() * 0.4F);
    }

    private void faceEyes(Vec3 target) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        double dy = target.y - this.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        this.setXRot((float) -(Mth.atan2(dy, horizontal) * (180.0 / Math.PI)));
    }
}
