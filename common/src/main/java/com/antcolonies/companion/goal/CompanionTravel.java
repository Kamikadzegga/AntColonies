package com.antcolonies.companion.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Puts a companion down beside someone, in the same world or in another. */
public final class CompanionTravel {
    private static final int ATTEMPTS = 24;
    private static final int REACH = 3;
    /** A crowd spreads a block further for every so many. */
    private static final int CROWD_PER_BLOCK = 12;
    private static final int MAX_SPREAD = 5;

    /** To a player wherever they are, beside them; {@code before} is how many went just before it. */
    public static void bring(Mob mob, ServerPlayer player, int before) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        mob.setTarget(null);
        if (mob.level() != level) {
            to(mob, level, player, before);
        } else if (!near(mob, level, player, before)) {
            mob.snapTo(player.getX(), player.getY(), player.getZ(), mob.getYRot(), mob.getXRot());
            mob.getNavigation().stop();
        }
    }

    /** Beside an entity in the same world, if there is room. */
    public static boolean near(Mob mob, ServerLevel level, Entity target) {
        return near(mob, level, target, 0);
    }

    private static boolean near(Mob mob, ServerLevel level, Entity target, int before) {
        BlockPos spot = spot(mob, level, target.blockPosition(), before);
        if (spot == null) {
            return false;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, mob.getYRot(), mob.getXRot());
        mob.getNavigation().stop();
        return true;
    }

    /**
     * Into another world, beside an entity there (on its own spot when there is no better one).
     *
     * @param before how many went there just before it
     */
    public static void to(Mob mob, ServerLevel level, Entity target, int before) {
        BlockPos spot = spot(mob, level, target.blockPosition(), before);
        Vec3 at = spot == null ? target.position() : Vec3.atBottomCenterOf(spot);
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.teleport(new TeleportTransition(level, at, Vec3.ZERO, mob.getYRot(), mob.getXRot(), TeleportTransition.DO_NOTHING));
    }

    private static @Nullable BlockPos spot(Mob mob, ServerLevel level, BlockPos center, int before) {
        int reach = REACH + Math.min(MAX_SPREAD, before / CROWD_PER_BLOCK);
        RandomSource random = mob.getRandom();
        for (int i = 0; i < ATTEMPTS; i++) {
            BlockPos pos = center.offset(random.nextIntBetweenInclusive(-reach, reach), random.nextIntBetweenInclusive(-1, 1),
                    random.nextIntBetweenInclusive(-reach, reach));
            // In another world the chunks round the player may not be in yet: they are fetched, or all would land on one spot.
            if (pos.getX() == center.getX() && pos.getZ() == center.getZ() || mob.level() == level && !level.isLoaded(pos)) {
                continue;
            }
            if (safe(mob, level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean safe(Mob mob, ServerLevel level, BlockPos pos) {
        if (mob.level() == level) {
            if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) != PathType.WALKABLE) {
                return false;
            }
        } else if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        return !(level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
                && level.noCollision(mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos)));
    }

    private CompanionTravel() {}
}
