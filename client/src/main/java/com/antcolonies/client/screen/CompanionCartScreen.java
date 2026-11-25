package com.antcolonies.client.screen;

import java.util.List;

import com.antcolonies.companion.menu.CompanionCartMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** What a Cart Ant carries, with a way to the ant's own screen. */
public class CompanionCartScreen extends AntScreen<CompanionCartMenu> {
    public CompanionCartScreen(CompanionCartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, CompanionCartMenu.INVENTORY_Y + 82);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String label = Component.translatable("gui.antcolonies.companion.ant").getString();
        int width = buttonWidth(label);
        buttons.add(new Button(leftPos + imageWidth - 8 - width, topPos + 5, width, 14, label, CompanionCartMenu.BUTTON_ANT,
                List.of(Component.translatable("gui.antcolonies.companion.ant.hint")), true, false));
    }
}
