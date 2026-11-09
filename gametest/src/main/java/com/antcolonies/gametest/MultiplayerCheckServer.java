package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.content.item.GuardData;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

/**
 * Dev-only multiplayer check, the dedicated server's half (see {@code MultiplayerCheckClient} for the players' half).
 * Runs when the server's folder holds a file named {@code mp-check}: {@code gradlew runMpServer}, then
 * {@code runMpClientA} and {@code runMpClientB}, which join as Ant_A and Ant_B. Once both are in, it builds a colony in
 * front of them and gives Ant_A a Guard Ant, opens the Terrarium for both players at once, feeds the colony and takes its
 * Queen out and back while both watch, opens a Storage for Ant_B, breaks a chamber, moves Ant_A away to see whose Guard
 * Ant follows, then sends both players off and stops. What it did and saw goes to {@code mp-check-result.txt}.
 */
@Mod(value = AntColonies.MODID, dist = Dist.DEDICATED_SERVER)
public class MultiplayerCheckServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("mp-check");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("mp-check-result.txt");

    private final List<String> results = new ArrayList<>();
    private int ticks = -1;
    private BlockPos origin;
    private ServerPlayer a;
    private ServerPlayer b;
    private GuardAnt guard;

    public MultiplayerCheckServer(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[MP check] server marker found");
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
            result("both in: " + a.getGameProfile().name() + ", " + b.getGameProfile().name());
        }
        ticks++;
        ServerLevel level = server.overworld();
        try {
            step(server, level);
        } catch (RuntimeException e) {
            result("ERROR at tick " + ticks + ": " + e);
            LOGGER.error("[MP check] failed", e);
            finish(server);
        }
    }

    private void step(MinecraftServer server, ServerLevel level) {
        switch (ticks) {
            case 40 -> build(server, level);
            case 140 -> open(level, a, origin);
            case 160 -> open(level, b, origin);
            case 200 -> {
                result("food before feeding: " + (int) colony(level).food());
                terrarium(level).setItem(TerrariumBlockEntity.SLOT_FEED, new ItemStack(ModItems.ANT_FOOD.get(), 8));
            }
            case 260 -> {
                result("food after feeding: " + (int) colony(level).food() + "; menus: A " + menu(a) + ", B " + menu(b));
                // An empty tube in the Queen slot takes the Queen out, while both have the screen open.
                terrarium(level).setItem(TerrariumBlockEntity.SLOT_QUEEN, new ItemStack(ModItems.TEST_TUBE.get()));
            }
            case 300 -> {
                result("queen after taking out: " + (colony(level).queen() != null) + ", output " + terrarium(level).getItem(TerrariumBlockEntity.SLOT_OUTPUT));
                ItemStack tube = terrarium(level).getItem(TerrariumBlockEntity.SLOT_OUTPUT).copy();
                terrarium(level).setItem(TerrariumBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                terrarium(level).setItem(TerrariumBlockEntity.SLOT_QUEEN, tube);
            }
            case 340 -> result("queen after putting back: " + (colony(level).queen() != null) + ", species " + colony(level).species());
            case 360 -> {
                a.closeContainer();
                b.closeContainer();
                open(level, b, origin.south());
            }
            case 420 -> {
                result("B's menu at the Storage: " + menu(b));
                b.closeContainer();
                level.destroyBlock(origin.east(), true, b);
                result("B broke the Nest Chamber: " + level.getBlockState(origin.east()));
            }
            case 440 -> command(server, String.format(Locale.ROOT, "tp %s %d %d %d -90 20", name(a), origin.getX() - 12, origin.getY(), origin.getZ() - 2));
            case 640 -> {
                double toA = guard.distanceTo(a);
                double toB = guard.distanceTo(b);
                result(String.format(Locale.ROOT, "guard: owner %s, %.1f blocks from A, %.1f from B, following A: %s", guard.getOwner() == null ? "none"
                        : guard.getOwner().getName().getString(), toA, toB, toA < 6));
                result("colony at the end: " + colony(level).workers() + " workers, food " + (int) colony(level).food());
            }
            case 660 -> {
                for (ServerPlayer player : List.of(a, b)) {
                    player.connection.disconnect(Component.literal("mp-check done"));
                }
            }
            case 700 -> finish(server);
            default -> { }
        }
    }

    /** A Common colony in front of both players: Terrarium, Foraging Site, Storage and Nest Chamber; a Guard Ant for A. */
    private void build(MinecraftServer server, ServerLevel level) {
        command(server, "gamemode creative " + name(a));
        command(server, "gamemode creative " + name(b));
        origin = a.blockPosition().offset(6, 0, 0);
        level.setBlockAndUpdate(origin, ModBlocks.TERRARIUM.get().defaultBlockState());
        level.setBlockAndUpdate(origin.north(), ModBlocks.STRUCTURES.get(StructureKind.FORAGING_SITE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.south(), ModBlocks.STRUCTURES.get(StructureKind.STORAGE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), ModBlocks.STRUCTURES.get(StructureKind.NEST_CHAMBER).get().defaultBlockState());
        Colony colony = colony(level);
        colony.setQueen(Genetics.wildQueen(AntColonies.id("common"), RandomSource.create(5)));
        colony.setPopulation(0, 0, 800);
        colony.setFood(2000);
        terrarium(level).setChanged();
        command(server, String.format(Locale.ROOT, "tp %s %d %d %d -90 25", name(a), origin.getX() - 4, origin.getY(), origin.getZ() - 1));
        command(server, String.format(Locale.ROOT, "tp %s %d %d %d -90 25", name(b), origin.getX() - 4, origin.getY(), origin.getZ() + 2));
        guard = ModEntities.GUARD_ANT.get().create(level, EntitySpawnReason.COMMAND);
        guard.snapTo(origin.getX() - 2.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        guard.setTame(true, false);
        guard.setOwner(a);
        guard.setup(new GuardData(AntColonies.id("common"), 4, -1));
        level.addFreshEntity(guard);
        result("built at " + origin.toShortString() + ", guard for " + name(a));
    }

    /** Right-clicks the block as the player would, which opens its screen. */
    private void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false);
        var outcome = level.getBlockState(pos).useWithoutItem(level, player, hit);
        result(name(player) + " opened " + level.getBlockState(pos).getBlock().getName().getString() + ": " + outcome + ", menu " + menu(player));
    }

    private TerrariumBlockEntity terrarium(ServerLevel level) {
        return (TerrariumBlockEntity) level.getBlockEntity(origin);
    }

    private Colony colony(ServerLevel level) {
        return terrarium(level).colony();
    }

    private static String menu(ServerPlayer player) {
        return player.containerMenu.getClass().getSimpleName();
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private synchronized void result(String line) {
        LOGGER.info("[MP check] {}", line);
        results.add(line);
    }

    private void finish(MinecraftServer server) {
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[MP check] couldn't write the results", e);
        }
        server.halt(false);
    }
}
