package com.huntlike.hunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.huntlike.HuntLike;
import com.huntlike.hunt.HuntData.Progress;
import com.huntlike.hunt.HuntData.Quest;
import com.huntlike.hunt.HuntDefs.Drop;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.HuntDefs.Tier;
import com.huntlike.network.HuntPayloads;
import com.huntlike.registry.ModItems;

/** Server side of Hunting Quests: starting, counting kills, completing and failing. */
public final class QuestManager {
	private QuestManager() {
	}

	public static final AttachmentType<HuntData> DATA = AttachmentRegistry.create(HuntLike.id("hunt_data"),
			builder -> builder.initializer(HuntData::new).persistent(HuntData.CODEC).copyOnDeath());

	/** The world day number: one per 24,000 ticks of the Overworld clock, starting at sunrise. */
	public static long worldDay(ServerPlayer player) {
		return player.level().getServer().overworld().getOverworldClockTime() / 24000L;
	}

	/** Daily Quests are open once any Hunt reaches Rank I (each quest still needs Rank I in its own Hunt). */
	public static boolean dailyUnlocked(HuntData data) {
		for (Hunt hunt : HuntDefs.HUNTS) {
			if (data.progress(hunt.id()).rank() >= HuntData.DAILY_UNLOCK_RANK) {
				return true;
			}
		}
		return false;
	}

	/** Refills the Daily Quest charges on a new world day. True if they were refilled. */
	public static boolean refreshDaily(ServerPlayer player, HuntData data) {
		long day = worldDay(player);
		if (data.dailyDay == day) {
			return false;
		}
		data.dailyDay = day;
		data.dailyCharges = HuntData.DAILY_CHARGES;
		return true;
	}

	/** Top-tier bosses needed for a Hunting Trophy (given once). */
	public static final int TROPHY_KILLS = 100;

