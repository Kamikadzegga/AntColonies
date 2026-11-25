package com.antcolonies.core.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** {@code antcolonies:companion}: something happened with a player's companion ("recruited", "level_10", "sad" and so on). */
public class CompanionTrigger extends SimpleCriterionTrigger<CompanionTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String what) {
        trigger(player, instance -> instance.what().equals(what));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String what) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("what").forGetter(TriggerInstance::what)
        ).apply(i, TriggerInstance::new));
    }
}
