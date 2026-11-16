package com.antcolonies.client.nest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * What is carved into the front of one colony block: a 16 x 16 grid of cells (u across, v up), the paths ants walk and
 * the chamber's features. Plain Java: nothing here needs the game.
 */
public final class NestLayout {
    public static final int N = 16;
    /** The sides of the face, as seen from the front. */
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    /** A side's crossing is a cell along it, 2 to 13: the two cells at either end lie under the frame. */
    public static final int GATE_MIN = 2;
    public static final int GATE_SPAN = 12;

    public enum Kind {
        FORMICARIUM, NEST_CHAMBER, FORAGING_SITE, HUNTING_GROUND, HARVESTING_SITE, STORAGE, QUEEN_CHAMBER, NETHER_FORAGING_SITE,
        END_FORAGING_SITE, COLONY_CONNECTOR, LIQUID_CHAMBER
    }

    /** What a node of the ants' paths is. */
    public enum Spot {
        /** The block's own chamber. */
        MAIN,
        /** The Formicarium's side chambers. */
        BROOD, FOOD, WASTE,
        /** Where a tunnel crosses a side of the block, into the next one. */
        GATE,
        /** Where ants come from and go to: a burrow going deeper, a way out, a portal. */
        SOURCE
    }

    public static final class Node {
        public final Spot spot;
        public final float u;
        public final float v;
        /** For a gate, its side; -1 otherwise. */
        public final int side;
        /** For a chamber, the cells inside it (for ants milling about); empty otherwise. */
        public final List<int[]> cells = new ArrayList<>();

        Node(Spot spot, float u, float v, int side) {
            this.spot = spot;
            this.u = u;
            this.v = v;
            this.side = side;
        }
    }

    /** A path between two nodes: a line through points, with the distance walked up to each. */
    public static final class Edge {
        public final int a;
        public final int b;
        public final float[] u;
        public final float[] v;
        public final float[] at;
        public final float length;

        Edge(int a, int b, List<float[]> points) {
            this.a = a;
            this.b = b;
            u = new float[points.size()];
            v = new float[points.size()];
            at = new float[points.size()];
            float total = 0;
            for (int i = 0; i < points.size(); i++) {
                u[i] = points.get(i)[0];
                v[i] = points.get(i)[1];
                if (i > 0) {
                    total += (float) Math.hypot(u[i] - u[i - 1], v[i] - v[i - 1]);
                }
                at[i] = total;
            }
            length = Math.max(total, 0.01F);
        }

        public int other(int node) {
            return node == a ? b : a;
        }

        /** The point {@code distance} along the edge, walked from node {@code from}; {@code out} gets u, v and the direction walked (du, dv). */
        public float[] point(int from, float distance, float[] out) {
            float d = Math.clamp(from == a ? distance : length - distance, 0, length);
            int i = 1;
            while (i < at.length - 1 && at[i] < d) {
                i++;
            }
            float span = at[i] - at[i - 1];
            float t = span <= 1e-5F ? 0 : (d - at[i - 1]) / span;
            out[0] = u[i - 1] + (u[i] - u[i - 1]) * t;
            out[1] = v[i - 1] + (v[i] - v[i - 1]) * t;
            float sign = from == a ? 1 : -1;
            out[2] = (u[i] - u[i - 1]) * sign;
            out[3] = (v[i] - v[i - 1]) * sign;
            return out;
        }
    }

    /** Something in a chamber, at a cell: the letter is the template's (see {@link #TEMPLATES}). */
    public record Feature(char type, int u, int v, int node) {}

    public final Kind kind;
    public final long seed;
    public final int stage;
    /** Per side: where the tunnel crosses it (its lower or left cell), or -1 when nothing of a colony is there. */
    public final int[] gates;
    public final boolean[] carved = new boolean[N * N];
    public final List<Node> nodes = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    public final List<Feature> features = new ArrayList<>();
    /** Left to the renderer: this layout's quads, worked out the first time it is drawn. */
    public Object renderCache;

