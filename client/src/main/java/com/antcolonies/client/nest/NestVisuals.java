package com.antcolonies.client.nest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.species.Caste;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The life drawn in one colony block (the Formicarium or a chamber): its {@link NestLayout} and a handful of ants that
 * show what the colony's real numbers say. They live only on the client, decide nothing, and follow the synced colony.
 *
 * <p>Ants walk the layout's paths and mill about in its chambers. Where a tunnel crosses into the next block of the
 * build, an ant walking out is handed to that block's visuals: workers leave the Formicarium for the sites, and come
 * back with what the sites find, through every block on the way.
 */
public final class NestVisuals {
    /** What a block's own ants do. */
    public enum Role {
        /** Formicarium: eggs from the Queen to the brood. */
        NURSE,
        /** Formicarium: food in from the burrow, or out to the sites to fetch it. */
        CARRY,
        /** Formicarium: soil and waste out of the chambers. */
        CLEAN,
        IDLE,
        /** Hunters: the big-jawed ants, wandering everywhere. */
        PATROL,
        /** Job sites: out through the way out (or the portal), back with a find. */
        WORKER,
        /** Chambers without a job: looking after what is there. */
        TENDER
    }

    public enum Mission {
        RESIDENT,
        /** Came in through a tunnel from the next block. */
        INBOUND,
        /** On the way to a tunnel into the next block. */
        OUTBOUND,
        /** On the way to the burrow, to be seen no more. */
        RETIRE
    }

    public enum Action {
        NONE, GROOM, FEED, TEND
    }

    public static final int EGG_COLOR = 0xFFF4F0E2;
    public static final int SOIL_COLOR = 0xFF5A3D25;

    public static final class Actor {
        public Caste caste = Caste.WORKER;
        public boolean former;
        public int carry;
        public float u;
        public float v;
        public float prevU;
        public float prevV;
        /** The way it faces: along +u or -u, and how steeply up (radians). */
        public boolean facingPositive = true;
        public float pitch;
        public float prevPitch;
        public float phase;
        public float prevPhase;
        public boolean moving;
        public int pause;
        public Action action = Action.NONE;
        public float seed;
        /** In the burrow or through the portal: not drawn. */
        public boolean hidden;
        float speed;
        Role role = Role.IDLE;
        Mission mission = Mission.RESIDENT;
        /** The node it stands at when on no edge. */
        int node;
        /** The node it is going to, -1 when it has yet to choose. */
        int goal = -1;
        NestLayout.@Nullable Edge edge;
        int from;
        float walked;
        /** Milling about in a chamber: to a point, a pause there, and back to the hub. */
        boolean roaming;
        boolean returning;
        float targetU;
        float targetV;
        Action roamAction = Action.NONE;
        int exitSide = -1;
        int arrivedSide = -1;
        /** Sent out of the Formicarium to bring something back. */
        boolean fetching;
        boolean gone;
    }

    /**
     * What a block's visuals follow, worked out by the block each tick.
     *
     * @param gates      per side of the face, where the tunnel crosses it, or -1
     * @param colony     the colony the block belongs to (a chamber's is its Formicarium's), if any
     * @param workers    ants assigned to a job site
     * @param ants       whether ants are drawn here at all
     * @param carryColor what ants carry out of this block (a job site's finds)
     */
    public record Brief(NestLayout.Kind kind, long seed, int[] gates, @Nullable Colony colony, int workers, boolean ants, boolean hunters,
                        int carryColor, int maxAnts) {}

    /** The blocks round this one, for ants that walk out of it. */
    public interface Host {
        /** The visuals of the block on a side of the face, when its tunnels join this one's. */
        @Nullable NestVisuals neighbour(int side);

        /** The side on which the build leads toward the Formicarium, or -1 (this is the Formicarium, or there is no way). */
        int homeward();

        /** The side of the neighbour's own face that an ant leaving through {@code side} comes in by (blocks may face opposite ways). */
        int entry(int side);
    }

