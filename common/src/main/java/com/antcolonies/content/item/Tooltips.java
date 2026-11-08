package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

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
        Genome genome = queen.genome();
        if (!genome.lineage().name().isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen.name", genome.lineage().name()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.species", species(queen.species())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.genetics", rarity(genome.rarity()),
                String.format(java.util.Locale.ROOT, "%.1f", genome.averageLevel())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.generation", genome.lineage().generation()).withStyle(ChatFormatting.GRAY));
        if (!detailed) {
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
