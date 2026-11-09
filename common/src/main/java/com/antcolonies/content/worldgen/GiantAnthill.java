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
 * A giant anthill: a mound of earth over a nest dug deep into the ground, with tunnels, a storage chamber, and the
 * Queen's chamber at the bottom guarded by soldiers. Only on dry, fairly flat ground.
 */
public class GiantAnthill extends Structure {
    public static final MapCodec<GiantAnthill> CODEC = simpleCodec(GiantAnthill::new);

    /** At most this much difference in ground height under the mound. */
    private static final int MAX_SLOPE = 3;

    public GiantAnthill(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        ChunkGenerator generator = context.chunkGenerator();
        int[][] samples = {{0, 0}, {-6, 0}, {6, 0}, {0, -6}, {0, 6}};
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int middle = 0;
        for (int[] sample : samples) {
            int x = cx + sample[0];
            int z = cz + sample[1];
            int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            int ground = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            if (surface != ground) {
                return Optional.empty(); // water
            }
            lowest = Math.min(lowest, ground);
            highest = Math.max(highest, ground);
            if (sample[0] == 0 && sample[1] == 0) {
                middle = ground;
            }
        }
        if (highest - lowest > MAX_SLOPE || middle - AnthillPiece.DEPTH - 2 <= context.heightAccessor().getMinY()) {
            return Optional.empty();
        }
        // The ground's top block is one below the first free one.
        BlockPos center = new BlockPos(cx, middle - 1, cz);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new AnthillPiece(center, false))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GIANT_ANTHILL.get();
    }
}
