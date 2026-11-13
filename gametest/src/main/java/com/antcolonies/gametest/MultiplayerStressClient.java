package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.antcolonies.AntColonies;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Dev-only multiplayer stress test, a player's half (see {@code MultiplayerStressServer}). Runs when the client's folder
 * holds a file named {@code mp-stress} and it is connected to a dedicated server. Frames are uncapped while it runs; once
 * a second it samples the frame rate and the packets a second it receives, summed up per phase as the server announces
 * them, with a screenshot in each phase. When the server sends it off it writes {@code mp-stress-result.txt}, puts the
 * player's settings back and quits.
 */
@Mod(value = AntColonies.MODID, dist = Dist.CLIENT)
public class MultiplayerStressClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("mp-stress");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("mp-stress-result.txt");

    private final List<String> results = new ArrayList<>();
    private final Map<String, List<double[]>> phases = new LinkedHashMap<>();
    private String phase = "joining";
    private int phaseTicks;
    private boolean connected;
    private Object[] savedOptions;
    private jdk.jfr.Recording recording;

    public MultiplayerStressClient(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[MP stress] client marker found");
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
            NeoForge.EVENT_BUS.addListener(this::onSystemMessage);
        }
    }

    private void onSystemMessage(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (text.startsWith("[stress] phase ")) {
            phase = text.substring("[stress] phase ".length());
            phaseTicks = 0;
            result("phase " + phase);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() != null) {
            if (connected) {
                connected = false;
                finish(minecraft);
            }
            return;
        }
        if (!connected) {
            connected = true;
            minecraft.options.pauseOnLostFocus = false;
            savedOptions = new Object[] {minecraft.options.enableVsync().get(), minecraft.options.framerateLimit().get(),
                    minecraft.options.inactivityFpsLimit().get()};
            minecraft.options.enableVsync().set(false);
            minecraft.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
            minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            if (!minecraft.gui.hud.isHidden()) {
                minecraft.gui.hud.toggle();
            }
            result("joined as " + minecraft.player.getGameProfile().name() + "; render distance " + minecraft.options.getEffectiveRenderDistance()
                    + ", window " + minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
        }
        phaseTicks++;
        if (phaseTicks % 20 == 0 && phaseTicks > 40) {
            var connection = minecraft.getConnection();
            phases.computeIfAbsent(phase, p -> new ArrayList<>()).add(new double[] {minecraft.getFps(),
                    connection == null ? 0 : connection.getConnection().getAverageReceivedPackets()});
        }
        // The second minute of the loaded world profiled, for {@code jfr view hot-methods}.
        if (phase.equals("loaded") && phaseTicks == 1200) {
            recording = StressProfiler.startRecording();
        }
        if (phase.equals("loaded") && phaseTicks == 2400 && recording != null) {
            StressProfiler.stopRecording(recording, FMLPaths.GAMEDIR.get().resolve("mp-stress-client-loaded.jfr"));
            recording = null;
        }
        if (phaseTicks == 300 && !phase.equals("done")) {
            String file = "mp-stress-" + minecraft.player.getGameProfile().name() + "-" + phase + ".png";
            Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
            result("screenshot " + file);
        }
    }

    private void result(String line) {
        LOGGER.info("[MP stress] {}", line);
        results.add(line);
    }

    private void finish(Minecraft minecraft) {
        phases.forEach((name, samples) -> {
            double fps = 0;
            double min = Double.MAX_VALUE;
            double packets = 0;
            for (double[] s : samples) {
                fps += s[0];
                min = Math.min(min, s[0]);
                packets += s[1];
            }
            int n = Math.max(1, samples.size());
            result(String.format(Locale.ROOT, "%s (%d s): %.0f fps average, %.0f lowest; %.0f packets/s received", name, samples.size(), fps / n,
                    min == Double.MAX_VALUE ? 0 : min, packets / n));
        });
        if (savedOptions != null) {
            minecraft.options.enableVsync().set((Boolean) savedOptions[0]);
            minecraft.options.framerateLimit().set((Integer) savedOptions[1]);
            minecraft.options.inactivityFpsLimit().set((net.minecraft.client.InactivityFpsLimit) savedOptions[2]);
        }
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[MP stress] couldn't write the results", e);
        }
        minecraft.stop();
    }
}
