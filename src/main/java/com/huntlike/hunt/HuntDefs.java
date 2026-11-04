package com.huntlike.hunt;

import java.util.List;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.huntlike.entity.ModEntities;
import com.huntlike.gear.ModGear;
import com.huntlike.registry.ModItems;

/** Static design data for every Hunt: ranks, tiers, bosses and drops. Shared by server and client. */
public final class HuntDefs {
	private HuntDefs() {
	}

	public static final int LIME = 0x55FF55;
	public static final int PREFIX = 0xFF55FF;
	public static final int AQUA = 0x55FFFF;
	public static final int DARK_GREEN = 0x00AA00;
	public static final int DARK_RED = 0xAA0000;
	public static final int GREEN = 0x55FF55;
	public static final int RED = 0xFF5555;
	public static final int GOLD = 0xFFAA00;
	public static final int DARK_PURPLE = 0xAA00AA;
	public static final int GRAY = 0xAAAAAA;
	public static final int LIGHT_BLUE = 0x8FD3FF;
	public static final int PINK = 0xFF8AD8;
	public static final int BLUE = 0x5555FF;
	/** Experience orb green. */
	public static final int XP_GREEN = 0x80FF20;
	/** Cluster Hunt brown. */
	public static final int BROWN = 0xB8743A;

	public static final String[] RANK_TITLES = {"Prey", "Scout", "Tracker", "Stalker", "Trapper", "Pursuer", "Warden",
			"Predator", "Apex Hunter", "Dread Hunter", "Slayer"};
	public static final int[] RANK_XP = {0, 60, 150, 390, 750, 1650, 3000, 6000, 10500, 26500, 50500};
	public static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	public static final int MAX_RANK = 10;

	/** One line in a Rank's unlock list. Color is an RGB value. */
	public record Unlock(String text, int color) {
		static Unlock plain(String text) {
			return new Unlock(text, 0xFFFFFF);
		}
	}

	/**
	 * A boss drop, with its chance as numerator / denominator (1/1 = guaranteed).
	 * dropClass picks the chat message: 0 none (guaranteed), 1 Lucky, 2 Good, 3 GREAT, 4 FANTASTIC.
	 * Several drops of a Hunt may share a class.
	 */
	public record Drop(String id, Item item, int minCount, int maxCount, int chanceNum, int chanceDen, int dropClass) {
		public boolean guaranteed() {
			return chanceNum >= chanceDen;
		}
	}

	/** A boss ability as players see it. */
	public record Ability(String name, String description) {
	}

	/** One tier of a Hunt: its boss and its rules. */
	public record Tier(int number, String bossName, String bossTitle, EntityType<? extends Mob> mob, float scale,
			int health, int melee, int regen, float eggDamage, int quota, int tokenCost, int xp, int unlockRank,
			List<Ability> abilities, String loreKey) {
		public String roman() {
			return ROMAN[number];
		}

		/** Name tag and aura color of this tier. */
		public int color() {
			return HuntDefs.tierColor(number);
		}
	}

	/** A Hunt type: Animal, then Cluster. */
	public record Hunt(String id, String name, int color, net.minecraft.world.BossEvent.BossBarColor barColor, List<Tier> tiers,
			List<List<Unlock>> unlocks) {
		public Tier tier(int number) {
			return tiers.get(number - 1);
		}

		public String rankName(int level) {
			return name + " " + RANK_TITLES[level];
		}
	}

	public static int tierColor(int tier) {
		return tier <= 2 ? RED : tier <= 4 ? DARK_RED : GOLD;
	}

	public static final Ability EGG_BLASTER = new Ability("Egg Blaster", "Eggs are shot, dealing damage constantly.");
	public static final Ability SOUL_STRIKE = new Ability("Soul Strike", "A spiritual lash that targets your soul.");
	public static final Ability ANIMALIA_LUNGE = new Ability("Animalia Lunge",
			"A straight charge with heavy knockback and greater damage.");
	public static final Ability HERD_CALL = new Ability("Herd Call",
			"Spawns supporting species that prevent damage to the boss until they are dealt with.");
	public static final Ability APEX_INSTINCT = new Ability("Apex Instinct",
			"After being wounded many times, survival instincts consume the canine body, increasing speed and damage.");

