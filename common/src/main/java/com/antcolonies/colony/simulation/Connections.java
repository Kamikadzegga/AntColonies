package com.antcolonies.colony.simulation;

import java.util.List;

import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.core.BlockPos;

/**
 * Colonies joined by a Colony Connector help each other: one with more than 60% of its food room full sends food to
 * one below 40%, up to the configured rate. This is how a Farmer Ant colony feeds a Diamond Ant colony. Each colony
 * only sends, never takes, so two colonies never move the same food both ways in a step.
 */
final class Connections {
    static void shareFood(ColonySimulator.Step step, List<BlockPos> linked, double capacity) {
        Colony colony = step.colony;
        double rate = AntConfig.get(AntConfig.CONNECTOR_FOOD_PER_MINUTE);
        if (linked.isEmpty() || rate <= 0) {
            return;
        }
        double budget = rate * step.dt;
        for (BlockPos pos : linked) {
            double food = ColonyAccess.food(colony);
            double spare = food - capacity * 0.6;
            if (spare <= 0 || budget <= 0) {
                return;
            }
            if (!step.level.isLoaded(pos) || !(step.level.getBlockEntity(pos) instanceof TerrariumBlockEntity other)) {
                continue;
            }
            Colony target = other.colony();
            double otherCapacity = target.stats().foodCapacity() > 0 ? target.stats().foodCapacity() : AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY);
            double need = otherCapacity * 0.4 - ColonyAccess.food(target);
            if (need <= 0) {
                continue;
            }
            double moved = Math.min(Math.min(spare, need), budget);
            budget -= moved;
            ColonyAccess.setFood(colony, food - moved, ColonyAccess.starvedMinutes(colony));
            ColonyAccess.setFood(target, ColonyAccess.food(target) + moved, ColonyAccess.starvedMinutes(target));
            other.setChanged();
        }
    }

    private Connections() {}
}
