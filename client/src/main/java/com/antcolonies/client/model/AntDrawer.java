package com.antcolonies.client.model;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.antcolonies.AntColonies;
import com.antcolonies.client.companion.CompanionLook;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Costume;
import com.antcolonies.colony.species.Pattern;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.Nullable;

/** Draws ants for every renderer: the grey model tinted per species, with its pattern, crystals and load. */
public final class AntDrawer {
    private static final Map<Caste, Identifier> BASE = new EnumMap<>(Caste.class);
    private static final Map<Caste, Map<Pattern, Identifier>> PATTERNS = new EnumMap<>(Caste.class);
    /** A Liquid Ant's crop, per caste: the liquid in it (solid, shaded pale to deep), tinted with the liquid's colour. */
    private static final Map<Caste, RenderType> CROPS = new EnumMap<>(Caste.class);
    private static final Map<Caste, RenderType> CROP_SKINS = new EnumMap<>(Caste.class);
    /**
     * A costume's texture per caste, in the ant skin's layout: paint over the ant's own boxes, and the costume's parts in
     * the corners no box uses. The paint is drawn a hair nearer than the skin under it.
     */
    private static final Map<Caste, Map<Costume, RenderType>> COSTUME_PAINT = new EnumMap<>(Caste.class);
    private static final Map<Caste, Map<Costume, RenderType>> COSTUME_PARTS = new EnumMap<>(Caste.class);
    private static final Identifier CRYSTAL = AntColonies.id("textures/entity/ant/crystal.png");
    private static final RenderType CRYSTAL_TYPE = RenderTypes.entityTranslucent(CRYSTAL);
    private static final RenderType CRYSTAL_GLOW = RenderTypes.eyes(CRYSTAL);
    private static final Identifier CARRY = AntColonies.id("textures/entity/ant/carry.png");
    private static final RenderType CARRY_TYPE = RenderTypes.entityCutout(CARRY);

    static {
        for (Caste caste : Caste.values()) {
            BASE.put(caste, AntColonies.id("textures/entity/ant/" + caste.getSerializedName() + ".png"));
            Map<Pattern, Identifier> patterns = new EnumMap<>(Pattern.class);
            for (Pattern pattern : Pattern.values()) {
                if (pattern != Pattern.PLAIN) {
                    patterns.put(pattern, AntColonies.id("textures/entity/ant/" + caste.getSerializedName() + "_" + pattern.getSerializedName() + ".png"));
                }
            }
            PATTERNS.put(caste, patterns);
            Map<Costume, RenderType> paint = new EnumMap<>(Costume.class);
            Map<Costume, RenderType> parts = new EnumMap<>(Costume.class);
            for (Costume costume : Costume.values()) {
                Identifier texture = AntColonies.id("textures/entity/ant/costume/" + costume.getSerializedName() + "_" + caste.getSerializedName() + ".png");
                paint.put(costume, RenderTypes.entityCutoutZOffset(texture));
                parts.put(costume, RenderTypes.entityCutout(texture));
            }
            COSTUME_PAINT.put(caste, paint);
            COSTUME_PARTS.put(caste, parts);
            CROPS.put(caste, RenderTypes.entityCutout(AntColonies.id("textures/entity/ant/crop_" + caste.getSerializedName() + ".png")));
            CROP_SKINS.put(caste, RenderTypes.entityTranslucent(AntColonies.id("textures/entity/ant/crop_skin_" + caste.getSerializedName() + ".png")));
        }
    }

    /** Wear textures, made as they are first needed: paint over the ant and the wear's own parts. */
    private static final Map<String, RenderType> WEAR_PAINT = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> WEAR_PARTS = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> MARKS = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> MARKS_GLOW = new ConcurrentHashMap<>();
    private static final RenderType CART_TYPE = RenderTypes.entityCutout(AntColonies.id("textures/entity/ant/cart.png"));
    /** From this marks step on a veteran's shell marks shine. */
    private static final int GLOWING_MARKS = 4;

    private final Map<Caste, AntModel> models = new EnumMap<>(Caste.class);
    private final ModelPart carried;
    private final CartModel cart;

    public AntDrawer(EntityModelSet models) {
        this.models.put(Caste.WORKER, new AntModel(models.bakeLayer(AntModel.WORKER), Caste.WORKER));
        this.models.put(Caste.SOLDIER, new AntModel(models.bakeLayer(AntModel.SOLDIER), Caste.SOLDIER));
        this.models.put(Caste.QUEEN, new AntModel(models.bakeLayer(AntModel.QUEEN), Caste.QUEEN));
        this.carried = models.bakeLayer(CarryModel.LAYER);
        this.cart = new CartModel(models.bakeLayer(CartModel.LAYER));
    }

    public AntModel model(Caste caste) {
        return models.get(caste);
    }

    private static RenderType wearType(Map<String, RenderType> types, String texture, boolean paint) {
        return types.computeIfAbsent(texture, name -> {
            Identifier id = AntColonies.id("textures/entity/ant/" + name + ".png");
            return paint ? RenderTypes.entityCutoutZOffset(id) : RenderTypes.entityCutout(id);
        });
    }

