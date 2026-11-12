package com.antcolonies.compat.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.UpgradeStationBlockEntity;
import com.antcolonies.colony.breeding.Hybrid;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.colony.genetics.TraitUpgrade;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.content.item.Tooltips;
import com.antcolonies.content.item.TweezersItem;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** Builds every JEI and EMI entry from the synced species, evolution, hybrid, mutation and trait upgrade data. */
public final class AntRecipes {
    private final RegistryAccess registries;
    private final List<Holder.Reference<AntSpecies>> species;
    private final RandomSource random = RandomSource.create(1);

    public AntRecipes(RegistryAccess registries) {
        this.registries = registries;
        this.species = registries.lookup(ModRegistries.SPECIES).map(lookup -> lookup.listElements().toList()).orElse(List.of());
    }

    private ItemStack queen(Identifier species) {
        return TweezersItem.queenTube(Genetics.wildQueen(species, random));
    }

    private ItemStack sample(Identifier species) {
        ItemStack stack = new ItemStack(ModItems.GENETIC_SAMPLE.get());
        stack.set(ModDataComponents.QUEEN.get(), Genetics.wildQueen(species, random));
        return stack;
    }

    private static ItemStack ants(Identifier species, Caste caste) {
        return TweezersItem.antTube(new AntSample(species, caste, AntSample.PINCH));
    }

    private static ItemStack brood(Identifier species, boolean larva) {
        ItemStack stack = new ItemStack(larva ? ModItems.ANT_LARVA.get() : ModItems.ANT_EGG.get());
        stack.set(ModDataComponents.SPECIES.get(), species);
        return stack;
    }

    private static List<ItemStack> stacks(Cost cost) {
        return cost.ingredient().items().map(item -> new ItemStack(item, cost.count())).toList();
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, Object... args) {
        return Component.translatable("jei.antcolonies." + key, args);
    }

    private static String percent(double value) {
        double p = value * 100;
        return p >= 10 ? String.format(Locale.ROOT, "%.0f", p) : p >= 0.1 ? String.format(Locale.ROOT, "%.1f", p)
                : p >= 0.01 ? String.format(Locale.ROOT, "%.2f", p) : "<0.01";
    }

    private static Identifier id(String category, Identifier of) {
        return AntColonies.id(category + "/" + of.getNamespace() + "/" + of.getPath());
    }

    private static List<AntRecipe.Output> outputs(List<SpeciesOutput> list) {
        List<AntRecipe.Output> outputs = new ArrayList<>();
        // Chances as the simulation picks them (rare finds scaled by the config's rareFindMultiplier), before genetics.
        double total = list.stream().mapToDouble(o -> com.antcolonies.colony.simulation.ColonySimulator.pickWeight(o, 1)).sum();
        for (SpeciesOutput output : list) {
            ItemStack stack = output.create();
            if (stack.isEmpty()) {
                continue;
            }
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(line("weight", percent(com.antcolonies.colony.simulation.ColonySimulator.pickWeight(output, 1) / Math.max(0.0001, total)) + "%").withStyle(ChatFormatting.GRAY));
            if (output.minStage() > 1) {
                tooltip.add(line("stage", output.minStage()).withStyle(ChatFormatting.GOLD));
            }
            if (output.special()) {
                tooltip.add(line("special").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            output.processed().ifPresent(processed -> tooltip.add(Component.literal(output.ratio() + " -> 1 ")
                    .append(new ItemStack(processed).getHoverName()).withStyle(ChatFormatting.DARK_AQUA)));
            outputs.add(new AntRecipe.Output(List.of(stack), tooltip));
        }
        return outputs;
    }

    // --- The categories ---

    public List<AntRecipe> species() {
        List<AntRecipe> list = new ArrayList<>();
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            List<Component> lines = new ArrayList<>();
            lines.add(Tooltips.species(id).withStyle(ChatFormatting.DARK_GREEN));
            lines.add(Component.translatable("book.antcolonies.kind", Component.translatable("category.antcolonies." + data.category().getSerializedName()),
                    data.tier(), Component.translatable("climate.antcolonies." + data.climate().getSerializedName())));
            lines.add(data.spawn().isPresent() ? Component.translatable("book.antcolonies.wild", data.spawn().get().location().getPath().replace("spawns/", ""))
                    : Component.translatable("book.antcolonies.not_wild"));
            if (data.needsSpecialFood()) {
                lines.add(line("diet", data.diet().specialKind(), data.diet().specialPerEgg()));
            }
            list.add(new AntRecipe(id("species", id), List.of(List.of(queen(id)), List.of(ants(id, Caste.WORKER)), List.of(ants(id, Caste.SOLDIER))),
                    outputs(data.canFarm() ? data.farming().harvest() : data.production().outputs()), lines));
        }
        return list;
    }

    public List<AntRecipe> rearing() {
        List<AntRecipe> list = new ArrayList<>();
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            List<List<ItemStack>> inputs = new ArrayList<>();
            inputs.add(List.of(queen(id)));
            inputs.add(List.of(new ItemStack(ModItems.TEST_TUBE.get())));
            data.rearing().cost().forEach(cost -> inputs.add(stacks(cost)));
            list.add(new AntRecipe(id("rearing", id), inputs, List.of(AntRecipe.Output.of(queen(id))),
                    List.of(line("time", data.rearing().time() / 20), line("needs_stage", 1, AntConfig.get(AntConfig.QUEEN_REARING_MIN_POPULATION)))));
        }
        return list;
    }

