package com.antcolonies.core.advancement;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, AntColonies.MODID);

    public static final Supplier<ColonyTrigger> COLONY = TRIGGERS.register("colony", ColonyTrigger::new);

    public static final Supplier<CompanionTrigger> COMPANION = TRIGGERS.register("companion", CompanionTrigger::new);

    private ModTriggers() {}
}
