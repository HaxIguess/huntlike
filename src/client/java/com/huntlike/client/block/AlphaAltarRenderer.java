package com.huntlike.client.block;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

import com.huntlike.HuntLike;
import com.huntlike.block.AlphaAltarBlockEntity;
import com.huntlike.registry.ModBlocks;

/**
 * Draws the Alpha Altar from the user's Blockbench model: its texture, and the end portal starfield on the side panels.
 * The center (top slab, leather band, rim and symbol holder) drops one pixel at the start of every second and rises
 * back over the rest of it, as in the model's animation. The same drawing is used for the item.
 */
public class AlphaAltarRenderer implements BlockEntityRenderer<AlphaAltarBlockEntity, AlphaAltarRenderer.State> {
	private static final Identifier TEXTURE = HuntLike.id("textures/entity/alpha_altar.png");
	/** The animation: one second; the drop takes 1/24 of it. */
	private static final float DROP = 1.0F / 24.0F;

	public static class State extends BlockEntityRenderState {
		float time;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AlphaAltarBlockEntity altar, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(altar, state, partialTick, cameraPos, crumbling);
		long gameTime = altar.getLevel() == null ? 0 : altar.getLevel().getGameTime();
		state.time = ((gameTime % 20) + partialTick) / 20.0F;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		draw(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, centerOffset(state.time));
	}

	/** The center's height offset (in blocks) at time t (0-1) of the one-second loop. */
	static float centerOffset(float t) {
		float px = t < DROP ? -t / DROP : -1.0F + (t - DROP) / (1.0F - DROP);
		return px / 16.0F;
	}

	static void draw(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, float centerOffset) {
		collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(TEXTURE),
				(p, consumer) -> emit(p, consumer, false, light, overlay, centerOffset));
		collector.submitCustomGeometry(pose, RenderTypes.endPortal(), (p, consumer) -> emit(p, consumer, true, light, overlay, centerOffset));
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer consumer, boolean portal, int light, int overlay, float centerOffset) {
		for (float[] q : AlphaAltarGeometry.QUADS) {
			int flags = (int) q[23];
			if (((flags & AlphaAltarGeometry.FLAG_PORTAL) != 0) != portal) {
				continue;
			}
			float dy = (flags & AlphaAltarGeometry.FLAG_CENTER) != 0 ? centerOffset : 0.0F;
			for (int i = 0; i < 4; i++) {
				int o = i * 5;
				if (portal) {
					consumer.addVertex(pose, q[o], q[o + 1] + dy, q[o + 2]);
				} else {
					consumer.addVertex(pose, q[o], q[o + 1] + dy, q[o + 2]).setColor(-1).setUv(q[o + 3], q[o + 4]).setOverlay(overlay)
							.setLight(light).setNormal(pose, q[20], q[21], q[22]);
				}
			}
		}
	}

	/** The item: the same model, standing still. */
	public static class Item implements NoDataSpecialModelRenderer {
		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
			draw(pose, collector, light, overlay, 0.0F);
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
				return new Item();
			}
		}
	}

	public static void initialize() {
		BlockEntityRendererRegistry.register(ModBlocks.ALPHA_ALTAR_ENTITY, context -> new AlphaAltarRenderer());
		SpecialModelRenderers.ID_MAPPER.put(HuntLike.id("alpha_altar"), Item.Unbaked.MAP_CODEC);
	}
}
