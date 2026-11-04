package com.huntlike.client.block;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

import com.huntlike.HuntLike;
import com.huntlike.block.SoulModelBlockEntity;
import com.huntlike.block.SoulMonolithBlock;
import com.huntlike.block.SoulNexusBlock;
import com.huntlike.registry.ModBlocks;

/**
 * Draws the user's Blockbench models of the Soul Nexus and the two Soul Monoliths. Each model's front (the flat side of
 * the Nexus' triangle, the Monolith's face) points north in the model and is turned to the block's FACING. On the
 * upgraded Monolith, one pebble hops and turns in a 6 second loop, as in the model's animation.
 */
public class SoulModelRenderer implements BlockEntityRenderer<SoulModelBlockEntity, SoulModelRenderer.State> {
	private static final Identifier NEXUS = HuntLike.id("textures/entity/soul_nexus.png");
	private static final Identifier MONOLITH = HuntLike.id("textures/entity/soul_monolith.png");
	private static final Identifier UPGRADED_MONOLITH = HuntLike.id("textures/entity/upgraded_soul_monolith.png");

	public enum Kind {
		NEXUS("soul_nexus"), MONOLITH("soul_monolith"), UPGRADED_MONOLITH("upgraded_soul_monolith");

		public static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::byName, kind -> kind.name);
		final String name;

		Kind(String name) {
			this.name = name;
		}

		static Kind byName(String name) {
			for (Kind kind : values()) {
				if (kind.name.equals(name)) {
					return kind;
				}
			}
			return NEXUS;
		}

		float[][] quads() {
			return this == NEXUS ? SoulNexusGeometry.QUADS : SoulMonolithGeometry.QUADS;
		}

		Identifier texture() {
			return switch (this) {
				case NEXUS -> SoulModelRenderer.NEXUS;
				case MONOLITH -> SoulModelRenderer.MONOLITH;
				case UPGRADED_MONOLITH -> SoulModelRenderer.UPGRADED_MONOLITH;
			};
		}
	}

	public static class State extends BlockEntityRenderState {
		Kind kind = Kind.NEXUS;
		Direction facing = Direction.NORTH;
		/** Seconds into the upgraded Monolith's loop, or -1 for no animation. */
		float time = -1.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SoulModelBlockEntity entity, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);
		BlockState block = entity.getBlockState();
		state.kind = block.getBlock() instanceof SoulNexusBlock ? Kind.NEXUS
				: block.getBlock() instanceof SoulMonolithBlock monolith && monolith.upgraded() ? Kind.UPGRADED_MONOLITH : Kind.MONOLITH;
		state.facing = block.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				? block.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
		if (state.kind == Kind.UPGRADED_MONOLITH && entity.getLevel() != null) {
			// Each Monolith starts its loop at a different moment, so a row of them doesn't move in step.
			long phase = Math.floorMod(entity.getBlockPos().asLong() * 0x9E3779B97F4A7C15L, 120L);
			float ticks = (entity.getLevel().getGameTime() + phase) % (long) (SoulMonolithGeometry.LENGTH * 20) + partialTick;
			state.time = ticks / 20.0F;
		} else {
			state.time = -1.0F;
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.5F, 0.0F, 0.5F);
		// The model's front points north; turn it to FACING.
		pose.rotateDegrees(Axis.YP, 180.0F - state.facing.toYRot());
		pose.translate(-0.5F, 0.0F, -0.5F);
		draw(pose, collector, state.kind, state.lightCoords, OverlayTexture.NO_OVERLAY, state.time);
		pose.popPose();
	}

	static void draw(PoseStack pose, SubmitNodeCollector collector, Kind kind, int light, int overlay, float time) {
		float[][] quads = kind.quads();
		var type = RenderTypes.entityCutout(kind.texture());
		collector.submitCustomGeometry(pose, type, (p, consumer) -> emit(p, consumer, quads, 0, light, overlay));
		if (kind == Kind.NEXUS) {
			return;
		}
		pose.pushPose();
		if (time >= 0.0F) {
			animatePebble(pose, time);
		}
		collector.submitCustomGeometry(pose, type, (p, consumer) -> emit(p, consumer, quads, 1, light, overlay));
		pose.popPose();
	}

	/** Blockbench's bone animation: position (x flipped) and rotation (x and y flipped, Z then Y then X) about the pivot. */
	private static void animatePebble(PoseStack pose, float time) {
		float[] position = sample(SoulMonolithGeometry.POSITION, time);
		float[] rotation = sample(SoulMonolithGeometry.ROTATION, time);
		pose.translate(-position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
		pose.translate(SoulMonolithGeometry.PIVOT_X, SoulMonolithGeometry.PIVOT_Y, SoulMonolithGeometry.PIVOT_Z);
		pose.rotateDegrees(Axis.ZP, rotation[2]);
		pose.rotateDegrees(Axis.YP, -rotation[1]);
		pose.rotateDegrees(Axis.XP, -rotation[0]);
		pose.translate(-SoulMonolithGeometry.PIVOT_X, -SoulMonolithGeometry.PIVOT_Y, -SoulMonolithGeometry.PIVOT_Z);
	}

	/** Linear keyframes: holds the first and last values outside them. */
	static float[] sample(float[][] keys, float time) {
		if (time <= keys[0][0]) {
			return new float[] {keys[0][1], keys[0][2], keys[0][3]};
		}
		for (int i = 1; i < keys.length; i++) {
			if (time <= keys[i][0]) {
				float[] a = keys[i - 1];
				float[] b = keys[i];
				float f = (time - a[0]) / (b[0] - a[0]);
				return new float[] {a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f};
			}
		}
		float[] last = keys[keys.length - 1];
		return new float[] {last[1], last[2], last[3]};
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer consumer, float[][] quads, int part, int light, int overlay) {
		for (float[] q : quads) {
			if ((int) q[23] != part) {
				continue;
			}
			for (int i = 0; i < 4; i++) {
				int o = i * 5;
				consumer.addVertex(pose, q[o], q[o + 1], q[o + 2]).setColor(-1).setUv(q[o + 3], q[o + 4]).setOverlay(overlay)
						.setLight(light).setNormal(pose, q[20], q[21], q[22]);
			}
		}
	}

	/** The items: the same models, standing still and facing the viewer. */
	public record Item(Kind kind) implements NoDataSpecialModelRenderer {
		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
			draw(pose, collector, kind, light, overlay, -1.0F);
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
			for (int i = 0; i < 8; i++) {
				output.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
			}
		}

		public record Unbaked(Kind kind) implements NoDataSpecialModelRenderer.Unbaked {
			public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
					.group(Kind.CODEC.fieldOf("kind").forGetter(Unbaked::kind)).apply(instance, Unbaked::new));

			@Override
			public MapCodec<Unbaked> type() {
				return MAP_CODEC;
			}

			@Override
			public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
				return new Item(kind);
			}
		}
	}

	public static void initialize() {
		BlockEntityRendererRegistry.register(ModBlocks.SOUL_MODEL_ENTITY, context -> new SoulModelRenderer());
		SpecialModelRenderers.ID_MAPPER.put(HuntLike.id("soul_model"), Item.Unbaked.MAP_CODEC);
	}
}
