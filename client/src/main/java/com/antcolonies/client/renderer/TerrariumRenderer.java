package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.List;


import com.antcolonies.AntColonies;
import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.block.ColonyPartBlock;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.nest.NestVisuals;
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
 *
 * <p>That is the Formicarium standing on the ground. With another part of a colony over or under it, it is an upright
 * nest instead, drawn by {@link NestRenderer}.
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
    static final RenderType SIDE = RenderTypes.entityCutout(SIDE_TEXTURE);
    private static final int SIDE_CELL_W = 24;
    private static final int SIDE_CELL_H = 12;
    private static final int SIDE_TEX_W = 96;
    private static final int SIDE_TEX_H = 192;
    /** Liquid Ants in the sprite sheet: per caste a row for the full crop (from this row) and one for the plates on it. */
    private static final int SIDE_CROP_ROW = 10;
    private static final int SIDE_PLATES_ROW = 13;
    /** A tunnel worker sprite's length, px. */
    static final float SPRITE_LENGTH = 2.1F;

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
        /** Which glass sides (north, east, south, west) face the camera; the others are behind the soil. */
        final boolean[] facing = new boolean[4];
        /** Its place in the build: the sides joined to chambers have no glass and no wall, the soil runs on there. */
        final FlatGround.Shape shape = new FlatGround.Shape();
        /** In a stack: drawn as an upright nest, from {@link #nest}. */
        boolean upright;
        final NestRenderer.State nest = new NestRenderer.State();
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
        state.upright = ColonyPartBlock.upright(terrarium.getBlockState());
        if (state.upright) {
            NestRenderer.State nest = state.nest;
            NestRenderer.extract(nest, terrarium.getBlockPos(), terrarium.getLevel(), terrarium.getBlockState().getValue(ColonyEntityBlock.FACING), camera,
                    partialTicks, AntConfig.client(AntConfig.VISUAL_DISTANCE));
            nest.visuals = terrarium.clientVisuals instanceof NestVisuals visuals ? visuals : null;
            nest.layout = nest.visuals == null ? null : nest.visuals.layout;
            AntSpecies species = ClientSpecies.get(terrarium.colony().species());
            AntSpecies former = ClientSpecies.get(terrarium.colony().formerSpecies());
            nest.climate = species == null ? Climate.TEMPERATE : species.climate();
            nest.look = species == null ? AntLook.UNKNOWN : species.look();
            nest.former = former == null ? nest.look : former.look();
            nest.queen = terrarium.colony().queen() != null;
            nest.stage = terrarium.colony().stage();
            nest.linked = true;
            nest.lit = false;
            nest.shown = 0;
            nest.items.clear();
            return;
        }
        state.visuals = terrarium.clientVisuals instanceof TerrariumVisuals visuals ? visuals : null;
        state.colony = terrarium.colony();
        state.partialTicks = partialTicks;
        state.time = terrarium.getLevel() == null ? 0 : com.antcolonies.client.ClientHooks.animationTime(terrarium.getLevel().getGameTime(), partialTicks);
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(terrarium.getBlockPos()).distanceToSqr(camera) < distance * distance;
        double detail = AntConfig.client(AntConfig.ANT_DETAIL_DISTANCE);
        state.detailed = AntConfig.client(AntConfig.ANT_3D_MODELS) && Vec3.atCenterOf(terrarium.getBlockPos()).distanceToSqr(camera) < detail * detail;
        state.species = ClientSpecies.get(terrarium.colony().species());
        state.former = ClientSpecies.get(terrarium.colony().formerSpecies());
        // Only the sides facing the camera can be seen (two at most): the others are left out, a good share of the work
        // when many Terrariums are in view.
        double cx = (camera.x - terrarium.getBlockPos().getX()) * 16;
        double cz = (camera.z - terrarium.getBlockPos().getZ()) * 16;
        FlatGround.facing(state.facing, cx, cz);
        if (terrarium.getLevel() != null) {
            FlatGround.extract(state.shape, terrarium.getLevel(), terrarium.getBlockPos(), terrarium.getBlockState());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.upright) {
            NestRenderer.submit(state.nest, poseStack, collector);
            return;
        }
        TerrariumVisuals visuals = state.visuals;
        if (visuals == null || visuals.faces[0] == null || state.colony == null) {
            return;
        }
        Climate climate = state.species == null ? Climate.TEMPERATE : state.species.climate();
        int soil = soilColor(climate);
        int top = topColor(climate);
        int light = state.lightCoords;
        FlatGround.Shape shape = state.shape;
        // Where the Formicarium's tunnels leave it: on into the next block's wall, or round a corner.
        @SuppressWarnings("unchecked")
        List<float[][]>[] exits = new List[4];
        for (int face = 0; face < 4; face++) {
            exits[face] = shape.outer(face) && state.facing[face] ? FlatGround.exits(shape, face, visuals.faces[face]) : List.of();
        }
        collector.submitCustomGeometry(poseStack, SOLID, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, TEX, light);
            FlatGround.soil(g, shape, soil, top, state.facing);
            int dark = Geometry.shade(soil, 0.42F);
            for (int face = 0; face < 4; face++) {
                // A side joined to a chamber has no glass: the soil runs on, nothing of the tunnels shows there.
                if (!state.facing[face] || !shape.outer(face)) {
                    continue;
                }
                // The colony's own tunnels and chambers on this side, and its ways out, cut on the pixel grid together.
                TunnelLayout layout = visuals.faces[face];
                List<float[][]> paths = new ArrayList<>(exits[face]);
                float[] p = new float[2];
                for (TunnelLayout.Edge edge : layout.edges) {
                    float[][] points = new float[9][];
                    for (int i = 0; i <= 8; i++) {
                        points[i] = layout.point(edge, edge.a(), i / 8F, p).clone();
                    }
                    paths.add(points);
                }
                List<float[]> hollows = new ArrayList<>();
                for (TunnelLayout.Node node : layout.nodes) {
                    hollows.add(new float[] {node.u(), node.v(), node.radiusU(), node.radiusV(), node.type() == TunnelLayout.ChamberType.CRYSTAL ? 1 : 0});
                }
                FlatGround.tunnels(g, face, shape, paths, hollows, dark);
            }
            drawItems(g, visuals, state);
            if (shape.anyJoined()) {
                FlatGround.trails(g, shape, FlatGround.trailColor(soil, top));
                // The main entrance, where the trails from the chambers meet.
                float[] c = FlatGround.trailCentre(shape);
                g.box(c[0] - 1.3F, TOP, c[1] - 1.3F, c[0] + 1.3F, TOP + 0.45F, c[1] + 1.3F, Geometry.shade(soil, 0.7F), 17, 1, 23, 7);
                g.box(c[0] - 0.55F, TOP + 0.01F, c[1] - 0.55F, c[0] + 0.55F, TOP + 0.47F, c[1] + 0.55F, 0xFF1E140C, 17, 1, 19, 3);
            }
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
            if (shape.outer(visuals.frontFace)) {
                drawQueenSprite(g, visuals, state, look);
            }
            int surface = 0;
            for (TerrariumVisuals.Actor actor : visuals.actors) {
                if (actor.face != TerrariumVisuals.SURFACE) {
                    if (state.facing[actor.face] && shape.outer(actor.face)) {
                        drawActorSprite(g, actor, state, actor.former ? former : look);
                    }
                } else if (!state.detailed || surface++ >= MAX_MODEL_ANTS) {
                    drawSurfaceSprite(g, actor, state, actor.former ? former : look);
                }
            }
            if (state.colony.adults() > 0) {
                for (int face = 0; face < 4; face++) {
                    FlatGround.walkers(g, face, exits[face], look, state.time, shape.seed, visuals.carryColor);
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

    // --- Colors by climate (shared with the chambers: FlatGround) ---

    private static int soilColor(Climate climate) {
        return FlatGround.soilColor(climate);
    }

    private static int topColor(Climate climate) {
        return FlatGround.topColor(climate);
    }

    // --- Tunnels ---

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

    // --- Brood, food, surface ---

    private static void drawItems(Geometry g, TerrariumVisuals visuals, State state) {
        float[] at = new float[3];
        for (TerrariumVisuals.Item item : visuals.items) {
            if (item.face() >= 0 && item.face() < 4 && (!state.facing[item.face()] || !state.shape.outer(item.face()))) {
                continue;
            }
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
        long seed = visuals.seed();
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
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(visuals.seed() * 7L);
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
        if (!state.facing[visuals.frontFace]) {
            return;
        }
        TunnelLayout layout = visuals.faces[visuals.frontFace];
        int chamber = layout.find(TunnelLayout.ChamberType.QUEEN);
        if (chamber < 0) {
            return;
        }
        TunnelLayout.Node node = layout.nodes.get(chamber);
        // She walks her chamber's floor (TerrariumVisuals): her legs step with the distance she covers, and stand still
        // while she rests.
        float shuffle = Mth.lerp(state.partialTicks, visuals.prevQueenOffset, visuals.queenOffset);
        boolean positive = visuals.queenRight;
        boolean walking = visuals.queenMoving;
        // Resting, her gaster swells a little (more while she lays) and she shifts a leg now and then.
        float walked = Mth.lerp(state.partialTicks, visuals.prevQueenWalked, visuals.queenWalked);
        float pulse = walking ? 1 : 1 + (visuals.queenLaying ? 0.06F : 0.02F) * Mth.sin(state.time * 0.25F);
        int frame = walking ? Math.floorMod((int) (walked * 2.4F), 4) : (int) (state.time / 40) % 6 == 0 ? 2 : 0;
        int light = visuals.evolutionGlow > 0.05F ? LightCoordsUtil.FULL_BRIGHT : g.light;
        drawSprite(g, visuals.frontFace, node.u() + shuffle, FlatGround.chamberFloor(node.v(), node.radiusV()) + 0.02F, positive, 0, Caste.QUEEN, look,
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
        drawSprite(g, actor.face, u, FlatGround.tunnelFloor(v), actor.facingPositive, 0, actor.caste, look, length, frame,
                actor.carry, g.light, 1);
    }

    /**
     * One side-view ant in a glass side's plane: feet at (u, v), walking along +u or -u, tilted up a slope by
     * {@code pitch}. Gaster, body and crystals are layers of the sprite sheet, each in the species' color.
     */
    static void drawSprite(Geometry g, int face, float u, float v, boolean positive, float pitch, Caste caste, AntLook look, float length,
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
        if (look.crop().isPresent()) {
            // A Liquid Ant: its crop, always full and always the same size, with the gaster's plates over it.
            g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height, frame, SIDE_CROP_ROW + caste.ordinal(), 0xFF000000 | look.crop().get(), 0.075F);
            g.light = light;
            spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height, frame, SIDE_PLATES_ROW + caste.ordinal(), 0xFF000000 | visible(look.gaster()), 0.08F);
        } else {
            spriteLayer(g, face, u, v, fu, fv, upU, upV, len, height * gasterScale, frame, row + 1, 0xFF000000 | visible(look.gaster()), 0.08F);
        }
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
            float[][] uv = {{88 / 96F, 112F / SIDE_TEX_H}, {92 / 96F, 112F / SIDE_TEX_H}, {92 / 96F, 108F / SIDE_TEX_H}, {88 / 96F, 108F / SIDE_TEX_H}};
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
            if (look.crop().isPresent()) {
                g.light = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
                surfaceLayer(g, x, z, fx, fz, n, len, height, frame, SIDE_CROP_ROW + actor.caste.ordinal(), 0xFF000000 | look.crop().get(), 0.005F);
                g.light = light;
                surfaceLayer(g, x, z, fx, fz, n, len, height, frame, SIDE_PLATES_ROW + actor.caste.ordinal(), 0xFF000000 | visible(look.gaster()), 0.01F);
            } else {
                surfaceLayer(g, x, z, fx, fz, n, len, height, frame, row + 1, 0xFF000000 | visible(look.gaster()), 0.01F);
            }
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
            poseStack.mulPose(Axis.YP.rotation(yaw));
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
