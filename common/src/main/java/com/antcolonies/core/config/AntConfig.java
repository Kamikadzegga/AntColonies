package com.antcolonies.core.config;

import java.util.List;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every number the simulation uses. Server settings (antcolonies/server.toml) are the world's rules and are synced to
 * players, so GUIs and JEI show the same numbers the server uses. Client settings only change what is drawn.
 * Getters fall back to the defaults before the files are loaded (game tests, early world loading).
 */
public final class AntConfig {
    public static final String FOLDER = "antcolonies";
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- Simulation ---
    public static final ModConfigSpec.IntValue SIMULATION_INTERVAL;
    public static final ModConfigSpec.IntValue OFFLINE_CATCH_UP_MINUTES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SIMULATION_UPGRADE_MINUTES;
    public static final ModConfigSpec.LongValue MAX_COLONY_POPULATION;
    public static final ModConfigSpec.IntValue NETWORK_SCAN_LIMIT;

    // --- Population ---
    public static final ModConfigSpec.IntValue TERRARIUM_CAPACITY;
    public static final ModConfigSpec.IntValue WORKER_HOUSING_CAPACITY;
    public static final ModConfigSpec.IntValue BARRACKS_CAPACITY;
    public static final ModConfigSpec.IntValue NURSERY_LARVAE_CAPACITY;
    public static final ModConfigSpec.DoubleValue NURSERY_GROWTH_BONUS;
    public static final ModConfigSpec.DoubleValue BASE_EGGS_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue NO_BREEDING_CHAMBER_FACTOR;
    public static final ModConfigSpec.DoubleValue BREEDING_CHAMBER_BONUS;
    public static final ModConfigSpec.DoubleValue EGG_HATCH_MINUTES;
    public static final ModConfigSpec.DoubleValue LARVAE_GROW_MINUTES;
    public static final ModConfigSpec.DoubleValue MAINTENANCE_FRACTION;
    public static final ModConfigSpec.DoubleValue ADULT_LIFESPAN_MINUTES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STAGE_POPULATIONS;

    // --- Food ---
    public static final ModConfigSpec.DoubleValue QUEEN_FOOD;
    public static final ModConfigSpec.DoubleValue WORKER_FOOD;
    public static final ModConfigSpec.DoubleValue SOLDIER_FOOD;
    public static final ModConfigSpec.DoubleValue LARVA_FOOD;
    public static final ModConfigSpec.DoubleValue IDLE_ACTIVITY;
    public static final ModConfigSpec.IntValue TERRARIUM_FOOD_CAPACITY;
    public static final ModConfigSpec.IntValue FOOD_STORAGE_CAPACITY;
    public static final ModConfigSpec.DoubleValue STARVATION_GRACE_MINUTES;
    public static final ModConfigSpec.DoubleValue STARVATION_DECLINE;
    public static final ModConfigSpec.DoubleValue STARVED_FACTOR;
    public static final ModConfigSpec.BooleanValue STARVATION_KILLS_QUEEN;

    // --- Production ---
    public static final ModConfigSpec.DoubleValue POPULATION_EXPONENT;
    public static final ModConfigSpec.DoubleValue BONUS_EXPONENT;
    public static final ModConfigSpec.IntValue MAX_BENEFICIAL_MUTATIONS;
    public static final ModConfigSpec.IntValue SITE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SITE_CAPACITY_PER_UPGRADE;
    public static final ModConfigSpec.DoubleValue SCAVENGE_ROLLS;
    public static final ModConfigSpec.DoubleValue FARMER_FOOD;
    public static final ModConfigSpec.DoubleValue FARMER_BYPRODUCT_ROLLS;
    public static final ModConfigSpec.DoubleValue FIGHT_ROLLS;
    public static final ModConfigSpec.DoubleValue SOLDIER_CASUALTY_CHANCE;
    public static final ModConfigSpec.DoubleValue PROCESSING_PER_WORKER;
    public static final ModConfigSpec.DoubleValue SPEED_UPGRADE_BONUS;
    public static final ModConfigSpec.DoubleValue FOOD_UPGRADE_SAVING;
    public static final ModConfigSpec.DoubleValue SPECIES_UPGRADE_BONUS;
    public static final ModConfigSpec.DoubleValue RARE_FIND_MULTIPLIER;
    public static final ModConfigSpec.IntValue RESOURCE_PILE_SLOTS_PER_UPGRADE;

