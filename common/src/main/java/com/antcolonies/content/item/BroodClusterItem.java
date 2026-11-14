package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * An Ant Egg Cluster or Larva Cluster taken from an anthill with Silk Touch: it carries the anthill's species, goes
 * into the Terrarium of a working colony of that species, or is placed as a decoration that never hatches.
 */
public class BroodClusterItem extends BlockItem {
    private final boolean eggs;

    public BroodClusterItem(Block block, boolean eggs, Properties properties) {
        super(block, properties);
        this.eggs = eggs;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Identifier species = stack.get(ModDataComponents.SPECIES.get());
        if (species != null) {
            tooltip.accept(Tooltips.species(species).withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.brood_cluster.use." + (eggs ? "eggs" : "larvae"), TerrariumBlockEntity.CLUSTER_BROOD)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.brood_cluster.placed").withStyle(ChatFormatting.DARK_GRAY));
    }
}
