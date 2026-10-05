package com.sourceofmystery.particle;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义粒子。GOLD_MOTE / DARK_MOTE 是"汇聚"粒子：从生成点出发，在寿命内滑向 生成点 + 速度参数 的位置。
 * 服务端用 sendParticles(type, 起点, count=0, 偏移xyz, speed=1) 发送，偏移就是终点相对起点的位移；
 * 偏移为 0 时粒子原地淡出（用作拖尾）。
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SourceOfMystery.MOD_ID);

    /** 金色光点：神威天道召唤倒计时期间汇入祭坛、法阵周围的光 */
    public static final RegistryObject<SimpleParticleType> GOLD_MOTE =
            PARTICLES.register("gold_mote", () -> new SimpleParticleType(true));

    /** 黑色烟尘：神威天道大招凝聚黑色球体时向球体靠拢 */
    public static final RegistryObject<SimpleParticleType> DARK_MOTE =
            PARTICLES.register("dark_mote", () -> new SimpleParticleType(true));

    /** 骷髅头：泣死之主背后的神环偶尔冒出来，缓缓上飘、淡出（速度参数为漂移速度） */
    public static final RegistryObject<SimpleParticleType> DEATH_SKULL =
            PARTICLES.register("death_skull", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
