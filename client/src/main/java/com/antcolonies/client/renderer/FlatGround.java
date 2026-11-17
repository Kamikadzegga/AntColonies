package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.client.terrarium.TunnelLayout;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.content.block.ColonyPartBlock;

import java.util.Random;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The soil of a colony built flat, one bed shared by every block, with its tunnels and trails.
 * Sides: 0 north, 1 east, 2 south, 3 west; on a side, u runs 1 to 15 and v up (px).
 */
public final class FlatGround {
    /** The soil's surface, px: the same in every block of a flat colony. */
    public static final float GROUND = TunnelLayout.SURFACE;
    static final float BOTTOM = 1;
    /** Where the soil stops behind an outer glass, px from the block's edge. */
    static final float MIN = 2;
    static final float MAX = 14;
    static final Identifier TEXTURE = AntColonies.id("textures/entity/terrarium_inside.png");
    static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    static final float TEX = 64;
    /** How far the renderer's soil stands in front of the block model's own soil core (DataGen soilCore), px. */
    private static final float LIFT = 0.02F;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** The side whose neighbour lies at each end of a side's wall: [side][low u end, high u end]. */
    private static final int[][] END_SIDE = {{1, 3}, {2, 0}, {3, 1}, {0, 2}};
    private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};

    private FlatGround() {}

    /** The u a point at {@code w} px along a side is at (the soil's corners are u 1 and 15). */
    static float uOf(float w) {
        return 1 + (w - MIN) * 14 / (MAX - MIN);
    }

    /** One block's place in its build: which sides join, where its walls end and where tunnels cross into the next block. */
    public static final class Shape {
        public final boolean[] joined = new boolean[4];
        /** Per side and end (low u, high u): the u the wall ends at. */
        final float[][] endU = new float[4][2];
        /** Per side and end: the height a tunnel crosses there into the next wall, or NaN for none. */
        final float[][] crossV = new float[4][2];
        /** Small soil walls at inner corners, seen through a neighbour's glass: {x0, z0, x1, z1, side they face}. */
        final List<float[]> notches = new ArrayList<>();
        /** Per joined side: how far along the edge its trail crosses, px. */
        final float[] trailAt = new float[4];
        long seed;

        float x0() {
            return joined[3] ? 0 : MIN;
        }

        float x1() {
            return joined[1] ? 16 : MAX;
        }

        float z0() {
            return joined[0] ? 0 : MIN;
        }

        float z1() {
            return joined[2] ? 16 : MAX;
        }

        public boolean outer(int side) {
            return !joined[side];
        }

        public boolean anyJoined() {
            return joined[0] || joined[1] || joined[2] || joined[3];
        }
    }

    private static boolean flatPart(BlockState state) {
        return state.getBlock() instanceof ColonyPartBlock && !ColonyPartBlock.upright(state);
    }

    /** Whether a colony part's {@code side} touches another flat part (from its own block state; neighbours' are not read). */
    private static boolean connected(BlockState state, int side) {
        return state.getBlock() instanceof ColonyPartBlock && state.getValue(ColonyPartBlock.CONNECTED.get(SIDES[side]));
    }

    public static void extract(Shape shape, BlockGetter level, BlockPos pos, BlockState state) {
        shape.seed = pos.asLong();
        BlockState[] around = new BlockState[4];
        for (int side = 0; side < 4; side++) {
            around[side] = level.getBlockState(pos.relative(SIDES[side]));
            shape.joined[side] = connected(state, side) && flatPart(around[side]);
        }
        long wx = pos.getX() * 16L;
        long wz = pos.getZ() * 16L;
        for (int side = 0; side < 4; side++) {
            for (int end = 0; end < 2; end++) {
                int toward = END_SIDE[side][end];
                if (shape.outer(side) && !shape.joined[toward]) {
                    // An outer corner: the wall turns into the next side of this block; the tunnel turns with it.
                    float w = end == 0 ? MIN : MAX;
                    shape.endU[side][end] = end == 0 ? 1 : 15;
                    float[] at = wallPoint(side, w);
                    shape.crossV[side][end] = crossHeight(wx + (long) at[0], wz + (long) at[1]);
                } else {
                    float w = end == 0 ? 0 : 16;
                    shape.endU[side][end] = uOf(w);
                    // Joined: the wall runs on into the neighbour's if its same side is outer too; else it ends at an inner corner.
                    boolean runsOn = shape.outer(side) && flatPart(around[toward]) && !connected(around[toward], side);
                    float[] at = wallPoint(side, w);
                    shape.crossV[side][end] = runsOn ? crossHeight(wx + (long) at[0], wz + (long) at[1]) : Float.NaN;
                }
            }
        }
        // Inner corners: a little of this block's soil shows behind a neighbour's glass.
        shape.notches.clear();
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                if (a == b || (a + 2) % 4 == b || !shape.joined[a] || !shape.joined[b]) {
                    continue;
                }
                if (!connected(around[a], b)) {
                    shape.notches.add(notch(a, b));
                }
            }
        }
        for (int side = 0; side < 4; side++) {
            if (shape.joined[side]) {
                // Seeded by where the edge's middle is in the world, so both blocks meet the trail at the same point.
                float[] mid = edgeMiddle(side);
                shape.trailAt[side] = 8 + (float) (hash(wx + (long) mid[0], wz + (long) mid[1], 77) - 0.5) * 5;
            }
        }
    }

    /** The point {@code w} px along a side's wall line, in the block: {x, z}. */
    private static float[] wallPoint(int side, float w) {
        return switch (side) {
            case 0 -> new float[] {16 - w, MIN};
            case 1 -> new float[] {MAX, 16 - w};
            case 2 -> new float[] {w, MAX};
            default -> new float[] {MIN, w};
        };
    }

    private static float[] edgeMiddle(int side) {
        return switch (side) {
            case 0 -> new float[] {8, 0};
            case 1 -> new float[] {16, 8};
            case 2 -> new float[] {8, 16};
            default -> new float[] {0, 8};
        };
    }

    /** The little wall on side {@code a} of the block, in the band next to side {@code b}. */
    private static float[] notch(int a, int b) {
        float lo;
        float hi;
        // The band along a's edge, next to b.
        if (b == 1 || b == 2) {
            lo = MAX;
            hi = 16;
        } else {
            lo = 0;
            hi = MIN;
        }
        return switch (a) {
            case 0 -> new float[] {lo, 0, hi, 0, 0};
            case 2 -> new float[] {lo, 16, hi, 16, 2};
            case 1 -> new float[] {16, lo, 16, hi, 1};
            default -> new float[] {0, lo, 0, hi, 3};
        };
    }

    private static double hash(long x, long z, long salt) {
        // SplitMix64's finaliser.
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The height a tunnel crosses at a wall's join or corner, the same for both walls that meet there. */
    private static float crossHeight(long x, long z) {
        return 2.5F + (float) hash(x, z, 31) * (GROUND - 4.4F);
    }

    // --- Colours by climate (the Formicarium's soil and surface) ---

    public static int soilColor(Climate climate) {
        return switch (climate) {
            case COLD -> 0xFF7A6857;
            case HOT -> 0xFFD6BA7A;
            case UNDERGROUND -> 0xFF75716B;
            case NETHER -> 0xFF6E2E2A;
            default -> 0xFF8A6240;
        };
    }

    public static int topColor(Climate climate) {
        return switch (climate) {
            case COLD -> 0xFFF2F6FA;
            case HOT -> 0xFFE3CD8E;
            case UNDERGROUND -> 0xFF8A857E;
            case NETHER -> 0xFF8C2E26;
            default -> 0xFF6DA544;
        };
    }

    static int trailColor(int soil, int top) {
        int r = (int) (((top >> 16) & 0xFF) * 0.35F + ((soil >> 16) & 0xFF) * 0.65F);
        int g = (int) (((top >> 8) & 0xFF) * 0.35F + ((soil >> 8) & 0xFF) * 0.65F);
        int b = (int) ((top & 0xFF) * 0.35F + (soil & 0xFF) * 0.65F);
        return 0xFF000000 | Math.min(255, (int) (r * 1.08F)) << 16 | Math.min(255, (int) (g * 1.08F)) << 8 | Math.min(255, (int) (b * 1.08F));
    }

    // --- Drawing ---

    private static float[][] uvRect(float u0, float v0, float u1, float v1) {
        return new float[][] {{u0 / TEX, v1 / TEX}, {u1 / TEX, v1 / TEX}, {u1 / TEX, v0 / TEX}, {u0 / TEX, v0 / TEX}};
    }

    static void soil(Geometry g, Shape shape, int soil, int top, boolean[] facing) {
        float x0 = shape.x0();
        float x1 = shape.x1();
        float z0 = shape.z0();
        float z1 = shape.z1();
        float lo = MIN - LIFT;
        float hi = MAX + LIFT;
        float y0 = BOTTOM;
        float y1 = GROUND + LIFT;
        float tv0 = 16 - GROUND;
        float tv1 = 16 - BOTTOM;
        if (shape.outer(0) && facing[0]) {
            g.quadUv(new float[][] {{x1, y0, lo}, {x0, y0, lo}, {x0, y1, lo}, {x1, y1, lo}}, uvRect(16 - x1, tv0, 16 - x0, tv1),
                    Geometry.shade(soil, 0.85F), NORMALS[0]);
        }
        if (shape.outer(2) && facing[2]) {
            g.quadUv(new float[][] {{x0, y0, hi}, {x1, y0, hi}, {x1, y1, hi}, {x0, y1, hi}}, uvRect(x0, tv0, x1, tv1),
                    Geometry.shade(soil, 0.85F), NORMALS[2]);
        }
        if (shape.outer(3) && facing[3]) {
            g.quadUv(new float[][] {{lo, y0, z0}, {lo, y0, z1}, {lo, y1, z1}, {lo, y1, z0}}, uvRect(z0, tv0, z1, tv1),
                    Geometry.shade(soil, 0.7F), NORMALS[3]);
        }
        if (shape.outer(1) && facing[1]) {
            g.quadUv(new float[][] {{hi, y0, z1}, {hi, y0, z0}, {hi, y1, z0}, {hi, y1, z1}}, uvRect(16 - z1, tv0, 16 - z0, tv1),
                    Geometry.shade(soil, 0.7F), NORMALS[1]);
        }
        for (float[] n : shape.notches) {
            int side = (int) n[4];
            float[] normal = NORMALS[side];
            float out = LIFT;
            float[][] corners = {{n[0] + normal[0] * out, y0, n[1] + normal[2] * out}, {n[2] + normal[0] * out, y0, n[3] + normal[2] * out},
                    {n[2] + normal[0] * out, y1, n[3] + normal[2] * out}, {n[0] + normal[0] * out, y1, n[1] + normal[2] * out}};
            g.quadUv(corners, uvRect(4, tv0, 6, tv1), Geometry.shade(soil, side % 2 == 0 ? 0.85F : 0.7F), normal);
        }
        g.quadUv(new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, uvRect(32 + x0, z0, 32 + x1, z1), top,
                new float[] {0, 1, 0});
    }

    private static final byte NONE = 0;
    private static final byte TUNNEL = 1;
    private static final byte CHAMBER = 2;
    private static final byte CRYSTAL = 3;

    /** The w (px along a side, 0 to 16) of a layout's u. */
    static float wOf(float u) {
        return MIN + (u - 1) * (MAX - MIN) / 14;
    }

    /** The bottom of a chamber, px: whole pixels, so its floor is a straight row the ants and the Queen stand on. */
    public static float chamberFloor(float v, float radiusV) {
        return Math.max(1, Math.round(v - 0.8F * radiusV));
    }

    /** Where an ant's feet go in a tunnel whose line is at {@code v}: the floor of the 1 px tunnel there. */
    public static float tunnelFloor(float v) {
        return Mth.floor(v) + 0.02F;
    }

    /**
     * A side's tunnels (1 px high) and chambers (2 px high), cut on the pixel grid between its wall's ends.
     * {@code hollows} are {u, v, radiusU, radiusV}, with a fifth value 1 for a crystal chamber.
     */
    static void tunnels(Geometry g, int side, Shape shape, List<float[][]> paths, List<float[]> hollows, int dark) {
        int rows = (int) GROUND;
        byte[][] grid = new byte[16][rows];
        for (float[][] path : paths) {
            for (int i = 1; i < path.length; i++) {
                float w0 = wOf(path[i - 1][0]);
                float w1 = wOf(path[i][0]);
                float v0 = path[i - 1][1];
                float v1 = path[i][1];
                int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(w1 - w0), Math.abs(v1 - v0)) * 4));
                int lastI = Integer.MIN_VALUE;
                int lastJ = Integer.MIN_VALUE;
                for (int s = 0; s <= steps; s++) {
                    float t = s / (float) steps;
                    // Where it steps both across and up, the corner pixel is filled too: no diagonal dots.
                    int ci = Mth.floor(Mth.lerp(t, w0, w1));
                    int cj = Mth.floor(Mth.lerp(t, v0, v1));
                    if (lastI != Integer.MIN_VALUE && ci != lastI && cj != lastJ) {
                        mark(grid, ci, lastJ, TUNNEL);
                    }
                    mark(grid, ci, cj, TUNNEL);
                    lastI = ci;
                    lastJ = cj;
                }
            }
        }
        for (float[] h : hollows) {
            byte kind = h.length > 4 && h[4] > 0 ? CRYSTAL : CHAMBER;
            float w = wOf(h[0]);
            // Chambers are long and low, as ants dig them: about two thirds as wide as the layout says, 2 px high.
            float rw = Math.max(1.5F, h[2] * 0.6F * (MAX - MIN) / 14);
            int left = Math.round(w - rw);
            int right = Math.round(w + rw);
            int bottom = (int) chamberFloor(h[1], h[3]);
            int top = Math.min(rows, bottom + 2);
            for (int i = left; i < right; i++) {
                for (int j = bottom; j < top; j++) {
                    // The top corners cut off, for a chamber at least 4 wide and 3 high.
                    boolean corner = j == top - 1 && (i == left || i == right - 1) && right - left >= 4 && top - bottom >= 3;
                    if (!corner) {
                        mark(grid, i, j, kind);
                    }
                }
            }
        }
        // Only between the wall's ends (a corner of the soil, or the block's edge where the wall runs on).
        int from = Math.round(wOf(shape.endU[side][0]));
        int to = Math.round(wOf(shape.endU[side][1]));
        float out = 0.03F + LIFT;
        float[] n = NORMALS[side];
        int crystal = Geometry.shade(dark, 1.3F);
        for (int j = 1; j < rows; j++) {
            int i = Math.max(0, from);
            while (i < Math.min(16, to)) {
                byte kind = grid[i][j];
                if (kind == NONE) {
                    i++;
                    continue;
                }
                int start = i;
                while (i < Math.min(16, to) && grid[i][j] == kind) {
                    i++;
                }
                float[][] uv = kind == TUNNEL ? uvRect(18 + start % 4, 2, 18 + start % 4 + (i - start), 4)
                        : uvRect(18 + start % 4, 6 + j, 18 + start % 4 + (i - start), 7 + j);
                float u0 = uOf(start);
                float u1 = uOf(i);
                float[][] corners = {TerrariumRenderer.onFace(side, u0, j, out, new float[3]),
                        TerrariumRenderer.onFace(side, u1, j, out, new float[3]),
                        TerrariumRenderer.onFace(side, u1, j + 1, out, new float[3]),
                        TerrariumRenderer.onFace(side, u0, j + 1, out, new float[3])};
                g.quadUv(corners, uv, kind == CRYSTAL ? crystal : dark, n);
            }
        }
    }

    private static void mark(byte[][] grid, int i, int j, byte kind) {
        if (i >= 0 && i < grid.length && j >= 1 && j < grid[i].length && grid[i][j] < kind) {
            grid[i][j] = kind;
        }
    }

    /**
     * A tunnel from a point to where it crosses into the next wall, level at the end so both blocks' tunnels meet.
     * {@code highEnd}: the crossing is at the side's high-u end.
     */
    static float[][] toCrossing(float[] from, float[] crossing, boolean highEnd, float bow) {
        float[] level = {crossing[0] + (highEnd ? -2.4F : 2.4F), crossing[1]};
        float[][] bend = curve(from, level, bow);
        float[][] out = new float[bend.length + 1][];
        System.arraycopy(bend, 0, out, 0, bend.length);
        out[bend.length] = crossing;
        return out;
    }

    /** Points along a gentle curve from a to b, bowed by {@code bow} (px, across the line). */
    static float[][] curve(float[] a, float[] b, float bow) {
        int steps = 8;
        float mu = (a[0] + b[0]) / 2;
        float mv = (a[1] + b[1]) / 2;
        float du = b[0] - a[0];
        float dv = b[1] - a[1];
        float len = Math.max(1e-4F, Mth.sqrt(du * du + dv * dv));
        float cu = mu - dv / len * bow;
        float cv = mv + du / len * bow;
        float[][] points = new float[steps + 1][];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float s = 1 - t;
            points[i] = new float[] {s * s * a[0] + 2 * s * t * cu + t * t * b[0], s * s * a[1] + 2 * s * t * cv + t * t * b[1]};
        }
        return points;
    }

    /** A side's tunnels as paths: what a chamber's soil shows behind its glass (the Formicarium has its own, see {@link #exits}). */
    static List<float[][]> chamberPaths(Shape shape, int side, List<float[]> hollows) {
        List<float[][]> paths = new ArrayList<>();
        Random random = new Random(shape.seed * 31 + side * 7919L);
        float[] mid = {4.5F + random.nextFloat() * 7, 2.8F + random.nextFloat() * (GROUND - 5F)};
        int ends = 0;
        for (int end = 0; end < 2; end++) {
            if (!Float.isNaN(shape.crossV[side][end])) {
                float[] at = {shape.endU[side][end], shape.crossV[side][end]};
                paths.add(toCrossing(mid, at, end == 1, (random.nextFloat() - 0.5F) * 1.6F));
                ends++;
            }
        }
        if (ends < 2 || random.nextFloat() < 0.5F) {
            // A dead end, or a resting place on the way: a small chamber.
            hollows.add(new float[] {mid[0], mid[1], 1.3F + random.nextFloat() * 0.6F, 0.8F + random.nextFloat() * 0.3F});
        }
        if (random.nextFloat() < 0.4F) {
            // A shaft up to the surface.
            paths.add(curve(mid, new float[] {mid[0] + (random.nextFloat() - 0.5F) * 3, GROUND + 0.4F}, (random.nextFloat() - 0.5F)));
        }
        return paths;
    }

    /** The Formicarium's way out on a side: from its nearest tunnel node to each place a tunnel crosses into the next wall. */
    static List<float[][]> exits(Shape shape, int side, TunnelLayout layout) {
        List<float[][]> paths = new ArrayList<>();
        Random random = new Random(shape.seed * 17 + side * 131L);
        for (int end = 0; end < 2; end++) {
            if (Float.isNaN(shape.crossV[side][end])) {
                continue;
            }
            float[] at = {shape.endU[side][end], shape.crossV[side][end]};
            TunnelLayout.Node best = null;
            float bestScore = Float.MAX_VALUE;
            for (TunnelLayout.Node node : layout.nodes) {
                float score = Math.abs(node.u() - at[0]) + Math.abs(node.v() - at[1]) * 0.6F
                        + (node.type() == TunnelLayout.ChamberType.QUEEN ? 4 : 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            if (best != null) {
                paths.add(toCrossing(new float[] {best.u(), best.v()}, at, end == 1, (random.nextFloat() - 0.5F) * 1.4F));
            }
        }
        return paths;
    }

    /** Ants walking a side's tunnels back and forth, placed from the time alone; drawn into the side-view sprite buffer. */
    static void walkers(Geometry g, int side, List<float[][]> paths, AntLook look, float time, long seed, int carry) {
        for (int p = 0; p < paths.size(); p++) {
            float[][] path = paths.get(p);
            float length = 0;
            for (int i = 1; i < path.length; i++) {
                length += dist(path[i - 1], path[i]);
            }
            if (length < 2) {
                continue;
            }
            int ants = length > 9 ? 2 : 1;
            for (int a = 0; a < ants; a++) {
                long key = seed * 41 + side * 7 + p * 3 + a;
                float speed = 0.05F + (float) hash(key, a, 5) * 0.03F;
                float travel = time * speed + (float) hash(key, p, 9) * length * 2;
                float s = travel % (2 * length);
                boolean back = s > length;
                float along = back ? 2 * length - s : s;
                // Find the point and the slope there.
                float walked = 0;
                for (int i = 1; i < path.length; i++) {
                    float d = dist(path[i - 1], path[i]);
                    if (walked + d >= along || i == path.length - 1) {
                        float t = d < 1e-4F ? 0 : Math.clamp((along - walked) / d, 0, 1);
                        float u = Mth.lerp(t, path[i - 1][0], path[i][0]);
                        float v = Mth.lerp(t, path[i - 1][1], path[i][1]);
                        float du = path[i][0] - path[i - 1][0];
                        // Walking toward the path's start when coming back.
                        if (back) {
                            du = -du;
                        }
                        boolean positive = du >= 0;
                        int frame = Math.floorMod((int) (travel * 1.1F), 4);
                        TerrariumRenderer.drawSprite(g, side, u, tunnelFloor(v), positive, 0, Caste.WORKER, look, TerrariumRenderer.SPRITE_LENGTH,
                                frame, back ? carry : 0, g.light, 1);
                        break;
                    }
                    walked += d;
                }
            }
        }
    }

    private static float dist(float[] a, float[] b) {
        return Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
    }

    static void trails(Geometry g, Shape shape, int color) {
        Random random = new Random(shape.seed * 53);
        float[] centre = {8 + (random.nextFloat() - 0.5F) * 2.5F, 8 + (random.nextFloat() - 0.5F) * 2.5F};
        float y = GROUND + LIFT + 0.02F;
        float half = 0.75F;
        float[][] uv = uvRect(36, 4, 40, 8);
        float[] up = {0, 1, 0};
        for (int side = 0; side < 4; side++) {
            if (!shape.joined[side]) {
                continue;
            }
            float t = shape.trailAt[side];
            float[] edge;
            float[] inner;
            switch (side) {
                case 0 -> {
                    edge = new float[] {t, -0.01F};
                    inner = new float[] {t, 3.5F};
                }
                case 1 -> {
                    edge = new float[] {16.01F, t};
                    inner = new float[] {12.5F, t};
                }
                case 2 -> {
                    edge = new float[] {t, 16.01F};
                    inner = new float[] {t, 12.5F};
                }
                default -> {
                    edge = new float[] {-0.01F, t};
                    inner = new float[] {3.5F, t};
                }
            }
            float[][] bend = curve(centre, inner, (random.nextFloat() - 0.5F) * 2.5F);
            float[][] path = new float[bend.length + 1][];
            System.arraycopy(bend, 0, path, 0, bend.length);
            path[bend.length] = edge;
            for (int i = 1; i < path.length; i++) {
                float dx = path[i][0] - path[i - 1][0];
                float dz = path[i][1] - path[i - 1][1];
                float len = Math.max(1e-4F, Mth.sqrt(dx * dx + dz * dz));
                float nx = -dz / len * half;
                float nz = dx / len * half;
                g.quadUv(new float[][] {{path[i - 1][0] - nx, y, path[i - 1][1] - nz}, {path[i][0] - nx, y, path[i][1] - nz},
                        {path[i][0] + nx, y, path[i][1] + nz}, {path[i - 1][0] + nx, y, path[i - 1][1] + nz}}, uv, color, up);
            }
        }
    }

    static float[] trailCentre(Shape shape) {
        Random random = new Random(shape.seed * 53);
        return new float[] {8 + (random.nextFloat() - 0.5F) * 2.5F, 8 + (random.nextFloat() - 0.5F) * 2.5F};
    }

    /** Which outer sides of the block face the camera ({@code cx}, {@code cz}: the camera in the block, px). */
    static void facing(boolean[] facing, double cx, double cz) {
        facing[0] = cz < MIN;
        facing[1] = cx > MAX;
        facing[2] = cz > MAX;
        facing[3] = cx < MIN;
    }
}
