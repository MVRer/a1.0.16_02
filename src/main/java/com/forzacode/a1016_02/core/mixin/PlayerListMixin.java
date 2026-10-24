package com.forzacode.a1016_02.core.mixin;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every disconnect ends in {@code PlayerList.remove}, on the server thread, with the player still listed (R12-4): a
 * leave Fabric saw on the network thread is handled here at the latest, so {@code HerobrineEvents.PARTICIPATION} false
 * always fires on the server thread while the leaving player is still online ({@code PlayerWatch.onRemoving}).
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void a1016_02$removing(ServerPlayer player, CallbackInfo ci) {
		Services.watch().onRemoving(player);
	}
}
