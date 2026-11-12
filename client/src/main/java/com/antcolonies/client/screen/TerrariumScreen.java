package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.expedition.Expedition;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyHistory;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.ForagingMode;
import com.antcolonies.colony.model.Founding;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.simulation.ProductionMath;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.content.menu.TerrariumMenu;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Colony GUI 2.0: the Terrarium's screen. Eight tabs (Overview, Colony, Jobs, Production, Genetics, Network, Statistics,
 * Info) over the Terrarium's slots, in one of two modes the player picks (remembered in the client config):
 * <ul>
 *     <li><b>Visual</b> (the default): pictures of the ants, item icons, coloured bars and cards, short notes.</li>
 *     <li><b>Detailed</b>: the exact numbers, rates per minute, consumption, efficiency and the full statistics.</li>
 * </ul>
 * Everything shown is the server's: the colony synced with its Terrarium (and, while this screen is open, its history,
 * its Storage's contents and its network). The controls send menu buttons; nothing here changes the colony itself.
 */
public class TerrariumScreen extends AntScreen<TerrariumMenu> {
    private static final String[] PAGES = {"overview", "colony", "jobs", "production", "genetics", "network", "statistics", "species"};
    private static final int CONTENT_X = 48;
    private static final int CONTENT_Y = 20;
    private static final int CONTENT_W = 196;
    private static final int CONTENT_H = 116;
    private static final int JOB_ROWS = 3;
    private static final int JOB_ROW_H = 33;
    private static final int BUTTON_MODE = -50;
    private static final int BUTTON_CARD = -60;
    private static final int BUTTON_SERIES = -80;
    private static final Identifier SIDE = AntColonies.id("textures/entity/ant_side.png");
    /** Card colours: a lighter slate than the glass, so pages read as bright panels, and an accent per page. */
    private static final int CARD = 0xFF1F3647;
    private static final int CARD_HI = 0xFF2A4A60;
    private static final int[] ACCENTS = {0xFF4FE3FF, 0xFFFFC85A, 0xFF6CF08A, 0xFFE0B040, 0xFFE07AFF, 0xFF7AA8FF, 0xFFFF8A6E, 0xFF9AD0C0};

    private static int page;
    private static int colonyCard;
    private static int series;
    private int jobScroll;
    private final InfoPanel info = new InfoPanel();
    /** Network nodes drawn this frame, for their tooltips: x, y, radius, index into the colony's network list. */
    private final List<int[]> nodes = new ArrayList<>();

