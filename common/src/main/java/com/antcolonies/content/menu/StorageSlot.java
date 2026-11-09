package com.antcolonies.content.menu;

import java.util.function.BooleanSupplier;

import com.antcolonies.content.blockentity.StructureBlockEntity;

import net.minecraft.world.item.ItemStack;

/**
 * A Storage slot, on the screen's scrolling window. It holds as much as the Storage's compacting allows (Storage
 * Upgrades add a stack per slot per tier), but hands out at most a normal stack at a time, so the cursor never carries
 * more than the item stacks to.
 */
public class StorageSlot extends FilteredSlot {
    private final StructureBlockEntity storage;

    public StorageSlot(StorageWindow window, StructureBlockEntity storage, int slot, int x, int y, BooleanSupplier active) {
        super(window, slot, x, y, stack -> true, Integer.MAX_VALUE, active);
        this.storage = storage;
    }

    @Override
    public int getMaxStackSize() {
        return storage.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
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
