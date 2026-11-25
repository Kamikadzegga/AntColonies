package com.antcolonies.core.registry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * A load condition, {@code antcolonies:registered}: an item, fluid or other built-in registry entry exists.
 * NeoForge's own {@code neoforge:registered} asks the loading context, which a client has none of when it reads a mod's
 * data from its own jar: it then drops the entry the server kept, and every species tag points one species off.
 *
 * @param registry the registry's id (items when left out)
 * @param value    the entry's id
 */
public record RegisteredCondition(Identifier registry, Identifier value) implements ICondition {
    public static final MapCodec<RegisteredCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("registry", Registries.ITEM.identifier()).forGetter(RegisteredCondition::registry),
            Identifier.CODEC.fieldOf("value").forGetter(RegisteredCondition::value)
    ).apply(i, RegisteredCondition::new));

    @Override
    public boolean test(IContext context) {
        Registry<?> entries = BuiltInRegistries.REGISTRY.getValue(registry);
        return entries != null && entries.containsKey(value);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
