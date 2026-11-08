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
 * Life on the colony structures: ants at work on the job sites (as many as the site's real assignment calls for, up to
 * six), soldiers sparring on Fighting Sites, and the Resource Pile's heap, made of the items it really holds (the most
 * plentiful ones, piled higher the more there are).
 */
public class StructureRenderer implements BlockEntityRenderer<StructureBlockEntity, StructureRenderer.State> {
    private static final float LENGTH = 3.2F / 16;
    private final AntDrawer drawer;
    private final AntPose pose = new AntPose();
    private final ItemModelResolver items;

    public static class State extends BlockEntityRenderState {
        StructureKind kind = StructureKind.RESOURCE_PILE;
        int ants;
        AntLook look = AntLook.UNKNOWN;
        boolean farmers;
        float time;
        long seed;
        boolean near;
        int carry;
        final List<ItemStackRenderState> heap = new ArrayList<>();
        final List<Integer> heapCounts = new ArrayList<>();
    }

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
        state.kind = structure.kind();
        state.time = structure.getLevel() == null ? 0 : structure.getLevel().getGameTime() + partialTicks;
        state.seed = structure.getBlockPos().asLong();
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(structure.getBlockPos()).distanceToSqr(camera) < distance * distance;
        state.ants = 0;
        if (structure.kind().job().any() && structure.clientLinked() && structure.assigned() > 0 && AntConfig.client(AntConfig.STRUCTURE_ANTS)) {
            state.ants = (int) Math.min(6, 1 + Math.floor(Math.log(structure.assigned()) / Math.log(2.5)));
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
            state.carry = state.kind == StructureKind.FARMER_GARDEN ? 0xFF5DAA3C
                    : state.look.crystal().map(c -> 0xFF000000 | c).orElse(0xFFD9B26A);
        }
        state.heap.clear();
        state.heapCounts.clear();
        if (structure.kind() == StructureKind.RESOURCE_PILE && state.near) {
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
        if (!state.near) {
            return;
        }
        if (!state.heap.isEmpty()) {
            drawHeap(state, poseStack, collector);
        }
        if (state.ants <= 0) {
            return;
        }
        float top = switch (state.kind) {
            case SCAVENGING_SITE, FARMER_GARDEN, FIGHTING_SITE -> 12;
            default -> 16;
        };
        for (int i = 0; i < state.ants; i++) {
            pose.reset();
            pose.time = state.time;
            pose.seed = i * 1.7F;
            float x;
            float z;
            float yaw;
            int carry = 0;
            if (state.kind == StructureKind.FIGHTING_SITE) {
                // Pairs of soldiers face off in the arena, lunging at each other.
                int pair = i / 2;
                boolean left = i % 2 == 0;
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
                float angle = state.time * speed + i * Mth.TWO_PI / state.ants + (state.seed & 7);
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
            Caste caste = state.kind == StructureKind.FIGHTING_SITE ? Caste.SOLDIER : Caste.WORKER;
            drawer.draw(collector, poseStack, caste, state.look, pose, LENGTH * AntDrawer.casteScale(caste), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, carry, 0);
            poseStack.popPose();
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
                poseStack.translate(spots[i][0] / 16, (10.2F + layer * 0.9F + (i == 0 ? 0.8F : 0)) / 16, spots[i][1] / 16);
                poseStack.rotate(Axis.YP.rotationDegrees((i * 67 + layer * 41) % 360));
                poseStack.rotate(Axis.XP.rotationDegrees(90));
                poseStack.scale(0.42F, 0.42F, 0.42F);
                item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }
}
