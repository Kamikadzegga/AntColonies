package com.antcolonies.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Anthill soil, loose or packed: one block in several tones, set by the anthill that holds it or the ground it is laid on. */
public class AnthillSoilBlock extends Block {
    public static final EnumProperty<SoilTone> TONE = EnumProperty.create("tone", SoilTone.class);

    public AnthillSoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TONE, SoilTone.EARTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TONE);
    }

    /** The tone of the block it is laid against; against air or plain earth, of the block under it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        SoilTone tone = SoilTone.of(context.getLevel().getBlockState(pos.relative(context.getClickedFace().getOpposite())));
        if (tone == SoilTone.EARTH) {
            tone = SoilTone.of(context.getLevel().getBlockState(pos.below()));
        }
        return defaultBlockState().setValue(TONE, tone);
    }
}
