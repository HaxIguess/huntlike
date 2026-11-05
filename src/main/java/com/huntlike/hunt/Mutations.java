package com.huntlike.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.world.entity.Display;
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
		MINERAL("Mineral", new int[] {0x555555, 0xFF5555, 0x555555, 0x55FF55, 0x555555, 0x55FFFF, 0x555555}, 0.5F),
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
		long lastHit = Long.MIN_VALUE / 2;
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
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Mutations::tickShards);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(SHARD_TAG) && FLYING.stream().noneMatch(shard -> shard.display() == entity)) {
				entity.discard();
			}
		});
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

	/** Hits count at most once per half second (fire and the like hurt every tick; a hurt boss ignores hits that long). */
	private static final int HIT_TICKS = 10;

	/**
	 * A hit on the boss, before its damage. While a phase is open the boss loses no health: the hit only counts toward the
	 * phase (a pickaxe swing clears it with a drop; any other damage, poison, wither and fire included, counts as one of
	 * five). The hit that clears the dying phase kills it. Returns whether the damage goes through.
	 */
	public static boolean onHit(LivingEntity boss, DamageSource source) {
		State state = state(boss);
		if (state == null || state.pending <= 0) {
			return true;
		}
		if (!(boss.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (now - state.lastHit < HIT_TICKS) {
			return false;
		}
		state.lastHit = now;
		ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
		ItemStack tool = player == null ? ItemStack.EMPTY : player.getMainHandItem();
		boolean pickaxe = player != null && source.getDirectEntity() == player && tool.is(ItemTags.PICKAXES);
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
		if (state.pending == 0 && state.dying) {
			// The last phase is done: this hit finishes it.
			boss.setHealth(1.0F);
			return true;
		}
		return false;
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

	/** The user's 3D shards (Blockbench models), drawn by item displays; only used for this. */
	public static final Item MINERAL_SHARD = com.huntlike.registry.ModItems.register("mutation_mineral_particle", Item::new, new Item.Properties());
	public static final Item BEJEWELED_SHARD = com.huntlike.registry.ModItems.register("mutation_bejeweled_particle", Item::new,
			new Item.Properties());
	private static final int SHARDS = 10;
	/** Shards left over from a save (the server stopped mid-flight) are removed when they load. */
	private static final String SHARD_TAG = "huntlike_mutation_shard";
	private static final int SHARD_LIFE = 24;

	/** A flying shard: its display, direction and the tick it was thrown. */
	private record Shard(Display.ItemDisplay display, org.joml.Vector3f direction, float spin, long born) {
	}

	private static final List<Shard> FLYING = new ArrayList<>();

	/** Shards of the user's model burst out of the boss as it enters a phase, arc out and fall, then vanish. */
	/** For tests and showcases: a burst of this mutation's shards. */
	public static void burst(LivingEntity boss, String mutation) {
		Mutation m = Mutation.byTitle(mutation);
		if (m != null) {
			spray(boss, m);
		}
	}

	private static void spray(LivingEntity boss, Mutation mutation) {
		if (!(boss.level() instanceof ServerLevel level)) {
			return;
		}
		RandomSource random = boss.getRandom();
		Item shard = mutation == Mutation.BEJEWELED ? BEJEWELED_SHARD : MINERAL_SHARD;
		double y = boss.getY() + boss.getBbHeight() * 0.6;
		for (int i = 0; i < SHARDS; i++) {
			Display.ItemDisplay display = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level,
					net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (display == null) {
				return;
			}
			display.addTag(SHARD_TAG);
			display.setPos(boss.getX(), y, boss.getZ());
			display.setItemStack(new ItemStack(shard));
			display.setItemTransform(net.minecraft.world.item.ItemDisplayContext.FIXED);
			float angle = random.nextFloat() * (float) (Math.PI * 2);
			float spin = random.nextFloat() * (float) (Math.PI * 2);
			display.setTransformation(transform(new org.joml.Vector3f(), spin, 0.0F));
			level.addFreshEntity(display);
			float reach = (boss.getBbWidth() / 2.0F + 0.6F) * (0.7F + random.nextFloat() * 0.6F);
			FLYING.add(new Shard(display, new org.joml.Vector3f((float) Math.cos(angle) * reach, 0.0F, (float) Math.sin(angle) * reach), spin,
					level.getGameTime()));
		}
		level.playSound(null, boss.blockPosition(), mutation == Mutation.BEJEWELED ? SoundEvents.AMETHYST_CLUSTER_BREAK : SoundEvents.STONE_BREAK,
				SoundSource.HOSTILE, 1.2F, 0.8F);
	}

	private static com.mojang.math.Transformation transform(org.joml.Vector3f translation, float spin, float tumble) {
		org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateY(spin).rotateX(tumble);
		return new com.mojang.math.Transformation(translation, rotation, new org.joml.Vector3f(1.5F), null);
	}

	/** Each shard: up and out over 8 ticks, then out and down over 12 (the client smooths both), then it's removed. */
	private static void tickShards(net.minecraft.server.MinecraftServer server) {
		if (FLYING.isEmpty()) {
			return;
		}
		for (java.util.Iterator<Shard> it = FLYING.iterator(); it.hasNext();) {
			Shard shard = it.next();
			Display.ItemDisplay display = shard.display();
			long age = display.level().getGameTime() - shard.born();
			if (display.isRemoved() || age >= SHARD_LIFE) {
				display.discard();
				it.remove();
			} else if (age == 1) {
				display.setTransformationInterpolationDelay(0);
				display.setTransformationInterpolationDuration(8);
				display.setTransformation(transform(new org.joml.Vector3f(shard.direction()).mul(0.6F).add(0, 0.7F, 0), shard.spin(), 1.5F));
			} else if (age == 9) {
				display.setTransformationInterpolationDelay(0);
				display.setTransformationInterpolationDuration(12);
				display.setTransformation(transform(new org.joml.Vector3f(shard.direction()).add(0, -0.9F, 0), shard.spin(), 3.5F));
			}
		}
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
		// All bold, between an obfuscated (scrambling) dark red AZ and ZA.
		return Messages.c("AZ", 0xAA0000).withStyle(style -> style.withObfuscated(true))
				.append(Messages.c(" MUTATION! ", HuntDefs.RED).withStyle(style -> style.withObfuscated(false)))
				.append(mutation.colored().withStyle(style -> style.withObfuscated(false))).append(Messages.c(" ", 0xFFFFFF).withStyle(style -> style.withObfuscated(false)))
				.append(Messages.c(tier.bossName() + " ", hunt.color()).withStyle(style -> style.withObfuscated(false)))
				.append(Messages.c("ZA", 0xAA0000))
				.withStyle(style -> style.withBold(true));
	}
}
