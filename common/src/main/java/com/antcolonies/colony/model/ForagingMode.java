package com.antcolonies.colony.model;

import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.SpeciesOutput;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What an ordinary Foraging Site's workers look for: how often each of the species' outputs is picked, never what can be found. */
public enum ForagingMode {
    BALANCED,
    MINERAL,
    ORGANIC;

    /** Finds that count as mineral; everything else is organic. */
    public static final TagKey<Item> MINERAL_TAG = TagKey.create(Registries.ITEM, AntColonies.id("foraging/mineral"));

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.antcolonies.mode." + getSerializedName();
    }

    public ForagingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static ForagingMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : BALANCED;
    }

    public static boolean mineral(SpeciesOutput output) {
        if (output.item().isPresent()) {
            return output.item().get().is(MINERAL_TAG);
        }
        ItemStack stack = output.create(1);
        return !stack.isEmpty() && stack.is(MINERAL_TAG);
    }

    public boolean wants(SpeciesOutput output) {
        return switch (this) {
            case BALANCED -> true;
            case MINERAL -> mineral(output);
            case ORGANIC -> !mineral(output);
        };
    }

    public double weight(SpeciesOutput output) {
        if (this == BALANCED) {
            return 1;
        }
        return wants(output) ? AntConfig.get(AntConfig.FOCUS_BOOST) : AntConfig.get(AntConfig.FOCUS_PENALTY);
    }
}
