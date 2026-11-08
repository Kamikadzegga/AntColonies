package com.antcolonies.content.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * A block of a colony build: the Terrarium and the chambers. Each knows which of its six sides touch another part of a
 * colony; the models draw their wooden frame only on the outer edges, so touching parts look like one big build.
 */
public abstract class ColonyPartBlock extends ColonyEntityBlock {
    public static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;

    protected ColonyPartBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (BooleanProperty property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        CONNECTED.values().forEach(builder::add);
    }

    /** Whether a neighbour is part of a colony build (any colony: the frame is only a look). */
    public static boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof ColonyPartBlock;
    }

    public static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTED.entrySet()) {
            state = state.setValue(entry.getValue(), joins(level.getBlockState(pos.relative(entry.getKey()))));
        }
        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connect(state, context.getLevel(), context.getClickedPos());
    }

    /** Placed without a player (commands, structures, tests): it still looks at its neighbours. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState connected = connect(state, level, pos);
        if (connected != state) {
            level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(CONNECTED.get(direction), joins(neighbourState));
    }
}
