package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.antcolonies.content.recipe.CentrifugingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's one recipe type: the Droplet Centrifuge's (an item spun into a fluid). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AntColonies.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, AntColonies.MODID);
    public static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, AntColonies.MODID);

    public static final Supplier<RecipeType<CentrifugingRecipe>> CENTRIFUGING = RECIPE_TYPES.register("centrifuging",
            () -> RecipeType.simple(AntColonies.id("centrifuging")));
    public static final Supplier<RecipeSerializer<CentrifugingRecipe>> CENTRIFUGING_SERIALIZER = RECIPE_SERIALIZERS.register("centrifuging",
            () -> new RecipeSerializer<>(CentrifugingRecipe.MAP_CODEC, CentrifugingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeBookCategory> CENTRIFUGING_CATEGORY = RECIPE_BOOK_CATEGORIES.register("centrifuging", RecipeBookCategory::new);

    /**
     * Clients get the Centrifuge's recipes (vanilla sends none): its screen checks what its slot takes with them, and
     * JEI and EMI list them.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(CENTRIFUGING.get());
    }

    private ModRecipes() {}
}
