package com.antcolonies.colony.liquid;

import com.antcolonies.AntColonies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

/**
 * One fluid Liquid Ants can make droplets of; it is only offered here, and real when {@link #available()}.
 *
 * @param mod   the id of the mod that adds the fluid ("minecraft" for water and lava)
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

    /** Whether this kind exists in this game: its mod is installed and its fluid is registered as a still fluid. */
    public boolean available() {
        return modLoaded() && usable(fluid);
    }

    /**
     * Whether an id names a registered, non-empty, still fluid. The fluid's type can't be asked yet when this runs:
     * fluid types are registered after items.
     */
    public static boolean usable(Identifier id) {
        if (!BuiltInRegistries.FLUID.containsKey(id)) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        return fluid != null && fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }
}
