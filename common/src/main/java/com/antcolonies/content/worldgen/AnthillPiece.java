package com.antcolonies.content.worldgen;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModStructures;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.Tags;

/**
 * The whole anthill in one piece, built from its center (the ground's top block under the mound's peak): a mound of
 * earth with a shaft down through it, a gallery, a storage chamber with a chest, and the Queen's chamber at the bottom
 * with a barrel, a nest of the species and its defenders. Every choice comes from the world seed and the center, so each
 * chunk builds its part of the same anthill. The layout is mirrored at random so anthills don't all look alike.
 */
public class AnthillPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/giant_anthill"));
    /** Mound radius and height, and how deep the nest goes below the ground. */
    static final int RADIUS = 7;
    static final int HEIGHT = 5;
    static final int DEPTH = 18;
    private static final int HALF_WIDTH = 10;

    /** Chambers: center x, y, z and radii x, y, z (relative to the center, before mirroring). */
    private static final double[][] CHAMBERS = {
            {3, -5, 1, 3, 2, 2.5},       // gallery
            {-4, -9, 3, 3, 2, 3},        // storage
            {1, -14, -2, 4.5, 2.5, 4}};  // the Queen's chamber
    private static final int GALLERY = 0;
    private static final int STORAGE = 1;
    private static final int QUEEN = 2;
    /** Tunnels: from x, y, z to x, y, z, radius. The first is the shaft down through the mound. */
    private static final double[][] TUNNELS = {
            {0, HEIGHT + 1, 0, 0, -3, 0, 0.6},
            {0, -3, 0, 3, -5, 1, 1.1},
            {2, -5, 1, -4, -9, 3, 1.1},
            {-4, -9, 3, 1, -14, -2, 1.1},
            {4, -5, 1, 7, -7, -3, 1.0}};

    private final BlockPos center;

    public AnthillPiece(BlockPos center) {
        super(ModStructures.ANTHILL_PIECE.get(), 0, box(center));
        this.center = center;
        setOrientation(null);
    }

    public AnthillPiece(CompoundTag tag) {
        super(ModStructures.ANTHILL_PIECE.get(), tag);
        this.center = new BlockPos(tag.getIntOr("CX", 0), tag.getIntOr("CY", 0), tag.getIntOr("CZ", 0));
    }

    private static BoundingBox box(BlockPos center) {
        return new BoundingBox(center.getX() - HALF_WIDTH, center.getY() - DEPTH - 1, center.getZ() - HALF_WIDTH,
                center.getX() + HALF_WIDTH, center.getY() + HEIGHT + 2, center.getZ() + HALF_WIDTH);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", center.getX());
        tag.putInt("CY", center.getY());
        tag.putInt("CZ", center.getZ());
    }

    // --- Shape ---

    /** Whether a spot (relative, before mirroring) is dug out: inside a chamber or a tunnel. */
    static boolean carved(double x, double y, double z) {
        return inside(x, y, z, 0);
    }

    /** The walls: one block around what is dug out. */
    static boolean shell(double x, double y, double z) {
        return !carved(x, y, z) && inside(x, y, z, 1);
    }

    private static boolean inside(double x, double y, double z, double grow) {
        for (double[] c : CHAMBERS) {
            double dx = (x - c[0]) / (c[3] + grow);
            double dy = (y - c[1]) / (c[4] + grow);
            double dz = (z - c[2]) / (c[5] + grow);
            if (dx * dx + dy * dy + dz * dz <= 1) {
                return true;
            }
        }
        for (double[] t : TUNNELS) {
            if (segmentDistance(x, y, z, t) <= t[6] + grow) {
                return true;
            }
        }
        return false;
    }

    private static double segmentDistance(double x, double y, double z, double[] t) {
        double vx = t[3] - t[0];
        double vy = t[4] - t[1];
        double vz = t[5] - t[2];
        double length = vx * vx + vy * vy + vz * vz;
        double f = length == 0 ? 0 : Math.clamp(((x - t[0]) * vx + (y - t[1]) * vy + (z - t[2]) * vz) / length, 0, 1);
        double dx = x - (t[0] + vx * f);
        double dy = y - (t[1] + vy * f);
        double dz = z - (t[2] + vz * f);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The mound's height over a column (0 outside it): a rounded cone, a little uneven. */
    private int moundHeight(long seed, int x, int z) {
        double distance = Math.sqrt(x * x + z * z) + (hash(seed, x, 0, z) - 0.5) * 1.4;
        if (distance >= RADIUS) {
            return 0;
        }
        double f = distance / RADIUS;
        return (int) Math.round(HEIGHT * (1 - f * f));
    }

    // --- Building ---

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        long seed = level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L;
        RandomSource layout = RandomSource.create(seed);
        int sx = layout.nextBoolean() ? 1 : -1;
        int sz = layout.nextBoolean() ? 1 : -1;
        Soil soil = Soil.of(level, center);
        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                int rx = (x - center.getX()) * sx;
                int rz = (z - center.getZ()) * sz;
                int mound = moundHeight(seed, rx, rz);
                for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunkBB.isInside(pos)) {
                        continue;
                    }
                    int ry = y - center.getY();
                    boolean carved = carved(rx, ry, rz);
                    if (ry > 0) {
                        if (ry <= mound) {
                            level.setBlock(pos, carved ? Blocks.AIR.defaultBlockState() : soil.mound(hash(seed, x, y, z)), Block.UPDATE_CLIENTS);
                        } else if (mound > 0 && ry <= mound + 2 && !level.getBlockState(pos).isAir() && level.getBlockState(pos).canBeReplaced()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); // grass left floating
                        }
                        continue;
                    }
                    if (carved) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    BlockState here = level.getBlockState(pos);
                    if (shell(rx, ry, rz)) {
                        // Ant-dug walls, but caves and water stay as they are.
                        if (!here.isAir() && here.getFluidState().isEmpty() && !here.is(Blocks.BEDROCK)) {
                            boolean floor = carved(rx, ry + 1, rz);
                            level.setBlock(pos, soil.wall(hash(seed, x, y, z), floor), Block.UPDATE_CLIENTS);
                        }
                    } else if (mound > 0 && ry >= -2 && here.canBeReplaced() && here.getFluidState().isEmpty()) {
                        level.setBlock(pos, soil.mound(hash(seed, x, y, z)), Block.UPDATE_CLIENTS); // fill dips under the mound
                    }
                }
            }
        }
        furnish(level, chunkBB, seed, sx, sz);
    }

    /** The chests, the nest and the ants, each only by the chunk it stands in. */
    private void furnish(WorldGenLevel level, BoundingBox chunkBB, long seed, int sx, int sz) {
        RandomSource random = RandomSource.create(seed + 1);
        Identifier species = WildAnt.speciesFor(level, center, random);
        BlockPos storageChest = floor(STORAGE, 1, 0, sx, sz);
        BlockPos queenBarrel = floor(QUEEN, -2, 1, sx, sz);
        BlockPos nest = floor(QUEEN, 3, 0, sx, sz);
        if (chunkBB.isInside(storageChest)) {
            level.setBlock(storageChest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)),
                    Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, storageChest, LOOT);
        }
        if (chunkBB.isInside(queenBarrel)) {
            level.setBlock(queenBarrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, queenBarrel, LOOT);
        }
        var nestBlock = AntColonies.MODID.equals(species.getNamespace()) ? ModBlocks.NESTS.get(species.getPath()) : null;
        if (nestBlock != null && chunkBB.isInside(nest)) {
            level.setBlock(nest, nestBlock.get().defaultBlockState().setValue(WildNestBlock.RECOVERING, false), Block.UPDATE_CLIENTS);
        }
        BlockPos home = floor(QUEEN, 0, 0, sx, sz);
        spawn(level, chunkBB, home, species, Caste.QUEEN, home);
        int[][] soldiers = {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, -1}};
        for (int[] spot : soldiers) {
            spawn(level, chunkBB, floor(QUEEN, spot[0], spot[1], sx, sz), species, Caste.SOLDIER, home);
        }
        spawn(level, chunkBB, floor(GALLERY, 0, 0, sx, sz), species, Caste.SOLDIER, home);
        spawn(level, chunkBB, floor(GALLERY, 1, 1, sx, sz), species, Caste.WORKER, home);
        spawn(level, chunkBB, floor(GALLERY, -1, 0, sx, sz), species, Caste.WORKER, home);
        spawn(level, chunkBB, floor(STORAGE, -1, 1, sx, sz), species, Caste.WORKER, home);
    }

    /** The lowest dug-out spot of a chamber's column (offset from its center), in the world. */
    private BlockPos floor(int chamber, int dx, int dz, int sx, int sz) {
        double[] c = CHAMBERS[chamber];
        int x = (int) c[0] + dx;
        int z = (int) c[2] + dz;
        int y = (int) c[1];
        while (carved(x, y - 1, z) && y > c[1] - c[4] - 1) {
            y--;
        }
        return new BlockPos(center.getX() + x * sx, center.getY() + y, center.getZ() + z * sz);
    }

    private static void spawn(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, Identifier species, Caste caste, BlockPos home) {
        if (!chunkBB.isInside(pos)) {
            return;
        }
        WildAnt ant = ModEntities.WILD_ANT.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (ant == null) {
            return;
        }
        ant.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        ant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        ant.setSpecies(species);
        ant.setCaste(caste);
        ant.setGuardian(true);
        ant.setPersistenceRequired();
        ant.setHomeTo(home, 14);
        level.addFreshEntityWithPassengers(ant);
    }

    /** A number from 0 to 1 for a spot, the same whichever chunk asks. */
    private static double hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The anthill's earth, from its biome: sandy in deserts and badlands, dark earth elsewhere. */
    private enum Soil {
        EARTH(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.ROOTED_DIRT, Blocks.GRAVEL),
        DESERT(Blocks.SAND, Blocks.SANDSTONE, Blocks.COARSE_DIRT, Blocks.GRAVEL),
        BADLANDS(Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.COARSE_DIRT, Blocks.GRAVEL);

        private final Block main;
        private final Block second;
        private final Block third;
        private final Block grit;

        Soil(Block main, Block second, Block third, Block grit) {
            this.main = main;
            this.second = second;
            this.third = third;
            this.grit = grit;
        }

        static Soil of(WorldGenLevel level, BlockPos pos) {
            var biome = level.getBiome(pos);
            return biome.is(BiomeTags.IS_BADLANDS) ? BADLANDS : biome.is(Tags.Biomes.IS_DESERT) ? DESERT : EARTH;
        }

        BlockState mound(double roll) {
            return (roll < 0.5 ? main : roll < 0.75 ? second : roll < 0.9 ? third : grit).defaultBlockState();
        }

        /** Tunnel walls: packed earth, with a softer floor. */
        BlockState wall(double roll, boolean floor) {
            if (floor) {
                return (roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.5 ? Blocks.ROOTED_DIRT : main == Blocks.COARSE_DIRT ? Blocks.COARSE_DIRT : main)
                        .defaultBlockState();
            }
            return (roll < 0.45 ? Blocks.PACKED_MUD : roll < 0.8 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
        }
    }
}
