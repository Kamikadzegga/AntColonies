package com.antcolonies.colony.simulation;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.registry.ModDataMaps;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Food items become colony food: Food Storage slots are eaten from while there is room for their food. What an item is
 * worth comes from the {@code antcolonies:ant_food} data map, so any mod's food can be added there. Special food (for
 * mineral species) only counts towards a species that needs that kind.
 */
final class FoodIntake {
    record Result(double food, double special) {}

    static Result take(ColonySimulator.Step step, double capacity) {
        Colony colony = step.colony;
        String kind = step.species.diet().specialKind();
        double food = ColonyAccess.food(colony);
        double special = ColonyAccess.specialFood(colony);
        double addedFood = 0;
        double addedSpecial = 0;
        for (StructureBlockEntity storage : step.of(StructureKind.FOOD_STORAGE)) {
            Container items = storage;
            for (int slot = 0; slot < StructureKind.FOOD_STORAGE.slots(); slot++) {
                ItemStack stack = items.getItem(slot);
                ModDataMaps.AntFood value = ModDataMaps.food(stack);
                if (value == null) {
                    continue;
                }
                boolean specialUseful = !kind.isEmpty() && kind.equals(value.specialKind()) && value.special() > 0;
                // Eat while it all fits (or while the special part is wanted and the plain part isn't wasted badly).
                while (!stack.isEmpty() && (food + value.food() <= capacity || specialUseful && special + value.special() <= capacity / 4)) {
                    food += value.food();
                    addedFood += value.food();
                    if (specialUseful) {
                        special += value.special();
                        addedSpecial += value.special();
                    }
                    stack.shrink(1);
                }
                items.setItem(slot, stack);
            }
        }
        ColonyAccess.setFood(colony, Math.min(food, capacity), ColonyAccess.starvedMinutes(colony));
        ColonyAccess.setSpecialFood(colony, Math.min(special, capacity / 4));
        return new Result(addedFood, addedSpecial);
    }

    private FoodIntake() {}
}