    public List<AntRecipe> evolution() {
        List<AntRecipe> list = new ArrayList<>();
        registries.lookup(ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            Evolution evolution = entry.value();
            List<ItemStack> items = evolution.item().items().map(ItemStack::new).toList();
            double chance = Math.min(1, evolution.chance() * AntConfig.get(AntConfig.EVOLUTION_CHANCE_MULTIPLIER));
            list.add(new AntRecipe(id("evolution", entry.key().identifier()), List.of(List.of(queen(evolution.from())), items),
                    List.of(AntRecipe.Output.of(queen(evolution.to()))),
                    evolution.dimension().isEmpty()
                            ? List.of(line("chance", percent(chance)), line("guaranteed", evolution.guaranteedAfter()),
                                    line("needs_stage", evolution.minStage(), evolution.minPopulation()))
                            : List.of(line("chance", percent(chance)), line("guaranteed", evolution.guaranteedAfter()),
                                    line("needs_stage", evolution.minStage(), evolution.minPopulation()),
                                    line("dimension", com.antcolonies.content.item.Tooltips.dimension(evolution.dimension().get())).withStyle(ChatFormatting.RED))));
        }));
        return list;
    }

    public List<AntRecipe> hybrids() {
        List<AntRecipe> list = new ArrayList<>();
        registries.lookup(ModRegistries.HYBRID).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            Hybrid hybrid = entry.value();
            list.add(new AntRecipe(id("hybrid", entry.key().identifier()), List.of(List.of(queen(hybrid.first())), List.of(sample(hybrid.second()))),
                    List.of(AntRecipe.Output.of(queen(hybrid.result()))),
                    List.of(Component.translatable("jei.antcolonies.hybrid_of", Tooltips.species(hybrid.first()), Tooltips.species(hybrid.second()),
                                    Tooltips.species(hybrid.result()).withStyle(ChatFormatting.DARK_GREEN)),
                            line("hybrid_chance", percent(hybrid.chance()), hybrid.minAverageLevel()))));
        }));
        return list;
    }

    public List<AntRecipe> mutations() {
        List<AntRecipe> list = new ArrayList<>();
        registries.lookup(ModRegistries.MUTATION).ifPresent(lookup -> lookup.listElements().forEach(entry -> {
            Mutation mutation = entry.value();
            Identifier id = entry.key().identifier();
            List<List<ItemStack>> inputs = new ArrayList<>();
            inputs.add(List.of(new ItemStack(ModItems.QUEEN_TEST_TUBE.get())));
            mutation.catalyst().ifPresent(cost -> inputs.add(stacks(cost)));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(Mutation.translationKey(id)).withStyle(mutation.negative() ? ChatFormatting.RED : ChatFormatting.DARK_PURPLE));
            lines.add(effects(mutation.effects()));
            lines.add(mutation.catalyst().isPresent() ? line("catalyst") : line("random", mutation.randomWeight()));
            if (mutation.negative()) {
                lines.add(line("negative"));
            }
            list.add(new AntRecipe(id("mutation", id), inputs, List.of(), lines));
        }));
        return list;
    }

    private static Component effects(Mutation.MutationEffects e) {
        List<Component> parts = new ArrayList<>();
        add(parts, "production", e.production());
        add(parts, "gathering", e.gathering());
        add(parts, "food", e.food());
        add(parts, "eggs", e.eggs());
        add(parts, "growth", e.growth());
        add(parts, "soldiers", e.soldiers());
        add(parts, "farming", e.farming());
        add(parts, "special", e.special());
        if (e.coldResistant()) {
            parts.add(Component.translatable("jei.antcolonies.effect.cold_resistant"));
        }
        if (e.heatResistant()) {
            parts.add(Component.translatable("jei.antcolonies.effect.heat_resistant"));
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
        }
        return out;
    }

    private static void add(List<Component> parts, String name, double factor) {
        if (factor != 1) {
            parts.add(Component.translatable("jei.antcolonies.effect." + name, String.format(Locale.ROOT, "x%.2f", factor)));
        }
    }

    public List<AntRecipe> eggs() {
        List<AntRecipe> list = new ArrayList<>();
        double base = AntConfig.get(AntConfig.BASE_EGGS_PER_MINUTE);
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            List<Component> lines = new ArrayList<>(List.of(line("eggs_info", String.format(Locale.ROOT, "%.1f", base * data.breeding().eggs()),
                    AntConfig.get(AntConfig.EGG_HATCH_MINUTES))));
            if (data.needsSpecialFood()) {
                lines.add(line("diet", data.diet().specialKind(), data.diet().specialPerEgg()));
            }
            list.add(new AntRecipe(id("eggs", id), List.of(List.of(queen(id))), List.of(AntRecipe.Output.of(brood(id, false))), lines));
        }
        return list;
    }

    public List<AntRecipe> larvae() {
        List<AntRecipe> list = new ArrayList<>();
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            double minutes = AntConfig.get(AntConfig.LARVAE_GROW_MINUTES) / Math.max(0.01, data.breeding().growth());
            list.add(new AntRecipe(id("larvae", id), List.of(List.of(brood(id, false))),
                    List.of(AntRecipe.Output.of(brood(id, true)), AntRecipe.Output.of(ants(id, Caste.WORKER)), AntRecipe.Output.of(ants(id, Caste.SOLDIER))),
                    List.of(line("larvae_info", String.format(Locale.ROOT, "%.1f", minutes), data.breeding().growth()))));
        }
        return list;
    }

    public List<AntRecipe> production() {
        List<AntRecipe> list = new ArrayList<>();
        double base = AntConfig.get(AntConfig.SCAVENGE_ROLLS);
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            if (data.production().outputs().isEmpty()) {
                continue;
            }
            list.add(new AntRecipe(id("production", id), List.of(List.of(ants(id, Caste.WORKER))), outputs(data.production().outputs()),
                    List.of(line("rate", String.format(Locale.ROOT, base * data.production().rate() < 0.01 ? "%.4f" : "%.3f", base * data.production().rate())))));
        }
        return list;
    }

    /**
     * The Nether and End expeditions: for each species that may go, its ants and the site in, the finds it can bring back
     * out (the expedition table, filtered to that species).
     */
    public List<AntRecipe> expeditions() {
        List<AntRecipe> list = new ArrayList<>();
        double base = AntConfig.get(AntConfig.EXPEDITION_ROLLS);
        for (com.antcolonies.colony.expedition.Expedition expedition : com.antcolonies.colony.expedition.Expedition.all(registries)) {
            ItemStack site = new ItemStack(com.antcolonies.core.registry.ModBlocks.STRUCTURES.get(expedition.site()).get());
            for (Identifier id : expedition.adaptedSpecies(registries)) {
                List<SpeciesOutput> finds = expedition.outputsFor(id);
                if (finds.isEmpty()) {
                    continue;
                }
                list.add(new AntRecipe(id("expedition", AntColonies.id(expedition.site().getSerializedName() + "_" + id.getNamespace() + "_" + id.getPath())),
                        List.of(List.of(ants(id, Caste.WORKER)), List.of(site)), outputs(finds),
                        List.of(line("rate", String.format(Locale.ROOT, "%.3f", base * expedition.rate())),
                                line("expedition_dimension", Component.translatable("dimension." + expedition.home().identifier().getNamespace() + "."
                                        + expedition.home().identifier().getPath())).withStyle(ChatFormatting.DARK_PURPLE))));
            }
        }
        return list;
    }

    public List<AntRecipe> farming() {
        List<AntRecipe> list = new ArrayList<>();
        double base = AntConfig.get(AntConfig.FARMER_FOOD);
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            if (!data.canFarm()) {
                continue;
            }
            list.add(new AntRecipe(id("farming", id), List.of(List.of(ants(id, Caste.WORKER))), outputs(data.farming().harvest()),
                    List.of(line("food_rate", String.format(Locale.ROOT, "%.2f", base * data.farming().foodPerMinute())))));
        }
        return list;
    }

    public List<AntRecipe> fighting() {
        List<AntRecipe> list = new ArrayList<>();
        for (Holder.Reference<AntSpecies> entry : species) {
            Identifier id = entry.key().identifier();
            AntSpecies data = entry.value();
            if (!data.canHunt()) {
                continue;
            }
            List<AntSpecies.Combat.Target> targets = data.combat().targets().isEmpty() ? defaultTargets() : data.combat().targets();
            int total = targets.stream().mapToInt(AntSpecies.Combat.Target::weight).sum();
            List<AntRecipe.Output> outputs = new ArrayList<>();
            for (AntSpecies.Combat.Target target : targets) {
                ItemStack egg = spawnEgg(target.entity().value());
                outputs.add(new AntRecipe.Output(List.of(egg), List.of(target.entity().value().getDescription().copy().withStyle(ChatFormatting.GOLD),
                        line("weight", percent(target.weight() / (double) total) + "%"), line("loot").withStyle(ChatFormatting.GRAY))));
            }
            list.add(new AntRecipe(id("fighting", id), List.of(List.of(ants(id, Caste.WORKER))), outputs,
                    List.of(line("strength", String.format(Locale.ROOT, "%.2f", data.combat().strength())), line("loot"))));
        }
        return list;
    }

    private static List<AntSpecies.Combat.Target> defaultTargets() {
        return List.of(new AntSpecies.Combat.Target(net.minecraft.world.entity.EntityType.ZOMBIE.builtInRegistryHolder(), 30),
                new AntSpecies.Combat.Target(net.minecraft.world.entity.EntityType.SKELETON.builtInRegistryHolder(), 25),
                new AntSpecies.Combat.Target(net.minecraft.world.entity.EntityType.SPIDER.builtInRegistryHolder(), 25),
                new AntSpecies.Combat.Target(net.minecraft.world.entity.EntityType.CREEPER.builtInRegistryHolder(), 15),
                new AntSpecies.Combat.Target(net.minecraft.world.entity.EntityType.SLIME.builtInRegistryHolder(), 5));
    }

    private static ItemStack spawnEgg(EntityType<?> type) {
        return SpawnEggItem.byId(type).map(ItemStack::new).orElseGet(() -> new ItemStack(net.minecraft.world.item.Items.BONE));
    }

    public List<AntRecipe> lab() {
        List<AntRecipe> list = new ArrayList<>();
        double costs = AntConfig.get(AntConfig.LAB_COST_MULTIPLIER);
        for (Trait trait : Trait.values()) {
            TraitUpgrade upgrade = registries.lookup(ModRegistries.TRAIT_UPGRADE)
                    .flatMap(lookup -> lookup.listElements().map(Holder::value).filter(u -> u.trait() == trait).findFirst()).orElse(null);
            if (upgrade == null) {
                continue;
            }
            for (int level = Trait.MIN_LEVEL; level < Trait.maxLevel(); level++) {
                TraitUpgrade.Step step = upgrade.step(level);
                List<List<ItemStack>> inputs = new ArrayList<>();
                inputs.add(List.of(new ItemStack(ModItems.QUEEN_TEST_TUBE.get())));
                for (Cost cost : step.cost()) {
                    inputs.add(cost.ingredient().items().map(item -> new ItemStack(item, cost.scaled(costs))).toList());
                }
                list.add(new AntRecipe(AntColonies.id("lab/" + trait.getSerializedName() + "/" + level), inputs, List.of(),
                        List.of(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.DARK_PURPLE),
                                line("level_step", level, level + 1), line("energy", String.format(Locale.ROOT, "%,d", step.energy())), line("time", step.time() / 20))));
            }
        }
        return list;
    }

    public List<AntRecipe> traits() {
        List<AntRecipe> list = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.DARK_PURPLE));
            StringBuilder levels = new StringBuilder();
            for (int level = 1; level <= Trait.maxLevel(); level++) {
                levels.append(level == 1 ? "" : " ").append(String.format(Locale.ROOT, "%.2f", Trait.multiplier(level)));
                if (level == 5) {
                    lines.add(Component.literal("1-5: " + levels));
                    levels.setLength(0);
                }
            }
            lines.add(Component.literal("6-" + Trait.maxLevel() + ": " + levels.toString().trim()));
            list.add(new AntRecipe(AntColonies.id("trait/" + trait.getSerializedName()), List.of(List.of(new ItemStack(ModItems.QUEEN_TEST_TUBE.get()))),
                    List.of(), lines));
        }
        return list;
    }

    /**
     * Colony upgrades: one entry per type and tier. The upgrade on the left, every block it goes in on the right, and what
     * it does there in numbers underneath (the same lines as its tooltip).
     */
    public List<AntRecipe> colonyUpgrades() {
        List<AntRecipe> list = new ArrayList<>();
        for (var type : com.antcolonies.colony.upgrade.UpgradeType.values()) {
            List<AntRecipe.Output> places = com.antcolonies.colony.upgrade.UpgradeEffects.placements(type).stream()
                    .map(place -> AntRecipe.Output.of(place, line("goes_in").withStyle(ChatFormatting.GRAY))).toList();
            for (int tier = 1; tier <= com.antcolonies.colony.upgrade.UpgradeType.MAX_TIER; tier++) {
                list.add(new AntRecipe(AntColonies.id("colony_upgrade/" + type.getSerializedName() + "/" + tier),
                        List.of(List.of(new ItemStack(ModItems.upgrade(type, tier)))), places,
                        com.antcolonies.colony.upgrade.UpgradeEffects.lines(type, tier)));
            }
        }
        return list;
    }

    public List<AntRecipe> speciesUpgrades() {
        List<AntRecipe> list = new ArrayList<>();
        UpgradeStationBlockEntity.withSignatures(registries).forEach((id, data) -> {
            ItemStack signature = new ItemStack(data.signature().get());
            for (SpeciesUpgradeKind kind : SpeciesUpgradeKind.values()) {
                for (int tier = 1; tier <= 3; tier++) {
                    ItemStack output = new ItemStack(ModItems.SPECIES_UPGRADE.get());
                    output.set(ModDataComponents.SPECIES_UPGRADE.get(), new SpeciesUpgrade(id, kind, tier));
                    ItemStack base;
                    if (tier == 1) {
                        base = new ItemStack(ModItems.UPGRADE_BASE.get());
                    } else {
                        base = new ItemStack(ModItems.SPECIES_UPGRADE.get());
                        base.set(ModDataComponents.SPECIES_UPGRADE.get(), new SpeciesUpgrade(id, kind, tier - 1));
                    }
                    list.add(new AntRecipe(AntColonies.id("species_upgrade/" + id.getPath() + "/" + kind.getSerializedName() + "/" + tier),
                            List.of(List.of(base), List.of(signature.copyWithCount(UpgradeStationBlockEntity.materialFor(tier)))),
                            List.of(AntRecipe.Output.of(output)),
                            List.of(Tooltips.species(id), line("material_count", UpgradeStationBlockEntity.materialFor(tier)))));
                }
            }
        });
        return list;
    }
}
