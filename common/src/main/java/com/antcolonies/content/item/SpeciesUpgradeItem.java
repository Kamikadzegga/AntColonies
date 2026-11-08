package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A species-specific upgrade ("Diamond Extraction II"): only works in Scavenging Sites of its species' colonies. */
public class SpeciesUpgradeItem extends Item {
    private static final String[] NUMERALS = {"I", "II", "III"};

    public SpeciesUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpeciesUpgrade upgrade = stack.get(ModDataComponents.SPECIES_UPGRADE.get());
        if (upgrade == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.antcolonies.species_upgrade.named", Component.translatable(upgrade.translationKey()),
                NUMERALS[Math.clamp(upgrade.tier() - 1, 0, 2)]);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        SpeciesUpgrade upgrade = stack.get(ModDataComponents.SPECIES_UPGRADE.get());
        if (upgrade == null) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.species_upgrade.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        int percent = (int) Math.round(AntConfig.get(AntConfig.SPECIES_UPGRADE_BONUS) * upgrade.tier() * 100);
        tooltip.accept(Component.translatable("tooltip.antcolonies.species_upgrade.for", Tooltips.species(upgrade.species())).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.antcolonies.species_upgrade." + upgrade.kind().getSerializedName(), percent)
                .withStyle(ChatFormatting.GRAY));
    }
}
