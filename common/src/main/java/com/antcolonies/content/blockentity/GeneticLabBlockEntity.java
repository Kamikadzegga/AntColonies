package com.antcolonies.content.blockentity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.client.ClientHooks;
import com.antcolonies.colony.evolution.SpeciesMutation;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.research.Discovery;
import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.content.block.ComparatorSource;
import com.antcolonies.content.block.GeneticLabBlock;
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
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.colony.upgrade.UpgradeEffects;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** The Genetic Laboratory: spends FE and materials to change the genes of the Queen in its Queen slot. */
public class GeneticLabBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, ComparatorSource {
    @Override
    public int comparatorSignal() {
        return ComparatorSource.fill(energy.getAmountAsInt(), energy.getCapacityAsInt());
    }

    public static final int SLOT_QUEEN = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int MATERIAL_SLOTS = 3;
    public static final int SLOT_CATALYST = 4;
    /** Species Mutation: its own materials and the transformed Queen's way out. */
    public static final int SLOT_MUTATION = 5;
    public static final int MUTATION_SLOTS = 2;
    public static final int SLOT_MUTATION_OUTPUT = 7;
    /** Hybridization: an empty Test Tube in, the Queen's copied genes out. */
    public static final int SLOT_TUBE = 8;
    public static final int SLOT_SAMPLE_OUTPUT = 9;
    /** Stabilization: the Royal Jelly it takes. */
    public static final int SLOT_STABILIZER = 10;
    /** Research: the scanner's slot. What lies in it is read, never used up. */
    public static final int SLOT_SCAN = 11;
    public static final int SLOTS = 12;
    public static final int UPGRADE_SLOTS = 2;
    public static int energyCapacity() {
        return AntConfig.get(AntConfig.LAB_ENERGY);
    }

    public enum Operation {
        NONE,
        /** Raises one trait by one level. */
        UPGRADE,
        /** Rerolls one trait, never below its current level. */
        REROLL,
        /** Adds the beneficial mutation of the item in the catalyst slot. */
        MUTATE,
        /** Removes the Queen's first harmful mutation. */
        PURGE,
        /** Copies her genes into a Genetic Sample. */
        SAMPLE,
        /** Species Mutation: turns her into the chosen genetically modified species. */
        TRANSFORM,
        /** Hybridization: the same copy as SAMPLE, from that page's own tube slot into its output. */
        PREPARE_SAMPLE,
        /** Stabilization: locks the chosen trait or protects the chosen mutation. */
        STABILIZE;

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
    /** The chosen species mutation: its place among {@link SpeciesMutation#options} of the Queen's species, or -1. */
    public static final int DATA_MUTATION = 6;
    /** The gene chosen on the Stabilization page: a trait (0-9) or one of her beneficial mutations (10 and up). */
    public static final int DATA_GENE = 7;
    /** What the scanner made of the item in its slot (a {@link Discovery.Outcome}), until the slot changes. */
    public static final int DATA_SCAN = 8;
    public static final int DATA_COUNT = 9;

    /** FE one scan takes. */
    public static final int SCAN_ENERGY = 1000;

    /** What stabilizing one trait or one mutation takes. */
    public static final int STABILIZE_TRAIT_JELLY = 2;
    public static final int STABILIZE_TRAIT_ENERGY = 30_000;
    public static final int STABILIZE_MUTATION_JELLY = 4;
    public static final int STABILIZE_MUTATION_ENERGY = 60_000;

    /** The slots an operation takes its materials from: each page has its own. */
    public enum Bay {
        EDITING(SLOT_MATERIAL, MATERIAL_SLOTS),
        MUTATION(SLOT_MUTATION, MUTATION_SLOTS),
        STABILIZATION(SLOT_STABILIZER, 1);

        public final int first;
        public final int slots;

        Bay(int first, int slots) {
            this.first = first;
            this.slots = slots;
        }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return UpgradeEffects.Host.LAB.takes(stack);
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
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(energyCapacity(), AntConfig.get(AntConfig.LAB_ENERGY_INPUT), 0) {
        @Override
        protected void onEnergyChanged(int previous) {
            GeneticLabBlockEntity.this.setChanged();
        }
    };
    private Operation operation = Operation.NONE;
    private int selectedTrait;
    private @Nullable Identifier selectedMutation;
    private int selectedGene;
    private Discovery.Outcome scanned = Discovery.Outcome.NONE;
    /** Who started the running operation: a finished species mutation is recorded as their discovery. */
    private @Nullable UUID operator;
    private int progress;
    private int time;
    private int status;

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
                case DATA_MUTATION -> selectedMutationIndex();
                case DATA_GENE -> selectedGene;
                case DATA_SCAN -> scanned.ordinal();
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

