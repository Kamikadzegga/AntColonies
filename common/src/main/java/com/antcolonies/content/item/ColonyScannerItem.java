package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Colony Scanner reports on a Terrarium's colony (population, food, structures and every problem) or on one
 * structure (its colony link, its ants and its problems), in chat. It reads the server's numbers.
 */
public class ColonyScannerItem extends Item {
    public ColonyScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium) {
            if (!level.isClientSide()) {
                report(player, terrarium.colony());
            }
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity structure) {
            if (!level.isClientSide()) {
                report(player, structure);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void report(Player player, Colony colony) {
        player.sendSystemMessage(Component.translatable("scanner.antcolonies.colony", Tooltips.species(colony.species()), colony.stage())
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("scanner.antcolonies.population", colony.workers(), colony.soldiers(), colony.larvae(),
                colony.eggs()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("scanner.antcolonies.food", (long) colony.food(), (long) colony.stats().foodCapacity(),
                String.format(java.util.Locale.ROOT, "%.1f", colony.stats().foodIn()), String.format(java.util.Locale.ROOT, "%.1f", colony.stats().foodOut()))
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("scanner.antcolonies.health", Math.round(colony.health() * 100), colony.stats().structures())
                .withStyle(ChatFormatting.GRAY));
        if (colony.queen() != null) {
            player.sendSystemMessage(Component.translatable("scanner.antcolonies.queen", colony.queen().genome().lineage().name(),
                    Tooltips.rarity(colony.queen().genome().rarity())).withStyle(ChatFormatting.GRAY));
        }
        for (ColonyStatus status : ColonyStatus.of(colony.status())) {
            player.sendSystemMessage(Component.literal(" - ").append(Component.translatable(status.translationKey())).withStyle(status.color()));
        }
    }

    private static void report(Player player, StructureBlockEntity structure) {
        StructureKind kind = structure.kind();
        player.sendSystemMessage(Component.translatable(kind.translationKey()).withStyle(ChatFormatting.GOLD));
        if (!structure.isLinked()) {
            player.sendSystemMessage(Component.translatable(ColonyStatus.MISSING_CONNECTION.translationKey()).withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos terrarium = structure.terrarium();
        player.sendSystemMessage(Component.translatable("scanner.antcolonies.linked", terrarium.getX(), terrarium.getY(), terrarium.getZ())
                .withStyle(ChatFormatting.GRAY));
        if (kind.job().any()) {
            player.sendSystemMessage(Component.translatable("scanner.antcolonies.site", structure.assigned(), structure.capacity(),
                    String.format(java.util.Locale.ROOT, "%.2f", structure.rate())).withStyle(ChatFormatting.GRAY));
        }
        for (ColonyStatus status : ColonyStatus.of(structure.siteStatus())) {
            player.sendSystemMessage(Component.literal(" - ").append(Component.translatable(status.translationKey())).withStyle(status.color()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.colony_scanner").withStyle(ChatFormatting.GRAY));
    }
}
