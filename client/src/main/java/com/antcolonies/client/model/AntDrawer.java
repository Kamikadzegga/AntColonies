package com.antcolonies.client.model;

import java.util.EnumMap;
import java.util.Map;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.AntLook;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Pattern;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/**
 * Draws ants for every renderer (wild ants, Terrariums, working chambers): the grey model tinted with the species'
 * colors, its gaster pattern over it, its crystals (glowing for species that glow) and whatever it carries.
 */
public final class AntDrawer {
    private static final Map<Caste, Identifier> BASE = new EnumMap<>(Caste.class);
    private static final Map<Caste, Map<Pattern, Identifier>> PATTERNS = new EnumMap<>(Caste.class);
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
        }
    }

    private final Map<Caste, AntModel> models = new EnumMap<>(Caste.class);
    private final net.minecraft.client.model.geom.ModelPart carried;

    public AntDrawer(EntityModelSet models) {
        this.models.put(Caste.WORKER, new AntModel(models.bakeLayer(AntModel.WORKER), Caste.WORKER));
        this.models.put(Caste.SOLDIER, new AntModel(models.bakeLayer(AntModel.SOLDIER), Caste.SOLDIER));
        this.models.put(Caste.QUEEN, new AntModel(models.bakeLayer(AntModel.QUEEN), Caste.QUEEN));
        this.carried = models.bakeLayer(CarryModel.LAYER);
    }

    public AntModel model(Caste caste) {
        return models.get(caste);
    }

    /**
     * Draws one ant at the pose stack's origin, standing on y = 0 and facing -z, {@code length} blocks long.
     *
     * @param carry ARGB of what it carries between its mandibles (0 for nothing)
     */
    public void draw(SubmitNodeCollector collector, PoseStack poseStack, Caste caste, AntLook look, AntPose pose, float length, int light,
                     int overlay, int carry, int outline) {
        AntModel model = models.get(caste);
        model.pose(pose);
        // Model parts are 1/16 block per unit: the whole ant (its length in units) becomes `length` blocks.
        float k = 16.0F * length * look.size() / AntModel.length(caste);
        poseStack.pushPose();
        // Model space is y-down like entity models: flip x and y (keeps the faces' winding).
        poseStack.scale(-k, -k, k);
        RenderType base = RenderTypes.entityCutout(BASE.get(caste));
        collector.submitModelPart(model.body, poseStack, base, light, overlay, null, ARGB.opaque(look.body()), outline);
        collector.submitModelPart(model.gaster, poseStack, base, light, overlay, null, ARGB.opaque(look.gaster()), outline);
        Identifier pattern = PATTERNS.get(caste).get(look.pattern());
        if (pattern != null) {
            collector.submitModelPart(model.pattern, poseStack, RenderTypes.entityTranslucent(pattern), light, overlay, null, ARGB.opaque(look.accent()));
        }
        if (look.crystal().isPresent()) {
            int crystal = ARGB.color(220, look.crystal().get());
            int crystalLight = look.glow() ? LightCoordsUtil.FULL_BRIGHT : light;
            collector.submitModelPart(model.crystalsBody, poseStack, CRYSTAL_TYPE, crystalLight, overlay, null, crystal);
            collector.submitModelPart(model.crystalsGaster, poseStack, CRYSTAL_TYPE, crystalLight, overlay, null, crystal);
            if (look.glow()) {
                collector.submitModelPart(model.crystalsBody, poseStack, CRYSTAL_GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                        ARGB.color(160, look.crystal().get()));
                collector.submitModelPart(model.crystalsGaster, poseStack, CRYSTAL_GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                        ARGB.color(160, look.crystal().get()));
            }
        }
        if (carry != 0) {
            float[] mouth = model.mouth();
            poseStack.pushPose();
            poseStack.translate(mouth[0] / 16.0F, mouth[1] / 16.0F, mouth[2] / 16.0F);
            collector.submitModelPart(carried, poseStack, CARRY_TYPE, light, overlay, null, carry);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** How long a caste is drawn compared to a worker. */
    public static float casteScale(Caste caste) {
        return switch (caste) {
            case WORKER -> 1.0F;
            case SOLDIER -> 1.25F;
            case QUEEN -> 1.75F;
        };
    }
}
