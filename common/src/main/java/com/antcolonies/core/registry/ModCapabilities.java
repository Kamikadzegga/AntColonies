package com.antcolonies.core.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Item access for hoppers, pipes and other mods: Storage chambers are emptied, Storage and Queen Chambers fed, the
 * Terrarium fed and its empty tubes taken, the Genetic Laboratory given materials, the Generator given fuel. Which slots each side sees
 * comes from the block entities' {@code WorldlyContainer} rules.
 */
public final class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STRUCTURE.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TERRARIUM.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        // FE: the Generator gives energy out; the Genetic Laboratory takes it in on any side.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> be.energyHandler());
    }

    private ModCapabilities() {}
}
