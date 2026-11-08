package com.antcolonies.content.blockentity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.antcolonies.content.block.LabPartBlock;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.genetics.TraitUpgrade;
import com.antcolonies.content.menu.GeneticLabMenu;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Genetic Laboratory's controller. The laboratory is formed when a DNA Analyzer, a Mutation Chamber, Genetic
 * Storage, a Queen Enhancement Chamber and a Genetic Energy Core all stand within two blocks of the controller
 * (one block up or down). Formed, it works on the Queen in its Queen slot:
 * <ul>
 * <li><b>Upgrade</b>: raises one trait by one level, for the materials of the trait's data file and Genetic Energy.
 * Permanent, and no other trait changes.</li>
 * <li><b>Reroll</b> (Genetic Reroll Serum): rerolls one trait, never below its current level.</li>
 * <li><b>Mutate</b>: feeds the catalyst in the catalyst slot to add the matching beneficial mutation.</li>
 * <li><b>Purge</b> (Genetic Reroll Serum): removes the Queen's first harmful mutation.</li>
 * <li><b>Sample</b> (an empty Test Tube): copies her genes into a Genetic Sample for crossing in a Queen Chamber.</li>
 * </ul>
 */
public class GeneticLabBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, com.antcolonies.content.block.ComparatorSource {
    /** Comparators read how full the lab's FE store is. */
    @Override
    public int comparatorSignal() {
        return com.antcolonies.content.block.ComparatorSource.fill(energy.getAmountAsInt(), ENERGY_CAPACITY);
    }

    public static final int SLOT_QUEEN = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int MATERIAL_SLOTS = 3;
    public static final int SLOT_CATALYST = 4;
    public static final int SLOTS = 5;
    public static final int UPGRADE_SLOTS = 2;
    /** FE the lab stores, and the most it takes in per tick (from a Generator or any mod's cable). */
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int MAX_RECEIVE = 4_000;
    public static final int RANGE = 2;

    public enum Operation {
        NONE, UPGRADE, REROLL, MUTATE, PURGE, SAMPLE;

