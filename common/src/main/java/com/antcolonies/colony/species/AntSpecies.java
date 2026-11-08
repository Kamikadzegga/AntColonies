package com.antcolonies.colony.species;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * One ant species, loaded from {@code data/<namespace>/antcolonies/species/<name>.json}. A species decides what a colony
 * produces; the Queen's genetics decide how well. Everything here is plain data, so modpacks add species, outputs and
 * foods without code. See the README for the format.
 *
 * @param tier     0 for the five base species and Farmer Ants, 1-3 for mineral species, 4 for the end game
 * @param look     how its ants are drawn (colors, pattern, crystals)
 * @param climate  where it thrives; elsewhere colony health suffers unless its genetics resist
 * @param spawn    where it lives in the wild (for JEI and natural spawning), if anywhere
 */
public record AntSpecies(int tier, Category category, AntLook look, Climate climate, Optional<TagKey<Biome>> spawn, Diet diet,
                         Production production, Farming farming, Combat combat, Rearing rearing, Breeding breeding,
                         Optional<Holder<Item>> signature) {

    public static final Codec<AntSpecies> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 10).optionalFieldOf("tier", 0).forGetter(AntSpecies::tier),
            Category.CODEC.optionalFieldOf("category", Category.BASE).forGetter(AntSpecies::category),
            AntLook.CODEC.fieldOf("look").forGetter(AntSpecies::look),
            Climate.CODEC.optionalFieldOf("climate", Climate.TEMPERATE).forGetter(AntSpecies::climate),
            TagKey.codec(Registries.BIOME).optionalFieldOf("spawn_biomes").forGetter(AntSpecies::spawn),
            Diet.CODEC.optionalFieldOf("diet", Diet.DEFAULT).forGetter(AntSpecies::diet),
            Production.CODEC.optionalFieldOf("production", Production.NONE).forGetter(AntSpecies::production),
            Farming.CODEC.optionalFieldOf("farming", Farming.NONE).forGetter(AntSpecies::farming),
            Combat.CODEC.optionalFieldOf("combat", Combat.DEFAULT).forGetter(AntSpecies::combat),
            Rearing.CODEC.optionalFieldOf("rearing", Rearing.DEFAULT).forGetter(AntSpecies::rearing),
            Breeding.CODEC.optionalFieldOf("breeding", Breeding.DEFAULT).forGetter(AntSpecies::breeding),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("signature_item").forGetter(AntSpecies::signature)
    ).apply(i, AntSpecies::new));

    /** Whether its workers can tend a Harvesting Site. */
    public boolean canFarm() {
        return farming.foodPerMinute() > 0;
    }

    /** Whether its workers hunt at a Hunting Ground (hunter species only). */
    public boolean canHunt() {
        return category == Category.HUNTER;
    }

    /** What its eggs need besides plain food: "" for nothing, else a special food kind (see the ant_food data map). */
    public boolean needsSpecialFood() {
        return !diet.specialKind().isEmpty() && diet.specialPerEgg() > 0;
    }

    public static String translationKey(Identifier id) {
        return "species." + id.getNamespace() + "." + id.getPath();
    }

    /**
     * @param consumption food eaten per ant compared to Common Ants
     * @param specialKind special food its eggs need ("" for none)
     * @param specialPerEgg special food units per egg laid
     */
    public record Diet(double consumption, String specialKind, double specialPerEgg) {
        public static final Diet DEFAULT = new Diet(1.0, "", 0);
        public static final Codec<Diet> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("consumption", 1.0).forGetter(Diet::consumption),
                Codec.STRING.optionalFieldOf("special_food", "").forGetter(Diet::specialKind),
                Codec.doubleRange(0, 1000).optionalFieldOf("special_per_egg", 0.0).forGetter(Diet::specialPerEgg)
        ).apply(i, Diet::new));
    }

    /** What its workers find at Foraging Chambers, and how fast compared to Common Ants. */
    public record Production(double rate, List<SpeciesOutput> outputs) {
        public static final Production NONE = new Production(1.0, List.of());
        public static final Codec<Production> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("rate", 1.0).forGetter(Production::rate),
                SpeciesOutput.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(Production::outputs)
        ).apply(i, Production::new));
    }

    /**
     * What its workers grow in a Fungus Garden: food per minute compared to Farmer Ants (0: they can't farm), and
     * the harvest (mushrooms, seeds, berries) that goes to the Storage chambers.
     */
    public record Farming(double foodPerMinute, List<SpeciesOutput> harvest) {
        public static final Farming NONE = new Farming(0, List.of());
        public static final Codec<Farming> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("food", 0.0).forGetter(Farming::foodPerMinute),
                SpeciesOutput.CODEC.listOf().optionalFieldOf("harvest", List.of()).forGetter(Farming::harvest)
        ).apply(i, Farming::new));
    }

    /** How hard its soldiers hit, and what they hunt at Hunting Chambers (each mob's own loot table is used). */
    public record Combat(double strength, List<Target> targets) {
        public static final Combat DEFAULT = new Combat(1.0, List.of());
        public static final Codec<Combat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("strength", 1.0).forGetter(Combat::strength),
                Target.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(Combat::targets)
        ).apply(i, Combat::new));

        public record Target(Holder<EntityType<?>> entity, int weight) {
            public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("entity").forGetter(Target::entity),
                    Codec.intRange(1, 100000).optionalFieldOf("weight", 1).forGetter(Target::weight)
            ).apply(i, Target::new));
        }
    }

    /** What a Queen Chamber needs to raise a new Queen of this species, and how long it takes (ticks). */
    public record Rearing(List<Cost> cost, int time) {
        public static final Rearing DEFAULT = new Rearing(List.of(), 2400);
        public static final Codec<Rearing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Cost.CODEC.listOf().optionalFieldOf("cost", List.of()).forGetter(Rearing::cost),
                Codec.intRange(1, 10_000_000).optionalFieldOf("time", 2400).forGetter(Rearing::time)
        ).apply(i, Rearing::new));
    }

    /** Egg laying and larvae growth compared to Common Ants. */
    public record Breeding(double eggs, double growth) {
        public static final Breeding DEFAULT = new Breeding(1.0, 1.0);
        public static final Codec<Breeding> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("eggs", 1.0).forGetter(Breeding::eggs),
                Codec.doubleRange(0, 1000).optionalFieldOf("growth", 1.0).forGetter(Breeding::growth)
        ).apply(i, Breeding::new));
    }
}
