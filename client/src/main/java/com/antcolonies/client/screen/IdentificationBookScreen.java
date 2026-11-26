package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.antcolonies.client.ClientResearch;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.content.item.ChitinGear;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDecor;
import com.antcolonies.core.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * The Ant Identification Book: five sections over one list and one page. What the Species, Research, Compatibility
 * and Production sections show follows what the reader and their team have discovered.
 */
public class IdentificationBookScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int LIST_W = 122;
    private static final int HEIGHT = 196;
    private static final int ROWS = 15;
    /** The list starts under the row of section buttons. */
    private static final int LIST_Y = 29;

    private static final int GUIDE = 0;
    private static final int SPECIES = 1;
    private static final int RESEARCH = 2;
    private static final int COMPATIBILITY = 3;
    private static final int PRODUCTION = 4;
    private static final String[] SECTIONS = {"guide", "species", "research", "compatibility", "production"};

    /** The section last open, kept between openings. */
    private static int lastSection = GUIDE;

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
            new Guide("evolution", () -> stacks(ModItems.EVOLUTION_CATALYST.get(), Items.IRON_INGOT,
                    ModBlocks.STRUCTURES.get(StructureKind.QUEEN_CHAMBER).get().asItem())),
            new Guide("genetics", () -> stacks(ModItems.GENETIC_LAB.get(), ModItems.GENERATOR.get(), ModItems.GENETIC_CIRCUIT.get())),
            new Guide("research", () -> stacks(ModItems.GENETIC_LAB.get(), Items.SPYGLASS, ModItems.EVOLUTION_CATALYST.get(),
                    ModItems.ANT_IDENTIFICATION_BOOK.get())),
            new Guide("upgrades", () -> stacks(ModItems.UPGRADE_BASE.get(), ModItems.UPGRADE_STATION.get())),
            new Guide("automation", () -> stacks(Items.HOPPER, Items.COMPARATOR, Items.REDSTONE_BLOCK)),
            new Guide("founding", () -> stacks(ModItems.QUEEN_TEST_TUBE.get(), ModItems.TERRARIUM.get(), ModItems.ANT_FOOD.get())),
            new Guide("expeditions", () -> stacks(ModItems.NETHER_TRAIL_PHEROMONE.get(),
                    ModBlocks.STRUCTURES.get(StructureKind.NETHER_FORAGING_SITE).get(),
                    ModItems.END_TRAIL_PHEROMONE.get(), ModBlocks.STRUCTURES.get(StructureKind.END_FORAGING_SITE).get())),
            new Guide("connectors", () -> stacks(ModBlocks.STRUCTURES.get(StructureKind.COLONY_CONNECTOR).get())),
            new Guide("building", () -> stacks(ModDecor.ANTHILL_BRICKS.get(),
                    ModDecor.CHITIN_BRICKS.get(),
                    ModDecor.AMBER_BLOCK.get(), ModDecor.ROYAL_CHITIN_BLOCK.get())),
            new Guide("gear", () -> stacks(ModItems.CHITIN_CHESTPLATE.get(),
                    ModItems.CHITIN_TIERS.get(ChitinGear.Tier.HARDENED).get("chestplate").get(),
                    ModItems.CHITIN_TIERS.get(ChitinGear.Tier.INFERNAL).get("chestplate").get(),
                    ModItems.CHITIN_TIERS.get(ChitinGear.Tier.VOID).get("chestplate").get())),
            new Guide("companions", () -> stacks(ModItems.RALLY_PHEROMONE.get(), ModItems.GUARD_ANT.get(),
                    ModItems.COMPANION_ARMOR.get(ChitinGear.Tier.CHITIN).get(), ModItems.ANT_CART.get())));

    private final List<Holder.Reference<AntSpecies>> species = ordered();
    /** The species the reader has met, worked out once a frame. */
    private List<Holder.Reference<AntSpecies>> met = findMet();
    private final InfoPanel info = new InfoPanel();
    private final ScrollBar listBar = new ScrollBar();
    private int section = lastSection;
    /** The chosen entry and the scroll of each section, so switching sections loses neither. */
    private final int[] selected = new int[SECTIONS.length];
    private final int[] scroll = new int[SECTIONS.length];

    public IdentificationBookScreen() {
        super(Component.translatable("item.antcolonies.ant_identification_book"));
    }

    /** A book opened at a section and one of its entries (for the dev showcase). */
    public static IdentificationBookScreen at(int section, int entry) {
        IdentificationBookScreen book = new IdentificationBookScreen();
        book.section = Math.clamp(section, 0, SECTIONS.length - 1);
        book.selected[book.section] = Math.max(0, entry);
        book.scroll[book.section] = Math.max(0, entry - ROWS / 2);
        return book;
    }

    private static List<ItemStack> stacks(ItemLike... items) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemLike item : items) {
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

    // --- What each section lists ---

    /** Every species, the ones the reader has met first, then the ones whose mutation is researched, then the unknown. */
    public static List<Holder.Reference<AntSpecies>> ordered() {
        List<Holder.Reference<AntSpecies>> list = new ArrayList<>(ClientSpecies.all());
        list.sort(Comparator.comparing(entry -> -familiarity(entry.key().identifier()).ordinal()));
        return list;
    }

    /** How much is known about a species: nothing, its mutation only, or the ant itself. */
    private enum Familiarity {
        UNKNOWN,
        RESEARCHED,
        MET
    }

    private static Familiarity familiarity(Identifier species) {
        Knowledge knowledge = ClientResearch.view();
        if (knowledge.knowsSpecies(species)) {
            return Familiarity.MET;
        }
        Researches.Entry research = Researches.forSpecies(ClientSpecies.registries(), species);
        return research != null && knowledge.knowsResearch(research.id()) ? Familiarity.RESEARCHED : Familiarity.UNKNOWN;
    }

    /** The species the reader has met: the Compatibility and Production sections list only these. */
    private List<Holder.Reference<AntSpecies>> metSpecies() {
        return met;
    }

    private List<Holder.Reference<AntSpecies>> findMet() {
        return species.stream().filter(entry -> familiarity(entry.key().identifier()) == Familiarity.MET).toList();
    }

    private int entries() {
        return switch (section) {
            case GUIDE -> GUIDES.size();
            case SPECIES -> species.size();
            case RESEARCH -> Researches.all(ClientSpecies.registries()).size();
            default -> metSpecies().size();
        };
    }

    /** Draws one row's mark and returns its name; {@code color[0]} is set to the name's color. */
    private String row(GuiGraphicsExtractor graphics, int index, int x, int y, int[] color) {
        Knowledge knowledge = ClientResearch.view();
        switch (section) {
            case GUIDE -> {
                graphics.text(font, "?", x + 3, y, 0xFFFFC85A, false);
                color[0] = 0xFFFFE6A8;
                return Component.translatable("guide.antcolonies." + GUIDES.get(index).page() + ".title").getString();
            }
            case SPECIES -> {
                Holder.Reference<AntSpecies> entry = species.get(index);
                Familiarity familiarity = familiarity(entry.key().identifier());
                if (familiarity == Familiarity.MET) {
                    graphics.fill(x + 2, y + 1, x + 8, y + 7, 0xFF000000 | entry.value().look().gaster());
                } else {
                    // A silhouette: an empty chip, lit at the edge once its mutation is researched.
                    graphics.fill(x + 2, y + 1, x + 8, y + 7, familiarity == Familiarity.RESEARCHED ? 0xFF4FE3FF : 0xFF2B4A5A);
                    graphics.fill(x + 3, y + 2, x + 7, y + 6, 0xFF0A1A24);
                }
                color[0] = familiarity == Familiarity.MET ? 0xFFD8F2FF : familiarity == Familiarity.RESEARCHED ? 0xFF4FE3FF : 0xFF4E6676;
                return familiarity == Familiarity.UNKNOWN ? Component.translatable("book.antcolonies.unknown.name").getString()
                        : Tooltips.species(entry.key().identifier()).getString();
            }
            case RESEARCH -> {
                Researches.Entry entry = Researches.all(ClientSpecies.registries()).get(index);
                boolean known = knowledge.knowsResearch(entry.id());
                int tier = switch (entry.research().tier()) {
                    case BASIC -> 0xFF6CF08A;
                    case ADVANCED -> 0xFF4FE3FF;
                    case EXPERIMENTAL -> 0xFFE07AFF;
                };
                graphics.fill(x + 2, y + 1, x + 8, y + 7, known ? tier : 0xFF2B4A5A);
                if (!known) {
                    graphics.fill(x + 3, y + 2, x + 7, y + 6, 0xFF0A1A24);
                }
                color[0] = known ? 0xFFD8F2FF : 0xFF4E6676;
                return known ? entry.research().title().getString() : Component.translatable(entry.research().unlocksSpecies()
                        ? "book.antcolonies.research.unknown_species" : "book.antcolonies.research.unknown_gene").getString();
            }
            default -> {
                Holder.Reference<AntSpecies> entry = metSpecies().get(index);
                graphics.fill(x + 2, y + 1, x + 8, y + 7, 0xFF000000 | entry.value().look().gaster());
                // Compatibility: a green name once a Queen of the species has been analysed.
                color[0] = section == COMPATIBILITY && knowledge.hasAnalysed(entry.key().identifier()) ? 0xFF6CF08A : 0xFFD8F2FF;
                return Tooltips.species(entry.key().identifier()).getString();
            }
        }
    }

    private void page(int index, int w) {
        if (entries() == 0) {
            info.note(font, "book.antcolonies.section." + SECTIONS[section] + ".empty", w);
            return;
        }
        switch (section) {
            case GUIDE -> info.guide(font, GUIDES.get(index).page(), GUIDES.get(index).items().get(), w);
            case SPECIES -> {
                Holder.Reference<AntSpecies> entry = species.get(index);
                if (familiarity(entry.key().identifier()) == Familiarity.MET) {
                    info.species(font, entry.key().identifier(), entry.value(), null, null, w);
                } else {
                    info.unknownSpecies(font, entry.key().identifier(), entry.value(), w);
                }
            }
            case RESEARCH -> info.research(font, Researches.all(ClientSpecies.registries()).get(index), w);
            case COMPATIBILITY -> info.compatibility(font, metSpecies().get(index).key().identifier(), w);
            default -> info.production(font, metSpecies().get(index).key().identifier(), metSpecies().get(index).value(), w);
        }
    }

    private ItemStack sectionIcon(int index) {
        return new ItemStack(switch (index) {
            case GUIDE -> ModItems.ANT_IDENTIFICATION_BOOK.get();
            case SPECIES -> ModItems.TWEEZERS.get();
            case RESEARCH -> Items.SPYGLASS;
            case COMPATIBILITY -> ModItems.GENETIC_SAMPLE.get();
            default -> ModItems.CHITIN_PICKAXE.get();
        });
    }

    private boolean overSection(double mouseX, double mouseY, int index) {
        int x = left() + 8 + index * 24;
        int y = top() + 8;
        return mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 17;
    }

    /** How many entries of the open section are discovered, for the count under the list; -1 where nothing is hidden. */
    private int discovered() {
        Knowledge knowledge = ClientResearch.view();
        return switch (section) {
            case SPECIES -> metSpecies().size();
            case RESEARCH -> (int) Researches.all(ClientSpecies.registries()).stream().filter(entry -> knowledge.knowsResearch(entry.id())).count();
            default -> -1;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        met = findMet();
        int left = left();
        int top = top();
        AntScreen.drawPanel(graphics, left, top, WIDTH, HEIGHT);
        AntScreen.drawWell(graphics, left + 6, top + 6, LIST_W, HEIGHT - 12);
        AntScreen.drawWell(graphics, left + LIST_W + 8, top + 6, WIDTH - LIST_W - 14, HEIGHT - 12);
        // The sections: an item icon each, the open one lit.
        List<Component> sectionTip = null;
        for (int i = 0; i < SECTIONS.length; i++) {
            int x = left + 8 + i * 24;
            boolean hovered = overSection(mouseX, mouseY, i);
            AntScreen.sprite(graphics, section == i ? (hovered ? "button_primary_hover" : "button_primary") : hovered ? "button_hover" : "button",
                    x, top + 8, 22, 17);
            graphics.item(sectionIcon(i), x + 3, top + 8);
            if (hovered) {
                sectionTip = List.of(Component.translatable("book.antcolonies.section." + SECTIONS[i]),
                        Component.translatable("book.antcolonies.section." + SECTIONS[i] + ".hint").withStyle(ChatFormatting.GRAY));
            }
        }
        int entries = entries();
        scroll[section] = Math.clamp(scroll[section], 0, Math.max(0, entries - ROWS));
        selected[section] = Math.clamp(selected[section], 0, Math.max(0, entries - 1));
        listBar.draw(graphics, left + LIST_W, top + LIST_Y - 1, ROWS * 10, scroll[section], Math.max(0, entries - ROWS),
                entries == 0 ? 1 : ROWS / (double) entries, mouseX, mouseY);
        String cutName = null;
        int[] color = new int[1];
        for (int row = 0; row < ROWS && scroll[section] + row < entries; row++) {
            int index = scroll[section] + row;
            int y = top + LIST_Y + row * 10;
            if (index == selected[section]) {
                graphics.fill(left + 7, y - 1, left + LIST_W - 2, y + 9, 0x553FB8E0);
            }
            String name = row(graphics, index, left + 7, y, color);
            if (font.width(name) > LIST_W - 22) {
                if (mouseX >= left + 7 && mouseX < left + LIST_W - 2 && mouseY >= y - 1 && mouseY < y + 9) {
                    cutName = name; // shown whole on hover
                }
                name = font.plainSubstrByWidth(name, LIST_W - 22 - font.width("..")) + "..";
            }
            graphics.text(font, name, left + 18, y, color[0], false);
        }
        // How far the section has been filled in.
        int discovered = discovered();
        if (discovered >= 0) {
            String count = Component.translatable("book.antcolonies.discovered", discovered, entries).getString();
            graphics.text(font, count, left + 6 + (LIST_W - font.width(count)) / 2, top + HEIGHT - 16, discovered == entries ? 0xFFFFD36A : 0xFF7896A8, false);
        }
        page(selected[section], WIDTH - LIST_W - 26);
        info.draw(graphics, font, left + LIST_W + 14, top + 10, HEIGHT - 20);
        if (sectionTip != null) {
            graphics.setComponentTooltipForNextFrame(font, sectionTip, mouseX, mouseY);
            return;
        }
        if (cutName != null) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(cutName)), mouseX, mouseY);
            return;
        }
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
            if (listBar.press(event.x(), event.y())) {
                scroll[section] = listBar.offsetAt(event.y());
                return true;
            }
            if (info.press(event.x(), event.y())) {
                return true;
            }
            for (int i = 0; i < SECTIONS.length; i++) {
                if (overSection(event.x(), event.y(), i)) {
                    section = i;
                    lastSection = i;
                    return true;
                }
            }
            if (event.x() >= left + 7 && event.x() < left + LIST_W - 2 && event.y() >= top + LIST_Y - 1) {
                int row = (int) ((event.y() - top - LIST_Y + 1) / 10);
                if (row >= 0 && row < ROWS && scroll[section] + row < entries()) {
                    selected[section] = scroll[section] + row;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (listBar.dragging()) {
            scroll[section] = listBar.offsetAt(event.y());
            return true;
        }
        return info.drag(event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        listBar.release();
        info.release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // Over the page: scroll the page; over the list: scroll the list.
        if (x >= left() + LIST_W + 8) {
            info.scroll(scrollY);
        } else {
            scroll[section] = Math.max(0, scroll[section] - (int) Math.signum(scrollY));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
