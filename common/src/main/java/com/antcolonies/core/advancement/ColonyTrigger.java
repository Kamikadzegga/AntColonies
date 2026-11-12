package com.antcolonies.core.advancement;

import java.util.Optional;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.species.AntSpecies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;

/**
 * {@code antcolonies:colony}: a player is near (or opens) a Terrarium whose colony matches. Every condition is optional:
 * {@code has_queen}, {@code stage} (bounds), {@code adults} (bounds), {@code species} (an id), {@code category}
 * ("base", "mineral", "hybrid"...), {@code best_trait} (bounds on the Queen's highest trait level), {@code queen_founded} (grown
 * from a single Queen through founding), {@code linked} (another colony online on one of its Colony Connector channels).
 */
public class ColonyTrigger extends SimpleCriterionTrigger<ColonyTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Colony colony, @Nullable AntSpecies species) {
        trigger(player, instance -> instance.matches(colony, species));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<Boolean> hasQueen, MinMaxBounds.Ints stage,
                                  MinMaxBounds.Doubles adults, Optional<Identifier> species, Optional<String> category, MinMaxBounds.Ints bestTrait,
                                  Optional<Boolean> queenFounded, Optional<Boolean> linked)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.BOOL.optionalFieldOf("has_queen").forGetter(TriggerInstance::hasQueen),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("stage", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::stage),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("adults", MinMaxBounds.Doubles.ANY).forGetter(TriggerInstance::adults),
                Identifier.CODEC.optionalFieldOf("species").forGetter(TriggerInstance::species),
                Codec.STRING.optionalFieldOf("category").forGetter(TriggerInstance::category),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("best_trait", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::bestTrait),
                Codec.BOOL.optionalFieldOf("queen_founded").forGetter(TriggerInstance::queenFounded),
                Codec.BOOL.optionalFieldOf("linked").forGetter(TriggerInstance::linked)
        ).apply(i, TriggerInstance::new));

        boolean matches(Colony colony, @Nullable AntSpecies data) {
            if (hasQueen.isPresent() && hasQueen.get() != (colony.queen() != null)) {
                return false;
            }
            if (!stage.matches(colony.stage()) || !adults.matches(colony.adults())) {
                return false;
            }
            if (species.isPresent() && !species.get().equals(colony.species())) {
                return false;
            }
            if (category.isPresent() && (data == null || !category.get().equals(data.category().getSerializedName()))) {
                return false;
            }
            if (queenFounded.isPresent() && queenFounded.get() != colony.queenFounded()) {
                return false;
            }
            // Linked: another colony is online on one of its Colony Connector channels.
            if (linked.isPresent() && linked.get() != colony.network().stream().anyMatch(link -> link.online() && !link.self())) {
                return false;
            }
            if (!bestTrait.isAny()) {
                if (colony.queen() == null) {
                    return false;
                }
                int best = 0;
                for (int level : colony.queen().genome().levels()) {
                    best = Math.max(best, level);
                }
                return bestTrait.matches(best);
            }
            return true;
        }
    }
}
