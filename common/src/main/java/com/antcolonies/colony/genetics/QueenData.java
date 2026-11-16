package com.antcolonies.colony.genetics;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.core.config.AntConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * A Queen as Queen Test Tubes, Genetic Samples and Terrariums carry her.
 *
 * @param grown  the highest colony stage she has grown into (0 in old saves: she takes her colony's stage)
 * @param growth minutes her colony has spent at a bigger stage than {@code grown}
 */
public record QueenData(Identifier species, Genome genome, int grown, float growth) {
    /** The highest level a Queen's trait reaches by growing with her colony (config queenGrowthCap): higher takes the Genetic Laboratory. */
    public static int growthCap() {
        return AntConfig.get(AntConfig.QUEEN_GROWTH_CAP);
    }

    public static final Codec<QueenData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("species").forGetter(QueenData::species),
            Genome.CODEC.fieldOf("genome").forGetter(QueenData::genome),
            Codec.intRange(0, 4).optionalFieldOf("grown", 0).forGetter(QueenData::grown),
            Codec.FLOAT.optionalFieldOf("growth", 0.0F).forGetter(QueenData::growth)
    ).apply(i, QueenData::new));

    public static final StreamCodec<ByteBuf, QueenData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, QueenData::species,
            Genome.STREAM_CODEC, QueenData::genome,
            ByteBufCodecs.VAR_INT, QueenData::grown,
            ByteBufCodecs.FLOAT, QueenData::growth,
            QueenData::new);

    /** A new Queen: she has grown with no colony yet. */
    public QueenData(Identifier species, Genome genome) {
        this(species, genome, 1, 0.0F);
    }

    /** A Queen grown into a stage, with nothing yet towards the next. */
    public QueenData(Identifier species, Genome genome, int grown) {
        this(species, genome, grown, 0.0F);
    }

    public QueenData withGenome(Genome genome) {
        return new QueenData(species, genome, grown, growth);
    }

    /** The same Queen evolved into another species; her lineage records it. */
    public QueenData evolvedInto(Identifier next) {
        return new QueenData(next, genome.withLineage(genome.lineage().withSpecies(next)), grown, growth);
    }

    /** Minutes at a bigger colony stage a Queen needs to grow into {@code stage}. */
    public static double minutesFor(int stage) {
        return AntConfig.get(AntConfig.QUEEN_GROWTH_MINUTES) * Math.max(1, stage - 1);
    }

    /** Minutes still to go before she grows into her next stage. */
    public double minutesToNext() {
        return Math.max(0, minutesFor(grown + 1) - growth);
    }

    /**
     * Counts {@code minutes} towards her next stage; once enough have passed she grows into it and random traits (added
     * to {@code raised}) go up by one. Returns this Queen when nothing changes.
     */
    public QueenData growTo(int stage, double minutes, RandomSource random, List<Trait> raised) {
        if (grown == 0) {
            return new QueenData(species, genome, Math.max(1, stage), 0.0F);
        }
        if (stage <= grown || grown >= 4) {
            return this;
        }
        double progress = growth + minutes;
        double need = minutesFor(grown + 1);
        if (progress < need) {
            return new QueenData(species, genome, grown, (float) progress);
        }
        int cap = Math.min(growthCap(), Trait.maxLevel());
        Genome next = genome;
        List<Trait> open = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            if (next.level(trait) < cap) {
                open.add(trait);
            }
        }
        for (int n = 0, traits = AntConfig.get(AntConfig.QUEEN_GROWTH_TRAITS); n < traits && !open.isEmpty(); n++) {
            Trait trait = open.remove(random.nextInt(open.size()));
            next = next.withLevel(trait, next.level(trait) + 1);
            raised.add(trait);
        }
        return new QueenData(species, next, grown + 1, 0.0F);
    }
}
