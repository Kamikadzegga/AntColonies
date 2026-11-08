package com.antcolonies.client.terrarium;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientSpecies;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The ants drawn inside one Terrarium: a handful of representative actors that show what the colony's real numbers
 * say (how many ants, which jobs, soldiers, hunger, a Queen laying, an evolution under way). They live only on the
 * client, decide nothing, and are rebuilt from the synced colony whenever it changes.
 *
 * <p>Actors walk the tunnel graph of their glass side (face 0-3: north, east, south, west) or wander the soil's surface
 * (face {@link #SURFACE}). Carriers bring food from the entrances to the stores, nurses carry eggs from the Queen to
 * the brood chambers, cleaners take soil out, soldiers patrol the surface, and ants that meet stop to feed each other.
 */
public final class TerrariumVisuals {
    public static final int SURFACE = 4;

    public enum Task {
        CARRY, FARM, CLEAN, NURSE, PATROL, IDLE
    }

    public enum Action {
        NONE, GROOM, FEED, TEND
    }

    public static final class Actor {
        public int face;
        public Caste caste = Caste.WORKER;
        public boolean former;
        public Task task = Task.IDLE;
        public int carry;
        // On a glass side: the edge walked, from which node, and how far along.
        int from;
        TunnelLayout.Edge edge;
        float t;
        float speed;
        public float u;
        public float v;
        public float prevU;
        public float prevV;
        // On the surface.
        public float x;
        public float z;
        public float prevX;
        public float prevZ;
        float targetX;
        float targetZ;
        public float yaw;
        public float prevYaw;
        public float pitch;
        public float prevPitch;
        public boolean facingPositive = true;
        public int pause;
        public Action action = Action.NONE;
        public float phase;
        public float prevPhase;
        public float seed;
        public boolean moving;
    }

    /** A brood or store item lying in a chamber: u, v on the front or a side face. */
    public record Item(int face, float u, float v, float size, int kind) {}

    public static final int EGG = 0;
    public static final int LARVA = 1;
    public static final int CRUMB = 2;

    private final long seed;
    private final RandomSource random;
    public final TunnelLayout[] faces = new TunnelLayout[4];
    public final List<Actor> actors = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();
    public int frontFace;
    public int stage = -1;
    public int carryColor = 0xFFD9B26A;
    public float queenPhase;
    public boolean queenLaying;
    public int eggFlash;
    public float evolutionGlow;
    private int itemsHash;
    private int compositionHash;
    private long age;
    private final int[][] distances = new int[4 * TunnelLayout.ChamberType.values().length][];

    public TerrariumVisuals(long seed, int frontFace) {
        this.seed = seed;
        this.random = RandomSource.create(seed);
        this.frontFace = frontFace;
    }

    // --- Following the colony ---

    public void tick(TerrariumBlockEntity terrarium, boolean animate) {
        age++;
        Colony colony = terrarium.colony();
        if (colony.stage() != stage) {
            stage = colony.stage();
            for (int face = 0; face < 4; face++) {
                faces[face] = TunnelLayout.create(seed, face, face == frontFace, stage);
            }
            Arrays.fill(distances, null);
            actors.clear();
            compositionHash = 0;
            itemsHash = 0;
        }
        if (age % 20 == 1) {
            updateComposition(colony);
            updateItems(colony);
        }
        queenLaying = colony.activity().laying() && colony.queen() != null;
        queenPhase += queenLaying ? 1.0F : 0.4F;
        if (queenLaying && age % 60 == 0) {
            eggFlash = 20;
        }
        if (eggFlash > 0) {
            eggFlash--;
        }
        if (evolutionGlow > 0) {
            evolutionGlow = Math.max(0, evolutionGlow - 0.01F);
        }
        if (!animate) {
            return;
        }
        float hunger = colony.activity().starving() ? 0.45F : 1;
        for (Actor actor : actors) {
            if (actor.face == SURFACE) {
                tickSurface(actor, hunger);
            } else {
                tickFace(actor, hunger);
            }
        }
        meet();
    }

    /** Matches the actors to the colony: how many, which castes and jobs, and which species they look like. */
    private void updateComposition(Colony colony) {
        long adults = colony.adults();
        int max = AntConfig.client(AntConfig.MAX_VISUAL_ANTS);
        int count = adults <= 0 ? 0 : (int) Math.min(max, Math.round(3 + 7 * Math.log10(1 + adults)));
        count = (int) Math.min(count, adults);
        int soldiers = adults <= 0 ? 0 : (int) Math.min(count / 3, Math.round(count * colony.soldiers() / (double) adults));
        if (colony.soldiers() > 0 && soldiers == 0 && count > 4) {
            soldiers = 1;
        }
        Colony.Activity activity = colony.activity();
        float conversion = (float) colony.conversion();
        int hash = count * 31 + soldiers;
        hash = hash * 31 + Math.round(activity.scavenging() * 10) + Math.round(activity.farming() * 10) * 7 + Math.round(activity.maintenance() * 10) * 13;
        hash = hash * 31 + Math.round(conversion * 10) + colony.species().hashCode();
        if (hash == compositionHash) {
            return;
        }
        compositionHash = hash;
        AntSpecies species = ClientSpecies.get(colony.species());
        AntLook look = species == null ? AntLook.UNKNOWN : species.look();
        carryColor = look.crystal().map(c -> 0xFF000000 | c).orElse(0xFFD9B26A);
        boolean farmers = species != null && species.canFarm();
        // Keep the ants already walking where possible; add or drop the difference.
        while (actors.size() > count) {
            actors.removeLast();
        }
        while (actors.size() < count) {
            actors.add(spawn());
        }
        float working = activity.scavenging() + activity.processing();
        for (int i = 0; i < actors.size(); i++) {
            Actor actor = actors.get(i);
            boolean soldier = i < soldiers;
            actor.caste = soldier ? Caste.SOLDIER : Caste.WORKER;
            actor.former = colony.formerSpecies() != null && (i * 0.618F + 0.3F) % 1 >= conversion;
            float roll = (i * 0.381966F + 0.17F) % 1;
            Task task;
            if (soldier) {
                task = Task.PATROL;
            } else if (roll < Math.max(0.15F, working * 1.5F)) {
                task = Task.CARRY;
            } else if (farmers && roll < 0.15F + activity.farming() * 2) {
                task = Task.FARM;
            } else if (roll < 0.62F) {
                task = Task.NURSE;
            } else if (roll < 0.8F) {
                task = Task.CLEAN;
            } else {
                task = Task.IDLE;
            }
            if (actor.task != task) {
                actor.task = task;
                actor.carry = 0;
            }
            boolean surface = task == Task.PATROL || task == Task.FARM || task == Task.CARRY && i % 3 == 0 || task == Task.IDLE && i % 2 == 0;
            int face = surface ? SURFACE : task == Task.NURSE ? frontFace : (i % 4);
            if (actor.face != face) {
                place(actor, face);
            }
        }
    }

    private Actor spawn() {
        Actor actor = new Actor();
        actor.seed = random.nextFloat() * 10;
        actor.speed = 0.09F + random.nextFloat() * 0.06F;
        actor.face = -1;
        return actor;
    }

    private void place(Actor actor, int face) {
        actor.face = face;
        if (face == SURFACE) {
            actor.x = 3 + random.nextFloat() * 10;
            actor.z = 3 + random.nextFloat() * 10;
            actor.prevX = actor.x;
            actor.prevZ = actor.z;
            pickSurfaceTarget(actor);
            return;
        }
        TunnelLayout layout = faces[face];
        TunnelLayout.Edge edge = layout.edges.get(random.nextInt(layout.edges.size()));
        actor.edge = edge;
        actor.from = edge.a();
        actor.t = random.nextFloat();
        float[] point = layout.point(edge, actor.from, actor.t, new float[2]);
        actor.u = actor.prevU = point[0];
        actor.v = actor.prevV = point[1];
    }

    /** Eggs, larvae and stored food lying in the chambers, as many as the colony's numbers call for (log scale). */
    private void updateItems(Colony colony) {
        int eggs = colony.eggs() <= 0 ? 0 : (int) Math.min(18, Math.round(2 + 4 * Math.log10(1 + colony.eggs())));
        int larvae = colony.larvae() <= 0 ? 0 : (int) Math.min(14, Math.round(1 + 3.5 * Math.log10(1 + colony.larvae())));
        double capacity = Math.max(1, colony.stats().foodCapacity());
        int crumbs = colony.food() <= 0 ? 0 : (int) Math.min(16, Math.ceil(16 * Math.sqrt(colony.food() / capacity)));
        int hash = eggs * 961 + larvae * 31 + crumbs;
        if (hash == itemsHash) {
            return;
        }
        itemsHash = hash;
        items.clear();
        RandomSource placement = RandomSource.create(seed ^ 0x5DEECE66DL);
        scatter(placement, TunnelLayout.ChamberType.BROOD, eggs, EGG, 0.55F);
        scatter(placement, TunnelLayout.ChamberType.BROOD, larvae, LARVA, 0.75F);
        scatter(placement, TunnelLayout.ChamberType.FOOD, crumbs, CRUMB, 0.6F);
    }

    private void scatter(RandomSource placement, TunnelLayout.ChamberType type, int count, int kind, float size) {
        List<int[]> chambers = new ArrayList<>();
        for (int face = 0; face < 4; face++) {
            TunnelLayout layout = faces[face];
            for (int i = 0; i < layout.nodes.size(); i++) {
                if (layout.nodes.get(i).type() == type) {
                    chambers.add(new int[] {face, i});
                }
            }
        }
        if (chambers.isEmpty()) {
            return;
        }
        for (int n = 0; n < count; n++) {
            int[] chamber = chambers.get(n % chambers.size());
            TunnelLayout.Node node = faces[chamber[0]].nodes.get(chamber[1]);
            float u = node.u() + (placement.nextFloat() - 0.5F) * node.radiusU() * 1.6F;
            // On the chamber's floor, piled a little.
            float v = node.v() - node.radiusV() + size * 0.5F + placement.nextFloat() * node.radiusV() * 0.6F;
            items.add(new Item(chamber[0], u, v, size * (0.85F + placement.nextFloat() * 0.3F), kind));
        }
    }

    // --- Walking the tunnels ---

    private void tickFace(Actor actor, float hunger) {
        actor.prevU = actor.u;
        actor.prevV = actor.v;
        actor.prevPhase = actor.phase;
        actor.prevPitch = actor.pitch;
        if (actor.pause > 0) {
            actor.pause--;
            actor.moving = false;
            if (actor.pause == 0) {
                actor.action = Action.NONE;
            }
            return;
        }
        TunnelLayout layout = faces[actor.face];
        float length = Math.max(0.5F, layout.length(actor.edge));
        float step = actor.speed * hunger * (actor.carry != 0 ? 0.8F : 1);
        actor.t += step / length;
        if (actor.t >= 1) {
            arrive(actor, layout, actor.edge.other(actor.from));
        }
        float[] point = layout.point(actor.edge, actor.from, Math.min(1, actor.t), new float[2]);
        float du = point[0] - actor.u;
        float dv = point[1] - actor.v;
        actor.u = point[0];
        actor.v = point[1];
        if (Math.abs(du) > 1e-4F || Math.abs(dv) > 1e-4F) {
            actor.facingPositive = du >= 0;
            actor.pitch = (float) Math.atan2(dv, Math.abs(du));
        }
        actor.moving = true;
        actor.phase += step * 6.5F;
    }

    private void arrive(Actor actor, TunnelLayout layout, int node) {
        TunnelLayout.ChamberType type = layout.nodes.get(node).type();
        // Jobs: pick up or drop what the task is about.
        switch (actor.task) {
            case CARRY, FARM -> {
                if (type == TunnelLayout.ChamberType.ENTRANCE) {
                    actor.carry = actor.task == Task.FARM ? 0xFF5DAA3C : carryColor;
                } else if (type == TunnelLayout.ChamberType.FOOD || type == TunnelLayout.ChamberType.QUEEN) {
                    actor.carry = 0;
                }
            }
            case NURSE -> {
                if (type == TunnelLayout.ChamberType.QUEEN) {
                    actor.carry = 0xFFF4F0E2;
                } else if (type == TunnelLayout.ChamberType.BROOD) {
                    actor.carry = 0;
                    pause(actor, 25 + random.nextInt(30), Action.TEND);
                }
            }
            case CLEAN -> {
                if (type == TunnelLayout.ChamberType.GALLERY || type == TunnelLayout.ChamberType.BROOD) {
                    actor.carry = 0xFF5A3D25;
                } else if (type == TunnelLayout.ChamberType.ENTRANCE || type == TunnelLayout.ChamberType.WASTE) {
                    actor.carry = 0;
                }
            }
            default -> {
            }
        }
        if (actor.pause == 0 && random.nextFloat() < 0.18F) {
            pause(actor, 15 + random.nextInt(40), random.nextBoolean() ? Action.GROOM : Action.NONE);
        }
        TunnelLayout.ChamberType goal = goal(actor);
        List<TunnelLayout.Edge> exits = layout.edgesAt(node);
        TunnelLayout.Edge next = actor.edge;
        if (!exits.isEmpty()) {
            if (goal != null && random.nextFloat() < 0.8F) {
                int[] distance = distances(actor.face, goal);
                int best = Integer.MAX_VALUE;
                for (TunnelLayout.Edge exit : exits) {
                    int d = distance[exit.other(node)];
                    if (d < best || d == best && random.nextBoolean()) {
                        best = d;
                        next = exit;
                    }
                }
            } else {
                next = exits.get(random.nextInt(exits.size()));
                if (next == actor.edge && exits.size() > 1) {
                    next = exits.get((exits.indexOf(next) + 1) % exits.size());
                }
            }
        }
        actor.edge = next;
        actor.from = node;
        actor.t = 0;
    }

    /** Where an ant's task takes it next. */
    private static TunnelLayout.ChamberType goal(Actor actor) {
        return switch (actor.task) {
            case CARRY, FARM -> actor.carry != 0 ? TunnelLayout.ChamberType.FOOD : TunnelLayout.ChamberType.ENTRANCE;
            case NURSE -> actor.carry != 0 ? TunnelLayout.ChamberType.BROOD : TunnelLayout.ChamberType.QUEEN;
            case CLEAN -> actor.carry != 0 ? TunnelLayout.ChamberType.ENTRANCE : TunnelLayout.ChamberType.GALLERY;
            default -> null;
        };
    }

    /** Steps from each node of a face to the nearest chamber of a type (or anywhere, when there is none). */
    private int[] distances(int face, TunnelLayout.ChamberType type) {
        int key = face * TunnelLayout.ChamberType.values().length + type.ordinal();
        if (distances[key] != null) {
            return distances[key];
        }
        TunnelLayout layout = faces[face];
        int[] distance = new int[layout.nodes.size()];
        Arrays.fill(distance, Integer.MAX_VALUE / 2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < layout.nodes.size(); i++) {
            if (layout.nodes.get(i).type() == type) {
                distance[i] = 0;
                queue.add(i);
            }
        }
        if (queue.isEmpty()) {
            Arrays.fill(distance, 0);
        }
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (TunnelLayout.Edge edge : layout.edgesAt(node)) {
                int other = edge.other(node);
                if (distance[other] > distance[node] + 1) {
                    distance[other] = distance[node] + 1;
                    queue.add(other);
                }
            }
        }
        distances[key] = distance;
        return distance;
    }

    private void pause(Actor actor, int ticks, Action action) {
        actor.pause = ticks;
        actor.action = action;
    }

    /** Two walking ants that meet stop and feed each other (trophallaxis). */
    private void meet() {
        if (age % 5 != 0) {
            return;
        }
        for (int i = 0; i < actors.size(); i++) {
            Actor a = actors.get(i);
            if (a.face == SURFACE || a.pause > 0) {
                continue;
            }
            for (int j = i + 1; j < actors.size(); j++) {
                Actor b = actors.get(j);
                if (b.face != a.face || b.pause > 0) {
                    continue;
                }
                float du = a.u - b.u;
                float dv = a.v - b.v;
                if (du * du + dv * dv < 1.1F && random.nextFloat() < 0.35F) {
                    int ticks = 20 + random.nextInt(20);
                    pause(a, ticks, Action.FEED);
                    pause(b, ticks, Action.FEED);
                    // Face each other.
                    a.facingPositive = b.u > a.u;
                    b.facingPositive = a.u > b.u;
                    a.pitch = 0;
                    b.pitch = 0;
                }
            }
        }
    }

    // --- The surface ---

    private void tickSurface(Actor actor, float hunger) {
        actor.prevX = actor.x;
        actor.prevZ = actor.z;
        actor.prevYaw = actor.yaw;
        actor.prevPhase = actor.phase;
        if (actor.pause > 0) {
            actor.pause--;
            actor.moving = false;
            if (actor.pause == 0) {
                actor.action = Action.NONE;
            }
            return;
        }
        float dx = actor.targetX - actor.x;
        float dz = actor.targetZ - actor.z;
        float distance = Mth.sqrt(dx * dx + dz * dz);
        float speed = actor.speed * hunger * (actor.task == Task.PATROL ? 0.8F : 1);
        if (distance < speed * 1.5F) {
            if (actor.task == Task.CARRY || actor.task == Task.FARM) {
                actor.carry = actor.carry == 0 ? (actor.task == Task.FARM ? 0xFF5DAA3C : carryColor) : 0;
            }
            if (random.nextFloat() < 0.35F) {
                pause(actor, 15 + random.nextInt(35), actor.task == Task.PATROL ? Action.NONE : Action.GROOM);
            }
            pickSurfaceTarget(actor);
            return;
        }
        float targetYaw = (float) Math.atan2(-dx, -dz);
        actor.yaw = Mth.rotLerpRad(0.25F, actor.yaw, targetYaw);
        actor.x += -Mth.sin(actor.yaw) * speed;
        actor.z += -Mth.cos(actor.yaw) * speed;
        actor.x = Math.clamp(actor.x, 2.2F, 13.8F);
        actor.z = Math.clamp(actor.z, 2.2F, 13.8F);
        actor.moving = true;
        actor.phase += speed * 6.5F;
    }

    private void pickSurfaceTarget(Actor actor) {
        if (actor.task == Task.PATROL) {
            // Soldiers walk the edge of the surface.
            float angle = random.nextFloat() * Mth.TWO_PI;
            actor.targetX = 8 + Mth.cos(angle) * 5.2F;
            actor.targetZ = 8 + Mth.sin(angle) * 5.2F;
        } else if ((actor.task == Task.CARRY || actor.task == Task.FARM) && actor.carry != 0) {
            // Home: the middle, where the entrances are.
            actor.targetX = 7 + random.nextFloat() * 2;
            actor.targetZ = 7 + random.nextFloat() * 2;
        } else {
            actor.targetX = 2.6F + random.nextFloat() * 10.8F;
            actor.targetZ = 2.6F + random.nextFloat() * 10.8F;
        }
    }

    public void evolved() {
        evolutionGlow = 1;
    }
}
