package com.antcolonies.content.worldgen;

import com.antcolonies.core.registry.ModDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * What makes an anthill's rooms look lived in: pillars left standing, stores along the walls, things set in the walls
 * and hanging from the ceilings, lights along the tunnels. Nothing stands in a doorway or where an ant is posted.
 */
final class AnthillDecor {
    /** How far from a room's middle anything solid stands, squared: the ants of a room are posted nearer than this. */
    private static final int CLEAR = 4;
    private static final int CLEAR_QUEEN = 10;
    private static final int CLEAR_GRAND_QUEEN = 18;
    /** A tunnel has a light in its wall about every this many blocks. */
    private static final double LIGHT_SPACING = 10;

    private final WorldGenLevel level;
    private final BoundingBox chunkBB;
    private final AnthillLayout layout;
    private final BlockPos center;
    private final AnthillPiece.Kind kind;
    private final AnthillTheme theme;
    /** An abandoned nest: its lights have gone out. */
    private final boolean dark;

    AnthillDecor(WorldGenLevel level, BoundingBox chunkBB, AnthillLayout layout, BlockPos center, AnthillPiece.Kind kind, @Nullable AnthillTheme theme,
                 boolean dark) {
        this.level = level;
        this.chunkBB = chunkBB;
        this.layout = layout;
        this.center = center;
        this.kind = kind;
        this.theme = theme != null ? theme : kind == AnthillPiece.Kind.NETHER ? AnthillTheme.NETHER : kind == AnthillPiece.Kind.END ? AnthillTheme.END
                : AnthillTheme.TEMPERATE;
        this.dark = dark;
    }

    /** What the ants dug into in a rare-find chamber. */
    static BlockState[] finds(AnthillPiece.Kind kind) {
        return switch (kind) {
            case NETHER -> new BlockState[] {Blocks.NETHER_GOLD_ORE.defaultBlockState(), Blocks.NETHER_QUARTZ_ORE.defaultBlockState(),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState()};
            case END -> new BlockState[] {Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                    ModDecor.AMBER_BLOCK.get().defaultBlockState()};
            case OVERWORLD -> new BlockState[] {Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
                    ModDecor.AMBER_BLOCK.get().defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState()};
        };
    }

    // --- Rooms ---

