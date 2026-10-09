package com.forzacode.a1016_02.lore.mixin;

import java.util.List;

import com.forzacode.a1016_02.lore.Telling;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

/** The telling watcher sees every sign a player finishes writing (server side, after the text changed). */
@Mixin(SignBlockEntity.class)
abstract class SignBlockEntityMixin {
	@Inject(method = "updateSignText", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;setAllowedPlayerEditor(Ljava/util/UUID;)V"))
	private void a1016$signWritten(Player player, SignTextSlot slot, List<FilteredText> lines, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			Telling.onSignWritten(serverPlayer, (SignBlockEntity) (Object) this);
		}
	}
}
