package com.huntlike.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.huntlike.HuntLike;
import com.huntlike.entity.ClusterBall;
import com.huntlike.entity.ModEntities;
import com.huntlike.entity.cluster.TheVile;
import com.huntlike.hunt.HuntData.Quest;
import com.huntlike.hunt.HuntDefs.ClusterSpec;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.HuntDefs.Tier;
import com.huntlike.network.HuntPayloads;

/**
 * Cluster Hunt boss fights: a main body that splits (Mitosis) into smaller bodies, and the main body's abilities:
 * Sticky Fingers, Baller, Yummy!, Watch out! and Thermodynamic. Only the main body uses abilities; split bodies
 * just jump and melee. Every ability targets only the quest owner.
 */
public final class ClusterFight {
	private ClusterFight() {
	}

	/** Mitosis generation of a body: 0 is the main body. */
	public static final String GEN_PREFIX = "huntlike_gen:";
	public static final ResourceKey<DamageType> DIGESTED = ResourceKey.create(Registries.DAMAGE_TYPE, HuntLike.id("digested"));
	public static final ResourceKey<DamageType> STICKY_FINGERS = ResourceKey.create(Registries.DAMAGE_TYPE,
			HuntLike.id("sticky_fingers"));

	private static final int BALLER_INTERVAL = 20 * 5;
	/** Sticky Fingers: damage per main-body jump (Tiers I-III / IV-V). */
	public static final float JUMP_DAMAGE_LOW = 3.0F;
	public static final float JUMP_DAMAGE_HIGH = 4.0F;
	private static final float BALLER_DAMAGE = 8.0F;
	private static final int DIGEST_INTERVAL = 40;
	private static final int WATCH_ROUNDS = 3;
	private static final int WATCH_TELEPORT_TICKS = 20;
	private static final int WATCH_ROUND_TICKS = WATCH_TELEPORT_TICKS + 60;
	private static final net.minecraft.resources.Identifier YUMMY_SPEED = HuntLike.id("yummy.speed");
	private static final net.minecraft.resources.Identifier YUMMY_JUMP = HuntLike.id("yummy.jump");

	/** Live state of one owner's fight. */
	private static final class Fight {
		final Set<UUID> helpers = new HashSet<>();
		int ballerTimer;
		int coreTimer = 100;
		boolean yummyDone;
		boolean yummyActive;
		int[] keys = new int[0];
		int progress;
		int digestTimer;
		boolean watch75;
		boolean watch25;
		boolean watchActive;
		int watchTicks;
		/** Better Than Ever!: earned by escaping Yummy! in Gel or Thermodynamic Armor; ends with the fight. */
		boolean betterThanEver;
	}

	/** Players set on fire by a Cluster boss (Watch out!): Room Temperature doesn't put that fire out. */
	private static final Set<UUID> BOSS_BURNING = new HashSet<>();
	/** Yummy!'s Slowness I after escaping, in ticks. */
	public static final int YUMMY_SLOWNESS_TICKS = 5 * 20;

	public static boolean bossBurning(ServerPlayer player) {
		if (player.getRemainingFireTicks() <= 0) {
			BOSS_BURNING.remove(player.getUUID());
			return false;
		}
		return BOSS_BURNING.contains(player.getUUID());
	}

	/** Better Than Ever! is active for this player's fight. */
	public static boolean betterThanEver(ServerPlayer owner) {
		Fight fight = FIGHTS.get(owner.getUUID());
		return fight != null && fight.betterThanEver;
	}

