package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import com.antcolonies.AntColonies;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * The mod's GUIs share this look: a riveted gunmetal console with dark glass panels and lit slots (the sprites in
 * textures/gui/sprites/lab, painted by TexGen), light text, and console buttons that send menu button clicks (the
 * server applies them and syncs back). A selected or primary button glows.
 */
public abstract class AntScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final int LABEL = 0xFFD8F2FF;
    protected static final int DIM = 0xFF7896A8;
    protected static final int FAINT = 0xFF4E6676;
    protected static final int GOOD = 0xFF6CF08A;
    protected static final int BAD = 0xFFFF6E6E;
    protected static final int WARN = 0xFFFFC85A;
    protected static final int INFO = 0xFF4FE3FF;
    protected static final int ACCENT = 0xFFE07AFF;
    protected static final int TRACK = 0xFF06121A;
    protected static final int RIM = 0xFF2B6E86;

    /** A button drawn this frame: where, what it sends, what it says on hover. */
    protected record Button(int x, int y, int w, int h, String label, int id, @Nullable List<Component> tooltip, boolean enabled, boolean selected) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    protected final List<Button> buttons = new ArrayList<>();

    /** Text drawn cut short this frame: where (screen coordinates) and the whole text, shown on hover. */
    private record Cut(int x, int y, int w, int h, String text) {
    }

    private final List<Cut> cuts = new ArrayList<>();

    protected AntScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        buttons.clear();
        cuts.clear();
        drawFrame(graphics);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                drawSlotBackground(graphics, slot, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        extractPage(graphics, mouseX, mouseY);
        for (Button button : buttons) {
            drawButton(graphics, button, button.contains(mouseX, mouseY));
        }
    }

    /** The panel behind everything; screens with their own look draw their own. */
    protected void drawFrame(GuiGraphicsExtractor graphics) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    /** The well behind a slot (x, y: its top-left corner, one pixel outside the item). */
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, Slot slot, int x, int y) {
        drawSlot(graphics, x, y);
    }

    /** A faint glyph in an empty slot for what goes in: queen (a tube), feed (a crumb), output (an arrow out), upgrade (a chevron). */
    protected static void drawGlyph(GuiGraphicsExtractor graphics, String kind, int x, int y) {
        int c = 0xFF2B4A5A;
        int cx = x + 9;
        int cy = y + 9;
        switch (kind) {
            case "queen" -> {
                graphics.fill(cx - 3, cy - 6, cx + 3, cy - 5, c);
                graphics.fill(cx - 2, cy - 5, cx - 1, cy + 5, c);
                graphics.fill(cx + 1, cy - 5, cx + 2, cy + 5, c);
                graphics.fill(cx - 1, cy + 5, cx + 1, cy + 6, c);
                graphics.fill(cx - 1, cy + 1, cx + 1, cy + 5, c);
            }
            case "food" -> {
                // A heap of food.
                graphics.fill(cx - 4, cy - 1, cx + 4, cy + 3, c);
                graphics.fill(cx - 3, cy - 3, cx + 2, cy - 1, c);
                graphics.fill(cx - 2, cy + 3, cx + 3, cy + 4, c);
            }
            case "feed" -> {
                // A tube lying on its side with three ants in it.
                graphics.fill(cx - 6, cy - 3, cx + 5, cy - 2, c);
                graphics.fill(cx - 6, cy + 2, cx + 5, cy + 3, c);
                graphics.fill(cx + 5, cy - 2, cx + 6, cy + 2, c);
                graphics.fill(cx - 7, cy - 4, cx - 6, cy + 4, c);
                for (int i = 0; i < 3; i++) {
                    graphics.fill(cx - 4 + i * 3, cy - 1, cx - 2 + i * 3, cy + 1, c);
                }
            }
            case "output" -> {
                graphics.fill(cx - 5, cy - 1, cx + 2, cy + 1, c);
                graphics.fill(cx + 1, cy - 3, cx + 3, cy + 3, c);
                graphics.fill(cx + 3, cy - 2, cx + 4, cy + 2, c);
                graphics.fill(cx + 4, cy - 1, cx + 5, cy + 1, c);
            }
            default -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(cx - 4 + i, cy + 1 - i, cx - 2 + i, cy + 3 - i, c);
                    graphics.fill(cx + 2 - i, cy + 1 - i, cx + 4 - i, cy + 3 - i, c);
                }
            }
        }
    }

    /** The screen's own contents, in screen coordinates (leftPos and topPos are the panel's corner). */
    protected abstract void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    /** Title and inventory label in the console's light text (vanilla's dark grey vanishes on the dark panels). */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    /** Text wrapped to {@code width}, one line per {@code lineHeight}; returns the y under the last line. */
    protected int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int lineHeight, int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, width)) {
            graphics.text(font, line, leftPos + x, topPos + y, color, false);
            y += lineHeight;
        }
        return y;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (Button button : buttons) {
            if (button.tooltip() != null && button.contains(mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(font, button.tooltip(), mouseX, mouseY);
                return;
            }
        }
        // A text cut short shows whole under the mouse (an item in a slot under it keeps its own tooltip).
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            for (Cut cut : cuts) {
                if (mouseX >= cut.x() && mouseY >= cut.y() && mouseX < cut.x() + cut.w() && mouseY < cut.y() + cut.h()) {
                    List<Component> lines = new ArrayList<>();
                    font.getSplitter().splitLines(cut.text(), 220, net.minecraft.network.chat.Style.EMPTY)
                            .forEach(part -> lines.add(Component.literal(part.getString())));
                    graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                    return;
                }
            }
        }
        extractPageTooltip(graphics, mouseX, mouseY);
    }

    /** Registers a hover with {@code text} over an area (panel coordinates), for text shown shortened some other way. */
    protected void hoverText(int x, int y, int w, int h, String text) {
        cuts.add(new Cut(leftPos + x, topPos + y, w, h, text));
    }

    /**
     * Call after drawing {@code drawn} at screen coordinates: if it is the text {@link #fit} cut last, hovering it shows it
     * whole. {@link #label} and the screens' own text helpers call this, so any {@code label(fit(...))} gets its hover.
     */
    protected void noteDrawn(String drawn, int screenX, int screenY) {
        if (lastCutShown != null && lastCutShown.equals(drawn)) {
            cuts.add(new Cut(screenX, screenY - 1, font.width(drawn), 10, lastCutFull));
        }
        lastCutShown = null;
        lastCutFull = null;
    }

    /** The last text {@link #fit} cut, as drawn and whole, until it is drawn. */
    private @Nullable String lastCutShown;
    private @Nullable String lastCutFull;

    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Button button : buttons) {
                if (button.contains(event.x(), event.y())) {
                    if (button.enabled()) {
                        onButton(button.id());
                    }
                    return true;
                }
            }
            if (clickPage(event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Clicks on the page that aren't buttons (tabs, lists); true if used. */
    protected boolean clickPage(double mouseX, double mouseY) {
        return false;
    }

    /** By default a button is a menu button: the server gets its id. */
    protected void onButton(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        clickSound();
    }

    protected void clickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    protected void button(int x, int y, int w, int h, String label, int id, @Nullable List<Component> tooltip, boolean enabled) {
        buttons.add(new Button(leftPos + x, topPos + y, w, h, label, id, tooltip, enabled, false));
    }

    protected void toggle(int x, int y, int w, int h, String label, int id, @Nullable List<Component> tooltip, boolean selected) {
        buttons.add(new Button(leftPos + x, topPos + y, w, h, label, id, tooltip, true, selected));
    }

    // --- Drawing ---

    protected static Identifier sprite(String name) {
        return AntColonies.id("lab/" + name);
    }

    protected static void sprite(GuiGraphicsExtractor graphics, String name, int x, int y, int w, int h) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, w, h);
    }

    /** A console button; a selected (tab, toggle) or primary button glows. */
    protected void drawButton(GuiGraphicsExtractor graphics, Button button, boolean hovered) {
        boolean lit = button.selected();
        String name = !button.enabled() ? "button_disabled" : lit ? (hovered ? "button_primary_hover" : "button_primary")
                : hovered ? "button_hover" : "button";
        sprite(graphics, name, button.x(), button.y(), button.w(), button.h());
        int color = !button.enabled() ? 0xFF5E7484 : lit || hovered ? 0xFFFFFFFF : LABEL;
        // Flat text (a shadow smears on the lit buttons), centered on its glyphs: font widths count a trailing gap
        // column and capitals are 7 pixels tall. A label too wide for its button is cut rather than spilling over.
        String label = button.label();
        int room = button.w() - 4;
        if (font.width(label) - 1 > room) {
            label = font.plainSubstrByWidth(label, room - font.width(".")) + ".";
            if (button.tooltip() == null) {
                cuts.add(new Cut(button.x(), button.y(), button.w(), button.h(), button.label()));
            }
        }
        int textW = font.width(label) - 1;
        graphics.text(font, label, button.x() + (button.w() - textW + 1) / 2, button.y() + (button.h() - 7) / 2, color, false);
    }

    /** The width of a button that fits {@code label} with a little air either side. */
    protected int buttonWidth(String label) {
        return font.width(label) + 7;
    }

    /** The console's riveted frame. */
    protected static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        sprite(graphics, "frame", x, y, width, height);
    }

    protected static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        sprite(graphics, "slot", x, y, 18, 18);
    }

    /** A dark glass panel for text and lists. */
    protected static void drawWell(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        sprite(graphics, "glass", x, y, w, h);
    }

    /** A glass gauge filled to {@code fraction} in {@code color}. */
    protected static void drawBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double fraction, int color) {
        graphics.fill(x, y, x + w, y + h, TRACK);
        int filled = (int) Math.round((w - 2) * Math.clamp(fraction, 0, 1));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x60FFFFFF);
        }
        graphics.outline(x - 1, y - 1, w + 2, h + 2, RIM);
    }

    protected void label(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, leftPos + x, topPos + y, color, false);
    }

    protected void label(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, leftPos + x, topPos + y, color, false);
        noteDrawn(text, leftPos + x, topPos + y);
    }

    /** Text cut to a width, with an ellipsis; drawn with {@link #label} (or noted with {@link #noteDrawn}) it shows whole on hover. */
    protected String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "...") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        lastCutShown = cut + "...";
        lastCutFull = text;
        return lastCutShown;
    }

    protected boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseY >= topPos + y && mouseX < leftPos + x + w && mouseY < topPos + y + h;
    }

    protected static String number(double value) {
        if (value >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000);
        }
        if (value >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000);
        }
        return String.valueOf((long) value);
    }

    /** An amount of Forge Energy, short: "850 FE", "62.5k FE", "1M FE". */
    public static String fe(long amount) {
        String text = amount >= 1_000_000 ? String.format(java.util.Locale.ROOT, "%.2fM", amount / 1_000_000.0)
                : amount >= 10_000 ? String.format(java.util.Locale.ROOT, "%.1fk", amount / 1000.0)
                : String.format(java.util.Locale.ROOT, "%,d", amount);
        // Drop trailing zeros of the fraction: "100.0k" -> "100k", "1.50M" -> "1.5M".
        text = text.replaceAll("(\\.\\d*?)0+([kM])$", "$1$2").replace(".k", "k").replace(".M", "M");
        return text + " FE";
    }

    protected static String rate(double perMinute) {
        return perMinute >= 100 ? String.format(java.util.Locale.ROOT, "%.0f", perMinute)
                : String.format(java.util.Locale.ROOT, perMinute >= 10 ? "%.1f" : perMinute >= 0.1 || perMinute == 0 ? "%.2f" : "%.3f", perMinute);
    }
}
