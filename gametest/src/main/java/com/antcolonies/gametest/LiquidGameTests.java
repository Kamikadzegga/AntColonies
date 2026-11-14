package com.antcolonies.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Category;
import com.antcolonies.content.block.CentrifugeBlock;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity.Status;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.item.DropletItem;
import com.antcolonies.content.recipe.CentrifugingRecipe;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Game tests of Liquid Ants, their droplets and the Droplet Centrifuge: which species and droplets exist (Water and Lava
 * always; another mod's only with that mod and its fluid), what Liquid Ants do and don't do, and that the Centrifuge
 * never loses a droplet or makes fluid from nothing.
 */
final class LiquidGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, AntColonies.MODID);
    private static final Identifier WATER = AntColonies.id("water");
    private static final Identifier LAVA = AntColonies.id("lava");
    private static final Identifier COMMON = AntColonies.id("common");

    private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> ALL = List.of(
            test("liquid_species_follow_their_fluids", LiquidGameTests::speciesFollowFluids),
            test("liquid_kinds_need_mod_and_fluid", LiquidGameTests::kindsNeedModAndFluid),
            test("liquid_ants_make_only_their_droplets", LiquidGameTests::onlyTheirDroplets),
            test("liquid_ants_do_no_other_job", LiquidGameTests::noOtherJob),
            test("liquid_scales_with_workers_and_food", LiquidGameTests::scalesWithWorkersAndFood),
            test("liquid_food_bonus", LiquidGameTests::foodBonus),
            test("liquid_species_without_its_fluid_waits", LiquidGameTests::missingFluidWaits),
            test("honeypot_fed_water_becomes_water_ants", LiquidGameTests::honeypotBecomesWaterAnts),
            test("droplets_named_and_drawn", LiquidGameTests::dropletsNamedAndDrawn),
            test("centrifuge_spins_droplets", LiquidGameTests::centrifugeSpins),
            test("centrifuge_takes_only_droplets", LiquidGameTests::centrifugeTakesOnlyDroplets),
            test("centrifuge_respects_its_tank", LiquidGameTests::centrifugeRespectsTank),
            test("centrifuge_power_cut_keeps_droplet", LiquidGameTests::centrifugePowerCut),
            test("centrifuge_persists", LiquidGameTests::centrifugePersists),
            test("centrifuge_fluid_out", LiquidGameTests::centrifugeFluidOut),
            test("centrifuge_hands_on_a_bucket_a_tick", LiquidGameTests::centrifugeHandsOnBuckets),
            test("centrifuge_feeds_machinaopus_tank_and_pipes", LiquidGameTests::centrifugeFeedsMachinaOpus));

    private LiquidGameTests() {}

    static void register(IEventBus bus) {
        TESTS.register(bus);
    }

    static void registerTests(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : ALL) {
            event.registerTest(test.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, test.getId()),
                    AntColoniesGameTests.spaced(environment, 400)));
        }
    }

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test(String name, Consumer<GameTestHelper> test) {
        return TESTS.register(name, () -> test);
    }

    // --- Helpers ---

    private static Item droplet(String kind) {
        return BuiltInRegistries.ITEM.getValue(AntColonies.id(kind + "_droplet"));
    }

    /** How many of an item a chamber's own slots hold. */
    private static int count(StructureBlockEntity store, Item item) {
        int count = 0;
        for (int slot = 0; slot < store.getContainerSize(); slot++) {
            if (store.getItem(slot).is(item)) {
                count += store.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** Every kind of item a chamber's own slots hold. */
    private static Set<Item> kinds(StructureBlockEntity store) {
        Set<Item> items = new HashSet<>();
        for (int slot = 0; slot < store.getContainerSize(); slot++) {
            if (!store.getItem(slot).isEmpty()) {
                items.add(store.getItem(slot).getItem());
            }
        }
        return items;
    }

    private static CentrifugeBlockEntity centrifuge(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CENTRIFUGE.get());
        return helper.getBlockEntity(pos, CentrifugeBlockEntity.class);
    }

    /** Runs a body with a config value changed, and puts it back. */
    private static void withFoodChance(double chance, Runnable body) {
        double before = AntConfig.LIQUID_FOOD_CHANCE.get();
        AntConfig.LIQUID_FOOD_CHANCE.set(chance);
        try {
            body.run();
        } finally {
            AntConfig.LIQUID_FOOD_CHANCE.set(before);
        }
    }

    // --- Which species and droplets exist ---

    /**
     * Water and Lava Ants are always there. Every other kind any integration offers exists exactly when its mod is
     * installed and its fluid is in the fluid registry, and then all of it does: the droplet item, the species, its
     * Centrifuge recipe and the evolution into it. Otherwise none of it does. Worked out here from the mod list and the
     * fluid registry alone, so it holds with or without the other mods (CI has none; a dev run has MachinaOpus).
     */
    private static void speciesFollowFluids(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<CentrifugingRecipe> recipes = CentrifugingRecipe.all(helper.getLevel());
        var evolutions = registries.lookupOrThrow(ModRegistries.EVOLUTION).listElements().toList();
        Set<Identifier> liquidSpecies = new HashSet<>();
        int available = 0;
        for (LiquidKind kind : LiquidKinds.offered()) {
            boolean base = kind.mod().equals("minecraft");
            boolean modLoaded = base || ModList.get().isLoaded(kind.mod());
            boolean fluidThere = BuiltInRegistries.FLUID.containsKey(kind.fluid());
            boolean expected = modLoaded && fluidThere;
            String why = kind.name() + " (mod " + kind.mod() + (modLoaded ? " installed" : " missing") + ", fluid " + kind.fluid()
                    + (fluidThere ? " registered" : " missing") + ")";
            helper.assertTrue(kind.available() == expected, "Wrong availability of " + why);
            Item item = BuiltInRegistries.ITEM.getOptional(kind.dropletId()).orElse(null);
            helper.assertTrue((item != null) == expected, "Droplet item of " + why + ": " + item);
            AntSpecies species = ModRegistries.species(registries, kind.speciesId());
            helper.assertTrue((species != null) == expected, "Species of " + why + ": " + (species != null));
            boolean recipe = item != null && recipes.stream().anyMatch(r -> r.ingredient().test(new ItemStack(item)));
            helper.assertTrue(recipe == expected, "Centrifuge recipe of " + why + ": " + recipe);
            boolean evolution = evolutions.stream().anyMatch(e -> e.value().to().equals(kind.speciesId()));
            helper.assertTrue(evolution == expected, "Evolution into " + why + ": " + evolution);
            if (!expected) {
                continue;
            }
            available++;
            liquidSpecies.add(kind.speciesId());
            helper.assertTrue(species.category() == Category.LIQUID && species.makesLiquid(), kind.name() + " is not a Liquid Ant");
            AntSpecies.Liquid liquid = species.liquid().orElseThrow();
            helper.assertTrue(liquid.fluid().equals(kind.fluid()) && liquid.droplet().equals(kind.dropletId()) && liquid.available(),
                    kind.name() + " makes the wrong thing: " + liquid);
            helper.assertTrue(species.look().crop().isPresent(), kind.name() + " has no crop to draw");
            helper.assertTrue(species.production().outputs().isEmpty() && !species.canFarm() && !species.canHunt(),
                    kind.name() + " should make nothing but droplets");
            // Its recipe gives its own fluid.
            CentrifugingRecipe own = CentrifugingRecipe.find(helper.getLevel(), new ItemStack(item));
            helper.assertTrue(own != null && BuiltInRegistries.FLUID.getKey(own.fluid().getFluid()).equals(kind.fluid()) && own.amount() > 0
                    && own.energy() > 0, kind.name() + "'s droplet spins into the wrong fluid");
            helper.assertTrue(item instanceof DropletItem made && made.fluid() == own.fluid().getFluid(), "Droplet of " + kind.name() + " is not a DropletItem of its fluid");
        }
        helper.assertTrue(LiquidKinds.WATER.available() && LiquidKinds.LAVA.available(), "Water and Lava Ants must always be there");
        // The data files' own conditions, on four files made for this (gametest resources): one whose mod, fluid and droplet
        // are all there loads; one each with the fluid, the mod or the droplet missing does not.
        helper.assertTrue(ModRegistries.species(registries, Identifier.fromNamespaceAndPath("antcolonies_test", "conditions_met")) != null,
                "A species whose mod, fluid and droplet all exist did not load");
        for (String missing : new String[] {"missing_fluid", "missing_mod", "missing_both"}) {
            helper.assertTrue(ModRegistries.species(registries, Identifier.fromNamespaceAndPath("antcolonies_test", missing)) == null,
                    "A species loaded although its conditions fail: " + missing);
        }
        helper.assertTrue(LiquidKinds.available().size() == available, "The list of available kinds is off");
        // No Liquid Ant species of this mod exists that isn't one of the kinds.
        registries.lookupOrThrow(ModRegistries.SPECIES).listElements().forEach(entry -> {
            Identifier id = entry.key().identifier();
            if (entry.value().category() == Category.LIQUID && id.getNamespace().equals(AntColonies.MODID)) {
                helper.assertTrue(liquidSpecies.contains(id), "A Liquid Ant species without an available kind: " + id);
            }
        });
        if (!ModList.get().isLoaded("machinaopus")) {
            helper.assertTrue(available == 2 || LiquidKinds.offered().stream().anyMatch(k -> !k.mod().equals("minecraft") && ModList.get().isLoaded(k.mod())),
                    "Without any of the other mods only Water and Lava Ants may exist, found " + available);
        }
        helper.succeed();
    }

    /**
     * The rule itself, on kinds made up for the purpose: a kind needs both its mod and its fluid, and the fluid has to be
     * a real, still one. Offering a kind after items are registered is refused.
     */
    private static void kindsNeedModAndFluid(GameTestHelper helper) {
        helper.assertTrue(LiquidKind.of("a", "minecraft", "minecraft:water").available(), "A base game fluid is always available");
        helper.assertTrue(LiquidKind.of("b", AntColonies.MODID, "minecraft:lava").available(), "Mod installed and fluid registered: available");
        helper.assertTrue(!LiquidKind.of("c", AntColonies.MODID, "antcolonies:no_such_fluid").available(), "Mod installed but fluid missing: not available");
        helper.assertTrue(!LiquidKind.of("d", "no_such_mod", "minecraft:water").available(), "Fluid registered but mod missing: not available");
        helper.assertTrue(!LiquidKind.of("e", "no_such_mod", "no_such_mod:nothing").available(), "Neither: not available");
        helper.assertTrue(!LiquidKind.of("f", "minecraft", "minecraft:flowing_water").available(), "A flowing fluid is not one a tank holds");
        helper.assertTrue(!LiquidKind.of("g", "minecraft", "minecraft:empty").available(), "The empty fluid is no fluid");
        boolean refused = false;
        try {
            LiquidKinds.offer(LiquidKind.of("too_late", "minecraft", "minecraft:water"));
        } catch (IllegalStateException e) {
            refused = true;
        }
        helper.assertTrue(refused, "A kind offered after registration must be refused");
        helper.assertTrue(LiquidKinds.byName("too_late") == null && BuiltInRegistries.ITEM.getOptional(AntColonies.id("too_late_droplet")).isEmpty(),
                "The late kind left something behind");
        helper.succeed();
    }

    // --- What Liquid Ants do ---

    /**
     * Water Ants at a Liquid Chamber give Water Droplets and nothing else, Lava Ants Lava Droplets and nothing else:
     * into the Storage, or the chamber's own store when there is none.
     */
    private static void onlyTheirDroplets(GameTestHelper helper) {
        withFoodChance(0, () -> {
            BlockPos waterPos = new BlockPos(1, 1, 1);
            TerrariumBlockEntity water = AntColoniesGameTests.terrarium(helper, waterPos, WATER, 250, 0, 2000);
            StructureBlockEntity chamber = AntColoniesGameTests.structure(helper, waterPos.east(), StructureKind.LIQUID_CHAMBER);
            StructureBlockEntity storage = AntColoniesGameTests.structure(helper, waterPos.south(), StructureKind.STORAGE);
            for (int i = 0; i < 4; i++) {
                AntColoniesGameTests.step(helper, water, 1);
            }
            helper.assertTrue(chamber.assigned() > 0, "Water Ants should work at the Liquid Chamber");
            helper.assertTrue(chamber.rate() > 0, "The Liquid Chamber shows no droplet rate");
            helper.assertTrue(count(storage, droplet("water")) > 0, "No Water Droplets in the Storage");
            helper.assertTrue(kinds(storage).equals(Set.of(droplet("water"))), "Water Ants made something else: " + kinds(storage));
            helper.assertTrue(kinds(chamber).isEmpty(), "With a Storage the chamber's own store stays empty: " + kinds(chamber));
            helper.assertTrue(ColonyStatus.WORKING.in(water.colony().status()), "The colony should count as working");

            // Lava Ants, with no Storage: their droplets wait in the chamber.
            BlockPos lavaPos = new BlockPos(9, 1, 9);
            TerrariumBlockEntity lava = AntColoniesGameTests.terrarium(helper, lavaPos, LAVA, 250, 0, 2000);
            StructureBlockEntity lavaChamber = AntColoniesGameTests.structure(helper, lavaPos.east(), StructureKind.LIQUID_CHAMBER);
            for (int i = 0; i < 6; i++) {
                AntColoniesGameTests.step(helper, lava, 1);
            }
            helper.assertTrue(count(lavaChamber, droplet("lava")) > 0, "No Lava Droplets in the chamber's store");
            helper.assertTrue(kinds(lavaChamber).equals(Set.of(droplet("lava"))), "Lava Ants made something else: " + kinds(lavaChamber));
            helper.assertTrue(lavaChamber.rate() < chamber.rate(), "Lava should come slower than water: " + lavaChamber.rate() + " vs " + chamber.rate());
        });
        helper.succeed();
    }

    /**
     * Liquid Ants leave every other job site alone (foraging, farming, hunting, expeditions), and no other ants work at a
     * Liquid Chamber.
     */
    private static void noOtherJob(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, pos, WATER, 600, 0, 2000);
        StructureBlockEntity foraging = AntColoniesGameTests.structure(helper, pos.east(), StructureKind.FORAGING_SITE);
        StructureBlockEntity farm = AntColoniesGameTests.structure(helper, pos.west(), StructureKind.HARVESTING_SITE);
        StructureBlockEntity hunt = AntColoniesGameTests.structure(helper, pos.north(), StructureKind.HUNTING_GROUND);
        StructureBlockEntity nether = AntColoniesGameTests.structure(helper, pos.above(), StructureKind.NETHER_FORAGING_SITE);
        StructureBlockEntity chamber = AntColoniesGameTests.structure(helper, pos.south(), StructureKind.LIQUID_CHAMBER);
        for (int i = 0; i < 3; i++) {
            AntColoniesGameTests.step(helper, terrarium, 1);
        }
        for (StructureBlockEntity site : List.of(foraging, farm, hunt, nether)) {
            helper.assertTrue(site.assigned() == 0, "Liquid Ants went to a " + site.kind().getSerializedName());
            helper.assertTrue(ColonyStatus.INCOMPATIBLE_SPECIES.in(site.siteStatus()), site.kind().getSerializedName() + " should say these are the wrong ants");
            helper.assertTrue(site.isEmpty(), "A " + site.kind().getSerializedName() + " of Liquid Ants found something");
        }
        helper.assertTrue(chamber.assigned() > 0 && count(chamber, droplet("water")) > 0, "The Liquid Chamber should be the one job that runs");
        helper.assertTrue(kinds(chamber).stream().allMatch(item -> item == droplet("water") || item == ModItems.ANT_FOOD.get()),
                "Liquid Ants brought back finds: " + kinds(chamber));

        // Common Ants at a Liquid Chamber: none go, nothing comes of it.
        BlockPos commonPos = new BlockPos(10, 1, 10);
        TerrariumBlockEntity common = AntColoniesGameTests.terrarium(helper, commonPos, COMMON, 300, 0, 2000);
        StructureBlockEntity idle = AntColoniesGameTests.structure(helper, commonPos.east(), StructureKind.LIQUID_CHAMBER);
        for (int i = 0; i < 3; i++) {
            AntColoniesGameTests.step(helper, common, 1);
        }
        helper.assertTrue(idle.assigned() == 0 && idle.isEmpty(), "Common Ants worked a Liquid Chamber");
        helper.assertTrue(ColonyStatus.INCOMPATIBLE_SPECIES.in(idle.siteStatus()), "The Liquid Chamber should say it needs Liquid Ants");
        helper.succeed();
    }

    /** More Liquid Ants make more droplets; a colony short of food makes fewer, and a starving one fewer still. */
    private static void scalesWithWorkersAndFood(GameTestHelper helper) {
        // With the rare Ant Food bonus off: a lucky Ant Food would feed the colony that is meant to run out.
        withFoodChance(0, () -> scales(helper));
        helper.succeed();
    }

    private static void scales(GameTestHelper helper) {
        BlockPos smallPos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity small = AntColoniesGameTests.terrarium(helper, smallPos, WATER, 60, 0, 2000);
        StructureBlockEntity smallChamber = AntColoniesGameTests.structure(helper, smallPos.east(), StructureKind.LIQUID_CHAMBER);
        BlockPos bigPos = new BlockPos(9, 1, 9);
        TerrariumBlockEntity big = AntColoniesGameTests.terrarium(helper, bigPos, WATER, 250, 0, 2000);
        StructureBlockEntity bigChamber = AntColoniesGameTests.structure(helper, bigPos.east(), StructureKind.LIQUID_CHAMBER);
        for (int i = 0; i < 3; i++) {
            AntColoniesGameTests.step(helper, small, 1);
            AntColoniesGameTests.step(helper, big, 1);
        }
        helper.assertTrue(bigChamber.assigned() > smallChamber.assigned(), "The bigger colony should have more ants at work");
        helper.assertTrue(bigChamber.rate() > smallChamber.rate() * 2, "Four times the ants should make well over twice the droplets: "
                + smallChamber.rate() + " -> " + bigChamber.rate());
        helper.assertTrue(count(bigChamber, droplet("water")) > count(smallChamber, droplet("water")), "More ants, but no more droplets");
        // Food: the same colony with its food gone.
        double fed = bigChamber.rate();
        big.colony().setFood(0);
        for (int i = 0; i < 8; i++) {
            AntColoniesGameTests.step(helper, big, 1);
        }
        helper.assertTrue(ColonyStatus.STARVING.in(big.colony().status()) || ColonyStatus.NO_FOOD.in(big.colony().status()), "The colony should be out of food");
        helper.assertTrue(bigChamber.rate() < fed * 0.6, "A colony without food should make far fewer droplets: " + fed + " -> " + bigChamber.rate());
        // And they eat like other workers: food goes down while they work (with the rare Ant Food bonus off, which
        // would otherwise now and then put food back).
        small.colony().setFood(500);
        AntColoniesGameTests.step(helper, small, 5);
        helper.assertTrue(small.colony().food() < 500, "Liquid Ants should eat: food " + small.colony().food());
    }

    /** The rare extra: with the chance at 1 every droplet comes with an Ant Food, with it at 0 none ever does. */
    private static void foodBonus(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        // Food room full, so the colony doesn't eat the Ant Food out of its own stores while we count.
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, pos, WATER, 250, 0, 1_000_000);
        StructureBlockEntity chamber = AntColoniesGameTests.structure(helper, pos.east(), StructureKind.LIQUID_CHAMBER);
        withFoodChance(0, () -> {
            for (int i = 0; i < 3; i++) {
                AntColoniesGameTests.step(helper, terrarium, 1);
            }
        });
        helper.assertTrue(count(chamber, droplet("water")) > 0, "No droplets");
        helper.assertTrue(count(chamber, ModItems.ANT_FOOD.get()) == 0, "Ant Food with the chance at 0");
        withFoodChance(1, () -> AntColoniesGameTests.step(helper, terrarium, 1));
        helper.assertTrue(count(chamber, ModItems.ANT_FOOD.get()) > 0, "No Ant Food with the chance at 1");
        helper.assertTrue(AntConfig.LIQUID_FOOD_CHANCE.getDefault() <= 0.01, "The default chance should be very small: " + AntConfig.LIQUID_FOOD_CHANCE.getDefault());
        helper.succeed();
    }

    /**
     * How Water Ants are had: a Honeypot Queen fed buckets of water evolves (within the guaranteed number of attempts),
     * and a bucket fed in the Terrarium comes back empty in its output slot.
     */
    private static void honeypotBecomesWaterAnts(GameTestHelper helper) {
        Identifier honeypot = AntColonies.id("honeypot");
        var registries = helper.getLevel().registryAccess();
        // The rule, attempt by attempt.
        BlockPos pos = new BlockPos(1, 1, 1);
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, pos, honeypot, 400, 0, 5000);
        AntColoniesGameTests.structure(helper, pos.east(), StructureKind.NEST_CHAMBER);
        AntColoniesGameTests.step(helper, terrarium, 0.05);
        helper.assertTrue(terrarium.colony().stage() >= 2, "The colony should be stage 2, is " + terrarium.colony().stage());
        helper.assertTrue(com.antcolonies.colony.evolution.EvolutionLogic.find(registries, terrarium.colony(), new ItemStack(Items.LAVA_BUCKET)) != null
                        && com.antcolonies.colony.evolution.EvolutionLogic.find(registries, terrarium.colony(), new ItemStack(Items.MILK_BUCKET)) == null,
                "A lava bucket should be an evolution material of Honeypot Ants, a milk bucket not");
        var outcome = com.antcolonies.colony.evolution.EvolutionLogic.Outcome.NONE;
        int attempts = 0;
        while (outcome != com.antcolonies.colony.evolution.EvolutionLogic.Outcome.EVOLVED && attempts < 64) {
            outcome = com.antcolonies.colony.evolution.EvolutionLogic.attempt(registries, terrarium.colony(), new ItemStack(Items.WATER_BUCKET), 0,
                    helper.getLevel().dimension(), net.minecraft.util.RandomSource.create(attempts)).outcome();
            attempts++;
        }
        helper.assertTrue(outcome == com.antcolonies.colony.evolution.EvolutionLogic.Outcome.EVOLVED && attempts <= 25,
                "No Water Ants within the guaranteed attempts: " + outcome + " after " + attempts);
        helper.assertTrue(terrarium.colony().species().equals(WATER), "Species is " + terrarium.colony().species());
        // Lava Ants only in the Nether: here (the Overworld) a lava bucket is refused, not eaten.
        TerrariumBlockEntity other = AntColoniesGameTests.terrarium(helper, new BlockPos(9, 1, 9), honeypot, 400, 0, 5000);
        AntColoniesGameTests.structure(helper, new BlockPos(10, 1, 9), StructureKind.NEST_CHAMBER);
        AntColoniesGameTests.step(helper, other, 0.05);
        helper.assertTrue(com.antcolonies.colony.evolution.EvolutionLogic.attempt(registries, other.colony(), new ItemStack(Items.LAVA_BUCKET), 0,
                        helper.getLevel().dimension(), net.minecraft.util.RandomSource.create(1)).outcome()
                        == com.antcolonies.colony.evolution.EvolutionLogic.Outcome.WRONG_DIMENSION, "Lava Ants should need the Nether");
        // In the Terrarium itself: the Queen takes the water, the bucket comes back.
        other.setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(Items.WATER_BUCKET));
        helper.succeedWhen(() -> {
            helper.assertTrue(other.getItem(TerrariumBlockEntity.SLOT_FEED).isEmpty(), "The water bucket is still in the feed slot");
            helper.assertTrue(other.getItem(TerrariumBlockEntity.SLOT_OUTPUT).is(Items.BUCKET), "The empty bucket should come back in the output slot, found "
                    + other.getItem(TerrariumBlockEntity.SLOT_OUTPUT));
        });
    }

    /**
     * A colony whose species is gone (the mod that added its fluid was removed) keeps its ants and says so, without
     * errors: here a colony of a species id that no data file defines.
     */
    private static void missingFluidWaits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        // A kind some integration offers but that isn't in this game, if there is one; else a made-up id.
        Identifier gone = LiquidKinds.offered().stream().filter(kind -> !kind.available()).map(LiquidKind::speciesId).findFirst()
                .orElse(AntColonies.id("gone_liquid"));
        TerrariumBlockEntity terrarium = AntColoniesGameTests.terrarium(helper, pos, gone, 200, 0, 2000);
        StructureBlockEntity chamber = AntColoniesGameTests.structure(helper, pos.east(), StructureKind.LIQUID_CHAMBER);
        for (int i = 0; i < 3; i++) {
            AntColoniesGameTests.step(helper, terrarium, 1);
        }
        helper.assertTrue(ColonyStatus.UNKNOWN_SPECIES.in(terrarium.colony().status()), "The colony should say its species is unknown");
        helper.assertTrue(terrarium.colony().workers() == 200, "The ants of a missing species must be kept: " + terrarium.colony().workers());
        helper.assertTrue(chamber.isEmpty(), "A missing species made something");
        // Saved and loaded, the colony is still what it was.
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = terrarium.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), terrarium.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof TerrariumBlockEntity copy && copy.colony().species().equals(gone) && copy.colony().workers() == 200,
                "The colony of a missing species did not survive saving");
        helper.succeed();
    }

    /**
     * Every droplet that exists is named after its fluid ("<the fluid's own name> Droplet"), never by an id, and has its
     * model and texture; so do the droplets of the kinds that aren't in this game, ready for when their mod is added.
     */
    private static void dropletsNamedAndDrawn(GameTestHelper helper) {
        Language language = Language.getInstance();
        for (LiquidKind kind : LiquidKinds.offered()) {
            String id = kind.dropletId().getPath();
            for (String path : new String[] {"textures/item/" + id + ".png", "models/item/" + id + ".json", "items/" + id + ".json"}) {
                helper.assertTrue(AntColonies.class.getResource("/assets/antcolonies/" + path) != null, "Missing asset " + path);
            }
            helper.assertTrue(language.has("item.antcolonies." + id), "No name for " + id);
            helper.assertTrue(language.has("species.antcolonies." + kind.name()), "No name for the " + kind.name() + " species");
            helper.assertTrue(language.has("mod.antcolonies." + kind.mod()), "No name for the mod " + kind.mod());
            String name = language.getOrDefault("item.antcolonies." + id);
            helper.assertTrue(name.endsWith(" Droplet") && !name.contains("_") && !name.contains(":"), "Not a droplet's name: " + name);
            if (!kind.available()) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(kind.dropletId()));
            String fluid = BuiltInRegistries.FLUID.getValue(kind.fluid()).getFluidType().getDescription().getString();
            helper.assertTrue(stack.getHoverName().getString().equals(fluid + " Droplet"),
                    "The droplet of " + fluid + " is called " + stack.getHoverName().getString());
            helper.assertTrue(stack.is(net.minecraft.tags.TagKey.create(Registries.ITEM, AntColonies.id("droplets"))), id + " is not in the droplets tag");
        }
        for (String path : new String[] {"textures/entity/ant/crop_worker.png", "textures/entity/ant/crop_soldier.png", "textures/entity/ant/crop_queen.png",
                "textures/entity/ant/crop_skin_worker.png", "textures/entity/ant/crop_skin_soldier.png", "textures/entity/ant/crop_skin_queen.png",
                "blockstates/centrifuge.json", "models/block/centrifuge.json", "blockstates/liquid_chamber.json", "models/block/liquid_chamber.json"}) {
            helper.assertTrue(AntColonies.class.getResource("/assets/antcolonies/" + path) != null, "Missing asset " + path);
        }
        helper.succeed();
    }

    // --- The Droplet Centrifuge ---

    /**
     * Four Water Droplets become 400 mB of water for exactly four runs' FE; without FE nothing happens and nothing is
     * lost; the drum's block state follows the work.
     */
    private static void centrifugeSpins(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity centrifuge = centrifuge(helper, pos);
        CentrifugingRecipe recipe = CentrifugingRecipe.find(helper.getLevel(), new ItemStack(droplet("water")));
        helper.assertTrue(recipe != null && recipe.fluid().getFluid() == Fluids.WATER, "Water Droplets have no recipe into water");
        centrifuge.setItem(0, new ItemStack(droplet("water"), 4));
        int cost = recipe.energyCost();
        int each = recipe.amount();
        helper.startSequence()
                .thenIdle(recipe.duration() * 2)
                .thenExecute(() -> {
                    helper.assertTrue(centrifuge.status() == Status.NO_ENERGY, "Without FE it should wait for FE: " + centrifuge.status());
                    helper.assertTrue(centrifuge.getItem(0).getCount() == 4 && centrifuge.fluid().isEmpty() && centrifuge.progress() == 0,
                            "Without FE nothing may happen");
                    helper.assertTrue(!helper.getBlockState(pos).getValue(CentrifugeBlock.WORKING), "The drum turned without FE");
                    centrifuge.setEnergy(cost * 10);
                })
                .thenIdle(recipe.duration() / 2)
                .thenExecute(() -> {
                    helper.assertTrue(centrifuge.status() == Status.WORKING && helper.getBlockState(pos).getValue(CentrifugeBlock.WORKING),
                            "With FE and droplets the drum should turn: " + centrifuge.status());
                    helper.assertTrue(centrifuge.getItem(0).getCount() == 4 && centrifuge.fluid().isEmpty(), "The droplet goes only when the fluid arrives");
                })
                .thenIdle(recipe.duration() * 4 + 10)
                .thenExecute(() -> {
                    helper.assertTrue(centrifuge.getItem(0).isEmpty(), "Droplets left: " + centrifuge.getItem(0));
                    FluidStack made = centrifuge.fluid();
                    helper.assertTrue(made.getFluid() == Fluids.WATER && made.getAmount() == each * 4, "Expected " + each * 4 + " mB of water, got " + made);
                    helper.assertTrue(centrifuge.energy() == cost * 10 - cost * 4, "Four runs should take exactly " + cost * 4 + " FE, took "
                            + (cost * 10 - centrifuge.energy()));
                    helper.assertTrue(centrifuge.status() == Status.DONE && !helper.getBlockState(pos).getValue(CentrifugeBlock.WORKING),
                            "With the droplets gone it should say done and stop: " + centrifuge.status());
                })
                .thenSucceed();
    }

    /** Only what a Centrifuge recipe takes goes in (by hand, hopper or pipe); fluid and FE only go the right way. */
    private static void centrifugeTakesOnlyDroplets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity centrifuge = centrifuge(helper, pos);
        helper.assertTrue(centrifuge.canPlaceItem(0, new ItemStack(droplet("water"))) && centrifuge.canPlaceItem(0, new ItemStack(droplet("lava"))),
                "Droplets must go in");
        for (Item wrong : List.of(Items.COBBLESTONE, Items.WATER_BUCKET, Items.LAVA_BUCKET, ModItems.ANT_FOOD.get(), Items.POTION)) {
            helper.assertTrue(!centrifuge.canPlaceItem(0, new ItemStack(wrong)), wrong + " must not go in");
        }
        BlockPos absolute = helper.absolutePos(pos);
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.UP);
        helper.assertTrue(items != null, "The Centrifuge has no item capability");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(items.insert(ItemResource.of(new ItemStack(Items.COBBLESTONE)), 8, transaction) == 0, "A pipe put cobblestone in");
            helper.assertTrue(items.insert(ItemResource.of(new ItemStack(droplet("lava"))), 8, transaction) == 8, "A pipe could not put droplets in");
            transaction.commit();
        }
        helper.assertTrue(centrifuge.getItem(0).is(droplet("lava")) && centrifuge.getItem(0).getCount() == 8, "The droplets did not arrive");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(items.extract(ItemResource.of(new ItemStack(droplet("lava"))), 8, transaction) == 0, "A pipe may not take the droplets back out");
        }
        ResourceHandler<FluidResource> fluid = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, absolute, Direction.DOWN);
        helper.assertTrue(fluid != null, "The Centrifuge has no fluid capability");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(fluid.insert(FluidResource.of(Fluids.WATER), 1000, transaction) == 0, "Fluid went into the Centrifuge's tank from outside");
        }
        helper.assertTrue(centrifuge.fluid().isEmpty(), "The tank is not empty");
        var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, absolute, Direction.NORTH);
        helper.assertTrue(energy != null, "The Centrifuge has no FE capability");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(energy.insert(1000, transaction) == 1000, "FE should go in");
            helper.assertTrue(energy.extract(1000, transaction) == 0, "FE must not come back out");
            transaction.commit();
        }
        helper.assertTrue(centrifuge.energy() == 1000, "The FE did not arrive");
        helper.succeed();
    }

    /**
     * A tank without room for a whole run stops the Centrifuge (no droplet and no FE goes), and so does a tank that holds
     * another fluid; with room made, it goes on.
     */
    private static void centrifugeRespectsTank(GameTestHelper helper) {
        BlockPos fullPos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity full = centrifuge(helper, fullPos);
        int capacity = full.tankCapacity();
        CentrifugingRecipe recipe = CentrifugingRecipe.find(helper.getLevel(), new ItemStack(droplet("water")));
        full.setFluid(new FluidStack(Fluids.WATER, capacity - recipe.amount() + 1));
        full.setItem(0, new ItemStack(droplet("water"), 3));
        full.setEnergy(20_000);
        BlockPos otherPos = new BlockPos(4, 1, 1);
        CentrifugeBlockEntity other = centrifuge(helper, otherPos);
        other.setFluid(new FluidStack(Fluids.LAVA, 500));
        other.setItem(0, new ItemStack(droplet("water"), 3));
        other.setEnergy(20_000);
        helper.startSequence()
                .thenIdle(recipe.duration() * 3)
                .thenExecute(() -> {
                    helper.assertTrue(full.status() == Status.TANK_FULL, "A tank without room should stop it: " + full.status());
                    helper.assertTrue(full.getItem(0).getCount() == 3 && full.energy() == 20_000 && full.fluid().getAmount() == capacity - recipe.amount() + 1,
                            "A full tank cost droplets, FE or fluid");
                    helper.assertTrue(other.status() == Status.BLOCKED, "A tank of another fluid should block it: " + other.status());
                    helper.assertTrue(other.getItem(0).getCount() == 3 && other.energy() == 20_000 && other.fluid().getFluid() == Fluids.LAVA
                            && other.fluid().getAmount() == 500, "A blocked tank cost droplets or FE, or its fluid changed");
                    // Room made: one run now fits exactly.
                    full.setFluid(new FluidStack(Fluids.WATER, capacity - recipe.amount()));
                    other.setFluid(FluidStack.EMPTY);
                })
                .thenIdle(recipe.duration() * 4)
                .thenExecute(() -> {
                    helper.assertTrue(full.fluid().getAmount() == capacity && full.getItem(0).getCount() == 2 && full.status() == Status.TANK_FULL,
                            "It should fill the tank to the brim and stop: " + full.fluid() + ", " + full.getItem(0) + ", " + full.status());
                    helper.assertTrue(other.fluid().getFluid() == Fluids.WATER && other.fluid().getAmount() == recipe.amount() * 3 && other.getItem(0).isEmpty(),
                            "Emptied, the blocked one should spin all three: " + other.fluid());
                })
                .thenSucceed();
    }

    /**
     * FE for half a run: the run stops halfway and waits, the droplet still in the slot; with more FE it finishes, and
     * the whole run cost exactly its FE.
     */
    private static void centrifugePowerCut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity centrifuge = centrifuge(helper, pos);
        CentrifugingRecipe recipe = CentrifugingRecipe.find(helper.getLevel(), new ItemStack(droplet("lava")));
        helper.assertTrue(recipe != null && recipe.fluid().getFluid() == Fluids.LAVA, "Lava Droplets have no recipe into lava");
        centrifuge.setItem(0, new ItemStack(droplet("lava"), 1));
        int cost = recipe.energyCost();
        centrifuge.setEnergy(cost / 2);
        helper.startSequence()
                .thenIdle(recipe.duration() * 2)
                .thenExecute(() -> {
                    helper.assertTrue(centrifuge.status() == Status.NO_ENERGY, "Out of FE mid-run it should wait: " + centrifuge.status());
                    helper.assertTrue(centrifuge.getItem(0).getCount() == 1 && centrifuge.fluid().isEmpty(), "The power cut cost the droplet or made fluid");
                    int progress = centrifuge.progress();
                    helper.assertTrue(progress > 0 && progress < recipe.duration(), "The run should have stopped partway: " + progress);
                    helper.assertTrue(centrifuge.energy() < Math.max(1, cost / recipe.duration() + 1), "It should have used the FE it had: " + centrifuge.energy());
                    centrifuge.setEnergy(centrifuge.energy() + cost);
                })
                .thenIdle(recipe.duration() + 10)
                .thenExecute(() -> {
                    helper.assertTrue(centrifuge.getItem(0).isEmpty() && centrifuge.fluid().getFluid() == Fluids.LAVA
                            && centrifuge.fluid().getAmount() == recipe.amount(), "The run did not finish: " + centrifuge.fluid());
                    helper.assertTrue(centrifuge.energy() == cost / 2 + cost - cost, "The whole run should cost exactly " + cost + " FE, left "
                            + centrifuge.energy());
                })
                .thenSucceed();
    }

    /** Its droplets, FE, fluid and the run in progress all survive saving and loading. */
    private static void centrifugePersists(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity centrifuge = centrifuge(helper, pos);
        CentrifugingRecipe recipe = CentrifugingRecipe.find(helper.getLevel(), new ItemStack(droplet("lava")));
        centrifuge.setItem(0, new ItemStack(droplet("lava"), 5));
        centrifuge.setFluid(new FluidStack(Fluids.LAVA, 1234));
        centrifuge.setEnergy(30_000);
        helper.startSequence()
                .thenIdle(recipe.duration() / 2)
                .thenExecute(() -> {
                    int progress = centrifuge.progress();
                    helper.assertTrue(progress > 0, "The run should be under way");
                    var registries = helper.getLevel().registryAccess();
                    CompoundTag saved = centrifuge.saveWithFullMetadata(registries);
                    BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), centrifuge.getBlockState(), saved, registries);
                    helper.assertTrue(loaded instanceof CentrifugeBlockEntity, "The Centrifuge did not load");
                    CentrifugeBlockEntity copy = (CentrifugeBlockEntity) loaded;
                    helper.assertTrue(copy.getItem(0).is(droplet("lava")) && copy.getItem(0).getCount() == centrifuge.getItem(0).getCount(), "Droplets changed");
                    helper.assertTrue(copy.energy() == centrifuge.energy(), "FE changed: " + copy.energy() + " vs " + centrifuge.energy());
                    helper.assertTrue(copy.fluid().getFluid() == Fluids.LAVA && copy.fluid().getAmount() == centrifuge.fluid().getAmount(),
                            "Fluid changed: " + copy.fluid());
                    helper.assertTrue(copy.progress() == progress, "The run's progress changed: " + copy.progress() + " vs " + progress);
                })
                .thenSucceed();
    }

    /**
     * The tank is an ordinary fluid handler on every side: taking from it takes exactly that much (a look without
     * committing takes nothing), and never more than there is.
     */
    private static void centrifugeFluidOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity centrifuge = centrifuge(helper, pos);
        centrifuge.setFluid(new FluidStack(Fluids.WATER, 1000));
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> tank = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), side);
            helper.assertTrue(tank != null, "No fluid handler on the " + side + " side");
            helper.assertTrue(tank.getResource(0).getFluid() == Fluids.WATER && tank.getAmountAsInt(0) == centrifuge.fluid().getAmount(),
                    "The handler shows something else than the tank holds");
        }
        ResourceHandler<FluidResource> tank = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.EAST);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(tank.extract(FluidResource.of(Fluids.WATER), 250, transaction) == 250, "Could not take 250 mB");
            // Not committed: nothing is taken.
        }
        helper.assertTrue(centrifuge.fluid().getAmount() == 1000, "Looking took fluid");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(tank.extract(FluidResource.of(Fluids.LAVA), 250, transaction) == 0, "Took lava out of water");
            helper.assertTrue(tank.extract(FluidResource.of(Fluids.WATER), 250, transaction) == 250, "Could not take 250 mB");
            transaction.commit();
        }
        helper.assertTrue(centrifuge.fluid().getAmount() == 750, "Taking 250 mB left " + centrifuge.fluid().getAmount());
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(tank.extract(FluidResource.of(Fluids.WATER), 5000, transaction) == 750, "Took more than there was");
            transaction.commit();
        }
        helper.assertTrue(centrifuge.fluid().isEmpty(), "The tank should be empty");
        helper.succeed();
    }

    /**
     * What a Centrifuge hands to the blocks next to it is a bucket a tick, not a trickle: a cauldron, which only takes
     * whole buckets, fills from it (water and lava), and what does not fit stays in the Centrifuge.
     */
    private static void centrifugeHandsOnBuckets(GameTestHelper helper) {
        helper.assertTrue(AntConfig.get(AntConfig.CENTRIFUGE_PUSH) >= 1000, "The Centrifuge hands on less than a bucket a tick: "
                + AntConfig.get(AntConfig.CENTRIFUGE_PUSH) + " mB");
        BlockPos water = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity first = centrifuge(helper, water);
        first.setFluid(new FluidStack(Fluids.WATER, 2500));
        helper.setBlock(water.east(), net.minecraft.world.level.block.Blocks.CAULDRON);
        BlockPos lava = new BlockPos(1, 1, 5);
        CentrifugeBlockEntity second = centrifuge(helper, lava);
        second.setFluid(new FluidStack(Fluids.LAVA, 1000));
        helper.setBlock(lava.east(), net.minecraft.world.level.block.Blocks.CAULDRON);
        helper.succeedWhen(() -> {
            var filled = helper.getBlockState(water.east());
            helper.assertTrue(filled.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
                    && filled.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 3, "The cauldron is not full of water: " + filled);
            // One bucket fits a cauldron; the other 1500 mB stay.
            helper.assertTrue(first.fluid().getAmount() == 1500, "The Centrifuge should keep 1500 mB: " + first.fluid().getAmount());
            helper.assertTrue(helper.getBlockState(lava.east()).is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON),
                    "The cauldron is not full of lava: " + helper.getBlockState(lava.east()));
            helper.assertTrue(second.fluid().isEmpty(), "The Centrifuge still holds " + second.fluid());
        });
    }

    /**
     * With MachinaOpus: a Centrifuge hands its fluid to a MachinaOpus Fluid Tank standing next to it, and through
     * MachinaOpus fluid pipes to one further away; not a millibucket is lost or gained on the way. (Without MachinaOpus
     * there is no tank or pipe to try: the test passes.)
     */
    private static void centrifugeFeedsMachinaOpus(GameTestHelper helper) {
        if (!ModList.get().isLoaded("machinaopus")) {
            helper.succeed();
            return;
        }
        var tankBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse("machinaopus:fluid_tank_t1"));
        // Next to it.
        BlockPos direct = new BlockPos(1, 1, 1);
        CentrifugeBlockEntity first = centrifuge(helper, direct);
        first.setFluid(new FluidStack(Fluids.WATER, 2000));
        helper.setBlock(direct.east(), tankBlock);
        // Through two pipes.
        BlockPos piped = new BlockPos(1, 1, 5);
        CentrifugeBlockEntity second = centrifuge(helper, piped);
        second.setFluid(new FluidStack(Fluids.LAVA, 2000));
        MachinaOpusPipes.place(helper, piped.east(), "FLUID");
        MachinaOpusPipes.place(helper, piped.east(2), "FLUID");
        helper.setBlock(piped.east(3), tankBlock);
        helper.succeedWhen(() -> {
            ResourceHandler<FluidResource> near = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(direct.east()), Direction.WEST);
            helper.assertTrue(near != null, "The MachinaOpus tank has no fluid handler");
            helper.assertTrue(first.fluid().isEmpty(), "The Centrifuge still holds " + first.fluid());
            helper.assertTrue(near.getResource(0).getFluid() == Fluids.WATER && near.getAmountAsInt(0) == 2000,
                    "The tank next to it should hold all 2000 mB: " + near.getAmountAsInt(0));
            ResourceHandler<FluidResource> far = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(piped.east(3)), Direction.WEST);
            helper.assertTrue(far != null && far.getResource(0).getFluid() == Fluids.LAVA, "No lava reached the tank behind the pipes");
            helper.assertTrue(second.fluid().isEmpty() && far.getAmountAsInt(0) == 2000, "Through the pipes: Centrifuge " + second.fluid().getAmount()
                    + " mB, tank " + far.getAmountAsInt(0) + " mB of 2000");
        });
    }
}
