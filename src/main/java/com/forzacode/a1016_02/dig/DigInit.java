package com.forzacode.a1016_02.dig;

import com.forzacode.a1016_02.core.Director;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Common entrypoint of the dig workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class DigInit {
	private DigInit() {
	}

	public static void init() {
		DigConfig.get();
		NetworkChest.init();
		Director.register(new TorchCards.TorchesGone());
		Director.register(new TorchCards.TorchesBehindYou());
		Director.register(new SoundCards.MiningThatMoves());
		Director.register(new ScarCards.TunnelThatGrows());
		Director.register(new ScarCards.TunnelIntoMine());
		Director.register(new ScarCards.TreesStripped());
		Director.register(new SoundCards.UnderYouSound());
		Director.register(new SoundCards.UnderYouFootstep());
		Director.register(new UnderYouStackCard());
		DigCommands.register();
		ServerTickEvents.END_SERVER_TICK.register(DigTicker.INSTANCE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DigTicker.INSTANCE.clear());
	}
}
