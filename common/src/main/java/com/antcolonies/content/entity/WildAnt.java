package com.antcolonies.content.entity;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

    /** Guards a giant anthill: its soldiers attack players who come close (not ones in a full Chitin set). */
    private boolean guardian;

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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setSpecies(Identifier.tryParse(input.getStringOr("species", ModRegistries.COMMON.toString())) instanceof Identifier id ? id : ModRegistries.COMMON);
        guardian = input.getBooleanOr("guardian", false);
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
        return caste() != Caste.QUEEN && !guardian;
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
