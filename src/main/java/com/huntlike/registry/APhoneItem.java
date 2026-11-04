package com.huntlike.registry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.huntlike.hunt.QuestManager;

/** The A-Phone: the Alpha Altar in your pocket. Right-click opens the Hunt menu anywhere. */
public class APhoneItem extends Item {
	public APhoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			QuestManager.openMenu(serverPlayer, true);
		}
		return InteractionResult.SUCCESS;
	}
}
