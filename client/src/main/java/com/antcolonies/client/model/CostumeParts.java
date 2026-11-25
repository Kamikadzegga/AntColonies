package com.antcolonies.client.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The small parts a costume adds to the ant model: horns, a hat, a book. Plain Java, so the texture generator reads
 * the same boxes and texture corners as the game (tools/TexGen.java compiles this class).
 * <p>
 * Units are the model's (1/16 block at scale 1); up is -y and the ant faces -z, as in AntModel. A part hangs from the
 * head, the thorax or the gaster and turns with it.
 */
public final class CostumeParts {
    /** The costumes, by the names of the species look's "costume". */
    public static final String[] NAMES = {"cow", "chicken", "sheep", "baker", "bookworm", "party", "pirate", "pig", "rabbit", "fisher"};

    public enum Anchor {
        HEAD, BODY, GASTER
    }

    /** The sizes of one caste's head, thorax and gaster (AntModel.Shape's numbers). */
    public record Dims(float thoraxW, float thoraxH, float thoraxL, float headW, float headH, float headL, float gasterW, float gasterH,
                       float gasterL) {}

    /**
     * One box.
     *
     * @param name  what it is: the texture generator paints by it
     * @param grow  how far the box is inflated; a shell (see {@link #shell}) has only this
     * @param u     its corner in the costume's texture, the layout of a model box
     */
    public record Part(String name, Anchor anchor, float pivotX, float pivotY, float pivotZ, float pitch, float yaw, float roll,
                       float x, float y, float z, float w, float h, float d, float grow, int u, int v) {
        Part at(int u, int v) {
            return new Part(name, anchor, pivotX, pivotY, pivotZ, pitch, yaw, roll, x, y, z, w, h, d, grow, u, v);
        }
    }

    // Free corners of the ant skin's layout (128 x 64): two regions no model box of any caste reaches.
    private static final int[][] REGIONS = {{14, 43, 64, 64}, {106, 28, 128, 64}};

