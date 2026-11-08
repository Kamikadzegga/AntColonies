package com.antcolonies.client.renderer;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.content.block.AntTubeBlock;
import com.antcolonies.content.blockentity.AntTubeBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.model.AntDrawer;
import com.antcolonies.client.model.AntPose;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ants walking through a working colony's Glass Ant Tubes: along the tube floor from one opening to the next, some
 * carrying food, climbing where the tube runs up or down.
 */
public class AntTubeRenderer implements BlockEntityRenderer<AntTubeBlockEntity, AntTubeRenderer.State> {
    private static final float LENGTH = 2.6F / 16;
    private final AntDrawer drawer;
    private final AntPose pose = new AntPose();

    public static class State extends BlockEntityRenderState {
        boolean active;
        List<Direction> openings = new ArrayList<>();
        AntLook look = AntLook.UNKNOWN;
        float time;
        long seed;
        boolean near;
    }

    public AntTubeRenderer(BlockEntityRendererProvider.Context context) {
        this.drawer = new AntDrawer(context.entityModelSet());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AntTubeBlockEntity tube, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tube, state, partialTicks, camera, breakProgress);
        BlockState block = tube.getBlockState();
        state.active = AntTubeBlock.isActive(block) && tube.species() != null && AntConfig.client(AntConfig.TUBE_ANTS);
        state.openings.clear();
        if (state.active) {
            for (Direction direction : Direction.values()) {
                if (block.getValue(AntTubeBlock.SIDES.get(direction))) {
                    state.openings.add(direction);
                }
            }
            state.look = ClientSpecies.look(tube.species());
        }
        state.time = tube.getLevel() == null ? 0 : tube.getLevel().getGameTime() + partialTicks;
        state.seed = tube.getBlockPos().asLong();
        double distance = AntConfig.client(AntConfig.VISUAL_DISTANCE);
        state.near = Vec3.atCenterOf(tube.getBlockPos()).distanceToSqr(camera) < distance * distance;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.active || !state.near || state.openings.isEmpty()) {
            return;
        }
        int ants = state.openings.size() >= 2 ? 2 : 1;
        for (int i = 0; i < ants; i++) {
            float speed = 0.011F + (Math.floorMod(state.seed * 31 + i, 7)) * 0.0012F;
            float s = (state.time * speed + Math.floorMod(state.seed * 13 + i * 5, 100) / 100.0F) % 1;
            Direction from = state.openings.get(i % state.openings.size());
            Direction to = state.openings.size() > 1 ? state.openings.get((i + 1) % state.openings.size()) : from;
            // The first half from the opening to the middle, the second from the middle out again.
            boolean inbound = s < 0.5F;
            float t = inbound ? s * 2 : (s - 0.5F) * 2;
            Direction leg = inbound ? from : to;
            float along = inbound ? 1 - t : t;
            drawAnt(poseStack, collector, state, leg, along, inbound, i == 0 && (state.seed & 1) == 0 ? 0xFFD9B26A : 0);
        }
    }

    /**
     * An ant on the half of the tube towards {@code side}, {@code along} from the middle (0) to the opening (1), walking
     * outwards unless {@code inbound}.
     */
    private void drawAnt(PoseStack poseStack, SubmitNodeCollector collector, State state, Direction side, float along, boolean inbound, int carry) {
        float distance = along * 8;
        float x = 8 + side.getStepX() * distance;
        float y = 8 + side.getStepY() * distance;
        float z = 8 + side.getStepZ() * distance;
        poseStack.pushPose();
        if (side.getAxis().isHorizontal()) {
            // On the tube's floor.
            y -= 1.75F;
            poseStack.translate(x / 16, y / 16, z / 16);
            Direction heading = inbound ? side.getOpposite() : side;
            poseStack.rotate(Axis.YP.rotationDegrees(180 - heading.toYRot()));
        } else {
            // Climbing the tube's north wall.
            z -= 1.75F;
            poseStack.translate(x / 16, y / 16, z / 16);
            boolean up = (side == Direction.UP) != inbound;
            poseStack.rotate(Axis.XP.rotationDegrees(up ? 90 : -90));
            poseStack.rotate(Axis.YP.rotationDegrees(180));
        }
        pose.reset();
        pose.time = state.time;
        pose.walkPhase = state.time * 0.55F;
        pose.walkAmount = 1;
        pose.seed = (float) (state.seed % 17);
        drawer.draw(collector, poseStack, Caste.WORKER, state.look, pose, LENGTH, state.lightCoords, OverlayTexture.NO_OVERLAY, carry, 0);
        poseStack.popPose();
    }
}
