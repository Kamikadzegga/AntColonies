package com.antcolonies.core.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** {@code antcolonies:companion}: something happened with a player's companion ("recruited", "level_10", "sad" and so on). */
public class CompanionTrigger extends SimpleCriterionTrigger<CompanionTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String what) {
        trigger(player, instance -> instance.what().equals(what));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, String what) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("what").forGetter(TriggerInstance::what)
        ).apply(i, TriggerInstance::new));
    }
}
