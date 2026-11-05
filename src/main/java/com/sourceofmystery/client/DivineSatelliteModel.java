package com.sourceofmystery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import java.io.*;
import java.util.*;

/** Smooth, UV-mapped mesh baked into GeckoLib; uses the same supported adapter as the CG throne. */
final class DivineSatelliteModel extends GeoModel<DivineSatelliteVisual> {
    private final Map<ResourceLocation,BakedGeoModel> populated=new HashMap<>();
    final Map<GeoQuad,Vector3f[]> normals=new IdentityHashMap<>();
    static ResourceLocation resource(String path){return new ResourceLocation("sourceofmystery",path);}
    @Override public ResourceLocation getModelResource(DivineSatelliteVisual visual){return resource("geo/effects/divine_satellite_star.geo.json");}
    @Override public ResourceLocation getTextureResource(DivineSatelliteVisual visual){return resource("textures/effects/divine_satellites/"+visual.variant+".png");}
    @Override public ResourceLocation getAnimationResource(DivineSatelliteVisual visual){return resource("animations/effects/divine_satellites.animation.json");}
    @Override public BakedGeoModel getBakedModel(ResourceLocation location){
        BakedGeoModel baked=super.getBakedModel(location);if(populated.get(location)==baked)return baked;
        var root=baked.topLevelBones().get(0);
        for(var cube:root.getCubes())for(var quad:cube.quads())normals.remove(quad);
        root.getCubes().clear();
        String mesh=location.getPath().replace(".geo.json",".mesh");
        try(var in=new DataInputStream(Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource(mesh)).open())){
            if(in.readInt()!=0x534f4d54||in.readInt()!=1)throw new IOException("Unknown satellite mesh");
            int count=in.readInt();if(count<3||count>30000||count%3!=0)throw new IOException("Invalid satellite mesh vertex count");
            List<GeoQuad> quads=new ArrayList<>(count/3);
            for(int i=0;i<count;i+=3){
                GeoVertex[] vertices=new GeoVertex[4];Vector3f[] directions=new Vector3f[4];
                for(int j=0;j<3;j++){
                    var p=new Vector3f(in.readFloat(),in.readFloat(),in.readFloat());
                    directions[j]=new Vector3f(in.readFloat(),in.readFloat(),in.readFloat());
                    vertices[j]=new GeoVertex(p,in.readFloat(),in.readFloat());
                }
                vertices[3]=vertices[2];directions[3]=directions[2];
                var quad=new GeoQuad(vertices,new Vector3f(directions[0]),Direction.UP);normals.put(quad,directions);
                quads.add(quad);
            }
            root.getCubes().add(new GeoCube(quads.toArray(GeoQuad[]::new),Vec3.ZERO,Vec3.ZERO,new Vec3(1,1,1),0,false));
            if(in.read()!=-1)throw new IOException("Unexpected satellite payload");
        }catch(IOException ex){throw new IllegalStateException("Unable to bake divine satellite",ex);}
        populated.put(location,baked);return baked;
    }
}
