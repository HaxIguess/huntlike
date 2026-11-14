package com.huntlike.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Femurang: flies straight (no drop) up to 20 blocks, and turns back as soon as it hits a mob or a block, or
 * reaches that distance. On the way back it passes through everything and is caught when it reaches its thrower.
 * It deals 6 damage, plus its Sharpness; it presses buttons and hits targets, bells and end crystals like an arrow.
 */
public class FemurangEntity extends ThrowableItemProjectile {
	public static final float DAMAGE = 6.0F;
	public static final double RANGE = 20.0;
	public static final float SPEED = 1.5F;
	private static final double RETURN_SPEED = 1.4;
	/** Lost (too far, another dimension, owner gone) after this long. */
	private static final int MAX_TICKS = 20 * 15;

	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(FemurangEntity.class,
			EntityDataSerializers.BOOLEAN);

	private Vec3 start;

	public FemurangEntity(EntityType<? extends FemurangEntity> type, Level level) {
		super(type, level);
	}

	public FemurangEntity(ServerLevel level, LivingEntity owner, ItemStack stack) {
		super(ModTools.FEMURANG_ENTITY, owner, level, stack.copyWithCount(1));
		this.start = owner.getEyePosition();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(RETURNING, false);
	}

	@Override
	protected Item getDefaultItem() {
		return ModTools.FEMURANG;
	}

	public boolean isReturning() {
		return this.entityData.get(RETURNING);
	}

	private void startReturning() {
		if (!isReturning()) {
			this.entityData.set(RETURNING, true);
			this.noPhysics = true;
		}
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected float getAirDrag() {
		return 1.0F;
	}

	@Override
	public void tick() {
		if (!isReturning()) {
			super.tick();
			if (!this.level().isClientSide()) {
				if (start == null) {
					start = this.position();
				}
				if (this.position().distanceTo(start) >= RANGE || this.tickCount > MAX_TICKS) {
					startReturning();
				}
			}
			return;
		}
		// Coming back: straight to the thrower's chest, through blocks and mobs.
		this.noPhysics = true;
		this.setOldPosAndRot();
		Entity owner = this.getOwner();
		if (!this.level().isClientSide()) {
			if (!(owner instanceof LivingEntity living) || !living.isAlive() || living.level() != this.level()
					|| this.tickCount > MAX_TICKS * 2) {
				// Lost: its thrower may throw again.
				if (owner instanceof ServerPlayer player) {
					FemurangItem.returned(player);
				}
				this.discard();
				return;
			}
			Vec3 target = living.position().add(0, living.getBbHeight() * 0.6, 0);
			Vec3 to = target.subtract(this.position());
			if (to.length() < 1.2) {
				caught(living);
				return;
			}
			this.setDeltaMovement(to.normalize().scale(Math.min(RETURN_SPEED, to.length())));
		}
		this.setPos(this.position().add(this.getDeltaMovement()));
	}

	private void caught(LivingEntity owner) {
		this.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F);
		if (owner instanceof ServerPlayer player) {
			FemurangItem.returned(player);
		}
		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !isReturning() && entity != this.getOwner() && super.canHitEntity(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (this.level() instanceof ServerLevel level) {
			Entity target = result.getEntity();
			DamageSource source = this.damageSources().thrown(this, this.getOwner());
			float damage = EnchantmentHelper.modifyDamage(level, this.getItem(), target, source, DAMAGE);
			target.hurtServer(level, source, damage);
			level.playSound(null, target.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (this.level() instanceof ServerLevel level) {
			BlockPos pos = result.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ButtonBlock button && !state.getValue(ButtonBlock.POWERED)) {
				button.press(state, level, pos, this.getOwner() instanceof net.minecraft.world.entity.player.Player p ? p : null);
			}
			level.playSound(null, pos, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (isReturning()) {
			return;
		}
		super.onHit(result);
		if (!this.level().isClientSide()) {
			startReturning();
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
