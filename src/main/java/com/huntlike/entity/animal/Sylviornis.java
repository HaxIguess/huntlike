package com.huntlike.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.animal.chicken.Chicken;

import com.huntlike.hunt.BossController;

/** Tier I boss, based on the chicken. Its look (the user's model) is SylviornisRenderer. */
public class Sylviornis extends Chicken {
	/** Client side: the game time its latest melee attack started (for the attack animation), or -1. */
	public long attackStart = -1L;

	public Sylviornis(EntityType<? extends Chicken> type, Level level) {
		super(type, level);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BossController.ATTACK_EVENT) {
			attackStart = level().getGameTime();
		} else {
			super.handleEntityEvent(id);
		}
	}
}
