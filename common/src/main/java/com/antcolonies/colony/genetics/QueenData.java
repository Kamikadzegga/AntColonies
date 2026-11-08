package com.antcolonies.colony.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A Queen as Queen Test Tubes, Genetic Samples and Terrariums carry her: her species and her genome. A Genetic Sample
 * holds a copy of a Queen's genes (the same data), which a Queen Chamber can cross into a daughter.
 */
public record QueenData(Identifier species, Genome genome) {
    public static final Codec<QueenData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("species").forGetter(QueenData::species),
            Genome.CODEC.fieldOf("genome").forGetter(QueenData::genome)
    ).apply(i, QueenData::new));

    public static final StreamCodec<ByteBuf, QueenData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, QueenData::species,
            Genome.STREAM_CODEC, QueenData::genome,
            QueenData::new);

    public QueenData withGenome(Genome genome) {
        return new QueenData(species, genome);
    }

    /** The same Queen evolved into another species; her lineage records it. */
    public QueenData evolvedInto(Identifier next) {
        return new QueenData(next, genome.withLineage(genome.lineage().withSpecies(next)));
    }
}
