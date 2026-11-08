package com.antcolonies.colony.resource;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.StructureKind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A colony's stores as one: the site that found something keeps it in its own small store first (Foraging Sites have
 * one), then it goes to the first Storage with room, then to the colony's other Foraging Sites.
 */
public final class ColonyStorage {
    /** The stores in the order finds go into them; {@code from} (the site that found it, or null) first. */
    public static List<StructureBlockEntity> piles(ServerLevel level, Colony colony, @Nullable StructureBlockEntity from) {
        List<StructureBlockEntity> piles = new ArrayList<>();
        if (from != null && from.kind().stores()) {
            piles.add(from);
        }
        for (StructureKind kind : new StructureKind[] {StructureKind.STORAGE, StructureKind.FORAGING_SITE}) {
            for (BlockPos pos : colony.layout().of(kind)) {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StructureBlockEntity pile && pile.kind() == kind && pile != from) {
                    piles.add(pile);
                }
            }
        }
        return piles;
    }

    public static List<StructureBlockEntity> piles(ServerLevel level, Colony colony) {
        return piles(level, colony, null);
    }

    /** Whether all of the stacks would fit (checked before a find, so nothing is ever lost). */
    public static boolean canFit(ServerLevel level, Colony colony, List<ItemStack> stacks, @Nullable StructureBlockEntity from) {
        if (stacks.isEmpty()) {
            return true;
        }
        List<StructureBlockEntity> piles = piles(level, colony, from);
        if (piles.isEmpty()) {
            return false;
        }
        // Counted without copying anything (this runs for every find): what each kind of item still needs after topping up
        // the partial stacks of its kind, against the empty slots. insert() fills partial stacks first too, so this is exact.
        List<ItemStack> wanted = new ArrayList<>();
        List<Integer> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int index = -1;
            for (int i = 0; i < wanted.size(); i++) {
                if (ItemStack.isSameItemSameComponents(wanted.get(i), stack)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                wanted.add(stack);
                left.add(stack.getCount());
            } else {
                left.set(index, left.get(index) + stack.getCount());
            }
        }
        int emptySlots = 0;
        for (StructureBlockEntity pile : piles) {
            for (int slot = 0; slot < pile.unlockedSlots(); slot++) {
                ItemStack existing = pile.getItem(slot);
                if (existing.isEmpty()) {
                    emptySlots++;
                    continue;
                }
                for (int i = 0; i < wanted.size(); i++) {
                    if (left.get(i) > 0 && ItemStack.isSameItemSameComponents(existing, wanted.get(i))) {
                        left.set(i, left.get(i) - Math.min(left.get(i), existing.getMaxStackSize() - existing.getCount()));
                        break;
                    }
                }
            }
        }
        int needed = 0;
        for (int i = 0; i < wanted.size(); i++) {
            int max = Math.max(1, wanted.get(i).getMaxStackSize());
            needed += (left.get(i) + max - 1) / max;
        }
        return needed <= emptySlots;
    }

    /**
     * Puts the stack into the stores; returns what didn't fit. First it tops up partial stacks of the same item in every
     * store, then it uses empty slots, the same order canFit counts in, so a stack canFit allowed always goes in whole.
     */
    public static ItemStack insert(ServerLevel level, Colony colony, ItemStack stack, @Nullable StructureBlockEntity from) {
        ItemStack left = stack.copy();
        List<StructureBlockEntity> piles = piles(level, colony, from);
        for (boolean empties : new boolean[] {false, true}) {
            for (StructureBlockEntity pile : piles) {
                int before = left.getCount();
                for (int slot = 0; slot < pile.unlockedSlots() && !left.isEmpty(); slot++) {
                    ItemStack existing = pile.getItem(slot);
                    if (!empties && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, left)) {
                        int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), left.getCount());
                        if (moved > 0) {
                            existing.grow(moved);
                            left.shrink(moved);
                        }
                    } else if (empties && existing.isEmpty()) {
                        pile.setItemQuietly(slot, left.split(Math.min(left.getMaxStackSize(), left.getCount())));
                    }
                }
                if (left.getCount() != before) {
                    pile.setChanged();
                    pile.contentsChanged();
                }
                if (left.isEmpty()) {
                    return left;
                }
            }
        }
        return left;
    }

    private ColonyStorage() {}
}
