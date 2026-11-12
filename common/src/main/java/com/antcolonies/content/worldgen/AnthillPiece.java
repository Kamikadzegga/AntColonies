package com.antcolonies.content.worldgen;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDecor;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModStructures;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.Tags;

/**
 * A whole anthill in one piece, built from its center (the ground's top block under the mound's peak): a mound with
 * a shaft down through it into a nest dug from an {@link AnthillLayout} plan (rooms of a dozen kinds joined by tunnels,
 * in one of seven layouts). Every choice comes from the plan's seed and the center, so each chunk builds its part of
 * the same anthill, and the Queen's chamber is always there and joined to the entrance.
 *
 * <p>Every anthill has exactly one Queen, spawned in her chamber by the chunk she stands in: an anthill Queen can't be
 * hurt and never despawns (see {@link WildAnt#isAnthillQueen}), so she is always there to be caught with Tweezers.
 *
 * <p>Three kinds: the Overworld's (earth, or sand in deserts and badlands), the Nether's (soul soil and blackstone, lava
 * walled off) and the End's (end stone and purpur). Pieces saved before the plans existed have no seed and build the
 * old fixed layout ({@link AnthillLayout.Style#CLASSIC}), so anthills half-generated in an old world finish the same.
 */
public class AnthillPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/giant_anthill"));
    public static final ResourceKey<LootTable> NETHER_LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/nether_anthill"));
    public static final ResourceKey<LootTable> END_LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/end_anthill"));
    public static final ResourceKey<LootTable> TREASURE = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/anthill_treasure"));
    public static final ResourceKey<LootTable> FOOD = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/anthill_food"));
    /** Mound radius and height, and how deep the old fixed nest went. */
    static final int RADIUS = 7;
    static final int HEIGHT = 5;
    static final int DEPTH = 18;

    /** Where an anthill stands: it decides its materials, its ants and its loot. */
    public enum Kind {
        OVERWORLD, NETHER, END;

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : OVERWORLD;
        }
    }

    private final BlockPos center;
    private final Kind kind;
    private final AnthillLayout.Style style;
    private final long seed;
    /** The plan, made again from the seed after loading (never saved). */
    private final AnthillLayout layout;

    /** An anthill of the old fixed layout (game tests, and pieces saved before layouts were planned). */
    public AnthillPiece(BlockPos center, boolean nether) {
        this(center, nether ? Kind.NETHER : Kind.OVERWORLD, AnthillLayout.Style.CLASSIC, center.asLong());
    }

    public AnthillPiece(BlockPos center, Kind kind, AnthillLayout.Style style, long seed) {
        this(center, kind, style, seed, AnthillLayout.create(style, seed));
    }

    private AnthillPiece(BlockPos center, Kind kind, AnthillLayout.Style style, long seed, AnthillLayout layout) {
        super(ModStructures.ANTHILL_PIECE.get(), 0, box(center, layout));
        this.center = center;
        this.kind = kind;
        this.style = style;
        this.seed = seed;
        this.layout = layout;
        setOrientation(null);
    }

    public AnthillPiece(CompoundTag tag) {
        super(ModStructures.ANTHILL_PIECE.get(), tag);
        this.center = new BlockPos(tag.getIntOr("CX", 0), tag.getIntOr("CY", 0), tag.getIntOr("CZ", 0));
        boolean nether = tag.getBooleanOr("Nether", false);
        this.kind = tag.getInt("Kind").map(Kind::byId).orElse(nether ? Kind.NETHER : Kind.OVERWORLD);
        // No style saved: an anthill planned before layouts existed.
        this.style = AnthillLayout.Style.byName(tag.getStringOr("Style", "classic"));
        this.seed = tag.getLongOr("Seed", center.asLong());
        this.layout = AnthillLayout.create(style, seed);
    }

    /**
     * Plans an anthill at {@code center}: a layout style from the seed, tried again with other styles (up to four) while
     * the plan would reach below {@code lowestY} (the world's bottom, the Nether's lava sea, the underside of an End
     * island). Falls back to the shallow old layout, so an anthill that may generate always has its Queen's chamber.
     */
    public static AnthillPiece plan(BlockPos center, Kind kind, long seed, int lowestY) {
        for (int attempt = 0; attempt < 4; attempt++) {
            long tried = seed + attempt * 0x9E3779B97F4A7C15L;
            AnthillLayout.Style style = styleFor(tried);
            AnthillLayout layout = AnthillLayout.create(style, tried);
            if (center.getY() + layout.minY() > lowestY) {
                return new AnthillPiece(center, kind, style, tried, layout);
            }
        }
        return new AnthillPiece(center, kind, AnthillLayout.Style.ABANDONED, seed, AnthillLayout.create(AnthillLayout.Style.ABANDONED, seed));
    }

    /** A plan's style picked from its seed (the same seed gives the same anthill). */
    public static AnthillLayout.Style styleFor(long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x2545F4914F6CDD1DL);
        return AnthillLayout.Style.RANDOM[random.nextInt(AnthillLayout.Style.RANDOM.length)];
    }

    private static BoundingBox box(BlockPos center, AnthillLayout layout) {
        return new BoundingBox(center.getX() + Math.min(-RADIUS - 2, layout.minX()), center.getY() + layout.minY() - 1,
                center.getZ() + Math.min(-RADIUS - 2, layout.minZ()), center.getX() + Math.max(RADIUS + 2, layout.maxX()),
                center.getY() + HEIGHT + 2, center.getZ() + Math.max(RADIUS + 2, layout.maxZ()));
    }

    public AnthillLayout layout() {
        return layout;
    }

    public BlockPos center() {
        return center;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", center.getX());
        tag.putInt("CY", center.getY());
        tag.putInt("CZ", center.getZ());
        tag.putBoolean("Nether", kind == Kind.NETHER);
        tag.putInt("Kind", kind.ordinal());
        tag.putString("Style", style.getSerializedName());
        tag.putLong("Seed", seed);
    }

    /** The mound's height over a column (0 outside it): a rounded cone, a little uneven. */
    private int moundHeight(long hashSeed, int x, int z) {
        double distance = Math.sqrt(x * x + z * z) + (hash(hashSeed, x, 0, z) - 0.5) * 1.4;
        if (distance >= RADIUS) {
            return 0;
        }
        double f = distance / RADIUS;
        return (int) Math.round(HEIGHT * (1 - f * f));
    }

    // --- Building ---

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        long hashSeed = style == AnthillLayout.Style.CLASSIC ? level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L : seed;
        Soil soil = kind == Kind.NETHER ? Soil.NETHER : kind == Kind.END ? Soil.END : Soil.of(level, center);
        for (int x = Math.max(boundingBox.minX(), chunkBB.minX()); x <= Math.min(boundingBox.maxX(), chunkBB.maxX()); x++) {
            for (int z = Math.max(boundingBox.minZ(), chunkBB.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBB.maxZ()); z++) {
                int rx = x - center.getX();
                int rz = z - center.getZ();
                int mound = moundHeight(hashSeed, rx, rz);
                for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunkBB.isInside(pos) || level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    int ry = y - center.getY();
                    boolean carved = layout.carved(rx, ry, rz);
                    if (ry > 0) {
                        if (ry <= mound) {
                            level.setBlock(pos, carved ? Blocks.AIR.defaultBlockState() : soil.mound(hash(hashSeed, x, y, z)), Block.UPDATE_CLIENTS);
                        } else if (mound > 0 && ry <= mound + 2 && !level.getBlockState(pos).isAir() && level.getBlockState(pos).canBeReplaced()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); // grass left floating
                        }
                        continue;
                    }
                    BlockState here = level.getBlockState(pos);
                    if (here.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (carved) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    if (layout.shell(rx, ry, rz)) {
                        // Ant-dug walls; caves and water stay as they are. Lava is walled off too, so it never pours in.
                        boolean wallOff = here.getFluidState().isEmpty() || here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
                        if ((!here.isAir() || kind == Kind.END) && wallOff) {
                            boolean floor = layout.carved(rx, ry + 1, rz);
                            AnthillLayout.Room room = floor ? layout.roomAt(rx, ry + 1, rz) : null;
                            level.setBlock(pos, soil.wall(hash(hashSeed, x, y, z), floor, room == null ? null : room.type()), Block.UPDATE_CLIENTS);
                        }
                    } else if (mound > 0 && ry >= -2 && here.canBeReplaced() && here.getFluidState().isEmpty()) {
                        level.setBlock(pos, soil.mound(hash(hashSeed, x, y, z)), Block.UPDATE_CLIENTS); // fill dips under the mound
                    }
                }
            }
        }
        furnish(level, chunkBB, hashSeed);
    }

    /** What lies in each room, and who lives there; each only by the chunk it stands in. */
    private void furnish(WorldGenLevel level, BoundingBox chunkBB, long hashSeed) {
        RandomSource random = RandomSource.create(hashSeed + 1);
        Identifier species = switch (kind) {
            case NETHER -> netherSpecies(level);
            case END -> AntColonies.id("chorus");
            case OVERWORLD -> WildAnt.speciesFor(level, center, random);
        };
        ResourceKey<LootTable> loot = switch (kind) {
            case NETHER -> NETHER_LOOT;
            case END -> END_LOOT;
            case OVERWORLD -> LOOT;
        };
        AnthillLayout.Room queen = layout.queen();
        BlockPos home = floor(queen, 0, 0);
        boolean abandoned = style == AnthillLayout.Style.ABANDONED;
        for (AnthillLayout.Room room : layout.rooms()) {
            RandomSource roomRandom = RandomSource.create(hashSeed ^ (long) (room.x() * 31 + room.y() * 977 + room.z() * 7919));
            switch (room.type()) {
                case QUEEN -> {
                    // The Queen, her nest, a barrel of the colony's riches, and her guard (an abandoned nest's Queen is alone).
                    container(level, chunkBB, floor(room, -2, 1), Blocks.BARREL.defaultBlockState(), loot, roomRandom);
                    var nestBlock = AntColonies.MODID.equals(species.getNamespace()) ? ModBlocks.NESTS.get(species.getPath()) : null;
                    BlockPos nest = floor(room, 3, 0);
                    if (nestBlock != null && chunkBB.isInside(nest)) {
                        level.setBlock(nest, nestBlock.get().defaultBlockState().setValue(WildNestBlock.RECOVERING, false), Block.UPDATE_CLIENTS);
                    }
                    place(level, chunkBB, floor(room, -3, -1), kind == Kind.OVERWORLD ? ModDecor.PHEROMONE_LAMP.get().defaultBlockState()
                            : kind == Kind.NETHER ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.END_ROD.defaultBlockState());
                    spawn(level, chunkBB, home, species, Caste.QUEEN, home);
                    if (!abandoned) {
                        int[][] soldiers = {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, -1}};
                        for (int[] spot : soldiers) {
                            spawn(level, chunkBB, floor(room, spot[0], spot[1]), species, Caste.SOLDIER, home);
                        }
                    }
                }
                case NURSERY, EGG_STORAGE, LARVAL -> {
                    // Eggs and larvae in piles on the floor; nurses among them.
                    int piles = room.type() == AnthillLayout.RoomType.NURSERY ? 4 : 5;
                    for (int i = 0; i < piles; i++) {
                        boolean eggs = room.type() == AnthillLayout.RoomType.EGG_STORAGE || room.type() == AnthillLayout.RoomType.NURSERY && i % 2 == 0;
                        place(level, chunkBB, floor(room, roomRandom.nextInt(5) - 2, roomRandom.nextInt(5) - 2),
                                (eggs ? ModDecor.ANT_EGG_CLUSTER : ModDecor.LARVA_CLUSTER).get().defaultBlockState());
                    }
                    spawn(level, chunkBB, floor(room, 1, 1), species, Caste.WORKER, home);
                }
                case FOOD_STORAGE -> {
                    container(level, chunkBB, floor(room, 1, 0), Blocks.BARREL.defaultBlockState(), FOOD, roomRandom);
                    spawn(level, chunkBB, floor(room, -1, 1), species, Caste.WORKER, home);
                }
                case FUNGAL_GARDEN -> {
                    // A fungus garden: fungal beds on the floor, mushrooms on them, glowing fungus in the walls' light.
                    for (int i = 0; i < 6; i++) {
                        BlockPos bed = floor(room, roomRandom.nextInt(5) - 2, roomRandom.nextInt(5) - 2).below();
                        if (chunkBB.isInside(bed) && !level.getBlockState(bed).isAir()) {
                            level.setBlock(bed, (i == 0 ? ModDecor.GLOWING_FUNGAL_BLOCK : ModDecor.FUNGAL_BLOCK).get().defaultBlockState(), Block.UPDATE_CLIENTS);
                            BlockState plant = kind == Kind.NETHER ? (i % 2 == 0 ? Blocks.CRIMSON_FUNGUS : Blocks.WARPED_FUNGUS).defaultBlockState()
                                    : (i % 2 == 0 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState();
                            if (kind != Kind.END) {
                                place(level, chunkBB, bed.above(), plant);
                            }
                        }
                    }
                    spawn(level, chunkBB, floor(room, 0, 0), species, Caste.WORKER, home);
                }
                case BARRACKS -> {
                    if (!abandoned) {
                        for (int i = 0; i < 3; i++) {
                            spawn(level, chunkBB, floor(room, i - 1, roomRandom.nextInt(3) - 1), species, Caste.SOLDIER, home);
                        }
                    }
                    place(level, chunkBB, floor(room, 2, 1), Blocks.BONE_BLOCK.defaultBlockState());
                }
                case CHITIN_STORAGE -> {
                    for (int i = 0; i < 3; i++) {
                        int dx = roomRandom.nextInt(5) - 2;
                        int dz = roomRandom.nextInt(3) - 1;
                        if (dx != 0 || dz != 1) { // never on the chest's spot
                            place(level, chunkBB, floor(room, dx, dz), ModDecor.CHITIN_BLOCK.get().defaultBlockState());
                        }
                    }
                    container(level, chunkBB, floor(room, 0, 1), Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(roomRandom)), loot, roomRandom);
                }
                case TREASURE -> {
                    container(level, chunkBB, floor(room, 0, 0), Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(roomRandom)), TREASURE, roomRandom);
                    place(level, chunkBB, floor(room, 1, 1), ModDecor.ROYAL_CHITIN_BLOCK.get().defaultBlockState());
                }
                case RARE -> {
                    // Where the ants dug into something precious: a few ore and crystal blocks left in the walls and floor.
                    BlockState[] finds = switch (kind) {
                        case NETHER -> new BlockState[] {Blocks.NETHER_GOLD_ORE.defaultBlockState(), Blocks.NETHER_QUARTZ_ORE.defaultBlockState(),
                                Blocks.GILDED_BLACKSTONE.defaultBlockState()};
                        case END -> new BlockState[] {Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                                ModDecor.AMBER_BLOCK.get().defaultBlockState()};
                        case OVERWORLD -> new BlockState[] {Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
                                ModDecor.AMBER_BLOCK.get().defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState()};
                    };
                    for (int i = 0; i < 3; i++) {
                        BlockPos spot = floor(room, roomRandom.nextInt(3) - 1, roomRandom.nextInt(3) - 1).below();
                        if (chunkBB.isInside(spot)) {
                            level.setBlock(spot, finds[roomRandom.nextInt(finds.length)], Block.UPDATE_CLIENTS);
                        }
                    }
                }
                case ABANDONED -> {
                    // Left long ago: cobwebs, bones and fallen earth.
                    place(level, chunkBB, floor(room, 1, 0), Blocks.COBWEB.defaultBlockState());
                    place(level, chunkBB, floor(room, -1, 1), Blocks.BONE_BLOCK.defaultBlockState());
                    place(level, chunkBB, floor(room, 0, -1), kind == Kind.OVERWORLD ? ModDecor.ANTHILL_SOIL.get().defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState());
                }
                case GALLERY, HALL -> {
                    spawn(level, chunkBB, floor(room, 0, 0), species, Caste.SOLDIER, home);
                    spawn(level, chunkBB, floor(room, 1, 1), species, Caste.WORKER, home);
                    spawn(level, chunkBB, floor(room, -1, 0), species, Caste.WORKER, home);
                }
            }
        }
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, BlockState state) {
        if (chunkBB.isInside(pos) && level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void container(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, BlockState state, ResourceKey<LootTable> loot, RandomSource random) {
        if (chunkBB.isInside(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
        }
    }

    /** A Nether anthill's ants: Warped Ants in the fungus forests, Fire Ants elsewhere. */
    private Identifier netherSpecies(WorldGenLevel level) {
        var biome = level.getBiome(center);
        boolean forest = biome.is(net.minecraft.world.level.biome.Biomes.WARPED_FOREST) || biome.is(net.minecraft.world.level.biome.Biomes.CRIMSON_FOREST);
        return AntColonies.id(forest ? "warped" : "fire");
    }

    /** The lowest dug-out spot of a room's column (offset from its center), in the world. */
    public BlockPos floor(AnthillLayout.Room room, int dx, int dz) {
        int x = (int) Math.round(room.x()) + dx;
        int z = (int) Math.round(room.z()) + dz;
        int y = (int) Math.round(room.y());
        while (layout.carved(x, y - 1, z) && y > room.y() - room.ry() - 1) {
            y--;
        }
        if (!layout.carved(x, y, z)) {
            // Off the room's floor: fall back to its middle.
            x = (int) Math.round(room.x());
            z = (int) Math.round(room.z());
            y = (int) Math.round(room.y());
            while (layout.carved(x, y - 1, z) && y > room.y() - room.ry() - 1) {
                y--;
            }
        }
        return new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z);
    }

    private static void spawn(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, Identifier species, Caste caste, BlockPos home) {
        if (!chunkBB.isInside(pos)) {
            return;
        }
        WildAnt ant = ModEntities.WILD_ANT.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (ant == null) {
            return;
        }
        ant.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        ant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        ant.setSpecies(species);
        ant.setCaste(caste);
        ant.setGuardian(true);
        ant.setPersistenceRequired();
        ant.setHomeTo(home, 24);
        level.addFreshEntityWithPassengers(ant);
    }

    /** A number from 0 to 1 for a spot, the same whichever chunk asks. */
    private static double hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The anthill's earth: anthill soil (sandy in deserts and badlands), the Nether's or the End's. */
    private enum Soil {
        EARTH, DESERT, BADLANDS, NETHER, END;

        static Soil of(WorldGenLevel level, BlockPos pos) {
            var biome = level.getBiome(pos);
            return biome.is(BiomeTags.IS_BADLANDS) ? BADLANDS : biome.is(Tags.Biomes.IS_DESERT) ? DESERT : EARTH;
        }

        BlockState mound(double roll) {
            Block block = switch (this) {
                case EARTH -> roll < 0.45 ? ModDecor.ANTHILL_SOIL.get() : roll < 0.7 ? Blocks.COARSE_DIRT : roll < 0.9 ? Blocks.ROOTED_DIRT : Blocks.GRAVEL;
                case DESERT -> roll < 0.5 ? Blocks.SAND : roll < 0.75 ? Blocks.SANDSTONE : roll < 0.9 ? ModDecor.ANTHILL_SOIL.get() : Blocks.GRAVEL;
                case BADLANDS -> roll < 0.5 ? Blocks.RED_SAND : roll < 0.75 ? Blocks.TERRACOTTA : roll < 0.9 ? ModDecor.ANTHILL_SOIL.get() : Blocks.GRAVEL;
                case NETHER -> roll < 0.5 ? Blocks.SOUL_SOIL : roll < 0.75 ? Blocks.NETHERRACK : roll < 0.9 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
                case END -> roll < 0.6 ? Blocks.END_STONE : roll < 0.85 ? Blocks.END_STONE_BRICKS : Blocks.PURPUR_BLOCK;
            };
            return block.defaultBlockState();
        }

        /** Tunnel walls: earth the ants packed hard, with a softer floor (a little special in some rooms). */
        BlockState wall(double roll, boolean floor, AnthillLayout.RoomType room) {
            if (floor && room == AnthillLayout.RoomType.QUEEN && roll < 0.6) {
                return (this == END ? Blocks.PURPUR_BLOCK : ModDecor.ROYAL_CHITIN_BLOCK.get()).defaultBlockState();
            }
            if (floor && room == AnthillLayout.RoomType.FUNGAL_GARDEN && this != END && roll < 0.5) {
                return (this == NETHER ? Blocks.CRIMSON_NYLIUM : Blocks.MYCELIUM).defaultBlockState();
            }
            if (floor && room == AnthillLayout.RoomType.ABANDONED && roll < 0.35) {
                return Blocks.GRAVEL.defaultBlockState();
            }
            return switch (this) {
                case NETHER -> (floor ? roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.6 ? Blocks.SOUL_SOIL : Blocks.SOUL_SAND
                        : roll < 0.45 ? Blocks.POLISHED_BLACKSTONE_BRICKS : roll < 0.8 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
                case END -> (floor ? roll < 0.7 ? Blocks.END_STONE : Blocks.END_STONE_BRICKS
                        : roll < 0.5 ? Blocks.END_STONE_BRICKS : roll < 0.85 ? Blocks.END_STONE : Blocks.PURPUR_BLOCK).defaultBlockState();
                default -> {
                    if (floor) {
                        Block soft = this == DESERT ? Blocks.SAND : this == BADLANDS ? Blocks.RED_SAND : ModDecor.ANTHILL_SOIL.get();
                        yield (roll < 0.06 ? Blocks.BONE_BLOCK : roll < 0.5 ? Blocks.ROOTED_DIRT : soft).defaultBlockState();
                    }
                    yield (roll < 0.45 ? ModDecor.PACKED_ANTHILL_SOIL.get() : roll < 0.6 ? Blocks.PACKED_MUD : roll < 0.85 ? Blocks.COARSE_DIRT
                            : ModDecor.ANTHILL_SOIL.get()).defaultBlockState();
                }
            };
        }
    }
}
