package com.antcolonies.content.block;

import com.antcolonies.colony.model.StructureKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Nether and End Foraging Sites: a chamber with a little portal that glows ({@link #ACTIVE}) while ants are out through it. */
public class ExpeditionSiteBlock extends StructureBlock {
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public ExpeditionSiteBlock(StructureKind kind, Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) {
            return;
        }
        // Motes drifting out of the little portal.
        boolean nether = structureKind() == StructureKind.NETHER_FORAGING_SITE;
        net.minecraft.core.Direction front = state.getValue(FACING);
        if (upright(state)) {
            // In a stack the portal is in the chamber behind the glass: its motes drift out before it, or behind it.
            double side = random.nextBoolean() ? 0.56 : -0.56;
            double along = random.nextDouble() * 0.4 - 0.2;
            level.addParticle(nether ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5 + front.getStepX() * side + front.getStepZ() * along, pos.getY() + 0.3 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.5 + front.getStepZ() * side + front.getStepX() * along, front.getStepX() * side * 0.2, 0.02, front.getStepZ() * side * 0.2);
            return;
        }
        // The portal stands a quarter block in from the front glass, across the chamber.
        double across = random.nextDouble() * 0.5 - 0.25;
        double x = pos.getX() + 0.5 + front.getStepX() * 0.3 + front.getStepZ() * across;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.3 + front.getStepX() * across;
        level.addParticle(nether ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 0.35 + random.nextDouble() * 0.4, z,
                -front.getStepX() * 0.15, 0.02, -front.getStepZ() * 0.15);
    }

    // 26.1.2: every block still names its codec.
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(properties -> new ExpeditionSiteBlock(structureKind(), properties));
    }
}
