package com.antcolonies.colony.research;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps every online player's client in step with what they and their team know. */
public final class ResearchSync {
    /** How often (ticks) team membership is looked at again: joining or leaving a team shows within two seconds. */
    private static final int TEAM_CHECK_INTERVAL = 40;

    /** The team each online player was last told about. */
    private static final Map<UUID, Optional<UUID>> LAST_TEAM = new HashMap<>();

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, List.of());
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TEAM.remove(event.getEntity().getUUID());
    }

    /** Players whose team changed are sent their new view: the old team's research goes, the new team's comes. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TEAM_CHECK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshIfTeamChanged(player);
        }
    }

    /** Sends a player their view again if their team is not the one they were last told about. */
    public static void refreshIfTeamChanged(ServerPlayer player) {
        Optional<UUID> team = Teams.teamOf(player.level().getServer(), player.getUUID());
        if (!team.equals(LAST_TEAM.get(player.getUUID()))) {
            send(player, List.of());
        }
    }

    public static void send(ServerPlayer player, List<KnowledgePayload.News> news) {
        MinecraftServer server = player.level().getServer();
        ResearchStore store = ResearchStore.of(server);
        Optional<UUID> team = Teams.teamOf(server, player.getUUID());
        LAST_TEAM.put(player.getUUID(), team);
        // A client without the mod (or a test's stand-in player) has nowhere to take it.
        if (player.connection.hasChannel(KnowledgePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new KnowledgePayload(store.personal(player.getUUID()), team.map(store::team), news));
        }
    }

    /** After a discovery by {@code player}: they and, for a team discovery, every online team member hear of it. */
    static void announce(MinecraftServer server, UUID player, KnowledgePayload.News news) {
        Optional<UUID> team = Teams.teamOf(server, player);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            boolean shares = online.getUUID().equals(player)
                    || team.isPresent() && team.equals(Teams.teamOf(server, online.getUUID()));
            if (shares) {
                send(online, List.of(news));
            }
        }
    }

    private ResearchSync() {}
}
