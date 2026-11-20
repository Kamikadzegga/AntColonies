package com.antcolonies.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.antcolonies.client.ClientResearch;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.breeding.Hybrid;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.evolution.SpeciesMutation;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.research.Research;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.colony.research.SpeciesOrigin;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Climate;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.recipe.CentrifugingRecipe;
import com.antcolonies.core.registry.ModRegistries;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** A scrolling page of text and item icons: a species in full, or one of the Identification Book's how-to-play pages. */
public final class InfoPanel {
    static final int TITLE = 0xFF4FE3FF;
    static final int TEXT = 0xFFD8F2FF;
    static final int DIM = 0xFF7896A8;
    static final int GOOD = 0xFF6CF08A;
    static final int BAD = 0xFFFF6E6E;
    static final int ACCENT = 0xFFE07AFF;

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

    /** A row of item icons, each with its own tooltip. An icon that stands for several items cycles through them, one a second. */
    private record Icons(List<List<ItemStack>> stacks, List<List<Component>> tips) implements Row {
        @Override
        public int height() {
            return 18;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, List<Hover> hovers) {
            long second = Util.getMillis() / 1000;
            for (int i = 0; i < stacks.size(); i++) {
                List<ItemStack> family = stacks.get(i);
                graphics.item(family.get((int) (second % family.size())), x + i * 18, y);
                hovers.add(new Hover(x + i * 18, y, 16, 16, tips.get(i)));
            }
        }
    }

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
    private final ScrollBar bar = new ScrollBar();
    private int width = 100;
    private @Nullable Object shown;

    private void begin(Object what, int width) {
        if (!what.equals(shown)) {
            scroll = 0;
            shown = what;
        }
        // The text keeps clear of the scroll bar at the page's right edge.
        this.width = width - ScrollBar.WIDTH - 2;
        rows.clear();
    }

