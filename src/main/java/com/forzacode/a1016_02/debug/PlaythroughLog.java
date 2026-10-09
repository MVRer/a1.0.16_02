package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.director.CardInfo;
import com.forzacode.a1016_02.director.DirectorConfig;
import com.forzacode.a1016_02.director.DirectorRules;
import com.forzacode.a1016_02.director.DirectorSim;
import com.forzacode.a1016_02.director.DirectorSim.Event;
import com.forzacode.a1016_02.director.DirectorSim.Kind;

/** The text of {@code logs/a1016_playthrough_<tempo>.log}. Every number in {@code Locale.ROOT}. */
final class PlaythroughLog {
	private static final long DAY_TICKS = 24000L;

	private PlaythroughLog() {
	}

	static List<String> lines(Playthrough.Report r) {
		DirectorSim.Result result = r.result();
		DirectorRules rules = result.rules;
		PlaythroughCheck.Limits limits = r.limits();
		long h = limits.hourTicks();
		List<String> lines = new ArrayList<>();

		WorldProfile profile = r.profile();
		Map<Tier, Integer> byTier = Playthrough.deckByTier(r.cards());
		DirectorConfig config = DirectorConfig.get();
		lines.add(Fmt.f("a1016 playthrough: %s (pace x%.2f), %.1f h of real play from a fresh state, seed %d", r.tempo(), limits.paceFactor(),
				r.hours(), r.seed()));
		lines.add(Fmt.f("profile (rolled from the seed): habits %s, density %s, signature %s, %d fragments", profile.habits(), profile.density(),
				profile.signature(), profile.fragments().size()));
		lines.add(Fmt.f("deck: %d registry cards (ambient %d, minor %d, major %d, signature %d)%s", r.cards().size(), byTier.get(Tier.AMBIENT),
				byTier.get(Tier.MINOR), byTier.get(Tier.MAJOR), byTier.get(Tier.SIGNATURE),
				r.leftOut() > 0 ? ", " + DebugPingCard.ID + " left out (its moment never fits)" : ""));
		lines.add(Fmt.f("dice: a held card's moment fits %.0f%% per director tick, no spot %.0f%%, sessions %.0f to %.0f min; attention held at %.1f",
				result.params.fitChance * 100, result.params.noSpotChance * 100, config.simSessionMinMinutes, config.simSessionMaxMinutes,
				r.attention()));
		lines.add(Fmt.f("4b numbers: join grace %s, minors %s apart, 1 major per hour, no major before day %d, Alone max %d ambient, "
				+ "first accident %s; targets: Traces %.1f ambient/h, Proximity %.0f-%.0f minors/h and a major every %s-%s",
				Fmt.dur(limits.joinGrace(), h), Fmt.dur(limits.minorGap(), h), limits.noMajorBeforeDay(), limits.aloneMaxAmbient(),
				Fmt.hm(limits.firstAccident(), h), limits.tracesAmbientPerHour(), limits.minorsPerHourMin(), limits.minorsPerHourMax(),
				Fmt.hm(limits.majorEveryMin(), h), Fmt.hm(limits.majorEveryMax(), h)));
		if (result.params.tellingAtHour >= 0) {
			lines.add(Fmt.f("telling: the subject names him at %s (simTellingAtHour), so Telling runs from then on", Fmt.hm(Math.round(
					result.params.tellingAtHour * h), h)));
		}
		lines.add(Playthrough.rulesNote(rules));
		lines.add("");
		lines.add(Fmt.f("RESULT: %s (hard %d/%d held, soft %d/%d on target)", r.passed() ? "PASS" : "FAIL",
				r.hard().stream().filter(c -> c.status() == PlaythroughCheck.Status.PASS).count(), r.hard().size(), r.softOnTarget(), r.softMeasured()));
		lines.add("");
		lines.add("HARD LIMITS");
		for (PlaythroughCheck.Check check : r.hard()) {
			lines.add(Fmt.f("  [%s] %s: %s", check.status() == PlaythroughCheck.Status.PASS ? "PASS" : "FAIL", check.name(), check.measured()));
			check.breaches().forEach(b -> lines.add("         - " + b));
		}
		lines.add("SOFT TARGETS (reported, never failed; allowing quiet: not above the band over all the stage's time, not below it over");
		lines.add("              the active time, which leaves out quiets and empty sessions)");
		for (PlaythroughCheck.Check check : r.soft()) {
			String status = switch (check.status()) {
				case PASS -> "ok  ";
				case OFF -> "OFF ";
				case NA -> "n/a ";
				case FAIL -> "FAIL";
			};
			lines.add(Fmt.f("  [%s] %s: %s", status, check.name(), check.measured()));
		}

		lines.add("");
		lines.add("STAGES");
		lines.add(Fmt.f("  %-10s from %s", result.startStage, Fmt.hm(result.startPlay, h)) + stageTime(r, result.startStage));
		for (Event e : result.events) {
			if (e.kind() == Kind.STAGE) {
				lines.add(Fmt.f("  %-10s from %s (day %d)", e.stage(), Fmt.hm(e.play(), h), e.day()) + stageTime(r, e.stage()));
			}
		}

		List<Event> fires = result.fires();
		long fakeable = fires.stream().filter(Event::flag).count();
		long fakes = fires.stream().filter(Event::fake).count();
		long quietPlay = r.timeline().quiets().stream().mapToLong(q -> Math.min(q[1], result.endPlay) - q[0]).sum();
		long quietDays = result.events.stream().filter(e -> e.kind() == Kind.QUIET).mapToLong(e -> (e.until() - e.dayTicks()) / DAY_TICKS).sum();
		lines.add("");
		lines.add("TOTALS");
		lines.add(Fmt.f("  fires %d: ambient %d, minor %d, major %d, signature %d; fakes %d of %d that could be", fires.size(),
				result.firesOf(Tier.AMBIENT), result.firesOf(Tier.MINOR), result.firesOf(Tier.MAJOR), result.firesOf(Tier.SIGNATURE), fakes, fakeable));
		lines.add(Fmt.f("  quiets %d (%d in-game days, %s of play); sessions %d, empty %d; final tension %.1f", result.count(Kind.QUIET), quietDays,
				Fmt.hm(quietPlay, h), r.timeline().sessions().size(), r.timeline().empties().size(), result.tension));

		lines.add("");
		lines.add("PER HOUR (fires by tier and card; (f) = fake)");
		lines.add("  hour  stage       amb min maj sig  quiet  empty  tension  cards");
		Map<Integer, StringJoiner> cardsByHour = new LinkedHashMap<>();
		for (Event e : fires) {
			int hour = (int) Math.max(0, (e.play() - result.startPlay - 1) / h);
			cardsByHour.computeIfAbsent(hour, k -> new StringJoiner(", ")).add(e.cardId() + (e.fake() ? " (f)" : ""));
		}
		for (DirectorSim.HourRow row : result.hours) {
			StringJoiner cards = cardsByHour.get(row.index);
			lines.add(Fmt.f("  h%03d  %-10s  %3d %3d %3d %3d  %4dm  %4dm  %7.1f  %s", row.index, row.stage, row.fires.get(Tier.AMBIENT),
					row.fires.get(Tier.MINOR), row.fires.get(Tier.MAJOR), row.fires.get(Tier.SIGNATURE), Fmt.minutes(row.quietTicks, h),
					Fmt.minutes(row.emptyTicks, h), row.tension, cards == null ? "-" : cards.toString()));
		}

		lines.add("");
		lines.add("QUIET PERIODS (tension past the threshold buys in-game days of nothing)");
		boolean any = false;
		for (Event e : result.events) {
			if (e.kind() == Kind.QUIET) {
				any = true;
				lines.add(Fmt.f("  %s day %d: %s, until day %d (%s of play), %s; tension was %.1f", Fmt.hm(e.play(), h), e.day(),
						days((e.until() - e.dayTicks()) / DAY_TICKS), Math.floorDiv(e.until(), DAY_TICKS), Fmt.hm(e.until() - e.dayTicks(), h), e.stage(),
						e.value()));
			}
		}
		if (!any) {
			lines.add("  none");
		}

		lines.add("");
		lines.add("EMPTY SESSIONS (nothing at all on purpose)");
		any = false;
		for (Event e : result.events) {
			if (e.kind() == Kind.SESSION_START && e.flag()) {
				any = true;
				long end = r.timeline().empties().stream().filter(x -> x[0] == e.play()).mapToLong(x -> x[1]).findFirst().orElse(e.until());
				lines.add(Fmt.f("  %s to %s (%s) in %s", Fmt.hm(e.play(), h), Fmt.hm(end, h), Fmt.hm(end - e.play(), h), e.stage()));
			}
		}
		if (!any) {
			lines.add("  none");
		}

		lines.add("");
		lines.add(Fmt.f("ACCIDENT CARDS (none allowed before %s)", Fmt.hm(limits.firstAccident(), h)));
		any = false;
		for (Event e : fires) {
			CardInfo card = r.cards().get(e.cardId());
			if (card != null && card.has(CardTag.ACCIDENT)) {
				any = true;
				lines.add(Fmt.f("  %s day %d: %s %s%s in %s", Fmt.hm(e.play(), h), e.day(), Fmt.lower(e.tier()), e.cardId(), e.fake() ? " (fake)" : "",
						e.stage()));
			}
		}
		if (!any) {
			lines.add("  none fired");
		}

		lines.add("");
		lines.add("TIMELINE (every decision except draws)");
		for (Event e : result.events) {
			if (e.kind() != Kind.DRAW) {
				lines.add(Fmt.f("  %s day %3d  %-13s %s", Fmt.hm(e.play(), h), e.day(), e.kind(), describe(e)));
			}
		}
		return lines;
	}

