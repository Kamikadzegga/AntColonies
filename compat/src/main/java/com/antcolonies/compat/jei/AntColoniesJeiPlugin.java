package com.antcolonies.compat.jei;

import com.antcolonies.compat.recipes.AntRecipe;
import com.antcolonies.compat.recipes.AntRecipes;
import java.util.Objects;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI integration: the whole progression is discoverable from JEI. Thirteen categories, every entry made from the
 * synced data (so modpack species, evolutions and hybrids show up too). Only loaded when JEI is installed.
 */
@JeiPlugin
public class AntColoniesJeiPlugin implements IModPlugin {
    public static final IRecipeType<AntRecipe> SPECIES = AntCategory.type("species");
    public static final IRecipeType<AntRecipe> BREEDING = AntCategory.type("breeding");
    public static final IRecipeType<AntRecipe> EVOLUTION = AntCategory.type("evolution");
    public static final IRecipeType<AntRecipe> MUTATION = AntCategory.type("mutation");
    public static final IRecipeType<AntRecipe> HYBRID = AntCategory.type("hybrid");
    public static final IRecipeType<AntRecipe> EGGS = AntCategory.type("eggs");
    public static final IRecipeType<AntRecipe> LARVAE = AntCategory.type("larvae");
    public static final IRecipeType<AntRecipe> PRODUCTION = AntCategory.type("production");
    public static final IRecipeType<AntRecipe> EXPEDITION = AntCategory.type("expedition");
    public static final IRecipeType<AntRecipe> FARMING = AntCategory.type("farming");
    public static final IRecipeType<AntRecipe> FIGHTING = AntCategory.type("fighting");
    public static final IRecipeType<AntRecipe> LAB = AntCategory.type("lab");
    public static final IRecipeType<AntRecipe> TRAIT = AntCategory.type("trait");
    public static final IRecipeType<AntRecipe> SPECIES_UPGRADE = AntCategory.type("species_upgrade");
    public static final IRecipeType<AntRecipe> COLONY_UPGRADE = AntCategory.type("colony_upgrade");

