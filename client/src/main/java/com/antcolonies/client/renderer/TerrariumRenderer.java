package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.List;


import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.model.AntDrawer;
import com.antcolonies.client.model.AntPose;
import com.antcolonies.client.terrarium.TerrariumVisuals;
import com.antcolonies.client.terrarium.TunnelLayout;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Climate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The living Terrarium: soil in its climate's colors behind the glass, with the colony's tunnels and chambers dug
 * into every side, eggs, larvae and food in the chambers, the Queen in her chamber on the front, the colony's
 * representative ants at work, and on the surface whatever the colony grows (fungus gardens, crystals at stage 4).
 * Everything follows the synced colony; nothing here feeds back into it.
 */
public class TerrariumRenderer implements BlockEntityRenderer<TerrariumBlockEntity, TerrariumRenderer.State> {
    private static final Identifier TEXTURE = AntColonies.id("textures/entity/terrarium_inside.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOW = RenderTypes.eyes(TEXTURE);
    private static final float TEX = 64;
    /** The soil block inside the glass, px. */
    private static final float MIN = 2;
    private static final float MAX = 14;
    private static final float BOTTOM = 1;
    private static final float TOP = TunnelLayout.SURFACE;
    private static final float WORKER_LENGTH = 1.3F / 16;
    /** Most surface ants per Terrarium drawn as 3D models; the rest are flat. */
    private static final int MAX_MODEL_ANTS = 12;
    /** The side-view sprite sheet: 24x12 cells, four frames across, per caste body, gaster and crystal rows. */
    private static final Identifier SIDE_TEXTURE = AntColonies.id("textures/entity/ant_side.png");
    private static final RenderType SIDE = RenderTypes.entityCutout(SIDE_TEXTURE);
    private static final int SIDE_CELL_W = 24;
    private static final int SIDE_CELL_H = 12;
    private static final int SIDE_TEX_W = 96;
    private static final int SIDE_TEX_H = 112;
    /** A tunnel worker sprite's length, px. */
    private static final float SPRITE_LENGTH = 2.1F;

    private final AntDrawer drawer;
    private final AntPose pose = new AntPose();

    public static class State extends BlockEntityRenderState {
        @Nullable TerrariumVisuals visuals;
        @Nullable Colony colony;
        float partialTicks;
        float time;
        boolean near;
        /** Close enough for the surface ants to be 3D models. */
        boolean detailed;
        @Nullable AntSpecies species;
        @Nullable AntSpecies former;
    }

    public TerrariumRenderer(BlockEntityRendererProvider.Context context) {
        this.drawer = new AntDrawer(context.entityModelSet());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TerrariumBlockEntity terrarium, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(terrarium, state, partialTicks, camera, breakProgress);
        state.visuals = terrarium.clientVisuals instanceof TerrariumVisuals visuals ? visuals : null;
        state.colony = terrarium.colony();
        state.partialTicks = partialTicks;
        state.time = terrarium.getLevel() == null ? 0 : terrarium.getLevel().getGameTime() + partialTicks;
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(terrarium.getBlockPos()).distanceToSqr(camera) < distance * distance;
        double detail = AntConfig.client(AntConfig.ANT_DETAIL_DISTANCE);
        state.detailed = AntConfig.client(AntConfig.ANT_3D_MODELS) && Vec3.atCenterOf(terrarium.getBlockPos()).distanceToSqr(camera) < detail * detail;
        state.species = ClientSpecies.get(terrarium.colony().species());
        state.former = ClientSpecies.get(terrarium.colony().formerSpecies());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TerrariumVisuals visuals = state.visuals;
        if (visuals == null || visuals.faces[0] == null || state.colony == null) {
            return;
        }
        Climate climate = state.species == null ? Climate.TEMPERATE : state.species.climate();
        int soil = soilColor(climate);
        int top = topColor(climate);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, SOLID, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, TEX, light);
            drawSoil(g, soil, top);
            for (int face = 0; face < 4; face++) {
                drawTunnels(g, face, visuals.faces[face], Geometry.shade(soil, 0.42F), soil);
            }
            drawItems(g, visuals, state);
            drawSurface(g, visuals, state);
        });
        AntLook look = state.species == null ? AntLook.UNKNOWN : state.species.look();
        if (look.glow() && visuals.stage >= 4 && look.crystal().isPresent()) {
            int crystal = 0xFF000000 | look.crystal().get();
            collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> drawCrystals(new Geometry(pose, buffer, TEX, LightCoordsUtil.FULL_BRIGHT),
                    visuals, Geometry.shade(crystal, 0.6F)));
        }
        if (!state.near) {
            return;
        }
        AntLook former = state.former == null ? look : state.former.look();
        // The ants in the tunnels and the Queen are flat side-view sprites, seen through the glass like a real ant farm.
        collector.submitCustomGeometry(poseStack, SIDE, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, TEX, light);
            drawQueenSprite(g, visuals, state, look);
            int surface = 0;
            for (TerrariumVisuals.Actor actor : visuals.actors) {
                if (actor.face != TerrariumVisuals.SURFACE) {
                    drawActorSprite(g, actor, state, actor.former ? former : look);
                } else if (!state.detailed || surface++ >= MAX_MODEL_ANTS) {
                    drawSurfaceSprite(g, actor, state, actor.former ? former : look);
                }
            }
        });
        // On the open surface, up close, the first few walk as models (each model costs far more than a flat ant).
        int models = 0;
        for (TerrariumVisuals.Actor actor : visuals.actors) {
            if (actor.face == TerrariumVisuals.SURFACE && state.detailed && models++ < MAX_MODEL_ANTS) {
                drawActor(poseStack, collector, actor, state, actor.former ? former : look);
            }
        }
    }

    // --- Colors by climate ---

    private static int soilColor(Climate climate) {
        return switch (climate) {
            case COLD -> 0xFF7A6857;
            case HOT -> 0xFFD6BA7A;
            case UNDERGROUND -> 0xFF75716B;
            case NETHER -> 0xFF6E2E2A;
            default -> 0xFF8A6240;
        };
    }

    private static int topColor(Climate climate) {
        return switch (climate) {
            case COLD -> 0xFFF2F6FA;
            case HOT -> 0xFFE3CD8E;
            case UNDERGROUND -> 0xFF8A857E;
            case NETHER -> 0xFF8C2E26;
            default -> 0xFF6DA544;
        };
    }

    // --- Soil and tunnels ---

    private static void drawSoil(Geometry g, int soil, int top) {
        float e = 0;
        // Sides.
        g.quad(new float[] {MAX, BOTTOM, MIN - e}, new float[] {MIN, BOTTOM, MIN - e}, new float[] {MIN, TOP, MIN - e}, new float[] {MAX, TOP, MIN - e},
                Geometry.shade(soil, 0.85F), 0, 0, 12, 10, 0, 0, -1);
        g.quad(new float[] {MIN, BOTTOM, MAX + e}, new float[] {MAX, BOTTOM, MAX + e}, new float[] {MAX, TOP, MAX + e}, new float[] {MIN, TOP, MAX + e},
                Geometry.shade(soil, 0.85F), 2, 2, 14, 12, 0, 0, 1);
        g.quad(new float[] {MIN - e, BOTTOM, MIN}, new float[] {MIN - e, BOTTOM, MAX}, new float[] {MIN - e, TOP, MAX}, new float[] {MIN - e, TOP, MIN},
                Geometry.shade(soil, 0.7F), 1, 3, 13, 13, -1, 0, 0);
        g.quad(new float[] {MAX + e, BOTTOM, MAX}, new float[] {MAX + e, BOTTOM, MIN}, new float[] {MAX + e, TOP, MIN}, new float[] {MAX + e, TOP, MAX},
                Geometry.shade(soil, 0.7F), 3, 1, 15, 11, 1, 0, 0);
        // The surface.
        g.quad(new float[] {MIN, TOP, MIN}, new float[] {MIN, TOP, MAX}, new float[] {MAX, TOP, MAX}, new float[] {MAX, TOP, MIN}, top, 34, 2, 46, 14, 0, 1, 0);
    }

    /** Where a point of a side's 2D layout is in the block, px; {@code out} is how far in front of the soil. */
    static float[] onFace(int face, float u, float v, float out, float[] into) {
        float w = MIN + (u - 1) * (MAX - MIN) / 14;
        switch (face) {
            case 0 -> {
                into[0] = 16 - w;
                into[2] = MIN - out;
            }
            case 1 -> {
                into[0] = MAX + out;
                into[2] = 16 - w;
            }
            case 2 -> {
                into[0] = w;
                into[2] = MAX + out;
            }
            default -> {
                into[0] = MIN - out;
                into[2] = w;
            }
        }
        into[1] = v;
        return into;
    }

    private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    /** The direction of +u on each side, in the block. */
    private static final float[][] U_DIR = {{-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};

    /** One tunnel or chamber quad on a side, its corners already wound to face out of the soil. Kind: 0 tunnel, 1 chamber, 2 crystal chamber. */
    private record TunnelQuad(float[] a, float[] b, float[] c, float[] d, int kind) {}

    /**
     * The tunnels and chambers of one side. Their shape only changes when the colony's layout does (a new stage), so the
     * quads are worked out once per layout and kept on it; each frame only sends them on.
     */
    private static void drawTunnels(Geometry g, int face, TunnelLayout layout, int dark, int soil) {
        @SuppressWarnings("unchecked")
        List<TunnelQuad> quads = layout.renderCache instanceof List<?> cached ? (List<TunnelQuad>) cached : null;
        if (quads == null) {
            quads = bakeTunnels(face, layout);
            layout.renderCache = quads;
        }
        float[] n = NORMALS[face];
        int crystal = Geometry.shade(dark, 1.3F);
        for (TunnelQuad q : quads) {
            if (q.kind() == 0) {
                g.quad(q.a(), q.b(), q.c(), q.d(), dark, 18, 2, 30, 4, n[0], n[1], n[2]);
            } else {
                g.quad(q.a(), q.b(), q.c(), q.d(), q.kind() == 2 ? crystal : dark, 18, 6, 30, 14, n[0], n[1], n[2]);
            }
        }
    }

    private static List<TunnelQuad> bakeTunnels(int face, TunnelLayout layout) {
        List<TunnelQuad> quads = new ArrayList<>();
        float[] n = NORMALS[face];
        float out = 0.03F;
        float[] p = new float[2];
        int segments = 7;
        for (TunnelLayout.Edge edge : layout.edges) {
            float halfWidth = 0.7F;
            float[] prev = null;
            float prevNu = 0;
            float prevNv = 0;
            for (int i = 0; i <= segments; i++) {
                float[] point = layout.point(edge, edge.a(), i / (float) segments, p).clone();
                if (prev != null) {
                    float du = point[0] - prev[0];
                    float dv = point[1] - prev[1];
                    float len = Math.max(1e-4F, Mth.sqrt(du * du + dv * dv));
                    float nu = -dv / len * halfWidth;
                    float nv = du / len * halfWidth;
                    if (i == 1) {
                        prevNu = nu;
                        prevNv = nv;
                    }
                    quads.add(facing(n, onFace(face, prev[0] - prevNu, prev[1] - prevNv, out, new float[3]),
                            onFace(face, point[0] - nu, point[1] - nv, out, new float[3]), onFace(face, point[0] + nu, point[1] + nv, out, new float[3]),
                            onFace(face, prev[0] + prevNu, prev[1] + prevNv, out, new float[3]), 0));
                    prevNu = nu;
                    prevNv = nv;
                }
                prev = point;
            }
        }
        // Chambers: rounded hollows, flat at the bottom.
        int sides = 12;
        for (TunnelLayout.Node node : layout.nodes) {
            float ru = node.radiusU();
            float rv = node.radiusV();
            int kind = node.type() == TunnelLayout.ChamberType.CRYSTAL ? 2 : 1;
            for (int i = 0; i < sides; i++) {
                float a0 = i * Mth.TWO_PI / sides;
                float a1 = (i + 1) * Mth.TWO_PI / sides;
                float v0 = Math.max(-0.65F, Mth.sin(a0));
                float v1 = Math.max(-0.65F, Mth.sin(a1));
                float[] c = onFace(face, node.u() + Mth.cos(a1) * ru, node.v() + v1 * rv, out + 0.005F, new float[3]);
                quads.add(facing(n, onFace(face, node.u(), node.v(), out + 0.005F, new float[3]),
                        onFace(face, node.u() + Mth.cos(a0) * ru, node.v() + v0 * rv, out + 0.005F, new float[3]), c, c.clone(), kind));
            }
        }
        return quads;
    }

    /** The quad with its corners in the order that faces along {@code n}. */
    private static TunnelQuad facing(float[] n, float[] a, float[] b, float[] c, float[] d, int kind) {
        float[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float[] ac = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        float cross = (ab[1] * ac[2] - ab[2] * ac[1]) * n[0] + (ab[2] * ac[0] - ab[0] * ac[2]) * n[1] + (ab[0] * ac[1] - ab[1] * ac[0]) * n[2];
        return cross >= 0 ? new TunnelQuad(a, b, c, d, kind) : new TunnelQuad(d, c, b, a, kind);
    }

    // --- Brood, food, surface ---

    private static void drawItems(Geometry g, TerrariumVisuals visuals, State state) {
        float[] at = new float[3];
        for (TerrariumVisuals.Item item : visuals.items) {
            onFace(item.face(), item.u(), item.v(), 0.3F, at);
            float s = item.size() / 2;
            int color = switch (item.kind()) {
                case TerrariumVisuals.EGG -> 0xFFF6F2E6;
                case TerrariumVisuals.LARVA -> 0xFFEDE0C0;
                default -> visuals.carryColor;
            };
            if (item.kind() == TerrariumVisuals.LARVA) {
                // Larvae lie along the chamber floor.
                boolean alongX = item.face() % 2 == 0;
                g.box(at[0] - (alongX ? s * 1.4F : s * 0.6F), at[1] - s * 0.6F, at[2] - (alongX ? s * 0.6F : s * 1.4F),
                        at[0] + (alongX ? s * 1.4F : s * 0.6F), at[1] + s * 0.5F, at[2] + (alongX ? s * 0.6F : s * 1.4F), color, 49, 1, 55, 7);
            } else {
                g.box(at[0] - s * 0.8F, at[1] - s, at[2] - s * 0.8F, at[0] + s * 0.8F, at[1] + s, at[2] + s * 0.8F, color, 49, 1, 55, 7);
            }
        }
    }

    private static void drawSurface(Geometry g, TerrariumVisuals visuals, State state) {
        AntSpecies species = state.species;
        long seed = visuals.hashCode();
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(seed);
        // Entrance mounds.
        for (int i = 0; i < 2 + Math.min(2, visuals.stage); i++) {
            float x = 4 + random.nextFloat() * 8;
            float z = 4 + random.nextFloat() * 8;
            g.box(x - 0.9F, TOP, z - 0.9F, x + 0.9F, TOP + 0.35F, z + 0.9F, Geometry.shade(soilColor(species == null ? Climate.TEMPERATE : species.climate()), 0.7F),
                    17, 1, 23, 7);
            g.box(x - 0.35F, TOP + 0.01F, z - 0.35F, x + 0.35F, TOP + 0.37F, z + 0.35F, 0xFF1E140C, 17, 1, 19, 3);
        }
        if (species != null && species.canFarm() && visuals.stage >= 2) {
            // The fungus garden of Farmer Ants.
            for (int i = 0; i < 2 + visuals.stage; i++) {
                float x = 3 + random.nextFloat() * 10;
                float z = 3 + random.nextFloat() * 10;
                float h = 0.8F + random.nextFloat() * 0.8F;
                g.box(x - 0.2F, TOP, z - 0.2F, x + 0.2F, TOP + h, z + 0.2F, 0xFFE8DDC8, 49, 1, 55, 7);
                g.box(x - 0.7F, TOP + h, z - 0.7F, x + 0.7F, TOP + h + 0.5F, z + 0.7F, i % 2 == 0 ? 0xFFB5462E : 0xFFC9A57A, 57, 1, 63, 7);
            }
        }
        if (visuals.stage >= 4 && species != null && species.look().crystal().isPresent()) {
            drawCrystals(g, visuals, 0xFF000000 | species.look().crystal().get());
        }
    }

    private static void drawCrystals(Geometry g, TerrariumVisuals visuals, int color) {
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(visuals.hashCode() * 7L);
        for (int i = 0; i < 5; i++) {
            float x = 3 + random.nextFloat() * 10;
            float z = 3 + random.nextFloat() * 10;
            for (int k = 0; k < 3; k++) {
                float ox = (random.nextFloat() - 0.5F) * 1.2F;
                float oz = (random.nextFloat() - 0.5F) * 1.2F;
                float h = 1 + random.nextFloat() * 1.8F;
                float w = 0.25F + random.nextFloat() * 0.2F;
                g.box(x + ox - w, TOP, z + oz - w, x + ox + w, TOP + h, z + oz + w, color, 49, 9, 55, 15);
            }
        }
    }

    // --- Ants ---

    private static void drawQueenSprite(Geometry g, TerrariumVisuals visuals, State state, AntLook look) {
        if (state.colony.queen() == null) {
            return;
        }
        TunnelLayout layout = visuals.faces[visuals.frontFace];
        int chamber = layout.find(TunnelLayout.ChamberType.QUEEN);
        if (chamber < 0) {
            return;
        }
        TunnelLayout.Node node = layout.nodes.get(chamber);
        float shuffle = Mth.sin(visuals.queenPhase * 0.02F) * node.radiusU() * 0.25F;
        boolean positive = Mth.cos(visuals.queenPhase * 0.02F) > 0;
        // Her gaster swells a little while she lays; she shifts her legs now and then.
        float pulse = 1 + (visuals.queenLaying ? 0.06F : 0.02F) * Mth.sin(state.time * 0.25F);
        int frame = (int) (state.time / 12) % 2 == 0 ? 0 : 2;
        int light = visuals.evolutionGlow > 0.05F ? LightCoordsUtil.FULL_BRIGHT : g.light;
        drawSprite(g, visuals.frontFace, node.u() + shuffle, node.v() - node.radiusV() + 0.15F, positive, 0, Caste.QUEEN, look,
                SPRITE_LENGTH * 1.75F, frame, 0, light, pulse);
    }

    private static void drawActorSprite(Geometry g, TerrariumVisuals.Actor actor, State state, AntLook look) {
        float pt = state.partialTicks;
        float u = Mth.lerp(pt, actor.prevU, actor.u);
        float v = Mth.lerp(pt, actor.prevV, actor.v);
        float phase = Mth.lerp(pt, actor.prevPhase, actor.phase);
        int frame = actor.moving ? Math.floorMod((int) (phase * 0.9F), 4)
                : actor.action == TerrariumVisuals.Action.GROOM ? (int) (state.time / 4) % 2 : 0;
        float length = SPRITE_LENGTH * (actor.caste == Caste.SOLDIER ? 1.15F : 1);
        // The tunnel's centre line is a little above its floor.
        drawSprite(g, actor.face, u, v - 0.55F, actor.facingPositive, Mth.lerp(pt, actor.prevPitch, actor.pitch), actor.caste, look, length, frame,
                actor.carry, g.light, 1);
    }

    /**
     * One side-view ant in a glass side's plane: feet at (u, v), walking along +u or -u, tilted up a slope by
     * {@code pitch}. Gaster, body and crystals are layers of the sprite sheet, each in the species' color.
     */
    private static void drawSprite(Geometry g, int face, float u, float v, boolean positive, float pitch, Caste caste, AntLook look, float length,
                                   int frame, int carry, int light, float gasterScale) {
        float d = positive ? 1 : -1;
        float fu = d * Mth.cos(pitch);
        float fv = Mth.sin(pitch);
        float upU = -d * Mth.sin(pitch);
        float upV = Mth.cos(pitch);
        float len = length * look.size();
        float height = len * SIDE_CELL_H / SIDE_CELL_W;
        int row = caste.ordinal() * 3;
        int saved = g.light;
        g.light = light;
        spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height * gasterScale, frame, row + 1, 0xFF000000 | visible(look.gaster()), 0.08F);
        spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height, frame, row, 0xFF000000 | visible(look.body()), 0.09F);
        if (look.crystal().isPresent()) {
            g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height, frame, row + 2, 0xFF000000 | look.crystal().get(), 0.1F);
            g.light = light;
        }
        if (carry != 0) {
            // The load between the mandibles, at the front.
            float cu = u + fu * len * 0.5F + upU * height * 0.35F;
            float cv = v + fv * len * 0.5F + upV * height * 0.35F;
            float s = len * 0.14F;
            float[][] uv = {{88 / 96F, 1}, {92 / 96F, 1}, {92 / 96F, 108 / 112F}, {88 / 96F, 108 / 112F}};
            float[][] local = {{-s, -s}, {s, -s}, {s, s}, {-s, s}};
            float[][] corners = new float[4][];
            for (int i = 0; i < 4; i++) {
                corners[i] = onFace(face, cu + local[i][0], cv + local[i][1], 0.11F, new float[3]);
            }
            g.quadUv(corners, uv, carry, NORMALS[face]);
        }
        g.light = saved;
    }

    /**
     * A species color lifted toward white until it reads against the dark soil of the tunnels (a navy or black ant would
     * otherwise vanish there); light colors are left alone.
     */
    private static int visible(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float luma = 0.299F * r + 0.587F * gr + 0.114F * b;
        float min = 105;
        if (luma >= min) {
            return rgb & 0xFFFFFF;
        }
        // Scale first (keeps the hue: navy becomes a clear blue, not grey), then blend toward white for what is left.
        float scale = Math.min(2.6F, min / Math.max(luma, 1));
        r = Math.min(255, (int) (r * scale));
        gr = Math.min(255, (int) (gr * scale));
        b = Math.min(255, (int) (b * scale));
        luma = 0.299F * r + 0.587F * gr + 0.114F * b;
        float t = Math.max(0, (min - luma) / (255 - luma));
        r = (int) (r + (255 - r) * t);
        gr = (int) (gr + (255 - gr) * t);
        b = (int) (b + (255 - b) * t);
        return r << 16 | gr << 8 | b;
    }

    private static void spriteLayer(Geometry g, int face, float u, float v, float fu, float fv, float upU, float upV, float len, float height,
                                    int frame, int row, int color, float out) {
        float half = len / 2;
        float[][] local = {{-half, 0}, {half, 0}, {half, height}, {-half, height}};
        float[][] corners = new float[4][];
        for (int i = 0; i < 4; i++) {
            corners[i] = onFace(face, u + fu * local[i][0] + upU * local[i][1], v + fv * local[i][0] + upV * local[i][1], out, new float[3]);
        }
        float u0 = frame * SIDE_CELL_W / (float) SIDE_TEX_W;
        float u1 = (frame + 1) * SIDE_CELL_W / (float) SIDE_TEX_W;
        float v0 = row * SIDE_CELL_H / (float) SIDE_TEX_H;
        float v1 = (row + 1) * SIDE_CELL_H / (float) SIDE_TEX_H;
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        g.quadUv(corners, uv, color, NORMALS[face]);
    }

    /**
     * A surface ant as a 2D side-view sprite, the same as the ants in the tunnels: it stands on the soil in the vertical
     * plane of its walk, head first, drawn from both sides so it shows through every glass wall. Used beyond the detail
     * distance, past the 3D model cap, and always when 3D ants are turned off; far cheaper than the 3D model.
     */
    private static void drawSurfaceSprite(Geometry g, TerrariumVisuals.Actor actor, State state, AntLook look) {
        float pt = state.partialTicks;
        float x = Mth.lerp(pt, actor.prevX, actor.x);
        float z = Mth.lerp(pt, actor.prevZ, actor.z);
        float yaw = Mth.rotLerpRad(pt, actor.prevYaw, actor.yaw);
        float phase = Mth.lerp(pt, actor.prevPhase, actor.phase);
        int frame = actor.moving ? Math.floorMod((int) (phase * 0.9F), 4)
                : actor.action == TerrariumVisuals.Action.GROOM ? (int) (state.time / 4) % 2 : 0;
        float len = SPRITE_LENGTH * (actor.caste == Caste.SOLDIER ? 1.15F : 1) * look.size();
        float height = len * SIDE_CELL_H / SIDE_CELL_W;
        // Forward (the way the 3D model faces: +z turned by yaw) and the horizontal normal of the sprite's plane.
        float fx = Mth.sin(yaw);
        float fz = Mth.cos(yaw);
        float nx = fz;
        float nz = -fx;
        int row = actor.caste.ordinal() * 3;
        int light = g.light;
        for (int side = -1; side <= 1; side += 2) {
            float[] n = {nx * side, 0, nz * side};
            surfaceLayer(g, x, z, fx, fz, n, len, height, frame, row + 1, 0xFF000000 | visible(look.gaster()), 0.01F);
            surfaceLayer(g, x, z, fx, fz, n, len, height, frame, row, 0xFF000000 | visible(look.body()), 0.02F);
            if (look.crystal().isPresent()) {
                g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
                surfaceLayer(g, x, z, fx, fz, n, len, height, frame, row + 2, 0xFF000000 | look.crystal().get(), 0.03F);
                g.light = light;
            }
        }
    }

    /** One layer of a standing surface sprite, nudged {@code out} along its normal so the layers stack in order. */
    private static void surfaceLayer(Geometry g, float x, float z, float fx, float fz, float[] n, float len, float height, int frame, int row,
                                     int color, float out) {
        float half = len / 2;
        float ox = x + n[0] * out;
        float oz = z + n[2] * out;
        float[][] corners = {{ox - fx * half, TOP, oz - fz * half}, {ox + fx * half, TOP, oz + fz * half},
                {ox + fx * half, TOP + height, oz + fz * half}, {ox - fx * half, TOP + height, oz - fz * half}};
        float u0 = frame * SIDE_CELL_W / (float) SIDE_TEX_W;
        float u1 = (frame + 1) * SIDE_CELL_W / (float) SIDE_TEX_W;
        float v0 = row * SIDE_CELL_H / (float) SIDE_TEX_H;
        float v1 = (row + 1) * SIDE_CELL_H / (float) SIDE_TEX_H;
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        g.quadUv(corners, uv, color, n);
    }

    private void drawActor(PoseStack poseStack, SubmitNodeCollector collector, TerrariumVisuals.Actor actor, State state, AntLook look) {
        float pt = state.partialTicks;
        pose.reset();
        pose.time = state.time;
        pose.seed = actor.seed;
        pose.walkPhase = Mth.lerp(pt, actor.prevPhase, actor.phase);
        pose.walkAmount = actor.moving ? 1 : 0;
        switch (actor.action) {
            case GROOM -> pose.groom = 0.8F + Mth.sin(state.time * 0.4F + actor.seed) * 0.2F;
            case FEED -> {
                pose.headPitch = 0.35F;
                pose.mandibleOpen = 0.4F;
            }
            case TEND -> pose.headPitch = 0.45F + Mth.sin(state.time * 0.3F) * 0.15F;
            default -> {
            }
        }
        if (actor.caste == Caste.SOLDIER) {
            pose.alert = 0.6F;
        }
        float length = WORKER_LENGTH * AntDrawer.casteScale(actor.caste);
        if (actor.face == TerrariumVisuals.SURFACE) {
            float x = Mth.lerp(pt, actor.prevX, actor.x);
            float z = Mth.lerp(pt, actor.prevZ, actor.z);
            float yaw = Mth.rotLerpRad(pt, actor.prevYaw, actor.yaw);
            poseStack.pushPose();
            poseStack.translate(x / 16, TOP / 16, z / 16);
            poseStack.rotate(Axis.YP.rotation(yaw));
            drawer.draw(collector, poseStack, actor.caste, look, pose, length, state.lightCoords, OverlayTexture.NO_OVERLAY, actor.carry, 0);
            poseStack.popPose();
            return;
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
