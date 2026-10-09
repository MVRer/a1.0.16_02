package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.util.RandomSource;

/**
 * Dry runs of {@link DirectorBrain}: no world, no cards fire. Contexts fit by dice, sessions start and end by
 * dice, and everything the brain decides is recorded. Used by {@code /a1016 director sim}, by timewarp's summary
 * and by the game tests. {@link #check} replays the record against every pacing limit.
 */
public final class DirectorSim {
	public enum Kind { STAGE, SESSION_START, SESSION_END, DRAW, GIVE_UP, REFILL, FIRE, QUIET }

	/**
	 * One recorded decision.
	 *
	 * @param value tension after a fire, or tension before a quiet
	 * @param until end of an empty session (play ticks) or of a quiet (day ticks)
	 * @param flag  for a fire: the card has a fake version (the fake ratio's denominator); for a session start: empty
	 */
	public record Event(Kind kind, long play, long dayTicks, Stage stage, Tier tier, String cardId, boolean fake, double value,
			long until, boolean flag, String note) {
		public long day() {
			return Math.floorDiv(dayTicks, DirectorBrain.DAY_TICKS);
		}
	}

	/** One hour of the simulated run. */
	public static final class HourRow {
		public final int index;
		public final Map<Tier, Integer> fires = new EnumMap<>(Tier.class);
		public int fakes;
		public long quietTicks;
		public long emptyTicks;
		public Stage stage = Stage.ALONE;
		public double tension;

		HourRow(int index) {
			this.index = index;
			for (Tier tier : Tier.values()) {
				fires.put(tier, 0);
			}
		}
	}

	/** Knobs of a dry run. Ticks are play ticks. */
	public static final class Params {
		public double hours = 20;
		public long seed = 1;
		public double fitChance = 0.35;
		public double noSpotChance = 0.1;
		public long sessionMin = 45 * 60 * 20;
		public long sessionMax = 180 * 60 * 20;
		/** Keep the memory's current session going instead of starting with a join. */
		public boolean continueSession;
		/** One continuous stretch (timewarp): no leaves or joins. */
		public boolean singleSession;
		/** Constant attention for the whole run. */
		public double attention;
		/** Optional per-card override of the context dice (tests). */
		public Predicate<CardInfo> fits;

		public static Params from(DirectorConfig config, DirectorRules rules) {
			Params p = new Params();
			p.fitChance = config.simFitChance;
			p.noSpotChance = config.simNoSpotChance;
			p.sessionMin = Math.max(rules.tickInterval, Math.round(config.simSessionMinMinutes * 60 / 3600.0 * rules.hourTicks));
			p.sessionMax = Math.max(p.sessionMin, Math.round(config.simSessionMaxMinutes * 60 / 3600.0 * rules.hourTicks));
			return p;
		}
	}

	/** Everything a run produced. */
	public static final class Result {
		public final DirectorRules rules;
		public final Params params;
		public final DirectorMemory start;
		public final DirectorMemory memory;
		public final List<Event> events = new ArrayList<>();
		public final List<HourRow> hours = new ArrayList<>();
		public final long startPlay;
		public final long startDayTicks;
		public long endPlay;
		public Stage startStage;
		public Stage stage;
		public double tension;
		public List<String> violations = List.of();

		Result(DirectorRules rules, Params params, DirectorMemory start, DirectorMemory memory, long startPlay, long startDayTicks) {
			this.rules = rules;
			this.params = params;
			this.start = start;
			this.memory = memory;
			this.startPlay = startPlay;
			this.startDayTicks = startDayTicks;
		}

		public List<Event> fires() {
			return events.stream().filter(e -> e.kind() == Kind.FIRE).toList();
		}

		public long count(Kind kind) {
			return events.stream().filter(e -> e.kind() == kind).count();
		}

		public long firesOf(Tier tier) {
			return events.stream().filter(e -> e.kind() == Kind.FIRE && e.tier() == tier).count();
		}

		/** Play tick at which the run first entered this stage, or -1. */
		public long stageAt(Stage stage) {
			return events.stream().filter(e -> e.kind() == Kind.STAGE && e.stage() == stage).mapToLong(Event::play).findFirst().orElse(-1);
		}

