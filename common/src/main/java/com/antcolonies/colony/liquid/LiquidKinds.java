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
 * Every fluid Liquid Ants can make droplets of. Kinds are offered while mods are constructed; {@link #registerItems}
 * then registers a droplet for each available one and closes the list.
 */
public final class LiquidKinds {
    private static final Map<String, LiquidKind> OFFERED = new LinkedHashMap<>();
    /** The kinds that exist in this game, by name; filled when items are registered. */
    private static final Map<String, LiquidKind> AVAILABLE = new LinkedHashMap<>();
    private static final Map<Identifier, LiquidKind> BY_FLUID = new LinkedHashMap<>();
    private static boolean closed;

    public static final LiquidKind WATER = LiquidKind.of("water", "minecraft", "minecraft:water");
    public static final LiquidKind LAVA = LiquidKind.of("lava", "minecraft", "minecraft:lava");

    static {
        offer(WATER);
        offer(LAVA);
    }

    /** Offers a kind. Must happen before items are registered (in a mod's constructor). */
    public static synchronized void offer(LiquidKind kind) {
        if (closed) {
            throw new IllegalStateException("Liquid kinds are offered while mods are constructed: too late for " + kind.name());
        }
        LiquidKind previous = OFFERED.putIfAbsent(kind.name(), kind);
        if (previous != null && !previous.equals(kind)) {
            throw new IllegalStateException("Two different liquid kinds are named " + kind.name());
        }
    }

    public static synchronized Collection<LiquidKind> offered() {
        return Collections.unmodifiableCollection(new ArrayList<>(OFFERED.values()));
    }

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

    public static synchronized @Nullable LiquidKind byFluid(Fluid fluid) {
        return BY_FLUID.get(BuiltInRegistries.FLUID.getKey(fluid));
    }

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

    /** Registers a droplet item per available kind. Runs when items are registered, after every mod's fluids. */
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
