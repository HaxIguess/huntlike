package com.huntlike.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Femurang: right-click to throw it (it never breaks). It can't be thrown again until it has come back, and never
 * sooner than 1 second after the throw. While it flies, the item shows a cooldown.
 */
public class FemurangItem extends Item {
	/** Shortest time between throws, in ticks. */
	public static final int MIN_COOLDOWN = 20;
	/** The cooldown shown while it flies; cleared when it comes back. */
	private static final int FLYING_COOLDOWN = 20 * 30;

	/** Each player's flying Femurang, and the tick it was thrown. */
	private static final Map<UUID, FemurangEntity> FLYING = new HashMap<>();
	private static final Map<UUID, Long> THROWN_AT = new HashMap<>();

	public FemurangItem(Properties properties) {
		super(properties);
	}

	public static boolean flying(Player player) {
		FemurangEntity entity = FLYING.get(player.getUUID());
		return entity != null && !entity.isRemoved();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (flying(player)) {
				return InteractionResult.FAIL;
			}
			FemurangEntity femurang = Projectile.spawnProjectileFromRotation(FemurangEntity::new, serverLevel, stack, player, 0.0F,
					FemurangEntity.SPEED, 0.0F);
			FLYING.put(player.getUUID(), femurang);
			THROWN_AT.put(player.getUUID(), serverLevel.getGameTime());
			serverPlayer.getCooldowns().addCooldown(stack, FLYING_COOLDOWN);
			level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		}
		return InteractionResult.SUCCESS;
	}

	/** The Femurang is back (or lost): it can be thrown again, at least 1 second after the throw. */
	static void returned(ServerPlayer player) {
		FLYING.remove(player.getUUID());
		Long thrown = THROWN_AT.remove(player.getUUID());
		Identifier group = player.getCooldowns().getCooldownGroup(new ItemStack(ModTools.FEMURANG));
		long elapsed = thrown == null ? MIN_COOLDOWN : player.level().getGameTime() - thrown;
		if (elapsed < MIN_COOLDOWN) {
			player.getCooldowns().addCooldown(group, (int) (MIN_COOLDOWN - elapsed));
		} else {
			player.getCooldowns().removeCooldown(group);
		}
	}
}
