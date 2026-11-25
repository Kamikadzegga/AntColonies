package com.antcolonies.content.worldgen;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Where a wild nest stands in a column: on the ground under the leaves, or on a cave floor where the dimension has a roof
 * (the Nether, and other mods' underground worlds). The spot is dry, and free but for a small plant or a little snow.
 */
public class NestGround implements PlacementModifier {
    public static final MapCodec<NestGround> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0, 1).optionalFieldOf("chance", 1.0).forGetter(ground -> ground.chance)
    ).apply(i, NestGround::new));
    /** Columns of the chunk tried in a roofed dimension, and heights tried in each: most spots there are rock or lava. */
    private static final int ROOFED_COLUMNS = 4;
    private static final int ROOFED_TRIES = 4;
    /** How far below a tried height a cave floor is looked for. */
    private static final int ROOFED_SCAN = 32;
    /** Kept clear of a roofed dimension's bottom and roof. */
    private static final int ROOFED_MARGIN = 8;

    /** The share of the spots it is offered that it takes: under 1 thins the nests out. */
    private final double chance;

    public NestGround(double chance) {
        this.chance = chance;
    }

    public double chance() {
        return chance;
    }

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
        if (chance < 1 && random.nextDouble() >= chance) {
            return;
        }
        WorldGenLevel level = context.getLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.getX(), 0, origin.getZ());
        DimensionType dimension = level.dimensionType();
        if (!dimension.hasCeiling()) {
            pos.setY(context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()));
            if (pos.getY() > context.getMinY() && fits(level, pos)) {
                output.accept(pos.immutable());
            }
            return;
        }
        int low = dimension.minY() + ROOFED_MARGIN;
        int high = dimension.minY() + dimension.logicalHeight() - ROOFED_MARGIN;
        for (int attempt = 0; attempt < ROOFED_COLUMNS * ROOFED_TRIES && high > low; attempt++) {
            if (attempt > 0 && attempt % ROOFED_TRIES == 0) {
                // The next column, somewhere in the same chunk.
                pos.setX((origin.getX() & ~15) + random.nextInt(16)).setZ((origin.getZ() & ~15) + random.nextInt(16));
            }
            pos.setY(low + random.nextInt(high - low));
            for (int step = 0; step < ROOFED_SCAN && pos.getY() > low && free(level.getBlockState(pos)); step++) {
                pos.move(Direction.DOWN);
            }
            pos.move(Direction.UP);
            if (fits(level, pos)) {
                output.accept(pos.immutable());
                return;
            }
        }
    }

    /** A free spot with a block under it that a nest can stand on. */
    private static boolean fits(WorldGenLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return free(level.getBlockState(pos)) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Air, or something a nest takes the place of. No fluid, and no tall plant: its upper half would be left hanging. */
    private static boolean free(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof DoublePlantBlock);
    }

    @Override
    public MapCodec<NestGround> codec() {
        return CODEC;
    }
}
