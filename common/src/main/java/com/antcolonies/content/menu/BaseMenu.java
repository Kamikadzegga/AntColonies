package com.antcolonies.content.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shared menu plumbing: the block's own slots come first, then the player's inventory; shift-clicking moves between the
 * two (into the first block slot that takes the item).
 */
public abstract class BaseMenu extends AbstractContainerMenu {
    protected int blockSlots;

    protected BaseMenu(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Call after adding the block's slots. */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        blockSlots = slots.size();
        addStandardInventorySlots(inventory, x, y);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < blockSlots) {
            if (!moveItemStackTo(stack, blockSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < blockSlots && !stack.isEmpty(); i++) {
                Slot target = slots.get(i);
                if (target.isActive() && target.mayPlace(stack)) {
                    moved |= moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }
}
