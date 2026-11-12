package com.antcolonies.colony.species;

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
 * One thing a species can find. Either a fixed item or an item tag: for a tag the first item in it is used, and the
 * entry is skipped while the tag is empty, so modded materials only appear when some mod provides them.
 *
 * @param weight    how often it is picked compared to the species' other outputs
 * @param minStage  the colony stage (1-4) it needs
 * @param special   a rare, signature output: Specialization genetics make it more likely
 * @param processed what Processing species upgrades turn {@code ratio} of it into
 */
public record SpeciesOutput(Optional<Holder<Item>> item, Optional<TagKey<Item>> tag, int count, double weight, int minStage, boolean special,
                            Optional<Holder<Item>> processed, int ratio) {
    /** The fields alone, for records that add their own (expedition finds). Check {@link #validate} on the whole. */
    public static final com.mojang.serialization.MapCodec<SpeciesOutput> MAP_CODEC = RecordCodecBuilder.<SpeciesOutput>mapCodec(i -> i.group(
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
        return new ItemStack(set.get().get(0), amount);
    }

    public ItemStack create() {
        return create(count);
    }

    public boolean available() {
        return !create(1).isEmpty();
    }
}
