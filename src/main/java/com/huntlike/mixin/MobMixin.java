package com.huntlike.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import com.huntlike.path.WildfirePath;

/** Nether Veil: ghasts don't pick a veiled player as their target unless that player hit them first. */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void huntlike$netherVeil(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && WildfirePath.veiledFrom(self, target)) {
			ci.cancel();
		}
	}
}
