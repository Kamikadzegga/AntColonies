package com.antcolonies.colony.simulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;

/**
 * Puts the colony's ants to work. Workers kept for maintenance are taken out first; the rest go to Harvesting Sites
 * (food first), then Foraging Sites, each site in the scan's fixed order taking what it asks for (or all it can hold) up
 * to its capacity. Soldiers go to Foraging Sites the same way, to hunt.
 *
 * <p>An ant is only ever counted once: the totals handed out never exceed the ants available, which
 * {@link #allocate} checks. A site that is gone, unloaded or no longer joined simply isn't in the next step's list, so
 * its ants are free again straight away.
 */
public final class Allocation {
    /** The order workers are handed out in. */
    private static final List<StructureKind> WORKER_ORDER = List.of(StructureKind.HARVESTING_SITE, StructureKind.FORAGING_SITE);

    /** One working site and the ants it got this step. */
    public static final class Site {
        private final StructureBlockEntity entity;
        private int assigned;
        private int soldiers;
        private final int requested;
        private final int capacity;
        private int status;
        private double rate;
        private double huntRate;

        Site(StructureBlockEntity entity, int assigned, int requested, int capacity, int status) {
            this.entity = entity;
            this.assigned = assigned;
            this.requested = requested;
            this.capacity = capacity;
            this.status = status;
        }

        public StructureBlockEntity entity() {
            return entity;
        }

        public StructureKind kind() {
            return entity.kind();
        }

        /** Workers (farmers at a Harvesting Site). */
        public int assigned() {
            return assigned;
        }

        /** Soldiers hunting here (Foraging Sites only). */
        public int soldiers() {
            return soldiers;
        }

        public void addStatus(ColonyStatus flag) {
            status |= flag.bit();
        }

        public void setRate(double rate) {
            this.rate = rate;
        }

        public double rate() {
            return rate;
        }

        public void setHuntRate(double huntRate) {
            this.huntRate = huntRate;
        }

        public double huntRate() {
            return huntRate;
        }
    }

    /** A working site with a redstone signal on it is paused: its ants go to the other sites (or stay home). */
    private static boolean paused(ColonySimulator.Step step, StructureBlockEntity entity) {
        return step.level.hasNeighborSignal(entity.getBlockPos());
    }

    private final Map<StructureKind, List<Site>> sites = new EnumMap<>(StructureKind.class);
    private int assignedWorkers;
    private int assignedSoldiers;

    /**
     * @param workers  workers free for jobs (after maintenance)
     * @param soldiers soldiers free for jobs
     */
    static Allocation allocate(ColonySimulator.Step step, int workers, int soldiers) {
        Allocation allocation = new Allocation();
        int freeWorkers = Math.max(0, workers);
        for (StructureKind kind : WORKER_ORDER) {
            for (StructureBlockEntity entity : step.of(kind)) {
                int status = 0;
                int capacity = entity.capacity();
                int wanted = entity.requested() < 0 ? capacity : Math.min(entity.requested(), capacity);
                if (kind == StructureKind.HARVESTING_SITE && !step.species.canFarm()) {
                    // A Harvesting Site needs Farmer Ants (or a species that farms); other ants leave it alone.
                    wanted = 0;
                    status |= ColonyStatus.INCOMPATIBLE_SPECIES.bit();
                }
                if (paused(step, entity)) {
                    wanted = 0;
                    status |= ColonyStatus.PAUSED.bit();
                }
                int given = Math.min(wanted, freeWorkers);
                freeWorkers -= given;
                allocation.assignedWorkers += given;
                if (given == 0 && wanted > 0) {
                    status |= ColonyStatus.NO_WORKERS.bit();
                }
                allocation.sites.computeIfAbsent(kind, k -> new ArrayList<>()).add(new Site(entity, given, entity.requested(), capacity, status));
            }
        }
        int freeSoldiers = Math.max(0, soldiers);
        for (Site site : allocation.of(StructureKind.FORAGING_SITE)) {
            StructureBlockEntity entity = site.entity;
            int capacity = entity.capacity();
            int wanted = paused(step, entity) ? 0 : entity.requestedSoldiers() < 0 ? capacity : Math.min(entity.requestedSoldiers(), capacity);
            int given = Math.min(wanted, freeSoldiers);
            freeSoldiers -= given;
            allocation.assignedSoldiers += given;
            site.soldiers = given;
        }
        if (allocation.assignedWorkers > Math.max(0, workers) || allocation.assignedSoldiers > Math.max(0, soldiers)) {
            throw new IllegalStateException("Ant allocation handed out more ants than the colony has");
        }
        return allocation;
    }

    public List<Site> of(StructureKind kind) {
        return sites.getOrDefault(kind, List.of());
    }

    public List<Site> all() {
        List<Site> all = new ArrayList<>();
        sites.values().forEach(all::addAll);
        return all;
    }

    public int assignedWorkers() {
        return assignedWorkers;
    }

    public int assignedSoldiers() {
        return assignedSoldiers;
    }

    /** Workers (or farmers) at the sites of a kind. */
    public int assigned(StructureKind kind) {
        return of(kind).stream().mapToInt(Site::assigned).sum();
    }

    /** Soldiers at the sites of a kind. */
    public int soldiers(StructureKind kind) {
        return of(kind).stream().mapToInt(Site::soldiers).sum();
    }

    /** Tells each site what it got, for its GUI and its animation. */
    void publish(ColonySimulator.Step step) {
        for (Site site : all()) {
            site.entity.setAssignment(site.assigned, site.soldiers, site.capacity, site.status, site.rate, site.huntRate);
        }
    }

    List<Colony.Assignment> assignments() {
        List<Colony.Assignment> list = new ArrayList<>();
        for (Site site : all()) {
            list.add(new Colony.Assignment(site.entity.getBlockPos(), site.kind(), site.assigned, site.soldiers, site.requested, site.capacity,
                    site.status, site.rate));
        }
        return list;
    }
}
