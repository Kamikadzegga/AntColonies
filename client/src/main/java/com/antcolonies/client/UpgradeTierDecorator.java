package com.antcolonies.client;

import com.antcolonies.colony.upgrade.UpgradeType;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Draws an upgrade's tier on its icon (I, II or III in the top left, silver, gold and aqua), wherever the item is shown:
 * machine slots, inventories, the hotbar. The boards of the three tiers look alike at slot size; the numeral tells
 * them apart at a glance. The top left keeps clear of the stack count.
 */
public final class UpgradeTierDecorator {
    private static final String[] NUMERALS = {"I", "II", "III"};
    private static final int[] COLORS = {0xFFD8DEE6, 0xFFFFC94A, 0xFF5FF0F0};

    private UpgradeTierDecorator() {}

    static void register(RegisterItemDecorationsEvent event) {
        for (UpgradeType type : UpgradeType.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                int shown = tier;
                event.register(ModItems.upgrade(type, tier), (graphics, font, stack, x, y) -> draw(graphics, font, shown, x, y));
            }
        }
        IItemDecorator species = (graphics, font, stack, x, y) -> {
            SpeciesUpgrade upgrade = stack.get(ModDataComponents.SPECIES_UPGRADE.get());
            return upgrade != null && draw(graphics, font, upgrade.tier(), x, y);
        };
        event.register(ModItems.SPECIES_UPGRADE.get(), species);
    }

    private static boolean draw(GuiGraphicsExtractor graphics, Font font, int tier, int x, int y) {
        if (tier < 1 || tier > NUMERALS.length) {
            return false;
        }
        graphics.text(font, NUMERALS[tier - 1], x + 1, y, COLORS[tier - 1], true);
        return true;
    }
}
