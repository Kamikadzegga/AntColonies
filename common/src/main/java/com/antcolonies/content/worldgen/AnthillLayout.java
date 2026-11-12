package com.antcolonies.content.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The plan of one anthill's nest, built from modular pieces: rooms (ellipsoids of a {@link RoomType}) joined by
 * tunnels (capsules). Every layout is a tree grown from the entrance shaft: each room is dug off a room or tunnel junction
 * already joined to the entrance, so every room (the Queen's chamber above all) can be reached, and rooms never cut
 * into each other. The plan depends only on its seed and style, so every chunk of the anthill builds its part of the
 * same nest. Coordinates are relative to the anthill's center (the ground's top block under the mound's peak).
 *
 * <p>Styles: {@link Style#BRANCHING} (rooms dug off the shaft in all directions), {@link Style#DEEP} (a long spine with
 * rooms off both sides), {@link Style#MULTI_LEVEL} (floors of rooms joined by ramps), {@link Style#SPIRAL} (rooms down a
 * spiral), {@link Style#CENTRAL} (rooms around a great hall), {@link Style#WINGS} (three wings off a gallery) and
 * {@link Style#ABANDONED} (a small nest left by its colony: only its Queen stayed). {@link Style#CLASSIC} is the one
 * fixed layout anthills had before, kept so anthills already in a world finish building the same way.
 */
public final class AnthillLayout {
    public enum Style {
        CLASSIC, BRANCHING, DEEP, MULTI_LEVEL, SPIRAL, CENTRAL, WINGS, ABANDONED;

        public static final Style[] RANDOM = {BRANCHING, DEEP, MULTI_LEVEL, SPIRAL, CENTRAL, WINGS, ABANDONED};

        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Style byName(String name) {
            for (Style style : values()) {
                if (style.getSerializedName().equals(name)) {
                    return style;
                }
            }
            return CLASSIC;
        }
    }

    /** What a room is for: it decides how the room is furnished and who lives in it. */
    public enum RoomType {
        QUEEN, NURSERY, EGG_STORAGE, LARVAL, FOOD_STORAGE, FUNGAL_GARDEN, BARRACKS, CHITIN_STORAGE, TREASURE, ABANDONED, RARE, GALLERY, HALL
    }

    public record Room(RoomType type, double x, double y, double z, double rx, double ry, double rz) {
        boolean overlaps(Room other, double gap) {
            double dx = Math.abs(x - other.x) - (rx + other.rx + gap);
            double dy = Math.abs(y - other.y) - (ry + other.ry + gap);
            double dz = Math.abs(z - other.z) - (rz + other.rz + gap);
            // Boxes around the ellipsoids: a little stricter than needed, so walls between rooms stay whole.
            return dx < 0 && dy < 0 && dz < 0;
        }

        /** The floor of the room under its middle. */
        public double floorY() {
            return y - ry;
        }
    }

    public record Tunnel(double x0, double y0, double z0, double x1, double y1, double z1, double radius) {}

    /** The entrance shaft runs from above the mound down to here. */
    public static final double SHAFT_BOTTOM = -3;
    public static final double TUNNEL = 1.1;
    /** How far the plan may reach from the center, and how deep. */
    public static final int REACH = 20;

    private final Style style;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Tunnel> tunnels = new ArrayList<>();
    private final double[] min = {-8, 0, -8};
    private final double[] max = {8, 0, 8};

    private AnthillLayout(Style style) {
        this.style = style;
    }

    public Style style() {
        return style;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<Tunnel> tunnels() {
        return tunnels;
    }

    public Room queen() {
        return rooms.stream().filter(room -> room.type() == RoomType.QUEEN).findFirst().orElseThrow();
    }

    /** The lowest point dug (relative y), for the structure's box and the depth check. */
    public double depth() {
        return min[1];
    }

    public int minX() {
        return Mth.floor(min[0]) - 2;
    }

    public int maxX() {
        return Mth.ceil(max[0]) + 2;
    }

    public int minZ() {
        return Mth.floor(min[2]) - 2;
    }

    public int maxZ() {
        return Mth.ceil(max[2]) + 2;
    }

    public int minY() {
        return Mth.floor(min[1]) - 2;
    }

    // --- Shape ---

    public boolean carved(double x, double y, double z) {
        return inside(x, y, z, 0);
    }

    public boolean shell(double x, double y, double z) {
        return !carved(x, y, z) && inside(x, y, z, 1);
    }

    private boolean inside(double x, double y, double z, double grow) {
        for (Room c : rooms) {
            double dx = (x - c.x) / (c.rx + grow);
            double dy = (y - c.y) / (c.ry + grow);
            double dz = (z - c.z) / (c.rz + grow);
            if (dx * dx + dy * dy + dz * dz <= 1) {
                return true;
            }
        }
        for (Tunnel t : tunnels) {
            if (segmentDistance(x, y, z, t) <= t.radius + grow) {
                return true;
            }
        }
        return false;
    }

    private static double segmentDistance(double x, double y, double z, Tunnel t) {
        double vx = t.x1 - t.x0;
        double vy = t.y1 - t.y0;
        double vz = t.z1 - t.z0;
        double length = vx * vx + vy * vy + vz * vz;
        double f = length == 0 ? 0 : Math.clamp(((x - t.x0) * vx + (y - t.y0) * vy + (z - t.z0) * vz) / length, 0, 1);
        double dx = x - (t.x0 + vx * f);
        double dy = y - (t.y0 + vy * f);
        double dz = z - (t.z0 + vz * f);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The room a spot (relative) lies in, or null in a tunnel or outside. */
    public Room roomAt(double x, double y, double z) {
        for (Room c : rooms) {
            double dx = (x - c.x) / c.rx;
            double dy = (y - c.y) / c.ry;
            double dz = (z - c.z) / c.rz;
            if (dx * dx + dy * dy + dz * dz <= 1) {
                return c;
            }
        }
        return null;
    }

    // --- Building the plan ---

    private void add(Room room) {
        rooms.add(room);
        grow(room.x - room.rx, room.y - room.ry, room.z - room.rz);
        grow(room.x + room.rx, room.y + room.ry, room.z + room.rz);
    }

    private void tunnel(double x0, double y0, double z0, double x1, double y1, double z1, double radius) {
        tunnels.add(new Tunnel(x0, y0, z0, x1, y1, z1, radius));
        grow(Math.min(x0, x1) - radius, Math.min(y0, y1) - radius, Math.min(z0, z1) - radius);
        grow(Math.max(x0, x1) + radius, Math.max(y0, y1) + radius, Math.max(z0, z1) + radius);
    }

    /** A tunnel from a point to a room, bent once on the way so it doesn't run dead straight. */
    private void dig(double x0, double y0, double z0, Room to, RandomSource random) {
        double mx = (x0 + to.x) / 2 + (random.nextDouble() - 0.5) * 3;
        double my = (y0 + to.y) / 2;
        double mz = (z0 + to.z) / 2 + (random.nextDouble() - 0.5) * 3;
        tunnel(x0, y0, z0, mx, my, mz, TUNNEL);
        tunnel(mx, my, mz, to.x, to.y, to.z, TUNNEL);
    }

    private void dig(Room from, Room to, RandomSource random) {
        dig(from.x, from.y, from.z, to, random);
    }

    private void grow(double x, double y, double z) {
        min[0] = Math.min(min[0], x);
        min[1] = Math.min(min[1], y);
        min[2] = Math.min(min[2], z);
        max[0] = Math.max(max[0], x);
        max[1] = Math.max(max[1], y);
        max[2] = Math.max(max[2], z);
    }

    /** Whether a room fits: inside the reach, not under the depth limit, and clear of every other room. */
    private boolean fits(Room room, int maxDepth) {
        if (Math.abs(room.x) + room.rx > REACH || Math.abs(room.z) + room.rz > REACH || room.y - room.ry < -maxDepth || room.y + room.ry > -2) {
            return false;
        }
        for (Room other : rooms) {
            if (room.overlaps(other, 1.5)) {
                return false;
            }
        }
        return true;
    }

    private static Room room(RoomType type, double x, double y, double z, RandomSource random) {
        return switch (type) {
            case QUEEN -> new Room(type, x, y, z, 4.5, 2.5, 4);
            case HALL -> new Room(type, x, y, z, 6, 3.5, 6);
            case FUNGAL_GARDEN, BARRACKS -> new Room(type, x, y, z, 3.5 + random.nextDouble(), 2, 3 + random.nextDouble());
            case TREASURE, RARE -> new Room(type, x, y, z, 2.5, 1.8, 2.5);
            default -> new Room(type, x, y, z, 3 + random.nextDouble() * 0.8, 2, 2.5 + random.nextDouble() * 0.8);
        };
    }

    /** The anthill's plan for a style, from its seed. */
    public static AnthillLayout create(Style style, long seed) {
        AnthillLayout layout = new AnthillLayout(style);
        RandomSource random = RandomSource.create(seed);
        // The entrance shaft, down through the mound.
        layout.tunnel(0, AnthillPiece.HEIGHT + 1, 0, 0, SHAFT_BOTTOM, 0, 0.6);
        switch (style) {
            case CLASSIC -> classic(layout);
            case BRANCHING -> branching(layout, random);
            case DEEP -> deep(layout, random);
            case MULTI_LEVEL -> multiLevel(layout, random);
            case SPIRAL -> spiral(layout, random);
            case CENTRAL -> central(layout, random);
            case WINGS -> wings(layout, random);
            case ABANDONED -> abandoned(layout, random);
        }
        return layout;
    }

    /** The rooms of a living nest besides the Queen's, in a shuffled order (the first ones are dug nearest the entrance). */
    private static List<RoomType> livingRooms(RandomSource random, int count) {
        List<RoomType> pool = new ArrayList<>(List.of(RoomType.NURSERY, RoomType.FOOD_STORAGE, RoomType.FUNGAL_GARDEN, RoomType.BARRACKS,
                RoomType.EGG_STORAGE, RoomType.LARVAL, RoomType.CHITIN_STORAGE, RoomType.TREASURE, RoomType.RARE, RoomType.GALLERY));
        List<RoomType> out = new ArrayList<>();
        // A nest always has a nursery and somewhere to keep its food.
        out.add(RoomType.NURSERY);
        out.add(RoomType.FOOD_STORAGE);
        pool.remove(RoomType.NURSERY);
        pool.remove(RoomType.FOOD_STORAGE);
        while (out.size() < count && !pool.isEmpty()) {
            out.add(pool.remove(random.nextInt(pool.size())));
        }
        for (int i = out.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            RoomType swap = out.get(i);
            out.set(i, out.get(j));
            out.set(j, swap);
        }
        return out;
    }

    /**
     * Places a room near a target spot (a few tries, nudged each time), dug from the nearest joined point; returns it, or
     * null if no try fits.
     */
    private Room place(RoomType type, double x, double y, double z, int maxDepth, RandomSource random, Room from) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double jitter = attempt * 0.8;
            Room room = room(type, x + (random.nextDouble() - 0.5) * jitter, y - attempt * 0.3, z + (random.nextDouble() - 0.5) * jitter, random);
            if (fits(room, maxDepth)) {
                add(room);
                if (from == null) {
                    dig(0, SHAFT_BOTTOM, 0, room, random);
                } else {
                    dig(from, room, random);
                }
                return room;
            }
        }
        return null;
    }

    /** The room nearest a spot (where a new room is dug from), or null for the shaft. */
    private Room nearest(double x, double y, double z) {
        Room best = null;
        double bestDistance = x * x + (y - SHAFT_BOTTOM) * (y - SHAFT_BOTTOM) + z * z;
        for (Room room : rooms) {
            double d = (room.x - x) * (room.x - x) + (room.y - y) * (room.y - y) + (room.z - z) * (room.z - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = room;
            }
        }
        return best;
    }

    /** The Queen's chamber: placed last-but-not-least, in the deepest spot the style offers (it must fit). */
    private void queenAt(double x, double y, double z, int maxDepth, RandomSource random) {
        Room from = nearest(x, y, z);
        if (place(RoomType.QUEEN, x, y, z, maxDepth, random, from) == null) {
            // Never an anthill without its Queen: straight down from the shaft, as deep as there is room.
            for (double depth = y; depth < -6; depth += 1.5) {
                Room room = room(RoomType.QUEEN, 0, depth, 0, random);
                if (fits(room, maxDepth + 8)) {
                    add(room);
                    dig(0, SHAFT_BOTTOM, 0, room, random);
                    return;
                }
            }
            Room room = room(RoomType.QUEEN, 0, -9, 0, random);
            rooms.removeIf(other -> other.overlaps(room, 0.5));
            add(room);
            tunnel(0, SHAFT_BOTTOM, 0, 0, room.y, 0, TUNNEL);
        }
    }

    private static void classic(AnthillLayout layout) {
        // The fixed layout of anthills from before (already generated anthills finish building with it).
        layout.add(new Room(RoomType.GALLERY, 3, -5, 1, 3, 2, 2.5));
        // Its storage chamber kept a chest of the anthill's loot: a Chitin store (chest with that loot) keeps it so.
        layout.add(new Room(RoomType.CHITIN_STORAGE, -4, -9, 3, 3, 2, 3));
        layout.add(new Room(RoomType.QUEEN, 1, -14, -2, 4.5, 2.5, 4));
        layout.tunnel(0, -3, 0, 3, -5, 1, 1.1);
        layout.tunnel(2, -5, 1, -4, -9, 3, 1.1);
        layout.tunnel(-4, -9, 3, 1, -14, -2, 1.1);
        layout.tunnel(4, -5, 1, 7, -7, -3, 1.0);
    }

    private static void branching(AnthillLayout layout, RandomSource random) {
        int depthLimit = 22;
        List<RoomType> types = livingRooms(random, 5 + random.nextInt(3));
        double angle = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < types.size(); i++) {
            angle += 1.6 + random.nextDouble() * 1.4;
            double distance = 6 + random.nextDouble() * 8;
            double y = -5 - i * 1.8 - random.nextDouble() * 2;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            layout.place(types.get(i), x, y, z, depthLimit, random, layout.nearest(x, y, z));
        }
        double a = random.nextDouble() * Math.PI * 2;
        layout.queenAt(Math.cos(a) * 4, -18, Math.sin(a) * 4, depthLimit, random);
    }

    private static void deep(AnthillLayout layout, RandomSource random) {
        // A spine tunnel straight down; rooms off it on alternate sides; the Queen at its foot.
        int depthLimit = 34;
        double bottom = -28;
        layout.tunnel(0, SHAFT_BOTTOM, 0, 0.5, bottom, 0.5, 1.2);
        List<RoomType> types = livingRooms(random, 6);
        double side = random.nextBoolean() ? 1 : -1;
        boolean alongX = random.nextBoolean();
        for (int i = 0; i < types.size(); i++) {
            double y = -6 - i * 3.6;
            double offset = 6 + random.nextDouble() * 2;
            double x = alongX ? side * offset : (random.nextDouble() - 0.5) * 3;
            double z = alongX ? (random.nextDouble() - 0.5) * 3 : side * offset;
            Room room = room(types.get(i), x, y, z, random);
            if (layout.fits(room, depthLimit)) {
                layout.add(room);
                layout.tunnel(0.25, y, 0.25, room.x, room.y, room.z, TUNNEL);
            }
            side = -side;
        }
        Room queen = room(RoomType.QUEEN, 0.5, bottom - 2, 0.5, random);
        if (layout.fits(queen, depthLimit)) {
            layout.add(queen);
        } else {
            layout.queenAt(0, bottom, 0, depthLimit, random);
        }
    }

    private static void multiLevel(AnthillLayout layout, RandomSource random) {
        // Three floors of rooms; each floor's rooms joined in a row, and a ramp down to the next floor.
        int depthLimit = 26;
        double[] floors = {-6, -13, -20};
        List<RoomType> types = livingRooms(random, 6);
        int next = 0;
        Room previousFloor = null;
        for (int level = 0; level < floors.length; level++) {
            double angle = random.nextDouble() * Math.PI * 2;
            Room last = previousFloor;
            int perFloor = level == floors.length - 1 ? 1 : 2 + random.nextInt(2);
            for (int i = 0; i < perFloor; i++) {
                boolean queen = level == floors.length - 1;
                if (!queen && next >= types.size()) {
                    break;
                }
                double distance = 4 + i * 7;
                double x = Math.cos(angle) * distance;
                double z = Math.sin(angle) * distance;
                if (queen) {
                    layout.queenAt(x, floors[level], z, depthLimit, random);
                    break;
                }
                Room placed = layout.place(types.get(next++), x, floors[level], z, depthLimit, random, last);
                if (placed != null) {
                    last = placed;
                    if (i == 0) {
                        previousFloor = placed;
                    }
                }
                angle += 0.5 + random.nextDouble() * 0.6;
            }
            if (last != null) {
                previousFloor = last;
            }
        }
    }

    private static void spiral(AnthillLayout layout, RandomSource random) {
        // Rooms down a spiral around the shaft; each joined to the one before; the Queen at the bottom of the turn.
        int depthLimit = 28;
        List<RoomType> types = livingRooms(random, 6);
        double angle = random.nextDouble() * Math.PI * 2;
        double turn = random.nextBoolean() ? 1 : -1;
        Room last = null;
        double y = -5;
        for (RoomType type : types) {
            double x = Math.cos(angle) * 9;
            double z = Math.sin(angle) * 9;
            Room placed = layout.place(type, x, y, z, depthLimit, random, last);
            if (placed != null) {
                last = placed;
            }
            angle += turn * (1.05 + random.nextDouble() * 0.2);
            y -= 3.4;
        }
        layout.queenAt(Math.cos(angle) * 7, y, Math.sin(angle) * 7, depthLimit, random);
    }

    private static void central(AnthillLayout layout, RandomSource random) {
        // A great hall under the shaft; rooms dug off it all round; the Queen under the hall.
        int depthLimit = 26;
        Room hall = room(RoomType.HALL, 0, -9, 0, random);
        layout.add(hall);
        layout.tunnel(0, SHAFT_BOTTOM, 0, 0, hall.y, 0, TUNNEL);
        List<RoomType> types = livingRooms(random, 5);
        double angle = random.nextDouble() * Math.PI * 2;
        for (RoomType type : types) {
            double distance = 12 + random.nextDouble() * 3;
            layout.place(type, Math.cos(angle) * distance, -9 - random.nextDouble() * 3, Math.sin(angle) * distance, depthLimit, random, hall);
            angle += Math.PI * 2 / types.size();
        }
        Room queen = layout.place(RoomType.QUEEN, 0, -18, 0, depthLimit, random, hall);
        if (queen == null) {
            layout.queenAt(0, -20, 0, depthLimit, random);
        }
    }

    private static void wings(AnthillLayout layout, RandomSource random) {
        // A gallery under the shaft and three wings off it, each a chain of rooms; the Queen at the end of the deepest.
        int depthLimit = 24;
        Room gallery = room(RoomType.GALLERY, 0, -6, 0, random);
        layout.add(gallery);
        layout.tunnel(0, SHAFT_BOTTOM, 0, 0, gallery.y, 0, TUNNEL);
        List<RoomType> types = livingRooms(random, 6);
        double angle = random.nextDouble() * Math.PI * 2;
        int next = 0;
        int queenWing = random.nextInt(3);
        for (int wing = 0; wing < 3; wing++) {
            Room last = gallery;
            double wingAngle = angle + wing * Math.PI * 2 / 3;
            int length = wing == queenWing ? 1 : 2;
            for (int i = 0; i < length && next < types.size(); i++) {
                double distance = 8 + i * 7;
                Room placed = layout.place(types.get(next++), Math.cos(wingAngle) * distance, -7 - i * 3, Math.sin(wingAngle) * distance,
                        depthLimit, random, last);
                if (placed != null) {
                    last = placed;
                }
            }
            if (wing == queenWing) {
                Room queen = layout.place(RoomType.QUEEN, Math.cos(wingAngle) * 15, -16, Math.sin(wingAngle) * 15, depthLimit, random, last);
                if (queen == null) {
                    layout.queenAt(0, -16, 0, depthLimit, random);
                }
            }
        }
    }

    private static void abandoned(AnthillLayout layout, RandomSource random) {
        // A small, shallow nest its colony left: two or three empty rooms, and its old Queen, who stayed.
        int depthLimit = 16;
        int count = 2 + random.nextInt(2);
        double angle = random.nextDouble() * Math.PI * 2;
        Room last = null;
        for (int i = 0; i < count; i++) {
            double distance = 5 + i * 4;
            Room placed = layout.place(i == 1 ? RoomType.FOOD_STORAGE : RoomType.ABANDONED, Math.cos(angle) * distance, -5 - i * 2.5,
                    Math.sin(angle) * distance, depthLimit, random, last);
            if (placed != null) {
                last = placed;
            }
            angle += 1.2;
        }
        layout.queenAt(-Math.cos(angle) * 4, -12, -Math.sin(angle) * 4, depthLimit, random);
    }
}
