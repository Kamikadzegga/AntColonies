package com.antcolonies.companion;

import java.util.Optional;
import java.util.UUID;

import com.antcolonies.colony.research.Teams;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.companion.goal.CompanionGoals;
import com.antcolonies.content.entity.AntLike;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.advancement.ModTriggers;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModAttachments;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Which ants are companions and whose, and what turns one into one. The server decides everything here. */
public final class Companions {
    /** Ticks without being hurt before a companion starts to mend. */
    private static final int CALM_TICKS = 200;

    // --- Reading ---

    public static @Nullable CompanionData get(@Nullable Entity entity) {
        return entity instanceof Mob ? entity.getExistingDataOrNull(ModAttachments.COMPANION) : null;
    }

    public static boolean isCompanion(@Nullable Entity entity) {
        return get(entity) != null;
    }

    public static boolean isOwner(@Nullable CompanionData data, Player player) {
        return data != null && data.ownedBy(player.getUUID());
    }

    /** Its owner, if they are in the same dimension and alive. */
    public static @Nullable ServerPlayer owner(Mob mob, CompanionData data) {
        if (data.owner().isEmpty() || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getPlayerByUUID(data.owner().get().id()) instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()
                ? player : null;
    }

    /** Whether a mob can be recruited at all: the mod's own ants, of any species. */
    public static boolean companionable(Mob mob) {
        return mob instanceof AntLike;
    }

    /** A species' name as a Guard ("Iron Guard Ant"); a data pack's species without one is "Guard" and its own name. */
    public static Component guardName(CompanionData data) {
        return guardName(data.species());
    }

    public static Component guardName(Identifier species) {
        String key = AntSpecies.translationKey(species);
        return Component.translatableWithFallback(key + ".guard", "Guard %s", Component.translatable(key));
    }

    // --- Writing ---

    /** Stores new data on a companion and puts its numbers in place; the change goes to every client that sees it. */
    public static void set(Mob mob, CompanionData data) {
        mob.setData(ModAttachments.COMPANION, data);
        CompanionStats.apply(mob, data);
        // One that is out in the world is on its owner's roster; one being made for a tube is not.
        if (mob.level() instanceof ServerLevel level && CompanionIndex.byId(data.id()) == mob) {
            CompanionRoster.of(level.getServer()).note(mob, data);
        }
    }

    public static void command(Mob mob, CompanionData data, CompanionCommand command) {
        set(mob, data.withCommand(command, mob.blockPosition()));
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    /** Into the inventory, or at the player's feet when it is full. */
    public static void give(Player player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    /** A companion that has been left in peace for a while slowly mends. */
    public static void rest(Mob mob) {
        if (mob.getHealth() >= mob.getMaxHealth() || mob.getTarget() != null || mob.tickCount - mob.getLastHurtByMobTimestamp() <= CALM_TICKS
                || !mob.isAlive() || !isCompanion(mob)) {
            return;
        }
        int every = (int) Math.round(AntConfig.get(AntConfig.COMPANION_REGEN) * 20);
        if (every > 0 && mob.tickCount % every == 0) {
            mob.heal(1);
        }
    }

    // --- Recruiting ---

    /** Why a player cannot recruit this mob right now, or null: they can. */
    public static @Nullable Component refusal(Mob mob, Player player) {
        if (isCompanion(mob)) {
            return Component.translatable("message.antcolonies.companion.already");
        }
        if (mob instanceof OwnableEntity pet && pet.getOwnerReference() != null && !pet.getOwnerReference().matches(player)) {
            return Component.translatable("message.antcolonies.companion.owned");
        }
        if (mob instanceof WildAnt ant && ant.caste() == Caste.QUEEN) {
            return Component.translatable("message.antcolonies.companion.queen");
        }
        if (mob instanceof WildAnt ant && (ant.alarmed() || ant.getTarget() != null)) {
            return Component.translatable("message.antcolonies.companion.angry");
        }
        return tooMany(player);
    }

    /** The refusal for a player who has as many Guard Ants out as the server allows, or null. */
    public static @Nullable Component tooMany(Player player) {
        int most = AntConfig.get(AntConfig.COMPANION_MAX);
        if (most > 0 && player.level() instanceof ServerLevel level && CompanionRoster.of(level.getServer()).count(player.getUUID()) >= most) {
            return Component.translatable("message.antcolonies.companion.too_many", most);
        }
        return null;
    }

    /** Makes this very ant a companion: nothing about it is replaced. */
    public static CompanionData recruit(Mob mob, Player player) {
        // A wild ant is no hunter bred for it: its Hunting Strength is 1 to 3.
        CompanionData data = create(mob, 1 + mob.getRandom().nextInt(3), mob.getRandom());
        if (mob instanceof WildAnt ant) {
            ant.leaveHome();
        }
        join(mob, data, player);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 5, 0.3, 0.2, 0.3, 0);
            level.playSound(null, mob.blockPosition(), ModSounds.CAPTURE.get(), SoundSource.NEUTRAL, 0.8F, 1.3F);
        }
        player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.joined", guardName(data)).withStyle(ChatFormatting.GREEN));
        return get(mob);
    }

    /** Fresh data for an ant nobody owns yet. */
    public static CompanionData create(Mob mob, int strength, RandomSource random) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        Identifier species = mob instanceof AntLike ant ? ant.species() : ModRegistries.COMMON;
        return new CompanionData(UUID.randomUUID(), Optional.empty(), type, species, Math.clamp(strength, 1, 10), 1, 0, CompanionCommand.FOLLOW,
                Optional.empty(), CompanionData.Gear.EMPTY, Optional.empty(), Personality.values()[random.nextInt(Personality.values().length)]);
    }

    /** Takes a player as its owner and gets up and running. */
    public static void join(Mob mob, CompanionData data, Player player) {
        set(mob, data.withOwner(new CompanionData.Owner(player.getUUID(), player.getGameProfile().name())));
        if (mob instanceof TamableAnimal pet && pet.isTame()) {
            // A kind that knows owners of its own (an old Guard Ant) changes hands there too.
            pet.setOwner(player);
        }
        mob.setPersistenceRequired();
        mob.setTarget(null);
        mob.setHealth(mob.getMaxHealth());
        if (mob.level() instanceof ServerLevel level && mob.isAddedToLevel()) {
            CompanionIndex.add(mob);
            CompanionGoals.attach(mob);
            CompanionRoster.of(level.getServer()).note(mob, get(mob));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ModTriggers.COMPANION.get().trigger(serverPlayer, "recruited");
        }
    }

    // --- Friend or foe ---

    /** Whether a companion must never hurt this: its owner, their friends, and anyone's pet or companion. */
    public static boolean friend(Mob mob, CompanionData data, LivingEntity other) {
        if (other == mob || data.ownedBy(other.getUUID())) {
            return true;
        }
        if (get(other) != null || other instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
            return true;
        }
        if (!(other instanceof Player player)) {
            return false;
        }
        ServerPlayer owner = owner(mob, data);
        return owner == null || !owner.canHarmPlayer(player) || owner.isAlliedTo(player) || sameTeam(owner, player);
    }

    private static boolean sameTeam(ServerPlayer owner, Player player) {
        var server = owner.level().getServer();
        Optional<UUID> team = Teams.teamOf(server, owner.getUUID());
        return team.isPresent() && team.equals(Teams.teamOf(server, player.getUUID()));
    }

    private Companions() {}
}
