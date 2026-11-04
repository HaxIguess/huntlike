package com.huntlike.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import com.huntlike.hunt.HuntData;

/** The latest Hunt data the server sent this client. */
public final class ClientHuntState {
	private ClientHuntState() {
	}

	public static HuntData data = new HuntData();
	public static boolean peaceful;
	/** Towernaar's Final Chapter: 0 none, 1 waiting for an enderman kill, 2 fighting. */
	public static int towernaarPhase;
	/** The Interlude's living bodies (they glow for the owner). */
	public static int[] interludeIds = new int[0];
	public static int bossTicksLeft;
	public static int bossEntityId = -1;
	/** Every living body of a Cluster boss (empty for Animal bosses). */
	public static int[] bossEntityIds = new int[0];
	/** Spiritual Catalyst highlight: entity id and the client tick it ends at. */
	/** Entities highlighted by the Spiritual Catalyst or Compass: id -> game time the glow ends. */
	public static final java.util.Map<Integer, Long> highlights = new java.util.concurrent.ConcurrentHashMap<>();
	/** The outline color of spiritual animals: light blue. */
	public static final int SPIRIT_COLOR = 0x55FFFF;
	public static List<String> huntable = new ArrayList<>();
	/** The player's Hunting Pack (see HuntingPacks.writeSync), or an empty tag. */
	public static CompoundTag pack = new CompoundTag();
	/** Pending pack invites: pack, from, name, seconds, blocked (why Accept is greyed, if it is). */
	public static List<CompoundTag> invites = new ArrayList<>();
	public static boolean invitesNew;
	/** Items waiting in the Stash (rewards from while the player was away). */
	public static List<net.minecraft.world.item.ItemStack> stash = new ArrayList<>();

	public static void update(CompoundTag tag) {
		data = HuntData.fromTag(tag);
		peaceful = tag.getBooleanOr("peaceful", false);
		towernaarPhase = tag.getIntOr("towernaarPhase", 0);
		interludeIds = tag.getIntArray("interludeEntities").orElse(new int[0]);
		bossTicksLeft = tag.getIntOr("bossTicksLeft", 0);
		bossEntityId = tag.getIntOr("bossEntity", -1);
		bossEntityIds = tag.getIntArray("bossEntities").orElse(new int[0]);
		List<String> names = new ArrayList<>();
		ListTag list = tag.getListOrEmpty("huntable");
		for (int i = 0; i < list.size(); i++) {
			names.add(list.getStringOr(i, ""));
		}
		huntable = names;
		pack = tag.getCompoundOrEmpty("pack");
		List<CompoundTag> inviteList = new ArrayList<>();
		ListTag invitesTag = tag.getListOrEmpty("invites");
		for (int i = 0; i < invitesTag.size(); i++) {
			inviteList.add(invitesTag.getCompoundOrEmpty(i));
		}
		invites = inviteList;
		invitesNew = tag.getBooleanOr("invitesNew", false);
		List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
			ListTag stashTag = tag.getListOrEmpty("stash");
			for (int i = 0; i < stashTag.size(); i++) {
				net.minecraft.world.item.ItemStack.CODEC.parse(ops, stashTag.get(i)).result().ifPresent(items::add);
			}
		}
		stash = items;
	}

	/** In a pack with at least one other player. */
	public static boolean grouped() {
		return pack.getListOrEmpty("members").size() >= 2;
	}

	public static boolean packLeader() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		return player != null && player.getUUID().toString().equals(pack.getStringOr("leader", player.getUUID().toString()));
	}

	/** The pack's running quest, or null. */
	public static HuntData.Quest packQuest() {
		return grouped() && pack.contains("quest") ? HuntData.questFrom(pack.getCompoundOrEmpty("quest")) : null;
	}
}
