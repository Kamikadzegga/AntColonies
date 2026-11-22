package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.client.ClientHooks;
import com.antcolonies.client.nest.NestVisuals;
import com.antcolonies.client.terrarium.SiteVisuals;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.block.ColonyPartBlock;
import com.antcolonies.content.block.ExpeditionSiteBlock;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.model.AntDrawer;
import com.antcolonies.client.model.AntPose;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws a colony chamber: its soil or upright nest, the ants at work on it, and the Storage's heap of the items it holds. */
public class StructureRenderer implements BlockEntityRenderer<StructureBlockEntity, StructureRenderer.State> {
    private static final float LENGTH = 3.2F / 16;
    /** Height of every chamber's floor inside its glass, in pixels: the colony's one ground height (DataGen.FLOOR). */
    public static final float FLOOR = FlatGround.GROUND;
    private final AntDrawer drawer;
    private final AntPose pose = new AntPose();
    private final ItemModelResolver items;

    public static class State extends BlockEntityRenderState {
        StructureKind kind = StructureKind.STORAGE;
        int ants;
        int soldiers;
        AntLook look = AntLook.UNKNOWN;
        boolean farmers;
        float time;
        long seed;
        boolean near;
        int carry;
        /** Degrees the site's model is turned to its facing: its ants' spots turn with it. */
        float turn;
        /** The site's working ants (ClientHooks ticks them), and how far this frame is into the tick. */
        @Nullable SiteVisuals site;
        float partialTicks;
        final List<ItemStackRenderState> heap = new ArrayList<>();
        final List<Integer> heapCounts = new ArrayList<>();
        /** In a stack: drawn as an upright nest, from {@link #nest}. */
        boolean upright;
        final NestRenderer.State nest = new NestRenderer.State();
        /** On the ground: the colony's soil under the chamber, run on into the blocks it joins (FlatGround). */
        final FlatGround.Shape shape = new FlatGround.Shape();
        final boolean[] facing = new boolean[4];
        int soil = FlatGround.soilColor(Climate.TEMPERATE);
        int top = FlatGround.topColor(Climate.TEMPERATE);
        /** Ants walking the tunnels behind the glass: the chamber belongs to a colony with ants. */
        boolean wallAnts;
        AntLook wallLook = AntLook.UNKNOWN;
    }

    /** A stacked Storage's shelves hold this many kinds of item. */
    private static final int SHELF_ITEMS = 6;

