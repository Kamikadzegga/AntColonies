package com.antcolonies.content.recipe;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * One Droplet Centrifuge recipe: an item spun into a fluid for FE. An ordinary recipe file, so datapacks and KubeJS add,
 * change and remove them like any other ({@code data/<namespace>/recipe/<name>.json}):
 * <pre>{@code
 * { "type": "antcolonies:centrifuging", "ingredient": "antcolonies:lava_droplet",
 *   "result": { "id": "minecraft:lava", "amount": 50 }, "energy": 2000, "time": 100 }
 * }</pre>
 *
 * @param ingredient what goes in, one per run (a droplet, but any item works)
 * @param result     the fluid and how many millibuckets one item gives
 * @param energy     FE one run takes in all, spread evenly over its time
 * @param time       ticks one run takes
 */
public record CentrifugingRecipe(Ingredient ingredient, FluidStackTemplate result, int energy, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_ENERGY = 400;
    public static final int DEFAULT_TIME = 40;

    public static final MapCodec<CentrifugingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CentrifugingRecipe::ingredient),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(CentrifugingRecipe::result),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(CentrifugingRecipe::energy),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(CentrifugingRecipe::time)
    ).apply(i, CentrifugingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CentrifugingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CentrifugingRecipe::ingredient,
            FluidStackTemplate.STREAM_CODEC, CentrifugingRecipe::result,
            ByteBufCodecs.VAR_INT, CentrifugingRecipe::energy,
            ByteBufCodecs.VAR_INT, CentrifugingRecipe::time,
            CentrifugingRecipe::new);

    /** The fluid one run makes. */
    public FluidResource fluid() {
        return FluidResource.of(result);
    }

    /** Millibuckets one run makes. */
    public int amount() {
        return result.amount();
    }

    /** The FE one run takes with the config's multiplier. */
    public int energyCost() {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(energy * AntConfig.get(AntConfig.CENTRIFUGE_ENERGY_MULTIPLIER)));
    }

    /** The ticks one run takes with the config's multiplier. */
    public int duration() {
        return (int) Math.clamp(Math.round(time * AntConfig.get(AntConfig.CENTRIFUGE_TIME_MULTIPLIER)), 1, 1_000_000);
    }

    public FluidStack createResult() {
        return result.create();
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    /** The result is a fluid: there is no item to assemble. */
    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<CentrifugingRecipe> getSerializer() {
        return ModRecipes.CENTRIFUGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CentrifugingRecipe> getType() {
        return ModRecipes.CENTRIFUGING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.CENTRIFUGING_CATEGORY.get();
    }

    // --- Looking recipes up ---

    /** The client's copy: the server sends the recipes of this type to every player (see ModRecipes.onDatapackSync). */
    private static volatile List<CentrifugingRecipe> clientRecipes = List.of();

    /** Called on the client when the server's recipes arrive, and with an empty list on leaving the world. */
    public static void setClientRecipes(List<CentrifugingRecipe> recipes) {
        clientRecipes = List.copyOf(recipes);
    }

    /** Every recipe this side knows: the server's own, or the client's synced copy. */
    public static List<CentrifugingRecipe> all(@Nullable Level level) {
        if (level instanceof ServerLevel server) {
            List<CentrifugingRecipe> list = new ArrayList<>();
            for (RecipeHolder<CentrifugingRecipe> holder : server.recipeAccess().recipeMap().byType(ModRecipes.CENTRIFUGING.get())) {
                list.add(holder.value());
            }
            return list;
        }
        return clientRecipes;
    }

    /** The recipe for an item, if a Centrifuge can spin it. */
    public static @Nullable CentrifugingRecipe find(@Nullable Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (CentrifugingRecipe recipe : all(level)) {
            if (recipe.ingredient().test(stack)) {
                return recipe;
            }
        }
        return null;
    }
}
