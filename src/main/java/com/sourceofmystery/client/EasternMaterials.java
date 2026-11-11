package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Known NEW_ENTITY programs keep Oculus compatibility and real effect depth. */
final class EasternMaterials {
    private static final Map<String,RenderType> TYPES=new HashMap<>();
    private static final class States extends RenderStateShard {
        static final TransparencyStateShard ALPHA=TRANSLUCENT_TRANSPARENCY,OPAQUE=NO_TRANSPARENCY;
        static final CullStateShard CULL=NO_CULL;
        static final WriteMaskStateShard DEPTH=COLOR_DEPTH_WRITE;
        static final DepthTestStateShard TEST=LEQUAL_DEPTH_TEST;
        static final LightmapStateShard LIGHT=LIGHTMAP;
        static final OverlayStateShard OVER=OVERLAY;
        private States(){super("eastern_rite",()->{},()->{});}
    }
    static RenderType material(ResourceLocation texture,boolean emissive,boolean opaque){
        var key=texture+":"+emissive+":"+opaque;
        return TYPES.computeIfAbsent(key,k->RenderType.create("som_eastern_"+texture.getPath()+"_"+emissive+"_"+opaque,
            DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,!opaque&&!texture.getPath().endsWith("/particle.png"),
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(emissive?GameRenderer::getRendertypeEntityTranslucentEmissiveShader:GameRenderer::getRendertypeEntityTranslucentShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture,true,false))
                .setTransparencyState(opaque?States.OPAQUE:States.ALPHA).setCullState(States.CULL)
                .setWriteMaskState(States.DEPTH).setDepthTestState(States.TEST)
                .setLightmapState(States.LIGHT).setOverlayState(States.OVER).createCompositeState(false)));
    }
    private EasternMaterials(){}
}
