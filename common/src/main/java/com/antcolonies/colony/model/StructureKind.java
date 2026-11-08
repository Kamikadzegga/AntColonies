package com.antcolonies.colony.model;

import java.util.EnumSet;
import java.util.Set;

import com.antcolonies.colony.upgrade.UpgradeType;
import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The colony structures a Terrarium can connect to through tubes and tunnels. Each kind is one block; what it does is
 * worked out by the colony's simulation.
 *
 * @param job       which ants it puts to work, if any
 * @param slots     its own item slots
 * @param upgrades  its upgrade slots
 * @param allowed   the generic upgrades it takes (species upgrades go into job sites of workers)
 */
public enum StructureKind implements StringRepresentable {
    BREEDING_CHAMBER("breeding_chamber", Job.NONE, 0, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.SPEED)),
    SCAVENGING_SITE("scavenging_site", Job.WORKERS, 0, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    RESOURCE_PILE("resource_pile", Job.NONE, 54, 2, EnumSet.of(UpgradeType.STORAGE, UpgradeType.AUTOMATION)),
    FIGHTING_SITE("fighting_site", Job.SOLDIERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.SOLDIER, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    FARMER_GARDEN("farmer_garden", Job.FARMERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    FOOD_STORAGE("food_storage", Job.NONE, 9, 2, EnumSet.of(UpgradeType.STORAGE, UpgradeType.AUTOMATION)),
    NURSERY("nursery", Job.NONE, 0, 2, EnumSet.of(UpgradeType.SPEED, UpgradeType.POPULATION)),
    QUEEN_CHAMBER("queen_chamber", Job.NONE, 7, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.GENETIC, UpgradeType.SPEED)),
    WORKER_HOUSING("worker_housing", Job.NONE, 0, 2, EnumSet.of(UpgradeType.POPULATION)),
    SOLDIER_BARRACKS("soldier_barracks", Job.NONE, 0, 2, EnumSet.of(UpgradeType.POPULATION, UpgradeType.SOLDIER)),
    PROCESSING_CHAMBER("processing_chamber", Job.WORKERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    COLONY_CONNECTOR("colony_connector", Job.NONE, 0, 0, EnumSet.noneOf(UpgradeType.class));

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
        return this == SCAVENGING_SITE;
    }

    public String translationKey() {
        return "block.antcolonies." + name;
    }
}
