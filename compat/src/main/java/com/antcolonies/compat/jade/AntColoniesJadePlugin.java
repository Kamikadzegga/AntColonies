package com.antcolonies.compat.jade;

import com.antcolonies.content.block.GeneratorBlock;
import com.antcolonies.content.block.GeneticLabBlock;
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
 * Jade look-at tooltips: a Terrarium's colony, a working site's ants and output, a machine's energy and work, and what
 * a wild ant or nest is. Only loaded
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
        registration.registerBlockDataProvider(ColonyProviders.NEST_DATA, com.antcolonies.content.blockentity.HomeNestBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ColonyProviders.TERRARIUM, TerrariumBlock.class);
        registration.registerBlockComponent(ColonyProviders.STRUCTURE, StructureBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneratorBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneticLabBlock.class);
        registration.registerBlockComponent(ColonyProviders.WILD_NEST, com.antcolonies.content.block.WildNestBlock.class);
        registration.registerEntityComponent(ColonyProviders.WILD_ANT, com.antcolonies.content.entity.WildAnt.class);
    }
}
