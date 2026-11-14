package com.huntlike.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

import com.huntlike.tools.FemurangEntity;

/** The thrown Femurang: its item lying flat and spinning like a boomerang (almost three turns a second). */
public class FemurangRenderer extends EntityRenderer<FemurangEntity, FemurangRenderer.State> {
	private final ItemModelResolver resolver;

	public static class State extends ThrownItemRenderState {
		float spin;
	}

	public FemurangRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.resolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FemurangEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		this.resolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
		state.spin = (entity.tickCount + partialTick) * 50.0F;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.0F, 0.15F, 0.0F);
		pose.rotateDegrees(Axis.XP, 90.0F);
		pose.rotateDegrees(Axis.ZP, state.spin);
		pose.scale(1.4F, 1.4F, 1.4F);
		state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
