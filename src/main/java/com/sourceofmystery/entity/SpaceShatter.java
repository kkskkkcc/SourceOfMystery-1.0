package com.sourceofmystery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 泣死之主「吸附」斩击时的斩碎空间特效：一块朝向她的空间像玻璃一样碎裂，裂缝里露出虚空，
 * 碎片向四周飞散后淡出。画面见 client.SpaceShatterRenderer（贴图 textures/entity/space_shatter.png）。
 */
public class SpaceShatter extends TimedEffectEntity {

    /** 裂开的时间 */
    public static final int CRACK_TICKS = 4;
    /** 裂开后静止多久才开始飞散 */
    public static final int HOLD_TICKS = 10;
    public static final float SIZE = 7.0f;

    public SpaceShatter(EntityType<? extends SpaceShatter> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount == CRACK_TICKS + HOLD_TICKS) {
            close();
        }
    }

    @Override
    protected int maxLifetime() {
        return 80;
    }

    @Override
    public int closeTicks() {
        return 26;
    }
}
