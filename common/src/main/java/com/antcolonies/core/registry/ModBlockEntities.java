package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.blockentity.UpgradeStationBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AntColonies.MODID);

    public static final Supplier<BlockEntityType<TerrariumBlockEntity>> TERRARIUM = BLOCK_ENTITY_TYPES.register("terrarium",
            () -> new BlockEntityType<>(TerrariumBlockEntity::new, ModBlocks.TERRARIUM.get()));
    public static final Supplier<BlockEntityType<StructureBlockEntity>> STRUCTURE = BLOCK_ENTITY_TYPES.register("structure",
            () -> new BlockEntityType<>(StructureBlockEntity::new, ModBlocks.STRUCTURES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new)));
    public static final Supplier<BlockEntityType<GeneticLabBlockEntity>> GENETIC_LAB = BLOCK_ENTITY_TYPES.register("genetic_lab",
            () -> new BlockEntityType<>(GeneticLabBlockEntity::new, ModBlocks.GENETIC_LAB.get()));
    public static final Supplier<BlockEntityType<com.antcolonies.content.blockentity.GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITY_TYPES.register("generator",
            () -> new BlockEntityType<>(com.antcolonies.content.blockentity.GeneratorBlockEntity::new, ModBlocks.GENERATOR.get()));
    public static final Supplier<BlockEntityType<UpgradeStationBlockEntity>> UPGRADE_STATION = BLOCK_ENTITY_TYPES.register("upgrade_station",
            () -> new BlockEntityType<>(UpgradeStationBlockEntity::new, ModBlocks.UPGRADE_STATION.get()));

    public static final Supplier<BlockEntityType<com.antcolonies.content.blockentity.CentrifugeBlockEntity>> CENTRIFUGE = BLOCK_ENTITY_TYPES.register("centrifuge",
            () -> new BlockEntityType<>(com.antcolonies.content.blockentity.CentrifugeBlockEntity::new, ModBlocks.CENTRIFUGE.get()));

    /** Nests a player placed (only those have one: see WildNestBlock.HOME). */
    public static final Supplier<BlockEntityType<com.antcolonies.content.blockentity.HomeNestBlockEntity>> HOME_NEST = BLOCK_ENTITY_TYPES.register("home_nest",
            () -> new BlockEntityType<>(com.antcolonies.content.blockentity.HomeNestBlockEntity::new,
                    ModBlocks.NESTS.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new)));

    /** Ant Egg and Larva Clusters: their species, and the anthill of one it was built with. */
    public static final Supplier<BlockEntityType<com.antcolonies.content.blockentity.BroodClusterBlockEntity>> BROOD_CLUSTER = BLOCK_ENTITY_TYPES.register("brood_cluster",
            () -> new BlockEntityType<>(com.antcolonies.content.blockentity.BroodClusterBlockEntity::new, ModDecor.ANT_EGG_CLUSTER.get(), ModDecor.LARVA_CLUSTER.get()));

    private ModBlockEntities() {}
}
