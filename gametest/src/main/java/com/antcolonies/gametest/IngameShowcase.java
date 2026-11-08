package com.antcolonies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.Genome;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.Caste;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only visual check, run in the real client when the run folder holds a file named {@code ingame-showcase}: makes
 * a flat creative world, builds a stage 4 Diamond colony with its structures and tubes, a young Common colony, a
 * Genetic Laboratory and a few wild ants, then takes screenshots (close-ups and every GUI page) into run/screenshots
 * and quits. What it saw goes to {@code ingame-showcase-result.txt}.
 */
@Mod(value = AntColonies.MODID, dist = Dist.CLIENT)
public class IngameShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path MARKER = FMLPaths.GAMEDIR.get().resolve("ingame-showcase");
    private static final Path RESULT = FMLPaths.GAMEDIR.get().resolve("ingame-showcase-result.txt");

    private final List<String> results = new ArrayList<>();
    private boolean worldRequested;
    private boolean createRequested;
    private int ticks = -1;
    private BlockPos origin;

    public IngameShowcase(IEventBus modEventBus) {
        if (Files.exists(MARKER) || Files.exists(GUI_MARKER)) {
            LOGGER.info("[Showcase] marker found");
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
        } else if (Files.exists(CONFIG_MARKER)) {
            LOGGER.info("[Showcase] config check");
            NeoForge.EVENT_BUS.addListener(this::onConfigCheckTick);
        }
    }

    /** A file named {@code config-check} in the run folder: only open the config screen, take pictures and quit. */
    private static final Path CONFIG_MARKER = FMLPaths.GAMEDIR.get().resolve("config-check");
    private int configTicks = -1;

    /** The config-only check: from the title screen, open the Config screen, then its client page. */
    private void onConfigCheckTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (configTicks < 0) {
            if (minecraft.gui.screen() != null && minecraft.gui.screen().getClass().getSimpleName().equals("LoadingErrorScreen")) {
                minecraft.gui.setScreen(new TitleScreen());
            }
            if (!(minecraft.gui.screen() instanceof TitleScreen)) {
                return;
            }
            minecraft.options.pauseOnLostFocus = false;
        }
        configTicks++;
        switch (configTicks) {
            case 10 -> {
                var container = net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow();
                minecraft.gui.setScreen(net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.getModInfo())
                        .map(factory -> factory.createScreen(container, new TitleScreen())).orElse(null));
                result("config screen: " + (minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getSimpleName()));
            }
            case 30 -> {
                screenshot(minecraft, "32_config_screen");
                clickButton(minecraft, "Client Settings");
            }
            case 50 -> screenshot(minecraft, "33_config_client");
            case 52 -> {
                // The tooltip of the first option, as a player sees it on hover.
                var widget = findWidget(minecraft.gui.screen().children(), "3D ant models");
                result("3D ant models option: " + (widget == null ? "not found" : "found"));
            }
            case 55 -> finish(minecraft);
            default -> { }
        }
    }

    /** A file named {@code gui-check} in the run folder: build the demo, screenshot every colony screen and the lab, quit. */
    private static final Path GUI_MARKER = FMLPaths.GAMEDIR.get().resolve("gui-check");
    private final boolean guiCheck = Files.exists(GUI_MARKER);
    /** Screens to open in the GUI check: a name for the picture and the block's offset from the origin. */
    private static final Object[][] GUI_TARGETS = {
            {"upgrades_terrarium", 0, 0, 5}, {"upgrades_foraging_site", -1, 0, 4}, {"upgrades_nest_chamber", 1, 0, 6},
            {"storage", 0, 0, 4}, {"foraging_site", 1, 0, 4}, {"nest_chamber", 1, 0, 6}, {"queen_chamber", 0, 0, 6},
            {"foraging_site_soldiers", -1, 0, 6}, {"nest_chamber_west", -1, 0, 5}, {"harvesting_site", -6, 0, 6}, {"young_terrarium", -6, 0, 5},
            {"genetic_laboratory", 0, 0, 10}, {"lonely_nest_chamber", 3, 0, -4}, {"lonely_queen_chamber", 5, 0, -4},
            {"lonely_foraging_site", 3, 0, -6}};

    private void guiStep(Minecraft minecraft, IntegratedServer server) {
        if (ticks == 60) {
            origin = minecraft.player.blockPosition();
            minecraft.options.pauseOnLostFocus = false;
            server.execute(() -> {
                command(server, "difficulty peaceful");
                build(server);
                buildGuiExtras(server);
            });
            return;
        }
        if (ticks < 140 || origin == null) {
            return;
        }
        int index = (ticks - 140) / 30;
        int phase = (ticks - 140) % 30;
        if (index < GUI_TARGETS.length) {
            Object[] target = GUI_TARGETS[index];
            BlockPos pos = origin.offset((int) target[1], (int) target[2], (int) target[3]);
            if (phase == 0) {
                // Step up to the block first: a screen closes when its block is more than 8 blocks away.
                server.execute(() -> {
                    command(server, String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f", pos.getX() + 0.5, pos.getY(), pos.getZ() - 2.5));
                    open(server, pos);
                });
            } else if (phase == 25) {
                screenshot(minecraft, "g_" + target[0]);
            }
            return;
        }
        finish(minecraft);
    }

    /** Upgrades in the Terrarium and two chambers (to see them in their slots), and three chambers with no Terrarium. */
    private void buildGuiExtras(IntegratedServer server) {
        ServerLevel level = server.overworld();
        var terrariumUpgrades = ((TerrariumBlockEntity) level.getBlockEntity(origin.offset(0, 0, 5))).upgrades();
        terrariumUpgrades.setItem(0, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 3)));
        terrariumUpgrades.setItem(1, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SIMULATION, 2)));
        terrariumUpgrades.setItem(2, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.EVOLUTION, 1)));
        var siteUpgrades = ((StructureBlockEntity) level.getBlockEntity(origin.offset(-1, 0, 4))).upgrades();
        siteUpgrades.setItem(0, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SPEED, 1)));
        siteUpgrades.setItem(1, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SPEED, 2)));
        siteUpgrades.setItem(2, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 3)));
        ((StructureBlockEntity) level.getBlockEntity(origin.offset(1, 0, 6))).upgrades()
                .setItem(0, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.BREEDING, 2)));
        // Three chambers with no Terrarium, apart from each other: their screens say they aren't part of a colony.
        structure(level, origin.offset(3, 0, -4), StructureKind.NEST_CHAMBER);
        structure(level, origin.offset(5, 0, -4), StructureKind.QUEEN_CHAMBER);
        structure(level, origin.offset(3, 0, -6), StructureKind.FORAGING_SITE);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
                startWorld(minecraft);
                return;
            }
            ticks++;
            if (guiCheck) {
                guiStep(minecraft, minecraft.getSingleplayerServer());
            } else {
                step(minecraft, minecraft.getSingleplayerServer());
            }
        } catch (Exception e) {
            LOGGER.error("[Showcase] failed", e);
            result("ERROR " + e);
            finish(minecraft);
        }
    }

    private void startWorld(Minecraft minecraft) throws Exception {
        // Mods with load warnings (EMI's port has two) open a warnings screen before the title screen: go on past it.
        if (!worldRequested && minecraft.gui.screen() != null && minecraft.gui.screen().getClass().getSimpleName().equals("LoadingErrorScreen")) {
            result("skipped the mod loading warnings screen");
            minecraft.gui.setScreen(new TitleScreen());
            return;
        }
        if (minecraft.gui.screen() instanceof TitleScreen && !worldRequested) {
            worldRequested = true;
            CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(new TitleScreen()));
        } else if (minecraft.gui.screen() instanceof CreateWorldScreen create && !createRequested) {
            WorldCreationUiState state = create.getUiState();
            WorldCreationUiState.WorldTypeEntry flat = state.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(WorldPresets.FLAT)).findFirst().orElse(null);
            if (flat == null) {
                return;
            }
            createRequested = true;
            state.setName("Ant Colonies Showcase");
            state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            state.setAllowCommands(true);
            state.setWorldType(flat);
            var onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(create);
        }
    }

    private void step(Minecraft minecraft, IntegratedServer server) {
        switch (ticks) {
            case 60 -> {
                origin = minecraft.player.blockPosition();
                // No pause menu when the window loses focus, and no slimes in the pictures.
                minecraft.options.pauseOnLostFocus = false;
                server.execute(() -> command(server, "difficulty peaceful"));
                if (!minecraft.gui.hud.isHidden()) {
                    minecraft.gui.hud.toggle();
                }
                server.execute(() -> build(server));
            }
            case 120 -> camera(server, 0.5, 3.4, -2.5, 0, 38);
            case 160 -> screenshot(minecraft, "01_overview");
            case 165 -> camera(server, 0.5, 1.25 - 1.62, 3.4, 0, 20);
            case 205 -> screenshot(minecraft, "02_terrarium_front");
            case 210 -> camera(server, -1.7, 1.9 - 1.62, 5.5, -90, 35);
            case 250 -> screenshot(minecraft, "03_terrarium_side");
            case 255 -> camera(server, 0.5, 2.2 - 1.62, 4.6, 0, 70);
            case 295 -> screenshot(minecraft, "04_terrarium_top");
            case 300 -> camera(server, -5.5, 1.25 - 1.62, 3.4, 0, 20);
            case 340 -> screenshot(minecraft, "05_young_colony");
            case 345 -> camera(server, 0.5, 3.2 - 1.62, 2.4, 0, 50);
            case 385 -> screenshot(minecraft, "06_job_sites");
            case 390 -> camera(server, 0.2, 1.05 - 1.62, -1.0, 0, 38);
            case 430 -> screenshot(minecraft, "07_wild_ants");
            case 435 -> camera(server, 0.5, 2.2 - 1.62, 10.5 - 3.2, 0, 25);
            case 475 -> screenshot(minecraft, "08_genetic_lab");
            case 480 -> {
                if (minecraft.gui.hud.isHidden()) {
                    minecraft.gui.hud.toggle();
                }
                server.execute(() -> open(server, origin.offset(0, 0, 5)));
            }
            case 510 -> screenshot(minecraft, "09_gui_overview");
            case 512 -> clickTab(minecraft, 1);
            case 530 -> screenshot(minecraft, "10_gui_genetics");
            case 532 -> clickTab(minecraft, 2);
            case 550 -> screenshot(minecraft, "11_gui_jobs");
            case 552 -> clickTab(minecraft, 3);
            case 570 -> screenshot(minecraft, "12_gui_stats");
            case 571 -> clickTab(minecraft, 4);
            case 574 -> screenshot(minecraft, "12b_gui_species");
            case 575 -> {
                server.execute(() -> open(server, origin.offset(-1, 0, 4)));
            }
            case 600 -> screenshot(minecraft, "13_gui_foraging_site");
            case 605 -> {
                server.execute(() -> open(server, origin.offset(0, 0, 10)));
            }
            case 630 -> screenshot(minecraft, "14_gui_genetic_lab");
            case 633 -> server.execute(() -> {
                GeneticLabBlockEntity lab = (GeneticLabBlockEntity) server.overworld().getBlockEntity(origin.offset(0, 0, 10));
                lab.start(GeneticLabBlockEntity.Operation.UPGRADE, server.registryAccess());
                result("lab start: " + lab.operation() + ", energy " + lab.energy() + ", status "
                        + com.antcolonies.colony.model.ColonyStatus.of(lab.data().get(GeneticLabBlockEntity.DATA_STATUS)));
            });
            case 678 -> {
                if (minecraft.player.containerMenu instanceof com.antcolonies.content.menu.GeneticLabMenu labMenu) {
                    result("client lab: operation " + labMenu.data(GeneticLabBlockEntity.DATA_OPERATION) + ", progress "
                            + labMenu.data(GeneticLabBlockEntity.DATA_PROGRESS) + "/" + labMenu.data(GeneticLabBlockEntity.DATA_TIME));
                }
                server.execute(() -> {
                    GeneticLabBlockEntity lab = (GeneticLabBlockEntity) server.overworld().getBlockEntity(origin.offset(0, 0, 10));
                    var player = server.getPlayerList().getPlayers().getFirst();
                    result("server lab: operation " + lab.operation() + ", progress " + lab.data().get(GeneticLabBlockEntity.DATA_PROGRESS)
                            + ", player menu " + player.containerMenu.getClass().getSimpleName()
                            + (player.containerMenu instanceof com.antcolonies.content.menu.GeneticLabMenu m ? ", same lab " + (m.lab() == lab)
                            + ", menu sees " + m.data(GeneticLabBlockEntity.DATA_OPERATION) : ""));
                });
            }
            case 680 -> screenshot(minecraft, "14b_gui_genetic_lab_working");
            case 685 -> {
                minecraft.player.closeContainer();
                server.execute(() -> report(server));
            }
            case 690 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.EVOLUTION);
            case 715 -> screenshot(minecraft, "15_jei_evolution");
            case 720 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.SPECIES);
            case 745 -> screenshot(minecraft, "16_jei_species");
            case 750 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.PRODUCTION);
            case 775 -> screenshot(minecraft, "17_jei_production");
            case 780 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.LAB);
            case 805 -> screenshot(minecraft, "18_jei_lab");
            case 810 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.HYBRID);
            case 835 -> screenshot(minecraft, "19_jei_hybrid");
            case 845 -> {
                minecraft.gui.setScreen(null);
                server.execute(() -> open(server, origin.offset(0, 0, 4)));
            }
            case 870 -> screenshot(minecraft, "20_gui_storage");
            case 875 -> {
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(new com.antcolonies.client.screen.IdentificationBookScreen());
            }
            case 893 -> screenshot(minecraft, "21_book");
            case 894 -> {
                // The second species in the book's list (after its how-to-play pages).
                if (minecraft.gui.screen() instanceof com.antcolonies.client.screen.IdentificationBookScreen book) {
                    int left = (book.width - 330) / 2;
                    int top = (book.height - 196) / 2;
                    book.mouseClicked(new MouseButtonEvent(left + 30, top + 9 + 10 * 10 + 4, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                }
            }
            case 898 -> screenshot(minecraft, "21b_book_species");
            // Jade look-at tooltips (when Jade is in run/mods): the crosshair on a Terrarium, a job site and the lab.
            case 900 -> {
                minecraft.gui.setScreen(null);
                look(server, 0.5, 1.2, 3.3, 0, 41);
            }
            case 930 -> screenshot(minecraft, "22_jade_terrarium");
            case 935 -> look(server, -0.5, 0, 2.2, 0, 30);
            case 965 -> screenshot(minecraft, "23_jade_foraging_chamber");
            case 970 -> look(server, 0.5, 1.2, 8.5, 0, 50);
            case 1000 -> screenshot(minecraft, "24_jade_genetic_lab");
            // EMI (when it is in run/mods): two of the mod's categories in EMI's recipe screen.
            case 1005 -> emi(minecraft, "production");
            case 1035 -> screenshot(minecraft, "25_emi_production");
            case 1040 -> emi(minecraft, "evolution");
            case 1070 -> screenshot(minecraft, "26_emi_evolution");
            case 1075 -> emi(minecraft, "species");
            case 1105 -> screenshot(minecraft, "27_emi_species");
            case 1108 -> minecraft.gui.setScreen(null);
            // The giant anthill (on a raised stone platform: the flat world is too shallow to dig into), Guard Ants and
            // Chitin gear.
            case 1112 -> server.execute(() -> anthill(server));
            case 1120 -> camera(server, 22.5, 26, -11, 0, 33);
            case 1160 -> screenshot(minecraft, "28_anthill_mound");
            case 1165 -> server.execute(() -> anthillInside(server));
            case 1205 -> screenshot(minecraft, "29_anthill_queen_chamber");
            case 1210 -> {
                server.execute(() -> command(server, "effect clear @s minecraft:night_vision"));
                server.execute(() -> guardShowcase(server));
                camera(server, -7.5, 0.6, -6.5, 0, 22);
            }
            case 1250 -> screenshot(minecraft, "30_guard_ant_and_chitin");
            case 1255 -> {
                if (minecraft.gui.hud.isHidden()) {
                    minecraft.gui.hud.toggle();
                }
                server.execute(() -> {
                    command(server, "gamemode creative");
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    net.minecraft.world.item.Item[] items = {ModItems.CHITIN_SWORD.get(), ModItems.CHITIN_PICKAXE.get(), ModItems.CHITIN_AXE.get(),
                            ModItems.CHITIN_SHOVEL.get(), ModItems.CHITIN_HOE.get(), ModItems.CHITIN_HELMET.get(), ModItems.CHITIN_CHESTPLATE.get(),
                            ModItems.CHITIN_LEGGINGS.get(), ModItems.CHITIN_BOOTS.get()};
                    for (int i = 0; i < items.length; i++) {
                        player.getInventory().setItem(i, new ItemStack(items[i]));
                    }
                    player.getInventory().setItem(9, new ItemStack(ModItems.RALLY_PHEROMONE.get()));
                });
            }
            case 1275 -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
            case 1295 -> screenshot(minecraft, "31_chitin_items");
            case 1298 -> minecraft.gui.setScreen(null);
            // The Config button in the Mods list: NeoForge's configuration screen, then its client page.
            case 1300 -> minecraft.gui.setScreen(net.neoforged.neoforge.client.gui.IConfigScreenFactory
                    .getForMod(net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow().getModInfo())
                    .map(factory -> factory.createScreen(net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow(), null))
                    .orElse(null));
            case 1320 -> {
                screenshot(minecraft, "32_config_screen");
                clickButton(minecraft, "Client Settings");
            }
            case 1340 -> screenshot(minecraft, "33_config_client");
            case 1343 -> minecraft.gui.setScreen(null);
            default -> {
                if (ticks > 1345) {
                    audit(minecraft, server, ticks - 1350);
                }
            }
        }
    }

    /** The showcase, in front of the player (who looks south, +z). */
    private void build(IntegratedServer server) {
        ServerLevel level = server.overworld();
        command(server, "time set 6000");
        command(server, "weather clear");
        // The Diamond colony: a full ring, its low Storage in front so the Terrarium shows behind it.
        BlockPos t = origin.offset(0, 0, 5);
        place(level, t, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        StructureKind[] diamondRing = {StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.FORAGING_SITE,
                StructureKind.FORAGING_SITE, StructureKind.NEST_CHAMBER, StructureKind.QUEEN_CHAMBER, StructureKind.FORAGING_SITE,
                StructureKind.NEST_CHAMBER};
        ring(level, t, diamondRing);
        place(level, origin.offset(6, 0, 3), ModBlocks.UPGRADE_STATION.get().defaultBlockState());
        StructureBlockEntity pile = (StructureBlockEntity) level.getBlockEntity(origin.offset(0, 0, 4));
        pile.setItem(0, new ItemStack(ModItems.DIAMOND_FRAGMENT.get(), 64));
        pile.setItem(1, new ItemStack(ModItems.DIAMOND_FRAGMENT.get(), 40));
        pile.setItem(2, new ItemStack(Items.COAL, 50));
        pile.setItem(3, new ItemStack(Items.DIAMOND, 6));
        TerrariumBlockEntity diamond = (TerrariumBlockEntity) level.getBlockEntity(t);
        Colony colony = diamond.colony();
        QueenData queen = Genetics.wildQueen(AntColonies.id("diamond"), RandomSource.create(4));
        Genome genome = queen.genome();
        for (Trait trait : Trait.values()) {
            genome = genome.withLevel(trait, 5 + trait.ordinal() % 5);
        }
        colony.setQueen(queen.withGenome(genome.withMutation(AntColonies.id("crystal_genetics"))));
        colony.setPopulation(300, 420, 6200, 640);
        colony.setFood(20000);
        colony.addFood(0, 4000);
        diamond.rescan(level);
        for (int i = 0; i < 4; i++) {
            ColonySimulator.step(diamond, level, 0.05);
        }
        diamond.syncNow();
        // A young Fire Ant colony (one of the newer species) with a smaller build: a chamber on each side of its Terrarium.
        BlockPos young = origin.offset(-6, 0, 5);
        place(level, young, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        structure(level, young.north(), StructureKind.STORAGE);
        structure(level, young.east(), StructureKind.FORAGING_SITE);
        structure(level, young.south(), StructureKind.HARVESTING_SITE);
        structure(level, young.west(), StructureKind.NEST_CHAMBER);
        TerrariumBlockEntity common = (TerrariumBlockEntity) level.getBlockEntity(young);
        common.colony().setQueen(Genetics.wildQueen(AntColonies.id("fire"), RandomSource.create(8)));
        common.colony().setPopulation(6, 4, 9, 0);
        common.colony().setFood(800);
        common.rescan(level);
        ColonySimulator.step(common, level, 0.05);
        common.syncNow();
        // The Genetic Laboratory with a Queen inside, a Generator burning coal beside it, the lab already charged (through
        // its FE input, as a cable would).
        BlockPos lab = origin.offset(0, 0, 10);
        place(level, lab, ModBlocks.GENETIC_LAB.get().defaultBlockState());
        GeneticLabBlockEntity labEntity = (GeneticLabBlockEntity) level.getBlockEntity(lab);
        ItemStack tube = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        tube.set(ModDataComponents.QUEEN.get(), colony.queen());
        labEntity.setItem(GeneticLabBlockEntity.SLOT_QUEEN, tube);
        place(level, lab.east(), ModBlocks.GENERATOR.get().defaultBlockState());
        ((com.antcolonies.content.blockentity.GeneratorBlockEntity) level.getBlockEntity(lab.east())).setItem(0, new ItemStack(Items.COAL, 16));
        for (int i = 0; i < 50; i++) {
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                labEntity.energyHandler().insert(4000, transaction);
                transaction.commit();
            }
        }
        labEntity.setItem(GeneticLabBlockEntity.SLOT_MATERIAL, new ItemStack(ModItems.GENETIC_CIRCUIT.get(), 4));
        labEntity.setItem(GeneticLabBlockEntity.SLOT_MATERIAL + 1, new ItemStack(Items.IRON_INGOT, 12));
        labEntity.setItem(GeneticLabBlockEntity.SLOT_CATALYST, new ItemStack(Items.EMERALD, 4));
        // A second lab with no Queen and no power, for the audit.
        place(level, origin.offset(-7, 0, 10), ModBlocks.GENETIC_LAB.get().defaultBlockState());
        // Wild ants of a few species.
        String[] species = {"common", "wood", "desert", "snow", "farmer"};
        for (int i = 0; i < species.length; i++) {
            WildAnt ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.COMMAND);
            if (ant != null) {
                ant.setSpecies(AntColonies.id(species[i]));
                ant.setCaste(i == 1 ? Caste.SOLDIER : i == 4 ? Caste.QUEEN : Caste.WORKER);
                ant.snapTo(origin.getX() - 1.2 + i * 0.7, origin.getY(), origin.getZ() + 0.4 + (i % 2) * 0.5, 180 + i * 25, 0);
                ant.setNoAi(true);
                level.addFreshEntity(ant);
            }
        }
        tooltipItems(server);
        result("built at " + origin + ", diamond stage " + colony.stage() + ", status " + com.antcolonies.colony.model.ColonyStatus.of(colony.status()));
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static StructureBlockEntity structure(ServerLevel level, BlockPos pos, StructureKind kind) {
        level.setBlock(pos, ModBlocks.STRUCTURES.get(kind).get().defaultBlockState(), Block.UPDATE_ALL);
        return (StructureBlockEntity) level.getBlockEntity(pos);
    }

    /** The eight places around a Terrarium on its level, north-west first, clockwise. */
    private static List<BlockPos> places(BlockPos center) {
        return List.of(center.offset(-1, 0, -1), center.north(), center.offset(1, 0, -1), center.east(), center.offset(1, 0, 1), center.south(),
                center.offset(-1, 0, 1), center.west());
    }

    /** Chambers all around a Terrarium (north-west first, clockwise). */
    private static void ring(ServerLevel level, BlockPos center, StructureKind[] kinds) {
        List<BlockPos> places = places(center);
        for (int i = 0; i < places.size(); i++) {
            structure(level, places.get(i), kinds[i % kinds.length]);
        }
    }

    private static void command(IntegratedServer server, String command) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), command);
    }

    private void camera(IntegratedServer server, double dx, double dy, double dz, float yaw, float pitch) {
        double x = origin.getX() + dx;
        double y = origin.getY() + dy;
        double z = origin.getZ() + dz;
        server.execute(() -> {
            command(server, "gamemode spectator");
            command(server, String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch));
        });
    }

    /** Stand (flying, in creative, so Jade shows) at this spot and look. */
    private void look(IntegratedServer server, double dx, double dy, double dz, float yaw, float pitch) {
        double x = origin.getX() + dx;
        double y = origin.getY() + dy;
        double z = origin.getZ() + dz;
        server.execute(() -> {
            command(server, "gamemode creative");
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            command(server, String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch));
        });
    }

    private static void open(IntegratedServer server, BlockPos pos) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        command(server, "gamemode creative");
        if (server.overworld().getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider) {
            player.openMenu(provider, pos);
        }
    }

    /** A widget whose label contains the text, also inside lists and other containers. */
    private static net.minecraft.client.gui.components.AbstractWidget findWidget(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children,
                                                                                 String label) {
        for (var child : children) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getMessage().getString().contains(label)) {
                return widget;
            }
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
                var found = findWidget(container.children(), label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Clicks the first button whose label contains the text, as a player would. */
    private void clickButton(Minecraft minecraft, String label) {
        var screen = minecraft.gui.screen();
        if (screen == null) {
            result("FAIL no screen to click " + label);
            return;
        }
        var widget = findWidget(screen.children(), label);
        if (widget != null) {
            double x = widget.getX() + widget.getWidth() / 2.0;
            double y = widget.getY() + widget.getHeight() / 2.0;
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            result("clicked " + widget.getMessage().getString());
            return;
        }
        result("FAIL no button " + label + " on " + screen.getClass().getSimpleName());
    }

    private void clickTab(Minecraft minecraft, int tab) {
        if (minecraft.gui.screen() instanceof com.antcolonies.client.screen.TerrariumScreen) {
            com.antcolonies.client.screen.TerrariumScreen.showPage(tab);
        } else {
            result("FAIL no container screen: " + minecraft.gui.screen());
        }
    }

    /** The anthill's spot: 22 blocks east, on a stone platform 20 blocks thick. */
    private BlockPos anthillCenter() {
        return origin.offset(22, 19, 0);
    }

    private void anthill(IntegratedServer server) {
        ServerLevel level = server.overworld();
        BlockPos center = anthillCenter();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-14, -21, -14), center.offset(14, 0, 14))) {
            net.minecraft.world.level.block.Block block = pos.getY() == center.getY() ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                    : pos.getY() > center.getY() - 3 ? net.minecraft.world.level.block.Blocks.DIRT : net.minecraft.world.level.block.Blocks.STONE;
            level.setBlock(pos, block.defaultBlockState(), 2);
        }
        com.antcolonies.content.worldgen.AnthillPiece piece = new com.antcolonies.content.worldgen.AnthillPiece(center);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), piece.getBoundingBox(),
                net.minecraft.world.level.ChunkPos.containing(center), center);
        var ants = level.getEntitiesOfClass(com.antcolonies.content.entity.WildAnt.class, new net.minecraft.world.phys.AABB(center).inflate(14, 22, 14));
        result("anthill: " + ants.size() + " ants, " + ants.stream().filter(com.antcolonies.content.entity.WildAnt::guardian).count() + " guardians, species "
                + (ants.isEmpty() ? "-" : ants.getFirst().species()));
    }

    /** The camera in the Queen chamber, looking at her (with night vision: it is dark down there). */
    private void anthillInside(IntegratedServer server) {
        ServerLevel level = server.overworld();
        var queen = level.getEntitiesOfClass(com.antcolonies.content.entity.WildAnt.class, new net.minecraft.world.phys.AABB(anthillCenter()).inflate(14, 22, 14),
                ant -> ant.caste() == Caste.QUEEN).stream().findFirst().orElse(null);
        if (queen == null) {
            result("FAIL anthill: no Queen");
            return;
        }
        command(server, "effect give @s minecraft:night_vision 60 0 true");
        command(server, "gamemode spectator");
        command(server, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", queen.getX() - 2.6, queen.getY() + 0.4, queen.getZ() - 2.6,
                -45.0F, 18.0F));
    }

    /** A Guard Ant beside an armor stand in Chitin, and a wild soldier for size. */
    private void guardShowcase(IntegratedServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        BlockPos spot = origin.offset(-8, 0, -3);
        var stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        stand.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180, 0);
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.CHITIN_HELMET.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.CHITIN_SWORD.get()));
        level.addFreshEntity(stand);
        var guard = com.antcolonies.core.registry.ModEntities.GUARD_ANT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        guard.snapTo(spot.getX() + 1.6, spot.getY(), spot.getZ() + 0.6, 200, 0);
        // Owned without taming, so no advancement toast covers the picture.
        guard.setTame(true, false);
        guard.setOwner(player);
        guard.setup(new com.antcolonies.content.item.GuardData(AntColonies.id("netherite"), 8, -1));
        guard.setOrderedToSit(true);
        guard.setInSittingPose(true);
        level.addFreshEntity(guard);
        var wild = com.antcolonies.core.registry.ModEntities.WILD_ANT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        wild.snapTo(spot.getX() - 1.2, spot.getY(), spot.getZ() + 0.4, 160, 0);
        wild.setSpecies(AntColonies.id("netherite"));
        wild.setCaste(Caste.SOLDIER);
        wild.setNoAi(true);
        level.addFreshEntity(wild);
        result("guard: health " + guard.getHealth() + "/" + guard.getMaxHealth() + ", armor " + guard.getArmorValue() + ", sitting " + guard.isInSittingPose());
    }

    private void emi(Minecraft minecraft, String category) {
        if (!net.neoforged.fml.ModList.get().isLoaded("emi")) {
            return;
        }
        if (minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
        }
        result(EmiShowcase.open(category));
    }

    private void jei(mezz.jei.api.recipe.types.IRecipeType<?> type) {
        if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
            return; // With EMI installed, JEI's recipe screen doesn't start: EMI shows the recipes.
        }
        if (com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime == null) {
            result("FAIL JEI runtime missing");
            return;
        }
        com.antcolonies.compat.jei.AntColoniesJeiPlugin.runtime.getRecipesGui().showTypes(List.of(type));
    }


    // --- The audit pass: every remaining screen, JEI category, item tooltip and chat report, one screenshot each ---

    private static final int AUDIT_STEP = 25;
    private @org.jspecify.annotations.Nullable List<ItemStack> auditItems;

    private void audit(Minecraft minecraft, IntegratedServer server, int t) {
        // Never let a lost window focus pause the game (the pause screen would cover the screenshots and the FPS readings).
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.gui.setScreen(null);
        }
        if (t < 0 || t % AUDIT_STEP != 0 && t % AUDIT_STEP != AUDIT_STEP - 2) {
            return;
        }
        int index = t / AUDIT_STEP;
        boolean shoot = t % AUDIT_STEP == AUDIT_STEP - 2;
        Object[][] screens = {
                {"a01_nest_chamber", origin.offset(-1, 0, 5)},
                {"a02_queen_chamber", origin.offset(0, 0, 6)},
                {"a03_nest_chamber_corner", origin.offset(1, 0, 6)},
                {"a04_foraging_site", origin.offset(-1, 0, 6)},
                {"a05_harvesting_site", origin.offset(-6, 0, 6)},
                {"a06_young_terrarium", origin.offset(-6, 0, 5)},
                {"a07_young_storage", origin.offset(-6, 0, 4)},
                {"a08_upgrade_station", origin.offset(6, 0, 3)},
                {"a09_lab_no_queen", origin.offset(-7, 0, 10)},
                {"a10_generator", origin.offset(1, 0, 10)},
        };
        mezz.jei.api.recipe.types.IRecipeType<?>[] jei = {
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.BREEDING, com.antcolonies.compat.jei.AntColoniesJeiPlugin.MUTATION,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.EGGS, com.antcolonies.compat.jei.AntColoniesJeiPlugin.LARVAE,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.FARMING, com.antcolonies.compat.jei.AntColoniesJeiPlugin.FIGHTING,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.TRAIT, com.antcolonies.compat.jei.AntColoniesJeiPlugin.SPECIES_UPGRADE,
        };
        if (index < screens.length) {
            if (shoot) {
                screenshot(minecraft, (String) screens[index][0]);
            } else {
                minecraft.gui.setScreen(null);
                BlockPos pos = (BlockPos) screens[index][1];
                server.execute(() -> open(server, pos));
            }
            return;
        }
        index -= screens.length;
        if (index < jei.length) {
            if (shoot) {
                screenshot(minecraft, String.format(java.util.Locale.ROOT, "b%02d_jei_%s", index + 1, jei[index].getUid().getPath()));
            } else {
                if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
                    minecraft.player.closeContainer();
                }
                jei(jei[index]);
            }
            return;
        }
        index -= jei.length;
        List<ItemStack> items = tooltipItems(server);
        if (index < items.size()) {
            if (shoot) {
                screenshot(minecraft, String.format(java.util.Locale.ROOT, "c%02d_tooltip_%s", index + 1,
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.get(index).getItem()).getPath()));
            } else {
                minecraft.gui.setScreen(new TooltipScreen(items.get(index)));
            }
            return;
        }
        index -= items.size();
        if (index == 0) {
            if (shoot) {
                screenshot(minecraft, "d01_chat_reports");
            } else {
                minecraft.gui.setScreen(null);
                server.execute(() -> chatReports(server));
            }
            return;
        }
        if (index == 1) {
            if (shoot) {
                screenshot(minecraft, "d02_chat_open");
            } else {
                minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
            }
            return;
        }
        // --- Frame rate: 25 busy colonies in view, then the same view without them. ---
        if (index == 2) {
            if (!shoot) {
                minecraft.gui.setScreen(null);
                minecraft.options.enableVsync().set(false);
                minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                minecraft.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
                server.execute(() -> benchmarkGrid(server, true));
                camera(server, 0.5, 6.0, 10.0, 0, 38);
            }
            return;
        }
        // Each step's action runs at its start and its reading 23 ticks later, and every reading gets a step of its own to
        // settle: build (2), read everything (4), surface ants flat (5, read 6), no Terrarium ants (7, read 8), all back and
        // the grid removed (9, read 11).
        if (index == 3 || index == 10) {
            return;
        }
        if (index == 4) {
            if (shoot) {
                fpsWith = minecraft.getFps();
                screenshot(minecraft, "e01_fps_25_colonies");
            }
            return;
        }
        if (index == 5) {
            if (!shoot) {
                com.antcolonies.core.config.AntConfig.ANT_3D_MODELS.set(false);
            }
            return;
        }
        if (index == 6) {
            if (shoot) {
                fpsFlat = minecraft.getFps();
            }
            return;
        }
        if (index == 7) {
            if (!shoot) {
                com.antcolonies.core.config.AntConfig.MAX_VISUAL_ANTS.set(0);
            }
            return;
        }
        if (index == 8) {
            if (shoot) {
                fpsNoAnts = minecraft.getFps();
            }
            return;
        }
        if (index == 9) {
            if (!shoot) {
                com.antcolonies.core.config.AntConfig.MAX_VISUAL_ANTS.set(40);
                com.antcolonies.core.config.AntConfig.ANT_3D_MODELS.set(true);
                server.execute(() -> benchmarkGrid(server, false));
            }
            return;
        }
        if (index == 11) {
            if (shoot) {
                result(String.format(java.util.Locale.ROOT, "fps: 25 colonies %d, 2D surface ants (ant3DModels off) %d, no Terrarium ants %d, view without the colonies %d",
                        fpsWith, fpsFlat, fpsNoAnts, minecraft.getFps()));
            }
            return;
        }
        // A close look at the 2D surface ants (ant3DModels off), from above the main Terrarium.
        if (index == 12) {
            if (shoot) {
                screenshot(minecraft, "e02_2d_surface_ants");
            } else {
                com.antcolonies.core.config.AntConfig.ANT_3D_MODELS.set(false);
                camera(server, 0.5, 2.2 - 1.62, 4.6, 0, 70);
            }
            return;
        }
        if (index == 13) {
            if (!shoot) {
                com.antcolonies.core.config.AntConfig.ANT_3D_MODELS.set(true);
            }
            return;
        }
        if (!shoot) {
            finish(minecraft);
        }
    }


    private int fpsWith;
    private int fpsNoAnts;
    private int fpsFlat;

    /** 25 stage-4 colonies, each with eight chambers, in a 5x5 grid south of the lab (or, with {@code build} false, the same spots cleared). */
    private void benchmarkGrid(IntegratedServer server, boolean build) {
        ServerLevel level = server.overworld();
        for (int gx = 0; gx < 5; gx++) {
            for (int gz = 0; gz < 5; gz++) {
                BlockPos pos = origin.offset(-8 + gx * 4, 0, 15 + gz * 4);
                if (!build) {
                    place(level, pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    for (BlockPos clear : places(pos)) {
                        place(level, clear, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                    continue;
                }
                place(level, pos, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
                ring(level, pos, new StructureKind[] {StructureKind.NEST_CHAMBER, StructureKind.STORAGE, StructureKind.FORAGING_SITE,
                        StructureKind.NEST_CHAMBER});
                TerrariumBlockEntity terrarium = (TerrariumBlockEntity) level.getBlockEntity(pos);
                Colony colony = terrarium.colony();
                colony.setQueen(Genetics.wildQueen(AntColonies.id(gx % 2 == 0 ? "diamond" : "fire"), RandomSource.create(gx * 5L + gz)));
                colony.setPopulation(300, 400, 6000, 600);
                colony.setFood(20000);
                terrarium.rescan(level);
                ColonySimulator.step(terrarium, level, 0.05);
                terrarium.syncNow();
            }
        }
    }

    private List<ItemStack> tooltipItems(IntegratedServer server) {
        if (auditItems != null) {
            return auditItems;
        }
        List<ItemStack> list = new java.util.ArrayList<>();
        TerrariumBlockEntity terrarium = (TerrariumBlockEntity) server.overworld().getBlockEntity(origin.offset(0, 0, 5));
        ItemStack queen = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        queen.set(ModDataComponents.QUEEN.get(), terrarium.colony().queen());
        list.add(queen);
        ItemStack sample = new ItemStack(ModItems.GENETIC_SAMPLE.get());
        sample.set(ModDataComponents.QUEEN.get(), terrarium.colony().queen());
        list.add(sample);
        ItemStack ants = new ItemStack(ModItems.ANT_TEST_TUBE.get());
        ants.set(ModDataComponents.ANTS.get(), new com.antcolonies.content.item.AntSample(AntColonies.id("wood"), Caste.SOLDIER, 12));
        list.add(ants);
        ItemStack egg = new ItemStack(ModItems.ANT_EGG.get(), 8);
        egg.set(ModDataComponents.SPECIES.get(), AntColonies.id("netherite"));
        list.add(egg);
        ItemStack upgrade = new ItemStack(ModItems.SPECIES_UPGRADE.get());
        upgrade.set(ModDataComponents.SPECIES_UPGRADE.get(), new com.antcolonies.content.item.SpeciesUpgrade(AntColonies.id("diamond"),
                com.antcolonies.colony.upgrade.SpeciesUpgradeKind.EXTRACTION, 2));
        list.add(upgrade);
        list.add(new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 3)));
        list.add(new ItemStack(ModItems.TERRARIUM.get()));
        list.add(new ItemStack(ModItems.TWEEZERS.get()));
        list.add(new ItemStack(ModItems.EVOLUTION_CATALYST.get()));
        list.add(new ItemStack(ModItems.CRYSTAL_ANT_FOOD.get()));
        list.add(new ItemStack(ModItems.GENETIC_LAB.get()));
        list.add(new ItemStack(ModBlocks.STRUCTURES.get(StructureKind.QUEEN_CHAMBER).get()));
        list.add(new ItemStack(ModItems.GENERATOR.get()));
        ItemStack guard = new ItemStack(ModItems.GUARD_ANT.get());
        guard.set(ModDataComponents.GUARD.get(), new com.antcolonies.content.item.GuardData(AntColonies.id("netherite"), 8, 41));
        list.add(guard);
        list.add(new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        list.add(new ItemStack(ModItems.RALLY_PHEROMONE.get()));
        auditItems = list;
        return list;
    }

    /** The Colony Scanner on the Terrarium and on a job site, then the Genetic Analyzer on the Terrarium. */
    private void chatReports(IntegratedServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        use(player, new ItemStack(ModItems.COLONY_SCANNER.get()), origin.offset(0, 0, 5));
        use(player, new ItemStack(ModItems.COLONY_SCANNER.get()), origin.offset(-1, 0, 4));
        use(player, new ItemStack(ModItems.GENETIC_ANALYZER.get()), origin.offset(0, 0, 5));
    }

    private static void use(ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
        stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
    }

    /** A bare screen showing one item and its tooltip, as hovering it in an inventory would. */
    private static final class TooltipScreen extends net.minecraft.client.gui.screens.Screen {
        private final ItemStack stack;

        TooltipScreen(ItemStack stack) {
            super(net.minecraft.network.chat.Component.empty());
            this.stack = stack;
        }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractRenderState(graphics, mouseX, mouseY, a);
            graphics.item(stack, 20, 20);
            graphics.setTooltipForNextFrame(font, stack, 36, 30);
        }
    }

    private void report(IntegratedServer server) {
        ServerLevel level = server.overworld();
        if (level.getBlockEntity(origin.offset(0, 0, 5)) instanceof TerrariumBlockEntity terrarium) {
            Colony colony = terrarium.colony();
            result("diamond colony: stage " + colony.stage() + ", workers " + colony.workers() + ", soldiers " + colony.soldiers()
                    + ", food " + (long) colony.food() + ", status " + com.antcolonies.colony.model.ColonyStatus.of(colony.status())
                    + ", jobs " + colony.assignments().size() + ", finds/min " + colony.stats().finds());
        }
        if (level.getBlockEntity(origin.offset(0, 0, 4)) instanceof StructureBlockEntity pile) {
            int total = 0;
            for (int slot = 0; slot < pile.getContainerSize(); slot++) {
                total += pile.getItem(slot).getCount();
            }
            result("storage items: " + total);
        }
        result("done");
    }

    private void screenshot(Minecraft minecraft, String name) {
        String file = "showcase-" + name + ".png";
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
        result("screenshot " + file + (minecraft.gui.screen() == null ? "" : " (" + minecraft.gui.screen().getClass().getSimpleName() + ")"));
    }

    private synchronized void result(String line) {
        LOGGER.info("[Showcase] {}", line);
        results.add(line);
    }

    private void finish(Minecraft minecraft) {
        try {
            synchronized (this) {
                Files.write(RESULT, results);
            }
        } catch (IOException e) {
            LOGGER.error("[Showcase] couldn't write the results", e);
        }
        minecraft.stop();
    }
}
