package com.antcolonies.client;

import com.antcolonies.AntColonies;
import com.antcolonies.client.model.AntModel;
import com.antcolonies.client.model.CarryModel;
import com.antcolonies.client.model.CartModel;
import com.antcolonies.client.model.ChitinHelmetModel;
import com.antcolonies.client.renderer.CentrifugeRenderer;
import com.antcolonies.client.renderer.GeneticLabRenderer;
import com.antcolonies.client.renderer.StructureRenderer;
import com.antcolonies.client.renderer.TerrariumRenderer;
import com.antcolonies.client.renderer.WildAntRenderer;
import com.antcolonies.client.screen.CentrifugeScreen;
import com.antcolonies.client.screen.CompanionCartScreen;
import com.antcolonies.client.screen.CompanionScreen;
import com.antcolonies.client.screen.GeneratorScreen;
import com.antcolonies.client.screen.GeneticLabScreen;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.client.screen.AntScreen;
import com.antcolonies.client.screen.ConnectorScreen;
import com.antcolonies.client.screen.StructureScreen;
import com.antcolonies.client.screen.TerrariumScreen;
import com.antcolonies.client.screen.UpgradeStationScreen;
import com.antcolonies.client.terrarium.HuntVisuals;
import com.antcolonies.content.entity.GuardAnt;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.content.item.ChitinGear;
import com.antcolonies.content.recipe.CentrifugingRecipe;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;
import com.antcolonies.core.registry.ModParticles;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.core.registry.ModRecipes;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

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
        event.registerLayerDefinition(CartModel.LAYER, CartModel::create);
        event.registerLayerDefinition(ChitinHelmetModel.LAYER, ChitinHelmetModel::create);
        event.registerLayerDefinition(ChitinHelmetModel.SPINES, ChitinHelmetModel::createSpines);
        event.registerLayerDefinition(ChitinHelmetModel.WINGS, ChitinHelmetModel::createWings);
    }

    /** Fires on every resource reload, after the model layers are baked. */
    @SubscribeEvent
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        ChitinHelmetModel.bake(event.getEntityModels());
    }

    @SubscribeEvent
    static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(ChitinHelmetModel.EXTENSIONS, ModItems.CHITIN_HELMET.get());
        // Every tier's helmet is the same ant's head, in its own skin.
        ModItems.CHITIN_TIERS.values().forEach(tier -> event.registerItem(ChitinHelmetModel.EXTENSIONS, tier.get("helmet").get()));
        // Spines on the Hardened and Infernal chestplates, a Queen's wings on the Void one.
        var tiers = ModItems.CHITIN_TIERS;
        event.registerItem(ChitinHelmetModel.SPINE_EXTENSIONS,
                tiers.get(ChitinGear.Tier.HARDENED).get("chestplate").get(),
                tiers.get(ChitinGear.Tier.INFERNAL).get("chestplate").get());
        event.registerItem(ChitinHelmetModel.WING_EXTENSIONS, tiers.get(ChitinGear.Tier.VOID).get("chestplate").get());
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WILD_ANT.get(), WildAntRenderer<WildAnt>::new);
        event.registerEntityRenderer(ModEntities.GUARD_ANT.get(), WildAntRenderer<GuardAnt>::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERRARIUM.get(), TerrariumRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STRUCTURE.get(), StructureRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GENETIC_LAB.get(), GeneticLabRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CENTRIFUGE.get(), CentrifugeRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TERRARIUM.get(), TerrariumScreen::new);
        // A Colony Connector has a screen of its own.
        event.<StructureMenu, AntScreen<StructureMenu>>register(ModMenus.STRUCTURE.get(), (menu, inventory, title) ->
                menu.structure().kind() == StructureKind.COLONY_CONNECTOR ? new ConnectorScreen(menu, inventory, title)
                        : new StructureScreen(menu, inventory, title));
        event.register(ModMenus.GENETIC_LAB.get(), GeneticLabScreen::new);
        event.register(ModMenus.UPGRADE_STATION.get(), UpgradeStationScreen::new);
        event.register(ModMenus.GENERATOR.get(), GeneratorScreen::new);
        event.register(ModMenus.CENTRIFUGE.get(), CentrifugeScreen::new);
        event.register(ModMenus.COMPANION.get(), CompanionScreen::new);
        event.register(ModMenus.COMPANION_CART.get(), CompanionCartScreen::new);
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

    /** Gives the mod a Config button in the Mods list (NeoForge's configuration screen). */
    public AntColoniesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // The Droplet Centrifuge's recipes, as the server sends them; dropped on leaving the world.
        NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent event) -> {
            List<CentrifugingRecipe> recipes = new ArrayList<>();
            event.getRecipeMap().byType(ModRecipes.CENTRIFUGING.get()).forEach(holder -> recipes.add(holder.value()));
            CentrifugingRecipe.setClientRecipes(recipes);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)
                -> CentrifugingRecipe.setClientRecipes(List.of()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientResearch.clear());
        // The Hunting Grounds' stand-in mobs belong to the world being left (the server's own levels unload on its thread: not those).
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> HuntVisuals.clear());
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) {
                HuntVisuals.clear();
            }
        });
    }
}
