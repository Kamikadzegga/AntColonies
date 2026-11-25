package com.antcolonies.colony.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.colony.upgrade.UpgradeType;
import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The colony chambers: additions that join a Terrarium when they touch it or another chamber of its colony.
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
    /** Hunter species kill mobs here: their loot, and food from what they catch; a small store for when the Storage is full. */
    HUNTING_GROUND("hunting_ground", Job.HUNTING, 9, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.HUNTING, UpgradeType.FOOD_EFFICIENCY,
            UpgradeType.POPULATION)),
    /** Farmer Ants grow food and harvest. */
    HARVESTING_SITE("harvesting_site", Job.FARMERS, 0, 3, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** More room for what the colony finds, and food room. */
    STORAGE("storage", Job.NONE, 162, 2, EnumSet.of(UpgradeType.STORAGE, UpgradeType.AUTOMATION)),
    /** Raises new Queens, and hybrids when a Genetic Sample is crossed in. */
    QUEEN_CHAMBER("queen_chamber", Job.NONE, 10, 2, EnumSet.of(UpgradeType.BREEDING, UpgradeType.GENETIC, UpgradeType.SPEED)),
    /** Nether-adapted ants go on simulated expeditions from here; other ants leave it alone. */
    NETHER_FORAGING_SITE("nether_foraging_site", Job.FORAGING, 9, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY,
            UpgradeType.POPULATION)),
    /** The same for End-adapted ants. */
    END_FORAGING_SITE("end_foraging_site", Job.FORAGING, 9, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION)),
    /** Links its colony to the others on its channel, to share food and the items its filter (its slots) names. */
    COLONY_CONNECTOR("colony_connector", Job.NONE, 9, 0, EnumSet.noneOf(UpgradeType.class)),
    /** Liquid Ants give droplets of their fluid here; a small store for when the Storage is full. */
    LIQUID_CHAMBER("liquid_chamber", Job.LIQUID, 9, 4, EnumSet.of(UpgradeType.SPEED, UpgradeType.FOOD_EFFICIENCY, UpgradeType.POPULATION));

    public static final Codec<StructureKind> CODEC = StringRepresentable.fromEnum(StructureKind::values);

    /** Queen Chamber slots: the rearing materials, an empty Test Tube, Genetic Samples to cross in, and the new Queen. */
    public static final int QUEEN_CHAMBER_MATERIALS = 4;
    public static final int QUEEN_CHAMBER_TUBE = 4;
    /** The first sample slot. */
    public static final int QUEEN_CHAMBER_SAMPLE = 5;
    public static final int QUEEN_CHAMBER_OUTPUT = 6;
    /** Every sample slot, in the order they are used. The later ones come after the output: older chambers had one. */
    public static final int[] QUEEN_CHAMBER_SAMPLES = {QUEEN_CHAMBER_SAMPLE, 7, 8, 9};

    public static boolean isQueenChamberSample(int slot) {
        return slot == QUEEN_CHAMBER_SAMPLE || slot > QUEEN_CHAMBER_OUTPUT && slot <= QUEEN_CHAMBER_SAMPLES[QUEEN_CHAMBER_SAMPLES.length - 1];
    }

    public enum Job {
        NONE, FORAGING, FARMERS, HUNTING, LIQUID;

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

    public boolean takesSpeciesUpgrades() {
        return this == FORAGING_SITE;
    }

    public boolean stores() {
        return this == STORAGE || siteStore();
    }

    /** The job sites with a small store of their own for what their ants bring: Foraging Sites, Liquid Chamber, Hunting Ground. */
    public boolean siteStore() {
        return foraging() || this == LIQUID_CHAMBER || this == HUNTING_GROUND;
    }

    public boolean foraging() {
        return job == Job.FORAGING;
    }

    /** The Nether and End Foraging Sites: their finds come from an expedition table, not the species' own outputs. */
    public boolean expedition() {
        return this == NETHER_FORAGING_SITE || this == END_FORAGING_SITE;
    }

    /** All the Foraging Site kinds, in the order the colony hands out its workers to them. */
    public static final List<StructureKind> FORAGING_KINDS = List.of(FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE);

    /** Every job site with a store of its own ({@link #siteStore}), in the order finds overflow into them. */
    public static final List<StructureKind> SITE_STORES =
            List.of(FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE, LIQUID_CHAMBER, HUNTING_GROUND);

    public String translationKey() {
        return "block.antcolonies." + name;
    }
}
