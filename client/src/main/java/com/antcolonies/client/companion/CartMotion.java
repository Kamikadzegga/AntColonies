package com.antcolonies.client.companion;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** How each cart in sight trails behind its ant. Only the client keeps this, and only while the ant is loaded. */
final class CartMotion {
    private static final Map<Mob, CartMotion> MOTIONS = new WeakHashMap<>();
    /** How far a cart swings out at most, in degrees. */
    private static final float MAX_SWING = 55;
    /** How much of the swing is taken back each tick, standing and at a walk. */
    private static final float RETURN_STANDING = 0.02F;
    private static final float RETURN_WALKING = 0.22F;

    private float yaw;
    private float pitch;
    private float last = -1;

    static CompanionLook.Cart of(Mob mob, float partialTicks) {
        return MOTIONS.computeIfAbsent(mob, m -> new CartMotion()).next(mob, partialTicks);
    }

    private CompanionLook.Cart next(Mob mob, float partialTicks) {
        float time = mob.tickCount + partialTicks;
        float body = Mth.rotLerp(partialTicks, mob.yBodyRotO, mob.yBodyRot);
        float walk = Math.min(1, mob.walkAnimation.speed(partialTicks));
        float climb = (float) Mth.clamp((mob.getY() - mob.yo) * 1.6, -0.5, 0.5);
        if (last < 0 || time < last || time - last > 20) {
            yaw = body;
            pitch = 0;
        } else {
            float dt = time - last;
            float back = 1 - (float) Math.pow(1 - Mth.lerp(walk, RETURN_STANDING, RETURN_WALKING), dt);
            yaw += Mth.wrapDegrees(body - yaw) * back;
            float off = Mth.clamp(Mth.wrapDegrees(body - yaw), -MAX_SWING, MAX_SWING);
            yaw = body - off;
            pitch += (climb - pitch) * (1 - (float) Math.pow(0.75, dt));
        }
        last = time;
        float swing = Mth.wrapDegrees(yaw - body) * Mth.DEG_TO_RAD;
        float roll = -mob.walkAnimation.position(partialTicks) * 1.1F;
        return new CompanionLook.Cart(swing, pitch, roll, Mth.sin(time * 1.9F) * 0.12F * walk);
    }
}
