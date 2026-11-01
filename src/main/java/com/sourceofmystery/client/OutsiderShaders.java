package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;

@Mod.EventBusSubscriber(modid="sourceofmystery",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class OutsiderShaders {
    private static ShaderInstance model;
    private static ShaderInstance halo;
    private static ShaderInstance ritual;
    private static ShaderInstance ritualDepth;
    public static ShaderInstance model(){return model!=null?model:net.minecraft.client.renderer.GameRenderer.getRendertypeEntityCutoutNoCullShader();}
    public static ShaderInstance halo(){return halo!=null?halo:net.minecraft.client.renderer.GameRenderer.getPositionColorTexShader();}
    public static ShaderInstance ritual(){return ritual;}
    public static ShaderInstance ritualDepth(){return ritualDepth;}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException {
        OutsiderShaderCompat.reload();
        OutsiderRitualComposite.reload();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("sourceofmystery","outsider"),
                DefaultVertexFormat.NEW_ENTITY),shader->{model=shader;
                    com.sourceofmystery.SourceOfMystery.LOGGER.info("Outsider lighting shader loaded");});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("sourceofmystery","outsider_halo"),
                DefaultVertexFormat.POSITION_COLOR_TEX),shader->{halo=shader;
                    com.sourceofmystery.SourceOfMystery.LOGGER.info("Outsider monochrome back halo shader loaded");});
        event.registerShader(new OutsiderRitualComposite.RitualShader(event.getResourceProvider(),"outsider_ritual_composite"),shader->{ritual=shader;});
        event.registerShader(new OutsiderRitualComposite.RitualShader(event.getResourceProvider(),"outsider_ritual_depth"),shader->{ritualDepth=shader;});
    }
}
