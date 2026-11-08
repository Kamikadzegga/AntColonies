package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The Ant Identification Book: every species of the loaded data, its colors, where it lives, what it eats, what it
 * finds and how Queens evolve into and out of it.
 */
public class IdentificationBookScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int LIST_W = 122;
    private static final int HEIGHT = 196;
    private static final int ROWS = 17;
    private final List<Holder.Reference<AntSpecies>> species = new ArrayList<>(ClientSpecies.all());
    private int selected;
    private int scroll;

    public IdentificationBookScreen() {
        super(Component.translatable("item.antcolonies.ant_identification_book"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = left();
        int top = top();
        AntScreen.drawPanel(graphics, left, top, WIDTH, HEIGHT);
        AntScreen.drawWell(graphics, left + 6, top + 6, LIST_W, HEIGHT - 12);
        AntScreen.drawWell(graphics, left + LIST_W + 8, top + 6, WIDTH - LIST_W - 14, HEIGHT - 12);
        scroll = Math.clamp(scroll, 0, Math.max(0, species.size() - ROWS));
        for (int row = 0; row < ROWS && scroll + row < species.size(); row++) {
            int index = scroll + row;
            Holder.Reference<AntSpecies> entry = species.get(index);
            int y = top + 9 + row * 10;
            if (index == selected) {
                graphics.fill(left + 7, y - 1, left + LIST_W + 5, y + 9, 0x553FB8E0);
            }
            graphics.fill(left + 9, y + 1, left + 15, y + 7, 0xFF000000 | entry.value().look().gaster());
            String name = Tooltips.species(entry.key().identifier()).getString();
            if (font.width(name) > LIST_W - 16) {
                name = font.plainSubstrByWidth(name, LIST_W - 16 - font.width("..")) + "..";
            }
            graphics.text(font, name, left + 18, y, 0xFFD8F2FF, false);
        }
        if (species.isEmpty()) {
            return;
        }
        Holder.Reference<AntSpecies> entry = species.get(Math.clamp(selected, 0, species.size() - 1));
        AntSpecies data = entry.value();
        Identifier id = entry.key().identifier();
        int x = left + LIST_W + 14;
        int y = top + 10;
        graphics.text(font, Tooltips.species(id), x, y, 0xFF4FE3FF, false);
        y += 11;
        graphics.text(font, Component.translatable("book.antcolonies.kind", Component.translatable("category.antcolonies." + data.category().getSerializedName()),
                data.tier(), Component.translatable("climate.antcolonies." + data.climate().getSerializedName())), x, y, 0xFF7896A8, false);
        y += 10;
        // Colors: body, gaster, accent, crystals.
        int[] colors = {data.look().body(), data.look().gaster(), data.look().accent(), data.look().crystal().orElse(-1)};
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] >= 0) {
                graphics.fill(x + i * 12, y, x + i * 12 + 10, y + 8, 0xFF000000 | colors[i]);
            }
        }
        y += 12;
        if (data.spawn().isPresent()) {
            graphics.text(font, Component.translatable("book.antcolonies.wild", data.spawn().get().location().getPath().replace('_', ' ')), x, y, 0xFF7896A8, false);
        } else {
            graphics.text(font, Component.translatable("book.antcolonies.not_wild"), x, y, 0xFF7896A8, false);
        }
        y += 10;
        String diet = data.needsSpecialFood() ? Component.translatable("book.antcolonies.diet_special", data.diet().specialKind()).getString()
                : Component.translatable("book.antcolonies.diet").getString();
        graphics.text(font, diet + String.format(Locale.ROOT, " (x%.2f)", data.diet().consumption()), x, y, 0xFF7896A8, false);
        y += 12;
        graphics.text(font, Component.translatable(data.canFarm() ? "book.antcolonies.farms" : "book.antcolonies.finds"), x, y, 0xFF4FE3FF, false);
        y += 10;
        List<SpeciesOutput> outputs = data.canFarm() ? data.farming().harvest() : data.production().outputs();
        int ix = x;
        for (SpeciesOutput output : outputs) {
            ItemStack stack = output.create(1);
            if (stack.isEmpty()) {
                continue;
            }
            graphics.item(stack, ix, y);
            ix += 18;
            if (ix > left + WIDTH - 24) {
                ix = x;
                y += 18;
            }
        }
        y += 22;
        // Evolutions from and to this species.
        var registries = ClientSpecies.registries();
        if (registries != null) {
            var lookup = registries.lookup(ModRegistries.EVOLUTION);
            if (lookup.isPresent()) {
                for (Holder.Reference<Evolution> evolution : lookup.get().listElements().toList()) {
                    Evolution value = evolution.value();
                    if (y > top + HEIGHT - 20) {
                        break;
                    }
                    if (value.from().equals(id) || value.to().equals(id)) {
                        ItemStack item = value.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                        graphics.item(item, x, y - 4);
                        Component text = Component.translatable("book.antcolonies.evolution", Tooltips.species(value.from()), Tooltips.species(value.to()),
                                Math.round(value.chance() * 100));
                        graphics.text(font, text, x + 18, y, 0xFFE07AFF, false);
                        y += 16;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int left = left();
            int top = top();
            if (event.x() >= left + 7 && event.x() < left + LIST_W + 5) {
                int row = (int) ((event.y() - top - 8) / 10);
                if (row >= 0 && row < ROWS && scroll + row < species.size()) {
                    selected = scroll + row;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
