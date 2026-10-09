package com.forzacode.a1016_02.atmosphere.mixin;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.mob.TamperState;
import com.forzacode.a1016_02.atmosphere.mob.TamperedMob;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Mob;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MobTamper hooks: an in-memory tamper state per mob, the frozen AI step, facing, and muted ambient sounds. */
@Mixin(Mob.class)
abstract class MobMixin implements TamperedMob {
	@Unique
	private @Nullable TamperState a1016_02$tamperState;

	@Override
	public @Nullable TamperState a1016_02$tamper() {
		return a1016_02$tamperState;
	}

	@Override
	public void a1016_02$setTamper(@Nullable TamperState state) {
		a1016_02$tamperState = state;
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void a1016_02$freeze(CallbackInfo ci) {
		TamperState state = a1016_02$tamperState;
		if (state != null && MobTamperImpl.onAiStep((Mob) (Object) this, state)) {
			ci.cancel();
		}
	}

	@Inject(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"))
	private void a1016_02$face(CallbackInfo ci) {
		TamperState state = a1016_02$tamperState;
		if (state != null) {
			MobTamperImpl.beforeLook((Mob) (Object) this, state);
		}
	}

	@WrapOperation(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;playAmbientSound()V"))
	private void a1016_02$silence(Mob self, Operation<Void> original) {
		if (MobTamperImpl.allowAmbientSound(self, a1016_02$tamperState)) {
			original.call(self);
		}
	}
}
