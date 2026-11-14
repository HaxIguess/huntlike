package com.huntlike.tools;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** Corroded Palladium's one-time pickaxe upgrade: Unbreaking III (or more, from other mods) goes up one level. */
public final class PalladiumUpgrade {
	private PalladiumUpgrade() {
	}

	public static final String MARK = "huntlike_palladium";
	public static final int MIN_LEVEL = 3;

	public static boolean upgraded(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARK, false);
	}

	/** The upgraded pickaxe, or empty when this one can't take it. */
	public static ItemStack upgrade(Level level, ItemStack base) {
		if (base.isEmpty() || !base.is(ItemTags.PICKAXES) || upgraded(base)) {
			return ItemStack.EMPTY;
		}
		Holder<Enchantment> unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
		int current = EnchantmentHelper.getItemEnchantmentLevel(unbreaking, base);
		if (current < MIN_LEVEL) {
			return ItemStack.EMPTY;
		}
		ItemStack result = base.copyWithCount(1);
		EnchantmentHelper.updateEnchantments(result, enchantments -> enchantments.set(unbreaking, current + 1));
		CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(MARK, true);
		result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		result.set(DataComponents.LORE, result.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(
				Component.literal("Reinforced with Corroded Palladium").withStyle(style -> style.withColor(0x55FFFF).withItalic(false))));
		return result;
	}
}
