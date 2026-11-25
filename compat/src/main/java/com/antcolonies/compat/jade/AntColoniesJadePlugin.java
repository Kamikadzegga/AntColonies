package com.antcolonies.compat.jade;

import com.antcolonies.content.block.CentrifugeBlock;
import com.antcolonies.content.block.GeneratorBlock;
import com.antcolonies.content.block.GeneticLabBlock;
import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.content.block.TerrariumBlock;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.blockentity.CentrifugeBlockEntity;
import com.antcolonies.content.blockentity.GeneratorBlockEntity;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.HomeNestBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.blockentity.WildNestBlockEntity;
import com.antcolonies.content.entity.WildAnt;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade look-at tooltips for the mod's blocks and wild ants; the numbers come from the server. Only loaded when Jade is installed. */
@WailaPlugin
public class AntColoniesJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ColonyProviders.TERRARIUM_DATA, TerrariumBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.STRUCTURE_DATA, StructureBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.MACHINE_DATA, GeneratorBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.MACHINE_DATA, GeneticLabBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.MACHINE_DATA, CentrifugeBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.NEST_DATA, HomeNestBlockEntity.class);
        registration.registerBlockDataProvider(ColonyProviders.NEST_DATA, WildNestBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ColonyProviders.TERRARIUM, TerrariumBlock.class);
        registration.registerBlockComponent(ColonyProviders.STRUCTURE, StructureBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneratorBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, GeneticLabBlock.class);
        registration.registerBlockComponent(ColonyProviders.MACHINE, CentrifugeBlock.class);
        registration.registerBlockComponent(ColonyProviders.WILD_NEST, WildNestBlock.class);
        registration.registerEntityComponent(ColonyProviders.WILD_ANT, WildAnt.class);
    }
}
