package com.antcolonies.content.menu;

import java.util.function.BooleanSupplier;

import com.antcolonies.content.blockentity.StructureBlockEntity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A Storage slot: holds as much as the Storage's compacting allows, but hands out at most a normal stack at a time. */
public class StorageSlot extends FilteredSlot {
    private final StructureBlockEntity storage;

    public StorageSlot(StorageWindow window, StructureBlockEntity storage, int slot, int x, int y, BooleanSupplier active) {
        super(window, slot, x, y, stack -> true, Integer.MAX_VALUE, active);
        this.storage = storage;
    }

    /** A locked slot (past what the Storage's upgrades open) takes nothing, whatever a client asks for. */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return isActive() && super.mayPlace(stack);
    }

    /** Nor does a locked slot give anything. */
    @Override
    public boolean mayPickup(Player player) {
        return isActive() && super.mayPickup(player);
    }

    @Override
    public int getMaxStackSize() {
        return storage.getMaxStackSize();
    }

    /** No room for another item while it holds more than a stack: a click would swap the whole pile onto the cursor. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        if (overfull() && !ItemStack.isSameItemSameComponents(stack, getItem())) {
            return 0;
        }
        return storage.slotLimit(stack);
    }

    @Override
    public ItemStack remove(int amount) {
        return super.remove(Math.min(amount, Math.max(1, getItem().getMaxStackSize())));
    }

    /** Holds more than one stack of its item (only a compacting Storage does). */
    public boolean overfull() {
        return getItem().getCount() > getItem().getMaxStackSize();
    }
}
