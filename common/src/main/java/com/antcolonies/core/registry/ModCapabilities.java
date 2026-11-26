package com.antcolonies.core.registry;

import com.antcolonies.colony.model.StructureKind;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/** Item, energy and fluid access for hoppers, pipes and other mods; the block entities' {@code WorldlyContainer} rules pick the slots. */
public final class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        // A Colony Connector's slots only name items: it has no items to give or take.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STRUCTURE.get(),
                (be, side) -> be.kind() == StructureKind.COLONY_CONNECTOR ? null : new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TERRARIUM.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        // A home nest's store of harvested crops: taken out on any side, nothing put in.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.HOME_NEST.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        // FE: the Generator gives energy out; the Genetic Laboratory takes it in on any side.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENETIC_LAB.get(), (be, side) -> be.energyHandler());
        // The Droplet Centrifuge: droplets and FE in on any side; its tank is only taken from.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CENTRIFUGE.get(), (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CENTRIFUGE.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.CENTRIFUGE.get(), (be, side) -> be.fluidOutput());
    }

    private ModCapabilities() {}
}
