package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A scrolling page of text and item icons, for the Terrarium's Species page and the Identification Book: everything about
 * a species (climate and how to fix it, finds, hunts, evolutions), or one of the book's how-to-play pages. Rows that don't
 * fit are scrolled to with the mouse wheel; icons explain themselves when pointed at.
 */
public final class InfoPanel {
    static final int TITLE = 0xFF4FE3FF;
    static final int TEXT = 0xFFD8F2FF;
    static final int DIM = 0xFF7896A8;
    static final int GOOD = 0xFF6CF08A;
    static final int BAD = 0xFFFF6E6E;
    static final int ACCENT = 0xFFE07AFF;

    /** One line of the page. */
    private interface Row {
        int height();

        void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers);
    }

    private record Hover(int x, int y, int w, int h, List<Component> lines) {}

    private record Text(FormattedCharSequence text, int color) implements Row {
        @Override
        public int height() {
            return 10;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers) {
            graphics.text(font, text, x, y, color, false);
        }
    }

    private record Gap(int height) implements Row {
        @Override
        public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers) {
        }
    }

    /** A row of item icons, each with its own tooltip. */
    private record Icons(List<ItemStack> stacks, List<List<Component>> tips) implements Row {
        @Override
        public int height() {
            return 18;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers) {
            for (int i = 0; i < stacks.size(); i++) {
                graphics.item(stacks.get(i), x + i * 18, y);
                hovers.add(new Hover(x + i * 18, y, 16, 16, tips.get(i)));
            }
        }
    }

    /** An item icon with a line of text beside it. */
    private record ItemLine(ItemStack stack, FormattedCharSequence text, int color, List<Component> tip) implements Row {
        @Override
        public int height() {
            return 17;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers) {
            graphics.item(stack, x, y);
            graphics.text(font, text, x + 19, y + 4, color, false);
            hovers.add(new Hover(x, y, w, 16, tip));
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Hover> hovers = new ArrayList<>();
    private int scroll;
    private int width = 100;
    private @Nullable Object shown;

    /** Starts the page again (after the species or page changed: the scroll goes back to the top). */
    private void begin(Object what, int width) {
        if (!what.equals(shown)) {
            scroll = 0;
            shown = what;
        }
        this.width = width;
        rows.clear();
    }

    private void text(Font font, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            rows.add(new Text(line, color));
        }
    }

    private void icons(List<ItemStack> stacks, List<List<Component>> tips) {
        int perRow = Math.max(1, width / 18);
        for (int i = 0; i < stacks.size(); i += perRow) {
            rows.add(new Icons(stacks.subList(i, Math.min(stacks.size(), i + perRow)), tips.subList(i, Math.min(tips.size(), i + perRow))));
        }
    }

    // --- What a species is ---

    /**
     * Everything about a species.
     *
     * @param here     in a Terrarium: whether its climate is right there (null in the book)
     * @param floor    in a Terrarium: the climate the block under it gives (null if none, or in the book)
     */
    public void species(Font font, Identifier id, @Nullable AntSpecies data, @Nullable Boolean here, @Nullable Climate floor, int width) {
        begin(id, width);
        text(font, Tooltips.species(id), TITLE);
        if (data == null) {
            return;
        }
        text(font, Component.translatable("book.antcolonies.kind", Component.translatable("category.antcolonies." + data.category().getSerializedName()),
                data.tier(), Component.translatable("climate.antcolonies." + data.climate().getSerializedName())), DIM);
        // Climate, and what fixes it.
        rows.add(new Gap(3));
        if (data.climate() == Climate.ANY) {
            text(font, Component.translatable("info.antcolonies.climate.any"), GOOD);
        } else {
            if (here != null) {
                text(font, Component.translatable(here ? "info.antcolonies.climate.right" : "info.antcolonies.climate.wrong"), here ? GOOD : BAD);
            }
            text(font, Component.translatable("info.antcolonies.climate.fix"), DIM);
            List<ItemStack> blocks = new ArrayList<>();
            List<List<Component>> tips = new ArrayList<>();
            for (Holder<Block> block : BuiltInRegistries.BLOCK.getTagOrEmpty(data.climate().floorTag())) {
                ItemStack stack = new ItemStack(block.value());
                if (!stack.isEmpty() && blocks.size() < 8) {
                    blocks.add(stack);
                    tips.add(List.of(stack.getHoverName(), Component.translatable("info.antcolonies.climate.under").withStyle(ChatFormatting.GRAY)));
                }
            }
            icons(blocks, tips);
            if (floor != null) {
                text(font, Component.translatable("info.antcolonies.climate.floor",
                        Component.translatable("climate.antcolonies." + floor.getSerializedName())), DIM);
            }
        }
        // Where it lives and what it eats.
        rows.add(new Gap(3));
        text(font, data.spawn().isPresent()
                ? Component.translatable("book.antcolonies.wild", data.spawn().get().location().getPath().replace('_', ' '))
                : Component.translatable("book.antcolonies.not_wild"), DIM);
        String diet = data.needsSpecialFood() ? Component.translatable("book.antcolonies.diet_special", data.diet().specialKind()).getString()
                : Component.translatable("book.antcolonies.diet").getString();
        text(font, Component.literal(diet + String.format(Locale.ROOT, " (x%.2f)", data.diet().consumption())), DIM);
        // What its workers find, or what its farmers grow.
        rows.add(new Gap(3));
        outputs(font, data.production().outputs(), "info.antcolonies.finds");
        if (data.canFarm()) {
            text(font, Component.translatable("info.antcolonies.farms", String.format(Locale.ROOT, "%.1f", data.farming().foodPerMinute())), TEXT);
            outputs(font, data.farming().harvest(), null);
        }
        // What hunters hunt at a Hunting Ground.
        if (data.canHunt()) {
            List<AntSpecies.Combat.Target> targets = data.combat().targets().isEmpty() ? ColonySimulator.DEFAULT_TARGETS : data.combat().targets();
            MutableComponent mobs = Component.empty();
            for (int i = 0; i < targets.size(); i++) {
                mobs.append(i == 0 ? Component.empty() : Component.literal(", ")).append(targets.get(i).entity().value().getDescription());
            }
            rows.add(new Gap(3));
            text(font, Component.translatable("info.antcolonies.hunts", mobs), TEXT);
        }
        // Evolutions into and out of it.
        var registries = ClientSpecies.registries();
        if (registries != null) {
            List<Holder.Reference<Evolution>> evolutions = ModRegistries.lookup(registries, ModRegistries.EVOLUTION)
                    .map(lookup -> lookup.listElements().toList()).orElse(List.of());
            boolean header = false;
            for (Holder.Reference<Evolution> entry : evolutions) {
                Evolution evolution = entry.value();
                boolean into = evolution.from().equals(id);
                if (!into && !evolution.to().equals(id)) {
                    continue;
                }
                if (!header) {
                    rows.add(new Gap(3));
                    text(font, Component.translatable("info.antcolonies.evolution"), TEXT);
                    header = true;
                }
                ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                Component line = into ? Component.translatable("info.antcolonies.evolves_into", Tooltips.species(evolution.to()))
                        : Component.translatable("info.antcolonies.evolves_from", Tooltips.species(evolution.from()));
                List<Component> tip = List.of(line, Component.translatable("info.antcolonies.evolution.feed", item.getHoverName()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("info.antcolonies.evolution.needs", evolution.minStage(), evolution.minPopulation(),
                                Math.round(evolution.chance() * 100), evolution.guaranteedAfter()).withStyle(ChatFormatting.GRAY));
                rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), ACCENT, tip));
            }
        }
    }

    /** Output icons: each tells its chance, the stage it needs and whether it is a signature find. */
    private void outputs(Font font, List<SpeciesOutput> outputs, @Nullable String header) {
        double total = outputs.stream().filter(SpeciesOutput::available).mapToDouble(o -> ColonySimulator.pickWeight(o, 1)).sum();
        List<ItemStack> stacks = new ArrayList<>();
        List<List<Component>> tips = new ArrayList<>();
        for (SpeciesOutput output : outputs) {
            ItemStack stack = output.available() ? output.create(output.count()) : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                continue;
            }
            List<Component> tip = new ArrayList<>();
            tip.add(stack.getHoverName());
            double chance = total <= 0 ? 0 : ColonySimulator.pickWeight(output, 1) / total * 100;
            tip.add(Component.translatable("info.antcolonies.chance", chance >= 1 ? String.format(Locale.ROOT, "%.0f", chance)
                    : String.format(Locale.ROOT, "%.2f", chance)).withStyle(ChatFormatting.GRAY));
            if (output.minStage() > 1) {
                tip.add(Component.translatable("info.antcolonies.from_stage", output.minStage()).withStyle(ChatFormatting.GRAY));
            }
            if (output.special()) {
                tip.add(Component.translatable("info.antcolonies.signature").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            stacks.add(stack);
            tips.add(tip);
        }
        if (header != null) {
            text(font, Component.translatable(header), TEXT);
        }
        icons(stacks, tips);
    }

    // --- How to play ---

    /** One of the book's how-to-play pages: a title, then its text in paragraphs, with the blocks it is about. */
    public void guide(Font font, String page, List<ItemStack> items, int width) {
        begin("guide:" + page, width);
        text(font, Component.translatable("guide.antcolonies." + page + ".title"), TITLE);
        rows.add(new Gap(3));
        if (!items.isEmpty()) {
            List<List<Component>> tips = new ArrayList<>();
            for (ItemStack stack : items) {
                tips.add(List.of(stack.getHoverName()));
            }
            icons(items, tips);
            rows.add(new Gap(2));
        }
        for (String paragraph : Component.translatable("guide.antcolonies." + page + ".text").getString().split("\n")) {
            text(font, Component.literal(paragraph), TEXT);
            rows.add(new Gap(4));
        }
    }

    // --- Drawing ---

    /** Draws the rows that fit in the box, from the scrolled-to row on. */
    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int h) {
        hovers.clear();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - 1));
        int top = y;
        for (int i = scroll; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (y + row.height() > top + h) {
                break;
            }
            row.draw(graphics, font, x, y, width, hovers);
            y += row.height();
        }
        if (scroll > 0) {
            graphics.text(font, "^", x + width - 6, top, DIM, false);
        }
        if (canScrollDown(top, h)) {
            graphics.text(font, "v", x + width - 6, top + h - 9, DIM, false);
        }
    }

    private boolean canScrollDown(int top, int h) {
        int used = 0;
        for (int i = scroll; i < rows.size(); i++) {
            used += rows.get(i).height();
            if (used > h) {
                return true;
            }
        }
        return false;
    }

    /** Scrolls a row at a time; true if the page moved. */
    public boolean scroll(double amount) {
        int before = scroll;
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, rows.size() - 1));
        return scroll != before;
    }

    /** The tooltip of what is under the mouse, if anything. */
    public @Nullable List<Component> hover(double mouseX, double mouseY) {
        for (Hover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + hover.w() && mouseY >= hover.y() && mouseY < hover.y() + hover.h()) {
                return hover.lines();
            }
        }
        return null;
    }
}
