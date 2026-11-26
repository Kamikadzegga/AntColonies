package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.network.ConnectorNote;
import com.antcolonies.content.blockentity.ConnectorSettings;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.menu.RenameConnectorPayload;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** A Colony Connector: its name and channel, what it does with food and with items, how it sends, and its filter. */
public class ConnectorScreen extends AntScreen<StructureMenu> {
    private static final int BUTTON_VIEW = -1;
    private static final int PANEL_Y = 34;
    private static final int PANEL_W = 116;
    private static final int PANEL_H = 73;
    private static final int FOOD_X = 8;
    private static final int ITEMS_X = 128;
    /** The row under the panels: speed and order, which count for food and items alike. */
    private static final int SENDING_Y = 110;
    /** A stepper: its minus button, the value between, its plus button (from the row's left edge). */
    private static final int LESS_X = 44;
    private static final int VALUE_X = 58;
    private static final int VALUE_W = 36;
    private static final int MORE_X = 96;
    private static final String G = "gui.antcolonies.connector.";

    private EditBox name;
    /** The name last sent, so closing does not send it again. */
    private @Nullable String sent;
    /** The channel swatches drawn this frame: x, y, colour id, selected. */
    private final List<int[]> channelSwatches = new ArrayList<>();

    public ConnectorScreen(StructureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StructureMenu.CONNECTOR_WIDTH, StructureMenu.inventoryY(menu.structure().kind()) + 82);
        inventoryLabelX = StructureMenu.CONNECTOR_SLOT_X;
        inventoryLabelY = StructureMenu.inventoryY(menu.structure().kind()) - 11;
        titleLabelY = 6;
    }

    private static boolean detailed() {
        return AntConfig.client(AntConfig.DETAILED_COLONY_VIEW);
    }

    // --- The name box ---

    @Override
    protected void init() {
        super.init();
        String old = name == null ? savedName() : name.getValue();
        name = new EditBox(font, leftPos + 38, topPos + 19, 84, 12, Component.translatable(G + "name"));
        name.setMaxLength(ConnectorSettings.MAX_NAME);
        name.setValue(old);
        Colony colony = colony();
        name.setHint(colony == null ? Component.translatable(G + "name") : Tooltips.species(colony.species()));
        addRenderableWidget(name);
    }

    private String savedName() {
        ConnectorSettings settings = menu.structure().connector();
        return settings == null ? "" : settings.name;
    }

    private void sendName() {
        String typed = ConnectorSettings.clean(name.getValue());
        if (!typed.equals(savedName()) && !typed.equals(sent)) {
            sent = typed;
            ClientPacketDistributor.sendToServer(new RenameConnectorPayload(typed));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name.isFocused() && !event.isEscape()) {
            if (event.isConfirmation()) {
                sendName();
                setFocused(null);
                return true;
            }
            // Letters that are keys too (the inventory key) belong to the name.
            name.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (name.isFocused() && !name.isMouseOver(event.x(), event.y())) {
            sendName();
            setFocused(null);
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The name goes out before the close: after it the server no longer has this connector's menu open. */
    @Override
    public void onClose() {
        sendName();
        super.onClose();
    }

    /** Another screen taking this one's place (a recipe viewer) leaves the menu open: the name still goes. */
    @Override
    public void removed() {
        sendName();
        super.removed();
    }

    // --- The page ---

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        channelSwatches.clear();
        String view = Component.translatable(detailed() ? "gui.antcolonies.view.detailed" : "gui.antcolonies.view.visual").getString();
        toggle(imageWidth - 48, 4, 40, 13, view, BUTTON_VIEW, List.of(Component.translatable("gui.antcolonies.view"),
                Component.translatable(detailed() ? G + "view.detailed.hint" : G + "view.visual.hint").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.antcolonies.view.click").withStyle(ChatFormatting.DARK_GRAY)), detailed());
        // Beside the title: whether it has a colony, and how many others it reaches.
        boolean linked = menu.data(StructureBlockEntity.DATA_LINKED) != 0;
        int members = menu.data(StructureBlockEntity.DATA_MEMBERS);
        String reach = Component.translatable(!linked ? (menu.data(StructureBlockEntity.DATA_CONTESTED) != 0 ? "gui.antcolonies.contested"
                : "gui.antcolonies.not_linked") : G + "colonies", members).getString();
        int titleEnd = 8 + font.width(title) + 6;
        String shown = fit(reach, imageWidth - 52 - titleEnd);
        label(graphics, shown, imageWidth - 52 - font.width(shown), 6, !linked ? BAD : members > 0 ? GOOD : DIM);

        label(graphics, Component.translatable(G + "name"), 8, 21, LABEL);
        int channel = menu.data(StructureBlockEntity.DATA_CHANNEL);
        for (int c = 0; c < 16; c++) {
            int bx = 130 + c * 7;
            button(bx, 19, 7, 12, "", StructureMenu.CHANNEL + c, List.of(Component.translatable("gui.antcolonies.network.channel",
                    Component.translatable("color.minecraft." + DyeColor.byId(c).getName())),
                    Component.translatable(G + "channel.hint", AntConfig.get(AntConfig.CONNECTOR_RANGE)).withStyle(ChatFormatting.GRAY)), true);
            channelSwatches.add(new int[] {bx, 19, c, c == channel ? 1 : 0});
        }
        foodPanel(graphics);
        itemPanel(graphics);
        sendingRow(graphics);
        // Over the filter: what its slots mean now.
        boolean sendsItems = menu.data(StructureBlockEntity.DATA_ITEM_MODE) == ConnectorSettings.SEND;
        boolean allBut = menu.data(StructureBlockEntity.DATA_FILTER_MODE) == ConnectorSettings.ALL_BUT;
        String filter = Component.translatable(G + (!sendsItems ? "filter.unused" : allBut ? "filter.all_but.label" : "filter.only.label")).getString();
        label(graphics, fit(filter, 162), StructureMenu.CONNECTOR_SLOT_X, StructureMenu.FILTER_Y - 11, sendsItems ? LABEL : FAINT);
    }

    private void foodPanel(GuiGraphicsExtractor graphics) {
        int x = FOOD_X + 4;
        int y = PANEL_Y;
        drawWell(graphics, leftPos + FOOD_X, topPos + y, PANEL_W, PANEL_H);
        int mode = menu.data(StructureBlockEntity.DATA_FOOD_MODE);
        ConnectorNote note = ConnectorNote.byId(menu.data(StructureBlockEntity.DATA_FOOD_NOTE));
        header(graphics, x, y, G + "food", menu.data(StructureBlockEntity.DATA_FOOD_IN) / 10.0, menu.data(StructureBlockEntity.DATA_FOOD_OUT) / 10.0,
                note, 0xFFE0B040);
        modes(x, y + 13, StructureMenu.FOOD_MODE, G + "food.", new String[] {"off", "supply", "request"}, mode);
        if (mode == ConnectorSettings.OFF) {
            wrapped(graphics, Component.translatable(G + "food.off.hint"), x, y + 28, PANEL_W - 8, 9, DIM);
            return;
        }
        boolean sends = mode == ConnectorSettings.SUPPLY;
        int percent = menu.data(sends ? StructureBlockEntity.DATA_RESERVE : StructureBlockEntity.DATA_TARGET);
        stepper(graphics, x, y + 26, G + (sends ? "keep" : "until"), sends ? StructureMenu.RESERVE_LESS : StructureMenu.TARGET_LESS,
                sends ? StructureMenu.RESERVE_MORE : StructureMenu.TARGET_MORE, percent > 0, percent < 100,
                Component.translatable(G + (sends ? "keep.hint" : "until.hint"), percent));
        Colony colony = colony();
        double has = colony == null ? 0 : colony.food() / foodRoom(colony);
        if (detailed()) {
            value(graphics, x, y + 26, percent + "%");
        } else {
            level(graphics, x, y + 26, has, percent / 100.0);
        }
        if (sends) {
            int size = menu.data(StructureBlockEntity.DATA_FOOD_PACKAGE);
            packageRow(graphics, x, y + 39, size, ConnectorSettings.FOOD_PACKAGES, AntConfig.get(AntConfig.CONNECTOR_FOOD_PACKAGE),
                    StructureMenu.FOOD_PACKAGE_LESS, StructureMenu.FOOD_PACKAGE_MORE, G + "package.food.hint");
        } else if (detailed() && colony != null) {
            label(graphics, fit(Component.translatable(G + "has", Math.round(has * 100), number(colony.food())).getString(), PANEL_W - 8),
                    x, y + 41, DIM);
        }
        note(graphics, note, x, y + 53);
    }

    private void itemPanel(GuiGraphicsExtractor graphics) {
        int x = ITEMS_X + 4;
        int y = PANEL_Y;
        drawWell(graphics, leftPos + ITEMS_X, topPos + y, PANEL_W, PANEL_H);
        int mode = menu.data(StructureBlockEntity.DATA_ITEM_MODE);
        ConnectorNote note = ConnectorNote.byId(menu.data(StructureBlockEntity.DATA_ITEM_NOTE));
        header(graphics, x, y, G + "items", menu.data(StructureBlockEntity.DATA_ITEMS_IN) / 10.0,
                menu.data(StructureBlockEntity.DATA_ITEMS_OUT) / 10.0, note, 0xFF4FE3FF);
        modes(x, y + 13, StructureMenu.ITEM_MODE, G + "items.", new String[] {"off", "send", "receive"}, mode);
        if (mode == ConnectorSettings.OFF) {
            wrapped(graphics, Component.translatable(G + "items.off.hint"), x, y + 28, PANEL_W - 8, 9, DIM);
            return;
        }
        if (mode == ConnectorSettings.SEND) {
            int size = menu.data(StructureBlockEntity.DATA_ITEM_PACKAGE);
            packageRow(graphics, x, y + 26, size, ConnectorSettings.ITEM_PACKAGES, AntConfig.get(AntConfig.CONNECTOR_ITEM_PACKAGE),
                    StructureMenu.ITEM_PACKAGE_LESS, StructureMenu.ITEM_PACKAGE_MORE, G + "package.items.hint");
            boolean allBut = menu.data(StructureBlockEntity.DATA_FILTER_MODE) == ConnectorSettings.ALL_BUT;
            String key = G + (allBut ? "filter.all_but" : "filter.only");
            button(x, y + 39, PANEL_W - 8, 12, Component.translatable(key).getString(), StructureMenu.FILTER_MODE,
                    List.of(Component.translatable(key + ".hint"),
                            Component.translatable("gui.antcolonies.view.click").withStyle(ChatFormatting.DARK_GRAY)), true);
        } else {
            wrapped(graphics, Component.translatable(G + "items.receive.where"), x, y + 28, PANEL_W - 8, 9, DIM);
        }
        note(graphics, note, x, y + 53);
    }

    /** Speed and order: how this connector sends, food and items alike. */
    private void sendingRow(GuiGraphicsExtractor graphics) {
        boolean sends = menu.data(StructureBlockEntity.DATA_FOOD_MODE) == ConnectorSettings.SUPPLY
                || menu.data(StructureBlockEntity.DATA_ITEM_MODE) == ConnectorSettings.SEND;
        int speed = Math.clamp(menu.data(StructureBlockEntity.DATA_INTERVAL), 0, ConnectorSettings.INTERVALS.length - 1);
        int seconds = ConnectorSettings.INTERVALS[speed];
        int x = FOOD_X + 4;
        label(graphics, Component.translatable(G + "speed"), x, SENDING_Y + 2, sends ? LABEL : FAINT);
        List<Component> hint = List.of(Component.translatable(G + "speed.hint", seconds));
        button(x + LESS_X, SENDING_Y, 12, 12, "-", StructureMenu.SLOWER, hint, sends && speed > 0);
        button(x + MORE_X, SENDING_Y, 12, 12, "+", StructureMenu.FASTER, hint, sends && speed < ConnectorSettings.INTERVALS.length - 1);
        if (detailed()) {
            value(graphics, x, SENDING_Y, Component.translatable(G + "speed.value", seconds).getString(), sends ? LABEL : FAINT);
        } else {
            pips(graphics, x, SENDING_Y, speed + 1, ConnectorSettings.INTERVALS.length, sends ? 0xFF6CF08A : 0xFF2B3B48);
        }
        int order = Math.clamp(menu.data(StructureBlockEntity.DATA_ORDER), 0, 2);
        int ox = ITEMS_X + 4;
        label(graphics, Component.translatable(G + "order"), ox, SENDING_Y + 2, sends ? LABEL : FAINT);
        button(ox + LESS_X, SENDING_Y, PANEL_W - 8 - LESS_X, 12, Component.translatable(G + "order." + order).getString(),
                StructureMenu.CYCLE_ORDER, List.of(Component.translatable(G + "order." + order + ".hint"),
                        Component.translatable("gui.antcolonies.view.click").withStyle(ChatFormatting.DARK_GRAY)), sends);
    }

    // --- Parts ---

    /** A panel's title; right of it what moved a minute (Detailed) or dots running the way it moves (Visual). */
    private void header(GuiGraphicsExtractor graphics, int x, int y, String key, double in, double out, ConnectorNote note, int color) {
        label(graphics, Component.translatable(key), x, y + 3, LABEL);
        int right = x + PANEL_W - 8;
        if (detailed()) {
            String flow = Component.translatable(G + "flow.short", rate(in), rate(out)).getString();
            String shown = fit(flow, PANEL_W - 14 - font.width(Component.translatable(key)));
            label(graphics, shown, right - font.width(shown), y + 3, in > 0.01 || out > 0.01 ? GOOD : FAINT);
        } else if (note == ConnectorNote.SENDING || note == ConnectorNote.RECEIVING) {
            long time = System.currentTimeMillis();
            for (int dot = 0; dot < 3; dot++) {
                double along = ((time / 25.0 + dot * 33) % 100) / 100.0;
                int px = right - 44 + (int) Math.round((note == ConnectorNote.SENDING ? along : 1 - along) * 40);
                graphics.fill(leftPos + px, topPos + y + 6, leftPos + px + 2, topPos + y + 8, color);
            }
        }
    }

    /** The three mode buttons of a panel. */
    private void modes(int x, int y, int firstId, String key, String[] names, int selected) {
        int[] widths = {24, 34, 46};
        int bx = x;
        for (int i = 0; i < 3; i++) {
            toggle(bx, y, widths[i], 12, Component.translatable(key + names[i]).getString(), firstId + i,
                    List.of(Component.translatable(key + names[i] + ".hint")), selected == i);
            bx += widths[i] + 2;
        }
    }

    private void stepper(GuiGraphicsExtractor graphics, int x, int y, String key, int less, int more, boolean canLess, boolean canMore,
                         Component hint) {
        label(graphics, Component.translatable(key), x, y + 2, LABEL);
        button(x + LESS_X, y, 12, 12, "-", less, List.of(hint), canLess);
        button(x + MORE_X, y, 12, 12, "+", more, List.of(hint), canMore);
    }

    private void packageRow(GuiGraphicsExtractor graphics, int x, int y, int index, int[] sizes, int cap, int less, int more, String hintKey) {
        int at = Math.clamp(index, 0, sizes.length - 1);
        int size = Math.min(sizes[at], cap);
        stepper(graphics, x, y, G + "package", less, more, at > 0, at < sizes.length - 1 && sizes[at] < cap,
                Component.translatable(hintKey, size));
        if (detailed()) {
            value(graphics, x, y, String.valueOf(size));
        } else {
            pips(graphics, x, y, at + 1, sizes.length, 0xFF4FE3FF);
        }
    }

    private void value(GuiGraphicsExtractor graphics, int x, int y, String text) {
        value(graphics, x, y, text, LABEL);
    }

    /** A stepper's value, centred between its buttons. */
    private void value(GuiGraphicsExtractor graphics, int x, int y, String text, int color) {
        label(graphics, text, x + VALUE_X + (VALUE_W - font.width(text) + 1) / 2, y + 2, color);
    }

    /** A stepper's value as a row of pips. */
    private void pips(GuiGraphicsExtractor graphics, int x, int y, int lit, int total, int color) {
        int pitch = total > 6 ? 4 : 6;
        int left = leftPos + x + VALUE_X + (VALUE_W - total * pitch + 1) / 2;
        for (int i = 0; i < total; i++) {
            // Each pip a little taller than the one before, like a volume bar.
            int height = 3 + i * 5 / Math.max(1, total - 1);
            graphics.fill(left + i * pitch, topPos + y + 10 - height, left + i * pitch + pitch - 1, topPos + y + 10, i < lit ? color : 0xFF1E2E3A);
        }
    }

    /** The colony's own food bar with a mark at the level set. */
    private void level(GuiGraphicsExtractor graphics, int x, int y, double has, double set) {
        int left = leftPos + x + VALUE_X + 1;
        drawBar(graphics, left, topPos + y + 3, VALUE_W - 2, 6, has, 0xFFE0B040);
        int mark = left + (int) Math.round((VALUE_W - 3) * Math.clamp(set, 0, 1));
        graphics.fill(mark, topPos + y + 1, mark + 1, topPos + y + 11, 0xFFFFFFFF);
    }

    /** Why it moves or doesn't: two lines at most, the rest on hover. */
    private void note(GuiGraphicsExtractor graphics, ConnectorNote note, int x, int y) {
        int color = switch (note.tone()) {
            case GOOD -> GOOD;
            case QUIET -> DIM;
            case WAITING -> WARN;
            case PROBLEM -> BAD;
        };
        String text = Component.translatable(note.translationKey()).getString();
        int width = PANEL_W - 8;
        List<String> parts = new ArrayList<>();
        font.getSplitter().splitLines(text, width, Style.EMPTY).forEach(part -> parts.add(part.getString()));
        for (int i = 0; i < parts.size() && i < 2; i++) {
            String shown = parts.get(i);
            if (i == 1 && parts.size() > 2) {
                shown = fit(String.join(" ", parts.subList(1, parts.size())), width);
            }
            label(graphics, shown, x, y + i * 9, color);
        }
    }

    private @Nullable Colony colony() {
        BlockPos terrarium = menu.structure().terrarium();
        return terrarium != null && minecraft.level != null && minecraft.level.getBlockEntity(terrarium) instanceof TerrariumBlockEntity be
                ? be.colony() : null;
    }

    private static double foodRoom(Colony colony) {
        return Math.max(1, colony.stats().foodCapacity() > 0 ? colony.stats().foodCapacity() : AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY));
    }

    // --- The filter ---

    private boolean filterSlot(@Nullable Slot slot) {
        return slot != null && slot.container == menu.structure();
    }

    /** Over each named item, like a stack's count: how many of it stay in the Storage. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);
        if (!filterSlot(slot) || !slot.hasItem() || menu.data(StructureBlockEntity.DATA_FILTER_MODE) == ConnectorSettings.ALL_BUT) {
            return;
        }
        int stock = menu.data(StructureBlockEntity.DATA_STOCK + slot.getContainerSlot());
        if (stock > 0) {
            String text = stock >= 1000 ? stock / 1000 + "k" : String.valueOf(stock);
            graphics.nextStratum();
            graphics.text(font, text, slot.x + 17 - font.width(text), slot.y + 9, 0xFFFFE08A, true);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (filterSlot(hoveredSlot) && hoveredSlot.hasItem() && scrollY != 0
                && menu.data(StructureBlockEntity.DATA_FILTER_MODE) == ConnectorSettings.ONLY) {
            onButton((scrollY > 0 ? StructureMenu.STOCK_MORE : StructureMenu.STOCK_LESS) + hoveredSlot.getContainerSlot());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!filterSlot(hoveredSlot)) {
            return;
        }
        boolean allBut = menu.data(StructureBlockEntity.DATA_FILTER_MODE) == ConnectorSettings.ALL_BUT;
        List<Component> lines = new ArrayList<>();
        if (!hoveredSlot.hasItem()) {
            lines.add(Component.translatable(G + (allBut ? "filter.all_but.slot" : "filter.only.slot")));
            lines.add(Component.translatable(G + "filter.hint").withStyle(ChatFormatting.GRAY));
        } else {
            ItemStack named = hoveredSlot.getItem();
            lines.add(named.getHoverName());
            if (!allBut) {
                int stock = menu.data(StructureBlockEntity.DATA_STOCK + hoveredSlot.getContainerSlot());
                lines.add((stock > 0 ? Component.translatable(G + "stock", stock) : Component.translatable(G + "stock.none"))
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(G + "stock.hint").withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(G + "filter.clear").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // --- Buttons ---

    @Override
    protected void onButton(int id) {
        if (id == BUTTON_VIEW) {
            AntConfig.DETAILED_COLONY_VIEW.set(!detailed());
            AntConfig.CLIENT_SPEC.save();
            clickSound();
            return;
        }
        super.onButton(id);
    }

    @Override
    protected void drawButton(GuiGraphicsExtractor graphics, Button button, boolean hovered) {
        super.drawButton(graphics, button, hovered);
        for (int[] swatch : channelSwatches) {
            if (button.x() == leftPos + swatch[0] && button.y() == topPos + swatch[1]) {
                int color = 0xFF000000 | DyeColor.byId(swatch[2]).getTextureDiffuseColor();
                graphics.fill(button.x() + 1, button.y() + 1, button.x() + button.w() - 1, button.y() + button.h() - 1, color);
                if (swatch[3] == 1) {
                    graphics.outline(button.x() - 1, button.y() - 1, button.w() + 2, button.h() + 2, 0xFFFFFFFF);
                }
            }
        }
    }
}
