package com.forzacode.a1016_02.atmosphere;

import java.util.Locale;

import com.forzacode.a1016_02.core.FogLimits;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The dusk fog builds so gradually that the player barely notices it getting foggy until it is (Mariano's playtest:
 * "dusk fog pops up too abrupt"). The client's fog math ({@link Curves#applyDusk}) is sampled every second across whole
 * days and across level changes, starting from vanilla's overworld fog, and no step may move the fog by more than a
 * small share of the render distance. Deep dusk and the night keep the look tuned in v0.4.
 */
public class DuskFogGameTests {
	/** Render distances in chunks: vanilla's minimum, short, default-ish, long. */
	private static final int[] CHUNKS = {2, 4, 6, 12, 16, 32};
	/** Sampling step: one second. */
	private static final int STEP = 20;
	/** The most the fog end or a fog start may move in one step, as a share of the render distance. */
	private static final double MAX_EDGE_STEP = 0.015;
	/**
	 * The most the haze (where the fog reaches a quarter, half, three quarters) may move in one step, as a share of the
	 * render distance. Checked in clear weather from 4 chunks: rain's haze starts 160 blocks behind the camera, and
	 * at 2 chunks the whole view is 32 blocks, so the same gradual change sweeps through the view faster.
	 */
	private static final double MAX_HAZE_STEP = 0.025;
	private static final double[] HAZE_LEVELS = {0.25, 0.5, 0.75};
	/** The most the clouds' fade at the render distance may change in one step (v0.4: from 9% to 100% in one frame). */
	private static final double MAX_CLOUD_STEP = 0.04;

