package com.antcolonies.compat.ftbteams;

import java.util.Optional;
import java.util.UUID;

import com.antcolonies.colony.research.ResearchSync;
import com.antcolonies.colony.research.Teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FTB Teams: players in a party share their genetic research. Only loaded when FTB Teams is installed.
 * Written against FTB Teams 26.1.2.5 (its {@code api} package).
 */
public final class FtbTeamsResearch {
    public static final String MOD_ID = "ftbteams";

    public static void register() {
        Teams.setProvider(FtbTeamsResearch::partyOf);
        // Joining, leaving, being kicked and a party being made all come through this one event.
        NeoForge.EVENT_BUS.addListener(FTBTeamsEvent.PlayerChangedTeam.class, event -> {
            if (FTBTeamsAPI.api().isManagerLoaded()) {
                MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
                server.getPlayerList().getPlayers().forEach(ResearchSync::refreshIfTeamChanged);
            }
        });
    }

    /**
     * The party a player is in. A player on their own has a personal team in FTB Teams, which is not a team to
     * share with: their research stays personal. A party's id is made once and never changes.
     */
    private static Optional<UUID> partyOf(MinecraftServer server, UUID player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player).filter(Team::isPartyTeam).map(Team::getId);
    }

    private FtbTeamsResearch() {}
}
