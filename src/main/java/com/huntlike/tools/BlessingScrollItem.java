package com.huntlike.tools;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Blessing Scroll (a cleric's trade for Golden Meat): 3 uses, each giving Regeneration V for 1 second. */
public class BlessingScrollItem extends Item {
	public static final int USES = 3;
	public static final int TICKS = 20;
	/** Regeneration V. */
	public static final int AMPLIFIER = 4;

	public BlessingScrollItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TICKS, AMPLIFIER));
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			serverPlayer.getCooldowns().addCooldown(stack, TICKS);
			stack.hurtAndBreak(1, serverPlayer, hand);
		}
		return InteractionResult.SUCCESS;
	}
}
