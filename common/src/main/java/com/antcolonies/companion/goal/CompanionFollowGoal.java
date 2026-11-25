package com.antcolonies.companion.goal;

import java.util.EnumSet;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/** Keeps a following companion near its owner: it walks, and catches up at once when it is far behind or stuck. */
public class CompanionFollowGoal extends Goal {
    private static final double START = 6;
    private static final double STOP = 2.5;
    private static final double SPEED = 1.15;
    /** Checks (half a second each) without getting nearer before it counts as stuck. */
    private static final int STUCK = 6;

    private final Mob mob;
    private @Nullable ServerPlayer owner;
    private int nextPath;
    private int stuck;
    private double nearest;
    private float waterMalus;

    public CompanionFollowGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        CompanionData data = CompanionGoals.data(mob);
        if (data == null || data.command() != CompanionCommand.FOLLOW || mob.isPassenger() || mob.isLeashed()) {
            return false;
        }
        owner = Companions.owner(mob, data);
        if (owner == null) {
            return false;
        }
        double away = mob.distanceToSqr(owner);
        if (mob.getTarget() != null) {
            // A fight goes on until the owner has walked away from it.
            return away > CompanionGoals.FOLLOW_RANGE * CompanionGoals.FOLLOW_RANGE;
        }
        return away > START * START;
    }

    @Override
    public boolean canContinueToUse() {
        CompanionData data = CompanionGoals.data(mob);
        return data != null && data.command() == CompanionCommand.FOLLOW && mob.getTarget() == null && owner != null && owner.isAlive()
                && owner.level() == mob.level() && !mob.isPassenger() && !mob.isLeashed() && mob.distanceToSqr(owner) > STOP * STOP;
    }

    @Override
    public void start() {
        mob.setTarget(null);
        nextPath = 0;
        stuck = 0;
        nearest = Double.MAX_VALUE;
        waterMalus = mob.getPathfindingMalus(PathType.WATER);
        mob.setPathfindingMalus(PathType.WATER, 0);
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
        mob.setPathfindingMalus(PathType.WATER, waterMalus);
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        mob.getLookControl().setLookAt(owner, 10, mob.getMaxHeadXRot());
        if (--nextPath > 0) {
            return;
        }
        nextPath = adjustedTickDelay(10);
        double away = mob.distanceTo(owner);
        if (away < nearest - 0.5) {
            nearest = away;
            stuck = 0;
        } else {
            stuck++;
        }
        if (away > AntConfig.get(AntConfig.COMPANION_TELEPORT) || stuck >= STUCK && away > START) {
            if (mob.level() instanceof ServerLevel level && CompanionTravel.near(mob, level, owner)) {
                stuck = 0;
                nearest = Double.MAX_VALUE;
                return;
            }
        }
        mob.getNavigation().moveTo(owner, SPEED);
    }
}
