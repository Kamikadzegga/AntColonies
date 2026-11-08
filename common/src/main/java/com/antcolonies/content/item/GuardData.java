package com.antcolonies.content.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A Guard Ant in its tube: the colony's species, the Soldier Strength level of the Queen it came from (1 to 10), and
 * its health when it was picked up ({@code -1}: full).
 */
public record GuardData(Identifier species, int strength, float health) {
    public static final Codec<GuardData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("species").forGetter(GuardData::species),
            Codec.intRange(1, 10).optionalFieldOf("strength", 1).forGetter(GuardData::strength),
            Codec.FLOAT.optionalFieldOf("health", -1.0F).forGetter(GuardData::health)
    ).apply(i, GuardData::new));
    public static final StreamCodec<ByteBuf, GuardData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, GuardData::species,
            ByteBufCodecs.VAR_INT, GuardData::strength,
            ByteBufCodecs.FLOAT, GuardData::health,
            GuardData::new);
}
