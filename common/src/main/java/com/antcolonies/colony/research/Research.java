package com.antcolonies.colony.research;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Cost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One genetic discovery: scanning {@code material} in the Genetic Laboratory reveals a species mutation or a gene.
 *
 * @param species   the genetically modified species it unlocks in the Genetic Laboratory (every evolution into it)
 * @param mutation  or the Queen mutation it reveals (knowledge only: the lab applies mutations as before)
 * @param energy    FE one species mutation takes (default: by tier)
 * @param time      ticks one species mutation takes (default: by tier)
 * @param catalysts what a species mutation takes on top of the evolution's own material
 * @param requires  research that must be known before this one can be discovered
 */
public record Research(Ingredient material, Optional<Identifier> species, Optional<Identifier> mutation, Tier tier,
                       Optional<Integer> energy, Optional<Integer> time, List<Cost> catalysts, List<Identifier> requires) {
    public static final Codec<Research> CODEC = RecordCodecBuilder.<Research>create(i -> i.group(
            Ingredient.CODEC.fieldOf("material").forGetter(Research::material),
            Identifier.CODEC.optionalFieldOf("species").forGetter(Research::species),
            Identifier.CODEC.optionalFieldOf("mutation").forGetter(Research::mutation),
            Tier.CODEC.optionalFieldOf("tier", Tier.BASIC).forGetter(Research::tier),
            Codec.intRange(0, 100_000_000).optionalFieldOf("energy").forGetter(Research::energy),
            Codec.intRange(1, 10_000_000).optionalFieldOf("time").forGetter(Research::time),
            Cost.CODEC.listOf().optionalFieldOf("catalysts", List.of()).forGetter(Research::catalysts),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Research::requires)
    ).apply(i, Research::new)).validate(research -> research.species().isPresent() != research.mutation().isPresent()
            ? DataResult.success(research)
            : DataResult.error(() -> "A research entry needs exactly one of \"species\" and \"mutation\""));

    /** How deep into the game a discovery sits; it sets the default cost of its species mutation. */
    public enum Tier implements StringRepresentable {
        BASIC(40_000, 600),
        ADVANCED(120_000, 1200),
        EXPERIMENTAL(400_000, 2400);

        public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

        private final int energy;
        private final int time;

        Tier(int energy, int time) {
            this.energy = energy;
            this.time = time;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "research.antcolonies.tier." + getSerializedName();
        }
    }

    public boolean unlocksSpecies() {
        return species.isPresent();
    }

    public int energyCost() {
        return energy.orElse(tier.energy);
    }

    public int ticks() {
        return time.orElse(tier.time);
    }

    /** "Iron Ant Mutation" or "Gene: Iron Jaws". */
    public MutableComponent title() {
        return species.isPresent()
                ? Component.translatable("research.antcolonies.title.species", Component.translatable(AntSpecies.translationKey(species.get())))
                : Component.translatable("research.antcolonies.title.gene", Component.translatable(Mutation.translationKey(mutation.orElseThrow())));
    }

    /** The lang key of an entry's hint; packs may leave it out (the tier's general hint is shown). */
    public static String hintKey(Identifier id) {
        return "research." + id.getNamespace() + "." + id.getPath() + ".hint";
    }
}
