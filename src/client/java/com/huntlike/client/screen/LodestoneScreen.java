package com.huntlike.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.huntlike.client.ClientHuntState;
import com.huntlike.hunt.HuntData.Waypoint;
import com.huntlike.network.HuntPayloads;
import com.huntlike.tools.LodestoneMenu;
import com.huntlike.tools.Waypoints;

/**
 * A waypoint lodestone's menu, after the user's mockup: the lodestone (big) at the top left, "Name" and its box beside it,
 * then Dye Waypoint, the dye slot and Remove Waypoint, then the inventory. The name is saved as it is typed.
 */
public class LodestoneScreen extends AbstractContainerScreen<LodestoneMenu> {
	private static final int BACKGROUND = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int DARK = 0xFF555555;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int TEXT = 0xFF404040;

	private EditBox name;
	private Button dyeButton;
	private boolean nameLoaded;
	private String lastSent = "";

	public LodestoneScreen(LodestoneMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LodestoneMenu.WIDTH, 166);
		this.inventoryLabelX = 18;
		this.inventoryLabelY = LodestoneMenu.INVENTORY_Y - 11;
		this.titleLabelY = -1000;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.name = new EditBox(this.font, x + 64, y + 21, 122, 12, Component.literal("Name"));
		this.name.setMaxLength(Waypoints.MAX_NAME);
		this.name.setBordered(false);
		this.name.setTextColor(0xFFFFFFFF);
		this.name.setValue(this.title.getString());
		this.name.setResponder(this::sendName);
		this.addRenderableWidget(this.name);
		this.dyeButton = this.addRenderableWidget(Button.builder(Component.literal("Dye Waypoint"), button -> click(LodestoneMenu.BUTTON_DYE))
				.bounds(x + 7, y + LodestoneMenu.DYE_Y - 1, 70, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("Remove Waypoint"), button -> click(LodestoneMenu.BUTTON_REMOVE))
				.bounds(x + 99, y + LodestoneMenu.DYE_Y - 1, 90, 18).build());
		this.lastSent = this.title.getString();
	}

	private void click(int id) {
		if (this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private void sendName(String value) {
		if (!value.isBlank() && !value.equals(lastSent)) {
			lastSent = value;
			ClientPlayNetworking.send(new HuntPayloads.Action("waypoint_rename", value, 0));
		}
	}

	/** This lodestone's waypoint in the client's copy of the player's data. */
	private Waypoint waypoint() {
		if (this.minecraft.level == null) {
			return null;
		}
		var pos = this.menu.pos();
		return ClientHuntState.data.waypoint(Waypoints.dimension(this.minecraft.level), pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256) {
			this.onClose();
			return true;
		}
		if (this.name.isFocused()) {
			// Typing in the box doesn't close the menu (E) or drop items (Q).
			return this.name.keyPressed(event) || true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		this.dyeButton.active = this.menu.getSlot(0).hasItem();
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, DARK);
		g.fill(x, y, x + this.imageWidth - 1, y + this.imageHeight - 1, LIGHT);
		g.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, BACKGROUND);
		// The lodestone, three times an item's size.
		g.pose().pushMatrix();
		g.pose().translate(x + 9, y + 6);
		g.pose().scale(2.75F, 2.75F);
		g.item(new ItemStack(Items.LODESTONE), 0, 0);
		g.pose().popMatrix();
		// The name box's dark frame, like the anvil's.
		g.fill(x + 60, y + 18, x + 189, y + 34, SLOT_DARK);
		g.fill(x + 61, y + 19, x + 188, y + 33, 0xFF000000);
		for (Slot slot : this.menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			g.fill(sx, sy, sx + 18, sy + 18, LIGHT);
			g.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
			g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
		}
		if (!this.menu.getSlot(0).hasItem()) {
			// A faint dye in the empty slot.
			g.fakeItem(new ItemStack(Items.DYE.red()), x + LodestoneMenu.DYE_X, y + LodestoneMenu.DYE_Y);
			g.fill(x + LodestoneMenu.DYE_X, y + LodestoneMenu.DYE_Y, x + LodestoneMenu.DYE_X + 16, y + LodestoneMenu.DYE_Y + 16, 0xA08B8B8B);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractLabels(g, mouseX, mouseY);
		g.text(this.font, Component.literal("Name"), 62, 8, TEXT, false);
		Waypoint waypoint = waypoint();
		if (waypoint != null && waypoint.color >= 0) {
			g.fill(180, 7, 188, 15, 0xFF000000);
			g.fill(181, 8, 187, 14, 0xFF000000 | waypoint.color);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// The title already holds the name; once the lodestone's place arrives, follow the saved name if it changed elsewhere.
		Waypoint waypoint = waypoint();
		if (!nameLoaded && waypoint != null) {
			nameLoaded = true;
			if (!this.name.isFocused() && !waypoint.name.equals(this.name.getValue())) {
				lastSent = waypoint.name;
				this.name.setValue(waypoint.name);
			}
		}
	}
}
