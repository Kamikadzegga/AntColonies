package com.antcolonies.content.blockentity;

import com.antcolonies.colony.upgrade.UpgradeEffects;
import com.antcolonies.colony.upgrade.UpgradeType;
import com.antcolonies.content.block.CentrifugeBlock;
import com.antcolonies.content.block.ComparatorSource;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.content.menu.CentrifugeMenu;
import com.antcolonies.content.recipe.CentrifugingRecipe;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlockEntities;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** The Droplet Centrifuge: spins one droplet at a time into its recipe's fluid, paying the FE evenly over the run. */
public class CentrifugeBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ComparatorSource {
    public enum Status {
        /** Nothing in the slot, nothing in the tank. */
        IDLE,
        WORKING,
        /** A droplet waits, but there is not enough FE for the next tick of the run. */
        NO_ENERGY,
        TANK_FULL,
        /** The tank holds another fluid: it has to be emptied first. */
        BLOCKED,
        /** A redstone signal holds it. */
        PAUSED,
        /** No droplet left; the fluid made waits in the tank. */
        DONE;

        public static Status byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : IDLE;
        }

        public String translationKey() {
            return "gui.antcolonies.centrifuge.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_DURATION = 3;
    public static final int DATA_AMOUNT = 4;
    public static final int DATA_CAPACITY = 5;
    public static final int DATA_STATUS = 6;
    /** The fluid in the tank, and the fluid of the droplet in the slot: fluid registry ids, -1 for none. */
    public static final int DATA_FLUID = 7;
    public static final int DATA_MAKING = 8;
    /** What one run of the droplet in the slot gives (mB) and takes (FE). */
    public static final int DATA_RUN_AMOUNT = 9;
    public static final int DATA_RUN_ENERGY = 10;
    /** Steps of a run it makes each tick: one, and one more per Speed Upgrade tier. */
    public static final int DATA_SPEED = 11;
    public static final int DATA_COUNT = 12;
    public static final int UPGRADE_SLOTS = 2;
    private static final int[] SLOTS = {0};

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return UpgradeEffects.Host.CENTRIFUGE.takes(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            CentrifugeBlockEntity.this.setChanged();
        }
    };
    private final SimpleEnergyHandler energy;
    private final FluidStacksResourceHandler tank;
    /** The tank as other blocks see it: fluid comes out, nothing goes in. */
    private final ResourceHandler<FluidResource> output;
    /** Ticks of the current run done, and the FE paid for them. */
    private int progress;
    private int spent;
    /** The item the current run is of: another item in the slot starts a new run. */
    private @Nullable Item running;
    private Status status = Status.IDLE;
    /** The recipe of the item in the slot, looked up again when the item or the loaded recipes change. */
    private @Nullable CentrifugingRecipe recipe;
    private @Nullable Item recipeItem;
    private @Nullable Object recipeSource;
    private boolean syncPending;
    private final BlockCapabilityCache<ResourceHandler<FluidResource>, Direction>[] neighbours = createCacheArray();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_ENERGY -> energy.getAmountAsInt();
                case DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                case DATA_PROGRESS -> progress;
                case DATA_DURATION -> recipe == null ? 0 : recipe.duration();
                case DATA_AMOUNT -> tank.getAmountAsInt(0);
                case DATA_CAPACITY -> tankCapacity();
                case DATA_STATUS -> status.ordinal();
                case DATA_FLUID -> tank.getAmountAsInt(0) <= 0 ? -1 : BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid());
                case DATA_MAKING -> recipe == null ? -1 : BuiltInRegistries.FLUID.getId(recipe.fluid().getFluid());
                case DATA_RUN_AMOUNT -> recipe == null ? 0 : recipe.amount();
                case DATA_RUN_ENERGY -> recipe == null ? 0 : recipe.energyCost();
                case DATA_SPEED -> speed();
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

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state);
        int room = AntConfig.get(AntConfig.CENTRIFUGE_ENERGY);
        this.energy = new SimpleEnergyHandler(room, room, 0) {
            @Override
            protected void onEnergyChanged(int previous) {
                CentrifugeBlockEntity.this.setChanged();
            }
        };
        this.tank = new FluidStacksResourceHandler(1, AntConfig.get(AntConfig.CENTRIFUGE_TANK)) {
            @Override
            protected void onContentsChanged(int index, FluidStack previous) {
                CentrifugeBlockEntity.this.setChanged();
                syncPending = true;
            }
        };
        this.output = new DelegatingResourceHandler<>(tank) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return false;
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int insert(FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<ResourceHandler<FluidResource>, Direction>[] createCacheArray() {
        return new BlockCapabilityCache[Direction.values().length];
    }

    public ContainerData data() {
        return data;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    /** Steps of a run each tick: one, and one more per Speed Upgrade tier in its slots. The FE of a run stays the same. */
    public int speed() {
        int speed = 1;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.type() == UpgradeType.SPEED) {
                speed += upgrade.tier();
            }
        }
        return speed;
    }

    /** The FE input, on every side: takes energy in, never gives it out. */
    public EnergyHandler energyHandler() {
        return energy;
    }

    public ResourceHandler<FluidResource> fluidOutput() {
        return output;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    /** For tests and creative setups: sets the stored FE (within the store's room). */
    public void setEnergy(int amount) {
        energy.set(Math.clamp(amount, 0, energy.getCapacityAsInt()));
    }

    public FluidStack fluid() {
        return tank.getAmountAsInt(0) <= 0 ? FluidStack.EMPTY : tank.getResource(0).toStack(tank.getAmountAsInt(0));
    }

    /** For tests and creative setups: sets what the tank holds (within its room; empty for nothing). */
    public void setFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            tank.set(0, FluidResource.EMPTY, 0);
        } else {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), tankCapacity()));
        }
    }

    public int tankCapacity() {
        return tank.getCapacityAsInt(0, FluidResource.EMPTY);
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    /** Whether its drum is turning (the block's state, so clients know too). */
    public boolean working() {
        return getBlockState().hasProperty(CentrifugeBlock.WORKING) && getBlockState().getValue(CentrifugeBlock.WORKING);
    }

    @Override
    public int comparatorSignal() {
        return ComparatorSource.fill(tank.getAmountAsInt(0), tankCapacity());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (level instanceof ServerLevel serverLevel) {
            centrifuge.tick(serverLevel, pos, state);
        }
    }

    private @Nullable CentrifugingRecipe currentRecipe(ServerLevel level) {
        ItemStack input = items.get(0);
        Object source = level.recipeAccess().recipeMap();
        Item item = input.isEmpty() ? null : input.getItem();
        if (item != recipeItem || source != recipeSource) {
            recipeItem = item;
            recipeSource = source;
            recipe = CentrifugingRecipe.find(level, input);
        }
        return recipe;
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Status now = spin(level, pos);
        // Speed Upgrades: more steps in the same tick, while it keeps working.
        for (int extra = speed() - 1; extra > 0 && now == Status.WORKING; extra--) {
            if (spin(level, pos) != Status.WORKING) {
                break;
            }
        }
        status = now;
        push(level, pos);
        boolean working = now == Status.WORKING;
        if (state.getValue(CentrifugeBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(CentrifugeBlock.WORKING, working), Block.UPDATE_ALL);
        }
        if (syncPending && level.getGameTime() % 10 == 0) {
            syncPending = false;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** One step of the run of the droplet in the slot; says what the Centrifuge did, or why it waits. */
    private Status spin(ServerLevel level, BlockPos pos) {
        ItemStack input = items.get(0);
        CentrifugingRecipe run = currentRecipe(level);
        int amount = tank.getAmountAsInt(0);
        Status now;
        if (run == null) {
            progress = 0;
            spent = 0;
            running = null;
            now = amount > 0 ? Status.DONE : Status.IDLE;
        } else {
            if (running != input.getItem()) {
                // Another item than the run was started with: a new run.
                running = input.getItem();
                progress = 0;
                spent = 0;
            }
            int duration = run.duration();
            int cost = run.energyCost();
            progress = Math.min(progress, duration - 1);
            FluidResource result = run.fluid();
            if (amount > 0 && !tank.getResource(0).equals(result)) {
                now = Status.BLOCKED;
            } else if (amount + run.amount() > tankCapacity()) {
                now = Status.TANK_FULL;
            } else if (level.hasNeighborSignal(pos)) {
                now = Status.PAUSED;
            } else {
                // The run's FE is paid evenly: by the end of tick n, cost * n / duration in all.
                int due = (int) ((long) cost * (progress + 1) / duration) - spent;
                if (energy.getAmountAsInt() < due) {
                    now = Status.NO_ENERGY;
                } else {
                    if (due > 0) {
                        energy.set(energy.getAmountAsInt() - due);
                        spent += due;
                    }
                    progress++;
                    now = Status.WORKING;
                    if (progress >= duration) {
                        // The droplet goes and the fluid arrives in the same moment.
                        input.shrink(1);
                        tank.set(0, result, amount + run.amount());
                        progress = 0;
                        spent = 0;
                    }
                    setChanged();
                }
            }
        }
        return now;
    }

    private void push(ServerLevel level, BlockPos pos) {
        int budget = Math.min(tank.getAmountAsInt(0), AntConfig.get(AntConfig.CENTRIFUGE_PUSH));
        if (budget <= 0) {
            return;
        }
        FluidResource resource = tank.getResource(0);
        for (Direction side : Direction.values()) {
            if (budget <= 0) {
                break;
            }
            BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache = neighbours[side.ordinal()];
            if (cache == null) {
                cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(side), side.getOpposite());
                neighbours[side.ordinal()] = cache;
            }
            ResourceHandler<FluidResource> target = cache.getCapability();
            if (target == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = target.insert(resource, budget, transaction);
                // Out of the tank exactly what went in there, or nothing at all.
                if (taken > 0 && tank.extract(0, resource, taken, transaction) == taken) {
                    transaction.commit();
                    budget -= taken;
                }
            }
        }
    }

    // --- Container ---

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return CentrifugingRecipe.find(level, stack) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Hoppers and pipes take out only what can no longer be spun (its recipe was removed). */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return CentrifugingRecipe.find(level, stack) == null;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.antcolonies.centrifuge");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CentrifugeMenu(containerId, inventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, upgrades);
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.clearContent();
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("upgrades", ItemStack.OPTIONAL_CODEC)) {
            if (slot < UPGRADE_SLOTS) {
                upgrades.setItem(slot++, stack);
            }
        }
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, energy.getCapacityAsInt()));
        FluidStack stored = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        if (stored.isEmpty() || stored.getFluid() == Fluids.EMPTY) {
            tank.set(0, FluidResource.EMPTY, 0);
        } else {
            tank.set(0, FluidResource.of(stored), Math.min(stored.getAmount(), tankCapacity()));
        }
        progress = Math.max(0, input.getIntOr("progress", 0));
        spent = Math.max(0, input.getIntOr("spent", 0));
        running = input.read("running", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        syncPending = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        ValueOutput.TypedOutputList<ItemStack> list = output.list("upgrades", ItemStack.OPTIONAL_CODEC);
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            list.add(upgrades.getItem(slot));
        }
        output.putInt("energy", energy.getAmountAsInt());
        output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid());
        output.putInt("progress", progress);
        output.putInt("spent", spent);
        if (running != null) {
            output.store("running", BuiltInRegistries.ITEM.byNameCodec(), running);
        }
    }

    /** Clients draw the fluid in the drum from this. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static Fluid fluidById(int id) {
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }
}
