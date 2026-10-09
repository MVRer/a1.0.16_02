package com.forzacode.a1016_02.lore.mixin;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.lore.UnbreakableSigns;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

/** Explosions leave F30's signs alone (no drop, no removal): they are taken out of the exploded blocks. */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> a1016$spareProtectedSigns(List<BlockPos> targets) {
		if (targets.stream().noneMatch(pos -> UnbreakableSigns.isProtected(level, pos))) {
			return targets;
		}
		List<BlockPos> spared = new ArrayList<>(targets);
		spared.removeIf(pos -> UnbreakableSigns.isProtected(level, pos));
		return spared;
	}
}
