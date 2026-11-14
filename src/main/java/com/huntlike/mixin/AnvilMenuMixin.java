package com.huntlike.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import com.huntlike.interlude.ModInterlude;

/**
 * The Growing Slime Ball on an anvil: an enchanted book or any enchanted item on the left, the Slimeball on the right, and
 * every enchantment below its maximum goes up one level, for 5 levels per enchantment raised (see GrowingSlimeBall). A Horseshoe on Pom's
 * Skeletal Remains adds a star (up to 5) for 5 levels. Slime-Washed Holmium on a Spiritual Compass makes it a Spiritual Compass ★.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;
	@Shadow
	private int repairItemCountCost;

	private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
			net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void huntlike$growingSlimeball(CallbackInfo ci) {
		ItemStack book = this.inputSlots.getItem(0);
		ItemStack ball = this.inputSlots.getItem(1);
		if (ball.is(ModInterlude.HORSESHOE)) {
			huntlike$horseshoe(book, ci);
			return;
		}
		if (ball.is(com.huntlike.registry.ModItems.SLIME_WASHED_HOLMIUM)) {
			huntlike$holmium(book, ci);
			return;
		}
		if (!ball.is(ModInterlude.GROWING_SLIME_BALL)) {
			return;
		}
		ci.cancel();
		ItemStack result = com.huntlike.interlude.GrowingSlimeBall.grow(book);
		if (result.isEmpty()) {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
			this.cost.set(0);
			return;
		}
		this.resultSlots.setItem(0, result);
		this.repairItemCountCost = 1;
		this.cost.set(com.huntlike.interlude.GrowingSlimeBall.cost(book));
		this.broadcastChanges();
	}

	/** Slime-Washed Holmium on a Spiritual Compass: the Spiritual Compass ★ (waypoints), for 5 levels. */
	private void huntlike$holmium(ItemStack compass, CallbackInfo ci) {
		ci.cancel();
		if (!compass.is(com.huntlike.registry.ModItems.SPIRITUAL_COMPASS)) {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
			this.cost.set(0);
			return;
		}
		this.resultSlots.setItem(0, compass.transmuteCopy(com.huntlike.tools.ModTools.SPIRITUAL_COMPASS_STAR, 1));
		this.repairItemCountCost = 1;
		this.cost.set(5);
		this.broadcastChanges();
	}

	/** A Horseshoe on Pom's Skeletal Remains: one more star (up to 5) for 5 levels. */
	private void huntlike$horseshoe(ItemStack remains, CallbackInfo ci) {
		ci.cancel();
		int stars = com.huntlike.interlude.RemainsItem.stars(remains);
		if (!remains.is(ModInterlude.POMS_SKELETAL_REMAINS) || stars >= com.huntlike.interlude.RemainsItem.MAX_STARS) {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
			this.cost.set(0);
			return;
		}
		ItemStack result = remains.copyWithCount(1);
		result.set(ModInterlude.HORSESHOES, stars + 1);
		this.resultSlots.setItem(0, result);
		this.repairItemCountCost = 1;
		this.cost.set(5);
		this.broadcastChanges();
	}
}