    public @Nullable NestLayout layout;
    public final List<Actor> actors = new ArrayList<>();
    /** How full the brood and food spots are, 0 to 1, and how much of the brood are larvae. */
    public float brood;
    public float larvae;
    public float food;
    public float queenPhase;
    public boolean queenLaying;
    /**
     * The Queen, walking about her chamber's floor: where (u of her middle; NaN before the layout has one), how far she
     * has walked (her legs' cycle follows it), which way she faces, and whether she is walking. She walks a few steps,
     * rests (laying, when the colony lays), and walks on.
     */
    public float queenU = Float.NaN;
    public float prevQueenU = Float.NaN;
    public float queenV;
    public float queenWalked;
    public float prevQueenWalked;
    public boolean queenRight = true;
    public boolean queenMoving;
    private float queenTarget;
    private float queenMin;
    private float queenMax;
    private int queenRest;
    public float evolutionGlow;
    public int carryColor = 0xFFD9B26A;
    private final RandomSource random;
    private long age;
    private long awakeAt = Long.MIN_VALUE / 2;
    private int target;
    private final float[] scratch = new float[4];

    public NestVisuals(long seed) {
        this.random = RandomSource.create(seed);
    }

    // ------------------------------------------------------------------ Following the colony

    public void tick(Brief brief, Host host, boolean animate) {
        age++;
        Colony colony = brief.colony();
        int stage = colony == null ? 1 : colony.stage();
        if (layout == null || layout.kind != brief.kind() || !Arrays.equals(layout.gates, brief.gates())
                || brief.kind() == NestLayout.Kind.FORMICARIUM && layout.stage != Math.clamp(stage, 1, 4)) {
            layout = NestLayout.create(brief.kind(), brief.seed(), brief.gates(), stage);
            actors.clear();
            placeQueen();
        }
        carryColor = brief.carryColor();
        if (age % 20 == 1) {
            follow(brief);
        }
        queenLaying = colony != null && colony.activity().laying() && colony.queen() != null;
        queenPhase += queenLaying ? 1.0F : 0.4F;
        if (animate) {
            walkQueen();
        }
        if (evolutionGlow > 0) {
            evolutionGlow = Math.max(0, evolutionGlow - 0.01F);
        }
        if (!animate) {
            return;
        }
        awakeAt = age;
        if (actors.size() < target && age % 12 == 7) {
            actors.add(spawn(brief));
        }
        float hunger = colony != null && colony.activity().starving() ? 0.45F : 1;
        for (int i = 0; i < actors.size(); i++) {
            step(actors.get(i), brief, host, hunger);
        }
        actors.removeIf(actor -> actor.gone);
        meet();
    }

    /** How many ants the block should hold, and how full its brood and food spots are. */
    private void follow(Brief brief) {
        Colony colony = brief.colony();
        long adults = colony == null ? 0 : colony.adults();
        int count;
        if (!brief.ants() || adults <= 0) {
            count = 0;
        } else {
            count = switch (brief.kind()) {
                // A block's face is 16 px: past ten ants they only pile on each other (more of the colony walks the
                // blocks round it).
                case FORMICARIUM -> (int) Math.min(Math.min(adults, 10), Math.round(2 + 2.5 * Math.log10(1 + adults)));
                case NEST_CHAMBER -> colony.larvae() + colony.eggs() > 200 ? 3 : 2;
                case QUEEN_CHAMBER, COLONY_CONNECTOR -> 2;
                case STORAGE -> 1;
                default -> brief.workers() <= 0 ? 0 : (int) Math.min(6, 1 + Math.floor(Math.log(brief.workers()) / Math.log(2.5)));
            };
        }
        target = Math.min(count, brief.maxAnts());
        if (target == 0 && !actors.isEmpty() && (!brief.ants() || adults <= 0)) {
            actors.clear();
        }
        // More than the block should hold: the last of its own go down the burrow.
        for (int i = actors.size() - 1, extra = actors.size() - target - 3; i >= 0 && extra > 0; i--) {
            if (actors.get(i).mission == Mission.RESIDENT) {
                send(actors.get(i), Mission.RETIRE);
                extra--;
            }
        }
        if (colony == null) {
            brood = 0;
            larvae = 0;
            food = 0;
            return;
        }
        double young = colony.eggs() + colony.larvae();
        brood = young <= 0 ? 0 : (float) Math.min(1, (0.6 + 1.2 * Math.log10(1 + young)) / 4.8);
        larvae = young <= 0 ? 0 : (float) (colony.larvae() / young);
        food = colony.food() <= 0 ? 0 : (float) Math.min(1, Math.sqrt(colony.food() / Math.max(1, colony.stats().foodCapacity())));
    }