	/** The Hunting Trophy: into the inventory, with a golden announcement to everyone on the server. */
	public static void giveTrophy(ServerPlayer player, Hunt hunt) {
		ItemStack trophy = new ItemStack(ModItems.trophy(hunt.id()));
		java.util.List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
		com.huntlike.registry.Lore.gray(lore, "A sign of " + player.getScoreboardName() + "'s mastery over "
				+ hunt.name().toLowerCase(java.util.Locale.ROOT) + " hunting.");
		trophy.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
		giveStack(player, trophy);
		player.level().getServer().getPlayerList().broadcastSystemMessage(Messages.mastery(player, hunt), false);
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			playSound(other, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F);
		}
	}

	/** Lured mobs slain for "I swear it was Self-Defense". */
	public static final int SELF_DEFENSE_KILLS = 1000;
	public static final int NOT_SELF_DEFENSE_KILLS = 10000;
	public static final TagKey<EntityType<?>> QUOTA_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, HuntLike.id("animal_quota"));

	public static final int BOSS_TIME_LIMIT = 20 * 60 * 5;
	public static final int SUMMON_TICKS = 40;
	public static final double FLEE_DISTANCE = 64.0;

	public static void initialize() {
		ServerPlayNetworking.registerGlobalReceiver(HuntPayloads.Action.TYPE,
				(payload, context) -> handleAction(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(QuestManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.huntlike.gear.KoroWhakamateItem::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.huntlike.gear.HideBoots::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.huntlike.gear.GearEffects::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(QuestManager::onDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BossController::allowDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(com.huntlike.gear.GearEffects::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BossController::afterDamage);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onChangeLevel(player));
		ServerEntityEvents.ENTITY_LOAD.register(QuestManager::onEntityLoad);
		HuntingPacks.initialize();
		Mutations.initialize();
	}

	public static HuntData data(ServerPlayer player) {
		return player.getAttachedOrCreate(DATA);
	}

	public static void save(ServerPlayer player, HuntData data) {
		player.setAttached(DATA, data);
	}

	public static boolean isPeaceful(ServerPlayer player) {
		return player.level().getDifficulty() == Difficulty.PEACEFUL;
	}

	// ---------------------------------------------------------------- menu

	/** Opens on the Hunting Pack screen during a pack quest. */
	public static final int PACK_TAB = 3;
	/** Added to the tab when the menu comes from the A-Phone (it has the Hunting Pack side tab). */
	public static final int PHONE_FLAG = 16;

	public static void openMenu(ServerPlayer player) {
		openMenu(player, false);
	}

	public static void openMenu(ServerPlayer player, boolean phone) {
		sync(player);
		int tab = HuntingPacks.redirect(player) ? PACK_TAB : 0;
		ServerPlayNetworking.send(player, new HuntPayloads.OpenMenu(tab + (phone ? PHONE_FLAG : 0)));
	}

	public static void openNexus(ServerPlayer player) {
		sync(player);
		ServerPlayNetworking.send(player, new HuntPayloads.OpenMenu(com.huntlike.nexus.SoulNexus.SCREEN_TAB));
	}

	private static void handleAction(ServerPlayer player, HuntPayloads.Action action) {
		if (HuntingPacks.handle(player, action.action(), action.hunt(), action.value())) {
			sync(player);
			return;
		}
		HuntData data = data(player);
		Hunt hunt = HuntDefs.hunt(action.hunt());
		switch (action.action()) {
			case "select" -> {
				data.selectedHunt = com.huntlike.interlude.InterludeDefs.SLIME_RIDER.equals(action.hunt()) ? action.hunt() : hunt.id();
				save(player, data);
			}
			case "start" -> startQuest(player, data, hunt, action.value());
			case "cancel" -> {
				if (data.quest != null && data.quest.isPack() && !HuntingPacks.isLeader(player)) {
					player.sendSystemMessage(Messages.error("Only the pack's leader can cancel the Hunt."));
				} else if (data.quest != null) {
					fail(player, data);
				} else if (HuntingPacks.packQuest(player) != null) {
					player.sendSystemMessage(Messages.error("Only the pack's leader can cancel the Hunt."));
				} else if (data.pausedQuest != null) {
					Quest paused = data.pausedQuest;
					data.pausedQuest = null;
					save(player, data);
					player.sendSystemMessage(Messages.failed(HuntDefs.hunt(paused.hunt), paused.tier));
					playSound(player, SoundEvents.BEACON_DEACTIVATE, 0.8F);
				}
			}
			case "buy_tokens" -> buyTokens(player, data, hunt, action.value() == 1);
			case "buy_gear" -> buyGear(player, data, hunt, action.value());
			case "koro_click" -> KoroManager.click(player, data);
			case "interlude_start" -> {
				if (HuntingPacks.grouped(player)) {
					player.sendSystemMessage(Messages.error("Interludes can't be started while you are in a Hunting Pack."));
				} else {
					com.huntlike.interlude.SlimeRiderFight.start(player, data, action.value());
				}
			}
			case "interlude_tokens" -> com.huntlike.interlude.SlimeRiderFight.buyTokens(player, data, action.value() == 1);
			case "towernaar_click" -> com.huntlike.towernaar.TowernaarManager.click(player, data);
			case "towernaar_start" -> com.huntlike.towernaar.TowernaarFight.start(player, data);
			case "towernaar_prd" -> com.huntlike.towernaar.TowernaarManager.buyPrd(player, data);
			case "yummy_key" -> {
				ClusterFight.onYummyKey(player, action.value());
				return;
			}
			case "nexus" -> {
				if (com.huntlike.nexus.SoulNexus.isCorner(action.hunt()) && action.value() != 0) {
					com.huntlike.nexus.SoulNexus.change(player, data, action.hunt(), action.value());
				}
			}
			case "wildfire_buff" -> {
				try {
					com.huntlike.path.WildfirePath.change(player, data, Integer.parseInt(action.hunt()), action.value());
				} catch (NumberFormatException e) {
					return;
				}
			}
			case "hint_seen" -> {
				data.hintSeen = true;
				save(player, data);
			}
			default -> {
			}
		}
		sync(player);
	}

	private static void buyTokens(ServerPlayer player, HuntData data, Hunt hunt, boolean bulk) {
		int material = HuntDefs.tokenMaterialCount(hunt) * (bulk ? 10 : 1);
		int tokens = bulk ? 640 : 64;
		Inventory inventory = player.getInventory();
		int have = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (HuntDefs.isTokenMaterial(hunt, stack)) {
				have += stack.getCount();
			}
		}
		if (have < material) {
			player.sendSystemMessage(Messages.error("You need " + material + " " + HuntDefs.tokenMaterialName(hunt) + "."));
			return;
		}
		int left = material;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (HuntDefs.isTokenMaterial(hunt, stack)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inventory.setChanged();
		data.progress(hunt.id()).tokens += tokens;
		save(player, data);
		playSound(player, HuntDefs.isCluster(hunt) ? SoundEvents.SOUL_SAND_PLACE : SoundEvents.WOOD_PLACE, 1.0F);
	}


	private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
		int have = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				have += stack.getCount();
			}
		}
		return have;
	}

	/** Buys a Gear trade. value = page * 100 + index (page 0 armor, 2 weapons). Upgrades keep enchantments. */
	private static void buyGear(ServerPlayer player, HuntData data, Hunt hunt, int value) {
		int page = value / 100;
		int index = value % 100;
		if (page < 0 || page > 2) {
			return;
		}
		List<HuntDefs.GearTrade> trades = HuntDefs.trades(hunt, page);
		if (index < 0 || index >= trades.size()) {
			return;
		}
		HuntDefs.GearTrade trade = trades.get(index);
		if (!HuntDefs.tradeOpen(data, hunt, trade)) {
			player.sendSystemMessage(Messages.error(HuntDefs.isCluster(hunt) ? "Not unlocked yet." : "Finish Koro's chapter first."));
			return;
		}
		if (data.progress(hunt.id()).rank() < trade.rank()) {
			player.sendSystemMessage(Messages.error("Your Rank is too low for this trade."));
			return;
		}
		Inventory inventory = player.getInventory();
		for (HuntDefs.Cost cost : trade.cost()) {
			int have = count(inventory, cost.item());
			if (have < cost.count()) {
				player.sendSystemMessage(Messages.error("Missing " + new ItemStack(cost.item()).getHoverName().getString() + " x"
						+ (cost.count() - have) + "."));
				return;
			}
		}
		int upgraded = trade.result().size();
		ItemStack[] sources = new ItemStack[upgraded];
		int[] sourceSlots = new int[upgraded];
		for (int c = 0; c < trade.cost().size(); c++) {
			HuntDefs.Cost cost = trade.cost().get(c);
			int left = cost.count();
			for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.is(cost.item())) {
					continue;
				}
				if (c < upgraded && sources[c] == null) {
					sources[c] = stack.copyWithCount(1);
					sourceSlots[c] = i;
				}
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		for (net.minecraft.world.item.Item item : trade.result()) {
			data.progress(hunt.id()).dropsSeen.add(HuntDefs.gearSeenId(item));
		}
		save(player, data);
		for (int i = 0; i < upgraded; i++) {
			ItemStack result = new ItemStack(trade.result().get(i));
			if (sources[i] != null) {
				var enchantments = sources[i].get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
				if (enchantments != null) {
					result.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, enchantments);
				}
				var name = sources[i].get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
				if (name != null) {
					result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
				}
			}
			if (sources[i] != null && inventory.getItem(sourceSlots[i]).isEmpty()) {
				inventory.setItem(sourceSlots[i], result);
			} else {
				giveStack(player, result);
			}
		}
		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		ServerPlayNetworking.send(player, new HuntPayloads.GearToast(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(trade.result().get(0)).toString(), trade.name()));
		playSound(player, SoundEvents.SMITHING_TABLE_USE, 1.0F);
	}

	// ---------------------------------------------------------------- quest lifecycle

	private static void startQuest(ServerPlayer player, HuntData data, Hunt hunt, int tierNumber) {
		if (HuntingPacks.grouped(player)) {
			if (HuntingPacks.isLeader(player)) {
				HuntingPacks.start(player, hunt, tierNumber);
			} else {
				player.sendSystemMessage(Messages.error("Only the pack's leader can start a Hunt."));
			}
			return;
		}
		if (data.quest != null) {
			player.sendSystemMessage(Messages.error("You already have an active quest."));
			return;
		}
		if (isPeaceful(player)) {
			player.sendSystemMessage(Messages.peacefulStart());
			return;
		}
		if (tierNumber < 1 || tierNumber > hunt.tiers().size()) {
			return;
		}
		if (!HuntDefs.huntUnlocked(data, hunt)) {
			player.sendSystemMessage(Messages.error("The " + hunt.name() + " Hunt unlocks at "
					+ HuntDefs.ANIMAL.rankName(HuntDefs.huntUnlockRank(hunt)) + "."));
			return;
		}
		Tier tier = hunt.tier(tierNumber);
		Progress progress = data.progress(hunt.id());
		if (progress.rank() < tier.unlockRank()) {
			player.sendSystemMessage(Messages.error("This tier unlocks at " + hunt.rankName(tier.unlockRank()) + "."));
			return;
		}
		if (tierNumber > 1 && progress.bossKills[tierNumber - 2] == 0) {
			player.sendSystemMessage(Messages.error("Defeat " + hunt.tier(tierNumber - 1).bossName() + " first."));
			return;
		}
		if (progress.tokens < tier.tokenCost()) {
			player.sendSystemMessage(Messages.error("Not enough " + hunt.name() + " Ritual Tokens."));
			return;
		}
		progress.tokens -= tier.tokenCost();
		Quest quest = new Quest();
		quest.hunt = hunt.id();
		quest.tier = tierNumber;
		quest.quota = tier.quota();
		refreshDaily(player, data);
		if (data.dailyCharges > 0 && progress.rank() >= HuntData.DAILY_UNLOCK_RANK) {
			data.dailyCharges--;
			quest.daily = true;
		}
		data.quest = quest;
		save(player, data);
		SpiritLure.reset(player);
		player.sendSystemMessage(Messages.started(hunt, tierNumber, quest.daily));
		playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F);
		playSound(player, SoundEvents.BEACON_ACTIVATE, 1.2F);
		award(player, "animal/conquest");
	}

	/** Admin start: no token cost, no Rank or previous-boss requirement. False if a quest is already running. */
	public static boolean forceStart(ServerPlayer player, Hunt hunt, int tierNumber) {
		HuntData data = data(player);
		if (data.quest != null || tierNumber < 1 || tierNumber > hunt.tiers().size()) {
			return false;
		}
		Quest quest = new Quest();
		quest.hunt = hunt.id();
		quest.tier = tierNumber;
		quest.quota = hunt.tier(tierNumber).quota();
		data.quest = quest;
		save(player, data);
		SpiritLure.reset(player);
		player.sendSystemMessage(Messages.started(hunt, tierNumber));
		playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F);
		playSound(player, SoundEvents.BEACON_ACTIVATE, 1.2F);
		sync(player);
		return true;
	}

	/** Admin boss: starts the quest with its quota already filled and summons the boss next to the player. */
	public static boolean forceBoss(ServerPlayer player, Hunt hunt, int tierNumber) {
		if (!forceStart(player, hunt, tierNumber)) {
			return false;
		}
		HuntData data = data(player);
		Quest quest = data.quest;
		quest.kills = quest.quota;
		beginSummon(player, data, quest, player.blockPosition());
		if (quest.phase != HuntData.PHASE_SUMMONING) {
			fail(player, data);
			return false;
		}
		save(player, data);
		sync(player);
		return true;
	}

	/** Fails or cancels the quest: no refund. */
	public static void fail(ServerPlayer player, HuntData data) {
		Quest quest = data.quest;
		if (quest == null) {
			return;
		}
		if (quest.isPack()) {
			HuntingPacks.fail(player, quest);
			sync(player);
			return;
		}
		BossController.discardBoss(player, quest);
		SpiritLure.cleanup(player);
		data.quest = null;
		save(player, data);
		player.sendSystemMessage(Messages.failed(HuntDefs.hunt(quest.hunt), quest.tier));
		playSound(player, SoundEvents.BEACON_DEACTIVATE, 0.8F);
		sync(player);
	}

	/** One kill short of the boss: used when the boss can't spawn or disappears without being killed. */
	public static void resetToQuotaMinusOne(ServerPlayer player, HuntData data) {
		Quest quest = data.quest;
		if (quest == null) {
			return;
		}
		BossController.discardBoss(player, quest);
		quest.phase = HuntData.PHASE_KILLING;
		quest.kills = Math.max(0, quest.quota - 1);
		quest.bossUuid = "";
		save(player, data);
		sync(player);
	}

	/**
	 * Vanilla experience for a boss kill: 218 points, so 5 bosses take a player from level 0 to about level 27 (1,089
	 * points). It drops as orbs at the player's feet (so Mending can use it).
	 */
	public static final int BOSS_EXPERIENCE = 218;
	/** Helpers each get 20% of it on top; the owner's share isn't reduced. */
	public static final int HELPER_EXPERIENCE = BOSS_EXPERIENCE / 5;

	public static void awardBossExperience(ServerPlayer owner, Collection<UUID> helpers) {
		ExperienceOrb.award(owner.level(), owner.position(), BOSS_EXPERIENCE);
		for (UUID helperId : helpers) {
			ServerPlayer helper = helperId.equals(owner.getUUID()) ? null
					: owner.level().getServer().getPlayerList().getPlayer(helperId);
			if (helper != null) {
				ExperienceOrb.award(helper.level(), helper.position(), HELPER_EXPERIENCE);
			}
		}
	}

	public static void complete(ServerPlayer owner, HuntData data, Collection<UUID> helpers) {
		Quest quest = data.quest;
		if (quest.isPack()) {
			HuntingPacks.complete(owner, quest, helpers);
			return;
		}
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		awardBossExperience(owner, helpers);
		rollDrops(owner, data, hunt, tier, quest.daily, (stack, rare) -> giveStack(owner, stack));
		rewardHelpers(owner, hunt, tier, helpers, java.util.Set.of());
		SpiritLure.cleanup(owner);
		data.quest = null;
		save(owner, data);
		credit(owner, data, hunt, tier, quest.daily, quest.mutation);
	}

	/** Helpers (players who hit the boss but don't own the quest) roll the chance drops with their own luck. */
	public static void rewardHelpers(ServerPlayer owner, Hunt hunt, Tier tier, Collection<UUID> helpers, java.util.Set<UUID> skip) {
		RandomSource random = owner.getRandom();
		for (UUID helperId : helpers) {
			if (helperId.equals(owner.getUUID()) || skip.contains(helperId)) {
				continue;
			}
			ServerPlayer helper = owner.level().getServer().getPlayerList().getPlayer(helperId);
			if (helper == null) {
				continue;
			}
			HuntData helperData = data(helper);
			for (Drop drop : HuntDefs.drops(tier)) {
				if (!drop.guaranteed() && random.nextInt(HuntDefs.odds(hunt.id(), drop.chanceNum(), drop.chanceDen(), HuntDefs.luck(helperData, hunt.id()))) < drop.chanceNum()) {
					give(helper, helperData, hunt, drop, drop.minCount());
					if (drop.dropClass() > 0) {
						helper.sendSystemMessage(Messages.rareDrop(drop.dropClass(), new ItemStack(drop.item()).getStyledHoverName()));
						playSound(helper, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F);
					}
				}
			}
			save(helper, helperData);
			sync(helper);
		}
	}

	/**
	 * Rolls one player's boss drops (guaranteed ones and the chance ones, with their luck) and hands each to the sink,
	 * recording what they've seen. The sink gets the stack and whether it was a rare drop.
	 */
	public static void rollDrops(ServerPlayer player, HuntData data, Hunt hunt, Tier tier, boolean daily,
			java.util.function.BiConsumer<ItemStack, Boolean> sink) {
		RandomSource random = player.getRandom();
		Progress progress = data.progress(hunt.id());
		for (Drop drop : HuntDefs.drops(tier)) {
			int count;
			if (drop.guaranteed()) {
				count = drop.minCount() + random.nextInt(drop.maxCount() - drop.minCount() + 1);
			} else if (random.nextDouble() < HuntDefs.chance(hunt.id(), drop.chanceNum(), drop.chanceDen(), HuntDefs.luck(data, hunt.id()), daily)) {
				count = drop.minCount();
			} else {
				continue;
			}
			progress.dropsSeen.add(drop.id());
			progress.dropCounts.merge(drop.id(), count, Integer::sum);
			boolean rare = !drop.guaranteed() && drop.dropClass() > 0;
			sink.accept(new ItemStack(drop.item(), count), rare);
			if (rare) {
				player.sendSystemMessage(Messages.rareDrop(drop.dropClass(), new ItemStack(drop.item()).getStyledHoverName()));
				playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F);
			}
		}
	}

	/** Boss kill credit: the kill, Rank XP, rank-up titles, the trophy and the advancement. */
	public static void credit(ServerPlayer owner, HuntData data, Hunt hunt, Tier tier, boolean daily, String mutation) {
		Progress progress = data.progress(hunt.id());
		if (!mutation.isEmpty()) {
			progress.mutationKills.merge(tier.number() + ":" + mutation, 1, Integer::sum);
		}
		int oldRank = progress.rank();
		progress.bossKills[tier.number() - 1]++;
		progress.xp += daily ? tier.xp() * 2 : tier.xp();
		int newRank = progress.rank();
		save(owner, data);

		owner.sendSystemMessage(Messages.complete(hunt, tier.number()));
		if (tier.number() == hunt.tiers().size() && progress.bossKills[tier.number() - 1] == TROPHY_KILLS) {
			giveTrophy(owner, hunt);
		}
		playSound(owner, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
		award(owner, hunt.id() + "/tier" + tier.number());
		for (int level = oldRank + 1; level <= newRank; level++) {
			owner.sendSystemMessage(Messages.rankUp(hunt, level));
			owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
			owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
					Messages.c("Rank " + HuntDefs.ROMAN[level], HuntDefs.GOLD)));
			owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
					Messages.c(hunt.rankName(level).toUpperCase(java.util.Locale.ROOT), hunt.color()).withStyle(style -> style.withBold(true))));
			playSound(owner, SoundEvents.PLAYER_LEVELUP, 1.0F);
			playSound(owner, SoundEvents.BELL_BLOCK, 1.0F);
			if (level == HuntDefs.MAX_RANK) {
				award(owner, hunt.id() + "/slayer");
			}
		}
		sync(owner);
	}

	/** Delivers a drop like /give: into the inventory, anything left over drops for that player only. */
	private static void give(ServerPlayer player, HuntData data, Hunt hunt, Drop drop, int count) {
		Progress progress = data.progress(hunt.id());
		progress.dropsSeen.add(drop.id());
		progress.dropCounts.merge(drop.id(), count, Integer::sum);
		giveStack(player, new ItemStack(drop.item(), count));
	}

	public static void giveStack(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
			item.setNoPickUpDelay();
			item.setTarget(player.getUUID());
			player.level().addFreshEntity(item);
		}
		player.inventoryMenu.broadcastChanges();
	}

	// ---------------------------------------------------------------- events

	private static void tick(MinecraftServer server) {
		boolean syncNow = server.getTickCount() % 20 == 0;
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			HuntData data = data(player);
			if (syncNow) {
				boolean firstDay = data.dailyDay < 0;
				if (refreshDaily(player, data)) {
					save(player, data);
					sync(player);
					if (!firstDay && dailyUnlocked(data)) {
						player.sendSystemMessage(Messages.c("Your Daily Quest charges have been refilled!", HuntDefs.PINK));
						playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
					}
				}
			}
			Quest quest = data.quest;
			if (quest == null) {
				continue;
			}
			switch (quest.phase) {
				case HuntData.PHASE_KILLING -> {
					// A Rogue Hunter pauses the Spirit Lure until it's dealt with.
					if (!isPeaceful(player) && !com.huntlike.rogue.RogueHunters.active(player)) {
						SpiritLure.tick(player, data, quest);
					}
				}
				case HuntData.PHASE_SUMMONING -> BossController.tickSummon(player, data, quest);
				case HuntData.PHASE_BOSS -> BossController.tickBoss(player, data, quest);
				default -> {
				}
			}
			if (syncNow && data.quest != null) {
				sync(player);
			}
		}
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player) {
			HuntData data = data(player);
			// In a Hunting Pack a death fails nothing: the member comes back and keeps hunting.
			if (data.quest != null && !data.quest.isPack() && data.quest.phase != HuntData.PHASE_KILLING) {
				fail(player, data);
			}
			return;
		}
		if (BossController.isBoss(entity)) {
			BossController.onBossDeath(entity);
			return;
		}
		if (ClusterQuota.isClusterMob(entity)) {
			ClusterQuota.onDeath(entity, source);
			return;
		}
		if (!entity.getType().builtInRegistryHolder().is(QUOTA_ANIMALS)) {
			return;
		}
		if (entity instanceof Mob mob && mob.isBaby()) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		HuntData data = data(killer);
		if (SpiritLure.isLuredBy(entity, killer)) {
			countLuredKill(killer, data);
		}
		if (SpiritLure.isLuredBy(entity, killer) && killer.getRandom().nextDouble() < HuntDefs.chance("animal", 1, 100,
				data.concoction, data.quest != null && data.quest.daily)) {
			HuntData.Progress progress = data.progress("animal");
			progress.dropsSeen.add("soul_stone");
			progress.dropCounts.merge("soul_stone", 1, Integer::sum);
			save(killer, data);
			giveStack(killer, new ItemStack(ModItems.SOUL_STONE));
		}
		Quest quest = HuntingPacks.questFor(killer, data);
		if (quest == null || quest.phase != HuntData.PHASE_KILLING
				|| !(quest.hunt.equals("animal") || com.huntlike.interlude.InterludeDefs.isInterlude(quest.hunt)) || isPeaceful(killer)
				|| com.huntlike.rogue.RogueHunters.active(killer)) {
			return;
		}
		if (quest.isPack()) {
			HuntingPacks.addPoints(killer, quest, com.huntlike.gear.GearEffects.quotaKills(killer));
			return;
		}
		quest.kills = Math.min(quest.quota, quest.kills + com.huntlike.gear.GearEffects.quotaKills(killer));
		SpiritLure.onQuotaKill(killer);
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		killer.sendSystemMessage(Messages.quota(hunt, quest.kills, quest.quota), true);
		killer.level().playSound(null, killer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F,
				0.6F + 0.9F * quest.kills / Math.max(1, quest.quota));
		if (quest.kills >= quest.quota) {
			beginSummon(killer, data, quest, entity.blockPosition());
		}
		save(killer, data);
		sync(killer);
	}

	/** One lured mob slain: counts toward "I Swear It Was Self-Defense". */
	public static void countLuredKill(ServerPlayer killer, HuntData data) {
		data.luredKills++;
		if (data.luredKills >= SELF_DEFENSE_KILLS) {
			award(killer, "animal/self_defense");
		}
		if (data.luredKills >= NOT_SELF_DEFENSE_KILLS) {
			award(killer, "animal/not_self_defense");
		}
		save(killer, data);
		com.huntlike.rogue.RogueHunters.onLuredKill(killer, data);
	}

	/** Adds quota points (one kill, or a whole cluster) and summons the boss once the quota is full. */
	static void addQuotaPoints(ServerPlayer killer, HuntData data, Quest quest, int points, BlockPos killPos) {
		if (quest.isPack()) {
			HuntingPacks.addPoints(killer, quest, points);
			return;
		}
		quest.kills = Math.min(quest.quota, quest.kills + points);
		SpiritLure.onQuotaKill(killer);
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		killer.sendSystemMessage(Messages.quota(hunt, quest.kills, quest.quota), true);
		killer.level().playSound(null, killer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F,
				0.6F + 0.9F * quest.kills / Math.max(1, quest.quota));
		if (quest.kills >= quest.quota) {
			beginSummon(killer, data, quest, killPos);
		}
		save(killer, data);
		sync(killer);
	}

	static void beginSummon(ServerPlayer player, HuntData data, Quest quest, BlockPos killPos) {
		BlockPos spot = findGround(player.level(), killPos);
		if (spot == null) {
			player.sendSystemMessage(Messages.badTerrain());
			quest.kills = quest.quota - 1;
			return;
		}
		quest.phase = HuntData.PHASE_SUMMONING;
		quest.summonX = spot.getX();
		quest.summonY = spot.getY();
		quest.summonZ = spot.getZ();
		quest.summonTicks = SUMMON_TICKS;
		playSound(player, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F);
	}

	/** A standing spot with solid ground within 5 blocks of the last quota kill, or null. */
	public static BlockPos findGround(ServerLevel level, BlockPos origin) {
		for (int radius = 0; radius <= 5; radius++) {
			for (int dy = 0; dy <= 5; dy++) {
				for (int sign : new int[] {1, -1}) {
					for (int dx = -radius; dx <= radius; dx++) {
						for (int dz = -radius; dz <= radius; dz++) {
							if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
								continue;
							}
							BlockPos pos = origin.offset(dx, dy * sign, dz);
							BlockState below = level.getBlockState(pos.below());
							if (below.isFaceSturdy(level, pos.below(), Direction.UP)
									&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
									&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
									&& level.getFluidState(pos).isEmpty()) {
								return pos;
							}
						}
					}
				}
			}
		}
		return null;
	}

	private static void onLogout(ServerPlayer player) {
		HuntingPacks.onLogout(player);
		com.huntlike.gear.HideBoots.forget(player);
		HuntData data = data(player);
		if (data.quest != null && data.quest.phase != HuntData.PHASE_KILLING) {
			resetToQuotaMinusOne(player, data);
		}
		SpiritLure.cleanup(player);
		SpiritLure.forget(player);
	}

	private static void onChangeLevel(ServerPlayer player) {
		HuntData data = data(player);
		if (data.quest != null && data.quest.isPack()) {
			HuntingPacks.onRunnerInterrupted(player, data);
		} else if (data.quest != null && data.quest.phase != HuntData.PHASE_KILLING) {
			fail(player, data);
		}
		sync(player);
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		ClusterQuota.onLoad(entity);
		if (entity instanceof LivingEntity living && BossController.isBoss(living) && ClusterFight.isBody(entity)) {
			UUID owner = BossController.ownerOf(entity);
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			Quest quest = player == null ? null : data(player).quest;
			if (quest == null || quest.phase != HuntData.PHASE_BOSS || !HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
				entity.discard();
			}
		} else if (entity instanceof LivingEntity living && BossController.isBoss(living)) {
			UUID owner = BossController.ownerOf(entity);
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			Quest quest = player == null ? null : data(player).quest;
			if (quest == null || !quest.bossUuid.equals(entity.getUUID().toString())) {
				entity.discard();
			}
		} else if (BossAbilities.isHerd(entity) && !BossAbilities.isTrackedHerd(entity)) {
			// Herd animals only live during their boss fight.
			entity.discard();
		} else if (SpiritLure.isLured(entity)) {
			UUID owner = SpiritLure.ownerOf(entity);
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player == null || data(player).quest == null) {
				entity.discard();
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	public static void playSound(ServerPlayer player, SoundEvent sound, float pitch) {
		player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, pitch);
	}

	public static void award(ServerPlayer player, String path) {
		var advancement = player.level().getServer().getAdvancements().get(HuntLike.id(path));
		if (advancement != null) {
			player.getAdvancements().award(advancement, "done");
		}
	}

	/** Sends the player's Hunt data and live quest values to their client. */
	public static void sync(ServerPlayer player) {
		HuntData data = data(player);
		CompoundTag tag = data.toTag();
		tag.putBoolean("peaceful", isPeaceful(player));
		tag.putInt("towernaarPhase", com.huntlike.towernaar.TowernaarFight.phase(player));
		tag.putIntArray("interludeEntities", com.huntlike.interlude.SlimeRiderFight.bodyIds(player));
		Quest quest = data.quest;
		if (quest != null && quest.phase == HuntData.PHASE_BOSS) {
			long elapsed = player.level().getGameTime() - quest.bossSpawnTime;
			tag.putInt("bossTicksLeft", (int) Math.max(0, BOSS_TIME_LIMIT - elapsed));
			Entity boss = BossController.findBoss(player, quest);
			tag.putInt("bossEntity", boss == null ? -1 : boss.getId());
			if (HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
				tag.putIntArray("bossEntities", ClusterFight.bodyIds(player));
			}
		} else {
			tag.putInt("bossEntity", -1);
		}
		ListTag huntable = new ListTag();
		List<String> names = new ArrayList<>();
		for (EntityType<?> type : SpiritLure.huntableHere(player,
				HuntDefs.hunt(quest != null ? quest.hunt : data.selectedHunt))) {
			String name = type.getDescription().getString();
			if (!names.contains(name)) {
				names.add(name);
				huntable.add(StringTag.valueOf(name));
			}
		}
		tag.put("huntable", huntable);
		HuntingPacks.writeSync(player, tag);
		ServerPlayNetworking.send(player, new HuntPayloads.Sync(tag));
	}
}
