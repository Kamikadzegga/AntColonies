package com.antcolonies.colony.simulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.breeding.QueenRearing;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.ColonyLayout;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.Founding;
import com.antcolonies.colony.model.ForagingMode;
import com.antcolonies.colony.expedition.Expedition;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.resource.ColonyStorage;
import com.antcolonies.colony.resource.MobLoot;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One simulation step of a colony, {@code dt} minutes long. Everything is worked out from the colony's numbers and
 * its connected structures; nothing here looks at entities or at what the client draws.
 *
 * <p>Order of a step: structures and modifiers, ants to jobs, food in and out, eggs, larvae and adults, deaths,
 * production in the working chambers, Queen rearing, then stage and status.
 */
public final class ColonySimulator {
    /** At most this many finds per site and step, so a long catch-up can't stall the server. */
    private static final int MAX_ROLLS = 256;
    /** What hunters of species without their own list hunt. */
    public static final List<AntSpecies.Combat.Target> DEFAULT_TARGETS = List.of(
            new AntSpecies.Combat.Target(EntityTypes.ZOMBIE.builtInRegistryHolder(), 30),
            new AntSpecies.Combat.Target(EntityTypes.SKELETON.builtInRegistryHolder(), 25),
            new AntSpecies.Combat.Target(EntityTypes.SPIDER.builtInRegistryHolder(), 25),
            new AntSpecies.Combat.Target(EntityTypes.CREEPER.builtInRegistryHolder(), 15),
            new AntSpecies.Combat.Target(EntityTypes.SLIME.builtInRegistryHolder(), 5));

    /** Everything a step needs, worked out once at its start. */
    static final class Step {
        final ServerLevel level;
        final TerrariumBlockEntity terrarium;
        final Colony colony;
        final AntSpecies species;
        final @Nullable AntSpecies former;
        final Genome genome;
        final Mutation.MutationEffects effects;
        final Map<StructureKind, List<StructureBlockEntity>> sites = new EnumMap<>(StructureKind.class);
        final RandomSource random;
        final double dt;
        int status;
        double healthFactor;
        double foodFactor = 1;
        /** Speed of the jobs that bring food in (farming, hunting): never below hungryFoodWorkFactor. */
        double foodWork = 1;
        /** Work per assigned ant, per kind of job: diminishing returns over all the colony's sites of that kind. */
        final Map<StructureKind, Double> perAnt = new EnumMap<>(StructureKind.class);
        @Nullable Allocation allocation;

        Step(ServerLevel level, TerrariumBlockEntity terrarium, AntSpecies species, @Nullable AntSpecies former, double dt) {
            this.level = level;
            this.terrarium = terrarium;
            this.colony = terrarium.colony();
            this.species = species;
            this.former = former;
            Genome queenGenome = colony.queen() != null ? colony.queen().genome() : null;
            this.genome = queenGenome != null ? queenGenome : Genome.basic(colony.species());
            this.effects = Genetics.effects(genome, level.registryAccess());
            this.random = level.getRandom();
            this.dt = dt;
        }

        List<StructureBlockEntity> of(StructureKind kind) {
            return sites.getOrDefault(kind, List.of());
        }

        private @Nullable List<Expedition> expeditions;

        /** The loaded expedition tables (looked up once per step). */
        List<Expedition> expeditions() {
            if (expeditions == null) {
                expeditions = Expedition.all(level.registryAccess());
            }
            return expeditions;
        }

        /** The expedition a Nether or End Foraging Site runs, if its table is loaded. */
        @Nullable Expedition expedition(StructureKind kind) {
            for (Expedition expedition : expeditions()) {
                if (expedition.site() == kind) {
                    return expedition;
                }
            }
            return null;
        }

        int count(StructureKind kind) {
            return of(kind).size();
        }

        int tiers(StructureKind kind, UpgradeType type) {
            int sum = 0;
            for (StructureBlockEntity site : of(kind)) {
                sum += site.upgradeTier(type);
            }
            return sum;
        }

        double trait(Trait trait) {
            return genome.multiplier(trait);
        }

        /** A site's ants as effective workers: their share of the colony's total on this kind of job, after diminishing returns. */
        double effective(Allocation.Site site) {
            double share = perAnt.computeIfAbsent(site.kind(), kind -> {
                double total = 0;
                if (allocation != null) {
                    for (Allocation.Site other : allocation.of(kind)) {
                        total += other.assigned();
                    }
                }
                return ProductionMath.perAnt(total);
            });
            return site.assigned() * share;
        }
    }

