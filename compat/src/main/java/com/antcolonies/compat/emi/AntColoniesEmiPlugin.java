package com.antcolonies.compat.emi;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.compat.recipes.AntRecipe;
import com.antcolonies.compat.recipes.AntRecipes;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * EMI integration: the same thirteen categories as JEI, from the same synced data (so modpack species, evolutions and
 * hybrids show up too). Only loaded when EMI is installed.
 */
@EmiEntrypoint
public class AntColoniesEmiPlugin implements EmiPlugin {
    /** A category: its name (also its JEI title key), icon, slot rows and text lines, its entries, its workstations. */
    private record Kind(String name, ItemLike icon, int rows, int textLines, Function<AntRecipes, List<AntRecipe>> recipes, ItemLike... stations) {}

    private static List<Kind> kinds() {
        ItemLike lab = ModItems.GENETIC_LAB.get();
        return List.of(
                new Kind("species", ModItems.ANT_IDENTIFICATION_BOOK.get(), 2, 5, AntRecipes::species, ModItems.ANT_IDENTIFICATION_BOOK.get(), ModItems.TWEEZERS.get()),
                new Kind("breeding", structure(StructureKind.QUEEN_CHAMBER), 2, 2, AntRecipes::rearing, structure(StructureKind.QUEEN_CHAMBER)),
                new Kind("evolution", ModItems.TERRARIUM.get(), 1, 3, AntRecipes::evolution, ModItems.TERRARIUM.get(), ModItems.EVOLUTION_CATALYST.get()),
                new Kind("mutation", lab, 1, 4, AntRecipes::mutations, lab, structure(StructureKind.QUEEN_CHAMBER)),
                new Kind("hybrid", ModItems.GENETIC_SAMPLE.get(), 1, 4, AntRecipes::hybrids, structure(StructureKind.QUEEN_CHAMBER)),
                new Kind("eggs", structure(StructureKind.NEST_CHAMBER), 1, 3, AntRecipes::eggs, ModItems.TERRARIUM.get(), structure(StructureKind.NEST_CHAMBER)),
                new Kind("larvae", structure(StructureKind.NEST_CHAMBER), 1, 2, AntRecipes::larvae, structure(StructureKind.NEST_CHAMBER)),
                new Kind("production", structure(StructureKind.FORAGING_SITE), 2, 1, AntRecipes::production, structure(StructureKind.FORAGING_SITE),
                        structure(StructureKind.STORAGE)),
                new Kind("farming", structure(StructureKind.HARVESTING_SITE), 2, 1, AntRecipes::farming, structure(StructureKind.HARVESTING_SITE),
                        structure(StructureKind.STORAGE)),
                new Kind("fighting", structure(StructureKind.HUNTING_GROUND), 2, 2, AntRecipes::fighting, structure(StructureKind.HUNTING_GROUND)),
                new Kind("lab", ModItems.GENETIC_LAB.get(), 1, 4, AntRecipes::lab, lab),
                new Kind("trait", ModItems.GENETIC_ANALYZER.get(), 1, 3, AntRecipes::traits, ModItems.GENETIC_LAB.get(), ModItems.GENETIC_ANALYZER.get()),
                new Kind("species_upgrade", ModItems.UPGRADE_STATION.get(), 1, 2, AntRecipes::speciesUpgrades, ModItems.UPGRADE_STATION.get()),
                new Kind("colony_upgrade", ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SPEED, 1), 2, 9, AntRecipes::colonyUpgrades));
    }

    private static ItemLike structure(StructureKind kind) {
        return ModBlocks.STRUCTURES.get(kind).get();
    }

    @Override
    public void register(EmiRegistry registry) {
        // The creative tab's icon item is not an ingredient of anything.
        registry.removeEmiStacks(dev.emi.emi.api.stack.EmiStack.of(ModItems.ANT_HEAD.get()));
        var level = Objects.requireNonNull(Minecraft.getInstance().level, "EMI registers recipes in a world");
        AntRecipes recipes = new AntRecipes(level.registryAccess());
        for (Kind kind : kinds()) {
            Component title = Component.translatable("jei.antcolonies." + kind.name());
            EmiRecipeCategory category = new EmiRecipeCategory(AntColonies.id(kind.name()), EmiStack.of(kind.icon())) {
                @Override
                public Component getName() {
                    return title;
                }
            };
            registry.addCategory(category);
            for (ItemLike station : kind.stations()) {
                registry.addWorkstation(category, EmiStack.of(station));
            }
            for (AntRecipe recipe : kind.recipes().apply(recipes)) {
                registry.addRecipe(new AntEmiRecipe(category, recipe, kind.rows(), kind.textLines()));
            }
        }
        // Tubes, samples, brood and species upgrades of each species are different stacks (but two Queens of one
        // species with different genes are the same one, so a recipe's wild Queen matches yours).
        registry.setDefaultComparison(ModItems.QUEEN_TEST_TUBE.get(), Comparison.compareData(stack -> queenSpecies(stack.getItemStack())));
        registry.setDefaultComparison(ModItems.GENETIC_SAMPLE.get(), Comparison.compareData(stack -> queenSpecies(stack.getItemStack())));
        registry.setDefaultComparison(ModItems.ANT_TEST_TUBE.get(), Comparison.compareData(stack -> {
            AntSample ants = stack.getItemStack().get(ModDataComponents.ANTS.get());
            return ants == null ? "" : ants.species() + "/" + ants.caste().getSerializedName();
        }));
        for (ItemLike brood : new ItemLike[] {ModItems.ANT_EGG.get(), ModItems.ANT_LARVA.get()}) {
            registry.setDefaultComparison(brood.asItem(), Comparison.compareData(stack -> {
                var species = stack.getItemStack().get(ModDataComponents.SPECIES.get());
                return species == null ? "" : species.toString();
            }));
        }
        registry.setDefaultComparison(ModItems.SPECIES_UPGRADE.get(), Comparison.compareData(stack -> {
            SpeciesUpgrade upgrade = stack.getItemStack().get(ModDataComponents.SPECIES_UPGRADE.get());
            return upgrade == null ? "" : upgrade.species() + "/" + upgrade.kind().getSerializedName() + "/" + upgrade.tier();
        }));
    }

    private static String queenSpecies(ItemStack stack) {
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        return queen == null ? "" : queen.species().toString();
    }
}
