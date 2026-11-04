package com.huntlike.potion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.huntlike.hunt.QuestManager;

/**
 * Blazing Soul (Potion of Blazing): Fire Resistance all the time; Strength I for a player who has completed step 6 of
 * the Path of the Wildfire; Speed I in the Nether once step 10 is completed. The three are given as hidden, short
 * effects refreshed every second, so only Blazing Soul shows.
 */
public class BlazingSoulEffect extends MobEffect {
	/** Path of the Wildfire steps completed for Strength I, and for Speed I in the Nether. */
	public static final int STRENGTH_STEPS = 6;
	public static final int SPEED_STEPS = 10;
	private static final int REFRESH = 20;
	private static final int HIDDEN_TICKS = REFRESH + 20;

	public BlazingSoulEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xFF7A1F);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % REFRESH == 0 || duration < REFRESH;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		hidden(entity, MobEffects.FIRE_RESISTANCE);
		if (entity instanceof ServerPlayer player) {
			int steps = QuestManager.data(player).wildfireGoals;
			if (steps >= STRENGTH_STEPS) {
				hidden(entity, MobEffects.STRENGTH);
			}
			if (steps >= SPEED_STEPS && level.dimension() == Level.NETHER) {
				hidden(entity, MobEffects.SPEED);
			}
		}
		return true;
	}

	private static void hidden(LivingEntity entity, net.minecraft.core.Holder<MobEffect> effect) {
		entity.addEffect(new MobEffectInstance(effect, HIDDEN_TICKS, 0, true, false, false));
	}
}
