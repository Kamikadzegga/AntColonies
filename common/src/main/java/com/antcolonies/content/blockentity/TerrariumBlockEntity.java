package com.antcolonies.content.blockentity;

import java.util.HashSet;
import java.util.Set;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.ColonyLayout;
import com.antcolonies.colony.model.ColonyNetwork;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.evolution.EvolutionLogic;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.content.menu.TerrariumMenu;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The heart of a colony: it holds the colony's numbers, its Queen, and runs the colony's simulation steps. Its slots
 * take a Queen Test Tube (the Queen moves in, the empty tube stays), things to feed (food, evolution materials, caught
 * ants, eggs, larvae, Evolution Catalysts) and give back empty tubes.
 *
 * <p>While the Terrarium is unloaded its colony stands still. When it loads again it catches up on at most the
 * configured offline minutes (more with Simulation Upgrades), never on the whole time it was away.
 */
public class TerrariumBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, com.antcolonies.content.block.ComparatorSource {
    /** Comparators read how full the colony's food store is. */
    @Override
    public int comparatorSignal() {
        return com.antcolonies.content.block.ComparatorSource.fill(colony.food(), colony.stats().foodCapacity());
    }

    public static final int SLOT_QUEEN = 0;
    public static final int SLOT_FEED = 1;
    public static final int SLOT_OUTPUT = 2;
    /** Food only, so food never blocks the feed slot (Test Tubes, ants, catalysts and evolution materials). */
    public static final int SLOT_FOOD = 3;
    /** Eggs (or larvae) one Ant Egg Cluster (or Larva Cluster) adds to a working colony of its species. */
    public static final int CLUSTER_BROOD = 3;
    public static final int UPGRADE_SLOTS = 4;
    /** Block events the client turns into effects. */
    public static final int EVENT_EVOLVED = 1;
    public static final int EVENT_ABSORBED = 2;
    public static final int EVENT_QUEEN = 3;
    public static final int EVENT_QUEEN_GREW = 4;
    private static final int RESCAN_INTERVAL = 200;
    private static final int FEED_INTERVAL = 10;
    private static final int EVOLUTION_INTERVAL = 40;

