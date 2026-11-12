package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.menu.StorageWindow;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Cost;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Any colony chamber: its link and problems, its ants (working sites), its slots, and its upgrades on the right. */
public class StructureScreen extends AntScreen<StructureMenu> {
    private final StructureKind kind;
    /** Status lines cut to fit this frame, with their full text for the hover tooltip. */
    private final List<Clipped> clipped = new ArrayList<>();

    private record Clipped(int x, int y, int w, String text) {
    }

    /** A Storage's scrollbar: right of its slots, as tall as the six rows shown. */
    private static final int SCROLL_X = 172;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_H = StorageWindow.ROWS * 18 - 2;
    private boolean draggingScrollbar;

    private int thumbHeight() {
        return Math.max(8, SCROLL_H * StorageWindow.ROWS / Math.max(StorageWindow.ROWS, menu.storageRows()));
    }

    /** Scrolls here and on the server (which then sends what the slots now show). */
    private void scrollStorage(int row) {
        int target = Math.clamp(row, 0, menu.maxScroll());
        if (target != menu.firstRow()) {
            menu.scrollTo(target);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StructureMenu.SCROLL + target);
        }
    }

    private void dragScrollbar(double mouseY) {
        int thumb = thumbHeight();
        double along = (mouseY - topPos - SCROLL_Y - thumb / 2.0) / Math.max(1, SCROLL_H - thumb);
        scrollStorage((int) Math.round(Math.clamp(along, 0, 1) * menu.maxScroll()));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (kind == StructureKind.STORAGE && menu.maxScroll() > 0 && scrollY != 0 && over(x, y, 0, 0, 182, StructureMenu.inventoryY(kind) - 12)) {
            scrollStorage(menu.firstRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    protected boolean clickPage(double mouseX, double mouseY) {
        if (kind == StructureKind.STORAGE && menu.maxScroll() > 0 && over(mouseX, mouseY, SCROLL_X, SCROLL_Y, 6, SCROLL_H)) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }
        return super.clickPage(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar) {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    public StructureScreen(StructureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 206, StructureMenu.inventoryY(menu.structure().kind()) + 82);
        this.kind = menu.structure().kind();
        inventoryLabelY = StructureMenu.inventoryY(kind) - 11;
        titleLabelY = 6;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, 8, 6, LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clipped.clear();
        channelSwatches.clear();
        boolean linked = menu.data(StructureBlockEntity.DATA_LINKED) != 0;
        int x = 8;
        int y = kind == StructureKind.STORAGE ? 130 : 18;
        // The locked Storage slots, darkened, and the scrollbar when the open rows don't fit.
        if (kind == StructureKind.STORAGE) {
            int unlocked = menu.data(StructureBlockEntity.DATA_UNLOCKED);
            int first = menu.firstRow() * 9;
            for (int slot = 0; slot < StorageWindow.SIZE; slot++) {
                if (first + slot < unlocked) {
                    continue;
                }
                int sx = leftPos + 8 + (slot % 9) * 18 - 1;
                int sy = topPos + 18 + (slot / 9) * 18 - 1;
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF141B22);
                graphics.fill(sx + 6, sy + 7, sx + 12, sy + 12, 0xFF2B3B48);
                graphics.fill(sx + 7, sy + 4, sx + 11, sy + 7, 0xFF2B3B48);
            }
            if (menu.maxScroll() > 0) {
                int top = topPos + SCROLL_Y;
                graphics.fill(leftPos + SCROLL_X, top, leftPos + SCROLL_X + 6, top + SCROLL_H, 0xFF141B22);
                int thumb = thumbHeight();
                int thumbY = top + (SCROLL_H - thumb) * menu.firstRow() / menu.maxScroll();
                boolean hot = draggingScrollbar || over(mouseX, mouseY, SCROLL_X, SCROLL_Y, 6, SCROLL_H);
                graphics.fill(leftPos + SCROLL_X, thumbY, leftPos + SCROLL_X + 6, thumbY + thumb, hot ? 0xFF6FD8F5 : 0xFF3FA8C8);
            }
            String rows = Component.translatable("gui.antcolonies.storage.slots", unlocked).getString();
            label(graphics, linkLine(linked), x, y, linked ? GOOD : BAD);
            label(graphics, rows, 8 + 162 - font.width(rows), 6, DIM);
            return;
        }
        label(graphics, linkLine(linked), x, y, linked ? GOOD : BAD);
        y += 10;
        if (kind == StructureKind.COLONY_CONNECTOR) {
            connectorPage(graphics, x, y, mouseX, mouseY);
            return;
        }
        if (kind.job().any()) {
            y = jobRow(graphics, x, y);
            y = settingsRow(x, y);
        }
        int status = menu.data(StructureBlockEntity.DATA_STATUS) | (kind == StructureKind.QUEEN_CHAMBER ? menu.data(StructureBlockEntity.DATA_REARING_STATUS) : 0);
        boolean slotted = kind == StructureKind.QUEEN_CHAMBER;
        if (kind == StructureKind.QUEEN_CHAMBER) {
            drawQueenChamber(graphics, x);
        } else if (kind == StructureKind.NEST_CHAMBER) {
            y = nestRoom(graphics, x, y);
        } else if (!kind.job().any()) {
            y = wrapped(graphics, Component.translatable("gui.antcolonies.effect." + kind.getSerializedName()), x, y, 170, 9, DIM);
        } else if (kind.foraging()) {
            // Its store's slots come next (their tooltip says what they are); the problems go under them.
            y = StructureMenu.STORE_Y + 20;
        }
        if (kind.expedition()) {
            // Which ants it needs, named, when the colony's are not among them.
            int flags = menu.data(StructureBlockEntity.DATA_STATUS);
            if (ColonyStatus.INCOMPATIBLE_SPECIES.in(flags)) {
                String needs = Component.translatable("gui.antcolonies.expedition.needs." + kind.getSerializedName(), adaptedNames()).getString();
                String shown = fit(needs, 170);
                if (!shown.equals(needs)) {
                    clipped.add(new Clipped(x, y, 170, needs));
                }
                label(graphics, shown, x, y, BAD);
                y += 9;
            }
        }
        // Problems and work state: under the link line. Slotted structures have room for one line above their slots.
        int line = slotted ? 28 : y;
        int last = slotted ? 28 : StructureMenu.inventoryY(kind) - 20;
        for (ColonyStatus flag : ColonyStatus.of(status)) {
            if (line > last) {
                break;
            }
            if (kind.expedition() && flag == ColonyStatus.INCOMPATIBLE_SPECIES) {
                continue; // said above, with the species it needs
            }
            String full = Component.translatable(flag.translationKey()).getString();
            String shown = fit(full, 170);
            if (!shown.equals(full)) {
                clipped.add(new Clipped(x, line, 170, full));
            }
            label(graphics, shown, x, line, flag == ColonyStatus.WORKING ? GOOD : flag.problem() ? BAD : WARN);
            line += 9;
        }
    }

    /** The first line: linked or not, short enough for the panel (the full status is in the colony's screens). */
    private String linkLine(boolean linked) {
        String key = linked ? "gui.antcolonies.linked" : menu.data(StructureBlockEntity.DATA_CONTESTED) != 0 ? "gui.antcolonies.contested"
                : "gui.antcolonies.not_linked";
        String full = Component.translatable(key).getString();
        String shown = fit(full, 170);
        if (!shown.equals(full)) {
            clipped.add(new Clipped(8, kind == StructureKind.STORAGE ? 130 : 18, 170, full));
        }
        return shown;
    }

    /**
     * What this Nest Chamber adds to its colony, with the Population Upgrades in it (each tier adds half again): room for
     * adults (some of them soldiers) and larvae. Updates as upgrades go in and out.
     */
    private int nestRoom(GuiGraphicsExtractor graphics, int x, int y) {
        int tier = menu.structure().upgradeTier(com.antcolonies.colony.upgrade.UpgradeType.POPULATION);
        double room = 1 + 0.5 * tier;
        long adults = Math.round(AntConfig.get(AntConfig.NEST_ADULT_CAPACITY) * room);
        long larvae = Math.round(AntConfig.get(AntConfig.NEST_LARVAE_CAPACITY) * room);
        label(graphics, fit(Component.translatable("gui.antcolonies.nest.adults", number(adults)).getString(), 170), x, y, LABEL);
        y += 10;
        label(graphics, fit(Component.translatable("gui.antcolonies.nest.larvae", number(larvae)).getString(), 170), x, y, LABEL);
        y += 10;
        if (tier > 0) {
            label(graphics, fit(Component.translatable("gui.antcolonies.nest.upgraded", tier * 50).getString(), 170), x, y, GOOD);
        } else {
            label(graphics, fit(Component.translatable("gui.antcolonies.nest.upgrade_hint").getString(), 170), x, y, DIM);
        }
        return y + 11;
    }

    /** A working site's ants: how many it has and wants, the +/- buttons, and how fast they work. */
    private int jobRow(GuiGraphicsExtractor graphics, int x, int y) {
        int assigned = menu.data(StructureBlockEntity.DATA_ASSIGNED);
        int requested = menu.data(StructureBlockEntity.DATA_REQUESTED);
        int capacity = menu.data(StructureBlockEntity.DATA_CAPACITY);
        boolean hunting = kind == StructureKind.HUNTING_GROUND;
        String wanted = requested < 0 ? Component.translatable("gui.antcolonies.auto").getString() : String.valueOf(requested);
        String key = hunting ? "gui.antcolonies.site.hunters" : kind == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.farmers"
                : "gui.antcolonies.site.workers";
        label(graphics, fit(Component.translatable(key, assigned, capacity, wanted).getString(), 170), x, y, LABEL);
        y += 11;
        int bx = x;
        Object[][] row = {{"-10", StructureMenu.LESS_10}, {"-1", StructureMenu.LESS}, {"+1", StructureMenu.MORE}, {"+10", StructureMenu.MORE_10}};
        for (Object[] b : row) {
            int width = buttonWidth((String) b[0]);
            button(bx, y, width, 12, (String) b[0], (Integer) b[1], null, true);
            bx += width + 2;
        }
        String auto = Component.translatable("gui.antcolonies.auto").getString();
        toggle(bx, y, buttonWidth(auto), 12, auto, StructureMenu.AUTO, List.of(Component.translatable("gui.antcolonies.auto.hint")), requested < 0);
        y += 15;
        double rate = menu.data(StructureBlockEntity.DATA_RATE) / 10_000.0;
        String rateKey = hunting ? "gui.antcolonies.site.hunt_rate" : kind == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.food_rate"
                : "gui.antcolonies.site.rate";
        label(graphics, Component.translatable(rateKey, rate(rate)), x, y, DIM);
        double progress = menu.data(StructureBlockEntity.DATA_PROGRESS) / 1000.0;
        drawBar(graphics, leftPos + x + 110, topPos + y, 56, 8, progress, hunting ? 0xFFD05A3F : 0xFF3FD0A0);
        return y + 11;
    }

    /**
     * A job site's settings: what a Foraging Site looks for (its mode), its priority among the colony's sites, and a
     * switch to turn it off. The tooltips explain each; the server applies the click and syncs back.
     */
    private int settingsRow(int x, int y) {
        int bx = x;
        if (kind == StructureKind.FORAGING_SITE) {
            com.antcolonies.colony.model.ForagingMode mode = com.antcolonies.colony.model.ForagingMode.byId(menu.data(StructureBlockEntity.DATA_MODE));
            String text = Component.translatable(mode.translationKey()).getString();
            int w = buttonWidth(text);
            button(bx, y, w, 12, text, StructureMenu.CYCLE_MODE, List.of(Component.translatable("gui.antcolonies.mode"),
                    Component.translatable(mode.translationKey() + ".hint").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.antcolonies.mode.click").withStyle(ChatFormatting.DARK_GRAY)), true);
            bx += w + 3;
        }
        int priority = menu.data(StructureBlockEntity.DATA_PRIORITY);
        String priorityText = Component.translatable("gui.antcolonies.priority.short." + priority).getString();
        int pw = buttonWidth(priorityText);
        button(bx, y, pw, 12, priorityText, StructureMenu.CYCLE_PRIORITY, List.of(Component.translatable("gui.antcolonies.priority." + priority),
                Component.translatable("gui.antcolonies.priority.hint").withStyle(ChatFormatting.GRAY)), true);
        bx += pw + 3;
        boolean enabled = menu.data(StructureBlockEntity.DATA_ENABLED) != 0;
        String onOff = Component.translatable(enabled ? "gui.antcolonies.site.on" : "gui.antcolonies.site.off").getString();
        toggle(bx, y, buttonWidth(onOff), 12, onOff, StructureMenu.TOGGLE, List.of(Component.translatable("gui.antcolonies.site.toggle.hint")), enabled);
        return y + 15;
    }

    /** The species an expedition site takes, from the synced species tag (names, comma-separated). */
    private String adaptedNames() {
        var expedition = com.antcolonies.colony.expedition.Expedition.forSite(minecraft.level == null ? null : minecraft.level.registryAccess(), kind);
        if (expedition == null || minecraft.level == null) {
            return "?";
        }
        StringBuilder names = new StringBuilder();
        for (var id : expedition.adaptedSpecies(minecraft.level.registryAccess())) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(com.antcolonies.content.item.Tooltips.species(id).getString());
        }
        return names.toString();
    }

    /**
     * A Colony Connector: its channel (a colour), what it does with food (keep a reserve and supply the rest, or ask for food
     * up to a target), how fast, what it does with the items its filter names, and what it moved lately.
     */
    private void connectorPage(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        int channel = menu.data(StructureBlockEntity.DATA_CHANNEL);
        label(graphics, Component.translatable("gui.antcolonies.connector.channel"), x, y + 2, LABEL);
        int sx = x + 44;
        for (int c = 0; c < 16; c++) {
            int bx = sx + c * 8;
            button(bx, y, 7, 11, "", StructureMenu.CHANNEL + c, List.of(Component.translatable("color.minecraft." + net.minecraft.world.item.DyeColor.byId(c).getName())), true);
            channelSwatches.add(new int[] {bx, y, c, c == channel ? 1 : 0});
        }
        y += 14;
        int foodMode = menu.data(StructureBlockEntity.DATA_FOOD_MODE);
        label(graphics, Component.translatable("gui.antcolonies.connector.food"), x, y + 2, LABEL);
        int bx = x + 44;
        String[] foodModes = {"off", "supply", "request"};
        for (int i = 0; i < 3; i++) {
            String text = Component.translatable("gui.antcolonies.connector.food." + foodModes[i]).getString();
            int w = buttonWidth(text);
            toggle(bx, y, w, 12, text, StructureMenu.FOOD_MODE + i, List.of(Component.translatable("gui.antcolonies.connector.food." + foodModes[i] + ".hint")),
                    foodMode == i);
            bx += w + 2;
        }
        y += 15;
        int reserve = menu.data(StructureBlockEntity.DATA_RESERVE);
        int target = menu.data(StructureBlockEntity.DATA_TARGET);
        boolean supply = foodMode == com.antcolonies.content.blockentity.ConnectorSettings.SUPPLY;
        String amountKey = supply ? "gui.antcolonies.connector.keep" : "gui.antcolonies.connector.until";
        int amount = supply ? reserve : target;
        label(graphics, Component.translatable(amountKey), x, y + 2, foodMode == 0 ? DIM : LABEL);
        int ax = x + 44;
        button(ax, y, 12, 12, "-", supply ? StructureMenu.RESERVE_LESS : StructureMenu.TARGET_LESS, null, foodMode != 0);
        label(graphics, amount + "%", ax + 16, y + 2, foodMode == 0 ? DIM : LABEL);
        button(ax + 40, y, 12, 12, "+", supply ? StructureMenu.RESERVE_MORE : StructureMenu.TARGET_MORE, null, foodMode != 0);
        int rate = com.antcolonies.content.blockentity.ConnectorSettings.RATES[Math.clamp(menu.data(StructureBlockEntity.DATA_RATE_LIMIT), 0, 3)];
        String rateText = rate < 0 ? Component.translatable("gui.antcolonies.connector.rate.max").getString()
                : Component.translatable("gui.antcolonies.connector.rate", rate).getString();
        int rw = buttonWidth(rateText);
        button(x + 170 - rw, y, rw, 12, rateText, StructureMenu.CYCLE_RATE, List.of(Component.translatable("gui.antcolonies.connector.rate.hint")), true);
        y += 15;
        int itemMode = menu.data(StructureBlockEntity.DATA_ITEM_MODE);
        label(graphics, Component.translatable("gui.antcolonies.connector.items"), x, y + 2, LABEL);
        bx = x + 44;
        String[] itemModes = {"off", "send", "receive"};
        for (int i = 0; i < 3; i++) {
            String text = Component.translatable("gui.antcolonies.connector.items." + itemModes[i]).getString();
            int w = buttonWidth(text);
            toggle(bx, y, w, 12, text, StructureMenu.ITEM_MODE + i, List.of(Component.translatable("gui.antcolonies.connector.items." + itemModes[i] + ".hint")),
                    itemMode == i);
            bx += w + 2;
        }
        y += 15;
        label(graphics, Component.translatable("gui.antcolonies.connector.filter"), x, StructureMenu.FILTER_Y - 10, itemMode == 1 ? LABEL : DIM);
        // What it moved, under the filter.
        int line = y + 1;
        double in = menu.data(StructureBlockEntity.DATA_FOOD_IN) / 10.0;
        double out = menu.data(StructureBlockEntity.DATA_FOOD_OUT) / 10.0;
        double items = menu.data(StructureBlockEntity.DATA_ITEMS_MOVED) / 10.0;
        int members = menu.data(StructureBlockEntity.DATA_MEMBERS);
        boolean paused = ColonyStatus.PAUSED.in(menu.data(StructureBlockEntity.DATA_STATUS));
        String summary = paused ? Component.translatable("status.antcolonies.paused").getString()
                : Component.translatable("gui.antcolonies.connector.flow", members, rate(in), rate(out), rate(items)).getString();
        String shown = fit(summary, 170);
        if (!shown.equals(summary)) {
            clipped.add(new Clipped(x, line, 170, summary));
        }
        label(graphics, shown, x, line, paused ? WARN : members > 0 ? GOOD : DIM);
    }

    /** The channel swatches drawn this frame: x, y, colour id, selected. */
    private final List<int[]> channelSwatches = new ArrayList<>();

    @Override
    protected void drawButton(GuiGraphicsExtractor graphics, Button button, boolean hovered) {
        super.drawButton(graphics, button, hovered);
        for (int[] swatch : channelSwatches) {
            if (button.x() == leftPos + swatch[0] && button.y() == topPos + swatch[1]) {
                int color = 0xFF000000 | net.minecraft.world.item.DyeColor.byId(swatch[2]).getTextureDiffuseColor();
                graphics.fill(button.x() + 1, button.y() + 1, button.x() + button.w() - 1, button.y() + button.h() - 1, color);
                if (swatch[3] == 1) {
                    graphics.outline(button.x() - 1, button.y() - 1, button.w() + 2, button.h() + 2, 0xFFFFFFFF);
                }
            }
        }
    }

    /** Queen Chamber slot roles: materials, the tube, the sample, the output; null for any other slot. */
    private @Nullable String chamberSlot(net.minecraft.world.inventory.Slot slot) {
        if (kind != StructureKind.QUEEN_CHAMBER || slot.container != menu.structure()) {
            return null;
        }
        int index = slot.getContainerSlot();
        return index < StructureKind.QUEEN_CHAMBER_MATERIALS ? "qc_materials" : index == StructureKind.QUEEN_CHAMBER_TUBE ? "qc_tube"
                : index == StructureKind.QUEEN_CHAMBER_SAMPLE ? "qc_sample" : "qc_output";
    }

    @Override
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, net.minecraft.world.inventory.Slot slot, int x, int y) {
        super.drawSlotBackground(graphics, slot, x, y);
        if (slot.hasItem()) {
            return;
        }
        String role = chamberSlot(slot);
        if (slot.container == menu.structure().upgrades()) {
            drawGlyph(graphics, "upgrade", x, y);
        } else if ("qc_tube".equals(role) || "qc_sample".equals(role)) {
            drawGlyph(graphics, "queen", x, y);
        } else if ("qc_output".equals(role)) {
            drawGlyph(graphics, "output", x, y);
        } else if ("qc_materials".equals(role)) {
            drawGlyph(graphics, "feed", x, y);
        }
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Clipped line : clipped) {
            if (hoveredSlot == null && over(mouseX, mouseY, line.x(), line.y() - 1, line.w(), 9)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(line.text())), mouseX, mouseY);
                return;
            }
        }
        if (hoveredSlot != null && kind == StructureKind.COLONY_CONNECTOR && hoveredSlot.container == menu.structure() && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.connector.filter"),
                    Component.translatable("gui.antcolonies.connector.filter.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && kind.foraging() && hoveredSlot.container == menu.structure() && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.site.store")), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.container == menu.structure().upgrades() && hoveredSlot.hasItem()
                && menu.upgradeHeld(hoveredSlot.getContainerSlot())) {
            // Why this Storage Upgrade won't come out, under the item's own tooltip.
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            lines.add(Component.translatable("gui.antcolonies.storage.upgrade_held").withStyle(ChatFormatting.RED));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.container == menu.structure().upgrades() && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.slot.upgrade"),
                    Component.translatable("gui.antcolonies.slot.structure_upgrade.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        String role = hoveredSlot == null || hoveredSlot.hasItem() ? null : chamberSlot(hoveredSlot);
        if (role != null && !role.equals("qc_materials")) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.slot." + role),
                    Component.translatable("gui.antcolonies.slot." + role + ".hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (kind == StructureKind.QUEEN_CHAMBER && ("qc_materials".equals(role) || hoveredSlot == null && over(mouseX, mouseY, 134, 52, 18, 16))) {
            graphics.setComponentTooltipForNextFrame(font, rearingNeeds(), mouseX, mouseY);
        }
    }

    /** The rearing progress as an arrow from the sample slot to the output, filling gold. */
    private void drawQueenChamber(GuiGraphicsExtractor graphics, int x) {
        AntSpecies species = colonySpecies();
        int time = species == null ? 2400 : species.rearing().time();
        double progress = Math.clamp(menu.data(StructureBlockEntity.DATA_REARING) / (double) time, 0, 1);
        int ax = leftPos + 135;
        int ay = topPos + 60;
        int length = 15;
        int filled = (int) Math.round(length * progress);
        for (int i = 0; i < length; i++) {
            // Shaft for the first 10 columns, then a head narrowing to a point.
            int half = i < 10 ? 1 : 4 - (i - 10);
            int color = i < filled ? 0xFFE0B040 : 0xFF1E2E3A;
            graphics.fill(ax + i, ay - half, ax + i + 1, ay + half + 1, color);
        }
        label(graphics, fit(Component.translatable("gui.antcolonies.queen_chamber.slots").getString(), 170), x, 40, DIM);
    }

    private List<Component> rearingNeeds() {
        AntSpecies species = colonySpecies();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.antcolonies.queen_chamber.needs"));
        if (species != null) {
            for (Cost cost : species.rearing().cost()) {
                ItemStack shown = cost.ingredient().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                lines.add(Component.literal(cost.count() + " x ").append(shown.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
            AntSpecies.Rearing rearing = species.rearing();
            lines.add(Component.translatable("gui.antcolonies.queen_chamber.time", Math.round(rearing.time() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private @Nullable AntSpecies colonySpecies() {
        BlockPos terrarium = menu.structure().terrarium();
        if (terrarium == null || minecraft.level == null || !(minecraft.level.getBlockEntity(terrarium) instanceof TerrariumBlockEntity be)) {
            return null;
        }
        return ClientSpecies.get(be.colony().species());
    }
}
