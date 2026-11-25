package com.antcolonies.companion.item;

import java.util.function.Consumer;

import com.antcolonies.companion.CompanionData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Something to dress a companion up in. The client draws it by its costume id. */
public class CompanionCostumeItem extends Item implements CompanionGear {
    private final Identifier costume;

    public CompanionCostumeItem(Identifier costume, Properties properties) {
        super(properties);
        this.costume = costume;
    }

    @Override
    public int slot() {
        return CompanionData.SLOT_COSTUME;
    }

    public Identifier costume() {
        return costume;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.companion_costume").withStyle(ChatFormatting.GRAY));
    }
}
