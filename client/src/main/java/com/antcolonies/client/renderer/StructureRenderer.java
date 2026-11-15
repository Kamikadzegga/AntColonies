package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * Life on the colony chambers: ants at work on the working sites (as many as the site's real assignment calls for, up to
 * six), soldiers sparring at Foraging Sites, and the Storage's heap, made of the items it really holds (the most
 * plentiful ones, piled higher the more there are).
 */
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
        final List<ItemStackRenderState> heap = new ArrayList<>();
        final List<Integer> heapCounts = new ArrayList<>();
        /** In a stack: drawn as an upright nest, from {@link #nest}. */
        boolean upright;
        final NestRenderer.State nest = new NestRenderer.State();
        /** On the ground: the colony's soil under the chamber, run on into the blocks it joins (FlatGround). */
        final FlatGround.Shape shape = new FlatGround.Shape();
        final boolean[] facing = new boolean[4];
        int soil = FlatGround.soilColor(com.antcolonies.colony.species.Climate.TEMPERATE);
        int top = FlatGround.topColor(com.antcolonies.colony.species.Climate.TEMPERATE);
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
        state.upright = com.antcolonies.content.block.ColonyPartBlock.upright(structure.getBlockState());
        if (state.upright) {
            extractNest(structure, state.nest, partialTicks, camera);
            return;
        }
        state.kind = structure.kind();
        state.time = structure.getLevel() == null ? 0 : com.antcolonies.client.ClientHooks.animationTime(structure.getLevel().getGameTime(), partialTicks);
        state.seed = structure.getBlockPos().asLong();
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(structure.getBlockPos()).distanceToSqr(camera) < distance * distance;
        state.ants = 0;
        state.soldiers = 0;
        // The ground: the colony's soil in its climate's colours (the Formicarium's), its shape from the blocks around.
        if (structure.getLevel() != null) {
            FlatGround.extract(state.shape, structure.getLevel(), structure.getBlockPos(), structure.getBlockState());
        }
        FlatGround.facing(state.facing, (camera.x - structure.getBlockPos().getX()) * 16, (camera.z - structure.getBlockPos().getZ()) * 16);
        var colony = structure.clientLinked() ? com.antcolonies.client.ClientHooks.colonyOf(structure) : null;
        AntSpecies colonySpecies = colony == null ? null : ClientSpecies.get(colony.species());
        com.antcolonies.colony.species.Climate climate = colonySpecies == null ? com.antcolonies.colony.species.Climate.TEMPERATE : colonySpecies.climate();
        state.soil = FlatGround.soilColor(climate);
        state.top = FlatGround.topColor(climate);
        state.wallAnts = state.near && colony != null && colony.adults() > 0 && AntConfig.client(AntConfig.STRUCTURE_ANTS);
        state.wallLook = colonySpecies == null ? AntLook.UNKNOWN : colonySpecies.look();
        // The working ants are 3D models, so (like the Terrarium's surface ants) only up close and with 3D ants on: further
        // away they would be a pixel or two, and a ring of busy chambers costs a lot of models.
        double detail = AntConfig.client(AntConfig.ANT_DETAIL_DISTANCE);
        boolean detailed = AntConfig.client(AntConfig.ANT_3D_MODELS)
                && Vec3.atCenterOf(structure.getBlockPos()).distanceToSqr(camera) < detail * detail;
        if (detailed && structure.kind().job().any() && structure.clientLinked() && structure.assigned() > 0
                && AntConfig.client(AntConfig.STRUCTURE_ANTS)) {
            int shown = (int) Math.min(6, 1 + Math.floor(Math.log(structure.assigned()) / Math.log(2.5)));
            // Hunters face off in pairs, lunging (the look of the hunt); everyone else walks loops.
            if (structure.kind() == StructureKind.HUNTING_GROUND) {
                state.soldiers = Math.max(2, shown / 2 * 2);
            } else {
                state.ants = shown;
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
                // Workers walk loops over the site, carrying their finds.
                float speed = 0.03F + i * 0.004F;
                float angle = state.time * speed + i * Mth.TWO_PI / Math.max(1, state.ants) + (state.seed & 7);
                float radius = 3.2F + (i % 3) * 1.1F;
                x = 8 + Mth.cos(angle) * radius;
                z = 8 + Mth.sin(angle) * radius * 0.8F;
                yaw = (float) Math.atan2(Mth.sin(angle), -Mth.cos(angle) * 0.8F);
                pose.walkPhase = state.time * 0.5F + i;
                pose.walkAmount = 1;
                carry = (Mth.sin(angle * 0.5F) > 0) ? state.carry : 0;
            }
            poseStack.pushPose();
            poseStack.translate(x / 16, top / 16, z / 16);
            poseStack.rotate(Axis.YP.rotation(yaw));
            Caste caste = soldier ? Caste.SOLDIER : Caste.WORKER;
            drawer.draw(collector, poseStack, caste, state.look, pose, LENGTH * AntDrawer.casteScale(caste), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, carry, 0);
            poseStack.popPose();
        }
    }

    /**
     * The chamber's soil, at the colony's one ground height: its walls behind the outer glass with tunnels running on
     * into the next blocks', its surface with the trails to its neighbours, and (up close) ants walking the tunnels.
     */
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

    /** A chamber in a stack: its own chamber in the upright nest, the ants at work there, the Storage's items on its shelves. */
    private void extractNest(StructureBlockEntity structure, NestRenderer.State nest, float partialTicks, Vec3 camera) {
        var block = structure.getBlockState();
        NestRenderer.extract(nest, structure.getBlockPos(), structure.getLevel(), block.getValue(com.antcolonies.content.block.ColonyEntityBlock.FACING), camera,
                partialTicks, AntConfig.client(AntConfig.VISUAL_DISTANCE));
        nest.visuals = structure.clientVisuals instanceof com.antcolonies.client.nest.NestVisuals visuals ? visuals : null;
        nest.layout = nest.visuals == null ? null : nest.visuals.layout;
        var colony = com.antcolonies.client.ClientHooks.colonyOf(structure);
        AntSpecies species = colony == null ? null : ClientSpecies.get(colony.species());
        AntSpecies former = colony == null ? null : ClientSpecies.get(colony.formerSpecies());
        nest.climate = species == null ? com.antcolonies.colony.species.Climate.TEMPERATE : species.climate();
        nest.look = species == null ? AntLook.UNKNOWN : species.look();
        nest.former = former == null ? nest.look : former.look();
        nest.queen = false;
        nest.stage = 1;
        nest.linked = structure.clientLinked();
        nest.lit = block.hasProperty(com.antcolonies.content.block.ExpeditionSiteBlock.ACTIVE) && block.getValue(com.antcolonies.content.block.ExpeditionSiteBlock.ACTIVE);
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

    /** The most plentiful items, laid in a heap over the pile's crate; the more of one, the higher its stack. */
    private static void drawHeap(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float[][] spots = {{8, 8}, {5, 5}, {11, 5}, {5, 11}, {11, 11}};
        for (int i = 0; i < state.heap.size(); i++) {
            ItemStackRenderState item = state.heap.get(i);
            int layers = Math.min(4, 1 + state.heapCounts.get(i) / 48);
            for (int layer = 0; layer < layers; layer++) {
                poseStack.pushPose();
                poseStack.translate(spots[i][0] / 16, (FLOOR + 1.1F + layer * 0.5F + (i == 0 ? 0.4F : 0)) / 16, spots[i][1] / 16);
                poseStack.rotate(Axis.YP.rotationDegrees((i * 67 + layer * 41) % 360));
                poseStack.rotate(Axis.XP.rotationDegrees(90));
                // About 4 px across: a load the ants could carry in, not a block-sized item (the user, 2026-11-14: "too big").
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
