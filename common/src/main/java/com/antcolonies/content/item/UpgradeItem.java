package com.antcolonies.content.item;

import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A generic upgrade of one type and tier; see {@link UpgradeType} for what each does where. */
public class UpgradeItem extends Item {
    private final UpgradeType type;
    private final int tier;

    public UpgradeItem(UpgradeType type, int tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public UpgradeType type() {
        return type;
    }

    public int tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.upgrade.tier", tier).withStyle(ChatFormatting.GOLD));
        // What it does at this tier, in numbers, for each place it works; then where it goes.
        for (Component line : com.antcolonies.colony.upgrade.UpgradeEffects.lines(type, tier)) {
            tooltip.accept(line);
        }
        List<Component> places = com.antcolonies.colony.upgrade.UpgradeEffects.placements(type).stream().map(ItemStack::getHoverName).toList();
        tooltip.accept(Component.translatable("tooltip.antcolonies.upgrade.goes_in", net.minecraft.network.chat.ComponentUtils.formatList(places,
                Component.literal(", "))).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.upgrade.adds_up").withStyle(ChatFormatting.DARK_GRAY));
    }
}