    /** Every random number is drawn whatever the world holds, so each chunk of a room decorates it the same way. */
    void room(AnthillLayout.Room room, RandomSource random) {
        double turn = random.nextDouble() * Math.PI * 2;
        boolean overworld = kind == AnthillPiece.Kind.OVERWORLD;
        boolean end = kind == AnthillPiece.Kind.END;
        switch (room.type()) {
            case QUEEN -> {
                // Pillars of royal chitin hold her ceiling up; amber is set in her walls.
                pillars(room, 4, 0.8, (end ? Blocks.PURPUR_PILLAR : ModDecor.ROYAL_CHITIN_PILLAR.get()).defaultBlockState());
                walls(room, random, turn, layout.grand() ? 6 : 4, ModDecor.AMBER_BLOCK.get().defaultBlockState());
                lights(room, random, turn + 0.7, 2);
            }
            case ANTECHAMBER -> {
                pillars(room, 2, 0.75, theme.pillar());
                walls(room, random, turn, 3, (end ? Blocks.PURPUR_BLOCK : ModDecor.CHITIN_BRICKS.get()).defaultBlockState());
            }
            case NURSERY, EGG_STORAGE, LARVAL -> {
                // Brood is kept warm and soft: bedding on the floor, a light in the wall.
                litter(room, random, 5);
                hangings(room, random, 2, theme.hanging());
                lights(room, random, turn, 1);
            }
            case FOOD_STORAGE -> {
                // A granary: stores heaped along the walls, more packed into the ceiling.
                Block[] stores = theme.stores();
                for (int i = 0; i < 4; i++) {
                    pile(room, turn + i * 1.7 + random.nextDouble() * 0.5, 0.7, stores[random.nextInt(stores.length)].defaultBlockState(),
                            1 + random.nextInt(2), null);
                }
                Block larder = overworld ? Blocks.HONEYCOMB_BLOCK : end ? null : Blocks.NETHER_WART_BLOCK;
                for (int i = 0; i < 3; i++) {
                    int dx = random.nextInt(5) - 2;
                    int dz = random.nextInt(5) - 2;
                    if (larder != null) {
                        inCeiling(room, dx, dz, larder.defaultBlockState());
                    }
                }
                lights(room, random, turn + 0.9, 1);
            }
            case FUNGAL_GARDEN -> {
                // Fungus grown over the walls and in mounds, one glowing patch overhead.
                BlockState fungal = ModDecor.FUNGAL_BLOCK.get().defaultBlockState();
                walls(room, random, turn, 5, fungal);
                walls(room, random, turn + 0.4, 1, ModDecor.GLOWING_FUNGAL_BLOCK.get().defaultBlockState());
                inCeiling(room, random.nextInt(3) - 1, random.nextInt(3) - 1, ModDecor.GLOWING_FUNGAL_BLOCK.get().defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    BlockState crop = kind == AnthillPiece.Kind.NETHER ? (i % 2 == 0 ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS).defaultBlockState()
                            : end ? null : (i % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
                    pile(room, turn + i * 2.1 + random.nextDouble() * 0.6, 0.7, fungal, 1, crop);
                }
                hangings(room, random, 3, overworld ? Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true)
                        : theme.hanging());
            }
            case APHID_FARM -> {
                // Root aphids feed on the roots that come through the ceiling; the honeydew is stored in the walls.
                for (int i = 0; i < 5; i++) {
                    int dx = random.nextInt(5) - 2;
                    int dz = random.nextInt(5) - 2;
                    if (overworld) {
                        inCeiling(room, dx, dz, Blocks.ROOTED_DIRT.defaultBlockState());
                        hang(room, dx, dz, i < 2 ? Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true)
                                : Blocks.HANGING_ROOTS.defaultBlockState(), 0);
                    } else {
                        hang(room, dx, dz, theme.hanging(), 0);
                    }
                }
                walls(room, random, turn, 3, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
            }
            case MIDDEN -> {
                // Refuse: bones heaped and trodden into the walls, and what grows on a rubbish heap.
                BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
                for (int i = 0; i < 3; i++) {
                    pile(room, turn + i * 2.1 + random.nextDouble() * 0.6, 0.7, i == 1 && overworld ? Blocks.COARSE_DIRT.defaultBlockState() : bone, 1,
                            null);
                }
                walls(room, random, turn + 1, 3, bone);
                litter(room, random, 4);
            }
            case BARRACKS -> {
                pillars(room, 2, 0.8, theme.pillar());
                walls(room, random, turn, 4, (end ? Blocks.PURPUR_BLOCK : ModDecor.CHITIN_BRICKS.get()).defaultBlockState());
                lights(room, random, turn + 0.8, 1);
            }
            case CHITIN_STORAGE -> {
                // Shed plates stacked by kind along the walls.
                Block[] plates = {ModDecor.CHITIN_BLOCK.get(), ModDecor.POLISHED_CHITIN.get(), ModDecor.CHITIN_TILES.get()};
                for (int i = 0; i < 4; i++) {
                    pile(room, turn + i * 1.7 + random.nextDouble() * 0.5, 0.7, plates[random.nextInt(plates.length)].defaultBlockState(),
                            1 + random.nextInt(2), null);
                }
                lights(room, random, turn + 0.9, 1);
            }
            case TREASURE -> {
                walls(room, random, turn, 3, ModDecor.AMBER_BLOCK.get().defaultBlockState());
                walls(room, random, turn + 1, 2, (end ? Blocks.PURPUR_PILLAR : ModDecor.ROYAL_CHITIN_BRICKS.get()).defaultBlockState());
                lights(room, random, turn + 0.5, 1);
            }
            case RARE -> {
                // The vein the ants struck runs on through the walls and ceiling.
                BlockState[] finds = finds(kind);
                for (int i = 0; i < 5; i++) {
                    walls(room, random, turn + i * 1.3, 1, finds[random.nextInt(finds.length)]);
                }
                inCeiling(room, random.nextInt(3) - 1, random.nextInt(3) - 1, finds[random.nextInt(finds.length)]);
            }
            case ABANDONED -> {
                // Left long ago: webs under the ceiling, fallen earth along the walls, weeds on the floor.
                for (int i = 0; i < 3; i++) {
                    hang(room, random.nextInt(5) - 2, random.nextInt(5) - 2, Blocks.COBWEB.defaultBlockState(), 2);
                }
                BlockState rubble = (overworld ? Blocks.COARSE_DIRT : end ? Blocks.END_STONE : Blocks.NETHERRACK).defaultBlockState();
                for (int i = 0; i < 2; i++) {
                    pile(room, turn + i * 2.6 + random.nextDouble(), 0.7, rubble, 1, null);
                }
                litter(room, random, 3);
            }
            case GALLERY, HALL -> {
                boolean hall = room.type() == AnthillLayout.RoomType.HALL;
                pillars(room, hall ? 4 : 2, hall ? 0.6 : 0.8, theme.pillar());
                hangings(room, random, 3, theme.hanging());
                litter(room, random, 3);
                lights(room, random, turn, hall ? 3 : 2);
            }
        }
    }

