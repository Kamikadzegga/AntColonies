package com.antcolonies.content.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Shared tooltip and name pieces: species names, rarity colors, genome summaries. */
public final class Tooltips {
    private static final ChatFormatting[] RARITY = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.RED};

    public static MutableComponent species(Identifier species) {
        return Component.translatable(AntSpecies.translationKey(species));
    }

    public static ChatFormatting rarityColor(int rarity) {
        return RARITY[Math.clamp(rarity, 0, RARITY.length - 1)];
    }

    public static MutableComponent rarity(int rarity) {
        return Component.translatable("rarity.antcolonies." + Math.clamp(rarity, 0, 5)).withStyle(rarityColor(rarity));
    }

    /** A Queen's name, species, rarity and (with Shift, see {@code detailed}) every trait and mutation. */
    public static void queen(QueenData queen, Consumer<Component> tooltip, boolean detailed) {
        queen(queen, tooltip, detailed, null);
    }

    /** The same, with what her species is like when the registries are known (item tooltips). */
    public static void queen(QueenData queen, Consumer<Component> tooltip, boolean detailed, HolderLookup.@Nullable Provider registries) {
        Genome genome = queen.genome();
        if (!genome.lineage().name().isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen.name", genome.lineage().name()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.species", species(queen.species())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.genetics", rarity(genome.rarity()),
                String.format(java.util.Locale.ROOT, "%.1f", genome.averageLevel())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.generation", genome.lineage().generation()).withStyle(ChatFormatting.GRAY));
        speciesFacts(registries, queen.species(), tooltip, detailed);
        if (!detailed) {
            bestTraits(genome, tooltip);
            tooltip.accept(Component.translatable("tooltip.antcolonies.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (Trait trait : Trait.values()) {
            int level = genome.level(trait);
            tooltip.accept(Component.literal(" ").append(Component.translatable(trait.translationKey())).append(": ")
                    .append(Component.literal(String.valueOf(level)).withStyle(levelColor(level))).append(" ")
                    .append(Component.translatable(Trait.gradeKey(level)).withStyle(levelColor(level))).withStyle(ChatFormatting.GRAY));
        }
        for (Identifier mutation : genome.mutations()) {
            tooltip.accept(Component.literal(" + ").append(Component.translatable(Mutation.translationKey(mutation))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /**
     * What a species is like, for tube tooltips: its climate (and the blocks that make a Terrarium right for it), what it
     * finds or grows, and with {@code detailed} what it evolves into. Nothing if the species' data isn't loaded.
     */
    public static void speciesFacts(HolderLookup.@Nullable Provider registries, Identifier id, Consumer<Component> tooltip, boolean detailed) {
        AntSpecies data = ModRegistries.species(registries, id);
        if (data == null) {
            return;
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.species.climate",
                Component.translatable("climate.antcolonies." + data.climate().getSerializedName())).withStyle(ChatFormatting.GRAY));
        List<SpeciesOutput> outputs = data.canFarm() ? data.farming().harvest() : data.production().outputs();
        List<Component> names = new ArrayList<>();
        for (SpeciesOutput output : outputs) {
            ItemStack stack = output.available() ? output.create(1) : ItemStack.EMPTY;
            if (!stack.isEmpty() && names.stream().noneMatch(name -> name.getString().equals(stack.getHoverName().getString()))) {
                names.add(stack.getHoverName());
            }
        }
        if (!names.isEmpty()) {
            MutableComponent list = Component.empty();
            int shown = Math.min(detailed ? names.size() : 3, names.size());
            for (int i = 0; i < shown; i++) {
                list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(names.get(i));
            }
            if (shown < names.size()) {
                list.append(Component.translatable("tooltip.antcolonies.species.more", names.size() - shown));
            }
            tooltip.accept(Component.translatable(data.canFarm() ? "tooltip.antcolonies.species.grows" : "tooltip.antcolonies.species.finds", list)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!detailed) {
            return;
        }
        ModRegistries.lookup(registries, ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            Evolution evolution = entry.value();
            if (evolution.from().equals(id)) {
                ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                tooltip.accept(Component.translatable("tooltip.antcolonies.species.evolves", species(evolution.to()), item.getHoverName(),
                        evolution.minStage()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }));
    }

    /** The Queen's two best traits, for the short tooltip. */
    public static void bestTraits(Genome genome, Consumer<Component> tooltip) {
        List<Trait> traits = new ArrayList<>(List.of(Trait.values()));
        traits.sort(java.util.Comparator.comparingInt(genome::level).reversed());
        MutableComponent line = Component.empty();
        for (int i = 0; i < 2 && i < traits.size(); i++) {
            int level = genome.level(traits.get(i));
            line.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Component.translatable(traits.get(i).translationKey()))
                    .append(" ").append(Component.literal(String.valueOf(level)).withStyle(levelColor(level)));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.best", line).withStyle(ChatFormatting.GRAY));
    }

    /** The RGB of {@link #levelColor} (for bars in GUIs). */
    public static int levelRgb(int level) {
        return level >= 10 ? 0xFF5555 : level >= 8 ? 0xFFAA00 : level >= 6 ? 0xFF55FF : level >= 4 ? 0x55FFFF : level >= 2 ? 0x55FF55 : 0xFFFFFF;
    }

    public static ChatFormatting levelColor(int level) {
        return level >= 10 ? ChatFormatting.RED : level >= 8 ? ChatFormatting.GOLD : level >= 6 ? ChatFormatting.LIGHT_PURPLE
                : level >= 4 ? ChatFormatting.AQUA : level >= 2 ? ChatFormatting.GREEN : ChatFormatting.WHITE;
    }

    private Tooltips() {}
}
