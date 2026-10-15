package com.forzacode.a1016_02.atmosphere;

import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.Stage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Review R2 (v1.2 bug hunt): fog weather findings, each test failing on main at 1f78a6f. Pure: the live weather's
 * {@link FogWeather#step} as the server runs it every second, and the client's {@link WeatherFog} math exactly as
 * {@code ClientAtmosphere} calls it (the tint on vanilla's base fog color, which vanilla's {@code computeFogColor}
 * darkens after: modeled here from the 26.3 source).
 */
public class R2FogReviewTests {
	private static final FogWeatherRules.Settings D = FogWeatherRules.Settings.DEFAULT;
	private static final long DAY = FogWeatherRules.DAY;
	private static final int CHECK = 20;

	private static double fog(Optional<FogSpell> spell, long clock, FogWeatherRules.Settings settings) {
		return FogLimits.spellAmount(spell, clock, settings);
	}

	/** Every night the rules allow is foggy, no carry-over, no quiet spell: only the rules decide. */
	private static FogWeatherRules.Settings everyAllowedNight() {
		return new FogWeatherRules.Settings(FogWeatherRules.perStage(1.0), 1.0, 4, 0.0, D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(),
				0.0, D.rampTicks(), D.fadeTicks(), D.easeExponent());
	}

	/**
	 * A clock set back by less than a day (here {@code /time set 30000} at 36000, night 1's fog easing in) kept the spell
	 * rolled ahead of it (FogWeather.step kept any spell starting within a day) while night 1 rolled again. In Alone right
	 * after a fog night the re-roll is always clear, but a clear roll never touched the stored spell, so the old one
	 * played anyway: fog on a night the rules call clear, and the history (now "clear") let night 2 be foggy too, two fog
	 * nights in a row in Alone. The memory here has nothing to undo (as a file from before the fix): the spell made for a
	 * time the clock has not reached goes, and the re-roll decides.
	 */
	@GameTest
	public void aClearRerollAfterTheClockWentBackDropsTheOldSpell(GameTestHelper helper) {
		FogWeatherRules.Settings always = everyAllowedNight();
		FogSpell rolled = new FogSpell(FogWeatherRules.nightFrom(1), FogWeatherRules.nightUntil(1, false), (float) always.nightLevel(Stage.ALONE), false,
				always);
		// The state at 36000: night 0 clear, night 1 rolled foggy at 34000, day 1's daylight rolled at 21500 of day 0.
		FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(2, 0, true, false), 1, 1, Optional.of(rolled), Optional.empty());
		Optional<FogSpell> spell = Optional.of(rolled);
		double night1Fog = -1;
		double night2Fog = -1;
		Optional<FogSpell> night1Roll = Optional.empty();
		// The clock now at 30000 (day 1, 6000), running on through nights 1 and 2.
		for (long now = 30_000; now <= 2 * DAY + 18_000; now += CHECK) {
			FogWeather.Outcome out = FogWeather.step(memory, spell, now, Stage.ALONE, RandomSource.create(now), true, false, always);
			memory = out.memory();
			spell = out.spell();
			if (now == DAY + 18_000) {
				night1Fog = fog(spell, now, always);
				night1Roll = memory.nightRoll();
			} else if (now == 2 * DAY + 18_000) {
				night2Fog = fog(spell, now, always);
			}
		}
		helper.assertTrue(night1Roll.isPresent() || night1Fog == 0.0,
				"night 1 rolled again clear after the clock went back, but the spell rolled before still gives fog " + night1Fog + " at its middle");
		helper.assertFalse(night1Fog > 0.0 && night2Fog > 0.0,
				String.format(Locale.ROOT, "two fog nights in a row in Alone after /time set: night 1 %.3f, night 2 %.3f (history %s)", night1Fog, night2Fog,
						memory.history()));
		helper.succeed();
	}

	/**
	 * A fog night without a carry-over followed by a fog day: the night starts fading at 22000 (23500 minus the fade).
	 * The day used to be rolled at 23000 and joined from what was left, about 11% of the night's level, so the fog almost
	 * lifted before dawn and then thickened again. The day is rolled at {@link FogWeatherRules#dayRollTime} (21500), while
	 * the night is still at full, so the fog never drops below the lighter of the two.
	 */
	@GameTest
	public void aFogNightIntoAFogDayNeverAlmostLifts(GameTestHelper helper) {
		FogWeatherRules.Settings always = new FogWeatherRules.Settings(FogWeatherRules.perStage(1.0), 1.0, 4, 0.0, FogWeatherRules.perStage(1.0),
				D.nightLevelByStage(), D.dayLevelByStage(), 0.0, D.rampTicks(), D.fadeTicks(), D.easeExponent());
		long d = 12;
		Stage stage = Stage.PROXIMITY;
		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), (float) always.nightLevel(stage), false, always);
		FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(12, 0, true, false), d, d, Optional.of(night), Optional.empty());
		Optional<FogSpell> spell = Optional.of(night);
		double least = 1.0;
		long leastAt = 0;
		for (long now = d * DAY + 18_000; now <= (d + 1) * DAY + 4_000; now += CHECK) {
			FogWeather.Outcome out = FogWeather.step(memory, spell, now, stage, RandomSource.create(now), true, false, always);
			memory = out.memory();
			spell = out.spell();
			double fog = fog(spell, now, always);
			if (fog < least) {
				least = fog;
				leastAt = Math.floorMod(now, DAY);
			}
		}
		helper.assertTrue(memory.dayRolled() == d + 1 && memory.dayRoll().isPresent(), "the fog day did not roll: " + memory);
		double lighter = Math.min(always.nightLevel(stage), always.dayLevel(stage));
		helper.assertTrue(least >= lighter - 1.0E-6, String.format(Locale.ROOT,
				"a fog night (%.2f) handed to a fog day (%.2f) thinned to %.3f at %d before the fog day came back", always.nightLevel(stage),
				always.dayLevel(stage), least, leastAt));
		helper.succeed();
	}

	/**
	 * On a dedicated server the spell's ease used to be each side's own config: the server built hand-overs with its
	 * {@code fogSpellRampTicks} while every client eased the stored spell with its own. A server with a ramp of 5000
	 * hands a fog day (0.55, full) to a fog night (0.72) at 10000: a client on the default 2500 jumped to the night's
	 * full level in one frame (fog end 61 to 43 blocks at a 12-chunk view). Now a spell carries the ease it was made
	 * with: the client, sent the spell through the payload and drawing it with its own default settings exactly as
	 * {@code ClientAtmosphere.applyFog} does, gets the server's fog at the hand-over and over the whole ramp after it.
	 */
	@GameTest
	public void aServerRampTheClientDoesNotShareMakesItPop(GameTestHelper helper) {
		FogWeatherRules.Settings server = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), 5_000L, D.fadeTicks(),
				D.easeExponent());
		FogWeatherRules.Settings client = D;
		long d = 8;
		FogSpell fogDay = new FogSpell(FogWeatherRules.dayFrom(d), FogWeatherRules.dayUntil(d), 0.55F, false, server);
		FogSpell tonight = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.72F, false, server);
		long now = d * DAY + FogWeather.nightRollTime(server);
		FogSpell joined = FogWeatherRules.handOver(Optional.of(fogDay), tonight, now, server);
		helper.assertTrue(joined.rampTicks() == 5_000L && joined.fadeTicks() == D.fadeTicks(), "the hand-over did not keep the server's ease: " + joined);

		// What the client receives: the spell through the payload's codec.
		WeatherFog before = new WeatherFog();
		before.receive(new ClientEffects.FogSpell(roundTrip(Optional.of(fogDay))));
		WeatherFog after = new WeatherFog();
		after.receive(new ClientEffects.FogSpell(roundTrip(Optional.of(joined))));
		helper.assertTrue(after.spell().equals(Optional.of(joined)), "the payload lost the spell's ease: " + after.spell());

		double serverBefore = fog(Optional.of(fogDay), now, server);
		double serverAfter = fog(Optional.of(joined), now, server);
		double clientBefore = before.amount(now, false, client);
		double clientAfter = after.amount(now, false, client);
		helper.assertTrue(Math.abs(serverAfter - serverBefore) < 2.0E-3, "the server's own hand-over popped: " + serverBefore + " -> " + serverAfter);
		helper.assertTrue(Math.abs(clientAfter - clientBefore) < 0.01, String.format(Locale.ROOT,
				"a client on the default ramp pops at the server's hand-over: %.3f -> %.3f (fog end %.0f -> %.0f blocks)", clientBefore, clientAfter,
				FogLimits.fogEnd(192.0, 24.0, clientBefore), FogLimits.fogEnd(192.0, 24.0, clientAfter)));
		FogLimits.Shape shape = FogLimits.Shape.DEFAULT;
		for (long t = now; t <= joined.untilClock(); t += 50) {
			double s = fog(Optional.of(joined), t, server);
			double c = after.amount(t, false, client);
			FogLimits.Result serverFog = FogLimits.of(12, 12, 0.15F, t, Optional.of(joined), shape, server);
			FogLimits.Result clientFog = FogLimits.of(12, 12, 0.15F, t, Optional.of(joined), shape, client);
			helper.assertTrue(s == c && serverFog.equals(clientFog),
					String.format(Locale.ROOT, "client %.4f and server %.4f differ at %d", c, s, Math.floorMod(t, DAY)));
		}
		helper.succeed();
	}

	/**
	 * 26.3's {@code FogRenderer.computeFogColor} darkens the fog color toward black near the bottom of the world (void
	 * darkness: {@code darkness = (32 + minY - cameraY) / 32}, color times {@code (1 - darkness)^2}). The tint used to go
	 * on after that, so at y -56 (diamond depth) a fog day lifted vanilla's near-black fog (and the clear color) to a pale
	 * grey and a fog night lifted it too. Now the client tints vanilla's base color, where computeFogColor's color source
	 * hands it over ({@code ClientAtmosphere.tintFogColor}, exactly {@link WeatherFog#color}), and vanilla darkens after
	 * it: at every height the tinted color is darkened by vanilla's own factor, black at the bottom of the world, never
	 * much lighter than vanilla's at -56, and the whole tint in the open.
	 */
	@GameTest
	public void theTintKeepsVanillasVoidDarkness(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		int minY = -64;
		for (Tinted c : tintedCases(cfg)) {
			for (double y = minY - 8; y <= minY + 48; y += 0.25) {
				float darkness = voidDarkness(y, minY);
				float[] vanilla = darkened(c.base(), darkness);
				float[] ours = darkened(c.tinted(), darkness);
				float brightness = (1.0F - darkness) * (1.0F - darkness);
				for (int k = 0; k < 3; k++) {
					helper.assertTrue(ours[k] <= c.tinted()[k] * brightness + 1.0E-6F, String.format(Locale.ROOT,
							"the tinted %s fog is lighter than vanilla's darkening of it at y %.2f: %.4f", c.name(), y, ours[k]));
				}
				if (y <= minY) {
					helper.assertTrue(max(ours) == 0.0F, String.format(Locale.ROOT, "the %s fog is not black at the bottom of the world (y %.2f): %.4f", c.name(), y,
							max(ours)));
				}
				if (y >= minY + 32) {
					helper.assertTrue(java.util.Arrays.equals(ours, c.tinted()), "the tint is not whole in the open at y " + y);
				}
				if (y == -56.0) {
					helper.assertTrue(max(ours) <= max(vanilla) * 1.5F + 0.01F, String.format(Locale.ROOT,
							"the spell's tint lifts vanilla's void-black %s fog at y %.0f: %.4f -> %.4f", c.name(), y, max(vanilla), max(ours)));
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Blindness and Darkness used to switch the tint off outright (an on/off check on the effect), so the fog color
	 * snapped from the tinted grey to vanilla's dark color the tick an effect began, and back the tick it ended. Now
	 * vanilla's own blend darkens the tinted color ({@code computeFogColor}: Darkness {@code max(blendFactor, void)} over
	 * its 22-tick blend, Blindness {@code max(duration / 20, void)} over its last second, 1 while it lasts): the color at
	 * the first tick of an effect is the tinted one barely darkened, each tick moves it no more than vanilla's own
	 * darkening moves a white fog, it is black at the effect's full, and it comes back the same way.
	 */
	@GameTest
	public void theTintFollowsTheBlindnessAndDarknessBlends(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		int[] blends = {22, 20};
		for (Tinted c : tintedCases(cfg)) {
			for (int ticks : blends) {
				float[] previous = c.tinted().clone();
				float previousBrightness = 1.0F;
				// In over the blend, held, then out over it (Blindness only ever fades out; in is the same math).
				for (int t = 1; t <= 3 * ticks; t++) {
					float darkness = t <= ticks ? t / (float) ticks : t <= 2 * ticks ? 1.0F : (3 * ticks - t) / (float) ticks;
					float[] ours = darkened(c.tinted(), darkness);
					float brightness = (1.0F - darkness) * (1.0F - darkness);
					for (int k = 0; k < 3; k++) {
						helper.assertTrue(Math.abs(ours[k] - previous[k]) <= Math.abs(brightness - previousBrightness) + 1.0E-6F, String.format(Locale.ROOT,
								"the %s fog snaps under a %d-tick blend at tick %d: %.4f -> %.4f", c.name(), ticks, t, previous[k], ours[k]));
					}
					if (t == 1) {
						helper.assertTrue(max(ours) >= max(c.tinted()) * 0.85F, "the tint went off as the effect began: " + max(ours) + " of " + max(c.tinted()));
					}
					if (darkness == 1.0F) {
						helper.assertTrue(max(ours) == 0.0F, "not black at the effect's full: " + max(ours));
					}
					previous = ours;
					previousBrightness = brightness;
				}
				helper.assertTrue(java.util.Arrays.equals(previous, c.tinted()), "the tint did not come back whole after the effect");
			}
		}
		helper.succeed();
	}

	/** Vanilla's base fog color and the client's tint of it, by day and by night. */
	private record Tinted(String name, float[] base, float[] tinted) {
	}

	/**
	 * Vanilla's day base fog color (#c0d8ff) under a Traces fog day at noon, and its night one (the day timeline's
	 * #0f0f16 multiply) under a Proximity fog night at midnight, each tinted as the client tints it.
	 */
	private static Tinted[] tintedCases(AtmosphereConfig cfg) {
		float[] day = {0xc0 / 255.0F, 0xd8 / 255.0F, 1.0F};
		float[] night = {0xc0 / 255.0F * 0x0f / 255.0F, 0xd8 / 255.0F * 0x0f / 255.0F, 0x16 / 255.0F};
		float[] dayTinted = day.clone();
		WeatherFog.color(dayTinted, D.dayLevel(Stage.TRACES), 6_000L, cfg);
		float[] nightTinted = night.clone();
		WeatherFog.color(nightTinted, D.nightLevel(Stage.PROXIMITY), 18_000L, cfg);
		return new Tinted[] {new Tinted("day", day, dayTinted), new Tinted("night", night, nightTinted)};
	}

	/** Vanilla's void darkness at this camera height ({@code computeFogColor}, the overworld's onset range of 32). */
	private static float voidDarkness(double cameraY, int minY) {
		return Math.clamp((32.0F + minY - (float) cameraY) / 32.0F, 0.0F, 1.0F);
	}

	/** {@code computeFogColor}'s darkening of the color its source gave: times {@code (1 - darkness)^2} when there is any. */
	private static float[] darkened(float[] rgb, float darkness) {
		float[] out = rgb.clone();
		if (darkness > 0.0F) {
			float brightness = (1.0F - darkness) * (1.0F - darkness);
			for (int k = 0; k < 3; k++) {
				out[k] *= brightness;
			}
		}
		return out;
	}

	private static float max(float[] rgb) {
		return Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
	}

	private static Optional<FogSpell> roundTrip(Optional<FogSpell> spell) {
		ByteBuf buf = Unpooled.buffer();
		try {
			ClientEffects.FogSpell.CODEC.encode(buf, new ClientEffects.FogSpell(spell));
			return ClientEffects.FogSpell.CODEC.decode(buf).spell();
		} finally {
			buf.release();
		}
	}
}
