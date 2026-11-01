package com.sourceofmystery.entity;

import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.world.phys.Vec3;

/** Common-side hand trajectory baked from the same final clip as the client mesh. */
final class OutsiderSockets {
    private static final float[] GRASP=load("grasp"),RITUAL=load("ritual");
    private static float[] load(String name){
        try(DataInputStream in=new DataInputStream(OutsiderSockets.class.getResourceAsStream("/assets/sourceofmystery/meshes/entity/outsider_"+name+"_socket.bin"))){
            if(Integer.reverseBytes(in.readInt())!=0x4F534B31)throw new IOException("Invalid socket data");
            int frames=Integer.reverseBytes(in.readInt()),fps=Integer.reverseBytes(in.readInt());
            if(frames<2||frames>10000||fps!=60)throw new IOException("Invalid socket dimensions");
            float[] values=new float[frames*3];for(int i=0;i<values.length;i++)values[i]=Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
            return values;
        }catch(IOException|NullPointerException e){throw new IllegalStateException("Missing Outsider hand socket",e);}
    }
    static Vec3 grasp(float seconds){
        return sample(GRASP,seconds);
    }
    static Vec3 ritual(float seconds){return sample(RITUAL,seconds);}
    private static Vec3 sample(float[] track,float seconds){
        float f=Math.max(0,Math.min(track.length/3-1,seconds*60));int a=(int)f,b=Math.min(a+1,track.length/3-1);float t=f-a;
        return new Vec3(track[a*3]+(track[b*3]-track[a*3])*t,track[a*3+1]+(track[b*3+1]-track[a*3+1])*t,
                track[a*3+2]+(track[b*3+2]-track[a*3+2])*t).scale(OutsiderEntity.MODEL_SCALE);
    }
}