    // --- Tunnels ---

    /** A light in the wall of each tunnel every few blocks (a rod under the ceiling in the End); none in an abandoned nest. */
    void tunnels(RandomSource random) {
        for (AnthillLayout.Tunnel tunnel : layout.tunnels()) {
            double dx = tunnel.x1() - tunnel.x0();
            double dy = tunnel.y1() - tunnel.y0();
            double dz = tunnel.z1() - tunnel.z0();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int count = (int) (length / LIGHT_SPACING);
            for (int i = 0; i < count; i++) {
                double at = (i + 0.3 + random.nextDouble() * 0.4) / count;
                double side = random.nextDouble() * Math.PI * 2;
                if (dark) {
                    continue;
                }
                double x = tunnel.x0() + dx * at;
                double y = tunnel.y0() + dy * at;
                double z = tunnel.z0() + dz * at;
                if (layout.roomAt(x, y, z) != null) {
                    continue;
                }
                // Level tunnels are lit from the side, shafts from any side.
                double flat = Math.sqrt(dx * dx + dz * dz);
                double angle = flat < 0.5 ? side : Math.atan2(dz, dx) + (side < Math.PI ? Math.PI / 2 : -Math.PI / 2);
                light(x, y, z, angle, tunnel.radius() + 3);
            }
        }
    }

    // --- Pieces of decoration ---

    private int clear(AnthillLayout.Room room) {
        return room.type() != AnthillLayout.RoomType.QUEEN ? CLEAR : layout.grand() ? CLEAR_GRAND_QUEEN : CLEAR_QUEEN;
    }