    public StructureRenderer(BlockEntityRendererProvider.Context context) {
        this.drawer = new AntDrawer(context.entityModelSet());
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StructureBlockEntity structure, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(structure, state, partialTicks, camera, breakProgress);
        state.upright = ColonyPartBlock.upright(structure.getBlockState());
        if (state.upright) {
            extractNest(structure, state.nest, partialTicks, camera);
            return;
        }
        state.kind = structure.kind();
        state.time = structure.getLevel() == null ? 0 : ClientHooks.animationTime(structure.getLevel().getGameTime(), partialTicks);
        state.seed = structure.getBlockPos().asLong();
        state.turn = (structure.getBlockState().getValue(ColonyEntityBlock.FACING).toYRot() + 180) % 360;
        state.site = structure.clientVisuals instanceof SiteVisuals site ? site : null;
        state.partialTicks = partialTicks;
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(structure.getBlockPos()).distanceToSqr(camera) < distance * distance;
        state.ants = 0;
        state.soldiers = 0;
        // The ground: the colony's soil in its climate's colours (the Formicarium's), its shape from the blocks around.
        if (structure.getLevel() != null) {
            FlatGround.extract(state.shape, structure.getLevel(), structure.getBlockPos(), structure.getBlockState());
        }
        FlatGround.facing(state.facing, (camera.x - structure.getBlockPos().getX()) * 16, (camera.z - structure.getBlockPos().getZ()) * 16);
        var colony = structure.clientLinked() ? ClientHooks.colonyOf(structure) : null;
        AntSpecies colonySpecies = colony == null ? null : ClientSpecies.get(colony.species());
        Climate climate = colonySpecies == null ? Climate.TEMPERATE : colonySpecies.climate();
        state.soil = FlatGround.soilColor(climate);
        state.top = FlatGround.topColor(climate);
        state.wallAnts = state.near && colony != null && colony.adults() > 0 && AntConfig.client(AntConfig.STRUCTURE_ANTS);
        state.wallLook = colonySpecies == null ? AntLook.UNKNOWN : colonySpecies.look();
        // The working ants are 3D models, so only up close and with 3D ants on: many busy chambers cost a lot of models.
        double detail = AntConfig.client(AntConfig.ANT_DETAIL_DISTANCE);
        boolean detailed = AntConfig.client(AntConfig.ANT_3D_MODELS)
                && Vec3.atCenterOf(structure.getBlockPos()).distanceToSqr(camera) < detail * detail;
        if (detailed && structure.kind().job().any() && structure.clientLinked() && structure.assigned() > 0
                && AntConfig.client(AntConfig.STRUCTURE_ANTS)) {
            int shown = (int) Math.min(6, 1 + Math.floor(Math.log(structure.assigned()) / Math.log(2.5)));
            // Hunters face off in pairs, lunging (the look of the hunt); everyone else goes about its site's work (SiteVisuals).
            if (structure.kind() == StructureKind.HUNTING_GROUND) {
                state.soldiers = Math.max(2, shown / 2 * 2);
            } else {
                state.ants = state.site == null ? 0 : state.site.ants.size();
            }
            BlockPos terrarium = structure.terrarium() != null ? structure.terrarium() : null;
            AntSpecies species = null;
            if (structure.getLevel() != null) {
                for (BlockPos pos : terrarium == null ? List.<BlockPos>of() : List.of(terrarium)) {
                    if (structure.getLevel().getBlockEntity(pos) instanceof TerrariumBlockEntity be) {
                        species = ClientSpecies.get(be.colony().species());
                    }
                }
            }
            state.look = species == null ? AntLook.UNKNOWN : species.look();
            state.farmers = species != null && species.canFarm();
            // Liquid Ants carry a droplet of their fluid.
            state.carry = state.kind == StructureKind.LIQUID_CHAMBER ? 0xFF000000 | state.look.crop().orElse(0x9AD8FF)
                    : state.kind == StructureKind.HARVESTING_SITE ? 0xFF5DAA3C
                    : state.look.crystal().map(c -> 0xFF000000 | c).orElse(0xFFD9B26A);
        }
        state.heap.clear();
        state.heapCounts.clear();
        if (structure.kind() == StructureKind.STORAGE && state.near) {
            Map<Item, Integer> totals = new HashMap<>();
            Map<Item, ItemStack> samples = new HashMap<>();
            for (int slot = 0; slot < structure.getContainerSize(); slot++) {
                ItemStack stack = structure.getItem(slot);
                if (!stack.isEmpty()) {
                    totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    samples.putIfAbsent(stack.getItem(), stack);
                }
            }
            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(totals.entrySet());
            sorted.sort(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()));
            int seed = (int) state.seed;
            for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                ItemStackRenderState item = new ItemStackRenderState();
                items.updateForTopItem(item, samples.get(sorted.get(i).getKey()), ItemDisplayContext.FIXED, structure.getLevel(), null, seed + i);
                state.heap.add(item);
                state.heapCounts.add(sorted.get(i).getValue());
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.upright) {
            NestRenderer.submit(state.nest, poseStack, collector);
            return;
        }
        drawGround(state, poseStack, collector);
        if (!state.near) {
            return;
        }
        if (!state.heap.isEmpty()) {
            drawHeap(state, poseStack, collector);
        }
        if (state.ants + state.soldiers <= 0) {
            return;
        }
        float top = FLOOR;
        // The ants' spots are laid out on the site's model, which the block turns to its facing.
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.turn));
        poseStack.translate(-0.5F, 0, -0.5F);
        for (int i = 0; i < state.ants + state.soldiers; i++) {
            boolean soldier = i >= state.ants;
            pose.reset();
            pose.time = state.time;
            pose.seed = i * 1.7F;
            float x;
            float z;
            float yaw;
            int carry = 0;
            if (soldier) {
                // Pairs of soldiers face off in a corner of the site, lunging at each other.
                int pair = (i - state.ants) / 2;
                boolean left = (i - state.ants) % 2 == 0;
                float lunge = Math.max(0, Mth.sin(state.time * 0.25F + pair * 2)) * 0.8F;
                float cx = 5 + (pair % 2) * 6;
                float cz = 5 + (pair / 2) * 6;
                x = cx + (left ? -1.8F + lunge : 1.8F - lunge);
                z = cz;
                yaw = left ? -Mth.HALF_PI : Mth.HALF_PI;
                pose.rear = 0.4F + lunge * 0.5F;
                pose.mandibleOpen = 0.5F + lunge * 0.5F;
                pose.alert = 1;
            } else {
                // Workers go about their site's work, each in its own time: gather and pile, tend, the portal, fill and carry.
                if (state.site == null || i >= state.site.ants.size() || state.site.ants.get(i).away()) {
                    continue;
                }
                SiteVisuals.Ant ant = state.site.ants.get(i);
                float pt = state.partialTicks;
                x = Mth.lerp(pt, ant.prevX, ant.x);
                z = Mth.lerp(pt, ant.prevZ, ant.z);
                yaw = Mth.rotLerpRad(pt, ant.prevYaw, ant.yaw);
                pose.seed = ant.seed;
                if (ant.moving) {
                    pose.walkPhase = Mth.lerp(pt, ant.prevPhase, ant.phase);
                    pose.walkAmount = 1;
                } else {
                    workPose(ant, pose, state.time);
                }
                carry = ant.loaded ? state.carry : 0;
            }
            poseStack.pushPose();
            poseStack.translate(x / 16, top / 16, z / 16);
            poseStack.mulPose(Axis.YP.rotation(yaw));
            Caste caste = soldier ? Caste.SOLDIER : Caste.WORKER;
            drawer.draw(collector, poseStack, caste, state.look, pose, LENGTH * AntDrawer.casteScale(caste), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, carry, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** Poses an ant that has stopped at its work. */
    private static void workPose(SiteVisuals.Ant ant, AntPose pose, float time) {
        float beat = Mth.sin(time * 0.6F + ant.seed);
        float progress = ant.stay <= 0 ? 0 : 1 - ant.pause / (float) ant.stay;
        switch (ant.work) {
            case GATHER -> {
                pose.headPitch = 0.45F + beat * 0.12F;
                pose.mandibleOpen = 0.5F + beat * 0.5F;
            }
            case DROP -> {
                pose.headPitch = 0.5F * Mth.sin(progress * Mth.PI);
                pose.mandibleOpen = progress < 0.5F ? 0.2F : 0.9F;
            }
            case TEND -> {
                pose.headPitch = 0.2F + beat * 0.1F;
                pose.mandibleOpen = 0.5F + Mth.sin(time * 0.9F + ant.seed) * 0.5F;
                pose.groom = 0.6F;
            }
            case FILL -> {
                pose.headPitch = 0.55F;
                pose.gasterPulse = 0.5F + beat * 0.5F;
            }
            case REST -> pose.groom = 0.8F + beat * 0.2F;
            default -> { }
        }
    }

    private static void drawGround(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        FlatGround.Shape shape = state.shape;
        @SuppressWarnings("unchecked")
        List<float[][]>[] paths = new List[4];
        @SuppressWarnings("unchecked")
        List<float[]>[] hollows = new List[4];
        for (int side = 0; side < 4; side++) {
            hollows[side] = new ArrayList<>();
            paths[side] = shape.outer(side) && state.facing[side] ? FlatGround.chamberPaths(shape, side, hollows[side]) : List.of();
        }
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, FlatGround.SOLID, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, FlatGround.TEX, light);
            FlatGround.soil(g, shape, state.soil, state.top, state.facing);
            int dark = Geometry.shade(state.soil, 0.42F);
            for (int side = 0; side < 4; side++) {
                if (!paths[side].isEmpty() || !hollows[side].isEmpty()) {
                    FlatGround.tunnels(g, side, shape, paths[side], hollows[side], dark);
                }
            }
            FlatGround.trails(g, shape, FlatGround.trailColor(state.soil, state.top));
        });
        if (state.wallAnts) {
            collector.submitCustomGeometry(poseStack, TerrariumRenderer.SIDE, (pose, buffer) -> {
                Geometry g = new Geometry(pose, buffer, FlatGround.TEX, light);
                for (int side = 0; side < 4; side++) {
                    FlatGround.walkers(g, side, paths[side], state.wallLook, state.time, shape.seed, 0xFFD9B26A);
                }
            });
        }
    }

    private void extractNest(StructureBlockEntity structure, NestRenderer.State nest, float partialTicks, Vec3 camera) {
        var block = structure.getBlockState();
        NestRenderer.extract(nest, structure.getBlockPos(), structure.getLevel(), block.getValue(ColonyEntityBlock.FACING), camera,
                partialTicks, AntConfig.client(AntConfig.VISUAL_DISTANCE));
        nest.visuals = structure.clientVisuals instanceof NestVisuals visuals ? visuals : null;
        nest.layout = nest.visuals == null ? null : nest.visuals.layout;
        var colony = ClientHooks.colonyOf(structure);
        AntSpecies species = colony == null ? null : ClientSpecies.get(colony.species());
        AntSpecies former = colony == null ? null : ClientSpecies.get(colony.formerSpecies());
        nest.climate = species == null ? Climate.TEMPERATE : species.climate();
        nest.look = species == null ? AntLook.UNKNOWN : species.look();
        nest.former = former == null ? nest.look : former.look();
        nest.queen = false;
        nest.stage = 1;
        nest.linked = structure.clientLinked();
        nest.lit = block.hasProperty(ExpeditionSiteBlock.ACTIVE) && block.getValue(ExpeditionSiteBlock.ACTIVE);
        nest.shown = nest.linked && structure.kind().job().any() && structure.assigned() > 0
                ? (int) Math.min(6, 1 + Math.floor(Math.log(structure.assigned()) / Math.log(2.5))) : 0;
        nest.items.clear();
        if (structure.kind() == StructureKind.STORAGE && nest.near && nest.front) {
            Map<Item, Integer> totals = new HashMap<>();
            Map<Item, ItemStack> samples = new HashMap<>();
            for (int slot = 0; slot < structure.getContainerSize(); slot++) {
                ItemStack stack = structure.getItem(slot);
                if (!stack.isEmpty()) {
                    totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    samples.putIfAbsent(stack.getItem(), stack);
                }
            }
            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(totals.entrySet());
            sorted.sort(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()));
            int seed = (int) structure.getBlockPos().asLong();
            for (int i = 0; i < Math.min(SHELF_ITEMS, sorted.size()); i++) {
                ItemStackRenderState item = new ItemStackRenderState();
                items.updateForTopItem(item, samples.get(sorted.get(i).getKey()), ItemDisplayContext.FIXED, structure.getLevel(), null, seed + i);
                nest.items.add(item);
            }
        }
    }

    private static void drawHeap(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float[][] spots = {{8, 8}, {5, 5}, {11, 5}, {5, 11}, {11, 11}};
        for (int i = 0; i < state.heap.size(); i++) {
            ItemStackRenderState item = state.heap.get(i);
            int layers = Math.min(4, 1 + state.heapCounts.get(i) / 48);
            for (int layer = 0; layer < layers; layer++) {
                poseStack.pushPose();
                poseStack.translate(spots[i][0] / 16, (FLOOR + 1.1F + layer * 0.5F + (i == 0 ? 0.4F : 0)) / 16, spots[i][1] / 16);
                poseStack.mulPose(Axis.YP.rotationDegrees((i * 67 + layer * 41) % 360));
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
                // About 4 px across: a load the ants could carry in, not a block-sized item.
                poseStack.scale(0.24F, 0.24F, 0.24F);
                item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
