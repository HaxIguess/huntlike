package com.huntlike.hunt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.huntlike.HuntLike;
import com.huntlike.hunt.HuntData.Quest;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.HuntDefs.Tier;

/**
 * Hunting Packs (the A-Phone's side tab): players group up for the main Hunts. The leader names the pack, sets its
 * size limit, invites (every online player outside the pack can be invited; invites wait in the receiver's inbox for 10
 * minutes or until they log out), kicks, promotes, and picks and starts the Hunt (from the A-Phone or an Alpha Altar).
 *
 * <p>A pack quest is shared: its quota is the tier's quota times the members when it started, any member's kills count,
 * and the boss (health times that number, everything else the same) is summoned at the leader one second later than
 * usual. Every member pays their own tokens, rolls their own drops and gets 25% more experience, wherever they are;
 * members who are offline when it dies get their share on their next login, the items in their Stash. Being in a pack
 * pauses a member's own quest. Nobody joins or leaves during a pack quest, but the leader can kick: a kicked player stops
 * counting and gets no experience, only the drops if they hit the boss.
 *
 * <p>The quest is saved with the pack (data/huntlike_packs.dat), not in any player's data; while it runs it sits on one
 * online member, the "runner" (the leader when online), so the solo quest code (Spirit Lure, summoning, the boss) drives
 * it. If the runner logs out it moves to another online member; a living boss is then put back to one kill short.
 */
public final class HuntingPacks {
	private HuntingPacks() {
	}

	public static final int NAME_LENGTH = 40;
	public static final int INVITE_TICKS = 10 * 60 * 20;
	/** Members get 25% more experience than a solo kill. */
	public static final int EXPERIENCE = Math.round(QuestManager.BOSS_EXPERIENCE * 1.25F);
	public static final int EXTRA_SUMMON_TICKS = 20;
	public static final String REQUIREMENTS = "Some Hunters don't meet the requirements for this boss...";

	public static final class Pack {
		UUID id;
		String name;
		UUID leader;
		/** In joining order; the leader is one of them. */
		final List<UUID> members = new ArrayList<>();
		final Map<UUID, String> names = new HashMap<>();
		/** 0 = no limit. */
		int max;
		Quest quest;
		/** The online member the running quest sits on (not saved). */
		UUID runner;

		boolean grouped() {
			return members.size() >= 2;
		}

		public UUID id() {
			return id;
		}

		public UUID leader() {
			return leader;
		}

		public List<UUID> members() {
			return List.copyOf(members);
		}

		public Quest quest() {
			return quest;
		}

		public String name() {
			return name;
		}
	}

	private record Invite(UUID pack, String from, String packName, long expires) {
	}

	/** A boss the pack beat while this member was offline: given on their next login. */
	private record Credit(String hunt, int tier, String mutation) {
	}

