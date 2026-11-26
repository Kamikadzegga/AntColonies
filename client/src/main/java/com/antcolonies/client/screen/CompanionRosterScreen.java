package com.antcolonies.client.screen;

import java.util.List;

import com.antcolonies.companion.Companions;
import com.antcolonies.companion.menu.CallCompanionPayload;
import com.antcolonies.companion.menu.RosterPayload;
import com.antcolonies.content.item.Tooltips;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A player's Guard Ants out in the world: each one's name, level and where it is, and a button that calls it. */
public class CompanionRosterScreen extends Screen {
    private static final int WIDTH = 236;
    private static final int ROWS = 6;
    private static final int ROW_H = 24;
    private static final int LIST_Y = 22;
    private static final int HEIGHT = LIST_Y + ROWS * ROW_H + 22;
    private static final int CALL_W = 46;
    /** Nearer than this (blocks) an ant counts as being with its owner. */
    private static final int WITH_YOU = 12;

    private RosterPayload roster;
    private int scroll;

    public CompanionRosterScreen(RosterPayload roster) {
        super(Component.translatable("gui.antcolonies.roster.title"));
        this.roster = roster;
    }

    /** The server's newer list, after a call. */
    public void update(RosterPayload newer) {
        roster = newer;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int callX() {
        return left() + WIDTH - 12 - CALL_W;
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = left();
        int top = top();
        AntScreen.drawPanel(graphics, left, top, WIDTH, HEIGHT);
        graphics.text(font, title, left + 8, top + 7, AntScreen.LABEL, false);
        AntScreen.drawWell(graphics, left + 6, top + LIST_Y - 3, WIDTH - 12, ROWS * ROW_H + 4);
        List<RosterPayload.Row> rows = roster.rows();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        if (rows.isEmpty()) {
            graphics.text(font, Component.translatable("gui.antcolonies.roster.empty"), left + 12, top + LIST_Y + 4, AntScreen.DIM, false);
        }
        List<Component> tip = null;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            RosterPayload.Row row = rows.get(scroll + i);
            int y = top + LIST_Y + i * ROW_H;
            Component kind = Companions.guardName(row.species());
            graphics.text(font, row.name().isEmpty() ? kind : Component.literal(row.name()), left + 12, y + 2, AntScreen.LABEL, false);
            Component second = Component.translatable("gui.antcolonies.roster.level", row.level()).append("  ").append(where(row));
            graphics.text(font, second, left + 12, y + 12, AntScreen.DIM, false);
            if (!row.name().isEmpty() && over(mouseX, mouseY, left + 12, y + 1, callX() - left - 16, 10)) {
                tip = List.of(kind);
            }
            if (row.called()) {
                graphics.text(font, Component.translatable("gui.antcolonies.roster.called"), callX(), y + 7, AntScreen.GOOD, false);
                continue;
            }
            boolean hovered = over(mouseX, mouseY, callX(), y + 3, CALL_W, 16);
            AntScreen.sprite(graphics, hovered ? "button_hover" : "button", callX(), y + 3, CALL_W, 16);
            String call = Component.translatable("gui.antcolonies.roster.call").getString();
            graphics.text(font, call, callX() + (CALL_W - font.width(call)) / 2, y + 7, AntScreen.LABEL, false);
            if (hovered) {
                tip = List.of(Component.translatable("gui.antcolonies.roster.call.hint").withStyle(ChatFormatting.GRAY));
            }
        }
        Component count = roster.most() > 0 ? Component.translatable("gui.antcolonies.roster.count", rows.size(), roster.most())
                : Component.translatable("gui.antcolonies.roster.count.any", rows.size());
        graphics.text(font, count, left + 8, top + HEIGHT - 14, rows.size() >= roster.most() && roster.most() > 0 ? AntScreen.WARN : AntScreen.DIM, false);
        if (rows.size() > ROWS) {
            String more = (scroll + 1) + "-" + Math.min(rows.size(), scroll + ROWS);
            graphics.text(font, more, left + WIDTH - 8 - font.width(more), top + HEIGHT - 14, AntScreen.FAINT, false);
        }
        if (tip != null) {
            graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    /** Where an ant is, as its owner would say it: with them, so many blocks away, or in another dimension. */
    private Component where(RosterPayload.Row row) {
        if (minecraft.player == null || minecraft.level == null) {
            return Component.empty();
        }
        if (!row.dimension().equals(minecraft.level.dimension().identifier())) {
            return Tooltips.dimension(ResourceKey.create(Registries.DIMENSION, row.dimension()));
        }
        int blocks = (int) Math.round(Math.sqrt(row.pos().distSqr(minecraft.player.blockPosition())));
        return blocks <= WITH_YOU ? Component.translatable("gui.antcolonies.roster.here")
                : Component.translatable("gui.antcolonies.roster.away", blocks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && over(event.x(), event.y(), callX(), top() + LIST_Y, CALL_W, ROWS * ROW_H)) {
            int i = (int) ((event.y() - top() - LIST_Y) / ROW_H);
            List<RosterPayload.Row> rows = roster.rows();
            if (scroll + i < rows.size() && !rows.get(scroll + i).called() && over(event.x(), event.y(), callX(), top() + LIST_Y + i * ROW_H + 3, CALL_W, 16)) {
                ClientPacketDistributor.sendToServer(new CallCompanionPayload(rows.get(scroll + i).id()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