    /** Per cell: the chamber node it belongs to, -2 for a tunnel, -3 for the burrow, -1 for solid. */
    private final int[] owner = new int[N * N];
    private final float[] noise = new float[N * N];
    private final Random random;
    private int[][] distances;

    // --- The chambers, drawn by hand ---
    //
    // Rows from the top down. '.' is solid; every other character is carved. Letters mark what is there:
    //   q the Queen           e brood (eggs, larvae)     c food crumbs or finds     w waste
    //   k a royal cell        m the golden dais          i an item on a shelf       s a shelf
    //   f a fungus            b bones                    r a full Liquid Ant hangs here
    //   l the pool            o portal frame             p portal                   g the pheromone gland
    //   h a shaft going down

    private static final String[] QUEEN_SMALL = {
            "..####..",
            ".######.",
            "########",
            "###q####"};
    private static final String[] QUEEN_LARGE = {
            "...####...",
            ".########.",
            "##########",
            "##########",
            "####q#####"};
    private static final String[] BROOD_POCKET = {
            ".##.",
            "####",
            "eeee"};
    private static final String[] FOOD_POCKET = {
            ".##.",
            "####",
            "cccc"};
    private static final String[] WASTE_POCKET = {
            "##",
            "ww"};

    /** The chamber of each kind of block (the Formicarium's is one of the two Queen's chambers, by the colony's stage). */
    public static final String[][] TEMPLATES = {
            QUEEN_SMALL,
            // Nest Chamber: two brood galleries, one over the other.
            {"..######..",
             ".eeeeeeee.",
             "...####...",
             ".########.",
             "##########",
             "eeeeeeeeee"},
            // Foraging Site: a low hall where the finds are piled.
            {"..#####..",
             "#########",
             "#########",
             "#cc#ccc##"},
            // Hunting Ground: a wide arena, bones of old catches along the floor.
            {".########.",
             "##########",
             "##########",
             "##########",
             "bb###b##bb"},
            // Harvesting Site: the fungus garden.
            {"..######..",
             ".########.",
             "##########",
             "##########",
             "#f#ff#f#f#"},
            // Storage: a granary with a shelf, three items a tier.
            {"..#######..",
             ".#########.",
             "###########",
             "##i##i##i##",
             "sssssssssss",
             "###########",
             "##i##i##i##"},
            // Queen Chamber: three royal cells over a golden dais.
            {".kk.kk.kk.",
             ".kk.kk.kk.",
             "##########",
             "##########",
             "##mmmmmm##"},
            // Nether and End Foraging Sites: a little portal standing in the chamber.
            {".#######.",
             "##oooo###",
             "##oppo###",
             "##oppo###",
             "##oppo###",
             "##oooo#c#"},
            {".#######.",
             "###oooo##",
             "###oppo##",
             "###oppo##",
             "###oppo##",
             "#c#oooo##"},
            // Colony Connector: the pheromone gland, and a shaft going down to the trail.
            {".######.",
             "########",
             "##g#####",
             "#####hhh",
             "#####hhh"},
            // Liquid Chamber: full ants hanging from the ceiling over the pool.
            {".r#r#r#r.",
             "#########",
             "#########",
             "#########",
             "#########",
             ".lllllll."}};

    private NestLayout(Kind kind, long seed, int[] gates, int stage) {
        this.kind = kind;
        this.seed = seed;
        this.gates = gates.clone();
        this.stage = stage;
        this.random = new Random(mix(seed * 31 + kind.ordinal()));
        Arrays.fill(owner, -1);
    }

    /**
     * @param gates per side (LEFT, RIGHT, UP, DOWN) the cell where the tunnel crosses it, or -1
     * @param stage the colony's stage, 1 to 4 (only the Formicarium grows with it)
     */
    public static NestLayout create(Kind kind, long seed, int[] gates, int stage) {
        NestLayout layout = new NestLayout(kind, seed, gates, kind == Kind.FORMICARIUM ? Math.clamp(stage, 1, 4) : 1);
        layout.dig();
        return layout;
    }

