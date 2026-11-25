package com.antcolonies.companion;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** A companion's character: only how often it does which little thing while idle. */
public enum Personality implements StringRepresentable {
    CURIOUS("curious", 4, 1, 1, 1),
    TIDY("tidy", 1, 4, 1, 1),
    CALM("calm", 1, 1, 4, 1),
    DEVOTED("devoted", 1, 1, 1, 4),
    PLAYFUL("playful", 3, 2, 0, 2);

    public static final Codec<Personality> CODEC = StringRepresentable.fromEnum(Personality::values);

    /** The little things a companion does while nothing happens. */
    public enum Idle {
        LOOK, GROOM, REST, OWNER
    }

    private final String name;
    /** How often it does each of {@link Idle}, against each other. */
    private final int[] weights;

    Personality(String name, int look, int groom, int rest, int owner) {
        this.name = name;
        this.weights = new int[] {look, groom, rest, owner};
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The idle act for a roll from 0 to 1. */
    public Idle idle(float roll) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        float left = roll * total;
        for (Idle idle : Idle.values()) {
            left -= weights[idle.ordinal()];
            if (left < 0) {
                return idle;
            }
        }
        return Idle.LOOK;
    }

    public Component label() {
        return Component.translatable("companion.antcolonies.personality." + name);
    }
}
