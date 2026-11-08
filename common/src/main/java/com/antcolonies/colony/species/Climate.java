package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Where a species thrives. A colony kept somewhere else (a Snow Ant Terrarium in a desert) loses health, less so the
 * better its Queen's Environmental Resistance, and not at all with a matching resistance mutation.
 */
public enum Climate implements StringRepresentable {
    TEMPERATE("temperate"),
    COLD("cold"),
    HOT("hot"),
    UNDERGROUND("underground"),
    NETHER("nether"),
    ANY("any");

    public static final Codec<Climate> CODEC = StringRepresentable.fromEnum(Climate::values);

    private final String name;

    Climate(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * How badly a place suits the species, 0 (fine) to 1 (hostile).
     *
     * @param temperature the biome's base temperature
     * @param underground whether the Terrarium has no sky above it
     * @param nether      whether it stands in an ultra-warm dimension
     */
    public double mismatch(float temperature, boolean underground, boolean nether) {
        return switch (this) {
            case ANY -> 0;
            case TEMPERATE -> nether ? 1 : temperature < 0.15F || temperature > 1.5F ? 0.6 : 0;
            case COLD -> nether ? 1 : temperature < 0.2F ? 0 : temperature < 0.8F ? 0.4 : 1;
            case HOT -> temperature > 1.4F || nether ? 0 : temperature > 0.8F ? 0.3 : 0.7;
            case UNDERGROUND -> underground ? 0 : 0.5;
            case NETHER -> nether ? 0 : temperature > 1.4F ? 0.4 : 1;
        };
    }

    /** Whether the place is too cold (rather than too hot) for the species, for resistance mutations. */
    public boolean tooCold(float temperature, boolean nether) {
        return switch (this) {
            case HOT, NETHER -> !nether;
            case COLD -> false;
            default -> !nether && temperature < 0.5F;
        };
    }
}
