package com.antcolonies.content.worldgen;

import java.util.Optional;

import com.antcolonies.core.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A Nether anthill: the giant anthill's nest dug into the Nether's floor (see {@link AnthillPiece} with {@code nether}).
 * The Nether has a roof, so the surface heightmap is no use: each column is scanned down for a floor with open air above
 * it, high enough over the lava sea that the nest dug under it stays dry.
 */
public class NetherAnthill extends Structure {
    public static final MapCodec<NetherAnthill> CODEC = simpleCodec(NetherAnthill::new);

    /** The highest floor looked for, and the lowest: the nest goes {@link AnthillPiece#DEPTH} down, and lava lies at 31. */
    private static final int TOP = 100;
    private static final int LOWEST = 31 + AnthillPiece.DEPTH + 6;
    /** Open air needed over the floor for the mound. */
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
        int middle = floor(generator, context, cx, cz, TOP, LOWEST);
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
        // Planned to stay above the lava sea (at 31), so its tunnels stay dry.
        AnthillPiece piece = AnthillPiece.plan(center, AnthillPiece.Kind.NETHER, context.seed() ^ center.asLong() * 0x9E3779B97F4A7C15L, 35);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(piece)));
    }

    /**
     * The highest solid, dry block of a column between {@code from} and {@code to} (downwards) with {@link #HEADROOM}
     * blocks of air over it; -1 if there is none.
     */
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
