package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;

/**
 * Problems and notes a colony or one of its structures can show. Kept as bit flags, so a status is one int in saves
 * and update packets.
 */
public enum ColonyStatus {
    NO_QUEEN(true),
    NO_FOOD(true),
    NO_WORKERS(true),
    NO_SOLDIERS(true),
    MISSING_CONNECTION(true),
    STORAGE_FULL(true),
    INCOMPATIBLE_SPECIES(true),
    INSUFFICIENT_MATERIALS(true),
    TWO_COLONIES(true),
    UNKNOWN_SPECIES(true),
    NO_STORAGE(true),
    NO_SPECIAL_FOOD(true),
    CLIMATE(false),
    LOW_HEALTH(false),
    NO_NEST_CHAMBER(false),
    POPULATION_FULL(false),
    STARVING(true),
    WORKING(false),
    /** A redstone signal has paused this working chamber: it takes no ants. */
    PAUSED(false),
    /** The Queen is fed an evolution material, but this evolution happens only in another dimension (the Nether or the End). */
    WRONG_DIMENSION(true),
    /** Queen-only founding: the Queen waits for her food bar, or raises her first brood. */
    FOUNDING(false),
    /** Food is running low: the colony works, lays and grows more slowly (see lowFoodMinutes). */
    LOW_FOOD(false),
    /** A focused Foraging Site's species has no finds of the kind it looks for: it works as if Balanced. */
    NOTHING_TO_FOCUS(false),
    /** The job site is turned off (its screen or the Terrarium's Jobs page): it takes no ants. */
    DISABLED(false);

    private final boolean problem;

    ColonyStatus(boolean problem) {
        this.problem = problem;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int flags) {
        return (flags & bit()) != 0;
    }

    public boolean problem() {
        return problem;
    }

    public ChatFormatting color() {
        return this == WORKING ? ChatFormatting.GREEN : problem ? ChatFormatting.RED : ChatFormatting.GOLD;
    }

    public String translationKey() {
        return "status.antcolonies." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static List<ColonyStatus> of(int flags) {
        List<ColonyStatus> list = new ArrayList<>();
        for (ColonyStatus status : values()) {
            if (status.in(flags)) {
                list.add(status);
            }
        }
        return list;
    }
}
