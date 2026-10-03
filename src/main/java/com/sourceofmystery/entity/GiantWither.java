package com.sourceofmystery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;

/**
 * 神威天道二阶段召唤的巨型凋灵：行为和原版凋灵一样，体型放大到和末影龙差不多。
 * 碰撞箱在 ModEntities 中按 SCALE 放大，渲染器 GiantWitherRenderer 按同样倍数放大模型。
 */
public class GiantWither extends WitherBoss {

    public static final float SCALE = 3.0f;

    public GiantWither(EntityType<? extends WitherBoss> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }
}
