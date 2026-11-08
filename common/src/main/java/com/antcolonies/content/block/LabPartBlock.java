package com.antcolonies.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * One of the Genetic Laboratory's five parts. FORMED (set by the controller) lights its glass and crystals; a part
 * of no formed laboratory stays dark.
 */
public class LabPartBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public enum Part {
        DNA_ANALYZER("dna_analyzer"),
        MUTATION_CHAMBER("mutation_chamber"),
        GENETIC_STORAGE("genetic_storage"),
        QUEEN_ENHANCEMENT_CHAMBER("queen_enhancement_chamber"),
        GENETIC_ENERGY_CORE("genetic_energy_core");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        public String id() {
            return name;
        }
    }

    private final Part part;

    public LabPartBlock(Part part, Properties properties) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(FACING, Direction.NORTH));
    }

    public Part part() {
        return part;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public static void setFormed(ServerLevel level, BlockPos pos, boolean formed) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(FORMED) && state.getValue(FORMED) != formed) {
            level.setBlock(pos, state.setValue(FORMED, formed), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FORMED)) {
            com.antcolonies.client.ClientHooks.labParticles(level, pos, part, random);
        }
    }
}
