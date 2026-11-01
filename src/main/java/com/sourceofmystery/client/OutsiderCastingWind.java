package com.sourceofmystery.client;

import org.joml.Vector3f;

/** Coherent anime updraft targets. Absolute strand directions prevent chain amplification. */
final class OutsiderCastingWind {
    private OutsiderCastingWind(){}
    static void bend(Vector3f direction,Vector3f root,Vector3f hips,int category,float time,float strength){
        if(strength<=0||category==0)return;
        float length=direction.length();
        if(category==1){
            float wave=(float)Math.sin(time*2.8-root.y*.40);
            direction.rotateX(-strength*(.36f+.06f*wave))
                    .rotateZ(strength*.045f*(float)Math.sin(time*2.1-root.y*.27));
        }else{
            float x=root.x-hips.x,z=root.z-hips.z;
            float radial=(float)Math.sqrt(x*x+z*z);
            if(radial>.01f){
                float wave=(float)Math.sin(time*3.3-root.y*.55);
                float flare=strength*(.29f+.055f*wave)*length/radial;
                direction.add(x*flare,length*.10f*strength,z*flare).normalize().mul(length);
            }
        }
    }
}
