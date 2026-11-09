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
 * A colony's stores as one: finds go to the first Storage with room; with every Storage full (or none), into the
 * small store of the Foraging Site that found them, then the colony's other Foraging Sites. What waits in a site's store
 * moves on to Storage as soon as there is room ({@link #moveToStorage}).
 */
public final class ColonyStorage {
    /** The stores in the order finds go into them: Storage, then {@code from} (the site that found it, or null), then the other sites. */
    public static List<StructureBlockEntity> piles(ServerLevel level, Colony colony, @Nullable StructureBlockEntity from) {
        List<StructureBlockEntity> piles = new ArrayList<>(storages(level, colony));
        if (from != null && from.kind().stores() && !piles.contains(from)) {
            piles.add(from);
        }
        for (BlockPos pos : colony.layout().of(StructureKind.FORAGING_SITE)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StructureBlockEntity site && site.kind() == StructureKind.FORAGING_SITE
                    && site != from) {
                piles.add(site);
            }
        }
        return piles;
    }

    /** The colony's Storage chambers. */
    public static List<StructureBlockEntity> storages(ServerLevel level, Colony colony) {
        List<StructureBlockEntity> storages = new ArrayList<>();
        for (BlockPos pos : colony.layout().of(StructureKind.STORAGE)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StructureBlockEntity storage && storage.kind() == StructureKind.STORAGE) {
                storages.add(storage);
            }
        }
        return storages;
    }

    /** Moves what waits in a Foraging Site's store into the colony's Storage, as much as fits. */
    public static void moveToStorage(ServerLevel level, Colony colony, StructureBlockEntity site) {
        List<StructureBlockEntity> storages = storages(level, colony);
        if (storages.isEmpty()) {
            return;
        }
        boolean moved = false;
        for (int slot = 0; slot < site.getContainerSize(); slot++) {
            ItemStack stack = site.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = insertInto(storages, stack);
            if (left.getCount() != stack.getCount()) {
                site.setItemQuietly(slot, left);
                moved = true;
            }
        }
        if (moved) {
            site.setChanged();
            site.contentsChanged();
        }
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
                        left.set(i, left.get(i) - Math.min(left.get(i), Math.max(0, pile.slotLimit(existing) - existing.getCount())));
                        break;
                    }
                }
            }
        }
        // An empty slot takes as much as the least compacting store allows (counting on the most could overfill).
        int compacting = Integer.MAX_VALUE;
        for (StructureBlockEntity pile : piles) {
            compacting = Math.min(compacting, pile.slotLimit(new ItemStack(net.minecraft.world.item.Items.STONE)) / 64);
        }
        int needed = 0;
        for (int i = 0; i < wanted.size(); i++) {
            int max = Math.max(1, wanted.get(i).getMaxStackSize()) * Math.max(1, compacting);
            needed += (left.get(i) + max - 1) / max;
        }
        return needed <= emptySlots;
    }

    /**
     * Puts the stack into the stores; returns what didn't fit. First it tops up partial stacks of the same item in every
     * store, then it uses empty slots, the same order canFit counts in, so a stack canFit allowed always goes in whole.
     */
    public static ItemStack insert(ServerLevel level, Colony colony, ItemStack stack, @Nullable StructureBlockEntity from) {
        return insertInto(piles(level, colony, from), stack);
    }

    private static ItemStack insertInto(List<StructureBlockEntity> piles, ItemStack stack) {
        ItemStack left = stack.copy();
        for (boolean empties : new boolean[] {false, true}) {
            for (StructureBlockEntity pile : piles) {
                int before = left.getCount();
                for (int slot = 0; slot < pile.unlockedSlots() && !left.isEmpty(); slot++) {
                    ItemStack existing = pile.getItem(slot);
                    if (!empties && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, left)) {
                        int moved = Math.min(pile.slotLimit(existing) - existing.getCount(), left.getCount());
                        if (moved > 0) {
                            existing.grow(moved);
                            left.shrink(moved);
                        }
                    } else if (empties && existing.isEmpty()) {
                        pile.setItemQuietly(slot, left.split(Math.min(pile.slotLimit(left), left.getCount())));
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
