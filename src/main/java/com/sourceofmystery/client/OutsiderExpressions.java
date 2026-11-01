package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.io.DataInputStream;
import java.io.IOException;

/** Facial curves share the body's phase clock, including windup and recovery. */
final class OutsiderExpressions {
    static final int CHANNELS = 12;
    private final float[] values;
    private final int frames, fps;

    private OutsiderExpressions(float[] values, int frames, int fps) {
        this.values=values;this.frames=frames;this.fps=fps;
    }

    static OutsiderExpressions load(String name) {
        ResourceLocation path=new ResourceLocation(SourceOfMystery.MOD_ID,
                "meshes/entity/outsider_expressions/"+name+".bin");
        try (DataInputStream in=new DataInputStream(Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(path).open())) {
            if(readInt(in)!=0x4F464331||readInt(in)!=1||readInt(in)!=CHANNELS)
                throw new IOException("Unsupported facial curve header");
            int frames=readInt(in),fps=readInt(in);
            if(frames<2||frames>10000||fps!=60)throw new IOException("Invalid facial curve dimensions");
            float[] values=new float[frames*CHANNELS];
            for(int i=0;i<values.length;i++) {
                values[i]=Float.intBitsToFloat(readInt(in));
                if(!Float.isFinite(values[i])||values[i]<0||values[i]>1)
                    throw new IOException("Invalid facial weight");
            }
            com.sourceofmystery.SourceOfMystery.LOGGER.info("Outsider expressions loaded: {} ({} frames, {} channels)",name,frames,CHANNELS);
            return new OutsiderExpressions(values,frames,fps);
        } catch(IOException e) {throw new IllegalStateException("Unable to load "+path,e);}
    }

    void sample(float seconds, boolean loop, float[] output) {
        float frame=Math.max(0,seconds*fps);
        frame=loop?frame%(frames-1):Math.min(frame,frames-1);
        int a=(int)frame,b=Math.min(a+1,frames-1);
        for(int i=0;i<CHANNELS;i++)output[i]=Mth.lerp(frame-a,values[a*CHANNELS+i],values[b*CHANNELS+i]);
    }

    private static int readInt(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }
}
