package com.antcolonies.colony.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.core.registry.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * What one owner (a player or a team) has found out. Immutable; nothing in it is ever forgotten.
 *
 * @param research research entries discovered by scanning their material
 * @param species  species met in person: scanned, caught, kept in a colony or made in the laboratory
 * @param analysed species whose Queens have been analysed for compatibility
 */
public record Knowledge(Set<Identifier> research, Set<Identifier> species, Set<Identifier> analysed) {
    public static final Knowledge EMPTY = new Knowledge(Set.of(), Set.of(), Set.of());

    private static final Codec<Set<Identifier>> IDS = Identifier.CODEC.listOf().xmap(Knowledge::ordered, List::copyOf);

    public static final Codec<Knowledge> CODEC = RecordCodecBuilder.create(i -> i.group(
            IDS.optionalFieldOf("research", Set.of()).forGetter(Knowledge::research),
            IDS.optionalFieldOf("species", Set.of()).forGetter(Knowledge::species),
            IDS.optionalFieldOf("analysed", Set.of()).forGetter(Knowledge::analysed)
    ).apply(i, Knowledge::new));

    private static final StreamCodec<ByteBuf, Set<Identifier>> IDS_STREAM =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(4096)).map(Knowledge::ordered, List::copyOf);

    public static final StreamCodec<ByteBuf, Knowledge> STREAM_CODEC = StreamCodec.composite(
            IDS_STREAM, Knowledge::research,
            IDS_STREAM, Knowledge::species,
            IDS_STREAM, Knowledge::analysed,
            Knowledge::new);

    /** The three kinds of things an owner can come to know. */
    public enum Kind {
        RESEARCH,
        SPECIES,
        ANALYSIS;

        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(id -> values()[Math.clamp(id, 0, values().length - 1)],
                Kind::ordinal);
    }

    public Knowledge {
        research = ordered(research);
        species = ordered(species);
        analysed = ordered(analysed);
    }

    private static Set<Identifier> ordered(Iterable<Identifier> ids) {
        Set<Identifier> set = new LinkedHashSet<>();
        ids.forEach(set::add);
        return Collections.unmodifiableSet(set);
    }

    public boolean knows(Kind kind, Identifier id) {
        return switch (kind) {
            case RESEARCH -> research.contains(id);
            // Common Ants are where every player starts.
            case SPECIES -> species.contains(id) || ModRegistries.COMMON.equals(id);
            case ANALYSIS -> analysed.contains(id);
        };
    }

    public boolean knowsResearch(Identifier id) {
        return knows(Kind.RESEARCH, id);
    }

    public boolean knowsSpecies(Identifier id) {
        return knows(Kind.SPECIES, id);
    }

    public boolean hasAnalysed(Identifier id) {
        return knows(Kind.ANALYSIS, id);
    }

    public Knowledge with(Kind kind, Identifier id) {
        return switch (kind) {
            case RESEARCH -> new Knowledge(plus(research, id), species, analysed);
            case SPECIES -> new Knowledge(research, plus(species, id), analysed);
            case ANALYSIS -> new Knowledge(research, species, plus(analysed, id));
        };
    }

    /** Everything either of the two knows: a player's own knowledge together with the team's. */
    public Knowledge union(Knowledge other) {
        return new Knowledge(both(research, other.research), both(species, other.species), both(analysed, other.analysed));
    }

    public boolean isEmpty() {
        return research.isEmpty() && species.isEmpty() && analysed.isEmpty();
    }

    private static Set<Identifier> plus(Set<Identifier> set, Identifier id) {
        List<Identifier> next = new ArrayList<>(set);
        next.add(id);
        return ordered(next);
    }

    private static Set<Identifier> both(Set<Identifier> a, Set<Identifier> b) {
        List<Identifier> next = new ArrayList<>(a);
        next.addAll(b);
        return ordered(next);
    }
}