		/** Empty sessions as [start, end) play ticks (end = where the session or run ended). */
		public List<long[]> emptySessions() {
			List<long[]> out = new ArrayList<>();
			long openStart = -1;
			for (Event e : events) {
				if (openStart >= 0 && (e.kind() == Kind.SESSION_END || e.kind() == Kind.SESSION_START)) {
					out.add(new long[] {openStart, e.play()});
					openStart = -1;
				}
				if (e.kind() == Kind.SESSION_START && e.flag()) {
					openStart = e.play();
				}
			}
			if (openStart >= 0) {
				out.add(new long[] {openStart, endPlay});
			}
			return out;
		}

		public List<String> summary() {
			List<String> lines = new ArrayList<>();
			long fires = count(Kind.FIRE);
			long fakeable = fires().stream().filter(Event::flag).count();
			lines.add(String.format(Locale.ROOT, "%.1fh dry run from %s (seed %d, tempo %s): %d fires (amb %d, min %d, maj %d, sig %d), fakes %d of %d",
					(endPlay - startPlay) / (double) rules.hourTicks, time(startPlay, rules), params.seed, rules.profile.tempo(), fires,
					firesOf(Tier.AMBIENT), firesOf(Tier.MINOR), firesOf(Tier.MAJOR), firesOf(Tier.SIGNATURE),
					fires().stream().filter(Event::fake).count(), fakeable));
			StringBuilder stages = new StringBuilder();
			for (Event e : events) {
				if (e.kind() == Kind.STAGE) {
					stages.append(' ').append(e.stage()).append('@').append(time(e.play(), rules));
				}
			}
			List<long[]> empties = emptySessions();
			lines.add(String.format(Locale.ROOT, "stage %s -> %s%s | quiets %d | sessions %d, empty %d | tension %.1f",
					startStage, stage, stages.length() == 0 ? "" : " (" + stages.toString().trim() + ")", count(Kind.QUIET),
					count(Kind.SESSION_START), empties.size(), tension));
			lines.add(violations.isEmpty() ? "limits: all respected" : "LIMITS BROKEN (" + violations.size() + "): " + violations.getFirst());
			return lines;
		}

		/** The full log: summary, hourly table, every event, every broken limit. */
		public List<String> log() {
			List<String> lines = new ArrayList<>(summary());
			lines.add("");
			lines.add("hour  stage      amb min maj sig fake  quiet  empty  tension");
			for (HourRow row : hours) {
				lines.add(String.format(Locale.ROOT, "h%03d  %-9s  %3d %3d %3d %3d %4d  %4dm  %4dm  %6.1f", row.index, row.stage,
						row.fires.get(Tier.AMBIENT), row.fires.get(Tier.MINOR), row.fires.get(Tier.MAJOR), row.fires.get(Tier.SIGNATURE),
						row.fakes, minutes(row.quietTicks, rules), minutes(row.emptyTicks, rules), row.tension));
			}
			lines.add("");
			for (Event e : events) {
				if (e.kind() == Kind.DRAW) {
					continue;
				}
				lines.add(String.format(Locale.ROOT, "%s day %d  %-13s %s", time(e.play(), rules), e.day(), e.kind(), describe(e, rules)));
			}
			lines.add("");
			if (violations.isEmpty()) {
				lines.add("limits: all respected");
			} else {
				lines.add("LIMITS BROKEN:");
				violations.forEach(v -> lines.add("  " + v));
			}
			return lines;
		}
	}

	private DirectorSim() {
	}

