package com.antcolonies.client.screen;

import java.util.List;
import java.util.Locale;

import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.CompanionForms;
import com.antcolonies.companion.CompanionStats;
import com.antcolonies.companion.CompanionXp;
import com.antcolonies.companion.Companions;
import com.antcolonies.companion.menu.CompanionMenu;
import com.antcolonies.companion.menu.RenameCompanionPayload;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** A companion's screen: the ant itself, who it is, how far it has come, what it wears and what it is told to do. */
public class CompanionScreen extends AntScreen<CompanionMenu> {
    private static final int VIEW_X = 8;
    private static final int VIEW_Y = 20;
    private static final int VIEW_W = 58;
    private static final int VIEW_H = 70;
    private static final int TEXT_X = 72;
    private static final int TEXT_W = 120;
    private static final int COMMAND_Y = 117;
    private static final String[] SLOT_NAMES = {"armor", "costume", "cart"};

    private EditBox name;
    /** The name the server was last sent: closing does not send it a second time. */
    private @Nullable String sent;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, CompanionMenu.INVENTORY_Y + 82);
    }

    @Override
    protected void init() {
        super.init();
        String old = name == null ? customName() : name.getValue();
        name = new EditBox(font, leftPos + TEXT_X, topPos + 20, TEXT_W, 12, Component.translatable("gui.antcolonies.companion.name"));
        name.setMaxLength(CompanionMenu.MAX_NAME);
        name.setValue(old);
        name.setHint(Component.translatable("gui.antcolonies.companion.name"));
        addRenderableWidget(name);
    }

    private String customName() {
        Mob mob = menu.mob();
        return mob != null && mob.hasCustomName() ? mob.getCustomName().getString() : "";
    }

    private void sendName() {
        String typed = name.getValue().strip();
        if (!typed.equals(sent != null ? sent : customName())) {
            sent = typed;
            ClientPacketDistributor.sendToServer(new RenameCompanionPayload(name.getValue()));
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

    /** The name goes first: once the server is told the screen is closed, it no longer takes one. */
    @Override
    public void onClose() {
        sendName();
        super.onClose();
    }

    @Override
    public void removed() {
        sendName();
        super.removed();
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Mob mob = menu.mob();
        CompanionData data = Companions.get(mob);
        drawWell(graphics, leftPos + VIEW_X, topPos + VIEW_Y, VIEW_W, VIEW_H);
        if (mob == null || data == null) {
            return;
        }
        // With a cart behind it, it is drawn smaller: both fit.
        int size = (int) (Math.clamp(34 / Math.max(0.45F, mob.getBbWidth()) * 0.75F, 12, 46) * (data.hasCart() ? 0.72F : 1));
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos + VIEW_X + 2, topPos + VIEW_Y + 2, leftPos + VIEW_X + VIEW_W - 2,
                topPos + VIEW_Y + VIEW_H - 2, size, 0.1F, mouseX, mouseY, mob);
        int max = AntConfig.get(AntConfig.COMPANION_MAX_LEVEL);
        label(graphics, fit(Companions.guardName(data).getString(), TEXT_W), TEXT_X, 36, INFO);
        label(graphics, Component.translatable("gui.antcolonies.companion.level", data.level(), max), TEXT_X, 47, LABEL);
        int needed = CompanionXp.needed(data.level());
        boolean top = data.level() >= max;
        drawBar(graphics, leftPos + TEXT_X + 1, topPos + 58, TEXT_W - 2, 5, top ? 1 : data.xp() / (double) needed, top ? 0xFFFFC85A : 0xFF6CF08A);
        label(graphics, Component.translatable("gui.antcolonies.companion.health", number(Math.ceil(mob.getHealth())),
                number(Math.round(mob.getMaxHealth()))), TEXT_X, 68, LABEL);
        label(graphics, Component.translatable("gui.antcolonies.companion.damage", one(menu.damage())), TEXT_X, 78, LABEL);
        label(graphics, Component.translatable("gui.antcolonies.companion.armor", one(CompanionStats.armor(mob))), TEXT_X, 88, LABEL);
        label(graphics, fit(data.personality().label().getString(), 58), TEXT_X + 62, 78, DIM);
        label(graphics, fit(CompanionForms.label(data.form()).getString(), 58), TEXT_X + 62, 88, data.form().isPresent() ? ACCENT : FAINT);
        // The orders, then what else there is to do.
        int x = VIEW_X;
        for (CompanionCommand command : CompanionCommand.values()) {
            int width = 40;
            buttons.add(new Button(leftPos + x, topPos + COMMAND_Y, width, 14, command.label().getString(), command.ordinal(),
                    List.of(command.label(), Component.translatable("gui.antcolonies.companion.command." + command.getSerializedName())
                            .withStyle(ChatFormatting.GRAY)), true, data.command() == command));
            x += width + 2;
        }
        buttons.add(new Button(leftPos + 136, topPos + COMMAND_Y, 56, 14, Component.translatable("gui.antcolonies.companion.pick_up").getString(),
                CompanionMenu.BUTTON_PICK_UP, List.of(Component.translatable("gui.antcolonies.companion.pick_up.hint")), true, false));
        if (data.hasCart()) {
            buttons.add(new Button(leftPos + 136, topPos + CompanionMenu.GEAR_Y, 56, 16, Component.translatable("gui.antcolonies.companion.cart").getString(),
                    CompanionMenu.BUTTON_CART, List.of(Component.translatable("gui.antcolonies.companion.cart.hint")), true, false));
        }
    }

    private static String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Mob mob = menu.mob();
        CompanionData data = Companions.get(mob);
        if (mob == null || data == null) {
            return;
        }
        if (hoveredSlot != null && hoveredSlot.index < SLOT_NAMES.length && !hoveredSlot.hasItem()) {
            String key = "gui.antcolonies.companion.slot." + SLOT_NAMES[hoveredSlot.index];
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(key),
                    Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (over(mouseX, mouseY, TEXT_X, 46, TEXT_W, 18)) {
            int max = AntConfig.get(AntConfig.COMPANION_MAX_LEVEL);
            graphics.setComponentTooltipForNextFrame(font, List.of(data.level() >= max ? Component.translatable("gui.antcolonies.companion.xp.max")
                            : Component.translatable("gui.antcolonies.companion.xp", data.xp(), CompanionXp.needed(data.level())),
                    Component.translatable("gui.antcolonies.companion.xp.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (over(mouseX, mouseY, VIEW_X, VIEW_Y, VIEW_W, VIEW_H)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Companions.guardName(data),
                    Component.translatable("gui.antcolonies.companion.owner", data.owner().map(CompanionData.Owner::name).orElse("?"))
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.antcolonies.companion.strength", data.strength()).withStyle(ChatFormatting.GRAY),
                    Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()).withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        }
    }
}
