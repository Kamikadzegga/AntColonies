package com.antcolonies.client;

import java.util.Optional;

import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.research.KnowledgePayload;
import com.antcolonies.colony.research.Research;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.content.item.Tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The client's copy of what its player knows: their own research and their team's, as the server last sent them. */
public final class ClientResearch {
    /** Whose a piece of knowledge is. */
    public enum Owner {
        NONE,
        PERSONAL,
        TEAM
    }

    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    private static Knowledge personal = Knowledge.EMPTY;
    private static Optional<Knowledge> team = Optional.empty();
    private static Knowledge view = Knowledge.EMPTY;

    public static void accept(KnowledgePayload payload) {
        personal = payload.personal();
        team = payload.team();
        view = team.map(personal::union).orElse(personal);
        for (KnowledgePayload.News news : payload.news()) {
            announce(news);
        }
    }

    public static void clear() {
        personal = Knowledge.EMPTY;
        team = Optional.empty();
        view = Knowledge.EMPTY;
    }

    /** Everything the player can use: their own knowledge and their team's. */
    public static Knowledge view() {
        return view;
    }

    public static boolean inTeam() {
        return team.isPresent();
    }

    public static Owner owner(Knowledge.Kind kind, Identifier id) {
        if (team.isPresent() && team.get().knows(kind, id)) {
            return Owner.TEAM;
        }
        return personal.knows(kind, id) ? Owner.PERSONAL : Owner.NONE;
    }

    /** An entry's hint: its own line from the lang file, or its tier's general hint when a pack gave it none. */
    public static Component hint(Identifier id, Research research) {
        String key = Research.hintKey(id);
        return Language.getInstance().has(key) ? Component.translatable(key)
                : Component.translatable("research.antcolonies.hint." + research.tier().getSerializedName());
    }

    private static void announce(KnowledgePayload.News news) {
        Minecraft minecraft = Minecraft.getInstance();
        Component what = switch (news.kind()) {
            case RESEARCH -> {
                Researches.Entry entry = Researches.byId(ClientSpecies.registries(), news.id());
                yield entry == null ? Component.literal(news.id().toString()) : entry.research().title();
            }
            case SPECIES, ANALYSIS -> Tooltips.species(news.id());
        };
        String title = switch (news.kind()) {
            case RESEARCH -> "toast.antcolonies.research";
            case SPECIES -> "toast.antcolonies.species";
            case ANALYSIS -> "toast.antcolonies.analysis";
        };
        SystemToast.add(minecraft.getToastManager(), TOAST, Component.translatable(inTeam() ? title + ".team" : title), what);
    }

    private ClientResearch() {}
}
