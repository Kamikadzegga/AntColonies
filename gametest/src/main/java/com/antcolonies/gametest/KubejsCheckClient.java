package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.screen.IdentificationBookScreen;
import com.antcolonies.core.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Dev-only KubeJS check, the player's half (see {@link KubejsCheckServer}): {@code gradlew runKubejsClient} joins the
 * KubeJS check server from runs/kubejs-client (KubeJS and JEI in its mods folder, gametest/kubejs_example/kubejs copied
 * in) when that folder holds a file named {@code kubejs-check}. Checks the names from kubejs/assets and the species synced
 * from kubejs/data, takes screenshots of the Ant Identification Book on the new species and of JEI's recipes for the
 * KubeJS-changed items, writes {@code kubejs-check-result.txt}, leaves and quits.
 */
@Mod(value = AntColonies.MODID, dist = Dist.CLIENT)
public class KubejsCheckClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("kubejs-check");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("kubejs-check-result.txt");
    private static final Identifier COPPER = Identifier.parse("mypack:copper");
    private static final Identifier SYRUP = Identifier.parse("mypack:syrup");

    private final List<String> results = new ArrayList<>();
    private int ticks = -1;

    public KubejsCheckClient(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[KubeJS check] client marker found");
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() != null) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        ticks++;
        switch (ticks) {
            // The Centrifuge full of the pack's fluid that the server put in front of the player.
            case 80 -> screenshot(minecraft, "world_centrifuge_syrup");
            case 100 -> {
                result("KubeJS loaded on the client: " + ModList.get().isLoaded("kubejs") + ", JEI: " + ModList.get().isLoaded("jei"));
                // Guide section 12: names from kubejs/assets.
                expect("Copper Ant".equals(I18n.get("species.mypack.copper")), "species.mypack.copper = " + I18n.get("species.mypack.copper"));
                expect("Bronze Ant".equals(I18n.get("species.mypack.bronze")), "species.mypack.bronze = " + I18n.get("species.mypack.bronze"));
                expect("Oxidized".equals(I18n.get("mutation.mypack.oxidized")), "mutation.mypack.oxidized = " + I18n.get("mutation.mypack.oxidized"));
                // Guide section 14: the species from kubejs/data reaches the client.
                expect(ClientSpecies.get(COPPER) != null, "species mypack:copper synced to the client: " + (ClientSpecies.get(COPPER) != null));
                liquids(minecraft);
                minecraft.setScreen(new IdentificationBookScreen());
            }
            case 105 -> selectInBook(minecraft, COPPER);
            case 130 -> screenshot(minecraft, "book_mypack_copper");
            case 140 -> {
                // What JEI lists as crafting recipes: the server's, changed by KubeJS (JEI must be on the server too, see the guide).
                jeiCrafting(new ItemStack(ModItems.TERRARIUM.get()), ids -> ids.size() == 1 && ids.getFirst().contains("kjs/"));
                jeiCrafting(new ItemStack(ModItems.ROYAL_JELLY.get()), List::isEmpty);
                jeiCrafting(new ItemStack(ModItems.ANT_FOOD.get()), ids -> ids.contains("mypack:cheap_ant_food"));
                jeiCrafting(new ItemStack(ModItems.TWEEZERS.get()), ids -> ids.equals(List.of("antcolonies:tweezers")));
                jeiOutput(minecraft, new ItemStack(ModItems.TERRARIUM.get()));
            }
            case 170 -> screenshot(minecraft, "jei_terrarium");
            case 180 -> {
                // JEI's own lists of the mod's two liquid categories, and the page of what a Syrup Droplet is used for.
                jeiAnt("Droplet Centrifuge", com.antcolonies.compat.jei.AntColoniesJeiPlugin.CENTRIFUGING, ids -> ids.size() == 4
                        && ids.stream().anyMatch(id -> id.contains("mypack/syrup_droplet")) && ids.stream().anyMatch(id -> id.contains("minecraft/snowball")));
                jeiAnt("Liquid Ants", com.antcolonies.compat.jei.AntColoniesJeiPlugin.LIQUID, ids -> ids.size() == 3
                        && ids.stream().anyMatch(id -> id.contains("syrup")));
                var droplet = BuiltInRegistries.ITEM.getOptional(Identifier.parse("mypack:syrup_droplet"));
                if (droplet.isEmpty() || !ModList.get().isLoaded("jei") || !Jei.showUses(new ItemStack(droplet.get()))) {
                    result("FAIL couldn't show the Syrup Droplet's uses in JEI");
                }
            }
            case 210 -> screenshot(minecraft, "jei_syrup_droplet");
            case 220 -> minecraft.setScreen(new IdentificationBookScreen());
            case 225 -> selectInBook(minecraft, SYRUP);
            case 250 -> screenshot(minecraft, "book_mypack_syrup");
            case 280 -> {
                minecraft.setScreen(null);
                try {
                    Files.write(RESULT, results);
                } catch (IOException e) {
                    LOGGER.error("[KubeJS check] couldn't write the results", e);
                }
                minecraft.stop();
            }
            default -> {}
        }
    }

    /**
     * Guide sections 3 and 5 on the client: the pack's Liquid Ant, its fluid and droplet (registered by the startup
     * script, which runs on the client too) and the Centrifuge recipes the server sent, as the scripts changed them.
     */
    private void liquids(Minecraft minecraft) {
        expect("Syrup Ant".equals(I18n.get("species.mypack.syrup")), "species.mypack.syrup = " + I18n.get("species.mypack.syrup"));
        var species = ClientSpecies.get(SYRUP);
        expect(species != null && species.makesLiquid() && species.liquid().orElseThrow().available(), "species mypack:syrup synced to the client: "
                + (species != null) + ", a Liquid Ant whose fluid and droplet exist here: "
                + (species != null && species.liquid().map(com.antcolonies.colony.species.AntSpecies.Liquid::available).orElse(false)));
        var droplet = BuiltInRegistries.ITEM.getOptional(Identifier.parse("mypack:syrup_droplet"));
        var syrup = BuiltInRegistries.FLUID.getOptional(SYRUP);
        expect(droplet.isPresent() && syrup.isPresent(), "client registries: item mypack:syrup_droplet " + droplet.isPresent() + ", fluid mypack:syrup "
                + syrup.isPresent());
        if (droplet.isEmpty() || syrup.isEmpty()) {
            return;
        }
        String dropletName = new ItemStack(droplet.get()).getHoverName().getString();
        String fluidName = syrup.get().getFluidType().getDescription().getString();
        expect("Syrup Droplet".equals(dropletName) && "Syrup".equals(fluidName), "names KubeJS gives them: \"" + dropletName + "\", \"" + fluidName + "\"");
        var recipes = com.antcolonies.content.recipe.CentrifugingRecipe.all(minecraft.level);
        var fromDroplet = com.antcolonies.content.recipe.CentrifugingRecipe.find(minecraft.level, new ItemStack(droplet.get()));
        var fromSnow = com.antcolonies.content.recipe.CentrifugingRecipe.find(minecraft.level, new ItemStack(net.minecraft.world.item.Items.SNOWBALL));
        var fromLava = com.antcolonies.content.recipe.CentrifugingRecipe.find(minecraft.level,
                new ItemStack(BuiltInRegistries.ITEM.getValue(AntColonies.id("lava_droplet"))));
        expect(recipes.size() == 4 && fromDroplet != null && fromDroplet.createResult().getFluid() == syrup.get() && fromDroplet.createResult().getAmount() == 50
                && fromSnow != null && fromSnow.createResult().getAmount() == 250 && fromLava != null && fromLava.createResult().getAmount() == 25,
                "Centrifuge recipes the server sent: " + recipes.size() + "; Syrup Droplet " + (fromDroplet == null ? "none" : fromDroplet.createResult().getAmount()
                        + " mB") + ", snowball " + (fromSnow == null ? "none" : fromSnow.createResult().getAmount() + " mB") + ", Lava Droplet "
                        + (fromLava == null ? "none" : fromLava.createResult().getAmount() + " mB"));
        // How the mod draws the pack's fluid (the Centrifuge's tank, its screen): a sprite and a colour must come out.
        try {
            var sprite = com.antcolonies.client.FluidLook.sprite(syrup.get());
            int colour = com.antcolonies.client.FluidLook.color(syrup.get());
            expect(sprite != null, "how the mod draws mypack:syrup: sprite " + (sprite == null ? "none" : sprite.contents().name()) + ", colour #"
                    + String.format("%06X", colour & 0xFFFFFF));
        } catch (RuntimeException e) {
            result("FAIL FluidLook of mypack:syrup threw " + e);
        }
    }

    /** Logs the ids of what JEI lists in one of the mod's categories and checks them. */
    private void jeiAnt(String name, mezz.jei.api.recipe.types.IRecipeType<com.antcolonies.compat.recipes.AntRecipe> type,
            java.util.function.Predicate<List<String>> ok) {
        if (!ModList.get().isLoaded("jei")) {
            result("FAIL JEI not loaded");
            return;
        }
        List<String> ids = Jei.antRecipes(type);
        if (ids == null) {
            result("FAIL JEI runtime missing");
            return;
        }
        expect(ok.test(ids), "JEI " + name + " category: " + ids);
    }

    /** Opens the book on a KubeJS species (the list holds the guide pages first, then the species). */
    private void selectInBook(Minecraft minecraft, Identifier species) {
        if (!(minecraft.screen instanceof IdentificationBookScreen book)) {
            result("FAIL the book did not open");
            return;
        }
        try {
            var all = ClientSpecies.all();
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).key().identifier().equals(species)) {
                    index = i;
                }
            }
            expect(index >= 0, species + " in the book's species list: " + (index >= 0));
            var guides = IdentificationBookScreen.class.getDeclaredField("GUIDES");
            guides.setAccessible(true);
            int entry = ((List<?>) guides.get(null)).size() + index;
            var selected = IdentificationBookScreen.class.getDeclaredField("selected");
            selected.setAccessible(true);
            selected.setInt(book, entry);
            var scroll = IdentificationBookScreen.class.getDeclaredField("scroll");
            scroll.setAccessible(true);
            scroll.setInt(book, Math.max(0, entry - 5));
        } catch (ReflectiveOperationException e) {
            result("FAIL couldn't select the species in the book: " + e);
        }
    }

    /** Logs the crafting recipes JEI shows for this output and checks them. */
    private void jeiCrafting(ItemStack stack, java.util.function.Predicate<List<String>> ok) {
        if (!ModList.get().isLoaded("jei")) {
            result("FAIL JEI not loaded");
            return;
        }
        List<String> ids = Jei.craftingRecipes(stack);
        if (ids == null) {
            result("FAIL JEI runtime missing");
            return;
        }
        expect(ok.test(ids), "JEI crafting recipes for " + stack.getItem() + ": " + ids);
    }

    private void jeiOutput(Minecraft minecraft, ItemStack stack) {
        if (!ModList.get().isLoaded("jei") || !Jei.show(stack)) {
            result("FAIL JEI runtime missing");
        }
    }

    /**
     * Everything that touches JEI's classes, kept apart: this class is only loaded when JEI is, so a client without JEI
     * (the multiplayer runs) still loads {@link KubejsCheckClient}.
     */
    private static final class Jei {
        static List<String> craftingRecipes(ItemStack stack) {
            var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
            if (runtime == null) {
                return null;
            }
            return runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).limitFocus(List.of(focus(runtime, stack)))
                    .get().map(holder -> holder.id().identifier().toString()).sorted().toList();
        }

        static List<String> antRecipes(mezz.jei.api.recipe.types.IRecipeType<com.antcolonies.compat.recipes.AntRecipe> type) {
            var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
            if (runtime == null) {
                return null;
            }
            return runtime.getRecipeManager().createRecipeLookup(type).get().map(recipe -> recipe.id().toString()).sorted().toList();
        }

        /** Opens JEI on what this item is used for. */
        static boolean showUses(ItemStack stack) {
            var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
            if (runtime == null) {
                return false;
            }
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
            return true;
        }

        static boolean show(ItemStack stack) {
            var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
            if (runtime == null) {
                return false;
            }
            runtime.getRecipesGui().show(focus(runtime, stack));
            return true;
        }

        private static mezz.jei.api.recipe.IFocus<ItemStack> focus(mezz.jei.api.runtime.IJeiRuntime runtime, ItemStack stack) {
            return runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack);
        }
    }

    private void screenshot(Minecraft minecraft, String name) {
        minecraft.getToastManager().clear();
        String file = "kubejs-" + name + ".png";
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), 1, message -> {});
        result("screenshot " + file + " (screen " + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName()) + ")");
    }

    private void expect(boolean ok, String line) {
        result((ok ? "ok   " : "FAIL ") + line);
    }

    private void result(String line) {
        LOGGER.info("[KubeJS check] {}", line);
        results.add(line);
    }
}