    private void dig() {
        // Soft noise for the tunnels' way: low hills and valleys, with a little grit.
        float[][] lattice = new float[5][5];
        for (float[] row : lattice) {
            for (int i = 0; i < row.length; i++) {
                row[i] = random.nextFloat();
            }
        }
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                float gx = u / 4F;
                float gy = v / 4F;
                int x0 = (int) gx;
                int y0 = (int) gy;
                float tx = gx - x0;
                float ty = gy - y0;
                float smooth = (lattice[y0][x0] * (1 - tx) + lattice[y0][x0 + 1] * tx) * (1 - ty)
                        + (lattice[y0 + 1][x0] * (1 - tx) + lattice[y0 + 1][x0 + 1] * tx) * ty;
                noise[v * N + u] = smooth * 0.85F + random.nextFloat() * 0.15F;
            }
        }
        boolean mirror = random.nextBoolean();
        int main;
        if (kind == Kind.FORMICARIUM) {
            String[] queen = stage >= 3 ? QUEEN_LARGE : QUEEN_SMALL;
            main = stamp(queen, (N - queen[0].length()) / 2, 2, mirror, Spot.MAIN);
            // The nest grows with the colony's stage: a brood chamber first, then a food store, then a midden.
            int brood = stamp(BROOD_POCKET, mirror ? 10 : 2, 10 + random.nextInt(2), false, Spot.BROOD);
            tunnel(brood, main);
            if (stage >= 2) {
                int food = stamp(FOOD_POCKET, mirror ? 2 : 10, 8 + random.nextInt(2), false, Spot.FOOD);
                tunnel(food, main);
            }
            if (stage >= 3) {
                int waste = stamp(WASTE_POCKET, 7, 12, false, Spot.WASTE);
                tunnel(waste, main);
            }
        } else {
            String[] template = TEMPLATES[kind.ordinal()];
            int w = template[0].length();
            int h = template.length;
            int x0 = Math.clamp((N - w) / 2 + random.nextInt(3) - 1, 2, 14 - w);
            int v0 = Math.clamp((N - h) / 2 + random.nextInt(3) - 1, 2, 14 - h);
            main = stamp(template, x0, v0, mirror, Spot.MAIN);
        }
        source(main);
        for (int side = 0; side < 4; side++) {
            if (gates[side] >= 0) {
                gate(side, Math.clamp(gates[side], GATE_MIN, GATE_MIN + GATE_SPAN - 1));
            }
        }
    }

    // --- Chambers ---

    /** Carves a template with its lower left cell at (x0, v0); returns its node. */
    private int stamp(String[] rows, int x0, int v0, boolean mirror, Spot spot) {
        int w = rows[0].length();
        int h = rows.length;
        int index = nodes.size();
        List<int[]> cells = new ArrayList<>();
        float sumU = 0;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                char c = rows[row].charAt(mirror ? w - 1 - col : col);
                if (c == '.') {
                    continue;
                }
                int u = x0 + col;
                int v = v0 + (h - 1 - row);
                carved[v * N + u] = true;
                owner[v * N + u] = index;
                cells.add(new int[] {u, v});
                sumU += u + 0.5F;
                if (c != '#') {
                    features.add(new Feature(c, u, v, index));
                }
            }
        }
        // Its hub, where the paths meet: on the chamber's floor, under its middle.
        float hubU = sumU / cells.size();
        int[] nearest = cells.getFirst();
        float best = Float.MAX_VALUE;
        for (int[] cell : cells) {
            if (owner[(cell[1] - 1) * N + cell[0]] == index) {
                continue; // not on the floor
            }
            float d = Math.abs(cell[0] + 0.5F - hubU) * 4 + cell[1];
            if (d < best) {
                best = d;
                nearest = cell;
            }
        }
        Node node = new Node(spot, nearest[0] + 0.5F, nearest[1] + 0.5F, -1);
        node.cells.addAll(cells);
        nodes.add(node);
        return index;
    }

    /** Where the block's ants come from and go to: its portal or shaft, or else a burrow at the end of a short tunnel. */
    private void source(int main) {
        float sumU = 0;
        float sumV = 0;
        int count = 0;
        for (Feature feature : features) {
            if (feature.type() == 'p' || feature.type() == 'h') {
                sumU += feature.u() + 0.5F;
                sumV += feature.v() + 0.5F;
                count++;
            }
        }
        if (count > 0) {
            int index = nodes.size();
            nodes.add(new Node(Spot.SOURCE, sumU / count, sumV / count, -1));
            Node hub = nodes.get(main);
            edges.add(new Edge(index, main, List.of(new float[] {sumU / count, sumV / count}, new float[] {hub.u, hub.v})));
            return;
        }
        // A free 2 x 2 spot with solid nest all round it, as far from what is carved as there is room for.
        int bestX = -1;
        int bestY = -1;
        float best = -1;
        for (int ay = 2; ay <= 12; ay++) {
            for (int ax = 2; ax <= 12; ax++) {
                float nearest = Float.MAX_VALUE;
                for (int v = 0; v < N; v++) {
                    for (int u = 0; u < N; u++) {
                        if (carved[v * N + u]) {
                            float du = Math.max(0, Math.max(ax - u, u - (ax + 1)));
                            float dv = Math.max(0, Math.max(ay - v, v - (ay + 1)));
                            nearest = Math.min(nearest, Math.max(du, dv));
                        }
                    }
                }
                if (nearest < 3) {
                    continue;
                }
                float score = Math.min(nearest, 5) + random.nextFloat() * 2.5F;
                if (score > best) {
                    best = score;
                    bestX = ax;
                    bestY = ay;
                }
            }
        }
        if (bestX < 0) {
            // No room: the burrow opens in the chamber's own floor.
            Node hub = nodes.get(main);
            int index = nodes.size();
            int[] cell = hub.cells.get(random.nextInt(hub.cells.size()));
            features.add(new Feature('H', cell[0], cell[1], index));
            nodes.add(new Node(Spot.SOURCE, cell[0] + 0.5F, cell[1] + 0.5F, -1));
            edges.add(new Edge(index, main, List.of(new float[] {cell[0] + 0.5F, cell[1] + 0.5F}, new float[] {hub.u, hub.v})));
            return;
        }
        int index = nodes.size();
        List<int[]> starts = new ArrayList<>();
        for (int dv = 0; dv < 2; dv++) {
            for (int du = 0; du < 2; du++) {
                int cell = (bestY + dv) * N + bestX + du;
                carved[cell] = true;
                owner[cell] = -3;
                features.add(new Feature('H', bestX + du, bestY + dv, index));
                starts.add(new int[] {bestX + du, bestY + dv});
            }
        }
        nodes.add(new Node(Spot.SOURCE, bestX + 1, bestY + 1, -1));
        route(index, starts, new float[] {bestX + 1, bestY + 1}, -1);
    }

    private void gate(int side, int at) {
        int index = nodes.size();
        int[] start;
        float[] point;
        switch (side) {
            case LEFT -> {
                start = new int[] {0, at};
                point = new float[] {0, at + 0.5F};
            }
            case RIGHT -> {
                start = new int[] {N - 1, at};
                point = new float[] {N, at + 0.5F};
            }
            case UP -> {
                start = new int[] {at, N - 1};
                point = new float[] {at + 0.5F, N};
            }
            default -> {
                start = new int[] {at, 0};
                point = new float[] {at + 0.5F, 0};
            }
        }
        nodes.add(new Node(Spot.GATE, point[0], point[1], side));
        route(index, List.of(start), point, -1);
    }

    private void tunnel(int from, int to) {
        Node node = nodes.get(from);
        route(from, node.cells, new float[] {node.u, node.v}, to);
    }

    // --- Digging a tunnel ---

    /**
     * Digs a tunnel from the start cells to chamber {@code to} (the nearest when -1), by the cheapest way. It keeps a cell
     * of wall from other chambers unless there is no other way.
     */
    private void route(int from, List<int[]> starts, float[] first, int to) {
        for (int pass = 0; pass < 2; pass++) {
            boolean strict = pass == 0;
            float[] cost = new float[N * N];
            int[] previous = new int[N * N];
            Arrays.fill(cost, Float.MAX_VALUE);
            Arrays.fill(previous, -1);
            PriorityQueue<float[]> queue = new PriorityQueue<>((x, y) -> Float.compare(x[0], y[0]));
            for (int[] start : starts) {
                int id = start[1] * N + start[0];
                cost[id] = 0;
                queue.add(new float[] {0, id});
            }
            int found = -1;
            while (!queue.isEmpty()) {
                float[] head = queue.poll();
                int id = (int) head[1];
                if (head[0] > cost[id]) {
                    continue;
                }
                int chamber = owner[id];
                if (chamber >= 0 && chamber != from && (to < 0 || chamber == to)) {
                    found = id;
                    break;
                }
                int u = id % N;
                int v = id / N;
                for (int d = 0; d < 4; d++) {
                    int nu = u + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nv = v + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nu < 0 || nv < 0 || nu >= N || nv >= N) {
                        continue;
                    }
                    float step = stepCost(nu, nv, from, to, strict);
                    if (step < 0) {
                        continue;
                    }
                    int next = nv * N + nu;
                    if (cost[id] + step < cost[next]) {
                        cost[next] = cost[id] + step;
                        previous[next] = id;
                        queue.add(new float[] {cost[next], next});
                    }
                }
            }
            if (found < 0) {
                continue;
            }
            List<int[]> path = new ArrayList<>();
            for (int id = found; id >= 0; id = previous[id]) {
                path.addFirst(new int[] {id % N, id / N});
            }
            int target = owner[found];
            // Leave out the steps inside the chambers at either end: the path goes straight to their hubs.
            while (path.size() > 1 && owner[path.get(1)[1] * N + path.get(1)[0]] == from) {
                path.removeFirst();
            }
            List<float[]> points = new ArrayList<>();
            points.add(first);
            for (int[] cell : path) {
                int index = cell[1] * N + cell[0];
                if (!carved[index]) {
                    carved[index] = true;
                    owner[index] = -2;
                }
                points.add(new float[] {cell[0] + 0.5F, cell[1] + 0.5F});
            }
            Node hub = nodes.get(target);
            points.add(new float[] {hub.u, hub.v});
            edges.add(new Edge(from, target, smooth(points)));
            return;
        }
    }

    /** What it costs to dig into a cell, or -1 when the tunnel may not go there. */
    private float stepCost(int u, int v, int from, int to, boolean strict) {
        int o = owner[v * N + u];
        if (o >= 0) {
            return o != from && o != to && to >= 0 && strict ? -1 : 0.1F;
        }
        if (o == -2) {
            return 0.15F;
        }
        if (o == -3) {
            return strict ? -1 : 0.15F;
        }
        // The two cells along every side stay solid (the frame lies over them), but for the tunnels that cross a side.
        if ((u < 2 || u > N - 3 || v < 2 || v > N - 3) && !corridor(u, v)) {
            return -1;
        }
        if (strict) {
            // A cell of wall between the tunnel and the chambers (and the burrow) it is not going to.
            for (int dv = -1; dv <= 1; dv++) {
                for (int du = -1; du <= 1; du++) {
                    int nu = u + du;
                    int nv = v + dv;
                    if (nu >= 0 && nv >= 0 && nu < N && nv < N) {
                        int near = owner[nv * N + nu];
                        if (near == -3 && from >= 0 && nodes.get(from).spot != Spot.SOURCE
                                || near >= 0 && near != from && to >= 0 && near != to) {
                            return -1;
                        }
                    }
                }
            }
        }
        return 1 + noise[v * N + u] * 1.6F;
    }

    /** Whether a cell near a side is in the tunnel that crosses that side. */
    private boolean corridor(int u, int v) {
        if (u < 2 && v == gates[LEFT] || u > N - 3 && v == gates[RIGHT]) {
            return true;
        }
        return v > N - 3 && u == gates[UP] || v < 2 && u == gates[DOWN];
    }

    /** Rounds a path's corners (twice), keeping its ends: the ants turn through a tunnel's bends, not on the spot. */
    private static List<float[]> smooth(List<float[]> points) {
        List<float[]> line = points;
        for (int pass = 0; pass < 2 && line.size() > 2; pass++) {
            List<float[]> next = new ArrayList<>();
            next.add(line.getFirst());
            for (int i = 0; i < line.size() - 1; i++) {
                float[] p = line.get(i);
                float[] q = line.get(i + 1);
                if (i > 0) {
                    next.add(new float[] {p[0] * 0.75F + q[0] * 0.25F, p[1] * 0.75F + q[1] * 0.25F});
                }
                if (i < line.size() - 2) {
                    next.add(new float[] {p[0] * 0.25F + q[0] * 0.75F, p[1] * 0.25F + q[1] * 0.75F});
                }
            }
            next.add(line.getLast());
            line = next;
        }
        return line;
    }

    // --- For the ants ---

    public boolean carved(int u, int v) {
        return u >= 0 && v >= 0 && u < N && v < N && carved[v * N + u];
    }

    /** Whether a cell is carved and stands on solid nest: somewhere an ant can stand. */
    public boolean floor(int u, int v) {
        return carved(u, v) && !carved(u, v - 1);
    }

    public int find(Spot spot) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).spot == spot) {
                return i;
            }
        }
        return -1;
    }

    public int gate(int side) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).spot == Spot.GATE && nodes.get(i).side == side) {
                return i;
            }
        }
        return -1;
    }

    public List<Edge> edgesAt(int node) {
        List<Edge> list = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.a == node || edge.b == node) {
                list.add(edge);
            }
        }
        return list;
    }

    /** Steps from every node to a node (through the edges). */
    public int[] distances(int to) {
        if (distances == null) {
            distances = new int[nodes.size()][];
        }
        if (distances[to] == null) {
            int[] d = new int[nodes.size()];
            Arrays.fill(d, Integer.MAX_VALUE / 2);
            d[to] = 0;
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Edge edge : edges) {
                    if (d[edge.a] + 1 < d[edge.b]) {
                        d[edge.b] = d[edge.a] + 1;
                        changed = true;
                    }
                    if (d[edge.b] + 1 < d[edge.a]) {
                        d[edge.a] = d[edge.b] + 1;
                        changed = true;
                    }
                }
            }
            distances[to] = d;
        }
        return distances[to];
    }

    /** Whether the straight line between two points stays in what is carved. */
    public boolean clear(float u0, float v0, float u1, float v1) {
        int steps = (int) Math.ceil(Math.hypot(u1 - u0, v1 - v0) * 3) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            if (!carved((int) Math.floor(u0 + (u1 - u0) * t), (int) Math.floor(v0 + (v1 - v0) * t))) {
                return false;
            }
        }
        return true;
    }

    // --- Where the tunnels cross the sides ---

    /**
     * Where the tunnel between two touching blocks crosses their shared side, the same for both. {@code lower}: the
     * packed position of the one with the smaller coordinate along {@code axis} (0 x, 1 y, 2 z).
     */
    public static int crossing(long lower, int axis) {
        return (int) Math.floorMod(mix(lower * 3 + axis), GATE_SPAN);
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The layout as text (rows from the top down), for looking at it without the game. */
    public String dump() {
        StringBuilder out = new StringBuilder();
        for (int v = N - 1; v >= 0; v--) {
            for (int u = 0; u < N; u++) {
                char c = carved[v * N + u] ? (owner[v * N + u] >= 0 ? (char) ('A' + owner[v * N + u]) : '+') : '.';
                for (Feature feature : features) {
                    if (feature.u() == u && feature.v() == v) {
                        c = feature.type();
                    }
                }
                out.append(c);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
