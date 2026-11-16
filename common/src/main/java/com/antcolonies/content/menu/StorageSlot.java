package com.antcolonies.content.menu;

import java.util.function.BooleanSupplier;

import com.antcolonies.content.blockentity.StructureBlockEntity;

import net.minecraft.world.item.ItemStack;

/** A Storage slot: holds as much as the Storage's compacting allows, but hands out at most a normal stack at a time. */
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
