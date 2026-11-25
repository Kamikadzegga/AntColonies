package com.antcolonies.companion.goal;

import java.util.EnumSet;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Staying: it stands where it was told to. Guarding: it walks a small round about its post. Both come back after a fight.
 * A follower whose owner is away (offline, dead, in another dimension) waits where it is the same way.
 */
public class CompanionPostGoal extends Goal {
    private static final int PATROL = 5;

    private final Mob mob;
    /** Where a follower waits while its owner is away. */
    private @Nullable BlockPos waiting;
    private int nextPath;
    private int nextRound;

    public CompanionPostGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        CompanionData data = CompanionGoals.data(mob);
        if (data == null) {
            return false;
        }
        boolean alone = data.command() == CompanionCommand.FOLLOW && Companions.owner(mob, data) == null;
        if (data.command() == CompanionCommand.FOLLOW && !alone) {
            waiting = null;
            return false;
        }
        if (alone && waiting == null) {
            waiting = mob.blockPosition();
        }
        return mob.getTarget() == null && !mob.isPassenger();
    }

    @Override
    public void start() {
        nextPath = 0;
        nextRound = 40;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        CompanionData data = CompanionGoals.data(mob);
        if (data == null) {
            return;
        }
        boolean guard = data.command() == CompanionCommand.GUARD;
        BlockPos post = data.command() == CompanionCommand.FOLLOW && waiting != null ? waiting : CompanionGoals.post(mob, data);
        double away = mob.distanceToSqr(Vec3.atBottomCenterOf(post));
        nextPath--;
        nextRound--;
        if (away > (guard ? (PATROL + 2) * (PATROL + 2) : 2.5)) {
            if (nextPath <= 0) {
                nextPath = adjustedTickDelay(20);
                mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
            }
        } else if (!guard) {
            mob.getNavigation().stop();
        } else if (nextRound <= 0 && mob.getNavigation().isDone()) {
            nextRound = adjustedTickDelay(80 + mob.getRandom().nextInt(80));
            Vec3 spot = mob instanceof PathfinderMob walker ? LandRandomPos.getPos(walker, PATROL, 2) : null;
            if (spot != null && spot.distanceToSqr(Vec3.atBottomCenterOf(post)) <= PATROL * PATROL) {
                mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.8);
            }
        }
    }
}
