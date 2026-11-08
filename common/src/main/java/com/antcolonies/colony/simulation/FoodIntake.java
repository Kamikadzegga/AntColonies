package com.antcolonies.colony.simulation;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;

/**
 * Keeps a colony's food within its room. Food items reach a colony through the Terrarium's feed slot (by hand, or a
 * hopper or pipe into the Terrarium); what an item is worth comes from the {@code antcolonies:ant_food} data map. The
 * room shrinks when a Storage is taken out of the ring, and what no longer fits is lost.
 */
final class FoodIntake {
    record Result(double food, double special) {}

    static Result take(ColonySimulator.Step step, double capacity) {
        Colony colony = step.colony;
        ColonyAccess.setFood(colony, Math.min(ColonyAccess.food(colony), capacity), ColonyAccess.starvedMinutes(colony));
        ColonyAccess.setSpecialFood(colony, Math.min(ColonyAccess.specialFood(colony), capacity / 4));
        return new Result(0, 0);
    }

    private FoodIntake() {}
}
