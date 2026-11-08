package com.antcolonies.colony.model;

import java.util.EnumSet;
import java.util.Set;

import com.antcolonies.colony.upgrade.UpgradeType;
import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The colony chambers. A colony is a 3x3 ring: the Terrarium in the middle and eight chambers around it on the same
 * level, of any kinds in any mix. Each kind is one block; what it does is worked out by the colony's simulation.
 *
 * @param job       which ants it puts to work, if any
 * @param slots     its own item slots
 * @param upgrades  its upgrade slots
 * @param allowed   the generic upgrades it takes (species upgrades go into Foraging Chambers)
 */
public enum StructureKind implements StringRepresentable {
    /** Egg laying at full speed, faster larvae, room for larvae. */
    BROOD_CHAMBER("brood_chamber", Job.NONE, 0, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.SPEED, UpgradeType.POPULATION)),
    /** Houses adults, workers and soldiers; a colony needs one to raise soldiers. */
    HOUSING("housing", Job.NONE, 0, 2, EnumSet.of(UpgradeType.POPULATION, UpgradeType.SOLDIER)),
    /** Holds what the colony finds, and food room. */
    STORAGE("storage", Job.NONE, 54, 2, EnumSet.of(UpgradeType.STORAGE, UpgradeType.AUTOMATION)),
    /** Workers find the species' resources. */
    FORAGING_CHAMBER("foraging_chamber", Job.WORKERS, 0, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** Farmer Ants grow food and harvest. */
    FUNGUS_GARDEN("fungus_garden", Job.FARMERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** Soldiers hunt for mob drops. */
    HUNTING_CHAMBER("hunting_chamber", Job.SOLDIERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.SOLDIER, UpgradeType.FOOD_EFFICIENCY,
            UpgradeType.POPULATION)),
    /** Raises new Queens, and hybrids when a Genetic Sample is crossed in. */
    QUEEN_CHAMBER("queen_chamber", Job.NONE, 7, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.GENETIC, UpgradeType.SPEED));

    public static final Codec<StructureKind> CODEC = StringRepresentable.fromEnum(StructureKind::values);

    /** Queen Chamber slots: the rearing materials, an empty Test Tube, a Genetic Sample to cross in, and the new Queen. */
    public static final int QUEEN_CHAMBER_MATERIALS = 4;
    public static final int QUEEN_CHAMBER_TUBE = 4;
    public static final int QUEEN_CHAMBER_SAMPLE = 5;
    public static final int QUEEN_CHAMBER_OUTPUT = 6;

    public enum Job {
        NONE, WORKERS, SOLDIERS, FARMERS;

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
        return this == FORAGING_CHAMBER;
    }

    public String translationKey() {
        return "block.antcolonies." + name;
    }
}
