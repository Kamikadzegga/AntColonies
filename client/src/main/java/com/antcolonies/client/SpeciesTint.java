package com.antcolonies.client;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.colony.species.AntLook;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item model tint {@code antcolonies:species}: the color of the species an item holds (tubes, samples, eggs, larvae,
 * species upgrades), so every species gets its own look without its own textures.
 */
public record SpeciesTint(Part part, int fallback) implements ItemTintSource {
    public static final Identifier ID = AntColonies.id("species");

    public enum Part implements StringRepresentable {
        BODY("body"), GASTER("gaster"), ACCENT("accent"), CRYSTAL("crystal");

        public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);
        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<SpeciesTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Part.CODEC.optionalFieldOf("part", Part.GASTER).forGetter(SpeciesTint::part),
            Codec.INT.optionalFieldOf("default", 0xFFFFFFFF).forGetter(SpeciesTint::fallback)
    ).apply(i, SpeciesTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier species = species(stack);
        if (species == null) {
            return fallback;
        }
        AntLook look = ClientSpecies.look(species);
        int rgb = switch (part) {
            case BODY -> look.body();
            case GASTER -> look.gaster();
            case ACCENT -> look.accent();
            case CRYSTAL -> look.crystal().orElse(look.gaster());
        };
        return ARGB.opaque(rgb);
    }

    private static @Nullable Identifier species(ItemStack stack) {
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        if (queen != null) {
            return queen.species();
        }
        AntSample ants = stack.get(ModDataComponents.ANTS.get());
        if (ants != null) {
            return ants.species();
        }
        SpeciesUpgrade upgrade = stack.get(ModDataComponents.SPECIES_UPGRADE.get());
        if (upgrade != null) {
            return upgrade.species();
        }
        return stack.get(ModDataComponents.SPECIES.get());
    }

    @Override
    public MapCodec<SpeciesTint> type() {
        return MAP_CODEC;
    }
}
