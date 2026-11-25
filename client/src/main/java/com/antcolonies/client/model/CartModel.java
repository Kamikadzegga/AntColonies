package com.antcolonies.client.model;

import com.antcolonies.AntColonies;
import com.antcolonies.client.companion.CompanionLook;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** A Cart Ant's cart: its hitch is the origin, the ground is y = 0 (up is -y) and the cart lies towards +z. Ant units. */
public final class CartModel extends Model<CompanionLook.Cart> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AntColonies.id("ant_cart"), "main");
    /** Half a wheel's height; its eight sides are four bars laid across each other. */
    private static final float WHEEL = 2.8F;
    private static final float WHEEL_BAR = 2.3F;
    private static final int WHEEL_BARS = 4;
    private static final float AXLE_Z = 8.5F;
    /** How high over the ground the cart's floor hangs. */
    private static final float HITCH = 3.5F;

    private final ModelPart cart;
    private final ModelPart bed;
    private final ModelPart[] wheels = new ModelPart[2];

    public CartModel(ModelPart root) {
        super(root, texture -> RenderTypes.entityCutout(texture));
        cart = root.getChild("cart");
        bed = cart.getChild("bed");
        wheels[0] = cart.getChild("wheel0");
        wheels[1] = cart.getChild("wheel1");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition cart = mesh.getRoot().addOrReplaceChild("cart", CubeListBuilder.create(), PartPose.offset(0, -HITCH, 0));
        PartDefinition bed = cart.addOrReplaceChild("bed", CubeListBuilder.create()
                        // The floor, then front, back and the two sides.
                        .texOffs(0, 0).addBox(-4, 0, 4, 8, 1, 9)
                        .texOffs(0, 10).addBox(-4, -3, 4, 8, 3, 1)
                        .texOffs(0, 10).addBox(-4, -3, 12, 8, 3, 1)
                        .texOffs(34, 0).addBox(-4, -3, 5, 1, 3, 7)
                        .texOffs(34, 0).addBox(3, -3, 5, 1, 3, 7)
                        // Corner posts stand a little over the boards.
                        .texOffs(24, 10).addBox(-4.2F, -3.8F, 3.8F, 1.2F, 5, 1.2F)
                        .texOffs(24, 10).addBox(3.0F, -3.8F, 3.8F, 1.2F, 5, 1.2F)
                        .texOffs(24, 10).addBox(-4.2F, -3.8F, 12.0F, 1.2F, 5, 1.2F)
                        .texOffs(24, 10).addBox(3.0F, -3.8F, 12.0F, 1.2F, 5, 1.2F)
                        // The axle.
                        .texOffs(0, 14).addBox(-5, 0.6F, AXLE_Z - 0.5F, 10, 1, 1),
                PartPose.ZERO);
        // Two shafts from the bed to the ant, and the yoke that joins them.
        bed.addOrReplaceChild("shafts", CubeListBuilder.create()
                .texOffs(34, 10).addBox(-2.6F, 0.2F, -1, 0.8F, 0.8F, 5.5F)
                .texOffs(34, 10).addBox(1.8F, 0.2F, -1, 0.8F, 0.8F, 5.5F)
                .texOffs(0, 16).addBox(-2.8F, 0, -1.6F, 5.6F, 1.2F, 1), PartPose.ZERO);
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? -5.6F : 4.6F;
            PartDefinition wheel = cart.addOrReplaceChild("wheel" + i, CubeListBuilder.create()
                            .texOffs(24, 18).addBox(x - 0.2F, -0.7F, -0.7F, 1.4F, 1.4F, 1.4F),
                    PartPose.offset(0, HITCH - WHEEL, AXLE_Z));
            for (int bar = 0; bar < WHEEL_BARS; bar++) {
                // A hair wider each, so no two lie in one plane.
                float wide = bar * 0.02F;
                wheel.addOrReplaceChild("bar" + bar, CubeListBuilder.create().texOffs(0, 20)
                                .addBox(x - wide / 2, -WHEEL, -WHEEL_BAR / 2, 1 + wide, WHEEL * 2, WHEEL_BAR),
                        PartPose.rotation((float) Math.PI * bar / WHEEL_BARS, 0, 0));
            }
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(CompanionLook.Cart state) {
        resetPose();
        // It swings behind the ant as the ant turns, and noses up or down as the ant climbs or leaps.
        cart.yRot = state.yaw();
        cart.xRot = state.pitch();
        bed.y += state.rattle();
        wheels[0].xRot = state.roll();
        wheels[1].xRot = state.roll();
    }
}
