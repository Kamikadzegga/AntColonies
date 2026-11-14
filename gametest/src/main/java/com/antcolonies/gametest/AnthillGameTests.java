package com.antcolonies.gametest;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.content.worldgen.AnthillAlarm;
import com.antcolonies.content.worldgen.AnthillLayout;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.content.worldgen.AnthillTheme;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Game tests of the anthill update (0.2.0): grown nests (every room reachable, the Queen's chamber only through her
 * antechamber, all three sizes), biome themes, a themed anthill built for real (spires, trails, the great Queen and her
 * royal guard, every ant knowing its anthill), and the alarm (Chitin keeps the ants peaceful until a chest is raided, the
 * Queen's chamber entered or an ant struck; alarms are per anthill and per player, saved, and run out).
 */
final class AnthillGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, AntColonies.MODID);
    private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");

    private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> ALL = List.of(
            test("anthill_grown_layouts", AnthillGameTests::grownLayouts),
            test("anthill_themes_by_biome", AnthillGameTests::themesByBiome),
            test("anthill_themed_build", AnthillGameTests::themedBuild),
            test("anthill_alarm", AnthillGameTests::alarm),
            test("anthill_ants_rest_unwatched", AnthillGameTests::restUnwatched),
            test("anthill_royal_guard_ants", AnthillGameTests::royalGuardAnts),
            test("anthill_brood_clusters", AnthillGameTests::broodClusters));

    private AnthillGameTests() {}

    static void register(IEventBus bus) {
        TESTS.register(bus);
    }

    static void registerTests(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : ALL) {
            // A grown anthill reaches some 30 blocks round its center: these tests get wide padding of their own.
            event.registerTest(test.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, test.getId()),
                    new TestData<>(environment, EMPTY, 400, 0, true, net.minecraft.world.level.block.Rotation.NONE,
                            false, 1, 1, false, 64)));
        }
    }

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test(String name, Consumer<GameTestHelper> test) {
        return TESTS.register(name, () -> test);
    }

    // --- Layouts ---

    /** The dug-out cells reachable from the top of the entrance shaft, never through cells {@code blocked} says no to. */
    private static Set<BlockPos> reachable(AnthillLayout layout, java.util.function.Predicate<BlockPos> blocked) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = BlockPos.ZERO;
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 400_000) {
            BlockPos pos = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (next.getY() <= 6 && !seen.contains(next) && layout.carved(next.getX(), next.getY(), next.getZ()) && !blocked.test(next)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * Grown nests over many seeds: one Queen's chamber (a great one), every room reachable from the entrance, rooms
     * within the size's reach, the Queen reachable only through her antechamber (when she has one, as nearly all do),
     * and all three sizes turning up with as many rooms as they should.
     */
    private static void grownLayouts(GameTestHelper helper) {
        Map<AnthillLayout.Size, Integer> sizes = new EnumMap<>(AnthillLayout.Size.class);
        int withAntechamber = 0;
        int checked = 120;
        for (long seed = 1; seed <= checked; seed++) {
            AnthillLayout layout = AnthillLayout.create(AnthillLayout.Style.GROWN, seed * 104729);
            String name = "grown/" + seed + " (" + layout.size() + ")";
            sizes.merge(layout.size(), 1, Integer::sum);
            long queens = layout.rooms().stream().filter(r -> r.type() == AnthillLayout.RoomType.QUEEN).count();
            helper.assertTrue(queens == 1, name + ": " + queens + " Queen's chambers");
            AnthillLayout.Room queen = layout.queen();
            helper.assertTrue(layout.grand() && queen.rx() > 6, name + ": the Queen's chamber should be a great one");
            helper.assertTrue(layout.rooms().size() >= layout.size().minRooms - 1, name + ": only " + layout.rooms().size() + " rooms");
            for (AnthillLayout.Room room : layout.rooms()) {
                helper.assertTrue(Math.abs(room.x()) <= layout.size().reach + 0.01 && Math.abs(room.z()) <= layout.size().reach + 0.01
                        && room.y() + room.ry() <= -2, name + ": room out of bounds " + room);
            }
            Set<BlockPos> seen = reachable(layout, pos -> false);
            for (AnthillLayout.Room room : layout.rooms()) {
                helper.assertTrue(seen.contains(BlockPos.containing(room.x(), room.y(), room.z())), name + ": " + room.type() + " can't be reached");
            }
            AnthillLayout.Room ante = layout.rooms().stream().filter(r -> r.type() == AnthillLayout.RoomType.ANTECHAMBER).findFirst().orElse(null);
            if (ante != null) {
                withAntechamber++;
                Set<BlockPos> around = reachable(layout, pos -> layout.roomAt(pos.getX(), pos.getY(), pos.getZ()) == ante);
                helper.assertFalse(around.contains(BlockPos.containing(queen.x(), queen.y(), queen.z())),
                        name + ": the Queen's chamber can be reached without passing her antechamber");
            }
        }
        for (AnthillLayout.Size size : AnthillLayout.Size.values()) {
            helper.assertTrue(sizes.getOrDefault(size, 0) > 5, "Too few " + size + " nests: " + sizes);
        }
        helper.assertTrue(withAntechamber >= checked * 9 / 10, "Most Queens should have an antechamber: " + withAntechamber + " of " + checked);
        helper.succeed();
    }

    /** Overworld anthills take their look from the biome. */
    private static void themesByBiome(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, AnthillTheme> expected = Map.of(
                Biomes.PLAINS, AnthillTheme.TEMPERATE, Biomes.DESERT, AnthillTheme.DESERT, Biomes.BADLANDS, AnthillTheme.BADLANDS,
                Biomes.SAVANNA, AnthillTheme.SAVANNA, Biomes.JUNGLE, AnthillTheme.JUNGLE, Biomes.MANGROVE_SWAMP, AnthillTheme.SWAMP,
                Biomes.SNOWY_PLAINS, AnthillTheme.SNOWY, Biomes.TAIGA, AnthillTheme.TAIGA, Biomes.MUSHROOM_FIELDS, AnthillTheme.MUSHROOM,
                Biomes.CHERRY_GROVE, AnthillTheme.CHERRY);
        expected.forEach((biome, theme) -> helper.assertTrue(AnthillTheme.of(biomes.getOrThrow(biome)) == theme,
                biome.identifier() + " should be " + theme + ", is " + AnthillTheme.of(biomes.getOrThrow(biome))));
        helper.assertTrue(AnthillTheme.of(biomes.getOrThrow(Biomes.SNOWY_TAIGA)) == AnthillTheme.SNOWY, "A snowy taiga is snowy before it is a taiga");
        helper.assertTrue(AnthillTheme.of(biomes.getOrThrow(Biomes.SWAMP)) == AnthillTheme.SWAMP, "Swamps are swamps");
        // Every theme looks its own: no two share their mound's or walls' blocks entirely.
        Set<Set<Block>> looks = new HashSet<>();
        for (AnthillTheme theme : AnthillTheme.values()) {
            Set<Block> blocks = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                double roll = i / 100.0;
                blocks.add(theme.mound(roll, 0, 64).getBlock());
                blocks.add(theme.mound(roll, 2, 64).getBlock());
                blocks.add(theme.wall(roll, false, null, 64).getBlock());
            }
            helper.assertTrue(looks.add(blocks), theme + " looks like another theme");
            helper.assertFalse(blocks.contains(Blocks.AIR), theme + " builds air");
        }
        helper.succeed();
    }

    /** A grown layout of a size whose plan stays within {@code reach} of its center and {@code depth} below it. */
    private static long seedFor(AnthillLayout.Size size, int reach, int depth) {
        for (long seed = 1; seed < 10_000; seed++) {
            AnthillLayout layout = AnthillLayout.create(AnthillLayout.Style.GROWN, seed);
            if (layout.size() == size && layout.minX() >= -reach && layout.maxX() <= reach && layout.minZ() >= -reach && layout.maxZ() <= reach
                    && layout.minY() >= -depth && layout.rooms().stream().anyMatch(r -> r.type() == AnthillLayout.RoomType.ANTECHAMBER)) {
                return seed;
            }
        }
        throw new IllegalStateException("No " + size + " layout within " + reach + " blocks");
    }

    /**
     * A grown savanna anthill built into a block of stone under grass: termite spires over the mound, trails worn into
     * the grass, termite-clay walls, its loot; exactly one great Queen with her chamber, her royal guard round her and in
     * the antechamber, soldiers and workers about the nest, every one of them knowing its anthill; raising the alarm
     * reaches them all.
     */
    private static void themedBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = seedFor(AnthillLayout.Size.MEDIUM, 26, 40);
        BlockPos center = helper.absolutePos(new BlockPos(30, 44, 30));
        AnthillPiece piece = new AnthillPiece(center, AnthillPiece.Kind.OVERWORLD, AnthillLayout.Style.GROWN, seed, AnthillTheme.SAVANNA);
        BoundingBox box = piece.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), center.getY(), box.maxZ())) {
            level.setBlock(pos, pos.getY() == center.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), center.getY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box,
                net.minecraft.world.level.ChunkPos.containing(center), center);
        int spire = 0;
        int trail = 0;
        int clay = 0;
        int containers = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            int ry = pos.getY() - center.getY();
            if (ry >= 7 && !state.isAir()) {
                spire++;
            }
            if (ry == 0 && state.is(Blocks.COARSE_DIRT) && piece.moundHeight(pos.getX() - center.getX(), pos.getZ() - center.getZ()) == 0) {
                trail++;
            }
            if (ry < 0 && (state.is(Blocks.PACKED_MUD) || state.is(Blocks.MUD_BRICKS))) {
                clay++;
            }
            if (level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null
                    && AnthillPiece.CONTAINER_LOOT.contains(container.getLootTable())) {
                containers++;
            }
        }
        helper.assertTrue(spire > 6, "Termite spires should rise over the mound: " + spire + " blocks 7+ up");
        helper.assertTrue(trail > 6, "Trails should be worn into the grass round the mound: " + trail);
        helper.assertTrue(clay > 50, "Termite-clay walls: " + clay);
        helper.assertTrue(containers >= 2, "The Queen's barrel and the food store at least: " + containers);
        helper.runAfterDelay(20, () -> {
            List<WildAnt> ants = level.getEntitiesOfClass(WildAnt.class, AABB.of(box).inflate(2));
            List<WildAnt> queens = ants.stream().filter(a -> a.caste() == Caste.QUEEN).toList();
            helper.assertTrue(queens.size() == 1, "Exactly one Queen: " + queens.size() + " of " + ants.size());
            WildAnt queen = queens.getFirst();
            helper.assertTrue(queen.rank() == WildAnt.Rank.GREAT_QUEEN && queen.renderScale() == 2.0F && queen.isAnthillQueen(),
                    "She should be a great Queen: " + queen.rank());
            helper.assertTrue(queen.getBbWidth() > 1.2F, "A great Queen is big: " + queen.getBbWidth());
            BlockPos anthill = queen.anthill();
            helper.assertTrue(anthill != null && queen.chamber() != null && queen.chamber().contains(queen.position()),
                    "She knows her anthill and stands in her chamber");
            long royal = ants.stream().filter(a -> a.rank() == WildAnt.Rank.ROYAL && a.caste() == Caste.SOLDIER).count();
            helper.assertTrue(royal >= 8, "Six royal guards round her and four in the antechamber: " + royal);
            helper.assertTrue(ants.size() >= 18, "A medium nest should be busy: " + ants.size() + " ants");
            helper.assertTrue(ants.stream().allMatch(a -> a.guardian() && anthill.equals(a.anthill()) && a.isPersistenceRequired()),
                    "Every ant belongs to the anthill");
            long guarding = ants.stream().filter(a -> queen.chamber().contains(a.position()) && a.rank() == WildAnt.Rank.ROYAL).count();
            helper.assertTrue(guarding >= 5, "The royal guard stands in her chamber: " + guarding);
            // The alarm reaches every ant of the anthill.
            Player intruder = helper.makeMockServerPlayerInLevel();
            int alarmed = AnthillAlarm.raise(level, anthill, intruder, AnthillAlarm.Reason.QUEEN);
            helper.assertTrue(alarmed == ants.size() && ants.stream().allMatch(a -> a.alarmedAt(intruder)), "Alarmed " + alarmed + " of " + ants.size());
            ants.forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    /**
     * An anthill's ants (persistent) stop idle strolling while no player is within 32 blocks, as in 26.3: 26.1.2 would
     * keep them walking round their tunnels with nobody there. A player near wakes them again.
     */
    private static void restUnwatched(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WildAnt ant = ant(helper, new BlockPos(8, 1, 8), Caste.WORKER, WildAnt.Rank.NORMAL, new BlockPos(8, 1, 8));
        Player nearest = level.getNearestPlayer(ant, -1.0);
        if (nearest == null || nearest.distanceToSqr(ant) >= 32 * 32) {
            ant.setNoActionTime(150);
            ant.checkDespawn();
            helper.assertTrue(ant.getNoActionTime() == 150, "An unwatched anthill ant's idle time was reset to " + ant.getNoActionTime());
        }
        Player player = helper.makeMockServerPlayerInLevel();
        player.setPos(ant.position().add(3, 0, 0));
        ant.setNoActionTime(150);
        ant.checkDespawn();
        helper.assertTrue(ant.getNoActionTime() == 0, "A watched anthill ant stays idle: " + ant.getNoActionTime());
        ant.discard();
        player.discard();
        helper.succeed();
    }

    /**
     * Royal Guard Ants, the great Queen's guard: named so (other soldiers stay Wild Ants), they take on only a player at their
     * post (not one merely in sight), let go of anyone more than 10 blocks from it, and walk back to it and stand there.
     */
    private static void royalGuardAnts(GameTestHelper helper) {
        // A stone floor to stand on (the test area has none out here).
        for (int x = 12; x <= 34; x++) {
            for (int z = 12; z <= 34; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos postAt = new BlockPos(20, 1, 20);
        WildAnt soldier = ant(helper, postAt, Caste.SOLDIER, WildAnt.Rank.ROYAL, new BlockPos(20, 1, 24));
        WildAnt tunnel = ant(helper, new BlockPos(30, 1, 30), Caste.SOLDIER, WildAnt.Rank.NORMAL, new BlockPos(20, 1, 24));
        helper.assertTrue(soldier.royalGuard() && !tunnel.royalGuard(), "Only the royal soldier is a Royal Guard Ant");
        helper.assertValueEqual(soldier.getName().getString(), "Royal Guard Ant", "royal soldier's name");
        helper.assertValueEqual(tunnel.getName().getString(), "Wild Ant", "tunnel soldier's name");
        BlockPos post = soldier.post();
        helper.assertTrue(post.equals(helper.absolutePos(postAt)), "Its post is where it was placed: " + post);
        Player player = helper.makeMockServerPlayerInLevel();
        net.minecraft.world.phys.Vec3 postCenter = net.minecraft.world.phys.Vec3.atBottomCenterOf(post);
        player.setPos(postCenter.add(3, 0, 0));
        helper.assertTrue(soldier.atPost(player), "A player 3 blocks from its post is at its post");
        player.setPos(postCenter.add(7, 0, 0));
        helper.assertTrue(!soldier.atPost(player) && soldier.withinLeash(player), "7 blocks away: in sight, not at its post");
        player.setPos(postCenter.add(12, 0, 0));
        helper.assertTrue(!soldier.withinLeash(player), "12 blocks away: out of its ground");
        // Chasing someone that far, it lets them go.
        soldier.setTarget(player);
        // And taken 6 blocks from its post, it walks back and stands there.
        soldier.teleportTo(postCenter.x + 6, postCenter.y, postCenter.z);
        helper.runAfterDelay(5, () -> helper.assertTrue(soldier.getTarget() == null, "It should let go of a player 12 blocks from its post"));
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.getTarget() == null, "still chasing at " + soldier.position() + " tick " + soldier.tickCount);
            helper.assertTrue(soldier.distanceToSqr(postCenter) < 1.5, "Back at its post? " + Math.sqrt(soldier.distanceToSqr(postCenter)) + " blocks off, at " + soldier.position() + " post " + postCenter + " tick " + soldier.tickCount + " nav " + soldier.getNavigation().isDone() + " ground " + soldier.onGround());
            player.discard();
        });
    }

    /**
     * Ant Egg and Larva Clusters: not craftable; an anthill's own (natural) egg cluster becomes a larva cluster and that 1
     * to 3 ants of its species and anthill; broken, a natural one drops nothing without Silk Touch and itself (with the
     * species) with it; a placed one never hatches and always drops itself; the item goes only into a working colony of
     * its species (not a founding one, not another species).
     */
    private static void broodClusters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 14; x++) {
            for (int z = 2; z <= 14; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        var recipes = level.getServer().getRecipeManager();
        for (String id : new String[] {"ant_egg_cluster", "larva_cluster"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, AntColonies.id(id))).isEmpty(), id + " should not be craftable");
        }
        Identifier common = AntColonies.id("common");
        BlockPos anthill = helper.absolutePos(new BlockPos(12, 1, 12));
        // A natural egg cluster: eggs, then larvae, then ants.
        BlockPos at = new BlockPos(4, 1, 4);
        cluster(helper, at, true, true, common, anthill);
        var block = (com.antcolonies.content.block.BroodClusterBlock) com.antcolonies.core.registry.ModDecor.ANT_EGG_CLUSTER.get();
        helper.assertTrue(helper.getBlockState(at).isRandomlyTicking(), "A natural cluster ticks");
        helper.assertTrue(block.grow(helper.getBlockState(at), level, helper.absolutePos(at), level.getRandom()), "The eggs should grow");
        helper.assertBlockPresent(com.antcolonies.core.registry.ModDecor.LARVA_CLUSTER.get(), at);
        var larvae = helper.getBlockEntity(at, com.antcolonies.content.blockentity.BroodClusterBlockEntity.class);
        helper.assertTrue(common.equals(larvae.species()) && anthill.equals(larvae.anthill()), "The larvae keep the species and anthill");
        var larvaBlock = (com.antcolonies.content.block.BroodClusterBlock) com.antcolonies.core.registry.ModDecor.LARVA_CLUSTER.get();
        helper.assertTrue(larvaBlock.grow(helper.getBlockState(at), level, helper.absolutePos(at), level.getRandom()), "The larvae should hatch");
        helper.assertBlockNotPresent(com.antcolonies.core.registry.ModDecor.LARVA_CLUSTER.get(), at);
        List<WildAnt> hatched = level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(helper.absolutePos(at)).inflate(2));
        helper.assertTrue(hatched.size() >= 1 && hatched.size() <= 3, "1 to 3 ants should hatch, not " + hatched.size());
        for (WildAnt ant : hatched) {
            helper.assertTrue(ant.species().equals(common) && anthill.equals(ant.anthill()) && ant.guardian() && ant.caste() == Caste.WORKER,
                    "A hatched ant is a worker of the anthill's species and of the anthill");
            ant.discard();
        }
        // A placed (decorative) cluster never hatches.
        BlockPos placed = new BlockPos(8, 1, 4);
        cluster(helper, placed, false, false, common, null);
        helper.assertTrue(!helper.getBlockState(placed).isRandomlyTicking(), "A placed cluster doesn't tick");
        helper.assertTrue(!larvaBlock.grow(helper.getBlockState(placed), level, helper.absolutePos(placed), level.getRandom()), "A placed cluster never hatches");
        // Drops.
        ItemStack hand = ItemStack.EMPTY;
        ItemStack silk = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        BlockPos natural = new BlockPos(4, 1, 8);
        cluster(helper, natural, true, true, common, anthill);
        helper.assertTrue(drops(helper, natural, hand).isEmpty(), "A natural cluster without Silk Touch drops nothing");
        List<ItemStack> taken = drops(helper, natural, silk);
        helper.assertTrue(taken.size() == 1 && taken.get(0).is(com.antcolonies.core.registry.ModDecor.ANT_EGG_CLUSTER.get().asItem())
                && common.equals(taken.get(0).get(com.antcolonies.core.registry.ModDataComponents.SPECIES.get())), "Silk Touch takes it with its species: " + taken);
        helper.assertTrue(drops(helper, placed, hand).size() == 1, "A placed cluster drops itself");
        // Into a Terrarium: a founding colony and another species' colony don't take it; a working one of its species does.
        ItemStack item = taken.get(0).copyWithCount(2);
        var founding = AntColoniesGameTests.terrarium(helper, new BlockPos(4, 1, 12), common, 0, 0, 1000);
        founding.colony().startFounding();
        founding.setItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED, item.copy());
        founding.processSlots(level);
        helper.assertTrue(founding.getItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED).getCount() == 2
                && founding.colony().eggs() == 0, "A founding colony doesn't take a cluster");
        var iron = AntColoniesGameTests.terrarium(helper, new BlockPos(7, 1, 12), AntColonies.id("iron"), 300, 0, 1000);
        iron.setItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED, item.copy());
        iron.processSlots(level);
        helper.assertTrue(iron.getItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED).getCount() == 2,
                "Another species' colony doesn't take it");
        var working = AntColoniesGameTests.terrarium(helper, new BlockPos(10, 1, 12), common, 300, 0, 1000);
        double eggsBefore = working.colony().eggs();
        working.setItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED, item.copy());
        working.processSlots(level);
        helper.assertTrue(working.getItem(com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FEED).isEmpty()
                && working.colony().eggs() - eggsBefore == 2 * com.antcolonies.content.blockentity.TerrariumBlockEntity.CLUSTER_BROOD,
                "A working colony of its species takes 2 clusters as " + 2 * com.antcolonies.content.blockentity.TerrariumBlockEntity.CLUSTER_BROOD
                        + " eggs: " + (working.colony().eggs() - eggsBefore));
        helper.succeed();
    }

    private static void cluster(GameTestHelper helper, BlockPos at, boolean eggs, boolean natural, Identifier species, @org.jspecify.annotations.Nullable BlockPos anthill) {
        var block = eggs ? com.antcolonies.core.registry.ModDecor.ANT_EGG_CLUSTER : com.antcolonies.core.registry.ModDecor.LARVA_CLUSTER;
        helper.setBlock(at, block.get().defaultBlockState().setValue(com.antcolonies.content.block.BroodClusterBlock.NATURAL, natural));
        helper.getBlockEntity(at, com.antcolonies.content.blockentity.BroodClusterBlockEntity.class).set(species, anthill);
    }

    private static List<ItemStack> drops(GameTestHelper helper, BlockPos at, ItemStack tool) {
        BlockPos pos = helper.absolutePos(at);
        return Block.getDrops(helper.getBlockState(at), helper.getLevel(), pos, helper.getLevel().getBlockEntity(pos), null, tool);
    }

    // --- The alarm ---

    private static WildAnt ant(GameTestHelper helper, BlockPos at, Caste caste, WildAnt.Rank rank, BlockPos anthill) {
        WildAnt ant = helper.spawn(ModEntities.WILD_ANT.get(), at);
        ant.setCaste(caste);
        ant.setGuardian(true);
        ant.setRank(rank);
        ant.setAnthill(helper.absolutePos(anthill));
        ant.setHomeTo(ant.blockPosition(), 6);
        ant.setPersistenceRequired();
        return ant;
    }

    private static void chitin(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.CHITIN_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
    }

    /**
     * A player in Chitin is left alone until they raise the alarm. The anthill's unopened chests raise it (opened ones
     * no longer do), it reaches every ant of that anthill and no other's, it is per player, the Queen's chamber is where
     * she says it is, alarms survive saving, ants from before 0.2.0 learn their anthill on loading, and alarms run out.
     */
    private static void alarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = new BlockPos(8, 1, 8);
        BlockPos other = new BlockPos(30, 1, 30);
        WildAnt queen = ant(helper, home, Caste.QUEEN, WildAnt.Rank.GREAT_QUEEN, home);
        queen.setChamber(new AABB(helper.absolutePos(home)).inflate(4, 0, 4).expandTowards(0, 3, 0));
        WildAnt soldier = ant(helper, new BlockPos(10, 1, 8), Caste.SOLDIER, WildAnt.Rank.ROYAL, home);
        WildAnt worker = ant(helper, new BlockPos(14, 1, 12), Caste.WORKER, WildAnt.Rank.NORMAL, home);
        WildAnt stranger = ant(helper, new BlockPos(28, 1, 30), Caste.SOLDIER, WildAnt.Rank.NORMAL, other);
        Player player = helper.makeMockServerPlayerInLevel();
        Player friend = helper.makeMockServerPlayerInLevel();
        chitin(player);
        chitin(friend);
        helper.assertTrue(!soldier.hostileTo(player) && !worker.hostileTo(player), "Chitin keeps the anthill's ants peaceful");
        helper.assertTrue(queen.chamber().contains(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(home))) && !queen.chamber().contains(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(16, 1, 8)))),
                "Her chamber is round her spot");
        // A chest of the anthill's loot, unopened: touching it raises the alarm of the anthill nearby.
        BlockPos chestPos = helper.absolutePos(new BlockPos(12, 1, 10));
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chestPos, AnthillPiece.FOOD);
        helper.assertTrue(AnthillAlarm.anthillContainer(level, chestPos), "An unopened anthill chest");
        helper.assertTrue(helper.absolutePos(home).equals(AnthillAlarm.anthillNear(level, chestPos)), "The chest belongs to the nearer anthill");
        // Mock players are always in creative mode, which never raises alarms: raise it as the chest would have.
        helper.assertTrue(AnthillAlarm.exempt(player), "Mock players count as creative");
        int alarmed = AnthillAlarm.raise(level, helper.absolutePos(home), player, AnthillAlarm.Reason.CHEST);
        helper.assertTrue(alarmed == 3, "The Queen, the soldier and the worker: " + alarmed);
        helper.assertTrue(soldier.hostileTo(player) && worker.hostileTo(player) && soldier.alarmed(), "Now they fight, Chitin or not");
        helper.assertFalse(stranger.hostileTo(player), "Another anthill's ants aren't alarmed");
        helper.assertFalse(soldier.hostileTo(friend), "Only the player who raised it");
        // Once looted, the chest is just a chest.
        if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
            chest.unpackLootTable(player);
        }
        helper.assertFalse(AnthillAlarm.anthillContainer(level, chestPos), "A looted chest no longer raises the alarm");
        // Saved and loaded, the alarm stays.
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        soldier.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        WildAnt copy = ModEntities.WILD_ANT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(copy.alarmedAt(player) && copy.rank() == WildAnt.Rank.ROYAL && helper.absolutePos(home).equals(copy.anthill()),
                "The alarm, rank and anthill are saved");
        // An anthill Queen from before 0.2.0 (no rank, no anthill saved) becomes a great Queen of the anthill she was kept at.
        TagValueOutput old = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        queen.saveWithoutId(old);
        CompoundTag oldTag = old.buildResult();
        oldTag.remove("rank");
        oldTag.remove("anthill");
        oldTag.remove("chamber");
        oldTag.remove("alarms");
        WildAnt oldQueen = ModEntities.WILD_ANT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        oldQueen.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), oldTag));
        helper.assertTrue(oldQueen.rank() == WildAnt.Rank.GREAT_QUEEN && oldQueen.anthill() != null && oldQueen.chamber() != null,
                "An old anthill Queen learns her anthill: " + oldQueen.rank() + " " + oldQueen.anthill());
        // Alarms run out.
        worker.alarm(friend, level.getGameTime() + 5);
        helper.assertTrue(worker.hostileTo(friend), "Alarmed at the friend for a moment");
        helper.runAfterDelay(15, () -> {
            helper.assertFalse(worker.hostileTo(friend), "The alarm should have run out");
            List.of(queen, soldier, worker, stranger).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }
}
