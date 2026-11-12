package com.antcolonies.colony.model;

import java.util.Locale;

/**
 * Queen-only founding: a colony started from a single Queen put into a completely empty Terrarium. It is the only way
 * this state begins (see {@code TerrariumBlockEntity#processSlots}); colonies started or moved with workers, eggs or
 * larvae never enter it, and old saves load as {@link #NONE}.
 *
 * <ol>
 *     <li>{@link #FEEDING}: the Queen waits for her food. She lays nothing until the colony's food reaches the founding
 *     requirement ({@code foundingFoodShare} of the Terrarium's own food room).</li>
 *     <li>{@link #BROOD}: she lays her first brood; the eggs hatch, the larvae grow, and once {@code foundingWorkers}
 *     adults have been raised the colony is founded and runs like any other.</li>
 * </ol>
 *
 * Any ants, eggs or larvae put in from outside end it at once (the colony is no longer one Queen alone), and so does
 * taking the Queen out.
 */
public enum Founding {
    NONE,
    FEEDING,
    BROOD;

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean active() {
        return this != NONE;
    }

    public static Founding byName(String name) {
        for (Founding founding : values()) {
            if (founding.getSerializedName().equals(name)) {
                return founding;
            }
        }
        return NONE;
    }
}