    /** The texture of a wear's own parts for one caste. */
    public static RenderType wearParts(String texture, Caste caste) {
        return wearType(WEAR_PARTS, "wear/" + texture + "_" + caste.getSerializedName(), false);
    }

    /** An ant that wears nothing of its own. */
    public void draw(SubmitNodeCollector collector, PoseStack poseStack, Caste caste, AntLook look, AntPose pose, float length, int light,
                     int overlay, int carry, int outline) {
        draw(collector, poseStack, caste, look, pose, length, light, overlay, carry, outline, null);
    }

    /** Draws a cart with its hitch at the pose stack's origin, in units of 1/16 block, y down. */
    public void cart(SubmitNodeCollector collector, PoseStack poseStack, CompanionLook.Cart state, int light, int overlay, int outline) {
        collector.submitModel(cart, state, poseStack, CART_TYPE, light, overlay, -1, null, outline, null);
    }

    /**
     * Draws one ant at the pose stack's origin, standing on y = 0 and facing -z, {@code length} blocks long.
     *
     * @param carry ARGB of what it carries between its mandibles (0 for nothing)
     */
    public void draw(SubmitNodeCollector collector, PoseStack poseStack, Caste caste, AntLook look, AntPose pose, float length, int light,
                     int overlay, int carry, int outline, @Nullable CompanionLook worn) {
        AntModel model = models.get(caste);
        // Parts are drawn later and their joints read only then: each goes in as a model that re-poses this ant (Posed).
        // Posing here as well gives the mouth's place for what it carries.
        AntPose frozen = pose.copy();
        frozen.costume = look.costume().orElse(null);
        if (worn != null) {
            frozen.wear = worn.wears();
        }
        model.pose(frozen);
        // Model parts are 1/16 block per unit: the whole ant (its length in units) becomes `length` blocks.
        float k = 16.0F * length * look.size() / AntModel.length(caste);
        poseStack.pushPose();
        // Model space is y-down like entity models: flip x and y (keeps the faces' winding).
        poseStack.scale(-k, -k, k);
        RenderType base = RenderTypes.entityCutout(BASE.get(caste));
        submit(collector, frozen, model, model.body, poseStack, base, light, overlay, null, ARGB.opaque(look.body()), outline);
        boolean replete = look.crop().isPresent();
        if (replete) {
            // A Liquid Ant: the full crop in place of the gaster (the liquid, the gaster's plates on top, the clear skin).
            int liquid = ARGB.opaque(look.crop().get());
            submit(collector, frozen, model, model.crop, poseStack, CROPS.get(caste), look.glow() ? LightCoordsUtil.FULL_BRIGHT : light, overlay, null,
                    liquid, outline);
            submit(collector, frozen, model, model.cropPlates, poseStack, base, light, overlay, null, ARGB.opaque(look.gaster()), outline);
            // The skin takes a paler shade of the liquid's colour.
            submit(collector, frozen, model, model.cropSkin, poseStack, CROP_SKINS.get(caste), light, overlay, null,
                    ARGB.color(255, paler(ARGB.red(liquid)), paler(ARGB.green(liquid)), paler(ARGB.blue(liquid))));
        } else {
            submit(collector, frozen, model, model.gaster, poseStack, base, light, overlay, null, ARGB.opaque(look.gaster()), outline);
        }
        Identifier pattern = replete ? null : PATTERNS.get(caste).get(look.pattern());
        if (pattern != null) {
            submit(collector, frozen, model, model.pattern, poseStack, RenderTypes.entityTranslucent(pattern), light, overlay, null, ARGB.opaque(look.accent()));
        }
        if (look.crystal().isPresent()) {
            int crystal = ARGB.color(220, look.crystal().get());
            int crystalLight = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            submit(collector, frozen, model, model.crystalsBody, poseStack, CRYSTAL_TYPE, crystalLight, overlay, null, crystal);
            if (!replete) {
                submit(collector, frozen, model, model.crystalsGaster, poseStack, CRYSTAL_TYPE, crystalLight, overlay, null, crystal);
            }
            if (look.glow()) {
                submit(collector, frozen, model, model.crystalsBody, poseStack, CRYSTAL_GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                        ARGB.color(160, look.crystal().get()));
                if (!replete) {
                    submit(collector, frozen, model, model.crystalsGaster, poseStack, CRYSTAL_GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                            ARGB.color(160, look.crystal().get()));
                }
            }
        }
        if (look.costume().isPresent()) {
            Costume costume = look.costume().get();
            RenderType paint = COSTUME_PAINT.get(caste).get(costume);
            RenderType parts = COSTUME_PARTS.get(caste).get(costume);
            submit(collector, frozen, model, model.body, poseStack, paint, light, overlay, null, -1);
            // One hat at a time: a companion's hat takes the place of its species' own.
            if (worn == null || !worn.hat() || !costume.hat()) {
                submit(collector, frozen, model, model.costume(costume), poseStack, parts, light, overlay, null, -1, outline);
            }
            if (!replete) {
                submit(collector, frozen, model, model.gaster, poseStack, paint, light, overlay, null, -1);
                submit(collector, frozen, model, model.costumeGaster(costume), poseStack, parts, light, overlay, null, -1, outline);
            }
        }
        if (worn != null) {
            boolean ownHat = look.costume().map(Costume::hat).orElse(false);
            drawWorn(collector, poseStack, model, frozen, worn, replete, ownHat, light, overlay, outline);
        }
        if (carry != 0) {
            float[] mouth = model.mouth();
            poseStack.pushPose();
            poseStack.translate(mouth[0] / 16.0F, mouth[1] / 16.0F, mouth[2] / 16.0F);
            collector.submitModelPart(carried, poseStack, CARRY_TYPE, light, overlay, null, carry, null);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** A companion's level marks, transformation, plates, costume and cart, from the skin outwards. */
    private void drawWorn(SubmitNodeCollector collector, PoseStack poseStack, AntModel model, AntPose pose, CompanionLook worn, boolean replete,
                          boolean ownHat, int light, int overlay, int outline) {
        Caste caste = model.caste;
        if (worn.marks() > 0) {
            String texture = "marks/" + worn.marks() + "_" + caste.getSerializedName();
            RenderType marks = wearType(MARKS, texture, true);
            submit(collector, pose, model, model.body, poseStack, marks, light, overlay, null, -1);
            if (!replete) {
                submit(collector, pose, model, model.gaster, poseStack, marks, light, overlay, null, -1);
            }
            if (worn.marks() >= GLOWING_MARKS) {
                RenderType glow = MARKS_GLOW.computeIfAbsent(texture, name -> RenderTypes.eyes(AntColonies.id("textures/entity/ant/" + name + ".png")));
                int tint = ARGB.color(255, 110, 100, 70);
                submit(collector, pose, model, model.body, poseStack, glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, tint);
                if (!replete) {
                    submit(collector, pose, model, model.gaster, poseStack, glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, tint);
                }
            }
        }
        int order = 1;
        for (CompanionLook.Wear wear : worn.layers()) {
            if (ownHat && wear.parts().equals(CostumeParts.HELM)) {
                continue;
            }
            String texture = "wear/" + wear.texture() + "_" + caste.getSerializedName();
            RenderType paint = wearType(WEAR_PAINT, texture, true);
            RenderType parts = wearType(WEAR_PARTS, texture, false);
            var layer = collector.order(order++);
            if (CostumeParts.PAINTS_BODY.contains(wear.parts())) {
                submitOn(layer, pose, model, model.body, poseStack, paint, light, overlay, outline);
            }
            submitOn(layer, pose, model, model.wear(wear.parts()), poseStack, parts, light, overlay, outline);
            if (!replete && CostumeParts.PAINTS_GASTER.contains(wear.parts())) {
                submitOn(layer, pose, model, model.gaster, poseStack, paint, light, overlay, outline);
            }
            if (!replete && model.wearsOnGaster(wear.parts())) {
                submitOn(layer, pose, model, model.wearGaster(wear.parts()), poseStack, parts, light, overlay, outline);
            }
        }
        if (worn.cart() != null) {
            poseStack.pushPose();
            poseStack.translate(0, 0, AntModel.rear(caste, replete) / 16.0F);
            cart(collector, poseStack, worn.cart(), light, overlay, outline);
            poseStack.popPose();
        }
    }

    private void submitOn(OrderedSubmitNodeCollector collector, AntPose pose, AntModel model, ModelPart part, PoseStack poseStack, RenderType type,
                          int light, int overlay, int outline) {
        collector.submitModel(posedParts.computeIfAbsent(part, p -> new Posed(model, p)), pose, poseStack, type, light, overlay, -1, null, outline, null);
    }

    private void submit(SubmitNodeCollector collector, AntPose pose, AntModel model, ModelPart part, PoseStack poseStack,
                        RenderType type, int light, int overlay, Object uv, int tint) {
        submit(collector, pose, model, part, poseStack, type, light, overlay, uv, tint, 0);
    }

    private void submit(SubmitNodeCollector collector, AntPose pose, AntModel model, ModelPart part, PoseStack poseStack,
                        RenderType type, int light, int overlay, Object uv, int tint, int outline) {
        Posed posed = posedParts.computeIfAbsent(part, p -> new Posed(model, p));
        collector.submitModel(posed, pose, poseStack, type, light, overlay, tint, null, outline, null);
    }

    /** Each ant model part wrapped as a model of its own, which poses the ant right before it is drawn. */
    private final Map<ModelPart, Posed> posedParts = new IdentityHashMap<>();

    private static final class Posed extends Model<AntPose> {
        private final AntModel ant;

        Posed(AntModel ant, ModelPart part) {
            super(part, texture -> RenderTypes.entityCutout(texture));
            this.ant = ant;
        }

        @Override
        public void setupAnim(AntPose pose) {
            ant.pose(pose);
        }
    }

    private static int paler(int channel) {
        return channel + (255 - channel) / 3;
    }

    public static float casteScale(Caste caste) {
        return switch (caste) {
            case WORKER -> 1.0F;
            case SOLDIER -> 1.25F;
            case QUEEN -> 1.75F;
        };
    }
}
