package com.huntlike.client.block;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

import com.huntlike.HuntLike;

/** Canavar's Chalice while it fills (holds some blood but isn't full): the blood sloshes, from the user's model. */
public record ChaliceRenderer() implements NoDataSpecialModelRenderer {
	private static final Identifier TEXTURE = HuntLike.id("textures/block/canavar_chalice_blood.png");

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		var type = RenderTypes.entityCutout(TEXTURE);
		float[][] quads = ChaliceGeometry.QUADS;
		collector.submitCustomGeometry(pose, type, (p, consumer) -> SoulModelRenderer.emitPart(p, consumer, quads, 0, light, overlay));
		float time = (net.minecraft.util.Util.getMillis() % (long) (ChaliceGeometry.LENGTH * 1000)) / 1000.0F;
		float[] position = SoulModelRenderer.sample(ChaliceGeometry.POSITION, time);
		float[] rotation = SoulModelRenderer.sample(ChaliceGeometry.ROTATION, time);
		pose.pushPose();
		pose.translate(-position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
		pose.translate(ChaliceGeometry.PIVOT_X, ChaliceGeometry.PIVOT_Y, ChaliceGeometry.PIVOT_Z);
		pose.rotateDegrees(Axis.ZP, rotation[2]);
		pose.rotateDegrees(Axis.YP, -rotation[1]);
		pose.rotateDegrees(Axis.XP, -rotation[0]);
		pose.translate(-ChaliceGeometry.PIVOT_X, -ChaliceGeometry.PIVOT_Y, -ChaliceGeometry.PIVOT_Z);
		collector.submitCustomGeometry(pose, type, (p, consumer) -> SoulModelRenderer.emitPart(p, consumer, quads, 1, light, overlay));
		pose.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		for (int i = 0; i < 8; i++) {
			output.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
		}
	}

	public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
			return new ChaliceRenderer();
		}
	}

	public static void initialize() {
		SpecialModelRenderers.ID_MAPPER.put(HuntLike.id("chalice_filling"), Unbaked.MAP_CODEC);
	}
}
