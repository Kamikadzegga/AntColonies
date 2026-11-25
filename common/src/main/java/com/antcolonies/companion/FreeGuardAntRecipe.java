package com.antcolonies.companion;

import java.util.List;

import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRecipes;
import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/** A Guard Ant's tube and a name tag: the same tube with no owner, for whoever opens it next. */
public class FreeGuardAntRecipe extends CustomRecipe {
    public static final FreeGuardAntRecipe INSTANCE = new FreeGuardAntRecipe();
    public static final MapCodec<FreeGuardAntRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeGuardAntRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    /** The owned tube among the ingredients, if it and one name tag are all there is. */
    private static ItemStack tube(CraftingInput input) {
        ItemStack tube = ItemStack.EMPTY;
        int tags = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.is(Items.NAME_TAG)) {
                tags++;
            } else if (!stack.isEmpty()) {
                if (!tube.isEmpty() || !owned(stack)) {
                    return ItemStack.EMPTY;
                }
                tube = stack;
            }
        }
        return tags == 1 ? tube : ItemStack.EMPTY;
    }

    private static boolean owned(ItemStack stack) {
        CompanionTube tube = stack.get(ModDataComponents.COMPANION.get());
        return stack.is(ModItems.GUARD_ANT.get()) && tube != null && tube.data().owner().isPresent();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !tube(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack tube = tube(input);
        if (tube.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack freed = tube.copyWithCount(1);
        freed.update(ModDataComponents.COMPANION.get(), tube.get(ModDataComponents.COMPANION.get()), CompanionTube::freed);
        return freed;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(
                List.of(new SlotDisplay.ItemSlotDisplay(ModItems.GUARD_ANT.get()), new SlotDisplay.ItemSlotDisplay(Items.NAME_TAG)),
                new SlotDisplay.ItemSlotDisplay(ModItems.GUARD_ANT.get()), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.FREE_GUARD_ANT.get();
    }
}
