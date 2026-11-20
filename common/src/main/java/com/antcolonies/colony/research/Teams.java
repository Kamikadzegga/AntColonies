package com.antcolonies.colony.research;

import java.util.Optional;
import java.util.UUID;

import com.antcolonies.AntColonies;

import net.minecraft.server.MinecraftServer;

/** Which team a player shares research with. No team mod: nobody has a team and research is each player's own. */
public final class Teams {
    /** A team mod's answer: the stable id of the team a player is in, or empty when the player is on their own. */
    public interface Provider {
        Optional<UUID> teamOf(MinecraftServer server, UUID player);
    }

    private static final Provider NONE = (server, player) -> Optional.empty();
    private static Provider provider = NONE;
    private static boolean failed;

    public static void setProvider(Provider next) {
        provider = next;
        failed = false;
    }

    public static void clearProvider() {
        provider = NONE;
    }

    public static boolean hasProvider() {
        return provider != NONE;
    }

    public static Optional<UUID> teamOf(MinecraftServer server, UUID player) {
        try {
            return provider.teamOf(server, player);
        } catch (RuntimeException | LinkageError e) {
            // A team mod that changed under us must not take research down with it: players keep their own.
            if (!failed) {
                failed = true;
                AntColonies.LOGGER.error("The team integration failed; research is personal until the next restart", e);
            }
            return Optional.empty();
        }
    }

    private Teams() {}
}
