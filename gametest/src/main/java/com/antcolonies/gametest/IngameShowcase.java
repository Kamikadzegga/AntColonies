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
import net.minecraft.world.item.Item;
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
        if (Files.exists(MARKER) || Files.exists(GUI_MARKER) || Files.exists(UPDATE_MARKER) || Files.exists(STRESS_MARKER) || Files.exists(MACHINE_MARKER)) {
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
            if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
                minecraft.setScreen(new TitleScreen());
            }
            if (!(minecraft.screen instanceof TitleScreen)) {
                return;
            }
            minecraft.options.pauseOnLostFocus = false;
        }
        configTicks++;
        switch (configTicks) {
            case 10 -> {
                var container = net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow();
                minecraft.setScreen(net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.getModInfo())
                        .map(factory -> factory.createScreen(container, new TitleScreen())).orElse(null));
                result("config screen: " + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName()));
            }
            case 30 -> {
                screenshot(minecraft, "32_config_screen");
                clickButton(minecraft, "Client Settings");
            }
            case 50 -> screenshot(minecraft, "33_config_client");
            case 52 -> {
                // The tooltip of the first option, as a player sees it on hover.
                var widget = findWidget(minecraft.screen.children(), "3D ant models");
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
            {"foraging_site_soldiers", -1, 0, 6}, {"nest_chamber_west", -1, 0, 5}, {"hunting_ground", -5, 0, 5}, {"young_terrarium", -6, 0, 5},
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
        ((StructureBlockEntity) level.getBlockEntity(origin.offset(1, 0, 6))).upgrades()
                .setItem(1, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.POPULATION, 2)));
        // Three chambers with no Terrarium, apart from each other: their screens say they aren't part of a colony.
        structure(level, origin.offset(3, 0, -4), StructureKind.NEST_CHAMBER);
        structure(level, origin.offset(5, 0, -4), StructureKind.QUEEN_CHAMBER);
        structure(level, origin.offset(3, 0, -6), StructureKind.FORAGING_SITE);
    }

    // ------------------------------------------------------------------ Stress test (marker "stress-test")

    /**
     * A file named {@code stress-test} in the run folder: 30 seconds of an empty world for a baseline, then 100 big
     * colonies (2000 workers and a full ring of eight chambers each: Foraging or Harvesting Sites, Storage, Nest and Queen
     * Chambers and a Colony Connector, 4 channels, all in range of many others) and 100 wild ants; 20 seconds to settle,
     * then five minutes measured: the server's tick time and the client's frames per second, once a second. The results go
     * to {@code ingame-showcase-result.txt}. While it runs, frames are not capped (no vsync, no limit, no slowing down in
     * the background); the player's own settings are put back before it quits.
     */
    private static final Path STRESS_MARKER = FMLPaths.GAMEDIR.get().resolve("stress-test");
    private final boolean stressTest = Files.exists(STRESS_MARKER);
    private static final int STRESS_BASELINE_END = 700;
    private static final int STRESS_MEASURE_START = 1100;
    private static final int STRESS_MEASURE_END = STRESS_MEASURE_START + 5 * 60 * 20;
    private final List<double[]> baseline = new ArrayList<>();
    private final List<double[]> loaded = new ArrayList<>();
    private Object[] savedOptions;

    private void stressStep(Minecraft minecraft, IntegratedServer server) {
        if (ticks == 60) {
            origin = minecraft.player.blockPosition();
            minecraft.options.pauseOnLostFocus = false;
            if (!minecraft.options.hideGui) {
                minecraft.options.hideGui = !minecraft.options.hideGui;
            }
            savedOptions = new Object[] {minecraft.options.enableVsync().get(), minecraft.options.framerateLimit().get(),
                    minecraft.options.inactivityFpsLimit().get()};
            minecraft.options.enableVsync().set(false);
            minecraft.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
            minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            result("stress: render distance " + minecraft.options.getEffectiveRenderDistance() + ", simulation distance "
                    + minecraft.options.simulationDistance().get() + ", window " + minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
            server.execute(() -> {
                command(server, "difficulty peaceful");
                command(server, "time set noon");
            });
            camera(server, 28.5, 14 - 1.62, -6.0, 0, 32);
            return;
        }
        if (ticks > 100 && ticks <= STRESS_BASELINE_END && ticks % 20 == 0) {
            baseline.add(sample(minecraft, server));
        }
        if (ticks == STRESS_BASELINE_END) {
            screenshot(minecraft, "stress_0_empty");
            server.execute(() -> {
                long start = System.nanoTime();
                buildStress(server);
                result(String.format(java.util.Locale.ROOT, "stress: built in %.0f ms", (System.nanoTime() - start) / 1e6));
            });
        }
        if (ticks > STRESS_MEASURE_START && ticks <= STRESS_MEASURE_END && ticks % 20 == 0) {
            loaded.add(sample(minecraft, server));
            if ((ticks - STRESS_MEASURE_START) % 1200 == 0) {
                double[] s = loaded.getLast();
                result(String.format(java.util.Locale.ROOT, "stress: minute %d: tick %.2f ms (worst %.2f), %d fps",
                        (ticks - STRESS_MEASURE_START) / 1200, s[0], s[1], (int) s[2]));
            }
        }
        if (ticks == STRESS_MEASURE_START + 100) {
            screenshot(minecraft, "stress_1_start");
        } else if (ticks == STRESS_MEASURE_START + 3000) {
            screenshot(minecraft, "stress_2_middle");
        } else if (ticks == STRESS_MEASURE_END - 20) {
            screenshot(minecraft, "stress_3_end");
        }
        if (ticks == STRESS_MEASURE_END + 1) {
            // Block entities can only be read on the server's own thread: count there, and wait for it.
            server.submit(() -> stressCount(server)).join();
        }
        if (ticks == STRESS_MEASURE_END + 2) {
            finishStress(minecraft);
        }
    }

    private void stressCount(IntegratedServer server) {
        {
            ServerLevel level = server.overworld();
            int colonies = 0;
            double workers = 0;
            long found = 0;
            for (int gx = 0; gx < 10; gx++) {
                for (int gz = 0; gz < 10; gz++) {
                    if (level.getBlockEntity(stressPos(gx, gz)) instanceof TerrariumBlockEntity t) {
                        colonies++;
                        workers += t.colony().workers();
                        found += t.colony().totalFound();
                    }
                }
            }
            int ants = level.getEntitiesOfClass(com.antcolonies.content.entity.WildAnt.class, new net.minecraft.world.phys.AABB(origin).inflate(120)).size();
            result(String.format(java.util.Locale.ROOT, "stress: after 5 minutes: %d colonies, %.0f workers, %d finds in all, %d wild ants",
                    colonies, workers, found, ants));
        }
    }

    private void finishStress(Minecraft minecraft) {
        {
            result("stress: baseline (empty world, " + baseline.size() + " s): " + summary(baseline));
            result("stress: loaded (5 minutes, " + loaded.size() + " s): " + summary(loaded));
            minecraft.options.enableVsync().set((Boolean) savedOptions[0]);
            minecraft.options.framerateLimit().set((Integer) savedOptions[1]);
            minecraft.options.inactivityFpsLimit().set((net.minecraft.client.InactivityFpsLimit) savedOptions[2]);
            finish(minecraft);
        }
    }

    private BlockPos stressPos(int gx, int gz) {
        return origin.offset(6 + gx * 5, 0, 6 + gz * 5);
    }

    /** One second's sample: the server's average tick (last 100 ticks), its worst tick (last 100), the frames per second. */
    private static double[] sample(Minecraft minecraft, IntegratedServer server) {
        long worst = 0;
        for (long nanos : server.getTickTimesNanos()) {
            worst = Math.max(worst, nanos);
        }
        return new double[] {server.getAverageTickTimeNanos() / 1e6, worst / 1e6, minecraft.getFps()};
    }

    private static String summary(List<double[]> samples) {
        double tick = 0;
        double worst = 0;
        double fps = 0;
        double minFps = Double.MAX_VALUE;
        List<Double> ticks = new ArrayList<>();
        for (double[] s : samples) {
            tick += s[0];
            worst = Math.max(worst, s[1]);
            fps += s[2];
            minFps = Math.min(minFps, s[2]);
            ticks.add(s[0]);
        }
        int n = Math.max(1, samples.size());
        ticks.sort(Double::compare);
        double p95 = ticks.isEmpty() ? 0 : ticks.get(Math.min(ticks.size() - 1, (int) Math.floor(ticks.size() * 0.95)));
        return String.format(java.util.Locale.ROOT, "server tick %.2f ms average (95th percentile %.2f ms, worst single tick %.2f ms); %.0f fps average, %.0f lowest",
                tick / n, p95, worst, fps / n, minFps == Double.MAX_VALUE ? 0 : minFps);
    }

    /** 100 big colonies in a 10 x 10 grid, every one with a Colony Connector, and 100 wild ants among them. */
    private void buildStress(IntegratedServer server) {
        ServerLevel level = server.overworld();
        StructureKind[] mine = {StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.NEST_CHAMBER,
                StructureKind.FORAGING_SITE, StructureKind.NEST_CHAMBER, StructureKind.QUEEN_CHAMBER};
        StructureKind[] farm = {StructureKind.HARVESTING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.NEST_CHAMBER,
                StructureKind.HARVESTING_SITE, StructureKind.NEST_CHAMBER, StructureKind.QUEEN_CHAMBER};
        int i = 0;
        for (int gx = 0; gx < 10; gx++) {
            for (int gz = 0; gz < 10; gz++) {
                BlockPos pos = stressPos(gx, gz);
                boolean farmer = i % 2 == 0;
                colony(level, pos, farmer ? "farmer" : "stone", 2000, 20000);
                List<BlockPos> places = places(pos);
                StructureKind[] kinds = farmer ? farm : mine;
                for (int p = 0; p < kinds.length; p++) {
                    structure(level, places.get(p), kinds[p]);
                }
                connector(level, places.get(7), i % 4, farmer ? com.antcolonies.content.blockentity.ConnectorSettings.SUPPLY
                        : com.antcolonies.content.blockentity.ConnectorSettings.REQUEST, 40, 75, 0, ItemStack.EMPTY);
                i++;
            }
        }
        RandomSource random = RandomSource.create(99);
        for (int n = 0; n < 100; n++) {
            var ant = com.antcolonies.core.registry.ModEntities.WILD_ANT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            BlockPos at = origin.offset(4 + random.nextInt(50), 0, 4 + random.nextInt(50));
            ant.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
            level.addFreshEntity(ant);
        }
    }

    // ------------------------------------------------------------------ Machine look check (marker "machine-check")

    /**
     * A file named {@code machine-check} in the run folder: a Generator and a Genetic Laboratory, each idle and running,
     * side by side (ticking frozen, so they keep their looks), photographed from the front, at an angle and from above.
     */
    private static final Path MACHINE_MARKER = FMLPaths.GAMEDIR.get().resolve("machine-check");
    private final boolean machineCheck = Files.exists(MACHINE_MARKER);

    private void machineStep(Minecraft minecraft, IntegratedServer server) {
        switch (ticks) {
            case 60 -> {
                origin = minecraft.player.blockPosition();
                minecraft.options.pauseOnLostFocus = false;
                if (!minecraft.options.hideGui) {
                    minecraft.options.hideGui = !minecraft.options.hideGui;
                }
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    command(server, "time set noon");
                    var generator = ModBlocks.GENERATOR.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
                    var lab = ModBlocks.GENETIC_LAB.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
                    place(level, origin.offset(3, 0, 4), generator);
                    place(level, origin.offset(5, 0, 4), generator.setValue(com.antcolonies.content.block.GeneratorBlock.LIT, true));
                    place(level, origin.offset(8, 0, 4), lab);
                    place(level, origin.offset(10, 0, 4), lab.setValue(com.antcolonies.content.block.GeneticLabBlock.WORKING, true));
                    command(server, "tick freeze");
                });
            }
            case 80 -> camera(server, 6.9, 2.3 - 1.62, 0.6, 0, 24);
            case 120 -> screenshot(minecraft, "m1_machines_front");
            case 125 -> camera(server, 13.2, 2.6 - 1.62, 1.4, 52, 26);
            case 165 -> screenshot(minecraft, "m2_machines_angle");
            case 170 -> camera(server, 0.4, 2.6 - 1.62, 7.6, -128, 26);
            case 210 -> screenshot(minecraft, "m3_machines_back");
            case 215 -> camera(server, 7.0, 5.2 - 1.62, 4.5, 0, 90);
            case 255 -> screenshot(minecraft, "m4_machines_top");
            case 260 -> camera(server, 7.0, 1.7 - 1.62, 2.4, 0, 8);
            case 300 -> screenshot(minecraft, "m5_machines_close");
            case 305 -> {
                server.execute(() -> command(server, "tick unfreeze"));
                finish(minecraft);
            }
            default -> { }
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
                startWorld(minecraft);
                return;
            }
            ticks++;
            if (machineCheck) {
                machineStep(minecraft, minecraft.getSingleplayerServer());
            } else if (stressTest) {
                stressStep(minecraft, minecraft.getSingleplayerServer());
            } else if (updateCheck) {
                updateStep(minecraft, minecraft.getSingleplayerServer());
            } else if (guiCheck) {
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
        if (!worldRequested && minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
            result("skipped the mod loading warnings screen");
            minecraft.setScreen(new TitleScreen());
            return;
        }
        if (minecraft.screen instanceof TitleScreen && !worldRequested) {
            worldRequested = true;
            CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(new TitleScreen()));
        } else if (minecraft.screen instanceof CreateWorldScreen create && !createRequested) {
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


    // ------------------------------------------------------------------ The colony update's own check (marker "update-check")

    /** A file named {@code update-check} in the run folder: build the update's demo, screenshot its screens and blocks, quit. */
    private static final Path UPDATE_MARKER = FMLPaths.GAMEDIR.get().resolve("update-check");
    private final boolean updateCheck = Files.exists(UPDATE_MARKER);
    /** Screens of the update check: picture name, block offset from the origin, Terrarium page (or -1), Detailed Mode. */
    private static final Object[][] UPDATE_SCREENS = {
            {"u01_overview_visual", 0, 0, 5, 0, false}, {"u02_overview_detailed", 0, 0, 5, 0, true},
            {"u03_colony_visual", 0, 0, 5, 1, false}, {"u04_colony_detailed", 0, 0, 5, 1, true},
            {"u05_jobs_visual", 0, 0, 5, 2, false}, {"u06_jobs_detailed", 0, 0, 5, 2, true},
            {"u07_production_visual", 0, 0, 5, 3, false}, {"u08_production_detailed", 0, 0, 5, 3, true},
            {"u09_genetics_visual", 0, 0, 5, 4, false}, {"u10_genetics_detailed", 0, 0, 5, 4, true},
            {"u11_network_visual", 8, 0, 5, 5, false}, {"u12_network_detailed", 8, 0, 5, 5, true},
            {"u13_statistics_visual", 0, 0, 5, 6, false}, {"u14_statistics_detailed", 0, 0, 5, 6, true},
            {"u15_info", 0, 0, 5, 7, false},
            {"u16_founding_feeding", 4, 0, 12, 0, false}, {"u17_founding_detailed", 4, 0, 12, 0, true},
            {"u18_empty_network", 0, 0, 5, 5, false},
            {"u19_connector_supply", 9, 0, 5, -1, false}, {"u20_connector_request", 11, 0, 5, -1, false},
            {"u21_foraging_site_modes", -1, 0, 4, -1, false}, {"u22_nether_site", 16, 0, 6, -1, false},
            {"u23_end_site", 20, 0, 6, -1, false}, {"u24_end_site_wrong_ants", 24, 0, 6, -1, false},
            {"u25_hunting_ground_settings", -5, 0, 5, -1, false}};

    private void updateStep(Minecraft minecraft, IntegratedServer server) {
        if (ticks == 60) {
            origin = minecraft.player.blockPosition();
            minecraft.options.pauseOnLostFocus = false;
            if (!minecraft.options.hideGui) {
                minecraft.options.hideGui = !minecraft.options.hideGui;
            }
            server.execute(() -> {
                command(server, "difficulty peaceful");
                build(server);
                buildUpdate(server);
            });
            return;
        }
        if (ticks < 160 || origin == null) {
            return;
        }
        int t = ticks - 160;
        int index = t / 30;
        int phase = t % 30;
        if (index < UPDATE_SCREENS.length) {
            Object[] target = UPDATE_SCREENS[index];
            BlockPos pos = origin.offset((int) target[1], (int) target[2], (int) target[3]);
            int page = (int) target[4];
            if (phase == 0) {
                com.antcolonies.client.screen.TerrariumScreen.showDetailed((boolean) target[5]);
                if (page >= 0) {
                    com.antcolonies.client.screen.TerrariumScreen.showPage(page);
                }
                server.execute(() -> {
                    command(server, String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f", pos.getX() + 0.5, pos.getY(), pos.getZ() - 2.5));
                    open(server, pos);
                });
            } else if (phase == 26) {
                screenshot(minecraft, (String) target[0]);
            } else if (phase == 28) {
                minecraft.setScreen(null);
            }
            return;
        }
        int w = t - UPDATE_SCREENS.length * 30;
        switch (w) {
            case 0 -> {
                com.antcolonies.client.screen.TerrariumScreen.showDetailed(false);
                camera(server, 18.5, 2.4 - 1.62, 2.6, 0, 30);
            }
            case 40 -> screenshot(minecraft, "u30_expedition_sites");
            case 45 -> camera(server, 10, 2.6 - 1.62, 3.0, 0, 35);
            case 85 -> screenshot(minecraft, "u31_connected_colonies");
            case 90 -> camera(server, -0.5, 3.4 - 1.62, -6.5, 180, 22);
            case 130 -> screenshot(minecraft, "u32_building_blocks");
            case 135 -> camera(server, -8.5, 3.4 - 1.62, -6.5, 180, 22);
            case 175 -> screenshot(minecraft, "u33_building_blocks_left");
            case 180 -> camera(server, 8.5, 3.4 - 1.62, -6.5, 180, 22);
            case 220 -> screenshot(minecraft, "u34_building_blocks_right");
            case 225 -> jei(com.antcolonies.compat.jei.AntColoniesJeiPlugin.EXPEDITION);
            case 250 -> screenshot(minecraft, "u35_jei_expedition");
            case 255 -> emi(minecraft, "expedition");
            case 285 -> screenshot(minecraft, "u35b_emi_expedition");
            case 290 -> {
                minecraft.setScreen(null);
                look(server, 9.5, 1.0, 3.2, 0, 34);
            }
            case 320 -> screenshot(minecraft, "u36_jade_connector");
            case 325 -> look(server, 16.5, 1.0, 4.3, 0, 42);
            case 355 -> screenshot(minecraft, "u37_jade_nether_site");
            case 360 -> camera(server, 32.5, 1.3 - 1.62, 2.4, 0, 10);
            case 400 -> screenshot(minecraft, "u38_chitin_tiers");
            case 405 -> camera(server, 32.5, 1.3 - 1.62, 10.0, 180, 10);
            case 445 -> screenshot(minecraft, "u39_chitin_tiers_back");
            case 450 -> camera(server, 27.6, 1.75 - 1.62, 4.4, -50, 8);
            case 490 -> screenshot(minecraft, "u40_chitin_jaws");
            case 495 -> {
                server.execute(() -> updateReport(server));
                finish(minecraft);
            }
            default -> {
            }
        }
    }

    /**
     * The update's demo next to the usual one: two colonies linked by Colony Connectors (a Farmer colony supplying a Stone
     * colony), a Fire colony at a Nether Foraging Site, a Chorus colony at an End Foraging Site and a Common colony at
     * one (wrong ants), a Queen founding alone, settings on the Diamond colony's sites, half an hour of history, and a
     * gallery of the building blocks behind the player.
     */
    private void buildUpdate(IntegratedServer server) {
        ServerLevel level = server.overworld();
        // The Diamond colony's sites: one looking for minerals at high priority, one turned off.
        StructureBlockEntity mineral = (StructureBlockEntity) level.getBlockEntity(origin.offset(-1, 0, 4));
        mineral.setMode(com.antcolonies.colony.model.ForagingMode.MINERAL);
        mineral.setPriority(StructureBlockEntity.PRIORITY_HIGH);
        if (level.getBlockEntity(origin.offset(1, 0, 4)) instanceof StructureBlockEntity organic && organic.kind() == StructureKind.FORAGING_SITE) {
            organic.setMode(com.antcolonies.colony.model.ForagingMode.ORGANIC);
        }
        if (level.getBlockEntity(origin.offset(-1, 0, 6)) instanceof StructureBlockEntity off && off.kind().job().any()) {
            off.setEnabled(false);
        }
        // Linked colonies: a Farmer colony gives its spare food to a Stone colony.
        TerrariumBlockEntity farm = colony(level, origin.offset(8, 0, 5), "farmer", 900, 15000);
        structure(level, origin.offset(8, 0, 6), StructureKind.HARVESTING_SITE);
        structure(level, origin.offset(8, 0, 4), StructureKind.STORAGE);
        connector(level, origin.offset(9, 0, 5), 4, com.antcolonies.content.blockentity.ConnectorSettings.SUPPLY, 40, 75,
                com.antcolonies.content.blockentity.ConnectorSettings.SEND, new ItemStack(Items.WHEAT_SEEDS));
        TerrariumBlockEntity mine = colony(level, origin.offset(12, 0, 5), "stone", 1200, 300);
        structure(level, origin.offset(12, 0, 6), StructureKind.FORAGING_SITE);
        structure(level, origin.offset(12, 0, 4), StructureKind.STORAGE);
        connector(level, origin.offset(11, 0, 5), 4, com.antcolonies.content.blockentity.ConnectorSettings.REQUEST, 40, 75,
                com.antcolonies.content.blockentity.ConnectorSettings.RECEIVE, ItemStack.EMPTY);
        // The expedition sites.
        TerrariumBlockEntity fire = colony(level, origin.offset(16, 0, 5), "fire", 1500, 20000);
        StructureBlockEntity nether = structure(level, origin.offset(16, 0, 6), StructureKind.NETHER_FORAGING_SITE);
        structure(level, origin.offset(16, 0, 4), StructureKind.STORAGE);
        TerrariumBlockEntity chorus = colony(level, origin.offset(20, 0, 5), "chorus", 1500, 20000);
        structure(level, origin.offset(20, 0, 6), StructureKind.END_FORAGING_SITE);
        structure(level, origin.offset(20, 0, 4), StructureKind.STORAGE);
        TerrariumBlockEntity wrong = colony(level, origin.offset(24, 0, 5), "common", 500, 5000);
        structure(level, origin.offset(24, 0, 6), StructureKind.END_FORAGING_SITE);
        // A Queen alone: founding, half fed.
        BlockPos alone = origin.offset(4, 0, 12);
        place(level, alone, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        TerrariumBlockEntity founding = (TerrariumBlockEntity) level.getBlockEntity(alone);
        founding.setItem(TerrariumBlockEntity.SLOT_QUEEN, TweezersItemAccess.queenTube(AntColonies.id("blossom")));
        founding.processSlots(level);
        founding.colony().setFood(1200);
        // Run them a while: each simulation step, the connectors' passes, and a history sample (half an hour squeezed in).
        TerrariumBlockEntity diamond = (TerrariumBlockEntity) level.getBlockEntity(origin.offset(0, 0, 5));
        List<TerrariumBlockEntity> all = List.of(diamond, farm, mine, fire, chorus, wrong, founding);
        long time = level.getGameTime();
        RandomSource random = RandomSource.create(12);
        for (int i = 0; i < 40; i++) {
            for (TerrariumBlockEntity terrarium : all) {
                terrarium.rescan(level);
                ColonySimulator.step(terrarium, level, 0.5);
                if (terrarium != founding) {
                    terrarium.colony().setFood(Math.max(terrarium.colony().food(), terrarium == mine ? 0 : 400));
                }
            }
            if (i % 3 == 0) {
                diamond.colony().setFood(diamond.colony().food() * (0.85 + random.nextDouble() * 0.2));
            }
            for (int pass = 0; pass < 30; pass++) {
                com.antcolonies.colony.network.ConnectorNetwork.tick(level);
            }
            for (TerrariumBlockEntity terrarium : all) {
                terrarium.colony().history().sample(time + i * com.antcolonies.colony.model.ColonyHistory.INTERVAL, terrarium.colony());
            }
        }
        all.forEach(TerrariumBlockEntity::syncNow);
        result("nether site: " + nether.assigned() + " ants, status " + com.antcolonies.colony.model.ColonyStatus.of(nether.siteStatus())
                + "; network links " + farm.colony().network().size() + "; founding " + founding.colony().founding());
        // The building blocks, in rows behind the player (north): blocks, then stairs, slabs and walls.
        BlockPos gallery = origin.offset(-12, 0, -13);
        int x = 0;
        for (var entry : com.antcolonies.core.registry.ModDecor.ALL.entrySet()) {
            BlockPos pos = gallery.offset(x, 0, 0);
            BlockState state = entry.getValue().get().defaultBlockState();
            if (entry.getKey().equals("nest_door")) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                level.setBlock(pos.above(), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                if (!entry.getKey().endsWith("cluster") && !entry.getKey().endsWith("trapdoor")) {
                    level.setBlock(pos.above(), state, Block.UPDATE_ALL);
                }
            }
            x++;
        }
        int sx = 0;
        for (String id : com.antcolonies.core.registry.ModDecor.WITH_STAIRS) {
            String stem = com.antcolonies.core.registry.ModDecor.stem(id);
            BlockPos row = gallery.offset(sx, 0, -2);
            level.setBlock(row, block(stem + "_stairs").defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(row.offset(1, 0, 0), block(stem + "_slab").defaultBlockState(), Block.UPDATE_ALL);
            if (com.antcolonies.core.registry.ModDecor.WITH_WALLS.contains(id)) {
                level.setBlock(row.offset(2, 0, 0), block(stem + "_wall").defaultBlockState(), Block.UPDATE_ALL);
            }
            sx += 3;
        }
        // The four chitin tiers on armor stands, a sword in each hand-side and a pickaxe in the other.
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        com.antcolonies.content.item.ChitinGear.Tier[] tiers = com.antcolonies.content.item.ChitinGear.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            BlockPos spot = origin.offset(29 + i * 2, 0, 6);
            var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            stand.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180, 0);
            stand.setYBodyRot(180);
            stand.setYHeadRot(180);
            for (int p = 0; p < 4; p++) {
                stand.setItemSlot(slots[p], new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        AntColonies.id(tiers[i].id(ModItems.GEAR_PIECES[p])))));
            }
            stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(AntColonies.id(tiers[i].id("pickaxe")))));
            stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(AntColonies.id(tiers[i].id("sword")))));
            stand.setShowArms(true);
            level.addFreshEntity(stand);
        }
    }

    private static Block block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(AntColonies.id(id));
    }

    private TerrariumBlockEntity colony(ServerLevel level, BlockPos pos, String species, double workers, double food) {
        place(level, pos, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        TerrariumBlockEntity terrarium = (TerrariumBlockEntity) level.getBlockEntity(pos);
        terrarium.colony().setQueen(Genetics.wildQueen(AntColonies.id(species), RandomSource.create(species.hashCode())));
        terrarium.colony().setPopulation(20, 30, workers);
        terrarium.colony().setFood(food);
        return terrarium;
    }

    private static void connector(ServerLevel level, BlockPos pos, int channel, int foodMode, int reserve, int target, int itemMode, ItemStack filter) {
        StructureBlockEntity connector = structure(level, pos, StructureKind.COLONY_CONNECTOR);
        var settings = connector.connector();
        settings.channel = channel;
        settings.foodMode = foodMode;
        settings.reserve = reserve;
        settings.target = target;
        settings.itemMode = itemMode;
        if (!filter.isEmpty()) {
            connector.setItem(0, filter);
        }
        connector.connectorChanged();
    }

    /** Queen tubes for the demo (TweezersItem.queenTube with a fixed seed). */
    private static final class TweezersItemAccess {
        static ItemStack queenTube(net.minecraft.resources.Identifier species) {
            return com.antcolonies.content.item.TweezersItem.queenTube(Genetics.wildQueen(species, RandomSource.create(21)));
        }
    }

    private void updateReport(IntegratedServer server) {
        ServerLevel level = server.overworld();
        for (int[] offset : new int[][] {{0, 0, 5}, {8, 0, 5}, {12, 0, 5}, {16, 0, 5}, {20, 0, 5}, {24, 0, 5}, {4, 0, 12}}) {
            if (level.getBlockEntity(origin.offset(offset[0], offset[1], offset[2])) instanceof TerrariumBlockEntity terrarium) {
                Colony colony = terrarium.colony();
                result(colony.species().getPath() + ": workers " + colony.workers() + ", food " + (long) colony.food() + ", founding " + colony.founding()
                        + ", status " + com.antcolonies.colony.model.ColonyStatus.of(colony.status()) + ", history " + colony.history().count()
                        + ", network " + colony.network().size());
            }
        }
        result("done");
    }
    private void step(Minecraft minecraft, IntegratedServer server) {
        switch (ticks) {
            case 60 -> {
                origin = minecraft.player.blockPosition();
                // No pause menu when the window loses focus, and no slimes in the pictures.
                minecraft.options.pauseOnLostFocus = false;
                server.execute(() -> command(server, "difficulty peaceful"));
                if (!minecraft.options.hideGui) {
                    minecraft.options.hideGui = !minecraft.options.hideGui;
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
                if (minecraft.options.hideGui) {
                    minecraft.options.hideGui = !minecraft.options.hideGui;
                }
                server.execute(() -> open(server, origin.offset(0, 0, 5)));
            }
            case 510 -> screenshot(minecraft, "09_gui_overview");
            case 512 -> clickTab(minecraft, 4);
            case 530 -> screenshot(minecraft, "10_gui_genetics");
            case 532 -> clickTab(minecraft, 2);
            case 550 -> screenshot(minecraft, "11_gui_jobs");
            case 552 -> clickTab(minecraft, 3);
            case 570 -> screenshot(minecraft, "12_gui_stats");
            case 571 -> clickTab(minecraft, 7);
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
                minecraft.setScreen(null);
                server.execute(() -> open(server, origin.offset(0, 0, 4)));
            }
            case 870 -> screenshot(minecraft, "20_gui_storage");
            case 875 -> {
                minecraft.player.closeContainer();
                minecraft.setScreen(new com.antcolonies.client.screen.IdentificationBookScreen());
            }
            case 893 -> screenshot(minecraft, "21_book");
            case 894 -> {
                // The second species in the book's list (after its how-to-play pages).
                if (minecraft.screen instanceof com.antcolonies.client.screen.IdentificationBookScreen book) {
                    int left = (book.width - 330) / 2;
                    int top = (book.height - 196) / 2;
                    book.mouseClicked(new MouseButtonEvent(left + 30, top + 9 + 10 * 10 + 4, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                }
            }
            case 898 -> screenshot(minecraft, "21b_book_species");
            // Jade look-at tooltips (when Jade is in run/mods): the crosshair on a Terrarium, a job site and the lab.
            case 900 -> {
                minecraft.setScreen(null);
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
            case 1108 -> minecraft.setScreen(null);
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
            // Close to the stand's head, from the front and a little to the side: the helmet's antennae and eyes.
            case 1251 -> camera(server, -6.0, 0.15, -4.2, 41, 5);
            case 1254 -> screenshot(minecraft, "30b_chitin_helmet");
            case 1255 -> {
                if (minecraft.options.hideGui) {
                    minecraft.options.hideGui = !minecraft.options.hideGui;
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
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(ModItems.CHITIN_SHIELD.get()));
                });
            }
            case 1275 -> minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
            case 1295 -> screenshot(minecraft, "31_chitin_items");
            case 1298 -> minecraft.setScreen(null);
            // The Config button in the Mods list: NeoForge's configuration screen, then its client page.
            case 1300 -> minecraft.setScreen(net.neoforged.neoforge.client.gui.IConfigScreenFactory
                    .getForMod(net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow().getModInfo())
                    .map(factory -> factory.createScreen(net.neoforged.fml.ModList.get().getModContainerById(AntColonies.MODID).orElseThrow(), null))
                    .orElse(null));
            case 1320 -> {
                screenshot(minecraft, "32_config_screen");
                clickButton(minecraft, "Client Settings");
            }
            case 1340 -> screenshot(minecraft, "33_config_client");
            case 1343 -> minecraft.setScreen(null);
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
        colony.setPopulation(300, 420, 6840);
        colony.setFood(20000);
        colony.addFood(0, 4000);
        diamond.rescan(level);
        for (int i = 0; i < 4; i++) {
            ColonySimulator.step(diamond, level, 0.05);
        }
        diamond.syncNow();
        // A young Army Ant colony (hunters) with a smaller build: a chamber on each side of its Terrarium, one a Hunting Ground.
        BlockPos young = origin.offset(-6, 0, 5);
        place(level, young, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        structure(level, young.north(), StructureKind.STORAGE);
        structure(level, young.east(), StructureKind.HUNTING_GROUND);
        structure(level, young.south(), StructureKind.FORAGING_SITE);
        structure(level, young.west(), StructureKind.NEST_CHAMBER);
        TerrariumBlockEntity common = (TerrariumBlockEntity) level.getBlockEntity(young);
        common.colony().setQueen(Genetics.wildQueen(AntColonies.id("army"), RandomSource.create(8)));
        common.colony().setPopulation(6, 4, 60);
        common.colony().setFood(800);
        common.rescan(level);
        ColonySimulator.step(common, level, 0.05);
        common.syncNow();
        // A Badlands colony (hot climate: its Info page shows terracotta cycling through its colours) and a Storage with
        // two Storage IV upgrades (162 slots, scrolling), some of its items in the lower rows.
        BlockPos badlands = origin.offset(-12, 0, 5);
        place(level, badlands, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH));
        TerrariumBlockEntity hot = (TerrariumBlockEntity) level.getBlockEntity(badlands);
        hot.colony().setQueen(Genetics.wildQueen(AntColonies.id("badlands"), RandomSource.create(9)));
        hot.colony().setPopulation(6, 4, 60);
        hot.colony().setFood(800);
        hot.rescan(level);
        hot.syncNow();
        StructureBlockEntity big = structure(level, origin.offset(-12, 0, 9), StructureKind.STORAGE);
        big.upgrades().setItem(0, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.STORAGE, 4)));
        big.upgrades().setItem(1, new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.STORAGE, 4)));
        Item[] goods = {Items.COBBLESTONE, Items.IRON_INGOT, Items.DIAMOND, Items.REDSTONE, Items.WHEAT, Items.GOLD_INGOT, Items.EMERALD, Items.COAL};
        for (int slot = 0; slot < 140; slot += 3) {
            Item item = goods[slot % goods.length];
            big.setItem(slot, new ItemStack(item, Math.min(big.slotLimit(new ItemStack(item)), 40 + slot * 3)));
        }
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
        var screen = minecraft.screen;
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
        if (minecraft.screen instanceof com.antcolonies.client.screen.TerrariumScreen) {
            com.antcolonies.client.screen.TerrariumScreen.showPage(tab);
        } else {
            result("FAIL no container screen: " + minecraft.screen);
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
        com.antcolonies.content.worldgen.AnthillPiece piece = new com.antcolonies.content.worldgen.AnthillPiece(center, false);
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
        var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        stand.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180, 0);
        // snapTo turns only the stand's yaw; armor stands are drawn by their body's, which would face away from the camera.
        stand.setYBodyRot(180);
        stand.setYHeadRot(180);
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.CHITIN_HELMET.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        // The shield in the hand on the close-up camera's side, front outward; the sword in the other.
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.CHITIN_SHIELD.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(ModItems.CHITIN_SWORD.get()));
        level.addFreshEntity(stand);
        // A baby zombie in Chitin, for the armor's baby layer (zombies need more than Peaceful; this one does nothing).
        command(server, "difficulty easy");
        var baby = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        baby.setBaby(true);
        baby.setNoAi(true);
        baby.setPersistenceRequired();
        baby.snapTo(spot.getX() + 2.6, spot.getY(), spot.getZ() - 0.4, 180, 0);
        baby.setYBodyRot(180);
        baby.setYHeadRot(180);
        for (var piece : new Object[][] {{net.minecraft.world.entity.EquipmentSlot.HEAD, ModItems.CHITIN_HELMET},
                {net.minecraft.world.entity.EquipmentSlot.CHEST, ModItems.CHITIN_CHESTPLATE}, {net.minecraft.world.entity.EquipmentSlot.LEGS, ModItems.CHITIN_LEGGINGS},
                {net.minecraft.world.entity.EquipmentSlot.FEET, ModItems.CHITIN_BOOTS}}) {
            baby.setItemSlot((net.minecraft.world.entity.EquipmentSlot) piece[0],
                    new ItemStack(((net.neoforged.neoforge.registries.DeferredItem<?>) piece[1]).get()));
        }
        level.addFreshEntity(baby);
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
        if (minecraft.screen == null) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
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
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
        }
        if (t < 0 || t % AUDIT_STEP != 0 && t % AUDIT_STEP != 6 && t % AUDIT_STEP != AUDIT_STEP - 2) {
            return;
        }
        int index = t / AUDIT_STEP;
        boolean shoot = t % AUDIT_STEP == AUDIT_STEP - 2;
        Object[][] screens = {
                {"a01_nest_chamber", origin.offset(-1, 0, 5)},
                {"a02_queen_chamber", origin.offset(0, 0, 6)},
                {"a03_nest_chamber_corner", origin.offset(1, 0, 6)},
                {"a04_foraging_site", origin.offset(-1, 0, 6)},
                {"a05_hunting_ground", origin.offset(-5, 0, 5)},
                {"a06_young_terrarium", origin.offset(-6, 0, 5)},
                {"a07_young_storage", origin.offset(-6, 0, 4)},
                {"a08_upgrade_station", origin.offset(6, 0, 3)},
                {"a09_lab_no_queen", origin.offset(-7, 0, 10)},
                {"a10_generator", origin.offset(1, 0, 10)},
                {"a11_badlands_info", origin.offset(-12, 0, 5)},
                {"a12_big_storage", origin.offset(-12, 0, 9)},
                {"a13_big_storage_scrolled", origin.offset(-12, 0, 9)},
                {"a14_lab_other_trait", origin.offset(0, 0, 10)},
        };
        // A few ticks after opening: the Info page, or scroll the Storage to its end.
        if (t % AUDIT_STEP == 6) {
            if (t / AUDIT_STEP >= screens.length) {
                return;
            }
            String name = (String) screens[t / AUDIT_STEP][0];
            if (name.endsWith("_info")) {
                clickTab(minecraft, 7);
            } else if (name.endsWith("_other_trait") && minecraft.player.containerMenu instanceof com.antcolonies.content.menu.GeneticLabMenu lab) {
                // Select Soldier Strength (trait 5) as the trait list does: the cost card should follow it.
                minecraft.gameMode.handleInventoryButtonClick(lab.containerId, 5);
            } else if (name.endsWith("_scrolled") && minecraft.screen != null) {
                var window = minecraft.getWindow();
                for (int i = 0; i < 20; i++) {
                    minecraft.screen.mouseScrolled(window.getGuiScaledWidth() / 2.0, window.getGuiScaledHeight() / 2.0 - 60, 0, -1);
                }
            }
            return;
        }
        mezz.jei.api.recipe.types.IRecipeType<?>[] jei = {
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.BREEDING, com.antcolonies.compat.jei.AntColoniesJeiPlugin.MUTATION,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.EGGS, com.antcolonies.compat.jei.AntColoniesJeiPlugin.LARVAE,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.FARMING, com.antcolonies.compat.jei.AntColoniesJeiPlugin.FIGHTING,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.TRAIT, com.antcolonies.compat.jei.AntColoniesJeiPlugin.SPECIES_UPGRADE,
                com.antcolonies.compat.jei.AntColoniesJeiPlugin.COLONY_UPGRADE,
        };
        if (index < screens.length) {
            if (shoot) {
                screenshot(minecraft, (String) screens[index][0]);
            } else {
                minecraft.setScreen(null);
                BlockPos pos = (BlockPos) screens[index][1];
                // Stand on the block first: a screen out of reach closes at once.
                server.execute(() -> {
                    command(server, String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5));
                    open(server, pos);
                });
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
                // With EMI installed JEI's recipe screen doesn't open; show the same category in EMI.
                if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                    emi(minecraft, jei[index].getUid().getPath());
                } else {
                    jei(jei[index]);
                }
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
                minecraft.setScreen(new TooltipScreen(items.get(index)));
            }
            return;
        }
        index -= items.size();
        if (index == 0) {
            if (shoot) {
                screenshot(minecraft, "d01_chat_reports");
            } else {
                minecraft.setScreen(null);
                server.execute(() -> chatReports(server));
            }
            return;
        }
        if (index == 1) {
            if (shoot) {
                screenshot(minecraft, "d02_chat_open");
            } else {
                minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
            }
            return;
        }
        // --- Frame rate: 25 busy colonies in view, then the same view without them. ---
        if (index == 2) {
            if (!shoot) {
                minecraft.setScreen(null);
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
                colony.setPopulation(300, 400, 6600);
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
        list.add(new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.STORAGE, 2)));
        list.add(new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.STORAGE, 4)));
        list.add(new ItemStack(ModItems.upgrade(com.antcolonies.colony.upgrade.UpgradeType.SPEED, 4)));
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
            result("diamond colony: stage " + colony.stage() + ", workers " + colony.workers()
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
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), 1, message -> {});
        result("screenshot " + file + (minecraft.screen == null ? "" : " (" + minecraft.screen.getClass().getSimpleName() + ")"));
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
