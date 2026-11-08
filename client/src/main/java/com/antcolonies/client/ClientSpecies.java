package com.antcolonies.client;

import java.util.List;

import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The client's copy of the species data (synced from the server's datapacks). */
public final class ClientSpecies {
    public static @Nullable RegistryAccess registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        return minecraft.getConnection() == null ? null : minecraft.getConnection().registryAccess();
    }

    public static @Nullable AntSpecies get(@Nullable Identifier id) {
        return ModRegistries.species(registries(), id);
    }

    public static AntLook look(@Nullable Identifier id) {
        AntSpecies species = get(id);
        return species == null ? AntLook.UNKNOWN : species.look();
    }

    /** Every species, in registry order. */
    public static List<Holder.Reference<AntSpecies>> all() {
        RegistryAccess registries = registries();
        if (registries == null) {
            return List.of();
        }
        return registries.lookup(ModRegistries.SPECIES).map(lookup -> lookup.listElements().toList()).orElse(List.of());
    }

    private ClientSpecies() {}
}
