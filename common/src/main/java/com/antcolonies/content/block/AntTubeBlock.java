package com.antcolonies.content.block;

import java.util.EnumMap;
import java.util.Map;

import com.antcolonies.content.blockentity.AntTubeBlockEntity;
import com.antcolonies.colony.model.ColonyNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Glass Ant Tube: joins colony blocks it touches, like a pipe. While its colony has ants, ants walk through it
 * (drawn only; the ACTIVE state is set by the colony's network scan).
 */
public class AntTubeBlock extends BaseEntityBlock implements ColonyNetworkBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        ARMS.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        ARMS.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        ARMS.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
        ARMS.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        ARMS.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
    }

    private final Map<BlockState, VoxelShape> shapes = new java.util.HashMap<>();

    public AntTubeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(ACTIVE, false);
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    public Role role(BlockState state) {
        return Role.CONDUIT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
        SIDES.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(SIDES.get(direction), connects(context.getLevel(), context.getClickedPos().relative(direction)));
        }
        return state;
    }

    private static boolean connects(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ColonyNetworkBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(SIDES.get(direction), neighbourState.getBlock() instanceof ColonyNetworkBlock);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Direction direction : Direction.values()) {
                if (s.getValue(SIDES.get(direction))) {
                    shape = Shapes.or(shape, ARMS.get(direction));
                }
            }
            return shape;
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            ColonyNetwork.changed(level);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ColonyNetwork.changed(level);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntTubeBlockEntity(pos, state);
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    /** Shows or hides the tube's ants (no neighbour updates: nothing else cares). */
    public static void setActive(ServerLevel level, BlockPos pos, boolean active, net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier species) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AntTubeBlock)) {
            return;
        }
        if (state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(pos) instanceof com.antcolonies.content.blockentity.AntTubeBlockEntity tube) {
            tube.setSpecies(active ? species : null);
        }
    }

    public static boolean isActive(BlockState state) {
        return state.hasProperty(ACTIVE) && state.getValue(ACTIVE);
    }
}
