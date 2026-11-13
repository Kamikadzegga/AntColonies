package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPromise;
import jdk.jfr.Recording;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;

import org.slf4j.Logger;

/**
 * Dev-only tools for the stress tests: a Java Flight Recorder recording of where the time goes (CPU samples every
 * 10 ms, allocations), read afterwards with {@code jfr view hot-methods <file>}, and a count of the packets a connection
 * sends or receives by type, bundles counted by what they hold.
 */
final class StressProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StressProfiler() {}

    static Recording startRecording() {
        Recording recording = new Recording();
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
        recording.enable("jdk.ObjectAllocationSample").withPeriod(Duration.ofMillis(10));
        recording.enable("jdk.GarbageCollection");
        recording.start();
        return recording;
    }

    static void stopRecording(Recording recording, Path file) {
        try {
            recording.stop();
            recording.dump(file);
            LOGGER.info("[stress] profile written to {}", file);
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("[stress] couldn't write the profile", e);
        } finally {
            recording.close();
        }
    }

    /**
     * Watches the wild ants tick by tick and sums up, per kind of ant, what makes the server send movement packets:
     * changes of motion, flips of being on the ground (each a full position packet) and turns of the head.
     */
    static final class MovementSampler {
        private final Map<String, double[]> groups = new java.util.TreeMap<>();
        private final Map<Integer, Object[]> last = new java.util.HashMap<>();
        private int ticks;

        void tick(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin) {
            ticks++;
            for (var ant : level.getEntitiesOfClass(com.antcolonies.content.entity.WildAnt.class, new net.minecraft.world.phys.AABB(origin).inflate(160))) {
                String key = (ant.anthill() != null ? "anthill" : "free") + (ant.getNavigation().isInProgress() ? " walking" : " still")
                        + " on " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ant.getBlockStateOn().getBlock()).getPath()
                        + (ant.isInWater() ? " in water" : "");
                double[] g = groups.computeIfAbsent(key, k -> new double[5]);
                g[0]++;
                Object[] before = last.get(ant.getId());
                var motion = ant.getDeltaMovement();
                if (before != null) {
                    if (motion.distanceToSqr((net.minecraft.world.phys.Vec3) before[0]) > 1.0E-7) {
                        g[1]++;
                    }
                    if ((Boolean) before[1] != ant.onGround()) {
                        g[2]++;
                    }
                    if (Math.abs(net.minecraft.util.Mth.packDegrees(ant.getYHeadRot()) - (Byte) before[2]) >= 1) {
                        g[3]++;
                    }
                    if (ant.position().distanceToSqr((net.minecraft.world.phys.Vec3) before[3]) > 7.6E-6) {
                        g[4]++;
                    }
                }
                last.put(ant.getId(), new Object[] {motion, ant.onGround(), net.minecraft.util.Mth.packDegrees(ant.getYHeadRot()), ant.position()});
            }
        }

        /** Per kind of ant: how many on average, and per ant a second the changes of motion, ground, head and position. */
        List<String> summary() {
            List<String> lines = new java.util.ArrayList<>();
            groups.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> -e.getValue()[0])).limit(16)
                    .forEach(e -> {
                        double[] g = e.getValue();
                        double antSeconds = g[0] / 20.0;
                        lines.add(String.format(Locale.ROOT, "%-40s %5.1f ants; per ant a second: motion %.1f, ground flips %.1f, head turns %.1f, moved %.1f",
                                e.getKey(), g[0] / ticks, g[1] / antSeconds, g[2] / antSeconds, g[3] / antSeconds, g[4] / antSeconds));
                    });
            return lines;
        }
    }

    /** Counts the packets through a connection's channel by type; {@link #reset} starts a new count. */
    static final class PacketCounter extends ChannelDuplexHandler {
        private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();
        private volatile long since = System.nanoTime();
        private final boolean outbound;

        private PacketCounter(boolean outbound) {
            this.outbound = outbound;
        }

        /** Counts what the channel sends (a server's connection to a player) or receives (a player's to the server). */
        static PacketCounter attach(Channel channel, boolean outbound) {
            PacketCounter counter = new PacketCounter(outbound);
            channel.pipeline().addLast("antcolonies_stress_counter", counter);
            return counter;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (outbound) {
                count(msg);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!outbound) {
                count(msg);
            }
            super.channelRead(ctx, msg);
        }

        private void count(Object msg) {
            if (msg instanceof BundlePacket<?> bundle) {
                for (Packet<?> packet : bundle.subPackets()) {
                    count(packet);
                }
                return;
            }
            if (msg instanceof Packet<?> packet) {
                counts.computeIfAbsent(name(packet), n -> new LongAdder()).increment();
            }
        }

        private static String name(Packet<?> packet) {
            String name = packet.getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1);
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) {
                name += "[" + custom.payload().type().id() + "]";
            }
            return name;
        }

        void reset() {
            counts.clear();
            since = System.nanoTime();
        }

        /** Packets a second by type since the last reset, the busiest first. */
        String summary(int top) {
            double seconds = Math.max(1e-3, (System.nanoTime() - since) / 1e9);
            long all = counts.values().stream().mapToLong(LongAdder::sum).sum();
            return String.format(Locale.ROOT, "%.0f/s: ", all / seconds) + counts.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed()).limit(top)
                    .map(e -> String.format(Locale.ROOT, "%s %.0f", e.getKey(), e.getValue().sum() / seconds)).collect(Collectors.joining(", "));
        }
    }
}
