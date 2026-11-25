package com.antcolonies.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.antcolonies.companion.goal.CompanionGoals;
import com.antcolonies.companion.goal.CompanionTravel;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModAttachments;
import com.antcolonies.core.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The game's events as companions see them. */
public final class CompanionEvents {
    /** Further than this in one tick is no walk, ride or flight: the player was moved (a teleport, a portal, a respawn). */
    private static final double JUMP = 24;
    /** Followers this near the place a player was moved from go with them. */
    private static final double NEARBY = 30;
    /** How often a player's calls are looked at, in ticks. */
    private static final int CALL_CHECK_TICKS = 10;

    /** Where a player was a tick ago. */
    private record Place(ResourceKey<Level> dimension, Vec3 pos) {}

    private static final Map<UUID, Place> LAST = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(CompanionEvents::onJoin);
        bus.addListener(CompanionEvents::onLeave);
        bus.addListener(EventPriority.HIGH, CompanionEvents::onInteract);
        bus.addListener(CompanionEvents::onTarget);
        bus.addListener(CompanionEvents::onDamage);
        bus.addListener(CompanionEvents::onDrops);
        bus.addListener(CompanionEvents::onPlayerTick);
        bus.addListener(CompanionEvents::onLogout);
        bus.addListener(CompanionEvents::onStopped);
        bus.addListener(CompanionXp::onEffect);
        bus.addListener(EventPriority.LOWEST, CompanionXp::onDeath);
    }

    // --- Coming and going ---

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob instanceof GuardAnt guard) {
            guard.becomeCompanion();
        }
        if (Companions.isCompanion(mob)) {
            // Adding it may give a copy an id of its own, so its data is read after.
            CompanionIndex.add(mob);
            CompanionData data = Companions.get(mob);
            CompanionStats.apply(mob, data);
            CompanionGoals.attach(mob);
            CompanionRoster.of(level.getServer()).note(mob, data);
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        CompanionData data = Companions.get(mob);
        CompanionIndex.remove(mob);
        if (data == null || level.getServer().isStopped()) {
            return;
        }
        // Dead or in a tube it is gone from the roster; unloaded, the roster keeps where it was.
        Entity.RemovalReason reason = mob.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            CompanionRoster.of(level.getServer()).forget(data.id());
        } else {
            CompanionRoster.of(level.getServer()).note(mob, data);
        }
    }

    private static void onStopped(ServerStoppedEvent event) {
        CompanionIndex.clear();
        LAST.clear();
        CompanionXp.clear();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            CompanionRoster.of(player.level().getServer()).dropCalls(player.getUUID());
        }
    }

    /**
     * A player who was moved all at once takes the followers at their side along: through a portal, by a teleport of any
     * mod, or to where they wake up after dying.
     */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Place now = new Place(level.dimension(), player.position());
        Place last = LAST.put(player.getUUID(), now);
        if (player.tickCount % CALL_CHECK_TICKS == 0) {
            CompanionRoster.of(level.getServer()).answerCalls(player);
        }
        if (last == null || player.isSpectator()) {
            return;
        }
        boolean otherWorld = last.dimension() != now.dimension();
        if (otherWorld ? !AntConfig.get(AntConfig.COMPANION_DIMENSIONS) : last.pos().distanceToSqr(now.pos()) < JUMP * JUMP) {
            return;
        }
        int brought = 0;
        for (Mob mob : CompanionIndex.of(player.getUUID())) {
            CompanionData data = Companions.get(mob);
            if (data != null && data.command() == CompanionCommand.FOLLOW && mob.isAlive() && mob.level().dimension() == last.dimension()
                    && mob.position().distanceToSqr(last.pos()) <= NEARBY * NEARBY && !mob.isLeashed() && !mob.isPassenger()) {
                CompanionTravel.bring(mob, player, brought++);
            }
        }
    }

    // --- Interaction ---

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob mob) || event.getEntity().isSpectator()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        CompanionData data = Companions.get(mob);
        InteractionResult result = data == null ? recruit(mob, player, stack)
                : Companions.isOwner(data, player)
                ? CompanionInteractions.run(new CompanionInteractions.Context(mob, data, player, event.getHand(), stack))
                : stranger(data, player, event.getHand());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** Nobody but its owner names, leashes, equips or opens a companion. */
    private static InteractionResult stranger(CompanionData data, Player player, InteractionHand hand) {
        if (!player.level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.not_yours",
                    data.owner().map(CompanionData.Owner::name).orElse("?")).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult recruit(Mob mob, Player player, ItemStack stack) {
        if (!stack.is(ModItems.RALLY_PHEROMONE.get()) || !Companions.companionable(mob)) {
            return null;
        }
        if (!player.level().isClientSide()) {
            Component refusal = Companions.refusal(mob, player);
            if (refusal != null) {
                player.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
            } else {
                Companions.recruit(mob, player);
                stack.consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // --- Fighting ---

    /** The last word on whom a companion goes for, whichever of its goals picked it. */
    private static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            CompanionData data = Companions.get(mob);
            if (data != null && !CompanionGoals.mayFight(mob, data, target)) {
                event.setCanceled(true);
            }
        }
    }

    /** A companion's blow never lands on a friend, whatever it was meant for. */
    private static void onDamage(LivingIncomingDamageEvent event) {
        // A crowd at its owner's side is no harm to itself.
        if (event.getSource().is(DamageTypes.CRAMMING) && Companions.isCompanion(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Mob mob && mob != event.getEntity()) {
            CompanionData data = Companions.get(mob);
            if (data != null && Companions.friend(mob, data, event.getEntity())) {
                event.setCanceled(true);
            }
        }
    }

    /** What it wore and carried is left behind when it dies. */
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        CompanionData data = Companions.get(mob);
        if (data == null) {
            return;
        }
        List<ItemStack> left = new ArrayList<>();
        for (int slot = 0; slot < CompanionData.GEAR_SLOTS; slot++) {
            left.add(data.gear().get(slot).copy());
        }
        left.addAll(CompanionCart.of(mob).removeAllItems());
        for (ItemStack stack : left) {
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), stack);
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
            }
        }
        mob.setData(ModAttachments.COMPANION, data.withGear(CompanionData.Gear.EMPTY));
        data.owner().map(owner -> level.getServer().getPlayerList().getPlayer(owner.id()))
                .ifPresent(owner -> owner.sendSystemMessage(event.getSource().getLocalizedDeathMessage(mob)));
    }

    private CompanionEvents() {}
}
