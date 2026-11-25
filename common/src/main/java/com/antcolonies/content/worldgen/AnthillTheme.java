package com.antcolonies.content.worldgen;

import java.util.List;
import java.util.Locale;

import com.antcolonies.content.block.AnthillSoilBlock;
import com.antcolonies.content.block.SoilTone;
import com.antcolonies.core.registry.ModDecor;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;

/** What an anthill looks like where it stands: its mound's shape and earth, its tunnels' walls, and what grows on and around it. */
public enum AnthillTheme {
    TEMPERATE(Shape.CONE),
    TAIGA(Shape.DOME),
    SNOWY(Shape.CONE),
    DESERT(Shape.CRATER),
    BADLANDS(Shape.SPIRES),
    SAVANNA(Shape.SPIRES),
    JUNGLE(Shape.MOSSY),
    SWAMP(Shape.LOW),
    MUSHROOM(Shape.CONE),
    CHERRY(Shape.CONE),
    BIRCH(Shape.CONE),
    DARK_FOREST(Shape.DOME),
    FLOWER(Shape.CONE),
    PALE_GARDEN(Shape.MOSSY),
    BAMBOO(Shape.CONE),
    OLD_GROWTH(Shape.DOME),
    MOUNTAIN(Shape.CAIRN),
    ICE(Shape.SPIRES),
    BEACH(Shape.DUNE),
    NETHER(Shape.CONE),
    END(Shape.CONE),
    CRIMSON(Shape.DOME),
    WARPED(Shape.MOSSY),
    SOUL_VALLEY(Shape.SPIRES),
    BASALT(Shape.CAIRN),
    END_HIGHLANDS(Shape.SPIRES),
    /** Another mod's world: the anthill is built of the ground it stands on (AnthillPiece puts that in for grass, dirt and stone). */
    LOCAL(Shape.CONE);

