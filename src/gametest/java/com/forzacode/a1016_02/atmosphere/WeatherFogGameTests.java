package com.forzacode.a1016_02.atmosphere;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Fog weather on the client (D-071, {@link WeatherFog}), with the client's own math: the spell's fog combined with the
 * dusk fog never jumps (across the spell's edges, its hand-overs, a fade for the quiet for good, sunset, sunrise and a
 * dusk level change), is vanilla's or the dusk fog's bit for bit without a spell, and ends exactly where core's
 * {@code FogLimits} says on the server; the color lift is 0 without a spell and grows with it, and never jumps either.
 * {@code DeadMountainGameTests} extends this.
 */
public class WeatherFogGameTests extends DuskFogGameTests {
	private static final long DAY = FogWeatherRules.DAY;
	/** The most one channel of the fog color may move in a second (vanilla's own sunset moves it about 0.01). */
	private static final double MAX_COLOR_STEP = 0.03;

	/** One stretch of the stored spell: from this clock tick on, the server stores {@code spell}. */
	record Piece(long from, Optional<FogSpell> spell) {
	}

	/** The spell the server stores over time, as a list of pieces from the earliest. */
	record Weather(String name, List<Piece> pieces) {
		Optional<FogSpell> at(long clock) {
			Optional<FogSpell> spell = Optional.empty();
			for (Piece piece : pieces) {
				if (piece.from() <= clock) {
					spell = piece.spell();
				}
			}
			return spell;
		}

		long start() {
			return pieces.getFirst().spell().map(FogSpell::fromClock).orElse(pieces.getFirst().from()) - 1_000;
		}

		long end() {
			return pieces.getLast().spell().map(FogSpell::untilClock).orElse(pieces.getLast().from()) + 1_000;
		}
	}

