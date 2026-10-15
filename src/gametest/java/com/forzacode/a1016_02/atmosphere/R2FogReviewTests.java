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
 * {@code ClientAtmosphere.applyFog} calls it.
 */
public class R2FogReviewTests {
	private static final FogWeatherRules.Settings D = FogWeatherRules.Settings.DEFAULT;
	private static final long DAY = FogWeatherRules.DAY;
	private static final int CHECK = 20;

	private static double fog(Optional<FogSpell> spell, long clock, FogWeatherRules.Settings settings) {
		return FogLimits.spellAmount(spell, clock, settings);
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
