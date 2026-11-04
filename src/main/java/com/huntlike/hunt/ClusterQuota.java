package com.huntlike.hunt;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;

/**
 * Cluster Hunt quota: a whole cluster (a slime or magma cube and everything it splits into) counts once, when its
 * last piece dies. A slime cluster is worth 1 point, a magma cube cluster 2.
 */
public final class ClusterQuota {
	private ClusterQuota() {
	}

	public static final String CLUSTER_PREFIX = "huntlike_cluster:";

	public static boolean isClusterMob(Entity entity) {
		return entity.getType() == EntityTypes.SLIME || entity.getType() == EntityTypes.MAGMA_CUBE;
	}

	public static int points(Entity entity) {
		return entity.getType() == EntityTypes.MAGMA_CUBE ? 2 : 1;
	}

	/** Every new vanilla slime or magma cube starts its own cluster; its splits inherit the tag. */
	public static void onLoad(Entity entity) {
		if (isClusterMob(entity) && clusterId(entity) == null) {
			entity.addTag(CLUSTER_PREFIX + entity.getUUID());
		}
	}

	public static String clusterId(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(CLUSTER_PREFIX)) {
				return tag;
			}
		}
		return null;
	}

	/** Copies the cluster, Spirit Lure and owner tags from a splitting cube to each new piece. */
	public static void copyTags(Entity parent, Entity child) {
		for (String tag : List.copyOf(child.entityTags())) {
			if (tag.startsWith(CLUSTER_PREFIX)) {
				child.removeTag(tag);
			}
		}
		for (String tag : parent.entityTags()) {
			if (tag.startsWith(CLUSTER_PREFIX) || tag.equals(SpiritLure.LURED_TAG) || tag.startsWith(BossController.OWNER_PREFIX)) {
				child.addTag(tag);
			}
		}
	}

	/** True when this dying piece is the last of its cluster: it can't split and no other piece is left. */
	public static boolean clusterFinished(LivingEntity dying) {
		if (dying instanceof AbstractCubeMob cube && cube.getSize() > 1) {
			return false;
		}
		String id = clusterId(dying);
		if (id == null) {
			return true;
		}
		// Pieces killed together (a sweep) die one after another: only the last one, when every other piece is dead
		// and none of them will still split, completes the cluster.
		return dying.level().getEntitiesOfClass(LivingEntity.class, dying.getBoundingBox().inflate(96.0),
				other -> other != dying && !other.isRemoved() && other.entityTags().contains(id)
						&& (other.isAlive() || other instanceof AbstractCubeMob cube && cube.getSize() > 1)).isEmpty();
	}

	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(entity.level() instanceof ServerLevel)) {
			return;
		}
		HuntData data = QuestManager.data(killer);
		if (SpiritLure.isLuredBy(entity, killer)) {
			QuestManager.countLuredKill(killer, data);
		}
		HuntData.Quest quest = HuntingPacks.questFor(killer, data);
		if (quest == null || quest.phase != HuntData.PHASE_KILLING
				|| !(HuntDefs.isCluster(HuntDefs.hunt(quest.hunt)) || com.huntlike.interlude.InterludeDefs.isInterlude(quest.hunt))
				|| QuestManager.isPeaceful(killer) || !clusterFinished(entity) || com.huntlike.rogue.RogueHunters.active(killer)) {
			return;
		}
		QuestManager.addQuotaPoints(killer, data, quest, points(entity) * com.huntlike.gear.ModScythes.quotaMultiplier(killer),
				entity.blockPosition());
	}
}
