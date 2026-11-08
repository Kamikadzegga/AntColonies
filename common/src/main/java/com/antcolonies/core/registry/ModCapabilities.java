package com.antcolonies.core.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Item access for hoppers, pipes and other mods: Resource Piles are emptied, Food Storage and Queen Chambers fed, the
 * Terrarium fed and its empty tubes taken, the Genetic Laboratory given materials, the Generator given fuel. Which slots each side sees
 * comes from the block entities' {@code WorldlyContainer} rules.
 */
public final class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STRUCTURE.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TERRARIUM.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        // FE: the Generator gives energy out; the lab takes it in at its controller and at its Genetic Energy Core.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> be.energyHandler());
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> new com.antcolonies.content.blockentity.EnergyCoreHandler(level, pos),
                ModBlocks.LAB_PARTS.get(com.antcolonies.content.block.LabPartBlock.Part.GENETIC_ENERGY_CORE).get());
    }

    private ModCapabilities() {}
}