	/** Night, carry-over, fog day, both hand-overs, a late roll, a fade for the quiet for good, a short spell, a command from noon. */
	static List<Weather> weathers(FogWeatherRules.Settings s) {
		long d = 1;
		FogSpell night45 = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.45F, false);
		FogSpell night80 = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, true), 0.8F, false);
		FogSpell day65 = new FogSpell(FogWeatherRules.dayFrom(d), FogWeatherRules.dayUntil(d), 0.65F, true);
		FogSpell tonight = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.8F, false);
		long nightRoll = d * DAY + FogWeather.nightRollTime(s);
		FogSpell night72 = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.72F, false);
		FogSpell day55 = new FogSpell(FogWeatherRules.dayFrom(d + 1), FogWeatherRules.dayUntil(d + 1), 0.55F, false);
		long dayRoll = FogWeatherRules.dayFrom(d + 1);
		FogSpell late = new FogSpell(d * DAY + 15_000, FogWeatherRules.nightUntil(d, false), 0.8F, false);
		long quietAt = d * DAY + 17_000;
		long noon = d * DAY + 6_000;
		return List.of(
				new Weather("night 0.45", List.of(new Piece(0, Optional.of(night45)))),
				new Weather("night 0.8 into its morning", List.of(new Piece(0, Optional.of(night80)))),
				new Weather("fog day 0.65 handed to a fog night 0.8", List.of(new Piece(0, Optional.of(day65)),
						new Piece(nightRoll, Optional.of(FogWeatherRules.handOver(Optional.of(day65), tonight, nightRoll, s))))),
				new Weather("fog night 0.72 handed to a fog day 0.55", List.of(new Piece(0, Optional.of(night72)),
						new Piece(dayRoll, Optional.of(FogWeatherRules.handOver(Optional.of(night72), day55, dayRoll, s))))),
				new Weather("a late night 0.8 from 15000", List.of(new Piece(0, Optional.of(late)))),
				new Weather("night 0.8 eased out for the quiet for good", List.of(new Piece(0, Optional.of(night80)),
						new Piece(quietAt, FogWeather.fadeOut(Optional.of(night80), quietAt, s)))),
				new Weather("a short spell", List.of(new Piece(0, Optional.of(new FogSpell(d * DAY + 1_000, d * DAY + 3_000, 0.8F, false))))),
				new Weather("a command fog day from noon", List.of(new Piece(0, Optional.empty()),
						new Piece(noon, Optional.of(FogWeather.commandSpell(FogWeather.Kind.DAY, noon, Stage.REMOVAL, false, s))))));
	}

	/** The fog the client draws at this clock tick: vanilla's for this render distance, the dusk fog at rest at {@code level}, and the spell. */
	static Curves.Fog frame(double r, boolean rain, double level, long clock, Optional<FogSpell> spell, AtmosphereConfig cfg) {
		FogLimits.Shape shape = cfg.fogShape();
		double presence = level > 0.0 ? Math.min(1.0, level / cfg.duskHazeFullLevel) : 0.0;
		Curves.Fog fog = vanilla(r, rain);
		WeatherFog.shape(fog, Curves.duskAmount(level, clock, shape), Curves.duskHaze(clock, shape) * presence,
				FogLimits.spellAmount(spell, clock, cfg.fogWeather()), 0.0, cfg);
		return fog;
	}

	/**
	 * The spell eases in and out over 2500 and 1500 ticks where the dusk fog takes 4000 (from its start to its peak):
	 * its steps may be that much faster than the dusk fog's limits. A pop moves the fog by most of the view at once.
	 */
	static double speedUp(AtmosphereConfig cfg) {
		return (double) (cfg.duskFogPeak - cfg.duskFogStart) / Math.max(1L, Math.min(cfg.fogSpellRampTicks, cfg.fogSpellFadeTicks));
	}

	@GameTest
	public void spellFogNeverJumps(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		double f = speedUp(cfg);
		for (Weather weather : weathers(cfg.fogWeather())) {
			for (int chunks : CHUNKS) {
				double r = chunks * 16.0;
				for (boolean rain : new boolean[] {false, true}) {
					for (double level : new double[] {0.0, 0.15, 0.45, 0.7}) {
						Curves.Fog previous = null;
						for (long t = weather.start(); t <= weather.end(); t += STEP) {
							Curves.Fog fog = frame(r, rain, level, t, weather.at(t), cfg);
							String where = String.format(Locale.ROOT, "(%s, %d chunks%s, dusk %.2f, day %d %d)", weather.name(), chunks, rain ? ", rain" : "", level,
									Math.floorDiv(t, DAY), Math.floorMod(t, DAY));
							if (previous != null) {
								assertSteps(helper, previous, fog, r, MAX_EDGE_STEP * f, chunks >= 4 && !rain ? MAX_HAZE_STEP * f : Double.NaN, MAX_CLOUD_STEP * f,
										where);
							}
							previous = fog;
						}
						// Where the stored spell changes, the fog just before and just after is the same (the hand-over).
						for (Piece piece : weather.pieces().subList(1, weather.pieces().size())) {
							Curves.Fog before = frame(r, rain, level, piece.from(), weather.at(piece.from() - 1), cfg);
							Curves.Fog after = frame(r, rain, level, piece.from(), piece.spell(), cfg);
							assertSteps(helper, before, after, r, 0.01, Double.NaN, 0.01, "at the change of " + weather.name());
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/** A dusk level change (a stage change) during a fog night eases as gently as without one. */
	@GameTest
	public void aDuskLevelChangeUnderASpellEases(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		FogLimits.Shape shape = cfg.fogShape();
		FogWeatherRules.Settings s = cfg.fogWeather();
		FogSpell spell = new FogSpell(FogWeatherRules.nightFrom(1), FogWeatherRules.nightUntil(1, true), 0.65F, false);
		int length = (int) Math.round(cfg.duskLevelChangeSeconds * 20.0);
		for (long at : new long[] {DAY + 12_000, DAY + 15_000, DAY + 21_000, 2 * DAY + 3_000}) {
			for (float[] change : new float[][] {{0.15F, 0.3F}, {0.45F, 0.6F}, {0.7F, 0.0F}, {0.0F, 0.15F}}) {
				for (int chunks : CHUNKS) {
					double r = chunks * 16.0;
					DuskLevel dusk = new DuskLevel();
					dusk.set(change[0], cfg.duskHazeFullLevel, 0, length);
					dusk.set(change[1], cfg.duskHazeFullLevel, 0, length);
					Curves.Fog previous = null;
					for (int k = 0; k <= length; k += STEP) {
						long clock = at + k;
						Curves.Fog fog = vanilla(r, false);
						WeatherFog.shape(fog, Curves.duskAmount(dusk.level(k), clock, shape), Curves.duskHaze(clock, shape) * dusk.haze(k),
								FogLimits.spellAmount(Optional.of(spell), clock, s), 0.0, cfg);
						if (previous != null) {
							assertSteps(helper, previous, fog, r, 2 * MAX_EDGE_STEP, Double.NaN, 2 * MAX_CLOUD_STEP,
									String.format(Locale.ROOT, "(dusk %.2f to %.2f at %d, %d chunks, tick %d)", change[0], change[1], at % DAY, chunks, k));
						}
						previous = fog;
					}
				}
			}
		}
		helper.succeed();
	}

	/** Without a spell the client's fog is exactly what it was before fog weather (the dusk fog and surges only). */
	@GameTest
	public void noSpellChangesNothing(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		FogLimits.Shape shape = cfg.fogShape();
		for (int chunks : CHUNKS) {
			for (float level : new float[] {0.0F, 0.15F, 0.45F, 0.7F, 1.0F}) {
				for (long t = 0; t < DAY; t += 100) {
					for (double surge : new double[] {0.0, 0.5}) {
						double dusk = Curves.duskAmount(level, t, shape);
						double haze = Curves.duskHaze(t, shape) * Math.min(1.0, level / cfg.duskHazeFullLevel);
						Curves.Fog now = vanilla(chunks * 16.0, false);
						boolean shaped = WeatherFog.shape(now, dusk, haze, 0.0, surge, cfg);
						Curves.Fog before = vanilla(chunks * 16.0, false);
						if (dusk > 0.0 || haze > 0.0 || surge >= 1.0E-3) {
							Curves.applyDusk(before, dusk, haze, cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
							Curves.applySurge(before, surge, cfg.surgeMinFogBlocks, cfg.heavyFogStartFraction);
						}
						helper.assertTrue(shaped == (dusk > 0.0 || haze > 0.0 || surge >= 1.0E-3) && exactly(now, before),
								String.format(Locale.ROOT, "the fog changed without a spell (%d chunks, level %.2f, t %d, surge %.1f)", chunks, level, t, surge));
					}
				}
			}
		}
		helper.assertTrue(WeatherFog.combined(0.37, 0.0) == 0.37 && WeatherFog.combined(0.0, 0.37) == 0.37 && WeatherFog.haze(0.4, 0.0, 0.6) == 0.4,
				"zero does not give the other exactly");
		helper.succeed();
	}

	/**
	 * The color lift: none without a spell at any time of day; at night it lifts vanilla's near black toward
	 * {@code fogNightColor} by {@code fogNightColorLift} times the amount, by day it greys toward {@code fogDayColor} by
	 * the amount, both growing with the amount; across sunset and sunrise and every spell above it never jumps.
	 */
	@GameTest
	public void theColorLiftIsZeroWithoutASpellAndGrowsWithIt(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		for (long t = 0; t < DAY; t += 50) {
			float[] rgb = vanillaColor(t);
			float[] copy = rgb.clone();
			WeatherFog.color(rgb, 0.0, t, cfg);
			helper.assertTrue(java.util.Arrays.equals(rgb, copy) && WeatherFog.colorWeight(0.0, t, cfg.fogNightColorLift) == 0.0, "a lift without a spell at " + t);
		}
		helper.assertTrue(Curves.daylight(6_000) == 1.0 && Curves.daylight(18_000) == 0.0 && Curves.daylight(Curves.SUNSET_FROM) == 1.0
				&& Curves.daylight(Curves.SUNSET_TO) == 0.0 && Curves.daylight(Curves.SUNRISE_FROM) == 0.0 && Curves.daylight(133) == 1.0, "daylight keyframes");
		double lastNight = brightness(vanillaColor(18_000));
		double lastDay = saturation(vanillaColor(6_000));
		float[] night = rgb(cfg.fogNightRgb());
		float[] grey = rgb(cfg.fogDayRgb());
		for (int i = 1; i <= 16; i++) {
			double amount = i * 0.05;
			float[] dark = vanillaColor(18_000);
			float[] base = dark.clone();
			WeatherFog.color(dark, amount, 18_000, cfg);
			helper.assertTrue(Math.abs(WeatherFog.colorWeight(amount, 18_000, cfg.fogNightColorLift) - cfg.fogNightColorLift * amount) < 1.0E-12,
					"the night lift is not lift times the amount");
			for (int c = 0; c < 3; c++) {
				double expected = base[c] + cfg.fogNightColorLift * amount * (night[c] - base[c]);
				helper.assertTrue(Math.abs(dark[c] - expected) < 1.0E-6, "the night color is not lifted toward the night color at " + amount);
			}
			helper.assertTrue(brightness(dark) > lastNight, "the night fog does not get paler with the amount: " + amount);
			lastNight = brightness(dark);
			float[] day = vanillaColor(6_000);
			WeatherFog.color(day, amount, 6_000, cfg);
			helper.assertTrue(Math.abs(WeatherFog.colorWeight(amount, 6_000, cfg.fogNightColorLift) - amount) < 1.0E-12, "the day weight is not the amount");
			helper.assertTrue(saturation(day) < lastDay, "the day fog does not grey with the amount: " + amount);
			lastDay = saturation(day);
			for (int c = 0; c < 3; c++) {
				helper.assertTrue(Math.abs(day[c] - grey[c]) < Math.abs(vanillaColor(6_000)[c] - grey[c]) + 1.0E-6, "the day fog moved away from the grey");
			}
		}
		// Never a jump: every spell above, across sunset, the night and sunrise.
		for (Weather weather : weathers(cfg.fogWeather())) {
			float[] previous = null;
			for (long t = weather.start(); t <= weather.end(); t += STEP) {
				float[] rgb = vanillaColor(t);
				WeatherFog.color(rgb, FogLimits.spellAmount(weather.at(t), t, cfg.fogWeather()), Math.floorMod(t, DAY), cfg);
				if (previous != null) {
					for (int c = 0; c < 3; c++) {
						helper.assertTrue(Math.abs(rgb[c] - previous[c]) <= MAX_COLOR_STEP, String.format(Locale.ROOT, "the color jumps %.4f (%s, day %d %d)",
								Math.abs(rgb[c] - previous[c]), weather.name(), Math.floorDiv(t, DAY), Math.floorMod(t, DAY)));
					}
				}
				previous = rgb;
			}
		}
		helper.succeed();
	}

	/**
	 * Core's server-side fog ({@code FogLimits.of} with the spell, where he may stand) lands exactly where the client's
	 * fog with the same spell ends, for every stage's dusk level, every spell above, across two days and four view
	 * distances, with atmosphere's live settings; the server form reads the stored spell on the level's clock.
	 */
	@GameTest
	public void serverFogMatchesClientFogWithASpell(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		helper.assertTrue(settings.equals(cfg.fogWeather()) && FogLimits.shape().equals(cfg.fogShape()), "FogLimits does not use atmosphere's live config");
		WeatherFog client = new WeatherFog();
		for (Weather weather : weathers(settings)) {
			for (Stage stage : Stage.values()) {
				float dusk = cfg.duskFogFor(stage);
				for (long clock = weather.start(); clock <= weather.end(); clock += 100) {
					Optional<FogSpell> spell = weather.at(clock);
					client.receive(new ClientEffects.FogSpell(spell));
					double amount = client.amount(clock, false, settings);
					helper.assertTrue(amount == FogLimits.spellAmount(spell, clock, settings) && client.amount(clock, true, settings) == 0.0, "the client's amount");
					for (int chunks : new int[] {2, 8, 12, 32}) {
						FogLimits.Result server = FogLimits.of(chunks, 32, dusk, clock, spell);
						Curves.Fog fog = vanilla(server.renderLimit(), false);
						WeatherFog.shape(fog, Curves.duskAmount(dusk, clock, cfg.fogShape()), Curves.duskHaze(clock, cfg.fogShape()), amount, 0.0, cfg);
						helper.assertTrue(Math.abs(server.fogEnd() - fog.end()) < 1.0E-9, String.format(Locale.ROOT,
								"server fog %.4f != client %.4f (%s, stage %s, day %d %d, %d chunks)", server.fogEnd(), fog.end(), weather.name(), stage,
								Math.floorDiv(clock, DAY), Math.floorMod(clock, DAY), chunks));
					}
				}
			}
		}
		// The server form: the stored spell, the player's level clock and view distance.
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects before = state.effects();
		try {
			ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			long now = player.level().getDefaultClockTime();
			FogSpell spell = new FogSpell(now - 3_000, now + 6_000, 0.7F, false);
			ClientEffects.setFogSpell(server, Optional.of(spell));
			FogLimits.Result live = FogLimits.of(player);
			FogLimits.Result pure = FogLimits.of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), before.duskFogLevel(), now,
					Optional.of(spell));
			helper.assertTrue(live.equals(pure) && live.fogEnd() < live.renderLimit(), "the server form: " + live + " vs " + pure);
		} finally {
			ClientEffects.setFogSpell(server, before.fogSpell());
		}
		helper.succeed();
	}

	/** Vanilla's overworld fog color, modeled: the plains fog color (#c0d8ff) times the day timeline's multiplier (to #0f0f16 at night). */
	static float[] vanillaColor(long time) {
		double day = Curves.daylight(time);
		float[] base = {0xc0 / 255.0F, 0xd8 / 255.0F, 1.0F};
		float[] dark = {0x0f / 255.0F, 0x0f / 255.0F, 0x16 / 255.0F};
		float[] out = new float[3];
		for (int c = 0; c < 3; c++) {
			out[c] = (float) (base[c] * Curves.lerp(day, dark[c], 1.0));
		}
		return out;
	}

	private static float[] rgb(int color) {
		return new float[] {((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
	}

	private static double brightness(float[] rgb) {
		return (rgb[0] + rgb[1] + rgb[2]) / 3.0;
	}

	private static double saturation(float[] rgb) {
		return Math.max(rgb[0], Math.max(rgb[1], rgb[2])) - Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
	}

	private static boolean exactly(Curves.Fog a, Curves.Fog b) {
		return a.environmentalStart == b.environmentalStart && a.environmentalEnd == b.environmentalEnd && a.renderDistanceStart == b.renderDistanceStart
				&& a.renderDistanceEnd == b.renderDistanceEnd && a.skyEnd == b.skyEnd && a.cloudEnd == b.cloudEnd;
	}
}
