import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Paints every texture of Ant Colonies at build time: the ant skins (one grey layout per caste, tinted per species in
 * game), their gaster patterns and crystals, the Terrarium's inside, every block and item, and the particles.
 * Run from the project root with JDK 25: {@code java tools/TexGen.java}. Original art, no third-party textures.
 */
public class TexGen {
    static final Path TEX = Path.of("common/src/generated/resources/assets/antcolonies/textures");
    static int written;

    // ------------------------------------------------------------------ Canvas

    static final class Img {
        final BufferedImage image;
        final int w;
        final int h;

        Img(int w, int h) {
            this.w = w;
            this.h = h;
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        void set(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                image.setRGB(x, y, argb);
            }
        }

        int get(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h ? image.getRGB(x, y) : 0;
        }

        /**
         * Small chitin scales over an icon's plain chitin (its base and light pixels): rows of scales 4 pixels wide, a
         * new row every 2, each shifted by half a scale; lit on top, dark where they round off at the bottom.
         */
        Img scaled() {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int color = get(x, y);
                    if (color == CH_BASE || color == CH_LIGHT) {
                        int dx = Math.floorMod(x + (y / 2 % 2) * 2, 4);
                        boolean edge = dx == 0 || dx == 3;
                        set(x, y, y % 2 == 0 ? (edge ? CH_BASE : CH_LIGHT) : (edge ? CH_DARK : CH_BASE));
                    }
                }
            }
            return this;
        }

        void fill(int x0, int y0, int x1, int y1, int argb) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    set(x, y, argb);
                }
            }
        }

        /** Blends a color over a pixel (alpha of the color). */
        void blend(int x, int y, int argb) {
            int base = get(x, y);
            float a = (argb >>> 24) / 255.0F;
            int ba = base >>> 24;
            int r = (int) (((argb >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * (1 - a));
            int g = (int) (((argb >> 8) & 0xFF) * a + ((base >> 8) & 0xFF) * (1 - a));
            int b = (int) ((argb & 0xFF) * a + (base & 0xFF) * (1 - a));
            int outA = Math.max(ba, argb >>> 24);
            set(x, y, outA << 24 | r << 16 | g << 8 | b);
        }

        void save(String path) {
            try {
                Path file = TEX.resolve(path + ".png");
                Files.createDirectories(file.getParent());
                ImageIO.write(image, "png", file.toFile());
                written++;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int argb(int a, int r, int g, int b) {
        return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int shade(int color, double f) {
        return (color & 0xFF000000) | clamp((int) (((color >> 16) & 0xFF) * f)) << 16 | clamp((int) (((color >> 8) & 0xFF) * f)) << 8
                | clamp((int) ((color & 0xFF) * f));
    }

    static int gray(int v) {
        return rgb(v, v, v);
    }

    static int hex(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Fills with a color and per-pixel noise of +-amount. */
    static void noise(Img img, int x0, int y0, int x1, int y1, int color, int amount, Random random) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double f = 1 + (random.nextDouble() * 2 - 1) * amount / 100.0;
                img.set(x, y, shade(color, f));
            }
        }
    }

    static void border(Img img, int x0, int y0, int x1, int y1, int color) {
        for (int x = x0; x < x1; x++) {
            img.set(x, y0, color);
            img.set(x, y1 - 1, color);
        }
        for (int y = y0; y < y1; y++) {
            img.set(x0, y, color);
            img.set(x1 - 1, y, color);
        }
    }

    static void disc(Img img, double cx, double cy, double r, int color) {
        for (int y = (int) (cy - r - 1); y <= cy + r + 1; y++) {
            for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
                if ((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy) <= r * r) {
                    img.set(x, y, color);
                }
            }
        }
    }

    static void line(Img img, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            img.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), color);
        }
    }

    /** A 1-px dark outline around every opaque pixel of an item sprite. */
    static void outline(Img img, int color) {
        boolean[][] solid = new boolean[img.w][img.h];
        for (int y = 0; y < img.h; y++) {
            for (int x = 0; x < img.w; x++) {
                solid[x][y] = (img.get(x, y) >>> 24) > 0;
            }
        }
        for (int y = 0; y < img.h; y++) {
            for (int x = 0; x < img.w; x++) {
                if (!solid[x][y] && (x > 0 && solid[x - 1][y] || x + 1 < img.w && solid[x + 1][y] || y > 0 && solid[x][y - 1] || y + 1 < img.h && solid[x][y + 1])) {
                    img.set(x, y, color);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Ant skins

    /** One box of the ant model: its texture corner and size (units = texels). */
    record Box(String part, double u, double v, double w, double h, double d) {}

    record Shape(double thoraxW, double thoraxH, double thoraxL, double headW, double headH, double headL, double gasterW, double gasterH,
                 double gasterL, double mandibleL, double femur, double tibia, double legThickness) {}

    static Shape shape(String caste) {
        return switch (caste) {
            case "worker" -> new Shape(3.4, 3.8, 7.5, 5.2, 4.8, 5.4, 6, 5.6, 8.5, 2.6, 6, 7, 1.0);
            case "soldier" -> new Shape(4.2, 4.2, 8, 8, 6.4, 7.2, 6.4, 5.8, 8, 5, 6.5, 7.5, 1.3);
            default -> new Shape(5.2, 5.2, 10, 6, 5.4, 6, 8, 7.4, 13, 3, 7, 8, 1.2);
        };
    }

    /** The same layout as AntModel.create. */
    static Box[] boxes(String caste) {
        Shape s = shape(caste);
        double t = s.legThickness();
        return new Box[] {
                new Box("thorax", 0, 0, s.thoraxW(), s.thoraxH(), s.thoraxL()),
                new Box("petiole", 76, 0, 2, 2.5, 2.5),
                new Box("hump", 76, 6, s.thoraxW() - 1, 1.2, 3),
                new Box("head", 40, 0, s.headW(), s.headH(), s.headL()),
                new Box("mandible", 96, 0, 1.5, 1.2, s.mandibleL()),
                new Box("antenna", 112, 0, 1, 1, 5),
                new Box("antenna", 112, 7, 0.9, 0.9, 5.5),
                new Box("club", 96, 8, 1.3, 1.3, 1.6),
                new Box("leg", 72, 20, s.femur(), t, t),
                new Box("leg", 72, 24, s.tibia(), t * 0.8, t * 0.8),
                new Box("gaster", 0, 20, s.gasterW(), s.gasterH(), s.gasterL()),
                new Box("tip", 52, 20, s.gasterW() - 2.4, s.gasterH() - 2.4, 1.5),
                new Box("tip", 52, 28, s.gasterW() - 2.4, s.gasterH() - 2.4, 1.2),
                new Box("gaster", 64, 36, s.gasterW() + 0.8, s.gasterH() + 0.8, s.gasterL() - 2),
        };
    }

    /** The six faces of a box in the texture, by name: x0, y0, x1, y1 (texel bounds, rounded out). */
    static int[][] faces(Box b) {
        double u = b.u(), v = b.v(), w = b.w(), h = b.h(), d = b.d();
        double[][] f = {
                {u + d, v, u + d + w, v + d},             // up (top)
                {u + d + w, v, u + d + 2 * w, v + d},     // down
                {u, v + d, u + d, v + d + h},             // west
                {u + d, v + d, u + d + w, v + d + h},     // north (front)
                {u + d + w, v + d, u + 2 * d + w, v + d + h}, // east
                {u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h}, // south (back)
        };
        int[][] out = new int[6][];
        for (int i = 0; i < 6; i++) {
            out[i] = new int[] {(int) Math.floor(f[i][0]), (int) Math.floor(f[i][1]), (int) Math.ceil(f[i][2]), (int) Math.ceil(f[i][3])};
        }
        return out;
    }

    static void antSkins() {
        for (String caste : new String[] {"worker", "soldier", "queen"}) {
            Random random = new Random(caste.hashCode());
            Img img = new Img(128, 64);
            for (Box box : boxes(caste)) {
                int[][] faces = faces(box);
                for (int f = 0; f < 6; f++) {
                    int[] r = faces[f];
                    // Light grey, tinted in game; tops lighter, bottoms darker.
                    int base = gray(f == 0 ? 238 : f == 1 ? 168 : 214);
                    if (box.part().equals("mandible") || box.part().equals("club")) {
                        base = shade(base, 0.72);
                    }
                    if (box.part().equals("leg")) {
                        base = shade(base, 0.85);
                    }
                    noise(img, r[0], r[1], r[2], r[3], base, 6, random);
                    // Darker rims make each segment read as a plate.
                    for (int x = r[0]; x < r[2]; x++) {
                        img.set(x, r[3] - 1, shade(img.get(x, r[3] - 1), 0.8));
                    }
                    for (int y = r[1]; y < r[3]; y++) {
                        img.set(r[0], y, shade(img.get(r[0], y), 0.88));
                    }
                    if (box.part().equals("gaster") && f >= 2) {
                        // The overlapping tergites of the gaster: bands across.
                        int span = r[2] - r[0];
                        for (int x = r[0]; x < r[2]; x++) {
                            int along = f == 3 || f == 5 ? 0 : (x - r[0]);
                            if (f != 3 && f != 5 && along % 3 == 2) {
                                for (int y = r[1]; y < r[3]; y++) {
                                    img.set(x, y, shade(img.get(x, y), 0.78));
                                }
                            }
                        }
                        if (span > 0) {
                            // A glossy highlight along the top of the sides.
                            for (int x = r[0]; x < r[2]; x++) {
                                img.set(x, r[1], shade(img.get(x, r[1]), 1.12));
                            }
                        }
                    }
                    if (box.part().equals("gaster") && f == 0) {
                        for (int y = r[1]; y < r[3]; y++) {
                            if ((y - r[1]) % 3 == 2) {
                                for (int x = r[0]; x < r[2]; x++) {
                                    img.set(x, y, shade(img.get(x, y), 0.82));
                                }
                            }
                        }
                    }
                    if (box.part().equals("thorax") && (f == 2 || f == 4)) {
                        // The thorax's segments: two lines across its sides.
                        int span = r[2] - r[0];
                        for (int k = 1; k <= 2; k++) {
                            int x = r[0] + span * k / 3;
                            for (int y = r[1]; y < r[3]; y++) {
                                img.set(x, y, shade(img.get(x, y), 0.8));
                            }
                        }
                    }
                    if (box.part().equals("leg") && (f >= 2)) {
                        img.set(r[0], r[1], shade(img.get(r[0], r[1]), 0.6));
                        img.set(r[2] - 1, r[1], shade(img.get(r[2] - 1, r[1]), 0.6));
                    }
                }
                if (box.part().equals("head")) {
                    // Compound eyes on both sides, near the middle, with a glint; the face plate in front.
                    for (int side : new int[] {2, 4}) {
                        int[] r = faces[side];
                        double cx = (r[0] + r[2]) / 2.0 + (side == 2 ? -0.6 : 0.6);
                        double cy = r[1] + (r[3] - r[1]) * 0.38;
                        double radius = Math.max(1.0, (r[3] - r[1]) * 0.22);
                        disc(img, cx, cy, radius, gray(28));
                        img.set((int) cx, (int) (cy - radius * 0.4), gray(200));
                    }
                    int[] front = faces[3];
                    int mid = (front[0] + front[2]) / 2;
                    // The clypeus and the antenna sockets.
                    img.set(mid, front[3] - 2, shade(img.get(mid, front[3] - 2), 0.75));
                    img.set(mid - 1, front[3] - 2, shade(img.get(mid - 1, front[3] - 2), 0.75));
                    img.set(front[0] + 1, front[1] + 1, gray(60));
                    img.set(front[2] - 2, front[1] + 1, gray(60));
                }
            }
            img.save("entity/ant/" + caste);
            patterns(caste);
        }
        // Crystals: pale facets, a little see-through.
        Img crystal = new Img(128, 64);
        Random random = new Random(7);
        for (int y = 48; y < 58; y++) {
            for (int x = 0; x < 12; x++) {
                int v = 210 + random.nextInt(40) - ((x + y) % 4 == 0 ? 50 : 0);
                crystal.set(x, y, argb(225, v, v, v));
            }
        }
        crystal.save("entity/ant/crystal");
        // What ants carry.
        Img carry = new Img(32, 16);
        noise(carry, 0, 0, 32, 16, gray(225), 10, random);
        carry.save("entity/ant/carry");
    }

    /** The gaster patterns, each a grey layer over the gaster's faces (tinted with the species' accent color). */
    static void patterns(String caste) {
        Box gaster = boxes(caste)[10];
        int[][] faces = faces(gaster);
        for (String pattern : new String[] {"striped", "banded", "speckled", "faceted", "metallic"}) {
            Img img = new Img(128, 64);
            Random random = new Random(pattern.hashCode() ^ caste.hashCode());
            for (int f = 0; f < 6; f++) {
                int[] r = faces[f];
                for (int y = r[1]; y < r[3]; y++) {
                    for (int x = r[0]; x < r[2]; x++) {
                        int lx = x - r[0];
                        int ly = y - r[1];
                        int w = r[2] - r[0];
                        // Along the gaster runs the face's u (sides) or v (top and bottom).
                        int along = f == 0 || f == 1 ? ly : lx;
                        int length = f == 0 || f == 1 ? r[3] - r[1] : w;
                        boolean on = switch (pattern) {
                            case "striped" -> f != 3 && along % 3 == 0;
                            case "banded" -> f != 3 && f != 5 && (along * 6 / Math.max(1, length)) % 3 == 1;
                            case "speckled" -> random.nextInt(5) == 0;
                            case "faceted" -> (lx + ly) % 4 == 0 || (lx - ly + 64) % 4 == 0;
                            default -> f == 0 && Math.abs(lx - w / 2) <= 1 || random.nextInt(9) == 0;
                        };
                        if (on) {
                            int alpha = pattern.equals("metallic") ? 150 : 235;
                            img.set(x, y, argb(alpha, 240, 240, 240));
                        }
                    }
                }
            }
            img.save("entity/ant/" + caste + "_" + pattern);
        }
    }

    // ------------------------------------------------------------------ Terrarium inside

    static void terrariumInside() {
        Img img = new Img(64, 64);
        Random random = new Random(11);
        // Soil: light and neutral (tinted by climate), with grains and pebbles.
        noise(img, 0, 0, 16, 16, gray(230), 9, random);
        for (int i = 0; i < 14; i++) {
            int x = random.nextInt(16);
            int y = random.nextInt(16);
            img.set(x, y, gray(170 + random.nextInt(30)));
            if (random.nextBoolean()) {
                img.set(x + 1, y, gray(250));
            }
        }
        // Tunnels: the carved floor, darker grains.
        noise(img, 16, 0, 32, 16, gray(200), 12, random);
        for (int i = 0; i < 20; i++) {
            img.set(16 + random.nextInt(16), random.nextInt(16), gray(140));
        }
        // The surface: grass-like specks (tinted green, sand or snow).
        noise(img, 32, 0, 48, 16, gray(222), 14, random);
        for (int i = 0; i < 30; i++) {
            int x = 32 + random.nextInt(16);
            int y = random.nextInt(16);
            img.set(x, y, gray(255));
            img.set(x, y + 1, gray(190));
        }
        // Smooth white (eggs, larvae, crumbs, the lab's helix), mushroom cap spots, crystal facets.
        noise(img, 48, 0, 56, 8, gray(246), 3, random);
        noise(img, 56, 0, 64, 8, gray(235), 5, random);
        img.set(58, 2, gray(255));
        img.set(61, 4, gray(255));
        img.set(59, 6, gray(255));
        for (int y = 8; y < 16; y++) {
            for (int x = 48; x < 56; x++) {
                img.set(x, y, gray((x + y) % 3 == 0 ? 255 : 215));
            }
        }
        img.save("entity/terrarium_inside");
    }

    // ------------------------------------------------------------------ Blocks

    static Img block(int color, int amount, long seed) {
        Img img = new Img(16, 16);
        noise(img, 0, 0, 16, 16, color, amount, new Random(seed));
        return img;
    }

    static void planks(Img img, int color, Random random) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double grain = 1 + (random.nextDouble() - 0.5) * 0.12 + (Math.sin((x + random.nextInt(2)) * 1.3) * 0.03);
                img.set(x, y, shade(color, grain));
            }
            if (y % 4 == 3) {
                for (int x = 0; x < 16; x++) {
                    img.set(x, y, shade(color, 0.7));
                }
            }
        }
        for (int row = 0; row < 4; row++) {
            int seam = (row * 7 + 3) % 16;
            for (int y = row * 4; y < row * 4 + 3; y++) {
                img.set(seam, y, shade(color, 0.72));
            }
        }
    }

    static void bricks(Img img, int color, Random random, int mortar) {
        noise(img, 0, 0, 16, 16, color, 10, random);
        for (int y = 0; y < 16; y += 4) {
            for (int x = 0; x < 16; x++) {
                img.set(x, y, mortar);
            }
            int offset = (y / 4) % 2 == 0 ? 0 : 4;
            for (int x = offset; x < 16; x += 8) {
                for (int k = 0; k < 4; k++) {
                    img.set(x, y + k, mortar);
                }
            }
        }
    }

    /** A dark metal panel with rivets, for the Genetic Laboratory. */
    static Img labPanel(int accent, boolean lit, long seed) {
        Img img = new Img(16, 16);
        Random random = new Random(seed);
        noise(img, 0, 0, 16, 16, rgb(58, 64, 74), 5, random);
        border(img, 0, 0, 16, 16, rgb(34, 38, 46));
        border(img, 1, 1, 15, 15, rgb(84, 92, 104));
        for (int[] rivet : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
            img.set(rivet[0], rivet[1], rgb(150, 158, 170));
        }
        return img;
    }

    static void glassWindow(Img img, int x0, int y0, int x1, int y1, int tint, boolean lit) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.set(x, y, lit ? shade(tint, 0.8 + 0.3 * ((x + y) % 5 == 0 ? 1 : 0)) : shade(tint, 0.35));
            }
        }
        border(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, rgb(30, 34, 40));
        if (lit) {
            img.set(x0, y0, rgb(255, 255, 255));
        }
    }

    /** The Generator: the lab's gunmetal panels with a firebox front (a grate that glows while it burns), vents and a copper terminal. */
    static void generator() {
        int copper = rgb(196, 112, 64);
        for (boolean on : new boolean[] {false, true}) {
            Img front = labPanel(copper, on, 71);
            // Firebox: dark mouth, a warm glow when burning, and a grate of bars over it.
            for (int y = 5; y < 13; y++) {
                for (int x = 4; x < 12; x++) {
                    double heat = (y - 5) / 7.0;
                    front.set(x, y, on ? rgb(200 + (int) (55 * heat), 80 + (int) (110 * heat), 20 + (int) (40 * heat)) : rgb(24, 22, 22));
                }
            }
            border(front, 3, 4, 13, 14, rgb(30, 32, 38));
            for (int x = 5; x < 12; x += 2) {
                line(front, x, 5, x, 12, on ? rgb(90, 50, 30) : rgb(70, 74, 82));
            }
            // The status lamp over the mouth.
            front.set(7, 2, on ? rgb(255, 200, 80) : rgb(80, 70, 50));
            front.set(8, 2, on ? rgb(255, 230, 140) : rgb(80, 70, 50));
            front.save("block/generator_front" + (on ? "_on" : ""));
        }
        Img side = labPanel(copper, false, 72);
        for (int y = 4; y < 12; y += 2) {
            line(side, 3, y, 9, y, rgb(30, 34, 40));
            line(side, 3, y + 1, 9, y + 1, rgb(96, 104, 116));
        }
        // The FE terminal: a copper socket with two pins.
        side.fill(10, 5, 14, 11, copper);
        border(side, 10, 5, 14, 11, shade(copper, 0.6));
        side.set(11, 7, rgb(40, 30, 24));
        side.set(12, 8, rgb(40, 30, 24));
        side.save("block/generator_side");
        Img top = labPanel(copper, false, 73);
        disc(top, 8, 8, 5.5, rgb(36, 40, 48));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + 0.4;
            line(top, 8, 8, 8 + (int) Math.round(Math.cos(a) * 4.5), 8 + (int) Math.round(Math.sin(a) * 4.5), rgb(120, 128, 140));
        }
        disc(top, 8, 8, 1.2, copper);
        top.save("block/generator_top");
    }

    static void blocks() {
        Random random = new Random(3);
        // Terrarium: oak frame and clear glass.
        Img wood = new Img(16, 16);
        planks(wood, rgb(150, 108, 64), random);
        wood.save("block/terrarium_wood");
        Img glass = new Img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                glass.set(x, y, argb(28, 220, 240, 255));
            }
        }
        line(glass, 2, 12, 6, 8, argb(110, 255, 255, 255));
        line(glass, 3, 13, 8, 8, argb(70, 255, 255, 255));
        glass.save("block/terrarium_glass");

        // Chambers: every chamber is the same glass case on the same wooden base; only its floor (and what its model puts
        // on it) shows what it is. No borders: touching chambers' floors run into each other.
        // Nest Chamber: soft earth with bits of bedding.
        Img nestTop = block(rgb(150, 104, 76), 12, 31);
        for (int i = 0; i < 14; i++) {
            nestTop.set(random.nextInt(16), random.nextInt(16), rgb(200, 170, 110));
        }
        nestTop.save("block/nest_chamber_top");
        // Queen Chamber: dark earth with golden flecks.
        Img queenTop = block(rgb(120, 86, 50), 12, 32);
        for (int i = 0; i < 10; i++) {
            queenTop.set(random.nextInt(16), random.nextInt(16), rgb(232, 190, 80));
        }
        queenTop.save("block/queen_chamber_top");
        // Foraging Site: loose soil with twigs and pebbles.
        Img scavTop = block(rgb(124, 92, 62), 14, 22);
        line(scavTop, 2, 3, 7, 6, rgb(150, 110, 60));
        line(scavTop, 9, 12, 14, 10, rgb(150, 110, 60));
        scavTop.set(11, 4, rgb(140, 140, 140));
        scavTop.set(4, 12, rgb(160, 160, 160));
        scavTop.save("block/foraging_site_top");
        // Harvesting Site: fungus-covered soil.
        Img gardenTop = block(rgb(190, 176, 146), 12, 25);
        for (int i = 0; i < 18; i++) {
            gardenTop.set(random.nextInt(16), random.nextInt(16), rgb(232, 224, 204));
        }
        for (int i = 0; i < 8; i++) {
            gardenTop.set(random.nextInt(16), random.nextInt(16), rgb(80, 140, 60));
        }
        gardenTop.save("block/harvesting_site_top");
        // Hunting Ground: trampled earth with dark stains of the catch.
        Img huntTop = block(rgb(112, 86, 64), 14, 33);
        for (int i = 0; i < 6; i++) {
            huntTop.set(random.nextInt(16), random.nextInt(16), rgb(110, 34, 28));
        }
        line(huntTop, 3, 12, 7, 12, rgb(210, 204, 186));
        huntTop.save("block/hunting_ground_top");
        // Storage: a plank floor for the heap.
        Img inside = new Img(16, 16);
        planks(inside, rgb(120, 88, 52), random);
        inside.save("block/storage_inside");
        // Upgrade Station: a workbench with chitin plates and tools.
        Img stationSide = new Img(16, 16);
        planks(stationSide, rgb(170, 128, 80), random);
        stationSide.save("block/upgrade_station_side");
        Img stationTop = new Img(16, 16);
        planks(stationTop, rgb(186, 142, 92), random);
        stationTop.fill(3, 3, 8, 8, rgb(120, 80, 40));
        stationTop.fill(4, 4, 7, 7, rgb(220, 180, 90));
        line(stationTop, 9, 10, 13, 6, rgb(170, 170, 176));
        stationTop.fill(10, 11, 14, 14, rgb(60, 110, 50));
        stationTop.save("block/upgrade_station_top");

        // The Genetic Laboratory: a dark panel with a DNA screen; while it works, the windows glow.
        Object[][] lab = {{"genetic_laboratory", rgb(80, 200, 255)}};
        for (Object[] part : lab) {
            String id = (String) part[0];
            int color = (Integer) part[1];
            for (boolean lit : new boolean[] {false, true}) {
                String suffix = lit ? "_working" : "";
                Img side = labPanel(color, lit, id.hashCode());
                glassWindow(side, 4, 4, 12, 12, color, lit);
                side.save("block/" + id + "_side" + suffix);
                Img top = labPanel(color, lit, id.hashCode() + 1);
                disc(top, 8, 8, 3.5, lit ? color : shade(color, 0.35));
                top.save("block/" + id + "_top" + suffix);
                Img front = labPanel(color, lit, id.hashCode() + 2);
                switch (id) {
                    case "genetic_laboratory" -> {
                        glassWindow(front, 3, 3, 13, 9, color, lit);
                        for (int x = 4; x < 12; x++) {
                            int y = 6 + (int) Math.round(Math.sin(x * 0.9) * 2);
                            front.set(x, y, lit ? rgb(255, 255, 255) : rgb(120, 120, 130));
                        }
                        front.fill(4, 11, 12, 13, rgb(30, 34, 40));
                        front.set(5, 12, lit ? rgb(90, 255, 120) : rgb(60, 80, 60));
                        front.set(7, 12, lit ? rgb(255, 80, 80) : rgb(80, 60, 60));
                    }
                    default -> {
                        glassWindow(front, 3, 3, 13, 9, color, lit);
                        line(front, 4, 6, 12, 6, lit ? rgb(255, 255, 255) : rgb(90, 90, 100));
                        front.fill(5, 11, 11, 13, lit ? shade(color, 0.9) : rgb(40, 44, 52));
                    }
                }
                front.save("block/" + id + "_front" + suffix);
            }
        }
        // Wild nests: soil mounds in their biome's colors, with an entrance on top.
        Object[][] nests = {{"common", rgb(118, 86, 58), rgb(96, 134, 64)}, {"wood", rgb(108, 74, 48), rgb(80, 64, 40)},
                {"stone", rgb(118, 116, 112), rgb(96, 94, 90)}, {"snow", rgb(214, 226, 236), rgb(240, 246, 252)},
                {"desert", rgb(214, 190, 130), rgb(226, 204, 146)}, {"farmer", rgb(110, 84, 56), rgb(186, 172, 140)},
                {"jungle", rgb(84, 62, 40), rgb(70, 122, 42)}, {"swamp", rgb(72, 62, 50), rgb(88, 98, 58)},
                {"savanna", rgb(150, 100, 60), rgb(172, 160, 88)}, {"shore", rgb(222, 208, 160), rgb(234, 222, 178)},
                {"blossom", rgb(118, 86, 58), rgb(108, 150, 70)}, {"pale", rgb(122, 112, 102), rgb(200, 205, 195)},
                {"badlands", rgb(172, 92, 50), rgb(190, 102, 52)}, {"army", rgb(96, 60, 36), rgb(120, 104, 70)},
                {"warped", rgb(104, 42, 40), rgb(24, 124, 116)}, {"chorus", rgb(220, 222, 158), rgb(150, 108, 172)}};
        for (Object[] nest : nests) {
            String id = (String) nest[0];
            Img side = block((Integer) nest[1], 14, id.hashCode());
            for (int i = 0; i < 6; i++) {
                side.set(random.nextInt(16), random.nextInt(16), shade((Integer) nest[1], 0.65));
            }
            if (id.equals("wood")) {
                for (int i = 0; i < 10; i++) {
                    int x = random.nextInt(14);
                    int y = random.nextInt(16);
                    line(side, x, y, x + 2, y, rgb(150, 110, 60));
                }
            }
            switch (id) {
                // Hanging vines over dark jungle soil.
                case "jungle" -> {
                    for (int i = 0; i < 4; i++) {
                        int x = 2 + i * 4;
                        line(side, x, 0, x, 3 + random.nextInt(6), rgb(52, 110, 30));
                    }
                }
                // Bands of terracotta, like the badlands' cliffs.
                case "badlands" -> {
                    int[] bands = {rgb(196, 124, 70), rgb(150, 70, 40), rgb(210, 170, 120), rgb(160, 90, 60)};
                    for (int y = 2; y < 16; y += 4) {
                        line(side, 0, y, 15, y, bands[(y / 4) % bands.length]);
                    }
                }
                // Shell fragments in the sand.
                case "shore" -> {
                    for (int i = 0; i < 5; i++) {
                        side.set(random.nextInt(16), random.nextInt(16), rgb(250, 244, 230));
                    }
                }
                // Wet, dark streaks of mud.
                case "swamp" -> {
                    for (int i = 0; i < 6; i++) {
                        int x = random.nextInt(14);
                        int y = random.nextInt(16);
                        line(side, x, y, x + 2, y, rgb(52, 46, 38));
                    }
                }
                default -> {
                }
            }
            side.save("block/" + id + "_ant_nest_side");
            Img top = block((Integer) nest[2], 12, id.hashCode() + 1);
            // Petals on the blossom nest, dry tufts on the savanna one.
            if (id.equals("blossom")) {
                Random petals = new Random(42);
                for (int i = 0; i < 16; i++) {
                    top.set(petals.nextInt(16), petals.nextInt(16), i % 2 == 0 ? rgb(246, 170, 200) : rgb(255, 210, 225));
                }
            } else if (id.equals("savanna")) {
                for (int i = 0; i < 7; i++) {
                    top.set(random.nextInt(16), random.nextInt(16), rgb(200, 186, 110));
                }
            }
            disc(top, 8, 8, 2.6, rgb(30, 20, 14));
            disc(top, 8, 8, 1.4, rgb(14, 10, 8));
            top.save("block/" + id + "_ant_nest_top");
        }
    }

    // ------------------------------------------------------------------ Items

    static Img item() {
        return new Img(16, 16);
    }

    static void tube(Img img, Img overlay, int cap, boolean crown) {
        // Glass outline, contents left blank for the tinted overlay.
        for (int y = 3; y < 15; y++) {
            img.set(5, y, argb(255, 200, 225, 240));
            img.set(10, y, argb(255, 170, 200, 220));
            for (int x = 6; x < 10; x++) {
                img.set(x, y, argb(70, 220, 240, 255));
            }
        }
        line(img, 6, 15, 9, 15, argb(255, 170, 200, 220));
        img.fill(5, 1, 11, 3, cap);
        img.fill(5, 3, 11, 4, shade(cap, 0.7));
        img.set(6, 5, argb(230, 255, 255, 255));
        img.set(6, 6, argb(200, 255, 255, 255));
        if (crown) {
            img.set(5, 0, rgb(255, 210, 80));
            img.set(8, 0, rgb(255, 220, 100));
            img.set(10, 0, rgb(255, 210, 80));
        }
        if (overlay != null) {
            for (int y = 8; y < 15; y++) {
                for (int x = 6; x < 10; x++) {
                    overlay.set(x, y, gray(y == 8 ? 255 : 220));
                }
            }
        }
    }

    static void items() {
        Random random = new Random(9);
        // Tweezers: two steel arms.
        Img tweezers = item();
        line(tweezers, 3, 13, 11, 3, rgb(190, 196, 206));
        line(tweezers, 5, 13, 12, 4, rgb(150, 156, 166));
        line(tweezers, 3, 14, 5, 14, rgb(90, 96, 106));
        tweezers.set(11, 2, rgb(230, 236, 246));
        tweezers.set(13, 4, rgb(230, 236, 246));
        tweezers.save("item/tweezers");
        Img testTube = item();
        tube(testTube, null, rgb(150, 110, 70), false);
        testTube.save("item/test_tube");
        // Filled tubes: the glass, and the tinted contents as a second layer.
        Img antTube = item();
        Img antOverlay = item();
        tube(antTube, antOverlay, rgb(150, 110, 70), false);
        for (int[] ant : new int[][] {{7, 10}, {8, 12}, {7, 13}}) {
            antOverlay.set(ant[0], ant[1], gray(90));
            antOverlay.set(ant[0] + 1, ant[1], gray(70));
        }
        antTube.save("item/ant_test_tube");
        antOverlay.save("item/ant_test_tube_overlay");
        Img queenTube = item();
        Img queenOverlay = item();
        tube(queenTube, queenOverlay, rgb(220, 170, 60), true);
        for (int y = 9; y < 14; y++) {
            queenOverlay.set(7, y, gray(70));
            queenOverlay.set(8, y, gray(80));
        }
        queenTube.save("item/queen_test_tube");
        queenOverlay.save("item/queen_test_tube_overlay");
        Img sample = item();
        Img sampleOverlay = item();
        tube(sample, sampleOverlay, rgb(80, 160, 200), false);
        for (int y = 9; y < 14; y += 2) {
            sampleOverlay.set(7, y, gray(255));
            sampleOverlay.set(8, y + 1, gray(255));
        }
        sample.save("item/genetic_sample");
        sampleOverlay.save("item/genetic_sample_overlay");
        // Eggs and larvae: white shapes with a tinted shimmer.
        Img eggs = item();
        Img eggOverlay = item();
        for (int[] egg : new int[][] {{5, 9}, {9, 10}, {7, 6}, {11, 7}}) {
            disc(eggs, egg[0], egg[1], 2.1, rgb(240, 236, 222));
            eggs.set(egg[0] - 1, egg[1] - 1, rgb(255, 255, 255));
            eggOverlay.set(egg[0], egg[1] + 1, argb(160, 255, 255, 255));
        }
        outline(eggs, rgb(110, 100, 86));
        eggs.save("item/ant_egg");
        eggOverlay.save("item/ant_egg_overlay");
        Img larvae = item();
        Img larvaOverlay = item();
        for (int[] larva : new int[][] {{3, 10}, {8, 6}, {7, 12}}) {
            for (int k = 0; k < 5; k++) {
                larvae.set(larva[0] + k, larva[1] + (k == 0 || k == 4 ? 1 : 0), rgb(236, 224, 192));
                larvae.set(larva[0] + k, larva[1] + 1, rgb(220, 206, 170));
            }
            larvaOverlay.set(larva[0], larva[1] + 1, argb(200, 255, 255, 255));
        }
        outline(larvae, rgb(120, 104, 80));
        larvae.save("item/ant_larva");
        larvaOverlay.save("item/ant_larva_overlay");
        // Foods: pellets in their colors.
        Object[][] foods = {{"ant_food", rgb(206, 170, 96)}, {"mineral_ant_food", rgb(170, 170, 180)}, {"crystal_ant_food", rgb(150, 110, 230)},
                {"nether_ant_food", rgb(190, 60, 40)}};
        for (Object[] food : foods) {
            Img img = item();
            int color = (Integer) food[1];
            for (int[] pellet : new int[][] {{5, 10}, {8, 11}, {11, 10}, {6, 7}, {9, 8}, {8, 5}}) {
                disc(img, pellet[0], pellet[1], 1.9, shade(color, 0.9 + random.nextDouble() * 0.2));
                img.set(pellet[0] - 1, pellet[1] - 1, shade(color, 1.3));
            }
            if (!food[0].equals("ant_food")) {
                img.set(8, 5, rgb(255, 255, 255));
                img.set(11, 9, shade(color, 1.5));
            }
            outline(img, shade(color, 0.4));
            img.save("item/" + food[0]);
        }
        // Royal Jelly: a jar of golden jelly.
        Img jelly = item();
        jelly.fill(4, 5, 12, 14, rgb(250, 214, 110));
        jelly.fill(4, 5, 12, 7, rgb(255, 240, 180));
        jelly.fill(5, 3, 11, 5, rgb(170, 120, 70));
        outline(jelly, rgb(110, 80, 40));
        jelly.save("item/royal_jelly");
        // Nuptial Pheromone: a pink bottle.
        Img pheromone = item();
        disc(pheromone, 8, 10, 4, rgb(240, 120, 190));
        pheromone.fill(7, 3, 9, 7, rgb(220, 230, 240));
        pheromone.set(6, 9, rgb(255, 210, 240));
        outline(pheromone, rgb(120, 50, 90));
        pheromone.save("item/nuptial_pheromone");
        // Evolution Catalyst: a glowing orb.
        Img catalyst = item();
        disc(catalyst, 8, 8, 5, rgb(160, 90, 240));
        disc(catalyst, 8, 8, 3.2, rgb(210, 150, 255));
        disc(catalyst, 7, 7, 1.4, rgb(255, 240, 255));
        outline(catalyst, rgb(70, 30, 110));
        catalyst.save("item/evolution_catalyst");
        // Reroll Serum: a syringe.
        Img serum = item();
        line(serum, 3, 13, 11, 5, rgb(200, 220, 230));
        line(serum, 4, 13, 12, 5, rgb(170, 190, 200));
        line(serum, 5, 11, 9, 7, rgb(90, 230, 160));
        line(serum, 11, 4, 13, 2, rgb(160, 160, 170));
        line(serum, 2, 14, 3, 13, rgb(140, 140, 150));
        serum.save("item/reroll_serum");
        // Chitin Plate: one big scale, rounded at the bottom, with a ridge down its middle.
        Img plate = sprite("................",
               "................",
               "....OOOOOOOO....",
               "...OWWLLLLCCO...",
               "..OWLLLLCCCCDO..",
               "..OLLLCCCCCCDO..",
               "..OLLCCCDCCCDO..",
               "..OLCCCCDCCCDO..",
               "..OCCCCCDCCDDO..",
               "...OCCCCDCCDO...",
               "...OCCCCDCDDO...",
               "....OCCCDCDO....",
               ".....OCCDDO.....",
               "......OODO......",
               ".......OO.......",
               "................");
        plate.save("item/chitin_plate");
        // The Chitin ladder: the same scale, baked grey-brown (hardened), ember-red with a glowing rim (infernal), void-violet (void).
        recolor(plate, rgb(40, 34, 30), rgb(74, 64, 58), rgb(112, 98, 86), rgb(150, 136, 120), rgb(196, 186, 170)).save("item/hardened_chitin");
        recolor(plate, rgb(70, 14, 8), rgb(130, 30, 14), rgb(186, 60, 20), rgb(236, 120, 40), rgb(255, 210, 110)).save("item/infernal_chitin");
        recolor(plate, rgb(16, 8, 26), rgb(40, 20, 66), rgb(70, 40, 110), rgb(120, 80, 170), rgb(214, 170, 255)).save("item/void_chitin");
        // Upgrade Base and upgrade boards.
        Img base = item();
        base.fill(2, 3, 14, 13, rgb(96, 70, 44));
        base.fill(3, 4, 13, 12, rgb(130, 96, 62));
        base.set(4, 5, rgb(180, 180, 186));
        base.set(11, 5, rgb(180, 180, 186));
        base.set(4, 10, rgb(180, 180, 186));
        base.set(11, 10, rgb(180, 180, 186));
        outline(base, rgb(50, 36, 22));
        base.save("item/upgrade_base");
        int[] rims = {rgb(190, 194, 200), rgb(240, 200, 70), rgb(110, 230, 230), rgb(178, 96, 246)};
        for (int tier = 1; tier <= 4; tier++) {
            Img board = item();
            board.fill(2, 2, 14, 14, rims[tier - 1]);
            board.fill(3, 3, 13, 13, rgb(70, 52, 34));
            for (int pip = 0; pip < tier; pip++) {
                board.set(4 + pip * 2, 12, rgb(255, 255, 255));
            }
            outline(board, rgb(30, 22, 14));
            board.save("item/upgrade_board_" + tier);
        }
        upgradeIcons();
        // Genetic Circuit: a green board with gold traces.
        Img circuit = item();
        circuit.fill(2, 3, 14, 13, rgb(40, 120, 70));
        line(circuit, 3, 6, 12, 6, rgb(240, 200, 80));
        line(circuit, 3, 10, 12, 10, rgb(240, 200, 80));
        line(circuit, 7, 4, 7, 12, rgb(240, 200, 80));
        circuit.fill(9, 7, 12, 10, rgb(30, 30, 34));
        circuit.set(10, 8, rgb(200, 90, 255));
        outline(circuit, rgb(18, 50, 30));
        circuit.save("item/genetic_circuit");
        // Crystal Core: a gem in a frame.
        Img crystalCore = item();
        crystalCore.fill(3, 3, 13, 13, rgb(80, 80, 90));
        crystalCore.fill(4, 4, 12, 12, rgb(50, 50, 60));
        for (int y = 5; y < 11; y++) {
            for (int x = 5; x < 11; x++) {
                if (Math.abs(x - 7.5) + Math.abs(y - 7.5) < 4) {
                    crystalCore.set(x, y, (x + y) % 3 == 0 ? rgb(220, 255, 255) : rgb(110, 220, 240));
                }
            }
        }
        outline(crystalCore, rgb(30, 30, 36));
        crystalCore.save("item/crystal_core");
        // The book: brown cover with a gold ant.
        Img book = item();
        book.fill(3, 2, 13, 14, rgb(110, 70, 40));
        book.fill(4, 2, 13, 13, rgb(140, 90, 52));
        line(book, 4, 13, 13, 13, rgb(236, 226, 200));
        for (int[] p : new int[][] {{8, 5}, {8, 6}, {8, 8}, {8, 9}, {8, 10}, {7, 7}, {9, 7}, {6, 6}, {10, 6}, {6, 9}, {10, 9}}) {
            book.set(p[0], p[1], rgb(240, 200, 80));
        }
        outline(book, rgb(50, 30, 16));
        book.save("item/ant_identification_book");
        // Colony Scanner: a handheld with a green screen.
        Img scanner = item();
        scanner.fill(4, 2, 12, 14, rgb(90, 96, 106));
        scanner.fill(5, 3, 11, 9, rgb(20, 60, 30));
        line(scanner, 6, 7, 7, 5, rgb(100, 255, 120));
        line(scanner, 7, 5, 9, 7, rgb(100, 255, 120));
        scanner.set(6, 11, rgb(255, 80, 80));
        scanner.set(9, 11, rgb(80, 160, 255));
        outline(scanner, rgb(30, 32, 38));
        scanner.save("item/colony_scanner");
        // Genetic Analyzer: a lens over a double helix.
        Img analyzer = item();
        for (int y = 2; y < 14; y++) {
            int x1 = 6 + (int) Math.round(Math.sin(y * 0.8) * 2);
            int x2 = 6 - (int) Math.round(Math.sin(y * 0.8) * 2);
            analyzer.set(x1, y, rgb(80, 220, 255));
            analyzer.set(x2, y, rgb(220, 90, 255));
        }
        disc(analyzer, 10, 10, 3.2, argb(150, 220, 240, 255));
        line(analyzer, 12, 12, 14, 14, rgb(120, 90, 60));
        analyzer.save("item/genetic_analyzer");
        // Gem fragments.
        Object[][] fragments = {{"diamond_fragment", rgb(110, 230, 240)}, {"emerald_fragment", rgb(70, 220, 120)}};
        for (Object[] fragment : fragments) {
            Img img = item();
            int color = (Integer) fragment[1];
            int[][] shards = {{6, 9, 2}, {10, 7, 2}, {8, 12, 1}};
            for (int[] s : shards) {
                for (int y = -s[2] - 1; y <= s[2] + 1; y++) {
                    for (int x = -s[2]; x <= s[2]; x++) {
                        if (Math.abs(x) + Math.abs(y) <= s[2] + 1) {
                            img.set(s[0] + x, s[1] + y, (x + y) % 2 == 0 ? shade(color, 1.2) : color);
                        }
                    }
                }
            }
            outline(img, shade(color, 0.35));
            img.save("item/" + fragment[0]);
        }
        // Wild Ant Spawn Egg.
        Img egg = item();
        for (int y = 2; y < 15; y++) {
            for (int x = 3; x < 13; x++) {
                double dx = (x + 0.5 - 8) / 5.0;
                double dy = (y + 0.5 - 9) / (y < 9 ? 7.0 : 6.0);
                if (dx * dx + dy * dy <= 1) {
                    egg.set(x, y, rgb(150, 92, 52));
                }
            }
        }
        for (int[] spot : new int[][] {{6, 6}, {9, 9}, {7, 11}, {10, 5}}) {
            egg.set(spot[0], spot[1], rgb(30, 20, 14));
            egg.set(spot[0] + 1, spot[1], rgb(30, 20, 14));
        }
        outline(egg, rgb(60, 36, 20));
        egg.save("item/wild_ant_spawn_egg");
        // Species Upgrade: a board with a tinted gem.
        Img speciesUpgrade = item();
        speciesUpgrade.fill(2, 2, 14, 14, rgb(110, 76, 44));
        speciesUpgrade.fill(3, 3, 13, 13, rgb(140, 100, 60));
        outline(speciesUpgrade, rgb(50, 34, 20));
        speciesUpgrade.save("item/species_upgrade");
        Img speciesGem = item();
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                if (Math.abs(x - 7.5) + Math.abs(y - 7.5) <= 4) {
                    speciesGem.set(x, y, gray((x + y) % 3 == 0 ? 255 : 210));
                }
            }
        }
        speciesGem.save("item/species_upgrade_overlay");
    }

    /** A small symbol per upgrade type, drawn over the tier's board. */
    static void upgradeIcons() {
        String[] types = {"speed", "food_efficiency", "simulation", "storage", "breeding", "genetic", "hunting", "automation", "population", "evolution"};
        for (String type : types) {
            Img img = item();
            switch (type) {
                case "speed" -> {
                    for (int k = 0; k < 2; k++) {
                        line(img, 5 + k * 3, 5, 8 + k * 3, 8, rgb(255, 230, 90));
                        line(img, 8 + k * 3, 8, 5 + k * 3, 11, rgb(255, 230, 90));
                    }
                }
                case "food_efficiency" -> {
                    disc(img, 8, 8, 2.8, rgb(220, 180, 90));
                    img.set(7, 7, rgb(255, 240, 180));
                    line(img, 8, 4, 9, 3, rgb(90, 180, 60));
                }
                case "simulation" -> {
                    disc(img, 8, 8, 3.4, rgb(230, 230, 240));
                    line(img, 8, 8, 8, 5, rgb(40, 40, 50));
                    line(img, 8, 8, 10, 9, rgb(40, 40, 50));
                }
                case "storage" -> {
                    img.fill(4, 5, 12, 11, rgb(170, 120, 60));
                    line(img, 4, 7, 11, 7, rgb(90, 60, 30));
                    img.set(8, 8, rgb(230, 230, 230));
                }
                case "breeding" -> {
                    disc(img, 8, 8, 2.5, rgb(250, 246, 232));
                    disc(img, 8, 9, 1.4, rgb(230, 222, 200));
                }
                case "genetic" -> {
                    for (int y = 4; y < 12; y++) {
                        img.set(8 + (int) Math.round(Math.sin(y) * 2), y, rgb(80, 220, 255));
                        img.set(8 - (int) Math.round(Math.sin(y) * 2), y, rgb(230, 90, 255));
                    }
                }
                case "hunting" -> {
                    line(img, 5, 11, 11, 5, rgb(220, 224, 232));
                    line(img, 6, 9, 7, 10, rgb(150, 100, 50));
                    img.set(11, 4, rgb(255, 255, 255));
                }
                case "automation" -> {
                    for (int k = 0; k < 8; k++) {
                        double a = k * Math.PI / 4;
                        img.set((int) Math.round(8 + Math.cos(a) * 3.5), (int) Math.round(8 + Math.sin(a) * 3.5), rgb(200, 200, 210));
                    }
                    disc(img, 8, 8, 2.2, rgb(150, 150, 160));
                    img.set(8, 8, rgb(60, 60, 70));
                }
                case "population" -> {
                    for (int[] ant : new int[][] {{5, 7}, {9, 6}, {7, 10}}) {
                        img.set(ant[0], ant[1], rgb(40, 30, 24));
                        img.set(ant[0] + 1, ant[1], rgb(60, 44, 34));
                        img.set(ant[0] + 2, ant[1], rgb(40, 30, 24));
                    }
                }
                default -> {
                    line(img, 8, 4, 8, 12, rgb(255, 230, 120));
                    line(img, 4, 8, 12, 8, rgb(255, 230, 120));
                    line(img, 5, 5, 11, 11, rgb(255, 200, 80));
                    line(img, 11, 5, 5, 11, rgb(255, 200, 80));
                    img.set(8, 8, rgb(255, 255, 255));
                }
            }
            img.save("item/upgrade_icon_" + type);
        }
    }

    // ------------------------------------------------------------------ Particles

    static void particles() {
        for (int i = 0; i < 4; i++) {
            Img soil = new Img(8, 8);
            disc(soil, 4, 4, 1.2 + i * 0.5, gray(200 - i * 10));
            soil.set(3, 3, gray(240));
            soil.save("particle/soil_" + i);
            Img sparkle = new Img(8, 8);
            int r = 3 - i % 3;
            line(sparkle, 4, 4 - r, 4, 4 + r, gray(255));
            line(sparkle, 4 - r, 4, 4 + r, 4, gray(255));
            sparkle.set(4, 4, gray(255));
            sparkle.save("particle/sparkle_" + i);
            Img gene = new Img(8, 8);
            for (int y = 1; y < 7; y++) {
                gene.set(4 + (int) Math.round(Math.sin(y + i) * 2), y, gray(255));
                gene.set(4 - (int) Math.round(Math.sin(y + i) * 2), y, gray(200));
            }
            gene.save("particle/gene_" + i);
            Img evolution = new Img(8, 8);
            disc(evolution, 4, 4, 3.6 - i * 0.6, argb(200, 255, 255, 255));
            disc(evolution, 4, 4, 1.5, gray(255));
            evolution.save("particle/evolution_" + i);
        }
        for (int i = 0; i < 2; i++) {
            Img crumb = new Img(8, 8);
            crumb.fill(3, 3, 5 + i, 5, gray(235));
            crumb.set(3, 3, gray(255));
            crumb.save("particle/crumb_" + i);
        }
    }

    // ------------------------------------------------------------------ GUI sprites (the Genetic Laboratory console)

    /** Writes a nine-slice .mcmeta next to a sprite. */
    static void nineSlice(String path, int w, int h, int border) {
        try {
            Path file = TEX.resolve(path + ".png.mcmeta");
            Files.writeString(file, "{\n  \"gui\": {\n    \"scaling\": {\n      \"type\": \"nine_slice\",\n      \"width\": " + w + ",\n      \"height\": " + h
                    + ",\n      \"border\": " + border + "\n    }\n  }\n}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A gunmetal console button: dark body, a light top edge, a colored rim. */
    static void button(String name, int top, int bottom, int rim, int glint) {
        Img img = new Img(24, 14);
        for (int y = 0; y < 14; y++) {
            double t = y / 13.0;
            int color = rgb((int) (((top >> 16) & 0xFF) * (1 - t) + ((bottom >> 16) & 0xFF) * t),
                    (int) (((top >> 8) & 0xFF) * (1 - t) + ((bottom >> 8) & 0xFF) * t), (int) ((top & 0xFF) * (1 - t) + (bottom & 0xFF) * t));
            img.fill(0, y, 24, y + 1, color);
        }
        border(img, 0, 0, 24, 14, rgb(6, 10, 14));
        // A thin rim (lit along the top, shaded along the bottom), so even a 9-pixel button keeps a body for its text.
        border(img, 1, 1, 23, 13, rim);
        img.fill(1, 1, 23, 2, glint);
        img.fill(1, 12, 23, 13, shade(rim, 0.55));
        img.save("gui/sprites/lab/" + name);
        nineSlice("gui/sprites/lab/" + name, 24, 14, 2);
    }

    static void guiSprites() {
        Random random = new Random(21);
        // The console's frame: blue-grey gunmetal, bevelled, riveted, with a thin cyan inlay.
        Img frame = new Img(32, 32);
        noise(frame, 0, 0, 32, 32, rgb(36, 44, 56), 4, random);
        border(frame, 0, 0, 32, 32, rgb(4, 6, 10));
        for (int i = 1; i < 31; i++) {
            frame.set(i, 1, rgb(92, 112, 134));
            frame.set(1, i, rgb(92, 112, 134));
            frame.set(i, 30, rgb(16, 20, 28));
            frame.set(30, i, rgb(16, 20, 28));
            frame.set(i, 2, rgb(60, 74, 92));
            frame.set(2, i, rgb(60, 74, 92));
        }
        for (int i = 5; i < 27; i++) {
            frame.set(i, 5, argb(255, 38, 118, 146));
            frame.set(5, i, argb(255, 38, 118, 146));
            frame.set(i, 26, argb(255, 30, 90, 112));
            frame.set(26, i, argb(255, 30, 90, 112));
        }
        for (int[] rivet : new int[][] {{3, 3}, {28, 3}, {3, 28}, {28, 28}}) {
            frame.set(rivet[0], rivet[1], rgb(170, 186, 204));
            frame.set(rivet[0] + 1, rivet[1] + 1, rgb(20, 24, 32));
        }
        frame.save("gui/sprites/lab/frame");
        nineSlice("gui/sprites/lab/frame", 32, 32, 6);
        // Dark glass panels with a cyan rim and a faint sheen.
        Img glass = new Img(16, 16);
        noise(glass, 0, 0, 16, 16, rgb(12, 24, 32), 3, random);
        border(glass, 0, 0, 16, 16, rgb(4, 8, 12));
        border(glass, 1, 1, 15, 15, rgb(34, 92, 112));
        glass.fill(2, 2, 14, 3, rgb(24, 46, 58));
        for (int i = 0; i < 5; i++) {
            glass.set(3 + i, 12 - i, shade(glass.get(3 + i, 12 - i), 1.25));
        }
        glass.save("gui/sprites/lab/glass");
        nineSlice("gui/sprites/lab/glass", 16, 16, 4);
        // Slots: sunken glass with a lit rim.
        Img slot = new Img(18, 18);
        slot.fill(0, 0, 18, 18, rgb(40, 100, 122));
        slot.fill(1, 1, 17, 17, rgb(8, 14, 20));
        slot.fill(1, 1, 17, 2, rgb(4, 8, 12));
        slot.fill(1, 1, 2, 17, rgb(4, 8, 12));
        slot.fill(2, 16, 17, 17, rgb(22, 48, 60));
        slot.fill(16, 2, 17, 17, rgb(22, 48, 60));
        slot.save("gui/sprites/lab/slot");
        // Buttons: plain, hovered, disabled, and the glowing primary action.
        button("button", rgb(42, 66, 82), rgb(24, 40, 52), rgb(58, 130, 158), rgb(86, 120, 140));
        button("button_hover", rgb(54, 96, 120), rgb(32, 60, 76), rgb(110, 220, 255), rgb(150, 220, 245));
        button("button_disabled", rgb(34, 38, 44), rgb(24, 28, 32), rgb(52, 58, 66), rgb(52, 58, 66));
        button("button_primary", rgb(26, 112, 118), rgb(14, 64, 72), rgb(120, 240, 255), rgb(170, 250, 255));
        button("button_primary_hover", rgb(40, 150, 156), rgb(20, 90, 98), rgb(200, 255, 255), rgb(220, 255, 255));
        // The specimen capsule round the Queen slot: a glass tube between two riveted caps, fluid glowing at its foot.
        Img capsule = new Img(28, 40);
        for (int y = 4; y < 36; y++) {
            for (int x = 2; x < 26; x++) {
                boolean edge = x == 2 || x == 25;
                capsule.set(x, y, edge ? argb(255, 70, 170, 200) : argb(255, 10, 26, 34));
            }
            capsule.set(4, y, argb(255, 30, 70, 86));
        }
        for (int y = 28; y < 36; y++) {
            for (int x = 3; x < 25; x++) {
                int glow = 40 + (y - 28) * 10;
                capsule.set(x, y, rgb(10, glow + 40, glow + 50));
            }
        }
        for (int[] bubble : new int[][] {{8, 31}, {17, 29}, {12, 33}, {20, 32}}) {
            capsule.set(bubble[0], bubble[1], rgb(170, 250, 255));
        }
        for (int[] cap : new int[][] {{0, 0}, {0, 36}}) {
            capsule.fill(cap[0], cap[1], 28, cap[1] + 4, rgb(70, 84, 100));
            capsule.fill(cap[0], cap[1], 28, cap[1] + 1, rgb(130, 150, 170));
            capsule.fill(cap[0], cap[1] + 3, 28, cap[1] + 4, rgb(26, 32, 40));
            capsule.set(3, cap[1] + 2, rgb(190, 200, 214));
            capsule.set(24, cap[1] + 2, rgb(190, 200, 214));
        }
        capsule.save("gui/sprites/lab/capsule");
    }

    // ------------------------------------------------------------------ Side-view ant sprites (the ants in the tunnels)

    static final int SIDE_CELL_W = 24;
    static final int SIDE_CELL_H = 12;

    /**
     * entity/ant_side.png: ants seen from the side, head to the right, standing on the cell's bottom row. Columns are
     * four walking frames; per caste three rows: body (head, thorax, legs, antennae), gaster, crystals. Grey, tinted in
     * game with the species' colors. A white 4x4 block at (88, 108) is the carried crumb.
     */
    static void sideSprites() {
        Img img = new Img(96, 112);
        String[] castes = {"worker", "soldier", "queen"};
        for (int c = 0; c < 3; c++) {
            for (int frame = 0; frame < 4; frame++) {
                int ox = frame * SIDE_CELL_W;
                int body = (c * 3) * SIDE_CELL_H;
                int gaster = (c * 3 + 1) * SIDE_CELL_H;
                int crystal = (c * 3 + 2) * SIDE_CELL_H;
                sideAnt(img, castes[c], frame, ox, body, gaster, crystal);
            }
        }
        img.fill(88, 108, 92, 112, gray(235));
        img.save("entity/ant_side");
    }

    static void ellipse(Img img, double cx, double cy, double rx, double ry, int color) {
        for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
            for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
                double dx = (x + 0.5 - cx) / rx;
                double dy = (y + 0.5 - cy) / ry;
                if (dx * dx + dy * dy <= 1) {
                    img.set(x, y, color);
                }
            }
        }
    }

    static void sideAnt(Img img, String caste, int frame, int ox, int body, int gasterRow, int crystalRow) {
        boolean queen = caste.equals("queen");
        boolean soldier = caste.equals("soldier");
        int base = gray(224);
        int shadow = gray(176);
        int far = gray(118);
        int dark = gray(60);
        // Gaster (its own layer): an oval with the bands of its segments, darker underneath, a highlight on top.
        double gcx = queen ? 5.8 : 6.0;
        double grx = queen ? 5.3 : soldier ? 3.9 : 4.2;
        double gry = queen ? 3.1 : 2.3;
        double gcy = queen ? 6.6 : 7.2;
        ellipse(img, ox + gcx, gasterRow + gcy, grx, gry, base);
        for (int y = 0; y < SIDE_CELL_H; y++) {
            for (int x = 0; x < SIDE_CELL_W; x++) {
                int p = img.get(ox + x, gasterRow + y);
                if (p == 0) {
                    continue;
                }
                if ((x + 1) % 3 == 0) {
                    p = shadow;
                }
                if (y > gcy) {
                    p = shade(p, 0.86);
                }
                img.set(ox + x, gasterRow + y, p);
            }
        }
        img.set(ox + (int) gcx, gasterRow + (int) Math.ceil(gcy - gry), gray(255));
        img.set(ox + (int) gcx + 1, gasterRow + (int) Math.ceil(gcy - gry), gray(255));
        int top = queen ? 5 : 6;
        int x0 = queen ? 11 : 11;
        int x1 = queen ? 17 : 16;
        int[] hips = queen ? new int[] {12, 14, 16} : new int[] {12, 14, 15};
        for (int pass = 0; pass < 2; pass++) {
            boolean near = pass == 1;
            // Legs: thin, the front pair reaching forward, the hind pair back; the far side in the opposite phase.
            for (int i = 0; i < 3; i++) {
                boolean groupA = (i % 2 == 0) == near;
                double phase = frame * Math.PI / 2 + (groupA ? 0 : Math.PI);
                double swing = Math.sin(phase) * 1.3;
                boolean lifted = Math.cos(phase) > 0.4;
                int hx = hips[i];
                double reach = (i - 1) * 3.2;
                int kneeX = (int) Math.round(hx + reach * 0.55 + swing * 0.5);
                int footX = (int) Math.round(hx + reach + swing);
                int footY = lifted ? 10 : 11;
                int color = near ? shadow : far;
                line(img, ox + hx, body + 8, ox + kneeX, body + 9, color);
                line(img, ox + kneeX, body + 9, ox + footX, body + footY, color);
            }
            if (pass == 0) {
                // The waist (petiole node) and the slim, arched thorax.
                img.set(ox + 10, body + 7, shadow);
                img.set(ox + 10, body + 6, base);
                for (int x = x0; x <= x1; x++) {
                    int t = x >= x1 - 1 ? top - 1 : x <= x0 + 1 ? top + 1 : top;
                    for (int y = t; y <= 8; y++) {
                        img.set(ox + x, body + y, y == 8 ? shadow : base);
                    }
                }
                if (queen) {
                    // Wing scars.
                    img.set(ox + 14, body + top - 1, shadow);
                    img.set(ox + 15, body + top - 1, shadow);
                }
            }
        }
        // Head: bigger for soldiers, with an eye, mandibles and an elbowed antenna that twitches between frames.
        double hcx = queen ? 19.8 : soldier ? 19.6 : 18.9;
        double hrx = soldier ? 3.0 : 2.2;
        double hry = soldier ? 2.4 : 1.7;
        double hcy = soldier ? 6.0 : 6.3;
        ellipse(img, ox + hcx, body + hcy, hrx, hry, base);
        img.set(ox + (int) Math.round(hcx + 0.6), body + (int) Math.round(hcy - 0.8), dark);
        int mx = (int) Math.round(hcx + hrx);
        img.set(ox + mx, body + (int) hcy + 1, dark);
        img.set(ox + mx - 1, body + (int) hcy + 2, dark);
        if (soldier) {
            img.set(ox + mx + 1, body + (int) hcy + 1, dark);
            img.set(ox + mx + 1, body + (int) hcy + 2, dark);
        }
        int twitch = frame % 2;
        int ax = (int) Math.round(hcx);
        int ay = body + (int) Math.round(hcy - hry);
        line(img, ox + ax, ay, ox + ax - 1, ay - 3, shadow);
        line(img, ox + ax - 1, ay - 3, ox + ax + 3, ay - 3 + twitch, shadow);
        img.set(ox + ax + 4, ay - 2 + twitch, shadow);
        // Crystals: spikes rising from the gaster and the thorax.
        int[][] spikes = {{4, 3}, {7, 4}, {9, 2}, {13, 2}};
        for (int[] spike : spikes) {
            int sx = ox + spike[0];
            int base0 = spike[0] >= 12 ? crystalRow + top : crystalRow + (int) Math.ceil(gcy - gry) + 1;
            for (int y = 0; y < spike[1]; y++) {
                img.set(sx, base0 - spike[1] + y, gray(y == 0 ? 255 : 210));
                if (y > 0 && spike[1] > 2) {
                    img.set(sx + 1, base0 - spike[1] + y, gray(178));
                }
            }
        }
    }

    // ------------------------------------------------------------------ Chitin gear, Guard Ants

    static final int CH_OUT = rgb(52, 30, 16);
    static final int CH_DARK = rgb(104, 58, 30);
    static final int CH_BASE = rgb(152, 92, 48);
    static final int CH_LIGHT = rgb(198, 134, 78);
    static final int CH_SHINE = rgb(238, 192, 130);
    static final int STICK = rgb(112, 82, 48);
    static final int STICK_DARK = rgb(74, 52, 28);

    static final int CH_EYE = rgb(26, 16, 12);
    static final int CH_EYE_SHINE = rgb(150, 112, 88);

    /** A copy of a chitin sprite in other colours: outline, dark, base, light and shine take the five given. */
    static Img recolor(Img source, int out, int dark, int base, int light, int shine) {
        Img copy = new Img(source.w, source.h);
        int[] from = {CH_OUT, CH_DARK, CH_BASE, CH_LIGHT, CH_SHINE};
        int[] to = {out, dark, base, light, shine};
        for (int y = 0; y < source.h; y++) {
            for (int x = 0; x < source.w; x++) {
                int color = source.get(x, y);
                for (int i = 0; i < from.length; i++) {
                    if (color == from[i]) {
                        color = to[i];
                        break;
                    }
                }
                copy.set(x, y, color);
            }
        }
        return copy;
    }

    /**
     * A sprite from rows of letters: O outline, D dark, C chitin, L light, W shine, S/s stick, E/e compound eye and its
     * glint; anything else is clear.
     */
    static Img sprite(String... rows) {
        Img img = item();
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int color = switch (rows[y].charAt(x)) {
                    case 'O' -> CH_OUT;
                    case 'D' -> CH_DARK;
                    case 'C' -> CH_BASE;
                    case 'L' -> CH_LIGHT;
                    case 'W' -> CH_SHINE;
                    case 'S' -> STICK;
                    case 's' -> STICK_DARK;
                    case 'E' -> CH_EYE;
                    case 'e' -> CH_EYE_SHINE;
                    default -> 0;
                };
                if (color != 0) {
                    img.set(x, y, color);
                }
            }
        }
        return img;
    }

    static void chitinGear() {
        // Armor, drawn like vanilla's icons. The helmet is an ant's head: elbowed antennae, compound eyes, closed jaws.
        sprite("..OO........OO..",
               "..OD........DO..",
               "...D........D...",
               "...D........D...",
               "....D......D....",
               ".....O....O.....",
               "....OOOOOOOO....",
               "...OLWWLCCCDO...",
               "..OLWLCCCCCCDO..",
               "..OEeCCDDCCEeO..",
               "..OEEECDDCEEEO..",
               "..ODEECDDCEEDO..",
               "...OCCDDDDCCO...",
               "...OCDO..ODCO...",
               "....ODCOOCDO....",
               ".....OO..OO.....").save("item/chitin_helmet");
        sprite("................",
               "..OOOO....OOOO..",
               ".OWLLO....OLCDO.",
               ".OLCCOOOOOOCCDO.",
               ".OCCLLLLLLCCCDO.",
               "..OOCCCCCCCCOO..",
               "...OCCCCCCCDO...",
               "...OCCCCCCCDO...",
               "...ODDDDDDDDO...",
               "....OOCCCDOO....",
               "....OLCCCCDO....",
               "...OLCCCCCCDO...",
               "...OCCCCCCCDO...",
               "....OOOOOOOO....",
               "................",
               "................").scaled().save("item/chitin_chestplate");
        sprite("................",
               "................",
               "...OOOOOOOOOO...",
               "...OLDDLDDLDO...",
               "...OCCCCCCCDO...",
               "...OCCCOOCCDO...",
               "...OCCO..OCDO...",
               "...OWLO..OLDO...",
               "...ODDO..ODDO...",
               "...OCCO..OCDO...",
               "...OCCO..OCDO...",
               "...OCCO..OCDO...",
               "...OOOO..OOOO...",
               "................",
               "................",
               "................").scaled().save("item/chitin_leggings");
        sprite("................",
               "................",
               "................",
               "................",
               "...OOOO..OOOO...",
               "...OLDO..OLDO...",
               "...OCCO..OCDO...",
               "...OCCO..OCDO...",
               "..OCCCO..OCCDO..",
               ".OLCCCO..OCCCDO.",
               ".OOOOOO..OOOOOO.",
               "................",
               "................",
               "................",
               "................",
               "................").scaled().save("item/chitin_boots");

        // Tools, drawn like vanilla's: a straight three-pixel stick from the bottom left and a chitin head with grooves
        // between its plates.
        sprite(".............OOO",
               "............OWLO",
               "...........OWLCO",
               "..........OWLCO.",
               ".........OWDCO..",
               "........OWLCO...",
               ".......OWLCO....",
               "..OO..OWDCO.....",
               "..OCO.OLCO......",
               "...OCOLCO.......",
               "...OCCDO........",
               "...sSOCCO.......",
               "..sSs.OOCO......",
               "OOSs....OO......",
               "OCO.............",
               "OOO.............").save("item/chitin_sword");
        sprite("................",
               "................",
               "......OOOOO.....",
               ".....OWWLDCOsS..",
               "......OOOOLCSs..",
               "..........sLCO..",
               ".........sSsCDO.",
               "........sSs.OCO.",
               ".......sSs..ODO.",
               "......sSs...OLO.",
               ".....sSs....OCO.",
               "....sSs......O..",
               "...sSs..........",
               "..sSs...........",
               "..ss............",
               "................").save("item/chitin_pickaxe");
        sprite("................",
               ".........OO.....",
               "........OWLO....",
               ".......OWLCO....",
               "......OWLCDOsS..",
               ".....OWLCDCDSs..",
               ".....OLCDCCDDO..",
               "......OOsSsDDO..",
               ".......sSs.OO...",
               "......sSs.......",
               ".....sSs........",
               "....sSs.........",
               "...sSs..........",
               "..sSs...........",
               "..ss............",
               "................").save("item/chitin_axe");
        sprite("................",
               "................",
               "...........OOO..",
               "..........OWLCO.",
               ".........OWLCDO.",
               "........OWLCDDO.",
               ".........OLCDO..",
               "........sOCDO...",
               ".......sSsOO....",
               "......sSs.......",
               ".....sSs........",
               "....sSs.........",
               "...sSs..........",
               "..sSs...........",
               "..ss............",
               "................").save("item/chitin_shovel");
        sprite("................",
               "......OOOO......",
               ".....OWLCDO.....",
               "......OOOLCOsS..",
               ".........OCDSs..",
               "..........sOO...",
               ".........sSs....",
               "........sSs.....",
               ".......sSs......",
               "......sSs.......",
               ".....sSs........",
               "....sSs.........",
               "...sSs..........",
               "..sSs...........",
               "..ss............",
               "................").save("item/chitin_hoe");
        chitinArmorLayers();
        chitinShield();
        // Rally Pheromone: a tube of glowing orange-red scent.
        Img rally = item();
        tube(rally, null, rgb(150, 110, 70), false);
        for (int y = 8; y < 15; y++) {
            for (int x = 6; x < 10; x++) {
                rally.set(x, y, y == 8 ? rgb(255, 170, 90) : rgb(230 - (y - 8) * 6, 96 + (x == 6 ? 20 : 0), 40));
            }
        }
        rally.set(7, 6, argb(160, 255, 150, 80));
        rally.set(8, 5, argb(110, 255, 150, 80));
        rally.save("item/rally_pheromone");
        // Guard Ant: a red-capped tube with a big soldier inside.
        Img guard = item();
        tube(guard, null, rgb(180, 40, 36), false);
        for (int y = 8; y < 15; y++) {
            for (int x = 6; x < 10; x++) {
                guard.set(x, y, argb(110, 230, 210, 170));
            }
        }
        int[][] ant = {{7, 8}, {8, 8}, {6, 9}, {9, 9}, {7, 10}, {8, 10}, {7, 11}, {8, 11}, {6, 12}, {9, 12}, {7, 13}, {8, 13}, {7, 14}, {8, 14}};
        for (int[] p : ant) {
            guard.set(p[0], p[1], p[1] == 8 ? rgb(150, 40, 30) : rgb(60, 30, 20));
        }
        guard.save("item/guard_ant");
    }

    /**
     * The worn armor, all in chitin scales. Layer "humanoid" (64x32) holds the helmet, chestplate (body and arms) and
     * boots (the bottom of the legs); "humanoid_leggings" the leggings (the hips and the legs); "humanoid_baby" (64x64)
     * everything for baby mobs.
     */
    static void chitinArmorLayers() {
        Img outer = new Img(64, 32);
        Random random = new Random(41);
        plates(outer, 0, 0, 8, 8, 8, 0, 8, random);      // head
        antFace(outer, 8, 8, 8);
        antennaSkins(outer);
        // Chestplate: an ant's thorax, a groove, a narrow waist (the sides and the corners cut away), the abdomen.
        plates(outer, 16, 16, 8, 12, 4, 0, 12, random);
        band(outer, 16, 16, 8, 4, 6, CH_OUT);
        for (int row = 7; row <= 8; row++) {
            for (int x = 0; x < 24; x++) {
                int faceX = x < 4 ? -1 : x < 12 ? x - 4 : x < 16 ? -1 : x - 16;
                if (faceX < 1 || faceX > 6) {
                    outer.set(16 + x, 20 + row, 0);
                } else if (faceX == 1 || faceX == 6) {
                    outer.set(16 + x, 20 + row, CH_OUT);
                }
            }
        }
        band(outer, 16, 16, 8, 4, 9, CH_LIGHT);
        // Arms: a lit shoulder plate, scales, a studded cuff at the elbow and bare forearms.
        plates(outer, 40, 16, 4, 12, 4, 0, 9, random);
        band(outer, 40, 16, 4, 4, 0, CH_SHINE);
        band(outer, 40, 16, 4, 4, 1, CH_LIGHT);
        band(outer, 40, 16, 4, 4, 2, CH_OUT);
        studded(outer, 40, 16, 4, 4, 8);
        // Boots: a cuff, scales, a dark sole.
        plates(outer, 0, 16, 4, 12, 4, 8, 12, random);
        studded(outer, 0, 16, 4, 4, 8);
        band(outer, 0, 16, 4, 4, 11, CH_OUT);
        outer.save("entity/equipment/humanoid/chitin");

        Img inner = new Img(64, 32);
        // Leggings: a studded belt over the hips, scaled legs with a lit knee cap and a dark joint below it.
        plates(inner, 16, 16, 8, 12, 4, 7, 12, random);
        studded(inner, 16, 16, 8, 4, 7);
        plates(inner, 0, 16, 4, 12, 4, 0, 10, random);
        band(inner, 0, 16, 4, 4, 6, CH_OUT);
        for (int x = 5; x <= 6; x++) {
            inner.set(x, 23, CH_SHINE);
            inner.set(x, 24, CH_LIGHT);
        }
        inner.set(4, 24, CH_LIGHT);
        inner.set(7, 24, CH_BASE);
        inner.set(4, 25, CH_DARK);
        inner.set(7, 25, CH_DARK);
        inner.save("entity/equipment/humanoid_leggings/chitin");

        // Babies: their own smaller boxes in one 64x64 texture (see HumanoidModel.createBabyArmorMesh).
        Img baby = new Img(64, 64);
        plates(baby, 0, 0, 9, 8, 8, 0, 8, random);       // head, 9 wide
        antFace(baby, 8, 8, 9);
        plates(baby, 0, 17, 6, 5, 3, 0, 5, random);      // body
        band(baby, 0, 17, 6, 3, 3, CH_OUT);
        plates(baby, 30, 17, 2, 5, 3, 0, 5, random);     // left arm
        plates(baby, 30, 25, 2, 5, 3, 0, 5, random);     // right arm
        band(baby, 30, 17, 2, 3, 4, CH_OUT);
        band(baby, 30, 25, 2, 3, 4, CH_OUT);
        plates(baby, 0, 36, 6, 2, 3, 0, 2, random);      // waist (leggings)
        studded(baby, 0, 36, 6, 3, 0);
        plates(baby, 18, 17, 3, 4, 3, 0, 4, random);     // legs
        plates(baby, 18, 24, 3, 4, 3, 0, 4, random);
        plates(baby, 0, 25, 3, 1, 3, 0, 1, random);      // feet
        plates(baby, 0, 29, 3, 1, 3, 0, 1, random);
        baby.save("entity/equipment/humanoid_baby/chitin");
    }

    /**
     * The Chitin Shield's 64x64 skin, one pixel per model pixel (DataGen's shield model maps its faces onto it): the
     * front (2,2 to 14,24) in scales with a dark rim, a ridge down the middle and a lit boss like an ant's head; the
     * back (16,2) dark chitin with a strap; the rim's edges (30,2 / 32,2 and 2,26 / 16,26); the handle (40,2).
     */
    static void chitinShield() {
        Img img = new Img(64, 64);
        Random random = new Random(7);
        for (int y = 0; y < 22; y++) {
            for (int x = 0; x < 12; x++) {
                boolean rim = x == 0 || x == 11 || y == 0 || y == 21;
                boolean corner = (x == 0 || x == 11) && (y == 0 || y == 21);
                int front = rim ? CH_OUT : x == 5 || x == 6 ? (x == 5 ? CH_DARK : shade(CH_DARK, 0.85)) : scaleColor(x - 1, y + 1, random);
                img.set(2 + x, 2 + y, corner ? 0 : front);
                int back = rim ? CH_OUT : y >= 9 && y <= 12 ? (y == 9 || y == 12 ? STICK_DARK : STICK) : shade(CH_DARK, 0.85 + random.nextDouble() * 0.15);
                img.set(16 + x, 2 + y, corner ? 0 : back);
            }
        }
        // The boss: a lit oval over the ridge, with two eyes.
        int[][] boss = {{4, 8}, {5, 7}, {6, 7}, {7, 8}, {4, 9}, {5, 9}, {6, 9}, {7, 9}, {4, 10}, {5, 10}, {6, 10}, {7, 10}, {5, 11}, {6, 11}};
        for (int[] p : boss) {
            img.set(2 + p[0], 2 + p[1], p[1] <= 8 ? CH_SHINE : CH_LIGHT);
        }
        img.set(2 + 4, 2 + 9, CH_EYE);
        img.set(2 + 7, 2 + 9, CH_EYE);
        for (int y = 0; y < 22; y++) {
            img.set(30, 2 + y, CH_DARK);
            img.set(32, 2 + y, CH_DARK);
        }
        for (int x = 0; x < 12; x++) {
            img.set(2 + x, 26, CH_LIGHT);
            img.set(16 + x, 26, CH_DARK);
        }
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                img.set(40 + x, 2 + y, y == 0 || y == 5 ? STICK_DARK : STICK);
            }
        }
        img.save("item/chitin_shield");
    }

    /**
     * The helmet's face as an ant's head, on the front face at (fx, fy), {@code w} wide: compound eyes wrapping round the
     * front corners onto the sides, a ridge down the middle, and jaws with a gap between them at the bottom.
     */
    static void antFace(Img img, int fx, int fy, int w) {
        for (int y = fy + 2; y <= fy + 4; y++) {
            for (int x = 0; x < 2; x++) {
                img.set(fx + x, y, CH_EYE);
                img.set(fx + w - 1 - x, y, CH_EYE);
                img.set(fx - 2 + x, y, CH_EYE);
                img.set(fx + w + x, y, CH_EYE);
            }
        }
        for (int x : new int[] {fx - 1, fx + 1, fx + w - 2, fx + w}) {
            img.set(x, fy + 2, CH_EYE_SHINE);
        }
        double middle = (w - 1) / 2.0;
        for (int x = 0; x < w; x++) {
            if (Math.abs(x - middle) <= 0.5) {
                for (int y = fy + 2; y <= fy + 5; y++) {
                    img.set(fx + x, y, CH_DARK);
                }
            }
        }
        // Jaws: the bottom two rows, light on top, dark tips pointing in, nothing between them.
        for (int x = 0; x < w; x++) {
            int fromEdge = Math.min(x, w - 1 - x);
            img.set(fx + x, fy + 6, fromEdge < 2 ? (x < w / 2 ? CH_LIGHT : CH_BASE) : fromEdge == 2 ? CH_DARK : 0);
            img.set(fx + x, fy + 7, fromEdge == 1 ? CH_DARK : fromEdge == 2 ? CH_OUT : 0);
        }
    }

    /**
     * The antennae's skins, which take the place of the hat layer (32,0 to 64,16; the helmet model has no hat): see
     * {@code ChitinHelmetModel} for the boxes. Dark segments, a lit club at the tip.
     */
    static void antennaSkins(Img img) {
        for (int y = 0; y < 7; y++) {
            for (int x = 32; x < 50; x++) {
                img.set(x, y, (x + y) % 2 == 0 ? CH_OUT : CH_DARK);
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 50; x < 58; x++) {
                img.set(x, y, y == 0 ? CH_LIGHT : CH_BASE);
            }
        }
        // The jaws (a 1x1x4 box at 32,8): darker than the face so they stand out against it, a lit ridge on top
        // brightening to the tip, black underneath; a pale tooth (a 1x1x1 box at 54,8).
        int[] ridge = {CH_DARK, CH_BASE, CH_LIGHT, CH_LIGHT};
        for (int z = 0; z < 4; z++) {
            img.set(36, 8 + z, ridge[z]);
            img.set(37, 8 + z, CH_OUT);
            img.set(32 + z, 12, z % 2 == 0 ? CH_DARK : CH_OUT);
            img.set(37 + z, 12, z % 2 == 0 ? CH_DARK : CH_OUT);
        }
        img.set(36, 12, CH_LIGHT);
        img.set(41, 12, CH_OUT);
        img.set(55, 8, CH_SHINE);
        img.set(56, 8, CH_LIGHT);
        for (int x = 54; x < 58; x++) {
            img.set(x, 9, x == 55 ? CH_SHINE : CH_LIGHT);
        }
        // The spines (a 1x1x4 box at 44,8): dark at the root, lighter to a bright point at the back face (53,12).
        int[] thorn = {CH_DARK, CH_BASE, CH_LIGHT, CH_SHINE};
        for (int z = 0; z < 4; z++) {
            img.set(48, 8 + z, thorn[z]);
            img.set(49, 8 + z, CH_OUT);
            img.set(44 + z, 12, thorn[3 - z] == CH_SHINE ? CH_LIGHT : thorn[3 - z]);
            img.set(49 + z, 12, thorn[z] == CH_SHINE ? CH_LIGHT : thorn[z]);
        }
        img.set(48, 12, CH_OUT);
        img.set(53, 12, CH_SHINE);
        // The wings (4 x 13 planes at 56,16, front and back side by side): a pale membrane, a dark leading edge on the
        // outside, veins from the root (one along, two across, one fork), the tip rounded off.
        for (int face = 0; face < 2; face++) {
            int u = 56 + face * 4;
            for (int y = 0; y < 13; y++) {
                for (int x = 0; x < 4; x++) {
                    int outer = face == 0 ? 0 : 3;   // the wings' outer edges, as each face is seen
                    int inner = 3 - outer;
                    int along = face == 0 ? 2 : 1;   // the long vein, nearer the outer edge
                    int color;
                    if (x == outer) {
                        color = y < 9 ? CH_DARK : CH_BASE;
                    } else if (y == 0) {
                        color = CH_DARK;
                    } else if (x == along && y < 10 || y == 4 && x != inner || y == 8 && x == along) {
                        color = CH_BASE;
                    } else {
                        color = y < 2 ? CH_LIGHT : CH_SHINE;
                    }
                    if (y == 12 && x != along || y == 11 && x == outer) {
                        color = 0;
                    }
                    img.set(u + x, 16 + y, color);
                }
            }
        }
    }

    /**
     * Paints the faces of one model box (texture corner u, v; size w, h, d) with chitin scales between side rows
     * {@code from} and {@code to}; the top face when the rows start at 0, the bottom face when they reach h. The four
     * side faces lie side by side in the texture, so the scales run on round the box.
     */
    static void plates(Img img, int u, int v, int w, int h, int d, int from, int to, Random random) {
        int sideY = v + d;
        int[][] faces = {{u, d}, {u + d, w}, {u + d + w, d}, {u + d + d + w, w}};
        for (int[] face : faces) {
            for (int y = from; y < to; y++) {
                for (int x = 0; x < face[1]; x++) {
                    int color = scaleColor(face[0] - u + x, y, random);
                    img.set(face[0] + x, sideY + y, x == 0 || x == face[1] - 1 ? shade(color, 0.88) : color);
                }
            }
        }
        if (from == 0) {
            for (int y = 0; y < d; y++) {
                for (int x = 0; x < w; x++) {
                    img.set(u + d + x, v + y, scaleColor(x, y + 1, random));
                }
            }
        }
        if (to == h) {
            for (int y = 0; y < d; y++) {
                for (int x = 0; x < w; x++) {
                    img.set(u + d + w + x, v + y, shade(CH_DARK, 0.9));
                }
            }
        }
    }

    /** A scale's rows: from which to which pixel it covers, top to bottom (rounded at the bottom). */
    static final int[][] SCALE = {{0, 5}, {0, 5}, {0, 5}, {1, 4}, {2, 3}};
    static final int SCALE_WIDTH = 6;
    static final int SCALE_STEP = 3;

    /**
     * Chitin scales, like a pangolin's: rows of rounded scales 6 pixels wide, a new row every 3 pixels, each row shifted
     * by half a scale. Every scale lies over the top of the ones below it, so what shows of each is its rounded lower
     * part: lit at the top, a dark rim, a darker tip.
     */
    static int scaleColor(int x, int y, Random random) {
        int row = Math.floorDiv(y, SCALE_STEP);
        int color = CH_DARK;
        for (int r = row - SCALE.length / SCALE_STEP - 1; r <= row; r++) {
            int dy = y - r * SCALE_STEP;
            if (dy < 0 || dy >= SCALE.length) {
                continue;
            }
            int dx = Math.floorMod(x - Math.floorMod(r, 2) * (SCALE_WIDTH / 2), SCALE_WIDTH);
            if (dx < SCALE[dy][0] || dx > SCALE[dy][1]) {
                continue;
            }
            boolean bottom = dy + 1 >= SCALE.length || dx < SCALE[dy + 1][0] || dx > SCALE[dy + 1][1];
            boolean side = dx == SCALE[dy][0] || dx == SCALE[dy][1];
            // Low contrast on purpose: at the size armor is seen, black tips and bright glints read as spots, not scales.
            if (bottom) {
                color = dy == SCALE.length - 1 ? shade(CH_DARK, 0.85) : CH_DARK;
            } else if (side && dy > 0) {
                color = shade(CH_BASE, 0.88);
            } else if (dy == 0) {
                color = CH_LIGHT;
            } else if (dy == 1 && dx <= SCALE_WIDTH / 2) {
                color = shade(CH_BASE, 1.12);
            } else {
                color = CH_BASE;
            }
            break;
        }
        return shade(color, 0.97 + random.nextDouble() * 0.06);
    }

    /** One side row of a box (all four side faces) in a colour, or cleared with 0. */
    static void band(Img img, int u, int v, int w, int d, int row, int color) {
        for (int x = 0; x < 2 * (w + d); x++) {
            img.set(u + x, v + d + row, color);
        }
    }

    /** A belt or cuff: a dark band with a lit stud every third pixel. */
    static void studded(Img img, int u, int v, int w, int d, int row) {
        for (int x = 0; x < 2 * (w + d); x++) {
            img.set(u + x, v + d + row, x % 3 == 1 ? CH_LIGHT : CH_OUT);
        }
    }

    // ------------------------------------------------------------------ The nest-building palette and the new chambers
    //
    // Painted on a tone map: each pixel is an index into the material's ramp (dark to light), so shading is +-1 tone and
    // the clusters stay on the ramp. Clusters come from tileable value noise on the 16 px torus (two octaves, quantized),
    // which gives vanilla's 2-4 px blotches without single-pixel noise. Every material has a signature of its own:
    // soil pellets (what ants carry out of their tunnels), adobe bricks with rounded corners, overlapping chitin scales,
    // an ant caught in amber, fungus gardens with gongylidia knobs, royal plum chitin set in gold.

    static int[] ramp(int... rgb) {
        int[] out = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            out[i] = 0xFF000000 | rgb[i];
        }
        return out;
    }

    /** Tileable value noise, 0..1, lattice every {@code cell} px (a divisor of 16), smoothstep between lattice points. */
    static double[][] valueNoise(long seed, int cell) {
        Random random = new Random(seed);
        int n = 16 / cell;
        double[][] lattice = new double[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                lattice[y][x] = random.nextDouble();
            }
        }
        double[][] out = new double[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double gx = x / (double) cell;
                double gy = y / (double) cell;
                int x0 = (int) Math.floor(gx);
                int y0 = (int) Math.floor(gy);
                double tx = gx - x0;
                double ty = gy - y0;
                tx = tx * tx * (3 - 2 * tx);
                ty = ty * ty * (3 - 2 * ty);
                double a = lattice[y0 % n][x0 % n];
                double b = lattice[y0 % n][(x0 + 1) % n];
                double c = lattice[(y0 + 1) % n][x0 % n];
                double d = lattice[(y0 + 1) % n][(x0 + 1) % n];
                out[y][x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
            }
        }
        return out;
    }

    /** A tone map from two octaves of noise and a little per-pixel jitter, spread over {@code tones} tones. */
    static int[][] clusters(long seed, int tones, double jitter, double bias) {
        double[][] big = valueNoise(seed, 8);
        double[][] small = valueNoise(seed * 31 + 7, 4);
        double[][] fine = valueNoise(seed * 17 + 3, 2);
        Random random = new Random(seed ^ 0x5DEECE66DL);
        int[][] t = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double v = big[y][x] * 0.45 + small[y][x] * 0.35 + fine[y][x] * 0.2 + (random.nextDouble() - 0.5) * jitter + bias;
                // Stretch the middle so all tones get used.
                v = Math.clamp((v - 0.5) * 1.9 + 0.5, 0, 0.9999);
                t[y][x] = (int) (v * tones);
            }
        }
        return t;
    }

    static void render(Img img, int[][] t, int[] ramp) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.set(x, y, ramp[Math.clamp(t[y][x], 0, ramp.length - 1)]);
            }
        }
    }

    static void tone(int[][] t, int x, int y, int delta, int max) {
        int wx = Math.floorMod(x, 16);
        int wy = Math.floorMod(y, 16);
        t[wy][wx] = Math.clamp(t[wy][wx] + delta, 0, max);
    }

    /**
     * Soil pellets: the balls of earth ants carry out of their tunnels. A 2x2 crumb lit at its top left, with a contact
     * shadow under its right; wraps across the edges so the block tiles.
     */
    static void pellets(int[][] t, int max, long seed, int count) {
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(16);
            int y = random.nextInt(16);
            int base = max - 1 - random.nextInt(2);
            t[y][x] = Math.min(max, base + 1);
            t[y][(x + 1) % 16] = base;
            t[(y + 1) % 16][x] = base;
            t[(y + 1) % 16][(x + 1) % 16] = base - 1;
            tone(t, x + 1, y + 2, -2, max);
            tone(t, x + 2, y + 1, -1, max);
        }
    }

    /**
     * Brick courses: each brick is its own cluster field (a little lighter or darker than its neighbours), lit along its
     * top, shaded along its bottom; the mortar is the last row and column of each brick. {@code rounded} knocks the
     * corners off (hand-pressed adobe).
     */
    static int[][] bricks(long seed, int tones, int[] rows, int[] widths, int[] offsets, boolean rounded, int[][] mortar) {
        int[][] t = new int[16][16];
        Random random = new Random(seed);
        int y0 = 0;
        for (int row = 0; row < rows.length; row++) {
            int h = rows[row];
            int w = widths[row];
            for (int bx = offsets[row]; bx < offsets[row] + 16; bx += w) {
                double bias = (random.nextDouble() - 0.5) * 0.22;
                int[][] field = clusters(random.nextLong(), tones, 0.18, bias);
                for (int dy = 0; dy < h; dy++) {
                    for (int dx = 0; dx < w; dx++) {
                        int x = Math.floorMod(bx + dx, 16);
                        int y = y0 + dy;
                        boolean seamRow = dy == h - 1;
                        boolean seamCol = dx == w - 1;
                        boolean corner = rounded && (dx == 0 || dx == w - 2) && (dy == 0 || dy == h - 2);
                        if (seamRow || seamCol) {
                            mortar[y][x] = 1;
                            t[y][x] = 0;
                        } else if (corner) {
                            mortar[y][x] = 2;
                            t[y][x] = 1;
                        } else {
                            int v = field[y][x];
                            if (dy == 0) {
                                v += 1;
                            } else if (dy == h - 2) {
                                v -= 1;
                            }
                            if (dx == w - 2 && dy > 0) {
                                v -= 1;
                            }
                            t[y][x] = Math.clamp(v, 1, tones - 1);
                        }
                    }
                }
            }
            y0 += h;
        }
        return t;
    }

    /**
     * Overlapping scales, like the chitin of the armour: 8 px wide, a new row every 4, each row shifted half a scale.
     * Each scale is an arch: its top edge curves down at the sides, where the scale above overlaps it (a shadow pocket);
     * a lit crest runs along the arch, and the scale darkens towards its lower rim.
     *
     * @param base  tone of a scale's body in {@code body} (it varies by one with the clusters and darkens downwards)
     */
    static Img scales(long seed, int[] body, int base, int crest, int crestEdge, int glint, int pocket, int seam) {
        Img img = new Img(16, 16);
        int[][] t = clusters(seed, 3, 0.2, 0);
        Random random = new Random(seed + 1);
        // Two rows of scales 8 px square per block, the second shifted half a scale: big enough to read as arches.
        for (int y = 0; y < 16; y++) {
            int row = y / 8;
            int dy = y % 8;
            for (int x = 0; x < 16; x++) {
                int dx = Math.floorMod(x + row * 4, 8);
                double edge = Math.abs(dx - 3.5) / 3.5;
                // Each scale's lower rim is a U: low in the middle, rising at the sides, and it overlaps the scales of the
                // next row (shifted half a scale), which lie in its shadow there.
                int rim = 7 - (int) Math.round(edge * edge * 3.2);
                int c;
                if (dy > rim) {
                    c = dy == rim + 1 ? pocket : seam;
                } else if (dy == rim) {
                    c = edge > 0.8 ? crestEdge : crest;
                } else if (dx == 0) {
                    c = seam;
                } else {
                    // Shaded where the row above overlaps it, fullest in its middle.
                    int shade = dy == 0 ? -2 : dy == 1 ? -1 : dy >= 3 && dy <= 4 && edge < 0.6 ? 1 : 0;
                    c = body[Math.clamp(base + t[y][x] - 1 + shade, 0, body.length - 1)];
                }
                img.set(x, y, c);
            }
        }
        // A glint on the crest of two of the four scales.
        for (int row = 0; row < 2; row++) {
            int dx = 2 + random.nextInt(3);
            img.set(Math.floorMod(dx - row * 4, 8) + random.nextInt(2) * 8, row * 8 + 3, glint);
        }
        return img;
    }

    static int[][] grid(int value) {
        int[][] g = new int[16][16];
        for (int[] row : g) {
            java.util.Arrays.fill(row, value);
        }
        return g;
    }

    /** A brick texture: bricks in the ramp, mortar (1) and rounded corners (2) in their own colours. */
    static Img brickImg(int[][] t, int[][] mortar, int[] ramp, int mortarColor, int cornerColor) {
        Img img = new Img(16, 16);
        render(img, t, ramp);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (mortar[y][x] == 1) {
                    img.set(x, y, mortarColor);
                } else if (mortar[y][x] == 2) {
                    img.set(x, y, cornerColor);
                }
            }
        }
        return img;
    }

    static final int[] SOIL = ramp(0x3B2416, 0x553520, 0x6E462A, 0x875834, 0x9F6B40, 0xB7814F);
    static final int[] ADOBE = ramp(0x5A3923, 0x70482C, 0x865936, 0x9C6A41, 0xB27D50);
    static final int[] CHITIN = ramp(0x341E10, 0x4E2C17, 0x683A1E, 0x804A26, 0x985C30, 0xB0713E, 0xC6864E, 0xDDA466, 0xEEC082);
    static final int[] ROYAL = ramp(0x1C0E1C, 0x2F1630, 0x432042, 0x572B55, 0x6C3868, 0x84487D, 0x9C5C92);
    static final int[] GOLD = ramp(0x5E420F, 0x8E6A1A, 0xBF962B, 0xE2BF4A, 0xF8E28A);
    static final int[] AMBER = ramp(0x3E1C05, 0x5A2A08, 0x7A3D0B, 0x9A520F, 0xB66816, 0xCC7F20, 0xE09A34, 0xF0BE62);
    static final int[] FUNGUS = ramp(0x6E604C, 0x8C7D64, 0xAA9B7E, 0xC5B797, 0xDCCFB0, 0xEEE4CA, 0xFAF4E2);
    static final int[] GLOW = ramp(0x16221E, 0x1F322C, 0x2A453B, 0x34604F, 0x3F8A6F, 0x5DBE97, 0x97EBC6, 0xDBFFEE);

    static void nestBlocks() {
        // --- Anthill: soil, packed soil, adobe bricks, polished bricks, a pillar ---
        {
            // Calm earth in the middle tones, so the pellets on it read: lit crumbs with a shadow under them.
            int[][] t = clusters(101, 3, 0.25, 0);
            for (int[] row : t) {
                for (int x = 0; x < 16; x++) {
                    row[x] += 1;
                }
            }
            pellets(t, SOIL.length - 1, 102, 9);
            Img img = new Img(16, 16);
            render(img, t, SOIL);
            img.save("block/anthill_soil");
        }
        {
            // Pellets pressed flat: thin horizontal laminae, every few rows a darker seam.
            int[][] t = clusters(111, 5, 0.2, 0);
            double[][] wave = valueNoise(112, 8);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int band = (y + (int) Math.round(wave[0][x] * 2)) % 4;
                    if (band == 0) {
                        t[y][x] = Math.max(0, t[y][x] - 2);
                    } else if (band == 1) {
                        t[y][x] = Math.min(4, t[y][x] + 1);
                    }
                }
            }
            Img img = new Img(16, 16);
            render(img, t, ramp(0x3A2417, 0x4F321F, 0x654129, 0x7B5034, 0x91623F));
            img.save("block/packed_anthill_soil");
        }
        {
            int[][] mortar = new int[16][16];
            int[][] t = bricks(121, ADOBE.length, new int[] {4, 4, 4, 4}, new int[] {8, 8, 8, 8}, new int[] {0, 4, 0, 4}, true, mortar);
            pellets(t, ADOBE.length - 1, 122, 2);
            brickImg(t, mortar, ADOBE, hex(0x3B2416), hex(0x4A2F1C)).save("block/anthill_bricks");
        }
        {
            // Two big pressed slabs per row, their faces smoothed by the ants' mandibles: calm clusters, a lit rim.
            int[][] mortar = new int[16][16];
            int[][] t = bricks(131, ADOBE.length, new int[] {8, 8}, new int[] {16, 16}, new int[] {0, 8}, false, mortar);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (mortar[y][x] == 0 && (y % 8 == 1 || y % 8 == 6)) {
                        t[y][x] = Math.clamp(t[y][x] + (y % 8 == 1 ? 1 : -1), 1, ADOBE.length - 1);
                    }
                }
            }
            brickImg(t, mortar, ramp(0x6A4429, 0x7F5333, 0x94633E, 0xA9744B, 0xBC875B), hex(0x452B19), hex(0x452B19))
                    .save("block/polished_anthill_bricks");
        }
        {
            // Nest pillar: a column the ants built up in rings of pellets, ring seams every 4 rows, lit left.
            int[][] t = clusters(141, ADOBE.length, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int v = t[y][x];
                    v += x < 3 ? 1 : x > 12 ? -1 : 0;
                    if (y % 4 == 3) {
                        v = 0;
                    } else if (y % 4 == 0) {
                        v += 1;
                    }
                    t[y][x] = Math.clamp(v, 0, ADOBE.length - 1);
                }
            }
            Img side = new Img(16, 16);
            render(side, t, ADOBE);
            for (int y = 0; y < 16; y++) {
                side.set(0, y, hex(0x452B19));
                side.set(15, y, hex(0x452B19));
            }
            side.save("block/nest_pillar");
            Img top = new Img(16, 16);
            int[][] rings = clusters(142, ADOBE.length, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double r = Math.hypot(x - 7.5, y - 7.5);
                    int v = rings[y][x];
                    if ((int) r % 3 == 2) {
                        v -= 2;
                    }
                    rings[y][x] = Math.clamp(v, 0, ADOBE.length - 1);
                }
            }
            render(top, rings, ADOBE);
            border(top, 0, 0, 16, 16, hex(0x452B19));
            top.save("block/nest_pillar_top");
        }

        // --- Chitin: scales, polished, bricks, tiles ---
        {
            scales(201, CHITIN, 5, CHITIN[7], CHITIN[5], CHITIN[8], CHITIN[0], CHITIN[2]).save("block/chitin_block");
        }
        {
            // Polished: one calm plate per block face, a lit rim at the top and left, a dark rim at the bottom and right.
            int[][] t = clusters(211, 3, 0.12, 0);
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int v = 3 + t[y][x];
                    if (y == 0 || x == 0) {
                        v = 7;
                    } else if (y == 15 || x == 15) {
                        v = 1;
                    } else if (y == 1 || x == 1) {
                        v = 6;
                    } else if (y == 14 || x == 14) {
                        v = 2;
                    }
                    img.set(x, y, CHITIN[v]);
                }
            }
            // A soft sheen in the plate's upper left, where the light catches the gloss.
            for (int[] p : new int[][] {{3, 3}, {4, 3}, {5, 3}, {3, 4}, {4, 4}, {3, 5}, {6, 3}, {3, 6}}) {
                img.set(p[0], p[1], CHITIN[p[0] + p[1] <= 7 ? 6 : 5]);
            }
            img.set(4, 3, CHITIN[7]);
            img.set(0, 0, CHITIN[8]);
            img.save("block/polished_chitin");
        }
        {
            // Chitin bricks: plates of dark, glossy carapace (darker than the other chitin blocks: an ant's cuticle, not
            // wood), laid in courses of uneven height (5, 5 and 6 rows), staggered, each course overlapping the one below
            // like the segments of an ant's abdomen: its lower lip catches the light and throws a shadow onto the plate
            // under it. Narrow sutures between the plates, a lit edge after each; a short curved highlight in each plate's
            // upper part where the gloss catches the light, and a hair pit or two in the cuticle. No mortar.
            int[] carapace = {hex(0x150C07), CHITIN[0], CHITIN[1], CHITIN[2], CHITIN[3], CHITIN[4], CHITIN[5], CHITIN[7], CHITIN[8]};
            Img img = new Img(16, 16);
            int[][] t = clusters(221, 2, 0.2, 0);
            int[][] courses = {{0, 5, 0, 9}, {5, 5, 4, 13}, {10, 6, 2, 11}}; // top row, height, the two sutures' x
            for (int ci = 0; ci < courses.length; ci++) {
                int[] course = courses[ci];
                int h = course[1];
                for (int b = 0; b < 2; b++) {
                    int joint = course[2 + b];
                    int start = joint + 1;
                    int end = course[2 + (1 - b)] + (b == 1 ? 16 : 0); // the next suture, wrapping round the tile
                    int w = end - start;
                    for (int r = 0; r < h; r++) {
                        int y = course[0] + r;
                        img.set(joint, y, carapace[r == h - 1 ? 3 : 0]);
                        for (int c = 0; c < w; c++) {
                            int x = Math.floorMod(start + c, 16);
                            int v;
                            if (r == 0) {
                                v = 1;                                 // the shadow of the plate above
                            } else if (r == h - 1) {
                                v = c == w - 1 ? 4 : 6;                // the lower lip, lit
                            } else if (c == 0) {
                                v = 5;                                 // the lit edge after the suture
                            } else if (c == w - 1) {
                                v = 2;                                 // turning into the next suture
                            } else {
                                v = (r == 1 ? 3 : 4) + t[y][x] - (r == h - 2 ? 1 : 0); // the dome
                            }
                            img.set(x, y, carapace[Math.clamp(v, 0, 8)]);
                        }
                    }
                    // The highlight: a short arc over the dome, its brightest pixel off-centre to the left; shifted per
                    // plate so the highlights never line up.
                    int hx = start + 1 + (ci + b) % 2;
                    int hy = course[0] + 2;
                    img.set(Math.floorMod(hx, 16), hy, carapace[7]);
                    img.set(Math.floorMod(hx + 1, 16), hy - 1, carapace[8]);
                    img.set(Math.floorMod(hx + 2, 16), hy - 1, carapace[7]);
                    if (w >= 8) {
                        img.set(Math.floorMod(hx + 3, 16), hy - 1, carapace[6]);
                    }
                    // Hair pits.
                    int[] pits = w >= 8 ? new int[] {w - 3, 5} : new int[] {w - 2};
                    for (int i = 0; i < pits.length; i++) {
                        img.set(Math.floorMod(start + pits[i], 16), course[0] + (i == 0 ? h - 2 : 3), carapace[1]);
                    }
                }
            }
            img.save("block/chitin_bricks");
        }
        {
            // Tiles: octagonal plates 8x8 with amber-chitin studs where four corners meet.
            Img img = new Img(16, 16);
            int[][] t = clusters(231, 3, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int dx = x % 8;
                    int dy = y % 8;
                    int cx = Math.min(dx, 7 - dx);
                    int cy = Math.min(dy, 7 - dy);
                    int v;
                    if (cx + cy <= 1) {
                        v = -1; // the stud between four tiles
                    } else if (dx == 7 || dy == 7 || cx + cy == 2) {
                        v = 1;
                    } else if (dy == 0 || dx == 0 || cx + cy == 3 && (dx < 4 || dy < 4)) {
                        v = 6;
                    } else {
                        v = 3 + t[y][x];
                    }
                    img.set(x, y, v < 0 ? hex(0xD9922A) : CHITIN[v]);
                }
            }
            for (int[] p : new int[][] {{0, 0}, {8, 8}, {8, 0}, {0, 8}}) {
                img.set(p[0], p[1], hex(0xF2BB4C));
                img.set(Math.floorMod(p[0] - 1, 16), Math.floorMod(p[1] - 1, 16), hex(0x82430C));
            }
            img.save("block/chitin_tiles");
        }

        // --- Amber: a block with an ant caught in it, bricks, glass, reinforced ---
        {
            int[][] t = clusters(301, AMBER.length, 0.15, 0.05);
            // Light comes through: brighter towards the middle, deeper at the rim.
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double r = Math.hypot(x - 7.5, y - 7.5);
                    t[y][x] = Math.clamp(t[y][x] + (r < 4 ? 1 : r > 8 ? -1 : 0), 1, AMBER.length - 2);
                }
            }
            Img img = new Img(16, 16);
            render(img, t, AMBER);
            // The ant inside: a tiny side-on silhouette, dark and slightly blurred into the amber.
            int ant = hex(0x2A1204);
            int haze = hex(0x5E2D08);
            int[][] body = {{5, 8}, {6, 8}, {7, 7}, {8, 7}, {9, 8}, {10, 8}, {10, 7}, {11, 8}, {6, 9}};
            for (int[] p : body) {
                img.set(p[0], p[1], ant);
            }
            for (int[] p : new int[][] {{4, 7}, {4, 6}, {6, 10}, {8, 9}, {9, 10}, {11, 9}, {12, 10}}) {
                img.set(p[0], p[1], haze);
            }
            // Bubbles caught with it.
            img.set(3, 3, AMBER[7]);
            img.set(12, 4, AMBER[7]);
            img.set(13, 12, AMBER[6]);
            img.set(2, 12, AMBER[6]);
            img.save("block/amber_block");
        }
        {
            int[][] mortar = new int[16][16];
            int[][] t = bricks(313, 6, new int[] {5, 5, 6}, new int[] {16, 8, 16}, new int[] {5, 2, 11}, false, mortar);
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    img.set(x, y, mortar[y][x] != 0 ? CHITIN[1] : AMBER[Math.min(7, 2 + t[y][x])]);
                }
            }
            // A glossy streak on each brick's upper left.
            for (int[] brick : new int[][] {{5, 0}, {2, 5}, {10, 5}, {11, 10}}) {
                img.set(Math.floorMod(brick[0] + 1, 16), brick[1], AMBER[7]);
                img.set(Math.floorMod(brick[0] + 2, 16), brick[1], AMBER[6]);
                img.set(Math.floorMod(brick[0] + 3, 16), brick[1], AMBER[6]);
                img.set(Math.floorMod(brick[0] + 1, 16), brick[1] + 1, AMBER[6]);
            }
            img.save("block/amber_bricks");
        }
        {
            // Amber glass: clear amber you can see through (semi-transparent, so it renders translucent), a deeper rim and
            // a few streaks. 26.x picks the render type from the alpha.
            Img img = new Img(16, 16);
            int[][] t = clusters(321, 3, 0.1, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
                    int c = rim ? AMBER[3] : AMBER[5 + Math.min(1, t[y][x])];
                    img.set(x, y, (rim ? 220 : 110 + t[y][x] * 10) << 24 | (c & 0xFFFFFF));
                }
            }
            for (int i = 0; i < 4; i++) {
                img.set(3 + i, 5 - i, 0xD0000000 | (AMBER[7] & 0xFFFFFF));
                img.set(9 + i, 13 - i, 0xC0000000 | (AMBER[7] & 0xFFFFFF));
            }
            img.save("block/amber_glass");
        }
        {
            // Reinforced: four amber panes in a lattice of Hardened Chitin, studded where the bars cross.
            Img img = new Img(16, 16);
            int[][] t = clusters(331, 4, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean bar = x <= 1 || y <= 1 || x == 8 || x == 7 || y == 7 || y == 8;
                    if (bar) {
                        boolean lit = x == 0 || y == 0 || x == 7 && y > 1 || y == 7 && x > 1;
                        img.set(x, y, lit ? CHITIN[3] : CHITIN[1]);
                    } else {
                        int v = 3 + t[y][x];
                        int px = x % 8;
                        int py = y % 8;
                        if (px == 2 || py == 2) {
                            v -= 2; // pane shadow under the bars
                        } else if (px == 3 && py == 3) {
                            v = 7;
                        }
                        img.set(x, y, AMBER[Math.clamp(v, 0, AMBER.length - 1)]);
                    }
                }
            }
            for (int[] p : new int[][] {{0, 0}, {7, 0}, {0, 7}, {7, 7}}) {
                img.set(p[0], p[1], CHITIN[6]);
                img.set(p[0] + 1, p[1] + 1, CHITIN[0]);
            }
            img.save("block/reinforced_amber");
        }

        // --- Fungal: a fungus garden, its bricks, and the glowing kind ---
        {
            int[][] t = clusters(401, FUNGUS.length - 1, 0.25, 0.05);
            Img img = new Img(16, 16);
            render(img, t, FUNGUS);
            // Gongylidia: the swollen hyphae tips the ants eat, little yellow-cream knobs in clumps.
            Random random = new Random(402);
            for (int i = 0; i < 6; i++) {
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                img.set(x, y, hex(0xF2DC96));
                img.set((x + 1) % 16, y, hex(0xE2C273));
                img.set(x, (y + 1) % 16, hex(0xC9A85A));
            }
            // Fine hyphae threads in the hollows.
            for (int i = 0; i < 5; i++) {
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                img.set(x, y, FUNGUS[0]);
                img.set((x + 1) % 16, (y + 1) % 16, FUNGUS[1]);
            }
            img.save("block/fungal_block");
        }
        {
            int[][] mortar = new int[16][16];
            int[][] t = bricks(411, 5, new int[] {5, 5, 6}, new int[] {8, 8, 8}, new int[] {0, 4, 0}, true, mortar);
            Img img = brickImg(t, mortar, ramp(0x9B8B70, 0xB9A98A, 0xD3C5A5, 0xE7DCC0, 0xF6EFDC), hex(0x5E5040), hex(0x7A6B55));
            // Hyphae creeping over the mortar.
            img.set(3, 4, hex(0xC5B797));
            img.set(12, 9, hex(0xC5B797));
            img.set(7, 15, hex(0xC5B797));
            img.save("block/fungal_bricks");
        }
        {
            // Glowing fungus: bioluminescent clumps on a dark substrate.
            int[][] t = clusters(421, GLOW.length - 1, 0.3, -0.1);
            Img img = new Img(16, 16);
            render(img, t, GLOW);
            Random random = new Random(422);
            for (int i = 0; i < 3; i++) {
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                img.set(x, y, GLOW[7]);
                img.set((x + 1) % 16, y, GLOW[6]);
                img.set(x, (y + 1) % 16, GLOW[6]);
                img.set(Math.floorMod(x - 1, 16), y, GLOW[5]);
            }
            img.save("block/glowing_fungal_block");
        }

        // --- Royal: plum chitin set in gold, for a Queen's chamber ---
        {
            // The same arched scales, plum, each crest gilded.
            scales(501, ROYAL, 4, ROYAL[6], ROYAL[4], GOLD[3], ROYAL[0], ROYAL[1]).save("block/royal_chitin_block");
        }
        {
            int[][] mortar = new int[16][16];
            int[][] t = bricks(511, 5, new int[] {4, 4, 4, 4}, new int[] {8, 8, 8, 8}, new int[] {0, 4, 0, 4}, false, mortar);
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    img.set(x, y, mortar[y][x] != 0 ? GOLD[(x + y) % 5 == 0 ? 2 : 1] : ROYAL[1 + t[y][x]]);
                }
            }
            img.save("block/royal_chitin_bricks");
        }
        {
            // Pillar: plum fluting between gold bands at the ends; the top a gold ring on plum.
            Img side = new Img(16, 16);
            int[][] t = clusters(521, 3, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c;
                    if (y <= 1 || y >= 14) {
                        c = y == 0 || y == 14 ? GOLD[3] : GOLD[1];
                    } else if (x % 4 == 0) {
                        c = ROYAL[1];
                    } else {
                        c = ROYAL[Math.clamp(2 + t[y][x] + (x % 4 == 1 ? 1 : 0), 0, ROYAL.length - 1)];
                    }
                    side.set(x, y, c);
                }
            }
            side.set(5, 0, GOLD[4]);
            side.set(10, 14, GOLD[4]);
            side.save("block/royal_chitin_pillar");
            Img top = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double r = Math.hypot(x - 7.5, y - 7.5);
                    int c = r > 6.5 ? ROYAL[1] : r > 5.3 ? GOLD[r > 6 ? 1 : 3] : ROYAL[Math.clamp(2 + t[y][x] + (r < 2.5 ? 1 : 0), 0, 6)];
                    top.set(x, y, c);
                }
            }
            top.set(5, 3, GOLD[4]);
            top.save("block/royal_chitin_pillar_top");
        }
        {
            // Chiseled: a gold-framed panel with the Queen: a crowned ant seen from above.
            Img img = new Img(16, 16);
            int[][] t = clusters(531, 3, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean frame = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                    int c = frame ? (x == 0 || y == 0 ? GOLD[3] : x == 15 || y == 15 ? GOLD[0] : GOLD[2]) : ROYAL[Math.clamp(1 + t[y][x], 0, 6)];
                    img.set(x, y, c);
                }
            }
            // The Queen's crown inlaid in gold, set with a Royal Jelly cabochon; lit on its left, shaded on its right.
            String[] crown = {
                    "............",
                    "..g...g...g.",
                    ".gGg.gGg.gGg",
                    ".GGG.GGG.GGG",
                    ".GGGGGGGGGGd",
                    ".GGGGJJGGGGd",
                    ".GGGJjjJGGGd",
                    ".GGGGJJGGGGd",
                    ".dddddddddd.",
                    "..ssssssss..",
                    "............"};
            for (int y = 0; y < crown.length; y++) {
                for (int x = 0; x < 12; x++) {
                    int c = switch (crown[y].charAt(x)) {
                        case 'g' -> GOLD[4];
                        case 'G' -> x < 5 ? GOLD[3] : GOLD[2];
                        case 'd' -> GOLD[1];
                        case 's' -> ROYAL[0];
                        case 'J' -> hex(0xE8C878);
                        case 'j' -> hex(0xFFF4C8);
                        default -> 0;
                    };
                    if (c != 0) {
                        img.set(2 + x, 3 + y, c);
                    }
                }
            }
            img.save("block/chiseled_royal_chitin");
        }

        // --- Fittings ---
        doorAndTrapdoor();
        {
            // Pheromone Lamp: four glowing gland sacs held in a chitin cage.
            Img img = new Img(16, 16);
            int[] glow = ramp(0x9A520C, 0xD07A14, 0xF2A42A, 0xFFCF52, 0xFFEC98, 0xFFFBE2);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    img.set(x, y, (x == 0 || y == 0) ? CHITIN[3] : (x == 15 || y == 15) ? CHITIN[0] : CHITIN[1]);
                }
            }
            for (int[] c : new int[][] {{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
                for (int y = -3; y <= 3; y++) {
                    for (int x = -3; x <= 3; x++) {
                        double r = Math.hypot(x + 0.5, y + 0.5);
                        if (r <= 3.3) {
                            int v = r > 2.6 ? 1 : r > 1.8 ? 2 : r > 1.0 ? 3 : 4;
                            if (x <= -1 && y <= -1 && r < 2.0) {
                                v = 5;
                            }
                            img.set(c[0] + x, c[1] + y, glow[v]);
                        }
                    }
                }
            }
            // Bars of the cage between the sacs.
            for (int i = 1; i < 15; i++) {
                img.set(i, 8, CHITIN[2]);
                img.set(8, i, CHITIN[2]);
                img.set(i, 7, CHITIN[4]);
                img.set(7, i, CHITIN[4]);
            }
            img.save("block/pheromone_lamp");
        }
        {
            // Eggs: pearly ovals; larvae: creamy segmented grubs. Shared by the cluster models (8x8 patches).
            Img eggs = new Img(16, 16);
            int[] pearl = ramp(0xB8AE96, 0xD2C9B2, 0xE6E0CF, 0xF4F1E8, 0xFFFFFF);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int dx = x % 8;
                    int dy = y % 8;
                    int v = dy < 2 ? 3 : dy > 5 ? 1 : 2;
                    if (dx == 0 || dx == 7) {
                        v -= 1;
                    }
                    if (dx == 2 && dy == 1) {
                        v = 4;
                    }
                    eggs.set(x, y, pearl[Math.clamp(v, 0, 4)]);
                }
            }
            eggs.save("block/ant_egg_cluster");
            Img larvae = new Img(16, 16);
            int[] cream = ramp(0xA89268, 0xC4AE84, 0xDCC8A0, 0xEDDDBB, 0xF8EFD8);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int v = x % 3 == 2 ? 1 : (y % 8 < 3 ? 3 : 2);
                    if (y % 8 == 7) {
                        v = 0;
                    }
                    larvae.set(x, y, cream[v]);
                }
            }
            larvae.set(1, 1, cream[4]);
            larvae.set(9, 9, cream[4]);
            larvae.save("block/larva_cluster");
        }
        {
            // Terrarium Glass: the colony case's glass with its wooden corner posts (faintly tinted, see-through).
            Img img = new Img(16, 16);
            int frame = hex(0x7A5634);
            int frameDark = hex(0x553B22);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                    img.set(x, y, edge ? ((x == 15 || y == 15) ? frameDark : frame) : 0x30D8F0F0);
                }
            }
            for (int i = 0; i < 4; i++) {
                img.set(3 + i, 7 - i, 0x80FFFFFF);
                img.set(4 + i, 7 - i, 0x50FFFFFF);
                img.set(10 + i, 12 - i, 0x60FFFFFF);
            }
            img.save("block/terrarium_glass_block");
        }
    }

    /** The Nest Door (two halves and its item) and Trapdoor: chitin frames around packed-soil panels, round tunnel windows. */
    static void doorAndTrapdoor() {
        int[] panel = ramp(0x4F321F, 0x654129, 0x7B5034, 0x91623F);
        int[][] soil = clusters(601, 4, 0.2, 0);
        for (int half = 0; half < 2; half++) {
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean frame = x <= 1 || x >= 14 || (half == 0 ? y >= 14 : y <= 1);
                    boolean band = y % 8 == 4 || y % 8 == 5;
                    int c;
                    if (frame) {
                        c = x == 0 || (half == 1 && y == 0) ? CHITIN[5] : x == 15 || (half == 0 && y == 15) ? CHITIN[0] : CHITIN[3];
                    } else if (band) {
                        c = y % 8 == 4 ? CHITIN[4] : CHITIN[1];
                    } else {
                        c = panel[soil[(y + half * 8) % 16][x]];
                    }
                    img.set(x, y, c);
                }
            }
            if (half == 0) {
                // The round tunnel window near the top of the door (see-through: cutout).
                for (int y = 4; y < 12; y++) {
                    for (int x = 4; x < 12; x++) {
                        double r = Math.hypot(x - 7.5, y - 8.5);
                        if (r < 3.0) {
                            img.set(x, y, 0);
                        } else if (r < 3.8) {
                            img.set(x, y, x + y < 16 ? CHITIN[2] : CHITIN[5]);
                        }
                    }
                }
            }
            img.save(half == 0 ? "block/nest_door_top" : "block/nest_door_bottom");
        }
        // The item: the whole door, shrunk into an icon.
        Img item = new Img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 4; x < 12; x++) {
                boolean frame = x == 4 || x == 11 || y == 0 || y == 15;
                int c = frame ? (x == 4 || y == 0 ? CHITIN[5] : CHITIN[0]) : y == 8 ? CHITIN[4] : panel[soil[y][x] % 4];
                item.set(x, y, c);
            }
        }
        disc(item, 8, 4.5, 1.6, 0);
        item.set(7, 3, CHITIN[2]);
        item.set(9, 6, CHITIN[5]);
        item.set(10, 10, GOLD[3]);
        item.save("item/nest_door");
        // The trapdoor: frame, a cross band, four round breathing holes.
        Img trap = new Img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean frame = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                boolean band = x == 7 || x == 8 || y == 7 || y == 8;
                int c = frame ? (x == 0 || y == 0 ? CHITIN[5] : x == 15 || y == 15 ? CHITIN[0] : CHITIN[3])
                        : band ? (x == 7 || y == 7 ? CHITIN[4] : CHITIN[1]) : panel[soil[y][x]];
                trap.set(x, y, c);
            }
        }
        for (int[] c : new int[][] {{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
            trap.set(c[0], c[1], 0);
            trap.set(c[0] - 1, c[1], 0);
            trap.set(c[0], c[1] - 1, 0);
            trap.set(c[0] - 1, c[1] - 1, 0);
            trap.set(c[0] + 1, c[1], CHITIN[5]);
            trap.set(c[0], c[1] + 1, CHITIN[5]);
        }
        trap.save("block/nest_trapdoor");
    }

    /** The floors of the new chambers and the End portal's animation. */
    static void newChambers() {
        {
            // Nether Foraging Site: netherrack-red earth with soul-soil patches, embers in the cracks.
            int[][] t = clusters(701, 5, 0.25, 0);
            Img img = new Img(16, 16);
            render(img, t, ramp(0x2C0D0C, 0x461714, 0x5E211C, 0x772D24, 0x8F3C2D));
            Random random = new Random(702);
            for (int i = 0; i < 5; i++) {
                int x = random.nextInt(15);
                int y = random.nextInt(15);
                img.set(x, y, hex(0x4C3A2E));
                img.set(x + 1, y, hex(0x3A2B22));
                img.set(x, y + 1, hex(0x5E4838));
            }
            img.set(4, 11, hex(0xF2A33A));
            img.set(12, 6, hex(0xE07A22));
            img.save("block/nether_foraging_site_top");
        }
        {
            // End Foraging Site: pale end-stone grit, with violet chorus crumbs.
            int[][] t = clusters(711, 5, 0.25, 0.05);
            Img img = new Img(16, 16);
            render(img, t, ramp(0x8D895E, 0xA8A472, 0xC2BE88, 0xD8D49C, 0xEAE7B4));
            Random random = new Random(712);
            for (int i = 0; i < 4; i++) {
                int x = random.nextInt(15);
                int y = random.nextInt(15);
                img.set(x, y, hex(0x8E5E92));
                img.set(x + 1, y + 1, hex(0x6A4270));
            }
            img.save("block/end_foraging_site_top");
        }
        {
            // Colony Connector: the floor around a tunnel mouth going down, a pheromone trail of dots leading to it.
            int[][] t = clusters(721, SOIL.length - 1, 0.25, 0.05);
            pellets(t, SOIL.length - 1, 722, 3);
            Img img = new Img(16, 16);
            render(img, t, SOIL);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double r = Math.hypot(x - 7.5, y - 7.5);
                    if (r < 2.6) {
                        img.set(x, y, hex(0x140C07));
                    } else if (r < 3.4) {
                        img.set(x, y, y < 8 ? hex(0x2B190D) : SOIL[4]);
                    }
                }
            }
            for (int[] p : new int[][] {{1, 2}, {3, 3}, {13, 12}, {14, 14}, {12, 2}, {2, 13}}) {
                img.set(p[0], p[1], hex(0xE6C36A));
            }
            img.save("block/colony_connector_top");
        }
        {
            // The End rift in the little End portal: a starfield drifting through deep teal-black, eight frames.
            Img strip = new Img(16, 16 * 8);
            Random random = new Random(731);
            int[][] stars = new int[18][3];
            for (int[] s : stars) {
                s[0] = random.nextInt(16);
                s[1] = random.nextInt(16);
                s[2] = random.nextInt(3);
            }
            int[] starColors = {hex(0x2E6E6A), hex(0x5FB7A4), hex(0xC9F5E6)};
            double[][] haze = valueNoise(732, 8);
            for (int frame = 0; frame < 8; frame++) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        double h = haze[Math.floorMod(y - frame, 16)][Math.floorMod(x + frame, 16)];
                        strip.set(x, frame * 16 + y, h > 0.62 ? hex(0x0E2026) : h > 0.4 ? hex(0x081419) : hex(0x040A0E));
                    }
                }
                for (int[] s : stars) {
                    // Near stars move faster than far ones.
                    int sx = Math.floorMod(s[0] + frame * (s[2] + 1) / 2, 16);
                    int sy = Math.floorMod(s[1] - frame * (s[2] + 1) / 2, 16);
                    strip.set(sx, frame * 16 + sy, starColors[s[2]]);
                }
            }
            strip.save("block/end_rift");
            try {
                Files.writeString(TEX.resolve("block/end_rift.png.mcmeta"), "{\n  \"animation\": {\n    \"frametime\": 4\n  }\n}\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Ant Resin and the two trail pheromones. */
    static void nestItems() {
        {
            // Ant Resin: a drop of tree resin, lit at its top left, deep where it pools.
            Img img = item();
            for (int y = 2; y < 15; y++) {
                for (int x = 2; x < 14; x++) {
                    double dx = x - 7.5;
                    double dy = y - 9.0;
                    // A teardrop: round below, drawn to a point above.
                    double width = y < 9 ? (y - 2) * 0.62 : Math.sqrt(Math.max(0, 25 - dy * dy * 0.9));
                    if (Math.abs(dx) <= width) {
                        double light = -dx * 0.12 - dy * 0.16;
                        int v = (int) Math.round(4 + light * 2);
                        img.set(x, y, AMBER[Math.clamp(v, 1, 6)]);
                    }
                }
            }
            img.set(6, 7, AMBER[7]);
            img.set(5, 8, AMBER[7]);
            img.set(6, 8, AMBER[6]);
            img.set(9, 12, AMBER[2]);
            outline(img, AMBER[0]);
            img.save("item/ant_resin");
        }
        trailPheromone("item/nether_trail_pheromone", ramp(0x5A0E08, 0x8E1E0E, 0xC23E16, 0xEC6E22, 0xFFB04A), hex(0xFFE08A));
        trailPheromone("item/end_trail_pheromone", ramp(0x1E0F38, 0x3A1E66, 0x5B3896, 0x3FA79A, 0x8FE8D2), hex(0xF4F0FF));
    }

    /**
     * A trail pheromone: a sealed glass ampoule (pointed tip, no cork) holding the scent, a dotted trail curling through
     * it. Not a tube or a bottle, so it reads apart from the Rally and Nuptial Pheromones.
     */
    static void trailPheromone(String path, int[] liquid, int trail) {
        Img img = item();
        int glass = 0xFFDCEAF0;
        int glassDark = 0xFF9CB4C0;
        for (int y = 1; y < 15; y++) {
            for (int x = 3; x < 13; x++) {
                double dx = x - 7.5;
                double width = y < 5 ? (y - 1) * 0.55 : y > 11 ? 4.4 - (y - 11) * 0.9 : 4.4;
                if (Math.abs(dx) > width) {
                    continue;
                }
                boolean wall = Math.abs(dx) > width - 1;
                if (y < 6) {
                    img.set(x, y, wall ? glassDark : glass);
                } else {
                    int v = (int) Math.round(2 - dx * 0.25 + (y > 11 ? -1 : 0));
                    img.set(x, y, wall ? liquid[0] : liquid[Math.clamp(v, 1, 4)]);
                }
            }
        }
        // The meniscus and a glint on the glass.
        for (int x = 4; x < 12; x++) {
            if (img.get(x, 6) != 0) {
                img.set(x, 6, liquid[4]);
            }
        }
        img.set(6, 3, 0xFFFFFFFF);
        img.set(5, 8, 0xFFFFFFFF);
        // The trail: dots curling down through the scent.
        for (int[] p : new int[][] {{9, 8}, {7, 9}, {6, 11}, {8, 12}, {10, 11}}) {
            img.set(p[0], p[1], trail);
        }
        outline(img, liquid[0]);
        img.save(path);
    }

    // ------------------------------------------------------------------ Chitin gear tiers

    /** One tier of chitin gear: its colour ramp (outline to shine), its handle, its eye glint and its surface detail. */
    record GearTier(String name, int[] ramp, int handle, int handleDark, int eyeShine, char detail) {}

    /**
     * Hardened (smoky umber, fine dark cracks), Infernal (lava-red, ember seams glowing in its dark joints, a blaze-rod
     * handle) and Void (night violet, pale star flecks, an end-rod handle). Same shapes as the chitin gear, the way
     * vanilla's tiers share theirs; each tier's own surface detail on top.
     */
    static final GearTier[] GEAR_TIERS = {
            new GearTier("hardened", new int[] {rgb(42, 30, 24), rgb(74, 58, 48), rgb(110, 90, 74), rgb(149, 128, 112), rgb(200, 180, 158)},
                    STICK, STICK_DARK, rgb(176, 168, 150), 'c'),
            new GearTier("infernal", new int[] {rgb(70, 14, 8), rgb(130, 30, 14), rgb(186, 60, 20), rgb(236, 120, 40), rgb(255, 210, 110)},
                    rgb(250, 196, 60), rgb(196, 112, 22), rgb(255, 150, 40), 'e'),
            new GearTier("void", new int[] {rgb(16, 8, 26), rgb(40, 20, 66), rgb(70, 40, 110), rgb(120, 80, 170), rgb(214, 170, 255)},
                    rgb(236, 228, 214), rgb(150, 120, 156), rgb(150, 240, 200), 's')};

    static Img load(String path) {
        try {
            BufferedImage image = ImageIO.read(TEX.resolve(path + ".png").toFile());
            Img img = new Img(image.getWidth(), image.getHeight());
            for (int y = 0; y < img.h; y++) {
                for (int x = 0; x < img.w; x++) {
                    img.set(x, y, image.getRGB(x, y));
                }
            }
            return img;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static double luma(int c) {
        return 0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF);
    }

    static int lerp(int a, int b, double t) {
        return rgb((int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t),
                (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t),
                (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t));
    }

    /** A chitin pixel in a tier's colours: where its brightness falls on the chitin ramp, on the tier's ramp. */
    static int tierColor(int color, GearTier tier) {
        int alpha = color >>> 24;
        if (alpha == 0) {
            return 0;
        }
        int opaque = color | 0xFF000000;
        if (opaque == STICK) {
            return tier.handle;
        }
        if (opaque == STICK_DARK) {
            return tier.handleDark;
        }
        if (opaque == CH_EYE_SHINE) {
            return tier.eyeShine;
        }
        int[] from = {CH_EYE, CH_OUT, CH_DARK, CH_BASE, CH_LIGHT, CH_SHINE};
        int[] to = {shade(tier.ramp[0], 0.7), tier.ramp[0], tier.ramp[1], tier.ramp[2], tier.ramp[3], tier.ramp[4]};
        double l = luma(opaque);
        int result = to[to.length - 1];
        if (l <= luma(from[0])) {
            result = to[0];
        } else {
            for (int i = 0; i < from.length - 1; i++) {
                double a = luma(from[i]);
                double b = luma(from[i + 1]);
                if (l <= b) {
                    result = lerp(to[i], to[i + 1], (l - a) / (b - a));
                    break;
                }
            }
        }
        return (result & 0x00FFFFFF) | alpha << 24;
    }

    /** A stable pseudo-random number for a pixel, so a tier's detail lands in the same places every run. */
    static int hash(int x, int y, int salt) {
        int h = x * 73856093 ^ y * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7FFFFFFF;
    }

    static boolean solid(Img img, int x, int y) {
        return img.get(x, y) >>> 24 != 0;
    }

    /** The chitin sprite or armor skin at a path in a tier's colours, with the tier's detail. */
    static Img tiered(String path, GearTier tier, int salt) {
        Img source = load(path);
        Img img = new Img(source.w, source.h);
        for (int y = 0; y < source.h; y++) {
            for (int x = 0; x < source.w; x++) {
                img.set(x, y, tierColor(source.get(x, y), tier));
            }
        }
        for (int y = 0; y < source.h; y++) {
            for (int x = 0; x < source.w; x++) {
                if (!solid(source, x, y)) {
                    continue;
                }
                int c = source.get(x, y) | 0xFF000000;
                int h = hash(x, y, salt);
                boolean inside = solid(source, x, y - 1) && solid(source, x, y + 1) && solid(source, x - 1, y) && solid(source, x + 1, y);
                switch (tier.detail) {
                    // Hardened: short hairline cracks across its plain plates.
                    case 'c' -> {
                        if (inside && (c == CH_BASE || c == CH_LIGHT) && h % 9 == 0) {
                            img.set(x, y, tier.ramp[1]);
                            if ((source.get(x + 1, y + 1) | 0xFF000000) == CH_BASE) {
                                img.set(x + 1, y + 1, lerp(tier.ramp[1], tier.ramp[2], 0.5));
                            }
                        }
                    }
                    // Infernal: embers glowing in the dark joints between plates.
                    case 'e' -> {
                        if (c == CH_DARK && h % 3 == 0) {
                            img.set(x, y, h % 2 == 0 ? rgb(255, 168, 48) : rgb(232, 100, 24));
                        }
                    }
                    // Void: pale stars, one here and there, in the plates.
                    case 's' -> {
                        if (inside && (c == CH_BASE || c == CH_DARK) && h % 13 == 0) {
                            img.set(x, y, h % 2 == 0 ? rgb(236, 220, 255) : tier.ramp[4]);
                        }
                    }
                    default -> { }
                }
            }
        }
        return img;
    }

    static void chitinTiers() {
        String[] items = {"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"};
        for (int t = 0; t < GEAR_TIERS.length; t++) {
            GearTier tier = GEAR_TIERS[t];
            for (int i = 0; i < items.length; i++) {
                tiered("item/chitin_" + items[i], tier, t * 31 + i).save("item/" + tier.name + "_chitin_" + items[i]);
            }
            for (String layer : new String[] {"humanoid", "humanoid_leggings", "humanoid_baby"}) {
                tiered("entity/equipment/" + layer + "/chitin", tier, t * 31 + 17).save("entity/equipment/" + layer + "/" + tier.name + "_chitin");
            }
        }
        reinforcement("infernal_reinforcement", "item/infernal_chitin", GEAR_TIERS[1].handle, GEAR_TIERS[1].handleDark, rgb(255, 236, 160), rgb(255, 140, 40));
        reinforcement("void_reinforcement", "item/void_chitin", GEAR_TIERS[2].handle, GEAR_TIERS[2].handleDark, rgb(190, 255, 220), rgb(40, 150, 110));
    }

    /**
     * A Reinforcement: the tier's chitin plate, bound by a rod (a blaze rod or an end rod) rising across it,
     * tied on with a glowing knot in the middle, the way a smithing template carries what it adds.
     */
    static void reinforcement(String name, String plate, int rod, int rodDark, int knot, int knotDark) {
        Img img = load(plate);
        int outline = shade(img.get(8, 8) | 0xFF000000, 0.35);
        for (int i = 2; i <= 13; i++) {
            // One rod rising to the right across the plate, with a dark lower edge.
            int[][] rods = {{i, 15 - i}};
            for (int[] p : rods) {
                img.set(p[0], p[1], i == 2 || i == 13 ? rodDark : rod);
                img.set(p[0], p[1] + 1, rodDark);
            }
        }
        for (int y = 6; y <= 9; y++) {
            for (int x = 6; x <= 9; x++) {
                boolean corner = (x == 6 || x == 9) && (y == 6 || y == 9);
                img.set(x, y, corner ? outline : x + y <= 13 ? knot : knotDark);
            }
        }
        img.set(7, 7, rgb(255, 255, 255));
        img.save("item/" + name);
    }

    public static void main(String[] args) {
        antSkins();
        terrariumInside();
        blocks();
        generator();
        items();
        chitinGear();
        chitinTiers();
        particles();
        guiSprites();
        sideSprites();
        nestBlocks();
        newChambers();
        nestItems();
        System.out.println("Wrote " + written + " textures.");
    }
}
