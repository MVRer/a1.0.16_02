package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * Dry runs of {@link DirectorBrain}: no world, no cards fire. Contexts fit by dice, sessions start and end by
 * dice, and everything the brain decides is recorded. Used by {@code /a1016 director sim}, by timewarp's summary
 * and by the game tests. {@link #check} replays the record against every pacing limit. The director never enters
 * Telling on its own (D-041), so a run reaches Stage 3 only when {@link Params#tellingAtHour} names him.
 * <p>
 * {@link #run} plays one player. {@link #runGroup} plays several from a fresh world (D-059, D-060): one shared world
 * memory and stage, a pacing memory and a clock per player, everyone joining and leaving with the world's sessions
 * from their own join hour on, and a {@link Sharing} rule for who is close to whom. Each player gets a {@link Result}
 * of their own, on their own clock: their own fires, the moments they felt from someone else's cards
 * ({@link Kind#FELT}), their sessions and quiets, and the world's stage changes.
 * <p>
 * Fog weather (D-071): one sky for the table, rolled with core's {@link FogWeatherRules} on its own dice (so the
 * weather never shifts the director's), at the times atmosphere's live weather rolls it: tonight's fog night at
 * {@link FogWeatherRules#nightRollTime} ({@link FogWeatherRules#NIGHT_ROLL}, 10000) and a fog day at
 * {@link FogWeatherRules#DAY_ROLL} (23000) of the day before, joined with {@code handOver}. The run's day clock stands in
 * for the overworld clock. Each {@link Result} books its time and fires under each {@link Sky}.
 */
public final class DirectorSim {
	/** The player of a one-player dry run. */
	public static final UUID SOLO = new UUID(0L, 0xA1016L);

	/** The sky over a stretch of a run (D-071): no spell on, a quiet spell (no boost), or a fog spell that boosts. */
	public enum Sky { CLEAR, QUIET, FOG }

	/**
	 * {@code TELLING}: someone named him (a dry run's {@link Params#tellingAtHour}). {@code FELT}: someone else's card,
	 * felt. {@code MOOD}: meeting someone whose session started earlier, the session took its mood (empty or not).
	 */
	public enum Kind { STAGE, SESSION_START, SESSION_END, DRAW, GIVE_UP, REFILL, FIRE, FELT, QUIET, TELLING, MOOD }

	/**
	 * One recorded decision, on that player's clock.
	 *
	 * @param value tension after a fire or a felt moment, or tension before a quiet
	 * @param until end of an empty session (play ticks) or of a quiet (day ticks)
	 * @param flag  for a fire or a felt moment: the card has a fake version (the fake ratio's denominator); for a
	 *              session start: empty
	 */
	public record Event(Kind kind, long play, long dayTicks, Stage stage, Tier tier, String cardId, boolean fake, double value,
			long until, boolean flag, String note) {
		public long day() {
			return Math.floorDiv(dayTicks, DirectorBrain.DAY_TICKS);
		}

		/** A fire of this player's own or a moment felt from someone else's: what the player went through. */
		public boolean felt() {
			return kind == Kind.FIRE || kind == Kind.FELT;
		}
	}

	/** One hour of the simulated run. */
	public static final class HourRow {
		public final int index;
		public final Map<Tier, Integer> fires = new EnumMap<>(Tier.class);
		public int fakes;
		/** Moments felt from someone else's cards. */
		public int felt;
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
		/**
		 * Cards whose moment never comes in this run (a surface player's tunnel cards, a card that never fits): they never
		 * fit, whatever {@link #fits} or the dice say, so the run plays their holds, give-backs and parks. None by default.
		 */
		public Predicate<CardInfo> neverFits;
		/**
		 * Hours into the run at which someone names him: lore's naming {@code HerobrineEvents.TELLING}, so the
		 * run reaches Telling and Stage 3's pacing is exercised. Negative: never (the director alone never gets there).
		 */
		public double tellingAtHour = -1;
		/** Whose pacing a one-player run plays ({@link #run}); {@link #SOLO} by default. */
		public UUID player = SOLO;
		/** Roll fog weather (D-071); off, the sky stays as {@link #fogSpell} leaves it. */
		public boolean fogWeather = true;
		/** The weather's numbers: D-071's by default (the live dry runs use the installed ones). */
		public FogWeatherRules.Settings fogSettings = FogWeatherRules.Settings.DEFAULT;
		/** The spell already stored when the run starts, on the run's day clock. */
		public Optional<FogSpell> fogSpell = Optional.empty();
		/** The weather's history when the run starts (a fresh world's by default). */
		public FogWeatherRules.History fogHistory = FogWeatherRules.History.START;

		public static Params from(DirectorConfig config, DirectorRules rules) {
			Params p = new Params();
			p.fitChance = config.simFitChance;
			p.noSpotChance = config.simNoSpotChance;
			p.sessionMin = Math.max(rules.tickInterval, Math.round(config.simSessionMinMinutes * 60 / 3600.0 * rules.hourTicks));
			p.sessionMax = Math.max(p.sessionMin, Math.round(config.simSessionMaxMinutes * 60 / 3600.0 * rules.hourTicks));
			p.tellingAtHour = config.simTellingAtHour;
			return p;
		}
	}

	/** A player of a group run: who, and how many hours into the run they first take part (0: from the start). */
	public record Seat(UUID player, double joinAtHour) {
	}

	/** Who shares moments with whom in a group run (D-060), asked before every fire. */
	@FunctionalInterface
	public interface Sharing {
		/** Nobody is ever close to anyone. */
		Sharing APART = (worldTicks, a, b) -> false;
		/** Everyone online is always close to everyone. */
		Sharing TOGETHER = (worldTicks, a, b) -> true;

		boolean together(long worldTicks, UUID a, UUID b);
	}

	/** Everything a run produced for one player, on their own clock. */
	public static final class Result {
		public final DirectorRules rules;
		public final Params params;
		public final UUID player;
		/** Copies taken when this player's run started (for a late joiner: when they joined). */
		public final WorldMemory startWorld;
		public final PacingMemory startPacing;
		/** The run's world memory (shared by every player of a group run) and this player's pacing, at the end. */
		public final WorldMemory world;
		public final PacingMemory pacing;
		public final List<Event> events = new ArrayList<>();
		public final List<HourRow> hours = new ArrayList<>();
		public final long startPlay;
		public final long startDayTicks;
		public long endPlay;
		public Stage startStage;
		public Stage stage;
		public double tension;
		public List<String> violations = List.of();
		/** Every fog spell the table stored while this player took part, in order (a hand-over stores the joined spell). */
		public final List<FogSpell> fogSpells = new ArrayList<>();
		/** Per stage: play ticks [all, active], sampled at every director tick after its decision. */
		private final Map<Stage, long[]> stageTime = new EnumMap<>(Stage.class);
		/** Per sky: play ticks [all, active], sampled like {@link #stageTime}. */
		private final Map<Sky, long[]> skyTime = new EnumMap<>(Sky.class);
		/** Per sky: this player's own fires (fakes included) under it. */
		private final Map<Sky, Integer> skyFires = new EnumMap<>(Sky.class);

		Result(DirectorRules rules, Params params, UUID player, WorldMemory world, PacingMemory pacing, long startPlay, long startDayTicks) {
			this.rules = rules;
			this.params = params;
			this.player = player;
			this.startWorld = world.copy();
			this.startPacing = pacing.copy();
			this.world = world;
			this.pacing = pacing;
			this.startPlay = startPlay;
			this.startDayTicks = startDayTicks;
		}

		/** This player's own fires. */
		public List<Event> fires() {
			return events.stream().filter(e -> e.kind() == Kind.FIRE).toList();
		}

		/** What this player went through: their own fires and the moments they felt from others, in order. */
		public List<Event> felt() {
			return events.stream().filter(Event::felt).toList();
		}

		public long count(Kind kind) {
			return events.stream().filter(e -> e.kind() == kind).count();
		}

		public long firesOf(Tier tier) {
			return events.stream().filter(e -> e.kind() == Kind.FIRE && e.tier() == tier).count();
		}

		/** Play ticks the run spent in this stage. */
		public long stageTicks(Stage stage) {
			long[] time = stageTime.get(stage);
			return time == null ? 0 : time[0];
		}

		/** Play ticks the run spent in this stage outside quiets and empty sessions. */
		public long activeTicks(Stage stage) {
			long[] time = stageTime.get(stage);
			return time == null ? 0 : time[1];
		}

		/** Play ticks the run spent under this sky (D-071). */
		public long skyTicks(Sky sky) {
			long[] time = skyTime.get(sky);
			return time == null ? 0 : time[0];
		}

		/** Play ticks the run spent under this sky outside quiets and empty sessions. */
		public long skyActiveTicks(Sky sky) {
			long[] time = skyTime.get(sky);
			return time == null ? 0 : time[1];
		}

		/** This player's own fires (fakes included) under this sky. */
		public int skyFires(Sky sky) {
			return skyFires.getOrDefault(sky, 0);
		}

		/** Play tick at which the run first entered this stage, or -1. */
		public long stageAt(Stage stage) {
			return events.stream().filter(e -> e.kind() == Kind.STAGE && e.stage() == stage).mapToLong(Event::play).findFirst().orElse(-1);
		}

		/** Empty stretches as [start, end) play ticks: from the session start to its end or the empty cap. */
		public List<long[]> emptySessions() {
			List<long[]> out = new ArrayList<>();
			long openStart = -1;
			long openUntil = -1;
			if (params.continueSession && startPacing.sessionStart >= 0 && startPacing.sessionEmpty && startPacing.sessionEmptyUntil > startPlay) {
				openStart = startPlay;
				openUntil = startPacing.sessionEmptyUntil;
			}
			for (Event e : events) {
				if (openStart >= 0 && (e.kind() == Kind.SESSION_END || e.kind() == Kind.SESSION_START || e.kind() == Kind.MOOD)) {
					out.add(new long[] {openStart, Math.min(e.play(), openUntil)});
					openStart = -1;
				}
				if ((e.kind() == Kind.SESSION_START || e.kind() == Kind.MOOD) && e.flag()) {
					openStart = e.play();
					openUntil = e.until();
				}
			}
			if (openStart >= 0) {
				out.add(new long[] {openStart, Math.min(endPlay, openUntil)});
			}
			return out;
		}

		public List<String> summary() {
			List<String> lines = new ArrayList<>();
			long fires = count(Kind.FIRE);
			long fakeable = fires().stream().filter(Event::flag).count();
			long felt = count(Kind.FELT);
			lines.add(String.format(Locale.ROOT, "%.1fh dry run from %s (seed %d, tempo %s): %d fires (amb %d, min %d, maj %d, sig %d), fakes %d of %d%s",
					(endPlay - startPlay) / (double) rules.hourTicks, time(startPlay, rules), params.seed, rules.profile.tempo(), fires,
					firesOf(Tier.AMBIENT), firesOf(Tier.MINOR), firesOf(Tier.MAJOR), firesOf(Tier.SIGNATURE),
					fires().stream().filter(Event::fake).count(), fakeable, felt == 0 ? "" : ", felt " + felt + " from others"));
			StringBuilder stages = new StringBuilder();
			for (Event e : events) {
				if (e.kind() == Kind.STAGE) {
					stages.append(' ').append(e.stage()).append('@').append(time(e.play(), rules));
				}
			}
			List<long[]> empties = emptySessions();
			lines.add(String.format(Locale.ROOT, "stage %s -> %s%s | quiets %d | sessions %d, empty %d | tension %.1f | fog spells %d (%d quiet), %.1fh boosted",
					startStage, stage, stages.length() == 0 ? "" : " (" + stages.toString().trim() + ")", count(Kind.QUIET),
					count(Kind.SESSION_START), empties.size(), tension, fogSpells.size(), fogSpells.stream().filter(FogSpell::quiet).count(),
					skyTicks(Sky.FOG) / (double) rules.hourTicks));
			lines.add(violations.isEmpty() ? "limits: all respected" : "LIMITS BROKEN (" + violations.size() + "): " + violations.getFirst());
			return lines;
		}

		/** The full log: summary, hourly table, every event, every broken limit. */
		public List<String> log() {
			List<String> lines = new ArrayList<>(summary());
			lines.add("");
			lines.add("hour  stage      amb min maj sig fake  quiet  empty  tension");
			for (HourRow row : hours) {
				lines.add(String.format(Locale.ROOT, "h%03d  %-9s  %3d %3d %3d %3d %4d  %4dm  %4dm  %6.1f%s", row.index, row.stage,
						row.fires.get(Tier.AMBIENT), row.fires.get(Tier.MINOR), row.fires.get(Tier.MAJOR), row.fires.get(Tier.SIGNATURE),
						row.fakes, minutes(row.quietTicks, rules), minutes(row.emptyTicks, rules), row.tension, row.felt == 0 ? "" : "  felt " + row.felt));
			}
			lines.add("");
			for (Event e : events) {
				if (e.kind() == Kind.DRAW) {
					continue;
				}
				lines.add(String.format(Locale.ROOT, "%s day %d  %-13s %s", time(e.play(), rules), e.day(), e.kind(), describe(e, rules)));
			}
			lines.add("");
			for (FogSpell spell : fogSpells) {
				lines.add(String.format(Locale.ROOT, "fog spell day %d %05d to day %d %05d, level %.2f%s", Math.floorDiv(spell.fromClock(), DirectorBrain.DAY_TICKS),
						Math.floorMod(spell.fromClock(), DirectorBrain.DAY_TICKS), Math.floorDiv(spell.untilClock(), DirectorBrain.DAY_TICKS),
						Math.floorMod(spell.untilClock(), DirectorBrain.DAY_TICKS), spell.level(), spell.quiet() ? " (quiet)" : ""));
			}
			for (Sky sky : Sky.values()) {
				double hours = skyTicks(sky) / (double) rules.hourTicks;
				lines.add(String.format(Locale.ROOT, "sky %-5s %.1fh, %d fires (%.2f/h)", sky, hours, skyFires(sky), hours <= 0 ? 0 : skyFires(sky) / hours));
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

	/**
	 * A group run: one {@link Result} per player (in seat order), the shared world memory, the world's own stage and
	 * telling events (on the world clock) and the group's broken limits ({@link #checkGroup}).
	 */
	public static final class Group {
		public final List<Result> players;
		public final WorldMemory world;
		public final List<Event> worldEvents;
		public List<String> violations = List.of();

		Group(List<Result> players, WorldMemory world, List<Event> worldEvents) {
			this.players = List.copyOf(players);
			this.world = world;
			this.worldEvents = List.copyOf(worldEvents);
		}

		public @Nullable Result player(UUID player) {
			return players.stream().filter(r -> r.player.equals(player)).findFirst().orElse(null);
		}

		/** Every player's own and the group's broken limits, one line each. */
		public List<String> allViolations() {
			List<String> out = new ArrayList<>(violations);
			for (int i = 0; i < players.size(); i++) {
				for (String v : players.get(i).violations) {
					out.add("player " + (i + 1) + ": " + v);
				}
			}
			return out;
		}
	}

	/**
	 * The soft rates of DESIGN.md 4b pooled over several runs: counts summed over time summed, so long and short
	 * stages weigh by their length. "Active" time leaves out quiets and empty sessions, as the playthrough's rate
	 * checks do. Majors count the MAJOR tier only (no signatures), as the playthrough does.
	 *
	 * @param silentSessions sessions that began in Traces or later, lasted at least {@code emptySessionMin} and
	 *                       saw nothing fire (rolled empty or not)
	 */
	public record Rates(int runs, long hourTicks, long tracesTicks, long tracesActiveTicks, int tracesAmbients, long proximityTicks,
			long proximityActiveTicks, int proximityMinors, int proximityMajors, int sessionsFromTraces, int emptySessions, int silentSessions,
			int quiets) {
		/** Pools these runs' own fires. They should share one config (one hour length). */
		public static Rates of(Collection<Result> results) {
			return of(results, false);
		}

		/** Pools what each player felt: their own fires and the moments they shared from someone else's (D-060.3). */
		public static Rates felt(Collection<Result> results) {
			return of(results, true);
		}

		private static Rates of(Collection<Result> results, boolean withFelt) {
			int runs = 0;
			long hourTicks = 1;
			long tracesTicks = 0;
			long tracesActive = 0;
			int tracesAmbients = 0;
			long proximityTicks = 0;
			long proximityActive = 0;
			int minors = 0;
			int majors = 0;
			int sessions = 0;
			int empty = 0;
			int silent = 0;
			int quiets = 0;
			for (Result r : results) {
				runs++;
				hourTicks = r.rules.hourTicks;
				tracesTicks += r.stageTicks(Stage.TRACES);
				tracesActive += r.activeTicks(Stage.TRACES);
				proximityTicks += r.stageTicks(Stage.PROXIMITY);
				proximityActive += r.activeTicks(Stage.PROXIMITY);
				long openStart = -1;
				boolean openCounts = false;
				int openFires = 0;
				for (Event e : r.events) {
					Kind kind = e.kind() == Kind.FELT && withFelt ? Kind.FIRE : e.kind();
					switch (kind) {
						case FIRE -> {
							openFires++;
							if (e.stage() == Stage.TRACES && e.tier() == Tier.AMBIENT) {
								tracesAmbients++;
							} else if (e.stage() == Stage.PROXIMITY && e.tier() == Tier.MINOR) {
								minors++;
							} else if (e.stage() == Stage.PROXIMITY && e.tier() == Tier.MAJOR) {
								majors++;
							}
						}
						case QUIET -> quiets++;
						case SESSION_START, SESSION_END -> {
							if (openStart >= 0 && openCounts && openFires == 0 && e.play() - openStart >= r.rules.emptySessionMin) {
								silent++;
							}
							openStart = -1;
							if (e.kind() == Kind.SESSION_START) {
								openStart = e.play();
								openCounts = e.stage().atLeast(Stage.TRACES);
								openFires = 0;
								if (openCounts) {
									sessions++;
									empty += e.flag() ? 1 : 0;
								}
							}
						}
						default -> {
						}
					}
				}
				if (openStart >= 0 && openCounts && openFires == 0 && r.endPlay - openStart >= r.rules.emptySessionMin) {
					silent++;
				}
			}
			return new Rates(runs, hourTicks, tracesTicks, tracesActive, tracesAmbients, proximityTicks, proximityActive, minors, majors, sessions,
					empty, silent, quiets);
		}

		private double hours(long ticks) {
			return ticks / (double) hourTicks;
		}

		private static double per(double count, double hours) {
			return hours <= 0 ? 0 : count / hours;
		}

		public double tracesAmbientsPerHour() {
			return per(tracesAmbients, hours(tracesTicks));
		}

		public double tracesAmbientsPerActiveHour() {
			return per(tracesAmbients, hours(tracesActiveTicks));
		}

		public double minorsPerHour() {
			return per(proximityMinors, hours(proximityTicks));
		}

		public double minorsPerActiveHour() {
			return per(proximityMinors, hours(proximityActiveTicks));
		}

		/** Hours of Proximity per major, or infinity if none fired. */
		public double hoursPerMajor() {
			return proximityMajors == 0 ? Double.POSITIVE_INFINITY : hours(proximityTicks) / proximityMajors;
		}

		public double activeHoursPerMajor() {
			return proximityMajors == 0 ? Double.POSITIVE_INFINITY : hours(proximityActiveTicks) / proximityMajors;
		}

		/** Majors per hour of Proximity (0 if none). */
		public double majorsPerHour() {
			return per(proximityMajors, hours(proximityTicks));
		}

		/** Share of Proximity spent in quiets or empty sessions. */
		public double proximityQuietShare() {
			return proximityTicks <= 0 ? 0 : 1 - proximityActiveTicks / (double) proximityTicks;
		}

		/** One line, every number in {@code Locale.ROOT}. */
		public String summary() {
			return String.format(Locale.ROOT,
					"%d runs | Traces %.1fh: %.2f amb/h (%.2f active) | Proximity %.1fh (%.0f%% quiet or empty): minors %.2f/h (%.2f active), "
							+ "a major every %.2fh (%.2fh active) | sessions from Traces %d, empty %d, silent 45+ min %d | quiets %d",
					runs, hours(tracesTicks), tracesAmbientsPerHour(), tracesAmbientsPerActiveHour(), hours(proximityTicks), proximityQuietShare() * 100,
					minorsPerHour(), minorsPerActiveHour(), hoursPerMajor(), activeHoursPerMajor(), sessionsFromTraces, emptySessions, silentSessions,
					quiets);
		}
	}

	private DirectorSim() {
	}

	/**
	 * Runs the brain over {@code params.hours} of play for one player ({@link Params#player}) from a copy of the
	 * world part and that player's pacing in {@code start} (a fresh pacing if they have none). Nothing outside the
	 * copy changes.
	 */
	public static Result run(DirectorRules rules, Collection<CardInfo> cards, int historySize, DirectorMemory start, Stage stage,
			double tension, DirectorBrain.Clock clock, Params params) {
		PacingMemory pacing = start.find(params.player).orElseGet(() -> new PacingMemory(clock.dayTicks()));
		return run(rules, cards, historySize, start.world(), pacing, stage, tension, clock, params);
	}

	/**
	 * Runs the brain over {@code params.hours} of play for one player from copies of this world part and pacing,
	 * starting at {@code clock} (their own play ticks, the day ticks and the world clock) with this tension.
	 */
	public static Result run(DirectorRules rules, Collection<CardInfo> cards, int historySize, WorldMemory world, PacingMemory pacing,
			Stage stage, double tension, DirectorBrain.Clock clock, Params params) {
		Table table = new Table(rules, cards, historySize, world.copy(), stage, clock.worldTicks(), clock.dayTicks(), Sharing.APART, params);
		table.seats.add(new SeatRun(table, params.player, clock.worldTicks(), pacing.copy(), clock.playTicks(), tension));
		table.play(cards);
		return table.seats.getFirst().result;
	}

	/**
	 * Plays several players from a fresh world (play tick 0, day 0, Alone, nobody's tension): each seat first takes
	 * part at its own hour, with a fresh pacing whose first in-game day starts then, and then plays every world
	 * session to the end. {@code sharing} says who feels whose cards. Same arguments, same run.
	 */
	public static Group runGroup(DirectorRules rules, Collection<CardInfo> cards, int historySize, List<Seat> seats, Sharing sharing,
			Params params) {
		Table table = new Table(rules, cards, historySize, new WorldMemory(), Stage.ALONE, 0, 0, sharing, params);
		for (Seat seat : seats) {
			long joinAt = Math.max(0, Math.round(seat.joinAtHour() * rules.hourTicks));
			table.seats.add(new SeatRun(table, seat.player(), joinAt, null, 0, 0));
		}
		table.play(cards);
		List<Result> results = new ArrayList<>();
		for (SeatRun seat : table.seats) {
			if (seat.result != null) {
				results.add(seat.result);
			}
		}
		Group group = new Group(results, table.world, table.worldEvents);
		group.violations = checkGroup(group, cards);
		return group;
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

	/**
	 * Replays one player's record against every pacing limit: their own fires and the moments they felt from others
	 * alike, since a shared moment must obey everything theirs would (D-060.3). Returns one line per broken limit.
	 */
	public static List<String> check(Result result, Collection<CardInfo> cards) {
		DirectorRules rules = result.rules;
		PacingMemory start = result.startPacing;
		Map<String, CardInfo> byId = new HashMap<>();
		cards.forEach(card -> byId.put(card.id(), card));
		List<String> broken = new ArrayList<>();

		long sessionStart = start.sessionStart;
		long emptyUntil = start.sessionEmpty ? start.sessionEmptyUntil : -1;
		long quietUntil = start.quietUntilDayTicks;
		long lastMinor = start.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER);
		long lastMajor = start.lastMajorOrSignature;
		long firstDayEnd = start.firstDayTicks + (long) rules.noMajorBeforeDay * DirectorBrain.DAY_TICKS;
		int aloneAmbients = start.aloneAmbientCount;
		int firstHour = start.firstHourFires;
		Map<Tier, Set<String>> cycle = new EnumMap<>(Tier.class);
		Map<Tier, String> last = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			cycle.put(tier, new HashSet<>(start.cycleFired.get(tier)));
			if (start.lastFired.get(tier) != null) {
				last.put(tier, start.lastFired.get(tier));
			}
		}
		Set<String> once = new HashSet<>(result.startWorld.signaturesFired);
		Event pendingQuiet = null;
		Stage stage = result.startStage;
		long namedAt = -1;

		for (Event e : result.events) {
			String at = time(e.play(), rules) + " ";
			switch (e.kind()) {
				case TELLING -> namedAt = e.play();
				case STAGE -> {
					// D-041: Telling only from a telling that names him, never Removal from here, never back.
					if (e.stage() == Stage.TELLING && namedAt != e.play()) {
						broken.add(at + "Telling started without anyone naming him");
					}
					if (e.stage() == Stage.REMOVAL) {
						broken.add(at + "the director moved into Removal");
					}
					if (stage != null && e.stage().level() <= stage.level()) {
						broken.add(at + "stage went from " + stage + " to " + e.stage());
					}
					stage = e.stage();
				}
				case SESSION_START -> {
					sessionStart = e.play();
					emptyUntil = e.flag() ? e.until() : -1;
					if (e.flag() && !e.stage().atLeast(rules.emptySessionMinStage)) {
						broken.add(at + "empty session rolled in " + e.stage());
					}
				}
				case SESSION_END -> {
					sessionStart = -1;
					emptyUntil = -1;
				}
				case MOOD -> emptyUntil = e.flag() ? e.until() : -1;
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
				case FIRE, FELT -> {
					if (pendingQuiet != null) {
						broken.add(time(pendingQuiet.play(), rules) + " tension reached the threshold without a quiet");
						pendingQuiet = null;
					}
					CardInfo card = byId.get(e.cardId());
					Tier tier = e.tier();
					long now = e.play();
					String what = e.kind() == Kind.FELT ? e.cardId() + " (felt)" : e.cardId();
					if (sessionStart >= 0 && now - sessionStart < rules.joinGrace) {
						broken.add(at + what + " fired " + minutes(now - sessionStart, rules) + " min after joining");
					}
					if (e.dayTicks() < quietUntil) {
						broken.add(at + what + " fired during a quiet");
					}
					if (e.day() < rules.silenceUntilDay) {
						broken.add(at + what + " fired while the director is silenced");
					}
					if (emptyUntil > now) {
						broken.add(at + what + " fired in an empty session");
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
						if (e.dayTicks() < firstDayEnd) {
							broken.add(at + what + " on day " + e.day() + " (their first in-game day)");
						}
						if (tier == Tier.MAJOR && !e.stage().atLeast(rules.majorMinStage)) {
							broken.add(at + "major in " + e.stage());
						}
						if (tier == Tier.SIGNATURE && !e.stage().atLeast(rules.signatureMinStage)) {
							broken.add(at + "signature in " + e.stage());
						}
						lastMajor = now;
					}
					if (tier == Tier.AMBIENT && e.stage() == Stage.ALONE && ++aloneAmbients > rules.aloneMaxAmbient) {
						broken.add(at + "ambient number " + aloneAmbients + " in Alone");
					}
					if (rules.firstHourMaxFires >= 0 && now < rules.hourTicks && !"forced".equals(e.note()) && ++firstHour > rules.firstHourMaxFires) {
						broken.add(at + "moment number " + firstHour + " in their first hour");
					}
					if (card != null) {
						if (card.has(CardTag.ACCIDENT) && now < rules.firstAccident) {
							broken.add(at + "accident card " + what + " before " + time(rules.firstAccident, rules));
						}
						if (!e.stage().atLeast(card.earliestStage())) {
							broken.add(at + what + " before its stage " + card.earliestStage());
						}
						if (DirectorBrain.oncePerWorld(card) && !once.add(card.id())) {
							broken.add(at + "signature " + what + " fired twice");
						}
						if (!card.hasFake() && e.fake()) {
							broken.add(at + what + " fired as a fake but has none");
						}
					}
					if (e.cardId().equals(last.get(tier))) {
						broken.add(at + what + " fired twice in a row");
					} else if (cycle.get(tier).contains(e.cardId())) {
						broken.add(at + what + " repeated before its deck ran out");
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

	/**
	 * The group's own limits, beyond each player's ({@link #check}): every once-per-world card fired at most once
	 * whoever got it, every stage change happened once for the world, and nobody felt a card that nobody fired.
	 */
	public static List<String> checkGroup(Group group, Collection<CardInfo> cards) {
		List<String> broken = new ArrayList<>();
		Map<String, CardInfo> byId = new HashMap<>();
		cards.forEach(card -> byId.put(card.id(), card));
		DirectorRules rules = group.players.isEmpty() ? null : group.players.getFirst().rules;
		Set<String> once = new HashSet<>();
		Set<Stage> stages = new HashSet<>();
		for (Event e : group.worldEvents) {
			if (e.kind() == Kind.STAGE && !stages.add(e.stage())) {
				broken.add("the world moved into " + e.stage() + " twice");
			}
		}
		int fired = 0;
		int felt = 0;
		for (Result r : group.players) {
			for (Event e : r.events) {
				if (e.kind() == Kind.FIRE) {
					fired++;
					CardInfo card = byId.get(e.cardId());
					if (card != null && DirectorBrain.oncePerWorld(card) && !once.add(card.id())) {
						broken.add((rules == null ? "" : time(e.play(), rules) + " ") + "once-per-world " + e.cardId() + " fired for a second player");
					}
				} else if (e.kind() == Kind.FELT) {
					felt++;
				}
			}
		}
		if (felt > 0 && fired == 0) {
			broken.add(felt + " moments felt but nothing fired");
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
			case MOOD -> e.flag() ? "now empty, as the session met" : "no longer empty, as the session met";
			case DRAW -> e.tier() + " " + e.cardId();
			case GIVE_UP -> e.tier() + " " + e.cardId() + ": " + e.note();
			case REFILL -> e.tier() + " deck refilled";
			case FIRE -> String.format(Locale.ROOT, "%-9s %s%s  tension %.1f", e.tier(), e.cardId(), e.fake() ? " (fake)" : "", e.value());
			case FELT -> String.format(Locale.ROOT, "%-9s %s%s  tension %.1f (someone else's)", e.tier(), e.cardId(), e.fake() ? " (fake)" : "",
					e.value());
			case QUIET -> String.format(Locale.ROOT, "%d days, until day %d (tension was %.1f)",
					(e.until() - e.dayTicks()) / DirectorBrain.DAY_TICKS, Math.floorDiv(e.until(), DirectorBrain.DAY_TICKS), e.value());
			case TELLING -> "someone named him (in " + e.stage() + ")";
		};
	}

	/** The run's shared state: the world, the stage, the clocks, the seats and the dice. */
	private static final class Table {
		final DirectorRules rules;
		final Params params;
		final DirectorBrain brain;
		final RandomSource random;
		final WorldMemory world;
		final Sharing sharing;
		final List<SeatRun> seats = new ArrayList<>();
		final List<Event> worldEvents = new ArrayList<>();
		final long worldStart;
		Stage stage;
		long worldTicks;
		long dayTicks;
		/** The sky (D-071): its own dice, the stored spell, the rules' history and the day clock it was last rolled up to. */
		final RandomSource weather;
		Optional<FogSpell> fogSpell;
		FogWeatherRules.History fogHistory;
		long weatherClock;

		Table(DirectorRules rules, Collection<CardInfo> cards, int historySize, WorldMemory world, Stage stage, long worldTicks, long dayTicks,
				Sharing sharing, Params params) {
			this.rules = rules;
			this.params = params;
			this.brain = new DirectorBrain(rules, cards, historySize);
			this.random = RandomSource.create(mix(params.seed));
			this.weather = RandomSource.create(mix(params.seed ^ 0xF065_9E11L));
			this.world = world;
			this.stage = stage;
			this.worldStart = worldTicks;
			this.worldTicks = worldTicks;
			this.dayTicks = dayTicks;
			this.sharing = sharing;
			this.fogSpell = params.fogSpell == null ? Optional.empty() : params.fogSpell;
			this.fogHistory = params.fogHistory == null ? FogWeatherRules.History.START : params.fogHistory;
			this.weatherClock = dayTicks;
		}

		/** The spell on right now, or empty. */
		Optional<FogSpell> spellNow() {
			return fogSpell.filter(spell -> spell.activeAt(dayTicks));
		}

		Sky sky() {
			Optional<FogSpell> on = spellNow();
			return on.isEmpty() ? Sky.CLEAR : on.get().quiet() ? Sky.QUIET : Sky.FOG;
		}

		/**
		 * Rolls the fog weather due since the last call, as atmosphere's live weather does (D-071): at
		 * {@link FogWeatherRules#nightRollTime} (10000) tonight's fog night (or a clear night), at
		 * {@link FogWeatherRules#DAY_ROLL} the next day's fog day (never when a morning carry-over already runs into it);
		 * a spell rolled while another still gives fog is joined to it ({@code handOver}). The stage is the world's at the
		 * roll.
		 */
		void rollWeather() {
			if (!params.fogWeather) {
				return;
			}
			FogWeatherRules.Settings settings = params.fogSettings == null ? FogWeatherRules.Settings.DEFAULT : params.fogSettings;
			long nightRoll = FogWeatherRules.nightRollTime(settings);
			long dayRoll = FogWeatherRules.DAY_ROLL;
			while (true) {
				long base = Math.floorDiv(weatherClock, DirectorBrain.DAY_TICKS) * DirectorBrain.DAY_TICKS;
				long night = base + nightRoll > weatherClock ? base + nightRoll : base + DirectorBrain.DAY_TICKS + nightRoll;
				long day = base + dayRoll > weatherClock ? base + dayRoll : base + DirectorBrain.DAY_TICKS + dayRoll;
				long next = Math.min(night, day);
				if (next > dayTicks) {
					return;
				}
				weatherClock = next;
				long clockDay = Math.floorDiv(next, DirectorBrain.DAY_TICKS);
				Optional<FogSpell> rolled;
				if (next == night) {
					rolled = FogWeatherRules.night(weather, clockDay, stage, fogHistory, settings);
					fogHistory = fogHistory.afterNight(rolled.isPresent());
				} else {
					rolled = FogWeatherRules.day(weather, clockDay + 1, stage, fogHistory, fogSpell, settings);
					fogHistory = fogHistory.afterDay(rolled.isPresent());
				}
				if (rolled.isPresent()) {
					FogSpell stored = FogWeatherRules.handOver(fogSpell, rolled.get(), next, settings);
					fogSpell = Optional.of(stored);
					for (SeatRun seat : online()) {
						seat.result.fogSpells.add(stored);
					}
				}
			}
		}

		List<SeatRun> online() {
			return seats.stream().filter(s -> s.result != null).toList();
		}

		/**
		 * Seats join, sessions turn over, someone names him, and every online player decides each director tick. The
		 * run's hours, each seat's join hour and the naming hour count on the run's own time; the world clock, the day
		 * and with them the stage only move while someone is seated, as the live world clock stops while nobody takes
		 * part. A seat decides from one director tick after it joins, as the live director schedules a newcomer.
		 */
		void play(Collection<CardInfo> cards) {
			long interval = rules.tickInterval;
			long end = worldStart + Math.round(params.hours * rules.hourTicks);
			long tellingAt = params.tellingAtHour < 0 ? Long.MAX_VALUE : worldStart + Math.round(params.tellingAtHour * rules.hourTicks);
			boolean told = false;
			for (SeatRun seat : seats) {
				if (seat.joinAt <= worldTicks) {
					seat.join(params.continueSession);
				}
			}
			long sessionEnd = params.singleSession ? Long.MAX_VALUE : worldTicks + pick(params.sessionMin, params.sessionMax, random);
			int tick = 0;
			long runTicks = worldStart;
			while (runTicks + interval <= end) {
				runTicks += interval;
				List<SeatRun> online = online();
				if (!online.isEmpty()) {
					worldTicks += interval;
					dayTicks += interval;
					rollWeather();
				}
				online.forEach(seat -> seat.play += interval);
				if (worldTicks >= sessionEnd) {
					online.forEach(seat -> brain.onLeave(seat.pacing, seat.clock(), seat.rec));
					online.forEach(seat -> brain.onJoin(world, seat.pacing, seat.clock(), seat.env, random, seat.rec));
					sessionEnd = worldTicks + pick(params.sessionMin, params.sessionMax, random);
				}
				List<SeatRun> joined = new ArrayList<>();
				for (SeatRun seat : seats) {
					if (seat.result == null && runTicks >= seat.joinAt) {
						seat.join(false);
						joined.add(seat);
					}
				}
				online = online();
				if (!told && runTicks >= tellingAt && !online.isEmpty()) {
					// Someone writes his name, as lore's TELLING event reports it to the live director.
					told = true;
					SeatRun first = online.getFirst();
					named();
					brain.onTelling(world, first.clock(), first.env, true, first.rec);
				}
				List<SeatRun> deciding = joined.isEmpty() ? online : online.stream().filter(seat -> !joined.contains(seat)).toList();
				for (int k = 0; k < deciding.size(); k++) {
					// Players take turns going first, as the live director staggers them.
					SeatRun seat = deciding.get((tick + k) % deciding.size());
					brain.step(world, seat.pacing, seat.clock(), seat.env, random, seat.rec);
				}
				for (SeatRun seat : deciding) {
					seat.bookkeep();
				}
				tick++;
			}
			for (SeatRun seat : online()) {
				Result result = seat.result;
				result.endPlay = seat.play;
				result.stage = stage;
				result.tension = seat.env.tension;
				result.violations = check(result, cards);
			}
		}

		/** The world's stage moved: one world event, and one in every online player's record on their own clock. */
		void stageChanged(Stage from, Stage to) {
			worldEvents.add(new Event(Kind.STAGE, worldTicks, dayTicks, to, null, null, false, 0, -1, false, from.name()));
			for (SeatRun seat : online()) {
				seat.add(Kind.STAGE, seat.clock(), to, null, null, false, 0, -1, false, from.name());
			}
		}

		/** The run's naming telling, recorded before the stage change it causes. */
		void named() {
			worldEvents.add(new Event(Kind.TELLING, worldTicks, dayTicks, stage, null, null, false, 0, -1, true, "named him"));
			for (SeatRun seat : online()) {
				seat.add(Kind.TELLING, seat.clock(), stage, null, null, false, 0, -1, true, "named him");
			}
		}

		List<DirectorBrain.Sharer> sharers(SeatRun of) {
			List<DirectorBrain.Sharer> out = new ArrayList<>();
			for (SeatRun other : online()) {
				if (other != of && sharing.together(worldTicks, of.player, other.player)) {
					out.add(new DirectorBrain.Sharer(other.pacing, other.clock(), other.env, other.rec));
				}
			}
			return out;
		}
	}

	/** One player at the table: their pacing, clock, env and record. Online from their join to the end. */
	private static final class SeatRun {
		final Table table;
		final UUID player;
		final long joinAt;
		final SimEnv env;
		final RecordingRecorder rec;
		PacingMemory pacing;
		long play;
		Result result;

		SeatRun(Table table, UUID player, long joinAt, @Nullable PacingMemory pacing, long play, double tension) {
			this.table = table;
			this.player = player;
			this.joinAt = joinAt;
			this.pacing = pacing;
			this.play = play;
			this.env = new SimEnv(table, this, tension);
			this.rec = new RecordingRecorder(this);
		}

		DirectorBrain.Clock clock() {
			return new DirectorBrain.Clock(play, table.dayTicks, table.worldTicks);
		}

		/** Takes part from now on: a fresh pacing for a newcomer, then a session unless one is kept going. */
		void join(boolean keepSession) {
			if (pacing == null) {
				pacing = new PacingMemory(table.dayTicks);
			}
			result = new Result(table.rules, table.params, player, table.world, pacing, play, table.dayTicks);
			result.startStage = table.stage;
			table.fogSpell.filter(spell -> !spell.endedBy(table.dayTicks)).ifPresent(result.fogSpells::add);
			rec.ensureRow(play);
			if (!keepSession || pacing.sessionStart < 0) {
				table.brain.onJoin(table.world, pacing, clock(), env, table.random, rec);
			}
		}

		void add(Kind kind, DirectorBrain.Clock c, Stage stage, Tier tier, String id, boolean fake, double value, long until, boolean flag,
				String note) {
			result.events.add(new Event(kind, c.playTicks(), c.dayTicks(), stage, tier, id, fake, value, until, flag, note));
		}

		void bookkeep() {
			DirectorRules rules = table.rules;
			HourRow row = rec.ensureRow(play);
			DirectorBrain.Clock c = clock();
			boolean quiet = table.brain.inQuiet(pacing, c);
			boolean empty = pacing.sessionEmpty && play < pacing.sessionEmptyUntil;
			if (quiet) {
				row.quietTicks += rules.tickInterval;
			}
			if (empty) {
				row.emptyTicks += rules.tickInterval;
			}
			row.stage = table.stage;
			row.tension = env.tension;
			long[] time = result.stageTime.computeIfAbsent(table.stage, s -> new long[2]);
			time[0] += rules.tickInterval;
			if (!quiet && !empty) {
				time[1] += rules.tickInterval;
			}
			long[] sky = result.skyTime.computeIfAbsent(table.sky(), s -> new long[2]);
			sky[0] += rules.tickInterval;
			if (!quiet && !empty) {
				sky[1] += rules.tickInterval;
			}
		}
	}

	/** Dice instead of a world, for one seat. */
	private static final class SimEnv implements DirectorBrain.Env {
		private final Table table;
		private final SeatRun seat;
		double tension;

		SimEnv(Table table, SeatRun seat, double tension) {
			this.table = table;
			this.seat = seat;
			this.tension = tension;
		}

		@Override
		public Stage stage() {
			return table.stage;
		}

		@Override
		public void setStage(Stage stage) {
			table.stage = stage;
		}

		@Override
		public double attention() {
			return table.params.attention;
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
			if (table.params.neverFits != null && table.params.neverFits.test(card)) {
				return false;
			}
			return table.params.fits != null ? table.params.fits.test(card) : table.random.nextDouble() < table.params.fitChance;
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			return table.random.nextDouble() < table.params.noSpotChance ? FireResult.NO_SPOT : FireResult.FIRED;
		}

		@Override
		public List<DirectorBrain.Sharer> sharers() {
			return table.sharers(seat);
		}

		@Override
		public Optional<FogSpell> fogSpell() {
			return table.spellNow();
		}
	}

	/** Turns brain callbacks into one seat's events and hourly rows (stage changes go to every seat). */
	private static final class RecordingRecorder implements DirectorBrain.Recorder {
		private final SeatRun seat;

		RecordingRecorder(SeatRun seat) {
			this.seat = seat;
		}

		HourRow ensureRow(long play) {
			Result result = seat.result;
			int index = (int) Math.max(0, (play - result.startPlay - 1) / seat.table.rules.hourTicks);
			while (result.hours.size() <= index) {
				HourRow row = new HourRow(result.hours.size());
				row.stage = seat.table.stage;
				row.tension = seat.env.tension;
				result.hours.add(row);
			}
			return result.hours.get(index);
		}

		@Override
		public void stageChanged(DirectorBrain.Clock c, Stage from, Stage to) {
			seat.table.stageChanged(from, to);
		}

		@Override
		public void sessionStarted(DirectorBrain.Clock c, Stage stage, boolean empty, long emptyUntil) {
			seat.add(Kind.SESSION_START, c, stage, null, null, false, 0, emptyUntil, empty, "");
		}

		@Override
		public void sessionEnded(DirectorBrain.Clock c) {
			seat.add(Kind.SESSION_END, c, seat.table.stage, null, null, false, 0, -1, false, "");
		}

		@Override
		public void sessionMood(DirectorBrain.Clock c, boolean empty, long emptyUntil) {
			seat.add(Kind.MOOD, c, seat.table.stage, null, null, false, 0, emptyUntil, empty, "");
		}

		@Override
		public void drew(DirectorBrain.Clock c, CardInfo card) {
			seat.add(Kind.DRAW, c, seat.table.stage, card.tier(), card.id(), false, 0, -1, false, "");
		}

		@Override
		public void gaveUp(DirectorBrain.Clock c, Tier tier, String cardId, String why) {
			seat.add(Kind.GIVE_UP, c, seat.table.stage, tier, cardId, false, 0, -1, false, why);
		}

		@Override
		public void refilled(DirectorBrain.Clock c, Tier tier) {
			seat.add(Kind.REFILL, c, seat.table.stage, tier, null, false, 0, -1, false, "");
		}

		@Override
		public void fired(DirectorBrain.Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			seat.add(Kind.FIRE, c, stage, card.tier(), card.id(), fake, tensionAfter, -1, card.hasFake(), forced ? "forced" : "");
			if (!forced) {
				seat.result.skyFires.merge(seat.table.sky(), 1, Integer::sum);
			}
			HourRow row = ensureRow(c.playTicks());
			row.fires.merge(card.tier(), 1, Integer::sum);
			if (fake) {
				row.fakes++;
			}
		}

		@Override
		public void felt(DirectorBrain.Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			seat.add(Kind.FELT, c, stage, card.tier(), card.id(), fake, tensionAfter, -1, card.hasFake(), forced ? "forced" : "felt");
			ensureRow(c.playTicks()).felt++;
		}

		@Override
		public void quietStarted(DirectorBrain.Clock c, int days, long untilDayTicks, double tensionBefore) {
			seat.add(Kind.QUIET, c, seat.table.stage, null, null, false, tensionBefore, untilDayTicks, false, days + "d");
		}
	}
}
