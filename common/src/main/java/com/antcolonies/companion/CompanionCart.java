package com.antcolonies.companion;

import com.antcolonies.core.registry.ModAttachments;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/** What a Cart Ant carries: nine slots, saved with the ant and never sent to clients except through its screen. */
public class CompanionCart extends SimpleContainer implements ValueIOSerializable {
    public static final int SIZE = 9;

    public CompanionCart() {
        super(SIZE);
    }

    public static CompanionCart of(Mob mob) {
        return mob.getData(ModAttachments.COMPANION_CART);
    }

    /** No boxes in boxes: what a shulker box refuses, a cart refuses too. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems();
    }

    /** Whether this player can reach a mob's cart: the owner, close by, while the mob is there and still has it. */
    public static boolean reachable(Mob mob, Player player) {
        CompanionData data = Companions.get(mob);
        return !mob.isRemoved() && mob.isAlive() && data != null && data.hasCart() && Companions.isOwner(data, player)
                && player.level() == mob.level() && player.distanceToSqr(mob) <= 64;
    }

    @Override
    public void serialize(ValueOutput output) {
        ContainerHelper.saveAllItems(output, getItems(), false);
    }

    @Override
    public void deserialize(ValueInput input) {
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < SIZE; slot++) {
            setItem(slot, items.get(slot));
        }
    }
}
