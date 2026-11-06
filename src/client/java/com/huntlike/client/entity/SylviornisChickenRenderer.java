package com.huntlike.client.entity;

import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;

import com.huntlike.HuntLike;

/** The Tier I boss, previous look (kept to switch back): the chicken model (always the temperate one, whatever biome it spawns in) with its own skin. */
public class SylviornisChickenRenderer extends ChickenRenderer {
	private static final Identifier TEXTURE = HuntLike.id("textures/entity/animal/sylviornis.png");

	public SylviornisChickenRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void extractRenderState(Chicken entity, ChickenRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		entity.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT).get(ChickenVariants.TEMPERATE)
				.ifPresent(variant -> state.variant = variant.value());
	}

	@Override
	public Identifier getTextureLocation(ChickenRenderState state) {
		return TEXTURE;
	}
}
