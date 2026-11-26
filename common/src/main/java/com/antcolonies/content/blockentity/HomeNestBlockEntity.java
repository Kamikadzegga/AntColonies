package com.antcolonies.content.blockentity;

import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.Random;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

/** A nest a player placed ({@code home=true}): its worker ants go out to farm and carry the harvest into its store. */
public class HomeNestBlockEntity extends BlockEntity implements WorldlyContainer, RaidedNest {
    public static final int SLOTS = 9;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int inside = residents();
    private int out;
    private int replaceTimer;
    private long recoveredAt;

    @Override
    public long recoveredAt() {
        return recoveredAt;
    }

    @Override
    public void setRecoveredAt(long gameTime) {
        recoveredAt = gameTime;
        setChanged();
    }

    public HomeNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOME_NEST.get(), pos, state);
    }

    /** Only while the nest is a home: a wild nest has a {@link WildNestBlockEntity}. */
    @Override
    public boolean isValidBlockState(BlockState state) {
        return super.isValidBlockState(state) && state.getValue(WildNestBlock.HOME);
    }

    public static int residents() {
        return AntConfig.get(AntConfig.HOME_NEST_ANTS);
    }

    /** At most this many out at once: all but one, so the nest is never left empty. */
    private static int maxOut() {
        return Math.max(1, residents() - 1);
    }

    public int inside() {
        return inside;
    }

    public int out() {
        return out;
    }

    public static boolean foragingWeather(Level level) {
        return level.isBrightOutside() && !level.isRaining();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HomeNestBlockEntity nest) {
        WildNestBlock.recover(level, pos, state, nest);
        if (!(level instanceof ServerLevel server) || level.getGameTime() % 20 != 0) {
            return;
        }
        if (nest.inside + nest.out < residents()
                && (nest.replaceTimer += 20) >= AntConfig.get(AntConfig.HOME_NEST_REFILL_MINUTES) * 1200) {
            nest.replaceTimer = 0;
            nest.inside++;
            nest.setChanged();
        }
        if (foragingWeather(level) && nest.inside > 0 && nest.out < maxOut() && level.getRandom().nextInt(8) == 0) {
            nest.release(server);
        }
    }

    // --- The land around the nest ---

    /** What the nest's ants can work on, as last looked for: crops to harvest and plants to tend. */
    public record Land(List<BlockPos> ripe, List<BlockPos> growing) {}

    /** How long (ticks) one look at the land serves all the nest's ants. */
    public static final int LAND_SEARCH_INTERVAL = 100;
    private final Land land = new Land(new ArrayList<>(), new ArrayList<>());
    // "Long ago", not Long.MIN_VALUE: "now - then" would overflow.
    private long landSearched = Long.MIN_VALUE / 2;
    private int landSearches;

    /** The land around the nest: one search for all its ants, made again when the last is five seconds old. */
    public Land land(ServerLevel level) {
        if (level.getGameTime() - landSearched >= LAND_SEARCH_INTERVAL) {
            landSearched = level.getGameTime();
            searchLand(level);
        }
        return land;
    }

    /** An ant has taken this spot: the nest's other ants leave it alone until the next search. */
    public void claim(BlockPos pos) {
        land.ripe().remove(pos);
        land.growing().remove(pos);
    }

    /** How many times this nest has searched its land. */
    public int landSearches() {
        return landSearches;
    }

    /**
     * Looks at the blocks within {@link WildAnt#NEST_RANGE} of the nest, two up and two down. Only chunks that are loaded
     * (none is loaded for it), and a chunk section that holds no crop or sapling at all is passed over whole.
     */
    private void searchLand(ServerLevel level) {
        landSearches++;
        land.ripe().clear();
        land.growing().clear();
        int range = WildAnt.NEST_RANGE;
        int minX = worldPosition.getX() - range;
        int maxX = worldPosition.getX() + range;
        int minZ = worldPosition.getZ() - range;
        int maxZ = worldPosition.getZ() + range;
        int minY = Math.max(level.getMinY(), worldPosition.getY() - 2);
        int maxY = Math.min(level.getMaxY(), worldPosition.getY() + 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    if (section.hasOnlyAir() || !section.maybeHas(WildAnt::farmed)) {
                        continue;
                    }
                    for (int y = Math.max(minY, sectionY << 4); y <= Math.min(maxY, (sectionY << 4) + 15); y++) {
                        for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                            for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (!WildAnt.farmed(state)) {
                                    continue;
                                }
                                pos.set(x, y, z);
                                if (WildAnt.ripe(level, pos, state)) {
                                    land.ripe().add(pos.immutable());
                                } else if (WildAnt.growable(level, pos, state)) {
                                    land.growing().add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Sends one ant out to forage, or returns null. It leaves by a side: a mob on top of the low nest finds no path. */
    public @Nullable WildAnt release(ServerLevel level) {
        BlockPos exit = exit(level, worldPosition);
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
        // An ant the world did not take (another mod refused it) never went out.
        if (!level.addFreshEntity(ant)) {
            return null;
        }
        inside--;
        out++;
        setChanged();
        return ant;
    }

    /** A free spot beside a nest, one with ground under it if there is one, or null if walled in. */
    public static @Nullable BlockPos exit(ServerLevel level, BlockPos nest) {
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(sides, new Random(level.getRandom().nextLong()));
        BlockPos open = null;
        for (Direction side : sides) {
            BlockPos spot = nest.relative(side);
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
        inside = Math.min(residents(), inside + 1);
        ItemStack rest = store(carried);
        setChanged();
        return rest;
    }

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
        output.putLong("recovered_at", recoveredAt);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inside = input.getIntOr("inside", residents());
        out = input.getIntOr("out", 0);
        replaceTimer = input.getIntOr("replace_timer", 0);
        recoveredAt = input.getLongOr("recovered_at", 0);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    // --- The store: hoppers and pipes take from it on any side; nothing goes in from outside ---

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