    private void text(Font font, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            rows.add(new Text(line, color));
        }
    }

    private void icons(List<ItemStack> stacks, List<List<Component>> tips) {
        cyclingIcons(stacks.stream().map(List::of).toList(), tips);
    }

    private void cyclingIcons(List<List<ItemStack>> families, List<List<Component>> tips) {
        int perRow = Math.max(1, width / 18);
        for (int i = 0; i < families.size(); i += perRow) {
            rows.add(new Icons(families.subList(i, Math.min(families.size(), i + perRow)), tips.subList(i, Math.min(tips.size(), i + perRow))));
        }
    }

    /** A climate's floor blocks in families: a family is the last word of the blocks' names, after dropping "block". */
    static Map<String, List<ItemStack>> floorFamilies(Climate climate) {
        Map<String, List<ItemStack>> families = new LinkedHashMap<>();
        for (Holder<Block> block : BuiltInRegistries.BLOCK.getTagOrEmpty(climate.floorTag())) {
            ItemStack stack = new ItemStack(block.value());
            if (stack.isEmpty()) {
                continue;
            }
            String name = BuiltInRegistries.BLOCK.getKey(block.value()).getPath().replaceAll("_blocks?$", "");
            families.computeIfAbsent(name.substring(name.lastIndexOf('_') + 1), key -> new ArrayList<>()).add(stack);
        }
        return families;
    }

    // --- What a species is ---

    /**
     * Everything about a species. In a Terrarium, {@code here} is whether its climate is right there and {@code floor} the
     * climate the block under it gives; both are null in the book.
     */
    public void species(Font font, Identifier id, @Nullable AntSpecies data, @Nullable Boolean here, @Nullable Climate floor, int width) {
        begin(id, width);
        text(font, Tooltips.species(id), TITLE);
        if (data == null) {
            return;
        }
        text(font, Component.translatable("book.antcolonies.kind",
                Component.translatable("category.antcolonies." + data.category().getSerializedName()), data.tier(),
                Component.translatable("climate.antcolonies." + data.climate().getSerializedName())), DIM);
        SpeciesOrigin origin = SpeciesOrigin.of(ClientSpecies.registries(), id);
        text(font, Component.translatable(origin.translationKey()), originColor(origin));
        // Climate, and what fixes it.
        rows.add(new Gap(3));
        if (data.climate() == Climate.ANY) {
            text(font, Component.translatable("info.antcolonies.climate.any"), GOOD);
        } else {
            if (here != null) {
                text(font, Component.translatable(here ? "info.antcolonies.climate.right" : "info.antcolonies.climate.wrong"), here ? GOOD : BAD);
            }
            text(font, Component.translatable("info.antcolonies.climate.fix"), DIM);
            // One icon per family of blocks, cycling through its variants (all of them work).
            List<List<ItemStack>> families = new ArrayList<>();
            List<List<Component>> tips = new ArrayList<>();
            for (List<ItemStack> family : floorFamilies(data.climate()).values()) {
                families.add(family);
                List<Component> tip = new ArrayList<>();
                tip.add(family.getFirst().getHoverName());
                if (family.size() > 1) {
                    tip.add(Component.translatable("info.antcolonies.climate.variants", family.size()).withStyle(ChatFormatting.AQUA));
                }
                tip.add(Component.translatable("info.antcolonies.climate.under").withStyle(ChatFormatting.GRAY));
                tips.add(tip);
            }
            cyclingIcons(families, tips);
            if (floor != null) {
                text(font, Component.translatable("info.antcolonies.climate.floor",
                        Component.translatable("climate.antcolonies." + floor.getSerializedName())), DIM);
            }
        }
        // Where it lives and what it eats.
        rows.add(new Gap(3));
        text(font, where(id, data), DIM);
        String diet = data.needsSpecialFood() ? Component.translatable("book.antcolonies.diet_special", data.diet().specialKind()).getString()
                : Component.translatable("book.antcolonies.diet").getString();
        text(font, Component.literal(diet + String.format(Locale.ROOT, " (x%.2f)", data.diet().consumption())), DIM);
        // What its workers find, or what its farmers grow.
        rows.add(new Gap(3));
        if (data.makesLiquid()) {
            // A Liquid Ant: the one thing it makes, with the droplet itself and the fluid it becomes.
            text(font, Component.translatable("info.antcolonies.liquid"), TEXT);
            AntSpecies.Liquid liquid = data.liquid().orElse(null);
            if (liquid != null && liquid.available()) {
                ItemStack droplet = new ItemStack(liquid.dropletItem().get());
                Component fluid = liquid.fluidValue().get().getFluidType().getDescription();
                Component line = Component.translatable("info.antcolonies.liquid.makes", droplet.getHoverName());
                List<Component> tip = new ArrayList<>(List.of(droplet.getHoverName(),
                        Component.translatable("info.antcolonies.liquid.fluid", fluid).withStyle(ChatFormatting.GRAY)));
                var recipe = CentrifugingRecipe.find(Minecraft.getInstance().level, droplet);
                if (recipe != null) {
                    tip.add(Component.translatable("info.antcolonies.liquid.centrifuge", recipe.amount(),
                            recipe.fluid().getFluidType().getDescription(), recipe.energyCost()).withStyle(ChatFormatting.AQUA));
                }
                tip.add(Component.translatable("info.antcolonies.liquid.rate",
                        String.format(Locale.ROOT, "%.2f", liquid.rate())).withStyle(ChatFormatting.DARK_GRAY));
                rows.add(new ItemLine(droplet, font.split(line, width - 20).getFirst(), ACCENT, tip));
            } else {
                text(font, Component.translatable("status.antcolonies.no_droplet"), BAD);
            }
        }
        if (!data.makesLiquid()) {
            outputs(font, data.production().outputs(), "info.antcolonies.finds");
        }
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
                Researches.Entry research = Researches.forSpecies(registries, evolution.to());
                if (research != null && !ClientResearch.view().knowsResearch(research.id())) {
                    // A species mutation nobody here has researched: it is there, but not named.
                    rows.add(new ItemLine(new ItemStack(ModItems.GENETIC_LAB.get()),
                            font.split(Component.translatable("info.antcolonies.mutation.unknown"), width - 20).getFirst(), DIM,
                            List.of(Component.translatable("info.antcolonies.mutation.unknown"),
                                    ClientResearch.hint(research.id(), research.research()).copy().withStyle(ChatFormatting.GRAY),
                                    Component.translatable(research.research().tier().translationKey()).withStyle(ChatFormatting.DARK_AQUA))));
                    continue;
                }
                Component line = into ? Component.translatable("info.antcolonies.evolves_into", Tooltips.species(evolution.to()))
                        : Component.translatable("info.antcolonies.evolves_from", Tooltips.species(evolution.from()));
                List<Component> tip = new ArrayList<>(List.of(line));
                if (research != null) {
                    tip.add(Component.translatable("info.antcolonies.mutation.lab", evolution.labAmount(), item.getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("info.antcolonies.mutation.needs", evolution.minStage()).withStyle(ChatFormatting.GRAY));
                } else {
                    tip.add(Component.translatable("info.antcolonies.evolution.feed", item.getHoverName()).withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("info.antcolonies.evolution.needs", evolution.minStage(), evolution.minPopulation(),
                            Math.round(evolution.chance() * 100), evolution.guaranteedAfter()).withStyle(ChatFormatting.GRAY));
                }
                evolution.dimension().ifPresent(dimension -> tip.add(Component.translatable(research != null
                        ? "info.antcolonies.mutation.dimension" : "tooltip.antcolonies.species.evolves_in",
                        Tooltips.dimension(dimension)).withStyle(ChatFormatting.RED)));
                rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), ACCENT, tip));
            }
        }
    }

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

    // --- What is known, and what is not ---

    private static int originColor(SpeciesOrigin origin) {
        return switch (origin) {
            case NATURAL -> 0xFF7FD08A;
            case MODIFIED -> TITLE;
            case HYBRID -> ACCENT;
        };
    }

    /** Where a species is found: the biomes of its spawn tag by name, or that it is not out in the wild. */
    private static Component where(Identifier id, AntSpecies data) {
        var registries = ClientSpecies.registries();
        if (data.spawn().isEmpty() || registries == null) {
            return Component.translatable("book.antcolonies.not_wild");
        }
        MutableComponent biomes = Component.empty();
        int listed = 0;
        int total = 0;
        var tag = registries.lookupOrThrow(Registries.BIOME).get(data.spawn().get());
        if (tag.isPresent()) {
            for (Holder<Biome> biome : tag.get()) {
                total++;
                if (listed < 4 && biome.unwrapKey().isPresent()) {
                    biomes.append(listed == 0 ? Component.empty() : Component.literal(", "))
                            .append(Component.translatable(Util.makeDescriptionId("biome", biome.unwrapKey().get().identifier())));
                    listed++;
                }
            }
        }
        if (listed == 0) {
            return Component.translatable("book.antcolonies.wild", data.spawn().get().location().getPath().replace('_', ' '));
        }
        if (total > listed) {
            biomes.append(Component.translatable("tooltip.antcolonies.species.more", total - listed));
        }
        return Component.translatable("book.antcolonies.wild", biomes);
    }

    private static Component speciesOrUnknown(Identifier species, Knowledge knowledge) {
        return knowledge.knowsSpecies(species) ? Tooltips.species(species) : Component.translatable("book.antcolonies.unknown.name");
    }

    /**
     * A species the reader has not met: what kind of thing it is and where to look, never its finds. A genetically
     * modified one whose mutation is researched is named, with how it is made.
     */
    public void unknownSpecies(Font font, Identifier id, AntSpecies data, int width) {
        begin("unknown:" + id, width);
        var registries = ClientSpecies.registries();
        Knowledge knowledge = ClientResearch.view();
        SpeciesOrigin origin = SpeciesOrigin.of(registries, id);
        Researches.Entry research = Researches.forSpecies(registries, id);
        boolean researched = research != null && knowledge.knowsResearch(research.id());
        text(font, researched ? Tooltips.species(id) : Component.translatable("book.antcolonies.unknown.title"), researched ? TITLE : DIM);
        text(font, Component.translatable(origin.translationKey()), originColor(origin));
        rows.add(new Gap(3));
        switch (origin) {
            case NATURAL -> {
                text(font, Component.translatable("book.antcolonies.unknown.natural"), TEXT);
                rows.add(new Gap(2));
                text(font, where(id, data), DIM);
                evolvesFrom(font, id, knowledge);
            }
            case HYBRID -> {
                text(font, Component.translatable("book.antcolonies.unknown.hybrid"), TEXT);
                rows.add(new Gap(2));
                ModRegistries.lookup(registries, ModRegistries.HYBRID).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
                    if (holder.value().result().equals(id)) {
                        text(font, Component.translatable("book.antcolonies.unknown.cross", speciesOrUnknown(holder.value().first(), knowledge),
                                speciesOrUnknown(holder.value().second(), knowledge)), DIM);
                    }
                }));
            }
            case MODIFIED -> {
                if (!researched) {
                    text(font, Component.translatable("book.antcolonies.unknown.modified"), TEXT);
                    rows.add(new Gap(2));
                    text(font, Component.translatable(research.research().tier().translationKey()), DIM);
                    text(font, ClientResearch.hint(research.id(), research.research()), DIM);
                } else {
                    text(font, Component.translatable("book.antcolonies.researched"), TEXT);
                    rows.add(new Gap(2));
                    mutationRows(font, id, knowledge);
                    rows.add(new Gap(3));
                    text(font, Component.translatable("book.antcolonies.researched.make"), DIM);
                }
            }
        }
    }

    /** The feeding evolutions into a species, as hints: the material is shown, the source only once it is known. */
    private void evolvesFrom(Font font, Identifier id, Knowledge knowledge) {
        ModRegistries.lookup(ClientSpecies.registries(), ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            Evolution evolution = holder.value();
            if (evolution.to().equals(id)) {
                ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                Component line = Component.translatable("book.antcolonies.unknown.evolves", speciesOrUnknown(evolution.from(), knowledge));
                List<Component> tip = new ArrayList<>(List.of(line,
                        Component.translatable("info.antcolonies.evolution.feed", item.getHoverName()).withStyle(ChatFormatting.GRAY)));
                evolution.dimension().ifPresent(dimension -> tip.add(Component.translatable("tooltip.antcolonies.species.evolves_in",
                        Tooltips.dimension(dimension)).withStyle(ChatFormatting.RED)));
                rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), DIM, tip));
            }
        }));
    }

    /** Every laboratory mutation into a species: its source, its material and what else it asks for. */
    private void mutationRows(Font font, Identifier target, Knowledge knowledge) {
        var registries = ClientSpecies.registries();
        ModRegistries.lookup(registries, ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            Evolution evolution = holder.value();
            if (!evolution.to().equals(target)) {
                return;
            }
            ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            item.setCount(Math.min(item.getMaxStackSize(), evolution.labAmount()));
            Component line = Component.translatable("info.antcolonies.evolves_from", Tooltips.species(evolution.from()));
            List<Component> tip = new ArrayList<>(List.of(line,
                    Component.translatable("info.antcolonies.mutation.lab", evolution.labAmount(), item.getHoverName()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("info.antcolonies.mutation.needs", evolution.minStage()).withStyle(ChatFormatting.GRAY)));
            evolution.dimension().ifPresent(dimension -> tip.add(Component.translatable("info.antcolonies.mutation.dimension",
                    Tooltips.dimension(dimension)).withStyle(ChatFormatting.RED)));
            tip.add(Component.translatable(knowledge.hasAnalysed(evolution.from()) ? "book.antcolonies.compatibility.analysed"
                    : "book.antcolonies.compatibility.not_analysed").withStyle(ChatFormatting.DARK_AQUA));
            rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), ACCENT, tip));
        }));
    }

    /** One research entry: hidden behind its hint until its material is scanned, then what it unlocks and what that takes. */
    public void research(Font font, Researches.Entry entry, int width) {
        begin("research:" + entry.id(), width);
        var registries = ClientSpecies.registries();
        Knowledge knowledge = ClientResearch.view();
        Research research = entry.research();
        boolean known = knowledge.knowsResearch(entry.id());
        text(font, known ? research.title() : Component.translatable(research.unlocksSpecies() ? "book.antcolonies.research.unknown_species"
                : "book.antcolonies.research.unknown_gene"), known ? TITLE : DIM);
        text(font, Component.translatable(research.tier().translationKey()), DIM);
        List<Identifier> waits = Researches.missing(research, knowledge);
        if (!known) {
            text(font, Component.translatable(waits.isEmpty() ? "book.antcolonies.research.undiscovered" : "book.antcolonies.research.locked"),
                    waits.isEmpty() ? DIM : BAD);
            rows.add(new Gap(3));
            text(font, ClientResearch.hint(entry.id(), research), TEXT);
            rows.add(new Gap(2));
            text(font, Component.translatable("book.antcolonies.research.how"), DIM);
            for (Identifier wait : waits) {
                Researches.Entry needed = Researches.byId(registries, wait);
                boolean named = needed != null && knowledge.knowsResearch(wait);
                text(font, Component.translatable("book.antcolonies.research.first", named ? needed.research().title()
                        : Component.translatable("book.antcolonies.research.unknown_species")), BAD);
            }
            return;
        }
        boolean team = ClientResearch.owner(Knowledge.Kind.RESEARCH, entry.id()) == ClientResearch.Owner.TEAM;
        text(font, Component.translatable(team ? "book.antcolonies.research.team" : "book.antcolonies.research.personal"), team ? 0xFFFFD36A : GOOD);
        rows.add(new Gap(3));
        // The material that was scanned (every item of a tag, in turn).
        List<ItemStack> materials = research.material().items().map(ItemStack::new).toList();
        if (!materials.isEmpty()) {
            text(font, Component.translatable("book.antcolonies.research.material"), TEXT);
            cyclingIcons(List.of(materials), List.of(List.of(materials.getFirst().getHoverName())));
        }
        if (research.unlocksSpecies()) {
            Identifier target = research.species().get();
            text(font, Component.translatable("book.antcolonies.research.unlocks", Tooltips.species(target)), TEXT);
            mutationRows(font, target, knowledge);
            rows.add(new Gap(3));
            text(font, Component.translatable("book.antcolonies.research.power", AntScreen.fe(research.energyCost())), DIM);
            if (research.catalysts().isEmpty()) {
                text(font, Component.translatable("book.antcolonies.research.catalyst",
                        Component.translatable("book.antcolonies.research.no_catalyst")), DIM);
            }
            for (Cost catalyst : research.catalysts()) {
                ItemStack stack = catalyst.ingredient().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                Component line = Component.translatable("book.antcolonies.research.catalyst", catalyst.count() + "x " + stack.getHoverName().getString());
                rows.add(new ItemLine(stack, font.split(line, width - 20).getFirst(), DIM, List.of(stack.getHoverName())));
            }
            text(font, Component.translatable(knowledge.knowsSpecies(target) ? "book.antcolonies.research.complete"
                    : "book.antcolonies.researched.make"), knowledge.knowsSpecies(target) ? GOOD : DIM);
        } else {
            Identifier id = research.mutation().get();
            text(font, Component.translatable("book.antcolonies.research.gene", Component.translatable(Mutation.translationKey(id))), TEXT);
            Mutation mutation = ModRegistries.lookup(registries, ModRegistries.MUTATION)
                    .flatMap(lookup -> lookup.get(ResourceKey.create(ModRegistries.MUTATION, id))).map(Holder::value).orElse(null);
            if (mutation != null && mutation.catalyst().isPresent()) {
                Cost catalyst = mutation.catalyst().get();
                ItemStack stack = catalyst.ingredient().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                Component line = Component.translatable("book.antcolonies.research.takes", catalyst.count() + "x " + stack.getHoverName().getString());
                rows.add(new ItemLine(stack, font.split(line, width - 20).getFirst(), DIM, List.of(stack.getHoverName())));
            }
            rows.add(new Gap(2));
            text(font, Component.translatable("book.antcolonies.research.on_editing"), DIM);
        }
    }

    /** What the Queens of a species can become, and what each change still waits for. */
    public void compatibility(Font font, Identifier species, int width) {
        begin("compatibility:" + species, width);
        var registries = ClientSpecies.registries();
        Knowledge knowledge = ClientResearch.view();
        text(font, Tooltips.species(species), TITLE);
        boolean analysed = knowledge.hasAnalysed(species);
        text(font, Component.translatable(analysed ? "book.antcolonies.compatibility.analysed" : "book.antcolonies.compatibility.not_analysed"),
                analysed ? GOOD : DIM);
        rows.add(new Gap(3));
        List<SpeciesMutation.Option> options = SpeciesMutation.options(registries, species);
        List<Evolution> natural = SpeciesMutation.natural(registries, species);
        if (!options.isEmpty()) {
            text(font, Component.translatable("book.antcolonies.compatibility.mutations"), TEXT);
        }
        for (SpeciesMutation.Option option : options) {
            Evolution evolution = option.evolution();
            boolean researched = knowledge.knowsResearch(option.research().id());
            if (!researched) {
                rows.add(new ItemLine(new ItemStack(ModItems.GENETIC_LAB.get()),
                        font.split(Component.translatable("info.antcolonies.mutation.unknown"), width - 20).getFirst(), DIM,
                        List.of(Component.translatable("book.antcolonies.research.not_researched").withStyle(ChatFormatting.RED),
                                ClientResearch.hint(option.research().id(), option.research().research()).copy().withStyle(ChatFormatting.GRAY),
                                Component.translatable(option.research().research().tier().translationKey()).withStyle(ChatFormatting.DARK_AQUA))));
                continue;
            }
            ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            item.setCount(Math.min(item.getMaxStackSize(), evolution.labAmount()));
            Component line = Component.translatable("info.antcolonies.evolves_into", Tooltips.species(evolution.to()));
            List<Component> tip = new ArrayList<>(List.of(line,
                    Component.translatable("book.antcolonies.research.researched").withStyle(ChatFormatting.GREEN),
                    Component.translatable("info.antcolonies.mutation.lab", evolution.labAmount(), item.getHoverName()).withStyle(ChatFormatting.GRAY)));
            for (Cost catalyst : option.research().research().catalysts()) {
                ItemStack stack = catalyst.ingredient().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                tip.add(Component.literal(catalyst.count() + "x ").append(stack.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.literal(AntScreen.fe(option.research().research().energyCost())).withStyle(ChatFormatting.DARK_AQUA));
            // Whether a Queen is ready is only known once one has been analysed.
            tip.add(analysed ? Component.translatable("info.antcolonies.mutation.needs", evolution.minStage()).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("book.antcolonies.compatibility.scan").withStyle(ChatFormatting.DARK_GRAY));
            evolution.dimension().ifPresent(dimension -> tip.add(Component.translatable("info.antcolonies.mutation.dimension",
                    Tooltips.dimension(dimension)).withStyle(ChatFormatting.RED)));
            rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), ACCENT, tip));
        }
        if (!natural.isEmpty()) {
            rows.add(new Gap(3));
            text(font, Component.translatable("book.antcolonies.compatibility.natural"), TEXT);
        }
        for (Evolution evolution : natural) {
            ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            Component line = Component.translatable("info.antcolonies.evolves_into", Tooltips.species(evolution.to()));
            List<Component> tip = new ArrayList<>(List.of(line,
                    Component.translatable("book.antcolonies.compatibility.natural_change").withStyle(ChatFormatting.GREEN),
                    Component.translatable("info.antcolonies.evolution.feed", item.getHoverName()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("info.antcolonies.evolution.needs", evolution.minStage(), evolution.minPopulation(),
                            Math.round(evolution.chance() * 100), evolution.guaranteedAfter()).withStyle(ChatFormatting.GRAY)));
            evolution.dimension().ifPresent(dimension -> tip.add(Component.translatable("tooltip.antcolonies.species.evolves_in",
                    Tooltips.dimension(dimension)).withStyle(ChatFormatting.RED)));
            rows.add(new ItemLine(item, font.split(line, width - 20).getFirst(), 0xFF7FD08A, tip));
        }
        // The crosses it takes part in: the partner is named once it is known.
        boolean header = false;
        for (Holder.Reference<Hybrid> holder : ModRegistries.lookup(registries, ModRegistries.HYBRID)
                .map(lookup -> lookup.listElements().toList()).orElse(List.of())) {
            Hybrid hybrid = holder.value();
            if (!hybrid.first().equals(species) && !hybrid.second().equals(species)) {
                continue;
            }
            if (!header) {
                rows.add(new Gap(3));
                text(font, Component.translatable("book.antcolonies.compatibility.hybrids"), TEXT);
                header = true;
            }
            Identifier partner = hybrid.first().equals(species) ? hybrid.second() : hybrid.first();
            text(font, Component.translatable("book.antcolonies.compatibility.cross", speciesOrUnknown(partner, knowledge),
                    speciesOrUnknown(hybrid.result(), knowledge), Math.round(hybrid.chance() * 100),
                    String.format(Locale.ROOT, "%.1f", hybrid.minAverageLevel())), DIM);
        }
        if (options.isEmpty() && natural.isEmpty() && !header) {
            text(font, Component.translatable("book.antcolonies.compatibility.nothing"), DIM);
        }
    }

    /** What a species brings in: its finds with their shares, its harvest, its pace and what it is best at. */
    public void production(Font font, Identifier id, AntSpecies data, int width) {
        begin("production:" + id, width);
        text(font, Tooltips.species(id), TITLE);
        text(font, Component.translatable("book.antcolonies.production.kind",
                Component.translatable("category.antcolonies." + data.category().getSerializedName())), DIM);
        rows.add(new Gap(3));
        if (data.makesLiquid()) {
            AntSpecies.Liquid liquid = data.liquid().orElse(null);
            if (liquid != null && liquid.available()) {
                ItemStack droplet = new ItemStack(liquid.dropletItem().get());
                Component line = Component.translatable("info.antcolonies.liquid.makes", droplet.getHoverName());
                rows.add(new ItemLine(droplet, font.split(line, width - 20).getFirst(), ACCENT, List.of(droplet.getHoverName(),
                        Component.translatable("info.antcolonies.liquid.rate", String.format(Locale.ROOT, "%.2f", liquid.rate()))
                                .withStyle(ChatFormatting.GRAY))));
            } else {
                text(font, Component.translatable("status.antcolonies.no_droplet"), BAD);
            }
        } else {
            text(font, Component.translatable("book.antcolonies.production.rate", String.format(Locale.ROOT, "%.2f", data.production().rate())), TEXT);
            outputs(font, data.production().outputs(), "info.antcolonies.finds");
        }
        if (data.canFarm()) {
            rows.add(new Gap(3));
            text(font, Component.translatable("info.antcolonies.farms", String.format(Locale.ROOT, "%.1f", data.farming().foodPerMinute())), TEXT);
            outputs(font, data.farming().harvest(), null);
        }
        if (data.canHunt()) {
            List<AntSpecies.Combat.Target> targets = data.combat().targets().isEmpty() ? ColonySimulator.DEFAULT_TARGETS : data.combat().targets();
            MutableComponent mobs = Component.empty();
            for (int i = 0; i < targets.size(); i++) {
                mobs.append(i == 0 ? Component.empty() : Component.literal(", ")).append(targets.get(i).entity().value().getDescription());
            }
            rows.add(new Gap(3));
            text(font, Component.translatable("info.antcolonies.hunts", mobs), TEXT);
        }
        // Its unique finds, and the upgrades made for it.
        List<SpeciesOutput> unique = data.production().outputs().stream().filter(output -> output.special() && output.available()).toList();
        if (!unique.isEmpty()) {
            rows.add(new Gap(3));
            outputs(font, unique, "book.antcolonies.production.unique");
        }
        if (data.signature().isPresent()) {
            rows.add(new Gap(3));
            ItemStack signature = new ItemStack(data.signature().get());
            Component line = Component.translatable("book.antcolonies.production.upgrades");
            rows.add(new ItemLine(signature, font.split(line, width - 20).getFirst(), TEXT, List.of(signature.getHoverName(),
                    Component.translatable("book.antcolonies.production.signature").withStyle(ChatFormatting.GRAY))));
            for (String kind : List.of("extraction", "processing", "efficiency")) {
                text(font, Component.literal(" ").append(Component.translatable("upgrade." + id.getNamespace() + "." + id.getPath() + "." + kind)), DIM);
            }
        }
    }

    /** A note for an empty section of the book. */
    public void note(Font font, String key, int width) {
        begin("note:" + key, width);
        text(font, Component.translatable(key), DIM);
    }

    // --- How to play ---

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

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int h) {
        hovers.clear();
        shownHeight = h;
        scroll = Math.clamp(scroll, 0, maxScroll());
        int top = y;
        for (int i = scroll; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (y + row.height() > top + h) {
                break;
            }
            row.draw(graphics, font, x, y, width, hovers);
            y += row.height();
        }
        // A scroll bar in the margin kept free on the right, while the page is longer than its box.
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        bar.draw(graphics, x + width + 2, top, h, scroll, maxScroll(), total == 0 ? 1 : h / (double) total, -1, -1);
    }

    /** A click: true when it took hold of the scroll bar. */
    public boolean press(double mouseX, double mouseY) {
        if (bar.press(mouseX, mouseY)) {
            scroll = bar.offsetAt(mouseY);
            return true;
        }
        return false;
    }

    /** The mouse moved with its button down: true while it is dragging the scroll bar. */
    public boolean drag(double mouseY) {
        if (bar.dragging()) {
            scroll = bar.offsetAt(mouseY);
            return true;
        }
        return false;
    }

    public void release() {
        bar.release();
    }

    /** The box's height as last drawn (until then, any row can be the top one). */
    private int shownHeight = Integer.MAX_VALUE;

    /** The furthest row the page scrolls to: the one from which the last rows just fill the box, never past the end. */
    private int maxScroll() {
        if (shownHeight == Integer.MAX_VALUE) {
            return Math.max(0, rows.size() - 1);
        }
        int used = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            used += rows.get(i).height();
            if (used > shownHeight) {
                return Math.min(i + 1, rows.size() - 1);
            }
        }
        return 0;
    }


    public boolean scroll(double amount) {
        int before = scroll;
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        return scroll != before;
    }

    public @Nullable List<Component> hover(double mouseX, double mouseY) {
        for (Hover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + hover.w() && mouseY >= hover.y() && mouseY < hover.y() + hover.h()) {
                return hover.lines();
            }
        }
        return null;
    }
}
