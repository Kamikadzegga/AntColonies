package com.antcolonies.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The small parts a costume adds to the ant model: horns, a hat, a book. Plain Java, so the texture generator reads
 * the same boxes and texture corners as the game (tools/TexGen.java compiles this class).
 * <p>
 * Units are the model's (1/16 block at scale 1); up is -y and the ant faces -z, as in AntModel. A part hangs from the
 * head, the thorax or the gaster and turns with it.
 */
public final class CostumeParts {
    /** The costumes, by the names of the species look's "costume". */
    public static final String[] NAMES = {"cow", "chicken", "sheep", "baker", "bookworm", "party", "pirate", "pig", "rabbit", "fisher",
            "carpenter", "leafcutter", "weaver"};

    /** What a companion wears or turns into, each a set of parts like a costume's (and a texture of its own). */
    public static final String[] WEAR = {"wear_pirate", "wear_explorer", "wear_miner", "wear_crown", "wear_chef", "wear_flower", "wear_backpack",
            "wear_party", "wear_plates", "wear_helm", "wear_bindle", "wear_jumping"};

    /** The helm of a suit of plates: a hat, the ant's own or one it was given, is worn in its place. */
    public static final String HELM = "wear_helm";

    /** The wears whose texture also paints the thorax, head or legs, and those that paint the gaster (TexGen's wears). */
    public static final Set<String> PAINTS_BODY = Set.of("wear_pirate", "wear_backpack", "wear_bindle", "wear_jumping");
    public static final Set<String> PAINTS_GASTER = Set.of("wear_jumping");

