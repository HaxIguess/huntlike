package com.huntlike.tools;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import com.huntlike.registry.Lore;
import com.huntlike.registry.SpiritualCatalystItem;

/**
 * The Spiritual Compass ★ (a Spiritual Compass with Slime-Washed Holmium on an anvil). Two modes, switched with
 * Shift + right-click: Location (right-click detects the 3 nearest souls, as before) and Guiding (right-click lists your
 * waypoints to pick one; while held, the action bar points the way). Right-click a lodestone with it to make the
 * lodestone a waypoint.
 */
public class SpiritualCompassStarItem extends SpiritualCatalystItem {
	private static final String GUIDING = "huntlike_guiding";

	/** Opens the waypoint list on the client (set by the client). */
	public static Runnable openList = () -> {
	};

	public SpiritualCompassStarItem(Properties properties) {
		super(properties, 3);
	}

	public static List<Component> starLore() {
		List<Component> lore = new ArrayList<>(compassLore());
		Lore.ability(lore, "Waypoints", "Right-click a lodestone to save it as a waypoint, then guide yourself back to it.");
		Lore.gray(lore, "Shift and Right Click to switch compass modes.");
		return lore;
	}

	public static boolean guiding(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(GUIDING, false);
	}

	private static void setGuiding(ItemStack stack, boolean guiding) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(GUIDING, guiding);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive()) {
			if (player instanceof ServerPlayer serverPlayer) {
				boolean guiding = !guiding(stack);
				setGuiding(stack, guiding);
				serverPlayer.sendOverlayMessage(Component.literal("Compass mode: " + (guiding ? "Guiding" : "Location"))
						.withStyle(style -> style.withColor(guiding ? 0x55FFFF : 0xAAAAAA)));
				level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.8F,
						guiding ? 1.3F : 0.9F);
			}
			return InteractionResult.SUCCESS;
		}
		if (guiding(stack)) {
			if (level.isClientSide()) {
				openList.run();
			}
			return InteractionResult.SUCCESS;
		}
		return super.use(level, player, hand);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).is(Blocks.LODESTONE) || context.getPlayer() == null) {
			return InteractionResult.PASS;
		}
		if (context.getPlayer() instanceof ServerPlayer player) {
			Waypoints.register(player, (ServerLevel) level, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		super.inventoryTick(stack, level, entity, slot);
		if (entity instanceof ServerPlayer player && slot != null && slot.getType() == EquipmentSlot.Type.HAND && guiding(stack)
				&& level.getGameTime() % 5 == 0) {
			Waypoints.showWay(player);
		}
	}
}
