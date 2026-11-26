package com.antcolonies.companion;

import java.util.Map;
import java.util.WeakHashMap;

import com.antcolonies.AntColonies;
import com.antcolonies.core.advancement.ModTriggers;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModParticles;
import com.antcolonies.core.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.Nullable;

/** A companion's experience: the whole of a kill goes to the one ant that struck the last blow, and to nobody else. */
public final class CompanionXp {
    /** Damage that works on after the blow (wither, fire): it counts for the companion that dealt the last one shortly before. */
    public static final TagKey<DamageType> LASTING = TagKey.create(Registries.DAMAGE_TYPE, AntColonies.id("companion_lasting_damage"));
    /** Mobs that give no experience. */
    public static final TagKey<EntityType<?>> NO_XP = TagKey.create(Registries.ENTITY_TYPE, AntColonies.id("no_companion_xp"));
    /** How long after a companion's blow lasting damage still counts as its own, in ticks. */
    private static final int LASTING_TICKS = 100;

    /** Who gave a mob its last harmful effect. */
    private static final Map<LivingEntity, Mob> EFFECT_FROM = new WeakHashMap<>();

    static void clear() {
        EFFECT_FROM.clear();
    }

    static void onEffect(MobEffectEvent.Added event) {
        if (event.getEffectSource() instanceof Mob mob && Companions.isCompanion(mob)
                && !event.getEffectInstance().getEffect().value().isBeneficial()) {
            EFFECT_FROM.put(event.getEntity(), mob);
        }
    }

    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        Mob killer = killer(event.getEntity(), event.getSource());
        EFFECT_FROM.remove(event.getEntity());
        if (killer != null && killer.isAlive()) {
            int worth = worth(event.getEntity());
            if (worth > 0) {
                award(killer, worth);
            }
        }
    }

    /** The companion a death is owed to, or null: the one whose blow, shot or lasting effect it was. */
    public static @Nullable Mob killer(LivingEntity dead, DamageSource source) {
        Entity causing = source.getEntity();
        if (causing != null) {
            return causing instanceof Mob mob && Companions.isCompanion(mob) ? mob : null;
        }
        if (!source.is(LASTING)) {
            return null;
        }
        Mob effect = EFFECT_FROM.get(dead);
        if (effect != null && !effect.isRemoved()) {
            return effect;
        }
        return dead.getLastHurtByMob() instanceof Mob mob && Companions.isCompanion(mob)
                && dead.tickCount - dead.getLastHurtByMobTimestamp() <= LASTING_TICKS ? mob : null;
    }

    /** What a kill is worth: more for a mob that is tough, armored or hits hard. */
    public static int worth(LivingEntity dead) {
        if (!(dead instanceof Mob mob) || mob.getType().getCategory() == MobCategory.MISC || mob.isBaby() || Companions.isCompanion(mob)
                || mob instanceof OwnableEntity pet && pet.getOwnerReference() != null
                || mob.getType().builtInRegistryHolder().is(NO_XP)) {
            return 0;
        }
        double worth = mob.getMaxHealth() / 4;
        if (mob.getAttribute(Attributes.ARMOR) != null) {
            worth += mob.getAttributeValue(Attributes.ARMOR) / 2;
        }
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            worth += mob.getAttributeValue(Attributes.ATTACK_DAMAGE) / 2;
        }
        return (int) Math.round(Math.max(1, worth) * AntConfig.get(AntConfig.COMPANION_XP));
    }

    /** Experience from one level to the next, capped at what an int holds: steep settings would wrap around. */
    public static int needed(int level) {
        long needed = Math.round(AntConfig.get(AntConfig.COMPANION_XP_BASE)
                * Math.pow(level, AntConfig.get(AntConfig.COMPANION_XP_GROWTH)));
        return (int) Math.min(Integer.MAX_VALUE, needed);
    }

    public static void award(Mob mob, int xp) {
        CompanionData data = Companions.get(mob);
        if (data == null || xp <= 0) {
            return;
        }
        int max = AntConfig.get(AntConfig.COMPANION_MAX_LEVEL);
        int level = data.level();
        int left = data.xp() + xp;
        while (level < max && left >= needed(level)) {
            left -= needed(level);
            level++;
        }
        if (level >= max) {
            left = 0;
        }
        if (level == data.level() && left == data.xp()) {
            return;
        }
        Companions.set(mob, data.withProgress(Math.max(level, data.level()), left));
        if (level > data.level()) {
            celebrate(mob, data, level);
        }
    }

    private static void celebrate(Mob mob, CompanionData data, int level) {
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(ColorParticleOption.create(ModParticles.SPARKLE.get(), 0xFFFFD75A), mob.getX(),
                    mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 24, 0.35, 0.3, 0.35, 0.05);
            server.playSound(null, mob.blockPosition(), ModSounds.EVOLUTION.get(), SoundSource.NEUTRAL, 0.7F, 1.2F);
        }
        ServerPlayer owner = Companions.owner(mob, data);
        if (owner != null) {
            owner.sendOverlayMessage(Component.translatable("message.antcolonies.companion.level_up", mob.getDisplayName(), level)
                    .withStyle(ChatFormatting.GOLD));
            // Every mark it has passed, not only the last: one reached while its owner was away still counts.
            ModTriggers.COMPANION.get().trigger(owner, "level");
            if (level >= 10) {
                ModTriggers.COMPANION.get().trigger(owner, "level_10");
            }
            if (level >= 30) {
                ModTriggers.COMPANION.get().trigger(owner, "level_30");
            }
        }
    }

    private CompanionXp() {}
}
