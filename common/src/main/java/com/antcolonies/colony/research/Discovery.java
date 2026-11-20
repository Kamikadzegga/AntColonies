package com.antcolonies.colony.research;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Making discoveries on the server: every one is checked, saved and sent to the clients here. */
public final class Discovery {
    /** What scanning a material did to one research entry. */
    public enum Result {
        /** Just discovered. */
        NEW,
        /** The player or their team knew it already: nothing is gained twice. */
        KNOWN,
        /** It waits for earlier research (its {@code requires}). */
        LOCKED
    }

    public record Scan(Researches.Entry entry, Result result) {
    }

    /** What the laboratory's scanner made of the item in its slot. */
    public enum Outcome {
        NONE,
        /** The laboratory has too little FE to scan. */
        NO_POWER,
        /** A material nothing is researched from. */
        NOTHING,
        NEW,
        KNOWN,
        LOCKED,
        /** Ants in a tube (or eggs, larvae, a Queen): their species was recorded just now, or was known. */
        SPECIES_NEW,
        SPECIES_KNOWN;

        public static Outcome byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    /** Scans one item for a player: ants in it are recorded (a Queen is analysed), a material is researched. */
    public static Outcome scan(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level) || stack.isEmpty()) {
            return Outcome.NONE;
        }
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        AntSample ants = stack.get(ModDataComponents.ANTS.get());
        Identifier species = queen != null ? queen.species() : ants != null ? ants.species() : stack.get(ModDataComponents.SPECIES.get());
        if (species != null) {
            boolean fresh = queen != null ? analysed(player, species) : species(player, species);
            return fresh ? Outcome.SPECIES_NEW : Outcome.SPECIES_KNOWN;
        }
        List<Scan> scans = material(level.getServer(), player.getUUID(), level.registryAccess(), stack);
        if (scans.isEmpty()) {
            return Outcome.NOTHING;
        }
        if (scans.stream().anyMatch(scan -> scan.result() == Result.NEW)) {
            return Outcome.NEW;
        }
        return scans.stream().anyMatch(scan -> scan.result() == Result.LOCKED) ? Outcome.LOCKED : Outcome.KNOWN;
    }

    /** What a player can use right now, on the server. Empty for anything that is not a player on a server. */
    public static Knowledge view(Player player) {
        return player.level() instanceof ServerLevel level ? ResearchStore.of(level.getServer()).view(level.getServer(), player.getUUID())
                : Knowledge.EMPTY;
    }

    /** Records one discovery and tells the clients; false when it was already known. */
    public static boolean record(MinecraftServer server, UUID player, Knowledge.Kind kind, Identifier id) {
        if (!ResearchStore.of(server).discover(server, player, kind, id)) {
            return false;
        }
        ResearchSync.announce(server, player, new KnowledgePayload.News(kind, id));
        return true;
    }

    /** A species met in person (scanned, caught, kept or made): false when it was known, or off the server. */
    public static boolean species(Player player, Identifier species) {
        return player.level() instanceof ServerLevel level && record(level.getServer(), player.getUUID(), Knowledge.Kind.SPECIES, species);
    }

    /** A Queen of this species was analysed: her species counts as met as well. */
    public static boolean analysed(Player player, Identifier species) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean met = species(player, species);
        return record(level.getServer(), player.getUUID(), Knowledge.Kind.ANALYSIS, species) || met;
    }

    /** Scans a material: every research entry it belongs to is discovered, unless known or still locked. */
    public static List<Scan> material(MinecraftServer server, UUID player, HolderLookup.Provider registries, ItemStack stack) {
        List<Scan> scans = new ArrayList<>();
        for (Researches.Entry entry : Researches.scannedFrom(registries, stack)) {
            Knowledge knowledge = ResearchStore.of(server).view(server, player);
            if (knowledge.knowsResearch(entry.id())) {
                scans.add(new Scan(entry, Result.KNOWN));
            } else if (!Researches.missing(entry.research(), knowledge).isEmpty()) {
                scans.add(new Scan(entry, Result.LOCKED));
            } else {
                record(server, player, Knowledge.Kind.RESEARCH, entry.id());
                scans.add(new Scan(entry, Result.NEW));
            }
        }
        return scans;
    }

    private Discovery() {}
}
