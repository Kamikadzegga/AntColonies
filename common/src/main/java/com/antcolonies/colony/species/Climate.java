package com.antcolonies.colony.species;

import com.antcolonies.AntColonies;
import com.mojang.serialization.Codec;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where a species thrives. A colony kept somewhere else (a Snow Ant Terrarium in a desert) loses health, less so the
 * better its Queen's Environmental Resistance, and not at all with a matching resistance mutation. A block of the
 * climate under the Terrarium (block tag {@code antcolonies:climate/<name>}: snow or ice for cold, sand for hot...) makes
 * any place right for species of that climate.
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

    /** The blocks that make a Terrarium standing on them this climate. */
    public TagKey<Block> floorTag() {
        return TagKey.create(Registries.BLOCK, AntColonies.id("climate/" + name));
    }

    /** The climate a block under a Terrarium gives it, if any. */
    public static @Nullable Climate ofFloor(BlockState state) {
        for (Climate climate : values()) {
            if (climate != ANY && state.is(climate.floorTag())) {
                return climate;
            }
        }
        return null;
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
