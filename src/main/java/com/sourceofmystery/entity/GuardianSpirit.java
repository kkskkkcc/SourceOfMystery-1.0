package com.sourceofmystery.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A non-hostile village guardian. The server owns one explicit action state and
 * one movement decision per tick; GeckoLib only displays the synchronized state.
 */
public class GuardianSpirit extends PathfinderMob implements GeoEntity {
    private enum Action { IDLE, LOVE, WATER_FLOAT, CHARGE, PRAYER }

    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(GuardianSpirit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ACTION_STARTED =
            SynchedEntityData.defineId(GuardianSpirit.class, EntityDataSerializers.LONG);
    private static final DustParticleOptions BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.12F, 0.55F, 1.0F), 1.1F);
    private static final double VILLAGE_PATROL_RADIUS = 24.0;
    private static final double RETURN_DISTANCE_SQR = 64.0 * 64.0;
    private static final double ATTACK_DISTANCE_SQR = 32.0 * 32.0;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos homeVillageCenter;
    @Nullable private Vec3 patrolDestination;
    @Nullable private UUID recentPlayerAttacker;
    private long recentAttackerExpires;
    private int actionTicks;
    private int chargeCooldown;
    private int prayerCooldown;
    private int loveCooldown;
    private int floatCooldown;
    private int flightTicks;
    private int landingTicks;
    private int desiredAllays;
    private int allayLoadDelay;
    private final List<UUID> allayIds = new ArrayList<>();
    private final Map<UUID, Integer> missingAllayTicks = new HashMap<>();
    private boolean allaysInitialized;
    private boolean cleanedUp;

    public GuardianSpirit(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        flightTicks = 300 + random.nextInt(300);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3000.0)
                .add(Attributes.ATTACK_DAMAGE, 50.0)
                .add(Attributes.ARMOR, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Nullable public BlockPos getHomeVillageCenter() { return homeVillageCenter; }
    public void setHomeVillageCenter(BlockPos center) { homeVillageCenter = center.immutable(); }
    public int getAnimState() { return entityData.get(ACTION); }
    public boolean isPraying() { return getAnimState() == Action.PRAYER.ordinal(); }
    public long getStateAge() { return Math.max(0L, level().getGameTime() - entityData.get(ACTION_STARTED)); }

    /** The event handler records a player attacker without ever targeting players. */
    public void rememberPlayerAttacker(LivingEntity attacker) {
        if (level().isClientSide || isFriendly(attacker)) return;
        recentPlayerAttacker = attacker.getUUID();
        recentAttackerExpires = level().getGameTime() + 200;
        setTarget(attacker);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, Action.IDLE.ordinal());
        entityData.define(ACTION_STARTED, 0L);
    }

    @Override
    protected void registerGoals() {
        // Movement, target selection and actions are coordinated in serverTick().
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        serverTick(server);
    }

    private void serverTick(ServerLevel server) {
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(5.0F);
        chargeCooldown = Math.max(0, chargeCooldown - 1);
        prayerCooldown = Math.max(0, prayerCooldown - 1);
        loveCooldown = Math.max(0, loveCooldown - 1);
        floatCooldown = Math.max(0, floatCooldown - 1);

        if (tickCount % 10 == 0) selectTarget(server);
        if (tickCount % 100 == 0) {
            healVillagers(server);
            if (homeVillageCenter == null || distanceToSqr(Vec3.atCenterOf(homeVillageCenter)) > RETURN_DISTANCE_SQR) {
                adoptNearestVacantVillage(server);
            }
        }
        tickAllays(server);
        tickMovement(server);
        tickAction(server);
    }

    private void selectTarget(ServerLevel server) {
        if (recentPlayerAttacker != null && server.getGameTime() < recentAttackerExpires
                && server.getEntity(recentPlayerAttacker) instanceof LivingEntity attacker
                && isHostileToPlayers(attacker) && distanceToSqr(attacker) <= ATTACK_DISTANCE_SQR) {
            setTarget(attacker);
            return;
        }
        LivingEntity nearest = null;
        double nearestDistance = ATTACK_DISTANCE_SQR;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(32.0), this::isHostileToPlayers)) {
            double distance = distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        setTarget(nearest);
    }

    private boolean isHostileToPlayers(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || isFriendly(entity)) return false;
        if (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER) return true;
        if (entity instanceof Mob mob && mob.getTarget() instanceof Player) return true;
        return recentPlayerAttacker != null && entity.getUUID().equals(recentPlayerAttacker)
                && level().getGameTime() < recentAttackerExpires;
    }

    private static boolean isFriendly(LivingEntity entity) {
        return entity instanceof Player || entity instanceof GuardianSpirit || entity instanceof Animal
                || entity instanceof AbstractVillager || entity instanceof AbstractGolem;
    }

    private void tickAction(ServerLevel server) {
        Action current = Action.values()[getAnimState()];
        if (current != Action.IDLE) {
            if (current == Action.LOVE && tickCount % 3 == 0) {
                server.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.1, getZ(),
                        2, 0.35, 0.3, 0.35, 0.02);
            } else if (current == Action.CHARGE) {
                showGrowingOrb(server);
            }
            if (--actionTicks <= 0) {
                if (current == Action.CHARGE) fireOrb(server);
                if (current == Action.PRAYER) castLightning(server);
                setAction(Action.IDLE, 0);
            }
            return;
        }

        LivingEntity target = getTarget();
        if (target != null && isHostileToPlayers(target) && distanceToSqr(target) <= ATTACK_DISTANCE_SQR) {
            int hostileCount = server.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(32), this::isHostileToPlayers).size();
            if (prayerCooldown == 0 && (hostileCount >= 2 || random.nextInt(4) == 0)) {
                setAction(Action.PRAYER, 60);
                prayerCooldown = 180;
            } else if (chargeCooldown == 0) {
                setAction(Action.CHARGE, 40);
                chargeCooldown = 20;
            }
            return;
        }

        if (loveCooldown == 0 && seesBabyVillager(server)) {
            setAction(Action.LOVE, 76); // 0.5 s approach, 3 s hold, 0.3 s release
            loveCooldown = 400;
        } else if (floatCooldown == 0 && random.nextInt(300) == 0) {
            setAction(Action.WATER_FLOAT, 200);
            floatCooldown = 400;
        }
    }

    private void setAction(Action action, int duration) {
        entityData.set(ACTION, action.ordinal());
        entityData.set(ACTION_STARTED, level().getGameTime());
        actionTicks = duration;
    }

    private boolean seesBabyVillager(ServerLevel server) {
        return server.getEntitiesOfClass(Villager.class, getBoundingBox().inflate(10.0),
                villager -> villager.isBaby() && villager.isAlive() && hasLineOfSight(villager)).size() > 0;
    }

    private void healVillagers(ServerLevel server) {
        for (Villager villager : server.getEntitiesOfClass(Villager.class, getBoundingBox().inflate(8.0),
                villager -> villager.isAlive() && villager.getHealth() < villager.getMaxHealth())) {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
    }

    private void showGrowingOrb(ServerLevel server) {
        float growth = Math.min(1.0F, (40 - actionTicks) / 40.0F);
        double radius = 0.08 + growth * 0.42;
        Vec3 forward = getLookAngle();
        Vec3 center = position().add(forward.x * 0.6, 1.25, forward.z * 0.6);
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double elevation = random.nextDouble() * Math.PI;
            server.sendParticles(BLUE_DUST,
                    center.x + radius * Math.sin(elevation) * Math.cos(angle),
                    center.y + radius * Math.cos(elevation),
                    center.z + radius * Math.sin(elevation) * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                2, radius, radius, radius, 0);
    }

    private void fireOrb(ServerLevel server) {
        LivingEntity target = getTarget();
        if (target == null || !isHostileToPlayers(target) || distanceToSqr(target) > ATTACK_DISTANCE_SQR) return;
        GuardianSpiritProjectile orb = ModEntities.GUARDIAN_SPIRIT_PROJECTILE.get().create(server);
        if (orb == null) return;
        orb.setOwner(this);
        orb.setTarget(target);
        Vec3 forward = getLookAngle();
        orb.moveTo(getX() + forward.x * 0.6, getY() + 1.25, getZ() + forward.z * 0.6);
        server.addFreshEntity(orb);
        server.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL,
                1.0F, 1.15F);
    }

    private void castLightning(ServerLevel server) {
        AABB area = getBoundingBox().inflate(32.0);
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> isHostileToPlayers(entity) && distanceToSqr(entity) <= ATTACK_DISTANCE_SQR);
        for (LivingEntity target : targets) {
            var bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
            target.invulnerableTime = 0;
            target.hurt(damageSources().indirectMagic(this, this), 10.0F);
        }
        if (!targets.isEmpty()) server.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    private void tickMovement(ServerLevel server) {
        if (homeVillageCenter == null) {
            setDeltaMovement(getDeltaMovement().scale(0.65));
            return;
        }
        double homeDistance = horizontalDistanceSqr(blockPosition(), homeVillageCenter);
        if (homeDistance > RETURN_DISTANCE_SQR) {
            if (!isNoGravity()) setNoGravity(true);
            patrolDestination = null;
            flyToward(server, Vec3.atCenterOf(homeVillageCenter).add(0, 2, 0), 0.48);
            return;
        }
        if (!server.isVillage(blockPosition()) && homeDistance > 4.0) {
            if (!isNoGravity()) setNoGravity(true);
            patrolDestination = null;
            flyToward(server, Vec3.atCenterOf(homeVillageCenter).add(0, 2, 0), 0.34);
            return;
        }

        Action action = Action.values()[getAnimState()];
        if (action == Action.LOVE || action == Action.CHARGE || action == Action.PRAYER) {
            // The 3-second affection hold and both spell casts remain in place.
            setDeltaMovement(getDeltaMovement().scale(isNoGravity() ? 0.25 : 0.50));
            patrolDestination = null;
            if (getTarget() != null) faceMovement(getTarget().position().subtract(position()));
            return;
        }

        if (landingTicks > 0) {
            landingTicks--;
            if (landingTicks == 0) {
                setNoGravity(true);
                flightTicks = 300 + random.nextInt(300);
                patrolDestination = null;
            } else {
                if (patrolDestination == null || position().distanceToSqr(patrolDestination) < 2.0) {
                    patrolDestination = choosePatrolPoint(server, false);
                }
                if (patrolDestination != null) moveHorizontally(patrolDestination, 0.13);
            }
            return;
        }
        if (--flightTicks <= 0) {
            landingTicks = 60 + random.nextInt(80);
            setNoGravity(false);
            patrolDestination = null;
            return;
        }
        if (patrolDestination == null || position().distanceToSqr(patrolDestination) < 3.0
                || !server.isVillage(BlockPos.containing(patrolDestination))) {
            patrolDestination = choosePatrolPoint(server, true);
        }
        if (patrolDestination != null) flyToward(server, patrolDestination, 0.26);
    }

    @Nullable
    private Vec3 choosePatrolPoint(ServerLevel server, boolean airborne) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 5.0 + random.nextDouble() * (VILLAGE_PATROL_RADIUS - 5.0);
            int x = (int) Math.floor(homeVillageCenter.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(homeVillageCenter.getZ() + Math.sin(angle) * radius);
            BlockPos probe = new BlockPos(x, homeVillageCenter.getY(), z);
            if (!server.hasChunkAt(probe) || !server.isVillage(probe)) continue;
            int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return new Vec3(x + 0.5, ground + (airborne ? 1.6 + random.nextDouble() * 2.0 : 0.1), z + 0.5);
        }
        return Vec3.atCenterOf(homeVillageCenter).add(0, 2, 0);
    }

    private void flyToward(ServerLevel server, Vec3 destination, double speed) {
        Vec3 direction = destination.subtract(position());
        if (direction.lengthSqr() < 0.02) return;
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        int aheadX = (int) Math.floor(getX() + Math.signum(direction.x) * 2.0);
        int aheadZ = (int) Math.floor(getZ() + Math.signum(direction.z) * 2.0);
        double desiredY = destination.y;
        if (server.hasChunkAt(new BlockPos(aheadX, blockPosition().getY(), aheadZ))) {
            desiredY = Math.max(desiredY, server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    aheadX, aheadZ) + 1.5);
        }
        Vec3 velocity = new Vec3(horizontal.lengthSqr() < 0.01 ? 0 : horizontal.normalize().x * speed,
                Math.max(-0.20, Math.min(0.30, (desiredY - getY()) * 0.12)),
                horizontal.lengthSqr() < 0.01 ? 0 : horizontal.normalize().z * speed);
        if (!server.noCollision(this, getBoundingBox().move(velocity.scale(2.0)))) {
            velocity = velocity.add(0, 0.20, 0);
        }
        setDeltaMovement(getDeltaMovement().scale(0.45).add(velocity.scale(0.55)));
        hasImpulse = true;
        faceMovement(horizontal);
    }

    private void moveHorizontally(Vec3 destination, double speed) {
        Vec3 direction = destination.subtract(position());
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 0.04) return;
        horizontal = horizontal.normalize().scale(speed);
        setDeltaMovement(horizontal.x, getDeltaMovement().y, horizontal.z);
        hasImpulse = true;
        faceMovement(horizontal);
    }

    private void faceMovement(Vec3 direction) {
        if (direction.lengthSqr() < 0.001) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void adoptNearestVacantVillage(ServerLevel server) {
        GuardianVillageRegistry registry = GuardianVillageRegistry.get(server);
        List<Villager> candidates = server.getEntitiesOfClass(Villager.class,
                getBoundingBox().inflate(256.0), villager -> villager.isAlive()
                        && server.isVillage(villager.blockPosition()));
        candidates.sort((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b)));
        for (Villager villager : candidates) {
            BlockPos center = villager.blockPosition();
            boolean alreadyGuarded = !server.getEntitiesOfClass(GuardianSpirit.class,
                    new AABB(center).inflate(48.0), other -> other != this && other.isAlive()
                            && other.homeVillageCenter != null
                            && horizontalDistanceSqr(other.homeVillageCenter, center) < 48.0 * 48.0).isEmpty();
            if (alreadyGuarded) continue;
            if (registry.move(getUUID(), center)) {
                homeVillageCenter = center.immutable();
                patrolDestination = null;
                return;
            }
        }
    }

    private void tickAllays(ServerLevel server) {
        if (allayLoadDelay > 0) {
            allayLoadDelay--; // saved companions may enter the entity index after their owner
            return;
        }
        if (desiredAllays == 0) desiredAllays = 3 + random.nextInt(3);
        if (!allaysInitialized || tickCount % 100 == 0) {
            List<Allay> extras = new ArrayList<>();
            for (Allay allay : server.getEntitiesOfClass(Allay.class, getBoundingBox().inflate(12.0))) {
                if (!allay.getPersistentData().hasUUID("GuardianSpiritOwner")
                        || !getUUID().equals(allay.getPersistentData().getUUID("GuardianSpiritOwner"))) continue;
                if (!allayIds.contains(allay.getUUID()) && allayIds.size() < desiredAllays) allayIds.add(allay.getUUID());
                else if (!allayIds.contains(allay.getUUID())) extras.add(allay);
            }
            extras.forEach(Allay::discard);
            allaysInitialized = true;
        }

        for (int index = 0; index < allayIds.size(); index++) {
            UUID id = allayIds.get(index);
            Entity entity = server.getEntity(id);
            if (!(entity instanceof Allay allay) || !allay.isAlive()) {
                int missing = missingAllayTicks.getOrDefault(id, 0) + 1;
                if (missing > 200) {
                    allayIds.remove(index--);
                    missingAllayTicks.remove(id);
                } else missingAllayTicks.put(id, missing);
                continue;
            }
            missingAllayTicks.remove(id);
            double phase = index * 2.39996;
            double tick = tickCount;
            double angle = tick * (0.045 + index * 0.003) + phase;
            double radius = 1.45 + 0.25 * Math.sin(tick * 0.07 + phase);
            Vec3 goal = position().add(radius * Math.cos(angle),
                    1.1 + 0.35 * Math.sin(tick * 0.09 + phase), radius * Math.sin(angle));
            if (!server.hasChunkAt(BlockPos.containing(goal))) goal = position().add(0, 1.2, 0);
            double smoothing = allay.distanceToSqr(goal) > 36.0 ? 1.0 : 0.20;
            Vec3 next = allay.position().lerp(goal, smoothing);
            allay.moveTo(next.x, next.y, next.z, (float) Math.toDegrees(-angle), 0);
            allay.hasImpulse = true;
        }
        while (allayIds.size() < desiredAllays) {
            Allay allay = EntityType.ALLAY.create(server);
            if (allay == null) break;
            allay.setNoAi(true);
            allay.setNoGravity(true);
            allay.setInvulnerable(true);
            allay.setPersistenceRequired();
            allay.getPersistentData().putUUID("GuardianSpiritOwner", getUUID());
            allay.moveTo(getX(), getY() + 1.2, getZ());
            if (!server.addFreshEntity(allay)) break;
            allayIds.add(allay.getUUID());
        }
    }

    private void removeOwnedAllays(ServerLevel server) {
        if (cleanedUp) return;
        cleanedUp = true;
        for (UUID id : allayIds) {
            if (server.getEntity(id) instanceof Allay allay) allay.discard();
        }
        List<Allay> companions = new ArrayList<>();
        for (Allay allay : server.getEntitiesOfClass(Allay.class, getBoundingBox().inflate(16.0))) {
            if (allay.getPersistentData().hasUUID("GuardianSpiritOwner")
                    && getUUID().equals(allay.getPersistentData().getUUID("GuardianSpiritOwner"))) companions.add(allay);
        }
        companions.forEach(Allay::discard);
        allayIds.clear();
        GuardianVillageRegistry.get(server).release(getUUID());
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) removeOwnedAllays(server);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server && (reason == RemovalReason.KILLED
                || reason == RemovalReason.DISCARDED || reason == RemovalReason.CHANGED_DIMENSION)) {
            removeOwnedAllays(server);
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homeVillageCenter != null) tag.putLong("HomeVillage", homeVillageCenter.asLong());
        tag.putInt("DesiredAllays", desiredAllays);
        tag.putInt("ChargeCooldown", chargeCooldown);
        tag.putInt("PrayerCooldown", prayerCooldown);
        tag.putInt("LoveCooldown", loveCooldown);
        tag.putInt("FloatCooldown", floatCooldown);
        tag.putInt("FlightTicks", flightTicks);
        ListTag list = new ListTag();
        for (UUID id : allayIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            list.add(entry);
        }
        tag.put("FollowingAllays", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        homeVillageCenter = tag.contains("HomeVillage") ? BlockPos.of(tag.getLong("HomeVillage")) : null;
        desiredAllays = tag.getInt("DesiredAllays");
        chargeCooldown = tag.getInt("ChargeCooldown");
        prayerCooldown = tag.getInt("PrayerCooldown");
        loveCooldown = tag.getInt("LoveCooldown");
        floatCooldown = tag.getInt("FloatCooldown");
        flightTicks = Math.max(1, tag.getInt("FlightTicks"));
        allayIds.clear();
        ListTag list = tag.getList("FollowingAllays", Tag.TAG_COMPOUND);
        for (Tag item : list) {
            CompoundTag entry = (CompoundTag) item;
            if (entry.hasUUID("Id")) allayIds.add(entry.getUUID("Id"));
        }
        allayLoadDelay = 40;
        setNoGravity(true);
        setAction(Action.IDLE, 0);
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            RawAnimation animation = switch (Action.values()[getAnimState()]) {
                case LOVE -> RawAnimation.begin().thenPlay("animation.guardian_spirit.love");
                case WATER_FLOAT -> RawAnimation.begin().thenLoop("animation.guardian_spirit.water_float");
                case CHARGE -> RawAnimation.begin().thenPlay("animation.guardian_spirit.attack_charge");
                case PRAYER -> RawAnimation.begin().thenPlay("animation.guardian_spirit.attack_prayer");
                default -> RawAnimation.begin().thenLoop("animation.guardian_spirit.idle");
            };
            return state.setAndContinue(animation);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
