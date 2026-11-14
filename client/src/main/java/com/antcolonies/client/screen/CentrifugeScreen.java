package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.client.FluidLook;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity.Status;
import com.antcolonies.content.menu.CentrifugeMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The Droplet Centrifuge: the droplet slot on the left, the drum between (it turns, and a bar under it fills, while a
 * droplet is spun), the tank as a glass gauge filled with the fluid itself, and beside it the fluid's name, how much there
 * is and what one droplet gives and costs. The first line says what the machine is doing or why it waits; the FE store is
 * the bar along the bottom.
 */
public class CentrifugeScreen extends AntScreen<CentrifugeMenu> {
    private static final int STATUS_Y = 18;
    private static final int DRUM_X = 44;
    private static final int DRUM_Y = 34;
    private static final int DRUM_SIZE = 26;
    private static final int RUN_X = 43;
    private static final int RUN_Y = 64;
    private static final int RUN_W = 28;
    private static final int TANK_X = 80;
    private static final int TANK_Y = 30;
    private static final int TANK_W = 18;
    private static final int TANK_H = 54;
    private static final int INFO_X = 104;
    private static final int INFO_W = 64;
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 102;
    private static final int ENERGY_W = 160;
    private static final int ENERGY_H = 8;

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, CentrifugeMenu.INVENTORY_Y + 82);
        inventoryLabelY = CentrifugeMenu.INVENTORY_Y - 11;
    }

    private Fluid tankFluid() {
        return CentrifugeBlockEntity.fluidById(menu.data(CentrifugeBlockEntity.DATA_FLUID));
    }

    private Fluid makingFluid() {
        return CentrifugeBlockEntity.fluidById(menu.data(CentrifugeBlockEntity.DATA_MAKING));
    }

    private static String name(Fluid fluid) {
        return fluid.getFluidType().getDescription().getString();
    }

    private static String amount(int millibuckets) {
        return String.format(java.util.Locale.ROOT, "%,d", millibuckets);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Status status = Status.byId(menu.data(CentrifugeBlockEntity.DATA_STATUS));
        int energy = menu.data(CentrifugeBlockEntity.DATA_ENERGY);
        int energyRoom = Math.max(1, menu.data(CentrifugeBlockEntity.DATA_ENERGY_CAPACITY));
        int progress = menu.data(CentrifugeBlockEntity.DATA_PROGRESS);
        int duration = menu.data(CentrifugeBlockEntity.DATA_DURATION);
        int held = menu.data(CentrifugeBlockEntity.DATA_AMOUNT);
        int room = Math.max(1, menu.data(CentrifugeBlockEntity.DATA_CAPACITY));
        Fluid tank = tankFluid();
        Fluid making = makingFluid();

        // What it is doing, or why it waits.
        int color = switch (status) {
            case WORKING -> GOOD;
            case NO_ENERGY, BLOCKED -> BAD;
            case TANK_FULL, PAUSED -> WARN;
            case DONE -> INFO;
            case IDLE -> DIM;
        };
        Component line = switch (status) {
            case WORKING -> Component.translatable(status.translationKey(), name(making));
            case BLOCKED -> Component.translatable(status.translationKey(), name(tank));
            default -> Component.translatable(status.translationKey());
        };
        label(graphics, fit(line.getString(), 160), 8, STATUS_Y, color);

        // The drum: three arms that turn while it works, and the run's progress under it.
        drawDrum(graphics, leftPos + DRUM_X, topPos + DRUM_Y, status == Status.WORKING, making);
        drawBar(graphics, leftPos + RUN_X, topPos + RUN_Y, RUN_W, 5, duration <= 0 ? 0 : progress / (double) duration, 0xFF3FD0A0);

        // The tank.
        drawTank(graphics, leftPos + TANK_X, topPos + TANK_Y, tank, held, room);

        // Beside it: what is in it, and what one droplet gives and takes.
        int y = TANK_Y;
        if (held > 0 && tank != Fluids.EMPTY) {
            label(graphics, fit(name(tank), INFO_W), INFO_X, y, LABEL);
        } else {
            label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.empty").getString(), INFO_W), INFO_X, y, DIM);
        }
        y += 10;
        label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.amount", amount(held)).getString(), INFO_W), INFO_X, y, LABEL);
        y += 9;
        label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.of", amount(room)).getString(), INFO_W), INFO_X, y, DIM);
        y += 12;
        if (making != Fluids.EMPTY) {
            // What one droplet gives and takes.
            label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.each").getString(), INFO_W), INFO_X, y, DIM);
            y += 9;
            label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.gives", amount(menu.data(CentrifugeBlockEntity.DATA_RUN_AMOUNT))).getString(),
                    INFO_W), INFO_X, y, INFO);
            y += 9;
            label(graphics, fit(Component.translatable("gui.antcolonies.centrifuge.takes", fe(menu.data(CentrifugeBlockEntity.DATA_RUN_ENERGY))).getString(),
                    INFO_W), INFO_X, y, INFO);
        }

        // The FE store.
        label(graphics, Component.translatable("gui.antcolonies.centrifuge.energy"), ENERGY_X, ENERGY_Y - 10, DIM);
        String stored = fe(energy) + " / " + fe(energyRoom).replace(" FE", "");
        label(graphics, stored, ENERGY_X + ENERGY_W - font.width(stored), ENERGY_Y - 10, status == Status.NO_ENERGY ? BAD : LABEL);
        drawBar(graphics, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy / (double) energyRoom, 0xFFE0A030);
    }

    /**
     * The drum seen from above: a ring, a hub and three arms with a tube at each end, in the colour of the fluid being
     * made. The arms turn while the Centrifuge works and stand still otherwise.
     */
    private static void drawDrum(GuiGraphicsExtractor graphics, int x, int y, boolean spinning, Fluid making) {
        int cx = x + DRUM_SIZE / 2;
        int cy = y + DRUM_SIZE / 2;
        int rim = spinning ? RIM : 0xFF22323C;
        // The ring: an octagon of short strokes.
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            int px = cx + (int) Math.round(Math.cos(a) * 12);
            int py = cy + (int) Math.round(Math.sin(a) * 12);
            graphics.fill(px, py, px + 1, py + 1, rim);
        }
        double turn = spinning ? (System.currentTimeMillis() % 900) / 900.0 * Math.PI * 2 : 0.35;
        int tube = making == Fluids.EMPTY ? 0xFF3A4E5C : FluidLook.accent(making);
        for (int arm = 0; arm < 3; arm++) {
            double a = turn + arm * Math.PI * 2 / 3;
            for (int r = 3; r <= 7; r++) {
                int px = cx + (int) Math.round(Math.cos(a) * r);
                int py = cy + (int) Math.round(Math.sin(a) * r);
                graphics.fill(px, py, px + 1, py + 1, 0xFF8FA7B5);
            }
            int tx = cx + (int) Math.round(Math.cos(a) * 9);
            int ty = cy + (int) Math.round(Math.sin(a) * 9);
            graphics.fill(tx - 1, ty - 1, tx + 2, ty + 2, tube);
            graphics.fill(tx - 1, ty - 1, tx, ty, 0x80FFFFFF);
        }
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF5E7484);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFB8CCD8);
    }

    /** The tank: the fluid's own texture and tint filling it from the bottom, with a mark at every quarter. */
    private static void drawTank(GuiGraphicsExtractor graphics, int x, int y, Fluid fluid, int amount, int capacity) {
        graphics.fill(x, y, x + TANK_W, y + TANK_H, TRACK);
        int filled = fluid == Fluids.EMPTY || amount <= 0 ? 0 : Math.max(1, Mth.ceil((double) amount * (TANK_H - 2) / capacity));
        filled = Math.min(filled, TANK_H - 2);
        if (filled > 0) {
            TextureAtlasSprite sprite = FluidLook.sprite(fluid);
            int color = FluidLook.color(fluid);
            int bottom = y + TANK_H - 1;
            int top = bottom - filled;
            // 16x16 tiles from the bottom up; the scissor cuts off what is above the fluid's level.
            graphics.enableScissor(x + 1, top, x + TANK_W - 1, bottom);
            for (int tileY = bottom - 16; tileY > top - 16; tileY -= 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + 1, tileY, 16, 16, color);
            }
            graphics.disableScissor();
            graphics.fill(x + 1, top, x + TANK_W - 1, top + 1, 0x50FFFFFF);
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int markY = y + TANK_H - 1 - (TANK_H - 2) * quarter / 4;
            graphics.fill(x + 1, markY, x + 5, markY + 1, 0xA0D8F2FF);
        }
        graphics.fill(x + 2, y + 2, x + 3, y + TANK_H - 2, 0x30FFFFFF);
        graphics.outline(x - 1, y - 1, TANK_W + 2, TANK_H + 2, RIM);
    }

    /** A faint droplet in the empty slot. */
    @Override
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, Slot slot, int x, int y) {
        super.drawSlotBackground(graphics, slot, x, y);
        if (slot.index == 0 && !slot.hasItem()) {
            int c = 0xFF2B4A5A;
            int cx = x + 9;
            graphics.fill(cx - 1, y + 3, cx + 1, y + 5, c);
            graphics.fill(cx - 2, y + 5, cx + 2, y + 7, c);
            graphics.fill(cx - 3, y + 7, cx + 3, y + 9, c);
            graphics.fill(cx - 4, y + 9, cx + 4, y + 13, c);
            graphics.fill(cx - 3, y + 13, cx + 3, y + 14, c);
            graphics.fill(cx - 2, y + 14, cx + 2, y + 15, c);
        }
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index == 0 && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.centrifuge.slot"),
                    Component.translatable("gui.antcolonies.centrifuge.slot.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (over(mouseX, mouseY, TANK_X, TANK_Y, TANK_W, TANK_H)) {
            int held = menu.data(CentrifugeBlockEntity.DATA_AMOUNT);
            Fluid tank = tankFluid();
            List<Component> lines = new ArrayList<>();
            lines.add(held > 0 && tank != Fluids.EMPTY ? tank.getFluidType().getDescription() : Component.translatable("gui.antcolonies.centrifuge.empty"));
            lines.add(Component.literal(String.format(java.util.Locale.ROOT, "%,d / %,d mB", held, menu.data(CentrifugeBlockEntity.DATA_CAPACITY)))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.antcolonies.centrifuge.tank.hint").withStyle(ChatFormatting.DARK_AQUA));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (over(mouseX, mouseY, DRUM_X, DRUM_Y, DRUM_SIZE, DRUM_SIZE + 10)) {
            int duration = menu.data(CentrifugeBlockEntity.DATA_DURATION);
            if (duration > 0) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.centrifuge.run",
                        String.format(java.util.Locale.ROOT, "%.1f", menu.data(CentrifugeBlockEntity.DATA_PROGRESS) / 20.0),
                        String.format(java.util.Locale.ROOT, "%.1f", duration / 20.0))), mouseX, mouseY);
            }
            return;
        }
        if (over(mouseX, mouseY, ENERGY_X, ENERGY_Y - 10, ENERGY_W, ENERGY_H + 10)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(String.format(java.util.Locale.ROOT, "%,d / %,d FE", menu.data(CentrifugeBlockEntity.DATA_ENERGY),
                            menu.data(CentrifugeBlockEntity.DATA_ENERGY_CAPACITY))),
                    Component.translatable("gui.antcolonies.centrifuge.energy.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
