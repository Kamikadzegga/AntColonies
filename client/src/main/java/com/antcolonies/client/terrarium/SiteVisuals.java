package com.antcolonies.client.terrarium;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.model.StructureKind;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The working ants drawn on a job site in a flat colony: client-only actors that go about that site's work, each in its
 * own time and to spots it picks at random, so no two sites look alike. Positions are in the site model's own pixels,
 * before the block is turned to its facing.
 */
public final class SiteVisuals {
    /** Pixels an ant walks in a tick: one pace for every ant on every site. */
    private static final float SPEED = 0.11F;
    /** An ant walking near another leans away from it by up to this much a tick, px: it goes round, it is not pushed. */
    private static final float LEAN = 0.05F;
    /** How near another ant has to be for that, px (a worker is about 3 long): closer than this they may brush. */
    private static final float NEAR = 2.6F;
    /** A spot is taken when another ant stands on it or heads for it within this, px. */
    private static final float TAKEN = 2.6F;
    private static final float EDGE_MIN = 2.2F;
    private static final float EDGE_MAX = 13.8F;

    public enum Work {
        NONE,
        /** Picking something up off the ground: head down, jaws working. */
        GATHER,
        /** Putting the load down. */
        DROP,
        /** Tending a mushroom: jaws snipping, antennae busy. */
        TEND,
        /** Drinking at the bowl: head down, gaster swelling. */
        FILL,
        /** Through the portal: not on the site at all. */
        AWAY,
        /** A moment's rest: grooming. */
        REST
    }

    public static final class Ant {
        public float x;
        public float z;
        public float prevX;
        public float prevZ;
        public float yaw;
        public float prevYaw;
        public float phase;
        public float prevPhase;
        public float seed;
        public boolean moving;
        public boolean loaded;
        public Work work = Work.NONE;
        /** Ticks left of its stop, and how long the whole stop is. */
        public int pause;
        public int stay;
        float targetX;
        float targetZ;
        /** On its way to where the work is (the litter, the edge, the portal, the bowl), or back with what it got. */
        boolean outbound = true;

        public boolean away() {
            return work == Work.AWAY;
        }
    }

    public final List<Ant> ants = new ArrayList<>();
    private final RandomSource random;
    private StructureKind kind = StructureKind.FORAGING_SITE;

    public SiteVisuals(long seed) {
        this.random = RandomSource.create(seed ^ 0x51A7E5L);
    }

    // --- The spots of each kind of site ---

    /** Foraging Site: beside the dirt, the stick and the bones; the finds go to the pile in the corner. */
    private static final float[][] FORAGING_LITTER = {{6.9F, 6.9F}, {7.0F, 4.4F}, {4.4F, 7.0F}, {11.0F, 5.6F}, {8.0F, 6.0F}, {7.2F, 11.6F}, {4.6F, 9.0F}};
    private static final float[][] FORAGING_PILE = {{9.2F, 9.2F}, {11.8F, 9.2F}, {9.2F, 11.8F}};
    /** Harvesting Site: beside the three mushrooms. */
    private static final float[][] MUSHROOMS = {{5.3F, 6.6F}, {6.4F, 4.2F}, {9.2F, 11.4F}, {11.9F, 8.2F}, {9.8F, 5.8F}, {12.8F, 6.0F}};
    /** Nether and End Foraging Sites: beside the two heaps. */
    private static final float[][] HEAPS = {{5.8F, 9.4F}, {6.2F, 11.4F}, {3.4F, 9.0F}, {11.4F, 10.2F}, {10.2F, 12.2F}};

