package com.antcolonies.content.blockentity;

import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A nest of the world's own ({@code home=false}). It holds nothing: the game expects a block entity from every block
 * that can have one, and warns in the log for each nest it generates without.
 */
public class WildNestBlockEntity extends BlockEntity {
    public WildNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WILD_NEST.get(), pos, state);
    }

    /** Only while the nest is wild: turned into a home nest in place, it gets a {@link HomeNestBlockEntity}. */
    @Override
    public boolean isValidBlockState(BlockState state) {
        return super.isValidBlockState(state) && !state.getValue(WildNestBlock.HOME);
    }
}
