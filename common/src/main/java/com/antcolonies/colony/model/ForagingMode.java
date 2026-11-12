package com.antcolonies.colony.model;

import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.SpeciesOutput;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What an ordinary Foraging Site's workers look for. The species decides what can be found at all (its outputs); the
 * mode only changes how often each kind is picked, never the biome or anything outside the colony:
 * <ul>
 *     <li>{@link #BALANCED}: every output at its own weight, as always.</li>
 *     <li>{@link #MINERAL}: finds in the item tag {@code antcolonies:foraging/mineral} (ores, raw metal, gems, dusts,
 *     stone...) are picked {@code focusedForagingBoost} times as often, the rest {@code focusedForagingPenalty} as often.</li>
 *     <li>{@link #ORGANIC}: the other way round (seeds, plants, mushrooms, bones, shells...).</li>
 * </ul>
 * A species with no finds of the kind looked for gathers as if Balanced (and the site says so).
 */
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

    /** Whether an output is of the kind this mode looks for (Balanced looks for everything). */
    public boolean wants(SpeciesOutput output) {
        return switch (this) {
            case BALANCED -> true;
            case MINERAL -> mineral(output);
            case ORGANIC -> !mineral(output);
        };
    }

    /** How much more (or less) often this mode picks an output. */
    public double weight(SpeciesOutput output) {
        if (this == BALANCED) {
            return 1;
        }
        return wants(output) ? AntConfig.get(AntConfig.FOCUS_BOOST) : AntConfig.get(AntConfig.FOCUS_PENALTY);
    }
}