    /** Where the work of this kind of site is: a spot picked afresh each time. */
    private float[] workSpot() {
        return switch (kind) {
            // A leaf bit from the site's west or south edge.
            case HARVESTING_SITE -> random.nextBoolean() ? new float[] {2.8F, 6 + random.nextFloat() * 7} : new float[] {3 + random.nextFloat() * 6, 13.2F};
            // Into the portal, anywhere across its mouth.
            case NETHER_FORAGING_SITE, END_FORAGING_SITE -> new float[] {5.8F + random.nextFloat() * 4.4F, 4.4F};
            // The bowl's two long sides, under the rack.
            case LIQUID_CHAMBER -> new float[] {5.2F + random.nextFloat() * 5.6F, random.nextBoolean() ? 11.3F : 4.7F};
            // The litter, or now and then a patch of bare ground.
            default -> random.nextInt(3) == 0 ? new float[] {3 + random.nextFloat() * 5.5F, 3 + random.nextFloat() * 10}
                    : jitter(FORAGING_LITTER[random.nextInt(FORAGING_LITTER.length)]);
        };
    }

    /** Where what the ant got is taken. */
    private float[] deliverySpot() {
        return switch (kind) {
            case HARVESTING_SITE -> jitter(MUSHROOMS[random.nextInt(MUSHROOMS.length)]);
            case NETHER_FORAGING_SITE, END_FORAGING_SITE -> jitter(HEAPS[random.nextInt(HEAPS.length)]);
            // Out to the site's edge, the way to the Storage.
            case LIQUID_CHAMBER -> random.nextBoolean() ? new float[] {random.nextBoolean() ? 2.8F : 13.2F, 3 + random.nextFloat() * 10}
                    : new float[] {3 + random.nextFloat() * 10, random.nextBoolean() ? 2.8F : 13.2F};
            default -> jitter(FORAGING_PILE[random.nextInt(FORAGING_PILE.length)]);
        };
    }

    private float[] jitter(float[] spot) {
        return new float[] {spot[0] + (random.nextFloat() - 0.5F) * 0.8F, spot[1] + (random.nextFloat() - 0.5F) * 0.8F};
    }

    private Work workThere() {
        return switch (kind) {
            case NETHER_FORAGING_SITE, END_FORAGING_SITE -> Work.AWAY;
            case LIQUID_CHAMBER -> Work.FILL;
            default -> Work.GATHER;
        };
    }

    /** Ticks an ant spends where the work is: a quick pick-up of a leaf, a long search, a longer trip through the portal. */
    private int workTicks() {
        return switch (kind) {
            case HARVESTING_SITE -> 10 + random.nextInt(12);
            case NETHER_FORAGING_SITE, END_FORAGING_SITE -> 50 + random.nextInt(80);
            default -> 28 + random.nextInt(44);
        };
    }

    // --- Following the site ---

    public void tick(StructureKind site, int count) {
        if (site != kind) {
            kind = site;
            ants.clear();
        }
        while (ants.size() > count) {
            ants.removeLast();
        }
        while (ants.size() < count) {
            ants.add(spawn());
        }
        for (Ant ant : ants) {
            step(ant);
        }
        leanAway();
    }

    private Ant spawn() {
        Ant ant = new Ant();
        ant.seed = random.nextFloat() * 10;
        float[] start = deliverySpot();
        ant.x = ant.prevX = start[0];
        ant.z = ant.prevZ = start[1];
        ant.yaw = ant.prevYaw = random.nextFloat() * Mth.TWO_PI;
        // They set off one after another, not all at once.
        ant.work = Work.REST;
        ant.pause = ant.stay = 1 + random.nextInt(90);
        return ant;
    }

