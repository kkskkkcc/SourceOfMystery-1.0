package com.sourceofmystery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import java.io.*;
import java.util.*;

/** Bake arbitrary GLB triangles into GeckoLib quads, preserving all source normals and UVs. */
final class DivineThroneModel extends GeoModel<DivineCGThrone> {
    final Map<GeoQuad,Vector3f[]> normals=new IdentityHashMap<>();
    private BakedGeoModel populated;
    private static ResourceLocation resource(String path){return new ResourceLocation("sourceofmystery",path);}
    @Override public ResourceLocation getModelResource(DivineCGThrone throne){return resource("geo/cinematic/divine_throne.geo.json");}
    @Override public ResourceLocation getTextureResource(DivineCGThrone throne){return DivineThroneMaterial.texture();}
    @Override public ResourceLocation getAnimationResource(DivineCGThrone throne){return resource("animations/cinematic/divine_throne.animation.json");}
    @Override public BakedGeoModel getBakedModel(ResourceLocation location){
        BakedGeoModel baked=super.getBakedModel(location);if(baked==populated)return baked;
        var root=baked.topLevelBones().get(0);root.getCubes().clear();normals.clear();
        try(var in=new DataInputStream(Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource("geo/cinematic/divine_throne.mesh")).open())){
            if(in.readInt()!=0x534f4d54||in.readInt()!=1)throw new IOException("Unknown throne mesh stream");
            int count=in.readInt();if(count<3||count>300000||count%3!=0)throw new IOException("Invalid throne vertex count");
            for(int i=0;i<count;i+=3){
                GeoVertex[] vertices=new GeoVertex[4];Vector3f[] directions=new Vector3f[4];
                for(int j=0;j<3;j++){
                    Vector3f position=new Vector3f(in.readFloat(),in.readFloat(),in.readFloat());
                    directions[j]=new Vector3f(in.readFloat(),in.readFloat(),in.readFloat());
                    vertices[j]=new GeoVertex(position,in.readFloat(),in.readFloat());
                }
                // Minecraft's entity pass is quads: duplicate the last vertex for a degenerate second triangle.
                vertices[3]=vertices[2];directions[3]=directions[2];
                var face=new GeoQuad(vertices,new Vector3f(directions[0]),Direction.UP);normals.put(face,directions);
                root.getCubes().add(new GeoCube(new GeoQuad[]{face},Vec3.ZERO,Vec3.ZERO,new Vec3(1,1,1),0,false));
            }
            if(in.read()!=-1)throw new IOException("Unexpected throne mesh payload");
        }catch(IOException e){throw new IllegalStateException("Unable to bake supplied throne",e);}
        populated=baked;return baked;
    }
}
