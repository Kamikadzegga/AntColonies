package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.antcolonies.client.ClientHooks;
import com.antcolonies.client.ClientResearch;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.breeding.Hybrid;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.evolution.SpeciesMutation;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.research.Discovery;
import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.research.Research;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.menu.GeneticLabMenu;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.upgrade.UpgradeEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Genetic Laboratory console. The Queen under glass and the power stay on the left; six pages share the rest:
 * Research, Analysis, Species Mutation, Genetic Editing, Hybridization and Stabilization. The lab shows only what has
 * been discovered: what is still unknown is the Ant Identification Book's business.
 */
public class GeneticLabScreen extends AntScreen<GeneticLabMenu> {
    // Layout (relative to the panel).
    private static final int LEFT_X = 6;
    private static final int LEFT_W = 76;
    private static final int LIST_X = 86;
    private static final int RIGHT_EDGE = 314;
    /** The Analysis list's first row of changes: above it are the Queen's own row and a heading. */
    private static final int ANALYSIS_FIRST_ROW = 2;
    /** Where the Stability page's line about its purpose starts, right of the upgrade slots. */
    private static final int PURPOSE_X = 90;
    /** The list is narrow and the detail panel wide on the pages that explain in sentences; the other way on the rest. */
    private static final int LIST_W_NARROW = 104;
    private static final int LIST_W_WIDE = 128;
    private static final int PANEL_Y = 20;
    private static final int PANEL_H = 106;
    private static final int ROW_Y = 23;
    private static final int ROW_H = 10;
    private static final int ROWS = 10;
    private static final int TOOL_BUTTON_X = 110;
    private static final int TOOL_BUTTON_W = 48;
    private static final int STRIP_Y = 151;
    /** The page tabs fill the console's width; each is as wide as its name needs. */
    private static final int[] TAB_WIDTHS = {56, 52, 52, 46, 49, 53};
    /** The last line of text that still fits in a panel. */
    private static final int LAST_LINE_Y = 116;

    // Console palette.
    private static final int TEXT = 0xFFD8F2FF;
    private static final int TEXT_DIM = 0xFF7896A8;
    private static final int TEXT_FAINT = 0xFF4E6676;
    private static final int CYAN = 0xFF4FE3FF;
    private static final int MAGENTA = 0xFFE07AFF;
    private static final int GREEN = 0xFF6CF08A;
    private static final int LEAF = 0xFF3FA860;
    private static final int RED = 0xFFFF6E6E;
    private static final int GOLD = 0xFFFFD36A;
    private static final int PIP_EMPTY = 0xFF1E2E3A;

    private static final String[] PAGES = {"research", "analysis", "mutation", "editing", "hybrid", "stability"};
    private static final int BUTTON_PAGE = -100;
    private static final int BUTTON_HELP = -200;

    /** The small pixel marks rows and checklists use. */
    private enum Mark {
        CHECK,
        CROSS,
        LOCK,
        SHIELD,
        DOT
    }

    /** The page this player last had open, kept between openings. */
    private static int lastPage = GeneticLabMenu.TAB_RESEARCH;
    private static int lastRow = -1;

    /** How far each page's list is scrolled, and which of its rows is chosen. */
    private final int[] scroll = new int[GeneticLabMenu.TABS];
    private final int[] chosen = new int[GeneticLabMenu.TABS];
    private final ScrollBar listBar = new ScrollBar();
    /** The species an analysis was last asked for, so the request goes out once. */
    private @Nullable Identifier analysisAsked;
    /** On the Analysis page: the detail panel shows the Queen's own genes (the list's first row), not a change. */
    private boolean analysisQueen = true;
    /** The scanner's last answer the list has already jumped to. */
    private int scanShown;

