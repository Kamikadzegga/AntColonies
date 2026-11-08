package com.antcolonies.compat.jade;

import com.antcolonies.content.block.GeneratorBlock;
import com.antcolonies.content.block.GeneticLabControllerBlock;
import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.content.block.TerrariumBlock;
import com.antcolonies.content.blockentity.GeneratorBlockEntity;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade look-at tooltips: a Terrarium's colony, a job site's ants and output, a machine's energy and work. Only loaded
 * when Jade is installed. The numbers come from the server (Jade asks for them while you look at the block).
 */
@WailaPlugin
public class AntColoniesJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ColonyProviders.TERRARIUM_DATA, TerrariumBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.STRUCTURE_DATA, StructureBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.MACHINE_DATA, GeneratorBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.MACHINE_DATA, GeneticLabBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ColonyProviders.TERRARIUM, TerrariumBlock.class);
        registration.registerBlockComponent(ColonyProviders.STRUCTURE, StructureBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneratorBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneticLabControllerBlock.class);
    }
}
