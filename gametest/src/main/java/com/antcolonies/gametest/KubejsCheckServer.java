package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.antcolonies.AntColonies;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

/**
 * Dev-only KubeJS check on a dedicated server ({@code gradlew runKubejsServer}: runs/kubejs-server, with KubeJS in its
 * mods folder and gametest/kubejs_example/kubejs copied in). Runs when that folder holds a file named
 * {@code kubejs-check}: checks that the KubeJS examples of docs/modpack-guide.md (recipes, biome tags, data files) and a
 * few other common KubeJS changes took effect, writes {@code kubejs-check-result.txt}, waits for the client half
 * ({@link KubejsCheckClient}) to join and leave, then stops. Lines starting with FAIL are problems.
 */
@Mod(value = AntColonies.MODID, dist = Dist.DEDICATED_SERVER)
public class KubejsCheckServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("kubejs-check");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("kubejs-check-result.txt");
    /** Gives up waiting for the client after this many ticks (5 minutes). */
    private static final int CLIENT_WAIT = 6000;

    private final List<String> results = new ArrayList<>();
    private int ticks;
    private boolean clientJoined;
    private boolean clientLeft;

    public KubejsCheckServer(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[KubeJS check] marker found");
            NeoForge.EVENT_BUS.addListener(this::onServerTick);
            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                clientJoined = true;
                showSyrup(event.getEntity());
            });
            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> clientLeft = true);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ticks++;
        if (ticks == 40 || ticks == 240) {
            if (ticks == 240) {
                result("--- again after /reload (KubeJS runs its server scripts again) ---");
            }
            try {
                check(server);
            } catch (RuntimeException e) {
                fail("ERROR: " + e);
                LOGGER.error("[KubeJS check] failed", e);
            }
            write();
            if (ticks == 40) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            }
        }
        if (ticks > 240 && (clientLeft || (!clientJoined && ticks > CLIENT_WAIT))) {
            result(clientLeft ? "client joined and left" : "FAIL no client joined");
            write();
            server.halt(false);
        }
    }

    private void check(MinecraftServer server) {
        result("KubeJS loaded: " + ModList.get().isLoaded("kubejs") + " "
                + ModList.get().getModContainerById("kubejs").map(c -> c.getModInfo().getVersion().toString()).orElse("-"));
        ServerLevel level = server.overworld();
        List<RecipeHolder<?>> recipes = List.copyOf(server.getRecipeManager().getRecipes());

        // --- Guide section 3: the Terrarium recipe replaced, Royal Jelly not craftable.
        List<String> terrarium = recipesMaking(level, recipes, ModItems.TERRARIUM.get());
        expect(terrarium.size() == 1 && !terrarium.contains("antcolonies:terrarium"), "Terrarium recipes: " + terrarium);
        recipes.stream().filter(holder -> terrarium.contains(holder.id().identifier().toString())).findFirst().ifPresent(holder -> {
            var ingredients = holder.value().placementInfo().ingredients();
            boolean glass = ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.GLASS)));
            boolean iron = ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.IRON_INGOT)));
            boolean dirt = ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.DIRT)));
            expect(glass && iron && !dirt && ingredients.size() == 8, "new Terrarium recipe: glass " + glass + ", iron " + iron + ", dirt " + dirt
                    + ", " + ingredients.size() + " ingredients");
        });
        List<String> jelly = recipesMaking(level, recipes, ModItems.ROYAL_JELLY.get());
        expect(jelly.isEmpty(), "Royal Jelly recipes: " + jelly);

        // --- Not in the guide: a new recipe making a mod item, an ingredient replaced inside a mod recipe.
        List<String> food = recipesMaking(level, recipes, ModItems.ANT_FOOD.get());
        expect(food.contains("mypack:cheap_ant_food"), "Ant Food recipes: " + food);
        recipes.stream().filter(holder -> holder.id().identifier().toString().equals("antcolonies:tweezers")).findFirst().ifPresentOrElse(holder -> {
            var ingredients = holder.value().placementInfo().ingredients();
            boolean copper = ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.COPPER_INGOT)));
            boolean iron = ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.IRON_INGOT)));
            expect(copper && !iron, "Tweezers recipe after replaceInput: copper " + copper + ", iron ingot " + iron);
        }, () -> fail("FAIL the Tweezers recipe is gone"));
        // Every other recipe of the mod still loaded (KubeJS re-reads all recipes; one it can't parse would vanish).
        long mine = recipes.stream().filter(holder -> holder.id().identifier().getNamespace().equals(AntColonies.MODID)).count();
        // DataGen writes 191 recipes that need no other mod; the guide's scripts remove the Terrarium, Royal Jelly and
        // Lava Droplet ones.
        long files = 191 - 3;
        expect(mine == files, "antcolonies recipes loaded: " + mine + " (expected " + files + ")");

        liquids(server, level, recipes);

        // --- Guide section 11: biome tags.
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        Set<String> copperBiomes = tagMembers(biomes, "mypack:spawns/copper");
        expect(copperBiomes.equals(Set.of("minecraft:dripstone_caves", "minecraft:stony_peaks")), "#mypack:spawns/copper = " + copperBiomes);
        Set<String> wild = tagMembers(biomes, "antcolonies:has_wild_ants");
        expect(wild.containsAll(copperBiomes) && wild.size() > copperBiomes.size(), "#antcolonies:has_wild_ants has the copper biomes and "
                + (wild.size() - copperBiomes.size()) + " others");
        Set<String> desert = tagMembers(biomes, "antcolonies:spawns/desert");
        expect(desert.isEmpty(), "#antcolonies:spawns/desert = " + desert);
        expect(!biomes.getOrThrow(Biomes.DESERT).is(TagKey.create(Registries.BIOME, AntColonies.id("spawns/desert"))), "desert biome not a Desert Ant biome");

        // --- Not in the guide: an item tag.
        boolean amethyst = new ItemStack(Items.AMETHYST_SHARD).is(TagKey.create(Registries.ITEM, AntColonies.id("foraging/mineral")));
        expect(amethyst, "amethyst shard in #antcolonies:foraging/mineral: " + amethyst);

        // --- Guide section 14: kubejs/data species and evolution.
        var species = ModRegistries.species(server.registryAccess(), Identifier.parse("mypack:copper"));
        expect(species != null, "species mypack:copper loaded: " + (species != null));
        var evolutions = server.registryAccess().lookupOrThrow(ModRegistries.EVOLUTION);
        boolean evolution = evolutions.get(ResourceKey.create(ModRegistries.EVOLUTION, Identifier.parse("mypack:stone_to_copper"))).isPresent();
        expect(evolution, "evolution mypack:stone_to_copper loaded: " + evolution);

        // --- Guide section 4: the food data map merged from kubejs/data.
        var cookie = BuiltInRegistries.ITEM.wrapAsHolder(Items.COOKIE).getData(ModDataMaps.ANT_FOOD);
        var lichen = BuiltInRegistries.ITEM.wrapAsHolder(Items.GLOW_LICHEN).getData(ModDataMaps.ANT_FOOD);
        var flesh = BuiltInRegistries.ITEM.wrapAsHolder(Items.ROTTEN_FLESH).getData(ModDataMaps.ANT_FOOD);
        var poppy = BuiltInRegistries.ITEM.wrapAsHolder(Items.POPPY).getData(ModDataMaps.ANT_FOOD);
        var bread = BuiltInRegistries.ITEM.wrapAsHolder(Items.BREAD).getData(ModDataMaps.ANT_FOOD);
        expect(cookie != null && cookie.food() == 60, "cookie food: " + (cookie == null ? "none" : cookie.food()) + " (built-in 40, guide 60)");
        expect(lichen != null && lichen.food() == 20 && "crystal".equals(lichen.specialKind()) && lichen.special() == 20,
                "glow lichen: " + lichen);
        expect(flesh == null, "rotten flesh removed: " + (flesh == null));
        expect(poppy != null && poppy.food() == 10, "poppy (#minecraft:flowers) food: " + (poppy == null ? "none" : poppy.food()));
        expect(bread != null, "built-in entries kept (bread): " + bread);

        // --- Guide section 15: an advancement using the antcolonies:colony trigger, from kubejs/data.
        var advancement = server.getAdvancements().get(Identifier.parse("mypack:diamond_thousand"));
        expect(advancement != null && advancement.value().parent().map(p -> p.toString().equals("antcolonies:evolution")).orElse(false),
                "advancement mypack:diamond_thousand loaded with its parent: " + (advancement != null));
    }

    /**
     * Guide sections 3 and 5: Centrifuge recipes removed, replaced and added by a script, and a pack's own Liquid Ant
     * whose fluid and droplet a startup script registers. Read back from the server's recipes, then run for real: a
     * Centrifuge spins them, and a colony of the pack's ants makes its droplets.
     */
    private void liquids(MinecraftServer server, ServerLevel level, List<RecipeHolder<?>> recipes) {
        java.util.Map<String, com.antcolonies.content.recipe.CentrifugingRecipe> centrifuging = new java.util.TreeMap<>();
        for (RecipeHolder<?> holder : recipes) {
            if (holder.value() instanceof com.antcolonies.content.recipe.CentrifugingRecipe recipe) {
                centrifuging.put(holder.id().identifier().toString(), recipe);
            }
        }
        expect(centrifuging.keySet().equals(Set.of("antcolonies:centrifuging/water", "mypack:centrifuging/lava", "mypack:centrifuging/snowball",
                "mypack:centrifuging/syrup")), "Centrifuge recipes: " + centrifuging.keySet());
        var lava = centrifuging.get("mypack:centrifuging/lava");
        expect(lava != null && lava.result().fluid().value() == net.minecraft.world.level.material.Fluids.LAVA && lava.result().amount() == 25
                && lava.energy() == 4000 && lava.time() == 200, "replaced Lava Droplet recipe: " + (lava == null ? "none" : lava.result().amount()
                + " mB, " + lava.energy() + " FE, " + lava.time() + " ticks"));
        var snowball = centrifuging.get("mypack:centrifuging/snowball");
        expect(snowball != null && snowball.ingredient().test(new ItemStack(Items.SNOWBALL)) && snowball.result().amount() == 250
                && snowball.energy() == 200 && snowball.time() == 40, "added snowball recipe: " + (snowball == null ? "none" : snowball.result().amount()
                + " mB, " + snowball.energy() + " FE, " + snowball.time() + " ticks (time left out: the default 40)"));

        // What the startup script registered.
        Identifier syrupId = Identifier.parse("mypack:syrup");
        Identifier dropletId = Identifier.parse("mypack:syrup_droplet");
        var syrup = BuiltInRegistries.FLUID.getOptional(syrupId);
        var droplet = BuiltInRegistries.ITEM.getOptional(dropletId);
        var bucket = BuiltInRegistries.ITEM.getOptional(Identifier.parse("mypack:syrup_bucket"));
        expect(syrup.isPresent() && droplet.isPresent() && bucket.isPresent(), "startup script registered: fluid mypack:syrup " + syrup.isPresent()
                + ", item mypack:syrup_droplet " + droplet.isPresent() + ", item mypack:syrup_bucket " + bucket.isPresent());

        // The pack's Liquid Ant and the evolution into it, from kubejs/data.
        var species = ModRegistries.species(server.registryAccess(), syrupId);
        expect(species != null && species.makesLiquid() && species.liquid().orElseThrow().available(), "species mypack:syrup: loaded "
                + (species != null) + ", a Liquid Ant " + (species != null && species.makesLiquid()) + ", its fluid and droplet exist "
                + (species != null && species.liquid().map(com.antcolonies.colony.species.AntSpecies.Liquid::available).orElse(false)));
        var evolutions = server.registryAccess().lookupOrThrow(ModRegistries.EVOLUTION);
        var evolution = evolutions.get(ResourceKey.create(ModRegistries.EVOLUTION, Identifier.parse("mypack:water_to_syrup")));
        expect(evolution.isPresent() && bucket.isPresent() && evolution.get().value().item().test(new ItemStack(bucket.get())),
                "evolution mypack:water_to_syrup loaded and takes the Syrup Bucket: " + evolution.isPresent());
        if (syrup.isEmpty() || droplet.isEmpty() || species == null) {
            return;
        }

        // A real Centrifuge with each of them.
        var water = spin(level, new ItemStack(Items.SNOWBALL), 45);
        expect(water.getFluid() == net.minecraft.world.level.material.Fluids.WATER && water.getAmount() == 250,
                "a Centrifuge spins a snowball into " + water.getAmount() + " mB of " + BuiltInRegistries.FLUID.getKey(water.getFluid()));
        var slow = spin(level, new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id("lava_droplet"))), 150);
        var done = spin(level, new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id("lava_droplet"))), 205);
        expect(slow.isEmpty() && done.getFluid() == net.minecraft.world.level.material.Fluids.LAVA && done.getAmount() == 25,
                "a Centrifuge spins a Lava Droplet into " + done.getAmount() + " mB of lava in 200 ticks (after 150: " + slow.getAmount() + " mB)");
        var thick = spin(level, new ItemStack(droplet.get()), 65);
        expect(thick.getFluid() == syrup.get() && thick.getAmount() == 50,
                "a Centrifuge spins a Syrup Droplet into " + thick.getAmount() + " mB of " + BuiltInRegistries.FLUID.getKey(thick.getFluid()));

        // A colony of Syrup Ants with a Liquid Chamber.
        level.getChunk(0, 0);
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(4, 200, 4);
        level.setBlockAndUpdate(pos, com.antcolonies.core.registry.ModBlocks.TERRARIUM.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), com.antcolonies.core.registry.ModBlocks.STRUCTURES
                .get(com.antcolonies.colony.model.StructureKind.LIQUID_CHAMBER).get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof com.antcolonies.content.blockentity.TerrariumBlockEntity terrarium
                && level.getBlockEntity(pos.east()) instanceof com.antcolonies.content.blockentity.StructureBlockEntity chamber) {
            var queen = com.antcolonies.colony.genetics.Genetics.wildQueen(syrupId, net.minecraft.util.RandomSource.create(5));
            terrarium.colony().setQueen(new com.antcolonies.colony.genetics.QueenData(queen.species(), queen.genome(), 4));
            terrarium.colony().setPopulation(0, 0, 250);
            terrarium.colony().setFood(2000);
            for (int i = 0; i < 6; i++) {
                terrarium.rescan(level);
                com.antcolonies.colony.simulation.ColonySimulator.step(terrarium, level, 1);
            }
            int made = 0;
            Set<String> others = new java.util.TreeSet<>();
            for (int slot = 0; slot < chamber.getContainerSize(); slot++) {
                ItemStack stack = chamber.getItem(slot);
                if (stack.is(droplet.get())) {
                    made += stack.getCount();
                } else if (!stack.isEmpty() && !stack.is(ModItems.ANT_FOOD.get())) {
                    others.add(stack.getItem().toString());
                }
            }
            expect(chamber.assigned() > 0 && made > 0 && others.isEmpty(), "a colony of Syrup Ants at a Liquid Chamber: " + chamber.assigned()
                    + " ants there, " + made + " Syrup Droplets in 6 minutes, anything else: " + others);
            chamber.clearContent();
            terrarium.clearContent();
        } else {
            fail("FAIL couldn't place a Terrarium and a Liquid Chamber");
        }
        level.removeBlock(pos.east(), false);
        level.removeBlock(pos, false);
    }

    /** For the client's screenshot: a Centrifuge three quarters full of the pack's fluid, in front of the player who joined. */
    private static void showSyrup(net.minecraft.world.entity.player.Player player) {
        var syrup = BuiltInRegistries.FLUID.getOptional(Identifier.parse("mypack:syrup"));
        if (syrup.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.core.BlockPos pos = player.blockPosition().relative(player.getDirection(), 2).above();
        level.setBlockAndUpdate(pos, com.antcolonies.core.registry.ModBlocks.CENTRIFUGE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, player.getDirection().getOpposite()));
        if (level.getBlockEntity(pos) instanceof com.antcolonies.content.blockentity.CentrifugeBlockEntity centrifuge) {
            centrifuge.setFluid(new net.neoforged.neoforge.fluids.FluidStack(syrup.get(), 6000));
        }
    }

    /** What a Centrifuge with full power holds after this many ticks with this item in its slot. */
    private static net.neoforged.neoforge.fluids.FluidStack spin(ServerLevel level, ItemStack input, int ticks) {
        level.getChunk(0, 0);
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(8, 200, 8);
        level.setBlockAndUpdate(pos, com.antcolonies.core.registry.ModBlocks.CENTRIFUGE.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof com.antcolonies.content.blockentity.CentrifugeBlockEntity centrifuge)) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        centrifuge.setEnergy(50000);
        centrifuge.setItem(0, input);
        for (int i = 0; i < ticks; i++) {
            com.antcolonies.content.blockentity.CentrifugeBlockEntity.serverTick(level, pos, level.getBlockState(pos), centrifuge);
        }
        var held = centrifuge.fluid().copy();
        centrifuge.clearContent();
        level.removeBlock(pos, false);
        return held;
    }

    /** Ids of the crafting recipes whose result (with their ingredients' first items in the grid) is this item. */
    private static List<String> recipesMaking(ServerLevel level, List<RecipeHolder<?>> recipes, Item item) {
        List<String> ids = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {
            if (holder.value() instanceof CraftingRecipe crafting && !(crafting instanceof CustomRecipe)) {
                var input = AntColoniesGameTests.craftingInput(crafting);
                if (input != null && crafting.matches(input, level) && crafting.assemble(input).is(item)) {
                    ids.add(holder.id().identifier().toString());
                }
            }
        }
        return ids;
    }

    private static Set<String> tagMembers(net.minecraft.core.HolderLookup.RegistryLookup<Biome> biomes, String tag) {
        return biomes.get(TagKey.create(Registries.BIOME, Identifier.parse(tag)))
                .map(set -> set.stream().map(holder -> holder.unwrapKey().orElseThrow().identifier().toString()).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private void expect(boolean ok, String line) {
        result((ok ? "ok   " : "FAIL ") + line);
    }

    private void fail(String line) {
        result(line);
    }

    private void result(String line) {
        LOGGER.info("[KubeJS check] {}", line);
        results.add(line);
    }

    private void write() {
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[KubeJS check] couldn't write the results", e);
        }
    }
}
