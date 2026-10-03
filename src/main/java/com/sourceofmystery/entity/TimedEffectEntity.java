package com.sourceofmystery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 出场演出用的纯视觉实体（空间裂缝、金色法阵）：没有碰撞、不存档，
 * 由服务端决定何时开始关闭，客户端按自身 tickCount 计算展开 / 收拢的进度来渲染，画面完全由渲染器绘制。
 */
public abstract class TimedEffectEntity extends Entity {

    private static final EntityDataAccessor<Boolean> DATA_CLOSING =
            SynchedEntityData.defineId(TimedEffectEntity.class, EntityDataSerializers.BOOLEAN);

    private int closingStart = -1;

    protected TimedEffectEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /** 最长存在时间（tick），防止服务端流程意外中断后残留 */
    protected abstract int maxLifetime();

    /** 关闭动画时长（tick） */
    public abstract int closeTicks();

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CLOSING, false);
    }

    /** 服务端调用：开始收拢，收拢完自动消失 */
    public void close() {
        this.entityData.set(DATA_CLOSING, true);
    }

    public boolean isClosing() {
        return this.entityData.get(DATA_CLOSING);
    }

    /**
     * 收拢进度 0~1（没在收拢时为 0）
     */
    public float closeProgress(float partialTick) {
        if (closingStart < 0) {
            return 0.0f;
        }
        return Math.min(1.0f, (this.tickCount + partialTick - closingStart) / closeTicks());
    }

    @Override
    public void tick() {
        super.tick();
        if (isClosing() && closingStart < 0) {
            closingStart = this.tickCount;
        }
        if (!this.level().isClientSide) {
            boolean closed = closingStart >= 0 && this.tickCount - closingStart > closeTicks() + 2;
            if (closed || this.tickCount > maxLifetime()) {
                this.discard();
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
    public boolean isPushable() {
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
