package com.antcolonies.colony.upgrade;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** The ten generic upgrades. Each comes in tiers 1-3; several in one block add up. */
public enum UpgradeType implements StringRepresentable {
    /** Faster work at job sites, Genetic Laboratory processing, larvae growth in Nurseries. */
    SPEED("speed"),
    /** Less food eaten by the ants of the structure (or, in a Terrarium, the whole colony). */
    FOOD_EFFICIENCY("food_efficiency"),
    /** More offline catch-up for the colony (Terrarium only): the numbers are worked out in bigger steps, no entities. */
    SIMULATION("simulation"),
    /** More slots in Resource Piles, more food in Food Storage. */
    STORAGE("storage"),
    /** Faster egg laying (Breeding Chamber, Terrarium) and Queen rearing (Queen Chamber). */
    BREEDING("breeding"),
    /** Cheaper Genetic Laboratory work and more mutations in Queen Chambers. */
    GENETIC("genetic"),
    /** Stronger soldiers at Fighting Sites and Soldier Barracks. */
    SOLDIER("soldier"),
    /** Resource Piles push into the container below; Food Storage pulls food from the container above. */
    AUTOMATION("automation"),
    /** More ants housed (Terrarium, housing, barracks) or working at one job site. */
    POPULATION("population"),
    /** Better evolution chances (Terrarium only). */
    EVOLUTION("evolution");

    public static final Codec<UpgradeType> CODEC = StringRepresentable.fromEnum(UpgradeType::values);
    public static final int MAX_TIER = 3;

    private final String name;

    UpgradeType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String itemName(int tier) {
        return name + "_upgrade_" + tier;
    }
}