    private Actor spawn(Brief brief) {
        NestLayout layout = this.layout;
        Actor actor = new Actor();
        actor.seed = random.nextFloat() * 10;
        actor.speed = 0.09F + random.nextFloat() * 0.06F;
        int source = layout.find(NestLayout.Spot.SOURCE);
        actor.node = source >= 0 ? source : layout.find(NestLayout.Spot.MAIN);
        NestLayout.Node node = layout.nodes.get(actor.node);
        actor.u = actor.prevU = node.u;
        actor.v = actor.prevV = node.v;
        settle(actor, brief);
        return actor;
    }

    /** Makes an ant one of the block's own, with a job. */
    private void settle(Actor actor, Brief brief) {
        actor.mission = Mission.RESIDENT;
        actor.goal = -1;
        actor.fetching = false;
        Colony colony = brief.colony();
        actor.former = colony != null && colony.formerSpecies() != null && random.nextFloat() >= colony.conversion();
        actor.caste = Caste.WORKER;
        float roll = random.nextFloat();
        switch (brief.kind()) {
            case FORMICARIUM -> {
                Colony.Activity activity = colony == null ? Colony.Activity.NONE : colony.activity();
                float working = activity.scavenging() + activity.processing() + activity.farming();
                if (brief.hunters() && roll < 0.3F) {
                    actor.role = Role.PATROL;
                    actor.caste = Caste.SOLDIER;
                } else if (roll < 0.3F + Math.max(0.15F, Math.min(0.35F, working))) {
                    actor.role = Role.CARRY;
                } else if (roll < 0.8F) {
                    actor.role = Role.NURSE;
                } else if (roll < 0.92F) {
                    actor.role = Role.CLEAN;
                } else {
                    actor.role = Role.IDLE;
                }
            }
            case HUNTING_GROUND -> {
                actor.role = Role.PATROL;
                actor.caste = Caste.SOLDIER;
            }
            case NEST_CHAMBER, QUEEN_CHAMBER, STORAGE -> actor.role = Role.TENDER;
            default -> actor.role = Role.WORKER;
        }
    }

    private void send(Actor actor, Mission mission) {
        actor.mission = mission;
        actor.goal = -1;
    }

    // ------------------------------------------------------------------ Walking