    public Operation operation() {
        return operation;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    /** The FE input, on every side: takes energy in, never gives it out. */
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

    // --- Species Mutation: the chosen mutation ---

    /** Chooses one of the Queen's possible mutations by its place in the list. */
    public void selectMutation(int index, HolderLookup.Provider registries) {
        QueenData queen = queen();
        if (operation != Operation.NONE || queen == null) {
            return;
        }
        List<SpeciesMutation.Option> options = SpeciesMutation.options(registries, queen.species());
        if (index >= 0 && index < options.size()) {
            selectedMutation = options.get(index).id();
            setChanged();
        }
    }

    /** The chosen mutation of this Queen; her first one while none (or one of another species) is chosen. */
    public SpeciesMutation.@Nullable Option selectedOption(QueenData queen, HolderLookup.Provider registries) {
        List<SpeciesMutation.Option> options = SpeciesMutation.options(registries, queen.species());
        for (SpeciesMutation.Option option : options) {
            if (option.id().equals(selectedMutation)) {
                return option;
            }
        }
        return options.isEmpty() ? null : options.getFirst();
    }

    private int selectedMutationIndex() {
        QueenData queen = queen();
        if (queen == null || level == null) {
            return -1;
        }
        SpeciesMutation.Option option = selectedOption(queen, level.registryAccess());
        return option == null ? -1 : SpeciesMutation.options(level.registryAccess(), queen.species()).indexOf(option);
    }

    // --- Stabilization: the chosen gene ---

    public void selectGene(int gene) {
        if (operation == Operation.NONE && gene >= 0 && gene < Trait.values().length + 32) {
            selectedGene = gene;
            setChanged();
        }
    }

    /** Her beneficial mutations, in her genome's order: the ones Stabilization can protect. */
    public static List<Identifier> beneficial(Genome genome, HolderLookup.Provider registries) {
        return genome.mutations().stream().filter(id -> !Genetics.isFlaw(id, registries)).toList();
    }

    /** The mutation a gene number stands for (10 and up), or null for a trait or a number past her mutations. */
    public static @Nullable Identifier geneMutation(int gene, Genome genome, HolderLookup.Provider registries) {
        List<Identifier> beneficial = beneficial(genome, registries);
        int index = gene - Trait.values().length;
        return index >= 0 && index < beneficial.size() ? beneficial.get(index) : null;
    }

    public static boolean geneExists(int gene, Genome genome, HolderLookup.Provider registries) {
        return gene >= 0 && gene < Trait.values().length || geneMutation(gene, genome, registries) != null;
    }

    public static boolean geneStabilized(int gene, Genome genome, HolderLookup.Provider registries) {
        if (gene >= 0 && gene < Trait.values().length) {
            return genome.locked(Trait.values()[gene]);
        }
        Identifier mutation = geneMutation(gene, genome, registries);
        return mutation != null && genome.protects(mutation);
    }

    /** Releases the chosen gene again: at once and for nothing (the Royal Jelly is not given back). */
    public void release(HolderLookup.Provider registries) {
        QueenData queen = queen();
        if (operation != Operation.NONE || queen == null || !geneStabilized(selectedGene, queen.genome(), registries)) {
            return;
        }
        setQueen(queen.withGenome(stabilized(queen.genome(), selectedGene, false, registries)));
    }

    private static Genome stabilized(Genome genome, int gene, boolean on, HolderLookup.Provider registries) {
        if (gene >= 0 && gene < Trait.values().length) {
            return genome.withLock(Trait.values()[gene], on);
        }
        Identifier mutation = geneMutation(gene, genome, registries);
        return mutation == null ? genome : genome.withProtection(mutation, on);
    }

    /** Rewrites the Queen in her tube without stopping anything. */
    private void setQueen(QueenData queen) {
        ItemStack tube = items.get(SLOT_QUEEN).copy();
        tube.set(ModDataComponents.QUEEN.get(), queen);
        items.set(SLOT_QUEEN, tube);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The scanner: reads the item in its slot for this player. A material is researched, ants in a tube are recorded;
     * the item stays where it is. It needs no Queen and takes a little FE.
     */
    public void scan(Player player) {
        ItemStack stack = items.get(SLOT_SCAN);
        if (stack.isEmpty() || !(level instanceof ServerLevel server)) {
            return;
        }
        if (energy.getAmountAsInt() < SCAN_ENERGY) {
            scanned = Discovery.Outcome.NO_POWER;
            setChanged();
            return;
        }
        energy.set(energy.getAmountAsInt() - SCAN_ENERGY);
        scanned = Discovery.scan(player, stack);
        boolean discovery = scanned == Discovery.Outcome.NEW || scanned == Discovery.Outcome.SPECIES_NEW;
        server.playSound(null, worldPosition, (discovery ? ModSounds.LAB_DISCOVERY : ModSounds.LAB_SCAN).get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        setChanged();
    }

    /** The lang key of what the running operation works on: the species being made, the gene being stabilized, or the trait. */
    public String workingOnKey(HolderLookup.Provider registries) {
        QueenData queen = queen();
        if (queen != null && operation == Operation.TRANSFORM) {
            SpeciesMutation.Option option = selectedOption(queen, registries);
            if (option != null) {
                return AntSpecies.translationKey(option.evolution().to());
            }
        }
        if (queen != null && operation == Operation.STABILIZE) {
            Identifier mutation = geneMutation(selectedGene, queen.genome(), registries);
            return mutation != null ? Mutation.translationKey(mutation)
                    : Trait.values()[Math.clamp(selectedGene, 0, Trait.values().length - 1)].translationKey();
        }
        return selectedTrait().translationKey();
    }

    /** Analysis: what this Queen's species can become goes into the player's (or their team's) knowledge. */
    public void analyse(Player player) {
        QueenData queen = queen();
        if (queen != null) {
            Discovery.analysed(player, queen.species());
        }
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

    public double costMultiplier() {
        return AntConfig.get(AntConfig.LAB_COST_MULTIPLIER) * (1 - Math.min(0.5, 0.1 * tier(UpgradeType.GENETIC)));
    }

    public static Optional<TraitUpgrade> traitUpgrade(HolderLookup.Provider registries, Trait trait) {
        return registries.lookup(ModRegistries.TRAIT_UPGRADE)
                .flatMap(lookup -> lookup.listElements().map(Holder::value).filter(upgrade -> upgrade.trait() == trait).findFirst());
    }

    // --- Working ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneticLabBlockEntity lab) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // The block lights up while the lab works.
        boolean working = lab.operation != Operation.NONE;
        if (state.hasProperty(GeneticLabBlock.WORKING)
                && state.getValue(GeneticLabBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(GeneticLabBlock.WORKING, working), Block.UPDATE_ALL);
        }
        if (!working) {
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

    public void start(Operation next, RegistryAccess registries) {
        start(next, registries, null);
    }

    /**
     * Starts an operation if everything it needs is there; nothing is used until it finishes, so cancelling is free.
     *
     * @param operator who pressed the button: a species mutation needs their (or their team's) research
     */
    public void start(Operation next, RegistryAccess registries, @Nullable Player operator) {
        if (operation != Operation.NONE) {
            return;
        }
        status = 0;
        QueenData queen = queen();
        if (queen == null) {
            status = ColonyStatus.NO_QUEEN.bit();
            return;
        }
        int problem = next == Operation.TRANSFORM ? mutationProblem(queen, registries, operator)
                : next == Operation.PREPARE_SAMPLE && !items.get(SLOT_SAMPLE_OUTPUT).isEmpty() ? ColonyStatus.STORAGE_FULL.bit() : 0;
        if (problem != 0) {
            status = problem;
            return;
        }
        Requirement requirement = requirement(next, queen, registries);
        if (requirement == null || !requirement.met(this)) {
            status = ColonyStatus.INSUFFICIENT_MATERIALS.bit();
            return;
        }
        this.operator = operator == null ? null : operator.getUUID();
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

    /**
     * Why the chosen species mutation can't start, as a status flag; 0 when only materials and power are left to check.
     * Research is the operator's: nobody at the buttons means nothing is researched.
     */
    public int mutationProblem(QueenData queen, HolderLookup.Provider registries, @Nullable Player operator) {
        SpeciesMutation.Option option = selectedOption(queen, registries);
        if (option == null) {
            return ColonyStatus.INCOMPATIBLE_SPECIES.bit();
        }
        Knowledge knowledge = operator == null ? Knowledge.EMPTY : Discovery.view(operator);
        Set<SpeciesMutation.Missing> missing = SpeciesMutation.missing(option, knowledge, queen,
                level == null ? Level.OVERWORLD : level.dimension());
        if (missing.contains(SpeciesMutation.Missing.RESEARCH)) {
            return ColonyStatus.NOT_RESEARCHED.bit();
        }
        if (missing.contains(SpeciesMutation.Missing.QUEEN_GROWTH)) {
            return ColonyStatus.QUEEN_NOT_GROWN.bit();
        }
        if (missing.contains(SpeciesMutation.Missing.DIMENSION)) {
            return ColonyStatus.WRONG_DIMENSION.bit();
        }
        return items.get(SLOT_MUTATION_OUTPUT).isEmpty() ? 0 : ColonyStatus.STORAGE_FULL.bit();
    }

    /**
     * What an operation takes.
     *
     * @param catalyst an item taken from {@code catalystSlot}, or null
     * @param bay      where the materials lie
     */
    public record Requirement(List<Cost> materials, int energy, @Nullable Item catalyst, int catalystCount, int time, Bay bay, int catalystSlot) {
        public Requirement(List<Cost> materials, int energy, @Nullable Item catalyst, int catalystCount, int time) {
            this(materials, energy, catalyst, catalystCount, time, Bay.EDITING, SLOT_CATALYST);
        }

        boolean met(GeneticLabBlockEntity lab) {
            if (lab.energy() < energy) {
                return false;
            }
            if (catalyst != null && (!lab.items.get(catalystSlot).is(catalyst) || lab.items.get(catalystSlot).getCount() < catalystCount)) {
                return false;
            }
            for (Cost cost : materials) {
                int found = 0;
                for (int slot = bay.first; slot < bay.first + bay.slots; slot++) {
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
        return requirement(op, selectedTrait(), queen, registries);
    }

    /** What an operation on this trait takes. The screen passes the menu's trait: the client's lab isn't told the selection. */
    public @Nullable Requirement requirement(Operation op, Trait trait, QueenData queen, HolderLookup.Provider registries) {
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
            case PREPARE_SAMPLE -> new Requirement(List.of(), 10_000, ModItems.TEST_TUBE.get(), 1, 100, Bay.EDITING, SLOT_TUBE);
            case TRANSFORM -> {
                SpeciesMutation.Option option = selectedOption(queen, registries);
                if (option == null || !SpeciesMutation.grownEnough(queen, option.evolution())
                        || this.level != null && !option.evolution().allowedIn(this.level.dimension())) {
                    yield null;
                }
                yield new Requirement(option.costs(costs), option.energy(costs), null, 0, option.research().research().ticks(),
                        Bay.MUTATION, SLOT_CATALYST);
            }
            case STABILIZE -> {
                if (!geneExists(selectedGene, queen.genome(), registries) || geneStabilized(selectedGene, queen.genome(), registries)) {
                    yield null;
                }
                boolean isTrait = selectedGene < Trait.values().length;
                Cost jelly = new Cost(Ingredient.of(ModItems.ROYAL_JELLY.get()), isTrait ? STABILIZE_TRAIT_JELLY : STABILIZE_MUTATION_JELLY);
                yield new Requirement(List.of(new Cost(jelly.ingredient(), jelly.scaled(costs))),
                        (int) Math.ceil((isTrait ? STABILIZE_TRAIT_ENERGY : STABILIZE_MUTATION_ENERGY) * costs), null, 0, isTrait ? 300 : 400,
                        Bay.STABILIZATION, SLOT_CATALYST);
            }
            case NONE -> null;
        };
    }

    /** The highest level a Reroll can reach: four short of the top, so the expensive last steps can only be upgraded. */
    public static int rerollCap() {
        return Math.max(1, Trait.maxLevel() - 4);
    }

    private Map.@Nullable Entry<Identifier, Mutation> catalystMutation(Genome genome, HolderLookup.Provider registries) {
        ItemStack catalyst = items.get(SLOT_CATALYST);
        if (catalyst.isEmpty()) {
            return null;
        }
        if (!Genetics.roomForBeneficial(genome.mutations(), registries)) {
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
        if (operation == Operation.TRANSFORM && !items.get(SLOT_MUTATION_OUTPUT).isEmpty()
                || operation == Operation.PREPARE_SAMPLE && !items.get(SLOT_SAMPLE_OUTPUT).isEmpty()) {
            cancel(ColonyStatus.STORAGE_FULL.bit());
            return;
        }
        Genome genome = queen.genome();
        Trait trait = selectedTrait();
        ItemStack result = null;
        SpeciesMutation.Option mutated = null;
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
            case SAMPLE, PREPARE_SAMPLE -> {
                result = new ItemStack(ModItems.GENETIC_SAMPLE.get());
                result.set(ModDataComponents.QUEEN.get(), queen);
            }
            case TRANSFORM -> mutated = selectedOption(queen, level.registryAccess());
            case STABILIZE -> genome = stabilized(genome, selectedGene, true, level.registryAccess());
            case NONE -> {
            }
        }
        // Pay.
        energy.set(Math.max(0, energy.getAmountAsInt() - requirement.energy()));
        Bay bay = requirement.bay();
        for (Cost cost : requirement.materials()) {
            int left = cost.count();
            for (int slot = bay.first; slot < bay.first + bay.slots && left > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (cost.test(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    ItemStackTemplate remainder = stack.getCraftingRemainder();
                    stack.shrink(taken);
                    left -= taken;
                    if (remainder != null) {
                        // What a material leaves behind (a bottle, a bucket) stays in its slot, or drops out on top.
                        ItemStack leftover = remainder.create();
                        leftover.setCount(taken);
                        if (items.get(slot).isEmpty()) {
                            items.set(slot, leftover);
                        } else {
                            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, leftover);
                        }
                    }
                }
            }
        }
        if (requirement.catalyst() != null) {
            items.get(requirement.catalystSlot()).shrink(requirement.catalystCount());
        }
        if (mutated != null) {
            // The Queen leaves as the new species; her genes, her growth and her lineage come with her.
            ItemStack tube = items.get(SLOT_QUEEN).copy();
            tube.set(ModDataComponents.QUEEN.get(), queen.evolvedInto(mutated.evolution().to()));
            items.set(SLOT_MUTATION_OUTPUT, tube);
            items.set(SLOT_QUEEN, ItemStack.EMPTY);
            if (operator != null) {
                Discovery.record(level.getServer(), operator, Knowledge.Kind.SPECIES, mutated.evolution().to());
            }
        } else if (operation == Operation.PREPARE_SAMPLE) {
            items.set(SLOT_SAMPLE_OUTPUT, result);
        } else if (result != null) {
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
            ClientHooks.labEvent(this, id);
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
        if (slot == SLOT_SCAN) {
            scanned = Discovery.Outcome.NONE;
        }
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
        if (slot == SLOT_SCAN) {
            scanned = Discovery.Outcome.NONE;
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_QUEEN -> stack.is(ModItems.QUEEN_TEST_TUBE.get());
            case SLOT_MATERIAL, SLOT_MATERIAL + 1, SLOT_MATERIAL + 2 -> isEditingMaterial(stack);
            case SLOT_CATALYST -> isCatalyst(stack);
            case SLOT_MUTATION, SLOT_MUTATION + 1 -> level == null || SpeciesMutation.isMaterial(level.registryAccess(), stack);
            case SLOT_TUBE -> stack.is(ModItems.TEST_TUBE.get());
            case SLOT_STABILIZER -> stack.is(ModItems.ROYAL_JELLY.get());
            case SLOT_MUTATION_OUTPUT, SLOT_SAMPLE_OUTPUT -> false;
            // The scanner reads anything.
            default -> true;
        };
    }

    /** Whether some trait upgrade takes this stack as a material. */
    private boolean isEditingMaterial(ItemStack stack) {
        if (level == null) {
            return true;
        }
        var upgrades = level.registryAccess().lookup(ModRegistries.TRAIT_UPGRADE);
        return upgrades.isEmpty() || upgrades.get().listElements().anyMatch(upgrade -> upgrade.value().steps().stream()
                .anyMatch(step -> step.cost().stream().anyMatch(cost -> cost.test(stack))));
    }

    /** Whether the catalyst slot uses this stack: Reroll Serum, an empty Test Tube, or a mutation's catalyst. */
    private boolean isCatalyst(ItemStack stack) {
        if (stack.is(ModItems.REROLL_SERUM.get()) || stack.is(ModItems.TEST_TUBE.get()) || level == null) {
            return true;
        }
        var mutations = level.registryAccess().lookup(ModRegistries.MUTATION);
        return mutations.isEmpty() || mutations.get().listElements().map(Holder::value)
                .anyMatch(mutation -> !mutation.negative() && mutation.catalyst().isPresent() && mutation.catalyst().get().test(stack));
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

    /** Hoppers may bring materials; Queens are only handled by hand. */
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
        return Component.translatable("block.antcolonies.genetic_laboratory");
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
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, energy.getCapacityAsInt()));
        operation = Operation.byId(input.getIntOr("operation", 0));
        selectedTrait = input.getIntOr("trait", 0);
        selectedMutation = input.read("mutation", Identifier.CODEC).orElse(null);
        selectedGene = input.getIntOr("gene", 0);
        operator = input.read("operator", UUIDUtil.CODEC).orElse(null);
        progress = input.getIntOr("progress", 0);
        time = input.getIntOr("time", 0);
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
        output.storeNullable("mutation", Identifier.CODEC, selectedMutation);
        output.putInt("gene", selectedGene);
        output.storeNullable("operator", UUIDUtil.CODEC, operator);
        output.putInt("progress", progress);
        output.putInt("time", time);
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
