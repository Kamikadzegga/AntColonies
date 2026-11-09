package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.core.registry.ModBlocks;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

/**
 * Dev-only world generation check on a dedicated server with real terrain (the game test server's other dimensions are
 * flat and empty). Runs when the server's folder ({@code gradlew runWorldgenServer}: runs/worldgen-server) holds a file
 * named {@code worldgen-check}: finds a Nether anthill and generates it, counts Warped Ant nests in a warped forest and
 * Chorus Ant nests on the End's outer islands, writes {@code worldgen-check-result.txt} and stops.
 */
@Mod(value = AntColonies.MODID, dist = Dist.DEDICATED_SERVER)
public class WorldgenCheckServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("worldgen-check");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("worldgen-check-result.txt");

    private final List<String> results = new ArrayList<>();
    private int ticks;

    public WorldgenCheckServer(IEventBus modEventBus) {
        if (Files.exists(MARKER)) {
            LOGGER.info("[Worldgen check] marker found");
            NeoForge.EVENT_BUS.addListener(this::onServerTick);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++ticks != 40) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            netherAnthill(server.getLevel(Level.NETHER));
            nests(server.getLevel(Level.NETHER), Biomes.WARPED_FOREST, ModBlocks.NESTS.get("warped").get(), "Warped");
            nests(server.getLevel(Level.END), Biomes.END_HIGHLANDS, ModBlocks.NESTS.get("chorus").get(), "Chorus");
        } catch (RuntimeException e) {
            result("ERROR: " + e);
            LOGGER.error("[Worldgen check] failed", e);
        }
        try {
            Files.write(RESULT, results);
        } catch (IOException e) {
            LOGGER.error("[Worldgen check] couldn't write the results", e);
        }
        server.halt(false);
    }

    /** The nearest Nether anthill to 0,0: found, its chunks generated, and what it built. */
    private void netherAnthill(ServerLevel nether) {
        var structure = nether.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, AntColonies.id("nether_anthill")));
        var found = nether.getChunkSource().getGenerator().findNearestMapStructure(nether, HolderSet.direct(structure), BlockPos.ZERO, 64, false);
        if (found == null) {
            result("Nether anthill: none within 64 chunks of 0,0");
            return;
        }
        BlockPos at = found.getFirst();
        loadAround(nether, at, 1);
        int bricks = 0;
        int chests = 0;
        BlockPos center = null;
        for (BlockPos pos : BlockPos.betweenClosed(at.getX() - 10, 32, at.getZ() - 10, at.getX() + 10, 120, at.getZ() + 10)) {
            var state = nether.getBlockState(pos);
            if (state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
                bricks++;
            } else if (state.is(Blocks.CHEST) && nether.getBlockEntity(pos) instanceof RandomizableContainer chest
                    && AnthillPiece.NETHER_LOOT.equals(chest.getLootTable())) {
                chests++;
                center = pos.immutable();
            }
        }
        result("Nether anthill near " + at.toShortString() + ": " + bricks + " blackstone bricks, " + chests + " Nether-loot chest(s)"
                + (center == null ? "" : " (chest at " + center.toShortString() + ")"));
    }

    /** Nests of a species in the chunks around the nearest spot of its biome. */
    private void nests(ServerLevel level, ResourceKey<Biome> biome, Block nest, String name) {
        var spot = level.findClosestBiome3d(holder -> holder.is(biome), new BlockPos(0, 64, 0), 3000, 32, 64);
        if (spot == null) {
            result(name + " nests: no " + biome.identifier() + " within 3000 blocks");
            return;
        }
        BlockPos at = spot.getFirst();
        int radius = 4;
        loadAround(level, at, radius);
        int count = 0;
        BlockPos first = null;
        for (BlockPos pos : BlockPos.betweenClosed(at.getX() - radius * 16, level.getMinY(), at.getZ() - radius * 16,
                at.getX() + radius * 16 + 15, level.getMaxY(), at.getZ() + radius * 16 + 15)) {
            if (level.getBlockState(pos).is(nest)) {
                count++;
                first = first == null ? pos.immutable() : first;
            }
        }
        result(name + " nests around " + at.toShortString() + " (" + (2 * radius + 1) * (2 * radius + 1) + " chunks): " + count
                + (first == null ? "" : ", e.g. at " + first.toShortString() + " on " + level.getBlockState(first.below()).getBlock().getName().getString()));
    }

    private static void loadAround(ServerLevel level, BlockPos at, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
            }
        }
    }

    private void result(String line) {
        LOGGER.info("[Worldgen check] {}", line);
        results.add(line);
    }
}