    private final Colony colony = new Colony();
    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return acceptsUpgrade(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            TerrariumBlockEntity.this.setChanged();
        }
    };
    private long scannedVersion = -1;
    // Timers start long ago, never at Long.MIN_VALUE: "now - then" would overflow to a negative number, and the network
    // scan, the climate check and evolution attempts would never come due.
    private long scannedAt = Long.MIN_VALUE / 2;
    private int feedStatus;
    private long lastEvolutionAttempt = Long.MIN_VALUE / 2;
    private int lastSyncHash;
    private boolean caughtUp;
    // Climate, looked up now and then.
    private float temperature = 0.8F;
    /** The climate the block under the Terrarium gives it (see {@link Climate#ofFloor}), if any. Synced for the screens. */
    private @Nullable Climate floor;
    private boolean underground;
    private boolean nether;
    private long climateAt = Long.MIN_VALUE / 2;
    /** The client's animation state (TerrariumVisuals); untyped so the server never loads client classes. */
    public @Nullable Object clientVisuals;

    public TerrariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERRARIUM.get(), pos, state);
    }

    public Colony colony() {
        return colony;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public int feedStatus() {
        return feedStatus;
    }

    public static boolean acceptsUpgrade(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade
                && com.antcolonies.colony.upgrade.UpgradeEffects.TerrariumAccepts.types().contains(upgrade.type());
    }

    public int upgradeTier(UpgradeType type) {
        int sum = 0;
        for (int slot = 0; slot < upgrades.getContainerSize(); slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.type() == type) {
                sum += upgrade.tier();
            }
        }
        return sum;
    }

    // --- Climate ---

    private void updateClimate(Level level) {
        if (level.getGameTime() - climateAt < 600) {
            return;
        }
        climateAt = level.getGameTime();
        temperature = level.getBiome(worldPosition).value().getBaseTemperature();
        underground = !level.canSeeSky(worldPosition.above());
        nether = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, worldPosition);
    }

    /** Reads the block under the Terrarium (cheap: once a second). */
    private void updateFloor(Level level) {
        Climate now = Climate.ofFloor(level.getBlockState(worldPosition.below()));
        if (now != floor) {
            floor = now;
            syncNow();
        }
    }

    public @Nullable Climate floor() {
        return floor;
    }

    public double climateMismatch(Climate climate) {
        if (floor == climate) {
            return 0;
        }
        return climate.mismatch(temperature, underground, nether);
    }

    public boolean tooCold(Climate climate) {
        return climate.tooCold(temperature, nether);
    }

    // --- Ticking ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerrariumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long time = level.getGameTime();
        be.updateClimate(level);
        if (time % 20 == 0) {
            be.updateFloor(level);
        }
        if (time % FEED_INTERVAL == 0) {
            be.processSlots(serverLevel);
        }
        long version = ColonyNetwork.version(level);
        if (version != be.scannedVersion && time - be.scannedAt >= 10 || time - be.scannedAt >= RESCAN_INTERVAL) {
            be.rescan(serverLevel);
        }
        int interval = AntConfig.get(AntConfig.SIMULATION_INTERVAL);
        if ((time + Math.floorMod(pos.asLong(), interval)) % interval == 0) {
            be.simulate(serverLevel, time, interval);
        }
        // Every second while someone has this Terrarium's screen open (with the details its pages show: the history, the
        // Storage and the network); otherwise every five seconds is plenty for the animated ants (each update carries the
        // whole colony, about 3 KB).
        if (time % 20 == 0) {
            if (be.hasViewer(serverLevel)) {
                be.syncNow();
            } else if (time % 100 == 0) {
                be.syncIfChanged();
            }
        }
    }

    private boolean hasViewer(ServerLevel level) {
        for (var player : level.players()) {
            if (player.containerMenu instanceof com.antcolonies.content.menu.TerrariumMenu menu && menu.terrarium() == this) {
                return true;
            }
        }
        return false;
    }

    /** Runs the simulation: one step per interval, and after loading a bounded catch-up. */
    /** Lets the colony advancements check this colony for one player (near it, or opening it). */
    private void checkAdvancements(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        com.antcolonies.core.advancement.ModTriggers.COLONY.get().trigger(player, colony, com.antcolonies.core.registry.ModRegistries.species(level.registryAccess(), colony.species()));
    }

    private void simulate(ServerLevel level, long time, int interval) {
        long last = ColonyAccess.lastSimTime(colony);
        if (!caughtUp) {
            caughtUp = true;
            if (last != Long.MIN_VALUE && time - last > interval * 2L) {
                double catchUp = catchUpMinutes(time - last, upgradeTier(UpgradeType.SIMULATION));
                // In steps of at most two minutes, so the result matches running all along.
                while (catchUp > 0) {
                    double dt = Math.min(2.0, catchUp);
                    ColonySimulator.step(this, level, dt);
                    catchUp -= dt;
                }
            }
        }
        ColonySimulator.step(this, level, interval / 1200.0);
        // Colony advancements, for the players close enough to watch it (every five seconds).
        if (time % 100 < interval) {
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player.blockPosition().distSqr(worldPosition) < 16 * 16) {
                    checkAdvancements(level, player);
                }
            }
        }
        ColonyAccess.setLastSimTime(colony, time);
        setChanged();
    }

    /**
     * How many minutes a colony catches up on after {@code awayTicks} unloaded: the time away, but never more than the
     * configured offline minutes plus what its Simulation Upgrades allow.
     */
    public static double catchUpMinutes(long awayTicks, int simulationTiers) {
        double allowance = AntConfig.get(AntConfig.OFFLINE_CATCH_UP_MINUTES)
                + AntConfig.simulationUpgradeMinutes(Math.min(UpgradeType.MAX_TIER, simulationTiers));
        return Math.max(0, Math.min(awayTicks / 1200.0, allowance));
    }

    /** Looks at the ring of chambers around the Terrarium again. */
    public void rescan(ServerLevel level) {
        scannedVersion = ColonyNetwork.version(level);
        scannedAt = level.getGameTime();
        colony.setLayout(ColonyNetwork.scan(level, worldPosition));
    }

    /** The Queen slot and the feed slot. */
    /** Handles the Queen and feed slots (every few ticks; game tests call it directly). */
    public void processSlots(ServerLevel level) {
        ItemStack queenSlot = items.get(SLOT_QUEEN);
        QueenData queen = queenSlot.get(ModDataComponents.QUEEN.get());
        if (queen != null && queenSlot.is(ModItems.QUEEN_TEST_TUBE.get()) && colony.queen() == null && queenMayEnter()
                && giveBack(new ItemStack(ModItems.TEST_TUBE.get()))) {
            // The Queen moves in; her empty tube goes to the output, so the slot is free for taking her out again.
            boolean alone = colony.isEmptyOfAnts();
            colony.setQueen(queen);
            if (alone) {
                // A single Queen in a completely empty Terrarium: Queen-only founding. Ants, eggs or larvae put in later
                // (in this same pass too, from the feed slot) end it, so a colony moved with its ants never needs it.
                colony.startFounding();
            }
            items.set(SLOT_QUEEN, ItemStack.EMPTY);
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_QUEEN, 0);
            level.playSound(null, worldPosition, ModSounds.QUEEN_ACTIVITY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            setChanged();
        } else if (queenSlot.is(ModItems.TEST_TUBE.get()) && colony.queen() != null) {
            // An empty Test Tube in the Queen slot takes her out, into the output.
            removeQueen();
        }
        feedStatus = 0;
        // The food slot: eaten as the food bar has room. A full bar is no problem, the food just waits.
        ItemStack food = items.get(SLOT_FOOD);
        if (!food.isEmpty() && ModDataMaps.food(food) != null) {
            eat(level, food);
        }
        ItemStack feed = items.get(SLOT_FEED);
        if (feed.isEmpty()) {
            setChanged();
            return;
        }
        AntSample ants = feed.get(ModDataComponents.ANTS.get());
        Identifier species = feed.get(ModDataComponents.SPECIES.get());
        if (ants != null && feed.is(ModItems.ANT_TEST_TUBE.get())) {
            if (!accepts(ants.species()) || ants.caste() == Caste.QUEEN) {
                feedStatus = ColonyStatus.INCOMPATIBLE_SPECIES.bit();
                return;
            }
            if (!giveBack(new ItemStack(ModItems.TEST_TUBE.get()))) {
                feedStatus = ColonyStatus.STORAGE_FULL.bit();
                return;
            }
            adopt(ants.species());
            // Caught soldiers join as workers: colonies have no soldiers.
            colony.addWorkers(ants.count());
            feed.shrink(1);
        } else if (feed.is(ModItems.TEST_TUBE.get())) {
            // Empty Test Tubes fill with the colony's workers, a tube at a time into the output. This is how a colony moves: a
            // broken Terrarium keeps none of its ants.
            if (!items.get(SLOT_OUTPUT).isEmpty()) {
                feedStatus = ColonyStatus.STORAGE_FULL.bit();
                return;
            }
            int count = (int) Math.min(AntSample.MAX, colony.workers());
            if (count <= 0) {
                feedStatus = ColonyStatus.NO_WORKERS.bit();
                return;
            }
            colony.addWorkers(-count);
            items.set(SLOT_OUTPUT, com.antcolonies.content.item.TweezersItem.antTube(new AntSample(colony.species(), Caste.WORKER, count)));
            feed.shrink(1);
            syncNow();
        } else if (feed.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof com.antcolonies.content.block.BroodClusterBlock cluster) {
            // A cluster taken from an anthill with Silk Touch: only a working colony (a Queen and workers, past founding)
            // of its species takes it in. It waits in the slot meanwhile.
            if (colony.queen() == null) {
                feedStatus = ColonyStatus.NO_QUEEN.bit();
                return;
            }
            if (colony.founding().active() || colony.workers() < 1) {
                feedStatus = ColonyStatus.NO_WORKERS.bit();
                return;
            }
            if (species == null || !species.equals(colony.species())) {
                feedStatus = ColonyStatus.INCOMPATIBLE_SPECIES.bit();
                return;
            }
            if (cluster.eggs()) {
                colony.addEggs(feed.getCount() * (double) CLUSTER_BROOD);
            } else {
                colony.addLarvae(feed.getCount() * (double) CLUSTER_BROOD);
            }
            feed.setCount(0);
        } else if (species != null && (feed.is(ModItems.ANT_EGG.get()) || feed.is(ModItems.ANT_LARVA.get()))) {
            if (!accepts(species)) {
                feedStatus = ColonyStatus.INCOMPATIBLE_SPECIES.bit();
                return;
            }
            adopt(species);
            if (feed.is(ModItems.ANT_EGG.get())) {
                colony.addEggs(feed.getCount() * 4.0);
            } else {
                colony.addLarvae(feed.getCount() * 4.0);
            }
            feed.setCount(0);
        } else if (feed.is(ModItems.EVOLUTION_CATALYST.get())) {
            colony.addCatalyst(feed.getCount());
            feed.setCount(0);
        } else if (EvolutionLogic.find(level.registryAccess(), colony, feed) != null) {
            if (level.getGameTime() - lastEvolutionAttempt < EVOLUTION_INTERVAL) {
                return;
            }
            lastEvolutionAttempt = level.getGameTime();
            EvolutionLogic.Result result = EvolutionLogic.attempt(level.registryAccess(), colony, feed, upgradeTier(UpgradeType.EVOLUTION), level.dimension(),
                    level.getRandom());
            switch (result.outcome()) {
                case NOT_READY -> feedStatus = ColonyStatus.INSUFFICIENT_MATERIALS.bit();
                case WRONG_DIMENSION -> feedStatus = ColonyStatus.WRONG_DIMENSION.bit();
                case ABSORBED -> {
                    absorb(feed);
                    level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_ABSORBED, 0);
                    level.playSound(null, worldPosition, ModSounds.QUEEN_ACTIVITY.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
                }
                case EVOLVED -> {
                    absorb(feed);
                    level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_EVOLVED, 0);
                    level.playSound(null, worldPosition, ModSounds.EVOLUTION.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    syncNow();
                }
                case NONE -> {
                }
            }
        } else {
            // Food left in the feed slot (from before the food slot): eaten like the food slot's.
            if (ModDataMaps.food(feed) == null) {
                feedStatus = ColonyStatus.INCOMPATIBLE_SPECIES.bit();
                return;
            }
            eat(level, feed);
        }
        setChanged();
    }

    /**
     * Eats from a stack of food while the food bar has room, or, for a special food the species needs, while its special
     * food store (a quarter of the food room, as in {@code FoodIntake}) is not full. The rest waits in the slot.
     */
    private void eat(ServerLevel level, ItemStack stack) {
        ModDataMaps.AntFood value = ModDataMaps.food(stack);
        if (value == null) {
            return;
        }
        double capacity = colony.stats().foodCapacity() > 0 ? colony.stats().foodCapacity() : AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY);
        double specialRoom = capacity / 4;
        String kind = value.specialKind();
        boolean special = !kind.isEmpty() && kind.equals(speciesDiet(level)) && value.special() > 0;
        while (!stack.isEmpty() && (colony.food() + value.food() <= capacity || special && colony.specialFood() < specialRoom)) {
            colony.addFood(value.food(), special ? value.special() : 0);
            stack.shrink(1);
        }
    }

    /** Whether a stack belongs in the food slot: food the colony eats. */
    public static boolean isFood(ItemStack stack) {
        return ModDataMaps.food(stack) != null;
    }

    /**
     * Whether a stack belongs in the feed slot: anything but plain food (ants in tubes, empty tubes, eggs, larvae,
     * catalysts, evolution materials; food only when it is also this colony's evolution material).
     */
    public boolean isFeed(ItemStack stack) {
        return !isFood(stack) || level != null && com.antcolonies.colony.evolution.EvolutionLogic.find(level.registryAccess(), colony, stack) != null;
    }

    private String speciesDiet(Level level) {
        var data = com.antcolonies.core.registry.ModRegistries.species(level.registryAccess(), colony.species());
        return data == null ? "" : data.diet().specialKind();
    }

    /**
 * The Queen grew with her colony: a shimmer of genes, her sound, and a note to the players nearby saying which traits
 * went up.
 */
