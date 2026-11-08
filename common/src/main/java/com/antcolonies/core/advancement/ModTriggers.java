package com.antcolonies.core.advancement;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's advancement triggers. */
public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, AntColonies.MODID);

    public static final Supplier<ColonyTrigger> COLONY = TRIGGERS.register("colony", ColonyTrigger::new);

    private ModTriggers() {}
}
