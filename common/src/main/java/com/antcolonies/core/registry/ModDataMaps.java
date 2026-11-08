package com.antcolonies.core.registry;

import com.antcolonies.AntColonies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code data/<namespace>/data_maps/item/ant_food.json}: what each item is worth as ant food. Any mod's food can be
 * given a value there (nothing is guessed from item names). Synced, so tooltips and JEI show the server's values.
 */
public final class ModDataMaps {
    /**
     * @param food        plain colony food
     * @param specialKind the special food it also gives ("mineral", "crystal", "nether"; "" for none)
     * @param special     special food units
     */
    public record AntFood(double food, String specialKind, double special) {
        public static final Codec<AntFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1e9).fieldOf("food").forGetter(AntFood::food),
                Codec.STRING.optionalFieldOf("special_kind", "").forGetter(AntFood::specialKind),
                Codec.doubleRange(0, 1e9).optionalFieldOf("special", 0.0).forGetter(AntFood::special)
        ).apply(i, AntFood::new));
    }

    public static final DataMapType<Item, AntFood> ANT_FOOD = DataMapType.builder(AntColonies.id("ant_food"), Registries.ITEM, AntFood.CODEC)
            .synced(AntFood.CODEC, false)
            .build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(ANT_FOOD);
    }

    public static @Nullable AntFood food(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(ANT_FOOD);
    }

    private ModDataMaps() {}
}
