package com.antcolonies.client.model;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.species.Caste;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * An ant, built like a real one: head with elbowed antennae and mandibles, the thorax with six two-segment legs, the
 * petiole, and the gaster. Workers, soldiers (big head, long mandibles) and Queens (long thorax, huge gaster) each have
 * their own proportions. Every part is drawn grey and tinted per species, so a new species needs no new model.
 *
 * <p>Units: the worker is 24 units long, its legs stand it 5 units off the ground (y = 0, up is -y as in entity models),
 * and it faces -z. Renderers scale it to size. The body and the gaster are separate roots, drawn in their own colors;
 * the pattern and crystal parts follow them.
 */
public final class AntModel {
    public static final ModelLayerLocation WORKER = new ModelLayerLocation(AntColonies.id("ant"), "worker");
    public static final ModelLayerLocation SOLDIER = new ModelLayerLocation(AntColonies.id("ant"), "soldier");
    public static final ModelLayerLocation QUEEN = new ModelLayerLocation(AntColonies.id("ant"), "queen");
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 64;

    /** The sizes of one caste's parts (units), shared by the model and the texture generator's layout. */
    public record Shape(float thoraxW, float thoraxH, float thoraxL, float headW, float headH, float headL, float gasterW, float gasterH,
                        float gasterL, float mandibleL, float femur, float tibia, float legSpread, float bodyHeight, float gasterTilt,
                        float legThickness) {
        public static Shape of(Caste caste) {
            return switch (caste) {
                case WORKER -> new Shape(3.4F, 3.8F, 7.5F, 5.2F, 4.8F, 5.4F, 6, 5.6F, 8.5F, 2.6F, 6, 7, 2.2F, 5, -0.12F, 1.0F);
                case SOLDIER -> new Shape(4.2F, 4.2F, 8, 8, 6.4F, 7.2F, 6.4F, 5.8F, 8, 5, 6.5F, 7.5F, 2.4F, 5.5F, -0.1F, 1.3F);
                case QUEEN -> new Shape(5.2F, 5.2F, 10, 6, 5.4F, 6, 8, 7.4F, 13, 3, 7, 8, 3, 6, -0.18F, 1.2F);
            };
        }
    }

    public final ModelPart root;
    public final ModelPart body;
    public final ModelPart head;
    public final ModelPart gaster;
    public final ModelPart pattern;
    public final ModelPart crystalsBody;
    public final ModelPart crystalsGaster;
    /** Liquid Ants: the swollen, see-through crop that takes the gaster's place, and the gaster's plates stretched over it. */
    public final ModelPart crop;
    public final ModelPart cropSkin;
    public final ModelPart cropPlates;
    public final ModelPart[] femurs = new ModelPart[6];
    public final ModelPart[] tibias = new ModelPart[6];
    public final ModelPart[] antennae = new ModelPart[2];
    public final ModelPart[] mandibles = new ModelPart[2];
    private final PartPose[] femurRest = new PartPose[6];
    private final PartPose[] tibiaRest = new PartPose[6];
    private final PartPose bodyRest;
    private final PartPose gasterRest;
    private final PartPose cropRest;
    private final PartPose headRest;
    private final PartPose[] antennaRest = new PartPose[2];
    private final PartPose[] mandibleRest = new PartPose[2];
    public final Caste caste;

    public AntModel(ModelPart root, Caste caste) {
        this.root = root;
        this.caste = caste;
        body = root.getChild("body");
        head = body.getChild("head");
        gaster = root.getChild("gaster");
        pattern = root.getChild("pattern");
        crystalsBody = root.getChild("crystals_body");
        crystalsGaster = root.getChild("crystals_gaster");
        crop = root.getChild("crop");
        cropSkin = root.getChild("crop_skin");
        cropPlates = root.getChild("crop_plates");
        for (int i = 0; i < 6; i++) {
            femurs[i] = body.getChild("femur" + i);
            tibias[i] = femurs[i].getChild("tibia");
            femurRest[i] = femurs[i].storePose();
            tibiaRest[i] = tibias[i].storePose();
        }
        for (int i = 0; i < 2; i++) {
            antennae[i] = head.getChild("antenna" + i);
            mandibles[i] = head.getChild("mandible" + i);
            antennaRest[i] = antennae[i].storePose();
            mandibleRest[i] = mandibles[i].storePose();
        }
        bodyRest = body.storePose();
        gasterRest = gaster.storePose();
        cropRest = crop.storePose();
        headRest = head.storePose();
    }

