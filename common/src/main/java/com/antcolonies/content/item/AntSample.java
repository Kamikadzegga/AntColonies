package com.antcolonies.content.item;

import com.antcolonies.colony.species.Caste;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Ants caught in a Test Tube: their species, caste and how many (a tube holds up to {@link #MAX}). */
public record AntSample(Identifier species, Caste caste, int count) {
    public static final int MAX = 1000;
    /** How many a pinch of the Tweezers takes from a Terrarium (the Terrarium's buttons take a whole tube). */
    public static final int PINCH = 16;
    public static final Codec<AntSample> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("species").forGetter(AntSample::species),
            Caste.CODEC.optionalFieldOf("caste", Caste.WORKER).forGetter(AntSample::caste),
            Codec.intRange(1, MAX).optionalFieldOf("count", 1).forGetter(AntSample::count)
    ).apply(i, AntSample::new));
    public static final StreamCodec<ByteBuf, AntSample> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, AntSample::species,
            Caste.STREAM_CODEC, AntSample::caste,
            ByteBufCodecs.VAR_INT, AntSample::count,
            AntSample::new);

    public AntSample withCount(int count) {
        return new AntSample(species, caste, Math.clamp(count, 1, MAX));
    }
}
