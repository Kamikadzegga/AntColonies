package com.antcolonies.colony.simulation;

import com.antcolonies.core.config.AntConfig;

/**
 * The shared formulas. Production Rate = Base Rate x Effective Population x Genetic Efficiency x Species Bonus x
 * Upgrade Bonus x Colony Health Modifier, where Effective Population = assigned^exponent (diminishing returns).
 */
public final class ProductionMath {
    /** n ants work like n^exponent ants: twice the ants never quite doubles the output. */
    public static double effective(double assigned) {
        if (assigned <= 0) {
            return 0;
        }
        return Math.pow(assigned, AntConfig.get(AntConfig.POPULATION_EXPONENT));
    }

    /**
     * Stacked bonuses, softened: a total multiplier above 1 counts as multiplier^bonusExponent, so genetics, mutations
     * and upgrades still all help but can't multiply without end (with 0.6: x2 -> x1.5, x7 -> x3.2, x20 -> x6). Below 1
     * (penalties) it is used as it is.
     */
    public static double bonus(double multiplier) {
        return multiplier <= 1 ? multiplier : Math.pow(multiplier, AntConfig.get(AntConfig.BONUS_EXPONENT));
    }

    /**
     * Diminishing returns over a whole colony: {@code total} ants on one kind of job work like total^exponent ants, shared
     * between the sites by how many each has. The share per ant, so splitting the work over more sites gains nothing.
     */
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
