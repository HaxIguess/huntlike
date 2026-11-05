package com.huntlike.command;

import java.util.Collection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.huntlike.hunt.HuntData;
import com.huntlike.hunt.HuntDefs;
import com.huntlike.hunt.HuntDefs.Drop;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.QuestManager;

/**
 * Operator commands:
 * /huntlike xp|tokens add &lt;amount&gt; [hunt], /huntlike reset,
 * /huntlike quest start &lt;players&gt; &lt;hunt&gt; &lt;tier&gt;, /huntlike quest cancel &lt;players&gt;,
 * /huntlike stats [player], /huntlike boss &lt;hunt&gt; &lt;tier&gt; [players],
 * /huntlike questline &lt;hunt&gt; &lt;chapter&gt; [players] (animal = Koro, cluster = Towernaar; one past the last = all done).
 */
public final class HuntCommand {
	private HuntCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
				Commands.literal("huntlike")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("xp").then(Commands.literal("add")
								.then(Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes(ctx -> add(ctx, true, HuntDefs.ANIMAL))
										.then(huntArgument().executes(ctx -> add(ctx, true, hunt(ctx)))))))
						.then(Commands.literal("tokens").then(Commands.literal("add")
								.then(Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes(ctx -> add(ctx, false, HuntDefs.ANIMAL))
										.then(huntArgument().executes(ctx -> add(ctx, false, hunt(ctx)))))))
						.then(Commands.literal("reset").executes(HuntCommand::reset))
						.then(Commands.literal("quest")
								.then(Commands.literal("start")
										.then(Commands.argument("players", EntityArgument.players())
												.then(huntArgument()
														.then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
																.executes(ctx -> start(ctx, false))))))
								.then(Commands.literal("cancel")
										.then(Commands.argument("players", EntityArgument.players())
												.executes(HuntCommand::cancel))))
						.then(Commands.literal("stats")
								.executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> stats(ctx, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("questline")
								.then(huntArgument()
										.then(Commands.argument("chapter", IntegerArgumentType.integer(1, 9))
												.executes(ctx -> questline(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
												.then(Commands.argument("players", EntityArgument.players())
														.executes(ctx -> questline(ctx, EntityArgument.getPlayers(ctx, "players")))))))
						.then(Commands.literal("boss")
								.then(huntArgument()
										.then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
												.executes(ctx -> bossSelf(ctx))
												.then(Commands.argument("players", EntityArgument.players())
														.executes(ctx -> start(ctx, true)))
												// /huntlike boss <hunt> <tier> mutation <Mineral|Bejeweled|none> [players]
												.then(Commands.literal("mutation")
														.then(Commands.argument("mutation", StringArgumentType.word())
																.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(mutationNames(), builder))
																.executes(ctx -> mutatedBoss(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
																.then(Commands.argument("players", EntityArgument.players())
																		.executes(ctx -> mutatedBoss(ctx, EntityArgument.getPlayers(ctx, "players")))))))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> huntArgument() {
		return Commands.argument("hunt", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HuntDefs.HUNTS.stream().map(Hunt::id), builder));
	}

	private static Hunt hunt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "hunt");
		for (Hunt hunt : HuntDefs.HUNTS) {
			if (hunt.id().equals(id)) {
				return hunt;
			}
		}
		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown Hunt: " + id)).create();
	}

	private static int start(CommandContext<CommandSourceStack> ctx, boolean boss) throws CommandSyntaxException {
		return start(ctx, EntityArgument.getPlayers(ctx, "players"), boss);
	}

	private static java.util.List<String> mutationNames() {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (com.huntlike.hunt.Mutations.Mutation mutation : com.huntlike.hunt.Mutations.Mutation.values()) {
			names.add(mutation.title);
		}
		names.add("none");
		return names;
	}

	/** Summons the boss with the given mutation (or "none", for none at all). Animal bosses only for now. */
	private static int mutatedBoss(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
		Hunt hunt = hunt(ctx);
		String name = StringArgumentType.getString(ctx, "mutation");
		String mutation = null;
		for (String known : mutationNames()) {
			if (known.equalsIgnoreCase(name)) {
				mutation = known;
			}
		}
		if (mutation == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown mutation: " + name + " (" + String.join(", ", mutationNames()) + ")"));
			return 0;
		}
		if (HuntDefs.isCluster(hunt) && !mutation.equals("none")) {
			ctx.getSource().sendFailure(Component.literal("Cluster bosses can't be mutated yet."));
			return 0;
		}
		int count = start(ctx, players, true);
		for (ServerPlayer player : players) {
			HuntData.Quest quest = QuestManager.data(player).quest;
			if (quest != null && quest.phase == HuntData.PHASE_SUMMONING) {
				quest.forcedMutation = mutation;
			}
		}
		return count;
	}

	private static int bossSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return start(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), true);
	}

	private static int start(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, boolean boss)
			throws CommandSyntaxException {
		Hunt hunt = hunt(ctx);
		int tier = IntegerArgumentType.getInteger(ctx, "tier");
		int done = 0;
		for (ServerPlayer player : players) {
			boolean ok = boss ? QuestManager.forceBoss(player, hunt, tier) : QuestManager.forceStart(player, hunt, tier);
			if (ok) {
				done++;
			} else {
				ctx.getSource().sendFailure(Component.literal(player.getScoreboardName()
						+ (QuestManager.data(player).quest != null ? " already has an active quest." : ": no room to summon the boss here.")));
			}
		}
		int count = done;
		String what = (boss ? "Summoned " : "Started ") + hunt.name() + " [Tier " + HuntDefs.ROMAN[tier] + "]"
				+ (boss ? (count == 1 ? " boss" : " bosses") : (count == 1 ? " quest" : " quests"));
		if (count > 0) {
			ctx.getSource().sendSuccess(() -> Component.literal(what + " for " + count + " player" + (count == 1 ? "." : "s.")), true);
		}
		return count;
	}

	/**
	 * Jumps a Hunt's questline to the start of a chapter (1 = the first): the chapters before it count as finished (no rewards
	 * are given), and the chapter itself is waiting for its first click. One past the last chapter = the questline is done.
	 * The chapter's Rank requirement still applies.
	 */
	private static int questline(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
		Hunt hunt = hunt(ctx);
		int chapter = IntegerArgumentType.getInteger(ctx, "chapter");
		boolean cluster = HuntDefs.isCluster(hunt);
		int chapters = cluster ? com.huntlike.towernaar.TowernaarScript.CHAPTERS.size() : com.huntlike.hunt.KoroScript.CHAPTERS.size();
		if (chapter > chapters + 1) {
			ctx.getSource().sendFailure(Component.literal(hunt.name() + " has " + chapters + " chapters (use " + (chapters + 1)
					+ " to mark them all done)."));
			return 0;
		}
		int done = chapter - 1;
		for (ServerPlayer player : players) {
			HuntData data = QuestManager.data(player);
			if (cluster) {
				com.huntlike.towernaar.TowernaarFight.forget(player);
				data.towernaarDone = done;
				data.towernaarStep = 0;
				data.towernaarKillBase = data.rogueKills;
			} else {
				data.koroDone = done;
				data.koroStep = 0;
			}
			player.setAttached(QuestManager.DATA, data);
			if (cluster) {
				com.huntlike.gear.ModShields.stampSigil(player, data);
			}
			QuestManager.sync(player);
		}
		String who = cluster ? "Towernaar" : "Koro";
		String what = chapter > chapters ? who + "'s questline set to finished" : who + "'s questline set to Chapter " + chapter;
		int rank = chapter > chapters ? 0 : cluster ? com.huntlike.towernaar.TowernaarScript.CHAPTERS.get(done).rank()
				: com.huntlike.hunt.KoroScript.CHAPTERS.get(done).rank();
		int count = players.size();
		ctx.getSource().sendSuccess(() -> Component.literal(what + " for " + count + " player" + (count == 1 ? "" : "s")
				+ (rank > 0 ? " (needs " + hunt.name() + " Rank " + HuntDefs.ROMAN[rank] + ")." : ".")), true);
		return count;
	}

	private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		int done = 0;
		for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
			HuntData data = QuestManager.data(player);
			if (data.quest != null) {
				QuestManager.fail(player, data);
				done++;
			}
		}
		int count = done;
		if (count == 0) {
			ctx.getSource().sendFailure(Component.literal("No active quests to cancel."));
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal("Cancelled " + count + " quest" + (count == 1 ? "." : "s.")), true);
		}
		return count;
	}

	private static int stats(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		HuntData data = QuestManager.data(player);
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("HuntLike stats - " + player.getScoreboardName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (Hunt hunt : HuntDefs.HUNTS) {
			HuntData.Progress progress = data.progress(hunt.id());
			int rank = progress.rank();
			source.sendSuccess(() -> line(hunt.name(), hunt.rankName(rank) + " (Rank " + rank + ") - " + progress.xp + " XP, "
					+ progress.tokens + " tokens", hunt.color()), false);
			StringBuilder kills = new StringBuilder();
			for (int i = 0; i < hunt.tiers().size(); i++) {
				kills.append(i > 0 ? "  " : "").append(HuntDefs.ROMAN[i + 1]).append(": ").append(progress.bossKills[i]);
			}
			source.sendSuccess(() -> line("Boss kills", kills.toString(), 0xAAAAAA), false);
			java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
			for (Drop drop : HuntDefs.drops(hunt.tier(hunt.tiers().size()))) {
				names.put(drop.id(), new net.minecraft.world.item.ItemStack(drop.item()).getHoverName().getString());
			}
			names.put("soul_stone", "Soul Stone");
			StringBuilder drops = new StringBuilder();
			names.forEach((id, name) -> {
				if (progress.dropsSeen.contains(id)) {
					drops.append(drops.length() > 0 ? ", " : "").append(name).append(" x").append(progress.dropCounts.getOrDefault(id, 0));
				}
			});
			String dropText = drops.length() > 0 ? drops.toString() : "none yet";
			source.sendSuccess(() -> line("Drops", dropText, 0xAAAAAA), false);
		}
		source.sendSuccess(() -> line("Lured mobs slain", String.valueOf(data.luredKills), 0xAAAAAA), false);
		source.sendSuccess(() -> line("Mystery Concoction", data.concoction ? "eaten" : "not eaten", 0xAAAAAA), false);
		source.sendSuccess(() -> line("Warm and Slimy Beef Jerky", data.jerky ? "eaten" : "not eaten", 0xAAAAAA), false);
		source.sendSuccess(() -> line("Koro chapters", data.koroDone + " / 4", 0xAAAAAA), false);
		source.sendSuccess(() -> line("Towernaar chapters", data.towernaarDone + " / 4", 0xAAAAAA), false);
		source.sendSuccess(() -> line("Rogue Hunters slain", data.rogueKills + " (special: " + data.specialRogueKills + ")", 0xAAAAAA),
				false);
		HuntData.Quest quest = data.quest;
		String questText = quest == null ? "none"
				: HuntDefs.hunt(quest.hunt).name() + " [Tier " + HuntDefs.ROMAN[quest.tier] + "] - "
						+ switch (quest.phase) {
							case HuntData.PHASE_KILLING -> quest.kills + "/" + quest.quota + " kills";
							case HuntData.PHASE_SUMMONING -> "summoning";
							default -> "boss alive";
						};
		source.sendSuccess(() -> line("Active quest", questText, 0xAAAAAA), false);
		return 1;
	}

	private static MutableComponent line(String label, String value, int color) {
		return Component.literal(label + ": ").withStyle(style -> style.withColor(color))
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static int add(CommandContext<CommandSourceStack> ctx, boolean xp, Hunt hunt) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		HuntData data = QuestManager.data(player);
		HuntData.Progress progress = data.progress(hunt.id());
		if (xp) {
			progress.xp += amount;
		} else {
			progress.tokens += amount;
		}
		player.setAttached(QuestManager.DATA, data);
		QuestManager.sync(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Added " + amount + " " + hunt.name()
				+ (xp ? " XP." : " Ritual Tokens.")), false);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		HuntData data = QuestManager.data(player);
		if (data.quest != null) {
			QuestManager.fail(player, data);
		}
		player.setAttached(QuestManager.DATA, new HuntData());
		QuestManager.sync(player);
		ctx.getSource().sendSuccess(() -> Component.literal("HuntLike progress reset."), false);
		return 1;
	}
}
