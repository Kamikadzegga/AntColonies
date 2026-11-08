package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.SpeciesUpgrade;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AntColonies.MODID);

    /** The Queen in a Queen Test Tube, or the genes in a Genetic Sample. Synced for tooltips and the tube's color. */
    public static final Supplier<DataComponentType<QueenData>> QUEEN = DATA_COMPONENTS.registerComponentType("queen",
            builder -> builder.persistent(QueenData.CODEC).networkSynchronized(QueenData.STREAM_CODEC));

    /** The ants in a filled Test Tube. */
    public static final Supplier<DataComponentType<AntSample>> ANTS = DATA_COMPONENTS.registerComponentType("ants",
            builder -> builder.persistent(AntSample.CODEC).networkSynchronized(AntSample.STREAM_CODEC));

    /** The species of Ant Eggs and Ant Larvae. */
    public static final Supplier<DataComponentType<Identifier>> SPECIES = DATA_COMPONENTS.registerComponentType("species",
            builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** What a Species Upgrade is for. */
    public static final Supplier<DataComponentType<SpeciesUpgrade>> SPECIES_UPGRADE = DATA_COMPONENTS.registerComponentType("species_upgrade",
            builder -> builder.persistent(SpeciesUpgrade.CODEC).networkSynchronized(SpeciesUpgrade.STREAM_CODEC));

    /** A whole colony (Queen, population, food) kept in a broken Terrarium, put back when it is placed again. */
    public static final Supplier<DataComponentType<net.minecraft.world.item.component.CustomData>> COLONY = DATA_COMPONENTS.registerComponentType("colony",
            builder -> builder.persistent(net.minecraft.world.item.component.CustomData.CODEC).networkSynchronized(net.minecraft.world.item.component.CustomData.STREAM_CODEC));

    /** The Guard Ant in its tube. */
    public static final Supplier<DataComponentType<com.antcolonies.content.item.GuardData>> GUARD = DATA_COMPONENTS.registerComponentType("guard",
            builder -> builder.persistent(com.antcolonies.content.item.GuardData.CODEC).networkSynchronized(com.antcolonies.content.item.GuardData.STREAM_CODEC));

    private ModDataComponents() {}
}
