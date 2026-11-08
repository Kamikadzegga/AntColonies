package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.menu.GeneticLabMenu;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Cost;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Genetic Laboratory console. Left: the specimen capsule with the Queen, her name and the lab's Genetic Energy.
 * Middle: her genome, one row per trait (click to select). Right: the selected trait in full, what raising it costs
 * (the items it needs, green when the material slots below hold enough), and the Upgrade button. Underneath: upgrades,
 * the catalyst slot with the lab's other tools, and a strip that shows the work in progress or what is wrong.
 */
public class GeneticLabScreen extends AntScreen<GeneticLabMenu> {
    // Layout (relative to the panel).
    private static final int LEFT_X = 6;
    private static final int LEFT_W = 76;
    private static final int LIST_X = 86;
    private static final int LIST_W = 128;
    private static final int DETAIL_X = 218;
    private static final int DETAIL_W = 96;
    private static final int PANEL_Y = 20;
    private static final int PANEL_H = 106;
    private static final int ROW_Y = 23;
    private static final int ROW_H = 10;
    private static final int TOOL_BUTTON_X = 110;
    private static final int TOOL_BUTTON_W = 48;
    private static final int STRIP_Y = 151;

    // Console palette.
    private static final int TEXT = 0xFFD8F2FF;
    private static final int TEXT_DIM = 0xFF7896A8;
    private static final int TEXT_FAINT = 0xFF4E6676;
    private static final int CYAN = 0xFF4FE3FF;
    private static final int MAGENTA = 0xFFE07AFF;
    private static final int GREEN = 0xFF6CF08A;
    private static final int RED = 0xFFFF6E6E;
    private static final int GOLD = 0xFFFFD36A;
    private static final int PIP_EMPTY = 0xFF1E2E3A;

