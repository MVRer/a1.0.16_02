package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * Fog weather's rolls (D-071), pure: one sky for the world, some nights fog nights, a fog night sometimes carrying
 * into a grey morning, rarely a whole fog day. The live weather (atmosphere: rolls at the times it picks, stores and
 * sends the spell with {@link ClientEffects#setFogSpell}) and the director's dry runs use the same rules, from a
 * {@link RandomSource}, the clock day, the {@link Stage} and a small {@link History}. None of them checks what only the
 * caller knows: no spell once the world is quiet for good ({@code Gates.quietForGood}, Ending D's afterward) and none
 * shows in fixed-time dimensions ({@link FogLimits#of(net.minecraft.server.level.ServerPlayer)} skips it there).
 *
 * <p>Times (time of day, 0 is sunrise): a fog night runs {@link #NIGHT_FROM} to {@link #NIGHT_UNTIL}; its morning
 * carry-over is the same spell running on to {@link #MORNING_UNTIL} of the next day; a fog day runs from
 * {@link #DAY_FROM} of the day before to {@link #DAY_UNTIL}, where a fog night that rolls takes over without a gap
 * (store {@link #handOver}'s join, never the two spells one after the other).
 *
 * <p>Atmosphere owns the numbers: it installs its config with {@link #install}; until then {@link Settings#DEFAULT}
 * holds D-071's.
 */
public final class FogWeatherRules {
	public static final long DAY = 24000L;
	/** A fog night starts at this time of day. */
	public static final long NIGHT_FROM = 11500L;
	/** A fog night without a morning carry-over is gone by this time of day. */
	public static final long NIGHT_UNTIL = 23500L;
	/** A morning carry-over is gone by this time of the next day. */
	public static final long MORNING_UNTIL = 5500L;
	/** A fog day starts at this time of the day before (just before sunrise). */
	public static final long DAY_FROM = 23000L;
	/** A fog day is gone by this time of day, where a fog night starts. */
	public static final long DAY_UNTIL = 11500L;

	/**
	 * The numbers behind the rolls, D-071's by default. Every list has one entry per {@link Stage} ({@link Stage#level()}
	 * 0 to 4): the constructor makes each exactly that long (a short list repeats its last entry, an empty or null one is
	 * all 0) and puts every chance, level and share in 0 to 1 (NaN is 0), {@code clearNightsBeforeFog} at 1 or more,
	 * the ramp and fade at 0 or more and the exponent in 1 to 8 (NaN is 3).
	 *
	 * @param nightChanceByStage   chance of a fog night from the fourth night on
	 * @param secondNightChance    chance that the second night (night 1) is foggy; the first never is
	 * @param clearNightsBeforeFog after this many clear nights in a row the next one is foggy
	 * @param morningCarryChance   share of fog nights that carry into the next morning
	 * @param dayChanceByStage     chance of a fog day, per day not already covered (never in Alone, never two in a row)
	 * @param nightLevelByStage    a fog night's level
	 * @param dayLevelByStage      a fog day's level
	 * @param quietShare           share of spells that are quiet (no director boost)
	 * @param rampTicks            a spell eases in over this many ticks from its start
	 * @param fadeTicks            and out over this many before its end
	 * @param easeExponent         the ease's exponent ({@link FogLimits#ease}, the dusk fog's 3)
	 */
	public record Settings(List<Double> nightChanceByStage, double secondNightChance, int clearNightsBeforeFog, double morningCarryChance,
			List<Double> dayChanceByStage, List<Double> nightLevelByStage, List<Double> dayLevelByStage, double quietShare, long rampTicks,
			long fadeTicks, double easeExponent) {
		public static final Settings DEFAULT = new Settings(perStage(0.25, 0.33, 0.4, 0.45, 0.5), 0.5, 4, 0.35, perStage(0, 0.06, 0.1, 0.12, 0.12),
				perStage(0.45, 0.55, 0.65, 0.72, 0.8), perStage(0.4, 0.45, 0.55, 0.6, 0.65), 0.25, 2500L, 1500L, 3.0);

		public Settings {
			nightChanceByStage = normalized(nightChanceByStage);
			secondNightChance = unit(secondNightChance);
			clearNightsBeforeFog = Math.max(1, clearNightsBeforeFog);
			morningCarryChance = unit(morningCarryChance);
			dayChanceByStage = normalized(dayChanceByStage);
			nightLevelByStage = normalized(nightLevelByStage);
			dayLevelByStage = normalized(dayLevelByStage);
			quietShare = unit(quietShare);
			rampTicks = Math.max(0L, rampTicks);
			fadeTicks = Math.max(0L, fadeTicks);
			easeExponent = Double.isNaN(easeExponent) ? 3.0 : Math.clamp(easeExponent, 1.0, 8.0);
		}

		public double nightChance(Stage stage) {
			return nightChanceByStage.get(stage.level());
		}

		public double dayChance(Stage stage) {
			return dayChanceByStage.get(stage.level());
		}

		public double nightLevel(Stage stage) {
			return nightLevelByStage.get(stage.level());
		}

		public double dayLevel(Stage stage) {
			return dayLevelByStage.get(stage.level());
		}

		private static List<Double> normalized(@Nullable List<Double> values) {
			int stages = Stage.values().length;
			List<Double> out = new ArrayList<>(stages);
			double last = 0.0;
			for (int i = 0; i < stages; i++) {
				if (values != null && i < values.size() && values.get(i) != null) {
					last = unit(values.get(i));
				}
				out.add(last);
			}
			return List.copyOf(out);
		}

		private static double unit(double value) {
			return Double.isNaN(value) ? 0.0 : Math.clamp(value, 0.0, 1.0);
		}
	}

	/**
	 * What the rules remember between rolls: how many nights were rolled before (the first-nights rules count these, so
	 * a world that got the mod late still starts with a clear night), the clear nights in a row, and whether the last
	 * night and the last day were foggy. Keep it with the weather ({@link #CODEC}) and step it after every roll.
	 */
	public record History(int nights, int clearNights, boolean lastNightFoggy, boolean lastDayFoggy) {
		/** Before the first night. */
		public static final History START = new History(0, 0, false, false);
		public static final Codec<History> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("nights", 0).forGetter(History::nights),
				Codec.INT.optionalFieldOf("clearNights", 0).forGetter(History::clearNights),
				Codec.BOOL.optionalFieldOf("lastNightFoggy", false).forGetter(History::lastNightFoggy),
				Codec.BOOL.optionalFieldOf("lastDayFoggy", false).forGetter(History::lastDayFoggy)
		).apply(i, History::new));

		public History {
			nights = Math.max(0, nights);
			clearNights = Math.max(0, clearNights);
		}

		/** After a night's roll: one more night, foggy or clear. */
		public History afterNight(boolean foggy) {
			return new History(nights == Integer.MAX_VALUE ? nights : nights + 1, foggy ? 0 : Math.min(Integer.MAX_VALUE - 1, clearNights) + 1,
					foggy, lastDayFoggy);
		}

		/** After a day's roll. A day a morning carry-over covered is not a fog day ({@code afterDay(false)}). */
		public History afterDay(boolean foggy) {
			return new History(nights, clearNights, lastNightFoggy, foggy);
		}
	}

	private static volatile Supplier<Settings> settings = () -> Settings.DEFAULT;

	private FogWeatherRules() {
	}

	/** Atmosphere: where the settings come from (its config section). Any thread. */
	public static void install(Supplier<Settings> source) {
		settings = Objects.requireNonNull(source);
	}

	/** The installed settings, or {@link Settings#DEFAULT}. */
	public static Settings settings() {
		Settings current = settings.get();
		return current != null ? current : Settings.DEFAULT;
	}

	/** One value per stage, for {@link Settings}. */
	public static List<Double> perStage(double... values) {
		List<Double> out = new ArrayList<>(values.length);
		for (double value : values) {
			out.add(value);
		}
		return out;
	}

	/** {@link #perStage(double...)} from a config's float array. */
	public static List<Double> perStage(float[] values) {
		List<Double> out = new ArrayList<>(values.length);
		for (float value : values) {
			out.add((double) value);
		}
		return out;
	}

	/** Clock tick a fog night starting on clock day {@code day} begins. */
	public static long nightFrom(long day) {
		return day * DAY + NIGHT_FROM;
	}

	/** Clock tick that night is gone by: {@link #NIGHT_UNTIL}, or {@link #MORNING_UNTIL} of the next day with a carry-over. */
	public static long nightUntil(long day, boolean morningCarry) {
		return morningCarry ? (day + 1) * DAY + MORNING_UNTIL : day * DAY + NIGHT_UNTIL;
	}

	/** Clock tick a fog day over clock day {@code day}'s daylight begins ({@link #DAY_FROM} of the day before). */
	public static long dayFrom(long day) {
		return (day - 1) * DAY + DAY_FROM;
	}

	/** Clock tick that fog day is gone by. */
	public static long dayUntil(long day) {
		return day * DAY + DAY_UNTIL;
	}

	/**
	 * Whether the next night is a fog night, in this order: the first night (no night rolled before) never; the second
	 * at {@code secondNightChance}; the third always if neither before it was; always after {@code clearNightsBeforeFog}
	 * clear nights in a row; never right after a fog night in Alone or Traces; otherwise at the stage's chance. Draws
	 * one number from {@code random} only when a chance decides.
	 */
	public static boolean foggyNight(RandomSource random, Stage stage, History history, Settings settings) {
		int night = history.nights();
		if (night == 0) {
			return false;
		}
		if (night == 1) {
			return random.nextDouble() < settings.secondNightChance();
		}
		if (night == 2 && history.clearNights() >= 2) {
			return true;
		}
		if (history.clearNights() >= settings.clearNightsBeforeFog()) {
			return true;
		}
		if (history.lastNightFoggy() && !stage.atLeast(Stage.PROXIMITY)) {
			return false;
		}
		return random.nextDouble() < settings.nightChance(stage);
	}

	/** {@link #night(RandomSource, long, Stage, History, Settings)} with the installed settings. */
	public static Optional<FogSpell> night(RandomSource random, long day, Stage stage, History history) {
		return night(random, day, stage, history, settings());
	}

	/**
	 * Tonight's roll, for the night that starts on clock day {@code day} ({@code clock / 24000}): a fog night by
	 * {@link #foggyNight}, at the stage's night level, carrying into the next morning at {@code morningCarryChance},
	 * quiet at {@code quietShare}; empty for a clear night. Step the history with
	 * {@code history.afterNight(result.isPresent())}.
	 */
	public static Optional<FogSpell> night(RandomSource random, long day, Stage stage, History history, Settings settings) {
		if (!foggyNight(random, stage, history, settings)) {
			return Optional.empty();
		}
		boolean carry = random.nextDouble() < settings.morningCarryChance();
		boolean quiet = random.nextDouble() < settings.quietShare();
		return Optional.of(new FogSpell(nightFrom(day), nightUntil(day, carry), (float) settings.nightLevel(stage), quiet));
	}

	/** Whether {@code spell} already runs into clock day {@code day}'s daylight (a morning carry-over, or a fog day set before). */
	public static boolean covers(Optional<FogSpell> spell, long day) {
		return spell.isPresent() && spell.get().untilClock() > day * DAY;
	}

	/** {@link #day(RandomSource, long, Stage, History, Optional, Settings)} with the installed settings. */
	public static Optional<FogSpell> day(RandomSource random, long day, Stage stage, History history, Optional<FogSpell> current) {
		return day(random, day, stage, history, current, settings());
	}

	/**
	 * The roll for clock day {@code day}'s daylight, made by its {@link #DAY_FROM}: never in Alone, never right after a
	 * fog day, never when {@code current} already {@link #covers} the day; otherwise a fog day at the stage's chance and
	 * day level, quiet at {@code quietShare}. Draws from {@code random} only when a chance decides. Step the history with
	 * {@code history.afterDay(result.isPresent())}.
	 */
	public static Optional<FogSpell> day(RandomSource random, long day, Stage stage, History history, Optional<FogSpell> current,
			Settings settings) {
		if (!stage.atLeast(Stage.TRACES) || history.lastDayFoggy() || covers(current, day)) {
			return Optional.empty();
		}
		if (random.nextDouble() >= settings.dayChance(stage)) {
			return Optional.empty();
		}
		boolean quiet = random.nextDouble() < settings.quietShare();
		return Optional.of(new FogSpell(dayFrom(day), dayUntil(day), (float) settings.dayLevel(stage), quiet));
	}

	/** {@link #handOver(Optional, FogSpell, long, Settings)} with the installed settings. */
	public static FogSpell handOver(Optional<FogSpell> current, FogSpell next, long now) {
		return handOver(current, next, now, settings());
	}

	/**
	 * The spell to store when {@code next} is rolled at clock tick {@code now} while {@code current} may still be
	 * giving fog (a fog day when tonight's fog night rolls, a fog night when a fog day rolls): {@code next} itself when
	 * {@code current} gives none now; otherwise one spell that goes on from exactly the fog there is now, never thins
	 * after it, eases up to {@code next}'s level over what is left of a ramp, and ends like {@code next}. Its level is
	 * the larger of {@code next}'s and the fog now (a lighter next spell keeps the heavier fog until it fades), its
	 * quiet is {@code next}'s. Both sides compute the same fog from the stored result, so the hand-over never pops. A
	 * fog day starts fading {@code fadeTicks} before {@link #DAY_UNTIL}: roll tonight before that for no thinning at all.
	 */
	public static FogSpell handOver(Optional<FogSpell> current, FogSpell next, long now, Settings settings) {
		double fogNow = current.map(spell -> spell.amount(now, settings)).orElse(0.0);
		if (fogNow <= 0.0) {
			return next;
		}
		float level = (float) Math.max(next.level(), fogNow);
		double share = level > 0.0F ? Math.min(1.0, fogNow / level) : 1.0;
		long back = Math.round(inverseEase(share, settings.easeExponent()) * settings.rampTicks());
		long from = now - back;
		// The joined spell must not start easing out before now: keep at least the fade's length ahead.
		long until = Math.max(next.untilClock(), now + settings.fadeTicks());
		return new FogSpell(from, until, level, next.quiet());
	}

	/** The x in 0 to 1 where {@link FogLimits#ease} reaches {@code value} (it rises monotonically). */
	static double inverseEase(double value, double exponent) {
		if (value <= 0.0) {
			return 0.0;
		}
		if (value >= 1.0) {
			return 1.0;
		}
		double lo = 0.0;
		double hi = 1.0;
		for (int i = 0; i < 50; i++) {
			double mid = (lo + hi) / 2.0;
			if (FogLimits.ease(mid, exponent) < value) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2.0;
	}
}
