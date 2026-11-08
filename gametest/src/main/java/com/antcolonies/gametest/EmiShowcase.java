package com.antcolonies.gametest;

import java.util.List;

import com.antcolonies.AntColonies;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;

/** The showcase's EMI steps, apart so EMI's classes only load when EMI is installed. */
final class EmiShowcase {
    private EmiShowcase() {}

    /** Opens one of the mod's EMI categories; returns what it found, for the result file. */
    static String open(String name) {
        List<EmiRecipeCategory> ours = EmiApi.getRecipeManager().getCategories().stream()
                .filter(category -> category.getId().getNamespace().equals(AntColonies.MODID)).toList();
        EmiRecipeCategory category = ours.stream().filter(c -> c.getId().equals(AntColonies.id(name))).findFirst().orElse(null);
        if (category == null) {
            return "FAIL EMI category " + name + " missing (mod categories: " + ours.stream().map(c -> c.getId().toString()).toList() + ")";
        }
        EmiApi.displayRecipeCategory(category);
        return "EMI " + name + ": " + EmiApi.getRecipeManager().getRecipes(category).size() + " recipes; the mod has " + ours.size()
                + " EMI categories: " + ours.stream().map(c -> c.getId().getPath()).toList();
    }
}
