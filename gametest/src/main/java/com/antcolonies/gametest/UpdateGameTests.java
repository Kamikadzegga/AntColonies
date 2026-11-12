package com.antcolonies.gametest;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.expedition.Expedition;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.ForagingMode;
import com.antcolonies.colony.model.Founding;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.network.ConnectorNetwork;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.content.blockentity.ConnectorSettings;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.content.item.ChitinGear;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.TweezersItem;
import com.antcolonies.content.worldgen.AnthillLayout;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModDecor;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Game tests of the colony update: Queen-only founding (and every way a colony starts or moves that must not trigger
 * it), anthill layouts and their guaranteed Queen, Foraging Site modes, food as a constraint, the Nether and End
 * Foraging Sites and their trail pheromones, Colony Connectors, and the building blocks' data.
 */
final class UpdateGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, AntColonies.MODID);
    private static final Identifier COMMON = AntColonies.id("common");

    private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> ALL = List.of(
            test("founding_queen_alone", UpdateGameTests::foundingQueenAlone),
            test("founding_not_with_workers", UpdateGameTests::foundingNotWithWorkers),
            test("founding_not_for_workers_or_moves", UpdateGameTests::foundingNotForWorkersOrMoves),
            test("founding_food_gate_only_for_founding", UpdateGameTests::foundingFoodGateOnlyForFounding),
            test("founding_saves_and_ends_cleanly", UpdateGameTests::foundingSavesAndEndsCleanly),
            test("anthill_layouts_are_whole", UpdateGameTests::anthillLayoutsAreWhole),
            test("anthill_queen_guaranteed", UpdateGameTests::anthillQueenGuaranteed),
            test("end_anthill_builds", UpdateGameTests::endAnthillBuilds),
            test("foraging_modes_shift_finds", UpdateGameTests::foragingModesShiftFinds),
            test("food_limits_work", UpdateGameTests::foodLimitsWork),
            test("expedition_sites", UpdateGameTests::expeditionSites),
            test("trail_pheromone_in_the_nether", UpdateGameTests::trailPheromoneInTheNether),
            test("connectors_share_food", UpdateGameTests::connectorsShareFood),
            test("connectors_move_items", UpdateGameTests::connectorsMoveItems),
            test("connectors_respect_range", UpdateGameTests::connectorsRespectRange),
            test("decor_blocks_drop", UpdateGameTests::decorBlocksDrop),
            test("job_priority_and_switch", UpdateGameTests::jobPriorityAndSwitch),
            test("chitin_tiers", UpdateGameTests::chitinTiers),
            test("chitin_only_from_ants", UpdateGameTests::chitinOnlyFromAnts),
            test("queen_grows_with_colony", UpdateGameTests::queenGrowsWithColony),
            test("food_slot", UpdateGameTests::foodSlot));

    private UpdateGameTests() {}

    static void register(IEventBus bus) {
        TESTS.register(bus);
    }

    static void registerTests(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : ALL) {
            event.registerTest(test.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, test.getId()),
                    AntColoniesGameTests.spaced(environment, 300)));
        }
    }

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test(String name, Consumer<GameTestHelper> test) {
        return TESTS.register(name, () -> test);
    }

    // --- Helpers ---

    private static ItemStack queenTube(Identifier species) {
        return TweezersItem.queenTube(Genetics.wildQueen(species, RandomSource.create(3)));
    }

    private static ItemStack workerTube(Identifier species, int count) {
        return TweezersItem.antTube(new AntSample(species, Caste.WORKER, count));
    }

    /** An empty Terrarium, placed. */
    private static TerrariumBlockEntity emptyTerrarium(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.TERRARIUM.get());
        return helper.getBlockEntity(pos, TerrariumBlockEntity.class);
    }

    private static CompoundTag save(GameTestHelper helper, Colony colony) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        colony.save(output, false);
        return output.buildResult();
    }

    private static Colony load(GameTestHelper helper, CompoundTag tag) {
        Colony colony = new Colony();
        colony.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        return colony;
    }

    // --- Queen-only founding ---

    /**
     * Scenario A: one Queen into a completely empty Terrarium starts founding. She lays nothing until the food bar is full;
     * then she lays, the eggs hatch, the larvae grow, and once her first workers are grown the colony is founded.
     */
    private static void foundingQueenAlone(GameTestHelper helper) {
        TerrariumBlockEntity terrarium = emptyTerrarium(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        terrarium.processSlots(level);
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.queen() != null, "The Queen should move in");
        helper.assertTrue(colony.founding() == Founding.FEEDING, "A Queen alone in an empty Terrarium should start founding: " + colony.founding());
        // Half the food: still waiting, no eggs, and she doesn't eat her reserve away meanwhile.
        colony.setFood(ColonySimulator.foundingFood() / 2);
        AntColoniesGameTests.step(helper, terrarium, 2);
        helper.assertTrue(colony.eggs() == 0 && colony.founding() == Founding.FEEDING, "No eggs before the food bar is full: " + colony.eggs());
        helper.assertTrue(ColonyStatus.FOUNDING.in(colony.status()), "The colony should say it is founding");
        helper.assertTrue(Math.abs(colony.food() - ColonySimulator.foundingFood() / 2) < 0.01, "A founding Queen waiting for food eats nothing: " + colony.food());
        // Fed through the feed slot until the bar is full.
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(ModItems.ANT_FOOD.get(), 64));
        terrarium.processSlots(level);
        helper.assertTrue(colony.food() >= ColonySimulator.foundingFood() - 0.5, "The feed slot should fill the food bar: " + colony.food());
        AntColoniesGameTests.step(helper, terrarium, 1);
        helper.assertTrue(colony.founding() == Founding.BROOD, "A full food bar should start her first brood: " + colony.founding());
        helper.assertTrue(colony.eggs() > 0 || colony.larvae() > 0, "She should lay once fed");
        // Eggs to larvae to workers; the colony is founded.
        for (int i = 0; i < 40 && colony.founding().active(); i++) {
            colony.setFood(Math.max(colony.food(), 1500));
            AntColoniesGameTests.step(helper, terrarium, 1);
        }
        helper.assertTrue(colony.founding() == Founding.NONE && colony.queenFounded(), "Her first workers should found the colony: " + colony.founding()
                + ", " + colony.workers() + " workers");
        helper.assertTrue(colony.workers() >= AntConfig.get(AntConfig.FOUNDING_WORKERS) - 1, "Workers should have been raised: " + colony.workers());
        helper.succeed();
    }

    /** Scenario B: a Queen and workers put in together (the same pass) never found: the colony runs normally. */
    private static void foundingNotWithWorkers(GameTestHelper helper) {
        TerrariumBlockEntity terrarium = emptyTerrarium(helper, new BlockPos(2, 1, 2));
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, workerTube(COMMON, 50));
        terrarium.processSlots(helper.getLevel());
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.queen() != null && colony.workers() == 50, "Queen and workers should both be in: " + colony.workers());
        helper.assertTrue(colony.founding() == Founding.NONE, "A Queen with workers must not found: " + colony.founding());
        // And a Queen first, then workers a moment later (moving a colony tube by tube): founding ends at once.
        TerrariumBlockEntity second = emptyTerrarium(helper, new BlockPos(8, 1, 2));
        second.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        second.processSlots(helper.getLevel());
        helper.assertTrue(second.colony().founding() == Founding.FEEDING, "A lone Queen founds");
        second.setItem(TerrariumBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        second.setItem(TerrariumBlockEntity.SLOT_FEED, workerTube(COMMON, 20));
        second.processSlots(helper.getLevel());
        helper.assertTrue(second.colony().founding() == Founding.NONE && second.colony().workers() == 20,
                "Workers joining a founding Queen make it a normal colony: " + second.colony().founding());
        // Eggs too.
        TerrariumBlockEntity third = emptyTerrarium(helper, new BlockPos(14, 1, 2));
        third.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        third.processSlots(helper.getLevel());
        ItemStack eggs = new ItemStack(ModItems.ANT_EGG.get(), 2);
        eggs.set(ModDataComponents.SPECIES.get(), COMMON);
        third.setItem(TerrariumBlockEntity.SLOT_FEED, eggs);
        third.processSlots(helper.getLevel());
        helper.assertTrue(third.colony().founding() == Founding.NONE, "Eggs put in with the Queen end founding");
        helper.succeed();
    }

    /**
     * Scenarios C to F: workers alone into an empty Terrarium (no Queen: nothing founds), a whole colony moved (workers
     * first, then its Queen), and a Queen added to a colony that has ants: never founding, populations kept.
     */
    private static void foundingNotForWorkersOrMoves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TerrariumBlockEntity workersOnly = emptyTerrarium(helper, new BlockPos(2, 1, 2));
        workersOnly.setItem(TerrariumBlockEntity.SLOT_FEED, workerTube(COMMON, 300));
        workersOnly.processSlots(level);
        helper.assertTrue(workersOnly.colony().queen() == null && workersOnly.colony().founding() == Founding.NONE
                && workersOnly.colony().workers() == 300, "Workers alone: no Queen, no founding, all 300 kept");
        // A colony moved: its workers go in first, then its Queen.
        TerrariumBlockEntity moved = emptyTerrarium(helper, new BlockPos(8, 1, 2));
        moved.setItem(TerrariumBlockEntity.SLOT_FEED, workerTube(COMMON, 30));
        moved.processSlots(level);
        moved.setItem(TerrariumBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        moved.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        moved.processSlots(level);
        Colony colony = moved.colony();
        helper.assertTrue(colony.queen() != null && colony.workers() == 30, "The moved colony keeps all its ants: " + colony.workers());
        helper.assertTrue(colony.founding() == Founding.NONE, "A Queen joining her ants must not found");
        // A Queen added to an existing colony with larvae: nothing reset.
        TerrariumBlockEntity existing = AntColoniesGameTests.terrarium(helper, new BlockPos(14, 1, 2), COMMON, 120, 0, 500);
        existing.colony().addLarvae(40);
        existing.colony().setQueen(null);
        existing.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        existing.processSlots(level);
        helper.assertTrue(existing.colony().founding() == Founding.NONE && existing.colony().workers() == 120 && existing.colony().larvae() == 40,
                "Adding a Queen to a colony keeps its population and never founds");
        // Normal colonies lay at once, with ordinary food.
        colony.setFood(500);
        AntColoniesGameTests.step(helper, moved, 1);
        helper.assertTrue(colony.eggs() > 0 || colony.larvae() > 0, "A moved colony lays straight away");
        helper.succeed();
    }

    /** The full food bar is needed only by a founding Queen: an ordinary young colony with a little food lays anyway. */
    private static void foundingFoodGateOnlyForFounding(GameTestHelper helper) {
        TerrariumBlockEntity normal = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 5, 0, 100);
        AntColoniesGameTests.step(helper, normal, 1);
        helper.assertTrue(normal.colony().eggs() > 0 || normal.colony().larvae() > 0, "An ordinary colony with 100 food lays");
        TerrariumBlockEntity founding = emptyTerrarium(helper, new BlockPos(8, 1, 2));
        founding.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        founding.processSlots(helper.getLevel());
        founding.colony().setFood(100);
        AntColoniesGameTests.step(helper, founding, 1);
        helper.assertTrue(founding.colony().eggs() == 0 && founding.colony().larvae() == 0, "A founding Queen with 100 food waits");
        helper.succeed();
    }

    /**
     * Founding is saved and loaded (old saves load as NONE), and taking the Queen out mid-founding ends it without leaving
     * anything broken: no negative counts, and putting her back with eggs present doesn't found again.
     */
    private static void foundingSavesAndEndsCleanly(GameTestHelper helper) {
        TerrariumBlockEntity terrarium = emptyTerrarium(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube(COMMON));
        terrarium.processSlots(level);
        Colony copy = load(helper, save(helper, terrarium.colony()));
        helper.assertTrue(copy.founding() == Founding.FEEDING && copy.queen() != null, "Founding should survive saving: " + copy.founding());
        // A colony saved before founding existed: no "founding" key.
        Colony old = load(helper, save(helper, AntColoniesGameTests.terrarium(helper, new BlockPos(8, 1, 2), COMMON, 50, 0, 100).colony()));
        helper.assertTrue(old.founding() == Founding.NONE, "Colonies without founding data load as normal colonies");
        // Fed, laying: then the Queen is taken out in an empty tube.
        terrarium.colony().setFood(ColonySimulator.foundingFood());
        AntColoniesGameTests.step(helper, terrarium, 1);
        helper.assertTrue(terrarium.colony().founding() == Founding.BROOD, "Fed: the first brood");
        terrarium.setItem(TerrariumBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, new ItemStack(ModItems.TEST_TUBE.get()));
        terrarium.processSlots(level);
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.queen() == null && colony.founding() == Founding.NONE, "Taking the Queen out ends founding");
        helper.assertTrue(colony.eggs() >= 0 && colony.larvae() >= 0 && colony.workers() >= 0, "No negative numbers");
        // Back in (after the short wait), with eggs present: a normal colony.
        ItemStack queen = terrarium.getItem(TerrariumBlockEntity.SLOT_OUTPUT).copy();
        helper.runAfterDelay(50, () -> {
            terrarium.setItem(TerrariumBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, queen);
            terrarium.processSlots(level);
            helper.assertTrue(colony.queen() != null, "The Queen should go back in");
            helper.assertTrue(colony.founding() == Founding.NONE || colony.isEmptyOfAnts(), "With brood present she doesn't found again");
            helper.succeed();
        });
    }

    // --- Anthills ---

    /**
     * Every style over many seeds: exactly one Queen's chamber, rooms clear of each other and within reach, and every
     * room (the Queen's above all) joined by dug-out space to the top of the entrance shaft.
     */
    private static void anthillLayoutsAreWhole(GameTestHelper helper) {
        int checked = 0;
        for (AnthillLayout.Style style : AnthillLayout.Style.values()) {
            for (long seed = 1; seed <= 25; seed++) {
                AnthillLayout layout = AnthillLayout.create(style, seed * 7919);
                long queens = layout.rooms().stream().filter(r -> r.type() == AnthillLayout.RoomType.QUEEN).count();
                helper.assertTrue(queens == 1, style + "/" + seed + ": " + queens + " Queen's chambers");
                for (AnthillLayout.Room room : layout.rooms()) {
                    helper.assertTrue(Math.abs(room.x()) <= 24 && Math.abs(room.z()) <= 24 && room.y() > -42, style + "/" + seed + ": room out of reach " + room);
                }
                // Flood fill through the dug-out cells from the shaft's top.
                Set<BlockPos> seen = new HashSet<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                BlockPos start = new BlockPos(0, 0, 0);
                helper.assertTrue(layout.carved(0, 0, 0), style + "/" + seed + ": the shaft should be dug at the ground");
                queue.add(start);
                seen.add(start);
                while (!queue.isEmpty() && seen.size() < 200_000) {
                    BlockPos pos = queue.poll();
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        BlockPos next = pos.relative(side);
                        if (next.getY() <= 6 && !seen.contains(next) && layout.carved(next.getX(), next.getY(), next.getZ())) {
                            seen.add(next);
                            queue.add(next);
                        }
                    }
                }
                for (AnthillLayout.Room room : layout.rooms()) {
                    BlockPos middle = BlockPos.containing(room.x(), room.y(), room.z());
                    helper.assertTrue(seen.contains(middle), style + "/" + seed + ": " + room.type() + " room can't be reached from the entrance");
                }
                checked++;
            }
        }
        helper.assertTrue(checked == AnthillLayout.Style.values().length * 25, "Checked " + checked);
        helper.succeed();
    }

    /**
     * A planned anthill built into stone: exactly one Queen in it, persistent, unhurt by damage, and caught by Tweezers
     * (after which there is none: no second Queen appears).
     */
    private static void anthillQueenGuaranteed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(10, 0, 10));
        AnthillPiece piece = new AnthillPiece(center, AnthillPiece.Kind.OVERWORLD, AnthillLayout.Style.CENTRAL, 4242L);
        var box = piece.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), center.getY(), box.maxZ())) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box,
                net.minecraft.world.level.ChunkPos.containing(center), center);
        helper.runAfterDelay(20, () -> {
            var ants = level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1,
                    box.maxY() + 1, box.maxZ() + 1));
            List<WildAnt> queens = ants.stream().filter(a -> a.caste() == Caste.QUEEN).toList();
            helper.assertTrue(queens.size() == 1, "Exactly one Queen, found " + queens.size() + " of " + ants.size() + " ants");
            WildAnt queen = queens.getFirst();
            helper.assertTrue(queen.isAnthillQueen() && queen.isPersistenceRequired(), "The anthill's Queen is guarded and persistent");
            float health = queen.getHealth();
            queen.hurtServer(level, level.damageSources().generic(), 50);
            queen.hurtServer(level, level.damageSources().lava(), 50);
            helper.assertTrue(queen.isAlive() && queen.getHealth() == health, "The anthill's Queen can't be hurt: " + queen.getHealth());
            // Caught with Tweezers: she leaves in a Queen Test Tube.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack tweezers = new ItemStack(ModItems.TWEEZERS.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tweezers);
            player.getInventory().add(new ItemStack(ModItems.TEST_TUBE.get()));
            ModItems.TWEEZERS.get().interactLivingEntity(tweezers, player, queen, InteractionHand.MAIN_HAND);
            helper.assertTrue(queen.isRemoved(), "Tweezers should catch her");
            helper.assertTrue(player.getInventory().contains(stack -> stack.is(ModItems.QUEEN_TEST_TUBE.get())), "Her tube should be in the inventory");
            long left = level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(center).inflate(30)).stream()
                    .filter(a -> a.caste() == Caste.QUEEN).count();
            helper.assertTrue(left == 0, "No second Queen: " + left);
            level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(center).inflate(30)).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    /** The End anthill: end stone and purpur walls, Chorus Ants, its own loot. */
    private static void endAnthillBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        helper.assertTrue(structures.get(ResourceKey.create(Registries.STRUCTURE, AntColonies.id("end_anthill"))).isPresent(), "end_anthill didn't load");
        BlockPos center = helper.absolutePos(new BlockPos(10, 0, 10));
        AnthillPiece piece = new AnthillPiece(center, AnthillPiece.Kind.END, AnthillLayout.Style.ABANDONED, 99L);
        var box = piece.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), center.getY(), box.maxZ())) {
            level.setBlock(pos, Blocks.END_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box,
                net.minecraft.world.level.ChunkPos.containing(center), center);
        int bricks = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.END_STONE_BRICKS) || state.is(Blocks.PURPUR_BLOCK)) {
                bricks++;
            }
            if (state.is(Blocks.BARREL) && level.getBlockEntity(pos) instanceof net.minecraft.world.RandomizableContainer container
                    && container.getLootTable() != null && container.getLootTable().equals(AnthillPiece.END_LOOT)) {
                bricks += 1000;
            }
        }
        helper.assertTrue(bricks > 1000, "End walls and the End loot barrel: " + bricks);
        helper.runAfterDelay(20, () -> {
            var ants = level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(center).inflate(30));
            helper.assertTrue(!ants.isEmpty() && ants.stream().allMatch(a -> a.species().equals(AntColonies.id("chorus"))), "Chorus Ants: "
                    + ants.stream().map(WildAnt::species).toList());
            helper.assertTrue(ants.stream().filter(a -> a.caste() == Caste.QUEEN).count() == 1, "One Queen in the End anthill too");
            ants.forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    // --- Production ---

    /** Mineral Gathering picks more of the minerals than Balanced, Organic fewer; the same outputs, the species decides. */
    private static void foragingModesShiftFinds(GameTestHelper helper) {
        AntSpecies common = ModRegistries.species(helper.getLevel().registryAccess(), COMMON);
        List<SpeciesOutput> outputs = common.production().outputs();
        helper.assertTrue(outputs.stream().anyMatch(ForagingMode::mineral) && outputs.stream().anyMatch(o -> !ForagingMode.mineral(o)),
                "Common Ants should find both kinds");
        double[] share = new double[3];
        for (ForagingMode mode : ForagingMode.values()) {
            RandomSource random = RandomSource.create(77);
            int minerals = 0;
            for (int i = 0; i < 4000; i++) {
                SpeciesOutput picked = ColonySimulator.pickOutput(outputs, 4, 1.0, random, mode);
                if (picked != null && ForagingMode.mineral(picked)) {
                    minerals++;
                }
            }
            share[mode.ordinal()] = minerals / 4000.0;
        }
        helper.assertTrue(share[1] > share[0] + 0.15 && share[2] < share[0] - 0.05, "Mineral/Balanced/Organic shares: " + share[1] + " / " + share[0]
                + " / " + share[2]);
        // A site's mode is saved with it.
        StructureBlockEntity site = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 2), StructureKind.FORAGING_SITE);
        site.setMode(ForagingMode.ORGANIC);
        CompoundTag tag = site.saveWithoutMetadata(helper.getLevel().registryAccess());
        site.setMode(ForagingMode.BALANCED);
        site.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        helper.assertTrue(site.mode() == ForagingMode.ORGANIC, "The mode should be saved: " + site.mode());
        helper.succeed();
    }

    /**
     * Food as a constraint: the same colony finds less with its food nearly gone than with plenty; a starving Farmer
     * colony still grows food (it can always feed itself again).
     */
    private static void foodLimitsWork(GameTestHelper helper) {
        TerrariumBlockEntity fed = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 800, 0, 20000);
        StructureBlockEntity fedSite = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 3), StructureKind.FORAGING_SITE);
        AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 1), StructureKind.STORAGE);
        AntColoniesGameTests.step(helper, fed, 0.5);
        AntColoniesGameTests.step(helper, fed, 0.5);
        double fedRate = fedSite.rate();
        TerrariumBlockEntity hungry = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 2), COMMON, 800, 0, 20000);
        StructureBlockEntity hungrySite = AntColoniesGameTests.structure(helper, new BlockPos(10, 1, 3), StructureKind.FORAGING_SITE);
        AntColoniesGameTests.structure(helper, new BlockPos(10, 1, 1), StructureKind.STORAGE);
        AntColoniesGameTests.step(helper, hungry, 0.5);
        hungry.colony().setFood(1); // almost nothing left: well under lowFoodMinutes of food
        AntColoniesGameTests.step(helper, hungry, 0.5);
        helper.assertTrue(hungrySite.rate() < fedRate * 0.9, "Low food should slow work: " + hungrySite.rate() + " vs " + fedRate);
        helper.assertTrue(ColonyStatus.LOW_FOOD.in(hungry.colony().status()) || ColonyStatus.STARVING.in(hungry.colony().status())
                || ColonyStatus.NO_FOOD.in(hungry.colony().status()), "The colony should say its food is low");
        helper.assertTrue(hungry.colony().foodFactor() < 1, "Its food factor should drop: " + hungry.colony().foodFactor());
        // A starving Farmer colony keeps farming.
        TerrariumBlockEntity farmers = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 10), AntColonies.id("farmer"), 400, 0, 0);
        AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 11), StructureKind.HARVESTING_SITE);
        for (int i = 0; i < 5; i++) {
            AntColoniesGameTests.step(helper, farmers, 1);
        }
        helper.assertTrue(farmers.colony().stats().foodIn() > 0, "Hungry farmers still grow food: " + farmers.colony().stats().foodIn());
        helper.succeed();
    }

    /**
     * The Nether and End Foraging Sites: adapted ants bring back the expedition's finds (no power anywhere); other ants
     * leave the site alone and it says so.
     */
    private static void expeditionSites(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Expedition nether = Expedition.forSite(registries, StructureKind.NETHER_FORAGING_SITE);
        Expedition end = Expedition.forSite(registries, StructureKind.END_FORAGING_SITE);
        helper.assertTrue(nether != null && end != null, "Both expedition tables should load");
        helper.assertTrue(nether.adapted(registries, AntColonies.id("fire")) && nether.adapted(registries, AntColonies.id("warped"))
                && !nether.adapted(registries, COMMON), "Fire and Warped Ants go to the Nether, Common Ants don't");
        helper.assertTrue(end.adapted(registries, AntColonies.id("chorus")) && !end.adapted(registries, AntColonies.id("fire")),
                "Chorus Ants go to the End, Fire Ants don't");
        // Fire Ants at a Nether Foraging Site.
        TerrariumBlockEntity fire = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), AntColonies.id("fire"), 1500, 0, 20000);
        StructureBlockEntity site = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 3), StructureKind.NETHER_FORAGING_SITE);
        StructureBlockEntity storage = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 1), StructureKind.STORAGE);
        for (int i = 0; i < 10; i++) {
            fire.colony().setFood(20000);
            AntColoniesGameTests.step(helper, fire, 2);
        }
        helper.assertTrue(site.assigned() > 0, "Fire Ants should work the Nether site");
        Set<net.minecraft.world.item.Item> table = new HashSet<>();
        nether.outputs().forEach(find -> table.add(find.output().create(1).getItem()));
        int found = 0;
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(table.contains(stack.getItem()) || isFireOutput(registries, stack), "Not a Nether find: " + stack);
                if (table.contains(stack.getItem())) {
                    found += stack.getCount();
                }
            }
        }
        helper.assertTrue(found > 0, "Nether finds should reach Storage");
        helper.assertTrue(helper.getLevel().getBlockState(site.getBlockPos()).getValue(com.antcolonies.content.block.ExpeditionSiteBlock.ACTIVE),
                "Its portal should be lit while ants are out");
        // Common Ants at an End site: nothing, and the reason.
        TerrariumBlockEntity common = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 2), COMMON, 1500, 0, 20000);
        StructureBlockEntity endSite = AntColoniesGameTests.structure(helper, new BlockPos(10, 1, 3), StructureKind.END_FORAGING_SITE);
        AntColoniesGameTests.step(helper, common, 2);
        helper.assertTrue(endSite.assigned() == 0 && ColonyStatus.INCOMPATIBLE_SPECIES.in(endSite.siteStatus()),
                "Common Ants must leave the End site alone and it must say why");
        helper.assertTrue(!helper.getLevel().getBlockState(endSite.getBlockPos()).getValue(com.antcolonies.content.block.ExpeditionSiteBlock.ACTIVE),
                "An idle site's portal is dark");
        helper.succeed();
    }

    private static boolean isFireOutput(net.minecraft.core.RegistryAccess registries, ItemStack stack) {
        AntSpecies fire = ModRegistries.species(registries, AntColonies.id("fire"));
        return fire != null && fire.production().outputs().stream().anyMatch(o -> o.create(1).is(stack.getItem()));
    }

    /**
     * A colony living in the Nether finds Nether Trail Pheromones at its ordinary Foraging Site now and then; the same
     * colony in the Overworld never does.
     */
    private static void trailPheromoneInTheNether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "No Nether");
        BlockPos base = new BlockPos(4000, 100, 4000);
        nether.getChunk(base);
        nether.setBlock(base, ModBlocks.TERRARIUM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        nether.setBlock(base.south(), ModBlocks.STRUCTURES.get(StructureKind.FORAGING_SITE).get().defaultBlockState(), Block.UPDATE_CLIENTS);
        nether.setBlock(base.north(), ModBlocks.STRUCTURES.get(StructureKind.STORAGE).get().defaultBlockState(), Block.UPDATE_CLIENTS);
        try {
            TerrariumBlockEntity terrarium = (TerrariumBlockEntity) nether.getBlockEntity(base);
            StructureBlockEntity storage = (StructureBlockEntity) nether.getBlockEntity(base.north());
            terrarium.colony().setQueen(Genetics.wildQueen(AntColonies.id("fire"), RandomSource.create(5)));
            terrarium.colony().setPopulation(0, 0, 3000);
            int pheromones = 0;
            for (int i = 0; i < 30 && pheromones == 0; i++) {
                terrarium.colony().setFood(20000);
                terrarium.rescan(nether);
                ColonySimulator.step(terrarium, nether, 2);
                pheromones = 0;
                for (StructureBlockEntity store : List.of(storage, (StructureBlockEntity) nether.getBlockEntity(base.south()))) {
                    for (int slot = 0; slot < store.getContainerSize(); slot++) {
                        if (store.getItem(slot).is(ModItems.NETHER_TRAIL_PHEROMONE.get())) {
                            pheromones += store.getItem(slot).getCount();
                        }
                    }
                }
            }
            helper.assertTrue(pheromones > 0, "A colony foraging in the Nether should find a Nether Trail Pheromone");
        } finally {
            for (BlockPos pos : List.of(base, base.south(), base.north())) {
                nether.removeBlockEntity(pos);
                nether.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        helper.succeed();
    }

    // --- Chitin gear tiers ---

    /**
     * The higher chitin tiers: each mines what its level mines (Hardened like iron, Infernal obsidian, Void anything),
     * each lasts longer and hits harder than the one below, Infernal and Void don't burn; a full set of mixed tiers is a
     * set at its lowest tier (ants leave the wearer alone, that tier's dig bonus); a full Infernal or Void set gives Fire
     * Resistance; each tier has its recipe (Hardened crafted, the others at a smithing table from the tier below).
     */
    private static void chitinTiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var tiers = ModItems.CHITIN_TIERS;
        ItemStack hardenedPick = new ItemStack(tiers.get(ChitinGear.Tier.HARDENED).get("pickaxe").get());
        ItemStack infernalPick = new ItemStack(tiers.get(ChitinGear.Tier.INFERNAL).get("pickaxe").get());
        ItemStack voidPick = new ItemStack(tiers.get(ChitinGear.Tier.VOID).get("pickaxe").get());
        helper.assertTrue(hardenedPick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())
                && !hardenedPick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Hardened mines like iron");
        helper.assertTrue(infernalPick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Infernal mines obsidian");
        helper.assertTrue(voidPick.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()), "Void mines ancient debris");
        ItemStack previousPick = new ItemStack(ModItems.CHITIN_PICKAXE.get());
        ItemStack previousChest = new ItemStack(ModItems.CHITIN_CHESTPLATE.get());
        for (ChitinGear.Tier tier : List.of(ChitinGear.Tier.HARDENED, ChitinGear.Tier.INFERNAL, ChitinGear.Tier.VOID)) {
            ItemStack pick = new ItemStack(tiers.get(tier).get("pickaxe").get());
            ItemStack chest = new ItemStack(tiers.get(tier).get("chestplate").get());
            helper.assertTrue(pick.getMaxDamage() > previousPick.getMaxDamage() && chest.getMaxDamage() > previousChest.getMaxDamage(),
                    tier + " should last longer than the tier below");
            helper.assertTrue(tier.tools.attackDamageBonus() > ((ChitinGear.Piece) previousPick.getItem()).tier().tools.attackDamageBonus()
                    && tier.armor.toughness() > ((ChitinGear.Piece) previousChest.getItem()).tier().armor.toughness(), tier + " should be stronger");
            helper.assertTrue(chest.isValidRepairItem(new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id(tier.name().toLowerCase(java.util.Locale.ROOT) + "_chitin")))),
                    tier + " gear should be repaired with its own chitin");
            helper.assertTrue(pick.is(net.minecraft.tags.ItemTags.PICKAXES) && chest.is(net.minecraft.tags.ItemTags.CHEST_ARMOR), tier + " gear in the vanilla tags");
            boolean fireproof = chest.has(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT);
            helper.assertTrue(fireproof == tier.fireproof, tier + " fireproof: " + fireproof);
            previousPick = pick;
            previousChest = chest;
        }
        // A mixed set is a set, at its lowest tier.
        var player = helper.makeMockServerPlayerInLevel();
        double baseSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        var VOID = tiers.get(ChitinGear.Tier.VOID);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(VOID.get("helmet").get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(VOID.get("chestplate").get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(tiers.get(ChitinGear.Tier.HARDENED).get("leggings").get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        helper.assertTrue(ChitinGear.setTier(player) == ChitinGear.Tier.CHITIN && WildAnt.ignores(player), "A mixed set counts at its lowest tier");
        ChitinGear.updateSetBonus(player);
        double speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        helper.assertTrue(Math.abs(speed - baseSpeed * (1 + ChitinGear.Tier.CHITIN.digBonus)) < 1e-6, "Mixed set: Chitin's dig bonus, " + speed);
        helper.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "No Fire Resistance below Infernal");
        // The whole Void set: its bigger dig bonus (the old one replaced, not added to) and Fire Resistance.
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(VOID.get("leggings").get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(VOID.get("boots").get()));
        ChitinGear.updateSetBonus(player);
        speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        helper.assertTrue(Math.abs(speed - baseSpeed * (1 + ChitinGear.Tier.VOID.digBonus)) < 1e-6, "Void set: its dig bonus, " + speed);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "A Void set gives Fire Resistance");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
        ChitinGear.updateSetBonus(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED) == baseSpeed,
                "Taking a piece off ends the dig bonus");
        // Recipes: Hardened crafted; Infernal and Void at the smithing table, from the tier below with a Reinforcement.
        var recipes = level.getServer().getRecipeManager();
        for (String id : List.of("hardened_chitin_sword", "infernal_reinforcement", "void_reinforcement")) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, AntColonies.id(id))).isPresent(), "No recipe " + id);
        }
        String[][] smithing = {{"infernal_chitin_chestplate", "infernal_reinforcement", "hardened_chitin_chestplate", "antcolonies:infernal_chitin"},
                {"void_chitin_pickaxe", "void_reinforcement", "infernal_chitin_pickaxe", "minecraft:netherite_ingot"}};
        for (String[] s : smithing) {
            var recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE, AntColonies.id(s[0] + "_smithing")));
            helper.assertTrue(recipe.isPresent(), "No smithing recipe for " + s[0]);
            var input = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id(s[1]))),
                    new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id(s[2]))), new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s[3]))));
            @SuppressWarnings("unchecked")
            var smith = (net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SmithingRecipeInput>) recipe.get().value();
            helper.assertTrue(smith.matches(input, level), s[0] + ": the tier below with its Reinforcement should match");
            var wrong = new net.minecraft.world.item.crafting.SmithingRecipeInput(input.template(), new ItemStack(ModItems.CHITIN_CHESTPLATE.get()), input.addition());
            helper.assertTrue(!smith.matches(wrong, level), s[0] + ": a piece two tiers down should not match");
        }
        helper.succeed();
    }

    /**
     * Chitin comes only from ants. No crafting or cooking recipe (of any mod loaded) makes any chitin; wild ants drop
     * Chitin Plates and Hardened Chitin anywhere, Infernal Chitin only in the Nether and Void Chitin only in the End; a
     * foraging colony brings chitin home with its finds (plates, and Hardened Chitin once grown).
     */
    private static void chitinOnlyFromAnts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<net.minecraft.world.item.Item> chitin = Set.of(ModItems.CHITIN_PLATE.get(), ModItems.HARDENED_CHITIN.get(), ModItems.INFERNAL_CHITIN.get(),
                ModItems.VOID_CHITIN.get());
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            ItemStack result = ItemStack.EMPTY;
            if (holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting
                    && !(crafting instanceof net.minecraft.world.item.crafting.CustomRecipe)) {
                var input = AntColoniesGameTests.craftingInput(crafting);
                if (input != null && crafting.matches(input, level)) {
                    result = crafting.assemble(input);
                }
            } else if (holder.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking) {
                ItemStack item = AntColoniesGameTests.first(cooking.input());
                if (!item.isEmpty()) {
                    result = cooking.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(item));
                }
            }
            helper.assertTrue(result.isEmpty() || !chitin.contains(result.getItem()), "A recipe makes chitin: " + holder.id());
        }
        // Wild ants' drops, by dimension.
        var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("entities/wild_ant")));
        java.util.Map<ResourceKey<Level>, java.util.Map<net.minecraft.world.item.Item, Integer>> drops = new java.util.HashMap<>();
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel there = level.getServer().getLevel(dimension);
            helper.assertTrue(there != null, "No " + dimension.identifier() + " on the test server");
            WildAnt ant = com.antcolonies.core.registry.ModEntities.WILD_ANT.get().create(there, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            ant.snapTo(0.5, 64, 0.5, 0, 0);
            java.util.Map<net.minecraft.world.item.Item, Integer> counts = new java.util.HashMap<>();
            for (int i = 0; i < 1500; i++) {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(there)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, ant)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, ant.position())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, there.damageSources().generic())
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
                for (ItemStack stack : table.getRandomItems(params)) {
                    counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
            drops.put(dimension, counts);
        }
        var overworld = drops.get(Level.OVERWORLD);
        var nether = drops.get(Level.NETHER);
        var end = drops.get(Level.END);
        helper.assertTrue(overworld.getOrDefault(ModItems.CHITIN_PLATE.get(), 0) > 300 && overworld.getOrDefault(ModItems.HARDENED_CHITIN.get(), 0) > 20,
                "Wild ants should leave plates and some Hardened Chitin: " + overworld);
        helper.assertTrue(!overworld.containsKey(ModItems.INFERNAL_CHITIN.get()) && !overworld.containsKey(ModItems.VOID_CHITIN.get()),
                "No Infernal or Void Chitin from Overworld ants: " + overworld);
        helper.assertTrue(nether.getOrDefault(ModItems.INFERNAL_CHITIN.get(), 0) > 100 && !nether.containsKey(ModItems.VOID_CHITIN.get()),
                "Nether ants leave Infernal Chitin: " + nether);
        helper.assertTrue(end.getOrDefault(ModItems.VOID_CHITIN.get(), 0) > 80 && !end.containsKey(ModItems.INFERNAL_CHITIN.get()),
                "End ants leave Void Chitin: " + end);
        // A grown foraging colony brings chitin home (every find brings some here, to see it quickly).
        double chance = AntConfig.get(AntConfig.CHITIN_FIND_CHANCE);
        int plates = 0;
        int hardened = 0;
        try {
            AntConfig.CHITIN_FIND_CHANCE.set(1.0);
            TerrariumBlockEntity stone = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), AntColonies.id("stone"), 2000, 0, 20000);
            AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 3), StructureKind.FORAGING_SITE);
            StructureBlockEntity storage = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 1), StructureKind.STORAGE);
            for (int i = 0; i < 5; i++) {
                stone.colony().setFood(20000);
                AntColoniesGameTests.step(helper, stone, 1);
            }
            for (int slot = 0; slot < storage.getContainerSize(); slot++) {
                ItemStack stack = storage.getItem(slot);
                if (stack.is(ModItems.CHITIN_PLATE.get())) {
                    plates += stack.getCount();
                } else if (stack.is(ModItems.HARDENED_CHITIN.get())) {
                    hardened += stack.getCount();
                }
                helper.assertTrue(!stack.is(ModItems.INFERNAL_CHITIN.get()) && !stack.is(ModItems.VOID_CHITIN.get()),
                        "An Overworld colony finds no Infernal or Void Chitin");
            }
        } finally {
            AntConfig.CHITIN_FIND_CHANCE.set(chance);
        }
        helper.assertTrue(plates > 0 && hardened > 0, "A grown colony should bring plates and Hardened Chitin home: " + plates + " / " + hardened);
        helper.succeed();
    }

    // --- The Queen grows, and the food slot ---

    /**
     * A Queen grows with her colony in her own time: a stage bigger than hers counts towards growing into the next, which
     * takes the configured minutes (10) for stage 2, twice that for 3 and three times for 4; then two different traits go
     * up by one, never past level 7. One stage at a time, so a new Queen in a big colony doesn't catch up at once. Time
     * counted is kept (and saved) while the colony is smaller again. A Queen saved before Queens grew takes her colony's
     * stage without growing. In a colony of 400 workers (stage 2) a new Queen grows after ten minutes, not before.
     */
    private static void queenGrowsWithColony(GameTestHelper helper) {
        double step = QueenData.minutesFor(2);
        helper.assertTrue(Math.abs(QueenData.minutesFor(3) - 2 * step) < 1e-9 && Math.abs(QueenData.minutesFor(4) - 3 * step) < 1e-9,
                "Each next stage should take longer: " + step + " / " + QueenData.minutesFor(3) + " / " + QueenData.minutesFor(4));
        QueenData young = new QueenData(COMMON, Genome.basic(COMMON));
        helper.assertTrue(young.grown() == 1, "A new Queen has grown with no colony yet");
        // In a stage-4 colony: not at once, one stage at a time.
        List<Trait> raised = new java.util.ArrayList<>();
        QueenData q = young.growTo(4, step * 0.5, RandomSource.create(4), raised);
        helper.assertTrue(q.grown() == 1 && raised.isEmpty() && Math.abs(q.growth() - step * 0.5) < 1e-4, "Half the time: no growth yet, " + q);
        q = q.growTo(4, step * 0.6, RandomSource.create(4), raised);
        helper.assertTrue(q.grown() == 2 && raised.size() == 2 && raised.get(0) != raised.get(1) && q.growth() == 0,
                "After the time for stage 2: stage 2 only, two different traits, " + q + " " + raised);
        int gained = q.genome().levels().stream().mapToInt(Integer::intValue).sum() - young.genome().levels().stream().mapToInt(Integer::intValue).sum();
        helper.assertTrue(gained == 2, "Two traits +1: " + gained);
        // A smaller colony: her time towards stage 3 is kept, not counted on.
        q = q.growTo(3, step, RandomSource.create(5), raised);
        QueenData paused = q.growTo(2, step, RandomSource.create(5), raised);
        helper.assertTrue(paused == q && q.grown() == 2 && Math.abs(q.growth() - step) < 1e-4, "Time is kept while the colony is smaller: " + paused);
        var tag = QueenData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, q).getOrThrow();
        helper.assertTrue(QueenData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().equals(q), "Her growth and its time should be saved");
        q = q.growTo(3, step * 1.01, RandomSource.create(5), raised);
        helper.assertTrue(q.grown() == 3 && raised.size() == 4, "Twice the time in all: stage 3, " + q);
        // Never past level 7.
        Genome high = Genome.basic(COMMON);
        for (Trait trait : Trait.values()) {
            high = high.withLevel(trait, 7);
        }
        QueenData capped = new QueenData(COMMON, high).growTo(2, step * 2, RandomSource.create(6), new java.util.ArrayList<>());
        helper.assertTrue(capped.genome().levels().stream().allMatch(level -> level == 7) && capped.grown() == 2, "Growth stops at level 7");
        // Saved before Queens grew: she takes the stage, nothing more.
        CompoundTag old = new CompoundTag();
        old.putString("species", COMMON.toString());
        old.put("genome", Genome.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, young.genome()).getOrThrow());
        QueenData saved = QueenData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(saved.grown() == 0, "A Queen saved without growth reads as not yet grown: " + saved.grown());
        QueenData adopted = saved.growTo(4, step * 10, RandomSource.create(7), raised = new java.util.ArrayList<>());
        helper.assertTrue(adopted.grown() == 4 && raised.isEmpty() && adopted.genome().equals(saved.genome()), "An old Queen takes the stage without growing");
        // In a colony: 400 workers make stage 2; the new Queen waits ten minutes, then grows.
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 400, 0, 20000);
        terrarium.colony().setQueen(young);
        AntColoniesGameTests.step(helper, terrarium, 0.05);
        helper.assertTrue(terrarium.colony().stage() >= 2 && terrarium.colony().queen().grown() == 1, "Not at once: " + terrarium.colony().queen());
        for (int i = 0; i < 12 && terrarium.colony().queen().grown() == 1; i++) {
            terrarium.colony().setFood(20000);
            AntColoniesGameTests.step(helper, terrarium, 1);
        }
        QueenData grown = terrarium.colony().queen();
        helper.assertTrue(grown.grown() == 2, "After about ten minutes at stage 2 she should have grown: " + grown);
        helper.succeed();
    }

    /**
     * The food slot takes only food and the feed slot no plain food (from players and hoppers alike); food there is eaten
     * as the food bar has room and waits quietly when it is full (no "full" warning); Test Tubes in the feed slot still
     * fill with ants while the food slot is full.
     */
    private static void foodSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 300, 0, 0);
        ItemStack food = new ItemStack(ModItems.ANT_FOOD.get(), 64);
        ItemStack tube = new ItemStack(ModItems.TEST_TUBE.get());
        helper.assertTrue(terrarium.canPlaceItem(TerrariumBlockEntity.SLOT_FOOD, food) && !terrarium.canPlaceItem(TerrariumBlockEntity.SLOT_FOOD, tube),
                "The food slot takes food, not tubes");
        helper.assertTrue(!terrarium.canPlaceItem(TerrariumBlockEntity.SLOT_FEED, food) && terrarium.canPlaceItem(TerrariumBlockEntity.SLOT_FEED, tube),
                "The feed slot takes tubes, not plain food");
        helper.assertTrue(terrarium.canPlaceItemThroughFace(TerrariumBlockEntity.SLOT_FOOD, food, net.minecraft.core.Direction.UP)
                && !terrarium.canPlaceItemThroughFace(TerrariumBlockEntity.SLOT_FEED, food, net.minecraft.core.Direction.UP), "Hoppers put food in the food slot");
        // Eaten while there is room; then it waits, without a warning.
        terrarium.setItem(TerrariumBlockEntity.SLOT_FOOD, food);
        terrarium.processSlots(level);
        double eaten = terrarium.colony().food();
        helper.assertTrue(eaten > 0, "Food in the food slot should be eaten");
        helper.assertTrue(!terrarium.getItem(TerrariumBlockEntity.SLOT_FOOD).isEmpty(), "With the bar full, the rest waits in the slot");
        helper.assertTrue(!ColonyStatus.STORAGE_FULL.in(terrarium.feedStatus()), "No 'full' warning for food waiting in its slot");
        // A tube in the feed slot fills with ants all the same.
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, tube);
        terrarium.processSlots(level);
        helper.assertTrue(terrarium.getItem(TerrariumBlockEntity.SLOT_OUTPUT).is(ModItems.ANT_TEST_TUBE.get()), "A tube should fill with ants while food waits");
        helper.succeed();
    }

    // --- Colony Connectors ---

    private static StructureBlockEntity connector(GameTestHelper helper, BlockPos pos, int foodMode, int reserve, int target, int itemMode) {
        StructureBlockEntity connector = AntColoniesGameTests.structure(helper, pos, StructureKind.COLONY_CONNECTOR);
        ConnectorSettings settings = connector.connector();
        settings.channel = 5;
        settings.foodMode = foodMode;
        settings.reserve = reserve;
        settings.target = target;
        settings.rateLimit = 3;
        settings.itemMode = itemMode;
        connector.connectorChanged();
        return connector;
    }

    /**
     * Food moves from a supplying colony (only what it has above its reserve) to the hungriest requesting colony, until the
     * target; the total is never more or less than before; a redstone signal stops a connector; the network remembers its
     * connectors.
     */
    private static void connectorsShareFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TerrariumBlockEntity farm = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), AntColonies.id("farmer"), 100, 0, 2000);
        TerrariumBlockEntity mine = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 2), AntColonies.id("stone"), 100, 0, 100);
        TerrariumBlockEntity mine2 = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 10), AntColonies.id("stone"), 100, 0, 300);
        connector(helper, new BlockPos(2, 1, 3), ConnectorSettings.SUPPLY, 50, 0, 0);
        connector(helper, new BlockPos(10, 1, 3), ConnectorSettings.REQUEST, 0, 75, 0);
        StructureBlockEntity third = connector(helper, new BlockPos(2, 1, 11), ConnectorSettings.REQUEST, 0, 75, 0);
        for (TerrariumBlockEntity t : List.of(farm, mine, mine2)) {
            AntColoniesGameTests.step(helper, t, 0.05);
        }
        for (StructureBlockEntity c : List.of(third)) {
            helper.assertTrue(c.terrarium() != null, "The connectors should join their colonies");
        }
        double capacity = farm.colony().stats().foodCapacity();
        farm.colony().setFood(capacity);
        mine.colony().setFood(capacity * 0.05);
        mine2.colony().setFood(capacity * 0.4);
        double before = farm.colony().food() + mine.colony().food() + mine2.colony().food();
        ConnectorNetwork.tick(level);
        double after = farm.colony().food() + mine.colony().food() + mine2.colony().food();
        helper.assertTrue(Math.abs(before - after) < 0.001, "Food is moved, never made or lost: " + before + " -> " + after);
        helper.assertTrue(mine.colony().food() > capacity * 0.05, "The hungriest colony should get food first: " + mine.colony().food());
        for (int i = 0; i < 400; i++) {
            ConnectorNetwork.tick(level);
        }
        after = farm.colony().food() + mine.colony().food() + mine2.colony().food();
        helper.assertTrue(Math.abs(before - after) < 0.01, "Still no food made or lost: " + before + " -> " + after);
        helper.assertTrue(farm.colony().food() >= capacity * 0.5 - 0.01, "The supplier keeps its reserve: " + farm.colony().food());
        helper.assertTrue(mine.colony().food() <= capacity * 0.75 + 0.01, "A receiver stops at its target: " + mine.colony().food());
        // A redstone signal stops the supplier.
        mine.colony().setFood(0);
        farm.colony().setFood(capacity);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.REDSTONE_BLOCK);
        double farmFood = farm.colony().food();
        ConnectorNetwork.tick(level);
        helper.assertTrue(farm.colony().food() == farmFood, "A powered connector gives nothing");
        helper.assertTrue(ConnectorNetwork.registry(level).channels().containsKey(third.getBlockPos()), "The network remembers its connectors");
        helper.succeed();
    }

    /**
     * Connectors link only within the range (blocks apart on x and on z): with the range shorter than the colonies' distance
     * no food moves and neither colony counts the other; with the default 30 it does.
     */
    private static void connectorsRespectRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TerrariumBlockEntity farm = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), AntColonies.id("farmer"), 100, 0, 2000);
        TerrariumBlockEntity mine = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 2), AntColonies.id("stone"), 100, 0, 100);
        connector(helper, new BlockPos(2, 1, 3), ConnectorSettings.SUPPLY, 50, 0, 0);
        connector(helper, new BlockPos(10, 1, 3), ConnectorSettings.REQUEST, 0, 75, 0);
        for (TerrariumBlockEntity t : List.of(farm, mine)) {
            AntColoniesGameTests.step(helper, t, 0.05);
        }
        double capacity = farm.colony().stats().foodCapacity();
        int range = AntConfig.get(AntConfig.CONNECTOR_RANGE);
        helper.assertTrue(range == 30, "The default connector range should be 30, not " + range);
        try {
            // 8 blocks apart, a range of 5: not linked.
            AntConfig.CONNECTOR_RANGE.set(5);
            farm.colony().setFood(capacity);
            mine.colony().setFood(capacity * 0.05);
            for (int i = 0; i < 20; i++) {
                ConnectorNetwork.tick(level);
            }
            helper.assertTrue(mine.colony().food() == capacity * 0.05, "No food beyond the range: " + mine.colony().food());
            helper.assertTrue(mine.colony().network().stream().noneMatch(link -> !link.self()),
                    "A colony out of range is not on the network: " + mine.colony().network());
        } finally {
            AntConfig.CONNECTOR_RANGE.set(range);
        }
        ConnectorNetwork.tick(level);
        helper.assertTrue(mine.colony().food() > capacity * 0.05, "Within the range food moves: " + mine.colony().food());
        helper.assertTrue(mine.colony().network().stream().anyMatch(link -> !link.self()), "Within the range the colonies are linked");
        helper.succeed();
    }

    /** Items named by a sending connector's filter move to the receiving colony's Storage; counts conserved; nothing when full. */
    private static void connectorsMoveItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TerrariumBlockEntity a = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 10, 0, 500);
        TerrariumBlockEntity b = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 2), COMMON, 10, 0, 500);
        StructureBlockEntity storageA = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 1), StructureKind.STORAGE);
        StructureBlockEntity storageB = AntColoniesGameTests.structure(helper, new BlockPos(10, 1, 1), StructureKind.STORAGE);
        StructureBlockEntity sender = connector(helper, new BlockPos(2, 1, 3), 0, 0, 0, ConnectorSettings.SEND);
        connector(helper, new BlockPos(10, 1, 3), 0, 0, 0, ConnectorSettings.RECEIVE);
        AntColoniesGameTests.step(helper, a, 0.05);
        AntColoniesGameTests.step(helper, b, 0.05);
        sender.setItem(0, new ItemStack(Items.COBBLESTONE));
        storageA.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        storageA.setItem(1, new ItemStack(Items.DIRT, 64));
        for (int i = 0; i < 60; i++) {
            ConnectorNetwork.tick(level);
        }
        int cobbleA = countOf(storageA, Items.COBBLESTONE);
        int cobbleB = countOf(storageB, Items.COBBLESTONE);
        helper.assertTrue(cobbleA + cobbleB == 64, "Cobblestone conserved: " + cobbleA + " + " + cobbleB);
        helper.assertTrue(cobbleB > 0, "Cobblestone should arrive");
        helper.assertTrue(countOf(storageA, Items.DIRT) == 64 && countOf(storageB, Items.DIRT) == 0, "Dirt isn't in the filter: it stays");
        // A full receiver: nothing moves, nothing is lost.
        for (int slot = 0; slot < storageB.unlockedSlots(); slot++) {
            storageB.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        storageA.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        int total = countOf(storageA, Items.COBBLESTONE) + countOf(storageB, Items.COBBLESTONE);
        for (int i = 0; i < 20; i++) {
            ConnectorNetwork.tick(level);
        }
        helper.assertTrue(countOf(storageA, Items.COBBLESTONE) + countOf(storageB, Items.COBBLESTONE) == total, "Nothing lost into a full Storage");
        helper.succeed();
    }

    private static int countOf(StructureBlockEntity store, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < store.getContainerSize(); slot++) {
            if (store.getItem(slot).is(item)) {
                total += store.getItem(slot).getCount();
            }
        }
        return total;
    }

    // --- Data ---

    /** Every block of the mod has a loot table that drops something (a double slab two), and an item. */
    private static void decorBlocksDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(AntColonies.MODID) || block.getLootTable().isEmpty()) {
                continue;
            }
            var table = level.getServer().reloadableRegistries().getLootTable(block.getLootTable().get());
            helper.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "No loot table for " + id);
            checked++;
        }
        helper.assertTrue(checked > 60, "Checked " + checked + " blocks");
        for (var block : ModDecor.ALL.values()) {
            helper.assertTrue(block.get().asItem() != Items.AIR, "No item for " + block.getId());
        }
        // A double slab drops two.
        BlockPos pos = new BlockPos(2, 1, 2);
        Block slab = BuiltInRegistries.BLOCK.getValue(AntColonies.id("anthill_brick_slab"));
        helper.setBlock(pos, slab.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        var drops = Block.getDrops(helper.getBlockState(pos), level, helper.absolutePos(pos), null);
        helper.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum() == 2, "A double slab drops two: " + drops);
        // The expedition sites' recipes need their dimension's trail pheromone.
        var recipes = level.getServer().getRecipeManager();
        for (String[] r : new String[][] {{"nether_foraging_site", "nether_trail_pheromone"}, {"end_foraging_site", "end_trail_pheromone"}}) {
            var holder = recipes.byKey(ResourceKey.create(Registries.RECIPE, AntColonies.id(r[0])));
            helper.assertTrue(holder.isPresent(), "No recipe " + r[0]);
            var ingredients = holder.get().value().placementInfo().ingredients();
            Identifier needed = AntColonies.id(r[1]);
            helper.assertTrue(ingredients.stream().anyMatch(i -> i.items().anyMatch(item -> item.is(ResourceKey.create(Registries.ITEM, needed)))),
                    r[0] + " should need " + r[1]);
        }
        helper.succeed();
    }

    /** Job sites of higher priority get their ants first; a site turned off gets none and says so. */
    private static void jobPriorityAndSwitch(GameTestHelper helper) {
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, new BlockPos(2, 1, 2), COMMON, 120, 0, 20000);
        StructureBlockEntity first = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 1), StructureKind.FORAGING_SITE);
        StructureBlockEntity second = AntColoniesGameTests.structure(helper, new BlockPos(2, 1, 3), StructureKind.FORAGING_SITE);
        second.setPriority(StructureBlockEntity.PRIORITY_HIGH);
        AntColoniesGameTests.step(helper, terrarium, 0.1);
        helper.assertTrue(second.assigned() > 0 && second.assigned() >= first.assigned(), "The high-priority site fills first: " + second.assigned()
                + " vs " + first.assigned());
        second.setEnabled(false);
        AntColoniesGameTests.step(helper, terrarium, 0.1);
        helper.assertTrue(second.assigned() == 0 && ColonyStatus.DISABLED.in(second.siteStatus()) && first.assigned() > 0,
                "A site turned off takes no ants; they go to the other");
        helper.succeed();
    }
}
