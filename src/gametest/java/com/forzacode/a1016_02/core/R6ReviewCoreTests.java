package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.core.MultiplayerCoreTests.Roster;
import com.forzacode.a1016_02.core.MultiplayerCoreTests.TestPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Bug hunt R6 (v1.3): core's blind moments (D-077, D-079) at their seams. Every test brings its own {@link Roster}, so
 * nothing here reads or changes the live server's players.
 */
public class R6ReviewCoreTests {
	/** A director that records each recheck as the player's name and blind ticks then. */
	private static final class Rechecks implements Director {
		private final Director stub = new Director.Stub();
		final List<String> seen = new ArrayList<>();
		PlayerWatch watch;

		@Override
		public void tick(MinecraftServer server) {
		}

		@Override
		public FireResult fire(MinecraftServer server, ServerPlayer player, String cardId, boolean fake) {
			return stub.fire(server, player, cardId, fake);
		}

		@Override
		public List<String> timewarp(MinecraftServer server, int days) {
			return List.of();
		}

		@Override
		public long ticksSinceTag(MinecraftServer server, CardTag tag) {
			return Long.MAX_VALUE;
		}

		@Override
		public boolean inQuiet(MinecraftServer server) {
			return false;
		}

		@Override
		public List<String> debugLines(MinecraftServer server) {
			return List.of();
		}

		@Override
		public void recheck(MinecraftServer server, ServerPlayer player) {
			seen.add(player.getName().getString() + "@" + watch.blindTicks(player));
		}
	}

	private static void tickBlind(PlayerWatch watch, MinecraftServer server, int ticks) {
		for (int i = 0; i < ticks; i++) {
			watch.tickBlind(server);
		}
	}

	/**
	 * FINDING (low): D-079 says a blind moment is reset when the player dies, and the client only says so again for a
	 * new player object ({@code BlindReporter}: a new {@code LocalPlayer} resets what it sent to "not blind"). But the
	 * moment is kept by UUID and the server only notices a death if its END tick ({@code tickBlind}) sees the dead player
	 * object. A death after core's END handler (an RCON or management {@code /kill}, run as a task between ticks, or any
	 * later END handler) with the immediate respawn game rule is answered by the client at once
	 * ({@code ClientPacketListener.handlePlayerCombatKill}: no death screen, {@code LocalPlayer.respawn()}), and 26.3
	 * handles that request at the start of the next tick, before its END: no END tick ever sees the dead object. The
	 * respawned object is alive, a participant, in the same dimension, so the moment goes on, rechecks too. The client
	 * closed its menu inside {@code handleRespawn} ({@code oldPlayer.closeContainer()}) and, for its new player object,
	 * never sends "not blind": the server keeps that player blind until they next open and close a menu (and that next
	 * menu joins the stale moment, its ten rechecks spent), so dig's BLIND cards (blind 1.5 s, alone) fit at every
	 * director tick while the player looks around. Binding the moment to the player object (or ending it when
	 * {@code online(uuid)} is a new object) closes it.
	 */
	@GameTest
	public void aDeathAndRespawnBetweenTwoTicksEndTheBlindMoment(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		Rechecks director = new Rechecks();
		director.watch = r.watch;
		r.director = director;
		PlayerWatch w = r.watch;
		TestPlayer a = new TestPlayer(helper.getLevel(), UUID.randomUUID(), "A", GameType.SURVIVAL);
		a.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)), 0.0F, 0.0F);
		r.join(a);
		r.tick(server, 1);

		w.onBlindState(a, true);
		tickBlind(w, server, 40);
		helper.assertTrue(w.blindTicks(a) == 40 && director.seen.equals(List.of("A@30")), "the menu: " + w.blindTicks(a) + ", " + director.seen);

		// Killed between two server ticks, respawned at once (immediate respawn) before the next one: no tick sees the dead object.
		a.setHealth(0.0F);
		TestPlayer respawned = r.respawn(a, GameType.SURVIVAL);
		tickBlind(w, server, 200);
		helper.assertTrue(w.blindTicks(respawned) == 0, "the respawned player, whose client closed the menu and will not say so, is still blind for "
				+ w.blindTicks(respawned) + " ticks; rechecks " + director.seen);
		helper.succeed();
	}
}
