package com.antcolonies.content.entity;

import com.antcolonies.content.item.GuardData;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Guard Ant: a soldier a colony gave up to follow and defend its owner. It fights whatever hurts its owner or what
 * its owner attacks, and hostile mobs that come close (not creepers). Its strength comes from the colony's genetics:
 * health and bite from the Queen's Soldier Strength level, armor from the species' tier. Right-click to make it stay
 * or follow, feed it ant food to heal it, sneak-right-click with an empty hand to put it back in its tube.
 */
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

    /** Health at a Soldier Strength level: 24 at level 1, 60 at level 10. */
    public static double maxHealth(int strength) {
        return 20 + 4 * strength;
    }

    /** Bite damage at a Soldier Strength level: 3.6 at level 1, 9 at level 10. */
    public static double damage(int strength) {
        return 3 + 0.6 * strength;
    }

    /** Armor from the species' tier: 2 per tier, at most 10. */
    public static double armor(int tier) {
        return Math.min(5, Math.max(1, tier)) * 2;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        // A guard: hostile mobs near it are fair game, except the ones that explode.
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (target, level) -> isTame() && !isOrderedToSit() && target instanceof Enemy && !(target instanceof Creeper)));
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
        return isInSittingPose();
    }

    public int strength() {
        return strength;
    }

    /** Sets what it is and how strong (from its tube), and its stats with them. */
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

    public GuardData toData() {
        return new GuardData(species(), strength, getHealth() >= getMaxHealth() ? -1 : getHealth());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isOwnedBy(player)) {
            return super.mobInteract(player, hand);
        }
        ModDataMaps.AntFood food = ModDataMaps.food(stack);
        if (food != null && getHealth() < getMaxHealth()) {
            if (!level().isClientSide()) {
                heal((float) Math.max(2, food.food() / 5));
                stack.consume(1, player);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.4, getZ(), 2, 0.2, 0.1, 0.2, 0);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide()) {
            if (player.isShiftKeyDown()) {
                // Back into its tube, health and all.
                ItemStack tube = new ItemStack(ModItems.GUARD_ANT.get());
                tube.set(ModDataComponents.GUARD.get(), toData());
                if (hasCustomName()) {
                    tube.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
                }
                player.getInventory().placeItemBackInInventory(tube, net.minecraft.util.Prediction.SERVER_ONLY);
                discard();
            } else {
                setOrderedToSit(!isOrderedToSit());
                jumping = false;
                getNavigation().stop();
                setTarget(null);
                player.sendOverlayMessage(Component.translatable(isOrderedToSit() ? "message.antcolonies.guard.stay" : "message.antcolonies.guard.follow"));
            }
        }
        return InteractionResult.SUCCESS;
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
