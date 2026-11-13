package com.antcolonies.content.worldgen;

import java.util.Locale;

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
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * What an anthill looks like where it stands: its mound's shape and earth, its tunnels' walls and floors, and what grows
 * on and around it. Overworld anthills take theirs from the biome at their center ({@link #of}); the Nether's and the
 * End's have their own. Pieces planned before themes existed keep the old look ({@code AnthillPiece.Soil}).
 *
 * <p>Shapes: a cone (plains, forests, mushroom fields, cherry groves), the wood ants' tall rounded thatch dome (taiga),
 * a snow-capped cone (snowy biomes), the harvester ants' crater ringed with pebbles (desert), termite cathedrals of
 * spires (savanna, banded terracotta in the badlands), a low mossy mound (jungle) and a squat mud mound (swamps).
 */
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
    NETHER(Shape.CONE),
    END(Shape.CONE);

    /** The badlands' terracotta bands, by height. */
    private static final Block[] BANDS = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.TERRACOTTA,
            Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA};

    /** A mound's outline: radius and height of its body (spires rise above the body). */
    public enum Shape {
        CONE(7, 5), DOME(8, 7), CRATER(7, 3), SPIRES(6, 3), MOSSY(8, 4), LOW(6, 3);

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

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AnthillTheme byName(String name) {
        for (AnthillTheme theme : values()) {
            if (theme.getSerializedName().equals(name)) {
                return theme;
            }
        }
        return TEMPERATE;
    }

    /** An Overworld anthill's theme from the biome at its center. */
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
        if (biome.is(Tags.Biomes.IS_SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return SWAMP;
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return SAVANNA;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY)) {
            return SNOWY;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return TAIGA;
        }
        if (biome.is(Biomes.CHERRY_GROVE)) {
            return CHERRY;
        }
        return TEMPERATE;
    }

    // --- The mound ---

    /** How far the mound and what grows round it reach from the center. */
    public int reach() {
        return shape.radius + (trail() != null ? 12 : 3);
    }

    /** The highest the mound rises over the center's ground (spires included). */
    public int maxHeight() {
        return shape == Shape.SPIRES ? 14 : shape.height;
    }

    /** The mound's height over a column (0 outside it), the same whichever chunk asks. */
    public int moundHeight(long seed, int x, int z) {
        double noise = (hash(seed, x, 0, z) - 0.5);
        double distance = Math.sqrt(x * x + z * z) + noise * (shape == Shape.MOSSY ? 2.4 : 1.4);
        double f = distance / shape.radius;
        double body = switch (shape) {
            case CONE, LOW -> f >= 1 ? 0 : shape.height * (1 - f * f);
            case DOME -> f >= 1 ? 0 : shape.height * Math.sqrt(1 - f * f);
            case MOSSY -> f >= 1 ? 0 : shape.height * (1 - Math.pow(f, 1.5));
            // A ring of dug-out earth round the entrance, pebbles piled on it.
            case CRATER -> f >= 1.15 ? 0 : shape.height * Math.exp(-Math.pow((f - 0.55) / 0.24, 2));
            case SPIRES -> f >= 1 ? 0 : shape.height * (1 - f * f);
        };
        int height = (int) Math.round(body);
        if (shape == Shape.SPIRES) {
            height = Math.max(height, spires(seed, x, z));
        }
        return Math.min(height, maxHeight());
    }

    /**
     * The termite spires over a column: three to five narrow towers round the entrance (never over it), each tapering to
     * a point, 7 to 13 blocks high.
     */
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

    /**
     * A block of the mound. {@code depth} is how far under the mound's surface it lies (0: the top block of its column);
     * {@code y} is its height in the world (the badlands' bands follow it).
     */
    public BlockState mound(double roll, int depth, int y) {
        boolean top = depth == 0;
        Block block = switch (this) {
            case TEMPERATE -> top && roll < 0.2 ? Blocks.PODZOL : roll < 0.45 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.7 ? Blocks.COARSE_DIRT
                    : roll < 0.9 ? Blocks.ROOTED_DIRT : Blocks.GRAVEL;
            // Wood ants thatch their domes with needles and twigs.
            case TAIGA -> top ? roll < 0.45 ? Blocks.SPRUCE_LEAVES : roll < 0.75 ? Blocks.PODZOL : roll < 0.9 ? Blocks.COARSE_DIRT : Blocks.SPRUCE_WOOD
                    : roll < 0.35 ? Blocks.PODZOL : roll < 0.6 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.8 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT;
            case SNOWY -> top ? roll < 0.8 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE
                    : roll < 0.4 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.65 ? Blocks.COARSE_DIRT : roll < 0.85 ? Blocks.GRAVEL : Blocks.SNOW_BLOCK;
            case DESERT -> top && roll < 0.45 ? Blocks.GRAVEL : roll < 0.45 ? Blocks.SAND : roll < 0.75 ? Blocks.SANDSTONE
                    : roll < 0.9 ? Blocks.SMOOTH_SANDSTONE : Blocks.GRAVEL;
            case BADLANDS -> top && roll < 0.25 ? Blocks.RED_SAND : roll < 0.85 ? BANDS[Math.floorMod(y, BANDS.length)]
                    : roll < 0.93 ? Blocks.RED_SANDSTONE : Blocks.COARSE_DIRT;
            // Termite clay: mud and dirt cemented hard.
            case SAVANNA -> roll < 0.3 ? Blocks.PACKED_MUD : roll < 0.5 ? Blocks.COARSE_DIRT : roll < 0.7 ? Blocks.BROWN_TERRACOTTA
                    : roll < 0.85 ? Blocks.TERRACOTTA : Blocks.MUD_BRICKS;
            case JUNGLE -> top ? roll < 0.5 ? Blocks.MOSS_BLOCK : roll < 0.7 ? Blocks.JUNGLE_LEAVES : roll < 0.85 ? Blocks.ROOTED_DIRT : Blocks.MOSSY_COBBLESTONE
                    : roll < 0.35 ? Blocks.MUD : roll < 0.6 ? Blocks.ROOTED_DIRT : roll < 0.8 ? ModDecor.ANTHILL_SOIL.get() : Blocks.MOSS_BLOCK;
            case SWAMP -> roll < 0.35 ? Blocks.MUD : roll < 0.6 ? Blocks.PACKED_MUD : roll < 0.75 ? Blocks.MUD_BRICKS : roll < 0.9 ? Blocks.CLAY
                    : Blocks.MUDDY_MANGROVE_ROOTS;
            case MUSHROOM -> top ? roll < 0.75 ? Blocks.MYCELIUM : Blocks.BROWN_MUSHROOM_BLOCK
                    : roll < 0.5 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.75 ? Blocks.DIRT : roll < 0.9 ? Blocks.MUSHROOM_STEM : Blocks.ROOTED_DIRT;
            case CHERRY -> top && roll < 0.35 ? Blocks.GRASS_BLOCK : roll < 0.5 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.75 ? Blocks.ROOTED_DIRT
                    : roll < 0.9 ? Blocks.COARSE_DIRT : Blocks.CALCITE;
            case NETHER -> roll < 0.5 ? Blocks.SOUL_SOIL : roll < 0.75 ? Blocks.NETHERRACK : roll < 0.9 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
            case END -> roll < 0.6 ? Blocks.END_STONE : roll < 0.85 ? Blocks.END_STONE_BRICKS : Blocks.PURPUR_BLOCK;
        };
        BlockState state = block.defaultBlockState();
        // Leaves in a mound are part of it: they never decay.
        return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
    }

    /** What lies on top of the mound (or round its foot), or null for nothing: snow, petals, mushrooms, dead bushes. */
    public BlockState topping(double roll, boolean onMound) {
        return switch (this) {
            case SNOWY -> onMound || roll < 0.5 ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (roll * 10) % 3) : null;
            case CHERRY -> roll < (onMound ? 0.35 : 0.3) ? Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + (int) (roll * 40) % 4)
                    .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue((int) (roll * 97) % 4)) : null;
            case MUSHROOM -> roll < (onMound ? 0.1 : 0.04) ? (roll < 0.05 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState() : null;
            case TAIGA -> !onMound && roll < 0.12 ? Blocks.FERN.defaultBlockState() : null;
            case JUNGLE -> !onMound && roll < 0.1 ? Blocks.FERN.defaultBlockState() : onMound && roll < 0.05 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            case DESERT, BADLANDS -> !onMound && roll < 0.035 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case SAVANNA -> !onMound && roll < 0.05 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case NETHER -> onMound && roll < 0.06 ? Blocks.CRIMSON_ROOTS.defaultBlockState() : null;
            default -> null;
        };
    }

    /** The block the ants' foraging trails wear into the ground round the mound, or null where none show. */
    public BlockState trail() {
        return switch (this) {
            case TEMPERATE, TAIGA, JUNGLE, CHERRY, MUSHROOM -> Blocks.DIRT_PATH.defaultBlockState();
            case SAVANNA -> Blocks.COARSE_DIRT.defaultBlockState();
            default -> null;
        };
    }

    // --- The nest ---

    /** Tunnel walls: earth the ants packed hard, with a softer floor (a little special in some rooms). */
    public BlockState wall(double roll, boolean floor, AnthillLayout.RoomType room, int y) {
        if (floor && room == AnthillLayout.RoomType.QUEEN && roll < 0.6) {
            return (this == END ? Blocks.PURPUR_BLOCK : ModDecor.ROYAL_CHITIN_BLOCK.get()).defaultBlockState();
        }
        if (floor && room == AnthillLayout.RoomType.ANTECHAMBER && roll < 0.35) {
            return (this == END ? Blocks.PURPUR_BLOCK : ModDecor.CHITIN_BLOCK.get()).defaultBlockState();
        }
        if (floor && (room == AnthillLayout.RoomType.FUNGAL_GARDEN || room == AnthillLayout.RoomType.APHID_FARM) && this != END && roll < 0.5) {
            return (this == NETHER ? Blocks.CRIMSON_NYLIUM : room == AnthillLayout.RoomType.APHID_FARM ? Blocks.MOSS_BLOCK : Blocks.MYCELIUM).defaultBlockState();
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
                    : roll < 0.35 ? Blocks.PACKED_MUD : roll < 0.6 ? Blocks.MUD_BRICKS : roll < 0.8 ? Blocks.BROWN_TERRACOTTA : Blocks.TERRACOTTA;
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
        };
        return block.defaultBlockState();
    }

    /** What hangs from a tunnel's ceiling here, or null: roots, and glowing lichen where it's damp. */
    public BlockState ceiling(double roll) {
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
        return switch (this) {
            case TEMPERATE, TAIGA, CHERRY, SAVANNA -> roll < 0.05 ? Blocks.HANGING_ROOTS.defaultBlockState() : null;
            case JUNGLE, SWAMP -> roll < 0.05 ? Blocks.HANGING_ROOTS.defaultBlockState() : roll < 0.11 ? lichen : null;
            case MUSHROOM -> roll < 0.08 ? lichen : null;
            default -> null;
        };
    }

    /** What lies on a room's floor here, or null: moss in the jungle, snow in the cold. */
    public BlockState carpet(double roll) {
        return switch (this) {
            case JUNGLE -> roll < 0.18 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            case SNOWY -> roll < 0.15 ? Blocks.SNOW.defaultBlockState() : null;
            case TAIGA -> roll < 0.06 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
            default -> null;
        };
    }

    /** The block a midden's buried finds lie in (brushed out like an archaeology site). */
    public Block suspicious() {
        return this == DESERT || this == BADLANDS ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL;
    }

    /** A number from 0 to 1 for a spot, the same whichever chunk asks. */
    static double hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
