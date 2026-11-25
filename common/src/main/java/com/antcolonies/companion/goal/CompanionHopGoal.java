package com.antcolonies.companion.goal;

import java.util.EnumSet;

import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.CompanionForms;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** The Jumping Ant's hops: now and then while it stands, more often on its way somewhere. */
public class CompanionHopGoal extends Goal {
    private static final int IDLE_CHANCE = 45;
    private static final int WALKING_CHANCE = 12;

    private final Mob mob;

    public CompanionHopGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        CompanionData data = CompanionGoals.data(mob);
        if (data == null || !data.hasForm(CompanionForms.JUMPING) || !mob.onGround() || mob.isInWater()) {
            return false;
        }
        boolean walking = !mob.getNavigation().isDone();
        return mob.getRandom().nextInt(reducedTickDelay(walking ? WALKING_CHANCE : IDLE_CHANCE)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        mob.getJumpControl().jump();
    }
}