    private void step(Actor actor, Brief brief, Host host, float hunger) {
        NestLayout layout = this.layout;
        actor.prevU = actor.u;
        actor.prevV = actor.v;
        actor.prevPhase = actor.phase;
        actor.prevPitch = actor.pitch;
        if (actor.pause > 0) {
            actor.pause--;
            actor.moving = false;
            if (actor.pause == 0) {
                actor.action = Action.NONE;
                actor.hidden = false;
            }
            return;
        }
        float pace = actor.speed * hunger * (actor.carry != 0 ? 0.8F : 1);
        if (actor.edge != null) {
            actor.walked += pace;
            float[] point = actor.edge.point(actor.from, Math.min(actor.walked, actor.edge.length), scratch);
            move(actor, point[0], point[1], point[2], point[3], pace);
            if (actor.walked >= actor.edge.length) {
                actor.node = actor.edge.other(actor.from);
                actor.edge = null;
                if (actor.goal == actor.node) {
                    actor.goal = -1;
                    reached(actor, brief, host);
                }
            }
            return;
        }
        if (actor.roaming) {
            float du = actor.targetU - actor.u;
            float dv = actor.targetV - actor.v;
            float distance = Mth.sqrt(du * du + dv * dv);
            float slow = pace * 0.7F;
            if (distance > slow) {
                move(actor, actor.u + du / distance * slow, actor.v + dv / distance * slow, du, dv, slow);
                return;
            }
            move(actor, actor.targetU, actor.targetV, du, dv, slow);
            if (actor.returning) {
                actor.roaming = false;
            } else {
                pause(actor, 20 + random.nextInt(40), actor.roamAction);
                NestLayout.Node hub = layout.nodes.get(actor.node);
                actor.targetU = hub.u;
                actor.targetV = hub.v;
                actor.returning = true;
            }
            return;
        }
        actor.moving = false;
        if (actor.goal < 0) {
            choose(actor, brief, host);
            if (actor.gone || actor.goal < 0) {
                return;
            }
        }
        if (actor.goal == actor.node) {
            actor.goal = -1;
            reached(actor, brief, host);
            return;
        }
        // The edge that leads nearest to where it is going.
        int[] distance = layout.distances(actor.goal);
        NestLayout.Edge best = null;
        int steps = Integer.MAX_VALUE;
        for (NestLayout.Edge edge : layout.edges) {
            if (edge.a != actor.node && edge.b != actor.node) {
                continue;
            }
            int d = distance[edge.other(actor.node)];
            if (d < steps || d == steps && random.nextBoolean()) {
                steps = d;
                best = edge;
            }
        }
        if (best == null || steps >= Integer.MAX_VALUE / 2) {
            actor.goal = -1;
            pause(actor, 20, Action.NONE);
            return;
        }
        actor.edge = best;
        actor.from = actor.node;
        actor.walked = 0;
    }

    private void move(Actor actor, float u, float v, float du, float dv, float pace) {
        actor.u = u;
        actor.v = v;
        if (Math.abs(du) > 1e-3F || Math.abs(dv) > 1e-3F) {
            if (Math.abs(du) > Math.abs(dv) * 0.2F) {
                actor.facingPositive = du > 0;
            }
            float pitch = (float) Math.atan2(dv, Math.abs(du));
            actor.pitch += Mth.clamp(pitch - actor.pitch, -0.45F, 0.45F);
        }
        actor.moving = true;
        actor.phase += pace * 6.5F;
    }

    private void pause(Actor actor, int ticks, Action action) {
        actor.pause = ticks;
        actor.action = action;
        actor.moving = false;
    }

