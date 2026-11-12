package com.antcolonies.client.renderer;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.model.AntDrawer;
import com.antcolonies.client.model.AntPose;
import com.antcolonies.content.entity.AntLike;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Wild ants and Guard Ants: the shared ant model in their species' colors, walking with a tripod gait, turning red when hurt and
 * rolling over when they die. Soldiers rear up and open their mandibles while they have a target.
 */
public class WildAntRenderer<T extends net.minecraft.world.entity.Mob & AntLike> extends EntityRenderer<T, WildAntRenderer.State> {
    private final AntDrawer drawer;
    private final AntPose pose = new AntPose();

    public static class State extends EntityRenderState {
        Caste caste = Caste.WORKER;
        AntLook look = AntLook.UNKNOWN;
        float bodyRot;
        float walkPos;
        float walkSpeed;
        float deathTime;
        boolean hurt;
        boolean aggressive;
        float seed;
        float scale = 1;
        boolean sitting;
        /** ARGB of what it carries home (a home nest ant's harvest), 0 for nothing. */
        int carry;
    }

    public WildAntRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.drawer = new AntDrawer(context.getModelSet());
        this.shadowRadius = 0.2F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T ant, State state, float partialTicks) {
        super.extractRenderState(ant, state, partialTicks);
        state.caste = ant.caste();
        state.look = ClientSpecies.look(ant.species());
        state.bodyRot = Mth.rotLerp(partialTicks, ant.yBodyRotO, ant.yBodyRot);
        state.walkPos = ant.walkAnimation.position(partialTicks);
        state.walkSpeed = Math.min(1, ant.walkAnimation.speed(partialTicks));
        state.deathTime = ant.deathTime > 0 ? ant.deathTime + partialTicks : 0;
        state.hurt = ant.hurtTime > 0 || ant.deathTime > 0;
        state.aggressive = ant.getTarget() != null;
        state.seed = (ant.getId() % 97) * 0.37F;
        state.scale = ant.renderScale();
        state.sitting = ant.sitting();
        state.carry = ant instanceof com.antcolonies.content.entity.WildAnt wild ? wild.carryColor() : 0;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - state.bodyRot));
        if (state.deathTime > 0) {
            float roll = Math.min(1, Mth.sqrt((state.deathTime - 1) / 20.0F * 1.6F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll * 180));
            poseStack.translate(0, -0.15F * roll, 0);
        }
        pose.reset();
        pose.time = state.ageInTicks;
        pose.seed = state.seed;
        pose.walkPhase = state.walkPos * 2.2F;
        pose.walkAmount = state.walkSpeed * 1.4F;
        if (state.sitting) {
            pose.walkAmount = 0;
            poseStack.translate(0, -0.04F * state.scale, 0);
        }
        if (state.aggressive && state.caste == Caste.SOLDIER) {
            pose.rear = 0.6F;
            pose.mandibleOpen = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.6F);
            pose.alert = 1;
        }
        if (state.caste == Caste.QUEEN) {
            pose.gasterPulse = 0.5F;
        }
        float length = 0.5F * AntDrawer.casteScale(state.caste) * state.scale;
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(state.hurt));
        drawer.draw(collector, poseStack, state.caste, state.look, pose, length, state.lightCoords, overlay, state.carry, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
