package com.antcolonies.content.menu;

import java.util.HashSet;
import java.util.Set;

import com.antcolonies.content.item.SpeciesUpgradeItem;
import com.antcolonies.content.item.UpgradeItem;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Shared menu plumbing: the block's slots first, then the player's inventory, and shift-clicking between the two. */
public abstract class BaseMenu extends AbstractContainerMenu {
    protected int blockSlots;
    private final Set<Integer> upgradeSlots = new HashSet<>();

    protected BaseMenu(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Adds one of the block's upgrade slots: shift-clicked upgrades go only into these. */
    protected Slot addUpgradeSlot(Slot slot) {
        Slot added = addSlot(slot);
        upgradeSlots.add(added.index);
        return added;
    }

    public static boolean isUpgrade(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem || stack.getItem() instanceof SpeciesUpgradeItem;
    }

    /** Call after adding the block's slots. */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        blockSlots = slots.size();
        addStandardInventorySlots(inventory, x, y);
    }

    /** Whether shift-clicking may put this stack into the block slot {@code slot} (which must accept it as well). */
    protected boolean quickMovesInto(int slot, ItemStack stack) {
        return true;
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
            boolean upgrade = isUpgrade(stack);
            for (int i = 0; i < blockSlots && !stack.isEmpty(); i++) {
                if (upgrade != upgradeSlots.contains(i)) {
                    continue;
                }
                Slot target = slots.get(i);
                if (target.isActive() && target.mayPlace(stack) && quickMovesInto(i, stack)) {
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
