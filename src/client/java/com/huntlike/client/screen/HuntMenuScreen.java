package com.huntlike.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.huntlike.HuntLike;
import com.huntlike.client.ClientHuntState;
import com.huntlike.hunt.HuntData;
import com.huntlike.hunt.HuntDefs;
import com.huntlike.hunt.HuntDefs.Ability;
import com.huntlike.hunt.HuntDefs.Drop;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.HuntDefs.Tier;
import com.huntlike.hunt.HuntDefs.Unlock;
import com.huntlike.network.HuntPayloads;
import com.huntlike.registry.ModItems;

/**
 * The Hunt menu opened by the Alpha Altar: Quests, Gear and Ranks tabs.
 * Every tab starts on the Hunt rail; clicking a Hunt's symbol (or Enter) opens its panel.
 */
public class HuntMenuScreen extends Screen {
	private static final Identifier ANKH = HuntLike.id("textures/gui/ankh.png");
	private static final Identifier MERCURY = HuntLike.id("textures/gui/mercury.png");
	private static final Identifier INTERLUDE = HuntLike.id("textures/gui/interlude.png");
	private static final int RAIL_SIZE = 3;
	/** Rail positions in pixels: Animal, the Interlude orb halfway, Cluster. */
	private static final int[] RAIL_COORDS = {0, 28, 56};
	/** Vanilla's padlock (the cartography table's "locked" icon) and iron chain. */
	private static final Identifier LOCK = Identifier.withDefaultNamespace("container/cartography_table/locked");
	private static final Identifier CHAIN = Identifier.withDefaultNamespace("textures/block/iron_chain.png");

	/** Each Hunt's symbol on the rail: the ankh for Animal, Mercury's alchemical symbol for Cluster. */
	private static Identifier symbol(Hunt hunt) {
		return HuntDefs.isCluster(hunt) ? MERCURY : ANKH;
	}
	private static final String[] TABS = {"Quests", "Gear", "Ranks"};

	private static final int W = 360;
	private static final int MAX_H = 256;
	private static final int TAB_H = 20;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int TAB_TEXT = 0xFF404040;
	private static final int RAIL = 0xFFC6C6C6;
	private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
	private static final int TEXT_GRAY = 0xFFAAAAAA;
	private static final int TEXT_DIM = 0xFF6F6F6F;

	private int tab;
	/** Opened from the A-Phone: the Hunting Pack side tab shows. */
	private final boolean phone;
	/** The Hunting Pack screen is showing (from the side tab, or opened on it during a pack quest). */
	private boolean packView;
	/** The members box lists online players to invite instead of the members. */
	private boolean packPlayers;
	/** The open options popup: a member's UUID, or "leader" for the big head; null when closed. */
	private String popupFor;
	private int popupX;
	private int popupY;
	private boolean inboxOpen;
	private boolean stashOpen;
	/** Text being typed: 0 none, 1 the pack's name, 2 its member limit. */
	private int editField;
	private String editText = "";
	/** Scroll of the members box, in pixels. */
	private int packScroll;
	private int[] packBox;
	/** False: the Hunt rail only. True: the selected Hunt's panel is open. */
	private boolean panelOpen;
	private int huntIndex;
	/** Rail position: 0 Animal, 1 the Slime Rider Interlude, 2 Cluster. */
	private int railPos;
	private int interludeStage = 1;
	/** The Interlude token trade button (x, y, w, h) from the last frame, or null. */
	private int[] interludeTokenButton;
	private boolean giveUpArmed;
	private int questTier = 1;
	private int rankView;
	private int abilityScroll;
	/** Client-only boss entities for the boss card preview. */
	private final java.util.Map<Integer, LivingEntity> previews = new java.util.HashMap<>();
	/** The tier whose Drops panel is hovered this frame, or null. */
	private Tier dropsHover;
	/** Gear tab page: 0 Armor, 1 Misc, 2 Weapons. */
	private int gearPage = 1;
	private int gearScroll;
	/** The token trade button (x, y, w, h) from the last frame, or null. */
	private int[] tokenButton;
	private boolean cancelArmed;

	// Layout, recomputed each frame.
	private int H = MAX_H;
	private int left;
	private int top;
	private int ix;
	private int iy;
	private int iw;
	private int ih;
	private int px;
	private int py;
	private int pw;
	private int ph;

	/** Center of each labelled button drawn last frame, for automated tests. */
	private final java.util.Map<String, int[]> buttonCenters = new java.util.HashMap<>();

	/** Clickable areas registered while drawing, checked on click. */
	private final List<Hit> hits = new ArrayList<>();

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public HuntMenuScreen(int tab) {
		super(Component.literal("Hunt Menu"));
		this.phone = (tab & com.huntlike.hunt.QuestManager.PHONE_FLAG) != 0;
		int base = tab & 15;
		this.packView = base == com.huntlike.hunt.QuestManager.PACK_TAB;
		this.tab = this.packView ? 0 : Math.max(0, Math.min(2, base));
		HuntData data = ClientHuntState.data;
		for (int i = 0; i < HuntDefs.HUNTS.size(); i++) {
			if (HuntDefs.HUNTS.get(i).id().equals(data.selectedHunt)) {
				this.huntIndex = i;
			}
		}
		// A Hunt that is still locked never stays selected: the menu opens on the Animal Hunt instead.
		if (!HuntDefs.huntUnlocked(data, hunt())) {
			this.huntIndex = 0;
			ClientPlayNetworking.send(new HuntPayloads.Action("select", hunt().id(), 0));
		}
		this.railPos = huntIndex == 0 ? 0 : 2;
		if (com.huntlike.interlude.InterludeDefs.SLIME_RIDER.equals(data.selectedHunt)) {
			this.railPos = com.huntlike.interlude.InterludeDefs.open(data) ? 1 : 0;
			this.huntIndex = 0;
			if (this.railPos == 0) {
				// A locked Interlude never stays selected either.
				ClientPlayNetworking.send(new HuntPayloads.Action("select", "animal", 0));
			}
		}
		this.interludeStage = highestOpenStage();
		this.rankView = progress().rank();
		this.questTier = highestUnlockedTier();
		// After the first visit in this world, the menu opens straight onto the selected Hunt's panel.
		this.panelOpen = hintSeen();
	}

	@Override
	protected void init() {
		ClientPlayNetworking.send(new HuntPayloads.Action("refresh", hunt().id(), 0));
	}

	/** GUI coordinates of a button's center from the last frame, or null. Used by automated tests. */
	public int[] buttonCenter(String label) {
		return buttonCenters.get(label);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Hunt hunt() {
		return HuntDefs.HUNTS.get(huntIndex);
	}

	/** The Slime Rider Interlude is selected on the rail. */
	private boolean interlude() {
		return railPos == 1;
	}

	private int highestOpenStage() {
		int best = 1;
		for (com.huntlike.interlude.InterludeDefs.Stage stage : com.huntlike.interlude.InterludeDefs.STAGES) {
			if (com.huntlike.interlude.InterludeDefs.stageOpen(ClientHuntState.data, stage)) {
				best = stage.number();
			}
		}
		return best;
	}

	private HuntData.Progress progress() {
		return ClientHuntState.data.progress(hunt().id());
	}

	private int highestUnlockedTier() {
		int rank = progress().rank();
		int best = 1;
		for (Tier tier : hunt().tiers()) {
			if (tierOpen(tier)) {
				best = tier.number();
			}
		}
		return best;
	}

	/** A tier can be started once its Hunt is open, its Rank is reached and the previous tier's boss is defeated. */
	private boolean tierOpen(Tier tier) {
		HuntData.Progress progress = progress();
		return huntOpen() && progress.rank() >= tier.unlockRank() && previousDefeated(tier);
	}

	private boolean huntOpen() {
		return HuntDefs.huntUnlocked(ClientHuntState.data, hunt());
	}

	private boolean previousDefeated(Tier tier) {
		return tier.number() == 1 || progress().bossKills[tier.number() - 2] > 0;
	}

	/** The player's own quest: the running one, or the one paused while they are in a Hunting Pack. */
	private HuntData.Quest activeQuest() {
		HuntData data = ClientHuntState.data;
		if (data.quest != null) {
			return data.quest;
		}
		// The player's own quest, paused while in a pack, comes first; otherwise the pack's running quest.
		return data.pausedQuest != null ? data.pausedQuest : ClientHuntState.packQuest();
	}

	private boolean questPaused() {
		return ClientHuntState.data.quest == null && ClientHuntState.data.pausedQuest != null;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		hits.clear();
		H = Math.max(180, Math.min(MAX_H, this.height - TAB_H - 8));
		left = (this.width - W) / 2;
		top = (this.height - H - TAB_H) / 2 + TAB_H;
		ix = left + 8;
		iy = top + 8;
		iw = W - 16;
		ih = H - 16;
		px = ix + 92;
		py = iy + 8;
		pw = iw - 100;
		ph = ih - 16;

		boolean sideTab = phone || packView;
		if (sideTab && !packView) {
			drawSideTab(g, mouseX, mouseY);
		}
		drawTabs(g, mouseX, mouseY, false);
		sprite(g, "window", left, top, W, H);
		drawTabs(g, mouseX, mouseY, true);
		if (sideTab && packView) {
			drawSideTab(g, mouseX, mouseY);
		}
		sprite(g, "content", ix - 2, iy - 2, iw + 4, ih + 4);
		sprite(g, "portal", ix, iy, iw, ih);
		if (packView) {
			drawPack(g, mouseX, mouseY);
			return;
		}
		drawRail(g, mouseX, mouseY);

		boolean questRunning = activeQuest() != null;
		interludeTokenButton = null;
		if (tab == 0 && questRunning) {
			drawActiveQuest(g, mouseX, mouseY);
		} else if (panelOpen && interlude()) {
			if (tab == 0) {
				drawInterludeCard(g, mouseX, mouseY);
			} else {
				drawInterludeOther(g);
			}
		} else if (panelOpen) {
			switch (tab) {
				case 0 -> drawBossCard(g, mouseX, mouseY);
				case 1 -> drawGear(g, mouseX, mouseY);
				default -> drawRanks(g, mouseX, mouseY);
			}
		} else if (!hintSeen()) {
			g.text(this.font, Component.literal("Click a symbol or press Enter"), px + 8, iy + ih - 14, TEXT_DIM, false);
		}
		if (tab == 0 && !interlude()) {
			drawDailyCharges(g, mouseX, mouseY);
		}
		if (dropsHover != null) {
			drawDropsPanel(g, dropsHover, mouseX, mouseY);
			dropsHover = null;
		}
	}

	// ---------------------------------------------------------------- Daily Quest charges

	/** Five stars in the bottom-left corner: pink for each charge left, grey once used (from right to left). */
	private void drawDailyCharges(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean unlocked = progress().rank() >= HuntData.DAILY_UNLOCK_RANK;
		int charges = unlocked ? Math.max(0, Math.min(HuntData.DAILY_CHARGES, ClientHuntState.data.dailyCharges)) : 0;
		MutableComponent stars = Component.empty();
		for (int i = 0; i < HuntData.DAILY_CHARGES; i++) {
			stars.append(i < charges ? colored("\u2605", HuntDefs.PINK) : colored("\u2606", 0xFF808080));
		}
		int w = this.font.width(stars) + 8;
		int h = 14;
		int x = ix + 4;
		int y = iy + ih - h - 4;
		sprite(g, "panel_dark", x, y, w, h);
		g.text(this.font, stars, x + 4, y + 3, WHITE, false);
		buttonCenters.put("Daily Quest Charges", new int[] {x + w / 2, y + h / 2});
		if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
			List<Component> lines = new ArrayList<>();
			lines.add(colored("Daily Quest Charges", HuntDefs.PINK));
			for (FormattedCharSequence line : this.font.split(colored(
					"The first five hunting quests you start will yield more XP and have higher odds of a rare drop!", TEXT_GRAY), 200)) {
				lines.add(toComponent(line));
			}
			if (unlocked) {
				lines.add(colored("Refills at sunrise.", TEXT_DIM));
			} else {
				lines.add(colored("Unlocks at " + hunt().rankName(HuntData.DAILY_UNLOCK_RANK) + " (Rank I).", HuntDefs.RED));
			}
			g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
		}
	}

