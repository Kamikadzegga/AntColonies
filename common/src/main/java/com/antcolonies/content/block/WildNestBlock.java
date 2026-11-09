package com.antcolonies.content.block;

import com.antcolonies.AntColonies;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.blockentity.HomeNestBlockEntity;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.colony.species.Caste;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * An ant nest. Wild ones are anthills found in the species' home biomes: Tweezers (with an empty Test Tube) pull workers
 * out, sometimes a Queen; disturbing one brings soldiers out to defend it, and a raided nest needs time to recover.
 * Silk Touch picks a nest up; placed by a player it becomes a home nest ({@code home=true}): ants live in it and work the
 * land around it like bees from a beehive, and never turn on you (see {@link HomeNestBlockEntity}).
 */
public class WildNestBlock extends Block implements EntityBlock {
    public static final BooleanProperty RECOVERING = BooleanProperty.create("recovering");
    /** Placed by a player: ants live in it and forage around it. World generation places nests without it. */
    public static final BooleanProperty HOME = BooleanProperty.create("home");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

    private final Identifier species;

    public WildNestBlock(String species, Properties properties) {
        super(properties);
        this.species = AntColonies.id(species);
        registerDefaultState(stateDefinition.any().setValue(RECOVERING, false).setValue(HOME, false));
    }

    public Identifier species() {
        return species;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RECOVERING, HOME);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HOME, true);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HOME) ? new HomeNestBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && state.getValue(HOME) && type == ModBlockEntities.HOME_NEST.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<HomeNestBlockEntity>) HomeNestBlockEntity::serverTick : null;
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

    /** Breaking or raiding a wild nest brings out one to three soldiers; a home nest's ants know you. */
    public void defend(ServerLevel level, BlockPos pos, Player player) {
        if (level.getBlockState(pos).getOptionalValue(HOME).orElse(false)) {
            return;
        }
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
