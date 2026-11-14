package com.antcolonies.client.renderer;

import com.antcolonies.AntColonies;
import com.antcolonies.client.FluidLook;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * What shows behind a Droplet Centrifuge's glass: the rotor (a hub, four arms and a tube hanging from each), turning
 * fast while a droplet is spun and standing still otherwise, and under it the fluid in the tank, drawn with the fluid's
 * own texture and tint and as deep as the tank is full.
 */
public class CentrifugeRenderer implements BlockEntityRenderer<CentrifugeBlockEntity, CentrifugeRenderer.State> {
    /** A plain pale patch of this texture, tinted: the rotor's metal and glass. */
    private static final Identifier TEXTURE = AntColonies.id("textures/entity/terrarium_inside.png");
    private static final RenderType METAL = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType FLUID = RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
    /** The glass section inside the block's model (pixels): the rotor and the fluid live between these. */
    private static final float FLOOR = 8.05F;
    private static final float CEILING = 13.95F;
    private static final float WALL = 1.6F;

    public static class State extends BlockEntityRenderState {
        boolean working;
        float time;
        float fill;
        @Nullable TextureAtlasSprite sprite;
        int fluidColor;
        int fluidLight;
        int tube = 0xFFBFD8E6;
        long seed;
    }

    public CentrifugeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CentrifugeBlockEntity centrifuge, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(centrifuge, state, partialTicks, camera, breakProgress);
        state.working = centrifuge.working();
        state.time = centrifuge.getLevel() == null ? 0 : centrifuge.getLevel().getGameTime() + partialTicks;
        state.seed = centrifuge.getBlockPos().asLong();
        FluidStack fluid = centrifuge.fluid();
        if (fluid.isEmpty()) {
            state.sprite = null;
            state.fill = 0;
            state.tube = 0xFFBFD8E6;
        } else {
            state.sprite = FluidLook.sprite(fluid.getFluid());
            state.fluidColor = FluidLook.color(fluid.getFluid());
            state.fluidLight = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, fluid.getFluidType().getLightLevel(fluid));
            state.fill = Math.clamp(fluid.getAmount() / (float) Math.max(1, centrifuge.tankCapacity()), 0, 1);
            state.tube = FluidLook.accent(fluid.getFluid());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // The fluid: a pool under the rotor, never less than a film while there is any.
        TextureAtlasSprite sprite = state.sprite;
        if (sprite != null && state.fill > 0) {
            float top = FLOOR + 0.5F + (CEILING - FLOOR - 1.2F) * state.fill;
            int color = state.fluidColor;
            int light = state.fluidLight;
            collector.submitCustomGeometry(poseStack, FLUID, (pose, buffer) -> {
                Geometry g = new Geometry(pose, buffer, 16, light);
                float a = WALL;
                float b = 16 - WALL;
                float u0 = sprite.getU(a / 16);
                float u1 = sprite.getU(b / 16);
                float v0 = sprite.getV(a / 16);
                float v1 = sprite.getV(b / 16);
                g.quadUv(new float[][] {{a, top, a}, {a, top, b}, {b, top, b}, {b, top, a}},
                        new float[][] {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}}, color, new float[] {0, 1, 0});
                // The four sides, as tall as the fluid is deep.
                float sv0 = sprite.getV(1 - (top - FLOOR) / 16);
                float sv1 = sprite.getV(1);
                float[][] sideUv = {{u0, sv1}, {u1, sv1}, {u1, sv0}, {u0, sv0}};
                int side = Geometry.shade(color, 0.8F);
                g.quadUv(new float[][] {{b, FLOOR, a}, {a, FLOOR, a}, {a, top, a}, {b, top, a}}, sideUv, side, new float[] {0, 0, -1});
                g.quadUv(new float[][] {{a, FLOOR, b}, {b, FLOOR, b}, {b, top, b}, {a, top, b}}, sideUv, side, new float[] {0, 0, 1});
                g.quadUv(new float[][] {{a, FLOOR, a}, {a, FLOOR, b}, {a, top, b}, {a, top, a}}, sideUv, side, new float[] {-1, 0, 0});
                g.quadUv(new float[][] {{b, FLOOR, b}, {b, FLOOR, a}, {b, top, a}, {b, top, b}}, sideUv, side, new float[] {1, 0, 0});
            });
        }
        // The rotor.
        float angle = state.working ? state.time * 27 : (state.seed & 3) * 22.5F;
        int light = state.lightCoords;
        int tube = state.tube;
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(angle));
        collector.submitCustomGeometry(poseStack, METAL, (pose, buffer) -> {
            Geometry g = new Geometry(pose, buffer, 64, light);
            int steel = 0xFF8D99A3;
            int dark = 0xFF4E5860;
            // The shaft, from the deck to the lid, with a collar where the arms sit.
            g.box(-0.8F, FLOOR, -0.8F, 0.8F, CEILING, 0.8F, dark, 49, 1, 55, 7);
            g.box(-1.5F, 11.6F, -1.5F, 1.5F, 12.8F, 1.5F, steel, 49, 1, 55, 7);
            for (int arm = 0; arm < 4; arm++) {
                boolean alongX = arm % 2 == 0;
                float sign = arm < 2 ? 1 : -1;
                // An arm out from the collar, and the tube hanging at its end: glass, with the fluid's colour in it.
                float a0 = sign * 1.5F;
                float a1 = sign * 5.4F;
                float t0 = sign * 4.3F;
                float t1 = sign * 6.1F;
                if (alongX) {
                    g.box(Math.min(a0, a1), 11.9F, -0.45F, Math.max(a0, a1), 12.5F, 0.45F, steel, 49, 1, 55, 7);
                    g.box(Math.min(t0, t1), 9.0F, -0.9F, Math.max(t0, t1), 11.9F, 0.9F, tube, 49, 1, 55, 7);
                    g.box(Math.min(t0, t1) - 0.15F, 11.5F, -1.05F, Math.max(t0, t1) + 0.15F, 12.1F, 1.05F, dark, 49, 1, 55, 7);
                } else {
                    g.box(-0.45F, 11.9F, Math.min(a0, a1), 0.45F, 12.5F, Math.max(a0, a1), steel, 49, 1, 55, 7);
                    g.box(-0.9F, 9.0F, Math.min(t0, t1), 0.9F, 11.9F, Math.max(t0, t1), tube, 49, 1, 55, 7);
                    g.box(-1.05F, 11.5F, Math.min(t0, t1) - 0.15F, 1.05F, 12.1F, Math.max(t0, t1) + 0.15F, dark, 49, 1, 55, 7);
                }
            }
        });
        poseStack.popPose();
    }
}