    // --- Genetics ---
    public static final ModConfigSpec.DoubleValue TRAIT_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TRAIT_MAX_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_TRAIT_LEVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STARTING_LEVEL_WEIGHTS;
    public static final ModConfigSpec.DoubleValue INHERITANCE_VARIATION;
    public static final ModConfigSpec.DoubleValue BASE_MUTATION_CHANCE;
    public static final ModConfigSpec.DoubleValue LAB_TIME_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LAB_COST_MULTIPLIER;

    // --- Evolution and breeding ---
    public static final ModConfigSpec.DoubleValue EVOLUTION_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HYBRID_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.IntValue QUEEN_REARING_MIN_POPULATION;

    // --- Wild ants ---
    public static final ModConfigSpec.DoubleValue WILD_SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue WILD_QUEEN_CHANCE;
    public static final ModConfigSpec.DoubleValue WILD_SOLDIER_CHANCE;
    public static final ModConfigSpec.DoubleValue NEST_QUEEN_CHANCE;
    public static final ModConfigSpec.IntValue NEST_COOLDOWN_SECONDS;

    // --- Guard Ants ---
    public static final ModConfigSpec.IntValue GUARD_SOLDIER_COST;

    // --- Connections ---
    public static final ModConfigSpec.DoubleValue CONNECTOR_FOOD_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue CONNECTOR_SHARES_STORAGE;
    public static final ModConfigSpec.IntValue GENERATOR_OUTPUT;
    public static final ModConfigSpec.IntValue GENERATOR_PUSH;

    // --- Client ---
    public static final ModConfigSpec.IntValue MAX_VISUAL_ANTS;
    public static final ModConfigSpec.BooleanValue TUBE_ANTS;
    public static final ModConfigSpec.BooleanValue STRUCTURE_ANTS;
    public static final ModConfigSpec.DoubleValue ANT_DETAIL_DISTANCE;
    public static final ModConfigSpec.BooleanValue ANT_3D_MODELS;
    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.BooleanValue AMBIENT_SOUNDS;
    public static final ModConfigSpec.DoubleValue VISUAL_DISTANCE;

