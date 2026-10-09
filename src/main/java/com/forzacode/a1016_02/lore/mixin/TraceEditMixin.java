package com.forzacode.a1016_02.lore.mixin;

import java.util.Map;

import com.forzacode.a1016_02.lore.UnbreakableSigns;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Not even he can remove F30's signs: a trace edit that would change one (directly, or as a dependent of a block
 * it takes, like the log a sign hangs on) is refused as a whole. Requested as a core contract; until then lore
 * hooks core's planner here.
 */
@Mixin(targets = "com.forzacode.a1016_02.core.TraceEdit")
abstract class TraceEditMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	@Final
	private Map<BlockPos, BlockState> changes;

	@Inject(method = "expand", at = @At("RETURN"), cancellable = true)
	private void a1016$refuseProtectedSigns(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && changes.keySet().stream().anyMatch(pos -> UnbreakableSigns.isProtected(level, pos))) {
			cir.setReturnValue(false);
		}
	}
}
