package com.huntlike.path;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import com.huntlike.hunt.HuntData;
import com.huntlike.hunt.Messages;
import com.huntlike.hunt.QuestManager;

/**
 * The Path of the Wildfire, found in the Soul Nexus. Its goals, one after another; each gives a Wildfire point, and the
 * first three raise the Blazing Heart's drop chance by 1%. Points buy the Path's buffs (1 point each, free to take back).
 * Draft values, to measure how strong the buffs are.
 */
public final class WildfirePath {
	private WildfirePath() {
	}

	public static final String NAME = "Path of the Wildfire";
	public static final int COLOR = 0xFF8A2A;
	public static final List<String> GOALS = List.of(
			"Find a Blazing Heart",
			"Consume a Blazing Heart",
			"Slay 50 blazes",
			"Defeat a Blazing Piglin",
			"Defeat a Blazing Wither Skeleton",
			"Defeat a Demonling",
			"Survive the Blazing Spawner",
			"Fill Canavar's Chalice with 100 kills of fleshy mobs",
			"Defeat the Spectral Sorrow",
			"Slay 10 Evilborns");
	/** The hint each goal shows while it's the next one. */
	public static final List<String> HINTS = List.of(
			"Deep within the Nether, dark fortresses are home to burning souls. Some of them still keep a pounding heart inside their demonic bodies.",
			"Taste the beating remnant of a burning soul, and feel the pain held within its sorrow.",
			"One flame lights a torch, but many can set a forest ablaze in a screaming wildfire.",
			"Let the body of a greedy soul burn in the hue that shines within the metal it desires.",
			"Charred souls corroded by anger, withering remains of what were once warriors. Let them burn in the color their souls once burned with in rage.",
			"A heart that has burned twice still remembers the fire it came from. Return it to a burning soul, and face the demon it awakens.",
			"Into the cruel cages that trap suffering, fiery souls, bring a sign of life and redemption: the organ of burning blood, which must burn once more before it perishes. Give the organ to the cage, and let the mightiest sorrowing souls rise to trial whoever dared to wake them.",
			"Fill the ornament with neither rage nor fire, though it was born of both. Let it hold what makes us human, and what makes others what they are. Even in those who are human no more, the nectar of life still flows through their flesh.",
			"To what lies dry and suffering in hell, thirsting for the liquid it lost, offer the fruit of your effort. From it will come forth all that suffering can make of a creature.",
			"Convert one of those who reasons, thinks, works and brings food to feed their family. Forcefully, one of nature's creations of flesh will convert to one of ashes, one of fire, one of evil. Then, strip various of the evilborns of their life.");
	public static final int BLAZES = 50;
	/** Evilborns to slay on the tenth goal. */
	public static final int EVILBORNS = 10;
	/** Blazing Heart from a blaze: 2%, +1% per Looting level, +1% for each of the first three goals reached. */
	public static final int BASE_CHANCE = 2;
	public static final int BONUS_GOALS = 3;

	/**
	 * The Path's skills, as a tree (the user's design). I: Netherspawn. II (after Netherspawn): Hot Blows, Raging
	 * Metabolism, Golden Style. III: Fiery Blows (after Hot Blows), Hot food! (after Raging Metabolism), Nether Veil and
	 * Hot Tub (after Golden Style). The tier is the numeral shown in its square. The order is the save bit: keep it (the
	 * first five took the places of the old Fire Resistance, Burning Blows, Nether Strength, Warm Heart and Hot Heart).
	 */
	public enum Buff {
		NETHERSPAWN("Netherspawn", "Become more resistant to normal fire damage.", null, 1),
		HOT_BLOWS("Hot Blows", "Have a chance of lighting enemies ablaze!", NETHERSPAWN, 2),
		FIERY_BLOWS("Fiery Blows", "The chance of lighting enemies on fire is increased!", HOT_BLOWS, 3),
		RAGING_METABOLISM("Raging Metabolism", "Blazing Hearts go down faster!", NETHERSPAWN, 2),
		HOT_FOOD("Hot food!", "Blazing Heart's ability now deals more damage", RAGING_METABOLISM, 3),
		GOLDEN_STYLE("Golden Style", "Your way of walking changes and Piglins seem to not be against you so much anymore.", NETHERSPAWN, 2),
		NETHER_VEIL("Nether Veil", "Nether wanderers won't notice you...", GOLDEN_STYLE, 3),
		HOT_TUB("Hot Tub", "Gain fire immunity upon contact with lava for a brief period of time.", GOLDEN_STYLE, 3);