    /** How far a plate stands off the shell under it. */
    private static final float PLATE = 0.45F;
    /** How far over the head a carried leaf's lower edge is. */
    private static final float LEAF_CLEAR = 0.7F;

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
    /** A wear that paints nothing on the ant has the whole texture for its parts. */
    private static final int[][] WHOLE = {{0, 0, 128, 64}};

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
            case "carpenter" -> {
                // A hard hat with a peak and a ridge, a pencil tucked under its rim, and a saw laid along the back.
                parts.add(box("helmet", Anchor.HEAD, 0, top + 0.1F, crown, 0.04F, 0, 0, -s.headW() * 0.46F, -1.8F, -s.headL() * 0.42F, s.headW() * 0.92F,
                        1.8F, s.headL() * 0.84F));
                parts.add(box("helmet_peak", Anchor.HEAD, 0, top + 0.1F, crown - s.headL() * 0.42F, 0.12F, 0, 0, -s.headW() * 0.4F, -0.5F, -1.3F,
                        s.headW() * 0.8F, 0.5F, 1.3F));
                parts.add(box("helmet_ridge", Anchor.HEAD, 0, top - 1.7F, crown, 0.04F, 0, 0, -0.6F, -0.7F, -s.headL() * 0.36F, 1.2F, 0.7F,
                        s.headL() * 0.72F));
                parts.add(box("pencil", Anchor.HEAD, s.headW() / 2 + 0.1F, top + 1.5F, crown + 0.4F, 0.25F, -0.12F, 0, 0, -0.5F, -2.4F, 1, 1, 4.8F));
                // The saw rises to the rear, over the hump.
                float back = -s.thoraxH() / 2 - 0.3F;
                float sawZ = -s.thoraxL() * 0.12F;
                parts.add(box("saw_blade", Anchor.BODY, 0.2F, back, sawZ, 0.25F, 0.1F, 0, -1, -0.5F, 0, 2, 0.5F, Math.round(s.thoraxL() * 0.72F)));
                parts.add(box("saw_handle", Anchor.BODY, 0.2F, back, sawZ, 0.25F, 0.1F, 0, -1.5F, -1.0F, -2, 3, 1, 2));
            }
            case "leafcutter" -> {
                // A cut piece of leaf held up in the jaws: its stalk rises in front of the face and the blade stands over the
                // head like a sail, clear of the ant all round and a little askew. A tiny ant rides on it.
                float turn = 0.3F;
                float tilt = 0.1F;
                float py = top - LEAF_CLEAR;
                float pz = -s.headL() - 0.8F;
                float thick = 0.6F;
                float tall = 6.5F * k;
                float reach = s.headL() + 3.0F;
                float jaws = s.headH() / 2 - 1.4F;
                parts.add(box("leaf_stem", Anchor.HEAD, 0, py, pz, 0, turn, tilt, -thick / 2, 0, -0.4F, thick, jaws - py, 0.8F));
                // The blade, front to rear: a round lobe, the tall middle, and two steps to its point.
                float[][] blade = {{-2.0F, -0.2F, 0.62F, 0.14F}, {-0.2F, reach * 0.45F, 1.0F, 0}, {reach * 0.45F, reach * 0.78F, 0.92F, 0.2F},
                        {reach * 0.78F, reach, 0.78F, 0.42F}};
                String[] names = {"leaf_front", "leaf", "leaf_tail", "leaf_tip"};
                for (int i = 0; i < blade.length; i++) {
                    parts.add(box(names[i], Anchor.HEAD, 0, py, pz, 0, turn, tilt, -thick / 2, -tall * blade[i][2], blade[i][0], thick,
                            tall * (blade[i][2] - blade[i][3]), blade[i][1] - blade[i][0]));
                }
                parts.add(box("rider", Anchor.HEAD, 0, py, pz, 0, turn, tilt, -0.55F, -tall - 1.0F, reach * 0.2F, 1.1F, 1.0F, 2.2F));
                parts.add(box("rider_head", Anchor.HEAD, 0, py, pz, 0, turn, tilt, -0.65F, -tall - 1.3F, reach * 0.2F - 1.2F, 1.3F, 1.3F, 1.3F));
                // The spines of its back, two pairs.
                for (int i = 0; i < 2; i++) {
                    for (int side = -1; side <= 1; side += 2) {
                        parts.add(box("thorn", Anchor.BODY, side * s.thoraxW() * 0.3F, -s.thoraxH() / 2 + 0.2F, -s.thoraxL() * 0.24F + i * s.thoraxL() * 0.3F,
                                -0.3F, 0, side * 0.5F, -0.4F, -1.8F, -0.4F, 0.8F, 1.8F, 0.8F));
                    }
                }
            }
            case "weaver" -> {
                // A knitted cap with a folded brim and a pompom, a scarf with its end flying, a ball of silk with two needles in it.
                parts.add(box("cap_brim", Anchor.HEAD, 0, top + 0.2F, crown, 0.04F, 0, 0, -s.headW() / 2 - 0.2F, -0.9F, -s.headL() * 0.45F - 0.2F,
                        s.headW() + 0.4F, 0.9F, s.headL() * 0.9F + 0.4F));
                parts.add(box("cap", Anchor.HEAD, 0, top - 0.6F, crown, 0.04F, 0, 0, -s.headW() * 0.42F, -1.4F, -s.headL() * 0.38F, s.headW() * 0.84F, 1.4F,
                        s.headL() * 0.76F));
                parts.add(box("cap_pompom", Anchor.HEAD, 0, top - 1.9F, crown, 0, 0.5F, 0, -0.7F * k, -1.3F * k, -0.7F * k, 1.4F * k, 1.3F * k, 1.4F * k));
                parts.add(box("scarf", Anchor.BODY, 0, 0, -s.thoraxL() / 2 + 0.6F, 0, 0, 0, -s.thoraxW() / 2 - 0.55F, -s.thoraxH() / 2 - 0.55F, -0.9F,
                        s.thoraxW() + 1.1F, s.thoraxH() + 1.1F, 1.8F));
                parts.add(box("scarf_end", Anchor.BODY, 0.7F, -s.thoraxH() / 2 - 0.4F, -s.thoraxL() / 2 + 1.3F, 0.35F, -0.2F, 0, -0.8F, -0.7F, 0, 1.6F, 0.7F,
                        s.thoraxL() * 0.6F));
                float g = s.gasterW() / 6;
                float ballY = -s.gasterH() / 2 - 0.3F;
                float ballZ = s.gasterL() * 0.45F;
                parts.add(box("silk_ball", Anchor.GASTER, 0, ballY, ballZ, 0, 0.4F, 0, -1.4F * g, -2.8F * g, -1.4F * g, 2.8F * g, 2.8F * g, 2.8F * g));
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("needle", Anchor.GASTER, 0, ballY - 1.6F * g, ballZ, -0.35F, side * 0.55F, 0, -0.3F, -0.3F, -3.4F * g, 0.6F, 0.6F,
                            6.8F * g));
                }
            }
            case "wear_pirate" -> {
                parts.addAll(parts("pirate", s));
                // A patch over the right eye.
                parts.add(box("patch", Anchor.HEAD, s.headW() * 0.27F, -0.5F - s.headH() * 0.14F, -s.headL(), 0, 0, 0, -0.9F * k, -0.8F * k, -0.3F,
                        1.8F * k, 1.7F * k, 0.3F));
            }
            case "wear_explorer" -> {
                // A sun helmet: a wide flat brim, a domed crown with a button on top.
                parts.add(box("sun_brim", Anchor.HEAD, 0, top, crown, 0.05F, 0, 0, -s.headW() / 2 - 1.2F, -0.5F, -s.headL() / 2 - 1.0F,
                        s.headW() + 2.4F, 0.5F, s.headL() + 2.0F));
                parts.add(box("sun_crown", Anchor.HEAD, 0, top - 0.4F, crown, 0.05F, 0, 0, -s.headW() * 0.38F, -1.6F, -s.headL() * 0.38F,
                        s.headW() * 0.76F, 1.6F, s.headL() * 0.76F));
                parts.add(box("sun_top", Anchor.HEAD, 0, top - 1.9F, crown, 0.05F, 0, 0, -s.headW() * 0.24F, -0.8F, -s.headL() * 0.24F,
                        s.headW() * 0.48F, 0.8F, s.headL() * 0.48F));
            }
            case "wear_miner" -> {
                parts.add(box("hard_hat", Anchor.HEAD, 0, top + 0.1F, crown, 0.04F, 0, 0, -s.headW() * 0.46F, -1.7F, -s.headL() * 0.42F, s.headW() * 0.92F,
                        1.7F, s.headL() * 0.84F));
                parts.add(box("hard_peak", Anchor.HEAD, 0, top + 0.1F, crown - s.headL() * 0.42F, 0.12F, 0, 0, -s.headW() * 0.4F, -0.5F, -1.2F,
                        s.headW() * 0.8F, 0.5F, 1.2F));
                parts.add(box("hard_ridge", Anchor.HEAD, 0, top - 1.5F, crown, 0.04F, 0, 0, -0.6F, -0.7F, -s.headL() * 0.36F, 1.2F, 0.7F, s.headL() * 0.72F));
                parts.add(box("lamp", Anchor.HEAD, 0, top - 0.7F, crown - s.headL() * 0.42F, 0.04F, 0, 0, -0.9F, -0.9F, -0.9F, 1.8F, 1.8F, 0.9F));
            }
            case "wear_crown" -> {
                parts.add(box("crown_band", Anchor.HEAD, 0, top + 0.1F, crown, 0.06F, 0, 0, -s.headW() * 0.34F, -2.4F, -s.headL() * 0.32F, s.headW() * 0.68F,
                        2.4F, s.headL() * 0.64F));
                parts.add(box("crown_velvet", Anchor.HEAD, 0, top, crown, 0.06F, 0, 0, -s.headW() * 0.27F, -1.4F, -s.headL() * 0.25F, s.headW() * 0.54F,
                        1.4F, s.headL() * 0.5F));
                parts.add(box("jewel", Anchor.HEAD, 0, top - 0.9F, crown - s.headL() * 0.32F, 0.06F, 0, 0.785F, -0.5F, -0.5F, -0.35F, 1, 1, 0.5F));
            }
            case "wear_chef" -> parts.addAll(parts("baker", s));
            case "wear_party" -> parts.addAll(parts("party", s));
            case "wear_flower" -> {
                float ringW = s.headW() * 0.44F;
                float ringD = s.headL() * 0.42F;
                parts.add(box("vine", Anchor.HEAD, 0, top + 0.2F, crown, 0.05F, 0, 0, -ringW, -0.7F, -ringD, ringW * 2, 0.7F, ringD * 2));
                // Blossoms round the ring, each turned its own way.
                float[][] spots = {{0, -1}, {-1, -0.45F}, {1, -0.45F}, {-0.8F, 0.75F}, {0.8F, 0.75F}};
                for (int i = 0; i < spots.length; i++) {
                    parts.add(box("bloom" + i % 3, Anchor.HEAD, spots[i][0] * ringW, top - 0.3F, crown + spots[i][1] * ringD, 0, 0.4F + i * 0.7F, 0,
                            -0.8F * k, -0.9F, -0.8F * k, 1.6F * k, 0.9F, 1.6F * k));
                }
            }
            case "wear_backpack" -> {
                float w = s.thoraxW() * 1.05F;
                float back = -s.thoraxH() / 2;
                parts.add(box("pack", Anchor.BODY, 0, back, 0.4F, -0.08F, 0, 0, -w / 2, -3.2F, -2, w, 3.2F, 4));
                parts.add(box("flap", Anchor.BODY, 0, back - 3.1F, 0.4F, -0.08F, 0, 0, -w / 2 - 0.2F, -0.7F, -2.2F, w + 0.4F, 1.1F, 4.4F));
                parts.add(box("bedroll", Anchor.BODY, 0, back - 4.4F, 0.6F, -0.08F, 0, 0, -w / 2 - 0.9F, -0.8F, -0.8F, w + 1.8F, 1.6F, 1.6F));
                parts.add(box("pocket", Anchor.BODY, 0, back, 2.4F, -0.08F, 0, 0, -w * 0.3F, -2.2F, 0, w * 0.6F, 1.6F, 0.8F));
            }
            case "wear_helm" -> {
                // The plates' helm, worn unless a hat is: domed, and it leaves the face and the feelers free.
                float helmW = s.headW() + 0.8F;
                float helmFront = -s.headL() + 1.8F;
                float helmSplit = -s.headL() * 0.5F;
                parts.add(box("helm_neck", Anchor.HEAD, 0, top, 0, 0, 0, 0, -helmW / 2, -PLATE, helmSplit, helmW, s.headH() * 0.62F + PLATE,
                        -helmSplit + 0.2F));
                parts.add(box("helm_brow", Anchor.HEAD, 0, top, 0, 0, 0, 0, -helmW / 2 + 0.15F, -PLATE + 0.05F, helmFront, helmW - 0.3F,
                        s.headH() * 0.26F + PLATE, helmSplit - helmFront + 0.1F));
                parts.add(box("helm_dome", Anchor.HEAD, 0, top, 0, 0, 0, 0, -helmW * 0.28F, -PLATE - 0.55F, helmFront + 0.5F, helmW * 0.56F, 0.7F,
                        -helmFront - 0.7F));
            }
            case "wear_plates" -> {
                // A suit of overlapping chitin lames, like a pill bug's: one over the thorax, one over its hump, three down the
                // gaster. Spines and fins are painted for some tiers only.
                float back = -s.thoraxH() / 2;
                float lameW = s.thoraxW() + 0.9F;
                parts.add(box("lame_thorax", Anchor.BODY, 0, back, -s.thoraxL() / 2, 0, 0, 0, -lameW / 2, -PLATE, 0.6F, lameW,
                        s.thoraxH() * 0.5F + PLATE, s.thoraxL() - 4.0F));
                parts.add(box("lame_hump", Anchor.BODY, 0, back - 1, s.thoraxL() / 2 - 3.5F, 0, 0, 0, -lameW / 2 + 0.2F, -PLATE, -0.25F, lameW - 0.4F,
                        s.thoraxH() * 0.4F + 1 + PLATE, 3.5F));
                for (int i = 0; i < 3; i++) {
                    float w = s.gasterW() + 1.5F - i * 0.25F;
                    float from = i * s.gasterL() / 3 - (i == 0 ? 0.5F : 0.15F);
                    // Each lifts a little to the rear, over the next one's front.
                    parts.add(box("lame_gaster", Anchor.GASTER, 0, -s.gasterH() / 2 - 0.4F, from, 0.05F, 0, 0, -w / 2, -PLATE, 0, w,
                            s.gasterH() * (0.5F - i * 0.05F) + 0.4F + PLATE, s.gasterL() / 3 + (i == 2 ? 0.4F : 0.75F)));
                }
                for (int i = 0; i < 2; i++) {
                    parts.add(box("plate_spine", Anchor.BODY, 0, back - PLATE - i, -s.thoraxL() * 0.2F + i * s.thoraxL() * 0.44F, -0.4F, 0, 0,
                            -0.5F, -1.9F, -0.5F, 1, 1.9F, 1));
                }
                for (int side = -1; side <= 1; side += 2) {
                    // Two fins swept back from the hump, like wing cases half open.
                    parts.add(box("plate_fin", Anchor.BODY, side * s.thoraxW() * 0.36F, back - 1 - PLATE, s.thoraxL() / 2 - 2.4F, 0.18F, side * 0.2F,
                            side * 0.4F, -0.3F, -2.0F, 0, 0.6F, 2.0F, s.thoraxL() * 0.5F));
                }
            }
            case "wear_bindle" -> {
                float eyeY = -0.5F - s.headH() * 0.12F;
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(box("sad_eye", Anchor.HEAD, side * s.headW() * 0.26F, eyeY, -s.headL(), 0, 0, 0, -0.9F * k, -0.9F * k, -0.35F, 1.8F * k,
                            1.8F * k, 0.35F));
                    // Brows that rise to the middle.
                    parts.add(box("brow", Anchor.HEAD, side * s.headW() * 0.26F, eyeY - 1.3F * k, -s.headL(), 0, 0, side * 0.45F, -1.0F * k, -0.3F, -0.45F,
                            2.0F * k, 0.6F, 0.45F));
                }
                parts.add(box("tear", Anchor.HEAD, -s.headW() * 0.26F, eyeY + 0.9F * k, -s.headL(), 0, 0, 0, -0.35F, 0, -0.5F, 0.7F, 1.1F, 0.5F));
                // The stick over the right shoulder, and the spotted cloth knotted to its end.
                float reach = s.thoraxL() * 0.95F;
                float rodX = s.thoraxW() / 2 + 0.4F;
                float rodY = -s.thoraxH() / 2 + 0.2F;
                float tipY = rodY - (float) Math.sin(0.62) * reach;
                float tipZ = 0.5F + (float) Math.cos(0.62) * reach;
                parts.add(box("stick", Anchor.BODY, rodX, rodY, 0.5F, 0, 0.62F - (float) Math.PI / 2, -(float) Math.PI / 2, -3.2F, -0.35F, -0.35F,
                        reach + 3.8F, 0.7F, 0.7F));
                parts.add(box("knot", Anchor.BODY, rodX, tipY, tipZ, 0, 0.5F, 0, -0.8F, -0.6F, -0.8F, 1.6F, 1.1F, 1.6F));
                parts.add(box("cloth", Anchor.BODY, rodX, tipY + 0.3F, tipZ, 0, 0.5F, 0, -1.9F, 0, -1.9F, 3.8F, 3.4F, 3.8F));
            }
            case "wear_jumping" -> {
                float eyeY = -0.5F - s.headH() * 0.06F;
                for (int side = -1; side <= 1; side += 2) {
                    // Two big eyes in front, two small ones beside them, fluff over them and soft feelers under them.
                    parts.add(box("big_eye", Anchor.HEAD, side * s.headW() * 0.27F, eyeY, -s.headL(), 0, 0, 0, -1.0F * k, -1.2F * k, -0.7F, 2.0F * k,
                            2.2F * k, 0.8F));
                    parts.add(box("small_eye", Anchor.HEAD, side * s.headW() * 0.5F, eyeY - 0.7F * k, -s.headL() * 0.84F, 0, 0, 0, -0.55F * k, -0.6F * k,
                            -0.6F, 1.1F * k, 1.2F * k, 1.2F));
                    parts.add(box("tuft", Anchor.HEAD, side * s.headW() * 0.3F, top + 0.2F, -s.headL() * 0.62F, 0.25F, 0, side * 0.3F, -0.7F, -1.3F,
                            -0.5F, 1.4F, 1.3F, 1.0F));
                    parts.add(box("palp", Anchor.HEAD, side * 0.9F, s.headH() / 2 - 1.2F, -s.headL(), 0.5F, 0, 0, -0.6F, 0, -0.9F, 1.2F, 1.6F, 1.1F));
                }
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
        // Parts borrowed from another costume are placed afresh.
        parts.replaceAll(part -> part.grow() > 0 ? part : part.at(-1, -1));
        int[][] regions = costume.equals("wear_plates") || costume.equals("wear_helm") ? WHOLE : REGIONS;
        int region = 0;
        int x = regions[0][0];
        int y = regions[0][1];
        int shelf = 0;
        for (Part part : parts) {
            if (part.u() >= 0) {
                placed.add(part);
                continue;
            }
            int width = (int) Math.ceil(2 * (part.w() + part.d()));
            int height = (int) Math.ceil(part.d() + part.h());
            while (true) {
                int[] r = regions[region];
                if (x + width > r[2]) {
                    // Next shelf.
                    x = r[0];
                    y += shelf;
                    shelf = 0;
                }
                if (x + width <= r[2] && y + height <= r[3]) {
                    break;
                }
                if (++region >= regions.length) {
                    throw new IllegalStateException("The " + costume + " costume's parts do not fit the texture: " + part.name());
                }
                x = regions[region][0];
                y = regions[region][1];
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
