package com.antcolonies.gametest;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.breeding.Hybrid;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.expedition.Expedition;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.TraitUpgrade;
import com.antcolonies.colony.species.AntSpecies;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagFile;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Checks added with the Minecraft 26.1.2 port. Codecs ignore fields they don't know, so data written in another
 * version's format can load without an error and still lose conditions, functions or settings. This decodes every
 * data file of the mod with the game's own codec, encodes it again and fails on every field that did not survive.
 */
final class PortAuditTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, AntColonies.MODID);

    /** Data folder -> the codec the game reads it with. */
    private static final Map<String, Codec<?>> CODECS = new LinkedHashMap<>();

    static {
        CODECS.put("loot_table", LootTable.DIRECT_CODEC);
        CODECS.put("advancement", Advancement.CODEC);
        CODECS.put("recipe", Recipe.CODEC);
        CODECS.put("worldgen/configured_feature", ConfiguredFeature.DIRECT_CODEC);
        CODECS.put("worldgen/placed_feature", PlacedFeature.DIRECT_CODEC);
        CODECS.put("worldgen/structure", Structure.DIRECT_CODEC);
        CODECS.put("worldgen/structure_set", StructureSet.DIRECT_CODEC);
        CODECS.put("neoforge/biome_modifier", BiomeModifier.DIRECT_CODEC);
        CODECS.put("antcolonies/species", AntSpecies.CODEC);
        CODECS.put("antcolonies/evolution", Evolution.CODEC);
        CODECS.put("antcolonies/hybrid", Hybrid.CODEC);
        CODECS.put("antcolonies/mutation", Mutation.CODEC);
        CODECS.put("antcolonies/trait_upgrade", TraitUpgrade.CODEC);
        CODECS.put("antcolonies/expedition", Expedition.CODEC);
        CODECS.put("tags", TagFile.CODEC);
    }

    static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DATA_SURVIVES = TESTS.register("data_survives_codecs",
            () -> PortAuditTests::dataSurvivesCodecs);

    private PortAuditTests() {}

    static void register(IEventBus bus) {
        TESTS.register(bus);
    }

    static void registerTests(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        event.registerTest(DATA_SURVIVES.getId(), new LoadedAreaTest(ResourceKey.create(Registries.TEST_FUNCTION, DATA_SURVIVES.getId()),
                AntColoniesGameTests.spaced(environment, 100)));
    }

    private static void dataSurvivesCodecs(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int conditional = 0;
        for (Map.Entry<String, Codec<?>> folder : CODECS.entrySet()) {
            Map<Identifier, Resource> files = server.getResourceManager().listResources(folder.getKey(), id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
                if (!file.getValue().sourcePackId().equals("mod/" + AntColonies.MODID)) {
                    continue;
                }
                JsonElement json;
                try (Reader reader = file.getValue().openAsReader()) {
                    json = JsonParser.parseReader(reader);
                } catch (Exception e) {
                    problems.add(file.getKey() + ": unreadable: " + e.getMessage());
                    continue;
                }
                boolean hasConditions = json.isJsonObject() && json.getAsJsonObject().has("neoforge:conditions");
                if (hasConditions) {
                    json = json.deepCopy();
                    json.getAsJsonObject().remove("neoforge:conditions");
                }
                String result = roundTrip(folder.getValue(), ops, json);
                if (result == null) {
                    checked++;
                } else if (hasConditions && result.startsWith("decode")) {
                    // Data for another mod (neoforge:conditions): its items may not exist here.
                    conditional++;
                } else {
                    problems.add(file.getKey() + ": " + result);
                }
            }
        }
        AntColonies.LOGGER.info("Port audit: {} data files survive their codecs unchanged, {} conditional files skipped", checked, conditional);
        problems.forEach(p -> AntColonies.LOGGER.error("Port audit: {}", p));
        helper.assertTrue(checked > 400, "Only " + checked + " data files were checked");
        helper.assertTrue(problems.isEmpty(), problems.size() + " data files lose data in their codecs, first: " + (problems.isEmpty() ? "" : problems.getFirst()));
        helper.succeed();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String roundTrip(Codec codec, RegistryOps<JsonElement> ops, JsonElement json) {
        DataResult<?> decoded = codec.parse(ops, json);
        if (decoded.error().isPresent()) {
            return "decode failed: " + ((DataResult.Error<?>) decoded.error().get()).message();
        }
        DataResult<JsonElement> encoded = codec.encodeStart(ops, decoded.getOrThrow());
        if (encoded.error().isPresent()) {
            return "encode failed: " + ((DataResult.Error<?>) encoded.error().get()).message();
        }
        JsonElement baseline = encoded.getOrThrow();
        // Re-reads the (temporarily changed) input: empty if it no longer decodes.
        java.util.function.Supplier<java.util.Optional<JsonElement>> reread = () -> codec.parse(ops, json).flatMap(v -> codec.encodeStart(ops, v)).result();
        List<String> lost = new ArrayList<>();
        compare("", json, baseline, lost, new Probe(reread, baseline));
        return lost.isEmpty() ? null : "lost " + String.join(", ", lost);
    }

    /** Lists what is in {@code in} but missing or different in {@code out} (formatting differences aside). */
    private static void compare(String path, JsonElement in, JsonElement out, List<String> lost, Probe probe) {
        if (in.isJsonArray() && !out.isJsonArray() && in.getAsJsonArray().size() == 1) {
            compare(path, in.getAsJsonArray().get(0), out, lost, probe);
            return;
        }
        if (!in.isJsonArray() && out.isJsonArray() && out.getAsJsonArray().size() == 1) {
            compare(path, in, out.getAsJsonArray().get(0), lost, probe);
            return;
        }
        if (in.isJsonObject()) {
            if (!out.isJsonObject()) {
                // A state or item written in full that the codec writes back as a plain id.
                if (out.isJsonPrimitive() && in.getAsJsonObject().size() == 1 && in.getAsJsonObject().has("id")) {
                    compare(path, in.getAsJsonObject().get("id"), out, lost, probe);
                    return;
                }
                lost.add(path + " (object became " + out + ")");
                return;
            }
            JsonObject o = out.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : in.getAsJsonObject().entrySet()) {
                if (!o.has(e.getKey())) {
                    // Left out on writing: fine if it holds the default value, lost if the codec never reads it.
                    if (!probe.reads(in.getAsJsonObject(), e.getKey())) {
                        lost.add(path + "/" + e.getKey() + " (never read)");
                    }
                } else {
                    compare(path + "/" + e.getKey(), e.getValue(), o.get(e.getKey()), lost, probe);
                }
            }
            return;
        }
        if (in.isJsonArray()) {
            if (!out.isJsonArray() || out.getAsJsonArray().size() != in.getAsJsonArray().size()) {
                lost.add(path + " (list changed)");
                return;
            }
            for (int i = 0; i < in.getAsJsonArray().size(); i++) {
                compare(path + "[" + i + "]", in.getAsJsonArray().get(i), out.getAsJsonArray().get(i), lost, probe);
            }
            return;
        }
        if (in.isJsonPrimitive() && out.isJsonPrimitive()) {
            var a = in.getAsJsonPrimitive();
            var b = out.getAsJsonPrimitive();
            if (a.isNumber() && b.isNumber() ? Math.abs(a.getAsDouble() - b.getAsDouble()) > 1e-6 : !id(a.getAsString()).equals(id(b.getAsString()))) {
                lost.add(path + " (" + a + " became " + b + ")");
            }
            return;
        }
        if (!in.equals(out)) {
            lost.add(path + " (" + in + " became " + out + ")");
        }
    }

    /**
     * Tells a field the codec reads (and left out only because it holds the default) from one it never reads: changes
     * the field's value in the input and reads the file again. A field that is read either changes the result or makes
     * the file fail to decode; an ignored one changes nothing.
     */
    private record Probe(java.util.function.Supplier<java.util.Optional<JsonElement>> reread, JsonElement baseline) {
        boolean reads(JsonObject parent, String key) {
            JsonElement original = parent.get(key);
            List<JsonElement> changes = new ArrayList<>();
            if (original.isJsonPrimitive() && original.getAsJsonPrimitive().isBoolean()) {
                changes.add(new com.google.gson.JsonPrimitive(!original.getAsBoolean()));
            } else if (original.isJsonPrimitive() && original.getAsJsonPrimitive().isNumber()) {
                double d = original.getAsDouble();
                changes.add(new com.google.gson.JsonPrimitive(d == Math.rint(d) ? (Number) ((long) d + 1) : (Number) (d * 0.5 + 0.37)));
                changes.add(new com.google.gson.JsonPrimitive(d == Math.rint(d) ? (Number) ((long) d - 1) : (Number) (d * 0.25)));
            } else {
                changes.add(new com.google.gson.JsonPrimitive("antcolonies:port_audit_probe"));
                changes.add(new com.google.gson.JsonPrimitive(7));
                changes.add(new JsonObject());
            }
            try {
                for (JsonElement change : changes) {
                    parent.add(key, change);
                    java.util.Optional<JsonElement> result = reread.get();
                    if (result.isEmpty() || !result.get().equals(baseline)) {
                        return true;
                    }
                }
                return false;
            } finally {
                parent.add(key, original);
            }
        }
    }

    /** "stone" and "minecraft:stone" are the same id. */
    private static String id(String s) {
        boolean tag = s.startsWith("#");
        String bare = tag ? s.substring(1) : s;
        if (bare.matches("[a-z0-9_./-]+") && !bare.contains(":")) {
            bare = "minecraft:" + bare;
        }
        return (tag ? "#" : "") + bare;
    }
}
