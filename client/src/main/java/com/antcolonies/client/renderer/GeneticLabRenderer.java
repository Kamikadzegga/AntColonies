package com.antcolonies.client.renderer;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.LabPartBlock;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.species.AntLook;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Over a formed Genetic Laboratory's controller turns a glowing double helix in the colors of the Queen being worked
 * on, faster while an operation runs.
 */
public class GeneticLabRenderer implements BlockEntityRenderer<GeneticLabBlockEntity, GeneticLabRenderer.State> {
    private static final Identifier TEXTURE = AntColonies.id("textures/entity/terrarium_inside.png");
    private static final RenderType GLOW = RenderTypes.eyes(TEXTURE);

    public static class State extends BlockEntityRenderState {
        boolean formed;
        boolean working;
        float time;
        int colorA = 0xFF4FE3FF;
        int colorB = 0xFFE04FFF;
    }

    public GeneticLabRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GeneticLabBlockEntity lab, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(lab, state, partialTicks, camera, breakProgress);
        state.formed = lab.getBlockState().hasProperty(LabPartBlock.FORMED) && lab.getBlockState().getValue(LabPartBlock.FORMED);
        state.working = lab.operation() != GeneticLabBlockEntity.Operation.NONE;
        state.time = lab.getLevel() == null ? 0 : lab.getLevel().getGameTime() + partialTicks;
        QueenData queen = lab.queen();
        if (queen != null) {
            AntLook look = ClientSpecies.look(queen.species());
            state.colorA = 0xFF000000 | look.crystal().orElse(look.gaster() | 0x404040);
            state.colorB = 0xFF000000 | look.accent() | 0x303030;
        } else {
            state.colorA = 0xFF4FE3FF;
            state.colorB = 0xFFE04FFF;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed) {
            return;
        }
        float spin = state.time * (state.working ? 6 : 1.5F);
        poseStack.pushPose();
        poseStack.translate(0.5, 1.05, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(spin));
        int a = state.colorA;
        int b = state.colorB;
        collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, 64, LightCoordsUtil.FULL_BRIGHT);
            int steps = 14;
            for (int i = 0; i < steps; i++) {
                float y = i * 0.9F;
                float angle = i * 0.55F;
                float ax = Mth.cos(angle) * 2.6F;
                float az = Mth.sin(angle) * 2.6F;
                g.box(ax - 0.45F, y, az - 0.45F, ax + 0.45F, y + 0.7F, az + 0.45F, a, 49, 1, 55, 7);
                g.box(-ax - 0.45F, y, -az - 0.45F, -ax + 0.45F, y + 0.7F, -az + 0.45F, b, 49, 1, 55, 7);
                if (i % 2 == 0) {
                    // A rung between the strands: beads from one to the other.
                    for (int k = 1; k < 5; k++) {
                        float t = k / 5.0F;
                        float rx = Mth.lerp(t, ax, -ax);
                        float rz = Mth.lerp(t, az, -az);
                        g.box(rx - 0.2F, y + 0.2F, rz - 0.2F, rx + 0.2F, y + 0.5F, rz + 0.2F, k < 3 ? Geometry.shade(a, 0.7F) : Geometry.shade(b, 0.7F),
                                49, 1, 55, 7);
                    }
                }
            }
        });
        poseStack.popPose();
    }
}
