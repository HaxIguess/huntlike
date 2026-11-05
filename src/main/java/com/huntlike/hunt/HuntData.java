package com.huntlike.hunt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

/** Everything HuntLike saves about one player: Ranks, tokens, stats and the running quest. */
public final class HuntData {
	public static final Codec<HuntData> CODEC = CompoundTag.CODEC.xmap(HuntData::fromTag, HuntData::toTag);

	public static final int PHASE_KILLING = 0;
	public static final int PHASE_SUMMONING = 1;
	public static final int PHASE_BOSS = 2;
	/** Daily Quest charges given every new world day. */
	public static final int DAILY_CHARGES = 5;
	/** Daily Quests unlock at this Rank of a Hunt, so new players learn quests first. */
	public static final int DAILY_UNLOCK_RANK = 1;

	public String selectedHunt = "animal";
	/** Ate the Mystery Concoction: rare drops roll at 1/(X-1). */
	public boolean concoction;
	/** Warm and Slimy Beef Jerky eaten: better Cluster rare-drop odds. */
	public boolean jerky;
	/** Opened a Hunt menu panel once in this world: the "click a symbol" hint stays hidden. */
	public boolean hintSeen;
	/** Koro: chapters finished (0-4), and steps shown of the chapter in progress (0 = not started). */
	public int koroDone;
	/** Spirit Lure mobs this player has slain, all time. */
	public int luredKills;
	public int koroStep;
	/** Rogue Hunters this player has slain, all time. */
	public int rogueKills;
	/** Special Rogue Hunters slain, all time. */
	public int specialRogueKills;
	/** Spirit Lure kills since the last Rogue Hunter (the 30th always brings one). */
	public int rogueLures;
	/** Rogue Hunters since the last special one (the 10th is always special). */
	public int rogueSinceSpecial;
	/** Towernaar: chapters finished (0-4). */
	public int towernaarDone;
	/** Towernaar: steps shown of the chapter in progress (0 = not started). */
	public int towernaarStep;
	/** Rogue Hunter kills when the current kill quest started (only later kills count). */
	public int towernaarKillBase;
	/** Interlude Tokens (1 gold ingot = 16), shared by every Interlude. */
	public int interludeTokens;
	/** Used the Magnetic Wand PRD: the Magnetic Wand trade is open. */
	public boolean wandUnlocked;
	/** Soul Nexus points on each corner. */
	public int nexusHealth;
	public int nexusDefense;
	public int nexusDamage;
	/** Path of the Wildfire: goals reached (0 = undiscovered), blazes slain on the Slay goal, buffs bought (bits). */
	public int wildfireGoals;
	public int wildfireBlazes;
	/** Evilborns slain on the tenth goal. */
	public int wildfireEvilborns;
	public int wildfireBuffs;
	/** Daily Quest charges left today (0-5), and the world day they were last refilled on. */
	public int dailyCharges = DAILY_CHARGES;
	public long dailyDay = -1;
	public final Map<String, Progress> progress = new HashMap<>();
	/** The running quest, or null. A Hunting Pack quest sits here on the member running it, but is saved with the pack. */
	public Quest quest;
	/** The player's own quest, paused while they are in a Hunting Pack (its quota shows "paused"), or null. */
	public Quest pausedQuest;

	/** Per-Hunt progress. */
	public static final class Progress {
		public int xp;
		public int tokens;
		public final int[] bossKills = new int[5];
		/** Ids of every drop this player has received at least once. */
		public final Set<String> dropsSeen = new HashSet<>();
		/** How many of each drop this player has received. */
		public final Map<String, Integer> dropCounts = new HashMap<>();
		/** Mutated bosses killed, by "tier:Mutation" (for the Hunter's Journal). */
		public final Map<String, Integer> mutationKills = new HashMap<>();

		public int rank() {
			return HuntDefs.rankForXp(xp);
		}
	}

