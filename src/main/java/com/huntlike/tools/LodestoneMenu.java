package com.huntlike.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.huntlike.HuntLike;

/**
 * A waypoint lodestone's menu (the user's mockup, laid out like an anvil): the lodestone, the waypoint's name, a dye slot
 * with Dye Waypoint, Remove Waypoint, and the inventory. Button 0 dyes (uses one dye), button 1 removes the waypoint.
 * The lodestone's position travels as six 16-bit numbers.
 */
public class LodestoneMenu extends AbstractContainerMenu {
	public static final MenuType<LodestoneMenu> TYPE = Registry.register(BuiltInRegistries.MENU, HuntLike.id("lodestone"),
			new MenuType<>(LodestoneMenu::new, FeatureFlags.VANILLA_SET));

	public static final int WIDTH = 196;
	public static final int DYE_X = 80;
	public static final int DYE_Y = 47;
	public static final int INVENTORY_Y = 84;
	public static final int BUTTON_DYE = 0;
	public static final int BUTTON_REMOVE = 1;

	private final SimpleContainer dye = new SimpleContainer(1);
	private final ContainerData data;
	private final BlockPos serverPos;

	/** Client side. */
	public LodestoneMenu(int id, Inventory inventory) {
		this(id, inventory, null, new SimpleContainerData(6));
	}

	/** Server side. */
	public LodestoneMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, pos, new SimpleContainerData(6));
		set(this.data, 0, pos.getX());
		set(this.data, 2, pos.getY());
		set(this.data, 4, pos.getZ());
	}

	private LodestoneMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
		super(TYPE, id);
		this.serverPos = pos;
		this.data = data;
		this.addSlot(new Slot(dye, 0, DYE_X, DYE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.DYE) && stack.is(net.minecraft.tags.ItemTags.DYES);
			}
		});
		this.addStandardInventorySlots(inventory, 18, INVENTORY_Y);
		this.addDataSlots(data);
	}

	private static void set(ContainerData data, int index, int value) {
		data.set(index, value & 0xFFFF);
		data.set(index + 1, (value >>> 16) & 0xFFFF);
	}

	private int get(int index) {
		return (data.get(index) & 0xFFFF) | (data.get(index + 1) & 0xFFFF) << 16;
	}

	public BlockPos pos() {
		return serverPos != null ? serverPos : new BlockPos(get(0), get(2), get(4));
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPos == null) {
			return false;
		}
		if (id == BUTTON_DYE) {
			ItemStack stack = dye.getItem(0);
			if (!stack.isEmpty() && Waypoints.dye(serverPlayer, serverPos, stack)) {
				stack.shrink(1);
				dye.setChanged();
				this.broadcastChanges();
				return true;
			}
			return false;
		}
		if (id == BUTTON_REMOVE) {
			Waypoints.remove(serverPlayer, serverPos);
			serverPlayer.closeContainer();
			return true;
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.clearContainer(player, dye);
	}

	@Override
	public boolean stillValid(Player player) {
		return serverPos == null || Waypoints.mayUse(player, serverPos);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index == 0) {
			if (!this.moveItemStackTo(stack, 1, 37, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.slots.get(0).mayPlace(stack) || !this.moveItemStackTo(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}
}