	private static final Map<UUID, Pack> PACKS = new LinkedHashMap<>();
	private static final Map<UUID, UUID> PACK_OF = new HashMap<>();
	private static final Map<UUID, List<ItemStack>> STASH = new HashMap<>();
	private static final Map<UUID, List<Credit>> CREDITS = new HashMap<>();
	private static final Map<UUID, List<Invite>> INVITES = new HashMap<>();
	/** Players with an invite they haven't looked at (the red dot). */
	private static final Set<UUID> UNSEEN = new HashSet<>();
	private static boolean dirty;
	private static MinecraftServer server;

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			save();
			PACKS.clear();
			PACK_OF.clear();
			STASH.clear();
			CREDITS.clear();
			INVITES.clear();
			UNSEEN.clear();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(HuntingPacks::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.player));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
				(entity, source, amount) -> !(entity instanceof ServerPlayer target && source.getEntity() instanceof ServerPlayer attacker
						&& attacker != target && samePack(attacker, target)));
	}

	/** Packmates can't hurt each other (melee, arrows, anything they cause), quest or not. */
	public static boolean samePack(ServerPlayer a, ServerPlayer b) {
		Pack pack = packOf(a.getUUID());
		return pack != null && pack.grouped() && pack.members.contains(b.getUUID());
	}

	// ---------------------------------------------------------------- lookups

	public static Pack packOf(UUID player) {
		UUID id = PACK_OF.get(player);
		return id == null ? null : PACKS.get(id);
	}

	/** In a pack with at least one other player. */
	public static boolean grouped(ServerPlayer player) {
		Pack pack = packOf(player.getUUID());
		return pack != null && pack.grouped();
	}

	public static boolean isLeader(ServerPlayer player) {
		Pack pack = packOf(player.getUUID());
		return pack != null && pack.leader.equals(player.getUUID());
	}

	/** The running pack quest this player's kills count for, or null. */
	public static Quest packQuest(ServerPlayer player) {
		Pack pack = packOf(player.getUUID());
		return pack == null || !pack.grouped() ? null : pack.quest;
	}

	/** Whether the Hunt menu should open on the Hunting Pack screen (a pack quest is running). */
	public static boolean redirect(ServerPlayer player) {
		return packQuest(player) != null;
	}

	/** The quest a kill by this player counts for: their own, or their pack's. */
	public static Quest questFor(ServerPlayer player, HuntData data) {
		return data.quest != null ? data.quest : packQuest(player);
	}

	private static Pack ownPack(ServerPlayer player) {
		Pack pack = packOf(player.getUUID());
		if (pack == null) {
			pack = new Pack();
			pack.id = UUID.randomUUID();
			pack.name = player.getName().getString() + "'s Group";
			pack.leader = player.getUUID();
			pack.members.add(player.getUUID());
			PACKS.put(pack.id, pack);
			PACK_OF.put(player.getUUID(), pack.id);
			dirty = true;
		}
		pack.names.put(player.getUUID(), player.getName().getString());
		return pack;
	}

	private static List<ServerPlayer> online(Pack pack) {
		List<ServerPlayer> out = new ArrayList<>();
		if (server == null) {
			return out;
		}
		for (UUID id : pack.members) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				out.add(player);
			}
		}
		return out;
	}

	private static ServerPlayer player(UUID id) {
		return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
	}

	private static String name(Pack pack, UUID id) {
		ServerPlayer player = player(id);
		return player != null ? player.getName().getString() : pack.names.getOrDefault(id, "?");
	}

	private static void tell(Pack pack, String text, int color) {
		for (ServerPlayer member : online(pack)) {
			member.sendSystemMessage(Messages.c(text, color));
		}
	}

	private static void syncAll(Pack pack) {
		for (ServerPlayer member : online(pack)) {
			QuestManager.sync(member);
		}
	}

	private static boolean liveBoss(HuntData data) {
		return data.quest != null && data.quest.phase != HuntData.PHASE_KILLING;
	}

	// ---------------------------------------------------------------- actions from the screen

	/** Returns true if the action was a pack action. */
	public static boolean handle(ServerPlayer player, String action, String arg, int value) {
		switch (action) {
			case "pack_name" -> rename(player, arg);
			case "pack_max" -> setMax(player, value);
			case "pack_invite" -> invite(player, arg);
			case "pack_accept" -> accept(player, arg);
			case "pack_decline" -> decline(player, arg);
			case "pack_seen" -> UNSEEN.remove(player.getUUID());
			case "pack_kick" -> kick(player, arg);
			case "pack_promote" -> promote(player, arg);
			case "pack_leave" -> leave(player);
			case "pack_disband" -> disband(player);
			case "stash_take" -> takeStash(player, value);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static Pack ledPack(ServerPlayer player) {
		Pack pack = ownPack(player);
		if (!pack.leader.equals(player.getUUID())) {
			player.sendSystemMessage(Messages.error("Only the pack's leader can do that."));
			return null;
		}
		return pack;
	}

	private static void rename(ServerPlayer player, String name) {
		Pack pack = ledPack(player);
		if (pack == null) {
			return;
		}
		String clean = name.replaceAll("[^A-Za-z0-9 ]", "").trim();
		if (clean.length() > NAME_LENGTH) {
			clean = clean.substring(0, NAME_LENGTH).trim();
		}
		if (!clean.isEmpty()) {
			pack.name = clean;
			dirty = true;
			syncAll(pack);
		}
	}

	private static void setMax(ServerPlayer player, int max) {
		Pack pack = ledPack(player);
		if (pack == null) {
			return;
		}
		if (max != 0 && max < pack.members.size()) {
			player.sendSystemMessage(Messages.error("The pack already has " + pack.members.size() + " members."));
			return;
		}
		pack.max = Math.max(0, Math.min(999, max));
		dirty = true;
		syncAll(pack);
	}

	private static boolean full(Pack pack) {
		return pack.max > 0 && pack.members.size() >= pack.max;
	}

	private static void invite(ServerPlayer player, String target) {
		Pack pack = ledPack(player);
		if (pack == null) {
			return;
		}
		ServerPlayer other;
		try {
			other = player(UUID.fromString(target));
		} catch (IllegalArgumentException e) {
			return;
		}
		if (other == null || other == player || pack.members.contains(other.getUUID())) {
			return;
		}
		if (pack.quest != null) {
			player.sendSystemMessage(Messages.error("Nobody can join while the pack is on a Hunt."));
			return;
		}
		if (full(pack)) {
			player.sendSystemMessage(Messages.error("The pack is full."));
			return;
		}
		List<Invite> list = INVITES.computeIfAbsent(other.getUUID(), id -> new ArrayList<>());
		list.removeIf(invite -> invite.pack().equals(pack.id));
		list.add(new Invite(pack.id, player.getName().getString(), pack.name, server.getTickCount() + INVITE_TICKS));
		UNSEEN.add(other.getUUID());
		player.sendSystemMessage(Messages.c("Invite sent to " + other.getName().getString() + ".", HuntDefs.GREEN));
		QuestManager.playSound(other, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F);
		QuestManager.sync(other);
	}

	/** Why this player can't accept this invite right now, or null. */
	private static String acceptBlocked(ServerPlayer player, Invite invite) {
		Pack pack = PACKS.get(invite.pack());
		if (pack == null) {
			return "This pack no longer exists.";
		}
		if (grouped(player)) {
			return "Leave your current pack first.";
		}
		if (liveBoss(QuestManager.data(player))) {
			return "Finish or cancel your boss fight first.";
		}
		if (pack.quest != null) {
			return "The pack is on a Hunt right now.";
		}
		if (full(pack)) {
			return "The pack is full.";
		}
		ServerPlayer leader = player(pack.leader);
		if (leader != null && !pack.grouped() && liveBoss(QuestManager.data(leader))) {
			return "The pack's leader is fighting a boss.";
		}
		return null;
	}

	private static Invite findInvite(ServerPlayer player, String packId) {
		for (Invite invite : INVITES.getOrDefault(player.getUUID(), List.of())) {
			if (invite.pack().toString().equals(packId)) {
				return invite;
			}
		}
		return null;
	}

	private static void accept(ServerPlayer player, String packId) {
		Invite invite = findInvite(player, packId);
		if (invite == null) {
			return;
		}
		String blocked = acceptBlocked(player, invite);
		if (blocked != null) {
			player.sendSystemMessage(Messages.error(blocked));
			return;
		}
		INVITES.get(player.getUUID()).remove(invite);
		Pack old = packOf(player.getUUID());
		if (old != null) {
			old.members.remove(player.getUUID());
			if (old.members.isEmpty()) {
				PACKS.remove(old.id);
			}
		}
		Pack pack = PACKS.get(invite.pack());
		pack.members.add(player.getUUID());
		pack.names.put(player.getUUID(), player.getName().getString());
		PACK_OF.put(player.getUUID(), pack.id);
		dirty = true;
		tell(pack, player.getName().getString() + " joined " + pack.name + "!", HuntDefs.GREEN);
		for (ServerPlayer member : online(pack)) {
			pause(member);
			QuestManager.playSound(member, SoundEvents.PLAYER_LEVELUP, 1.6F);
		}
		syncAll(pack);
	}

	private static void decline(ServerPlayer player, String packId) {
		Invite invite = findInvite(player, packId);
		if (invite != null) {
			INVITES.get(player.getUUID()).remove(invite);
		}
	}

	private static UUID memberArg(Pack pack, String arg) {
		try {
			UUID id = UUID.fromString(arg);
			return pack.members.contains(id) ? id : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void kick(ServerPlayer player, String arg) {
		Pack pack = ledPack(player);
		UUID target = pack == null ? null : memberArg(pack, arg);
		if (target == null || target.equals(pack.leader)) {
			return;
		}
		String kickedName = name(pack, target);
		if (pack.quest != null) {
			pack.quest.kicked.add(target.toString());
		}
		removeMember(pack, target);
		tell(pack, kickedName + " was kicked from the pack.", HuntDefs.RED);
		ServerPlayer kicked = player(target);
		if (kicked != null) {
			kicked.sendSystemMessage(Messages.c("You were kicked from " + pack.name + ".", HuntDefs.RED));
		}
	}

	private static void promote(ServerPlayer player, String arg) {
		Pack pack = ledPack(player);
		UUID target = pack == null ? null : memberArg(pack, arg);
		if (target == null || target.equals(pack.leader)) {
			return;
		}
		pack.leader = target;
		dirty = true;
		tell(pack, name(pack, target) + " now leads the pack.", HuntDefs.GOLD);
		syncAll(pack);
	}

	private static void leave(ServerPlayer player) {
		Pack pack = packOf(player.getUUID());
		if (pack == null || !pack.grouped()) {
			return;
		}
		if (pack.quest != null) {
			player.sendSystemMessage(Messages.error("Nobody can leave while the pack is on a Hunt."));
			return;
		}
		removeMember(pack, player.getUUID());
		tell(pack, player.getName().getString() + " left the pack.", HuntDefs.RED);
		player.sendSystemMessage(Messages.c("You left " + pack.name + ".", HuntDefs.RED));
	}

	private static void disband(ServerPlayer player) {
		Pack pack = ledPack(player);
		if (pack == null || !pack.grouped()) {
			return;
		}
		if (pack.quest != null) {
			player.sendSystemMessage(Messages.error("The pack can't be disbanded during a Hunt."));
			return;
		}
		tell(pack, pack.name + " was disbanded.", HuntDefs.RED);
		for (UUID member : List.copyOf(pack.members)) {
			if (!member.equals(pack.leader)) {
				removeMember(pack, member);
			}
		}
	}

	/** Takes a member out: they get a pack of their own again, and their own quest back. */
	private static void removeMember(Pack pack, UUID member) {
		pack.members.remove(member);
		PACK_OF.remove(member);
		if (pack.leader.equals(member) && !pack.members.isEmpty()) {
			pack.leader = pack.members.get(0);
		}
		if (pack.members.isEmpty()) {
			PACKS.remove(pack.id);
		}
		dirty = true;
		ServerPlayer gone = player(member);
		if (gone != null) {
			ownPack(gone);
			if (gone.getUUID().equals(pack.runner) && pack.quest != null) {
				HuntData data = QuestManager.data(gone);
				if (pack.quest.phase != HuntData.PHASE_KILLING) {
					QuestManager.resetToQuotaMinusOne(gone, data);
				}
				data.quest = null;
				QuestManager.save(gone, data);
				pack.runner = null;
			}
			com.huntlike.hunt.SpiritLure.cleanup(gone);
			resume(gone);
			QuestManager.sync(gone);
		}
		if (!pack.grouped()) {
			for (ServerPlayer left : online(pack)) {
				if (pack.quest == null) {
					resume(left);
				}
			}
		}
		syncAll(pack);
	}

	/** In a pack: the player's own quest waits (only while its boss isn't out). */
	private static void pause(ServerPlayer player) {
		HuntData data = QuestManager.data(player);
		if (data.quest != null && !data.quest.isPack() && data.quest.phase == HuntData.PHASE_KILLING && data.pausedQuest == null) {
			data.pausedQuest = data.quest;
			data.quest = null;
			com.huntlike.hunt.SpiritLure.cleanup(player);
			QuestManager.save(player, data);
			player.sendSystemMessage(Messages.c("Your own quest is paused while you are in a Hunting Pack.", HuntDefs.GOLD));
		}
	}

	private static void resume(ServerPlayer player) {
		HuntData data = QuestManager.data(player);
		if (data.pausedQuest != null && data.quest == null && !grouped(player)) {
			data.quest = data.pausedQuest;
			data.pausedQuest = null;
			com.huntlike.hunt.SpiritLure.reset(player);
			QuestManager.save(player, data);
			player.sendSystemMessage(Messages.c("Your own quest continues.", HuntDefs.GREEN));
		}
	}

	// ---------------------------------------------------------------- the pack quest

	/** Why a member can't take this tier, or null (online, Hunt and tier unlocked, previous boss, tokens, no boss out). */
	private static String memberBlocked(ServerPlayer member, Hunt hunt, Tier tier) {
		if (member == null) {
			return "offline";
		}
		HuntData data = QuestManager.data(member);
		HuntData.Progress progress = data.progress(hunt.id());
		if (!HuntDefs.huntUnlocked(data, hunt) || progress.rank() < tier.unlockRank()
				|| tier.number() > 1 && progress.bossKills[tier.number() - 2] == 0) {
			return "locked";
		}
		if (progress.tokens < tier.tokenCost()) {
			return "tokens";
		}
		if (liveBoss(data) && !data.quest.isPack()) {
			return "busy";
		}
		return null;
	}

	public static boolean everyoneMeets(Pack pack, Hunt hunt, Tier tier) {
		for (UUID id : pack.members) {
			if (memberBlocked(player(id), hunt, tier) != null) {
				return false;
			}
		}
		return true;
	}

	/** The leader pressed Start (in the A-Phone or at an Alpha Altar) while in a pack. */
	public static void start(ServerPlayer leader, Hunt hunt, int tierNumber) {
		Pack pack = packOf(leader.getUUID());
		if (pack == null || !pack.grouped()) {
			return;
		}
		if (!pack.leader.equals(leader.getUUID())) {
			leader.sendSystemMessage(Messages.error("Only the pack's leader can start a Hunt."));
			return;
		}
		if (pack.quest != null) {
			leader.sendSystemMessage(Messages.error("The pack is already on a Hunt."));
			return;
		}
		if (QuestManager.isPeaceful(leader)) {
			leader.sendSystemMessage(Messages.peacefulStart());
			return;
		}
		if (com.huntlike.interlude.InterludeDefs.isInterlude(hunt) || tierNumber < 1 || tierNumber > hunt.tiers().size()) {
			return;
		}
		Tier tier = hunt.tier(tierNumber);
		if (!everyoneMeets(pack, hunt, tier)) {
			leader.sendSystemMessage(Messages.error(REQUIREMENTS));
			return;
		}
		Quest quest = new Quest();
		quest.hunt = hunt.id();
		quest.tier = tierNumber;
		quest.pack = pack.id.toString();
		quest.packSize = pack.members.size();
		quest.quota = tier.quota() * quest.packSize;
		for (ServerPlayer member : online(pack)) {
			pause(member);
			HuntData data = QuestManager.data(member);
			data.progress(hunt.id()).tokens -= tier.tokenCost();
			QuestManager.save(member, data);
			com.huntlike.hunt.SpiritLure.reset(member);
			member.sendSystemMessage(Messages.started(hunt, tierNumber, false));
			QuestManager.playSound(member, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F);
			QuestManager.playSound(member, SoundEvents.BEACON_ACTIVATE, 1.2F);
			QuestManager.award(member, "animal/conquest");
		}
		pack.quest = quest;
		setRunner(pack, leader);
		dirty = true;
		syncAll(pack);
	}

	private static void setRunner(Pack pack, ServerPlayer runner) {
		pack.runner = runner.getUUID();
		HuntData data = QuestManager.data(runner);
		data.quest = pack.quest;
		QuestManager.save(runner, data);
	}

	/** Quota points from a member's kill. The boss is summoned at the leader, a second later than usual. */
	static void addPoints(ServerPlayer killer, Quest quest, int points) {
		Pack pack = PACKS.get(UUID.fromString(quest.pack));
		ServerPlayer runner = pack == null ? null : player(pack.runner);
		if (runner == null) {
			return;
		}
		quest.kills = Math.min(quest.quota, quest.kills + points);
		com.huntlike.hunt.SpiritLure.onQuotaKill(killer);
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		killer.sendSystemMessage(Messages.quota(hunt, quest.kills, quest.quota), true);
		killer.level().playSound(null, killer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS,
				0.6F, 0.6F + 0.9F * quest.kills / Math.max(1, quest.quota));
		if (quest.kills >= quest.quota) {
			ServerPlayer leader = player(pack.leader);
			BlockPos at = leader != null && leader.level() == runner.level() ? leader.blockPosition() : runner.blockPosition();
			QuestManager.beginSummon(runner, QuestManager.data(runner), quest, at);
			if (quest.phase == HuntData.PHASE_SUMMONING) {
				quest.summonTicks += EXTRA_SUMMON_TICKS;
			}
		}
		dirty = true;
		syncAll(pack);
	}

	/** The pack quest failed or the leader cancelled it: over for everyone, no refund. */
	public static void fail(ServerPlayer runner, Quest quest) {
		Pack pack = PACKS.get(UUID.fromString(quest.pack));
		BossController.discardBoss(runner, quest);
		HuntData data = QuestManager.data(runner);
		if (data.quest == quest) {
			data.quest = null;
			QuestManager.save(runner, data);
		}
		if (pack == null) {
			return;
		}
		pack.quest = null;
		pack.runner = null;
		dirty = true;
		for (ServerPlayer member : online(pack)) {
			com.huntlike.hunt.SpiritLure.cleanup(member);
			member.sendSystemMessage(Messages.failed(HuntDefs.hunt(quest.hunt), quest.tier));
			QuestManager.playSound(member, SoundEvents.BEACON_DEACTIVATE, 0.8F);
		}
		syncAll(pack);
	}

	/** The pack's boss died: every member's share, the kicked players' drops, the other helpers' usual share. */
	static void complete(ServerPlayer runner, Quest quest, Collection<UUID> helpers) {
		Pack pack = PACKS.get(UUID.fromString(quest.pack));
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		HuntData runnerData = QuestManager.data(runner);
		runnerData.quest = null;
		QuestManager.save(runner, runnerData);
		Set<UUID> members = new HashSet<>(pack == null ? List.of(runner.getUUID()) : pack.members);
		for (UUID id : members) {
			ServerPlayer member = player(id);
			if (member == null) {
				CREDITS.computeIfAbsent(id, k -> new ArrayList<>()).add(new Credit(hunt.id(), tier.number(), quest.mutation));
				continue;
			}
			reward(member, hunt, tier, false, quest.mutation);
		}
		Set<UUID> kicked = new HashSet<>();
		for (String id : quest.kicked) {
			kicked.add(UUID.fromString(id));
		}
		for (UUID helperId : helpers) {
			ServerPlayer helper = player(helperId);
			if (helper != null && !members.contains(helperId) && !kicked.contains(helperId)) {
				ExperienceOrb.award(helper.level(), helper.position(), QuestManager.HELPER_EXPERIENCE);
			}
		}
		QuestManager.rewardHelpers(runner, hunt, tier, helpers, members);
		if (pack != null) {
			pack.quest = null;
			pack.runner = null;
			for (ServerPlayer member : online(pack)) {
				com.huntlike.hunt.SpiritLure.cleanup(member);
			}
			syncAll(pack);
		}
		dirty = true;
	}

	/** One member's share: experience at their feet, their own drops (into the Stash when away), the kill credit. */
	private static void reward(ServerPlayer member, Hunt hunt, Tier tier, boolean away, String mutation) {
		HuntData data = QuestManager.data(member);
		ExperienceOrb.award(member.level(), member.position(), EXPERIENCE);
		QuestManager.rollDrops(member, data, hunt, tier, false, (stack, rare) -> {
			if (away) {
				STASH.computeIfAbsent(member.getUUID(), k -> new ArrayList<>()).add(stack);
				dirty = true;
			} else {
				QuestManager.giveStack(member, stack);
			}
		});
		QuestManager.credit(member, data, hunt, tier, false, mutation);
	}

	/** Who hears about the boss: the pack's online members, or just the player. */
	public static List<ServerPlayer> membersOnline(ServerPlayer runner, Quest quest) {
		Pack pack = quest.isPack() ? PACKS.get(UUID.fromString(quest.pack)) : null;
		return pack == null ? List.of(runner) : online(pack);
	}

	/** The boss may run off from the player it chases as long as some member is near it. */
	public static double nearestMember(ServerPlayer runner, Quest quest, Entity boss) {
		double nearest = boss.distanceTo(runner);
		Pack pack = quest.isPack() ? PACKS.get(UUID.fromString(quest.pack)) : null;
		if (pack != null) {
			for (ServerPlayer member : online(pack)) {
				if (member.level() == boss.level()) {
					nearest = Math.min(nearest, boss.distanceTo(member));
				}
			}
		}
		return nearest;
	}

	// ---------------------------------------------------------------- stash

	private static void takeStash(ServerPlayer player, int index) {
		List<ItemStack> stash = STASH.get(player.getUUID());
		if (stash == null || index < 0 || index >= stash.size()) {
			return;
		}
		QuestManager.giveStack(player, stash.remove(index));
		if (stash.isEmpty()) {
			STASH.remove(player.getUUID());
		}
		dirty = true;
		QuestManager.playSound(player, SoundEvents.ITEM_PICKUP, 1.0F);
		QuestManager.sync(player);
	}

	// ---------------------------------------------------------------- ticking, logins, logouts

	private static void tick(MinecraftServer s) {
		if (server == null) {
			return;
		}
		long now = s.getTickCount();
		if (now % 20 == 0) {
			for (Iterator<Map.Entry<UUID, List<Invite>>> it = INVITES.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, List<Invite>> entry = it.next();
				if (entry.getValue().removeIf(invite -> invite.expires() <= now || !PACKS.containsKey(invite.pack()))) {
					ServerPlayer player = player(entry.getKey());
					if (player != null) {
						QuestManager.sync(player);
					}
				}
				if (entry.getValue().isEmpty()) {
					it.remove();
					UNSEEN.remove(entry.getKey());
				}
			}
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				if (grouped(player)) {
					pause(player);
				}
			}
		}
		for (Pack pack : List.copyOf(PACKS.values())) {
			if (pack.quest == null) {
				continue;
			}
			ServerPlayer runner = player(pack.runner);
			if (runner == null || QuestManager.data(runner).quest != pack.quest) {
				pickRunner(pack, runner);
				runner = player(pack.runner);
			}
			if (runner == null || pack.quest == null || pack.quest.phase != HuntData.PHASE_KILLING) {
				continue;
			}
			// Every member's Spirit Lure works for the pack (the runner's runs in the usual quest tick).
			for (ServerPlayer member : online(pack)) {
				if (member != runner && !QuestManager.isPeaceful(member) && !com.huntlike.rogue.RogueHunters.active(member)) {
					com.huntlike.hunt.SpiritLure.tick(member, QuestManager.data(member), pack.quest);
				}
			}
		}
		if (dirty && now % 100 == 0) {
			save();
		}
	}

	/** Moves the running quest onto the leader (or another online member); a boss that was out is put back. */
	private static void pickRunner(Pack pack, ServerPlayer old) {
		if (old != null) {
			// Still online (after a death the respawned player's data lost it): it simply goes back on them.
			HuntData data = QuestManager.data(old);
			if (data.quest == null || data.quest == pack.quest) {
				setRunner(pack, old);
				return;
			}
		}
		ServerPlayer next = player(pack.leader);
		if (next == null) {
			List<ServerPlayer> online = online(pack);
			next = online.isEmpty() ? null : online.get(0);
		}
		if (pack.quest.phase != HuntData.PHASE_KILLING) {
			discardBossAnywhere(pack.quest);
			pack.quest.phase = HuntData.PHASE_KILLING;
			pack.quest.kills = Math.max(0, pack.quest.quota - 1);
			pack.quest.bossUuid = "";
		}
		pack.runner = null;
		if (next != null) {
			setRunner(pack, next);
		}
		dirty = true;
	}

	private static void discardBossAnywhere(Quest quest) {
		if (quest.bossUuid.isEmpty() || server == null) {
			return;
		}
		for (var level : server.getAllLevels()) {
			try {
				Entity boss = level.getEntity(UUID.fromString(quest.bossUuid));
				if (boss != null) {
					boss.discard();
				}
			} catch (IllegalArgumentException e) {
				return;
			}
		}
	}

	/** The runner logs out: the quest leaves their data (it is saved with the pack) and goes to someone else. */
	public static void onLogout(ServerPlayer player) {
		INVITES.remove(player.getUUID());
		UNSEEN.remove(player.getUUID());
		Pack pack = packOf(player.getUUID());
		if (pack == null) {
			return;
		}
		pack.names.put(player.getUUID(), player.getName().getString());
		if (pack.quest != null && player.getUUID().equals(pack.runner)) {
			HuntData data = QuestManager.data(player);
			if (pack.quest.phase != HuntData.PHASE_KILLING) {
				QuestManager.resetToQuotaMinusOne(player, data);
			}
			data.quest = null;
			QuestManager.save(player, data);
			pack.runner = null;
		}
		dirty = true;
	}

	/** The runner changed dimension or died: a boss that was out is put back one kill short. */
	public static void onRunnerInterrupted(ServerPlayer runner, HuntData data) {
		if (data.quest != null && data.quest.isPack() && data.quest.phase != HuntData.PHASE_KILLING) {
			QuestManager.resetToQuotaMinusOne(runner, data);
		}
	}

	private static void onJoin(ServerPlayer player) {
		Pack pack = ownPack(player);
		if (pack.grouped()) {
			pause(player);
		} else {
			resume(player);
		}
		List<Credit> credits = CREDITS.remove(player.getUUID());
		if (credits != null) {
			for (Credit credit : credits) {
				Hunt hunt = HuntDefs.hunt(credit.hunt());
				Tier tier = hunt.tier(credit.tier());
				player.sendSystemMessage(Messages.c("While you were away, your Hunting Pack defeated " + tier.bossName()
						+ "! Your drops are waiting in your Stash.", HuntDefs.GOLD));
				reward(player, hunt, tier, true, credit.mutation());
			}
			dirty = true;
		}
		QuestManager.sync(player);
	}

	// ---------------------------------------------------------------- what the client sees

	public static void writeSync(ServerPlayer player, CompoundTag tag) {
		Pack pack = packOf(player.getUUID());
		if (pack != null) {
			CompoundTag p = new CompoundTag();
			p.putString("id", pack.id.toString());
			p.putString("name", pack.name);
			p.putString("leader", pack.leader.toString());
			p.putInt("max", pack.max);
			ListTag members = new ListTag();
			for (UUID id : pack.members) {
				CompoundTag m = new CompoundTag();
				m.putString("id", id.toString());
				m.putString("name", name(pack, id));
				m.putBoolean("online", player(id) != null);
				members.add(m);
			}
			p.put("members", members);
			if (pack.quest != null) {
				p.put("quest", HuntData.questTag(pack.quest));
			}
			if (pack.leader.equals(player.getUUID()) && pack.grouped()) {
				// For the leader's Start button: whether every member can take each tier.
				CompoundTag ok = new CompoundTag();
				for (Hunt hunt : HuntDefs.HUNTS) {
					byte[] tiers = new byte[hunt.tiers().size()];
					for (Tier tier : hunt.tiers()) {
						tiers[tier.number() - 1] = (byte) (everyoneMeets(pack, hunt, tier) ? 1 : 0);
					}
					ok.putByteArray(hunt.id(), tiers);
				}
				p.put("ok", ok);
			}
			tag.put("pack", p);
		}
		ListTag invites = new ListTag();
		long now = server == null ? 0 : server.getTickCount();
		for (Invite invite : INVITES.getOrDefault(player.getUUID(), List.of())) {
			CompoundTag i = new CompoundTag();
			i.putString("pack", invite.pack().toString());
			i.putString("from", invite.from());
			i.putString("name", invite.packName());
			i.putInt("seconds", (int) Math.max(0, (invite.expires() - now) / 20));
			String blocked = acceptBlocked(player, invite);
			if (blocked != null) {
				i.putString("blocked", blocked);
			}
			invites.add(i);
		}
		tag.put("invites", invites);
		tag.putBoolean("invitesNew", UNSEEN.contains(player.getUUID()));
		ListTag stash = new ListTag();
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		for (ItemStack stack : STASH.getOrDefault(player.getUUID(), List.of())) {
			ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(stash::add);
		}
		tag.put("stash", stash);
	}

	// ---------------------------------------------------------------- saving

	private static Path file() {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("huntlike_packs.dat");
	}

	private static void save() {
		if (server == null) {
			return;
		}
		CompoundTag root = new CompoundTag();
		ListTag packs = new ListTag();
		for (Pack pack : PACKS.values()) {
			CompoundTag p = new CompoundTag();
			p.putString("id", pack.id.toString());
			p.putString("name", pack.name);
			p.putString("leader", pack.leader.toString());
			p.putInt("max", pack.max);
			ListTag members = new ListTag();
			for (UUID id : pack.members) {
				CompoundTag m = new CompoundTag();
				m.putString("id", id.toString());
				m.putString("name", pack.names.getOrDefault(id, "?"));
				members.add(m);
			}
			p.put("members", members);
			if (pack.quest != null) {
				p.put("quest", HuntData.questTag(pack.quest));
			}
			packs.add(p);
		}
		root.put("packs", packs);
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag stash = new CompoundTag();
		STASH.forEach((id, items) -> {
			ListTag list = new ListTag();
			for (ItemStack stack : items) {
				ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(list::add);
			}
			stash.put(id.toString(), list);
		});
		root.put("stash", stash);
		CompoundTag credits = new CompoundTag();
		CREDITS.forEach((id, list) -> {
			ListTag l = new ListTag();
			for (Credit credit : list) {
				CompoundTag c = new CompoundTag();
				c.putString("hunt", credit.hunt());
				c.putInt("tier", credit.tier());
				c.putString("mutation", credit.mutation());
				l.add(c);
			}
			credits.put(id.toString(), l);
		});
		root.put("credits", credits);
		try {
			Files.createDirectories(file().getParent());
			NbtIo.writeCompressed(root, file());
			dirty = false;
		} catch (IOException e) {
			HuntLike.LOGGER.error("Could not save the Hunting Packs", e);
		}
	}

	private static void load() {
		PACKS.clear();
		PACK_OF.clear();
		STASH.clear();
		CREDITS.clear();
		if (!Files.exists(file())) {
			return;
		}
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			HuntLike.LOGGER.error("Could not load the Hunting Packs", e);
			return;
		}
		ListTag packs = root.getListOrEmpty("packs");
		for (int i = 0; i < packs.size(); i++) {
			CompoundTag p = packs.getCompoundOrEmpty(i);
			try {
				Pack pack = new Pack();
				pack.id = UUID.fromString(p.getStringOr("id", ""));
				pack.name = p.getStringOr("name", "Group");
				pack.leader = UUID.fromString(p.getStringOr("leader", ""));
				pack.max = p.getIntOr("max", 0);
				ListTag members = p.getListOrEmpty("members");
				for (int j = 0; j < members.size(); j++) {
					CompoundTag m = members.getCompoundOrEmpty(j);
					UUID id = UUID.fromString(m.getStringOr("id", ""));
					pack.members.add(id);
					pack.names.put(id, m.getStringOr("name", "?"));
					PACK_OF.put(id, pack.id);
				}
				if (p.contains("quest")) {
					pack.quest = HuntData.questFrom(p.getCompoundOrEmpty("quest"));
					if (pack.quest.phase != HuntData.PHASE_KILLING) {
						pack.quest.phase = HuntData.PHASE_KILLING;
						pack.quest.kills = Math.max(0, pack.quest.quota - 1);
						pack.quest.bossUuid = "";
					}
				}
				if (!pack.members.isEmpty()) {
					PACKS.put(pack.id, pack);
				}
			} catch (IllegalArgumentException e) {
				HuntLike.LOGGER.warn("Skipped a broken Hunting Pack");
			}
		}
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag stash = root.getCompoundOrEmpty("stash");
		for (String key : stash.keySet()) {
			List<ItemStack> items = new ArrayList<>();
			ListTag list = stash.getListOrEmpty(key);
			for (int i = 0; i < list.size(); i++) {
				ItemStack.CODEC.parse(ops, list.get(i)).result().ifPresent(items::add);
			}
			STASH.put(UUID.fromString(key), items);
		}
		CompoundTag credits = root.getCompoundOrEmpty("credits");
		for (String key : credits.keySet()) {
			List<Credit> list = new ArrayList<>();
			ListTag l = credits.getListOrEmpty(key);
			for (int i = 0; i < l.size(); i++) {
				CompoundTag c = l.getCompoundOrEmpty(i);
				list.add(new Credit(c.getStringOr("hunt", "animal"), c.getIntOr("tier", 1), c.getStringOr("mutation", "")));
			}
			CREDITS.put(UUID.fromString(key), list);
		}
	}

	/** For tests: forget everything (packs, invites, stashes). */
	public static void resetAll() {
		PACKS.clear();
		PACK_OF.clear();
		STASH.clear();
		CREDITS.clear();
		INVITES.clear();
		UNSEEN.clear();
		dirty = true;
	}
}
