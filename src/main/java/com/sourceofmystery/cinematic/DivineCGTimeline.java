package com.sourceofmystery.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Seconds, stage-local metres and continuous poses, sampled at render frequency. */
public final class DivineCGTimeline {
    public static final float END=68.7f, EQUIP=24.7f, HOLD=32f;
    public static final float SPACE_START=3.6f, ORBIT_START=8f, EARTH_RADIUS=92f;
    public static final Vec3 EARTH=v(-32,-106,-54);
    private static final float[] REAL={0,8,14,18,23,35,40,47,51,59,65,68,68.35f,END};
    private static final float[] STORY={0,4,8,10,12,18.5f,21,24.1f,25.5f,29.5f,32,35,35.35f,35.7f};
    public static float storyTime(float real){return remap(real,REAL,STORY);}
    public static float realTime(float story){return remap(story,STORY,REAL);}
    private static float remap(float t,float[] from,float[] to){
        for(int i=1;i<from.length;i++)if(t<=from[i])return mix(to[i-1],to[i],Mth.clamp((t-from[i-1])/(from[i]-from[i-1]),0,1));
        return to[to.length-1];
    }
    public record Shot(Vec3 eye,Vec3 look,float fov) {}
    private DivineCGTimeline() {}
    public static float smooth(float x){x=Mth.clamp(x,0,1);return x*x*x*(x*(6*x-15)+10);}
    public static float ramp(float t,float a,float b){return smooth((t-a)/(b-a));}
    public static float mix(float a,float b,float x){return Mth.lerp(x,a,b);}
    public static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    /** A continuous surface-to-orbit pullback around the very same Earth sphere. */
    public static Shot ascentShot(float real){
        Shot orbit=shot(4);Vec3 radial=orbit.eye.subtract(EARTH),up=radial.normalize();
        float u=ramp(real,SPACE_START,ORBIT_START);
        double distance=mix(EARTH_RADIUS+.8f,(float)radial.length(),u);
        Vec3 eye=EARTH.add(up.scale(distance));
        Vec3 orbitDirection=orbit.look.subtract(orbit.eye).normalize();
        Vec3 tangent=orbitDirection.subtract(up.scale(orbitDirection.dot(up))).normalize();
        double angle=Math.toRadians(55);
        Vec3 horizon=up.scale(-Math.cos(angle)).add(tangent.scale(Math.sin(angle)));
        Vec3 direction=horizon.lerp(orbitDirection,ramp(real,5.8f,ORBIT_START)).normalize();
        return new Shot(eye,eye.add(direction),mix(80,48,ramp(real,4.8f,ORBIT_START)));
    }
    public static Vec3 player(float t){
        float walk=Mth.clamp((t-12)/6.5f,0,1),q=Math.min(5,(t-12)/1.0833333f);
        q=Math.max(0,q);
        float step=Math.min(4,(int)q),phase=q-step;
        float y=q>=5?1.8f:(step+smooth(phase))*.36f;
        float start=step==0?4.2f:3.35f-(step-1)*1.4f;
        float landing=3.35f-step*1.4f;
        float z=q>=5?mix(-2.25f,-3.8f,ramp(t,17.416666f,18.5f)):mix(start,landing,smooth(phase));
        return v(0,y,z);
    }
    public static float walkAmount(float t){return ramp(t,12,12.5f)*(1-ramp(t,18,18.5f));}
    public static float headPitch(float t){
        return -.12f*ramp(t,8,9) + .12f*ramp(t,10,11) + .55f*ramp(t,25.5f,26.5f)
                -.18f*ramp(t,27.2f,28.3f)-.37f*ramp(t,29.4f,31.5f);
    }
    public static float headYaw(float t){return -.25f*ramp(t,26.3f,27.2f)+.5f*ramp(t,27.3f,28.5f)-.25f*ramp(t,29.4f,31.4f);}
    public static Shot shot(float t){
        Vec3 p=player(t);float u;
        if(t<8){u=ramp(t,4,8);double a=mix(-.95f,1.22f,u),r=mix(6.4f,4.5f,u);
            return new Shot(p.add(Math.sin(a)*r,mix(2.9f,2.05f,u),Math.cos(a)*r),p.add(0,1.0,0),48);}
        if(t<10){u=ramp(t,8,10);return new Shot(p.add(mix(1,.65f,u),1.65,-mix(1.8f,1.45f,u)),p.add(0,1.55,0),34);}
        if(t<12){u=ramp(t,10,12);return new Shot(v(mix(5.5f,6.5f,u),mix(3.7f,4.7f,u),mix(5.4f,3.5f,u)),v(0,1.8,-2.6),49);}
        if(t<18.5){u=ramp(t,12,18.5f);return new Shot(p.add(mix(4.6f,3.5f,u),1.65,.7),p.add(0,1.05,-.35),45);}
        if(t<21){u=ramp(t,18.5f,21);return new Shot(v(mix(2.05f,1.7f,u),3.35,mix(-4.2f,-4.4f,u)),v(0,3.15,-4.2),38);}
        if(t<24.1){u=ramp(t,21,24.1f);return new Shot(v(mix(2.5f,2.25f,u),mix(3.45f,3.25f,u),mix(-5.2f,-5.0f,u)),v(0,3.12,-4.15),42);}
        if(t<25.5){u=ramp(t,24.1f,25.5f);return new Shot(v(mix(1.1f,2.6f,u),3.1,-6),p.add(0,1.1,0),43);}
        if(t<29.5){u=ramp(t,25.5f,29.5f);return new Shot(p.add(mix(1.9f,1.35f,u),1.55,-2.5),p.add(0,1.0,0),40);}
        u=ramp(t,29.5f,HOLD);return new Shot(v(mix(2.8f,2.5f,u),mix(3.8f,3.7f,u),mix(.2f,-.5f,u)),v(0,3.1,-7.7),48);
    }
}
