package com.antcolonies.content.block;

import com.antcolonies.colony.model.ColonyNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Packed soil with ant galleries through it: the underground way to join colony blocks. Joins like a Glass Ant Tube,
 * but is a full block, so it can be buried.
 */
public class TunnelBlock extends Block implements ColonyNetworkBlock {
    public TunnelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Role role(BlockState state) {
        return Role.CONDUIT;
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
}
