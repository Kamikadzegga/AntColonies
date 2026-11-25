package com.antcolonies.companion.item;

import java.util.function.Consumer;

import com.antcolonies.companion.CompanionData;
import com.antcolonies.content.item.ChitinGear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Plates for a companion, one set for every tier of chitin. They never wear out. */
public class CompanionArmorItem extends Item implements CompanionGear {
    private final ChitinGear.Tier tier;

    public CompanionArmorItem(ChitinGear.Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public int slot() {
        return CompanionData.SLOT_ARMOR;
    }

    public ChitinGear.Tier tier() {
        return tier;
    }

    /** Armor points, next to up to 10 of the Guard Form's own. */
    public int defense() {
        return switch (tier) {
            case CHITIN -> 3;
            case HARDENED -> 4;
            case INFERNAL -> 6;
            case VOID -> 7;
        };
    }

    public int toughness() {
        return switch (tier) {
            case CHITIN, HARDENED -> 0;
            case INFERNAL -> 1;
            case VOID -> 2;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.companion_armor", defense()).withStyle(ChatFormatting.BLUE));
        if (toughness() > 0) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.companion_armor.toughness", toughness()).withStyle(ChatFormatting.BLUE));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.companion_gear").withStyle(ChatFormatting.GRAY));
    }
}
