package com.antcolonies.gametest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * A function test that starts once its whole working area is ticking. The framework force-loads only the chunks under
 * a test's structure, and {@code minecraft:empty} is one block: near a chunk border, whatever a test placed a few blocks
 * along would stand in a chunk where entities and block entities never tick and entities can't be found, unless a
 * neighbouring test happened to keep it loaded. That made tests fail now and then for no reason of their own. The
 * framework releases the forced chunks when the batch ends.
 *
 * <p>The wait is measured in real time (chunks generate in real time, while a test server's ticks may run far faster
 * or, while it saves, far slower than 20 a second), at most {@link #MAX_WAIT_MILLIS}. The framework counts a test's time
 * from before the wait, so its limit is raised by {@link #WAIT_TICKS}; once started, the test keeps to its own limit.
 */
@EventBusSubscriber(modid = AntColonies.MODID)
final class LoadedAreaTest extends FunctionGameTestInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks along x and z from the test's corner, at least, that are loaded; a test's padding can make it more. */
    private static final int REACH = 16;
    /** The longest a test waits for its chunks before it starts anyway. */
    private static final long MAX_WAIT_MILLIS = 30_000;
    /** Added to the framework's limit for the wait: room for any wait at any tick rate. */
    private static final int WAIT_TICKS = 20_000;
    private static final List<Waiting> WAITING = new ArrayList<>();

    private record Waiting(LoadedAreaTest test, GameTestHelper helper, List<ChunkPos> chunks, long since, boolean[] logged) {}

    /** The test function, for the log. */
    private final ResourceKey<Consumer<GameTestHelper>> name;
    /** The test's own time limit, counted from when it really starts. */
    private final int ownTicks;

    LoadedAreaTest(ResourceKey<Consumer<GameTestHelper>> function, TestData<Holder<TestEnvironmentDefinition<?>>> info) {
        super(function, new TestData<>(info.environment(), info.structure(), info.maxTicks() + WAIT_TICKS, info.setupTicks(),
                info.required(), info.rotation(), info.manualOnly(), info.maxAttempts(), info.requiredSuccesses(), info.skyAccess(), info.padding()));
        this.ownTicks = info.maxTicks();
        this.name = function;
    }

    @Override
    public void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int reach = Math.max(REACH, padding());
        ChunkPos from = ChunkPos.containing(corner);
        ChunkPos to = ChunkPos.containing(corner.offset(reach, 0, reach));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = from.x(); x <= to.x(); x++) {
            for (int z = from.z(); z <= to.z(); z++) {
                level.setChunkForced(x, z, true);
                chunks.add(new ChunkPos(x, z));
            }
        }
        if (allTicking(level, chunks)) {
            start(helper);
        } else {
            WAITING.add(new Waiting(this, helper, chunks, System.currentTimeMillis(), new boolean[1]));
        }
    }

    private static boolean allTicking(ServerLevel level, List<ChunkPos> chunks) {
        return chunks.stream().allMatch(chunk -> level.isPositionEntityTicking(chunk.getWorldPosition()));
    }

    /** Between server ticks, as the framework's lists can't take a test scheduling things while it goes through them. */
    @SubscribeEvent
    static void startWaiting(ServerTickEvent.Pre event) {
        long now = System.currentTimeMillis();
        for (Iterator<Waiting> it = WAITING.iterator(); it.hasNext(); ) {
            Waiting waiting = it.next();
            long waited = now - waiting.since();
            boolean ticking = allTicking(waiting.helper().getLevel(), waiting.chunks());
            if (!ticking && waited > 5_000 && !waiting.logged()[0]) {
                waiting.logged()[0] = true;
                LOGGER.warn("[LoadedAreaTest] {} has waited {} ms for its chunks to tick", waiting.test().name.identifier(), waited);
            }
            if (ticking || waited >= MAX_WAIT_MILLIS) {
                if (!ticking) {
                    LOGGER.warn("[LoadedAreaTest] {} starts without all its chunks ticking", waiting.test().name.identifier());
                }
                it.remove();
                waiting.test().start(waiting.helper());
            }
        }
    }

    private void start(GameTestHelper helper) {
        // Its own limit: the framework's is far longer, for the wait.
        helper.runAfterDelay(ownTicks, () -> helper.assertTrue(false, "Timed out after " + ownTicks + " ticks"));
        try {
            super.run(helper);
        } catch (RuntimeException failure) {
            // Outside the framework's tick nothing would catch it, and it would take the server down: hand it over.
            helper.runAfterDelay(0, () -> {
                throw failure;
            });
        }
    }
}