		public final String title;
		public final String description;
		/** The skill it needs first, or null. */
		public final Buff parent;
		public final int tier;

		Buff(String title, String description, Buff parent, int tier) {
			this.title = title;
			this.description = description;
			this.parent = parent;
			this.tier = tier;
		}

		public int bit() {
			return 1 << ordinal();
		}
	}

	/** Netherspawn: 25% less fire damage (vanilla's fire damage types: fire, burning, lava, magma, fireballs, campfires). */
	public static final float NETHERSPAWN_RESISTANCE = 0.25F;
	/** Hot Blows / Fiery Blows: the chance a melee hit in the Nether sets the target on fire, and for how long. */
	public static final float HOT_BLOWS_CHANCE = 0.25F;
	public static final float FIERY_BLOWS_CHANCE = 0.5F;
	public static final int BLOWS_FIRE_SECONDS = 2;
	public static final int BEAM_TICKS = 3 * 20;
	public static final float BEAM_DAMAGE = 2.0F;
	public static final int BEAM_HIT_TICKS = 5;
	public static final double BEAM_RANGE = 12.0;
	/** Hot food!: the Blazing Heart's fire beam deals twice the damage. */
	public static final float HOT_FOOD_MULTIPLIER = 2.0F;
	/** Hot Tub: touching lava gives 5 s of fire immunity, at most once every 3 minutes. */
	public static final int HOT_TUB_TICKS = 5 * 20;
	public static final int HOT_TUB_COOLDOWN = 3 * 60 * 20;
	/** Nether Veil: a ghast hit by a player carries this tag + the player's UUID, and may hunt that player again. */
	public static final String PROVOKED_TAG = "huntlike.provoked.";

	/** Client side: whether the local player has Raging Metabolism (set by the client, for the eating animation). */
	public static java.util.function.BooleanSupplier clientRagingMetabolism = () -> false;

	public static boolean clientHasRagingMetabolism() {
		return clientRagingMetabolism.getAsBoolean();
	}

	public static boolean discovered(HuntData data) {
		return data.wildfireGoals > 0;
	}

	public static boolean has(HuntData data, Buff buff) {
		return (data.wildfireBuffs & buff.bit()) != 0;
	}

	/** Whether a buff that needs this one is taken (then this one can't be given back). */
	public static boolean hasChild(HuntData data, Buff buff) {
		for (Buff other : Buff.values()) {
			if (other.parent == buff && has(data, other)) {
				return true;
			}
		}
		return false;
	}

	/** Most points the Path can give (one per goal). */
	public static int maxPoints() {
		return GOALS.size();
	}

	public static int points(HuntData data) {
		return data.wildfireGoals;
	}

	public static int spent(HuntData data) {
		return Integer.bitCount(data.wildfireBuffs);
	}

	public static int free(HuntData data) {
		return Math.max(0, points(data) - spent(data));
	}

	/** The Blazing Heart's drop chance from a blaze, in percent. */
	public static int heartChance(HuntData data, int looting) {
		return BASE_CHANCE + Math.min(BONUS_GOALS, data.wildfireGoals) + Math.max(0, looting);
	}

