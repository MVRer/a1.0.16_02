package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The live fog weather (D-071): one sky for the world, on the overworld clock (absolute {@code getDefaultClockTime()}
 * ticks, so a fog night slept through lands in its morning). Every second ({@link #check}) it rolls by core's rules
 * ({@link FogWeatherRules}, the numbers from atmosphere's config) and stores the world's spell with
 * {@link ClientEffects#setFogSpell}, which sends it to every client; the client draws it, core's {@code FogLimits}
 * follows it on the server and the director boosts its odds during it.
 *
 * <p>When: tonight (the night starting on this clock day, with its morning carry-over) is rolled at
 * {@link #nightRollTime} (core's {@link FogWeatherRules#NIGHT_ROLL}, 10000: before a fog day starts thinning, so a fog
 * day hands over to a fog night without a dip), a fog day at {@link FogWeatherRules#dayRollTime} (core's
 * {@link FogWeatherRules#DAY_ROLL}, 21500 of the day before: before a fog night starts thinning, so a fog night hands
 * over to a fog day without a dip; never when a morning carry-over already covers it). The director's dry runs roll at
 * the same times. A new spell is joined to the one still giving fog ({@link FogWeatherRules#handOver}), so the fog
 * never pops. Each night and each day is rolled once, only while someone takes part (the first nights rules count the
 * nights a player was there for), and only inside its window; one rolled late (the first participant joined
 * mid-night) eases in from that moment. A spell over, or one far ahead of a clock set back, is cleared (it gives no
 * fog then).
 *
 * <p>A clock set back (R2#3): a roll the clock went back past is undone, so the night or day rolls again when it
 * comes and that re-roll replaces it. The memory keeps what the last night roll and the last day roll changed
 * ({@link Undo}: the history and the world's spell just before each), and going back past them puts both back, the
 * later first, so the re-roll counts in the history once and a clear re-roll leaves no fog of the roll it replaces. A
 * clock set back further than those two rolls rolls the nights and days ahead of it again as new ones (the history
 * goes on), and a spell that gives no fog yet, made for a time the clock has not reached, is dropped.
 *
 * <p>None while the world is quiet for good ({@link Gates#quietForGood}: Endings C and D's afterward) or while Ending
 * D's last minute gives back in view ({@link TraceService#LAST_MINUTE_FLAG}): nothing is rolled, and a spell still
 * giving fog eases out over the fade from where it is ({@link #fadeOut}). Fixed-time dimensions never show it (the
 * client and {@code FogLimits} skip it there).
 */
public final class FogWeather {
	public static final long DAY = FogWeatherRules.DAY;
	/** The rolled night or day index before anything was rolled. */
	public static final long NEVER = Long.MIN_VALUE;

	/**
	 * What one roll changed, to put back when the clock is set back past it: the clock tick it was made at, the rules'
	 * history just before it, the same kind's last roll before it (its clock day, {@link #NEVER} for none, and what it
	 * gave) and the world's spell just before it.
	 */
	public record Undo(long at, FogWeatherRules.History history, long rolled, Optional<FogSpell> roll, Optional<FogSpell> spell) {
		public static final Codec<Undo> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("at").forGetter(Undo::at),
				FogWeatherRules.History.CODEC.optionalFieldOf("history", FogWeatherRules.History.START).forGetter(Undo::history),
				Codec.LONG.optionalFieldOf("rolled", NEVER).forGetter(Undo::rolled),
				FogSpell.CODEC.optionalFieldOf("roll").forGetter(Undo::roll),
				FogSpell.CODEC.optionalFieldOf("spell").forGetter(Undo::spell)
		).apply(i, Undo::new));

		public Undo {
			history = history != null ? history : FogWeatherRules.History.START;
			roll = roll != null ? roll : Optional.empty();
			spell = spell != null ? spell : Optional.empty();
		}
	}

	/**
	 * What the weather remembers (in {@code atmosphere.dat}): the rules' history, the last night and day rolled (their
	 * clock day, {@link #NEVER} before the first), what those rolls gave (the spell as rolled, before any hand-over;
	 * empty for clear), for {@code /a1016 fog}, and what each of the two changed ({@link Undo}; none in a file from
	 * before the fix, or once undone).
	 */
	public record Memory(FogWeatherRules.History history, long nightRolled, long dayRolled, Optional<FogSpell> nightRoll, Optional<FogSpell> dayRoll,
			Optional<Undo> undoNight, Optional<Undo> undoDay) {
		public static final Memory START = new Memory(FogWeatherRules.History.START, NEVER, NEVER, Optional.empty(), Optional.empty());
		public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
				FogWeatherRules.History.CODEC.optionalFieldOf("history", FogWeatherRules.History.START).forGetter(Memory::history),
				Codec.LONG.optionalFieldOf("nightRolled", NEVER).forGetter(Memory::nightRolled),
				Codec.LONG.optionalFieldOf("dayRolled", NEVER).forGetter(Memory::dayRolled),
				FogSpell.CODEC.optionalFieldOf("nightRoll").forGetter(Memory::nightRoll),
				FogSpell.CODEC.optionalFieldOf("dayRoll").forGetter(Memory::dayRoll),
				Undo.CODEC.optionalFieldOf("undoNight").forGetter(Memory::undoNight),
				Undo.CODEC.optionalFieldOf("undoDay").forGetter(Memory::undoDay)
		).apply(i, Memory::new));

		public Memory {
			history = history != null ? history : FogWeatherRules.History.START;
			nightRoll = nightRoll != null ? nightRoll : Optional.empty();
			dayRoll = dayRoll != null ? dayRoll : Optional.empty();
			undoNight = undoNight != null ? undoNight : Optional.empty();
			undoDay = undoDay != null ? undoDay : Optional.empty();
		}

		/** A memory with nothing to undo (as a file from before the fix loads). */
		public Memory(FogWeatherRules.History history, long nightRolled, long dayRolled, Optional<FogSpell> nightRoll, Optional<FogSpell> dayRoll) {
			this(history, nightRolled, dayRolled, nightRoll, dayRoll, Optional.empty(), Optional.empty());
		}
	}

	/** The memory and the spell after putting back what the rolls a clock set back went past changed. */
	private record Rewound(Memory memory, Optional<FogSpell> spell) {
	}

	/** One check's result: the memory to keep and the spell to store (the same objects when nothing happened). */
	public record Outcome(Memory memory, Optional<FogSpell> spell) {
	}

	/** What {@code /a1016 fog night|morning|day} sets. */
	public enum Kind {
		NIGHT, MORNING, DAY
	}

	private FogWeather() {
	}

	// --- the schedule (pure) ---

	/**
	 * Time of day tonight is rolled at: {@link FogWeatherRules#nightRollTime}, {@link FogWeatherRules#NIGHT_ROLL}
	 * (10000) unless a longer fade needs it earlier, so a fog day still at full when tonight's fog night rolls is handed
	 * over without thinning.
	 */
	public static long nightRollTime(FogWeatherRules.Settings settings) {
		return FogWeatherRules.nightRollTime(settings);
	}

	/** The night whose roll window this clock tick is in or after: the clock day, from {@link #nightRollTime} on. */
	public static long nightIndex(long clock, FogWeatherRules.Settings settings) {
		return Math.floorDiv(clock - nightRollTime(settings), DAY);
	}

	/** When night {@code night}'s roll window closes: a spell rolled later would be little more than its fade. */
	public static long nightRollUntil(long night, FogWeatherRules.Settings settings) {
		return night * DAY + Math.max(FogWeatherRules.NIGHT_FROM, FogWeatherRules.NIGHT_UNTIL - settings.fadeTicks());
	}

	/**
	 * The day whose daylight roll window this clock tick is in or after: from {@link FogWeatherRules#dayRollTime} (21500)
	 * of the day before.
	 */
	public static long dayIndex(long clock, FogWeatherRules.Settings settings) {
		return Math.floorDiv(clock - FogWeatherRules.dayRollTime(settings), DAY) + 1;
	}

	/** When day {@code day}'s roll window closes: where tonight's opens ({@link #nightRollTime}, 10000). */
	public static long dayRollUntil(long day, FogWeatherRules.Settings settings) {
		return day * DAY + nightRollTime(settings);
	}

	/**
	 * One check of the weather at clock tick {@code now}, pure. {@code current} is the stored spell; {@code anyone}
	 * whether a participant is online (nothing is rolled while nobody is); {@code quiet} whether there is no fog weather
	 * now (quiet for good, the last minute). At most one roll per check: tonight's in its window, else today's.
	 */
	public static Outcome step(Memory memory, Optional<FogSpell> current, long now, Stage stage, RandomSource random, boolean anyone, boolean quiet,
			FogWeatherRules.Settings settings) {
		long night = nightIndex(now, settings);
		long day = dayIndex(now, settings);
		Memory m = memory;
		Optional<FogSpell> spell = current;
		// The clock went back (a /time set) past a roll: undo it; the night or day rolls again when it comes.
		if (m.nightRolled() > night || m.dayRolled() > day) {
			Rewound back = rewind(m, spell, now, night, day);
			m = back.memory();
			spell = back.spell();
		}
		// A spell that is over, or one far ahead of a clock set back, gives no fog now: drop it.
		spell = spell.filter(s -> !s.endedBy(now) && s.fromClock() <= now + DAY);
		if (quiet) {
			return new Outcome(m, fadeOut(spell, now, settings));
		}
		if (!anyone) {
			return new Outcome(m, spell);
		}
		if (night > m.nightRolled() && now < nightRollUntil(night, settings)) {
			Optional<FogSpell> rolled = FogWeatherRules.night(random, night, stage, m.history(), settings);
			Undo undo = new Undo(now, m.history(), m.nightRolled(), m.nightRoll(), spell);
			m = new Memory(m.history().afterNight(rolled.isPresent()), night, m.dayRolled(), rolled, m.dayRoll(), Optional.of(undo), m.undoDay());
			if (rolled.isPresent()) {
				spell = Optional.of(FogWeatherRules.handOver(spell, startingBy(rolled.get(), now), now, settings));
			}
		} else if (day > m.dayRolled() && now < dayRollUntil(day, settings)) {
			Optional<FogSpell> rolled = FogWeatherRules.day(random, day, stage, m.history(), spell, settings);
			Undo undo = new Undo(now, m.history(), m.dayRolled(), m.dayRoll(), spell);
			m = new Memory(m.history().afterDay(rolled.isPresent()), m.nightRolled(), day, m.nightRoll(), rolled, m.undoNight(), Optional.of(undo));
			if (rolled.isPresent()) {
				spell = Optional.of(FogWeatherRules.handOver(spell, startingBy(rolled.get(), now), now, settings));
			}
		}
		return new Outcome(m, spell);
	}

	/**
	 * The clock is before the last night roll ({@code nightRolled} past {@code night}) or the last day roll: puts back
	 * what each such roll changed (its {@link Undo}), the later one first, so the history and the spell are exactly what
	 * they were before them. What is still ahead after that (the clock went back further, or a memory with nothing to
	 * undo) rolls again as new: the night before the clock's and the day before the clock's count as the last rolled,
	 * and a spell that gives no fog yet is dropped unless it is a roll still standing, as rolled.
	 */
	private static Rewound rewind(Memory memory, Optional<FogSpell> current, long now, long night, long day) {
		Memory m = memory;
		Optional<FogSpell> spell = current;
		// At most one undo of each kind is kept, so two passes put back all there is.
		for (int i = 0; i < 2; i++) {
			Optional<Undo> nightUndo = m.nightRolled() > night ? m.undoNight() : Optional.empty();
			Optional<Undo> dayUndo = m.dayRolled() > day ? m.undoDay() : Optional.empty();
			if (nightUndo.isPresent() && (dayUndo.isEmpty() || nightUndo.get().at() >= dayUndo.get().at())) {
				Undo u = nightUndo.get();
				m = new Memory(u.history(), u.rolled(), m.dayRolled(), u.roll(), m.dayRoll(), Optional.empty(), m.undoDay());
				spell = u.spell();
			} else if (dayUndo.isPresent()) {
				Undo u = dayUndo.get();
				m = new Memory(u.history(), m.nightRolled(), u.rolled(), m.nightRoll(), u.roll(), m.undoNight(), Optional.empty());
				spell = u.spell();
			}
		}
		boolean nightAhead = m.nightRolled() > night;
		boolean dayAhead = m.dayRolled() > day;
		if (nightAhead || dayAhead) {
			m = new Memory(m.history(), nightAhead ? night - 1 : m.nightRolled(), dayAhead ? day - 1 : m.dayRolled(),
					nightAhead ? Optional.empty() : m.nightRoll(), dayAhead ? Optional.empty() : m.dayRoll(), nightAhead ? Optional.empty() : m.undoNight(),
					dayAhead ? Optional.empty() : m.undoDay());
			Memory kept = m;
			spell = spell.filter(s -> s.amount(now) > 0.0 || kept.nightRoll().equals(Optional.of(s)) || kept.dayRoll().equals(Optional.of(s)));
		}
		return new Rewound(m, spell);
	}

	/** A spell rolled after its start eases in from now instead (no fog popping in mid-ramp), with its own ease. */
	static FogSpell startingBy(FogSpell spell, long now) {
		return spell.fromClock() >= now ? spell : spell.withClocks(now, spell.untilClock());
	}

	/**
	 * The spell to keep when the fog must go: none if it gives no fog now; itself if it is already easing out (in its
	 * own fade, past the point where its ease in still rises); otherwise one that goes on from exactly the fog there is
	 * now and eases out over the spell's own fade, with its own ease (quiet: no boost). The settings are not read (the
	 * spell's ease counts; kept so older calls compile).
	 */
	public static Optional<FogSpell> fadeOut(Optional<FogSpell> spell, long now, FogWeatherRules.Settings settings) {
		if (spell.isEmpty()) {
			return spell;
		}
		FogSpell s = spell.get();
		double fog = s.amount(now);
		if (!(fog > 0.0)) {
			return Optional.empty();
		}
		// The ease out alone and the ease in alone: easing out once the first is the smaller (the amount only falls from here).
		double out = s.amount(now, 0.0, s.fadeTicks(), s.exponent());
		double in = s.amount(now, s.rampTicks(), 0.0, s.exponent());
		if (s.untilClock() - now <= s.fadeTicks() && out <= in) {
			return spell;
		}
		return Optional.of(new FogSpell(now - s.rampTicks() - 1, now + s.fadeTicks(), (float) fog, true, s.rampTicks(), s.fadeTicks(), s.exponent()));
	}

	/**
	 * The spell {@code /a1016 fog night|morning|day} sets: from now, at the stage's level, quiet or not, to the end of
	 * the night (23500), the morning (5500) or the day (11500) now or next (the next one when this one has less than the
	 * fade left), with these settings' ease. Store it joined to the current one ({@link FogWeatherRules#handOver}).
	 */
	public static FogSpell commandSpell(Kind kind, long now, Stage stage, boolean quiet, FogWeatherRules.Settings settings) {
		long time = Math.floorMod(now, DAY);
		long day = Math.floorDiv(now, DAY);
		long fade = settings.fadeTicks();
		return switch (kind) {
			case NIGHT -> new FogSpell(now, FogWeatherRules.nightUntil(time < FogWeatherRules.NIGHT_UNTIL - fade ? day : day + 1, false),
					(float) settings.nightLevel(stage), quiet, settings);
			case MORNING -> new FogSpell(now, FogWeatherRules.nightUntil(time < FogWeatherRules.MORNING_UNTIL - fade ? day - 1 : day, true),
					(float) settings.nightLevel(stage), quiet, settings);
			case DAY -> new FogSpell(now, FogWeatherRules.dayUntil(time < FogWeatherRules.DAY_UNTIL - fade ? day : day + 1),
					(float) settings.dayLevel(stage), quiet, settings);
		};
	}

	// --- live (server thread) ---

	/** No fog weather now: the world is quiet for good, or Ending D's last minute is giving back in view. */
	public static boolean quiet(MinecraftServer server) {
		return Gates.quietForGood(server) || HerobrineState.get(server).hasFlag(TraceService.LAST_MINUTE_FLAG);
	}

	/**
	 * Every second: rolls when a roll is due, clears what is over, fades out when the world goes quiet. Then the layer's
	 * night roll ({@link EmptyNight#check}, docs/SELF_CREATED.md 04), right after tonight's night roll; a failure there
	 * is logged and never stops the weather.
	 */
	static void check(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		boolean anyone = Gates.hasDayCycle(overworld) && !Services.watch().participants(server).isEmpty();
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		long now = overworld.getDefaultClockTime();
		Outcome outcome = step(AtmosphereData.get(server).fogWeather(), HerobrineState.get(server).effects().fogSpell(), now,
				HerobrineState.get(server).stage(), overworld.getRandom(), anyone, quiet(server), settings);
		apply(server, outcome);
		long night = nightIndex(now, settings);
		try {
			EmptyNight.check(server, night, outcome.memory().nightRolled() == night, nightRollTime(settings));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] sc_04 check failed", e);
		}
	}

	/** Keeps the outcome's memory and stores (and sends) its spell if it changed. */
	static void apply(MinecraftServer server, Outcome outcome) {
		AtmosphereData.get(server).setFogWeather(outcome.memory());
		if (!outcome.spell().equals(HerobrineState.get(server).effects().fogSpell())) {
			ClientEffects.setFogSpell(server, outcome.spell());
		}
	}

	/** {@code /a1016 fog night|morning|day}: a spell from now, joined to the fog there is. */
	static FogSpell set(MinecraftServer server, Kind kind, boolean quietSpell) {
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		long now = server.overworld().getDefaultClockTime();
		Optional<FogSpell> current = HerobrineState.get(server).effects().fogSpell();
		FogSpell joined = FogWeatherRules.handOver(current, commandSpell(kind, now, HerobrineState.get(server).stage(), quietSpell, settings), now,
				settings);
		ClientEffects.setFogSpell(server, Optional.of(joined));
		return joined;
	}

	/** {@code /a1016 fog clear}: the fog eases out from where it is (none at once if it gives none now). */
	static Optional<FogSpell> clear(MinecraftServer server) {
		long now = server.overworld().getDefaultClockTime();
		Optional<FogSpell> faded = fadeOut(HerobrineState.get(server).effects().fogSpell(), now, FogWeatherRules.settings());
		ClientEffects.setFogSpell(server, faded);
		return faded;
	}

	/** {@code /a1016 fog}: the clock, the spell, tonight's and today's roll, the history. */
	static List<String> status(MinecraftServer server) {
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		HerobrineState state = HerobrineState.get(server);
		Memory memory = AtmosphereData.get(server).fogWeather();
		long now = server.overworld().getDefaultClockTime();
		long today = Math.floorDiv(now, DAY);
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "now: %s, stage %s%s", when(now), state.stage(), quiet(server) ? "; no fog weather (quiet for good or the last minute)" : ""));
		Optional<FogSpell> spell = state.effects().fogSpell();
		lines.add("spell: " + spell.map(s -> describe(s, now, settings)).orElse("none"));
		String tonight = memory.nightRolled() == today ? memory.nightRoll().map(s -> "fog night " + level(s)).orElse("clear")
				: memory.nightRolled() > today ? "rolled ahead" : "rolls at " + nightRollTime(settings);
		lines.add(String.format(Locale.ROOT, "tonight (night of day %d): %s", today, tonight));
		long day = dayIndex(now, settings);
		String daylight = memory.dayRolled() == day ? memory.dayRoll().map(s -> "fog day " + level(s)).orElse("no fog day")
				: memory.dayRolled() > day ? "rolled ahead" : "rolls at " + FogWeatherRules.dayRollTime(settings) + " of day " + (day - 1);
		lines.add(String.format(Locale.ROOT, "daylight of day %d: %s", day, daylight));
		FogWeatherRules.History h = memory.history();
		lines.add(String.format(Locale.ROOT, "history: %d nights rolled, %d clear in a row, last night %s, last day %s", h.nights(), h.clearNights(),
				h.lastNightFoggy() ? "foggy" : "clear", h.lastDayFoggy() ? "foggy" : "clear"));
		return lines;
	}

	private static String level(FogSpell spell) {
		return String.format(Locale.ROOT, "%.2f%s, until %s", spell.level(), spell.quiet() ? " (quiet)" : "", when(spell.untilClock()));
	}

	private static String describe(FogSpell spell, long now, FogWeatherRules.Settings settings) {
		String phase = spell.fromClock() > now ? "starts in " + (spell.fromClock() - now) + " ticks"
				: spell.untilClock() - now <= spell.fadeTicks() ? "easing out" : now - spell.fromClock() < spell.rampTicks() ? "easing in" : "full";
		return String.format(Locale.ROOT, "%s to %s, level %.2f%s, fog now %.3f (%s%s; ramp %d, fade %d)", when(spell.fromClock()),
				when(spell.untilClock()), spell.level(), spell.quiet() ? ", quiet (no boost)" : "", spell.amount(now), phase,
				spell.counts(now, settings.fogGateShare()) ? ", counts" : "", spell.rampTicks(), spell.fadeTicks());
	}

	private static String when(long clock) {
		return String.format(Locale.ROOT, "day %d %d", Math.floorDiv(clock, DAY), Math.floorMod(clock, DAY));
	}
}