	/** A running Hunting Quest. */
	public static final class Quest {
		public String hunt = "animal";
		public int tier = 1;
		public int kills;
		public int quota;
		public int phase = PHASE_KILLING;
		public String bossUuid = "";
		public long bossSpawnTime;
		/** Started with a Daily Quest charge: x2 XP and +50% rare-drop odds for the owner. */
		public boolean daily;
		public int summonX;
		public int summonY;
		public int summonZ;
		public int summonTicks;
		/** A Hunting Pack quest: id of the pack, members when it started (quota and boss health scale), kicked players. */
		public String pack = "";
		/** The boss's mutation ("Mineral", "Bejeweled"), or "" for none; rolled when it is summoned. */
		public String mutation = "";
		public int packSize = 1;
		public java.util.Set<String> kicked = new java.util.HashSet<>();

		public boolean isPack() {
			return !pack.isEmpty();
		}
	}

	public Progress progress(String hunt) {
		return progress.computeIfAbsent(hunt, h -> new Progress());
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("selected", selectedHunt);
		tag.putBoolean("concoction", concoction);
		tag.putBoolean("jerky", jerky);
		tag.putBoolean("hintSeen", hintSeen);
		tag.putInt("koroDone", koroDone);
		tag.putInt("luredKills", luredKills);
		tag.putInt("koroStep", koroStep);
		tag.putInt("rogueKills", rogueKills);
		tag.putInt("specialRogueKills", specialRogueKills);
		tag.putInt("rogueLures", rogueLures);
		tag.putInt("rogueSinceSpecial", rogueSinceSpecial);
		tag.putInt("towernaarDone", towernaarDone);
		tag.putInt("towernaarStep", towernaarStep);
		tag.putInt("towernaarKillBase", towernaarKillBase);
		tag.putBoolean("wandUnlocked", wandUnlocked);
		tag.putInt("interludeTokens", interludeTokens);
		tag.putInt("nexusHealth", nexusHealth);
		tag.putInt("nexusDefense", nexusDefense);
		tag.putInt("nexusDamage", nexusDamage);
		tag.putInt("wildfireGoals", wildfireGoals);
		tag.putInt("wildfireBlazes", wildfireBlazes);
		tag.putInt("wildfireEvilborns", wildfireEvilborns);
		tag.putInt("wildfireBuffs", wildfireBuffs);
		tag.putInt("dailyCharges", dailyCharges);
		tag.putLong("dailyDay", dailyDay);
		CompoundTag hunts = new CompoundTag();
		progress.forEach((id, p) -> {
			CompoundTag h = new CompoundTag();
			h.putInt("xp", p.xp);
			h.putInt("tokens", p.tokens);
			for (int i = 0; i < 5; i++) {
				h.putInt("kills" + (i + 1), p.bossKills[i]);
			}
			CompoundTag seen = new CompoundTag();
			for (String drop : p.dropsSeen) {
				seen.putInt(drop, p.dropCounts.getOrDefault(drop, 0));
			}
			h.put("drops", seen);
			CompoundTag mutations = new CompoundTag();
			p.mutationKills.forEach(mutations::putInt);
			h.put("mutationKills", mutations);
			hunts.put(id, h);
		});
		tag.put("hunts", hunts);
		if (quest != null && !quest.isPack()) {
			tag.put("quest", questTag(quest));
		}
		if (pausedQuest != null) {
			tag.put("pausedQuest", questTag(pausedQuest));
		}
		return tag;
	}

	public static CompoundTag questTag(Quest quest) {
		{
			CompoundTag q = new CompoundTag();
			q.putString("hunt", quest.hunt);
			q.putInt("tier", quest.tier);
			q.putInt("kills", quest.kills);
			q.putInt("quota", quest.quota);
			q.putInt("phase", quest.phase);
			q.putString("boss", quest.bossUuid);
			q.putLong("bossSpawnTime", quest.bossSpawnTime);
			q.putInt("sx", quest.summonX);
			q.putInt("sy", quest.summonY);
			q.putInt("sz", quest.summonZ);
			q.putInt("summonTicks", quest.summonTicks);
			q.putBoolean("daily", quest.daily);
			q.putString("pack", quest.pack);
			q.putString("mutation", quest.mutation);
			q.putInt("packSize", quest.packSize);
			q.putString("kicked", String.join(",", quest.kicked));
			return q;
		}
	}

