package com.antcolonies.content.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
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

    /** Its rank in an anthill: an ordinary ant, one of the Queen's royal guard, or the great Queen herself. */
    private static final EntityDataAccessor<Byte> DATA_RANK = SynchedEntityData.defineId(WildAnt.class, EntityDataSerializers.BYTE);

    /** Guards a giant anthill: its soldiers attack players who come close (not ones in a full Chitin set). */
    private boolean guardian;
    /** The anthill it belongs to, named by its Queen's spot (null for ants of no anthill). */
    private @Nullable BlockPos anthill;
    /** A great Queen's chamber: a player stepping into it raises the alarm. */
    private @Nullable AABB chamber;
    /** Players its anthill is alarmed at, and the game time each alarm ends. */
    private final Map<UUID, Long> alarms = new HashMap<>();
    /** Whether any alarm is on (worked out once a tick, server side). */
    private boolean alarmed;

    /** An anthill ant's rank: royal guards are bigger and stronger soldiers, the great Queen twice any Queen's size. */
    public enum Rank {
        NORMAL, ROYAL, GREAT_QUEEN;

        static Rank byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NORMAL;
        }
    }

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
                // An alarmed anthill's workers swarm the intruder too.
                return (caste() == Caste.SOLDIER || alarmed && caste() == Caste.WORKER) && super.canUse();
            }
        });
        goalSelector.addGoal(2, new PanicGoal(this, 1.4) {
            @Override
            public boolean canUse() {
                return caste() != Caste.SOLDIER && !alarmed && super.canUse();
            }
        });
        goalSelector.addGoal(3, new ReturnToNestGoal());
        goalSelector.addGoal(3, new GuardPostGoal());
        goalSelector.addGoal(4, new WorkLandGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !royalGuard() && super.canUse(); // a Royal Guard Ant keeps its post
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 4.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // An alarmed anthill goes for the players it is alarmed at, wherever they are in it.
        targetSelector.addGoal(0, new AlarmTargetGoal());
        // Hurting any ant of a group brings its soldiers.
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(WildAnt.class));
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> guardian && caste() == Caste.SOLDIER && (!royalGuard() || atPost(target))));
    }

    // --- Royal Guard Ants: the great Queen's guard ---

    /** How near its post a Royal Guard Ant lets a player come before it attacks (blocks). */
    public static final double GUARD_REACH = 4.0;
    /** How far from its post a Royal Guard Ant follows anyone: it never leaves the Queen for long (blocks). */
    public static final double GUARD_LEASH = 10.0;

    /**
     * A Royal Guard Ant: one of the great Queen's guard (an anthill soldier of royal rank). Its job is her, not the anthill:
     * it stands at its post in her chamber or the antechamber instead of roaming, takes on only those who come up to its
     * post (or whom the anthill is alarmed at, or who hurt it) and never follows anyone farther than {@link #GUARD_LEASH}
     * from it, then goes back.
     */
    public boolean royalGuard() {
        return rank() == Rank.ROYAL && caste() == Caste.SOLDIER;
    }

    /** Its post: where it was placed (its home), or where it stands for an ant without one. */
    public BlockPos post() {
        return hasHome() ? getHomePosition() : blockPosition();
    }

    /** Whether someone has come up to this Royal Guard Ant's post. */
    public boolean atPost(net.minecraft.world.entity.Entity other) {
        return other.distanceToSqr(Vec3.atBottomCenterOf(post())) <= GUARD_REACH * GUARD_REACH;
    }

    /** Whether someone is still within the ground a Royal Guard Ant keeps (it lets anyone farther away go). */
    public boolean withinLeash(net.minecraft.world.entity.Entity other) {
        return other.distanceToSqr(Vec3.atBottomCenterOf(post())) <= GUARD_LEASH * GUARD_LEASH;
    }

    @Override
    protected Component getTypeName() {
        return royalGuard() ? Component.translatable("entity." + com.antcolonies.AntColonies.MODID + ".royal_guard_ant") : super.getTypeName();
    }

    /** A Royal Guard Ant with nothing to fight walks back to its post and stands there. */
    private class GuardPostGoal extends Goal {
        private int retry;

        GuardPostGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return royalGuard() && getTarget() == null && distanceToSqr(Vec3.atBottomCenterOf(post())) > 1.0;
        }

        @Override
        public boolean canContinueToUse() {
            return royalGuard() && getTarget() == null && distanceToSqr(Vec3.atBottomCenterOf(post())) > 0.3;
        }

        @Override
        public void start() {
            retry = 0;
        }

        @Override
        public void tick() {
            if (--retry <= 0) {
                retry = 20;
                Vec3 at = Vec3.atBottomCenterOf(post());
                getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, ModRegistries.COMMON.toString());
        builder.define(DATA_CASTE, (byte) Caste.WORKER.ordinal());
        builder.define(DATA_CARRY, 0);
        builder.define(DATA_RANK, (byte) 0);
    }

    public Rank rank() {
        return Rank.byId(entityData.get(DATA_RANK));
    }

    /** Makes it a royal guard or the great Queen (after its caste: the rank scales the caste's size and strength). */
    public void setRank(Rank rank) {
        entityData.set(DATA_RANK, (byte) rank.ordinal());
        applyCaste();
    }

    @Override
    public float renderScale() {
        return switch (rank()) {
            case GREAT_QUEEN -> 2.0F;
            case ROYAL -> 1.3F;
            case NORMAL -> 1.0F;
        };
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

    /** Soldiers are bigger and hit harder; Queens are the biggest. Royal guards and the great Queen are bigger still. */
    private void applyCaste() {
        Caste caste = caste();
        Rank rank = rank();
        double scale = caste == Caste.QUEEN ? 1.6 : caste == Caste.SOLDIER ? 1.3 : 1.0;
        double health = caste == Caste.QUEEN ? 10 : caste == Caste.SOLDIER ? 8 : 4;
        double damage = caste == Caste.SOLDIER ? 2.5 : 1.0;
        double speed = caste == Caste.QUEEN ? 0.18 : 0.28;
        double armor = 0;
        if (rank == Rank.ROYAL) {
            scale *= 1.3;
            health = 18;
            damage = 3.5;
            armor = 4;
            speed = 0.3;
        } else if (rank == Rank.GREAT_QUEEN) {
            scale *= 2.0;
            health = 40;
            speed = 0.1;
        }
        set(Attributes.SCALE, scale);
        set(Attributes.MAX_HEALTH, health);
        set(Attributes.ATTACK_DAMAGE, damage);
        set(Attributes.MOVEMENT_SPEED, speed);
        set(Attributes.ARMOR, armor);
        // An anthill's ants keep chasing an intruder through its tunnels.
        set(Attributes.FOLLOW_RANGE, guardian ? 24 : 12);
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
        return speciesFor(level.registryAccess(), level.getBiome(pos), random);
    }

    /** A species that lives in a biome, or Common Ants where none does. */
    public static Identifier speciesFor(net.minecraft.core.RegistryAccess registries, Holder<Biome> biome, RandomSource random) {
        List<Identifier> candidates = new ArrayList<>();
        registries.lookup(ModRegistries.SPECIES).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
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
        output.putString("rank", rank().name().toLowerCase(java.util.Locale.ROOT));
        if (anthill != null) {
            output.putLong("anthill", anthill.asLong());
        }
        if (chamber != null) {
            output.putIntArray("chamber", new int[] {(int) Math.floor(chamber.minX), (int) Math.floor(chamber.minY), (int) Math.floor(chamber.minZ),
                    (int) Math.ceil(chamber.maxX), (int) Math.ceil(chamber.maxY), (int) Math.ceil(chamber.maxZ)});
        }
        long now = level().getGameTime();
        var alarmList = output.childrenList("alarms");
        alarms.forEach((player, until) -> {
            if (until > now) {
                var entry = alarmList.addChild();
                entry.store("player", net.minecraft.core.UUIDUtil.CODEC, player);
                entry.putLong("until", until);
            }
        });
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
        anthill = input.getLong("anthill").map(BlockPos::of).orElse(null);
        chamber = input.getIntArray("chamber").filter(box -> box.length == 6)
                .map(box -> new AABB(box[0], box[1], box[2], box[3], box[4], box[5])).orElse(null);
        alarms.clear();
        input.childrenListOrEmpty("alarms").forEach(entry -> entry.read("player", net.minecraft.core.UUIDUtil.CODEC)
                .ifPresent(player -> alarms.put(player, entry.getLongOr("until", 0))));
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
        String rank = input.getStringOr("rank", "");
        for (Rank value : Rank.values()) {
            if (value.name().toLowerCase(java.util.Locale.ROOT).equals(rank)) {
                entityData.set(DATA_RANK, (byte) value.ordinal());
            }
        }
        if (guardian && anthill == null && hasHome()) {
            // An anthill ant from before 0.2.0: all of them were kept near the Queen's spot, which names the anthill.
            anthill = getHomePosition();
            if (caste() == Caste.QUEEN && rank.isEmpty()) {
                setRank(Rank.GREAT_QUEEN);
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

    /**
     * A player in a full Chitin set smells like the colony: no wild ant attacks them, until they raise an anthill's
     * alarm (its ants attack them then, Chitin or not).
     */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return hostileTo(target) && super.canAttack(target);
    }

    /** Whether it would fight this one (whatever the difficulty): anyone not in a full Chitin set, or whom its anthill is alarmed at. */
    public boolean hostileTo(net.minecraft.world.entity.LivingEntity target) {
        return !ignores(target) || alarmedAt(target);
    }

    /** The anthill it belongs to (its Queen's spot), or null. */
    public @Nullable BlockPos anthill() {
        return anthill;
    }

    public void setAnthill(@Nullable BlockPos anthill) {
        this.anthill = anthill == null ? null : anthill.immutable();
    }

    /** A great Queen's chamber (null for any other ant, or a Queen from before chambers were kept). */
    public @Nullable AABB chamber() {
        if (chamber == null && rank() == Rank.GREAT_QUEEN && anthill != null) {
            // A Queen from before 0.2.0: her chamber was about this big round her spot.
            return new AABB(anthill).inflate(4.5, 0, 4.5).expandTowards(0, 4, 0).move(0, -0.5, 0);
        }
        return chamber;
    }

    public void setChamber(@Nullable AABB chamber) {
        this.chamber = chamber;
    }

    /** Whether its anthill is alarmed at this one now. */
    public boolean alarmedAt(net.minecraft.world.entity.LivingEntity target) {
        Long until = target instanceof Player ? alarms.get(target.getUUID()) : null;
        return until != null && until > level().getGameTime();
    }

    /** Whether any alarm is on. */
    public boolean alarmed() {
        return alarmed;
    }

    /** Alarms it at a player until the game time {@code until} (see {@link com.antcolonies.content.worldgen.AnthillAlarm}). */
    public void alarm(Player player, long until) {
        alarms.merge(player.getUUID(), until, Math::max);
        alarmed = true;
    }

    @Override
    public boolean isWithinHome(BlockPos pos) {
        // An alarmed ant chases an intruder anywhere; otherwise it keeps to its room.
        return alarmed || super.isWithinHome(pos);
    }

    @Override
    public boolean isWithinHome(Vec3 pos) {
        return alarmed || super.isWithinHome(pos);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (anthill != null && source.getEntity() instanceof Player player && !com.antcolonies.content.worldgen.AnthillAlarm.exempt(player)) {
            // Striking an anthill's ant (its great Queen too, who can't be hurt) brings the whole anthill down on you.
            com.antcolonies.content.worldgen.AnthillAlarm.raise(level, anthill, player, com.antcolonies.content.worldgen.AnthillAlarm.Reason.ATTACK);
        }
        return super.hurtServer(level, source, damage);
    }

    /** Whether wild ants leave this one alone: a player in a full Chitin set. */
    public static boolean ignores(net.minecraft.world.entity.LivingEntity target) {
        return target instanceof Player player && com.antcolonies.content.item.ChitinGear.fullSet(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return caste() != Caste.QUEEN && !guardian && nest == null;
    }

    /**
     * Whether this is an anthill's Queen: every generated anthill has exactly one, and she is its guaranteed prize. She
     * can't be hurt (lava, falls, mobs and players alike; only the void and /kill end her) and never despawns, so the
     * only way she leaves is in a Test Tube, caught with Tweezers.
     */
    public boolean isAnthillQueen() {
        return guardian && caste() == Caste.QUEEN;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (isAnthillQueen() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPushable() {
        // Nothing shoves the anthill's Queen out of her chamber.
        return !isAnthillQueen() && super.isPushable();
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
        if (level() instanceof ServerLevel server) {
            if (!alarms.isEmpty()) {
                long now = server.getGameTime();
                if (tickCount % 40 == 0) {
                    alarms.values().removeIf(until -> until <= now);
                }
                alarmed = alarms.values().stream().anyMatch(until -> until > now);
            } else {
                alarmed = false;
            }
            // A Royal Guard Ant lets go of anyone who gets away from its post, and goes back to the Queen.
            if (royalGuard() && getTarget() != null && !withinLeash(getTarget())) {
                setTarget(null);
            }
            // The great Queen watches her chamber: whoever steps in raises the alarm.
            if (tickCount % 10 == 0 && rank() == Rank.GREAT_QUEEN && anthill != null && chamber() != null) {
                for (Player player : server.getEntitiesOfClass(Player.class, chamber(), player -> !com.antcolonies.content.worldgen.AnthillAlarm.exempt(player))) {
                    com.antcolonies.content.worldgen.AnthillAlarm.raise(server, anthill, player, com.antcolonies.content.worldgen.AnthillAlarm.Reason.QUEEN);
                }
            }
        }
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

    /**
     * Targets the nearest player its anthill is alarmed at (within 40 blocks, not in creative or spectator mode, never
     * in Peaceful), and keeps after them while the alarm lasts. Queens don't fight.
     */
    private class AlarmTargetGoal extends Goal {
        private static final double RANGE = 40;
        private @Nullable Player intruder;

        AlarmTargetGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!alarmed || caste() == Caste.QUEEN || !(level() instanceof ServerLevel server)
                    || server.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                return false;
            }
            intruder = null;
            double best = RANGE * RANGE;
            for (UUID id : alarms.keySet()) {
                Player player = server.getPlayerByUUID(id);
                if (player != null && valid(player) && distanceToSqr(player) < best) {
                    best = distanceToSqr(player);
                    intruder = player;
                }
            }
            return intruder != null;
        }

        private boolean valid(Player player) {
            // Alarmed, a Royal Guard Ant still only fights by the Queen.
            return player.isAlive() && !player.isCreative() && !player.isSpectator() && alarmedAt(player) && (!royalGuard() || withinLeash(player));
        }

        @Override
        public boolean canContinueToUse() {
            return intruder != null && valid(intruder) && intruder.level() == level() && distanceToSqr(intruder) < RANGE * RANGE;
        }

        @Override
        public void start() {
            setTarget(intruder);
        }

        @Override
        public void stop() {
            if (getTarget() == intruder) {
                setTarget(null);
            }
            intruder = null;
        }
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
