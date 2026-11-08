package com.antcolonies.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The mod's particles, all tinted by their option's color: soil and crumbs fall, sparkles twinkle and glow, genetic
 * glints spiral up, and an evolution's light swells and fades.
 */
public class AntParticle extends SingleQuadParticle {
    public enum Kind {
        SOIL, CRUMB, SPARKLE, GENE, EVOLUTION
    }

    private final SpriteSet sprites;
    private final Kind kind;
    private final float baseSize;
    private final float spin;

    protected AntParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.spin = (random.nextFloat() - 0.5F) * 0.2F;
        switch (kind) {
            case SOIL -> {
                lifetime = 14 + random.nextInt(10);
                quadSize = 0.035F + random.nextFloat() * 0.03F;
                gravity = 0.6F;
                hasPhysics = true;
            }
            case CRUMB -> {
                lifetime = 18 + random.nextInt(12);
                quadSize = 0.03F + random.nextFloat() * 0.02F;
                gravity = 0.4F;
                hasPhysics = true;
            }
            case SPARKLE -> {
                lifetime = 20 + random.nextInt(20);
                quadSize = 0.05F + random.nextFloat() * 0.05F;
                gravity = -0.01F;
                friction = 0.9F;
            }
            case GENE -> {
                lifetime = 30 + random.nextInt(20);
                quadSize = 0.06F + random.nextFloat() * 0.04F;
                gravity = -0.02F;
                friction = 0.95F;
            }
            case EVOLUTION -> {
                lifetime = 40 + random.nextInt(20);
                quadSize = 0.1F + random.nextFloat() * 0.08F;
                gravity = -0.015F;
                friction = 0.92F;
            }
        }
        baseSize = quadSize;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        setSpriteFromAge(sprites);
        oRoll = roll;
        roll += spin;
        float life = age / (float) lifetime;
        switch (kind) {
            case SPARKLE -> alpha = 0.5F + 0.5F * Mth.sin(age * 0.8F);
            case GENE -> {
                // Spiral up.
                xd += Mth.cos(age * 0.4F) * 0.002;
                zd += Mth.sin(age * 0.4F) * 0.002;
                alpha = 1 - life;
            }
            case EVOLUTION -> {
                quadSize = baseSize * (1 + life * 1.5F);
                alpha = 1 - life * life;
            }
            default -> alpha = 1 - life * 0.8F;
        }
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return kind == Kind.SOIL || kind == Kind.CRUMB ? super.getLightCoords(partialTick) : LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                       RandomSource random) {
            AntParticle particle = new AntParticle(level, x, y, z, xd, yd, zd, sprites, kind);
            particle.setColor(options.getRed(), options.getGreen(), options.getBlue());
            return particle;
        }
    }
}
