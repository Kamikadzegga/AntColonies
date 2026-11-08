package com.antcolonies.colony.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Where a Queen comes from: her own id and name, her mother's id, how many generations of rearing lie behind her, and
 * every species her line has been (wild origin, hybrid crosses and evolutions, oldest first).
 */
public record Lineage(UUID id, String name, Optional<UUID> parent, int generation, List<Identifier> history) {
    public static final Codec<Lineage> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Lineage::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Lineage::name),
            UUIDUtil.CODEC.optionalFieldOf("parent").forGetter(Lineage::parent),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("generation", 0).forGetter(Lineage::generation),
            Identifier.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(Lineage::history)
    ).apply(i, Lineage::new));

    public static final StreamCodec<ByteBuf, Lineage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Lineage::id,
            ByteBufCodecs.STRING_UTF8, Lineage::name,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Lineage::parent,
            ByteBufCodecs.VAR_INT, Lineage::generation,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Lineage::history,
            Lineage::new);

    /** The same Queen, now of another species (after an evolution). */
    public Lineage withSpecies(Identifier species) {
        List<Identifier> next = new ArrayList<>(history);
        if (next.isEmpty() || !next.getLast().equals(species)) {
            next.add(species);
        }
        // Long lines keep their newest 32 entries.
        while (next.size() > 32) {
            next.removeFirst();
        }
        return new Lineage(id, name, parent, generation, List.copyOf(next));
    }
}