    static {
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("simulation");
        SIMULATION_INTERVAL = s.comment("Ticks between colony simulation steps. Larger steps cost less and give the same totals.")
                .defineInRange("interval", 20, 5, 200);
        OFFLINE_CATCH_UP_MINUTES = s.comment("Minutes of simulation a colony catches up on when its Terrarium loads again after being unloaded.",
                        "Production never runs while unloaded; this is the most it can make up afterwards (0 = none).")
                .defineInRange("offlineCatchUpMinutes", 5, 0, 1440);
        SIMULATION_UPGRADE_MINUTES = s.comment("Extra catch-up minutes from a Simulation Upgrade of tier 1, 2 and 3.")
                .defineList("simulationUpgradeMinutes", List.of(15, 60, 240), () -> 15, o -> o instanceof Integer i && i >= 0);
        MAX_COLONY_POPULATION = s.comment("Hard cap on the adults of one colony, whatever its housing.")
                .defineInRange("maxColonyPopulation", 1_000_000L, 100L, 100_000_000L);
        NETWORK_SCAN_LIMIT = s.comment("Most blocks (tubes, tunnels, structures) one colony's network may hold.")
                .defineInRange("networkScanLimit", 1024, 16, 16384);
        s.pop();

        s.push("population");
        TERRARIUM_CAPACITY = s.comment("Adults a Terrarium houses on its own.").defineInRange("terrariumCapacity", 60, 1, 100000);
        WORKER_HOUSING_CAPACITY = s.comment("Adults each Worker Housing adds.").defineInRange("workerHousingCapacity", 2000, 1, 1000000);
        BARRACKS_CAPACITY = s.comment("Soldiers each Soldier Barracks houses. Without barracks a colony raises no soldiers.")
                .defineInRange("barracksCapacity", 1000, 1, 1000000);
        NURSERY_LARVAE_CAPACITY = s.comment("Larvae each Ant Nursery holds on top of the Terrarium's own room.")
                .defineInRange("nurseryLarvaeCapacity", 1500, 1, 1000000);
        NURSERY_GROWTH_BONUS = s.comment("Larvae growth speed added by each Ant Nursery (diminishing).").defineInRange("nurseryGrowthBonus", 0.5, 0, 10);
        BASE_EGGS_PER_MINUTE = s.comment("Eggs a Queen with average genetics lays per minute with one Breeding Chamber.")
                .defineInRange("baseEggsPerMinute", 12.0, 0.01, 10000);
        NO_BREEDING_CHAMBER_FACTOR = s.comment("Egg laying speed of a Queen without any Breeding Chamber.").defineInRange("noBreedingChamberFactor", 0.25, 0, 1);
        BREEDING_CHAMBER_BONUS = s.comment("Egg laying added by each Breeding Chamber after the first (diminishing).")
                .defineInRange("breedingChamberBonus", 0.75, 0, 10);
        EGG_HATCH_MINUTES = s.comment("Average minutes until an egg hatches into a larva.").defineInRange("eggHatchMinutes", 1.5, 0.05, 1000);
        LARVAE_GROW_MINUTES = s.comment("Average minutes until a larva becomes an adult (before genetics and nurseries).")
                .defineInRange("larvaeGrowMinutes", 3.0, 0.05, 1000);
        MAINTENANCE_FRACTION = s.comment("Share of a colony's adults that must stay home to clean tunnels and tend the brood.",
                        "Workers kept for maintenance are never assigned to a job; too few of them lower colony health.")
                .defineInRange("maintenanceFraction", 0.05, 0, 0.9);
        ADULT_LIFESPAN_MINUTES = s.comment("Average minutes an adult ant lives. A colony without a Queen dwindles; with one, eggs replace the dead.")
                .defineInRange("adultLifespanMinutes", 240.0, 1, 1000000);
        STAGE_POPULATIONS = s.comment("Adults a colony needs for stages 2, 3 and 4 (stage 2 also needs a Breeding Chamber, stage 3 a Nursery,",
                        "stage 4 a mineral or better species, or average genetics of 5 or more).")
                .defineList("stagePopulations", List.of(100, 1000, 5000), () -> 100, o -> o instanceof Integer i && i >= 0);
        s.pop();

        s.push("food");
        QUEEN_FOOD = s.comment("Food a Queen eats per minute.").defineInRange("queen", 2.0, 0, 10000);
        WORKER_FOOD = s.comment("Food a working worker eats per minute.").defineInRange("worker", 0.02, 0, 100);
        SOLDIER_FOOD = s.comment("Food a fighting soldier eats per minute.").defineInRange("soldier", 0.05, 0, 100);
        LARVA_FOOD = s.comment("Food a larva eats per minute.").defineInRange("larva", 0.015, 0, 100);
        IDLE_ACTIVITY = s.comment("How much an ant without a job eats, compared to a busy one.").defineInRange("idleActivity", 0.5, 0, 1);
        TERRARIUM_FOOD_CAPACITY = s.comment("Food a Terrarium stores on its own.").defineInRange("terrariumFoodCapacity", 2000, 1, Integer.MAX_VALUE);
        FOOD_STORAGE_CAPACITY = s.comment("Food each Food Storage adds.").defineInRange("foodStorageCapacity", 25000, 1, Integer.MAX_VALUE);
        STARVATION_GRACE_MINUTES = s.comment("Minutes without food before the population starts to die.").defineInRange("starvationGraceMinutes", 3.0, 0, 1000);
        STARVATION_DECLINE = s.comment("Share of the population lost per minute of starvation after the grace period.")
                .defineInRange("starvationDecline", 0.05, 0, 1);
        STARVED_FACTOR = s.comment("Work, egg laying and growth speed of a colony without food.").defineInRange("starvedFactor", 0.2, 0, 1);
        STARVATION_KILLS_QUEEN = s.comment("Whether a Queen dies once her colony has starved down to nothing.")
                .define("starvationKillsQueen", false);
        s.pop();

        s.push("production");
        POPULATION_EXPONENT = s.comment("Diminishing returns: n ants on one kind of job (all of a colony's sites of that kind together) work like n^exponent ants.")
                .defineInRange("populationExponent", 0.85, 0.1, 1.0);
        BONUS_EXPONENT = s.comment("Stacked bonuses (genetics x mutations x upgrades) above 1 count as bonus^exponent, so they can't multiply without end.",
                        "1 = no softening; 0.6 turns x20 into x6.")
                .defineInRange("bonusExponent", 0.6, 0.1, 1.0);
        SITE_BASE_CAPACITY = s.comment("Ants one job site takes before Population Upgrades.").defineInRange("siteBaseCapacity", 64, 1, 1000000);
        SITE_CAPACITY_PER_UPGRADE = s.comment("Ants each Population Upgrade tier adds to a job site.").defineInRange("siteCapacityPerUpgrade", 64, 0, 1000000);
        SCAVENGE_ROLLS = s.comment("Scavenging finds per minute per effective worker, before the species' own rate.")
                .defineInRange("scavengeRolls", 0.6, 0, 10000);
        FARMER_FOOD = s.comment("Food per minute per effective farmer, before the species' own food production.")
                .defineInRange("farmerFood", 1.2, 0, 10000);
        FARMER_BYPRODUCT_ROLLS = s.comment("Garden harvests (mushrooms, seeds, berries) per minute per effective farmer.")
                .defineInRange("farmerByproductRolls", 0.08, 0, 10000);
        FIGHT_ROLLS = s.comment("Simulated hunts per minute per effective soldier, before soldier strength.")
                .defineInRange("fightRolls", 0.05, 0, 10000);
        SOLDIER_CASUALTY_CHANCE = s.comment("Chance a hunt costs one soldier, before the soldiers' strength.")
                .defineInRange("soldierCasualtyChance", 0.03, 0, 1);
        PROCESSING_PER_WORKER = s.comment("Items a Resource Processing Chamber processes per minute per effective worker.")
                .defineInRange("processingPerWorker", 0.5, 0, 10000);
        SPEED_UPGRADE_BONUS = s.comment("Work speed added per Speed Upgrade tier.").defineInRange("speedUpgradeBonus", 0.25, 0, 100);
        FOOD_UPGRADE_SAVING = s.comment("Share of food saved per Food Efficiency Upgrade tier (at most 75% in total).")
                .defineInRange("foodUpgradeSaving", 0.12, 0, 1);
        SPECIES_UPGRADE_BONUS = s.comment("Strength of species-specific upgrades per tier.").defineInRange("speciesUpgradeBonus", 0.3, 0, 100);
        RARE_FIND_MULTIPLIER = s.comment("Multiplier on the chance of every rare (\"special\") find: diamonds, netherite scrap, shells, sponges, sniffer eggs...",
                        "0.5 halves them, 0 turns them off, 2 doubles them. Common finds are not changed (lower scavengeRolls for those).",
                        "Fighting Site loot comes from mob loot tables and is not affected (use fightRolls).")
                .defineInRange("rareFindMultiplier", 1.0, 0, 100);
        RESOURCE_PILE_SLOTS_PER_UPGRADE = s.comment("Resource Pile slots unlocked per Storage Upgrade tier (base 9, at most 54).")
                .defineInRange("resourcePileSlotsPerUpgrade", 9, 0, 45);
        s.pop();

        s.push("genetics");
        TRAIT_MIN_MULTIPLIER = s.comment("Effect of a level 1 trait.").defineInRange("level1Multiplier", 0.8, 0.01, 100);
        TRAIT_MAX_MULTIPLIER = s.comment("Effect of a trait at the maximum level.").defineInRange("maxLevelMultiplier", 2.0, 0.01, 1000);
        MAX_TRAIT_LEVEL = s.comment("Highest level the Genetic Laboratory can raise a trait to.").defineInRange("maxTraitLevel", 10, 2, 10);
        STARTING_LEVEL_WEIGHTS = s.comment("Weights of starting trait levels 1 to 10 for a new wild Queen.")
                .defineList("startingLevelWeights", List.of(30, 30, 18, 10, 6, 3, 2, 1, 0, 0), () -> 1, o -> o instanceof Integer i && i >= 0);
        INHERITANCE_VARIATION = s.comment("Chance per trait that a daughter Queen's level differs by one from her mother's.")
                .defineInRange("inheritanceVariation", 0.35, 0, 1);
        BASE_MUTATION_CHANCE = s.comment("Chance a daughter Queen is born with a random mutation (before Mutation Probability).")
                .defineInRange("baseMutationChance", 0.08, 0, 1);
        MAX_BENEFICIAL_MUTATIONS = s.comment("Most beneficial mutations one Queen can carry (flaws don't count).")
                .defineInRange("maxBeneficialMutations", 3, 0, 100);
        LAB_TIME_MULTIPLIER = s.comment("Multiplier on Genetic Laboratory processing times.").defineInRange("labTimeMultiplier", 1.0, 0.01, 100);
        LAB_COST_MULTIPLIER = s.comment("Multiplier on Genetic Laboratory material costs (rounded up, at least 1).")
                .defineInRange("labCostMultiplier", 1.0, 0.01, 100);
        s.pop();

        s.push("evolution");
        EVOLUTION_CHANCE_MULTIPLIER = s.comment("Multiplier on every evolution chance.").defineInRange("chanceMultiplier", 1.0, 0, 100);
        HYBRID_CHANCE_MULTIPLIER = s.comment("Multiplier on every hybrid chance.").defineInRange("hybridChanceMultiplier", 1.0, 0, 100);
        QUEEN_REARING_MIN_POPULATION = s.comment("Adults a colony needs before its Queen Chamber can raise a new Queen.")
                .defineInRange("queenRearingMinPopulation", 150, 0, 1000000);
        s.pop();

        s.push("wild");
        WILD_SPAWN_CHANCE = s.comment("Chance a natural spawn attempt of a wild ant succeeds (0 turns them off).")
                .defineInRange("spawnChance", 1.0, 0, 1);
        WILD_QUEEN_CHANCE = s.comment("Chance a naturally spawned wild ant is a Queen.").defineInRange("queenChance", 0.02, 0, 1);
        WILD_SOLDIER_CHANCE = s.comment("Chance a naturally spawned wild ant is a soldier.").defineInRange("soldierChance", 0.15, 0, 1);
        NEST_QUEEN_CHANCE = s.comment("Chance Tweezers pull a Queen instead of workers from a wild nest.").defineInRange("nestQueenChance", 0.05, 0, 1);
        NEST_COOLDOWN_SECONDS = s.comment("Seconds a wild nest needs to recover after ants were taken from it.").defineInRange("nestCooldownSeconds", 60, 0, 86400);
        s.pop();

        s.push("guards");
        GUARD_SOLDIER_COST = s.comment("Soldiers a colony gives up for one Guard Ant (a Rally Pheromone used on its Terrarium).")
                .defineInRange("soldierCost", 50, 1, 1000000);
        s.pop();

        s.push("connections");
        CONNECTOR_FOOD_PER_MINUTE = s.comment("Food a Colony Connector moves per minute from a colony with spare food to one that needs it.")
                .defineInRange("connectorFoodPerMinute", 600.0, 0, 1e9);
        CONNECTOR_SHARES_STORAGE = s.comment("Whether colonies joined by a Colony Connector may put resources into each other's Resource Piles.")
                .define("connectorSharesStorage", true);
        s.pop();

        s.push("power");
        GENERATOR_OUTPUT = s.comment("FE a Generator makes per tick while it burns fuel (one coal burns for 1600 ticks).")
                .defineInRange("generatorOutput", 40, 1, 100000);
        GENERATOR_PUSH = s.comment("Most FE a Generator gives to the blocks around it per tick.").defineInRange("generatorPush", 1000, 1, 1000000);
        s.pop();
        SERVER_SPEC = s.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        MAX_VISUAL_ANTS = c.comment("Most animated ants drawn in one Terrarium (they only show the colony; they never decide anything).")
                .defineInRange("maxVisualAnts", 40, 0, 120);
        TUBE_ANTS = c.comment("Ants walking through Glass Ant Tubes.").define("tubeAnts", true);
        STRUCTURE_ANTS = c.comment("Ants working on top of job sites.").define("structureAnts", true);
        PARTICLES = c.comment("Colony particles (soil, food, crystals, genetics).").define("particles", true);
        AMBIENT_SOUNDS = c.comment("Colony ambience and ant footsteps.").define("ambientSounds", true);
        VISUAL_DISTANCE = c.comment("Blocks from which Terrarium ants are no longer drawn.").defineInRange("visualDistance", 32.0, 4, 128);
        ANT_3D_MODELS = c.comment("Draw the ants on top of a Terrarium as 3D models up close. False draws them as 2D sprites like the ants in the",
                "tunnels: they look flatter but cost far less, which helps with many colonies in view.").define("ant3DModels", true);
        ANT_DETAIL_DISTANCE = c.comment("Blocks within which the ants on top of a Terrarium are 3D models (if ant3DModels is on); further away (where they are a pixel or",
                        "two) they are 2D sprites, which are far cheaper to draw.")
                .defineInRange("antDetailDistance", 8.0, 0, 128);
        CLIENT_SPEC = c.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SYNCED, SERVER_SPEC, FOLDER + "/server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, FOLDER + "/client.toml");
    }

