package com.antcolonies.content.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A slot taking only what its filter allows (and, optionally, only shown while a condition holds). */
public class FilteredSlot extends Slot {
    private final Predicate<ItemStack> filter;
    private final int maxStack;
    private final BooleanSupplier active;

    public FilteredSlot(Container container, int slot, int x, int y, Predicate<ItemStack> filter, int maxStack) {
        this(container, slot, x, y, filter, maxStack, () -> true);
    }

    public FilteredSlot(Container container, int slot, int x, int y, Predicate<ItemStack> filter, int maxStack, BooleanSupplier active) {
        super(container, slot, x, y);
        this.filter = filter;
        this.maxStack = maxStack;
        this.active = active;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return filter.test(stack);
    }

    @Override
    public int getMaxStackSize() {
        return Math.min(maxStack, super.getMaxStackSize());
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}
