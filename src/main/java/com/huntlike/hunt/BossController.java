package com.huntlike.hunt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import java.util.List;

import com.huntlike.entity.BossEgg;
import com.huntlike.hunt.HuntData.Quest;
import com.huntlike.hunt.HuntDefs.Hunt;
import com.huntlike.hunt.HuntDefs.Tier;
import com.huntlike.mixin.MobAccessor;

/** Spawns Hunt bosses and runs their fight. Bosses are vanilla mobs driven from here. */
public final class BossController {
	private BossController() {
	}

	public static final String BOSS_TAG = "huntlike_boss";
	public static final String OWNER_PREFIX = "huntlike_owner:";

	/** Players who damaged each boss (lootshare helpers). */
	private static final Map<UUID, Set<UUID>> HELPERS = new HashMap<>();
	/** Ticks until each boss can melee again. */
	private static final Map<UUID, Integer> MELEE_COOLDOWN = new HashMap<>();

	/** Boss health bars, by boss. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** For each boss: closest distance to its owner lately, and ticks since it last got closer. */
	private static final Map<UUID, double[]> STUCK = new HashMap<>();
	private static final int STUCK_TICKS = 20 * 5;

	/** Name, health and time left in the Hunt's color; shown to the owner and helpers nearby. */
	private static void updateBar(Mob boss, ServerPlayer owner, Hunt hunt, Tier tier, long elapsed) {
		ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), id -> new ServerBossEvent(id,
				Messages.c(tier.bossName(), tier.color()), hunt.barColor(), BossEvent.BossBarOverlay.NOTCHED_10));
		if (elapsed % 5 != 0 && !bar.getPlayers().isEmpty()) {
			return;
		}
		boolean immune = BossAbilities.immune(boss);
		int seconds = (int) Math.max(0, (QuestManager.BOSS_TIME_LIMIT - elapsed) / 20);
		HuntData.Quest quest = QuestManager.data(owner).quest;
		net.minecraft.network.chat.MutableComponent name = Mutations.barName(tier, quest == null ? "" : quest.mutation)
				.append(Messages.c("  \u2764 " + (int) Math.ceil(boss.getHealth()) + "/" + (int) boss.getMaxHealth(), HuntDefs.RED))
				.append(Messages.c(String.format("  %d:%02d", seconds / 60, seconds % 60), HuntDefs.GRAY));
		if (immune) {
			name.append(Messages.c("  [Immune]", 0xFFFFFF));
		}
		bar.setName(name);
		bar.setProgress(Math.max(0.0F, Math.min(1.0F, boss.getHealth() / boss.getMaxHealth())));
		bar.setColor(immune ? BossEvent.BossBarColor.WHITE : hunt.barColor());
		java.util.Set<UUID> viewers = new HashSet<>();
		viewers.add(owner.getUUID());
		Set<UUID> helpers = HELPERS.get(boss.getUUID());
		if (helpers != null) {
			viewers.addAll(helpers);
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!viewers.contains(player.getUUID()) || player.level() != boss.level() || player.distanceTo(boss) > 64) {
				bar.removePlayer(player);
			}
		}
		for (UUID id : viewers) {
			ServerPlayer player = owner.level().getServer().getPlayerList().getPlayer(id);
			if (player != null && player.level() == boss.level() && player.distanceTo(boss) <= 64
					&& !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	private static void removeBar(UUID boss) {
		ServerBossEvent bar = BARS.remove(boss);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** A boss that hasn't got closer to its owner for 5 seconds teleports next to them. */
	static void forgetStuck(UUID boss) {
		STUCK.remove(boss);
	}

	static void unstick(Mob boss, ServerPlayer owner) {
		double distance = boss.distanceTo(owner);
		double[] track = STUCK.computeIfAbsent(boss.getUUID(), id -> new double[] {distance, 0});
		if (distance < 4.0 || distance < track[0] - 0.5) {
			track[0] = distance;
			track[1] = 0;
			return;
		}
		track[1]++;
		if (track[1] < STUCK_TICKS) {
			return;
		}
		track[0] = distance;
		track[1] = 0;
		ServerLevel level = owner.level();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = owner.getRandom().nextDouble() * Math.PI * 2;
			net.minecraft.core.BlockPos near = net.minecraft.core.BlockPos.containing(owner.getX() + Math.cos(angle) * 2.5,
					owner.getY(), owner.getZ() + Math.sin(angle) * 2.5);
			net.minecraft.core.BlockPos spot = QuestManager.findGround(level, near);
			if (spot != null && spot.distSqr(owner.blockPosition()) <= 25) {
				level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 0.5, boss.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
				boss.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				boss.getNavigation().stop();
				level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 0.5, boss.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
				level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE,
						1.0F, 0.8F);
				return;
			}
		}
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity.entityTags().contains(BOSS_TAG);
	}

	public static UUID ownerOf(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(OWNER_PREFIX)) {
				try {
					return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
		}
		return null;
	}

	public static Mob findBoss(ServerPlayer player, Quest quest) {
		if (quest.bossUuid.isEmpty()) {
			return null;
		}
		try {
			Entity entity = player.level().getEntity(UUID.fromString(quest.bossUuid));
			return entity instanceof Mob mob ? mob : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static void discardBoss(ServerPlayer player, Quest quest) {
		if (com.huntlike.interlude.InterludeDefs.isInterlude(quest.hunt)) {
			com.huntlike.interlude.SlimeRiderFight.discard(player);
			return;
		}
		if (HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
			ClusterFight.discardAll(player);
			return;
		}
		Mob boss = findBoss(player, quest);
		if (boss != null) {
			HELPERS.remove(boss.getUUID());
			MELEE_COOLDOWN.remove(boss.getUUID());
			BossAbilities.discardHerd(boss);
			BossAbilities.forget(boss.getUUID());
			com.huntlike.gear.GearEffects.forgetBoss(boss.getUUID());
			removeBar(boss.getUUID());
			STUCK.remove(boss.getUUID());
			boss.discard();
		}
	}

	// ---------------------------------------------------------------- summoning

	public static void tickSummon(ServerPlayer player, HuntData data, Quest quest) {
		if (QuestManager.isPeaceful(player)) {
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		ServerLevel level = player.level();
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		double cx = quest.summonX + 0.5;
		double cy = quest.summonY + 0.1;
		double cz = quest.summonZ + 0.5;
		double progress = Math.max(0.0, 1.0 - quest.summonTicks / (double) QuestManager.SUMMON_TICKS);
		double radius = 1.6 * (1.0 - progress) + 0.2;
		double angle = quest.summonTicks * 0.45;
		for (int arm = 0; arm < 4; arm++) {
			double a = angle + arm * Math.PI / 2.0;
			double y = cy + progress * 1.5 + arm * 0.1;
			level.sendParticles(new DustParticleOptions(hunt.color(), 1.0F), cx + Math.cos(a) * radius, y,
					cz + Math.sin(a) * radius, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.WITCH, cx + Math.cos(a + 0.3) * radius, y, cz + Math.sin(a + 0.3) * radius,
					1, 0, 0, 0, 0);
		}
		quest.summonTicks--;
		if (quest.summonTicks <= 0) {
			spawnBoss(player, data, quest);
		}
	}

	private static void spawnBoss(ServerPlayer player, HuntData data, Quest quest) {
		if (com.huntlike.interlude.InterludeDefs.isInterlude(quest.hunt)) {
			com.huntlike.interlude.SlimeRiderFight.spawn(player, data, quest);
			return;
		}
		if (HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
			ClusterFight.spawn(player, data, quest);
			return;
		}
		ServerLevel level = player.level();
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		Mob boss = tier.mob().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (boss == null) {
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		boss.snapTo(quest.summonX + 0.5, quest.summonY, quest.summonZ + 0.5, player.getYRot() + 180.0F, 0.0F);
		boss.setBaby(false);
		// A Hunting Pack's boss: health times the members when the quest started.
		setBase(boss, Attributes.MAX_HEALTH, tier.health() * quest.packSize);
		// Bigger for a pack (easier to hit in a crowd): +10% per extra member, at most +50%.
		setBase(boss, Attributes.SCALE, tier.scale() * packScale(quest));
		setBase(boss, Attributes.MOVEMENT_SPEED, 0.3);
		setBase(boss, Attributes.FOLLOW_RANGE, 64.0);
		setBase(boss, Attributes.STEP_HEIGHT, 1.0);
		boss.setHealth(tier.health() * quest.packSize);
		MobAccessor accessor = (MobAccessor) boss;
		accessor.huntlike$getGoalSelector().removeAllGoals(goal -> !(goal instanceof FloatGoal));
		accessor.huntlike$getTargetSelector().removeAllGoals(goal -> true);
		boss.setPersistenceRequired();
		boss.addTag(BOSS_TAG);
		boss.addTag(OWNER_PREFIX + player.getUUID());

		quest.bossUuid = boss.getUUID().toString();
		quest.bossSpawnTime = level.getGameTime();
		quest.phase = HuntData.PHASE_BOSS;
		quest.mutation = quest.forcedMutation == null ? Mutations.roll(level.getRandom())
				: "none".equals(quest.forcedMutation) ? "" : quest.forcedMutation;
		quest.forcedMutation = null;
		player.setAttached(QuestManager.DATA, data);
		level.addFreshEntity(boss);
		ServerBossEvent bar = new ServerBossEvent(boss.getUUID(), Messages.c(tier.bossName(), tier.color()), hunt.barColor(),
				BossEvent.BossBarOverlay.NOTCHED_10);
		BARS.put(boss.getUUID(), bar);
		Mutations.start(boss, quest);
		Mutations.Mutation mutation = Mutations.Mutation.byTitle(quest.mutation);

		level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.5, boss.getZ(), 1, 0, 0, 0, 0);
		player.sendSystemMessage(Messages.bossSpawned(hunt, tier.number()));
		if (mutation != null) {
			for (ServerPlayer member : HuntingPacks.membersOnline(player, quest)) {
				member.sendSystemMessage(Mutations.announcement(hunt, tier, mutation));
				QuestManager.playSound(member, SoundEvents.ELDER_GUARDIAN_CURSE, 1.4F);
			}
		}
		QuestManager.playSound(player, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F);
		QuestManager.playSound(player, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, 0.8F);
		QuestManager.sync(player);
	}

	/** A pack's boss is 10% bigger per extra member, at most 50%. */
	public static double packScale(HuntData.Quest quest) {
		return Math.min(1.5, 1.0 + 0.1 * (quest.packSize - 1));
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ---------------------------------------------------------------- the fight

	public static void tickBoss(ServerPlayer player, HuntData data, Quest quest) {
		if (com.huntlike.interlude.InterludeDefs.isInterlude(quest.hunt)) {
			com.huntlike.interlude.SlimeRiderFight.tickQuest(player, data, quest);
			return;
		}
		if (HuntDefs.isCluster(HuntDefs.hunt(quest.hunt))) {
			ClusterFight.tick(player, data, quest);
			return;
		}
		Mob boss = findBoss(player, quest);
		if (boss == null || !boss.isAlive()) {
			if (boss == null || boss.isRemoved()) {
				// The boss vanished without being killed: one kill short of calling it again.
				QuestManager.resetToQuotaMinusOne(player, data);
			}
			return;
		}
		if (QuestManager.isPeaceful(player)) {
			QuestManager.resetToQuotaMinusOne(player, data);
			return;
		}
		ServerLevel level = player.level();
		long elapsed = level.getGameTime() - quest.bossSpawnTime;
		if (elapsed > QuestManager.BOSS_TIME_LIMIT || HuntingPacks.nearestMember(player, quest, boss) > QuestManager.FLEE_DISTANCE) {
			QuestManager.fail(player, data);
			return;
		}
		Hunt hunt = HuntDefs.hunt(quest.hunt);
		Tier tier = hunt.tier(quest.tier);
		updateBar(boss, player, hunt, tier, elapsed);
		BossAbilities.tickTimers(boss, player, tier);
		boolean frozen = BossAbilities.frozen(boss);

		// Chase the owner, or stand still while an ability winds up.
		boss.getLookControl().setLookAt(player);
		if (frozen) {
			boss.getNavigation().stop();
		} else if (elapsed % 10 == 0) {
			boss.getNavigation().moveTo(player, 1.2);
		}
		if (!frozen) {
			unstick(boss, player);
		}

		// Basic melee.
		int cooldown = MELEE_COOLDOWN.getOrDefault(boss.getUUID(), 0);
		if (cooldown > 0) {
			MELEE_COOLDOWN.put(boss.getUUID(), cooldown - 1);
		} else if (!frozen && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
			double reach = boss.getBbWidth() / 2.0 + 1.6;
			if (boss.distanceTo(player) <= reach) {
				// The swing reaches the players nearby, whose renderers play an attack animation if the boss has one.
				boss.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				player.hurtServer(level, level.damageSources().mobAttack(boss), tier.melee() * BossAbilities.meleeMultiplier(boss));
				MELEE_COOLDOWN.put(boss.getUUID(), 20);
			}
		}

		if (elapsed % 20 == 0) {
			// Regeneration: health equal to the tier every second (6 in Apex Instinct, none while the herd lives).
			if (!Mutations.blocksRegen(boss)) {
				boss.heal(BossAbilities.regen(boss, tier));
			}
			// Egg Blaster: one fast egg every second.
			if (!frozen && boss.hasLineOfSight(player) && !player.isSpectator()) {
				BossEgg egg = new BossEgg(level, boss, tier.eggDamage());
				double dx = player.getX() - egg.getX();
				double dy = player.getEyeY() - 0.4 - egg.getY();
				double dz = player.getZ() - egg.getZ();
				egg.shoot(dx, dy, dz, 2.2F, 0.0F);
				level.addFreshEntity(egg);
				level.playSound(null, boss.blockPosition(), SoundEvents.EGG_THROW, net.minecraft.sounds.SoundSource.HOSTILE,
						1.0F, 1.4F);
			}
		}

		BossAbilities.tickLunge(boss, player, tier, elapsed);
		BossAbilities.tickHerd(boss, player, elapsed);

		// Aura in the tier's color.
		if (elapsed % 4 == 0) {
			double w = boss.getBbWidth() / 2.0;
			double h = boss.getBbHeight();
			level.sendParticles(new DustParticleOptions(tier.color(), 1.2F), boss.getX(), boss.getY() + h / 2.0, boss.getZ(),
					3, w, h / 2.0, w, 0.0);
		}
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!isBoss(entity)) {
			return true;
		}
		if (entity instanceof Mob mob && BossAbilities.immune(mob)) {
			return false;
		}
		boolean allowed = !(source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL));
		if (allowed && !ClusterFight.isBody(entity)) {
			// A mutated boss in a hit phase takes no damage: the hit only counts toward the phase.
			return Mutations.onHit(entity, source);
		}
		return allowed;
	}

	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!isBoss(entity) || !(entity instanceof Mob boss) || !entity.isAlive()) {
			return;
		}
		if (ClusterFight.isBody(entity)) {
			ClusterFight.onDamaged(entity, source);
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			HELPERS.computeIfAbsent(entity.getUUID(), id -> new HashSet<>()).add(player.getUUID());
		}
		UUID ownerId = ownerOf(entity);
		if (ownerId == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || QuestManager.data(owner).quest == null) {
			return;
		}
		HuntData.Quest quest = QuestManager.data(owner).quest;
		Mutations.afterDamage(boss);
		BossAbilities.afterDamage(boss, owner, HuntDefs.hunt(quest.hunt).tier(quest.tier));
	}

	public static void onBossDeath(LivingEntity entity) {
		if (ClusterFight.isBody(entity)) {
			ClusterFight.onBodyDeath(entity);
			return;
		}
		Set<UUID> helpers = HELPERS.remove(entity.getUUID());
		MELEE_COOLDOWN.remove(entity.getUUID());
		if (entity instanceof Mob mob) {
			BossAbilities.discardHerd(mob);
		}
		BossAbilities.forget(entity.getUUID());
		Mutations.forget(entity.getUUID());
		com.huntlike.gear.GearEffects.forgetBoss(entity.getUUID());
		removeBar(entity.getUUID());
		STUCK.remove(entity.getUUID());
		UUID ownerId = ownerOf(entity);
		if (ownerId == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			return;
		}
		HuntData data = QuestManager.data(owner);
		if (data.quest == null || !data.quest.bossUuid.equals(entity.getUUID().toString())) {
			return;
		}
		QuestManager.complete(owner, data, helpers == null ? Set.of() : helpers);
	}
}
