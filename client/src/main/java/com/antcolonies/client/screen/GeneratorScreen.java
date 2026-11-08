package com.antcolonies.client.screen;

import java.util.List;

import com.antcolonies.content.blockentity.GeneratorBlockEntity;
import com.antcolonies.content.menu.GeneratorMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Generator: the fuel slot under a flame that burns down, and on the right the FE buffer as a glass gauge with what
 * the generator is doing right now.
 */
public class GeneratorScreen extends AntScreen<GeneratorMenu> {
    private static final int GAUGE_X = 76;
    private static final int GAUGE_Y = 22;
    private static final int GAUGE_W = 92;
    private static final int GAUGE_H = 12;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int energy = menu.data(GeneratorBlockEntity.DATA_ENERGY);
        int burn = menu.data(GeneratorBlockEntity.DATA_BURN);
        int total = Math.max(1, menu.data(GeneratorBlockEntity.DATA_BURN_TOTAL));
        int output = menu.data(GeneratorBlockEntity.DATA_OUTPUT);
        // The flame over the fuel slot, burning down as the current item is used up.
        drawFlame(graphics, leftPos + GeneratorMenu.FUEL_X + 1, topPos + GeneratorMenu.FUEL_Y - 18, burn > 0 ? burn / (double) total : 0);
        // The buffer.
        drawWell(graphics, leftPos + GAUGE_X - 4, topPos + GAUGE_Y - 4, GAUGE_W + 8, 52);
        drawBar(graphics, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H, energy / (double) GeneratorBlockEntity.CAPACITY, 0xFFE0A030);
        label(graphics, fe(energy) + " / " + fe(GeneratorBlockEntity.CAPACITY).replace(" FE", ""), GAUGE_X, GAUGE_Y + 16, LABEL);
        Component state;
        int color;
        if (output > 0) {
            state = Component.translatable("gui.antcolonies.generator.making", output);
            color = GOOD;
        } else if (energy >= GeneratorBlockEntity.CAPACITY - 1000) {
            state = Component.translatable("gui.antcolonies.generator.full");
            color = WARN;
        } else {
            state = Component.translatable("gui.antcolonies.generator.idle");
            color = DIM;
        }
        label(graphics, fit(state.getString(), GAUGE_W), GAUGE_X, GAUGE_Y + 27, color);
    }

    /** A flame drawn in pixels; only the part above {@code 1 - fraction} of its height is lit. */
    private static void drawFlame(GuiGraphicsExtractor graphics, int x, int y, double fraction) {
        String[] rows = {
                "......#.......",
                ".....##.......",
                ".....###......",
                "....####...#..",
                "....#####.##..",
                "...#########..",
                "...##########.",
                "..###########.",
                "..####++#####.",
                ".####++++####.",
                ".###++++++###.",
                ".###++++++###.",
                "..##+++++###..",
                "...#######....",
        };
        int litFrom = (int) Math.round(rows.length * (1 - fraction));
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                char c = rows[row].charAt(col);
                if (c == '.') {
                    continue;
                }
                boolean lit = fraction > 0 && row >= litFrom;
                int color = !lit ? 0xFF22323C : c == '+' ? 0xFFFFE070 : 0xFFE07020;
                graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index == 0 && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.generator.fuel"),
                    Component.translatable("gui.antcolonies.generator.fuel.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (over(mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(String.format(java.util.Locale.ROOT, "%,d / %,d FE", menu.data(GeneratorBlockEntity.DATA_ENERGY),
                            GeneratorBlockEntity.CAPACITY)),
                    Component.translatable("gui.antcolonies.generator.push").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.antcolonies.generator.hint").withStyle(ChatFormatting.DARK_AQUA)), mouseX, mouseY);
        }
    }
}