    /** What blooms on and round a flower forest's anthill. */
    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY};

    /** The badlands' terracotta bands, by height. */
    private static final Block[] BANDS = {Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.TERRACOTTA,
            Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.orange(),
            Blocks.DYED_TERRACOTTA.lightGray()};

    /** A mound's outline: radius and height of its body (spires rise above the body). */
    public enum Shape {
        CONE(7, 5), DOME(8, 7), CRATER(7, 3), SPIRES(6, 3), MOSSY(8, 4), LOW(6, 3), CAIRN(6, 8), DUNE(8, 4);

        public final int radius;
        public final int height;

        Shape(int radius, int height) {
            this.radius = radius;
            this.height = height;
        }
    }

    public final Shape shape;

    AnthillTheme(Shape shape) {
        this.shape = shape;
    }

    /** One of the Nether's looks, or of the End's. */
    public boolean nether() {
        return this == NETHER || this == CRIMSON || this == WARPED || this == SOUL_VALLEY || this == BASALT;
    }

    public boolean end() {
        return this == END || this == END_HIGHLANDS;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The ants this look is the home of, by species name. An anthill takes one of them that lives in its biome, else the
     * first. Empty: any ant of the biome (the plain look, and another mod's world).
     */
    public List<String> dwellers() {
        return switch (this) {
            case TEMPERATE, LOCAL -> List.of();
            case TAIGA, OLD_GROWTH -> List.of("wood");
            case DARK_FOREST -> List.of("wood", "common");
            case BIRCH -> List.of("common", "wood");
            case SNOWY, ICE -> List.of("snow");
            case DESERT -> List.of("desert");
            case BADLANDS -> List.of("badlands", "desert");
            case SAVANNA -> List.of("savanna", "army");
            case JUNGLE, BAMBOO -> List.of("jungle", "farmer");
            case SWAMP -> List.of("swamp", "farmer");
            case MUSHROOM -> List.of("farmer");
            case CHERRY, FLOWER -> List.of("blossom", "common");
            case PALE_GARDEN -> List.of("pale");
            case MOUNTAIN -> List.of("stone");
            case BEACH -> List.of("shore");
            case WARPED -> List.of("warped", "fire");
            // Warped Ants are native to crimson forests too: this look names only its own, so they stay out.
            case CRIMSON -> List.of("crimson");
            case SOUL_VALLEY -> List.of("soul");
            case NETHER, BASALT -> List.of("fire");
            case END, END_HIGHLANDS -> List.of("chorus");
        };
    }

    /** The tone of this look's anthill soil. */
    public SoilTone tone() {
        return switch (this) {
            case DESERT, BEACH -> SoilTone.SAND;
            case BADLANDS -> SoilTone.RED;
            case SNOWY, ICE -> SoilTone.FROST;
            case MOUNTAIN -> SoilTone.STONE;
            case PALE_GARDEN -> SoilTone.PALE;
            case SOUL_VALLEY, BASALT -> SoilTone.DARK;
            case NETHER, CRIMSON, WARPED -> SoilTone.NETHER;
            default -> SoilTone.EARTH;
        };
    }

    private BlockState toned(BlockState state) {
        return state.hasProperty(AnthillSoilBlock.TONE) ? state.setValue(AnthillSoilBlock.TONE, tone()) : state;
    }

    public static AnthillTheme byName(String name) {
        for (AnthillTheme theme : values()) {
            if (theme.getSerializedName().equals(name)) {
                return theme;
            }
        }
        return TEMPERATE;
    }

    public static AnthillTheme of(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return BADLANDS;
        }
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            return DESERT;
        }
        if (biome.is(Tags.Biomes.IS_MUSHROOM)) {
            return MUSHROOM;
        }
        if (biome.is(Biomes.PALE_GARDEN)) {
            return PALE_GARDEN;
        }
        if (biome.is(Tags.Biomes.IS_SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return SWAMP;
        }
        if (biome.is(Biomes.BAMBOO_JUNGLE)) {
            return BAMBOO;
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return SAVANNA;
        }
        // Ice before snow, and snow before the trees it lies on.
        if (biome.is(Tags.Biomes.IS_ICY)) {
            return ICE;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY)) {
            return SNOWY;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return biome.is(Tags.Biomes.IS_OLD_GROWTH) ? OLD_GROWTH : TAIGA;
        }
        if (biome.is(Biomes.CHERRY_GROVE)) {
            return CHERRY;
        }
        if (biome.is(Tags.Biomes.IS_BIRCH_FOREST)) {
            return BIRCH;
        }
        if (biome.is(Tags.Biomes.IS_DARK_FOREST)) {
            return DARK_FOREST;
        }
        if (biome.is(Tags.Biomes.IS_FLOWER_FOREST) || biome.is(Tags.Biomes.IS_FLORAL)) {
            return FLOWER;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return BEACH;
        }
        // Bare rock last: a mountain that is also a forest or a meadow keeps that look.
        if (biome.is(Tags.Biomes.IS_STONY_SHORES) || biome.is(Tags.Biomes.IS_WINDSWEPT) || biome.is(Tags.Biomes.IS_MOUNTAIN_PEAK)
                || biome.is(Biomes.STONY_PEAKS)) {
            return MOUNTAIN;
        }
        return TEMPERATE;
    }

    /** A Nether anthill's look: by the biome, or by its name where another mod has its own forest, valley or delta. */
    public static AnthillTheme ofNether(Holder<Biome> biome) {
        String name = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        if (biome.is(Biomes.CRIMSON_FOREST) || name.contains("crimson")) {
            return CRIMSON;
        }
        if (biome.is(Biomes.WARPED_FOREST) || name.contains("warped")) {
            return WARPED;
        }
        if (biome.is(Biomes.SOUL_SAND_VALLEY) || name.contains("soul")) {
            return SOUL_VALLEY;
        }
        if (biome.is(Biomes.BASALT_DELTAS) || name.contains("basalt")) {
            return BASALT;
        }
        return NETHER;
    }

    public static AnthillTheme ofEnd(Holder<Biome> biome) {
        return biome.is(Biomes.END_HIGHLANDS) ? END_HIGHLANDS : END;
    }

    // --- The mound ---

    public int reach() {
        return shape.radius + (trail() != null ? 12 : 3);
    }

    public int maxHeight() {
        return shape == Shape.SPIRES ? 14 : shape.height;
    }

    public int moundHeight(long seed, int x, int z) {
        double noise = (hash(seed, x, 0, z) - 0.5);
        double flat = Math.sqrt(x * x + z * z);
        double distance = flat + noise * (shape == Shape.MOSSY ? 2.4 : shape == Shape.CAIRN ? 2.6 : 1.4);
        double f = distance / shape.radius;
        double body = switch (shape) {
            case CONE, LOW -> f >= 1 ? 0 : shape.height * (1 - f * f);
            case DOME -> f >= 1 ? 0 : shape.height * Math.sqrt(1 - f * f);
            case MOSSY -> f >= 1 ? 0 : shape.height * (1 - Math.pow(f, 1.5));
            // A ring of dug-out earth round the entrance, pebbles piled on it.
            case CRATER -> f >= 1.15 ? 0 : shape.height * Math.exp(-Math.pow((f - 0.55) / 0.24, 2));
            case SPIRES -> f >= 1 ? 0 : shape.height * (1 - f * f);
            // A steep pile of rocks.
            case CAIRN -> f >= 1 ? 0 : shape.height * Math.pow(1 - f, 0.75);
            // Sand blown up against one side: high on the east, running out low to the west.
            case DUNE -> f >= 1 ? 0 : shape.height * (1 - f * f) * (0.55 + 0.45 * (flat < 0.5 ? 0 : x / flat));
        };
        int height = (int) Math.round(body);
        if (shape == Shape.SPIRES) {
            height = Math.max(height, spires(seed, x, z));
        }
        return Math.min(height, maxHeight());
    }

    /** The termite spires over a column: a few narrow towers round the entrance (never over it), each tapering to a point. */
    private static int spires(long seed, int x, int z) {
        int count = 3 + (int) (hash(seed, 7, 7, 7) * 3);
        int best = 0;
        for (int i = 0; i < count; i++) {
            double radius = 1.3 + hash(seed, i, 11, 0) * 0.9;
            double angle = (i + hash(seed, i, 12, 0) * 0.6) * Math.PI * 2 / count;
            double out = radius + 1.2 + hash(seed, i, 13, 0) * 2;
            double height = 7 + hash(seed, i, 14, 0) * 6;
            double dx = x - Math.cos(angle) * out;
            double dz = z - Math.sin(angle) * out;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < radius) {
                best = Math.max(best, (int) Math.round(height * Math.pow(1 - d / radius, 0.55)));
            }
        }
        return best;
    }

    /** A block of the mound, {@code depth} blocks under its surface (0: the top), at world height {@code y}. */
    public BlockState mound(double roll, int depth, int y) {
        boolean top = depth == 0;
        Block block = switch (this) {
            case TEMPERATE -> top && roll < 0.2 ? Blocks.PODZOL : roll < 0.45 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.7 ? Blocks.COARSE_DIRT
                    : roll < 0.9 ? Blocks.ROOTED_DIRT : Blocks.GRAVEL;
            // Wood ants thatch their domes with needles and twigs.
            case TAIGA -> top
                    ? roll < 0.45 ? Blocks.SPRUCE_LEAVES : roll < 0.75 ? Blocks.PODZOL : roll < 0.9 ? Blocks.COARSE_DIRT : Blocks.SPRUCE_WOOD
                    : roll < 0.35 ? Blocks.PODZOL : roll < 0.6 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.8 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT;
            case SNOWY -> top ? roll < 0.8 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE
                    : roll < 0.4 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.65 ? Blocks.COARSE_DIRT : roll < 0.85 ? Blocks.GRAVEL : Blocks.SNOW_BLOCK;
            case DESERT -> top && roll < 0.45 ? Blocks.GRAVEL : roll < 0.45 ? Blocks.SAND : roll < 0.75 ? Blocks.SANDSTONE
                    : roll < 0.9 ? Blocks.SMOOTH_SANDSTONE : Blocks.GRAVEL;
            case BADLANDS -> top && roll < 0.25 ? Blocks.RED_SAND : roll < 0.85 ? BANDS[Math.floorMod(y, BANDS.length)]
                    : roll < 0.93 ? Blocks.RED_SANDSTONE : Blocks.COARSE_DIRT;
            // Termite clay: mud and dirt cemented hard.
            case SAVANNA -> roll < 0.3 ? Blocks.PACKED_MUD : roll < 0.5 ? Blocks.COARSE_DIRT : roll < 0.7 ? Blocks.DYED_TERRACOTTA.brown()
                    : roll < 0.85 ? Blocks.TERRACOTTA : Blocks.MUD_BRICKS;
            case JUNGLE -> top
                    ? roll < 0.5 ? Blocks.MOSS_BLOCK : roll < 0.7 ? Blocks.JUNGLE_LEAVES : roll < 0.85 ? Blocks.ROOTED_DIRT : Blocks.MOSSY_COBBLESTONE
                    : roll < 0.35 ? Blocks.MUD : roll < 0.6 ? Blocks.ROOTED_DIRT : roll < 0.8 ? ModDecor.ANTHILL_SOIL.get() : Blocks.MOSS_BLOCK;
            case SWAMP -> roll < 0.35 ? Blocks.MUD : roll < 0.6 ? Blocks.PACKED_MUD : roll < 0.75 ? Blocks.MUD_BRICKS : roll < 0.9 ? Blocks.CLAY
                    : Blocks.MUDDY_MANGROVE_ROOTS;
            case MUSHROOM -> top ? roll < 0.75 ? Blocks.MYCELIUM : Blocks.BROWN_MUSHROOM_BLOCK
                    : roll < 0.5 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.75 ? Blocks.DIRT : roll < 0.9 ? Blocks.MUSHROOM_STEM : Blocks.ROOTED_DIRT;
            case CHERRY -> top && roll < 0.35 ? Blocks.GRASS_BLOCK : roll < 0.5 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.75 ? Blocks.ROOTED_DIRT
                    : roll < 0.9 ? Blocks.COARSE_DIRT : Blocks.CALCITE;
            case NETHER -> roll < 0.5 ? Blocks.SOUL_SOIL : roll < 0.75 ? Blocks.NETHERRACK : roll < 0.9 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
            case END -> roll < 0.6 ? Blocks.END_STONE : roll < 0.85 ? Blocks.END_STONE_BRICKS : Blocks.PURPUR_BLOCK;
            case CRIMSON -> top ? roll < 0.45 ? Blocks.CRIMSON_NYLIUM : roll < 0.8 ? Blocks.NETHER_WART_BLOCK : roll < 0.93 ? Blocks.CRIMSON_HYPHAE
                    : Blocks.SHROOMLIGHT
                    : roll < 0.45 ? Blocks.NETHERRACK : roll < 0.75 ? Blocks.NETHER_WART_BLOCK : roll < 0.9 ? Blocks.CRIMSON_HYPHAE : Blocks.BLACKSTONE;
            case WARPED -> top ? roll < 0.45 ? Blocks.WARPED_NYLIUM : roll < 0.8 ? Blocks.WARPED_WART_BLOCK : roll < 0.93 ? Blocks.WARPED_HYPHAE
                    : Blocks.SHROOMLIGHT
                    : roll < 0.45 ? Blocks.NETHERRACK : roll < 0.75 ? Blocks.WARPED_WART_BLOCK : roll < 0.9 ? Blocks.WARPED_HYPHAE : Blocks.BLACKSTONE;
            // Bones stand out of the soul soil, like the valley's own fossils.
            case SOUL_VALLEY -> depth <= 2 ? roll < 0.75 ? Blocks.BONE_BLOCK : Blocks.SOUL_SOIL
                    : roll < 0.45 ? Blocks.SOUL_SOIL : roll < 0.7 ? Blocks.SOUL_SAND : roll < 0.88 ? Blocks.BONE_BLOCK : Blocks.BASALT;
            case BASALT -> roll < 0.4 ? Blocks.BASALT : roll < 0.65 ? Blocks.SMOOTH_BASALT : roll < 0.88 ? Blocks.BLACKSTONE
                    : top ? Blocks.MAGMA_BLOCK : Blocks.POLISHED_BASALT;
            // Purpur towers on end stone that chorus can root in.
            case END_HIGHLANDS -> depth <= 1 ? roll < 0.5 ? Blocks.END_STONE : roll < 0.8 ? Blocks.PURPUR_BLOCK : Blocks.PURPUR_PILLAR
                    : roll < 0.4 ? Blocks.PURPUR_PILLAR : roll < 0.7 ? Blocks.END_STONE_BRICKS : roll < 0.92 ? Blocks.PURPUR_BLOCK : Blocks.OBSIDIAN;
            case LOCAL -> top ? roll < 0.7 ? Blocks.GRASS_BLOCK : Blocks.DIRT
                    : roll < 0.45 ? Blocks.DIRT : roll < 0.75 ? ModDecor.ANTHILL_SOIL.get() : Blocks.STONE;
            // Fallen birch branches and their leaves over the soil.
            case BIRCH -> top ? roll < 0.3 ? Blocks.BIRCH_LEAVES : roll < 0.5 ? Blocks.PODZOL : roll < 0.75 ? Blocks.COARSE_DIRT
                    : roll < 0.9 ? ModDecor.ANTHILL_SOIL.get() : Blocks.BIRCH_WOOD
                    : roll < 0.4 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.65 ? Blocks.COARSE_DIRT : roll < 0.85 ? Blocks.ROOTED_DIRT : Blocks.BIRCH_WOOD;
            case DARK_FOREST -> top ? roll < 0.4 ? Blocks.DARK_OAK_LEAVES : roll < 0.65 ? Blocks.PODZOL : roll < 0.8 ? Blocks.MUD
                    : roll < 0.92 ? Blocks.DARK_OAK_WOOD : Blocks.BROWN_MUSHROOM_BLOCK
                    : roll < 0.4 ? Blocks.MUD : roll < 0.65 ? Blocks.PACKED_MUD : roll < 0.85 ? ModDecor.ANTHILL_SOIL.get() : Blocks.ROOTED_DIRT;
            // Turf the flowers root in.
            case FLOWER -> top ? roll < 0.55 ? Blocks.GRASS_BLOCK : roll < 0.8 ? Blocks.MOSS_BLOCK : roll < 0.92 ? Blocks.ROOTED_DIRT
                    : Blocks.FLOWERING_AZALEA_LEAVES
                    : roll < 0.45 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.7 ? Blocks.DIRT : roll < 0.9 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT;
            case PALE_GARDEN -> top ? roll < 0.6 ? Blocks.PALE_MOSS_BLOCK : roll < 0.8 ? Blocks.PALE_OAK_LEAVES : roll < 0.92 ? Blocks.PALE_OAK_WOOD
                    : Blocks.ROOTED_DIRT
                    : roll < 0.4 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.65 ? Blocks.ROOTED_DIRT : roll < 0.85 ? Blocks.PALE_MOSS_BLOCK
                    : Blocks.COARSE_DIRT;
            case BAMBOO -> top ? roll < 0.38 ? Blocks.PODZOL : roll < 0.58 ? Blocks.MOSS_BLOCK : roll < 0.9 ? Blocks.BAMBOO_BLOCK : Blocks.MUD
                    : roll < 0.4 ? Blocks.MUD : roll < 0.6 ? Blocks.PODZOL : roll < 0.8 ? ModDecor.ANTHILL_SOIL.get() : Blocks.BAMBOO_BLOCK;
            // Boulders gone green, as the old taiga's own.
            case OLD_GROWTH -> top ? roll < 0.4 ? Blocks.PODZOL : roll < 0.65 ? Blocks.MOSSY_COBBLESTONE : roll < 0.85 ? Blocks.COARSE_DIRT
                    : Blocks.SPRUCE_WOOD
                    : roll < 0.35 ? Blocks.PODZOL : roll < 0.6 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.8 ? Blocks.COARSE_DIRT
                    : Blocks.MOSSY_COBBLESTONE;
            case MOUNTAIN -> roll < 0.3 ? Blocks.COBBLESTONE : roll < 0.55 ? Blocks.STONE : roll < 0.72 ? Blocks.ANDESITE
                    : roll < 0.86 ? (top ? Blocks.GRAVEL : Blocks.TUFF) : Blocks.MOSSY_COBBLESTONE;
            case ICE -> top ? roll < 0.5 ? Blocks.PACKED_ICE : roll < 0.75 ? Blocks.SNOW_BLOCK : Blocks.BLUE_ICE
                    : roll < 0.5 ? Blocks.PACKED_ICE : roll < 0.75 ? Blocks.BLUE_ICE : roll < 0.9 ? Blocks.SNOW_BLOCK : ModDecor.ANTHILL_SOIL.get();
            // What the sea leaves behind: sand, shingle and dead coral.
            case BEACH -> top ? roll < 0.55 ? Blocks.SAND : roll < 0.75 ? Blocks.GRAVEL : roll < 0.9 ? Blocks.SANDSTONE
                    : Blocks.DEAD_BRAIN_CORAL_BLOCK
                    : roll < 0.5 ? Blocks.SAND : roll < 0.8 ? Blocks.SANDSTONE : roll < 0.92 ? Blocks.CLAY : Blocks.GRAVEL;
        };
        BlockState state = toned(block.defaultBlockState());
        // Leaves in a mound are part of it: they never decay.
        return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
    }

    public BlockState topping(double roll, boolean onMound) {
        return switch (this) {
            case SNOWY -> onMound || roll < 0.5 ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (roll * 10) % 3) : null;
            case CHERRY -> roll < (onMound ? 0.35 : 0.3)
                    ? Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + (int) (roll * 40) % 4)
                            .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue((int) (roll * 97) % 4))
                    : null;
            case MUSHROOM -> roll < (onMound ? 0.1 : 0.04) ? (roll < 0.05 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState() : null;
            case TAIGA -> !onMound && roll < 0.12 ? Blocks.FERN.defaultBlockState() : null;
            case JUNGLE -> !onMound && roll < 0.1 ? Blocks.FERN.defaultBlockState()
                    : onMound && roll < 0.05 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            case DESERT, BADLANDS -> !onMound && roll < 0.035 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case SAVANNA -> !onMound && roll < 0.05 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case NETHER -> onMound && roll < 0.06 ? Blocks.CRIMSON_ROOTS.defaultBlockState() : null;
            case BIRCH -> roll < 0.22 ? flowerBed(Blocks.WILDFLOWERS, roll) : !onMound && roll < 0.34 ? litter(roll) : null;
            case DARK_FOREST -> roll < (onMound ? 0.06 : 0.05) ? (roll < 0.02 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState()
                    : !onMound && roll < 0.17 ? litter(roll) : null;
            case FLOWER -> roll < (onMound ? 0.4 : 0.18) ? FLOWERS[(int) (roll * 1000) % FLOWERS.length].defaultBlockState() : null;
            case PALE_GARDEN -> onMound ? roll < 0.35 ? Blocks.PALE_MOSS_CARPET.defaultBlockState() : null
                    : roll < 0.05 ? Blocks.CLOSED_EYEBLOSSOM.defaultBlockState() : roll < 0.17 ? Blocks.PALE_MOSS_CARPET.defaultBlockState() : null;
            case BAMBOO -> roll < 0.07 ? Blocks.BAMBOO_SAPLING.defaultBlockState() : !onMound && roll < 0.15 ? Blocks.FERN.defaultBlockState() : null;
            case OLD_GROWTH -> roll < (onMound ? 0.08 : 0.12) ? Blocks.FERN.defaultBlockState()
                    : !onMound && roll < 0.16 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : null;
            case ICE -> onMound || roll < 0.5 ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (roll * 10) % 3) : null;
            case BEACH -> !onMound && roll < 0.03 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case CRIMSON -> roll < 0.08 ? Blocks.CRIMSON_ROOTS.defaultBlockState() : roll < 0.12 ? Blocks.CRIMSON_FUNGUS.defaultBlockState() : null;
            case WARPED -> roll < 0.08 ? Blocks.WARPED_ROOTS.defaultBlockState() : roll < 0.14 ? Blocks.NETHER_SPROUTS.defaultBlockState()
                    : roll < 0.18 ? Blocks.WARPED_FUNGUS.defaultBlockState() : null;
            case END_HIGHLANDS -> onMound && roll < 0.05 ? Blocks.CHORUS_FLOWER.defaultBlockState() : null;
            default -> null;
        };
    }

    /** Petals or wildflowers: one to four of them, turned any way. */
    private static BlockState flowerBed(Block block, double roll) {
        return block.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + (int) (roll * 400) % 4)
                .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue((int) (roll * 970) % 4));
    }

    private static BlockState litter(double roll) {
        return Blocks.LEAF_LITTER.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((int) (roll * 970) % 4));
    }

    public BlockState trail() {
        return switch (this) {
            case TEMPERATE, TAIGA, JUNGLE, CHERRY, MUSHROOM, BIRCH, DARK_FOREST, FLOWER, BAMBOO, OLD_GROWTH -> Blocks.DIRT_PATH.defaultBlockState();
            case SAVANNA -> Blocks.COARSE_DIRT.defaultBlockState();
            default -> null;
        };
    }

    // --- The nest ---

    public BlockState wall(double roll, boolean floor, AnthillLayout.RoomType room, int y) {
        if (floor && room == AnthillLayout.RoomType.QUEEN && roll < 0.6) {
            return (end() ? Blocks.PURPUR_BLOCK : ModDecor.ROYAL_CHITIN_BLOCK.get()).defaultBlockState();
        }
        if (floor && room == AnthillLayout.RoomType.ANTECHAMBER && roll < 0.35) {
            return (end() ? Blocks.PURPUR_BLOCK : ModDecor.CHITIN_BLOCK.get()).defaultBlockState();
        }
        if (floor && (room == AnthillLayout.RoomType.FUNGAL_GARDEN || room == AnthillLayout.RoomType.APHID_FARM) && !end() && roll < 0.5) {
            return (this == WARPED ? Blocks.WARPED_NYLIUM : nether() ? Blocks.CRIMSON_NYLIUM
                    : room == AnthillLayout.RoomType.APHID_FARM ? Blocks.MOSS_BLOCK : Blocks.MYCELIUM).defaultBlockState();
        }
        if (floor && (room == AnthillLayout.RoomType.ABANDONED || room == AnthillLayout.RoomType.MIDDEN) && roll < 0.35) {
            return (room == AnthillLayout.RoomType.MIDDEN && roll < 0.12 ? Blocks.BONE_BLOCK : Blocks.GRAVEL).defaultBlockState();
        }
        Block block = switch (this) {
            case TEMPERATE, CHERRY -> floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.5 ? Blocks.ROOTED_DIRT : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.45 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.6 ? Blocks.PACKED_MUD : roll < 0.85 ? Blocks.COARSE_DIRT
                    : this == CHERRY && roll < 0.93 ? Blocks.CALCITE : ModDecor.ANTHILL_SOIL.get();
            case TAIGA -> floor ? roll < 0.4 ? Blocks.PODZOL : roll < 0.75 ? Blocks.ROOTED_DIRT : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.4 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.6 ? Blocks.COARSE_DIRT : roll < 0.8 ? Blocks.MOSSY_COBBLESTONE
                    : Blocks.ROOTED_DIRT;
            case SNOWY -> floor ? roll < 0.4 ? Blocks.SNOW_BLOCK : roll < 0.7 ? Blocks.GRAVEL : ModDecor.PACKED_ANTHILL_SOIL.get()
                    : roll < 0.25 ? Blocks.PACKED_ICE : roll < 0.3 ? Blocks.BLUE_ICE : roll < 0.7 ? ModDecor.PACKED_ANTHILL_SOIL.get()
                    : roll < 0.85 ? Blocks.COARSE_DIRT : Blocks.TUFF;
            case DESERT -> floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.55 ? Blocks.SAND : Blocks.SANDSTONE
                    : roll < 0.45 ? Blocks.SANDSTONE : roll < 0.65 ? Blocks.SMOOTH_SANDSTONE : roll < 0.8 ? Blocks.CUT_SANDSTONE
                    : roll < 0.95 ? ModDecor.PACKED_ANTHILL_SOIL.get() : Blocks.CHISELED_SANDSTONE;
            case BADLANDS -> floor ? roll < 0.5 ? Blocks.RED_SAND : Blocks.RED_SANDSTONE
                    : roll < 0.65 ? BANDS[Math.floorMod(y, BANDS.length)] : roll < 0.85 ? Blocks.RED_SANDSTONE : ModDecor.PACKED_ANTHILL_SOIL.get();
            case SAVANNA -> floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.55 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT
                    : roll < 0.35 ? Blocks.PACKED_MUD : roll < 0.6 ? Blocks.MUD_BRICKS : roll < 0.8 ? Blocks.DYED_TERRACOTTA.brown()
                    : Blocks.TERRACOTTA;
            case JUNGLE -> floor ? roll < 0.45 ? Blocks.MOSS_BLOCK : roll < 0.75 ? Blocks.ROOTED_DIRT : Blocks.MUD
                    : roll < 0.25 ? Blocks.MOSSY_COBBLESTONE : roll < 0.5 ? Blocks.MOSS_BLOCK : roll < 0.7 ? Blocks.PACKED_MUD
                    : roll < 0.85 ? ModDecor.PACKED_ANTHILL_SOIL.get() : Blocks.MUD;
            case SWAMP -> floor ? roll < 0.5 ? Blocks.MUD : roll < 0.8 ? Blocks.PACKED_MUD : Blocks.CLAY
                    : roll < 0.4 ? Blocks.MUD_BRICKS : roll < 0.7 ? Blocks.PACKED_MUD : roll < 0.85 ? Blocks.CLAY : Blocks.MUDDY_MANGROVE_ROOTS;
            case MUSHROOM -> floor ? roll < 0.5 ? Blocks.MYCELIUM : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.4 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.6 ? Blocks.MUSHROOM_STEM : roll < 0.75 ? Blocks.BROWN_MUSHROOM_BLOCK
                    : roll < 0.78 ? Blocks.SHROOMLIGHT : Blocks.DIRT;
            case NETHER -> floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.6 ? Blocks.SOUL_SOIL : Blocks.SOUL_SAND
                    : roll < 0.45 ? Blocks.POLISHED_BLACKSTONE_BRICKS : roll < 0.8 ? Blocks.BLACKSTONE : Blocks.BASALT;
            case END -> floor ? roll < 0.7 ? Blocks.END_STONE : Blocks.END_STONE_BRICKS
                    : roll < 0.5 ? Blocks.END_STONE_BRICKS : roll < 0.85 ? Blocks.END_STONE : Blocks.PURPUR_BLOCK;
            case CRIMSON -> floor ? roll < 0.45 ? Blocks.CRIMSON_NYLIUM : roll < 0.8 ? Blocks.NETHERRACK : Blocks.SOUL_SOIL
                    : roll < 0.4 ? Blocks.NETHERRACK : roll < 0.62 ? Blocks.CRIMSON_HYPHAE : roll < 0.8 ? Blocks.NETHER_WART_BLOCK
                    : roll < 0.96 ? Blocks.BLACKSTONE : Blocks.SHROOMLIGHT;
            case WARPED -> floor ? roll < 0.45 ? Blocks.WARPED_NYLIUM : roll < 0.8 ? Blocks.NETHERRACK : Blocks.SOUL_SOIL
                    : roll < 0.4 ? Blocks.NETHERRACK : roll < 0.62 ? Blocks.WARPED_HYPHAE : roll < 0.8 ? Blocks.WARPED_WART_BLOCK
                    : roll < 0.96 ? Blocks.BLACKSTONE : Blocks.SHROOMLIGHT;
            case SOUL_VALLEY -> floor ? roll < 0.08 ? Blocks.BONE_BLOCK : roll < 0.55 ? Blocks.SOUL_SOIL : Blocks.SOUL_SAND
                    : roll < 0.35 ? Blocks.SOUL_SOIL : roll < 0.55 ? Blocks.BASALT : roll < 0.7 ? Blocks.POLISHED_BASALT : roll < 0.88 ? Blocks.BONE_BLOCK
                    : Blocks.BLACKSTONE;
            case BASALT -> floor ? roll < 0.5 ? Blocks.BASALT : roll < 0.8 ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT
                    : roll < 0.35 ? Blocks.SMOOTH_BASALT : roll < 0.6 ? Blocks.BASALT : roll < 0.8 ? Blocks.BLACKSTONE
                    : Blocks.POLISHED_BLACKSTONE_BRICKS;
            case END_HIGHLANDS -> floor ? roll < 0.6 ? Blocks.END_STONE : Blocks.PURPUR_BLOCK
                    : roll < 0.4 ? Blocks.PURPUR_PILLAR : roll < 0.65 ? Blocks.PURPUR_BLOCK : roll < 0.9 ? Blocks.END_STONE_BRICKS : Blocks.OBSIDIAN;
            case LOCAL -> floor ? roll < 0.5 ? ModDecor.ANTHILL_SOIL.get() : Blocks.DIRT
                    : roll < 0.5 ? Blocks.STONE : roll < 0.85 ? ModDecor.PACKED_ANTHILL_SOIL.get() : Blocks.DIRT;
            case BIRCH -> floor ? roll < 0.3 ? Blocks.PODZOL : roll < 0.65 ? Blocks.ROOTED_DIRT : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.45 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.6 ? Blocks.STRIPPED_BIRCH_WOOD : roll < 0.8 ? Blocks.COARSE_DIRT
                    : Blocks.PACKED_MUD;
            case DARK_FOREST -> floor ? roll < 0.4 ? Blocks.PODZOL : roll < 0.7 ? Blocks.MUD : Blocks.ROOTED_DIRT
                    : roll < 0.35 ? Blocks.PACKED_MUD : roll < 0.6 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.8 ? Blocks.DARK_OAK_WOOD
                    : Blocks.MOSSY_COBBLESTONE;
            case FLOWER -> floor ? roll < 0.4 ? Blocks.MOSS_BLOCK : roll < 0.7 ? Blocks.ROOTED_DIRT : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.45 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.65 ? Blocks.ROOTED_DIRT : roll < 0.85 ? Blocks.MOSS_BLOCK
                    : Blocks.COARSE_DIRT;
            // Pale wood, and the resin that seeps from it.
            case PALE_GARDEN -> floor ? roll < 0.5 ? Blocks.PALE_MOSS_BLOCK : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.4 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.62 ? Blocks.PALE_OAK_WOOD : roll < 0.8 ? Blocks.STRIPPED_PALE_OAK_WOOD
                    : roll < 0.94 ? Blocks.PALE_MOSS_BLOCK : Blocks.RESIN_BLOCK;
            case BAMBOO -> floor ? roll < 0.4 ? Blocks.PODZOL : roll < 0.7 ? Blocks.MUD : Blocks.MOSS_BLOCK
                    : roll < 0.35 ? Blocks.PACKED_MUD : roll < 0.6 ? Blocks.BAMBOO_BLOCK : roll < 0.75 ? Blocks.STRIPPED_BAMBOO_BLOCK
                    : ModDecor.PACKED_ANTHILL_SOIL.get();
            case OLD_GROWTH -> floor ? roll < 0.5 ? Blocks.PODZOL : roll < 0.8 ? Blocks.COARSE_DIRT : ModDecor.ANTHILL_SOIL.get()
                    : roll < 0.35 ? Blocks.MOSSY_COBBLESTONE : roll < 0.55 ? Blocks.COBBLESTONE : roll < 0.8 ? ModDecor.PACKED_ANTHILL_SOIL.get()
                    : Blocks.ROOTED_DIRT;
            case MOUNTAIN -> floor ? roll < 0.4 ? Blocks.STONE : roll < 0.7 ? Blocks.COBBLESTONE : ModDecor.PACKED_ANTHILL_SOIL.get()
                    : roll < 0.35 ? Blocks.STONE : roll < 0.55 ? Blocks.COBBLESTONE : roll < 0.7 ? Blocks.ANDESITE : roll < 0.85 ? Blocks.TUFF
                    : ModDecor.PACKED_ANTHILL_SOIL.get();
            case ICE -> floor ? roll < 0.5 ? Blocks.SNOW_BLOCK : roll < 0.8 ? Blocks.PACKED_ICE : ModDecor.PACKED_ANTHILL_SOIL.get()
                    : roll < 0.45 ? Blocks.PACKED_ICE : roll < 0.65 ? Blocks.BLUE_ICE : roll < 0.85 ? ModDecor.PACKED_ANTHILL_SOIL.get()
                    : Blocks.CALCITE;
            case BEACH -> floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.55 ? Blocks.SAND : Blocks.SANDSTONE
                    : roll < 0.35 ? Blocks.SANDSTONE : roll < 0.55 ? Blocks.CLAY : roll < 0.85 ? ModDecor.PACKED_ANTHILL_SOIL.get()
                    : roll < 0.93 ? Blocks.DEAD_BRAIN_CORAL_BLOCK : Blocks.DEAD_TUBE_CORAL_BLOCK;
        };
        return toned(block.defaultBlockState());
    }

    public BlockState ceiling(double roll) {
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
        return switch (this) {
            case TEMPERATE, TAIGA, CHERRY, SAVANNA, BIRCH, DARK_FOREST, FLOWER, OLD_GROWTH -> roll < 0.05 ? Blocks.HANGING_ROOTS.defaultBlockState() : null;
            case JUNGLE, SWAMP, BAMBOO -> roll < 0.05 ? Blocks.HANGING_ROOTS.defaultBlockState() : roll < 0.11 ? lichen : null;
            case PALE_GARDEN -> roll < 0.08 ? Blocks.PALE_HANGING_MOSS.defaultBlockState() : null;
            case CRIMSON -> roll < 0.07 ? Blocks.WEEPING_VINES.defaultBlockState() : null;
            // Stone drips: small stalactites.
            case MOUNTAIN -> roll < 0.04 ? Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN) : null;
            case MUSHROOM -> roll < 0.08 ? lichen : null;
            default -> null;
        };
    }

    public BlockState carpet(double roll) {
        return switch (this) {
            case JUNGLE -> roll < 0.18 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            case SNOWY, ICE -> roll < 0.15 ? Blocks.SNOW.defaultBlockState() : null;
            case FLOWER, BAMBOO -> roll < 0.08 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            case PALE_GARDEN -> roll < 0.15 ? Blocks.PALE_MOSS_CARPET.defaultBlockState() : null;
            case TAIGA -> roll < 0.06 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            default -> null;
        };
    }

    // --- Inside the rooms ---

    /** What the pillars of the halls are: a root of the trees above, or the rock the ants left standing. */
    public BlockState pillar() {
        Block block = switch (this) {
            case TAIGA, OLD_GROWTH, SNOWY -> Blocks.SPRUCE_WOOD;
            case BIRCH -> Blocks.BIRCH_WOOD;
            case DARK_FOREST -> Blocks.DARK_OAK_WOOD;
            case CHERRY -> Blocks.CHERRY_WOOD;
            case PALE_GARDEN -> Blocks.PALE_OAK_WOOD;
            case SAVANNA -> Blocks.ACACIA_WOOD;
            case JUNGLE -> Blocks.JUNGLE_WOOD;
            case BAMBOO -> Blocks.BAMBOO_BLOCK;
            case SWAMP -> Blocks.MANGROVE_WOOD;
            case MUSHROOM -> Blocks.MUSHROOM_STEM;
            case DESERT, BEACH -> Blocks.CUT_SANDSTONE;
            case BADLANDS -> Blocks.CUT_RED_SANDSTONE;
            case ICE -> Blocks.PACKED_ICE;
            case MOUNTAIN -> Blocks.DRIPSTONE_BLOCK;
            case NETHER, BASALT -> Blocks.POLISHED_BASALT;
            case CRIMSON -> Blocks.CRIMSON_STEM;
            case WARPED -> Blocks.WARPED_STEM;
            case SOUL_VALLEY -> Blocks.BONE_BLOCK;
            case END, END_HIGHLANDS -> Blocks.PURPUR_PILLAR;
            case TEMPERATE, FLOWER, LOCAL -> ModDecor.NEST_PILLAR.get();
        };
        return block.defaultBlockState();
    }

    /** The light set in walls. None in the End: its nests are lit by rods under the ceilings. */
    public BlockState lamp() {
        Block block = switch (this) {
            case MUSHROOM, CRIMSON, WARPED -> Blocks.SHROOMLIGHT;
            case NETHER, BASALT, SOUL_VALLEY -> Blocks.GLOWSTONE;
            case END, END_HIGHLANDS -> null;
            default -> ModDecor.PHEROMONE_LAMP.get();
        };
        return block == null ? null : block.defaultBlockState();
    }

    /** What a food store is heaped with. */
    public Block[] stores() {
        return switch (this) {
            case JUNGLE, BAMBOO -> new Block[] {Blocks.MELON, Blocks.HAY_BLOCK};
            case TAIGA, OLD_GROWTH, DARK_FOREST -> new Block[] {Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK};
            case MUSHROOM, SWAMP -> new Block[] {Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK};
            case BEACH -> new Block[] {Blocks.DRIED_KELP_BLOCK, Blocks.HAY_BLOCK};
            case NETHER, BASALT, CRIMSON -> new Block[] {Blocks.NETHER_WART_BLOCK};
            case WARPED -> new Block[] {Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK};
            case SOUL_VALLEY -> new Block[] {Blocks.NETHER_WART_BLOCK, Blocks.BONE_BLOCK};
            case END, END_HIGHLANDS -> new Block[] {ModDecor.FUNGAL_BLOCK.get()};
            default -> new Block[] {Blocks.HAY_BLOCK};
        };
    }

    /** What hangs from the ceilings of the rooms, if anything does. */
    public BlockState hanging() {
        Block block = switch (this) {
            case PALE_GARDEN -> Blocks.PALE_HANGING_MOSS;
            case NETHER, CRIMSON -> Blocks.WEEPING_VINES;
            case DESERT, BADLANDS, BEACH, SNOWY, ICE, MOUNTAIN, WARPED, SOUL_VALLEY, BASALT, END, END_HIGHLANDS -> null;
            default -> Blocks.HANGING_ROOTS;
        };
        return block == null ? null : block.defaultBlockState();
    }

    /** A small thing on a room's floor, if anything gathers there. */
    public BlockState floorLitter(double roll) {
        Block block = switch (this) {
            case TAIGA, OLD_GROWTH, DARK_FOREST, MUSHROOM -> roll < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM;
            case SNOWY, ICE -> Blocks.SNOW;
            case DESERT, BADLANDS, BEACH, SAVANNA -> Blocks.DEAD_BUSH;
            case PALE_GARDEN -> Blocks.PALE_MOSS_CARPET;
            case MOUNTAIN -> roll < 0.5 ? Blocks.POINTED_DRIPSTONE : Blocks.MOSS_CARPET;
            case NETHER, CRIMSON -> roll < 0.6 ? Blocks.CRIMSON_ROOTS : Blocks.CRIMSON_FUNGUS;
            case WARPED -> roll < 0.4 ? Blocks.WARPED_ROOTS : roll < 0.7 ? Blocks.NETHER_SPROUTS : Blocks.WARPED_FUNGUS;
            case SOUL_VALLEY, BASALT -> null;
            case END, END_HIGHLANDS -> Blocks.CHORUS_FLOWER;
            default -> roll < 0.5 ? Blocks.MOSS_CARPET : Blocks.BROWN_MUSHROOM;
        };
        // A chorus bud that has done growing: a live one would grow a tree through the room.
        return block == null ? null : block.defaultBlockState().trySetValue(BlockStateProperties.AGE_5, 5);
    }

    /** The block a midden's buried finds lie in (brushed out like an archaeology site). */
    public Block suspicious() {
        return this == DESERT || this == BADLANDS || this == BEACH ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL;
    }

    /** A number from 0 to 1 for a spot, the same whichever chunk asks. */
    static double hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