public void queenGrew(ServerLevel level, int stage, java.util.List<com.antcolonies.colony.genetics.Trait> raised) {
    level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_QUEEN_GREW, 0);
    level.playSound(null, worldPosition, ModSounds.QUEEN_ACTIVITY.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
    net.minecraft.network.chat.MutableComponent traits = Component.empty();
    for (int i = 0; i < raised.size(); i++) {
        if (i > 0) {
            traits.append(", ");
        }
        traits.append(Component.translatable(raised.get(i).translationKey()).withStyle(net.minecraft.ChatFormatting.GREEN));
    }
    Component message = Component.translatable("message.antcolonies.queen_grew", com.antcolonies.content.item.Tooltips.species(colony.species()), stage, traits);
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
        if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) < 48 * 48) {
            player.sendSystemMessage(message);
        }
    }
    syncNow();
    setChanged();
}

/** Caught ants join a colony of their own species, or an empty colony without a Queen (which becomes theirs). */
    private boolean accepts(Identifier species) {
        return species.equals(colony.species()) || colony.queen() == null && colony.exactAdults() < 1;
    }

    /** An empty colony takes on the species of the first ants put in. */
    private void adopt(Identifier species) {
        colony.adoptSpecies(species);
    }

    /**
     * The Queen takes one of the evolution material. What the item leaves behind (the bucket of a bucket of water, lava
     * or another fluid fed to a Liquid Ant line) goes to the output slot, or drops by the Terrarium if that is taken.
     */
    private void absorb(ItemStack feed) {
        net.minecraft.world.item.ItemStackTemplate remainder = feed.getCraftingRemainder();
        feed.shrink(1);
        if (remainder != null && level != null) {
            ItemStack left = remainder.create();
            if (!giveBack(left)) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, left);
            }
        }
    }

    /** Puts an item into the output slot; false when it doesn't fit. */
    private boolean giveBack(ItemStack stack) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, stack);
            return true;
        }
        if (ItemStack.isSameItemSameComponents(output, stack) && output.getCount() + stack.getCount() <= output.getMaxStackSize()) {
            output.grow(stack.getCount());
            return true;
        }
        return false;
    }

    /**
     * The Queen leaves into an empty Test Tube in the Queen slot, and her tube goes to the output (the colony keeps its
     * ants, but lays no more eggs). Waits while the output is taken.
     */
    public boolean removeQueen() {
        QueenData queen = colony.queen();
        ItemStack slot = items.get(SLOT_QUEEN);
        if (queen == null || !slot.is(ModItems.TEST_TUBE.get()) || !items.get(SLOT_OUTPUT).isEmpty()) {
            return false;
        }
        ItemStack tube = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        tube.set(ModDataComponents.QUEEN.get(), queen);
        slot.shrink(1);
        items.set(SLOT_OUTPUT, tube);
        ColonyAccess.killQueen(colony);
        // Keep the tube from flowing straight back in.
        colonyQueenRemovedAt = level == null ? 0 : level.getGameTime();
        syncNow();
        setChanged();
        return true;
    }

    private long colonyQueenRemovedAt = Long.MIN_VALUE;

    /** Whether a Queen Test Tube in the Queen slot should move in now (not right after she was taken out). */
    public boolean queenMayEnter() {
        return level == null || colonyQueenRemovedAt == Long.MIN_VALUE || level.getGameTime() - colonyQueenRemovedAt > 40;
    }

    // --- Syncing ---

    private int syncHash() {
        int hash = Long.hashCode(colony.adults());
        hash = hash * 31 + Long.hashCode(colony.eggs());
        hash = hash * 31 + Long.hashCode(colony.larvae());
        hash = hash * 31 + (int) colony.food();
        hash = hash * 31 + colony.status();
        hash = hash * 31 + colony.stage();
        hash = hash * 31 + colony.species().hashCode();
        hash = hash * 31 + (colony.queen() == null ? 0 : colony.queen().hashCode());
        hash = hash * 31 + colony.assignments().hashCode();
        hash = hash * 31 + (int) (colony.health() * 100);
        hash = hash * 31 + feedStatus;
        return hash;
    }

    private void syncIfChanged() {
        int hash = syncHash();
        if (hash != lastSyncHash) {
            syncNow();
        }
    }

    public void syncNow() {
        if (level != null && !level.isClientSide()) {
            lastSyncHash = syncHash();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level != null && level.isClientSide()) {
            com.antcolonies.client.ClientHooks.terrariumEvent(this, id);
        }
        return true;
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return items.size();
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
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_QUEEN -> stack.is(ModItems.QUEEN_TEST_TUBE.get()) || stack.is(ModItems.TEST_TUBE.get());
            case SLOT_FEED -> isFeed(stack);
            case SLOT_FOOD -> isFood(stack);
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    private static final int[] FEED = {SLOT_FOOD, SLOT_FEED, SLOT_OUTPUT};

    /** Hoppers put food in the food slot and anything else in the feed slot, and take empty tubes from the output slot. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return FEED;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return (slot == SLOT_FOOD || slot == SLOT_FEED) && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT;
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.antcolonies.terrarium");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            checkAdvancements(serverLevel, serverPlayer);
        }
        return new TerrariumMenu(containerId, inventory, this);
    }

    public AABB getRenderBox() {
        return new AABB(worldPosition).inflate(0.25);
    }

    // --- Breaking: the colony goes with the item ---

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            Containers.dropContents(level, pos, upgrades);
        }
    }

    // A broken Terrarium keeps no colony: its Queen and ants leave only in Test Tubes (the Queen and feed slots).

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.clearContent();
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("upgrades", ItemStack.OPTIONAL_CODEC)) {
            if (slot < UPGRADE_SLOTS) {
                upgrades.setItem(slot++, stack);
            }
        }
        input.child("colony").ifPresent(colony::load);
        feedStatus = input.getIntOr("feed_status", 0);
        floor = input.getString("floor").flatMap(name -> java.util.Arrays.stream(Climate.values()).filter(c -> c.getSerializedName().equals(name)).findFirst()).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        saveUpgrades(output);
        colony.save(output.child("colony"), false);
    }

    private void saveUpgrades(ValueOutput output) {
        ValueOutput.TypedOutputList<ItemStack> list = output.list("upgrades", ItemStack.OPTIONAL_CODEC);
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            list.add(upgrades.getItem(slot));
        }
    }

    /** Clients get the colony with its last step's results, for the GUI and the animation. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        ContainerHelper.saveAllItems(output, items);
        // The upgrades too: loading clears them, so an update without them would empty the slots on the client.
        saveUpgrades(output);
        ValueOutput colonyOutput = output.child("colony");
        colony.save(colonyOutput, true);
        if (level instanceof ServerLevel serverLevel && hasViewer(serverLevel)) {
            // The Terrarium's screen is open: its pages need the history, the Storage's contents and the network too.
            com.antcolonies.colony.resource.ColonyStorage.summarize(serverLevel, colony);
            colony.saveDetails(colonyOutput);
        }
        output.putInt("feed_status", feedStatus);
        if (floor != null) {
            output.putString("floor", floor.getSerializedName());
        }
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
