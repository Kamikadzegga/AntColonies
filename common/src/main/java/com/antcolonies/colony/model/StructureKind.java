package com.antcolonies.colony.model;

import java.util.EnumSet;
import java.util.Set;

import com.antcolonies.colony.upgrade.UpgradeType;
import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The colony chambers. A Terrarium works on its own; chambers are additions that join it when they touch it or another
 * chamber of its colony, in any shape. Each kind is one block; what it does is worked out by the colony's simulation.
 *
 * @param job       which ants it puts to work, if any
 * @param slots     its own item slots
 * @param upgrades  its upgrade slots
 * @param allowed   the generic upgrades it takes (species upgrades go into Foraging Sites)
 */
public enum StructureKind implements StringRepresentable {
    /** Brood and housing: eggs at full speed, faster larvae, room for larvae and adults. */
    NEST_CHAMBER("nest_chamber", Job.NONE, 0, 3, EnumSet.of(UpgradeType.BREEDING, UpgradeType.SPEED, UpgradeType.POPULATION)),
    /** Workers find the species' resources; a small store for when the Storage is full. */
    FORAGING_SITE("foraging_site", Job.FORAGING, 9, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** Hunter species kill mobs here: their loot, and food from what they catch. */
    HUNTING_GROUND("hunting_ground", Job.HUNTING, 0, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.HUNTING, UpgradeType.FOOD_EFFICIENCY,
            UpgradeType.POPULATION)),
    /** Farmer Ants grow food and harvest. */
    HARVESTING_SITE("harvesting_site", Job.FARMERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** More room for what the colony finds, and food room. */
    STORAGE("storage", Job.NONE, 162, 2, EnumSet.of(UpgradeType.STORAGE, UpgradeType.AUTOMATION)),
    /** Raises new Queens, and hybrids when a Genetic Sample is crossed in. */
    QUEEN_CHAMBER("queen_chamber", Job.NONE, 7, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.GENETIC, UpgradeType.SPEED));

    public static final Codec<StructureKind> CODEC = StringRepresentable.fromEnum(StructureKind::values);

    /** Queen Chamber slots: the rearing materials, an empty Test Tube, a Genetic Sample to cross in, and the new Queen. */
    public static final int QUEEN_CHAMBER_MATERIALS = 4;
    public static final int QUEEN_CHAMBER_TUBE = 4;
    public static final int QUEEN_CHAMBER_SAMPLE = 5;
    public static final int QUEEN_CHAMBER_OUTPUT = 6;

    public enum Job {
        NONE, FORAGING, FARMERS, HUNTING;

        public boolean any() {
            return this != NONE;
        }
    }

    private final String name;
    private final Job job;
    private final int slots;
    private final int upgradeSlots;
    private final Set<UpgradeType> allowed;

    StructureKind(String name, Job job, int slots, int upgradeSlots, Set<UpgradeType> allowed) {
        this.name = name;
        this.job = job;
        this.slots = slots;
        this.upgradeSlots = upgradeSlots;
        this.allowed = allowed;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Job job() {
        return job;
    }

    public int slots() {
        return slots;
    }

    public int upgradeSlots() {
        return upgradeSlots;
    }

    public boolean allows(UpgradeType type) {
        return allowed.contains(type);
    }

    /** Whether species-specific upgrades fit (they change what workers find). */
    public boolean takesSpeciesUpgrades() {
        return this == FORAGING_SITE;
    }

    /** Whether finds can be kept here (Storage, and the Foraging Site's small store). */
    public boolean stores() {
        return this == STORAGE || this == FORAGING_SITE;
    }

    public String translationKey() {
        return "block.antcolonies." + name;
    }
}
