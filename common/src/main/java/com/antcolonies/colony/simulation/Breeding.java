package com.antcolonies.colony.simulation;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.upgrade.UpgradeType;

/**
 * Eggs, larvae and adults. The Queen lays eggs (faster with Nest Chambers; genetics, food and room permitting); eggs hatch
 * into larvae after a while; larvae eat and grow into adults, some of them soldiers while there is soldier room. Adults die of
 * age, and of hunger once a colony has starved past the grace period.
 */
final class Breeding {
    record Result(double eggs, double raised, boolean full, boolean specialShort) {}

    static Result run(ColonySimulator.Step step, double adultCapacity, double soldierCapacity, double larvaeCapacity) {
        Colony colony = step.colony;
        double eggs = ColonyAccess.eggs(colony);
        double larvae = ColonyAccess.larvae(colony);
        double adults = colony.exactAdults();
        boolean full = false;
        boolean specialShort = false;

        // Laying.
        double laid = 0;
        if (colony.queen() != null) {
            int chambers = step.count(StructureKind.NEST_CHAMBER);
            double chamberFactor = chambers == 0 ? AntConfig.get(AntConfig.NO_NEST_FACTOR)
                    : ProductionMath.diminishing(chambers - 1, AntConfig.get(AntConfig.NEST_LAYING_BONUS));
            int breedingTiers = step.terrarium.upgradeTier(UpgradeType.BREEDING) + step.tiers(StructureKind.NEST_CHAMBER, UpgradeType.BREEDING);
            int speedTiers = step.tiers(StructureKind.NEST_CHAMBER, UpgradeType.SPEED);
            double rate = AntConfig.get(AntConfig.BASE_EGGS_PER_MINUTE) * step.species.breeding().eggs() * step.trait(Trait.EGG_PRODUCTION)
                    * step.effects.eggs() * chamberFactor * (1 + 0.25 * breedingTiers) * (1 + 0.1 * speedTiers) * step.healthFactor * step.foodFactor;
            double wanted = rate * step.dt;
            // Room: eggs wait for larvae room, and the whole brood for adult room.
            double room = Math.max(0, Math.min(larvaeCapacity - eggs, adultCapacity + larvaeCapacity - adults - larvae - eggs));
            if (wanted > room) {
                full = true;
                wanted = room;
            }
            if (step.species.needsSpecialFood() && wanted > 0) {
                double perEgg = step.species.diet().specialPerEgg();
                double special = ColonyAccess.specialFood(colony);
                double fed = Math.min(wanted, special / perEgg);
                if (fed < wanted) {
                    // Without their special food, eggs come at a tenth of the speed.
                    specialShort = true;
                    fed += (wanted - fed) * 0.1;
                }
                ColonyAccess.setSpecialFood(colony, special - Math.min(special, fed * perEgg));
                wanted = fed;
            }
            laid = wanted;
            eggs += laid;
            ColonyAccess.countLaid(colony, laid);
        }

        // Hatching: eggs don't eat, but need larvae room.
        double hatched = ProductionMath.flow(eggs, step.dt, AntConfig.get(AntConfig.EGG_HATCH_MINUTES));
        hatched = Math.min(hatched, Math.max(0, larvaeCapacity - larvae));
        eggs -= hatched;
        larvae += hatched;

        // Growth.
        // Nest Chambers also tend the larvae: each one makes them grow faster (diminishing).
        int nests = step.count(StructureKind.NEST_CHAMBER);
        int nestSpeed = step.tiers(StructureKind.NEST_CHAMBER, UpgradeType.SPEED);
        double growthSpeed = step.species.breeding().growth() * step.trait(Trait.LARVAE_GROWTH) * step.effects.growth()
                * ProductionMath.diminishing(nests, AntConfig.get(AntConfig.NEST_GROWTH_BONUS)) * ProductionMath.speed(nestSpeed)
                * step.foodFactor * step.healthFactor;
        double growMinutes = AntConfig.get(AntConfig.LARVAE_GROW_MINUTES) / Math.max(0.01, growthSpeed);
        double matured = ProductionMath.flow(larvae, step.dt, growMinutes);
        double adultRoom = Math.max(0, adultCapacity - adults);
        if (matured > adultRoom) {
            matured = adultRoom;
            full = true;
        }
        larvae -= matured;
        double newSoldiers = 0;
        if (soldierCapacity > 0) {
            newSoldiers = Math.min(matured * ColonyAccess.soldierShare(colony), Math.max(0, soldierCapacity - ColonyAccess.soldiers(colony)));
        }
        ColonyAccess.setPopulation(colony, eggs, larvae, ColonyAccess.workers(colony), ColonyAccess.soldiers(colony));
        ColonyAccess.addAdults(colony, matured - newSoldiers, newSoldiers);
        return new Result(laid, matured, full, specialShort);
    }

    /** Old age, and hunger after the grace period. Returns how many died. */
    static double deaths(ColonySimulator.Step step) {
        Colony colony = step.colony;
        double eggs = ColonyAccess.eggs(colony);
        double larvae = ColonyAccess.larvae(colony);
        double workers = ColonyAccess.workers(colony);
        double soldiers = ColonyAccess.soldiers(colony);
        double before = eggs + larvae + workers + soldiers;
        double age = 1 - Math.exp(-step.dt / AntConfig.get(AntConfig.ADULT_LIFESPAN_MINUTES));
        workers -= workers * age;
        soldiers -= soldiers * age;
        double starved = ColonyAccess.starvedMinutes(colony) - AntConfig.get(AntConfig.STARVATION_GRACE_MINUTES);
        if (starved > 0) {
            double resistance = Math.clamp((step.trait(Trait.ENVIRONMENTAL_RESISTANCE) - 1) * 0.5, 0, 0.6);
            double loss = Math.clamp(AntConfig.get(AntConfig.STARVATION_DECLINE) * (1 - resistance) * Math.min(step.dt, starved), 0, 1);
            // The brood goes first: eggs and larvae are given up before adults.
            eggs -= eggs * Math.min(1, loss * 2);
            larvae -= larvae * Math.min(1, loss * 1.5);
            workers -= workers * loss;
            soldiers -= soldiers * loss;
            if (workers + soldiers < 1 && larvae < 1 && eggs < 1) {
                workers = 0;
                soldiers = 0;
                if (AntConfig.get(AntConfig.STARVATION_KILLS_QUEEN)) {
                    ColonyAccess.killQueen(colony);
                }
            }
        }
        ColonyAccess.setPopulation(colony, eggs, larvae, workers, soldiers);
        return Math.max(0, before - (eggs + larvae + workers + soldiers));
    }

    private Breeding() {}
}
