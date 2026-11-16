package com.antcolonies.colony.simulation;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModDataMaps;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A colony's food: kept within its room, and topped up from its own stores. */
final class FoodIntake {
    /** Food the colony ate from its stores this step, and of it the special food. */
    record Result(double food, double special) {}

    /** Items a colony never eats from its stores. */
    static final TagKey<Item> KEPT_IN_STORAGE = TagKey.create(Registries.ITEM, AntColonies.id("kept_in_storage"));

    static Result take(ColonySimulator.Step step, double capacity) {
        Colony colony = step.colony;
        double specialRoom = capacity / 4;
        // Food over the room (a Storage in an unloaded chunk shrank it) is kept: nothing more is eaten until it is back under.
        double food = ColonyAccess.food(colony);
        double special = ColonyAccess.specialFood(colony);
        double eaten = 0;
        double specialEaten = 0;
        if (AntConfig.get(AntConfig.STORAGE_FEEDING)) {
            String diet = step.species.diet().specialKind();
            List<StructureBlockEntity> stores = new ArrayList<>(step.of(StructureKind.STORAGE));
            for (StructureKind kind : StructureKind.SITE_STORES) {
                stores.addAll(step.of(kind));
            }
            for (StructureBlockEntity store : stores) {
                boolean changed = false;
                for (int slot = 0; slot < store.unlockedSlots(); slot++) {
                    ItemStack stack = store.getItem(slot);
                    if (stack.isEmpty() || stack.is(KEPT_IN_STORAGE)) {
                        continue;
                    }
                    ModDataMaps.AntFood value = ModDataMaps.food(stack);
                    if (value == null) {
                        continue;
                    }
                    boolean isSpecial = !value.specialKind().isEmpty() && value.specialKind().equals(diet) && value.special() > 0;
                    // As many as fit whole: in the food room, or for a needed special food, in the special room.
                    int byFood = value.food() <= 0 ? 0 : (int) Math.floor((capacity - food) / value.food());
                    int bySpecial = isSpecial ? (int) Math.floor((specialRoom - special) / value.special()) : 0;
                    int count = Math.min(stack.getCount(), Math.max(byFood, bySpecial));
                    if (count <= 0) {
                        continue;
                    }
                    stack.shrink(count);
                    food = Math.max(food, Math.min(capacity, food + count * value.food()));
                    eaten += count * value.food();
                    if (isSpecial) {
                        special = Math.max(special, Math.min(specialRoom, special + count * value.special()));
                        specialEaten += count * value.special();
                    }
                    changed = true;
                }
                if (changed) {
                    store.setChanged();
                    store.contentsChanged();
                }
            }
        }
        ColonyAccess.setFood(colony, food, ColonyAccess.starvedMinutes(colony));
        ColonyAccess.setSpecialFood(colony, special);
        return new Result(eaten, specialEaten);
    }

    private FoodIntake() {}
}
