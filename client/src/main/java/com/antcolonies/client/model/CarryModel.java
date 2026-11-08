package com.antcolonies.client.model;

import com.antcolonies.AntColonies;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** What an ant carries in its mandibles: a crumb, a leaf piece, an egg or a grain, tinted. Same units as the ant. */
public final class CarryModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AntColonies.id("ant_carry"), "main");

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("load", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2, -2, -3.5F, 4, 3.5F, 3.5F)
                        .texOffs(0, 8).addBox(-1.4F, -2.8F, -3, 2.8F, 1, 2.6F),
                PartPose.rotation(0.2F, 0.3F, 0.1F));
        return LayerDefinition.create(mesh, 32, 16);
    }

    private CarryModel() {}
}
