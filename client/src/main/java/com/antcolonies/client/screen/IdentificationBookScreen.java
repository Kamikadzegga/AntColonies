package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Ant Identification Book: how-to-play pages first, then every species of the loaded data, with its colors, where it
 * lives, its climate and the blocks that fix it, what it eats, finds and hunts, and how Queens evolve into and out of it.
 */
public class IdentificationBookScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int LIST_W = 122;
    private static final int HEIGHT = 196;
    private static final int ROWS = 17;

    /** A how-to-play page: its name in the lang file and the items it is about. */
    private record Guide(String page, Supplier<List<ItemStack>> items) {}

    private static final List<Guide> GUIDES = List.of(
            new Guide("start", () -> stacks(ModItems.TWEEZERS.get(), ModItems.TEST_TUBE.get(), ModItems.TERRARIUM.get(), ModItems.ANT_FOOD.get())),
            new Guide("chambers", () -> {
                List<ItemStack> list = new ArrayList<>();
                for (StructureKind kind : StructureKind.values()) {
                    list.add(new ItemStack(ModBlocks.STRUCTURES.get(kind).get()));
                }
                return list;
            }),
            new Guide("climate", () -> stacks(Items.SNOW_BLOCK, Items.SAND, Items.GRASS_BLOCK, Items.STONE, Items.NETHERRACK)),
            new Guide("food", () -> stacks(ModItems.ANT_FOOD.get(), ModItems.ROYAL_JELLY.get(), ModItems.MINERAL_ANT_FOOD.get())),
            new Guide("jobs", () -> stacks(ModBlocks.STRUCTURES.get(StructureKind.FORAGING_SITE).get().asItem(),
                    ModBlocks.STRUCTURES.get(StructureKind.HARVESTING_SITE).get().asItem())),
            new Guide("evolution", () -> stacks(ModItems.EVOLUTION_CATALYST.get(), Items.IRON_INGOT, ModBlocks.STRUCTURES.get(StructureKind.QUEEN_CHAMBER).get().asItem())),
            new Guide("genetics", () -> stacks(ModItems.GENETIC_LAB.get(), ModItems.GENERATOR.get(), ModItems.GENETIC_ANALYZER.get())),
            new Guide("upgrades", () -> stacks(ModItems.UPGRADE_BASE.get(), ModItems.UPGRADE_STATION.get())),
            new Guide("automation", () -> stacks(Items.HOPPER, Items.COMPARATOR, Items.REDSTONE_BLOCK)));

    private final List<Holder.Reference<AntSpecies>> species = new ArrayList<>(ClientSpecies.all());
    private final InfoPanel info = new InfoPanel();
    private int selected;
    private int scroll;

    public IdentificationBookScreen() {
        super(Component.translatable("item.antcolonies.ant_identification_book"));
    }

    private static List<ItemStack> stacks(net.minecraft.world.level.ItemLike... items) {
        List<ItemStack> list = new ArrayList<>();
        for (net.minecraft.world.level.ItemLike item : items) {
            list.add(new ItemStack(item));
        }
        return list;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int entries() {
        return GUIDES.size() + species.size();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = left();
        int top = top();
        AntScreen.drawPanel(graphics, left, top, WIDTH, HEIGHT);
        AntScreen.drawWell(graphics, left + 6, top + 6, LIST_W, HEIGHT - 12);
        AntScreen.drawWell(graphics, left + LIST_W + 8, top + 6, WIDTH - LIST_W - 14, HEIGHT - 12);
        scroll = Math.clamp(scroll, 0, Math.max(0, entries() - ROWS));
        for (int row = 0; row < ROWS && scroll + row < entries(); row++) {
            int index = scroll + row;
            int y = top + 9 + row * 10;
            if (index == selected) {
                graphics.fill(left + 7, y - 1, left + LIST_W + 5, y + 9, 0x553FB8E0);
            }
            String name;
            int color;
            if (index < GUIDES.size()) {
                // How-to-play pages: a gold "?" mark.
                graphics.text(font, "?", left + 10, y, 0xFFFFC85A, false);
                name = Component.translatable("guide.antcolonies." + GUIDES.get(index).page() + ".title").getString();
                color = 0xFFFFE6A8;
            } else {
                Holder.Reference<AntSpecies> entry = species.get(index - GUIDES.size());
                graphics.fill(left + 9, y + 1, left + 15, y + 7, 0xFF000000 | entry.value().look().gaster());
                name = Tooltips.species(entry.key().identifier()).getString();
                color = 0xFFD8F2FF;
            }
            if (font.width(name) > LIST_W - 16) {
                name = font.plainSubstrByWidth(name, LIST_W - 16 - font.width("..")) + "..";
            }
            graphics.text(font, name, left + 18, y, color, false);
        }
        selected = Math.clamp(selected, 0, Math.max(0, entries() - 1));
        int x = left + LIST_W + 14;
        int w = WIDTH - LIST_W - 26;
        if (selected < GUIDES.size()) {
            Guide guide = GUIDES.get(selected);
            info.guide(font, guide.page(), guide.items().get(), w);
        } else if (!species.isEmpty()) {
            Holder.Reference<AntSpecies> entry = species.get(selected - GUIDES.size());
            info.species(font, entry.key().identifier(), entry.value(), null, null, w);
        }
        info.draw(graphics, font, x, top + 10, HEIGHT - 20);
        List<Component> tooltip = info.hover(mouseX, mouseY);
        if (tooltip != null) {
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int left = left();
            int top = top();
            if (event.x() >= left + 7 && event.x() < left + LIST_W + 5) {
                int row = (int) ((event.y() - top - 8) / 10);
                if (row >= 0 && row < ROWS && scroll + row < entries()) {
                    selected = scroll + row;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // Over the page: scroll the page; over the list: scroll the list.
        if (x >= left() + LIST_W + 8) {
            info.scroll(scrollY);
        } else {
            scroll -= (int) Math.signum(scrollY);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