    /** The parts of a costume for one caste, each with its place in the texture. */
    public static List<Part> parts(String costume, Dims s) {
        List<Part> parts = new ArrayList<>();
        float top = -s.headH() / 2 - 0.5F;
        float crown = -s.headL() * 0.5F;
        // Hats grow with the head: a soldier's is half again a worker's.
        float k = Math.min(1.5F, s.headW() / 5.2F);
        switch (costume) {
            case "cow" -> {
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("horn", Anchor.HEAD, side * s.headW() * 0.36F, top + 0.2F, crown, 0, 0, side * 0.35F, -0.5F, -1.9F, -0.5F, 1, 1.9F, 1));
                    parts.add(box("ear", Anchor.HEAD, side * s.headW() / 2, top + 1.2F, -s.headL() * 0.32F, 0, 0, side * 0.3F,
                            side > 0 ? 0 : -1.5F, -0.4F, -0.7F, 1.5F, 0.8F, 1.4F));
                }
                parts.add(box("bell", Anchor.BODY, 0, s.thoraxH() / 2 - 0.2F, -s.thoraxL() / 2 + 0.7F, 0, 0, 0, -0.7F, 0, -0.7F, 1.4F, 1.4F, 1.4F));
            }
            case "chicken" -> {
                parts.add(box("comb", Anchor.HEAD, 0, top, -s.headL() * 0.55F, 0, 0, 0, -0.4F, -1.7F, -1.5F, 0.8F, 1.7F, 3));
                parts.add(box("wattle", Anchor.HEAD, 0, s.headH() / 2 - 0.5F, -s.headL() + 0.4F, 0, 0, 0, -0.5F, 0, -0.5F, 1, 1.4F, 1));
                parts.add(box("beak", Anchor.HEAD, 0, s.headH() / 2 - 2.0F, -s.headL(), 0.15F, 0, 0, -0.8F, -0.5F, -1.5F, 1.6F, 1, 1.5F));
                float fan = s.gasterW() * 0.7F;
                parts.add(box("tail", Anchor.GASTER, 0, -s.gasterH() / 2 + 0.8F, s.gasterL() - 0.4F, -0.85F, 0, 0, -fan / 2, -3.4F, 0, fan, 3.4F, 0.6F));
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("tail_side", Anchor.GASTER, side * s.gasterW() * 0.32F, -s.gasterH() / 2 + 1.2F, s.gasterL() - 0.6F, -0.7F, 0,
                            side * 0.45F, -0.9F, -2.6F, 0, 1.8F, 2.6F, 0.5F));
                }
            }
            case "sheep" -> {
                parts.add(shell("wool_thorax", Anchor.BODY, -s.thoraxW() / 2, -s.thoraxH() / 2, -s.thoraxL() / 2, s.thoraxW(), s.thoraxH(), s.thoraxL(),
                        0.95F, 0, 0));
                parts.add(shell("wool_gaster", Anchor.GASTER, -s.gasterW() / 2, -s.gasterH() / 2, 0, s.gasterW(), s.gasterH(), s.gasterL(), 0.9F, 0, 20));
                parts.add(box("wool_cap", Anchor.HEAD, 0, top, crown, 0, 0, 0, -s.headW() * 0.38F, -1.1F, -s.headL() * 0.3F, s.headW() * 0.76F, 1.1F,
                        s.headL() * 0.6F));
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("ear", Anchor.HEAD, side * s.headW() / 2, top + 1.5F, -s.headL() * 0.35F, 0, 0, side * 0.45F,
                            side > 0 ? 0 : -1.6F, -0.4F, -0.7F, 1.6F, 0.8F, 1.4F));
                }
            }
            case "baker" -> {
                parts.add(box("toque_band", Anchor.HEAD, 0, top, crown, 0, 0, 0, -s.headW() * 0.4F, -1.3F, -s.headL() * 0.36F, s.headW() * 0.8F, 1.3F,
                        s.headL() * 0.72F));
                parts.add(box("toque_puff", Anchor.HEAD, 0, top - 1.2F, crown, -0.08F, 0, 0.06F, -s.headW() * 0.5F, -2.4F, -s.headL() * 0.45F, s.headW(),
                        2.4F, s.headL() * 0.9F));
            }
            case "bookworm" -> {
                float lens = s.headW() * 0.42F - 0.3F;
                float eyeY = -0.5F - s.headH() * 0.14F;
                parts.add(box("lens", Anchor.HEAD, 0, eyeY, -s.headL(), 0, 0, 0, -s.headW() * 0.42F, -0.9F, -0.35F, lens, 1.8F, 0.35F));
                parts.add(box("lens", Anchor.HEAD, 0, eyeY, -s.headL(), 0, 0, 0, 0.3F, -0.9F, -0.35F, lens, 1.8F, 0.35F));
                parts.add(box("bridge", Anchor.HEAD, 0, eyeY, -s.headL(), 0, 0, 0, -0.3F, -0.35F, -0.35F, 0.6F, 0.5F, 0.35F));
                parts.add(box("book_red", Anchor.BODY, 0, -s.thoraxH() / 2, -s.thoraxL() * 0.2F, 0, 0.12F, 0, -s.thoraxW() * 0.48F, -1.0F,
                        -s.thoraxL() * 0.26F, s.thoraxW() * 0.96F, 1.0F, s.thoraxL() * 0.52F));
                parts.add(box("book_blue", Anchor.BODY, 0, -s.thoraxH() / 2 - 1.0F, -s.thoraxL() * 0.2F, 0, -0.2F, 0, -s.thoraxW() * 0.38F, -0.8F,
                        -s.thoraxL() * 0.2F, s.thoraxW() * 0.76F, 0.8F, s.thoraxL() * 0.4F));
            }
            case "party" -> {
                parts.add(box("hat_low", Anchor.HEAD, 0, top, crown, 0, 0, 0, -1.3F * k, -1.2F * k, -1.3F * k, 2.6F * k, 1.2F * k, 2.6F * k));
                parts.add(box("hat_mid", Anchor.HEAD, 0, top - 1.2F * k, crown, 0, 0, 0, -0.9F * k, -1.2F * k, -0.9F * k, 1.8F * k, 1.2F * k, 1.8F * k));
                parts.add(box("hat_tip", Anchor.HEAD, 0, top - 2.4F * k, crown, 0, 0, 0, -0.5F * k, -1.1F * k, -0.5F * k, k, 1.1F * k, k));
                parts.add(box("pompom", Anchor.HEAD, 0, top - 3.5F * k, crown, 0, 0.6F, 0, -0.6F * k, -1.0F * k, -0.6F * k, 1.2F * k, k, 1.2F * k));
            }
            case "pirate" -> {
                parts.add(box("hat_brim", Anchor.HEAD, 0, top, crown, 0.06F, 0, 0, -s.headW() / 2 - 0.8F, -0.6F, -s.headL() / 2 - 0.4F, s.headW() + 1.6F,
                        0.6F, s.headL() + 0.8F));
                parts.add(box("hat_crown", Anchor.HEAD, 0, top - 0.5F, crown, 0.06F, 0, 0, -s.headW() * 0.38F, -1.9F, -s.headL() * 0.34F, s.headW() * 0.76F,
                        1.9F, s.headL() * 0.68F));
            }
            case "pig" -> {
                parts.add(box("snout", Anchor.HEAD, 0, 0.3F, -s.headL(), 0, 0, 0, -1.3F * k, -0.9F * k, -1.0F, 2.6F * k, 1.8F * k, 1.0F));
                for (int side = -1; side <= 1; side += 2) {
                    // Flop ears: flat, hanging forward over the eyes.
                    parts.add(box("pig_ear", Anchor.HEAD, side * s.headW() * 0.34F, top + 0.2F, -s.headL() * 0.42F, 0.55F, 0, side * 0.3F,
                            -0.9F * k, -0.6F, -1.9F * k, 1.8F * k, 0.6F, 1.9F * k));
                }
                // The tail: a stub that turns up, and the curl on top of it.
                float g = s.gasterW() / 6;
                float stub = 2.8F * g;
                float tailY = -0.4F - (float) Math.sin(0.9) * stub;
                float tailZ = s.gasterL() + 1.2F + (float) Math.cos(0.9) * stub;
                parts.add(box("curl", Anchor.GASTER, 0, -0.4F, s.gasterL() + 1.2F, 0.9F, 0, 0, -0.5F * g, -0.5F * g, 0, g, g, stub));
                parts.add(box("curl", Anchor.GASTER, 0.7F * g, tailY - 0.2F * g, tailZ - 0.3F * g, -0.5F, 0.9F, 0, -0.5F * g, -0.5F * g, -1.6F * g,
                        g, g, 1.6F * g));
            }
            case "rabbit" -> {
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("rabbit_ear", Anchor.HEAD, side * s.headW() * 0.22F, top + 0.2F, crown + 0.6F, -0.18F, 0, side * 0.12F,
                            -0.6F * k, -4.6F * k, -0.4F, 1.2F * k, 4.6F * k, 0.8F));
                }
                parts.add(box("teeth", Anchor.HEAD, 0, s.headH() / 2 - 0.9F, -s.headL(), 0, 0, 0, -0.6F, 0, -0.3F, 1.2F, 1.0F, 0.3F));
                parts.add(box("cottontail", Anchor.GASTER, 0, -s.gasterH() * 0.12F, s.gasterL() + 0.8F, 0, 0, 0, -1.1F, -1.1F, 0, 2.2F, 2.2F, 1.7F));
            }
            case "fisher" -> {
                // A sou'wester: a round crown and a brim that runs long and low over the neck.
                parts.add(box("rain_crown", Anchor.HEAD, 0, top, crown, 0, 0, 0, -s.headW() * 0.4F, -1.6F, -s.headL() * 0.36F, s.headW() * 0.8F, 1.6F,
                        s.headL() * 0.72F));
                parts.add(box("rain_brim", Anchor.HEAD, 0, top + 0.1F, crown, -0.14F, 0, 0, -s.headW() / 2 - 0.6F, -0.5F, -s.headL() * 0.5F - 0.5F,
                        s.headW() + 1.2F, 0.5F, s.headL() + 2.2F));
                // The rod over the right shoulder, its line and float behind.
                float reach = s.thoraxL() * 1.1F;
                float rodX = s.thoraxW() / 2 + 0.3F;
                float rodY = -s.thoraxH() / 2 + 0.3F;
                float tipY = rodY - (float) Math.sin(0.55) * reach;
                float tipZ = 0.5F + (float) Math.cos(0.55) * reach;
                // Long in x and turned to point back and up: a box long in z would not fit the texture's free corners.
                parts.add(box("rod", Anchor.BODY, rodX, rodY, 0.5F, 0, 0.55F - (float) Math.PI / 2, -(float) Math.PI / 2,
                        -2.2F, -0.3F, -0.3F, reach + 2.2F, 0.6F, 0.6F));
                parts.add(box("line", Anchor.BODY, rodX, tipY, tipZ, 0, 0, 0, -0.1F, 0, -0.1F, 0.2F, 1.4F, 0.2F));
                parts.add(box("float", Anchor.BODY, rodX, tipY + 1.4F, tipZ, 0, 0, 0, -0.6F, 0, -0.6F, 1.2F, 1.4F, 1.2F));
            }
            default -> throw new IllegalArgumentException("No such costume: " + costume);
        }
        return pack(parts, costume);
    }

    private static Part box(String name, Anchor anchor, float pivotX, float pivotY, float pivotZ, float pitch, float yaw, float roll,
                            float x, float y, float z, float w, float h, float d) {
        return new Part(name, anchor, pivotX, pivotY, pivotZ, pitch, yaw, roll, x, y, z, w, h, d, 0, -1, -1);
    }

    /** A copy of one of the model's own boxes, inflated: it takes that box's place in the texture, so a coat is painted there. */
    private static Part shell(String name, Anchor anchor, float x, float y, float z, float w, float h, float d, float grow, int u, int v) {
        return new Part(name, anchor, 0, 0, 0, 0, 0, 0, x, y, z, w, h, d, grow, u, v);
    }

    /** Gives every part without a place one in the free regions: left to right in rows, like shelves. */
    private static List<Part> pack(List<Part> parts, String costume) {
        List<Part> placed = new ArrayList<>();
        int region = 0;
        int x = REGIONS[0][0];
        int y = REGIONS[0][1];
        int shelf = 0;
        for (Part part : parts) {
            if (part.u() >= 0) {
                placed.add(part);
                continue;
            }
            int width = (int) Math.ceil(2 * (part.w() + part.d()));
            int height = (int) Math.ceil(part.d() + part.h());
            while (true) {
                int[] r = REGIONS[region];
                if (x + width > r[2]) {
                    // Next shelf.
                    x = r[0];
                    y += shelf;
                    shelf = 0;
                }
                if (x + width <= r[2] && y + height <= r[3]) {
                    break;
                }
                if (++region >= REGIONS.length) {
                    throw new IllegalStateException("The " + costume + " costume's parts do not fit the texture: " + part.name());
                }
                x = REGIONS[region][0];
                y = REGIONS[region][1];
                shelf = 0;
            }
            placed.add(part.at(x, y));
            x += width;
            shelf = Math.max(shelf, height);
        }
        return placed;
    }

    private CostumeParts() {}
}