	private static Component toComponent(FormattedCharSequence sequence) {
		MutableComponent out = Component.empty();
		sequence.accept((index, style, codePoint) -> {
			out.append(Component.literal(new String(Character.toChars(codePoint))).withStyle(style));
			return true;
		});
		return out;
	}

	// ---------------------------------------------------------------- the "click a symbol" hint, shown until first used

	/** The hint is remembered per world or server, in the player's Hunt data. */
	private static boolean hintSeen() {
		return ClientHuntState.data.hintSeen;
	}

	private static void markHintSeen() {
		if (hintSeen()) {
			return;
		}
		ClientHuntState.data.hintSeen = true;
		ClientPlayNetworking.send(new HuntPayloads.Action("hint_seen", "animal", 0));
	}


	/** Unselected tabs are drawn behind the window, the selected tab in front so it joins the window. */
	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean selectedPass) {
		int tabW = W / 3;
		for (int i = 0; i < 3; i++) {
			boolean selected = i == tab && !packView;
			if (selected != selectedPass) {
				continue;
			}
			int x = left + i * tabW;
			int y = top - TAB_H;
			if (selected) {
				sprite(g, "tab_selected", x, y, tabW, TAB_H + 4);
			} else {
				sprite(g, "tab", x + 1, y + 2, tabW - 2, TAB_H);
			}
			Component label = Component.literal(TABS[i]);
			g.text(this.font, label, x + (tabW - this.font.width(label)) / 2, y + (selected ? 7 : 8), TAB_TEXT, false);
			if (!selectedPass) {
				int index = i;
				hits.add(new Hit(x, y, tabW, TAB_H, () -> {
					closePackOverlays();
					packView = false;
					switchTab(index);
				}));
			}
		}
	}

	private void drawRail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int cx = ix + 46;
		int cy = iy + ih / 2;
		// Slanted rail: leans right toward the selected symbol, then back. Two straight, smooth segments.
		line(g, railX(iy, cx, cy), iy, cx, cy, RAIL);
		line(g, cx, cy, railX(iy + ih, cx, cy), iy + ih, RAIL);
		// The Hunts keep their 56-pixel spacing; the Interlude is a small orb halfway between them.
		int interludeY = 0;
		for (int pos = 0; pos < RAIL_SIZE; pos++) {
			int sy = cy + RAIL_COORDS[pos] - RAIL_COORDS[railPos];
			int offset = pos == railPos ? 0 : 1;
			int size = offset == 0 ? 32 : 22;
			if (pos == 1) {
				interludeY = sy;
				continue;
			}
			int index = pos == 0 ? 0 : 1;
			// Each symbol sits centered on the rail.
			int x0 = Math.round(railX(sy, cx, cy)) - size / 2;
			int y0 = sy - size / 2;
			boolean hover = mouseX >= x0 && mouseX < x0 + size && mouseY >= y0 && mouseY < y0 + size;
			sprite(g, hover ? "symbol_hover" : "symbol", x0 - 1, y0 - 1, size + 2, size + 2);
			Hunt railHunt = HuntDefs.HUNTS.get(index);
			boolean locked = !HuntDefs.huntUnlocked(ClientHuntState.data, railHunt);
			g.blit(RenderPipelines.GUI_TEXTURED, symbol(railHunt), x0 + 3, y0 + 3, 0.0F, 0.0F, size - 6, size - 6,
					32, 32, 32, 32);
			if (locked) {
				// Locked Hunts: a dark veil over the symbol and a padlock in the corner.
				g.fill(x0, y0, x0 + size, y0 + size, 0x90101010);
				int lw = offset == 0 ? 15 : 10;
				int lh = offset == 0 ? 21 : 14;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK, x0 + (size - lw) / 2, y0 + (size - lh) / 2, lw, lh);
			}
			if (offset == 0) {
				hits.add(new Hit(x0, y0, size, size, this::openPanel));
				if (hover) {
					if (locked) {
						g.setComponentTooltipForNextFrame(this.font, List.of(
								Component.literal(railHunt.name() + " Hunt").withColor(railHunt.color()),
								colored("Locked: unlocks at " + HuntDefs.ANIMAL.rankName(HuntDefs.huntUnlockRank(railHunt))
										+ " (Animal Rank " + HuntDefs.ROMAN[HuntDefs.huntUnlockRank(railHunt)] + ")", HuntDefs.RED)),
								mouseX, mouseY);
					} else {
						g.setTooltipForNextFrame(this.font, Component.literal(railHunt.name() + " Hunt").withColor(railHunt.color()),
								mouseX, mouseY);
					}
				}
			}
		}
		drawInterludeSymbol(g, Math.round(railX(interludeY, cx, cy)), interludeY, railPos == 1 ? 16 : 10, railPos == 1, mouseX, mouseY);
	}

	/** The Interlude's orb on the rail, between the Animal and Cluster symbols. */
	private void drawInterludeSymbol(GuiGraphicsExtractor g, int x, int y, int size, boolean selected, int mouseX, int mouseY) {
		int x0 = x - size / 2;
		int y0 = y - size / 2;
		boolean hover = mouseX >= x0 - 2 && mouseX < x0 + size + 2 && mouseY >= y0 - 2 && mouseY < y0 + size + 2;
		boolean locked = !com.huntlike.interlude.InterludeDefs.open(ClientHuntState.data);
		// Just the orb, no box: a locked orb is drawn darker instead of under a veil.
		g.blit(RenderPipelines.GUI_TEXTURED, INTERLUDE, x0, y0, 0.0F, 0.0F, size, size, 16, 16, 16, 16,
				locked ? 0xFF505050 : 0xFFFFFFFF);
		if (selected) {
			hits.add(new Hit(x0 - 2, y0 - 2, size + 4, size + 4, this::openPanel));
		}
		if (hover) {
			List<Component> tip = new ArrayList<>();
			tip.add(colored("Interlude: " + com.huntlike.interlude.InterludeDefs.NAME, com.huntlike.interlude.InterludeDefs.COLOR));
			if (locked) {
				tip.add(colored("Locked: unlocks at Animal Rank " + HuntDefs.ROMAN[com.huntlike.interlude.InterludeDefs.STAGES.get(0)
						.animalRank()], HuntDefs.RED));
			}
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	/** Where the rail passes at height y: at cx in the middle, 12 pixels further left at the top and bottom. */
	private float railX(float y, int cx, int cy) {
		return cx - 12.0F * Math.abs(y - cy) / (ih / 2.0F);
	}

	/** A 2-pixel line drawn as one rotated bar, so slanted lines stay smooth instead of stair-stepped. */
	private static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		int length = Math.round((float) Math.sqrt(dx * dx + dy * dy));
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, -1, length, 1, color);
		g.pose().popMatrix();
	}

	/** Two iron chains crossed over a locked area (vanilla's chain texture, tiled along each diagonal). */
	private static void drawChains(GuiGraphicsExtractor g, int x, int y, int w, int h, float scale) {
		g.enableScissor(x, y, x + w, y + h);
		float length = (float) Math.sqrt(w * w + h * h) / scale;
		int segment = 16;
		int count = (int) Math.ceil(length / segment) + 1;
		for (int side = -1; side <= 1; side += 2) {
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2.0F, y + h / 2.0F);
			g.pose().rotate((float) Math.atan2(h, side * w) - (float) (Math.PI / 2));
			g.pose().scale(scale, scale);
			for (int i = 0; i < count; i++) {
				g.blit(RenderPipelines.GUI_TEXTURED, CHAIN, -2, (int) (-length / 2) + i * segment - 2, 0.0F, 0.0F, 3, segment, 16, 16);
			}
			g.pose().popMatrix();
		}
		g.disableScissor();
	}

	private void panelBox(GuiGraphicsExtractor g) {
		sprite(g, "panel", px, py, pw, ph);
	}

	/** Draws a button, registers its click and returns whether the mouse is over it. */
	private boolean button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, boolean enabled,
			int mouseX, int mouseY, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		buttonCenters.put(label.getString(), new int[] {x + w / 2, y + h / 2});
		g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : hover ? BUTTON_HIGHLIGHTED : BUTTON_SPRITE, x, y, w, h);
		g.centeredText(this.font, label, x + w / 2, y + (h - 8) / 2, enabled ? WHITE : 0xFFA0A0A0);
		if (enabled && action != null) {
			hits.add(new Hit(x, y, w, h, action));
		}
		return hover;
	}

	/** Draws one of HuntLike's GUI sprites (assets/huntlike/textures/gui/sprites/menu/<name>.png). */
	private static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HuntLike.id("menu/" + name), x, y, w, h);
	}

	private static MutableComponent colored(String text, int rgb) {
		return Component.literal(text).withColor(rgb);
	}

	// ---------------------------------------------------------------- Hunting Pack

	private static final int SIDE_W = 20;
	private static final int SIDE_H = 96;
	private static final int HEAD = 48;
	/** A head in the members box: 24 pixels, with room around it. */
	private static final int CELL = 34;
	private static final int FACE = 24;
	private static final int BOX_ROWS = 2;
	private static final int ICON = 24;

	/** The "Hunting Pack" tab on the window's left side (A-Phone only), text reading upwards. */
	private void drawSideTab(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = left - SIDE_W + (packView ? 3 : 1);
		int y = top + (H - SIDE_H) * 2 / 3;
		int w = SIDE_W + (packView ? 1 : 0);
		sprite(g, "tab", x, y, w, SIDE_H);
		Component label = Component.literal("Hunting Pack");
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2.0F - 4.0F, y + SIDE_H / 2.0F);
		g.pose().rotate((float) (-Math.PI / 2));
		g.text(this.font, label, -this.font.width(label) / 2, 0, TAB_TEXT, false);
		g.pose().popMatrix();
		buttonCenters.put("Hunting Pack", new int[] {x + w / 2, y + SIDE_H / 2});
		hits.add(new Hit(x, y, w, SIDE_H, () -> {
			closePackOverlays();
			packView = !packView;
		}));
	}

	private void closePackOverlays() {
		popupFor = null;
		inboxOpen = false;
		stashOpen = false;
		editField = 0;
	}

	private void submitEdit() {
		if (editField == 1 && !editText.isBlank()) {
			ClientPlayNetworking.send(new HuntPayloads.Action("pack_name", editText.trim(), 0));
		} else if (editField == 2) {
			int max = editText.isEmpty() ? 0 : Integer.parseInt(editText);
			ClientPlayNetworking.send(new HuntPayloads.Action("pack_max", "", max));
		}
		editField = 0;
	}

	private String myId() {
		return this.minecraft.player == null ? "" : this.minecraft.player.getUUID().toString();
	}

	private List<CompoundTagView> packMembers() {
		List<CompoundTagView> out = new ArrayList<>();
		net.minecraft.nbt.ListTag list = ClientHuntState.pack.getListOrEmpty("members");
		for (int i = 0; i < list.size(); i++) {
			net.minecraft.nbt.CompoundTag m = list.getCompoundOrEmpty(i);
			out.add(new CompoundTagView(m.getStringOr("id", ""), m.getStringOr("name", "?"), m.getBooleanOr("online", false)));
		}
		if (out.isEmpty() && this.minecraft.player != null) {
			out.add(new CompoundTagView(myId(), this.minecraft.player.getName().getString(), true));
		}
		return out;
	}

	private record CompoundTagView(String id, String name, boolean online) {
	}

	private void face(GuiGraphicsExtractor g, String id, int x, int y, int size, boolean online) {
		net.minecraft.world.entity.player.PlayerSkin skin;
		java.util.UUID uuid;
		try {
			uuid = java.util.UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return;
		}
		var connection = this.minecraft.getConnection();
		var info = connection == null ? null : connection.getPlayerInfo(uuid);
		skin = info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(uuid);
		net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
		if (!online) {
			g.fill(x, y, x + size, y + size, 0x99000000);
		}
	}

	private void drawPack(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		net.minecraft.nbt.CompoundTag pack = ClientHuntState.pack;
		boolean leader = ClientHuntState.packLeader();
		boolean grouped = ClientHuntState.grouped();
		HuntData.Quest quest = ClientHuntState.packQuest();
		List<CompoundTagView> members = packMembers();
		String leaderId = pack.getStringOr("leader", myId());
		CompoundTagView leaderView = members.stream().filter(m -> m.id().equals(leaderId)).findFirst().orElse(members.get(0));
		String packName = pack.getStringOr("name", leaderView.name() + "'s Group");

		int x0 = ix + 4;
		int y0 = iy + 4;
		int w0 = iw - 8;
		int h0 = ih - 8;
		// The vanilla inventory window look (light grey, bevelled), with dark text like a container screen.
		sprite(g, "panel_light", x0, y0, w0, h0);

		// The leader's big head.
		int hx = x0 + 8;
		int hy = y0 + 8;
		inset(g, hx - 1, hy - 1, HEAD + 2, HEAD + 2);
		face(g, leaderView.id(), hx, hy, HEAD, leaderView.online());
		boolean hoverHead = mouseX >= hx && mouseX < hx + HEAD && mouseY >= hy && mouseY < hy + HEAD;
		hits.add(new Hit(hx, hy, HEAD, HEAD, () -> openPopup("leader", mouseX, mouseY)));

		// Name, members, active Hunt, quota.
		int tx = hx + HEAD + 10;
		int ty = hy + 1;
		if (editField == 1) {
			sprite(g, "panel_dark", tx - 3, ty - 3, w0 - (tx - x0) - 8, 14);
			boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
			g.text(this.font, colored(editText + (caret ? "_" : ""), WHITE), tx, ty, WHITE, false);
		} else {
			// Long names stop short of the Stash and Invites buttons in the corner.
			int room = x0 + w0 - 2 * ICON - 14 - tx;
			String shown = this.font.width(packName) <= room ? packName
					: this.font.plainSubstrByWidth(packName, room - this.font.width("...")) + "...";
			g.text(this.font, colored(shown, LABEL), tx, ty, LABEL, false);
			if (leader) {
				hits.add(new Hit(tx, ty - 2, Math.max(40, this.font.width(packName)), 11, () -> {
					closePackOverlays();
					editField = 1;
					editText = packName;
				}));
			}
		}
		ty += 13;
		int max = pack.getIntOr("max", 0);
		String count = editField == 2 ? editText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "")
				: max > 0 ? members.size() + "/" + max : String.valueOf(members.size());
		Component membersLine = colored("Members: ", LABEL).append(colored(count, LABEL));
		g.text(this.font, membersLine, tx, ty, WHITE, false);
		boolean hoverMembers = mouseX >= tx && mouseX < tx + this.font.width(membersLine) + 4 && mouseY >= ty - 2 && mouseY < ty + 9;
		if (leader && editField != 2) {
			hits.add(new Hit(tx, ty - 2, this.font.width(membersLine) + 4, 11, () -> {
				closePackOverlays();
				editField = 2;
				editText = "";
			}));
		}
		ty += 13;
		Component huntLine;
		if (quest == null) {
			huntLine = colored("None", 0xFF707070);
		} else {
			Hunt hunt = HuntDefs.hunt(quest.hunt);
			Tier tier = hunt.tier(quest.tier);
			huntLine = colored(hunt.name() + " ", hunt.color()).append(colored("[Tier " + tier.roman() + "] ", tier.color()))
					.append(colored(tier.bossName() + mutationSuffix(quest), tier.color()));
		}
		g.text(this.font, colored("Active Hunt: ", LABEL), tx, ty, LABEL, false);
		// The Hunt's bright colors keep their shadow so they read on the light grey.
		g.text(this.font, huntLine, tx + this.font.width("Active Hunt: "), ty, WHITE, quest != null);
		ty += 13;
		if (quest != null) {
			int barX = tx;
			int barW = x0 + w0 - 8 - barX;
			float fill = quest.phase == HuntData.PHASE_KILLING ? quest.kills / (float) Math.max(1, quest.quota) : 1.0F;
			Component label = quest.phase == HuntData.PHASE_KILLING ? colored("Quota " + quest.kills + "/" + quest.quota, WHITE)
					: quest.phase == HuntData.PHASE_SUMMONING ? colored("The boss is being summoned...", WHITE) : colored("Boss alive", WHITE);
			sprite(g, "bar_background", barX, ty, barW, 12);
			int filled = Math.round(barW * fill);
			if (filled > 0) {
				sprite(g, "bar_quota", barX, ty, filled, 12);
			}
			g.centeredText(this.font, label, barX + barW / 2, ty + 2, WHITE);
		}

		// Stash and Invites: the panel's top-right corner.
		int bx = x0 + w0 - ICON - 4;
		int by = y0 + 4;
		drawIconButton(g, bx, by, "Invites", mouseX, mouseY, () -> {
			closePackOverlays();
			inboxOpen = true;
			ClientPlayNetworking.send(new HuntPayloads.Action("pack_seen", "", 0));
			ClientHuntState.invitesNew = false;
		});
		// The user's envelope the basket's size (16 wide), centered; the dotted one is the same envelope 1 pixel in.
		if (ClientHuntState.invitesNew) {
			sprite(g, "invites_new", bx + 3, by + 5, 18, 13);
		} else {
			sprite(g, "invites", bx + 4, by + 6, 16, 11);
		}
		int sx = bx - ICON - 3;
		drawIconButton(g, sx, by, "Stash", mouseX, mouseY, () -> {
			closePackOverlays();
			stashOpen = true;
		});
		sprite(g, "stash", sx + 4, by + 4, 16, 16);

		// The members box: two rows of heads, scrolled with the mouse wheel.
		int boxX = x0 + 6;
		int boxW = w0 - 12;
		int boxH = BOX_ROWS * CELL + 6;
		int boxTop = y0 + h0 - 6 - boxH;
		g.text(this.font, colored(packPlayers ? "Players" : "Members", LABEL), boxX + 2, boxTop - 11, LABEL, false);
		inset(g, boxX, boxTop, boxW, boxH);
		packBox = new int[] {boxX, boxTop, boxW, boxH};

		List<CompoundTagView> entries = new ArrayList<>();
		if (packPlayers) {
			var connection = this.minecraft.getConnection();
			if (connection != null) {
				for (var info : connection.getListedOnlinePlayers()) {
					String id = info.getProfile().id().toString();
					if (members.stream().noneMatch(m -> m.id().equals(id))) {
						entries.add(new CompoundTagView(id, info.getProfile().name(), true));
					}
				}
			}
		} else {
			for (CompoundTagView member : members) {
				if (!member.id().equals(leaderId)) {
					if (member.id().equals(myId())) {
						entries.add(0, member);
					} else {
						entries.add(member);
					}
				}
			}
		}
		int perRow = Math.max(1, (boxW - 10) / CELL);
		int cells = entries.size() + (leader && quest == null ? 1 : 0);
		int rows = (cells + perRow - 1) / perRow;
		int maxScroll = Math.max(0, rows * CELL - (boxH - 6));
		packScroll = Math.max(0, Math.min(maxScroll, packScroll));
		int left0 = boxX + (boxW - perRow * CELL) / 2 + (CELL - FACE) / 2;
		String hoverName = null;
		g.enableScissor(boxX + 1, boxTop + 1, boxX + boxW - 1, boxTop + boxH - 1);
		for (int i = 0; i < cells; i++) {
			int cx = left0 + (i % perRow) * CELL;
			int cy = boxTop + 3 + (CELL - FACE) / 2 + (i / perRow) * CELL - packScroll;
			if (cy + FACE < boxTop || cy > boxTop + boxH) {
				continue;
			}
			boolean inBox = mouseY >= boxTop && mouseY < boxTop + boxH;
			boolean hover = inBox && mouseX >= cx && mouseX < cx + FACE && mouseY >= cy && mouseY < cy + FACE;
			if (i < entries.size()) {
				CompoundTagView entry = entries.get(i);
				g.fill(cx - 1, cy - 1, cx + FACE + 1, cy + FACE + 1, hover ? 0xFFFFFFFF : 0xFF202020);
				face(g, entry.id(), cx, cy, FACE, entry.online());
				if (hover) {
					hoverName = entry.name() + (entry.online() ? "" : " (offline)") + (packPlayers ? " - click to invite" : "");
				}
				if (inBox) hits.add(new Hit(cx, cy, FACE, FACE, packPlayers ? () -> {
					ClientPlayNetworking.send(new HuntPayloads.Action("pack_invite", entry.id(), 0));
					packPlayers = false;
				} : () -> openPopup(entry.id(), mouseX, mouseY)));
			} else {
				// The + (or - to go back) for the leader.
				g.fill(cx - 1, cy - 1, cx + FACE + 1, cy + FACE + 1, hover ? 0xFFFFFFFF : 0xFFA0A0A0);
				g.fill(cx, cy, cx + FACE, cy + FACE, 0xFF6F6F6F);
				g.centeredText(this.font, colored(packPlayers ? "-" : "+", WHITE), cx + (FACE) / 2, cy + (FACE) / 2 - 4, WHITE);
				buttonCenters.put("Pack +", new int[] {cx + (FACE) / 2, cy + (FACE) / 2});
				if (inBox) hits.add(new Hit(cx, cy, FACE, FACE, () -> {
					closePackOverlays();
					packPlayers = !packPlayers;
					packScroll = 0;
				}));
				if (hover) {
					hoverName = packPlayers ? "Back to the members" : "Invite players";
				}
			}
		}
		g.disableScissor();
		if (maxScroll > 0) {
			// A thin scrollbar on the box's right edge.
			int trackH = boxH - 4;
			int thumbH = Math.max(8, trackH * (boxH - 6) / (rows * CELL));
			int thumbY = boxTop + 2 + (trackH - thumbH) * packScroll / maxScroll;
			g.fill(boxX + boxW - 4, boxTop + 2, boxX + boxW - 2, boxTop + 2 + trackH, 0xFF1A1A1A);
			g.fill(boxX + boxW - 4, thumbY, boxX + boxW - 2, thumbY + thumbH, 0xFFA0A0A0);
		}
		if (entries.isEmpty() && packPlayers) {
			g.text(this.font, colored("No other players online.", 0xFFE0E0E0), left0 + CELL + 4, boxTop + 3 + CELL / 2 - 4, WHITE, false);
		}

		// Overlays take every click while open.
		if (popupFor != null) {
			drawPopup(g, mouseX, mouseY, leader, grouped, quest != null, leaderId);
		} else if (inboxOpen) {
			drawInbox(g, mouseX, mouseY);
		} else if (stashOpen) {
			drawStash(g, mouseX, mouseY);
		} else if (hoverName != null) {
			g.setComponentTooltipForNextFrame(this.font, List.of(colored(hoverName, WHITE)), mouseX, mouseY);
		} else if (hoverHead) {
			g.setComponentTooltipForNextFrame(this.font, List.of(colored(leaderView.name() + " (leader)", HuntDefs.GOLD)), mouseX, mouseY);
		} else if (hoverMembers && leader) {
			g.setComponentTooltipForNextFrame(this.font, List.of(colored("Click to set the most members (0 = no limit).", TEXT_GRAY)),
					mouseX, mouseY);
		}
	}

	/** Vanilla container text color. */
	private static final int LABEL = 0xFF404040;

	/** A sunken area like an inventory slot: dark top and left, white bottom and right, mid grey inside. */
	private static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
		g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
		g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
		g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
		g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
	}

	/** " | Mineral" once a mutated boss is out. */
	private static String mutationSuffix(HuntData.Quest quest) {
		return quest == null || quest.mutation.isEmpty() ? "" : " | " + quest.mutation;
	}

	private void drawIconButton(GuiGraphicsExtractor g, int x, int y, String name, int mouseX, int mouseY, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + ICON && mouseY >= y && mouseY < y + ICON;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HIGHLIGHTED : BUTTON_SPRITE, x, y, ICON, ICON);
		buttonCenters.put(name, new int[] {x + ICON / 2, y + ICON / 2});
		hits.add(new Hit(x, y, ICON, ICON, action));
		if (hover && popupFor == null && !inboxOpen && !stashOpen) {
			g.setComponentTooltipForNextFrame(this.font, List.of(colored(name, WHITE)), mouseX, mouseY);
		}
	}

	private void openPopup(String target, int mouseX, int mouseY) {
		closePackOverlays();
		popupFor = target;
		popupX = mouseX;
		popupY = mouseY;
	}

	/** A player's options: the leader can kick or promote; the big head disbands or leaves; your own head leaves. */
	private void drawPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean leader, boolean grouped, boolean questRunning,
			String leaderId) {
		List<String> options = new ArrayList<>();
		if ("leader".equals(popupFor)) {
			if (leader && grouped) {
				options.add("Disband");
				options.add("Leave");
			}
		} else if (leader) {
			options.add("Kick");
			if (!questRunning) {
				options.add("Promote to leader");
			}
		} else if (popupFor.equals(myId())) {
			options.add("Leave");
		}
		hits.clear();
		hits.add(new Hit(0, 0, this.width, this.height, this::closePackOverlays));
		if (options.isEmpty()) {
			popupFor = null;
			return;
		}
		int w = 0;
		for (String option : options) {
			w = Math.max(w, this.font.width(option) + 12);
		}
		int x = Math.min(popupX, this.width - w - 4);
		int y = Math.min(popupY, this.height - options.size() * 16 - 8);
		sprite(g, "panel_dark", x - 3, y - 3, w + 6, options.size() * 16 + 4);
		List<Hit> mine = new ArrayList<>();
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			String target = popupFor;
			boolean disabled = questRunning && (option.equals("Leave") || option.equals("Disband"));
			button(g, x, y + i * 16, w, 15, Component.literal(option), !disabled, mouseX, mouseY, null);
			if (!disabled) {
				mine.add(new Hit(x, y + i * 16, w, 15, () -> {
					switch (option) {
						case "Kick" -> ClientPlayNetworking.send(new HuntPayloads.Action("pack_kick", target, 0));
						case "Promote to leader" -> ClientPlayNetworking.send(new HuntPayloads.Action("pack_promote", target, 0));
						case "Leave" -> ClientPlayNetworking.send(new HuntPayloads.Action("pack_leave", "", 0));
						default -> ClientPlayNetworking.send(new HuntPayloads.Action("pack_disband", "", 0));
					}
					closePackOverlays();
				}));
			}
		}
		hits.addAll(0, mine);
	}

	private int[] overlayBox(int w, int h) {
		return new int[] {(this.width - w) / 2, (this.height - h) / 2, w, h};
	}

	/** The invites inbox: each invite with Accept (greyed, with the reason, when it can't be taken) and Decline. */
	private void drawInbox(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<net.minecraft.nbt.CompoundTag> invites = ClientHuntState.invites;
		int rows = Math.max(1, invites.size());
		int[] box = overlayBox(240, 30 + rows * 24);
		hits.clear();
		List<Hit> mine = new ArrayList<>();
		sprite(g, "window", box[0], box[1], box[2], box[3]);
		g.centeredText(this.font, colored("Invites", TAB_TEXT), box[0] + box[2] / 2, box[1] + 7, TAB_TEXT);
		if (invites.isEmpty()) {
			g.centeredText(this.font, colored("No invites for you right now...", 0xFF606060), box[0] + box[2] / 2, box[1] + 26, TAB_TEXT);
		}
		List<Component> tooltip = null;
		for (int i = 0; i < invites.size(); i++) {
			net.minecraft.nbt.CompoundTag invite = invites.get(i);
			int y = box[1] + 22 + i * 24;
			sprite(g, "panel_dark", box[0] + 6, y, box[2] - 12, 22);
			int seconds = invite.getIntOr("seconds", 0);
			g.text(this.font, colored(invite.getStringOr("name", "?"), WHITE), box[0] + 10, y + 3, WHITE, false);
			g.text(this.font, colored("from " + invite.getStringOr("from", "?") + String.format(" - %d:%02d", seconds / 60, seconds % 60),
					TEXT_GRAY), box[0] + 10, y + 12, WHITE, false);
			String packId = invite.getStringOr("pack", "");
			String blocked = invite.getStringOr("blocked", "");
			int ax = box[0] + box[2] - 108;
			boolean hoverAccept = button(g, ax, y + 3, 48, 16, Component.literal("Accept"), blocked.isEmpty(), mouseX, mouseY, null);
			if (blocked.isEmpty()) {
				mine.add(new Hit(ax, y + 3, 48, 16, () -> {
					ClientPlayNetworking.send(new HuntPayloads.Action("pack_accept", packId, 0));
					closePackOverlays();
				}));
			} else if (hoverAccept) {
				tooltip = List.of(colored(blocked, HuntDefs.RED));
			}
			button(g, ax + 52, y + 3, 50, 16, Component.literal("Decline"), true, mouseX, mouseY, null);
			mine.add(new Hit(ax + 52, y + 3, 50, 16, () -> ClientPlayNetworking.send(new HuntPayloads.Action("pack_decline", packId, 0))));
		}
		hits.addAll(mine);
		hits.add(new Hit(0, 0, this.width, this.height, this::closePackOverlays));
		if (tooltip != null) {
			g.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
		}
	}

	/** The Stash: rewards that came while the player was away. Items can only be taken out (click one). */
	private void drawStash(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<ItemStack> stash = ClientHuntState.stash;
		int columns = 9;
		int rows = Math.max(1, (stash.size() + columns - 1) / columns);
		int[] box = overlayBox(columns * 18 + 16, 30 + rows * 18 + 4);
		hits.clear();
		List<Hit> mine = new ArrayList<>();
		sprite(g, "window", box[0], box[1], box[2], box[3]);
		g.centeredText(this.font, colored("Stash", TAB_TEXT), box[0] + box[2] / 2, box[1] + 7, TAB_TEXT);
		if (stash.isEmpty()) {
			g.centeredText(this.font, colored("Your Stash is empty.", 0xFF606060), box[0] + box[2] / 2, box[1] + 26, TAB_TEXT);
		}
		ItemStack hovered = null;
		for (int i = 0; i < stash.size(); i++) {
			int x = box[0] + 8 + (i % columns) * 18;
			int y = box[1] + 22 + (i / columns) * 18;
			g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
			g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
			ItemStack stack = stash.get(i);
			g.item(stack, x + 1, y + 1);
			g.itemDecorations(this.font, stack, x + 1, y + 1);
			if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
				hovered = stack;
			}
			int index = i;
			mine.add(new Hit(x, y, 18, 18, () -> ClientPlayNetworking.send(new HuntPayloads.Action("stash_take", "", index))));
		}
		hits.addAll(mine);
		hits.add(new Hit(0, 0, this.width, this.height, this::closePackOverlays));
		if (hovered != null) {
			g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	// ---------------------------------------------------------------- Quests tab

	private void drawBossCard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		panelBox(g);
		Hunt hunt = hunt();
		Tier tier = hunt.tier(questTier);
		HuntData.Progress progress = progress();
		boolean unlocked = tierOpen(tier);
		int cx = px + pw / 2;
		int y = py + 5;

		g.centeredText(this.font, colored(hunt.name() + " Hunt", hunt.color()), cx, y, WHITE);
		y += 11;
		g.centeredText(this.font, colored("<  ", TEXT_GRAY).append(colored("Tier " + tier.roman(), tier.color()))
				.append(colored("  >", TEXT_GRAY)), cx, y, WHITE);
		hits.add(new Hit(cx - 40, y - 2, 20, 12, () -> changeTier(-1)));
		hits.add(new Hit(cx + 20, y - 2, 20, 12, () -> changeTier(1)));
		y += 12;
		g.centeredText(this.font, bossName(tier.bossName(), tier.color(), unlocked), cx, y, WHITE);
		y += 10;
		g.centeredText(this.font, colored(tier.bossTitle(), TEXT_GRAY), cx, y, WHITE);
		y += 12;
		int statsTop = y;
		boolean affordable = progress.tokens >= tier.tokenCost();
		g.text(this.font, colored("Tokens: ", TEXT_GRAY).append(colored(String.valueOf(tier.tokenCost()), WHITE))
				.append(colored(" [" + progress.tokens + "]", affordable ? HuntDefs.LIME : HuntDefs.RED)), px + 6, y, WHITE, false);
		y += 10;
		g.text(this.font, colored("Experience: ", TEXT_GRAY).append(colored(tier.xp() + " XP", HuntDefs.XP_GREEN)),
				px + 6, y, WHITE, false);
		y += 12;

		if (!unlocked) {
			String lock = !huntOpen()
					? "The " + hunt.name() + " Hunt unlocks at " + HuntDefs.ANIMAL.rankName(HuntDefs.huntUnlockRank(hunt))
							+ " (Animal Rank " + HuntDefs.ROMAN[HuntDefs.huntUnlockRank(hunt)] + ")"
					: progress.rank() < tier.unlockRank()
					? "Unlocks at " + hunt.rankName(tier.unlockRank()) + " (Rank " + HuntDefs.ROMAN[tier.unlockRank()] + ")"
					: "Defeat " + hunt.tier(tier.number() - 1).bossName() + " (Tier " + HuntDefs.ROMAN[tier.number() - 1]
							+ ") to unlock";
			for (FormattedCharSequence line : this.font.split(colored(lock, HuntDefs.RED), pw - 90)) {
				g.text(this.font, line, px + 6, y, WHITE, false);
				y += 9;
			}
		} else {
			g.text(this.font, colored("Health: ", TEXT_GRAY).append(colored(String.valueOf(tier.health()), HuntDefs.RED)),
					px + 6, y, WHITE, false);
			y += 10;
			g.text(this.font, colored("Damage: ", TEXT_GRAY).append(colored(String.valueOf(tier.melee()), HuntDefs.GOLD)),
					px + 6, y, WHITE, false);
			y += 10;
			g.text(this.font, colored("Regeneration: ", TEXT_GRAY).append(colored(tier.regen() + "/s", HuntDefs.LIME)),
					px + 6, y, WHITE, false);
			y += 12;
			drawAbilities(g, tier.abilities(), px + 4, y, pw - 8, py + ph - 26 - y);
		}
		// The boss is only shown once it has been defeated; until then, a question mark.
		if (progress.bossKills[tier.number() - 1] > 0) {
			drawBossModel(g, tier, px + pw - 84, statsTop - 4, px + pw - 6, statsTop + 54);
		} else {
			drawUnknownModel(g, px + pw - 84, statsTop - 4, px + pw - 6, statsTop + 54);
		}
		if (!huntOpen()) {
			// A locked Hunt: two chains cross its whole panel, corner to corner.
			g.nextStratum();
			drawChains(g, px, py, pw, ph, 2.0F);
		}

		int by = py + ph - 20;
		boolean defeated = progress.bossKills[tier.number() - 1] > 0;
		int startX = px + 4;
		int startW = pw - 8;
		if (defeated) {
			boolean hover = button(g, px + 4, by, 34, 16, Component.literal("Lore"), true, mouseX, mouseY, null);
			if (hover) {
				List<FormattedCharSequence> lines = new ArrayList<>();
				lines.add(colored(tier.bossName() + ", " + tier.bossTitle(), tier.color()).getVisualOrderText());
				lines.addAll(this.font.split(Component.translatable(tier.loreKey()).withColor(0xFFDDDDDD), 220));
				g.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}
			startX += 38;
			startW -= 38;
		}
		boolean canStart = unlocked && affordable && !ClientHuntState.peaceful && packAllowsStart(hunt, tier);
		if (ClientHuntState.peaceful) {
			g.text(this.font, colored("Cannot start quest because of", HuntDefs.RED), px + 4, by - 20, WHITE, false);
			g.text(this.font, colored("the world's difficulty.", HuntDefs.RED), px + 4, by - 11, WHITE, false);
		}
		boolean hoverStart = button(g, startX, by, startW, 16, Component.literal("Start [" + tier.tokenCost() + " Tokens]"), canStart,
				mouseX, mouseY, this::startQuest);
		if (hoverStart && ClientHuntState.grouped()) {
			String tip = !ClientHuntState.packLeader() ? "Only the pack's leader can start Hunts."
					: ClientHuntState.packQuest() != null ? "The pack is already on a Hunt."
					: !packAllowsStart(hunt, tier) ? com.huntlike.hunt.HuntingPacks.REQUIREMENTS : "Starts this Hunt for the whole pack.";
			g.setComponentTooltipForNextFrame(this.font, List.of(colored(tip, packAllowsStart(hunt, tier) ? TEXT_GRAY : HuntDefs.RED)),
					mouseX, mouseY);
		}
	}

	/** In a Hunting Pack only the leader starts, and only a tier every member can take (unlocked, tokens, online). */
	private static boolean packAllowsStart(Hunt hunt, Tier tier) {
		if (!ClientHuntState.grouped()) {
			return true;
		}
		if (!ClientHuntState.packLeader() || ClientHuntState.packQuest() != null) {
			return false;
		}
		byte[] ok = ClientHuntState.pack.getCompoundOrEmpty("ok").getByteArray(hunt.id()).orElse(new byte[0]);
		return tier.number() - 1 < ok.length && ok[tier.number() - 1] == 1;
	}

	/** A boss's name, scrambled until its tier (or stage) is unlocked. */
	private static Component bossName(String name, int color, boolean unlocked) {
		MutableComponent text = colored(name, color);
		return unlocked ? text : text.withStyle(style -> style.withObfuscated(true));
	}

	/** In place of a boss nobody has defeated yet: a big gray question mark. */
	private void drawUnknownModel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		g.pose().pushMatrix();
		g.pose().translate((x1 + x2) / 2.0F, (y1 + y2) / 2.0F);
		g.pose().scale(4.0F, 4.0F);
		g.centeredText(this.font, colored("?", 0x777777), 0, -4, WHITE);
		g.pose().popMatrix();
	}

	private boolean interludeCanStart() {
		com.huntlike.interlude.InterludeDefs.Stage stage = com.huntlike.interlude.InterludeDefs.stage(interludeStage);
		HuntData data = ClientHuntState.data;
		return com.huntlike.interlude.InterludeDefs.stageOpen(data, stage) && data.interludeTokens >= stage.tokenCost()
				&& !ClientHuntState.peaceful && activeQuest() == null;
	}

	/** The Slime Rider Interlude's card: stage, stats, abilities, tokens and Start. */
	private void drawInterludeCard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		panelBox(g);
		HuntData data = ClientHuntState.data;
		com.huntlike.interlude.InterludeDefs.Stage stage = com.huntlike.interlude.InterludeDefs.stage(interludeStage);
		boolean open = com.huntlike.interlude.InterludeDefs.open(data);
		boolean stageOpen = com.huntlike.interlude.InterludeDefs.stageOpen(data, stage);
		int cx = px + pw / 2;
		int y = py + 5;
		g.centeredText(this.font, colored("Interlude", com.huntlike.interlude.InterludeDefs.COLOR), cx, y, WHITE);
		y += 11;
		g.centeredText(this.font, colored("<  ", TEXT_GRAY).append(colored("Stage " + stage.roman(), stage.color()))
				.append(colored("  >", TEXT_GRAY)), cx, y, WHITE);
		hits.add(new Hit(cx - 44, y - 2, 20, 12, () -> changeTier(-1)));
		hits.add(new Hit(cx + 24, y - 2, 20, 12, () -> changeTier(1)));
		y += 12;
		g.centeredText(this.font, bossName(com.huntlike.interlude.InterludeDefs.NAME, stage.color(), stageOpen), cx, y, WHITE);
		y += 10;
		g.centeredText(this.font, colored(com.huntlike.interlude.InterludeDefs.TITLE, TEXT_GRAY), cx, y, WHITE);
		y += 12;
		int statsTop = y;
		boolean affordable = data.interludeTokens >= stage.tokenCost();
		g.text(this.font, colored("Tokens: ", TEXT_GRAY).append(colored(String.valueOf(stage.tokenCost()), WHITE))
				.append(colored(" [" + data.interludeTokens + "]", affordable ? HuntDefs.LIME : HuntDefs.RED)), px + 6, y, WHITE, false);
		y += 12;
		// Lore / Drops / Start sit one row up; the token bar is the bottom row.
		int bottom = py + ph - 44;
		if (!stageOpen) {
			String lock = !com.huntlike.interlude.InterludeDefs.ranksMet(data, stage)
					? "Unlocks at " + com.huntlike.interlude.InterludeDefs.rankText(stage)
					: "Win Stage " + HuntDefs.ROMAN[stage.number() - 1] + " to unlock";
			for (FormattedCharSequence line : this.font.split(colored(lock, HuntDefs.RED), pw - 90)) {
				g.text(this.font, line, px + 6, y, WHITE, false);
				y += 9;
			}
		} else {
			g.text(this.font, colored("Health: ", TEXT_GRAY).append(colored(String.valueOf(stage.health()), HuntDefs.RED)),
					px + 6, y, WHITE, false);
			y += 10;
			g.text(this.font, colored("Damage: ", TEXT_GRAY).append(colored(String.valueOf(stage.melee()), HuntDefs.GOLD)),
					px + 6, y, WHITE, false);
			// The abilities start where a Hunt card's do (after Experience and Regeneration), leaving the model room.
			y = statsTop + 58;
			drawAbilities(g, com.huntlike.interlude.InterludeDefs.abilities(stage.number()), px + 4, y, pw - 8, bottom - 2 - y);
		}
		boolean seen = data.progress(com.huntlike.interlude.InterludeDefs.SLIME_RIDER).bossKills[stage.number() - 1] > 0;
		if (seen) {
			drawInterludeModel(g, px + pw - 84, statsTop - 4, px + pw - 6, statsTop + 54);
		} else {
			drawUnknownModel(g, px + pw - 84, statsTop - 4, px + pw - 6, statsTop + 54);
		}
		if (!open) {
			g.nextStratum();
			drawChains(g, px, py, pw, ph, 2.0F);
		}

		// Interlude Tokens, the bottom row: 1 gold ingot = 16 (Shift: 10 = 160).
		int tokenY = py + ph - 20;
		interludeTokenButton = new int[] {px + 4, tokenY, pw - 8, 16};
		boolean tokenHover = button(g, px + 4, tokenY, pw - 8, 16, Component.literal("Buy 16 Interlude Tokens [1 Gold Ingot]"), true,
				mouseX, mouseY, null);
		if (tokenHover) {
			g.setComponentTooltipForNextFrame(this.font, List.of(colored("Click: 1 Gold Ingot -> 16 Interlude Tokens", WHITE),
					colored("Shift-click: 10 Gold Ingots -> 160 Interlude Tokens", TEXT_GRAY)), mouseX, mouseY);
		}

		int by = py + ph - 38;
		int startX = px + 4;
		int startW = pw - 8;
		boolean won = data.progress(com.huntlike.interlude.InterludeDefs.SLIME_RIDER).bossKills[stage.number() - 1] > 0;
		if (won) {
			boolean hover = button(g, startX, by, 34, 16, Component.literal("Lore"), true, mouseX, mouseY, null);
			if (hover) {
				List<FormattedCharSequence> lines = new ArrayList<>();
				lines.add(colored(com.huntlike.interlude.InterludeDefs.NAME + ", " + com.huntlike.interlude.InterludeDefs.TITLE,
						stage.color()).getVisualOrderText());
				lines.addAll(this.font.split(colored(stage.lore(), 0xFFDDDDDD), 220));
				g.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}
			startX += 38;
			startW -= 38;
		}
		boolean dropsHover = button(g, startX, by, 44, 16, Component.literal("Drops"), true, mouseX, mouseY, null);
		if (dropsHover) {
			g.setComponentTooltipForNextFrame(this.font, interludeDropLines(stage.number()), mouseX, mouseY);
		}
		startX += 48;
		startW -= 48;
		button(g, startX, by, startW, 16, Component.literal("Start [" + stage.tokenCost() + " Tokens]"), interludeCanStart(),
				mouseX, mouseY, () -> ClientPlayNetworking.send(new HuntPayloads.Action("interlude_start",
						com.huntlike.interlude.InterludeDefs.SLIME_RIDER, interludeStage)));
	}

	private List<Component> interludeDropLines(int stage) {
		List<Component> lines = new ArrayList<>();
		// Grouped by who stays for Phase II: Bartholomew's drops, then Pom's.
		lines.add(colored("Bartholomew", com.huntlike.interlude.InterludeDefs.COLOR));
		for (Drop drop : com.huntlike.interlude.InterludeDefs.bartholomewDrops(stage)) {
			lines.add(dropLine(drop));
		}
		lines.add(Component.empty());
		lines.add(colored("Pom", com.huntlike.interlude.InterludeDefs.COLOR));
		for (Drop drop : com.huntlike.interlude.InterludeDefs.pomDrops(stage)) {
			lines.add(dropLine(drop));
		}
		return lines;
	}

	private Component dropLine(Drop drop) {
		String name = drop.id().equals("leaping_potion") ? "Potion of Leaping (3:00)" : new ItemStack(drop.item()).getHoverName().getString();
		return colored(" " + name + " x" + drop.minCount(), WHITE).append(colored("  " + oneIn(drop), TEXT_GRAY));
	}

	/** A chance written as "1/X", the way the design writes it: 2/5 shows as 1/2.5. */
	private static String oneIn(Drop drop) {
		if (drop.chanceNum() == 1) {
			return "1/" + drop.chanceDen();
		}
		double x = drop.chanceDen() / (double) drop.chanceNum();
		return "1/" + (x == Math.rint(x) ? String.valueOf((long) x) : String.valueOf(Math.round(x * 100) / 100.0));
	}

	/**
	 * The Slime Rider on the card: the pair for 5 seconds (Bartholomew on Pom's back), then Bartholomew alone for 2 and
	 * Pom alone for 2, and around again. Client-only copies of the mobs.
	 */
	private void drawInterludeModel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		if (this.minecraft.level == null) {
			return;
		}
		LivingEntity pom = previews.computeIfAbsent(100, n -> {
			LivingEntity created = com.huntlike.interlude.ModInterlude.POM.create(this.minecraft.level, EntitySpawnReason.LOAD);
			if (created != null) {
				created.setId(-1100);
				created.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY,
						new ItemStack(net.minecraft.world.item.Items.GOLDEN_HORSE_ARMOR));
			}
			return created;
		});
		LivingEntity slime = previews.computeIfAbsent(101, n -> {
			com.huntlike.interlude.Bartholomew created = com.huntlike.interlude.ModInterlude.BARTHOLOMEW.create(this.minecraft.level,
					EntitySpawnReason.LOAD);
			if (created != null) {
				created.setId(-1101);
				created.setSize(2, false);
			}
			return created;
		});
		if (pom == null || slime == null) {
			return;
		}
		long millis = System.currentTimeMillis();
		double time = millis / 1000.0;
		float lookX = (x1 + x2) / 2.0F + (float) Math.sin(time * 0.7) * 80.0F;
		float lookY = (y1 + y2) / 2.0F - 10.0F;
		long cycle = millis % 9000L;
		if (cycle < 5000L) {
			// The pair: Bartholomew sits on Pom's back. Drawn first, so Pom's head stays in front of him.
			int scale = 20;
			int lift = Math.round(scale * 0.95F);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1 - lift, x2, y2 - lift, scale, 0.0625F, lookX, lookY, slime);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625F, lookX, lookY, pom);
		} else if (cycle < 7000L) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, 34, 0.0625F, lookX, lookY, slime);
		} else {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, 22, 0.0625F, lookX, lookY, pom);
		}
	}

	/** The Gear and Ranks tabs of an Interlude: nothing to buy or rank up, just its record. */
	private void drawInterludeOther(GuiGraphicsExtractor g) {
		panelBox(g);
		int cx = px + pw / 2;
		int y = py + 8;
		g.centeredText(this.font, colored("Interlude: " + com.huntlike.interlude.InterludeDefs.NAME, com.huntlike.interlude.InterludeDefs.COLOR),
				cx, y, WHITE);
		y += 16;
		String text = tab == 1 ? "Interludes have no Gear." : "Interludes have no Ranks. Kills:";
		for (FormattedCharSequence line : this.font.split(colored(text, TEXT_GRAY), pw - 16)) {
			g.centeredText(this.font, line, cx, y, WHITE);
			y += 10;
		}
		if (tab == 1) {
			return;
		}
		y += 4;
		HuntData.Progress progress = ClientHuntState.data.progress(com.huntlike.interlude.InterludeDefs.SLIME_RIDER);
		for (com.huntlike.interlude.InterludeDefs.Stage stage : com.huntlike.interlude.InterludeDefs.STAGES) {
			g.centeredText(this.font, colored("Stage " + stage.roman() + ": ", stage.color())
					.append(colored(String.valueOf(progress.bossKills[stage.number() - 1]), WHITE)), cx, y, WHITE);
			y += 11;
		}
	}

	private void drawAbilities(GuiGraphicsExtractor g, List<Ability> abilities, int x, int y, int w, int h) {
		if (h < 20) {
			return;
		}
		sprite(g, "panel_dark", x, y, w, h);
		g.text(this.font, colored("Abilities", WHITE), x + 4, y + 3, WHITE, false);
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (Ability ability : abilities) {
			if (!lines.isEmpty()) {
				lines.add(FormattedCharSequence.EMPTY);
				colors.add(WHITE);
			}
			lines.add(colored(ability.name(), HuntDefs.GOLD).getVisualOrderText());
			colors.add(WHITE);
			for (String paragraph : ability.description().split("\n")) {
				for (FormattedCharSequence line : this.font.split(colored(paragraph, TEXT_GRAY), w - 14)) {
					lines.add(line);
					colors.add(WHITE);
				}
			}
		}
		int lineH = 9;
		int viewTop = y + 14;
		int viewH = h - 16;
		int maxScroll = Math.max(0, lines.size() * lineH - viewH);
		abilityScroll = Math.max(0, Math.min(abilityScroll, maxScroll));
		g.enableScissor(x, viewTop, x + w, viewTop + viewH);
		for (int i = 0; i < lines.size(); i++) {
			int ly = viewTop + i * lineH - abilityScroll;
			g.text(this.font, lines.get(i), x + 4, ly, colors.get(i), false);
		}
		g.disableScissor();
		// Scroll bar: a dark track, with a handle when there is more to read.
		g.fill(x + w - 5, viewTop, x + w - 1, viewTop + viewH, 0xFF1A1A1A);
		if (maxScroll > 0) {
			int barH = Math.max(10, viewH * viewH / (lines.size() * lineH));
			int barY = viewTop + (viewH - barH) * abilityScroll / maxScroll;
			g.fill(x + w - 5, barY, x + w - 1, barY + barH, 0xFFC6C6C6);
			g.fill(x + w - 2, barY, x + w - 1, barY + barH, 0xFF8B8B8B);
		} else {
			g.fill(x + w - 5, viewTop, x + w - 1, viewTop + viewH, 0xFF555555);
		}
	}

	private void drawActiveQuest(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		panelBox(g);
		HuntData.Quest quest = activeQuest();
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		int cx = px + pw / 2;
		int y = py + 5;
		g.centeredText(this.font, colored(quest.isPack() ? "Hunting Pack Quest" : "Active Quest", WHITE), cx, y, WHITE);
		y += 13;
		boolean interludeQuest = com.huntlike.interlude.InterludeDefs.isInterlude(hunt);
		if (interludeQuest) {
			var stage = com.huntlike.interlude.InterludeDefs.stage(quest.tier);
			g.text(this.font, colored("Interlude ", hunt.color()).append(colored("[Stage " + stage.roman() + "]", stage.color())),
					px + 6, y, WHITE, false);
		} else {
			g.text(this.font, colored(hunt.name() + " ", hunt.color()).append(colored("[Tier " + tier.roman() + "]", tier.color())),
					px + 6, y, WHITE, false);
		}
		y += 11;
		g.text(this.font, colored("Boss: ", TEXT_GRAY).append(colored(tier.bossName() + mutationSuffix(quest), tier.color())), px + 6, y,
				WHITE, false);
		y += 11;
		if (quest.daily) {
			g.text(this.font, colored("\u2605 Daily Quest - \u00d72 XP", HuntDefs.PINK), px + 6, y, WHITE, false);
			y += 11;
		}
		y += 3;

		int barX = px + 6;
		int barW = pw - 12;
		float fill;
		Component label;
		if (questPaused()) {
			fill = quest.quota == 0 ? 0 : quest.kills / (float) quest.quota;
			label = colored("Quota paused", WHITE);
		} else if (quest.phase == HuntData.PHASE_KILLING) {
			fill = quest.quota == 0 ? 0 : quest.kills / (float) quest.quota;
			label = colored("Quota " + quest.kills + "/" + quest.quota, WHITE);
		} else if (quest.phase == HuntData.PHASE_SUMMONING) {
			fill = 1.0F;
			label = colored("The boss is being summoned...", WHITE);
		} else {
			fill = 1.0F;
			int seconds = ClientHuntState.bossTicksLeft / 20;
			label = quest.isPack() && seconds == 0 ? colored("Boss alive", WHITE)
					: colored(String.format("Boss alive - %d:%02d left", seconds / 60, seconds % 60), WHITE);
		}
		sprite(g, "bar_background", barX, y, barW, 12);
		int filled = Math.round(barW * fill);
		if (filled > 0) {
			sprite(g, "bar_quota", barX, y, filled, 12);
		}
		g.centeredText(this.font, label, cx, y + 2, WHITE);
		y += 15;
		if (ClientHuntState.peaceful) {
			for (FormattedCharSequence line : this.font.split(
					colored("Cannot progress further because of the world's difficulty.", HuntDefs.RED), pw - 12)) {
				g.text(this.font, line, px + 6, y, WHITE, false);
				y += 9;
			}
			y += 3;
		}
		g.text(this.font, colored("Huntable mobs:", TEXT_GRAY), px + 6, y, WHITE, false);
		y += 10;
		String huntable = ClientHuntState.huntable.isEmpty() ? "[None]" : String.join(", ", ClientHuntState.huntable);
		for (FormattedCharSequence line : this.font.split(colored(huntable, hunt.color()), pw - 12)) {
			g.text(this.font, line, px + 6, y, WHITE, false);
			y += 9;
		}

		int by = py + ph - 20;
		boolean hoverDrops = button(g, px + 4, by, 50, 16, Component.literal("Drops"), true, mouseX, mouseY, null);
		if (hoverDrops && interludeQuest) {
			g.setComponentTooltipForNextFrame(this.font, interludeDropLines(quest.tier), mouseX, mouseY);
		} else if (hoverDrops) {
			dropsHover = tier;
		}
		Component cancelLabel = cancelArmed ? colored("Confirm?", HuntDefs.RED) : Component.literal("Cancel");
		button(g, px + pw - 64, by, 60, 16, cancelLabel, true, mouseX, mouseY, () -> {
			if (cancelArmed) {
				cancelArmed = false;
				ClientPlayNetworking.send(new HuntPayloads.Action("cancel", hunt().id(), 0));
			} else {
				cancelArmed = true;
			}
		});
	}

	/** The boss, turning slowly, from a client-only copy of its entity. */
	private void drawBossModel(GuiGraphicsExtractor g, Tier tier, int x1, int y1, int x2, int y2) {
		if (this.minecraft.level == null) {
			return;
		}
		int key = tier.number() + (HuntDefs.isCluster(tier) ? 10 : 0);
		LivingEntity entity = previews.computeIfAbsent(key, n -> {
			LivingEntity created = tier.mob().create(this.minecraft.level, EntitySpawnReason.LOAD);
			if (created != null) {
				// A preview never joins the world; it only needs an id the renderer can read.
				created.setId(-1000 - n);
				if (created instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube) {
					cube.setSize(3, false);
				}
			}
			return created;
		});
		if (entity == null) {
			return;
		}
		double time = System.currentTimeMillis() / 1000.0;
		float lookX = (x1 + x2) / 2.0F + (float) Math.sin(time * 0.7) * 80.0F;
		float lookY = (y1 + y2) / 2.0F - 10.0F;
		int[] scales = {0, 34, 22, 19, 18, 26};
		int scale = HuntDefs.isCluster(tier) ? 22 : scales[tier.number()];
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625F, lookX, lookY, entity);
	}

	/** Drops list with item icons, drawn on top of everything. */
	private void drawDropsPanel(GuiGraphicsExtractor g, Tier tier, int mouseX, int mouseY) {
		HuntData.Progress progress = progress();
		boolean concoction = HuntDefs.luck(ClientHuntState.data, hunt().id());
		HuntData.Quest running = activeQuest();
		boolean daily = running != null && running.daily;
		List<ItemStack> icons = new ArrayList<>();
		List<Component> names = new ArrayList<>();
		for (Drop drop : HuntDefs.drops(tier)) {
			boolean seen = progress.dropsSeen.contains(drop.id());
			icons.add(seen ? new ItemStack(drop.item()) : ItemStack.EMPTY);
			String chance = drop.guaranteed() ? drop.minCount() + "-" + drop.maxCount() + " (100%)"
					: formatPercent(100.0 * HuntDefs.chance(hunt().id(), drop.chanceNum(), drop.chanceDen(), concoction, daily));
			names.add(Component.empty().append(seen ? new ItemStack(drop.item()).getStyledHoverName() : colored("Unknown", TEXT_DIM))
					.append(colored("  " + chance, TEXT_GRAY)));
		}
		if (!HuntDefs.isCluster(hunt())) {
			boolean seenStone = progress.dropsSeen.contains("soul_stone");
			icons.add(seenStone ? new ItemStack(ModItems.SOUL_STONE) : ItemStack.EMPTY);
			names.add(Component.empty().append(seenStone ? new ItemStack(ModItems.SOUL_STONE).getStyledHoverName()
					: colored("Unknown", TEXT_DIM)).append(colored("  " + formatPercent(100.0 * HuntDefs.chance("animal", 1, 100,
							concoction, daily)) + " (Spirit Lure animals)", TEXT_GRAY)));
		}
		int width = 0;
		for (Component name : names) {
			width = Math.max(width, this.font.width(name));
		}
		int rowH = 14;
		int w = width + 30;
		int h = 16 + names.size() * rowH + (concoction ? 11 : 0) + (daily ? 11 : 0);
		int x = Math.min(mouseX + 10, this.width - w - 4);
		int y = Math.max(4, mouseY - h - 4);
		g.nextStratum();
		sprite(g, "panel", x, y, w, h);
		g.text(this.font, colored("Drops", WHITE), x + 6, y + 5, WHITE, false);
		for (int i = 0; i < names.size(); i++) {
			int ry = y + 16 + i * rowH;
			if (icons.get(i).isEmpty()) {
				sprite(g, "panel_dark", x + 5, ry, 12, 12);
				g.text(this.font, colored("?", TEXT_GRAY), x + 9, ry + 2, WHITE, false);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(x + 5, ry);
				g.pose().scale(0.75F, 0.75F);
				g.item(icons.get(i), 0, 0);
				g.pose().popMatrix();
			}
			g.text(this.font, names.get(i), x + 22, ry + 2, WHITE, false);
		}
		if (concoction) {
			g.text(this.font, colored(HuntDefs.isCluster(hunt()) ? "Warm and Slimy Beef Jerky: odds improved" : "Mystery Concoction: odds improved", 0xFF55FF), x + 6, y + h - 11 - (daily ? 11 : 0), WHITE, false);
		}
		if (daily) {
			g.text(this.font, colored("Daily Quest: rare-drop odds +50%", HuntDefs.PINK), x + 6, y + h - 11, WHITE, false);
		}
	}

	private List<Component> dropLines(Tier tier) {
		HuntData.Progress progress = progress();
		List<Component> lines = new ArrayList<>();
		lines.add(colored("Drops", WHITE));
		for (Drop drop : HuntDefs.drops(tier)) {
			boolean seen = progress.dropsSeen.contains(drop.id());
			Component name = seen ? new ItemStack(drop.item()).getStyledHoverName() : colored("Unknown", TEXT_DIM);
			String chance;
			if (drop.guaranteed()) {
				chance = drop.minCount() + "-" + drop.maxCount() + " (100%)";
			} else {
				chance = formatPercent(drop.chanceNum() * 100.0 / drop.chanceDen());
			}
			lines.add(Component.empty().append(name).append(colored("  " + chance, TEXT_GRAY)));
		}
		boolean seenStone = progress.dropsSeen.contains("soul_stone");
		Component stone = seenStone ? new ItemStack(ModItems.SOUL_STONE).getStyledHoverName() : colored("Unknown", TEXT_DIM);
		lines.add(Component.empty().append(stone).append(colored("  1% (Spirit Lure animals)", TEXT_GRAY)));
		return lines;
	}

	private static String formatPercent(double percent) {
		if (percent == Math.floor(percent)) {
			return (int) percent + "%";
		}
		return String.format("%.1f%%", percent);
	}

	// ---------------------------------------------------------------- Gear tab

	private static final String[] GEAR_PAGES = {"Armor", "Misc", "Weapons"};

	private void drawGear(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		panelBox(g);
		Hunt hunt = hunt();
		int cx = px + pw / 2;
		int y = py + 5;
		g.centeredText(this.font, colored(hunt.name() + " Gear", hunt.color()), cx, y, WHITE);
		y += 14;
		// Page switcher: "Armor  <  Misc  >  Weapons", wrapping around.
		String prev = GEAR_PAGES[(gearPage + 2) % 3];
		String next = GEAR_PAGES[(gearPage + 1) % 3];
		String current = GEAR_PAGES[gearPage];
		int curW = this.font.width(current);
		g.centeredText(this.font, colored(current, WHITE), cx, y, WHITE);
		g.text(this.font, colored("<", TEXT_GRAY), cx - curW / 2 - 12, y, WHITE, false);
		g.text(this.font, colored(">", TEXT_GRAY), cx + curW / 2 + 7, y, WHITE, false);
		g.text(this.font, colored(prev, TEXT_DIM), cx - curW / 2 - 18 - this.font.width(prev), y, WHITE, false);
		g.text(this.font, colored(next, TEXT_DIM), cx + curW / 2 + 18, y, WHITE, false);
		hits.add(new Hit(px, y - 2, cx - curW / 2 - 4 - px, 12, () -> changeGearPage(-1)));
		hits.add(new Hit(cx + curW / 2 + 4, y - 2, px + pw - (cx + curW / 2 + 4), 12, () -> changeGearPage(1)));
		y += 15;

		tokenButton = null;
		switch (gearPage) {
			case 1 -> drawGearMisc(g, hunt, y, mouseX, mouseY);
			case 0 -> drawGearTradesOrNone(g, HuntDefs.trades(hunt, 0), y);
			default -> drawGearTradesOrNone(g, HuntDefs.trades(hunt, 2), y);
		}
	}

	private void drawGearTradesOrNone(GuiGraphicsExtractor g, List<HuntDefs.GearTrade> trades, int y) {
		if (trades.isEmpty()) {
			g.centeredText(this.font, colored("Nothing here yet.", TEXT_GRAY), px + pw / 2, y + 10, WHITE);
			return;
		}
		drawGearTrades(g, trades, y, lastMouseX, lastMouseY);
	}

	private int lastMouseX;
	private int lastMouseY;

	private void drawGearMisc(GuiGraphicsExtractor g, Hunt hunt, int y, int mouseX, int mouseY) {
		int cx = px + pw / 2;
		sprite(g, "panel_dark", px + 4, y, pw - 8, 52);
		g.text(this.font, colored(hunt.name() + " Ritual Tokens", WHITE), px + 8, y + 4, WHITE, false);
		g.text(this.font, colored("Balance: ", TEXT_GRAY).append(colored(String.valueOf(progress().tokens), HuntDefs.LIME)),
				px + 8, y + 15, WHITE, false);
		tokenButton = new int[] {px + 8, y + 28, pw - 16, 16};
		int count = HuntDefs.tokenMaterialCount(hunt);
		String material = HuntDefs.tokenMaterialName(hunt);
		boolean hover = button(g, px + 8, y + 28, pw - 16, 16, Component.literal("Buy 64 Tokens [" + count + " " + material + "]"),
				true, mouseX, mouseY, null);
		if (hover) {
			List<Component> tip = new ArrayList<>();
			tip.add(colored("Click: " + count + " " + material + " -> 64 Tokens", WHITE));
			if (!HuntDefs.isCluster(hunt)) {
				tip.add(colored("Any Overworld log works.", TEXT_GRAY));
			}
			tip.add(colored("Shift-click: " + count * 10 + " " + material + " -> 640 Tokens", TEXT_GRAY));
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
		if (HuntDefs.isCluster(hunt)) {
			y += 58;
			sprite(g, "panel_dark", px + 4, y, pw - 8, 44);
			HuntData cdata = ClientHuntState.data;
			g.centeredText(this.font, colored("Questline - " + com.huntlike.towernaar.TowernaarScript.name(cdata), WHITE), cx, y + 6, WHITE);
			int firstCluster = com.huntlike.towernaar.TowernaarScript.CHAPTERS.get(0).rank();
			boolean clusterMet = progress().rank() >= firstCluster;
			Component clusterLabel = !clusterMet ? Component.literal("[Click] - Rank " + HuntDefs.ROMAN[firstCluster] + " Required")
					: cdata.towernaarDone > com.huntlike.towernaar.TowernaarScript.FINAL ? Component.literal("[Click] - Visit Towernaar")
					: Component.literal("[Click] - Talk");
			button(g, px + 8, y + 20, pw - 16, 16, clusterLabel, clusterMet, mouseX, mouseY,
					() -> this.minecraft.gui.setScreen(new TowernaarScreen(this)));
			y += 50;
			drawGearTrades(g, HuntDefs.CLUSTER_SPECIAL, y, mouseX, mouseY);
			return;
		}
		y += 58;
		sprite(g, "panel_dark", px + 4, y, pw - 8, 44);
		g.centeredText(this.font, colored("Questline - Spiritual Connection", WHITE), cx, y + 6, WHITE);
		int firstRank = HuntDefs.QUESTLINE_RANKS[0];
		boolean met = progress().rank() >= firstRank;
		HuntData data = ClientHuntState.data;
		Component label;
		if (!met) {
			label = Component.literal("[Click] - Rank " + HuntDefs.ROMAN[firstRank] + " Required");
		} else if (data.koroDone >= com.huntlike.hunt.KoroScript.CHAPTERS.size()) {
			label = Component.literal("[Click] - Visit the Soul");
		} else {
			label = Component.literal("[Click] - Connect");
		}
		button(g, px + 8, y + 20, pw - 16, 16, label, met, mouseX, mouseY,
				() -> this.minecraft.gui.setScreen(new KoroScreen(this)));
		y += 50;
		drawGearTrades(g, HuntDefs.ANIMAL_SPECIAL, y, mouseX, mouseY);
	}

	private void drawGearTrades(GuiGraphicsExtractor g, List<HuntDefs.GearTrade> trades, int y, int mouseX, int mouseY) {
		int listX = px + 4;
		int listW = pw - 14;
		int listTop = y;
		int listH = py + ph - 4 - y;
		int rank = progress().rank();
		int total = 0;
		for (HuntDefs.GearTrade trade : trades) {
			if (shown(trade)) {
				total += entryHeight(trade.cost().size()) + 4;
			}
		}
		if (total == 0) {
			return;
		}
		int maxScroll = Math.max(0, total - 4 - listH);
		gearScroll = Math.max(0, Math.min(gearScroll, maxScroll));
		g.enableScissor(listX, listTop, listX + listW, listTop + listH);
		int ey = listTop - gearScroll;
		for (int t = 0; t < trades.size(); t++) {
			HuntDefs.GearTrade trade = trades.get(t);
			if (!shown(trade)) {
				continue;
			}
			int value = gearPage * 100 + t;
			boolean affordable = true;
			for (HuntDefs.Cost cost : trade.cost()) {
				affordable &= countInInventory(cost) >= cost.count();
			}
			int h = entryHeight(trade.cost().size());
			sprite(g, "panel_dark", listX, ey, listW, h);
			boolean unlocked = rank >= trade.rank();
			MutableComponent title = colored(trade.name(), unlocked ? HuntDefs.GOLD : 0x8A8A8A);
			if (!unlocked) {
				title = title.withStyle(style -> style.withObfuscated(true));
			}
			g.text(this.font, title, listX + 5, ey + 4, WHITE, false);
			int ly = ey + 15;
			for (HuntDefs.Cost cost : trade.cost()) {
				boolean have = countInInventory(cost) >= cost.count();
				String name = new ItemStack(cost.item()).getHoverName().getString();
				// Hunt gear you haven't obtained stays a mystery: no icon, and no name until its trade unlocks.
				int gearRank = HuntDefs.gearRank(cost.item());
				boolean hidden = gearRank >= 0 && !have && !progress().dropsSeen.contains(HuntDefs.gearSeenId(cost.item()));
				if (hidden) {
					sprite(g, "panel_dark", listX + 5, ly, 10, 10);
					g.text(this.font, colored("?", TEXT_GRAY), listX + 8, ly + 1, WHITE, false);
				} else {
					g.pose().pushMatrix();
					g.pose().translate(listX + 5, ly);
					g.pose().scale(0.625F, 0.625F);
					g.item(new ItemStack(cost.item()), 0, 0);
					g.pose().popMatrix();
				}
				MutableComponent costName = colored(name, have ? 0xFFFFFF : HuntDefs.RED);
				if (gearRank > rank) {
					costName = costName.withStyle(style -> style.withObfuscated(true));
				}
				g.text(this.font, Component.empty().append(costName).append(colored(" x" + cost.count(), have ? 0xFFFFFF : HuntDefs.RED)),
						listX + 18, ly + 1, WHITE, false);
				ly += 11;
			}
			Component label = unlocked ? Component.literal("[Click] - Buy") : Component.literal("Rank Required: [?]");
			if (ey + h > listTop && ey < listTop + listH) {
				boolean inside = mouseY >= listTop && mouseY < listTop + listH;
				button(g, listX + 4, ly + 2, listW - 8, 14, label, unlocked && affordable, mouseX, mouseY,
						inside ? () -> ClientPlayNetworking.send(new HuntPayloads.Action("buy_gear", hunt().id(), value)) : null);
			}
			ey += h + 4;
		}
		g.disableScissor();

		// Scroll bar: the track shows the whole list, the handle the part on screen.
		int trackX = px + pw - 8;
		g.fill(trackX, listTop, trackX + 4, listTop + listH, 0xFF1A1A1A);
		if (maxScroll > 0) {
			int handleH = Math.max(12, listH * listH / (listH + maxScroll));
			int handleY = listTop + (listH - handleH) * gearScroll / maxScroll;
			g.fill(trackX, handleY, trackX + 4, handleY + handleH, 0xFFC6C6C6);
			g.fill(trackX + 3, handleY, trackX + 4, handleY + handleH, 0xFF8B8B8B);
		} else {
			g.fill(trackX, listTop, trackX + 4, listTop + listH, 0xFF555555);
		}
	}

	/** How many of a cost item the player is carrying. */
	private int countInInventory(HuntDefs.Cost cost) {
		if (this.minecraft.player == null) {
			return 0;
		}
		net.minecraft.world.entity.player.Inventory inventory = this.minecraft.player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(cost.item())) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Koro's and Towernaar's restocks only appear once their chapter is finished (the wand once its PRD is used). */
	private boolean shown(HuntDefs.GearTrade trade) {
		return HuntDefs.tradeOpen(ClientHuntState.data, hunt(), trade);
	}

	private static int entryHeight(int costLines) {
		return 15 + costLines * 11 + 20;
	}

	private void changeGearPage(int direction) {
		gearPage = (gearPage + direction + 3) % 3;
		gearScroll = 0;
	}

	// ---------------------------------------------------------------- Ranks tab

	private void drawRanks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Hunt hunt = hunt();
		HuntData.Progress progress = progress();
		int rank = progress.rank();
		int maxView = Math.min(rank + 1, HuntDefs.MAX_RANK);
		rankView = Math.max(0, Math.min(rankView, maxView));
		boolean snapshot = rankView > rank;
		int cx = px + pw / 2;

		// Header box: "Hunt Type - Rank - RN".
		int headerH = 24;
		sprite(g, "panel", px, py, pw, headerH);
		Component header = colored(hunt.name(), hunt.color())
				.append(colored(" - " + HuntDefs.RANK_TITLES[rankView] + " - " + HuntDefs.ROMAN[rankView],
						snapshot ? TEXT_GRAY : WHITE));
		g.centeredText(this.font, header, cx, py + 8, WHITE);
		if (rankView > 0) {
			g.text(this.font, colored("<", TEXT_GRAY), px + 4, py + 8, WHITE, false);
			hits.add(new Hit(px, py, 14, headerH, () -> changeRank(-1)));
		}
		if (rankView < maxView) {
			g.text(this.font, colored(">", TEXT_GRAY), px + pw - 9, py + 8, WHITE, false);
			hits.add(new Hit(px + pw - 14, py, 14, headerH, () -> changeRank(1)));
		}

		// Unlock list.
		int listY = py + headerH + 4;
		int listH = ph - headerH - 4 - 20;
		sprite(g, "panel", px, listY, pw, listH);
		int y = listY + 5;
		if (snapshot) {
			g.text(this.font, colored("Next Rank: " + HuntDefs.RANK_XP[rankView] + " XP", HuntDefs.GOLD), px + 6, y, WHITE, false);
			y += 12;
		}
		for (Unlock unlock : hunt.unlocks().get(rankView)) {
			int color = snapshot ? 0x8A8A8A : unlock.color();
			for (FormattedCharSequence line : this.font.split(colored("* " + unlock.text(), color), pw - 12)) {
				g.text(this.font, line, px + 6, y, WHITE, false);
				y += 9;
			}
			y += 2;
		}
		if (!snapshot && rankView == rank && progress.bossKills[4] > 0) {
			y += 4;
			g.text(this.font, colored("Boss kills", WHITE), px + 6, y, WHITE, false);
			y += 10;
			StringBuilder kills = new StringBuilder();
			for (int i = 0; i < 5; i++) {
				kills.append(HuntDefs.ROMAN[i + 1]).append(": ").append(progress.bossKills[i]).append(i < 4 ? "  " : "");
			}
			g.text(this.font, colored(kills.toString(), TEXT_GRAY), px + 6, y, WHITE, false);
			y += 11;
			g.text(this.font, colored("Drops received", WHITE), px + 6, y, WHITE, false);
			y += 10;
			for (Drop drop : HuntDefs.drops(hunt.tier(5))) {
				int count = progress.dropCounts.getOrDefault(drop.id(), 0);
				g.text(this.font, Component.empty().append(new ItemStack(drop.item()).getStyledHoverName())
						.append(colored(": " + count, TEXT_GRAY)), px + 6, y, WHITE, false);
				y += 9;
			}
		}

		// XP bar.
		int barY = py + ph - 14;
		int next = rank >= HuntDefs.MAX_RANK ? HuntDefs.RANK_XP[HuntDefs.MAX_RANK] : HuntDefs.RANK_XP[rank + 1];
		int prev = HuntDefs.RANK_XP[rank];
		float fill = rank >= HuntDefs.MAX_RANK ? 1.0F : (progress.xp - prev) / (float) Math.max(1, next - prev);
		sprite(g, "bar_background", px, barY, pw, 12);
		int xpFilled = Math.round(pw * Math.max(0, Math.min(1, fill)));
		if (xpFilled > 0) {
			sprite(g, "bar_xp", px, barY, xpFilled, 12);
		}
		String xpText = rank >= HuntDefs.MAX_RANK ? progress.xp + " XP (Max Rank)" : progress.xp + " / " + next + " XP";
		g.centeredText(this.font, colored(xpText, WHITE), cx, barY + 2, WHITE);
	}

	// ---------------------------------------------------------------- input

	private void switchTab(int newTab) {
		if (newTab != tab) {
			tab = newTab;
			panelOpen = hintSeen();
			gearPage = 1;
			gearScroll = 0;
			rankView = progress().rank();
			cancelArmed = false;
		}
	}

	private void openPanel() {
		panelOpen = true;
		markHintSeen();
		abilityScroll = 0;
		rankView = progress().rank();
		questTier = Math.min(questTier, hunt().tiers().size());
		gearPage = 1;
		gearScroll = 0;
	}

	private void changeHunt(int direction) {
		int next = Math.max(0, Math.min(RAIL_SIZE - 1, railPos + direction));
		if (next != railPos) {
			railPos = next;
			huntIndex = railPos == 2 ? 1 : 0;
			rankView = progress().rank();
			questTier = highestUnlockedTier();
			interludeStage = highestOpenStage();
			abilityScroll = 0;
			giveUpArmed = false;
			ClientPlayNetworking.send(new HuntPayloads.Action("select",
					interlude() ? com.huntlike.interlude.InterludeDefs.SLIME_RIDER : hunt().id(), 0));
		}
	}

	private void changeTier(int direction) {
		if (interlude()) {
			interludeStage = Math.max(1, Math.min(com.huntlike.interlude.InterludeDefs.STAGES.size(), interludeStage + direction));
			abilityScroll = 0;
			return;
		}
		questTier = Math.max(1, Math.min(hunt().tiers().size(), questTier + direction));
		abilityScroll = 0;
	}

	private void changeRank(int direction) {
		rankView += direction;
	}

	private void startQuest() {
		ClientPlayNetworking.send(new HuntPayloads.Action("start", hunt().id(), questTier));
	}

	private boolean questPanelShowing() {
		return tab == 0 && activeQuest() != null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (editField != 0) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				submitEdit();
			} else if (key == InputConstants.KEY_ESCAPE) {
				editField = 0;
			} else if (key == InputConstants.KEY_BACKSPACE && !editText.isEmpty()) {
				editText = editText.substring(0, editText.length() - 1);
			}
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE && (popupFor != null || inboxOpen || stashOpen)) {
			closePackOverlays();
			return true;
		}
		if (packView) {
			if (key == InputConstants.KEY_ESCAPE) {
				// First Esc goes back to the Quests tab, the second closes the menu.
				packView = false;
				tab = 0;
			}
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE) {
			if (panelOpen && !questPanelShowing()) {
				panelOpen = false;
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				this.onClose();
			}
			return true;
		}
		if (key == InputConstants.KEY_W || key == InputConstants.KEY_UP) {
			changeHunt(-1);
			return true;
		}
		if (key == InputConstants.KEY_S || key == InputConstants.KEY_DOWN) {
			changeHunt(1);
			return true;
		}
		if (key == InputConstants.KEY_A || key == InputConstants.KEY_LEFT) {
			if (panelOpen && tab == 0 && !questPanelShowing()) {
				changeTier(-1);
			} else if (panelOpen && tab == 2) {
				changeRank(-1);
			} else if (panelOpen && tab == 1) {
				changeGearPage(-1);
			}
			return true;
		}
		if (key == InputConstants.KEY_D || key == InputConstants.KEY_RIGHT) {
			if (panelOpen && tab == 0 && !questPanelShowing()) {
				changeTier(1);
			} else if (panelOpen && tab == 2) {
				changeRank(1);
			} else if (panelOpen && tab == 1) {
				changeGearPage(1);
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (!panelOpen && !questPanelShowing()) {
				openPanel();
			} else if (tab == 0 && !questPanelShowing() && interlude()) {
				if (interludeCanStart()) {
					ClientPlayNetworking.send(new HuntPayloads.Action("interlude_start", com.huntlike.interlude.InterludeDefs.SLIME_RIDER,
							interludeStage));
				}
			} else if (tab == 0 && !questPanelShowing()) {
				Tier tier = hunt().tier(questTier);
				HuntData.Progress progress = progress();
				if (tierOpen(tier) && progress.tokens >= tier.tokenCost() && !ClientHuntState.peaceful && packAllowsStart(hunt(), tier)) {
					startQuest();
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (editField == 0) {
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (editField == 1 && typed.matches("[A-Za-z0-9 ]") && editText.length() < com.huntlike.hunt.HuntingPacks.NAME_LENGTH) {
			editText += typed;
		} else if (editField == 2 && typed.matches("[0-9]") && editText.length() < 3) {
			editText += typed;
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// In the Hunting Pack screen both buttons open a player's options.
		if (packView && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			for (Hit hit : List.copyOf(hits)) {
				if (hit.contains(event.x(), event.y())) {
					hit.action().run();
					return true;
				}
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			boolean shift = (event.modifiers() & InputConstants.MOD_SHIFT) != 0;
			// The token trade reads Shift, so it is handled here.
			int[] ib = interludeTokenButton;
			if (tab == 0 && panelOpen && interlude() && ib != null && mx >= ib[0] && mx < ib[0] + ib[2] && my >= ib[1]
					&& my < ib[1] + ib[3]) {
				ClientPlayNetworking.send(new HuntPayloads.Action("interlude_tokens", com.huntlike.interlude.InterludeDefs.SLIME_RIDER,
						shift ? 1 : 0));
				return true;
			}
			int[] tb = tokenButton;
			if (tab == 1 && panelOpen && tb != null && mx >= tb[0] && mx < tb[0] + tb[2] && my >= tb[1] && my < tb[1] + tb[3]) {
				ClientPlayNetworking.send(new HuntPayloads.Action("buy_tokens", hunt().id(), shift ? 1 : 0));
				return true;
			}
			for (Hit hit : List.copyOf(hits)) {
				if (hit.contains(mx, my)) {
					boolean wasArmed = cancelArmed;
					boolean wasGiveUp = giveUpArmed;
					hit.action().run();
					if (wasArmed && cancelArmed) {
						cancelArmed = false;
					}
					if (wasGiveUp && giveUpArmed) {
						giveUpArmed = false;
					}
					return true;
				}
			}
			cancelArmed = false;
			giveUpArmed = false;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (packView) {
			int[] box = packBox;
			if (box != null && mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3]) {
				packScroll -= (int) Math.round(scrollY * 12);
			}
			return true;
		}
		if (panelOpen && tab == 0 && !questPanelShowing() && mouseX >= px && mouseX < px + pw) {
			abilityScroll -= (int) Math.round(scrollY * 9);
			return true;
		}
		if (panelOpen && tab == 1 && mouseX >= px && mouseX < px + pw) {
			gearScroll -= (int) Math.round(scrollY * 12);
			return true;
		}
		if (mouseX < px) {
			changeHunt(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
