package com.huntlike.client.entity;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.huntlike.HuntLike;
import com.huntlike.entity.animal.Sylviornis;

/**
 * The Tier I boss from the user's Blockbench model (sylviornis_color.bbmodel, 64x64), with its 2 second walk cycle (legs,
 * wings, tail, head, body) played as it walks and faded out as it stops. Generated from the .bbmodel; on trial (the
 * chicken look is SylviornisChickenRenderer).
 */
public class SylviornisRenderer extends MobRenderer<Sylviornis, LivingEntityRenderState, SylviornisRenderer.Model> {
	private static final Identifier TEXTURE = HuntLike.id("textures/entity/animal/sylviornis_model.png");
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HuntLike.id("sylviornis"), "main");
	/** Walk keyframes per bone: time (s), x, y, z (degrees, as Blockbench shows them). */
	static final Map<String, float[][]> WALK = new HashMap<>();
	static final float WALK_LENGTH = 2.0F;
	/** Where each animated bone sits in the model. */
	static final Map<String, String[]> PATHS = new HashMap<>();

	static {
		PATHS.put("tail", new String[] {"body", "tail"});
		PATHS.put("left", new String[] {"body", "legs", "left"});
		PATHS.put("right", new String[] {"body", "legs", "right"});
		PATHS.put("head", new String[] {"body", "head"});
		PATHS.put("neck", new String[] {"body", "head", "neck"});
		PATHS.put("left2", new String[] {"body", "main", "wings", "left2"});
		PATHS.put("right2", new String[] {"body", "main", "wings", "right2"});
		PATHS.put("body2", new String[] {"body", "main", "body2"});
		WALK.put("tail", new float[][] {{0F, 0F, 0F, 0F}, {0.29167F, 0F, 7.5F, 0F}, {0.5F, 0F, 10F, 0F}, {0.70833F, 0F, 7.5F, 0F}, {1F, 0F, 0F, 0F}, {1.33333F, 0F, -7.5F, 0F}, {1.5F, 0F, -10F, 0F}, {1.66667F, 0F, -7.5F, 0F}, {2F, 0F, 0F, 0F}});
		WALK.put("left", new float[][] {{0F, 25F, 0F, 0F}, {0.25F, 0F, 0F, 0F}, {0.5F, 25F, 0F, 0F}, {0.75F, 0F, 0F, 0F}, {1F, 25F, 0F, 0F}, {1.25F, 0F, 0F, 0F}, {1.5F, 25F, 0F, 0F}, {1.75F, 0F, 0F, 0F}, {2F, 25F, 0F, 0F}});
		WALK.put("right", new float[][] {{0F, 0F, 0F, 0F}, {0.25F, 25F, 0F, 0F}, {0.5F, 0F, 0F, 0F}, {0.75F, 25F, 0F, 0F}, {1F, 0F, 0F, 0F}, {1.25F, 25F, 0F, 0F}, {1.5F, 0F, 0F, 0F}, {1.75F, 25F, 0F, 0F}, {2F, 0F, 0F, 0F}});
		WALK.put("head", new float[][] {{0F, 0F, 0F, 0F}, {1F, 10F, 0F, 0F}, {2F, 0F, 0F, 0F}});
		WALK.put("neck", new float[][] {{1F, 0F, 0F, 0F}});
		WALK.put("left2", new float[][] {{0F, 0F, 0F, 0F}, {0.25F, 0F, 0F, 0F}, {0.375F, 0F, 0F, -25F}, {0.5F, 0F, 0F, -30F}, {2F, 0F, 0F, 0F}});
		WALK.put("right2", new float[][] {{0F, 0F, 0F, 0F}, {0.25F, 0F, 0F, 0F}, {0.375F, 0F, 0F, 25F}, {0.5F, 0F, 0F, 30F}, {2F, 0F, 0F, 0F}});
		WALK.put("body2", new float[][] {{0F, 0F, 0F, 0F}, {1F, 2.5F, 0F, 0F}, {2F, 0F, 0F, 0F}});
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition b_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 21F, 0F));
		PartDefinition b_main = b_body.addOrReplaceChild("main", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
		PartDefinition b_body2 = b_main.addOrReplaceChild("body2", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
		b_body2.addOrReplaceChild("e_front_dome", CubeListBuilder.create().texOffs(24, 21).addBox(-3F, -3F, -8F, 4F, 2F, 5F, new CubeDeformation(-0.0002F)),
				PartPose.offsetAndRotation(1F, -4.17277F, 2.84461F, 0.2618F, 0F, 0F));
		b_body2.addOrReplaceChild("e_back_dome", CubeListBuilder.create().texOffs(0, 25).addBox(-3F, -3F, -8F, 4F, 2F, 5F, new CubeDeformation(-0.0003F)),
				PartPose.offsetAndRotation(1F, -1.32577F, 6.12132F, -0.2618F, 0F, 0F));
		b_body2.addOrReplaceChild("e_main_structure", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -8F, 4F, 3F, 10F, new CubeDeformation(-0.00005F)),
				PartPose.offsetAndRotation(1F, -2F, 3F, 0F, 0F, 0F));
		PartDefinition b_wings = b_main.addOrReplaceChild("wings", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
		PartDefinition b_left2 = b_wings.addOrReplaceChild("left2", CubeListBuilder.create(), PartPose.offset(-2F, -4.9F, -0.35F));
		b_left2.addOrReplaceChild("e_left_main", CubeListBuilder.create().texOffs(42, 21).addBox(0F, -2F, -2F, 1F, 2F, 4F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(-1F, 1.9F, 0.35F, 0.17453F, 0F, 0F));
		b_left2.addOrReplaceChild("e_left_higher", CubeListBuilder.create().texOffs(0, 39).addBox(0F, -2F, -2F, 1F, 1F, 5F, new CubeDeformation(-0.1F)),
				PartPose.offsetAndRotation(-1F, 2.9F, 0.35F, -0.17453F, 0F, 0F));
		b_left2.addOrReplaceChild("e_left_center", CubeListBuilder.create().texOffs(34, 28).addBox(0F, -2F, -2F, 1F, 1F, 6F, new CubeDeformation(-0.09F)),
				PartPose.offsetAndRotation(-1F, 3.297F, 0.31527F, -0.17453F, 0F, 0F));
		b_left2.addOrReplaceChild("e_left_lower", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -1F, -2F, 1F, 1F, 4F, new CubeDeformation(-0.1F)),
				PartPose.offsetAndRotation(-1F, 2.59459F, 0.35F, -0.17453F, 0F, 0F));
		PartDefinition b_right2 = b_wings.addOrReplaceChild("right2", CubeListBuilder.create(), PartPose.offset(2F, -4.9F, -0.35F));
		b_right2.addOrReplaceChild("e_right_higher", CubeListBuilder.create().texOffs(30, 41).addBox(-1F, -2F, -2F, 1F, 1F, 5F, new CubeDeformation(-0.1F)),
				PartPose.offsetAndRotation(1F, 2.9F, 0.35F, -0.17453F, 0F, 0F));
		b_right2.addOrReplaceChild("e_right_center", CubeListBuilder.create().texOffs(16, 35).addBox(-1F, -2F, -2F, 1F, 1F, 6F, new CubeDeformation(-0.09F)),
				PartPose.offsetAndRotation(1F, 3.297F, 0.31527F, -0.17453F, 0F, 0F));
		b_right2.addOrReplaceChild("e_right_lower", CubeListBuilder.create().texOffs(44, 5).addBox(-1F, -1F, -2F, 1F, 1F, 4F, new CubeDeformation(-0.1F)),
				PartPose.offsetAndRotation(1F, 2.59459F, 0.35F, -0.17453F, 0F, 0F));
		b_right2.addOrReplaceChild("e_right_main", CubeListBuilder.create().texOffs(42, 21).mirror().addBox(-1F, -2F, -2F, 1F, 2F, 4F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(1F, 1.9F, 0.35F, 0.17453F, 0F, 0F));
		PartDefinition b_tail = b_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0F, -4.0275F, 5F));
		b_tail.addOrReplaceChild("e_right", CubeListBuilder.create().texOffs(18, 28).addBox(-2F, -2F, -10F, 2F, 1F, 6F, new CubeDeformation(-0.0006F)),
				PartPose.offsetAndRotation(5.43198F, 3.9606F, 6.41933F, -0.2618F, 0.5236F, 0F));
		b_tail.addOrReplaceChild("e_center", CubeListBuilder.create().texOffs(28, 0).addBox(-2F, -2F, -10F, 2F, 1F, 6F, new CubeDeformation(-0.0006F)),
				PartPose.offsetAndRotation(1F, 3.9606F, 8.67836F, -0.2618F, 0F, 0F));
		b_tail.addOrReplaceChild("e_left", CubeListBuilder.create().texOffs(18, 28).mirror().addBox(0F, -2F, -10F, 2F, 1F, 6F, new CubeDeformation(-0.0006F)),
				PartPose.offsetAndRotation(-5.43198F, 3.9606F, 6.41933F, -0.2618F, -0.5236F, 0F));
		PartDefinition b_legs = b_body.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0F, 0F, -1F));
		b_legs.addOrReplaceChild("e_right_drum", CubeListBuilder.create().texOffs(44, 35).addBox(1F, -2F, 0F, 2F, 3F, 2F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(-0.65562F, -0.0216F, 0.83004F, 1.13446F, -0.17453F, 0F));
		b_legs.addOrReplaceChild("e_left_drum", CubeListBuilder.create().texOffs(10, 46).addBox(-3F, -2F, 0F, 2F, 3F, 2F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(0.65562F, -0.0216F, 0.83004F, 1.13446F, 0.17453F, 0F));
		b_legs.addOrReplaceChild("e_belly_back", CubeListBuilder.create().texOffs(28, 7).addBox(-1F, -2F, -2F, 4F, 2F, 4F, new CubeDeformation(-0.0003F)),
				PartPose.offsetAndRotation(-1F, -1.48236F, 4.06815F, 0.2618F, 0F, 0F));
		b_legs.addOrReplaceChild("e_belly_front", CubeListBuilder.create().texOffs(24, 13).addBox(-1F, -2F, -5F, 4F, 2F, 6F, new CubeDeformation(-0.0002F)),
				PartPose.offsetAndRotation(-1F, -1.13837F, 1.15149F, -0.17453F, 0F, 0F));
		PartDefinition b_right = b_legs.addOrReplaceChild("right", CubeListBuilder.create(), PartPose.offset(0F, 0F, 3F));
		b_right.addOrReplaceChild("e_leg", CubeListBuilder.create().texOffs(44, 15).addBox(-1F, -2F, -2F, 1F, 1F, 4F, new CubeDeformation(-0.05F)),
				PartPose.offsetAndRotation(2.47539F, 0.97455F, -3.54481F, -1.5708F, -0.17453F, 0F));
		b_right.addOrReplaceChild("e_finger_left", CubeListBuilder.create().texOffs(18, 25).addBox(-1F, -2F, 0F, 1F, 1F, 2F, new CubeDeformation(-0.2F)),
				PartPose.offsetAndRotation(1.29775F, 3.96955F, -3.98508F, 0F, 0.3927F, 0F));
		b_right.addOrReplaceChild("e_finger_center", CubeListBuilder.create().texOffs(26, 47).addBox(-0.5F, -0.5F, -2F, 1F, 1F, 3F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(1.80529F, 2.46955F, -2.65262F, 0F, -0.17453F, 0F));
		b_right.addOrReplaceChild("e_finger_right", CubeListBuilder.create().texOffs(48, 27).addBox(-1F, -2F, 0F, 1F, 1F, 2F, new CubeDeformation(-0.2F)),
				PartPose.offsetAndRotation(3.47522F, 3.96955F, -3.05555F, 0F, -0.74176F, 0F));
		PartDefinition b_left = b_legs.addOrReplaceChild("left", CubeListBuilder.create(), PartPose.offset(0F, 0F, 3F));
		b_left.addOrReplaceChild("e_leg_2", CubeListBuilder.create().texOffs(44, 10).addBox(0F, -2F, -2F, 1F, 1F, 4F, new CubeDeformation(-0.05F)),
				PartPose.offsetAndRotation(-2.47539F, 0.97455F, -3.54481F, -1.5708F, 0.17453F, 0F));
		b_left.addOrReplaceChild("e_finger_left_2", CubeListBuilder.create().texOffs(24, 42).addBox(0F, -2F, 0F, 1F, 1F, 2F, new CubeDeformation(-0.2F)),
				PartPose.offsetAndRotation(-3.47522F, 3.96955F, -3.05555F, 0F, 0.74176F, 0F));
		b_left.addOrReplaceChild("e_finger_center_2", CubeListBuilder.create().texOffs(18, 46).addBox(-0.5F, -0.5F, -2F, 1F, 1F, 3F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(-1.80529F, 2.46955F, -2.65262F, 0F, 0.17453F, 0F));
		b_left.addOrReplaceChild("e_finger_right_2", CubeListBuilder.create().texOffs(0, 48).addBox(0F, -2F, 0F, 1F, 1F, 2F, new CubeDeformation(-0.2F)),
				PartPose.offsetAndRotation(-1.29775F, 3.96955F, -3.98508F, 0F, -0.3927F, 0F));
		PartDefinition b_head = b_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, -4F, -5F));
		PartDefinition b_neck = b_head.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0F, 6F, 6F));
		b_neck.addOrReplaceChild("e_neck", CubeListBuilder.create().texOffs(30, 35).addBox(-3F, -3F, -2F, 3F, 2F, 4F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(1.5F, -5.73205F, -7F, -1.0472F, 0F, 0F));
		PartDefinition b_head2 = b_head.addOrReplaceChild("head2", CubeListBuilder.create(), PartPose.offset(0F, 6F, 6F));
		b_head2.addOrReplaceChild("e_crest_thing", CubeListBuilder.create().texOffs(42, 47).addBox(-2F, -1F, 0F, 2F, 1F, 2F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(1F, -10.06218F, -9.5F, 0.5236F, 0F, 0F));
		b_head2.addOrReplaceChild("e_main_head", CubeListBuilder.create().texOffs(0, 13).addBox(-6F, -6F, -1F, 6F, 6F, 6F, new CubeDeformation(-1.5F)),
				PartPose.offsetAndRotation(3F, -5.91506F, -7.68301F, 0.5236F, 0F, 0F));
		PartDefinition b_peck = b_head2.addOrReplaceChild("peck", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
		PartDefinition b_top = b_peck.addOrReplaceChild("top", CubeListBuilder.create(), PartPose.offset(1.5F, -7.33013F, -10.23205F));
		b_top.addOrReplaceChild("e_peck_top", CubeListBuilder.create().texOffs(12, 42).addBox(-3F, -1F, -1F, 3F, 1F, 3F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(0F, 0F, 0F, 0.5236F, 0F, 0F));
		b_top.addOrReplaceChild("e_peck_top_detail", CubeListBuilder.create().texOffs(0, 45).addBox(-3F, -1F, 0F, 3F, 1F, 2F, new CubeDeformation(-0.05F)),
				PartPose.offsetAndRotation(0F, -0.36607F, -0.00005F, 0.5236F, 0F, 0F));
		PartDefinition b_bottom = b_peck.addOrReplaceChild("bottom", CubeListBuilder.create(), PartPose.offset(0F, 1F, -1F));
		b_bottom.addOrReplaceChild("e_peck_bottom", CubeListBuilder.create().texOffs(34, 47).addBox(-2F, -1F, 0F, 2F, 1F, 2F, new CubeDeformation(0F)),
				PartPose.offsetAndRotation(1F, -7.4641F, -8.73205F, 0.5236F, 0F, 0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static void registerLayer() {
		ModelLayerRegistry.registerModelLayer(LAYER, SylviornisRenderer::createLayer);
	}

	public static class Model extends EntityModel<LivingEntityRenderState> {
		private final Map<String, ModelPart> bones = new HashMap<>();
		private final ModelPart head;

		Model(ModelPart root) {
			super(root);
			collect(root);
			this.head = bones.get("head");
		}

		private void collect(ModelPart root) {
			for (Map.Entry<String, String[]> entry : PATHS.entrySet()) {
				ModelPart part = root;
				for (String name : entry.getValue()) {
					part = part.getChild(name);
				}
				bones.put(entry.getKey(), part);
			}
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float time = (state.walkAnimationPos * 0.25F) % WALK_LENGTH;
			if (time < 0) {
				time += WALK_LENGTH;
			}
			float amount = Math.min(1.0F, state.walkAnimationSpeed * 1.5F);
			for (Map.Entry<String, float[][]> entry : WALK.entrySet()) {
				ModelPart bone = bones.get(entry.getKey());
				if (bone == null) {
					continue;
				}
				float[] r = SylviornisRenderer.sample(entry.getValue(), time);
				// Blockbench's bone rotation, in the model's y-down space: x as is, y and z flipped.
				bone.xRot += (float) Math.toRadians(r[0]) * amount;
				bone.yRot -= (float) Math.toRadians(r[1]) * amount;
				bone.zRot -= (float) Math.toRadians(r[2]) * amount;
			}
			if (head != null) {
				head.yRot += state.yRot * ((float) Math.PI / 180.0F);
				head.xRot += state.xRot * ((float) Math.PI / 180.0F);
			}
		}
	}

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

	public SylviornisRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.4F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
