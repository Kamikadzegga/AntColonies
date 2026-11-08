package com.antcolonies.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.breeding.QueenRearing;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.colony.evolution.EvolutionLogic;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.SpeciesOutput;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world tests of the colony rules: real numbers that persist, allocation that never counts an ant twice, production,
 * food, starvation, breeding, evolution, rearing and hybrids, the Genetic Laboratory, separate colonies, offline
 * limits, and that the shipped data loads. Run with {@code gradlew runGameTestServer}; dev runs only.
 */
@Mod(AntColonies.MODID)
public class AntColoniesGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, AntColonies.MODID);
    private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");
    private static final Identifier COMMON = AntColonies.id("common");

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SPECIES_DATA_LOADS = test("species_data_loads", AntColoniesGameTests::speciesDataLoads);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> COLONY_PERSISTS = test("colony_persists", AntColoniesGameTests::colonyPersists);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ALLOCATION = test("allocation_never_double_assigns", AntColoniesGameTests::allocation);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SCAVENGING = test("scavenging_fills_resource_pile", AntColoniesGameTests::scavenging);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MORE_WORKERS = test("more_workers_find_more", AntColoniesGameTests::moreWorkers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STARVATION = test("starvation_reduces_population", AntColoniesGameTests::starvation);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BREEDING = test("eggs_become_adults", AntColoniesGameTests::breeding);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FARMING = test("farmer_garden_feeds_colony", AntColoniesGameTests::farming);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GARDEN_NEEDS_FARMERS = test("garden_needs_farmers", AntColoniesGameTests::gardenNeedsFarmers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FIGHTING = test("fighting_site_makes_loot", AntColoniesGameTests::fighting);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EVOLUTION = test("evolution_changes_species", AntColoniesGameTests::evolution);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REARING = test("queen_chamber_raises_daughter", AntColoniesGameTests::rearing);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HYBRIDS = test("hybrids_need_their_pair", AntColoniesGameTests::hybrids);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LAB = test("genetic_lab_raises_one_trait", AntColoniesGameTests::lab);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TWO_COLONIES = test("colonies_never_mix", AntColoniesGameTests::twoColonies);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OFFLINE = test("offline_catch_up_is_bounded", AntColoniesGameTests::offline);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TWEEZERS = test("tweezers_catch_wild_ants", AntColoniesGameTests::tweezers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FEEDING = test("terrarium_takes_queen_and_food", AntColoniesGameTests::feeding);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NESTS = test("nest_features_place_nests", AntColoniesGameTests::nests);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GUIDE = test("guide_examples_load", AntColoniesGameTests::guideExampleLoads);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GENERATOR = test("generator_powers_lab", AntColoniesGameTests::generatorPowersLab);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PERFORMANCE = test("big_colony_performance", AntColoniesGameTests::performance);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DIMINISHING = test("diminishing_is_colony_wide", AntColoniesGameTests::diminishingIsColonyWide);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LIMITS = test("genetic_limits", AntColoniesGameTests::geneticLimits);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RARE = test("rare_find_multiplier", AntColoniesGameTests::rareFindMultiplier);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ADVANCEMENTS = test("advancements_work", AntColoniesGameTests::advancementsWork);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REDSTONE = test("redstone_control", AntColoniesGameTests::redstoneControl);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CHITIN = test("chitin_gear", AntColoniesGameTests::chitinGear);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GUARDS = test("guard_ants", AntColoniesGameTests::guardAnts);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ANTHILL = test("giant_anthill", AntColoniesGameTests::giantAnthill);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RUNS_ALONE = test("colony_runs_by_itself", AntColoniesGameTests::colonyRunsByItself);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> JOINING = test("chambers_join_by_touching", AntColoniesGameTests::chambersJoinByTouching);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MOVING = test("colony_moves_with_terrarium", AntColoniesGameTests::colonyMoves);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> UPGRADE_SYNC = test("terrarium_update_keeps_upgrades", AntColoniesGameTests::terrariumUpdateKeepsUpgrades);

    public AntColoniesGameTests(IEventBus modEventBus) {
        TESTS.register(modEventBus);
        modEventBus.addListener(AntColoniesGameTests::registerTests);
        modEventBus.addListener(AntColoniesGameTests::addGuidePack);
    }

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test(String name, Consumer<GameTestHelper> test) {
        return TESTS.register(name, () -> test);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(AntColonies.id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : List.of(SPECIES_DATA_LOADS, COLONY_PERSISTS, ALLOCATION, SCAVENGING,
                MORE_WORKERS, STARVATION, BREEDING, FARMING, GARDEN_NEEDS_FARMERS, FIGHTING, EVOLUTION, REARING, HYBRIDS, TWO_COLONIES, OFFLINE,
                TWEEZERS, FEEDING, NESTS, GUIDE, GENERATOR, PERFORMANCE, DIMINISHING, LIMITS, RARE, ADVANCEMENTS, REDSTONE, CHITIN, GUARDS, ANTHILL, RUNS_ALONE, JOINING, MOVING, UPGRADE_SYNC)) {
            event.registerTest(test.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, test.getId()),
                    spaced(environment, 200)));
        }
        event.registerTest(LAB.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, LAB.getId()),
                spaced(environment, 600)));
    }

    // --- Helpers ---

    /**
     * A test's data with 20 blocks of padding: tests build up to 16 blocks from their corner, and without the padding a
     * colony could reach into a neighbouring test's blocks (its structures joined the other colony and broke both).
     */
    private static TestData<Holder<TestEnvironmentDefinition<?>>> spaced(Holder<TestEnvironmentDefinition<?>> environment, int ticks) {
        return new TestData<>(environment, net.minecraft.world.level.Level.OVERWORLD, EMPTY, ticks, 0, true, net.minecraft.world.level.block.Rotation.NONE,
                false, 1, 1, false, 20);
    }

    private static TerrariumBlockEntity terrarium(GameTestHelper helper, BlockPos pos, Identifier species, double workers, double soldiers, double food) {
        helper.setBlock(pos, ModBlocks.TERRARIUM.get());
        TerrariumBlockEntity terrarium = helper.getBlockEntity(pos, TerrariumBlockEntity.class);
        Colony colony = terrarium.colony();
        colony.setQueen(Genetics.wildQueen(species, RandomSource.create(5)));
        // Colonies have no soldiers: any asked for join the workers.
        colony.setPopulation(0, 0, workers + soldiers);
        colony.setFood(food);
        return terrarium;
    }

    private static StructureBlockEntity structure(GameTestHelper helper, BlockPos pos, StructureKind kind) {
        helper.setBlock(pos, ModBlocks.STRUCTURES.get(kind).get());
        return helper.getBlockEntity(pos, StructureBlockEntity.class);
    }

    /**
     * Places around a Terrarium on its level, in an order where each touches the Terrarium or one placed before it: the
     * four sides (north, east, south, west), then the corners.
     */
    private static List<BlockPos> places(BlockPos center) {
        return List.of(center.north(), center.east(), center.south(), center.west(), center.north().east(), center.south().east(),
                center.south().west(), center.north().west());
    }

    /** Chambers around the Terrarium at {@code center} (see {@link #places}), all joined to it. Returns them in order. */
    private static List<StructureBlockEntity> ring(GameTestHelper helper, BlockPos center, StructureKind... kinds) {
        List<BlockPos> places = places(center);
        List<StructureBlockEntity> placed = new java.util.ArrayList<>();
        for (int i = 0; i < kinds.length; i++) {
            placed.add(structure(helper, places.get(i), kinds[i]));
        }
        return placed;
    }

    private static void step(GameTestHelper helper, TerrariumBlockEntity terrarium, double minutes) {
        ServerLevel level = helper.getLevel();
        terrarium.rescan(level);
        ColonySimulator.step(terrarium, level, minutes);
    }

    private static int count(StructureBlockEntity pile) {
        int total = 0;
        for (int slot = 0; slot < pile.getContainerSize(); slot++) {
            total += pile.getItem(slot).getCount();
        }
        return total;
    }

    // --- Tests ---

    /**
     * The modpack guide's examples (docs/modpack-guide.md), loaded as a separate data pack: a new species and evolution
     * in another namespace, ant food merged into the built-in data map, and biome tags merged for wild ant spawns.
     */
    private static void guideExampleLoads(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Identifier copper = Identifier.fromNamespaceAndPath("mypack", "copper");
        AntSpecies species = ModRegistries.species(registries, copper);
        helper.assertTrue(species != null, "The guide's Copper Ant didn't load");
        for (SpeciesOutput output : species.production().outputs()) {
            helper.assertTrue(output.available(), "Copper Ant output missing: " + output);
        }
        helper.assertTrue(species.signature().isPresent() && species.signature().get().value() == Items.COPPER_INGOT, "Copper Ant signature item");
        helper.assertTrue(registries.lookupOrThrow(ModRegistries.EVOLUTION).listElements()
                .anyMatch(e -> e.value().to().equals(copper) && e.value().from().equals(COMMON.withPath("stone"))), "The guide's evolution didn't load");
        // Mod conditions: a species whose condition holds (antcolonies, through an "or") loads; one that needs Mekanism doesn't.
        helper.assertTrue(ModRegistries.species(registries, Identifier.fromNamespaceAndPath("mypack", "needs_present")) != null,
                "A species whose conditions hold should load");
        helper.assertTrue(ModRegistries.species(registries, Identifier.fromNamespaceAndPath("mypack", "needs_absent")) == null,
                "A species that needs Mekanism should not load without it");
        // The food map merges with the built-in one: new entries, a tag entry, a removal, and the built-in values kept.
        var cookie = com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(Items.COOKIE));
        helper.assertTrue(cookie != null && cookie.food() == 60, "Cookie should be worth 60 food");
        var lichen = com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(Items.GLOW_LICHEN));
        helper.assertTrue(lichen != null && lichen.specialKind().equals("crystal"), "Glow lichen should give crystal food");
        var dandelion = com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(Items.DANDELION));
        helper.assertTrue(dandelion != null && dandelion.food() == 10, "Flowers (by tag) should be worth 10 food");
        helper.assertTrue(com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(Items.ROTTEN_FLESH)) == null, "Rotten flesh should be removed");
        var antFood = com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(ModItems.ANT_FOOD.get()));
        helper.assertTrue(antFood != null && antFood.food() == 200, "The built-in Ant Food value should survive the merge");
        // Biome tags merge: the new spawn tag, and the built-in has_wild_ants extended with it.
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var dripstone = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES);
        helper.assertTrue(dripstone.is(net.minecraft.tags.TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("mypack", "spawns/copper"))),
                "Dripstone caves should be in mypack:spawns/copper");
        helper.assertTrue(dripstone.is(net.minecraft.tags.TagKey.create(Registries.BIOME, AntColonies.id("has_wild_ants"))),
                "has_wild_ants should include the guide's copper biomes");
        helper.assertTrue(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.FOREST)
                .is(net.minecraft.tags.TagKey.create(Registries.BIOME, AntColonies.id("has_wild_ants"))), "The built-in wild ant biomes should stay");
        helper.succeed();
    }

    /**
     * The guide's examples as a separate data pack, only on the (dedicated) game test server, which turns on every
     * available pack; dev clients and their worlds never see it.
     */
    private static void addGuidePack(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA
                && net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) {
            event.addPackFinders(AntColonies.id("guide_example"), net.minecraft.server.packs.PackType.SERVER_DATA,
                    net.minecraft.network.chat.Component.literal("Ant Colonies guide examples"), net.minecraft.server.packs.repository.PackSource.BUILT_IN,
                    false, net.minecraft.server.packs.repository.Pack.Position.TOP);
        }
    }

    /**
     * All 38 species load (the ones for other mods' metals stay out without those mods), their item outputs exist (tag
     * outputs may be empty until a mod fills them), every evolution and hybrid points at real species, and no two
     * mutations share a catalyst.
     */
    private static void speciesDataLoads(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var species = registries.lookupOrThrow(ModRegistries.SPECIES);
        long own = species.listElements().filter(e -> e.key().identifier().getNamespace().equals(AntColonies.MODID)).count();
        helper.assertTrue(own == 42, "Expected 42 species, got " + own);
        for (var entry : species.listElements().toList()) {
            for (SpeciesOutput output : entry.value().production().outputs()) {
                helper.assertTrue(output.tag().isPresent() || output.available(), "Output of " + entry.key().identifier() + " missing: " + output);
            }
        }
        registries.lookupOrThrow(ModRegistries.EVOLUTION).listElements().forEach(e -> {
            helper.assertTrue(ModRegistries.species(registries, e.value().from()) != null, "Evolution from unknown species " + e.value().from());
            helper.assertTrue(ModRegistries.species(registries, e.value().to()) != null, "Evolution to unknown species " + e.value().to());
        });
        registries.lookupOrThrow(ModRegistries.HYBRID).listElements().forEach(h ->
                helper.assertTrue(ModRegistries.species(registries, h.value().result()) != null, "Hybrid of unknown species " + h.value().result()));
        helper.assertTrue(registries.lookupOrThrow(ModRegistries.TRAIT_UPGRADE).listElements().count() == Trait.values().length,
                "Every trait needs its Genetic Laboratory costs");
        helper.assertTrue(com.antcolonies.core.registry.ModDataMaps.food(new ItemStack(ModItems.ANT_FOOD.get())) != null, "Ant Food has no food value");
        for (String other : List.of("osmium", "tin", "lead", "silver", "nickel", "aluminum", "zinc", "uranium", "fluorite", "certus")) {
            helper.assertTrue(ModRegistries.species(registries, AntColonies.id(other)) == null, other + " needs another mod and should not load here");
        }
        var catalysts = new java.util.HashSet<net.minecraft.world.item.Item>();
        registries.lookupOrThrow(ModRegistries.MUTATION).listElements().forEach(m -> m.value().catalyst().ifPresent(cost -> cost.ingredient().items()
                .forEach(item -> helper.assertTrue(catalysts.add(item.value()), "Two mutations share the catalyst " + item.value()))));
        helper.assertTrue(registries.lookupOrThrow(ModRegistries.MUTATION).listElements().count() == 31, "Expected 31 mutations");
        helper.succeed();
    }

    /** A colony's population, food, Queen genome and lineage survive saving and loading its Terrarium. */
    private static void colonyPersists(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 12345.5, 678, 4321);
        Colony colony = terrarium.colony();
        colony.setPopulation(55, 77, 12345.5);
        QueenData queen = colony.queen();
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = terrarium.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), terrarium.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof TerrariumBlockEntity, "Terrarium did not load");
        Colony copy = ((TerrariumBlockEntity) loaded).colony();
        helper.assertTrue(copy.workers() == 12345 && copy.eggs() == 55 && copy.larvae() == 77,
                "Population changed: " + copy.workers() + " " + copy.eggs() + " " + copy.larvae());
        helper.assertTrue(Math.abs(copy.food() - 4321) < 1e-6, "Food changed: " + copy.food());
        helper.assertTrue(queen.equals(copy.queen()), "Queen changed: " + copy.queen());
        helper.assertTrue(copy.id().equals(colony.id()), "Colony id changed");
        helper.succeed();
    }

    /**
     * Workers are handed out once: maintenance first, chambers up to their wish and capacity; a Foraging Chamber swapped
     * for another chamber frees its ants.
     */
    private static void allocation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 600, 0, 5000);
        List<StructureBlockEntity> sites = ring(helper, pos, StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE,
                StructureKind.FORAGING_SITE, StructureKind.STORAGE);
        StructureBlockEntity a = sites.get(0);
        StructureBlockEntity b = sites.get(1);
        StructureBlockEntity c = sites.get(2);
        b.setRequested(10);
        step(helper, terrarium, 0.05);
        Colony colony = terrarium.colony();
        int total = a.assigned() + b.assigned() + c.assigned();
        int maintenance = colony.maintenanceWorkers();
        helper.assertTrue(total + maintenance <= colony.workers() + 1, "Handed out " + total + " + " + maintenance + " of " + colony.workers());
        helper.assertTrue(b.assigned() == 10, "Site asking for 10 got " + b.assigned());
        helper.assertTrue(a.assigned() <= a.capacity() && c.assigned() <= c.capacity(), "A site went over its capacity");
        // Swapping a chamber for another kind: its ants go to the others.
        int before = c.assigned();
        structure(helper, places(pos).get(0), StructureKind.QUEEN_CHAMBER);
        step(helper, terrarium, 0.05);
        helper.assertTrue(c.assigned() >= before, "Freed workers were not reassigned");
        helper.assertTrue(b.assigned() + c.assigned() + colony.maintenanceWorkers() <= colony.workers() + 1, "Reassignment counted ants twice");
        helper.succeed();
    }

    /**
     * Assigned workers find their species' resources and put them in the Storage; with the Storage full they keep them in
     * their Foraging Site's store, which empties into the Storage again once it has room.
     */
    private static void scavenging(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 80, 0, 5000);
        List<StructureBlockEntity> built = ring(helper, pos, StructureKind.FORAGING_SITE, StructureKind.STORAGE);
        StructureBlockEntity site = built.get(0);
        StructureBlockEntity pile = built.get(1);
        step(helper, terrarium, 5);
        helper.assertTrue(count(pile) > 0 && count(site) == 0, "Finds should go to the Storage: storage " + count(pile) + ", site " + count(site));
        // Storage full: finds wait in the site.
        java.util.List<ItemStack> kept = new java.util.ArrayList<>();
        for (int slot = 0; slot < pile.getContainerSize(); slot++) {
            kept.add(pile.getItem(slot).copy());
            pile.setItem(slot, new ItemStack(Items.BEDROCK, 64));
        }
        step(helper, terrarium, 5);
        helper.assertTrue(count(site) > 0, "With the Storage full, finds should wait in the site's store");
        // Room again: the site's store moves on to the Storage.
        for (int slot = 0; slot < pile.getContainerSize(); slot++) {
            pile.setItem(slot, kept.get(slot));
        }
        step(helper, terrarium, 1 / 60.0);
        helper.assertTrue(count(site) == 0, "The site's store should empty into the Storage, still " + count(site));
        AntSpecies common = ModRegistries.species(helper.getLevel().registryAccess(), COMMON);
        for (int slot = 0; slot < pile.getContainerSize(); slot++) {
            ItemStack stack = pile.getItem(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(common.production().outputs().stream().anyMatch(o -> o.create(1).is(stack.getItem())),
                        "Found something Common Ants don't find: " + stack);
            }
        }
        helper.succeed();
    }

    /** Twice the workers find more (but less than twice as much: diminishing returns). */
    private static void moreWorkers(GameTestHelper helper) {
        BlockPos small = new BlockPos(1, 1, 1);
        BlockPos large = new BlockPos(1, 1, 6);
        TerrariumBlockEntity few = terrarium(helper, small, COMMON, 10, 0, 5000);
        TerrariumBlockEntity many = terrarium(helper, large, COMMON, 70, 0, 5000);
        // Same Queen genes for both.
        many.colony().setQueen(null);
        many.colony().setQueen(few.colony().queen());
        StructureBlockEntity siteA = ring(helper, small, StructureKind.FORAGING_SITE, StructureKind.STORAGE).get(0);
        StructureBlockEntity siteB = ring(helper, large, StructureKind.FORAGING_SITE, StructureKind.STORAGE).get(0);
        step(helper, few, 0.05);
        step(helper, many, 0.05);
        helper.assertTrue(siteB.assigned() > siteA.assigned(), "More workers should be assigned: " + siteA.assigned() + " vs " + siteB.assigned());
        helper.assertTrue(siteB.rate() > siteA.rate(), "More workers should find faster: " + siteA.rate() + " vs " + siteB.rate());
        helper.assertTrue(siteB.rate() < siteA.rate() * siteB.assigned() / Math.max(1.0, siteA.assigned()),
                "Production should have diminishing returns");
        helper.succeed();
    }

    /** Without food the colony starves: past the grace period its population declines. */
    private static void starvation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 500, 0, 0);
        ring(helper, pos);
        for (int i = 0; i < 20; i++) {
            step(helper, terrarium, 1);
        }
        Colony colony = terrarium.colony();
        helper.assertTrue(ColonyStatus.STARVING.in(colony.status()), "The colony should be starving");
        helper.assertTrue(colony.workers() < 450, "Starvation should kill workers, still " + colony.workers());
        helper.succeed();
    }

    /** A fed Queen with a Brood Chamber lays eggs, they hatch, larvae grow into adults. */
    private static void breeding(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 0, 0, 2000);
        ring(helper, pos, StructureKind.NEST_CHAMBER);
        for (int i = 0; i < 30; i++) {
            step(helper, terrarium, 0.5);
        }
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.adults() > 0, "No adults were raised");
        helper.assertTrue(colony.stats().eggsLaid() > 0, "No eggs were laid");
        helper.succeed();
    }

    /** Farmer Ants in a Fungus Garden grow food for their colony. */
    private static void farming(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, AntColonies.id("farmer"), 200, 0, 100);
        ring(helper, pos, StructureKind.HARVESTING_SITE, StructureKind.STORAGE);
        step(helper, terrarium, 3);
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.stats().foodIn() > colony.stats().foodOut(), "The garden should feed more than the colony eats: in "
                + colony.stats().foodIn() + ", out " + colony.stats().foodOut());
        helper.assertTrue(colony.food() > 100, "Food did not grow: " + colony.food());
        helper.succeed();
    }

    /** A Fungus Garden takes no ants of a species that can't farm. */
    private static void gardenNeedsFarmers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 200, 0, 1000);
        StructureBlockEntity garden = ring(helper, pos, StructureKind.HARVESTING_SITE).get(0);
        step(helper, terrarium, 0.1);
        helper.assertTrue(garden.assigned() == 0, "Common Ants should not farm");
        helper.assertTrue(ColonyStatus.INCOMPATIBLE_SPECIES.in(garden.siteStatus()), "The garden should say the species can't farm");
        helper.succeed();
    }

    /**
     * Hunters at a Hunting Ground bring mob drops (from loot tables, no mobs spawned) into the Storage and feed the colony
     * on their catch; a species that doesn't hunt leaves a Hunting Ground alone.
     */
    private static void fighting(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, AntColonies.id("army"), 200, 0, 100);
        List<StructureBlockEntity> built = ring(helper, pos, StructureKind.HUNTING_GROUND, StructureKind.STORAGE);
        StructureBlockEntity ground = built.get(0);
        StructureBlockEntity storage = built.get(1);
        int mobsBefore = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(16)).size();
        step(helper, terrarium, 10);
        helper.assertTrue(ground.assigned() > 0, "Army Ants should hunt at the Hunting Ground");
        helper.assertTrue(count(storage) > 0, "The hunts brought nothing");
        helper.assertTrue(terrarium.colony().stats().hunts() > 0 && terrarium.colony().stats().foodIn() > 0,
                "The hunters should feed the colony on their catch: food in " + terrarium.colony().stats().foodIn());
        // Common Ants don't hunt.
        TerrariumBlockEntity common = terrarium(helper, new BlockPos(1, 1, 6), COMMON, 200, 0, 1000);
        StructureBlockEntity idle = ring(helper, new BlockPos(1, 1, 6), StructureKind.HUNTING_GROUND).get(0);
        step(helper, common, 0.1);
        helper.assertTrue(idle.assigned() == 0 && ColonyStatus.INCOMPATIBLE_SPECIES.in(idle.siteStatus()), "Common Ants should not hunt");
        int mobsAfter = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(16)).size();
        helper.assertTrue(mobsAfter == mobsBefore, "Hunting must not spawn mobs");
        helper.succeed();
    }

    /** Feeding a ready Stone Queen iron evolves her into an Iron Queen; the colony keeps its ants and transforms. */
    private static void evolution(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, AntColonies.id("stone"), 400, 0, 5000);
        ring(helper, pos, StructureKind.NEST_CHAMBER, StructureKind.NEST_CHAMBER);
        step(helper, terrarium, 0.05);
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.stage() >= 2, "The colony should be stage 2, is " + colony.stage());
        var registries = helper.getLevel().registryAccess();
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 64);
        EvolutionLogic.Outcome outcome = EvolutionLogic.Outcome.NONE;
        int attempts = 0;
        while (outcome != EvolutionLogic.Outcome.EVOLVED && attempts < 64) {
            outcome = EvolutionLogic.attempt(registries, colony, iron, 0, RandomSource.create(attempts)).outcome();
            attempts++;
        }
        helper.assertTrue(outcome == EvolutionLogic.Outcome.EVOLVED, "No evolution within the guaranteed number of attempts");
        helper.assertTrue(colony.species().equals(AntColonies.id("iron")), "Species is " + colony.species());
        helper.assertTrue(AntColonies.id("stone").equals(colony.formerSpecies()), "The former species should be remembered");
        helper.assertTrue(colony.workers() >= 399, "Evolution must keep the population");
        helper.assertTrue(colony.queen().genome().lineage().history().contains(AntColonies.id("iron")), "The lineage should record the evolution");
        helper.succeed();
    }

    /** A Queen Chamber with materials and a Test Tube raises a daughter of the next generation. */
    private static void rearing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 400, 0, 5000);
        StructureBlockEntity chamber = ring(helper, pos, StructureKind.QUEEN_CHAMBER).get(0);
        chamber.setItem(0, new ItemStack(ModItems.ROYAL_JELLY.get(), 4));
        chamber.setItem(1, new ItemStack(Items.POPPY, 8));
        chamber.setItem(StructureKind.QUEEN_CHAMBER_TUBE, new ItemStack(ModItems.TEST_TUBE.get()));
        for (int i = 0; i < 6; i++) {
            step(helper, terrarium, 0.5);
        }
        ItemStack output = chamber.getItem(StructureKind.QUEEN_CHAMBER_OUTPUT);
        QueenData daughter = output.get(ModDataComponents.QUEEN.get());
        helper.assertTrue(daughter != null, "No daughter was raised (status " + ColonyStatus.of(chamber.rearingStatus()) + ")");
        Genome mother = terrarium.colony().queen().genome();
        helper.assertTrue(daughter.genome().lineage().generation() == mother.lineage().generation() + 1, "Wrong generation");
        helper.assertTrue(daughter.genome().lineage().parent().orElseThrow().equals(mother.lineage().id()), "Wrong mother");
        helper.assertTrue(chamber.getItem(StructureKind.QUEEN_CHAMBER_TUBE).isEmpty(), "The Test Tube should be used");
        helper.succeed();
    }

    /** Only listed pairs cross; a Wood Queen crossed with a Diamond sample sometimes gives a Crystal Wood Queen. */
    private static void hybrids(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(QueenRearing.findHybrid(level, AntColonies.id("wood"), AntColonies.id("diamond")) != null, "Wood x Diamond should cross");
        helper.assertTrue(QueenRearing.findHybrid(level, AntColonies.id("diamond"), AntColonies.id("wood")) != null, "Crosses work both ways");
        helper.assertTrue(QueenRearing.findHybrid(level, COMMON, AntColonies.id("diamond")) == null, "Common x Diamond must not cross");
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, AntColonies.id("wood"), 400, 0, 50000);
        Colony colony = terrarium.colony();
        // A strong mother, so the cross is likely (it needs average level 3).
        Genome strong = colony.queen().genome();
        for (Trait trait : Trait.values()) {
            strong = strong.withLevel(trait, Trait.maxLevel());
        }
        colony.setQueen(null);
        colony.setQueen(new QueenData(AntColonies.id("wood"), strong));
        StructureBlockEntity chamber = structure(helper, pos.east(), StructureKind.QUEEN_CHAMBER);
        AntSpecies wood = ModRegistries.species(level.registryAccess(), AntColonies.id("wood"));
        boolean hybrid = false;
        for (int round = 0; round < 24 && !hybrid; round++) {
            chamber.setItem(0, new ItemStack(ModItems.ROYAL_JELLY.get(), 4));
            chamber.setItem(1, new ItemStack(Items.OAK_SAPLING, 8));
            chamber.setItem(StructureKind.QUEEN_CHAMBER_TUBE, new ItemStack(ModItems.TEST_TUBE.get()));
            chamber.setItem(StructureKind.QUEEN_CHAMBER_OUTPUT, ItemStack.EMPTY);
            ItemStack sample = new ItemStack(ModItems.GENETIC_SAMPLE.get());
            sample.set(ModDataComponents.QUEEN.get(), new QueenData(AntColonies.id("diamond"), strong));
            chamber.setItem(StructureKind.QUEEN_CHAMBER_SAMPLE, sample);
            chamber.setRearing(wood.rearing().time(), 0);
            QueenRearing.tick(level, colony, wood, chamber, 0.01, 1);
            QueenData daughter = chamber.getItem(StructureKind.QUEEN_CHAMBER_OUTPUT).get(ModDataComponents.QUEEN.get());
            helper.assertTrue(daughter != null, "Round " + round + " raised nothing (" + ColonyStatus.of(chamber.rearingStatus()) + ")");
            hybrid = daughter.species().equals(AntColonies.id("crystal_wood"));
        }
        helper.assertTrue(hybrid, "24 crosses gave no Crystal Wood Queen");
        helper.succeed();
    }

    /** The Genetic Laboratory, powered, raises exactly the chosen trait by one level. */
    private static void lab(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.GENETIC_LAB.get());
        GeneticLabBlockEntity lab = helper.getBlockEntity(pos, GeneticLabBlockEntity.class);
        QueenData queen = Genetics.wildQueen(COMMON, RandomSource.create(3));
        Genome genome = queen.genome();
        for (Trait trait : Trait.values()) {
            genome = genome.withLevel(trait, 1);
        }
        QueenData basic = queen.withGenome(genome);
        ItemStack tube = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        tube.set(ModDataComponents.QUEEN.get(), basic);
        lab.setItem(GeneticLabBlockEntity.SLOT_QUEEN, tube);
        lab.setItem(GeneticLabBlockEntity.SLOT_MATERIAL, new ItemStack(ModItems.ROYAL_JELLY.get(), 2));
        lab.setItem(GeneticLabBlockEntity.SLOT_MATERIAL + 1, new ItemStack(Items.REDSTONE, 16));
        // Power it the way a cable would: FE into the lab through its energy capability.
        int given = power(helper, pos, 12_000);
        helper.assertTrue(given == 12_000 && lab.energy() == 12_000, "The lab should take 12000 FE, took " + given + ", has " + lab.energy());
        lab.selectTrait(Trait.EGG_PRODUCTION.ordinal());
        helper.runAfterDelay(20, () -> {
            lab.start(GeneticLabBlockEntity.Operation.UPGRADE, helper.getLevel().registryAccess());
            helper.assertTrue(lab.operation() == GeneticLabBlockEntity.Operation.UPGRADE, "The upgrade did not start");
        });
        helper.succeedWhen(() -> {
            QueenData now = lab.getItem(GeneticLabBlockEntity.SLOT_QUEEN).get(ModDataComponents.QUEEN.get());
            helper.assertTrue(now != null && now.genome().level(Trait.EGG_PRODUCTION) == 2, "Egg Production is not level 2 yet");
            for (Trait trait : Trait.values()) {
                if (trait != Trait.EGG_PRODUCTION) {
                    helper.assertTrue(now.genome().level(trait) == 1, trait + " changed");
                }
            }
            helper.assertTrue(now.genome().lineage().equals(basic.genome().lineage()), "The lineage must not change");
        });
    }


    /** FE into the block at {@code pos} through its energy capability, as a cable would (several calls, as it takes 4000 a call). */
    private static int power(GameTestHelper helper, BlockPos pos, int amount) {
        var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, helper.absolutePos(pos),
                net.minecraft.core.Direction.UP);
        helper.assertTrue(handler != null, "The Genetic Laboratory takes no FE");
        int given = 0;
        for (int i = 0; i < 100 && given < amount; i++) {
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int now = handler.insert(amount - given, transaction);
                transaction.commit();
                given += now;
                if (now == 0) {
                    break;
                }
            }
        }
        return given;
    }

    /**
     * A Generator burns coal into FE and gives it to the lab next to it: the coal is used, the generator lights, and the lab
     * fills. Nothing can be pushed into a Generator.
     */
    private static void generatorPowersLab(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.GENETIC_LAB.get());
        GeneticLabBlockEntity lab = helper.getBlockEntity(pos, GeneticLabBlockEntity.class);
        BlockPos generatorPos = pos.above();
        helper.setBlock(generatorPos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(generatorPos, com.antcolonies.content.blockentity.GeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 2));
        helper.assertTrue(generator.burnDuration(helper.getLevel(), new ItemStack(Items.COAL)) == 1600, "Coal should burn for 1600 ticks");
        helper.assertTrue(generator.burnDuration(helper.getLevel(), new ItemStack(Items.DIRT)) == 0, "Dirt is no fuel");
        var into = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, helper.absolutePos(generatorPos),
                net.minecraft.core.Direction.UP);
        helper.assertTrue(into != null, "The Generator has no FE capability");
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            helper.assertTrue(into.insert(1000, transaction) == 0, "Nothing may be pushed into a Generator");
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(generator.getItem(0).getCount() == 1, "One coal should be burning, the other waiting");
            helper.assertTrue(helper.getBlockState(generatorPos).getValue(com.antcolonies.content.block.GeneratorBlock.LIT), "The Generator should be lit");
            helper.assertTrue(lab.energy() >= 2000, "The lab should be filling, has " + lab.energy());
            helper.assertTrue(generator.energy() < 1000, "The Generator should hand its FE on, holds " + generator.energy());
        });
    }


    /**
     * A big colony (a full ring of the busiest chambers, 2000 workers and 300 soldiers): how long a ring scan and a
     * simulation step take, and how big the Terrarium's sync packet is. Logged for the record; fails only if a step gets
     * far too slow for a server (over 5 ms on average).
     */
    private static void performance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = terrarium(helper, center, AntColonies.id("stone"), 2000, 300, 1_000_000);
        ring(helper, center, StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.NEST_CHAMBER,
                StructureKind.FORAGING_SITE, StructureKind.NEST_CHAMBER, StructureKind.STORAGE, StructureKind.QUEEN_CHAMBER);
        terrarium.rescan(level);
        int found = 0;
        for (StructureKind kind : StructureKind.values()) {
            found += terrarium.colony().layout().of(kind).size();
        }
        // Network scans.
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            terrarium.rescan(level);
        }
        double scanMs = (System.nanoTime() - start) / 50 / 1e6;
        // Normal steps (one second of game time each), then catch-up steps (two minutes each).
        for (int i = 0; i < 20; i++) {
            ColonySimulator.step(terrarium, level, 1 / 60.0);
        }
        start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            ColonySimulator.step(terrarium, level, 1 / 60.0);
        }
        double stepMs = (System.nanoTime() - start) / 200 / 1e6;
        start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            ColonySimulator.step(terrarium, level, 2.0);
        }
        double catchUpMs = (System.nanoTime() - start) / 10 / 1e6;
        int bytes;
        try {
            var out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.write(terrarium.getUpdateTag(level.registryAccess()), new java.io.DataOutputStream(out));
            bytes = out.size();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        com.mojang.logging.LogUtils.getLogger().info(String.format(java.util.Locale.ROOT,
                "[Performance] %d chambers found; scan %.3f ms; step %.3f ms; 2-minute catch-up step %.3f ms; sync packet %d bytes; workers %d, found %d",
                found, scanMs, stepMs, catchUpMs, bytes, (long) terrarium.colony().workers(), terrarium.colony().totalFound()));
        helper.assertTrue(found == 8, "The scan should find the eight chambers, found " + found);
        helper.assertTrue(stepMs < 5, "A simulation step took " + stepMs + " ms on average");
        helper.succeed();
    }


    /**
     * Diminishing returns are over the whole colony: workers spread over two Foraging Chambers find as much as the same
     * workers in one chamber with a Population Upgrade, so splitting the work over more chambers gains nothing.
     */
    private static void diminishingIsColonyWide(GameTestHelper helper) {
        TerrariumBlockEntity split = terrarium(helper, new BlockPos(1, 1, 1), AntColonies.id("stone"), 500, 0, 100_000);
        List<StructureBlockEntity> two = ring(helper, new BlockPos(1, 1, 1), StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE,
                StructureKind.STORAGE);
        StructureBlockEntity siteA = two.get(0);
        StructureBlockEntity siteB = two.get(1);
        TerrariumBlockEntity single = terrarium(helper, new BlockPos(6, 1, 1), AntColonies.id("stone"), 500, 0, 100_000);
        StructureBlockEntity big = ring(helper, new BlockPos(6, 1, 1), StructureKind.FORAGING_SITE, StructureKind.STORAGE).get(0);
        big.upgrades().setItem(0, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 1)));
        // The same Queen for both, so only the layout differs.
        single.colony().setQueen(split.colony().queen());
        step(helper, split, 1 / 60.0);
        step(helper, single, 1 / 60.0);
        double splitRate = siteA.rate() + siteB.rate();
        int splitAnts = siteA.assigned() + siteB.assigned();
        double singleRate = big.rate();
        int singleAnts = big.assigned();
        helper.assertTrue(splitAnts == singleAnts && splitAnts > 400, "Both colonies should put the same ants to work: " + splitAnts + " / " + singleAnts + " (sites " + siteA.assigned() + "+" + siteB.assigned()
                + ", found " + split.colony().layout().of(StructureKind.FORAGING_SITE).size() + " and " + single.colony().layout().of(StructureKind.FORAGING_SITE).size()
                + ", big capacity " + big.capacity() + ", workers " + (long) split.colony().workers() + "/" + (long) single.colony().workers() + ")");
        helper.assertTrue(Math.abs(splitRate - singleRate) < 1e-6 * Math.max(1, singleRate),
                "Two sites (" + splitRate + "/min) should find exactly as much as one (" + singleRate + "/min)");
        helper.succeed();
    }

    /**
     * A Queen carries at most 3 beneficial mutations: daughters never get more, the lab won't add a fourth, and a
     * Reroll never takes a trait above level 6 (the top levels must be upgraded).
     */
    private static void geneticLimits(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Genome mother = Genetics.wildQueen(COMMON, RandomSource.create(1)).genome()
                .withMutation(AntColonies.id("crystal_genetics")).withMutation(AntColonies.id("energized")).withMutation(AntColonies.id("golden_touch"));
        Genome mate = Genetics.wildQueen(COMMON, RandomSource.create(2)).genome()
                .withMutation(AntColonies.id("iron_jaws")).withMutation(AntColonies.id("keen_senses")).withMutation(AntColonies.id("fertile"));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            Genome daughter = Genetics.daughter(mother, mate, COMMON, random, registries, com.antcolonies.colony.species.Category.BASE, 4.0);
            helper.assertTrue(Genetics.beneficial(daughter.mutations(), registries) <= 3,
                    "A daughter got " + Genetics.beneficial(daughter.mutations(), registries) + " beneficial mutations");
        }
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.GENETIC_LAB.get());
        GeneticLabBlockEntity lab = helper.getBlockEntity(pos, GeneticLabBlockEntity.class);
        lab.setItem(GeneticLabBlockEntity.SLOT_CATALYST, new ItemStack(Items.IRON_BLOCK));
        QueenData full = new QueenData(COMMON, mother);
        helper.assertTrue(lab.requirement(GeneticLabBlockEntity.Operation.MUTATE, full, registries) == null,
                "The lab should not add a fourth beneficial mutation");
        QueenData two = new QueenData(COMMON, mother.withoutMutation(AntColonies.id("golden_touch")));
        helper.assertTrue(lab.requirement(GeneticLabBlockEntity.Operation.MUTATE, two, registries) != null,
                "With two beneficial mutations the lab should add Iron Jaws");
        lab.selectTrait(Trait.WORKER_PRODUCTIVITY.ordinal());
        QueenData six = new QueenData(COMMON, mother.withLevel(Trait.WORKER_PRODUCTIVITY, 6));
        QueenData five = new QueenData(COMMON, mother.withLevel(Trait.WORKER_PRODUCTIVITY, 5));
        helper.assertTrue(lab.requirement(GeneticLabBlockEntity.Operation.REROLL, six, registries) == null, "No Reroll from level 6");
        helper.assertTrue(lab.requirement(GeneticLabBlockEntity.Operation.REROLL, five, registries) != null, "A Reroll from level 5");
        helper.succeed();
    }


    /** rareFindMultiplier scales only the rare finds: at 0.5 about half as many diamonds come up, at 0 none. */
    private static void rareFindMultiplier(GameTestHelper helper) {
        var outputs = ModRegistries.species(helper.getLevel().registryAccess(), AntColonies.id("diamond")).production().outputs();
        double before = AntConfig.get(AntConfig.RARE_FIND_MULTIPLIER);
        try {
            int[] rare = new int[3];
            double[] multipliers = {1.0, 0.5, 0.0};
            for (int m = 0; m < multipliers.length; m++) {
                AntConfig.RARE_FIND_MULTIPLIER.set(multipliers[m]);
                RandomSource random = RandomSource.create(11);
                for (int i = 0; i < 40_000; i++) {
                    SpeciesOutput picked = ColonySimulator.pickOutput(outputs, 4, 1.0, random);
                    if (picked != null && picked.special()) {
                        rare[m]++;
                    }
                }
            }
            helper.assertTrue(rare[0] > 500, "Diamonds should come up at the normal setting: " + rare[0]);
            double ratio = rare[1] / (double) rare[0];
            helper.assertTrue(ratio > 0.42 && ratio < 0.58, "At 0.5 about half as many diamonds should come up, got " + ratio);
            helper.assertTrue(rare[2] == 0, "At 0 no diamonds should come up, got " + rare[2]);
        } finally {
            AntConfig.RARE_FIND_MULTIPLIER.set(before);
        }
        helper.succeed();
    }


    /** The advancements load, and the colony trigger awards exactly the ones the colony meets. */
    private static void advancementsWork(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String id : List.of("root", "catch_ants", "catch_queen", "first_colony", "stage_2", "stage_4", "evolution", "hybrid", "generator", "lab",
                "perfect_trait", "netherite", "ender", "giant_anthill", "chitin_set", "guard_ant")) {
            helper.assertTrue(server.getAdvancements().get(AntColonies.id(id)) != null, "Advancement " + id + " didn't load");
        }
        TerrariumBlockEntity terrarium = terrarium(helper, new BlockPos(1, 1, 1), COMMON, 50, 0, 1000);
        var player = helper.makeMockServerPlayerInLevel();
        var species = ModRegistries.species(helper.getLevel().registryAccess(), COMMON);
        com.antcolonies.core.advancement.ModTriggers.COLONY.get().trigger(player, terrarium.colony(), species);
        helper.assertTrue(done(player, "first_colony"), "A colony with a Queen should give Home Sweet Home");
        helper.assertTrue(!done(player, "stage_4"), "A young colony must not give Metropolis");
        helper.assertTrue(!done(player, "perfect_trait"), "A wild Queen has no level-10 trait");
        QueenData queen = terrarium.colony().queen();
        terrarium.colony().setQueen(queen.withGenome(queen.genome().withLevel(Trait.EGG_PRODUCTION, 10)));
        com.antcolonies.core.advancement.ModTriggers.COLONY.get().trigger(player, terrarium.colony(), species);
        helper.assertTrue(done(player, "perfect_trait"), "A level-10 trait should give Perfection");
        helper.succeed();
    }

    private static boolean done(net.minecraft.server.level.ServerPlayer player, String id) {
        var holder = player.level().getServer().getAdvancements().get(AntColonies.id(id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }


    /**
     * Redstone: a powered job site gets no ants (status Paused) and its ants go elsewhere; a Terrarium's comparator reads
     * its food level; a powered Generator doesn't burn.
     */
    private static void redstoneControl(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = terrarium(helper, center, COMMON, 50, 0, 1000);
        List<StructureBlockEntity> sites = ring(helper, center, StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE);
        StructureBlockEntity powered = sites.get(0);
        StructureBlockEntity open = sites.get(1);
        // West of the north chamber, where no chamber is.
        helper.setBlock(places(center).get(0).west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        step(helper, terrarium, 1 / 60.0);
        helper.assertTrue(powered.assigned() == 0 && ColonyStatus.PAUSED.in(powered.siteStatus()), "The powered site should be paused, has "
                + powered.assigned() + " ants");
        helper.assertTrue(open.assigned() > 0, "The other site should get the ants");
        int reading = terrarium.comparatorSignal();
        int expected = com.antcolonies.content.block.ComparatorSource.fill(terrarium.colony().food(), terrarium.colony().stats().foodCapacity());
        helper.assertTrue(reading == expected && reading > 0, "The Terrarium should read its food level: " + reading + " vs " + expected);
        // The Generator: fuel waits while it is powered.
        BlockPos generatorPos = new BlockPos(6, 1, 6);
        helper.setBlock(generatorPos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(generatorPos, com.antcolonies.content.blockentity.GeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 1));
        helper.setBlock(generatorPos.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(generator.getItem(0).getCount() == 1 && generator.energy() == 0, "A powered Generator should not burn");
            helper.succeed();
        });
    }


    /**
     * Chitin: a full set makes wild soldiers leave the player alone and gives the digging bonus; one piece short does
     * neither. The gear is iron level and repairs with Chitin Plates.
     */
    private static void chitinGear(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        WildAnt soldier = helper.spawn(ModEntities.WILD_ANT.get(), new BlockPos(2, 1, 2));
        soldier.setCaste(com.antcolonies.colony.species.Caste.SOLDIER);
        double baseSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.CHITIN_HELMET.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        helper.assertTrue(!com.antcolonies.content.item.ChitinGear.fullSet(player) && !WildAnt.ignores(player), "Three pieces are not a set");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        helper.assertTrue(com.antcolonies.content.item.ChitinGear.fullSet(player), "Four pieces should be the full set");
        helper.assertTrue(WildAnt.ignores(player) && !soldier.canAttack(player), "A wild soldier must not attack a player in full Chitin");
        com.antcolonies.content.item.ChitinGear.updateSetBonus(player);
        double setSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        helper.assertTrue(Math.abs(setSpeed - baseSpeed * (1 + com.antcolonies.content.item.ChitinGear.SET_DIG_BONUS)) < 1e-6,
                "The full set should dig faster: " + baseSpeed + " -> " + setSpeed);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
        com.antcolonies.content.item.ChitinGear.updateSetBonus(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED) == baseSpeed,
                "Taking a piece off should end the bonus");
        ItemStack chestplate = new ItemStack(ModItems.CHITIN_CHESTPLATE.get());
        helper.assertTrue(chestplate.isValidRepairItem(new ItemStack(ModItems.CHITIN_PLATE.get())), "Chitin Plates should repair Chitin gear");
        helper.assertTrue(new ItemStack(ModItems.CHITIN_PICKAXE.get()).isCorrectToolForDrops(net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState()),
                "A Chitin Pickaxe should mine diamond ore like iron");
        helper.assertTrue(new ItemStack(ModItems.CHITIN_PICKAXE.get()).is(net.minecraft.tags.ItemTags.PICKAXES), "The pickaxe should be in #pickaxes");
        helper.succeed();
    }

    /**
     * Guard Ants: a Rally Pheromone on a hunter colony's Terrarium costs it hunters and gives a Guard Ant as strong as the
     * Queen's Hunting Strength; let out, it belongs to the player and has the stats of its tube. Other colonies give none.
     */
    private static void guardAnts(GameTestHelper helper) {
        int cost = AntConfig.get(AntConfig.GUARD_COST);
        TerrariumBlockEntity common = terrarium(helper, new BlockPos(1, 1, 6), COMMON, 1000, 0, 1000);
        helper.assertFalse(com.antcolonies.content.block.TerrariumBlock.recruitGuard(common, helper.makeMockServerPlayerInLevel(),
                new ItemStack(ModItems.RALLY_PHEROMONE.get())), "Only hunter colonies should give Guard Ants");
        TerrariumBlockEntity terrarium = terrarium(helper, new BlockPos(1, 1, 1), AntColonies.id("army"), cost - 1, 0, 1000);
        QueenData queen = terrarium.colony().queen();
        terrarium.colony().setQueen(queen.withGenome(queen.genome().withLevel(Trait.SOLDIER_STRENGTH, 7)));
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack pheromone = new ItemStack(ModItems.RALLY_PHEROMONE.get(), 2);
        helper.assertTrue(!com.antcolonies.content.block.TerrariumBlock.recruitGuard(terrarium, player, pheromone) && pheromone.getCount() == 2,
                "Too few hunters: no Guard Ant, the pheromone kept");
        terrarium.colony().setPopulation(0, 0, cost);
        helper.assertTrue(com.antcolonies.content.block.TerrariumBlock.recruitGuard(terrarium, player, pheromone), "Enough hunters should give a Guard Ant");
        helper.assertTrue(pheromone.getCount() == (player.hasInfiniteMaterials() ? 2 : 1) && terrarium.colony().workers() == 0, "The hunters and one pheromone should be used: "
                + terrarium.colony().workers() + " left");
        ItemStack tube = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.GUARD_ANT.get())) {
                tube = player.getInventory().getItem(slot);
            }
        }
        com.antcolonies.content.item.GuardData data = tube.get(ModDataComponents.GUARD.get());
        helper.assertTrue(data != null && data.strength() == 7 && data.species().equals(AntColonies.id("army")), "The tube should hold a strength-7 Army Ant guard: " + data);
        com.antcolonies.content.entity.GuardAnt ant = helper.spawn(ModEntities.GUARD_ANT.get(), new BlockPos(3, 1, 3));
        ant.tame(player);
        ant.setup(data);
        helper.assertTrue(ant.isOwnedBy(player) && ant.getMaxHealth() == com.antcolonies.content.entity.GuardAnt.maxHealth(7)
                && ant.getHealth() == ant.getMaxHealth(), "Let out, it should be the player's, at full health " + ant.getMaxHealth());
        helper.assertTrue(ant.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == com.antcolonies.content.entity.GuardAnt.damage(7),
                "Its bite should come from Soldier Strength");
        ant.setHealth(10);
        helper.assertTrue(ant.toData().health() == 10, "Picked up, it should keep its health");
        helper.succeed();
    }

    /**
     * The giant anthill builds (inside the test's padding): the shaft, a chest and a barrel with its loot, and its ants
     * as defenders: a Queen and soldiers that guard it and stay.
     */
    private static void giantAnthill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        helper.assertTrue(structures.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                AntColonies.id("giant_anthill"))).isPresent(), "The giant_anthill structure didn't load");
        // In the middle of the test's loaded area (20 blocks of padding), so its ants are in ticking chunks.
        BlockPos center = helper.absolutePos(new BlockPos(10, 0, 10));
        // Solid ground to dig into.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -20, -10), center.offset(10, 0, 10))) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        }
        com.antcolonies.content.worldgen.AnthillPiece piece = new com.antcolonies.content.worldgen.AnthillPiece(center);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), piece.getBoundingBox(),
                net.minecraft.world.level.ChunkPos.containing(center), center);
        helper.assertTrue(level.getBlockState(center.above(1)).isAir() && level.getBlockState(center.below(1)).isAir(), "The shaft should go down");
        int chests = 0;
        int barrels = 0;
        int lining = 0;
        for (BlockPos pos : BlockPos.betweenClosed(piece.getBoundingBox().minX(), piece.getBoundingBox().minY(), piece.getBoundingBox().minZ(),
                piece.getBoundingBox().maxX(), piece.getBoundingBox().maxY(), piece.getBoundingBox().maxZ())) {
            var state = level.getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.CHEST) || state.is(net.minecraft.world.level.block.Blocks.BARREL)) {
                var container = (net.minecraft.world.RandomizableContainer) level.getBlockEntity(pos);
                helper.assertTrue(com.antcolonies.content.worldgen.AnthillPiece.LOOT.equals(container.getLootTable()), "A container without the anthill loot");
                if (state.is(net.minecraft.world.level.block.Blocks.CHEST)) {
                    chests++;
                } else {
                    barrels++;
                }
            } else if (state.is(net.minecraft.world.level.block.Blocks.PACKED_MUD)) {
                lining++;
            }
        }
        helper.assertTrue(chests == 1 && barrels == 1, "One chest and one barrel, found " + chests + " and " + barrels);
        helper.assertTrue(lining > 30, "The tunnels should have dug walls, found " + lining);
        helper.runAfterDelay(20, () -> {
            var ants = level.getEntitiesOfClass(WildAnt.class, new net.minecraft.world.phys.AABB(center).inflate(14, 22, 14));
        long soldiers = ants.stream().filter(a -> a.guardian() && a.caste() == com.antcolonies.colony.species.Caste.SOLDIER).count();
        long queens = ants.stream().filter(a -> a.caste() == com.antcolonies.colony.species.Caste.QUEEN).count();
        helper.assertTrue(soldiers >= 5 && queens == 1 && ants.stream().allMatch(WildAnt::isPersistenceRequired),
                "Defenders: " + soldiers + " soldiers, " + queens + " Queens of " + ants.size() + " ants");
            ants.forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }


    /**
     * A colony left to the game's own ticking (no rescan or step by hand, as in a real world): it must find its
     * structures, link them, grow to stage 2 and try evolving with the iron in its feed slot.
     */
    private static void colonyRunsByItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, AntColonies.id("stone"), 400, 0, 5000);
        StructureBlockEntity site = ring(helper, pos, StructureKind.NEST_CHAMBER, StructureKind.NEST_CHAMBER, StructureKind.FORAGING_SITE,
                StructureKind.STORAGE).get(2);
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(Items.IRON_INGOT, 64));
        helper.succeedWhen(() -> {
            helper.assertTrue(site.isLinked(), "The Foraging Chamber was never linked: the Terrarium didn't look at its ring");
            helper.assertTrue(site.assigned() > 0, "The linked Foraging Chamber has no workers");
            helper.assertTrue(terrarium.colony().stage() >= 2, "The colony didn't reach stage 2: " + terrarium.colony().stage());
            ItemStack feed = terrarium.getItem(TerrariumBlockEntity.SLOT_FEED);
            helper.assertTrue(feed.getCount() < 64 || !feed.is(Items.IRON_INGOT),
                    "No evolution attempt used any iron: the Terrarium never tried");
        });
    }

    /**
     * Chambers are additions: a Terrarium alone lays eggs and raises ants. A chamber joins when it touches the Terrarium
     * or a joined chamber (above, below or beside it), and one that touches neither stays out.
     */
    private static void chambersJoinByTouching(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 10, 0, 5000);
        for (int i = 0; i < 10; i++) {
            step(helper, terrarium, 0.5);
        }
        Colony colony = terrarium.colony();
        helper.assertTrue(colony.layout().size() == 0, "Nothing should have joined yet");
        helper.assertTrue(colony.stats().eggsLaid() > 0 && colony.adults() > 10, "A Terrarium alone should lay eggs and raise ants: "
                + colony.adults() + " adults");
        // A Foraging Site against the Terrarium, a Storage on top of the site, a Nest Chamber against the Storage's side.
        StructureBlockEntity site = structure(helper, pos.east(), StructureKind.FORAGING_SITE);
        StructureBlockEntity storage = structure(helper, pos.east().above(), StructureKind.STORAGE);
        StructureBlockEntity nest = structure(helper, pos.east().above().east(), StructureKind.NEST_CHAMBER);
        // Two blocks from anything of the colony: it stays out.
        StructureBlockEntity apart = structure(helper, pos.west().west(), StructureKind.FORAGING_SITE);
        step(helper, terrarium, 1 / 60.0);
        helper.assertTrue(colony.layout().size() == 3, "The three touching chambers should join, found " + colony.layout().size());
        helper.assertTrue(site.isLinked() && storage.isLinked() && nest.isLinked(), "The joined chambers should be linked");
        helper.assertFalse(apart.isLinked(), "A chamber touching nothing of the colony should stay out");
        helper.assertTrue(site.assigned() > 0, "The Foraging Site should get workers");
        // The Terrarium's block state shows the joined side (its frame opens there).
        helper.assertTrue(helper.getBlockState(pos).getValue(com.antcolonies.content.block.ColonyPartBlock.CONNECTED.get(net.minecraft.core.Direction.EAST)),
                "The Terrarium should show it is joined on its east side");
        helper.succeed();
    }


    /**
     * Moving a colony goes through Test Tubes: an empty tube in the Queen slot takes the Queen out, empty tubes in the feed
     * slot fill with workers, then soldiers. A broken Terrarium keeps nothing. The tubes start the colony again elsewhere.
     */
    private static void colonyMoves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, from, COMMON, 300, 40, 900);
        Colony colony = terrarium.colony();
        // The Queen out.
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, new ItemStack(ModItems.TEST_TUBE.get()));
        terrarium.processSlots(level);
        ItemStack queenTube = terrarium.removeItemNoUpdate(TerrariumBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(colony.queen() == null && queenTube.has(ModDataComponents.QUEEN.get()), "An empty tube in the Queen slot should take her out: "
                + queenTube);
        // The ants out: workers, then soldiers, a tube each.
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(ModItems.TEST_TUBE.get(), 3));
        List<ItemStack> tubes = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            terrarium.processSlots(level);
            ItemStack out = terrarium.removeItemNoUpdate(TerrariumBlockEntity.SLOT_OUTPUT);
            if (!out.isEmpty()) {
                tubes.add(out);
            }
        }
        helper.assertTrue(tubes.size() == 1 && colony.workers() == 0, "One tube should hold all the workers (the 40 soldiers joined them): " + tubes
                + ", left " + colony.workers());
        helper.assertTrue(terrarium.getItem(TerrariumBlockEntity.SLOT_FEED).getCount() == 2, "The other tubes should stay empty in the feed slot");
        // Broken, a Terrarium keeps no colony.
        BlockPos fromAbs = helper.absolutePos(from);
        terrarium.colony().setPopulation(0, 0, 50);
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(fromAbs), level, fromAbs, terrarium);
        helper.assertTrue(drops.stream().noneMatch(stack -> stack.has(ModDataComponents.COLONY.get())), "A broken Terrarium must not keep its colony");
        // The tubes start the colony again in a new Terrarium.
        BlockPos to = new BlockPos(6, 1, 1);
        helper.setBlock(to, ModBlocks.TERRARIUM.get());
        TerrariumBlockEntity moved = helper.getBlockEntity(to, TerrariumBlockEntity.class);
        moved.setItem(TerrariumBlockEntity.SLOT_QUEEN, queenTube);
        moved.processSlots(level);
        for (ItemStack tube : tubes) {
            moved.removeItemNoUpdate(TerrariumBlockEntity.SLOT_OUTPUT);
            moved.setItem(TerrariumBlockEntity.SLOT_FEED, tube);
            moved.processSlots(level);
        }
        Colony again = moved.colony();
        helper.assertTrue(again.queen() != null && again.workers() == 340,
                "The new Terrarium should hold the Queen and 340 workers: " + again.queen() + ", " + again.workers());
        helper.succeed();
    }

    /**
     * A Terrarium's upgrades must survive the updates sent to players: loading an update clears the upgrade slots, so
     * an update without them emptied the slots on every client while the screen was open.
     */
    private static void terrariumUpdateKeepsUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = terrarium(helper, pos, COMMON, 50, 0, 1000);
        ItemStack upgrade = new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 2));
        terrarium.upgrades().setItem(1, upgrade.copy());
        var registries = helper.getLevel().registryAccess();
        var tag = terrarium.getUpdateTag(registries);
        // What a client does with the update: load it into its own copy of the Terrarium.
        TerrariumBlockEntity client = new TerrariumBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        client.upgrades().setItem(1, upgrade.copy());
        client.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));
        helper.assertTrue(ItemStack.isSameItemSameComponents(client.upgrades().getItem(1), upgrade),
                "After an update the client's upgrade slot holds " + client.upgrades().getItem(1) + " instead of the upgrade");
        helper.succeed();
    }

    /**
     * Chambers between two Terrariums go to the nearer one; one exactly as near to both joins neither and says so. Both
     * colonies keep working. With the second Terrarium gone, the first takes them all.
     */
    private static void twoColonies(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 2);
        BlockPos b = new BlockPos(6, 1, 2);
        TerrariumBlockEntity first = terrarium(helper, a, COMMON, 50, 0, 1000);
        TerrariumBlockEntity second = terrarium(helper, b, COMMON, 50, 0, 1000);
        StructureBlockEntity near = structure(helper, new BlockPos(3, 1, 2), StructureKind.FORAGING_SITE);
        StructureBlockEntity middle = structure(helper, new BlockPos(4, 1, 2), StructureKind.FORAGING_SITE);
        StructureBlockEntity far = structure(helper, new BlockPos(5, 1, 2), StructureKind.FORAGING_SITE);
        step(helper, first, 0.05);
        step(helper, second, 0.05);
        var layout = first.colony().layout();
        helper.assertTrue(layout.size() == 1 && layout.of(StructureKind.FORAGING_SITE).contains(near.getBlockPos()),
                "The first colony should take only the chamber next to it, took " + layout.all());
        helper.assertTrue(layout.contested().contains(middle.getBlockPos()), "The middle chamber should be shared: " + layout.contested());
        helper.assertTrue(second.colony().layout().of(StructureKind.FORAGING_SITE).contains(far.getBlockPos()), "The second colony should take the far chamber");
        helper.assertTrue(middle.isContested() && !middle.isLinked(), "The middle chamber should say it joins neither");
        helper.assertTrue(near.assigned() > 0 && far.assigned() > 0, "Both colonies should keep working");
        helper.setBlock(b, net.minecraft.world.level.block.Blocks.AIR);
        step(helper, first, 0.05);
        helper.assertTrue(first.colony().layout().size() == 3, "Without the second Terrarium the first should take all three: "
                + first.colony().layout().all());
        helper.succeed();
    }

    /** Catching up after being unloaded never exceeds the configured allowance. */
    private static void offline(GameTestHelper helper) {
        double base = AntConfig.get(AntConfig.OFFLINE_CATCH_UP_MINUTES);
        long tenHours = 20L * 60 * 60 * 10;
        helper.assertTrue(TerrariumBlockEntity.catchUpMinutes(tenHours, 0) == base, "Without upgrades the catch-up is the base allowance");
        helper.assertTrue(TerrariumBlockEntity.catchUpMinutes(tenHours, 1) == base + AntConfig.simulationUpgradeMinutes(1), "Tier 1 adds its minutes");
        helper.assertTrue(TerrariumBlockEntity.catchUpMinutes(1200, 3) == 1, "A short absence is caught up exactly");
        helper.succeed();
    }

    /** Tweezers catch a wild ant into an empty Test Tube, keeping its species and caste. */
    private static void tweezers(GameTestHelper helper) {
        WildAnt ant = helper.spawn(ModEntities.WILD_ANT.get(), new BlockPos(1, 1, 1));
        ant.setSpecies(AntColonies.id("desert"));
        ant.setCaste(Caste.SOLDIER);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tweezers = new ItemStack(ModItems.TWEEZERS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tweezers);
        player.getInventory().add(new ItemStack(ModItems.TEST_TUBE.get()));
        var result = ModItems.TWEEZERS.get().interactLivingEntity(tweezers, player, ant, InteractionHand.MAIN_HAND);
        helper.assertTrue(ant.isRemoved(), "The ant should be caught (" + result + ", client " + player.level().isClientSide() + ", tube slot "
                + player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.TEST_TUBE.get())) + ")");
        boolean found = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            AntSample sample = player.getInventory().getItem(slot).get(ModDataComponents.ANTS.get());
            if (sample != null) {
                found = sample.species().equals(AntColonies.id("desert")) && sample.caste() == Caste.WORKER;
            }
        }
        helper.assertTrue(found, "A caught soldier should come as a desert worker (colonies have no soldiers)");
        helper.succeed();
    }

    /** A Queen Test Tube in the Queen slot moves in (her empty tube goes to the output); food in the feed slot becomes colony food. */
    private static void feeding(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.TERRARIUM.get());
        TerrariumBlockEntity terrarium = helper.getBlockEntity(pos, TerrariumBlockEntity.class);
        ItemStack tube = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        tube.set(ModDataComponents.QUEEN.get(), Genetics.wildQueen(AntColonies.id("snow"), RandomSource.create(9)));
        terrarium.setItem(TerrariumBlockEntity.SLOT_QUEEN, tube);
        terrarium.setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(ModItems.ANT_FOOD.get(), 4));
        helper.succeedWhen(() -> {
            Colony colony = terrarium.colony();
            helper.assertTrue(colony.queen() != null && colony.species().equals(AntColonies.id("snow")), "The Queen did not move in");
            helper.assertTrue(terrarium.getItem(TerrariumBlockEntity.SLOT_QUEEN).isEmpty() && terrarium.getItem(TerrariumBlockEntity.SLOT_OUTPUT).is(ModItems.TEST_TUBE.get()),
                    "The empty tube should go to the output, leaving the Queen slot free");
            helper.assertTrue(colony.food() >= 700, "Food was not added: " + colony.food());
        });
    }

    /** Every wild nest feature in the data places its nest on solid ground (world generation uses the same features). */
    private static void nests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        int x = 0;
        for (var entry : ModBlocks.NESTS.entrySet()) {
            BlockPos pos = new BlockPos(x, 1, 1);
            helper.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.DIRT);
            var feature = features.getValue(AntColonies.id(entry.getKey() + "_ant_nest"));
            helper.assertTrue(feature != null, "No feature for the " + entry.getKey() + " nest");
            feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(pos));
            helper.assertBlockPresent(entry.getValue().get(), pos);
            x += 2;
        }
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getValue(AntColonies.id("desert_ant_nest")) != null,
                "The nests need placed features");
        helper.succeed();
    }
}
