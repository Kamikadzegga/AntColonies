package com.antcolonies.companion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** Every companion that is loaded on the server, by its own id: nobody has to search the world for them. */
public final class CompanionIndex {
    private static final Map<UUID, Mob> LOADED = new HashMap<>();

    /** Adds a companion; a second one with the same id (a copied tube or entity) becomes an individual of its own. */
    static void add(Mob mob) {
        CompanionData data = Companions.get(mob);
        if (data == null) {
            return;
        }
        Mob other = LOADED.get(data.id());
        if (other != null && other != mob && !other.isRemoved() && !other.getUUID().equals(mob.getUUID())) {
            data = data.withId(UUID.randomUUID());
            Companions.set(mob, data);
        }
        LOADED.put(data.id(), mob);
    }

    static void remove(Mob mob) {
        CompanionData data = Companions.get(mob);
        if (data != null) {
            LOADED.remove(data.id(), mob);
        }
    }

    static void clear() {
        LOADED.clear();
    }

    public static @Nullable Mob byId(UUID id) {
        return LOADED.get(id);
    }

    /** A player's loaded companions. */
    public static List<Mob> of(UUID owner) {
        return LOADED.values().stream().filter(mob -> {
            CompanionData data = Companions.get(mob);
            return !mob.isRemoved() && data != null && data.ownedBy(owner);
        }).toList();
    }

    private CompanionIndex() {}
}
