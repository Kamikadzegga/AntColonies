package com.antcolonies.companion;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.antcolonies.AntColonies;
import com.antcolonies.companion.goal.CompanionTravel;
import com.antcolonies.companion.menu.RosterPayload;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every Guard Ant that is out in the world, with its owner and where it was last seen, saved with the world: an owner can
 * list theirs and call one from wherever it is. An ant in a tube is not on it.
 */
public final class CompanionRoster extends SavedData {
    /** How long a called ant has to turn up before the call is given up, in ticks (its chunks are held that long). */
    private static final int CALL_TICKS = 300;
    /** Chunks around a called ant that are loaded: enough for the one in the middle to tick. */
    private static final int CALL_CHUNKS = 2;

    /** One ant: who it is and where it was last seen. */
    public record Entry(UUID id, UUID owner, String name, Identifier species, int level, ResourceKey<Level> dimension, BlockPos pos) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
                Identifier.CODEC.fieldOf("species").forGetter(Entry::species),
                Codec.INT.optionalFieldOf("level", 1).forGetter(Entry::level),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(i, Entry::new));
    }

    public static final Codec<CompanionRoster> CODEC = Entry.CODEC.listOf().fieldOf("companions").codec()
            .xmap(CompanionRoster::new, roster -> List.copyOf(roster.entries.values()));
    public static final SavedDataType<CompanionRoster> TYPE = new SavedDataType<>(AntColonies.id("companions"), CompanionRoster::new, CODEC);

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    /** Ants that were called while their chunks were not loaded: who called, and until when. Not saved. */
    private final Map<UUID, Long> called = new HashMap<>();

    public CompanionRoster() {
    }

    private CompanionRoster(List<Entry> saved) {
        saved.forEach(entry -> entries.put(entry.id(), entry));
    }

    public static CompanionRoster of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // --- Keeping it ---

    /** Writes down a companion as it is now. */
    public void note(Mob mob, CompanionData data) {
        if (data.owner().isEmpty()) {
            return;
        }
        Entry entry = new Entry(data.id(), data.owner().get().id(), mob.hasCustomName() ? mob.getCustomName().getString() : "", data.species(),
                data.level(), mob.level().dimension(), mob.blockPosition());
        if (!entry.equals(entries.put(entry.id(), entry))) {
            setDirty();
        }
    }

    /** An ant that died or went into a tube. */
    public void forget(UUID id) {
        if (entries.remove(id) != null) {
            setDirty();
        }
        called.remove(id);
    }

    public int count(UUID owner) {
        return (int) entries.values().stream().filter(entry -> entry.owner().equals(owner)).count();
    }

    // --- Showing it ---

    /** Sends a player their roster, the loaded ants as they are this moment. */
    public void show(ServerPlayer player) {
        CompanionIndex.of(player.getUUID()).forEach(mob -> note(mob, Companions.get(mob)));
        List<RosterPayload.Row> rows = entries.values().stream().filter(entry -> entry.owner().equals(player.getUUID()))
                .map(entry -> new RosterPayload.Row(entry.id(), entry.name(), entry.species(), entry.level(), entry.dimension().identifier(), entry.pos(),
                        called.containsKey(entry.id()))).toList();
        PacketDistributor.sendToPlayer(player, new RosterPayload(rows, AntConfig.get(AntConfig.COMPANION_MAX)));
    }

    // --- Calling ---

    /** Brings one of a player's ants to them from wherever it is, for a Rally Pheromone. */
    public void call(ServerPlayer player, UUID id) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.owner().equals(player.getUUID()) || called.containsKey(id)) {
            return;
        }
        Mob mob = CompanionIndex.byId(id);
        boolean here = mob != null && mob.isAlive();
        ServerLevel level = player.level().getServer().getLevel(entry.dimension());
        if (!here && level == null) {
            // Its dimension is no more.
            forget(id);
            return;
        }
        if (!player.hasInfiniteMaterials() && !takePheromone(player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.call.pheromone").withStyle(ChatFormatting.RED));
            return;
        }
        if (here) {
            arrive(mob, player);
            return;
        }
        // Its chunks are held for a while: it loads, and is brought once it is in the world.
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(entry.pos()), CALL_CHUNKS);
        called.put(id, level.getServer().overworld().getGameTime() + CALL_TICKS);
    }

    private static boolean takePheromone(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.RALLY_PHEROMONE.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private void arrive(Mob mob, ServerPlayer player) {
        Companions.command(mob, Companions.get(mob), CompanionCommand.FOLLOW);
        CompanionTravel.bring(mob, player, 0);
        player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.call.came", mob.getDisplayName()).withStyle(ChatFormatting.GREEN));
    }

    /** Brings a player's called ants that have loaded; one that never turns up is gone, and its pheromone comes back. */
    void answerCalls(ServerPlayer player) {
        if (called.isEmpty()) {
            return;
        }
        long now = player.level().getServer().overworld().getGameTime();
        for (UUID id : List.copyOf(called.keySet())) {
            Entry entry = entries.get(id);
            if (entry == null || !entry.owner().equals(player.getUUID())) {
                continue;
            }
            Mob mob = CompanionIndex.byId(id);
            if (mob != null && mob.isAlive()) {
                called.remove(id);
                arrive(mob, player);
            } else if (called.get(id) < now) {
                forget(id);
                if (!player.hasInfiniteMaterials()) {
                    Companions.give(player, new ItemStack(ModItems.RALLY_PHEROMONE.get()));
                }
                player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.call.lost").withStyle(ChatFormatting.RED));
            }
        }
    }

    /** A player who left is not waiting for anyone: their calls end, and each one's pheromone comes back. */
    void dropCalls(ServerPlayer player) {
        for (UUID id : List.copyOf(called.keySet())) {
            Entry entry = entries.get(id);
            if (entry != null && !entry.owner().equals(player.getUUID())) {
                continue;
            }
            called.remove(id);
            if (entry != null && !player.hasInfiniteMaterials()) {
                Companions.give(player, new ItemStack(ModItems.RALLY_PHEROMONE.get()));
            }
        }
    }
}
