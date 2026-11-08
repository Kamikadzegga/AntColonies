package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Ant Eggs and Ant Larvae of a species: each item adds four to a colony of the same species. */
public class SpeciesItem extends Item {
    public SpeciesItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier species = stack.get(ModDataComponents.SPECIES.get());
        return species == null ? super.getName(stack) : Component.translatable(getDescriptionId() + ".of", Tooltips.species(species));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.brood.use").withStyle(ChatFormatting.GRAY));
    }
}
