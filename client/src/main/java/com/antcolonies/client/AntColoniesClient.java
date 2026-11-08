package com.antcolonies.client;

import com.antcolonies.AntColonies;
import com.antcolonies.client.model.AntModel;
import com.antcolonies.client.model.CarryModel;
import com.antcolonies.client.model.ChitinHelmetModel;
import com.antcolonies.client.renderer.GeneticLabRenderer;
import com.antcolonies.client.renderer.StructureRenderer;
import com.antcolonies.client.renderer.TerrariumRenderer;
import com.antcolonies.client.renderer.WildAntRenderer;
import com.antcolonies.client.screen.GeneticLabScreen;
import com.antcolonies.client.screen.StructureScreen;
import com.antcolonies.client.screen.TerrariumScreen;
import com.antcolonies.client.screen.UpgradeStationScreen;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;
import com.antcolonies.core.registry.ModParticles;
import com.antcolonies.colony.species.Caste;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client setup: renderers, model layers, screens, particles and the species tint. Never loaded on a dedicated server. */
@Mod(value = AntColonies.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AntColonies.MODID, value = Dist.CLIENT)
public final class AntColoniesClient {
    @SubscribeEvent
    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AntModel.WORKER, () -> AntModel.create(Caste.WORKER));
        event.registerLayerDefinition(AntModel.SOLDIER, () -> AntModel.create(Caste.SOLDIER));
        event.registerLayerDefinition(AntModel.QUEEN, () -> AntModel.create(Caste.QUEEN));
        event.registerLayerDefinition(CarryModel.LAYER, CarryModel::create);
        event.registerLayerDefinition(ChitinHelmetModel.LAYER, ChitinHelmetModel::create);
    }

    /** Fires on every resource reload, after the model layers are baked. */
    @SubscribeEvent
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        ChitinHelmetModel.bake(event.getEntityModels());
    }

    @SubscribeEvent
    static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(ChitinHelmetModel.EXTENSIONS, ModItems.CHITIN_HELMET.get());
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WILD_ANT.get(), WildAntRenderer<com.antcolonies.content.entity.WildAnt>::new);
        event.registerEntityRenderer(ModEntities.GUARD_ANT.get(), WildAntRenderer<com.antcolonies.content.entity.GuardAnt>::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERRARIUM.get(), TerrariumRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STRUCTURE.get(), StructureRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GENETIC_LAB.get(), GeneticLabRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TERRARIUM.get(), TerrariumScreen::new);
        event.register(ModMenus.STRUCTURE.get(), StructureScreen::new);
        event.register(ModMenus.GENETIC_LAB.get(), GeneticLabScreen::new);
        event.register(ModMenus.UPGRADE_STATION.get(), UpgradeStationScreen::new);
        event.register(ModMenus.GENERATOR.get(), com.antcolonies.client.screen.GeneratorScreen::new);
    }

    @SubscribeEvent
    static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOIL.get(), sprites -> new AntParticle.Provider(sprites, AntParticle.Kind.SOIL));
        event.registerSpriteSet(ModParticles.CRUMB.get(), sprites -> new AntParticle.Provider(sprites, AntParticle.Kind.CRUMB));
        event.registerSpriteSet(ModParticles.SPARKLE.get(), sprites -> new AntParticle.Provider(sprites, AntParticle.Kind.SPARKLE));
        event.registerSpriteSet(ModParticles.GENE.get(), sprites -> new AntParticle.Provider(sprites, AntParticle.Kind.GENE));
        event.registerSpriteSet(ModParticles.EVOLUTION.get(), sprites -> new AntParticle.Provider(sprites, AntParticle.Kind.EVOLUTION));
    }

    @SubscribeEvent
    static void registerTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(SpeciesTint.ID, SpeciesTint.MAP_CODEC);
    }

    /**
     * Gives the mod a Config button in the Mods list: NeoForge's configuration screen, with every client option (how the
     * ants are drawn, particles, sounds) and, in single-player, the server options.
     */
    public AntColoniesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
