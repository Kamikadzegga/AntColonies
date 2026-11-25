package com.antcolonies.client.companion;

import com.antcolonies.companion.CompanionData;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Which way a companion's owner is from it. */
final class OwnerGaze {
    private static final float MAX_TURN = 60;

    /** The turn of its head towards its owner, in radians (0 with no owner in sight). */
    static float yaw(Mob mob, CompanionData data, float partialTicks) {
        if (data.owner().isEmpty()) {
            return 0;
        }
        Player owner = mob.level().getPlayerByUUID(data.owner().get().id());
        if (owner == null || owner.distanceToSqr(mob) > 256) {
            return 0;
        }
        float to = (float) (Mth.atan2(owner.getZ() - mob.getZ(), owner.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90;
        float body = Mth.rotLerp(partialTicks, mob.yBodyRotO, mob.yBodyRot);
        return Mth.clamp(Mth.wrapDegrees(to - body), -MAX_TURN, MAX_TURN) * Mth.DEG_TO_RAD;
    }

    private OwnerGaze() {}
}
