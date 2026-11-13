package com.antcolonies.content.worldgen;

import java.util.List;
import java.util.Locale;

import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * An anthill's alarm. A player in a full Chitin set smells like the colony and walks among an anthill's ants unharmed,
 * until they open (or break) one of its chests or barrels still holding its loot, step into its Queen's chamber, or
 * strike one of its ants. Then every ant of that anthill (named by its Queen's spot, {@link WildAnt#anthill}) is
 * alarmed at that player for {@code anthillAlarmSeconds}: soldiers and royal guards attack them, workers swarm them,
 * and all of them chase them through the tunnels, Chitin or not. Players without Chitin raise it the same way (its
 * soldiers already attack them on sight, but now the whole nest comes).
 */
public final class AnthillAlarm {
    /** How far from the Queen's spot an anthill's ants are looked for (the largest nests reach 28 blocks out, 46 down). */
    private static final double REACH = 64;
    /** How far from a chest the ants that own it are looked for. */
    private static final double CHEST_REACH = 40;

    /** What raised an alarm (it picks the message the player sees). */
    public enum Reason {
        CHEST, QUEEN, ATTACK;

        public String key() {
            return "message.antcolonies.anthill_alarm." + name().toLowerCase(Locale.ROOT);
        }
    }

    private AnthillAlarm() {}

    /** Players in creative or spectator mode never raise an alarm. */
    public static boolean exempt(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    /**
     * Alarms every ant of the anthill at the player (and extends an alarm already on); the player is told the first
     * time. Returns how many ants were alarmed.
     */
    public static int raise(ServerLevel level, BlockPos anthill, Player player, Reason reason) {
        long until = level.getGameTime() + AntConfig.get(AntConfig.ANTHILL_ALARM_SECONDS) * 20L;
        List<WildAnt> ants = level.getEntitiesOfClass(WildAnt.class, new AABB(anthill).inflate(REACH), ant -> anthill.equals(ant.anthill()));
        boolean fresh = false;
        for (WildAnt ant : ants) {
            fresh |= !ant.alarmedAt(player);
            ant.alarm(player, until);
        }
        if (fresh) {
            player.sendOverlayMessage(Component.translatable(reason.key()));
            level.playSound(null, player.blockPosition(), ModSounds.SOLDIER_BITE.get(), SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        return ants.size();
    }

    /** The anthill whose ants live nearest a spot (its Queen's spot), or null where none do. */
    public static @Nullable BlockPos anthillNear(ServerLevel level, BlockPos pos) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WildAnt ant : level.getEntitiesOfClass(WildAnt.class, new AABB(pos).inflate(CHEST_REACH), ant -> ant.anthill() != null)) {
            double distance = ant.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ant.anthill();
            }
        }
        return best;
    }

    /** Whether the block at a spot is an anthill's container still holding its loot (unopened). */
    public static boolean anthillContainer(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null
                && AnthillPiece.CONTAINER_LOOT.contains(container.getLootTable());
    }

    /** A player opening (or breaking) an anthill's container: the anthill nearby raises its alarm. */
    public static boolean containerTouched(ServerLevel level, BlockPos pos, Player player) {
        if (exempt(player) || !anthillContainer(level, pos)) {
            return false;
        }
        BlockPos anthill = anthillNear(level, pos);
        return anthill != null && raise(level, anthill, player, Reason.CHEST) > 0;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            containerTouched(level, event.getPos(), event.getEntity());
        }
    }

    public static void onBreakBlock(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            containerTouched(level, event.getPos(), event.getPlayer());
        }
    }
}
