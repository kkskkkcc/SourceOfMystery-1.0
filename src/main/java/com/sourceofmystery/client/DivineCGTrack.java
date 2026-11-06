package com.sourceofmystery.client;

import com.google.gson.*;
import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Authored story-time tracks with reusable sampling results. */
final class DivineCGTrack {
    private static final ResourceLocation RESOURCE=new ResourceLocation("sourceofmystery","cinematic/divine_ascension.json");
    private static final String[] PARTS={"head","body","rightArm","leftArm","rightLeg","leftLeg"};
    private static Frames body,camera;
    private static boolean warned;
    private DivineCGTrack(){}
    static void load(){
        body=camera=null;
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(RESOURCE)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("version").getAsInt()!=1 || root.get("rate").getAsFloat()!=20)throw new IllegalArgumentException("Unsupported track format");
            float start=root.get("start").getAsFloat(),end=root.get("end").getAsFloat();
            if(!Float.isFinite(start)||!Float.isFinite(end)||end<start)throw new IllegalArgumentException("Invalid track range");
            var parts=root.getAsJsonArray("parts");
            if(parts.size()!=PARTS.length)throw new IllegalArgumentException("Invalid parts");
            for(int i=0;i<PARTS.length;i++)if(!PARTS[i].equals(parts.get(i).getAsString()))throw new IllegalArgumentException("Invalid part order");
            var parsedBody=new Frames(root.getAsJsonArray("body"),5+PARTS.length*3);
            var parsedCamera=new Frames(root.getAsJsonArray("camera"),8);
            body=parsedBody;camera=parsedCamera;
        } catch(Exception ex){
            if(!warned){SourceOfMystery.LOGGER.warn("Cannot load divine ascension track; using formula fallback: {}",ex.toString());warned=true;}
        }
    }
    static Sample body(float t){return body==null?null:body.sample(t);}
    static Sample camera(float t){return camera==null?null:camera.sample(t);}
    static final class Sample {
        final float[] values;
        Sample(int width){values=new float[width];}
    }
    private static final class Frames {
        final float[] data;
        final int width,count;
        final Sample result;
        Frames(JsonArray rows,int width){
            this.width=width;count=rows.size();result=new Sample(width);
            if(count==0)throw new IllegalArgumentException("Empty track");
            data=new float[count*width];
            for(int i=0;i<count;i++){
                var row=rows.get(i).getAsJsonArray();
                if(row.size()!=width)throw new IllegalArgumentException("Invalid frame width");
                for(int j=0;j<width;j++){
                    float value=row.get(j).getAsFloat();
                    if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite frame value");
                    data[i*width+j]=value;
                }
                if(i>0&&data[i*width]<data[(i-1)*width])throw new IllegalArgumentException("Unordered frames");
            }
        }
        Sample sample(float t){
            // Upper bound selects the last duplicate exactly at a camera cut.
            int lo=0,hi=count;
            while(lo<hi){int mid=(lo+hi)>>>1;if(data[mid*width]<=t)lo=mid+1;else hi=mid;}
            int a=Math.max(0,lo-1)*width,b=Math.min(count-1,lo)*width;
            float span=data[b]-data[a],u=t<data[0]||span<=0?0:Math.min(1,(t-data[a])/span);
            for(int j=0;j<width;j++)result.values[j]=data[a+j]+(data[b+j]-data[a+j])*u;
            return result;
        }
    }
}
