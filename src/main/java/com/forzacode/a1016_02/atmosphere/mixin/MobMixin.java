package com.forzacode.a1016_02.atmosphere.mixin;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.mob.TamperState;
import com.forzacode.a1016_02.atmosphere.mob.TamperedMob;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MobTamper hooks: an in-memory tamper state per mob, the frozen AI step, the steered AI step (D-096: navigation, move,
 * look and jump controls only, no goal or target selector), a hunt's hold on its target, facing, and muted ambient
 * sounds.
 */
@Mixin(Mob.class)
abstract class MobMixin implements TamperedMob {
	@Shadow
	@Final
	protected GoalSelector goalSelector;
	@Shadow
	@Final
	protected GoalSelector targetSelector;
	@Shadow
	protected PathNavigation navigation;
	@Shadow
	protected MoveControl moveControl;
	@Shadow
	protected LookControl lookControl;
	@Shadow
	protected JumpControl jumpControl;

	@Unique
	private @Nullable TamperState a1016_02$tamperState;
	/** Read from its saved data: it was saved while set on a player (R12-28); calmed as it loads. */
	@Unique
	private boolean a1016_02$huntSaved;

	@Override
	public @Nullable TamperState a1016_02$tamper() {
		return a1016_02$tamperState;
	}

	@Override
	public boolean a1016_02$takeHuntSaved() {
		boolean saved = a1016_02$huntSaved;
		a1016_02$huntSaved = false;
		return saved;
	}

	/** A save while set on a player marks it, so a reload after a crash calms what vanilla saved of the hunt (R12-28). */
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void a1016_02$markHunt(ValueOutput output, CallbackInfo ci) {
		if (MobTamperImpl.savedHunting((Mob) (Object) this, a1016_02$tamperState)) {
			output.putBoolean(MobTamperImpl.HUNT_SAVED_KEY, true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void a1016_02$readHunt(ValueInput input, CallbackInfo ci) {
		a1016_02$huntSaved = input.getBooleanOr(MobTamperImpl.HUNT_SAVED_KEY, false);
	}

	@Override
	public void a1016_02$setTamper(@Nullable TamperState state) {
		a1016_02$tamperState = state;
	}

	@Override
	public void a1016_02$stopGoals() {
		for (WrappedGoal goal : targetSelector.getAvailableGoals()) {
			if (goal.isRunning()) {
				goal.stop();
			}
		}
		for (WrappedGoal goal : goalSelector.getAvailableGoals()) {
			if (goal.isRunning()) {
				goal.stop();
			}
		}
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void a1016_02$freeze(CallbackInfo ci) {
		TamperState state = a1016_02$tamperState;
		if (state == null) {
			return;
		}
		Mob self = (Mob) (Object) this;
		if (MobTamperImpl.onAiStep(self, state)) {
			ci.cancel();
		} else if (MobTamperImpl.onSteeredStep(self, state)) {
			// Steered: the mob's own way of walking, without its goals and target selector.
			navigation.tick();
			moveControl.tick();
			MobTamperImpl.beforeLook(self, state);
			lookControl.tick();
			jumpControl.tick();
			ci.cancel();
		} else {
			MobTamperImpl.beforeTargeting(self, state);
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
