package com.antcolonies.colony.liquid;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

/**
 * One fluid Liquid Ants can make droplets of; it is only offered here, and real when {@link #available()}.
 * Several mods may add the same liquid (honey, crude oil): the kind then lists each of them as a source and uses the
 * first that is in the game.
 *
 * @param sources the mods that add this liquid and their fluids, the preferred one first
 */
public record LiquidKind(String name, List<Source> sources) {
    /**
     * @param mod   the id of the mod that adds the fluid ("minecraft" for water and lava)
     * @param fluid the still fluid's id in the fluid registry
     */
    public record Source(String mod, Identifier fluid) {
        public boolean modLoaded() {
            return mod.equals("minecraft") || ModList.get() != null && ModList.get().isLoaded(mod);
        }

        public boolean available() {
            return modLoaded() && usable(fluid);
        }
    }

    public LiquidKind {
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Not a kind name: " + name);
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No fluid for the kind " + name);
        }
        sources = List.copyOf(sources);
    }

    public static LiquidKind of(String name, String mod, String fluid) {
        return new LiquidKind(name, List.of(new Source(mod, Identifier.parse(fluid))));
    }

    /** This kind with one more mod's fluid to fall back on. */
    public LiquidKind or(String mod, String fluid) {
        List<Source> all = new ArrayList<>(sources);
        all.add(new Source(mod, Identifier.parse(fluid)));
        return new LiquidKind(name, all);
    }

    /** The source in use: the first whose fluid is in the game, else the first listed. */
    public Source source() {
        for (Source source : sources) {
            if (source.available()) {
                return source;
            }
        }
        return sources.getFirst();
    }

    public String mod() {
        return source().mod();
    }

    public Identifier fluid() {
        return source().fluid();
    }

    /** The droplet item's id (registered only while the kind is available). */
    public Identifier dropletId() {
        return AntColonies.id(name + "_droplet");
    }

    public Identifier speciesId() {
        return AntColonies.id(name);
    }

    public boolean modLoaded() {
        return sources.stream().anyMatch(Source::modLoaded);
    }

    public boolean available() {
        return sources.stream().anyMatch(Source::available);
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
