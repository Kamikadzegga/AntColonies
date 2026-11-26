package com.antcolonies.content.blockentity;

import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * A nest of the world's own ({@code home=false}). While a player is near it keeps one worker walking around it, so the
 * nest is easier to spot. Its ants are the nest block's species, or a nest dweller's where one is at home.
 */
public class WildNestBlockEntity extends BlockEntity implements RaidedNest {
    /** How often (ticks) a nest looks whether its ant is out. */
    public static final int ANT_CHECK_INTERVAL = 100;
    /** A player this close (blocks) brings the nest's ant out. */
    public static final int ANT_PLAYER_RANGE = 48;
    /** The ant goes back in once no player is this close: further than {@link #ANT_PLAYER_RANGE}, so it doesn't come and go. */
    public static final int ANT_LEAVE_RANGE = 64;

    /**
     * Species that live in any kind of wild nest standing in their own biomes: for species with no nest block of their own
     * (Allthemodium and Vibranium Ants in The Other).
     */
    public static final TagKey<AntSpecies> NEST_DWELLERS = TagKey.create(ModRegistries.SPECIES, AntColonies.id("nest_dwellers"));

    /** The game time from which the nest sends out an ant again, after its last one was caught or killed. */
    private long nextAnt;
    /** Whose nest this is, decided by where it stands the first time it is asked (null until then). */
    private @Nullable Identifier species;
    private long recoveredAt;

    @Override
    public long recoveredAt() {
        return recoveredAt;
    }

    @Override
    public void setRecoveredAt(long gameTime) {
        recoveredAt = gameTime;
        setChanged();
    }

    public WildNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WILD_NEST.get(), pos, state);
    }

    /** Only while the nest is wild: turned into a home nest in place, it gets a {@link HomeNestBlockEntity}. */
    @Override
    public boolean isValidBlockState(BlockState state) {
        return super.isValidBlockState(state) && !state.getValue(WildNestBlock.HOME);
    }

    /** The species of this nest's ants. */
    public Identifier species(ServerLevel level) {
        if (species == null && getBlockState().getBlock() instanceof WildNestBlock block) {
            species = dweller(level.registryAccess(), level.getBiome(worldPosition), worldPosition, block.species());
            setChanged();
        }
        return species == null ? ModRegistries.COMMON : species;
    }

    /** A nest dweller at home in the biome (of several, one by the nest's spot), or the nest block's own species. */
    public static Identifier dweller(RegistryAccess registries, Holder<Biome> biome, BlockPos pos, Identifier own) {
        List<Identifier> dwellers = registries.lookup(ModRegistries.SPECIES).map(lookup -> lookup.listElements()
                .filter(entry -> entry.is(NEST_DWELLERS) && entry.value().spawn().isPresent() && biome.is(entry.value().spawn().get()))
                .map(entry -> entry.key().identifier()).sorted().toList()).orElse(List.of());
        return dwellers.isEmpty() ? own : dwellers.get(Math.floorMod(Mth.getSeed(pos), dwellers.size()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WildNestBlockEntity nest) {
        WildNestBlock.recover(level, pos, state, nest);
        // Each nest on its own tick of the interval, by where it stands.
        if (level instanceof ServerLevel server && Math.floorMod(level.getGameTime() + pos.asLong(), ANT_CHECK_INTERVAL) == 0) {
            nest.keepAntOut(server, server.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ANT_PLAYER_RANGE));
        }
    }

    /** Sends the nest's one ant out if a player is near and none is out; returns the ant it sent, or null. */
    public @Nullable WildAnt keepAntOut(ServerLevel level, boolean playerNear) {
        if (!playerNear || !AntConfig.get(AntConfig.NEST_ANT) || level.getGameTime() < nextAnt
                || !(getBlockState().getBlock() instanceof WildNestBlock)) {
            return null;
        }
        List<WildAnt> out = level.getEntitiesOfClass(WildAnt.class, new AABB(worldPosition).inflate(WildAnt.WILD_NEST_RANGE + 8),
                ant -> worldPosition.equals(ant.wildNest()));
        // One is enough: a second can turn up when its chunk loads after the nest's.
        for (int extra = 1; extra < out.size(); extra++) {
            out.get(extra).discard();
        }
        BlockPos exit = HomeNestBlockEntity.exit(level, worldPosition);
        if (!out.isEmpty() || exit == null) {
            return null;
        }
        WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (ant == null) {
            return null;
        }
        ant.setSpecies(species(level));
        ant.setCaste(Caste.WORKER);
        ant.snapTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        ant.setWildNest(worldPosition);
        level.addFreshEntity(ant);
        return ant;
    }

    /** The nest's ant was caught or killed: the next one comes out after a while. */
    public void antLost() {
        if (level != null) {
            nextAnt = level.getGameTime() + AntConfig.get(AntConfig.NEST_ANT_SECONDS) * 20L;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("next_ant", nextAnt);
        output.putLong("recovered_at", recoveredAt);
        if (species != null) {
            output.putString("species", species.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        nextAnt = input.getLongOr("next_ant", 0);
        recoveredAt = input.getLongOr("recovered_at", 0);
        species = input.getString("species").map(Identifier::tryParse).orElse(null);
    }
}