	public static HuntData fromTag(CompoundTag tag) {
		HuntData data = new HuntData();
		data.selectedHunt = tag.getStringOr("selected", "animal");
		data.concoction = tag.getBooleanOr("concoction", false);
		data.jerky = tag.getBooleanOr("jerky", false);
		data.hintSeen = tag.getBooleanOr("hintSeen", false);
		data.koroDone = tag.getIntOr("koroDone", 0);
		data.luredKills = tag.getIntOr("luredKills", 0);
		data.koroStep = tag.getIntOr("koroStep", 0);
		data.rogueKills = tag.getIntOr("rogueKills", 0);
		data.specialRogueKills = tag.getIntOr("specialRogueKills", 0);
		data.rogueLures = tag.getIntOr("rogueLures", 0);
		data.rogueSinceSpecial = tag.getIntOr("rogueSinceSpecial", 0);
		data.towernaarDone = tag.getIntOr("towernaarDone", 0);
		data.towernaarStep = tag.getIntOr("towernaarStep", 0);
		data.towernaarKillBase = tag.getIntOr("towernaarKillBase", 0);
		data.wandUnlocked = tag.getBooleanOr("wandUnlocked", false);
		data.interludeTokens = tag.getIntOr("interludeTokens", 0);
		data.nexusHealth = tag.getIntOr("nexusHealth", 0);
		data.nexusDefense = tag.getIntOr("nexusDefense", 0);
		data.nexusDamage = tag.getIntOr("nexusDamage", 0);
		data.wildfireGoals = tag.getIntOr("wildfireGoals", 0);
		data.wildfireBlazes = tag.getIntOr("wildfireBlazes", 0);
		data.wildfireEvilborns = tag.getIntOr("wildfireEvilborns", 0);
		data.wildfireBuffs = tag.getIntOr("wildfireBuffs", 0);
		data.dailyCharges = tag.getIntOr("dailyCharges", DAILY_CHARGES);
		data.dailyDay = tag.getLongOr("dailyDay", -1L);
		CompoundTag hunts = tag.getCompoundOrEmpty("hunts");
		for (String id : hunts.keySet()) {
			CompoundTag h = hunts.getCompoundOrEmpty(id);
			Progress p = data.progress(id);
			p.xp = h.getIntOr("xp", 0);
			p.tokens = h.getIntOr("tokens", 0);
			for (int i = 0; i < 5; i++) {
				p.bossKills[i] = h.getIntOr("kills" + (i + 1), 0);
			}
			CompoundTag seen = h.getCompoundOrEmpty("drops");
			for (String drop : seen.keySet()) {
				p.dropsSeen.add(drop);
				p.dropCounts.put(drop, seen.getIntOr(drop, 0));
			}
			CompoundTag mutations = h.getCompoundOrEmpty("mutationKills");
			for (String key : mutations.keySet()) {
				p.mutationKills.put(key, mutations.getIntOr(key, 0));
			}
		}
		if (tag.contains("quest")) {
			data.quest = questFrom(tag.getCompoundOrEmpty("quest"));
		}
		if (tag.contains("pausedQuest")) {
			data.pausedQuest = questFrom(tag.getCompoundOrEmpty("pausedQuest"));
		}
		return data;
	}

	public static Quest questFrom(CompoundTag q) {
		{
			Quest quest = new Quest();
			quest.hunt = q.getStringOr("hunt", "animal");
			quest.tier = q.getIntOr("tier", 1);
			quest.kills = q.getIntOr("kills", 0);
			quest.quota = q.getIntOr("quota", 4);
			quest.phase = q.getIntOr("phase", PHASE_KILLING);
			quest.bossUuid = q.getStringOr("boss", "");
			quest.bossSpawnTime = q.getLongOr("bossSpawnTime", 0L);
			quest.summonX = q.getIntOr("sx", 0);
			quest.summonY = q.getIntOr("sy", 0);
			quest.summonZ = q.getIntOr("sz", 0);
			quest.summonTicks = q.getIntOr("summonTicks", 0);
			quest.daily = q.getBooleanOr("daily", false);
			quest.pack = q.getStringOr("pack", "");
			quest.mutation = q.getStringOr("mutation", "");
			quest.packSize = q.getIntOr("packSize", 1);
			for (String id : q.getStringOr("kicked", "").split(",")) {
				if (!id.isEmpty()) {
					quest.kicked.add(id);
				}
			}
			return quest;
		}
	}
}
