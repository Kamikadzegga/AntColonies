package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

/**
 * Dev-only multiplayer stress test, the dedicated server's half (see {@code MultiplayerStressClient} for the players').
 * Runs when the server's folder holds a file named {@code mp-stress}: {@code gradlew runMpServer}, then
 * {@code runMpClientA} and {@code runMpClientB}, each folder with an {@code mp-stress} file too. Once both players are
 * in (as spectators, Ant_A over the colonies, Ant_B over an anthill): 30 seconds of an empty world, then the
 * {@link StressWorld} and five minutes measured, then one minute with Ant_A (in Chitin, unkillable) in an anthill's
 * Queen's chamber and the whole anthill after them, while Ant_B watches. Each second it samples the server's tick time
 * and the packets it sends each player; it tells the players each phase by a system message ("[stress] phase ..."), so
 * their frame rates are summed up per phase. Results in {@code mp-stress-result.txt}; then it sends them off and stops.
 */
@Mod(value = AntColonies.MODID, dist = Dist.DEDICATED_SERVER)
public class MultiplayerStressServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("mp-stress");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("mp-stress-result.txt");
    private static final int BASELINE_START = 60;
    private static final int BUILD = 660;
    private static final int LOADED_START = 1060;
    // With a file named mp-stress-short beside mp-stress: one minute loaded instead of five, and no profile.
    private static final boolean SHORT = Files.exists(FMLPaths.GAMEDIR.get().resolve("mp-stress-short"));
    private static final int LOADED_END = LOADED_START + (SHORT ? 1 : 5) * 60 * 20;
    private static final int ALARM_START = LOADED_END + 100;
    private static final int ALARM_END = ALARM_START + 60 * 20;

    private final List<String> results = new ArrayList<>();
    private final List<double[]> baseline = new ArrayList<>();
    private final List<double[]> loaded = new ArrayList<>();
    private final List<double[]> alarm = new ArrayList<>();
    private int ticks = -1;
    private BlockPos origin;
    private ServerPlayer a;
    private ServerPlayer b;
    private StressProfiler.PacketCounter toA;
    private StressProfiler.PacketCounter toB;
    private jdk.jfr.Recording recording;
    private final StressProfiler.MovementSampler movement = new StressProfiler.MovementSampler();

    public MultiplayerStressServer(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[MP stress] server marker found");
            NeoForge.EVENT_BUS.addListener(this::onServerTick);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ticks < 0) {
            if (players.size() < 2) {
                return;
            }
            List<ServerPlayer> sorted = players.stream().sorted(Comparator.comparing(p -> p.getGameProfile().name())).toList();
            a = sorted.get(0);
            b = sorted.get(1);
            ticks = 0;
            toA = StressProfiler.PacketCounter.attach(a.connection.getConnection().channel(), true);
            toB = StressProfiler.PacketCounter.attach(b.connection.getConnection().channel(), true);
            result("both in: " + a.getGameProfile().name() + ", " + b.getGameProfile().name() + "; view distance "
                    + server.getPlayerList().getViewDistance() + ", simulation distance " + server.getPlayerList().getSimulationDistance());
        }
        ticks++;
        try {
            step(server, server.overworld());
        } catch (RuntimeException e) {
            result("ERROR at tick " + ticks + ": " + e);
            LOGGER.error("[MP stress] failed", e);
            finish(server);
        }
    }

    private void step(MinecraftServer server, ServerLevel level) {
        if (ticks == 20) {
            origin = a.blockPosition();
            command(server, "difficulty peaceful");
            command(server, "time set noon");
            command(server, "gamerule advance_time false");
            for (ServerPlayer player : List.of(a, b)) {
                command(server, "gamemode spectator " + name(player));
            }
            look(server, a, 28.5, 12.4, -6.0, 0, 32);
            BlockPos hill = StressWorld.anthillCenter(origin, 0).subtract(origin);
            look(server, b, hill.getX() - 22, hill.getY() + 12, hill.getZ() - 22, -45, 26);
            phase("baseline");
        }
        if (ticks > BASELINE_START && ticks <= BUILD && ticks % 20 == 0) {
            baseline.add(sample(server));
        }
        if (ticks == BUILD) {
            phase("building");
            long start = System.nanoTime();
            StressWorld.buildColonies(level, origin);
            result(String.format(Locale.ROOT, "colonies built in %.0f ms", (System.nanoTime() - start) / 1e6));
        }
        for (int i = 0; i < StressWorld.THEMES.length; i++) {
            if (ticks == BUILD + 10 + i * 10) {
                long start = System.nanoTime();
                String line = StressWorld.buildAnthill(level, origin, i);
                result(String.format(Locale.ROOT, "%s, built in %.0f ms", line, (System.nanoTime() - start) / 1e6));
            }
        }
        if (ticks == LOADED_START) {
            phase("loaded");
            toA.reset();
            toB.reset();
        }
        if (ticks > LOADED_START && ticks <= LOADED_END && ticks % 20 == 0) {
            loaded.add(sample(server));
            if ((ticks - LOADED_START) % 1200 == 0) {
                double[] s = loaded.getLast();
                result(String.format(Locale.ROOT, "minute %d: tick %.2f ms (worst %.2f); packets/s to A %.0f, to B %.0f",
                        (ticks - LOADED_START) / 1200, s[0], s[1], s[2], s[3]));
            }
        }
        // One minute of the loaded world profiled (the second), for {@code jfr view hot-methods}.
        // Ten seconds of the ants watched tick by tick: what makes their movement packets.
        if (ticks > LOADED_START + 200 && ticks <= LOADED_START + 400) {
            movement.tick(level, origin);
        }
        if (ticks == LOADED_START + 400) {
            movement.summary().forEach(line -> result("ants: " + line));
        }
        if (!SHORT && ticks == LOADED_START + 1200) {
            recording = StressProfiler.startRecording();
        }
        if (!SHORT && ticks == LOADED_START + 2400) {
            StressProfiler.stopRecording(recording, FMLPaths.GAMEDIR.get().resolve("mp-stress-server-loaded.jfr"));
        }
        if (ticks == LOADED_END + 1) {
            result("packets to A while loaded, " + toA.summary(14));
            result("packets to B while loaded, " + toB.summary(14));
            result("after 5 minutes: " + StressWorld.count(level, origin));
            command(server, "difficulty normal");
            result(StressWorld.raiseAlarm(level, origin, a, 0));
            phase("alarm");
        }
        if (ticks == ALARM_START) {
            toA.reset();
            toB.reset();
            result("alarm raised: " + StressWorld.count(level, origin));
        }
        if (ticks > ALARM_START && ticks <= ALARM_END && ticks % 20 == 0) {
            alarm.add(sample(server));
        }
        if (ticks == ALARM_END + 1) {
            result("packets to A while alarmed, " + toA.summary(14));
            result("packets to B while alarmed, " + toB.summary(14));
            result("after the alarm minute: " + StressWorld.count(level, origin) + "; A's health " + a.getHealth());
            result("baseline (empty world, " + baseline.size() + " s): " + summary(baseline));
            result("loaded (5 minutes, " + loaded.size() + " s): " + summary(loaded));
            result("anthill alarmed (1 minute, " + alarm.size() + " s): " + summary(alarm));
            phase("done");
        }
        if (ticks == ALARM_END + 40) {
            for (ServerPlayer player : List.of(a, b)) {
                player.connection.disconnect(Component.literal("mp-stress done"));
            }
        }
        if (ticks == ALARM_END + 80) {
            finish(server);
        }
    }

    /** Tells both players the phase, so they sum up their frame rates per phase. */
    private void phase(String name) {
        for (ServerPlayer player : List.of(a, b)) {
            player.sendSystemMessage(Component.literal("[stress] phase " + name));
        }
        result("phase " + name + " at tick " + ticks);
    }

    /** The server's average tick and worst tick (last 100), and the packets a second it sends A and B. */
    private double[] sample(MinecraftServer server) {
        long worst = 0;
        for (long nanos : server.getTickTimesNanos()) {
            worst = Math.max(worst, nanos);
        }
        return new double[] {server.getAverageTickTimeNanos() / 1e6, worst / 1e6, a.connection.getConnection().getAverageSentPackets(),
                b.connection.getConnection().getAverageSentPackets()};
    }

    private static String summary(List<double[]> samples) {
        double tick = 0;
        double worst = 0;
        double toA = 0;
        double toB = 0;
        List<Double> ticks = new ArrayList<>();
        for (double[] s : samples) {
            tick += s[0];
            worst = Math.max(worst, s[1]);
            toA += s[2];
            toB += s[3];
            ticks.add(s[0]);
        }
        int n = Math.max(1, samples.size());
        ticks.sort(Double::compare);
        double p95 = ticks.isEmpty() ? 0 : ticks.get(Math.min(ticks.size() - 1, (int) Math.floor(ticks.size() * 0.95)));
        return String.format(Locale.ROOT, "server tick %.2f ms average (95th percentile %.2f ms, worst single tick %.2f ms); packets/s to A %.0f, to B %.0f",
                tick / n, p95, worst, toA / n, toB / n);
    }

    private void look(MinecraftServer server, ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        command(server, String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f", name(player), origin.getX() + dx, origin.getY() + dy,
                origin.getZ() + dz, yaw, pitch));
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private synchronized void result(String line) {
        LOGGER.info("[MP stress] {}", line);
        results.add(line);
    }

    private void finish(MinecraftServer server) {
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[MP stress] couldn't write the results", e);
        }
        server.halt(false);
    }
}
