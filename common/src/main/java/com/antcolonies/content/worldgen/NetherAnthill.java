package com.antcolonies.content.worldgen;

import java.util.Optional;

import com.antcolonies.core.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * An anthill of the Nether, and of other mods' cave worlds. Such a world has a roof, so each column is scanned down for
 * a dry floor with open air above it.
 */
public class NetherAnthill extends Structure {
    public static final MapCodec<NetherAnthill> CODEC = simpleCodec(NetherAnthill::new);

    /** How far under the top of the world's terrain the highest floor is looked for: the roof is passed over (the Nether's floors start at 100). */
    private static final int UNDER_ROOF = 28;
    /** How far over the sea (the Nether's lava) the lowest floor is: the nest goes {@link AnthillPiece#DEPTH} down and stays dry. */
    private static final int OVER_SEA = AnthillPiece.DEPTH + 5;
    private static final int HEADROOM = AnthillPiece.HEIGHT + 3;
    private static final int MAX_SLOPE = 3;

    public NetherAnthill(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        ChunkGenerator generator = context.chunkGenerator();
        int sea = generator.getSeaLevel();
        int middle = floor(generator, context, cx, cz, generator.getMinY() + generator.getGenDepth() - UNDER_ROOF, sea + OVER_SEA);
        if (middle < 0) {
            return Optional.empty();
        }
        int[][] samples = {{-6, 0}, {6, 0}, {0, -6}, {0, 6}};
        for (int[] sample : samples) {
            int ground = floor(generator, context, cx + sample[0], cz + sample[1], middle + MAX_SLOPE, middle - MAX_SLOPE);
            if (ground < 0) {
                return Optional.empty();
            }
        }
        BlockPos center = new BlockPos(cx, middle, cz);
        // Another mod's cave world is built of its own ground; the Nether's look follows its biome.
        Block rock = AnthillPiece.foreignRock(generator.getBaseColumn(cx, cz, context.heightAccessor(), context.randomState()), middle, middle - 10);
        AnthillTheme theme = rock != null ? AnthillTheme.LOCAL : AnthillTheme.ofNether(context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(middle), QuartPos.fromBlock(cz)));
        // Planned to stay above the sea (the Nether's lava), so its tunnels stay dry.
        AnthillPiece piece = AnthillPiece.plan(center, AnthillPiece.Kind.NETHER, context.seed() ^ center.asLong() * 0x9E3779B97F4A7C15L, sea + 3,
                theme, rock);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(piece)));
    }

    /** The highest solid, dry block of a column from {@code from} down to {@code to} with {@link #HEADROOM} air over it, or -1. */
    private static int floor(ChunkGenerator generator, GenerationContext context, int x, int z, int from, int to) {
        NoiseColumn column = generator.getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        int air = 0;
        for (int y = from + HEADROOM; y >= to; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) {
                air++;
                continue;
            }
            if (y <= from && air >= HEADROOM && state.getFluidState().isEmpty()) {
                return y;
            }
            air = 0;
        }
        return -1;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.NETHER_ANTHILL.get();
    }
}
