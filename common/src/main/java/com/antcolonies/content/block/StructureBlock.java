package com.antcolonies.content.block;

import com.antcolonies.client.ClientHooks;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** One of the colony chambers (see {@link StructureKind}); all share one block entity type. */
public class StructureBlock extends ColonyPartBlock {
    private final StructureKind kind;
    private final VoxelShape shape;

    public StructureBlock(StructureKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        // Every chamber is a full block: a nest behind a front glass.
        this.shape = Shapes.block();
    }

    public StructureKind structureKind() {
        return kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, ModBlockEntities.STRUCTURE.get(),
                    (l, pos, s, be) -> ClientHooks.structureTick(be));
        }
        return createTickerHelper(type, ModBlockEntities.STRUCTURE.get(), StructureBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ClientHooks.structureParticles(level, pos, kind, random);
    }
}
