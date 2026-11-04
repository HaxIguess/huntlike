package com.huntlike.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;

import com.huntlike.path.WildfirePath;

/** Golden Style: piglins treat the player as wearing gold (brutes never check, so they still attack). */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true)
	private static void huntlike$goldenStyle(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (WildfirePath.goldenStyle(entity)) {
			cir.setReturnValue(true);
		}
	}
}
