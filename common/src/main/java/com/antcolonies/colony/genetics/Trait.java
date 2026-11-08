package com.antcolonies.colony.genetics;

import com.antcolonies.core.config.AntConfig;
import com.mojang.serialization.Codec;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/**
 * A Queen's ten genetic attributes. Each is a level from 1 to 10; the level turns into a multiplier between the
 * configured level 1 and maximum level effects, and every multiplier is used by the simulation.
 */
public enum Trait implements StringRepresentable {
    /** Scavenging finds per worker. */
    WORKER_PRODUCTIVITY("worker_productivity"),
    /** Chance each find comes with extra items. */
    GATHERING_EFFICIENCY("gathering_efficiency"),
    /** Divides the food every ant of the colony eats. */
    FOOD_EFFICIENCY("food_efficiency"),
    /** Eggs the Queen lays. */
    EGG_PRODUCTION("egg_production"),
    /** How fast larvae become adults. */
    LARVAE_GROWTH("larvae_growth"),
    /** Hunts per soldier at Fighting Sites, and fewer casualties. */
    SOLDIER_STRENGTH("soldier_strength"),
    /** Food grown per farmer. */
    FARMER_PRODUCTIVITY("farmer_productivity"),
    /** Mutation chance of daughter Queens, and evolution chance. */
    MUTATION_PROBABILITY("mutation_probability"),
    /** Less harm from the wrong climate and from starvation. */
    ENVIRONMENTAL_RESISTANCE("environmental_resistance"),
    /** More of the species' rare signature outputs. */
    SPECIALIZATION("specialization");

    public static final Codec<Trait> CODEC = StringRepresentable.fromEnum(Trait::values);
    public static final int MIN_LEVEL = 1;
    public static final int ABSOLUTE_MAX_LEVEL = 10;

    private final String name;

    Trait(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "trait.antcolonies." + name;
    }

    /** The configured highest level (the Genetic Laboratory stops there). */
    public static int maxLevel() {
        return Mth.clamp(AntConfig.get(AntConfig.MAX_TRAIT_LEVEL), 2, ABSOLUTE_MAX_LEVEL);
    }

    /** The effect of a level: level 1 gives the configured minimum, the maximum level the configured maximum. */
    public static double multiplier(int level) {
        double min = AntConfig.get(AntConfig.TRAIT_MIN_MULTIPLIER);
        double max = AntConfig.get(AntConfig.TRAIT_MAX_MULTIPLIER);
        int top = maxLevel();
        double t = (Mth.clamp(level, MIN_LEVEL, top) - MIN_LEVEL) / (double) (top - MIN_LEVEL);
        return min + (max - min) * t;
    }

    /** The name of a level's grade: "Basic" to "Perfect". */
    public static String gradeKey(int level) {
        return "grade.antcolonies." + Mth.clamp(level, MIN_LEVEL, ABSOLUTE_MAX_LEVEL);
    }
}
