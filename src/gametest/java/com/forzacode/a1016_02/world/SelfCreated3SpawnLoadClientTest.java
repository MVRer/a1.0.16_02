package com.forzacode.a1016_02.world;

import java.util.Locale;

import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.debug.client.gallery.GalleryScript;
import com.forzacode.a1016_02.world.sc2.kit.Decided;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * R3-V (dev only; never part of {@code build}): a new world of the default preset, hardcore, and its first 90 s at
 * the spawn at render distance 18 (simulation 10), with the layer on and then off on the same seed. On a new world the first chunks past the spawn's clear
 * ring site the places round them at once (FIX-R3-E: about 105 candidates, on worldgen's threads): the player must not
 * see the world stall. Per world: the creation's time, the time until every chunk in view is drawn, the worst server
 * tick and the ticks over 50 and 250 ms in the first 90 s, the worst gap between two client ticks, and how many
 * candidates were sited. Does nothing without {@code -Da1016_02.sc3spawn} ({@code true}, or a seed).
 */
public class SelfCreated3SpawnLoadClientTest implements FabricClientGameTest {
	public static final String PROPERTY = "a1016_02.sc3spawn";
	private static final long SEED = 7_215_046_119_033_812_771L;
	private static final int TICKS = 1800;
	/** Mariano's own render and simulation distances (his options.txt). */
	private static final int RENDER = 18;
	private static final int SIMULATION = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		String value = System.getProperty(PROPERTY, "");
		if (value.isEmpty()) {
			return;
		}
		long seed = value.equals("true") ? SEED : Long.parseLong(value);
		boolean was = SelfCreated.config().enabled;
		try {
			Sc2RealNoise.log("spawn load, layer on: " + measure(context, seed, true));
			Sc2RealNoise.log("spawn load, layer off: " + measure(context, seed, false));
		} finally {
			SelfCreated.config().enabled = was;
		}
	}

	private static String measure(ClientGameTestContext context, long seed, boolean layer) {
		SelfCreated.config().enabled = layer;
		context.runOnClient(client -> distances(client));
		int sitedBefore = Decided.size();
		long t0 = System.nanoTime();
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setSeed(Long.toString(seed));
			state.setGenerateStructures(true);
			state.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
		}).create()) {
			long created = System.nanoTime();
			context.runOnClient(client -> distances(client));
			int sitedAtJoin = Decided.size();
			long ready = -1;
			long worstServer = 0;
			int over50 = 0;
			int over250 = 0;
			long worstClientGap = 0;
			long last = System.nanoTime();
			for (int tick = 1; tick <= TICKS; tick++) {
				context.waitTick();
				long now = System.nanoTime();
				worstClientGap = Math.max(worstClientGap, now - last);
				last = now;
				if (ready < 0 && context.computeOnClient(GalleryScript::chunksReady)) {
					ready = now;
				}
				if (tick % 20 == 0) {
					long[] times = world.getServer().computeOnServer(server -> server.getTickTimesNanos().clone());
					// The last 100 ticks: every one counted once (20 new since the last look).
					for (int i = 0; i < times.length; i++) {
						worstServer = Math.max(worstServer, times[i]);
					}
					long[] recent = recent(times, world.getServer().computeOnServer(server -> server.getTickCount()), 20);
					for (long t : recent) {
						over50 += t > 50_000_000L ? 1 : 0;
						over250 += t > 250_000_000L ? 1 : 0;
					}
				}
			}
			int distance = context.computeOnClient(client -> client.options.getEffectiveRenderDistance());
			int sited = Decided.size();
			return String.format(Locale.ROOT,
					"seed %d, render distance %d: created in %.1f s, every chunk in view drawn %s; first %d s: worst server tick %.0f ms, %d over 50 ms, %d over 250 ms; worst client tick gap %.0f ms; candidates kept %d before the world, %d at the join, %d after",
					seed, distance, (created - t0) / 1e9, ready < 0 ? "not within the minute" : String.format(Locale.ROOT, "%.1f s after", (ready - created) / 1e9),
					TICKS / 20, worstServer / 1e6, over50, over250, worstClientGap / 1e6, sitedBefore, sitedAtJoin, sited);
		}
	}

	private static void distances(net.minecraft.client.Minecraft client) {
		client.options.renderDistance().set(RENDER);
		client.options.simulationDistance().set(SIMULATION);
	}

	/** The last {@code n} tick times of the server's ring of 100, by its tick count. */
	private static long[] recent(long[] ring, int tickCount, int n) {
		long[] out = new long[Math.min(n, ring.length)];
		for (int i = 0; i < out.length; i++) {
			out[i] = ring[Math.floorMod(tickCount - 1 - i, ring.length)];
		}
		return out;
	}
}
