package com.antcolonies.content.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Reads a Queen's full genetic profile and lineage: right-click a Terrarium, or use it with a Queen Test Tube or Genetic
 * Sample in the other hand.
 */
public class GeneticAnalyzerItem extends Item {
    public GeneticAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TerrariumBlockEntity terrarium) {
            if (!context.getLevel().isClientSide()) {
                QueenData queen = terrarium.colony().queen();
                if (queen == null) {
                    context.getPlayer().sendSystemMessage(Component.translatable("status.antcolonies.no_queen").withStyle(ChatFormatting.RED));
                } else {
                    report(context.getPlayer(), queen);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        QueenData queen = other.get(ModDataComponents.QUEEN.get());
        if (queen == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            report(player, queen);
        }
        return InteractionResult.SUCCESS;
    }

    private static void report(Player player, QueenData queen) {
        List<Component> lines = new ArrayList<>();
        Tooltips.queen(queen, lines::add, true);
        player.sendSystemMessage(Component.translatable("analyzer.antcolonies.header").withStyle(ChatFormatting.AQUA));
        lines.forEach(player::sendSystemMessage);
        var lineage = queen.genome().lineage();
        if (!lineage.history().isEmpty()) {
            Component history = Component.empty();
            for (int i = 0; i < lineage.history().size(); i++) {
                if (i > 0) {
                    history = history.copy().append(" > ");
                }
                history = history.copy().append(Tooltips.species(lineage.history().get(i)));
            }
            player.sendSystemMessage(Component.translatable("analyzer.antcolonies.lineage", history).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.genetic_analyzer").withStyle(ChatFormatting.GRAY));
    }
}
