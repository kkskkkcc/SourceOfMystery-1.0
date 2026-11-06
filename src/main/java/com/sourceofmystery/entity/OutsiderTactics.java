package com.sourceofmystery.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared cone limits and frozen-yaw barrage math. */
final class OutsiderTactics {
    static final float BARRAGE_YAW=27, BARRAGE_PITCH=32;
    static final double BARRAGE_SPEED=.75;

    private OutsiderTactics(){}
    static Vec3 barrageVelocity(float yaw,float offsetDeg,float pitchDeg){
        return Vec3.directionFromRotation(Mth.clamp(pitchDeg,-BARRAGE_PITCH,BARRAGE_PITCH),
                yaw+Mth.clamp(offsetDeg,-BARRAGE_YAW,BARRAGE_YAW)).normalize().scale(BARRAGE_SPEED);
    }
}
