package com.huntlike.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.huntlike.HuntLike;

/**
 * The Demonling's horns: the Imp's two horns (a base leaning 45 degrees forward, then a straight tip) at twice their
 * size, on the front of the blaze's head. Their 16x16 sheet is the Imp's horn pixels, doubled.
 */
public final class DemonlingHorns {
	private DemonlingHorns() {
	}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HuntLike.id("demonling_horns"), "main");

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int i = 0; i < 2; i++) {
			float x = i == 0 ? -2.5F : 2.5F;
			// The base grows from the head's front top edge, tipped 45 degrees forward.
			root.addOrReplaceChild("base_" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
					PartPose.offsetAndRotation(x, -3.3F, -3.6F, (float) Math.toRadians(45.0), 0.0F, 0.0F));
			// The tip stands straight up from the end of the base.
			root.addOrReplaceChild("tip_" + i, CubeListBuilder.create().texOffs(0, 8).addBox(x - 1.0F, -9.4F, -7.1F, 2.0F, 4.0F, 2.0F),
					PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 16, 16);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, DemonlingHorns::create);
	}
}
