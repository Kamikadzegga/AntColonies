package com.antcolonies.colony.simulation;

import com.antcolonies.core.config.AntConfig;

public final class ProductionMath {
    /** n ants work like n^exponent ants: twice the ants never quite doubles the output. */
    public static double effective(double assigned) {
        if (assigned <= 0) {
            return 0;
        }
        return Math.pow(assigned, AntConfig.get(AntConfig.POPULATION_EXPONENT));
    }

    /** Stacked bonuses, softened: a multiplier above 1 counts as multiplier^bonusExponent; a penalty below 1 is used as it is. */
    public static double bonus(double multiplier) {
        return multiplier <= 1 ? multiplier : Math.pow(multiplier, AntConfig.get(AntConfig.BONUS_EXPONENT));
    }

    /** The work of each of {@code total} ants on one kind of job, diminishing over the whole colony: more sites gain nothing. */
    public static double perAnt(double total) {
        return total <= 0 ? 0 : effective(total) / total;
    }

    /** Health 0..1 as a work multiplier: a sick colony still works at half speed. */
    public static double healthFactor(double health) {
        return 0.5 + 0.5 * Math.clamp(health, 0, 1);
    }

    /** Work speed from Speed Upgrade tiers. */
    public static double speed(int tiers) {
        return 1 + AntConfig.get(AntConfig.SPEED_UPGRADE_BONUS) * tiers;
    }

    /** Food eaten after Food Efficiency tiers (and species efficiency upgrades): at most 75% saved. */
    public static double foodSaving(double tiers) {
        return 1 - Math.min(0.75, AntConfig.get(AntConfig.FOOD_UPGRADE_SAVING) * tiers);
    }

    /** n structures of a kind with a bonus each, diminishing: 1 + bonus * n^0.7. */
    public static double diminishing(int count, double bonus) {
        return count <= 0 ? 1 : 1 + bonus * Math.pow(count, 0.7);
    }

    /** The share of {@code amount} that follows a process with average time {@code minutes} within {@code dt} minutes. */
    public static double flow(double amount, double dt, double minutes) {
        if (amount <= 0 || minutes <= 0) {
            return amount;
        }
        return amount * (1 - Math.exp(-dt / minutes));
    }

    private ProductionMath() {}
}