    public TerrariumScreen(TerrariumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 252, 238);
        inventoryLabelX = TerrariumMenu.INVENTORY_X;
        inventoryLabelY = TerrariumMenu.INVENTORY_Y - 11;
    }

    private static boolean detailed() {
        return AntConfig.client(AntConfig.DETAILED_COLONY_VIEW);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    /** Opens a page (for the in-game showcase). */
    public static void showPage(int index) {
        page = Math.clamp(index, 0, PAGES.length - 1);
    }

    /** Sets the mode (for the in-game showcase). */
    public static void showDetailed(boolean detailed) {
        AntConfig.DETAILED_COLONY_VIEW.set(detailed);
    }

    // --- Frame: tabs, mode switch, the page ---

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        nodes.clear();
        // The mode switch, top left over the slots.
        String modeText = Component.translatable(detailed() ? "gui.antcolonies.view.detailed" : "gui.antcolonies.view.visual").getString();
        toggle(4, 4, 40, 13, modeText, BUTTON_MODE, List.of(Component.translatable("gui.antcolonies.view"),
                Component.translatable(detailed() ? "gui.antcolonies.view.detailed.hint" : "gui.antcolonies.view.visual.hint").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.antcolonies.view.click").withStyle(ChatFormatting.DARK_GRAY)), detailed());
        // Tabs: an item icon each, the page's accent under the open one.
        ItemStack[] icons = tabIcons();
        for (int i = 0; i < PAGES.length; i++) {
            int tx = CONTENT_X + i * 24 + 1;
            toggle(tx, 2, 22, 17, "", -100 - i, List.of(Component.translatable("gui.antcolonies.page." + PAGES[i]),
                    Component.translatable("gui.antcolonies.page." + PAGES[i] + ".hint").withStyle(ChatFormatting.GRAY)), page == i);
        }
        drawWell(graphics, leftPos + CONTENT_X, topPos + CONTENT_Y, CONTENT_W, CONTENT_H);
        graphics.fill(leftPos + CONTENT_X + 1, topPos + CONTENT_Y + 1, leftPos + CONTENT_X + CONTENT_W - 1, topPos + CONTENT_Y + 2, ACCENTS[page]);
        Colony colony = menu.colony();
        // Whatever a page draws stays inside its panel, however long a name or number gets.
        graphics.enableScissor(leftPos + CONTENT_X + 1, topPos + CONTENT_Y + 1, leftPos + CONTENT_X + CONTENT_W - 1, topPos + CONTENT_Y + CONTENT_H - 1);
        switch (page) {
            case 0 -> overview(graphics, colony);
            case 1 -> colonyPage(graphics, colony);
            case 2 -> jobs(graphics, colony);
            case 3 -> production(graphics, colony);
            case 4 -> genetics(graphics, colony);
            case 5 -> network(graphics, colony, mouseX, mouseY);
            case 6 -> statistics(graphics, colony, mouseX, mouseY);
            default -> species(graphics, colony);
        }
        graphics.disableScissor();
        // Icons go over the tab buttons (drawn after this page), so they are drawn in drawButton.
        tabIconsThisFrame = icons;
    }

    private ItemStack[] tabIconsThisFrame = new ItemStack[0];

    private ItemStack[] tabIcons() {
        Colony colony = menu.colony();
        ItemStack queen = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        if (colony.queen() != null) {
            queen.set(ModDataComponents.QUEEN.get(), colony.queen());
        }
        // Icons that look different at a glance (the chambers all look alike as items: glass cases).
        return new ItemStack[] {new ItemStack(ModItems.TERRARIUM.get()), queen, new ItemStack(ModItems.CHITIN_PICKAXE.get()), new ItemStack(Items.CHEST),
                new ItemStack(ModItems.GENETIC_ANALYZER.get()), new ItemStack(ModItems.RALLY_PHEROMONE.get()), new ItemStack(ModItems.COLONY_SCANNER.get()),
                new ItemStack(ModItems.ANT_IDENTIFICATION_BOOK.get())};
    }

    @Override
    protected void drawButton(GuiGraphicsExtractor graphics, Button button, boolean hovered) {
        super.drawButton(graphics, button, hovered);
        int id = button.id();
        if (id <= -100 && id > -100 - PAGES.length && -100 - id < tabIconsThisFrame.length) {
            graphics.item(tabIconsThisFrame[-100 - id], button.x() + 3, button.y());
        }
        if (id <= BUTTON_SERIES && id > BUTTON_SERIES - ColonyHistory.Series.values().length) {
            ColonyHistory.Series s = ColonyHistory.Series.values()[BUTTON_SERIES - id];
            graphics.fill(button.x() + 2, button.y() + button.h() - 3, button.x() + button.w() - 2, button.y() + button.h() - 2, s.color());
        }
    }

    @Override
    protected void onButton(int id) {
        if (id <= -100) {
            page = -100 - id;
            clickSound();
            return;
        }
        if (id == BUTTON_MODE) {
            AntConfig.DETAILED_COLONY_VIEW.set(!detailed());
            AntConfig.CLIENT_SPEC.save();
            clickSound();
            return;
        }
        if (id <= BUTTON_CARD && id > BUTTON_CARD - 5) {
            colonyCard = BUTTON_CARD - id;
            clickSound();
            return;
        }
        if (id <= BUTTON_SERIES && id > BUTTON_SERIES - ColonyHistory.Series.values().length) {
            series = BUTTON_SERIES - id;
            clickSound();
            return;
        }
        super.onButton(id);
    }

    /** Which Terrarium slot this is (queen, food, feed, output, upgrade), or null for the player's inventory. */
    private static @Nullable String slotKind(Slot slot) {
        if (slot.container instanceof com.antcolonies.content.blockentity.TerrariumBlockEntity) {
            return switch (slot.getContainerSlot()) {
                case com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_QUEEN -> "queen";
                case com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_FOOD -> "food";
                case com.antcolonies.content.blockentity.TerrariumBlockEntity.SLOT_OUTPUT -> "output";
                default -> "feed";
            };
        }
        return slot.container instanceof net.minecraft.world.entity.player.Inventory ? null : "upgrade";
    }

    @Override
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, Slot slot, int x, int y) {
        super.drawSlotBackground(graphics, slot, x, y);
        String kind = slotKind(slot);
        if (kind != null && !slot.hasItem()) {
            drawGlyph(graphics, kind, x, y);
        }
    }

    // --- Drawing helpers ---

    /** A card: a lighter panel with an accent strip on top. */
    private void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent, boolean lit) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, lit ? CARD_HI : CARD);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + 1, accent);
        graphics.outline(leftPos + x - 1, topPos + y - 1, w + 2, h + 2, 0xFF0B1820);
    }

    /** A rounded chip with a note in it (statuses). Returns its width. */
    private int chip(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        // A solid dark plate with a coloured bar on its left: the text reads on it whatever its colour.
        int w = font.width(text) + 7;
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + 10, 0xFF0C1820);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 2, topPos + y + 10, color);
        label(graphics, text, x + 5, y + 1, color);
        return w;
    }

    /** A number or value: light, with a shadow, so it reads on any background. */
    private void value(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, leftPos + x, topPos + y, color, true);
    }

    /** An item drawn at twice its size. */
    private void bigItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + x, topPos + y);
        graphics.pose().scale(2, 2);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    /**
     * An ant drawn from the side, from the same sprites the Terrarium's tunnels use (ant_side.png: 24x12 cells, a row each
     * for body, gaster and crystals per caste), tinted with the species' colours, {@code scale} times its size.
     */
    private void sideAnt(GuiGraphicsExtractor graphics, Caste caste, AntLook look, int x, int y, int scale) {
        int casteRow = caste == Caste.QUEEN ? 2 : caste == Caste.SOLDIER ? 1 : 0;
        int frame = (int) (System.currentTimeMillis() / 400 % 4);
        int[] tints = {look.body(), look.gaster(), look.crystal().orElse(-1)};
        for (int part = 0; part < 3; part++) {
            if (tints[part] == -1) {
                continue;
            }
            int row = casteRow * 3 + part;
            graphics.blit(RenderPipelines.GUI_TEXTURED, SIDE, leftPos + x, topPos + y, frame * 24, row * 12, 24 * scale, 12 * scale, 24, 12, 96, 112,
                    0xFF000000 | tints[part]);
        }
    }

    private static int bright(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        float factor = max < 170 ? 170F / Math.max(1, max) : 1;
        return 0xFF000000 | Math.min(255, (int) (r * factor)) << 16 | Math.min(255, (int) (g * factor)) << 8 | Math.min(255, (int) (b * factor));
    }

    private int speciesColor(Colony colony) {
        AntSpecies species = ClientSpecies.get(colony.species());
        return species == null ? LABEL : bright(species.look().crystal().orElse(species.look().gaster()));
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    // --- Overview ---

    private void overview(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        AntLook look = ClientSpecies.look(colony.species());
        // The Queen's card: her portrait on terrarium soil (dark ants read on it as well as pale ones), her stage under it.
        card(graphics, x, y, 62, 56, ACCENTS[0], false);
        graphics.fill(leftPos + x + 2, topPos + y + 3, leftPos + x + 60, topPos + y + 33, 0xFFB79A72);
        graphics.fill(leftPos + x + 2, topPos + y + 27, leftPos + x + 60, topPos + y + 33, 0xFF9C7F5A);
        if (colony.queen() != null) {
            sideAnt(graphics, Caste.QUEEN, look, x + 7, y + 6, 2);
        } else {
            label(graphics, "?", x + 28, y + 14, 0xFF5A4630);
        }
        Component stage = Component.translatable("gui.antcolonies.stage", colony.stage());
        label(graphics, fit(stage.getString(), 58), x + 3, y + 36, INFO);
        drawBar(graphics, leftPos + x + 3, topPos + y + 47, 56, 5, stageProgress(colony), INFO);
        // Name and Queen, to the right.
        int rx = x + 68;
        int room = CONTENT_X + CONTENT_W - 4 - rx;
        label(graphics, fit(Tooltips.species(colony.species()).getString(), room), rx, y, speciesColor(colony));
        QueenData queen = colony.queen();
        if (queen != null) {
            label(graphics, fit(Component.translatable("gui.antcolonies.queen", queen.genome().lineage().name()).getString() + " "
                    + Tooltips.rarity(queen.genome().rarity()).getString(), room), rx, y + 10, LABEL);
        } else {
            label(graphics, Component.translatable("status.antcolonies.no_queen"), rx, y + 10, BAD);
        }
        // Health and food bars, with an icon each and the value at the end (a percentage, or the food itself in Detailed Mode).
        int barW = room - 17 - 28;
        double capacity = Math.max(1, colony.stats().foodCapacity());
        graphics.item(new ItemStack(Items.GLISTERING_MELON_SLICE), leftPos + rx - 1, topPos + y + 20);
        drawBar(graphics, leftPos + rx + 17, topPos + y + 24, barW, 7, colony.health(), colony.health() < 0.5 ? BAD : GOOD);
        value(graphics, percent(colony.health()), rx + 20 + barW, y + 24, LABEL);
        graphics.item(new ItemStack(ModItems.ANT_FOOD.get()), leftPos + rx - 1, topPos + y + 37);
        drawBar(graphics, leftPos + rx + 17, topPos + y + 41, barW, 7, colony.food() / capacity, colony.food() <= 0 ? BAD
                : colony.foodFactor() < 0.999 ? WARN : 0xFFE0B040);
        value(graphics, fit(detailed() ? number(colony.food()) : percent(colony.food() / capacity), 27), rx + 20 + barW, y + 41, LABEL);
        y += 62;
        if (colony.founding().active()) {
            // Queen-only founding takes the population's place until her first workers are grown.
            founding(graphics, colony, x, y);
            y += 34;
        } else if (detailed()) {
            Colony.Stats stats = colony.stats();
            String perMinute = Component.translatable("gui.antcolonies.per_minute").getString();
            label(graphics, fit(Component.translatable("gui.antcolonies.workers", number(colony.workers())).getString(), 92), x, y, LABEL);
            label(graphics, fit(Component.translatable("gui.antcolonies.housing", number(colony.adults()), number(stats.adultCapacity())).getString(), 92),
                    x + 96, y, DIM);
            y += 10;
            label(graphics, fit(Component.translatable("gui.antcolonies.larvae", number(colony.larvae())).getString(), 92), x, y, LABEL);
            label(graphics, fit(Component.translatable("gui.antcolonies.eggs", number(colony.eggs())).getString(), 92), x + 96, y, LABEL);
            y += 10;
            label(graphics, fit(Component.translatable("gui.antcolonies.food_balance", rate(stats.foodIn()) + perMinute, rate(stats.foodOut()) + perMinute)
                    .getString() + "  " + Component.translatable("gui.antcolonies.efficiency", percent(colony.foodFactor())).getString(), CONTENT_W - 8),
                    x, y, stats.foodOut() > stats.foodIn() || colony.foodFactor() < 0.999 ? WARN : GOOD);
            y += 12;
        } else {
            populationIcons(graphics, colony, x, y);
            y += 22;
        }
        statusChips(graphics, colony, x, y);
        // Workers into a tube, to move them (a broken Terrarium keeps none): in the slot column, under the upgrades.
        String workersText = Component.translatable("gui.antcolonies.take.workers").getString();
        button(4, 134, 40, 13, workersText, TerrariumMenu.BUTTON_TAKE_WORKERS,
                List.of(Component.translatable("gui.antcolonies.take.hint", AntSample.MAX),
                        Component.translatable("gui.antcolonies.take.move").withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.antcolonies.take.whole").withStyle(ChatFormatting.GRAY)), colony.workers() > 0);
    }

    /** Progress to the next colony stage (adults against its threshold); full at stage 4. */
    private static double stageProgress(Colony colony) {
        List<? extends Integer> thresholds = AntConfig.get(AntConfig.STAGE_POPULATIONS);
        int stage = colony.stage();
        if (stage >= 4 || stage - 1 >= thresholds.size()) {
            return 1;
        }
        double from = stage == 1 ? 0 : thresholds.get(stage - 2);
        double to = thresholds.get(stage - 1);
        return Math.clamp((colony.exactAdults() - from) / Math.max(1, to - from), 0, 1);
    }

    /** Eggs, larvae and workers as their items (tinted to the species) with their counts. */
    private void populationIcons(GuiGraphicsExtractor graphics, Colony colony, int x, int y) {
        ItemStack egg = new ItemStack(ModItems.ANT_EGG.get());
        egg.set(ModDataComponents.SPECIES.get(), colony.species());
        ItemStack larva = new ItemStack(ModItems.ANT_LARVA.get());
        larva.set(ModDataComponents.SPECIES.get(), colony.species());
        ItemStack workers = com.antcolonies.content.item.TweezersItem.antTube(new AntSample(colony.species(), Caste.WORKER, 1));
        Object[][] row = {{egg, colony.eggs(), "eggs"}, {larva, colony.larvae(), "larvae"}, {workers, colony.workers(), "workers"}};
        int cx = x;
        for (Object[] r : row) {
            card(graphics, cx, y, 58, 18, ACCENTS[1], false);
            graphics.item((ItemStack) r[0], leftPos + cx + 1, topPos + y + 1);
            value(graphics, number((Long) r[1]), cx + 20, y + 5, LABEL);
            cx += 63;
        }
    }

    /** The colony's problems first, then its notes, as coloured chips that wrap. */
    private void statusChips(GuiGraphicsExtractor graphics, Colony colony, int x, int y) {
        List<ColonyStatus> statuses = new ArrayList<>(ColonyStatus.of(colony.status() | menu.terrarium().feedStatus()));
        statuses.sort((a, b) -> Boolean.compare(b.problem(), a.problem()));
        int cx = x;
        int bottom = CONTENT_Y + CONTENT_H - 16;
        for (ColonyStatus status : statuses) {
            String key = status.translationKey() + (detailed() ? "" : ".short");
            String text = Component.translatable(key).getString();
            int color = status == ColonyStatus.WORKING ? GOOD : status.problem() ? BAD : WARN;
            int w = font.width(text) + 7;
            if (cx + w > CONTENT_X + CONTENT_W - 4) {
                cx = x;
                y += 12;
            }
            if (y > bottom) {
                break;
            }
            chip(graphics, fit(text, CONTENT_W - 10), cx, y, color);
            cx += w + 3;
        }
    }

    /**
     * Queen-only founding: the steps (her food, her first brood, founded), the food she waits for, and her first brood's
     * eggs, larvae and adults so far.
     */
    private void founding(GuiGraphicsExtractor graphics, Colony colony, int x, int y) {
        double needed = ColonySimulator.foundingFood();
        int target = AntConfig.get(AntConfig.FOUNDING_WORKERS);
        boolean feeding = colony.founding() == Founding.FEEDING;
        int w = CONTENT_W - 8;
        card(graphics, x, y, w, 30, ACCENTS[1], false);
        // The three steps, the current one lit: her food, her first brood, founded.
        String[] steps = {"1", "2", "3"};
        int current = feeding ? 0 : 1;
        for (int i = 0; i < 3; i++) {
            int sx = x + 3 + i * 12;
            graphics.fill(leftPos + sx, topPos + y + 3, leftPos + sx + 10, topPos + y + 12, i == current ? ACCENTS[1] : i < current ? GOOD : 0xFF33495A);
            label(graphics, steps[i], sx + 3, y + 4, i == current ? 0xFF1A1206 : LABEL);
        }
        label(graphics, fit(Component.translatable("gui.antcolonies.founding").getString() + ": "
                + Component.translatable(feeding ? "gui.antcolonies.founding.feed" : "gui.antcolonies.founding.brood").getString(), w - 44), x + 40, y + 4, LABEL);
        double progress = feeding ? colony.food() / Math.max(1, needed) : colony.foundingRaised() / target;
        String numbers = feeding ? number(colony.food()) + " / " + number(needed) : (int) colony.foundingRaised() + " / " + target;
        int numbersW = font.width(numbers);
        drawBar(graphics, leftPos + x + 4, topPos + y + 18, w - 12 - numbersW, 7, progress, feeding ? 0xFFE0B040 : GOOD);
        value(graphics, numbers, x + w - 3 - numbersW, y + 18, LABEL);
    }

    // --- Colony: the castes as cards; click one for its details ---

    private void colonyPage(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        AntLook look = ClientSpecies.look(colony.species());
        AntSpecies species = ClientSpecies.get(colony.species());
        String[] names = {"queen", "eggs", "larvae", "workers", "specialists"};
        long specialists = colony.assignments().stream().filter(a -> a.kind().job().any()).mapToLong(Colony.Assignment::assigned).sum();
        long[] counts = {colony.queen() != null ? 1 : 0, colony.eggs(), colony.larvae(), colony.workers(), specialists};
        int w = 36;
        for (int i = 0; i < 5; i++) {
            int cx = x + i * (w + 2);
            boolean selected = colonyCard == i;
            card(graphics, cx, y, w, 44, ACCENTS[1], selected);
            if (selected) {
                graphics.outline(leftPos + cx - 1, topPos + y - 1, w + 2, 46, ACCENTS[1]);
            }
            drawCardPicture(graphics, colony, look, i, cx, y);
            String count = detailed() ? String.valueOf(counts[i]) : number(counts[i]);
            value(graphics, fit(count, w - 2), cx + (w - Math.min(w - 2, font.width(count))) / 2, y + 26, selected ? 0xFFFFFFFF : LABEL);
            // The name under it; a long one in smaller letters rather than cut.
            String name = Component.translatable("gui.antcolonies.card." + names[i]).getString();
            if (font.width(name) > w - 2) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(leftPos + cx + (w - font.width(name) * 0.75F) / 2, topPos + y + 36);
                graphics.pose().scale(0.75F, 0.75F);
                graphics.text(font, name, 0, 0, DIM, false);
                graphics.pose().popMatrix();
            } else {
                label(graphics, name, cx + (w - font.width(name)) / 2, y + 35, DIM);
            }
        }
        // The chosen card's details.
        y += 50;
        card(graphics, x, y, CONTENT_W - 8, CONTENT_H - 56, ACCENTS[1], false);
        List<Component> lines = cardDetails(colony, species);
        int ly = y + 4;
        for (Component line : lines) {
            if (ly > CONTENT_Y + CONTENT_H - 10) {
                break;
            }
            label(graphics, fit(line.getString(), CONTENT_W - 16), x + 4, ly, line.getStyle().getColor() == null ? LABEL : 0xFF000000 | line.getStyle().getColor().getValue());
            ly += 9;
        }
    }

    /** The Colony page's cards: where they are, for clicks and tooltips. */
    private static int cardX(int i) {
        return CONTENT_X + 4 + i * 38;
    }

    @Override
    protected boolean clickPage(double mouseX, double mouseY) {
        if (page == 1) {
            for (int i = 0; i < 5; i++) {
                if (over(mouseX, mouseY, cardX(i), CONTENT_Y + 5, 36, 44)) {
                    colonyCard = i;
                    clickSound();
                    return true;
                }
            }
        }
        return super.clickPage(mouseX, mouseY);
    }

    private void drawCardPicture(GuiGraphicsExtractor graphics, Colony colony, AntLook cardLook, int card, int x, int y) {
        switch (card) {
            case 0 -> {
                if (colony.queen() != null) {
                    sideAnt(graphics, Caste.QUEEN, cardLook, x + 6, y + 6, 1);
                }
            }
            case 1, 2 -> {
                ItemStack stack = new ItemStack(card == 1 ? ModItems.ANT_EGG.get() : ModItems.ANT_LARVA.get());
                stack.set(ModDataComponents.SPECIES.get(), colony.species());
                graphics.item(stack, leftPos + x + 10, topPos + y + 5);
            }
            case 3 -> sideAnt(graphics, Caste.WORKER, cardLook, x + 6, y + 8, 1);
            default -> graphics.item(new ItemStack(ModBlocks.STRUCTURES.get(StructureKind.FORAGING_SITE).get()), leftPos + x + 10, topPos + y + 5);
        }
    }

    private List<Component> cardDetails(Colony colony, @Nullable AntSpecies species) {
        List<Component> lines = new ArrayList<>();
        Colony.Stats stats = colony.stats();
        String perMinute = Component.translatable("gui.antcolonies.per_minute").getString();
        switch (colonyCard) {
            case 0 -> {
                QueenData queen = colony.queen();
                if (queen == null) {
                    lines.add(Component.translatable("status.antcolonies.no_queen").withStyle(ChatFormatting.RED));
                    lines.add(Component.translatable("gui.antcolonies.card.queen.none"));
                    break;
                }
                Genome genome = queen.genome();
                lines.add(Component.translatable("gui.antcolonies.genetics_of", genome.lineage().name(), genome.lineage().generation()));
                lines.add(Component.translatable("gui.antcolonies.card.queen.laying", rate(stats.eggsLaid()) + perMinute));
                lines.add(Component.translatable("gui.antcolonies.card.queen.best", bestTraits(genome)).withStyle(ChatFormatting.GREEN));
                if (colony.founding().active()) {
                    lines.add(Component.translatable("gui.antcolonies.founding").withStyle(ChatFormatting.GOLD));
                } else if (colony.queenFounded()) {
                    lines.add(Component.translatable("gui.antcolonies.card.queen.founded").withStyle(ChatFormatting.GOLD));
                }
            }
            case 1 -> {
                lines.add(Component.translatable("gui.antcolonies.eggs", number(colony.eggs())));
                lines.add(Component.translatable("gui.antcolonies.card.eggs.laid", rate(stats.eggsLaid()) + perMinute));
                lines.add(Component.translatable("gui.antcolonies.card.eggs.hatch", rate(AntConfig.get(AntConfig.EGG_HATCH_MINUTES))));
                lines.add(Component.translatable("gui.antcolonies.card.eggs.total", number(colony.totalLaid())).withStyle(ChatFormatting.GRAY));
            }
            case 2 -> {
                lines.add(Component.translatable("gui.antcolonies.larvae", number(colony.larvae())));
                lines.add(Component.translatable("gui.antcolonies.card.larvae.raised", rate(stats.adultsRaised()) + perMinute));
                lines.add(Component.translatable("gui.antcolonies.stat.larvae_room", number(stats.larvaeCapacity())));
                lines.add(Component.translatable("gui.antcolonies.card.larvae.food").withStyle(ChatFormatting.GRAY));
            }
            case 3 -> {
                long assigned = colony.assignments().stream().mapToLong(Colony.Assignment::assigned).sum();
                long idle = Math.max(0, colony.workers() - assigned - colony.maintenanceWorkers());
                lines.add(Component.translatable("gui.antcolonies.workers", number(colony.workers())));
                lines.add(Component.translatable("gui.antcolonies.housing", number(colony.adults()), number(stats.adultCapacity())));
                lines.add(Component.translatable("gui.antcolonies.card.workers.split", number(assigned), number(colony.maintenanceWorkers()), number(idle)));
                lines.add(Component.translatable("gui.antcolonies.card.workers.deaths", rate(stats.deaths()) + perMinute).withStyle(ChatFormatting.GRAY));
            }
            default -> {
                // Who does which job: farmers, hunters, foragers and expedition ants.
                long[] byJob = new long[StructureKind.values().length];
                for (Colony.Assignment a : colony.assignments()) {
                    byJob[a.kind().ordinal()] += a.assigned();
                }
                lines.add(Component.translatable("gui.antcolonies.card.specialists.list").withStyle(ChatFormatting.GRAY));
                for (StructureKind kind : StructureKind.values()) {
                    if (kind.job().any() && byJob[kind.ordinal()] > 0) {
                        lines.add(Component.translatable("gui.antcolonies.card.job", Component.translatable(kind.translationKey()), number(byJob[kind.ordinal()])));
                    }
                }
                if (lines.size() == 1) {
                    lines.add(Component.translatable("gui.antcolonies.no_jobs"));
                }
                if (species != null && species.canHunt()) {
                    lines.add(Component.translatable("gui.antcolonies.card.hunters").withStyle(ChatFormatting.GOLD));
                } else if (species != null && species.canFarm()) {
                    lines.add(Component.translatable("gui.antcolonies.card.farmers").withStyle(ChatFormatting.GREEN));
                }
            }
        }
        return lines;
    }

    private static String bestTraits(Genome genome) {
        List<Trait> traits = new ArrayList<>(List.of(Trait.values()));
        traits.sort((a, b) -> Integer.compare(genome.level(b), genome.level(a)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(Component.translatable(traits.get(i).translationKey() + ".short").getString()).append(' ').append(genome.level(traits.get(i)));
        }
        return out.toString();
    }

    // --- Jobs ---

    private void jobs(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        List<Colony.Assignment> list = colony.assignments();
        String range = list.size() > JOB_ROWS ? (jobScroll + 1) + "-" + Math.min(list.size(), jobScroll + JOB_ROWS) + "/" + list.size() : "";
        int rangeW = range.isEmpty() ? 0 : font.width(range) + 4;
        label(graphics, fit(Component.translatable("gui.antcolonies.jobs_header", colony.maintenanceWorkers()).getString(), CONTENT_W - 8 - rangeW),
                x, y, LABEL);
        if (!range.isEmpty()) {
            label(graphics, range, CONTENT_X + CONTENT_W - 4 - font.width(range), y, DIM);
        }
        y += 11;
        if (list.isEmpty()) {
            label(graphics, Component.translatable("gui.antcolonies.no_jobs"), x, y, DIM);
            return;
        }
        jobScroll = Math.clamp(jobScroll, 0, Math.max(0, list.size() - JOB_ROWS));
        for (int row = 0; row < JOB_ROWS && jobScroll + row < list.size(); row++) {
            int index = jobScroll + row;
            Colony.Assignment job = list.get(index);
            int rowY = y + row * JOB_ROW_H;
            boolean problem = ColonyStatus.of(job.status()).stream().anyMatch(ColonyStatus::problem);
            card(graphics, x - 2, rowY - 1, CONTENT_W - 4, JOB_ROW_H - 1, job.enabled() ? (problem ? BAD : ACCENTS[2]) : FAINT, false);
            graphics.item(new ItemStack(ModBlocks.STRUCTURES.get(job.kind()).get()), leftPos + x, topPos + rowY + 2);
            int right = CONTENT_X + CONTENT_W - 6;
            // Line 1: its name, then how full it is (a bar in Visual Mode, the numbers in Detailed), and its rate.
            String perMinute = rate(job.rate()) + Component.translatable("gui.antcolonies.per_minute").getString();
            int rateX = right - font.width(perMinute) + 1;
            label(graphics, fit(Component.translatable(job.kind().translationKey()).getString(), rateX - x - 22), x + 19, rowY + 1,
                    job.enabled() ? LABEL : DIM);
            value(graphics, perMinute, rateX, rowY + 1, INFO);
            if (detailed()) {
                String workers = Component.translatable("gui.antcolonies.job_workers", job.assigned(), job.capacity()).getString();
                String note = ColonyStatus.of(job.status()).stream().filter(ColonyStatus::problem).findFirst()
                        .map(s -> " - " + Component.translatable(s.translationKey() + ".short").getString()).orElse("");
                label(graphics, fit(workers + note, right - x - 18), x + 19, rowY + 11, problem ? BAD : INFO);
            } else {
                drawBar(graphics, leftPos + x + 20, topPos + rowY + 12, right - x - 22, 5, job.assigned() / (double) Math.max(1, job.capacity()),
                        problem ? BAD : GOOD);
            }
            // Line 2: how many ants it wants: the step buttons and Auto.
            int base = TerrariumMenu.BUTTON_ASSIGNMENT + index * 20;
            String[] steps = {"less_10", "less_1", "more_1", "more_10"};
            int[] ids = {StructureMenu.LESS_10, StructureMenu.LESS, StructureMenu.MORE, StructureMenu.MORE_10};
            int bx = x;
            int lineY = rowY + 21;
            for (int i = 0; i < steps.length; i++) {
                Component text = Component.translatable("gui.antcolonies." + steps[i]);
                int w = buttonWidth(text.getString());
                button(bx, lineY, w, 11, text.getString(), base + ids[i], List.of(text), true);
                bx += w + 1;
            }
            String auto = Component.translatable("gui.antcolonies.auto").getString();
            int autoW = buttonWidth(auto);
            toggle(bx, lineY, autoW, 11, auto, base + StructureMenu.AUTO, List.of(Component.translatable("gui.antcolonies.auto.hint")), job.requested() < 0);
            // Then, from the right: on or off, its priority, and a Foraging Site's mode.
            int sx = right + 1;
            String onOff = Component.translatable(job.enabled() ? "gui.antcolonies.site.on" : "gui.antcolonies.site.off").getString();
            int onW = buttonWidth(onOff);
            sx -= onW;
            toggle(sx, lineY, onW, 11, onOff, base + StructureMenu.TOGGLE, List.of(Component.translatable("gui.antcolonies.site.toggle.hint")), job.enabled());
            String prio = Component.translatable("gui.antcolonies.priority.short." + job.priority()).getString();
            int pw = buttonWidth(prio);
            sx -= pw + 1;
            button(sx, lineY, pw, 11, prio, base + StructureMenu.CYCLE_PRIORITY, List.of(Component.translatable("gui.antcolonies.priority"),
                    Component.translatable("gui.antcolonies.priority." + job.priority()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.antcolonies.priority.hint").withStyle(ChatFormatting.DARK_GRAY)), true);
            if (job.kind() == StructureKind.FORAGING_SITE) {
                ForagingMode mode = ForagingMode.byId(job.mode());
                String modeText = Component.translatable(mode.translationKey() + ".short").getString();
                int mw = buttonWidth(modeText);
                sx -= mw + 1;
                if (sx > bx + autoW + 1) {
                    button(sx, lineY, mw, 11, modeText, base + StructureMenu.CYCLE_MODE, List.of(Component.translatable("gui.antcolonies.mode"),
                            Component.translatable(mode.translationKey()).withStyle(ChatFormatting.AQUA),
                            Component.translatable(mode.translationKey() + ".hint").withStyle(ChatFormatting.GRAY)), true);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (page == 2 && over(x, y, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)) {
            jobScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (page == 7 && over(x, y, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)) {
            info.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // --- Production ---

    /** One find this colony makes: the item, its expected finds per minute from its sites' real rates, and how many are stored. */
    private record Find(ItemStack item, double perMinute, long stored) {}

    private void production(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        List<Find> finds = finds(colony);
        Colony.Stats stats = colony.stats();
        String perMinute = Component.translatable("gui.antcolonies.per_minute").getString();
        // Storage use and the food balance, on top.
        if (colony.storageSlots() > 0) {
            label(graphics, Component.translatable("gui.antcolonies.production.storage"), x, y, LABEL);
            drawBar(graphics, leftPos + x + 50, topPos + y + 1, 70, 6, colony.storageUsed() / (double) colony.storageSlots(),
                    colony.storageUsed() >= colony.storageSlots() ? BAD : INFO);
            label(graphics, colony.storageUsed() + "/" + colony.storageSlots(), x + 124, y, DIM);
        } else {
            label(graphics, Component.translatable("gui.antcolonies.production.no_storage"), x, y, WARN);
        }
        y += 10;
        label(graphics, fit(Component.translatable("gui.antcolonies.food_balance", rate(stats.foodIn()) + perMinute, rate(stats.foodOut()) + perMinute)
                .getString(), CONTENT_W - 8), x, y, stats.foodOut() > stats.foodIn() ? WARN : GOOD);
        y += 11;
        if (finds.isEmpty()) {
            label(graphics, Component.translatable("gui.antcolonies.production.none"), x, y, DIM);
            return;
        }
        if (detailed()) {
            // A table: item, stored, finds per minute, share.
            double total = finds.stream().mapToDouble(Find::perMinute).sum();
            label(graphics, Component.translatable("gui.antcolonies.production.col.item"), x + 10, y, FAINT);
            label(graphics, Component.translatable("gui.antcolonies.production.col.stored"), x + 86, y, FAINT);
            label(graphics, Component.translatable("gui.antcolonies.per_minute"), x + 120, y, FAINT);
            label(graphics, "%", x + 160, y, FAINT);
            y += 9;
            for (Find find : finds) {
                if (y > CONTENT_Y + CONTENT_H - 12) {
                    break;
                }
                graphics.pose().pushMatrix();
                graphics.pose().translate(leftPos + x, topPos + y);
                graphics.pose().scale(0.5F, 0.5F);
                graphics.item(find.item(), 0, 0);
                graphics.pose().popMatrix();
                label(graphics, fit(find.item().getHoverName().getString(), 72), x + 10, y, LABEL);
                label(graphics, number(find.stored()), x + 86, y, INFO);
                label(graphics, rate(find.perMinute()), x + 120, y, GOOD);
                label(graphics, percent(total <= 0 ? 0 : find.perMinute() / total), x + 160, y, DIM);
                y += 9;
            }
        } else {
            // Icons: the item, how many are stored, a green bar for how fast it comes in.
            double most = finds.stream().mapToDouble(Find::perMinute).max().orElse(1);
            int perRow = 7;
            for (int i = 0; i < finds.size() && i < perRow * 2; i++) {
                Find find = finds.get(i);
                int cx = x + (i % perRow) * 27;
                int cy = y + (i / perRow) * 33;
                card(graphics, cx, cy, 25, 31, ACCENTS[3], false);
                graphics.item(find.item(), leftPos + cx + 4, topPos + cy + 2);
                // How many are stored, under the icon; a green bar for how fast it comes in.
                String stored = fit(number(find.stored()), 25);
                value(graphics, stored, cx + (26 - font.width(stored)) / 2, cy + 19, find.stored() > 0 ? LABEL : FAINT);
                // How fast it comes in: a bar of its own under the number, never behind it.
                drawBar(graphics, leftPos + cx + 3, topPos + cy + 28, 19, 2, find.perMinute() / Math.max(0.0001, most), GOOD);
            }
            if (finds.size() > perRow * 2) {
                String more = Component.translatable("gui.antcolonies.production.more", finds.size() - perRow * 2).getString();
                label(graphics, more, CONTENT_X + CONTENT_W - 4 - font.width(more), y + 67, DIM);
            }
        }
    }

    /**
     * What the colony finds: its Foraging Sites' outputs (by their modes) and its expedition sites' tables, each worth
     * its share of the site's real rate; and anything else its Storage holds. Most first.
     */
    private List<Find> finds(Colony colony) {
        List<Find> finds = new ArrayList<>();
        AntSpecies species = ClientSpecies.get(colony.species());
        int stage = colony.stage();
        double gathering = 1;
        if (colony.queen() != null && ClientSpecies.registries() != null) {
            Genome genome = colony.queen().genome();
            gathering = ProductionMath.bonus(genome.multiplier(Trait.GATHERING_EFFICIENCY) * Genetics.effects(genome, ClientSpecies.registries()).gathering());
        }
        double specialWeight = 1;
        for (Colony.Assignment job : colony.assignments()) {
            if (!job.kind().foraging() || job.rate() <= 0) {
                continue;
            }
            List<SpeciesOutput> outputs;
            ForagingMode mode = ForagingMode.byId(job.mode());
            if (job.kind().expedition()) {
                Expedition expedition = Expedition.forSite(ClientSpecies.registries(), job.kind());
                outputs = expedition == null ? List.of() : expedition.outputsFor(colony.species());
                mode = ForagingMode.BALANCED;
            } else {
                outputs = species == null ? List.of() : species.production().outputs();
            }
            double total = 0;
            for (SpeciesOutput output : outputs) {
                if (output.minStage() <= stage && output.available()) {
                    total += ColonySimulator.pickWeight(output, specialWeight, mode);
                }
            }
            if (total <= 0) {
                continue;
            }
            for (SpeciesOutput output : outputs) {
                if (output.minStage() > stage || !output.available()) {
                    continue;
                }
                double share = ColonySimulator.pickWeight(output, specialWeight, mode) / total;
                double count = output.special() ? output.count() : output.count() * gathering;
                add(finds, output.create(1), job.rate() * share * count, 0);
            }
        }
        for (Colony.StoredItem stored : colony.stored()) {
            add(finds, stored.item(), 0, stored.count());
        }
        finds.sort((a, b) -> a.perMinute() != b.perMinute() ? Double.compare(b.perMinute(), a.perMinute()) : Long.compare(b.stored(), a.stored()));
        return finds;
    }

    private static void add(List<Find> finds, ItemStack item, double perMinute, long stored) {
        for (int i = 0; i < finds.size(); i++) {
            Find find = finds.get(i);
            if (ItemStack.isSameItemSameComponents(find.item(), item)) {
                finds.set(i, new Find(find.item(), find.perMinute() + perMinute, find.stored() + stored));
                return;
            }
        }
        finds.add(new Find(item.copyWithCount(1), perMinute, stored));
    }

    // --- Genetics ---

    private void genetics(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        QueenData queen = colony.queen();
        if (queen == null) {
            label(graphics, Component.translatable("status.antcolonies.no_queen"), x, y, BAD);
            return;
        }
        Genome genome = queen.genome();
        sideAnt(graphics, Caste.QUEEN, ClientSpecies.look(colony.species()), CONTENT_X + CONTENT_W - 30, y - 2, 1);
        label(graphics, fit(Component.translatable("gui.antcolonies.genetics_of", genome.lineage().name(), genome.lineage().generation()).getString(), 150),
                x, y, LABEL);
        y += 11;
        int max = Trait.maxLevel();
        for (Trait trait : Trait.values()) {
            int level = genome.level(trait);
            double multiplier = Trait.multiplier(level);
            if (trait.ordinal() % 2 == 0) {
                graphics.fill(leftPos + CONTENT_X + 2, topPos + y - 1, leftPos + CONTENT_X + CONTENT_W - 2, topPos + y + 7, 0x30000000);
            }
            label(graphics, fit(Component.translatable(trait.translationKey() + ".short").getString(), 68), x, y, LABEL);
            for (int i = 0; i < max; i++) {
                int sx = leftPos + x + 72 + i * 6;
                graphics.fill(sx, topPos + y + 1, sx + 5, topPos + y + 7, i < level ? 0xFF000000 | Tooltips.levelRgb(level) : 0xFF1E2E3A);
            }
            // Above 1x helps the colony, below 1x hurts it: green or red.
            String text = detailed() ? String.format(Locale.ROOT, "x%.2f", multiplier) : multiplier >= 1.0 ? "+" : "-";
            label(graphics, text, x + 76 + max * 6, y, multiplier > 1.001 ? GOOD : multiplier < 0.999 ? BAD : DIM);
            y += 8;
        }
        y += 2;
        if (genome.mutations().isEmpty()) {
            label(graphics, Component.translatable("gui.antcolonies.no_mutations"), x, y, DIM);
            return;
        }
        int cx = x;
        for (Identifier id : genome.mutations()) {
            Mutation mutation = ModRegistries.lookup(ClientSpecies.registries(), ModRegistries.MUTATION)
                    .flatMap(lookup -> lookup.get(net.minecraft.resources.ResourceKey.create(ModRegistries.MUTATION, id)))
                    .map(net.minecraft.core.Holder::value).orElse(null);
            boolean bad = mutation != null && mutation.negative();
            String name = Component.translatable(Mutation.translationKey(id)).getString();
            int w = font.width(name) + 7;
            if (cx + w > CONTENT_X + CONTENT_W - 4) {
                cx = x;
                y += 11;
            }
            if (y > CONTENT_Y + CONTENT_H - 10) {
                break;
            }
            chip(graphics, name, cx, y, bad ? BAD : GOOD);
            cx += w + 3;
        }
    }

    // --- Network ---

    private void network(GuiGraphicsExtractor graphics, Colony colony, int mouseX, int mouseY) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        List<Colony.NetworkLink> links = colony.network();
        if (links.isEmpty()) {
            y = wrapped(graphics, Component.translatable("gui.antcolonies.network.none"), x, y,
                    CONTENT_W - 8, 9, DIM);
            graphics.item(new ItemStack(ModBlocks.STRUCTURES.get(StructureKind.COLONY_CONNECTOR).get()), leftPos + CONTENT_X + CONTENT_W / 2 - 8, topPos + y + 8);
            return;
        }
        if (detailed()) {
            // A table, one colony a row.
            String[] headers = {"colony", "food", "mode", "flow"};
            int[] columns = {x + 10, x + 52, x + 82, x + 128};
            for (int c = 0; c < headers.length; c++) {
                int end = c + 1 < columns.length ? columns[c + 1] - 3 : CONTENT_X + CONTENT_W - 4;
                label(graphics, fit(Component.translatable("gui.antcolonies.network.col." + headers[c]).getString(), end - columns[c]), columns[c], y, FAINT);
            }
            y += 10;
            for (int i = 0; i < links.size(); i++) {
                Colony.NetworkLink link = links.get(i);
                if (y > CONTENT_Y + CONTENT_H - 10) {
                    break;
                }
                graphics.fill(leftPos + x, topPos + y, leftPos + x + 6, topPos + y + 7, 0xFF000000 | DyeColor.byId(link.channel()).getTextureDiffuseColor());
                String who = link.self() ? Component.translatable("gui.antcolonies.network.self.short").getString()
                        : link.terrarium().getX() + "," + link.terrarium().getZ();
                label(graphics, fit(who, 39), x + 10, y, link.online() ? LABEL : FAINT);
                if (link.online()) {
                    label(graphics, fit(percent(link.food() / Math.max(1, link.capacity())), 27), x + 52, y, INFO);
                    label(graphics, fit(modeName(link), 43), x + 82, y, DIM);
                    label(graphics, fit("+" + rate(link.inPerMinute()) + " -" + rate(link.outPerMinute()), CONTENT_X + CONTENT_W - 4 - (x + 128)), x + 128, y,
                            link.inPerMinute() > 0 ? GOOD : DIM);
                } else {
                    label(graphics, fit(Component.translatable("gui.antcolonies.network.offline").getString(), CONTENT_X + CONTENT_W - 4 - (x + 52)), x + 52, y, FAINT);
                }
                nodes.add(new int[] {x, y, 0, i, CONTENT_W - 8});
                y += 9;
            }
            return;
        }
        // A picture: this colony in the middle, the others round it, joined by lines in their channel's colour; green
        // dots move along lines into a colony that receives food, orange ones out of a colony that gives it.
        int cx = CONTENT_X + CONTENT_W / 2;
        int cy = CONTENT_Y + CONTENT_H / 2 + 2;
        List<Colony.NetworkLink> others = links.stream().filter(link -> !link.self()).toList();
        Colony.NetworkLink self = links.stream().filter(Colony.NetworkLink::self).findFirst().orElse(null);
        long time = System.currentTimeMillis();
        for (int i = 0; i < others.size(); i++) {
            Colony.NetworkLink link = others.get(i);
            // Round this colony on an ellipse, the first to its right.
            double angle = i * Math.PI * 2 / others.size();
            int nx = cx + (int) Math.round(Math.cos(angle) * 66);
            int ny = cy + (int) Math.round(Math.sin(angle) * 34);
            int color = 0xFF000000 | DyeColor.byId(link.channel()).getTextureDiffuseColor();
            dottedLine(graphics, cx, cy, nx, ny, link.online() ? color : 0xFF3A4A55);
            if (link.online()) {
                // Flow: dots running towards the colony that takes the food.
                boolean inbound = link.outPerMinute() > 0.01;
                boolean outbound = link.inPerMinute() > 0.01;
                if (inbound || outbound) {
                    for (int d = 0; d < 3; d++) {
                        double t = ((time / 30.0 + d * 33) % 100) / 100.0;
                        double f = inbound ? 1 - t : t;
                        int px = (int) Math.round(cx + (nx - cx) * f);
                        int py = (int) Math.round(cy + (ny - cy) * f);
                        graphics.fill(leftPos + px - 1, topPos + py - 1, leftPos + px + 1, topPos + py + 1, inbound ? GOOD : 0xFFFFA040);
                    }
                }
            }
            node(graphics, link, nx, ny, 13);
            nodes.add(new int[] {nx - 13, ny - 13, 26, links.indexOf(link), 26});
        }
        if (self != null) {
            node(graphics, self, cx, cy, 15);
            nodes.add(new int[] {cx - 15, cy - 15, 30, links.indexOf(self), 30});
        }
        label(graphics, Component.translatable("gui.antcolonies.network.count", others.size()), x, CONTENT_Y + 5, DIM);
    }

    private String modeName(Colony.NetworkLink link) {
        String key = switch (link.mode()) {
            case 1 -> "gui.antcolonies.connector.food.supply";
            case 2 -> "gui.antcolonies.connector.food.request";
            default -> "gui.antcolonies.connector.food.off";
        };
        return fit(Component.translatable(key).getString(), 42);
    }

    /** A colony on the network: a disc in its channel's colour with its ant, and a food bar under it. */
    private void node(GuiGraphicsExtractor graphics, Colony.NetworkLink link, int x, int y, int r) {
        int color = link.online() ? 0xFF000000 | DyeColor.byId(link.channel()).getTextureDiffuseColor() : 0xFF3A4A55;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            graphics.fill(leftPos + x - half, topPos + y + dy, leftPos + x + half, topPos + y + dy + 1, dy == -r || dy == r ? color : CARD_HI);
            graphics.fill(leftPos + x - half, topPos + y + dy, leftPos + x - half + 1, topPos + y + dy + 1, color);
            graphics.fill(leftPos + x + half - 1, topPos + y + dy, leftPos + x + half, topPos + y + dy + 1, color);
        }
        if (link.online()) {
            sideAnt(graphics, Caste.WORKER, ClientSpecies.look(link.species()), x - 12, y - 6, 1);
            drawBar(graphics, leftPos + x - r, topPos + y + r + 2, r * 2, 3, link.food() / Math.max(1, link.capacity()), link.paused() ? WARN : 0xFFE0B040);
        } else {
            label(graphics, "?", x - 2, y - 4, FAINT);
        }
    }

    private void dottedLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int px = (int) Math.round(x0 + (x1 - x0) * t);
            int py = (int) Math.round(y0 + (y1 - y0) * t);
            graphics.fill(leftPos + px, topPos + py, leftPos + px + 1, topPos + py + 1, color);
        }
    }

    private List<Component> nodeTooltip(Colony.NetworkLink link) {
        List<Component> lines = new ArrayList<>();
        lines.add(link.self() ? Component.translatable("gui.antcolonies.network.self") : Tooltips.species(link.species()));
        BlockPos pos = link.terrarium();
        lines.add(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("gui.antcolonies.network.channel",
                Component.translatable("color.minecraft." + DyeColor.byId(link.channel()).getName())).withStyle(ChatFormatting.GRAY));
        if (!link.online()) {
            lines.add(Component.translatable("gui.antcolonies.network.offline").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(Component.translatable("gui.antcolonies.network.food", number(link.food()), number(link.capacity())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(modeName(link)).withStyle(ChatFormatting.AQUA));
        if (link.mode() == 1) {
            lines.add(Component.translatable("gui.antcolonies.network.keeps", percent(link.reserve())).withStyle(ChatFormatting.GRAY));
        } else if (link.mode() == 2) {
            lines.add(Component.translatable("gui.antcolonies.network.asks", percent(link.target())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.antcolonies.network.flow", rate(link.inPerMinute()), rate(link.outPerMinute())).withStyle(ChatFormatting.GREEN));
        if (link.paused()) {
            lines.add(Component.translatable("status.antcolonies.paused").withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    // --- Statistics ---

    private void statistics(GuiGraphicsExtractor graphics, Colony colony, int mouseX, int mouseY) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 5;
        ColonyHistory history = colony.history();
        ColonyHistory.Series[] all = ColonyHistory.Series.values();
        // Series buttons down the left, each underlined in its chart colour.
        for (int i = 0; i < all.length; i++) {
            String text = Component.translatable(all[i].translationKey() + ".short").getString();
            toggle(x - 1, y + i * 15, 40, 13, text, BUTTON_SERIES - i, List.of(Component.translatable(all[i].translationKey())), series == i);
        }
        ColonyHistory.Series shown = all[Math.clamp(series, 0, all.length - 1)];
        int chartX = x + 46;
        // The scale sits outside the chart (its top value above it, the bottom one under it), never over the line.
        int chartY = y + 11;
        int chartW = CONTENT_W - 56;
        int chartH = detailed() ? 60 : 82;
        card(graphics, chartX - 2, chartY - 2, chartW + 4, chartH + 4, shown.color(), false);
        float[] values = history.values(shown);
        if (values.length < 2) {
            wrapped(graphics, Component.translatable("gui.antcolonies.stats.collecting", ColonyHistory.INTERVAL / 20), chartX + 2, chartY + 4, chartW - 4, 9, DIM);
            return;
        }
        // Food in and out together; any other series alone.
        float[] second = shown == ColonyHistory.Series.FOOD_IN ? history.values(ColonyHistory.Series.FOOD_OUT)
                : shown == ColonyHistory.Series.FOOD_OUT ? history.values(ColonyHistory.Series.FOOD_IN) : null;
        float max = 0;
        float min = Float.MAX_VALUE;
        for (float v : values) {
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        float lowest = min;
        float highest = max;
        if (second != null) {
            for (float v : second) {
                max = Math.max(max, v);
                min = Math.min(min, v);
            }
        }
        min = Math.min(0, min);
        float span = Math.max(0.0001F, max - min);
        // Grid lines at a quarter, half and three quarters.
        for (int g = 1; g < 4; g++) {
            int gy = chartY + chartH - chartH * g / 4;
            graphics.fill(leftPos + chartX, topPos + gy, leftPos + chartX + chartW, topPos + gy + 1, 0x20FFFFFF);
        }
        if (second != null) {
            plot(graphics, second, chartX, chartY, chartW, chartH, min, span, (shown == ColonyHistory.Series.FOOD_IN ? ColonyHistory.Series.FOOD_OUT
                    : ColonyHistory.Series.FOOD_IN).color() & 0x99FFFFFF);
        }
        plot(graphics, values, chartX, chartY, chartW, chartH, min, span, shown.color());
        // The top of the scale and the time span above the chart, the bottom of the scale under it.
        String minutes = Component.translatable("gui.antcolonies.stats.span", (values.length - 1) * ColonyHistory.INTERVAL / 1200.0).getString();
        value(graphics, fit("▲ " + number(max), chartW - font.width(minutes) - 6), chartX - 2, chartY - 11, LABEL);
        label(graphics, minutes, chartX + chartW + 2 - font.width(minutes), chartY - 11, DIM);
        value(graphics, "▼ " + number(min), chartX - 2, chartY + chartH + 4, LABEL);
        // The value under the mouse.
        if (over(mouseX, mouseY, chartX, chartY, chartW, chartH)) {
            int index = (int) Math.round((mouseX - leftPos - chartX) / (double) chartW * (values.length - 1));
            index = Math.clamp(index, 0, values.length - 1);
            int px = chartX + (int) Math.round(index * chartW / (double) (values.length - 1));
            graphics.fill(leftPos + px, topPos + chartY, leftPos + px + 1, topPos + chartY + chartH, 0x60FFFFFF);
            hoverStat = List.of(Component.translatable(shown.translationKey()), Component.literal(number(values[index])).withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.antcolonies.stats.ago", (values.length - 1 - index) * ColonyHistory.INTERVAL / 1200.0)
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (detailed()) {
            float sum = 0;
            for (float v : values) {
                sum += v;
            }
            int sy = chartY + chartH + 14;
            label(graphics, fit(Component.translatable("gui.antcolonies.stats.range", number(lowest), number(highest)).getString(), chartW + 4),
                    chartX - 2, sy, LABEL);
            label(graphics, fit(Component.translatable("gui.antcolonies.stats.average", number(sum / values.length),
                    number(values[values.length - 1])).getString(), chartW + 4), chartX - 2, sy + 10, LABEL);
        }
    }

    private @Nullable List<Component> hoverStat;

    private void plot(GuiGraphicsExtractor graphics, float[] values, int x, int y, int w, int h, float min, float span, int color) {
        int fill = (color & 0x00FFFFFF) | 0x22000000;
        int last = Math.max(1, values.length - 1);
        // A faint area under the line, filled once per pixel column.
        for (int col = 0; col <= w; col++) {
            double at = col * last / (double) Math.max(1, w);
            int i = Math.min(last - 1, (int) at);
            double t = values.length < 2 ? 0 : at - i;
            double v = values.length < 2 ? values[0] : values[i] + (values[Math.min(last, i + 1)] - values[i]) * t;
            int py = y + h - 1 - (int) Math.round((v - min) / span * (h - 2));
            graphics.fill(leftPos + x + col, topPos + py + 1, leftPos + x + col + 1, topPos + y + h, fill);
        }
        // Then the line itself, one pixel wide.
        int lastX = -1;
        int lastY = -1;
        for (int i = 0; i < values.length; i++) {
            int px = x + (int) Math.round(i * w / (double) last);
            int py = y + h - 1 - (int) Math.round((values[i] - min) / span * (h - 2));
            if (lastX >= 0) {
                int steps = Math.max(Math.abs(px - lastX), Math.abs(py - lastY));
                for (int s = 0; s <= steps; s++) {
                    double t = steps == 0 ? 0 : s / (double) steps;
                    int lx = (int) Math.round(lastX + (px - lastX) * t);
                    int ly = (int) Math.round(lastY + (py - lastY) * t);
                    graphics.fill(leftPos + lx, topPos + ly, leftPos + lx + 1, topPos + ly + 1, color);
                }
            }
            lastX = px;
            lastY = py;
        }
    }

    // --- Species (the Info page) ---

    private void species(GuiGraphicsExtractor graphics, Colony colony) {
        boolean right = !ColonyStatus.CLIMATE.in(colony.status());
        info.species(font, colony.species(), ClientSpecies.get(colony.species()), right, menu.terrarium().floor(), CONTENT_W - 8);
        info.draw(graphics, font, leftPos + CONTENT_X + 4, topPos + CONTENT_Y + 4, CONTENT_H - 6);
    }

    // --- Tooltips ---

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (page == 7 && hoveredSlot == null) {
            List<Component> lines = info.hover(mouseX, mouseY);
            if (lines != null) {
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && slotKind(hoveredSlot) != null) {
            String key = "gui.antcolonies.slot." + slotKind(hoveredSlot);
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(key),
                    Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (page == 4 && menu.colony().queen() != null) {
            if (over(mouseX, mouseY, CONTENT_X + 2, CONTENT_Y + 3, 150, 10)) {
                // The Queen herself: her lineage, and how far she has grown with her colony.
                List<Component> lines = new ArrayList<>();
                Tooltips.queen(menu.colony().queen(), lines::add, true);
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
            for (Trait trait : Trait.values()) {
                if (over(mouseX, mouseY, CONTENT_X + 2, CONTENT_Y + 16 + trait.ordinal() * 8 - 1, CONTENT_W - 4, 8)) {
                    int level = menu.colony().queen().genome().level(trait);
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(trait.translationKey()),
                            Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.antcolonies.trait.level", level, String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level)))
                                    .withStyle(ChatFormatting.AQUA)), mouseX, mouseY);
                    return;
                }
            }
        }
        if (page == 3) {
            List<Find> finds = finds(menu.colony());
            if (!detailed()) {
                for (int i = 0; i < finds.size() && i < 14; i++) {
                    int cx = CONTENT_X + 4 + (i % 7) * 27;
                    int cy = CONTENT_Y + 26 + (i / 7) * 33;
                    if (over(mouseX, mouseY, cx, cy, 25, 31)) {
                        Find find = finds.get(i);
                        graphics.setComponentTooltipForNextFrame(font, List.of(find.item().getHoverName(),
                                Component.translatable("gui.antcolonies.production.stored", number(find.stored())).withStyle(ChatFormatting.AQUA),
                                Component.translatable("gui.antcolonies.production.rate", rate(find.perMinute())).withStyle(ChatFormatting.GREEN)), mouseX, mouseY);
                        return;
                    }
                }
            }
        }
        if (page == 5) {
            List<Colony.NetworkLink> links = menu.colony().network();
            for (int[] node : nodes) {
                boolean hit = detailed() ? over(mouseX, mouseY, node[0], node[1], node[4], 9) : over(mouseX, mouseY, node[0], node[1], node[2], node[2]);
                if (hit && node[3] < links.size()) {
                    graphics.setComponentTooltipForNextFrame(font, nodeTooltip(links.get(node[3])), mouseX, mouseY);
                    return;
                }
            }
        }
        if (page == 1) {
            String[] names = {"queen", "eggs", "larvae", "workers", "specialists"};
            for (int i = 0; i < 5; i++) {
                if (over(mouseX, mouseY, cardX(i), CONTENT_Y + 5, 36, 44)) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.card." + names[i]),
                            Component.translatable("gui.antcolonies.card.click").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
                    return;
                }
            }
        }
        if (page == 6 && hoverStat != null) {
            graphics.setComponentTooltipForNextFrame(font, hoverStat, mouseX, mouseY);
            hoverStat = null;
            return;
        }
        hoverStat = null;
        if (page == 2) {
            List<Colony.Assignment> list = menu.colony().assignments();
            for (int row = 0; row < JOB_ROWS && jobScroll + row < list.size(); row++) {
                Colony.Assignment job = list.get(jobScroll + row);
                if (over(mouseX, mouseY, CONTENT_X + 2, CONTENT_Y + 16 + row * JOB_ROW_H - 1, CONTENT_W - 4, 12)) {
                    graphics.setComponentTooltipForNextFrame(font, jobTooltip(job), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private List<Component> jobTooltip(Colony.Assignment job) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(job.kind().translationKey()));
        lines.add(Component.literal(job.pos().getX() + ", " + job.pos().getY() + ", " + job.pos().getZ()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(job.kind() == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.farmers"
                : job.kind() == StructureKind.HUNTING_GROUND ? "gui.antcolonies.site.hunters" : "gui.antcolonies.site.workers", job.assigned(), job.capacity(),
                job.requested() < 0 ? Component.translatable("gui.antcolonies.auto") : Component.literal(String.valueOf(job.requested())))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(job.kind() == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.food_rate"
                : job.kind() == StructureKind.HUNTING_GROUND ? "gui.antcolonies.site.hunt_rate" : "gui.antcolonies.site.rate", rate(job.rate()))
                .withStyle(ChatFormatting.GRAY));
        if (job.kind() == StructureKind.FORAGING_SITE) {
            lines.add(Component.translatable(ForagingMode.byId(job.mode()).translationKey()).withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.antcolonies.priority." + job.priority()).withStyle(ChatFormatting.GRAY));
        for (ColonyStatus status : ColonyStatus.of(job.status())) {
            lines.add(Component.translatable(status.translationKey()).withStyle(status.color()));
        }
        if (job.kind() == StructureKind.HARVESTING_SITE) {
            lines.add(Component.translatable("gui.antcolonies.farm.hint").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