        public static Operation byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    public static final int DATA_ENERGY = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_TIME = 2;
    public static final int DATA_OPERATION = 3;
    public static final int DATA_TRAIT = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_MISSING = 6;
    public static final int DATA_COUNT = 7;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem upgrade && (upgrade.type() == UpgradeType.SPEED || upgrade.type() == UpgradeType.GENETIC);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            GeneticLabBlockEntity.this.setChanged();
        }
    };
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
        @Override
        protected void onEnergyChanged(int previous) {
            GeneticLabBlockEntity.this.setChanged();
        }
    };
    private Operation operation = Operation.NONE;
    private int selectedTrait;
    private int progress;
    private int time;
    private int status;
    /** Bits of the parts the laboratory still lacks (see {@link LabPartBlock.Part}). */
    private int missing = (1 << LabPartBlock.Part.values().length) - 1;
    private long checkedAt = Long.MIN_VALUE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_ENERGY -> energy.getAmountAsInt();
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> time;
                case DATA_OPERATION -> operation.ordinal();
                case DATA_TRAIT -> selectedTrait;
                case DATA_STATUS -> status;
                case DATA_MISSING -> missing;
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

    public GeneticLabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENETIC_LAB.get(), pos, state);
    }

    public ContainerData data() {
        return data;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public boolean formed() {
        return missing == 0;
    }

    public int missing() {
        return missing;
    }

    public Operation operation() {
        return operation;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    /** The FE input, for the controller and the Genetic Energy Core: takes energy in, never gives it out. */
    public EnergyHandler energyHandler() {
        return energy;
    }

    public void selectTrait(int trait) {
        if (operation == Operation.NONE && trait >= 0 && trait < Trait.values().length) {
            selectedTrait = trait;
            setChanged();
        }
    }

    public Trait selectedTrait() {
        return Trait.values()[Math.clamp(selectedTrait, 0, Trait.values().length - 1)];
    }

    private int tier(UpgradeType type) {
        int sum = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.type() == type) {
                sum += upgrade.tier();
            }
        }
        return sum;
    }

    /** Material costs after the configured multiplier and Genetic Upgrades (10% cheaper per tier, at most 50%). */
    public double costMultiplier() {
        return AntConfig.get(AntConfig.LAB_COST_MULTIPLIER) * (1 - Math.min(0.5, 0.1 * tier(UpgradeType.GENETIC)));
    }

    public static Optional<TraitUpgrade> traitUpgrade(HolderLookup.Provider registries, Trait trait) {
        return registries.lookup(ModRegistries.TRAIT_UPGRADE)
                .flatMap(lookup -> lookup.listElements().map(Holder::value).filter(upgrade -> upgrade.trait() == trait).findFirst());
    }

    // --- The multiblock ---

    /** Looks for the five parts around the controller and lights the ones it found. */
    public void checkStructure(ServerLevel level) {
        Set<LabPartBlock.Part> found = EnumSet.noneOf(LabPartBlock.Part.class);
        List<BlockPos> parts = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -1, -RANGE), worldPosition.offset(RANGE, 1, RANGE))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LabPartBlock part) {
                found.add(part.part());
                parts.add(pos.immutable());
            }
        }
        int nowMissing = 0;
        for (LabPartBlock.Part part : LabPartBlock.Part.values()) {
            if (!found.contains(part)) {
                nowMissing |= 1 << part.ordinal();
            }
        }
        boolean formed = nowMissing == 0;
        for (BlockPos pos : parts) {
            LabPartBlock.setFormed(level, pos, formed);
        }
        if (getBlockState().hasProperty(LabPartBlock.FORMED) && getBlockState().getValue(LabPartBlock.FORMED) != formed) {
            level.setBlock(worldPosition, getBlockState().setValue(LabPartBlock.FORMED, formed), Block.UPDATE_ALL);
        }
        if (nowMissing != missing) {
            missing = nowMissing;
            setChanged();
        }
    }

    // --- Working ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneticLabBlockEntity lab) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() - lab.checkedAt >= 40) {
            lab.checkedAt = level.getGameTime();
            lab.checkStructure(serverLevel);
        }
        if (lab.operation == Operation.NONE) {
            return;
        }
        if (!lab.formed()) {
            lab.status = ColonyStatus.INVALID_MULTIBLOCK.bit();
            return;
        }
        QueenData queen = lab.queen();
        if (queen == null) {
            lab.cancel(ColonyStatus.NO_QUEEN.bit());
            return;
        }
        lab.status = ColonyStatus.WORKING.bit();
        int speed = 1 + lab.tier(UpgradeType.SPEED);
        lab.progress = Math.min(lab.time, lab.progress + speed);
        if (lab.progress >= lab.time) {
            lab.finish(serverLevel, queen);
        }
        lab.setChanged();
    }

    public @Nullable QueenData queen() {
        ItemStack stack = items.get(SLOT_QUEEN);
        return stack.is(ModItems.QUEEN_TEST_TUBE.get()) ? stack.get(ModDataComponents.QUEEN.get()) : null;
    }

    /**
     * Starts an operation if everything it needs is there; nothing is used until it finishes, so cancelling (or taking
     * the Queen out) costs nothing.
     */
    public void start(Operation next, RegistryAccess registries) {
        if (operation != Operation.NONE) {
            return;
        }
        status = 0;
        QueenData queen = queen();
        if (!formed()) {
            status = ColonyStatus.INVALID_MULTIBLOCK.bit();
            return;
        }
        if (queen == null) {
            status = ColonyStatus.NO_QUEEN.bit();
            return;
        }
        Requirement requirement = requirement(next, queen, registries);
        if (requirement == null || !requirement.met(this)) {
            status = ColonyStatus.INSUFFICIENT_MATERIALS.bit();
            return;
        }
        operation = next;
        progress = 0;
        time = Math.max(20, (int) (requirement.time() * AntConfig.get(AntConfig.LAB_TIME_MULTIPLIER)));
        status = ColonyStatus.WORKING.bit();
        setChanged();
    }

    public void cancel(int reason) {
        operation = Operation.NONE;
        progress = 0;
        time = 0;
        status = reason;
        setChanged();
    }

    /** What an operation on the selected trait takes: materials, energy, a catalyst-slot item, time. */
    public record Requirement(List<Cost> materials, int energy, @Nullable Item catalyst, int catalystCount, int time) {
        boolean met(GeneticLabBlockEntity lab) {
            if (lab.energy() < energy) {
                return false;
            }
            if (catalyst != null && (!lab.items.get(SLOT_CATALYST).is(catalyst) || lab.items.get(SLOT_CATALYST).getCount() < catalystCount)) {
                return false;
            }
            for (Cost cost : materials) {
                int found = 0;
                for (int slot = SLOT_MATERIAL; slot < SLOT_MATERIAL + MATERIAL_SLOTS; slot++) {
                    if (cost.test(lab.items.get(slot))) {
                        found += lab.items.get(slot).getCount();
                    }
                }
                if (found < cost.count()) {
                    return false;
                }
            }
            return true;
        }
    }

    public @Nullable Requirement requirement(Operation op, QueenData queen, HolderLookup.Provider registries) {
        Trait trait = selectedTrait();
        int level = queen.genome().level(trait);
        double costs = costMultiplier();
        return switch (op) {
            case UPGRADE -> {
                if (level >= Trait.maxLevel()) {
                    yield null;
                }
                TraitUpgrade.Step step = traitUpgrade(registries, trait).map(upgrade -> upgrade.step(level)).orElse(TraitUpgrade.Step.EMPTY);
                List<Cost> scaled = step.cost().stream().map(cost -> new Cost(cost.ingredient(), cost.scaled(costs))).toList();
                yield new Requirement(scaled, (int) Math.ceil(step.energy() * costs), null, 0, step.time());
            }
            case REROLL -> level >= rerollCap() ? null : new Requirement(List.of(), 40_000, ModItems.REROLL_SERUM.get(), 1, 400);
            case PURGE -> firstNegative(queen.genome(), registries) == null ? null
                    : new Requirement(List.of(), 60_000, ModItems.REROLL_SERUM.get(), 1, 600);
            case MUTATE -> {
                Map.Entry<Identifier, Mutation> mutation = catalystMutation(queen.genome(), registries);
                yield mutation == null ? null : new Requirement(List.of(), 80_000, items.get(SLOT_CATALYST).getItem(),
                        mutation.getValue().catalyst().map(Cost::count).orElse(1), 800);
            }
            case SAMPLE -> new Requirement(List.of(), 10_000, ModItems.TEST_TUBE.get(), 1, 100);
            case NONE -> null;
        };
    }

    /**
     * The highest level a Reroll can reach. A Reroll never lowers a trait and costs one serum, so it stops four levels
     * short of the top: the expensive last steps (crystal cores, netherite, a nether star) can only be upgraded.
     */
    public static int rerollCap() {
        return Math.max(1, Trait.maxLevel() - 4);
    }

    /** The beneficial mutation the item in the catalyst slot gives, if the Queen doesn't have it yet. */
    private Map.@Nullable Entry<Identifier, Mutation> catalystMutation(Genome genome, HolderLookup.Provider registries) {
        ItemStack catalyst = items.get(SLOT_CATALYST);
        if (catalyst.isEmpty()) {
            return null;
        }
        if (!com.antcolonies.colony.genetics.Genetics.roomForBeneficial(genome.mutations(), registries)) {
            return null;
        }
        var lookup = registries.lookup(ModRegistries.MUTATION);
        if (lookup.isEmpty()) {
            return null;
        }
        for (Holder.Reference<Mutation> mutation : lookup.get().listElements().toList()) {
            Mutation value = mutation.value();
            if (!value.negative() && value.catalyst().isPresent() && value.catalyst().get().test(catalyst)
                    && catalyst.getCount() >= value.catalyst().get().count() && !genome.mutations().contains(mutation.key().identifier())) {
                return Map.entry(mutation.key().identifier(), value);
            }
        }
        return null;
    }

    private static @Nullable Identifier firstNegative(Genome genome, HolderLookup.Provider registries) {
        var lookup = registries.lookup(ModRegistries.MUTATION);
        if (lookup.isEmpty()) {
            return null;
        }
        for (Identifier id : genome.mutations()) {
            var mutation = lookup.get().get(ResourceKey.create(ModRegistries.MUTATION, id));
            if (mutation.isPresent() && mutation.get().value().negative()) {
                return id;
            }
        }
        return null;
    }

    private void finish(ServerLevel level, QueenData queen) {
        Requirement requirement = requirement(operation, queen, level.registryAccess());
        if (requirement == null || !requirement.met(this)) {
            cancel(ColonyStatus.INSUFFICIENT_MATERIALS.bit());
            return;
        }
        Genome genome = queen.genome();
        Trait trait = selectedTrait();
        ItemStack result = null;
        switch (operation) {
            case UPGRADE -> genome = genome.withLevel(trait, genome.level(trait) + 1);
            case REROLL -> {
                int current = genome.level(trait);
                int rolled = current + level.getRandom().nextInt(3);
                genome = genome.withLevel(trait, Math.max(current, Math.min(rerollCap(), rolled)));
            }
            case PURGE -> {
                Identifier negative = firstNegative(genome, level.registryAccess());
                if (negative != null) {
                    genome = genome.withoutMutation(negative);
                }
            }
            case MUTATE -> {
                Map.Entry<Identifier, Mutation> mutation = catalystMutation(genome, level.registryAccess());
                if (mutation != null) {
                    genome = genome.withMutation(mutation.getKey());
                }
            }
            case SAMPLE -> {
                result = new ItemStack(ModItems.GENETIC_SAMPLE.get());
                result.set(ModDataComponents.QUEEN.get(), queen);
            }
            case NONE -> {
            }
        }
        // Pay.
        energy.set(Math.max(0, energy.getAmountAsInt() - requirement.energy()));
        for (Cost cost : requirement.materials()) {
            int left = cost.count();
            for (int slot = SLOT_MATERIAL; slot < SLOT_MATERIAL + MATERIAL_SLOTS && left > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (cost.test(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        if (requirement.catalyst() != null) {
            items.get(SLOT_CATALYST).shrink(requirement.catalystCount());
        }
        if (result != null) {
            if (items.get(SLOT_CATALYST).isEmpty()) {
                items.set(SLOT_CATALYST, result);
            } else {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, result);
            }
        } else {
            ItemStack tube = items.get(SLOT_QUEEN).copy();
            tube.set(ModDataComponents.QUEEN.get(), queen.withGenome(genome));
            items.set(SLOT_QUEEN, tube);
        }
        level.playSound(null, worldPosition, ModSounds.LAB_COMPLETE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, operation.ordinal());
        cancel(ColonyStatus.WORKING.bit());
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level != null && level.isClientSide()) {
            com.antcolonies.client.ClientHooks.labEvent(this, id);
        }
        return true;
    }

    // --- Container ---

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
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (slot == SLOT_QUEEN && operation != Operation.NONE) {
            cancel(0);
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (slot == SLOT_QUEEN && operation != Operation.NONE) {
            cancel(0);
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_QUEEN -> stack.is(ModItems.QUEEN_TEST_TUBE.get());
            default -> true;
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

    private static final int[] AUTOMATED = {SLOT_MATERIAL, SLOT_MATERIAL + 1, SLOT_MATERIAL + 2};

    /** Hoppers may bring materials and fuel; Queens are only handled by hand. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return AUTOMATED;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.antcolonies.genetic_lab_controller");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeneticLabMenu(containerId, inventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            Containers.dropContents(level, pos, upgrades);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.clearContent();
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("upgrades", ItemStack.OPTIONAL_CODEC)) {
            if (slot < UPGRADE_SLOTS) {
                upgrades.setItem(slot++, stack);
            }
        }
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, ENERGY_CAPACITY));
        operation = Operation.byId(input.getIntOr("operation", 0));
        selectedTrait = input.getIntOr("trait", 0);
        progress = input.getIntOr("progress", 0);
        time = input.getIntOr("time", 0);
        missing = input.getIntOr("missing", missing);
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
        output.putInt("operation", operation.ordinal());
        output.putInt("trait", selectedTrait);
        output.putInt("progress", progress);
        output.putInt("time", time);
        output.putInt("missing", missing);
    }

    /** Clients see the Queen (for the enhancement chamber's display) and whether it is working. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
