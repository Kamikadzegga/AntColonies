package com.antcolonies.content.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.evolution.SpeciesMutation;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Shared tooltip and name pieces: species names, rarity colors, genome summaries. */
public final class Tooltips {
    private static final ChatFormatting[] RARITY = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.RED};

    public static MutableComponent dimension(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

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

    public static void queen(QueenData queen, Consumer<Component> tooltip, boolean detailed, HolderLookup.@Nullable Provider registries) {
        Genome genome = queen.genome();
        if (!genome.lineage().name().isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen.name", genome.lineage().name()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.species", species(queen.species())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.genetics", rarity(genome.rarity()),
                String.format(Locale.ROOT, "%.1f", genome.averageLevel())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.queen.generation", genome.lineage().generation()).withStyle(ChatFormatting.GRAY));
        // The species her line has been, oldest first.
        if (detailed && !genome.lineage().history().isEmpty()) {
            MutableComponent history = Component.empty();
            for (int i = 0; i < genome.lineage().history().size(); i++) {
                history.append(i > 0 ? " > " : "").append(species(genome.lineage().history().get(i)));
            }
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen.lineage", history).withStyle(ChatFormatting.DARK_AQUA));
        }
        // How far she has grown with a colony, and how she grows.
        if (queen.grown() > 1) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen_grown", queen.grown()).withStyle(ChatFormatting.GREEN));
        }
        if (queen.grown() > 0 && queen.grown() < 4 && queen.growth() > 0) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen_growing", queen.grown() + 1, (int) Math.ceil(queen.minutesToNext()))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (detailed && queen.grown() < 4) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen_grows", (int) Math.round(QueenData.minutesFor(2)), QueenData.growthCap())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        speciesFacts(registries, queen.species(), tooltip, detailed);
        if (!detailed) {
            bestTraits(genome, tooltip);
            tooltip.accept(Component.translatable("tooltip.antcolonies.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (Trait trait : Trait.values()) {
            int level = genome.level(trait);
            MutableComponent line = Component.literal(" ").append(Component.translatable(trait.translationKey())).append(": ")
                    .append(Component.literal(String.valueOf(level)).withStyle(levelColor(level))).append(" ")
                    .append(Component.translatable(Trait.gradeKey(level)).withStyle(levelColor(level)));
            if (genome.locked(trait)) {
                line.append(" ").append(Component.translatable("tooltip.antcolonies.queen.locked").withStyle(ChatFormatting.GOLD));
            }
            tooltip.accept(line.withStyle(ChatFormatting.GRAY));
        }
        for (Identifier mutation : genome.mutations()) {
            // A flaw is marked as one: a minus and red, where a strength has a plus.
            boolean flaw = mutation(registries, mutation) instanceof Mutation data && data.negative();
            MutableComponent line = Component.literal(flaw ? " - " : " + ").append(Component.translatable(Mutation.translationKey(mutation)));
            String effects = mutationEffectLine(registries, mutation);
            if (!effects.isEmpty()) {
                line.append(Component.literal(": " + effects).withStyle(ChatFormatting.GRAY));
            }
            if (genome.protects(mutation)) {
                line.append(" ").append(Component.translatable("tooltip.antcolonies.queen.protected").withStyle(ChatFormatting.GOLD));
            }
            tooltip.accept(line.withStyle(flaw ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
        }
        if (genome.stability() > 0) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen.stability", Math.round(genome.stability() * 100))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

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
        if (data.canHunt()) {
            MutableComponent mobs = Component.empty();
            List<AntSpecies.Combat.Target> targets = data.combat().targets();
            for (int i = 0; i < targets.size(); i++) {
                mobs.append(i == 0 ? Component.empty() : Component.literal(", ")).append(targets.get(i).entity().value().getDescription());
            }
            tooltip.accept(Component.translatable("tooltip.antcolonies.species.hunts", mobs).withStyle(ChatFormatting.RED));
        }
        if (!detailed) {
            return;
        }
        // Species mutations are told apart from evolutions, and not by name: they are discoveries.
        if (!SpeciesMutation.options(registries, id).isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.species.mutates").withStyle(ChatFormatting.AQUA));
        }
        ModRegistries.lookup(registries, ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            Evolution evolution = entry.value();
            if (evolution.from().equals(id) && Researches.forSpecies(registries, evolution.to()) == null) {
                ItemStack item = evolution.item().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
                tooltip.accept(Component.translatable("tooltip.antcolonies.species.evolves", species(evolution.to()), item.getHoverName(),
                        evolution.minStage()).withStyle(ChatFormatting.LIGHT_PURPLE));
                evolution.dimension().ifPresent(dimension -> tooltip.accept(Component.translatable("tooltip.antcolonies.species.evolves_in",
                        dimension(dimension)).withStyle(ChatFormatting.RED)));
            }
        }));
    }

    public static void bestTraits(Genome genome, Consumer<Component> tooltip) {
        List<Trait> traits = new ArrayList<>(List.of(Trait.values()));
        traits.sort(Comparator.comparingInt(genome::level).reversed());
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

    // --- Mutations ---

    private static @Nullable Mutation mutation(HolderLookup.@Nullable Provider registries, Identifier id) {
        return ModRegistries.lookup(registries, ModRegistries.MUTATION)
                .flatMap(lookup -> lookup.get(ResourceKey.create(ModRegistries.MUTATION, id))).map(holder -> holder.value()).orElse(null);
    }

    /** What a mutation changes, a part per effect ("+25% hunting strength"). Empty when the mutation is not in the data. */
    public static List<Component> mutationEffects(HolderLookup.@Nullable Provider registries, Identifier id) {
        Mutation mutation = mutation(registries, id);
        if (mutation == null) {
            return List.of();
        }
        Mutation.MutationEffects effects = mutation.effects();
        List<Component> parts = new ArrayList<>();
        effect(parts, "production", effects.production());
        effect(parts, "gathering", effects.gathering());
        effect(parts, "food", effects.food());
        effect(parts, "eggs", effects.eggs());
        effect(parts, "growth", effects.growth());
        effect(parts, "soldiers", effects.soldiers());
        effect(parts, "farming", effects.farming());
        effect(parts, "special", effects.special());
        if (effects.coldResistant()) {
            parts.add(Component.translatable("mutation.antcolonies.effect.cold_resistant"));
        }
        if (effects.heatResistant()) {
            parts.add(Component.translatable("mutation.antcolonies.effect.heat_resistant"));
        }
        return parts;
    }

    private static void effect(List<Component> parts, String name, double factor) {
        if (factor != 1) {
            String percent = String.format(Locale.ROOT, "%+d%%", Math.round((factor - 1) * 100));
            parts.add(Component.translatable("mutation.antcolonies.effect." + name, percent));
        }
    }

    /** {@link #mutationEffects} in one line, its parts joined by commas. */
    public static String mutationEffectLine(HolderLookup.@Nullable Provider registries, Identifier id) {
        List<String> parts = new ArrayList<>();
        mutationEffects(registries, id).forEach(part -> parts.add(part.getString()));
        return String.join(", ", parts);
    }

    private Tooltips() {}
}
