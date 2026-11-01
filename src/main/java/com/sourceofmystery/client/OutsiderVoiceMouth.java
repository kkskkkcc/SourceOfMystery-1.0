package com.sourceofmystery.client;

import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.entity.OutsiderVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.io.DataInputStream;
import java.io.IOException;

/** Finished recordings and dialogue-specific acting share the synchronized cue. */
final class OutsiderVoiceMouth {
    private final float[][] amplitudes=new float[OutsiderVoice.IDS.length][];
    OutsiderVoiceMouth(){
        var path=new ResourceLocation("sourceofmystery","meshes/entity/outsider_voice.bin");
        try(var in=new DataInputStream(Minecraft.getInstance().getResourceManager().getResourceOrThrow(path).open())){
            if(read(in)!=0x4f564f31||read(in)!=1||read(in)!=amplitudes.length||read(in)!=20)throw new IOException("Invalid voice envelope header");
            for(int i=0;i<amplitudes.length;i++){
                int n=read(in);if(n<1||n>600)throw new IOException("Invalid voice length");
                amplitudes[i]=new float[n];
                for(int j=0;j<n;j++)amplitudes[i][j]=Float.intBitsToFloat(read(in));
            }
        }catch(IOException error){throw new IllegalStateException("Unable to load voice mouth curves",error);}
    }
    private static int read(DataInputStream in)throws IOException{return Integer.reverseBytes(in.readInt());}
    void apply(OutsiderEntity entity,float partial,float[] weights){
        int index=entity.voiceIndex();if(index<0||index>=amplitudes.length)return;
        float age=entity.voiceAge(partial);float[] curve=amplitudes[index];
        if(age<0||age>=curve.length)return;
        int a=(int)age;float value=Mth.lerp(age-a,curve[a],curve[Math.min(a+1,curve.length-1)]);
        OutsiderSpeechFace.apply(index,age,value,weights);
    }
}
