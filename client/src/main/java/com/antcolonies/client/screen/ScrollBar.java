package com.antcolonies.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A thin scroll bar beside a list or a page: drawn each frame while there is more than fits, and dragged with the mouse. */
final class ScrollBar {
    static final int WIDTH = 4;

    private int x;
    private int y;
    private int height;
    private int thumb;
    private int max;
    private boolean shown;
    private boolean dragging;

    /**
     * Draws the bar (screen coordinates) and remembers where it is.
     *
     * @param offset how far the list is scrolled, 0 to {@code max}
     * @param max    the furthest it scrolls; 0 hides the bar
     * @param share  the part of the whole that is in view, 0 to 1: the thumb's size
     */
    void draw(GuiGraphicsExtractor graphics, int x, int y, int height, int offset, int max, double share, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.max = max;
        shown = max > 0;
        if (!shown) {
            dragging = false;
            return;
        }
        thumb = Math.clamp((int) Math.round(height * share), 8, height);
        int thumbY = y + (height - thumb) * Math.clamp(offset, 0, max) / max;
        graphics.fill(x, y, x + WIDTH, y + height, 0xFF06121A);
        graphics.fill(x, thumbY, x + WIDTH, thumbY + thumb, dragging || over(mouseX, mouseY) ? 0xFF6FD8F5 : 0xFF3FA8C8);
    }

    /** No bar this frame: the list it belongs to is not on screen. */
    void hide() {
        shown = false;
        dragging = false;
    }

    private boolean over(double mouseX, double mouseY) {
        return shown && mouseX >= x - 1 && mouseX < x + WIDTH + 1 && mouseY >= y && mouseY < y + height;
    }

    /** A click: true when it landed on the bar, which is then being dragged. */
    boolean press(double mouseX, double mouseY) {
        dragging = over(mouseX, mouseY);
        return dragging;
    }

    boolean dragging() {
        return dragging;
    }

    void release() {
        dragging = false;
    }

    /** The offset that puts the thumb's middle under the mouse. */
    int offsetAt(double mouseY) {
        double along = (mouseY - y - thumb / 2.0) / Math.max(1, height - thumb);
        return (int) Math.round(Math.clamp(along, 0, 1) * max);
    }
}
