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
            case 100 -> {
                result("KubeJS loaded on the client: " + ModList.get().isLoaded("kubejs") + ", JEI: " + ModList.get().isLoaded("jei"));
                // Guide section 12: names from kubejs/assets.
                expect("Copper Ant".equals(I18n.get("species.mypack.copper")), "species.mypack.copper = " + I18n.get("species.mypack.copper"));
                expect("Bronze Ant".equals(I18n.get("species.mypack.bronze")), "species.mypack.bronze = " + I18n.get("species.mypack.bronze"));
                expect("Oxidized".equals(I18n.get("mutation.mypack.oxidized")), "mutation.mypack.oxidized = " + I18n.get("mutation.mypack.oxidized"));
                // Guide section 14: the species from kubejs/data reaches the client.
                expect(ClientSpecies.get(COPPER) != null, "species mypack:copper synced to the client: " + (ClientSpecies.get(COPPER) != null));
                minecraft.setScreen(new IdentificationBookScreen());
            }
            case 105 -> selectInBook(minecraft);
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
            case 260 -> {
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

    /** Opens the book on the KubeJS species (the list holds the guide pages first, then the species). */
    private void selectInBook(Minecraft minecraft) {
        if (!(minecraft.screen instanceof IdentificationBookScreen book)) {
            result("FAIL the book did not open");
            return;
        }
        try {
            var all = ClientSpecies.all();
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).key().identifier().equals(COPPER)) {
                    index = i;
                }
            }
            expect(index >= 0, "mypack:copper in the book's species list: " + (index >= 0));
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
        var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
        if (runtime == null) {
            result("FAIL JEI runtime missing");
            return;
        }
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack);
        List<String> ids = runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).limitFocus(List.of(focus)).get()
                .map(holder -> holder.id().identifier().toString()).sorted().toList();
        expect(ok.test(ids), "JEI crafting recipes for " + stack.getItem() + ": " + ids);
    }

    private void jeiOutput(Minecraft minecraft, ItemStack stack) {
        var runtime = com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime;
        if (runtime == null) {
            result("FAIL JEI runtime missing");
            return;
        }
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack);
        runtime.getRecipesGui().show(focus);
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
