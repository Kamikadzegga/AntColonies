package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.content.menu.TerrariumMenu;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The colony overview: five pages (colony, genetics, jobs, production, species) over the Terrarium's slots. Everything shown is
 * the server's numbers, synced with the Terrarium.
 */
public class TerrariumScreen extends AntScreen<TerrariumMenu> {
    private static final String[] PAGES = {"overview", "genetics", "jobs", "production", "species"};
    private static final int CONTENT_X = 48;
    private static final int CONTENT_Y = 20;
    private static final int CONTENT_W = 160;
    private static final int CONTENT_H = 106;
    private static final int JOB_ROWS = 3;
    private static final int JOB_ROW_H = 27;
    private static int page;
    private int jobScroll;
    private final InfoPanel info = new InfoPanel();

    public TerrariumScreen(TerrariumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 214, 222);
        inventoryLabelX = TerrariumMenu.INVENTORY_X;
        inventoryLabelY = TerrariumMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Tabs as wide as their names, the spare room shared out evenly, together spanning the content panel.
        String[] names = new String[PAGES.length];
        int textTotal = 0;
        for (int i = 0; i < PAGES.length; i++) {
            names[i] = Component.translatable("gui.antcolonies.page." + PAGES[i]).getString();
            textTotal += font.width(names[i]);
        }
        int gap = 1;
        int spare = Math.max(0, CONTENT_W - textTotal - gap * (PAGES.length - 1));
        int tabX = CONTENT_X;
        for (int i = 0; i < PAGES.length; i++) {
            int w = i == PAGES.length - 1 ? CONTENT_X + CONTENT_W - tabX : font.width(names[i]) + spare / PAGES.length;
            toggle(tabX, 4, w, 14, names[i], -100 - i, null, page == i);
            tabX += w + gap;
        }
        drawWell(graphics, leftPos + CONTENT_X, topPos + CONTENT_Y, CONTENT_W, CONTENT_H);
        Colony colony = menu.colony();
        switch (page) {
            case 0 -> overview(graphics, colony);
            case 1 -> genetics(graphics, colony);
            case 2 -> jobs(graphics, colony);
            case 3 -> production(graphics, colony);
            default -> species(graphics, colony);
        }
    }

    /** Opens a page (for the in-game showcase). */
    public static void showPage(int index) {
        page = Math.clamp(index, 0, PAGES.length - 1);
    }

    @Override
    protected void onButton(int id) {
        if (id <= -100) {
            page = -100 - id;
            clickSound();
            return;
        }
        super.onButton(id);
    }


    /** Which Terrarium slot this is (queen, feed, output, upgrade), or null for the player's inventory. */
    private static String slotKind(Slot slot) {
        return slot.index == 0 ? "queen" : slot.index == 1 ? "feed" : slot.index == 2 ? "output" : slot.index < 3 + com.antcolonies.content.blockentity.TerrariumBlockEntity.UPGRADE_SLOTS ? "upgrade" : null;
    }

    @Override
    protected void drawSlotBackground(GuiGraphicsExtractor graphics, Slot slot, int x, int y) {
        super.drawSlotBackground(graphics, slot, x, y);
        String kind = slotKind(slot);
        if (kind != null && !slot.hasItem()) {
            drawGlyph(graphics, kind, x, y);
        }
    }

    // --- Species: what this colony's ants are, and whether the Terrarium suits them ---

    private void species(GuiGraphicsExtractor graphics, Colony colony) {
        boolean right = !ColonyStatus.CLIMATE.in(colony.status());
        info.species(font, colony.species(), ClientSpecies.get(colony.species()), right, menu.terrarium().floor(), CONTENT_W - 8);
        info.draw(graphics, font, leftPos + CONTENT_X + 4, topPos + CONTENT_Y + 4, CONTENT_H - 6);
    }

    // --- Overview ---

    private void overview(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 4;
        AntSpecies species = ClientSpecies.get(colony.species());
        int speciesColor = species == null ? LABEL : bright(species.look().crystal().orElse(species.look().gaster()));
        label(graphics, Tooltips.species(colony.species()), x, y, speciesColor);
        Component stage = Component.translatable("gui.antcolonies.stage", colony.stage());
        label(graphics, stage, CONTENT_X + CONTENT_W - 4 - font.width(stage), y, INFO);
        y += 11;
        QueenData queen = colony.queen();
        if (queen != null) {
            label(graphics, Component.translatable("gui.antcolonies.queen", queen.genome().lineage().name()).append(" ")
                    .append(Tooltips.rarity(queen.genome().rarity())), x, y, LABEL);
            button(CONTENT_X + CONTENT_W - 18, y - 2, 14, 11, "X", TerrariumMenu.BUTTON_REMOVE_QUEEN,
                    List.of(Component.translatable("gui.antcolonies.remove_queen"), Component.translatable("gui.antcolonies.remove_queen.hint")
                            .withStyle(ChatFormatting.GRAY)), true);
        } else {
            label(graphics, Component.translatable("status.antcolonies.no_queen"), x, y, BAD);
        }
        y += 11;
        Colony.Stats stats = colony.stats();
        label(graphics, Component.translatable("gui.antcolonies.workers", number(colony.workers())), x, y, LABEL);
        label(graphics, Component.translatable("gui.antcolonies.soldiers", number(colony.soldiers())), x + 80, y, LABEL);
        y += 10;
        label(graphics, Component.translatable("gui.antcolonies.larvae", number(colony.larvae())), x, y, LABEL);
        label(graphics, Component.translatable("gui.antcolonies.eggs", number(colony.eggs())), x + 80, y, LABEL);
        y += 10;
        label(graphics, Component.translatable("gui.antcolonies.housing", number(colony.adults()), number(stats.adultCapacity())), x, y, DIM);
        y += 11;
        // Food and health.
        double capacity = Math.max(1, stats.foodCapacity());
        label(graphics, Component.translatable("gui.antcolonies.food"), x, y, LABEL);
        drawBar(graphics, leftPos + x + 34, topPos + y, 82, 8, colony.food() / capacity, colony.food() <= 0 ? BAD : 0xFFE0B040);
        label(graphics, number(colony.food()), x + 120, y, DIM);
        y += 10;
        label(graphics, Component.translatable("gui.antcolonies.health"), x, y, LABEL);
        drawBar(graphics, leftPos + x + 34, topPos + y, 82, 8, colony.health(), colony.health() < 0.5 ? BAD : GOOD);
        label(graphics, Math.round(colony.health() * 100) + "%", x + 120, y, DIM);
        y += 11;
        // Soldier share.
        label(graphics, Component.translatable("gui.antcolonies.soldier_share", Math.round(colony.soldierShare() * 100)), x, y, LABEL);
        button(x + 116, y - 2, 12, 11, "-", TerrariumMenu.BUTTON_SOLDIERS_LESS, List.of(Component.translatable("gui.antcolonies.soldier_share.hint")), true);
        button(x + 130, y - 2, 12, 11, "+", TerrariumMenu.BUTTON_SOLDIERS_MORE, List.of(Component.translatable("gui.antcolonies.soldier_share.hint")), true);
        y += 11;
        if (colony.formerSpecies() != null) {
            label(graphics, Component.translatable("gui.antcolonies.transforming", Tooltips.species(colony.formerSpecies()),
                    Math.round(colony.conversion() * 100)), x, y, ACCENT);
            y += 10;
        }
        // Problems first, then notes.
        List<ColonyStatus> statuses = new ArrayList<>(ColonyStatus.of(colony.status() | menu.terrarium().feedStatus()));
        statuses.sort((a, b) -> Boolean.compare(b.problem(), a.problem()));
        for (ColonyStatus status : statuses) {
            if (y > CONTENT_Y + CONTENT_H - 10) {
                break;
            }
            int color = status == ColonyStatus.WORKING ? GOOD : status.problem() ? BAD : WARN;
            label(graphics, fit(Component.translatable(status.translationKey()).getString(), CONTENT_W - 8), x, y, color);
            y += 9;
        }
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

    // --- Genetics ---

    private void genetics(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 4;
        QueenData queen = colony.queen();
        if (queen == null) {
            label(graphics, Component.translatable("status.antcolonies.no_queen"), x, y, BAD);
            return;
        }
        Genome genome = queen.genome();
        label(graphics, Component.translatable("gui.antcolonies.genetics_of", genome.lineage().name(), genome.lineage().generation()), x, y, LABEL);
        y += 11;
        int max = Trait.maxLevel();
        for (Trait trait : Trait.values()) {
            int level = genome.level(trait);
            if (trait.ordinal() % 2 == 0) {
                graphics.fill(leftPos + CONTENT_X + 2, topPos + y - 1, leftPos + CONTENT_X + CONTENT_W - 2, topPos + y + 7, 0x30000000);
            }
            label(graphics, fit(Component.translatable(trait.translationKey() + ".short").getString(), 68), x, y, LABEL);
            for (int i = 0; i < max; i++) {
                int sx = leftPos + x + 72 + i * 5;
                graphics.fill(sx, topPos + y + 1, sx + 4, topPos + y + 7, i < level ? levelColor(level) : 0xFF1E2E3A);
            }
            label(graphics, String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level)), x + 74 + max * 5, y, DIM);
            y += 8;
        }
        y += 2;
        if (genome.mutations().isEmpty()) {
            label(graphics, Component.translatable("gui.antcolonies.no_mutations"), x, y, DIM);
        } else {
            StringBuilder names = new StringBuilder();
            for (Identifier mutation : genome.mutations()) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(Component.translatable(Mutation.translationKey(mutation)).getString());
            }
            label(graphics, fit(Component.translatable("gui.antcolonies.mutations").getString() + " " + names, CONTENT_W - 8), x, y, ACCENT);
        }
    }

    private static int levelColor(int level) {
        return 0xFF000000 | Tooltips.levelRgb(level);
    }

    // --- Jobs ---

    private void jobs(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 4;
        List<Colony.Assignment> list = colony.assignments();
        label(graphics, fit(Component.translatable("gui.antcolonies.jobs_header", colony.maintenanceWorkers()).getString(), CONTENT_W - 8), x, y, LABEL);
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
            // A card per site. Top line: icon, workers, rate; bottom line: the step buttons and Auto, sized to their text.
            graphics.fill(leftPos + CONTENT_X + 2, topPos + rowY - 1, leftPos + CONTENT_X + CONTENT_W - 2, topPos + rowY + JOB_ROW_H - 2, 0x40000000);
            graphics.item(new ItemStack(ModBlocks.STRUCTURES.get(job.kind()).get()), leftPos + x, topPos + rowY + 3);
            int color = job.status() != 0 && ColonyStatus.of(job.status()).stream().anyMatch(ColonyStatus::problem) ? BAD : LABEL;
            Component workers = job.kind().job().soldiers()
                    ? Component.translatable("gui.antcolonies.job_foraging", job.assigned(), job.soldiers(), job.capacity())
                    : Component.translatable("gui.antcolonies.job_workers", job.assigned(), job.capacity());
            int right = CONTENT_X + CONTENT_W - 4;
            // The ants, then the rate on the right if both fit; otherwise the ants alone (the tooltip has the rate).
            String perMinute = rate(job.rate()) + Component.translatable("gui.antcolonies.per_minute").getString();
            int room = right - (x + 19);
            boolean rateFits = font.width(workers) + 6 + font.width(perMinute) <= room;
            label(graphics, fit(workers.getString(), room), x + 19, rowY + 2, color);
            if (rateFits) {
                label(graphics, perMinute, right - font.width(perMinute) + 1, rowY + 2, DIM);
            }
            int base = TerrariumMenu.BUTTON_ASSIGNMENT + index * 20;
            String[] steps = {"less_10", "less_1", "more_1", "more_10"};
            int[] ids = {StructureMenu.LESS_10, StructureMenu.LESS, StructureMenu.MORE, StructureMenu.MORE_10};
            int bx = x + 19;
            for (int i = 0; i < steps.length; i++) {
                Component text = Component.translatable("gui.antcolonies." + steps[i]);
                int w = buttonWidth(text.getString());
                button(bx, rowY + 12, w, 11, text.getString(), base + ids[i], List.of(text), true);
                bx += w + 2;
            }
            String auto = Component.translatable("gui.antcolonies.auto").getString();
            int autoW = buttonWidth(auto);
            toggle(right - autoW, rowY + 12, autoW, 11, auto, base + StructureMenu.AUTO, List.of(Component.translatable("gui.antcolonies.auto.hint")),
                    job.requested() < 0);
        }
        if (list.size() > JOB_ROWS) {
            label(graphics, (jobScroll + 1) + "-" + Math.min(list.size(), jobScroll + JOB_ROWS) + " / " + list.size(), x, CONTENT_Y + CONTENT_H - 9, DIM);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (page == 2 && over(x, y, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)) {
            jobScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (page == 4 && over(x, y, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)) {
            info.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // --- Production ---

    private void production(GuiGraphicsExtractor graphics, Colony colony) {
        int x = CONTENT_X + 4;
        int y = CONTENT_Y + 4;
        Colony.Stats stats = colony.stats();
        String perMinute = Component.translatable("gui.antcolonies.per_minute").getString();
        Object[][] rows = {
                {"gui.antcolonies.stat.eggs", rate(stats.eggsLaid()) + perMinute, LABEL},
                {"gui.antcolonies.stat.raised", rate(stats.adultsRaised()) + perMinute, LABEL},
                {"gui.antcolonies.stat.food_in", rate(stats.foodIn()) + perMinute, GOOD},
                {"gui.antcolonies.stat.food_out", rate(stats.foodOut()) + perMinute, stats.foodOut() > stats.foodIn() ? BAD : LABEL},
                {"gui.antcolonies.stat.finds", rate(stats.finds()) + perMinute, LABEL},
                {"gui.antcolonies.stat.hunts", rate(stats.hunts()) + perMinute, LABEL},
                {"gui.antcolonies.stat.deaths", rate(stats.deaths()) + perMinute, DIM},
                {"gui.antcolonies.stat.soldier_room", number(stats.soldierCapacity()), LABEL},
                {"gui.antcolonies.stat.larvae_room", number(stats.larvaeCapacity()), LABEL},
                {"gui.antcolonies.stat.structures", String.valueOf(stats.structures()), LABEL},
                {"gui.antcolonies.stat.found_total", number(colony.totalFound()), INFO},
        };
        for (Object[] row : rows) {
            label(graphics, Component.translatable((String) row[0]), x, y, LABEL);
            label(graphics, (String) row[1], x + 112, y, (Integer) row[2]);
            y += 9;
        }
        if (colony.catalystCharges() > 0 || colony.evolutionAttempts() > 0) {
            label(graphics, Component.translatable("gui.antcolonies.evolution_attempts", colony.evolutionAttempts(), colony.catalystCharges()), x, y, ACCENT);
        }
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (page == 4 && hoveredSlot == null) {
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
        if (page == 1 && menu.colony().queen() != null) {
            for (Trait trait : Trait.values()) {
                if (over(mouseX, mouseY, CONTENT_X + 2, CONTENT_Y + 15 + trait.ordinal() * 8 - 1, CONTENT_W - 4, 8)) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(trait.translationKey()),
                            Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                    return;
                }
            }
        }
        if (page == 2) {
            List<Colony.Assignment> list = menu.colony().assignments();
            for (int row = 0; row < JOB_ROWS && jobScroll + row < list.size(); row++) {
                Colony.Assignment job = list.get(jobScroll + row);
                if (over(mouseX, mouseY, CONTENT_X + 2, CONTENT_Y + 15 + row * JOB_ROW_H - 1, CONTENT_W - 4, 12)) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable(job.kind().translationKey()));
                    lines.add(Component.literal(job.pos().getX() + ", " + job.pos().getY() + ", " + job.pos().getZ()).withStyle(ChatFormatting.DARK_GRAY));
                    // The full numbers: the card may have no room for the rate.
                    lines.add(Component.translatable(job.kind() == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.farmers"
                            : "gui.antcolonies.site.workers", job.assigned(), job.capacity(), job.requested() < 0
                            ? Component.translatable("gui.antcolonies.auto") : Component.literal(String.valueOf(job.requested())))
                            .withStyle(ChatFormatting.GRAY));
                    if (job.kind().job().soldiers()) {
                        lines.add(Component.translatable("gui.antcolonies.job_soldiers", job.soldiers(), job.capacity()).withStyle(ChatFormatting.GRAY));
                    }
                    lines.add(Component.translatable(job.kind() == StructureKind.HARVESTING_SITE ? "gui.antcolonies.site.food_rate"
                            : "gui.antcolonies.site.rate", rate(job.rate())).withStyle(ChatFormatting.GRAY));
                    for (ColonyStatus status : ColonyStatus.of(job.status())) {
                        lines.add(Component.translatable(status.translationKey()).withStyle(status.color()));
                    }
                    if (job.kind() == StructureKind.HARVESTING_SITE) {
                        lines.add(Component.translatable("gui.antcolonies.farm.hint").withStyle(ChatFormatting.GRAY));
                    }
                    graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                    return;
                }
            }
        }
    }
}