    public static void step(TerrariumBlockEntity terrarium, ServerLevel level, double dt) {
        Colony colony = terrarium.colony();
        AntSpecies species = ModRegistries.species(level.registryAccess(), colony.species());
        if (species == null) {
            ColonyAccess.finish(colony, 1, ColonyStatus.UNKNOWN_SPECIES.bit(), Colony.Stats.NONE, Colony.Activity.NONE, List.of(), 0);
            return;
        }
        AntSpecies former = colony.formerSpecies() == null ? null : ModRegistries.species(level.registryAccess(), colony.formerSpecies());
        Step step = new Step(level, terrarium, species, former, dt);
        ColonyLayout layout = colony.layout();
        collectSites(step, layout);
        // Chambers exactly as near to another Terrarium join neither colony, and say so.
        for (BlockPos pos : layout.contested()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StructureBlockEntity shared) {
                shared.markContested();
            }
        }

        // --- Capacities ---
        int terrariumPopulation = terrarium.upgradeTier(UpgradeType.POPULATION);
        long maxPopulation = AntConfig.get(AntConfig.MAX_COLONY_POPULATION);
        double adultCapacity = AntConfig.get(AntConfig.TERRARIUM_CAPACITY) * (1 + terrariumPopulation);
        double larvaeCapacity = Math.max(20, AntConfig.get(AntConfig.TERRARIUM_CAPACITY) / 2.0);
        // Nest Chambers: room for adults and for larvae.
        for (StructureBlockEntity nest : step.of(StructureKind.NEST_CHAMBER)) {
            double room = 1 + 0.5 * nest.upgradeTier(UpgradeType.POPULATION);
            adultCapacity += AntConfig.get(AntConfig.NEST_ADULT_CAPACITY) * room;
            larvaeCapacity += AntConfig.get(AntConfig.NEST_LARVAE_CAPACITY) * room;
        }
        adultCapacity = Math.min(maxPopulation, adultCapacity);
        double foodCapacity = AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY);
        for (StructureBlockEntity storage : step.of(StructureKind.STORAGE)) {
            foodCapacity += AntConfig.get(AntConfig.STORAGE_FOOD_CAPACITY) * (1 + storage.upgradeTier(UpgradeType.STORAGE));
        }

        // --- Food from items (the Terrarium's feed slot) ---
        FoodIntake.Result intake = FoodIntake.take(step, foodCapacity);

        // --- Health ---
        double resistance = (step.trait(Trait.ENVIRONMENTAL_RESISTANCE) - Trait.multiplier(Trait.MIN_LEVEL))
                / Math.max(0.0001, Trait.multiplier(Trait.maxLevel()) - Trait.multiplier(Trait.MIN_LEVEL));
        resistance = Math.clamp(resistance, 0, 1) * 0.8;
        double mismatch = terrarium.climateMismatch(species.climate());
        boolean tooCold = terrarium.tooCold(species.climate());
        if (tooCold && step.effects.coldResistant() || !tooCold && step.effects.heatResistant()) {
            mismatch = 0;
        }
        double climatePenalty = mismatch * 0.5 * (1 - resistance);
        if (climatePenalty > 0.05) {
            step.status |= ColonyStatus.CLIMATE.bit();
        }
        double adults = colony.exactAdults();
        int maintenanceNeeded = adults <= 0 ? 0 : (int) Math.ceil(adults * AntConfig.get(AntConfig.MAINTENANCE_FRACTION));
        int workerCount = (int) Math.floor(ColonyAccess.workers(colony));
        int maintenance = Math.min(workerCount, maintenanceNeeded);
        double maintenancePenalty = maintenanceNeeded == 0 ? 0 : 0.4 * (1 - maintenance / (double) maintenanceNeeded);
        boolean starving = ColonyAccess.starvedMinutes(colony) > 0;
        double targetHealth = Math.clamp(1 - climatePenalty - maintenancePenalty - (starving ? 0.4 : 0), 0, 1);
        double health = ColonyAccess.health(colony);
        health += Math.clamp(targetHealth - health, -0.1 * step.dt, 0.1 * step.dt);
        ColonyAccess.setHealth(colony, health);
        if (health < 0.5) {
            step.status |= ColonyStatus.LOW_HEALTH.bit();
        }
        step.healthFactor = ProductionMath.healthFactor(health);
        // Food holds up work, laying and growth: fully while there is enough for a while, less as it runs low, little
        // when it is out. What the colony ate per minute in its last step tells how long the food lasts.
        double lowFood = lowFoodFactor(ColonyAccess.food(colony), colony.stats().foodOut());
        step.foodFactor = starving ? AntConfig.get(AntConfig.STARVED_FACTOR) : lowFood;
        if (!starving && lowFood < 0.999) {
            step.status |= ColonyStatus.LOW_FOOD.bit();
        }
        // The jobs that bring food in keep going while food is short, so a hungry colony can always feed itself again.
        step.foodWork = Math.max(step.foodFactor, AntConfig.get(AntConfig.HUNGRY_FOOD_WORK));

        // --- Queen-only founding: the Queen waits for her food before her first brood ---
        if (colony.founding() == Founding.FEEDING && ColonyAccess.food(colony) >= foundingFood() - 0.5) {
            ColonyAccess.beginFoundingBrood(colony);
        }
        if (colony.founding().active()) {
            step.status |= ColonyStatus.FOUNDING.bit();
        }

        // --- Ants to jobs ---
        Allocation allocation = Allocation.allocate(step, workerCount - maintenance);
        step.allocation = allocation;

        // --- Food in and out ---
        double farmed = 0;
        for (Allocation.Site site : allocation.of(StructureKind.HARVESTING_SITE)) {
            farmed += Farming.food(step, site);
        }
        double consumption = foodUse(step, allocation, maintenance);
        double food = ColonyAccess.food(colony) + farmed * step.dt;
        double eaten = consumption * step.dt;
        double starvedMinutes = ColonyAccess.starvedMinutes(colony);
        if (food >= eaten) {
            food -= eaten;
            starvedMinutes = Math.max(0, starvedMinutes - step.dt * 2);
        } else {
            // Ran out partway through the step.
            double fedShare = eaten <= 0 ? 1 : food / eaten;
            food = 0;
            starvedMinutes += step.dt * (1 - fedShare);
        }
        if (food > foodCapacity) {
            food = foodCapacity;
        }
        if (starvedMinutes > 0) {
            step.status |= ColonyStatus.STARVING.bit() | ColonyStatus.NO_FOOD.bit();
        } else if (food <= 0 && consumption > 0) {
            step.status |= ColonyStatus.NO_FOOD.bit();
        }
        ColonyAccess.setFood(colony, food, starvedMinutes);

        // --- Eggs, larvae and adults ---
        Breeding.Result bred = Breeding.run(step, adultCapacity, larvaeCapacity);
        if (colony.founding() == Founding.BROOD
                && ColonyAccess.countFoundingRaised(colony, bred.raised()) >= AntConfig.get(AntConfig.FOUNDING_WORKERS)) {
            // The first workers are grown: the colony is founded.
            ColonyAccess.completeFounding(colony);
        }

        // --- Deaths ---
        double deaths = Breeding.deaths(step);

        // --- Job sites ---
        double finds = 0;
        double hunts = 0;
        double hunted = 0;
        for (StructureKind kind : StructureKind.FORAGING_KINDS) {
            for (Allocation.Site site : allocation.of(kind)) {
                Scavenging.run(step, site);
                finds += site.rate();
            }
        }
        for (Allocation.Site site : allocation.of(StructureKind.HUNTING_GROUND)) {
            // Hunters feed on what they catch: food for the colony, besides the mobs' loot.
            hunted += Hunting.run(step, site) * AntConfig.get(AntConfig.HUNT_FOOD);
            hunts += site.rate();
        }
        if (hunted > 0) {
            ColonyAccess.setFood(colony, Math.min(foodCapacity, ColonyAccess.food(colony) + hunted), ColonyAccess.starvedMinutes(colony));
        }
        for (Allocation.Site site : allocation.of(StructureKind.HARVESTING_SITE)) {
            Farming.harvest(step, site);
        }
        for (Allocation.Site site : allocation.of(StructureKind.LIQUID_CHAMBER)) {
            // Liquid Ants: droplets of their fluid, counted with the finds.
            Liquids.run(step, site);
            finds += site.rate();
        }
        allocation.publish(step);

        // What waits in the job sites' own stores moves on to Storage.
        for (StructureKind kind : StructureKind.SITE_STORES) {
            for (StructureBlockEntity site : step.of(kind)) {
                ColonyStorage.moveToStorage(level, colony, site);
            }
        }

        // --- Queen Chambers ---
        for (StructureBlockEntity chamber : step.of(StructureKind.QUEEN_CHAMBER)) {
            QueenRearing.tick(step.level, colony, step.species, chamber, step.dt, step.foodFactor);
        }

        // --- Stage and status ---
        int stage = stage(step, colony.exactAdults());
        // The Queen grows with her colony, in her own time: after a while at a bigger stage she grows into it, and two of her
        // traits go up (see QueenData.growTo).
        if (colony.queen() != null) {
            List<Trait> raised = new ArrayList<>();
            com.antcolonies.colony.genetics.QueenData grown = colony.queen().growTo(stage, step.dt, step.random, raised);
            if (grown != colony.queen()) {
                colony.setQueen(grown);
                if (!raised.isEmpty()) {
                    step.terrarium.queenGrew(step.level, grown.grown(), raised);
                }
            }
        }
        if (colony.queen() == null) {
            step.status |= ColonyStatus.NO_QUEEN.bit();
        }
        if (StructureKind.SITE_STORES.stream().anyMatch(kind -> !step.of(kind).isEmpty()) || !step.of(StructureKind.HARVESTING_SITE).isEmpty()) {
            if (allocation.assignedWorkers() == 0) {
                step.status |= ColonyStatus.NO_WORKERS.bit();
            }
        }
        if (step.count(StructureKind.NEST_CHAMBER) == 0 && colony.queen() != null) {
            step.status |= ColonyStatus.NO_NEST_CHAMBER.bit();
        }
        if (allocation.assigned(StructureKind.HARVESTING_SITE) > 0 && !step.species.farming().harvest().isEmpty()
                && step.of(StructureKind.STORAGE).isEmpty() && StructureKind.FORAGING_KINDS.stream().allMatch(kind -> step.of(kind).isEmpty())) {
            // The harvest needs somewhere to go: a Storage, or a Foraging Site's small store.
            step.status |= ColonyStatus.NO_STORAGE.bit();
        }
        if (bred.full()) {
            step.status |= ColonyStatus.POPULATION_FULL.bit();
        }
        if (bred.specialShort()) {
            step.status |= ColonyStatus.NO_SPECIAL_FOOD.bit();
        }
        if (allocation.assignedWorkers() > 0 && !starving) {
            step.status |= ColonyStatus.WORKING.bit();
        }
        double adultsNow = Math.max(1, colony.exactAdults());
        Colony.Activity activity = new Colony.Activity(
                (float) (StructureKind.FORAGING_KINDS.stream().mapToInt(allocation::assigned).sum() / adultsNow),
                (float) (allocation.assigned(StructureKind.HUNTING_GROUND) / adultsNow),
                (float) (allocation.assigned(StructureKind.HARVESTING_SITE) / adultsNow),
                (float) (allocation.assigned(StructureKind.LIQUID_CHAMBER) / adultsNow),
                (float) (maintenance / adultsNow),
                (float) Math.max(0, 1 - (allocation.assignedWorkers() + maintenance) / adultsNow),
                intake.food() > 0 || farmed > 0 || hunted > 0, starving, bred.eggs() > 0);
        int structureCount = step.sites.values().stream().mapToInt(List::size).sum();
        Colony.Stats stats = new Colony.Stats(bred.eggs() / step.dt, bred.raised() / step.dt, farmed + (intake.food() + hunted) / step.dt, consumption,
                finds, hunts, deaths / step.dt, (long) adultCapacity, (long) larvaeCapacity,
                foodCapacity, structureCount, intake.food() / step.dt);
        ColonyAccess.finish(colony, stage, step.status, stats, activity, allocation.assignments(), maintenance);
        ColonyAccess.setFoodFactor(colony, step.foodFactor);
        colony.history().sample(level.getGameTime(), colony);
        // Structures show their part of the colony's state (problems that concern them, and the link itself).
        for (List<StructureBlockEntity> list : step.sites.values()) {
            for (StructureBlockEntity site : list) {
                site.linkTo(terrarium.getBlockPos(), step.status);
            }
        }
    }

    /** The structures of the layout that are loaded and still what the scan found. */
    private static void collectSites(Step step, ColonyLayout layout) {
        for (Map.Entry<StructureKind, List<BlockPos>> entry : layout.structures().entrySet()) {
            List<StructureBlockEntity> found = new ArrayList<>();
            for (BlockPos pos : entry.getValue()) {
                if (step.level.isLoaded(pos) && step.level.getBlockEntity(pos) instanceof StructureBlockEntity site && site.kind() == entry.getKey()) {
                    found.add(site);
                }
            }
            if (!found.isEmpty()) {
                step.sites.put(entry.getKey(), found);
            }
        }
    }

    /** The food a founding Queen waits for before her first brood: foundingFoodShare of the Terrarium's own food room. */
    public static double foundingFood() {
        return AntConfig.get(AntConfig.FOUNDING_FOOD_SHARE) * AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY);
    }

    /**
     * How a colony's food holds up its work: 1 with at least lowFoodMinutes of food left at what it eats per minute,
     * falling evenly to lowFoodFactor as the food runs out (starving is worse: starvedFactor).
     */
    public static double lowFoodFactor(double food, double perMinute) {
        double minutes = AntConfig.get(AntConfig.LOW_FOOD_MINUTES);
        if (minutes <= 0 || perMinute <= 0) {
            return 1;
        }
        double left = food / perMinute;
        if (left >= minutes) {
            return 1;
        }
        double low = AntConfig.get(AntConfig.LOW_FOOD_FACTOR);
        return low + (1 - low) * Math.max(0, left) / minutes;
    }

    /** Food per minute the whole colony eats right now. */
    private static double foodUse(Step step, Allocation allocation, int maintenance) {
        Colony colony = step.colony;
        double worker = AntConfig.get(AntConfig.WORKER_FOOD);
        double hunter = AntConfig.get(AntConfig.HUNTER_FOOD);
        double idle = AntConfig.get(AntConfig.IDLE_ACTIVITY);
        // A founding Queen waiting for her food lives on her own reserves, as real founding Queens do.
        double use = colony.queen() != null && colony.founding() != Founding.FEEDING ? AntConfig.get(AntConfig.QUEEN_FOOD) : 0;
        use += maintenance * worker;
        for (Allocation.Site site : allocation.all()) {
            double saving = site.entity().upgradeTier(UpgradeType.FOOD_EFFICIENCY)
                    + site.entity().speciesUpgradeTier(colony.species(), SpeciesUpgradeKind.EFFICIENCY);
            use += site.assigned() * (site.kind() == StructureKind.HUNTING_GROUND ? hunter : worker) * ProductionMath.foodSaving(saving);
        }
        double idleWorkers = Math.max(0, ColonyAccess.workers(colony) - maintenance - allocation.assignedWorkers());
        use += idleWorkers * worker * idle;
        use += ColonyAccess.larvae(colony) * AntConfig.get(AntConfig.LARVA_FOOD);
        double efficiency = step.trait(Trait.FOOD_EFFICIENCY);
        use *= step.species.diet().consumption() / Math.max(0.01, efficiency) * step.effects.food();
        use *= ProductionMath.foodSaving(step.terrarium.upgradeTier(UpgradeType.FOOD_EFFICIENCY));
        return use;
    }

    /** The colony stage, 1 to 4: decides which outputs appear and how the Terrarium looks. */
    static int stage(Step step, double adults) {
        List<? extends Integer> thresholds = AntConfig.get(AntConfig.STAGE_POPULATIONS);
        int two = thresholds.size() > 0 ? thresholds.get(0) : 100;
        int three = thresholds.size() > 1 ? thresholds.get(1) : 1000;
        int four = thresholds.size() > 2 ? thresholds.get(2) : 5000;
        int stage = 1;
        if (adults >= two) {
            stage = 2;
            if (adults >= three) {
                stage = 3;
                if (adults >= four && (step.species.tier() >= 1 || step.genome.averageLevel() >= 5)) {
                    stage = 4;
                }
            }
        }
        return stage;
    }

    // --- The working chambers ---

    static final class Scavenging {
        static double run(Step step, Allocation.Site site) {
            if (site.assigned() <= 0) {
                return 0;
            }
            StructureBlockEntity entity = site.entity();
            Identifier speciesId = step.colony.species();
            double bonus = ProductionMath.bonus(step.trait(Trait.WORKER_PRODUCTIVITY) * step.effects.production()
                    * ProductionMath.speed(entity.upgradeTier(UpgradeType.SPEED))
                    * (1 + AntConfig.get(AntConfig.SPECIES_UPGRADE_BONUS) * entity.speciesUpgradeTier(speciesId, SpeciesUpgradeKind.EXTRACTION)));
            if (entity.kind().expedition()) {
                // A Nether or End Foraging Site: its workers bring back the expedition's finds, not the species' own.
                Expedition expedition = step.expedition(entity.kind());
                if (expedition == null) {
                    return 0;
                }
                double rate = AntConfig.get(AntConfig.EXPEDITION_ROLLS) * expedition.rate() * step.effective(site) * bonus
                        * step.healthFactor * step.foodFactor;
                site.setRate(rate);
                return rollOutputs(step, entity, rate, site, from -> expedition.outputsFor(speciesId), false, ForagingMode.BALANCED);
            }
            double rate = AntConfig.get(AntConfig.SCAVENGE_ROLLS) * step.species.production().rate() * step.effective(site) * bonus
                    * step.healthFactor * step.foodFactor;
            site.setRate(rate);
            ForagingMode mode = entity.mode();
            if (mode != ForagingMode.BALANCED && step.species.production().outputs().stream()
                    .noneMatch(output -> output.minStage() <= ColonyAccess.stage(step.colony) && output.available() && mode.wants(output))) {
                // Nothing of the kind it looks for: it gathers as if Balanced, and says so.
                site.addStatus(ColonyStatus.NOTHING_TO_FOCUS);
            }
            double rolls = rollOutputs(step, entity, rate, site, from -> from.production().outputs(), false, mode);
            trail(step, entity, (int) rolls);
            chitin(step, entity, (int) rolls);
            return rolls;
        }

        /**
         * Chitin comes only from ants: now and then a find comes back with chitin (shed skins and the remains of dead
         * ants), one chance per find ({@code chitinFindChance}). Mostly Chitin Plates; Hardened Chitin from colony stage
         * 2, more at stage 3 and up; in the Nether Infernal Chitin and in the End Void Chitin instead of some of the plates.
         */
        static void chitin(Step step, StructureBlockEntity entity, int rolls) {
            double chance = AntConfig.get(AntConfig.CHITIN_FIND_CHANCE);
            if (rolls <= 0 || chance <= 0) {
                return;
            }
            int stage = ColonyAccess.stage(step.colony);
            double hardened = stage >= 3 ? 0.25 : stage == 2 ? 0.1 : 0;
            net.minecraft.world.item.Item dimensional = step.level.dimension() == net.minecraft.world.level.Level.NETHER
                    ? com.antcolonies.core.registry.ModItems.INFERNAL_CHITIN.get()
                    : step.level.dimension() == net.minecraft.world.level.Level.END ? com.antcolonies.core.registry.ModItems.VOID_CHITIN.get() : null;
            double dimensionalShare = dimensional != null ? 0.3 : 0;
            int[] counts = new int[3];
            for (int i = 0; i < rolls; i++) {
                if (step.random.nextDouble() >= chance) {
                    continue;
                }
                double kind = step.random.nextDouble();
                if (kind < dimensionalShare) {
                    counts[2]++;
                } else if (kind < dimensionalShare + hardened) {
                    counts[1]++;
                } else {
                    counts[0]++;
                }
            }
            net.minecraft.world.item.Item[] items = {com.antcolonies.core.registry.ModItems.CHITIN_PLATE.get(),
                    com.antcolonies.core.registry.ModItems.HARDENED_CHITIN.get(), dimensional};
            for (int k = 0; k < 3; k++) {
                if (counts[k] <= 0 || items[k] == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(items[k], counts[k]);
                if (ColonyStorage.canFit(step.level, step.colony, List.of(stack), entity)) {
                    ColonyStorage.insert(step.level, step.colony, stack, entity);
                    ColonyAccess.countFound(step.colony, counts[k]);
                }
            }
        }

        /**
         * A colony living in another dimension sometimes finds that dimension's trail pheromone while it forages there (the
         * material its expedition site is crafted from): one chance per find.
         */
        private static void trail(Step step, StructureBlockEntity entity, int rolls) {
            if (rolls <= 0) {
                return;
            }
            for (Expedition expedition : step.expeditions()) {
                if (expedition.trail().isEmpty() || !expedition.home().equals(step.level.dimension())) {
                    continue;
                }
                Expedition.Trail trail = expedition.trail().get();
                int found = 0;
                for (int i = 0; i < rolls; i++) {
                    if (step.random.nextDouble() < trail.chance()) {
                        found++;
                    }
                }
                if (found > 0) {
                    ItemStack stack = new ItemStack(trail.item(), found);
                    if (ColonyStorage.canFit(step.level, step.colony, List.of(stack), entity)) {
                        ColonyStorage.insert(step.level, step.colony, stack, entity);
                        ColonyAccess.countFound(step.colony, found);
                    }
                }
            }
        }
    }

    static final class Farming {
        /** Food per minute the site grows. Species that can't farm grow nothing. */
        static double food(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.canFarm()) {
                return 0;
            }
            double rate = AntConfig.get(AntConfig.FARMER_FOOD) * step.species.farming().foodPerMinute() * step.effective(site)
                    * ProductionMath.bonus(step.trait(Trait.FARMER_PRODUCTIVITY) * step.effects.farming()
                    * ProductionMath.speed(site.entity().upgradeTier(UpgradeType.SPEED))) * step.healthFactor * step.foodWork;
            site.setRate(rate);
            return rate;
        }

        /** The site's harvest (mushrooms, seeds, berries), into the colony's stores. */
        static double harvest(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.canFarm() || step.species.farming().harvest().isEmpty()) {
                return 0;
            }
            double rolls = AntConfig.get(AntConfig.FARMER_BYPRODUCT_ROLLS) * step.effective(site)
                    * ProductionMath.bonus(step.trait(Trait.FARMER_PRODUCTIVITY) * ProductionMath.speed(site.entity().upgradeTier(UpgradeType.SPEED)))
                    * step.healthFactor * step.foodWork;
            return rollOutputs(step, site.entity(), rolls, site, from -> from.farming().harvest(), true, ForagingMode.BALANCED);
        }
    }

    /**
     * Liquid Ants at a Liquid Chamber: droplets of the species' one fluid, worked out from the number of ants there like
     * every other job (no ant has a crop that fills: the full crops they are drawn with are only how the species looks).
     * Nothing else comes of it, except now and then one Ant Food ({@code liquidFoodChance} per droplet).
     */
    static final class Liquids {
        static double run(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.makesLiquid()) {
                return 0;
            }
            StructureBlockEntity entity = site.entity();
            AntSpecies.Liquid liquid = step.species.liquid().orElse(null);
            net.minecraft.world.item.Item droplet = liquid == null || !liquid.available() ? null : liquid.dropletItem().orElse(null);
            if (droplet == null) {
                // The fluid or its droplet is gone (the mod that added it was removed): the ants stay, and wait.
                site.addStatus(ColonyStatus.NO_DROPLET);
                step.status |= ColonyStatus.NO_DROPLET.bit();
                return 0;
            }
            double bonus = ProductionMath.bonus(step.trait(Trait.WORKER_PRODUCTIVITY) * step.effects.production()
                    * ProductionMath.speed(entity.upgradeTier(UpgradeType.SPEED)));
            // A colony still turning into this species has only that share of Liquid Ants so far.
            double share = step.former != null ? Math.clamp(step.colony.conversion(), 0, 1) : 1;
            double rate = AntConfig.get(AntConfig.DROPLET_ROLLS) * liquid.rate() * step.effective(site) * share * bonus
                    * step.healthFactor * step.foodFactor;
            site.setRate(rate);
            double gathering = ProductionMath.bonus(step.trait(Trait.GATHERING_EFFICIENCY) * step.effects.gathering());
            double foodChance = AntConfig.get(AntConfig.LIQUID_FOOD_CHANCE);
            double progress = entity.progress() + rate * step.dt;
            int rolls = 0;
            while (progress >= 1 && rolls < MAX_ROLLS) {
                int count = (int) Math.floor(gathering + step.random.nextDouble());
                if (count > 0) {
                    ItemStack stack = new ItemStack(droplet, count);
                    if (!ColonyStorage.canFit(step.level, step.colony, List.of(stack), entity)) {
                        site.addStatus(ColonyStatus.STORAGE_FULL);
                        step.status |= ColonyStatus.STORAGE_FULL.bit();
                        progress = 1;
                        break;
                    }
                    ColonyStorage.insert(step.level, step.colony, stack, entity);
                    ColonyAccess.countFound(step.colony, count);
                }
                if (foodChance > 0 && step.random.nextDouble() < foodChance) {
                    // The rare extra: a little of what the ants were fed comes back as Ant Food.
                    ItemStack food = new ItemStack(com.antcolonies.core.registry.ModItems.ANT_FOOD.get());
                    if (ColonyStorage.canFit(step.level, step.colony, List.of(food), entity)) {
                        ColonyStorage.insert(step.level, step.colony, food, entity);
                    }
                }
                progress -= 1;
                rolls++;
            }
            entity.setProgress(Math.min(progress, MAX_ROLLS));
            return rolls;
        }
    }

    /** Hunters at a Hunting Ground catch mobs (their loot tables; no mob is spawned). Returns the catches. */
    static final class Hunting {
        static double run(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.canHunt()) {
                return 0;
            }
            StructureBlockEntity entity = site.entity();
            double strength = step.trait(Trait.SOLDIER_STRENGTH) * step.effects.soldiers() * step.species.combat().strength();
            int huntingTiers = entity.upgradeTier(UpgradeType.HUNTING);
            double rate = AntConfig.get(AntConfig.HUNT_ROLLS) * step.effective(site)
                    * ProductionMath.bonus(strength * (1 + 0.25 * huntingTiers) * ProductionMath.speed(entity.upgradeTier(UpgradeType.SPEED)))
                    * step.healthFactor * step.foodWork;
            site.setRate(rate);
            List<AntSpecies.Combat.Target> targets = step.species.combat().targets().isEmpty() ? DEFAULT_TARGETS : step.species.combat().targets();
            int total = targets.stream().mapToInt(AntSpecies.Combat.Target::weight).sum();
            double progress = entity.progress() + rate * step.dt;
            int rolls = 0;
            double casualtyChance = AntConfig.get(AntConfig.HUNTER_CASUALTY_CHANCE) / Math.max(0.1, strength * (1 + 0.25 * huntingTiers));
            while (progress >= 1 && rolls < MAX_ROLLS) {
                Holder<EntityType<?>> target = pick(targets, total, step.random);
                List<ItemStack> loot = MobLoot.roll(step.level, target.value(), entity.getBlockPos());
                if (!ColonyStorage.canFit(step.level, step.colony, loot, entity)) {
                    site.addStatus(ColonyStatus.STORAGE_FULL);
                    progress = 1;
                    break;
                }
                for (ItemStack stack : loot) {
                    ColonyStorage.insert(step.level, step.colony, stack, entity);
                }
                if (step.random.nextDouble() < casualtyChance && ColonyAccess.workers(step.colony) >= 1) {
                    ColonyAccess.killWorker(step.colony);
                }
                progress -= 1;
                rolls++;
            }
            entity.setProgress(Math.min(progress, MAX_ROLLS));
            return rolls;
        }

        private static Holder<EntityType<?>> pick(List<AntSpecies.Combat.Target> targets, int total, RandomSource random) {
            int pick = random.nextInt(Math.max(1, total));
            for (AntSpecies.Combat.Target target : targets) {
                pick -= target.weight();
                if (pick < 0) {
                    return target.entity();
                }
            }
            return targets.getFirst().entity();
        }
    }


    /**
     * Finds from a species' output list (or its harvest) at {@code rate} per minute, into the site's own store first, then
     * the colony's Storage. While the colony is still partly its former species, some finds come from the former species' list.
     */
    private static double rollOutputs(Step step, StructureBlockEntity entity, double rate, Allocation.Site site,
                                      java.util.function.Function<AntSpecies, List<SpeciesOutput>> source, boolean harvest,
                                      ForagingMode mode) {
        double progress = entity.progress() + rate * step.dt;
        int rolls = 0;
        Identifier speciesId = step.colony.species();
        int processingTier = harvest ? 0 : entity.speciesUpgradeTier(speciesId, SpeciesUpgradeKind.PROCESSING);
        int efficiencyTier = harvest ? 0 : entity.speciesUpgradeTier(speciesId, SpeciesUpgradeKind.EFFICIENCY);
        double gathering = ProductionMath.bonus(step.trait(Trait.GATHERING_EFFICIENCY) * step.effects.gathering());
        double specialWeight = ProductionMath.bonus(step.trait(Trait.SPECIALIZATION) * step.effects.special() * (1 + 0.15 * efficiencyTier));
        int stage = ColonyAccess.stage(step.colony);
        while (progress >= 1 && rolls < MAX_ROLLS) {
            AntSpecies from = step.former != null && step.random.nextDouble() >= step.colony.conversion() ? step.former : step.species;
            List<SpeciesOutput> outputs = source.apply(from);
            SpeciesOutput output = pickOutput(outputs, stage, specialWeight, step.random, mode);
            if (output == null) {
                progress = Math.min(progress, 1);
                break;
            }
            // Gathering brings more of the common finds; a rare (special) find is one find.
            int count = output.special() ? output.count() : (int) Math.floor(output.count() * gathering + step.random.nextDouble());
            ItemStack stack;
            if (processingTier > 0 && output.processed().isPresent()) {
                // Processing: finds pile up as credit, paid out in processed form with a bonus.
                double credit = entity.addProcessingCredit(output, count * (1 + AntConfig.get(AntConfig.SPECIES_UPGRADE_BONUS) * processingTier));
                int made = (int) (credit / output.ratio());
                stack = made > 0 ? new ItemStack(output.processed().get(), made) : ItemStack.EMPTY;
                if (made > 0) {
                    entity.addProcessingCredit(output, -made * output.ratio());
                }
            } else {
                stack = count > 0 ? output.create(count) : ItemStack.EMPTY;
            }
            if (!stack.isEmpty()) {
                if (!ColonyStorage.canFit(step.level, step.colony, List.of(stack), entity)) {
                    site.addStatus(ColonyStatus.STORAGE_FULL);
                    step.status |= ColonyStatus.STORAGE_FULL.bit();
                    progress = 1;
                    break;
                }
                ColonyStorage.insert(step.level, step.colony, stack, entity);
                ColonyAccess.countFound(step.colony, stack.getCount());
            }
            progress -= 1;
            rolls++;
        }
        entity.setProgress(Math.min(progress, MAX_ROLLS));
        return rolls;
    }

    /** An output's weight in a pick: rare (special) ones scaled by Specialization and the config's rareFindMultiplier. */
    public static double pickWeight(SpeciesOutput output, double specialWeight) {
        return output.special() ? output.weight() * specialWeight * AntConfig.get(AntConfig.RARE_FIND_MULTIPLIER) : output.weight();
    }

    public static @Nullable SpeciesOutput pickOutput(List<SpeciesOutput> outputs, int stage, double specialWeight, RandomSource random) {
        return pickOutput(outputs, stage, specialWeight, random, ForagingMode.BALANCED);
    }

    /** An output's weight in a pick by a Foraging Site in this mode (Mineral or Organic Gathering favour their kind). */
    public static double pickWeight(SpeciesOutput output, double specialWeight, ForagingMode mode) {
        return pickWeight(output, specialWeight) * mode.weight(output);
    }

    public static @Nullable SpeciesOutput pickOutput(List<SpeciesOutput> outputs, int stage, double specialWeight, RandomSource random,
                                                     ForagingMode mode) {
        double total = 0;
        for (SpeciesOutput output : outputs) {
            if (output.minStage() <= stage && output.available()) {
                total += pickWeight(output, specialWeight, mode);
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (SpeciesOutput output : outputs) {
            if (output.minStage() <= stage && output.available()) {
                pick -= pickWeight(output, specialWeight, mode);
                if (pick < 0) {
                    return output;
                }
            }
        }
        return null;
    }

    private ColonySimulator() {}
}
