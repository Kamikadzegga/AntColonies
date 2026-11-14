package com.antcolonies.colony.liquid;

import com.antcolonies.AntColonies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

/**
 * One fluid Liquid Ants can make droplets of: the row that ties a fluid to its droplet item and its ant species.
 *
 * <p>A kind is only <em>offered</em> here. It becomes real ({@link #available()}) when its mod is installed and its fluid
 * is in the fluid registry as a still fluid; only then is its droplet item registered and its species, recipe
 * and evolution loaded (their data files carry the same two checks as "neoforge:conditions"). A mod that is installed
 * but doesn't have the fluid in this version gives nothing: no item, no species, no error.
 *
 * @param name  the kind's name: the droplet is {@code antcolonies:<name>_droplet}, the species {@code antcolonies:<name>}
 * @param mod   the id of the mod that adds the fluid ("minecraft" for water and lava, which are always there)
 * @param fluid the still fluid's id in the fluid registry
 */
public record LiquidKind(String name, String mod, Identifier fluid) {
    public LiquidKind {
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Not a kind name: " + name);
        }
    }

    public static LiquidKind of(String name, String mod, String fluid) {
        return new LiquidKind(name, mod, Identifier.parse(fluid));
    }

    /** The droplet item's id (registered only while the kind is available). */
    public Identifier dropletId() {
        return AntColonies.id(name + "_droplet");
    }

    /** The id of the species that makes it. */
    public Identifier speciesId() {
        return AntColonies.id(name);
    }

    /** Whether the fluid's mod is installed. */
    public boolean modLoaded() {
        return mod.equals("minecraft") || ModList.get() != null && ModList.get().isLoaded(mod);
    }

    /**
     * Whether this kind exists in this game: its mod is installed, and its fluid is registered as a real, still fluid
     * with a fluid type of its own (so standard tanks and pipes can hold it). Asked after the fluid registry is filled,
     * which is before items are registered.
     */
    public boolean available() {
        return modLoaded() && usable(fluid);
    }

    /**
     * Whether an id names a fluid standard fluid handlers can hold: an entry of the fluid registry that is not the empty
     * fluid and is the still form (the flowing form is never what a tank holds). The fluid's type can't be asked yet when
     * this runs: fluid types are registered after items.
     */
    public static boolean usable(Identifier id) {
        if (!BuiltInRegistries.FLUID.containsKey(id)) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        return fluid != null && fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }
}
