package com.huntlike.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.huntlike.HuntLike;
import com.huntlike.client.entity.horns.Horns;
import com.huntlike.path.Demonling;

/**
 * The Demonling: the blaze model, 1.5x a blaze, with nine rods (three per ring, 120 degrees apart) and the Imp's two horns
 * at twice their size (DemonlingHorns).
 * Uses textures/entity/path/demonling.png when the mod has one (64x32, the blaze's layout), otherwise the blaze's skin.
 */
public class DemonlingRenderer extends MobRenderer<Demonling, LivingEntityRenderState, BlazeModel> {
	private static final Identifier TEXTURE = HuntLike.id("textures/entity/path/demonling.png");
	private final BlazeRenderer blaze;

	/** The blaze's 12 rods: the fourth of each ring is hidden and the other three spread evenly. */
	static class DemonlingModel extends BlazeModel {
		private static final float[] RADIUS = {9.0F, 7.0F, 5.0F};
		private final ModelPart[] rods = new ModelPart[12];

		DemonlingModel(ModelPart root) {
			super(root);
			for (int i = 0; i < 12; i++) {
				rods[i] = root.getChild("part" + i);
			}
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float[] start = {age * (float) Math.PI * -0.1F, (float) Math.PI / 4.0F + age * (float) Math.PI * 0.03F,
					0.47123894F + age * (float) Math.PI * -0.05F};
			for (int ring = 0; ring < 3; ring++) {
				for (int k = 0; k < 4; k++) {
					ModelPart rod = rods[ring * 4 + k];
					rod.visible = k < 3;
					if (k < 3) {
						float angle = start[ring] + (float) (Math.PI * 2.0 / 3.0) * k;
						rod.x = (float) Math.cos(angle) * RADIUS[ring];
						rod.z = (float) Math.sin(angle) * RADIUS[ring];
					}
				}
			}
		}
	}

	public DemonlingRenderer(EntityRendererProvider.Context context) {
		super(context, new DemonlingModel(context.bakeLayer(ModelLayers.BLAZE)), 0.5F);
		this.blaze = new BlazeRenderer(context);
		this.addLayer(new Horns.Layer<>(this, context.bakeLayer(DemonlingHorns.LAYER), HuntLike.id("textures/entity/path/demonling_horns.png")));
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isPresent() ? TEXTURE : blaze.getTextureLocation(state);
	}

	@Override
	protected int getBlockLightLevel(Demonling entity, BlockPos pos) {
		return 15;
	}
}
