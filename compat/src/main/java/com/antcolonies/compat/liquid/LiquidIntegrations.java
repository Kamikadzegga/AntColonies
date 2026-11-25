package com.antcolonies.compat.liquid;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

/**
 * The fluids of other mods that Liquid Ants can make. They are offered by id only, so nothing here needs the other mod
 * to load. The list is the file {@code antcolonies.liquid_kinds.json} in the jar, which tools/DataGen.java writes from
 * the same table as the species: a kind and its data files cannot drift apart.
 */
public final class LiquidIntegrations {
    private static final String FILE = "/antcolonies.liquid_kinds.json";

    public static void offerAll() {
        try (InputStream stream = LiquidIntegrations.class.getResourceAsStream(FILE)) {
            if (stream == null) {
                AntColonies.LOGGER.error("{} is missing from the jar: only Water and Lava Ants", FILE);
                return;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                for (JsonElement entry : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("kinds")) {
                    LiquidKinds.offer(kind(entry.getAsJsonObject()));
                }
            }
        } catch (IOException | RuntimeException e) {
            AntColonies.LOGGER.error("Couldn't read {}: other mods' Liquid Ants are left out", FILE, e);
        }
    }

    private static LiquidKind kind(JsonObject json) {
        List<LiquidKind.Source> sources = new ArrayList<>();
        for (JsonElement source : json.getAsJsonArray("sources")) {
            JsonObject object = source.getAsJsonObject();
            sources.add(new LiquidKind.Source(object.get("mod").getAsString(), Identifier.parse(object.get("fluid").getAsString())));
        }
        return new LiquidKind(json.get("name").getAsString(), sources);
    }

    private LiquidIntegrations() {}
}
