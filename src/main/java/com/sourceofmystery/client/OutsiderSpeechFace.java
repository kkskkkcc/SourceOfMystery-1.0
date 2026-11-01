package com.sourceofmystery.client;

import com.sourceofmystery.entity.OutsiderVoice;
import net.minecraft.util.Mth;

/** Dialogue acting on the model's existing morphs; no wrist/bone modifications. */
final class OutsiderSpeechFace {
    private OutsiderSpeechFace(){}
    static void apply(int voice,float age,float mouth,float[] weights){
        if(voice<0||voice>=OutsiderVoice.IDS.length||age<0||age>=OutsiderVoice.TICKS[voice])return;
        float ending=OutsiderVoice.SPEECH_TICKS[voice];
        float blend=ease(age/3)*ease((ending-age)/6);
        float progress=Mth.clamp(age/ending,0,1);
        float smile=0,surprise=0,serious=0,smirk=0,angerEyes=0,half=0,anger=0;
        switch(voice){
            case 0 -> {smile=.12f;smirk=.28f;half=.14f;serious=.12f;}
            case 1,2 -> {serious=.62f;angerEyes=.28f;anger=.20f;}
            case 3,4 -> {serious=.74f;angerEyes=.46f;anger=.38f;}
            case 5,6 -> {serious=.28f+progress*.2f;smirk=.25f;half=.14f*(1-progress);}
            case 7,8 -> {serious=.50f+progress*.15f;angerEyes=.12f+progress*.12f;surprise=.10f;}
            case 9,10 -> {
                float burst=ease((age-OutsiderVoice.CLIMAX_TICKS[voice]+3)/5);
                serious=Mth.lerp(burst,.28f,.78f);smirk=.18f*(1-burst);
                half=.10f*(1-burst);angerEyes=.44f*burst;anger=.38f*burst;surprise=.12f*burst;
            }
            case 11 -> {serious=.18f;half=.15f;smile=.05f;}
            case 12 -> {serious=.60f;angerEyes=.24f;anger=.36f;half=.25f*(1-progress);}
            case 13,14 -> {smile=.10f;smirk=.06f;half=.12f;serious=.08f;}
            case 15 -> {serious=.35f;smirk=.10f;}
            case 16 -> {smile=.06f;half=.15f;serious=.10f;}
            default -> {return;}
        }
        // Brow pressure follows emphasis gently, without audio-rate jitter.
        float pressure=.88f+.12f*Mth.clamp(mouth/.65f,0,1);
        set(weights,1,smile,blend);set(weights,2,surprise,blend);
        set(weights,3,serious*pressure,blend);
        set(weights,4,0,blend);set(weights,5,0,blend);
        set(weights,6,smirk,blend);set(weights,9,angerEyes*pressure,blend);
        set(weights,10,half,blend);set(weights,11,anger*pressure,blend);
        // Pauses close the mouth; the echo tail does not hold a shout pose.
        float lipFade=ease((ending-age)/3);
        weights[7]=mouth*lipFade;weights[8]=mouth*lipFade*(voice>=9&&voice<=10?.28f:.20f);
    }
    private static void set(float[] values,int channel,float target,float blend){values[channel]=Mth.lerp(blend,values[channel],target);}
    private static float ease(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
}
