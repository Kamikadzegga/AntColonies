package com.antcolonies.companion.goal;

import java.util.Comparator;
import java.util.EnumSet;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** Picks a companion's fights: whoever hurts its owner or it, whom its owner hits, and hostile mobs around. */
public class CompanionTargetGoal extends Goal {
    private static final double SIGHT = 10;
    /** Ticks between two looks around for hostile mobs. */
    private static final int LOOK_INTERVAL = 15;
    /** How long a fight goes on without seeing the enemy, in ticks. */
    private static final int MEMORY = 80;

    private final Mob mob;
    private @Nullable LivingEntity found;
    private int nextLook;
    private int ownerHurtBy;
    private int ownerHurt;
    private int hurtBy;
    private int unseen;

    public CompanionTargetGoal(Mob mob) {
        this.mob = mob;
        this.nextLook = mob.getRandom().nextInt(LOOK_INTERVAL);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        CompanionData data = CompanionGoals.data(mob);
        if (data == null) {
            return false;
        }
        found = wronged(data);
        if (found == null && mob.tickCount >= nextLook) {
            nextLook = mob.tickCount + LOOK_INTERVAL;
            found = look(data);
        }
        return found != null;
    }

    /** Someone new who hurt the owner or the ant, or whom the owner hit: each blow is looked at once. */
    private @Nullable LivingEntity wronged(CompanionData data) {
        ServerPlayer owner = Companions.owner(mob, data);
        if (owner != null && data.command() != CompanionCommand.STAY) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && owner.getLastHurtByMobTimestamp() != ownerHurtBy) {
                ownerHurtBy = owner.getLastHurtByMobTimestamp();
                if (CompanionGoals.mayFight(mob, data, attacker)) {
                    return attacker;
                }
            }
            LivingEntity victim = owner.getLastHurtMob();
            if (victim != null && owner.getLastHurtMobTimestamp() != ownerHurt) {
                ownerHurt = owner.getLastHurtMobTimestamp();
                if (CompanionGoals.mayFight(mob, data, victim)) {
                    return victim;
                }
            }
        }
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker != null && mob.getLastHurtByMobTimestamp() != hurtBy) {
            hurtBy = mob.getLastHurtByMobTimestamp();
            if (CompanionGoals.mayFight(mob, data, attacker)) {
                return attacker;
            }
        }
        return null;
    }

    /** The nearest enemy in sight. A staying companion only minds those that are after it or its owner. */
    private @Nullable LivingEntity look(CompanionData data) {
        boolean onlyThreats = data.command() == CompanionCommand.STAY;
        double range = data.command() == CompanionCommand.GUARD ? CompanionGoals.GUARD_RANGE : SIGHT;
        ServerPlayer owner = Companions.owner(mob, data);
        return mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range, 4, range),
                        other -> suspect(other, owner, onlyThreats) && CompanionGoals.mayFight(mob, data, other))
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(4)
                .filter(mob.getSensing()::hasLineOfSight).findFirst().orElse(null);
    }

    /** A quick look that sorts out nearly everyone: in a crowd of companions this is asked very often. */
    private boolean suspect(Mob other, @Nullable ServerPlayer owner, boolean onlyThreats) {
        if (other == mob) {
            return false;
        }
        LivingEntity victim = other.getTarget();
        return !onlyThreats && CompanionGoals.fairGame(other) || victim != null && (victim == mob || victim == owner);
    }

    @Override
    public void start() {
        mob.setTarget(found);
        unseen = 0;
    }

    @Override
    public boolean canContinueToUse() {
        CompanionData data = CompanionGoals.data(mob);
        LivingEntity target = mob.getTarget();
        if (data == null || target == null || !CompanionGoals.within(mob, data, target) || Companions.friend(mob, data, target)) {
            return false;
        }
        // A new wrong outweighs the fight it is in.
        LivingEntity next = wronged(data);
        if (next != null && next != target) {
            mob.setTarget(next);
            unseen = 0;
            return true;
        }
        unseen = mob.getSensing().hasLineOfSight(target) ? 0 : unseen + 2;
        return target.isAlive() && unseen <= MEMORY;
    }

    @Override
    public void stop() {
        mob.setTarget(null);
        found = null;
    }
}
