package com.huntlike.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.huntlike.HuntLike;
import com.huntlike.client.ClientHuntState;
import com.huntlike.hunt.HuntData;
import com.huntlike.network.HuntPayloads;
import com.huntlike.path.WildfirePath;
import com.huntlike.path.WildfirePath.Buff;

/**
 * One Path (for now the Path of the Wildfire), opened from the Path selection: a Progress panel on the left (scroll it) with
 * the goals reached and the hint for the next one, and a Skill panel on the right (drag it) with the buff tree. Later goals
 * and locked buffs stay hidden. Attack (left-click) takes a buff, use (right-click) gives it back.
 */
public class PathsScreen extends Screen {
	private static final int W = 360;
	private static final int H = 226;
	private static final int NODE = 20;
	private static final int BUFF = 20;
	private static final int PROGRESS_W = 122;
	private static final int WHITE = 0xFFFFFFFF;

	private final Screen parent;
	private int left;
	private int top;
	private double panX;
	private double panY;
	private boolean dragging;
	private double scroll;
	private int progressHeight;

	public PathsScreen(Screen parent) {
		super(Component.literal(WildfirePath.NAME));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	// ---------------------------------------------------------------- layout

	private int mapX0() {
		return left + 10;
	}

	private int mapY0() {
		return top + 20;
	}

	private int mapX1() {
		return left + W - 10;
	}

	private int mapY1() {
		return top + H - 10;
	}

	/** The Progress panel: left, top, right, bottom (inside its lava frame). */
	private int[] progressPanel() {
		return new int[] {mapX0() + 8, mapY0() + 6, mapX0() + 8 + PROGRESS_W, mapY1() - 6};
	}

	/** The Skill panel: left, top, right, bottom (inside its lava frame). */
	private int[] skillPanel() {
		int[] p = progressPanel();
		return new int[] {p[2] + 8, p[1], mapX1() - 8, p[3]};
	}

	private static boolean in(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	private static boolean inside(int[] box, double x, double y) {
		return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}

	/** A skill's square: Netherspawn on the left, the three tier II skills next, the four tier III skills last. */
	private int[] buff(int i) {
		Buff buff = Buff.values()[i];
		int[] s = skillPanel();
		int cx = (s[0] + s[2]) / 2 + (int) panX;
		int cy = (s[1] + s[3]) / 2 + 14 + (int) panY;
		int column = buff.tier - 2;
		// Rows in half steps: tier II at -1, 0, 1; tier III's four at -1.5, -0.5, 0.5, 1.5, each next to its parent.
		int halfRow = switch (buff) {
			case NETHERSPAWN, RAGING_METABOLISM -> 0;
			case HOT_BLOWS -> -2;
			case GOLDEN_STYLE -> 2;
			case FIERY_BLOWS -> -3;
			case HOT_FOOD -> -1;
			case NETHER_VEIL -> 1;
			case HOT_TUB -> 3;
		};
		return new int[] {cx + column * (BUFF + 36) - BUFF / 2, cy + halfRow * (BUFF + 12) / 2 - BUFF / 2, BUFF, BUFF};
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		HuntData data = ClientHuntState.data;
		left = (this.width - W) / 2;
		top = (this.height - H) / 2;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HuntLike.id("menu/window"), left, top, W, H);
		Component title = Component.literal(WildfirePath.NAME).withStyle(style -> style.withBold(true)).withColor(0x404040);
		g.text(this.font, title, left + W / 2 - this.font.width(title) / 2, top + 7, 0xFF404040, false);
		drawStarfield(g, mapX0(), mapY0(), mapX1(), mapY1(), 0, 0);
		List<Component> tooltip = null;

		int[] p = progressPanel();
		lavaFrame(g, p);
		g.enableScissor(p[0], p[1], p[2], p[3]);
		drawFloor(g, p, SOUL_FLOOR, 0, (int) -scroll, 0x88000000);
		List<Component> t = drawProgress(g, data, p, mouseX, mouseY);
		g.disableScissor();
		if (t != null && in(p, mouseX, mouseY)) {
			tooltip = t;
		}

		int[] s = skillPanel();
		lavaFrame(g, s);
		g.enableScissor(s[0], s[1], s[2], s[3]);
		drawFloor(g, s, BRICKS, (int) panX, (int) panY, 0x66000000);
		t = drawSkills(g, data, s, mouseX, mouseY);
		g.disableScissor();
		if (t != null && in(s, mouseX, mouseY)) {
			tooltip = t;
		}
		if (tooltip != null) {
			g.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
		}
	}

	/** The goals reached (their names) and the next one (its hint); nothing after it. */
	private List<Component> drawProgress(GuiGraphicsExtractor g, HuntData data, int[] p, int mouseX, int mouseY) {
		List<Component> tooltip = null;
		int reached = data.wildfireGoals;
		int shown = Math.min(WildfirePath.GOALS.size(), reached + 1);
		int textX = p[0] + 6 + NODE + 8;
		int textW = p[2] - textX - 4;
		int y = p[1] + 8 - (int) scroll;
		int[] previous = null;
		for (int i = 0; i < shown; i++) {
			boolean done = i < reached;
			String text = done ? WildfirePath.GOALS.get(i) : WildfirePath.HINTS.get(i);
			List<FormattedCharSequence> lines = this.font.split(done ? Component.literal(text).withColor(0xFFFFFF)
					: Component.literal(text).withStyle(style -> style.withItalic(true)).withColor(0xC8B8A8), textW);
			// Goals with a count show it under the hint: "X/50".
			if (!done && (i == 2 || i == 9)) {
				int count = i == 2 ? Math.min(WildfirePath.BLAZES, data.wildfireBlazes) : Math.min(WildfirePath.EVILBORNS, data.wildfireEvilborns);
				int total = i == 2 ? WildfirePath.BLAZES : WildfirePath.EVILBORNS;
				lines = new ArrayList<>(lines);
				lines.addAll(this.font.split(Component.literal(count + "/" + total).withColor(0xFFCC66), textW));
			}
			int[] box = {p[0] + 6, y, NODE, NODE};
			if (previous != null) {
				lavaRun(g, previous[0] + NODE / 2, previous[1] + NODE + 2, box[1] - 2, true);
			}
			square(g, box[0], box[1], NODE, done);
			numeral(g, box, i, true);
			int ty = y + Math.max(0, (NODE - lines.size() * 9) / 2);
			for (FormattedCharSequence line : lines) {
				g.text(this.font, line, textX, ty, WHITE, true);
				ty += 9;
			}
			if (inside(box, mouseX, mouseY)) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(done ? WildfirePath.GOALS.get(i) : "Next goal").withColor(WildfirePath.COLOR));
				tip.add(Component.literal("Increased affinity with the Path of the Wildfire.").withColor(0xFFCC66));
				tooltip = tip;
			}
			previous = box;
			y += Math.max(NODE, lines.size() * 9) + 14;
		}
		progressHeight = y + (int) scroll - p[1];
		return tooltip;
	}

	/** The points counter and the buff tree. A buff whose parent isn't taken stays a nameless crying obsidian square. */
	private List<Component> drawSkills(GuiGraphicsExtractor g, HuntData data, int[] s, int mouseX, int mouseY) {
		List<Component> tooltip = null;
		// The points: [earned / most] (spending them doesn't lower it), centered at the top of the panel.
		String count = WildfirePath.points(data) + "/" + WildfirePath.maxPoints();
		int pw = this.font.width(count) + 10;
		int px = (s[0] + s[2]) / 2 - pw / 2 + (int) panX;
		int py = s[1] + 10 + (int) panY;
		lavaFrame(g, new int[] {px, py, px + pw, py + 11});
		g.blitSprite(RenderPipelines.GUI_TEXTURED, block("obsidian"), px, py, pw, 11);
		g.text(this.font, Component.literal(count).withColor(0xFFA040), px + 5, py + 2, WHITE, true);
		if (mouseX >= px - 2 && mouseX < px + pw + 2 && mouseY >= py - 2 && mouseY < py + 13) {
			tooltip = List.of(Component.literal("Gain a ").withColor(0xFFFFFF).append(Component.literal("Wildfire point").withColor(WildfirePath.COLOR))
					.append(Component.literal(" for every goal on this Path!").withColor(0xFFFFFF)));
		}
		// Connectors: one clean run per parent (out, down or up to each child's row, in).
		for (Buff parentBuff : Buff.values()) {
			List<int[]> children = new ArrayList<>();
			for (Buff child : Buff.values()) {
				if (child.parent == parentBuff) {
					children.add(buff(child.ordinal()));
				}
			}
			if (children.isEmpty()) {
				continue;
			}
			int[] a = buff(parentBuff.ordinal());
			int ay = a[1] + BUFF / 2;
			int startX = a[0] + BUFF + 2;
			int endX = children.get(0)[0] - 2;
			int mid = (startX + endX) / 2;
			boolean straight = children.size() == 1 && children.get(0)[1] + BUFF / 2 == ay;
			if (straight) {
				lavaRun(g, ay, startX, endX, false);
				continue;
			}
			int minY = ay;
			int maxY = ay;
			for (int[] c : children) {
				minY = Math.min(minY, c[1] + BUFF / 2);
				maxY = Math.max(maxY, c[1] + BUFF / 2);
			}
			lavaRun(g, ay, startX, mid + 1, false);
			lavaRun(g, mid, minY - 1, maxY + 1, true);
			for (int[] c : children) {
				lavaRun(g, c[1] + BUFF / 2, mid - 1, c[0] - 2, false);
			}
		}
		for (Buff buff : Buff.values()) {
			int[] b = buff(buff.ordinal());
			boolean owned = WildfirePath.has(data, buff);
			boolean known = buff.parent == null || WildfirePath.has(data, buff.parent);
			square(g, b[0], b[1], BUFF, owned);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(b[0] + BUFF / 2.0F, b[1] + BUFF / 2.0F - 2.5F);
			pose.scale(0.75F, 0.75F);
			Component numeral = Component.literal(ROMAN[buff.tier - 1]).withColor(0xFFFFFF);
			g.text(this.font, numeral, -this.font.width(numeral) / 2, 0, WHITE, true);
			pose.popMatrix();
			if (inside(b, mouseX, mouseY)) {
				List<Component> lines = new ArrayList<>();
				if (!known) {
					lines.add(Component.literal("???").withColor(0x808080));
				} else {
					lines.add(Component.literal(buff.title).withColor(WildfirePath.COLOR));
					lines.add(Component.literal(buff.description).withColor(0xAAAAAA));
					if (owned) {
						lines.add(Component.literal("Taken").withColor(0x55FF55));
					} else {
						lines.add(Component.literal("Cost: 1 Wildfire point").withColor(WildfirePath.free(data) > 0 ? 0xFFCC66 : 0xFF5555));
					}
				}
				tooltip = lines;
			}
		}
		return tooltip;
	}

	private void numeral(GuiGraphicsExtractor g, int[] box, int i, boolean bold) {
		Component mark = Component.literal(ROMAN[i]).withStyle(style -> style.withBold(bold)).withColor(0xFFFFFF);
		g.text(this.font, mark, box[0] + (box[2] - this.font.width(mark)) / 2, box[1] + (box[3] - 7) / 2, WHITE, true);
	}

	// ---------------------------------------------------------------- textures

	static net.minecraft.client.renderer.texture.TextureAtlasSprite block(String name) {
		return net.minecraft.client.Minecraft.getInstance().getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(
				net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, net.minecraft.resources.Identifier.withDefaultNamespace("block/" + name)));
	}

	static final String[] BRICKS = {"nether_bricks", "nether_bricks", "nether_bricks", "red_nether_bricks", "cracked_nether_bricks",
			"chiseled_nether_bricks"};
	/** Mostly soul sand, some soul soil, a little bone. */
	static final String[] SOUL_FLOOR = {"soul_sand", "soul_sand", "soul_sand", "soul_sand", "soul_sand", "soul_sand", "soul_sand",
			"soul_sand", "soul_soil", "soul_soil", "soul_soil", "bone_block_top"};
	static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private static final net.minecraft.resources.Identifier END_PORTAL =
			net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer.END_PORTAL_LOCATION;

	/** The darkened end portal starfield, drifting slowly (used by the Paths and the Soul Nexus). */
	public static void drawStarfield(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double panX, double panY) {
		g.fill(x0, y0, x1, y1, 0xFF0C0A12);
		g.enableScissor(x0, y0, x1, y1);
		int size = 256;
		double drift = System.currentTimeMillis() / 200.0 % size;
		int ox = (int) Math.floor(panX * 0.5 + drift);
		int oy = (int) Math.floor(panY * 0.5);
		for (int y = y0 + Math.floorMod(oy, size) - size; y < y1; y += size) {
			for (int x = x0 + Math.floorMod(ox, size) - size; x < x1; x += size) {
				g.blit(RenderPipelines.GUI_TEXTURED, END_PORTAL, x, y, 0.0F, 0.0F, size, size, size, size);
			}
		}
		g.fill(x0, y0, x1, y1, 0x99000000);
		g.disableScissor();
	}

	/** A random mix of blocks filling a panel, moving with (ox, oy). */
	static void drawFloor(GuiGraphicsExtractor g, int[] r, String[] blocks, int ox, int oy, int shade) {
		int startX = r[0] + Math.floorMod(ox, 16) - 16;
		int startY = r[1] + Math.floorMod(oy, 16) - 16;
		for (int y = startY; y < r[3]; y += 16) {
			for (int x = startX; x < r[2]; x += 16) {
				int tx = Math.floorDiv(x - r[0] - ox, 16);
				int ty = Math.floorDiv(y - r[1] - oy, 16);
				long h = (tx * 73856093L) ^ (ty * 19349663L);
				h ^= h >>> 13;
				String name = blocks[(int) Math.floorMod(h * 0x9E3779B97F4A7C15L >>> 40, (long) blocks.length)];
				g.blitSprite(RenderPipelines.GUI_TEXTURED, block(name), x, y, 16, 16);
			}
		}
		g.fill(r[0], r[1], r[2], r[3], shade);
	}

	/** A 2-pixel lava frame around a rectangle (left, top, right, bottom). */
	static void lavaFrame(GuiGraphicsExtractor g, int[] r) {
		var lava = block("lava_still");
		g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, r[0] - 2, r[1] - 2, r[2] - r[0] + 4, 2);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, r[0] - 2, r[3], r[2] - r[0] + 4, 2);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, r[0] - 2, r[1], 2, r[3] - r[1]);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, r[2], r[1], 2, r[3] - r[1]);
	}

	/** A square: a lava frame (animated) around obsidian, or crying obsidian while not reached or taken. */
	static void square(GuiGraphicsExtractor g, int x, int y, int size, boolean unlocked) {
		lavaFrame(g, new int[] {x, y, x + size, y + size});
		g.blitSprite(RenderPipelines.GUI_TEXTURED, block(unlocked ? "obsidian" : "crying_obsidian"), x, y, size, size);
	}

	/**
	 * A 2-pixel lava run: horizontal at height `at` from `from` to `to` (x), or vertical at x `at` from `from` to `to` (y).
	 * Runs are exact, so joints meet without stubs.
	 */
	static void lavaRun(GuiGraphicsExtractor g, int at, int from, int to, boolean vertical) {
		int a = Math.min(from, to);
		int b = Math.max(from, to);
		if (b <= a) {
			return;
		}
		var lava = block("lava_still");
		if (vertical) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, at - 1, a, 2, b - a);
		} else {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, lava, a, at - 1, b - a, 2);
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		HuntData data = ClientHuntState.data;
		boolean attack = this.minecraft.options.keyAttack.matchesMouse(event);
		boolean use = this.minecraft.options.keyUse.matchesMouse(event);
		if (in(skillPanel(), event.x(), event.y())) {
			for (Buff buff : Buff.values()) {
				if ((attack || use) && inside(buff(buff.ordinal()), event.x(), event.y())) {
					ClientPlayNetworking.send(new HuntPayloads.Action("wildfire_buff", Integer.toString(buff.ordinal()), attack ? 1 : -1));
					return true;
				}
			}
			dragging = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			panX = Math.max(-140, Math.min(140, panX + dx));
			panY = Math.max(-90, Math.min(90, panY + dy));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int[] p = progressPanel();
		if (in(p, x, y)) {
			int max = Math.max(0, progressHeight - (p[3] - p[1]) + 6);
			scroll = Math.max(0, Math.min(max, scroll - scrollY * 12));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
