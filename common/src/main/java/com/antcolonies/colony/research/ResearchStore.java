package com.antcolonies.colony.research;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.antcolonies.AntColonies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every player's and every team's genetic research, saved with the world (in the Overworld's data folder).
 * A player sees their own knowledge together with their team's; the two are kept apart, so leaving a team takes
 * only the team's part away.
 */
public final class ResearchStore extends SavedData {
    /** The format of the saved file: raise it, and add a step to {@link #migrate}, when the layout changes. */
    public static final int VERSION = 1;

    private static final Codec<Map<UUID, Knowledge>> OWNERS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Knowledge.CODEC);

    public static final Codec<ResearchStore> CODEC = RecordCodecBuilder.create(i -> i.group(
            // Always written (a field with a default is left out when it holds the default).
            Codec.INT.optionalFieldOf("version").xmap(version -> version.orElse(VERSION), Optional::of).forGetter(store -> VERSION),
            OWNERS.optionalFieldOf("players", Map.of()).forGetter(store -> store.players),
            OWNERS.optionalFieldOf("teams", Map.of()).forGetter(store -> store.teams)
    ).apply(i, ResearchStore::new));

    public static final SavedDataType<ResearchStore> TYPE = new SavedDataType<>(AntColonies.id("genetic_research"), ResearchStore::new, CODEC);

    private final Map<UUID, Knowledge> players = new LinkedHashMap<>();
    private final Map<UUID, Knowledge> teams = new LinkedHashMap<>();

    public ResearchStore() {
    }

    private ResearchStore(int version, Map<UUID, Knowledge> players, Map<UUID, Knowledge> teams) {
        this.players.putAll(players);
        this.teams.putAll(teams);
        migrate(version);
    }

    /** Brings a file written by an older version up to date. Version 1 is the first: nothing to do yet. */
    private void migrate(int version) {
        if (version > VERSION) {
            AntColonies.LOGGER.warn("Genetic research was saved by a newer version ({} > {}); reading what this version knows", version, VERSION);
        }
    }

    public static ResearchStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Knowledge personal(UUID player) {
        return players.getOrDefault(player, Knowledge.EMPTY);
    }

    public Knowledge team(UUID team) {
        return teams.getOrDefault(team, Knowledge.EMPTY);
    }

    /** What a player can use right now: their own research and, while they are in a team, the team's. */
    public Knowledge view(MinecraftServer server, UUID player) {
        Optional<UUID> team = Teams.teamOf(server, player);
        return team.isPresent() ? personal(player).union(team(team.get())) : personal(player);
    }

    /**
     * Records a discovery made by a player: for the team when they are in one, for themselves otherwise.
     * Returns false when they could already use it (nothing is written twice).
     */
    public boolean discover(MinecraftServer server, UUID player, Knowledge.Kind kind, Identifier id) {
        Optional<UUID> team = Teams.teamOf(server, player);
        if (personal(player).knows(kind, id) || team.isPresent() && team(team.get()).knows(kind, id)) {
            return false;
        }
        if (team.isPresent()) {
            teams.put(team.get(), team(team.get()).with(kind, id));
        } else {
            players.put(player, personal(player).with(kind, id));
        }
        setDirty();
        return true;
    }
}