	/**
	 * Runs the brain over {@code params.hours} of play from a copy of {@code start}. Nothing outside the copy
	 * changes.
	 */
	public static Result run(DirectorRules rules, Collection<CardInfo> cards, int historySize, DirectorMemory start, Stage stage,
			double tension, DirectorBrain.Clock clock, Params params) {
		DirectorMemory memory = start.copy();
		Result result = new Result(rules, params, start.copy(), memory, clock.playTicks(), clock.dayTicks());
		result.startStage = stage;
		RandomSource random = RandomSource.create(mix(params.seed));
		SimEnv env = new SimEnv(stage, tension, params, random);
		DirectorBrain brain = new DirectorBrain(rules, cards, historySize);
		RecordingRecorder rec = new RecordingRecorder(result, env, rules);

		DirectorBrain.Clock c = clock;
		long end = clock.playTicks() + Math.round(params.hours * rules.hourTicks);
		rec.ensureRow(c.playTicks());
		if (!params.continueSession || memory.sessionStart < 0) {
			brain.onJoin(memory, c, env, random, rec);
		}
		long sessionEnd = params.singleSession ? Long.MAX_VALUE : c.playTicks() + pick(params.sessionMin, params.sessionMax, random);
		while (c.playTicks() + rules.tickInterval <= end) {
			c = c.plus(rules.tickInterval);
			if (c.playTicks() >= sessionEnd) {
				brain.onLeave(memory, c, rec);
				brain.onJoin(memory, c, env, random, rec);
				sessionEnd = c.playTicks() + pick(params.sessionMin, params.sessionMax, random);
			}
			brain.step(memory, c, env, random, rec);
			HourRow row = rec.ensureRow(c.playTicks());
			if (brain.inQuiet(memory, c)) {
				row.quietTicks += rules.tickInterval;
			}
			if (memory.sessionEmpty && c.playTicks() < memory.sessionEmptyUntil) {
				row.emptyTicks += rules.tickInterval;
			}
			row.stage = env.stage;
			row.tension = env.tension;
		}
		result.endPlay = c.playTicks();
		result.stage = env.stage;
		result.tension = env.tension;
		result.violations = check(result, cards);
		return result;
	}

	/** SplitMix64 finalizer: nearby seeds (1, 2, 3) give unrelated first rolls, unlike the bare LCG. */
	static long mix(long seed) {
		long z = seed + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static long pick(long min, long max, RandomSource random) {
		return max <= min ? min : min + (long) (random.nextDouble() * (max - min));
	}

	/** Replays the record against every pacing limit. Returns one line per broken limit. */
	public static List<String> check(Result result, Collection<CardInfo> cards) {
		DirectorRules rules = result.rules;
		DirectorMemory start = result.start;
		Map<String, CardInfo> byId = new HashMap<>();
		cards.forEach(card -> byId.put(card.id(), card));
		List<String> broken = new ArrayList<>();

		long sessionStart = start.sessionStart;
		long emptyUntil = start.sessionEmpty ? start.sessionEmptyUntil : -1;
		long quietUntil = start.quietUntilDayTicks;
		long lastMinor = start.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER);
		long lastMajor = start.lastMajorOrSignature;
		int aloneAmbients = start.aloneAmbientCount;
		Map<Tier, Set<String>> cycle = new EnumMap<>(Tier.class);
		Map<Tier, String> last = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			cycle.put(tier, new HashSet<>(start.cycleFired.get(tier)));
			if (start.lastFired.get(tier) != null) {
				last.put(tier, start.lastFired.get(tier));
			}
		}
		Set<String> once = new HashSet<>(start.signaturesFired);
		Event pendingQuiet = null;

