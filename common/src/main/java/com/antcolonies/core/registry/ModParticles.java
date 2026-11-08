package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Colored particles: soil and dust, food crumbs, crystal sparkles, genetic glints and the glow of an evolution. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, AntColonies.MODID);

    public static final Supplier<ParticleType<ColorParticleOption>> SOIL = colored("soil");
    public static final Supplier<ParticleType<ColorParticleOption>> CRUMB = colored("crumb");
    public static final Supplier<ParticleType<ColorParticleOption>> SPARKLE = colored("sparkle");
    public static final Supplier<ParticleType<ColorParticleOption>> GENE = colored("gene");
    public static final Supplier<ParticleType<ColorParticleOption>> EVOLUTION = colored("evolution");

    private static Supplier<ParticleType<ColorParticleOption>> colored(String name) {
        return PARTICLE_TYPES.register(name, () -> new ParticleType<ColorParticleOption>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        });
    }

    private ModParticles() {}
}
