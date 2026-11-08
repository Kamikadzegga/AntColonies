package com.antcolonies.colony.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.content.block.TerrariumBlock;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Which chambers belong to a Terrarium. A chamber joins when it touches the Terrarium or another chamber of the colony
 * (any of its six sides), so a colony is one build of any shape. Chambers are additions: a Terrarium works without any.
 *
 * <p>When chambers touch two colonies, each chamber goes to the Terrarium it is fewer steps from; a chamber exactly as
 * far from both joins neither, and says so. A colony takes at most {@link AntConfig#MAX_CHAMBERS} chambers, the nearest
 * first.
 *
 * <p>Placing or breaking a Terrarium or a chamber bumps its level's version, and Terrariums look at their builds again
 * when it changed.
 */
public final class ColonyNetwork {
    private static final Map<ResourceKey<Level>, Long> VERSIONS = new HashMap<>();
    private static final Direction[] SIDES = Direction.values();

    private ColonyNetwork() {}

    public static long version(Level level) {
        return VERSIONS.getOrDefault(level.dimension(), 0L);
    }

    public static void changed(Level level) {
        if (!level.isClientSide()) {
            VERSIONS.merge(level.dimension(), 1L, Long::sum);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            VERSIONS.remove(level.dimension());
        }
    }

    /** Whether a block is a chamber, in a loaded chunk. */
    private static boolean chamber(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof StructureBlock;
    }

    private static boolean terrarium(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof TerrariumBlock;
    }

    /** Looks at the chambers joined to the Terrarium at {@code root}. Chambers in unloaded chunks don't count. */
    public static ColonyLayout scan(Level level, BlockPos root) {
        int limit = AntConfig.get(AntConfig.MAX_CHAMBERS);
        // At most this many chambers are looked at, so a huge field of chambers can't stall the server.
        int budget = Math.max(64, limit * 4);
        Map<BlockPos, Integer> distance = new HashMap<>();
        List<BlockPos> order = new ArrayList<>();
        List<BlockPos> others = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        root = root.immutable();
        for (Direction side : SIDES) {
            BlockPos next = root.relative(side);
            if (chamber(level, next)) {
                distance.put(next, 1);
                queue.add(next);
            }
        }
        while (!queue.isEmpty() && order.size() < budget) {
            BlockPos pos = queue.poll();
            order.add(pos);
            int steps = distance.get(pos);
            for (Direction side : SIDES) {
                BlockPos next = pos.relative(side);
                if (next.equals(root) || distance.containsKey(next)) {
                    continue;
                }
                if (chamber(level, next)) {
                    distance.put(next, steps + 1);
                    queue.add(next);
                } else if (terrarium(level, next) && !others.contains(next)) {
                    others.add(next);
                }
            }
        }
        List<BlockPos> contested = new ArrayList<>();
        List<BlockPos> owned = order;
        if (!others.isEmpty()) {
            // Other colonies touch this build: each chamber goes to the nearer Terrarium.
            Map<BlockPos, Integer> otherDistance = new HashMap<>();
            ArrayDeque<BlockPos> otherQueue = new ArrayDeque<>();
            for (BlockPos other : others) {
                for (Direction side : SIDES) {
                    BlockPos next = other.relative(side);
                    if (distance.containsKey(next) && !otherDistance.containsKey(next)) {
                        otherDistance.put(next, 1);
                        otherQueue.add(next);
                    }
                }
            }
            while (!otherQueue.isEmpty()) {
                BlockPos pos = otherQueue.poll();
                int steps = otherDistance.get(pos);
                for (Direction side : SIDES) {
                    BlockPos next = pos.relative(side);
                    if (distance.containsKey(next) && !otherDistance.containsKey(next)) {
                        otherDistance.put(next, steps + 1);
                        otherQueue.add(next);
                    }
                }
            }
            owned = new ArrayList<>();
            for (BlockPos pos : order) {
                int ours = distance.get(pos);
                int theirs = otherDistance.getOrDefault(pos, Integer.MAX_VALUE);
                if (ours < theirs) {
                    owned.add(pos);
                } else if (ours == theirs) {
                    contested.add(pos);
                }
            }
        }
        Map<StructureKind, List<BlockPos>> structures = new EnumMap<>(StructureKind.class);
        for (int i = 0; i < Math.min(limit, owned.size()); i++) {
            BlockPos pos = owned.get(i);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof StructureBlock block) {
                structures.computeIfAbsent(block.structureKind(), k -> new ArrayList<>()).add(pos);
            }
        }
        Comparator<BlockPos> sort = Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
        structures.values().forEach(list -> list.sort(sort));
        return new ColonyLayout(structures, List.copyOf(contested), Math.max(0, owned.size() - limit));
    }
}
