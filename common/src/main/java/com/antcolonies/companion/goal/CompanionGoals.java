package com.antcolonies.companion.goal;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Guard Form's behaviour, laid over what the ant does by nature: its own goals stay, these come first. */
public final class CompanionGoals {
    /** How far from its post a guarding companion fights, in blocks. */
    public static final double GUARD_RANGE = 14;
    /** How far from its place a staying companion defends itself. */
    public static final double STAY_RANGE = 6;
    /** A following companion lets go of a fight when its owner is this far off. */
    public static final double FOLLOW_RANGE = 24;

    private static final int PRIORITY_POST = -4;
    private static final int PRIORITY_FOLLOW = -2;
    private static final int PRIORITY_HOP = -1;
    private static final int PRIORITY_TARGET = -4;

    /** Adds the goals to a companion that just entered a world (or just became one). It bites with its own attack goal. */
    public static void attach(Mob mob) {
        if (Companions.get(mob) == null || mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof CompanionPostGoal)) {
            return;
        }
        mob.goalSelector.addGoal(PRIORITY_POST, new CompanionPostGoal(mob));
        mob.goalSelector.addGoal(PRIORITY_FOLLOW, new CompanionFollowGoal(mob));
        mob.goalSelector.addGoal(PRIORITY_HOP, new CompanionHopGoal(mob));
        mob.targetSelector.addGoal(PRIORITY_TARGET, new CompanionTargetGoal(mob));
    }

    // --- Whom it fights ---

    /** Whether a companion may go for this target now: never a friend, and nobody without a reason or too far from where it belongs. */
    public static boolean mayFight(Mob mob, CompanionData data, LivingEntity target) {
        if (!target.isAlive() || target instanceof ArmorStand || Companions.friend(mob, data, target) || !within(mob, data, target)) {
            return false;
        }
        return fairGame(target) || provoked(mob, data, target);
    }

    /** Hostile by nature, and not one that blows up or cannot be reached. Neutral mobs need a reason. */
    static boolean fairGame(LivingEntity target) {
        return target instanceof Enemy && !(target instanceof NeutralMob) && !(target instanceof Player) && !explodes(target);
    }

    private static boolean explodes(LivingEntity target) {
        return target instanceof Creeper || target instanceof Ghast;
    }

    /** It started it (against the ant or its owner), or the owner did. */
    static boolean provoked(Mob mob, CompanionData data, LivingEntity target) {
        if (explodes(target)) {
            return false;
        }
        if (mob.getLastHurtByMob() == target || target instanceof Mob other && other.getTarget() == mob) {
            return true;
        }
        ServerPlayer owner = Companions.owner(mob, data);
        return owner != null && (owner.getLastHurtByMob() == target || owner.getLastHurtMob() == target
                || target instanceof Mob other && other.getTarget() == owner);
    }

    /** Whether a target is near enough to where the companion belongs: its owner, its post, or where it waits for its owner. */
    static boolean within(Mob mob, CompanionData data, LivingEntity target) {
        ServerPlayer owner = Companions.owner(mob, data);
        if (data.command() == CompanionCommand.FOLLOW && owner != null) {
            return target.distanceToSqr(owner) <= FOLLOW_RANGE * FOLLOW_RANGE;
        }
        double range = data.command() == CompanionCommand.GUARD ? GUARD_RANGE : STAY_RANGE;
        BlockPos post = data.command() == CompanionCommand.FOLLOW ? mob.blockPosition() : post(mob, data);
        return target.distanceToSqr(Vec3.atBottomCenterOf(post)) <= range * range;
    }

    static BlockPos post(Mob mob, CompanionData data) {
        return data.post().orElse(mob.blockPosition());
    }

    static @Nullable CompanionData data(Mob mob) {
        return Companions.get(mob);
    }

    private CompanionGoals() {}
}
