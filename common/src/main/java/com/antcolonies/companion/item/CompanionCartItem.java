package com.antcolonies.companion.item;

import java.util.function.Consumer;

import com.antcolonies.companion.CompanionCart;
import com.antcolonies.companion.CompanionData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A little cart: a companion that pulls it is a Cart Ant and carries nine stacks. */
public class CompanionCartItem extends Item implements CompanionGear {
    public CompanionCartItem(Properties properties) {
        super(properties);
    }

    @Override
    public int slot() {
        return CompanionData.SLOT_CART;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.ant_cart", CompanionCart.SIZE).withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.translatable("tooltip.antcolonies.companion_gear").withStyle(ChatFormatting.GRAY));
    }
}
