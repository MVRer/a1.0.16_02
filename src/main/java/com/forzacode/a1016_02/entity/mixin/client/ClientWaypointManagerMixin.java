package com.forzacode.a1016_02.entity.mixin.client;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.forzacode.a1016_02.entity.client.NamelessClient;
import com.mojang.datafixers.util.Either;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.world.waypoints.TrackedWaypoint;

/**
 * The self-created layer's 02 on vanilla's locator bar: while {@link NamelessClient} hides a friend's name, their dot is
 * not drawn ({@code forEachWaypoint}, which only {@code LocatorBar} walks), and when theirs is the only waypoint the bar
 * reads as having none ({@code hasWaypoints}, which only the hud asks), so the experience bar shows as it does in vanilla
 * for a player who is not sent. Nothing is untracked or changed: the dot is back the moment the name is.
 */
@Mixin(ClientWaypointManager.class)
public abstract class ClientWaypointManagerMixin {
	@Shadow
	@Final
	private Map<Either<UUID, String>, TrackedWaypoint> waypoints;

	@ModifyVariable(method = "forEachWaypoint", at = @At("HEAD"), argsOnly = true)
	private Consumer<TrackedWaypoint> a1016_02$nameless(Consumer<TrackedWaypoint> draw) {
		return NamelessClient.locator(draw);
	}

	@Inject(method = "hasWaypoints", at = @At("RETURN"), cancellable = true)
	private void a1016_02$namelessOnly(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && NamelessClient.allOffLocator(waypoints.keySet())) {
			cir.setReturnValue(false);
		}
	}
}
