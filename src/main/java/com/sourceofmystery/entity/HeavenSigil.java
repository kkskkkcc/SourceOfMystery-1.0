package com.sourceofmystery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 神威天道降临时悬在天上的金色法阵：从中心向外展开、缓缓旋转，神威天道穿过它从天而降，
 * 降临结束后由服务端调用 {@link #close()} 消散。画面见 client.HeavenSigilRenderer。
 */
public class HeavenSigil extends TimedEffectEntity {

    public static final float RADIUS = 30.0f;
    public static final int OPEN_TICKS = 40;

    public HeavenSigil(EntityType<? extends HeavenSigil> type, Level level) {
        super(type, level);
    }

    @Override
    protected int maxLifetime() {
        return 1200;
    }

    @Override
    public int closeTicks() {
        return 30;
    }

    /** 当前半径：展开时由小到大，消散时继续略微扩大 */
    public float radius(float partialTick) {
        float age = this.tickCount + partialTick;
        float open = Math.min(1.0f, age / OPEN_TICKS);
        open = 1 - (1 - open) * (1 - open) * (1 - open);
        return RADIUS * open * (1 + 0.2f * closeProgress(partialTick));
    }

    /** 当前亮度：消散时淡出 */
    public float alpha(float partialTick) {
        float age = this.tickCount + partialTick;
        return Math.min(1.0f, age / 20.0f) * (1 - closeProgress(partialTick));
    }
}