	private static String stageTime(Playthrough.Report r, Stage stage) {
		PlaythroughCheck.StageTime time = r.timeline().in(stage);
		long h = r.limits().hourTicks();
		return Fmt.f(": %s in it, %s active", Fmt.hm(time.total(), h), Fmt.hm(time.active(), h));
	}

	private static String describe(Event e) {
		return switch (e.kind()) {
			case STAGE -> e.note() + " -> " + e.stage();
			case SESSION_START -> e.flag() ? "empty (" + e.stage() + ")" : "(" + e.stage() + ")";
			case SESSION_END -> "";
			case DRAW -> Fmt.lower(e.tier()) + " " + e.cardId();
			case GIVE_UP -> Fmt.lower(e.tier()) + " " + e.cardId() + " back into its deck: " + e.note();
			case REFILL -> Fmt.lower(e.tier()) + " deck reshuffled";
			case FIRE -> Fmt.f("%-9s %s%s  tension %.1f", Fmt.lower(e.tier()), e.cardId(), e.fake() ? " (fake)" : "", e.value());
			case QUIET -> Fmt.f("%s, until day %d (tension was %.1f)", days((e.until() - e.dayTicks()) / DAY_TICKS),
					Math.floorDiv(e.until(), DAY_TICKS), e.value());
			case TELLING -> "the subject named him (in " + e.stage() + ")";
		};
	}

	private static String days(long days) {
		return days + (days == 1 ? " in-game day" : " in-game days");
	}
}
