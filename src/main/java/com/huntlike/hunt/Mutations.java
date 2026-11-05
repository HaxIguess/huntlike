package com.huntlike.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import com.huntlike.hunt.HuntData.Quest;

/**
 * Boss mutations: a summoned boss has a 5% chance to be mutated, the mutation picked evenly from those available.
 * Mineral and Bejeweled work the same way: at full health, and as the boss falls to 75%, 50%, 25% and would die, it enters
 * a hit phase. A phase is cleared by one pickaxe hit (amethyst sound, a drop) or five other hits (anvil sound, no drop);
 * the boss keeps fighting but doesn't regenerate while a phase is open, and can't die until every phase is cleared.
 * Phases it skips past in one blow stack up. Each phase it enters sprays its minerals.
 *
 * <p>Mineral drops one of the pickaxe's minerals (all equally likely): wooden or golden: stone, coal; stone or copper:
 * + raw copper, raw iron; iron: + raw gold, diamond; diamond or netherite: + ancient debris. Fortune adds emeralds and a
 * (level / 2)% chance to double the drop. Netherite has a 1% chance to upgrade it (to the block, debris to a netherite
 * ingot, stone to another mineral, upgraded). Bejeweled drops amethyst, a diamond or an emerald whatever the pickaxe;
 * netherite's 1% turns amethyst into budding amethyst; Fortune doubles with a level% chance.
 *
 * <p>Animal bosses for now (Cluster fights split their health between bodies: to review).
 */
public final class Mutations {
	private Mutations() {
	}

	/** 5%; tests set it to 0 so their bosses die the usual way. */
	public static float chance = 0.05F;
	public static final int NORMAL_HITS = 5;
	private static final float[] THRESHOLDS = {0.75F, 0.5F, 0.25F};

	public enum Mutation {
		MINERAL("Mineral", new int[] {0xAAAAAA, 0xFF5555, 0xAAAAAA, 0x55FF55, 0xAAAAAA, 0x55FFFF, 0xAAAAAA}, 0.5F),
		BEJEWELED("Bejeweled", new int[] {0xAA00AA, 0xAA00AA, 0xAA00AA, 0xFF55FF, 0xFF55FF, 0xFF55FF, 0xAA00AA, 0xAA00AA, 0xAA00AA}, 1.0F);

		public final String title;
		/** One color per letter. */
		final int[] colors;
		/** Fortune's chance to double a drop, in percent per level. */
		final float fortunePercent;

		Mutation(String title, int[] colors, float fortunePercent) {
			this.title = title;
			this.colors = colors;
			this.fortunePercent = fortunePercent;
		}

		public static Mutation byTitle(String title) {
			for (Mutation mutation : values()) {
				if (mutation.title.equals(title)) {
					return mutation;
				}
			}
			return null;
		}

		/** The name with each letter in its color (Mineral: grey red grey green grey blue grey). */
		public MutableComponent colored() {
			MutableComponent out = Component.empty();
			for (int i = 0; i < title.length(); i++) {
				out.append(Messages.c(String.valueOf(title.charAt(i)), colors[Math.min(i, colors.length - 1)]));
			}
			return out;
		}
	}

	/** A mutated boss's open phases. */
	private static final class State {
		final Mutation mutation;
		int pending;
		int normalHits;
		/** Thresholds (75, 50, 25%) already entered, and the dying one. */
		final boolean[] entered = new boolean[THRESHOLDS.length];
		boolean dying;

		State(Mutation mutation) {
			this.mutation = mutation;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(Mutations::allowDeath);
	}

	/** For tests: the next boss gets this mutation. */
	public static String forced;

	/** Rolled at summoning; "" for none. */
	public static String roll(RandomSource random) {
		if (forced != null) {
			String mutation = forced;
			forced = null;
			return mutation;
		}
		if (random.nextFloat() >= chance) {
			return "";
		}
		Mutation[] all = Mutation.values();
		return all[random.nextInt(all.length)].title;
	}

	/** The new boss's state: the full-health phase is open from the start. */
	public static void start(Mob boss, Quest quest) {
		Mutation mutation = Mutation.byTitle(quest.mutation);
		if (mutation == null) {
			return;
		}
		State state = new State(mutation);
		state.pending = 1;
		STATES.put(boss.getUUID(), state);
		spray(boss, mutation);
	}

