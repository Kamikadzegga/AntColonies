package com.antcolonies.client.model;

import com.antcolonies.AntColonies;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import org.jspecify.annotations.Nullable;

/**
 * The worn chitin armor's extra parts, all skinned from the armor texture's spare corners (the hat layer is left empty;
 * see TexGen's {@code antennaSkins}):
 * <ul>
 * <li>Every Chitin Helmet: vanilla's helmet box painted as an ant's head, two elbowed antennae on top (each rises from
 * the front of the head, leans out, bends forward at the elbow and ends in a club) and a pair of jaws under the face,
 * curving in towards each other, a tooth at each tip.</li>
 * <li>Hardened and Infernal chestplates: two thorn-like spines on the upper back, pointing back and up and a little
 * apart, like a spiny ant's.</li>
 * <li>The Void chestplate: a Queen's two wings, folded down the back past the waist.</li>
 * </ul>
 * Babies keep the plain shapes.
 */
public final class ChitinHelmetModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AntColonies.id("chitin_helmet"), "main");
    public static final ModelLayerLocation SPINES = new ModelLayerLocation(AntColonies.id("chitin_chestplate_spines"), "main");
    public static final ModelLayerLocation WINGS = new ModelLayerLocation(AntColonies.id("chitin_chestplate_wings"), "main");

    private static @Nullable HumanoidModel<HumanoidRenderState> helmet;
    private static @Nullable HumanoidModel<HumanoidRenderState> spines;
    private static @Nullable HumanoidModel<HumanoidRenderState> wings;

    private ChitinHelmetModel() {}

    public static LayerDefinition create() {
        MeshDefinition mesh = HumanoidModel.createArmorMeshSet(LayerDefinitions.INNER_ARMOR_DEFORMATION, LayerDefinitions.OUTER_ARMOR_DEFORMATION).head();
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "right" : "left";
            // The scape: from the top of the helmet (y -9 with the armor's 1-pixel swell), near the front, up and outward.
            PartDefinition scape = head.addOrReplaceChild(name + "_antenna",
                    CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
                    PartPose.offsetAndRotation(side * 2.0F, -9.0F, -3.5F, 0.35F, 0.0F, side * 0.35F));
            // The rest of the antenna, from the elbow forward and a little up and out, with the club at its tip (a 2-pixel
            // box drawn 0.3 smaller on every side, so it is a little thicker than the antenna, not a block).
            scape.addOrReplaceChild(name + "_antenna_tip",
                    CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 6.0F)
                            .texOffs(50, 0).addBox(-1.0F, -1.0F, -7.2F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.3F)),
                    PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, -0.75F, side * -0.3F, 0.0F));
            // A jaw: from under the face, forward, a little down and in towards the other; a tooth on its inner side at the tip.
            head.addOrReplaceChild(name + "_jaw",
                    CubeListBuilder.create().texOffs(32, 8).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F)
                            .texOffs(54, 8).addBox(side < 0 ? 0.5F : -1.5F, -0.5F, -4.0F, 1.0F, 1.0F, 1.0F),
                    PartPose.offsetAndRotation(side * 2.5F, -0.5F, -4.8F, 0.15F, side * 0.5F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The chestplate with two spines on the upper back. */
    public static LayerDefinition createSpines() {
        MeshDefinition mesh = HumanoidModel.createArmorMeshSet(LayerDefinitions.INNER_ARMOR_DEFORMATION, LayerDefinitions.OUTER_ARMOR_DEFORMATION).chest();
        PartDefinition body = mesh.getRoot().getChild("body");
        for (int side : new int[] {-1, 1}) {
            body.addOrReplaceChild((side < 0 ? "right" : "left") + "_spine",
                    CubeListBuilder.create().texOffs(44, 8).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F),
                    PartPose.offsetAndRotation(side * 2.0F, 3.0F, 2.6F, 0.55F, side * 0.4F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The chestplate with a Queen's folded wings down the back. */
    public static LayerDefinition createWings() {
        MeshDefinition mesh = HumanoidModel.createArmorMeshSet(LayerDefinitions.INNER_ARMOR_DEFORMATION, LayerDefinitions.OUTER_ARMOR_DEFORMATION).chest();
        PartDefinition body = mesh.getRoot().getChild("body");
        for (int side : new int[] {-1, 1}) {
            // A flat wing 4 wide and 13 long, from the shoulders down past the waist, its tip a little out and back.
            body.addOrReplaceChild((side < 0 ? "right" : "left") + "_wing",
                    CubeListBuilder.create().texOffs(56, 16).addBox(side < 0 ? -4.0F : 0.0F, 0.0F, 0.0F, 4.0F, 13.0F, 0.0F),
                    PartPose.offsetAndRotation(side * 0.5F, 0.5F, 3.1F, 0.2F, 0.0F, -side * 0.2F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Bakes the models; called whenever entity models are (re)loaded. */
    public static void bake(EntityModelSet models) {
        helmet = new HumanoidModel<>(models.bakeLayer(LAYER));
        spines = new HumanoidModel<>(models.bakeLayer(SPINES));
        wings = new HumanoidModel<>(models.bakeLayer(WINGS));
    }

    private static IClientItemExtensions extensions(java.util.function.Supplier<@Nullable HumanoidModel<HumanoidRenderState>> model) {
        return new IClientItemExtensions() {
            @Override
            public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
                HumanoidModel<HumanoidRenderState> replacement = model.get();
                if (replacement == null || layerType != EquipmentClientInfo.LayerType.HUMANOID || !(original instanceof HumanoidModel<?>)) {
                    return original;
                }
                return replacement;
            }
        };
    }

    /** Every chitin helmet on adult humanoids and armor stands: the antennae and jaws. */
    public static final IClientItemExtensions EXTENSIONS = extensions(() -> helmet);
    /** Hardened and Infernal chestplates: the spines. */
    public static final IClientItemExtensions SPINE_EXTENSIONS = extensions(() -> spines);
    /** The Void chestplate: the wings. */
    public static final IClientItemExtensions WING_EXTENSIONS = extensions(() -> wings);
}