    public static LayerDefinition create(Caste caste) {
        Shape s = Shape.of(caste);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float halfL = s.thoraxL() / 2;
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-s.thoraxW() / 2, -s.thoraxH() / 2, -halfL, s.thoraxW(), s.thoraxH(), s.thoraxL())
                        // The petiole, the narrow waist between thorax and gaster.
                        .texOffs(76, 0).addBox(-1, -1.5F, halfL, 2, 2.5F, 2.5F),
                PartPose.offset(0, -s.bodyHeight(), 0));
        // A hump on the thorax (the propodeum), smaller than the thorax itself.
        body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(76, 6)
                .addBox(-s.thoraxW() / 2 + 0.5F, -s.thoraxH() / 2 - 1, halfL - 3.5F, s.thoraxW() - 1, 1.2F, 3), PartPose.ZERO);
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-s.headW() / 2, -s.headH() / 2 - 0.5F, -s.headL(), s.headW(), s.headH(), s.headL()),
                PartPose.offsetAndRotation(0, -0.8F, -halfL + 0.3F, 0.18F, 0, 0));
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1 : 1;
            head.addOrReplaceChild("mandible" + i, CubeListBuilder.create().texOffs(96, 0)
                            .addBox(-0.75F, -0.6F, -s.mandibleL(), 1.5F, 1.2F, s.mandibleL()),
                    PartPose.offsetAndRotation(side * s.headW() * 0.28F, s.headH() / 2 - 1.2F, -s.headL() + 0.4F, 0.1F, side * 0.38F, 0));
            PartDefinition scape = head.addOrReplaceChild("antenna" + i, CubeListBuilder.create().texOffs(112, 0)
                            .addBox(-0.5F, -0.5F, -5, 1, 1, 5),
                    PartPose.offsetAndRotation(side * s.headW() * 0.3F, -s.headH() / 2 + 0.3F, -s.headL() + 1.2F, -0.55F, -side * 0.5F, 0));
            scape.addOrReplaceChild("funiculus", CubeListBuilder.create().texOffs(112, 7).addBox(-0.45F, -0.45F, -5.5F, 0.9F, 0.9F, 5.5F)
                            // The club at the tip.
                            .texOffs(96, 8).addBox(-0.65F, -0.65F, -6.6F, 1.3F, 1.3F, 1.6F),
                    PartPose.offsetAndRotation(0, 0, -4.8F, 1.15F, 0, 0));
        }
        float[] legZ = {-halfL * 0.62F, 0, halfL * 0.62F};
        float[] legYaw = {0.62F, 0.05F, -0.62F};
        for (int i = 0; i < 6; i++) {
            int pair = i / 2;
            float side = i % 2 == 0 ? -1 : 1;
            float t = s.legThickness();
            CubeListBuilder femur = CubeListBuilder.create().texOffs(72, 20);
            CubeListBuilder tibia = CubeListBuilder.create().texOffs(72, 24);
            if (side > 0) {
                femur.addBox(0, -t / 2, -t / 2, s.femur(), t, t);
                tibia.addBox(0, -t * 0.4F, -t * 0.4F, s.tibia(), t * 0.8F, t * 0.8F);
            } else {
                femur.addBox(-s.femur(), -t / 2, -t / 2, s.femur(), t, t);
                tibia.addBox(-s.tibia(), -t * 0.4F, -t * 0.4F, s.tibia(), t * 0.8F, t * 0.8F);
            }
            // Femurs rise from the body (z rotation), tibias come down to the ground; front legs reach forward, hind legs back.
            PartDefinition leg = body.addOrReplaceChild("femur" + i, femur, PartPose.offsetAndRotation(side * s.legSpread() * 0.85F,
                    s.thoraxH() / 2 - 1, legZ[pair], 0, side * legYaw[pair], -side * 0.55F));
            leg.addOrReplaceChild("tibia", tibia, PartPose.offsetAndRotation(side * s.femur(), 0, 0, 0, 0, side * 1.75F));
        }
        // The gaster hangs from the petiole; drawn on its own in the gaster color.
        PartPose gasterPose = PartPose.offsetAndRotation(0, -s.bodyHeight() - 0.6F, halfL + 2.2F, s.gasterTilt(), 0, 0);
        root.addOrReplaceChild("gaster", CubeListBuilder.create().texOffs(0, 20)
                        .addBox(-s.gasterW() / 2, -s.gasterH() / 2, 0, s.gasterW(), s.gasterH(), s.gasterL())
                        // The tip, narrower.
                        .texOffs(52, 20).addBox(-s.gasterW() / 2 + 1.2F, -s.gasterH() / 2 + 1.2F, s.gasterL(), s.gasterW() - 2.4F, s.gasterH() - 2.4F, 1.5F)
                        // The front, narrower, where it joins the petiole.
                        .texOffs(52, 28).addBox(-s.gasterW() / 2 + 1.2F, -s.gasterH() / 2 + 1.2F, -1.2F, s.gasterW() - 2.4F, s.gasterH() - 2.4F, 1.2F)
                        // A second, crossed box rounds the gaster off (an octagon seen from the front).
                        .texOffs(64, 36).addBox(-s.gasterW() / 2 + 0.8F, -s.gasterH() / 2 - 0.4F, 1, s.gasterW() - 1.6F, s.gasterH() + 0.8F, s.gasterL() - 2)
                        .texOffs(64, 36).addBox(-s.gasterW() / 2 - 0.4F, -s.gasterH() / 2 + 0.8F, 1, s.gasterW() + 0.8F, s.gasterH() - 1.6F, s.gasterL() - 2),
                gasterPose);
        root.addOrReplaceChild("pattern", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-s.gasterW() / 2, -s.gasterH() / 2, 0, s.gasterW(), s.gasterH(), s.gasterL(), new CubeDeformation(0.12F)), gasterPose);
        // Crystal growths of mineral species, on the thorax and along the gaster.
        PartDefinition crystalsBody = root.addOrReplaceChild("crystals_body", CubeListBuilder.create(), PartPose.offset(0, -s.bodyHeight(), 0));
        crystalsBody.addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 48).addBox(-1, -3.5F, -1, 2, 3.5F, 2),
                PartPose.offsetAndRotation(0, -s.thoraxH() / 2 + 0.3F, -1, 0.2F, 0.7F, 0.15F));
        crystalsBody.addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.8F, -2.6F, -0.8F, 1.6F, 2.6F, 1.6F),
                PartPose.offsetAndRotation(0.6F, -s.thoraxH() / 2 + 0.3F, 2, -0.3F, 0.2F, 0.35F));
        PartDefinition crystalsGaster = root.addOrReplaceChild("crystals_gaster", CubeListBuilder.create(), gasterPose);
        crystalsGaster.addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 48).addBox(-1.2F, -4.2F, -1.2F, 2.4F, 4.2F, 2.4F),
                PartPose.offsetAndRotation(0, -s.gasterH() / 2 + 0.4F, s.gasterL() * 0.35F, -0.25F, 0.8F, 0));
        crystalsGaster.addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.9F, -3.2F, -0.9F, 1.8F, 3.2F, 1.8F),
                PartPose.offsetAndRotation(-1.4F, -s.gasterH() / 2 + 0.6F, s.gasterL() * 0.62F, -0.5F, 0.3F, -0.4F));
        crystalsGaster.addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 48).addBox(-0.8F, -2.8F, -0.8F, 1.6F, 2.8F, 1.6F),
                PartPose.offsetAndRotation(1.5F, -s.gasterH() / 2 + 0.7F, s.gasterL() * 0.7F, -0.6F, -0.4F, 0.45F));
        // Liquid Ants (a look with a crop colour): a replete's abdomen. One size, always full: it is part of the species' shape
        // like a soldier's big head, not a gauge. The liquid is a rounded body (four boxes crossed, so it has no square
        // corner anywhere); around it, a little bigger, the thin skin of the crop, drawn see-through; and over the top the
        // gaster's plates, pulled apart into four narrow bands as the crop swelled under them.
        float cw = cropWidth(caste);
        float ch = cropHeight(caste);
        float cl = cropLength(caste);
        PartPose cropPose = PartPose.offsetAndRotation(0, -s.bodyHeight() - 0.6F - (ch - s.gasterH()) * 0.3F, halfL + 2.2F, s.gasterTilt(), 0, 0);
        root.addOrReplaceChild("crop", cropBoxes(cw, ch, cl, CubeDeformation.NONE), cropPose);
        root.addOrReplaceChild("crop_skin", cropBoxes(cw, ch, cl, new CubeDeformation(CROP_SKIN)), cropPose);
        CubeListBuilder plates = CubeListBuilder.create();
        float c = CROP_CHAMFER;
        for (int i = 0; i < CROP_PLATES; i++) {
            float z = c + (cl - 2 * c) * (0.05F + 0.25F * i);
            // Narrower towards both ends, like the crop under them.
            float w = (cw - 2 * c) * (i == 0 || i == CROP_PLATES - 1 ? 0.62F : 1.0F);
            plates.texOffs(0, 20).addBox(-w / 2, -ch / 2 - CROP_SKIN - 0.55F, z, w, 0.7F, (cl - 2 * c) * 0.14F);
        }
        root.addOrReplaceChild("crop_plates", plates, cropPose);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** The dark plates on top of a Liquid Ant's crop. */
    public static final int CROP_PLATES = 4;
    /** How much of each edge of the crop's bounding box is cut away to round it, in units. */
    public static final float CROP_CHAMFER = 1.5F;
    /** How far the crop's skin stands off the liquid inside it. */
    public static final float CROP_SKIN = 0.45F;

    /**
     * The crop's rounded body, {@code w} wide, {@code h} tall and {@code l} long: one box each at full width, full height
     * and full length (the other two sizes cut back by the chamfer), and one between them that fills their corners. Each
     * has its own quarter of the crop texture (the texture generator lays the same four boxes out).
     */
    private static CubeListBuilder cropBoxes(float w, float h, float l, CubeDeformation grow) {
        float c = CROP_CHAMFER;
        return CubeListBuilder.create()
                .texOffs(0, 0).addBox(-w / 2, -h / 2 + c, c, w, h - 2 * c, l - 2 * c, grow)
                .texOffs(64, 0).addBox(-w / 2 + c, -h / 2, c, w - 2 * c, h, l - 2 * c, grow)
                .texOffs(0, 32).addBox(-w / 2 + c, -h / 2 + c, 0, w - 2 * c, h - 2 * c, l, grow)
                .texOffs(64, 32).addBox(-w / 2 + c / 2, -h / 2 + c / 2, c / 2, w - c, h - c, l - c, grow);
    }

    /**
     * A Liquid Ant's crop, in units: much fatter and taller than the caste's gaster and a good deal longer (a Queen's is
     * already big, so hers swells less). The texture generator lays the crop's skin out from the same numbers.
     */
    public static float cropWidth(Caste caste) {
        return Shape.of(caste).gasterW() * (caste == Caste.QUEEN ? 1.2F : 1.5F);
    }

    public static float cropHeight(Caste caste) {
        return Shape.of(caste).gasterH() * (caste == Caste.QUEEN ? 1.2F : 1.5F);
    }

    public static float cropLength(Caste caste) {
        return Shape.of(caste).gasterL() * (caste == Caste.QUEEN ? 1.1F : 1.25F);
    }

    /** Puts every part back at rest, then poses the ant. */
    public void pose(AntPose pose) {
        body.loadPose(bodyRest);
        gaster.loadPose(gasterRest);
        head.loadPose(headRest);
        float walk = pose.walkAmount;
        float phase = pose.walkPhase;
        // Tripod gait: front and hind legs of one side move with the middle leg of the other.
        for (int i = 0; i < 6; i++) {
            femurs[i].loadPose(femurRest[i]);
            tibias[i].loadPose(tibiaRest[i]);
            int pair = i / 2;
            float side = i % 2 == 0 ? -1 : 1;
            boolean groupA = (pair % 2 == 0) == (side < 0);
            float p = phase + (groupA ? 0 : Mth.PI);
            float swing = Mth.sin(p) * 0.45F * walk;
            float lift = Math.max(0, Mth.cos(p)) * 0.35F * walk;
            femurs[i].yRot += swing * side;
            femurs[i].zRot += -side * lift;
            tibias[i].zRot += side * lift * 0.6F;
        }
        // Grooming: the front legs come up to the antennae.
        if (pose.groom > 0) {
            for (int i = 0; i < 2; i++) {
                float side = i % 2 == 0 ? -1 : 1;
                femurs[i].yRot += side * 0.7F * pose.groom;
                femurs[i].zRot += -side * 0.9F * pose.groom;
                tibias[i].zRot += -side * 0.8F * pose.groom;
            }
        }
        float bob = Mth.sin(phase * 2) * 0.25F * walk;
        body.y += bob;
        gaster.y += bob;
        // The body rears up when alarmed (soldiers), the head nods while eating or tending.
        body.xRot -= pose.rear * 0.35F;
        gaster.xRot += pose.rear * 0.25F + Mth.sin(pose.time * 0.07F) * 0.02F;
        head.xRot += pose.headPitch;
        head.yRot += pose.headYaw;
        if (pose.gasterPulse > 0) {
            float pulse = Mth.sin(pose.time * 0.25F) * 0.04F * pose.gasterPulse;
            gaster.xScale = 1 + pulse;
            gaster.yScale = 1 + pulse;
        } else {
            gaster.xScale = 1;
            gaster.yScale = 1;
        }
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1 : 1;
            antennae[i].loadPose(antennaRest[i]);
            mandibles[i].loadPose(mandibleRest[i]);
            float wave = Mth.sin(pose.time * (0.21F + i * 0.04F) + i * 1.7F + pose.seed);
            antennae[i].xRot += wave * 0.18F * (1 + pose.alert);
            antennae[i].yRot += -side * Mth.cos(pose.time * 0.17F + i + pose.seed) * 0.15F;
            mandibles[i].yRot += side * pose.mandibleOpen * -0.35F + side * Mth.sin(pose.time * 0.3F) * 0.03F;
        }
        pattern.loadPose(gaster.storePose());
        pattern.xScale = gaster.xScale;
        pattern.yScale = gaster.yScale;
        pattern.zScale = gaster.zScale;
        crystalsBody.loadPose(body.storePose());
        crystalsBody.xRot = body.xRot;
        crystalsGaster.loadPose(gaster.storePose());
        // The crop keeps its rest pose and size whatever the ant does: it only bobs and tilts with the body.
        crop.loadPose(cropRest);
        crop.y += gaster.y - gasterRest.y();
        crop.xRot += gaster.xRot - gasterRest.xRot();
        cropSkin.loadPose(crop.storePose());
        cropPlates.loadPose(crop.storePose());
    }

    /** Where the carried thing sits: between the mandibles (units, ant space). */
    public float[] mouth() {
        Shape s = Shape.of(caste);
        return new float[] {0, -s.bodyHeight() + 0.8F, -s.thoraxL() / 2 - s.headL() - 1.4F};
    }

    /** How long the ant is, in units (for scaling it to a size). */
    public static float length(Caste caste) {
        Shape s = Shape.of(caste);
        return s.headL() + s.thoraxL() + 2.5F + s.gasterL() + 1.5F;
    }
}
