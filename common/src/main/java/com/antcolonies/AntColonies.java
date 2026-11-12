package com.antcolonies;

import org.slf4j.Logger;

import com.antcolonies.colony.model.ColonyNetwork;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModCapabilities;
import com.antcolonies.core.registry.ModCreativeTabs;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;
import com.antcolonies.core.registry.ModParticles;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Ant Colonies: wild ants, Terrariums, Queens with real genetics, and colonies whose population is plain numbers on
 * the server. The ants drawn in a Terrarium are only a picture of those numbers, never their source.
 */
@Mod(AntColonies.MODID)
public class AntColonies {
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntColonies(IEventBus modEventBus, ModContainer modContainer) {
        AntConfig.register(modContainer);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.antcolonies.core.advancement.ModTriggers.TRIGGERS.register(modEventBus);
        com.antcolonies.core.registry.ModStructures.STRUCTURE_TYPES.register(modEventBus);
        com.antcolonies.core.registry.ModStructures.STRUCTURE_PIECES.register(modEventBus);
        // Species, evolutions, hybrids and mutations are datapack registries, synced to clients (JEI, rendering).
        modEventBus.addListener(ModRegistries::register);
        modEventBus.addListener(ModDataMaps::register);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(WildAnt::registerSpawnPlacement);
        modEventBus.addListener(ModCapabilities::register);
        // Any colony block placed or broken makes nearby Terrariums look at their network again.
        NeoForge.EVENT_BUS.addListener(ColonyNetwork::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.antcolonies.content.item.ChitinGear::onPlayerTick);
        // Colony Connectors move food and items between the colonies on their channels.
        NeoForge.EVENT_BUS.addListener(com.antcolonies.colony.network.ConnectorNetwork::onLevelTick);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