	@GameTest
	public void duskFogNeverJumpsAcrossTheDay(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		FogLimits.Shape shape = cfg.fogShape();
		for (int chunks : CHUNKS) {
			double r = chunks * 16.0;
			for (boolean rain : new boolean[] {false, true}) {
				for (float level : levels(cfg)) {
					DuskLevel drawn = new DuskLevel();
					drawn.set(level, cfg.duskHazeFullLevel, 0, 0);
					Curves.Fog previous = null;
					for (long t = 0; t <= Curves.DAY; t += STEP) {
						Curves.Fog fog = vanilla(r, rain);
						Curves.applyDusk(fog, Curves.duskAmount(drawn.level(0), t, shape), Curves.duskHaze(t, shape) * drawn.haze(0), cfg.duskMinFogBlocks,
								cfg.heavyFogStartFraction);
						String where = String.format(Locale.ROOT, "(%d chunks%s, level %.2f, t %d)", chunks, rain ? ", rain" : "", level, t);
						// Where he may stand: the server's estimate lands exactly where this fog is complete.
						double server = FogLimits.of(chunks, chunks, level, t, shape).fogEnd();
						helper.assertTrue(Math.abs(fog.end() - server) < 1.0E-6, "client fog end " + fog.end() + " != FogLimits " + server + " " + where);
						if (previous != null) {
							assertSteps(helper, previous, fog, r, MAX_EDGE_STEP, chunks >= 4 && !rain ? MAX_HAZE_STEP : Double.NaN, MAX_CLOUD_STEP, where);
						}
						previous = fog;
					}
				}
			}
		}
		// By day the fog is vanilla's, to the last digit.
		for (long t : new long[] {0, 6000, cfg.duskFogStart, cfg.duskFogEnd, 23999}) {
			Curves.Fog fog = vanilla(192, false);
			Curves.applyDusk(fog, Curves.duskAmount(0.7, t, shape), Curves.duskHaze(t, shape), cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
			helper.assertTrue(same(fog, vanilla(192, false)), "dusk fog by day at " + t);
		}
		helper.succeed();
	}

	/** Deep dusk and the night, with or without a surge, look exactly as in v0.4: only the way there changed. */
	@GameTest
	public void duskAndNightKeepTheTunedLook(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		FogLimits.Shape shape = cfg.fogShape();
		for (int chunks : CHUNKS) {
			double r = chunks * 16.0;
			for (float level : cfg.duskFogByStage) {
				for (long t : new long[] {cfg.duskFogPeak, (cfg.duskFogPeak + cfg.duskFogHoldUntil) / 2, 15000, cfg.duskFogNightFrom, 18000, cfg.duskFogFadeFrom}) {
					for (double surge : new double[] {0.0, 0.3, 0.7}) {
						double dusk = Curves.duskAmount(level, t, shape);
						Curves.Fog now = vanilla(r, false);
						Curves.applyDusk(now, dusk, Curves.duskHaze(t, shape), cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
						Curves.applySurge(now, surge, cfg.surgeMinFogBlocks, cfg.heavyFogStartFraction);
						Curves.Fog tuned = v04(vanilla(r, false), dusk, surge, cfg);
						helper.assertTrue(same(now, tuned), String.format(Locale.ROOT, "not the v0.4 look at %d chunks, level %.2f, t %d, surge %.1f", chunks,
								level, t, surge));
					}
				}
			}
		}
		helper.succeed();
	}

	/** What made it abrupt: v0.4 switched the close-in haze on whole at the first trace of dusk fog. */
	@GameTest
	public void oldDuskFogPoppedAtSunset(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		double r = 192.0;
		Curves.Fog clear = vanilla(r, false);
		Curves.Fog popped = v04(vanilla(r, false), 1.0E-3, 0.0, cfg);
		double jump = (reach(clear, 0.5, r) - reach(popped, 0.5, r)) / r;
		helper.assertTrue(jump > 0.3, "the v0.4 pop is gone from the reference: " + jump);
		// The new fog at the same amount is still all but vanilla.
		Curves.Fog now = vanilla(r, false);
		Curves.applyDusk(now, 1.0E-3, 1.0E-3, cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
		for (double q : HAZE_LEVELS) {
			helper.assertTrue(Math.abs(reach(now, q, r) - reach(clear, q, r)) < 0.01 * r, "a trace of dusk fog moves the fog at " + q);
		}
		helper.succeed();
	}

	@GameTest
	public void duskLevelChangesEase(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		double full = cfg.duskHazeFullLevel;
		int length = (int) Math.round(cfg.duskLevelChangeSeconds * 20.0);

		// The first level after joining applies at once: no fade-in from zero.
		DuskLevel dusk = new DuskLevel();
		dusk.set(0.45, full, 1000, length);
		helper.assertTrue(dusk.level(1000) == 0.45 && dusk.haze(1000) == 1.0, "the join level does not apply at once");

		// A stage change eases over the whole time, smoothly.
		dusk.set(0.6, full, 2000, length);
		helper.assertTrue(dusk.level(2000) == 0.45, "a level change snaps");
		helper.assertTrue(Math.abs(dusk.level(2000 + length / 2.0) - 0.525) < 1.0E-9, "not halfway at half time");
		helper.assertTrue(dusk.level(2000 + length) == 0.6 && dusk.level(2000 + length * 3) == 0.6, "does not land on the new level");
		double maxStep = 1.5 * 0.15 / length + 1.0E-12;
		for (int k = 1; k <= length; k++) {
			double step = dusk.level(2000 + k) - dusk.level(2000 + k - 1);
			helper.assertTrue(step >= 0.0 && step <= maxStep, "level step " + step + " at tick " + k);
		}
		// The same level again does not restart it; a new one mid-way starts from where it is.
		dusk.set(0.6, full, 2000 + length / 4.0, length);
		helper.assertTrue(Math.abs(dusk.level(2000 + length / 2.0) - 0.525) < 1.0E-9, "a resent level restarted the change");
		dusk.set(0.0, full, 6000, length);
		dusk.set(0.3, full, 6000 + length / 3.0, length);
		double mid = 0.6 * (1.0 - Curves.smooth(1.0 / 3.0));
		helper.assertTrue(Math.abs(dusk.level(6000 + length / 3.0) - mid) < 1.0E-9, "a change mid-way jumped");
		// Level 0 and back: the haze leaves and comes with the level, over the same time.
		dusk.set(0.0, full, 20000, length);
		helper.assertTrue(dusk.haze(20000) > 0.0 && dusk.haze(20000 + length / 2.0) == 0.5 && dusk.haze(20000 + length) == 0.0, "haze does not ease out");
		// Disconnect: the next join applies at once again.
		dusk.reset();
		dusk.set(0.3, full, 50000, length);
		helper.assertTrue(dusk.level(50000) == 0.3, "the next join faded in");

		// The fog itself while the level eases, at dusk and at night.
		FogLimits.Shape shape = cfg.fogShape();
		float[] stages = cfg.duskFogByStage;
		for (int chunks : CHUNKS) {
			double r = chunks * 16.0;
			for (long t : new long[] {12000, cfg.duskFogPeak, 18000}) {
				for (int i = 0; i < stages.length; i++) {
					// Stage to stage (and back to stage 0, as an ending does): as gentle as the day.
					float from = stages[i];
					float to = i + 1 < stages.length ? stages[i + 1] : stages[0];
					easedFog(helper, from, to, t, r, MAX_EDGE_STEP, chunks >= 4 ? MAX_HAZE_STEP : Double.NaN, MAX_CLOUD_STEP, length, shape, cfg);
					// From and to no fog (the command, the endings): the whole haze comes or goes too, still eased over the whole time.
					easedFog(helper, 0.0F, stages[i], t, r, 2 * MAX_EDGE_STEP, Double.NaN, 2 * MAX_CLOUD_STEP, length, shape, cfg);
					easedFog(helper, stages[i], 0.0F, t, r, 2 * MAX_EDGE_STEP, Double.NaN, 2 * MAX_CLOUD_STEP, length, shape, cfg);
				}
			}
		}
		helper.succeed();
	}

	/** Eases from one level to another at time of day {@code t} and checks every step of the fog. */
	private static void easedFog(GameTestHelper helper, float from, float to, long t, double r, double maxEdge, double maxHaze, double maxCloud,
			int length, FogLimits.Shape shape, AtmosphereConfig cfg) {
		DuskLevel dusk = new DuskLevel();
		dusk.set(from, cfg.duskHazeFullLevel, 0, length);
		dusk.set(to, cfg.duskHazeFullLevel, 0, length);
		Curves.Fog first = null;
		Curves.Fog previous = null;
		for (int k = 0; k <= length; k += STEP) {
			Curves.Fog fog = vanilla(r, false);
			Curves.applyDusk(fog, Curves.duskAmount(dusk.level(k), t, shape), Curves.duskHaze(t, shape) * dusk.haze(k), cfg.duskMinFogBlocks,
					cfg.heavyFogStartFraction);
			String where = String.format(Locale.ROOT, "(level %.2f to %.2f, %d blocks, t %d, tick %d)", from, to, (int) r, t, k);
			if (previous == null) {
				first = fog;
			} else {
				assertSteps(helper, previous, fog, r, maxEdge, maxHaze, maxCloud, where);
			}
			previous = fog;
		}
		// It starts where it was and ends where it goes.
		Curves.Fog before = vanilla(r, false);
		Curves.applyDusk(before, Curves.duskAmount(from, t, shape), Curves.duskHaze(t, shape) * Math.min(1.0, from / cfg.duskHazeFullLevel),
				cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
		Curves.Fog after = vanilla(r, false);
		Curves.applyDusk(after, Curves.duskAmount(to, t, shape), Curves.duskHaze(t, shape) * Math.min(1.0, to / cfg.duskHazeFullLevel),
				cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
		helper.assertTrue(same(first, before) && same(previous, after), String.format(Locale.ROOT, "level %.2f to %.2f does not go from one to the other",
				from, to));
	}

	/**
	 * One step of the fog: where it is complete, both starts and the sky's end within {@code maxEdge} of the render
	 * distance; where the haze reaches a quarter, half and three quarters within {@code maxHaze} (NaN: not checked);
	 * the clouds' fade at the render distance within {@code maxCloud}.
	 */
	private static void assertSteps(GameTestHelper helper, Curves.Fog a, Curves.Fog b, double r, double maxEdge, double maxHaze, double maxCloud,
			String where) {
		step(helper, "fog end", a.end(), b.end(), r, maxEdge, where);
		step(helper, "fog start", a.renderDistanceStart, b.renderDistanceStart, r, maxEdge, where);
		step(helper, "haze start", a.environmentalStart, b.environmentalStart, r, maxEdge, where);
		step(helper, "sky end", a.skyEnd, b.skyEnd, r, maxEdge, where);
		step(helper, "clouds at the render distance", Math.min(1.0, r / a.cloudEnd), Math.min(1.0, r / b.cloudEnd), 1.0, maxCloud, where);
		if (!Double.isNaN(maxHaze)) {
			for (double q : HAZE_LEVELS) {
				step(helper, "fog at " + q, reach(a, q, r), reach(b, q, r), r, maxHaze, where);
			}
		}
	}

	private static void step(GameTestHelper helper, String what, double a, double b, double r, double max, String where) {
		double share = Math.abs(b - a) / r;
		helper.assertTrue(share <= max, String.format(Locale.ROOT, "%s jumps %.4f of the render distance (%.2f to %.2f) %s", what, share, a, b, where));
	}

	/** The dusk levels the game uses (each stage) plus the heaviest the command allows. */
	private static float[] levels(AtmosphereConfig cfg) {
		float[] levels = new float[cfg.duskFogByStage.length + 1];
		System.arraycopy(cfg.duskFogByStage, 0, levels, 0, cfg.duskFogByStage.length);
		levels[levels.length - 1] = 1.0F;
		return levels;
	}

	/**
	 * Vanilla's overworld fog for a render distance in blocks (AtmosphericFogEnvironment and FogRenderer.setupFog):
	 * a faint haze from the camera to 1024 blocks, a short wall at the render limit, sky to the render limit (at most
	 * 512), clouds to 2048 (cloud range 128 chunks). Rain moves the haze's start 160 blocks in and its end 256.
	 */
	static Curves.Fog vanilla(double r, boolean rain) {
		double span = Math.clamp(r / 10.0, 4.0, 64.0);
		return new Curves.Fog().set(rain ? -160.0 : 0.0, rain ? 768.0 : 1024.0, r - span, r, Math.min(r, 512.0), 2048.0);
	}

	/** The nearest distance along the ground where the fog reaches {@code amount} (the larger of the two world fogs), at most {@code limit}. */
	static double reach(Curves.Fog fog, double amount, double limit) {
		double environmental = fog.environmentalStart + amount * (fog.environmentalEnd - fog.environmentalStart);
		double render = fog.renderDistanceStart + amount * (fog.renderDistanceEnd - fog.renderDistanceStart);
		return Math.clamp(Math.min(environmental, render), 0.0, limit);
	}

	private static boolean same(Curves.Fog a, Curves.Fog b) {
		return close(a.environmentalStart, b.environmentalStart) && close(a.environmentalEnd, b.environmentalEnd)
				&& close(a.renderDistanceStart, b.renderDistanceStart) && close(a.renderDistanceEnd, b.renderDistanceEnd) && close(a.skyEnd, b.skyEnd)
				&& close(a.cloudEnd, b.cloudEnd);
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) <= 1.0E-6 * Math.max(1.0, Math.abs(a));
	}

	/** The v0.4 client fog (ClientAtmosphere.applyFog before this change), for reference. */
	static Curves.Fog v04(Curves.Fog fog, double dusk, double surge, AtmosphereConfig cfg) {
		if (dusk < 1.0E-3 && surge < 1.0E-3) {
			return fog;
		}
		boolean renderLimited = fog.renderDistanceEnd <= fog.environmentalEnd;
		double base = renderLimited ? fog.renderDistanceEnd : fog.environmentalEnd;
		double baseStart = renderLimited ? fog.renderDistanceStart : fog.environmentalStart;
		double end = Curves.fogEnd(Curves.fogEnd(base, cfg.duskMinFogBlocks, dusk), cfg.surgeMinFogBlocks, surge);
		double heavy = Curves.combine(dusk, surge);
		double start = end * Curves.lerp(heavy, Curves.clamp01(baseStart / base), Curves.clamp01(cfg.heavyFogStartFraction));
		return fog.set(Math.min(fog.environmentalStart, start), Math.min(fog.environmentalEnd, end), Math.min(fog.renderDistanceStart, start),
				Math.min(fog.renderDistanceEnd, end), Math.min(fog.skyEnd, end), Math.min(fog.cloudEnd, end));
	}
}