    public GeneticLabScreen(GeneticLabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GeneticLabMenu.WIDTH, GeneticLabMenu.HEIGHT);
    }

    /** The page the next laboratory screen opens on (for the dev showcase). */
    public static void showPage(int page) {
        lastPage = Math.clamp(page, 0, GeneticLabMenu.TABS - 1);
    }

    /** The list entry the next laboratory screen opens with chosen (for the dev showcase). */
    public static void showRow(int row) {
        lastRow = row;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.tab() != lastPage) {
            openPage(lastPage);
        }
        if (lastRow >= 0) {
            chosen[lastPage] = lastRow;
            analysisQueen = false;
            lastRow = -1;
        }
    }

    private void openPage(int page) {
        lastPage = page;
        menu.setTab(page);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GeneticLabMenu.BUTTON_TAB + page);
    }

    private void panelSprite(GuiGraphicsExtractor graphics, String name, int x, int y, int w, int h) {
        sprite(graphics, name, leftPos + x, topPos + y, w, h);
    }

    private float time() {
        return minecraft.level == null ? 0 : ClientHooks.animationTime(minecraft.level.getGameTime(),
                minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    private @Nullable QueenData queen() {
        return menu.queenStack().get(ModDataComponents.QUEEN.get());
    }

    private Trait selected() {
        return Trait.values()[Math.clamp(menu.data(GeneticLabBlockEntity.DATA_TRAIT), 0, Trait.values().length - 1)];
    }

    private boolean idle() {
        return menu.data(GeneticLabBlockEntity.DATA_OPERATION) == 0;
    }

    private GeneticLabBlockEntity.Operation operation() {
        return GeneticLabBlockEntity.Operation.byId(menu.data(GeneticLabBlockEntity.DATA_OPERATION));
    }

    private @Nullable RegistryAccess registries() {
        return ClientSpecies.registries();
    }

    private ResourceKey<Level> dimension() {
        return minecraft.level == null ? Level.OVERWORLD : minecraft.level.dimension();
    }

    // --- Where the two panels of the open page lie ---

    private int listW() {
        int page = menu.tab();
        return page == GeneticLabMenu.TAB_RESEARCH || page == GeneticLabMenu.TAB_ANALYSIS || page == GeneticLabMenu.TAB_HYBRID
                ? LIST_W_NARROW : LIST_W_WIDE;
    }

    private int detailX() {
        return LIST_X + listW() + 4;
    }

    private int detailW() {
        return RIGHT_EDGE - detailX();
    }

    private int textX() {
        return detailX() + 5;
    }

    private int textW() {
        return detailW() - 10;
    }

    // --- The frame, slots and buttons in the console's look ---

    @Override
    protected void drawFrame(GuiGraphicsExtractor graphics) {
        panelSprite(graphics, "frame", 0, 0, imageWidth, imageHeight);
        panelSprite(graphics, "glass", LEFT_X, PANEL_Y, LEFT_W, PANEL_H);
        panelSprite(graphics, "glass", LIST_X, PANEL_Y, listW(), PANEL_H);
        panelSprite(graphics, "glass", detailX(), PANEL_Y, detailW(), PANEL_H);
        panelSprite(graphics, "glass", LEFT_X, STRIP_Y - 1, imageWidth - 12, 11);
        panelSprite(graphics, "capsule", 30, 22, 28, 40);
    }

    @Override
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, Slot slot, int x, int y) {
        super.drawSlotBackground(graphics, slot, x, y);
        if (slot.container == menu.lab() && !slot.hasItem()) {
            int index = slot.getContainerSlot();
            if (index == GeneticLabBlockEntity.SLOT_MUTATION_OUTPUT || index == GeneticLabBlockEntity.SLOT_SAMPLE_OUTPUT) {
                drawGlyph(graphics, "output", x, y);
            } else if (index == GeneticLabBlockEntity.SLOT_QUEEN) {
                drawGlyph(graphics, "queen", x, y);
            }
        }
    }

    /** A primary (glowing) button: the {@code selected} flag of the shared button record marks it. */
    private void primary(int x, int y, int w, int h, String label, int id, @Nullable List<Component> tooltip) {
        buttons.add(new Button(leftPos + x, topPos + y, w, h, label, id, tooltip, true, true));
    }

    @Override
    protected void onButton(int id) {
        if (id <= BUTTON_PAGE && id > BUTTON_PAGE - PAGES.length) {
            openPage(BUTTON_PAGE - id);
            clickSound();
        } else if (id >= 0) {
            super.onButton(id);
        }
    }

    /** The title bar is the row of page tabs: nothing else is written there. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
    }

    private static int scaleAlpha(int color, float alpha) {
        return ((int) (((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0, 1)) << 24) | (color & 0xFFFFFF);
    }

    // --- The pages ---

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        QueenData queen = queen();
        listBar.hide();
        tabs();
        specimen(graphics, queen);
        switch (menu.tab()) {
            case GeneticLabMenu.TAB_RESEARCH -> research(graphics, mouseX, mouseY);
            case GeneticLabMenu.TAB_ANALYSIS -> analysis(graphics, queen, mouseX, mouseY);
            case GeneticLabMenu.TAB_MUTATION -> mutation(graphics, queen, mouseX, mouseY);
            case GeneticLabMenu.TAB_HYBRID -> hybrids(graphics, queen, mouseX, mouseY);
            case GeneticLabMenu.TAB_STABILITY -> stability(graphics, queen, mouseX, mouseY);
            default -> {
                genome(graphics, queen, mouseX, mouseY, false);
                detail(graphics, queen);
                tools(queen);
            }
        }
        strip(graphics, queen);
    }

    /** The six page tabs along the top, each with its name; hovering one says what the page is for. */
    private void tabs() {
        int x = LEFT_X;
        for (int i = 0; i < PAGES.length; i++) {
            toggle(x, 3, TAB_WIDTHS[i] - 1, 14, Component.translatable("gui.antcolonies.lab.tab." + PAGES[i]).getString(),
                    BUTTON_PAGE - i, help(i), menu.tab() == i);
            x += TAB_WIDTHS[i];
        }
    }

    /** A page's full name and what it is for, a line of the lang file's text per tooltip line. */
    private List<Component> help(int page) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.antcolonies.lab.page." + PAGES[page]).withStyle(ChatFormatting.AQUA));
        for (String line : Component.translatable("gui.antcolonies.lab.page." + PAGES[page] + ".help").getString().split("\n")) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private void specimen(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        int center = LEFT_X + LEFT_W / 2;
        if (queen == null) {
            centered(graphics, fit(Component.translatable("gui.antcolonies.lab.no_queen").getString(), LEFT_W - 6), center, 66, TEXT_FAINT);
            centered(graphics, fit(Component.translatable("gui.antcolonies.lab.insert").getString(), LEFT_W - 6), center, 76, TEXT_FAINT);
        } else {
            Genome genome = queen.genome();
            centered(graphics, fit(genome.lineage().name(), LEFT_W - 6), center, 66, TEXT);
            AntLook look = ClientSpecies.look(queen.species());
            centered(graphics, fit(Tooltips.species(queen.species()).getString(), LEFT_W - 6), center, 76,
                    bright(look.crystal().orElse(look.gaster())));
            Component rarity = Tooltips.rarity(genome.rarity());
            centered(graphics, rarity.getString() + " " + String.format(Locale.ROOT, "%.1f", genome.averageLevel()), center, 86,
                    0xFF000000 | Tooltips.levelRgb((int) Math.round(genome.averageLevel())));
        }
        // Genetic Energy: a glass tube filling with light, bubbles rising. A status light beside its name pulses while the lab works.
        label(graphics, Component.translatable("gui.antcolonies.lab.energy_label"), LEFT_X + 4, 97, TEXT_DIM);
        int energy = menu.data(GeneticLabBlockEntity.DATA_ENERGY);
        int light = energy > 0 ? GREEN : RED;
        float pulse = idle() ? 1 : 0.6F + 0.4F * Mth.sin(time() * 0.3F);
        graphics.fill(leftPos + LEFT_X + LEFT_W - 10, topPos + 98, leftPos + LEFT_X + LEFT_W - 5, topPos + 103, scaleAlpha(light, pulse));
        int x0 = leftPos + LEFT_X + 4;
        int y0 = topPos + 107;
        int w = LEFT_W - 8;
        graphics.fill(x0, y0, x0 + w, y0 + 7, 0xFF06121A);
        int filled = (int) Math.round((w - 2) * Math.clamp(energy / (double) GeneticLabBlockEntity.energyCapacity(), 0, 1));
        if (filled > 0) {
            graphics.fillGradient(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 6, 0xFF7FF0FF, 0xFF1F8FB8);
            float t = time();
            for (int i = 0; i < 3; i++) {
                int bx = x0 + 1 + (int) ((t * 0.6F + i * 23) % Math.max(1, filled));
                graphics.fill(bx, y0 + 2 + i % 2, bx + 1, y0 + 3 + i % 2, 0xCCFFFFFF);
            }
        }
        graphics.outline(x0 - 1, y0 - 1, w + 2, 9, 0xFF2B6E86);
        if (energy > 0) {
            centered(graphics, fe(energy), LEFT_X + LEFT_W / 2, 116, TEXT_DIM);
        } else {
            centered(graphics, Component.translatable("gui.antcolonies.lab.offline").getString(), LEFT_X + LEFT_W / 2, 116, RED);
        }
    }

    // --- Shared pieces: rows, marks, text, cost cards ---

    private int rowY(int row) {
        return ROW_Y + row * ROW_H;
    }

    private boolean overRow(double mouseX, double mouseY, int row) {
        return over(mouseX, mouseY, LIST_X + 2, rowY(row) - 1, listW() - 10, ROW_H);
    }

    /** The band behind one list row: lit when chosen, faint under the mouse, striped otherwise. */
    private void rowBand(GuiGraphicsExtractor graphics, int row, boolean lit, boolean hovered) {
        int x0 = leftPos + LIST_X + 2;
        int x1 = leftPos + LIST_X + listW() - 8;
        int y0 = topPos + rowY(row) - 1;
        if (lit) {
            graphics.fill(x0, y0, x1, y0 + ROW_H, 0x553FB8E0);
            graphics.fill(x0, y0, x0 + 2, y0 + ROW_H, CYAN);
        } else if (hovered) {
            graphics.fill(x0, y0, x1, y0 + ROW_H, 0x22FFFFFF);
        } else if (row % 2 == 1) {
            graphics.fill(x0, y0, x1, y0 + ROW_H, 0x0CFFFFFF);
        }
    }

    /**
     * Keeps the open page's list scrolled and chosen within its {@code size}, and draws its scroll bar beside the
     * {@code rows} rows it shows from {@code firstRow} on.
     */
    private void listScroll(GuiGraphicsExtractor graphics, int size, int rows, int firstRow, int mouseX, int mouseY) {
        int page = menu.tab();
        int max = Math.max(0, size - rows);
        scroll[page] = Math.clamp(scroll[page], 0, max);
        chosen[page] = Math.clamp(chosen[page], 0, Math.max(0, size - 1));
        listBar.draw(graphics, leftPos + LIST_X + listW() - 7, topPos + rowY(firstRow) - 1, rows * ROW_H, scroll[page], max,
                size == 0 ? 1 : rows / (double) size, mouseX, mouseY);
    }

    /** A seven-pixel mark with its top-left corner at a panel position. */
    private void mark(GuiGraphicsExtractor graphics, Mark mark, int x, int y, int color) {
        int x0 = leftPos + x;
        int y0 = topPos + y;
        switch (mark) {
            case CHECK -> {
                int[] heights = {3, 4, 5, 4, 3, 2, 1};
                for (int i = 0; i < heights.length; i++) {
                    graphics.fill(x0 + i, y0 + heights[i], x0 + i + 1, y0 + heights[i] + 2, color);
                }
            }
            case CROSS -> {
                for (int i = 0; i < 6; i++) {
                    graphics.fill(x0 + i, y0 + 1 + i, x0 + i + 2, y0 + 2 + i, color);
                    graphics.fill(x0 + 5 - i, y0 + 1 + i, x0 + 7 - i, y0 + 2 + i, color);
                }
            }
            case LOCK -> {
                graphics.fill(x0 + 1, y0 + 3, x0 + 6, y0 + 7, color);
                graphics.fill(x0 + 2, y0, x0 + 5, y0 + 1, color);
                graphics.fill(x0 + 2, y0 + 1, x0 + 3, y0 + 3, color);
                graphics.fill(x0 + 4, y0 + 1, x0 + 5, y0 + 3, color);
            }
            case SHIELD -> {
                graphics.fill(x0 + 1, y0, x0 + 6, y0 + 4, color);
                graphics.fill(x0 + 2, y0 + 4, x0 + 5, y0 + 6, color);
                graphics.fill(x0 + 3, y0 + 6, x0 + 4, y0 + 7, color);
            }
            case DOT -> graphics.fill(x0 + 2, y0 + 2, x0 + 5, y0 + 5, color);
        }
    }

    /** One line of a checklist in the detail panel: a mark, then text cut to the panel. Returns the next line's y. */
    private int check(GuiGraphicsExtractor graphics, boolean ok, String text, int y) {
        mark(graphics, ok ? Mark.CHECK : Mark.CROSS, textX(), y, ok ? GREEN : RED);
        label(graphics, fit(text, textW() - 10), textX() + 10, y, ok ? TEXT : RED);
        return y + 9;
    }

    /** One line of the detail panel, cut to fit (whole on hover). Returns the next line's y. */
    private int line(GuiGraphicsExtractor graphics, String text, int y, int color) {
        label(graphics, fit(text, textW()), textX(), y, color);
        return y + 9;
    }

    /** A paragraph in the detail panel, wrapped, as far as the panel has room. Returns the y under it. */
    private int paragraph(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        for (String line : wrap(text.getString(), textW())) {
            if (y > LAST_LINE_Y) {
                break;
            }
            label(graphics, line, textX(), y, color);
            y += 9;
        }
        return y;
    }

    /** A note in the list panel, wrapped, from a row down: why the list is empty and what to do about it. */
    private void listNote(GuiGraphicsExtractor graphics, int row, String key, Object... args) {
        int y = rowY(row);
        for (String line : wrap(Component.translatable(key, args).getString(), listW() - 12)) {
            label(graphics, line, LIST_X + 6, y, TEXT_DIM);
            y += 10;
        }
    }

    private static ItemStack shown(Cost cost) {
        return cost.ingredient().items().findFirst().map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
    }

    private static String costText(List<Cost> costs) {
        StringBuilder text = new StringBuilder();
        for (Cost cost : costs) {
            text.append(text.isEmpty() ? "" : ", ").append(cost.count()).append("x ").append(shown(cost).getHoverName().getString());
        }
        return text.toString();
    }

    private int have(Cost cost, GeneticLabBlockEntity.Bay bay) {
        int found = 0;
        for (int i = 0; i < bay.slots; i++) {
            ItemStack stack = menu.getSlot(bay.first + i).getItem();
            if (cost.test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** The cost card: what an operation takes, set apart from the material slots under it. */
    private void costCard(GuiGraphicsExtractor graphics, List<Cost> costs, int energy, GeneticLabBlockEntity.Bay bay, int y) {
        graphics.fill(leftPos + detailX() + 3, topPos + y - 2, leftPos + detailX() + detailW() - 3, topPos + y + 17, 0x66000000);
        graphics.outline(leftPos + detailX() + 3, topPos + y - 2, detailW() - 6, 19, 0x553FB8E0);
        int cx = textX();
        for (Cost cost : costs) {
            graphics.item(shown(cost), leftPos + cx, topPos + y);
            String count = String.valueOf(cost.count());
            boolean enough = have(cost, bay) >= cost.count();
            graphics.nextStratum();
            graphics.text(font, count, leftPos + cx + 17 - font.width(count), topPos + y + 9, enough ? GREEN : RED, true);
            cx += 20;
        }
        // The energy it takes, with a small bolt.
        int bx = leftPos + cx + 2;
        int by = topPos + y + 3;
        boolean enough = menu.data(GeneticLabBlockEntity.DATA_ENERGY) >= energy;
        int bolt = enough ? CYAN : RED;
        graphics.fill(bx + 2, by, bx + 4, by + 4, bolt);
        graphics.fill(bx, by + 4, bx + 5, by + 5, bolt);
        graphics.fill(bx + 1, by + 5, bx + 3, by + 9, bolt);
        graphics.text(font, fe(energy).replace(" FE", ""), bx + 7, by + 1, enough ? TEXT : RED, false);
    }

    /** A row of what something takes, as item icons with their counts and the FE after them. Returns the y under the row. */
    private int costIcons(GuiGraphicsExtractor graphics, List<Cost> costs, int energy, int y) {
        int cx = textX() + 4;
        for (Cost cost : costs) {
            ItemStack stack = shown(cost);
            graphics.item(stack, leftPos + cx, topPos + y);
            String count = String.valueOf(cost.count());
            graphics.nextStratum();
            graphics.text(font, count, leftPos + cx + 17 - font.width(count), topPos + y + 9, TEXT, true);
            hoverText(cx, y, 16, 16, cost.count() + "x " + stack.getHoverName().getString());
            cx += 20;
        }
        label(graphics, fe(energy), cx + 4, y + 5, TEXT_DIM);
        return y + 18;
    }

    /** The tooltip of a cost card's icon under the mouse, if any. */
    private boolean costTooltip(GuiGraphicsExtractor graphics, List<Cost> costs, GeneticLabBlockEntity.Bay bay, int y, int mouseX, int mouseY) {
        for (int i = 0; i < costs.size(); i++) {
            if (over(mouseX, mouseY, textX() + i * 20, y, 16, 16)) {
                Cost cost = costs.get(i);
                int have = have(cost, bay);
                graphics.setComponentTooltipForNextFrame(font, List.of(shown(cost).getHoverName(),
                        Component.translatable("gui.antcolonies.lab.have", have, cost.count())
                                .withStyle(have >= cost.count() ? ChatFormatting.GREEN : ChatFormatting.RED),
                        Component.translatable("gui.antcolonies.lab.materials_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private static int tierColor(Research.Tier tier) {
        return switch (tier) {
            case BASIC -> GREEN;
            case ADVANCED -> CYAN;
            case EXPERIMENTAL -> MAGENTA;
        };
    }

    // --- Research: the scanner, and what it has found ---

    /** What this player (or their team) has researched: the lab lists nothing else. */
    private List<Researches.Entry> researched() {
        Knowledge knowledge = ClientResearch.view();
        return Researches.all(registries()).stream().filter(entry -> knowledge.knowsResearch(entry.id())).toList();
    }

    /** An entry's name in the list: just the species or the mutation (the detail panel has the full title). */
    private static String researchRow(Researches.Entry entry) {
        Research research = entry.research();
        return research.unlocksSpecies() ? Tooltips.species(research.species().get()).getString()
                : Component.translatable(Mutation.translationKey(research.mutation().get())).getString();
    }

    private void research(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Researches.Entry> entries = researched();
        listScroll(graphics, entries.size(), ROWS, 0, mouseX, mouseY);
        int page = GeneticLabMenu.TAB_RESEARCH;
        for (int row = 0; row < ROWS && scroll[page] + row < entries.size(); row++) {
            int index = scroll[page] + row;
            Researches.Entry entry = entries.get(index);
            rowBand(graphics, row, index == chosen[page], overRow(mouseX, mouseY, row));
            int y = rowY(row);
            // The tier as a chip before the name.
            graphics.fill(leftPos + LIST_X + 6, topPos + y + 1, leftPos + LIST_X + 10, topPos + y + 7, tierColor(entry.research().tier()));
            label(graphics, fit(researchRow(entry), listW() - 26), LIST_X + 14, y, TEXT);
        }
        if (entries.isEmpty()) {
            listNote(graphics, 0, "gui.antcolonies.lab.research.none");
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.research.intro"), 24, TEXT_DIM);
        } else {
            researchDetail(graphics, entries.get(chosen[page]));
        }
        scanner(graphics);
    }

    /** The scanner in the row under the panels: its slot, its button, and beside them what to do or what the scan found. */
    private void scanner(GuiGraphicsExtractor graphics) {
        ItemStack inScanner = menu.getSlot(GeneticLabBlockEntity.SLOT_SCAN).getItem();
        boolean enough = menu.data(GeneticLabBlockEntity.DATA_ENERGY) >= GeneticLabBlockEntity.SCAN_ENERGY;
        List<Component> tip = new ArrayList<>(List.of(Component.translatable("gui.antcolonies.lab.scan.hint"),
                Component.translatable("gui.antcolonies.lab.scan.keeps").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.antcolonies.lab.energy", fe(GeneticLabBlockEntity.SCAN_ENERGY))
                        .withStyle(enough ? ChatFormatting.DARK_AQUA : ChatFormatting.RED)));
        String scan = Component.translatable("gui.antcolonies.lab.scan").getString();
        if (inScanner.isEmpty()) {
            button(GeneticLabMenu.SCAN_X + 22, GeneticLabMenu.TOOLS_Y + 1, 38, 14, scan, -1, tip, false);
        } else {
            primary(GeneticLabMenu.SCAN_X + 22, GeneticLabMenu.TOOLS_Y + 1, 38, 14, scan, GeneticLabMenu.BUTTON_SCAN, tip);
        }
        showScanned(inScanner);
        Discovery.Outcome outcome = Discovery.Outcome.byId(menu.data(GeneticLabBlockEntity.DATA_SCAN));
        String message;
        int color;
        if (inScanner.isEmpty()) {
            message = Component.translatable("gui.antcolonies.lab.scan.empty").getString();
            color = TEXT_DIM;
        } else if (outcome == Discovery.Outcome.NONE) {
            message = Component.translatable("gui.antcolonies.lab.scan.ready", inScanner.getHoverName()).getString();
            color = TEXT;
        } else {
            message = Component.translatable("gui.antcolonies.lab.scan." + outcome.name().toLowerCase(Locale.ROOT), inScanner.getHoverName())
                    .getString();
            color = switch (outcome) {
                case NEW, SPECIES_NEW -> GOLD;
                case NO_POWER, LOCKED -> RED;
                default -> TEXT_DIM;
            };
        }
        label(graphics, fit(message, RIGHT_EDGE - 156), 156, GeneticLabMenu.TOOLS_Y + 5, color);
    }

    /** After a scan, the list jumps to the entry the scanned material belongs to (once per answer from the lab). */
    private void showScanned(ItemStack inScanner) {
        int outcome = menu.data(GeneticLabBlockEntity.DATA_SCAN);
        if (outcome == scanShown) {
            return;
        }
        scanShown = outcome;
        List<Researches.Entry> entries = researched();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).research().material().test(inScanner)) {
                chosen[GeneticLabMenu.TAB_RESEARCH] = i;
                scroll[GeneticLabMenu.TAB_RESEARCH] = Math.max(0, i - ROWS / 2);
                return;
            }
        }
    }

    /** One researched entry, in sentences: what was scanned, what that makes possible, and where in the lab it is done. */
    private void researchDetail(GuiGraphicsExtractor graphics, Researches.Entry entry) {
        Research research = entry.research();
        int y = 24;
        List<String> title = wrap(research.title().getString(), textW());
        for (int i = 0; i < Math.min(2, title.size()); i++) {
            y = line(graphics, title.get(i), y, CYAN);
        }
        boolean team = ClientResearch.owner(Knowledge.Kind.RESEARCH, entry.id()) == ClientResearch.Owner.TEAM;
        y = line(graphics, Component.translatable(team ? "gui.antcolonies.lab.research.team" : "gui.antcolonies.lab.research.personal",
                Component.translatable(research.tier().translationKey() + ".short")).getString(), y, tierColor(research.tier())) + 3;
        ItemStack material = research.material().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
        y = line(graphics, Component.translatable("gui.antcolonies.lab.research.scanned", material.getHoverName()).getString(), y, TEXT_DIM);
        if (research.unlocksSpecies()) {
            Identifier species = research.species().get();
            // Each Queen it can be made from, and under her what that takes.
            for (Holder.Reference<Evolution> holder : ModRegistries.lookup(registries(), ModRegistries.EVOLUTION)
                    .map(lookup -> lookup.listElements().toList()).orElse(List.of())) {
                Evolution evolution = holder.value();
                if (evolution.to().equals(species) && y + 27 <= LAST_LINE_Y + 9) {
                    List<Cost> costs = new ArrayList<>(List.of(new Cost(evolution.item(), evolution.labAmount())));
                    costs.addAll(research.catalysts());
                    y = line(graphics, Component.translatable("gui.antcolonies.lab.research.source", Tooltips.species(evolution.from()),
                            evolution.minStage()).getString(), y, TEXT);
                    y = costIcons(graphics, costs, research.energyCost(), y);
                }
            }
            if (y <= LAST_LINE_Y) {
                line(graphics, Component.translatable("gui.antcolonies.lab.research.on_mutation").getString(), y + 1, TEXT_FAINT);
            }
        } else {
            Identifier id = research.mutation().get();
            y = line(graphics, Component.translatable("gui.antcolonies.lab.research.gives").getString(), y, TEXT);
            for (Component effect : Tooltips.mutationEffects(registries(), id)) {
                y = paragraph(graphics, Component.literal(" ").append(effect), y, MAGENTA);
            }
            Mutation mutation = ModRegistries.lookup(registries(), ModRegistries.MUTATION)
                    .flatMap(lookup -> lookup.get(ResourceKey.create(ModRegistries.MUTATION, id))).map(Holder::value).orElse(null);
            if (mutation != null && mutation.catalyst().isPresent()) {
                paragraph(graphics, Component.translatable("gui.antcolonies.lab.research.on_editing", costText(List.of(mutation.catalyst().get()))),
                        y, TEXT_DIM);
            }
        }
    }

    // --- Analysis: what this Queen is, and what she can become ---

    /** One thing a Queen's species can turn into: a researched laboratory mutation, or an evolution by feeding. */
    private record Change(SpeciesMutation.@Nullable Option option, @Nullable Evolution natural) {
        Identifier target() {
            return option != null ? option.evolution().to() : natural.to();
        }
    }

    /** The mutations of a Queen's species that have been researched: the only ones the lab shows. */
    private List<SpeciesMutation.Option> options(@Nullable QueenData queen) {
        if (queen == null) {
            return List.of();
        }
        Knowledge knowledge = ClientResearch.view();
        return SpeciesMutation.options(registries(), queen.species()).stream()
                .filter(option -> knowledge.knowsResearch(option.research().id())).toList();
    }

    private List<Change> changes(QueenData queen) {
        List<Change> changes = new ArrayList<>();
        options(queen).forEach(option -> changes.add(new Change(option, null)));
        SpeciesMutation.natural(registries(), queen.species()).forEach(evolution -> changes.add(new Change(null, evolution)));
        return changes;
    }

    private void analysis(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY) {
        if (queen == null) {
            listNote(graphics, 0, "gui.antcolonies.lab.analysis.insert");
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.analysis.intro"), 24, TEXT_DIM);
            return;
        }
        Knowledge knowledge = ClientResearch.view();
        // Looking at her here is the analysis: it is recorded once per species.
        if (!knowledge.hasAnalysed(queen.species()) && !queen.species().equals(analysisAsked)) {
            analysisAsked = queen.species();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GeneticLabMenu.BUTTON_ANALYSE);
        }
        List<Change> changes = changes(queen);
        int page = GeneticLabMenu.TAB_ANALYSIS;
        // The first row is the Queen herself; under a heading, what her species can become.
        boolean herself = analysisQueen || changes.isEmpty();
        rowBand(graphics, 0, herself, overRow(mouseX, mouseY, 0));
        label(graphics, Component.translatable("gui.antcolonies.lab.analysis.genes"), LIST_X + 6, rowY(0), TEXT);
        int first = ANALYSIS_FIRST_ROW;
        listScroll(graphics, changes.size(), ROWS - first, first, mouseX, mouseY);
        label(graphics, Component.translatable("gui.antcolonies.lab.analysis.becomes"), LIST_X + 6, rowY(1), TEXT_DIM);
        for (int row = 0; row < ROWS - first && scroll[page] + row < changes.size(); row++) {
            int index = scroll[page] + row;
            Change change = changes.get(index);
            rowBand(graphics, row + first, !herself && index == chosen[page], overRow(mouseX, mouseY, row + first));
            int y = rowY(row + first);
            if (change.option() == null) {
                mark(graphics, Mark.DOT, LIST_X + 5, y, LEAF);
            } else {
                boolean ready = SpeciesMutation.missing(change.option(), knowledge, queen, dimension()).isEmpty();
                mark(graphics, ready ? Mark.CHECK : Mark.DOT, LIST_X + 5, y, ready ? GREEN : GOLD);
            }
            label(graphics, fit(Tooltips.species(change.target()).getString(), listW() - 28), LIST_X + 15, y, TEXT);
        }
        if (changes.isEmpty()) {
            listNote(graphics, first, "gui.antcolonies.lab.analysis.nothing");
        }
        if (herself) {
            genesDetail(graphics, queen);
        } else {
            changeDetail(graphics, changes.get(chosen[page]), queen, knowledge, 24);
        }
    }

    /** The Queen's own genes: how far she has grown, then each of her mutations with what it does. */
    private void genesDetail(GuiGraphicsExtractor graphics, QueenData queen) {
        int y = line(graphics, Component.translatable("gui.antcolonies.lab.analysis.growth", Math.max(1, queen.grown()),
                String.format(Locale.ROOT, "%.1f", queen.genome().averageLevel())).getString(), 24, TEXT) + 3;
        if (queen.genome().mutations().isEmpty()) {
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.analysis.no_mutations"), y, TEXT_DIM);
            return;
        }
        // Her strengths first, then her flaws.
        List<Identifier> mutations = new ArrayList<>(queen.genome().mutations());
        mutations.sort(Comparator.comparing(id -> registries() != null && Genetics.isFlaw(id, registries())));
        for (Identifier mutation : mutations) {
            if (y > LAST_LINE_Y) {
                break;
            }
            boolean flaw = registries() != null && Genetics.isFlaw(mutation, registries());
            y = line(graphics, Component.translatable(flaw ? "gui.antcolonies.lab.analysis.flaw" : "gui.antcolonies.lab.analysis.strength",
                    Component.translatable(Mutation.translationKey(mutation))).getString(), y, flaw ? RED : MAGENTA);
            for (Component effect : Tooltips.mutationEffects(registries(), mutation)) {
                y = paragraph(graphics, Component.literal(" ").append(effect), y, TEXT_DIM);
            }
            y += 2;
        }
    }

    private void changeDetail(GuiGraphicsExtractor graphics, Change change, QueenData queen, Knowledge knowledge, int y) {
        y = line(graphics, Tooltips.species(change.target()).getString(), y, CYAN);
        if (change.option() == null) {
            Evolution evolution = change.natural();
            ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            y = line(graphics, Component.translatable("gui.antcolonies.lab.analysis.natural").getString(), y, LEAF);
            y = paragraph(graphics, Component.translatable("gui.antcolonies.lab.analysis.natural.how", item.getHoverName(), evolution.minStage()),
                    y, TEXT_DIM);
            if (evolution.dimension().isPresent()) {
                paragraph(graphics, Component.translatable("tooltip.antcolonies.species.evolves_in", Tooltips.dimension(evolution.dimension().get())),
                        y, RED);
            }
            return;
        }
        SpeciesMutation.Option option = change.option();
        Set<SpeciesMutation.Missing> missing = SpeciesMutation.missing(option, knowledge, queen, dimension());
        y = check(graphics, !missing.contains(SpeciesMutation.Missing.QUEEN_GROWTH),
                Component.translatable("gui.antcolonies.lab.need.stage", option.evolution().minStage()).getString(), y);
        if (option.evolution().dimension().isPresent()) {
            y = check(graphics, !missing.contains(SpeciesMutation.Missing.DIMENSION), Component.translatable("gui.antcolonies.lab.need.dimension",
                    Tooltips.dimension(option.evolution().dimension().get())).getString(), y);
        }
        y = costIcons(graphics, option.costs(menu.lab().costMultiplier()), option.energy(menu.lab().costMultiplier()), y + 1) + 1;
        line(graphics, Component.translatable(missing.isEmpty() ? "gui.antcolonies.lab.need.ready" : "gui.antcolonies.lab.need.waits").getString(),
                y, missing.isEmpty() ? GREEN : GOLD);
    }

    // --- Species Mutation: turning a Queen into a researched species ---

    private SpeciesMutation.@Nullable Option chosenOption(@Nullable QueenData queen) {
        if (queen == null) {
            return null;
        }
        // The lab counts among all of her mutations; the list shows the researched ones.
        List<SpeciesMutation.Option> all = SpeciesMutation.options(registries(), queen.species());
        int index = menu.data(GeneticLabBlockEntity.DATA_MUTATION);
        SpeciesMutation.Option picked = index >= 0 && index < all.size() ? all.get(index) : null;
        List<SpeciesMutation.Option> shown = options(queen);
        for (SpeciesMutation.Option option : shown) {
            if (picked != null && option.id().equals(picked.id())) {
                return option;
            }
        }
        return shown.isEmpty() ? null : shown.getFirst();
    }

    private void mutation(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY) {
        // The arrow from the materials to the Queen's way out.
        int ax = leftPos + GeneticLabMenu.MATERIAL_X + 40;
        int ay = topPos + GeneticLabMenu.MATERIAL_Y + 7;
        graphics.fill(ax, ay, ax + 20, ay + 2, TEXT_FAINT);
        graphics.fill(ax + 18, ay - 2, ax + 20, ay + 4, TEXT_FAINT);
        graphics.fill(ax + 20, ay - 1, ax + 22, ay + 3, TEXT_FAINT);
        if (queen == null) {
            listNote(graphics, 0, "gui.antcolonies.lab.mutation.insert");
            return;
        }
        List<SpeciesMutation.Option> options = options(queen);
        if (options.isEmpty()) {
            listNote(graphics, 0, "gui.antcolonies.lab.mutation.none", Tooltips.species(queen.species()));
            return;
        }
        Knowledge knowledge = ClientResearch.view();
        SpeciesMutation.Option picked = chosenOption(queen);
        int page = GeneticLabMenu.TAB_MUTATION;
        listScroll(graphics, options.size(), ROWS, 0, mouseX, mouseY);
        for (int row = 0; row < ROWS && scroll[page] + row < options.size(); row++) {
            SpeciesMutation.Option option = options.get(scroll[page] + row);
            boolean ready = SpeciesMutation.missing(option, knowledge, queen, dimension()).isEmpty();
            rowBand(graphics, row, picked != null && option.id().equals(picked.id()), overRow(mouseX, mouseY, row) && idle());
            int y = rowY(row);
            mark(graphics, ready ? Mark.CHECK : Mark.DOT, LIST_X + 5, y, ready ? GREEN : GOLD);
            label(graphics, fit(Tooltips.species(option.evolution().to()).getString(), listW() - 28), LIST_X + 15, y, TEXT);
        }
        if (picked == null) {
            return;
        }
        Set<SpeciesMutation.Missing> missing = SpeciesMutation.missing(picked, knowledge, queen, dimension());
        int y = line(graphics, Tooltips.species(picked.evolution().to()).getString(), 24, CYAN) + 2;
        hoverText(textX(), y, textW(), 9, Component.translatable("gui.antcolonies.lab.need.stage.hint", picked.evolution().minStage(),
                Math.max(1, queen.grown())).getString());
        y = check(graphics, !missing.contains(SpeciesMutation.Missing.QUEEN_GROWTH),
                Component.translatable("gui.antcolonies.lab.need.stage", picked.evolution().minStage()).getString(), y);
        if (picked.evolution().dimension().isPresent()) {
            check(graphics, !missing.contains(SpeciesMutation.Missing.DIMENSION), Component.translatable("gui.antcolonies.lab.need.dimension",
                    Tooltips.dimension(picked.evolution().dimension().get())).getString(), y);
        }
        costCard(graphics, picked.costs(menu.lab().costMultiplier()), picked.energy(menu.lab().costMultiplier()),
                GeneticLabBlockEntity.Bay.MUTATION, 68);
        int by = 114;
        if (!idle()) {
            button(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.cancel_button").getString(),
                    GeneticLabMenu.BUTTON_CANCEL, List.of(Component.translatable("gui.antcolonies.lab.cancel")), true);
        } else if (!missing.isEmpty()) {
            String why = missing.contains(SpeciesMutation.Missing.QUEEN_GROWTH) ? "queen_not_grown" : "wrong_dimension";
            button(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.mutation.blocked").getString(), -1,
                    List.of(Component.translatable("status.antcolonies." + why).withStyle(ChatFormatting.RED)), false);
        } else {
            primary(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.mutation.start").getString(),
                    GeneticLabMenu.BUTTON_TRANSFORM, List.of(
                            Component.translatable("gui.antcolonies.lab.mutation.start.hint", Tooltips.species(picked.evolution().to())),
                            Component.translatable("gui.antcolonies.lab.mutation.keeps").withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.antcolonies.lab.time", picked.research().research().ticks() / 20)
                                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** Chooses a mutation of the list: the lab is told its place among all of her mutations, researched or not. */
    private void pickMutation(QueenData queen, int row) {
        List<SpeciesMutation.Option> shown = options(queen);
        int index = scroll[GeneticLabMenu.TAB_MUTATION] + row;
        if (index < shown.size()) {
            List<Identifier> all = SpeciesMutation.options(registries(), queen.species()).stream().map(SpeciesMutation.Option::id).toList();
            onButton(GeneticLabMenu.BUTTON_MUTATION + all.indexOf(shown.get(index).id()));
        }
    }

    // --- Genetic Editing: the Queen's traits, one at a time ---

    /** The ten traits as rows. On the Stabilization page they show their locks, and the chosen gene is the lit row. */
    private void genome(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY, boolean locks) {
        int max = Trait.maxLevel();
        int gene = menu.data(GeneticLabBlockEntity.DATA_GENE);
        for (Trait trait : Trait.values()) {
            int row = trait.ordinal();
            int y = rowY(row);
            boolean lit = locks ? gene == row : trait == selected();
            int x0 = leftPos + LIST_X + 2;
            int x1 = leftPos + LIST_X + listW() - 2;
            if (lit) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x553FB8E0);
                graphics.fill(x0, topPos + y - 1, x0 + 2, topPos + y + ROW_H - 1, CYAN);
            } else if (overTrait(mouseX, mouseY, row) && queen != null && idle()) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x22FFFFFF);
            } else if (row % 2 == 1) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x0CFFFFFF);
            }
            int level = queen == null ? 0 : queen.genome().level(trait);
            label(graphics, fit(Component.translatable(trait.translationKey() + ".short").getString(), 60), LIST_X + 6, y,
                    lit ? 0xFFFFFFFF : queen == null ? TEXT_FAINT : TEXT);
            for (int i = 0; i < max; i++) {
                int px = leftPos + LIST_X + 70 + i * 4;
                graphics.fill(px, topPos + y + 1, px + 3, topPos + y + 7, i < level ? 0xFF000000 | Tooltips.levelRgb(level) : PIP_EMPTY);
            }
            if (locks) {
                boolean locked = queen != null && queen.genome().locked(trait);
                mark(graphics, Mark.LOCK, LIST_X + listW() - 13, y, locked ? GOLD : PIP_EMPTY);
            } else {
                String number = queen == null ? "-" : String.valueOf(level);
                graphics.text(font, number, leftPos + LIST_X + listW() - 6 - font.width(number), topPos + y, level >= max ? GOLD : TEXT_DIM, false);
            }
        }
    }

    /** Whether the mouse is over a trait's row (the Editing and Stabilization pages: full-width rows, no scroll bar). */
    private boolean overTrait(double mouseX, double mouseY, int row) {
        return over(mouseX, mouseY, LIST_X + 2, rowY(row) - 1, listW() - 4, ROW_H);
    }

    private void detail(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        Trait trait = selected();
        int x = textX();
        List<String> name = wrap(Component.translatable(trait.translationKey()).getString(), textW());
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            label(graphics, name.get(i), x, 24 + i * 9, CYAN);
        }
        if (queen == null) {
            return;
        }
        int level = queen.genome().level(trait);
        int max = Trait.maxLevel();
        boolean maxed = level >= max;
        String now = String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level));
        if (maxed) {
            label(graphics, Component.translatable("gui.antcolonies.lab.level_short", level).getString() + "  " + now, x, 45, GOLD);
            label(graphics, Component.translatable(Trait.gradeKey(level)), x, 55, GOLD);
        } else {
            String next = String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level + 1));
            label(graphics, Component.translatable("gui.antcolonies.lab.level_short", level).getString(), x, 45, TEXT);
            label(graphics, ">", x + 34, 45, GREEN);
            label(graphics, String.valueOf(level + 1), x + 42, 45, GREEN);
            label(graphics, now, x, 55, TEXT_DIM);
            label(graphics, ">", x + 34, 55, GREEN);
            label(graphics, next, x + 42, 55, GREEN);
            label(graphics, fit(Component.translatable(Trait.gradeKey(level + 1)).getString(), textW()), x, 65, TEXT_DIM);
            GeneticLabBlockEntity.Requirement requirement = requirement(queen);
            if (requirement != null) {
                costCard(graphics, requirement.materials(), requirement.energy(), GeneticLabBlockEntity.Bay.EDITING, 75);
            }
        }
        if (!idle()) {
            button(detailX() + 4, 114, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.cancel_button").getString(),
                    GeneticLabMenu.BUTTON_CANCEL, List.of(Component.translatable("gui.antcolonies.lab.cancel")), true);
        } else if (maxed) {
            button(detailX() + 4, 114, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.max_button").getString(), -1, null, false);
        } else {
            primary(detailX() + 4, 114, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.upgrade").getString(),
                    GeneticLabMenu.BUTTON_UPGRADE, upgradeTooltip(queen, trait));
        }
    }

    private GeneticLabBlockEntity.@Nullable Requirement requirement(QueenData queen) {
        return minecraft.level == null ? null
                : menu.lab().requirement(GeneticLabBlockEntity.Operation.UPGRADE, selected(), queen, minecraft.level.registryAccess());
    }

    /** The row under the panels: upgrades (they need no buttons), the catalyst slot and the lab's tools. */
    private void tools(@Nullable QueenData queen) {
        boolean ready = idle() && queen != null;
        int y = GeneticLabMenu.TOOLS_Y + 1;
        String[][] tools = {{"reroll", String.valueOf(GeneticLabMenu.BUTTON_REROLL)}, {"mutate", String.valueOf(GeneticLabMenu.BUTTON_MUTATE)},
                {"purge", String.valueOf(GeneticLabMenu.BUTTON_PURGE)}, {"sample", String.valueOf(GeneticLabMenu.BUTTON_SAMPLE)}};
        for (int i = 0; i < tools.length; i++) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.antcolonies.lab." + tools[i][0] + ".hint"));
            if (tools[i][0].equals("mutate")) {
                if (queen != null && registries() != null && !Genetics.roomForBeneficial(queen.genome().mutations(), registries())) {
                    tooltip.add(Component.translatable("gui.antcolonies.lab.mutation_cap",
                            AntConfig.get(AntConfig.MAX_BENEFICIAL_MUTATIONS)).withStyle(ChatFormatting.RED));
                }
                Component gives = catalystMutation(queen);
                if (gives != null) {
                    tooltip.add(Component.translatable("gui.antcolonies.lab.catalyst_gives", gives).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            button(TOOL_BUTTON_X + i * (TOOL_BUTTON_W + 4), y, TOOL_BUTTON_W, 14,
                    Component.translatable("gui.antcolonies.lab." + tools[i][0]).getString(), Integer.parseInt(tools[i][1]), tooltip, ready);
        }
    }

    // --- Hybridization: which species cross, and making the Genetic Sample for it ---

    /**
     * The crosses whose two parent species have both been met, the ones this Queen can be a parent of first.
     * A cross with an unknown parent is not listed: the book hints at those.
     */
    private List<Hybrid> crosses(@Nullable QueenData queen) {
        Knowledge knowledge = ClientResearch.view();
        List<Hybrid> list = new ArrayList<>();
        ModRegistries.lookup(registries(), ModRegistries.HYBRID).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            Hybrid hybrid = holder.value();
            if (knowledge.knowsSpecies(hybrid.first()) && knowledge.knowsSpecies(hybrid.second())) {
                list.add(hybrid);
            }
        }));
        list.sort(Comparator.<Hybrid, Integer>comparing(hybrid -> queen != null && parent(hybrid, queen.species()) ? 0 : 1)
                .thenComparing(hybrid -> hybrid.result().toString()));
        return list;
    }

    private static boolean parent(Hybrid hybrid, Identifier species) {
        return hybrid.first().equals(species) || hybrid.second().equals(species);
    }

    private static Identifier partner(Hybrid hybrid, Identifier species) {
        return hybrid.first().equals(species) ? hybrid.second() : hybrid.first();
    }

    private void hybrids(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY) {
        List<Hybrid> crosses = crosses(queen);
        int page = GeneticLabMenu.TAB_HYBRID;
        listScroll(graphics, crosses.size(), ROWS - 1, 1, mouseX, mouseY);
        label(graphics, fit(Component.translatable("gui.antcolonies.lab.hybrid.list").getString(), listW() - 12), LIST_X + 6, rowY(0), TEXT_DIM);
        for (int row = 0; row < ROWS - 1 && scroll[page] + row < crosses.size(); row++) {
            int index = scroll[page] + row;
            Hybrid hybrid = crosses.get(index);
            boolean hers = queen != null && parent(hybrid, queen.species());
            rowBand(graphics, row + 1, index == chosen[page], overRow(mouseX, mouseY, row + 1));
            int y = rowY(row + 1);
            // Her own crosses are marked; the row names the hybrid, the detail panel its parents.
            mark(graphics, Mark.DOT, LIST_X + 5, y, hers ? MAGENTA : PIP_EMPTY);
            label(graphics, fit(Tooltips.species(hybrid.result()).getString(), listW() - 28), LIST_X + 15, y, hers ? TEXT : TEXT_DIM);
        }
        if (crosses.isEmpty()) {
            listNote(graphics, 1, "gui.antcolonies.lab.hybrid.none");
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.hybrid.intro"), 24, TEXT_DIM);
        } else {
            crossDetail(graphics, crosses.get(chosen[page]), queen);
        }
        // Under the panels: an empty tube in, her copied genes out.
        int ax = leftPos + GeneticLabMenu.TUBE_X + 19;
        int ay = topPos + GeneticLabMenu.TOOLS_Y + 7;
        graphics.fill(ax, ay, ax + 14, ay + 2, TEXT_FAINT);
        graphics.fill(ax + 14, ay - 2, ax + 16, ay + 4, TEXT_FAINT);
        graphics.fill(ax + 16, ay - 1, ax + 18, ay + 3, TEXT_FAINT);
        List<Component> tip = List.of(Component.translatable("gui.antcolonies.lab.hybrid.copy.hint"),
                Component.translatable("gui.antcolonies.lab.hybrid.copy.cost").withStyle(ChatFormatting.GRAY));
        String copy = Component.translatable("gui.antcolonies.lab.hybrid.copy").getString();
        if (!idle() && operation() == GeneticLabBlockEntity.Operation.PREPARE_SAMPLE) {
            button(154, GeneticLabMenu.TOOLS_Y + 1, 64, 14, Component.translatable("gui.antcolonies.lab.cancel_button").getString(),
                    GeneticLabMenu.BUTTON_CANCEL, List.of(Component.translatable("gui.antcolonies.lab.cancel")), true);
        } else if (idle() && queen != null && menu.getSlot(GeneticLabBlockEntity.SLOT_TUBE).hasItem()) {
            primary(154, GeneticLabMenu.TOOLS_Y + 1, 64, 14, copy, GeneticLabMenu.BUTTON_PREPARE_SAMPLE, tip);
        } else {
            button(154, GeneticLabMenu.TOOLS_Y + 1, 64, 14, copy, -1, tip, false);
        }
        // Beside the button: the next thing to do.
        String step = Component.translatable(queen == null ? "gui.antcolonies.lab.hybrid.step.queen"
                : menu.getSlot(GeneticLabBlockEntity.SLOT_SAMPLE_OUTPUT).hasItem() ? "gui.antcolonies.lab.hybrid.step.take"
                : menu.getSlot(GeneticLabBlockEntity.SLOT_TUBE).hasItem() ? "gui.antcolonies.lab.hybrid.step.copy"
                : "gui.antcolonies.lab.hybrid.step.tube").getString();
        label(graphics, fit(step, RIGHT_EDGE - 224), 224, GeneticLabMenu.TOOLS_Y + 5, TEXT_DIM);
    }

    /** One cross, in sentences: what it gives, from whom, how likely, and what to do with the Queen in the lab. */
    private void crossDetail(GuiGraphicsExtractor graphics, Hybrid hybrid, @Nullable QueenData queen) {
        int y = 24;
        List<String> title = wrap(Tooltips.species(hybrid.result()).getString(), textW());
        for (int i = 0; i < Math.min(2, title.size()); i++) {
            y = line(graphics, title.get(i), y, MAGENTA);
        }
        y = paragraph(graphics, Component.translatable("gui.antcolonies.lab.hybrid.parents", Tooltips.species(hybrid.first()),
                Tooltips.species(hybrid.second())), y, TEXT);
        double chance = Math.min(1, hybrid.chance() * AntConfig.get(AntConfig.HYBRID_CHANCE_MULTIPLIER));
        String level = String.format(Locale.ROOT, "%.1f", hybrid.minAverageLevel());
        y = paragraph(graphics, Component.translatable("gui.antcolonies.lab.hybrid.odds", Math.round(chance * 100), level), y, TEXT_DIM);
        if (queen != null && parent(hybrid, queen.species())) {
            hoverText(textX(), y, textW(), 9, Component.translatable("gui.antcolonies.lab.hybrid.queen.hint", level).getString());
            String hers = String.format(Locale.ROOT, "%.1f", queen.genome().averageLevel());
            if (queen.genome().averageLevel() >= hybrid.minAverageLevel()) {
                y = check(graphics, true, Component.translatable("gui.antcolonies.lab.hybrid.queen", hers).getString(), y);
            } else {
                y = line(graphics, Component.translatable("gui.antcolonies.lab.hybrid.queen.low", hers).getString(), y, GOLD);
            }
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.hybrid.how", Tooltips.species(partner(hybrid, queen.species()))), y + 2,
                    TEXT_DIM);
        } else {
            paragraph(graphics, Component.translatable("gui.antcolonies.lab.hybrid.other"), y + 2, TEXT_FAINT);
        }
    }

    // --- Stabilization: locking traits and protecting mutations ---

    private void stability(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY) {
        genome(graphics, queen, mouseX, mouseY, true);
        // What the page is for, always in view in the free row under the panels.
        List<String> purpose = wrap(Component.translatable("gui.antcolonies.lab.stability.purpose").getString(), RIGHT_EDGE - PURPOSE_X);
        for (int i = 0; i < Math.min(2, purpose.size()); i++) {
            label(graphics, purpose.get(i), PURPOSE_X, GeneticLabMenu.TOOLS_Y + i * 9, TEXT);
        }
        double stability = queen == null ? 0 : queen.genome().stability();
        label(graphics, fit(Component.translatable("gui.antcolonies.lab.stability", Math.round(stability * 100)).getString(), textW()), textX(), 24,
                stability >= 1 ? GOLD : CYAN);
        drawBar(graphics, leftPos + textX(), topPos + 35, textW(), 5, stability, stability >= 1 ? GOLD : CYAN);
        if (queen == null || registries() == null) {
            return;
        }
        int gene = menu.data(GeneticLabBlockEntity.DATA_GENE);
        // Her beneficial mutations, each with its shield.
        List<Identifier> mutations = GeneticLabBlockEntity.beneficial(queen.genome(), registries());
        for (int i = 0; i < Math.min(3, mutations.size()); i++) {
            int y = 44 + i * 10;
            boolean lit = gene == Trait.values().length + i;
            if (lit) {
                graphics.fill(leftPos + detailX() + 3, topPos + y - 1, leftPos + detailX() + detailW() - 3, topPos + y + 9, 0x553FB8E0);
            }
            mark(graphics, Mark.SHIELD, textX(), y, queen.genome().protects(mutations.get(i)) ? GOLD : PIP_EMPTY);
            hoverText(detailX() + 3, y - 1, detailW() - 6, 10, Component.translatable(Mutation.translationKey(mutations.get(i))).getString() + ": "
                    + Tooltips.mutationEffectLine(registries(), mutations.get(i)));
            label(graphics, fit(Component.translatable(Mutation.translationKey(mutations.get(i))).getString(), textW() - 10), textX() + 10, y,
                    lit ? 0xFFFFFFFF : MAGENTA);
        }
        if (mutations.isEmpty()) {
            label(graphics, fit(Component.translatable("gui.antcolonies.lab.stability.no_mutations").getString(), textW()), textX(), 44, TEXT_FAINT);
        }
        boolean exists = GeneticLabBlockEntity.geneExists(gene, queen.genome(), registries());
        boolean held = exists && GeneticLabBlockEntity.geneStabilized(gene, queen.genome(), registries());
        if (exists && !held) {
            costCard(graphics, stabilizeCosts(gene), stabilizeEnergy(gene), GeneticLabBlockEntity.Bay.STABILIZATION, 77);
        } else if (held) {
            label(graphics, fit(Component.translatable("gui.antcolonies.lab.stability.held").getString(), textW()), textX(), 80, GOLD);
        }
        int by = 114;
        if (!idle()) {
            button(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.cancel_button").getString(),
                    GeneticLabMenu.BUTTON_CANCEL, List.of(Component.translatable("gui.antcolonies.lab.cancel")), true);
        } else if (held) {
            button(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.stability.release").getString(),
                    GeneticLabMenu.BUTTON_RELEASE, List.of(Component.translatable("gui.antcolonies.lab.stability.release.hint")), true);
        } else {
            primary(detailX() + 4, by, detailW() - 8, 11, Component.translatable("gui.antcolonies.lab.stability.start").getString(),
                    GeneticLabMenu.BUTTON_STABILIZE, List.of(Component.translatable(gene < Trait.values().length
                            ? "gui.antcolonies.lab.stability.trait.hint" : "gui.antcolonies.lab.stability.mutation.hint"),
                            Component.translatable("gui.antcolonies.lab.stability.optional").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** What Stabilize does to the chosen trait or mutation, or what it has done, for the strip. */
    private Component stabilityLine(QueenData queen) {
        int gene = menu.data(GeneticLabBlockEntity.DATA_GENE);
        if (registries() == null || !GeneticLabBlockEntity.geneExists(gene, queen.genome(), registries())) {
            return Component.translatable("gui.antcolonies.lab.stability.pick");
        }
        String state = GeneticLabBlockEntity.geneStabilized(gene, queen.genome(), registries()) ? "held" : "will";
        Identifier mutation = GeneticLabBlockEntity.geneMutation(gene, queen.genome(), registries());
        if (mutation != null) {
            return Component.translatable("gui.antcolonies.lab.stability.mutation." + state, Component.translatable(Mutation.translationKey(mutation)),
                    Tooltips.mutationEffectLine(registries(), mutation));
        }
        Trait trait = Trait.values()[gene];
        return Component.translatable("gui.antcolonies.lab.stability.trait." + state, Component.translatable(trait.translationKey() + ".short"),
                queen.genome().level(trait));
    }

    private List<Cost> stabilizeCosts(int gene) {
        int jelly = gene < Trait.values().length ? GeneticLabBlockEntity.STABILIZE_TRAIT_JELLY : GeneticLabBlockEntity.STABILIZE_MUTATION_JELLY;
        Cost cost = new Cost(Ingredient.of(ModItems.ROYAL_JELLY.get()), jelly);
        return List.of(new Cost(cost.ingredient(), cost.scaled(menu.lab().costMultiplier())));
    }

    private int stabilizeEnergy(int gene) {
        int energy = gene < Trait.values().length ? GeneticLabBlockEntity.STABILIZE_TRAIT_ENERGY : GeneticLabBlockEntity.STABILIZE_MUTATION_ENERGY;
        return (int) Math.ceil(energy * menu.lab().costMultiplier());
    }

    // --- The strip at the bottom ---

    /** The work in progress as a running helix, or what is wrong, or what this page is for; the "?" at its end explains the page in full. */
    private void strip(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        int x0 = leftPos + LEFT_X + 2;
        int x1 = leftPos + imageWidth - LEFT_X - 14;
        int y0 = topPos + STRIP_Y;
        int page = menu.tab();
        button(imageWidth - LEFT_X - 12, STRIP_Y, 10, 9, "?", BUTTON_HELP, help(page), true);
        if (!idle()) {
            int time = Math.max(1, menu.data(GeneticLabBlockEntity.DATA_TIME));
            double progress = Math.clamp(menu.data(GeneticLabBlockEntity.DATA_PROGRESS) / (double) time, 0, 1);
            int filled = (int) ((x1 - x0) * progress);
            graphics.fill(x0, y0 + 1, x0 + filled, y0 + 8, 0x40B04FE0);
            float t = time();
            for (int x = x0; x < x0 + filled; x += 2) {
                float wave = Mth.sin((x - x0) * 0.25F - t * 0.4F);
                graphics.fill(x, y0 + 4 + Math.round(wave * 2.5F), x + 1, y0 + 5 + Math.round(wave * 2.5F), CYAN);
                graphics.fill(x, y0 + 4 - Math.round(wave * 2.5F), x + 1, y0 + 5 - Math.round(wave * 2.5F), MAGENTA);
            }
            GeneticLabBlockEntity.Operation operation = operation();
            Component text = Component.translatable("gui.antcolonies.lab.working." + operation.name().toLowerCase(Locale.ROOT),
                    workingOn(operation, queen), Math.round(progress * 100));
            centeredAt(graphics, fit(text.getString(), x1 - x0 - 8), (x0 + x1) / 2, y0 + 1, 0xFFFFFFFF);
            return;
        }
        Component message;
        int color;
        int status = menu.data(GeneticLabBlockEntity.DATA_STATUS);
        ColonyStatus problem = ColonyStatus.of(status).stream().filter(ColonyStatus::problem).findFirst().orElse(null);
        boolean needsQueen = page != GeneticLabMenu.TAB_RESEARCH && page != GeneticLabMenu.TAB_HYBRID;
        if (queen == null && needsQueen) {
            message = Component.translatable("gui.antcolonies.lab.insert_queen");
            color = TEXT_DIM;
        } else if (problem != null && needsQueen) {
            message = Component.translatable(problem.translationKey());
            color = RED;
        } else if (page == GeneticLabMenu.TAB_EDITING) {
            // The selected trait in a few words.
            message = Component.translatable("gui.antcolonies.lab.trait_hint", Component.translatable(selected().translationKey()),
                    Component.translatable(selected().translationKey() + ".desc"));
            color = TEXT_DIM;
        } else if (page == GeneticLabMenu.TAB_STABILITY && queen != null) {
            message = stabilityLine(queen);
            color = TEXT_DIM;
        } else {
            message = Component.translatable("gui.antcolonies.lab.page." + PAGES[page] + ".strip");
            color = TEXT_DIM;
        }
        centeredAt(graphics, fit(message.getString(), x1 - x0 - 8), (x0 + x1) / 2, y0 + 1, color);
    }

    /** What the running operation works on, for its line in the strip: a trait, a species or a gene. */
    private Component workingOn(GeneticLabBlockEntity.Operation operation, @Nullable QueenData queen) {
        if (operation == GeneticLabBlockEntity.Operation.TRANSFORM) {
            SpeciesMutation.Option option = chosenOption(queen);
            return option == null ? Component.empty() : Tooltips.species(option.evolution().to());
        }
        if (operation == GeneticLabBlockEntity.Operation.STABILIZE && queen != null && registries() != null) {
            int gene = menu.data(GeneticLabBlockEntity.DATA_GENE);
            Identifier mutation = GeneticLabBlockEntity.geneMutation(gene, queen.genome(), registries());
            return mutation != null ? Component.translatable(Mutation.translationKey(mutation))
                    : Component.translatable(Trait.values()[Math.clamp(gene, 0, Trait.values().length - 1)].translationKey());
        }
        return Component.translatable(selected().translationKey());
    }

    // --- Text helpers ---

    private void centered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        graphics.text(font, text, leftPos + centerX - font.width(text) / 2, topPos + y, color, false);
        noteDrawn(text, leftPos + centerX - font.width(text) / 2, topPos + y);
    }

    /** Text centered on a screen x (absolute coordinates), whole on hover when cut. */
    private void centeredAt(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
        noteDrawn(text, centerX - font.width(text) / 2, y);
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** A species color made light enough to read on the dark glass. */
    private static int bright(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        float factor = max < 170 ? 170F / Math.max(1, max) : 1;
        return 0xFF000000 | Math.min(255, (int) (r * factor)) << 16 | Math.min(255, (int) (g * factor)) << 8 | Math.min(255, (int) (b * factor));
    }

    private List<Component> upgradeTooltip(QueenData queen, Trait trait) {
        List<Component> lines = new ArrayList<>();
        int level = queen.genome().level(trait);
        lines.add(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.antcolonies.lab.next", level + 1, Component.translatable(Trait.gradeKey(level + 1)),
                String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level + 1))).withStyle(ChatFormatting.GREEN));
        GeneticLabBlockEntity.Requirement requirement = requirement(queen);
        if (requirement != null) {
            for (Cost cost : requirement.materials()) {
                int have = have(cost, GeneticLabBlockEntity.Bay.EDITING);
                lines.add(Component.literal(have + " / " + cost.count() + " ").append(shown(cost).getHoverName())
                        .withStyle(have >= cost.count() ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
            lines.add(Component.translatable("gui.antcolonies.lab.energy", fe(requirement.energy())).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable("gui.antcolonies.lab.time", requirement.time() / 20).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private @Nullable Component catalystMutation(@Nullable QueenData queen) {
        ItemStack catalyst = menu.getSlot(GeneticLabBlockEntity.SLOT_CATALYST).getItem();
        var registries = registries();
        if (queen == null || catalyst.isEmpty() || registries == null) {
            return null;
        }
        var lookup = registries.lookup(ModRegistries.MUTATION);
        if (lookup.isEmpty()) {
            return null;
        }
        for (var entry : lookup.get().listElements().toList()) {
            Mutation mutation = entry.value();
            if (!mutation.negative() && mutation.catalyst().isPresent() && mutation.catalyst().get().test(catalyst)
                    && !queen.genome().mutations().contains(entry.key().identifier())) {
                String effects = Tooltips.mutationEffectLine(registries, entry.key().identifier());
                return Component.translatable(Mutation.translationKey(entry.key().identifier())).append(effects.isEmpty() ? "" : " (" + effects + ")");
            }
        }
        return null;
    }

    // --- Clicks, scrolling and tooltips ---

    @Override
    protected boolean clickPage(double mouseX, double mouseY) {
        QueenData queen = queen();
        int page = menu.tab();
        if (listBar.press(mouseX, mouseY)) {
            scroll[page] = listBar.offsetAt(mouseY);
            return true;
        }
        boolean traits = page == GeneticLabMenu.TAB_EDITING || page == GeneticLabMenu.TAB_STABILITY;
        for (int row = 0; row < ROWS; row++) {
            if (traits ? !overTrait(mouseX, mouseY, row) : !overRow(mouseX, mouseY, row)) {
                continue;
            }
            switch (page) {
                case GeneticLabMenu.TAB_RESEARCH -> choose(page, row, researched().size());
                case GeneticLabMenu.TAB_ANALYSIS -> {
                    if (queen != null && row == 0) {
                        analysisQueen = true;
                        clickSound();
                    } else if (queen != null && row >= ANALYSIS_FIRST_ROW && scroll[page] + row - ANALYSIS_FIRST_ROW < changes(queen).size()) {
                        analysisQueen = false;
                        choose(page, row - ANALYSIS_FIRST_ROW, changes(queen).size());
                    }
                }
                case GeneticLabMenu.TAB_MUTATION -> {
                    if (idle() && queen != null) {
                        pickMutation(queen, row);
                    }
                }
                case GeneticLabMenu.TAB_HYBRID -> {
                    if (row >= 1) {
                        choose(page, row - 1, crosses(queen).size());
                    }
                }
                case GeneticLabMenu.TAB_STABILITY -> {
                    if (idle() && queen != null) {
                        onButton(GeneticLabMenu.BUTTON_GENE + row);
                    }
                }
                default -> {
                    if (idle() && queen != null) {
                        onButton(row);
                    }
                }
            }
            return true;
        }
        if (page == GeneticLabMenu.TAB_STABILITY && idle() && queen != null && registries() != null) {
            int mutations = Math.min(3, GeneticLabBlockEntity.beneficial(queen.genome(), registries()).size());
            for (int i = 0; i < mutations; i++) {
                if (over(mouseX, mouseY, detailX() + 3, 43 + i * 10, detailW() - 6, 10)) {
                    onButton(GeneticLabMenu.BUTTON_GENE + Trait.values().length + i);
                    return true;
                }
            }
        }
        return false;
    }

    /** Chooses a row of a page's own list (nothing is sent: the choice only changes what the detail panel shows). */
    private void choose(int page, int row, int size) {
        if (scroll[page] + row < size) {
            chosen[page] = scroll[page] + row;
            clickSound();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (listBar.dragging()) {
            scroll[menu.tab()] = listBar.offsetAt(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        listBar.release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (over(mouseX, mouseY, LIST_X, PANEL_Y, listW(), PANEL_H)) {
            scroll[menu.tab()] -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        QueenData queen = queen();
        int page = menu.tab();
        if (page == GeneticLabMenu.TAB_EDITING || page == GeneticLabMenu.TAB_STABILITY) {
            for (Trait trait : Trait.values()) {
                if (overTrait(mouseX, mouseY, trait.ordinal())) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.AQUA));
                    lines.add(Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
                    if (queen != null) {
                        int level = queen.genome().level(trait);
                        lines.add(Component.translatable("gui.antcolonies.lab.level", level, Component.translatable(Trait.gradeKey(level)),
                                String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level))).withStyle(Tooltips.levelColor(level)));
                        if (queen.genome().locked(trait)) {
                            lines.add(Component.translatable("gui.antcolonies.lab.stability.locked").withStyle(ChatFormatting.GOLD));
                        }
                    }
                    graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                    return;
                }
            }
        }
        if (queen != null && !queen.genome().mutations().isEmpty() && over(mouseX, mouseY, LEFT_X, 62, LEFT_W, 34)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.antcolonies.mutations").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (Identifier mutation : queen.genome().mutations()) {
                boolean flaw = registries() != null && Genetics.isFlaw(mutation, registries());
                lines.add(Component.translatable(Mutation.translationKey(mutation)).withStyle(flaw ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
                Tooltips.mutationEffects(registries(), mutation)
                        .forEach(effect -> lines.add(Component.literal(" ").append(effect).withStyle(ChatFormatting.GRAY)));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (page == GeneticLabMenu.TAB_EDITING && queen != null && queen.genome().level(selected()) < Trait.maxLevel()) {
            GeneticLabBlockEntity.Requirement requirement = requirement(queen);
            if (requirement != null && costTooltip(graphics, requirement.materials(), GeneticLabBlockEntity.Bay.EDITING, 75, mouseX, mouseY)) {
                return;
            }
        }
        if (page == GeneticLabMenu.TAB_MUTATION && queen != null) {
            SpeciesMutation.Option picked = chosenOption(queen);
            if (picked != null && costTooltip(graphics, picked.costs(menu.lab().costMultiplier()), GeneticLabBlockEntity.Bay.MUTATION, 68,
                    mouseX, mouseY)) {
                return;
            }
        }
        if (page == GeneticLabMenu.TAB_STABILITY && queen != null && registries() != null) {
            int gene = menu.data(GeneticLabBlockEntity.DATA_GENE);
            if (GeneticLabBlockEntity.geneExists(gene, queen.genome(), registries())
                    && !GeneticLabBlockEntity.geneStabilized(gene, queen.genome(), registries())
                    && costTooltip(graphics, stabilizeCosts(gene), GeneticLabBlockEntity.Bay.STABILIZATION, 77, mouseX, mouseY)) {
                return;
            }
            if (over(mouseX, mouseY, textX(), 22, textW(), 20)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.lab.stability.hint.1"),
                        Component.translatable("gui.antcolonies.lab.stability.hint.2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.antcolonies.lab.stability.hint.3").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        if (over(mouseX, mouseY, LEFT_X + 3, 96, LEFT_W - 6, 19)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.antcolonies.lab.energy_stored",
                            String.format(Locale.ROOT, "%,d", menu.data(GeneticLabBlockEntity.DATA_ENERGY)),
                            String.format(Locale.ROOT, "%,d", GeneticLabBlockEntity.energyCapacity())),
                    Component.translatable("gui.antcolonies.lab.power_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.container == menu.lab().upgrades()) {
            graphics.setComponentTooltipForNextFrame(font, UpgradeEffects.Host.LAB.slotLines(), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            String hint = slotHint(hoveredSlot);
            if (hint != null) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.lab." + hint)), mouseX, mouseY);
            }
        }
    }

    /** What an empty slot is for. */
    private @Nullable String slotHint(Slot slot) {
        if (slot.container != menu.lab()) {
            return null;
        }
        return switch (slot.getContainerSlot()) {
            case GeneticLabBlockEntity.SLOT_QUEEN -> "insert_queen";
            case GeneticLabBlockEntity.SLOT_CATALYST -> "catalyst_hint";
            case GeneticLabBlockEntity.SLOT_MUTATION, GeneticLabBlockEntity.SLOT_MUTATION + 1 -> "slot.mutation";
            case GeneticLabBlockEntity.SLOT_MUTATION_OUTPUT -> "slot.mutation_output";
            case GeneticLabBlockEntity.SLOT_TUBE -> "slot.tube";
            case GeneticLabBlockEntity.SLOT_SAMPLE_OUTPUT -> "slot.sample_output";
            case GeneticLabBlockEntity.SLOT_STABILIZER -> "slot.stabilizer";
            case GeneticLabBlockEntity.SLOT_SCAN -> "slot.scan";
            default -> "materials_hint";
        };
    }
}