    /** Sets an ant milling about in the chamber it stands in: to some point of it, a pause, and back. */
    private void roam(Actor actor, Action action) {
        NestLayout.Node node = layout.nodes.get(actor.node);
        if (node.cells.isEmpty()) {
            return;
        }
        // Somewhere on the chamber's floor it can walk to straight along the floor (no flying across the hollow).
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] cell = node.cells.get(random.nextInt(node.cells.size()));
            if (!layout.floor(cell[0], cell[1]) || Math.abs(cell[1] + 0.5F - actor.v) > 1.01F) {
                continue;
            }
            float u = cell[0] + 0.5F;
            float v = cell[1] + 0.5F;
            if (layout.clear(actor.u, actor.v, u, v) && layout.clear(u, v, node.u, node.v)) {
                actor.roaming = true;
                actor.returning = false;
                actor.targetU = u;
                actor.targetV = v;
                actor.roamAction = action;
                return;
            }
        }
    }

    // ------------------------------------------------------------------ What the ants are about

    /** Where an ant with nowhere to go goes next. */
    private void choose(Actor actor, Brief brief, Host host) {
        NestLayout layout = this.layout;
        int main = layout.find(NestLayout.Spot.MAIN);
        int source = layout.find(NestLayout.Spot.SOURCE);
        switch (actor.mission) {
            case OUTBOUND -> {
                actor.goal = layout.gate(actor.exitSide);
                if (actor.goal < 0) {
                    settle(actor, brief);
                }
            }
            case RETIRE -> {
                if (source < 0) {
                    actor.gone = true;
                } else {
                    actor.goal = source;
                }
            }
            case INBOUND -> {
                int food = layout.find(NestLayout.Spot.FOOD);
                actor.goal = actor.carry != 0 && food >= 0 ? food : main;
            }
            case RESIDENT -> {
                switch (actor.role) {
                    case NURSE -> {
                        int brood = layout.find(NestLayout.Spot.BROOD);
                        actor.goal = actor.carry != 0 && brood >= 0 ? brood : main;
                    }
                    case CARRY -> {
                        int food = layout.find(NestLayout.Spot.FOOD);
                        if (actor.carry != 0) {
                            actor.goal = food >= 0 ? food : main;
                        } else {
                            int exit = random.nextFloat() < 0.65F ? exit(host, -1) : -1;
                            if (exit >= 0) {
                                actor.mission = Mission.OUTBOUND;
                                actor.exitSide = exit;
                                actor.fetching = true;
                                actor.goal = layout.gate(exit);
                            } else {
                                actor.goal = source >= 0 ? source : main;
                            }
                        }
                    }
                    case CLEAN -> {
                        int waste = layout.find(NestLayout.Spot.WASTE);
                        actor.goal = actor.carry != 0 ? (waste >= 0 ? waste : source >= 0 ? source : main) : chamber();
                    }
                    case WORKER -> {
                        if (actor.carry == 0) {
                            actor.goal = source >= 0 ? source : main;
                        } else {
                            int home = random.nextFloat() < 0.45F ? host.homeward() : -1;
                            if (home >= 0 && layout.gate(home) >= 0 && host.neighbour(home) != null) {
                                actor.mission = Mission.OUTBOUND;
                                actor.exitSide = home;
                                actor.goal = layout.gate(home);
                            } else {
                                actor.goal = main;
                            }
                        }
                    }
                    case TENDER -> actor.goal = random.nextFloat() < 0.15F && source >= 0 ? source : main;
                    default -> actor.goal = chamber();
                }
            }
        }
    }

    /** A chamber of the block, any of them. */
    private int chamber() {
        int pick = -1;
        int seen = 0;
        for (int i = 0; i < layout.nodes.size(); i++) {
            if (!layout.nodes.get(i).cells.isEmpty() && random.nextInt(++seen) == 0) {
                pick = i;
            }
        }
        return pick;
    }

    /** A side with a tunnel into a block that takes ants, any but {@code not} when there is another; -1 when there is none. */
    private int exit(Host host, int not) {
        int pick = -1;
        int seen = 0;
        for (int side = 0; side < 4; side++) {
            if (side != not && layout.gate(side) >= 0 && host.neighbour(side) != null && random.nextInt(++seen) == 0) {
                pick = side;
            }
        }
        return pick >= 0 || not < 0 ? pick : layout.gate(not) >= 0 && host.neighbour(not) != null ? not : -1;
    }

    /** An ant came to where it was going. */
    private void reached(Actor actor, Brief brief, Host host) {
        NestLayout.Node node = layout.nodes.get(actor.node);
        switch (actor.mission) {
            case OUTBOUND -> {
                NestVisuals next = host.neighbour(node.side);
                if (next != null && next.accept(actor, host.entry(node.side))) {
                    actor.gone = true;
                } else {
                    // Nobody takes it: back the way it came.
                    actor.mission = Mission.INBOUND;
                    actor.arrivedSide = node.side;
                }
            }
            case RETIRE -> actor.gone = true;
            case INBOUND -> arrived(actor, brief, host);
            case RESIDENT -> {
                switch (actor.role) {
                    case NURSE -> {
                        if (node.spot == NestLayout.Spot.BROOD) {
                            actor.carry = 0;
                            roam(actor, Action.TEND);
                        } else {
                            actor.carry = queenLaying || brood > 0 ? EGG_COLOR : 0;
                            if (layout.find(NestLayout.Spot.BROOD) < 0 || actor.carry == 0) {
                                actor.carry = 0;
                                roam(actor, Action.TEND);
                            } else {
                                // Off to where the Queen lays to take an egg, not waiting in the middle of her chamber.
                                roam(actor, Action.TEND);
                            }
                        }
                    }
                    case CARRY -> {
                        if (node.spot == NestLayout.Spot.SOURCE) {
                            actor.carry = carryColor;
                            dive(actor);
                        } else {
                            actor.carry = 0;
                            roam(actor, Action.NONE);
                        }
                    }
                    case CLEAN -> {
                        if (actor.carry != 0) {
                            actor.carry = 0;
                            if (node.spot == NestLayout.Spot.SOURCE) {
                                dive(actor);
                            } else {
                                roam(actor, Action.NONE);
                            }
                        } else {
                            actor.carry = SOIL_COLOR;
                            roam(actor, Action.GROOM);
                        }
                    }
                    case WORKER -> {
                        if (node.spot == NestLayout.Spot.SOURCE) {
                            actor.carry = carryColor;
                            dive(actor);
                        } else {
                            actor.carry = 0;
                            roam(actor, Action.NONE);
                        }
                    }
                    case TENDER -> {
                        if (node.spot == NestLayout.Spot.SOURCE) {
                            dive(actor);
                        } else {
                            roam(actor, Action.TEND);
                        }
                    }
                    default -> {
                        if (node.spot == NestLayout.Spot.SOURCE) {
                            dive(actor);
                        } else {
                            roam(actor, random.nextBoolean() ? Action.GROOM : Action.NONE);
                        }
                    }
                }
            }
        }
    }

    /** Into the burrow (or through the portal) for a moment. */
    private void dive(Actor actor) {
        pause(actor, 25 + random.nextInt(50), Action.NONE);
        actor.hidden = true;
    }

    /** A visitor from the next block came to this one's chamber: it leaves what it brought, or takes something, and goes on. */
    private void arrived(Actor actor, Brief brief, Host host) {
        boolean formicarium = brief.kind() == NestLayout.Kind.FORMICARIUM;
        boolean store = formicarium || brief.kind() == NestLayout.Kind.STORAGE;
        roam(actor, Action.NONE);
        if (actor.carry != 0) {
            if (store) {
                actor.carry = 0;
            } else {
                int home = host.homeward();
                if (home >= 0 && home != actor.arrivedSide && layout.gate(home) >= 0 && host.neighbour(home) != null) {
                    actor.mission = Mission.OUTBOUND;
                    actor.exitSide = home;
                    return;
                }
                actor.carry = 0;
            }
        } else if (actor.fetching && !formicarium && (brief.workers() > 0 || brief.kind() == NestLayout.Kind.STORAGE)) {
            // What it was sent for: a find of this site (or something out of the Storage), and home with it.
            int home = host.homeward();
            if (home >= 0 && layout.gate(home) >= 0 && host.neighbour(home) != null) {
                actor.carry = carryColor;
                actor.fetching = false;
                actor.mission = Mission.OUTBOUND;
                actor.exitSide = home;
                return;
            }
        }
        long residents = actors.stream().filter(other -> other.mission == Mission.RESIDENT).count();
        if (formicarium && residents < target) {
            settle(actor, brief);
            return;
        }
        int exit = random.nextFloat() < (actor.fetching ? 0.85F : 0.5F) ? exit(host, actor.arrivedSide) : -1;
        if (exit >= 0) {
            actor.mission = Mission.OUTBOUND;
            actor.exitSide = exit;
        } else {
            send(actor, Mission.RETIRE);
        }
    }

    /**
     * Takes an ant walking in through the tunnel on a side of this block's face. False when the block has no tunnel
     * there, nobody is near enough to see it, or it is full.
     */
    public boolean accept(Actor actor, int side) {
        NestLayout layout = this.layout;
        if (layout == null || age - awakeAt > 2 || actors.size() >= target + 4) {
            return false;
        }
        int gate = layout.gate(side);
        if (gate < 0) {
            return false;
        }
        NestLayout.Edge edge = null;
        for (NestLayout.Edge candidate : layout.edges) {
            if (candidate.a == gate || candidate.b == gate) {
                edge = candidate;
            }
        }
        if (edge == null) {
            return false;
        }
        Actor arrival = new Actor();
        arrival.caste = actor.caste;
        arrival.former = actor.former;
        arrival.carry = actor.carry;
        arrival.fetching = actor.fetching;
        arrival.seed = actor.seed;
        arrival.speed = actor.speed;
        arrival.phase = arrival.prevPhase = actor.phase;
        arrival.facingPositive = actor.facingPositive;
        arrival.pitch = arrival.prevPitch = actor.pitch;
        NestLayout.Node node = layout.nodes.get(gate);
        arrival.u = arrival.prevU = node.u;
        arrival.v = arrival.prevV = node.v;
        arrival.mission = Mission.INBOUND;
        arrival.arrivedSide = side;
        arrival.node = gate;
        arrival.edge = edge;
        arrival.from = gate;
        actors.add(arrival);
        return true;
    }

    /** Two walking ants that meet stop and feed each other (trophallaxis). */
    private void meet() {
        if (age % 5 != 0) {
            return;
        }
        for (int i = 0; i < actors.size(); i++) {
            Actor a = actors.get(i);
            if (a.pause > 0 || !a.moving) {
                continue;
            }
            for (int j = i + 1; j < actors.size(); j++) {
                Actor b = actors.get(j);
                if (b.pause > 0 || !b.moving) {
                    continue;
                }
                float du = a.u - b.u;
                float dv = a.v - b.v;
                if (du * du + dv * dv < 1.1F && random.nextFloat() < 0.3F) {
                    int ticks = 20 + random.nextInt(20);
                    pause(a, ticks, Action.FEED);
                    pause(b, ticks, Action.FEED);
                    a.facingPositive = b.u > a.u;
                    b.facingPositive = a.u > b.u;
                    break;
                }
            }
        }
    }

    public void evolved() {
        evolutionGlow = 1;
    }

    // ------------------------------------------------------------------ The Queen

    /** Half the Queen's length on the face, px (NestRenderer draws her 1.75 times a worker's 1.9 px). */
    private static final float QUEEN_HALF = 1.75F * 1.9F / 2;

    /** Finds the floor of her chamber (the run of floor cells round her spot) and sets her on it. */
    private void placeQueen() {
        queenU = Float.NaN;
        prevQueenU = Float.NaN;
        for (NestLayout.Feature feature : layout.features) {
            if (feature.type() != 'q') {
                continue;
            }
            int v = feature.v();
            int left = feature.u();
            int right = feature.u();
            while (layout.floor(left - 1, v)) {
                left--;
            }
            while (layout.floor(right + 1, v)) {
                right++;
            }
            queenMin = left + QUEEN_HALF;
            queenMax = right + 1 - QUEEN_HALF;
            if (queenMin > queenMax) {
                queenMin = queenMax = (left + right + 1) / 2F;
            }
            queenU = prevQueenU = Math.clamp(feature.u() + 0.5F, queenMin, queenMax);
            queenV = v;
            queenTarget = queenU;
            queenRest = 40 + random.nextInt(80);
            queenMoving = false;
            return;
        }
    }

    /** A slow walk to a spot of her floor, a rest there, and on to another. */
    private void walkQueen() {
        prevQueenU = queenU;
        prevQueenWalked = queenWalked;
        if (Float.isNaN(queenU)) {
            return;
        }
        if (queenRest > 0) {
            queenRest--;
            queenMoving = false;
            if (queenRest == 0) {
                // Somewhere else on her floor, at least a step away when there is room for it.
                float span = queenMax - queenMin;
                queenTarget = span < 1 ? queenMin + random.nextFloat() * span : queenU;
                for (int attempt = 0; attempt < 6 && span >= 1 && Math.abs(queenTarget - queenU) < 1; attempt++) {
                    queenTarget = queenMin + random.nextFloat() * span;
                }
            }
            return;
        }
        float step = queenLaying ? 0.018F : 0.025F;
        float left = queenTarget - queenU;
        if (Math.abs(left) <= step) {
            queenWalked += Math.abs(left);
            queenU = queenTarget;
            queenMoving = false;
            // A long rest while she lays, a shorter one otherwise.
            queenRest = (queenLaying ? 120 : 50) + random.nextInt(120);
            return;
        }
        queenRight = left > 0;
        queenU += Math.signum(left) * step;
        queenWalked += step;
        queenMoving = true;
    }
}
