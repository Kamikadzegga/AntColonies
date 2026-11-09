package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.content.menu.TerrariumMenu;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Dev-only multiplayer check, a player's half (see {@code MultiplayerCheckServer}). Runs when the client's folder holds
 * a file named {@code mp-check} and it is connected to a dedicated server. Every second it writes down what this player
 * sees: the open screen and, for a Terrarium screen, the colony's numbers as synced to this client; the nearest
 * Terrarium's synced colony; the Guard Ants around and whether they are this player's. Screenshots of each new screen
 * and of the world now and then go to screenshots/. When the server sends it off it writes
 * {@code mp-check-result.txt} and quits.
 */
@Mod(value = AntColonies.MODID, dist = Dist.CLIENT)
public class MultiplayerCheckClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("mp-check");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("mp-check-result.txt");

    private final List<String> results = new ArrayList<>();
    private final Set<String> screensSeen = new HashSet<>();
    private boolean connected;
    private int ticks;
    private int screenshotAt = -1;
    private String screenshotName;

    public MultiplayerCheckClient(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[MP check] client marker found");
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() != null) {
            if (connected) {
                // Sent off by the server: done.
                connected = false;
                result("disconnected; screen " + name(minecraft.gui.screen()));
                finish(minecraft);
            }
            return;
        }
        if (!connected) {
            connected = true;
            minecraft.options.pauseOnLostFocus = false;
            result("joined as " + minecraft.player.getGameProfile().name());
        }
        ticks++;
        Screen screen = minecraft.gui.screen();
        String screenName = name(screen);
        if (screen != null && screensSeen.add(screenName)) {
            screenshotAt = ticks + 15;
            screenshotName = screenName;
            result("t" + ticks + " new screen " + screenName + (screen instanceof AbstractContainerScreen<?> container
                    ? " (menu " + container.getMenu().getClass().getSimpleName() + ")" : ""));
        }
        if (ticks == screenshotAt) {
            screenshot(minecraft, screenshotName);
        } else if (ticks % 200 == 0) {
            screenshot(minecraft, "world_t" + ticks);
        }
        if (ticks % 20 == 0) {
            result("t" + ticks + " " + observe(minecraft, screen));
        }
    }

    /** One line of what this player sees right now. */
    private String observe(Minecraft minecraft, Screen screen) {
        StringBuilder line = new StringBuilder("screen ").append(name(screen));
        if (screen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof TerrariumMenu menu) {
            var colony = menu.colony();
            line.append(String.format(Locale.ROOT, " [screen colony: %d workers, food %d, queen %s]", colony.workers(), (int) colony.food(),
                    colony.queen() != null));
        }
        TerrariumBlockEntity terrarium = nearestTerrarium(minecraft);
        if (terrarium == null) {
            line.append("; no Terrarium in view");
        } else {
            var colony = terrarium.colony();
            line.append(String.format(Locale.ROOT, "; Terrarium %s: %d workers, food %d, queen %s", terrarium.getBlockPos().toShortString(),
                    colony.workers(), (int) colony.food(), colony.queen() != null));
        }
        int guards = 0;
        int mine = 0;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof GuardAnt guard) {
                guards++;
                mine += guard.isOwnedBy(minecraft.player) ? 1 : 0;
            }
        }
        line.append("; guard ants ").append(guards).append(" (mine ").append(mine).append(')');
        return line.toString();
    }

    private static TerrariumBlockEntity nearestTerrarium(Minecraft minecraft) {
        TerrariumBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        int cx = minecraft.player.chunkPosition().x();
        int cz = minecraft.player.chunkPosition().z();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (BlockEntity entity : minecraft.level.getChunk(x, z).getBlockEntities().values()) {
                    double distance = entity.getBlockPos().distSqr(minecraft.player.blockPosition());
                    if (entity instanceof TerrariumBlockEntity terrarium && distance < best) {
                        nearest = terrarium;
                        best = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private static String name(Screen screen) {
        return screen == null ? "none" : screen.getClass().getSimpleName();
    }

    private void screenshot(Minecraft minecraft, String name) {
        String file = "mp-" + minecraft.player.getGameProfile().name() + "-" + name + ".png";
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
        result("screenshot " + file);
    }

    private void result(String line) {
        LOGGER.info("[MP check] {}", line);
        results.add(line);
    }

    private void finish(Minecraft minecraft) {
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[MP check] couldn't write the results", e);
        }
        minecraft.stop();
    }
}