	private static State state(LivingEntity boss) {
		State state = STATES.get(boss.getUUID());
		if (state == null && boss instanceof Mob mob && mob.level() instanceof ServerLevel level) {
			// After a restart: rebuilt from the quest, phases already passed counted as entered.
			UUID owner = BossController.ownerOf(boss);
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			Quest quest = player == null ? null : QuestManager.data(player).quest;
			Mutation mutation = quest == null ? null : Mutation.byTitle(quest.mutation);
			if (mutation != null) {
				state = new State(mutation);
				float fraction = boss.getHealth() / boss.getMaxHealth();
				for (int i = 0; i < THRESHOLDS.length; i++) {
					state.entered[i] = fraction <= THRESHOLDS[i];
				}
				STATES.put(boss.getUUID(), state);
			}
		}
		return state;
	}

	/** No regeneration while a phase is open. */
	public static boolean blocksRegen(LivingEntity boss) {
		State state = STATES.get(boss.getUUID());
		return state != null && state.pending > 0;
	}

	/** A hit on the boss (before its damage): counts toward the open phase. */
	public static void onHit(LivingEntity boss, DamageSource source) {
		State state = state(boss);
		if (state == null || state.pending <= 0 || !(source.getEntity() instanceof ServerPlayer player)
				|| !(boss.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack tool = player.getMainHandItem();
		boolean pickaxe = source.getDirectEntity() == player && tool.is(ItemTags.PICKAXES);
		if (pickaxe) {
			level.playSound(null, boss.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.HOSTILE, 1.5F, 1.0F);
			drop(boss, player, tool, state.mutation, level);
			state.pending--;
			state.normalHits = 0;
		} else {
			level.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 1.2F);
			if (++state.normalHits >= NORMAL_HITS) {
				state.pending--;
				state.normalHits = 0;
			}
		}
	}

	/** After damage: phases for the thresholds it fell past. */
	public static void afterDamage(LivingEntity boss) {
		State state = STATES.get(boss.getUUID());
		if (state == null) {
			return;
		}
		enterThresholds(boss, state);
	}

	private static void enterThresholds(LivingEntity boss, State state) {
		float fraction = boss.getHealth() / boss.getMaxHealth();
		for (int i = 0; i < THRESHOLDS.length; i++) {
			if (!state.entered[i] && fraction <= THRESHOLDS[i]) {
				state.entered[i] = true;
				state.pending++;
				spray(boss, state.mutation);
			}
		}
	}

	/** A mutated boss can't die with phases open: it stays at 1 health, the dying phase is added (and any it skipped). */
	private static boolean allowDeath(LivingEntity boss, DamageSource source, float amount) {
		State state = STATES.get(boss.getUUID());
		if (state == null) {
			return true;
		}
		if (!state.dying) {
			boss.setHealth(1.0F);
			enterThresholds(boss, state);
			for (int i = 0; i < THRESHOLDS.length; i++) {
				if (!state.entered[i]) {
					state.entered[i] = true;
					state.pending++;
				}
			}
			state.dying = true;
			state.pending++;
			spray(boss, state.mutation);
			return false;
		}
		if (state.pending > 0) {
			boss.setHealth(1.0F);
			return false;
		}
		STATES.remove(boss.getUUID());
		return true;
	}

	public static void forget(UUID boss) {
		STATES.remove(boss);
	}

	// ---------------------------------------------------------------- drops

	private static final Item[] BEJEWELED = {Items.AMETHYST_SHARD, Items.DIAMOND, Items.EMERALD};

	private static int pickaxeTier(ItemStack tool) {
		if (tool.is(Items.NETHERITE_PICKAXE) || tool.is(Items.DIAMOND_PICKAXE)) {
			return 3;
		}
		if (tool.is(Items.IRON_PICKAXE)) {
			return 2;
		}
		if (tool.is(Items.STONE_PICKAXE) || tool.is(Items.COPPER_PICKAXE)) {
			return 1;
		}
		return 0;
	}

	private static List<Item> minerals(int tier, boolean fortune) {
		List<Item> list = new ArrayList<>(List.of(Items.STONE, Items.COAL));
		if (tier >= 1) {
			list.add(Items.RAW_COPPER);
			list.add(Items.RAW_IRON);
		}
		if (tier >= 2) {
			list.add(Items.RAW_GOLD);
			list.add(Items.DIAMOND);
		}
		if (tier >= 3) {
			list.add(Items.ANCIENT_DEBRIS);
		}
		if (fortune) {
			list.add(Items.EMERALD);
		}
		return list;
	}

	private static Item upgrade(Item item) {
		if (item == Items.COAL) {
			return Items.COAL_BLOCK;
		}
		if (item == Items.RAW_COPPER) {
			return Items.RAW_COPPER_BLOCK;
		}
		if (item == Items.RAW_IRON) {
			return Items.RAW_IRON_BLOCK;
		}
		if (item == Items.RAW_GOLD) {
			return Items.RAW_GOLD_BLOCK;
		}
		if (item == Items.DIAMOND) {
			return Items.DIAMOND_BLOCK;
		}
		if (item == Items.EMERALD) {
			return Items.EMERALD_BLOCK;
		}
		if (item == Items.ANCIENT_DEBRIS) {
			return Items.NETHERITE_INGOT;
		}
		return item;
	}

	private static void drop(LivingEntity boss, ServerPlayer player, ItemStack tool, Mutation mutation, ServerLevel level) {
		RandomSource random = boss.getRandom();
		var fortuneHolder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
		int fortune = EnchantmentHelper.getItemEnchantmentLevel(fortuneHolder, tool);
		boolean netherite = tool.is(Items.NETHERITE_PICKAXE);
		boolean upgraded = netherite && random.nextFloat() < 0.01F;
		Item item;
		if (mutation == Mutation.BEJEWELED) {
			item = BEJEWELED[random.nextInt(BEJEWELED.length)];
			if (upgraded && item == Items.AMETHYST_SHARD) {
				item = Items.BUDDING_AMETHYST;
			}
		} else {
			List<Item> list = minerals(pickaxeTier(tool), fortune > 0);
			item = list.get(random.nextInt(list.size()));
			if (upgraded) {
				if (item == Items.STONE) {
					List<Item> others = new ArrayList<>(list);
					others.remove(Items.STONE);
					item = others.get(random.nextInt(others.size()));
				}
				item = upgrade(item);
			}
		}
		int count = random.nextFloat() * 100.0F < fortune * mutation.fortunePercent ? 2 : 1;
		ItemEntity entity = new ItemEntity(level, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), new ItemStack(item, count));
		entity.setDeltaMovement((random.nextDouble() - 0.5) * 0.3, 0.3, (random.nextDouble() - 0.5) * 0.3);
		entity.setPickUpDelay(10);
		level.addFreshEntity(entity);
		if (upgraded) {
			level.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
		}
	}

