package com.huntlike.path;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A blaze fed a Blazing Heart ★★: 100 health, 15 contact damage, 1.5x a blaze's size, with two big horns. It hunts its feeder and,
 * unlike the Blazing Piglin and Wither Skeleton, resists the heat (it never burns out). Drops a Blazing Heart ★★★.
 */
public class Demonling extends Blaze {
	public static final float HEALTH = 100.0F;
	public static final float DAMAGE = 15.0F;

	public Demonling(EntityType<? extends Blaze> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Blaze.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				// Half again a blaze's size.
				.add(Attributes.SCALE, 1.5);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Only its feeder is a target.
		this.targetSelector.removeAllGoals(goal -> true);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		BlazingMob.huntFeeder(this, level);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(ModPaths.BLAZING_HEART_STAR3));
	}
}
