package com.antcolonies.colony.model;

import java.util.Locale;

/** How far a colony started from a lone Queen has got. */
public enum Founding {
    NONE,
    /** She waits for food and lays nothing. */
    FEEDING,
    /** She raises her first workers. */
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
