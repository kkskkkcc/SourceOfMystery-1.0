package com.sourceofmystery.entity;

/** Generated from production recordings; order matches the 20 Hz mouth envelopes. */
public final class OutsiderVoice {
    private OutsiderVoice(){}
    public static final int FIRST_DAILY=13, IDLE_GROUND=13, IDLE_FLIGHT=14, TAKEOFF=15, LANDING=16;
    public static final String[] IDS={"engage","grasp_a","grasp_b","silence_a","silence_b","ultimate_a","ultimate_b","chant_a","chant_b","release_a","release_b","kill","death","idle_ground","idle_flight","takeoff","landing"};
    public static final int[] TICKS={59,55,22,47,41,83,65,54,58,139,139,47,44,61,48,63,39};
    public static final int[] SPEECH_TICKS={58,55,21,46,41,83,64,53,58,99,99,46,42,61,47,63,37};
    public static final int[] CLIMAX_TICKS={-1,-1,-1,-1,-1,-1,-1,-1,-1,66,66,-1,-1,-1,-1,-1,-1};
}
