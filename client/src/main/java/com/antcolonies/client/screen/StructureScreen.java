package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
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
        boolean linked = menu.data(StructureBlockEntity.DATA_LINKED) != 0;
        int x = 8;
        int y = kind == StructureKind.STORAGE ? 130 : 18;
        // The locked Storage slots, darkened.
        if (kind == StructureKind.STORAGE) {
            int unlocked = menu.data(StructureBlockEntity.DATA_UNLOCKED);
            for (int slot = unlocked; slot < kind.slots(); slot++) {
                int sx = leftPos + 8 + (slot % 9) * 18 - 1;
                int sy = topPos + 18 + (slot / 9) * 18 - 1;
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF141B22);
                graphics.fill(sx + 6, sy + 7, sx + 12, sy + 12, 0xFF2B3B48);
                graphics.fill(sx + 7, sy + 4, sx + 11, sy + 7, 0xFF2B3B48);
            }
            label(graphics, linkLine(linked), x, y, linked ? GOOD : BAD);
            return;
        }
        label(graphics, linkLine(linked), x, y, linked ? GOOD : BAD);
        y += 10;
        if (kind.job().any()) {
            y = jobRow(graphics, x, y, false);
            if (kind.job().soldiers()) {
                y = jobRow(graphics, x, y, true);
            }
        }
        int status = menu.data(StructureBlockEntity.DATA_STATUS) | (kind == StructureKind.QUEEN_CHAMBER ? menu.data(StructureBlockEntity.DATA_REARING_STATUS) : 0);
        boolean slotted = kind == StructureKind.QUEEN_CHAMBER;
        if (kind == StructureKind.QUEEN_CHAMBER) {
            drawQueenChamber(graphics, x);
        } else if (kind == StructureKind.NEST_CHAMBER) {
            y = nestRoom(graphics, x, y);
        } else if (!kind.job().any()) {
            y = wrapped(graphics, Component.translatable("gui.antcolonies.effect." + kind.getSerializedName()), x, y, 170, 9, DIM);
        } else if (kind == StructureKind.FORAGING_SITE) {
            // Its store's slots come next (their tooltip says what they are); the problems go under them.
            y = StructureMenu.STORE_Y + 20;
        }
        // Problems and work state: under the link line. Slotted structures have room for one line above their slots.
        int line = slotted ? 28 : y;
        int last = slotted ? 28 : StructureMenu.inventoryY(kind) - 20;
        for (ColonyStatus flag : ColonyStatus.of(status)) {
            if (line > last) {
                break;
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
        long soldiers = Math.round(AntConfig.get(AntConfig.NEST_SOLDIER_CAPACITY) * room);
        long larvae = Math.round(AntConfig.get(AntConfig.NEST_LARVAE_CAPACITY) * room);
        label(graphics, fit(Component.translatable("gui.antcolonies.nest.adults", number(adults)).getString(), 170), x, y, LABEL);
        y += 10;
        label(graphics, fit(Component.translatable("gui.antcolonies.nest.soldiers", number(soldiers)).getString(), 170), x, y, LABEL);
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

    /** A working site's ants of one kind: how many it has and wants, the +/- buttons, and how fast they work. */
    private int jobRow(GuiGraphicsExtractor graphics, int x, int y, boolean soldiers) {
        int assigned = menu.data(soldiers ? StructureBlockEntity.DATA_SOLDIERS : StructureBlockEntity.DATA_ASSIGNED);
        int requested = menu.data(soldiers ? StructureBlockEntity.DATA_REQUESTED_SOLDIERS : StructureBlockEntity.DATA_REQUESTED);
        int capacity = menu.data(StructureBlockEntity.DATA_CAPACITY);
        int offset = soldiers ? StructureMenu.SOLDIER_ROW : 0;
        String wanted = requested < 0 ? Component.translatable("gui.antcolonies.auto").getString() : String.valueOf(requested);
        String key = soldiers ? "gui.antcolonies.site.soldiers" : kind == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.farmers"
                : "gui.antcolonies.site.workers";
        label(graphics, fit(Component.translatable(key, assigned, capacity, wanted).getString(), 170), x, y, LABEL);
        y += 11;
        int bx = x;
        Object[][] row = {{"-10", StructureMenu.LESS_10}, {"-1", StructureMenu.LESS}, {"+1", StructureMenu.MORE}, {"+10", StructureMenu.MORE_10}};
        for (Object[] b : row) {
            int width = buttonWidth((String) b[0]);
            button(bx, y, width, 12, (String) b[0], (Integer) b[1] + offset, null, true);
            bx += width + 2;
        }
        String auto = Component.translatable("gui.antcolonies.auto").getString();
        toggle(bx, y, buttonWidth(auto), 12, auto, StructureMenu.AUTO + offset, List.of(Component.translatable("gui.antcolonies.auto.hint")), requested < 0);
        y += 15;
        double rate = menu.data(soldiers ? StructureBlockEntity.DATA_HUNT_RATE : StructureBlockEntity.DATA_RATE) / 10_000.0;
        String rateKey = soldiers ? "gui.antcolonies.site.hunt_rate" : kind == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.food_rate"
                : "gui.antcolonies.site.rate";
        label(graphics, Component.translatable(rateKey, rate(rate)), x, y, DIM);
        double progress = menu.data(soldiers ? StructureBlockEntity.DATA_HUNT_PROGRESS : StructureBlockEntity.DATA_PROGRESS) / 1000.0;
        drawBar(graphics, leftPos + x + 110, topPos + y, 56, 8, progress, soldiers ? 0xFFD05A3F : 0xFF3FD0A0);
        return y + 11;
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
        if (hoveredSlot != null && kind == StructureKind.FORAGING_SITE && hoveredSlot.container == menu.structure() && !hoveredSlot.hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.site.store")), mouseX, mouseY);
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