    /** JEI's runtime once it is up (used by the dev showcase to open categories). */
    public static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // The creative tab's icon item is not an ingredient of anything: hide it.
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                List.of(new net.minecraft.world.item.ItemStack(ModItems.ANT_HEAD.get())));
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public Identifier getPluginUid() {
        return AntColonies.id("jei_plugin");
    }

    /** Tubes, samples, brood and species upgrades of each species are different entries. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.QUEEN_TEST_TUBE.get(), (stack, context) -> {
            QueenData queen = stack.get(ModDataComponents.QUEEN.get());
            return queen == null ? null : queen.species().toString();
        });
        registration.registerSubtypeInterpreter(ModItems.GENETIC_SAMPLE.get(), (stack, context) -> {
            QueenData queen = stack.get(ModDataComponents.QUEEN.get());
            return queen == null ? null : queen.species().toString();
        });
        registration.registerSubtypeInterpreter(ModItems.ANT_TEST_TUBE.get(), (stack, context) -> {
            AntSample ants = stack.get(ModDataComponents.ANTS.get());
            return ants == null ? null : ants.species() + "/" + ants.caste().getSerializedName();
        });
        for (ItemLike brood : new ItemLike[] {ModItems.ANT_EGG.get(), ModItems.ANT_LARVA.get()}) {
            registration.registerSubtypeInterpreter(brood.asItem(), (stack, context) -> {
                Identifier species = stack.get(ModDataComponents.SPECIES.get());
                return species == null ? null : species.toString();
            });
        }
        registration.registerSubtypeInterpreter(ModItems.SPECIES_UPGRADE.get(), (stack, context) -> {
            SpeciesUpgrade upgrade = stack.get(ModDataComponents.SPECIES_UPGRADE.get());
            return upgrade == null ? null : upgrade.species() + "/" + upgrade.kind().getSerializedName()
                    + (context == UidContext.Ingredient ? "/" + upgrade.tier() : "");
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new AntCategory(SPECIES, "species", ModItems.ANT_IDENTIFICATION_BOOK.get(), gui, 2, 5),
                new AntCategory(BREEDING, "breeding", structure(StructureKind.QUEEN_CHAMBER), gui, 2, 2),
                new AntCategory(EVOLUTION, "evolution", ModItems.TERRARIUM.get(), gui, 1, 3),
                new AntCategory(MUTATION, "mutation", ModItems.GENETIC_LAB.get(), gui, 1, 4),
                new AntCategory(HYBRID, "hybrid", ModItems.GENETIC_SAMPLE.get(), gui, 1, 4),
                new AntCategory(EGGS, "eggs", structure(StructureKind.NEST_CHAMBER), gui, 1, 3),
                new AntCategory(LARVAE, "larvae", structure(StructureKind.NEST_CHAMBER), gui, 1, 2),
                new AntCategory(PRODUCTION, "production", structure(StructureKind.FORAGING_SITE), gui, 2, 1),
                new AntCategory(EXPEDITION, "expedition", structure(StructureKind.NETHER_FORAGING_SITE), gui, 3, 2),
                new AntCategory(FARMING, "farming", structure(StructureKind.HARVESTING_SITE), gui, 2, 1),
                new AntCategory(FIGHTING, "fighting", structure(StructureKind.HUNTING_GROUND), gui, 2, 2),
                new AntCategory(LAB, "lab", ModItems.GENETIC_LAB.get(), gui, 1, 4),
                new AntCategory(TRAIT, "trait", ModItems.GENETIC_ANALYZER.get(), gui, 1, 3),
                new AntCategory(SPECIES_UPGRADE, "species_upgrade", ModItems.UPGRADE_STATION.get(), gui, 1, 2),
                new AntCategory(COLONY_UPGRADE, "colony_upgrade", ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SPEED, 1), gui, 2, 9));
    }

    private static ItemLike structure(StructureKind kind) {
        return ModBlocks.STRUCTURES.get(kind).get();
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Objects.requireNonNull(Minecraft.getInstance().level, "JEI registers recipes in a world");
        AntRecipes recipes = new AntRecipes(level.registryAccess());
        registration.addRecipes(SPECIES, recipes.species());
        registration.addRecipes(BREEDING, recipes.rearing());
        registration.addRecipes(EVOLUTION, recipes.evolution());
        registration.addRecipes(MUTATION, recipes.mutations());
        registration.addRecipes(HYBRID, recipes.hybrids());
        registration.addRecipes(EGGS, recipes.eggs());
        registration.addRecipes(LARVAE, recipes.larvae());
        registration.addRecipes(PRODUCTION, recipes.production());
        registration.addRecipes(EXPEDITION, recipes.expeditions());
        registration.addRecipes(FARMING, recipes.farming());
        registration.addRecipes(FIGHTING, recipes.fighting());
        registration.addRecipes(LAB, recipes.lab());
        registration.addRecipes(TRAIT, recipes.traits());
        registration.addRecipes(SPECIES_UPGRADE, recipes.speciesUpgrades());
        registration.addRecipes(COLONY_UPGRADE, recipes.colonyUpgrades());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(SPECIES, ModItems.ANT_IDENTIFICATION_BOOK.get(), ModItems.TWEEZERS.get());
        registration.addCraftingStation(BREEDING, structure(StructureKind.QUEEN_CHAMBER));
        registration.addCraftingStation(EVOLUTION, ModItems.TERRARIUM.get(), ModItems.EVOLUTION_CATALYST.get());
        registration.addCraftingStation(MUTATION, ModItems.GENETIC_LAB.get(), structure(StructureKind.QUEEN_CHAMBER));
        registration.addCraftingStation(HYBRID, structure(StructureKind.QUEEN_CHAMBER));
        registration.addCraftingStation(EGGS, ModItems.TERRARIUM.get(), structure(StructureKind.NEST_CHAMBER));
        registration.addCraftingStation(LARVAE, structure(StructureKind.NEST_CHAMBER));
        registration.addCraftingStation(PRODUCTION, structure(StructureKind.FORAGING_SITE), structure(StructureKind.STORAGE));
        registration.addCraftingStation(EXPEDITION, structure(StructureKind.NETHER_FORAGING_SITE), structure(StructureKind.END_FORAGING_SITE));
        registration.addCraftingStation(FARMING, structure(StructureKind.HARVESTING_SITE), structure(StructureKind.STORAGE));
        registration.addCraftingStation(FIGHTING, structure(StructureKind.HUNTING_GROUND));
        registration.addCraftingStation(LAB, ModItems.GENETIC_LAB.get());
        registration.addCraftingStation(TRAIT, ModItems.GENETIC_LAB.get(), ModItems.GENETIC_ANALYZER.get());
        registration.addCraftingStation(SPECIES_UPGRADE, ModItems.UPGRADE_STATION.get());
    }
}
