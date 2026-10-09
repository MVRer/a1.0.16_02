package com.forzacode.a1016_02.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.director.CardInfo;
import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.director.DirectorRules;
import com.forzacode.a1016_02.director.DirectorSim;

/**
 * The scripted playthrough ({@code /a1016 debug playthrough <hours> [seed]}): for each tempo, a deterministic dry run
 * of that many hours of real play from a fresh state, through the same director brain the game runs, with the real
 * card registry (tiers, earliest stages, habits, tags). Whether a card's moment fits, whether it finds a spot and
 * how long sessions last are seeded dice. The run is then checked against every 4b limit ({@link PlaythroughCheck}).
 * Nothing in the world or the director's memory changes.
 */
public final class Playthrough {
	/** Dice seed when none is given. The same seed and config always give the same playthrough. */
	public static final long DEFAULT_SEED = 1016L;
	public static final int MAX_HOURS = 500;

	/** One tempo's run and its verdicts. */
	public record Report(Tempo tempo, long seed, double hours, WorldProfile profile, double attention, int leftOut,
			PlaythroughCheck.Limits limits, Map<String, CardInfo> cards, DirectorSim.Result result, PlaythroughCheck.Timeline timeline,
			List<PlaythroughCheck.Check> hard, List<PlaythroughCheck.Check> soft) {
		public boolean passed() {
			return hard.stream().noneMatch(PlaythroughCheck.Check::failed);
		}

		/** Every broken hard limit, one line each. */
		public List<String> failures() {
			List<String> out = new ArrayList<>();
			for (PlaythroughCheck.Check check : hard) {
				if (check.failed()) {
					check.breaches().forEach(b -> out.add(check.name() + ": " + b));
				}
			}
			return out;
		}

		public long softOnTarget() {
			return soft.stream().filter(c -> c.status() == PlaythroughCheck.Status.PASS).count();
		}

		public long softMeasured() {
			return soft.stream().filter(c -> c.status() != PlaythroughCheck.Status.NA).count();
		}

		/** One line for chat and the test log. */
		public String summary() {
			long hardPassed = hard.stream().filter(c -> c.status() == PlaythroughCheck.Status.PASS).count();
			StringBuilder rates = new StringBuilder();
			for (PlaythroughCheck.Check check : soft) {
				rates.append(" | ").append(shortName(check)).append(' ').append(check.status() == PlaythroughCheck.Status.PASS ? "ok" : Fmt.lower(check.status()))
						.append(" (").append(firstPart(check.measured())).append(')');
			}
			return Fmt.f("%s x%.2f: %s hard %d/%d, soft %d/%d on target%s", tempo, limits.paceFactor(), passed() ? "PASS" : "FAIL", hardPassed,
					hard.size(), softOnTarget(), softMeasured(), rates);
		}

		public List<String> log() {
			return PlaythroughLog.lines(this);
		}

		private static String shortName(PlaythroughCheck.Check check) {
			String name = check.name();
			if (name.startsWith("Traces")) {
				return "traces amb";
			}
			if (name.contains("minors")) {
				return "prox minors";
			}
			if (name.contains("major every")) {
				return "prox majors";
			}
			return "empty";
		}

		private static String firstPart(String measured) {
			int cut = measured.indexOf(" (");
			return cut < 0 ? measured : measured.substring(0, cut);
		}
	}

	private Playthrough() {
	}

	/** Every tempo, in order: EARLY, SLOW_BURN, VERY_LATE. */
	public static List<Report> runAll(double hours, long seed) {
		List<Report> out = new ArrayList<>();
		for (Tempo tempo : Tempo.values()) {
			out.add(run(tempo, hours, seed));
		}
		return out;
	}

	/** One tempo from a fresh state. Same arguments and config, same report. */
	public static Report run(Tempo tempo, double hours, long seed) {
		WorldProfile profile = profile(seed, tempo);
		List<CardInfo> registered = DirectorApi.registeredCards();
		List<CardInfo> deck = registered.stream().filter(card -> !DebugPingCard.ID.equals(card.id())).toList();
		Map<String, CardInfo> cards = new LinkedHashMap<>();
		deck.forEach(card -> cards.put(card.id(), card));
		double attention = DirectorApi.neutralAttention();

		DirectorSim.Result result = DirectorApi.dryRun(profile, deck, hours, seed, attention);
		Pacing pacing = ModConfig.pacing();
		PlaythroughCheck.Limits limits = PlaythroughCheck.Limits.of(result.rules, pacing.firstAccidentMinHours, pacing.paceFactor(tempo));
		PlaythroughCheck.Timeline timeline = PlaythroughCheck.timeline(result.events, result.startPlay, result.endPlay, result.startStage, limits);
		List<PlaythroughCheck.Check> hard = PlaythroughCheck.hard(result.events, limits, cards, result.violations);
		List<PlaythroughCheck.Check> soft = PlaythroughCheck.soft(result.events, timeline, limits);
		return new Report(tempo, seed, hours, profile, attention, registered.size() - deck.size(), limits, Collections.unmodifiableMap(cards), result,
				timeline, hard, soft);
	}

	/** The profile a playthrough plays: rolled from the seed (habits, density, signature, fragments), with this tempo. */
	public static WorldProfile profile(long seed, Tempo tempo) {
		WorldProfile rolled = WorldProfile.roll(seed, Long.rotateLeft(seed, 17) ^ 0x9E3779B97F4A7C15L);
		return new WorldProfile(rolled.habits(), rolled.density(), tempo, rolled.fragments(), rolled.signature());
	}

	/** Cards in the deck by tier. */
	static Map<Tier, Integer> deckByTier(Map<String, CardInfo> cards) {
		Map<Tier, Integer> out = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			out.put(tier, 0);
		}
		cards.values().forEach(card -> out.merge(card.tier(), 1, Integer::sum));
		return out;
	}

	/** Log file name for a tempo: {@code a1016_playthrough_<tempo>.log}. */
	public static String logName(Tempo tempo) {
		return "a1016_playthrough_" + Fmt.lower(tempo) + ".log";
	}

	/** Writes one log per report into {@code dir}. Returns the paths written. */
	public static List<Path> write(Path dir, List<Report> reports) throws IOException {
		Files.createDirectories(dir);
		List<Path> written = new ArrayList<>();
		for (Report report : reports) {
			Path path = dir.resolve(logName(report.tempo()));
			Files.write(path, report.log());
			written.add(path);
		}
		return written;
	}

	static String rulesNote(DirectorRules rules) {
		return ModConfig.get().devFastMode ? Fmt.f("devFastMode ON (/%d): one hour is %d ticks; in-game days are not divided", ModConfig.get().devFastDivisor,
				rules.hourTicks) : "devFastMode off";
	}
}
