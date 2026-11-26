package com.antcolonies.content.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.AnthillSoilBlock;
import com.antcolonies.content.block.BroodClusterBlock;
import com.antcolonies.content.block.SoilTone;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.blockentity.BroodClusterBlockEntity;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDecor;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModStructures;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * A whole anthill in one piece: a mound over a nest dug from an {@link AnthillLayout} plan. Every choice comes from its
 * seed, so each chunk builds its part of the same anthill.
 */
public class AnthillPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/giant_anthill"));
    public static final ResourceKey<LootTable> NETHER_LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/nether_anthill"));
    public static final ResourceKey<LootTable> END_LOOT = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/end_anthill"));
    public static final ResourceKey<LootTable> TREASURE = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/anthill_treasure"));
    public static final ResourceKey<LootTable> FOOD = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("chests/anthill_food"));
    public static final ResourceKey<LootTable> MIDDEN = ResourceKey.create(Registries.LOOT_TABLE, AntColonies.id("archaeology/anthill_midden"));
    /** Every loot table an anthill's containers are filled from: opening one of them raises the anthill's alarm. */
    public static final Set<ResourceKey<LootTable>> CONTAINER_LOOT = Set.of(LOOT, NETHER_LOOT, END_LOOT, TREASURE, FOOD);
    /** Mound radius and height, and how deep the old fixed nest went. */
    static final int RADIUS = 7;
    static final int HEIGHT = 5;
    static final int DEPTH = 18;

    /** Where an anthill stands: it decides its ants and its loot. */
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
    /** How it looks; null for a piece planned before themes (the old look). */
    private final @Nullable AnthillTheme theme;
    /** The rock of another mod's world, for the LOCAL look; null in the base game's own. */
    private final @Nullable Block rock;
    /** The plan, made again from the seed after loading (never saved). */
    private final AnthillLayout layout;
    /** The columns the ants' trails wear into the ground (relative x, z packed), made from the seed. */
    private volatile @Nullable Set<Long> trails;

    /** An anthill of the old fixed layout (game tests, and pieces saved before layouts were planned). */
    public AnthillPiece(BlockPos center, boolean nether) {
        this(center, nether ? Kind.NETHER : Kind.OVERWORLD, AnthillLayout.Style.CLASSIC, center.asLong());
    }

    /** An anthill of a given style with the old look (game tests). */
    public AnthillPiece(BlockPos center, Kind kind, AnthillLayout.Style style, long seed) {
        this(center, kind, style, seed, null);
    }

    public AnthillPiece(BlockPos center, Kind kind, AnthillLayout.Style style, long seed, @Nullable AnthillTheme theme) {
        this(center, kind, style, seed, theme, AnthillLayout.create(style, seed), null);
    }

    /** An anthill of the LOCAL look, with the rock of its world. */
    public static AnthillPiece ofLand(BlockPos center, Kind kind, AnthillLayout.Style style, long seed, Block rock) {
        return new AnthillPiece(center, kind, style, seed, AnthillTheme.LOCAL, AnthillLayout.create(style, seed), rock);
    }

    private AnthillPiece(BlockPos center, Kind kind, AnthillLayout.Style style, long seed, @Nullable AnthillTheme theme, AnthillLayout layout,
                         @Nullable Block rock) {
        super(ModStructures.ANTHILL_PIECE.get(), 0, box(center, layout, theme));
        this.rock = rock;
        this.center = center;
        this.kind = kind;
        this.style = style;
        this.seed = seed;
        this.theme = theme;
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
        this.theme = tag.getString("Theme").map(AnthillTheme::byName).orElse(null);
        this.rock = tag.getString("Rock").map(Identifier::tryParse).flatMap(BuiltInRegistries.BLOCK::getOptional).orElse(null);
        this.layout = AnthillLayout.create(style, seed);
    }

    /** Plans an anthill at {@code center}, trying other seeds while it would reach below {@code lowestY}, then a shallow abandoned nest. */
    public static AnthillPiece plan(BlockPos center, Kind kind, long seed, int lowestY, AnthillTheme theme) {
        return plan(center, kind, seed, lowestY, theme, null);
    }

    public static AnthillPiece plan(BlockPos center, Kind kind, long seed, int lowestY, AnthillTheme theme, @Nullable Block rock) {
        for (int attempt = 0; attempt < 4; attempt++) {
            long tried = seed + attempt * 0x9E3779B97F4A7C15L;
            AnthillLayout.Style style = hash(tried, 17, 29, 31) < 0.1 ? AnthillLayout.Style.ABANDONED
                    : AnthillLayout.Style.GROWN;
            AnthillLayout layout = AnthillLayout.create(style, tried);
            if (center.getY() + layout.minY() > lowestY) {
                return new AnthillPiece(center, kind, style, tried, theme, layout, rock);
            }
        }
        return new AnthillPiece(center, kind, AnthillLayout.Style.ABANDONED, seed, theme, AnthillLayout.create(AnthillLayout.Style.ABANDONED, seed), rock);
    }

    /**
     * The rock under a spot when it is another mod's: the first solid block of the column from {@code from} down to
     * {@code to} that is not stone, deepslate, netherrack or end stone. Null in the base game's worlds.
     */
    public static @Nullable Block foreignRock(NoiseColumn column, int from, int to) {
        for (int y = from; y >= to; y--) {
            BlockState state = column.getBlock(y);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                Block block = state.getBlock();
                return block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.NETHERRACK || block == Blocks.END_STONE ? null : block;
            }
        }
        return null;
    }

    /** A plan's style from the older set, picked from its seed (the game tests use it). */
    public static AnthillLayout.Style styleFor(long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x2545F4914F6CDD1DL);
        return AnthillLayout.Style.RANDOM[random.nextInt(AnthillLayout.Style.RANDOM.length)];
    }

    private static BoundingBox box(BlockPos center, AnthillLayout layout, @Nullable AnthillTheme theme) {
        int reach = theme == null ? RADIUS + 2 : theme.reach() + 1;
        int top = theme == null ? HEIGHT + 2 : Math.max(HEIGHT, theme.maxHeight()) + 2;
        return new BoundingBox(center.getX() + Math.min(-reach, layout.minX()), center.getY() + layout.minY() - 1,
                center.getZ() + Math.min(-reach, layout.minZ()), center.getX() + Math.max(reach, layout.maxX()),
                center.getY() + top, center.getZ() + Math.max(reach, layout.maxZ()));
    }

    public AnthillLayout layout() {
        return layout;
    }

    public BlockPos center() {
        return center;
    }

    public @Nullable AnthillTheme theme() {
        return theme;
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
        if (theme != null) {
            tag.putString("Theme", theme.getSerializedName());
        }
        if (rock != null) {
            tag.putString("Rock", BuiltInRegistries.BLOCK.getKey(rock).toString());
        }
    }

    private int legacyMoundHeight(long hashSeed, int x, int z) {
        double distance = Math.sqrt(x * x + z * z) + (hash(hashSeed, x, 0, z) - 0.5) * 1.4;
        if (distance >= RADIUS) {
            return 0;
        }
        double f = distance / RADIUS;
        return (int) Math.round(HEIGHT * (1 - f * f));
    }

    /** The mound's height over a column (relative x, z), whichever look the anthill has. */
    public int moundHeight(int x, int z) {
        long hashSeed = style == AnthillLayout.Style.CLASSIC ? 0 : seed;
        return theme == null ? legacyMoundHeight(hashSeed, x, z) : theme.moundHeight(hashSeed, x, z);
    }

    // --- Building ---

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        long hashSeed = style == AnthillLayout.Style.CLASSIC ? level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L : seed;
        if (theme == null) {
            buildLegacy(level, chunkBB, hashSeed, generator);
        } else {
            build(level, chunkBB, hashSeed, theme);
        }
        furnish(level, chunkBB, hashSeed, generator);
    }

    /** The old look, kept exactly for anthills planned before themes. */
    private void buildLegacy(WorldGenLevel level, BoundingBox chunkBB, long hashSeed, ChunkGenerator generator) {
        Soil soil = kind == Kind.NETHER ? Soil.NETHER : kind == Kind.END ? Soil.END : Soil.of(biome(level, generator));
        for (int x = Math.max(boundingBox.minX(), chunkBB.minX()); x <= Math.min(boundingBox.maxX(), chunkBB.maxX()); x++) {
            for (int z = Math.max(boundingBox.minZ(), chunkBB.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBB.maxZ()); z++) {
                int rx = x - center.getX();
                int rz = z - center.getZ();
                int mound = legacyMoundHeight(hashSeed, rx, rz);
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
                        boolean wallOff = here.getFluidState().isEmpty() || here.getFluidState().is(FluidTags.LAVA);
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
    }

    private void build(WorldGenLevel level, BoundingBox chunkBB, long hashSeed, AnthillTheme theme) {
        Set<Long> trailCells = trails(hashSeed, theme);
        BlockState trail = theme.trail();
        for (int x = Math.max(boundingBox.minX(), chunkBB.minX()); x <= Math.min(boundingBox.maxX(), chunkBB.maxX()); x++) {
            for (int z = Math.max(boundingBox.minZ(), chunkBB.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBB.maxZ()); z++) {
                int rx = x - center.getX();
                int rz = z - center.getZ();
                int mound = theme.moundHeight(hashSeed, rx, rz);
                // Read before anything is built in the column.
                BlockState[] land = theme == AnthillTheme.LOCAL ? land(level, chunkBB, x, z) : null;
                for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunkBB.isInside(pos) || level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    int ry = y - center.getY();
                    boolean carved = layout.carved(rx, ry, rz) || entrance(rx, ry, rz);
                    double roll = hash(hashSeed, x, y, z);
                    if (ry > 0) {
                        BlockState here = level.getBlockState(pos);
                        if (ry <= mound) {
                            level.setBlock(pos, carved ? Blocks.AIR.defaultBlockState() : local(theme.mound(roll, mound - ry, y), land), Block.UPDATE_CLIENTS);
                        } else if (mound > 0 && ry <= mound + 2 && (here.isAir() || here.canBeReplaced())) {
                            // Grass and flowers left floating are cleared; the mound's own topping (snow, petals) goes on its top.
                            BlockState topping = ry == mound + 1 && !carved && !layout.carved(rx, ry - 1, rz) ? theme.topping(roll, true) : null;
                            if (topping != null && topping.canSurvive(level, pos)) {
                                level.setBlock(pos, topping, Block.UPDATE_CLIENTS);
                            } else if (!here.isAir()) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                        }
                        continue;
                    }
                    BlockState here = level.getBlockState(pos);
                    if (here.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (carved) {
                        BlockState fill = Blocks.AIR.defaultBlockState();
                        if (ry < 0 && !layout.carved(rx, ry + 1, rz)) {
                            BlockState hanging = theme.ceiling(roll);
                            fill = hanging == null ? fill : hanging;
                        } else if (!layout.carved(rx, ry - 1, rz) && layout.roomAt(rx, ry, rz) != null && layout.carved(rx, ry + 1, rz)) {
                            BlockState carpet = theme.carpet(roll);
                            fill = carpet == null ? fill : carpet;
                        }
                        level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
                        continue;
                    }
                    if (layout.shell(rx, ry, rz)) {
                        // Ant-dug walls; caves stay open. Water and lava are walled off, so a deep nest never floods.
                        if (!here.isAir() || kind == Kind.END) {
                            boolean floor = layout.carved(rx, ry + 1, rz);
                            AnthillLayout.Room room = floor ? layout.roomAt(rx, ry + 1, rz) : null;
                            level.setBlock(pos, local(theme.wall(roll, floor, room == null ? null : room.type(), y), land), Block.UPDATE_CLIENTS);
                        }
                    } else if (mound > 0 && ry >= -2 && here.canBeReplaced() && here.getFluidState().isEmpty()) {
                        level.setBlock(pos, local(theme.mound(roll, 1, y), land), Block.UPDATE_CLIENTS); // fill dips under the mound
                    }
                }
                if (mound == 0) {
                    groundCover(level, chunkBB, x, z, rx, rz, hashSeed, theme, trail != null && trailCells.contains(pack(rx, rz)) ? trail : null);
                }
            }
        }
    }

    /** The entrance's way up through the mound: a dome or a cairn stands higher than the plan's shaft, and would close it. */
    private static boolean entrance(int rx, int ry, int rz) {
        return rx == 0 && rz == 0 && ry > 0;
    }

    /** How deep under and over the anthill's own ground a column's ground is looked for. */
    private static final int LAND_REACH = 6;

    /**
     * The ground of a column as the world made it, for the LOCAL look: its top block (the turf) and the block under
     * that (the soil). Where no ground is found near the anthill's own level, both are the world's rock.
     */
    private BlockState[] land(WorldGenLevel level, BoundingBox chunkBB, int x, int z) {
        BlockState stone = (rock == null ? Blocks.STONE : rock).defaultBlockState();
        for (int y = center.getY() + LAND_REACH; y >= center.getY() - LAND_REACH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!chunkBB.isInside(pos) || level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (ground(level, pos, state) && !ground(level, pos.above(), level.getBlockState(pos.above()))) {
                BlockState under = level.getBlockState(pos.below());
                return new BlockState[] {state, ground(level, pos.below(), under) ? under : state, stone};
            }
        }
        return new BlockState[] {stone, stone, stone};
    }

    /** A whole block of earth or rock (mud and soul sand count): not a plant, a tree, a fluid or something with a block entity. */
    private static boolean ground(WorldGenLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && state.isSolidRender();
    }

    /** A block of the LOCAL look: the column's own turf, soil and rock for grass, dirt and stone, anthill soil in that soil's tone. */
    private static BlockState local(BlockState state, BlockState @Nullable [] land) {
        if (land == null) {
            return state;
        }
        if (state.hasProperty(AnthillSoilBlock.TONE)) {
            return state.setValue(AnthillSoilBlock.TONE, SoilTone.of(land[1]));
        }
        return state.is(Blocks.GRASS_BLOCK) ? land[0] : state.is(Blocks.DIRT) ? land[1] : state.is(Blocks.STONE) ? land[2] : state;
    }

    private void groundCover(WorldGenLevel level, BoundingBox chunkBB, int x, int z, int rx, int rz, long hashSeed, AnthillTheme theme,
                             @Nullable BlockState trail) {
        double distance = Math.sqrt(rx * rx + rz * rz);
        if (trail == null && distance > theme.shape.radius + 3) {
            return;
        }
        // The final heightmap: worldgen keeps it from the features step on, and a built level has only those.
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos ground = new BlockPos(x, groundY, z);
        BlockPos above = ground.above();
        if (!chunkBB.isInside(ground) || !chunkBB.isInside(above) || layout.carved(rx, groundY - center.getY(), rz)
                || layout.carved(rx, groundY + 1 - center.getY(), rz)) {
            return;
        }
        BlockState top = level.getBlockState(ground);
        BlockState over = level.getBlockState(above);
        if (!over.isAir() && !over.canBeReplaced() || !over.getFluidState().isEmpty()) {
            return;
        }
        if (trail != null) {
            if (top.is(BlockTags.DIRT) || top.is(Blocks.GRASS_BLOCK)) {
                level.setBlock(ground, trail, Block.UPDATE_CLIENTS);
                if (!over.isAir()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            return;
        }
        BlockState topping = theme.topping(hash(hashSeed, x, groundY, z), false);
        if (topping != null && over.isAir() && top.isFaceSturdy(level, ground, Direction.UP) && topping.canSurvive(level, above)) {
            level.setBlock(above, topping, Block.UPDATE_CLIENTS);
        }
    }

    private Set<Long> trails(long hashSeed, AnthillTheme theme) {
        if (trails == null) {
            Set<Long> cells = new HashSet<>();
            if (theme.trail() != null) {
                RandomSource random = RandomSource.create(hashSeed ^ 0x51ED270B7A2F3C1DL);
                int count = 3 + random.nextInt(2);
                double angle = random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < count; i++) {
                    angle += Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.8;
                    double heading = angle;
                    double px = Math.cos(angle) * (theme.shape.radius - 1);
                    double pz = Math.sin(angle) * (theme.shape.radius - 1);
                    int length = 8 + random.nextInt(5);
                    int lastX = (int) Math.round(px);
                    int lastZ = (int) Math.round(pz);
                    for (int step = 0; step < length * 2; step++) {
                        heading += (random.nextDouble() - 0.5) * 0.5;
                        px += Math.cos(heading) * 0.5;
                        pz += Math.sin(heading) * 0.5;
                        int cx = (int) Math.round(px);
                        int cz = (int) Math.round(pz);
                        if (cx != lastX && cz != lastZ) {
                            // A diagonal step: fill the corner, so the trail runs on unbroken instead of in touching corners.
                            cells.add(pack(cx, lastZ));
                        }
                        cells.add(pack(cx, cz));
                        lastX = cx;
                        lastZ = cz;
                    }
                }
            }
            trails = cells;
        }
        return trails;
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    /** How far above the mound's foot the land may stand for an ant to be posted on it. */
    private static final int OUTSIDE_STEP = 3;

    /** What lies in each room, and who lives there; each only by the chunk it stands in. */
    private void furnish(WorldGenLevel level, BoundingBox chunkBB, long hashSeed, ChunkGenerator generator) {
        Holder<Biome> biome = biome(level, generator);
        RandomSource random = RandomSource.create(hashSeed + 1);
        Identifier species = dweller(level.registryAccess(), biome, random);
        ResourceKey<LootTable> loot = switch (kind) {
            case NETHER -> NETHER_LOOT;
            case END -> END_LOOT;
            case OVERWORLD -> LOOT;
        };
        AnthillLayout.Room queen = layout.queen();
        // The Queen's spot names the anthill: every ant of it remembers it.
        BlockPos anthill = floor(queen, 0, 0);
        boolean abandoned = style == AnthillLayout.Style.ABANDONED;
        AnthillTheme look = theme;
        AnthillDecor decor = new AnthillDecor(level, chunkBB, layout, center, kind, theme, abandoned);
        for (AnthillLayout.Room room : layout.rooms()) {
            long roomSeed = hashSeed ^ (long) (room.x() * 31 + room.y() * 977 + room.z() * 7919);
            RandomSource roomRandom = RandomSource.create(roomSeed);
            switch (room.type()) {
                case QUEEN -> {
                    // The great Queen, her nest, a barrel of the colony's riches, her eggs, and her royal guard (an abandoned nest's Queen is alone).
                    boolean grand = layout.grand();
                    container(level, chunkBB, floor(room, grand ? -4 : -2, grand ? 2 : 1), Blocks.BARREL.defaultBlockState(), loot, roomRandom);
                    var nestBlock = AntColonies.MODID.equals(species.getNamespace()) ? ModBlocks.NESTS.get(species.getPath()) : null;
                    BlockPos nest = floor(room, grand ? 5 : 3, 0);
                    if (nestBlock != null && chunkBB.isInside(nest)) {
                        level.setBlock(nest, nestBlock.get().defaultBlockState().setValue(WildNestBlock.RECOVERING, false), Block.UPDATE_CLIENTS);
                    }
                    BlockState lamp = kind == Kind.OVERWORLD ? ModDecor.PHEROMONE_LAMP.get().defaultBlockState()
                            : kind == Kind.NETHER ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.END_ROD.defaultBlockState();
                    // In a grand chamber a royal guard is posted three blocks out: the lamp stands a block further.
                    place(level, chunkBB, floor(room, grand ? -4 : -3, -1), lamp);
                    if (grand) {
                        place(level, chunkBB, floor(room, 3, -3), lamp);
                        place(level, chunkBB, floor(room, -1, 4), lamp);
                        for (int i = 0; i < 4; i++) {
                            cluster(level, chunkBB, floor(room, roomRandom.nextInt(7) - 3, roomRandom.nextInt(2) == 0 ? -4 : 4), i % 2 == 0, species, anthill);
                        }
                    }
                    spawn(level, chunkBB, anthill, species, Caste.QUEEN, WildAnt.Rank.GREAT_QUEEN, anthill, 2, chamber(room));
                    if (!abandoned) {
                        int guards = grand ? 6 : 5;
                        double radius = grand ? 3.5 : 2.5;
                        for (int i = 0; i < guards; i++) {
                            double a = i * Math.PI * 2 / guards + 0.4;
                            spawn(level, chunkBB, floor(room, (int) Math.round(Math.cos(a) * radius), (int) Math.round(Math.sin(a) * radius)), species,
                                    Caste.SOLDIER, WildAnt.Rank.ROYAL, anthill, 6);
                        }
                    }
                }
                case ANTECHAMBER -> {
                    // The royal guard's post: the only way to the Queen.
                    place(level, chunkBB, floor(room, 2, 2), ModDecor.CHITIN_BLOCK.get().defaultBlockState());
                    place(level, chunkBB, floor(room, -2, -2), ModDecor.CHITIN_BLOCK.get().defaultBlockState());
                    place(level, chunkBB, floor(room, -2, 2), kind == Kind.OVERWORLD ? ModDecor.PHEROMONE_LAMP.get().defaultBlockState()
                            : kind == Kind.NETHER ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.END_ROD.defaultBlockState());
                    int[][] posts = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] post : posts) {
                        spawn(level, chunkBB, floor(room, post[0], post[1]), species, Caste.SOLDIER, WildAnt.Rank.ROYAL, anthill, 5);
                    }
                }
                case NURSERY, EGG_STORAGE, LARVAL -> {
                    // Eggs and larvae in piles on the floor; nurses among them.
                    int piles = room.type() == AnthillLayout.RoomType.NURSERY ? 4 : 5;
                    for (int i = 0; i < piles; i++) {
                        boolean eggs = room.type() == AnthillLayout.RoomType.EGG_STORAGE || room.type() == AnthillLayout.RoomType.NURSERY && i % 2 == 0;
                        cluster(level, chunkBB, floor(room, roomRandom.nextInt(5) - 2, roomRandom.nextInt(5) - 2), eggs, species, anthill);
                    }
                    workers(level, chunkBB, room, species, anthill, 1 + roomRandom.nextInt(2));
                }
                case FOOD_STORAGE -> {
                    container(level, chunkBB, floor(room, 1, 0), Blocks.BARREL.defaultBlockState(), FOOD, roomRandom);
                    workers(level, chunkBB, room, species, anthill, 1 + roomRandom.nextInt(2));
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
                    workers(level, chunkBB, room, species, anthill, 1 + roomRandom.nextInt(2));
                }
                case APHID_FARM -> {
                    // Aphids kept for their honeydew: moss and azaleas to feed on, honey stored by.
                    for (int i = 0; i < 4; i++) {
                        BlockState plant = switch (kind) {
                            case NETHER -> (i % 2 == 0 ? Blocks.CRIMSON_ROOTS : Blocks.WARPED_ROOTS).defaultBlockState();
                            case END -> Blocks.MOSS_CARPET.defaultBlockState();
                            case OVERWORLD -> (i == 0 ? Blocks.FLOWERING_AZALEA : i == 1 ? Blocks.AZALEA : Blocks.MOSS_CARPET).defaultBlockState();
                        };
                        place(level, chunkBB, floor(room, roomRandom.nextInt(5) - 2, roomRandom.nextInt(5) - 2), plant);
                    }
                    place(level, chunkBB, floor(room, 2, -1), Blocks.HONEY_BLOCK.defaultBlockState());
                    workers(level, chunkBB, room, species, anthill, 2);
                }
                case MIDDEN -> {
                    // The colony's refuse heap: bones and spoil, and finds a Brush can work out of the gravel.
                    BlockState spoil = (look == null ? AnthillTheme.TEMPERATE : look).suspicious().defaultBlockState();
                    for (int i = 0; i < 4; i++) {
                        BlockPos spot = floor(room, roomRandom.nextInt(5) - 2, roomRandom.nextInt(5) - 2).below();
                        // Drawn whichever chunk the spot is in: every chunk of the room draws alike.
                        long find = roomRandom.nextLong();
                        if (chunkBB.isInside(spot) && !level.getBlockState(spot).isAir()) {
                            level.setBlock(spot, spoil, Block.UPDATE_CLIENTS);
                            if (level.getBlockEntity(spot) instanceof BrushableBlockEntity brushable) {
                                brushable.setLootTable(MIDDEN, find);
                            }
                        }
                    }
                    // Clear of the worker's post.
                    place(level, chunkBB, floor(room, 2, 1), Blocks.BONE_BLOCK.defaultBlockState());
                    place(level, chunkBB, floor(room, -1, -1), Blocks.BONE_BLOCK.defaultBlockState());
                    workers(level, chunkBB, room, species, anthill, 1);
                }
                case BARRACKS -> {
                    if (!abandoned) {
                        int soldiers = layout.grand() ? 4 : 3;
                        for (int i = 0; i < soldiers; i++) {
                            spawn(level, chunkBB, floor(room, i - 1, roomRandom.nextInt(3) - 1), species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 8);
                        }
                    }
                    // Clear of the soldiers' posts.
                    place(level, chunkBB, floor(room, -2, 2), Blocks.BONE_BLOCK.defaultBlockState());
                }
                case CHITIN_STORAGE -> {
                    boolean guarded = !abandoned && layout.grand();
                    BlockPos post = floor(room, -1, -1);
                    for (int i = 0; i < 3; i++) {
                        int dx = roomRandom.nextInt(5) - 2;
                        int dz = roomRandom.nextInt(3) - 1;
                        BlockPos spot = floor(room, dx, dz);
                        // Never on the chest's spot, nor where the store's soldier is posted.
                        if ((dx != 0 || dz != 1) && !(guarded && spot.equals(post))) {
                            place(level, chunkBB, spot, ModDecor.CHITIN_BLOCK.get().defaultBlockState());
                        }
                    }
                    container(level, chunkBB, floor(room, 0, 1), Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(roomRandom)), loot, roomRandom);
                    if (guarded) {
                        spawn(level, chunkBB, post, species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 8);
                    }
                }
                case TREASURE -> {
                    container(level, chunkBB, floor(room, 0, 0), Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(roomRandom)), TREASURE, roomRandom);
                    place(level, chunkBB, floor(room, 1, 1), ModDecor.ROYAL_CHITIN_BLOCK.get().defaultBlockState());
                    if (!abandoned && layout.grand()) {
                        spawn(level, chunkBB, floor(room, -1, 1), species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 6);
                        spawn(level, chunkBB, floor(room, 1, -1), species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 6);
                    }
                }
                case RARE -> {
                    // Where the ants dug into something precious: a few ore and crystal blocks left in the walls and floor.
                    BlockState[] finds = AnthillDecor.finds(kind);
                    for (int i = 0; i < 3; i++) {
                        BlockPos spot = floor(room, roomRandom.nextInt(3) - 1, roomRandom.nextInt(3) - 1).below();
                        // Drawn whichever chunk the spot is in: every chunk of the room draws alike.
                        BlockState find = finds[roomRandom.nextInt(finds.length)];
                        if (chunkBB.isInside(spot)) {
                            level.setBlock(spot, find, Block.UPDATE_CLIENTS);
                        }
                    }
                    if (layout.grand()) {
                        workers(level, chunkBB, room, species, anthill, 1);
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
                    int soldiers = layout.grand() ? 2 : 1;
                    for (int i = 0; i < soldiers; i++) {
                        spawn(level, chunkBB, floor(room, i * 2 - 1, 0), species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 8);
                    }
                    workers(level, chunkBB, room, species, anthill, 2);
                }
            }
            // After the room's own things, so nothing of them is built over.
            decor.room(room, RandomSource.create(roomSeed ^ 0x3C6EF372FE94F82BL));
        }
        decor.tunnels(RandomSource.create(hashSeed ^ 0x1F83D9ABFB41BD6BL));
        if (abandoned) {
            return;
        }
        // Soldiers watching tunnels.
        for (double[] spot : layout.patrols()) {
            BlockPos at = floorAt(spot[0], spot[1], spot[2]);
            if (at != null) {
                spawn(level, chunkBB, at, species, Caste.SOLDIER, WildAnt.Rank.NORMAL, anthill, 6);
            }
        }
        // Ants out on the mound: workers coming and going, a soldier or two on watch.
        if (theme != null) {
            RandomSource outside = RandomSource.create(hashSeed ^ 0x6A09E667F3BCC909L);
            int count = layout.size() == AnthillLayout.Size.LARGE ? 5 : layout.size() == AnthillLayout.Size.SMALL ? 3 : 4;
            for (int i = 0; i < count; i++) {
                double a = outside.nextDouble() * Math.PI * 2;
                double r = 2.5 + outside.nextDouble() * (theme.shape.radius - 3);
                int rx = (int) Math.round(Math.cos(a) * r);
                int rz = (int) Math.round(Math.sin(a) * r);
                int ry = moundHeight(rx, rz) + 1;
                if (layout.carved(rx, ry - 1, rz) || layout.carved(rx, ry, rz)) {
                    continue;
                }
                BlockPos spot = center.offset(rx, ry, rz);
                if (chunkBB.isInside(spot)) {
                    // On a slope the land stands higher than the mound's foot: the ant stands on it, or stays away from a cliff.
                    int land = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
                    if (land > spot.getY() + OUTSIDE_STEP) {
                        continue;
                    }
                    spot = spot.atY(Math.max(spot.getY(), land));
                }
                spawn(level, chunkBB, spot, species, i < 2 && count > 3 || i == 0 ? Caste.SOLDIER : Caste.WORKER, WildAnt.Rank.NORMAL, anthill, 10);
            }
        }
    }

    private void workers(WorldGenLevel level, BoundingBox chunkBB, AnthillLayout.Room room, Identifier species, BlockPos anthill, int count) {
        int[][] spots = {{1, 1}, {-1, 0}, {0, -1}};
        for (int i = 0; i < count && i < spots.length; i++) {
            spawn(level, chunkBB, floor(room, spots[i][0], spots[i][1]), species, Caste.WORKER, WildAnt.Rank.NORMAL, anthill, 8);
        }
    }

    private AABB chamber(AnthillLayout.Room room) {
        return new AABB(center.getX() + room.x() - room.rx(), center.getY() + room.y() - room.ry() - 0.5, center.getZ() + room.z() - room.rz(),
                center.getX() + room.x() + room.rx() + 1, center.getY() + room.y() + room.ry() + 1, center.getZ() + room.z() + room.rz() + 1);
    }

    static boolean free(BlockState state) {
        return state.isAir() || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.SNOW) || state.is(Blocks.HANGING_ROOTS) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.PALE_MOSS_CARPET) || state.is(Blocks.PALE_HANGING_MOSS) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.WEEPING_VINES);
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, BlockState state) {
        if (chunkBB.isInside(pos) && free(level.getBlockState(pos)) && !free(level.getBlockState(pos.below()))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A live egg or larva cluster of the anthill: it hatches, in time, into ants of its species that belong to the anthill. */
    private static void cluster(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, boolean eggs, Identifier species, BlockPos anthill) {
        if (chunkBB.isInside(pos) && free(level.getBlockState(pos)) && !free(level.getBlockState(pos.below()))) {
            level.setBlock(pos, (eggs ? ModDecor.ANT_EGG_CLUSTER : ModDecor.LARVA_CLUSTER).get().defaultBlockState()
                    .setValue(BroodClusterBlock.NATURAL, true), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof BroodClusterBlockEntity cluster) {
                cluster.set(species, anthill);
            }
        }
    }

    /** A container of loot. Its loot's seed is drawn whichever chunk it stands in, so every chunk of its room draws alike after it. */
    private static void container(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, BlockState state, ResourceKey<LootTable> loot, RandomSource random) {
        long seed = random.nextLong();
        if (chunkBB.isInside(pos) && free(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
                container.setLootTable(loot, seed);
            }
        }
    }

    /** The biome at the center, from the biome source: asking the level crashes when the center's chunk is out of the region's reach. */
    private Holder<Biome> biome(WorldGenLevel level, ChunkGenerator generator) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(center.getZ()),
                level.getLevel().getChunkSource().randomState().sampler());
    }

    /**
     * The ants of this anthill: one of its look's own species that lives in the biome, else the look's first. A look
     * with none of its own takes any ant of the biome, else Common, Fire or Chorus Ants by the world. The old look
     * keeps the old rule.
     */
    public Identifier dweller(RegistryAccess registries, Holder<Biome> biome, RandomSource random) {
        Identifier fallback = AntColonies.id(kind == Kind.NETHER ? "fire" : kind == Kind.END ? "chorus" : "common");
        if (theme == null) {
            return switch (kind) {
                case NETHER -> AntColonies.id(biome.is(Biomes.WARPED_FOREST) || biome.is(Biomes.CRIMSON_FOREST) ? "warped" : "fire");
                case END -> fallback;
                case OVERWORLD -> WildAnt.speciesFor(registries, biome, random);
            };
        }
        List<Identifier> natives = WildAnt.natives(registries, biome);
        List<String> own = theme.dwellers();
        if (own.isEmpty()) {
            return natives.isEmpty() ? fallback : natives.get(random.nextInt(natives.size()));
        }
        List<Identifier> suited = new ArrayList<>();
        for (String name : own) {
            if (natives.contains(AntColonies.id(name))) {
                suited.add(AntColonies.id(name));
            }
        }
        return suited.isEmpty() ? AntColonies.id(own.getFirst()) : suited.get(random.nextInt(suited.size()));
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

    /** The floor under a spot in a tunnel (relative), in the world; null if the spot isn't dug. */
    private @Nullable BlockPos floorAt(double sx, double sy, double sz) {
        int x = (int) Math.round(sx);
        int y = (int) Math.round(sy);
        int z = (int) Math.round(sz);
        if (!layout.carved(x, y, z)) {
            return null;
        }
        for (int i = 0; i < 4 && layout.carved(x, y - 1, z); i++) {
            y--;
        }
        return new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z);
    }

    private static @Nullable WildAnt spawn(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, Identifier species, Caste caste, WildAnt.Rank rank,
                                           BlockPos anthill, int homeRadius) {
        return spawn(level, chunkBB, pos, species, caste, rank, anthill, homeRadius, null);
    }

    /** An ant of the anthill, with the great Queen's chamber if it is hers: all of it set before the ant is added, which saves it at once. */
    private static @Nullable WildAnt spawn(WorldGenLevel level, BoundingBox chunkBB, BlockPos pos, Identifier species, Caste caste, WildAnt.Rank rank,
                                           BlockPos anthill, int homeRadius, @Nullable AABB chamber) {
        if (!chunkBB.isInside(pos)) {
            return null;
        }
        WildAnt ant = ModEntities.WILD_ANT.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (ant == null) {
            return null;
        }
        ant.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        ant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        ant.setSpecies(species);
        ant.setCaste(caste);
        ant.setGuardian(true);
        ant.setRank(rank);
        ant.setAnthill(anthill);
        ant.setPersistenceRequired();
        ant.setHomeTo(pos, homeRadius);
        ant.setChamber(chamber);
        level.addFreshEntityWithPassengers(ant);
        return ant;
    }

    private static double hash(long seed, int x, int y, int z) {
        return AnthillTheme.hash(seed, x, y, z);
    }

    /** The old look: anthill soil (sandy in deserts and badlands), the Nether's or the End's. */
    private enum Soil {
        EARTH, DESERT, BADLANDS, NETHER, END;

        static Soil of(Holder<Biome> biome) {
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
