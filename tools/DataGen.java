import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes every JSON file of Ant Colonies: species, evolutions, hybrids, mutations, Genetic Laboratory costs, food
 * values, recipes, loot tables, tags, world generation, blockstates, models and the English language file.
 * Run from the project root with JDK 25: {@code java tools/DataGen.java}. Edit this file, not the JSON it writes.
 */
public class DataGen {
    static final String NS = "antcolonies";
    static final Path RES = Path.of("common/src/generated/resources");
    static final Path DATA = RES.resolve("data/" + NS);
    static final Path ASSETS = RES.resolve("assets/" + NS);
    static final Map<String, String> LANG = new TreeMap<>();
    static int written;

    // ------------------------------------------------------------------ JSON

    /** A tiny JSON writer: maps, lists, strings, numbers, booleans and raw text. */
    static String json(Object value, int indent) {
        String pad = "  ".repeat(indent);
        String inner = "  ".repeat(indent + 1);
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder out = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(inner).append(quote(entry.getKey().toString())).append(": ").append(json(entry.getValue(), indent + 1));
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            return out.append(pad).append("}").toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            boolean simple = list.stream().allMatch(e -> e instanceof Number || e instanceof String s && s.length() < 12);
            if (simple && list.size() <= 8) {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    out.append(json(list.get(i), indent + 1)).append(i + 1 < list.size() ? ", " : "");
                }
                return out.append("]").toString();
            }
            StringBuilder out = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                out.append(inner).append(json(list.get(i), indent + 1)).append(i + 1 < list.size() ? ",\n" : "\n");
            }
            return out.append(pad).append("]").toString();
        }
        if (value instanceof String s) {
            return quote(s);
        }
        if (value instanceof Double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf(d.longValue()) + ".0" : String.valueOf(d);
        }
        return String.valueOf(value);
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A map from alternating keys and values, in order. */
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                map.put((String) kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    static List<Object> arr(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json(value, 0) + "\n", StandardCharsets.UTF_8);
            written++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String mc(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    static String ac(String id) {
        return NS + ":" + id;
    }

    static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ Species

    record Out(String item, int count, double weight, int stage, boolean special, String processed, int ratio, boolean tag) {
        static Out of(String item, double weight) {
            return new Out(item, 1, weight, 1, false, null, 9, false);
        }

        Out atStage(int s) {
            return new Out(item, count, weight, s, special, processed, ratio, tag);
        }

        Out asSpecial() {
            return new Out(item, count, weight, stage, true, processed, ratio, tag);
        }

        Out withCount(int c) {
            return new Out(item, c, weight, stage, special, processed, ratio, tag);
        }

        Out processed(String into, int r) {
            return new Out(item, count, weight, stage, special, into, r, tag);
        }

        Out asTag() {
            return new Out(item, count, weight, stage, special, processed, ratio, true);
        }

        Map<String, Object> json() {
            return obj(tag ? "tag" : "item", tag ? item : mc(item), "count", count == 1 ? null : count, "weight", weight == Math.rint(weight) ? (Object) (int) weight : (Object) weight,
                    "min_stage", stage == 1 ? null : stage, "special", special ? true : null,
                    "processed", processed == null ? null : mc(processed), "ratio", processed == null || ratio == 9 ? null : ratio);
        }
    }

    record Species(String id, String name, int tier, String category, String climate, String spawn, String body, String gaster, String accent,
                   String pattern, String crystal, boolean glow, double size, double consumption, String special, double specialPerEgg,
                   double rate, List<Out> outputs, double farmFood, List<Out> harvest, double strength, List<String[]> targets,
                   List<String[]> rearing, int rearingTime, double eggs, double growth, String signature,
                   String extraction, String processing, String efficiency, String description) {}

    static final List<Species> SPECIES = new ArrayList<>();

    /** Species ids that only load when one of these mods is present (see {@link #conditions}). */
    static final Map<String, List<String>> NEEDS_MODS = new LinkedHashMap<>();

    /** A "neoforge:conditions" list: loaded only when one of the mods is present. */
    static List<Object> conditions(List<String> mods) {
        List<Object> loaded = new ArrayList<>();
        for (String mod : mods) {
            loaded.add(obj("type", "neoforge:mod_loaded", "modid", mod));
        }
        return loaded.size() == 1 ? loaded : arr(obj("type", "neoforge:or", "values", loaded));
    }

    /**
     * A metal species for another mod's ore: nuggets, raw ore and (rarely) ingots through the c: tags, and some stone, so
     * it works with whichever mod provides the metal.
     */
    static void compat(String id, String name, int tier, String climate, String body, String gaster, String accent, String pattern, String crystal,
                       boolean glow, String metal, List<String> mods, String description) {
        // Common metals at the built-in metal ants' pace; tier 3 (uranium, which gates nuclear tech) much slower.
        double rate = tier == 1 ? 0.6 : tier == 2 ? 0.45 : 0.12;
        // Uranium's raw ore from the start: MachinaOpus has no uranium nuggets or ingots, so stage 1 would find only stone.
        int rawStage = tier == 3 ? 1 : 2;
        SPECIES.add(new Species(id, name, tier, "mineral", climate, null, body, gaster, accent, pattern, crystal, glow, 1.05, 1.2 + tier * 0.15,
                "mineral", 0.3 + tier * 0.15, rate,
                List.of(Out.of("c:nuggets/" + metal, 40).asTag(), Out.of("c:raw_materials/" + metal, 10).atStage(rawStage).asTag(), Out.of("cobblestone", 15),
                        Out.of("c:ingots/" + metal, 2).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {ac("mineral_ant_food"), "4"}),
                3600 + tier * 1200, 0.9, 0.9, null, title(metal) + " Extraction", "Metal Processing", title(metal) + " Efficiency", description));
        NEEDS_MODS.put(id, mods);
    }

    static void species() {
        List<String[]> baseRearing = List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"#minecraft:flowers", "4"});
        SPECIES.add(new Species("common", "Common Ant", 0, "base", "temperate", "spawns/common", "3A2A20", "2B1F18", "6B4A30", "banded", null, false,
                1.0, 1.0, "", 0, 1.0,
                List.of(Out.of("dirt", 30), Out.of("wheat_seeds", 22), Out.of("stick", 16), Out.of("bone_meal", 8), Out.of("flint", 5).atStage(2),
                        Out.of("clay_ball", 5).atStage(2), Out.of("string", 3).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), baseRearing, 2400, 1.0, 1.0, "wheat_seeds",
                "Forager Extraction", "Seed Sorting", "Thrifty Foraging",
                "The starting species of plains and forests. Brings back soil, seeds, sticks and odds and ends."));
        SPECIES.add(new Species("wood", "Wood Ant", 0, "base", "temperate", "spawns/wood", "8E3B1F", "2A1C16", "A0522D", "plain", null, false,
                1.05, 1.0, "", 0, 0.9,
                List.of(Out.of("oak_log", 18), Out.of("spruce_log", 10), Out.of("birch_log", 8), Out.of("stick", 22), Out.of("oak_sapling", 6),
                        Out.of("spruce_sapling", 5), Out.of("apple", 3).atStage(2), Out.of("dark_oak_log", 4).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"#minecraft:saplings", "4"}), 2400, 1.0, 1.0,
                "oak_log", "Wood Gathering", "Timber Processing", "Sapling Production",
                "Red-chested forest builders of the taiga. Gather logs, sticks and saplings."));
        SPECIES.add(new Species("stone", "Stone Ant", 0, "base", "temperate", "spawns/stone", "5C5A58", "3E3C3B", "8A8784", "speckled", null, false,
                1.0, 1.1, "", 0, 0.9,
                List.of(Out.of("cobblestone", 30), Out.of("gravel", 14), Out.of("flint", 8), Out.of("andesite", 6), Out.of("diorite", 5), Out.of("granite", 5),
                        Out.of("stone", 10).atStage(2), Out.of("coal", 5).atStage(2).asSpecial(), Out.of("raw_copper", 2).atStage(3).asSpecial()),
                0, List.of(), 1.2, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"cobblestone", "16"}), 2400, 0.95, 1.0,
                "cobblestone", "Quarry Extraction", "Stone Sorting", "Mineral Patience",
                "Tough mountain ants that tunnel through rock. Bring stone, gravel and the odd mineral; the start of every mineral line."));
        SPECIES.add(new Species("snow", "Snow Ant", 0, "base", "cold", "spawns/snow", "CFE3F0", "9FB8CC", "FFFFFF", "speckled", null, false,
                0.95, 0.9, "", 0, 0.9,
                List.of(Out.of("snowball", 36), Out.of("ice", 10).atStage(2), Out.of("spruce_sapling", 4), Out.of("packed_ice", 4).atStage(3),
                        Out.of("blue_ice", 1).atStage(4).asSpecial()),
                0, List.of(), 0.9, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"snowball", "16"}), 2400, 1.0, 0.9,
                "snowball", "Frost Gathering", "Ice Compaction", "Cold Endurance",
                "Pale ants of the frozen north that thrive where others freeze. Gather snow and ice."));
        SPECIES.add(new Species("desert", "Desert Ant", 0, "base", "hot", "spawns/desert", "C8963E", "A86A2A", "5A3A18", "striped", null, false,
                1.0, 0.85, "", 0, 0.95,
                List.of(Out.of("sand", 34), Out.of("red_sand", 8), Out.of("sandstone", 12), Out.of("cactus", 8), Out.of("dead_bush", 6),
                        Out.of("gold_nugget", 2).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"cactus", "4"}), 2400, 1.0, 1.1,
                "sand", "Dune Extraction", "Sand Sifting", "Desert Thrift",
                "Fast, heat-loving ants of deserts and badlands. Bring sand, sandstone and cactus, and sometimes gold."));
        SPECIES.add(new Species("farmer", "Farmer Ant", 0, "farmer", "temperate", "spawns/farmer", "9A4A22", "7A3A1A", "C87A3A", "plain", null, false,
                1.0, 0.8, "", 0, 0.8,
                List.of(Out.of("oak_leaves", 12), Out.of("moss_block", 5), Out.of("bone_meal", 10)),
                1.0, List.of(Out.of("brown_mushroom", 25), Out.of("red_mushroom", 20), Out.of("wheat_seeds", 14), Out.of("sweet_berries", 14),
                        Out.of("melon_seeds", 5), Out.of("pumpkin_seeds", 5), Out.of("glow_berries", 5).atStage(3).asSpecial()),
                0.8, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"brown_mushroom", "4"}), 2400, 1.0, 1.0,
                "brown_mushroom", "Leaf Cutting", "Fungus Curing", "Garden Rotation",
                "Leafcutters that grow fungus gardens. Their Harvesting Sites feed the whole colony."));
        List<String[]> mineralRearing = List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {ac("mineral_ant_food"), "4"});
        List<String[]> crystalRearing = List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"},
                new String[] {ac("nuptial_pheromone"), "1"});
        SPECIES.add(new Species("iron", "Iron Ant", 1, "mineral", "any", null, "5E5E62", "D0D0D4", "A8A8AC", "metallic", "C8C8D0", false,
                1.05, 1.3, "mineral", 0.5, 0.6,
                List.of(Out.of("iron_nugget", 40).processed("iron_ingot", 9), Out.of("cobblestone", 18), Out.of("raw_iron", 6).atStage(2),
                        Out.of("iron_ingot", 2).atStage(3).asSpecial()),
                0, List.of(), 1.3, List.of(), mineralRearing, 3600, 0.9, 0.9, "iron_ingot",
                "Iron Extraction", "Mineral Processing", "Metal Efficiency",
                "Evolved from Stone Ants fed iron. Their armoured bodies shine like polished metal."));
        SPECIES.add(new Species("gold", "Gold Ant", 2, "mineral", "hot", null, "7A5A12", "F2C94C", "FFF0A0", "metallic", "FFD84A", false,
                1.05, 1.4, "mineral", 0.6, 0.5,
                List.of(Out.of("gold_nugget", 40).processed("gold_ingot", 9), Out.of("raw_gold", 5).atStage(2), Out.of("gold_ingot", 2).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), mineralRearing, 3600, 0.85, 0.9, "gold_ingot",
                "Gold Extraction", "Metal Processing", "Nugget Compression",
                "Gilded ants of hot lands. Their nuggets come compressed to ingots with Processing upgrades."));
        SPECIES.add(new Species("redstone", "Redstone Ant", 2, "mineral", "any", null, "4A0E0E", "C81E1E", "FF5A3A", "faceted", "FF2A1A", true,
                1.0, 1.4, "mineral", 0.6, 0.55,
                List.of(Out.of("redstone", 45), Out.of("glowstone_dust", 8).atStage(2).asSpecial(), Out.of("cobblestone", 10)),
                0, List.of(), 1.1, List.of(), mineralRearing, 3600, 0.9, 1.0, "redstone",
                "Redstone Extraction", "Redstone Processing", "Energy Efficiency",
                "Glowing red ants that hum with energy. Gather redstone and, at higher stages, glowstone."));
        SPECIES.add(new Species("lapis", "Lapis Ant", 2, "mineral", "any", null, "1A2460", "2A4AC8", "6A8AFF", "faceted", "3A5AF0", false,
                1.0, 1.4, "mineral", 0.6, 0.55,
                List.of(Out.of("lapis_lazuli", 45), Out.of("cobblestone", 10), Out.of("calcite", 4).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), mineralRearing, 3600, 0.9, 1.0, "lapis_lazuli",
                "Lapis Extraction", "Lapis Grinding", "Azure Efficiency",
                "Deep blue ants with lapis plates. Gather lapis lazuli."));
        SPECIES.add(new Species("amethyst", "Amethyst Ant", 2, "mineral", "underground", null, "3E2460", "9A5AD8", "D8B0FF", "faceted", "B07AF0", true,
                1.0, 1.4, "crystal", 0.5, 0.5,
                List.of(Out.of("amethyst_shard", 45), Out.of("calcite", 8), Out.of("smooth_basalt", 5)),
                0, List.of(), 1.0, List.of(), crystalRearing, 4800, 0.85, 0.9, "amethyst_shard",
                "Geode Extraction", "Crystal Purification", "Resonant Efficiency",
                "Living geodes that glow violet in the dark. Prefer to live underground."));
        SPECIES.add(new Species("diamond", "Diamond Ant", 3, "mineral", "underground", null, "1E3A48", "6AE0E8", "C8FFFF", "faceted", "7AF0FF", true,
                1.1, 1.7, "crystal", 1.0, 0.008,
                List.of(Out.of(ac("diamond_fragment"), 60).processed("diamond", 9), Out.of("coal", 25), Out.of("diamond", 3).atStage(3).asSpecial()),
                0, List.of(), 1.4, List.of(), crystalRearing, 6000, 0.75, 0.8, "diamond",
                "Diamond Extraction", "Crystal Purification", "Mineral Efficiency",
                "Rare crystal ants grown from Gold or Lapis lines. Bring diamond fragments; whole diamonds at stage 3."));
        SPECIES.add(new Species("emerald", "Emerald Ant", 3, "mineral", "temperate", null, "16481F", "3AD86A", "B0FFC8", "faceted", "4AF08A", true,
                1.1, 1.7, "crystal", 1.0, 0.008,
                List.of(Out.of(ac("emerald_fragment"), 60).processed("emerald", 9), Out.of("cobblestone", 20), Out.of("emerald", 3).atStage(3).asSpecial()),
                0, List.of(), 1.2, List.of(), crystalRearing, 6000, 0.75, 0.8, "emerald",
                "Emerald Extraction", "Gem Purification", "Rare Mineral Detection",
                "Gem-backed ants evolved from Diamond Ants. Bring emerald fragments."));
        SPECIES.add(new Species("netherite", "Netherite Ant", 4, "endgame", "nether", null, "1E181A", "4A3E40", "8A6A5A", "metallic", "6A4A4A", false,
                1.2, 2.6, "nether", 2.0, 0.0025,
                List.of(Out.of("quartz", 30), Out.of("gold_nugget", 30), Out.of("netherrack", 20), Out.of("netherite_scrap", 2).atStage(4).asSpecial()),
                0, List.of(), 2.0, List.of(new String[] {"minecraft:blaze", "20"}, new String[] {"minecraft:magma_cube", "20"},
                        new String[] {"minecraft:wither_skeleton", "10"}, new String[] {"minecraft:piglin", "15"}),
                List.of(new String[] {ac("royal_jelly"), "6"}, new String[] {ac("nether_ant_food"), "12"}, new String[] {ac("nuptial_pheromone"), "4"}),
                12000, 0.6, 0.7, "netherite_scrap", "Ancient Extraction", "Debris Smelting", "Infernal Efficiency",
                "The end of the line: heat-forged ants of the Nether. Very slow, very hungry, and the only ants that find netherite."));
        SPECIES.add(new Species("crystal_wood", "Crystal Wood Ant", 2, "hybrid", "temperate", null, "6A3A1A", "7AD0D8", "C8F0F0", "faceted", "9AF0FF", false,
                1.05, 1.2, "", 0, 0.6,
                List.of(Out.of("oak_log", 24), Out.of("stick", 18), Out.of(ac("diamond_fragment"), 0.05).atStage(3).asSpecial(), Out.of("amethyst_shard", 10)),
                0, List.of(), 1.1, List.of(), crystalRearing, 4800, 0.9, 1.0, "dark_oak_log",
                "Crystal Timber Extraction", "Crystal Sap Refining", "Living Wood Efficiency",
                "A hybrid of Wood and Diamond Ants: forest ants whose bark has turned to crystal."));
        SPECIES.add(new Species("frost_emerald", "Frost Emerald Ant", 3, "hybrid", "cold", null, "BFE3F0", "4AE08A", "E0FFF0", "faceted", "9AFFC8", true,
                1.05, 1.3, "crystal", 0.6, 0.35,
                List.of(Out.of("snowball", 20), Out.of("packed_ice", 14), Out.of(ac("emerald_fragment"), 0.15).asSpecial().processed("emerald", 9),
                        Out.of("blue_ice", 3).atStage(3)),
                0, List.of(), 1.0, List.of(), crystalRearing, 6000, 0.85, 0.9, "packed_ice",
                "Frost Gem Extraction", "Glacial Purification", "Hibernation Efficiency",
                "A hybrid of Snow and Emerald Ants that grows emeralds in the cold."));
        SPECIES.add(new Species("mechanical_miner", "Mechanical Miner Ant", 2, "hybrid", "any", null, "3E3E42", "A82020", "D0D0D0", "metallic", "FF4A2A", false,
                1.0, 1.3, "mineral", 0.3, 0.75,
                List.of(Out.of("cobblestone", 25), Out.of("redstone", 18), Out.of("iron_nugget", 15).processed("iron_ingot", 9), Out.of("raw_copper", 10),
                        Out.of("coal", 10), Out.of("quartz", 3).atStage(3).asSpecial(),
                        // Other mods' ores, when some mod adds them (skipped while their tags are empty).
                        Out.of("c:raw_materials/tin", 3).atStage(3).asTag(), Out.of("c:raw_materials/osmium", 3).atStage(3).asTag(),
                        Out.of("c:raw_materials/zinc", 3).atStage(3).asTag(), Out.of("c:raw_materials/lead", 3).atStage(3).asTag(),
                        Out.of("c:raw_materials/molybdenum", 2).atStage(3).asTag()),
                0, List.of(), 1.3, List.of(), mineralRearing, 4800, 0.9, 1.0, "piston",
                "Drill Extraction", "Ore Processing", "Clockwork Efficiency",
                "A hybrid of Stone and Redstone Ants: tireless miners that bring a bit of everything."));

        // --- More wild species: one per kind of biome the first six don't cover. ---
        SPECIES.add(new Species("jungle", "Jungle Ant", 0, "base", "temperate", "spawns/jungle", "4A3A12", "6B8E23", "C9A227", "banded", null, false,
                1.0, 1.0, "", 0, 0.95,
                List.of(Out.of("jungle_log", 22), Out.of("bamboo", 18), Out.of("stick", 12), Out.of("vine", 10), Out.of("jungle_sapling", 6),
                        Out.of("cocoa_beans", 8).atStage(2), Out.of("melon_slice", 8).atStage(2), Out.of("glow_berries", 3).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"cocoa_beans", "4"}), 2400, 1.05, 1.0,
                "cocoa_beans", "Canopy Extraction", "Cocoa Curing", "Rainforest Thrift",
                "Busy ants of the jungle canopy. Bring jungle wood, bamboo, vines, cocoa and melon."));
        SPECIES.add(new Species("swamp", "Swamp Ant", 0, "base", "temperate", "spawns/swamp", "3B4A2A", "5E6B3A", "8BA05A", "speckled", null, false,
                1.0, 1.0, "", 0, 0.95,
                List.of(Out.of("clay_ball", 20).processed("clay", 4), Out.of("mud", 16), Out.of("sugar_cane", 14), Out.of("lily_pad", 10),
                        Out.of("mangrove_log", 10), Out.of("vine", 8), Out.of("mangrove_propagule", 5),
                        Out.of("slime_ball", 4).atStage(3).asSpecial().processed("slime_block", 9)),
                0, List.of(), 1.0, List.of(new String[] {"minecraft:slime", "30"}, new String[] {"minecraft:witch", "10"}, new String[] {"minecraft:zombie", "25"},
                        new String[] {"minecraft:drowned", "15"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"lily_pad", "4"}), 2400, 1.0, 1.0,
                "slime_ball", "Marsh Extraction", "Clay Kneading", "Wetland Thrift",
                "Mud-coated ants of swamps and mangroves. Bring clay, mud, sugar cane and lily pads, and in time slime."));
        SPECIES.add(new Species("savanna", "Savanna Ant", 0, "base", "hot", "spawns/savanna", "8A5A2A", "B07A3A", "E0B070", "striped", null, false,
                1.1, 1.05, "", 0, 0.95,
                List.of(Out.of("acacia_log", 22), Out.of("wheat", 14), Out.of("bone", 12), Out.of("feather", 10), Out.of("acacia_sapling", 6),
                        Out.of("leather", 5).atStage(2), Out.of("rabbit_hide", 5).atStage(2), Out.of("armadillo_scute", 3).atStage(2), Out.of("rabbit_foot", 0.015).atStage(3).asSpecial()),
                0, List.of(), 1.3, List.of(new String[] {"minecraft:husk", "25"}, new String[] {"minecraft:zombie", "25"}, new String[] {"minecraft:spider", "25"},
                        new String[] {"minecraft:skeleton", "20"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"wheat", "8"}), 2400, 1.0, 1.0,
                "acacia_log", "Grassland Foraging", "Hide Tanning", "Dry Season Thrift",
                "Large, bold ants of the savanna. Strip acacia, gather grain and pick over what the herds leave: bones, feathers, hide."));
        SPECIES.add(new Species("shore", "Shore Ant", 0, "base", "temperate", "spawns/shore", "C8B890", "3A8A8A", "8AE0D0", "banded", "6ADCC8", false,
                0.95, 1.0, "", 0, 0.9,
                List.of(Out.of("sand", 24), Out.of("kelp", 20).processed("dried_kelp", 1), Out.of("seagrass", 10), Out.of("ink_sac", 8),
                        Out.of("sea_pickle", 6).atStage(2), Out.of("prismarine_shard", 8).atStage(2).processed("prismarine", 4),
                        Out.of("prismarine_crystals", 4).atStage(3), Out.of("glow_ink_sac", 2).atStage(3), Out.of("turtle_scute", 0.012).atStage(3).asSpecial(), Out.of("nautilus_shell", 0.012).atStage(4).asSpecial()),
                0, List.of(), 1.0, List.of(new String[] {"minecraft:drowned", "40"}, new String[] {"minecraft:guardian", "10"}, new String[] {"minecraft:zombie", "20"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"kelp", "8"}), 2400, 1.0, 1.0,
                "prismarine_shard", "Tideline Extraction", "Kelp Drying", "Salt Thrift",
                "Beachcombing ants that work the tideline. Bring sand, kelp and ink, then prismarine and, rarely, nautilus shells."));
        SPECIES.add(new Species("blossom", "Blossom Ant", 0, "base", "temperate", "spawns/blossom", "E8A0C0", "FFC8DC", "FFFFFF", "speckled", null, false,
                0.9, 0.9, "", 0, 0.95,
                List.of(Out.of("pink_petals", 14), Out.of("cherry_log", 14), Out.of("dandelion", 10), Out.of("poppy", 10), Out.of("cornflower", 6),
                        Out.of("allium", 5), Out.of("azure_bluet", 5), Out.of("oxeye_daisy", 5), Out.of("lily_of_the_valley", 4), Out.of("wildflowers", 6),
                        Out.of("cherry_sapling", 4), Out.of("honeycomb", 4).atStage(2).asSpecial(), Out.of("honey_bottle", 2).atStage(3).asSpecial()),
                0, List.of(), 0.8, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"#minecraft:flowers", "8"}), 2400, 1.1, 1.0,
                "honeycomb", "Nectar Gathering", "Petal Pressing", "Pollinator Thrift",
                "Pink ants of cherry groves and flower meadows. Bring flowers for every dye, petals, cherry wood and honeycomb."));
        SPECIES.add(new Species("pale", "Pale Ant", 0, "base", "temperate", "spawns/pale", "B8B8B0", "E0E0D8", "FF8A3A", "plain", null, false,
                1.0, 1.0, "", 0, 0.85,
                List.of(Out.of("pale_oak_log", 22), Out.of("pale_moss_block", 14), Out.of("pale_hanging_moss", 10), Out.of("stick", 10),
                        Out.of("pale_oak_sapling", 5), Out.of("resin_clump", 6).atStage(2).asSpecial(), Out.of("open_eyeblossom", 3).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"pale_moss_block", "4"}), 2400, 0.95, 1.0,
                "resin_clump", "Pale Wood Gathering", "Resin Tapping", "Quiet Thrift",
                "Ghost-white ants of the pale garden. Bring pale oak, pale moss and resin, and the odd eyeblossom."));
        SPECIES.add(new Species("badlands", "Badlands Ant", 0, "base", "hot", "spawns/badlands", "A0522D", "C8643A", "E0A060", "striped", null, false,
                1.0, 0.9, "", 0, 0.95,
                List.of(Out.of("red_sand", 22), Out.of("terracotta", 18), Out.of("orange_terracotta", 8), Out.of("yellow_terracotta", 6),
                        Out.of("red_terracotta", 6), Out.of("brown_terracotta", 5), Out.of("white_terracotta", 4), Out.of("light_gray_terracotta", 4),
                        Out.of("dead_bush", 4), Out.of("gold_nugget", 3).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"terracotta", "8"}), 2400, 1.0, 1.05,
                "terracotta", "Mesa Extraction", "Clay Firing", "Canyon Thrift",
                "Rust-red ants of the badlands. Bring red sand and terracotta in all the badlands colors, and a little gold."));
        // --- The Stone line goes deeper: copper, coal and deepslate. ---
        SPECIES.add(new Species("copper", "Copper Ant", 1, "mineral", "any", null, "8A4A2A", "D8803A", "4AC0A0", "metallic", "E09050", false,
                1.05, 1.3, "mineral", 0.4, 0.65,
                List.of(Out.of("copper_nugget", 40).processed("copper_ingot", 9), Out.of("raw_copper", 12).atStage(2), Out.of("cobblestone", 12),
                        Out.of("copper_ingot", 3).atStage(3).asSpecial()),
                0, List.of(), 1.2, List.of(), mineralRearing, 3600, 0.9, 0.9, "copper_ingot",
                "Copper Extraction", "Metal Processing", "Patina Efficiency",
                "Evolved from Stone Ants fed copper. Their shells go green with age, like old roofs."));
        SPECIES.add(new Species("coal", "Coal Ant", 1, "mineral", "any", null, "1A1A1A", "2E2E2E", "5A5A5A", "speckled", null, false,
                1.0, 1.2, "", 0, 0.7,
                List.of(Out.of("coal", 34).processed("coal_block", 9), Out.of("charcoal", 14), Out.of("cobblestone", 14), Out.of("flint", 6),
                        Out.of("coal_block", 2).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"coal", "16"}), 3000, 0.95, 1.0, "coal",
                "Coal Extraction", "Coal Pressing", "Slow Burn Efficiency",
                "Soot-black ants evolved from Stone Ants fed coal. A steady supply of fuel for furnaces and Generators."));
        SPECIES.add(new Species("deepslate", "Deepslate Ant", 1, "mineral", "underground", null, "2A2A30", "484850", "7A7A88", "speckled", null, false,
                1.05, 1.2, "", 0, 0.7,
                List.of(Out.of("cobbled_deepslate", 28), Out.of("tuff", 14), Out.of("deepslate", 8).atStage(2), Out.of("pointed_dripstone", 8),
                        Out.of("dripstone_block", 6), Out.of("glow_lichen", 4), Out.of("calcite", 4), Out.of("raw_iron", 3).atStage(3).asSpecial()),
                0, List.of(), 1.3, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"cobbled_deepslate", "16"}), 3000, 0.9, 0.9,
                "cobbled_deepslate", "Deep Extraction", "Slate Splitting", "Darkness Efficiency",
                "Stone Ants that went down. Live best underground; bring deepslate, tuff, dripstone and glow lichen."));
        // --- The Nether line: Fire Ants from Desert Ants, Soul Ants from Fire Ants. ---
        List<String[]> netherRearing = List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {ac("nether_ant_food"), "4"});
        SPECIES.add(new Species("fire", "Fire Ant", 2, "mineral", "hot", null, "8A1A0A", "E04A1A", "FFB040", "striped", "FF7A1A", true,
                1.0, 1.5, "nether", 0.4, 0.5,
                List.of(Out.of("netherrack", 20), Out.of("blaze_powder", 10), Out.of("magma_cream", 8), Out.of("glowstone_dust", 8), Out.of("quartz", 8),
                        Out.of("nether_wart", 8).atStage(2).processed("nether_wart_block", 9), Out.of("magma_block", 4), Out.of("blaze_rod", 0.1).atStage(3).asSpecial()),
                0, List.of(), 1.5, List.of(new String[] {"minecraft:blaze", "25"}, new String[] {"minecraft:magma_cube", "25"},
                        new String[] {"minecraft:zombified_piglin", "20"}, new String[] {"minecraft:husk", "15"}),
                netherRearing, 4800, 0.9, 1.0, "blaze_powder",
                "Ember Extraction", "Blaze Refining", "Heat Efficiency",
                "Desert Ants fed blaze powder. They glow like coals and bring the Nether's fuels: blaze powder, magma, wart and quartz."));
        // Hunters: their workers hunt mobs at a Hunting Ground (the mobs' loot, and food from the catch). Army Ants live wild;
        // the others evolve from wild species fed a mob drop.
        SPECIES.add(new Species("army", "Army Ant", 0, "hunter", "any", "spawns/army", "5A2A12", "3A1A0A", "C0602A", "banded", null, false,
                1.1, 1.2, "", 0, 0.6,
                List.of(Out.of("rotten_flesh", 20), Out.of("bone", 20), Out.of("string", 10), Out.of("arrow", 5).atStage(2)),
                0, List.of(), 1.2, List.of(new String[] {"minecraft:zombie", "30"}, new String[] {"minecraft:skeleton", "25"},
                        new String[] {"minecraft:spider", "25"}, new String[] {"minecraft:husk", "10"}, new String[] {"minecraft:zombie_villager", "5"}),
                baseRearing, 2400, 1.2, 1.1, "rotten_flesh",
                "Raid Extraction", "Bone Grinding", "Swarm Efficiency",
                "Nomad hunters of plains and savannas that live anywhere. At a Hunting Ground they swarm zombies, skeletons and spiders, and feed on the catch."));
        SPECIES.add(new Species("bullet", "Bullet Ant", 1, "hunter", "hot", null, "2A1A10", "8A3A10", "E0A040", "striped", null, false,
                1.2, 1.3, "", 0, 0.5,
                List.of(Out.of("gunpowder", 10), Out.of("redstone", 6), Out.of("glowstone_dust", 4).atStage(2)),
                0, List.of(), 1.6, List.of(new String[] {"minecraft:creeper", "40"}, new String[] {"minecraft:witch", "15"},
                        new String[] {"minecraft:spider", "15"}, new String[] {"minecraft:cave_spider", "10"}, new String[] {"minecraft:phantom", "10"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"gunpowder", "4"}), 3600, 1.0, 1.0, "gunpowder",
                "Powder Extraction", "Powder Packing", "Sting Efficiency",
                "Jungle Ants fed gunpowder. Their sting fells creepers and witches: gunpowder, redstone and glowstone come home."));
        SPECIES.add(new Species("trap_jaw", "Trap-Jaw Ant", 1, "hunter", "temperate", null, "3A3020", "6A5A2A", "B0A050", "speckled", null, false,
                1.1, 1.2, "", 0, 0.5,
                List.of(Out.of("slime_ball", 10), Out.of("kelp", 10), Out.of("prismarine_shard", 3).atStage(3)),
                0, List.of(), 1.4, List.of(new String[] {"minecraft:slime", "35"}, new String[] {"minecraft:drowned", "30"},
                        new String[] {"minecraft:zombie", "15"}, new String[] {"minecraft:guardian", "5"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"slime_ball", "4"}), 3600, 1.0, 1.0, "slime_ball",
                "Snap Extraction", "Slime Pressing", "Ambush Efficiency",
                "Swamp Ants fed slime balls. Their jaws snap shut faster than anything: slimes, drowned and the odd guardian."));
        SPECIES.add(new Species("inferno", "Inferno Ant", 2, "hunter", "nether", null, "6A1A0A", "C03010", "FFA020", "striped", null, true,
                1.2, 1.4, "nether", 0.4, 0.4,
                List.of(Out.of("blaze_powder", 10), Out.of("magma_cream", 8), Out.of("coal", 10)),
                0, List.of(), 2.0, List.of(new String[] {"minecraft:blaze", "35"}, new String[] {"minecraft:magma_cube", "35"},
                        new String[] {"minecraft:wither_skeleton", "10"}, new String[] {"minecraft:ghast", "10"}),
                netherRearing, 4800, 0.9, 1.0, "blaze_powder",
                "Cinder Extraction", "Ember Pressing", "Furnace Efficiency",
                "Fire Ants fed blaze powder. They hunt the Nether's fire: blaze rods, magma cream, ghast tears and wither skulls."));
        SPECIES.add(new Species("soul", "Soul Ant", 3, "mineral", "nether", null, "3A2A20", "5A4030", "4AE0E8", "banded", "6AF0FF", true,
                1.05, 1.8, "nether", 0.8, 0.3,
                List.of(Out.of("soul_sand", 22), Out.of("soul_soil", 16), Out.of("bone", 14), Out.of("basalt", 10), Out.of("blackstone", 10), Out.of("quartz", 6),
                        Out.of("ghast_tear", 0.05).atStage(3).asSpecial(), Out.of("gilded_blackstone", 0.05).atStage(4).asSpecial()),
                0, List.of(), 1.6, List.of(new String[] {"minecraft:skeleton", "25"}, new String[] {"minecraft:ghast", "1"},
                        new String[] {"minecraft:wither_skeleton", "10"}, new String[] {"minecraft:magma_cube", "15"}),
                netherRearing, 6000, 0.8, 0.85, "soul_sand",
                "Soul Extraction", "Soul Fire Refining", "Restless Efficiency",
                "Fire Ants touched by soul fire. Live best in the Nether; bring soul sand, basalt and blackstone, and ghast tears."));
        SPECIES.add(new Species("crimson", "Crimson Ant", 2, "hybrid", "hot", null, "6A1020", "A02030", "2AA8A0", "plain", null, false,
                1.0, 1.3, "nether", 0.3, 0.6,
                List.of(Out.of("crimson_stem", 14), Out.of("warped_stem", 14), Out.of("weeping_vines", 6), Out.of("twisting_vines", 6),
                        Out.of("shroomlight", 3).atStage(3).asSpecial()),
                1.2, List.of(Out.of("crimson_fungus", 22), Out.of("warped_fungus", 22), Out.of("nether_wart", 18).processed("nether_wart_block", 9),
                        Out.of("shroomlight", 4).atStage(3).asSpecial()),
                1.0, List.of(), netherRearing, 4800, 1.0, 1.0, "nether_wart",
                "Fungal Extraction", "Wart Curing", "Spore Efficiency",
                "A hybrid of Farmer and Fire Ants that grows Nether fungus. Tends Harvesting Sites for food, wart and shroomlight."));
        SPECIES.add(new Species("obsidian", "Obsidian Ant", 3, "hybrid", "hot", null, "14101E", "2A1E40", "8A4AE0", "faceted", "A060FF", true,
                1.1, 1.6, "mineral", 0.6, 0.3,
                List.of(Out.of("obsidian", 20), Out.of("basalt", 12), Out.of("magma_block", 10), Out.of("blackstone", 10), Out.of("cobblestone", 10),
                        Out.of("crying_obsidian", 0.13).atStage(3).asSpecial()),
                0, List.of(), 1.8, List.of(), mineralRearing, 6000, 0.8, 0.85, "obsidian",
                "Lava Rock Extraction", "Glass Forging", "Cooling Efficiency",
                "A hybrid of Stone and Fire Ants: where lava meets stone. Bring obsidian, basalt and magma; crying obsidian at stage 3."));
        // --- The End: Ender Ants from Amethyst Ants. ---
        SPECIES.add(new Species("ender", "Ender Ant", 4, "endgame", "any", null, "0E0E14", "2A1A3A", "C870FF", "faceted", "D090FF", true,
                1.15, 2.2, "crystal", 1.5, 0.03,
                List.of(Out.of("end_stone", 30), Out.of("chorus_fruit", 18), Out.of("purpur_block", 6).atStage(2), Out.of("ender_pearl", 8).atStage(2),
                        Out.of("shulker_shell", 0.2).atStage(4).asSpecial()),
                0, List.of(), 1.8, List.of(new String[] {"minecraft:enderman", "40"}, new String[] {"minecraft:endermite", "20"},
                        new String[] {"minecraft:shulker", "1"}),
                List.of(new String[] {ac("royal_jelly"), "6"}, new String[] {ac("crystal_ant_food"), "12"}, new String[] {ac("nuptial_pheromone"), "4"},
                        new String[] {"ender_pearl", "4"}), 12000, 0.6, 0.7, "ender_pearl",
                "Void Extraction", "Chorus Refining", "Phase Efficiency",
                "Amethyst Ants changed by eyes of ender. Black with violet eyes; bring end stone, chorus and ender pearls, and very rarely shulker shells."));

        // --- More hybrids: crosses between the wild and evolved lines, each for things no single species brings. ---
        SPECIES.add(new Species("honeypot", "Honeypot Ant", 2, "hybrid", "temperate", null, "6A3A12", "F0B030", "FFE08A", "banded", null, false,
                1.0, 1.0, "", 0, 0.7,
                List.of(Out.of("honeycomb", 18).processed("honeycomb_block", 4), Out.of("sugar", 16), Out.of("sweet_berries", 10), Out.of("pink_petals", 8),
                        Out.of("honey_bottle", 6).atStage(2).processed("honey_block", 4), Out.of("honey_block", 1).atStage(4).asSpecial()),
                1.6, List.of(Out.of("honey_bottle", 20), Out.of("sugar", 20), Out.of("sweet_berries", 16), Out.of("honeycomb", 10).atStage(2).asSpecial()),
                0.8, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {"honey_bottle", "2"}), 3600, 1.05, 1.0,
                "honey_bottle", "Nectar Extraction", "Honey Thickening", "Sweet Thrift",
                "A hybrid of Blossom and Farmer Ants. Some workers swell into living honey jars: honey, sugar and comb. Tends Harvesting Sites."));
        SPECIES.add(new Species("harvester", "Harvester Ant", 2, "hybrid", "hot", null, "7A2A12", "A8461E", "D89A50", "plain", null, false,
                1.1, 1.0, "", 0, 0.7,
                List.of(Out.of("wheat_seeds", 16), Out.of("beetroot_seeds", 10), Out.of("pumpkin_seeds", 6), Out.of("melon_seeds", 6),
                        Out.of("wheat", 10), Out.of("dead_bush", 4)),
                1.4, List.of(Out.of("wheat", 18), Out.of("carrot", 14), Out.of("potato", 14), Out.of("beetroot", 12), Out.of("pumpkin", 6), Out.of("melon", 5),
                        Out.of("sugar_cane", 8), Out.of("poisonous_potato", 2), Out.of("golden_carrot", 1).atStage(4).asSpecial()),
                1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {"wheat", "16"}), 3600, 1.0, 1.0,
                "wheat", "Granary Foraging", "Seed Husking", "Granary Thrift",
                "A hybrid of Farmer and Savanna Ants, after the real seed-storing harvester ants. Tend Harvesting Sites for crops of every kind."));
        SPECIES.add(new Species("reef", "Reef Ant", 2, "hybrid", "temperate", null, "1E4A6A", "E05A8A", "FFD040", "speckled", "60D8E8", false,
                0.95, 1.1, "", 0, 0.55,
                List.of(Out.of("tube_coral_block", 6), Out.of("brain_coral_block", 6), Out.of("bubble_coral_block", 6), Out.of("fire_coral_block", 6),
                        Out.of("horn_coral_block", 6), Out.of("tube_coral_fan", 4), Out.of("brain_coral_fan", 4), Out.of("bubble_coral_fan", 4),
                        Out.of("fire_coral_fan", 4), Out.of("horn_coral_fan", 4), Out.of("sea_pickle", 8), Out.of("kelp", 10),
                        Out.of("tube_coral", 3).atStage(2), Out.of("brain_coral", 3).atStage(2), Out.of("bubble_coral", 3).atStage(2),
                        Out.of("fire_coral", 3).atStage(2), Out.of("horn_coral", 3).atStage(2), Out.of("turtle_scute", 0.02).atStage(4).asSpecial()),
                0, List.of(), 1.0, List.of(new String[] {"minecraft:drowned", "40"}, new String[] {"minecraft:guardian", "15"}),
                List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {"sea_pickle", "4"}), 3600, 1.0, 1.0,
                "tube_coral_block", "Reef Extraction", "Coral Curing", "Tidepool Thrift",
                "A hybrid of Shore and Jungle Ants, as bright as a coral reef. Bring living coral blocks and fans of all five colors."));
        SPECIES.add(new Species("abyssal", "Abyssal Ant", 3, "hybrid", "any", null, "0E2A3A", "2A6A7A", "8AF0E0", "faceted", "7AF0E0", true,
                1.05, 1.5, "crystal", 0.5, 0.05,
                List.of(Out.of("prismarine_shard", 20).processed("prismarine", 4), Out.of("prismarine_crystals", 12), Out.of("dark_prismarine", 8),
                        Out.of("prismarine", 8), Out.of("sea_lantern", 3).atStage(2), Out.of("wet_sponge", 0.1).atStage(3).asSpecial(),
                        Out.of("sponge", 0.05).atStage(4).asSpecial()),
                0, List.of(), 1.4, List.of(new String[] {"minecraft:guardian", "40"}, new String[] {"minecraft:drowned", "30"}),
                crystalRearing, 6000, 0.8, 0.85, "prismarine_crystals",
                "Deep Water Extraction", "Lantern Polishing", "Pressure Efficiency",
                "A hybrid of Shore and Lapis Ants that dives for the ocean monuments' treasures: prismarine, sea lanterns and sponges."));
        SPECIES.add(new Species("sculk", "Sculk Ant", 3, "hybrid", "underground", null, "0A1A22", "0E3A48", "2AE0E8", "speckled", "30E8F0", true,
                1.05, 1.6, "nether", 0.5, 0.06,
                List.of(Out.of("sculk", 26), Out.of("sculk_vein", 16), Out.of("bone", 12), Out.of("cobbled_deepslate", 10), Out.of("sculk_sensor", 3).atStage(2),
                        Out.of("sculk_catalyst", 0.05).atStage(3).asSpecial(), Out.of("echo_shard", 0.04).atStage(4).asSpecial()),
                0, List.of(), 1.5, List.of(), netherRearing, 6000, 0.8, 0.85, "sculk",
                "Deep Dark Extraction", "Sculk Harvesting", "Silent Efficiency",
                "A hybrid of Deepslate and Soul Ants from the deep dark. Bring sculk, sensors and catalysts, and the rare echo shard."));
        SPECIES.add(new Species("excavator", "Excavator Ant", 2, "hybrid", "underground", null, "8A7A5A", "B8A070", "E0D0A0", "banded", null, false,
                1.0, 1.2, "mineral", 0.3, 0.15,
                List.of(Out.of("gravel", 20), Out.of("bone", 14), Out.of("flint", 10), Out.of("bone_block", 6), Out.of("clay_ball", 8),
                        Out.of("torchflower_seeds", 3).atStage(2), Out.of("pitcher_pod", 3).atStage(2), Out.of("angler_pottery_sherd", 0.03).atStage(2).asSpecial(),
                        Out.of("archer_pottery_sherd", 0.03).atStage(2).asSpecial(), Out.of("prize_pottery_sherd", 0.03).atStage(3).asSpecial(), Out.of("skull_pottery_sherd", 0.03).atStage(3).asSpecial(),
                        Out.of("miner_pottery_sherd", 0.03).atStage(3).asSpecial(), Out.of("sniffer_egg", 0.006).atStage(4).asSpecial()),
                0, List.of(), 1.2, List.of(), mineralRearing, 4800, 0.9, 0.9, "bone_block",
                "Dig Site Extraction", "Find Cleaning", "Careful Digging",
                "A hybrid of Desert and Deepslate Ants that digs like an archaeologist: bones, pottery sherds, ancient seeds, even sniffer eggs."));

        // --- The Nether's and the End's own wild ants (nests there only), and the boss-gated endgame species. ---
        SPECIES.add(new Species("warped", "Warped Ant", 0, "base", "nether", "spawns/warped", "1A4A4A", "2AA59A", "5AE8D8", "speckled", null, false,
                1.0, 1.0, "", 0, 0.9,
                List.of(Out.of("netherrack", 26), Out.of("warped_fungus", 10), Out.of("crimson_fungus", 8), Out.of("warped_roots", 6),
                        Out.of("nether_wart", 6).atStage(2), Out.of("warped_wart_block", 4).atStage(2), Out.of("quartz", 3).atStage(3),
                        Out.of("shroomlight", 1).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"warped_fungus", "4"}), 2400, 1.0, 1.0,
                "warped_fungus", "Fungus Gathering", "Wart Curing", "Nether Thrift",
                "Teal ants of the Nether's fungus forests, at home in its heat. Bring netherrack, fungi and nether wart; fed blaze "
                        + "powder in the Nether they become Fire Ants."));
        SPECIES.add(new Species("chorus", "Chorus Ant", 1, "base", "any", "spawns/chorus", "3A2A4A", "9A6AB8", "D8B8F0", "faceted", "C8A0F0", false,
                1.0, 1.1, "", 0, 0.75,
                List.of(Out.of("end_stone", 26), Out.of("chorus_fruit", 14), Out.of("chorus_flower", 2).atStage(2), Out.of("purpur_block", 4).atStage(2),
                        Out.of("ender_pearl", 1).atStage(3).asSpecial(), Out.of("shulker_shell", 0.03).atStage(4).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"chorus_fruit", "4"}), 3600, 0.95, 1.0,
                "chorus_fruit", "Chorus Gathering", "Fruit Popping", "Island Thrift",
                "Pale violet ants of the End's outer islands. Bring end stone, chorus fruit and purpur; fed eyes of ender in the End "
                        + "they become Ender Ants."));
        SPECIES.add(new Species("dragon", "Dragon Ant", 4, "endgame", "any", null, "1A0A2A", "4A1A6A", "C060F0", "faceted", "D080FF", true,
                1.15, 1.6, "crystal", 0.6, 0.08,
                List.of(Out.of("end_stone", 20), Out.of("ender_pearl", 10), Out.of("purpur_block", 6), Out.of("shulker_shell", 2).atStage(2),
                        Out.of("dragon_breath", 0.6).atStage(3).asSpecial(), Out.of("dragon_head", 0.02).atStage(4).asSpecial()),
                0, List.of(), 1.6, List.of(), List.of(new String[] {ac("royal_jelly"), "4"}, new String[] {ac("crystal_ant_food"), "8"},
                        new String[] {ac("void_chitin"), "2"}), 9600, 0.7, 0.8, "dragon_breath",
                "Draconic Extraction", "Breath Bottling", "Void Efficiency",
                "Ender Ants fed the dragon's breath in the End. Black and violet, they glow faintly and bring the End's treasures: "
                        + "pearls, purpur, shulker shells and the dragon's breath itself."));
        SPECIES.add(new Species("wither", "Wither Ant", 4, "endgame", "nether", null, "141414", "2A2A2A", "6A6A6A", "plain", null, false,
                1.15, 1.6, "nether", 0.6, 0.08,
                List.of(Out.of("soul_sand", 18), Out.of("coal", 14), Out.of("bone", 12), Out.of("blackstone", 10), Out.of("wither_rose", 2).atStage(2),
                        Out.of("wither_skeleton_skull", 0.4).atStage(3).asSpecial(), Out.of("nether_star", 0.01).atStage(4).asSpecial()),
                0, List.of(), 1.8, List.of(), List.of(new String[] {ac("royal_jelly"), "4"}, new String[] {ac("nether_ant_food"), "8"},
                        new String[] {ac("infernal_chitin"), "2"}), 9600, 0.7, 0.8, "wither_skeleton_skull",
                "Withering Extraction", "Skull Polishing", "Decay Efficiency",
                "Soul Ants fed a Nether Star in the Nether. Ash-black and grim, they bring soul sand, coal and bones, wither roses "
                        + "and skulls, and very rarely a Nether Star of their own."));

        // --- Species for other mods' metals and crystals. Each loads only when a mod that adds its material is installed
        // (a "neoforge:conditions" block), finds through common c: item tags, and has no signature item (so no species
        // upgrades), since the exact item ids differ between mods. ---
        compat("osmium", "Osmium Ant", 2, "any", "5A6E80", "A8C0D0", "D8E8F0", "metallic", "B0D0E8", false, "osmium", List.of("mekanism"),
                "Stone Ants fed osmium. Bring osmium for Mekanism's machines.");
        compat("tin", "Tin Ant", 1, "any", "6A6A70", "C8C8D0", "E8E8F0", "metallic", null, false, "tin", List.of("mekanism", "thermal", "machinaopus"),
                "Copper Ants fed tin: together they make bronze. Bring tin.");
        compat("lead", "Lead Ant", 2, "underground", "2E2E3A", "5A5A78", "8A8AA8", "metallic", null, false, "lead",
                List.of("mekanism", "thermal", "immersiveengineering", "machinaopus"), "Heavy, slow Iron Ants fed lead. Bring lead.");
        compat("silver", "Silver Ant", 2, "any", "6A7078", "D8E0E8", "FFFFFF", "metallic", "E0E8F0", false, "silver",
                List.of("thermal", "immersiveengineering", "machinaopus"), "Lead Ants fed silver; they shine like mirrors. Bring silver.");
        compat("nickel", "Nickel Ant", 2, "any", "6A6A50", "C8C8A0", "E8E8C8", "metallic", null, false, "nickel",
                List.of("thermal", "immersiveengineering"), "Iron Ants fed nickel. Bring nickel.");
        compat("aluminum", "Aluminum Ant", 1, "any", "8A8A90", "D8DCE0", "F0F4F8", "metallic", null, false, "aluminum",
                List.of("immersiveengineering"), "Light, quick Iron Ants fed aluminum. Bring aluminum.");
        compat("zinc", "Zinc Ant", 1, "any", "5A6A60", "A8C0B0", "D0E8D8", "metallic", null, false, "zinc", List.of("create"),
                "Copper Ants fed zinc, ready for Create's brass. Bring zinc.");
        compat("uranium", "Uranium Ant", 3, "underground", "2A3A1A", "5AE02A", "C8FF8A", "faceted", "7AFF3A", true, "uranium",
                List.of("mekanism", "immersiveengineering", "machinaopus"), "Lead Ants fed raw uranium. They glow green in the dark; bring uranium.");
        // Crystals rather than metals: their own outputs.
        SPECIES.add(new Species("fluorite", "Fluorite Ant", 2, "mineral", "underground", null, "3A2A60", "8A6AE8", "D0C0FF", "faceted", "A890FF", true,
                1.0, 1.4, "crystal", 0.5, 0.45,
                List.of(Out.of("c:gems/fluorite", 40).asTag(), Out.of("c:dusts/fluorite", 12).asTag(), Out.of("cobblestone", 12),
                        Out.of("calcite", 6).atStage(2)),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"}), 4800, 0.85, 0.9, null,
                "Fluorite Extraction", "Crystal Purification", "Lattice Efficiency",
                "Amethyst Ants fed fluorite. Bring fluorite for Mekanism's or MachinaOpus's chemistry."));
        NEEDS_MODS.put("fluorite", List.of("mekanism", "machinaopus"));
        SPECIES.add(new Species("certus", "Certus Ant", 2, "mineral", "underground", null, "2A3A5A", "8AB8F0", "E0F0FF", "faceted", "A0D0FF", true,
                1.0, 1.4, "crystal", 0.5, 0.45,
                List.of(Out.of("c:gems/certus_quartz", 40).asTag(), Out.of("c:dusts/certus_quartz", 12).asTag(), Out.of("quartz", 10),
                        Out.of("c:gems/fluix", 0.5).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"}), 4800, 0.85, 0.9, null,
                "Certus Extraction", "Crystal Charging", "Resonance Efficiency",
                "Amethyst Ants fed certus quartz. Bring certus quartz for Applied Energistics, and fluix at stage 3."));
        NEEDS_MODS.put("certus", List.of("ae2"));

        // MachinaOpus's own materials. Only MachinaOpus adds them, so these name its items directly and have signature
        // items (and with them species upgrades).
        SPECIES.add(new Species("molybdenum", "Molybdenum Ant", 2, "mineral", "underground", null, "3A4048", "8A98A8", "C0CCD8", "metallic", "A8B8D0", false,
                1.05, 1.5, "mineral", 0.6, 0.45,
                List.of(Out.of("machinaopus:molybdenum_nugget", 40).processed("machinaopus:molybdenum_ingot", 9), Out.of("cobblestone", 15),
                        Out.of("machinaopus:raw_molybdenum", 10).atStage(2), Out.of("machinaopus:molybdenum_ingot", 2).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), mineralRearing, 6000, 0.9, 0.9, "machinaopus:molybdenum_ingot",
                "Molybdenum Extraction", "Metal Processing", "Molybdenum Efficiency",
                "Iron Ants fed molybdenum, the metal that hardens MachinaOpus's steel. Bring molybdenum."));
        NEEDS_MODS.put("molybdenum", List.of("machinaopus"));
        SPECIES.add(new Species("sulfur", "Sulfur Ant", 2, "mineral", "hot", null, "5A4A0A", "E0C820", "FFF080", "speckled", "F0E040", false,
                1.0, 1.4, "nether", 0.4, 0.5,
                List.of(Out.of("machinaopus:sulfur", 36), Out.of("netherrack", 14), Out.of("magma_block", 6), Out.of("basalt", 6),
                        Out.of("gunpowder", 4).atStage(2), Out.of("machinaopus:petroleum_coke", 0.5).atStage(3).asSpecial()),
                0, List.of(), 1.3, List.of(), netherRearing, 4800, 0.9, 1.0, "machinaopus:sulfur",
                "Brimstone Extraction", "Sulfur Refining", "Vent Efficiency",
                "Fire Ants fed sulfur that live around hot vents. Bring sulfur for MachinaOpus's acids without refining oil."));
        NEEDS_MODS.put("sulfur", List.of("machinaopus"));
        SPECIES.add(new Species("rubber", "Rubber Ant", 1, "farmer", "temperate", null, "4A3020", "2A1C14", "E8E0C8", "banded", null, false,
                1.0, 0.85, "", 0, 0.8,
                List.of(Out.of("machinaopus:rubber_log", 18), Out.of("stick", 14), Out.of("machinaopus:rubber_leaves", 8), Out.of("machinaopus:rubber_sapling", 5)),
                0.6, List.of(Out.of("machinaopus:rubber_sheet", 24), Out.of("slime_ball", 4).atStage(2)),
                0.9, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"machinaopus:rubber_sapling", "4"}), 3600, 1.0, 1.0,
                "machinaopus:rubber_sheet", "Sap Tapping", "Latex Curing", "Tapper Efficiency",
                "Wood Ants fed rubber that tap rubber trees. Bring rubber logs and saplings; at a Harvesting Site they bring rubber for MachinaOpus's cables."));
        NEEDS_MODS.put("rubber", List.of("machinaopus"));

        for (Species s : SPECIES) {
            List<Object> outputs = new ArrayList<>();
            s.outputs().forEach(o -> outputs.add(o.json()));
            List<Object> harvest = new ArrayList<>();
            s.harvest().forEach(o -> harvest.add(o.json()));
            List<Object> targets = new ArrayList<>();
            s.targets().forEach(t -> targets.add(obj("entity", t[0], "weight", Integer.parseInt(t[1]))));
            List<Object> cost = new ArrayList<>();
            s.rearing().forEach(c -> cost.add(obj("ingredient", c[0].startsWith("#") ? c[0] : mc(c[0]), "count", Integer.parseInt(c[1]))));
            Map<String, Object> look = obj("body", "#" + s.body(), "gaster", "#" + s.gaster(), "accent", "#" + s.accent(), "pattern", s.pattern(),
                    "crystal", s.crystal() == null ? null : "#" + s.crystal(), "glow", s.glow() ? true : null, "size", s.size() == 1.0 ? null : s.size());
            Map<String, Object> json = obj("tier", s.tier(), "category", s.category(), "look", look, "climate", s.climate(),
                    "spawn_biomes", s.spawn() == null ? null : ac(s.spawn()),
                    "diet", obj("consumption", s.consumption(), "special_food", s.special().isEmpty() ? null : s.special(),
                            "special_per_egg", s.specialPerEgg() == 0 ? null : s.specialPerEgg()),
                    "production", obj("rate", s.rate(), "outputs", outputs),
                    "farming", s.farmFood() > 0 ? obj("food", s.farmFood(), "harvest", harvest) : null,
                    "combat", obj("strength", s.strength(), "targets", targets.isEmpty() ? null : targets),
                    "rearing", obj("cost", cost, "time", s.rearingTime()),
                    "breeding", obj("eggs", s.eggs(), "growth", s.growth()),
                    "signature_item", s.signature() == null ? null : s.signature().contains(":") ? s.signature() : mc(s.signature()));
            if (NEEDS_MODS.containsKey(s.id())) {
                json.put("neoforge:conditions", conditions(NEEDS_MODS.get(s.id())));
            }
            write(DATA.resolve(NS + "/species/" + s.id() + ".json"), json);
            LANG.put("species." + NS + "." + s.id(), s.name());
            LANG.put("species." + NS + "." + s.id() + ".desc", s.description());
            LANG.put("upgrade." + NS + "." + s.id() + ".extraction", s.extraction());
            LANG.put("upgrade." + NS + "." + s.id() + ".processing", s.processing());
            LANG.put("upgrade." + NS + "." + s.id() + ".efficiency", s.efficiency());
        }
    }

    // ------------------------------------------------------------------ Evolutions, hybrids, mutations

    // Evolutions that happen only in a Terrarium in this dimension (the colony can live anywhere afterwards).
    static final String NETHER = "minecraft:the_nether";
    static final String END = "minecraft:the_end";

    static void evolutions() {
        Object[][] list = {
                {"common_to_farmer", "common", "farmer", "brown_mushroom", 0.15, 12, 1, 0},
                {"stone_to_iron", "stone", "iron", "iron_ingot", 0.08, 24, 2, 200},
                {"iron_to_gold", "iron", "gold", "gold_ingot", 0.07, 24, 2, 400},
                {"desert_to_gold", "desert", "gold", "gold_block", 0.05, 24, 2, 400},
                {"iron_to_redstone", "iron", "redstone", "redstone_block", 0.07, 24, 2, 400},
                {"stone_to_lapis", "stone", "lapis", "lapis_block", 0.06, 24, 2, 300},
                {"stone_to_amethyst", "stone", "amethyst", "amethyst_block", 0.06, 24, 2, 300},
                {"gold_to_diamond", "gold", "diamond", "diamond", 0.04, 32, 3, 1500},
                {"lapis_to_diamond", "lapis", "diamond", "diamond", 0.04, 32, 3, 1500},
                {"diamond_to_emerald", "diamond", "emerald", "emerald", 0.04, 32, 3, 2000},
                {"diamond_to_netherite", "diamond", "netherite", "netherite_scrap", 0.02, 48, 4, 5000, NETHER},
                {"stone_to_copper", "stone", "copper", "copper_ingot", 0.08, 24, 2, 200},
                {"stone_to_coal", "stone", "coal", "coal_block", 0.08, 24, 2, 200},
                {"stone_to_deepslate", "stone", "deepslate", "cobbled_deepslate", 0.1, 16, 2, 150},
                {"desert_to_fire", "desert", "fire", "blaze_powder", 0.06, 24, 2, 300, NETHER},
                {"jungle_to_bullet", "jungle", "bullet", "gunpowder", 0.06, 24, 2, 300},
                {"swamp_to_trap_jaw", "swamp", "trap_jaw", "slime_ball", 0.06, 24, 2, 300},
                {"fire_to_inferno", "fire", "inferno", "blaze_powder", 0.05, 32, 3, 1000, NETHER},
                {"fire_to_soul", "fire", "soul", "ghast_tear", 0.05, 32, 3, 1000, NETHER},
                {"amethyst_to_ender", "amethyst", "ender", "ender_eye", 0.03, 40, 4, 3000, END},
                // The Nether's and the End's own wild ants, and the two boss-gated endgame species.
                {"warped_to_fire", "warped", "fire", "blaze_powder", 0.08, 24, 2, 300, NETHER},
                {"chorus_to_ender", "chorus", "ender", "ender_eye", 0.05, 32, 3, 2000, END},
                {"ender_to_dragon", "ender", "dragon", "dragon_breath", 0.05, 24, 4, 4000, END},
                {"soul_to_wither", "soul", "wither", "nether_star", 0.5, 3, 4, 4000, NETHER},
                // Into other mods' metals and crystals (these load only with those mods).
                {"stone_to_osmium", "stone", "osmium", "#c:ingots/osmium", 0.07, 24, 2, 300},
                {"copper_to_tin", "copper", "tin", "#c:ingots/tin", 0.08, 24, 2, 300},
                {"copper_to_zinc", "copper", "zinc", "#c:ingots/zinc", 0.08, 24, 2, 300},
                {"iron_to_lead", "iron", "lead", "#c:ingots/lead", 0.07, 24, 2, 400},
                {"iron_to_nickel", "iron", "nickel", "#c:ingots/nickel", 0.07, 24, 2, 400},
                {"iron_to_aluminum", "iron", "aluminum", "#c:ingots/aluminum", 0.08, 24, 2, 300},
                {"lead_to_silver", "lead", "silver", "#c:ingots/silver", 0.06, 32, 2, 600},
                // Raw uranium, not ingots: MachinaOpus has none (it makes uranium into fuel, not metal).
                {"lead_to_uranium", "lead", "uranium", "#c:raw_materials/uranium", 0.04, 32, 3, 1500},
                {"amethyst_to_fluorite", "amethyst", "fluorite", "#c:gems/fluorite", 0.06, 24, 2, 400},
                {"amethyst_to_certus", "amethyst", "certus", "#c:gems/certus_quartz", 0.06, 24, 2, 400},
                {"iron_to_molybdenum", "iron", "molybdenum", "#c:ingots/molybdenum", 0.07, 24, 2, 400},
                {"fire_to_sulfur", "fire", "sulfur", "#c:dusts/sulfur", 0.06, 24, 2, 400},
                {"wood_to_rubber", "wood", "rubber", "#c:rubber", 0.08, 24, 1, 100},
        };
        for (Object[] e : list) {
            Map<String, Object> json = obj("from", ac((String) e[1]), "to", ac((String) e[2]),
                    "item", mc((String) e[3]), "chance", e[4], "guaranteed_after", e[5], "min_stage", e[6], "min_population", ((Integer) e[7]).longValue());
            if (e.length > 8) {
                json.put("dimension", e[8]);
            }
            if (NEEDS_MODS.containsKey((String) e[2])) {
                json.put("neoforge:conditions", conditions(NEEDS_MODS.get((String) e[2])));
            }
            write(DATA.resolve(NS + "/evolution/" + e[0] + ".json"), json);
        }
    }

    static void hybrids() {
        write(DATA.resolve(NS + "/hybrid/crystal_wood.json"), obj("first", ac("wood"), "second", ac("diamond"), "result", ac("crystal_wood"),
                "chance", 0.25, "min_average_level", 3.0));
        write(DATA.resolve(NS + "/hybrid/frost_emerald.json"), obj("first", ac("snow"), "second", ac("emerald"), "result", ac("frost_emerald"),
                "chance", 0.2, "min_average_level", 4.0));
        write(DATA.resolve(NS + "/hybrid/mechanical_miner.json"), obj("first", ac("stone"), "second", ac("redstone"), "result", ac("mechanical_miner"),
                "chance", 0.3, "min_average_level", 2.5));
        write(DATA.resolve(NS + "/hybrid/obsidian.json"), obj("first", ac("stone"), "second", ac("fire"), "result", ac("obsidian"),
                "chance", 0.25, "min_average_level", 3.0));
        write(DATA.resolve(NS + "/hybrid/crimson.json"), obj("first", ac("farmer"), "second", ac("fire"), "result", ac("crimson"),
                "chance", 0.25, "min_average_level", 3.0));
        Object[][] more = {
                {"honeypot", "blossom", "farmer", 0.3, 2.5}, {"harvester", "farmer", "savanna", 0.3, 2.5}, {"reef", "shore", "jungle", 0.25, 3.0},
                {"abyssal", "shore", "lapis", 0.2, 4.0}, {"sculk", "deepslate", "soul", 0.2, 4.0}, {"excavator", "desert", "deepslate", 0.25, 3.0},
        };
        for (Object[] h : more) {
            write(DATA.resolve(NS + "/hybrid/" + h[0] + ".json"), obj("first", ac((String) h[1]), "second", ac((String) h[2]), "result", ac((String) h[0]),
                    "chance", h[3], "min_average_level", h[4]));
        }
    }

    // ------------------------------------------------------------------ Advancements

    /** One advancement: id, parent (null for the root), icon item, frame, title, description, its one criterion. */
    static void advancement(String id, String parent, String icon, String frame, String title, String description, Map<String, Object> criterion) {
        Map<String, Object> display = obj("icon", obj("id", icon.contains(":") ? icon : ac(icon)), "title", obj("translate", "advancements." + NS + "." + id + ".title"),
                "description", obj("translate", "advancements." + NS + "." + id + ".description"), "frame", frame.equals("task") ? null : frame,
                "background", parent == null ? "minecraft:gui/advancements/backgrounds/husbandry" : null,
                "show_toast", parent == null ? false : null, "announce_to_chat", parent == null ? false : null);
        write(DATA.resolve("advancement/" + id + ".json"), obj("parent", parent == null ? null : ac(parent), "criteria", obj(id, criterion),
                "display", display, "requirements", arr(arr(id))));
        LANG.put("advancements." + NS + "." + id + ".title", title);
        LANG.put("advancements." + NS + "." + id + ".description", description);
    }

    static Map<String, Object> hasItem(String... items) {
        List<Object> list = new ArrayList<>();
        for (String item : items) {
            list.add(item.contains(":") ? item : ac(item));
        }
        return obj("trigger", "minecraft:inventory_changed", "conditions", obj("items", arr(obj("items", list.size() == 1 ? list.getFirst() : list))));
    }

    /** The mod's colony trigger: a nearby (or opened) Terrarium whose colony matches these conditions. */
    static Map<String, Object> colony(Object... conditions) {
        return obj("trigger", ac("colony"), "conditions", obj(conditions));
    }

    static void advancements() {
        // Jade: tooltip lines and the names of the toggles in its plugin config.
        LANG.put("jade." + NS + ".food", "Food %s%%");
        LANG.put("config.jade.plugin_" + NS + ".terrarium", "Terrarium colony");
        LANG.put("config.jade.plugin_" + NS + ".structure", "Colony chamber ants and output");
        LANG.put("config.jade.plugin_" + NS + ".wild_ant", "Wild ant species");
        LANG.put("config.jade.plugin_" + NS + ".wild_nest", "Wild nest species");
        LANG.put("jade." + NS + ".catch", "Catch it with Tweezers (and an empty Test Tube)");
        LANG.put("jade." + NS + ".nest", "Tweezers take workers out; now and then a Queen");
        LANG.put("jade." + NS + ".home_nest", "Home nest: %s ants in, %s out working");
        LANG.put("jade." + NS + ".home_nest.stored", "Harvest stored: %s");
        LANG.put("tooltip." + NS + ".species.climate", "Climate: %s");
        LANG.put("tooltip." + NS + ".species.finds", "Finds: %s");
        LANG.put("tooltip." + NS + ".species.grows", "Grows food and %s");
        LANG.put("tooltip." + NS + ".species.more", " and %s more");
        LANG.put("tooltip." + NS + ".species.hunts", "Hunts: %s");
        LANG.put("tooltip." + NS + ".species.evolves", "Evolves into %s: feed %s from stage %s");
        LANG.put("tooltip." + NS + ".species.evolves_in", "Only in a Terrarium in %s");
        LANG.put("jei." + NS + ".dimension", "Only in %s");
        LANG.put("dimension.minecraft.overworld", "the Overworld");
        LANG.put("dimension.minecraft.the_nether", "the Nether");
        LANG.put("dimension.minecraft.the_end", "the End");
        LANG.put("tooltip." + NS + ".queen.best", "Best traits: %s");
        LANG.put("config.jade.plugin_" + NS + ".machine", "Generator and Genetic Laboratory");
        advancement("root", null, "terrarium", "task", "Ant Colonies", "Tiny workers, big colonies", hasItem("tweezers", "terrarium", "ant_test_tube"));
        advancement("catch_ants", "root", "ant_test_tube", "task", "Tiny Catch", "Catch wild ants with Tweezers and an empty Test Tube",
                hasItem("ant_test_tube"));
        advancement("catch_queen", "catch_ants", "queen_test_tube", "task", "Long Live the Queen", "Catch or raise a Queen", hasItem("queen_test_tube"));
        advancement("first_colony", "catch_queen", "terrarium", "task", "Home Sweet Home", "Start a colony: a Terrarium with a Queen",
                colony("has_queen", true));
        advancement("stage_2", "first_colony", structureItem("nest_chamber"), "task", "Growing Family",
                "Reach colony stage 2 (100 adults)", colony("stage", obj("min", 2)));
        advancement("stage_4", "stage_2", structureItem("foraging_site"), "goal", "Metropolis", "Reach colony stage 4", colony("stage", obj("min", 4)));
        advancement("evolution", "first_colony", "evolution_catalyst", "task", "Evolution", "Evolve a colony into a mineral species",
                colony("category", "mineral"));
        advancement("hybrid", "evolution", "genetic_sample", "goal", "Hybrid Vigor", "Raise a hybrid colony from a Queen Chamber cross",
                colony("category", "hybrid"));
        advancement("generator", "root", "generator", "task", "Power Up", "Make a Generator to power the Genetic Laboratory", hasItem("generator"));
        advancement("lab", "generator", "genetic_laboratory", "task", "Genetic Engineer", "Make a Genetic Laboratory",
                hasItem("genetic_laboratory"));
        advancement("perfect_trait", "lab", "genetic_analyzer", "challenge", "Perfection", "Own a colony whose Queen has a trait at level 10",
                colony("best_trait", obj("min", 10)));
        advancement("netherite", "evolution", "minecraft:netherite_scrap", "challenge", "Forged in the Deep", "Raise a Netherite Ant colony",
                colony("species", ac("netherite")));
        advancement("ender", "evolution", "minecraft:ender_pearl", "challenge", "Void Walkers", "Raise an Ender Ant colony", colony("species", ac("ender")));
        // The path through the dimensions: the Chitin ladder, colonies in the Nether and the End, their own ants, the
        // boss-gated endgame species and tier IV upgrades.
        advancement("hardened_chitin", "first_colony", "hardened_chitin", "task", "Case Hardened", "Bake Chitin Plates into Hardened Chitin",
                hasItem("hardened_chitin"));
        advancement("nether_colony", "hardened_chitin", "minecraft:netherrack", "task", "Hot Property", "Keep a colony in a Terrarium in the Nether",
                colonyIn("minecraft:the_nether"));
        advancement("warped_colony", "nether_colony", "warped_ant_nest", "task", "Fungal Empire", "Raise a Warped Ant colony",
                colony("species", ac("warped")));
        advancement("infernal_chitin", "nether_colony", "infernal_chitin", "task", "Trial by Fire", "Temper Hardened Chitin into Infernal Chitin",
                hasItem("infernal_chitin"));
        advancement("wither_ant", "infernal_chitin", "minecraft:wither_skeleton_skull", "challenge", "Withering Heights",
                "Raise a Wither Ant colony: Soul Ants fed a Nether Star in the Nether", colony("species", ac("wither")));
        advancement("end_colony", "infernal_chitin", "minecraft:end_stone", "task", "The End of the Line", "Keep a colony in a Terrarium in the End",
                colonyIn("minecraft:the_end"));
        advancement("chorus_colony", "end_colony", "minecraft:chorus_fruit", "task", "Chorus Line", "Raise a Chorus Ant colony",
                colony("species", ac("chorus")));
        advancement("void_chitin", "end_colony", "void_chitin", "task", "Into the Void", "Steep Infernal Chitin into Void Chitin",
                hasItem("void_chitin"));
        advancement("dragon_ant", "void_chitin", "minecraft:dragon_head", "challenge", "Here Be Dragons",
                "Raise a Dragon Ant colony: Ender Ants fed the dragon's breath in the End", colony("species", ac("dragon")));
        List<String> tierFour = new ArrayList<>();
        for (String type : UPGRADE_TYPES) {
            tierFour.add(ac(type + "_upgrade_4"));
        }
        advancement("tier_four", "void_chitin", "speed_upgrade_4", "goal", "Overengineered", "Make a tier IV upgrade",
                obj("trigger", "minecraft:inventory_changed", "conditions", obj("items", arr(obj("items", tierFour)))));
    }

    /** A colony whose Terrarium the player is near, in this dimension. */
    static Map<String, Object> colonyIn(String dimension) {
        return obj("trigger", ac("colony"), "conditions", obj("player", obj("type", "minecraft:entity_properties", "entity", "this",
                "predicate", obj("minecraft:location", obj("dimension", dimension)))));
    }

    static String structureItem(String kind) {
        return ac(kind);
    }

    /** Common c: tags, so other mods' recipes take the fragments the way they take nuggets. */
    static void commonTags() {
        write(RES.resolve("data/c/tags/item/nuggets/diamond.json"), obj("values", arr(ac("diamond_fragment"))));
        write(RES.resolve("data/c/tags/item/nuggets/emerald.json"), obj("values", arr(ac("emerald_fragment"))));
        write(RES.resolve("data/c/tags/item/nuggets.json"), obj("values", arr("#c:nuggets/diamond", "#c:nuggets/emerald")));
        LANG.put("tag.item.c.nuggets.diamond", "Diamond Nuggets");
        LANG.put("tag.item.c.nuggets.emerald", "Emerald Nuggets");
    }

    static void mutations() {
        Object[][] list = {
                // id, name, effects, catalyst, count, weight, negative, rare, categories
                {"crystal_genetics", "Crystal Genetics", obj("production", 1.15, "special", 1.25), "diamond", 4, 6, false, false, List.of("mineral", "hybrid", "endgame")},
                {"efficient_metabolism", "Efficient Metabolism", obj("food", 0.85), "emerald", 4, 8, false, false, List.of()},
                {"energized", "Energized", obj("growth", 1.2, "eggs", 1.05), "redstone", 16, 8, false, false, List.of()},
                {"heat_resistance", "Heat Resistance", obj("heat_resistant", true), "blaze_powder", 4, 6, false, false, List.of()},
                {"cold_resistance", "Cold Resistance", obj("cold_resistant", true), "packed_ice", 8, 6, false, false, List.of()},
                {"golden_touch", "Golden Touch", obj("gathering", 1.15, "production", 1.05), "gold_ingot", 8, 6, false, false, List.of()},
                {"iron_jaws", "Iron Jaws", obj("soldiers", 1.25), "iron_block", 1, 6, false, false, List.of()},
                {"green_thumb", "Green Thumb", obj("farming", 1.2), "bone_block", 4, 6, false, false, List.of("farmer", "base", "hybrid")},
                {"royal_vigor", "Royal Vigor", obj("eggs", 1.25, "growth", 1.1), null, 0, 3, false, true, List.of()},
                {"sluggish", "Sluggish", obj("production", 0.9), null, 0, 6, true, false, List.of()},
                {"gluttony", "Gluttony", obj("food", 1.15), null, 0, 6, true, false, List.of()},
                {"fragile", "Fragile", obj("soldiers", 0.85), null, 0, 4, true, false, List.of()},
                {"slow_brood", "Slow Brood", obj("growth", 0.85), null, 0, 4, true, false, List.of()},
                // Trade-offs and specialists, each with its own catalyst.
                {"industrious", "Industrious", obj("production", 1.2, "food", 1.1), "redstone_block", 2, 6, false, false, List.of()},
                {"keen_senses", "Keen Senses", obj("gathering", 1.25), "spyglass", 1, 6, false, false, List.of()},
                {"fertile", "Fertile", obj("eggs", 1.3, "food", 1.05), "golden_carrot", 8, 6, false, false, List.of()},
                {"rapid_growth", "Rapid Growth", obj("growth", 1.3, "soldiers", 0.9), "glow_berries", 16, 6, false, false, List.of()},
                {"armored_carapace", "Armored Carapace", obj("soldiers", 1.35, "eggs", 0.9), "armadillo_scute", 4, 5, false, false, List.of()},
                {"lucky_strike", "Lucky Strike", obj("special", 1.4, "production", 0.95), "rabbit_foot", 1, 4, false, false, List.of()},
                {"deep_roots", "Deep Roots", obj("farming", 1.3), "moss_block", 16, 6, false, false, List.of("farmer", "base", "hybrid")},
                {"ember_heart", "Ember Heart", obj("heat_resistant", true, "production", 1.05), "magma_cream", 4, 5, false, false, List.of()},
                {"frost_heart", "Frost Heart", obj("cold_resistant", true, "growth", 1.05), "blue_ice", 2, 5, false, false, List.of()},
                {"symbiotic", "Symbiotic", obj("food", 0.8, "farming", 1.1, "production", 0.95), "honeycomb", 4, 5, false, false, List.of()},
                {"resonant", "Resonant", obj("production", 1.1, "special", 1.15), "echo_shard", 1, 3, false, false, List.of("mineral", "hybrid", "endgame")},
                {"void_touched", "Void Touched", obj("special", 1.5, "eggs", 0.85), "shulker_shell", 1, 2, false, true, List.of("mineral", "hybrid", "endgame")},
                {"adaptive", "Adaptive", obj("cold_resistant", true, "heat_resistant", true), null, 0, 2, false, true, List.of()},
                {"hive_mind", "Hive Mind", obj("production", 1.1, "gathering", 1.1, "farming", 1.1, "soldiers", 1.1), null, 0, 2, false, true, List.of()},
                // More flaws, from random mutations only; the lab's Purge removes them.
                {"weak_mandibles", "Weak Mandibles", obj("gathering", 0.85), null, 0, 5, true, false, List.of()},
                {"brittle_shell", "Brittle Shell", obj("soldiers", 0.8, "growth", 0.95), null, 0, 4, true, false, List.of()},
                {"infertile", "Infertile", obj("eggs", 0.8), null, 0, 3, true, false, List.of()},
                {"clumsy", "Clumsy", obj("special", 0.8, "production", 0.95), null, 0, 4, true, false, List.of()},
        };
        for (Object[] m : list) {
            Map<String, Object> json = obj("effects", m[2], "random_weight", m[5], "negative", (Boolean) m[6] ? true : null,
                    "rare", (Boolean) m[7] ? true : null,
                    "catalyst", m[3] == null ? null : obj("ingredient", mc((String) m[3]), "count", m[4]),
                    "categories", ((List<?>) m[8]).isEmpty() ? null : m[8]);
            write(DATA.resolve(NS + "/mutation/" + m[0] + ".json"), json);
            LANG.put("mutation." + NS + "." + m[0], (String) m[1]);
        }
    }

    static void traitUpgrades() {
        String[][] traits = {
                {"worker_productivity", "iron_ingot"}, {"gathering_efficiency", "gold_ingot"}, {"food_efficiency", "golden_carrot"},
                {"egg_production", "egg"}, {"larvae_growth", "honey_bottle"}, {"soldier_strength", "bone"}, {"farmer_productivity", "brown_mushroom"},
                {"mutation_probability", "fermented_spider_eye"}, {"environmental_resistance", "magma_cream"}, {"specialization", "amethyst_shard"},
        };
        for (String[] trait : traits) {
            String theme = mc(trait[1]);
            Object[][] steps = {
                    {ac("royal_jelly"), 1, mc("redstone"), 8, 10_000, 200},
                    {ac("royal_jelly"), 2, mc("glowstone_dust"), 8, 20_000, 300},
                    {ac("genetic_circuit"), 1, theme, 4, 30_000, 400},
                    {ac("genetic_circuit"), 2, mc("gold_ingot"), 4, 50_000, 500},
                    {ac("genetic_circuit"), 2, theme, 12, 80_000, 600},
                    {ac("crystal_core"), 1, mc("diamond"), 2, 120_000, 800},
                    {ac("crystal_core"), 2, ac("infernal_chitin"), 2, 180_000, 1000},
                    {ac("crystal_core"), 3, ac("void_chitin"), 2, 260_000, 1200},
                    {ac("crystal_core"), 4, mc("nether_star"), 1, 400_000, 1600},
            };
            List<Object> json = new ArrayList<>();
            for (Object[] step : steps) {
                json.add(obj("cost", arr(obj("ingredient", step[0], "count", step[1]), obj("ingredient", step[2], "count", step[3])),
                        "energy", step[4], "time", step[5]));
            }
            write(DATA.resolve(NS + "/trait_upgrade/" + trait[0] + ".json"), obj("trait", trait[0], "steps", json));
        }
    }

    static void foodMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        Object[][] foods = {
                {ac("ant_food"), 200, null, 0}, {ac("mineral_ant_food"), 100, "mineral", 100}, {ac("crystal_ant_food"), 100, "crystal", 100},
                {ac("nether_ant_food"), 150, "nether", 100}, {ac("royal_jelly"), 400, null, 0},
                {"sugar", 20, null, 0}, {"sweet_berries", 30, null, 0}, {"glow_berries", 40, null, 0}, {"apple", 50, null, 0},
                {"wheat_seeds", 4, null, 0}, {"wheat", 15, null, 0}, {"bread", 80, null, 0}, {"honey_bottle", 150, null, 0},
                {"brown_mushroom", 25, null, 0}, {"red_mushroom", 25, null, 0}, {"melon_slice", 25, null, 0}, {"carrot", 25, null, 0},
                {"potato", 25, null, 0}, {"beetroot", 20, null, 0}, {"rotten_flesh", 30, null, 0}, {"spider_eye", 25, null, 0},
                {"beef", 40, null, 0}, {"porkchop", 40, null, 0}, {"chicken", 40, null, 0}, {"mutton", 40, null, 0}, {"rabbit", 40, null, 0},
                {"cod", 30, null, 0}, {"salmon", 30, null, 0}, {"egg", 30, null, 0}, {"cookie", 40, null, 0},
        };
        for (Object[] food : foods) {
            values.put(mc((String) food[0]), obj("food", ((Integer) food[1]).doubleValue(), "special_kind", food[2],
                    "special", food[2] == null ? null : ((Integer) food[3]).doubleValue()));
        }
        write(DATA.resolve("data_maps/item/ant_food.json"), obj("values", values));
    }

    // ------------------------------------------------------------------ Recipes

    static void shaped(String name, String result, int count, String[] pattern, Object... keys) {
        Map<String, Object> key = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i += 2) {
            String value = (String) keys[i + 1];
            key.put((String) keys[i], value.startsWith("#") ? value : mc(value));
        }
        write(DATA.resolve("recipe/" + name + ".json"), obj("type", "minecraft:crafting_shaped", "category", "misc", "key", key,
                "pattern", Arrays.asList(pattern), "result", obj("count", count == 1 ? null : count, "id", mc(result))));
    }

    static void shapeless(String name, String result, int count, String... ingredients) {
        List<Object> list = new ArrayList<>();
        for (String ingredient : ingredients) {
            list.add(ingredient.startsWith("#") ? ingredient : mc(ingredient));
        }
        write(DATA.resolve("recipe/" + name + ".json"), obj("type", "minecraft:crafting_shapeless", "category", "misc", "ingredients", list,
                "result", obj("count", count == 1 ? null : count, "id", mc(result))));
    }

    static void recipes() {
        shaped("tweezers", ac("tweezers"), 1, new String[] {"N N", " I "}, "N", "iron_nugget", "I", "iron_ingot");
        shaped("test_tube", ac("test_tube"), 3, new String[] {"P", "P"}, "P", "glass_pane");
        shapeless("ant_food", ac("ant_food"), 4, "sugar", "wheat_seeds", "wheat_seeds", "#minecraft:flowers");
        shapeless("ant_food_from_berries", ac("ant_food"), 4, "sugar", "sweet_berries", "bone_meal");
        shapeless("mineral_ant_food", ac("mineral_ant_food"), 2, ac("ant_food"), ac("ant_food"), "iron_nugget", "iron_nugget", "redstone");
        shapeless("crystal_ant_food", ac("crystal_ant_food"), 2, ac("ant_food"), ac("ant_food"), "amethyst_shard", "quartz", "glowstone_dust");
        shapeless("nether_ant_food", ac("nether_ant_food"), 2, ac("ant_food"), ac("ant_food"), "blaze_powder", "nether_wart", "magma_cream");
        shapeless("royal_jelly", ac("royal_jelly"), 2, "honey_bottle", "sugar", ac("ant_food"));
        shapeless("nuptial_pheromone", ac("nuptial_pheromone"), 1, ac("royal_jelly"), "fermented_spider_eye", "glowstone_dust");
        shapeless("evolution_catalyst", ac("evolution_catalyst"), 1, ac("royal_jelly"), "glowstone_dust", "redstone", "amethyst_shard");
        shapeless("reroll_serum", ac("reroll_serum"), 1, ac("evolution_catalyst"), "nether_wart", "ender_pearl", ac("genetic_circuit"));
        shapeless("chitin_plate", ac("chitin_plate"), 4, "bone_meal", "bone_meal", "slime_ball");
        // The Chitin ladder: each step needs the next dimension. Hardened in a Blast Furnace; Infernal with the Nether's ghast
        // tears, blaze powder and wart; Void with the End's shulker shells, popped chorus and an eye of ender.
        write(DATA.resolve("recipe/hardened_chitin.json"), obj("type", "minecraft:blasting", "category", "misc", "cookingtime", 100, "experience", 0.2,
                "ingredient", ac("chitin_plate"), "result", obj("id", ac("hardened_chitin"))));
        shaped("infernal_chitin", ac("infernal_chitin"), 4, new String[] {"HBH", "WGW", "HBH"}, "H", ac("hardened_chitin"), "B", "blaze_powder",
                "W", "nether_wart", "G", "ghast_tear");
        shaped("void_chitin", ac("void_chitin"), 4, new String[] {"ISI", "PEP", "ISI"}, "I", ac("infernal_chitin"), "S", "shulker_shell",
                "P", "popped_chorus_fruit", "E", "ender_eye");
        shaped("upgrade_base", ac("upgrade_base"), 2, new String[] {"CIC", "IRI", "CIC"}, "C", ac("chitin_plate"), "I", "iron_ingot", "R", "redstone");
        shaped("genetic_circuit", ac("genetic_circuit"), 2, new String[] {"GRG", "QAQ", "GRG"}, "G", "gold_nugget", "R", "redstone", "Q", "quartz",
                "A", "amethyst_shard");
        shaped("crystal_core", ac("crystal_core"), 1, new String[] {"ADA", "AGA", "ADA"}, "A", "amethyst_shard", "D", "diamond", "G", ac("genetic_circuit"));
        shapeless("diamond_from_fragments", "diamond", 1, repeat(ac("diamond_fragment"), 9));
        shapeless("emerald_from_fragments", "emerald", 1, repeat(ac("emerald_fragment"), 9));
        shapeless("ant_identification_book", ac("ant_identification_book"), 1, "book", ac("test_tube"));
        shaped("colony_scanner", ac("colony_scanner"), 1, new String[] {" G ", "IRI", " I "}, "G", "glass_pane", "I", "iron_ingot", "R", "redstone");
        shaped("genetic_analyzer", ac("genetic_analyzer"), 1, new String[] {" G ", "ICI", " I "}, "G", "glass_pane", "I", "iron_ingot", "C", ac("genetic_circuit"));
        // Blocks.
        shaped("terrarium", ac("terrarium"), 1, new String[] {"PGP", "GDG", "PPP"}, "P", "#minecraft:planks", "G", "glass", "D", "dirt");
        // The colony chambers: packed mud and what each one is for.
        shaped("nest_chamber", ac("nest_chamber"), 1, new String[] {"MRM", "MWM", "MMM"}, "M", "packed_mud", "R", ac("royal_jelly"), "W", "#minecraft:wool");
        shaped("storage", ac("storage"), 1, new String[] {"P P", "PCP", "PPP"}, "P", "#minecraft:planks", "C", "chest");
        shaped("foraging_site", ac("foraging_site"), 1, new String[] {"SBS", "DMD", "DCD"}, "S", "stick", "B", "bone", "D", "coarse_dirt",
                "M", "packed_mud", "C", "chest");
        shaped("harvesting_site", ac("harvesting_site"), 1, new String[] {"RBR", "DMD", "DDD"}, "R", "brown_mushroom", "B", "bone_meal", "D", "dirt",
                "M", "packed_mud");
        shaped("hunting_ground", ac("hunting_ground"), 1, new String[] {"BWB", "SMS", "SSS"}, "B", "bone", "W", "iron_sword", "S", "cobblestone",
                "M", "packed_mud");
        shaped("queen_chamber", ac("queen_chamber"), 1, new String[] {"MGM", "RMR", "MMM"}, "M", "packed_mud", "G", "gold_ingot", "R", ac("royal_jelly"));
        shaped("generator", ac("generator"), 1, new String[] {"III", "IFI", "CRC"}, "I", "iron_ingot", "F", "furnace", "C", "copper_ingot", "R", "redstone");
        shaped("upgrade_station", ac("upgrade_station"), 1, new String[] {"CCC", "PTP", "P P"}, "C", ac("chitin_plate"), "P", "#minecraft:planks", "T", "crafting_table");
        // The Genetic Laboratory, one block: about what its controller and five parts used to cost together.
        shaped("genetic_laboratory", ac("genetic_laboratory"), 1, new String[] {"IGI", "CXC", "ARA"}, "I", ac("hardened_chitin"), "G", "glass",
                "C", ac("genetic_circuit"), "X", ac("genetic_analyzer"), "A", "amethyst_block", "R", "redstone_block");
        // Upgrades: tier 1 from an Upgrade Base, tiers 2 to 4 from the tier below.
        String[][] materials = {{"speed", "sugar"}, {"food_efficiency", "golden_carrot"}, {"simulation", "comparator"}, {"storage", "chest"},
                {"breeding", "egg"}, {"genetic", ac("genetic_circuit")}, {"hunting", "iron_sword"}, {"automation", "hopper"},
                {"population", "packed_mud"}, {"evolution", ac("evolution_catalyst")}};
        for (String[] m : materials) {
            shaped(m[0] + "_upgrade_1", ac(m[0] + "_upgrade_1"), 1, new String[] {" X ", "XBX", " X "}, "X", m[1], "B", ac("upgrade_base"));
            shaped(m[0] + "_upgrade_2", ac(m[0] + "_upgrade_2"), 1, new String[] {"GXG", "XUX", "GXG"}, "G", ac("hardened_chitin"), "X", m[1], "U", ac(m[0] + "_upgrade_1"));
            shaped(m[0] + "_upgrade_3", ac(m[0] + "_upgrade_3"), 1, new String[] {"DXD", "XUX", "DXD"}, "D", ac("infernal_chitin"), "X", m[1], "U", ac(m[0] + "_upgrade_2"));
            // Tier IV, the endgame: four Void Chitin (shulker shells, ghast tears, an eye of ender behind them) and two netherite ingots.
            shaped(m[0] + "_upgrade_4", ac(m[0] + "_upgrade_4"), 1, new String[] {"VXV", "NUN", "VXV"}, "V", ac("void_chitin"), "X", m[1],
                    "N", "netherite_ingot", "U", ac(m[0] + "_upgrade_3"));
        }
    }

    static String[] repeat(String value, int times) {
        String[] out = new String[times];
        Arrays.fill(out, value);
        return out;
    }

    // ------------------------------------------------------------------ Blocks, items, loot, tags, worldgen, models

    static final String[] STRUCTURES = {"nest_chamber", "foraging_site", "harvesting_site", "hunting_ground", "storage", "queen_chamber"};
    static final String[] NESTS = {"common", "wood", "stone", "snow", "desert", "farmer", "jungle", "swamp", "savanna", "shore", "blossom", "pale",
            "badlands", "army", "warped", "chorus"};
    static final String[] UPGRADE_TYPES = {"speed", "food_efficiency", "simulation", "storage", "breeding", "genetic", "hunting", "automation",
            "population", "evolution"};

    static void selfDrop(String block) {
        write(DATA.resolve("loot_table/blocks/" + block + ".json"), obj("type", "minecraft:block", "pools", arr(obj(
                "condition", obj("type", "minecraft:survives_explosion"),
                "entries", arr(obj("type", "minecraft:item", "name", ac(block))), "rolls", 1)), "random_sequence", ac("blocks/" + block)));
    }

    static void loot() {
        write(DATA.resolve("loot_table/blocks/terrarium.json"), obj("type", "minecraft:block", "pools", arr(obj(
                "entries", arr(obj("type", "minecraft:item", "modifier", obj("type", "minecraft:copy_components", "include",
                        arr("minecraft:custom_name"), "source", "block_entity"), "name", ac("terrarium"))), "rolls", 1)),
                "random_sequence", ac("blocks/terrarium")));
        for (String block : new String[] {"upgrade_station", "genetic_laboratory", "generator"}) {
            selfDrop(block);
        }
        for (String block : STRUCTURES) {
            selfDrop(block);
        }
        for (String nest : NESTS) {
            write(DATA.resolve("loot_table/blocks/" + nest + "_ant_nest.json"), obj("type", "minecraft:block", "pools", arr(obj(
                    "entries", arr(obj("type", "minecraft:alternatives", "children", arr(
                            obj("type", "minecraft:item", "name", ac(nest + "_ant_nest"), "condition", obj("type", "minecraft:match_tool",
                                    "predicate", obj("predicates", obj("minecraft:enchantments", arr(obj("enchantments", "minecraft:silk_touch",
                                            "levels", obj("min", 1))))))),
                            obj("type", "minecraft:item", "name", "minecraft:coarse_dirt")))), "rolls", 1)),
                    "random_sequence", ac("blocks/" + nest + "_ant_nest")));
        }
        // Wild ants sometimes leave a Chitin Plate.
        write(DATA.resolve("loot_table/entities/wild_ant.json"), obj("type", "minecraft:entity", "pools", arr(obj(
                "entries", arr(obj("type", "minecraft:item", "name", ac("chitin_plate"))),
                "condition", obj("type", "minecraft:random_chance", "chance", 0.35), "rolls", 1)),
                "random_sequence", ac("entities/wild_ant")));
    }

    static void tags() {
        List<Object> pickaxe = new ArrayList<>();
        List<Object> shovel = new ArrayList<>();
        List<Object> axe = new ArrayList<>();
        pickaxe.add(ac("genetic_laboratory"));
        pickaxe.add(ac("generator"));
        for (String s : new String[] {"nest_chamber", "foraging_site", "harvesting_site", "hunting_ground", "queen_chamber"}) {
            shovel.add(ac(s));
        }
        for (String nest : NESTS) {
            shovel.add(ac(nest + "_ant_nest"));
        }
        for (String s : new String[] {"terrarium", "storage", "upgrade_station"}) {
            axe.add(ac(s));
        }
        write(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"), obj("values", pickaxe));
        write(RES.resolve("data/minecraft/tags/block/mineable/shovel.json"), obj("values", shovel));
        write(RES.resolve("data/minecraft/tags/block/mineable/axe.json"), obj("values", axe));
        // Blocks that give a Terrarium standing on them a climate (Climate#ofFloor). Whole families count: every
        // terracotta colour, every sandstone, every ice... (the Info page shows one cycling icon per family).
        String[] colours = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"};
        List<Object> hot = arr("#minecraft:sand", "minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:cut_sandstone",
                "minecraft:smooth_sandstone", "minecraft:red_sandstone", "minecraft:chiseled_red_sandstone", "minecraft:cut_red_sandstone",
                "minecraft:smooth_red_sandstone", "#minecraft:terracotta");
        for (String colour : colours) {
            hot.add("minecraft:" + colour + "_glazed_terracotta");
        }
        Map<String, List<Object>> floors = new LinkedHashMap<>();
        floors.put("cold", arr("minecraft:snow_block", "minecraft:powder_snow", "#minecraft:ice"));
        floors.put("hot", hot);
        floors.put("temperate", arr("#minecraft:dirt", "#minecraft:grass_blocks", "#minecraft:mud", "#minecraft:moss_blocks"));
        floors.put("underground", arr("#minecraft:base_stone_overworld", "minecraft:smooth_stone", "minecraft:polished_granite",
                "minecraft:polished_diorite", "minecraft:polished_andesite", "minecraft:polished_tuff", "minecraft:cobbled_deepslate",
                "minecraft:polished_deepslate", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:calcite",
                "minecraft:dripstone_block"));
        floors.put("nether", arr("#minecraft:base_stone_nether", "minecraft:polished_basalt", "minecraft:smooth_basalt",
                "minecraft:polished_blackstone", "#minecraft:nylium", "minecraft:magma_block", "minecraft:soul_sand", "minecraft:soul_soil"));
        for (Map.Entry<String, List<Object>> floor : floors.entrySet()) {
            write(DATA.resolve("tags/block/climate/" + floor.getKey() + ".json"), obj("values", floor.getValue()));
        }
        // Where each wild species lives.
        Map<String, List<Object>> biomes = new LinkedHashMap<>();
        biomes.put("common", arr("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest",
                "minecraft:birch_forest", "minecraft:cherry_grove"));
        biomes.put("wood", arr("#minecraft:is_taiga", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga"));
        biomes.put("stone", arr("minecraft:stony_peaks", "minecraft:stony_shore", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                "minecraft:dripstone_caves", "minecraft:jagged_peaks"));
        biomes.put("snow", arr("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:snowy_slopes", "minecraft:grove",
                "minecraft:frozen_peaks", "minecraft:snowy_beach"));
        biomes.put("desert", arr("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"));
        biomes.put("farmer", arr("#minecraft:is_jungle", "minecraft:mushroom_fields", "minecraft:swamp", "minecraft:mangrove_swamp"));
        biomes.put("jungle", arr("#minecraft:is_jungle"));
        biomes.put("swamp", arr("minecraft:swamp", "minecraft:mangrove_swamp"));
        biomes.put("savanna", arr("#minecraft:is_savanna"));
        biomes.put("shore", arr("minecraft:beach"));
        biomes.put("blossom", arr("minecraft:cherry_grove", "minecraft:flower_forest", "minecraft:meadow", "minecraft:sunflower_plains"));
        biomes.put("pale", arr("minecraft:pale_garden"));
        biomes.put("badlands", arr("#minecraft:is_badlands"));
        biomes.put("army", arr("#minecraft:is_savanna", "minecraft:plains", "minecraft:sunflower_plains"));
        biomes.put("warped", arr("minecraft:warped_forest", "minecraft:crimson_forest"));
        biomes.put("chorus", arr("minecraft:end_highlands", "minecraft:end_midlands"));
        List<Object> all = new ArrayList<>();
        for (Map.Entry<String, List<Object>> entry : biomes.entrySet()) {
            write(DATA.resolve("tags/worldgen/biome/spawns/" + entry.getKey() + ".json"), obj("values", entry.getValue()));
            all.add("#" + ac("spawns/" + entry.getKey()));
        }
        write(DATA.resolve("tags/worldgen/biome/has_wild_ants.json"), obj("values", all));
    }

    static void worldgen() {
        for (String nest : NESTS) {
            write(DATA.resolve("worldgen/feature/" + nest + "_ant_nest.json"), obj("type", "minecraft:simple_block", "to_place", obj("id", ac(nest + "_ant_nest"))));
            List<Object> placement = new ArrayList<>();
            boolean nether = nest.equals("warped");
            placement.add(obj("type", "minecraft:rarity_filter", "chance", nest.equals("stone") || nest.equals("snow") ? 28
                    : nest.equals("shore") || nest.equals("pale") ? 14 : nether ? 6 : 20));
            if (nether) {
                // A few tries in each chosen chunk: many land in rock or over lava.
                placement.add(obj("type", "minecraft:count", "count", 4));
            }
            placement.add(obj("type", "minecraft:in_square"));
            // Where a nest may stand: air, or (in the Nether's forests, thick with roots and sprouts) a plant it replaces.
            Map<String, Object> room = nether ? obj("type", "minecraft:matching_block_tag", "tag", "minecraft:replaceable")
                    : obj("type", "minecraft:matching_block_tag", "tag", "minecraft:air");
            if (nether) {
                // The Nether has a roof: take a height between floor and roof, scan down to the ground, stand on it.
                placement.add(obj("type", "minecraft:height_range", "height", obj("type", "minecraft:uniform",
                        "min_inclusive", obj("absolute", 32), "max_inclusive", obj("absolute", 110))));
                placement.add(obj("type", "minecraft:environment_scan", "direction_of_search", "down", "max_steps", 32,
                        "target_condition", obj("type", "minecraft:solid"), "allowed_search_condition", room));
                placement.add(obj("type", "minecraft:offset", "x", 0, "y", 1, "z", 0));
            } else {
                placement.add(obj("type", "minecraft:heightmap", "heightmap", "WORLD_SURFACE_WG"));
            }
            placement.add(obj("type", "minecraft:biome"));
            placement.add(obj("type", "minecraft:block_predicate_filter", "predicate", obj("type", "minecraft:all_of", "predicates", arr(
                    room, obj("type", "minecraft:has_sturdy_face", "direction", "up", "offset", arr(0, -1, 0))))));
            write(DATA.resolve("worldgen/placed_feature/" + nest + "_ant_nest.json"), obj("feature", ac(nest + "_ant_nest"), "placement", placement));
            write(DATA.resolve("neoforge/biome_modifier/add_" + nest + "_ant_nests.json"), obj("type", "neoforge:add_features",
                    "biomes", "#" + ac("spawns/" + nest), "features", ac(nest + "_ant_nest"), "step", "vegetal_decoration"));
        }
        write(DATA.resolve("neoforge/biome_modifier/add_wild_ants.json"), obj("type", "neoforge:add_spawns", "biomes", "#" + ac("has_wild_ants"),
                "spawners", arr(obj("type", ac("wild_ant"), "count", obj("type", "minecraft:uniform", "min_inclusive", 2, "max_inclusive", 5), "weight", 12))));
    }

    static void blockstates() {
        Path states = ASSETS.resolve("blockstates");
        Path blockModels = ASSETS.resolve("models/block");
        // The Terrarium and the chambers: facing, with the colony frame on the edges that touch no other colony part.
        colonyPart("terrarium");
        for (String s : STRUCTURES) {
            colonyPart(s);
        }
        frameModels();
        write(states.resolve("upgrade_station.json"), obj("variants", obj("", obj("model", ac("block/upgrade_station")))));
        // The Genetic Laboratory: facing, its windows lit while it works.
        for (String part : new String[] {"genetic_laboratory"}) {
            Map<String, Object> variants = new LinkedHashMap<>();
            String[] facings = {"north", "east", "south", "west"};
            int[] rotations = {0, 90, 180, 270};
            for (int f = 0; f < 4; f++) {
                for (String working : new String[] {"false", "true"}) {
                    variants.put("facing=" + facings[f] + ",working=" + working, obj("model", ac("block/" + part + (working.equals("true") ? "_working" : "")),
                            "y", rotations[f] == 0 ? null : rotations[f]));
                }
            }
            write(states.resolve(part + ".json"), obj("variants", variants));
            for (String working : new String[] {"", "_working"}) {
                write(blockModels.resolve(part + working + ".json"), obj("parent", "minecraft:block/orientable",
                        "textures", obj("front", ac("block/" + part + "_front" + working), "side", ac("block/" + part + "_side" + working),
                                "top", ac("block/" + part + "_top" + working))));
            }
        }
        // The Generator: facing, its front glowing while it burns.
        Map<String, Object> generator = new LinkedHashMap<>();
        String[] sides = {"north", "east", "south", "west"};
        for (int f = 0; f < 4; f++) {
            for (String lit : new String[] {"false", "true"}) {
                generator.put("facing=" + sides[f] + ",lit=" + lit, obj("model", ac("block/generator" + (lit.equals("true") ? "_on" : "")), "y", f == 0 ? null : f * 90));
            }
        }
        write(states.resolve("generator.json"), obj("variants", generator));
        for (String on : new String[] {"", "_on"}) {
            write(blockModels.resolve("generator" + on + ".json"), obj("parent", "minecraft:block/orientable", "textures", obj("front", ac("block/generator_front" + on),
                    "side", ac("block/generator_side"), "top", ac("block/generator_top"))));
        }
        for (String nest : NESTS) {
            // One look for every state (recovering or not, wild or a home nest).
            write(states.resolve(nest + "_ant_nest.json"), obj("variants", obj("", obj("model", ac("block/" + nest + "_ant_nest")))));
            write(blockModels.resolve(nest + "_ant_nest.json"), obj("parent", ac("block/ant_nest"), "textures", obj("side", ac("block/" + nest + "_ant_nest_side"),
                    "top", ac("block/" + nest + "_ant_nest_top"), "particle", ac("block/" + nest + "_ant_nest_side"))));
        }
    }

    // ------------------------------------------------------------------ The colony frame

    /** How thick the colony frame is, and how far it stands out of the faces it lies on (so the two never share pixels). */
    static final double FRAME = 2;
    static final double PROUD = 0.05;
    /** The twelve edges of a block, each named by the two sides it lies between: it shows when neither side touches another colony part. */
    static final String[][] EDGES = {{"north", "west"}, {"north", "east"}, {"south", "west"}, {"south", "east"},
            {"up", "north"}, {"up", "south"}, {"up", "west"}, {"up", "east"}, {"down", "north"}, {"down", "south"}, {"down", "west"},
            {"down", "east"}};
    static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    /**
     * A box of the frame texture. Where it lies on the block's outside it stands {@link #PROUD} out, so it never fights the
     * chamber's own faces; its UVs come from the box inside the block, so they never leave the texture.
     */
    static Map<String, Object> frameBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        double[] a = {x1, y1, z1};
        double[] b = {x2, y2, z2};
        List<Object> from = new ArrayList<>();
        List<Object> to = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            from.add(a[i] <= 0 ? -PROUD : a[i]);
            to.add(b[i] >= 16 ? 16 + PROUD : b[i]);
        }
        Map<String, Object> faces = obj(
                "down", obj("uv", arr(a[0], 16 - b[2], b[0], 16 - a[2]), "texture", "#frame"),
                "up", obj("uv", arr(a[0], a[2], b[0], b[2]), "texture", "#frame"),
                "north", obj("uv", arr(16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]), "texture", "#frame"),
                "south", obj("uv", arr(a[0], 16 - b[1], b[0], 16 - a[1]), "texture", "#frame"),
                "west", obj("uv", arr(a[2], 16 - b[1], b[2], 16 - a[1]), "texture", "#frame"),
                "east", obj("uv", arr(16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]), "texture", "#frame"));
        return obj("from", from, "to", to, "faces", faces);
    }

    /** Height of the chambers' floor inside their glass, in pixels (the ants walk on it; StructureRenderer.FLOOR). */
    static final double FLOOR = 4;

    /** A chamber's floor: a wooden base up to {@link #FLOOR}, its own floor on top, its sides hidden against touching blocks. */
    static Map<String, Object> floorBody() {
        return obj("from", arr(0, 0, 0), "to", arr(16, FLOOR, 16), "faces", obj(
                "down", obj("texture", "#side", "cullface", "down"), "up", obj("texture", "#top"),
                "north", obj("texture", "#side", "cullface", "north"), "south", obj("texture", "#side", "cullface", "south"),
                "west", obj("texture", "#side", "cullface", "west"), "east", obj("texture", "#side", "cullface", "east")));
    }

    /**
     * The parts every colony block shares: a frame beam on each of the twelve edges, and a glass pane on each side and on
     * top. A part shows only where the block touches no other part of a colony, so a whole build is one glass case with
     * its frame on the outside.
     */
    static void frameModels() {
        double f = FRAME;
        double e = 16 - FRAME;
        Map<String, double[]> beams = new LinkedHashMap<>();
        beams.put("north_west", new double[] {0, 0, 0, f, 16, f});
        beams.put("north_east", new double[] {e, 0, 0, 16, 16, f});
        beams.put("south_west", new double[] {0, 0, e, f, 16, 16});
        beams.put("south_east", new double[] {e, 0, e, 16, 16, 16});
        beams.put("up_north", new double[] {0, e, 0, 16, 16, f});
        beams.put("up_south", new double[] {0, e, e, 16, 16, 16});
        beams.put("up_west", new double[] {0, e, 0, f, 16, 16});
        beams.put("up_east", new double[] {e, e, 0, 16, 16, 16});
        beams.put("down_north", new double[] {0, 0, 0, 16, f, f});
        beams.put("down_south", new double[] {0, 0, e, 16, f, 16});
        beams.put("down_west", new double[] {0, 0, 0, f, f, 16});
        beams.put("down_east", new double[] {e, 0, 0, 16, f, 16});
        for (Map.Entry<String, double[]> beam : beams.entrySet()) {
            double[] c = beam.getValue();
            write(ASSETS.resolve("models/block/frame/" + beam.getKey() + ".json"), obj("parent", "minecraft:block/block",
                    "textures", obj("particle", "#frame", "frame", ac("block/terrarium_wood")),
                    "elements", arr(frameBox(c[0], c[1], c[2], c[3], c[4], c[5]))));
        }
        // Glass, just inside the frame: the walls reach across the whole side, so neighbours' walls run on unbroken.
        Map<String, double[]> panes = new LinkedHashMap<>();
        panes.put("glass_north", new double[] {0, 0, 0.25, 16, 16, 0.5});
        panes.put("glass_south", new double[] {0, 0, 15.5, 16, 16, 15.75});
        panes.put("glass_west", new double[] {0.25, 0, 0, 0.5, 16, 16});
        panes.put("glass_east", new double[] {15.5, 0, 0, 15.75, 16, 16});
        panes.put("glass_up", new double[] {0, 15.25, 0, 16, 15.5, 16});
        for (Map.Entry<String, double[]> pane : panes.entrySet()) {
            double[] c = pane.getValue();
            write(ASSETS.resolve("models/block/frame/" + pane.getKey() + ".json"), obj("parent", "minecraft:block/block",
                    "textures", obj("particle", ac("block/terrarium_glass"), "glass", obj("force_translucent", true, "sprite", ac("block/terrarium_glass"))),
                    "elements", arr(box(p(c[0], c[1], c[2]), p(c[3], c[4], c[5]), "#glass", "#glass"))));
        }
    }

    /** The sides that get a glass pane when nothing of a colony is there. */
    static final String[] GLASS = {"north", "east", "south", "west", "up"};

    /**
     * A colony part's blockstate: its own model turned to face the player, a frame beam on every edge whose two sides touch
     * no other colony part, and glass on every side (and the top) that touches none.
     */
    static void colonyPart(String block) {
        List<Object> parts = new ArrayList<>();
        int[] rotations = {0, 90, 180, 270};
        for (int f = 0; f < 4; f++) {
            parts.add(obj("when", obj("facing", HORIZONTAL[f]), "apply", obj("model", ac("block/" + block), "y", rotations[f] == 0 ? null : rotations[f])));
        }
        for (String[] edge : EDGES) {
            parts.add(obj("when", obj(edge[0], "false", edge[1], "false"), "apply", obj("model", ac("block/frame/" + edge[0] + "_" + edge[1]))));
        }
        for (String side : GLASS) {
            parts.add(obj("when", obj(side, "false"), "apply", obj("model", ac("block/frame/glass_" + side))));
        }
        write(ASSETS.resolve("blockstates/" + block + ".json"), obj("multipart", parts));
    }

    /** The item of a colony part: its model in its whole frame and glass, as it looks standing alone. */
    static Map<String, Object> framedItem(String block) {
        List<Object> models = new ArrayList<>();
        models.add(obj("type", "minecraft:model", "model", ac("block/" + block)));
        for (String[] edge : EDGES) {
            models.add(obj("type", "minecraft:model", "model", ac("block/frame/" + edge[0] + "_" + edge[1])));
        }
        for (String side : GLASS) {
            models.add(obj("type", "minecraft:model", "model", ac("block/frame/glass_" + side)));
        }
        return obj("type", "minecraft:composite", "models", models);
    }

    static void facing(String block) {
        Map<String, Object> variants = new LinkedHashMap<>();
        String[] facings = {"north", "east", "south", "west"};
        int[] rotations = {0, 90, 180, 270};
        for (int f = 0; f < 4; f++) {
            variants.put("facing=" + facings[f], obj("model", ac("block/" + block), "y", rotations[f] == 0 ? null : rotations[f]));
        }
        write(ASSETS.resolve("blockstates/" + block + ".json"), obj("variants", variants));
    }

    /** Item model definitions (items/*.json) and the flat item models; tubes, samples, brood and species upgrades are tinted. */
    static void items() {
        Path items = ASSETS.resolve("items");
        Path models = ASSETS.resolve("models/item");
        String[] flat = {"tweezers", "test_tube", "ant_food", "mineral_ant_food", "crystal_ant_food", "nether_ant_food", "royal_jelly", "nuptial_pheromone",
                "evolution_catalyst", "reroll_serum", "chitin_plate", "hardened_chitin", "infernal_chitin", "void_chitin", "upgrade_base", "genetic_circuit", "crystal_core", "ant_identification_book",
                "colony_scanner", "genetic_analyzer", "diamond_fragment", "emerald_fragment", "wild_ant_spawn_egg", "ant_head"};
        for (String item : flat) {
            write(models.resolve(item + ".json"), obj("parent", item.equals("tweezers") ? "minecraft:item/handheld" : "minecraft:item/generated",
                    "textures", obj("layer0", ac("item/" + item))));
            write(items.resolve(item + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + item))));
        }
        for (String type : UPGRADE_TYPES) {
            for (int tier = 1; tier <= 4; tier++) {
                String id = type + "_upgrade_" + tier;
                write(models.resolve(id + ".json"), obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/upgrade_board_" + tier),
                        "layer1", ac("item/upgrade_icon_" + type))));
                write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + id))));
            }
        }
        // Tinted by species: layer 1 (the contents) takes the species color.
        Object[][] tinted = {{"ant_test_tube", "gaster"}, {"queen_test_tube", "gaster"}, {"genetic_sample", "crystal"}, {"ant_egg", "body"},
                {"ant_larva", "body"}, {"species_upgrade", "crystal"}};
        for (Object[] t : tinted) {
            String id = (String) t[0];
            write(models.resolve(id + ".json"), obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/" + id),
                    "layer1", ac("item/" + id + "_overlay"))));
            write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + id), "tints", arr(
                    obj("type", "minecraft:constant", "value", -1),
                    obj("type", ac("species"), "part", t[1], "default", id.equals("species_upgrade") ? -6250336 : -1)))));
        }
        // Block items use their block models.
        List<String> blocks = new ArrayList<>(Arrays.asList(STRUCTURES));
        blocks.addAll(List.of("terrarium", "upgrade_station", "genetic_laboratory", "generator"));
        for (String nest : NESTS) {
            blocks.add(nest + "_ant_nest");
        }
        for (String block : blocks) {
            boolean part = block.equals("terrarium") || Arrays.asList(STRUCTURES).contains(block);
            write(items.resolve(block + ".json"), obj("model", part ? framedItem(block)
                    : obj("type", "minecraft:model", "model", ac("block/" + block))));
        }
    }

    static void particles() {
        Object[][] list = {{"soil", 4}, {"crumb", 2}, {"sparkle", 4}, {"gene", 4}, {"evolution", 4}};
        for (Object[] p : list) {
            List<Object> textures = new ArrayList<>();
            for (int i = 0; i < (Integer) p[1]; i++) {
                textures.add(ac(p[0] + "_" + i));
            }
            write(ASSETS.resolve("particles/" + p[0] + ".json"), obj("textures", textures));
        }
    }

    static void sounds() {
        Map<String, Object> sounds = new LinkedHashMap<>();
        Object[][] list = {
                {"entity.ant.step", "minecraft:entity.silverfish.step", 0.3, 1.8},
                {"entity.ant.hurt", "minecraft:entity.silverfish.hurt", 0.6, 1.7},
                {"entity.ant.bite", "minecraft:entity.spider.step", 0.6, 1.9},
                {"block.terrarium.ambience", "minecraft:block.rooted_dirt.step", 0.4, 1.6},
                {"block.colony.food", "minecraft:entity.item.pickup", 0.4, 1.6},
                {"block.terrarium.queen", "minecraft:block.amethyst_block.chime", 0.8, 0.8},
                {"block.terrarium.egg", "minecraft:entity.turtle.lay_egg", 0.5, 1.6},
                {"block.colony.activate", "minecraft:block.beehive.work", 0.6, 1.2},
                {"block.fighting_site.battle", "minecraft:entity.spider.ambient", 0.4, 1.8},
                {"block.terrarium.evolution", "minecraft:block.beacon.power_select", 1.0, 1.4},
                {"block.genetic_lab.hum", "minecraft:block.beacon.ambient", 0.5, 1.6},
                {"block.genetic_lab.complete", "minecraft:block.amethyst_block.resonate", 1.0, 1.2},
                {"item.tweezers.capture", "minecraft:block.glass.hit", 0.6, 1.8},
        };
        for (Object[] s : list) {
            String name = (String) s[0];
            sounds.put(name, obj("subtitle", "subtitles." + NS + "." + name, "sounds", arr(obj("name", s[1], "type", "event", "volume", s[2], "pitch", s[3]))));
            LANG.put("subtitles." + NS + "." + name, title(name.substring(name.lastIndexOf('.') + 1)).replace("Step", "Ant scuttles")
                    .replace("Hurt", "Ant hurts").replace("Bite", "Ant bites").replace("Ambience", "Colony bustles").replace("Food", "Ants collect food")
                    .replace("Queen", "Queen stirs").replace("Egg", "Queen lays eggs").replace("Activate", "Colony structure starts").replace("Battle", "Soldiers fight")
                    .replace("Evolution", "Queen evolves").replace("Hum", "Genetic Laboratory hums").replace("Complete", "Genetic work completes")
                    .replace("Capture", "Ant caught"));
        }
        write(ASSETS.resolve("sounds.json"), sounds);
    }

    // ------------------------------------------------------------------ Hand-shaped block models

    static Map<String, Object> face(String texture) {
        return obj("texture", texture);
    }

    /** A box with the same texture variable on its sides and another on top and bottom (UVs from its position). */
    static Map<String, Object> box(double[] from, double[] to, String side, String top) {
        return obj("from", arr(from[0], from[1], from[2]), "to", arr(to[0], to[1], to[2]), "faces", obj(
                "down", face(top), "up", face(top), "north", face(side), "south", face(side), "west", face(side), "east", face(side)));
    }

    static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    static void model(String name, Map<String, Object> textures, List<Object> elements) {
        write(ASSETS.resolve("models/block/" + name + ".json"), obj("parent", "minecraft:block/block", "textures", textures, "elements", elements));
    }

    static void customModels() {
        // The Terrarium: a wooden base; its glass and frame come from the colony parts, so it opens into the chambers
        // touching it. The soil, tunnels and ants inside are drawn by its renderer.
        model("terrarium", obj("particle", ac("block/terrarium_wood"), "wood", ac("block/terrarium_wood")),
                arr(box(p(0, 0, 0), p(16, 1, 16), "#wood", "#wood")));
        // Wild nests: a mound with an entrance on top.
        write(ASSETS.resolve("models/block/ant_nest.json"), obj("parent", "minecraft:block/block", "textures", obj("particle", "#side"), "elements", arr(
                box(p(1, 0, 1), p(15, 4, 15), "#side", "#side"), box(p(3, 4, 3), p(13, 7, 13), "#side", "#side"),
                obj("from", arr(5, 7, 5), "to", arr(11, 9, 11), "faces", obj("down", face("#side"), "up", face("#top"), "north", face("#side"),
                        "south", face("#side"), "west", face("#side"), "east", face("#side"))))));
        // The chambers: the same wooden floor under the same glass, each with its own floor and what lies on it.
        String wood = ac("block/terrarium_wood");
        for (String s : STRUCTURES) {
            String top = ac("block/" + (s.equals("storage") ? "storage_inside" : s + "_top"));
            double y = FLOOR;
            switch (s) {
                case "nest_chamber" -> model(s, obj("particle", top, "side", wood, "top", top, "mud", "minecraft:block/packed_mud",
                        "egg", "minecraft:block/white_concrete", "larva", "minecraft:block/white_terracotta"), arr(
                        floorBody(),
                        // A mud mound with the brood beside it: eggs, and larvae curled up.
                        box(p(3, y, 3), p(9, y + 2.5, 9), "#mud", "#mud"), box(p(4.5, y + 2.5, 4.5), p(7.5, y + 3.5, 7.5), "#mud", "#mud"),
                        box(p(10.5, y, 4), p(11.5, y + 1.5, 5), "#egg", "#egg"), box(p(12, y, 4.5), p(13, y + 1.5, 5.5), "#egg", "#egg"),
                        box(p(11, y, 6), p(12, y + 1.5, 7), "#egg", "#egg"), box(p(4, y, 11), p(7, y + 1, 12), "#larva", "#larva"),
                        box(p(9, y, 11.5), p(12, y + 1, 12.5), "#larva", "#larva")));
                case "queen_chamber" -> model(s, obj("particle", top, "side", wood, "top", top, "gold", "minecraft:block/gold_block",
                        "comb", "minecraft:block/honeycomb_block"), arr(
                        floorBody(),
                        // A golden dais with a royal cell on it.
                        box(p(4, y, 4), p(12, y + 1, 12), "#gold", "#gold"), box(p(6, y + 1, 6), p(10, y + 3.5, 10), "#comb", "#comb")));
                case "foraging_site" -> model(s, obj("particle", top, "side", wood, "top", top, "stick", "minecraft:block/oak_log",
                        "bone", "minecraft:block/bone_block_side", "dirt", "minecraft:block/coarse_dirt"), arr(
                        floorBody(),
                        box(p(3, y, 3), p(6, y + 1, 6), "#dirt", "#dirt"), box(p(10, y, 10), p(13, y + 1.5, 13), "#dirt", "#dirt"),
                        obj("from", arr(9, y, 3), "to", arr(10, y + 1, 9), "rotation", obj("angle", 22.5, "axis", "y", "origin", arr(9.5, y + 0.5, 6)),
                                "faces", obj("up", face("#stick"), "north", face("#stick"), "south", face("#stick"), "west", face("#stick"), "east", face("#stick"))),
                        box(p(3, y, 11), p(6, y + 0.8, 12), "#bone", "#bone"), box(p(4, y, 10), p(5, y + 0.8, 13), "#bone", "#bone")));
                case "harvesting_site" -> model(s, obj("particle", top, "side", wood, "top", top, "stem", "minecraft:block/mushroom_stem",
                        "cap", "minecraft:block/red_mushroom_block", "cap2", "minecraft:block/brown_mushroom_block"), arr(
                        floorBody(),
                        box(p(3.5, y, 3.5), p(4.5, y + 3, 4.5), "#stem", "#stem"), box(p(2.5, y + 3, 2.5), p(5.5, y + 4, 5.5), "#cap", "#cap"),
                        box(p(11.5, y, 10.5), p(12.5, y + 4, 11.5), "#stem", "#stem"), box(p(10, y + 4, 9), p(14, y + 5, 13), "#cap2", "#cap2"),
                        box(p(11, y, 4), p(11.8, y + 1.2, 4.8), "#stem", "#stem"), box(p(10.4, y + 1.2, 3.4), p(12.4, y + 1.9, 5.4), "#cap", "#cap")));
                case "hunting_ground" -> model(s, obj("particle", top, "side", wood, "top", top, "bone", "minecraft:block/bone_block_side",
                        "stone", "minecraft:block/cobblestone"), arr(
                        floorBody(),
                        // Stones for cover and the bones of past catches.
                        box(p(2, y, 2), p(5, y + 2, 5), "#stone", "#stone"), box(p(11, y, 10), p(14, y + 1.5, 13), "#stone", "#stone"),
                        box(p(10, y, 3), p(13, y + 0.8, 4), "#bone", "#bone"), box(p(11, y, 2), p(12, y + 0.8, 5), "#bone", "#bone"),
                        box(p(3, y, 11), p(7, y + 0.8, 12), "#bone", "#bone")));
                default -> model(s, obj("particle", top, "side", wood, "top", top), arr(floorBody()));
            }
        }
        model("upgrade_station", obj("particle", ac("block/upgrade_station_side"), "side", ac("block/upgrade_station_side"),
                "top", ac("block/upgrade_station_top"), "leg", "minecraft:block/stripped_oak_log"), arr(
                box(p(0, 10, 0), p(16, 13, 16), "#side", "#top"),
                box(p(1, 0, 1), p(3, 10, 3), "#leg", "#leg"), box(p(13, 0, 1), p(15, 10, 3), "#leg", "#leg"),
                box(p(1, 0, 13), p(3, 10, 15), "#leg", "#leg"), box(p(13, 0, 13), p(15, 10, 15), "#leg", "#leg"),
                box(p(3, 4, 3), p(13, 5, 13), "#leg", "#leg")));
    }

    // ------------------------------------------------------------------ Language

    // ------------------------------------------------------------------ Chitin gear, Guard Ants, giant anthills

    static final String[] CHITIN_ARMOR = {"chitin_helmet", "chitin_chestplate", "chitin_leggings", "chitin_boots"};
    static final String[] CHITIN_TOOLS = {"chitin_sword", "chitin_pickaxe", "chitin_axe", "chitin_shovel", "chitin_hoe"};

    /**
     * The Chitin Shield's models: the vanilla shield's shape (a 12x22x1 plate and a 2x6x6 handle, as its special model
     * puts them after flipping y and z) and its hand poses, as a plain model, so it needs no renderer of its own. One
     * model for holding it and one for blocking, switched by whether it is being used, like the vanilla shield.
     * Texture pixels map 1:1 (TexGen's {@code chitinShield}); a 64-pixel texture is 16 uv units, so uv = pixel / 4.
     */
    static void chitinShield() {
        Map<String, Object> plate = obj("from", arr(-6, -11, 1), "to", arr(6, 11, 2), "faces", obj(
                "south", face(2, 2, 14, 24), "north", face(16, 2, 28, 24), "east", face(30, 2, 31, 24), "west", face(32, 2, 33, 24),
                "up", face(2, 26, 14, 27), "down", face(16, 26, 28, 27)));
        Map<String, Object> handleFace = face(40, 2, 46, 8);
        Map<String, Object> handle = obj("from", arr(-1, -3, -5), "to", arr(1, 3, 1), "faces", obj("north", handleFace, "south", handleFace,
                "east", handleFace, "west", handleFace, "up", handleFace, "down", handleFace));
        Map<String, Object> textures = obj("shield", ac("item/chitin_shield"), "particle", ac("item/chitin_shield"));
        Map<String, Object> holding = obj(
                "thirdperson_righthand", pose(0, 90, 0, 10, 6, -4, 1), "thirdperson_lefthand", pose(0, 90, 0, 10, 6, 12, 1),
                "firstperson_righthand", pose(0, 180, 5, -10, 1.75, -10, 1.25), "firstperson_lefthand", pose(0, 180, 5, 10, 0, -10, 1.25),
                "gui", pose(15, -25, -5, 2, 3, 0, 0.65), "fixed", pose(0, 180, 0, -4.5, 4.5, -5, 0.55),
                "on_shelf", pose(0, 0, 0, 11, 18.5, 8.7, 1.4), "ground", pose(0, 0, 0, 2, 4, 2, 0.25));
        Map<String, Object> blocking = new LinkedHashMap<>(holding);
        blocking.put("thirdperson_righthand", pose(45, 155, 0, -3.49, 11, -2, 1));
        blocking.put("thirdperson_lefthand", pose(45, 155, 0, 11.51, 7, 2.5, 1));
        blocking.put("firstperson_righthand", pose(0, 180, -5, -15, 3.25, -11, 1.25));
        blocking.put("firstperson_lefthand", pose(0, 180, -5, 5, 5, -11, 1.25));
        Path models = ASSETS.resolve("models/item");
        write(models.resolve("chitin_shield.json"), obj("gui_light", "front", "textures", textures, "elements", arr(plate, handle), "display", holding));
        write(models.resolve("chitin_shield_blocking.json"), obj("gui_light", "front", "textures", textures, "elements", arr(plate, handle),
                "display", blocking));
        write(ASSETS.resolve("items/chitin_shield.json"), obj("model", obj("type", "minecraft:condition", "property", "minecraft:using_item",
                "on_false", obj("type", "minecraft:model", "model", ac("item/chitin_shield")),
                "on_true", obj("type", "minecraft:model", "model", ac("item/chitin_shield_blocking")))));
    }

    /** A model face showing the texture's pixels from (x0, y0) to (x1, y1) of a 64x64 texture. */
    static Map<String, Object> face(double x0, double y0, double x1, double y1) {
        return obj("uv", arr(x0 / 4, y0 / 4, x1 / 4, y1 / 4), "texture", "#shield");
    }

    /** An item display pose: rotation, translation and an even scale. */
    static Map<String, Object> pose(double rx, double ry, double rz, double tx, double ty, double tz, double scale) {
        return obj("rotation", arr(rx, ry, rz), "translation", arr(tx, ty, tz), "scale", arr(scale, scale, scale));
    }

    static void newFeatures() {
        Path items = ASSETS.resolve("items");
        Path models = ASSETS.resolve("models/item");
        // Item models: tools are held like tools.
        List<String> flat = new ArrayList<>(Arrays.asList(CHITIN_ARMOR));
        flat.add("rally_pheromone");
        flat.add("guard_ant");
        for (String item : flat) {
            write(models.resolve(item + ".json"), obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/" + item))));
            write(items.resolve(item + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + item))));
        }
        for (String item : CHITIN_TOOLS) {
            write(models.resolve(item + ".json"), obj("parent", "minecraft:item/handheld", "textures", obj("layer0", ac("item/" + item))));
            write(items.resolve(item + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + item))));
        }
        // How the armor looks when worn (babies have their own smaller boxes, so their own texture).
        write(ASSETS.resolve("equipment/chitin.json"), obj("layers", obj("humanoid", arr(obj("texture", ac("chitin"))),
                "humanoid_baby", arr(obj("texture", ac("chitin"))), "humanoid_leggings", arr(obj("texture", ac("chitin"))))));
        chitinShield();

        // Recipes.
        String c = ac("chitin_plate");
        shaped("chitin_helmet", ac("chitin_helmet"), 1, new String[] {"CCC", "C C"}, "C", c);
        shaped("chitin_chestplate", ac("chitin_chestplate"), 1, new String[] {"C C", "CCC", "CCC"}, "C", c);
        shaped("chitin_leggings", ac("chitin_leggings"), 1, new String[] {"CCC", "C C", "C C"}, "C", c);
        shaped("chitin_boots", ac("chitin_boots"), 1, new String[] {"C C", "C C"}, "C", c);
        shaped("chitin_sword", ac("chitin_sword"), 1, new String[] {"C", "C", "S"}, "C", c, "S", "stick");
        shaped("chitin_pickaxe", ac("chitin_pickaxe"), 1, new String[] {"CCC", " S ", " S "}, "C", c, "S", "stick");
        shaped("chitin_axe", ac("chitin_axe"), 1, new String[] {"CC", "CS", " S"}, "C", c, "S", "stick");
        shaped("chitin_shovel", ac("chitin_shovel"), 1, new String[] {"C", "S", "S"}, "C", c, "S", "stick");
        shaped("chitin_hoe", ac("chitin_hoe"), 1, new String[] {"CC", " S", " S"}, "C", c, "S", "stick");
        shapeless("rally_pheromone", ac("rally_pheromone"), 1, ac("test_tube"), c, "sugar", "redstone");
        shaped("chitin_shield", ac("chitin_shield"), 1, new String[] {"CWC", "CCC", " C "}, "C", c, "W", "#minecraft:planks");

        // Tags: repairs, and the vanilla tool and armor tags (enchanting, durability and so on go by them).
        write(DATA.resolve("tags/item/repairs_chitin_gear.json"), obj("values", arr(c)));
        LANG.put("tag.item." + NS + ".repairs_chitin_gear", "Chitin Gear Repair Materials");
        // What a colony never eats from its own stores (FoodIntake): Royal Jelly is for raising Queens.
        write(DATA.resolve("tags/item/kept_in_storage.json"), obj("values", arr(ac("royal_jelly"))));
        LANG.put("tag.item." + NS + ".kept_in_storage", "Not Eaten From Colony Storage");
        // The shield: the common shield tag, and Unbreaking and Mending like the vanilla shield.
        write(RES.resolve("data/c/tags/item/tools/shield.json"), obj("values", arr(ac("chitin_shield"))));
        write(RES.resolve("data/minecraft/tags/item/enchantable/durability.json"), obj("values", arr(ac("chitin_shield"))));
        String[][] gearTags = {{"swords", "chitin_sword"}, {"pickaxes", "chitin_pickaxe"}, {"axes", "chitin_axe"}, {"shovels", "chitin_shovel"},
                {"hoes", "chitin_hoe"}, {"head_armor", "chitin_helmet"}, {"chest_armor", "chitin_chestplate"}, {"leg_armor", "chitin_leggings"},
                {"foot_armor", "chitin_boots"}};
        for (String[] tag : gearTags) {
            write(RES.resolve("data/minecraft/tags/item/" + tag[0] + ".json"), obj("values", arr(ac(tag[1]))));
        }

        // The giant anthill: the structure, how often (about one per 32x32 chunks, never in a village) and where.
        write(DATA.resolve("worldgen/structure/giant_anthill.json"), obj("type", ac("giant_anthill"), "biomes", "#" + ac("has_structure/giant_anthill"),
                "spawn_overrides", obj(), "step", "surface_structures", "terrain_adaptation", "none"));
        write(DATA.resolve("worldgen/structure_set/giant_anthills.json"), obj("placement", obj("type", "minecraft:random_spread",
                        "exclusion_zone", obj("chunk_count", 4, "other_set", "minecraft:villages"), "salt", 1836401827, "separation", 10, "spacing", 32),
                "structures", arr(obj("structure", ac("giant_anthill"), "weight", 1))));
        write(DATA.resolve("tags/worldgen/biome/has_structure/giant_anthill.json"), obj("values", arr("#c:is_plains", "#c:is_forest",
                "#c:is_savanna", "#c:is_jungle", "#c:is_taiga", "#c:is_desert", "#c:is_badlands", "minecraft:meadow")));
        // Its chests.
        write(DATA.resolve("loot_table/chests/giant_anthill.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem(c, 20, 2, 6), lootItem("minecraft:bone_meal", 15, 3, 8), lootItem(ac("ant_food"), 15, 2, 6),
                        lootItem("minecraft:gold_nugget", 10, 3, 9), lootItem("minecraft:iron_nugget", 10, 4, 10), lootItem(ac("test_tube"), 8, 1, 3),
                        lootItem(ac("royal_jelly"), 5, 1, 2), lootItem(ac("rally_pheromone"), 4, 1, 1), lootItem("minecraft:raw_gold", 3, 1, 2),
                        lootItem(ac("diamond_fragment"), 3, 1, 4), lootItem("minecraft:emerald", 2, 1, 2), lootItem(ac("evolution_catalyst"), 1, 1, 1)),
                        "rolls", obj("type", "minecraft:uniform", "max", 6, "min", 3)),
                obj("entries", arr(obj("type", "minecraft:empty", "weight", 3), lootItem(ac("chitin_helmet"), 1, 1, 1),
                        lootItem(ac("chitin_chestplate"), 1, 1, 1), lootItem(ac("chitin_leggings"), 1, 1, 1), lootItem(ac("chitin_boots"), 1, 1, 1),
                        lootItem(ac("chitin_pickaxe"), 1, 1, 1)), "rolls", 1)),
                "random_sequence", ac("chests/giant_anthill")));

        // The Nether anthill: the same nest dug into the Nether's floor, in blackstone and soul soil, home to Fire Ants (Warped
        // Ants in the fungus forests). Rarer than the Overworld's, and its chests hold the Nether's materials.
        write(DATA.resolve("worldgen/structure/nether_anthill.json"), obj("type", ac("nether_anthill"), "biomes", "#" + ac("has_structure/nether_anthill"),
                "spawn_overrides", obj(), "step", "surface_structures", "terrain_adaptation", "none"));
        write(DATA.resolve("worldgen/structure_set/nether_anthills.json"), obj("placement", obj("type", "minecraft:random_spread",
                        "salt", 517294613, "separation", 8, "spacing", 24),
                "structures", arr(obj("structure", ac("nether_anthill"), "weight", 1))));
        write(DATA.resolve("tags/worldgen/biome/has_structure/nether_anthill.json"), obj("values", arr("minecraft:nether_wastes",
                "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:soul_sand_valley")));
        write(DATA.resolve("loot_table/chests/nether_anthill.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem(ac("nether_ant_food"), 15, 2, 6), lootItem("minecraft:quartz", 15, 4, 12), lootItem("minecraft:gold_ingot", 12, 2, 6),
                        lootItem("minecraft:blaze_rod", 10, 1, 4), lootItem("minecraft:magma_cream", 10, 1, 4), lootItem(ac("hardened_chitin"), 10, 2, 5),
                        lootItem(ac("infernal_chitin"), 6, 1, 3), lootItem(ac("royal_jelly"), 6, 1, 3), lootItem("minecraft:ghast_tear", 4, 1, 2),
                        lootItem(ac("evolution_catalyst"), 3, 1, 2), lootItem("minecraft:ancient_debris", 1, 1, 1)),
                        "rolls", obj("type", "minecraft:uniform", "max", 6, "min", 3)),
                obj("entries", arr(obj("type", "minecraft:empty", "weight", 4), lootItem(ac("chitin_shield"), 1, 1, 1),
                        lootItem("minecraft:netherite_scrap", 1, 1, 1)), "rolls", 1)),
                "random_sequence", ac("chests/nether_anthill")));

        // Advancements.
        advancement("nether_anthill", "nether_colony", "minecraft:soul_soil", "task", "Hell's Kitchen Garden", "Find a Nether anthill",
                obj("trigger", "minecraft:location", "conditions", obj("player", obj("type", "minecraft:entity_properties", "entity", "this",
                        "predicate", obj("minecraft:location", obj("structures", ac("nether_anthill")))))));
        advancement("giant_anthill", "catch_ants", "minecraft:coarse_dirt", "task", "Down the Anthill", "Find a giant anthill",
                obj("trigger", "minecraft:location", "conditions", obj("player", obj("type", "minecraft:entity_properties", "entity", "this",
                        "predicate", obj("minecraft:location", obj("structures", ac("giant_anthill")))))));
        advancement("chitin_set", "root", "chitin_chestplate", "task", "One of Them",
                "Own a full set of Chitin armor: wild ants will take you for one of their own",
                obj("trigger", "minecraft:inventory_changed", "conditions", obj("items", arr(obj("items", ac("chitin_helmet")),
                        obj("items", ac("chitin_chestplate")), obj("items", ac("chitin_leggings")), obj("items", ac("chitin_boots"))))));
        advancement("guard_ant", "first_colony", "guard_ant", "task", "Bodyguard", "Recruit a Guard Ant: a Rally Pheromone on a Terrarium",
                hasItem("guard_ant"));

        // Names and text.
        String[][] names = {{"chitin_helmet", "Chitin Helmet"}, {"chitin_chestplate", "Chitin Chestplate"}, {"chitin_leggings", "Chitin Leggings"},
                {"chitin_boots", "Chitin Boots"}, {"chitin_sword", "Chitin Sword"}, {"chitin_pickaxe", "Chitin Pickaxe"}, {"chitin_axe", "Chitin Axe"},
                {"chitin_shovel", "Chitin Shovel"}, {"chitin_hoe", "Chitin Hoe"}, {"chitin_shield", "Chitin Shield"},
                {"rally_pheromone", "Rally Pheromone"}, {"guard_ant", "Guard Ant"}};
        for (String[] name : names) {
            LANG.put("item." + NS + "." + name[0], name[1]);
        }
        LANG.put("entity." + NS + ".guard_ant", "Guard Ant");
        LANG.put("tooltip." + NS + ".chitin_set", "Full set:");
        LANG.put("tooltip." + NS + ".chitin_set.ants", " Wild ants never attack you");
        LANG.put("tooltip." + NS + ".chitin_set.dig", " Dig %s%% faster");
        LANG.put("tooltip." + NS + ".chitin_shield", "Light: raised faster than a wooden shield, and an axe knocks it down for less time.");
        LANG.put("tooltip." + NS + ".rally_pheromone","Use on a hunter colony's Terrarium: it gives up hunters for a Guard Ant.");
        LANG.put("tooltip." + NS + ".guard_ant.empty", "An empty Guard Ant tube.");
        LANG.put("tooltip." + NS + ".guard_ant.stats", "Strength %s · Health %s/%s · Bite %s · Armor %s");
        LANG.put("tooltip." + NS + ".guard_ant.use", "Use on a block to let it out. Right-click it to make it stay or follow; sneak-right-click to put it back. Ant food heals it.");
        LANG.put("message." + NS + ".guard.stay", "Your Guard Ant stays here");
        LANG.put("message." + NS + ".guard.follow", "Your Guard Ant follows you");
        LANG.put("message." + NS + ".guard.no_queen", "This colony has no Queen to send a guard");
        LANG.put("message." + NS + ".guard.ants", "A Guard Ant takes %s hunters; this colony has %s");
        LANG.put("message." + NS + ".guard.not_hunters", "Only a hunter colony gives Guard Ants");
        LANG.put("message." + NS + ".guard.recruited", "%s hunters answered the call: a Guard Ant joins you");
    }

    static Map<String, Object> lootItem(String item, int weight, int min, int max) {
        return obj("type", "minecraft:item", "modifier", min == max && min == 1 ? null : obj("type", "minecraft:set_count",
                "count", min == max ? min : obj("type", "minecraft:uniform", "max", max, "min", min)), "name", item, "weight", weight);
    }

    // ------------------------------------------------------------------ Config screen

    /** Readable names for options whose camelCase name doesn't read well on its own. */
    static final Map<String, String> CONFIG_NAMES = Map.of(
            "ant3DModels", "3D ant models",
            "antDetailDistance", "3D ant distance",
            "maxVisualAnts", "Ants drawn per Terrarium",
            "visualDistance", "Ant draw distance",
            "rareFindMultiplier", "Rare find multiplier",
            "bonusExponent", "Bonus exponent");

    /**
     * Names, tooltips and section titles for NeoForge's configuration screen (the Config button in the Mods list), read
     * from the options and their comments in AntConfig.java, so the screen always matches the config file.
     */
    /** The info pages (the Terrarium's Species page, the book's species pages) and the book's how-to-play pages. */
    static void guideLang() {
        String[][] info = {{"climate.any", "Any climate suits it"}, {"climate.right", "The climate here suits it"},
                {"climate.wrong", "Wrong climate here: the colony loses health"}, {"climate.floor", "The block under the Terrarium makes it %s"},
                {"climate.fix", "A Terrarium on one of these suits it anywhere:"}, {"climate.under", "Place it under the Terrarium"},
                {"climate.variants", "Any of its %s variants works"},
                {"finds", "Its workers find:"}, {"farms", "Its farmers grow food (x%s) and harvest:"}, {"hunts", "Hunts at a Hunting Ground: %s"},
                {"evolution", "Evolution:"}, {"evolves_into", "Into %s"}, {"evolves_from", "From %s"},
                {"evolution.feed", "Feed the Terrarium %s"}, {"evolution.needs", "From stage %s and %s adults; %s%% a try, sure after %s tries"},
                {"chance", "%s%% of finds"}, {"from_stage", "From colony stage %s"}, {"signature", "Signature find (rare)"}};
        for (String[] entry : info) {
            LANG.put("info." + NS + "." + entry[0], entry[1]);
        }
        String[][] guide = {
                {"start", "Getting Started", String.join("\n",
                        "Craft Tweezers and some Test Tubes. Find wild ants or an Ant Nest and use the Tweezers on them: the ants go into an empty Test Tube in your inventory. Queens are rare; nests give one now and then.",
                        "Place a Terrarium, put the Queen Test Tube in, then some Ant Food. The Queen starts laying eggs at once: a Terrarium works on its own.",
                        "The Terrarium's screen shows the colony: its ants, genes, jobs and numbers, and on the Info page what its ants are like.",
                        "To move a colony, take it out in Test Tubes: an empty tube in the Queen slot takes the Queen out, empty tubes in the feed slot fill with ants (up to 1,000 each), and the Colony page's Take out buttons do the same from your inventory. Then break the Terrarium and put the tubes into a new one. Ants left inside a broken Terrarium are lost.")},
                {"chambers", "The Colony Build", String.join("\n",
                        "Chambers are additions. Put one against the Terrarium, or against a chamber already in the colony, on any side (above and below too): it joins, and the glass between them goes: the whole build becomes one glass case. A colony takes up to 32.",
                        "Nest Chamber: eggs at full speed, faster larvae, room for 3,000 adults and 1,500 larvae. Without one the Queen lays at half speed and only 60 adults fit.",
                        "Foraging Site: workers find the species' resources. It sends its finds to the Storage, and holds 9 stacks while the Storage is full.",
                        "Harvesting Site: Farmer Ants grow food and harvest there.",
                        "Hunting Ground: hunter species (Army Ants and the ants that evolve into hunters) kill mobs there: the mobs' loot, and food from what they catch.",
                        "Storage: more room for finds, and food room.",
                        "Queen Chamber: raises new Queens.",
                        "A chamber exactly as near to two Terrariums joins neither.")},
                {"climate", "Climate", String.join("\n",
                        "Every species likes a climate: temperate, cold, hot, underground or nether. In the wrong one the colony loses health, less with good Environmental Resistance.",
                        "A block under the Terrarium fixes it: snow or ice make it cold, sand or sandstone hot, grass or dirt temperate, stone or deepslate underground, netherrack or magma nether. The Terrarium's Info page shows the blocks for your colony.")},
                {"food", "Food", String.join("\n",
                        "Ants eat. Food goes in through the Terrarium's feed slot, by hand or from a hopper: Ant Food, Royal Jelly, sugar, sweet berries and other plain foods. The Terrarium holds 2,000 food; each Storage adds 25,000.",
                        "Mineral and crystal species need their own food for their eggs. A starving colony works slowly, then loses ants.",
                        "Farmer Ants at a Harvesting Site grow food themselves.")},
                {"jobs", "Jobs", String.join("\n",
                        "Workers go to Harvesting Sites and Hunting Grounds first (they bring food), then to Foraging Sites. A twentieth of the adults stay home to look after the nest.",
                        "Each site takes up to 256 ants, more with Population Upgrades. Its screen sets how many it wants; Auto takes all it can.",
                        "The more ants on a job, the more they find, a little less for each extra ant. Only Farmer species farm, and only hunter species hunt: the Info page says which.")},
                {"evolution", "Evolution", String.join("\n",
                        "Queens evolve into new species: feed the Terrarium the right material once the colony is big enough (the Info page lists it). A try may fail, but one always works after enough tries. An Evolution Catalyst helps.",
                        "A Queen Chamber raises daughter Queens from the colony's Queen; with a Genetic Sample of another species it can raise a hybrid.")},
                {"genetics", "Genetics", String.join("\n",
                        "Each Queen has ten traits from 1 to 10, and daughters inherit them with small changes. Mutations add strengths, or flaws.",
                        "The Genetic Laboratory raises one trait at a time, rerolls, mutates and purges flaws. It runs on FE: put a Generator next to it, or any mod's cable.")},
                {"upgrades", "Upgrades", String.join("\n",
                        "Upgrades go in a screen's upgrade slots; shift-clicking one puts it there. Each block takes the kinds it can use: the slot's tooltip, and JEI or EMI, tell which.",
                        "The Colony Upgrade Station makes species upgrades from an Upgrade Base and a species' signature material. They work only for that species, in Foraging Sites.")},
                {"automation", "Automation", String.join("\n",
                        "Hoppers and pipes can feed the Terrarium and Queen Chambers, and empty Storage and Foraging Sites. A Storage with an Automation Upgrade pushes its finds into the container under it.",
                        "Comparators read a Terrarium's food, a site's workforce, or how full a store is. A redstone signal pauses a site: its ants go elsewhere.")}};
        for (String[] page : guide) {
            LANG.put("guide." + NS + "." + page[0] + ".title", page[1]);
            LANG.put("guide." + NS + "." + page[0] + ".text", page[2]);
        }
        LANG.put("gui." + NS + ".page.species", "Info");
    }

    static void configLang() {
        Path source = Path.of("common/src/main/java/com/antcolonies/core/config/AntConfig.java");
        String text;
        try {
            text = Files.readString(source);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        java.util.regex.Matcher option = java.util.regex.Pattern.compile(
                "\\.comment\\(((?:\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*,?)+)\\)\\s*\\.define\\w*\\(\\s*\"(\\w+)\"").matcher(text);
        java.util.regex.Pattern part = java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        int options = 0;
        while (option.find()) {
            String name = option.group(2);
            StringBuilder comment = new StringBuilder();
            java.util.regex.Matcher pieces = part.matcher(option.group(1));
            while (pieces.find()) {
                comment.append(comment.isEmpty() ? "" : " ").append(pieces.group(1).replace("\\\"", "\""));
            }
            String key = NS + ".configuration." + name;
            if (LANG.containsKey(key)) {
                throw new IllegalStateException("Two config options are called " + name + ": their screen names would clash");
            }
            LANG.put(key, CONFIG_NAMES.getOrDefault(name, words(name)));
            LANG.put(key + ".tooltip", comment.toString());
            options++;
        }
        java.util.regex.Matcher section = java.util.regex.Pattern.compile("\\.push\\(\"(\\w+)\"\\)").matcher(text);
        while (section.find()) {
            String title = words(section.group(1));
            LANG.put(NS + ".configuration.section." + section.group(1), title);
            LANG.put(NS + ".configuration.section." + section.group(1) + ".title", "Ant Colonies: " + title);
        }
        LANG.put(NS + ".configuration.title", "Ant Colonies Configuration");
        if (options < 60) {
            throw new IllegalStateException("Only " + options + " config options found in " + source + ": did its format change?");
        }
    }

    /** "offlineCatchUpMinutes" -> "Offline catch up minutes". */
    static String words(String camel) {
        String spaced = camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static void lang() {
        Map<String, String> l = LANG;
        l.put("itemGroup." + NS, "Ant Colonies");
        String[][] items = {{"tweezers", "Tweezers"}, {"test_tube", "Test Tube"}, {"ant_test_tube", "Ant Test Tube"}, {"queen_test_tube", "Queen Test Tube"},
                {"genetic_sample", "Genetic Sample"}, {"ant_egg", "Ant Eggs"}, {"ant_larva", "Ant Larvae"}, {"ant_food", "Ant Food"},
                {"mineral_ant_food", "Mineral Ant Food"}, {"crystal_ant_food", "Crystal Ant Food"}, {"nether_ant_food", "Nether Ant Food"},
                {"royal_jelly", "Royal Jelly"}, {"nuptial_pheromone", "Nuptial Pheromone"}, {"evolution_catalyst", "Evolution Catalyst"},
                {"reroll_serum", "Genetic Reroll Serum"}, {"chitin_plate", "Chitin Plate"}, {"hardened_chitin", "Hardened Chitin"}, {"infernal_chitin", "Infernal Chitin"}, {"void_chitin", "Void Chitin"}, {"upgrade_base", "Upgrade Base"},
                {"genetic_circuit", "Genetic Circuit"}, {"crystal_core", "Crystal Core"}, {"ant_identification_book", "Ant Identification Book"},
                {"colony_scanner", "Colony Scanner"}, {"genetic_analyzer", "Genetic Analyzer"}, {"species_upgrade", "Species Upgrade"},
                {"diamond_fragment", "Diamond Fragment"}, {"emerald_fragment", "Emerald Fragment"}, {"wild_ant_spawn_egg", "Wild Ant Spawn Egg"},
                {"ant_head", "Ant Colonies"}};
        for (String[] item : items) {
            l.put("item." + NS + "." + item[0], item[1]);
        }
        l.put("item." + NS + ".ant_test_tube.of", "Test Tube of %s (%s)");
        l.put("item." + NS + ".queen_test_tube.of", "%s Queen");
        l.put("item." + NS + ".genetic_sample.of", "Genetic Sample (%s)");
        l.put("item." + NS + ".ant_egg.of", "%s Eggs");
        l.put("item." + NS + ".ant_larva.of", "%s Larvae");
        l.put("item." + NS + ".species_upgrade.named", "%s %s");
        String[] numerals = {"I", "II", "III", "IV"};
        String[][] upgradeNames = {{"speed", "Speed"}, {"food_efficiency", "Food Efficiency"}, {"simulation", "Simulation"}, {"storage", "Storage"},
                {"breeding", "Breeding"}, {"genetic", "Genetic"}, {"hunting", "Hunting"}, {"automation", "Automation"}, {"population", "Population"},
                {"evolution", "Evolution"}};
        for (String[] u : upgradeNames) {
            for (int t = 1; t <= 4; t++) {
                l.put("item." + NS + "." + u[0] + "_upgrade_" + t, "Colony " + u[1] + " Upgrade " + numerals[t - 1]);
            }
        }
        String[][] blocks = {{"terrarium", "Terrarium"}, {"nest_chamber", "Nest Chamber"}, {"storage", "Storage"},
                {"foraging_site", "Foraging Site"}, {"harvesting_site", "Harvesting Site"}, {"hunting_ground", "Hunting Ground"},
                {"queen_chamber", "Queen Chamber"}, {"upgrade_station", "Colony Upgrade Station"}, {"generator", "Generator"},
                {"genetic_laboratory", "Genetic Laboratory"}};
        for (String[] b : blocks) {
            l.put("block." + NS + "." + b[0], b[1]);
        }
        for (String nest : NESTS) {
            l.put("block." + NS + "." + nest + "_ant_nest", title(nest) + " Ant Nest");
            l.put("tooltip." + NS + "." + nest + "_ant_nest", "Place it and ants move in: they tend and harvest crops within 10 blocks and bring the harvest "
                    + "home, for hoppers and pipes to take out.");
        }
        l.put("entity." + NS + ".wild_ant", "Wild Ant");
        // Tooltips of blocks and items.
        String[][] tips = {
                {"terrarium", "The heart of a colony. Put a Queen in and feed her. Chambers placed against it, or against its chambers, join the colony. Take the Queen and ants out in Test Tubes before breaking it: what is left inside is lost."},
                {"nest_chamber", "Brood and housing: eggs at full speed, faster larvae, room for 3,000 adults and 1,500 larvae."},
                {"storage", "More room for what the colony finds, and food room. Hoppers can empty it; Storage Upgrades open more slots."},
                {"foraging_site", "Workers find their species' resources. Sends finds to Storage; holds 9 stacks when it is full."},
                {"hunting_ground", "Hunter species kill mobs here: the mobs' loot (from their loot tables; none spawn), and food from the catch."},
                {"harvesting_site", "Farmer Ants grow food here, plus mushrooms, seeds and berries."},
                {"queen_chamber", "Raises new Queens from the colony's Queen."},
                {"upgrade_station", "Makes species upgrades from an Upgrade Base and a species' signature material."},
                {"generator", "Burns furnace fuel into FE and gives it to the machines next to it, such as the Genetic Laboratory."},
                {"genetic_laboratory", "Upgrades, rerolls, mutates and purges a Queen's genes. Runs on FE: put a Generator next to it."},
                {"tweezers", "Catches wild ants into empty Test Tubes in your inventory. Also works on wild nests."},
                {"test_tube", "Holds caught ants. Keep some in your inventory when using Tweezers."},
                {"ant_food", "Basic colony food (200 food)."}, {"mineral_ant_food", "Food for mineral species; their eggs need it."},
                {"crystal_ant_food", "Food for crystal species (Amethyst, Diamond, Emerald)."}, {"nether_ant_food", "Food for Netherite Ants."},
                {"royal_jelly", "Rich food (400) and the base of Queen rearing."}, {"nuptial_pheromone", "Needed to rear Queens of advanced species."},
                {"evolution_catalyst", "Feed to a Terrarium: doubles the chance of the next evolution attempt."},
                {"reroll_serum", "Genetic Laboratory: reroll a trait (never lower) or purge a harmful mutation."},
                {"chitin_plate", "Upgrade material. Wild ants sometimes leave one."},
                {"hardened_chitin", "Chitin baked hard in a Blast Furnace. For tier II upgrades and the Genetic Laboratory."},
                {"infernal_chitin", "Hardened Chitin tempered with the Nether: for tier III upgrades and high genetics."},
                {"void_chitin", "Infernal Chitin steeped in the End: for the highest genetics and the Dragon Ant."}, {"upgrade_base", "The blank every upgrade starts from."},
                {"genetic_circuit", "Genetic Laboratory material."}, {"crystal_core", "High-level Genetic Laboratory material."},
                {"ant_identification_book", "A field guide to every ant species."}, {"diamond_fragment", "Nine make a diamond."},
                {"emerald_fragment", "Nine make an emerald."}, {"terrarium.colony", "Colony: %s"},
                {"terrarium.queen", "Queen inside"}, {"terrarium.no_queen", "No Queen"},
        };
        for (String[] tip : tips) {
            if (!tip[1].isEmpty()) {
                l.put("tooltip." + NS + "." + tip[0], tip[1]);
            }
        }
        l.put("tooltip." + NS + ".tweezers.terrarium", "On a Terrarium: take workers (sneak: eggs) to start another colony.");
        l.put("tooltip." + NS + ".colony_scanner", "Right-click a Terrarium or colony structure for a report.");
        l.put("tooltip." + NS + ".genetic_analyzer", "Right-click a Terrarium, or use with a Queen in the other hand.");
        l.put("tooltip." + NS + ".ant_test_tube.count", "%s %s");
        l.put("tooltip." + NS + ".ant_test_tube.use", "Put into a Terrarium of the same species (or an empty one).");
        l.put("tooltip." + NS + ".queen_test_tube.empty", "Empty");
        l.put("tooltip." + NS + ".genetic_sample.use", "Cross into a daughter in a Queen Chamber's sample slot.");
        l.put("tooltip." + NS + ".brood.use", "Put into a Terrarium of the same species: each adds 4.");
        l.put("tooltip." + NS + ".queen.name", "Queen %s");
        l.put("tooltip." + NS + ".queen.species", "Species: %s");
        l.put("tooltip." + NS + ".queen.genetics", "Genetics: %s (average %s)");
        l.put("tooltip." + NS + ".queen.generation", "Generation %s");
        l.put("tooltip." + NS + ".hold_shift", "Hold Shift for genetics");
        l.put("tooltip." + NS + ".upgrade.tier", "Tier %s");
        // What each colony upgrade does, in numbers (UpgradeEffects), on its tooltip and its JEI/EMI page.
        l.put("tooltip." + NS + ".upgrade.goes_in", "Goes in: %s");
        l.put("tooltip." + NS + ".upgrade.adds_up", "Two of a kind in one block add up.");
        l.put("jei." + NS + ".colony_upgrade", "Colony Upgrades");
        l.put("jei." + NS + ".goes_in", "Goes in here");
        // A heading per place, then short values under it, like vanilla's "When in Main Hand:" lines.
        String[][] places = {{"sites", "In job sites:"}, {"nest", "In Nest Chambers:"}, {"queen", "In Queen Chambers:"},
                {"lab", "In the Genetic Lab:"}, {"terrarium", "In the Terrarium:"}, {"terrarium_or_site", "In the Terrarium or a job site:"},
                {"terrarium_and_nest", "In the Terrarium and Nest Chambers:"}, {"storage", "In Storage:"}, {"hunting", "In Hunting Grounds:"}};
        for (String[] place : places) {
            l.put("tooltip." + NS + ".upgrade_place." + place[0], place[1]);
        }
        String[][] effects = {
                {"speed.sites", "+%s%% work speed"},
                {"speed.larvae", "+%s%% larvae growth"},
                {"speed.eggs", "+%s%% egg laying"},
                {"speed.queen", "+%s%% Queen rearing"},
                {"speed.lab", "%sx lab speed"},
                {"food_efficiency", "-%s%% food eaten"},
                {"simulation", "%s of offline catch-up"},
                {"storage.slots", "%s slots (+%s)"},
                {"storage.stacks", "%s stacks per slot"},
                {"storage.food", "+%s food room"},
                {"breeding.eggs", "+%s%% egg laying"},
                {"breeding.queen", "+%s%% Queen rearing"},
                {"genetic.lab", "-%s%% material cost"},
                {"genetic.queen", "+%s%% mutation chance"},
                {"hunting", "+%s%% hunting strength"},
                {"automation", "Pushes %s items/s down"},
                {"population.terrarium", "+%s adults housed"},
                {"population.nest", "+%s%% room"},
                {"population.sites", "+%s ants each"},
                {"evolution", "+%s%% evolution chance"}};
        for (String[] effect : effects) {
            l.put("tooltip." + NS + ".upgrade_effect." + effect[0], effect[1]);
        }
        String[][] upgradeTips = {{"speed", "Faster work in chambers and the lab, faster larvae."}, {"food_efficiency", "Less food eaten."},
                {"simulation", "Terrarium: more offline catch-up (numbers only, no entities)."}, {"storage", "Storage: more slots and more food room."},
                {"breeding", "Faster egg laying and Queen rearing."}, {"genetic", "Cheaper lab work; more mutations in Queen Chambers."},
                {"hunting", "Hunting Ground: stronger hunters, more catches, fewer losses."}, {"automation", "Storage: pushes its finds into the container below."},
                {"population", "More ants housed, or more working in one chamber."}, {"evolution", "Terrarium: better evolution chances."}};
        for (String[] u : upgradeTips) {
            l.put("tooltip." + NS + ".upgrade." + u[0], u[1]);
        }
        l.put("tooltip." + NS + ".species_upgrade.blank", "Made at the Colony Upgrade Station.");
        l.put("tooltip." + NS + ".species_upgrade.for", "For %s colonies only");
        l.put("tooltip." + NS + ".species_upgrade.extraction", "+%s%% finds in Foraging Sites");
        l.put("tooltip." + NS + ".species_upgrade.processing", "Finds come processed (nuggets to ingots, fragments to gems), +%s%%");
        l.put("tooltip." + NS + ".species_upgrade.efficiency", "Foragers eat less; signature finds more often (%s%%)");
        // Castes, categories, climates, rarity, grades, traits.
        l.put("caste." + NS + ".worker", "Workers");
        l.put("caste." + NS + ".soldier", "Soldiers");
        l.put("caste." + NS + ".queen", "Queen");
        for (String c : new String[] {"base", "farmer", "mineral", "hybrid", "endgame", "hunter"}) {
            l.put("category." + NS + "." + c, title(c));
        }
        for (String c : new String[] {"temperate", "cold", "hot", "underground", "nether", "any"}) {
            l.put("climate." + NS + "." + c, title(c));
        }
        String[] rarity = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Perfect"};
        for (int i = 0; i < rarity.length; i++) {
            l.put("rarity." + NS + "." + i, rarity[i]);
        }
        String[] grades = {"Basic", "Improved", "Enhanced", "Advanced", "Rare", "Superior", "Exceptional", "Elite", "Near-perfect", "Perfect"};
        for (int i = 0; i < grades.length; i++) {
            l.put("grade." + NS + "." + (i + 1), grades[i]);
        }
        String[][] traits = {{"worker_productivity", "Worker Productivity"}, {"gathering_efficiency", "Gathering Efficiency"},
                {"food_efficiency", "Food Efficiency"}, {"egg_production", "Egg Production"}, {"larvae_growth", "Larvae Growth"},
                {"soldier_strength", "Hunting Strength"}, {"farmer_productivity", "Farmer Productivity"}, {"mutation_probability", "Mutation Probability"},
                {"environmental_resistance", "Environmental Resistance"}, {"specialization", "Specialization"}};
        for (String[] t : traits) {
            l.put("trait." + NS + "." + t[0], t[1]);
        }
        String[][] traitShort = {{"worker_productivity", "Productivity", "More finds per worker"}, {"gathering_efficiency", "Gathering", "Extra items per find"},
                {"food_efficiency", "Food Use", "The whole colony eats less"}, {"egg_production", "Egg Laying", "The Queen lays more eggs"},
                {"larvae_growth", "Growth", "Larvae become adults faster"}, {"soldier_strength", "Hunting", "More hunts, fewer losses"},
                {"farmer_productivity", "Farming", "More food per farmer"}, {"mutation_probability", "Mutation", "Mutations, evolutions, hybrids"},
                {"environmental_resistance", "Resistance", "Bears wrong climates and hunger"}, {"specialization", "Specialty", "More signature finds"}};
        for (String[] t : traitShort) {
            l.put("trait." + NS + "." + t[0] + ".short", t[1]);
            l.put("trait." + NS + "." + t[0] + ".desc", t[2]);
        }
        // Statuses.
        String[][] statuses = {{"no_queen", "No Queen: no eggs are laid"}, {"no_food", "No food"}, {"no_workers", "No workers for the jobs"},
                {"missing_connection", "Not part of a colony: put it against a Terrarium or its chambers"},
                {"storage_full", "Storage full"},
                {"incompatible_species", "Incompatible species"}, {"insufficient_materials", "Insufficient materials (or colony not ready)"},
                {"two_colonies", "As near to two Terrariums: it joins neither"}, {"unknown_species", "Unknown species (missing data)"},
                {"no_storage", "No Storage or Foraging Site: the harvest has nowhere to go"}, {"no_special_food", "Eggs lack their special food"},
                {"climate", "Wrong climate for this species"},
                {"low_health", "Colony health is low"}, {"no_nest_chamber", "No Nest Chamber: slow eggs, little room"},
                {"population_full", "No room for more adults"}, {"starving", "Starving!"},
                {"working", "Working"}, {"paused", "Paused by a redstone signal"},
                {"wrong_dimension", "This evolution happens only in another dimension (see the Info page)"}};
        for (String[] s : statuses) {
            l.put("status." + NS + "." + s[0], s[1]);
        }
        // GUI.
        String[][] gui = {{"page.overview", "Colony"}, {"page.genetics", "Genes"}, {"page.jobs", "Jobs"}, {"page.production", "Stats"},
                {"stage", "Stage %s"}, {"queen", "Queen %s"}, {"workers", "Workers: %s"}, {"larvae", "Larvae: %s"},
                {"eggs", "Eggs: %s"}, {"housing", "Adults %s / %s housed"}, {"food", "Food"}, {"health", "Health"},
                
                {"slot.queen", "Queen slot"}, {"slot.queen.hint", "A Queen Test Tube starts the colony; an empty Test Tube takes the Queen out"},
                {"slot.feed", "Feed slot"}, {"slot.feed.hint", "Food, ants in Test Tubes, eggs, larvae or an Evolution Catalyst; empty Test Tubes fill with ants"},
                {"slot.output", "Output"}, {"slot.output.hint", "Empty Test Tubes and leftovers come out here"},
                {"slot.upgrade", "Upgrade slot"}, {"slot.upgrade.hint", "Population, Simulation, Evolution, Food Efficiency or Breeding upgrades"}, {"slot.structure_upgrade.hint", "Upgrades this chamber accepts (see JEI or EMI, or the item tooltip)"},
                {"transforming", "Transforming from %s: %s%%"}, {"genetics_of", "%s, generation %s"}, {"no_mutations", "No mutations"},
                {"mutations", "Mutations:"}, {"jobs_header", "Working sites - %s ants home"}, {"job_workers", "%s / %s ants"},
                {"no_jobs", "No Foraging Site, Harvesting Site or Hunting Ground"},
                {"auto", "Auto"}, {"auto.hint", "Auto: as many ants as the chamber holds"}, {"per_minute", "/min"}, {"less_10", "-10"}, {"less_1", "-1"},
                {"more_1", "+1"}, {"more_10", "+10"}, {"farm.hint", "Needs Farmer Ants (or a species that farms)"},
                {"stat.eggs", "Eggs laid"}, {"stat.raised", "Adults raised"}, {"stat.food_in", "Food in"}, {"stat.food_from_storage", " of it from Storage"}, {"stat.food_out", "Food eaten"},
                {"stat.finds", "Finds"}, {"stat.hunts", "Hunts"}, {"stat.deaths", "Deaths"},
                {"stat.larvae_room", "Larvae room"}, {"stat.structures", "Chambers"}, {"stat.found_total", "Found in total"},
                {"evolution_attempts", "Evolution attempts: %s, catalysts: %s"}, {"upgrades", "Upgrades"}, {"linked", "Part of a colony"}, {"not_linked", "Not joined to a Terrarium"},
                {"contested", "As near to two Terrariums: joins neither"},
                {"site.workers", "Workers %s/%s (want %s)"}, {"site.hunters", "Hunters %s/%s (want %s)"},
                {"site.farmers", "Farmers %s/%s (want %s)"}, {"site.rate", "%s finds per minute"},
                {"site.hunt_rate", "%s hunts per minute"}, {"site.food_rate", "%s food per minute"}, {"site.store", "Finds wait here while the Storage is full"},
                {"storage.slots", "%s slots"}, {"storage.upgrade_held", "Stuck: take out what won't fit without it"},
                {"queen_chamber.slots", "Rears a new Queen"}, {"queen_chamber.time", "Takes %s s at full speed"},
                {"slot.qc_tube", "Test Tube slot"}, {"slot.qc_tube.hint", "An empty Test Tube to hold the new Queen"},
                {"slot.qc_sample", "Sample slot (optional)"}, {"slot.qc_sample.hint", "A Genetic Sample can cross a hybrid into the daughter"},
                {"slot.qc_output", "New Queen"}, {"slot.qc_output.hint", "The finished Queen Test Tube comes out here"},
                {"queen_chamber.needs", "Rearing needs:"}, {"lab.queen", "Queen"}, {"lab.materials", "Materials"}, {"lab.fuel", "Fuel"},
                {"lab.range", "Within 2 blocks of the controller, 1 up or down."}, {"lab.insert_queen", "Insert a Queen Test Tube"},
                {"lab.upgrade", "Upgrade"}, {"lab.reroll", "Reroll"}, {"lab.mutate", "Mutate"}, {"lab.sample", "Copy"}, {"lab.purge", "Purge"},
                {"lab.reroll.hint", "Reroll Serum in the catalyst slot: rerolls the trait, never lower and never above level 6"},
                {"lab.mutate.hint", "A catalyst in the catalyst slot gives its mutation"},
                {"lab.sample.hint", "Empty Test Tube in the catalyst slot: copy her genes into a Genetic Sample"},
                {"lab.purge.hint", "Reroll Serum in the catalyst slot: removes a harmful mutation"}, {"lab.cancel", "Cancel (nothing is used)"},
                {"lab.level", "Level %s (%s, %s)"}, {"lab.next", "Next: level %s (%s, %s)"}, {"lab.max", "At its maximum"}, {"lab.energy", "Energy: %s"},
                {"lab.energy_stored", "Energy: %s / %s FE"}, {"station.extraction", "Extract"}, {"station.processing", "Process"},
                {"station.efficiency", "Efficient"}, {"station.extraction.hint", "More finds"}, {"station.processing.hint", "Finds come processed"},
                {"station.efficiency.hint", "Less food, more signature finds"},
                {"station.hint", "Base or upgrade + 4/8/16 signature items"},
                {"lab.title", "Genetic Laboratory"}, {"lab.online", "ONLINE"}, {"lab.offline", "NO POWER"}, {"lab.no_queen", "No specimen"},
                {"lab.insert", "Add a Queen"}, {"lab.energy_label", "Energy"}, {"lab.level_short", "Lv %s"}, {"lab.cancel_button", "Cancel"},
                {"lab.max_button", "Perfected"},
                {"lab.catalyst_gives", "This catalyst gives: %s"}, {"lab.working.upgrade", "Raising %s... %s%%"}, {"lab.working.reroll", "Rerolling %s... %s%%"},
                {"lab.working.mutate", "Inducing a mutation... %2$s%%"}, {"lab.working.purge", "Purging a harmful mutation... %2$s%%"},
                {"lab.working.sample", "Copying her genes... %2$s%%"},
                {"lab.hint", "Select a trait, put its materials below, then Upgrade"}, {"lab.trait_hint", "%s: %s"}, {"lab.time", "Takes %s s"}, {"lab.have", "Have %s, need %s"},
                {"lab.materials_hint", "Materials go in the three slots under the costs"},
                {"lab.power_hint", "Runs on FE: put a Generator, or any mod's cable, next to it"},
                {"lab.mutation_cap", "This Queen already carries %s beneficial mutations, the most one can (flaws don't count)"},
                {"generator.making", "Making %s FE/t"}, {"generator.full", "Full: waiting for a machine"}, {"generator.idle", "Idle: needs fuel"},
                {"generator.fuel", "Fuel slot"}, {"generator.fuel.hint", "Anything a furnace burns, for as long as it burns there"},
                {"generator.push", "Gives its energy to the machines and cables next to it"},
                {"generator.hint", "Place it next to the Genetic Laboratory"},
                {"lab.catalyst_hint", "Catalyst: Reroll Serum, a mutation catalyst, or an empty Test Tube"},
                {"lab.upgrade_slot_hint", "Speed or Genetic Upgrade"},
                {"effect.nest_chamber", "Eggs at full speed and faster larvae. Room for 3,000 adults and 1,500 larvae."},
                {"nest.adults", "Room for %s adults"}, {"nest.larvae", "Room for %s larvae"},
                {"take", "Take out:"}, {"take.workers", "Workers"},
                {"take.hint", "Takes up to %s ants into an empty Test Tube from your inventory"},
                {"take.move", "Use the tube on another Terrarium to add them."},
                {"take.whole", "Breaking the Terrarium loses the ants still in it."},
                {"nest.upgraded", "+%s%% from Population Upgrades"}, {"nest.upgrade_hint", "Population Upgrades: +50%% room per tier"},
                {"effect.storage", ""}};
        for (String[] g : gui) {
            if (!g[1].isEmpty()) {
                l.put("gui." + NS + "." + g[0], g[1]);
            }
        }
        // Messages, scanner, analyzer, book.
        String[][] messages = {{"message." + NS + ".tweezers.no_tube", "You need an empty Test Tube"},
                {"message." + NS + ".tweezers.caught", "Caught: %s"}, {"message." + NS + ".tweezers.no_eggs", "Not enough eggs"},
                {"message." + NS + ".tweezers.no_workers", "No spare workers"}, {"message." + NS + ".nest.recovering", "The nest is still recovering"},
                {"scanner." + NS + ".colony", "%s colony, stage %s"}, {"scanner." + NS + ".population", "%s workers, %s larvae, %s eggs"},
                {"scanner." + NS + ".food", "Food %s / %s (+%s, -%s per minute)"}, {"scanner." + NS + ".health", "Health %s%%, %s chambers"},
                {"scanner." + NS + ".queen", "Queen %s (%s)"}, {"scanner." + NS + ".linked", "Linked to the Terrarium at %s, %s, %s"},
                {"scanner." + NS + ".site", "%s / %s ants, %s per minute"}, {"analyzer." + NS + ".header", "Genetic analysis"},
                {"analyzer." + NS + ".lineage", "Lineage: %s"}, {"book." + NS + ".kind", "%s, tier %s, %s"}, {"book." + NS + ".wild", "Wild in: %s"},
                {"book." + NS + ".not_wild", "Not found in the wild"}, {"book." + NS + ".diet", "Eats plain food"},
                {"book." + NS + ".diet_special", "Eggs need %s food"}, {"book." + NS + ".finds", "Finds:"}, {"book." + NS + ".farms", "Grows:"},
                {"book." + NS + ".evolution", "%s -> %s (%s%%)"}};
        for (String[] m : messages) {
            l.put(m[0], m[1]);
        }
        // JEI.
        String[][] jei = {{"species", "Ant Species"}, {"breeding", "Queen Rearing"}, {"evolution", "Queen Evolution"}, {"mutation", "Genetic Mutation"},
                {"hybrid", "Hybrid Species"}, {"eggs", "Egg Production"}, {"larvae", "Larvae Development"}, {"production", "Resource Production"},
                {"farming", "Farmer Ant Production"}, {"fighting", "Hunting Loot"}, {"lab", "Genetic Laboratory"},
                {"trait", "Genetic Upgrades"}, {"species_upgrade", "Species Upgrades"}, {"hybrid_of", "%s x %s = %s"}, {"effect.production", "Production %s"}, {"effect.gathering", "Gathering %s"},
                {"effect.food", "Food use %s"}, {"effect.eggs", "Eggs %s"}, {"effect.growth", "Growth %s"}, {"effect.soldiers", "Hunting %s"},
                {"effect.farming", "Farming %s"}, {"effect.special", "Signature finds %s"}, {"effect.cold_resistant", "Cold resistant"},
                {"effect.heat_resistant", "Heat resistant"}, {"chance", "Chance: %s%% per attempt"},
                {"guaranteed", "Guaranteed after %s attempts"}, {"needs_stage", "Colony stage %s, %s adults"}, {"time", "%s s"},
                {"weight", "Weight %s"}, {"stage", "From stage %s"}, {"special", "Signature find"}, {"climate", "Climate: %s"},
                {"diet", "Eggs need %s food (%s each)"}, {"rate", "%s finds/min per worker"},
                {"food_rate", "%s food/min per farmer"}, {"strength", "Hunting strength x%s"},
                {"loot", "Drops from this mob's own loot table"}, {"hybrid_chance", "Chance: %s%%, both parents average level %s+"},
                {"catalyst", "Catalyst in the Mutation Chamber"}, {"random", "Random only (weight %s)"}, {"negative", "Harmful"},
                {"level_step", "Level %s -> %s"}, {"energy", "%s FE"}, {"eggs_info", "%s eggs/min, hatch in %s min"},
                {"larvae_info", "Grows in %s min (species x%s)"}, {"needs", "Needs:"}, {"structures", "Structures:"},
                {"material_count", "%s signature items"}, {"tier", "Tier %s"}};
        for (String[] j : jei) {
            l.put("jei." + NS + "." + j[0], j[1]);
        }
        Map<String, Object> out = new LinkedHashMap<>(l);
        write(ASSETS.resolve("lang/en_us.json"), out);
    }

    public static void main(String[] args) {
        species();
        evolutions();
        hybrids();
        mutations();
        commonTags();
        advancements();
        traitUpgrades();
        foodMap();
        recipes();
        loot();
        tags();
        worldgen();
        blockstates();
        customModels();
        items();
        newFeatures();
        configLang();
        guideLang();
        particles();
        sounds();
        lang();
        System.out.println("Wrote " + written + " files.");
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
