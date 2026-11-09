package com.antcolonies.content.blockentity;

import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

/**
 * A nest a player placed (a {@link WildNestBlock} with {@code home=true}): a few worker ants of its species live in it,
 * like bees in a beehive. In daylight and dry weather they come out a few at a time and work the land within
 * {@link WildAnt#NEST_RANGE} blocks: they tend crops and saplings (growing them on, as bees do) and harvest ripe crops,
 * replanting them and carrying the harvest home into this nest's store. Hoppers and pipes take from the store (nothing
 * can be put in); breaking the nest drops it. At night and in rain the ants stay in. An ant that dies is replaced, one
 * every few minutes. Wild nests from world generation have no block entity and no residents.
 */
public class HomeNestBlockEntity extends BlockEntity implements WorldlyContainer {
    /** How many ants live in a nest. */
    public static final int RESIDENTS = 3;
    /** At most this many out at once. */
    public static final int MAX_OUT = 2;
    /** Ticks for a lost ant to be replaced. */
    public static final int REPLACE_TICKS = 6000;
    public static final int SLOTS = 9;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int inside = RESIDENTS;
    private int out;
    private int replaceTimer;

    public HomeNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOME_NEST.get(), pos, state);
    }

    public int inside() {
        return inside;
    }

    public int out() {
        return out;
    }

    /** Whether ants go out: daylight, no rain. */
    public static boolean foragingWeather(Level level) {
        return level.isBrightOutside() && !level.isRaining();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HomeNestBlockEntity nest) {
        if (!(level instanceof ServerLevel server) || level.getGameTime() % 20 != 0) {
            return;
        }
        if (nest.inside + nest.out < RESIDENTS && (nest.replaceTimer += 20) >= REPLACE_TICKS) {
            nest.replaceTimer = 0;
            nest.inside++;
            nest.setChanged();
        }
        if (foragingWeather(level) && nest.inside > 0 && nest.out < MAX_OUT && level.getRandom().nextInt(8) == 0) {
            nest.release(server);
        }
    }

    /**
     * Sends one ant out to forage; returns it, or null if none is home or there is no way out. It crawls out of a side
     * of the nest onto open ground, not out of the top: a mob standing on the low nest block finds no path anywhere.
     */
    public @Nullable WildAnt release(ServerLevel level) {
        BlockPos exit = exit(level);
        if (inside <= 0 || exit == null || !(getBlockState().getBlock() instanceof WildNestBlock block)) {
            return null;
        }
        WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (ant == null) {
            return null;
        }
        ant.setSpecies(block.species());
        ant.setCaste(Caste.WORKER);
        ant.snapTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        ant.setNest(worldPosition);
        level.addFreshEntity(ant);
        inside--;
        out++;
        setChanged();
        return ant;
    }

    /** A free spot beside the nest (a random side first; one with ground under it if there is one), or null if walled in. */
    private @Nullable BlockPos exit(ServerLevel level) {
        List<Direction> sides = new java.util.ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        java.util.Collections.shuffle(sides, new java.util.Random(level.getRandom().nextLong()));
        BlockPos open = null;
        for (Direction side : sides) {
            BlockPos spot = worldPosition.relative(side);
            if (level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) {
                if (level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                    return spot;
                }
                open = open == null ? spot : open;
            }
        }
        return open;
    }

    /** An ant came home with what it carried; what doesn't fit in the store is returned. */
    public ItemStack returned(ItemStack carried) {
        out = Math.max(0, out - 1);
        inside = Math.min(RESIDENTS, inside + 1);
        ItemStack rest = store(carried);
        setChanged();
        return rest;
    }

    /** Puts a stack into the store, filling partial stacks first; returns what didn't fit. */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int slot = 0; slot < SLOTS && !rest.isEmpty(); slot++) {
                ItemStack here = items.get(slot);
                if (pass == 0 && !here.isEmpty() && ItemStack.isSameItemSameComponents(here, rest)) {
                    int move = Math.min(rest.getCount(), here.getMaxStackSize() - here.getCount());
                    here.grow(move);
                    rest.shrink(move);
                } else if (pass == 1 && here.isEmpty()) {
                    items.set(slot, rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
        }
        setChanged();
        return rest;
    }

    /** An ant out foraging died: it is replaced in time. */
    public void lost() {
        out = Math.max(0, out - 1);
        setChanged();
    }

    /** The nest is broken: the ants inside come out and go wild, and the store drops (as for any container). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && state.getBlock() instanceof WildNestBlock block) {
            for (int i = 0; i < inside; i++) {
                WildAnt ant = ModEntities.WILD_ANT.get().create(server, EntitySpawnReason.TRIGGERED);
                if (ant != null) {
                    ant.setSpecies(block.species());
                    ant.setCaste(Caste.WORKER);
                    ant.snapTo(pos.getX() + 0.5 + server.getRandom().nextGaussian() * 0.3, pos.getY() + 0.3,
                            pos.getZ() + 0.5 + server.getRandom().nextGaussian() * 0.3, server.getRandom().nextFloat() * 360, 0);
                    server.addFreshEntity(ant);
                }
            }
            inside = 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("inside", inside);
        output.putInt("out", out);
        output.putInt("replace_timer", replaceTimer);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inside = input.getIntOr("inside", RESIDENTS);
        out = input.getIntOr("out", 0);
        replaceTimer = input.getIntOr("replace_timer", 0);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    // --- The store: hoppers and pipes take from it on any side; nothing goes in from outside. ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}
