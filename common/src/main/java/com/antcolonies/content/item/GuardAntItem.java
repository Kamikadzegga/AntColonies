package com.antcolonies.content.item;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.CompanionTube;
import com.antcolonies.companion.Companions;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/** A Guard Ant in its tube, with everything it is; using it on a block lets it out. */
public class GuardAntItem extends Item {
    /** What of a saved mob belongs to the place it was saved in. */
    private static final String[] LEFT_BEHIND = {"Pos", "Motion", "Rotation", "Passengers", "leash", "fall_distance", "OnGround", "PortalCooldown"};

    public GuardAntItem(Properties properties) {
        super(properties);
    }

    // --- Into the tube ---

    /** A companion in a tube (the mob itself stays where it is). Empty if it is no companion. */
    public static ItemStack tube(Mob mob) {
        CompanionData data = Companions.get(mob);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), AntColonies.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, mob.registryAccess());
            mob.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            for (String key : LEFT_BEHIND) {
                tag.remove(key);
            }
            ItemStack stack = new ItemStack(ModItems.GUARD_ANT.get());
            stack.set(ModDataComponents.COMPANION.get(), new CompanionTube(CustomData.of(tag), data, Companions.guardName(data),
                    mob.getHealth(), mob.getMaxHealth()));
            if (mob.hasCustomName()) {
                stack.set(DataComponents.CUSTOM_NAME, mob.getCustomName());
            }
            return stack;
        }
    }

    /** A colony's soldier in a tube: an ant of the colony's own species, whose owner is whoever lets it out. */
    public static ItemStack recruit(ServerLevel level, Identifier species, int strength) {
        Mob ant = soldier(level, species, strength);
        return ant == null ? ItemStack.EMPTY : tube(ant);
    }

    private static @Nullable Mob soldier(ServerLevel level, Identifier species, int strength) {
        WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (ant == null) {
            return null;
        }
        ant.setSpecies(species);
        ant.setCaste(Caste.SOLDIER);
        Companions.set(ant, Companions.create(ant, strength, level.getRandom()));
        ant.setHealth(ant.getMaxHealth());
        return ant;
    }

    // --- Out of the tube ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        CompanionTube tube = stack.get(ModDataComponents.COMPANION.get());
        GuardData old = stack.get(ModDataComponents.GUARD.get());
        if (player == null || tube == null && old == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (tube != null && tube.data().owner().isPresent() && !Companions.isOwner(tube.data(), player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.not_yours", tube.data().owner().get().name())
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Component tooMany = Companions.tooMany(player);
        if (tooMany != null) {
            player.sendOverlayMessage(tooMany.copy().withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Mob mob = tube != null ? open(level, tube) : openOld(level, old);
        if (mob == null) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.unknown").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
        mob.setYBodyRot(mob.getYRot());
        mob.setYHeadRot(mob.getYRot());
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            mob.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }
        if (!level.addFreshEntity(mob)) {
            return InteractionResult.FAIL;
        }
        float health = mob.getHealth();
        // Adding it may have given a copy its own id, so its data is read again.
        Companions.join(mob, Companions.get(mob).withCommand(CompanionCommand.FOLLOW, pos), player);
        mob.setHealth(health);
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** The mob a tube holds, of its own kind, with its own saved data; null if that kind is not in the game. */
    private static @Nullable Mob open(ServerLevel level, CompanionTube tube) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(tube.data().type()).orElse(null);
        if (type == null || !(type.create(level, EntitySpawnReason.SPAWN_ITEM_USE) instanceof Mob mob)) {
            return null;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), AntColonies.LOGGER)) {
            mob.load(TagValueInput.create(reporter, level.registryAccess(), tube.entity().copyTag()));
        }
        // A copied tube lets out another ant, not the same one twice.
        if (taken(level, mob.getUUID())) {
            mob.setUUID(UUID.randomUUID());
        }
        if (Companions.get(mob) == null) {
            Companions.set(mob, tube.data());
        }
        return mob;
    }

    private static boolean taken(ServerLevel level, UUID id) {
        for (ServerLevel other : level.getServer().getAllLevels()) {
            Entity entity = other.getEntity(id);
            if (entity != null && !entity.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    /** A tube from before the companions: its species and strength, as an ant of that species. */
    private static @Nullable Mob openOld(ServerLevel level, GuardData old) {
        Mob mob = soldier(level, old.species(), old.strength());
        if (mob != null && old.health() > 0) {
            mob.setHealth(Math.min(old.health(), mob.getMaxHealth()));
        }
        return mob;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompanionTube tube = stack.get(ModDataComponents.COMPANION.get());
        return tube == null ? super.getName(stack) : tube.name();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        CompanionTube tube = stack.get(ModDataComponents.COMPANION.get());
        GuardData old = stack.get(ModDataComponents.GUARD.get());
        if (tube != null) {
            CompanionData data = tube.data();
            tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.level", data.level(), data.strength()).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.health", Math.round(tube.health()), Math.round(tube.maxHealth()))
                    .withStyle(ChatFormatting.GRAY));
            for (int slot = 0; slot < CompanionData.GEAR_SLOTS; slot++) {
                if (!data.gear().get(slot).isEmpty()) {
                    tooltip.accept(Component.literal(" ").append(data.gear().get(slot).getHoverName()).withStyle(ChatFormatting.BLUE));
                }
            }
            if (data.owner().isPresent()) {
                tooltip.accept(Component.translatable("gui.antcolonies.companion.owner", data.owner().get().name()).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.trade").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.free").withStyle(ChatFormatting.GREEN));
            }
        } else if (old != null) {
            tooltip.accept(Tooltips.species(old.species()).withStyle(ChatFormatting.GOLD));
            AntSpecies species = ModRegistries.species(context.registries(), old.species());
            double max = GuardAnt.maxHealth(old.strength());
            double health = old.health() > 0 ? Math.min(old.health(), max) : max;
            tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.stats", old.strength(), Math.round(health), Math.round(max),
                    String.format(Locale.ROOT, "%.1f", GuardAnt.damage(old.strength())),
                    Math.round(GuardAnt.armor(species == null ? 1 : species.tier()))).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
