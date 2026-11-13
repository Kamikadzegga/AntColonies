package com.antcolonies.content.worldgen;

import java.util.Optional;

import com.antcolonies.core.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * An End anthill: a nest of Chorus Ants dug into an outer island (see {@link AnthillPiece} with {@link AnthillPiece.Kind#END}):
 * a mound of end stone over tunnels walled in end stone bricks and purpur. Only where the island is solid and flat
 * enough, and its plan never reaches past the island's underside by more than its walls can close.
 */
public class EndAnthill extends Structure {
    public static final MapCodec<EndAnthill> CODEC = simpleCodec(EndAnthill::new);
    private static final int MAX_SLOPE = 3;

    public EndAnthill(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        ChunkGenerator generator = context.chunkGenerator();
        int[][] samples = {{0, 0}, {-7, 0}, {7, 0}, {0, -7}, {0, 7}};
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int middle = 0;
        for (int[] sample : samples) {
            int ground = generator.getFirstOccupiedHeight(cx + sample[0], cz + sample[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                    context.randomState());
            if (ground <= context.heightAccessor().getMinY() + 1) {
                return Optional.empty(); // the void
            }
            lowest = Math.min(lowest, ground);
            highest = Math.max(highest, ground);
            if (sample[0] == 0 && sample[1] == 0) {
                middle = ground;
            }
        }
        // Outer islands stand around y 50-70 and are some 20-40 blocks thick: stay well above their undersides.
        if (highest - lowest > MAX_SLOPE || middle < 50) {
            return Optional.empty();
        }
        BlockPos center = new BlockPos(cx, middle - 1, cz);
        AnthillPiece piece = AnthillPiece.plan(center, AnthillPiece.Kind.END, context.seed() ^ center.asLong() * 0x9E3779B97F4A7C15L, middle - 26,
                AnthillTheme.END);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.END_ANTHILL.get();
    }
}
