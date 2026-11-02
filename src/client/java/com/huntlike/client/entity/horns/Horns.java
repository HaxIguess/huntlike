package com.huntlike.client.entity.horns;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.huntlike.HuntLike;

/**
 * Custom horns (vanilla models have none this big): each horn is a chain of square segments, each growing up (model -y)
 * from the end of the previous one and turned by its own rotation. The left horn mirrors the right one and shares its
 * texture. Drawn as a layer that follows the parent model's head, with the boss's own skin.
 */
public final class Horns {
	private Horns() {
	}

	/** One segment: width, length, texture spot, rotation (x, y, z radians) from the previous segment. */
	public record Segment(float width, float length, int u, int v, float xRot, float yRot, float zRot) {
	}

	/** A pair of horns: the right horn's base (relative to the head's pivot) and its segments; the sheet size. */
	public record Spec(String name, float baseX, float baseY, float baseZ, List<Segment> segments, int texWidth, int texHeight) {
		ModelLayerLocation layer() {
			return new ModelLayerLocation(HuntLike.id(name + "_horns"), "main");
		}

		LayerDefinition create() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// A horn at the middle (baseX 0) is a single horn; otherwise a mirrored pair.
			for (int side = 0; side < (baseX == 0.0F ? 1 : 2); side++) {
				boolean left = side == 1;
				float k = left ? -1.0F : 1.0F;
				PartDefinition parent = root;
				for (int i = 0; i < segments.size(); i++) {
					Segment s = segments.get(i);
					CubeListBuilder cube = CubeListBuilder.create().texOffs(s.u(), s.v()).mirror(left)
							.addBox(-s.width() / 2.0F, -s.length(), -s.width() / 2.0F, s.width(), s.length(), s.width());
					PartPose pose = i == 0
							? PartPose.offsetAndRotation(baseX * k, baseY, baseZ, s.xRot(), s.yRot() * k, s.zRot() * k)
							: PartPose.offsetAndRotation(0.0F, -segments.get(i - 1).length(), 0.0F, s.xRot(), s.yRot() * k, s.zRot() * k);
					parent = parent.addOrReplaceChild((left ? "left" : "right") + "_horn_" + i, cube, pose);
				}
			}
			return LayerDefinition.create(mesh, texWidth, texHeight);
		}
	}

	/** Aurochs: out to the side, then up and forward (cow head, 64x64 sheet). */
	public static final Spec AUROCHS = new Spec("aurochs", -4.0F, -2.5F, -2.5F, List.of(
			new Segment(3, 4, 0, 40, 0.0F, 0.0F, -1.25F),
			new Segment(2, 3, 12, 40, 0.45F, 0.0F, 0.55F),
			new Segment(2, 3, 20, 40, 0.55F, 0.0F, 0.45F),
			new Segment(1, 2, 28, 40, 0.5F, 0.0F, 0.2F)), 64, 64);

	/** Ovis Ammon (argali): a curl back, down beside the head and forward (sheep head, 64x32 sheet). */
	public static final Spec OVIS_AMMON = new Spec("ovis_ammon", -4.5F, -3.5F, -3.0F, List.of(
			new Segment(3, 4, 28, 0, -0.9F, 0.0F, -0.1F),
			new Segment(3, 3, 40, 0, -1.1F, 0.0F, 0.0F),
			new Segment(2, 3, 52, 0, -1.1F, 0.0F, 0.0F),
			new Segment(2, 3, 56, 8, -1.1F, 0.0F, -0.1F),
			new Segment(1, 2, 56, 13, -0.9F, 0.0F, -0.25F)), 64, 32);

	public static void registerLayers() {
		for (Spec spec : List.of(AUROCHS, OVIS_AMMON)) {
			ModelLayerRegistry.registerModelLayer(spec.layer(), spec::create);
		}
	}

	static final class HornModel extends Model<Unit> {
		HornModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
		}
	}

	/** Draws the horns on the parent model's head, with the given skin. */
	public static final class Layer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		private final HornModel model;
		private final Identifier texture;

		public Layer(RenderLayerParent<S, M> parent, ModelPart horns, Identifier texture) {
			super(parent);
			this.model = new HornModel(horns);
			this.texture = texture;
		}

		public static ModelPart bake(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, Spec spec) {
			return context.bakeLayer(spec.layer());
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			pose.pushPose();
			ModelPart head = this.getParentModel().root().getChild("head");
			head.translateAndRotate(pose);
			collector.submitModel(this.model, Unit.INSTANCE, pose, this.model.renderType(this.texture), light,
					LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
			pose.popPose();
		}
	}
}
