package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Text for {@code /a1016 state} and {@code /a1016 director}: the world part, then each player's own pacing. */
final class DirectorDebug {
	private DirectorDebug() {
	}

	/** The short block shown by {@code /a1016 state}: the world part, then one line per participant. */
	static List<String> stateLines(DirectorImpl director, MinecraftServer server) {
		DirectorRules rules = director.rules(server);
		DirectorBrain brain = director.brain(rules);
		List<String> lines = new ArrayList<>(worldLines(server, rules));
		DirectorMemory memory = DirectorData.get(server).memory();
		HerobrineState state = HerobrineState.get(server);
		List<ServerPlayer> participants = Services.watch().participants(server);
		for (ServerPlayer player : participants) {
			UUID uuid = player.getUUID();
			PacingMemory m = memory.find(uuid).orElseGet(PacingMemory::new);
			lines.add(pacingLine(player.getName().getString(), m, DirectorImpl.clock(server, uuid), state.stage(), state.tension(uuid), rules, brain));
		}
		int away = memory.players().size() - (int) participants.stream().filter(p -> memory.find(p.getUUID()).isPresent()).count();
		if (participants.isEmpty() || away > 0) {
			lines.add(String.format(Locale.ROOT, "director: %d taking part, %d more paced and away%s", participants.size(), Math.max(0, away),
					memory.unowned() != null ? ", one old memory waiting for its player" : ""));
		}
		return lines;
	}

