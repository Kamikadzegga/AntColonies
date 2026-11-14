package com.antcolonies.colony.liquid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.AntColonies;
import com.antcolonies.content.item.DropletItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;

/**
 * Every fluid Liquid Ants can make droplets of. The two of the base game (water and lava) are always there; every other
 * one is offered by an integration ({@code com.antcolonies.compat.liquid}) and only exists while its mod is installed
 * and its fluid registered (see {@link LiquidKind#available()}).
 *
 * <p>Adding a fluid is one {@link #offer} call (an integration's, or another mod's from its constructor) plus the data
 * and art for it: nothing in the colony simulation, the items or the Centrifuge has to change.
 *
 * <p>Order of events: integrations offer their kinds while mods are constructed; the fluid registry is filled (it comes
 * before items in the registration order); then {@link #registerItems} registers a droplet item for each kind that is
 * available, and the list is closed.
 */
public final class LiquidKinds {
    private static final Map<String, LiquidKind> OFFERED = new LinkedHashMap<>();
    /** The kinds that exist in this game, by name; filled when items are registered. */
    private static final Map<String, LiquidKind> AVAILABLE = new LinkedHashMap<>();
    private static final Map<Identifier, LiquidKind> BY_FLUID = new LinkedHashMap<>();
    private static boolean closed;

    /** The base game's two. */
    public static final LiquidKind WATER = LiquidKind.of("water", "minecraft", "minecraft:water");
    public static final LiquidKind LAVA = LiquidKind.of("lava", "minecraft", "minecraft:lava");

    static {
        offer(WATER);
        offer(LAVA);
    }

    /**
     * Offers a kind. Must happen before items are registered (in a mod's constructor); the kind becomes real only if its
     * mod and fluid are there.
     */
    public static synchronized void offer(LiquidKind kind) {
        if (closed) {
            throw new IllegalStateException("Liquid kinds are offered while mods are constructed: too late for " + kind.name());
        }
        LiquidKind previous = OFFERED.putIfAbsent(kind.name(), kind);
        if (previous != null && !previous.equals(kind)) {
            throw new IllegalStateException("Two different liquid kinds are named " + kind.name());
        }
    }

    /** Every kind any integration offers, whether or not it exists in this game. */
    public static synchronized Collection<LiquidKind> offered() {
        return Collections.unmodifiableCollection(new ArrayList<>(OFFERED.values()));
    }

    /** The kinds that exist in this game (after items are registered). */
    public static synchronized Collection<LiquidKind> available() {
        return Collections.unmodifiableCollection(new ArrayList<>(AVAILABLE.values()));
    }

    public static synchronized @Nullable LiquidKind byName(String name) {
        return OFFERED.get(name);
    }

    /** The kind (offered, available or not) whose species this is, e.g. to say which mod a missing species needs. */
    public static synchronized @Nullable LiquidKind bySpecies(@Nullable Identifier species) {
        if (species == null || !species.getNamespace().equals(AntColonies.MODID)) {
            return null;
        }
        return OFFERED.get(species.getPath());
    }

    /** The available kind of a fluid, if Liquid Ants make it. */
    public static synchronized @Nullable LiquidKind byFluid(Fluid fluid) {
        return BY_FLUID.get(BuiltInRegistries.FLUID.getKey(fluid));
    }

    /** The droplet item of an available kind; null while the kind doesn't exist in this game. */
    public static @Nullable Item droplet(LiquidKind kind) {
        return BuiltInRegistries.ITEM.getOptional(kind.dropletId()).orElse(null);
    }

    /** Every droplet item that exists in this game, in the order their kinds were offered. */
    public static List<Item> droplets() {
        List<Item> items = new ArrayList<>();
        for (LiquidKind kind : available()) {
            Item item = droplet(kind);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Registers the droplet items: one per offered kind whose mod is installed and whose fluid is registered. Runs when
     * items are registered, after every mod's fluids.
     */
    public static void registerItems(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        synchronized (LiquidKinds.class) {
            closed = true;
            for (LiquidKind kind : OFFERED.values()) {
                if (!kind.available()) {
                    if (kind.modLoaded()) {
                        AntColonies.LOGGER.info("{} is installed but has no fluid {}: no {} ants", kind.mod(), kind.fluid(), kind.name());
                    }
                    continue;
                }
                AVAILABLE.put(kind.name(), kind);
                BY_FLUID.put(kind.fluid(), kind);
            }
            AntColonies.LOGGER.info("Liquid Ants of this game: {}", String.join(", ", AVAILABLE.keySet()));
        }
        event.register(Registries.ITEM, helper -> {
            for (LiquidKind kind : available()) {
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, kind.dropletId());
                helper.register(kind.dropletId(), new DropletItem(kind, new Item.Properties().setId(key)));
            }
        });
    }

    private LiquidKinds() {}
}
