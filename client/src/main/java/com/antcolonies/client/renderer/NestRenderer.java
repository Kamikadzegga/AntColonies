package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.client.ClientHooks;
import com.antcolonies.client.model.SideSprites;
import com.antcolonies.client.nest.NestLayout;
import com.antcolonies.client.nest.NestVisuals;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.content.block.ColonyPartBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws an upright colony block: behind each glass, a slab with its {@link NestLayout} carved in, the chamber's contents
 * and the ants. Drawn as if facing north, then turned (half a turn more for the back).
 */
public final class NestRenderer {
    private static final Identifier TEXTURE = AntColonies.id("textures/entity/nest.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    private static final float TEX = 128;
    /** The side-view sprite sheet: 24x12 cells, four frames across; per caste a body, gaster and crystal row. */
    private static final Identifier SIDE_TEXTURE = AntColonies.id("textures/entity/ant_side.png");
    private static final RenderType SIDE = RenderTypes.entityCutout(SIDE_TEXTURE);
    private static final int SIDE_CELL_W = 24;
    private static final int SIDE_CELL_H = 12;
    private static final int SIDE_TEX_W = 96;
    private static final int SIDE_TEX_H = SideSprites.TEX_H;
    private static final int SIDE_CROP_ROW = 10;
    private static final int SIDE_PLATES_ROW = 13;
    private static final float SPRITE_LENGTH = 1.9F;

    /** Depths behind the block's front, px: the slab's face (just behind the glass) and the floor of what is carved. */
    public static final float SURFACE = 1;
    public static final float FLOOR = 3;
    private static final float[] FRONT = {0, 0, -1};
    private static final int N = NestLayout.N;

    public static class State extends BlockEntityRenderState {
        public @Nullable NestVisuals visuals;
        public @Nullable NestLayout layout;
        public Climate climate = Climate.TEMPERATE;
        public AntLook look = AntLook.UNKNOWN;
        public AntLook former = AntLook.UNKNOWN;
        public float partialTicks;
        public float time;
        /** Quarter turns from north. */
        public int turns;
        /** The camera is before one of the block's two glasses (and nothing of a colony stands against it): {@link #back} says which. */
        public boolean front;
        public boolean back;
        public boolean near;
        public boolean queen;
        /** An expedition site with ants out through its portal. */
        public boolean lit;
        /** Ants at work here (a job site's assigned ants shown, a linked chamber). */
        public int shown;
        public boolean linked;
        public int stage = 1;
        public final List<ItemStackRenderState> items = new ArrayList<>();
    }

    /** Fills what every colony block's state shares. */
    public static void extract(State state, BlockPos pos, @Nullable Level level, Direction facing, Vec3 camera, float partialTicks, double visualDistance) {
        state.partialTicks = partialTicks;
        state.time = level == null ? 0 : ClientHooks.animationTime(level.getGameTime(), partialTicks);
        state.turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        // Only the glass the camera is before shows anything: from the side there is nothing to draw.
        double ahead = (camera.x - (pos.getX() + 0.5)) * facing.getStepX() + (camera.z - (pos.getZ() + 0.5)) * facing.getStepZ();
        state.back = ahead < 0;
        BlockPos before = pos.relative(state.back ? facing.getOpposite() : facing);
        state.front = Math.abs(ahead) > 0.4 && (level == null || !(level.getBlockState(before).getBlock() instanceof ColonyPartBlock));
        state.near = Vec3.atCenterOf(pos).distanceToSqr(camera) < visualDistance * visualDistance;
        // Lit as the air before the glass is.
        if (level != null) {
            state.lightCoords = LevelRenderer.getLightCoords(level, before);
        }
    }

    public static void submit(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        NestLayout layout = state.layout;
        if (layout == null || !state.front) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90 * (state.turns + (state.back ? 2 : 0))));
        poseStack.translate(-0.5F, 0, -0.5F);
        int light = state.lightCoords;
        boolean back = state.back;
        collector.submitCustomGeometry(poseStack, SOLID, (pose, buffer) -> {
            mirror = back;
            Geometry g = new Geometry(pose, buffer, TEX, light);
            drawSlab(g, layout, state.climate);
            drawFeatures(g, layout, state);
        });
        if (state.near && state.visuals != null) {
            NestVisuals visuals = state.visuals;
            collector.submitCustomGeometry(poseStack, SIDE, (pose, buffer) -> {
                mirror = back;
                Geometry g = new Geometry(pose, buffer, TEX, light);
                drawQueen(g, layout, visuals, state);
                drawHanging(g, layout, state);
                for (NestVisuals.Actor actor : visuals.actors) {
                    if (!actor.hidden) {
                        drawActor(g, actor, state);
                    }
                }
            });
        }
        drawItems(state, layout, poseStack, collector);
        poseStack.popPose();
    }

    // --- The slab ---

    /** A baked quad of the slab: four corners (x, y, z each), the texel rectangle in its strip, the strip, shade and normal. */
    private record SlabQuad(float[][] corners, float u0, float v0, float u1, float v1, int strip, int color, float[] normal) {}

    private static final float[] UP_NORMAL = {0, 1, 0};
    private static final float[] DOWN_NORMAL = {0, -1, 0};
    private static final float[] EAST_NORMAL = {1, 0, 0};
    private static final float[] WEST_NORMAL = {-1, 0, 0};

    private static void drawSlab(Geometry g, NestLayout layout, Climate climate) {
        // Baked once for each glass: the back's is the front's with left and right swapped.
        @SuppressWarnings("unchecked")
        List<SlabQuad>[] cache = layout.renderCache instanceof List<?>[] cached ? (List<SlabQuad>[]) cached : null;
        if (cache == null) {
            @SuppressWarnings("unchecked")
            List<SlabQuad>[] created = new List[2];
            cache = created;
            layout.renderCache = cache;
        }
        int side = mirror ? 1 : 0;
        if (cache[side] == null) {
            cache[side] = bake(layout);
        }
        List<SlabQuad> quads = cache[side];
        float row = climateRow(climate) * 16;
        float[][] uv = new float[4][2];
        for (SlabQuad q : quads) {
            float u0 = (q.strip() * 16 + q.u0()) / TEX;
            float u1 = (q.strip() * 16 + q.u1()) / TEX;
            float v0 = (row + q.v0()) / TEX;
            float v1 = (row + q.v1()) / TEX;
            uv[0][0] = u0;
            uv[0][1] = v1;
            uv[1][0] = u1;
            uv[1][1] = v1;
            uv[2][0] = u1;
            uv[2][1] = v0;
            uv[3][0] = u0;
            uv[3][1] = v0;
            g.quadUv(q.corners(), uv, q.color(), q.normal());
        }
    }

    private static int climateRow(Climate climate) {
        return switch (climate) {
            case COLD -> 1;
            case HOT -> 2;
            case UNDERGROUND -> 3;
            case NETHER -> 4;
            case END -> 5;
            default -> 0;
        };
    }

    private static int gray(float shade) {
        int v = Math.clamp(Math.round(shade * 255), 0, 255);
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    /** Set at the start of each drawing pass (they run one after another on the render thread): drawing the back glass. */
    private static boolean mirror;

    /** Where a point of the face is in the block (as it stands with the glass being drawn to the north), px. */
    private static float[] at(float u, float v, float depth) {
        return new float[] {mirror ? u : 16 - u, v, depth};
    }

    /** The slab's quads (face, carved floor, walls between), merged into few rectangles and shaded as if lit from the top left. */
    private static List<SlabQuad> bake(NestLayout layout) {
        List<SlabQuad> quads = new ArrayList<>();
        int[] shade = new int[N * N];
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                if (layout.carved(u, v)) {
                    boolean ceiling = v + 1 < N && !layout.carved(u, v + 1);
                    boolean wall = u > 0 && !layout.carved(u - 1, v);
                    shade[v * N + u] = ceiling ? 5 : wall ? 4 : 3;
                } else {
                    shade[v * N + u] = layout.carved(u, v + 1) ? 1 : layout.carved(u, v - 1) ? 2 : 0;
                }
            }
        }
        float[] factors = {1, 1.07F, 0.9F, 1, 0.84F, 0.68F};
        boolean[] done = new boolean[N * N];
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                if (done[v * N + u]) {
                    continue;
                }
                int kind = shade[v * N + u];
                int w = 1;
                while (u + w < N && !done[v * N + u + w] && shade[v * N + u + w] == kind) {
                    w++;
                }
                int h = 1;
                grow:
                while (v + h < N) {
                    for (int i = 0; i < w; i++) {
                        if (done[(v + h) * N + u + i] || shade[(v + h) * N + u + i] != kind) {
                            break grow;
                        }
                    }
                    h++;
                }
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        done[(v + j) * N + u + i] = true;
                    }
                }
                boolean floor = kind >= 3;
                float depth = floor ? FLOOR : SURFACE;
                quads.add(new SlabQuad(new float[][] {at(u, v, depth), at(u + w, v, depth), at(u + w, v + h, depth), at(u, v + h, depth)},
                        u, N - v - h, u + w, N - v, floor ? 1 : 0, gray(factors[kind]), FRONT));
            }
        }
        // Walls, in runs: under a ceiling (seen from below), over a floor, and at either side.
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                if (!layout.carved(u, v)) {
                    continue;
                }
                if (v + 1 < N && !layout.carved(u, v + 1) && (u == 0 || !layout.carved(u - 1, v) || layout.carved(u - 1, v + 1))) {
                    int w = 1;
                    while (u + w < N && layout.carved(u + w, v) && !layout.carved(u + w, v + 1)) {
                        w++;
                    }
                    quads.add(wall(u, v + 1, u + w, v + 1, u, N - v - 1, u + w, N - v, 0.5F, DOWN_NORMAL));
                }
                if (v > 0 && !layout.carved(u, v - 1) && (u == 0 || !layout.carved(u - 1, v) || layout.carved(u - 1, v - 1))) {
                    int w = 1;
                    while (u + w < N && layout.carved(u + w, v) && !layout.carved(u + w, v - 1)) {
                        w++;
                    }
                    quads.add(wall(u, v, u + w, v, u, N - v - 1, u + w, N - v, 1.0F, UP_NORMAL));
                }
                if (u > 0 && !layout.carved(u - 1, v) && (v == 0 || !layout.carved(u, v - 1) || layout.carved(u - 1, v - 1))) {
                    int h = 1;
                    while (v + h < N && layout.carved(u, v + h) && !layout.carved(u - 1, v + h)) {
                        h++;
                    }
                    // A hollow's low-u wall faces higher u: west as the block stands facing north, east when the back is drawn.
                    quads.add(wall(u, v, u, v + h, u, N - v - h, u + 1, N - v, 0.62F, mirror ? EAST_NORMAL : WEST_NORMAL));
                }
                if (u + 1 < N && !layout.carved(u + 1, v) && (v == 0 || !layout.carved(u, v - 1) || layout.carved(u + 1, v - 1))) {
                    int h = 1;
                    while (v + h < N && layout.carved(u, v + h) && !layout.carved(u + 1, v + h)) {
                        h++;
                    }
                    quads.add(wall(u + 1, v, u + 1, v + h, u, N - v - h, u + 1, N - v, 0.8F, mirror ? WEST_NORMAL : EAST_NORMAL));
                }
            }
        }
        // The slab's own edges, on every side where no tunnel leaves the block.
        if (layout.gates[NestLayout.LEFT] < 0) {
            quads.add(cap(0, 0, 0, N, 0, 0, 1, N, mirror ? WEST_NORMAL : EAST_NORMAL));
        }
        if (layout.gates[NestLayout.RIGHT] < 0) {
            quads.add(cap(N, 0, N, N, N - 1, 0, N, N, mirror ? EAST_NORMAL : WEST_NORMAL));
        }
        if (layout.gates[NestLayout.UP] < 0) {
            quads.add(cap(0, N, N, N, 0, 0, N, 1, UP_NORMAL));
        }
        if (layout.gates[NestLayout.DOWN] < 0) {
            quads.add(cap(0, 0, N, 0, 0, N - 1, N, N, DOWN_NORMAL));
        }
        return quads;
    }

    /** The slab's edge along a side of the block, from its face down to the floor's depth, in the face's own texture. */
    private static SlabQuad cap(float ua, float va, float ub, float vb, float u0, float v0, float u1, float v1, float[] normal) {
        return new SlabQuad(new float[][] {at(ua, va, SURFACE), at(ub, vb, SURFACE), at(ub, vb, FLOOR), at(ua, va, FLOOR)}, u0, v0, u1, v1, 0,
                gray(0.8F), normal);
    }

    private static SlabQuad wall(float ua, float va, float ub, float vb, float u0, float v0, float u1, float v1, float shade, float[] normal) {
        return new SlabQuad(new float[][] {at(ua, va, SURFACE), at(ub, vb, SURFACE), at(ub, vb, FLOOR), at(ua, va, FLOOR)}, u0, v0, u1, v1, 1,
                gray(shade), normal);
    }

    // --- What lies in the chambers ---

    /** A flat rectangle of one color on the face, at a depth (the atlas's white patch, tinted). */
    private static void flat(Geometry g, float u0, float v0, float u1, float v1, float depth, int color) {
        float[][] corners = {at(u0, v0, depth), at(u1, v0, depth), at(u1, v1, depth), at(u0, v1, depth)};
        float[][] uv = {{120 / TEX, 121 / TEX}, {121 / TEX, 121 / TEX}, {121 / TEX, 120 / TEX}, {120 / TEX, 120 / TEX}};
        g.quadUv(corners, uv, color, FRONT);
    }

    private static int hash(int u, int v, int salt) {
        return (int) (NestLayout.mix(u * 73856093L ^ v * 19349663L ^ salt * 83492791L) & 0x7FFFFFFF);
    }

    private static void drawFeatures(Geometry g, NestLayout layout, State state) {
        NestVisuals visuals = state.visuals;
        float brood = visuals == null ? 0 : visuals.brood;
        float larvae = visuals == null ? 0 : visuals.larvae;
        float food = visuals == null ? 0 : visuals.food;
        int carry = visuals == null ? 0xFFD9B26A : visuals.carryColor;
        boolean formicarium = layout.kind == NestLayout.Kind.FORMICARIUM;
        boolean nether = layout.kind == NestLayout.Kind.NETHER_FORAGING_SITE;
        int light = g.light;
        int broodSpots = 0;
        int crumbSpots = 0;
        for (NestLayout.Feature feature : layout.features) {
            broodSpots += feature.type() == 'e' ? 1 : 0;
            crumbSpots += feature.type() == 'c' ? 1 : 0;
        }
        int broodSeen = 0;
        int crumbSeen = 0;
        int fungus = 0;
        float deep = FLOOR - 0.04F;
        for (NestLayout.Feature feature : layout.features) {
            int u = feature.u();
            int v = feature.v();
            int h = hash(u, v, feature.type());
            switch (feature.type()) {
                case 'e' -> {
                    // Brood, as full as the colony's eggs and larvae call for: the fuller, the more spots have some.
                    int order = (broodSeen++ * 5 + 2) % Math.max(1, broodSpots);
                    if (brood <= 0 || (order + 0.5F) / broodSpots > brood) {
                        break;
                    }
                    if ((h % 100) / 100F < larvae) {
                        float x = u + 0.1F + (h >> 8) % 20 / 100F;
                        flat(g, x, v, x + 0.75F, v + 0.36F, deep - 0.3F, 0xFFEDE0C0);
                        flat(g, x + 0.08F, v + 0.36F, x + 0.6F, v + 0.5F, deep - 0.3F, 0xFFD9C9A2);
                    } else {
                        for (int i = 0; i < 3; i++) {
                            float x = u + 0.08F + i * 0.3F;
                            float y = v + ((h >> (4 + i * 3)) % 3) * 0.12F;
                            flat(g, x, y, x + 0.26F, y + 0.34F, deep - 0.3F, i == 1 ? 0xFFFFFDF4 : 0xFFF2ECDB);
                        }
                    }
                }
                case 'c' -> {
                    float fill = formicarium ? food : state.shown > 0 ? Math.min(1, 0.35F + state.shown / 6F) : 0;
                    int order = (crumbSeen++ * 3 + 1) % Math.max(1, crumbSpots);
                    if (fill <= 0 || (order + 0.5F) / crumbSpots > fill) {
                        break;
                    }
                    int color = carry;
                    flat(g, u + 0.05F, v, u + 0.55F, v + 0.45F, deep - 0.3F, Geometry.shade(color, 0.85F));
                    flat(g, u + 0.45F, v, u + 0.95F, v + 0.4F, deep - 0.25F, color);
                    if ((h & 1) == 0) {
                        flat(g, u + 0.25F, v + 0.4F, u + 0.7F, v + 0.8F, deep - 0.2F, Geometry.shade(color, 1.12F));
                    }
                }
                case 'w' -> {
                    flat(g, u + 0.1F, v, u + 0.6F, v + 0.35F, deep - 0.2F, 0xFF4A3220);
                    flat(g, u + 0.5F, v, u + 0.95F, v + 0.25F, deep - 0.2F, 0xFF3A2618);
                }
                case 'k' -> {
                    // A royal cell: silk, lighter toward the top.
                    boolean top = !layout.carved(u, v + 1);
                    flat(g, u, v, u + 1, v + 1, FLOOR - 0.6F, top ? 0xFFF1E6C8 : 0xFFDDCDA6);
                    flat(g, u + ((h & 1) == 0 ? 0.1F : 0.6F), v + 0.25F, u + ((h & 1) == 0 ? 0.4F : 0.9F), v + 0.75F, FLOOR - 0.62F, top ? 0xFFFFF8E2 : 0xFFC9B88E);
                }
                case 'm' -> {
                    flat(g, u, v, u + 1, v + 0.5F, FLOOR - 0.8F, 0xFFBF962B);
                    flat(g, u, v + 0.5F, u + 1, v + 0.75F, FLOOR - 0.8F, 0xFFF0CF5A);
                }
                case 's' -> {
                    flat(g, u, v + 0.25F, u + 1, v + 1, SURFACE + 0.5F, (h & 3) == 0 ? 0xFF94693F : 0xFF9F7448);
                    flat(g, u, v, u + 1, v + 0.25F, SURFACE + 0.5F, 0xFF6A4728);
                }
                case 'f' -> {
                    boolean red = fungus++ % 2 == 0;
                    float tall = 0.6F + (h % 5) * 0.1F;
                    flat(g, u + 0.35F, v, u + 0.65F, v + tall, deep - 0.4F, 0xFFE8DDC8);
                    flat(g, u + 0.05F, v + tall, u + 0.95F, v + tall + 0.4F, deep - 0.45F, red ? 0xFFB5462E : 0xFFC9A57A);
                    flat(g, u + 0.25F, v + tall + 0.4F, u + 0.75F, v + tall + 0.6F, deep - 0.45F, red ? 0xFFCB5A3E : 0xFFD9BA90);
                }
                case 'b' -> {
                    flat(g, u + 0.05F, v + 0.05F, u + 0.95F, v + 0.3F, deep - 0.2F, 0xFFE9E3D2);
                    flat(g, u + ((h & 1) == 0 ? 0 : 0.7F), v, u + ((h & 1) == 0 ? 0.3F : 1), v + 0.45F, deep - 0.22F, 0xFFD6CDB6);
                }
                case 'l' -> {
                    int liquid = state.look.crop().isPresent() && state.shown > 0 ? 0xFF000000 | state.look.crop().get() : 0;
                    if (liquid != 0) {
                        g.light = state.look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
                        float level = 0.55F + 0.08F * Mth.sin(state.time * 0.05F + u);
                        flat(g, u, v, u + 1, v + level, FLOOR - 0.5F, liquid);
                        flat(g, u, v + level - 0.15F, u + 1, v + level, FLOOR - 0.52F, Geometry.shade(liquid, 1.25F));
                        g.light = light;
                    } else {
                        flat(g, u, v, u + 1, v + 0.2F, FLOOR - 0.5F, 0xFF8A5236);
                    }
                }
                case 'o' -> flat(g, u, v, u + 1, v + 1, FLOOR - 0.9F, nether ? ((h & 3) == 0 ? 0xFF2A1B3D : 0xFF1B1029) : ((h & 3) == 0 ? 0xFFC9CC8C : 0xFFDBDE9E));
                case 'p' -> {
                    if (state.lit) {
                        g.light = LightCoordsUtil.FULL_BRIGHT;
                        float swirl = 0.75F + 0.25F * Mth.sin(state.time * 0.15F + u * 1.7F + v * 2.3F);
                        flat(g, u, v, u + 1, v + 1, FLOOR - 0.7F, Geometry.shade(nether ? 0xFF9A3DF0 : 0xFF14463A, swirl));
                        if (!nether && (hash(u, v, (int) (state.time / 12)) & 3) == 0) {
                            flat(g, u + 0.3F, v + 0.3F, u + 0.6F, v + 0.6F, FLOOR - 0.72F, 0xFFD8FFF0);
                        }
                        g.light = light;
                    } else {
                        flat(g, u, v, u + 1, v + 1, FLOOR - 0.7F, nether ? 0xFF120A1C : 0xFF0C1614);
                    }
                }
                case 'g' -> {
                    g.light = LightCoordsUtil.FULL_BRIGHT;
                    float pulse = 0.8F + 0.2F * Mth.sin(state.time * 0.1F);
                    flat(g, u, v, u + 1, v + 1, FLOOR - 0.6F, Geometry.shade(0xFF5DBE97, state.linked ? pulse : 0.45F));
                    flat(g, u + 0.25F, v + 0.25F, u + 0.75F, v + 0.75F, FLOOR - 0.62F, Geometry.shade(0xFFDBFFEE, state.linked ? pulse : 0.45F));
                    g.light = light;
                    flat(g, u + 0.3F, v - 1, u + 0.7F, v, FLOOR - 0.6F, 0xFF804A26);
                }
                case 'h' -> flat(g, u, v, u + 1, v + 1, deep, 0xFF140C08);
                case 'H' -> {
                    if (layout.kind == NestLayout.Kind.FORAGING_SITE) {
                        // The way out: daylight at the end of the tunnel.
                        g.light = LightCoordsUtil.FULL_BRIGHT;
                        flat(g, u, v, u + 1, v + 1, deep, state.linked ? 0xFFCFE8FF : 0xFF7F8E9C);
                        g.light = light;
                    } else {
                        flat(g, u, v, u + 1, v + 1, deep, 0xFF1A100A);
                    }
                }
                default -> {
                }
            }
        }
        // A stage 4 colony of a crystal species: crystals grow from the Queen's ceiling.
        if (formicarium && state.stage >= 4 && state.look.crystal().isPresent()) {
            int crystal = 0xFF000000 | state.look.crystal().get();
            g.light = state.look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            int main = layout.find(NestLayout.Spot.MAIN);
            for (int[] cell : layout.nodes.get(main).cells) {
                if (!layout.carved(cell[0], cell[1] + 1) && hash(cell[0], cell[1], 7) % 5 < 2) {
                    float tall = 0.5F + hash(cell[0], cell[1], 9) % 4 * 0.15F;
                    flat(g, cell[0] + 0.3F, cell[1] + 1 - tall, cell[0] + 0.7F, cell[1] + 1, deep - 0.3F, crystal);
                    flat(g, cell[0] + 0.4F, cell[1] + 1 - tall - 0.2F, cell[0] + 0.6F, cell[1] + 1 - tall, deep - 0.3F, Geometry.shade(crystal, 1.3F));
                }
            }
            g.light = light;
        }
    }

    private static void drawItems(State state, NestLayout layout, PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.items.isEmpty()) {
            return;
        }
        int index = 0;
        for (NestLayout.Feature feature : layout.features) {
            if (feature.type() != 'i') {
                continue;
            }
            if (index >= state.items.size()) {
                break;
            }
            poseStack.pushPose();
            poseStack.translate((state.back ? feature.u() + 0.5F : 16 - (feature.u() + 0.5F)) / 16, (feature.v() + 0.95F) / 16, (FLOOR - 0.9F) / 16);
            poseStack.scale(0.125F, 0.125F, 0.125F);
            state.items.get(index++).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    // --- Ants ---

    private static void drawQueen(Geometry g, NestLayout layout, NestVisuals visuals, State state) {
        if (!state.queen) {
            return;
        }
        for (NestLayout.Feature feature : layout.features) {
            if (feature.type() != 'q') {
                continue;
            }
            // She walks her chamber's floor (NestVisuals). Resting, her gaster swells a little and she shifts a leg now and then.
            boolean walking = visuals.queenMoving && !Float.isNaN(visuals.queenU);
            float u = Float.isNaN(visuals.queenU) ? feature.u() + 0.5F : Mth.lerp(state.partialTicks, visuals.prevQueenU, visuals.queenU);
            float walked = Mth.lerp(state.partialTicks, visuals.prevQueenWalked, visuals.queenWalked);
            float pulse = walking ? 1 : 1 + (visuals.queenLaying ? 0.06F : 0.02F) * Mth.sin(state.time * 0.25F);
            int frame = walking ? Math.floorMod((int) (walked * 2.4F), 4) : (int) (state.time / 40) % 6 == 0 ? 2 : 0;
            int light = visuals.evolutionGlow > 0.05F ? LightCoordsUtil.FULL_BRIGHT : g.light;
            float length = SPRITE_LENGTH * 1.75F;
            float height = length * state.look.size() * SIDE_CELL_H / SIDE_CELL_W;
            drawSprite(g, u, feature.v() + height / 2, visuals.queenRight, 0, Caste.QUEEN, state.look, length, frame, 0, light, pulse, FLOOR - 0.75F);
        }
    }

    private static void drawHanging(Geometry g, NestLayout layout, State state) {
        if (layout.kind != NestLayout.Kind.LIQUID_CHAMBER || state.shown <= 0 || state.look.crop().isEmpty()) {
            return;
        }
        int index = 0;
        for (NestLayout.Feature feature : layout.features) {
            if (feature.type() != 'r' || index++ >= Math.max(1, state.shown - 1)) {
                continue;
            }
            float sway = Mth.sin(state.time * 0.04F + index * 1.9F) * 0.06F;
            float length = SPRITE_LENGTH * state.look.size();
            drawSprite(g, feature.u() + 0.5F, feature.v() + 1 - length / 2, true, Mth.HALF_PI + sway, Caste.WORKER, state.look, SPRITE_LENGTH, 0, 0,
                    g.light, 1, FLOOR - 0.5F);
        }
    }

    private static void drawActor(Geometry g, NestVisuals.Actor actor, State state) {
        float pt = state.partialTicks;
        float u = Mth.lerp(pt, actor.prevU, actor.u);
        float v = Mth.lerp(pt, actor.prevV, actor.v);
        float phase = Mth.lerp(pt, actor.prevPhase, actor.phase);
        int frame = actor.moving ? Math.floorMod((int) (phase * 0.9F), 4)
                : actor.action == NestVisuals.Action.GROOM || actor.action == NestVisuals.Action.TEND ? (int) (state.time / 4) % 2 : 0;
        float length = SPRITE_LENGTH * (actor.caste == Caste.SOLDIER ? 1.15F : 1);
        // Each ant at a depth of its own, so two that pass never share a plane.
        float depth = FLOOR - 0.35F - (actor.seed % 1) * 0.6F;
        drawSprite(g, u, v, actor.facingPositive, Mth.lerp(pt, actor.prevPitch, actor.pitch), actor.caste, actor.former ? state.former : state.look, length,
                frame, actor.carry, g.light, 1, depth);
    }

    /** One side-view ant on the face, its middle at (u, v), walking along +u or -u and tilted up by {@code pitch}. */
    private static void drawSprite(Geometry g, float u, float v, boolean positive, float pitch, Caste caste, AntLook look, float length, int frame,
                                   int carry, int light, float gasterScale, float depth) {
        float d = positive ? 1 : -1;
        float fu = d * Mth.cos(pitch);
        float fv = Mth.sin(pitch);
        float upU = -d * Mth.sin(pitch);
        float upV = Mth.cos(pitch);
        float len = length * look.size();
        float height = len * SIDE_CELL_H / SIDE_CELL_W;
        // From the feet: the sprite stands on its bottom edge.
        float baseU = u - upU * height / 2;
        float baseV = v - upV * height / 2;
        int row = caste.ordinal() * 3;
        int saved = g.light;
        g.light = light;
        if (look.crop().isPresent()) {
            // A Liquid Ant: its crop, always full, with the gaster's plates over it.
            g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height, frame, SIDE_CROP_ROW + caste.ordinal(), 0xFF000000 | look.crop().get(), depth);
            g.light = light;
            spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height, frame, SIDE_PLATES_ROW + caste.ordinal(), 0xFF000000 | Geometry.visibleOnDark(look.gaster()),
                    depth - 0.005F);
        } else {
            spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height * gasterScale, frame, row + 1, 0xFF000000 | Geometry.visibleOnDark(look.gaster()), depth);
        }
        spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height, frame, row, 0xFF000000 | Geometry.visibleOnDark(look.body()), depth - 0.01F);
        if (look.crystal().isPresent()) {
            g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height, frame, row + 2, 0xFF000000 | look.crystal().get(), depth - 0.015F);
            g.light = light;
        }
        if (look.costume().isPresent()) {
            spriteLayer(g, baseU, baseV, fu, fv, upU, upV, len, height, frame, SideSprites.costumeRow(look.costume().get(), caste), -1, depth - 0.018F);
        }
        if (carry != 0) {
            // The load between the mandibles, at the front.
            float cu = baseU + fu * len * 0.5F + upU * height * 0.35F;
            float cv = baseV + fv * len * 0.5F + upV * height * 0.35F;
            float s = len * 0.14F;
            float[][] uv = {{88 / 96F, 112F / SIDE_TEX_H}, {92 / 96F, 112F / SIDE_TEX_H}, {92 / 96F, 108F / SIDE_TEX_H}, {88 / 96F, 108F / SIDE_TEX_H}};
            float[][] corners = {at(cu - s, cv - s, depth - 0.02F), at(cu + s, cv - s, depth - 0.02F), at(cu + s, cv + s, depth - 0.02F),
                    at(cu - s, cv + s, depth - 0.02F)};
            g.quadUv(corners, uv, carry, FRONT);
        }
        g.light = saved;
    }

    private static void spriteLayer(Geometry g, float u, float v, float fu, float fv, float upU, float upV, float len, float height, int frame, int row,
                                    int color, float depth) {
        float half = len / 2;
        float[][] local = {{-half, 0}, {half, 0}, {half, height}, {-half, height}};
        float[][] corners = new float[4][];
        for (int i = 0; i < 4; i++) {
            corners[i] = at(u + fu * local[i][0] + upU * local[i][1], v + fv * local[i][0] + upV * local[i][1], depth);
        }
        float u0 = frame * SIDE_CELL_W / (float) SIDE_TEX_W;
        float u1 = (frame + 1) * SIDE_CELL_W / (float) SIDE_TEX_W;
        float v0 = row * SIDE_CELL_H / (float) SIDE_TEX_H;
        float v1 = (row + 1) * SIDE_CELL_H / (float) SIDE_TEX_H;
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        g.quadUv(corners, uv, color, FRONT);
    }

    private NestRenderer() {}
}
