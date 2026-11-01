package com.sourceofmystery.entity;

/** Seconds measured only AFTER the caster has reached casting altitude. */
public final class OutsiderRitual {
    private OutsiderRitual(){}
    public static final float STAFF_END=3, BASE_END=7, ADVANCED_START=7,
            SUPER_START=9, UPPER_START=11, FULL_OPEN=12, CHANT_END=15,
            BEAM_HOLD_END=18, RINGS_FADE_END=20, BEAM_END=22,
            SKY_START=22, ORB_START=24, SKY_FADE_START=25, SKY_END=27,
            IMPACT=29, END=30.5f;
    public static final float RINGS_END=SUPER_START;
    public static final int FALL_TICKS=100, SKY_HEIGHT=255;
    public static final float ORB_RADIUS=25, SUPER_TIER_RADIUS=10, SKY_RADIUS=200;
    public static final double METEOR_MIN_AIR_HEIGHT=10.0;
    public static final double SILENCE_RADIUS=30.0;
    public static final int METEOR_COOLDOWN_TICKS=2*60*20;
    public static final float SHOUT_START=12.2f;
    public static float chargeEnvelope(float seconds){
        return (float)(smooth((seconds-STAFF_END)/4.0)*(1-smooth((seconds-BEAM_HOLD_END)/4.0)));
    }
    public static double smooth(double u){u=Math.max(0,Math.min(1,u));return u*u*u*(10+u*(-15+6*u));}
    public static double lightningBottom(double sky,double ground,float ageTicks){
        return sky+(ground-sky)*smooth(ageTicks/2.0);
    }
}
