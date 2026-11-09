package com.antcolonies.content.menu;

import java.util.function.IntSupplier;

import com.antcolonies.content.blockentity.StructureBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The six rows of a Storage that its screen shows: a view of the Storage's slots starting at the row it is scrolled
 * to. The menu's slots sit on this view, so scrolling changes which Storage slots they are, and the server sends the
 * new contents like any other change.
 */
public class StorageWindow implements Container {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    private final StructureBlockEntity storage;
    private final IntSupplier firstRow;

    public StorageWindow(StructureBlockEntity storage, IntSupplier firstRow) {
        this.storage = storage;
        this.firstRow = firstRow;
    }

    /** The Storage slot this window slot shows. */
    public int storageSlot(int slot) {
        return firstRow.getAsInt() * 9 + slot;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < SIZE; slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return storage.getItem(storageSlot(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return storage.removeItem(storageSlot(slot), count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return storage.removeItemNoUpdate(storageSlot(slot));
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        int index = storageSlot(slot);
        if (index < storage.getContainerSize()) {
            storage.setItem(index, stack);
        }
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return storage.canPlaceItem(storageSlot(slot), stack);
    }

    @Override
    public void setChanged() {
        storage.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return storage.stillValid(player);
    }

    @Override
    public void clearContent() {
    }
}
