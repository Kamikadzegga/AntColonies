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
    /** What soldiers of species without their own list hunt. */
    private static final List<AntSpecies.Combat.Target> DEFAULT_TARGETS = List.of(
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
        if (!layout.complete()) {
            // The ring isn't whole (or shares a chamber with another colony): the colony waits, nothing grows or works.
            int status = (layout.twoColonies() ? ColonyStatus.TWO_COLONIES.bit() : ColonyStatus.INVALID_MULTIBLOCK.bit())
                    | (colony.queen() == null ? ColonyStatus.NO_QUEEN.bit() : 0);
            ColonyAccess.finish(colony, colony.stage(), status, colony.stats(), Colony.Activity.NONE, List.of(), 0);
            for (List<StructureBlockEntity> list : step.sites.values()) {
                for (StructureBlockEntity site : list) {
                    site.setAssignment(0, 0, 0, 0);
                    site.linkTo(terrarium.getBlockPos(), status);
                }
            }
            return;
        }

        // --- Capacities ---
        int terrariumPopulation = terrarium.upgradeTier(UpgradeType.POPULATION);
        long maxPopulation = AntConfig.get(AntConfig.MAX_COLONY_POPULATION);
        double adultCapacity = AntConfig.get(AntConfig.TERRARIUM_CAPACITY) * (1 + terrariumPopulation);
        // Housing: room for adults, of which a share may be soldiers (without Housing a colony raises no soldiers).
        double soldierCapacity = 0;
        for (StructureBlockEntity housing : step.of(StructureKind.HOUSING)) {
            double room = 1 + 0.5 * housing.upgradeTier(UpgradeType.POPULATION);
            adultCapacity += AntConfig.get(AntConfig.HOUSING_CAPACITY) * room;
            soldierCapacity += AntConfig.get(AntConfig.HOUSING_SOLDIER_CAPACITY) * room;
        }
        adultCapacity = Math.min(maxPopulation, adultCapacity);
        double larvaeCapacity = Math.max(20, AntConfig.get(AntConfig.TERRARIUM_CAPACITY) / 2.0);
        for (StructureBlockEntity brood : step.of(StructureKind.BROOD_CHAMBER)) {
            larvaeCapacity += AntConfig.get(AntConfig.BROOD_LARVAE_CAPACITY) * (1 + 0.5 * brood.upgradeTier(UpgradeType.POPULATION));
        }
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
        step.foodFactor = starving ? AntConfig.get(AntConfig.STARVED_FACTOR) : 1;

        // --- Ants to jobs ---
        Allocation allocation = Allocation.allocate(step, workerCount - maintenance, (int) Math.floor(ColonyAccess.soldiers(colony)));
        step.allocation = allocation;

        // --- Food in and out ---
        double farmed = 0;
        for (Allocation.Site site : allocation.of(StructureKind.FUNGUS_GARDEN)) {
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
        Breeding.Result bred = Breeding.run(step, adultCapacity, soldierCapacity, larvaeCapacity);

        // --- Deaths ---
        double deaths = Breeding.deaths(step);

        // --- Job sites ---
        double finds = 0;
        double hunts = 0;
        for (Allocation.Site site : allocation.of(StructureKind.FORAGING_CHAMBER)) {
            Scavenging.run(step, site);
            finds += site.rate();
        }
        for (Allocation.Site site : allocation.of(StructureKind.FUNGUS_GARDEN)) {
            Farming.harvest(step, site);
        }
        for (Allocation.Site site : allocation.of(StructureKind.HUNTING_CHAMBER)) {
            Fighting.run(step, site);
            hunts += site.rate();
        }
        allocation.publish(step);

        // --- Queen Chambers ---
        for (StructureBlockEntity chamber : step.of(StructureKind.QUEEN_CHAMBER)) {
            QueenRearing.tick(step.level, colony, step.species, chamber, step.dt, step.foodFactor);
        }

        // --- Stage and status ---
        int stage = stage(step, colony.exactAdults());
        if (colony.queen() == null) {
            step.status |= ColonyStatus.NO_QUEEN.bit();
        }
        if (!step.of(StructureKind.FORAGING_CHAMBER).isEmpty() || !step.of(StructureKind.FUNGUS_GARDEN).isEmpty()) {
            if (allocation.assignedWorkers() == 0) {
                step.status |= ColonyStatus.NO_WORKERS.bit();
            }
        }
        if (!step.of(StructureKind.HUNTING_CHAMBER).isEmpty() && allocation.assignedSoldiers() == 0) {
            step.status |= ColonyStatus.NO_SOLDIERS.bit();
        }
        if (step.count(StructureKind.BROOD_CHAMBER) == 0) {
            step.status |= ColonyStatus.NO_BROOD_CHAMBER.bit();
        }
        if (step.count(StructureKind.HOUSING) == 0 && ColonyAccess.soldierShare(colony) > 0) {
            step.status |= ColonyStatus.NO_HOUSING.bit();
        }
        if ((allocation.of(StructureKind.FORAGING_CHAMBER).size() + allocation.of(StructureKind.HUNTING_CHAMBER).size()) > 0
                && step.of(StructureKind.STORAGE).isEmpty()) {
            step.status |= ColonyStatus.NO_STORAGE.bit();
        }
        if (bred.full()) {
            step.status |= ColonyStatus.POPULATION_FULL.bit();
        }
        if (bred.specialShort()) {
            step.status |= ColonyStatus.NO_SPECIAL_FOOD.bit();
        }
        if (allocation.assignedWorkers() + allocation.assignedSoldiers() > 0 && !starving) {
            step.status |= ColonyStatus.WORKING.bit();
        }
        double adultsNow = Math.max(1, colony.exactAdults());
        Colony.Activity activity = new Colony.Activity(
                (float) (allocation.assigned(StructureKind.FORAGING_CHAMBER) / adultsNow),
                (float) (allocation.assigned(StructureKind.HUNTING_CHAMBER) / adultsNow),
                (float) (allocation.assigned(StructureKind.FUNGUS_GARDEN) / adultsNow),
                0F,
                (float) (maintenance / adultsNow),
                (float) Math.max(0, 1 - (allocation.assignedWorkers() + allocation.assignedSoldiers() + maintenance) / adultsNow),
                intake.food() > 0 || farmed > 0, starving, bred.eggs() > 0);
        int structureCount = step.sites.values().stream().mapToInt(List::size).sum();
        Colony.Stats stats = new Colony.Stats(bred.eggs() / step.dt, bred.raised() / step.dt, farmed + intake.food() / step.dt, consumption,
                finds, hunts, deaths / step.dt, (long) adultCapacity, (long) soldierCapacity, (long) larvaeCapacity,
                foodCapacity, structureCount);
        ColonyAccess.finish(colony, stage, step.status, stats, activity, allocation.assignments(), maintenance);
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

    /** Food per minute the whole colony eats right now. */
    private static double foodUse(Step step, Allocation allocation, int maintenance) {
        Colony colony = step.colony;
        double worker = AntConfig.get(AntConfig.WORKER_FOOD);
        double soldier = AntConfig.get(AntConfig.SOLDIER_FOOD);
        double idle = AntConfig.get(AntConfig.IDLE_ACTIVITY);
        double use = colony.queen() != null ? AntConfig.get(AntConfig.QUEEN_FOOD) : 0;
        use += maintenance * worker;
        for (Allocation.Site site : allocation.all()) {
            double perAnt = site.kind().job() == StructureKind.Job.SOLDIERS ? soldier : worker;
            double saving = site.entity().upgradeTier(UpgradeType.FOOD_EFFICIENCY)
                    + site.entity().speciesUpgradeTier(colony.species(), SpeciesUpgradeKind.EFFICIENCY);
            use += site.assigned() * perAnt * ProductionMath.foodSaving(saving);
        }
        double idleWorkers = Math.max(0, ColonyAccess.workers(colony) - maintenance - allocation.assignedWorkers());
        double idleSoldiers = Math.max(0, ColonyAccess.soldiers(colony) - allocation.assignedSoldiers());
        use += idleWorkers * worker * idle + idleSoldiers * soldier * idle;
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
        if (adults >= two && step.count(StructureKind.BROOD_CHAMBER) > 0) {
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
            double rate = AntConfig.get(AntConfig.SCAVENGE_ROLLS) * step.species.production().rate() * step.effective(site) * bonus
                    * step.healthFactor * step.foodFactor;
            site.setRate(rate);
            return rollOutputs(step, entity, rate, site, false);
        }
    }

    static final class Farming {
        /** Food per minute the garden grows. Gardens of species that can't farm grow nothing. */
        static double food(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.canFarm()) {
                return 0;
            }
            double rate = AntConfig.get(AntConfig.FARMER_FOOD) * step.species.farming().foodPerMinute() * step.effective(site)
                    * ProductionMath.bonus(step.trait(Trait.FARMER_PRODUCTIVITY) * step.effects.farming()
                    * ProductionMath.speed(site.entity().upgradeTier(UpgradeType.SPEED))) * step.healthFactor * step.foodFactor;
            site.setRate(rate);
            return rate;
        }

        /** The garden's harvest (mushrooms, seeds, berries), into the Storage chambers. */
        static double harvest(Step step, Allocation.Site site) {
            if (site.assigned() <= 0 || !step.species.canFarm() || step.species.farming().harvest().isEmpty()) {
                return 0;
            }
            double rolls = AntConfig.get(AntConfig.FARMER_BYPRODUCT_ROLLS) * step.effective(site)
                    * ProductionMath.bonus(step.trait(Trait.FARMER_PRODUCTIVITY) * ProductionMath.speed(site.entity().upgradeTier(UpgradeType.SPEED)))
                    * step.healthFactor * step.foodFactor;
            return rollOutputs(step, site.entity(), rolls, site, true);
        }
    }

    static final class Fighting {
        static double run(Step step, Allocation.Site site) {
            if (site.assigned() <= 0) {
                return 0;
            }
            StructureBlockEntity entity = site.entity();
            double strength = step.trait(Trait.SOLDIER_STRENGTH) * step.effects.soldiers() * step.species.combat().strength();
            // The colony's Housing help every Hunting Chamber, but no more than one fully upgraded Housing would (two tier-3 upgrades).
            int soldierTiers = entity.upgradeTier(UpgradeType.SOLDIER) + Math.min(6, step.tiers(StructureKind.HOUSING, UpgradeType.SOLDIER));
            double rate = AntConfig.get(AntConfig.FIGHT_ROLLS) * step.effective(site)
                    * ProductionMath.bonus(strength * (1 + 0.25 * soldierTiers) * ProductionMath.speed(entity.upgradeTier(UpgradeType.SPEED)))
                    * step.healthFactor * step.foodFactor;
            site.setRate(rate);
            List<AntSpecies.Combat.Target> targets = step.species.combat().targets().isEmpty() ? DEFAULT_TARGETS : step.species.combat().targets();
            int total = targets.stream().mapToInt(AntSpecies.Combat.Target::weight).sum();
            double progress = entity.progress() + rate * step.dt;
            int rolls = 0;
            double casualtyChance = AntConfig.get(AntConfig.SOLDIER_CASUALTY_CHANCE) / Math.max(0.1, strength * (1 + 0.25 * soldierTiers));
            while (progress >= 1 && rolls < MAX_ROLLS) {
                Holder<EntityType<?>> target = pick(targets, total, step.random);
                List<ItemStack> loot = MobLoot.roll(step.level, target.value(), entity.getBlockPos());
                if (!ColonyStorage.canFit(step.level, step.colony, loot)) {
                    site.addStatus(ColonyStatus.STORAGE_FULL);
                    progress = 1;
                    break;
                }
                for (ItemStack stack : loot) {
                    ColonyStorage.insert(step.level, step.colony, stack);
                }
                if (step.random.nextDouble() < casualtyChance && ColonyAccess.soldiers(step.colony) >= 1) {
                    ColonyAccess.killSoldier(step.colony);
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
     * Finds from a species' output list (or its garden harvest) at {@code rate} per minute, into the colony's Resource
     * Piles. While the colony is still partly its former species, some finds come from the former species' list.
     */
    private static double rollOutputs(Step step, StructureBlockEntity entity, double rate, Allocation.Site site, boolean harvest) {
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
            List<SpeciesOutput> outputs = harvest ? from.farming().harvest() : from.production().outputs();
            SpeciesOutput output = pickOutput(outputs, stage, specialWeight, step.random);
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
                if (!ColonyStorage.canFit(step.level, step.colony, List.of(stack))) {
                    site.addStatus(ColonyStatus.STORAGE_FULL);
                    step.status |= ColonyStatus.STORAGE_FULL.bit();
                    progress = 1;
                    break;
                }
                ColonyStorage.insert(step.level, step.colony, stack);
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
        double total = 0;
        for (SpeciesOutput output : outputs) {
            if (output.minStage() <= stage && output.available()) {
                total += pickWeight(output, specialWeight);
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (SpeciesOutput output : outputs) {
            if (output.minStage() <= stage && output.available()) {
                pick -= pickWeight(output, specialWeight);
                if (pick < 0) {
                    return output;
                }
            }
        }
        return null;
    }

    private ColonySimulator() {}
}