	/** The world part: the shared stage clock, the flags, once-per-world cards and the last timewarp. */
	static List<String> worldLines(MinecraftServer server, DirectorRules rules) {
		HerobrineState state = HerobrineState.get(server);
		WorldMemory w = DirectorData.get(server).memory().world();
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "director: world stage clock %s (runs x%.2f at attention %.0f), traces at %s, proximity at %s",
				dur(rules, (long) Math.max(0, w.stageClock)), 1 / rules.attentionFactor(state.attention()), state.attention(),
				w.tracesAt < 0 ? "-" : dur(rules, w.tracesAt), w.proximityAt < 0 ? "-" : dur(rules, w.proximityAt)));
		lines.add("director: flags " + new DirectorFlags.Values(rules.silenceUntilDay, rules.paceMultiplier).describe()
				+ String.format(Locale.ROOT, " (minor gap %s, major gap %s, majors every %s to %s)", dur(rules, rules.minorGap),
						dur(rules, rules.majorGap), dur(rules, rules.majorEvery.min()), dur(rules, rules.majorEvery.max())));
		lines.add("director: once per world fired " + (w.signaturesFired.isEmpty() ? "-" : String.join(", ", w.signaturesFired))
				+ (w.lastWarpSummary.isEmpty() ? "" : " | last timewarp " + w.lastWarpSummary));
		lines.add("director: fog weather " + fogLine(state.effects().fogSpell(), server.overworld().getDefaultClockTime(), rules));
		return lines;
	}

	/** What the fog weather does to the director at this overworld clock (D-071): the spell on, if any, and its boost. */
	static String fogLine(Optional<FogSpell> stored, long clock, DirectorRules rules) {
		Optional<FogSpell> on = DirectorImpl.fogSpellOn(stored, clock, false);
		if (on.isEmpty()) {
			// A spell easing in or out below half its level is not fog yet (or any more): no boost (R2#1).
			return stored.filter(spell -> spell.activeAt(clock))
					.map(spell -> String.format(Locale.ROOT, "a %sspell giving %.2f fog of its %.2f, below half: no boost", spell.quiet() ? "quiet " : "",
							spell.amount(clock), spell.level()))
					.orElse("clear, no boost");
		}
		FogSpell spell = on.get();
		String until = String.format(Locale.ROOT, "until day %d %05d", Math.floorDiv(spell.untilClock(), DirectorBrain.DAY_TICKS),
				Math.floorMod(spell.untilClock(), DirectorBrain.DAY_TICKS));
		return spell.quiet() ? "a quiet spell " + until + ", no boost"
				: String.format(Locale.ROOT, "a fog spell %s: ambient and minor odds x%.1f, sightings x%.1f, a major due within %s may come now", until,
						rules.fogWeatherOddsFactor, rules.fogWeatherSightingWeight, dur(rules, rules.fogWeatherMajorPull));
	}

	/** One player's pacing on one line: tension, quiet, pity, session, held cards, the gate now, what is next, counts. */
	static String pacingLine(String name, PacingMemory m, DirectorBrain.Clock c, Stage stage, double tension, DirectorRules rules,
			DirectorBrain brain) {
		long now = c.playTicks();
		String quiet = brain.inQuiet(m, c) ? "until day " + Math.floorDiv(m.quietUntilDayTicks, DirectorBrain.DAY_TICKS) : "no";
		String session = m.sessionStart < 0 ? "none" : dur(rules, now - m.sessionStart) + (m.sessionEmpty && now < m.sessionEmptyUntil ? " (empty)" : "");
		StringJoiner held = new StringJoiner(", ");
		for (Tier tier : Tier.values()) {
			String id = m.held.get(tier);
			if (id != null) {
				held.add(tier.name().toLowerCase(Locale.ROOT) + " " + id);
			}
		}
		String blocked = brain.globalBlock(m, c);
		return String.format(Locale.ROOT,
				"director: %s: play %s, tension %.1f/%.0f, quiet %s, pity +%.0f%%, session %s, held %s | now %s | next minor %s, major %s | fires %d, felt %d",
				name, dur(rules, now), tension, rules.tensionThreshold, quiet, brain.pity(m, c) * 100, session, held.length() == 0 ? "-" : held,
				blocked == null ? "open" : blocked, nextAllowed(Tier.MINOR, m, c, stage, rules, brain), nextAllowed(Tier.MAJOR, m, c, stage, rules, brain),
				m.totalFires, m.feltFires);
	}

	/** The long block shown by {@code /a1016 director}: the world part, then one player's pacing, decks and history. */
	static List<String> fullLines(DirectorImpl director, MinecraftServer server, UUID player) {
		DirectorRules rules = director.rules(server);
		DirectorBrain brain = director.brain(rules);
		List<String> lines = new ArrayList<>(worldLines(server, rules));
		DirectorMemory memory = DirectorData.get(server).memory();
		WorldMemory w = memory.world();
		PacingMemory m = memory.find(player).orElse(null);
		String name = DirectorImpl.name(server, player);
		if (m == null) {
			lines.add("director: " + name + " has no pacing yet (never took part)");
			return lines;
		}
		HerobrineState state = HerobrineState.get(server);
		DirectorBrain.Clock c = DirectorImpl.clock(server, player);
		Stage stage = state.stage();
		long now = c.playTicks();
		lines.add(pacingLine(name, m, c, stage, state.tension(player), rules, brain));

		StringJoiner held = new StringJoiner(", ");
		for (Tier tier : Tier.values()) {
			String id = m.held.get(tier);
			if (id != null) {
				held.add(tier + "=" + id + " (" + dur(rules, now - m.heldSince.getOrDefault(tier, now)) + ")");
			}
		}
		lines.add(String.format(Locale.ROOT, "director: %s: held %s | first day until day %.2f | minors/h %.2f", name, held.length() == 0 ? "-" : held,
				brain.firstDayEnd(m) / (double) DirectorBrain.DAY_TICKS, brain.rate(Tier.MINOR, m, stage)));
		StringJoiner next = new StringJoiner(" | ");
		for (Tier tier : DirectorBrain.PRIORITY) {
			next.add(tier.name().toLowerCase(Locale.ROOT) + " " + nextAllowed(tier, m, c, stage, rules, brain));
		}
		lines.add("director: " + name + ": next " + next);
		lines.add(String.format(Locale.ROOT, "director: %s: fires %d (%d of %d fakeable were fakes), felt %d from others, quiets %d, empty sessions %d/%d",
				name, m.totalFires, m.fakeFires, m.fakeableFires, m.feltFires, m.quietCount, m.emptySessionCount, m.sessionCount));
		for (Tier tier : Tier.values()) {
			List<String> undrawn = new ArrayList<>();
			List<String> fired = new ArrayList<>();
			List<String> parked = new ArrayList<>();
			for (CardInfo card : brain.cards(tier)) {
				boolean used = m.cycleFired.get(tier).contains(card.id()) || DirectorBrain.oncePerWorld(card) && w.signaturesFired.contains(card.id());
				(used ? fired : undrawn).add(card.id());
				if (brain.parked(m, c, card.id())) {
					parked.add(card.id() + " " + dur(rules, m.parkedUntil.get(card.id()) - now));
				}
			}
			int drawable = brain.candidates(tier, w, m, c, stage).size();
			Long stuck = m.deckStuckSince.get(tier);
			String waiting = stuck == null ? "" : String.format(Locale.ROOT, ", waiting on gated cards for %.1f days",
					(c.dayTicks() - stuck) / (double) DirectorBrain.DAY_TICKS);
			String parkedNote = parked.isEmpty() ? "" : ", parked " + parked;
			lines.add(String.format(Locale.ROOT, "deck %s: undrawn %s, fired %s, last %s, drawable now %d%s%s", tier, undrawn, fired,
					m.lastFired.getOrDefault(tier, "-"), drawable, parkedNote, waiting));
		}
		List<DirectorMemory.HistoryEntry> history = m.history();
		int from = Math.max(0, history.size() - 5);
		for (DirectorMemory.HistoryEntry e : history.subList(from, history.size())) {
			lines.add(String.format(Locale.ROOT, "%s %s day %d: %s %s%s%s (%s)", e.felt() ? "felt " : "fired", dur(rules, e.playTicks()), e.day(), e.tier(),
					e.cardId(), e.fake() ? " fake" : "", e.forced() ? " forced" : "", e.stage()));
		}
		return lines;
	}

	private static String nextAllowed(Tier tier, PacingMemory m, DirectorBrain.Clock c, Stage stage, DirectorRules rules, DirectorBrain brain) {
		long now = c.playTicks();
		String block = brain.tierBlock(tier, m, c, stage);
		long wait = switch (tier) {
			case MINOR -> m.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER) + rules.minorGap - now;
			case MAJOR -> Math.max(m.lastMajorOrSignature + rules.majorGap, m.nextMajorDue) - now;
			case SIGNATURE -> m.lastMajorOrSignature + rules.majorGap - now;
			case AMBIENT -> 0;
		};
		if (block != null) {
			return block.endsWith("gap") ? "in " + dur(rules, wait) : "(" + block + ")";
		}
		if (tier == Tier.MAJOR && m.nextMajorDue >= 0 && now < m.nextMajorDue) {
			return "due in " + dur(rules, m.nextMajorDue - now);
		}
		double rate = brain.rate(tier, m, stage);
		return rate >= 0 ? String.format(Locale.ROOT, "open (%.2f/h)", rate) : "open";
	}

	/** Play ticks as h/m in real time (respects devFastMode through the rules' hour). */
	static String dur(DirectorRules rules, long ticks) {
		return DirectorSim.time(ticks, rules);
	}
}
