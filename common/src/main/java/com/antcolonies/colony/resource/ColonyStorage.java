package com.antcolonies.colony.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A colony's Resource Piles as one store. Finds go into the first pile with room; with Connectors (when the server
 * allows it) the piles of joined colonies take what the colony's own can't.
 */
public final class ColonyStorage {
    /** What processing turns an item into, from every species' outputs: built once per set of loaded data. */
    private static RegistryAccess processingSource;
    private static Map<Item, SpeciesOutput> processing = Map.of();

    public static List<StructureBlockEntity> piles(ServerLevel level, Colony colony) {
        List<StructureBlockEntity> piles = new ArrayList<>();
        addPiles(level, colony, piles);
        if (AntConfig.get(AntConfig.CONNECTOR_SHARES_STORAGE)) {
            for (BlockPos linked : colony.layout().linkedTerrariums()) {
                if (level.isLoaded(linked) && level.getBlockEntity(linked) instanceof TerrariumBlockEntity other) {
                    addPiles(level, other.colony(), piles);
                }
            }
        }
        return piles;
    }

    private static void addPiles(ServerLevel level, Colony colony, List<StructureBlockEntity> piles) {
        if (colony.layout().twoColonies()) {
            return;
        }
        for (BlockPos pos : colony.layout().of(StructureKind.RESOURCE_PILE)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StructureBlockEntity pile && pile.kind() == StructureKind.RESOURCE_PILE) {
                piles.add(pile);
            }
        }
    }

    /** Whether all of the stacks would fit (checked before a find, so nothing is ever lost). */
    public static boolean canFit(ServerLevel level, Colony colony, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }
        List<StructureBlockEntity> piles = piles(level, colony);
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
     * Puts the stack into the piles; returns what didn't fit. First it tops up partial stacks of the same item in every
     * pile, then it uses empty slots, the same order canFit counts in, so a stack canFit allowed always goes in whole.
     */
    public static ItemStack insert(ServerLevel level, Colony colony, ItemStack stack) {
        ItemStack left = stack.copy();
        List<StructureBlockEntity> piles = piles(level, colony);
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

    /**
     * One Resource Processing Chamber operation: some pile holding {@code ratio} of an item with a processed form
     * (nuggets, shards...) gives them up for one processed item. Returns false when nothing could be processed.
     */
    public static boolean processOne(ServerLevel level, Colony colony) {
        Map<Item, SpeciesOutput> recipes = processing(level.registryAccess());
        for (StructureBlockEntity pile : piles(level, colony)) {
            for (int slot = 0; slot < pile.unlockedSlots(); slot++) {
                ItemStack stack = pile.getItem(slot);
                SpeciesOutput recipe = recipes.get(stack.getItem());
                if (recipe != null && stack.getCount() >= recipe.ratio() && recipe.processed().isPresent()) {
                    ItemStack result = new ItemStack(recipe.processed().get());
                    ItemStack taken = stack.split(recipe.ratio());
                    pile.setItemQuietly(slot, stack);
                    if (!insert(level, colony, result).isEmpty()) {
                        // No room for the result: undo.
                        stack.grow(taken.getCount());
                        pile.setItemQuietly(slot, stack);
                        return false;
                    }
                    pile.setChanged();
                    pile.contentsChanged();
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized Map<Item, SpeciesOutput> processing(RegistryAccess registries) {
        if (registries != processingSource) {
            Map<Item, SpeciesOutput> map = new HashMap<>();
            registries.lookup(ModRegistries.SPECIES).ifPresent(lookup -> lookup.stream().forEach((AntSpecies species) -> {
                for (SpeciesOutput output : species.production().outputs()) {
                    if (output.processed().isPresent() && output.item().isPresent()) {
                        map.putIfAbsent(output.item().get().value(), output);
                    }
                }
            }));
            processing = Map.copyOf(map);
            processingSource = registries;
        }
        return processing;
    }

    private ColonyStorage() {}
}
