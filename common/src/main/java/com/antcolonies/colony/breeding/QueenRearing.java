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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/** Queen Chambers raise new Queens: a daughter of the colony's Queen, crossed with a Genetic Sample if one is given. */
public final class QueenRearing {
    /** What a Genetic Sample in a Queen Chamber does, as the chamber's screen says it. */
    public enum CrossState {
        /** No sample (or no Queen): a plain daughter. */
        NO_SAMPLE,
        /** The two species make no hybrid: the daughter only mixes the two Queens' traits. */
        NO_PAIR,
        /** The two Queens' traits are below the cross's level: the hybrid at a cut chance. */
        WEAK,
        /** Each new Queen has {@code chance} of being the hybrid. */
        READY
    }

    /**
     * @param hybrid the cross of the two species, when there is one
     * @param chance of the hybrid for each new Queen, 0 when there is no cross
     * @param level  the two Queens' average trait level
     */
    public record Cross(CrossState state, @Nullable Hybrid hybrid, double chance, double level) {}

    public static final Cross NO_CROSS = new Cross(CrossState.NO_SAMPLE, null, 0, 0);

    /** What crossing a sample into a colony of {@code species} would do. */
    public static Cross cross(HolderLookup.Provider registries, Identifier species, @Nullable QueenData queen, @Nullable QueenData sample) {
        if (queen == null || sample == null) {
            return NO_CROSS;
        }
        Hybrid hybrid = findHybrid(registries, species, sample.species());
        if (hybrid == null || ModRegistries.species(registries, hybrid.result()) == null) {
            return new Cross(CrossState.NO_PAIR, null, 0, 0);
        }
        double quality = (queen.genome().averageLevel() + sample.genome().averageLevel()) / 2;
        // Both Queens' Mutation trait counts, the same as the rest of their traits.
        double mutation = (queen.genome().multiplier(Trait.MUTATION_PROBABILITY) + sample.genome().multiplier(Trait.MUTATION_PROBABILITY)) / 2;
        double chance = hybrid.chance() * qualityFactor(hybrid, quality) * mutation * AntConfig.get(AntConfig.HYBRID_CHANCE_MULTIPLIER);
        CrossState state = quality < hybrid.minAverageLevel() ? CrossState.WEAK : CrossState.READY;
        return new Cross(state, hybrid, Math.clamp(chance, 0, 1), quality);
    }

    /** How many times two Queens' traits multiply a hybrid's chance: 1 at the cross's level, more above it, the weak share at level 1. */
    public static double qualityFactor(Hybrid hybrid, double quality) {
        double level = hybrid.minAverageLevel();
        if (quality >= level) {
            return 1 + AntConfig.get(AntConfig.HYBRID_QUALITY_BONUS) * (quality - level);
        }
        double weak = AntConfig.get(AntConfig.HYBRID_WEAK_CHANCE);
        return weak + (1 - weak) * (quality - Trait.MIN_LEVEL) / (level - Trait.MIN_LEVEL);
    }

    /** The slot of the Genetic Sample the next Queen will use up: the first sample slot that holds one, or -1. */
    public static int nextSampleSlot(StructureBlockEntity chamber) {
        for (int slot : StructureKind.QUEEN_CHAMBER_SAMPLES) {
            if (sampleOf(chamber.getItem(slot)) != null) {
                return slot;
            }
        }
        return -1;
    }

    /** The Queen a Genetic Sample was copied from; null for any other stack. */
    public static @Nullable QueenData sampleOf(ItemStack stack) {
        return stack.is(ModItems.GENETIC_SAMPLE.get()) ? stack.get(ModDataComponents.QUEEN.get()) : null;
    }

    /**
     * Whether a stack belongs in the rearing materials' slots: what the colony's species takes to raise a Queen, or
     * (the chamber has no colony yet, {@code colony} is null) what any species takes.
     */
    public static boolean isMaterial(HolderLookup.@Nullable Provider registries, @Nullable AntSpecies colony, ItemStack stack) {
        if (stack.is(ModItems.TEST_TUBE.get()) || stack.is(ModItems.GENETIC_SAMPLE.get())) {
            return false;
        }
        if (colony != null) {
            return colony.rearing().cost().stream().anyMatch(cost -> cost.test(stack));
        }
        var species = ModRegistries.lookup(registries, ModRegistries.SPECIES);
        return species.isEmpty() || species.get().listElements()
                .anyMatch(holder -> holder.value().rearing().cost().stream().anyMatch(cost -> cost.test(stack)));
    }

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
        int sampleSlot = nextSampleSlot(chamber);
        ItemStack sampleStack = sampleSlot < 0 ? ItemStack.EMPTY : chamber.getItem(sampleSlot);
        QueenData sample = sampleOf(sampleStack);
        Identifier daughterSpecies = colony.species();
        AntSpecies daughterData = species;
        Cross cross = cross(level.registryAccess(), colony.species(), queen, sample);
        if (cross.chance() > 0 && level.getRandom().nextDouble() < cross.chance()) {
            daughterSpecies = cross.hybrid().result();
            daughterData = ModRegistries.species(level.registryAccess(), daughterSpecies);
        }
        double mutationBonus = 1 + 0.25 * chamber.upgradeTier(UpgradeType.GENETIC);
        Genome genome = Genetics.daughter(queen.genome(), sample == null ? null : sample.genome(), daughterSpecies, level.getRandom(),
                level.registryAccess(), daughterData.category(), mutationBonus);
        consumeMaterials(level, chamber, species);
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
        return findHybrid(level.registryAccess(), a, b);
    }

    /** The hybrid two species make, whichever of them is the colony and whichever the sample. */
    public static @Nullable Hybrid findHybrid(HolderLookup.Provider registries, Identifier a, Identifier b) {
        Optional<? extends HolderLookup.RegistryLookup<Hybrid>> lookup = registries.lookup(ModRegistries.HYBRID);
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

    /** Uses the rearing materials up. What one leaves behind (a honey bottle's bottle) stays in its emptied slot, or drops on top. */
    private static void consumeMaterials(ServerLevel level, StructureBlockEntity chamber, AntSpecies species) {
        for (Cost cost : species.rearing().cost()) {
            int left = cost.count();
            for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS && left > 0; slot++) {
                ItemStack stack = chamber.getItem(slot);
                if (cost.test(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    ItemStackTemplate remainder = stack.getCraftingRemainder();
                    stack.shrink(taken);
                    left -= taken;
                    if (remainder != null) {
                        ItemStack leftover = remainder.create();
                        leftover.setCount(taken);
                        if (chamber.getItem(slot).isEmpty()) {
                            chamber.setItem(slot, leftover);
                        } else {
                            BlockPos pos = chamber.getBlockPos();
                            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, leftover);
                        }
                    }
                }
            }
        }
    }

    private QueenRearing() {}
}