    public static double get(ModConfigSpec.DoubleValue value) {
        return SERVER_SPEC.isLoaded() ? value.getAsDouble() : value.getDefault();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return SERVER_SPEC.isLoaded() ? value.getAsInt() : value.getDefault();
    }

    public static long get(ModConfigSpec.LongValue value) {
        return SERVER_SPEC.isLoaded() ? value.getAsLong() : value.getDefault();
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SERVER_SPEC.isLoaded() ? value.getAsBoolean() : value.getDefault();
    }

    public static List<? extends Integer> get(ModConfigSpec.ConfigValue<List<? extends Integer>> value) {
        return SERVER_SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int client(ModConfigSpec.IntValue value) {
        return CLIENT_SPEC.isLoaded() ? value.getAsInt() : value.getDefault();
    }

    public static boolean client(ModConfigSpec.BooleanValue value) {
        return CLIENT_SPEC.isLoaded() ? value.getAsBoolean() : value.getDefault();
    }

    public static double client(ModConfigSpec.DoubleValue value) {
        return CLIENT_SPEC.isLoaded() ? value.getAsDouble() : value.getDefault();
    }

    /** Extra catch-up minutes from a Simulation Upgrade of the given tier (0 = none). */
    public static int simulationUpgradeMinutes(int tier) {
        List<? extends Integer> list = get(SIMULATION_UPGRADE_MINUTES);
        return tier <= 0 || list.isEmpty() ? 0 : list.get(Math.min(tier, list.size()) - 1);
    }

    private AntConfig() {}
}
