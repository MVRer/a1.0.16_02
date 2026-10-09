package com.forzacode.a1016_02.lore.mixin;

import java.util.List;

import com.forzacode.a1016_02.lore.Telling;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** The telling watcher sees every book a player saves or signs, after vanilla changed the item in their slot. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "updateBookContents", at = @At("TAIL"))
	private void a1016$bookSaved(List<FilteredText> contents, int slot, CallbackInfo ci) {
		Telling.onBookWritten(player, slot, false);
	}

	@Inject(method = "signBook", at = @At("TAIL"))
	private void a1016$bookSigned(FilteredText title, List<FilteredText> contents, int slot, CallbackInfo ci) {
		Telling.onBookWritten(player, slot, true);
	}
}