    public GeneticLabScreen(GeneticLabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GeneticLabMenu.WIDTH, GeneticLabMenu.HEIGHT);
    }

    private void panelSprite(GuiGraphicsExtractor graphics, String name, int x, int y, int w, int h) {
        sprite(graphics, name, leftPos + x, topPos + y, w, h);
    }

    private float time() {
        return minecraft.level == null ? 0 : minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private @Nullable QueenData queen() {
        return menu.queenStack().get(ModDataComponents.QUEEN.get());
    }

    private Trait selected() {
        return Trait.values()[Math.clamp(menu.data(GeneticLabBlockEntity.DATA_TRAIT), 0, Trait.values().length - 1)];
    }

    /** Whether the lab has energy to work with. */
    private boolean powered() {
        return menu.data(GeneticLabBlockEntity.DATA_ENERGY) > 0;
    }

    private boolean idle() {
        return menu.data(GeneticLabBlockEntity.DATA_OPERATION) == 0;
    }

    // --- The frame, slots and buttons in the console's look ---

    @Override
    protected void drawFrame(GuiGraphicsExtractor graphics) {
        panelSprite(graphics, "frame", 0, 0, imageWidth, imageHeight);
        panelSprite(graphics, "glass", LEFT_X, 4, imageWidth - 12, 14);
        panelSprite(graphics, "glass", LEFT_X, PANEL_Y, LEFT_W, PANEL_H);
        panelSprite(graphics, "glass", LIST_X, PANEL_Y, LIST_W, PANEL_H);
        panelSprite(graphics, "glass", DETAIL_X, PANEL_Y, DETAIL_W, PANEL_H);
        panelSprite(graphics, "glass", LEFT_X, STRIP_Y - 1, imageWidth - 12, 11);
        panelSprite(graphics, "capsule", 30, 22, 28, 40);
    }


    /** A primary (glowing) button: the {@code selected} flag of the shared button record marks it. */
    private void primary(int x, int y, int w, int h, String label, int id, @Nullable List<Component> tooltip) {
        buttons.add(new Button(leftPos + x, topPos + y, w, h, label, id, tooltip, true, true));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, Component.translatable("gui.antcolonies.lab.title"), 10, 7, TEXT, false);
        Component state = Component.translatable(powered() ? "gui.antcolonies.lab.online" : "gui.antcolonies.lab.offline");
        int width = font.width(state);
        int chipX = imageWidth - 12 - width - 8;
        int color = powered() ? GREEN : RED;
        // A status light that pulses while the lab works.
        float pulse = idle() ? 1 : 0.6F + 0.4F * Mth.sin(time() * 0.3F);
        graphics.fill(chipX, 8, chipX + 5, 13, scaleAlpha(color, pulse));
        graphics.text(font, state, chipX + 8, 7, color, false);
    }

    private static int scaleAlpha(int color, float alpha) {
        return ((int) (((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0, 1)) << 24) | (color & 0xFFFFFF);
    }

    // --- The page ---

    @Override
    protected void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        QueenData queen = queen();
        specimen(graphics, queen);
        genome(graphics, queen, mouseX, mouseY);
        detail(graphics, queen);
        tools(graphics, queen);
        strip(graphics, queen);
    }

    /** The left column: the Queen under glass, who she is, and the lab's energy. */
    private void specimen(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        int center = LEFT_X + LEFT_W / 2;
        if (queen == null) {
            centered(graphics, fit(Component.translatable("gui.antcolonies.lab.no_queen").getString(), LEFT_W - 6), center, 66, TEXT_FAINT);
            centered(graphics, fit(Component.translatable("gui.antcolonies.lab.insert").getString(), LEFT_W - 6), center, 76, TEXT_FAINT);
        } else {
            Genome genome = queen.genome();
            centered(graphics, fit(genome.lineage().name(), LEFT_W - 6), center, 66, TEXT);
            AntLook look = ClientSpecies.look(queen.species());
            centered(graphics, fit(Tooltips.species(queen.species()).getString(), LEFT_W - 6), center, 76, bright(look.crystal().orElse(look.gaster())));
            Component rarity = Tooltips.rarity(genome.rarity());
            centered(graphics, rarity.getString() + " " + String.format(Locale.ROOT, "%.1f", genome.averageLevel()), center, 86,
                    0xFF000000 | Tooltips.levelRgb((int) Math.round(genome.averageLevel())));
        }
        // Genetic Energy: a glass tube filling with light, bubbles rising.
        label(graphics, Component.translatable("gui.antcolonies.lab.energy_label"), LEFT_X + 4, 97, TEXT_DIM);
        int energy = menu.data(GeneticLabBlockEntity.DATA_ENERGY);
        int x0 = leftPos + LEFT_X + 4;
        int y0 = topPos + 107;
        int w = LEFT_W - 8;
        graphics.fill(x0, y0, x0 + w, y0 + 7, 0xFF06121A);
        int filled = (int) Math.round((w - 2) * Math.clamp(energy / (double) GeneticLabBlockEntity.ENERGY_CAPACITY, 0, 1));
        if (filled > 0) {
            graphics.fillGradient(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 6, 0xFF7FF0FF, 0xFF1F8FB8);
            float t = time();
            for (int i = 0; i < 3; i++) {
                int bx = x0 + 1 + (int) ((t * 0.6F + i * 23) % Math.max(1, filled));
                graphics.fill(bx, y0 + 2 + i % 2, bx + 1, y0 + 3 + i % 2, 0xCCFFFFFF);
            }
        }
        graphics.outline(x0 - 1, y0 - 1, w + 2, 9, 0xFF2B6E86);
        centered(graphics, fe(energy), LEFT_X + LEFT_W / 2, 116, TEXT_DIM);
    }

    /** The middle: one row per trait, with its level as lit pips. */
    private void genome(GuiGraphicsExtractor graphics, @Nullable QueenData queen, int mouseX, int mouseY) {
        int max = Trait.maxLevel();
        Trait selected = selected();
        for (Trait trait : Trait.values()) {
            int row = trait.ordinal();
            int y = ROW_Y + row * ROW_H;
            int x0 = leftPos + LIST_X + 2;
            int x1 = leftPos + LIST_X + LIST_W - 2;
            if (trait == selected) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x553FB8E0);
                graphics.fill(x0, topPos + y - 1, x0 + 2, topPos + y + ROW_H - 1, CYAN);
            } else if (over(mouseX, mouseY, LIST_X + 2, y - 1, LIST_W - 4, ROW_H) && queen != null && idle()) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x22FFFFFF);
            } else if (row % 2 == 1) {
                graphics.fill(x0, topPos + y - 1, x1, topPos + y + ROW_H - 1, 0x0CFFFFFF);
            }
            int level = queen == null ? 0 : queen.genome().level(trait);
            label(graphics, fit(Component.translatable(trait.translationKey() + ".short").getString(), 60), LIST_X + 6, y,
                    trait == selected ? 0xFFFFFFFF : queen == null ? TEXT_FAINT : TEXT);
            for (int i = 0; i < max; i++) {
                int px = leftPos + LIST_X + 70 + i * 4;
                graphics.fill(px, topPos + y + 1, px + 3, topPos + y + 7, i < level ? 0xFF000000 | Tooltips.levelRgb(level) : PIP_EMPTY);
            }
            String number = queen == null ? "-" : String.valueOf(level);
            graphics.text(font, number, leftPos + LIST_X + LIST_W - 6 - font.width(number), topPos + y, level >= max ? GOLD : TEXT_DIM, false);
        }
    }

    /** The right: the selected trait, now and next, what it takes, and the Upgrade button. */
    private void detail(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        Trait trait = selected();
        int x = DETAIL_X + 5;
        List<String> name = wrap(Component.translatable(trait.translationKey()).getString(), DETAIL_W - 10);
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
            label(graphics, fit(Component.translatable(Trait.gradeKey(level + 1)).getString(), DETAIL_W - 10), x, 65, TEXT_DIM);
            costs(graphics, queen, x, 75);
        }
        if (!idle()) {
            button(DETAIL_X + 4, 114, DETAIL_W - 8, 11, Component.translatable("gui.antcolonies.lab.cancel_button").getString(),
                    GeneticLabMenu.BUTTON_CANCEL, List.of(Component.translatable("gui.antcolonies.lab.cancel")), true);
        } else if (maxed) {
            button(DETAIL_X + 4, 114, DETAIL_W - 8, 11, Component.translatable("gui.antcolonies.lab.max_button").getString(), -1, null, false);
        } else {
            primary(DETAIL_X + 4, 114, DETAIL_W - 8, 11, Component.translatable("gui.antcolonies.lab.upgrade").getString(),
                    GeneticLabMenu.BUTTON_UPGRADE, upgradeTooltip(queen, trait));
        }
    }

    /** The items raising the trait takes, each with its count green (enough in the material slots) or red. */
    private void costs(GuiGraphicsExtractor graphics, QueenData queen, int x, int y) {
        // The cost card: what it takes, set apart from the material slots under it.
        graphics.fill(leftPos + DETAIL_X + 3, topPos + y - 2, leftPos + DETAIL_X + DETAIL_W - 3, topPos + y + 17, 0x66000000);
        graphics.outline(leftPos + DETAIL_X + 3, topPos + y - 2, DETAIL_W - 6, 19, 0x553FB8E0);
        GeneticLabBlockEntity.Requirement requirement = requirement(queen);
        if (requirement == null) {
            return;
        }
        int cx = x;
        for (Cost cost : requirement.materials()) {
            ItemStack shown = shown(cost);
            graphics.item(shown, leftPos + cx, topPos + y);
            String count = String.valueOf(cost.count());
            boolean enough = have(cost) >= cost.count();
            graphics.nextStratum();
            graphics.text(font, count, leftPos + cx + 17 - font.width(count), topPos + y + 9, enough ? GREEN : RED, true);
            cx += 20;
        }
        // The energy it takes, with a small bolt.
        int bx = leftPos + cx + 2;
        int by = topPos + y + 3;
        boolean enough = menu.data(GeneticLabBlockEntity.DATA_ENERGY) >= requirement.energy();
        int bolt = enough ? CYAN : RED;
        graphics.fill(bx + 2, by, bx + 4, by + 4, bolt);
        graphics.fill(bx, by + 4, bx + 5, by + 5, bolt);
        graphics.fill(bx + 1, by + 5, bx + 3, by + 9, bolt);
        graphics.text(font, fe(requirement.energy()).replace(" FE", ""), bx + 7, by + 1, enough ? TEXT : RED, false);
    }

    private static ItemStack shown(Cost cost) {
        return cost.ingredient().items().findFirst().map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
    }

    private int have(Cost cost) {
        int found = 0;
        for (int i = 0; i < GeneticLabBlockEntity.MATERIAL_SLOTS; i++) {
            ItemStack stack = menu.getSlot(GeneticLabBlockEntity.SLOT_MATERIAL + i).getItem();
            if (cost.test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private GeneticLabBlockEntity.@Nullable Requirement requirement(QueenData queen) {
        return minecraft.level == null ? null : menu.lab().requirement(GeneticLabBlockEntity.Operation.UPGRADE, queen, minecraft.level.registryAccess());
    }


    /** The row under the panels: upgrades (they need no buttons), the catalyst slot and the lab's tools. */
    private void tools(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        boolean ready = idle() && queen != null;
        int y = GeneticLabMenu.TOOLS_Y + 1;
        String[][] tools = {{"reroll", String.valueOf(GeneticLabMenu.BUTTON_REROLL)}, {"mutate", String.valueOf(GeneticLabMenu.BUTTON_MUTATE)},
                {"purge", String.valueOf(GeneticLabMenu.BUTTON_PURGE)}, {"sample", String.valueOf(GeneticLabMenu.BUTTON_SAMPLE)}};
        for (int i = 0; i < tools.length; i++) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.antcolonies.lab." + tools[i][0] + ".hint"));
            if (tools[i][0].equals("mutate")) {
                if (queen != null && ClientSpecies.registries() != null
                        && !com.antcolonies.colony.genetics.Genetics.roomForBeneficial(queen.genome().mutations(), ClientSpecies.registries())) {
                    tooltip.add(Component.translatable("gui.antcolonies.lab.mutation_cap",
                            com.antcolonies.core.config.AntConfig.get(com.antcolonies.core.config.AntConfig.MAX_BENEFICIAL_MUTATIONS)).withStyle(ChatFormatting.RED));
                }
                Component gives = catalystMutation(queen);
                if (gives != null) {
                    tooltip.add(Component.translatable("gui.antcolonies.lab.catalyst_gives", gives).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            button(TOOL_BUTTON_X + i * (TOOL_BUTTON_W + 4), y, TOOL_BUTTON_W, 14, Component.translatable("gui.antcolonies.lab." + tools[i][0]).getString(),
                    Integer.parseInt(tools[i][1]), tooltip, ready);
        }
    }

    /** The strip at the bottom of the console: the work in progress as a running helix, or what is wrong. */
    private void strip(GuiGraphicsExtractor graphics, @Nullable QueenData queen) {
        int x0 = leftPos + LEFT_X + 2;
        int x1 = leftPos + imageWidth - LEFT_X - 2;
        int y0 = topPos + STRIP_Y;
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
            String op = GeneticLabBlockEntity.Operation.byId(menu.data(GeneticLabBlockEntity.DATA_OPERATION)).name().toLowerCase(Locale.ROOT);
            Component text = Component.translatable("gui.antcolonies.lab.working." + op, Component.translatable(selected().translationKey()),
                    Math.round(progress * 100));
            graphics.centeredText(font, fit(text.getString(), x1 - x0 - 8), (x0 + x1) / 2, y0 + 1, 0xFFFFFFFF);
            return;
        }
        Component message;
        int color;
        int status = menu.data(GeneticLabBlockEntity.DATA_STATUS);
        ColonyStatus problem = ColonyStatus.of(status).stream().filter(ColonyStatus::problem).findFirst().orElse(null);
        if (queen == null) {
            message = Component.translatable("gui.antcolonies.lab.insert_queen");
            color = TEXT_DIM;
        } else if (problem != null) {
            message = Component.translatable(problem.translationKey());
            color = RED;
        } else {
            // The selected trait in a few words.
            message = Component.translatable("gui.antcolonies.lab.trait_hint", Component.translatable(selected().translationKey()),
                    Component.translatable(selected().translationKey() + ".desc"));
            color = TEXT_DIM;
        }
        graphics.centeredText(font, fit(message.getString(), x1 - x0 - 8), (x0 + x1) / 2, y0 + 1, color);
    }

    // --- Text helpers ---

    private void centered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        graphics.text(font, text, leftPos + centerX - font.width(text) / 2, topPos + y, color, false);
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
                lines.add(Component.literal(have(cost) + " / " + cost.count() + " ").append(shown(cost).getHoverName())
                        .withStyle(have(cost) >= cost.count() ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
            lines.add(Component.translatable("gui.antcolonies.lab.energy", fe(requirement.energy())).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable("gui.antcolonies.lab.time", requirement.time() / 20).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** The beneficial mutation the item in the catalyst slot would give this Queen, if any. */
    private @Nullable Component catalystMutation(@Nullable QueenData queen) {
        ItemStack catalyst = menu.getSlot(GeneticLabBlockEntity.SLOT_CATALYST).getItem();
        var registries = ClientSpecies.registries();
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
                return Component.translatable(Mutation.translationKey(entry.key().identifier()));
            }
        }
        return null;
    }

    // --- Clicks and tooltips ---

    @Override
    protected boolean clickPage(double mouseX, double mouseY) {
        if (!idle() || queen() == null) {
            return false;
        }
        for (int row = 0; row < Trait.values().length; row++) {
            if (over(mouseX, mouseY, LIST_X + 2, ROW_Y + row * ROW_H - 1, LIST_W - 4, ROW_H)) {
                onButton(row);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void extractPageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        QueenData queen = queen();
        for (Trait trait : Trait.values()) {
            if (over(mouseX, mouseY, LIST_X + 2, ROW_Y + trait.ordinal() * ROW_H - 1, LIST_W - 4, ROW_H)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
                if (queen != null) {
                    int level = queen.genome().level(trait);
                    lines.add(Component.translatable("gui.antcolonies.lab.level", level, Component.translatable(Trait.gradeKey(level)),
                            String.format(Locale.ROOT, "x%.2f", Trait.multiplier(level))).withStyle(Tooltips.levelColor(level)));
                }
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (queen != null && !queen.genome().mutations().isEmpty() && over(mouseX, mouseY, LEFT_X, 62, LEFT_W, 34)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.antcolonies.mutations").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (Identifier mutation : queen.genome().mutations()) {
                lines.add(Component.literal(" ").append(Component.translatable(Mutation.translationKey(mutation))).withStyle(ChatFormatting.GRAY));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (queen != null && queen.genome().level(selected()) < Trait.maxLevel()) {
            GeneticLabBlockEntity.Requirement requirement = requirement(queen);
            if (requirement != null) {
                for (int i = 0; i < requirement.materials().size(); i++) {
                    if (over(mouseX, mouseY, DETAIL_X + 5 + i * 20, 75, 16, 16)) {
                        Cost cost = requirement.materials().get(i);
                        graphics.setComponentTooltipForNextFrame(font, List.of(shown(cost).getHoverName(),
                                Component.translatable("gui.antcolonies.lab.have", have(cost), cost.count())
                                        .withStyle(have(cost) >= cost.count() ? ChatFormatting.GREEN : ChatFormatting.RED),
                                Component.translatable("gui.antcolonies.lab.materials_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
                        return;
                    }
                }
            }
        }
        if (over(mouseX, mouseY, LEFT_X + 3, 106, LEFT_W - 6, 9)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.antcolonies.lab.energy_stored", String.format(Locale.ROOT, "%,d", menu.data(GeneticLabBlockEntity.DATA_ENERGY)),
                            String.format(Locale.ROOT, "%,d", GeneticLabBlockEntity.ENERGY_CAPACITY)),
                    Component.translatable("gui.antcolonies.lab.power_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            String hint = hoveredSlot.getContainerSlot() == GeneticLabBlockEntity.SLOT_QUEEN && hoveredSlot.container == menu.lab() ? "insert_queen"
                    : hoveredSlot.getContainerSlot() == GeneticLabBlockEntity.SLOT_CATALYST && hoveredSlot.container == menu.lab() ? "catalyst_hint"
                    : hoveredSlot.container == menu.lab().upgrades() ? "upgrade_slot_hint"
                    : hoveredSlot.container == menu.lab() ? "materials_hint" : null;
            if (hint != null) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.antcolonies.lab." + hint)), mouseX, mouseY);
            }
        }
    }
}
