package com.antcolonies.client.terrarium;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;

/**
 * The tunnels and chambers seen through one glass side of a Terrarium, in that side's own 2D coordinates: u across
 * (1 to 15 px), v up (1 px at the bottom to {@link #SURFACE} at the soil's surface). Made from the Terrarium's position
 * (so every Terrarium digs its own way) and the colony's stage: more and bigger chambers as the colony grows.
 *
 * <p>The front side holds the Queen's chamber; the other sides hold the brood, the food stores and the waste chamber.
 */
public final class TunnelLayout {
    public static final float SURFACE = 11;
    public static final float MIN_U = 1.6F;
    public static final float MAX_U = 14.4F;

    public enum ChamberType {
        ENTRANCE, QUEEN, BROOD, FOOD, WASTE, GALLERY, CRYSTAL
    }

    public record Node(float u, float v, float radiusU, float radiusV, ChamberType type) {}

    /** A tunnel between two nodes, bent through a middle point. */
    public record Edge(int a, int b, float midU, float midV) {
        public int other(int node) {
            return node == a ? b : a;
        }
    }

    public final List<Node> nodes = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    /** Left to the renderer: this layout's tunnel quads, worked out the first time it is drawn (client only). */
    public @org.jspecify.annotations.Nullable Object renderCache;
    public final boolean front;
    public final int stage;

    private TunnelLayout(boolean front, int stage) {
        this.front = front;
        this.stage = stage;
    }

    public static TunnelLayout create(long seed, int face, boolean front, int stage) {
        RandomSource random = RandomSource.create(seed * 31 + face * 7919L);
        TunnelLayout layout = new TunnelLayout(front, stage);
        float jitter = 1.2F;
        int entrance = layout.node(4 + random.nextFloat() * 8, SURFACE - 0.4F, 1.1F, 0.8F, ChamberType.ENTRANCE);
        int hub = layout.node(5 + random.nextFloat() * 6, 7.5F + random.nextFloat() * 1.2F, 1.2F, 0.9F, ChamberType.GALLERY);
        layout.edge(entrance, hub, random, jitter);
        if (front) {
            int queen = layout.node(8 + (random.nextFloat() - 0.5F) * 3, 3.0F, 3.2F + stage * 0.25F, 1.6F + stage * 0.1F, ChamberType.QUEEN);
            int brood = layout.node(random.nextBoolean() ? 3.2F : 12.8F, 4.8F + random.nextFloat(), 1.8F + stage * 0.2F, 1.1F, ChamberType.BROOD);
            layout.edge(hub, queen, random, jitter);
            layout.edge(queen, brood, random, jitter * 0.6F);
            layout.edge(hub, brood, random, jitter);
            if (stage >= 3) {
                int second = layout.node(layout.nodes.get(brood).u() > 8 ? 3.4F : 12.6F, 6.8F, 1.6F, 1.0F, ChamberType.BROOD);
                layout.edge(hub, second, random, jitter);
                layout.edge(second, queen, random, jitter);
            }
        } else {
            int food = layout.node(3 + random.nextFloat() * 3, 4 + random.nextFloat() * 2, 1.8F + stage * 0.2F, 1.1F + stage * 0.08F, ChamberType.FOOD);
            int brood = layout.node(10 + random.nextFloat() * 3, 3 + random.nextFloat() * 2, 1.6F + stage * 0.15F, 1.0F, ChamberType.BROOD);
            layout.edge(hub, food, random, jitter);
            layout.edge(hub, brood, random, jitter);
            if (stage >= 2) {
                layout.edge(food, brood, random, jitter);
                int waste = layout.node(13 + random.nextFloat(), 8.4F, 1.1F, 0.8F, ChamberType.WASTE);
                layout.edge(hub, waste, random, jitter);
                int second = layout.node(1.8F + random.nextFloat() * 1.5F, SURFACE - 0.4F, 0.9F, 0.7F, ChamberType.ENTRANCE);
                layout.edge(second, food, random, jitter);
            }
            if (stage >= 3) {
                int gallery = layout.node(7 + random.nextFloat() * 2, 1.8F, 2.0F, 0.9F, ChamberType.FOOD);
                layout.edge(food, gallery, random, jitter);
                layout.edge(gallery, brood, random, jitter);
            }
        }
        if (stage >= 4) {
            int crystal = layout.node(front ? 2.6F : 12.4F, 1.9F, 1.4F, 0.9F, ChamberType.CRYSTAL);
            layout.edge(nearest(layout, crystal), crystal, random, jitter);
        }
        return layout;
    }

    private int node(float u, float v, float radiusU, float radiusV, ChamberType type) {
        nodes.add(new Node(Math.clamp(u, MIN_U + radiusU * 0.5F, MAX_U - radiusU * 0.5F), Math.clamp(v, 1.2F + radiusV, SURFACE), radiusU, radiusV, type));
        return nodes.size() - 1;
    }

    private void edge(int a, int b, RandomSource random, float jitter) {
        Node from = nodes.get(a);
        Node to = nodes.get(b);
        float midU = (from.u() + to.u()) / 2 + (random.nextFloat() - 0.5F) * 2 * jitter;
        float midV = (from.v() + to.v()) / 2 + (random.nextFloat() - 0.5F) * jitter;
        edges.add(new Edge(a, b, Math.clamp(midU, MIN_U, MAX_U), Math.clamp(midV, 1.5F, SURFACE - 0.5F)));
    }

    private static int nearest(TunnelLayout layout, int to) {
        Node target = layout.nodes.get(to);
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < layout.nodes.size(); i++) {
            if (i == to) {
                continue;
            }
            Node node = layout.nodes.get(i);
            float distance = (node.u() - target.u()) * (node.u() - target.u()) + (node.v() - target.v()) * (node.v() - target.v());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** The point at {@code t} (0..1) along an edge from {@code from}: a quadratic curve through its middle point. */
    public float[] point(Edge edge, int from, float t, float[] out) {
        Node a = nodes.get(from);
        Node b = nodes.get(edge.other(from));
        float s = 1 - t;
        out[0] = s * s * a.u() + 2 * s * t * edge.midU() + t * t * b.u();
        out[1] = s * s * a.v() + 2 * s * t * edge.midV() + t * t * b.v();
        return out;
    }

    /** A rough length of an edge, px. */
    public float length(Edge edge) {
        Node a = nodes.get(edge.a());
        Node b = nodes.get(edge.b());
        float d1 = (float) Math.hypot(edge.midU() - a.u(), edge.midV() - a.v());
        float d2 = (float) Math.hypot(b.u() - edge.midU(), b.v() - edge.midV());
        return d1 + d2;
    }

    public List<Edge> edgesAt(int node) {
        List<Edge> list = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.a() == node || edge.b() == node) {
                list.add(edge);
            }
        }
        return list;
    }

    public int find(ChamberType type) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).type() == type) {
                return i;
            }
        }
        return -1;
    }
}
