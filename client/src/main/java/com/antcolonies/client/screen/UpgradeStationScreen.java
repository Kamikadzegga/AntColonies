package com.antcolonies.client.screen;

import java.util.List;

import com.antcolonies.content.menu.UpgradeStationMenu;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Upgrade Station: choose extraction, processing or efficiency; base + signature material = species upgrade. */
public class UpgradeStationScreen extends AntScreen<UpgradeStationMenu> {
    public UpgradeStationScreen(UpgradeStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SpeciesUpgradeKind selected = menu.selected();
        for (SpeciesUpgradeKind kind : SpeciesUpgradeKind.values()) {
            toggle(8 + kind.ordinal() * 54, 18, 52, 14, Component.translatable("gui.antcolonies.station." + kind.getSerializedName()).getString(),
                    kind.ordinal(), List.of(Component.translatable("gui.antcolonies.station." + kind.getSerializedName() + ".hint")), kind == selected);
        }
        // Plus and arrow between the slots.
        graphics.centeredText(font, "+", leftPos + 59, topPos + 51, LABEL);
        graphics.fill(leftPos + 100, topPos + 54, leftPos + 122, topPos + 57, 0xFF3FB8E0);
        graphics.fill(leftPos + 120, topPos + 51, leftPos + 123, topPos + 60, 0xFF3FB8E0);
        wrapped(graphics, Component.translatable("gui.antcolonies.station.hint"), 8, 67, 160, 9, DIM);
    }
}