	/** Bits of its minerals fly off the boss as it enters a phase (until the user's particles exist). */
	private static void spray(LivingEntity boss, Mutation mutation) {
		if (!(boss.level() instanceof ServerLevel level)) {
			return;
		}
		Item[] items = mutation == Mutation.BEJEWELED ? BEJEWELED
				: new Item[] {Items.STONE, Items.COAL, Items.RAW_COPPER, Items.RAW_IRON, Items.RAW_GOLD, Items.DIAMOND,
						Items.EMERALD, Items.ANCIENT_DEBRIS};
		double w = boss.getBbWidth() / 2.0;
		double h = boss.getBbHeight();
		for (Item item : items) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), boss.getX(), boss.getY() + h * 0.6, boss.getZ(), 6, w,
					h / 3.0, w, 0.25);
		}
		level.playSound(null, boss.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
	}

	// ---------------------------------------------------------------- names

	/** "Boss | [Mutation]" for the boss bar. */
	public static MutableComponent barName(HuntDefs.Tier tier, String mutation) {
		MutableComponent name = Messages.c(tier.bossName(), tier.color());
		Mutation m = Mutation.byTitle(mutation);
		if (m != null) {
			name.append(Messages.c(" | [", 0xAAAAAA)).append(m.colored()).append(Messages.c("]", 0xAAAAAA));
		}
		return name;
	}

	/** "MUTATION! Mineral Sylviornis" in chat. */
	public static Component announcement(HuntDefs.Hunt hunt, HuntDefs.Tier tier, Mutation mutation) {
		return Messages.c("MUTATION! ", HuntDefs.RED).append(mutation.colored()).append(Messages.c(" ", 0xFFFFFF))
				.append(Messages.c(tier.bossName(), hunt.color()));
	}
}
