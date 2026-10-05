package com.sourceofmystery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 泣死之主出场时代替原版凋灵死亡动画的"凋灵躯壳"：纯视觉实体，
 * 2 秒内全身变白、一边旋转一边越转越快（client.WitherHuskRenderer 按 tickCount 计算），随后由泣死之主引爆移除。
 */
public class WitherHusk extends Entity {

    /** 变白、加速旋转的总时长（与 WeepingDeathLord.INTRO_EXPLODE 一致：2 秒） */
    public static final int DURATION = Math.round(WeepingDeathLord.INTRO_EXPLODE * 20);

    public WitherHusk(EntityType<? extends WitherHusk> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /** 变白进度 0~1 */
    public float whiteness(float partialTick) {
        return Math.min(1.0f, (this.tickCount + partialTick) / DURATION);
    }

    /** 当前自转角度（度）：角速度线性增加，2 秒时约每秒 3 圈 */
    public float spin(float partialTick) {
        float seconds = (this.tickCount + partialTick) / 20.0f;
        return 270.0f * seconds * seconds;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > DURATION + 40) {
            this.discard(); // 泣死之主没来得及引爆时自行消失
        }
    }

    @Override
    protected void defineSynchedData() {
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
