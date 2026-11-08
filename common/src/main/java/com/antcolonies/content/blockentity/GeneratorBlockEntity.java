package com.antcolonies.content.blockentity;

import com.antcolonies.content.block.GeneratorBlock;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.menu.GeneratorMenu;
import com.antcolonies.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Generator: burns anything a furnace burns, for as long as a furnace would, making FE while it burns, and gives
 * that FE to every neighbour that takes energy (the Genetic Laboratory, or any mod's cable or machine). It only starts
 * a new item when its buffer has room, and pauses with a full buffer, so no fuel is wasted.
 */
public class GeneratorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, com.antcolonies.content.block.ComparatorSource {
    /** Comparators read how full the FE buffer is. */
    @Override
    public int comparatorSignal() {
        return com.antcolonies.content.block.ComparatorSource.fill(energy.getAmountAsInt(), CAPACITY);
    }

    public static final int CAPACITY = 100_000;
    public static final int DATA_ENERGY = 0;
    public static final int DATA_BURN = 1;
    public static final int DATA_BURN_TOTAL = 2;
    public static final int DATA_OUTPUT = 3;
    public static final int DATA_COUNT = 4;
    private static final int[] SLOTS = {0};

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, 0, Integer.MAX_VALUE) {
        @Override
        protected void onEnergyChanged(int previous) {
            GeneratorBlockEntity.this.setChanged();
        }
    };
    private int burn;
    private int burnTotal;
    /** FE made in the last tick: shown in the GUI. */
    private int output;
    private final BlockCapabilityCache<EnergyHandler, Direction>[] neighbours = createCacheArray();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_ENERGY -> energy.getAmountAsInt();
                case DATA_BURN -> burn;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_OUTPUT -> output;
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

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<EnergyHandler, Direction>[] createCacheArray() {
        return new BlockCapabilityCache[Direction.values().length];
    }

    public ContainerData data() {
        return data;
    }

    /** The FE output: other mods' cables may pull from it; nothing can be put in. */
    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    /** Ticks one item of this fuel burns for here (as long as in a furnace), 0 if it isn't fuel. */
    public int burnDuration(ServerLevel level, ItemStack stack) {
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, getLootContext(level, stack), 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        if (level instanceof ServerLevel serverLevel) {
            generator.tick(serverLevel, pos, state);
        }
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        int rate = AntConfig.get(AntConfig.GENERATOR_OUTPUT);
        // A redstone signal turns the Generator off: it keeps its fuel and its burn time, and still gives out what it holds.
        boolean room = energy.getAmountAsInt() + rate <= CAPACITY && !level.hasNeighborSignal(pos);
        output = 0;
        if (burn <= 0 && room) {
            ItemStack fuel = items.get(0);
            int duration = fuel.isEmpty() ? 0 : burnDuration(level, fuel);
            if (duration > 0) {
                burn = duration;
                burnTotal = duration;
                consumeFuel(level, pos, fuel);
            }
        }
        if (burn > 0 && room) {
            burn--;
            output = rate;
            energy.set(energy.getAmountAsInt() + rate);
        }
        push(level, pos);
        boolean lit = burn > 0;
        if (state.getValue(GeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Uses up one item, leaving what a furnace would leave (a lava bucket leaves its bucket). */
    private void consumeFuel(ServerLevel level, BlockPos pos, ItemStack fuel) {
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        fuel.shrink(1);
        if (remainder != null) {
            if (fuel.isEmpty()) {
                items.set(0, remainder.create());
            } else {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder.create());
            }
        }
        setChanged();
    }

    /** Shares the buffer out to the neighbours that take energy, up to the push limit per tick. */
    private void push(ServerLevel level, BlockPos pos) {
        int budget = Math.min(energy.getAmountAsInt(), AntConfig.get(AntConfig.GENERATOR_PUSH));
        for (Direction side : Direction.values()) {
            if (budget <= 0) {
                break;
            }
            BlockCapabilityCache<EnergyHandler, Direction> cache = neighbours[side.ordinal()];
            if (cache == null) {
                cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(side), side.getOpposite());
                neighbours[side.ordinal()] = cache;
            }
            EnergyHandler target = cache.getCapability();
            if (target == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int given = target.insert(budget, transaction);
                if (given > 0) {
                    transaction.commit();
                    budget -= given;
                    energy.set(energy.getAmountAsInt() - given);
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
        return stack.has(DataComponents.COOKING_FUEL);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Only what fuel leaves behind (an empty bucket) can be taken out by hoppers. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !stack.has(DataComponents.COOKING_FUEL);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.antcolonies.generator");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new GeneratorMenu(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, CAPACITY));
        burn = input.getIntOr("burn", 0);
        burnTotal = input.getIntOr("burn_total", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy", energy.getAmountAsInt());
        output.putInt("burn", burn);
        output.putInt("burn_total", burnTotal);
    }
}
