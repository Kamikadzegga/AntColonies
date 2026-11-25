package com.antcolonies.companion;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.companion.item.CompanionArmorItem;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/** A companion's numbers: modifiers with fixed ids over the species' own attributes: applying them again changes nothing. */
public final class CompanionStats {
    private static final Identifier GUARD_FORM = AntColonies.id("guard_form");
    private static final Identifier LEVEL = AntColonies.id("companion_level");
    private static final Identifier PLATES = AntColonies.id("companion_armor");
    private static final Identifier FORM = AntColonies.id("companion_form");

    // --- The Guard Form: what any ant gains when it is recruited ---
    private static final double GUARD_HEALTH = 12;
    private static final double GUARD_HEALTH_PER_STRENGTH = 4;
    private static final double GUARD_DAMAGE = 0.5;
    private static final double GUARD_DAMAGE_PER_STRENGTH = 0.6;
    private static final double GUARD_ARMOR_PER_TIER = 2;
    private static final int MAX_ARMOR_TIER = 5;
    /** How much of a species' own fighting skill (its hunters' strength in a colony) shows in a Guard's bite. */
    private static final double SPECIES_SKILL_WEIGHT = 0.5;
    /** A recruited worker grows a little more than a soldier does: both end up a good size to see and to click. */
    private static final float WORKER_GROWTH = 1.15F;

    /** The Jumping Ant's leap: added to a mob's jump strength, with the fall it then takes unhurt. */
    private static final double JUMP_STRENGTH = 0.2;
    private static final double JUMP_SAFE_FALL = 6;
    private static final double JUMP_STEP = 0.5;

    /** Puts every modifier in place; health that is gained by it is filled. */
    public static void apply(Mob mob, CompanionData data) {
        float before = mob.getMaxHealth();
        // A Guard Ant from before the companions has the Guard Form in its own attributes.
        boolean guard = !(mob instanceof GuardAnt);
        AntSpecies species = ModRegistries.species(mob.registryAccess(), data.species());
        set(mob, Attributes.MAX_HEALTH, GUARD_FORM, guard ? GUARD_HEALTH + GUARD_HEALTH_PER_STRENGTH * data.strength() : 0);
        set(mob, Attributes.ATTACK_DAMAGE, GUARD_FORM, guard ? (GUARD_DAMAGE + GUARD_DAMAGE_PER_STRENGTH * data.strength()) * skill(species) : 0);
        set(mob, Attributes.ARMOR, GUARD_FORM, guard ? GUARD_ARMOR_PER_TIER * tier(species) : 0);
        // Its body grows with the ant that is drawn: what is seen is what is hit.
        if (set(mob, Attributes.SCALE, GUARD_FORM, guard && mob instanceof WildAnt ant ? size(ant.caste()) - 1 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
            mob.refreshDimensions();
        }
        int levels = Math.max(0, data.level() - 1);
        set(mob, Attributes.MAX_HEALTH, LEVEL, levels * AntConfig.get(AntConfig.COMPANION_HEALTH));
        set(mob, Attributes.ATTACK_DAMAGE, LEVEL, levels * AntConfig.get(AntConfig.COMPANION_DAMAGE));
        set(mob, Attributes.ARMOR, LEVEL, levels * AntConfig.get(AntConfig.COMPANION_ARMOR));
        CompanionArmorItem plates = data.gear().armor().getItem() instanceof CompanionArmorItem item ? item : null;
        set(mob, Attributes.ARMOR, PLATES, plates == null ? 0 : plates.defense());
        set(mob, Attributes.ARMOR_TOUGHNESS, PLATES, plates == null ? 0 : plates.toughness());
        boolean jumping = data.hasForm(CompanionForms.JUMPING);
        set(mob, Attributes.JUMP_STRENGTH, FORM, jumping ? JUMP_STRENGTH : 0);
        set(mob, Attributes.SAFE_FALL_DISTANCE, FORM, jumping ? JUMP_SAFE_FALL : 0);
        set(mob, Attributes.STEP_HEIGHT, FORM, jumping ? JUMP_STEP : 0);
        float gained = mob.getMaxHealth() - before;
        if (gained > 0) {
            mob.heal(gained);
        } else if (mob.getHealth() > mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    /** Permanent, so a saved companion's health is not cut to its species' own when it loads. */
    private static void set(Mob mob, Holder<Attribute> attribute, Identifier id, double amount) {
        set(mob, attribute, id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Puts one of its numbers in place, and says whether that changed anything. */
    private static boolean set(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            return current != null && instance.removeModifier(id);
        }
        if (current != null && current.amount() == amount) {
            return false;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
        return true;
    }

    private static int tier(@Nullable AntSpecies species) {
        return Math.clamp(species == null ? 1 : species.tier(), 1, MAX_ARMOR_TIER);
    }

    /** A species that hunts well in a colony bites harder as a Guard, one that fights poorly a little softer. */
    private static double skill(@Nullable AntSpecies species) {
        return species == null ? 1 : Math.max(0.5, 1 + (species.combat().strength() - 1) * SPECIES_SKILL_WEIGHT);
    }

    /** How much bigger than a wild ant of its caste a Guard is. */
    public static float size(Caste caste) {
        return caste == Caste.WORKER ? GuardAnt.SIZE * WORKER_GROWTH : GuardAnt.SIZE;
    }

    public static double damage(Mob mob) {
        return mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    public static double armor(Mob mob) {
        return mob.getAttribute(Attributes.ARMOR) == null ? 0 : mob.getAttributeValue(Attributes.ARMOR);
    }

    private CompanionStats() {}
}
