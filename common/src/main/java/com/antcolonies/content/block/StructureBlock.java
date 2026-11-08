package com.antcolonies.content.block;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** One of the colony structures (see {@link StructureKind}); all share one block entity type. */
public class StructureBlock extends NetworkEntityBlock {
    private final StructureKind kind;
    private final VoxelShape shape;

    public StructureBlock(StructureKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.shape = switch (kind) {
            case RESOURCE_PILE -> Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 10, 15));
            case SCAVENGING_SITE, FARMER_GARDEN, FIGHTING_SITE -> Block.box(0, 0, 0, 16, 12, 16);
            case COLONY_CONNECTOR -> Block.box(2, 2, 2, 14, 14, 14);
            default -> Shapes.block();
        };
    }

    public StructureKind structureKind() {
        return kind;
    }

    @Override
    public Role role(BlockState state) {
        return kind == StructureKind.COLONY_CONNECTOR ? Role.CONNECTOR : Role.STRUCTURE;
    }

    @Override
    public StructureKind kind(BlockState state) {
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
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.STRUCTURE.get(), StructureBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        com.antcolonies.client.ClientHooks.structureParticles(level, pos, kind, random);
    }
}
