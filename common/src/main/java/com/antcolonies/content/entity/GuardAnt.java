package com.antcolonies.content.entity;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;
import com.antcolonies.content.item.GuardData;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A Guard Ant from before the companions (recruits are now ants of their own kind); it lives on as one of them. */
public class GuardAnt extends TamableAnimal implements AntLike {
    private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(GuardAnt.class, EntityDataSerializers.STRING);
    /** How much bigger than a wild soldier it is. */
    public static final float SIZE = 1.5F;

    private int strength = 1;

    public GuardAnt(EntityType<? extends GuardAnt> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth(1))
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, damage(1))
                .add(Attributes.ARMOR, 0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.SCALE, SIZE);
    }

    public static double maxHealth(int strength) {
        return 20 + 4 * strength;
    }

    public static double damage(int strength) {
        return 3 + 0.6 * strength;
    }

    public static double armor(int tier) {
        return Math.min(5, Math.max(1, tier)) * 2;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, ModRegistries.COMMON.toString());
    }

    @Override
    public Identifier species() {
        Identifier id = Identifier.tryParse(entityData.get(DATA_SPECIES));
        return id == null ? ModRegistries.COMMON : id;
    }

    @Override
    public Caste caste() {
        return Caste.SOLDIER;
    }

    @Override
    public float renderScale() {
        return SIZE;
    }

    @Override
    public boolean sitting() {
        CompanionData data = Companions.get(this);
        return data == null ? isInSittingPose() : data.command() == CompanionCommand.STAY && !walkAnimation.isMoving();
    }

    public int strength() {
        return strength;
    }

    public void setup(GuardData data) {
        entityData.set(DATA_SPECIES, data.species().toString());
        strength = Math.clamp(data.strength(), 1, 10);
        applyStats();
        setHealth(data.health() > 0 ? Math.min(data.health(), getMaxHealth()) : getMaxHealth());
    }

    private void applyStats() {
        AntSpecies species = ModRegistries.species(level().registryAccess(), species());
        set(Attributes.MAX_HEALTH, maxHealth(strength));
        set(Attributes.ATTACK_DAMAGE, damage(strength));
        set(Attributes.ARMOR, armor(species == null ? 1 : species.tier()));
    }

    private void set(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** An owned Guard Ant from before the companions becomes one, with all it had: owner, strength, orders, name and health. */
    public void becomeCompanion() {
        if (Companions.isCompanion(this) || getOwnerReference() == null) {
            return;
        }
        String name = getOwner() instanceof Player player ? player.getGameProfile().name() : "";
        float health = getHealth();
        CompanionData data = Companions.create(this, strength, getRandom()).withId(getUUID())
                .withOwner(new CompanionData.Owner(getOwnerReference().getUUID(), name))
                .withCommand(isOrderedToSit() ? CompanionCommand.STAY : CompanionCommand.FOLLOW, blockPosition());
        Companions.set(this, data);
        setHealth(health);
        setOrderedToSit(false);
        setInSittingPose(false);
        setPersistenceRequired();
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast || target == owner) {
            return false;
        }
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return false;
        }
        return !(target instanceof Player player && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(player));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            Companions.rest(this);
        }
    }

    @Override
    protected Component getTypeName() {
        CompanionData data = Companions.get(this);
        return data == null ? super.getTypeName() : Companions.guardName(data);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("species", species().toString());
        output.putInt("strength", strength);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Identifier species = Identifier.tryParse(input.getStringOr("species", ModRegistries.COMMON.toString()));
        entityData.set(DATA_SPECIES, (species == null ? ModRegistries.COMMON : species).toString());
        strength = Math.clamp(input.getIntOr("strength", 1), 1, 10);
        float health = getHealth();
        applyStats();
        setHealth(Math.min(health, getMaxHealth()));
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ANT_STEP.get(), 0.2F, 1.2F + getRandom().nextFloat() * 0.3F);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ANT_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.ANT_HURT.get();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            playSound(ModSounds.SOLDIER_BITE.get(), 0.6F, 1.1F);
        }
        return hit;
    }
}
