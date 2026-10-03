package com.sourceofmystery.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 汇聚粒子：在寿命内从起点平滑滑到 起点 + (xd, yd, zd)，越接近终点越小；偏移为 0 时原地缩小淡出。
 * 金色版本自发光，黑色版本是不透明的浓烟。
 */
public class ConvergeParticle extends TextureSheetParticle {

    private final double startX;
    private final double startY;
    private final double startZ;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final boolean glow;
    private final float baseSize;
    private final SpriteSet sprites;

    protected ConvergeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                               SpriteSet sprites, boolean glow) {
        super(level, x, y, z);
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.offsetX = dx;
        this.offsetY = dy;
        this.offsetZ = dz;
        this.glow = glow;
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = 18 + this.random.nextInt(12);
        if (glow) {
            float shade = 0.85f + this.random.nextFloat() * 0.15f;
            this.setColor(1.0f * shade, 0.80f * shade, 0.30f * shade);
            this.baseSize = 0.18f + this.random.nextFloat() * 0.12f;
        } else {
            float shade = this.random.nextFloat() * 0.08f;
            this.setColor(0.06f + shade, 0.02f + shade * 0.5f, 0.10f + shade);
            this.baseSize = 0.35f + this.random.nextFloat() * 0.35f;
        }
        this.quadSize = this.baseSize;
        this.alpha = 0.0f;
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
        float ease = u * u * (3 - 2 * u);
        this.setPos(startX + offsetX * ease, startY + offsetY * ease, startZ + offsetZ * ease);
        this.alpha = Math.min(1.0f, u * 5.0f) * (glow ? 1.0f : 0.85f);
        this.quadSize = this.baseSize * (1.0f - 0.7f * u);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return glow ? 0xF000F0 : super.getLightColor(partialTick);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean glow;

        public Provider(SpriteSet sprites, boolean glow) {
            this.sprites = sprites;
            this.glow = glow;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ConvergeParticle(level, x, y, z, xd, yd, zd, sprites, glow);
        }
    }
}
