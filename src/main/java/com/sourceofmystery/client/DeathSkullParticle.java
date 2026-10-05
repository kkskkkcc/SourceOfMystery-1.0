package com.sourceofmystery.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 骷髅头粒子：泣死之主背后的神环偶尔冒出来。先张大、再一边左右摇晃一边缓缓上飘淡出；
 * 贴图（death_skull_0~3）是下颌一张一合的四帧。
 */
public class DeathSkullParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float baseSize;
    private final float swayPhase;

    protected DeathSkullParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = 40 + this.random.nextInt(20);
        this.baseSize = 0.28f + this.random.nextFloat() * 0.12f;
        this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.quadSize = 0;
        this.alpha = 0;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float u = this.age / (float) this.lifetime;
        float sway = Mth.sin(this.age * 0.25f + swayPhase) * 0.015f;
        this.move(this.xd + sway, this.yd, this.zd + sway * 0.5f);
        float grow = Math.min(1.0f, u * 6.0f);
        this.quadSize = this.baseSize * (0.6f + 0.4f * grow) * (1.0f + 0.3f * u);
        this.alpha = Math.min(1.0f, u * 8.0f) * (1.0f - u * u) * 0.9f;
        this.roll = Mth.sin(this.age * 0.15f + swayPhase) * 0.2f;
        this.oRoll = this.roll;
        // 下颌一张一合
        this.setSprite(this.sprites.get((this.age / 4) % 4, 3));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new DeathSkullParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
