package com.antcolonies.content.block;

import com.antcolonies.colony.model.StructureKind;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A block that is part of colony networks: see {@link com.antcolonies.colony.model.ColonyNetwork}. */
public interface ColonyNetworkBlock {
    enum Role {
        TERRARIUM, CONDUIT, STRUCTURE, CONNECTOR
    }

    Role role(BlockState state);

    /** The structure it is, for {@link Role#STRUCTURE} and {@link Role#CONNECTOR} blocks. */
    default @Nullable StructureKind kind(BlockState state) {
        return null;
    }
}