	/** Multiplier for Temperature damage (scythes): 1.2 with Better Than Ever!, else 1. */
	public static float temperatureMultiplier(ServerPlayer owner) {
		return betterThanEver(owner) ? 1.0F + com.huntlike.gear.ModGear.BETTER_THAN_EVER : 1.0F;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	/** Baller projectiles thrown so far (read by the automated test). */
	public static int ballsThrown;
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private static Fight fight(ServerPlayer owner) {
		return FIGHTS.computeIfAbsent(owner.getUUID(), id -> new Fight());
	}

	// ---------------------------------------------------------------- bodies

	public static boolean isBody(Entity entity) {
		return generation(entity) >= 0;
	}

	public static int generation(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(GEN_PREFIX)) {
				try {
					return Integer.parseInt(tag.substring(GEN_PREFIX.length()));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** Living bodies of this owner's cluster boss. */
	public static List<Mob> bodies(ServerPlayer owner) {
		String ownerTag = BossController.OWNER_PREFIX + owner.getUUID();
		return owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(160.0),
				mob -> mob.isAlive() && isBody(mob) && mob.entityTags().contains(ownerTag));
	}

	public static int[] bodyIds(ServerPlayer owner) {
		return bodies(owner).stream().mapToInt(Entity::getId).toArray();
	}

	private static Mob mainBody(List<Mob> bodies) {
		for (Mob body : bodies) {
			if (generation(body) == 0) {
				return body;
			}
		}
		return null;
	}

	private static EntityType<? extends Mob> bodyType(Tier tier, RandomSource random) {
		if (tier.number() == 4) {
			return random.nextBoolean() ? ModEntities.VARIANCE_SLIME : ModEntities.VARIANCE_MAGMA;
		}
		return tier.mob();
	}

	private static Mob createBody(ServerLevel level, ServerPlayer owner, Tier tier, int generation, Vec3 pos, float yaw) {
		ClusterSpec spec = HuntDefs.clusterSpec(tier);
		Mob body = bodyType(tier, owner.getRandom()).create(level, EntitySpawnReason.MOB_SUMMONED);
		if (body == null) {
			return null;
		}
		body.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		body.setBaby(false);
		if (body instanceof AbstractCubeMob cube) {
			cube.setSize(spec.size(generation), false);
		}
		HuntData.Quest packQuest = QuestManager.data(owner).quest;
		int packSize = packQuest == null ? 1 : packQuest.packSize;
		setBase(body, Attributes.MAX_HEALTH, spec.health(generation) * packSize);
		if (packQuest != null && packQuest.isPack()) {
			AttributeInstance scale = body.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(scale.getBaseValue() * BossController.packScale(packQuest));
			}
		}
		setBase(body, Attributes.ATTACK_DAMAGE, tier.melee());
		setBase(body, Attributes.FOLLOW_RANGE, 64.0);
		body.setHealth(spec.health(generation) * packSize);
		body.setPersistenceRequired();
		body.addTag(BossController.BOSS_TAG);
		body.addTag(BossController.OWNER_PREFIX + owner.getUUID());
		body.addTag(GEN_PREFIX + generation);
		body.setTarget(owner);
		return body;
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ---------------------------------------------------------------- spawn, tick, death

	public static void spawn(ServerPlayer player, HuntData data, Quest quest) {
		ServerLevel level = player.level();
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		Mob main = createBody(level, player, tier, 0, new Vec3(quest.summonX + 0.5, quest.summonY, quest.summonZ + 0.5),
				player.getYRot() + 180.0F);
		if (main == null) {
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		if (main instanceof TheVile vile) {
			vile.setMagmaCore(player.getRandom().nextBoolean());
		}
		FIGHTS.put(player.getUUID(), new Fight());
		quest.bossUuid = main.getUUID().toString();
		quest.bossSpawnTime = level.getGameTime();
		quest.phase = HuntData.PHASE_BOSS;
		player.setAttached(QuestManager.DATA, data);
		level.addFreshEntity(main);
		level.sendParticles(ParticleTypes.EXPLOSION, main.getX(), main.getY() + 0.5, main.getZ(), 1, 0, 0, 0, 0);
		player.sendSystemMessage(Messages.bossSpawned(hunt, tier.number()));
		QuestManager.playSound(player, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F);
		QuestManager.playSound(player, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, 0.8F);
		QuestManager.sync(player);
	}

	public static void tick(ServerPlayer player, HuntData data, Quest quest) {
		List<Mob> bodies = bodies(player);
		if (bodies.isEmpty()) {
			// The boss vanished without being killed: one kill short of calling it again.
			discardAll(player);
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		if (QuestManager.isPeaceful(player)) {
			discardAll(player);
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		ServerLevel level = player.level();
		long elapsed = level.getGameTime() - quest.bossSpawnTime;
		double nearest = Double.MAX_VALUE;
		for (Mob body : bodies) {
			nearest = Math.min(nearest, HuntingPacks.nearestMember(player, quest, body));
		}
		if (elapsed > QuestManager.BOSS_TIME_LIMIT || nearest > QuestManager.FLEE_DISTANCE) {
			QuestManager.fail(player, data);
			return;
		}
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		Fight fight = fight(player);
		updateBar(player, hunt, tier, bodies, elapsed, fight);

		Mob main = mainBody(bodies);
		boolean frozen = fight.yummyActive || fight.watchActive;
		for (Mob body : bodies) {
			if (body != main || !frozen) {
				if (body.getTarget() != player) {
					body.setTarget(player);
				}
				BossController.unstick(body, player);
			}
			if (elapsed % 4 == 0) {
				double w = body.getBbWidth() / 2.0;
				level.sendParticles(new DustParticleOptions(tier.color(), 1.0F), body.getX(), body.getY() + body.getBbHeight() / 2.0,
						body.getZ(), 1, w, body.getBbHeight() / 2.0, w, 0.0);
			}
		}

		if (main != null) {
			main.setNoAi(frozen);
			if (elapsed % 20 == 0) {
				main.heal(regen(tier, fight));
			}
			int t = tier.number();
			float fraction = main.getHealth() / main.getMaxHealth();
			if (t >= 2 && !frozen && ++fight.ballerTimer >= BALLER_INTERVAL) {
				fight.ballerTimer = 0;
				if (main.hasLineOfSight(player) && !player.isSpectator()) {
					baller(main, player, level);
				}
			}
			if (t >= 3 && !fight.yummyDone && !fight.watchActive && fraction <= 0.5F) {
				startYummy(player, main, tier, fight);
			}
			if (t >= 4 && !fight.yummyActive && !fight.watchActive) {
				if (!fight.watch75 && fraction <= 0.75F) {
					fight.watch75 = true;
					startWatch(fight);
				} else if (!fight.watch25 && fraction <= 0.25F) {
					fight.watch25 = true;
					startWatch(fight);
				}
			}
		}
		// Thermodynamic: The Vile switches cores every 5-10 seconds, and keeps doing it after it splits (every piece switches).
		if (tier.number() >= 5 && --fight.coreTimer <= 0) {
			fight.coreTimer = 100 + player.getRandom().nextInt(101);
			for (Mob body : bodies) {
				if (body instanceof TheVile vile && vile.isAlive()) {
					switchCore(vile, level);
				}
			}
		}
		// Sticky Fingers, second line: once split, every living body deals 1 damage per second.
		if (main == null && elapsed % 20 == 0 && !player.isCreative() && !player.isSpectator() && player.isAlive()) {
			stick(player, bodies.get(0), level, bodies.size());
		}
		tickYummy(player, main, fight, level);
		tickWatch(player, main, tier, fight, level);
	}

	/** A body died: the main body (and Tier IV-V's first splits) divide; the last body completes the quest. */
	public static void onBodyDeath(LivingEntity entity) {
		BossController.forgetStuck(entity.getUUID());
		java.util.UUID ownerId = BossController.ownerOf(entity);
		if (ownerId == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			return;
		}
		HuntData data = QuestManager.data(owner);
		Quest quest = data.quest;
		if (quest == null || quest.phase != HuntData.PHASE_BOSS || !HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
			return;
		}
		Tier tier = HuntDefs.hunt(quest.hunt).tier(quest.tier);
		ClusterSpec spec = HuntDefs.clusterSpec(tier);
		Fight fight = fight(owner);
		int generation = generation(entity);
		if (generation == 0) {
			release(owner, fight);
			fight.watchActive = false;
		}
		int spawned = 0;
		if (generation >= 0 && generation < spec.generations()) {
			int count = spec.splits()[generation];
			RandomSource random = owner.getRandom();
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count + random.nextDouble() * 0.5;
				Vec3 pos = entity.position().add(Math.cos(angle) * 0.6, 0.3, Math.sin(angle) * 0.6);
				Mob child = createBody(level, owner, tier, generation + 1, pos, random.nextFloat() * 360.0F);
				if (child == null) {
					continue;
				}
				if (child instanceof TheVile vile && entity instanceof TheVile parent) {
					vile.setMagmaCore(parent.isMagmaCore());
				}
				child.setDeltaMovement(Math.cos(angle) * 0.35, 0.45, Math.sin(angle) * 0.35);
				level.addFreshEntity(child);
				spawned++;
			}
			level.playSound(null, entity.blockPosition(), entity instanceof MagmaCube ? SoundEvents.MAGMA_CUBE_SQUISH
					: SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6F, 0.6F);
			level.sendParticles(entity instanceof TheVile ? ParticleTypes.SOUL : ParticleTypes.ITEM_SLIME, entity.getX(),
					entity.getY() + 0.5, entity.getZ(), 40, 0.6, 0.4, 0.6, 0.1);
		}
		if (spawned > 0) {
			return;
		}
		boolean othersAlive = false;
		for (Mob body : bodies(owner)) {
			if (body != entity) {
				othersAlive = true;
				break;
			}
		}
		if (!othersAlive) {
			Set<UUID> helpers = new HashSet<>(fight.helpers);
			forget(owner);
			QuestManager.complete(owner, data, helpers);
		}
	}

	public static void onDamaged(LivingEntity body, DamageSource source) {
		java.util.UUID ownerId = BossController.ownerOf(body);
		if (ownerId == null || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		FIGHTS.computeIfAbsent(ownerId, id -> new Fight()).helpers.add(player.getUUID());
	}

	/** Health per second: none while Watch out! is on, half while Yummy! is on, the tier's regeneration otherwise. */
	private static float regen(Tier tier, Fight fight) {
		if (fight.watchActive) {
			return 0.0F;
		}
		return fight.yummyActive ? tier.regen() / 2.0F : tier.regen();
	}

	/** Brings the next Thermodynamic switch forward to the next tick (for the tests). */
	public static void switchCoresSoon(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		if (fight != null) {
			fight.coreTimer = 1;
		}
	}

	/** The boss's regeneration right now (for the tests), or -1 when there's no fight. */
	public static float regenNow(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		HuntData data = QuestManager.data(player);
		if (fight == null || data.quest == null) {
			return -1.0F;
		}
		return regen(HuntDefs.hunt(data.quest.hunt).tier(data.quest.tier), fight);
	}

	/** Removes every body, the boss bar and any Yummy! hold. */
	public static void discardAll(ServerPlayer owner) {
		String ownerTag = BossController.OWNER_PREFIX + owner.getUUID();
		for (Mob mob : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(256.0),
				mob -> isBody(mob) && mob.entityTags().contains(ownerTag))) {
			BossController.forgetStuck(mob.getUUID());
			mob.discard();
		}
		forget(owner);
	}

	public static void forget(ServerPlayer owner) {
		Fight fight = FIGHTS.remove(owner.getUUID());
		if (fight != null) {
			release(owner, fight);
		}
		ServerBossEvent bar = BARS.remove(owner.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	// ---------------------------------------------------------------- boss bar

	/** Name, the whole cluster's health, slimes left and time; shown to the owner and helpers nearby. */
	private static void updateBar(ServerPlayer owner, Hunt hunt, Tier tier, List<Mob> bodies, long elapsed, Fight fight) {
		ServerBossEvent bar = BARS.computeIfAbsent(owner.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
				Messages.c(tier.bossName(), tier.color()), hunt.barColor(), BossEvent.BossBarOverlay.PROGRESS));
		if (elapsed % 5 != 0 && !bar.getPlayers().isEmpty()) {
			return;
		}
		ClusterSpec spec = HuntDefs.clusterSpec(tier);
		double health = 0;
		for (Mob body : bodies) {
			health += body.getHealth() + spec.futureHealth(Math.max(0, generation(body)));
		}
		int total = spec.totalHealth();
		int seconds = (int) Math.max(0, (QuestManager.BOSS_TIME_LIMIT - elapsed) / 20);
		MutableComponent name = Messages.c(tier.bossName(), tier.color())
				.append(Messages.c("  ❤ " + (int) Math.ceil(health) + "/" + total, HuntDefs.RED))
				.append(Messages.c("  Slimes left: " + bodies.size(), hunt.color()))
				.append(Messages.c(String.format("  %d:%02d", seconds / 60, seconds % 60), HuntDefs.GRAY));
		bar.setName(name);
		bar.setProgress((float) Math.max(0.0, Math.min(1.0, health / total)));
		Set<UUID> viewers = new HashSet<>(fight.helpers);
		viewers.add(owner.getUUID());
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!viewers.contains(player.getUUID()) || player.level() != owner.level() || player.distanceTo(owner) > 96) {
				bar.removePlayer(player);
			}
		}
		for (UUID id : viewers) {
			ServerPlayer player = owner.level().getServer().getPlayerList().getPlayer(id);
			if (player != null && player.level() == owner.level() && player.distanceTo(owner) <= 96
					&& !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	// ---------------------------------------------------------------- Sticky Fingers

	/** Called by every Cluster boss body when it jumps: the main body's jumps hurt the owner, wherever they are. */
	public static void onJump(Mob body) {
		if (body.level().isClientSide() || generation(body) != 0) {
			return;
		}
		java.util.UUID ownerId = BossController.ownerOf(body);
		ServerLevel level = (ServerLevel) body.level();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || owner.isCreative() || owner.isSpectator() || owner.level() != level) {
			return;
		}
		Quest quest = QuestManager.data(owner).quest;
		if (quest == null || quest.phase != HuntData.PHASE_BOSS || !HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
			return;
		}
		Fight fight = FIGHTS.get(owner.getUUID());
		if (fight != null && fight.yummyActive) {
			return;
		}
		stick(owner, body, level, quest.tier <= 3 ? JUMP_DAMAGE_LOW : JUMP_DAMAGE_HIGH);
	}

	/** Sticky Fingers damage; Slippery (a full set of Cluster Hunt Armor) limits each hit to 1. */
	private static void stick(ServerPlayer owner, Mob body, ServerLevel level, float amount) {
		if (com.huntlike.gear.GearEffects.slippery(owner)) {
			amount = Math.min(amount, 1.0F);
		}
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STICKY_FINGERS),
				body);
		owner.hurtServer(level, source, amount);
	}

	/** A consumed player isn't also hit by touching the boss. */
	public static boolean canTouch(Mob body, net.minecraft.world.entity.player.Player player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight == null || !fight.yummyActive;
	}

	// ---------------------------------------------------------------- Baller

	private static Item ballItem(Mob body) {
		if (body instanceof TheVile vile) {
			return vile.isMagmaCore() ? Items.MAGMA_CREAM : Items.SNOWBALL;
		}
		return body instanceof MagmaCube ? Items.MAGMA_CREAM : Items.SLIME_BALL;
	}

	private static void baller(Mob main, ServerPlayer owner, ServerLevel level) {
		ClusterBall ball = new ClusterBall(level, main, ballItem(main), BALLER_DAMAGE);
		ball.setPos(main.getX(), main.getY() + main.getBbHeight() * 0.7, main.getZ());
		double dx = owner.getX() - ball.getX();
		double dz = owner.getZ() - ball.getZ();
		double dy = owner.getEyeY() - 0.3 - ball.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		ball.shoot(dx, dy + flat * 0.12, dz, 0.9F, 0.5F);
		level.addFreshEntity(ball);
		ballsThrown++;
		level.playSound(null, main.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	// ---------------------------------------------------------------- Yummy!

	public static boolean isConsumed(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight != null && fight.yummyActive;
	}

	/** The Yummy! keys (0 forward, 1 left, 2 back, 3 right, 4 attack, 5 use, 6 jump) and how many are done. */
	public static int[] yummyKeys(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight == null || !fight.yummyActive ? new int[0] : fight.keys.clone();
	}

	public static int yummyProgress(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight == null ? 0 : fight.progress;
	}

	private static void startYummy(ServerPlayer owner, Mob main, Tier tier, Fight fight) {
		fight.yummyDone = true;
		if (owner.isSpectator() || owner.distanceTo(main) > 24.0) {
			return;
		}
		int count = Math.max(1, (tier.number() - 2) * 5);
		fight.keys = new int[count];
		for (int i = 0; i < count; i++) {
			fight.keys[i] = owner.getRandom().nextInt(7);
		}
		fight.progress = 0;
		fight.digestTimer = 0;
		fight.yummyActive = true;
		main.setNoAi(true);
		hold(owner, main);
		AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(YUMMY_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance jump = owner.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(YUMMY_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		owner.connection.send(new ClientboundSetTitlesAnimationPacket(2, 30, 8));
		owner.connection.send(new ClientboundSetTitleTextPacket(Messages.c("Yummy!", HuntDefs.BROWN)
				.withStyle(style -> style.withBold(true))));
		QuestManager.playSound(owner, SoundEvents.GENERIC_EAT.value(), 0.6F);
		sendYummy(owner, fight);
	}

	private static void hold(ServerPlayer owner, Mob main) {
		owner.connection.teleport(main.getX(), main.getY() + 0.1, main.getZ(), owner.getYRot(), owner.getXRot());
		owner.setDeltaMovement(Vec3.ZERO);
	}

	private static void tickYummy(ServerPlayer owner, Mob main, Fight fight, ServerLevel level) {
		if (!fight.yummyActive) {
			return;
		}
		if (main == null || !main.isAlive() || owner.isDeadOrDying()) {
			release(owner, fight);
			return;
		}
		if (owner.distanceToSqr(main.getX(), main.getY() + 0.1, main.getZ()) > 0.25) {
			hold(owner, main);
		}
		if (++fight.digestTimer >= DIGEST_INTERVAL) {
			fight.digestTimer = 0;
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DIGESTED),
					main);
			owner.hurtServer(level, source, 1.0F);
		}
	}

	/** One key pressed by a consumed player: the right next key advances; wrong keys do nothing. */
	public static void onYummyKey(ServerPlayer owner, int key) {
		Fight fight = FIGHTS.get(owner.getUUID());
		if (fight == null || !fight.yummyActive || fight.progress >= fight.keys.length || fight.keys[fight.progress] != key) {
			return;
		}
		fight.progress++;
		QuestManager.playSound(owner, SoundEvents.SLIME_JUMP, 0.8F + fight.progress * 0.08F);
		if (fight.progress >= fight.keys.length) {
			release(owner, fight);
			owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
					YUMMY_SLOWNESS_TICKS, 0));
			if (com.huntlike.gear.GearEffects.betterThanEver(owner) && !fight.betterThanEver) {
				fight.betterThanEver = true;
				owner.sendSystemMessage(Messages.c("Better Than Ever! Your Temperature is 20% more effective until the boss perishes.",
						HuntDefs.BROWN));
				QuestManager.playSound(owner, SoundEvents.PLAYER_LEVELUP, 1.2F);
			}
			owner.setDeltaMovement(owner.getLookAngle().scale(-0.6).add(0, 0.5, 0));
			owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(owner));
			QuestManager.playSound(owner, SoundEvents.SLIME_SQUISH, 1.4F);
		} else {
			sendYummy(owner, fight);
		}
	}

	private static void release(ServerPlayer owner, Fight fight) {
		if (!fight.yummyActive) {
			return;
		}
		fight.yummyActive = false;
		AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(YUMMY_SPEED);
		}
		AttributeInstance jump = owner.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(YUMMY_JUMP);
		}
		sendYummy(owner, fight);
	}

