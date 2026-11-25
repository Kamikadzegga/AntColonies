import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.tools.ToolProvider;

/**
 * Paints every texture of Ant Colonies in code; original art, no third-party textures.
 * Run from the project root with JDK 25: {@code java tools/TexGen.java}.
 */
public class TexGen {
    static final Path TEX = Path.of("common/src/generated/resources/assets/antcolonies/textures");
    static int written;

    // --- Canvas ---

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

        /** Draws rows of small scales over an icon's plain chitin (its base and light pixels). */
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
                if (!solid[x][y] && (x > 0 && solid[x - 1][y] || x + 1 < img.w && solid[x + 1][y]
                        || y > 0 && solid[x][y - 1] || y + 1 < img.h && solid[x][y + 1])) {
                    img.set(x, y, color);
                }
            }
        }
    }

    // --- Ant skins ---

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

    // --- Costumes: what the ants for fun wear ---

    /** The costumes, in the order of the game's Costume enum (and CostumeParts.NAMES). */
    static final String[] COSTUMES = {"cow", "chicken", "sheep", "baker", "bookworm", "party", "pirate", "pig", "rabbit", "fisher"};

    /** One box of a costume, as the client's CostumeParts places it in the texture. */
    record Prop(String name, int u, int v, double w, double h, double d, double grow) {}

    /** Compiles the client's CostumeParts and asks it for a costume's boxes, so the model and its texture never differ. */
    static List<Prop> costumeProps(String costume, String caste) {
        try {
            if (costumeClass == null) {
                Path out = Files.createTempDirectory("costumeparts");
                int failed = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.toString(),
                        "client/src/main/java/com/antcolonies/client/model/CostumeParts.java");
                if (failed != 0) {
                    throw new IllegalStateException("CostumeParts.java did not compile");
                }
                costumeClass = new URLClassLoader(new URL[] {out.toUri().toURL()}).loadClass("com.antcolonies.client.model.CostumeParts");
                String[] names = (String[]) costumeClass.getField("NAMES").get(null);
                if (!Arrays.equals(names, COSTUMES)) {
                    throw new IllegalStateException("COSTUMES must list CostumeParts.NAMES, in its order");
                }
            }
            Class<?> dimsClass = costumeClass.getClassLoader().loadClass("com.antcolonies.client.model.CostumeParts$Dims");
            Shape s = shape(caste);
            Object dims = dimsClass.getDeclaredConstructors()[0].newInstance((float) s.thoraxW(), (float) s.thoraxH(), (float) s.thoraxL(),
                    (float) s.headW(), (float) s.headH(), (float) s.headL(), (float) s.gasterW(), (float) s.gasterH(), (float) s.gasterL());
            List<Prop> props = new ArrayList<>();
            for (Object part : (List<?>) costumeClass.getMethod("parts", String.class, dimsClass).invoke(null, costume, dims)) {
                Class<?> c = part.getClass();
                props.add(new Prop((String) c.getMethod("name").invoke(part), (int) c.getMethod("u").invoke(part), (int) c.getMethod("v").invoke(part),
                        (float) c.getMethod("w").invoke(part), (float) c.getMethod("h").invoke(part), (float) c.getMethod("d").invoke(part),
                        (float) c.getMethod("grow").invoke(part)));
            }
            return props;
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Class<?> costumeClass;

    /**
     * Clears paint that spilled over a box's edges in the texture. The model's boxes have sizes like 4.2, so two faces
     * that meet share a line of texels, and paint meant for one showed as a line along the edge of the other (a white
     * belly drew a white line down the back). The shared line goes to the face that shows: the top over the underside,
     * a side over the underside, the larger part between two sides. It takes the paint of that face's next line in.
     */
    static void settle(Img img, Box b) {
        double u = b.u(), v = b.v(), w = b.w(), h = b.h(), d = b.d();
        int top = (int) Math.floor(v);
        int split = (int) Math.floor(v + d);
        int bottom = (int) Math.ceil(v + d + h);
        boolean sharedRow = v + d > split;
        int topEnd = sharedRow ? split + 1 : split;
        double edge = u + d + w;
        int upFrom = (int) Math.floor(u + d);
        int column = (int) Math.floor(edge);
        if (edge > column && column - 1 >= upFrom) {
            for (int y = top; y < topEnd; y++) {
                img.set(column, y, img.get(column - 1, y));
            }
        }
        if (sharedRow) {
            int upTo = (int) Math.ceil(edge);
            for (int x = (int) Math.floor(u); x < (int) Math.ceil(u + 2 * d + 2 * w); x++) {
                boolean up = x >= upFrom && x < upTo;
                if (up && split - 1 >= top) {
                    img.set(x, split, img.get(x, split - 1));
                } else if (!up && split + 1 < bottom) {
                    img.set(x, split, img.get(x, split + 1));
                }
            }
        }
        for (double side : new double[] {u + d, edge, u + 2 * d + w}) {
            int x = (int) Math.floor(side);
            if (side > x) {
                int from = side - x >= 0.5 ? x - 1 : x + 1;
                for (int y = topEnd; y < bottom; y++) {
                    img.set(x, y, img.get(from, y));
                }
            }
        }
    }

    /**
     * A band round a leg, from tenth {@code from} to {@code to} of its length counted from the body: on its four long
     * faces (the back one runs the other way in the texture), and on its end when the band reaches the foot.
     */
    static void band(Img img, int[][] leg, int from, int to, int color) {
        for (int f : new int[] {0, 1, 3}) {
            patch(img, leg[f], from, 0, to, 10, color);
        }
        patch(img, leg[5], 10 - to, 0, 10 - from, 10, color);
        if (to == 10) {
            img.fill(leg[4][0], leg[4][1], leg[4][2], leg[4][3], color);
        }
    }

    /** Fills a box's six faces: its top, its sides and its underside, each one colour. */
    static void solid(Img img, int[][] faces, int top, int side, int under) {
        for (int f = 0; f < 6; f++) {
            int[] r = faces[f];
            img.fill(r[0], r[1], r[2], r[3], f == 0 ? top : f == 1 ? under : side);
        }
    }

    /** Every other texel of a face, in rings round the body: across a top or bottom face, down a side. */
    static void rings(Img img, int[][] faces, int a, int b) {
        for (int f = 0; f < 6; f++) {
            int[] r = faces[f];
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    int along = f == 0 || f == 1 ? y - r[1] : f == 2 || f == 4 ? x - r[0] : 0;
                    img.set(x, y, along / 2 % 2 == 0 ? a : b);
                }
            }
        }
    }

    /** A patch on a face, given in tenths of the face: from (x0, y0) to (x1, y1). */
    static void patch(Img img, int[] r, int x0, int y0, int x1, int y1, int color) {
        int w = r[2] - r[0];
        int h = r[3] - r[1];
        for (int y = r[1] + h * y0 / 10; y < Math.max(r[1] + h * y0 / 10 + 1, r[1] + h * y1 / 10); y++) {
            for (int x = r[0] + w * x0 / 10; x < Math.max(r[0] + w * x0 / 10 + 1, r[0] + w * x1 / 10); x++) {
                if (x < r[2] && y < r[3]) {
                    img.set(x, y, color);
                }
            }
        }
    }

    /** Wool: pale curls with a darker fleck in every other one, a soft underside. */
    static void wool(Img img, int[][] faces, Random random) {
        for (int f = 0; f < 6; f++) {
            int[] r = faces[f];
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    int curl = ((x - r[0]) / 2 + (y - r[1]) / 2) % 2;
                    int color = curl == 0 ? hex(0xF6F2E8) : hex(0xE6DFD0);
                    if ((x - r[0]) % 2 == 1 && (y - r[1]) % 2 == 1 && random.nextInt(3) == 0) {
                        color = hex(0xCFC6B2);
                    }
                    img.set(x, y, f == 1 ? shade(color, 0.86) : color);
                }
            }
        }
    }

    /**
     * entity/ant/costume/<costume>_<caste>: in the ant skin's layout, paint over the ant's own boxes (clear elsewhere)
     * and, in the corners no box uses, the costume's own parts. Opaque or clear only: it is drawn cut out.
     */
    static void costumes() {
        for (String caste : new String[] {"worker", "soldier", "queen"}) {
            Box[] boxes = boxes(caste);
            int[][] thorax = faces(boxes[0]);
            int[][] hump = faces(boxes[2]);
            int[][] head = faces(boxes[3]);
            int[][] mandible = faces(boxes[4]);
            int[][] femur = faces(boxes[8]);
            int[][] tibia = faces(boxes[9]);
            int[][][] gaster = {faces(boxes[10]), faces(boxes[13])};
            for (String costume : COSTUMES) {
                Img img = new Img(128, 64);
                Random random = new Random(costume.hashCode() * 31L + caste.hashCode());
                // --- Paint over the ant's own boxes ---
                switch (costume) {
                    case "cow" -> {
                        int black = hex(0x26211F);
                        int soft = hex(0x3A3330);
                        // Blotches: one over the shoulders, one low on each flank, one round an eye.
                        patch(img, thorax[0], 0, 1, 10, 5, black);
                        patch(img, thorax[2], 1, 0, 5, 7, black);
                        patch(img, thorax[4], 5, 0, 9, 7, black);
                        patch(img, thorax[2], 7, 5, 10, 10, soft);
                        patch(img, hump[0], 0, 0, 6, 10, black);
                        patch(img, head[0], 0, 5, 5, 10, black);
                        patch(img, head[2], 3, 0, 9, 5, black);
                        // The muzzle: pink across the lower face, two nostrils.
                        patch(img, head[3], 1, 6, 9, 10, hex(0xF2A9B6));
                        patch(img, head[3], 1, 9, 9, 10, hex(0xD98C9C));
                        patch(img, head[3], 3, 7, 4, 8, hex(0xB56A7C));
                        patch(img, head[3], 6, 7, 7, 8, hex(0xB56A7C));
                        // Hooves.
                        band(img, tibia, 8, 10, black);
                        for (int[][] g : gaster) {
                            patch(img, g[0], 2, 2, 8, 6, black);
                            patch(img, g[2], 5, 2, 9, 9, black);
                            patch(img, g[4], 1, 0, 4, 6, black);
                        }
                    }
                    case "chicken" -> {
                        // Yellow legs, darker at the feet.
                        solid(img, femur, hex(0xF2B62A), hex(0xE9A520), hex(0xC98612));
                        solid(img, tibia, hex(0xF2B62A), hex(0xE9A520), hex(0xC98612));
                        band(img, tibia, 8, 10, hex(0xB87410));
                        // Folded wings: grey scallops along the gaster's sides.
                        for (int[][] g : gaster) {
                            for (int side : new int[] {2, 4}) {
                                int[] r = g[side];
                                for (int x = r[0] + 1; x < r[2] - 1; x += 3) {
                                    img.set(x, r[1] + (r[3] - r[1]) / 2, hex(0xCFCBC0));
                                    img.set(x + 1, r[1] + (r[3] - r[1]) / 2 + 1, hex(0xCFCBC0));
                                    img.set(x, r[3] - 2, hex(0xBDB8AC));
                                }
                            }
                        }
                    }
                    case "sheep" -> {
                        // The fleece: the same faces are drawn again on the coat that stands off the body.
                        wool(img, thorax, random);
                        wool(img, hump, random);
                        for (int[][] g : gaster) {
                            wool(img, g, random);
                        }
                        // A paler nose.
                        patch(img, head[3], 2, 7, 8, 10, hex(0x8A7A6C));
                    }
                    case "baker" -> {
                        // An apron: white round the lower thorax and under it, a tie at the back; flour on the face.
                        for (int side : new int[] {2, 4}) {
                            patch(img, thorax[side], 0, 4, 7, 10, hex(0xFAFAF4));
                            patch(img, thorax[side], 0, 9, 7, 10, hex(0xD9D7CD));
                            patch(img, thorax[side], 7, 4, 10, 5, hex(0xE6E4DA));
                        }
                        img.fill(thorax[1][0], thorax[1][1], thorax[1][2], thorax[1][3], hex(0xE6E4DA));
                        patch(img, thorax[3], 0, 4, 10, 10, hex(0xFAFAF4));
                        patch(img, head[3], 2, 5, 4, 6, hex(0xFFFFFF));
                        patch(img, head[3], 6, 7, 8, 8, hex(0xFFFFFF));
                        patch(img, head[0], 6, 2, 8, 4, hex(0xFFFFFF));
                        for (int[][] g : gaster) {
                            patch(img, g[0], 3, 0, 7, 2, hex(0xFAFAF4));
                        }
                    }
                    case "bookworm" -> {
                        // Ink on the jaws, and a ribbon bookmark trailing down the gaster.
                        patch(img, mandible[0], 0, 0, 10, 4, hex(0x1E2A5A));
                        for (int[][] g : gaster) {
                            patch(img, g[0], 4, 0, 6, 7, hex(0xB02A2A));
                            patch(img, g[0], 4, 6, 6, 7, hex(0x7E1C1C));
                        }
                    }
                    case "party" -> {
                        // Confetti, everywhere.
                        int[] confetti = {hex(0xFFE040), hex(0x34C0E8), hex(0xE0349A), hex(0x58D858), hex(0xFFFFFF)};
                        int[][][] all = {thorax, head, hump, gaster[0], gaster[1]};
                        for (int[][] part : all) {
                            for (int f = 0; f < 6; f++) {
                                if (f == 1) {
                                    continue;
                                }
                                int[] r = part[f];
                                for (int y = r[1]; y < r[3]; y++) {
                                    for (int x = r[0]; x < r[2]; x++) {
                                        if ((x * 7 + y * 13 + f) % 9 == 0) {
                                            img.set(x, y, confetti[(x + y * 3) % confetti.length]);
                                        }
                                    }
                                }
                            }
                        }
                        // A bow tie.
                        patch(img, thorax[3], 2, 2, 8, 6, hex(0xE0349A));
                        patch(img, thorax[3], 4, 3, 6, 5, hex(0xFFE040));
                    }
                    case "pirate" -> {
                        // A striped shirt, an eyepatch with its strap over the head, a gold earring on the other side.
                        rings(img, thorax, hex(0xC42626), hex(0xF2EFE6));
                        img.fill(thorax[1][0], thorax[1][1], thorax[1][2], thorax[1][3], hex(0x9A1C1C));
                        int[] eye = head[4];
                        double cx = (eye[0] + eye[2]) / 2.0 + 0.6;
                        double cy = eye[1] + (eye[3] - eye[1]) * 0.38;
                        disc(img, cx, cy, Math.max(1.4, (eye[3] - eye[1]) * 0.3), hex(0x141416));
                        for (int x = eye[0]; x < eye[2]; x++) {
                            img.set(x, eye[1], hex(0x141416));
                        }
                        int strap = head[0][1] + (head[0][3] - head[0][1]) * 4 / 10;
                        for (int x = head[0][0]; x < head[0][2]; x++) {
                            img.set(x, strap, hex(0x141416));
                        }
                        int[] ear = head[2];
                        img.set((ear[0] + ear[2]) / 2, ear[3] - 2, hex(0xF2C230));
                        // A red sash round the gaster's front.
                        for (int[][] g : gaster) {
                            patch(img, g[0], 0, 0, 10, 2, hex(0xC42626));
                            patch(img, g[2], 0, 0, 2, 10, hex(0xC42626));
                            patch(img, g[4], 8, 0, 10, 10, hex(0xC42626));
                        }
                    }
                    case "pig" -> {
                        int pale = hex(0xF6C4C0);
                        int mud = hex(0x6E4A30);
                        int wet = hex(0x54361F);
                        // A paler belly and jowls, trotters, and mud up the legs and low on the flanks.
                        img.fill(thorax[1][0], thorax[1][1], thorax[1][2], thorax[1][3], pale);
                        patch(img, head[3], 1, 7, 9, 10, pale);
                        band(img, tibia, 6, 8, mud);
                        band(img, tibia, 8, 10, hex(0x4A3A34));
                        for (int side : new int[] {2, 4}) {
                            patch(img, thorax[side], 0, 6, 6, 10, mud);
                            patch(img, thorax[side], 1, 5, 4, 6, mud);
                            patch(img, thorax[side], 2, 8, 5, 10, wet);
                            patch(img, thorax[side], 7, 7, 10, 10, mud);
                        }
                        for (int[][] g : gaster) {
                            img.fill(g[1][0], g[1][1], g[1][2], g[1][3], pale);
                            // A splash up each flank: a ragged edge, wet at the bottom.
                            for (int side : new int[] {2, 4}) {
                                patch(img, g[side], 0, 6, 10, 10, mud);
                                patch(img, g[side], 1, 5, 4, 6, mud);
                                patch(img, g[side], 6, 4, 8, 6, mud);
                                patch(img, g[side], 2, 8, 9, 10, wet);
                            }
                            patch(img, g[0], 6, 5, 9, 8, mud);
                            patch(img, g[0], 1, 2, 3, 4, mud);
                        }
                    }
                    case "rabbit" -> {
                        int white = hex(0xF7F3EA);
                        int cream = hex(0xE6DCC8);
                        // A white chest and belly, white paws and muzzle, a pink nose.
                        img.fill(thorax[1][0], thorax[1][1], thorax[1][2], thorax[1][3], white);
                        patch(img, thorax[3], 0, 3, 10, 10, white);
                        patch(img, head[3], 2, 6, 8, 10, white);
                        patch(img, head[3], 4, 5, 6, 7, hex(0xE58FA0));
                        band(img, tibia, 7, 10, white);
                        for (int[][] g : gaster) {
                            img.fill(g[1][0], g[1][1], g[1][2], g[1][3], white);
                            patch(img, g[5], 2, 2, 8, 9, cream);
                        }
                    }
                    case "fisher" -> {
                        int coat = hex(0xF2C62A);
                        int fold = hex(0xD9A61C);
                        int dark = hex(0xB07F12);
                        // An oilskin coat over the thorax, buttoned down the front, and green rubber boots.
                        solid(img, thorax, coat, coat, dark);
                        solid(img, hump, hex(0xF8D850), coat, fold);
                        for (int side : new int[] {2, 4}) {
                            int[] r = thorax[side];
                            img.fill(r[0], r[3] - 1, r[2], r[3], fold);
                            for (int x = r[0] + 2; x < r[2]; x += 3) {
                                img.fill(x, r[1] + 1, x + 1, r[3] - 1, fold);
                            }
                        }
                        img.fill(thorax[0][0], thorax[0][1], thorax[0][2], thorax[0][1] + 1, dark);
                        int[] front = thorax[3];
                        int mid = (front[0] + front[2]) / 2;
                        for (int y = front[1] + 1; y < front[3]; y += 2) {
                            img.set(mid, y, hex(0x3A3A40));
                        }
                        band(img, tibia, 6, 10, hex(0x3E6B3A));
                        band(img, tibia, 6, 7, hex(0x5A8A4E));
                    }
                    default -> throw new IllegalStateException(costume);
                }
                // Paint on one face must not show along the edge of the next: see settle.
                for (int own : new int[] {0, 2, 3, 10, 13}) {
                    settle(img, boxes[own]);
                }
                // --- The costume's own parts ---
                for (Prop prop : costumeProps(costume, caste)) {
                    if (prop.grow() > 0) {
                        // A coat stands on a model box's own place in the texture: painted above.
                        continue;
                    }
                    int[][] f = faces(new Box(prop.name(), prop.u(), prop.v(), prop.w(), prop.h(), prop.d()));
                    switch (prop.name()) {
                        case "horn" -> {
                            solid(img, f, hex(0xF6EED6), hex(0xE2D6B4), hex(0xC9BA92));
                            // A darker tip: the top of each side.
                            for (int side = 2; side < 6; side++) {
                                img.fill(f[side][0], f[side][1], f[side][2], f[side][1] + 1, hex(0xA08E68));
                            }
                            img.fill(f[0][0], f[0][1], f[0][2], f[0][3], hex(0x8A7856));
                        }
                        case "ear" -> {
                            boolean sheep = costume.equals("sheep");
                            solid(img, f, sheep ? hex(0x5A4E44) : hex(0xF4F2EE), sheep ? hex(0x4A4038) : hex(0xE4E0DA), hex(0xE59AAC));
                            // Pink inside, seen from the front.
                            patch(img, f[3], 2, 2, 8, 8, hex(0xE59AAC));
                        }
                        case "bell" -> {
                            solid(img, f, hex(0xFBE27A), hex(0xF2C230), hex(0xA8781A));
                            for (int side = 2; side < 6; side++) {
                                img.fill(f[side][0], f[side][3] - 1, f[side][2], f[side][3], hex(0xC8961A));
                                img.set(f[side][0], f[side][1], hex(0xFFF6C0));
                            }
                        }
                        case "comb", "wattle" -> {
                            solid(img, f, hex(0xF0503C), hex(0xD8281E), hex(0xA81A14));
                            if (prop.name().equals("comb")) {
                                // Three points: notches in the top edge of each long side.
                                for (int side : new int[] {2, 4}) {
                                    int w = f[side][2] - f[side][0];
                                    img.set(f[side][0] + w / 3, f[side][1], 0);
                                    img.set(f[side][0] + 2 * w / 3, f[side][1], 0);
                                }
                            }
                        }
                        case "beak" -> solid(img, f, hex(0xF8C83A), hex(0xF2B21E), hex(0xD08A10));
                        case "tail", "tail_side" -> {
                            solid(img, f, hex(0xFFFFFF), hex(0xF6F4EE), hex(0xD8D4C8));
                            // Feather shafts and grey tips, on the broad faces.
                            for (int side : new int[] {3, 5}) {
                                int[] r = f[side];
                                for (int x = r[0]; x < r[2]; x++) {
                                    img.set(x, r[1], (x - r[0]) % 2 == 0 ? hex(0xBDB8AC) : 0);
                                    if ((x - r[0]) % 2 == 1) {
                                        for (int y = r[1] + 1; y < r[3]; y++) {
                                            img.set(x, y, hex(0xDDD9CE));
                                        }
                                    }
                                }
                            }
                        }
                        case "wool_cap" -> wool(img, f, random);
                        case "toque_band" -> {
                            solid(img, f, hex(0xFFFFFF), hex(0xF2F0E8), hex(0xD9D7CD));
                            for (int side = 2; side < 6; side++) {
                                img.fill(f[side][0], f[side][3] - 1, f[side][2], f[side][3], hex(0xD0CEC4));
                            }
                        }
                        case "toque_puff" -> {
                            solid(img, f, hex(0xFFFFFF), hex(0xFAFAF4), hex(0xDCDAD0));
                            // Pleats down the sides, a shaded lower edge where it billows over the band.
                            for (int side = 2; side < 6; side++) {
                                int[] r = f[side];
                                for (int x = r[0] + 1; x < r[2]; x += 2) {
                                    for (int y = r[1] + 1; y < r[3]; y++) {
                                        img.set(x, y, hex(0xE4E2D8));
                                    }
                                }
                                img.fill(r[0], r[3] - 1, r[2], r[3], hex(0xCFCDC3));
                            }
                        }
                        case "lens" -> {
                            // A gold rim round pale glass, with a glint.
                            solid(img, f, hex(0xE9BE44), hex(0xD8A830), hex(0xA87818));
                            for (int side : new int[] {3, 5}) {
                                int[] r = f[side];
                                if (r[2] - r[0] > 2 && r[3] - r[1] > 2) {
                                    img.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, hex(0xCDEBFA));
                                    img.set(r[0] + 1, r[1] + 1, hex(0xFFFFFF));
                                } else {
                                    img.fill(r[0], r[1], r[2], r[3], hex(0xCDEBFA));
                                    img.fill(r[0], r[1], r[2], r[1] + 1, hex(0xD8A830));
                                }
                            }
                        }
                        case "bridge" -> solid(img, f, hex(0xE9BE44), hex(0xD8A830), hex(0xA87818));
                        case "book_red", "book_blue" -> {
                            boolean red = prop.name().equals("book_red");
                            int cover = red ? hex(0xB02E26) : hex(0x2E5EAE);
                            int dark = red ? hex(0x7E1E1A) : hex(0x1E3F7C);
                            solid(img, f, cover, hex(0xF2E8CC), dark);
                            // Pages on three edges, the spine (the back) in the cover's colour, a gold title bar on top.
                            img.fill(f[5][0], f[5][1], f[5][2], f[5][3], dark);
                            for (int side : new int[] {2, 3, 4}) {
                                int[] r = f[side];
                                img.fill(r[0], r[1], r[2], r[1] + 1, cover);
                                if (r[3] - r[1] > 1) {
                                    img.fill(r[0], r[3] - 1, r[2], r[3], dark);
                                }
                            }
                            patch(img, f[0], 2, 2, 8, 4, hex(0xE9BE44));
                        }
                        case "hat_low", "hat_mid", "hat_tip" -> {
                            // The party hat: each tier its own two colours, in upright stripes.
                            int a = prop.name().equals("hat_mid") ? hex(0x34C0E8) : hex(0xE0349A);
                            int b = prop.name().equals("hat_mid") ? hex(0xFFE040) : hex(0x34C0E8);
                            solid(img, f, a, a, shade(a, 0.7));
                            for (int side = 2; side < 6; side++) {
                                int[] r = f[side];
                                for (int x = r[0]; x < r[2]; x++) {
                                    if ((x - r[0]) % 2 == 1) {
                                        for (int y = r[1]; y < r[3]; y++) {
                                            img.set(x, y, b);
                                        }
                                    }
                                }
                            }
                        }
                        case "pompom" -> {
                            solid(img, f, hex(0xFFF7A8), hex(0xFFE040), hex(0xD9B41E));
                            for (int side = 2; side < 6; side++) {
                                img.set(f[side][0], f[side][1], hex(0xFFFFFF));
                            }
                        }
                        case "hat_brim" -> {
                            solid(img, f, hex(0x2A2A30), hex(0x1C1C20), hex(0x101012));
                            // Gold braid round the edge.
                            for (int side = 2; side < 6; side++) {
                                img.fill(f[side][0], f[side][1], f[side][2], f[side][3], hex(0xE8C030));
                            }
                        }
                        case "hat_crown" -> {
                            solid(img, f, hex(0x2E2E36), hex(0x1E1E24), hex(0x101012));
                            // A skull on the front: a pale block with two eyes.
                            int[] r = f[3];
                            int mx = (r[0] + r[2]) / 2;
                            img.fill(mx - 1, r[1], mx + 1, r[3] - (r[3] - r[1] > 1 ? 1 : 0), hex(0xF2F0E6));
                            if (r[2] - r[0] >= 4) {
                                img.set(mx - 1, r[1], hex(0x1E1E24));
                            }
                            for (int side : new int[] {2, 4, 5}) {
                                img.fill(f[side][0], f[side][3] - 1, f[side][2], f[side][3], hex(0xC42626));
                            }
                        }
                        case "snout" -> {
                            solid(img, f, hex(0xF2A8AE), hex(0xE58E98), hex(0xC8707C));
                            // The flat end with its two nostrils.
                            int[] r = f[3];
                            img.fill(r[0], r[1], r[2], r[3], hex(0xF6B6BA));
                            int y = (r[1] + r[3] - 1) / 2;
                            img.set(r[0] + (r[2] - r[0] > 3 ? 1 : 0), y, hex(0x9A4A5A));
                            img.set(r[2] - 1 - (r[2] - r[0] > 3 ? 1 : 0), y, hex(0x9A4A5A));
                        }
                        case "pig_ear" -> {
                            solid(img, f, hex(0xEFA4A6), hex(0xE08C92), hex(0xD67A86));
                            // A darker tip at the front edge.
                            img.fill(f[0][0], f[0][1], f[0][2], f[0][1] + 1, hex(0xD67A86));
                        }
                        case "curl" -> solid(img, f, hex(0xF2A8AE), hex(0xE58E98), hex(0xC8707C));
                        case "rabbit_ear" -> {
                            solid(img, f, hex(0xB48C62), hex(0xA07850), hex(0x8A6440));
                            // Pink inside, seen from the front, with the fur as a rim; a dark tip.
                            int[] r = f[3];
                            if (r[2] - r[0] > 2) {
                                img.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3], hex(0xEFA9B4));
                            } else {
                                img.fill(r[0], r[1] + 1, r[2], r[3], hex(0xEFA9B4));
                                img.fill(r[0], r[1] + 1, r[0] + 1, r[3], hex(0xA07850));
                            }
                            for (int side = 2; side < 6; side++) {
                                img.fill(f[side][0], f[side][1], f[side][2], f[side][1] + 1, hex(0x6E4E30));
                            }
                            img.fill(f[0][0], f[0][1], f[0][2], f[0][3], hex(0x6E4E30));
                        }
                        case "teeth" -> solid(img, f, hex(0xFFFFFF), hex(0xF4F2EA), hex(0xD8D4C8));
                        case "cottontail" -> {
                            solid(img, f, hex(0xFFFFFF), hex(0xF7F3EA), hex(0xDCD6C8));
                            for (int side = 2; side < 6; side++) {
                                int[] r = f[side];
                                img.fill(r[0], r[3] - 1, r[2], r[3], hex(0xE4DDCE));
                                img.set(r[0], r[1], hex(0xE4DDCE));
                            }
                        }
                        case "rain_crown" -> {
                            solid(img, f, hex(0xF8D850), hex(0xF2C62A), hex(0xB07F12));
                            // Seams down from the top, and a dark band where the crown meets the brim.
                            for (int side = 2; side < 6; side++) {
                                int[] r = f[side];
                                img.set((r[0] + r[2]) / 2, r[1], hex(0xD9A61C));
                                img.fill(r[0], r[3] - 1, r[2], r[3], hex(0x3A3A40));
                            }
                            int[] r = f[0];
                            img.fill((r[0] + r[2]) / 2, r[1], (r[0] + r[2]) / 2 + 1, r[3], hex(0xD9A61C));
                        }
                        case "rain_brim" -> {
                            solid(img, f, hex(0xF2C62A), hex(0xD9A61C), hex(0xB07F12));
                            // The long back of the brim a shade deeper, where the rain runs off.
                            int[] r = f[0];
                            img.fill(r[0], r[3] - 2, r[2], r[3], hex(0xD9A61C));
                        }
                        case "rod" -> {
                            solid(img, f, hex(0x9A6E3A), hex(0x7E5628), hex(0x5E3E1A));
                            // A cork grip at the near end and a pale tip at the far one, on every long face (the back face is mirrored).
                            for (int side : new int[] {0, 1, 3, 5}) {
                                int[] r = f[side];
                                int grip = side == 5 ? r[2] - 3 : r[0];
                                img.fill(grip, r[1], grip + 3, r[3], hex(0xD8B078));
                                int tip = side == 5 ? r[0] : r[2] - 1;
                                img.fill(tip, r[1], tip + 1, r[3], hex(0xE8E0C8));
                            }
                        }
                        case "line" -> solid(img, f, hex(0xE8E8E8), hex(0xD8D8D8), hex(0xC0C0C0));
                        case "float" -> {
                            // Red over white, as a float sits in the water.
                            solid(img, f, hex(0xE04030), hex(0xF4F2EA), hex(0xD8D4C8));
                            for (int side = 2; side < 6; side++) {
                                int[] r = f[side];
                                img.fill(r[0], r[1], r[2], (r[1] + r[3] + 1) / 2, hex(0xD03020));
                            }
                        }
                        default -> throw new IllegalStateException("No paint for the costume part " + prop.name());
                    }
                }
                img.save("entity/ant/costume/" + costume + "_" + caste);
            }
        }
    }

    /**
     * The costumes' rows of entity/ant_side.png: from row 16, a row per costume and caste, the same in all four frames
     * (only the legs move). Drawn over the tinted ant, in its own colours.
     */
    static void sideCostumes(Img img) {
        String[] castes = {"worker", "soldier", "queen"};
        for (int c = 0; c < COSTUMES.length; c++) {
            for (int k = 0; k < 3; k++) {
                boolean queen = castes[k].equals("queen");
                boolean soldier = castes[k].equals("soldier");
                // Where the parts of sideAnt's sprite are: the head's middle and half sizes, the thorax's span and top, the gaster.
                double hcx = queen ? 19.8 : soldier ? 19.6 : 18.9;
                int hx = (int) Math.round(hcx - 0.5);
                int hr = soldier ? 3 : 2;
                double hcy = soldier ? 6.0 : 6.3;
                int headTop = (int) Math.ceil(hcy - (soldier ? 2.4 : 1.7));
                int top = queen ? 5 : 6;
                int x0 = 11;
                int x1 = queen ? 17 : 16;
                double gcx = queen ? 5.8 : 6.0;
                double grx = queen ? 5.3 : soldier ? 3.9 : 4.2;
                double gry = queen ? 3.1 : 2.3;
                double gcy = queen ? 6.6 : 7.2;
                int row = (16 + c * 3 + k) * SIDE_CELL_H;
                for (int frame = 0; frame < 4; frame++) {
                    int ox = frame * SIDE_CELL_W;
                    switch (COSTUMES[c]) {
                        case "cow" -> {
                            // Horns, a patch on the shoulders and one on the head, a pink muzzle, the bell.
                            img.set(ox + hx - 1, row + headTop - 1, hex(0xF6EED6));
                            img.set(ox + hx + 1, row + headTop - 1, hex(0xF6EED6));
                            img.set(ox + hx + 1, row + headTop - 2, hex(0xA08E68));
                            img.set(ox + x0 + 2, row + top, hex(0x26211F));
                            img.set(ox + x0 + 3, row + top, hex(0x26211F));
                            img.set(ox + x0 + 3, row + top + 1, hex(0x26211F));
                            img.set(ox + hx - 1, row + headTop + 1, hex(0x26211F));
                            img.set(ox + hx + hr, row + (int) hcy + 1, hex(0xF2A9B6));
                            img.set(ox + hx + hr - 1, row + (int) hcy + 2, hex(0xD98C9C));
                            img.set(ox + x1 + 1, row + 9, hex(0xF2C230));
                        }
                        case "chicken" -> {
                            // A comb, a beak with the wattle under it, tail feathers up behind.
                            img.set(ox + hx - 1, row + headTop - 1, hex(0xD8281E));
                            img.set(ox + hx, row + headTop - 2, hex(0xF0503C));
                            img.set(ox + hx, row + headTop - 1, hex(0xD8281E));
                            img.set(ox + hx + 1, row + headTop - 1, hex(0xD8281E));
                            img.set(ox + hx + hr + 1, row + (int) hcy, hex(0xF2B21E));
                            img.set(ox + hx + hr, row + (int) hcy, hex(0xF8C83A));
                            img.set(ox + hx + hr, row + (int) hcy + 1, hex(0xD8281E));
                            int tx = (int) Math.round(gcx - grx);
                            int ty = (int) Math.round(gcy - gry);
                            img.set(ox + tx, row + ty, hex(0xFFFFFF));
                            img.set(ox + tx, row + ty - 1, hex(0xF6F4EE));
                            img.set(ox + Math.max(0, tx - 1), row + ty - 2, hex(0xFFFFFF));
                            img.set(ox + tx + 1, row + ty - 2, hex(0xBDB8AC));
                            img.set(ox + tx + 1, row + ty - 1, hex(0xFFFFFF));
                        }
                        case "sheep" -> {
                            // The fleece: over the gaster and the thorax, a tuft on the head.
                            for (int y = 0; y < SIDE_CELL_H; y++) {
                                for (int x = 0; x < SIDE_CELL_W; x++) {
                                    double dx = (x + 0.5 - gcx) / (grx + 0.6);
                                    double dy = (y + 0.5 - (gcy - 0.4)) / (gry + 0.7);
                                    boolean onGaster = dx * dx + dy * dy <= 1;
                                    boolean onThorax = x >= x0 && x <= x1 && y >= top - 2 && y <= 7;
                                    if (onGaster || onThorax) {
                                        img.set(ox + x, row + y, (x / 2 + y / 2) % 2 == 0 ? hex(0xF6F2E8) : hex(0xE2DBCB));
                                    }
                                }
                            }
                            img.set(ox + hx - 1, row + headTop - 1, hex(0xF6F2E8));
                            img.set(ox + hx, row + headTop - 1, hex(0xE2DBCB));
                            img.set(ox + hx - 2, row + headTop + 1, hex(0x4A4038));
                        }
                        case "baker" -> {
                            // The toque: a band and the puff over it; the apron under the thorax.
                            for (int x = hx - 1; x <= hx + 1; x++) {
                                img.set(ox + x, row + headTop - 1, hex(0xE6E4DA));
                            }
                            for (int x = hx - 2; x <= hx + 2; x++) {
                                img.set(ox + x, row + headTop - 2, hex(0xFAFAF4));
                            }
                            for (int x = hx - 2; x <= hx + 1; x++) {
                                img.set(ox + x, row + headTop - 3, hex(0xFFFFFF));
                            }
                            for (int x = x0 + 2; x <= x1; x++) {
                                img.set(ox + x, row + 8, hex(0xFAFAF4));
                                img.set(ox + x, row + 7, hex(0xFAFAF4));
                            }
                        }
                        case "bookworm" -> {
                            // Glasses round the eye, two books on the back.
                            int ex = (int) Math.round(hcx + 0.6);
                            int ey = (int) Math.round(hcy - 0.8);
                            img.set(ox + ex - 1, row + ey, hex(0xD8A830));
                            img.set(ox + ex + 1, row + ey, hex(0xD8A830));
                            img.set(ox + ex, row + ey - 1, hex(0xD8A830));
                            img.set(ox + ex, row + ey + 1, hex(0xD8A830));
                            img.set(ox + ex, row + ey, hex(0xCDEBFA));
                            for (int x = x0 + 1; x <= x0 + 4; x++) {
                                img.set(ox + x, row + top - 1, hex(0xB02E26));
                            }
                            img.set(ox + x0 + 4, row + top - 1, hex(0xF2E8CC));
                            for (int x = x0 + 1; x <= x0 + 3; x++) {
                                img.set(ox + x, row + top - 2, hex(0x2E5EAE));
                            }
                        }
                        case "party" -> {
                            // The cone hat with its pompom; confetti on the back.
                            for (int x = hx - 1; x <= hx + 1; x++) {
                                img.set(ox + x, row + headTop - 1, (x - hx) % 2 == 0 ? hex(0xE0349A) : hex(0x34C0E8));
                            }
                            img.set(ox + hx - 1, row + headTop - 2, hex(0x34C0E8));
                            img.set(ox + hx, row + headTop - 2, hex(0xFFE040));
                            img.set(ox + hx, row + headTop - 3, hex(0xE0349A));
                            img.set(ox + hx, row + headTop - 4, hex(0xFFF7A8));
                            img.set(ox + x0 + 2, row + top, hex(0xFFE040));
                            img.set(ox + x0 + 4, row + top + 1, hex(0x34C0E8));
                            img.set(ox + (int) gcx - 2, row + (int) (gcy - gry) + 1, hex(0xE0349A));
                            img.set(ox + (int) gcx + 1, row + (int) gcy, hex(0x58D858));
                            img.set(ox + (int) gcx - 1, row + (int) gcy + 1, hex(0xFFFFFF));
                        }
                        case "pirate" -> {
                            // The hat: a brim wider than the head, the crown with its skull; the patch; the striped shirt.
                            for (int x = hx - hr; x <= hx + hr; x++) {
                                img.set(ox + x, row + headTop - 1, hex(0x1C1C20));
                            }
                            for (int x = hx - 1; x <= hx + 1; x++) {
                                img.set(ox + x, row + headTop - 2, hex(0x2E2E36));
                                img.set(ox + x, row + headTop - 3, hex(0x2E2E36));
                            }
                            img.set(ox + hx, row + headTop - 2, hex(0xF2F0E6));
                            img.set(ox + hx - hr, row + headTop - 1, hex(0xE8C030));
                            img.set(ox + hx + hr, row + headTop - 1, hex(0xE8C030));
                            int ex = (int) Math.round(hcx + 0.6);
                            int ey = (int) Math.round(hcy - 0.8);
                            img.set(ox + ex, row + ey, hex(0x141416));
                            img.set(ox + ex - 1, row + ey - 1, hex(0x141416));
                            img.set(ox + ex + 1, row + ey, hex(0x141416));
                            for (int x = x0 + 1; x <= x1 - 1; x++) {
                                for (int y = top + 1; y <= 7; y++) {
                                    img.set(ox + x, row + y, (x - x0) % 2 == 1 ? hex(0xC42626) : hex(0xF2EFE6));
                                }
                            }
                        }
                        case "pig" -> {
                            // The snout out in front, a flop ear, the curl of a tail, mud under the belly.
                            img.set(ox + hx + hr + 1, row + (int) hcy, hex(0xF6B6BA));
                            img.set(ox + hx + hr + 1, row + (int) hcy + 1, hex(0xE58E98));
                            img.set(ox + hx, row + headTop - 1, hex(0xE08C92));
                            img.set(ox + hx + 1, row + headTop, hex(0xD67A86));
                            int tx = (int) Math.round(gcx - grx) - 1;
                            int ty = (int) Math.round(gcy) - 1;
                            img.set(ox + Math.max(0, tx), row + ty, hex(0xF2A8AE));
                            img.set(ox + Math.max(0, tx), row + ty - 1, hex(0xE58E98));
                            img.set(ox + Math.max(0, tx - 1), row + ty - 2, hex(0xF2A8AE));
                            img.set(ox + x0 + 1, row + 8, hex(0x6E4A30));
                            img.set(ox + x0 + 2, row + 8, hex(0x54361F));
                            img.set(ox + (int) gcx, row + (int) Math.round(gcy + gry) - 1, hex(0x6E4A30));
                            img.set(ox + (int) gcx + 1, row + (int) Math.round(gcy + gry) - 1, hex(0x6E4A30));
                        }
                        case "rabbit" -> {
                            // Two long ears laid back, the white tail, a white chest and a tooth.
                            int fur = hex(0xC8A27A);
                            img.set(ox + hx - 1, row + headTop - 1, hex(0xEFA9B4));
                            img.set(ox + hx - 1, row + headTop - 2, hex(0xEFA9B4));
                            img.set(ox + hx - 2, row + headTop - 3, fur);
                            img.set(ox + hx - 2, row + headTop - 4, hex(0x6E4E30));
                            img.set(ox + hx - 2, row + headTop - 1, fur);
                            img.set(ox + hx - 3, row + headTop - 2, fur);
                            img.set(ox + hx - 3, row + headTop - 3, hex(0x6E4E30));
                            int tx = (int) Math.round(gcx - grx) - 1;
                            int ty = (int) Math.round(gcy) - 1;
                            img.set(ox + Math.max(0, tx), row + ty, hex(0xFFFFFF));
                            img.set(ox + Math.max(0, tx), row + ty + 1, hex(0xE4DDCE));
                            img.set(ox + tx + 1, row + ty, hex(0xF7F3EA));
                            for (int x = x1 - 2; x <= x1; x++) {
                                img.set(ox + x, row + 8, hex(0xF7F3EA));
                            }
                            img.set(ox + x1, row + 7, hex(0xF7F3EA));
                            img.set(ox + hx + hr, row + (int) hcy + 2, hex(0xFFFFFF));
                        }
                        case "fisher" -> {
                            // The rain hat with its long back brim, the yellow coat, the rod up over the back with its float.
                            for (int x = hx - 1; x <= hx + 1; x++) {
                                img.set(ox + x, row + headTop - 2, hex(0xF8D850));
                                img.set(ox + x, row + headTop - 1, hex(0x3A3A40));
                            }
                            for (int x = hx - hr - 1; x <= hx + hr; x++) {
                                img.set(ox + x, row + headTop, x < hx - 1 ? hex(0xD9A61C) : hex(0xF2C62A));
                            }
                            for (int x = x0 + 1; x <= x1 - 1; x++) {
                                for (int y = top; y <= 7; y++) {
                                    img.set(ox + x, row + y, y == 7 ? hex(0xD9A61C) : hex(0xF2C62A));
                                }
                            }
                            for (int i = 0; i < 5; i++) {
                                img.set(ox + x0 + 2 - i, row + top - 1 - (i + 1) / 2, i == 4 ? hex(0xE8E0C8) : hex(0x7E5628));
                            }
                            img.set(ox + x0 - 2, row + top - 2, hex(0xD8D8D8));
                            img.set(ox + x0 - 2, row + top - 1, hex(0xD03020));
                        }
                        default -> throw new IllegalStateException(COSTUMES[c]);
                    }
                }
            }
        }
    }

    // --- Formicarium inside ---

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

    // --- Blocks ---

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

    // --- The Generator and the Genetic Laboratory ---

    // The machines' palettes. Their faces are shaded symmetrically: lighter at the top, the same edge all round.
    static final int[] ENAMEL = ramp(0x9AA3A8, 0xB9C1C5, 0xD3D9DC, 0xE4E9EB, 0xF3F6F7);
    static final int[] GRAPHITE = ramp(0x1C2125, 0x2A3035, 0x3D454B, 0x566067);
    static final int[] STEEL = ramp(0x2E3338, 0x40474D, 0x545C63, 0x69727A, 0x808991, 0x99A2AA, 0xB4BCC3);
    static final int[] TEAL = ramp(0x0B3C44, 0x135E68, 0x1F8E99, 0x47C6CF, 0x9EF0F2, 0xE4FFFF);
    static final int[] FIRE = ramp(0x6E1A07, 0xB23A0E, 0xE06A16, 0xFAA62E, 0xFFDA6E, 0xFFF4C4);
    static final int[] COAL = ramp(0x151517, 0x232326, 0x333338, 0x4A4A50, 0x62626A);
    static final int HELIX_A = 0xFF4FE3FF;
    static final int HELIX_B = 0xFFE04FFF;

    /** A grid of letters as pixels: each letter's colour from the pairs given; '.' leaves the pixel as it is. */
    static void paint(Img img, int x0, int y0, String[] rows, Object... pairs) {
        Map<Character, Integer> colors = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            colors.put((Character) pairs[i], (Integer) pairs[i + 1]);
        }
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                Integer c = colors.get(rows[y].charAt(x));
                if (c != null) {
                    img.set(x0 + x, y0 + y, c);
                }
            }
        }
    }

    /**
     * Brushed metal: a gradient from tone {@code top} to tone {@code bottom} of the ramp, broken by short horizontal
     * streaks; {@code streak} is their strength, in tones.
     */
    static void brushed(Img img, int x0, int y0, int x1, int y1, int[] ramp, double top, double bottom, double streak, long seed) {
        Random random = new Random(seed);
        for (int y = y0; y < y1; y++) {
            double t = y1 - y0 <= 1 ? top : top + (bottom - top) * (y - y0) / (double) (y1 - y0 - 1);
            int x = x0;
            while (x < x1) {
                int run = 2 + random.nextInt(5);
                double offset = (random.nextInt(5) - 2) / 2.0 * streak;
                for (int k = 0; k < run && x < x1; k++, x++) {
                    img.set(x, y, ramp[Math.clamp((int) Math.round(t + offset), 0, ramp.length - 1)]);
                }
            }
        }
    }

    /** A machine face: a brushed panel, the same darker edge all round, and a graphite kickplate along the foot. */
    static Img casing(int[] ramp, double top, double bottom, double streak, long seed) {
        Img img = new Img(16, 16);
        brushed(img, 0, 0, 16, 13, ramp, top, bottom, streak, seed);
        brushed(img, 0, 13, 16, 16, GRAPHITE, 2.4, 1, 0.5, seed + 1);
        for (int i = 0; i < 16; i++) {
            img.set(i, 0, ramp[Math.max(0, (int) Math.round(bottom) - 1)]);
            if (i < 13) {
                img.set(0, i, ramp[Math.max(0, (int) Math.round(bottom) - 1)]);
                img.set(15, i, ramp[Math.max(0, (int) Math.round(bottom) - 1)]);
            }
            img.set(i, 13, GRAPHITE[3]);
        }
        return img;
    }

    /** A recessed window: the same dark frame on all four sides. */
    static void frame(Img img, int x0, int y0, int x1, int y1, int color) {
        for (int x = x0; x < x1; x++) {
            img.set(x, y0, color);
            img.set(x, y1 - 1, color);
        }
        for (int y = y0; y < y1; y++) {
            img.set(x0, y, color);
            img.set(x1 - 1, y, color);
        }
    }

    /** The Generator, off and on: the lab's enamel body ({@link #labBody}), a dark chamber between casing and lid, the burn core. */
    static void generator() {
        for (boolean on : new boolean[] {false, true}) {
            String suffix = on ? "_on" : "";
            // --- Front: three hooded power cells, lit hotter from left to right while it burns, and the status light ---
            Img front = labBody(171);
            for (int cell = 0; cell < 3; cell++) {
                for (int x = 4 + cell * 3; x < 6 + cell * 3; x++) {
                    front.set(x, 9, GRAPHITE[2]);
                    front.set(x, 10, on ? FIRE[3 + cell] : GRAPHITE[0]);
                    front.set(x, 11, on ? FIRE[2 + cell] : GRAPHITE[0]);
                }
            }
            front.set(7, 12, on ? 0xFF6CDA4E : GRAPHITE[2]);
            front.set(8, 12, on ? 0xFF6CDA4E : GRAPHITE[2]);
            front.save("block/generator_front" + suffix);

            // --- Sides: an energy conduit, a graphite channel with a line of light running through it ---
            Img side = labBody(172);
            for (int x = 3; x < 13; x++) {
                side.set(x, 10, GRAPHITE[2]);
                side.set(x, 11, GRAPHITE[1]);
            }
            for (int x = 4; x < 12; x++) {
                // Segments of light, a gap between each pair.
                boolean gap = (x - 4) % 3 == 2;
                side.set(x, 10, gap ? GRAPHITE[0] : on ? TEAL[4] : TEAL[1]);
                side.set(x, 11, gap ? GRAPHITE[0] : on ? TEAL[3] : TEAL[0]);
            }
            side.save("block/generator_side" + suffix);

            // --- Lid: the heat exchanger, square rings of fins round a centre that glows while it burns ---
            Img top = new Img(16, 16);
            brushed(top, 0, 0, 16, 16, ENAMEL, 4.0, 3.0, 0.35, 177);
            frame(top, 0, 0, 16, 16, ENAMEL[1]);
            top.fill(4, 4, 12, 12, GRAPHITE[0]);
            frame(top, 3, 3, 13, 13, GRAPHITE[2]);
            frame(top, 5, 5, 11, 11, STEEL[3]);
            frame(top, 6, 6, 10, 10, GRAPHITE[1]);
            top.fill(7, 7, 9, 9, on ? FIRE[4] : GRAPHITE[3]);
            if (on) {
                top.set(7, 7, FIRE[5]);
                frame(top, 6, 6, 10, 10, FIRE[1]);
            }
            top.save("block/generator_top" + suffix);
        }
        // The chamber floor and the underside of the lid, and the two electrodes: brushed graphite.
        Img deck = new Img(16, 16);
        brushed(deck, 0, 0, 16, 16, GRAPHITE, 2.4, 1.2, 0.5, 178);
        frame(deck, 4, 4, 12, 12, GRAPHITE[3]);
        deck.save("block/generator_deck");

        // --- The chamber's plating. The model uses columns 2-13 of rows 2-7 of each (between the corner posts) ---
        // Front: a viewport with cut corners in a steel frame, a teal pip either side (cutout).
        Img port = plating(181);
        for (int y = 2; y < 8; y++) {
            for (int x = 3; x < 13; x++) {
                boolean corner = (y == 2 || y == 7) && (x == 3 || x == 12);
                boolean rim = y == 2 || y == 7 || x == 3 || x == 12;
                port.set(x, y, corner ? GRAPHITE[1] : rim ? STEEL[y == 2 ? 5 : 3] : 0);
            }
        }
        // The corners of the opening filled in, so it reads as an octagon rather than a box.
        for (int[] p : new int[][] {{4, 3}, {11, 3}, {4, 6}, {11, 6}}) {
            port.set(p[0], p[1], STEEL[2]);
        }
        port.set(2, 4, TEAL[3]);
        port.set(2, 5, TEAL[2]);
        port.set(13, 4, TEAL[3]);
        port.set(13, 5, TEAL[2]);
        port.save("block/generator_chamber_front");
        // Sides: one narrow slit in a steel frame, a row of cooling fins over and under it (cutout).
        Img slit = plating(182);
        for (int x = 2; x < 14; x++) {
            slit.set(x, 2, x % 2 == 0 ? GRAPHITE[3] : GRAPHITE[0]);
            slit.set(x, 7, x % 2 == 0 ? GRAPHITE[3] : GRAPHITE[0]);
        }
        for (int y = 3; y < 7; y++) {
            for (int x = 3; x < 13; x++) {
                boolean rim = y == 3 || y == 6 || x == 3 || x == 12;
                slit.set(x, y, rim ? STEEL[y == 3 ? 5 : 3] : 0);
            }
        }
        slit.set(3, 3, GRAPHITE[1]);
        slit.set(12, 3, GRAPHITE[1]);
        slit.set(3, 6, GRAPHITE[1]);
        slit.set(12, 6, GRAPHITE[1]);
        slit.save("block/generator_chamber_side");
        // Back: upright cooling fins, and the exhaust port in a steel ring.
        Img fins = plating(183);
        for (int y = 2; y < 8; y++) {
            for (int x = 2; x < 14; x++) {
                fins.set(x, y, x % 2 == 0 ? GRAPHITE[3] : GRAPHITE[0]);
            }
        }
        fins.fill(5, 3, 11, 7, STEEL[3]);
        fins.fill(6, 4, 10, 6, GRAPHITE[0]);
        fins.set(5, 3, GRAPHITE[1]);
        fins.set(10, 3, GRAPHITE[1]);
        fins.set(5, 6, GRAPHITE[1]);
        fins.set(10, 6, GRAPHITE[1]);
        fins.save("block/generator_chamber_back");

        // --- The burn core. Idle: a dark slug with a few dull red seams ---
        Img core = new Img(16, 16);
        brushed(core, 0, 0, 16, 16, COAL, 3.0, 1.4, 0.8, 184);
        for (int[] seam : new int[][] {{6, 7}, {7, 7}, {9, 8}, {5, 9}, {10, 6}, {8, 10}, {6, 5}, {9, 5}}) {
            core.set(seam[0], seam[1], FIRE[0]);
        }
        core.save("block/generator_core");
        // Burning: eight frames of plasma; two crossing ripples make hot cells that drift and merge.
        Img plasma = new Img(16, 16 * 8);
        for (int f = 0; f < 8; f++) {
            double t = f * Math.PI / 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double v = Math.sin(x * 1.05 + t) * Math.cos(y * 0.95 - t) + 0.55 * Math.sin((x + y) * 0.7 - 2 * t);
                    int tone = v > 0.95 ? 5 : v > 0.45 ? 4 : v > -0.1 ? 3 : v > -0.7 ? 2 : 1;
                    plasma.set(x, f * 16 + y, FIRE[tone]);
                }
            }
        }
        plasma.save("block/generator_plasma");
        try {
            Files.writeString(TEX.resolve("block/generator_plasma.png.mcmeta"), "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The Generator's chamber plating: brushed graphite panels, a seam down every fourth column. */
    static Img plating(long seed) {
        Img img = new Img(16, 16);
        brushed(img, 0, 0, 16, 16, GRAPHITE, 2.6, 1.4, 0.5, seed);
        for (int y = 0; y < 16; y++) {
            for (int x = 2; x < 16; x += 4) {
                img.set(x, y, GRAPHITE[0]);
            }
        }
        for (int x = 0; x < 16; x++) {
            if (x % 4 != 2) {
                img.set(x, 2, GRAPHITE[3]);
            }
        }
        return img;
    }

    /**
     * One side of the Droplet Centrifuge or the Generator: the lab's enamel {@link #casing} with a dark open band
     * (rows 2-7) between two posts. The machine paints its own panel over rows 9-12.
     */
    static Img labBody(long seed) {
        Img img = casing(ENAMEL, 4.2, 2.6, 0.35, seed);
        img.fill(2, 2, 14, 8, GRAPHITE[0]);
        for (int x = 0; x < 16; x++) {
            img.set(x, 1, ENAMEL[1]);
            img.set(x, 8, ENAMEL[1]);
        }
        for (int y = 2; y < 8; y++) {
            img.set(1, y, ENAMEL[1]);
            img.set(14, y, ENAMEL[1]);
        }
        return img;
    }

    /** The Genetic Laboratory, idle and working: a helix display in front, a sample cylinder on each side, a hatch on top. */
    static void geneticLab() {
        for (boolean lit : new boolean[] {false, true}) {
            String suffix = lit ? "_working" : "";
            // --- Front: the helix display ---
            Img front = casing(ENAMEL, 4.2, 2.6, 0.35, 81);
            for (int y = 3; y < 12; y++) {
                for (int x = 3; x < 13; x++) {
                    front.set(x, y, GRAPHITE[0]);
                }
            }
            frame(front, 3, 3, 13, 12, GRAPHITE[2]);
            int a = lit ? HELIX_A : shade(HELIX_A, 0.55);
            int b = lit ? HELIX_B : shade(HELIX_B, 0.55);
            int rung = lit ? TEAL[3] : GRAPHITE[3];
            paint(front, 4, 4, new String[] {
                    ".arrrrb.",
                    "..a..b..",
                    "...ab...",
                    "...ba...",
                    "..b..a..",
                    ".brrrra.",
                    "..b..a.."},
                    'a', a, 'b', b, 'r', rung);
            front.set(7, 12, lit ? 0xFF6CDA4E : GRAPHITE[2]);
            front.set(8, 12, lit ? 0xFF6CDA4E : GRAPHITE[2]);
            front.save("block/genetic_laboratory_front" + suffix);

            // --- Side: the sample cylinder ---
            Img side = casing(ENAMEL, 4.2, 2.6, 0.35, 82);
            int[] liquid = lit ? new int[] {TEAL[2], TEAL[3], TEAL[4]} : new int[] {TEAL[1], TEAL[2], TEAL[3]};
            for (int y = 3; y < 12; y++) {
                for (int x = 5; x < 11; x++) {
                    int color;
                    if (y == 3 || y == 11) {
                        color = GRAPHITE[2];                       // the caps
                    } else if (x == 5 || x == 10) {
                        color = GRAPHITE[1];                       // the glass's edge, both sides alike
                    } else if (y == 4) {
                        color = lit ? TEAL[1] : TEAL[0];           // the space over the liquid
                    } else {
                        color = liquid[x == 7 || x == 8 ? 2 : 1]; // round: lightest in the middle
                    }
                    side.set(x, y, color);
                }
            }
            if (lit) {
                side.set(7, 7, TEAL[5]);
                side.set(8, 9, TEAL[5]);
            }
            side.save("block/genetic_laboratory_side" + suffix);

            // --- Top: the sample hatch ---
            Img top = new Img(16, 16);
            brushed(top, 0, 0, 16, 16, ENAMEL, 4.0, 3.0, 0.35, 83);
            frame(top, 0, 0, 16, 16, ENAMEL[1]);
            for (int y = 4; y < 12; y++) {
                for (int x = 4; x < 12; x++) {
                    top.set(x, y, lit ? TEAL[3] : TEAL[1]);
                }
            }
            for (int y = 6; y < 10; y++) {
                for (int x = 6; x < 10; x++) {
                    top.set(x, y, lit ? TEAL[4] : TEAL[2]);  // lighter in the middle of the glass
                }
            }
            frame(top, 3, 3, 13, 13, GRAPHITE[2]);
            top.save("block/genetic_laboratory_top" + suffix);
        }
    }

    static void blocks() {
        Random random = new Random(3);
        // Formicarium: oak frame and clear glass.
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
        // The glass of a stacked nest: one short glint, as a long streak would repeat on every block of a wall.
        Img pane = new Img(16, 16);
        pane.fill(0, 0, 16, 16, argb(28, 220, 240, 255));
        line(pane, 2, 4, 4, 2, argb(80, 255, 255, 255));
        line(pane, 3, 5, 5, 3, argb(45, 255, 255, 255));
        pane.save("block/formicarium_pane");

        // Chamber floors. No borders: touching chambers' floors run into each other.
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

        geneticLab();
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

    // --- Items ---

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
        String[] types = {"speed", "food_efficiency", "simulation", "storage", "breeding",
                "genetic", "hunting", "automation", "population", "evolution"};
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

    // --- Particles ---

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

    // --- GUI sprites (the Genetic Laboratory console) ---

    /** Writes a nine-slice .mcmeta next to a sprite. */
    static void nineSlice(String path, int w, int h, int border) {
        try {
            Path file = TEX.resolve(path + ".png.mcmeta");
            Files.writeString(file, "{\n  \"gui\": {\n    \"scaling\": {\n      \"type\": \"nine_slice\",\n      \"width\": " + w
                    + ",\n      \"height\": " + h + ",\n      \"border\": " + border + "\n    }\n  }\n}\n");
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

    // --- Side-view ant sprites (the ants in the tunnels) ---

    static final int SIDE_CELL_W = 24;
    static final int SIDE_CELL_H = 12;

    /**
     * entity/ant_side.png: side-view ants, four walking frames across; per caste a row each for body, gaster and
     * crystals. The white block at (88, 108) is the carried crumb; rows 10 to 15, see {@link #sideCrops}; from row 16,
     * {@link #sideCostumes}. The client's SideSprites has the same layout.
     */
    static void sideSprites() {
        Img img = new Img(96, SIDE_CELL_H * (16 + COSTUMES.length * 3));
        sideCrops(img);
        sideCostumes(img);
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
            // The big mandibles' tip, kept in the cell like the antenna's.
            int tip = Math.min(mx + 1, SIDE_CELL_W - 1);
            img.set(ox + tip, body + (int) hcy + 1, dark);
            img.set(ox + tip, body + (int) hcy + 2, dark);
        }
        int twitch = frame % 2;
        int ax = (int) Math.round(hcx);
        int ay = body + (int) Math.round(hcy - hry);
        // Its tip stays in the cell: a pixel further would show in the next frame, behind the gaster.
        int reach = Math.min(3, SIDE_CELL_W - 2 - ax);
        line(img, ox + ax, ay, ox + ax - 1, ay - 3, shadow);
        line(img, ox + ax - 1, ay - 3, ox + ax + reach, ay - 3 + twitch, shadow);
        img.set(ox + ax + reach + 1, ay - 2 + twitch, shadow);
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

    // --- Chitin gear, Guard Ants ---

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

    /** A sprite from rows of letters, each a colour of the chitin palette; anything else is clear. */
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

        // Tools, drawn like vanilla's: a straight stick from the bottom left and a grooved chitin head.
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
     * The worn armor: layer "humanoid" holds the helmet, chestplate and boots, "humanoid_leggings" the leggings,
     * "humanoid_baby" everything for baby mobs.
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
     * The Chitin Shield's skin, one pixel per model pixel: the front at (2,2), the back at (16,2), the rim's edges at
     * (30,2), (32,2), (2,26) and (16,26), the handle at (40,2).
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
                int back = rim ? CH_OUT : y >= 9 && y <= 12 ? (y == 9 || y == 12 ? STICK_DARK : STICK)
                        : shade(CH_DARK, 0.85 + random.nextDouble() * 0.15);
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

    /** The helmet's face as an ant's head, on the front face at (fx, fy), {@code w} wide: compound eyes, a ridge, jaws. */
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

    /** The antennae's skins, in the place of the hat layer (the helmet has no hat); see {@code ChitinHelmetModel} for the boxes. */
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
        // The jaws (a 1x1x4 box at 32,8), darker than the face so they stand out, and a pale tooth (a 1x1x1 box at 54,8).
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
        // The wings (4 x 13 planes at 56,16, front and back side by side): a pale membrane, a dark leading edge, veins.
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
     * Paints chitin scales on a model box (corner u, v; size w, h, d), side rows {@code from} to {@code to}; its top
     * too if they start at 0, its bottom if they reach h.
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

    /** One pixel of chitin scales: rows of rounded scales, each row shifted by half a scale and lying over the next. */
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

    // --- The nest-building palette and the new chambers ---
    //
    // Painted on a tone map: each pixel is an index into the material's ramp (dark to light), so shading is +-1 tone.

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

    /** Soil pellets: 2x2 crumbs lit at the top left, with a shadow; they wrap across the edges so the block tiles. */
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

    /** Brick courses as a tone map; sets {@code mortar} to 1 on the seams and 2 on the corners that {@code rounded} knocks off. */
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

    static int[][] grid(int value) {
        int[][] g = new int[16][16];
        for (int[] row : g) {
            Arrays.fill(row, value);
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
    /**
     * Anthill soil in the tone of the ground round it (SoilTone in the mod): the same pellets and laminae, in the colours
     * of earth, sand, red sand, frozen ground, bare rock, a pale garden, dark rock and the Nether.
     */
    static final String[] SOIL_TONES = {"earth", "sand", "red", "frost", "stone", "pale", "dark", "nether"};
    static final int[][] SOIL_RAMPS = {
            SOIL,
            ramp(0x93805A, 0xA59366, 0xB7A777, 0xC8BA89, 0xD6C99A, 0xE2D7AC),
            ramp(0x6A3014, 0x86401A, 0xA2511F, 0xBA6326, 0xCB7533, 0xD98A47),
            ramp(0x6C6A69, 0x82807F, 0x999897, 0xB1B1B1, 0xCACCCD, 0xE3E7E8),
            ramp(0x4C4A47, 0x5F5D59, 0x73706C, 0x87847F, 0x9B9893, 0xAFACA6),
            ramp(0x4C4E48, 0x5D6058, 0x6F7369, 0x82867B, 0x969A8E, 0xAAAFA2),
            ramp(0x252329, 0x333139, 0x424049, 0x52505A, 0x63616C, 0x76747F),
            ramp(0x3C191B, 0x502123, 0x652B2D, 0x7A3638, 0x8D4345, 0x9F5154)};
    static final int[][] PACKED_RAMPS = {
            ramp(0x3A2417, 0x4F321F, 0x654129, 0x7B5034, 0x91623F),
            ramp(0x8B7A54, 0x9D8C61, 0xAF9F70, 0xC0B180, 0xCFC191),
            ramp(0x662E14, 0x7E3E19, 0x964E1E, 0xAC5E25, 0xBE6F31),
            ramp(0x666463, 0x7A7877, 0x8F8E8D, 0xA5A5A5, 0xBCBDBE),
            ramp(0x484643, 0x595754, 0x6B6965, 0x7D7B77, 0x908D89),
            ramp(0x484A45, 0x585B54, 0x696C64, 0x7B7F75, 0x8D9186),
            ramp(0x222026, 0x2F2D35, 0x3D3B44, 0x4C4A54, 0x5C5A65),
            ramp(0x38181A, 0x4A1F21, 0x5D282A, 0x703133, 0x823C3E)};
    static final int[] ADOBE = ramp(0x5A3923, 0x70482C, 0x865936, 0x9C6A41, 0xB27D50);
    static final int[] CHITIN = ramp(0x341E10, 0x4E2C17, 0x683A1E, 0x804A26, 0x985C30, 0xB0713E, 0xC6864E, 0xDDA466, 0xEEC082);
    static final int[] GOLD = ramp(0x5E420F, 0x8E6A1A, 0xBF962B, 0xE2BF4A, 0xF8E28A);
    static final int[] AMBER = ramp(0x3E1C05, 0x5A2A08, 0x7A3D0B, 0x9A520F, 0xB66816, 0xCC7F20, 0xE09A34, 0xF0BE62);
    static final int[] FUNGUS = ramp(0x6E604C, 0x8C7D64, 0xAA9B7E, 0xC5B797, 0xDCCFB0, 0xEEE4CA, 0xFAF4E2);
    static final int[] GLOW = ramp(0x16221E, 0x1F322C, 0x2A453B, 0x34604F, 0x3F8A6F, 0x5DBE97, 0x97EBC6, 0xDBFFEE);

    /** The chitin blocks' cuticle (a red-brown, calmer than the items' caramel) and the royal blocks' wine and old gold. */
    static final int[] CUTICLE = ramp(0x3F2219, 0x4F2B1E, 0x603524, 0x72402B, 0x844C33, 0x96593C, 0xA86947, 0xBA7C56);
    static final int[] WINE = ramp(0x2C1519, 0x3A1C21, 0x48242A, 0x572C33, 0x66353D, 0x774048, 0x894C55);
    static final int[] OLD_GOLD = ramp(0x6B5220, 0x8F7030, 0xB39246, 0xD1B463);

    /** A distance along a tile's side, the short way round its edge. */
    static double around(double d) {
        return d - 16 * Math.round(d / 16);
    }

    static void nestBlocks() {
        // --- Anthill: soil and packed soil in the tone of each ground, adobe bricks, polished bricks, a pillar ---
        for (int tone = 0; tone < SOIL_TONES.length; tone++) {
            String suffix = tone == 0 ? "" : "_" + SOIL_TONES[tone];
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
                render(img, t, SOIL_RAMPS[tone]);
                img.save("block/anthill_soil" + suffix);
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
                render(img, t, PACKED_RAMPS[tone]);
                img.save("block/packed_anthill_soil" + suffix);
            }
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

        // --- Chitin: a mosaic of scutes, polished, plates in courses, tiles ---
        {
            // Raw chitin: scutes fitted edge to edge, each a low dome: lit along its upper left edge, shaded along its
            // lower right, mottled in between. Their middles are placed by hand.
            double[][] middles = {{2.5, 2.5}, {9.0, 1.5}, {14.0, 6.0}, {6.5, 7.0}, {11.0, 11.5}, {2.0, 11.0}, {6.5, 14.0}};
            int[] body = {4, 3, 4, 5, 3, 4, 3};
            int[][] grain = clusters(201, 3, 0.45, 0);
            int[][] owner = new int[16][16];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int nearest = 0;
                    double first = 99;
                    double second = 99;
                    for (int i = 0; i < middles.length; i++) {
                        double d = Math.hypot(around(x - middles[i][0]), around(y - middles[i][1]));
                        if (d < first) {
                            second = first;
                            first = d;
                            nearest = i;
                        } else if (d < second) {
                            second = d;
                        }
                    }
                    owner[y][x] = second - first < 0.9 ? -1 : nearest;   // -1: the seam between two scutes
                }
            }
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int own = owner[y][x];
                    int v;
                    if (own < 0) {
                        v = grain[y][x] == 2 ? 1 : 0;
                    } else if (owner[(y + 15) % 16][x] < 0 || owner[y][(x + 15) % 16] < 0) {
                        v = body[own] + 2;                       // the edge that faces the light
                    } else if (owner[(y + 1) % 16][x] < 0 || owner[y][(x + 1) % 16] < 0) {
                        v = body[own] - 1;                       // the edge that turns away
                    } else {
                        v = body[own] + grain[y][x] - 1;
                    }
                    img.set(x, y, CUTICLE[Math.clamp(v, 0, 7)]);
                }
            }
            img.save("block/chitin_block");
        }
        {
            // Polished: one mottled plate per face, a sheen in two strokes, a rim lit above and left, dark below and right.
            int[][] grain = clusters(211, 3, 0.4, 0);
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int v = 3 + grain[y][x];
                    int diagonal = x + y;
                    if (diagonal == 8 && x >= 2 && x <= 6 || diagonal == 11 && x >= 4 && x <= 6) {
                        v = 6;                                   // the sheen
                    }
                    if (y == 15 || x == 15) {
                        v = 1;
                    } else if (y == 0 || x == 0) {
                        v = 6;
                    } else if (y == 14 || x == 14) {
                        v = 2 + grain[y][x] / 2;
                    }
                    img.set(x, y, CUTICLE[v]);
                }
            }
            img.set(15, 0, CUTICLE[3]);
            img.set(0, 15, CUTICLE[3]);
            img.set(3, 0, CUTICLE[7]);
            img.set(0, 5, CUTICLE[7]);
            img.save("block/polished_chitin");
        }
        {
            // Chitin bricks: carapace plates in staggered courses, each lit along its top over a dark joint. No two plates
            // of one tone side by side.
            Img img = new Img(16, 16);
            int[][] grain = clusters(221, 3, 0.45, 0);
            int[][] courses = {{0, 5, 0, 9}, {5, 5, 4, 13}, {10, 6, 2, 11}}; // top row, height, the two sutures' x
            for (int ci = 0; ci < courses.length; ci++) {
                int[] course = courses[ci];
                int h = course[1];
                for (int b = 0; b < 2; b++) {
                    int joint = course[2 + b];
                    int start = joint + 1;
                    int end = course[2 + (1 - b)] + (b == 1 ? 16 : 0); // the next suture, wrapping round the tile
                    int w = end - start;
                    int base = 3 + (ci + b) % 2;
                    for (int r = 0; r < h; r++) {
                        int y = course[0] + r;
                        img.set(joint, y, CUTICLE[r == 0 ? 1 : 0]);
                        for (int c = 0; c < w; c++) {
                            int x = Math.floorMod(start + c, 16);
                            int v;
                            if (r == h - 1) {
                                v = grain[y][x] == 2 ? 1 : 0;          // the joint, in the shadow of this plate
                            } else if (r == 0) {
                                v = c == w - 1 ? base : base + 2;      // the plate's upper edge, lit
                            } else if (c == 0) {
                                v = base + 1;                          // its left edge, lit a little
                            } else if (r == h - 2 || c == w - 1) {
                                v = base - 1;                          // turning under
                            } else {
                                v = base + grain[y][x] - 1;            // the dome, mottled
                            }
                            img.set(x, y, CUTICLE[Math.clamp(v, 0, 7)]);
                        }
                    }
                    // A chip out of one corner of each plate, never the same corner twice running.
                    int chipX = Math.floorMod((ci + b) % 2 == 0 ? start : start + w - 1, 16);
                    img.set(chipX, course[0], CUTICLE[1]);
                }
            }
            img.save("block/chitin_bricks");
        }
        {
            // Tiles: four mottled plates with clipped corners, every other one a tone deeper, a stud where the corners meet.
            Img img = new Img(16, 16);
            int[][] grain = clusters(231, 3, 0.4, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int dx = x % 8;
                    int dy = y % 8;
                    int cx = Math.min(dx, 7 - dx);
                    int cy = Math.min(dy, 7 - dy);
                    int deep = (x / 8 + y / 8) % 2;
                    int v;
                    if (cx + cy == 0) {
                        v = 1;                                   // the stud between four tiles
                    } else if (dx == 7 || dy == 7 || cx + cy == 1 && (dx > 3 || dy > 3)) {
                        v = grain[y][x] == 2 ? 1 : 0;
                    } else if (dx == 0 || dy == 0 || cx + cy == 1) {
                        v = 6 - deep;
                    } else if (dx == 6 || dy == 6) {
                        v = 3 - deep;
                    } else {
                        v = 3 - deep + grain[y][x];
                    }
                    img.set(x, y, CUTICLE[v]);
                }
            }
            for (int[] p : new int[][] {{0, 0}, {8, 8}, {8, 0}, {0, 8}}) {
                img.set(p[0], p[1], CUTICLE[7]);
                img.set(Math.floorMod(p[0] - 1, 16), Math.floorMod(p[1] - 1, 16), CUTICLE[0]);
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
            // Amber glass: semi-transparent, with a deeper rim and a few streaks. 26.x picks the render type from the alpha.
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

        // --- Royal: wine-dark chitin for a Queen's chamber; gold only as studs on the worked blocks ---
        {
            // The Queen's own cuticle: rounded scales four rows to a block, each row half a scale along and lying over
            // the next. A scale's lower edge catches the light and shades the scale under it; its face is mottled. No gold.
            Img img = new Img(16, 16);
            int[][] grain = clusters(501, 3, 0.45, 0);
            int[][] owner = new int[16][16];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    // The tip of a scale of the row above, where it reaches this far down; else the scale of this row.
                    int above = Math.floorDiv(y, 4) - 1;
                    double down = (y - above * 4 + 0.5) / 8.0;
                    double halfWidth = 4 * Math.sqrt(1 - down * down);
                    double fromMiddle = Math.abs(Math.floorMod(x - Math.floorMod(above, 2) * 4, 8) + 0.5 - 4);
                    int row = fromMiddle <= halfWidth ? above : above + 1;
                    int column = Math.floorDiv(x - Math.floorMod(row, 2) * 4, 8);
                    owner[y][x] = Math.floorMod(row, 4) * 4 + Math.floorMod(column, 2);
                }
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int row = owner[y][x] / 4;
                    int inScale = Math.floorMod(x - row % 2 * 4, 8);
                    // Every other scale a tone deeper, so the rows do not read as stripes.
                    int base = 3 + (owner[y][x] % 4 + row / 2) % 2;
                    int v;
                    if (owner[(y + 1) % 16][x] != owner[y][x]) {
                        v = inScale <= 4 ? 6 : 5;                // the lower edge, lit on its left
                    } else if (owner[(y + 15) % 16][x] != owner[y][x]) {
                        v = grain[y][x] == 2 ? 1 : 0;            // in the shadow of the scale above
                    } else {
                        v = base + grain[y][x] - 1;
                    }
                    img.set(x, y, WINE[Math.clamp(v, 0, 6)]);
                }
            }
            img.save("block/royal_chitin_block");
        }
        {
            // Bricks: four mottled courses in dark joints, a small gold stud in the middle of two of them.
            int[][] mortar = new int[16][16];
            int[][] t = bricks(511, 4, new int[] {4, 4, 4, 4}, new int[] {8, 8, 8, 8}, new int[] {0, 4, 0, 4}, false, mortar);
            int[][] grain = clusters(512, 3, 0.45, 0);
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    // A brick's top row is lit, the row over the joint turns under.
                    int v = y % 4 == 0 ? 5 : y % 4 == 2 ? 2 + grain[y][x] / 2 : 2 + t[y][x];
                    img.set(x, y, mortar[y][x] != 0 ? WINE[grain[y][x] == 2 ? 1 : 0] : WINE[Math.clamp(v, 1, 6)]);
                }
            }
            for (int[] p : new int[][] {{3, 1}, {11, 9}}) {
                img.set(p[0], p[1], OLD_GOLD[3]);
                img.set(p[0] + 1, p[1], OLD_GOLD[2]);
                img.set(p[0], p[1] + 1, OLD_GOLD[1]);
                img.set(p[0] + 1, p[1] + 1, OLD_GOLD[0]);
            }
            img.save("block/royal_chitin_bricks");
        }
        {
            // Pillar: flutes, each lit on its left and deep on its right, between a gold fillet at each end; the top a
            // ring with four gold studs.
            Img side = new Img(16, 16);
            int[][] grain = clusters(521, 3, 0.4, 0);
            int[] flute = {0, 5, 4, 2};
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c;
                    if (y == 1 || y == 14) {
                        c = OLD_GOLD[x % 4 == 1 ? 3 : x % 4 == 0 ? 0 : 1 + grain[y][x] / 2];
                    } else if (y == 0 || y == 15) {
                        c = WINE[y == 0 ? 5 : 1];
                    } else if (y == 2 || y == 13) {
                        c = WINE[y == 2 ? 0 : 2];
                    } else {
                        c = WINE[Math.clamp(flute[x % 4] + (x % 4 == 0 ? 0 : grain[y][x] - 1), 0, 6)];
                    }
                    side.set(x, y, c);
                }
            }
            side.save("block/royal_chitin_pillar");
            Img top = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double r = Math.hypot(x - 7.5, y - 7.5);
                    int v = r > 7.2 ? grain[y][x] / 2 : r > 6.2 ? 2 : r > 5.0 ? (x + y < 15 ? 6 : 4) : r > 4.0 ? 1 : 2 + grain[y][x] + (r < 2.2 ? 1 : 0);
                    top.set(x, y, WINE[Math.clamp(v, 0, 6)]);
                }
            }
            for (int[] p : new int[][] {{7, 2}, {8, 13}, {2, 8}, {13, 7}}) {
                top.set(p[0], p[1], OLD_GOLD[3]);
            }
            top.save("block/royal_chitin_pillar_top");
        }
        {
            // Chiseled: a framed, mottled panel with the Queen's crown cut in relief; only its three points and its
            // jewel are gold.
            Img img = new Img(16, 16);
            int[][] grain = clusters(531, 3, 0.4, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int v;
                    if (x == 0 || y == 0) {
                        v = 6;
                    } else if (x == 15 || y == 15) {
                        v = 0;
                    } else if (x == 1 || y == 1 || x == 14 || y == 14) {
                        v = 3 + grain[y][x] / 2;
                    } else if (x == 2 || y == 2) {
                        v = 0;                                   // the panel is sunk: its upper and left walls in shadow
                    } else if (x == 13 || y == 13) {
                        v = 4;                                   // and its lower and right walls in the light
                    } else {
                        v = 1 + grain[y][x];
                    }
                    img.set(x, y, WINE[v]);
                }
            }
            img.set(15, 0, WINE[3]);
            img.set(0, 15, WINE[3]);
            String[] crown = {
                    "g...g...g",
                    "H...H...L",
                    "HL.LHL.LL",
                    "HLLLLLLLd",
                    "HLLLJLLLd",
                    "sssssssss",
                    "HLLLLLLLd"};
            for (int y = 0; y < crown.length; y++) {
                for (int x = 0; x < crown[y].length(); x++) {
                    int c = switch (crown[y].charAt(x)) {
                        case 'g' -> OLD_GOLD[3];
                        case 'H' -> WINE[6];
                        case 'L' -> WINE[5];
                        case 'd' -> WINE[4];
                        case 's' -> WINE[2];
                        case 'J' -> OLD_GOLD[3];
                        default -> 0;
                    };
                    if (c != 0) {
                        img.set(4 + x, 4 + y, c);
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
            // Formicarium Glass: the colony case's glass with its wooden corner posts (faintly tinted, see-through).
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
    /**
     * The Nest Door and Nest Trapdoor: packed soil panels in a chitin frame, held by chitin bands with a stud at each
     * end. The door's upper half has a round window with a raised rim (see-through: cutout), its lower half a latch.
     */
    static void doorAndTrapdoor() {
        int[] soil = PACKED_RAMPS[0];
        int[][] earth = clusters(601, 4, 0.3, 0);
        int[][] grain = clusters(602, 3, 0.45, 0);
        for (int half = 0; half < 2; half++) {
            Img img = new Img(16, 16);
            // The rows of chitin across this half: a band's lit edge, its face, its underside, the shadow it casts.
            int[] lit = half == 0 ? new int[] {0, 13} : new int[] {6, 13};
            int[] face = half == 0 ? new int[] {1, 14, 15} : new int[] {0, 7, 14};
            int[] under = half == 0 ? new int[] {} : new int[] {8};
            int[] shadow = half == 0 ? new int[] {2} : new int[] {1, 9, 15};
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c;
                    if (x == 0) {
                        c = CUTICLE[6];
                    } else if (x == 15) {
                        c = CUTICLE[0];
                    } else if (has(shadow, y) && (x >= 2 && x <= 13 || y == 15)) {
                        c = CUTICLE[grain[y][x] == 2 ? 1 : 0];
                    } else if (has(lit, y)) {
                        c = CUTICLE[x == 14 ? 4 : 6];
                    } else if (has(face, y)) {
                        c = CUTICLE[4 + grain[y][x]];
                    } else if (has(under, y)) {
                        c = CUTICLE[2 + grain[y][x] / 2];
                    } else if (x == 1) {
                        c = CUTICLE[4 + grain[y][x]];
                    } else if (x == 14) {
                        c = CUTICLE[3 + grain[y][x]];
                    } else {
                        // The panel: packed soil, darker where the frame on its left shades it.
                        c = soil[x == 2 ? 0 : Math.min(3, earth[y][x] + (y % 5 == 3 ? 1 : 0))];
                    }
                    img.set(x, y, c);
                }
            }
            // A stud at each end of a band.
            for (int row : half == 0 ? new int[] {14} : new int[] {7}) {
                for (int x : new int[] {3, 12}) {
                    img.set(x, row, CUTICLE[7]);
                    img.set(x + 1, row + 1, CUTICLE[0]);
                }
            }
            if (half == 0) {
                // The round window: clear inside, its rim lit above and on the left, dark below and on the right.
                for (int y = 3; y < 13; y++) {
                    for (int x = 3; x < 13; x++) {
                        double r = Math.hypot(x - 7.5, y - 7.5);
                        if (r < 2.6) {
                            img.set(x, y, 0);
                        } else if (r < 3.7) {
                            img.set(x, y, CUTICLE[x + y < 15 ? 7 : x + y == 15 ? 5 : 3]);
                        } else if (r < 4.6 && x + y > 16) {
                            img.set(x, y, soil[0]);              // the rim's shadow on the soil
                        }
                    }
                }
            } else {
                // The latch: a small hooked knob on the side away from the hinge.
                img.set(11, 3, CUTICLE[7]);
                img.set(12, 3, CUTICLE[5]);
                img.set(11, 4, CUTICLE[5]);
                img.set(12, 4, CUTICLE[2]);
                img.set(12, 5, soil[0]);
                img.set(13, 5, soil[0]);
            }
            img.save(half == 0 ? "block/nest_door_top" : "block/nest_door_bottom");
        }
        // The item: the whole door, shrunk into an icon.
        Img item = new Img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 4; x < 12; x++) {
                int c;
                if (x == 4 || y == 0) {
                    c = CUTICLE[6];
                } else if (x == 11 || y == 15) {
                    c = CUTICLE[0];
                } else if (y == 8 || y == 12) {
                    c = CUTICLE[5];
                } else if (y == 9 || y == 13) {
                    c = CUTICLE[1];
                } else {
                    c = soil[earth[y][x]];
                }
                item.set(x, y, c);
            }
        }
        disc(item, 8, 4.5, 1.6, 0);
        item.set(6, 3, CUTICLE[6]);
        item.set(7, 2, CUTICLE[6]);
        item.set(9, 6, CUTICLE[2]);
        item.set(10, 5, CUTICLE[2]);
        item.set(10, 10, CUTICLE[7]);
        item.save("item/nest_door");
        // The trapdoor: the same frame, two bands crossing with a stud where they meet, a breathing hole in each panel.
        Img trap = new Img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (x == 0 || y == 0) {
                    c = CUTICLE[6];
                } else if (x == 15 || y == 15) {
                    c = CUTICLE[0];
                } else if (x == 1 || y == 1) {
                    c = CUTICLE[4 + grain[y][x]];
                } else if (x == 14 || y == 14) {
                    c = CUTICLE[3 + grain[y][x]];
                } else if (x == 7 || y == 7) {
                    c = CUTICLE[6];
                } else if (x == 8 || y == 8) {
                    c = CUTICLE[4 + grain[y][x] / 2];
                } else if (x == 9 || y == 9 || x == 2 || y == 2) {
                    c = soil[0];                             // the shadow of a band or of the frame
                } else {
                    c = soil[Math.min(3, earth[y][x])];
                }
                trap.set(x, y, c);
            }
        }
        trap.set(15, 0, CUTICLE[3]);
        trap.set(0, 15, CUTICLE[3]);
        trap.set(7, 7, CUTICLE[7]);
        trap.set(8, 8, CUTICLE[1]);
        for (int[] c : new int[][] {{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
            trap.set(c[0], c[1], 0);
            trap.set(c[0] + 1, c[1], 0);
            trap.set(c[0], c[1] + 1, 0);
            trap.set(c[0] + 1, c[1] + 1, 0);
            // Lit below and on the right, where the hole's far edge faces the light.
            trap.set(c[0] + 2, c[1], soil[3]);
            trap.set(c[0] + 2, c[1] + 1, soil[3]);
            trap.set(c[0], c[1] + 2, soil[3]);
            trap.set(c[0] + 1, c[1] + 2, soil[3]);
        }
        trap.save("block/nest_trapdoor");
    }

    static boolean has(int[] rows, int row) {
        for (int one : rows) {
            if (one == row) {
                return true;
            }
        }
        return false;
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

    /** A trail pheromone: a sealed glass ampoule with a dotted trail in it, unlike the Rally and Nuptial Pheromones. */
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

    // --- Chitin gear tiers ---

    /** One tier of chitin gear: its colour ramp (outline to shine), its handle, its eye glint and its surface detail. */
    record GearTier(String name, int[] ramp, int handle, int handleDark, int eyeShine, char detail) {}

    /** Hardened, Infernal and Void: the chitin gear's shapes in each tier's colours, with its own surface detail on top. */
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
        reinforcement("infernal_reinforcement", "item/infernal_chitin", GEAR_TIERS[1].handle, GEAR_TIERS[1].handleDark,
                rgb(255, 236, 160), rgb(255, 140, 40));
        reinforcement("void_reinforcement", "item/void_chitin", GEAR_TIERS[2].handle, GEAR_TIERS[2].handleDark,
                rgb(190, 255, 220), rgb(40, 150, 110));
    }

    /** A Reinforcement: the tier's chitin plate with a rod tied across it by a glowing knot. */
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

    // --- Working Sites icons ---

    /** One site icon: 16x16 rows of palette letters ('.' clear). */
    record SiteIcon(String name, String[] rows, Map<Character, Integer> palette) {}

    static Map<Character, Integer> iconPalette(Object... kv) {
        Map<Character, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Character) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    /** gui/sprites/lab/site_<kind>: the Formicarium screen's icon for each kind of site, showing the chamber, not what it makes. */
    static void siteIcons() {
        SiteIcon[] icons = {
            // Foraging Site: a cut leaf, carried home (leaf, midrib, a bite out of its edge).
            new SiteIcon("foraging_site", new String[] {
                "................",
                "...........ooo..",
                ".........oohlo..",
                "........ohllmo..",
                ".......ohlmmmo..",
                "......ohlmmrmo..",
                ".....ohlmmrmdo..",
                ".....olmmrmmdo..",
                "....olmmrmmdo...",
                "....olmrmmddo...",
                "...olmrmmddo....",
                "...omrmddoo.....",
                "...orddoo.......",
                "..oroo..........",
                ".oo.............",
                "................"},
                iconPalette('o', 0xFF1E3A14, 'h', 0xFFC8EE84, 'l', 0xFF8FCF4E, 'm', 0xFF5FA232, 'd', 0xFF3D7424, 'r', 0xFFB8D86A)),
            // Harvesting Site: a fungus garden: two pale caps on stalks over a mound of tended soil.
            new SiteIcon("harvesting_site", new String[] {
                "................",
                "................",
                "....oooo........",
                "...ohhlmo...ooo.",
                "..ohllmmmo.ohlmo",
                "..ommmmddo.ommdo",
                "...oosoo....oso.",
                ".....so.....os..",
                ".....so....oso..",
                "...ooosoooooso..",
                "..oEeeeeeeeeeEo.",
                ".oEeEeeEeeEeeEEo",
                ".oEEEEEEEEEEEEEo",
                "..oooooooooooo..",
                "................",
                "................"},
                iconPalette('o', 0xFF2A1E16, 'h', 0xFFF6EEDC, 'l', 0xFFE2D2B4, 'm', 0xFFC4AE8A, 'd', 0xFF9C8466, 's', 0xFFD8CCB4,
                        'e', 0xFF8A6240, 'E', 0xFF6A4630)),
            // Hunting Ground: an ant's open mandibles, toothed, ready to close.
            new SiteIcon("hunting_ground", new String[] {
                "................",
                "..oo........oo..",
                ".ohlo......olmo.",
                ".olmo......omdo.",
                "..olmo....omdo..",
                "..olmto..otmdo..",
                "...olmo..omdo...",
                "...olmto.tmdo...",
                "....olmooomdo...",
                "....ollmmmmdo...",
                "...ohllmmmmddo..",
                "...olmmmmmmmdo..",
                "....olmmmmmdo...",
                ".....oommddo....",
                ".......oooo.....",
                "................"},
                iconPalette('o', 0xFF241008, 'h', 0xFFE8A07A, 'l', 0xFFC0603A, 'm', 0xFF96402A, 'd', 0xFF6A2818, 't', 0xFFEDE2CC)),
            // Liquid Chamber: the mod's droplet shape in a neutral silver, so it names no fluid.
            new SiteIcon("liquid_chamber", DROPLET, iconPalette('o', 0xFF2E343C, 'h', 0xFFFFFFFF, 'l', 0xFFDCE3EA, 'm', 0xFFB4BEC9, 'd', 0xFF8792A0)),
            // Nest Chamber: a heap of eggs, the brood.
            new SiteIcon("nest_chamber", new String[] {
                "................",
                "................",
                "......ooo.......",
                ".....ohlmo......",
                ".....olmmo.ooo..",
                "..ooo.ommoohlmo.",
                ".ohlmoooo.olmmo.",
                ".olmmoohlmoommo.",
                ".olmmoolmmo.oo..",
                "..ommoolmmooo...",
                "...ooohommohlo..",
                "....ohlmooolmmo.",
                "....olmmo.olmmo.",
                ".....ommo..ooo..",
                "......oo........",
                "................"},
                iconPalette('o', 0xFF4A4034, 'h', 0xFFFFFFFA, 'l', 0xFFF2ECDC, 'm', 0xFFD8CCB2)),
            // Queen Chamber: a crown.
            new SiteIcon("queen_chamber", new String[] {
                "................",
                "................",
                "................",
                ".o.....o.....o..",
                "oho...oho...oho.",
                ".olo..olo..olo..",
                ".olmo.olo.omlo..",
                ".ollmoolmomllo..",
                ".olllmmrmmllmo..",
                ".olmlllmllmlmo..",
                ".olrmlmrmlmrdo..",
                ".odddddddddddo..",
                ".olllllllllldo..",
                ".odddddddddddo..",
                "..ooooooooooo...",
                "................"},
                iconPalette('o', 0xFF3A2606, 'h', 0xFFFFF4B0, 'l', 0xFFF2C840, 'm', 0xFFD69E22, 'd', 0xFFA87418, 'r', 0xFFD8364A)),
            // Storage: a crate with iron bands.
            new SiteIcon("storage", new String[] {
                "................",
                "................",
                "..oooooooooooo..",
                "..ohhhhhhhhhlo..",
                "..ogggggggggdo..",
                "..ohlllollllmo..",
                "..ollllollllmo..",
                "..ogggggggggdo..",
                "..ohlllollllmo..",
                "..ollllollllmo..",
                "..ollllollllmo..",
                "..ogggggggggdo..",
                "..ommmmommmmdo..",
                "..oooooooooooo..",
                "................",
                "................"},
                iconPalette('o', 0xFF2A1A0E, 'h', 0xFFD8A866, 'l', 0xFFB88A4E, 'm', 0xFF8E6436, 'd', 0xFF6A4628, 'g', 0xFF8A8E96)),
            // Nether Foraging Site: a little portal of dark stone, its pane purple.
            new SiteIcon("nether_foraging_site", new String[] {
                "................",
                "....oooooooo....",
                "...ossssssssd...",
                "...osppqpppsd...",
                "...ospqppqpsd...",
                "...osppqqppsd...",
                "...osqppqppsd...",
                "...ospqppppsd...",
                "...osppqpqpsd...",
                "...ospqppqpsd...",
                "...osqppqppsd...",
                "...osppqpppsd...",
                "..ossssssssssd..",
                "..odddddddddddo.",
                "...oooooooooooo.",
                "................"},
                iconPalette('o', 0xFF0C0814, 's', 0xFF3A2E52, 'd', 0xFF1E1830, 'p', 0xFF8A3ADA, 'q', 0xFFC88AF6)),
            // End Foraging Site: a little gate of end stone, its pane the dark of the void with a star.
            new SiteIcon("end_foraging_site", new String[] {
                "................",
                "....oooooooo....",
                "...ossssssssd...",
                "...oseeeeeeesd..",
                "...osevveeeesd..",
                "...oseeeeeeesd..",
                "...oseeeeveesd..",
                "...oseeeeeeesd..",
                "...osevveeeesd..",
                "...oseeeeeeesd..",
                "...oseeeevvesd..",
                "...oseeeeeeesd..",
                "..ossssssssssd..",
                "..odddddddddddo.",
                "...oooooooooooo.",
                "................"},
                iconPalette('o', 0xFF2A2A1A, 's', 0xFFE6E2A6, 'd', 0xFFB4B07A, 'e', 0xFF0E1E1C, 'v', 0xFF6FE0C8)),
            // Colony Connector: a scent trail between two marks, the way another colony follows.
            new SiteIcon("colony_connector", new String[] {
                "................",
                "..ooo...........",
                ".ohlmo..........",
                ".olmmo..........",
                ".ommdo..........",
                "..ooo.t.........",
                ".......t........",
                "........t.t.....",
                "...........t....",
                "............t...",
                "...........ooo..",
                "..........ohlmo.",
                "..........olmmo.",
                "..........ommdo.",
                "...........ooo..",
                "................"},
                iconPalette('o', 0xFF141A10, 'h', 0xFFE6FFB0, 'l', 0xFFB4E05C, 'm', 0xFF84B83A, 'd', 0xFF5A8A26, 't', 0xFFB4E05C)),
        };
        for (SiteIcon icon : icons) {
            Img img = new Img(16, 16);
            for (int y = 0; y < 16; y++) {
                String row = icon.rows()[y];
                if (row.length() != 16) {
                    throw new IllegalStateException(icon.name() + " row " + y + " is " + row.length() + " wide");
                }
                for (int x = 0; x < 16; x++) {
                    char c = row.charAt(x);
                    if (c != '.') {
                        img.set(x, y, icon.palette().get(c));
                    }
                }
            }
            img.save("gui/sprites/lab/site_" + icon.name());
        }
    }

    // --- Liquid Ants, droplets and the Droplet Centrifuge ---

    /**
     * One fluid's droplet: its colour and its finish (clear, glass, gas, thick, oil, tar, slime or molten). The same rows are in
     * tools/DataGen.java (LIQUIDS) and the mod's code (LiquidKinds and compat/liquid).
     */
    record LiquidArt(String id, int color, String style) {}

    static final LiquidArt[] LIQUIDS = {
            // vanilla water_still is tinted 3F76E4; lava_still averages D8681A with C73405 shades and FAEB72 lights.
            new LiquidArt("water", 0x3F76E4, "clear"), new LiquidArt("lava", 0xE05A16, "molten"),
            // Milk (the Cow Ant's): a warm cream, thick, where latex is a cold white with bubbles.
            new LiquidArt("milk", 0xF4EEDD, "thick"),
            // MachinaOpus: crude oil tint 1E1A16 (lifted), liquid blaze FFA020 over the glowing molten texture.
            new LiquidArt("crude_oil", 0x24201C, "oil"), new LiquidArt("liquid_blaze", 0xFFA020, "molten"),
            // Mekanism: brine tint FEEF9C; heavy water is vanilla water tinted 0D1455 (a deep navy, lifted).
            new LiquidArt("brine", 0xFEEF9C, "clear"), new LiquidArt("heavy_water", 0x1C2A78, "clear"),
            // Create: honey E7A026, chocolate 9D483B, builder's tea CE7559.
            new LiquidArt("honey", 0xE7A026, "thick"), new LiquidArt("chocolate", 0x9D483B, "thick"), new LiquidArt("tea", 0xCE7559, "clear"),
            // Industrial Foregoing: pink slime CF9DCD, latex E3E3E3 (milky, opaque).
            new LiquidArt("pink_slime", 0xCF9DCD, "slime"), new LiquidArt("latex", 0xE3E3E3, "slime"),
            // Ender IO: nutrient distillation 535C00 (lifted); hootch is white and see-through: clear, cool, nearly colourless.
            new LiquidArt("nutrient_distillation", 0x5A6406, "thick"), new LiquidArt("hootch", 0xD4DEE2, "glass"),
            // Thermal: resin 9A6600 (dark amber), sap C04400 (deep red-brown, no glow).
            new LiquidArt("resin", 0x9E6806, "thick"), new LiquidArt("sap", 0xA03A08, "thick"),
            // Immersive Engineering: creosote 361300 (lifted), plant oil 918F4A.
            new LiquidArt("creosote", 0x3E1A04, "thick"), new LiquidArt("plant_oil", 0x918F4A, "thick"),
            // Liquid experience is 8CFE16 in every mod that has one: bubbly, to keep it apart from the acids.
            new LiquidArt("experience", 0x8CFE16, "slime"),
            // Ender IO's Vat fluids: fire water 8A490F (lifted, burning), liquid sunshine D1C357 (glowing), dew of the void 129238,
            // cloud seed 248589.
            new LiquidArt("fire_water", 0x9A4F10, "molten"), new LiquidArt("liquid_sunshine", 0xE6D64A, "molten"),
            new LiquidArt("dew_of_the_void", 0x129238, "clear"), new LiquidArt("cloud_seed", 0x248589, "clear"),
            // EvilCraft blood 8D0A0A; Allthemodium soul lava 5B80B2 (a blue lava).
            new LiquidArt("blood", 0x8D0A0A, "thick"), new LiquidArt("soul_lava", 0x5B80B2, "molten"),
            // Integrated Dynamics: menril resin 7ECDE1; liquid chorus is CE9ECE, pink slime's colour to a shade, so it is
            // turned toward the chorus fruit's violet.
            new LiquidArt("menril_resin", 0x7ECDE1, "thick"), new LiquidArt("liquid_chorus", 0xB58CD6, "thick"),
            // Theurgy's liquid sal ammoniac is water tinted FF00FF; Forcecraft's liquid force A99E00; Ars Magica's etherium B7DAFF.
            new LiquidArt("sal_ammoniac_solution", 0xD020D0, "clear"), new LiquidArt("liquid_force", 0xA99E00, "thick"),
            new LiquidArt("liquid_etherium", 0xB7DAFF, "clear"),
            // The Undergarden's virulent mix 300737 (lifted).
            new LiquidArt("virulent_mix", 0x5A1466, "thick"),
            // Just Dire Things: polymorphic fluid is water tinted FF0000 (a little off pure here).
            new LiquidArt("polymorphic_fluid", 0xE02020, "clear"),
            // Sulfuric acid in Oritech's green (its pale texture tinted 65FF4C); other mods colour theirs olive or orange.
            new LiquidArt("sulfuric_acid", 0x4ACE40, "clear"),
            // Actually Additions canola oil F4CF2F; Oritech biofuel 2E4011 (lifted and greener); Mekanism bioethanol CEEB3D.
            new LiquidArt("canola_oil", 0xF4CF2F, "thick"), new LiquidArt("biofuel", 0x2E6A2A, "clear"), new LiquidArt("bioethanol", 0xCEEB3D, "clear"),
            // Eternal Starlight ether DFFFF2 and Astral Sorcery liquid starlight DDE1EE are both near white: each is turned a
            // little toward its own hue, and starlight glows.
            new LiquidArt("ether", 0xBFF5E1, "clear"), new LiquidArt("liquid_starlight", 0xC8D2F0, "molten"),
            // Oritech's refinery: heavy oil 160A04 (a warm tar where crude oil is cold black), diesel 899732 (greener, away from
            // plant oil), light naphtha A14A19 in the fluid but a pale straw liquid by its tint F1ECBD, turbofuel 20303C (lifted).
            new LiquidArt("heavy_oil", 0x4A3A26, "tar"), new LiquidArt("diesel", 0x8FA02A, "clear"), new LiquidArt("naphtha", 0xD4C070, "glass"),
            new LiquidArt("turbofuel", 0x34566C, "thick"),
            // Stellaris: fuel 9A8B54 (browner: it sat on plant oil), hydrogen 2EC0DE and oxygen 95D5E3, both gases.
            new LiquidArt("fuel", 0xA8862E, "thick"), new LiquidArt("hydrogen", 0x22B8E8, "gas"), new LiquidArt("oxygen", 0xB8ECF8, "gas"),
            // Ender IO rocket fuel 707044 (greyer, away from nutrient distillation and plant oil).
            new LiquidArt("rocket_fuel", 0x6E6E58, "clear"),
            // Just Dire Things: its fuels burn (tints 8B0000, 40C7C7, 1B2027), so they glow where blood, cloud seed and crude oil
            // of the same colours do not. Time Fluid 00FF00 glows a pale mint, away from the acids; Portal Fluid 00DD00 is turned to emerald and bubbles.
            new LiquidArt("blaze_ember_fuel", 0xB83210, "molten"), new LiquidArt("voidflame_fuel", 0x38C4C0, "molten"),
            new LiquidArt("eclipse_ember_fuel", 0x2C2226, "molten"), new LiquidArt("time_fluid", 0x5CFF9A, "molten"),
            new LiquidArt("portal_fluid", 0x00B48C, "slime"),
    };

    /** The one droplet shape every fluid shares: o outline, h highlight, l light, m body, d shade. Lit from the top left. */
    static final String[] DROPLET = {
            "................",
            ".......oo.......",
            "......olho......",
            "......ollo......",
            ".....olllmo.....",
            ".....ohlmmo.....",
            "....ohhlmmmo....",
            "....ohllmmmo....",
            "...olhllmmmdo...",
            "...olllmmmmdo...",
            "...ollmmmmddo...",
            "...olmmmmmddo...",
            "...ommmmmdddo...",
            "....ommmddddo...",
            ".....odddddo....",
            "......ooooo.....",
    };

    /** A colour moved in brightness and saturation (factors) and turned in hue (degrees): shades run cooler, lights warmer. */
    static int tone(int rgb, double brightness, double saturation, double hueShift) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float h = (float) ((hsb[0] + hueShift / 360.0 + 1) % 1.0);
        float s = (float) Math.clamp(hsb[1] * saturation, 0, 1);
        float b = (float) Math.clamp(hsb[2] * brightness, 0, 1);
        return 0xFF000000 | (Color.HSBtoRGB(h, s, b) & 0xFFFFFF);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** item/<id>_droplet for every fluid: the shared shape in the fluid's own ramp, finished in its style. */
    static void droplets() {
        for (LiquidArt art : LIQUIDS) {
            int base = hex(art.color());
            float value = Color.RGBtoHSB((base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF, null)[2];
            boolean dark = value < 0.35;
            boolean molten = art.style().equals("molten");
            // The ramp: lights lean warm and pale, shades lean cool and deep. Very dark fluids get lights they can show.
            boolean oil = art.style().equals("oil");
            // Tar is a brown-black oil: where crude oil shines cold and blue, it shines warm.
            boolean tar = art.style().equals("tar");
            // Molten ramps come from the fluid's own hue, so lava stays red-orange and liquid blaze stays gold.
            // A blue or green fire (soul lava) burns pale cyan at its core, not yellow.
            float hue = Color.RGBtoHSB((base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF, null)[0];
            boolean cool = molten && hue > 0.4 && hue < 0.75;
            int highlight = molten ? mix(base, hex(cool ? 0xE8FBFF : 0xFFFBE0), 0.78) : oil ? hex(0x9AA8C8) : tar ? hex(0xE0B070)
                    : mix(base, hex(0xFFFFFF), dark ? 0.62 : 0.78);
            int light = molten ? mix(base, hex(cool ? 0x9AE8FF : 0xFFF07A), 0.48) : oil ? hex(0x4A4E62) : tar ? hex(0x8A6A3E)
                    : dark ? tone(mix(base, hex(0xFFFFFF), 0.24), 1.05, 1.1, 8) : tone(mix(base, hex(0xFFFFFF), 0.32), 1, 0.9, 6);
            int body = base;
            int shadeTone = molten ? tone(base, 0.74, 1.12, -12) : tone(base, dark ? 0.62 : 0.74, 1.12, -8);
            int outlineTone = molten ? tone(base, 0.4, 1.2, -16) : oil ? hex(0x0C0A0A) : tar ? hex(0x1A100A) : tone(base, dark ? 0.36 : 0.42, 1.15, -12);
            Img img = item();
            paint(img, 0, 0, DROPLET, 'o', outlineTone, 'h', highlight, 'l', light, 'm', body, 'd', shadeTone);
            // The lit side's outline a touch lighter than the far side's, so the drop sits in the light.
            for (int[] p : new int[][] {{6, 2}, {6, 3}, {5, 4}, {5, 5}, {4, 6}, {4, 7}, {3, 8}, {3, 9}}) {
                img.set(p[0], p[1], mix(outlineTone, body, 0.28));
            }
            switch (art.style()) {
                case "clear", "glass", "gas" -> {
                    // Light let through: a small bright crescent low on the far side.
                    img.set(10, 11, light);
                    img.set(9, 12, light);
                    img.set(10, 12, mix(light, highlight, 0.5));
                    if (art.style().equals("gas")) {
                        // A gas kept as a drop: bubbles rising through it, one at the top about to leave.
                        img.set(8, 10, highlight);
                        img.set(7, 12, highlight);
                        img.set(9, 7, highlight);
                        img.set(8, 5, mix(light, highlight, 0.5));
                    }
                    if (!art.style().equals("clear")) {
                        // A colourless liquid (hootch) or a gas: the body and its shade are see-through, the rim and gloss stay solid.
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                int c = img.get(x, y);
                                int rgb = c & 0xFFFFFF;
                                if ((c >>> 24) != 0 && (rgb == (body & 0xFFFFFF) || rgb == (shadeTone & 0xFFFFFF))) {
                                    img.set(x, y, (rgb == (body & 0xFFFFFF) ? 0x70 : 0xA0) << 24 | rgb);
                                }
                            }
                        }
                    }
                }
                case "oil", "tar" -> {
                    // A sheen on black: a gloss down the lit side and a faint glint low on the far side. Cold on oil
                    // (blue-grey, violet), warm on tar (amber, rust), which also drags a thick foot.
                    img.set(5, 9, highlight);
                    img.set(5, 10, light);
                    img.set(10, 11, tar ? hex(0x7A4A22) : hex(0x3C3450));
                    img.set(9, 12, tar ? hex(0x5A3418) : hex(0x2E2A3E));
                    if (tar) {
                        img.set(5, 8, light);
                        img.set(6, 11, hex(0x8A6A3E));
                        img.set(11, 13, tone(shadeTone, 0.82, 1, 0));
                    }
                    for (int x = 6; x <= 10; x++) {
                        img.set(x, 14, tone(shadeTone, 0.82, 1, 0));
                    }
                }
                case "thick" -> {
                    // A long gloss down the lit side, and a heavier underside.
                    img.set(5, 9, highlight);
                    img.set(5, 10, light);
                    for (int x = 6; x <= 10; x++) {
                        img.set(x, 14, tone(shadeTone, 0.82, 1, 0));
                    }
                    img.set(11, 13, tone(shadeTone, 0.82, 1, 0));
                }
                case "slime" -> {
                    // Bubbles: two small rings of light.
                    img.set(8, 10, light);
                    img.set(9, 9, highlight);
                    img.set(6, 12, light);
                    img.set(9, 12, shadeTone);
                }
                default -> {
                    // Molten: a bright core low in the drop, a fleck of dark crust on the far side.
                    img.set(6, 10, highlight);
                    img.set(7, 10, highlight);
                    img.set(6, 11, light);
                    img.set(7, 11, highlight);
                    img.set(8, 11, light);
                    img.set(10, 9, outlineTone);
                    img.set(10, 10, tone(shadeTone, 0.8, 1, 0));
                }
            }
            img.save("item/" + art.id() + "_droplet");
        }
    }

    /** The same numbers as AntModel.cropWidth, cropHeight and cropLength. */
    static double[] cropSize(String caste) {
        Shape s = shape(caste);
        boolean queen = caste.equals("queen");
        return new double[] {s.gasterW() * (queen ? 1.2 : 1.5), s.gasterH() * (queen ? 1.2 : 1.5), s.gasterL() * (queen ? 1.1 : 1.25)};
    }

    /** The crop's chamfer (AntModel.CROP_CHAMFER). */
    static final double CROP_CHAMFER = 1.5;

    /**
     * entity/ant/crop_<caste> (the liquid) and crop_skin_<caste> (the skin round it): a Liquid Ant's crop, tinted in game.
     * Laid out for the four boxes of AntModel.cropBoxes, each in its own quarter of the texture.
     */
    static void cropSkins() {
        double c = CROP_CHAMFER;
        for (String caste : new String[] {"worker", "soldier", "queen"}) {
            double[] size = cropSize(caste);
            double w = size[0], h = size[1], l = size[2];
            // The four boxes (as AntModel.cropBoxes), and how far below the crop's top each one starts.
            Box[] boxes = {new Box("crop", 0, 0, w, h - 2 * c, l - 2 * c), new Box("crop", 64, 0, w - 2 * c, h, l - 2 * c),
                    new Box("crop", 0, 32, w - 2 * c, h - 2 * c, l), new Box("crop", 64, 32, w - c, h - c, l - c)};
            double[] drop = {c, 0, c, c / 2};
            Img liquid = new Img(128, 64);
            Img skin = new Img(128, 64);
            Random random = new Random(caste.hashCode() * 31L + 5);
            for (int b = 0; b < boxes.length; b++) {
                int[][] faces = faces(boxes[b]);
                for (int f = 0; f < 6; f++) {
                    int[] r = faces[f];
                    for (int y = r[1]; y < r[3]; y++) {
                        for (int x = r[0]; x < r[2]; x++) {
                            // How far down the whole crop: 0 at its top, 1 at its bottom.
                            double down = f == 0 ? drop[b] / h : f == 1 ? 1 - drop[b] / h : (drop[b] + (y - r[1]) + 0.5) / h;
                            down = Math.clamp(down, 0, 1);
                            int v = (int) Math.round(252 - 96 * Math.pow(down, 1.3)) + random.nextInt(5) - 2;
                            liquid.set(x, y, gray(Math.clamp(v, 0, 255)));
                            boolean edge = x == r[0] || x == r[2] - 1 || y == r[1] || y == r[3] - 1;
                            skin.set(x, y, argb(edge ? 104 : 58, 255, 255, 255));
                        }
                    }
                    int width = r[2] - r[0];
                    int height = r[3] - r[1];
                    if (f >= 2 && b != 2 && height >= 4) {
                        // The gloss on the liquid: a short bright run high on each side, off centre.
                        int gx = r[0] + Math.max(1, width / 5);
                        for (int x = gx; x < gx + Math.max(2, width / 3); x++) {
                            liquid.set(x, r[1] + 1, gray(255));
                        }
                        liquid.set(gx, r[1] + 2, gray(255));
                        // Bubbles.
                        for (int i = 0; i < 2; i++) {
                            int bx = r[0] + 1 + random.nextInt(Math.max(1, width - 2));
                            int by = r[1] + 2 + random.nextInt(Math.max(1, height - 3));
                            liquid.set(bx, by, gray(Math.min(255, (liquid.get(bx, by) & 0xFF) + 28)));
                        }
                    }
                    if (f == 0 && b == 1) {
                        // The skin's gloss, where the light falls on top of the crop.
                        int gx = r[0] + Math.max(1, width / 4);
                        int gy = r[1] + Math.max(1, height / 4);
                        skin.fill(gx, gy, gx + 2, gy + Math.max(3, height / 3), argb(214, 255, 255, 255));
                        skin.set(gx + 2, gy, argb(170, 255, 255, 255));
                    }
                }
            }
            liquid.save("entity/ant/crop_" + caste);
            skin.save("entity/ant/crop_skin_" + caste);
        }
    }

    /** The Liquid Ants' rows of entity/ant_side.png: rows 10 to 12 the full crop per caste, rows 13 to 15 the gaster's plates. */
    static void sideCrops(Img img) {
        String[] castes = {"worker", "soldier", "queen"};
        for (int c = 0; c < 3; c++) {
            boolean queen = castes[c].equals("queen");
            double cx = queen ? 5.9 : 5.6;
            double cy = queen ? 6.1 : 6.3;
            double rx = queen ? 5.8 : 5.3;
            double ry = queen ? 4.7 : 4.4;
            for (int frame = 0; frame < 4; frame++) {
                int ox = frame * SIDE_CELL_W;
                int row = (10 + c) * SIDE_CELL_H;
                int plates = (13 + c) * SIDE_CELL_H;
                ellipse(img, ox + cx, row + cy, rx, ry, gray(236));
                for (int y = 0; y < SIDE_CELL_H; y++) {
                    for (int x = 0; x < SIDE_CELL_W; x++) {
                        if (img.get(ox + x, row + y) == 0) {
                            continue;
                        }
                        boolean edge = img.get(ox + x - 1, row + y) == 0 || img.get(ox + x + 1, row + y) == 0 || y == 0
                                || img.get(ox + x, row + y - 1) == 0 || img.get(ox + x, row + y + 1) == 0;
                        int v = edge ? 150 : y > cy + ry * 0.35 ? 196 : 236;
                        img.set(ox + x, row + y, gray(v));
                    }
                }
                // The gloss, up on the side the light comes from.
                int gx = (int) Math.round(cx - rx * 0.45);
                int gy = (int) Math.round(cy - ry * 0.5);
                img.set(ox + gx, row + gy, gray(255));
                img.set(ox + gx + 1, row + gy, gray(255));
                img.set(ox + gx, row + gy + 1, gray(250));
                // The plates: short dark bars riding the top of the bag.
                for (int k = 0; k < 4; k++) {
                    int px = (int) Math.round(cx - rx * 0.62 + k * rx * 0.42);
                    for (int dx = 0; dx < 2; dx++) {
                        for (int y = 0; y < SIDE_CELL_H; y++) {
                            if (img.get(ox + px + dx, row + y) != 0) {
                                img.set(ox + px + dx, plates + y, gray(214));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /** The Liquid Chamber: its floor of damp clay, the wax pots on its rack and the pale pool in its bowl. */
    static void liquidChamber() {
        int[] clay = ramp(0x51473E, 0x65594D, 0x7A6D5F, 0x8F8171, 0xA39584);
        Img top = new Img(16, 16);
        render(top, clusters(801, clay.length - 1, 0.25, 0.05), clay);
        for (int[] ring : new int[][] {{4, 4}, {11, 6}, {6, 12}}) {
            top.set(ring[0], ring[1], clay[0]);
            top.set(ring[0] + 1, ring[1], clay[1]);
            top.set(ring[0], ring[1] + 1, clay[1]);
            top.set(ring[0] + 1, ring[1] + 1, clay[4]);
        }
        top.save("block/liquid_chamber_top");

        int[] wax = ramp(0x7A4A12, 0xA2661A, 0xC8882A, 0xE6B052, 0xF8DC98);
        // Wax: soft blotches of the amber ramp (no grain or bands: it is not wood), with a few glints.
        Img gourd = new Img(16, 16);
        render(gourd, clusters(803, wax.length - 2, 0.3, 0.1), wax);
        for (int[] glint : new int[][] {{5, 5}, {8, 5}, {11, 6}, {6, 8}, {2, 12}, {13, 2}}) {
            gourd.set(glint[0], glint[1], wax[4]);
        }
        gourd.save("block/liquid_chamber_gourd");

        int[] pale = ramp(0x5F7F8E, 0x7FA0AE, 0xA4C2CE, 0xC8DEE6, 0xE8F4F8);
        Img pool = new Img(16, 16);
        render(pool, clusters(805, 3, 0.2, 0.1), pale);
        for (int[] glint : new int[][] {{6, 12}, {7, 12}, {9, 13}, {3, 4}, {12, 3}}) {
            pool.set(glint[0], glint[1], pale[4]);
        }
        pool.save("block/liquid_chamber_pool");
    }

    /**
     * The Droplet Centrifuge, built on {@link #labBody}. Each side texture is the block's whole side: the lid's edge
     * (rows 0-1), the glass section (rows 2-7) and the casing (rows 8-15).
     */
    static void centrifuge() {
        for (int variant = 0; variant < 3; variant++) {
            boolean front = variant > 0;
            boolean on = variant == 2;
            Img img = labBody(91 + variant);
            if (front) {
                // The panel: a dark display with the tank as a teal bar and two speed marks, the status light under it.
                img.fill(3, 9, 13, 12, GRAPHITE[0]);
                frame(img, 3, 9, 13, 12, GRAPHITE[2]);
                for (int x = 4; x < 9; x++) {
                    img.set(x, 10, on ? TEAL[x < 7 ? 4 : 3] : TEAL[1]);
                }
                img.set(10, 10, on ? HELIX_A : shade(HELIX_A, 0.55));
                img.set(11, 10, on ? HELIX_B : shade(HELIX_B, 0.55));
                img.set(7, 12, on ? 0xFF6CDA4E : GRAPHITE[2]);
                img.set(8, 12, on ? 0xFF6CDA4E : GRAPHITE[2]);
            } else {
                // The sides, as on the lab: a centred glass tube of teal liquid (lying down here), a graphite cap either end.
                for (int x = 5; x < 11; x++) {
                    img.set(x, 10, x == 5 || x == 10 ? GRAPHITE[2] : TEAL[x == 7 || x == 8 ? 3 : 2]);
                    img.set(x, 11, x == 5 || x == 10 ? GRAPHITE[2] : TEAL[x == 7 || x == 8 ? 2 : 1]);
                }
            }
            img.save("block/centrifuge_" + (front ? "front" + (on ? "_working" : "") : "side"));
        }
        // The top: the lab's enamel lid, with a steel hub cap in a graphite frame over the rotor's shaft.
        Img top = new Img(16, 16);
        brushed(top, 0, 0, 16, 16, ENAMEL, 4.0, 3.0, 0.35, 97);
        frame(top, 0, 0, 16, 16, ENAMEL[1]);
        frame(top, 4, 4, 12, 12, GRAPHITE[2]);
        top.fill(5, 5, 11, 11, STEEL[3]);
        top.fill(6, 6, 10, 10, STEEL[5]);
        top.fill(7, 7, 9, 9, STEEL[6]);
        top.save("block/centrifuge_top");
        // The deck under the rotor: brushed steel, the bearing ring, the shaft's seat.
        Img deck = new Img(16, 16);
        brushed(deck, 0, 0, 16, 16, STEEL, 3.4, 2.4, 0.5, 98);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double r = Math.hypot(x - 7.5, y - 7.5);
                if (r > 5.2 && r < 6.2) {
                    deck.set(x, y, STEEL[0]);
                } else if (r < 1.6) {
                    deck.set(x, y, GRAPHITE[0]);
                }
            }
        }
        deck.save("block/centrifuge_deck");
        // The glass: clear but for two glints, a light upper edge and a darker lower one (cutout: no half-clear pixels).
        Img glass = new Img(16, 16);
        for (int x = 2; x < 14; x++) {
            glass.set(x, 2, hex(0xD6ECF2));
            glass.set(x, 7, hex(0x6F8E9B));
        }
        for (int[] glint : new int[][] {{3, 3}, {4, 3}, {3, 4}, {12, 6}, {11, 6}, {12, 5}}) {
            glass.set(glint[0], glint[1], hex(0xEAF8FC));
        }
        glass.save("block/centrifuge_glass");
    }

    static void liquids() {
        droplets();
        siteIcons();
        cropSkins();
        liquidChamber();
        centrifuge();
    }

    // --- The nest behind the glass ---
    //
    // Every colony block is a slab of nest material behind glass: per climate, a face and a darker floor for what is carved.

    /** The slab's face by climate, in NestRenderer's row order: temperate, cold, hot, underground, nether, end. */
    static final int[][] NEST_FACE = {
            ramp(0x644329, 0x765233, 0x8A6240, 0x9B724D, 0xAC825A),
            ramp(0x584E45, 0x696055, 0x7C7065, 0x8D8176, 0xB4BDC6),
            ramp(0xB09058, 0xC2A368, 0xD6BA7A, 0xE2C98A, 0xECD79C),
            ramp(0x4F4C47, 0x615E58, 0x74716B, 0x86837D, 0x97948E),
            ramp(0x461A18, 0x5A2422, 0x6E2E2A, 0x80383A, 0x91453F),
            ramp(0xA9AE7C, 0xBDC289, 0xD3D69A, 0xE2E4AA, 0xEEEFC0)};
    /** The floor of what is carved, by climate: the same earth, deeper and in shadow (about half as bright as the face). */
    static final int[][] NEST_FLOOR = {
            ramp(0x2C1D13, 0x362419, 0x402B1F),
            ramp(0x25201B, 0x2E2722, 0x372F29),
            ramp(0x514027, 0x5D4A2E, 0x695536),
            ramp(0x201F1E, 0x282725, 0x31302D),
            ramp(0x1D0A09, 0x260E0D, 0x301312),
            ramp(0x4E4B36, 0x5A573F, 0x666347)};

    /** The tone map of a climate's slab face (tones 0 to 4 of its ramp). */
    static int[][] nestFace(int climate) {
        int[][] t = clusters(700 + climate * 13, 4, 0.3, 0);
        Random random = new Random(900 + climate);
        switch (climate) {
            case 0 -> pellets(t, 4, 731, 4);
            case 1 -> {
                // Frost: a bright crystal with a paler one beside it.
                for (int i = 0; i < 6; i++) {
                    int x = random.nextInt(16);
                    int y = random.nextInt(16);
                    t[y][x] = 4;
                    tone(t, x + 1, y, 1, 3);
                }
            }
            case 2 -> {
                // Strata: a thin darker seam every few rows, wavering a pixel, lit just under it.
                double[][] wave = valueNoise(741, 8);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int band = Math.floorMod(y + (int) Math.round(wave[0][x] * 2), 5);
                        if (band == 0) {
                            t[y][x] = Math.max(0, t[y][x] - 1);
                        } else if (band == 1) {
                            t[y][x] = Math.min(4, t[y][x] + 1);
                        }
                    }
                }
            }
            case 3 -> {
                // Cracks: short dark runs stepping down to the right.
                for (int i = 0; i < 4; i++) {
                    int x = random.nextInt(16);
                    int y = random.nextInt(16);
                    for (int k = 0; k < 3 + random.nextInt(2); k++) {
                        t[Math.floorMod(y + k / 2, 16)][Math.floorMod(x + k, 16)] = 0;
                    }
                }
            }
            case 5 -> {
                // Pebbles: a pale knob two wide, a shadow tucked under it and round its right end.
                for (int i = 0; i < 7; i++) {
                    int x = random.nextInt(16);
                    int y = random.nextInt(16);
                    t[y][x] = 4;
                    t[y][(x + 1) % 16] = 4;
                    t[(y + 1) % 16][x] = 1;
                    t[(y + 1) % 16][(x + 1) % 16] = 0;
                    t[y][(x + 2) % 16] = 1;
                }
            }
            default -> {
                // Pits: a dark hollow two wide, its lower lip lit.
                for (int i = 0; i < 5; i++) {
                    int x = random.nextInt(16);
                    int y = random.nextInt(16);
                    t[y][x] = 0;
                    t[y][(x + 1) % 16] = 0;
                    tone(t, x, y + 1, 1, 4);
                    tone(t, x + 1, y + 1, 1, 4);
                }
            }
        }
        return t;
    }

    static void nest() {
        Img atlas = new Img(128, 128);
        int[][][] face = new int[NEST_FACE.length][][];
        int[][][] floor = new int[NEST_FACE.length][][];
        for (int climate = 0; climate < NEST_FACE.length; climate++) {
            face[climate] = nestFace(climate);
            floor[climate] = clusters(800 + climate * 17, 3, 0.15, 0);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    atlas.set(x, climate * 16 + y, NEST_FACE[climate][Math.clamp(face[climate][y][x], 0, 4)]);
                    atlas.set(16 + x, climate * 16 + y, NEST_FLOOR[climate][Math.clamp(floor[climate][y][x], 0, 2)]);
                }
            }
        }
        // Plain white, tinted in game: brood, crumbs, shelves, portals, whatever lies in a chamber.
        atlas.fill(112, 112, 128, 128, 0xFFFFFFFF);
        atlas.save("entity/nest");

        // The case: plaster panels in the oak frame, with a few pores and two faint trowel strokes.
        int[][] t = clusters(761, 3, 0.1, 0);
        for (int[] row : t) {
            for (int x = 0; x < 16; x++) {
                row[x] += 1;
            }
        }
        Random random = new Random(762);
        for (int i = 0; i < 7; i++) {
            int x = random.nextInt(16);
            int y = random.nextInt(16);
            t[y][x] = 0;
            tone(t, x + 1, y + 1, 1, 4);
        }
        for (int i = 0; i < 2; i++) {
            int x = random.nextInt(16);
            int y = random.nextInt(16);
            for (int k = 0; k < 5 + random.nextInt(3); k++) {
                tone(t, x + k, y, 1, 4);
            }
        }
        Img panel = new Img(16, 16);
        render(panel, t, ramp(0xB3A88F, 0xCDC3AC, 0xD7CEB9, 0xE0D8C5, 0xEAE3D2));
        panel.save("block/formicarium_panel");

        nestFronts(face[0], floor[0]);
    }

    /** The colony blocks, in the order of the client's NestLayout.Kind. */
    static final String[] NEST_BLOCKS = {"terrarium", "nest_chamber", "foraging_site", "hunting_ground", "harvesting_site", "storage",
            "queen_chamber", "nether_foraging_site", "end_foraging_site", "colony_connector", "liquid_chamber"};

    /**
     * A picture of each colony block's front, for its item and for far-away blocks. Compiles the client's NestLayout and
     * asks it for each kind's layout, so the two never differ.
     */
    static void nestFronts(int[][] face, int[][] floor) {
        try {
            Path out = Files.createTempDirectory("nestlayout");
            int failed = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.toString(),
                    "client/src/main/java/com/antcolonies/client/nest/NestLayout.java");
            if (failed != 0) {
                throw new IllegalStateException("NestLayout.java did not compile");
            }
            try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()})) {
                Class<?> layoutClass = loader.loadClass("com.antcolonies.client.nest.NestLayout");
                Class<?> kindClass = loader.loadClass("com.antcolonies.client.nest.NestLayout$Kind");
                Class<?> featureClass = loader.loadClass("com.antcolonies.client.nest.NestLayout$Feature");
                Method create = layoutClass.getMethod("create", kindClass, long.class, int[].class, int.class);
                Object[] kinds = kindClass.getEnumConstants();
                if (kinds.length != NEST_BLOCKS.length) {
                    throw new IllegalStateException("NEST_BLOCKS must list the " + kinds.length + " kinds of NestLayout.Kind, in its order");
                }
                for (int k = 0; k < kinds.length; k++) {
                    Object layout = create.invoke(null, kinds[k], 7L, new int[] {-1, -1, -1, -1}, 2);
                    boolean[] carved = (boolean[]) layoutClass.getField("carved").get(layout);
                    List<?> features = (List<?>) layoutClass.getField("features").get(layout);
                    Img img = new Img(16, 16);
                    for (int v = 0; v < 16; v++) {
                        for (int u = 0; u < 16; u++) {
                            boolean above = v < 15 && carved[(v + 1) * 16 + u];
                            boolean below = v > 0 && carved[(v - 1) * 16 + u];
                            int y = 15 - v;
                            if (carved[v * 16 + u]) {
                                boolean wall = u > 0 && !carved[v * 16 + u - 1];
                                img.set(u, y, shade(NEST_FLOOR[0][Math.clamp(floor[y][u], 0, 2)], v < 15 && !above ? 0.68 : wall ? 0.84 : 1));
                            } else {
                                img.set(u, y, shade(NEST_FACE[0][Math.clamp(face[y][u], 0, 4)], above ? 1.07 : below ? 0.9 : 1));
                            }
                        }
                    }
                    boolean nether = NEST_BLOCKS[k].startsWith("nether");
                    int seen = 0;
                    for (Object feature : features) {
                        char type = (char) featureClass.getMethod("type").invoke(feature);
                        int u = (int) featureClass.getMethod("u").invoke(feature);
                        int y = 15 - (int) featureClass.getMethod("v").invoke(feature);
                        boolean odd = seen++ % 2 == 1;
                        switch (type) {
                            case 'q' -> {
                                // The Queen: head, thorax and a big gaster.
                                img.set(u - 1, y, hex(0x8A5A30));
                                img.set(u, y, hex(0xA06C3A));
                                img.set(u + 1, y, hex(0xC68A4C));
                                img.set(u + 1, y - 1, hex(0xD9A260));
                            }
                            case 'e' -> {
                                if (!odd) {
                                    img.set(u, y, hex(0xF2ECDB));
                                }
                            }
                            case 'c' -> {
                                if (!odd) {
                                    img.set(u, y, hex(0xD9B26A));
                                }
                            }
                            case 'w' -> img.set(u, y, hex(0x33221A));
                            case 'k' -> img.set(u, y, hex(y > 0 && img.get(u, y - 1) == hex(0xF1E6C8) ? 0xDDCDA6 : 0xF1E6C8));
                            case 'm' -> img.set(u, y, hex(0xD9B040));
                            case 'i' -> {
                                int color = new int[] {0x5DAA3C, 0xC9A45C, 0x8FD3E8}[seen % 3];
                                img.set(u, y, hex(color));
                                img.set(u, y - 1, shade(hex(color), 1.15));
                            }
                            case 's' -> img.set(u, y, hex((u & 3) == 0 ? 0x8C6238 : 0x9A6F44));
                            case 'f' -> {
                                img.set(u, y, hex(0xE8DDC8));
                                img.set(u, y - 1, hex(odd ? 0xC9A57A : 0xB5462E));
                            }
                            case 'b' -> img.set(u, y, hex(0xE9E3D2));
                            case 'r' -> {
                                img.set(u, y, hex(0x3A2A20));
                                img.set(u, y + 1, hex(0xE0A030));
                            }
                            case 'l' -> img.set(u, y, hex(0x4F9AD6));
                            case 'o' -> img.set(u, y, hex(nether ? 0x1B1029 : 0xDBDE9E));
                            case 'p' -> img.set(u, y, hex(nether ? (odd ? 0x9A3DF0 : 0x7B2BC8) : (odd ? 0x14463A : 0x1D6150)));
                            case 'g' -> img.set(u, y, hex(0x97EBC6));
                            case 'h' -> img.set(u, y, hex(0x140C08));
                            case 'H' -> img.set(u, y, hex(NEST_BLOCKS[k].equals("foraging_site") ? 0xCFE8FF : 0x1A100A));
                            default -> {
                            }
                        }
                    }
                    img.save("block/" + NEST_BLOCKS[k] + "_front");
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        liquids();
        antSkins();
        costumes();
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
        nest();
        System.out.println("Wrote " + written + " textures.");
    }
}
