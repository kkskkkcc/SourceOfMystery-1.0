package com.sourceofmystery.entity;

import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.ScreenEffectPacket;
import com.sourceofmystery.particle.ModParticles;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 神威天道的大招「天渊」：在高举的双手之间凝聚的黑色球体。
 * 蓄力阶段由神威天道每 tick 设置位置和大小；掷出后飞向玩家，碰到方块 / 实体或到达目标点时引发毁天灭地的爆炸，
 * 爆炸后再停留一会儿，向外扩散冲击波和蘑菇云。神威天道本身不受这次爆炸伤害。
 */
public class AbyssOrb extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(AbyssOrb.class, EntityDataSerializers.FLOAT);

    private static final double SPEED = 1.6;
    private static final int MAX_FLIGHT = 120;
    private static final int AFTERMATH_TICKS = 50;
    private static final float TERRAIN_POWER = 10.0f;     // 原版 TNT 为 4
    private static final double BLAST_RADIUS = 36.0;
    private static final float BLAST_DAMAGE = 160.0f;      // 中心伤害，向外线性衰减
    private static final double EFFECT_RANGE = 220.0;      // 闪光、震屏、轰鸣能传多远

    private enum Phase { CHARGING, FLYING, AFTERMATH }

    @Nullable
    private UUID ownerId;
    @Nullable
    private Entity cachedOwner;
    private Phase phase = Phase.CHARGING;
    private Vec3 targetPos = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private int phaseTicks;

    public AbyssOrb(EntityType<? extends AbyssOrb> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public AbyssOrb(Level level, Entity owner, Vec3 pos) {
        this(ModEntities.ABYSS_ORB.get(), level);
        this.ownerId = owner.getUUID();
        this.cachedOwner = owner;
        this.setPos(pos);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 0.3f);
    }

    public float radius() {
        return this.entityData.get(DATA_RADIUS);
    }

    @Nullable
    private Entity owner() {
        if (cachedOwner == null && ownerId != null && this.level() instanceof ServerLevel level) {
            cachedOwner = level.getEntity(ownerId);
        }
        return cachedOwner;
    }

    /** 蓄力中：由神威天道每 tick 调用 */
    public void hold(Vec3 pos, float radius) {
        this.setPos(pos);
        this.entityData.set(DATA_RADIUS, radius);
    }

    /** 掷出，飞向 target */
    public void launch(Vec3 target) {
        phase = Phase.FLYING;
        phaseTicks = 0;
        targetPos = target;
        Vec3 d = target.subtract(this.position());
        velocity = d.lengthSqr() < 1.0E-4 ? new Vec3(0, -SPEED, 0) : d.normalize().scale(SPEED);
    }

    public boolean isCharging() {
        return phase == Phase.CHARGING;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        Entity owner = owner();
        switch (phase) {
            case CHARGING -> {
                // 神威天道死亡 / 消失时球体随之消散
                if (owner == null || !owner.isAlive()) {
                    this.discard();
                }
            }
            case FLYING -> tickFlight(level, owner);
            case AFTERMATH -> tickAftermath(level);
        }
    }

    private void tickFlight(ServerLevel level, @Nullable Entity owner) {
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);
        float r = radius();
        // 拖尾：黑色粒子
        level.sendParticles(ModParticles.DARK_MOTE.get(), from.x, from.y, from.z, 6, r * 0.4, r * 0.4, r * 0.4, 0.0);
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        boolean reached = from.distanceTo(targetPos) <= SPEED * 1.5;
        boolean touched = !level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r * 0.8),
                e -> e != owner && e.isAlive() && !e.getTags().contains(DivineHeavenlyDaoBoss.MINION_TAG)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))).isEmpty();
        if (blocked || reached || touched || phaseTicks > MAX_FLIGHT) {
            if (blocked) {
                this.setPos(hit.getLocation());
            } else {
                this.setPos(to);
            }
            detonate(level, owner);
            return;
        }
        this.setPos(to);
    }

    /**
     * 爆炸：大范围破坏地形（遵守 mobGriefing）、按距离衰减的高额伤害和击飞、远处玩家的闪光与震屏
     */
    private void detonate(ServerLevel level, @Nullable Entity owner) {
        Vec3 c = this.position();
        phase = Phase.AFTERMATH;
        phaseTicks = 0;
        this.entityData.set(DATA_RADIUS, 0.0f);

        level.explode(owner, c.x, c.y, c.z, TERRAIN_POWER, Level.ExplosionInteraction.MOB);

        DamageSource source = this.damageSources().explosion(this, owner);
        AABB area = new AABB(c, c).inflate(BLAST_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != owner && e.isAlive()
                && !e.getTags().contains(DivineHeavenlyDaoBoss.MINION_TAG))) {
            double d = victim.position().distanceTo(c);
            if (d > BLAST_RADIUS) {
                continue;
            }
            double falloff = 1.0 - d / BLAST_RADIUS;
            victim.invulnerableTime = 0;
            victim.hurt(source, (float) (BLAST_DAMAGE * falloff + 10.0));
            Vec3 push = victim.position().subtract(c);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : push.normalize();
            victim.setDeltaMovement(victim.getDeltaMovement().add(push.scale(3.0 * falloff)).add(0, 1.2 * falloff, 0));
            victim.hurtMarked = true;
        }

        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 40, 9.0, 0);
        burst(level, ParticleTypes.FLASH, c, 4, 2.0, 0);
        burst(level, ModParticles.DARK_MOTE.get(), c, 300, 12.0, 0.6);

        for (ServerPlayer player : level.players()) {
            double d = player.position().distanceTo(c);
            if (d > EFFECT_RANGE) {
                continue;
            }
            float near = (float) (1.0 - d / EFFECT_RANGE);
            player.playNotifySound(ModSounds.DIVINE_HEAVENLY_DAO_ORB_EXPLODE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
            player.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 0.35f);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ScreenEffectPacket((int) (10 + 40 * near), (int) (20 + 40 * near), 0.5f + 2.5f * near));
        }
    }

    /**
     * 爆炸余波：向外扩散的地面冲击波、升起的烟柱和蘑菇云
     */
    private void tickAftermath(ServerLevel level) {
        Vec3 c = this.position();
        double u = phaseTicks / (double) AFTERMATH_TICKS;
        double ring = 4.0 + 56.0 * Math.sqrt(u);
        int points = 48;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points + phaseTicks * 0.03;
            double x = c.x + Math.cos(a) * ring;
            double z = c.z + Math.sin(a) * ring;
            send(level, ParticleTypes.CLOUD, x, c.y + 0.5, z, 1, 0.3, 0.2, 0.3, 0.05);
        }
        double stemTop = c.y + 6 + 22 * Math.min(1.0, u * 1.6);
        for (int i = 0; i < 6; i++) {
            double y = c.y + this.random.nextDouble() * (stemTop - c.y);
            send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, y, c.z, 1, 1.5, 0.5, 1.5, 0.02);
        }
        double cap = 4 + 10 * Math.min(1.0, u * 1.4);
        for (int i = 0; i < 10; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double r = this.random.nextDouble() * cap;
            send(level, i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.LARGE_SMOKE,
                    c.x + Math.cos(a) * r, stemTop + this.random.nextDouble() * 4, c.z + Math.sin(a) * r, 1, 0.5, 0.5, 0.5, 0.02);
        }
        if (phaseTicks >= AFTERMATH_TICKS) {
            this.discard();
        }
    }

    private void burst(ServerLevel level, ParticleOptions type, Vec3 c, int count, double spread, double speed) {
        send(level, type, c.x, c.y, c.z, count, spread, spread, spread, speed);
    }

    /** 远距离也能看到的粒子（原版 sendParticles 只发给 32 格内的玩家） */
    private static void send(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < EFFECT_RANGE * EFFECT_RANGE) {
                level.sendParticles(player, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
