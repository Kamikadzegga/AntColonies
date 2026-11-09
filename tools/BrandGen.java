import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Paints the mod's branding as pixel art: an ant's head (front view) and the name in a pixel font below it. Writes
 * the mod-list icon and banner into the jar's resources, and the logo, banner and social preview for the repository
 * into docs/images. Run from the project root with JDK 25: {@code java tools/BrandGen.java}.
 */
public class BrandGen {
    static final Path RES = Path.of("common/src/main/resources");
    static final Path DOCS = Path.of("docs/images");

    // Palette: a red wood ant's chitin, its eyes, and amber for the lettering.
    static final int CLEAR = 0;
    static final int OUTLINE = rgb(28, 14, 10);
    static final int[] CHITIN = {rgb(72, 32, 20), rgb(112, 50, 28), rgb(152, 72, 36), rgb(196, 104, 54), rgb(238, 164, 102)};
    static final int EYE = rgb(18, 14, 18);
    static final int EYE_SHEEN = rgb(70, 64, 86);
    static final int GLINT = rgb(236, 242, 250);
    static final int MANDIBLE_DARK = rgb(96, 44, 18);
    static final int MANDIBLE = rgb(150, 78, 32);
    static final int MANDIBLE_LIGHT = rgb(206, 128, 62);
    static final int[] LETTER = {rgb(255, 222, 140), rgb(246, 186, 92), rgb(222, 142, 58), rgb(170, 96, 38)};
    static final int LETTER_SHADOW = rgb(54, 26, 14);

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int shade(int color, double f) {
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (color & 0xFF) * f);
        return rgb(r, g, b);
    }

    /** A small pixel canvas. */
    static final class Art {
        final int w;
        final int h;
        final int[] px;

        Art(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new int[w * h];
        }

        void set(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                px[y * w + x] = c;
            }
        }

        int get(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h ? px[y * w + x] : CLEAR;
        }

        boolean solid(int x, int y) {
            return (get(x, y) >>> 24) != 0;
        }

        /** Draws another canvas onto this one (its clear pixels let this one show through). */
        void stamp(Art art, int ox, int oy) {
            for (int y = 0; y < art.h; y++) {
                for (int x = 0; x < art.w; x++) {
                    if (art.solid(x, y)) {
                        set(ox + x, oy + y, art.get(x, y));
                    }
                }
            }
        }

        /** A one-pixel outline around everything solid (sides and corners). */
        void outline(int color) {
            boolean[] solid = new boolean[w * h];
            for (int i = 0; i < px.length; i++) {
                solid[i] = (px[i] >>> 24) != 0;
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (solid[y * w + x]) {
                        continue;
                    }
                    boolean edge = false;
                    for (int dy = -1; dy <= 1 && !edge; dy++) {
                        for (int dx = -1; dx <= 1 && !edge; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            edge = nx >= 0 && ny >= 0 && nx < w && ny < h && solid[ny * w + nx];
                        }
                    }
                    if (edge) {
                        set(x, y, color);
                    }
                }
            }
        }

        /** Saves the canvas with every pixel drawn as a {@code scale} x {@code scale} block. */
        void save(Path file, int scale) throws IOException {
            BufferedImage image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h * scale; y++) {
                for (int x = 0; x < w * scale; x++) {
                    image.setRGB(x, y, px[(y / scale) * w + x / scale]);
                }
            }
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
            System.out.println("Wrote " + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        }
    }

    // ------------------------------------------------------------------ The ant's head

    /**
     * The head from the front, 32 x 32: a heart-shaped head capsule lit from the upper left, big compound eyes on its
     * sides, elbowed antennae with clubbed tips, and two curved mandibles that cross below.
     */
    static Art head() {
        Art art = new Art(32, 33);
        // Antennae first, so the head is drawn over their bases.
        antenna(art, false);
        antenna(art, true);
        // Head capsule: two upper lobes and a rounder lower half, with a slight dip at the top middle.
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double cx = x + 0.5;
                double cy = y + 0.5;
                boolean lobe = dist(cx, cy, 11.6, 13.8) <= 7.2 || dist(cx, cy, 20.4, 13.8) <= 7.2;
                double ex = (cx - 16) / 8.8;
                double ey = (cy - 17.6) / 6.8;
                boolean lower = ex * ex + ey * ey <= 1;
                if (lobe || lower) {
                    art.set(x, y, chitinTone(cx, cy));
                }
            }
        }
        // A dark seam down the middle of the face and a small lit ridge beside it.
        for (int y = 10; y <= 20; y++) {
            art.set(16, y, CHITIN[1]);
            if (y >= 11 && y <= 15) {
                art.set(15, y, CHITIN[3]);
            }
        }
        // Antennal sockets, low on the face.
        for (int[] socket : new int[][] {{12, 20}, {19, 20}}) {
            art.set(socket[0], socket[1], CHITIN[0]);
            art.set(socket[0] + 1, socket[1], CHITIN[1]);
        }
        eye(art, 6.6, 15.6, false);
        eye(art, 25.4, 15.6, true);
        // The right blade first, so the left one crosses over it.
        mandible(art, true);
        mandible(art, false);
        art.outline(OUTLINE);
        return art;
    }

    static double dist(double x, double y, double cx, double cy) {
        return Math.hypot(x - cx, y - cy);
    }

    /** Light from the upper left on a dome: five tones, a bright spot on the left lobe. */
    static int chitinTone(double x, double y) {
        double nx = (x - 16) / 11.0;
        double ny = (y - 16.5) / 10.5;
        double light = 0.42 + 0.62 * (nx * -0.55 + ny * -0.75) - 0.3 * (nx * nx + ny * ny);
        if (dist(x, y, 10.6, 10.4) < 1.1 || dist(x, y, 12.2, 9.6) < 0.8) {
            return CHITIN[4];
        }
        int tone = light > 0.78 ? 3 : light > 0.5 ? 2 : light > 0.22 ? 1 : 0;
        return CHITIN[tone];
    }

    /** A compound eye: a dark oval with a blue sheen at the bottom and a white glint at the top. */
    static void eye(Art art, double cx, double cy, boolean right) {
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double ex = (x + 0.5 - cx) / 2.6;
                double ey = (y + 0.5 - cy) / 3.4;
                if (ex * ex + ey * ey <= 1) {
                    art.set(x, y, ey > 0.35 ? EYE_SHEEN : EYE);
                }
            }
        }
        int gx = (int) Math.floor(cx) + (right ? 0 : -1);
        int gy = (int) Math.floor(cy) - 2;
        art.set(gx, gy, GLINT);
        art.set(gx + (right ? -1 : 1), gy + 1, shade(GLINT, 0.75));
    }

    /** An elbowed antenna: the scape goes up and out from the top of the head, the club bends outward at the end. */
    static void antenna(Art art, boolean right) {
        int[][] path = {{12, 9}, {11, 8}, {10, 7}, {9, 6}, {8, 5}, {8, 4}, {7, 3}, {6, 3}, {5, 3}, {4, 3}, {3, 4}};
        for (int[] p : path) {
            int x = right ? 31 - p[0] : p[0];
            art.set(x, p[1], CHITIN[1]);
        }
        // The club at the tip, and a little light along the scape.
        int[][] club = {{2, 4}, {3, 5}, {2, 5}};
        for (int[] p : club) {
            art.set(right ? 31 - p[0] : p[0], p[1], CHITIN[2]);
        }
        art.set(right ? 31 - 9 : 9, 6, CHITIN[3]);
        art.set(right ? 31 - 8 : 8, 4, CHITIN[3]);
    }

    /**
     * A mandible: a thick hook from the lower corner of the face that bulges outward and curls in, so the two are a pair
     * of open pincers below the face. Lit along its outer edge, dark along its inner edge, with a tooth.
     */
    static void mandible(Art art, boolean right) {
        // Rows of the left blade: y, first x, last x.
        int[][] rows = {{22, 9, 11}, {23, 8, 10}, {24, 7, 9}, {25, 7, 9}, {26, 7, 9}, {27, 8, 10}, {28, 9, 11}, {29, 10, 12}, {30, 12, 13}, {31, 13, 13}};
        for (int[] row : rows) {
            for (int x = row[1]; x <= row[2]; x++) {
                int color = x == row[1] ? MANDIBLE_LIGHT : x == row[2] ? MANDIBLE_DARK : MANDIBLE;
                art.set(right ? 31 - x : x, row[0], color);
            }
        }
        // A tooth on the inner edge.
        art.set(right ? 31 - 10 : 10, 27, MANDIBLE_DARK);
    }

    // ------------------------------------------------------------------ The name

    /** The pixel font: 7 rows tall, '#' is ink. */
    static String[] glyph(char c) {
        return switch (c) {
            case 'A' -> new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"};
            case 'N' -> new String[] {"#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"};
            case 'T' -> new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."};
            case 'C' -> new String[] {".####", "#....", "#....", "#....", "#....", "#....", ".####"};
            case 'O' -> new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."};
            case 'L' -> new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"};
            case 'I' -> new String[] {"###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"};
            case 'E' -> new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"};
            case 'S' -> new String[] {".####", "#....", "#....", ".###.", "....#", "....#", "####."};
            case ' ' -> new String[] {"...", "...", "...", "...", "...", "...", "..."};
            default -> throw new IllegalArgumentException("No glyph for " + c);
        };
    }

    /**
     * The name in pixel letters on the same grid as the head, lit from above in amber, with a dark outline and a drop
     * shadow.
     */
    static Art title(String text) {
        int gap = 1;
        int width = 0;
        for (char c : text.toCharArray()) {
            width += glyph(c)[0].length() + gap;
        }
        width -= gap;
        int scale = 1;
        Art ink = new Art(width * scale + 4, 7 * scale + 4);
        int x0 = 2;
        for (char c : text.toCharArray()) {
            String[] rows = glyph(c);
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    if (rows[row].charAt(col) != '#') {
                        continue;
                    }
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            int y = row * scale + sy;
                            // Lit from above: the top of each letter brightest.
                            int tone = y <= 1 ? 0 : y <= 3 ? 1 : y <= 5 ? 2 : 3;
                            ink.set(x0 + col * scale + sx, 2 + y, LETTER[tone]);
                        }
                    }
                }
            }
            x0 += (rows[0].length() + gap) * scale;
        }
        Art art = new Art(ink.w + 1, ink.h + 1);
        // The drop shadow: the letters again, one pixel down and right, in the outline color.
        for (int y = 0; y < ink.h; y++) {
            for (int x = 0; x < ink.w; x++) {
                if (ink.solid(x, y)) {
                    art.set(x + 1, y + 1, LETTER_SHADOW);
                }
            }
        }
        art.stamp(ink, 0, 0);
        art.outline(OUTLINE);
        return art;
    }

    /** The head above the name, centered, on a clear background. */
    static Art logo() {
        Art head = head();
        Art title = title("ANT COLONIES");
        int gap = 1;
        Art art = new Art(Math.max(head.w, title.w) + 4, head.h + gap + title.h + 2);
        art.stamp(head, (art.w - head.w) / 2, 1);
        art.stamp(title, (art.w - title.w) / 2, 1 + head.h + gap);
        return art;
    }

    // ------------------------------------------------------------------ Backgrounds

    /**
     * A slice of the ground: a strip of grass and the sky above it, and below, earth with tunnels and chambers that
     * little ants walk through. The logo goes in the middle, over a plain area of earth.
     */
    static Art ground(int w, int h, int grassY, long seed, int keepClear) {
        Random random = new Random(seed);
        Art art = new Art(w, h);
        int[] earth = {rgb(66, 44, 30), rgb(74, 50, 34), rgb(82, 56, 38)};
        int tunnel = rgb(40, 26, 18);
        int tunnelEdge = rgb(100, 70, 46);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (y < grassY) {
                    // Sky: a soft vertical gradient.
                    double f = y / (double) grassY;
                    art.set(x, y, rgb((int) (120 + 40 * f), (int) (170 + 30 * f), (int) (220 + 20 * f)));
                } else if (y < grassY + 3) {
                    art.set(x, y, y == grassY ? rgb(110, 176, 70) : y == grassY + 1 ? rgb(86, 150, 54) : rgb(70, 120, 46));
                } else {
                    int tone = random.nextInt(10) < 6 ? 1 : random.nextBoolean() ? 0 : 2;
                    art.set(x, y, earth[tone]);
                }
            }
        }
        // Grass blades poking up.
        for (int x = 0; x < w; x++) {
            if (random.nextInt(3) == 0) {
                art.set(x, grassY - 1, rgb(96, 164, 62));
            }
        }
        // Stones in the earth.
        for (int i = 0; i < w * h / 160; i++) {
            int sx = random.nextInt(w);
            int sy = grassY + 4 + random.nextInt(Math.max(1, h - grassY - 5));
            art.set(sx, sy, rgb(112, 104, 96));
            art.set(sx + 1, sy, rgb(92, 86, 80));
        }
        // Tunnels: wandering paths from the surface down, with round chambers along them; skip the middle (the logo).
        int middleL = w / 2 - keepClear;
        int middleR = w / 2 + keepClear;
        for (int t = 0; t < 7; t++) {
            int x = 6 + random.nextInt(w - 12);
            if (x > middleL - 6 && x < middleR + 6) {
                x = random.nextBoolean() ? middleL - 10 - random.nextInt(40) : middleR + 10 + random.nextInt(40);
            }
            int y = grassY + 3;
            int depth = h - grassY;
            // Walk down, drifting to one side for a while, then the other; one or two chambers on the way.
            int drift = random.nextBoolean() ? 1 : -1;
            int[] chambersAt = {depth / 3 + random.nextInt(Math.max(1, depth / 4)), depth * 2 / 3 + random.nextInt(Math.max(1, depth / 5))};
            int made = 0;
            int limit = 1 + random.nextInt(2);
            for (int s = 0; s < depth * 3 && y < h; s++) {
                carve(art, x, y, tunnel, tunnelEdge, middleL, middleR);
                if (random.nextInt(10) == 0) {
                    drift = -drift;
                }
                if (random.nextInt(5) < 2) {
                    x += drift;
                } else {
                    y++;
                }
                if (made < limit && y - grassY - 3 == chambersAt[made]) {
                    chamber(art, x, y, 5 + random.nextInt(3), 2, tunnel, tunnelEdge, middleL, middleR, random);
                    made++;
                    y += 3;
                }
            }
        }
        return art;
    }

    static void carve(Art art, int x, int y, int tunnel, int edge, int middleL, int middleR) {
        if (x > middleL && x < middleR) {
            return;
        }
        for (int dx = 0; dx <= 1; dx++) {
            art.set(x + dx, y, tunnel);
        }
        if (!isTunnel(art, x - 1, y, tunnel)) {
            art.set(x - 1, y, edge);
        }
        if (!isTunnel(art, x + 2, y, tunnel)) {
            art.set(x + 2, y, edge);
        }
    }

    static boolean isTunnel(Art art, int x, int y, int tunnel) {
        return art.get(x, y) == tunnel;
    }

    /** A flat-bottomed chamber with a couple of ants and an egg or two. */
    static void chamber(Art art, int cx, int cy, int rx, int ry, int tunnel, int edge, int middleL, int middleR, Random random) {
        if (cx + rx > middleL && cx - rx < middleR) {
            return;
        }
        for (int y = -ry - 1; y <= ry + 1; y++) {
            for (int x = -rx - 1; x <= rx + 1; x++) {
                double e = (x / (double) rx) * (x / (double) rx) + (y / (double) ry) * (y / (double) ry);
                if (e <= 1) {
                    art.set(cx + x, cy + y, tunnel);
                } else if (e <= 1.6 && !isTunnel(art, cx + x, cy + y, tunnel)) {
                    art.set(cx + x, cy + y, edge);
                }
            }
        }
        int floor = cy + ry;
        tinyAnt(art, cx - rx / 2, floor, random.nextBoolean());
        if (random.nextBoolean()) {
            tinyAnt(art, cx + rx / 2 - 2, floor, random.nextBoolean());
        }
        art.set(cx, floor, rgb(236, 230, 210));
        art.set(cx + 1, floor, rgb(214, 206, 186));
    }

    /** A four-pixel ant in profile, standing on {@code y}. */
    static void tinyAnt(Art art, int x, int y, boolean facingRight) {
        int body = rgb(150, 60, 34);
        int dark = rgb(96, 36, 22);
        int[][] pixels = facingRight ? new int[][] {{0, -1}, {1, -1}, {2, -1}, {3, -2}} : new int[][] {{3, -1}, {2, -1}, {1, -1}, {0, -2}};
        for (int[] p : pixels) {
            art.set(x + p[0], y + p[1], body);
        }
        art.set(x, y, dark);
        art.set(x + 2, y, dark);
        art.set(x + 3, y, dark);
    }

    /** A soft dark plate behind the logo so it stands out from the earth. */
    static void plate(Art art, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                boolean corner = (x == x0 || x == x0 + w - 1) && (y == y0 || y == y0 + h - 1);
                if (!corner) {
                    art.set(x, y, shade(art.get(x, y) | 0xFF000000, 0.55));
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Art head = head();
        Art logo = logo();
        // The icon is square: the head in the middle of a 34 x 34 canvas.
        Art icon = new Art(34, 34);
        icon.stamp(head, 1, 0);
        // In the jar: the mod list's icon and banner.
        icon.save(RES.resolve("icon.png"), 4);
        // The creative tab's icon: the head alone, as an item texture (32 x 32, drawn at item size in game).
        head.save(RES.resolve("assets/antcolonies/textures/item/ant_head.png"), 1);
        logo.save(RES.resolve("logo.png"), 4);
        // For the repository.
        icon.save(DOCS.resolve("icon.png"), 8);
        // CurseForge's project logo must be 400 x 400: the same icon centred on a 40 x 40 canvas, at exactly 10x.
        Art curseforge = new Art(40, 40);
        curseforge.stamp(icon, 3, 3);
        curseforge.save(DOCS.resolve("curseforge-logo.png"), 10);
        logo.save(DOCS.resolve("logo.png"), 8);
        // Banner, 1280 x 320 (art 256 x 64 at 5x): the logo on the earth.
        int keep = logo.w / 2 + 10;
        Art banner = ground(256, 64, 6, 7, keep);
        int by = 6 + (64 - 6 - logo.h) / 2;
        plate(banner, (256 - logo.w) / 2 - 3, by - 1, logo.w + 6, logo.h + 2);
        banner.stamp(logo, (256 - logo.w) / 2, by);
        banner.save(DOCS.resolve("banner.png"), 5);
        // Social preview, 1280 x 640 (art 256 x 128 at 5x), for the repository's Settings > Social preview.
        Art social = ground(256, 128, 22, 11, keep);
        int sy = 22 + (128 - 22 - logo.h) / 2;
        plate(social, (256 - logo.w) / 2 - 3, sy - 1, logo.w + 6, logo.h + 2);
        social.stamp(logo, (256 - logo.w) / 2, sy);
        social.save(DOCS.resolve("social-preview.png"), 5);
    }
}