	/** Reaches goal `index` if it is the next one. */
	public static void reach(ServerPlayer player, HuntData data, int index) {
		if (data.wildfireGoals != index) {
			return;
		}
		data.wildfireGoals++;
		player.setAttached(QuestManager.DATA, data);
		// Discovering the Path (the first goal) says only that; every later goal grows the affinity.
		player.sendSystemMessage(Messages.c(index == 0 ? "You feel how a fiery path opens within your soul..."
				: "Your affinity to the Path of the Wildfire has grown...", COLOR));
		QuestManager.playSound(player, SoundEvents.LAVA_EXTINGUISH, 1.0F);
		QuestManager.sync(player);
	}

	/** Buys (delta > 0) or takes back (delta < 0) a buff. */
	public static void change(ServerPlayer player, HuntData data, int buff, int delta) {
		if (buff < 0 || buff >= Buff.values().length || !discovered(data)) {
			return;
		}
		Buff b = Buff.values()[buff];
		boolean owned = has(data, b);
		if (delta > 0 && !owned && free(data) > 0 && (b.parent == null || has(data, b.parent))) {
			data.wildfireBuffs |= b.bit();
		} else if (delta < 0 && owned && !hasChild(data, b)) {
			data.wildfireBuffs &= ~b.bit();
		} else {
			QuestManager.playSound(player, SoundEvents.CHAIN_BREAK, 1.0F);
			return;
		}
		player.setAttached(QuestManager.DATA, data);
		player.level().playSound(null, player.blockPosition(), delta > 0 ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH,
				SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	// ---------------------------------------------------------------- damage

	/** Netherspawn, Hot Blows and Fiery Blows; a ghast hit by a player is provoked (Nether Veil). */
	public static float adjust(LivingEntity target, DamageSource source, float amount) {
		// Imps' fireballs hit twice as hard as a blaze's; the Demonling's 50% harder.
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball) {
			if (source.getEntity() instanceof Imp) {
				amount *= 2.0F;
			} else if (source.getEntity() instanceof Demonling) {
				amount *= 1.5F;
			}
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
			if (target.getType() == EntityTypes.GHAST) {
				target.addTag(PROVOKED_TAG + attacker.getStringUUID());
			}
			HuntData data = QuestManager.data(attacker);
			if (attacker.level().dimension() == Level.NETHER && source.getDirectEntity() == attacker && has(data, Buff.HOT_BLOWS)) {
				float chance = has(data, Buff.FIERY_BLOWS) ? FIERY_BLOWS_CHANCE : HOT_BLOWS_CHANCE;
				if (attacker.getRandom().nextFloat() < chance) {
					target.igniteForSeconds(BLOWS_FIRE_SECONDS);
				}
			}
		}
		if (target instanceof ServerPlayer player && source.is(net.minecraft.world.damagesource.DamageTypes.LAVA) && hotTub(player)) {
			return 0.0F;
		}
		if (target instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FIRE) && has(QuestManager.data(player), Buff.NETHERSPAWN)) {
			amount *= 1.0F - NETHERSPAWN_RESISTANCE;
		}
		return amount;
	}

	// ---------------------------------------------------------------- Hot Tub, Golden Style, Nether Veil

	/** Hot Tub: server tick of each player's last soak. */
	private static final Map<UUID, Long> HOT_TUB_USED = new HashMap<>();

