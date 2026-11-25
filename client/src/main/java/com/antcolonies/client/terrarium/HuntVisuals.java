package com.antcolonies.client.terrarium;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antcolonies.colony.simulation.ColonySimulator;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The fight on a Hunting Ground: a small stand-in of a mob the colony hunts, its hunters round it lunging in turn. */
public final class HuntVisuals {
    /** Ticks one mob is fought: it rises in the first RISE of them and falls in the last FALL. */
    public static final int BOUT = 260;
    public static final int RISE = 10;
    public static final int FALL = 20;
    /** The middle of the site, where the mob stands, in pixels. */
    public static final float SPOT = 8;
    /** Hunters lunge one after another: radians of that round a tick. */
    private static final float LUNGE_SPEED = 0.12F;
    /** The most room a mob takes on the site, in blocks, and the largest it is drawn compared to life. */
    private static final float MAX_WIDTH = 4.4F / 16;
    private static final float MAX_HEIGHT = 5.2F / 16;
    private static final float MAX_SCALE = 0.32F;

    private static WeakReference<Level> standInLevel = new WeakReference<>(null);
    private static final Map<EntityType<?>, Optional<Entity>> STAND_INS = new HashMap<>();

    /** A mob of the type to take the drawing state from: made once per world, never added to it. */
    public static @Nullable Entity standIn(Level level, EntityType<?> type) {
        if (standInLevel.get() != level) {
            STAND_INS.clear();
            standInLevel = new WeakReference<>(level);
        }
        return STAND_INS.computeIfAbsent(type, t -> create(level, t)).orElse(null);
    }

    private static Optional<Entity> create(Level level, EntityType<?> type) {
        try {
            // Never spawned, so the checks on what may spawn are skipped: in Peaceful a hostile mob would not be made.
            Entity mob = type.create(level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
            if (mob != null) {
                // An id of its own, as a spawner gives the mob it shows: the game asks for it when drawing.
                mob.setId(-1);
            }
            return Optional.ofNullable(mob);
        } catch (RuntimeException e) {
            // Any mod's mob may be a hunt target: one that can't be made off-world is not drawn.
            return Optional.empty();
        }
    }

    /** What a species' hunters fight in one bout: one of its hunt targets, by their weights. */
    public static EntityType<?> target(@Nullable AntSpecies species, long seed) {
        List<AntSpecies.Combat.Target> targets = species == null || species.combat().targets().isEmpty()
                ? ColonySimulator.DEFAULT_TARGETS : species.combat().targets();
        int total = targets.stream().mapToInt(AntSpecies.Combat.Target::weight).sum();
        int pick = Math.floorMod(Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32)), Math.max(1, total));
        for (AntSpecies.Combat.Target target : targets) {
            pick -= target.weight();
            if (pick < 0) {
                return target.entity().value();
            }
        }
        return targets.getFirst().entity().value();
    }

    /** How large a mob is drawn, so it fits the middle of the site. */
    public static float scale(Entity mob) {
        return Math.min(MAX_SCALE, Math.min(MAX_WIDTH / Math.max(0.1F, mob.getBbWidth()), MAX_HEIGHT / Math.max(0.1F, mob.getBbHeight())));
    }

    /** The side of the mob a hunter stands on, in radians round it. */
    public static float side(int hunter, int hunters) {
        return hunter * Mth.TWO_PI / hunters;
    }

    /** How far a hunter is into its lunge, 0 to 1: they go in turn, and hold back while the mob rises or falls. */
    public static float lunge(float time, float into, int hunter, int hunters) {
        if (into < RISE || into > BOUT - FALL) {
            return 0;
        }
        return Math.max(0, Mth.sin(time * LUNGE_SPEED + side(hunter, hunters)));
    }

    /** The side the mob faces: the hunter lunging at it. It swings between two hunters and turns among more. */
    public static float facing(float time, int hunters) {
        float phase = time * LUNGE_SPEED - Mth.HALF_PI;
        return hunters == 2 ? Mth.PI * (0.5F - 0.5F * Mth.cos(phase)) : -phase;
    }

    private HuntVisuals() {}
}