	private static final String KORO_FIRST = "A soul from the unknown territories is here to talk! [Gear]";
	private static final String ROGUE_HUNTERS = "Rogue Hunters may now stalk you during quests...";
	private static final String TOWERNAAR_FIRST = "A mysterious Enderian wants to talk! [Gear]";
	private static final String TOWERNAAR_NEXT = "The Enderian has a new request for you!";
	private static final String TOWERNAAR_FINAL = "The Enderian's Final Chapter is ready...";
	private static final String KORO_NEXT = "The soul of Koro thinks you are ready for his next quest!";

	public static final Hunt ANIMAL = new Hunt("animal", "Animal", LIME, net.minecraft.world.BossEvent.BossBarColor.GREEN,
			List.of(
					new Tier(1, "Sylviornis", "The Giant Galliforme", ModEntities.SYLVIORNIS, 1.5F, 70, 1, 1, 0.25F, 2, 2, 5, 0,
							List.of(EGG_BLASTER), "lore.huntlike.animal.1"),
					new Tier(2, "Hylochoerus", "The Aberrant Souleater", ModEntities.HYLOCHOERUS, 1.25F, 95, 1, 2, 0.5F, 4, 4, 20, 2,
							List.of(EGG_BLASTER, SOUL_STRIKE), "lore.huntlike.animal.2"),
					new Tier(3, "Ovis Ammon", "The Horned Creation", ModEntities.OVIS_AMMON, 1.25F, 145, 2, 3, 1.0F, 6, 8, 75, 4,
							List.of(EGG_BLASTER, SOUL_STRIKE, ANIMALIA_LUNGE), "lore.huntlike.animal.3"),
					new Tier(4, "Aurochs", "The Animal Mother", ModEntities.AUROCHS, 1.25F, 265, 2, 4, 2.0F, 8, 16, 250, 6,
							List.of(EGG_BLASTER, SOUL_STRIKE, ANIMALIA_LUNGE, HERD_CALL), "lore.huntlike.animal.4"),
					new Tier(5, "Epicyon Haydeni", "The Bone Crusher", ModEntities.EPICYON_HAYDENI, 2.0F, 300, 3, 5, 2.0F, 10, 32, 800, 8,
							List.of(EGG_BLASTER, SOUL_STRIKE, ANIMALIA_LUNGE, HERD_CALL, APEX_INSTINCT),
							"lore.huntlike.animal.5")),
			List.of(
					List.of(Unlock.plain("Tier I Animal Quests")),
					List.of(Unlock.plain("Hunt Weapon: Skin Knife [Genesis]")),
					List.of(Unlock.plain("Tier II Animal Quests"), Unlock.plain("Hunt Armor: Rotten Armor")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Meat Cutter [Act I]"), new Unlock("Next Hunt type: Cluster", BROWN),
							new Unlock(KORO_FIRST, DARK_PURPLE)),
					List.of(Unlock.plain("Tier III Animal Quests"), Unlock.plain("Hunt Armor upgrade: Carrion Armor")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Limb Severer [Act II]"), new Unlock(ROGUE_HUNTERS, 0xAA0000)),
					List.of(Unlock.plain("Tier IV Animal Quests"), Unlock.plain("Hunt Armor upgrade: Ecocide Armor"),
							new Unlock(KORO_NEXT, DARK_PURPLE)),
					List.of(Unlock.plain("Hunt Weapon upgrade: Bone Breaker [Act III]")),
					List.of(Unlock.plain("Tier V Animal Quests")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Bone Pulverizer [Apotheosis]"), new Unlock(KORO_NEXT, DARK_PURPLE)),
					List.of(new Unlock(KORO_NEXT, DARK_PURPLE))));

	// ---------------------------------------------------------------- Cluster Hunt

	public static final Ability STICKY_FINGERS = new Ability("Sticky Fingers",
			"Deals damage on every jump.\nThe remains of the main body also damage you when split.");
	public static final Ability MITOSIS = new Ability("Mitosis", "When the main body is defeated, it divides into smaller bodies.");
	public static final Ability MITOSIS_AGAIN = new Ability("Mitosis",
			"When the main body is defeated, it divides into smaller bodies.\nWait... does it keep going?");
	public static final Ability BALLER = new Ability("Baller", "Shoots slimeballs slowly, dealing high damage. Bring a shield!");
	public static final Ability YUMMY = new Ability("Yummy!",
			"You'll be consumed by the cluster, damaging you. Move in a specific way to escape!");
	public static final Ability WATCH_OUT = new Ability("Watch out!",
			"You'll be attacked with flames from different directions. Guard up!");
	public static final Ability THERMODYNAMIC = new Ability("Thermodynamic",
			"The cluster changes its core after some time to not be effectively damaged.");

	/**
	 * How a Cluster boss splits (Mitosis): the main body, then each generation's body count per parent, health and
	 * size. Tier I splits into 2; Tier IV into 4, then each of those into 2; Tier V into 4, then each into 4.
	 */
	public record ClusterSpec(int mainHealth, int mainSize, int[] splits, int[] splitHealth, int[] splitSize) {
		public int generations() {
			return splits.length;
		}

		/** Total health of the whole cluster, every body included. */
		public int totalHealth() {
			int total = mainHealth;
			int bodies = 1;
			for (int i = 0; i < splits.length; i++) {
				bodies *= splits[i];
				total += bodies * splitHealth[i];
			}
			return total;
		}

		/** Health still to come from a living body of this generation once it (and its splits) die. */
		public int futureHealth(int generation) {
			int total = 0;
			int bodies = 1;
			for (int i = generation; i < splits.length; i++) {
				bodies *= splits[i];
				total += bodies * splitHealth[i];
			}
			return total;
		}

		public int health(int generation) {
			return generation == 0 ? mainHealth : splitHealth[generation - 1];
		}

		public int size(int generation) {
			return generation == 0 ? mainSize : splitSize[generation - 1];
		}
	}

	public static final ClusterSpec[] CLUSTER_SPECS = {
			new ClusterSpec(48, 5, new int[] {2}, new int[] {14}, new int[] {3}),
			new ClusterSpec(64, 5, new int[] {3}, new int[] {16}, new int[] {3}),
			new ClusterSpec(94, 5, new int[] {4}, new int[] {21}, new int[] {3}),
			new ClusterSpec(120, 6, new int[] {4, 2}, new int[] {24, 9}, new int[] {3, 2}),
			new ClusterSpec(165, 6, new int[] {4, 4}, new int[] {33, 11}, new int[] {3, 2})};

	public static ClusterSpec clusterSpec(Tier tier) {
		return CLUSTER_SPECS[tier.number() - 1];
	}

	public static final Hunt CLUSTER = new Hunt("cluster", "Cluster", BROWN, net.minecraft.world.BossEvent.BossBarColor.YELLOW,
			List.of(
					new Tier(1, "Cubert", "The Overslime Fragment", ModEntities.CUBERT, 1.0F, CLUSTER_SPECS[0].totalHealth(), 2, 1, 0,
							2, 2, 5, 0, List.of(STICKY_FINGERS, MITOSIS), "lore.huntlike.cluster.1"),
					new Tier(2, "Blucky", "The Tough Slime", ModEntities.BLUCKY, 1.0F, CLUSTER_SPECS[1].totalHealth(), 3, 2, 0,
							4, 4, 20, 2, List.of(STICKY_FINGERS, MITOSIS, BALLER), "lore.huntlike.cluster.2"),
					new Tier(3, "Hot Head", "Angrier than Ever", ModEntities.HOT_HEAD, 1.0F, CLUSTER_SPECS[2].totalHealth(), 3, 3, 0,
							6, 8, 75, 4, List.of(STICKY_FINGERS, MITOSIS, BALLER, YUMMY), "lore.huntlike.cluster.3"),
					new Tier(4, "Variance", "The Demi-Soul Cluster", ModEntities.VARIANCE_SLIME, 1.0F, CLUSTER_SPECS[3].totalHealth(), 4,
							4, 0, 8, 16, 250, 6, List.of(STICKY_FINGERS, MITOSIS_AGAIN, BALLER, YUMMY, WATCH_OUT),
							"lore.huntlike.cluster.4"),
					new Tier(5, "The Vile", "Soul Cluster of Conquest", ModEntities.THE_VILE, 1.0F, CLUSTER_SPECS[4].totalHealth(), 4,
							5, 0, 10, 32, 800, 8, List.of(STICKY_FINGERS, MITOSIS_AGAIN, BALLER, YUMMY, WATCH_OUT, THERMODYNAMIC),
							"lore.huntlike.cluster.5")),
			List.of(
					List.of(Unlock.plain("Tier I Cluster Quests")),
					List.of(Unlock.plain("Hunt Weapon: Slick Kama [Genesis]")),
					List.of(Unlock.plain("Tier II Cluster Quests"), Unlock.plain("Hunt Armor: Icy Armor")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Polished Khopesh [Act I]"), new Unlock(TOWERNAAR_FIRST, DARK_PURPLE)),
					List.of(Unlock.plain("Tier III Cluster Quests"), Unlock.plain("Hunt Armor upgrade: Gel Armor")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Slimy Falx [Act II]"), new Unlock(ROGUE_HUNTERS, 0xAA0000)),
					List.of(Unlock.plain("Tier IV Cluster Quests"), Unlock.plain("Hunt Armor upgrade: Thermodynamic Armor"),
							new Unlock(TOWERNAAR_NEXT, DARK_PURPLE)),
					List.of(Unlock.plain("Hunt Weapon upgrade: Slimysteel Glaive [Act III]")),
					List.of(Unlock.plain("Tier V Cluster Quests")),
					List.of(Unlock.plain("Hunt Weapon upgrade: Scythe of Sublimation [Apotheosis]"), new Unlock(TOWERNAAR_NEXT, DARK_PURPLE)),
					List.of(new Unlock(TOWERNAAR_FINAL, DARK_PURPLE))));

	public static final List<Hunt> HUNTS = List.of(ANIMAL, CLUSTER);

	public static boolean isCluster(Hunt hunt) {
		return hunt == CLUSTER;
	}

	public static boolean isCluster(Tier tier) {
		return CLUSTER.tiers().contains(tier);
	}

	/** Animal Rank needed to start a Hunt (the Cluster Hunt opens at Animal Rank III). */
	public static int huntUnlockRank(Hunt hunt) {
		return hunt == CLUSTER ? 3 : 0;
	}

	public static boolean huntUnlocked(HuntData data, Hunt hunt) {
		return data.progress(ANIMAL.id()).rank() >= huntUnlockRank(hunt);
	}

	/** Ritual Token trade: how many of the Hunt's material buy 64 tokens, and its name. */
	public static int tokenMaterialCount(Hunt hunt) {
		return hunt == CLUSTER ? 5 : 10;
	}

	public static String tokenMaterialName(Hunt hunt) {
		return hunt == CLUSTER ? "Soul Sand" : "Logs";
	}

	public static boolean isTokenMaterial(Hunt hunt, net.minecraft.world.item.ItemStack stack) {
		return hunt == CLUSTER ? stack.is(Items.SOUL_SAND) : stack.is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN);
	}

	/** One cost line: an item and how many. */
	public record Cost(Item item, int count) {
		public static Cost of(Item item, int count) {
			return new Cost(item, count);
		}
	}

	/**
	 * A Gear-tab trade. The first result.size() costs are the gear being upgraded (one item each, in the same order as
	 * result), so each new item keeps that item's enchantments.
	 */
	public record GearTrade(String name, int rank, List<Cost> cost, List<Item> result, int chapter) {
		/** A Rank-gated trade. */
		public GearTrade(String name, int rank, List<Cost> cost, List<Item> result) {
			this(name, rank, cost, result, -1);
		}
	}

	/** Koro's restocks (Misc page): each appears once its questline chapter is finished. */
	public static final List<GearTrade> ANIMAL_SPECIAL = List.of(
			new GearTrade("Wooden Bell", 0, List.of(Cost.of(Items.OAK_LOG, 16), Cost.of(ModItems.THICK_HIDE, 1)),
					List.of(com.huntlike.gear.ModKoro.WOODEN_BELL), 0),
			new GearTrade("Golden Bell", 0, List.of(Cost.of(com.huntlike.gear.ModKoro.WOODEN_BELL, 1), Cost.of(Items.GOLD_INGOT, 10),
					Cost.of(ModItems.THICK_HIDE, 2)), List.of(com.huntlike.gear.ModKoro.GOLDEN_BELL), 1),
			new GearTrade("Mossy Bell", 0, List.of(Cost.of(com.huntlike.gear.ModKoro.GOLDEN_BELL, 1), Cost.of(ModItems.GOLDEN_MOSS, 1)),
					List.of(com.huntlike.gear.ModKoro.MOSSY_BELL), 2),
			new GearTrade("Koro Whakamate", 0, List.of(Cost.of(Items.OBSIDIAN, 10), Cost.of(Items.NETHER_STAR, 1),
					Cost.of(ModItems.SPIRITUAL_CATALYST, 1)), List.of(com.huntlike.gear.ModKoro.KORO_WHAKAMATE), 3));

	/** chapter value for a trade unlocked by the Magnetic Wand PRD. */
	public static final int WAND_CHAPTER = 99;

	/** Towernaar's restocks (Cluster Misc page): the shields after their chapters, the wand once its PRD is used. */
	public static final List<GearTrade> CLUSTER_SPECIAL = List.of(
			new GearTrade("Obsidian Shield", 0, List.of(Cost.of(Items.SHIELD, 1), Cost.of(Items.IRON_INGOT, 16), Cost.of(Items.OBSIDIAN, 8)),
					List.of(com.huntlike.gear.ModShields.OBSIDIAN_SHIELD), 0),
			new GearTrade("Unbreakable Shield", 0, List.of(Cost.of(com.huntlike.gear.ModShields.OBSIDIAN_SHIELD, 1),
					Cost.of(Items.NETHERITE_SCRAP, 2), Cost.of(Items.ENDER_PEARL, 16)), List.of(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD), 1),
			new GearTrade("Magnetic Wand", 0, List.of(Cost.of(ModItems.SLIME_WASHED_HOLMIUM, 1), Cost.of(ModItems.SUBLIMATING_MAGMA, 1),
					Cost.of(ModItems.GELID_SLIME_BALL, 5)), List.of(com.huntlike.gear.ModShields.MAGNETIC_WAND), WAND_CHAPTER),
			// Always on sale. The copper rod is the (unweathered, unwaxed) lightning rod.
			new GearTrade("A-Phone", 0, List.of(Cost.of(Items.REDSTONE, 8), Cost.of(Items.REDSTONE_TORCH, 2), Cost.of(Items.IRON_INGOT, 10),
					Cost.of(ModItems.FROZEN_SLIME_BALL, 4), Cost.of(ModItems.DECENT_QUALITY_MYSTERY_MEAT, 4),
					Cost.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
							net.minecraft.resources.Identifier.withDefaultNamespace("lightning_rod")), 1),
					Cost.of(Items.GLASS, 1), Cost.of(Items.GOLD_INGOT, 5)), List.of(ModItems.A_PHONE)));

	/** Whether a chapter-gated trade is open: Koro's chapters for Animal, Towernaar's (or the wand's PRD) for Cluster. */
	public static boolean tradeOpen(HuntData data, Hunt hunt, GearTrade trade) {
		if (trade.chapter() < 0) {
			return true;
		}
		if (hunt == CLUSTER) {
			return trade.chapter() == WAND_CHAPTER ? data.wandUnlocked : data.towernaarDone > trade.chapter();
		}
		return data.koroDone > trade.chapter();
	}

	private static List<Cost> set(ModGear.ArmorSet set) {
		return List.of(Cost.of(set.helmet(), 1), Cost.of(set.chestplate(), 1), Cost.of(set.leggings(), 1), Cost.of(set.boots(), 1));
	}

	private static List<Cost> join(List<Cost> first, Cost... rest) {
		java.util.ArrayList<Cost> list = new java.util.ArrayList<>(first);
		list.addAll(List.of(rest));
		return list;
	}

	public static final List<GearTrade> ANIMAL_ARMOR = List.of(
			new GearTrade("Rotten Armor", 2, join(List.of(Cost.of(Items.LEATHER_HELMET, 1), Cost.of(Items.LEATHER_CHESTPLATE, 1),
					Cost.of(Items.LEATHER_LEGGINGS, 1), Cost.of(Items.LEATHER_BOOTS, 1)), Cost.of(Items.OAK_LOG, 32),
					Cost.of(Items.OAK_LEAVES, 32), Cost.of(ModItems.DECENT_QUALITY_MYSTERY_MEAT, 4)), ModGear.ROTTEN.pieces()),
			new GearTrade("Carrion Armor", 4, join(set(ModGear.ROTTEN), Cost.of(ModItems.DECENT_QUALITY_MYSTERY_MEAT, 4),
					Cost.of(ModItems.THICK_HIDE, 2), Cost.of(Items.LEATHER, 16), Cost.of(Items.BONE, 16)), ModGear.CARRION.pieces()),
			new GearTrade("Ecocide Armor", 6, join(set(ModGear.CARRION), Cost.of(ModItems.HIGH_QUALITY_MYSTERY_MEAT, 2),
					Cost.of(ModItems.THICK_HIDE, 2), Cost.of(Items.OAK_LOG, 32), Cost.of(Items.DIAMOND, 4)), ModGear.ECOCIDE.pieces()));

	public static final List<GearTrade> ANIMAL_WEAPONS = List.of(
			new GearTrade("Skin Knife [Genesis]", 1, List.of(Cost.of(Items.IRON_SWORD, 1),
					Cost.of(ModItems.LOW_QUALITY_MYSTERY_MEAT, 5)), List.of(ModGear.SKIN_KNIFE)),
			new GearTrade("Meat Cutter [Act I]", 3, List.of(Cost.of(ModGear.SKIN_KNIFE, 1),
					Cost.of(ModItems.DECENT_QUALITY_MYSTERY_MEAT, 2), Cost.of(Items.IRON_INGOT, 5)), List.of(ModGear.MEAT_CUTTER)),
			new GearTrade("Limb Severer [Act II]", 5, List.of(Cost.of(ModGear.MEAT_CUTTER, 1),
					Cost.of(ModItems.HIGH_QUALITY_MYSTERY_MEAT, 1), Cost.of(Items.DIAMOND, 2)), List.of(ModGear.LIMB_SEVERER)),
			new GearTrade("Bone Breaker [Act III]", 7, List.of(Cost.of(ModGear.LIMB_SEVERER, 1), Cost.of(ModItems.LARGE_FEMUR, 1),
					Cost.of(Items.SOUL_SAND, 8)), List.of(ModGear.BONE_BREAKER)),
			new GearTrade("Bone Pulverizer [Apotheosis]", 9, List.of(Cost.of(ModGear.BONE_BREAKER, 1), Cost.of(ModItems.LARGE_FEMUR, 1),
					Cost.of(Items.BONE_MEAL, 12), Cost.of(Items.OBSIDIAN, 8)), List.of(ModGear.BONE_PULVERIZER)));

	/** Cluster Hunt Armor (Gear tab, Armor page). */
	public static final List<GearTrade> CLUSTER_ARMOR = List.of(
			new GearTrade("Icy Armor", 2, join(List.of(Cost.of(Items.IRON_HELMET, 1), Cost.of(Items.IRON_CHESTPLATE, 1),
					Cost.of(Items.IRON_LEGGINGS, 1), Cost.of(Items.IRON_BOOTS, 1)), Cost.of(Items.ICE, 16),
					Cost.of(ModItems.THERMAL_SLIMEBALL, 8)), ModGear.ICY.pieces()),
			new GearTrade("Gel Armor", 4, join(set(ModGear.ICY), Cost.of(ModItems.FROZEN_SLIME_BALL, 4), Cost.of(Items.SLIME_BLOCK, 8),
					Cost.of(ModItems.SLIMYSTEEL, 4)), ModGear.GEL.pieces()),
			new GearTrade("Thermodynamic Armor", 6, join(set(ModGear.GEL), Cost.of(ModItems.GELID_SLIME_BALL, 2),
					Cost.of(ModItems.SUBLIMATING_MAGMA, 2), Cost.of(ModItems.CORRODED_PALLADIUM, 1), Cost.of(Items.DIAMOND, 4)),
					ModGear.THERMODYNAMIC.pieces()));

	/** Cluster Hunt Weapons (Gear tab, Weapons page). */
	public static final List<GearTrade> CLUSTER_WEAPONS = List.of(
			new GearTrade("Slick Kama [Genesis]", 1, List.of(Cost.of(Items.IRON_HOE, 1), Cost.of(ModItems.THERMAL_SLIMEBALL, 8)),
					List.of(com.huntlike.gear.ModScythes.SLICK_KAMA)),
			new GearTrade("Polished Khopesh [Act I]", 3, List.of(Cost.of(com.huntlike.gear.ModScythes.SLICK_KAMA, 1),
					Cost.of(ModItems.THERMAL_SLIMEBALL, 16), Cost.of(ModItems.FROZEN_SLIME_BALL, 2), Cost.of(Items.IRON_INGOT, 5)),
					List.of(com.huntlike.gear.ModScythes.POLISHED_KHOPESH)),
			new GearTrade("Slimy Falx [Act II]", 5, List.of(Cost.of(com.huntlike.gear.ModScythes.POLISHED_KHOPESH, 1),
					Cost.of(ModItems.SLIMYSTEEL, 1), Cost.of(ModItems.THERMAL_SLIMEBALL, 24), Cost.of(Items.DIAMOND, 2)),
					List.of(com.huntlike.gear.ModScythes.SLIMY_FALX)),
			new GearTrade("Slimysteel Glaive [Act III]", 7, List.of(Cost.of(com.huntlike.gear.ModScythes.SLIMY_FALX, 1),
					Cost.of(ModItems.GELID_SLIME_BALL, 1), Cost.of(ModItems.SUBLIMATING_MAGMA, 1), Cost.of(ModItems.THERMAL_SLIMEBALL, 32)),
					List.of(com.huntlike.gear.ModScythes.SLIMYSTEEL_GLAIVE)),
			new GearTrade("Scythe of Sublimation [Apotheosis]", 9, List.of(Cost.of(com.huntlike.gear.ModScythes.SLIMYSTEEL_GLAIVE, 1),
					Cost.of(ModItems.CORRODED_PALLADIUM, 1), Cost.of(ModItems.SLIMY_CORE, 1), Cost.of(ModItems.THERMAL_SLIMEBALL, 48),
					Cost.of(Items.OBSIDIAN, 8)),
					List.of(com.huntlike.gear.ModScythes.SCYTHE_OF_SUBLIMATION)));

	/** Key stored in a player's seen list once they've obtained a piece of Hunt gear. */
	public static String gearSeenId(Item item) {
		return "gear:" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	/** The Rank that unlocks the trade giving this gear, or -1 if it isn't Hunt gear. */
	public static int gearRank(Item item) {
		for (List<GearTrade> list : List.of(ANIMAL_ARMOR, ANIMAL_WEAPONS, CLUSTER_ARMOR, CLUSTER_WEAPONS)) {
			for (GearTrade trade : list) {
				if (trade.result().contains(item)) {
					return trade.rank();
				}
			}
		}
		return -1;
	}

	/** Trades by page: 0 armor, 1 Misc (Koro's or Towernaar's restocks), 2 weapons. */
	public static List<GearTrade> trades(Hunt hunt, int page) {
		if (hunt == CLUSTER) {
			return page == 0 ? CLUSTER_ARMOR : page == 1 ? CLUSTER_SPECIAL : CLUSTER_WEAPONS;
		}
		return page == 0 ? ANIMAL_ARMOR : page == 1 ? ANIMAL_SPECIAL : ANIMAL_WEAPONS;
	}

	/** Ranks that unlock each chapter of the soul's questline (Koro for Animal). */
	public static final int[] QUESTLINE_RANKS = {3, 6, 9, 10};

	public static Hunt hunt(String id) {
		if (com.huntlike.interlude.InterludeDefs.isInterlude(id)) {
			return com.huntlike.interlude.InterludeDefs.hunt();
		}
		for (Hunt hunt : HUNTS) {
			if (hunt.id().equals(id)) {
				return hunt;
			}
		}
		return ANIMAL;
	}

	public static int rankForXp(int xp) {
		int level = 0;
		for (int i = 0; i <= MAX_RANK; i++) {
			if (xp >= RANK_XP[i]) {
				level = i;
			}
		}
		return level;
	}

	/**
	 * A rare drop's odds denominator for num/den odds. The Mystery Concoction (Animal Hunt only) turns "1 in X" into
	 * "1 in X-1": 1/10 becomes 1/9, and 2/25 (1 in 12.5) becomes 2/23 (1 in 11.5).
	 */
	public static int odds(String hunt, int num, int den, boolean concoction) {
		return concoction && ("animal".equals(hunt) || "cluster".equals(hunt)) ? Math.max(num, den - num) : den;
	}

	/** The player's luck item for this Hunt: Mystery Concoction (Animal) or Warm and Slimy Beef Jerky (Cluster). */
	public static boolean luck(HuntData data, String hunt) {
		return "cluster".equals(hunt) ? data.jerky : "animal".equals(hunt) && data.concoction;
	}

	/** Daily Quest bonus to rare-drop chances (applied after the Mystery Concoction). */
	public static final double DAILY_ODDS = 1.5;

	/** The chance (0-1) of a rare drop: num/den, with the Concoction's 1/(X-1), then x1.5 on a Daily Quest. */
	public static double chance(String hunt, int num, int den, boolean concoction, boolean daily) {
		double chance = num / (double) odds(hunt, num, den, concoction);
		return Math.min(1.0, daily ? chance * DAILY_ODDS : chance);
	}

	/** Boss drops for a tier of the Animal Hunt, owner odds. */
	public static List<Drop> drops(Tier tier) {
		if (isCluster(tier)) {
			return clusterDrops(tier);
		}
		int t = tier.number();
		boolean five = t == 5;
		java.util.ArrayList<Drop> list = new java.util.ArrayList<>();
		list.add(new Drop("low_quality_mystery_meat", ModItems.LOW_QUALITY_MYSTERY_MEAT, t * 2, t * 3, 1, 1, 0));
		if (t >= 2) {
			list.add(new Drop("thick_hide", ModItems.THICK_HIDE, 1, 1, 1, five ? 5 : 10, 1));
		}
		if (t >= 3) {
			list.add(new Drop("large_femur", ModItems.LARGE_FEMUR, 1, 1, five ? 2 : 1, 25, 2));
		}
		if (t >= 4) {
			list.add(new Drop("golden_moss", ModItems.GOLDEN_MOSS, 1, 1, 1, five ? 25 : 50, 3));
		}
		if (t >= 5) {
			list.add(new Drop("spiritual_catalyst", ModItems.SPIRITUAL_CATALYST, 1, 1, 1, 100, 4));
		}
		return list;
	}


	/** Cluster Hunt boss drops (Ice and Packed Ice show no chat message). */
	public static List<Drop> clusterDrops(Tier tier) {
		int t = tier.number();
		int[][] thermal = {{1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 5}};
		java.util.ArrayList<Drop> list = new java.util.ArrayList<>();
		list.add(new Drop("thermal_slimeball", ModItems.THERMAL_SLIMEBALL, thermal[t - 1][0], thermal[t - 1][1], 1, 1, 0));
		list.add(new Drop("slimy_core", ModItems.SLIMY_CORE, 1, 1, 1, 25, 2));
		if (t >= 2) {
			list.add(new Drop("ionic_liquid", ModItems.IONIC_LIQUID, 1, 1, 1, 10, 1));
		}
		if (t >= 3) {
			list.add(new Drop("sublimating_magma", ModItems.SUBLIMATING_MAGMA, 1, 1, 1, 25, 2));
		}
		if (t >= 4) {
			list.add(new Drop("corroded_palladium", ModItems.CORRODED_PALLADIUM, 1, 1, 1, 50, 3));
		}
		if (t >= 5) {
			list.add(new Drop("slime_washed_holmium", ModItems.SLIME_WASHED_HOLMIUM, 1, 1, 1, 100, 4));
		}
		list.add(new Drop("ice", Items.ICE, 1, 1, 1, 2, 0));
		list.add(new Drop("packed_ice", Items.PACKED_ICE, 1, 1, 1, 2, 0));
		return list;
	}
}