    private void step(Ant ant) {
        ant.prevX = ant.x;
        ant.prevZ = ant.z;
        ant.prevYaw = ant.yaw;
        ant.prevPhase = ant.phase;
        if (ant.pause > 0) {
            ant.pause--;
            ant.moving = false;
            // A load is put down partway through the stop.
            if (ant.loaded && ant.pause * 2 < ant.stay) {
                ant.loaded = false;
            }
            if (ant.pause == 0) {
                leave(ant);
            }
            return;
        }
        float dx = ant.targetX - ant.x;
        float dz = ant.targetZ - ant.z;
        if (dx * dx + dz * dz < SPEED * SPEED * 2.25F) {
            arrive(ant);
            return;
        }
        ant.yaw = Mth.rotLerpRad(0.3F, ant.yaw, (float) Math.atan2(-dx, -dz));
        ant.x = Math.clamp(ant.x - Mth.sin(ant.yaw) * SPEED, EDGE_MIN, EDGE_MAX);
        ant.z = Math.clamp(ant.z - Mth.cos(ant.yaw) * SPEED, EDGE_MIN, EDGE_MAX);
        ant.moving = true;
        ant.phase += SPEED * 6.5F;
    }

    private void arrive(Ant ant) {
        if (ant.outbound) {
            ant.work = workThere();
            ant.pause = ant.stay = workTicks();
        } else {
            ant.work = kind == StructureKind.HARVESTING_SITE ? Work.TEND : Work.DROP;
            ant.pause = ant.stay = kind == StructureKind.HARVESTING_SITE ? 40 + random.nextInt(50) : 12 + random.nextInt(10);
        }
    }

    /** Its stop is over: on with the other half of the trip, or, home with nothing to carry, a rest first now and then. */
    private void leave(Ant ant) {
        if (!ant.outbound && ant.work != Work.REST && random.nextInt(4) == 0) {
            ant.work = Work.REST;
            ant.pause = ant.stay = 30 + random.nextInt(70);
            return;
        }
        boolean wasResting = ant.work == Work.REST && ant.outbound;
        ant.outbound = wasResting || !ant.outbound;
        ant.loaded = !ant.outbound;
        ant.work = Work.NONE;
        float[] spot = ant.outbound ? workSpot() : deliverySpot();
        for (int attempt = 0; attempt < 8 && taken(spot, ant); attempt++) {
            spot = ant.outbound ? workSpot() : deliverySpot();
        }
        ant.targetX = spot[0];
        ant.targetZ = spot[1];
    }

    /** Whether another ant stands on a spot or is on its way to it (ants through the portal take up no room). */
    private boolean taken(float[] spot, Ant asking) {
        if (kind.expedition() && asking.outbound) {
            return false;
        }
        for (Ant other : ants) {
            if (other == asking || other.away()) {
                continue;
            }
            boolean standing = other.pause > 0 && Mth.square(other.x - spot[0]) + Mth.square(other.z - spot[1]) < TAKEN * TAKEN;
            boolean coming = other.pause == 0 && Mth.square(other.targetX - spot[0]) + Mth.square(other.targetZ - spot[1]) < TAKEN * TAKEN;
            if (standing || coming) {
                return true;
            }
        }
        return false;
    }

    /**
     * A walking ant leans away from any ant close by, to the side, and keeps walking: they go round each other, brushing
     * at most. An ant that has stopped stays where it is.
     */
    private void leanAway() {
        for (Ant ant : ants) {
            if (!ant.moving) {
                continue;
            }
            float rightX = Mth.cos(ant.yaw);
            float rightZ = -Mth.sin(ant.yaw);
            for (Ant other : ants) {
                if (other == ant || other.away()) {
                    continue;
                }
                float dx = other.x - ant.x;
                float dz = other.z - ant.z;
                float distance = Mth.sqrt(dx * dx + dz * dz);
                if (distance >= NEAR) {
                    continue;
                }
                // To the right when the other is on its left or dead ahead, to the left otherwise.
                float side = dx * rightX + dz * rightZ > 0.05F ? -1 : 1;
                float lean = LEAN * (1 - distance / NEAR) * side;
                ant.x = Math.clamp(ant.x + rightX * lean, EDGE_MIN, EDGE_MAX);
                ant.z = Math.clamp(ant.z + rightZ * lean, EDGE_MIN, EDGE_MAX);
            }
        }
    }
}
