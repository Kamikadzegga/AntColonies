package com.antcolonies.content.block;

import com.antcolonies.AntColonies;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wild ant nest: an anthill found in the species' home biomes. Tweezers (with an empty Test Tube) pull workers out of
 * it, sometimes a Queen; disturbing it brings soldiers out to defend it. A raided nest needs time to recover.
 */
public class WildNestBlock extends Block {
    public static final BooleanProperty RECOVERING = BooleanProperty.create("recovering");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

    private final Identifier species;

    public WildNestBlock(String species, Properties properties) {
        super(properties);
        this.species = AntColonies.id(species);
        registerDefaultState(stateDefinition.any().setValue(RECOVERING, false));
    }

    public Identifier species() {
        return species;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RECOVERING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Ants were taken: the nest recovers over the configured time. */
    public void raid(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        level.setBlock(pos, state.setValue(RECOVERING, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, Math.max(1, AntConfig.get(AntConfig.NEST_COOLDOWN_SECONDS) * 20));
        defend(level, pos, player);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RECOVERING)) {
            level.setBlock(pos, state.setValue(RECOVERING, false), Block.UPDATE_ALL);
        }
    }

    /** Breaking or raiding the nest brings out one to three soldiers. */
    public void defend(ServerLevel level, BlockPos pos, Player player) {
        int soldiers = 1 + level.getRandom().nextInt(3);
        for (int i = 0; i < soldiers; i++) {
            WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.TRIGGERED);
            if (ant == null) {
                continue;
            }
            ant.setSpecies(species);
            ant.setCaste(Caste.SOLDIER);
            ant.snapTo(pos.getX() + 0.5 + level.getRandom().nextGaussian() * 0.4, pos.getY() + 0.6, pos.getZ() + 0.5 + level.getRandom().nextGaussian() * 0.4,
                    level.getRandom().nextFloat() * 360, 0);
            if (!WildAnt.ignores(player)) {
                ant.setTarget(player); // a player in full Chitin smells like the colony
            }
            level.addFreshEntity(ant);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !player.isCreative()) {
            defend(serverLevel, pos, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        com.antcolonies.client.ClientHooks.nestParticles(level, pos, random);
    }
}
