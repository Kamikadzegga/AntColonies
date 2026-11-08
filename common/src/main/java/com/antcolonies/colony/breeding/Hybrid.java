package com.antcolonies.colony.breeding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A hybrid species, loaded from {@code data/<namespace>/antcolonies/hybrid/<name>.json}. A Queen Chamber whose colony
 * Queen is one parent species and whose Genetic Sample slot holds a sample of the other may raise a Queen of
 * {@code result} instead, with {@code chance} (raised by Mutation Probability). Only the pairs listed here can cross.
 *
 * @param minAverageLevel the average trait level both parents need, so rare hybrids take good stock
 */
public record Hybrid(Identifier first, Identifier second, Identifier result, double chance, double minAverageLevel) {
    public static final Codec<Hybrid> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("first").forGetter(Hybrid::first),
            Identifier.CODEC.fieldOf("second").forGetter(Hybrid::second),
            Identifier.CODEC.fieldOf("result").forGetter(Hybrid::result),
            Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Hybrid::chance),
            Codec.doubleRange(1, 10).optionalFieldOf("min_average_level", 1.0).forGetter(Hybrid::minAverageLevel)
    ).apply(i, Hybrid::new));

    public boolean matches(Identifier a, Identifier b) {
        return first.equals(a) && second.equals(b) || first.equals(b) && second.equals(a);
    }
}