    /** Pillars from floor to ceiling, evenly round the room. */
    private void pillars(AnthillLayout.Room room, int count, double out, BlockState pillar) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI / 4 + i * Math.PI * 2 / count;
            int dx = (int) Math.round(Math.cos(angle) * room.rx() * out);
            int dz = (int) Math.round(Math.sin(angle) * room.rz() * out);
            int x = (int) Math.round(room.x()) + dx;
            int z = (int) Math.round(room.z()) + dz;
            Integer floor = floor(room, x, z);
            if (floor == null || dx * dx + dz * dz < clear(room) || !clearOfTunnels(x, floor, z, 1.2)) {
                continue;
            }
            int top = floor;
            while (layout.carved(x, top + 1, z) && top - floor < 8) {
                top++;
            }
            if (top == floor || layout.carved(x, top + 1, z)) {
                continue;
            }
            for (int y = floor; y <= top; y++) {
                BlockPos pos = center.offset(x, y, z);
                if (chunkBB.isInside(pos) && AnthillPiece.free(level.getBlockState(pos))) {
                    level.setBlock(pos, pillar, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** A heap by the wall, with something growing on it if given. */
    private void pile(AnthillLayout.Room room, double angle, double out, BlockState state, int height, @Nullable BlockState onTop) {
        int dx = (int) Math.round(Math.cos(angle) * room.rx() * out);
        int dz = (int) Math.round(Math.sin(angle) * room.rz() * out);
        int x = (int) Math.round(room.x()) + dx;
        int z = (int) Math.round(room.z()) + dz;
        Integer floor = floor(room, x, z);
        if (floor == null || dx * dx + dz * dz < clear(room) || !clearOfTunnels(x, floor, z, 1.0)) {
            return;
        }
        int y = floor;
        for (; y < floor + height && layout.carved(x, y + 1, z); y++) {
            BlockPos pos = center.offset(x, y, z);
            if (!chunkBB.isInside(pos) || !AnthillPiece.free(level.getBlockState(pos)) || AnthillPiece.free(level.getBlockState(pos.below()))) {
                return;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        BlockPos above = center.offset(x, y, z);
        if (onTop != null && y > floor && chunkBB.isInside(above) && level.getBlockState(above).isAir()) {
            level.setBlock(above, onTop, Block.UPDATE_CLIENTS);
        }
    }

    /** Blocks set into the room's walls, spread round it at different heights. */
    private void walls(AnthillLayout.Room room, RandomSource random, double turn, int count, BlockState state) {
        for (int i = 0; i < count; i++) {
            double angle = turn + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.6;
            double lift = (random.nextDouble() - 0.6) * room.ry();
            BlockPos wall = wall(room.x(), room.y() + lift, room.z(), angle, Math.max(room.rx(), room.rz()) + 1.5);
            if (wall != null) {
                level.setBlock(wall, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void lights(AnthillLayout.Room room, RandomSource random, double turn, int count) {
        for (int i = 0; i < count; i++) {
            double angle = turn + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.6;
            if (!dark) {
                light(room.x(), room.y() + 0.2 * room.ry(), room.z(), angle, Math.max(room.rx(), room.rz()) + 1.5);
            }
        }
    }

    /** A light in the wall met from a point, looking along an angle. Where the look has no wall light, a rod under the ceiling. */
    private void light(double x, double y, double z, double angle, double reach) {
        BlockState lamp = theme.lamp();
        if (lamp != null) {
            BlockPos wall = wall(x, y, z, angle, reach);
            if (wall != null) {
                level.setBlock(wall, lamp, Block.UPDATE_CLIENTS);
            }
            return;
        }
        int cx = (int) Math.round(x + Math.cos(angle) * 0.6);
        int cz = (int) Math.round(z + Math.sin(angle) * 0.6);
        int cy = (int) Math.round(y);
        if (!layout.carved(cx, cy, cz)) {
            return;
        }
        while (layout.carved(cx, cy + 1, cz) && cy < y + 6) {
            cy++;
        }
        BlockPos pos = center.offset(cx, cy, cz);
        if (!layout.carved(cx, cy + 1, cz) && chunkBB.isInside(pos) && level.getBlockState(pos).isAir() && solid(level.getBlockState(pos.above()))) {
            level.setBlock(pos, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN), Block.UPDATE_CLIENTS);
        }
    }

    /** The wall block met from a point along an angle, if the ants built one there (a cave's opening has none). */
    private @Nullable BlockPos wall(double x, double y, double z, double angle, double reach) {
        int cy = (int) Math.round(y);
        for (double step = 0.5; step <= reach; step += 0.5) {
            int cx = (int) Math.round(x + Math.cos(angle) * step);
            int cz = (int) Math.round(z + Math.sin(angle) * step);
            if (layout.carved(cx, cy, cz)) {
                continue;
            }
            BlockPos pos = center.offset(cx, cy, cz);
            return layout.shell(cx, cy, cz) && chunkBB.isInside(pos) && solid(level.getBlockState(pos)) ? pos : null;
        }
        return null;
    }

    private static boolean solid(BlockState state) {
        return !AnthillPiece.free(state) && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.isSolidRender();
    }

    /** Small things strewn on the floor: what grows or gathers in this look's ground. */
    private void litter(AnthillLayout.Room room, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            int dx = random.nextInt(7) - 3;
            int dz = random.nextInt(7) - 3;
            BlockState state = theme.floorLitter(random.nextDouble());
            int x = (int) Math.round(room.x()) + dx;
            int z = (int) Math.round(room.z()) + dz;
            Integer floor = floor(room, x, z);
            if (state == null || floor == null) {
                continue;
            }
            boolean inTheWay = !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
            if (inTheWay && (dx * dx + dz * dz < clear(room) || !clearOfTunnels(x, floor, z, 1.0))) {
                continue;
            }
            BlockPos pos = center.offset(x, floor, z);
            if (chunkBB.isInside(pos) && level.getBlockState(pos).isAir() && solid(level.getBlockState(pos.below())) && state.canSurvive(level, pos)) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void hangings(AnthillLayout.Room room, RandomSource random, int count, @Nullable BlockState state) {
        for (int i = 0; i < count; i++) {
            hang(room, random.nextInt(7) - 3, random.nextInt(7) - 3, state, 0);
        }
    }

    /** Something hanging under the ceiling, where the room is at least so many blocks higher than it. */
    private void hang(AnthillLayout.Room room, int dx, int dz, @Nullable BlockState state, int headroom) {
        int x = (int) Math.round(room.x()) + dx;
        int z = (int) Math.round(room.z()) + dz;
        Integer top = top(room, x, z);
        Integer floor = floor(room, x, z);
        if (state == null || top == null || floor == null || top - floor < headroom) {
            return;
        }
        BlockPos pos = center.offset(x, top, z);
        if (chunkBB.isInside(pos) && level.getBlockState(pos).isAir() && solid(level.getBlockState(pos.above())) && state.canSurvive(level, pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A block set into the ceiling. */
    private void inCeiling(AnthillLayout.Room room, int dx, int dz, BlockState state) {
        int x = (int) Math.round(room.x()) + dx;
        int z = (int) Math.round(room.z()) + dz;
        Integer top = top(room, x, z);
        if (top == null || !layout.shell(x, top + 1, z)) {
            return;
        }
        BlockPos pos = center.offset(x, top + 1, z);
        if (chunkBB.isInside(pos) && solid(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- Where things are ---

    /** The lowest open block of the room in a column, or none if the column is not in the room. */
    private @Nullable Integer floor(AnthillLayout.Room room, int x, int z) {
        int y = (int) Math.round(room.y());
        if (layout.roomAt(x, y, z) != room) {
            return null;
        }
        while (layout.carved(x, y - 1, z) && y > room.y() - room.ry() - 1) {
            y--;
        }
        return layout.carved(x, y - 1, z) ? null : y;
    }

    /** The highest open block of the room in a column, or none if the column is not in the room or opens into a shaft. */
    private @Nullable Integer top(AnthillLayout.Room room, int x, int z) {
        int y = (int) Math.round(room.y());
        if (layout.roomAt(x, y, z) != room) {
            return null;
        }
        while (layout.carved(x, y + 1, z) && y < room.y() + room.ry() + 1) {
            y++;
        }
        return layout.carved(x, y + 1, z) ? null : y;
    }

    /** Whether a spot is further than a margin from the walls of every tunnel: the way through a room stays open. */
    private boolean clearOfTunnels(int x, int y, int z, double margin) {
        for (AnthillLayout.Tunnel tunnel : layout.tunnels()) {
            double dx = tunnel.x1() - tunnel.x0();
            double dy = tunnel.y1() - tunnel.y0();
            double dz = tunnel.z1() - tunnel.z0();
            double length = dx * dx + dy * dy + dz * dz;
            double t = length == 0 ? 0 : Math.clamp(((x - tunnel.x0()) * dx + (y - tunnel.y0()) * dy + (z - tunnel.z0()) * dz) / length, 0, 1);
            double px = tunnel.x0() + dx * t - x;
            double py = tunnel.y0() + dy * t - y;
            double pz = tunnel.z0() + dz * t - z;
            double reach = tunnel.radius() + margin;
            if (px * px + py * py + pz * pz < reach * reach) {
                return false;
            }
        }
        return true;
    }
}