	private static void sendYummy(ServerPlayer owner, Fight fight) {
		ServerPlayNetworking.send(owner, new HuntPayloads.Yummy(fight.yummyActive, fight.keys, fight.progress));
	}

	// ---------------------------------------------------------------- Watch out!

	private static void startWatch(Fight fight) {
		fight.watchActive = true;
		fight.watchTicks = 0;
	}

	public static boolean isWatching(ServerPlayer owner) {
		Fight fight = FIGHTS.get(owner.getUUID());
		return fight != null && fight.watchActive;
	}

	private static void tickWatch(ServerPlayer owner, Mob main, Tier tier, Fight fight, ServerLevel level) {
		if (!fight.watchActive) {
			return;
		}
		if (main == null || !main.isAlive()) {
			fight.watchActive = false;
			return;
		}
		int inRound = fight.watchTicks % WATCH_ROUND_TICKS;
		if (inRound == 0) {
			teleportNear(owner, main, level);
		} else if (inRound >= WATCH_TELEPORT_TICKS) {
			Vec3 from = main.position().add(0, main.getBbHeight() * 0.5, 0);
			Vec3 to = owner.position().add(0, 1.0, 0);
			if (inRound % 2 == 0) {
				Vec3 step = to.subtract(from);
				for (int i = 1; i <= 12; i++) {
					Vec3 point = from.add(step.scale(i / 12.0));
					level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.08, 0.08, 0.08, 0.01);
				}
			}
			if ((inRound - WATCH_TELEPORT_TICKS + 1) % 20 == 0) {
				if (blocking(owner, main)) {
					level.playSound(null, owner.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
				} else if (!owner.isCreative() && !owner.isSpectator()) {
					owner.hurtServer(level, level.damageSources().mobAttack(main), tier.number());
					owner.setRemainingFireTicks(Math.max(owner.getRemainingFireTicks(), 60));
					BOSS_BURNING.add(owner.getUUID());
				}
			}
		}
		if (++fight.watchTicks >= WATCH_ROUNDS * WATCH_ROUND_TICKS) {
			fight.watchActive = false;
		}
	}

