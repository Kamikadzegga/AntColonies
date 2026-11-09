package com.antcolonies.content.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.antcolonies.content.blockentity.HomeNestBlockEntity;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.jspecify.annotations.Nullable;

/**
 * A wild ant: workers wander and flee, soldiers defend themselves and each other, Queens are rare. Its species comes
 * from the biome it spawned in (the species' {@code spawn_biomes}). Caught with Tweezers into Test Tubes. These are the
 * only ant entities in the mod: colonies are numbers, never entities.
 */
public class WildAnt extends PathfinderMob implements AntLike {
    private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(WildAnt.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> DATA_CASTE = SynchedEntityData.defineId(WildAnt.class, EntityDataSerializers.BYTE);

    /** The colour of what it carries in its mandibles (0: nothing), for the renderer. */
    private static final EntityDataAccessor<Integer> DATA_CARRY = SynchedEntityData.defineId(WildAnt.class, EntityDataSerializers.INT);

    /** Guards a giant anthill: its soldiers attack players who come close (not ones in a full Chitin set). */
    private boolean guardian;

    /** How far from its home nest an ant works. */
    public static final int NEST_RANGE = 10;
    /** Crops and saplings an ant grows on one trip, like a bee's limit. */
    private static final int TENDS_PER_TRIP = 5;
    /** The home nest it lives in (a nest a player placed), or null for a wild ant. */
    private @Nullable BlockPos nest;
    /** What it harvested, on its way home. */
    private final List<ItemStack> carried = new ArrayList<>();
    private boolean returning;
    private int outTicks;
    private int tripTicks;
    private int tended;

    public WildAnt(EntityType<? extends WildAnt> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    public static void registerSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.WILD_ANT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WildAnt::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static boolean checkSpawnRules(EntityType<WildAnt> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
                                          RandomSource random) {
        if (reason == EntitySpawnReason.NATURAL && random.nextDouble() >= AntConfig.get(AntConfig.WILD_SPAWN_CHANCE)) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return Mob.checkMobSpawnRules(type, level, reason, pos, random) && below.isSolid();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return caste() == Caste.SOLDIER && super.canUse();
            }
        });
        goalSelector.addGoal(2, new PanicGoal(this, 1.4) {
            @Override
            public boolean canUse() {
                return caste() != Caste.SOLDIER && super.canUse();
            }
        });
        goalSelector.addGoal(3, new ReturnToNestGoal());
        goalSelector.addGoal(4, new WorkLandGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 4.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Hurting any ant of a group brings its soldiers.
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(WildAnt.class));
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> guardian && caste() == Caste.SOLDIER));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, ModRegistries.COMMON.toString());
        builder.define(DATA_CASTE, (byte) Caste.WORKER.ordinal());
        builder.define(DATA_CARRY, 0);
    }

    @Override
    public Identifier species() {
        Identifier id = Identifier.tryParse(entityData.get(DATA_SPECIES));
        return id == null ? ModRegistries.COMMON : id;
    }

    public void setSpecies(Identifier species) {
        entityData.set(DATA_SPECIES, species.toString());
    }

    @Override
    public Caste caste() {
        byte id = entityData.get(DATA_CASTE);
        return id >= 0 && id < Caste.values().length ? Caste.values()[id] : Caste.WORKER;
    }

    public void setCaste(Caste caste) {
        entityData.set(DATA_CASTE, (byte) caste.ordinal());
        applyCaste();
    }

    /** Soldiers are bigger and hit harder; Queens are the biggest. */
    private void applyCaste() {
        Caste caste = caste();
        set(Attributes.SCALE, caste == Caste.QUEEN ? 1.6 : caste == Caste.SOLDIER ? 1.3 : 1.0);
        set(Attributes.MAX_HEALTH, caste == Caste.QUEEN ? 10 : caste == Caste.SOLDIER ? 8 : 4);
        set(Attributes.ATTACK_DAMAGE, caste == Caste.SOLDIER ? 2.5 : 1.0);
        set(Attributes.MOVEMENT_SPEED, caste == Caste.QUEEN ? 0.18 : 0.28);
        setHealth(getMaxHealth());
    }

    private void set(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        if (reason != EntitySpawnReason.TRIGGERED) {
            setSpecies(speciesFor(level, blockPosition(), getRandom()));
            double roll = getRandom().nextDouble();
            setCaste(roll < AntConfig.get(AntConfig.WILD_QUEEN_CHANCE) ? Caste.QUEEN
                    : roll < AntConfig.get(AntConfig.WILD_QUEEN_CHANCE) + AntConfig.get(AntConfig.WILD_SOLDIER_CHANCE) ? Caste.SOLDIER : Caste.WORKER);
        }
        return data;
    }

    /** A species that lives in the biome here, or Common Ants where none does. */
    public static Identifier speciesFor(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);
        List<Identifier> candidates = new ArrayList<>();
        level.registryAccess().lookup(ModRegistries.SPECIES).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            AntSpecies species = entry.value();
            if (species.spawn().isPresent() && biome.is(species.spawn().get())) {
                candidates.add(entry.key().identifier());
            }
        }));
        return candidates.isEmpty() ? ModRegistries.COMMON : candidates.get(random.nextInt(candidates.size()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("species", species().toString());
        output.putString("caste", caste().getSerializedName());
        output.putBoolean("guardian", guardian);
        if (nest != null) {
            output.putLong("nest", nest.asLong());
            output.putBoolean("returning", returning);
            output.putInt("out_ticks", outTicks);
            output.putInt("trip_ticks", tripTicks);
            output.putInt("tended", tended);
            output.store("carried", ItemStack.CODEC.listOf(), List.copyOf(carried));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setSpecies(Identifier.tryParse(input.getStringOr("species", ModRegistries.COMMON.toString())) instanceof Identifier id ? id : ModRegistries.COMMON);
        guardian = input.getBooleanOr("guardian", false);
        input.getLong("nest").ifPresent(packed -> {
            setNest(BlockPos.of(packed));
            returning = input.getBooleanOr("returning", false);
            outTicks = input.getIntOr("out_ticks", 0);
            tripTicks = input.getIntOr("trip_ticks", tripTicks);
            tended = input.getIntOr("tended", 0);
            carried.clear();
            input.read("carried", ItemStack.CODEC.listOf()).ifPresent(carried::addAll);
            updateCarry();
        });
        String caste = input.getStringOr("caste", "worker");
        for (Caste value : Caste.values()) {
            if (value.getSerializedName().equals(caste)) {
                entityData.set(DATA_CASTE, (byte) value.ordinal());
            }
        }
    }

    public boolean guardian() {
        return guardian;
    }

    /** Makes it one of an anthill's defenders, staying near it. */
    public void setGuardian(boolean guardian) {
        this.guardian = guardian;
    }

    /** A player in a full Chitin set smells like the colony: no wild ant attacks them. */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return !ignores(target) && super.canAttack(target);
    }

    /** Whether wild ants leave this one alone: a player in a full Chitin set. */
    public static boolean ignores(net.minecraft.world.entity.LivingEntity target) {
        return target instanceof Player player && com.antcolonies.content.item.ChitinGear.fullSet(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return caste() != Caste.QUEEN && !guardian && nest == null;
    }

    // --- Home nests: ants that live in a nest a player placed, working the land around it like bees ---

    /** Makes it an ant of the home nest at {@code pos}: it works within {@link #NEST_RANGE} blocks and goes back in. */
    public void setNest(BlockPos pos) {
        nest = pos.immutable();
        setHomeTo(nest, NEST_RANGE);
        setPersistenceRequired();
        tripTicks = 1200 + getRandom().nextInt(1200);
    }

    public @Nullable BlockPos nest() {
        return nest;
    }

    /** Ends its trip: it heads back into its nest. */
    public boolean returning() {
        return returning;
    }

    public void goHome() {
        returning = true;
    }

    /** What it carries home: nothing unless it harvested. */
    public List<ItemStack> carried() {
        return carried;
    }

    /** The colour of its load for the renderer (0: none). */
    public int carryColor() {
        return entityData.get(DATA_CARRY);
    }

    private void updateCarry() {
        entityData.set(DATA_CARRY, carried.isEmpty() ? 0 : loadColor(carried.getFirst()));
    }

    /** A crumb's colour for a harvest: golden grain, orange carrots and so on. */
    private static int loadColor(ItemStack stack) {
        Item item = stack.getItem();
        int rgb = item == Items.WHEAT || item == Items.WHEAT_SEEDS ? 0xE3C95A : item == Items.CARROT ? 0xE8862A : item == Items.POTATO ? 0xC9A35A
                : item == Items.BEETROOT || item == Items.BEETROOT_SEEDS ? 0x9E2A2B : item == Items.SWEET_BERRIES ? 0xB8202A
                : item == Items.NETHER_WART ? 0x8A1A1A : item == Items.PUMPKIN ? 0xE3902D : item == Items.MELON_SLICE || item == Items.MELON ? 0x6EA33A
                : 0xD8C49A;
        return 0xFF000000 | rgb;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (nest != null && !level().isClientSide() && !returning) {
            outTicks++;
            if (outTicks > tripTicks || !HomeNestBlockEntity.foragingWeather(level())) {
                returning = true;
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (nest != null && level() instanceof ServerLevel server) {
            if (server.getBlockEntity(nest) instanceof HomeNestBlockEntity home) {
                home.lost();
            }
            carried.forEach(stack -> spawnAtLocation(server, stack));
            carried.clear();
        }
        super.die(source);
    }

    /** Back in the nest: what it carried goes into the nest's store (what doesn't fit drops by the nest), and it is gone. */
    private void enterNest(ServerLevel level) {
        if (!(level.getBlockEntity(nest) instanceof HomeNestBlockEntity home)) {
            // The nest is gone: it lives wild from now on.
            nest = null;
            clearHome();
            returning = false;
            return;
        }
        home.returned(ItemStack.EMPTY);
        for (ItemStack stack : carried) {
            ItemStack rest = home.store(stack);
            if (!rest.isEmpty()) {
                Block.popResource(level, nest.above(), rest);
            }
        }
        carried.clear();
        discard();
    }

    /** A crop, berry bush, nether wart or stem fruit here that is ripe for harvest. */
    public static boolean ripe(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (state.is(Blocks.NETHER_WART)) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockState stem = level.getBlockState(pos.relative(side));
                if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A crop or sapling here that tending would grow on. */
    public static boolean growable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.SAPLINGS) || state.is(BlockTags.BEE_GROWABLES) && !ripe(level, pos)
                && !(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state));
    }

    /**
     * Harvests the ripe crop at {@code pos} and replants it: its drops, less the one seed it keeps back to replant (as a
     * farmer would), go into its mandibles. Berries are picked and the bush left; pumpkins and melons are cut off the stem.
     */
    public boolean harvest(ServerLevel level, BlockPos pos) {
        if (!ripe(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>();
        BlockState after;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            drops.add(new ItemStack(Items.SWEET_BERRIES, 1 + getRandom().nextInt(2) + (age == 3 ? 1 : 0)));
            after = state.setValue(SweetBerryBushBlock.AGE, 1);
        } else if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            drops.addAll(Block.getDrops(state, level, pos, null, this, ItemStack.EMPTY));
            after = Blocks.AIR.defaultBlockState();
        } else {
            drops.addAll(Block.getDrops(state, level, pos, null, this, ItemStack.EMPTY));
            ItemStack seed = state.getCloneItemStack(level, pos, false);
            for (ItemStack drop : drops) {
                if (ItemStack.isSameItem(drop, seed)) {
                    drop.shrink(1);
                    break;
                }
            }
            after = state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(0) : state.setValue(NetherWartBlock.AGE, 0);
        }
        level.setBlockAndUpdate(pos, after);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5F, 1.2F);
        drops.stream().filter(stack -> !stack.isEmpty()).forEach(carried::add);
        updateCarry();
        return true;
    }

    /** Grows the crop or sapling at {@code pos} one stage, as a bee does; true if it grew. */
    public boolean tend(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState grown = null;
        if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
            grown = crop.getStateForAge(crop.getAge(state) + 1);
        } else if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) < StemBlock.MAX_AGE) {
            grown = state.setValue(StemBlock.AGE, state.getValue(StemBlock.AGE) + 1);
        } else if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) < 3) {
            grown = state.setValue(SweetBerryBushBlock.AGE, state.getValue(SweetBerryBushBlock.AGE) + 1);
        } else if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) {
            grown = state.setValue(NetherWartBlock.AGE, state.getValue(NetherWartBlock.AGE) + 1);
        } else if (state.getBlock() instanceof SaplingBlock sapling) {
            level.levelEvent(2011, pos, 15);
            sapling.advanceTree(level, pos, state, getRandom());
            return true;
        }
        if (grown == null) {
            return false;
        }
        level.levelEvent(2011, pos, 15);
        level.setBlockAndUpdate(pos, grown);
        return true;
    }

    /** Goes back into its nest when its trip is over, it carries a harvest, or night or rain comes. */
    private class ReturnToNestGoal extends Goal {
        private int retry;
        private int waited;

        ReturnToNestGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return nest != null && returning;
        }

        @Override
        public void start() {
            retry = 0;
            waited = 0;
        }

        @Override
        public void tick() {
            if (nest == null || !(level() instanceof ServerLevel server)) {
                return;
            }
            waited++;
            // It crawls in from any side: right beside the nest is close enough. Stuck long enough, it finds its own way.
            Vec3 nestCenter = Vec3.atBottomCenterOf(nest);
            if (distanceToSqr(nestCenter) < 1.8 || waited > 1200) {
                enterNest(server);
                return;
            }
            if (--retry <= 0) {
                retry = 20;
                getNavigation().moveTo(nestCenter.x, nestCenter.y, nestCenter.z, 1.0);
            }
        }
    }

    /**
     * Works the land around the nest: walks to a ripe crop and harvests it (then heads home with it), or to a growing one
     * or a sapling and tends it for a while, growing it now and then.
     */
    private class WorkLandGoal extends Goal {
        private @Nullable BlockPos target;
        private boolean harvesting;
        private int cooldown;
        private int stay;
        private int retry;

        WorkLandGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (nest == null || returning || !(level() instanceof ServerLevel) || --cooldown > 0) {
                return false;
            }
            cooldown = 40 + getRandom().nextInt(40);
            target = findWork();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && !returning && stay > 0;
        }

        @Override
        public void start() {
            stay = 300;
            retry = 0;
        }

        @Override
        public void stop() {
            target = null;
        }

        @Override
        public void tick() {
            if (target == null || !(level() instanceof ServerLevel server)) {
                return;
            }
            stay--;
            Vec3 spot = Vec3.atBottomCenterOf(target);
            // Within reach from the crop's own block or any block around it, diagonals too: a path can end beside the
            // target, and an ant that waited to stand exactly on it could wait for ever.
            if (distanceToSqr(spot) > 2.8) {
                if (--retry <= 0) {
                    retry = 20;
                    var path = getNavigation().createPath(target, 0);
                    if (path != null) {
                        getNavigation().moveTo(path, 0.9);
                    }
                }
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(spot);
            if (harvesting) {
                if (harvest(server, target) && !carried.isEmpty()) {
                    returning = true;
                }
                target = null;
            } else if (!growable(server, target) || tended >= TENDS_PER_TRIP) {
                target = null;
            } else if (getRandom().nextDouble() < AntConfig.get(AntConfig.NEST_CROP_GROWTH) && tend(server, target)) {
                tended++;
                target = null;
            }
        }

        /** The nearest-ish ripe crop around the nest (if harvesting is on and its mandibles are free), else a growing one. */
        private @Nullable BlockPos findWork() {
            List<BlockPos> ripe = new ArrayList<>();
            List<BlockPos> growing = new ArrayList<>();
            boolean harvest = AntConfig.get(AntConfig.NEST_HARVEST) && carried.isEmpty();
            boolean grow = tended < TENDS_PER_TRIP && AntConfig.get(AntConfig.NEST_CROP_GROWTH) > 0;
            for (BlockPos pos : BlockPos.betweenClosed(nest.offset(-NEST_RANGE, -2, -NEST_RANGE), nest.offset(NEST_RANGE, 2, NEST_RANGE))) {
                if (harvest && ripe(level(), pos)) {
                    ripe.add(pos.immutable());
                } else if (grow && growable(level(), pos)) {
                    growing.add(pos.immutable());
                }
            }
            harvesting = !ripe.isEmpty();
            List<BlockPos> choices = harvesting ? ripe : growing;
            if (choices.isEmpty()) {
                return null;
            }
            // Of three picked at random, the closest: ants spread over a field but don't cross it for every crop.
            BlockPos best = null;
            for (int i = 0; i < 3; i++) {
                BlockPos pick = choices.get(getRandom().nextInt(choices.size()));
                if (best == null || pick.distSqr(blockPosition()) < best.distSqr(blockPosition())) {
                    best = pick;
                }
            }
            return best;
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ANT_STEP.get(), 0.15F, 1.6F + getRandom().nextFloat() * 0.4F);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return ModSounds.ANT_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.ANT_HURT.get();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            playSound(ModSounds.SOLDIER_BITE.get(), 0.5F, 1.4F);
        }
        return hit;
    }
}
