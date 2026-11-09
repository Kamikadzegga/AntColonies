package com.antcolonies.colony.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What a colony upgrade does at a tier and where it goes, in numbers, for its tooltip and the JEI/EMI page. The numbers
 * follow the same formulas and config values as the simulation (several upgrades in one block add up their tiers).
 * They read like vanilla's attribute lines: a heading per place ("In Nest Chambers:"), then its values, one a line.
 */
public final class UpgradeEffects {
    private UpgradeEffects() {}

    /** What the upgrade does in one place: the place, and one short value per line ("+50% work speed"). */
    public record Effect(String place, List<Component> values) {}

    /** The blocks that take an upgrade of this type, as items to show. */
    public static List<ItemStack> placements(UpgradeType type) {
        List<ItemStack> list = new ArrayList<>();
        if (TerrariumAccepts.types().contains(type)) {
            list.add(new ItemStack(ModItems.TERRARIUM.get()));
        }
        for (StructureKind kind : StructureKind.values()) {
            if (kind.allows(type)) {
                list.add(new ItemStack(ModBlocks.STRUCTURES.get(kind).get()));
            }
        }
        if (type == UpgradeType.SPEED || type == UpgradeType.GENETIC) {
            list.add(new ItemStack(ModItems.GENETIC_LAB.get()));
        }
        return list;
    }

    /** The upgrade's effects at this tier, a place at a time. */
    public static List<Effect> effects(UpgradeType type, int tier) {
        List<Effect> effects = new ArrayList<>();
        switch (type) {
            case SPEED -> {
                int speed = percent(AntConfig.get(AntConfig.SPEED_UPGRADE_BONUS) * tier);
                effects.add(effect("sites", value("speed.sites", speed)));
                effects.add(effect("nest", value("speed.larvae", speed), value("speed.eggs", 10 * tier)));
                effects.add(effect("queen", value("speed.queen", 25 * tier)));
                effects.add(effect("lab", value("speed.lab", 1 + tier)));
            }
            case FOOD_EFFICIENCY -> effects.add(effect("terrarium_or_site",
                    value("food_efficiency", Math.min(75, percent(AntConfig.get(AntConfig.FOOD_UPGRADE_SAVING) * tier)))));
            case SIMULATION -> {
                int minutes = AntConfig.simulationUpgradeMinutes(Math.min(UpgradeType.MAX_TIER, tier));
                effects.add(effect("terrarium", value("simulation", minutes >= 60 && minutes % 60 == 0 ? (minutes / 60) + " h" : minutes + " min")));
            }
            case STORAGE -> {
                int slots = StructureBlockEntity.storageSlots(tier);
                effects.add(effect("storage", value("storage.slots", slots, slots - StructureBlockEntity.storageSlots(0)),
                        value("storage.stacks", 1 + tier), value("storage.food", thousands((long) AntConfig.get(AntConfig.STORAGE_FOOD_CAPACITY) * tier))));
            }
            case BREEDING -> {
                effects.add(effect("terrarium_and_nest", value("breeding.eggs", 25 * tier)));
                effects.add(effect("queen", value("breeding.queen", 25 * tier)));
            }
            case GENETIC -> {
                effects.add(effect("lab", value("genetic.lab", Math.min(50, 10 * tier))));
                effects.add(effect("queen", value("genetic.queen", 25 * tier)));
            }
            case HUNTING -> effects.add(effect("hunting", value("hunting", 25 * tier)));
            case AUTOMATION -> effects.add(effect("storage", value("automation", 16 * tier)));
            case POPULATION -> {
                effects.add(effect("terrarium", value("population.terrarium", thousands((long) AntConfig.get(AntConfig.TERRARIUM_CAPACITY) * tier))));
                effects.add(effect("nest", value("population.nest", 50 * tier)));
                effects.add(effect("sites", value("population.sites", AntConfig.get(AntConfig.SITE_CAPACITY_PER_UPGRADE) * tier)));
            }
            case EVOLUTION -> effects.add(effect("terrarium", value("evolution", 25 * tier)));
        }
        return effects;
    }

    /** The effects as tooltip lines: each place in gray, its values under it in blue. */
    public static List<Component> lines(UpgradeType type, int tier) {
        List<Component> lines = new ArrayList<>();
        for (Effect effect : effects(type, tier)) {
            lines.add(Component.translatable("tooltip.antcolonies.upgrade_place." + effect.place()).withStyle(ChatFormatting.GRAY));
            for (Component value : effect.values()) {
                lines.add(Component.literal(" ").append(value).withStyle(ChatFormatting.BLUE));
            }
        }
        return lines;
    }

    private static Effect effect(String place, Component... values) {
        return new Effect(place, List.of(values));
    }

    private static Component value(String key, Object... args) {
        return Component.translatable("tooltip.antcolonies.upgrade_effect." + key, args);
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    private static String thousands(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** The upgrade types a Terrarium takes (kept beside TerrariumBlockEntity.acceptsUpgrade). */
    public static final class TerrariumAccepts {
        private static final java.util.Set<UpgradeType> TYPES = java.util.EnumSet.of(UpgradeType.POPULATION, UpgradeType.SIMULATION,
                UpgradeType.EVOLUTION, UpgradeType.FOOD_EFFICIENCY, UpgradeType.BREEDING);

        public static java.util.Set<UpgradeType> types() {
            return TYPES;
        }

        private TerrariumAccepts() {}
    }
}