	/** The owner holds up a shield toward the boss. */
	private static boolean blocking(ServerPlayer owner, Mob main) {
		if (!owner.isBlocking()) {
			return false;
		}
		Vec3 look = owner.getViewVector(1.0F);
		Vec3 toBoss = main.position().subtract(owner.position());
		Vec3 flat = new Vec3(toBoss.x, 0, toBoss.z).normalize();
		Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
		return flat.dot(flatLook) > 0.3;
	}

	private static void teleportNear(ServerPlayer owner, Mob main, ServerLevel level) {
		RandomSource random = owner.getRandom();
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 6.0 + random.nextDouble() * 3.0;
			BlockPos near = BlockPos.containing(owner.getX() + Math.cos(angle) * distance, owner.getY(),
					owner.getZ() + Math.sin(angle) * distance);
			BlockPos spot = QuestManager.findGround(level, near);
			if (spot == null) {
				continue;
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, main.getX(), main.getY() + 0.5, main.getZ(), 20, 0.5, 0.5, 0.5, 0.02);
			main.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			level.sendParticles(ParticleTypes.FLAME, main.getX(), main.getY() + 0.5, main.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
			level.playSound(null, main.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.7F);
			return;
		}
		level.playSound(null, main.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.7F);
	}

	// ---------------------------------------------------------------- Thermodynamic

	private static void switchCore(TheVile vile, ServerLevel level) {
		boolean magma = !vile.isMagmaCore();
		vile.setMagmaCore(magma);
		level.sendParticles(magma ? ParticleTypes.FLAME : ParticleTypes.SNOWFLAKE, vile.getX(), vile.getY() + vile.getBbHeight() / 2,
				vile.getZ(), 40, vile.getBbWidth() / 2, vile.getBbHeight() / 2, vile.getBbWidth() / 2, 0.05);
		level.playSound(null, vile.blockPosition(), magma ? SoundEvents.FIRECHARGE_USE : SoundEvents.GLASS_BREAK, SoundSource.HOSTILE,
				1.2F, 0.8F);
	}

	public static List<Mob> mainBodies(ServerPlayer owner) {
		List<Mob> list = new ArrayList<>();
		Mob main = mainBody(bodies(owner));
		if (main != null) {
			list.add(main);
		}
		return list;
	}
}
