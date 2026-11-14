package com.antcolonies.content.block;

import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.blockentity.BroodClusterBlockEntity;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModDecor;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A pile of ant eggs (Ant Egg Cluster) or larvae (Larva Cluster) on the floor. The ones an anthill was built with are
 * alive ({@link #NATURAL}): in time an egg cluster becomes a larva cluster, and a larva cluster 1 to 3 ants of that
 * anthill. Not craftable: broken, a live cluster is lost unless taken with Silk Touch; its item (with the species) goes
 * into a working colony's Terrarium, or is placed again, but then only as a decoration that never hatches.
 */
public class BroodClusterBlock extends Block implements EntityBlock {
    /** Whether an anthill was built with it (and it hatches). A placed cluster never is. */
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 5, 15);
    /** How far round a larva cluster it counts wild ants before it hatches. */
    private static final double CROWD_RANGE = 24;

    public BroodClusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NATURAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NATURAL);
    }

    /** Whether this is the Ant Egg Cluster (else the Larva Cluster). */
    public boolean eggs() {
        return this == ModDecor.ANT_EGG_CLUSTER.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BroodClusterBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(NATURAL);
    }

    /**
     * The chance a random tick moves a live cluster on: a block gets one about every 68 seconds (at the default random
     * tick speed), so this makes a stage last {@code broodClusterMinutes} on average.
     */
    public static double stageChance() {
        return Math.min(1, 68.27 / (AntConfig.get(AntConfig.BROOD_CLUSTER_MINUTES) * 60));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(NATURAL) && random.nextDouble() < stageChance()) {
            grow(state, level, pos, random);
        }
    }

    /**
     * Moves a live cluster on a stage: eggs become larvae; larvae become 1 to 3 workers of the anthill (waiting while
     * {@code anthillMaxAnts} wild ants are already about). Returns whether it did.
     */
    public boolean grow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(NATURAL)) {
            return false;
        }
        BroodClusterBlockEntity cluster = level.getBlockEntity(pos) instanceof BroodClusterBlockEntity be ? be : null;
        Identifier species = cluster == null || cluster.species() == null ? ModRegistries.COMMON : cluster.species();
        BlockPos anthill = cluster == null ? null : cluster.anthill();
        if (eggs()) {
            level.setBlock(pos, ModDecor.LARVA_CLUSTER.get().defaultBlockState().setValue(NATURAL, true), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof BroodClusterBlockEntity larvae) {
                larvae.set(species, anthill);
            }
            level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 0.6F, 1.4F);
            return true;
        }
        if (level.getEntitiesOfClass(WildAnt.class, new AABB(pos).inflate(CROWD_RANGE)).size() >= AntConfig.get(AntConfig.ANTHILL_MAX_ANTS)) {
            return false;
        }
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.BREEDING);
            if (ant == null) {
                continue;
            }
            ant.snapTo(pos.getX() + 0.5 + random.nextGaussian() * 0.2, pos.getY(), pos.getZ() + 0.5 + random.nextGaussian() * 0.2,
                    random.nextFloat() * 360, 0);
            ant.setSpecies(species);
            ant.setCaste(Caste.WORKER);
            if (anthill != null) {
                // One of the anthill's own: it keeps to the room it hatched in, and answers the anthill's alarm.
                ant.setGuardian(true);
                ant.setAnthill(anthill);
                ant.setHomeTo(pos, 6);
                ant.setPersistenceRequired();
            }
            level.addFreshEntity(ant);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
        return true;
    }

}
