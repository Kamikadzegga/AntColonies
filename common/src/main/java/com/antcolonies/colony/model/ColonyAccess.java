package com.antcolonies.colony.model;

import java.util.List;

/**
 * The simulation's way into a colony's numbers. Kept apart from {@link Colony}'s public methods, which are what items,
 * GUIs and commands may change.
 */
public final class ColonyAccess {
    public static double workers(Colony colony) {
        return colony.workers;
    }

    public static double eggs(Colony colony) {
        return colony.eggs;
    }

    public static double larvae(Colony colony) {
        return colony.larvae;
    }

    public static double food(Colony colony) {
        return colony.food;
    }

    public static double specialFood(Colony colony) {
        return colony.specialFood;
    }

    public static double health(Colony colony) {
        return colony.health;
    }

    public static double starvedMinutes(Colony colony) {
        return colony.starvedMinutes;
    }

    public static int stage(Colony colony) {
        return colony.stage;
    }

    public static long lastSimTime(Colony colony) {
        return colony.lastSimTime;
    }

    public static void setLastSimTime(Colony colony, long time) {
        colony.lastSimTime = time;
    }

    public static void setHealth(Colony colony, double health) {
        colony.health = Math.clamp(health, 0, 1);
    }

    public static void setFood(Colony colony, double food, double starvedMinutes) {
        colony.food = Math.max(0, food);
        colony.starvedMinutes = Math.max(0, starvedMinutes);
    }

    public static void setSpecialFood(Colony colony, double special) {
        colony.specialFood = Math.max(0, special);
    }

    public static void setPopulation(Colony colony, double eggs, double larvae, double workers) {
        colony.eggs = Math.max(0, eggs);
        colony.larvae = Math.max(0, larvae);
        colony.workers = Math.max(0, workers);
    }

    /**
     * New adults of the current species join: the share of adults already of it rises; once (almost) every adult is,
     * the former species is gone.
     */
    public static void addAdults(Colony colony, double workers) {
        double before = colony.workers;
        double added = workers;
        colony.workers += workers;
        if (colony.formerSpecies != null && added > 0) {
            double after = before + added;
            colony.conversion = after <= 0 ? 1 : Math.min(1, (colony.conversion * before + added) / after);
            if (colony.conversion > 0.995) {
                colony.conversion = 1;
                colony.formerSpecies = null;
            }
        }
    }

    /** A hunter lost on a hunt. */
    public static void killWorker(Colony colony) {
        colony.workers = Math.max(0, colony.workers - 1);
    }

    public static void countFound(Colony colony, int count) {
        colony.totalFound += count;
    }

    public static void countLaid(Colony colony, double count) {
        colony.totalLaid += (long) count;
    }

    public static void killQueen(Colony colony) {
        colony.queen = null;
    }

    public static void finish(Colony colony, int stage, int status, Colony.Stats stats, Colony.Activity activity,
                              List<Colony.Assignment> assignments, int maintenance) {
        colony.stage = stage;
        colony.status = status;
        colony.stats = stats;
        colony.activity = activity;
        colony.assignments = List.copyOf(assignments);
        colony.maintenanceWorkers = maintenance;
    }

    private ColonyAccess() {}
}
