package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;

/** Text for {@code /a1016 state} and {@code /a1016 director}. */
final class DirectorDebug {
	private DirectorDebug() {
	}

	/** The short block shown by {@code /a1016 state}. */
	static List<String> stateLines(DirectorImpl director, MinecraftServer server) {
		HerobrineState state = HerobrineState.get(server);
		DirectorMemory m = DirectorData.get(server).memory();
		DirectorRules rules = director.rules(server);
		DirectorBrain brain = director.brain(server, rules);
		DirectorBrain.Clock c = DirectorImpl.clock(server);
		Stage stage = state.stage();
		long now = c.playTicks();
		List<String> lines = new ArrayList<>();

		lines.add(String.format(Locale.ROOT, "director: stage clock %s (runs x%.2f at attention %.0f), traces at %s, proximity at %s",
				dur(rules, (long) Math.max(0, m.stageClock)), 1 / rules.attentionFactor(state.attention()), state.attention(),
				m.tracesAt < 0 ? "-" : dur(rules, m.tracesAt), m.proximityAt < 0 ? "-" : dur(rules, m.proximityAt)));

		String quiet = brain.inQuiet(m, c) ? "until day " + Math.floorDiv(m.quietUntilDayTicks, DirectorBrain.DAY_TICKS) : "no";
		String session = m.sessionStart < 0 ? "none" : dur(rules, now - m.sessionStart) + (m.sessionEmpty && now < m.sessionEmptyUntil ? " (empty on purpose)" : "");
		lines.add(String.format(Locale.ROOT, "director: tension %.1f/%.0f, quiet %s, pity +%.0f%%, session %s, minors/h %.2f",
				state.tension(), rules.tensionThreshold, quiet, brain.pity(m, c) * 100, session, brain.rate(Tier.MINOR, m, stage)));

		StringJoiner held = new StringJoiner(", ");
		for (Tier tier : Tier.values()) {
			String id = m.held.get(tier);
			if (id != null) {
				held.add(tier + "=" + id + " (" + dur(rules, now - m.heldSince.getOrDefault(tier, now)) + ")");
			}
		}
		String blocked = brain.globalBlock(m, c);
		lines.add("director: held " + (held.length() == 0 ? "-" : held) + " | now " + (blocked == null ? "open" : blocked));
		lines.add("director: flags " + new DirectorFlags.Values(rules.silenceUntilDay, rules.paceMultiplier).describe()
				+ String.format(Locale.ROOT, " (minor gap %s, major gap %s, majors every %s to %s)", dur(rules, rules.minorGap),
						dur(rules, rules.majorGap), dur(rules, rules.majorEvery.min()), dur(rules, rules.majorEvery.max())));

		StringJoiner next = new StringJoiner(" | ");
		for (Tier tier : DirectorBrain.PRIORITY) {
			next.add(tier.name().toLowerCase(Locale.ROOT) + " " + nextAllowed(tier, m, c, stage, rules, brain));
		}
		lines.add("director: next " + next);

		StringJoiner decks = new StringJoiner(", ");
		for (Tier tier : Tier.values()) {
			int total = brain.cards(tier).size();
			long undrawn = brain.cards(tier).stream().filter(card -> !m.cycleFired.get(tier).contains(card.id())
					&& !(DirectorBrain.oncePerWorld(card) && m.signaturesFired.contains(card.id()))).count();
			decks.add(tier.name().toLowerCase(Locale.ROOT) + " " + undrawn + "/" + total);
		}
		lines.add(String.format(Locale.ROOT, "director: decks %s | fires %d (%d of %d fakeable were fakes), quiets %d, empty sessions %d/%d",
				decks, m.totalFires, m.fakeFires, m.fakeableFires, m.quietCount, m.emptySessionCount, m.sessionCount));
		if (!m.history.isEmpty()) {
			DirectorMemory.HistoryEntry last = m.history.getLast();
			lines.add(String.format(Locale.ROOT, "director: last fire %s (%s%s%s) %s ago", last.cardId(), last.tier(),
					last.fake() ? ", fake" : "", last.forced() ? ", forced" : "", dur(rules, now - last.playTicks())));
		}
		if (!m.lastWarpSummary.isEmpty()) {
			lines.add("director: last timewarp " + m.lastWarpSummary);
		}
		return lines;
	}

	/** The long block shown by {@code /a1016 director}: state plus deck contents and recent history. */
	static List<String> fullLines(DirectorImpl director, MinecraftServer server) {
		List<String> lines = new ArrayList<>(stateLines(director, server));
		DirectorMemory m = DirectorData.get(server).memory();
		DirectorRules rules = director.rules(server);
		DirectorBrain brain = director.brain(server, rules);
		DirectorBrain.Clock c = DirectorImpl.clock(server);
		Stage stage = HerobrineState.get(server).stage();
		for (Tier tier : Tier.values()) {
			List<String> undrawn = new ArrayList<>();
			List<String> fired = new ArrayList<>();
			for (CardInfo card : brain.cards(tier)) {
				boolean used = m.cycleFired.get(tier).contains(card.id()) || DirectorBrain.oncePerWorld(card) && m.signaturesFired.contains(card.id());
				(used ? fired : undrawn).add(card.id());
			}
			int drawable = brain.candidates(tier, m, c, stage).size();
			Long stuck = m.deckStuckSince.get(tier);
			String waiting = stuck == null ? "" : String.format(Locale.ROOT, ", waiting on gated cards for %.1f days",
					(c.dayTicks() - stuck) / (double) DirectorBrain.DAY_TICKS);
			lines.add(String.format(Locale.ROOT, "deck %s: undrawn %s, fired %s, last %s, drawable now %d%s", tier, undrawn, fired,
					m.lastFired.getOrDefault(tier, "-"), drawable, waiting));
		}
		List<DirectorMemory.HistoryEntry> history = m.history();
		int from = Math.max(0, history.size() - 5);
		for (DirectorMemory.HistoryEntry e : history.subList(from, history.size())) {
			lines.add(String.format(Locale.ROOT, "fired %s day %d: %s %s%s%s (%s)", dur(rules, e.playTicks()), e.day(), e.tier(), e.cardId(),
					e.fake() ? " fake" : "", e.forced() ? " forced" : "", e.stage()));
		}
		return lines;
	}

	private static String nextAllowed(Tier tier, DirectorMemory m, DirectorBrain.Clock c, Stage stage, DirectorRules rules, DirectorBrain brain) {
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
