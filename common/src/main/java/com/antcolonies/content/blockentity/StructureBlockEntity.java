package com.antcolonies.content.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

import com.antcolonies.colony.breeding.QueenRearing;
import com.antcolonies.colony.model.ForagingMode;
import com.antcolonies.colony.network.ConnectorNetwork;
import com.antcolonies.content.block.ColonyPartBlock;
import com.antcolonies.content.block.ComparatorSource;
import com.antcolonies.content.block.ExpeditionSiteBlock;
import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import org.jspecify.annotations.Nullable;

/** Any colony chamber: its slots, upgrades and settings. Its Terrarium's colony does the simulating and tells it the results. */
public class StructureBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, ComparatorSource {
    @Override
    public int comparatorSignal() {
        return kind.job().any() ? ComparatorSource.fill(assigned, capacityShown) : slotFill();
    }

    /** How full the open slots are: vanilla's reading counts locked Storage slots too and would never reach a full signal. */
    private int slotFill() {
        int open = Math.min(unlockedSlots(), getContainerSize());
        if (open <= 0) {
            return 0;
        }
        float fill = 0;
        for (int slot = 0; slot < open; slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                fill += (float) stack.getCount() / getMaxStackSize(stack);
            }
        }
        return Mth.lerpDiscrete(fill / open, 0, 15);
    }

    /** A structure not linked by any Terrarium for this long counts as disconnected. */
    private static final int LINK_TIMEOUT = 400;
    public static final int DATA_ASSIGNED = 0;
    public static final int DATA_REQUESTED = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_STATUS = 3;
    public static final int DATA_RATE = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_REARING = 6;
    public static final int DATA_REARING_STATUS = 7;
    public static final int DATA_COLONY_STATUS = 8;
    public static final int DATA_LINKED = 9;
    public static final int DATA_UNLOCKED = 10;
    public static final int DATA_CONTESTED = 11;
    /** A bit per upgrade slot whose upgrade can't come out now (canRemoveUpgrade). */
    public static final int DATA_UPGRADES_HELD = 12;
    /** Job sites: the Foraging Site's mode, whether the site is turned on, and its priority (0 low, 1 normal, 2 high). */
    public static final int DATA_MODE = 13;
    public static final int DATA_ENABLED = 14;
    public static final int DATA_PRIORITY = 15;
    /** Colony Connectors: their settings and what they moved (food and items per minute, times 10). */
    public static final int DATA_CHANNEL = 16;
    public static final int DATA_FOOD_MODE = 17;
    public static final int DATA_RESERVE = 18;
    public static final int DATA_TARGET = 19;
    public static final int DATA_RATE_LIMIT = 20;
    public static final int DATA_ITEM_MODE = 21;
    public static final int DATA_FOOD_IN = 22;
    public static final int DATA_FOOD_OUT = 23;
    public static final int DATA_ITEMS_MOVED = 24;
    public static final int DATA_MEMBERS = 25;
    /** Queen Chambers, what the sample in the slot does: a CrossState, the hybrid's chance in thousandths, the too-low trait average times 10. */
    public static final int DATA_CROSS = 26;
    public static final int DATA_CROSS_CHANCE = 27;
    public static final int DATA_CROSS_LEVEL = 28;
    public static final int DATA_COUNT = 29;

    /** Job site priorities: the colony hands out its workers to higher ones first. */
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_HIGH = 2;

    private final StructureKind kind;
    private NonNullList<ItemStack> items;
    private final SimpleContainer upgrades;
    /** Ants the player wants here; -1 for as many as it can take. */
    private int requested = -1;
    private @Nullable BlockPos terrarium;
    private long linkedAt = Long.MIN_VALUE;
    private int colonyStatus;
    private int assigned;
    private int capacityShown;
    private int siteStatus;
    private double rate;
    private double progress;
    /** When a Terrarium last found this chamber exactly as near to another colony (it then joins neither). */
    private long contestedAt = Long.MIN_VALUE / 2;
    private double rearingProgress;
    private int rearingStatus;
    /** Finds waiting to be paid out in processed form (Processing upgrades). */
    private final Map<SpeciesOutput, Double> processingCredit = new HashMap<>();
    private boolean syncPending;
    private int queensRaised;
    /** An ordinary Foraging Site's mode (what its workers look for). */
    private ForagingMode mode = ForagingMode.BALANCED;
    /** A job site turned off in the Jobs page takes no ants (like a redstone signal). */
    private boolean enabled = true;
    private int priority = PRIORITY_NORMAL;
    /** A Colony Connector's settings (null for every other chamber). */
    private final @Nullable ConnectorSettings connector;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_ASSIGNED -> assigned;
                case DATA_REQUESTED -> requested;
                case DATA_CAPACITY -> capacity();
                case DATA_STATUS -> siteStatus;
                case DATA_RATE -> (int) Math.min(Integer.MAX_VALUE, Math.round(rate * 10_000));
                case DATA_PROGRESS -> (int) Math.round(Math.clamp(progress, 0, 1) * 1000);
                case DATA_REARING -> (int) rearingProgress;
                case DATA_REARING_STATUS -> rearingStatus;
                case DATA_COLONY_STATUS -> colonyStatus;
                case DATA_LINKED -> isLinked() ? 1 : 0;
                case DATA_UNLOCKED -> unlockedSlots();
                case DATA_CONTESTED -> isContested() ? 1 : 0;
                case DATA_UPGRADES_HELD -> upgradesHeld();
                case DATA_MODE -> mode.ordinal();
                case DATA_ENABLED -> enabled ? 1 : 0;
                case DATA_PRIORITY -> priority;
                case DATA_CHANNEL -> connector == null ? 0 : connector.channel;
                case DATA_FOOD_MODE -> connector == null ? 0 : connector.foodMode;
                case DATA_RESERVE -> connector == null ? 0 : connector.reserve;
                case DATA_TARGET -> connector == null ? 0 : connector.target;
                case DATA_RATE_LIMIT -> connector == null ? 0 : connector.rateLimit;
                case DATA_ITEM_MODE -> connector == null ? 0 : connector.itemMode;
                case DATA_FOOD_IN -> connector == null ? 0 : (int) Math.round(connector.foodIn * 10);
                case DATA_FOOD_OUT -> connector == null ? 0 : (int) Math.round(connector.foodOut * 10);
                case DATA_ITEMS_MOVED -> connector == null ? 0 : (int) Math.round(connector.itemsMoved * 10);
                case DATA_MEMBERS -> connector == null ? 0 : connector.members;
                case DATA_CROSS -> cross().state().ordinal();
                case DATA_CROSS_CHANCE -> (int) Math.round(cross().chance() * 1000);
                case DATA_CROSS_LEVEL -> (int) Math.round(cross().level() * 10);
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /** The client's animation state (NestVisuals, while its colony is upright); untyped so the server never loads client classes. */
    public @Nullable Object clientVisuals;

    public StructureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE.get(), pos, state);
        this.kind = state.getBlock() instanceof StructureBlock block ? block.structureKind() : StructureKind.STORAGE;
        this.items = NonNullList.withSize(kind.slots(), ItemStack.EMPTY);
        this.connector = kind == StructureKind.COLONY_CONNECTOR ? new ConnectorSettings() : null;
        this.upgrades = new SimpleContainer(kind.upgradeSlots()) {
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
                StructureBlockEntity.this.setChanged();
                contentsChanged();
            }
        };
    }

    public StructureKind kind() {
        return kind;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public ContainerData data() {
        return data;
    }

    // --- Upgrades ---

    public boolean acceptsUpgrade(ItemStack stack) {
        if (stack.getItem() instanceof UpgradeItem upgrade) {
            return kind.allows(upgrade.type());
        }
        return stack.is(ModItems.SPECIES_UPGRADE.get()) && kind.takesSpeciesUpgrades() && stack.has(ModDataComponents.SPECIES_UPGRADE.get());
    }

    public int upgradeTier(UpgradeType type) {
        if (!kind.allows(type)) {
            return 0;
        }
        int sum = 0;
        for (int slot = 0; slot < upgrades.getContainerSize(); slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.type() == type) {
                sum += upgrade.tier();
            }
        }
        return sum;
    }

    public int speciesUpgradeTier(Identifier species, SpeciesUpgradeKind upgradeKind) {
        if (!kind.takesSpeciesUpgrades()) {
            return 0;
        }
        int sum = 0;
        for (int slot = 0; slot < upgrades.getContainerSize(); slot++) {
            SpeciesUpgrade upgrade = upgrades.getItem(slot).get(ModDataComponents.SPECIES_UPGRADE.get());
            if (upgrade != null && upgrade.kind() == upgradeKind && upgrade.species().equals(species)) {
                sum += upgrade.tier();
            }
        }
        return sum;
    }

    // --- Jobs ---

    /** Ants this site takes at most. */
    public int capacity() {
        if (!kind.job().any()) {
            return 0;
        }
        return AntConfig.get(AntConfig.SITE_BASE_CAPACITY) + AntConfig.get(AntConfig.SITE_CAPACITY_PER_UPGRADE) * upgradeTier(UpgradeType.POPULATION);
    }

    public int requested() {
        return requested;
    }

    public void setRequested(int requested) {
        this.requested = requested < 0 ? -1 : Math.min(requested, capacity());
        setChanged();
    }

    public int assigned() {
        return assigned;
    }

    public double rate() {
        return rate;
    }

    public double progress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0, progress);
    }

    public double addProcessingCredit(SpeciesOutput output, double amount) {
        return processingCredit.merge(output, amount, Double::sum);
    }

    /** The colony's step tells the site what it got. */
    public void setAssignment(int assigned, int capacity, int status, double rate) {
        boolean visible = this.assigned != assigned || this.siteStatus != status;
        this.assigned = assigned;
        this.capacityShown = capacity;
        this.siteStatus = status;
        this.rate = rate;
        if (visible) {
            contentsChanged();
        }
        setChanged();
        // A Nether or End Foraging Site's portal glows while ants are out on expeditions through it.
        if (kind.expedition() && level != null && getBlockState().hasProperty(ExpeditionSiteBlock.ACTIVE)) {
            boolean active = assigned > 0;
            if (getBlockState().getValue(ExpeditionSiteBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, getBlockState().setValue(ExpeditionSiteBlock.ACTIVE, active),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    public int siteStatus() {
        return siteStatus;
    }

    public ForagingMode mode() {
        return kind == StructureKind.FORAGING_SITE ? mode : ForagingMode.BALANCED;
    }

    public void setMode(ForagingMode mode) {
        this.mode = mode;
        setChanged();
        contentsChanged();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
        contentsChanged();
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.clamp(priority, PRIORITY_LOW, PRIORITY_HIGH);
        setChanged();
        contentsChanged();
    }

    public @Nullable ConnectorSettings connector() {
        return connector;
    }

    // --- The link to a colony ---

    public void linkTo(BlockPos terrarium, int colonyStatus) {
        boolean changed = !terrarium.equals(this.terrarium);
        this.terrarium = terrarium.immutable();
        this.colonyStatus = colonyStatus;
        if (level != null) {
            linkedAt = level.getGameTime();
        }
        if (changed) {
            contentsChanged();
        }
    }

    public boolean isLinked() {
        if (level != null && level.isClientSide()) {
            // A client knows only the last sync, so it must not time the link out itself: its screens would forget the colony.
            return clientLinked();
        }
        return terrarium != null && level != null && level.getGameTime() - linkedAt < LINK_TIMEOUT;
    }

    public @Nullable BlockPos terrarium() {
        return isLinked() ? terrarium : null;
    }

    public int colonyStatus() {
        return colonyStatus;
    }

    public void markContested() {
        if (level != null) {
            boolean was = isContested();
            contestedAt = level.getGameTime();
            if (!was) {
                contentsChanged();
            }
        }
    }

    public boolean isContested() {
        return level != null && level.getGameTime() - contestedAt < LINK_TIMEOUT && !isLinked();
    }

    // --- Queen Chamber ---

    public double rearingProgress() {
        return rearingProgress;
    }

    public void setRearing(double progress, int status) {
        this.rearingProgress = progress;
        this.rearingStatus = status;
        setChanged();
    }

    public int rearingStatus() {
        return rearingStatus;
    }

    /** What the next Genetic Sample in this Queen Chamber would do to the next Queen it raises. */
    public QueenRearing.Cross cross() {
        if (kind == StructureKind.QUEEN_CHAMBER && level != null && terrarium() != null
                && level.getBlockEntity(terrarium()) instanceof TerrariumBlockEntity home) {
            int slot = QueenRearing.nextSampleSlot(this);
            return QueenRearing.cross(level.registryAccess(), home.colony().species(), home.colony().queen(),
                    slot < 0 ? null : QueenRearing.sampleOf(getItem(slot)));
        }
        return QueenRearing.NO_CROSS;
    }

    public void onQueenRaised() {
        queensRaised++;
        contentsChanged();
    }

    public int queensRaised() {
        return queensRaised;
    }

    // --- Storage ---

    public static final int STORAGE_BASE_SLOTS = 18;

    public int unlockedSlots() {
        return kind != StructureKind.STORAGE ? kind.slots() : storageSlots(upgradeTier(UpgradeType.STORAGE));
    }

    public static int storageSlots(int tier) {
        return Math.min(StructureKind.STORAGE.slots(), STORAGE_BASE_SLOTS + AntConfig.get(AntConfig.STORAGE_SLOTS_PER_UPGRADE) * tier);
    }

    /** How many of an item one slot holds: a Storage's slots hold a stack more per Storage Upgrade tier. */
    public int slotLimit(ItemStack stack) {
        if (kind == StructureKind.COLONY_CONNECTOR) {
            return 1; // a filter names an item; one is enough
        }
        return kind == StructureKind.STORAGE ? slotLimit(stack, upgradeTier(UpgradeType.STORAGE)) : Math.max(1, stack.getMaxStackSize());
    }

    private static int slotLimit(ItemStack stack, int storageTier) {
        return Math.max(1, stack.getMaxStackSize()) * (1 + storageTier);
    }

    /** Whether this upgrade can come out: a Storage Upgrade can't while the Storage holds more than it would without it. */
    public boolean canRemoveUpgrade(int upgradeSlot) {
        if (kind != StructureKind.STORAGE || !(upgrades.getItem(upgradeSlot).getItem() instanceof UpgradeItem upgrade)
                || upgrade.type() != UpgradeType.STORAGE) {
            return true;
        }
        int tierWithout = upgradeTier(UpgradeType.STORAGE) - upgrade.tier();
        int slotsWithout = storageSlots(tierWithout);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && (slot >= slotsWithout || stack.getCount() > slotLimit(stack, tierWithout))) {
                return false;
            }
        }
        return true;
    }

    private int upgradesHeld() {
        int held = 0;
        for (int slot = 0; slot < upgrades.getContainerSize(); slot++) {
            if (!canRemoveUpgrade(slot)) {
                held |= 1 << slot;
            }
        }
        return held;
    }

    /** One saved slot: the item (as a single) and how many. */
    public record StoredSlot(int slot, ItemStack item, int count) {
        public static final Codec<StoredSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(StoredSlot::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(StoredSlot::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(StoredSlot::count)
        ).apply(i, StoredSlot::new));
    }

    /** Hoppers, pipes and the screen ask this: a compacting Storage takes more than a stack per slot. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return slotLimit(stack);
    }

    @Override
    public int getMaxStackSize() {
        return kind == StructureKind.STORAGE ? 64 * (1 + upgradeTier(UpgradeType.STORAGE)) : WorldlyContainer.super.getMaxStackSize();
    }

    /** Sets a slot without the change notice (bulk inserts call {@link #contentsChanged()} once). */
    public void setItemQuietly(int slot, ItemStack stack) {
        items.set(slot, stack);
    }

    /** Clients will get the change (the Storage's heap, a site's ants) at the next sync. */
    public void contentsChanged() {
        syncPending = true;
    }

    // --- Ticking: syncing, automation, losing the link ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, StructureBlockEntity be) {
        long time = level.getGameTime();
        if (be.terrarium != null && time - be.linkedAt >= LINK_TIMEOUT) {
            be.terrarium = null;
            be.assigned = 0;
            be.contentsChanged();
            // No colony any more: a glass case again, whatever its colony was.
            if (ColonyPartBlock.upright(state)) {
                level.setBlock(pos, state.setValue(ColonyPartBlock.UPRIGHT, false),
                        Block.UPDATE_CLIENTS);
            }
        }
        if ((time + pos.asLong()) % 20 == 0 && level instanceof ServerLevel serverLevel) {
            if (be.kind == StructureKind.STORAGE && be.upgradeTier(UpgradeType.AUTOMATION) > 0) {
                be.pushDown(serverLevel, pos);
            }
        }
        if (be.syncPending && time % 10 == 0) {
            be.syncPending = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Automation: a Storage empties into the container under it, 16 items a second per tier. */
    private void pushDown(ServerLevel level, BlockPos pos) {
        var target = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        if (target == null) {
            return;
        }
        int budget = 16 * upgradeTier(UpgradeType.AUTOMATION);
        for (int slot = 0; slot < unlockedSlots() && budget > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int moving = Math.min(budget, stack.getCount());
            ItemStack left = ItemUtil.insertItemReturnRemaining(target, stack.copyWithCount(moving), false, null);
            int inserted = moving - left.getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
                budget -= inserted;
                setChanged();
                contentsChanged();
            }
        }
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
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
            contentsChanged();
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
        contentsChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (kind) {
            case STORAGE, FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE, LIQUID_CHAMBER -> slot < unlockedSlots();
            // A Colony Connector's filter: one of each item it should send.
            case COLONY_CONNECTOR -> slot < items.size();
            case QUEEN_CHAMBER -> slot < StructureKind.QUEEN_CHAMBER_MATERIALS
                    && QueenRearing.isMaterial(level == null ? null : level.registryAccess(), colonySpecies(), stack)
                    || slot == StructureKind.QUEEN_CHAMBER_TUBE && stack.is(ModItems.TEST_TUBE.get())
                    || StructureKind.isQueenChamberSample(slot) && stack.is(ModItems.GENETIC_SAMPLE.get());
            default -> false;
        };
    }

    /** The species of this chamber's colony, or null while it has none (or its Formicarium isn't loaded). */
    private @Nullable AntSpecies colonySpecies() {
        if (level == null || terrarium == null || !level.isLoaded(terrarium)
                || !(level.getBlockEntity(terrarium) instanceof TerrariumBlockEntity home)) {
            return null;
        }
        return ModRegistries.species(level.registryAccess(), home.colony().species());
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    private static final int[] NO_SLOTS = {};

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (kind) {
            case STORAGE, FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE, LIQUID_CHAMBER -> range(0, unlockedSlots());
            case QUEEN_CHAMBER -> range(0, items.size());
            default -> NO_SLOTS;
        };
    }

    private static int[] range(int from, int to) {
        int[] slots = new int[Math.max(0, to - from)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = from + i;
        }
        return slots;
    }

    /** Hoppers and pipes may feed Queen Chambers; stores (Storage, a Foraging Site's) are only emptied. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return !kind.stores() && slot != StructureKind.QUEEN_CHAMBER_OUTPUT && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return kind.stores() || kind == StructureKind.QUEEN_CHAMBER && slot == StructureKind.QUEEN_CHAMBER_OUTPUT;
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StructureMenu(containerId, inventory, this);
    }

    /** The Storage's renderer draws its heap from this box. */
    public AABB getRenderBox() {
        return new AABB(worldPosition).inflate(0.5);
    }

    // --- Breaking ---

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            Containers.dropContents(level, pos, upgrades);
            if (connector != null && level instanceof ServerLevel serverLevel) {
                ConnectorNetwork.unregister(serverLevel, pos);
            }
        }
    }

    /** A Colony Connector joins its channel's network when it loads (the network remembers it while unloaded too). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (connector != null && level instanceof ServerLevel serverLevel) {
            ConnectorNetwork.register(serverLevel, worldPosition, connector.channel);
        }
    }

    public void connectorChanged() {
        if (connector != null && level instanceof ServerLevel serverLevel) {
            ConnectorNetwork.register(serverLevel, worldPosition, connector.channel);
        }
        setChanged();
        contentsChanged();
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(kind.slots(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items); // saves from before compacting Storage
        for (StoredSlot stored : input.listOrEmpty("stored", StoredSlot.CODEC)) {
            if (stored.slot() >= 0 && stored.slot() < items.size()) {
                items.set(stored.slot(), stored.item().copyWithCount(stored.count()));
            }
        }
        upgrades.clearContent();
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("upgrades", ItemStack.OPTIONAL_CODEC)) {
            if (slot < upgrades.getContainerSize()) {
                upgrades.setItem(slot++, stack);
            }
        }
        requested = input.getIntOr("requested", -1);
        progress = input.getDoubleOr("progress", 0);
        rearingProgress = input.getDoubleOr("rearing", 0);
        rearingStatus = input.getIntOr("rearing_status", 0);
        queensRaised = input.getIntOr("queens_raised", 0);
        // Chambers saved before modes, switches and priorities existed load as Balanced, on and normal.
        mode = ForagingMode.byId(input.getIntOr("mode", 0));
        enabled = input.getBooleanOr("enabled", true);
        priority = Math.clamp(input.getIntOr("priority", PRIORITY_NORMAL), PRIORITY_LOW, PRIORITY_HIGH);
        if (connector != null) {
            connector.load(input);
        }
        // Synced only: what the colony gave it.
        assigned = input.getIntOr("assigned", 0);
        if (input.getBooleanOr("contested", false)) {
            contestedAt = level != null ? level.getGameTime() : Long.MAX_VALUE / 2;
        } else {
            contestedAt = Long.MIN_VALUE / 2; // no longer contested: the badge goes at once
        }
        siteStatus = input.getIntOr("site_status", 0);
        colonyStatus = input.getIntOr("colony_status", 0);
        rate = input.getDoubleOr("rate", 0);
        capacityShown = input.getIntOr("capacity", capacityShown);
        terrarium = input.read("terrarium", BlockPos.CODEC).orElse(null);
        if (terrarium != null && input.getBooleanOr("linked", false)) {
            linkedAt = level != null ? level.getGameTime() : Long.MAX_VALUE / 2;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // A compacting Storage slot can hold more than an item stack may save (99): each is saved as one item and its count.
        ValueOutput.TypedOutputList<StoredSlot> stored = output.list("stored", StoredSlot.CODEC);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                stored.add(new StoredSlot(slot, stack.copyWithCount(1), stack.getCount()));
            }
        }
        ValueOutput.TypedOutputList<ItemStack> list = output.list("upgrades", ItemStack.OPTIONAL_CODEC);
        for (int slot = 0; slot < upgrades.getContainerSize(); slot++) {
            list.add(upgrades.getItem(slot));
        }
        output.putInt("requested", requested);
        output.putDouble("progress", progress);
        output.putDouble("rearing", rearingProgress);
        output.putInt("rearing_status", rearingStatus);
        output.putInt("queens_raised", queensRaised);
        if (kind.job().any()) {
            output.putInt("mode", mode.ordinal());
            output.putBoolean("enabled", enabled);
            output.putInt("priority", priority);
        }
        if (connector != null) {
            connector.save(output);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.putInt("assigned", assigned);
        tag.putBoolean("contested", isContested());
        tag.putInt("site_status", siteStatus);
        tag.putInt("colony_status", colonyStatus);
        tag.putDouble("rate", rate);
        tag.putInt("capacity", capacity());
        tag.putBoolean("linked", isLinked());
        if (terrarium != null) {
            tag.store("terrarium", BlockPos.CODEC, terrarium);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** On the client: whether the last sync said a colony works this structure (for its ants and glow). */
    public boolean clientLinked() {
        return terrarium != null && linkedAt != Long.MIN_VALUE;
    }

    public boolean hasProblem() {
        return ColonyStatus.of(siteStatus).stream().anyMatch(ColonyStatus::problem) || !isLinked();
    }
}
