package com.antcolonies.client.renderer;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.client.companion.CompanionLook;
import com.antcolonies.client.model.AntDrawer;
import com.antcolonies.client.model.AntPose;
import com.antcolonies.content.entity.AntLike;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;
import com.antcolonies.content.entity.WildAnt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** Draws wild ants and Guard Ants with the shared ant model, in their species' colors. */
public class WildAntRenderer<T extends Mob & AntLike> extends EntityRenderer<T, WildAntRenderer.State> {
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
        /** What it wears as a companion, or null. */
        @Nullable CompanionLook companion;
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
        CompanionData companion = Companions.get(ant);
        state.companion = companion == null ? null : CompanionLook.of(ant, companion, partialTicks);
        state.carry = ant instanceof WildAnt wild ? wild.carryColor() : 0;
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
        if (state.companion != null) {
            poseCompanion(state, state.companion);
        }
        float length = 0.5F * AntDrawer.casteScale(state.caste) * state.scale;
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(state.hurt));
        drawer.draw(collector, poseStack, state.caste, state.look, pose, length, state.lightCoords, overlay, state.carry, state.outlineColor,
                state.companion);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** A companion's manners: its transformation's, and the little things it does while it stands around. */
    private void poseCompanion(State state, CompanionLook look) {
        if (look.jumping()) {
            pose.bounce = -Math.abs(Mth.sin(state.ageInTicks * 0.22F)) * 0.5F;
            pose.alert = 1;
        }
        if (look.sad()) {
            pose.droop = 1;
            pose.walkAmount *= 0.7F;
            pose.bounce = 0.5F + Mth.sin(state.ageInTicks * 0.05F) * 0.25F;
        }
        if (look.idle() == null || state.walkSpeed > 0.1F || state.aggressive) {
            return;
        }
        // In and out gently.
        float amount = Mth.sin(look.idleAmount() * Mth.PI);
        switch (look.idle()) {
            case LOOK -> {
                pose.headYaw = Mth.sin(look.idleAmount() * Mth.TWO_PI) * 0.6F;
                pose.alert = Math.max(pose.alert, amount);
            }
            case GROOM -> {
                pose.groom = amount * (0.75F + 0.25F * Mth.sin(state.ageInTicks * 0.7F));
                pose.headPitch = 0.25F * amount;
            }
            case REST -> pose.bounce += 0.9F * amount;
            case OWNER -> {
                pose.headYaw = look.ownerYaw() * amount;
                pose.headPitch = -0.25F * amount;
            }
        }
    }
}
