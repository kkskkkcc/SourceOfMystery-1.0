package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.lang.reflect.Method;

/** Soft wind opacity must not inherit the entity program's hard alpha cutoff or relighting. */
@Mod.EventBusSubscriber(modid="sourceofmystery",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WeatherShaders {
    private static ShaderInstance wind;
    public static ShaderInstance wind(){return wind;}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException {
        event.registerShader(new WindShader(event.getResourceProvider()),shader->wind=shader);
    }
    private static final class WindShader extends ShaderInstance {
        private static Method unlock,blend;
        static{
            try{
                unlock=Class.forName("net.irisshaders.iris.gl.blending.DepthColorStorage").getMethod("unlockDepthColor");
                blend=Class.forName("net.irisshaders.iris.gl.blending.BlendModeOverride").getMethod("restore");
            }catch(ReflectiveOperationException|LinkageError ignored){unlock=null;}
        }
        WindShader(net.minecraft.server.packs.resources.ResourceProvider provider)throws IOException{
            super(provider,new ResourceLocation("sourceofmystery","weather_wind"),DefaultVertexFormat.POSITION_COLOR_TEX);
        }
        @Override public void apply(){
            super.apply();
            // As with the existing ritual compositor, explicitly selected Iris
            // targets permit our own depth-tested material instead of color masks
            // that Iris uses to suppress unknown vanilla entity programs.
            if(unlock!=null&&OutsiderShaderCompat.active())try{unlock.invoke(null);blend.invoke(null);}catch(ReflectiveOperationException|LinkageError ignored){}
        }
    }
}