	/** Hot Tub: if the player has it and it's ready, 5 s of fire immunity now. Returns whether it started. */
	public static boolean hotTub(ServerPlayer player) {
		if (!has(QuestManager.data(player), Buff.HOT_TUB)) {
			return false;
		}
		long now = player.level().getServer().getTickCount();
		Long used = HOT_TUB_USED.get(player.getUUID());
		if (used != null && now - used < HOT_TUB_COOLDOWN && now >= used) {
			return false;
		}
		HOT_TUB_USED.put(player.getUUID(), now);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
				HOT_TUB_TICKS, 0, false, true, true));
		player.level().playSound(null, player.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	/** Golden Style: piglins see the player as if they wore gold. */
	public static boolean goldenStyle(LivingEntity entity) {
		return entity instanceof ServerPlayer player && has(QuestManager.data(player), Buff.GOLDEN_STYLE);
	}

	/** Nether Veil: a (plain) ghast doesn't target the player unless that player hit it first. */
	public static boolean veiledFrom(Mob mob, LivingEntity target) {
		return mob.getType() == EntityTypes.GHAST && target instanceof ServerPlayer player
				&& has(QuestManager.data(player), Buff.NETHER_VEIL) && !mob.entityTags().contains(PROVOKED_TAG + player.getStringUUID());
	}

	// ---------------------------------------------------------------- the Blazing Heart

	/** Players breathing fire: ticks left and the damage per hit. */
	private static final Map<UUID, float[]> BEAMS = new HashMap<>();

	public static boolean beaming(ServerPlayer player) {
		return BEAMS.containsKey(player.getUUID());
	}

	public static void onHeartEaten(ServerPlayer player) {
		HuntData data = QuestManager.data(player);
		player.igniteForSeconds(5);
		int ticks = BEAM_TICKS;
		float damage = BEAM_DAMAGE * (has(data, Buff.HOT_FOOD) ? HOT_FOOD_MULTIPLIER : 1.0F);
		BEAMS.put(player.getUUID(), new float[] {ticks, damage});
		player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		reach(player, data, 0);
		reach(player, data, 1);
	}

	private static void tickBeams(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, float[]>> it = BEAMS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, float[]> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			float[] beam = entry.getValue();
			if (player == null || !player.isAlive() || --beam[0] < 0) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(BEAM_RANGE));
			HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.MISS) {
				end = hit.getLocation();
			}
			Vec3 dir = end.subtract(eye);
			double length = dir.length();
			for (double d = 0.8; d < length; d += 0.5) {
				Vec3 at = eye.add(dir.scale(d / length));
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.15, at.z, 1, 0.05, 0.05, 0.05, 0.01);
			}
			if ((int) beam[0] % 10 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.0F);
			}
			if ((int) beam[0] % BEAM_HIT_TICKS != 0) {
				continue;
			}
			Vec3 tip = end;
			for (Entity entity : level.getEntities(player, new AABB(eye, tip).inflate(1.0),
					e -> e instanceof LivingEntity living && living.isAlive() && e.getBoundingBox().inflate(0.4).clip(eye, tip).isPresent())) {
				LivingEntity living = (LivingEntity) entity;
				living.hurtServer(level, level.damageSources().playerAttack(player), beam[1]);
				living.igniteForSeconds(3);
			}
		}
	}

	// ---------------------------------------------------------------- blazes and the Blazing mobs

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		collectBlood(player, entity);
		HuntData data = QuestManager.data(player);
		if (entity.getType() == EntityTypes.BLAZE) {
			var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
			int chance = heartChance(data, EnchantmentHelper.getEnchantmentLevel(looting, player));
			if (player.getRandom().nextInt(100) < chance) {
				entity.spawnAtLocation(level, new ItemStack(ModPaths.BLAZING_HEART));
			}
			if (data.wildfireGoals == 2) {
				data.wildfireBlazes++;
				player.setAttached(QuestManager.DATA, data);
				if (data.wildfireBlazes >= BLAZES) {
					reach(player, data, 2);
				} else if (data.wildfireBlazes % 10 == 0) {
					QuestManager.sync(player);
				}
			}
		} else if (entity instanceof BlazingPiglin) {
			reach(player, data, 3);
		} else if (entity instanceof BlazingWitherSkeleton) {
			reach(player, data, 4);
		} else if (entity instanceof Demonling) {
			reach(player, data, 5);
		} else if (entity instanceof SpectralSorrow) {
			say(player, "Canavar: This was my daily routine...", 0);
			say(player, "Canavar: You are strong indeed, young one...", 60);
			reach(player, data, 8);
		} else if (entity instanceof Evilborn && data.wildfireGoals == 9 && data.wildfireEvilborns < EVILBORNS) {
			data.wildfireEvilborns++;
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
			if (data.wildfireEvilborns >= EVILBORNS) {
				canavarsStory(player);
			}
		}
	}

	/**
	 * Feeding hearts: a Blazing Heart turns a piglin into a Blazing Piglin; a Blazing Heart ★ makes a Blazing Wither
	 * Skeleton; a Blazing Heart ★★ turns a blaze into a Demonling.
	 */
	public static InteractionResult feed(ServerPlayer player, Mob target, ItemStack stack) {
		ServerLevel level = player.level();
		if (target.getType() == EntityTypes.PIGLIN && !target.isBaby() && stack.is(ModPaths.BLAZING_HEART)) {
			stack.consume(1, player);
			transform(player, target, new BlazingPiglin(ModPaths.BLAZING_PIGLIN, level));
			return InteractionResult.SUCCESS;
		}
		if (target.getType() == EntityTypes.WITHER_SKELETON && stack.is(ModPaths.BLAZING_HEART_STAR)) {
			stack.consume(1, player);
			transform(player, target, new BlazingWitherSkeleton(ModPaths.BLAZING_WITHER_SKELETON, level));
			return InteractionResult.SUCCESS;
		}
		if (target.getType() == EntityTypes.BLAZE && stack.is(ModPaths.BLAZING_HEART_STAR2)) {
			stack.consume(1, player);
			transform(player, target, new Demonling(ModPaths.DEMONLING, level));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}


	private static void transform(ServerPlayer player, Mob from, Mob to) {
		ServerLevel level = player.level();
		to.snapTo(from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
		to.setYHeadRot(from.getYHeadRot());
		BlazingMob.setFeeder(to, player.getUUID());
		BlazingMob.setBorn(to, level.getGameTime());
		from.discard();
		level.addFreshEntity(to);
		to.setTarget(player);
		level.sendParticles(ParticleTypes.LAVA, to.getX(), to.getY() + 1.0, to.getZ(), 20, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, to.getX(), to.getY() + 1.0, to.getZ(), 40, 0.4, 0.9, 0.4, 0.05);
		level.playSound(null, to.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (player.isSpectator() || !(entity instanceof Mob mob)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(ModPaths.BLOOD_CRYSTALLIZATION) && mob.getType() == EntityTypes.VILLAGER) {
			if (player instanceof ServerPlayer serverPlayer) {
				corrupt(serverPlayer, mob, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (!stack.is(ModPaths.BLAZING_HEART) && !stack.is(ModPaths.BLAZING_HEART_STAR) && !stack.is(ModPaths.BLAZING_HEART_STAR2)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			return feed(serverPlayer, mob, stack);
		}
		boolean feedable = mob.getType() == EntityTypes.PIGLIN && stack.is(ModPaths.BLAZING_HEART)
				|| mob.getType() == EntityTypes.WITHER_SKELETON && stack.is(ModPaths.BLAZING_HEART_STAR)
				|| mob.getType() == EntityTypes.BLAZE && stack.is(ModPaths.BLAZING_HEART_STAR2);
		return feedable ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** A Blazing Heart ★★★ on a blaze spawner (only those: it sends the player to a fortress): the Blazing Spawner's three rounds. */
	public static boolean upgradeSpawner(ServerPlayer player, net.minecraft.core.BlockPos pos, ItemStack stack) {
		ServerLevel level = player.level();
		if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner)
				|| !stack.is(ModPaths.BLAZING_HEART_STAR3)
				|| !spawner.saveWithoutMetadata(level.registryAccess()).toString().contains("minecraft:blaze")) {
			return false;
		}
		level.setBlockAndUpdate(pos, ModPaths.BLAZING_SPAWNER.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof BlazingSpawnerBlockEntity blazing) {
			blazing.setOwner(player.getUUID());
		}
		stack.consume(1, player);
		level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.03);
		level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.5F);
		player.sendSystemMessage(Messages.c("The spawner burns hotter... something is coming out of it.", COLOR));
		return true;
	}

	/** Creatures with flesh: animals (not bees or skeleton horses), zombies, piglins, villagers, illagers, fish, dolphins, squids, creepers and guardians. */
	public static boolean hasFlesh(LivingEntity entity) {
		if (entity.getType() == EntityTypes.BEE || entity.getType() == EntityTypes.SKELETON_HORSE) {
			return false;
		}
		return entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| entity instanceof net.minecraft.world.entity.monster.Zoglin
				|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
				|| entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
				|| entity instanceof net.minecraft.world.entity.raid.Raider
				|| entity instanceof net.minecraft.world.entity.animal.fish.AbstractFish
				|| entity instanceof net.minecraft.world.entity.animal.dolphin.Dolphin
				|| entity instanceof net.minecraft.world.entity.animal.squid.Squid
				|| entity instanceof net.minecraft.world.entity.monster.Creeper
				|| entity instanceof net.minecraft.world.entity.monster.Guardian;
	}

	/** A fleshy kill with the Chalice in the off-hand: one drop of blood; at 100 it becomes the Full Chalice. */
	private static void collectBlood(ServerPlayer player, LivingEntity victim) {
		ItemStack chalice = player.getOffhandItem();
		if (!chalice.is(ModPaths.CHALICE_ITEM) || !hasFlesh(victim)) {
			return;
		}
		int blood = Math.min(ChaliceItem.MAX_BLOOD, ChaliceItem.blood(chalice) + 1);
		if (blood >= ChaliceItem.MAX_BLOOD) {
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(ModPaths.FULL_CHALICE));
			player.sendSystemMessage(Messages.c("The chalice is full.", 0xAA0000));
			reach(player, QuestManager.data(player), 7);
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.5F);
			return;
		}
		chalice.set(ModPaths.BLOOD, blood);
		player.level().sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x6A0000, 1.0F), victim.getX(),
				victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 8, 0.2, 0.3, 0.2, 0.0);
	}

	/** The Full Chalice poured on a dried ghast: the Spectral Sorrow rises; the chalice comes back empty. */
	public static boolean feedDriedGhast(ServerPlayer player, net.minecraft.core.BlockPos pos, InteractionHand hand) {
		ServerLevel level = player.level();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ModPaths.FULL_CHALICE) || !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.DRIED_GHAST)) {
			return false;
		}
		level.removeBlock(pos, false);
		SpectralSorrow sorrow = ModPaths.SPECTRAL_SORROW.create(level, EntitySpawnReason.TRIGGERED);
		if (sorrow == null) {
			return false;
		}
		sorrow.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		level.addFreshEntity(sorrow);
		sorrow.setTarget(player);
		player.setItemInHand(hand, new ItemStack(ModPaths.CHALICE_ITEM));
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x8A0303, 2.0F), pos.getX() + 0.5, pos.getY() + 1.5,
				pos.getZ() + 0.5, 60, 1.2, 1.2, 1.2, 0.0);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 30, 1.0, 1.0, 1.0, 0.03);
		level.playSound(null, pos, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** Things done later for a player: player, game tick, action. */
	private static final java.util.List<Object[]> LATER = new java.util.ArrayList<>();

	private static void later(ServerPlayer player, int delay, java.util.function.Consumer<ServerPlayer> action) {
		LATER.add(new Object[] {player.getUUID(), player.level().getServer().getTickCount() + delay, action});
	}

	/** A line from Canavar (dark red), now or after `delay` ticks. */
	private static void say(ServerPlayer player, String text, int delay) {
		if (delay <= 0) {
			player.sendSystemMessage(Messages.c(text, 0xAA0000));
		} else {
			later(player, delay, p -> p.sendSystemMessage(Messages.c(text, 0xAA0000)));
		}
	}

	/** Canavar's lines after the tenth Evilborn, 2 s apart; the goal is reached after the last one. */
	public static final List<String> CANAVAR_STORY = List.of(
			"Canavar: You are one of those hunters that seeks power no matter what...",
			"Canavar: Either for it to let you save more people than you take",
			"Canavar: Or for your own good...",
			"Canavar: I won't suspect of you...",
			"Canavar: But I hope you are on life's side...",
			"Canavar: I wasn't when I was alive",
			"Canavar: But now I wish I was",
			"Canavar: ...",
			"Canavar: By now I'm seeking for someone to pass the torch to...",
			"Canavar: I was a champion of our race",
			"Canavar: I wish to not make my knowledge vanish...");
	public static final int STORY_LINE_TICKS = 40;

	private static void canavarsStory(ServerPlayer player) {
		for (int i = 0; i < CANAVAR_STORY.size(); i++) {
			say(player, CANAVAR_STORY.get(i), i * STORY_LINE_TICKS);
		}
		later(player, CANAVAR_STORY.size() * STORY_LINE_TICKS, p -> reach(p, QuestManager.data(p), 9));
	}

	/** The Blood Crystallization on a villager: it turns into an Evilborn (one use). */
	public static boolean corrupt(ServerPlayer player, Mob villager, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ModPaths.BLOOD_CRYSTALLIZATION) || villager.getType() != EntityTypes.VILLAGER) {
			return false;
		}
		ServerLevel level = player.level();
		Evilborn evilborn = villager.convertTo(ModPaths.EVILBORN, net.minecraft.world.entity.ConversionParams.single(villager, false, false),
				e -> e.setPersistenceRequired());
		if (evilborn == null) {
			return false;
		}
		stack.hurtAndBreak(1, player, hand);
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x8A0303, 1.6F), evilborn.getX(), evilborn.getY() + 1.0,
				evilborn.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ASH, evilborn.getX(), evilborn.getY() + 1.0, evilborn.getZ(), 60, 0.5, 0.9, 0.5, 0.02);
		level.sendParticles(ParticleTypes.FLAME, evilborn.getX(), evilborn.getY() + 1.0, evilborn.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
		level.playSound(null, evilborn.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.2F, 0.5F);
		return true;
	}

	private static void tickLater(MinecraftServer server) {
		List<Object[]> due = new java.util.ArrayList<>();
		for (Iterator<Object[]> it = LATER.iterator(); it.hasNext();) {
			Object[] entry = it.next();
			if (server.getTickCount() >= (int) entry[1]) {
				it.remove();
				due.add(entry);
			}
		}
		for (Object[] entry : due) {
			ServerPlayer player = server.getPlayerList().getPlayer((UUID) entry[0]);
			if (player != null) {
				@SuppressWarnings("unchecked")
				java.util.function.Consumer<ServerPlayer> action = (java.util.function.Consumer<ServerPlayer>) entry[2];
				action.accept(player);
			}
		}
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isSpectator() && stack.is(ModPaths.FULL_CHALICE)
				&& level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.DRIED_GHAST)) {
			if (player instanceof ServerPlayer serverPlayer) {
				feedDriedGhast(serverPlayer, hit.getBlockPos(), hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSpectator() || !stack.is(ModPaths.BLAZING_HEART_STAR3)
				|| !(level.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			return upgradeSpawner(serverPlayer, hit.getBlockPos(), stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register(WildfirePath::onDeath);
		UseEntityCallback.EVENT.register(WildfirePath::onUseEntity);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(WildfirePath::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickBeams(server);
			tickLater(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isInLava() && !player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
					hotTub(player);
				}
			}
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			// Holding a Blazing Heart discovers the Path.
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				HuntData data = QuestManager.data(player);
				if (data.wildfireGoals == 0 && player.getInventory().contains(new ItemStack(ModPaths.BLAZING_HEART))) {
					reach(player, data, 0);
				}
			}
		});
	}
}
