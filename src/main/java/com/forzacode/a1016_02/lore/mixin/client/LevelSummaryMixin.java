package com.forzacode.a1016_02.lore.mixin.client;

import com.forzacode.a1016_02.lore.client.WorldListMarks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * The self-created layer's 14 (a) and (b): what the world list shows for a world, and what vanilla's own backup question
 * reads ({@code WorldOpenFlows} asks the summary for {@code backupStatus} and {@code getWorldVersionName}). Each answer is
 * vanilla's unless {@link WorldListMarks} has a beat for that world.
 */
@Mixin(LevelSummary.class)
public abstract class LevelSummaryMixin {
	@Inject(method = "getLastPlayed", at = @At("RETURN"), cancellable = true)
	private void a1016_02$lastPlayed(CallbackInfoReturnable<Long> cir) {
		long real = cir.getReturnValue();
		long shown = WorldListMarks.lastPlayed((LevelSummary) (Object) this, real);
		if (shown != real) {
			cir.setReturnValue(shown);
		}
	}

	@Inject(method = "getWorldVersionName", at = @At("RETURN"), cancellable = true)
	private void a1016_02$versionName(CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent name = WorldListMarks.versionName((LevelSummary) (Object) this);
		if (name != null) {
			cir.setReturnValue(name);
		}
	}

	@Inject(method = "backupStatus", at = @At("RETURN"), cancellable = true)
	private void a1016_02$backupStatus(CallbackInfoReturnable<LevelSummary.BackupStatus> cir) {
		LevelSummary.BackupStatus real = cir.getReturnValue();
		LevelSummary.BackupStatus shown = WorldListMarks.backupStatus((LevelSummary) (Object) this, real);
		if (shown != real) {
			cir.setReturnValue(shown);
		}
	}
}
