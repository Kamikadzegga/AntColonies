package com.antcolonies.client.model;

import com.antcolonies.AntColonies;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
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
 * The worn Chitin Helmet: vanilla's helmet box, painted as an ant's head, with two elbowed antennae on top. Each antenna
 * rises from the front of the head and leans out, then bends forward at the elbow and ends in a club, like a real ant's.
 * The hat layer is left empty: its part of the armor texture (32,0 to 64,16) holds the antennae's skins instead (see
 * TexGen's {@code antHead}).
 */
public final class ChitinHelmetModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AntColonies.id("chitin_helmet"), "main");

    private static @Nullable HumanoidModel<HumanoidRenderState> model;

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
            // The rest of the antenna, from the elbow forward and a little up and out, with the club at its tip.
            scape.addOrReplaceChild(name + "_antenna_tip",
                    CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 6.0F)
                            .texOffs(50, 0).addBox(-1.0F, -1.0F, -7.5F, 2.0F, 2.0F, 2.0F),
                    PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, -0.75F, side * -0.3F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Bakes the model; called whenever entity models are (re)loaded. */
    public static void bake(EntityModelSet models) {
        model = new HumanoidModel<>(models.bakeLayer(LAYER));
    }

    /** Swaps in the antennae for the Chitin Helmet on adult humanoids and armor stands; babies keep the plain helmet. */
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
            if (model == null || layerType != EquipmentClientInfo.LayerType.HUMANOID || !(original instanceof HumanoidModel<?>)) {
                return original;
            }
            return model;
        }
    };
}
