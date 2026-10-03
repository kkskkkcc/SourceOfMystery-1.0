package com.sourceofmystery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 龙魂出场时的空间裂缝。只是一个渲染用实体：先出现一道细缝并向上下延伸，然后被撕开露出虚空，
 * 龙魂冲出去之后由服务端调用 {@link #close()} 合拢。画面见 client.VoidRiftRenderer。
 * 朝向（yRot）= 龙魂冲出的方向，裂缝平面与之垂直。
 */
public class VoidRift extends TimedEffectEntity {

    /** 细缝从无到完整高度的时间 */
    public static final int GROW_TICKS = 40;
    /** 被撕开的时间 */
    public static final int TEAR_TICKS = 8;
    public static final float HEIGHT = 9.0f;
    public static final float WIDTH = 4.6f;

    public VoidRift(EntityType<? extends VoidRift> type, Level level) {
        super(type, level);
    }

    @Override
    protected int maxLifetime() {
        return 600;
    }

    @Override
    public int closeTicks() {
        return 24;
    }

    /** 当前裂缝高度 */
    public float riftHeight(float partialTick) {
        float age = this.tickCount + partialTick;
        float grow = Math.min(1.0f, age / GROW_TICKS);
        float h = HEIGHT * (1 - (1 - grow) * (1 - grow));
        return h * (1 - closeProgress(partialTick));
    }

    /** 当前裂缝宽度：细缝阶段只有一条线，撕开后迅速张开，合拢时一起收回 */
    public float riftWidth(float partialTick) {
        float age = this.tickCount + partialTick;
        float slit = 0.12f + 0.08f * Math.min(1.0f, age / GROW_TICKS);
        float tear = Math.max(0.0f, Math.min(1.0f, (age - GROW_TICKS) / TEAR_TICKS));
        tear = 1 - (1 - tear) * (1 - tear) * (1 - tear);
        float close = closeProgress(partialTick);
        return (slit + (WIDTH - slit) * tear) * (1 - close * close);
    }

    /** 是否已经被撕开（服务端用来判断龙魂何时冲出） */
    public boolean isTorn() {
        return this.tickCount >= GROW_TICKS + TEAR_TICKS;
    }
}
