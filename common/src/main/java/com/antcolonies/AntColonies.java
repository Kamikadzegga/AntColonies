package com.antcolonies;

import org.slf4j.Logger;

import com.antcolonies.colony.liquid.LiquidKinds;
import com.antcolonies.colony.model.ColonyNetwork;
import com.antcolonies.colony.network.ConnectorNetwork;
import com.antcolonies.colony.resource.MobLoot;
import com.antcolonies.compat.liquid.LiquidIntegrations;
import com.antcolonies.content.item.ChitinGear;
import com.antcolonies.content.worldgen.AnthillAlarm;
import com.antcolonies.core.advancement.ModTriggers;
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
import com.antcolonies.core.registry.ModRecipes;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.core.registry.ModStructures;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

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
        ModTriggers.TRIGGERS.register(modEventBus);
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PIECES.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipes.RECIPE_BOOK_CATEGORIES.register(modEventBus);
        // Liquid Ants: fluids are offered now; droplet items wait until every mod's fluids are registered.
        LiquidIntegrations.offerAll();
        modEventBus.addListener(LiquidKinds::registerItems);
        // Species, evolutions, hybrids and mutations are datapack registries, synced to clients (JEI, rendering).
        modEventBus.addListener(ModRegistries::register);
        modEventBus.addListener(ModDataMaps::register);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(WildAnt::registerSpawnPlacement);
        modEventBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(ColonyNetwork::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MobLoot::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ChitinGear::onPlayerTick);
        // Opening or breaking an anthill's chests raises its alarm.
        NeoForge.EVENT_BUS.addListener(AnthillAlarm::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(AnthillAlarm::onBreakBlock);
        // Colony Connectors move food and items between the colonies on their channels.
        NeoForge.EVENT_BUS.addListener(ConnectorNetwork::onLevelTick);
        // The Droplet Centrifuge's recipes go to every client.
        NeoForge.EVENT_BUS.addListener(ModRecipes::onDatapackSync);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
