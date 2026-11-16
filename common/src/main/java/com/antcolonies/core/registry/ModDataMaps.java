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

/** {@code data/<namespace>/data_maps/item/ant_food.json}: what each item is worth as ant food, synced to clients. */
public final class ModDataMaps {
    /** An item's plain colony food, plus units of a special food ("mineral", "crystal", "nether"; "" for none). */
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
