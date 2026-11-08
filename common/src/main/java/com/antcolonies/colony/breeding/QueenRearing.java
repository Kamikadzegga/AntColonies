package com.antcolonies.colony.breeding;

import java.util.Optional;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Queen Chambers raise new Queens. With the colony's Queen present, enough adults, food, the species' rearing materials
 * and an empty Test Tube, the chamber works for the species' rearing time and then fills the tube with a daughter who
 * inherits her mother's genetics. A Genetic Sample in its sample slot is crossed in: the daughter takes each trait from
 * either parent, and if the two species form a hybrid she may be born a hybrid Queen.
 */
public final class QueenRearing {
    public static void tick(ServerLevel level, Colony colony, AntSpecies species, StructureBlockEntity chamber, double dt, double foodFactor) {
        int status = 0;
        QueenData queen = colony.queen();
        if (queen == null) {
            status |= ColonyStatus.NO_QUEEN.bit();
        }
        if (colony.adults() < AntConfig.get(AntConfig.QUEEN_REARING_MIN_POPULATION)) {
            status |= ColonyStatus.NO_WORKERS.bit();
        }
        if (colony.food() <= 0) {
            status |= ColonyStatus.NO_FOOD.bit();
        }
        if (!chamber.getItem(StructureKind.QUEEN_CHAMBER_OUTPUT).isEmpty()) {
            status |= ColonyStatus.STORAGE_FULL.bit();
        }
        if (!chamber.getItem(StructureKind.QUEEN_CHAMBER_TUBE).is(ModItems.TEST_TUBE.get()) || !hasMaterials(chamber, species)) {
            status |= ColonyStatus.INSUFFICIENT_MATERIALS.bit();
        }
        if (status != 0 || queen == null) {
            chamber.setRearing(chamber.rearingProgress(), status);
            return;
        }
        int time = species.rearing().time();
        double speed = (1 + 0.25 * chamber.upgradeTier(UpgradeType.BREEDING)) * (1 + 0.25 * chamber.upgradeTier(UpgradeType.SPEED)) * foodFactor;
        double progress = chamber.rearingProgress() + dt * 1200 * speed;
        if (progress < time) {
            chamber.setRearing(progress, ColonyStatus.WORKING.bit());
            return;
        }
        // Done: the daughter.
        ItemStack sampleStack = chamber.getItem(StructureKind.QUEEN_CHAMBER_SAMPLE);
        QueenData sample = sampleStack.is(ModItems.GENETIC_SAMPLE.get()) ? sampleStack.get(ModDataComponents.QUEEN.get()) : null;
        Identifier daughterSpecies = colony.species();
        AntSpecies daughterData = species;
        if (sample != null) {
            Hybrid hybrid = findHybrid(level, colony.species(), sample.species());
            double chance = hybrid == null ? 0 : hybrid.chance() * queen.genome().multiplier(Trait.MUTATION_PROBABILITY)
                    * AntConfig.get(AntConfig.HYBRID_CHANCE_MULTIPLIER);
            if (hybrid != null && queen.genome().averageLevel() >= hybrid.minAverageLevel() && sample.genome().averageLevel() >= hybrid.minAverageLevel()
                    && level.getRandom().nextDouble() < chance) {
                AntSpecies result = ModRegistries.species(level.registryAccess(), hybrid.result());
                if (result != null) {
                    daughterSpecies = hybrid.result();
                    daughterData = result;
                }
            }
        }
        double mutationBonus = 1 + 0.25 * chamber.upgradeTier(UpgradeType.GENETIC);
        Genome genome = Genetics.daughter(queen.genome(), sample == null ? null : sample.genome(), daughterSpecies, level.getRandom(),
                level.registryAccess(), daughterData.category(), mutationBonus);
        consumeMaterials(chamber, species);
        chamber.getItem(StructureKind.QUEEN_CHAMBER_TUBE).shrink(1);
        if (sample != null) {
            sampleStack.shrink(1);
        }
        ItemStack daughter = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        daughter.set(ModDataComponents.QUEEN.get(), new QueenData(daughterSpecies, genome));
        chamber.setItem(StructureKind.QUEEN_CHAMBER_OUTPUT, daughter);
        chamber.setRearing(0, ColonyStatus.WORKING.bit());
        chamber.onQueenRaised();
    }

    public static @Nullable Hybrid findHybrid(ServerLevel level, Identifier a, Identifier b) {
        Optional<? extends net.minecraft.core.HolderLookup.RegistryLookup<Hybrid>> lookup = level.registryAccess().lookup(ModRegistries.HYBRID);
        if (lookup.isEmpty()) {
            return null;
        }
        return lookup.get().listElements().map(Holder::value).filter(hybrid -> hybrid.matches(a, b)).findFirst().orElse(null);
    }

    public static boolean hasMaterials(StructureBlockEntity chamber, AntSpecies species) {
        for (Cost cost : species.rearing().cost()) {
            int found = 0;
            for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS; slot++) {
                ItemStack stack = chamber.getItem(slot);
                if (cost.test(stack)) {
                    found += stack.getCount();
                }
            }
            if (found < cost.count()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeMaterials(StructureBlockEntity chamber, AntSpecies species) {
        for (Cost cost : species.rearing().cost()) {
            int left = cost.count();
            for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS && left > 0; slot++) {
                ItemStack stack = chamber.getItem(slot);
                if (cost.test(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
    }

    private QueenRearing() {}
}
