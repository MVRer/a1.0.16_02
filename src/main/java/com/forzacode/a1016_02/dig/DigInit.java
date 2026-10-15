package com.forzacode.a1016_02.dig;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Services;

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
		// Someone behind you, the tunnel ladder (D-072).
		Director.register(new LadderCards.StepsBehindOnce());
		Director.register(new LadderCards.StepsBehindComing());
		Director.register(new LadderCards.StepsBehindGlimpse());
		DigCommands.register();
		ServerTickEvents.END_SERVER_TICK.register(DigTicker.INSTANCE::tick);
		// Sessions (D-062.4, D-062.5): a start remembers the player's base; a stop ends what ran for that player only.
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			Services.watch().participant(server, player).ifPresent(Bases.live(server)::remember);
			if (!participating) {
				DigTicker.INSTANCE.onStop(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DigTicker.INSTANCE.clear());
	}
}
