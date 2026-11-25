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
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes every JSON data and asset file of Ant Colonies, and the English language file.
 * Run from the project root with JDK 25: {@code java tools/DataGen.java}. Edit this file, not the JSON it writes.
 */
public class DataGen {
    static final String NS = "antcolonies";
    static final Path RES = Path.of("common/src/generated/resources");
    static final Path DATA = RES.resolve("data/" + NS);
    static final Path ASSETS = RES.resolve("assets/" + NS);
    static final Map<String, String> LANG = new TreeMap<>();
    static int written;

    // --- JSON ---

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

    // --- Species ---

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
            return obj(tag ? "tag" : "item", tag ? item : mc(item), "count", count == 1 ? null : count,
                    "weight", weight == Math.rint(weight) ? (Object) (int) weight : (Object) weight,
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
     * Another mod's items that a species names outright (most go through c: tags instead). Its files load only while each
     * of them is registered: a renamed item then takes the species away, where a missing one would stop the world loading.
     * The condition is the mod's own "antcolonies:registered", never "neoforge:registered": a client reading the files
     * from its own jar cannot test NeoForge's, and would drop a species the server has.
     */
    static final Map<String, List<String>> NEEDS_ITEMS = new LinkedHashMap<>();

    /** The load conditions of a species of {@link #NEEDS_MODS}, for its own file and for its evolution and research. */
    static List<Object> speciesConditions(String id) {
        List<Object> all = conditions(NEEDS_MODS.get(id));
        for (String item : NEEDS_ITEMS.getOrDefault(id, List.of())) {
            all.add(obj("type", ac("registered"), "registry", "minecraft:item", "value", item));
        }
        return all;
    }

    /**
     * A mineral species for a material of one mod or a few: {@code look} is body, gaster, accent, pattern and crystal
     * (null for none), {@code food} the special food its eggs need (mineral, crystal or nether).
     */
    static void pack(String id, String name, int tier, String climate, String[] look, boolean glow, String food, double rate, List<Out> outputs,
                     List<String> mods, String description) {
        SPECIES.add(new Species(id, name, tier, "mineral", climate, null, look[0], look[1], look[2], look[3], look[4], glow, 1.0, 1.2 + tier * 0.1,
                food, 0.3 + tier * 0.1, rate, outputs, 0, List.of(), 1.0, List.of(),
                List.of(new String[] {ac("royal_jelly"), tier >= 3 ? "4" : "3"}, new String[] {ac(food + "_ant_food"), "6"}),
                3600 + tier * 1200, 0.9, 0.9, null, name.replace(" Ant", "") + " Extraction", "Ore Sorting", name.replace(" Ant", "") + " Efficiency",
                description));
        NEEDS_MODS.put(id, mods);
        List<String> named = outputs.stream().filter(o -> !o.tag() && o.item().contains(":") && !o.item().startsWith("minecraft:"))
                .map(Out::item).toList();
        if (!named.isEmpty()) {
            NEEDS_ITEMS.put(id, named);
        }
    }

    /**
     * A metal species for another mod's ore: it finds through the c: tags, so it works with whichever mod provides the metal.
     * {@code difficulty} is how hard the metal is to get in a big pack: 1 like iron, 2 like gold, 3 hard ({@link #HARD_RATE}),
     * 4 like diamond ({@link #DIAMOND_RATE}).
     */
    static void compat(String id, String name, int difficulty, String climate, String body, String gaster, String accent, String pattern, String crystal,
                       boolean glow, String metal, List<String> mods, String description) {
        int tier = Math.min(difficulty, 3);
        double rate = difficulty == 1 ? 0.6 : difficulty == 2 ? 0.45 : difficulty == 3 ? HARD_RATE : DIAMOND_RATE;
        // A hard metal's raw ore comes from the start, or a young colony would find only stone. Uranium's too: MachinaOpus has
        // no uranium nuggets or ingots.
        int rawStage = difficulty >= 3 || id.equals("uranium") ? 1 : 2;
        // A metal like diamond comes nearly all as nuggets, nine finds to an ingot.
        double raw = difficulty >= 4 ? 3 : 10;
        SPECIES.add(new Species(id, name, tier, "mineral", climate, null, body, gaster, accent, pattern, crystal, glow, 1.05, 1.2 + tier * 0.15,
                "mineral", 0.3 + tier * 0.15, rate,
                List.of(Out.of("c:nuggets/" + metal, 40).asTag(), Out.of("c:raw_materials/" + metal, raw).atStage(rawStage).asTag(), Out.of("cobblestone", 15),
                        Out.of("c:ingots/" + metal, 2).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {ac("mineral_ant_food"), "4"}),
                3600 + tier * 1200, 0.9, 0.9, null, title(metal) + " Extraction", "Metal Processing", title(metal) + " Efficiency", description));
        NEEDS_MODS.put(id, mods);
    }

    /** A crystal species for another mod's gem: the gem and its dust through the c: tags, like the Fluorite Ant. */
    static void gem(String id, String name, String body, String gaster, String accent, String crystal, List<String> mods, String description) {
        SPECIES.add(new Species(id, name, 2, "mineral", "underground", null, body, gaster, accent, "faceted", crystal, true,
                1.0, 1.4, "crystal", 0.5, 0.45,
                List.of(Out.of("c:gems/" + id, 40).asTag(), Out.of("c:dusts/" + id, 12).asTag(), Out.of("cobblestone", 12), Out.of("calcite", 6).atStage(2)),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"}), 4800, 0.85, 0.9, null,
                title(id) + " Extraction", "Gem Cutting", title(id) + " Efficiency", description));
        NEEDS_MODS.put(id, mods);
    }

    /**
     * One of Allthemodium's metals: rock at the Netherite Ant's pace, with nuggets from stage 3 and raw ore at stage 4 as rare
     * finds (weight 0: none here, the metal comes from an expedition). Half the Netherite Ant's brood, three times its years.
     * The weights are per 80 of rock: 1 is about 20 an hour for a full colony, as netherite scrap's 2 is about 40.
     */
    static void modium(String id, String name, String climate, String body, String gaster, String accent, String crystal, String food, List<Out> rock,
                       double nuggets, double raw, String description) {
        List<Out> outputs = new ArrayList<>(rock);
        if (nuggets > 0) {
            outputs.add(Out.of("c:nuggets/" + id, nuggets).atStage(3).asSpecial().asTag());
        }
        if (raw > 0) {
            outputs.add(Out.of("c:raw_materials/" + id, raw).atStage(4).asSpecial().asTag());
        }
        SPECIES.add(new Species(id, name, 4, "endgame", climate, NEST_DWELLERS.get(id), body, gaster, accent, "metallic", crystal, true,
                1.2, 2.8, food, 2.0, 0.0025, outputs, 0, List.of(), 2.0, List.of(),
                List.of(new String[] {ac("royal_jelly"), "6"}, new String[] {ac(food + "_ant_food"), "12"}, new String[] {ac("nuptial_pheromone"), "4"}),
                12000, 0.3, 0.4, null, title(id) + " Extraction", "Rare Metal Smelting", title(id) + " Efficiency", description));
        NEEDS_MODS.put(id, List.of("allthemodium"));
        LIFESPANS.put(id, 3.0);
    }

    /**
     * Species with no nest block of their own that live wild in another kind of nest, and the biome tag they are at home
     * in: Allthemodium and Vibranium Ants in The Other, Crimson Ants in crimson forests (whose nests are the Warped
     * Ants' kind). They go into the species tag nest_dwellers.
     */
    static final Map<String, String> NEST_DWELLERS = Map.of("allthemodium", "spawns/the_other", "vibranium", "spawns/the_other",
            "crimson", "spawns/crimson");

    /** Species whose adults live this many times the usual lifespan. */
    static final Map<String, Double> LIFESPANS = new LinkedHashMap<>();

    /**
     * Species that are not Liquid Ants and still give a liquid at a Liquid Chamber: the fluid, its kind in LIQUIDS, the
     * droplet rate and the crop's colour.
     */
    /** Species that wear a costume of their own name over their colours (the game's Costume enum, TexGen's COSTUMES). */
    /** Other mods' worlds whose ants the mod knows: anthills stand there too, built of the ground they find (the LOCAL look). */
    static final List<String> SURFACE_WORLD_BIOMES = List.of("twilightforest:clearing", "twilightforest:dark_forest", "twilightforest:dark_forest_center",
            "twilightforest:dense_forest", "twilightforest:dense_mushroom_forest", "twilightforest:enchanted_forest", "twilightforest:final_plateau",
            "twilightforest:fire_swamp", "twilightforest:firefly_forest", "twilightforest:forest", "twilightforest:glacier", "twilightforest:highlands",
            "twilightforest:mushroom_forest", "twilightforest:oak_savannah", "twilightforest:snowy_forest", "twilightforest:spooky_forest",
            "twilightforest:swamp", "twilightforest:thornlands", "aether_ii:battleground_wastes", "aether_ii:contaminated_jungle",
            "aether_ii:enduring_woodland", "aether_ii:expanse", "aether_ii:flourishing_field", "aether_ii:frigid_sierra", "aether_ii:frozen_lakes",
            "aether_ii:glistening_swamp", "aether_ii:magnetic_scar", "aether_ii:sheer_tundra", "aether_ii:shimmering_basin",
            "aether_ii:shrouded_forest", "aether_ii:turquoise_forest", "aether_ii:verdant_woods", "aether_ii:violet_highwoods");
    static final List<String> CAVE_WORLD_BIOMES = List.of("undergarden:barren_abyss", "undergarden:blood_mushroom_bog", "undergarden:dense_forest",
            "undergarden:depths", "undergarden:forgotten_field", "undergarden:frostfields", "undergarden:frosty_smogstem_forest",
            "undergarden:gronglegrowth", "undergarden:indigo_mushroom_bog", "undergarden:ink_mushroom_bog", "undergarden:puff_mushroom_forest",
            "undergarden:smog_spires", "undergarden:smogstem_forest", "undergarden:veil_mushroom_bog", "undergarden:wigglewood_forest",
            "allthemodium:the_other", "allthemodium:warped_forest", "allthemodium:crimson_forest", "allthemodium:soul_sand_valley",
            "allthemodium:basalt_deltas", "allthemodium:desert", "allthemodium:desert_hills");

    static final Set<String> COSTUMED = Set.of("cow", "chicken", "sheep", "baker", "bookworm", "party", "pirate", "pig", "rabbit",
            "fisher", "carpenter", "leafcutter", "weaver");

    static final Map<String, Object[]> SIDE_LIQUIDS = Map.of("cow", new Object[] {"minecraft:milk", "milk", 0.3, "F4EEDD"});

    /** An ant's colours from its material's one colour: body, gaster, accent, the pattern and the crystal. */
    static String[] tones(String base, String pattern) {
        int rgb = Integer.parseInt(base, 16);
        return new String[] {toward(rgb, 0x000000, 0.62), base, toward(rgb, 0xFFFFFF, 0.55), pattern, toward(rgb, 0xFFFFFF, 0.25)};
    }

    static String toward(int rgb, int target, double share) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * (1 - share) + ((target >> 16) & 0xFF) * share);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * (1 - share) + ((target >> 8) & 0xFF) * share);
        int b = (int) Math.round((rgb & 0xFF) * (1 - share) + (target & 0xFF) * share);
        return String.format("%02X%02X%02X", r, g, b);
    }

    /** The laboratory rows of the species added with {@link #made}: evolutions() and research() append them. */
    static final List<Object[]> MADE = new ArrayList<>();

    /**
     * How a species of another mod is made: from which species, with which material (an item or a #tag), at which
     * research tier, after which research (null for none). Its evolution's numbers follow the species' tier.
     */
    static void made(String id, String from, String material, String research, String requires, String hint) {
        MADE.add(new Object[] {id, from, material, research, requires, hint});
        LAB_AMOUNTS.put(from + "_to_" + id, HARDEST.contains(id) ? 4 : 8);
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
                        Out.of("spruce_sapling", 5), Out.of("birch_sapling", 4), Out.of("apple", 3).atStage(2),
                        Out.of("dark_oak_log", 4).atStage(3).asSpecial(), Out.of("dark_oak_sapling", 1).atStage(3).asSpecial(),
                        Out.of(ac("ant_resin"), 7)),
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

        // --- More wild species: one per kind of biome the first six don't cover ---
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
        // --- The Stone line goes deeper: copper, coal and deepslate ---
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
        // --- The Nether line: Fire Ants from Desert Ants, Soul Ants from Fire Ants ---
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
        // Hunters: their workers hunt mobs at a Hunting Ground.
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
                List.of(Out.of("gunpowder", 10), Out.of("redstone", 6), Out.of("glowstone_dust", 4).atStage(2),
                        Out.of("phantom_membrane", 0.3).atStage(3).asSpecial()),
                0, List.of(), 1.6, List.of(new String[] {"minecraft:creeper", "40"}, new String[] {"minecraft:witch", "15"},
                        new String[] {"minecraft:spider", "15"}, new String[] {"minecraft:cave_spider", "10"},
                        // A membrane is a player's drop: a grown colony's hunters bring one from every fourth phantom.
                        new String[] {"minecraft:phantom", "10", "phantom_membrane", "0.25", "3"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"gunpowder", "4"}), 3600, 1.0, 1.0, "gunpowder",
                "Powder Extraction", "Powder Packing", "Sting Efficiency",
                "Jungle Ants fed gunpowder. Their sting fells creepers and witches: gunpowder, redstone and glowstone come home, and from "
                        + "stage 3 phantom membranes."));
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
        SPECIES.add(new Species("crimson", "Crimson Ant", 2, "hybrid", "hot", NEST_DWELLERS.get("crimson"), "6A1020", "A02030", "2AA8A0", "plain", null, false,
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
        // --- The End: Ender Ants from Amethyst Ants ---
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

        // --- More hybrids: crosses between the wild and evolved lines, each for things no single species brings ---
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

        // --- The Nether's and the End's own wild ants (nests there only), and the boss-gated endgame species ---
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

        // --- Species for other mods' metals and crystals: loaded only with such a mod, finds through c: tags, no signature item ---
        compat("osmium", "Osmium Ant", 3, "any", "5A6E80", "A8C0D0", "D8E8F0", "metallic", "B0D0E8", false, "osmium", List.of("mekanism", "alltheores"),
                "Stone Ants fed osmium, which takes a diamond pickaxe to mine. Slow; bring osmium for Mekanism's machines.");
        compat("tin", "Tin Ant", 1, "any", "6A6A70", "C8C8D0", "E8E8F0", "metallic", null, false, "tin",
                List.of("mekanism", "thermal", "machinaopus", "alltheores", "railcraft", "energizedpower", "modern_industrialization"),
                "Copper Ants fed tin: together they make bronze. Bring tin.");
        compat("lead", "Lead Ant", 1, "underground", "2E2E3A", "5A5A78", "8A8AA8", "metallic", null, false, "lead",
                List.of("mekanism", "thermal", "immersiveengineering", "machinaopus", "alltheores", "railcraft", "modern_industrialization"),
                "Heavy, slow Iron Ants fed lead. Bring lead.");
        compat("silver", "Silver Ant", 2, "any", "6A7078", "D8E0E8", "FFFFFF", "metallic", "E0E8F0", false, "silver",
                List.of("thermal", "immersiveengineering", "machinaopus", "alltheores", "railcraft", "occultism", "iceandfire"),
                "Lead Ants fed silver; they shine like mirrors. Bring silver.");
        compat("nickel", "Nickel Ant", 2, "any", "6A6A50", "C8C8A0", "E8E8C8", "metallic", null, false, "nickel",
                List.of("thermal", "immersiveengineering", "alltheores", "railcraft", "oritech", "modern_industrialization"), "Iron Ants fed nickel. Bring nickel.");
        compat("aluminum", "Aluminum Ant", 1, "any", "8A8A90", "D8DCE0", "F0F4F8", "metallic", null, false, "aluminum",
                List.of("immersiveengineering", "alltheores"), "Light, quick Iron Ants fed aluminum. Bring aluminum.");
        compat("zinc", "Zinc Ant", 1, "any", "5A6A60", "A8C0B0", "D0E8D8", "metallic", null, false, "zinc", List.of("create", "alltheores", "railcraft"),
                "Copper Ants fed zinc, ready for Create's brass. Bring zinc.");
        compat("uranium", "Uranium Ant", 2, "underground", "2A3A1A", "5AE02A", "C8FF8A", "faceted", "7AFF3A", true, "uranium",
                List.of("mekanism", "immersiveengineering", "machinaopus", "alltheores", "oritech", "modern_industrialization", "bigreactors"),
                "Lead Ants fed raw uranium. They glow green in the dark; bring uranium.");
        // Crystals rather than metals: their own outputs.
        SPECIES.add(new Species("fluorite", "Fluorite Ant", 2, "mineral", "underground", null, "3A2A60", "8A6AE8", "D0C0FF", "faceted", "A890FF", true,
                1.0, 1.4, "crystal", 0.5, 0.45,
                List.of(Out.of("c:gems/fluorite", 40).asTag(), Out.of("c:dusts/fluorite", 12).asTag(), Out.of("cobblestone", 12),
                        Out.of("calcite", 6).atStage(2)),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"}), 4800, 0.85, 0.9, null,
                "Fluorite Extraction", "Crystal Purification", "Lattice Efficiency",
                "Amethyst Ants fed fluorite. Bring fluorite for Mekanism's or MachinaOpus's chemistry."));
        NEEDS_MODS.put("fluorite", List.of("mekanism", "machinaopus", "alltheores"));
        SPECIES.add(new Species("certus", "Certus Ant", 2, "mineral", "underground", null, "2A3A5A", "8AB8F0", "E0F0FF", "faceted", "A0D0FF", true,
                1.0, 1.4, "crystal", 0.5, 0.45,
                List.of(Out.of("c:gems/certus_quartz", 40).asTag(), Out.of("c:dusts/certus_quartz", 12).asTag(), Out.of("quartz", 10),
                        Out.of("c:gems/fluix", 0.5).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "3"}, new String[] {ac("crystal_ant_food"), "6"}), 4800, 0.85, 0.9, null,
                "Certus Extraction", "Crystal Charging", "Resonance Efficiency",
                "Amethyst Ants fed certus quartz. Bring certus quartz for Applied Energistics, and fluix at stage 3."));
        NEEDS_MODS.put("certus", List.of("ae2"));

        // All The Ores' other ores: two rare metals, three gems, cinnabar (with the sulfur found beside it) and salt.
        compat("platinum", "Platinum Ant", 4, "underground", "56626E", "C4D4E0", "F2F8FF", "metallic", "D4E8F8", false, "platinum",
                List.of("alltheores", "oritech"), "Gold Ants fed platinum, a rare white metal. As slow as Diamond Ants; bring platinum.");
        compat("iridium", "Iridium Ant", 4, "underground", "4A465A", "B4ACD0", "ECE8FF", "metallic", "C8C0F0", true, "iridium",
                List.of("alltheores"), "Platinum Ants fed iridium, the hardest of the white metals. As slow as Diamond Ants; bring iridium.");
        gem("ruby", "Ruby Ant", "5A0E1E", "D8203E", "FF9AA8", "FF3A5A", List.of("alltheores", "silentgems"),
                "Amethyst Ants fed rubies. Deep red; bring rubies.");
        gem("sapphire", "Sapphire Ant", "0E2A6A", "2A6AE8", "A0C8FF", "4A8AFF", List.of("alltheores", "silentgems", "iceandfire"),
                "Lapis Ants fed sapphires. Clear blue; bring sapphires.");
        gem("peridot", "Peridot Ant", "2E4A0E", "8AD82A", "D8FF9A", "A8F04A", List.of("alltheores", "silentgems"),
                "Amethyst Ants fed peridot. Lime green; bring peridot.");
        // All The Ores' cinnabar: two small veins a chunk in a big pack, several times rarer than gold.
        SPECIES.add(new Species("cinnabar", "Cinnabar Ant", 3, "mineral", "hot", null, "4A1410", "C8341E", "FF8A5A", "speckled", "E84A2A", false,
                1.0, 1.4, "mineral", 0.6, HARD_RATE,
                List.of(Out.of("c:gems/cinnabar", 36).asTag(), Out.of("c:dusts/cinnabar", 10).asTag(), Out.of("c:dusts/sulfur", 10).asTag(),
                        Out.of("redstone", 8), Out.of("cobblestone", 12)),
                0, List.of(), 1.0, List.of(), mineralRearing, 4800, 0.85, 0.9, null,
                "Cinnabar Extraction", "Ore Roasting", "Vermilion Efficiency",
                "Redstone Ants fed cinnabar, the red ore of quicksilver. Bring cinnabar and the sulfur found beside it."));
        NEEDS_MODS.put("cinnabar", List.of("alltheores"));
        SPECIES.add(new Species("salt", "Salt Ant", 1, "mineral", "hot", null, "8A8078", "F0E8E0", "FFFFFF", "speckled", null, false,
                1.0, 1.35, "mineral", 0.45, 0.6,
                List.of(Out.of("c:dusts/salt", 40).asTag(), Out.of("sand", 20), Out.of("gravel", 8), Out.of("clay_ball", 4).atStage(2)),
                0, List.of(), 1.0, List.of(), mineralRearing, 3600, 0.9, 0.9, null,
                "Salt Extraction", "Brine Drying", "Salt Pan Efficiency",
                "Desert Ants fed salt that work dry salt pans. Pale as the crust they dig; bring salt."));
        NEEDS_MODS.put("salt", List.of("alltheores"));

        // Allthemodium's three metals, past netherite and as slow, each from the one before. The first digs its metal in the
        // Overworld; the other two find theirs only on Nether and End expeditions (see expeditions()).
        modium("allthemodium", "Allthemodium Ant", "underground", "5A4208", "F0C020", "FFF0A0", "FFD840", "mineral",
                List.of(Out.of("cobbled_deepslate", 30), Out.of("gold_nugget", 30), Out.of("tuff", 20)), 165, 3,
                "Netherite Ants fed allthemodium, the gold-bright metal under the deepest stone. They breed slowly and live long; "
                        + "very slowly, they bring allthemodium.");
        modium("vibranium", "Vibranium Ant", "nether", "0A3A34", "20C8A8", "A0FFE8", "40F0C8", "nether",
                List.of(Out.of("netherrack", 30), Out.of("quartz", 30), Out.of("blackstone", 20)), 0, 0,
                "Allthemodium Ants fed vibranium, the humming teal metal of the Nether. They breed slowly and live long; only a "
                        + "Nether Foraging Site brings them to their metal.");
        modium("unobtainium", "Unobtainium Ant", "end", "3A0A5A", "B040E8", "F0B0FF", "D060FF", "crystal",
                List.of(Out.of("end_stone", 40), Out.of("obsidian", 20), Out.of("chorus_fruit", 20)), 0, 0,
                "Vibranium Ants fed unobtainium, the violet metal of the End. They breed slowly and live long; only an End "
                        + "Foraging Site brings them to what cannot be obtained.");

        // MachinaOpus's own materials: these name its items directly and have signature items.
        SPECIES.add(new Species("molybdenum", "Molybdenum Ant", 2, "mineral", "underground", null, "3A4048", "8A98A8", "C0CCD8", "metallic", "A8B8D0", false,
                1.05, 1.5, "mineral", 0.6, 0.45,
                List.of(Out.of("machinaopus:molybdenum_nugget", 40).processed("machinaopus:molybdenum_ingot", 9), Out.of("cobblestone", 15),
                        Out.of("machinaopus:raw_molybdenum", 10).atStage(2), Out.of("machinaopus:molybdenum_ingot", 2).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), mineralRearing, 6000, 0.9, 0.9, "machinaopus:molybdenum_ingot",
                "Molybdenum Extraction", "Metal Processing", "Molybdenum Efficiency",
                "Iron Ants fed molybdenum, the metal that hardens MachinaOpus's steel. Bring molybdenum."));
        NEEDS_MODS.put("molybdenum", List.of("machinaopus"));
        // Sulfur through the common tag, so any mod's sulfur will do; the petroleum coke is MachinaOpus's alone (an own tag,
        // empty without it).
        SPECIES.add(new Species("sulfur", "Sulfur Ant", 2, "mineral", "hot", null, "5A4A0A", "E0C820", "FFF080", "speckled", "F0E040", false,
                1.0, 1.4, "nether", 0.4, 0.5,
                List.of(Out.of("c:dusts/sulfur", 36).asTag(), Out.of("netherrack", 14), Out.of("magma_block", 6), Out.of("basalt", 6),
                        Out.of("gunpowder", 4).atStage(2), Out.of(ac("foraging/sulfur_vents"), 0.5).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.3, List.of(), netherRearing, 4800, 0.9, 1.0, null,
                "Brimstone Extraction", "Sulfur Refining", "Vent Efficiency",
                "Fire Ants fed sulfur that live around hot vents. Bring sulfur without mining or refining for it."));
        NEEDS_MODS.put("sulfur", List.of("machinaopus", "alltheores", "railcraft", "neovitae"));
        SPECIES.add(new Species("rubber", "Rubber Ant", 1, "farmer", "temperate", null, "4A3020", "2A1C14", "E8E0C8", "banded", null, false,
                1.0, 0.85, "", 0, 0.8,
                List.of(Out.of("machinaopus:rubber_log", 18), Out.of("stick", 14), Out.of("machinaopus:rubber_leaves", 8), Out.of("machinaopus:rubber_sapling", 5)),
                0.6, List.of(Out.of("machinaopus:rubber_sheet", 24), Out.of("slime_ball", 4).atStage(2)),
                0.9, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"machinaopus:rubber_sapling", "4"}), 3600, 1.0, 1.0,
                "machinaopus:rubber_sheet", "Sap Tapping", "Latex Curing", "Tapper Efficiency",
                "Wood Ants fed rubber that tap rubber trees. Bring rubber logs and saplings; at a Harvesting Site they bring rubber for MachinaOpus's cables."));
        NEEDS_MODS.put("rubber", List.of("machinaopus"));

        // --- The ores and gems of the mods of big packs (All the Mods 11): one ant for each material found in the world ---
        pack("uraninite", "Uraninite Ant", 2, "underground", new String[] {"16301E", "2E9E5A", "9AF0B8", "speckled", "4ADE8A"}, true, "mineral", 0.35,
                List.of(Out.of("c:raw_materials/uraninite", 36).asTag(), Out.of("redstone", 10), Out.of("cobblestone", 14)),
                List.of("powah"), "Redstone Ants fed raw uraninite, the ore that charges Powah's machines. Bring raw uraninite.");
        pack("inferium", "Inferium Ant", 1, "temperate", new String[] {"3A4A10", "9AC81E", "E0F78A", "banded", "B8E83A"}, false, "mineral", 0.5,
                List.of(Out.of("mysticalagriculture:inferium_essence", 40), Out.of("cobblestone", 14), Out.of("dirt", 8),
                        Out.of("mysticalagriculture:prosperity_shard", 8).atStage(2)),
                List.of("mysticalagriculture"), "Stone Ants fed inferium essence. Bring essence for Mystical Agriculture, and prosperity shards "
                        + "from stage 2.");
        // Arcane crystal: three small veins a chunk, a third of gold. Its runes take a diamond pickaxe; stellarite is rarer still.
        pack("arcane", "Arcane Ant", 3, "underground", new String[] {"4A1048", "D040C8", "FFB0F4", "faceted", "F060E0"}, true, "crystal", HARD_RATE,
                List.of(Out.of("c:gems/arcane_crystal", 36).asTag(), Out.of("c:dusts/arcane_crystal", 10).asTag(), Out.of("cobblestone", 12),
                        Out.of("forbidden_arcanus:rune", 2).atStage(2), Out.of("forbidden_arcanus:stellarite_piece", 0.3).atStage(4).asSpecial()),
                List.of("forbidden_arcanus"), "Amethyst Ants fed arcane crystals. Slow; bring crystals and a few runes for Forbidden and Arcanus, and a rare "
                        + "piece of stellarite.");
        compat("iesnium", "Iesnium Ant", 4, "nether", "0E3438", "2AB8C0", "A8F4F8", "metallic", "50D8E0", true, "iesnium", List.of("occultism"),
                "Silver Ants fed iesnium, the metal Occultism hides in netherrack. They see it without a third eye, but find it as rarely as diamonds; bring iesnium.");
        pack("dark_gem", "Dark Gem Ant", 2, "underground", new String[] {"101014", "2A2A3A", "7A7A9A", "faceted", "4A4A6A"}, false, "crystal", 0.45,
                List.of(Out.of("evilcraft:dark_gem", 36), Out.of("evilcraft:dark_gem_crushed", 10), Out.of("coal", 10), Out.of("cobblestone", 12)),
                List.of("evilcraft"), "Coal Ants fed dark gems. Black as their find; bring dark gems for EvilCraft.");
        pack("bort", "Bort Ant", 2, "underground", new String[] {"2E2A28", "7A746E", "C8C2BA", "faceted", "A09A92"}, false, "crystal", 0.45,
                List.of(Out.of("c:gems/bort", 36).asTag(), Out.of("cobbled_deepslate", 16), Out.of("coal", 8)),
                List.of("silentgear"), "Deepslate Ants fed bort, the rough diamond of Silent Gear. Bring bort.");
        compat("crimson_iron", "Crimson Iron Ant", 2, "nether", "4A0E14", "C82838", "FF9AA0", "metallic", "E84050", false, "crimson_iron",
                List.of("silentgear"), "Iron Ants fed crimson iron, the red metal of the Nether. Bring crimson iron for Silent Gear.");
        compat("azure_silver", "Azure Silver Ant", 4, "end", "14285A", "4A8AF0", "C0DCFF", "metallic", "70A8FF", true, "azure_silver",
                List.of("silentgear"), "Chorus Ants fed azure silver, the blue metal of the End that only a netherite pickaxe breaks. As slow as Diamond Ants; bring azure silver for Silent Gear.");
        compat("cloggrum", "Cloggrum Ant", 2, "underground", "3A2E1E", "8A7450", "C8B48A", "metallic", null, false, "cloggrum",
                List.of("undergarden"), "Iron Ants fed cloggrum, the dull metal of the Undergarden. Bring cloggrum.");
        compat("froststeel", "Froststeel Ant", 3, "cold", "20384A", "7AB4D8", "D8F0FF", "metallic", "A0D0F0", false, "froststeel",
                List.of("undergarden"), "Cloggrum Ants fed froststeel, the cold metal of the Undergarden's ice. Slow; bring froststeel.");
        gemOf("utherium", "Utherium Ant", new String[] {"4A0A0A", "D82020", "FF9A8A", "faceted", "FF4030"}, 3, 1.0, "cobbled_deepslate", List.of("undergarden"),
                "Redstone Ants fed utherium crystals from the Undergarden. Blood red; bring utherium.");
        gemOf("regalium", "Regalium Ant", new String[] {"4A3A08", "E8C030", "FFF0A0", "faceted", "FFD850"}, 3, 0.5, "cobbled_deepslate", List.of("undergarden"),
                "Amethyst Ants fed regalium crystals from the Undergarden. Bright as coins; bring regalium.");
        pack("dimensional", "Dimensional Ant", 2, "any", new String[] {"10283A", "48C8D8", "D0FFFF", "faceted", "80F0F0"}, true, "crystal", 0.4,
                List.of(Out.of("rftoolsbase:dimensionalshard", 36), Out.of("cobblestone", 12), Out.of("netherrack", 6), Out.of("end_stone", 6)),
                List.of("rftoolsbase"), "Lapis Ants fed dimensional shards. Bring shards for RFTools from wherever they dig.");
        gem("fluxite", "Fluxite Ant", "3A0E3A", "D040A0", "FFB0E0", "F060C0", List.of("oritech"),
                "Lapis Ants fed fluxite. Bright pink; bring fluxite for Oritech.");
        gem("force", "Force Ant", "4A4208", "F0D820", "FFF8A0", "FFE840", List.of("forcecraft"),
                "Gold Ants fed force gems. Bring force gems for Forcecraft.");
        pack("chimerite", "Chimerite Ant", 2, "underground", new String[] {"3A2A4A", "C890D8", "F0D8FF", "faceted", "D8A8F0"}, true, "crystal", 0.4,
                List.of(Out.of("c:gems/chimerite", 30).asTag(), Out.of("c:dusts/vinteum", 14).asTag(), Out.of("cobblestone", 12),
                        Out.of("c:gems/topaz", 8).atStage(2).asTag(), Out.of("c:gems/moonstone", 0.1).atStage(4).asSpecial().asTag(),
                        Out.of("c:gems/sunstone", 0.3).atStage(4).asSpecial().asTag()),
                List.of("arsmagicalegacy"), "Amethyst Ants fed chimerite. Bring chimerite, vinteum dust and topaz for Ars Magica, and a rare "
                        + "moonstone or sunstone.");
        pack("amber", "Amber Ant", 2, "temperate", new String[] {"5A3008", "E89018", "FFD890", "banded", "FFB030"}, false, "crystal", 0.45,
                List.of(Out.of("c:gems/amber", 34).asTag(), Out.of("cobblestone", 12), Out.of("c:raw_materials/cinnabar", 8).asTag(),
                        Out.of("c:gems/quicksilver", 6).atStage(2).asTag()),
                List.of("thaumaturge"), "Stone Ants fed amber. Honey gold; bring amber for Thaumaturge, with cinnabar and quicksilver.");
        pack("saltpeter", "Saltpeter Ant", 1, "hot", new String[] {"6A6A58", "E0E0C8", "FFFFF0", "speckled", null}, false, "mineral", 0.6,
                List.of(Out.of("c:dusts/saltpeter", 38).asTag(), Out.of("sand", 20), Out.of("gravel", 8), Out.of("c:dusts/sulfur", 6).atStage(2).asTag()),
                List.of("railcraft", "neovitae", "eternal_starlight"), "Desert Ants fed saltpeter that scrape it from dry sand. Bring saltpeter.");
        gem("sal_ammoniac", "Sal Ammoniac Ant", "5A5A3A", "E8E8B0", "FFFFE0", "F0F0C0", List.of("theurgy"),
                "Amethyst Ants fed sal ammoniac crystals. Pale and sharp-smelling; bring sal ammoniac for Theurgy.");
        // Desh: only on the Moon, so a first rocket.
        pack("desh", "Desh Ant", 3, "any", new String[] {"5A2A0E", "E87A28", "FFD0A0", "metallic", "FF9A40"}, false, "mineral", HARD_RATE,
                List.of(Out.of("c:raw_materials/desh", 20).asTag(), Out.of("cobblestone", 30), Out.of("c:ingots/desh", 1).atStage(3).asSpecial().asTag()),
                List.of("stellaris", "ad_astra"), "Iron Ants fed desh, the orange metal of other worlds. Slow; bring raw desh.");
        compat("titanium", "Titanium Ant", 2, "any", "3A4250", "9AA8C0", "E0E8F8", "metallic", "B8C8E0", false, "titanium",
                List.of("stellaris", "modern_industrialization"), "Iron Ants fed titanium, light and hard. Bring titanium.");
        pack("ambrosium", "Ambrosium Ant", 1, "any", new String[] {"5A5210", "F0E040", "FFFFB0", "speckled", "FFF060"}, true, "mineral", 0.5,
                List.of(Out.of("aether_ii:ambrosium_shard", 40), Out.of("cobblestone", 14)),
                List.of("aether_ii"), "Coal Ants fed ambrosium shards, the glowing fuel of the Aether. Bring ambrosium.");
        pack("zanite", "Zanite Ant", 2, "any", new String[] {"34206A", "8A5AE8", "D8C8FF", "faceted", "A880FF"}, true, "crystal", 0.45,
                List.of(Out.of("aether_ii:zanite_gemstone", 36), Out.of("cobblestone", 14)),
                List.of("aether_ii"), "Amethyst Ants fed zanite gemstones from the Aether. Bring zanite.");
        // Arkenium and gravitite: deeper in the Aether's islands, each behind the tool tier before it.
        pack("arkenium", "Arkenium Ant", 3, "any", new String[] {"3A3E48", "A8B0C0", "F0F4FF", "metallic", "C8D0E0"}, false, "mineral", HARD_RATE,
                List.of(Out.of("aether_ii:arkenium_plate", 20), Out.of("cobblestone", 30)),
                List.of("aether_ii"), "Iron Ants fed arkenium plates, the heavy metal of the Aether. Slow; bring arkenium plates.");
        pack("gravitite", "Gravitite Ant", 3, "any", new String[] {"4A1040", "E060C8", "FFC0F0", "metallic", "F080E0"}, true, "mineral", HARD_RATE,
                List.of(Out.of("aether_ii:gravitite_plate", 10), Out.of("cobblestone", 40)),
                List.of("aether_ii"), "Arkenium Ants fed gravitite plates, the metal that falls upward. Slow; bring gravitite plates.");

        funnyAnts();
        packMaterials();
        alloys();
        for (Species s : SPECIES) {
            List<Object> outputs = new ArrayList<>();
            s.outputs().forEach(o -> outputs.add(o.json()));
            List<Object> harvest = new ArrayList<>();
            s.harvest().forEach(o -> harvest.add(o.json()));
            List<Object> targets = new ArrayList<>();
            // A target's row may go on with a trophy: its item, its chance a catch and the colony stage it starts at.
            s.targets().forEach(t -> targets.add(obj("entity", t[0], "weight", Integer.parseInt(t[1]), "trophy", t.length < 5 ? null
                    : obj("item", mc(t[2]), "chance", Double.parseDouble(t[3]), "min_stage", Integer.parseInt(t[4])))));
            List<Object> cost = new ArrayList<>();
            s.rearing().forEach(c -> cost.add(obj("ingredient", c[0].startsWith("#") ? c[0] : mc(c[0]), "count", Integer.parseInt(c[1]))));
            Map<String, Object> look = obj("body", "#" + s.body(), "gaster", "#" + s.gaster(), "accent", "#" + s.accent(), "pattern", s.pattern(),
                    "crystal", s.crystal() == null ? null : "#" + s.crystal(), "glow", s.glow() ? true : null,
                    "size", s.size() == 1.0 ? null : s.size());
            Map<String, Object> json = obj("tier", s.tier(), "category", s.category(), "look", look, "climate", s.climate(),
                    "spawn_biomes", s.spawn() == null ? null : ac(s.spawn()),
                    "diet", obj("consumption", s.consumption(), "special_food", s.special().isEmpty() ? null : s.special(),
                            "special_per_egg", s.specialPerEgg() == 0 ? null : Math.round(s.specialPerEgg() * 1000) / 1000.0),
                    "production", obj("rate", s.rate(), "outputs", outputs),
                    "farming", s.farmFood() > 0 ? obj("food", s.farmFood(), "harvest", harvest) : null,
                    "combat", obj("strength", s.strength(), "targets", targets.isEmpty() ? null : targets),
                    "rearing", obj("cost", cost, "time", s.rearingTime()),
                    "breeding", obj("eggs", s.eggs(), "growth", s.growth(), "lifespan", LIFESPANS.get(s.id())),
                    "signature_item", s.signature() == null ? null : s.signature().contains(":") ? s.signature() : mc(s.signature()));
            if (COSTUMED.contains(s.id())) {
                look.put("costume", s.id());
            }
            if (SIDE_LIQUIDS.containsKey(s.id())) {
                Object[] liquid = SIDE_LIQUIDS.get(s.id());
                look.put("crop", "#" + liquid[3]);
                json.put("liquid", obj("fluid", liquid[0], "droplet", ac(liquid[1] + "_droplet"), "rate", liquid[2]));
            }
            if (NEEDS_MODS.containsKey(s.id())) {
                json.put("neoforge:conditions", speciesConditions(s.id()));
            }
            write(DATA.resolve(NS + "/species/" + s.id() + ".json"), json);
            LANG.put("species." + NS + "." + s.id(), s.name());
            LANG.put("species." + NS + "." + s.id() + ".guard", guardName(s.name()));
            LANG.put("species." + NS + "." + s.id() + ".desc", s.description());
            LANG.put("upgrade." + NS + "." + s.id() + ".extraction", s.extraction());
            LANG.put("upgrade." + NS + "." + s.id() + ".processing", s.processing());
            LANG.put("upgrade." + NS + "." + s.id() + ".efficiency", s.efficiency());
        }
    }

    /**
     * Ants for fun, each fed into being like the hunters: five that took after animals (they forage, and "hunt" their
     * animal's drops at a Hunting Ground; the Cow Ant gives milk at a Liquid Chamber too), a baker, a reader, a reveller,
     * a pirate and a fisher. Their rare finds are kept rare: see the weights.
     */
    static void funnyAnts() {
        SPECIES.add(new Species("cow", "Cow Ant", 1, "hunter", "temperate", null, "F2F2F2", "2E2A28", "F0A6B4", "speckled", null, false,
                1.15, 1.3, "", 0, 0.5,
                List.of(Out.of("wheat", 20), Out.of("wheat_seeds", 14), Out.of("short_grass", 12), Out.of("dandelion", 5)),
                0, List.of(), 1.0, List.<String[]>of(new String[] {"minecraft:cow", "100"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"wheat", "4"}), 3600, 1.0, 1.0, "leather",
                "Herd Extraction", "Cheese Making", "Grazing Efficiency",
                "Army Ants fed milk. Black and white and very calm. They graze; what they bring from a Hunting Ground looks a lot like "
                        + "beef and leather; and at a Liquid Chamber they give Milk Droplets. Nobody has heard one moo."));
        SPECIES.add(new Species("chicken", "Chicken Ant", 1, "hunter", "temperate", null, "F6F4EC", "FFFFFF", "D8281E", "plain", null, false,
                0.9, 1.0, "", 0, 0.6,
                List.of(Out.of("egg", 22), Out.of("wheat_seeds", 20), Out.of("feather", 8)),
                0, List.of(), 0.8, List.<String[]>of(new String[] {"minecraft:chicken", "100"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"egg", "4"}), 3600, 1.1, 1.1, "feather",
                "Coop Extraction", "Egg Sorting", "Pecking Efficiency",
                "Army Ants fed feathers. Small, white and easily startled. They forage eggs and seeds, and bring chicken and feathers from a "
                        + "Hunting Ground. They cannot fly either."));
        SPECIES.add(new Species("sheep", "Sheep Ant", 1, "hunter", "temperate", null, "4A4038", "F4F0E6", "FFFFFF", "plain", null, false,
                1.05, 1.1, "", 0, 0.5,
                List.of(Out.of("white_wool", 20), Out.of("string", 8), Out.of("light_gray_wool", 3), Out.of("gray_wool", 3), Out.of("black_wool", 3),
                        Out.of("brown_wool", 2), Out.of("pink_wool", 0.2).atStage(3).asSpecial()),
                0, List.of(), 0.9, List.<String[]>of(new String[] {"minecraft:sheep", "100"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"white_wool", "4"}), 3600, 1.0, 1.0, "white_wool",
                "Flock Extraction", "Wool Carding", "Shearing Efficiency",
                "Army Ants fed wool. Woolly ants that follow each other everywhere. They forage wool in the colours sheep come in, pink "
                        + "very rarely, and bring wool and mutton from a Hunting Ground."));
        SPECIES.add(new Species("baker", "Baker Ant", 1, "farmer", "temperate", null, "E8D8B8", "C89A5A", "FFFFFF", "plain", null, false,
                1.0, 0.9, "", 0, 0.7,
                List.of(Out.of("wheat", 22), Out.of("sugar", 10), Out.of("egg", 8), Out.of("cocoa_beans", 5)),
                1.2, List.of(Out.of("bread", 30), Out.of("cookie", 20), Out.of("pumpkin_pie", 6).atStage(2), Out.of("cake", 1).atStage(3).asSpecial()),
                0.8, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"bread", "4"}), 3600, 1.0, 1.0, "cake",
                "Flour Milling", "Oven Tending", "Bakery Efficiency",
                "Farmer Ants fed cake. Dusted white with flour. They forage wheat, sugar and eggs, and bake at a Harvesting Site: bread, "
                        + "cookies, pumpkin pie and now and then a whole cake."));
        SPECIES.add(new Species("bookworm", "Bookworm Ant", 1, "base", "temperate", null, "5A3A22", "8A6A3A", "E8DCC0", "banded", null, false,
                1.0, 1.0, "", 0, 0.6,
                List.of(Out.of("paper", 30), Out.of("book", 12), Out.of("feather", 6), Out.of("ink_sac", 6), Out.of("bookshelf", 3).atStage(2),
                        Out.of("writable_book", 2).atStage(2), Out.of("experience_bottle", 1).atStage(3).asSpecial(),
                        Out.of("name_tag", 0.05).atStage(4).asSpecial()),
                0, List.of(), 0.8, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"book", "4"}), 3600, 1.0, 1.0, "book",
                "Shelf Extraction", "Book Binding", "Reading Efficiency",
                "Wood Ants fed books. They chew wood into paper and cannot stop reading. Paper, books and bookshelves come home, and "
                        + "now and then a Bottle o' Enchanting from something they learned."));
        SPECIES.add(new Species("carpenter", "Carpenter Ant", 1, "base", "temperate", null, "2A1E18", "4A2E1C", "B87A3E", "banded", null, false,
                1.2, 1.1, "", 0, 0.7,
                List.of(Out.of("oak_planks", 26), Out.of("spruce_planks", 12), Out.of("birch_planks", 10), Out.of("stick", 20), Out.of("ladder", 5),
                        Out.of("oak_stairs", 4).atStage(2), Out.of("barrel", 1).atStage(2), Out.of("scaffolding", 3).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"crafting_table", "4"}), 3600, 1.0, 1.0,
                "oak_planks", "Plank Extraction", "Joinery", "Sawdust Saving",
                "Wood Ants fed crafting tables. Big, dark and handy: they carry their work on their backs. Planks, sticks and ladders come home, "
                        + "later barrels and scaffolding."));
        SPECIES.add(new Species("leafcutter", "Leafcutter Ant", 1, "farmer", "hot", null, "8A3E1E", "A8522A", "D8864A", "plain", null, false,
                1.05, 1.0, "", 0, 0.7,
                List.of(Out.of("jungle_leaves", 16), Out.of("oak_leaves", 12), Out.of("azalea_leaves", 5), Out.of("moss_block", 8), Out.of("vine", 6),
                        Out.of("bone_meal", 10), Out.of("flowering_azalea", 1).atStage(3).asSpecial()),
                0.9, List.of(Out.of("brown_mushroom", 40), Out.of("red_mushroom", 25), Out.of("bone_meal", 10), Out.of("glow_lichen", 4).atStage(2)),
                0.9, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"#minecraft:leaves", "8"}), 3600, 1.1, 1.0,
                "jungle_leaves", "Leaf Extraction", "Fungus Pressing", "Compost Thrift",
                "Farmer Ants fed shears. They hold cut leaves over their heads like sails and grow fungus on them at a Harvesting Site."));
        SPECIES.add(new Species("weaver", "Weaver Ant", 1, "base", "hot", null, "B8662A", "8AB04A", "E8D8A0", "plain", null, false,
                1.0, 1.0, "", 0, 0.6,
                List.of(Out.of("string", 26), Out.of("jungle_leaves", 12), Out.of("vine", 6), Out.of("white_wool", 3).atStage(2),
                        Out.of("cobweb", 1).atStage(3).asSpecial(), Out.of("lead", 0.5).atStage(3).asSpecial()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"string", "8"}), 3600, 1.0, 1.0,
                "string", "Silk Extraction", "Silk Spinning", "Thread Saving",
                "Jungle Ants fed string. They sew leaves together with silk: string and leaves come home, now and then a whole cobweb."));
        SPECIES.add(new Species("party","Party Ant", 1, "base", "any", null, "D83A8A", "3AB8E8", "FFE040", "striped", null, true,
                1.0, 1.1, "", 0, 0.6,
                List.of(Out.of("firework_rocket", 22), Out.of("paper", 12), Out.of("gunpowder", 8), Out.of("sugar", 8),
                        Out.of("note_block", 3).atStage(2), Out.of("cake", 0.5).atStage(2), Out.of("music_disc_13", 0.02).atStage(3).asSpecial(),
                        Out.of("music_disc_cat", 0.02).atStage(3).asSpecial(), Out.of("music_disc_blocks", 0.02).atStage(3).asSpecial(),
                        Out.of("music_disc_chirp", 0.02).atStage(3).asSpecial()),
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"firework_rocket", "4"}), 3600, 1.1, 1.0,
                "firework_rocket", "Confetti Extraction", "Playlist Sorting", "Party Efficiency",
                "Common Ants fed firework rockets. Loud colours and no sense of bedtime. They bring rockets, note blocks and cake, and "
                        + "at stage 3 the odd music disc."));
        SPECIES.add(new Species("pirate", "Pirate Ant", 2, "base", "temperate", null, "8A6040", "A01818", "F0C830", "striped", null, false,
                1.05, 1.2, "", 0, 0.4,
                List.of(Out.of("sand", 20), Out.of("gold_nugget", 16), Out.of("bone", 8), Out.of("map", 3), Out.of("emerald", 0.05).atStage(3).asSpecial(),
                        Out.of("compass", 1).atStage(2), Out.of("nautilus_shell", 0.3).atStage(3).asSpecial(),
                        Out.of("heart_of_the_sea", 0.01).atStage(4).asSpecial()),
                0, List.of(), 1.3, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {"gold_ingot", "4"}), 4800, 0.9, 1.0,
                "gold_nugget", "Plunder Extraction", "Loot Counting", "Boarding Efficiency",
                "Shore Ants fed gold. Weathered raiders in black hats with a nose for buried treasure: sand, gold nuggets, a rare emerald and "
                        + "nautilus shell, and once in a long while a Heart of the Sea. Arr."));
        SPECIES.add(new Species("pig", "Pig Ant", 1, "hunter", "temperate", null, "E8A0A0", "F0B4B0", "C87880", "plain", null, false,
                1.1, 1.3, "", 0, 0.5,
                List.of(Out.of("carrot", 20), Out.of("potato", 14), Out.of("beetroot", 10), Out.of("mud", 8), Out.of("brown_mushroom", 4)),
                0, List.of(), 1.0, List.<String[]>of(new String[] {"minecraft:pig", "100"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"carrot", "4"}), 3600, 1.1, 1.0, "cooked_porkchop",
                "Sty Extraction", "Truffle Sorting", "Rooting Efficiency",
                "Army Ants fed carrots on sticks. Pink, round and fond of mud. They root up carrots, potatoes and beetroot, and bring "
                        + "porkchops from a Hunting Ground. They will follow a carrot anywhere."));
        SPECIES.add(new Species("rabbit", "Rabbit Ant", 1, "hunter", "temperate", null, "A07850", "C8A27A", "F4EEE2", "plain", null, false,
                0.9, 1.0, "", 0, 0.6,
                List.of(Out.of("carrot", 20), Out.of("dandelion", 12), Out.of("short_grass", 10), Out.of("wheat_seeds", 8)),
                // A rabbit's foot is a player's drop and a rare one from ants: a grown colony's hunters bring one from a rabbit
                // in 125, about two an hour.
                0, List.of(), 0.8, List.<String[]>of(new String[] {"minecraft:rabbit", "100", "rabbit_foot", "0.008", "3"}),
                List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"carrot", "4"}), 3600, 1.3, 1.2, "rabbit_hide",
                "Warren Extraction", "Hide Stretching", "Hopping Efficiency",
                "Army Ants fed rabbit hide. Long ears, a white tail and a very large brood. They nibble up carrots and dandelions, and bring "
                        + "raw rabbit and hide from a Hunting Ground, and from stage 3, rarely, a lucky foot."));
        SPECIES.add(new Species("fisher", "Fisher Ant", 1, "base", "temperate", null, "3A5A78", "E8C020", "F4E060", "plain", null, false,
                1.0, 1.1, "", 0, 0.5,
                List.of(Out.of("cod", 24), Out.of("salmon", 16), Out.of("kelp", 8), Out.of("string", 6), Out.of("lily_pad", 4),
                        Out.of("tropical_fish", 4).atStage(2), Out.of("pufferfish", 4).atStage(2), Out.of("bowl", 2).atStage(2),
                        Out.of("leather_boots", 0.1).atStage(2).asSpecial()),
                0, List.of(), 0.9, List.of(), List.of(new String[] {ac("royal_jelly"), "1"}, new String[] {"cod", "4"}), 3600, 1.0, 1.0,
                "tropical_fish", "Net Extraction", "Fish Gutting", "Angling Efficiency",
                "Shore Ants fed fishing rods. A yellow rain hat, a rod over the shoulder and all the patience in the world. Cod and salmon "
                        + "come home, then tropical fish and pufferfish, and now and then an old boot."));
    }

    // --- How fast the ants of other mods' materials work, by how hard the material is to get in its own mod ---
    // (ore veins a chunk, the tool that mines it, the dimension or boss before it). Numbers are for one full site of a
    // grown colony; materials as easy as iron or gold keep the pace of the built-in metal ants, 400 an hour and more.

    /** A material that takes a diamond pickaxe, the Nether or a rare vein: about 60 an hour, a tenth of a gold-like one. */
    static final double HARD_RATE = 0.03;
    /** A material as rare as diamond, or behind the End or a boss: the Diamond Ant's own rate, about 5 an hour. */
    static final double DIAMOND_RATE = 0.008;
    /** A material at the end of its mod, like netherite: the Netherite Ant's own rate, about one in two hours. */
    static final double NETHERITE_RATE = 0.0025;

    /** Species whose material ends its mod: they are made like Netherite Ants (stage 4, 5,000 ants, few successes). */
    static final Set<String> HARDEST = Set.of("eclipse_alloy", "demonite", "mithril", "calorite");

    /**
     * A crystal species coloured from the gem's one colour. {@code difficulty} 2 is a gem as easy as gold (see {@link #gem}),
     * 3 a hard one ({@link #HARD_RATE}), 4 one like diamond ({@link #DIAMOND_RATE}); {@code share} scales how much of its
     * finds is the gem, {@code rock} is what it digs through.
     */
    static void gemOf(String id, String name, String colour, int difficulty, double share, String rock, List<String> mods, String description) {
        gemOf(id, name, tones(colour, "faceted"), difficulty, share, rock, mods, description);
    }

    /** The same, with the ant's colours given as body, gaster, accent, pattern and crystal. */
    static void gemOf(String id, String name, String[] look, int difficulty, double share, String rock, List<String> mods, String description) {
        if (difficulty <= 2) {
            gem(id, name, look[0], look[1], look[2], look[4], mods, description);
            return;
        }
        boolean rare = difficulty >= 4;
        List<Out> outputs = List.of(Out.of("c:gems/" + id, (rare ? 6 : 20) * share).asTag(), Out.of("c:dusts/" + id, rare ? 2 : 4).asTag(),
                Out.of(rock, rare ? 40 : 22), Out.of("calcite", 4).atStage(2));
        SPECIES.add(new Species(id, name, 3, "mineral", "underground", null, look[0], look[1], look[2], "faceted", look[4], true,
                1.0, 1.5, "crystal", 0.6, rare ? DIAMOND_RATE : HARD_RATE, outputs,
                0, List.of(), 1.0, List.of(), List.of(new String[] {ac("royal_jelly"), "4"}, new String[] {ac("crystal_ant_food"), "6"}), 6000, 0.8, 0.85,
                null, title(id) + " Extraction", "Gem Cutting", title(id) + " Efficiency",
                description + (rare ? " As rare from ants as from the rock." : " They find few.")));
        NEEDS_MODS.put(id, mods);
    }

    // --- Alloys: metals that are mixed, not mined ---

    /**
     * An alloy ant works at this share of the pace of the ant of its hardest metal. The ants save the work of mixing it,
     * so an alloy must always come a little slower than what it is made of.
     */
    static final double ALLOY_PACE = 0.7;

    /**
     * A metal species for an alloy, which has no ore: its nuggets and dust through the c: tags, an ingot now and then.
     * {@code difficulty} is that of its hardest metal, as in {@link #compat}; its rate is {@link #ALLOY_PACE} of that
     * metal's ant. It is made from the ant of one of its metals, {@code from}, fed the alloy, after the research of
     * another, {@code requires}; of {@code mods} only those that also add both metals count.
     */
    static void alloy(String id, String name, String colour, boolean glow, int difficulty, String from, String requires, List<String> mods,
                      String hint, String description) {
        String[] look = tones(colour, "metallic");
        int tier = Math.min(difficulty, 3);
        double metalRate = difficulty == 1 ? 0.6 : difficulty == 2 ? 0.45 : difficulty == 3 ? HARD_RATE : DIAMOND_RATE;
        double rate = Math.round(metalRate * ALLOY_PACE * 10000) / 10000.0;
        SPECIES.add(new Species(id, name, tier, "mineral", "any", null, look[0], look[1], look[2], look[3], look[4], glow, 1.05, 1.2 + tier * 0.15,
                "mineral", 0.3 + tier * 0.15, rate,
                List.of(Out.of("c:nuggets/" + id, 40).asTag(), Out.of("c:dusts/" + id, difficulty >= 4 ? 3 : 10).atStage(difficulty >= 3 ? 1 : 2).asTag(),
                        Out.of("cobblestone", 15), Out.of("c:ingots/" + id, 2).atStage(3).asSpecial().asTag()),
                0, List.of(), 1.1, List.of(), List.of(new String[] {ac("royal_jelly"), "2"}, new String[] {ac("mineral_ant_food"), "4"}),
                3600 + tier * 1200, 0.9, 0.9, null, title(id) + " Extraction", "Alloy Casting", title(id) + " Efficiency", description));
        NEEDS_MODS.put(id, mods.stream().filter(mod -> List.of(from, requires).stream()
                .allMatch(metal -> !NEEDS_MODS.containsKey(metal) || NEEDS_MODS.get(metal).contains(mod))).toList());
        made(id, from, "#c:ingots/" + id, difficulty == 1 ? "basic" : "advanced", requires, hint);
    }

    /**
     * The nine alloys of All The Ores (other mods make some of them too). Difficulty is that of the hardest metal in
     * one: bronze, brass and steel like iron; those with nickel, silver or gold like gold; enderium like diamond, for
     * its platinum. Each then comes slower than that metal's own ant, by {@link #ALLOY_PACE}.
     */
    static void alloys() {
        alloy("bronze", "Bronze Ant", "C69317", false, 1, "tin", "tin", List.of("alltheores", "thermal", "mekanism", "railcraft", "modern_industrialization"),
                "Three parts copper to one of tin.", "Tin Ants fed bronze. They bring bronze ready mixed: nuggets, dust and the odd ingot.");
        alloy("brass", "Brass Ant", "CE9B3E", false, 1, "zinc", "zinc", List.of("alltheores", "create", "railcraft"),
                "Three parts copper to one of zinc.", "Zinc Ants fed brass. They bring brass ready mixed: nuggets, dust and the odd ingot.");
        alloy("steel", "Steel Ant", "797A7F", false, 1, "iron", "coal",
                List.of("alltheores", "thermal", "mekanism", "immersiveengineering", "railcraft", "oritech", "energizedpower", "modern_industrialization"),
                "Iron made hard with coal.", "Iron Ants fed steel. They bring steel with no furnace: nuggets, dust and the odd ingot.");
        alloy("invar", "Invar Ant", "AAB0A5", false, 2, "nickel", "nickel", List.of("alltheores", "thermal", "railcraft", "modern_industrialization"),
                "Two parts iron to one of nickel.", "Nickel Ants fed invar. They bring invar ready mixed: nuggets, dust and the odd ingot.");
        alloy("constantan", "Constantan Ant", "C2A958", false, 2, "copper", "nickel", List.of("alltheores", "thermal", "immersiveengineering"),
                "Copper and nickel in equal parts.", "Copper Ants fed constantan. They bring constantan ready mixed: nuggets, dust and the odd ingot.");
        alloy("electrum", "Electrum Ant", "E4DC76", false, 2, "gold", "silver", List.of("alltheores", "thermal", "immersiveengineering"),
                "Gold and silver in equal parts.", "Gold Ants fed electrum. They bring electrum ready mixed: nuggets, dust and the odd ingot.");
        alloy("signalum", "Signalum Ant", "C25D11", true, 2, "redstone", "silver", List.of("alltheores", "thermal"),
                "Copper and silver, red with redstone.", "Redstone Ants fed signalum. They bring signalum ready mixed: nuggets, dust and the odd ingot.");
        alloy("lumium", "Lumium Ant", "FFDE88", true, 2, "tin", "silver", List.of("alltheores", "thermal"),
                "Tin and silver that shine with glowstone.", "Tin Ants fed lumium. They glow, and bring lumium ready mixed: nuggets, dust and the odd ingot.");
        alloy("enderium", "Enderium Ant", "0C5151", true, 4, "lead", "platinum", List.of("alltheores", "thermal"),
                "Lead and platinum, bound with ender pearls.", "Lead Ants fed enderium. Slower than Platinum Ants; they bring enderium nuggets and dust.");
        // Silicon is made too: quartz or certus quartz dust through a furnace. Slower than a Certus Ant, by the same share.
        pack("silicon", "Silicon Ant", 2, "hot", tones("6E7B88", "faceted"), false, "mineral", Math.round(0.45 * ALLOY_PACE * 10000) / 10000.0,
                List.of(Out.of("c:silicon", 30).asTag(), Out.of("sand", 22), Out.of("gravel", 12)),
                List.of("ae2", "enderio", "energizedpower", "oritech", "refinedstorage", "stellaris"),
                "Desert Ants fed silicon. They sift it from the sand ready smelted, grey and brittle, for circuits and solar panels.");
        made("silicon", "desert", "#c:silicon", "advanced", null, "Quartz and sand, smelted to a grey wafer.");
    }

    /** The rest of the big packs' materials: Silent's Gems' other gems, and what no ore of the Overworld gives. */
    static void packMaterials() {
        // --- Silent's Gems: one crystal ant per gem, coloured from the gem ---
        // Difficulty by where the gem really generates in a pack: an Overworld vein like gold's is 2; one small deep vein a
        // chunk, or the Nether (a diamond pickaxe) is 3; the End (a netherite pickaxe) is 4.
        List<String> gems = List.of("silentgems");
        String deep = "cobbled_deepslate";
        Object[][] silent = {
                // id, name, colour, made from, description, research hint, difficulty, share, rock
                {"alexandrite", "Alexandrite Ant", "943BCA", "amethyst", "Amethyst Ants fed alexandrite. Violet in one light and green in another; bring alexandrite.",
                        "A gem that changes colour with the light.", 3, 1.0, deep},
                {"ammolite", "Ammolite Ant", "A022CC", "amethyst", "Amethyst Ants fed ammolite, old sea shells turned to gem. Bring ammolite.",
                        "A fossil shell that shines in every colour.", 4, 1.0, "end_stone"},
                {"aquamarine", "Aquamarine Ant", "31B3B8", "lapis", "Lapis Ants fed aquamarine. Sea blue; bring aquamarine.", "A gem the colour of shallow sea.",
                        2, 1.0, deep},
                {"black_diamond", "Black Diamond Ant", "5F524D", "coal", "Coal Ants fed black diamonds. Bring black diamonds.", "A diamond as dark as coal.",
                        3, 0.5, "netherrack"},
                {"carnelian", "Carnelian Ant", "C64D2A", "redstone", "Redstone Ants fed carnelian. Warm orange-red; bring carnelian.", "A warm orange-red stone.",
                        3, 1.0, "netherrack"},
                {"citrine", "Citrine Ant", "A67D1C", "gold", "Gold Ants fed citrine. Bring citrine.", "A yellow quartz.", 3, 1.0, "netherrack"},
                {"garnet", "Garnet Ant", "8A141E", "redstone", "Redstone Ants fed garnets. Dark red; bring garnets.", "A dark red gem.", 2, 1.0, deep},
                {"heliodor", "Heliodor Ant", "AC9728", "gold", "Gold Ants fed heliodor. Bring heliodor.", "A golden gem named for the sun.", 3, 1.0, deep},
                {"iolite", "Iolite Ant", "6952CD", "lapis", "Lapis Ants fed iolite. Violet-blue; bring iolite.", "A violet-blue gem sailors once steered by.",
                        3, 1.0, deep},
                {"kyanite", "Kyanite Ant", "3C9FB3", "lapis", "Lapis Ants fed kyanite. Bring kyanite.", "A blue gem that grows in blades.", 4, 1.0, "end_stone"},
                {"moldavite", "Moldavite Ant", "8FB343", "amethyst", "Amethyst Ants fed moldavite. Bottle green; bring moldavite.",
                        "Green glass made where a star fell.", 3, 1.0, "netherrack"},
                {"opal", "Opal Ant", "CED7DF", "amethyst", "Amethyst Ants fed opals. Milk white; bring opals.", "A milky gem with fire inside.", 3, 1.0, deep},
                {"pearl", "Pearl Ant", "CDC6BC", "amethyst", "Amethyst Ants fed pearls. Bring pearls.", "A round, pale gem.", 4, 1.0, "netherrack"},
                {"rose_quartz", "Rose Quartz Ant", "CC6DA2", "amethyst", "Amethyst Ants fed rose quartz. Pink; bring rose quartz.", "A pink quartz.",
                        4, 1.0, "end_stone"},
                {"tanzanite", "Tanzanite Ant", "54349F", "lapis", "Lapis Ants fed tanzanite. Deep blue-violet; bring tanzanite.", "A deep blue-violet gem.",
                        3, 1.0, "netherrack"},
                {"topaz", "Topaz Ant", "B0632B", "gold", "Gold Ants fed topaz. Orange and clear; bring topaz.", "An orange gem, clear and hard.", 2, 1.0, deep},
                {"turquoise", "Turquoise Ant", "4FD2B2", "copper", "Copper Ants fed turquoise. Blue-green; bring turquoise.",
                        "A blue-green stone that grows near copper.", 3, 1.0, deep},
                {"white_diamond", "White Diamond Ant", "BBAEB7", "diamond", "Diamond Ants fed white diamonds. Bring white diamonds.",
                        "A diamond without a trace of colour.", 4, 0.5, "end_stone"},
        };
        Map<String, List<String>> shared = Map.of("topaz", List.of("silentgems", "arsmagicalegacy", "irons_jewelry"),
                "garnet", List.of("silentgems", "irons_jewelry"),
                "aquamarine", List.of("silentgems", "astralsorcery"));
        for (Object[] gem : silent) {
            String id = (String) gem[0];
            gemOf(id, (String) gem[1], (String) gem[2], (Integer) gem[6], (Double) gem[7], (String) gem[8], shared.getOrDefault(id, gems), (String) gem[4]);
            made(id, (String) gem[3], "#c:gems/" + id, "advanced", null, (String) gem[5]);
        }
        // Chaos ore is commoner than gold and drops up to three essence: easy but for the diamond pickaxe.
        pack("chaos", "Chaos Ant", 2, "underground", tones("C6B2AF", "speckled"), true, "crystal", 0.4,
                List.of(Out.of("silentgems:chaos_essence", 34), Out.of("cobbled_deepslate", 16), Out.of("cobblestone", 10)),
                gems, "Amethyst Ants fed chaos essence. Restless and pale; bring chaos essence for Silent's Gems.");
        made("chaos", "amethyst", "silentgems:chaos_essence", "advanced", null, "A pale essence that will not sit still.");

        // --- Just Dire Things: its four materials, each ant from the one before, as its goo makes them ---
        // Each tier of goo eats a block of iron, gold, diamond, netherite, and needs the Nether, the End, an Ancient City.
        List<String> dire = List.of("justdirethings");
        pack("ferricore", "Ferricore Ant", 2, "any", tones("779C9D", "metallic"), false, "mineral", 0.45,
                List.of(Out.of("c:raw_materials/ferricore", 8).asTag(), Out.of("cobblestone", 30),
                        Out.of("c:ingots/ferricore", 1).atStage(3).asSpecial().asTag()),
                dire, "Iron Ants fed ferricore. Bring raw ferricore for Just Dire Things, with no goo to feed.");
        made("ferricore", "iron", "#c:ingots/ferricore", "advanced", null, "Iron changed by a living goo.");
        pack("blazegold", "Blazegold Ant", 3, "hot", tones("C06A4A", "metallic"), true, "nether", HARD_RATE,
                List.of(Out.of("c:raw_materials/blazegold", 20).asTag(), Out.of("netherrack", 30),
                        Out.of("c:ingots/blazegold", 1).atStage(3).asSpecial().asTag()),
                dire, "Ferricore Ants fed blazegold. Slow; bring raw blazegold for Just Dire Things.");
        made("blazegold", "ferricore", "#c:ingots/blazegold", "advanced", "ferricore", "Gold that took on the Nether's fire.");
        pack("celestigem", "Celestigem Ant", 3, "any", tones("50BBB1", "faceted"), true, "crystal", DIAMOND_RATE,
                List.of(Out.of("justdirethings:celestigem", 3), Out.of("cobblestone", 24), Out.of("end_stone", 20)),
                dire, "Blazegold Ants fed celestigems. Rarer than diamonds from ants too; bring celestigems for Just Dire Things.");
        made("celestigem", "blazegold", "justdirethings:celestigem", "advanced", "blazegold", "A sea-green gem grown from diamond.");
        pack("eclipse_alloy", "Eclipse Alloy Ant", 3, "end", tones("4C616B", "metallic"), false, "crystal", NETHERITE_RATE,
                List.of(Out.of("end_stone", 50), Out.of("obsidian", 30), Out.of("c:raw_materials/eclipsealloy", 5).atStage(3).asSpecial().asTag()),
                dire, "Celestigem Ants fed eclipse alloy. They bring raw eclipse alloy about as often as Netherite Ants bring scrap.");
        made("eclipse_alloy", "celestigem", "#c:ingots/eclipsealloy", "experimental", "celestigem", "A dark alloy that drinks the light.");

        // --- The Twilight Forest: its three metals ---
        // Ironwood is crafted from iron and a root; steeleaf is only found in chests; knightmetal is nine shards from knights.
        List<String> twilight = List.of("twilightforest");
        pack("ironwood", "Ironwood Ant", 2, "temperate", tones("8F875F", "banded"), false, "mineral", 0.45,
                List.of(Out.of("c:raw_materials/ironwood", 8).asTag(), Out.of("twilightforest:liveroot", 10), Out.of("stick", 12), Out.of("moss_block", 8)),
                twilight, "Wood Ants fed ironwood, the living metal of the Twilight Forest. Bring raw ironwood and liveroot.");
        made("ironwood", "wood", "#c:ingots/ironwood", "advanced", null, "A metal that grows like a root.");
        pack("steeleaf", "Steeleaf Ant", 3, "temperate", tones("4F7A3A", "banded"), false, "mineral", HARD_RATE,
                List.of(Out.of("c:ingots/steeleaf", 20).asTag(), Out.of("oak_leaves", 20), Out.of("vine", 5), Out.of("stick", 5)),
                twilight, "Ironwood Ants fed steeleaf. Slow; bring the Twilight Forest's steel-hard leaves, which no one can craft.");
        made("steeleaf", "ironwood", "#c:ingots/steeleaf", "advanced", "ironwood", "A leaf as hard as steel.");
        pack("knightmetal", "Knightmetal Ant", 3, "any", tones("99A788", "metallic"), false, "mineral", DIAMOND_RATE,
                List.of(Out.of("twilightforest:armor_shard", 40), Out.of("cobblestone", 10),
                        Out.of("c:raw_materials/knightmetal", 0.5).atStage(3).asSpecial().asTag()),
                twilight, "Iron Ants fed knightmetal. Very slow; bring armor shards, nine to an ingot of knightmetal.");
        made("knightmetal", "iron", "#c:ingots/knightmetal", "advanced", null, "The metal of the goblin knights' armour.");

        // --- One material each ---
        // Rogdorium: the Undergarden's last ore, three biomes, a tool tier above netherite. Nuggets, nine to a crystal.
        pack("rogdorium", "Rogdorium Ant", 3, "underground", tones("7293A3", "faceted"), true, "crystal", DIAMOND_RATE,
                List.of(Out.of("c:nuggets/rogdorium", 25).asTag(), Out.of("cobbled_deepslate", 22),
                        Out.of("c:ingots/rogdorium", 0.5).atStage(3).asSpecial().asTag()),
                List.of("undergarden"), "Froststeel Ants fed rogdorium, the pale crystal under the Undergarden's deepest rock. Very slow; bring rogdorium nuggets.");
        made("rogdorium", "froststeel", "#c:ingots/rogdorium", "advanced", "froststeel", "A pale crystal from under the deepest rock of the deep.");
        // Soulium: plentiful in the Nether's soulstone, any pickaxe; the dust is half an ingot.
        pack("soulium", "Soulium Ant", 2, "nether", tones("7A4A32", "speckled"), false, "nether", 0.4,
                List.of(Out.of("mysticalagriculture:soulium_dust", 30), Out.of("soul_sand", 16), Out.of("soul_soil", 10), Out.of("netherrack", 10)),
                List.of("mysticalagriculture"), "Soul Ants fed soulium dust. Bring soulium for Mystical Agriculture.");
        made("soulium", "soul", "mysticalagriculture:soulium_dust", "advanced", null, "A brown dust that holds what a mob leaves behind.");
        // Novite: 25 ore blocks in a meteor crater, a crater every 1,900 chunks or so.
        pack("novite", "Novite Ant", 3, "any", tones("8B6981", "metallic"), false, "mineral", DIAMOND_RATE,
                List.of(Out.of("c:raw_materials/novite", 6).asTag(), Out.of("cobblestone", 44), Out.of("c:ingots/novite", 0.3).atStage(3).asSpecial().asTag()),
                List.of("stellaris"), "Desh Ants fed novite, the violet metal of fallen stars. Very slow; bring raw novite for Stellaris.");
        made("novite", "desh", "#c:ingots/novite", "advanced", "desh", "A violet metal that fell from the sky.");
        // Firestone: beside Nether lava, a diamond pickaxe, a crusher; a player needs one.
        pack("firestone", "Firestone Ant", 3, "nether", tones("AE3102", "speckled"), true, "nether", 0.3,
                List.of(Out.of("netherrack", 30), Out.of("magma_block", 14), Out.of("blackstone", 10),
                        Out.of("railcraft:raw_firestone", 0.06).atStage(3).asSpecial()),
                List.of("railcraft"), "Fire Ants fed raw firestone, the burning stone under the Nether's lava. Now and then they bring one.");
        made("firestone", "fire", "railcraft:raw_firestone", "advanced", null, "A stone from under the lava that never cools.");
        LAB_AMOUNTS.put("fire_to_firestone", 2);
        // Demonite: only in the last dungeon of Neo Vitae; its metal equals netherite.
        pack("demonite", "Demonite Ant", 3, "nether", tones("78B1B3", "metallic"), false, "nether", NETHERITE_RATE,
                List.of(Out.of("netherrack", 50), Out.of("blackstone", 30), Out.of("c:raw_materials/hellforged", 5).atStage(3).asSpecial().asTag()),
                List.of("neovitae"), "Soul Ants fed hellforged metal. They bring raw demonite, the ore of Neo Vitae's last dungeon, about as often as "
                        + "Netherite Ants bring scrap.");
        made("demonite", "soul", "#c:ingots/hellforged", "experimental", null, "A pale metal forged where demons live.");
        // Grains of infinity: free wherever fire burns on bedrock or deepslate.
        pack("infinity", "Infinity Ant", 1, "underground", tones("6C6C6C", "speckled"), false, "mineral", 0.3,
                List.of(Out.of("cobbled_deepslate", 30), Out.of("c:dusts/grains_of_infinity", 20).asTag(), Out.of("flint", 10)),
                List.of("enderio"), "Deepslate Ants fed grains of infinity. They scrape the bedrock for the grey dust Ender IO starts with.");
        made("infinity", "deepslate", "#c:dusts/grains_of_infinity", "basic", null, "A grey dust left where fire burns on bedrock.");
        // Corrobonite: the Aether's deepest layer, its top tool, and a machine to restore each crystal.
        pack("corrobonite", "Corrobonite Ant", 3, "any", tones("6BC397", "faceted"), true, "crystal", DIAMOND_RATE,
                List.of(Out.of("aether_ii:corrobonite_crystal", 6), Out.of("cobblestone", 44)),
                List.of("aether_ii"), "Zanite Ants fed corrobonite crystals from the Aether. Very slow; bring corrobonite.");
        made("corrobonite", "zanite", "aether_ii:corrobonite_crystal", "advanced", "zanite", "A green crystal full of the sky's energy.");
        // Neptunium: about one nugget in 300 casts of a fishing rod.
        pack("neptunium", "Neptunium Ant", 3, "temperate", tones("3FCDA6", "metallic"), true, "mineral", 0.2,
                List.of(Out.of("prismarine_shard", 24), Out.of("sand", 20), Out.of("kelp", 10),
                        Out.of("c:nuggets/neptunium", 0.25).atStage(3).asSpecial().asTag()),
                List.of("aquaculture"), "Reef Ants fed neptunium, the sea metal only fishing brings up. Now and then they bring a nugget.");
        made("neptunium", "reef", "#c:ingots/neptunium", "advanced", null, "A sea-green metal only fishermen find.");
        LAB_AMOUNTS.put("reef_to_neptunium", 2);
        moreMaterials();
    }

    /**
     * The materials of mods that are in All the Mods 10 and not (yet) on newer versions of the game. Their ids, ore
     * generation and tool tiers were read from the mods' sources and never run: a species whose id is off simply does not load.
     */
    static void moreMaterials() {
        // --- Modern Industrialization ---
        List<String> industrialization = List.of("modern_industrialization");
        // Antimony: more veins than iron's lower layers, a stone pickaxe.
        String[] antimony = tones("B4B4D2", "metallic");
        compat("antimony", "Antimony Ant", 1, "underground", antimony[0], antimony[1], antimony[2], antimony[3], null, false, "antimony",
                industrialization, "Iron Ants fed antimony, a brittle white metal. Bring antimony for Modern Industrialization.");
        made("antimony", "iron", "#c:ingots/antimony", "basic", null, "A brittle silver-white metal that hardens others.");
        // Tungsten: veins like gold's, but the ingot belongs to the mod's late machines.
        pack("tungsten", "Tungsten Ant", 3, "underground", tones("8760AD", "metallic"), false, "mineral", HARD_RATE,
                List.of(Out.of("c:nuggets/tungsten", 30).asTag(), Out.of("cobbled_deepslate", 14), Out.of("c:raw_materials/tungsten", 10).asTag()),
                industrialization, "Iron Ants fed tungsten, the metal that will not melt. Slow; bring tungsten.");
        made("tungsten", "iron", "#c:ingots/tungsten", "advanced", null, "A violet-grey metal that will not melt.");
        // Monazite: two tiny veins a chunk, rarer than diamond by count.
        pack("monazite", "Monazite Ant", 3, "underground", tones("96248E", "speckled"), false, "mineral", HARD_RATE,
                List.of(Out.of("c:dusts/monazite", 9).asTag(), Out.of("cobblestone", 30), Out.of("sand", 8)),
                industrialization, "Redstone Ants fed monazite. Slow; bring monazite dust for Modern Industrialization.");
        made("monazite", "redstone", "#c:dusts/monazite", "advanced", null, "A purple sand full of rare elements.");
        pack("bauxite", "Bauxite Ant", 1, "hot", tones("C86400", "speckled"), false, "mineral", 0.5,
                List.of(Out.of("c:dusts/bauxite", 12).asTag(), Out.of("cobblestone", 30), Out.of("clay_ball", 8)),
                industrialization, "Iron Ants fed bauxite, the red earth aluminum comes from. Bring bauxite dust.");
        made("bauxite", "iron", "#c:dusts/bauxite", "basic", null, "A red-brown earth that aluminum comes from.");
        pack("lignite", "Lignite Ant", 1, "temperate", tones("644646", "speckled"), false, "mineral", 0.6,
                List.of(Out.of("c:gems/lignite_coal", 40).asTag(), Out.of("cobblestone", 14), Out.of("dirt", 8)),
                industrialization, "Coal Ants fed lignite coal. Bring lignite for Modern Industrialization's boilers.");
        made("lignite", "coal", "#c:gems/lignite_coal", "basic", null, "A young, brown coal.");

        // --- Extreme Reactors: its two crystals (its yellorite counts as uranium). Both are common where they are: the End
        // is what makes anglesite hard. ---
        List<String> reactors = List.of("bigreactors");
        pack("anglesite", "Anglesite Ant", 3, "end", tones("BD9522", "faceted"), true, "crystal", HARD_RATE,
                List.of(Out.of("bigreactors:anglesite_crystal", 20), Out.of("end_stone", 30)),
                reactors, "Amethyst Ants fed anglesite crystals from the End. Slow; bring anglesite for Extreme Reactors.");
        made("anglesite", "amethyst", "bigreactors:anglesite_crystal", "advanced", null, "A golden crystal from the End.");
        pack("benitoite", "Benitoite Ant", 2, "nether", tones("5AA8C1", "faceted"), true, "crystal", 0.4,
                List.of(Out.of("bigreactors:benitoite_crystal", 30), Out.of("netherrack", 24)),
                reactors, "Lapis Ants fed benitoite crystals from the Nether. Bring benitoite for Extreme Reactors.");
        made("benitoite", "lapis", "bigreactors:benitoite_crystal", "advanced", null, "A blue crystal from the Nether.");

        // --- One material each ---
        // Draconium: a vein in ten chunks outside the End, and the base of an end-game mod.
        pack("draconium", "Draconium Ant", 3, "any", tones("7824B1", "faceted"), true, "crystal", DIAMOND_RATE,
                List.of(Out.of("c:dusts/draconium", 6).asTag(), Out.of("end_stone", 24), Out.of("cobbled_deepslate", 20),
                        Out.of("c:ingots/draconium", 0.3).atStage(3).asSpecial().asTag()),
                List.of("draconicevolution"), "Diamond Ants fed draconium. Very slow; bring draconium dust for Draconic Evolution.");
        made("draconium", "diamond", "#c:ingots/draconium", "advanced", null, "A purple metal with a dragon's power in it.");
        gemOf("black_quartz", "Black Quartz Ant", "3A4452", 2, 1.0, "cobblestone", List.of("actuallyadditions"),
                "Coal Ants fed black quartz. Bring black quartz for Actually Additions.");
        made("black_quartz", "coal", "#c:gems/black_quartz", "basic", null, "A quartz as dark as night.");
        // Mithril: built as a second ancient debris: a netherite pickaxe, never in the open, four scraps to an ingot.
        pack("mithril", "Mithril Ant", 3, "underground", tones("6E848C", "metallic"), false, "mineral", NETHERITE_RATE,
                List.of(Out.of("cobbled_deepslate", 50), Out.of("tuff", 30), Out.of("c:raw_materials/mithril", 22).atStage(3).asSpecial().asTag()),
                List.of("irons_spellbooks"), "Deepslate Ants fed mithril, the pale metal at the bottom of the world. They bring raw mithril "
                        + "about as often as Netherite Ants bring scrap.");
        made("mithril", "deepslate", "#c:ingots/mithril", "experimental", null, "A pale metal from the very bottom of the world.");
        // Starmetal: costs only iron, but takes Astral Sorcery's second stage to make.
        pack("starmetal", "Starmetal Ant", 3, "any", tones("2E55B4", "metallic"), true, "mineral", HARD_RATE,
                List.of(Out.of("c:raw_materials/starmetal", 20).asTag(), Out.of("cobblestone", 30),
                        Out.of("c:ingots/starmetal", 1).atStage(3).asSpecial().asTag()),
                List.of("astralsorcery"), "Iron Ants fed starmetal. Slow; bring raw starmetal for Astral Sorcery, whatever the sky is doing.");
        made("starmetal", "iron", "#c:ingots/starmetal", "advanced", null, "Iron that lay under the stars too long.");
        // Xychorium: closed source, so judged from the pack's own ore table only: easy.
        pack("xychorium", "Xychorium Ant", 2, "underground", tones("4A8AD8", "faceted"), true, "crystal", 0.45,
                List.of(Out.of("xycraft_world:xychorium_gem_blue", 8), Out.of("xycraft_world:xychorium_gem_green", 8),
                        Out.of("xycraft_world:xychorium_gem_red", 8), Out.of("xycraft_world:xychorium_gem_dark", 8),
                        Out.of("xycraft_world:xychorium_gem_light", 8), Out.of("cobblestone", 16)),
                List.of("xycraft_world"), "Amethyst Ants fed xychorium. Bring its gems in all five colours for XyCraft.");
        made("xychorium", "amethyst", "#c:gems/xychorium", "advanced", null, "A gem that comes in five colours.");

        // --- Ad Astra: each metal is a planet further out. Ostrum takes the second rocket, calorite the third. ---
        List<String> astra = List.of("ad_astra");
        pack("ostrum", "Ostrum Ant", 3, "any", tones("6D4B56", "metallic"), false, "mineral", DIAMOND_RATE,
                List.of(Out.of("c:nuggets/ostrum", 36).asTag(), Out.of("cobblestone", 10), Out.of("c:raw_materials/ostrum", 2).asTag()),
                astra, "Desh Ants fed ostrum, the dusky metal of the red planet. Very slow; bring ostrum for Ad Astra.");
        made("ostrum", "desh", "#c:ingots/ostrum", "advanced", "desh", "A dusky metal from the red planet.");
        pack("calorite", "Calorite Ant", 3, "hot", tones("89233C", "metallic"), true, "mineral", NETHERITE_RATE,
                List.of(Out.of("netherrack", 50), Out.of("basalt", 30), Out.of("c:nuggets/calorite", 165).atStage(3).asSpecial().asTag()),
                astra, "Ostrum Ants fed calorite, the red metal of the hottest planet. They bring a few calorite nuggets an hour.");
        made("calorite", "ostrum", "#c:ingots/calorite", "experimental", "ostrum", "A red metal from the hottest planet.");
        // Ice shards: common on the Moon; the rocket is the cost.
        pack("ice_shard", "Ice Shard Ant", 2, "cold", tones("93A8C1", "faceted"), false, "crystal", 0.05,
                List.of(Out.of("ad_astra:ice_shard", 24), Out.of("ice", 16), Out.of("snowball", 10)),
                astra, "Snow Ants fed ice shards. Slow; bring ice shards for Ad Astra.");
        made("ice_shard", "snow", "ad_astra:ice_shard", "advanced", null, "A shard of ice from another world.");

        // --- Eternal Starlight: the ores of its dimension. Deepsilver and malarite are its iron; starlit diamond its diamond. ---
        List<String> starlight = List.of("eternal_starlight");
        String[] deepsilver = tones("787CA4", "metallic");
        compat("deepsilver", "Deepsilver Ant", 2, "any", deepsilver[0], deepsilver[1], deepsilver[2], deepsilver[3], deepsilver[4], false, "deepsilver",
                starlight, "Iron Ants fed deepsilver, the blue-grey silver of the Starlight dimension. Bring deepsilver.");
        made("deepsilver", "iron", "#c:ingots/deepsilver", "advanced", null, "A blue-grey silver from a land of endless night.");
        gemOf("malarite", "Malarite Ant", "5A5268", 2, 1.0, "cobblestone", starlight, "Amethyst Ants fed malarite. Bring malarite for Eternal Starlight.");
        made("malarite", "amethyst", "#c:gems/malarite", "advanced", null, "A dark gem from a land of endless night.");
        gemOf("starcore", "Starcore Ant", "9C5E4B", 3, 1.0, "cobbled_deepslate", starlight,
                "Redstone Ants fed starcores. Warm as embers; bring starcores for Eternal Starlight.");
        made("starcore", "redstone", "#c:gems/starcore", "advanced", null, "A warm stone like a small ember.");
        gemOf("starlit_diamond", "Starlit Diamond Ant", "C9C2ED", 4, 1.0, "cobbled_deepslate", starlight,
                "Diamond Ants fed starlit diamonds. Bring starlit diamonds for Eternal Starlight.");
        made("starlit_diamond", "diamond", "#c:gems/starlit_diamond", "advanced", null, "A diamond that keeps the starlight.");
        gemOf("thioquartz", "Thioquartz Ant", "91986E", 3, 1.0, "cobbled_deepslate", starlight,
                "Amethyst Ants fed thioquartz. Bring thioquartz shards for Eternal Starlight.");
        made("thioquartz", "amethyst", "#c:gems/thioquartz", "advanced", null, "A yellow-green quartz that smells of sulfur.");
    }

    // --- Evolutions, hybrids, mutations ---

    // Evolutions that happen only in a Formicarium in this dimension (the colony can live anywhere afterwards).
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
                // The ants for fun. None is fed ant food (eggs, bread, cookies): feeding a colony must not change it by accident.
                // Milk and cake come one at a time, so those two are sure after eight.
                {"army_to_cow", "army", "cow", "milk_bucket", 0.15, 8, 2, 300},
                {"army_to_chicken", "army", "chicken", "feather", 0.08, 24, 2, 300},
                {"army_to_sheep", "army", "sheep", "#minecraft:wool", 0.08, 24, 2, 300},
                {"farmer_to_baker", "farmer", "baker", "cake", 0.15, 8, 2, 200},
                {"wood_to_bookworm", "wood", "bookworm", "book", 0.08, 24, 2, 200},
                {"wood_to_carpenter", "wood", "carpenter", "crafting_table", 0.08, 24, 2, 200},
                {"farmer_to_leafcutter", "farmer", "leafcutter", "shears", 0.15, 8, 2, 200},
                {"jungle_to_weaver", "jungle", "weaver", "string", 0.08, 24, 2, 200},
                {"common_to_party", "common", "party", "firework_rocket", 0.08, 24, 2, 200},
                {"shore_to_pirate", "shore", "pirate", "gold_ingot", 0.06, 24, 2, 300},
                // Carrots on sticks and fishing rods come one at a time too. Carrots and raw rabbit are ant food: not those.
                {"army_to_pig", "army", "pig", "carrot_on_a_stick", 0.15, 8, 2, 300},
                {"army_to_rabbit", "army", "rabbit", "rabbit_hide", 0.08, 24, 2, 300},
                {"shore_to_fisher", "shore", "fisher", "fishing_rod", 0.15, 8, 2, 300},
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
                // All The Ores' other ores.
                {"gold_to_platinum", "gold", "platinum", "#c:ingots/platinum", 0.05, 32, 3, 1000},
                {"platinum_to_iridium", "platinum", "iridium", "#c:ingots/iridium", 0.04, 32, 3, 1500},
                {"amethyst_to_ruby", "amethyst", "ruby", "#c:gems/ruby", 0.06, 24, 2, 400},
                {"lapis_to_sapphire", "lapis", "sapphire", "#c:gems/sapphire", 0.06, 24, 2, 400},
                {"amethyst_to_peridot", "amethyst", "peridot", "#c:gems/peridot", 0.06, 24, 2, 400},
                {"redstone_to_cinnabar", "redstone", "cinnabar", "#c:gems/cinnabar", 0.06, 24, 2, 400},
                {"desert_to_salt", "desert", "salt", "#c:dusts/salt", 0.08, 24, 2, 200},
                // Allthemodium's metals, past netherite: the very end of the game, so made in any dimension.
                {"netherite_to_allthemodium", "netherite", "allthemodium", "#c:ingots/allthemodium", 0.02, 48, 4, 5000},
                {"allthemodium_to_vibranium", "allthemodium", "vibranium", "#c:ingots/vibranium", 0.02, 48, 4, 6000},
                {"vibranium_to_unobtainium", "vibranium", "unobtainium", "#c:ingots/unobtainium", 0.02, 48, 4, 8000},
                // The ores and gems of the mods of big packs.
                {"redstone_to_uraninite", "redstone", "uraninite", "#c:raw_materials/uraninite", 0.06, 24, 2, 400},
                {"stone_to_inferium", "stone", "inferium", "mysticalagriculture:inferium_essence", 0.08, 24, 2, 200},
                {"amethyst_to_arcane", "amethyst", "arcane", "#c:gems/arcane_crystal", 0.06, 24, 2, 400},
                {"silver_to_iesnium", "silver", "iesnium", "#c:ingots/iesnium", 0.06, 32, 2, 600},
                {"coal_to_dark_gem", "coal", "dark_gem", "evilcraft:dark_gem", 0.06, 24, 2, 400},
                {"deepslate_to_bort", "deepslate", "bort", "#c:gems/bort", 0.06, 24, 2, 400},
                {"iron_to_crimson_iron", "iron", "crimson_iron", "#c:ingots/crimson_iron", 0.07, 24, 2, 400},
                {"chorus_to_azure_silver", "chorus", "azure_silver", "#c:ingots/azure_silver", 0.06, 32, 2, 600},
                {"iron_to_cloggrum", "iron", "cloggrum", "#c:ingots/cloggrum", 0.07, 24, 2, 400},
                {"cloggrum_to_froststeel", "cloggrum", "froststeel", "#c:ingots/froststeel", 0.06, 32, 2, 600},
                {"redstone_to_utherium", "redstone", "utherium", "#c:gems/utherium", 0.06, 24, 2, 400},
                {"amethyst_to_regalium", "amethyst", "regalium", "#c:gems/regalium", 0.06, 24, 2, 400},
                {"lapis_to_dimensional", "lapis", "dimensional", "rftoolsbase:dimensionalshard", 0.06, 24, 2, 400},
                {"lapis_to_fluxite", "lapis", "fluxite", "#c:gems/fluxite", 0.06, 24, 2, 400},
                {"gold_to_force", "gold", "force", "#c:gems/force", 0.06, 24, 2, 400},
                {"amethyst_to_chimerite", "amethyst", "chimerite", "#c:gems/chimerite", 0.06, 24, 2, 400},
                {"stone_to_amber", "stone", "amber", "#c:gems/amber", 0.06, 24, 2, 400},
                {"desert_to_saltpeter", "desert", "saltpeter", "#c:dusts/saltpeter", 0.08, 24, 2, 200},
                {"amethyst_to_sal_ammoniac", "amethyst", "sal_ammoniac", "#c:gems/sal_ammoniac", 0.06, 24, 2, 400},
                {"iron_to_desh", "iron", "desh", "#c:ingots/desh", 0.05, 32, 3, 1000},
                {"iron_to_titanium", "iron", "titanium", "#c:ingots/titanium", 0.05, 32, 3, 1000},
                {"coal_to_ambrosium", "coal", "ambrosium", "aether_ii:ambrosium_shard", 0.08, 24, 2, 200},
                {"amethyst_to_zanite", "amethyst", "zanite", "aether_ii:zanite_gemstone", 0.06, 24, 2, 400},
                {"iron_to_arkenium", "iron", "arkenium", "aether_ii:arkenium_plate", 0.05, 32, 3, 1000},
                {"arkenium_to_gravitite", "arkenium", "gravitite", "aether_ii:gravitite_plate", 0.04, 32, 3, 1500},
        };
        List<Object[]> rows = new ArrayList<>(Arrays.asList(list));
        for (Object[] made : MADE) {
            int tier = SPECIES.stream().filter(s -> s.id().equals(made[0])).findFirst().orElseThrow().tier();
            Object[] numbers = HARDEST.contains(made[0]) ? new Object[] {0.02, 48, 4, 5000}
                    : new Object[] {tier == 1 ? 0.08 : tier == 2 ? 0.06 : 0.05, tier == 3 ? 32 : 24, tier == 3 ? 3 : 2, tier == 1 ? 200 : tier == 2 ? 400 : 1000};
            rows.add(new Object[] {made[1] + "_to_" + made[0], made[1], made[0], made[2], numbers[0], numbers[1], numbers[2], numbers[3]});
        }
        for (Object[] e : rows) {
            Map<String, Object> json = obj("from", ac((String) e[1]), "to", ac((String) e[2]),
                    "item", mc((String) e[3]), "chance", e[4], "guaranteed_after", e[5], "min_stage", e[6],
                    "min_population", ((Integer) e[7]).longValue());
            if (e.length > 8) {
                json.put("dimension", e[8]);
            }
            if (LAB_AMOUNTS.containsKey((String) e[0])) {
                json.put("lab_amount", LAB_AMOUNTS.get((String) e[0]));
            }
            if (NEEDS_MODS.containsKey((String) e[2])) {
                json.put("neoforge:conditions", speciesConditions((String) e[2]));
            }
            write(DATA.resolve(NS + "/evolution/" + e[0] + ".json"), json);
        }
    }

    /** How many of its material one Genetic Laboratory mutation takes, by evolution (the species with research below). */
    static final Map<String, Integer> LAB_AMOUNTS = new LinkedHashMap<>();

    static {
        String[][] amounts = {{"stone_to_iron", "8"}, {"stone_to_copper", "8"}, {"stone_to_coal", "4"}, {"stone_to_deepslate", "16"},
                {"iron_to_gold", "8"}, {"desert_to_gold", "2"}, {"iron_to_redstone", "4"}, {"stone_to_lapis", "4"}, {"stone_to_amethyst", "4"},
                {"gold_to_diamond", "6"}, {"lapis_to_diamond", "6"}, {"diamond_to_emerald", "6"}, {"diamond_to_netherite", "4"},
                {"fire_to_soul", "2"}, {"amethyst_to_ender", "4"}, {"chorus_to_ender", "4"}, {"ender_to_dragon", "4"}, {"soul_to_wither", "1"},
                {"stone_to_osmium", "8"}, {"copper_to_tin", "8"}, {"copper_to_zinc", "8"}, {"iron_to_lead", "8"}, {"iron_to_nickel", "8"},
                {"iron_to_aluminum", "8"}, {"lead_to_silver", "8"}, {"lead_to_uranium", "8"}, {"amethyst_to_fluorite", "8"},
                {"amethyst_to_certus", "8"}, {"iron_to_molybdenum", "8"}, {"fire_to_sulfur", "8"},
                {"gold_to_platinum", "8"}, {"platinum_to_iridium", "8"}, {"amethyst_to_ruby", "8"}, {"lapis_to_sapphire", "8"},
                {"amethyst_to_peridot", "8"}, {"redstone_to_cinnabar", "8"}, {"desert_to_salt", "8"},
                {"netherite_to_allthemodium", "4"}, {"allthemodium_to_vibranium", "4"}, {"vibranium_to_unobtainium", "4"},
                {"redstone_to_uraninite", "8"}, {"stone_to_inferium", "8"}, {"amethyst_to_arcane", "8"}, {"silver_to_iesnium", "8"},
                {"coal_to_dark_gem", "8"}, {"deepslate_to_bort", "8"}, {"iron_to_crimson_iron", "8"}, {"chorus_to_azure_silver", "8"},
                {"iron_to_cloggrum", "8"}, {"cloggrum_to_froststeel", "8"}, {"redstone_to_utherium", "8"}, {"amethyst_to_regalium", "8"},
                {"lapis_to_dimensional", "8"}, {"lapis_to_fluxite", "8"}, {"gold_to_force", "8"}, {"amethyst_to_chimerite", "8"},
                {"stone_to_amber", "8"}, {"desert_to_saltpeter", "8"}, {"amethyst_to_sal_ammoniac", "8"}, {"iron_to_desh", "8"},
                {"iron_to_titanium", "8"}, {"coal_to_ambrosium", "8"}, {"amethyst_to_zanite", "8"}, {"iron_to_arkenium", "4"},
                {"arkenium_to_gravitite", "4"}};
        for (String[] amount : amounts) {
            LAB_AMOUNTS.put(amount[0], Integer.parseInt(amount[1]));
        }
    }

    /**
     * Genetic research: the species made in the Genetic Laboratory (every mineral and endgame species that is evolved,
     * but Fire Ants, which live wild in Nether anthills). Scanning the material discovers the mutation; the evolutions
     * into that species then run in the laboratory instead of the Formicarium's feed slot.
     */
    static void research() {
        Object[][] list = {
                // species, scanned material(s), tier, Evolution Catalysts per mutation, requires, hint
                {"iron", "iron_ingot", "basic", 0, null, "The grey metal of every smith. Stone Ants take to it first."},
                {"copper", "copper_ingot", "basic", 0, null, "An orange metal that turns green with age."},
                {"coal", "coal", "basic", 0, null, "Black fuel dug from stone."},
                {"deepslate", "cobbled_deepslate", "basic", 0, null, "The dark rock under the stone, broken loose."},
                {"tin", "#c:ingots/tin", "basic", 0, null, "A soft white metal that makes bronze with copper."},
                {"zinc", "#c:ingots/zinc", "basic", 0, null, "A pale metal that makes brass with copper."},
                {"aluminum", "#c:ingots/aluminum", "basic", 0, null, "A light silvery metal."},
                {"gold", "gold_ingot", "advanced", 1, null, "A soft yellow metal piglins love."},
                {"redstone", "redstone", "advanced", 1, null, "Red dust that carries a signal."},
                {"lapis", "lapis_lazuli", "advanced", 1, null, "A deep blue stone enchanters use."},
                {"amethyst", "amethyst_shard", "advanced", 1, null, "A purple crystal that grows in geodes."},
                {"osmium", "#c:ingots/osmium", "advanced", 1, null, "A dense blue-grey metal."},
                {"lead", "#c:ingots/lead", "advanced", 1, null, "A heavy, dull metal."},
                {"nickel", "#c:ingots/nickel", "advanced", 1, null, "A pale yellowish metal."},
                {"silver", "#c:ingots/silver", "advanced", 1, "lead", "A bright white metal, found with lead."},
                {"fluorite", "#c:gems/fluorite", "advanced", 1, null, "A violet crystal chemists prize."},
                {"certus", "#c:gems/certus_quartz", "advanced", 1, null, "A clear crystal that holds a charge."},
                {"molybdenum", "#c:ingots/molybdenum", "advanced", 1, null, "The metal that hardens steel."},
                {"sulfur", "#c:dusts/sulfur", "advanced", 1, null, "Yellow dust from hot vents."},
                {"diamond", "diamond", "advanced", 1, null, "The hardest gem, found deep down."},
                {"emerald", "emerald", "advanced", 1, "diamond", "The green gem villagers trade in."},
                {"soul", "ghast_tear", "advanced", 1, null, "What a ghast leaves behind."},
                {"uranium", "#c:raw_materials/uranium", "advanced", 1, "lead", "A raw ore that glows green."},
                {"netherite", arr("minecraft:netherite_ingot", "minecraft:netherite_scrap"), "experimental", 2, "diamond",
                        "The Nether's ancient metal, as scrap or as an ingot."},
                {"ender", "ender_eye", "experimental", 2, null, "An eye that looks for strongholds."},
                {"dragon", "dragon_breath", "experimental", 2, "ender", "The breath of the End's ruler, caught in a bottle."},
                {"wither", "nether_star", "experimental", 2, "soul", "What the Wither leaves behind."},
                {"salt", "#c:dusts/salt", "basic", 0, null, "White dust from dry salt pans."},
                {"ruby", "#c:gems/ruby", "advanced", 1, null, "A deep red gem."},
                {"sapphire", "#c:gems/sapphire", "advanced", 1, null, "A clear blue gem."},
                {"peridot", "#c:gems/peridot", "advanced", 1, null, "A lime green gem."},
                {"cinnabar", "#c:gems/cinnabar", "advanced", 1, null, "The red ore of quicksilver."},
                {"platinum", "#c:ingots/platinum", "advanced", 1, "gold", "A rare white metal, heavier than gold."},
                {"iridium", "#c:ingots/iridium", "advanced", 1, "platinum", "The hardest of the white metals."},
                {"allthemodium", "#c:ingots/allthemodium", "experimental", 2, "netherite", "A gold-bright metal from under the deepest stone."},
                {"vibranium", "#c:ingots/vibranium", "experimental", 2, "allthemodium", "A teal metal that hums, from the Nether."},
                {"unobtainium", "#c:ingots/unobtainium", "experimental", 2, "vibranium", "A violet metal from the End that should not exist."},
                {"uraninite", "#c:raw_materials/uraninite", "advanced", 1, null, "A raw green ore that holds a charge."},
                {"inferium", "mysticalagriculture:inferium_essence", "basic", 0, null, "A green essence, the first of five."},
                {"arcane", "#c:gems/arcane_crystal", "advanced", 1, null, "A pink crystal that hums with magic."},
                {"iesnium", "#c:ingots/iesnium", "advanced", 1, "silver", "A teal metal most eyes cannot see in the rock."},
                {"dark_gem", "evilcraft:dark_gem", "advanced", 1, null, "A black gem that drinks the light."},
                {"bort", "#c:gems/bort", "advanced", 1, null, "A rough, cloudy diamond, too poor for jewels."},
                {"crimson_iron", "#c:ingots/crimson_iron", "advanced", 1, null, "A red iron from the Nether."},
                {"azure_silver", "#c:ingots/azure_silver", "advanced", 1, null, "A blue silver from the End."},
                {"cloggrum", "#c:ingots/cloggrum", "advanced", 1, null, "A dull brown metal from deep under the world."},
                {"froststeel", "#c:ingots/froststeel", "advanced", 1, "cloggrum", "A metal that never warms in the hand."},
                {"utherium", "#c:gems/utherium", "advanced", 1, null, "A blood red crystal from deep under the world."},
                {"regalium", "#c:gems/regalium", "advanced", 1, null, "A golden crystal traders of the deep value."},
                {"dimensional", "rftoolsbase:dimensionalshard", "advanced", 1, null, "A shard that belongs to no one world."},
                {"fluxite", "#c:gems/fluxite", "advanced", 1, null, "A bright pink gem that bends energy."},
                {"force", "#c:gems/force", "advanced", 1, null, "A yellow gem full of force."},
                {"chimerite", "#c:gems/chimerite", "advanced", 1, null, "A gem that shifts colour as it turns."},
                {"amber", "#c:gems/amber", "advanced", 1, null, "Old resin turned to stone."},
                {"saltpeter", "#c:dusts/saltpeter", "basic", 0, null, "A white dust that makes powder burn."},
                {"sal_ammoniac", "#c:gems/sal_ammoniac", "advanced", 1, null, "A pale crystal alchemists dissolve things in."},
                {"desh", "#c:ingots/desh", "advanced", 1, null, "An orange metal from another world."},
                {"titanium", "#c:ingots/titanium", "advanced", 1, null, "A light, hard metal from another world."},
                {"ambrosium", "aether_ii:ambrosium_shard", "basic", 0, null, "A glowing yellow shard from the sky."},
                {"zanite", "aether_ii:zanite_gemstone", "advanced", 1, null, "A purple gemstone from the sky."},
                {"arkenium", "aether_ii:arkenium_plate", "advanced", 1, null, "A heavy plate of the sky's metal."},
                {"gravitite", "aether_ii:gravitite_plate", "advanced", 1, "arkenium", "A plate of the metal that falls upward."},
        };
        List<Object[]> rows = new ArrayList<>(Arrays.asList(list));
        for (Object[] made : MADE) {
            rows.add(new Object[] {made[0], made[2], made[3], made[3].equals("basic") ? 0 : made[3].equals("experimental") ? 2 : 1, made[4], made[5]});
        }
        for (Object[] r : rows) {
            String id = (String) r[0];
            Object material = r[1] instanceof String item ? mc(item) : r[1];
            Map<String, Object> json = obj("material", material, "species", ac(id), "tier", r[2],
                    "catalysts", (Integer) r[3] == 0 ? null : arr(obj("ingredient", ac("evolution_catalyst"), "count", r[3])),
                    "requires", r[4] == null ? null : arr(ac((String) r[4])));
            if (NEEDS_MODS.containsKey(id)) {
                json.put("neoforge:conditions", speciesConditions(id));
            }
            write(DATA.resolve(NS + "/research/" + id + ".json"), json);
            LANG.put("research." + NS + "." + id + ".hint", (String) r[5]);
        }
    }

    /** The tier and the hint of each gene's research (the mutations the laboratory gives on purpose, by their catalyst). */
    static final Map<String, String[]> GENE_RESEARCH = new LinkedHashMap<>();

    static {
        String[][] genes = {
                {"crystal_genetics", "advanced", "The hardest gem changes how a Queen's workers look for treasure."},
                {"efficient_metabolism", "advanced", "A green gem teaches a colony thrift."},
                {"energized", "basic", "Red dust quickens the brood."},
                {"heat_resistance", "advanced", "The powder of a burning rod."},
                {"cold_resistance", "basic", "Ice pressed until it no longer melts."},
                {"golden_touch", "basic", "A soft yellow metal sharpens the foragers."},
                {"iron_jaws", "basic", "A whole block of the smith's metal."},
                {"green_thumb", "basic", "Bones pressed into a block feed more than soil."},
                {"industrious", "advanced", "A block of red dust drives workers harder."},
                {"keen_senses", "basic", "A tool for looking far."},
                {"fertile", "basic", "A carrot dressed in gold."},
                {"rapid_growth", "basic", "Berries that glow in caves."},
                {"armored_carapace", "advanced", "A plate shed by an armoured animal of the savanna."},
                {"lucky_strike", "advanced", "A lucky charm from a small hopping animal."},
                {"deep_roots", "basic", "A soft green block from lush caves."},
                {"ember_heart", "advanced", "Cream from a bouncing cube of the Nether."},
                {"frost_heart", "advanced", "The bluest, coldest ice."},
                {"symbiotic", "basic", "What bees build their nests from."},
                {"resonant", "experimental", "A shard that rings in the ancient cities."},
                {"void_touched", "experimental", "The shell of a creature that hides in End cities."},
        };
        for (String[] gene : genes) {
            GENE_RESEARCH.put(gene[0], new String[] {gene[1], gene[2]});
        }
    }

    static void hybrids() {
        write(DATA.resolve(NS + "/hybrid/mechanical_miner.json"), obj("first", ac("stone"), "second", ac("redstone"),
                "result", ac("mechanical_miner"),
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
            write(DATA.resolve(NS + "/hybrid/" + h[0] + ".json"), obj("first", ac((String) h[1]), "second", ac((String) h[2]),
                    "result", ac((String) h[0]),
                    "chance", h[3], "min_average_level", h[4]));
        }
    }

    // --- Advancements ---

    /** One advancement: id, parent (null for the root), icon item, frame, title, description, its one criterion. */
    static void advancement(String id, String parent, String icon, String frame, String title, String description, Map<String, Object> criterion) {
        Map<String, Object> display = obj("icon", obj("id", icon.contains(":") ? icon : ac(icon)),
                "title", obj("translate", "advancements." + NS + "." + id + ".title"),
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
        return obj("trigger", "minecraft:inventory_changed",
                "conditions", obj("items", arr(obj("items", list.size() == 1 ? list.getFirst() : list))));
    }

    /** The mod's colony trigger: a nearby (or opened) Formicarium whose colony matches these conditions. */
    static Map<String, Object> colony(Object... conditions) {
        return obj("trigger", ac("colony"), "conditions", obj(conditions));
    }

    static void advancements() {
        // Jade: tooltip lines and the names of the toggles in its plugin config.
        LANG.put("jade." + NS + ".food", "Food %s%%");
        LANG.put("config.jade.plugin_" + NS + ".terrarium", "Formicarium colony");
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
        LANG.put("tooltip." + NS + ".species.evolves_in", "Only in a Formicarium in %s");
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
        advancement("first_colony", "catch_queen", "terrarium", "task", "Home Sweet Home", "Start a colony: a Formicarium with a Queen",
                colony("has_queen", true));
        advancement("stage_2", "first_colony", structureItem("nest_chamber"), "task", "Growing Family",
                "Reach colony stage 2 (100 adults)", colony("stage", obj("min", 2)));
        advancement("stage_4", "stage_2", structureItem("foraging_site"), "goal", "Metropolis", "Reach colony stage 4", colony("stage", obj("min", 4)));
        advancement("evolution", "first_colony", "evolution_catalyst", "task", "Evolution", "Turn a colony into a mineral species",
                colony("category", "mineral"));
        advancement("hybrid", "evolution", "genetic_sample", "goal", "Hybrid Vigor", "Raise a hybrid colony from a Queen Chamber cross",
                colony("category", "hybrid"));
        advancement("generator", "root", "generator", "task", "Power Up", "Make a Generator to power the Genetic Laboratory", hasItem("generator"));
        advancement("lab", "generator", "genetic_laboratory", "task", "Genetic Engineer", "Make a Genetic Laboratory",
                hasItem("genetic_laboratory"));
        advancement("perfect_trait", "lab", "crystal_core", "challenge", "Perfection", "Own a colony whose Queen has a trait at level 10",
                colony("best_trait", obj("min", 10)));
        advancement("netherite", "evolution", "minecraft:netherite_scrap", "challenge", "Forged in the Deep", "Raise a Netherite Ant colony",
                colony("species", ac("netherite")));
        advancement("ender", "evolution", "minecraft:ender_pearl", "challenge", "Void Walkers", "Raise an Ender Ant colony", colony("species", ac("ender")));
        // The path through the dimensions: the Chitin ladder, Nether and End colonies, the endgame species.
        advancement("hardened_chitin", "first_colony", "hardened_chitin", "task", "Case Hardened", "Get Hardened Chitin: wild ants leave it now and then, and grown colonies find it",
                hasItem("hardened_chitin"));
        advancement("nether_colony", "hardened_chitin", "minecraft:netherrack", "task", "Hot Property", "Keep a colony in a Formicarium in the Nether",
                colonyIn("minecraft:the_nether"));
        advancement("warped_colony", "nether_colony", "warped_ant_nest", "task", "Fungal Empire", "Raise a Warped Ant colony",
                colony("species", ac("warped")));
        advancement("infernal_chitin", "nether_colony", "infernal_chitin", "task", "Trial by Fire", "Get Infernal Chitin from the ants of the Nether",
                hasItem("infernal_chitin"));
        advancement("wither_ant", "infernal_chitin", "minecraft:wither_skeleton_skull", "challenge", "Withering Heights",
                "Raise a Wither Ant colony: Soul Ants fed a Nether Star in the Nether", colony("species", ac("wither")));
        advancement("end_colony", "infernal_chitin", "minecraft:end_stone", "task", "The End of the Line", "Keep a colony in a Formicarium in the End",
                colonyIn("minecraft:the_end"));
        advancement("chorus_colony", "end_colony", "minecraft:chorus_fruit", "task", "Chorus Line", "Raise a Chorus Ant colony",
                colony("species", ac("chorus")));
        advancement("void_chitin", "end_colony", "void_chitin", "task", "Into the Void", "Get Void Chitin from the ants of the End",
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

    /** A colony whose Formicarium the player is near, in this dimension. */
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
        // What each effect of a mutation is called where a screen spells it out; %s is the change, like +25%.
        String[][] effects = {{"production", "%s worker finds"}, {"gathering", "%s items per find"}, {"food", "%s food eaten"},
                {"eggs", "%s eggs laid"}, {"growth", "%s larva growth"}, {"soldiers", "%s hunting"},
                {"farming", "%s farmer food"}, {"special", "%s special finds"},
                {"cold_resistant", "Bears cold climates"}, {"heat_resistant", "Bears hot climates"}};
        for (String[] effect : effects) {
            LANG.put("mutation." + NS + ".effect." + effect[0], effect[1]);
        }
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
            if (m[3] != null) {
                // Scanning a mutation's catalyst reveals the gene in the laboratory's Research page and the book.
                String[] gene = GENE_RESEARCH.get((String) m[0]);
                if (gene == null) {
                    throw new IllegalStateException("No research row for the mutation " + m[0]);
                }
                write(DATA.resolve(NS + "/research/gene_" + m[0] + ".json"),
                        obj("material", mc((String) m[3]), "mutation", ac((String) m[0]), "tier", gene[0]));
                LANG.put("research." + NS + ".gene_" + m[0] + ".hint", gene[1]);
            }
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
        // Whole kinds of food first, by the common tags, so other mods' crops, fruit, meat and fish feed a colony too.
        // The game reads the file in order: an item named further down keeps its own number.
        Object[][] kinds = {
                {"#c:seeds", 4}, {"#c:crops", 20}, {"#c:foods/vegetable", 25}, {"#c:foods/berry", 30}, {"#c:foods/fruit", 40},
                {"#c:foods/raw_fish", 30}, {"#c:foods/raw_meat", 40}, {"#c:foods/cooked_fish", 50}, {"#c:foods/cooked_meat", 65},
                {"#c:foods/dough", 30}, {"#c:foods/cookie", 40}, {"#c:foods/candy", 40}, {"#c:foods/soup", 70}, {"#c:foods/bread", 80},
                {"#c:foods/pie", 90},
        };
        for (Object[] kind : kinds) {
            values.put((String) kind[0], obj("food", ((Integer) kind[1]).doubleValue()));
        }
        Object[][] foods = {
                {ac("ant_food"), 200, null, 0}, {ac("mineral_ant_food"), 100, "mineral", 100}, {ac("crystal_ant_food"), 100, "crystal", 100},
                {ac("nether_ant_food"), 150, "nether", 100}, {ac("royal_jelly"), 400, null, 0},
                {"sugar", 20, null, 0}, {"sweet_berries", 30, null, 0}, {"glow_berries", 40, null, 0}, {"apple", 50, null, 0},
                {"wheat_seeds", 4, null, 0}, {"wheat", 15, null, 0}, {"bread", 80, null, 0}, {"honey_bottle", 150, null, 0},
                {"brown_mushroom", 25, null, 0}, {"red_mushroom", 25, null, 0}, {"melon_slice", 25, null, 0}, {"carrot", 25, null, 0},
                {"potato", 25, null, 0}, {"beetroot", 20, null, 0}, {"rotten_flesh", 30, null, 0}, {"spider_eye", 25, null, 0},
                {"beef", 40, null, 0}, {"porkchop", 40, null, 0}, {"chicken", 40, null, 0}, {"mutton", 40, null, 0}, {"rabbit", 40, null, 0},
                {"cod", 30, null, 0}, {"salmon", 30, null, 0}, {"egg", 30, null, 0}, {"cookie", 40, null, 0},
                // More of the base game's: what grows, what is cooked from an ant's finds, and what ants are known to carry off.
                {"baked_potato", 50, null, 0}, {"poisonous_potato", 15, null, 0}, {"melon", 225, null, 0}, {"pumpkin", 60, null, 0},
                {"sugar_cane", 10, null, 0}, {"cactus", 10, null, 0}, {"cocoa_beans", 15, null, 0}, {"nether_wart", 15, null, 0},
                {"chorus_fruit", 30, null, 0}, {"dried_kelp", 10, null, 0}, {"crimson_fungus", 25, null, 0},
                {"warped_fungus", 25, null, 0}, {"blue_egg", 30, null, 0}, {"brown_egg", 30, null, 0},
        };
        for (Object[] food : foods) {
            values.put(mc((String) food[0]), obj("food", ((Integer) food[1]).doubleValue(), "special_kind", food[2],
                    "special", food[2] == null ? null : ((Integer) food[3]).doubleValue()));
        }
        // Out again: what the tags bring in that a Queen is fed for something else. Golden food makes mutations, a cake
        // makes Baker Ants; as food they would be eaten instead.
        write(DATA.resolve("data_maps/item/ant_food.json"), obj("values", values,
                "remove", arr("minecraft:golden_carrot", "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:cake")));
    }

    // --- Recipes ---

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
        // Chitin is never crafted: it comes only from ants (wild ants' drops, colonies' finds, anthill chests).
        shaped("upgrade_base", ac("upgrade_base"), 2, new String[] {"CIC", "IRI", "CIC"}, "C", ac("chitin_plate"), "I", "iron_ingot", "R", "redstone");
        shaped("genetic_circuit", ac("genetic_circuit"), 2, new String[] {"GRG", "QAQ", "GRG"}, "G", "gold_nugget", "R", "redstone", "Q", "quartz",
                "A", "amethyst_shard");
        shaped("crystal_core", ac("crystal_core"), 1, new String[] {"ADA", "AGA", "ADA"}, "A", "amethyst_shard", "D", "diamond", "G", ac("genetic_circuit"));
        shapeless("diamond_from_fragments", "diamond", 1, repeat(ac("diamond_fragment"), 9));
        shapeless("emerald_from_fragments", "emerald", 1, repeat(ac("emerald_fragment"), 9));
        shapeless("ant_identification_book", ac("ant_identification_book"), 1, "book", ac("test_tube"));
        shaped("colony_scanner", ac("colony_scanner"), 1, new String[] {" G ", "IRI", " I "}, "G", "glass_pane", "I", "iron_ingot", "R", "redstone");

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
        // The Genetic Laboratory.
        shaped("genetic_laboratory", ac("genetic_laboratory"), 1, new String[] {"IGI", "CCC", "ARA"}, "I", ac("hardened_chitin"), "G", "glass",
                "C", ac("genetic_circuit"), "A", "amethyst_block", "R", "redstone_block");
        // Upgrades: tier 1 from an Upgrade Base, tiers 2 to 4 from the tier below.
        String[][] materials = {{"speed", "sugar"}, {"food_efficiency", "golden_carrot"}, {"simulation", "comparator"}, {"storage", "chest"},
                {"breeding", "egg"}, {"genetic", ac("genetic_circuit")}, {"hunting", "iron_sword"}, {"automation", "hopper"},
                {"population", "packed_mud"}, {"evolution", ac("evolution_catalyst")}};
        for (String[] m : materials) {
            shaped(m[0] + "_upgrade_1", ac(m[0] + "_upgrade_1"), 1, new String[] {" X ", "XBX", " X "}, "X", m[1], "B", ac("upgrade_base"));
            shaped(m[0] + "_upgrade_2", ac(m[0] + "_upgrade_2"), 1, new String[] {"GXG", "XUX", "GXG"}, "G", ac("hardened_chitin"), "X", m[1], "U", ac(m[0] + "_upgrade_1"));
            shaped(m[0] + "_upgrade_3", ac(m[0] + "_upgrade_3"), 1, new String[] {"DXD", "XUX", "DXD"}, "D", ac("infernal_chitin"), "X", m[1], "U", ac(m[0] + "_upgrade_2"));
            // Tier IV, the endgame: Void Chitin and netherite.
            shaped(m[0] + "_upgrade_4", ac(m[0] + "_upgrade_4"), 1, new String[] {"VXV", "NUN", "VXV"}, "V", ac("void_chitin"), "X", m[1],
                    "N", "netherite_ingot", "U", ac(m[0] + "_upgrade_3"));
        }
    }

    static String[] repeat(String value, int times) {
        String[] out = new String[times];
        Arrays.fill(out, value);
        return out;
    }

    // --- Blocks, items, loot, tags, worldgen, models ---

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
        // Wild ants leave chitin, its kind by dimension; Looting raises every chance.
        write(DATA.resolve("loot_table/entities/wild_ant.json"), obj("type", "minecraft:entity", "pools", arr(
                chitinDrop("chitin_plate", 0.35, 0.1, null), chitinDrop("hardened_chitin", 0.05, 0.03, null),
                chitinDrop("infernal_chitin", 0.15, 0.05, "minecraft:the_nether"), chitinDrop("void_chitin", 0.12, 0.05, "minecraft:the_end")),
                "random_sequence", ac("entities/wild_ant")));
    }

    /** A pool of the wild ant's loot: one chitin at a chance (more per Looting level), only in a dimension if one is given. */
    static Map<String, Object> chitinDrop(String item, double chance, double perLooting, String dimension) {
        Map<String, Object> roll = obj("type", "minecraft:random_chance_with_enchanted_bonus", "enchantment", "minecraft:looting",
                "unenchanted_chance", chance,
                "enchanted_chance", obj("type", "minecraft:linear", "base", Math.round((chance + perLooting) * 1000) / 1000.0,
                        "per_level_above_first", perLooting));
        Object condition = dimension == null ? roll : obj("type", "minecraft:all_of", "terms", arr(roll,
                obj("type", "minecraft:location_check", "predicate", obj("dimension", dimension))));
        return obj("entries", arr(obj("type", "minecraft:item", "name", ac(item))), "condition", condition, "rolls", 1);
    }

    static void tags() {
        List<Object> pickaxe = new ArrayList<>();
        List<Object> shovel = new ArrayList<>();
        List<Object> axe = new ArrayList<>();
        pickaxe.add(ac("genetic_laboratory"));
        pickaxe.add(ac("generator"));
        pickaxe.add(ac("centrifuge"));
        shovel.add(ac("liquid_chamber"));
        for (String s : new String[] {"nest_chamber", "foraging_site", "harvesting_site", "hunting_ground", "queen_chamber"}) {
            shovel.add(ac(s));
        }
        for (String nest : NESTS) {
            shovel.add(ac(nest + "_ant_nest"));
        }
        for (String s : new String[] {"nether_foraging_site", "end_foraging_site"}) {
            pickaxe.add(ac(s));
        }
        shovel.add(ac("colony_connector"));
        for (Map.Entry<String, String> tool : decorTools().entrySet()) {
            switch (tool.getValue()) {
                case "pickaxe" -> pickaxe.add(ac(tool.getKey()));
                case "shovel" -> shovel.add(ac(tool.getKey()));
                case "axe" -> axe.add(ac(tool.getKey()));
                default -> {
                }
            }
        }
        for (String s : new String[] {"terrarium", "storage", "upgrade_station"}) {
            axe.add(ac(s));
        }
        write(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"), obj("values", pickaxe));
        write(RES.resolve("data/minecraft/tags/block/mineable/shovel.json"), obj("values", shovel));
        write(RES.resolve("data/minecraft/tags/block/mineable/axe.json"), obj("values", axe));
        // Blocks that give a Formicarium standing on them a climate (Climate#ofFloor); whole families count.
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
        // The stones and nylium by name, not by the base game's tags: world generation mods add to those (Nullscape end stone
        // and Terralith blackstone to the Overworld's stones, Terralith grass to the nylium), and a block must have one climate.
        floors.put("underground", arr("minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
                "minecraft:deepslate", "minecraft:smooth_stone", "minecraft:polished_granite",
                "minecraft:polished_diorite", "minecraft:polished_andesite", "minecraft:polished_tuff", "minecraft:cobbled_deepslate",
                "minecraft:polished_deepslate", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:calcite",
                "minecraft:dripstone_block"));
        floors.put("nether", arr("minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone", "minecraft:polished_basalt",
                "minecraft:smooth_basalt", "minecraft:polished_blackstone", "minecraft:crimson_nylium", "minecraft:warped_nylium",
                "minecraft:magma_block", "minecraft:soul_sand", "minecraft:soul_soil"));
        floors.put("end", arr("minecraft:end_stone", "minecraft:end_stone_bricks"));
        for (Map.Entry<String, List<Object>> floor : floors.entrySet()) {
            write(DATA.resolve("tags/block/climate/" + floor.getKey() + ".json"), obj("values", floor.getValue()));
        }
        // Where each wild species lives.
        // The common biome tags (c:is_...) bring in the biomes of world generation mods; MOD_BIOMES adds the ones no tag sorts.
        Map<String, List<Object>> biomes = new LinkedHashMap<>();
        biomes.put("common", arr("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest",
                "minecraft:birch_forest", "minecraft:cherry_grove", "#c:is_plains", "#c:is_forest", "#c:is_birch_forest", "#c:is_flower_forest"));
        biomes.put("wood", arr("#minecraft:is_taiga", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga", "#c:is_taiga", "#c:is_old_growth", "#c:is_tree/coniferous"));
        biomes.put("stone", arr("minecraft:stony_peaks", "minecraft:stony_shore", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                "minecraft:dripstone_caves", "minecraft:jagged_peaks", "#c:is_mountain", "#c:is_hill", "#c:is_stony_shores", "#c:is_windswept"));
        biomes.put("snow", arr("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:snowy_slopes", "minecraft:grove",
                "minecraft:frozen_peaks", "minecraft:snowy_beach", "#c:is_snowy", "#c:is_icy"));
        biomes.put("desert", arr("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "#c:is_desert",
                "#c:is_badlands"));
        biomes.put("farmer", arr("#minecraft:is_jungle", "minecraft:mushroom_fields", "minecraft:swamp", "minecraft:mangrove_swamp", "#c:is_jungle",
                "#c:is_mushroom", "#c:is_swamp"));
        biomes.put("jungle", arr("#minecraft:is_jungle", "#c:is_jungle", "#c:is_tree/jungle"));
        biomes.put("swamp", arr("minecraft:swamp", "minecraft:mangrove_swamp", "#c:is_swamp"));
        biomes.put("savanna", arr("#minecraft:is_savanna", "#c:is_savanna"));
        biomes.put("shore", arr("minecraft:beach", "#c:is_beach"));
        biomes.put("blossom", arr("minecraft:cherry_grove", "minecraft:flower_forest", "minecraft:meadow", "minecraft:sunflower_plains", "#c:is_floral"));
        biomes.put("pale", arr("minecraft:pale_garden"));
        biomes.put("badlands", arr("#minecraft:is_badlands", "#c:is_badlands"));
        biomes.put("army", arr("#minecraft:is_savanna", "minecraft:plains", "minecraft:sunflower_plains", "#c:is_savanna", "#c:is_plains"));
        biomes.put("warped", arr("minecraft:warped_forest", "minecraft:crimson_forest", "#c:is_nether_forest"));
        biomes.put("chorus", arr("minecraft:end_highlands", "minecraft:end_midlands"));
        // Crimson Ants take the crimson forest over from Warped Ants, whose tag (and so whose nests) still covers it.
        biomes.put("crimson", arr("minecraft:crimson_forest"));
        // The Other (Allthemodium's Nether-like dimension): the home of Allthemodium and Vibranium Ants, who have no nest
        // block of their own and live in the nests that stand there (the nest_dwellers species tag).
        biomes.put("the_other", arr());
        for (String[] row : MOD_BIOMES) {
            for (String biome : row[2].split(" ")) {
                for (String nest : row[1].split(" ")) {
                    biomes.get(nest).add(obj("id", row[0] + ":" + biome, "required", false));
                }
            }
        }
        List<Object> all = new ArrayList<>();
        for (Map.Entry<String, List<Object>> entry : biomes.entrySet()) {
            write(DATA.resolve("tags/worldgen/biome/spawns/" + entry.getKey() + ".json"), obj("values", entry.getValue()));
            all.add("#" + ac("spawns/" + entry.getKey()));
        }
        write(DATA.resolve("tags/worldgen/biome/has_wild_ants.json"), obj("values", all));
    }

    /**
     * Other mods' biomes that the common tags leave out or sort too loosely: the mod, the nests that stand there, the biomes.
     * Every entry is optional in its tag, so a missing mod or a renamed biome changes nothing.
     */
    static final String[][] MOD_BIOMES = {
            // Terralith.
            {"terralith", "common", "blooming_plateau shrubland valley_clearing lush_valley shield_clearing alpha_islands"},
            {"terralith", "blossom", "blooming_plateau"},
            {"terralith", "wood", "yosemite_cliffs alpha_islands_winter"},
            {"terralith", "stone", "basalt_cliffs granite_cliffs white_cliffs yellowstone yosemite_cliffs frozen_cliffs"},
            {"terralith", "snow", "cold_shrubland rocky_shrubland gravel_desert alpha_islands_winter"},
            {"terralith", "savanna army", "hot_shrubland brushland"},
            {"terralith", "farmer", "mirage_isles"},
            {"terralith", "badlands desert", "warped_mesa"},
            // Regions Unexplored.
            {"regions_unexplored", "common", "tropics"},
            {"regions_unexplored", "jungle farmer", "tropics"},
            {"regions_unexplored", "wood", "pine_slopes cold_boreal_taiga frozen_pine_taiga"},
            {"regions_unexplored", "snow", "tundra cold_deciduous_forest"},
            {"regions_unexplored", "stone", "chalk_cliffs"},
            {"regions_unexplored", "pale", "ashen_woodland"},
            {"regions_unexplored", "warped", "glistering_meadow mycotoxic_undergrowth infernal_holt"},
            // Nullscape (the End).
            {"nullscape", "chorus", "shadowlands crystal_peaks"},
            // The Twilight Forest.
            {"twilightforest", "common", "forest dense_forest firefly_forest clearing enchanted_forest"},
            {"twilightforest", "blossom", "enchanted_forest"},
            {"twilightforest", "wood", "highlands dense_forest"},
            {"twilightforest", "savanna army", "oak_savannah"},
            {"twilightforest", "farmer", "mushroom_forest dense_mushroom_forest swamp"},
            {"twilightforest", "swamp", "swamp fire_swamp"},
            {"twilightforest", "snow", "snowy_forest glacier"},
            {"twilightforest", "pale", "dark_forest dark_forest_center spooky_forest"},
            {"twilightforest", "stone", "thornlands final_plateau"},
            // Aether II.
            {"aether_ii", "common", "flourishing_field verdant_woods enduring_woodland shimmering_basin expanse"},
            {"aether_ii", "blossom", "flourishing_field"},
            {"aether_ii", "wood", "violet_highwoods turquoise_forest shrouded_forest"},
            {"aether_ii", "stone", "magnetic_scar battleground_wastes"},
            {"aether_ii", "snow", "frigid_sierra sheer_tundra frozen_lakes"},
            {"aether_ii", "swamp farmer", "glistening_swamp"},
            {"aether_ii", "jungle farmer", "contaminated_jungle"},
            // The Undergarden (a roofed dimension: nests stand on its cave floors).
            {"undergarden", "stone", "forgotten_field barren_abyss smog_spires depths gronglegrowth"},
            {"undergarden", "wood", "smogstem_forest wigglewood_forest dense_forest"},
            {"undergarden", "snow", "frostfields frosty_smogstem_forest"},
            {"undergarden", "farmer", "blood_mushroom_bog indigo_mushroom_bog ink_mushroom_bog veil_mushroom_bog puff_mushroom_forest"},
            // Allthemodium: The Other (its own tag: its nests hold Allthemodium and Vibranium Ants) and The Beyond (like the
            // End). Not the Mining Dimension: the user wants no nests there.
            {"allthemodium", "the_other", "the_other warped_forest crimson_forest soul_sand_valley basalt_deltas desert desert_hills"},
            {"allthemodium", "chorus", "the_beyond"},
            // Thaumaturge.
            {"thaumaturge", "blossom", "magical_forest"},
    };

    /**
     * The share of their spots wild nests take (NestGround's chance): the rarities below thinned by a tenth, which a
     * whole number of chunks cannot say.
     */
    static final double NEST_SHARE = 0.9;
    /** One nest in this many chunks of The Other. */
    static final int THE_OTHER_NEST_RARITY = 24;

    static void worldgen() {
        for (String nest : NESTS) {
            write(DATA.resolve("worldgen/feature/" + nest + "_ant_nest.json"),
                    obj("type", "minecraft:simple_block", "to_place", obj("id", ac(nest + "_ant_nest"))));
            List<Object> placement = new ArrayList<>();
            boolean nether = nest.equals("warped");
            // Nether nests more often: their forests are small, and many spots there are rock or lava.
            placement.add(obj("type", "minecraft:rarity_filter", "chance", nest.equals("stone") || nest.equals("snow") ? 28
                    : nest.equals("shore") || nest.equals("pale") ? 14 : nether ? 6 : 20));
            placement.add(obj("type", "minecraft:in_square"));
            // The mod's own step (NestGround): the ground under the leaves, or a cave floor where the dimension has a roof.
            placement.add(obj("type", ac("nest_ground"), "chance", NEST_SHARE));
            placement.add(obj("type", "minecraft:biome"));
            write(DATA.resolve("worldgen/placed_feature/" + nest + "_ant_nest.json"), obj("feature", ac(nest + "_ant_nest"), "placement", placement));
            write(DATA.resolve("neoforge/biome_modifier/add_" + nest + "_ant_nests.json"), obj("type", "neoforge:add_features",
                    "biomes", "#" + ac("spawns/" + nest), "features", ac(nest + "_ant_nest"), "step", "vegetal_decoration"));
        }
        // The Other's nests: the Nether nest block, rarely, in every biome of that dimension. Who lives in one is decided
        // where it stands (WildNestBlockEntity.dweller).
        write(DATA.resolve("worldgen/placed_feature/the_other_ant_nest.json"), obj("feature", ac("warped_ant_nest"), "placement", arr(
                obj("type", "minecraft:rarity_filter", "chance", THE_OTHER_NEST_RARITY), obj("type", "minecraft:in_square"),
                obj("type", ac("nest_ground"), "chance", NEST_SHARE), obj("type", "minecraft:biome"))));
        write(DATA.resolve("neoforge/biome_modifier/add_the_other_ant_nests.json"), obj("type", "neoforge:add_features",
                "biomes", "#" + ac("spawns/the_other"), "features", ac("the_other_ant_nest"), "step", "vegetal_decoration"));
        write(DATA.resolve("neoforge/biome_modifier/add_wild_ants.json"), obj("type", "neoforge:add_spawns", "biomes", "#" + ac("has_wild_ants"),
                "spawners", arr(obj("type", ac("wild_ant"), "count", obj("type", "minecraft:uniform", "min_inclusive", 2, "max_inclusive", 5),
                        "weight", 12))));
    }

    static void blockstates() {
        Path states = ASSETS.resolve("blockstates");
        Path blockModels = ASSETS.resolve("models/block");
        // The Formicarium and the chambers: facing, with the colony frame on the edges that touch no other colony part.
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
                    variants.put("facing=" + facings[f] + ",working=" + working,
                            obj("model", ac("block/" + part + (working.equals("true") ? "_working" : "")),
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
                generator.put("facing=" + sides[f] + ",lit=" + lit,
                        obj("model", ac("block/generator" + (lit.equals("true") ? "_on" : "")), "y", f == 0 ? null : f * 90));
            }
        }
        write(states.resolve("generator.json"), obj("variants", generator));
        // Its models: the shell it shares with the Droplet Centrifuge (labMachine), with a burn chamber inside.
        for (String on : new String[] {"", "_on"}) {
            boolean lit = !on.isEmpty();
            List<Object> chamber = new ArrayList<>();
            // The electrodes, from the floor and from the lid.
            Map<String, Object> rod = obj("uv", arr(6, 7, 10, 8), "texture", "#deck");
            chamber.add(obj("from", arr(6, 8, 6), "to", arr(10, 9, 10), "faces", obj("north", rod, "south", rod, "west", rod, "east", rod)));
            chamber.add(obj("from", arr(6, 13, 6), "to", arr(10, 14, 10), "faces", obj("north", rod, "south", rod, "west", rod, "east", rod)));
            // The core: plasma (animated, full-bright) while it burns, a dark slug otherwise.
            Map<String, Object> coreSide = obj("uv", arr(4, 6, 12, 10), "texture", "#core");
            Map<String, Object> coreEnd = obj("uv", arr(4, 4, 12, 12), "texture", "#core");
            chamber.add(obj("from", arr(4, 9, 4), "to", arr(12, 13, 12), "light_emission", lit ? 15 : null, "faces", obj(
                    "up", coreEnd, "down", coreEnd, "north", coreSide, "south", coreSide, "west", coreSide, "east", coreSide)));
            if (lit) {
                // What is lit on the casing is drawn again a hair proud of it, full-bright, so it glows in the dark.
                chamber.add(obj("from", arr(3, 4, -0.02), "to", arr(13, 7, -0.02), "light_emission", 13,
                        "faces", obj("north", obj("uv", arr(3, 9, 13, 12), "texture", "#front"))));
                Map<String, Object> conduit = obj("uv", arr(3, 10, 13, 12), "texture", "#side");
                chamber.add(obj("from", arr(3, 4, 16.02), "to", arr(13, 6, 16.02), "light_emission", 13, "faces", obj("south", conduit)));
                chamber.add(obj("from", arr(-0.02, 4, 3), "to", arr(-0.02, 6, 13), "light_emission", 13, "faces", obj("west", conduit)));
                chamber.add(obj("from", arr(16.02, 4, 3), "to", arr(16.02, 6, 13), "light_emission", 13, "faces", obj("east", conduit)));
                chamber.add(obj("from", arr(6, 16.02, 6), "to", arr(10, 16.02, 10), "light_emission", 13,
                        "faces", obj("up", obj("uv", arr(6, 6, 10, 10), "texture", "#top"))));
            }
            Map<String, Object> textures = obj("particle", ac("block/generator_side"), "side", ac("block/generator_side" + on),
                    "front", ac("block/generator_front" + on), "top", ac("block/generator_top" + on), "deck", ac("block/generator_deck"),
                    "port", ac("block/generator_chamber_front"), "slit", ac("block/generator_chamber_side"),
                    "fins", ac("block/generator_chamber_back"), "core", ac("block/" + (lit ? "generator_plasma" : "generator_core")));
            labMachine("generator" + on, textures, chamber, "#port", "#fins", "#slit", "#slit");
        }
        for (String nest : NESTS) {
            // One look for every state (recovering or not, wild or a home nest).
            write(states.resolve(nest + "_ant_nest.json"), obj("variants", obj("", obj("model", ac("block/" + nest + "_ant_nest")))));
            write(blockModels.resolve(nest + "_ant_nest.json"), obj("parent", ac("block/ant_nest"),
                    "textures", obj("side", ac("block/" + nest + "_ant_nest_side"),
                            "top", ac("block/" + nest + "_ant_nest_top"), "particle", ac("block/" + nest + "_ant_nest_side"))));
        }
    }

    // --- The colony frame ---

    /** How thick the colony frame is, and how far it stands out of the faces it lies on (so the two never share pixels). */
    static final double FRAME = 2;
    static final double PROUD = 0.05;
    /** The twelve edges of a block, each named by the two sides it lies between. */
    static final String[][] EDGES = {{"north", "west"}, {"north", "east"}, {"south", "west"}, {"south", "east"},
            {"up", "north"}, {"up", "south"}, {"up", "west"}, {"up", "east"}, {"down", "north"}, {"down", "south"}, {"down", "west"},
            {"down", "east"}};
    static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    /** A box of the frame texture, {@link #PROUD} out where it lies on the block's outside; its UVs stay inside the texture. */
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

    /** Height of the ground inside a colony built flat, in pixels; the same as TunnelLayout.SURFACE and StructureRenderer.FLOOR. */
    static final double FLOOR = 8;

    /** A colony block's wooden base, under its soil: 1 px, like the Formicarium's. */
    static Map<String, Object> floorBody() {
        return obj("from", arr(0, 0, 0), "to", arr(16, 1, 16), "faces", obj(
                "down", obj("texture", "#side", "cullface", "down"), "up", obj("texture", "#side"),
                "north", obj("texture", "#side", "cullface", "north"), "south", obj("texture", "#side", "cullface", "south"),
                "west", obj("texture", "#side", "cullface", "west"), "east", obj("texture", "#side", "cullface", "east")));
    }

    /** The soil in a flat colony block, up to {@link #FLOOR}, its own floor on top; in the world its renderer draws over it. */
    static Map<String, Object> soilCore() {
        return obj("from", arr(2, 1, 2), "to", arr(14, FLOOR, 14), "faces", obj(
                "up", obj("texture", "#top"), "north", obj("texture", "#soil"), "south", obj("texture", "#soil"),
                "west", obj("texture", "#soil"), "east", obj("texture", "#soil")));
    }

    /** The parts every colony block shares: a frame beam for each of the twelve edges, a glass pane for each side and the top. */
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
                    "textures", obj("particle", ac("block/terrarium_glass"),
                            "glass", obj("force_translucent", true, "sprite", ac("block/terrarium_glass"))),
                    "elements", arr(box(p(c[0], c[1], c[2]), p(c[3], c[4], c[5]), "#glass", "#glass"))));
            if (!pane.getKey().equals("glass_up")) {
                // The same panes for a stacked part's two glasses, in a glass with one short glint.
                write(ASSETS.resolve("models/block/frame/" + pane.getKey().replace("glass_", "pane_") + ".json"),
                        obj("parent", "minecraft:block/block", "textures", obj("particle", ac("block/formicarium_pane"),
                                "glass", obj("force_translucent", true, "sprite", ac("block/formicarium_pane"))),
                        "elements", arr(box(p(c[0], c[1], c[2]), p(c[3], c[4], c[5]), "#glass", "#glass"))));
            }
        }
    }

    /** The sides that get a glass pane when nothing of a colony is there. */
    static final String[] GLASS = {"north", "east", "south", "west", "up"};

    /** A colony part's blockstate: its own model by facing, plus frame beams and glass where it touches no other colony part. */
    static void colonyPart(String block) {
        colonyPart(block, List.of());
    }

    /** A colony part with more parts on top (the expedition sites' portals, by facing and lit). */
    static void colonyPart(String block, List<Object> extra) {
        uprightModel(block);
        List<Object> parts = new ArrayList<>();
        int[] rotations = {0, 90, 180, 270};
        for (int f = 0; f < 4; f++) {
            // In a colony built flat: the glass case. In one built upright: the upright nest.
            parts.add(obj("when", obj("facing", HORIZONTAL[f], "upright", "false"),
                    "apply", obj("model", ac("block/" + block), "y", rotations[f] == 0 ? null : rotations[f])));
            parts.add(obj("when", obj("facing", HORIZONTAL[f], "upright", "true"),
                    "apply", obj("model", ac("block/" + block + "_upright"), "y", rotations[f] == 0 ? null : rotations[f])));
        }
        for (String[] edge : EDGES) {
            parts.add(obj("when", obj(edge[0], "false", edge[1], "false"), "apply", obj("model", ac("block/frame/" + edge[0] + "_" + edge[1]))));
        }
        for (String side : GLASS) {
            parts.add(obj("when", obj(side, "false", "upright", "false"), "apply", obj("model", ac("block/frame/glass_" + side))));
        }
        // An upright part's two glasses: the side it faces and the one behind, each unless another part stands against it.
        for (String[] axis : new String[][] {{"north|south", "north"}, {"north|south", "south"}, {"east|west", "east"}, {"east|west", "west"}}) {
            parts.add(obj("when", obj("facing", axis[0], axis[1], "false", "upright", "true"),
                    "apply", obj("model", ac("block/frame/pane_" + axis[1]))));
        }
        parts.addAll(extra);
        write(ASSETS.resolve("blockstates/" + block + ".json"), obj("multipart", parts));
    }

    /**
     * A colony part's model in an upright colony: plaster, with a picture of its nest (TexGen's {@code <block>_front})
     * front and back, which shows when the block is too far away for its renderer.
     */
    static void uprightModel(String block) {
        write(ASSETS.resolve("models/block/" + block + "_upright.json"), obj("parent", "minecraft:block/block",
                "textures", obj("particle", ac("block/formicarium_panel"), "panel", ac("block/formicarium_panel"),
                        "front", ac("block/" + block + "_front")),
                "elements", arr(obj("from", arr(0, 0, 3.05), "to", arr(16, 16, 12.95), "faces", obj(
                        "north", obj("uv", arr(0, 0, 16, 16), "texture", "#front"), "south", obj("uv", arr(16, 0, 0, 16), "texture", "#front"),
                        "west", face("#panel"), "east", face("#panel"), "up", face("#panel"), "down", face("#panel"))))));
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
                "colony_scanner", "diamond_fragment", "emerald_fragment", "wild_ant_spawn_egg", "ant_head", "nether_trail_pheromone",
                "end_trail_pheromone", "ant_resin"};
        for (String item : flat) {
            write(models.resolve(item + ".json"), obj("parent", item.equals("tweezers") ? "minecraft:item/handheld" : "minecraft:item/generated",
                    "textures", obj("layer0", ac("item/" + item))));
            write(items.resolve(item + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + item))));
        }
        for (String type : UPGRADE_TYPES) {
            for (int tier = 1; tier <= 4; tier++) {
                String id = type + "_upgrade_" + tier;
                write(models.resolve(id + ".json"), obj("parent", "minecraft:item/generated",
                        "textures", obj("layer0", ac("item/upgrade_board_" + tier),
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
        // (The Liquid Chamber's and the Droplet Centrifuge's item models are written with the rest of theirs, in liquids().)
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
                {"block.genetic_lab.scan", "minecraft:block.note_block.hat", 0.5, 1.9},
                {"block.genetic_lab.discovery", "minecraft:block.amethyst_block.chime", 1.0, 1.5},
        };
        for (Object[] s : list) {
            String name = (String) s[0];
            sounds.put(name, obj("subtitle", "subtitles." + NS + "." + name,
                    "sounds", arr(obj("name", s[1], "type", "event", "volume", s[2], "pitch", s[3]))));
            LANG.put("subtitles." + NS + "." + name, title(name.substring(name.lastIndexOf('.') + 1)).replace("Step", "Ant scuttles")
                    .replace("Hurt", "Ant hurts").replace("Bite", "Ant bites").replace("Ambience", "Colony bustles").replace("Food", "Ants collect food")
                    .replace("Queen", "Queen stirs").replace("Egg", "Queen lays eggs").replace("Activate", "Colony structure starts").replace("Battle", "Soldiers fight")
                    .replace("Evolution", "Queen evolves").replace("Hum", "Genetic Laboratory hums").replace("Complete", "Genetic work completes")
                    .replace("Capture", "Ant caught").replace("Scan", "Laboratory scanner reads").replace("Discovery", "Genetic discovery"));
        }
        write(ASSETS.resolve("sounds.json"), sounds);
    }

    // --- Other mods' files ---

    /**
     * FTB Library's face icons for the mod's mobs (FTB Quests and FTB Chunks draw them): the ant head of the creative
     * tab. They go in FTB Library's own namespace, at textures/faces/<mod>/<mob>.json.
     */
    static void ftbFaces() {
        for (String mob : List.of("wild_ant", "guard_ant")) {
            write(RES.resolve("assets/ftblibrary/textures/faces/" + NS + "/" + mob + ".json"),
                    obj("texture", NS + ":textures/item/ant_head.png"));
        }
    }

    // --- Hand-shaped block models ---

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
        if (!textures.containsKey("soil") && elements.toString().contains("#soil")) {
            // The soil core's sides (soilCore): plain dirt; in the world the block's renderer covers it.
            textures.put("soil", "minecraft:block/dirt");
        }
        write(ASSETS.resolve("models/block/" + name + ".json"), obj("parent", "minecraft:block/block", "textures", textures, "elements", elements));
    }

    static void customModels() {
        // The Formicarium: a wooden base and soil; its glass and frame come from the colony parts.
        model("terrarium", obj("particle", ac("block/terrarium_wood"), "wood", ac("block/terrarium_wood"), "side", ac("block/terrarium_wood"),
                "top", "minecraft:block/rooted_dirt"), arr(floorBody(), soilCore()));
        // Wild nests: a mound with an entrance on top.
        write(ASSETS.resolve("models/block/ant_nest.json"), obj("parent", "minecraft:block/block", "textures", obj("particle", "#side"),
                "elements", arr(
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
                        floorBody(), soilCore(),
                        // A mud mound with the brood beside it: eggs, and larvae curled up.
                        box(p(3, y, 3), p(9, y + 2.5, 9), "#mud", "#mud"), box(p(4.5, y + 2.5, 4.5), p(7.5, y + 3.5, 7.5), "#mud", "#mud"),
                        box(p(10.5, y, 4), p(11.5, y + 1.5, 5), "#egg", "#egg"), box(p(12, y, 4.5), p(13, y + 1.5, 5.5), "#egg", "#egg"),
                        box(p(11, y, 6), p(12, y + 1.5, 7), "#egg", "#egg"), box(p(4, y, 11), p(7, y + 1, 12), "#larva", "#larva"),
                        box(p(9, y, 11.5), p(12, y + 1, 12.5), "#larva", "#larva")));
                case "queen_chamber" -> model(s, obj("particle", top, "side", wood, "top", top, "gold", "minecraft:block/gold_block",
                        "comb", "minecraft:block/honeycomb_block"), arr(
                        floorBody(), soilCore(),
                        // A golden dais with a royal cell on it.
                        box(p(4, y, 4), p(12, y + 1, 12), "#gold", "#gold"), box(p(6, y + 1, 6), p(10, y + 3.5, 10), "#comb", "#comb")));
                case "foraging_site" -> model(s, obj("particle", top, "side", wood, "top", top, "stick", "minecraft:block/oak_log",
                        "bone", "minecraft:block/bone_block_side", "dirt", "minecraft:block/coarse_dirt"), arr(
                        floorBody(), soilCore(),
                        box(p(3, y, 3), p(6, y + 1, 6), "#dirt", "#dirt"), box(p(10, y, 10), p(13, y + 1.5, 13), "#dirt", "#dirt"),
                        obj("from", arr(9, y, 3), "to", arr(10, y + 1, 9), "rotation", obj("angle", 22.5, "axis", "y", "origin", arr(9.5, y + 0.5, 6)),
                                "faces", obj("up", face("#stick"), "north", face("#stick"), "south", face("#stick"),
                                        "west", face("#stick"), "east", face("#stick"))),
                        box(p(3, y, 11), p(6, y + 0.8, 12), "#bone", "#bone"), box(p(4, y, 10), p(5, y + 0.8, 13), "#bone", "#bone")));
                case "harvesting_site" -> model(s, obj("particle", top, "side", wood, "top", top, "stem", "minecraft:block/mushroom_stem",
                        "cap", "minecraft:block/red_mushroom_block", "cap2", "minecraft:block/brown_mushroom_block"), arr(
                        floorBody(), soilCore(),
                        box(p(3.5, y, 3.5), p(4.5, y + 3, 4.5), "#stem", "#stem"), box(p(2.5, y + 3, 2.5), p(5.5, y + 4, 5.5), "#cap", "#cap"),
                        box(p(11.5, y, 10.5), p(12.5, y + 4, 11.5), "#stem", "#stem"), box(p(10, y + 4, 9), p(14, y + 5, 13), "#cap2", "#cap2"),
                        box(p(11, y, 4), p(11.8, y + 1.2, 4.8), "#stem", "#stem"), box(p(10.4, y + 1.2, 3.4), p(12.4, y + 1.9, 5.4), "#cap", "#cap")));
                case "hunting_ground" -> model(s, obj("particle", top, "side", wood, "top", top, "bone", "minecraft:block/bone_block_side",
                        "stone", "minecraft:block/cobblestone"), arr(
                        floorBody(), soilCore(),
                        // Stones for cover and the bones of past catches.
                        box(p(2, y, 2), p(5, y + 2, 5), "#stone", "#stone"), box(p(11, y, 10), p(14, y + 1.5, 13), "#stone", "#stone"),
                        box(p(10, y, 3), p(13, y + 0.8, 4), "#bone", "#bone"), box(p(11, y, 2), p(12, y + 0.8, 5), "#bone", "#bone"),
                        box(p(3, y, 11), p(7, y + 0.8, 12), "#bone", "#bone")));
                // The Storage: a plank pallet on the colony's soil, the store's heap on it (StructureRenderer).
                default -> model(s, obj("particle", top, "side", wood, "top", "minecraft:block/coarse_dirt", "pallet", top), arr(
                        floorBody(), soilCore(),
                        box(p(3, y, 3), p(13, y + 1, 13), "#side", "#pallet")));
            }
        }
        model("upgrade_station", obj("particle", ac("block/upgrade_station_side"), "side", ac("block/upgrade_station_side"),
                "top", ac("block/upgrade_station_top"), "leg", "minecraft:block/stripped_oak_log"), arr(
                box(p(0, 10, 0), p(16, 13, 16), "#side", "#top"),
                box(p(1, 0, 1), p(3, 10, 3), "#leg", "#leg"), box(p(13, 0, 1), p(15, 10, 3), "#leg", "#leg"),
                box(p(1, 0, 13), p(3, 10, 15), "#leg", "#leg"), box(p(13, 0, 13), p(15, 10, 15), "#leg", "#leg"),
                box(p(3, 4, 3), p(13, 5, 13), "#leg", "#leg")));
    }

    // --- Language ---

    // --- Companions ---

    static final String[][] COMPANION_COSTUMES = {{"pirate", "Pirate Ant Hat"}, {"explorer", "Explorer Ant Hat"}, {"miner", "Miner Ant Helmet"},
            {"crown", "Royal Ant Crown"}, {"chef", "Chef Ant Hat"}, {"flower", "Ant Flower Crown"}, {"backpack", "Ant Backpack"},
            {"party", "Party Ant Hat"}};

    /** "Iron Ant" as a companion: "Iron Guard Ant". */
    static String guardName(String name) {
        return name.endsWith(" Ant") ? name.substring(0, name.length() - 4) + " Guard Ant" : name + " Guard Ant";
    }

    static void companions() {
        List<String> items = new ArrayList<>(List.of("chitin_ant_plates", "hardened_chitin_ant_plates", "infernal_chitin_ant_plates",
                "void_chitin_ant_plates", "ant_cart"));
        for (String[] costume : COMPANION_COSTUMES) {
            items.add("ant_costume_" + costume[0]);
            LANG.put("item." + NS + ".ant_costume_" + costume[0], costume[1]);
        }
        for (String item : items) {
            write(ASSETS.resolve("models/item/" + item + ".json"), obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/" + item))));
            write(ASSETS.resolve("items/" + item + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + item))));
        }
        LANG.put("item." + NS + ".chitin_ant_plates", "Chitin Ant Plates");
        LANG.put("item." + NS + ".hardened_chitin_ant_plates", "Hardened Chitin Ant Plates");
        LANG.put("item." + NS + ".infernal_chitin_ant_plates", "Infernal Chitin Ant Plates");
        LANG.put("item." + NS + ".void_chitin_ant_plates", "Void Chitin Ant Plates");
        LANG.put("item." + NS + ".ant_cart", "Ant Cart");

        // Recipes: each tier of plates is built on the one before.
        shaped("chitin_ant_plates", ac("chitin_ant_plates"), 1, new String[] {"CCC", "CSC", "C C"}, "C", ac("chitin_plate"), "S", "string");
        shaped("hardened_chitin_ant_plates", ac("hardened_chitin_ant_plates"), 1, new String[] {" C ", "CPC", " C "}, "C", ac("hardened_chitin"),
                "P", ac("chitin_ant_plates"));
        shaped("infernal_chitin_ant_plates", ac("infernal_chitin_ant_plates"), 1, new String[] {" C ", "CPC", " C "}, "C", ac("infernal_chitin"),
                "P", ac("hardened_chitin_ant_plates"));
        shaped("void_chitin_ant_plates", ac("void_chitin_ant_plates"), 1, new String[] {" C ", "CPC", " C "}, "C", ac("void_chitin"),
                "P", ac("infernal_chitin_ant_plates"));
        shaped("ant_cart", ac("ant_cart"), 1, new String[] {"P P", "PPP", "IBI"}, "P", "#minecraft:planks", "I", "iron_nugget", "B", "stick");
        shaped("ant_costume_pirate", ac("ant_costume_pirate"), 1, new String[] {" B ", "WGW"}, "B", "bone_meal", "W", "black_wool", "G", "gold_nugget");
        shaped("ant_costume_explorer", ac("ant_costume_explorer"), 1, new String[] {" W ", "WLW"}, "W", "yellow_wool", "L", "leather");
        shaped("ant_costume_miner", ac("ant_costume_miner"), 1, new String[] {" T ", "INI"}, "T", "torch", "I", "iron_nugget", "N", "yellow_dye");
        shaped("ant_costume_crown", ac("ant_costume_crown"), 1, new String[] {"GEG", "GRG"}, "G", "gold_ingot", "E", "emerald", "R", "red_wool");
        shaped("ant_costume_chef", ac("ant_costume_chef"), 1, new String[] {"WW", "WP"}, "W", "white_wool", "P", "paper");
        shaped("ant_costume_flower", ac("ant_costume_flower"), 1, new String[] {"FFF", "FVF"}, "F", "#minecraft:small_flowers", "V", "vine");
        shaped("ant_costume_backpack", ac("ant_costume_backpack"), 1, new String[] {"LWL", "LSL"}, "L", "leather", "W", "green_wool", "S", "string");
        shaped("ant_costume_party", ac("ant_costume_party"), 1, new String[] {" Y ", "PSP"}, "Y", "yellow_dye", "P", "paper", "S", "string");

        // A name tag in the crafting grid takes the owner off a tube.
        write(DATA.resolve("recipe/free_guard_ant.json"), obj("type", ac("free_guard_ant")));

        write(DATA.resolve("tags/entity_type/no_companion_xp.json"), obj("values", arr()));
        write(DATA.resolve("tags/damage_type/companion_lasting_damage.json"), obj("values", arr("minecraft:wither", "minecraft:magic",
                "minecraft:indirect_magic", "minecraft:on_fire", "minecraft:in_fire", "minecraft:freeze")));
        LANG.put("tag.entity_type." + NS + ".no_companion_xp", "No Companion Experience");

        // Advancements; the transformations are secret.
        advancement("companion", "guard_ant", "rally_pheromone", "task", "Come Along, Then", "Have an ant join you: a Rally Pheromone on a wild ant, or a Guard Ant let out of its tube",
                companion("recruited"));
        advancement("companion_level_10", "companion", "chitin_ant_plates", "task", "Seasoned", "Have a Guard Ant reach level 10", companion("level_10"));
        advancement("companion_level_30", "companion_level_10", "void_chitin_ant_plates", "goal", "Old Soldier", "Have a Guard Ant reach level 30",
                companion("level_30"));
        secret("companion_sad", "companion", "minecraft:bundle", "All It Owns", "Show a Guard Ant a Bundle", companion("sad"));
        secret("companion_jumping", "companion", "minecraft:rabbit_foot", "Eight Legs' Worth", "Give a Guard Ant a taste for leaping", companion("jumping"));

        String c = "companion." + NS + ".";
        String m = "message." + NS + ".companion.";
        String g = "gui." + NS + ".companion.";
        String t = "tooltip." + NS + ".";
        String[][] lines = {
                {c + "guard_name", "Guard %s"},
                {c + "command.follow", "Follow"}, {c + "command.stay", "Stay"}, {c + "command.guard", "Guard"},
                {c + "personality.curious", "Curious"}, {c + "personality.tidy", "Tidy"}, {c + "personality.calm", "Calm"},
                {c + "personality.devoted", "Devoted"}, {c + "personality.playful", "Playful"},
                {c + "form.none", "No change"}, {c + "form.bindle", "Sad"}, {c + "form.jumping", "Jumping"},
                {m + "already", "This ant is a companion already"}, {m + "owned", "This ant belongs to someone else"},
                {m + "queen", "A Queen will not leave her colony"}, {m + "angry", "It is too angry to listen"},
                {m + "joined", "%s joins you"}, {m + "not_yours", "This Guard Ant belongs to %s"},
                {m + "unknown", "Its kind is not in this world any more"}, {m + "tweezers", "Use an empty Test Tube to pick up a Guard Ant"},
                {m + "cart_full", "Empty its cart first"}, {m + "form.already", "It is like that already"},
                {m + "too_many", "You already have %s Guard Ants out"},
                {m + "call.pheromone", "Calling a Guard Ant takes a Rally Pheromone"}, {m + "call.came", "%s comes to you"},
                {m + "call.lost", "A Guard Ant you called could not be found"},
                {"gui." + NS + ".roster.title", "Your Guard Ants"},
                {"gui." + NS + ".roster.empty", "None out. Use a Rally Pheromone on a wild ant."},
                {"gui." + NS + ".roster.count", "%s of %s out"}, {"gui." + NS + ".roster.count.any", "%s out"},
                {"gui." + NS + ".roster.level", "Level %s"}, {"gui." + NS + ".roster.here", "With you"},
                {"gui." + NS + ".roster.away", "%s blocks away"}, {"gui." + NS + ".roster.call", "Call"},
                {"gui." + NS + ".roster.call.hint", "Brings it to you for one Rally Pheromone"},
                {"gui." + NS + ".roster.called", "Coming"},
                {m + "level_up", "%s reached level %s"},
                {m + "command.follow", "%s follows you"}, {m + "command.stay", "%s stays here"}, {m + "command.guard", "%s guards this place"},
                {g + "name", "Name"}, {g + "level", "Level %s of %s"}, {g + "health", "Health %s/%s"}, {g + "damage", "Bite %s"}, {g + "armor", "Armor %s"},
                {g + "owner", "Belongs to %s"}, {g + "strength", "Hunting Strength %s"},
                {g + "xp", "Experience %s / %s"}, {g + "xp.max", "The highest level"}, {g + "xp.hint", "Only the ant that lands the last blow gains it"},
                {g + "command.follow", "It stays near you and fights what threatens you"},
                {g + "command.stay", "It waits here and only defends itself and you"},
                {g + "command.guard", "It keeps this place clear of hostile mobs"},
                {g + "pick_up", "Pick up"}, {g + "pick_up.hint", "Puts it in a tube, with all it has"},
                {g + "cart", "Cart"}, {g + "cart.hint", "Shows what its cart holds"}, {g + "ant", "Ant"}, {g + "ant.hint", "Shows the ant itself"},
                {g + "slot.armor", "Armor"}, {g + "slot.armor.hint", "Ant Plates"}, {g + "slot.costume", "Costume"},
                {g + "slot.costume.hint", "A hat or a backpack, only for looks"}, {g + "slot.cart", "Cart"},
                {g + "slot.cart.hint", "An Ant Cart makes it a Cart Ant"},
                {t + "companion_armor", "+%s Armor"}, {t + "companion_armor.toughness", "+%s Armor Toughness"},
                {t + "companion_gear", "Use on your Guard Ant"}, {t + "companion_costume", "Dress up your Guard Ant"},
                {t + "ant_cart", "%s slots, pulled by your Guard Ant"},
                {t + "guard_ant.trade", "Craft it with a name tag to free it for a new owner"},
                {t + "guard_ant.free", "Belongs to whoever lets it out"},
                {t + "guard_ant.level", "Level %s · Strength %s"}, {t + "guard_ant.health", "Health %s/%s"},
                {"jade." + NS + ".companion", "Level %s · %s"}};
        for (String[] line : lines) {
            LANG.put(line[0], line[1]);
        }
    }

    static Map<String, Object> companion(String what) {
        return obj("trigger", ac("companion"), "conditions", obj("what", what));
    }

    /** An advancement nobody sees before it is made. */
    static void secret(String id, String parent, String icon, String title, String description, Map<String, Object> criterion) {
        write(DATA.resolve("advancement/" + id + ".json"), obj("parent", ac(parent), "criteria", obj(id, criterion),
                "display", obj("icon", obj("id", icon), "title", obj("translate", "advancements." + NS + "." + id + ".title"),
                        "description", obj("translate", "advancements." + NS + "." + id + ".description"), "announce_to_chat", false, "hidden", true),
                "requirements", arr(arr(id))));
        LANG.put("advancements." + NS + "." + id + ".title", title);
        LANG.put("advancements." + NS + "." + id + ".description", description);
    }

    // --- Chitin gear, Guard Ants, giant anthills ---


    static final String[] CHITIN_ARMOR = {"chitin_helmet", "chitin_chestplate", "chitin_leggings", "chitin_boots"};
    static final String[] CHITIN_TOOLS = {"chitin_sword", "chitin_pickaxe", "chitin_axe", "chitin_shovel", "chitin_hoe"};

    // --- Chitin gear tiers ---

    static final String[] TIERS = {"hardened", "infernal", "void"};
    static final String[] TIER_NAMES = {"Hardened", "Infernal", "Void"};
    static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"};
    static final String[] PIECE_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots", "Sword", "Pickaxe", "Axe", "Shovel", "Hoe"};

    /** Hardened, Infernal and Void chitin gear: models, worn looks, recipes, tags, names and the full Void set's advancement. */
    static void chitinTiers() {
        Path items = ASSETS.resolve("items");
        Path models = ASSETS.resolve("models/item");
        Map<String, List<Object>> vanillaTags = new LinkedHashMap<>();
        String[] tagOf = {"head_armor", "chest_armor", "leg_armor", "foot_armor", "swords", "pickaxes", "axes", "shovels", "hoes"};
        for (int p = 0; p < PIECES.length; p++) {
            vanillaTags.computeIfAbsent(tagOf[p], k -> new ArrayList<>()).add(ac("chitin_" + PIECES[p]));
        }
        String[] smithingMaterial = {null, ac("infernal_chitin"), "minecraft:netherite_ingot"};
        String[] template = {null, ac("infernal_reinforcement"), ac("void_reinforcement")};
        for (int t = 0; t < TIERS.length; t++) {
            String tier = TIERS[t];
            for (int p = 0; p < PIECES.length; p++) {
                String id = tier + "_chitin_" + PIECES[p];
                write(models.resolve(id + ".json"), obj("parent", p < 4 ? "minecraft:item/generated" : "minecraft:item/handheld",
                        "textures", obj("layer0", ac("item/" + id))));
                write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + id))));
                vanillaTags.get(tagOf[p]).add(ac(id));
                LANG.put("item." + NS + "." + id, TIER_NAMES[t] + " Chitin " + PIECE_NAMES[p]);
                if (t > 0) {
                    // The smithing table: the tier below, the Reinforcement, the material.
                    write(DATA.resolve("recipe/" + id + "_smithing.json"), obj("type", "minecraft:smithing_transform",
                            "addition", smithingMaterial[t], "base", ac(TIERS[t - 1] + "_chitin_" + PIECES[p]),
                            "result", obj("id", ac(id)), "template", template[t]));
                }
            }
            write(ASSETS.resolve("equipment/" + tier + "_chitin.json"), obj("layers", obj("humanoid", arr(obj("texture", ac(tier + "_chitin"))),
                    "humanoid_baby", arr(obj("texture", ac(tier + "_chitin"))), "humanoid_leggings", arr(obj("texture", ac(tier + "_chitin"))))));
            write(DATA.resolve("tags/item/repairs_" + tier + "_chitin_gear.json"), obj("values", arr(ac(tier + "_chitin"))));
            LANG.put("tag.item." + NS + ".repairs_" + tier + "_chitin_gear", TIER_NAMES[t] + " Chitin Gear Repair Materials");
        }
        // Hardened: the Chitin shapes, in Hardened Chitin.
        String h = ac("hardened_chitin");
        shaped("hardened_chitin_helmet", ac("hardened_chitin_helmet"), 1, new String[] {"CCC", "C C"}, "C", h);
        shaped("hardened_chitin_chestplate", ac("hardened_chitin_chestplate"), 1, new String[] {"C C", "CCC", "CCC"}, "C", h);
        shaped("hardened_chitin_leggings", ac("hardened_chitin_leggings"), 1, new String[] {"CCC", "C C", "C C"}, "C", h);
        shaped("hardened_chitin_boots", ac("hardened_chitin_boots"), 1, new String[] {"C C", "C C"}, "C", h);
        shaped("hardened_chitin_sword", ac("hardened_chitin_sword"), 1, new String[] {"C", "C", "S"}, "C", h, "S", "stick");
        shaped("hardened_chitin_pickaxe", ac("hardened_chitin_pickaxe"), 1, new String[] {"CCC", " S ", " S "}, "C", h, "S", "stick");
        shaped("hardened_chitin_axe", ac("hardened_chitin_axe"), 1, new String[] {"CC", "CS", " S"}, "C", h, "S", "stick");
        shaped("hardened_chitin_shovel", ac("hardened_chitin_shovel"), 1, new String[] {"C", "S", "S"}, "C", h, "S", "stick");
        shaped("hardened_chitin_hoe", ac("hardened_chitin_hoe"), 1, new String[] {"CC", " S", " S"}, "C", h, "S", "stick");
        // The Reinforcements: two at a time, from the chitin and the dimension's rarer finds.
        shaped("infernal_reinforcement", ac("infernal_reinforcement"), 2, new String[] {"IBI", "MTM", "IBI"},
                "I", ac("infernal_chitin"), "B", "blaze_rod", "M", "magma_cream", "T", "ghast_tear");
        shaped("void_reinforcement", ac("void_reinforcement"), 2, new String[] {"VSV", "PEP", "VSV"},
                "V", ac("void_chitin"), "S", "shulker_shell", "P", "popped_chorus_fruit", "E", "ender_eye");
        for (String reinforcement : new String[] {"infernal_reinforcement", "void_reinforcement"}) {
            write(models.resolve(reinforcement + ".json"),
                    obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/" + reinforcement))));
            write(items.resolve(reinforcement + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + reinforcement))));
        }
        LANG.put("item." + NS + ".infernal_reinforcement", "Infernal Reinforcement");
        LANG.put("item." + NS + ".void_reinforcement", "Void Reinforcement");
        LANG.put("tooltip." + NS + ".infernal_reinforcement",
                "Smithing template: turns Hardened Chitin gear into Infernal with a piece of Infernal Chitin.");
        LANG.put("tooltip." + NS + ".void_reinforcement",
                "Smithing template: turns Infernal Chitin gear into Void with a netherite ingot.");
        LANG.put("tooltip." + NS + ".chitin_set.fire", " Fire Resistance");
        LANG.put("message." + NS + ".queen_grew", "Your %s Queen grew with her colony (Queen stage %s): %s +1");
        LANG.put("gui." + NS + ".queen_grown", "Grown with her colony: stage %s");
        LANG.put("tooltip." + NS + ".queen_grown", "Grown with her colony to stage %s");
        LANG.put("tooltip." + NS + ".queen_grows", "Grows with her colony: %s minutes at a bigger stage (longer for each next one), then two traits +1 (up to level %s)");
        LANG.put("tooltip." + NS + ".queen_growing", "Growing into stage %s: %s min to go");
        LANG.put("tooltip." + NS + ".chitin_set.mixed", " Mixed tiers count: the lowest tier decides");
        // The vanilla tool and armor tags, all tiers (enchanting, durability and so on go by them).
        for (Map.Entry<String, List<Object>> tag : vanillaTags.entrySet()) {
            write(RES.resolve("data/minecraft/tags/item/" + tag.getKey() + ".json"), obj("values", tag.getValue()));
        }
        advancement("void_chitin_set", "chitin_set", "void_chitin_chestplate", "challenge", "Carapace of the Void",
                "Own a full set of Void Chitin armor",
                obj("trigger", "minecraft:inventory_changed", "conditions", obj("items", arr(obj("items", ac("void_chitin_helmet")),
                        obj("items", ac("void_chitin_chestplate")), obj("items", ac("void_chitin_leggings")),
                        obj("items", ac("void_chitin_boots"))))));
    }

    /** The Chitin Shield's models, held and blocking: the vanilla shield's shape as a plain model, so it needs no renderer. */
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
        write(models.resolve("chitin_shield.json"),
                obj("gui_light", "front", "textures", textures, "elements", arr(plate, handle), "display", holding));
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
        // The vanilla tool and armor tags: chitinTiers() writes them, with every tier in them.

        // The giant anthill: the structure, how often (about one per 20x20 chunks, never in a village) and where.
        write(DATA.resolve("worldgen/structure/giant_anthill.json"),
                obj("type", ac("giant_anthill"), "biomes", "#" + ac("has_structure/giant_anthill"),
                        "spawn_overrides", obj(), "step", "surface_structures", "terrain_adaptation", "none"));
        write(DATA.resolve("worldgen/structure_set/giant_anthills.json"), obj("placement", obj("type", "minecraft:random_spread",
                        "exclusion_zone", obj("chunk_count", 4, "other_set", "minecraft:villages"), "salt", 1836401827,
                        "separation", 7, "spacing", 20),
                "structures", arr(obj("structure", ac("giant_anthill"), "weight", 1))));
        List<Object> giantAnthillBiomes = new ArrayList<>(List.of("#c:is_plains", "#c:is_forest",
                "#c:is_savanna", "#c:is_jungle", "#c:is_taiga", "#c:is_desert", "#c:is_badlands", "minecraft:meadow",
                "#c:is_swamp", "#c:is_mushroom", "minecraft:cherry_grove", "minecraft:snowy_plains", "minecraft:grove",
                // Bare ground too: beaches, stony shores, windswept hills, peaks and ice.
                "minecraft:pale_garden", "#c:is_beach", "#c:is_stony_shores", "#c:is_windswept", "#c:is_mountain/peak", "#c:is_icy"));
        SURFACE_WORLD_BIOMES.forEach(biome -> giantAnthillBiomes.add(obj("id", biome, "required", false)));
        write(DATA.resolve("tags/worldgen/biome/has_structure/giant_anthill.json"), obj("values", giantAnthillBiomes));
        // Its chests.
        write(DATA.resolve("loot_table/chests/giant_anthill.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem(c, 20, 2, 6), lootItem("minecraft:bone_meal", 15, 3, 8), lootItem(ac("ant_food"), 15, 2, 6),
                        lootItem("minecraft:gold_nugget", 10, 3, 9), lootItem("minecraft:iron_nugget", 10, 4, 10), lootItem(ac("test_tube"), 8, 1, 3),
                        lootItem(ac("royal_jelly"), 5, 1, 2), lootItem(ac("rally_pheromone"), 4, 1, 1), lootItem("minecraft:raw_gold", 3, 1, 2),
                        lootItem(ac("ant_resin"), 8, 1, 4), lootItem(ac("anthill_soil"), 6, 4, 12),
                        lootItem(ac("diamond_fragment"), 3, 1, 4), lootItem("minecraft:emerald", 2, 1, 2), lootItem(ac("evolution_catalyst"), 1, 1, 1)),
                        "rolls", obj("type", "minecraft:uniform", "max", 6, "min", 3)),
                obj("entries", arr(obj("type", "minecraft:empty", "weight", 3), lootItem(ac("chitin_helmet"), 1, 1, 1),
                        lootItem(ac("chitin_chestplate"), 1, 1, 1), lootItem(ac("chitin_leggings"), 1, 1, 1), lootItem(ac("chitin_boots"), 1, 1, 1),
                        lootItem(ac("chitin_pickaxe"), 1, 1, 1)), "rolls", 1)),
                "random_sequence", ac("chests/giant_anthill")));

        // The Nether anthill: rarer than the Overworld's, and its chests hold the Nether's materials.
        write(DATA.resolve("worldgen/structure/nether_anthill.json"),
                obj("type", ac("nether_anthill"), "biomes", "#" + ac("has_structure/nether_anthill"),
                        "spawn_overrides", obj(), "step", "surface_structures", "terrain_adaptation", "none"));
        write(DATA.resolve("worldgen/structure_set/nether_anthills.json"), obj("placement", obj("type", "minecraft:random_spread",
                        "salt", 517294613, "separation", 6, "spacing", 18),
                "structures", arr(obj("structure", ac("nether_anthill"), "weight", 1))));
        List<Object> netherAnthillBiomes = new ArrayList<>(List.of("minecraft:nether_wastes",
                "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:soul_sand_valley", "minecraft:basalt_deltas"));
        CAVE_WORLD_BIOMES.forEach(biome -> netherAnthillBiomes.add(obj("id", biome, "required", false)));
        write(DATA.resolve("tags/worldgen/biome/has_structure/nether_anthill.json"), obj("values", netherAnthillBiomes));
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
        advancement("guard_ant", "first_colony", "guard_ant", "task", "Bodyguard", "Get a Guard Ant in a tube: a Rally Pheromone on a Formicarium",
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
        LANG.put("tooltip." + NS + ".chitin_set.ants", " Wild ants leave you alone (not if you raid their anthill)");
        LANG.put("tooltip." + NS + ".chitin_set.dig", " Dig %s%% faster");
        LANG.put("tooltip." + NS + ".chitin_shield", "Light: raised faster than a wooden shield, and an axe knocks it down for less time.");
        LANG.put("tooltip." + NS + ".rally_pheromone","On a wild ant: it becomes your Guard Ant. On a Formicarium: the colony gives one. In the air: lists your Guard Ants.");
        LANG.put("tooltip." + NS + ".guard_ant.empty", "An empty Guard Ant tube.");
        LANG.put("tooltip." + NS + ".guard_ant.stats", "Strength %s · Health %s/%s · Bite %s · Armor %s");
        LANG.put("tooltip." + NS + ".guard_ant.use", "Use on a block to let it out. Right-click it for its screen; sneak-right-click to give the next order. Ant food heals it.");
        LANG.put("message." + NS + ".guard.no_queen", "This colony has no Queen to send a guard");
        LANG.put("message." + NS + ".guard.ants", "A Guard Ant takes %s ants; this colony has %s");
        LANG.put("message." + NS + ".guard.recruited", "%s ants answered the call: a Guard Ant joins you");
    }

    static Map<String, Object> lootItem(String item, int weight, int min, int max) {
        return obj("type", "minecraft:item", "modifier", min == max && min == 1 ? null : obj("type", "minecraft:set_count",
                "count", min == max ? min : obj("type", "minecraft:uniform", "max", max, "min", min)), "name", item, "weight", weight);
    }

    // --- Config screen ---

    /** Readable names for options whose camelCase name doesn't read well on its own. */
    static final Map<String, String> CONFIG_NAMES = Map.of(
            "ant3DModels", "3D ant models",
            "antDetailDistance", "3D ant distance",
            "maxVisualAnts", "Ants drawn per Formicarium",
            "terrariumCapacity", "Formicarium capacity",
            "terrariumFoodCapacity", "Formicarium food capacity",
            "visualDistance", "Ant draw distance",
            "rareFindMultiplier", "Rare find multiplier",
            "bonusExponent", "Bonus exponent");

    /** The info pages (the Formicarium's Species page, the book's species pages) and the book's how-to-play pages. */
    /** Names and texts of genetic research: the scanner, the laboratory's pages and the book's sections. */
    static void researchLang() {
        Map<String, String> l = LANG;
        l.put("tooltip." + NS + ".species.mutates", "Its Queens can be mutated in the Genetic Laboratory");
        l.put("tooltip." + NS + ".queen.locked", "[locked]");
        l.put("tooltip." + NS + ".queen.protected", "[protected]");
        l.put("tooltip." + NS + ".queen.stability", "Genetic stability: %s%%");
        l.put("origin." + NS + ".natural", "Natural species");
        l.put("origin." + NS + ".modified", "Genetically modified species");
        l.put("origin." + NS + ".hybrid", "Hybrid species");
        l.put("research." + NS + ".tier.basic", "Basic Research");
        l.put("research." + NS + ".tier.advanced", "Advanced Research");
        l.put("research." + NS + ".tier.experimental", "Experimental Research");
        l.put("research." + NS + ".tier.basic.short", "Basic");
        l.put("research." + NS + ".tier.advanced.short", "Advanced");
        l.put("research." + NS + ".tier.experimental.short", "Experimental");
        l.put("research." + NS + ".title.species", "%s Mutation");
        l.put("research." + NS + ".title.gene", "Gene: %s");
        l.put("research." + NS + ".hint.basic", "A common material of the Overworld.");
        l.put("research." + NS + ".hint.advanced", "A rare mineral, or a rarer find.");
        l.put("research." + NS + ".hint.experimental", "An endgame material, from another dimension or a boss.");
        l.put("toast." + NS + ".research", "Genetic discovery");
        l.put("toast." + NS + ".research.team", "Team discovery");
        l.put("toast." + NS + ".species", "New species");
        l.put("toast." + NS + ".species.team", "Team: new species");
        l.put("toast." + NS + ".analysis", "Queen analysed");
        l.put("toast." + NS + ".analysis.team", "Team: Queen analysed");
        l.put("status." + NS + ".needs_laboratory", "A species mutation: it is made in the Genetic Laboratory, not by feeding");
        l.put("status." + NS + ".not_researched", "This mutation is not researched: scan its material in a Genetic Laboratory");
        l.put("status." + NS + ".queen_not_grown", "The Queen has not grown enough: her colony must reach a bigger stage first");

        String g = "gui." + NS + ".lab.";
        // Each page: its tab, its full name, the line in the strip under the panels, and the help its tab and the "?" show.
        String[][] pages = {
                {"research", "Research", "Research", "Scanned once, researched for good",
                        "The laboratory's scanner. Put a material in the scanner slot and press Scan.\n"
                                + "The right material reveals a species mutation or a gene, for good.\n"
                                + "The material is not used up. A Test Tube of ants records their species.\n"
                                + "The list shows what you have found. What is still unknown is in the book."},
                {"analysis", "Analysis", "Analysis", "What this Queen is and what she can become",
                        "Shows the Queen in the slot on the left. Her genes: how far she has grown,\n"
                                + "and each of her mutations with what it does. Under that, what she can become.\n"
                                + "A check: she is ready. A yellow dot: something is still missing.\n"
                                + "A green dot: a natural change, made by feeding her Formicarium.\n"
                                + "Looking at her here records her in the Ant Identification Book."},
                {"mutation", "Mutation", "Species Mutation", "Choose a species, add its materials, press Mutate",
                        "Turns the Queen into another species.\n"
                                + "1. Research that species' material on the Research page.\n"
                                + "2. Put a Queen of the right species, grown enough, in the slot on the left.\n"
                                + "3. Choose the species, put the materials in the two slots, press Mutate.\n"
                                + "It always works. She keeps her traits, mutations and name.\n"
                                + "Back in her Formicarium, her colony keeps its ants."},
                {"editing", "Editing", "Genetic Editing", "",
                        "Changes the Queen's genes, as before.\n"
                                + "Upgrade: click a trait, put its materials in, press Upgrade.\n"
                                + "Reroll, Mutate and Purge take an item from the catalyst slot.\n"
                                + "Copy puts her genes into an empty Test Tube."},
                {"hybrid", "Hybrids", "Hybridization", "Copy genes here for a cross in a Queen Chamber",
                        "A hybrid is the daughter of two species, raised in a Queen Chamber.\n"
                                + "1. Put a Queen of one species here, and an empty Test Tube below.\n"
                                + "2. Press Copy genes: you get her Genetic Sample.\n"
                                + "3. Put the sample in the Queen Chamber of a colony of the other species.\n"
                                + "4. Each daughter raised there may be the hybrid. The listed chance is for two Queens at the listed trait level: higher traits raise it, lower ones cut it.\n"
                                + "The list shows the crosses whose two species you have both met."},
                {"stability", "Stability", "Stabilization", "For breeding: lock genes for her daughters",
                        "For breeding. It fixes what a Queen's daughters inherit in a Queen Chamber.\n"
                                + "Trait: click it and press Stabilize. Every daughter gets exactly that level.\n"
                                + "Mutation: click it on the right. Her Genetic Sample then always passes it on.\n"
                                + "The Stability % is the chance that a daughter's new flaw is dropped.\n"
                                + "It changes nothing in her own colony, and nothing else needs it.\n"
                                + "Release undoes it for free."}};
        for (String[] page : pages) {
            l.put(g + "tab." + page[0], page[1]);
            l.put(g + "page." + page[0], page[2]);
            l.put(g + "page." + page[0] + ".strip", page[3]);
            l.put(g + "page." + page[0] + ".help", page[4]);
        }
        String[][] lab = {
                {"scan", "Scan"}, {"scan.hint", "Reads the item in the scanner slot"},
                {"scan.keeps", "The item is not used up; scanning it again gains nothing"},
                {"scan.empty", "Put a material in the slot"}, {"scan.ready", "Ready to scan %s"},
                {"scan.no_power", "The scanner needs power"}, {"scan.nothing", "%s: no use to ants"},
                {"scan.new", "New discovery: %s!"}, {"scan.known", "%s: already known"},
                {"scan.locked", "%s: research more first"},
                {"scan.species_new", "New species recorded in the book"}, {"scan.species_known", "That species is already known"},
                {"research.none", "Nothing researched yet. Scan a material below."},
                {"research.intro", "Put a material in the scanner slot under this panel and press Scan: an ingot, a gem, a mob's drop. "
                        + "If ants can use it, you learn a species mutation or a gene. The Ant Identification Book has hints."},
                {"research.team", "%s, the team's"}, {"research.personal", "%s, yours"},
                {"research.scanned", "Scanned: %s"},
                {"research.source", "%s, stage %s:"},
                {"research.on_mutation", "On the Mutation page"},
                {"research.gives", "The mutation gives:"},
                {"research.on_editing", "Editing page: %s in the catalyst slot, then Mutate."},
                {"analysis.insert", "Put a Queen in the slot on the left."},
                {"analysis.intro", "Analysis shows how far a Queen has grown, her strengths and flaws, and what her species can become."},
                {"analysis.becomes", "She can become:"}, {"analysis.nothing", "Nothing known yet. Scan materials on the Research page."},
                {"analysis.growth", "Stage %s, traits %s"}, {"analysis.genes", "Her genes"},
                {"analysis.strength", "Strength: %s"}, {"analysis.flaw", "Flaw: %s"},
                {"analysis.no_mutations", "No mutations: no strengths and no flaws. A catalyst on the Editing page gives her one."},
                {"analysis.natural", "Natural: no laboratory"},
                {"analysis.natural.how", "Feed %s to her Formicarium from colony stage %s. It works by chance."},
                {"need.stage", "Queen stage %s"},
                {"need.stage.hint", "The Queen must have grown with a colony of stage %s. This one has grown to stage %s."},
                {"need.dimension", "Lab in %s"}, {"need.ready", "Ready: Mutation page"},
                {"need.waits", "Not ready yet"},
                {"mutation.insert", "Put a Queen in the slot on the left."},
                {"mutation.none", "Nothing researched for %s Queens yet. Scan materials on the Research page."},
                {"mutation.blocked", "Not ready"}, {"mutation.start", "Mutate"},
                {"mutation.start.hint", "Turns this Queen into a %s Queen"},
                {"mutation.keeps", "Her traits, mutations and lineage stay; her colony keeps its ants"},
                {"hybrid.list", "Known crosses:"},
                {"hybrid.none", "None yet. A cross shows here once you have met both of its species."},
                {"hybrid.intro", "A hybrid is the daughter of two species. Copy one Queen's genes here, then raise daughters in a Queen "
                        + "Chamber of a colony of the other species."},
                {"hybrid.parents", "%s + %s"}, {"hybrid.odds", "Chance: %s%% at traits %s, rising with them."},
                {"hybrid.queen", "Her traits: %s"}, {"hybrid.queen.low", "Her traits: %s, low"},
                {"hybrid.queen.hint", "The chance is for two Queens, this one and the one whose colony raises the daughter, with an average trait level of %s. Higher traits raise it. Lower traits cut it, down to a few per cent with every trait at level 1."},
                {"hybrid.how", "Copy her genes for the Queen Chamber of a %s colony."},
                {"hybrid.other", "Put a Queen of one of the two species in the lab."},
                {"hybrid.copy", "Copy genes"},
                {"hybrid.copy.hint", "Copies the Queen's genes into a Genetic Sample for a Queen Chamber"},
                {"hybrid.copy.cost", "Takes an empty Test Tube and 10,000 FE"},
                {"hybrid.step.queen", "Needs a Queen"}, {"hybrid.step.tube", "Add a Test Tube"}, {"hybrid.step.copy", "Ready to copy"},
                {"hybrid.step.take", "Take the sample"},
                {"stability", "Stability %s%%"}, {"stability.no_mutations", "No mutations"}, {"stability.held", "Stabilized"},
                {"stability.release", "Release"},
                {"stability.release.hint", "Lets this gene vary again (free; the Royal Jelly is not returned)"},
                {"stability.start", "Stabilize"}, {"stability.trait.hint", "Locks this trait: her daughters get exactly this level"},
                {"stability.mutation.hint", "Protects this mutation: it always passes on when her genes are crossed in"},
                {"stability.optional", "Optional: nothing needs it"}, {"stability.locked", "Stabilized: passes on unchanged"},
                {"stability.purpose", "For breeding: stabilized genes pass to her daughters unchanged."},
                {"stability.pick", "Click a trait or a mutation to stabilize it"},
                {"stability.trait.will", "Stabilize: her daughters get exactly %s %s"},
                {"stability.trait.held", "Locked: her daughters get exactly %s %s"},
                {"stability.mutation.will", "%s (%s): her sample would always pass it on"},
                {"stability.mutation.held", "%s (%s): her sample always passes it on"},
                {"stability.hint.1", "Genetic stability"},
                {"stability.hint.2", "The share of her traits and mutations that are stabilized."},
                {"stability.hint.3", "It is also the chance that a daughter's new flaw is dropped."},
                {"slot.mutation", "Materials of the chosen mutation"}, {"slot.mutation_output", "The transformed Queen comes out here"},
                {"slot.tube", "An empty Test Tube for the copy"}, {"slot.sample_output", "The Genetic Sample comes out here"},
                {"slot.stabilizer", "Royal Jelly"}, {"slot.scan", "Scanner: a material to research, or ants in a Test Tube"},
                {"working.transform", "Mutating into %s... %s%%"}, {"working.prepare_sample", "Copying her genes... %2$s%%"},
                {"working.stabilize", "Stabilizing %s... %s%%"}};
        for (String[] s : lab) {
            l.put(g + s[0], s[1]);
        }

        String[][] info = {{"mutation.unknown", "Unknown mutation"}, {"mutation.lab", "Genetic Laboratory: %sx %s"},
                {"mutation.needs", "A Queen grown to stage %s"}, {"mutation.dimension", "Laboratory in %s"}};
        for (String[] s : info) {
            l.put("info." + NS + "." + s[0], s[1]);
        }
        String[][] book = {
                {"unknown.name", "???"}, {"unknown.title", "Undiscovered species"},
                {"research.unknown_species", "Unknown mutation"}, {"research.unknown_gene", "Unknown gene"},
                {"research.undiscovered", "Undiscovered"}, {"research.locked", "Needs earlier research"}, {"research.first", "First: %s"},
                {"research.team", "Team discovery"}, {"research.personal", "Your discovery"},
                {"research.researched", "Researched"}, {"research.not_researched", "Not researched"},
                {"research.power", "Power: %s"}, {"research.catalyst", "Catalyst: %s"}, {"research.no_catalyst", "none"},
                {"research.takes", "Takes: %s"}, {"research.on_editing", "Given on the laboratory's Editing page."},
                {"compatibility.natural_change", "Natural, not a mutation"}, {"compatibility.nothing", "Nothing known"},
                {"unknown.natural", "A natural species. Meet it to fill in this page: catch one, scan a tube of them in the Genetic Laboratory, or keep a colony of it."},
                {"unknown.hybrid", "A hybrid, crossed in a Queen Chamber from two species:"}, {"unknown.cross", "%s x %s"},
                {"unknown.modified", "Its mutation is still to be researched: scan materials in the Genetic Laboratory."},
                {"unknown.evolves", "Evolves from %s"},
                {"researched", "Its mutation is researched:"}, {"researched.make", "Make one in the Genetic Laboratory to learn what it finds."},
                {"research.how", "Scan the right material in the Genetic Laboratory."}, {"research.material", "Scanned material:"},
                {"research.unlocks", "Unlocks %s:"}, {"research.complete", "Made: its species page is complete."},
                {"research.gene", "Reveals the mutation %s."},
                {"compatibility.analysed", "Queen analysed"}, {"compatibility.not_analysed", "No Queen analysed yet"},
                {"compatibility.mutations", "Laboratory mutations:"}, {"compatibility.natural", "By feeding the Formicarium:"},
                {"compatibility.hybrids", "Crosses:"}, {"compatibility.cross", "x %s = %s (%s%% at traits %s)"},
                {"compatibility.scan", "Scan a Queen of this species to learn what she needs"},
                {"production.kind", "%s species"}, {"production.rate", "Finds x%s compared to Common Ants"},
                {"production.unique", "Unique finds:"}, {"production.upgrades", "Its Species Upgrades:"},
                {"production.signature", "The material its Species Upgrades are made from"},
                {"discovered", "%s / %s discovered"},
                {"section.guide", "How to Play"}, {"section.guide.hint", "What everything is for"},
                {"section.species", "Species"}, {"section.species.hint", "Every species: what it looks like, where it lives, what it finds"},
                {"section.research", "Genetic Research"}, {"section.research.hint", "Scanned materials and what they unlock"},
                {"section.compatibility", "Ant Compatibility"}, {"section.compatibility.hint", "What the Queens of each species can become"},
                {"section.production", "Production"}, {"section.production.hint", "What each species brings in, and what it is best at"},
                {"section.guide.empty", "Nothing here."}, {"section.species.empty", "No species in this game."},
                {"section.research.empty", "No research in this game."},
                {"section.compatibility.empty", "No species met yet. Catch an ant, or open a Formicarium."},
                {"section.production.empty", "No species met yet. Catch an ant, or open a Formicarium."}};
        for (String[] s : book) {
            l.put("book." + NS + "." + s[0], s[1]);
        }
        l.put("jei." + NS + ".species_mutation", "Species Mutation");
        l.put("jei." + NS + ".research", "Research: scan %s");
        l.put("jei." + NS + ".queen_stage", "Queen grown to stage %s");
        l.put("jei." + NS + ".lab_dimension", "Laboratory in %s");
    }

    static void guideLang() {
        String[][] info = {{"climate.any", "Any climate suits it"}, {"climate.right", "The climate here suits it"},
                {"climate.wrong", "Wrong climate here: the colony loses health"}, {"climate.floor", "The block under the Formicarium makes it %s"},
                {"climate.fix", "A Formicarium on one of these suits it anywhere:"}, {"climate.under", "Place it under the Formicarium"},
                {"climate.variants", "Any of its %s variants works"},
                {"finds", "Its workers find:"}, {"farms", "Its farmers grow food (x%s) and harvest:"}, {"hunts", "Hunts at a Hunting Ground: %s"},
                {"evolution", "Evolution:"}, {"evolves_into", "Into %s"}, {"evolves_from", "From %s"},
                {"evolution.feed", "Feed the Formicarium %s"}, {"evolution.needs", "From stage %s and %s adults; %s%% a try, sure after %s tries"},
                {"chance", "%s%% of finds"}, {"from_stage", "From colony stage %s"}, {"signature", "Signature find (rare)"}};
        for (String[] entry : info) {
            LANG.put("info." + NS + "." + entry[0], entry[1]);
        }
        String[][] guide = {
                {"start", "Getting Started", String.join("\n",
                        "Craft Tweezers and some Test Tubes. Find wild ants or an Ant Nest and use the Tweezers on them: the ants go into an empty Test Tube in your inventory. Your first catch from a nest is always a Queen. After that Queens are rare; nests give one now and then.",
                        "Place a Formicarium, put the Queen Test Tube in, then some Ant Food. The Queen starts laying eggs at once: a Formicarium works on its own.",
                        "The Formicarium's screen shows the colony: its ants, genes, jobs and numbers, and on the Info page what its ants are like.",
                        "To move a colony, take it out in Test Tubes: an empty tube in the Queen slot takes the Queen out, empty tubes in the feed slot fill with ants (up to 1,000 each), and the Colony page's Take out buttons do the same from your inventory. Then break the Formicarium and put the tubes into a new one. Ants left inside a broken Formicarium are lost.")},
                {"chambers", "The Colony Build", String.join("\n",
                        "Chambers are additions. Put one against the Formicarium, or against a chamber already in the colony, on any side (above and below too): it joins, and the glass between them goes: the whole build becomes one glass case. A colony takes up to 32.",
                        "Build upward and the colony becomes an upright nest: once more of its blocks are stacked (one right over another) than stand flat on the ground, every block shows its own chamber, carved into the earth between a glass at the front and one at the back, and tunnels join it to the blocks beside, above and below. With as many stacked as flat it stays as it was; with more flat ones it is glass cases again. A tower or a wall of them is one nest, and the ants walk through all of it.",
                        "Nest Chamber: eggs at full speed, faster larvae, room for 3,000 adults and 1,500 larvae. Without one the Queen lays at half speed and only 60 adults fit.",
                        "Foraging Site: workers find the species' resources. It sends its finds to the Storage, and holds 9 stacks while the Storage is full.",
                        "Harvesting Site: Farmer Ants grow food and harvest there.",
                        "Hunting Ground: hunter species (Army Ants and the ants that evolve into hunters) kill mobs there: the mobs' loot, and food from what they catch.",
                        "Storage: more room for finds, and food room.",
                        "Queen Chamber: raises new Queens.",
                        "A chamber exactly as near to two Formicariums joins neither.")},
                {"climate", "Climate", String.join("\n",
                        "Every species likes a climate: temperate, cold, hot, underground or nether. In the wrong one the colony loses health, less with good Environmental Resistance.",
                        "A block under the Formicarium fixes it: snow or ice make it cold, sand or sandstone hot, grass or dirt temperate, stone or deepslate underground, netherrack or magma nether. The Formicarium's Info page shows the blocks for your colony.")},
                {"food", "Food", String.join("\n",
                        "Ants eat. Food goes in the Formicarium's food slot (beside the feed slot), by hand, by right-clicking the Formicarium or from a hopper: Ant Food, Royal Jelly, sugar, sweet berries and other plain foods. The Formicarium holds 2,000 food; each Storage adds 25,000.",
                        "Mineral and crystal species need their own food for their eggs. A starving colony works slowly, then loses ants.",
                        "Farmer Ants at a Harvesting Site grow food themselves.")},
                {"jobs", "Jobs", String.join("\n",
                        "Workers go to Harvesting Sites and Hunting Grounds first (they bring food), then to Foraging Sites. A twentieth of the adults stay home to look after the nest.",
                        "Each site takes up to 256 ants, more with Population Upgrades. Its screen sets how many it wants; Auto takes all it can.",
                        "The more ants on a job, the more they find, a little less for each extra ant. Only Farmer species farm, and only hunter species hunt: the Info page says which.")},
                {"evolution", "Evolution", String.join("\n",
                        "Some species adapt by themselves: feed the Formicarium the right material once the colony is big enough (the Info page lists it). A try may fail, but one always works after enough tries. An Evolution Catalyst helps.",
                        "Mineral and endgame species are not fed into being: they are species mutations, researched and then made in the Genetic Laboratory (see Genetic Research).",
                        "A Queen Chamber raises daughter Queens from the colony's Queen; with a Genetic Sample of another species it can raise a hybrid.")},
                {"genetics", "Genetics", String.join("\n",
                        "Each Queen has ten traits from 1 to 10, and daughters inherit them with small changes. Mutations add strengths, or flaws.",
                        "The Genetic Laboratory raises one trait at a time, rerolls, mutates and purges flaws on its Editing page. It runs on FE: put a Generator next to it, or any mod's cable.",
                        "Its Stabilization page is optional: a locked trait passes to her daughters exactly as it is, and a protected mutation always passes on when her genes are crossed in.")},
                {"research", "Genetic Research", String.join("\n",
                        "The Genetic Laboratory has a scanner on its Research page. Put a material in its slot and press Scan: the right material reveals a species mutation or a gene, for good. The material stays in the slot, and scanning it again gains nothing.",
                        "A Test Tube of ants in the scanner records their species; a Queen in the lab's Queen slot is analysed on the Analysis page. Catching ants and opening a Formicarium record the species too.",
                        "A researched mutation is made in the Genetic Laboratory, on its Species Mutation page: a Queen of the right species who has grown with a big enough colony, the material, power, and for the rarer ones Evolution Catalysts. It always works. Take the Queen out of her Formicarium in a Test Tube and put her back afterwards: her ants stay and turn into the new species as they are replaced.",
                        "Research is yours. With FTB Teams, the members of a party share what they discover while they are in it.")},
                {"upgrades", "Upgrades", String.join("\n",
                        "Upgrades go in a screen's upgrade slots; shift-clicking one puts it there. Each block takes the kinds it can use: the slot's tooltip, and JEI or EMI, tell which.",
                        "The Colony Upgrade Station makes species upgrades from an Upgrade Base and a species' signature material. They work only for that species, in Foraging Sites.")},
                {"companions", "Guard Ants", String.join("\n",
                        "A Rally Pheromone used on a wild ant makes it your Guard Ant; used on a Formicarium, the colony gives up ants for one of its species, as strong as its Queen's Hunting Strength. It keeps its species and looks, and grows.",
                        "Right-click it for its screen: a name, its level, what it wears and its orders. Sneak and right-click gives the next order: follow, stay, guard. It learns from every kill it lands itself, mends when it rests, and ant food heals it at once.",
                        "Ant Plates are its armor, a costume is only for looks, and an Ant Cart makes it carry nine stacks. An empty Test Tube picks it up with all it has. A tube opens only for its owner: craft it with a name tag to pass the ant on.",
                        "Use the pheromone in the air to see your Guard Ants and where they are, and to call one to you for a pheromone. It can die, and then leaves what it had.")},
                {"automation", "Automation", String.join("\n",
                        "Hoppers and pipes can feed the Formicarium and Queen Chambers, and empty Storage and Foraging Sites. A Storage with an Automation Upgrade pushes its finds into the container under it.",
                        "Comparators read a Formicarium's food, a site's workforce, or how full a store is. A redstone signal pauses a site: its ants go elsewhere.")}};
        for (String[] page : guide) {
            LANG.put("guide." + NS + "." + page[0] + ".title", page[1]);
            LANG.put("guide." + NS + "." + page[0] + ".text", page[2]);
        }
        LANG.put("gui." + NS + ".page.species", "Info");
    }

    /** Names, tooltips and section titles for NeoForge's configuration screen, read from the options and comments in AntConfig.java. */
    static void configLang() {
        Path source = Path.of("common/src/main/java/com/antcolonies/core/config/AntConfig.java");
        String text;
        try {
            text = Files.readString(source);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Matcher option = Pattern.compile(
                "\\.comment\\(((?:\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*,?)+)\\)\\s*\\.define\\w*\\(\\s*\"(\\w+)\"").matcher(text);
        Pattern part = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        int options = 0;
        while (option.find()) {
            String name = option.group(2);
            StringBuilder comment = new StringBuilder();
            Matcher pieces = part.matcher(option.group(1));
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
        Matcher section = Pattern.compile("\\.push\\(\"(\\w+)\"\\)").matcher(text);
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
                {"colony_scanner", "Colony Scanner"}, {"species_upgrade", "Species Upgrade"},
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
        String[][] blocks = {{"terrarium", "Formicarium"}, {"nest_chamber", "Nest Chamber"}, {"storage", "Storage"},
                {"foraging_site", "Foraging Site"}, {"harvesting_site", "Harvesting Site"}, {"hunting_ground", "Hunting Ground"},
                {"queen_chamber", "Queen Chamber"}, {"upgrade_station", "Colony Upgrade Station"}, {"generator", "Generator"},
                {"genetic_laboratory", "Genetic Laboratory"}};
        for (String[] b : blocks) {
            l.put("block." + NS + "." + b[0], b[1]);
        }
        for (String nest : NESTS) {
            l.put("block." + NS + "." + nest + "_ant_nest", title(nest) + " Ant Nest");
            l.put("tooltip." + NS + "." + nest + "_ant_nest",
                    "Place it and ants move in: they tend and harvest crops within 10 blocks and bring the harvest "
                    + "home, for hoppers and pipes to take out.");
        }
        l.put("entity." + NS + ".wild_ant", "Wild Ant");
        l.put("entity." + NS + ".royal_guard_ant", "Royal Guard Ant");
        l.put("tooltip." + NS + ".brood_cluster.use.eggs",
                "Put into the Formicarium of a working colony (a Queen and workers) of its species: adds %s eggs.");
        l.put("tooltip." + NS + ".brood_cluster.use.larvae",
                "Put into the Formicarium of a working colony (a Queen and workers) of its species: adds %s larvae.");
        l.put("tooltip." + NS + ".brood_cluster.placed", "Only an anthill's own clusters hatch: placed by you, it is a decoration.");
        // What players may still call the Formicarium: the item viewers find it (and its glass) under this word too.
        l.put("alias." + NS + ".terrarium", "Terrarium");
        // Tooltips of blocks and items. The Formicarium's says "terrarium": the creative tab's search reads names and tooltips only.
        String[][] tips = {
                {"terrarium", "An ant terrarium: the heart of a colony. Put a Queen in and feed her. Chambers placed against it, or against its chambers, join the colony. Take the Queen and ants out in Test Tubes before breaking it: what is left inside is lost."},
                {"nest_chamber", "Brood and housing: eggs at full speed, faster larvae, room for 3,000 adults and 1,500 larvae."},
                {"storage", "More room for what the colony finds, and food room. Hoppers can empty it; Storage Upgrades open more slots."},
                {"foraging_site", "Workers find their species' resources. Sends finds to Storage; holds 9 stacks when it is full."},
                {"hunting_ground", "Hunter species kill mobs here: the mobs' loot (from their loot tables; none spawn), and food from the catch."},
                {"harvesting_site", "Farmer Ants grow food here, plus mushrooms, seeds and berries."},
                {"queen_chamber", "Raises new Queens from the colony's Queen."},
                {"upgrade_station", "Makes species upgrades from an Upgrade Base and a species' signature material."},
                {"generator", "Burns furnace fuel into FE and gives it to the machines next to it, such as the Genetic Laboratory."},
                {"genetic_laboratory", "Scans materials for research, mutates Queens into researched species and edits their genes. Runs on FE: put a Generator next to it."},
                {"tweezers", "Catches wild ants into Test Tubes in your inventory: a tube of the same ants, or an empty one. Also works on wild nests."},
                {"test_tube", "Holds caught ants. Keep some in your inventory when using Tweezers."},
                {"ant_food", "Basic colony food (200 food)."}, {"mineral_ant_food", "Food for mineral species; their eggs need it."},
                {"crystal_ant_food", "Food for crystal species (Amethyst, Diamond, Emerald)."}, {"nether_ant_food", "Food for Netherite Ants."},
                {"royal_jelly", "Rich food (400) and the base of Queen rearing."}, {"nuptial_pheromone", "Needed to rear Queens of advanced species."},
                {"evolution_catalyst", "Feed to a Formicarium: doubles the chance of the next evolution attempt. The rarer species mutations of the Genetic Laboratory take it too."},
                {"reroll_serum", "Genetic Laboratory: reroll a trait (never lower) or purge a harmful mutation."},
                {"chitin_plate", "Only from ants: wild ants leave it, and foraging colonies bring it home."},
                {"hardened_chitin", "Only from ants: sometimes from wild ants, and from colonies of stage 2 and up. For tier II upgrades and the Genetic Laboratory."},
                {"infernal_chitin", "Only from ants of the Nether: wild ants there and colonies living there. For tier III upgrades and high genetics."},
                {"void_chitin", "Only from ants of the End: wild ants there and colonies living there. For the highest genetics and the Dragon Ant."}, {"upgrade_base", "The blank every upgrade starts from."},
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
        l.put("tooltip." + NS + ".tweezers.terrarium", "On a Formicarium: take workers (sneak: eggs) to start another colony.");
        l.put("tooltip." + NS + ".colony_scanner", "Right-click a Formicarium or colony structure for a report.");

        l.put("tooltip." + NS + ".ant_test_tube.count", "%s %s");
        l.put("tooltip." + NS + ".ant_test_tube.use", "Put into a Formicarium of the same species (or an empty one).");
        l.put("tooltip." + NS + ".queen_test_tube.empty", "Empty");
        l.put("tooltip." + NS + ".genetic_sample.use", "Cross into a daughter in a Queen Chamber's sample slot.");
        l.put("tooltip." + NS + ".brood.use", "Put into a Formicarium of the same species: each adds 4.");
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
                {"lab", "In the Genetic Lab:"}, {"centrifuge", "In the Droplet Centrifuge:"}, {"terrarium", "In the Formicarium:"}, {"terrarium_or_site", "In the Formicarium or a job site:"},
                {"terrarium_and_nest", "In the Formicarium & Nest Chambers:"}, {"storage", "In Storage:"}, {"hunting", "In Hunting Grounds:"}};
        for (String[] place : places) {
            l.put("tooltip." + NS + ".upgrade_place." + place[0], place[1]);
        }
        String[][] effects = {
                {"speed.sites", "+%s%% work speed"},
                {"speed.larvae", "+%s%% larvae growth"},
                {"speed.eggs", "+%s%% egg laying"},
                {"speed.queen", "+%s%% Queen rearing"},
                {"speed.lab", "%sx lab speed"},
                {"speed.centrifuge", "%sx spinning speed"},
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
        String[][] upgradeTips = {{"speed", "Faster work in chambers, the lab and the Centrifuge, faster larvae."}, {"food_efficiency", "Less food eaten."},
                {"simulation", "Formicarium: more offline catch-up (numbers only, no entities)."}, {"storage", "Storage: more slots and more food room."},
                {"breeding", "Faster egg laying and Queen rearing."}, {"genetic", "Cheaper lab work; more mutations in Queen Chambers."},
                {"hunting", "Hunting Ground: stronger hunters, more catches, fewer losses."}, {"automation", "Storage: pushes its finds into the container below."},
                {"population", "More ants housed, or more working in one chamber."}, {"evolution", "Formicarium: better evolution chances."}};
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
        for (String c : new String[] {"temperate", "cold", "hot", "underground", "nether", "end", "any"}) {
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
                {"missing_connection", "Not part of a colony: put it against a Formicarium or its chambers"},
                {"storage_full", "Storage full"},
                {"incompatible_species", "Incompatible species"}, {"insufficient_materials", "Insufficient materials (or colony not ready)"},
                {"two_colonies", "As near to two Formicariums: it joins neither"}, {"unknown_species", "Unknown species (missing data)"},
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
                {"slot.feed", "Feed slot"}, {"slot.feed.hint", "Ants in Test Tubes, eggs, larvae, an Evolution Catalyst or evolution materials; empty Test Tubes fill with ants"},
                {"slot.food", "Food slot"}, {"slot.food.hint", "Food for the colony: eaten as its food bar has room, and kept here while the bar is full"},
                {"slot.output", "Output"}, {"slot.output.hint", "Empty Test Tubes and leftovers come out here"},
                {"slot.upgrade", "Upgrade slot"}, {"slot.upgrade.species", "Its species: more or better finds"},
                {"slot.upgrade.more", "Higher tiers do more; they add up."},
                {"transforming", "Transforming from %s: %s%%"}, {"genetics_of", "%s, generation %s"}, {"no_mutations", "No mutations"},
                {"mutations", "Mutations:"}, {"jobs_header", "Working sites - %s ants home"}, {"job_workers", "%s / %s ants"},
                {"no_jobs", "No Foraging Site, Harvesting Site or Hunting Ground"},
                {"auto", "Auto"}, {"auto.hint", "Auto: as many ants as the chamber holds"}, {"per_minute", "/min"}, {"less_10", "-10"}, {"less_1", "-1"},
                {"more_1", "+1"}, {"more_10", "+10"}, {"farm.hint", "Needs Farmer Ants (or a species that farms)"},
                {"stat.eggs", "Eggs laid"}, {"stat.raised", "Adults raised"}, {"stat.food_in", "Food in"}, {"stat.food_from_storage", " of it from Storage"}, {"stat.food_out", "Food eaten"},
                {"stat.finds", "Finds"}, {"stat.hunts", "Hunts"}, {"stat.deaths", "Deaths"},
                {"stat.larvae_room", "Larvae room"}, {"stat.structures", "Chambers"}, {"stat.found_total", "Found in total"},
                {"evolution_attempts", "Evolution attempts: %s, catalysts: %s"}, {"upgrades", "Upgrades"}, {"linked", "Part of a colony"}, {"not_linked", "Not joined to a Formicarium"},
                {"contested", "As near to two Formicariums: joins neither"},
                {"site.workers", "Workers %s/%s (want %s)"}, {"site.hunters", "Hunters %s/%s (want %s)"},
                {"site.farmers", "Farmers %s/%s (want %s)"}, {"site.rate", "%s finds per minute"},
                {"site.hunt_rate", "%s hunts per minute"}, {"site.food_rate", "%s food per minute"}, {"site.store", "Finds wait here while the Storage is full"},
                {"site.loot_store", "Loot waits here while the Storage is full"},
                {"storage.slots", "%s slots"}, {"storage.upgrade_held", "Stuck: take out what won't fit without it"},
                {"queen_chamber.slots", "Rears a new Queen"}, {"queen_chamber.time", "Takes %s s at full speed"},
                {"slot.qc_tube", "Test Tube slot"}, {"slot.qc_tube.hint", "An empty Test Tube to hold the new Queen"},
                {"slot.qc_sample", "Sample slot (optional)"},
                {"slot.qc_sample.hint", "A Genetic Sample of another species, copied in the Genetic Laboratory (Hybrids page). "
                        + "Each new Queen uses up one, the left-most first."},
                {"queen_chamber.samples", "Genetic Samples"},
                {"queen_chamber.cross.none", "Optional: put Genetic Samples in the four slots above to cross another species in."},
                {"queen_chamber.cross.ready", "Cross: %s, %s%% chance per new Queen. Higher traits raise it. Each new Queen uses up one sample."},
                {"queen_chamber.cross.weak", "Cross: %s, only %s%% chance per new Queen: low traits (%s of %s). Each new Queen uses a sample."},
                {"queen_chamber.cross.no_pair", "No cross: %s and %s make no hybrid. Their traits still mix."},
                {"queen_chamber.cross.same", "Same species, so no hybrid. The new Queen mixes the two Queens' traits."},
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
                {"lab.materials_hint", "Materials go in the slots under the costs"},
                {"lab.power_hint", "Runs on FE: put a Generator, or any mod's cable, next to it"},
                {"lab.mutation_cap", "This Queen already carries %s beneficial mutations, the most one can (flaws don't count)"},
                {"generator.making", "Making %s FE/t"}, {"generator.full", "Full: waiting for a machine"}, {"generator.idle", "Idle: needs fuel"},
                {"generator.fuel", "Fuel slot"}, {"generator.fuel.hint", "Anything a furnace burns, for as long as it burns there"},
                {"generator.push", "Gives its energy to the machines and cables next to it"},
                {"generator.hint", "Place it next to the Genetic Laboratory"},
                {"lab.catalyst_hint", "Catalyst: Reroll Serum, a mutation catalyst, or an empty Test Tube"},
                {"effect.nest_chamber", "Eggs at full speed and faster larvae. Room for 3,000 adults and 1,500 larvae."},
                {"nest.adults", "Room for %s adults"}, {"nest.larvae", "Room for %s larvae"},
                {"take", "Take out:"}, {"take.workers", "Take"},
                {"take.hint", "Takes up to %s ants into an empty Test Tube from your inventory"},
                {"take.move", "Use the tube on another Formicarium to add them."},
                {"take.whole", "Breaking the Formicarium loses the ants still in it."},
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
                {"message." + NS + ".nest.recovering_in", "The nest is recovering: its ants are back in %s"},
                {"scanner." + NS + ".colony", "%s colony, stage %s"}, {"scanner." + NS + ".population", "%s workers, %s larvae, %s eggs"},
                {"scanner." + NS + ".food", "Food %s / %s (+%s, -%s per minute)"}, {"scanner." + NS + ".health", "Health %s%%, %s chambers"},
                {"scanner." + NS + ".queen", "Queen %s (%s)"}, {"scanner." + NS + ".linked", "Linked to the Formicarium at %s, %s, %s"},
                {"scanner." + NS + ".site", "%s / %s ants, %s per minute"}, {"tooltip." + NS + ".queen.lineage", "Lineage: %s"},
                {"book." + NS + ".kind", "%s, tier %s, %s"}, {"book." + NS + ".wild", "Wild in: %s"},
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
                {"loot", "Drops from this mob's own loot table"},
                {"trophy", "From stage %3$s also %1$s, from %2$s%% of catches"}, {"hybrid_chance", "Chance: %s%% with both parents at trait level %s"},
                {"hybrid_quality", "Higher traits raise the chance, lower ones cut it"},
                {"catalyst", "Catalyst in the Mutation Chamber"}, {"random", "Random only (weight %s)"}, {"negative", "Harmful"},
                {"level_step", "Level %s -> %s"}, {"energy", "%s FE"}, {"eggs_info", "%s eggs/min, hatch in %s min"},
                {"larvae_info", "Grows in %s min (species x%s)"}, {"needs", "Needs:"}, {"structures", "Structures:"},
                {"material_count", "%s signature items"}, {"tier", "Tier %s"}};
        for (String[] j : jei) {
            l.put("jei." + NS + "." + j[0], j[1]);
        }
        // The update's screens last: their names (the tabs) replace older ones.
        uiLang();
        Map<String, Object> out = new LinkedHashMap<>(l);
        write(ASSETS.resolve("lang/en_us.json"), out);
    }

    // --- The colony update: building blocks, expeditions, connectors ---

    /** A decorative block: id, display name, how it is modelled (cube, pillar, door, trapdoor, cluster) and its tool. */
    record Decor(String id, String name, String model, String tool) {}

    /** The tones of anthill soil, as SoilTone names them. */
    static final String[] SOIL_TONES = {"earth", "sand", "red", "frost", "stone", "pale", "dark", "nether"};

    static final List<Decor> DECOR = List.of(
            new Decor("anthill_soil", "Anthill Soil", "toned", "shovel"),
            new Decor("packed_anthill_soil", "Packed Anthill Soil", "toned", "pickaxe"),
            new Decor("anthill_bricks", "Anthill Bricks", "cube", "pickaxe"),
            new Decor("polished_anthill_bricks", "Polished Anthill Bricks", "cube", "pickaxe"),
            new Decor("nest_pillar", "Nest Pillar", "pillar", "pickaxe"),
            new Decor("chitin_block", "Chitin Block", "cube", "pickaxe"),
            new Decor("polished_chitin", "Polished Chitin", "cube", "pickaxe"),
            new Decor("chitin_bricks", "Chitin Bricks", "cube", "pickaxe"),
            new Decor("chitin_tiles", "Chitin Tiles", "cube", "pickaxe"),
            new Decor("amber_block", "Amber Block", "cube", "pickaxe"),
            new Decor("amber_bricks", "Amber Bricks", "cube", "pickaxe"),
            new Decor("amber_glass", "Amber Glass", "cube", null),
            new Decor("reinforced_amber", "Reinforced Amber", "cube", "pickaxe"),
            new Decor("fungal_block", "Fungal Block", "cube", "hoe"),
            new Decor("fungal_bricks", "Fungal Bricks", "cube", "hoe"),
            new Decor("glowing_fungal_block", "Glowing Fungal Block", "cube", "hoe"),
            new Decor("royal_chitin_block", "Royal Chitin Block", "cube", "pickaxe"),
            new Decor("royal_chitin_bricks", "Royal Chitin Bricks", "cube", "pickaxe"),
            new Decor("royal_chitin_pillar", "Royal Chitin Pillar", "pillar", "pickaxe"),
            new Decor("chiseled_royal_chitin", "Chiseled Royal Chitin", "cube", "pickaxe"),
            new Decor("nest_door", "Nest Door", "door", "axe"),
            new Decor("nest_trapdoor", "Nest Trapdoor", "trapdoor", "axe"),
            new Decor("pheromone_lamp", "Pheromone Lamp", "cube", null),
            new Decor("ant_egg_cluster", "Ant Egg Cluster", "cluster", null),
            new Decor("larva_cluster", "Larva Cluster", "cluster", null),
            new Decor("terrarium_glass", "Formicarium Glass", "cube", null));
    /** Blocks with stairs and slabs (ModDecor.WITH_STAIRS), and those with walls too (ModDecor.WITH_WALLS). */
    static final List<String> WITH_STAIRS = List.of("anthill_bricks", "polished_anthill_bricks", "polished_chitin", "chitin_bricks", "chitin_tiles",
            "amber_bricks", "fungal_bricks", "royal_chitin_bricks");
    static final List<String> WITH_WALLS = List.of("anthill_bricks", "chitin_bricks", "amber_bricks", "fungal_bricks");
    static final String[] NEW_CHAMBERS = {"nether_foraging_site", "end_foraging_site", "colony_connector"};

    /** "anthill_bricks" -> "anthill_brick" (its stairs, slab and wall), as ModDecor.stem. */
    static String stem(String id) {
        return id.endsWith("bricks") || id.endsWith("tiles") ? id.substring(0, id.length() - 1) : id;
    }

    static Decor decor(String id) {
        return DECOR.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    /** Every decorative block id with its tool, the stairs, slabs and walls included (for the mineable tags). */
    static Map<String, String> decorTools() {
        Map<String, String> tools = new LinkedHashMap<>();
        for (Decor d : DECOR) {
            if (d.tool() != null) {
                tools.put(d.id(), d.tool());
            }
            if (WITH_STAIRS.contains(d.id())) {
                tools.put(stem(d.id()) + "_stairs", d.tool());
                tools.put(stem(d.id()) + "_slab", d.tool());
            }
            if (WITH_WALLS.contains(d.id())) {
                tools.put(stem(d.id()) + "_wall", d.tool());
            }
        }
        return tools;
    }


    /** The End anthill, and the loot of the anthills' new rooms (treasure chambers and food stores). */
    static void anthillRooms() {
        write(DATA.resolve("worldgen/structure/end_anthill.json"), obj("type", ac("end_anthill"), "biomes", "#" + ac("has_structure/end_anthill"),
                "spawn_overrides", obj(), "step", "surface_structures", "terrain_adaptation", "none"));
        write(DATA.resolve("worldgen/structure_set/end_anthills.json"), obj("placement", obj("type", "minecraft:random_spread",
                        "salt", 1290377481, "separation", 6, "spacing", 20),
                "structures", arr(obj("structure", ac("end_anthill"), "weight", 1))));
        write(DATA.resolve("tags/worldgen/biome/has_structure/end_anthill.json"),
                obj("values", arr("minecraft:end_highlands", "minecraft:end_midlands")));
        write(DATA.resolve("loot_table/chests/end_anthill.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem(ac("chitin_plate"), 18, 2, 6), lootItem("minecraft:chorus_fruit", 16, 3, 9), lootItem("minecraft:end_stone", 12, 4, 12),
                        lootItem("minecraft:ender_pearl", 10, 1, 3), lootItem(ac("crystal_ant_food"), 10, 2, 5), lootItem("minecraft:popped_chorus_fruit", 8, 2, 6),
                        lootItem(ac("infernal_chitin"), 6, 1, 3), lootItem(ac("void_chitin"), 4, 1, 2), lootItem(ac("royal_jelly"), 6, 1, 3),
                        lootItem("minecraft:shulker_shell", 2, 1, 1), lootItem(ac("evolution_catalyst"), 3, 1, 2)),
                        "rolls", obj("type", "minecraft:uniform", "max", 6, "min", 3))),
                "random_sequence", ac("chests/end_anthill")));
        // A treasure chamber: what the ants hoarded.
        write(DATA.resolve("loot_table/chests/anthill_treasure.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem("minecraft:gold_ingot", 14, 2, 5), lootItem(ac("diamond_fragment"), 12, 2, 6), lootItem(ac("emerald_fragment"), 10, 2, 6),
                        lootItem(ac("royal_jelly"), 12, 2, 4), lootItem(ac("ant_resin"), 10, 2, 6), lootItem("minecraft:amethyst_shard", 8, 2, 6),
                        lootItem(ac("evolution_catalyst"), 5, 1, 2), lootItem(ac("nuptial_pheromone"), 4, 1, 2), lootItem("minecraft:diamond", 2, 1, 1),
                        lootItem(ac("chiseled_royal_chitin"), 3, 1, 2)),
                        "rolls", obj("type", "minecraft:uniform", "max", 6, "min", 4)),
                obj("entries", arr(obj("type", "minecraft:empty", "weight", 2), lootItem(ac("chitin_sword"), 1, 1, 1), lootItem(ac("chitin_shield"), 1, 1, 1),
                        lootItem(ac("crystal_core"), 1, 1, 1)), "rolls", 1)),
                "random_sequence", ac("chests/anthill_treasure")));
        // A food store: what the colony laid up to eat.
        write(DATA.resolve("loot_table/chests/anthill_food.json"), obj("type", "minecraft:chest", "pools", arr(
                obj("entries", arr(
                        lootItem(ac("ant_food"), 20, 3, 8), lootItem("minecraft:wheat_seeds", 15, 4, 12), lootItem("minecraft:sweet_berries", 12, 3, 8),
                        lootItem("minecraft:sugar", 12, 2, 6), lootItem("minecraft:honeycomb", 6, 1, 3), lootItem("minecraft:brown_mushroom", 8, 2, 5),
                        lootItem(ac("royal_jelly"), 4, 1, 2), lootItem("minecraft:glow_berries", 5, 2, 5), lootItem(ac("fungal_block"), 5, 1, 3)),
                        "rolls", obj("type", "minecraft:uniform", "max", 5, "min", 3))),
                "random_sequence", ac("chests/anthill_food")));
        // A midden: what a Brush works out of the refuse heap's suspicious gravel (or sand).
        write(DATA.resolve("loot_table/archaeology/anthill_midden.json"), obj("type", "minecraft:archaeology", "pools", arr(
                obj("entries", arr(
                        lootItem("minecraft:bone", 16, 1, 1), lootItem(ac("chitin_plate"), 14, 1, 1), lootItem("minecraft:flint", 10, 1, 1),
                        lootItem(ac("ant_resin"), 10, 1, 1), lootItem("minecraft:gold_nugget", 8, 1, 1), lootItem(ac("ant_food"), 8, 1, 1),
                        lootItem(ac("royal_jelly"), 4, 1, 1), lootItem(ac("diamond_fragment"), 4, 1, 1), lootItem(ac("emerald_fragment"), 3, 1, 1),
                        lootItem(ac("evolution_catalyst"), 2, 1, 1), lootItem("minecraft:emerald", 1, 1, 1)),
                        "rolls", 1)),
                "random_sequence", ac("archaeology/anthill_midden")));
        // An anthill's alarm (AnthillAlarm), on the action bar.
        LANG.put("message." + NS + ".anthill_alarm.chest", "The ants saw you raid their stores: the whole anthill is after you!");
        LANG.put("message." + NS + ".anthill_alarm.queen", "You stepped into the Queen's chamber: her guard attacks!");
        LANG.put("message." + NS + ".anthill_alarm.attack", "You struck one of the anthill's ants: the colony rises against you!");
        LANG.put("jade." + NS + ".rank.royal", "Guards the great Queen at its post");
        LANG.put("jade." + NS + ".rank.great_queen", "Great Queen of this anthill");
        advancement("end_anthill", "end_colony", "minecraft:end_stone_bricks", "task", "Island Hoppers", "Find an End anthill",
                obj("trigger", "minecraft:location", "conditions", obj("player", obj("type", "minecraft:entity_properties", "entity", "this",
                        "predicate", obj("minecraft:location", obj("structures", ac("end_anthill")))))));
    }

    /** The update's advancements: founding a colony from one Queen, linking colonies, and the expedition sites. */
    static void updateAdvancements() {
        advancement("queen_founding", "first_colony", "queen_test_tube", "goal", "From a Single Queen",
                "Found a colony with nothing but a Queen: feed her until her first workers are grown", colony("queen_founded", true));
        advancement("linked_colonies", "nether_trail", "colony_connector", "task", "Trail Network",
                "Link two colonies with Colony Connectors on the same channel", colony("linked", true));
        advancement("nether_trail", "nether_colony", "nether_trail_pheromone", "task", "Scent of Sulphur",
                "Find a Nether Trail Pheromone: a colony foraging in the Nether turns one up now and then", hasItem("nether_trail_pheromone"));
        advancement("nether_expedition", "nether_trail", "nether_foraging_site", "goal", "Portal Foragers",
                "Build a Nether Foraging Site, for expeditions into the Nether from home", hasItem("nether_foraging_site"));
        advancement("end_trail", "end_colony", "end_trail_pheromone", "task", "Scent of the Void",
                "Find an End Trail Pheromone: a colony foraging in the End turns one up now and then", hasItem("end_trail_pheromone"));
        advancement("end_expedition", "end_trail", "end_foraging_site", "goal", "Beyond the Rift",
                "Build an End Foraging Site, for expeditions into the End from home", hasItem("end_foraging_site"));
    }

    static void colonyUpdate() {
        decorAssets();
        decorData();
        expeditions();
        chamberAssets();
        anthillRooms();
        updateAdvancements();
        updateLang();
    }

    /** Blockstates, block models and item models of the building blocks. */
    static void decorAssets() {
        Path states = ASSETS.resolve("blockstates");
        Path models = ASSETS.resolve("models/block");
        Path items = ASSETS.resolve("items");
        for (Decor d : DECOR) {
            String id = d.id();
            String texture = ac("block/" + (id.equals("terrarium_glass") ? "terrarium_glass_block" : id));
            switch (d.model()) {
                case "cube" -> {
                    write(models.resolve(id + ".json"), obj("parent", "minecraft:block/cube_all", "textures", obj("all", texture)));
                    write(states.resolve(id + ".json"), obj("variants", obj("", obj("model", ac("block/" + id)))));
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + id))));
                }
                case "toned" -> {
                    // One model a tone; earth, the first, keeps the block's plain name and is the item's look.
                    Map<String, Object> variants = new LinkedHashMap<>();
                    for (String tone : SOIL_TONES) {
                        String name = tone.equals(SOIL_TONES[0]) ? id : id + "_" + tone;
                        write(models.resolve(name + ".json"), obj("parent", "minecraft:block/cube_all", "textures", obj("all", ac("block/" + name))));
                        variants.put("tone=" + tone, obj("model", ac("block/" + name)));
                    }
                    write(states.resolve(id + ".json"), obj("variants", variants));
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + id))));
                }
                case "pillar" -> {
                    String end = texture + "_top";
                    write(models.resolve(id + ".json"), obj("parent", "minecraft:block/cube_column", "textures", obj("end", end, "side", texture)));
                    write(models.resolve(id + "_horizontal.json"), obj("parent", "minecraft:block/cube_column_horizontal",
                            "textures", obj("end", end, "side", texture)));
                    write(states.resolve(id + ".json"), obj("variants", obj(
                            "axis=x", obj("model", ac("block/" + id + "_horizontal"), "x", 90, "y", 90),
                            "axis=y", obj("model", ac("block/" + id)),
                            "axis=z", obj("model", ac("block/" + id + "_horizontal"), "x", 90))));
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + id))));
                }
                case "door" -> {
                    Map<String, Object> variants = new LinkedHashMap<>();
                    String[] facings = {"east", "south", "west", "north"};
                    for (int f = 0; f < 4; f++) {
                        for (String half : new String[] {"lower", "upper"}) {
                            for (String hinge : new String[] {"left", "right"}) {
                                for (String open : new String[] {"false", "true"}) {
                                    String model = ac("block/" + id + "_" + (half.equals("lower") ? "bottom" : "top") + "_" + hinge
                                            + (open.equals("true") ? "_open" : ""));
                                    int y = (f * 90 + (open.equals("true") ? (hinge.equals("left") ? 90 : 270) : 0)) % 360;
                                    variants.put("facing=" + facings[f] + ",half=" + half + ",hinge=" + hinge + ",open=" + open,
                                            obj("model", model, "y", y == 0 ? null : y));
                                }
                            }
                        }
                    }
                    write(states.resolve(id + ".json"), obj("variants", variants));
                    for (String part : new String[] {"bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open",
                            "top_right", "top_right_open"}) {
                        write(models.resolve(id + "_" + part + ".json"), obj("parent", "minecraft:block/door_" + part,
                                "textures", obj("bottom", ac("block/" + id + "_bottom"), "top", ac("block/" + id + "_top"))));
                    }
                    write(ASSETS.resolve("models/item/" + id + ".json"),
                            obj("parent", "minecraft:item/generated", "textures", obj("layer0", ac("item/" + id))));
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("item/" + id))));
                }
                case "trapdoor" -> {
                    Map<String, Object> variants = new LinkedHashMap<>();
                    String[] facings = {"north", "east", "south", "west"};
                    for (int f = 0; f < 4; f++) {
                        int base = f * 90;
                        for (String half : new String[] {"bottom", "top"}) {
                            for (String open : new String[] {"false", "true"}) {
                                Map<String, Object> v;
                                if (open.equals("false")) {
                                    v = obj("model", ac("block/" + id + "_" + half), "y", base == 0 ? null : base);
                                } else if (half.equals("bottom")) {
                                    v = obj("model", ac("block/" + id + "_open"), "y", base == 0 ? null : base);
                                } else {
                                    int y = (base + 180) % 360;
                                    v = obj("model", ac("block/" + id + "_open"), "x", 180, "y", y == 0 ? null : y);
                                }
                                variants.put("facing=" + facings[f] + ",half=" + half + ",open=" + open, v);
                            }
                        }
                    }
                    write(states.resolve(id + ".json"), obj("variants", variants));
                    for (String part : new String[] {"bottom", "top", "open"}) {
                        write(models.resolve(id + "_" + part + ".json"), obj("parent", "minecraft:block/template_orientable_trapdoor_" + part,
                                "textures", obj("texture", texture)));
                    }
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + id + "_bottom"))));
                }
                case "cluster" -> {
                    // A pile on the floor: eggs (pearly ovals standing up) or larvae (curled grubs lying down).
                    boolean eggs = id.equals("ant_egg_cluster");
                    List<Object> elements = new ArrayList<>();
                    double[][] spots = eggs
                            ? new double[][] {{3, 3, 2, 3}, {6, 2.5, 2, 3.5}, {9.5, 3.5, 2, 3}, {4.5, 6.5, 2, 3.5}, {8, 6.5, 2, 3}, {11, 7, 2, 3.5},
                            {3.5, 10, 2, 3}, {7, 10, 2, 3.5}, {10.5, 10.5, 2, 3}, {6, 13, 2, 2.5}}
                            : new double[][] {{3, 3, 5, 2}, {9, 4, 4, 2}, {4, 8, 6, 2}, {11, 9, 3, 2}, {5, 12, 5, 2}};
                    for (double[] s : spots) {
                        double w = s[2];
                        double h = s[3];
                        double depth = eggs ? w : 2;
                        elements.add(obj("from", arr(s[0], 0, s[1]), "to", arr(s[0] + w, eggs ? h : 2, s[1] + depth), "faces", obj(
                                "up", obj("uv", arr(0, 0, w, depth), "texture", "#all"),
                                "north", obj("uv", arr(0, 0, w, eggs ? h : 2), "texture", "#all"),
                                "south", obj("uv", arr(8, 0, 8 + w, eggs ? h : 2), "texture", "#all"),
                                "west", obj("uv", arr(0, 8, depth, 8 + (eggs ? h : 2)), "texture", "#all"),
                                "east", obj("uv", arr(8, 8, 8 + depth, 8 + (eggs ? h : 2)), "texture", "#all"))));
                    }
                    write(models.resolve(id + ".json"), obj("parent", "minecraft:block/block", "textures", obj("all", texture, "particle", texture),
                            "elements", elements));
                    write(states.resolve(id + ".json"), obj("variants", obj("", obj("model", ac("block/" + id)))));
                    write(items.resolve(id + ".json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + id))));
                }
                default -> throw new IllegalStateException(d.model());
            }
            String texId = ac("block/" + id);
            if (WITH_STAIRS.contains(id)) {
                String s = stem(id);
                Map<String, Object> textures = obj("bottom", texture, "top", texture, "side", texture);
                write(models.resolve(s + "_stairs.json"), obj("parent", "minecraft:block/stairs", "textures", textures));
                write(models.resolve(s + "_stairs_inner.json"), obj("parent", "minecraft:block/inner_stairs", "textures", textures));
                write(models.resolve(s + "_stairs_outer.json"), obj("parent", "minecraft:block/outer_stairs", "textures", textures));
                Map<String, Object> variants = new LinkedHashMap<>();
                String[] facings = {"east", "south", "west", "north"};
                for (int f = 0; f < 4; f++) {
                    for (String half : new String[] {"bottom", "top"}) {
                        for (String shape : new String[] {"inner_left", "inner_right", "outer_left", "outer_right", "straight"}) {
                            int y = f * 90;
                            int x = 0;
                            if (half.equals("bottom") && shape.endsWith("left")) {
                                y += 270;
                            }
                            if (half.equals("top")) {
                                x = 180;
                                if (shape.endsWith("right")) {
                                    y += 90;
                                }
                            }
                            y %= 360;
                            String model = ac("block/" + s + "_stairs"
                                    + (shape.startsWith("inner") ? "_inner" : shape.startsWith("outer") ? "_outer" : ""));
                            variants.put("facing=" + facings[f] + ",half=" + half + ",shape=" + shape,
                                    obj("model", model, "uvlock", x != 0 || y != 0 ? true : null,
                                            "x", x == 0 ? null : x, "y", y == 0 ? null : y));
                        }
                    }
                }
                write(states.resolve(s + "_stairs.json"), obj("variants", variants));
                write(items.resolve(s + "_stairs.json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + s + "_stairs"))));
                write(models.resolve(s + "_slab.json"), obj("parent", "minecraft:block/slab", "textures", textures));
                write(models.resolve(s + "_slab_top.json"), obj("parent", "minecraft:block/slab_top", "textures", textures));
                write(states.resolve(s + "_slab.json"), obj("variants", obj("type=bottom", obj("model", ac("block/" + s + "_slab")),
                        "type=double", obj("model", texId), "type=top", obj("model", ac("block/" + s + "_slab_top")))));
                write(items.resolve(s + "_slab.json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + s + "_slab"))));
            }
            if (WITH_WALLS.contains(id)) {
                String s = stem(id);
                write(models.resolve(s + "_wall_post.json"), obj("parent", "minecraft:block/template_wall_post", "textures", obj("wall", texture)));
                write(models.resolve(s + "_wall_side.json"), obj("parent", "minecraft:block/template_wall_side", "textures", obj("wall", texture)));
                write(models.resolve(s + "_wall_side_tall.json"),
                        obj("parent", "minecraft:block/template_wall_side_tall", "textures", obj("wall", texture)));
                write(models.resolve(s + "_wall_inventory.json"), obj("parent", "minecraft:block/wall_inventory", "textures", obj("wall", texture)));
                List<Object> parts = new ArrayList<>();
                parts.add(obj("apply", obj("model", ac("block/" + s + "_wall_post")), "when", obj("up", "true")));
                String[] sides = {"north", "east", "south", "west"};
                for (String height : new String[] {"low", "tall"}) {
                    for (int i = 0; i < 4; i++) {
                        parts.add(obj("apply", obj("model", ac("block/" + s + (height.equals("low") ? "_wall_side" : "_wall_side_tall")),
                                "uvlock", true, "y", i == 0 ? null : i * 90), "when", obj(sides[i], height)));
                    }
                }
                write(states.resolve(s + "_wall.json"), obj("multipart", parts));
                write(items.resolve(s + "_wall.json"), obj("model", obj("type", "minecraft:model", "model", ac("block/" + s + "_wall_inventory"))));
            }
        }
    }

    static void stonecutting(String name, String input, String result, int count) {
        write(DATA.resolve("recipe/" + name + ".json"), obj("type", "minecraft:stonecutting", "ingredient", mc(input),
                "result", obj("count", count, "id", mc(result))));
    }

    /** Loot, tags and recipes of the building blocks, and the recipes of the new chambers and items. */
    static void decorData() {
        for (Decor d : DECOR) {
            if (d.id().equals("nest_door")) {
                // Only the lower half drops the door.
                write(DATA.resolve("loot_table/blocks/nest_door.json"), obj("type", "minecraft:block", "pools", arr(obj(
                        "condition", obj("type", "minecraft:survives_explosion"),
                        "entries", arr(obj("type", "minecraft:item", "condition", obj("type", "minecraft:match_block", "blocks", ac("nest_door"),
                                "state", obj("half", "lower")), "name", ac("nest_door"))), "rolls", 1)), "random_sequence", ac("blocks/nest_door")));
            } else if (d.id().endsWith("_cluster")) {
                // A live cluster drops only with Silk Touch, keeping its species; a placed one always drops itself.
                write(DATA.resolve("loot_table/blocks/" + d.id() + ".json"), obj("type", "minecraft:block", "pools", arr(obj(
                        "condition", obj("type", "minecraft:any_of", "terms", arr("minecraft:tool/can_silk_touch",
                                obj("type", "minecraft:match_block", "blocks", ac(d.id()), "state", obj("natural", "false")))),
                        "entries", arr(obj("type", "minecraft:item", "modifier", obj("type", "minecraft:copy_components",
                                "include", arr(ac("species")), "source", "block_entity"), "name", ac(d.id()))), "rolls", 1)),
                        "random_sequence", ac("blocks/" + d.id())));
            } else if (d.id().endsWith("glass")) {
                // Glass only with Silk Touch, like vanilla glass.
                write(DATA.resolve("loot_table/blocks/" + d.id() + ".json"), obj("type", "minecraft:block", "pools", arr(obj(
                        "condition", "minecraft:tool/can_silk_touch",
                        "entries", arr(obj("type", "minecraft:item", "name", ac(d.id()))), "rolls", 1)), "random_sequence", ac("blocks/" + d.id())));
            } else {
                selfDrop(d.id());
            }
            if (WITH_STAIRS.contains(d.id())) {
                String s = stem(d.id());
                selfDrop(s + "_stairs");
                // A double slab drops two.
                write(DATA.resolve("loot_table/blocks/" + s + "_slab.json"), obj("type", "minecraft:block", "pools", arr(obj(
                        "entries", arr(obj("type", "minecraft:item", "modifier", arr(obj("type", "minecraft:set_count", "condition",
                                obj("type", "minecraft:match_block", "blocks", ac(s + "_slab"), "state", obj("type", "double")), "count", 2),
                                obj("type", "minecraft:explosion_decay")), "name", ac(s + "_slab"))), "rolls", 1)),
                        "random_sequence", ac("blocks/" + s + "_slab")));
            }
            if (WITH_WALLS.contains(d.id())) {
                selfDrop(stem(d.id()) + "_wall");
            }
        }
        for (String chamber : NEW_CHAMBERS) {
            selfDrop(chamber);
        }
        // Block tags vanilla uses for these shapes.
        List<Object> stairs = new ArrayList<>();
        List<Object> slabs = new ArrayList<>();
        List<Object> walls = new ArrayList<>();
        for (String id : WITH_STAIRS) {
            stairs.add(ac(stem(id) + "_stairs"));
            slabs.add(ac(stem(id) + "_slab"));
        }
        for (String id : WITH_WALLS) {
            walls.add(ac(stem(id) + "_wall"));
        }
        for (String kind : new String[] {"block", "item"}) {
            write(RES.resolve("data/minecraft/tags/" + kind + "/stairs.json"), obj("values", stairs));
            write(RES.resolve("data/minecraft/tags/" + kind + "/slabs.json"), obj("values", slabs));
            write(RES.resolve("data/minecraft/tags/" + kind + "/walls.json"), obj("values", walls));
            write(RES.resolve("data/minecraft/tags/" + kind + "/doors.json"), obj("values", arr(ac("nest_door"))));
            write(RES.resolve("data/minecraft/tags/" + kind + "/trapdoors.json"), obj("values", arr(ac("nest_trapdoor"))));
        }
        write(RES.resolve("data/minecraft/tags/block/impermeable.json"), obj("values", arr(ac("amber_glass"), ac("terrarium_glass"))));
        write(RES.resolve("data/minecraft/tags/block/mineable/hoe.json"), obj("values", new ArrayList<>(decorTools().entrySet().stream()
                .filter(e -> e.getValue().equals("hoe")).map(e -> (Object) ac(e.getKey())).toList())));

        // --- Recipes: building blocks ---
        shapeless("anthill_soil", ac("anthill_soil"), 4, "coarse_dirt", "coarse_dirt", "packed_mud", "packed_mud");
        shapeless("packed_anthill_soil", ac("packed_anthill_soil"), 1, ac("anthill_soil"), "wheat");
        shaped("anthill_bricks", ac("anthill_bricks"), 4, new String[] {"PP", "PP"}, "P", ac("packed_anthill_soil"));
        shaped("polished_anthill_bricks", ac("polished_anthill_bricks"), 4, new String[] {"BB", "BB"}, "B", ac("anthill_bricks"));
        shaped("nest_pillar", ac("nest_pillar"), 2, new String[] {"B", "B"}, "B", ac("anthill_bricks"));
        shaped("chitin_block", ac("chitin_block"), 1, new String[] {"CC", "CC"}, "C", ac("chitin_plate"));
        shaped("polished_chitin", ac("polished_chitin"), 4, new String[] {"CC", "CC"}, "C", ac("chitin_block"));
        shaped("chitin_bricks", ac("chitin_bricks"), 4, new String[] {"CC", "CC"}, "C", ac("polished_chitin"));
        shaped("chitin_tiles", ac("chitin_tiles"), 4, new String[] {"CC", "CC"}, "C", ac("chitin_bricks"));
        shaped("amber_block", ac("amber_block"), 1, new String[] {"RR", "RR"}, "R", ac("ant_resin"));
        shaped("amber_bricks", ac("amber_bricks"), 4, new String[] {"AA", "AA"}, "A", ac("amber_block"));
        shaped("amber_glass", ac("amber_glass"), 4, new String[] {" G ", "GRG", " G "}, "G", "glass", "R", ac("ant_resin"));
        shaped("reinforced_amber", ac("reinforced_amber"), 4, new String[] {"HAH", "A A", "HAH"}, "H", ac("hardened_chitin"), "A", ac("amber_block"));
        shapeless("fungal_block", ac("fungal_block"), 2, "brown_mushroom", "red_mushroom", "#minecraft:leaves", "#minecraft:leaves");
        shaped("fungal_bricks", ac("fungal_bricks"), 4, new String[] {"FF", "FF"}, "F", ac("fungal_block"));
        shapeless("glowing_fungal_block", ac("glowing_fungal_block"), 1, ac("fungal_block"), "glow_berries", "glowstone_dust");
        shaped("royal_chitin_block", ac("royal_chitin_block"), 4, new String[] {"CGC", "GJG", "CGC"}, "C", ac("chitin_block"), "G", "gold_nugget",
                "J", ac("royal_jelly"));
        shaped("royal_chitin_bricks", ac("royal_chitin_bricks"), 4, new String[] {"RR", "RR"}, "R", ac("royal_chitin_block"));
        shaped("royal_chitin_pillar", ac("royal_chitin_pillar"), 2, new String[] {"R", "R"}, "R", ac("royal_chitin_block"));
        shaped("chiseled_royal_chitin", ac("chiseled_royal_chitin"), 1, new String[] {"S", "S"}, "S", ac("royal_chitin_brick_slab"));
        shaped("nest_door", ac("nest_door"), 3, new String[] {"CC", "PP", "CC"}, "C", ac("chitin_plate"), "P", ac("packed_anthill_soil"));
        shaped("nest_trapdoor", ac("nest_trapdoor"), 2, new String[] {"CPC", "PCP"}, "C", ac("chitin_plate"), "P", ac("packed_anthill_soil"));
        shaped("pheromone_lamp", ac("pheromone_lamp"), 1, new String[] {" C ", "RGR", " C "}, "C", ac("chitin_plate"), "R", ac("ant_resin"), "G", "glowstone");
        shapeless("terrarium_glass", ac("terrarium_glass"), 4, "glass", "glass", "glass", "glass", "#minecraft:planks");
        for (String id : WITH_STAIRS) {
            String s = stem(id);
            shaped(s + "_stairs", ac(s + "_stairs"), 4, new String[] {"B  ", "BB ", "BBB"}, "B", ac(id));
            shaped(s + "_slab", ac(s + "_slab"), 6, new String[] {"BBB"}, "B", ac(id));
            stonecutting(s + "_stairs_from_stonecutting", ac(id), ac(s + "_stairs"), 1);
            stonecutting(s + "_slab_from_stonecutting", ac(id), ac(s + "_slab"), 2);
        }
        for (String id : WITH_WALLS) {
            String s = stem(id);
            shaped(s + "_wall", ac(s + "_wall"), 6, new String[] {"BBB", "BBB"}, "B", ac(id));
            stonecutting(s + "_wall_from_stonecutting", ac(id), ac(s + "_wall"), 1);
        }
        // The stonecutter turns a family's plain block into its finer cuts one to one.
        String[][] cuts = {{"packed_anthill_soil", "anthill_bricks"}, {"anthill_bricks", "polished_anthill_bricks"}, {"anthill_bricks", "nest_pillar"},
                {"chitin_block", "polished_chitin"}, {"chitin_block", "chitin_bricks"}, {"chitin_block", "chitin_tiles"}, {"amber_block", "amber_bricks"},
                {"fungal_block", "fungal_bricks"}, {"royal_chitin_block", "royal_chitin_bricks"}, {"royal_chitin_block", "royal_chitin_pillar"},
                {"royal_chitin_block", "chiseled_royal_chitin"}};
        for (String[] cut : cuts) {
            stonecutting(cut[1] + "_from_" + cut[0] + "_stonecutting", ac(cut[0]), ac(cut[1]), 1);
        }

        // --- Recipes: the new chambers ---
        // The Nether and End Foraging Sites need that dimension's trail pheromone and its Chitin.
        shaped("nether_foraging_site", ac("nether_foraging_site"), 1, new String[] {"OPO", "IFI", "OPO"}, "O", "obsidian",
                "P", ac("nether_trail_pheromone"), "I", ac("infernal_chitin"), "F", ac("foraging_site"));
        shaped("end_foraging_site", ac("end_foraging_site"), 1, new String[] {"EPE", "VFV", "EYE"}, "E", "end_stone_bricks",
                "P", ac("end_trail_pheromone"), "V", ac("void_chitin"), "F", ac("foraging_site"), "Y", "ender_eye");
        // The Colony Connector takes a Nether Trail Pheromone, so it needs a colony that has been to the Nether.
        shaped("colony_connector", ac("colony_connector"), 1, new String[] {"IPI", "BTB", "IMI"}, "I", ac("infernal_chitin"),
                "P", ac("nether_trail_pheromone"), "B", "blaze_rod", "M", "magma_block", "T", ac("test_tube"));
    }

    /**
     * Weights of the nuggets Vibranium and Unobtainium Ants bring from expeditions, among about 120 and 60 of other finds.
     * Set by the yield the user asked for: about 4 and 3 nuggets an hour from each of three full sites (256 ants), as
     * OreModGameTests measures it a minute at a time. One long step caps a site at 256 finds and measures nothing.
     */
    static final double VIBRANIUM_NUGGETS = 0.26;
    static final double UNOBTAINIUM_NUGGETS = 0.12;
    /**
     * The chance with each find that ants adapted to the Nether or the End turn up its trail pheromone in another
     * dimension: very small, a pheromone in an hour or two from one full site, where a colony living there finds 40.
     */
    static final double TRAIL_AWAY = 0.0003;
    /** The weight of each of the three froglights on a Nether expedition, a third of a ghast tear's. */
    static final double FROGLIGHTS = 0.2;

    /** The Nether and End expedition tables, the species that can go on them, and the Foraging Site modes' mineral tag. */
    static void expeditions() {
        write(DATA.resolve("tags/antcolonies/species/nether_adapted.json"), obj("values", arr(ac("warped"), ac("fire"), ac("inferno"), ac("soul"),
                ac("netherite"), ac("wither"), ac("crimson"), obj("id", ac("vibranium"), "required", false))));
        write(DATA.resolve("tags/antcolonies/species/end_adapted.json"), obj("values", arr(ac("chorus"), ac("ender"), ac("dragon"),
                obj("id", ac("unobtainium"), "required", false))));
        List<Object> dwellers = new ArrayList<>();
        NEST_DWELLERS.keySet().stream().sorted().forEach(id -> dwellers.add(obj("id", ac(id), "required", false)));
        write(DATA.resolve("tags/antcolonies/species/nest_dwellers.json"), obj("values", dwellers));
        List<Object> nether = arr(
                Out.of("quartz", 20).json(), Out.of("glowstone_dust", 15).json(), Out.of("nether_wart", 14).json(), Out.of("blackstone", 10).json(),
                Out.of("soul_sand", 10).json(), Out.of("basalt", 8).json(), Out.of("magma_cream", 8).json(), Out.of("soul_soil", 6).json(),
                Out.of("crimson_fungus", 5).json(), Out.of("warped_fungus", 5).json(), Out.of("blaze_powder", 6).atStage(2).json(),
                Out.of("shroomlight", 3).atStage(2).json(), Out.of("gold_nugget", 6).withCount(2).atStage(2).json(),
                Out.of("blaze_rod", 1.5).atStage(3).json(), Out.of("gilded_blackstone", 0.6).atStage(3).asSpecial().json(),
                Out.of("ghast_tear", 0.6).atStage(3).asSpecial().json(), Out.of(ac("infernal_chitin"), 0.8).atStage(3).asSpecial().json(),
                // Froglights: what a frog makes of a magma cube. Rare, one colour as likely as another.
                Out.of("ochre_froglight", FROGLIGHTS).atStage(3).asSpecial().json(), Out.of("verdant_froglight", FROGLIGHTS).atStage(3).asSpecial().json(),
                Out.of("pearlescent_froglight", FROGLIGHTS).atStage(3).asSpecial().json());
        Map<String, Object> debris = Out.of("ancient_debris", 0.08).atStage(4).asSpecial().json();
        debris.put("species", arr(ac("netherite"), ac("wither")));
        nether.add(debris);
        Map<String, Object> rose = Out.of("wither_rose", 0.3).atStage(3).asSpecial().json();
        rose.put("species", arr(ac("wither"), ac("soul")));
        nether.add(rose);
        // Allthemodium's Nether metal, for its own ants only (a tag: nothing while that mod is away).
        Map<String, Object> vibranium = Out.of("c:nuggets/vibranium", VIBRANIUM_NUGGETS).atStage(3).asSpecial().asTag().json();
        vibranium.put("species", arr(ac("vibranium")));
        nether.add(vibranium);
        write(DATA.resolve("antcolonies/expedition/nether.json"), obj("site", "nether_foraging_site", "adapted", ac("nether_adapted"), "rate", 0.8,
                "home", "minecraft:the_nether", "trail", obj("item", ac("nether_trail_pheromone"), "chance", 0.02, "away_chance", TRAIL_AWAY), "outputs", nether));
        List<Object> end = arr(
                Out.of("end_stone", 25).json(), Out.of("chorus_fruit", 18).json(), Out.of("obsidian", 4).json(),
                Out.of("purpur_block", 6).atStage(2).json(), Out.of("ender_pearl", 5).atStage(2).json(), Out.of("end_rod", 2).atStage(3).json(),
                Out.of("chorus_flower", 1).atStage(2).asSpecial().json(), Out.of("shulker_shell", 0.4).atStage(3).asSpecial().json(),
                Out.of(ac("void_chitin"), 0.6).atStage(3).asSpecial().json());
        Map<String, Object> breath = Out.of("dragon_breath", 0.15).atStage(4).asSpecial().json();
        breath.put("species", arr(ac("dragon")));
        end.add(breath);
        Map<String, Object> unobtainium = Out.of("c:nuggets/unobtainium", UNOBTAINIUM_NUGGETS).atStage(3).asSpecial().asTag().json();
        unobtainium.put("species", arr(ac("unobtainium")));
        end.add(unobtainium);
        write(DATA.resolve("antcolonies/expedition/end.json"), obj("site", "end_foraging_site", "adapted", ac("end_adapted"), "rate", 0.7,
                "home", "minecraft:the_end", "trail", obj("item", ac("end_trail_pheromone"), "chance", 0.02, "away_chance", TRAIL_AWAY), "outputs", end));
        // What Mineral Gathering looks for; everything else counts as organic.
        List<Object> mineral = arr("#c:ores", "#c:raw_materials", "#c:ingots", "#c:nuggets", "#c:gems", "#c:dusts", "#minecraft:coals",
                obj("id", "#c:stones", "required", false), obj("id", "#c:cobblestones", "required", false));
        for (String item : new String[] {"flint", "clay_ball", "gravel", "sand", "red_sand", "netherrack", "quartz", "amethyst_shard", "obsidian", "calcite",
                "tuff", "deepslate", "cobbled_deepslate", "dripstone_block", "pointed_dripstone", "magma_block", "end_stone", "prismarine_shard",
                "prismarine_crystals", "echo_shard", "glowstone_dust", "redstone", "lapis_lazuli", "ancient_debris", "netherite_scrap", "gilded_blackstone",
                "blackstone", "basalt", "stone", "cobblestone", "granite", "diorite", "andesite", "iron_nugget", "gold_nugget", "raw_iron", "raw_gold",
                "raw_copper", "coal", "diamond", "emerald", "amethyst_cluster", "budding_amethyst", "terracotta", "red_sandstone", "sandstone",
                "crying_obsidian", "purpur_block"}) {
            mineral.add(mc(item));
        }
        for (String item : new String[] {"diamond_fragment", "emerald_fragment"}) {
            mineral.add(ac(item));
        }
        // Minerals of other mods that a species names outright, and the Sulfur Ant's rare find (MachinaOpus's petroleum coke).
        if (NEEDS_ITEMS.isEmpty()) {
            throw new IllegalStateException("The species must be written before the foraging tags");
        }
        NEEDS_ITEMS.values().forEach(items -> items.forEach(item -> mineral.add(obj("id", item, "required", false))));
        mineral.add("#" + ac("foraging/sulfur_vents"));
        write(DATA.resolve("tags/item/foraging/mineral.json"), obj("values", mineral));
        LANG.put("tag.item." + NS + ".foraging.mineral", "Mineral Finds (Foraging Site Mineral Mode)");
        write(DATA.resolve("tags/item/foraging/sulfur_vents.json"), obj("values", arr(obj("id", "machinaopus:petroleum_coke", "required", false))));
        LANG.put("tag.item." + NS + ".foraging.sulfur_vents", "Rare Finds of Sulfur Ants");
    }

    /** Models and blockstates of the Nether and End Foraging Sites and the Colony Connector. */
    static void chamberAssets() {
        String wood = ac("block/terrarium_wood");
        double y = FLOOR;
        // The little portals: a frame on the floor near the front glass, its pane swirling while ants are out on expeditions.
        String[][] sites = {{"nether_foraging_site", "minecraft:block/obsidian", "minecraft:block/nether_portal", "minecraft:block/netherrack",
                "minecraft:block/soul_soil", "minecraft:block/crying_obsidian"},
                {"end_foraging_site", "minecraft:block/end_stone_bricks", ac("block/end_rift"), "minecraft:block/end_stone", "minecraft:block/chorus_plant",
                        "minecraft:block/end_portal_frame_eye"}};
        for (String[] site : sites) {
            String id = site[0];
            String top = ac("block/" + id + "_top");
            model(id, obj("particle", top, "side", wood, "top", top, "frame", site[1], "lump", site[3], "lump2", site[4], "cap", site[5]), arr(
                    floorBody(), soilCore(),
                    box(p(4, y, 3), p(5, 13, 5), "#frame", "#frame"), box(p(11, y, 3), p(12, 13, 5), "#frame", "#frame"),
                    box(p(4, 12, 3), p(12, 13, 5), "#frame", "#frame"), box(p(5, y, 3), p(11, y + 1, 5), "#frame", "#frame"),
                    box(p(7, 13, 3.5), p(9, 14, 4.5), "#cap", "#cap"),
                    box(p(2, y, 10), p(5, y + 1.5, 13), "#lump", "#lump"), box(p(11, y, 11), p(13, y + 1, 13), "#lump2", "#lump2")));
            for (String state : new String[] {"active", "dormant"}) {
                String pane = state.equals("active") ? site[2] : site[1];
                model(id + "_" + state, obj("particle", pane, "pane", pane), arr(obj("from", arr(5, y + 1, 3.5), "to", arr(11, 12, 4.5), "faces", obj(
                        "north", obj("uv", arr(5, 4, 11, 11), "texture", "#pane"), "south", obj("uv", arr(5, 4, 11, 11), "texture", "#pane")),
                        "light_emission", state.equals("active") ? 12 : null)));
            }
            List<Object> extra = new ArrayList<>();
            int[] rotations = {0, 90, 180, 270};
            for (int f = 0; f < 4; f++) {
                for (String lit : new String[] {"true", "false"}) {
                    extra.add(obj("when", obj("facing", HORIZONTAL[f], "lit", lit, "upright", "false"),
                            "apply", obj("model", ac("block/" + id + (lit.equals("true") ? "_active" : "_dormant")),
                                    "y", rotations[f] == 0 ? null : rotations[f])));
                }
            }
            colonyPart(id, extra);
        }
        // The Colony Connector: a tunnel mouth (in its floor texture) and a Chitin post with a glowing pheromone gland on top.
        String top = ac("block/colony_connector_top");
        model("colony_connector", obj("particle", top, "side", wood, "top", top, "post", ac("block/chitin_block"),
                "gland", ac("block/pheromone_lamp"), "soil", ac("block/anthill_soil")), arr(
                floorBody(), soilCore(),
                box(p(12, y, 2), p(13, 11, 3), "#post", "#post"),
                obj("from", arr(11, 11, 1), "to", arr(14, 14, 4), "light_emission", 10, "faces", obj("down", face("#gland"), "up", face("#gland"),
                        "north", face("#gland"), "south", face("#gland"), "west", face("#gland"), "east", face("#gland"))),
                box(p(4, y, 9), p(6, y + 1, 11), "#soil", "#soil"), box(p(10, y, 10), p(11, y + 1, 11), "#soil", "#soil"),
                box(p(9, y, 4), p(10, y + 1, 5), "#soil", "#soil")));
        colonyPart("colony_connector");
        for (String id : NEW_CHAMBERS) {
            Map<String, Object> item = framedItem(id);
            if (!id.equals("colony_connector")) {
                // The item shows its portal lit.
                @SuppressWarnings("unchecked")
                List<Object> models = (List<Object>) item.get("models");
                models.add(1, obj("type", "minecraft:model", "model", ac("block/" + id + "_active")));
            }
            write(ASSETS.resolve("items/" + id + ".json"), obj("model", item));
        }
    }

    static void updateLang() {
        Map<String, String> l = LANG;
        for (Decor d : DECOR) {
            l.put("block." + NS + "." + d.id(), d.name());
        }
        for (String id : WITH_STAIRS) {
            String name = title(stem(id));
            l.put("block." + NS + "." + stem(id) + "_stairs", name + " Stairs");
            l.put("block." + NS + "." + stem(id) + "_slab", name + " Slab");
        }
        for (String id : WITH_WALLS) {
            l.put("block." + NS + "." + stem(id) + "_wall", title(stem(id)) + " Wall");
        }
        l.put("block." + NS + ".nether_foraging_site", "Nether Foraging Site");
        l.put("block." + NS + ".end_foraging_site", "End Foraging Site");
        l.put("block." + NS + ".colony_connector", "Colony Connector");
        l.put("tooltip." + NS + ".nether_foraging_site",
                "Nether-adapted ants (Warped, Fire, Inferno, Soul, Netherite, Wither, Crimson) go on expeditions "
                + "through its little portal and bring back the Nether's finds. Other ants leave it alone. No power needed.");
        l.put("tooltip." + NS + ".end_foraging_site",
                "End-adapted ants (Chorus, Ender, Dragon) go on expeditions through its little portal and bring back "
                + "the End's finds. Other ants leave it alone. No power needed.");
        l.put("tooltip." + NS + ".colony_connector", "Links its colony to the other colonies with a connector on the same channel: food moves from "
                + "colonies with food to spare to those that ask for it, and the items its filter names move between their Storage.");
        l.put("item." + NS + ".nether_trail_pheromone", "Nether Trail Pheromone");
        l.put("item." + NS + ".end_trail_pheromone", "End Trail Pheromone");
        l.put("item." + NS + ".ant_resin", "Ant Resin");
        l.put("tooltip." + NS + ".nether_trail_pheromone", "The scent of a route through the Nether. A colony living in the Nether finds it now and "
                + "then while foraging. Makes the Nether Foraging Site.");
        l.put("tooltip." + NS + ".end_trail_pheromone",
                "The scent of a route through the End. A colony living in the End finds it now and then while "
                + "foraging. Makes the End Foraging Site.");
        l.put("tooltip." + NS + ".ant_resin", "Tree resin that Wood Ants gather. Hardens into Amber building blocks.");
    }


    /** The text of the update's screens: Colony GUI 2.0, the chamber settings, the Colony Connector. After lang(), so it wins. */
    static void uiLang() {
        Map<String, String> l = LANG;
        // The book's how-to-play pages for the update.
        String[][] bookPages = {
                {"gear", "Chitin Gear", String.join("\n",
                        "Chitin tools and armor come in four tiers. Chitin (from Chitin Plates) is iron level; Hardened is crafted the same way from Hardened Chitin and lasts three times as long.",
                        "Infernal: at a smithing table, a Hardened piece with an Infernal Reinforcement and an Infernal Chitin. Diamond level and fireproof. Void: an Infernal piece with a Void Reinforcement and a netherite ingot. Past netherite.",
                        "Reinforcements are crafted two at a time: Infernal from Infernal Chitin, blaze rods, magma cream and a ghast tear; Void from Void Chitin, shulker shells, popped chorus fruit and an eye of ender.",
                        "Any four chitin armor pieces are a full set: wild ants leave you alone, until you raid their anthill. The lowest tier you wear sets the rest: digging 20, 25, 30 or 40% faster, and from Infernal up Fire Resistance.")},
                {"founding", "Founding a Colony", String.join("\n",
                        "Every anthill has a Queen to catch. Put her into a completely empty Formicarium on her own and she founds a colony: fill the food bar (ten Ant Food) and she lays her first eggs. When her first 10 workers are grown, the colony is founded.",
                        "Put her in with workers, eggs or larvae instead (or move a colony across in its tubes) and the colony runs at once: founding is only for a Queen alone.",
                        "Food keeps a colony working: with less than 10 minutes of food left it slows down, and with none it starves. Farmer Ants at Harvesting Sites grow food, and Colony Connectors bring it from other colonies.")},
                {"expeditions", "Expeditions", String.join("\n",
                        "Take a colony to the Nether (or the End) and let it forage there: now and then its Foraging Sites find a Nether (or End) Trail Pheromone.",
                        "Back home, build a Nether Foraging Site (obsidian, Infernal Chitin, a Foraging Site and two Nether Trail Pheromones) or an End Foraging Site (end stone bricks, Void Chitin, an eye of ender, a Foraging Site and two End Trail Pheromones).",
                        "Nether-adapted ants (Warped, Fire, Inferno, Soul, Netherite, Wither, Crimson) and End-adapted ants (Chorus, Ender, Dragon) then go on expeditions through its little portal and bring back that dimension's finds. Nothing really travels: no power and no chunk loading.")},
                {"connectors", "Colony Connectors", String.join("\n",
                        "Connectors come in the mid game, after the Nether: one takes Infernal Chitin, a Nether Trail Pheromone, Blaze Rods and a Magma Block.",
                        "A Colony Connector joins a colony like any chamber. Connectors with the same channel colour in one dimension link their colonies when they are at most 30 blocks apart (on x and on z), as long as both are loaded.",
                        "Supply gives the food a colony has above its reserve; Request takes food up to its target, the hungriest colony first. Send moves the items in its filter out of the colony's Storage to colonies that Receive.",
                        "A Farmer colony supplying food lets mining and hunting colonies put more ants to work. The Formicarium's Network page shows the network.")},
                {"building", "Anthills and Building", String.join("\n",
                        "No two anthills are dug alike: each nest grows room by room, small, medium or large, with nurseries, food stores, fungus gardens, aphid farms, middens to brush, barracks and treasure chambers, side entrances, loops and dead ends. Their look follows the biome: termite spires in savannas and badlands, a crater in the desert, a thatched dome in the taiga, snow, moss or mud elsewhere. The Nether and the End have anthills too.",
                        "Deepest of all, behind her Royal Guard Ants, lies the great Queen. In a full Chitin set the ants let you pass, until you open one of their chests, step into her chamber or strike one of them: then the whole anthill attacks.",
                        "Its Ant Egg Clusters become Larva Clusters, and those, in time, 1 to 3 more of its ants. Taken with Silk Touch, a cluster goes into the Formicarium of a working colony of the same species; placed again, it is only a decoration.",
                        "Their walls are Anthill Soil. From it, Chitin Plates, Ant Resin (Wood Ants find it), mushrooms and Royal Jelly come five families of building blocks, with stairs, slabs and walls, a Nest Door and a Pheromone Lamp.")}};
        for (String[] page : bookPages) {
            l.put("guide." + NS + "." + page[0] + ".title", page[1]);
            l.put("guide." + NS + "." + page[0] + ".text", page[2]);
        }
        String g = "gui." + NS + ".";
        // Tabs.
        String[][] pages = {{"overview", "Overview", "The colony at a glance: its Queen, health, food, ants and problems"},
                {"colony", "Colony", "The Queen, eggs, larvae, workers and specialists; click a card for more"},
                {"jobs", "Jobs", "Its working sites: ants, priority, on or off, and what Foraging Sites look for"},
                {"production", "Production", "What it finds, how fast, and what its Storage holds"},
                {"genetics", "Genetics", "The Queen's traits and mutations: green helps, red hurts"},
                {"network", "Network", "The colonies linked to it by Colony Connectors, and the food moving between them"},
                {"statistics", "Statistics", "Charts of the last half hour, from the colony's own numbers"},
                {"species", "Info", "Its species: climate, diet, finds and evolutions"}};
        for (String[] p : pages) {
            l.put(g + "page." + p[0], p[1]);
            l.put(g + "page." + p[0] + ".hint", p[2]);
        }
        l.put(g + "view", "Screen mode");
        l.put(g + "view.visual", "Visual");
        l.put(g + "view.detailed", "Detail");
        l.put(g + "view.visual.hint", "Visual Mode: pictures, bars and short notes");
        l.put(g + "view.detailed.hint", "Detailed Mode: exact numbers, rates per minute and full statistics");
        l.put(g + "view.click", "Click to switch (remembered)");
        // Overview.
        l.put(g + "food_balance", "Food: +%s, -%s");
        l.put(g + "efficiency", "work %s");
        l.put(g + "founding", "Founding");
        l.put(g + "founding.feed", "fill her food bar");
        l.put(g + "founding.brood", "her first workers");
        l.put(g + "stage.colony", "Colony stage %s");
        l.put(g + "stage.queen", "Queen stage %s");
        l.put(g + "queen_growing", "Queen stage %s in %s");
        l.put(g + "queen_growing.title", "The colony is ahead of its Queen: she grows into its stage");
        l.put(g + "queen_growing.hint", "Each stage raises %s of her traits by one, up to level %s.");
        l.put(g + "queen_growing.pause", "The time stops while the colony is smaller.");
        // Statuses: the new ones, and short forms for Visual Mode's chips.
        l.put("status." + NS + ".founding", "Queen Founding: a single Queen starting a colony");
        l.put("status." + NS + ".low_food", "Food running low: the colony slows down");
        l.put("status." + NS + ".nothing_to_focus", "Nothing of that kind to look for: gathering as Balanced");
        l.put("status." + NS + ".disabled", "Turned off");
        l.put("status." + NS + ".no_soldiers", "No soldiers");
        String[][] shorts = {{"no_queen", "No Queen"}, {"no_food", "No food"}, {"no_workers", "No workers"}, {"no_soldiers", "No soldiers"},
                {"missing_connection", "Not joined"}, {"storage_full", "Storage full"}, {"incompatible_species", "Wrong ants"},
                {"insufficient_materials", "Not ready"}, {"two_colonies", "Two colonies"}, {"unknown_species", "Unknown species"},
                {"no_storage", "No Storage"}, {"no_special_food", "Special food"}, {"climate", "Wrong climate"}, {"low_health", "Low health"},
                {"no_nest_chamber", "No Nest Chamber"}, {"population_full", "Nest full"}, {"starving", "Starving!"}, {"working", "Working"},
                {"paused", "Paused"}, {"wrong_dimension", "Wrong dimension"}, {"founding", "Founding"}, {"low_food", "Low food"},
                {"nothing_to_focus", "Nothing to focus"}, {"disabled", "Off"},
                {"needs_laboratory", "Lab mutation"}, {"not_researched", "Not researched"}, {"queen_not_grown", "Queen too young"}};
        for (String[] s : shorts) {
            l.put("status." + NS + "." + s[0] + ".short", s[1]);
        }
        // Colony cards.
        l.put(g + "card.queen", "Queen");
        l.put(g + "card.eggs", "Eggs");
        l.put(g + "card.larvae", "Larvae");
        l.put(g + "card.workers", "Workers");
        l.put(g + "card.specialists", "Jobs");
        l.put(g + "card.click", "Click for details");
        l.put(g + "card.queen.none", "Put a Queen Test Tube in the Queen slot.");
        l.put(g + "card.queen.laying", "Lays %s eggs");
        l.put(g + "card.queen.best", "Best: %s");
        l.put(g + "card.queen.founded", "Founded this colony on her own");
        l.put(g + "card.eggs.laid", "Laid: %s");
        l.put(g + "card.eggs.hatch", "Hatch in about %s min");
        l.put(g + "card.eggs.total", "Laid in all: %s");
        l.put(g + "card.larvae.raised", "Grown into adults: %s");
        l.put(g + "card.larvae.food", "Larvae eat; Nest Chambers raise them faster");
        l.put(g + "card.workers.split", "%s at jobs, %s at home, %s idle");
        l.put(g + "card.workers.deaths", "Die of age or hunger: %s");
        l.put(g + "card.specialists.list", "Ants at work, by job:");
        l.put(g + "card.job", "%s: %s");
        l.put(g + "card.hunters", "A hunter species: it works Hunting Grounds");
        l.put(g + "card.farmers", "A farming species: it grows food at Harvesting Sites");
        // Jobs and the chambers' settings.
        l.put(g + "mode", "What it looks for");
        l.put(g + "mode.click", "Click for the next mode");
        String[][] modes = {{"balanced", "Balanced", "Bal", "Every find at its usual share"},
                {"mineral", "Mineral Gathering", "Min", "Ores, metals, gems, dusts and stone come three times as often, the rest far less"},
                {"organic", "Organic Gathering", "Org", "Plants, seeds, shells, bones and the like come three times as often, minerals far less"}};
        for (String[] m : modes) {
            l.put(g + "mode." + m[0], m[1]);
            l.put(g + "mode." + m[0] + ".short", m[2]);
            l.put(g + "mode." + m[0] + ".hint", m[3]);
        }
        l.put(g + "priority", "Priority");
        l.put(g + "priority.0", "Low priority");
        l.put(g + "priority.1", "Normal priority");
        l.put(g + "priority.2", "High priority");
        l.put(g + "priority.short.0", "Low");
        l.put(g + "priority.short.1", "Norm");
        l.put(g + "priority.short.2", "High");
        l.put(g + "priority.hint", "Sites of higher priority get their ants first. Click to change.");
        l.put(g + "site.on", "On");
        l.put(g + "site.off", "Off");
        l.put(g + "site.toggle.hint", "Turn the site on or off: off, it takes no ants and they work elsewhere");
        l.put(g + "expedition.needs.nether_foraging_site", "Needs Nether-adapted ants: %s");
        l.put(g + "expedition.needs.end_foraging_site", "Needs End-adapted ants: %s");
        // Production.
        l.put(g + "production.storage", "Storage");
        l.put(g + "production.no_storage", "No Storage: finds wait in the sites");
        l.put(g + "production.none", "No finds yet: put workers at a Foraging Site.");
        l.put(g + "production.col.item", "Item");
        l.put(g + "production.col.stored", "Stored");
        l.put(g + "production.stored", "Stored: %s");
        l.put(g + "production.rate", "Found: about %s a minute");
        l.put(g + "production.rate_measured", "Brought in: %s a minute (last %s min)");
        l.put(g + "production.window", "%s min");
        l.put(g + "production.window.hint", "Items per minute: the average of what the ants really brought in over the last %s minutes (up to 30). It stays when the Storage is emptied.");
        l.put(g + "production.estimate", "est.");
        l.put(g + "production.estimate.hint", "Estimated from the sites' rates until the ants have brought something in.");
        // Network.
        l.put(g + "network.none", "No colonies linked yet. Put a Colony Connector against this colony, pick a channel colour, and do the same at "
                + "another colony up to 30 blocks away: food (and filtered items) then move between them.");
        l.put(g + "network.col.colony", "Colony");
        l.put(g + "network.col.food", "Food");
        l.put(g + "network.col.mode", "Mode");
        l.put(g + "network.col.flow", "In / out");
        l.put(g + "network.self.short", "Here");
        l.put(g + "network.self", "This colony");
        l.put(g + "network.offline", "Unloaded");
        l.put(g + "network.count", "%s other colonies");
        l.put(g + "network.channel", "Channel: %s");
        l.put(g + "network.food", "Food: %s / %s");
        l.put(g + "network.keeps", "Keeps %s for itself");
        l.put(g + "network.asks", "Asks for food up to %s");
        l.put(g + "network.flow", "In %s / out %s food a minute");
        // Statistics.
        String[][] series = {{"adults", "Adults", "Adults"}, {"brood", "Eggs and larvae", "Brood"}, {"food", "Food stored", "Food"},
                {"eggs_laid", "Eggs laid per minute", "Eggs"}, {"finds", "Finds per minute", "Finds"}, {"food_in", "Food in per minute", "In"},
                {"food_out", "Food eaten per minute", "Out"}, {"born", "Ants born per minute", "Born"}, {"died", "Ants died per minute", "Died"}};
        for (String[] s : series) {
            l.put(g + "series." + s[0], s[1]);
            l.put(g + "series." + s[0] + ".short", s[2]);
        }
        // The Statistics page's charts (a pair draws two series): the button's name and its hover.
        String[][] views = {{"adults", "Adults", "Adults"}, {"brood", "Eggs and larvae", "Brood"}, {"food", "Food stored", "Food"},
                {"eggs_laid", "Eggs laid per minute", "Eggs"}, {"finds", "Finds per minute", "Finds"},
                {"food_flow", "Food in and out per minute", "In/Out"}, {"born_died", "Ants born and died per minute", "Births"}};
        for (String[] v : views) {
            l.put(g + "stats.view." + v[0], v[1]);
            l.put(g + "stats.view." + v[0] + ".short", v[2]);
        }
        l.put(g + "stats.minutes", "%s min");
        l.put(g + "stats.low_high", "Low %s, high %s");
        l.put(g + "stats.average_only", "Average %s");
        l.put(g + "stats.average_total", "%s avg, %s total");
        l.put(g + "stats.net", "Net %s");
        // Where a Working Sites row's site is, from the Formicarium.
        for (String[] d : new String[][] {{"east", "%s E"}, {"west", "%s W"}, {"north", "%s N"}, {"south", "%s S"}, {"up", "%s up"}, {"down", "%s down"}}) {
            l.put(g + "site." + d[0], d[1]);
        }
        l.put(g + "site.place", "%s from the Formicarium (%s)");
        l.put(g + "site.tile.hint", "Click to open it in Detailed view");
        l.put(g + "stats.collecting", "Collecting: a sample every %s seconds while the colony runs.");
        l.put(g + "stats.span", "Last %s minutes");
        l.put(g + "stats.ago", "%s minutes ago");
        l.put(g + "stats.range", "From %s to %s");
        l.put(g + "stats.average", "Average %s, now %s");
        l.put(g + "trait.level", "Level %s: %s");
        // The Colony Connector's screen.
        l.put(g + "connector.channel", "Channel");
        l.put(g + "connector.food", "Food");
        l.put(g + "connector.food.off", "Off");
        l.put(g + "connector.food.supply", "Supply");
        l.put(g + "connector.food.request", "Request");
        l.put(g + "connector.food.off.hint", "This colony neither gives nor takes food");
        l.put(g + "connector.food.supply.hint", "Gives the food it has above its reserve to colonies that ask");
        l.put(g + "connector.food.request.hint", "Takes food from supplying colonies while below its target (the hungriest colony first)");
        l.put(g + "connector.keep", "Keep");
        l.put(g + "connector.until", "Ask until");
        l.put(g + "connector.rate", "%s/min");
        l.put(g + "connector.rate.max", "Max/min");
        l.put(g + "connector.rate.hint", "The most food it moves a minute. Click to change.");
        l.put(g + "connector.items", "Items");
        l.put(g + "connector.items.off", "Off");
        l.put(g + "connector.items.send", "Send");
        l.put(g + "connector.items.receive", "Receive");
        l.put(g + "connector.items.off.hint", "Moves no items");
        l.put(g + "connector.items.send.hint", "The items in its filter leave this colony's Storage for colonies that receive");
        l.put(g + "connector.items.receive.hint", "Items sent on the channel arrive in this colony's Storage");
        l.put(g + "connector.filter", "Send these items:");
        l.put(g + "connector.filter.hint", "Put one of each item to send here (it stays in the connector)");
        l.put(g + "connector.flow", "%s colonies in range; food in %s, out %s; items %s a minute");
        l.put("jei." + NS + ".expedition", "Expeditions");
        l.put("jei." + NS + ".expedition_dimension", "Simulated expeditions into %s (no power, no chunk loading)");
        l.put("jei." + NS + ".trail_home", "Found by colonies in %s");
        l.put("jei." + NS + ".trail_site", "Any ants, at a Foraging Site");
        l.put("jei." + NS + ".trail_away", "Found away from %s too, very rarely");
        l.put("jei." + NS + ".trail_away_site", "Only these ants, at a Foraging Site");
        l.put("jei." + NS + ".trail_chance", "Chance: %s%% with each find");
        l.put("jei." + NS + ".trail_use", "Needed for the %s");
        l.put("jade." + NS + ".expedition.active", "Ants out on expeditions");
        l.put("jade." + NS + ".expedition.idle", "Portal dark: no expedition");
    }
    // --- Liquid Ants, droplets and the Droplet Centrifuge ---

    /**
     * One fluid Liquid Ants make droplets of; the same rows are in TexGen (LIQUIDS), LiquidKinds and compat/liquid.
     * Per droplet: {@code amount} in mB, {@code energy} in FE, {@code time} in ticks. A null {@code mod} is the base game.
     */
    record Liquid(String id, String ant, String mod, String fluid, String fluidName, String from, String feed, String dimension, int tier, String climate,
                  String body, String plates, String crop, boolean glow, double rate, double food, int amount, int energy, int time, String description,
                  List<String[]> others) {
        Liquid(String id, String ant, String mod, String fluid, String fluidName, String from, String feed, String dimension, int tier, String climate,
               String body, String plates, String crop, boolean glow, double rate, double food, int amount, int energy, int time, String description) {
            this(id, ant, mod, fluid, fluidName, from, feed, dimension, tier, climate, body, plates, crop, glow, rate, food, amount, energy, time,
                    description, List.of());
        }

        String droplet() {
            return id + "_droplet";
        }

        /** The same liquid as another mod adds it: used when the mods before it are not installed. */
        Liquid or(String otherMod, String otherFluid, String otherFeed) {
            List<String[]> all = new ArrayList<>(others);
            all.add(new String[] {otherMod, otherFluid, otherFeed});
            return new Liquid(id, ant, mod, fluid, fluidName, from, feed, dimension, tier, climate, body, plates, crop, glow, rate, food, amount, energy,
                    time, description, all);
        }

        /** Each mod that adds the liquid, the preferred one first: mod, fluid, what the Queen is fed. */
        List<String[]> sources() {
            List<String[]> all = new ArrayList<>();
            all.add(new String[] {mod, fluid, feed});
            all.addAll(others);
            return all;
        }
    }

    static final List<Liquid> LIQUIDS = List.of(
            // The base game's two: always there.
            new Liquid("water", "Water Ant", null, "minecraft:water", "Water", "honeypot", "minecraft:water_bucket", null, 1, "temperate",
                    "2E4A6B", "1E3550", "4F8FEA", false, 1.0, 1.3, 100, 400, 40,
                    "Honeypot Ants fed water. Their crops are full of it, always: at a Liquid Chamber they give Water Droplets and nothing else."),
            // Milk has no Liquid Ant: Cow Ants give it (see SIDE_LIQUIDS). Only its droplet and Centrifuge recipe are made here.
            new Liquid("milk", null, null, "minecraft:milk", "Milk", null, "", null, 0, null, null, null, "F4EEDD", false, 0, 0, 50, 600, 50, null),
            new Liquid("lava", "Lava Ant", null, "minecraft:lava", "Lava", "honeypot", "minecraft:lava_bucket", NETHER, 2, "hot",
                    "3A1A10", "5A2410", "FF7A1A", true, 0.1, 1.8, 50, 2000, 100,
                    "Honeypot Ants fed lava in the Nether. Their glowing crops hold lava: slow, hungry makers of Lava Droplets."),
            // MachinaOpus.
            new Liquid("crude_oil", "Oil Ant", "machinaopus", "machinaopus:crude_oil", "Crude Oil", "water", "machinaopus:crude_oil_bucket", null, 2, "hot",
                    "2A2420", "14110E", "2B211A", false, 0.08, 1.6, 50, 2400, 120,
                    "Water Ants fed crude oil. Black-cropped ants that give Crude Oil Droplets: oil with no pump, a little at a time.")
                    .or("pneumaticcraft", "pneumaticcraft:oil", "pneumaticcraft:oil_bucket")
                    .or("modern_industrialization", "modern_industrialization:crude_oil", "modern_industrialization:crude_oil_bucket")
                    .or("oritech", "oritech:still_oil", "oritech:still_oil_bucket").or("ad_astra", "ad_astra:oil", "ad_astra:oil_bucket")
                    .or("stellaris", "stellaris:oil", "stellaris:oil_bucket"),
            new Liquid("liquid_blaze", "Blaze Ant", "machinaopus", "machinaopus:liquid_blaze", "Liquid Blaze", "lava", "machinaopus:liquid_blaze_bucket", null, 2,
                    "hot", "6A3A0A", "8A4A0A", "FFA020", true, 0.04, 2.0, 25, 4000, 160,
                    "Lava Ants fed liquid blaze. Their crops burn gold: Liquid Blaze Droplets for MachinaOpus's Blaze Crucible, slowly."),
            // Mekanism.
            new Liquid("brine", "Brine Ant", "mekanism", "mekanism:brine", "Brine", "water", "mekanism:brine_bucket", null, 2, "hot",
                    "7A7450", "5A5438", "FEEF9C", false, 0.3, 1.4, 50, 800, 60,
                    "Water Ants fed brine. Pale-cropped ants that give Brine Droplets for Mekanism's chemistry."),
            new Liquid("heavy_water", "Heavy Water Ant", "mekanism", "mekanism:heavy_water", "Heavy Water", "water", "mekanism:heavy_water_bucket", null, 2,
                    "temperate", "1E2450", "141838", "2A3AB0", false, 0.05, 1.8, 25, 3200, 140,
                    "Water Ants fed heavy water. Deep blue crops and a slow trickle of Heavy Water Droplets for reactors.")
                    .or("modern_industrialization", "modern_industrialization:heavy_water", "modern_industrialization:heavy_water_bucket"),
            // Create.
            new Liquid("honey", "Honey Ant", "create", "create:honey", "Honey", "honeypot", "create:honey_bucket", null, 2, "temperate",
                    "6A3A12", "4A2808", "E8A126", false, 0.2, 1.4, 50, 800, 60,
                    "Honeypot Ants fed liquid honey. All crop, they give Honey Droplets for pipes, basins and hives.")
                    .or("productivebees", "productivebees:honey", "productivebees:honey_bucket")
                    .or("extended_industrialization", "extended_industrialization:honey", "extended_industrialization:honey_bucket")
                    .or("the_bumblezone", "the_bumblezone:honey_fluid_still", "the_bumblezone:honey_bucket"),
            new Liquid("chocolate", "Chocolate Ant", "create", "create:chocolate", "Chocolate", "water", "create:chocolate_bucket", null, 2, "temperate",
                    "4A2A1E", "2E1810", "8A4A30", false, 0.2, 1.4, 50, 800, 60,
                    "Water Ants fed chocolate. Brown-cropped ants that give Chocolate Droplets for Create's kitchens."),
            new Liquid("tea", "Tea Ant", "create", "create:tea", "Builder's Tea", "water", "create:builders_tea", null, 2, "temperate",
                    "5A3A2A", "3E2618", "CF755A", false, 0.25, 1.4, 50, 800, 60,
                    "Water Ants fed Builder's Tea. They give Builder's Tea Droplets, a fluid Create has no bucket for."),
            // Industrial Foregoing.
            new Liquid("pink_slime", "Pink Slime Ant", "industrialforegoing", "industrialforegoing:pink_slime", "Pink Slime", "water",
                    "industrialforegoing:pink_slime_bucket", null, 2, "temperate", "6A3A62", "4A2644", "D09DCD", false, 0.08, 1.6, 25, 2400, 120,
                    "Water Ants fed pink slime. Their crops wobble pink: Pink Slime Droplets for Industrial Foregoing, slowly."),
            new Liquid("latex", "Latex Ant", "industrialforegoing", "industrialforegoing:latex", "Latex", "water", "industrialforegoing:latex_bucket", null, 2,
                    "temperate", "6A6A66", "4A4A46", "E4E4E4", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed latex. Milky-cropped ants that give Latex Droplets for Industrial Foregoing."),
            // Ender IO.
            new Liquid("nutrient_distillation", "Nutrient Ant", "enderio", "enderio:fluid_nutrient_distillation_still", "Nutrient Distillation", "water",
                    "enderio:nutrient_distillation_bucket", null, 2, "temperate", "3A4010", "2A2E08", "6E7A10", false, 0.1, 1.6, 50, 1600, 100,
                    "Water Ants fed nutrient distillation. Olive crops, and Nutrient Distillation Droplets for Ender IO."),
            new Liquid("hootch", "Hootch Ant", "enderio", "enderio:fluid_hootch_still", "Hootch", "water", "enderio:hootch_bucket", null, 2, "temperate",
                    "5A5040", "3E3628", "E6D9A8", false, 0.08, 1.6, 50, 2000, 100,
                    "Water Ants fed hootch. They hold their drink, and give Hootch Droplets for Ender IO."),
            // Thermal Series.
            new Liquid("resin", "Resin Ant", "thermal", "thermal:resin", "Resin", "water", "thermal:resin_bucket", null, 2, "temperate",
                    "5A3A0A", "3E2806", "B87A10", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed resin. Amber-cropped ants that give Resin Droplets for Thermal's machines."),
            new Liquid("sap", "Sap Ant", "thermal", "thermal:sap", "Sap", "water", "thermal:sap_bucket", null, 2, "temperate",
                    "6A2E08", "4A1E04", "D85A10", false, 0.2, 1.4, 50, 800, 60,
                    "Water Ants fed sap. They give Sap Droplets: tree sap with no tapping.")
                    .or("productivetrees", "productivetrees:maple_sap", "productivetrees:maple_sap_bucket"),
            // Immersive Engineering.
            new Liquid("creosote", "Creosote Ant", "immersiveengineering", "immersiveengineering:creosote", "Creosote Oil", "lava",
                    "immersiveengineering:creosote_bucket", null, 2, "hot", "2E1A0E", "1C0E06", "4A2208", false, 0.15, 1.5, 50, 1200, 80,
                    "Lava Ants fed creosote oil. Tar-dark crops, and Creosote Oil Droplets for treated wood and rails.")
                    .or("railcraft", "railcraft:creosote", "railcraft:creosote_bucket")
                    .or("modern_industrialization", "modern_industrialization:creosote", "modern_industrialization:creosote_bucket"),
            new Liquid("plant_oil", "Plant Oil Ant", "immersiveengineering", "immersiveengineering:plantoil", "Plant Oil", "water",
                    "immersiveengineering:plantoil_bucket", null, 2, "temperate", "4A4A22", "32321A", "A8A450", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed plant oil. They give Plant Oil Droplets: oil pressed from seeds, with no press.")
                    .or("pneumaticcraft", "pneumaticcraft:vegetable_oil", "pneumaticcraft:vegetable_oil_bucket")
                    .or("createaddition", "createaddition:seed_oil", "createaddition:seed_oil_bucket")
                    .or("modern_industrialization", "modern_industrialization:plant_oil", "modern_industrialization:plant_oil_bucket"),
            // --- The fluids of big packs (All the Mods 11) ---
            // Liquid experience: nearly every pack has one. 20 mB is one point of experience in all of them.
            liquid("experience", "Experience Ant", "enderio", "enderio:fluid_xp_juice_still", "Liquid Experience", "water",
                    "enderio:xp_juice_bucket,minecraft:experience_bottle", "temperate", "8CFE16", true, 0.1, 1.8, 20, 2000, 100,
                    "Water Ants fed liquid experience. Their crops glow green: Experience Droplets, a point at a time.")
                    .or("industrialforegoing", "industrialforegoing:essence", "industrialforegoing:essence_bucket")
                    .or("pneumaticcraft", "pneumaticcraft:memory_essence", "pneumaticcraft:memory_essence_bucket")
                    .or("mob_grinding_utils", "mob_grinding_utils:fluid_xp", "mob_grinding_utils:fluid_xp_bucket")
                    .or("create_enchantment_industry", "create_enchantment_industry:experience", "create_enchantment_industry:experience_bucket")
                    .or("justdirethings", "justdirethings:xp_fluid_source", "justdirethings:xp_fluid_bucket")
                    .or("energizedpower", "energizedpower:liquid_xp", "energizedpower:liquid_xp_bucket")
                    .or("neovitae", "neovitae:liquified_experience_source", "neovitae:liquified_experience_bucket")
                    .or("sophisticatedcore", "sophisticatedcore:xp_still", "sophisticatedcore:xp_bucket"),
            // Ender IO's Vat fluids, each from the ant of the fluid the Vat makes it from.
            liquid("fire_water", "Fire Water Ant", "enderio", "enderio:fluid_fire_water_still", "Fire Water", "hootch", "enderio:fire_water_bucket",
                    "hot", "9A4F10", true, 0.06, 1.8, 50, 2400, 120,
                    "Hootch Ants fed fire water. They give Fire Water Droplets, a strong fuel for Ender IO."),
            liquid("liquid_sunshine", "Sunshine Ant", "enderio", "enderio:fluid_liquid_sunshine_still", "Liquid Sunshine", "hootch",
                    "enderio:liquid_sunshine_bucket", "hot", "E6D64A", true, 0.08, 1.6, 50, 2000, 100,
                    "Hootch Ants fed liquid sunshine. Their crops shine: Liquid Sunshine Droplets for Ender IO."),
            liquid("dew_of_the_void", "Void Dew Ant", "enderio", "enderio:fluid_dew_of_the_void_still", "Dew of the Void", "nutrient_distillation",
                    "enderio:dew_of_the_void_bucket", "temperate", "129238", false, 0.06, 1.8, 50, 2400, 120,
                    "Nutrient Ants fed dew of the void. They give Dew of the Void Droplets for Ender IO's crystals."),
            liquid("cloud_seed", "Cloud Seed Ant", "enderio", "enderio:fluid_cloud_seed_still", "Cloud Seed", "water", "enderio:cloud_seed_bucket",
                    "temperate", "248589", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed cloud seed. They give Cloud Seed Droplets for Ender IO's weather machines."),
            // EvilCraft.
            liquid("blood", "Blood Ant", "evilcraft", "evilcraft:blood", "Blood", "water", "evilcraft:bucket_blood", "temperate", "8D0A0A", false,
                    0.15, 1.6, 50, 1200, 80,
                    "Water Ants fed blood. Dark red crops, and Blood Droplets for EvilCraft with nothing harmed."),
            // Allthemodium.
            liquid("soul_lava", "Soul Lava Ant", "allthemodium", "allthemodium:soul_lava", "Soul Lava", "lava", "allthemodium:soul_lava_bucket",
                    "hot", "5B80B2", true, 0.06, 1.9, 25, 3200, 140,
                    "Lava Ants fed soul lava. Their crops burn blue: Soul Lava Droplets from The Other, slowly."),
            // Integrated Dynamics.
            liquid("menril_resin", "Menril Ant", "integrateddynamics", "integrateddynamics:menril_resin", "Menril Resin", "water",
                    "integrateddynamics:bucket_menril_resin", "temperate", "7ECDE1", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed menril resin. Pale blue crops, and Menril Resin Droplets for Integrated Dynamics."),
            liquid("liquid_chorus", "Liquid Chorus Ant", "integrateddynamics", "integrateddynamics:liquid_chorus", "Liquid Chorus", "water",
                    "integrateddynamics:bucket_liquid_chorus", "temperate", "B58CD6", false, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed liquid chorus. They give Liquid Chorus Droplets for Integrated Dynamics."),
            // Theurgy: a liquid that shares its name with the Sal Ammoniac Ant's crystal.
            liquid("sal_ammoniac_solution", "Solvent Ant", "theurgy", "theurgy:sal_ammoniac", "Sal Ammoniac", "water", "theurgy:sal_ammoniac_bucket",
                    "temperate", "D020D0", false, 0.2, 1.4, 50, 800, 60,
                    "Water Ants fed liquid sal ammoniac. They give Sal Ammoniac Droplets, the solvent of Theurgy's alchemy."),
            // Forcecraft.
            liquid("liquid_force", "Liquid Force Ant", "forcecraft", "forcecraft:fluid_force_source", "Liquid Force", "water", "forcecraft:force_bucket",
                    "temperate", "A99E00", true, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed liquid force. Their crops glow yellow: Liquid Force Droplets for Forcecraft."),
            // Ars Magica: Legacy.
            liquid("liquid_etherium", "Etherium Ant", "arsmagicalegacy", "arsmagicalegacy:liquid_etherium", "Liquid Etherium", "water",
                    "arsmagicalegacy:liquid_etherium_bucket", "temperate", "B7DAFF", true, 0.1, 1.7, 50, 2000, 100,
                    "Water Ants fed liquid etherium. Pale, glowing crops, and Liquid Etherium Droplets for Ars Magica."),
            // The Undergarden.
            liquid("virulent_mix", "Virulent Ant", "undergarden", "undergarden:virulent_mix_source", "Virulent Mix", "water",
                    "undergarden:virulent_mix_bucket", "underground", "5A1466", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed virulent mix. Purple crops, and Virulent Mix Droplets from the Undergarden's pools."),
            // Just Dire Things.
            liquid("polymorphic_fluid", "Polymorphic Ant", "justdirethings", "justdirethings:polymorphic_fluid_source", "Polymorphic Fluid", "water",
                    "justdirethings:polymorphic_fluid_bucket", "temperate", "E02020", true, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed polymorphic fluid. Red crops, and Polymorphic Fluid Droplets for Just Dire Things."),
            // Sulfuric acid: Mekanism's and Modern Industrialization's share a tag, so they come before Oritech's.
            liquid("sulfuric_acid", "Acid Ant", "mekanism", "mekanism:sulfuric_acid", "Sulfuric Acid", "water", "mekanism:sulfuric_acid_bucket",
                    "hot", "4ACE40", false, 0.1, 1.7, 50, 2000, 100,
                    "Water Ants fed sulfuric acid. Their crops hold it somehow: Sulfuric Acid Droplets for ore processing.")
                    .or("modern_industrialization", "modern_industrialization:sulfuric_acid", "modern_industrialization:sulfuric_acid_bucket")
                    .or("oritech", "oritech:still_sulfuric_acid", "oritech:still_sulfuric_acid_bucket"),
            // --- Fuels: each ant from the ant of the fluid its own is made from, slower the further down the line ---
            // Oritech's refinery. Heavy oil is half a bucket of crude; diesel and naphtha take a catalyst or a second
            // pass; turbofuel is diesel and a fluxite. Stellaris' diesel is in the same tag as Oritech's: one ant.
            liquid("heavy_oil", "Heavy Oil Ant", "oritech", "oritech:still_heavy_oil", "Heavy Oil", "crude_oil", "oritech:still_heavy_oil_bucket",
                    "hot", "4A3A26", false, 0.05, 1.7, 50, 2400, 120,
                    "Oil Ants fed heavy oil. Tar-brown crops, and Heavy Oil Droplets with no refinery."),
            liquid("diesel", "Diesel Ant", "oritech", "oritech:still_diesel", "Diesel", "crude_oil", "oritech:still_diesel_bucket",
                    "hot", "8FA02A", false, 0.06, 1.7, 50, 2400, 120,
                    "Oil Ants fed diesel. Yellow-green crops, and Diesel Droplets for generators and for turbofuel.")
                    .or("stellaris", "stellaris:diesel", "stellaris:diesel_bucket"),
            liquid("naphtha", "Naphtha Ant", "oritech", "oritech:still_naphtha", "Light Naphtha", "crude_oil", "oritech:still_naphtha_bucket",
                    "hot", "D4C070", false, 0.06, 1.7, 50, 2400, 120,
                    "Oil Ants fed light naphtha. Pale straw crops, and Light Naphtha Droplets for Oritech's plastics."),
            liquid("turbofuel", "Turbofuel Ant", "oritech", "oritech:still_fuel", "Turbofuel", "diesel", "oritech:still_fuel_bucket",
                    "hot", "34566C", false, 0.03, 1.9, 50, 3200, 140,
                    "Diesel Ants fed turbofuel. Slate-blue crops and a slow trickle of Turbofuel Droplets, Oritech's best fuel."),
            // Stellaris: fuel is the other half of its oil; hydrogen and oxygen are water, split. Oxygen has no bucket:
            // its ants are fed the Fluid Cell that carries it.
            liquid("fuel", "Fuel Ant", "stellaris", "stellaris:fuel", "Fuel", "crude_oil", "stellaris:fuel_bucket",
                    "hot", "A8862E", false, 0.06, 1.7, 50, 2400, 120,
                    "Oil Ants fed fuel. Ochre crops, and Fuel Droplets to fill a rocket for Stellaris."),
            liquid("hydrogen", "Hydrogen Ant", "stellaris", "stellaris:hydrogen", "Hydrogen", "water", "stellaris:hydrogen_bucket",
                    "temperate", "22B8E8", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed hydrogen. Their crops fizz bright blue: Hydrogen Droplets for Stellaris, with no electrolyzer."),
            liquid("oxygen", "Oxygen Ant", "stellaris", "stellaris:oxygen", "Oxygen", "water", "stellaris:fluid_cell",
                    "temperate", "B8ECF8", false, 0.1, 1.5, 50, 1200, 80,
                    "Water Ants fed Fluid Cells of oxygen. Pale, fizzing crops, and Oxygen Droplets for Stellaris: air to take along."),
            // Ender IO's rocket fuel: hootch, gunpowder and redstone in the Vat. No tag joins it to Stellaris' fuel.
            liquid("rocket_fuel", "Rocket Fuel Ant", "enderio", "enderio:fluid_rocket_fuel_still", "Rocket Fuel", "hootch", "enderio:rocket_fuel_bucket",
                    "hot", "6E6E58", false, 0.06, 1.8, 50, 2400, 120,
                    "Hootch Ants fed rocket fuel. Drab olive crops, and Rocket Fuel Droplets for Ender IO."),
            // Just Dire Things' fuels: each needs the goo of its tier (the Nether, the End, an Ancient City) and a bucket of
            // the fuel before it. Time Fluid needs the last goo and all three dimensions; Portal Fluid the End's.
            liquid("blaze_ember_fuel", "Blaze Ember Ant", "justdirethings", "justdirethings:refined_t2_fluid_source", "Blaze Ember Fuel", "polymorphic_fluid",
                    "justdirethings:refined_t2_fluid_bucket", "hot", "B83210", true, 0.03, 1.9, 25, 3200, 140,
                    "Polymorphic Ants fed Blaze Ember Fuel. Their crops glow like embers: refined fuel for Just Dire Things, slowly."),
            liquid("voidflame_fuel", "Voidflame Ant", "justdirethings", "justdirethings:refined_t3_fluid_source", "Voidflame Fuel", "blaze_ember_fuel",
                    "justdirethings:refined_t3_fluid_bucket", "temperate", "38C4C0", true, 0.012, 2.0, 25, 4000, 160,
                    "Blaze Ember Ants fed Voidflame Fuel. Their crops burn teal: a bucket of refined fuel in an hour and a half."),
            liquid("eclipse_ember_fuel", "Eclipse Ember Ant", "justdirethings", "justdirethings:refined_t4_fluid_source", "Eclipse Ember Fuel",
                    "voidflame_fuel", "justdirethings:refined_t4_fluid_bucket", "underground", "2C2226", true, 0.005, 2.2, 25, 6000, 200,
                    "Voidflame Ants fed Eclipse Ember Fuel. Dark crops with a pale fire inside: the last fuel of Just Dire Things, a bucket in "
                            + "about four hours."),
            liquid("time_fluid", "Time Ant", "justdirethings", "justdirethings:time_fluid_source", "Time Fluid", "polymorphic_fluid",
                    "justdirethings:time_fluid_bucket", "temperate", "5CFF9A", true, 0.005, 2.2, 25, 6000, 200,
                    "Polymorphic Ants fed Time Fluid. Their crops glow green and they are never late: a bucket in about four hours."),
            liquid("portal_fluid", "Portal Ant", "justdirethings", "justdirethings:portal_fluid_source", "Portal Fluid", "polymorphic_fluid",
                    "justdirethings:portal_fluid_bucket", "temperate", "00B48C", true, 0.012, 2.0, 25, 4000, 160,
                    "Polymorphic Ants fed Portal Fluid. Bubbling green crops, and Portal Fluid Droplets for the Portal Gun."),
            // --- The fluids of mods that are in All the Mods 10 only: ids read from their sources, never run ---
            liquid("canola_oil", "Canola Ant", "actuallyadditions", "actuallyadditions:canola_oil", "Canola Oil", "water",
                    "actuallyadditions:canola_oil_bucket", "temperate", "F4CF2F", false, 0.15, 1.5, 50, 1200, 80,
                    "Water Ants fed canola oil. Bright yellow crops, and Canola Oil Droplets for Actually Additions."),
            // Biofuel: Oritech's dark green comes first, as the one that has been run; Industrial Foregoing's is purple.
            liquid("biofuel", "Biofuel Ant", "oritech", "oritech:still_biofuel", "Biofuel", "water", "oritech:still_biofuel_bucket",
                    "temperate", "2E6A2A", false, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed biofuel. Dark green crops, and Biofuel Droplets: fuel pressed from plants, with no press.")
                    .or("industrialforegoing", "industrialforegoing:biofuel", "industrialforegoing:biofuel_bucket"),
            liquid("bioethanol", "Bioethanol Ant", "mekanismgenerators", "mekanismgenerators:bioethanol", "Bioethanol", "water",
                    "mekanismgenerators:bioethanol_bucket", "temperate", "CEEB3D", false, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed bioethanol. They give Bioethanol Droplets: fuel grown, not drilled.")
                    .or("createaddition", "createaddition:bioethanol", "createaddition:bioethanol_bucket"),
            liquid("ether", "Ether Ant", "eternal_starlight", "eternal_starlight:ether", "Ether", "water", "eternal_starlight:ether_bucket",
                    "temperate", "BFF5E1", true, 0.12, 1.6, 50, 1600, 100,
                    "Water Ants fed ether. Pale mint crops, and Ether Droplets from the rivers of the Starlight dimension."),
            liquid("liquid_starlight", "Starlight Ant", "astralsorcery", "astralsorcery:liquid_starlight", "Liquid Starlight", "water",
                    "astralsorcery:liquid_starlight_bucket", "cold", "C8D2F0", true, 0.1, 1.7, 50, 2000, 100,
                    "Water Ants fed liquid starlight. Their crops shine by night and by day: Liquid Starlight Droplets for Astral Sorcery."));

    /** A row for a tier 2 liquid of another mod; the ant's body and plates are shades of the fluid's one colour. */
    static Liquid liquid(String id, String ant, String mod, String fluid, String fluidName, String from, String feed, String climate, String colour,
                         boolean glow, double rate, double food, int amount, int energy, int time, String description) {
        int rgb = Integer.parseInt(colour, 16);
        return new Liquid(id, ant, mod, fluid, fluidName, from, feed, null, 2, climate, toward(rgb, 0x000000, 0.55), toward(rgb, 0x000000, 0.68),
                colour, glow, rate, food, amount, energy, time, description);
    }

    /** The names of the mods the liquids come from, for "these ants need ..." when a mod is gone. */
    static final Map<String, String> MOD_NAMES = new LinkedHashMap<>();

    static {
        String[][] names = {{"minecraft", "Minecraft"}, {"machinaopus", "MachinaOpus"}, {"mekanism", "Mekanism"}, {"create", "Create"},
                {"industrialforegoing", "Industrial Foregoing"}, {"enderio", "Ender IO"}, {"thermal", "Thermal Series"},
                {"immersiveengineering", "Immersive Engineering"}, {"oritech", "Oritech"}, {"stellaris", "Stellaris"},
                {"productivebees", "Productive Bees"}, {"the_bumblezone", "The Bumblezone"}, {"productivetrees", "Productive Trees"},
                {"railcraft", "Railcraft"}, {"justdirethings", "Just Dire Things"}, {"energizedpower", "Energized Power"},
                {"neovitae", "Neo Vitae"}, {"sophisticatedcore", "Sophisticated Core"}, {"evilcraft", "EvilCraft"},
                {"allthemodium", "Allthemodium"}, {"integrateddynamics", "Integrated Dynamics"}, {"theurgy", "Theurgy"},
                {"forcecraft", "Forcecraft"}, {"arsmagicalegacy", "Ars Magica: Legacy"}, {"undergarden", "The Undergarden"},
                {"pneumaticcraft", "PneumaticCraft"}, {"modern_industrialization", "Modern Industrialization"}, {"ad_astra", "Ad Astra"},
                {"extended_industrialization", "Extended Industrialization"}, {"mob_grinding_utils", "Mob Grinding Utils"},
                {"create_enchantment_industry", "Create: Enchantment Industry"}, {"createaddition", "Create Crafts & Additions"},
                {"actuallyadditions", "Actually Additions"}, {"mekanismgenerators", "Mekanism Generators"},
                {"eternal_starlight", "Eternal Starlight"}, {"astralsorcery", "Astral Sorcery"}};
        for (String[] name : names) {
            MOD_NAMES.put(name[0], name[1]);
        }
    }

    /** Load conditions for another mod's liquid: the mod, its fluid and the droplet item are there. Null for the base game's. */
    static List<Object> liquidConditions(Liquid l) {
        if (l.mod() == null) {
            return null;
        }
        Object droplet = obj("type", ac("registered"), "registry", "minecraft:item", "value", ac(l.droplet()));
        if (l.others().isEmpty()) {
            return arr(obj("type", "neoforge:mod_loaded", "modid", l.mod()),
                    obj("type", ac("registered"), "registry", "minecraft:fluid", "value", l.fluid()), droplet);
        }
        // Several mods add it: the droplet item is registered only while one of their fluids is in the game.
        List<Object> all = new ArrayList<>(conditions(l.sources().stream().map(source -> source[0]).toList()));
        all.add(droplet);
        return all;
    }

    /** The load conditions of the Centrifuge recipe for one source of a liquid: no mod listed before it has its fluid. */
    static List<Object> recipeConditions(Liquid l, int index) {
        String[] source = l.sources().get(index);
        List<Object> all = new ArrayList<>(arr(obj("type", "neoforge:mod_loaded", "modid", source[0]),
                obj("type", ac("registered"), "registry", "minecraft:fluid", "value", source[1]),
                obj("type", ac("registered"), "registry", "minecraft:item", "value", ac(l.droplet()))));
        for (int before = 0; before < index; before++) {
            all.add(obj("type", "neoforge:not", "value",
                    obj("type", ac("registered"), "registry", "minecraft:fluid", "value", l.sources().get(before)[1])));
        }
        return all;
    }

    /**
     * A block model in the build the Droplet Centrifuge and the Generator share: casing, lid, four corner posts, a wall
     * texture per side ({@code north} is the front), and {@code inside} behind the walls.
     */
    static void labMachine(String name, Map<String, Object> textures, List<Object> inside, String north, String south, String west, String east) {
        List<Object> elements = new ArrayList<>();
        // The casing.
        elements.add(obj("from", arr(0, 0, 0), "to", arr(16, 8, 16), "faces", obj(
                "down", obj("texture", "#top", "cullface", "down"), "up", obj("texture", "#deck"),
                "north", obj("uv", arr(0, 8, 16, 16), "texture", "#front", "cullface", "north"),
                "south", obj("uv", arr(0, 8, 16, 16), "texture", "#side", "cullface", "south"),
                "west", obj("uv", arr(0, 8, 16, 16), "texture", "#side", "cullface", "west"),
                "east", obj("uv", arr(0, 8, 16, 16), "texture", "#side", "cullface", "east"))));
        // The lid.
        elements.add(obj("from", arr(0, 14, 0), "to", arr(16, 16, 16), "faces", obj(
                "down", obj("texture", "#deck"), "up", obj("texture", "#top", "cullface", "up"),
                "north", obj("uv", arr(0, 0, 16, 2), "texture", "#front", "cullface", "north"),
                "south", obj("uv", arr(0, 0, 16, 2), "texture", "#side", "cullface", "south"),
                "west", obj("uv", arr(0, 0, 16, 2), "texture", "#side", "cullface", "west"),
                "east", obj("uv", arr(0, 0, 16, 2), "texture", "#side", "cullface", "east"))));
        // The four corner posts between them.
        for (double[] post : new double[][] {{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            Map<String, Object> face = obj("uv", arr(0, 2, 2, 8), "texture", "#side");
            elements.add(obj("from", arr(post[0], 8, post[1]), "to", arr(post[0] + 2, 14, post[1] + 2),
                    "faces", obj("north", face, "south", face, "west", face, "east", face)));
        }
        // The walls between the posts, seen from both sides.
        Map<String, Object> front = obj("uv", arr(2, 2, 14, 8), "texture", north);
        Map<String, Object> rear = obj("uv", arr(2, 2, 14, 8), "texture", south);
        Map<String, Object> left = obj("uv", arr(2, 2, 14, 8), "texture", west);
        Map<String, Object> right = obj("uv", arr(2, 2, 14, 8), "texture", east);
        elements.add(obj("from", arr(2, 8, 0.25), "to", arr(14, 14, 0.25), "faces", obj("north", front, "south", front)));
        elements.add(obj("from", arr(2, 8, 15.75), "to", arr(14, 14, 15.75), "faces", obj("north", rear, "south", rear)));
        elements.add(obj("from", arr(0.25, 8, 2), "to", arr(0.25, 14, 14), "faces", obj("west", left, "east", left)));
        elements.add(obj("from", arr(15.75, 8, 2), "to", arr(15.75, 14, 14), "faces", obj("west", right, "east", right)));
        elements.addAll(inside);
        model(name, textures, elements);
    }

    /** The Liquid Ant of a liquid: its species, the evolution into it and what its Queen is fed. */
    /** Liquids a bucket of which is a day's work in their own mod: their ants take four buckets at most, not twenty-four. */
    static final Set<String> PRECIOUS_LIQUIDS = Set.of("voidflame_fuel", "eclipse_ember_fuel", "time_fluid", "portal_fluid");

    static void liquidAnt(Liquid liquid, List<Object> conditions) {
        Map<String, String> l = LANG;
        // --- The species: a Liquid Ant, with its crop colour and its one droplet ---
        Map<String, Object> species = obj("tier", liquid.tier(), "category", "liquid",
                "look", obj("body", "#" + liquid.body(), "gaster", "#" + liquid.plates(), "accent", "#" + liquid.plates(), "pattern", "plain",
                        "glow", liquid.glow() ? true : null, "crop", "#" + liquid.crop()),
                "climate", liquid.climate(),
                "diet", obj("consumption", liquid.food()),
                "liquid", obj("fluid", liquid.others().isEmpty() ? liquid.fluid() : liquid.sources().stream().map(source -> (Object) source[1]).toList(),
                        "droplet", ac(liquid.droplet()), "rate", liquid.rate()),
                "rearing", obj("cost", arr(obj("ingredient", ac("royal_jelly"), "count", 2),
                        obj("ingredient", "minecraft:honey_bottle", "count", 2)),
                        "time", 3600),
                "breeding", obj("eggs", 0.9, "growth", 0.9));
        if (conditions != null) {
            species.put("neoforge:conditions", conditions);
        }
        write(DATA.resolve(NS + "/species/" + liquid.id() + ".json"), species);
        l.put("species." + NS + "." + liquid.id(), liquid.ant());
        l.put("species." + NS + "." + liquid.id() + ".guard", guardName(liquid.ant()));
        l.put("species." + NS + "." + liquid.id() + ".desc", liquid.description());
        // --- How to get it: its parent species' Queen fed the fluid (what she is fed is a tag, so packs can add to it) ---
        boolean precious = PRECIOUS_LIQUIDS.contains(liquid.id());
        Map<String, Object> evolution = obj("from", ac(liquid.from()), "to", ac(liquid.id()), "item", "#" + ac("liquid_feed/" + liquid.id()),
                "chance", precious ? 0.25 : 0.08, "guaranteed_after", precious ? 4 : 24, "min_stage", 2, "min_population", 300L);
        if (liquid.dimension() != null) {
            evolution.put("dimension", liquid.dimension());
        }
        if (conditions != null) {
            evolution.put("neoforge:conditions", conditions);
        }
        write(DATA.resolve(NS + "/evolution/" + liquid.from() + "_to_" + liquid.id() + ".json"), evolution);
        List<Object> feeds = new ArrayList<>();
        for (String[] source : liquid.sources()) {
            for (String feed : source[2].split(",")) {
                Object entry = liquid.mod() == null ? feed : obj("id", feed, "required", false);
                if (!feeds.contains(entry)) {
                    feeds.add(entry);
                }
            }
        }
        write(DATA.resolve("tags/item/liquid_feed/" + liquid.id() + ".json"), obj("values", feeds));
    }

    static void liquids() {
        Map<String, String> l = LANG;
        List<Object> droplets = new ArrayList<>();
        List<Object> kinds = new ArrayList<>();
        for (Liquid liquid : LIQUIDS) {
            if (SPECIES.stream().anyMatch(s -> s.id().equals(liquid.id()))) {
                throw new IllegalStateException("A liquid kind and a species share the id " + liquid.id());
            }
            List<Object> conditions = liquidConditions(liquid);
            if (liquid.ant() != null) {
                liquidAnt(liquid, conditions);
            }
            if (liquid.mod() != null) {
                List<Object> sources = new ArrayList<>();
                for (String[] source : liquid.sources()) {
                    if (!MOD_NAMES.containsKey(source[0])) {
                        throw new IllegalStateException("MOD_NAMES has no name for " + source[0]);
                    }
                    sources.add(obj("mod", source[0], "fluid", source[1]));
                }
                kinds.add(obj("name", liquid.id(), "sources", sources));
            }
            // --- The droplet: its name from the fluid's, its flat model ---
            l.put("item." + NS + "." + liquid.droplet(), liquid.fluidName() + " Droplet");
            write(ASSETS.resolve("models/item/" + liquid.droplet() + ".json"), obj("parent", "minecraft:item/generated",
                    "textures", obj("layer0", ac("item/" + liquid.droplet()))));
            write(ASSETS.resolve("items/" + liquid.droplet() + ".json"),
                    obj("model", obj("type", "minecraft:model", "model", ac("item/" + liquid.droplet()))));
            droplets.add(liquid.mod() == null ? ac(liquid.droplet()) : obj("id", ac(liquid.droplet()), "required", false));
            // --- The Centrifuge recipe: one droplet into the fluid ---
            for (int index = 0; index < liquid.sources().size(); index++) {
                String[] source = liquid.sources().get(index);
                Map<String, Object> recipe = obj("type", ac("centrifuging"), "ingredient", ac(liquid.droplet()),
                        "result", obj("id", source[1], "amount", liquid.amount()), "energy", liquid.energy(), "time", liquid.time());
                if (conditions != null) {
                    recipe.put("neoforge:conditions", liquid.others().isEmpty() ? conditions : recipeConditions(liquid, index));
                }
                write(DATA.resolve("recipe/centrifuging/" + liquid.id() + (index == 0 ? "" : "_" + source[0]) + ".json"), recipe);
            }
        }
        write(DATA.resolve("tags/item/droplets.json"), obj("values", droplets));
        // The kinds the mod offers when it starts (compat/liquid/LiquidIntegrations reads this from the jar).
        write(RES.resolve("antcolonies.liquid_kinds.json"), obj("kinds", kinds));
        MOD_NAMES.forEach((id, name) -> l.put("mod." + NS + "." + id, name));
        l.put("category." + NS + ".liquid", "Liquid");
        l.put("tag.item." + NS + ".droplets", "Droplets");
        for (Liquid liquid : LIQUIDS) {
            if (liquid.ant() != null) {
                l.put("tag.item." + NS + ".liquid_feed." + liquid.id(), "Fed to a Queen for " + liquid.ant() + "s");
            }
        }
        l.put("item." + NS + ".droplet", "%s Droplet");
        l.put("tooltip." + NS + ".droplet", "A drop of %s from Liquid Ants. A Droplet Centrifuge spins it into the fluid.");

        // --- The Liquid Chamber: a rack the full ants hang from (as honeypot ants do), over a bowl that catches the drips ---
        String wood = ac("block/terrarium_wood");
        String top = ac("block/liquid_chamber_top");
        double y = FLOOR;
        model("liquid_chamber", obj("particle", top, "side", wood, "top", top, "rack", "minecraft:block/stripped_spruce_log",
                "gourd", ac("block/liquid_chamber_gourd"), "bowl", "minecraft:block/terracotta", "pool", ac("block/liquid_chamber_pool")), arr(
                floorBody(), soilCore(),
                // The rack, from the soil to just under the lid; the gourds hang from it over the bowl.
                box(p(3, y, 7), p(4, 13, 9), "#rack", "#rack"), box(p(12, y, 7), p(13, 13, 9), "#rack", "#rack"),
                box(p(3, 13, 7.5), p(13, 14, 8.5), "#rack", "#rack"),
                box(p(5, 10, 7), p(7, 13, 9), "#gourd", "#gourd"), box(p(7.5, 9.6, 7), p(9.5, 13, 9), "#gourd", "#gourd"),
                box(p(10, 10.4, 7), p(12, 13, 9), "#gourd", "#gourd"),
                obj("from", arr(4.5, y, 5.5), "to", arr(11.5, y + 1, 10.5), "faces", obj("up", face("#pool"), "north", face("#bowl"), "south", face("#bowl"),
                        "west", face("#bowl"), "east", face("#bowl")))));
        colonyPart("liquid_chamber");
        write(ASSETS.resolve("items/liquid_chamber.json"), obj("model", framedItem("liquid_chamber")));
        selfDrop("liquid_chamber");
        shaped("liquid_chamber", ac("liquid_chamber"), 1, new String[] {"BHB", "DMD", "DCD"}, "B", "glass_bottle", "H", "honeycomb", "D", "clay",
                "M", "packed_mud", "C", "chest");
        l.put("block." + NS + ".liquid_chamber", "Liquid Chamber");
        l.put("tooltip." + NS + ".liquid_chamber",
                "Liquid Ants hang here with full crops and give droplets of their fluid. Only ants that carry a liquid work here.");

        // --- The Droplet Centrifuge: casing, glass drum section, lid; its front panel lights up while it works ---
        for (String working : new String[] {"", "_working"}) {
            labMachine("centrifuge" + working, obj("particle", ac("block/centrifuge_side"), "side", ac("block/centrifuge_side"),
                    "front", ac("block/centrifuge_front" + working), "top", ac("block/centrifuge_top"), "deck", ac("block/centrifuge_deck"),
                    "glass", ac("block/centrifuge_glass")), List.of(), "#glass", "#glass", "#glass", "#glass");
        }
        Map<String, Object> variants = new LinkedHashMap<>();
        String[] facings = {"north", "east", "south", "west"};
        for (int f = 0; f < 4; f++) {
            for (String working : new String[] {"false", "true"}) {
                variants.put("facing=" + facings[f] + ",working=" + working,
                        obj("model", ac("block/centrifuge" + (working.equals("true") ? "_working" : "")),
                                "y", f == 0 ? null : f * 90));
            }
        }
        write(ASSETS.resolve("blockstates/centrifuge.json"), obj("variants", variants));
        write(ASSETS.resolve("items/centrifuge.json"), obj("model", obj("type", "minecraft:model", "model", ac("block/centrifuge"))));
        selfDrop("centrifuge");
        shaped("centrifuge", ac("centrifuge"), 1, new String[] {"HGH", "CBC", "IRI"}, "H", ac("hardened_chitin"), "G", "glass", "C", "copper_ingot",
                "B", "bucket", "I", "iron_ingot", "R", "redstone");
        l.put("block." + NS + ".centrifuge", "Droplet Centrifuge");
        l.put("tooltip." + NS + ".centrifuge", "Spins Liquid Ants' droplets into fluid. Runs on FE; take the fluid with a bucket, a tank or pipes.");

        // --- Advancements ---
        advancement("liquid_ants", "hybrid", "liquid_chamber", "task", "Living Jars",
                "Raise a colony of Liquid Ants: feed a Honeypot Queen buckets of water (or of lava, in the Nether)", colony("category", "liquid"));
        advancement("centrifuge", "liquid_ants", "centrifuge", "task", "Spin Cycle", "Make a Droplet Centrifuge to turn droplets into fluid",
                hasItem("centrifuge"));

        // --- Words ---
        String[][] gui = {
                {"site.liquid_ants", "Liquid Ants %s/%s (want %s)"}, {"site.droplet_rate", "%s droplets/min"},
                {"site.droplet_store", "Droplets wait here while the Storage is full"},
                {"liquid.needs", "Only ants that carry a liquid work here"}, {"liquid.only", "Liquid Ants work only at a Liquid Chamber"},
                {"liquid.makes", "%s: a Droplet Centrifuge spins it into %s"}, {"liquid.missing", "These ants need %s"},
                {"liquid.line", "%s ants: %s, %s/min (%s)"}, {"card.liquid", "%s Liquid Ants, full of %s"},
                {"centrifuge.status.idle", "Waiting for droplets"}, {"centrifuge.status.working", "Spinning: %s"},
                {"centrifuge.status.no_energy", "Not enough FE"}, {"centrifuge.status.tank_full", "Tank full"},
                {"centrifuge.status.blocked", "Tank holds %s: empty it first"}, {"centrifuge.status.paused", "Paused by redstone"},
                {"centrifuge.status.done", "Done: the fluid is ready"},
                {"centrifuge.empty", "Empty"}, {"centrifuge.amount", "%s mB"}, {"centrifuge.of", "of %s mB"},
                {"centrifuge.each", "Per droplet:"}, {"centrifuge.gives", "+%s mB"}, {"centrifuge.takes", "%s"},
                {"centrifuge.energy", "Energy"}, {"centrifuge.energy.hint", "Takes FE on every side: a Generator next to it will do"},
                {"centrifuge.slot", "Droplets"}, {"centrifuge.slot.hint", "From the Liquid Chambers of Liquid Ants"},
                {"centrifuge.tank.hint", "Use a bucket on the block, or put a tank or a pipe next to it"},
                {"centrifuge.run", "%s s of %s s"}, {"centrifuge.speed", "%s steps a tick (Speed Upgrades)"},};
        for (String[] g : gui) {
            l.put("gui." + NS + "." + g[0], g[1]);
        }
        l.put("status." + NS + ".no_droplet", "Their fluid is not in this game: these ants make nothing");
        l.put("status." + NS + ".no_droplet.short", "No fluid");
        l.put("info." + NS + ".liquid", "Liquid Ant: works only at a Liquid Chamber");
        l.put("info." + NS + ".liquid.also", "At a Liquid Chamber its workers give a liquid");
        l.put("jei." + NS + ".liquid.also", "At a Liquid Chamber; it has other work too");
        l.put("info." + NS + ".liquid.makes", "Makes %s");
        l.put("info." + NS + ".liquid.fluid", "A drop of %s");
        l.put("info." + NS + ".liquid.centrifuge", "Droplet Centrifuge: %s mB of %s for %s FE");
        l.put("info." + NS + ".liquid.rate", "Droplet rate x%s");
        l.put("jei." + NS + ".liquid", "Liquid Ants");
        l.put("jei." + NS + ".liquid.rate", "Droplet rate x%s, food x%s");
        l.put("jei." + NS + ".liquid.only", "Works only at a Liquid Chamber");
        l.put("jei." + NS + ".centrifuging", "Droplet Centrifuge");
        l.put("jei." + NS + ".centrifuging.cost", "%s FE, %s s");
        l.put("jei." + NS + ".centrifuging.fluid", "%s mB of %s");
    }

    public static void main(String[] args) {
        species();
        liquids();
        evolutions();
        research();
        hybrids();
        mutations();
        companions();
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
        chitinTiers();
        colonyUpdate();
        researchLang();
        configLang();
        guideLang();
        particles();
        sounds();
        ftbFaces();
        lang();
        System.out.println("Wrote " + written + " files.");
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
