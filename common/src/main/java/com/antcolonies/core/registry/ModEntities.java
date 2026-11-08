package com.antcolonies.core.registry;

import com.antcolonies.AntColonies;
import com.antcolonies.content.entity.WildAnt;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The ant entities: wild ants and Guard Ants. Colony ants are numbers, never entities. */
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(AntColonies.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WildAnt>> WILD_ANT = ENTITY_TYPES.registerEntityType("wild_ant",
            WildAnt::new, MobCategory.CREATURE, b -> b.sized(0.45F, 0.25F).eyeHeight(0.15F).clientTrackingRange(8));

    /** A colony soldier that follows and defends its owner. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.content.entity.GuardAnt>> GUARD_ANT = ENTITY_TYPES.registerEntityType("guard_ant",
            com.antcolonies.content.entity.GuardAnt::new, MobCategory.CREATURE, b -> b.sized(0.45F, 0.25F).eyeHeight(0.15F).clientTrackingRange(10));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WILD_ANT.get(), WildAnt.createAttributes().build());
        event.put(GUARD_ANT.get(), com.antcolonies.content.entity.GuardAnt.createAttributes().build());
    }

    private ModEntities() {}
}
