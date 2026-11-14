package com.huntlike.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.huntlike.client.ClientHuntState;
import com.huntlike.hunt.HuntData.Waypoint;
import com.huntlike.network.HuntPayloads;

/**
 * The Spiritual Compass ★'s waypoints (right-click in Guiding mode): one button per waypoint, in its dye color, with its
 * place; the one being guided to is marked. Clicking one guides the compass there. Eight to a page.
 */
public class WaypointListScreen extends Screen {
	private static final int PER_PAGE = 8;
	private static final int ROW = 22;
	private int page;

	public WaypointListScreen() {
		super(Component.literal("Waypoints"));
	}

	@Override
	protected void init() {
		List<Waypoint> waypoints = ClientHuntState.data.waypoints;
		int pages = Math.max(1, (waypoints.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		int top = this.height / 2 - (PER_PAGE * ROW) / 2;
		int left = this.width / 2 - 110;
		for (int i = page * PER_PAGE; i < Math.min(waypoints.size(), (page + 1) * PER_PAGE); i++) {
			Waypoint waypoint = waypoints.get(i);
			int index = i;
			boolean target = waypoint.key().equals(ClientHuntState.data.waypointTarget);
			MutableComponent label = Component.literal(target ? "➤ " : "").append(Component.literal(waypoint.name)
					.withStyle(style -> style.withColor(waypoint.color < 0 ? 0xFFFFFF : waypoint.color)))
					.append(Component.literal("  " + waypoint.x + ", " + waypoint.y + ", " + waypoint.z + place(waypoint))
							.withStyle(style -> style.withColor(0xAAAAAA)));
			this.addRenderableWidget(Button.builder(label, button -> {
				ClientPlayNetworking.send(new HuntPayloads.Action("waypoint_target", "", index));
				this.onClose();
			}).bounds(left, top + (i - page * PER_PAGE) * ROW, 220, 20).build());
		}
		if (pages > 1) {
			int y = top + PER_PAGE * ROW + 4;
			this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
				page = (page + pages - 1) % pages;
				this.rebuildWidgets();
			}).bounds(left, y, 20, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
				page = (page + 1) % pages;
				this.rebuildWidgets();
			}).bounds(left + 200, y, 20, 20).build());
		}
	}

	private static String place(Waypoint waypoint) {
		return switch (waypoint.dimension) {
			case "minecraft:overworld" -> "";
			case "minecraft:the_nether" -> " (Nether)";
			case "minecraft:the_end" -> " (End)";
			default -> " (" + waypoint.dimension.substring(waypoint.dimension.indexOf(':') + 1) + ")";
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int top = this.height / 2 - (PER_PAGE * ROW) / 2;
		g.centeredText(this.font, this.title, this.width / 2, top - 16, 0xFFFFFFFF);
		if (ClientHuntState.data.waypoints.isEmpty()) {
			g.centeredText(this.font, Component.literal("No waypoints yet. Right-click a lodestone with the compass."), this.width / 2,
					top + 20, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
