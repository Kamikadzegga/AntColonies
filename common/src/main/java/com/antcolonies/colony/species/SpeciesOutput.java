package com.antcolonies.colony.species;

import com.antcolonies.core.config.AntConfig;
import com.mojang.serialization.MapCodec;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One thing a species can find: a fixed item, or an item of a tag (skipped while the tag is empty). Of a tag that several
 * mods fill, one item is found: see {@link #preferred}.
 *
 * @param weight    how often it is picked compared to the species' other outputs
 * @param minStage  the colony stage (1-4) it needs
 * @param special   a rare, signature output
 * @param processed what Processing species upgrades turn {@code ratio} of it into
 */
public record SpeciesOutput(Optional<Holder<Item>> item, Optional<TagKey<Item>> tag, int count, double weight, int minStage, boolean special,
                            Optional<Holder<Item>> processed, int ratio) {
    /** The fields alone, for records that add their own (expedition finds). Check {@link #validate} on the whole. */
    public static final MapCodec<SpeciesOutput> MAP_CODEC = RecordCodecBuilder.<SpeciesOutput>mapCodec(i -> i.group(
            Item.CODEC.optionalFieldOf("item").forGetter(SpeciesOutput::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(SpeciesOutput::tag),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(SpeciesOutput::count),
            Codec.doubleRange(0.0001, 100000).optionalFieldOf("weight", 10.0).forGetter(SpeciesOutput::weight),
            Codec.intRange(1, 4).optionalFieldOf("min_stage", 1).forGetter(SpeciesOutput::minStage),
            Codec.BOOL.optionalFieldOf("special", false).forGetter(SpeciesOutput::special),
            Item.CODEC.optionalFieldOf("processed").forGetter(SpeciesOutput::processed),
            Codec.intRange(1, 64).optionalFieldOf("ratio", 9).forGetter(SpeciesOutput::ratio)
    ).apply(i, SpeciesOutput::new));

    public static DataResult<SpeciesOutput> validate(SpeciesOutput output) {
        return output.item.isPresent() != output.tag.isPresent()
                ? DataResult.success(output) : DataResult.error(() -> "A species output needs exactly one of \"item\" and \"tag\"");
    }

    public static final Codec<SpeciesOutput> CODEC = RecordCodecBuilder.<SpeciesOutput>create(i -> i.group(
            Item.CODEC.optionalFieldOf("item").forGetter(SpeciesOutput::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(SpeciesOutput::tag),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(SpeciesOutput::count),
            Codec.doubleRange(0.0001, 100000).optionalFieldOf("weight", 10.0).forGetter(SpeciesOutput::weight),
            Codec.intRange(1, 4).optionalFieldOf("min_stage", 1).forGetter(SpeciesOutput::minStage),
            Codec.BOOL.optionalFieldOf("special", false).forGetter(SpeciesOutput::special),
            Item.CODEC.optionalFieldOf("processed").forGetter(SpeciesOutput::processed),
            Codec.intRange(1, 64).optionalFieldOf("ratio", 9).forGetter(SpeciesOutput::ratio)
    ).apply(i, SpeciesOutput::new)).validate(output -> output.item.isPresent() != output.tag.isPresent()
            ? DataResult.success(output) : DataResult.error(() -> "A species output needs exactly one of \"item\" and \"tag\""));

    /** The item it makes right now, or empty while its tag holds nothing. */
    public ItemStack create(int amount) {
        if (item.isPresent()) {
            return new ItemStack(item.get(), amount);
        }
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.get(tag.get());
        if (set.isEmpty() || set.get().size() == 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(preferred(set.get()), amount);
    }

    /**
     * Which of a tag's items is found: one named in the config's preferredItems, else the one of the first mod in
     * preferredItemMods (All The Ores by default), else the tag's first, which is that of the mod the game loaded first.
     */
    private static Holder<Item> preferred(HolderSet.Named<Item> items) {
        if (items.size() > 1) {
            for (String id : AntConfig.strings(AntConfig.PREFERRED_ITEMS)) {
                for (Holder<Item> item : items) {
                    if (item.unwrapKey().map(key -> key.identifier().toString().equals(id)).orElse(false)) {
                        return item;
                    }
                }
            }
            for (String mod : AntConfig.strings(AntConfig.PREFERRED_ITEM_MODS)) {
                for (Holder<Item> item : items) {
                    if (item.unwrapKey().map(key -> key.identifier().getNamespace().equals(mod)).orElse(false)) {
                        return item;
                    }
                }
            }
        }
        return items.get(0);
    }

    public ItemStack create() {
        return create(count);
    }

    public boolean available() {
        return !create(1).isEmpty();
    }
}