		for (Event e : result.events) {
			String at = time(e.play(), rules) + " ";
			switch (e.kind()) {
				case SESSION_START -> {
					sessionStart = e.play();
					emptyUntil = e.flag() ? e.until() : -1;
					if (e.flag() && !e.stage().atLeast(Stage.TRACES)) {
						broken.add(at + "empty session rolled in " + e.stage());
					}
				}
				case SESSION_END -> {
					sessionStart = -1;
					emptyUntil = -1;
				}
				case REFILL -> cycle.get(e.tier()).clear();
				case QUIET -> {
					if (e.value() < rules.tensionThreshold) {
						broken.add(at + String.format(Locale.ROOT, "quiet started below the threshold (%.1f)", e.value()));
					}
					long days = (e.until() - e.dayTicks()) / DirectorBrain.DAY_TICKS;
					if (days < rules.quietMinDays || days > rules.quietMaxDays) {
						broken.add(at + "quiet of " + days + " days");
					}
					quietUntil = e.until();
					pendingQuiet = null;
				}
				case FIRE -> {
					if (pendingQuiet != null) {
						broken.add(time(pendingQuiet.play(), rules) + " tension reached the threshold without a quiet");
						pendingQuiet = null;
					}
					CardInfo card = byId.get(e.cardId());
					Tier tier = e.tier();
					long now = e.play();
					if (sessionStart >= 0 && now - sessionStart < rules.joinGrace) {
						broken.add(at + e.cardId() + " fired " + minutes(now - sessionStart, rules) + " min after joining");
					}
					if (e.dayTicks() < quietUntil) {
						broken.add(at + e.cardId() + " fired during a quiet");
					}
					if (emptyUntil > now) {
						broken.add(at + e.cardId() + " fired in an empty session");
					}
					if (tier == Tier.MINOR) {
						if (now - lastMinor < rules.minorGap) {
							broken.add(at + "minors " + minutes(now - lastMinor, rules) + " min apart");
						}
						if (!e.stage().atLeast(rules.minorMinStage)) {
							broken.add(at + "minor in " + e.stage());
						}
						lastMinor = now;
					}
					if (tier == Tier.MAJOR || tier == Tier.SIGNATURE) {
						if (now - lastMajor < rules.majorGap) {
							broken.add(at + "two majors " + minutes(now - lastMajor, rules) + " min apart");
						}
						if (e.day() < rules.noMajorBeforeDay) {
							broken.add(at + e.cardId() + " on day " + e.day());
						}
						if (tier == Tier.MAJOR && !e.stage().atLeast(rules.majorMinStage)) {
							broken.add(at + "major in " + e.stage());
						}
						lastMajor = now;
					}
					if (tier == Tier.AMBIENT && e.stage() == Stage.ALONE && ++aloneAmbients > rules.aloneMaxAmbient) {
						broken.add(at + "ambient number " + aloneAmbients + " in Alone");
					}
					if (card != null) {
						if (card.has(CardTag.ACCIDENT) && now < rules.firstAccident) {
							broken.add(at + "accident card " + e.cardId() + " before " + time(rules.firstAccident, rules));
						}
						if (!e.stage().atLeast(card.earliestStage())) {
							broken.add(at + e.cardId() + " before its stage " + card.earliestStage());
						}
						if (DirectorBrain.oncePerWorld(card) && !once.add(card.id())) {
							broken.add(at + "signature " + e.cardId() + " fired twice");
						}
						if (!card.hasFake() && e.fake()) {
							broken.add(at + e.cardId() + " fired as a fake but has none");
						}
					}
					if (e.cardId().equals(last.get(tier))) {
						broken.add(at + e.cardId() + " fired twice in a row");
					} else if (cycle.get(tier).contains(e.cardId())) {
						broken.add(at + e.cardId() + " repeated before its deck ran out");
					}
					cycle.get(tier).add(e.cardId());
					last.put(tier, e.cardId());
					if (e.value() >= rules.tensionThreshold) {
						pendingQuiet = e;
					}
				}
				default -> {
				}
			}
		}
		if (pendingQuiet != null) {
			broken.add(time(pendingQuiet.play(), rules) + " tension reached the threshold without a quiet");
		}
		return broken;
	}

	static String time(long play, DirectorRules rules) {
		long minutes = Math.round(play * 60.0 / rules.hourTicks);
		return String.format(Locale.ROOT, "%dh%02dm", minutes / 60, minutes % 60);
	}

	static long minutes(long ticks, DirectorRules rules) {
		return Math.round(ticks * 60.0 / rules.hourTicks);
	}

	private static String describe(Event e, DirectorRules rules) {
		return switch (e.kind()) {
			case STAGE -> "-> " + e.stage();
			case SESSION_START -> e.flag() ? "empty (" + e.stage() + ")" : "(" + e.stage() + ")";
			case SESSION_END -> "";
			case DRAW -> e.tier() + " " + e.cardId();
			case GIVE_UP -> e.tier() + " " + e.cardId() + ": " + e.note();
			case REFILL -> e.tier() + " deck refilled";
			case FIRE -> String.format(Locale.ROOT, "%-9s %s%s  tension %.1f", e.tier(), e.cardId(), e.fake() ? " (fake)" : "", e.value());
			case QUIET -> String.format(Locale.ROOT, "%d days, until day %d (tension was %.1f)",
					(e.until() - e.dayTicks()) / DirectorBrain.DAY_TICKS, Math.floorDiv(e.until(), DirectorBrain.DAY_TICKS), e.value());
		};
	}

	/** Dice instead of a world. */
	private static final class SimEnv implements DirectorBrain.Env {
		Stage stage;
		double tension;
		private final Params params;
		private final RandomSource random;

		SimEnv(Stage stage, double tension, Params params, RandomSource random) {
			this.stage = stage;
			this.tension = tension;
			this.params = params;
			this.random = random;
		}

		@Override
		public Stage stage() {
			return stage;
		}

		@Override
		public void setStage(Stage stage) {
			this.stage = stage;
		}

		@Override
		public double attention() {
			return params.attention;
		}

		@Override
		public double tension() {
			return tension;
		}

		@Override
		public void addTension(double delta, String reason) {
			tension = Math.max(0, Math.min(100, tension + delta));
		}

		@Override
		public boolean contextFits(CardInfo card) {
			return params.fits != null ? params.fits.test(card) : random.nextDouble() < params.fitChance;
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			return random.nextDouble() < params.noSpotChance ? FireResult.NO_SPOT : FireResult.FIRED;
		}
	}

	/** Turns brain callbacks into events and hourly rows. */
	private static final class RecordingRecorder implements DirectorBrain.Recorder {
		private final Result result;
		private final SimEnv env;
		private final DirectorRules rules;

		RecordingRecorder(Result result, SimEnv env, DirectorRules rules) {
			this.result = result;
			this.env = env;
			this.rules = rules;
		}

		HourRow ensureRow(long play) {
			int index = (int) Math.max(0, (play - result.startPlay - 1) / rules.hourTicks);
			while (result.hours.size() <= index) {
				HourRow row = new HourRow(result.hours.size());
				row.stage = env.stage;
				row.tension = env.tension;
				result.hours.add(row);
			}
			return result.hours.get(index);
		}

		private void add(Kind kind, DirectorBrain.Clock c, Stage stage, Tier tier, String id, boolean fake, double value, long until,
				boolean flag, String note) {
			result.events.add(new Event(kind, c.playTicks(), c.dayTicks(), stage, tier, id, fake, value, until, flag, note));
		}

		@Override
		public void stageChanged(DirectorBrain.Clock c, Stage from, Stage to) {
			add(Kind.STAGE, c, to, null, null, false, 0, -1, false, from.name());
		}

		@Override
		public void sessionStarted(DirectorBrain.Clock c, Stage stage, boolean empty, long emptyUntil) {
			add(Kind.SESSION_START, c, stage, null, null, false, 0, emptyUntil, empty, "");
		}

		@Override
		public void sessionEnded(DirectorBrain.Clock c) {
			add(Kind.SESSION_END, c, env.stage, null, null, false, 0, -1, false, "");
		}

		@Override
		public void drew(DirectorBrain.Clock c, CardInfo card) {
			add(Kind.DRAW, c, env.stage, card.tier(), card.id(), false, 0, -1, false, "");
		}

		@Override
		public void gaveUp(DirectorBrain.Clock c, Tier tier, String cardId, String why) {
			add(Kind.GIVE_UP, c, env.stage, tier, cardId, false, 0, -1, false, why);
		}

		@Override
		public void refilled(DirectorBrain.Clock c, Tier tier) {
			add(Kind.REFILL, c, env.stage, tier, null, false, 0, -1, false, "");
		}

		@Override
		public void fired(DirectorBrain.Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			add(Kind.FIRE, c, stage, card.tier(), card.id(), fake, tensionAfter, -1, card.hasFake(), forced ? "forced" : "");
			HourRow row = ensureRow(c.playTicks());
			row.fires.merge(card.tier(), 1, Integer::sum);
			if (fake) {
				row.fakes++;
			}
		}

		@Override
		public void quietStarted(DirectorBrain.Clock c, int days, long untilDayTicks, double tensionBefore) {
			add(Kind.QUIET, c, env.stage, null, null, false, tensionBefore, untilDayTicks, false, days + "d");
		}
	}
}
