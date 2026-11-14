package com.huntlike.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

import com.huntlike.registry.ModItems;
import com.huntlike.tools.PalladiumUpgrade;

/**
 * Corroded Palladium at the smithing table: a Slimy Core as the template, a pickaxe with Unbreaking III or more as the
 * base and the Palladium as the material give the pickaxe one more level of Unbreaking, once. (A data recipe with the
 * same three ingredients lets them into the slots; its result is replaced here.) All three are used up.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
	private SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void huntlike$palladium(CallbackInfo ci) {
		ItemStack template = this.inputSlots.getItem(0);
		ItemStack addition = this.inputSlots.getItem(2);
		if (!template.is(ModItems.SLIMY_CORE) || !addition.is(ModItems.CORRODED_PALLADIUM)) {
			return;
		}
		ci.cancel();
		this.resultSlots.setItem(0, PalladiumUpgrade.upgrade(this.player.level(), this.inputSlots.getItem(1)));
	}
}
