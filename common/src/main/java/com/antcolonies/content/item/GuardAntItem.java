package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A Guard Ant in its tube. Use it on a block to let it out: it is yours, follows you and fights for you. Got by using
 * a Rally Pheromone on a Terrarium (the colony gives up {@link AntConfig#GUARD_SOLDIER_COST} soldiers).
 */
public class GuardAntItem extends Item {
    public GuardAntItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        GuardData data = stack.get(ModDataComponents.GUARD.get());
        Player player = context.getPlayer();
        if (data == null || player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        GuardAnt ant = ModEntities.GUARD_ANT.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (ant == null) {
            return InteractionResult.FAIL;
        }
        ant.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
        ant.tame(player);
        ant.setup(data);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            ant.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }
        level.addFreshEntity(ant);
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        GuardData data = stack.get(ModDataComponents.GUARD.get());
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Tooltips.species(data.species()).withStyle(ChatFormatting.GOLD));
        AntSpecies species = ModRegistries.species(context.registries(), data.species());
        double max = GuardAnt.maxHealth(data.strength());
        double health = data.health() > 0 ? Math.min(data.health(), max) : max;
        tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.stats", data.strength(), Math.round(health), Math.round(max),
                String.format(java.util.Locale.ROOT, "%.1f", GuardAnt.damage(data.strength())),
                Math.round(GuardAnt.armor(species == null ? 1 : species.tier()))).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.guard_ant.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
