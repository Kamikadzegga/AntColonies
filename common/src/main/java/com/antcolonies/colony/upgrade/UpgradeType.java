package com.antcolonies.colony.upgrade;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** The ten generic upgrades. Each comes in tiers 1-3; several in one block add up. */
public enum UpgradeType implements StringRepresentable {
    /** Faster work at working chambers, Genetic Laboratory processing, larvae growth in Brood Chambers. */
    SPEED("speed"),
    /** Less food eaten by the ants of the structure (or, in a Terrarium, the whole colony). */
    FOOD_EFFICIENCY("food_efficiency"),
    /** More offline catch-up for the colony (Terrarium only): the numbers are worked out in bigger steps, no entities. */
    SIMULATION("simulation"),
    /** More slots in Storage chambers, more food in Storage. */
    STORAGE("storage"),
    /** Faster egg laying (Brood Chamber, Terrarium) and Queen rearing (Queen Chamber). */
    BREEDING("breeding"),
    /** Cheaper Genetic Laboratory work and more mutations in Queen Chambers. */
    GENETIC("genetic"),
    /** Stronger soldiers at Hunting Chambers and Housing. */
    SOLDIER("soldier"),
    /** Storage pushes its finds into the container below. */
    AUTOMATION("automation"),
    /** More ants housed (Terrarium, Housing) or working at one working chamber. */
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
