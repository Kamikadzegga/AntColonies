package com.antcolonies.colony.species;

import java.util.List;
import java.util.Optional;

import com.antcolonies.colony.liquid.LiquidKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

/**
 * One ant species, as its data file gives it: it decides what a colony produces.
 *
 * @param tier     0 for the five base species and Farmer Ants, 1-3 for mineral species, 4 for the end game
 * @param spawn    where it lives in the wild, if anywhere
 * @param liquid   Liquid Ants only: the fluid they make droplets of
 */
public record AntSpecies(int tier, Category category, AntLook look, Climate climate, Optional<TagKey<Biome>> spawn, Diet diet,
                         Production production, Farming farming, Combat combat, Rearing rearing, Breeding breeding,
                         Optional<Holder<Item>> signature, Optional<Liquid> liquid) {

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
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("signature_item").forGetter(AntSpecies::signature),
            Liquid.CODEC.optionalFieldOf("liquid").forGetter(AntSpecies::liquid)
    ).apply(i, AntSpecies::new));

    public boolean canFarm() {
        return farming.foodPerMinute() > 0;
    }

    public boolean canHunt() {
        return category == Category.HUNTER;
    }

    /** Whether its workers give droplets at a Liquid Chamber: Liquid Ants, and any other species with a liquid (Cow Ants). */
    public boolean makesLiquid() {
        return category == Category.LIQUID || liquid.isPresent();
    }

    /** A Liquid Ant proper: its workers work at Liquid Chambers and nowhere else. */
    public boolean liquidOnly() {
        return category == Category.LIQUID;
    }

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

    /** What its workers find at Foraging Sites, and how fast compared to Common Ants. */
    public record Production(double rate, List<SpeciesOutput> outputs) {
        public static final Production NONE = new Production(1.0, List.of());
        public static final Codec<Production> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("rate", 1.0).forGetter(Production::rate),
                SpeciesOutput.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(Production::outputs)
        ).apply(i, Production::new));
    }

    /** What its workers grow at a Harvesting Site: food per minute compared to Farmer Ants (0: they can't farm), and the harvest. */
    public record Farming(double foodPerMinute, List<SpeciesOutput> harvest) {
        public static final Farming NONE = new Farming(0, List.of());
        public static final Codec<Farming> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("food", 0.0).forGetter(Farming::foodPerMinute),
                SpeciesOutput.CODEC.listOf().optionalFieldOf("harvest", List.of()).forGetter(Farming::harvest)
        ).apply(i, Farming::new));
    }

    /** How hard its soldiers hit, and what they hunt at Hunting Grounds (each mob's own loot table is used). */
    public record Combat(double strength, List<Target> targets) {
        public static final Combat DEFAULT = new Combat(1.0, List.of());
        public static final Codec<Combat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("strength", 1.0).forGetter(Combat::strength),
                Target.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(Combat::targets)
        ).apply(i, Combat::new));

        public record Target(Holder<EntityType<?>> entity, int weight, Optional<Trophy> trophy) {
            public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("entity").forGetter(Target::entity),
                    Codec.intRange(1, 100000).optionalFieldOf("weight", 1).forGetter(Target::weight),
                    Trophy.CODEC.optionalFieldOf("trophy").forGetter(Target::trophy)
            ).apply(i, Target::new));

            public Target(Holder<EntityType<?>> entity, int weight) {
                this(entity, weight, Optional.empty());
            }
        }

        /** A drop the mob only gives a player: hunters of a colony at {@code minStage} bring it with this chance a catch. */
        public record Trophy(Holder<Item> item, double chance, int minStage) {
            public static final Codec<Trophy> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Trophy::item),
                    Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Trophy::chance),
                    Codec.intRange(1, 4).optionalFieldOf("min_stage", 1).forGetter(Trophy::minStage)
            ).apply(i, Trophy::new));
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

    /**
     * What a Liquid Ant species makes. The fluid and the droplet are ids looked up when needed, so a species whose mod
     * was removed still loads and just makes nothing.
     *
     * @param fluids one fluid, or the same liquid as several mods add it: the first that is in the game is made
     * @param rate   droplets per minute compared to Water Ants
     */
    public record Liquid(List<Identifier> fluids, Identifier droplet, double rate) {
        public static final Codec<Liquid> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.nonEmptyList(ExtraCodecs.compactListCodec(Identifier.CODEC)).fieldOf("fluid").forGetter(Liquid::fluids),
                Identifier.CODEC.fieldOf("droplet").forGetter(Liquid::droplet),
                Codec.doubleRange(0, 1000).optionalFieldOf("rate", 1.0).forGetter(Liquid::rate)
        ).apply(i, Liquid::new));

        /** The fluid in use: the first listed that is in the game, else the first listed. */
        public Identifier fluid() {
            for (Identifier fluid : fluids) {
                if (LiquidKind.usable(fluid)) {
                    return fluid;
                }
            }
            return fluids.getFirst();
        }

        public Optional<Item> dropletItem() {
            return BuiltInRegistries.ITEM.getOptional(droplet);
        }

        public Optional<Fluid> fluidValue() {
            return BuiltInRegistries.FLUID.getOptional(fluid());
        }

        public boolean available() {
            return dropletItem().isPresent() && fluidValue().isPresent();
        }
    }

    /** Egg laying and larvae growth compared to Common Ants. */
    /** @param lifespan how long its adults live, compared to the config's adultLifespanMinutes */
    public record Breeding(double eggs, double growth, double lifespan) {
        public static final Breeding DEFAULT = new Breeding(1.0, 1.0, 1.0);
        public static final Codec<Breeding> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1000).optionalFieldOf("eggs", 1.0).forGetter(Breeding::eggs),
                Codec.doubleRange(0, 1000).optionalFieldOf("growth", 1.0).forGetter(Breeding::growth),
                Codec.doubleRange(0.01, 1000).optionalFieldOf("lifespan", 1.0).forGetter(Breeding::lifespan)
        ).apply(i, Breeding::new));
    }
}
