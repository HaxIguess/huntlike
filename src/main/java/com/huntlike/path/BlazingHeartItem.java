package com.huntlike.path;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.huntlike.hunt.QuestManager;

/**
 * The Blazing Heart: eaten slowly (3x a normal food), it fills the whole hunger bar, sets the eater on fire and makes
 * them breathe an uncontrollable flame beam for 3 seconds.
 */
public class BlazingHeartItem extends Item {
	/** Normal food takes 32 ticks; the heart takes three times that. */
	public static final int EAT_TICKS = 32 * 3;

	public BlazingHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && WildfirePath.has(QuestManager.data(player), WildfirePath.Buff.RAGING_METABOLISM)) {
			return EAT_TICKS / 2;
		}
		if (entity != null && entity.level().isClientSide() && WildfirePath.clientHasRagingMetabolism()) {
			return EAT_TICKS / 2;
		}
		return EAT_TICKS;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (entity instanceof ServerPlayer player) {
			WildfirePath.onHeartEaten(player);
		}
		return result;
	}
}
