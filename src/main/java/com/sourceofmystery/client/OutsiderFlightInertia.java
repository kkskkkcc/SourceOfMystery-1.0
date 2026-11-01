package com.sourceofmystery.client;

import org.joml.Quaternionf;

/** Shared-waist inertia: the legs inherit the chest's acceleration and bank. */
public final class OutsiderFlightInertia {
    private float pitch,roll,pelvisPitch,pelvisRoll;
    public void step(float velocityX,float velocityZ,float bank,float dt,boolean reset){
        if(reset)pelvisPitch=pelvisRoll=0;
        // The authored clip already gives the chest a slight lead.  Runtime
        // inertia should only add a small readable response, never tip the
        // whole body into a dive.
        pitch=clamp(velocityZ*.003f,.09f);
        roll=clamp(-velocityX*.002f-bank*.0008f,.10f);
        float follow=1-(float)Math.exp(-Math.max(0,dt)*5);
        pelvisPitch+=(pitch*.6f-pelvisPitch)*follow;
        pelvisRoll+=(roll*.6f-pelvisRoll)*follow;
    }
    public void apply(int bone,Quaternionf local,float age){
        float breath=(float)Math.sin(age*.105f)*.025f,sway=(float)Math.sin(age*.19f)*.045f;
        if(bone==5)local.rotateXYZ(pelvisPitch,0,pelvisRoll);
        if(bone==6)local.rotateXYZ(pitch-pelvisPitch+breath,0,roll-pelvisRoll);
        float drag=-(pitch-pelvisPitch)*.2f;
        if(bone==83||bone==98)local.rotateX(drag).rotateZ(sway*.25f);
        if(bone==90||bone==102)local.rotateX(drag).rotateZ(-sway*.25f);
    }
    private static float clamp(float value,float limit){return Math.max(-limit,Math.min(limit,value));}
}
