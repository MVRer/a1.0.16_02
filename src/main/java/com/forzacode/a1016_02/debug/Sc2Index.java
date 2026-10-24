package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc2.HuntData;
import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.s25.Pack;
import com.forzacode.a1016_02.accident.sc2.s25.Sc25Config;
import com.forzacode.a1016_02.accident.sc2.s26.Call;
import com.forzacode.a1016_02.accident.sc2.s26.Sc26Config;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Rings;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc21Config;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc2xData;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Processions;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Sc22Config;
import com.forzacode.a1016_02.atmosphere.sc2.s23.HeldBreath;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Follows;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Sc24Config;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorMemory;
import com.forzacode.a1016_02.director.PacingMemory;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.Variant;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Config;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Data;
import com.forzacode.a1016_02.lore.sc2.s30.S30Data;
import com.forzacode.a1016_02.lore.sc2.s30.Sc30Config;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.forzacode.a1016_02.world.sc2.s28.S28;
import com.forzacode.a1016_02.world.sc2.s28.S28Data;
import com.forzacode.a1016_02.world.sc2.s28.Sc28Config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The second wave's part of {@code /a1016 sc} (docs/SELF_CREATED_2.md, SC2-9): one line per number, 16 to 31, and each
 * participant's overlay parts for them, read-only and in opaque words only (counts, days, "armed", "going", time left,
 * the answers by their numbers), never what a number is: {@code SelfCreatedDebugTests} holds them to the first wave's
 * words and to D-103's. Lines 16 to 20 are his places by kind (known, visited, who is inside, each answer's count today
 * and ever); 21 to 31 the cards' world counts, what runs now and for whom, and each player's arm with its time left.
 */
final class Sc2Index {
	private static final long DAY_TICKS = 24_000L;

	private Sc2Index() {
	}

	/** Lines 16 to 31, in order. */
	static List<String> lines(MinecraftServer server, List<ServerPlayer> players) {
		List<String> lines = new ArrayList<>();
		for (int n = 16; n <= 20; n++) {
			int number = n;
			lines.add(safe(() -> remains(server, number, players), number));
		}
		lines.add(safe(() -> line21(server), 21));
		lines.add(safe(() -> line22(server), 22));
		lines.add(safe(() -> line23(server, players), 23));
		lines.add(safe(() -> line24(server), 24));
		lines.add(safe(() -> trap(server, players, "25", Pack.KIND, Sc25Config.get().maxPerWorld), 25));
		lines.add(safe(() -> trap(server, players, "26", Call.KIND, Sc26Config.get().maxPerWorld), 26));
		lines.add(safe(() -> line27(server, players), 27));
		lines.add(safe(() -> line28(server), 28));
		lines.add(safe(() -> "29: " + fired(server, players, "sc_29"), 29));
		lines.add(safe(() -> line30(server), 30));
		lines.add(safe(() -> "31: " + fired(server, players, "sc_31") + "; c " + fired(server, players, "sc_31c"), 31));
		return lines;
	}

	/** One number's line; a reader that fails says so on its own line and the rest still print. */
	private static String safe(java.util.function.Supplier<String> line, int number) {
		try {
			return line.get();
		} catch (RuntimeException e) {
			return number + ": could not be read (" + e.getClass().getSimpleName() + ")";
		}
	}

	// --- 16 to 20: his places, by kind ---

	/** "16: 3 known, 1 visited, 1 inside now (Ana); answers today/ever 16b 0/1, 16c 2/4", or "16: none known". */
	static String remains(MinecraftServer server, int number, List<ServerPlayer> players) {
		return remains(RemainsImpl.LIVE.data(), GameClock.day(server), number, players, Visits.LIVE::placeOf);
	}

	/** {@link #remains(MinecraftServer, int, List)} over this data, day and visits (tests). */
	static String remains(Sc2Data data, long today, int number, List<ServerPlayer> players, java.util.function.Function<UUID, Optional<Remains.Place>> placeOf) {
		int known = 0;
		int visited = 0;
		Map<String, int[]> answers = new TreeMap<>();
		for (Sc2Data.Known place : data.places()) {
			if (place.place().kind().number() != number) {
				continue;
			}
			known++;
			visited += place.visitors().isEmpty() ? 0 : 1;
			for (Map.Entry<String, Sc2Data.Spent> spent : place.spent().entrySet()) {
				int[] counts = answers.computeIfAbsent(spent.getKey(), k -> new int[2]);
				counts[0] += spent.getValue().on(today);
				counts[1] += spent.getValue().ever();
			}
		}
		StringJoiner inside = new StringJoiner(", ");
		int in = 0;
		for (ServerPlayer p : players) {
			Optional<Remains.Place> at = placeOf.apply(p.getUUID());
			if (at.isPresent() && at.get().kind().number() == number) {
				inside.add(p.getName().getString());
				in++;
			}
		}
		if (known == 0) {
			return number + ": none known";
		}
		StringBuilder line = new StringBuilder(Fmt.f("%d: %d known, %d visited, %d inside now", number, known, visited, in));
		if (in > 0) {
			line.append(" (").append(inside).append(')');
		}
		if (!answers.isEmpty()) {
			StringJoiner each = new StringJoiner(", ");
			answers.forEach((id, counts) -> each.add(Fmt.f("%s %d/%d", id, counts[0], counts[1])));
			line.append("; answers today/ever ").append(each);
		}
		return line.toString();
	}

	// --- 21 to 24 ---

	/** The world's count of its cap and the last day (from the cards' shared data). */
	private static String caps(MinecraftServer server, String id, int max) {
		Sc2xData.Caps caps = Sc2xData.get(server).caps(id);
		return Fmt.f("%d of %d, last %s", caps.count(), max, caps.lastDay() < 0 ? "never" : "day " + caps.lastDay());
	}

	private static String line21(MinecraftServer server) {
		StringJoiner going = new StringJoiner(", ");
		for (Rings.Scene scene : Rings.LIVE.scenes()) {
			going.add(Fmt.f("%d for %s (%s)", scene.count(), names(server, scene.sharers()), Fmt.lower(scene.phase())));
		}
		int cells = Rings.LIVE.printsWaiting();
		return "21: " + caps(server, "21", Sc21Config.get().maxPerWorld) + (going.length() == 0 ? ", none going" : ", going " + going)
				+ (cells > 0 ? Fmt.f(", %d waiting to be bared", cells) : "");
	}

	private static String line22(MinecraftServer server) {
		StringJoiner going = new StringJoiner(", ");
		for (Processions.Procession p : Processions.LIVE.processions()) {
			going.add(Fmt.f("%d for %s (%s, %d moved)", p.mobs().size(), names(server, p.sharers()), Fmt.lower(p.phase()), p.moved()));
		}
		return "22: " + caps(server, "22", Sc22Config.get().maxPerWorld) + (going.length() == 0 ? ", none going" : ", going " + going);
	}

	private static String line23(MinecraftServer server, List<ServerPlayer> players) {
		long today = GameClock.day(server);
		Sc2xData data = Sc2xData.get(server);
		StringJoiner spent = new StringJoiner(", ");
		for (ServerPlayer p : players) {
			if (data.breathDay(p.getUUID()) == today) {
				spent.add(p.getName().getString());
			}
		}
		StringJoiner held = new StringJoiner(", ");
		long now = server.getTickCount();
		for (HeldBreath.Breath b : HeldBreath.LIVE.breaths()) {
			held.add(Fmt.f("%d for %s, %.1f s left", b.mobs().size(), names(server, b.players()), Math.max(0, b.until() - now) / 20.0));
		}
		return "23: " + (spent.length() == 0 ? "nobody spent today" : "spent today " + spent) + (held.length() == 0 ? ", none going" : ", going " + held);
	}

	private static String line24(MinecraftServer server) {
		Follows.Follow f = Follows.LIVE.current();
		String going = "none going";
		if (f != null) {
			going = Fmt.f("going for %s (%s, %.2f d left)", name(server, f.target().id()), Fmt.lower(f.mode()), daysLeft(f));
		}
		return "24: " + caps(server, "24", Sc24Config.get().maxPerWorld) + ", " + going;
	}

	private static double daysLeft(Follows.Follow f) {
		return Math.max(0, f.untilClock() - f.mob().level().getDefaultClockTime()) / (double) DAY_TICKS;
	}

	// --- 25 and 26: the planner's trap kinds ---

	private static String trap(MinecraftServer server, List<ServerPlayer> players, String number, String kind, int max) {
		HuntData data = HuntData.get(server);
		StringJoiner armed = new StringJoiner(", ");
		for (ServerPlayer p : players) {
			Optional<TrapType> trap = Services.accidents().armed(p.getUUID());
			if (trap.isPresent() && trap.get().id().equals(kind) && Hunts.of(p.getUUID()).isEmpty()) {
				armed.add(p.getName().getString());
			}
		}
		StringJoiner going = new StringJoiner(", ");
		long now = server.getTickCount();
		for (HuntScene scene : Hunts.running()) {
			if (scene.kind().equals(kind)) {
				going.add(Fmt.f("%s %.0f s%s", name(server, scene.target()), (now - scene.startedAt()) / 20.0, scene.hostileAt() >= 0 ? ", hostile" : ""));
			}
		}
		return Fmt.f("%s: %d of %d", number, data.count(kind), max) + (armed.length() == 0 ? ", not armed" : ", armed " + armed)
				+ (going.length() == 0 ? ", none going" : ", going " + going);
	}

	// --- 27 to 31 ---

	private static String line27(MinecraftServer server, List<ServerPlayer> players) {
		Sc27Data data = Sc27Data.get(server);
		int now = server.getTickCount();
		StringJoiner on = new StringJoiner(", ");
		for (Sc27.Showing s : Sc27.LIVE.showings()) {
			on.add(Fmt.f("%s %.0f s", name(server, s.viewer()), (now - s.startTick()) / 20.0));
		}
		StringJoiner armed = new StringJoiner(", ");
		for (ServerPlayer p : players) {
			long left = Sc27.LIVE.armTicksLeft(p.getUUID(), now);
			if (left >= 0) {
				armed.add(p.getName().getString() + Fmt.f(" %.1f min", left / 1200.0));
			}
		}
		return Fmt.f("27: %d of %d", data.count(), Sc27Config.get().maxPerWorld) + (on.length() == 0 ? ", none going" : ", going " + on)
				+ (armed.length() == 0 ? ", no debug arm" : ", debug arm " + armed);
	}

	private static String line28(MinecraftServer server) {
		S28Data data = S28Data.get(server);
		return Fmt.f("28: %d of %d, last %s, %d pairs met, %s", data.count(), Sc28Config.get().maxPerWorld, data.lastDay() < 0 ? "never" : "day " + data.lastDay(),
				data.visits().size(), S28.pending() ? "giving back" : "nothing to give back");
	}

	private static String line30(MinecraftServer server) {
		S30Data data = S30Data.get(server);
		return Fmt.f("30: %d of %d, %d kept, %d waiting", data.count(), Sc30Config.get().maxPerWorld, data.edits().size(), data.pending().size());
	}

	/** A card without counts of its own: each participant's real fires of it in their pacing's history, and the last day. */
	static String fired(MinecraftServer server, List<ServerPlayer> players, String cardId) {
		if (players.isEmpty()) {
			return "nobody taking part";
		}
		DirectorMemory memory = DirectorData.get(server).memory();
		StringJoiner each = new StringJoiner(", ");
		for (ServerPlayer p : players.subList(0, Math.min(players.size(), DebugCommands.MAX_PLAYER_LINES))) {
			int count = 0;
			long last = -1;
			Optional<PacingMemory> pacing = memory.find(p.getUUID());
			if (pacing.isPresent()) {
				for (DirectorMemory.HistoryEntry e : pacing.get().history()) {
					if (e.cardId().equals(cardId) && !e.forced()) {
						count++;
						last = Math.max(last, e.day());
					}
				}
			}
			each.add(p.getName().getString() + " " + count + (last >= 0 ? " (last day " + last + ")" : ""));
		}
		return each.toString();
	}

	// --- the overlay ---

	/**
	 * This participant's parts for 16 to 31 in the overlay's row: the place they are inside as its number ("in 18"),
	 * and every scene or arm of theirs with its time left (21 to 24 going, 25 and 26 armed or going, 27 going or armed, 31
	 * or 31c out for them). 28, 29 and 30 have none (a move, a few seconds of sound, a map changed at once).
	 */
	static void arms(MinecraftServer server, ServerPlayer player, StringJoiner out) {
		UUID id = player.getUUID();
		Visits.LIVE.placeOf(id).ifPresent(place -> out.add("in " + place.kind().number()));
		for (Rings.Scene scene : Rings.LIVE.scenes()) {
			if (scene.sharers().contains(id)) {
				out.add("21 " + Fmt.lower(scene.phase()));
				break;
			}
		}
		for (Processions.Procession p : Processions.LIVE.processions()) {
			if (p.sharers().contains(id)) {
				out.add("22 " + Fmt.lower(p.phase()));
				break;
			}
		}
		long now = server.getTickCount();
		for (HeldBreath.Breath b : HeldBreath.LIVE.breaths()) {
			if (b.players().contains(id)) {
				out.add(Fmt.f("23 %.1fs", Math.max(0, b.until() - now) / 20.0));
				break;
			}
		}
		Follows.Follow f = Follows.LIVE.current();
		if (f != null && f.target().id().equals(id)) {
			out.add(Fmt.f("24 %.2fd", daysLeft(f)));
		}
		Optional<HuntScene> scene = Hunts.of(id);
		if (scene.isPresent()) {
			out.add(Fmt.f("%s going %.0fs", scene.get().kind().substring(2), (now - scene.get().startedAt()) / 20.0));
		} else {
			Services.accidents().armed(id).filter(t -> t.id().equals(Pack.KIND) || t.id().equals(Call.KIND))
					.ifPresent(t -> out.add(t.id().substring(2) + " armed"));
		}
		int tick = server.getTickCount();
		Sc27.LIVE.showing(id).ifPresent(s -> out.add(Fmt.f("27 %.0fs", Math.max(0, s.lifeTicks() - (tick - s.startTick())) / 20.0)));
		long arm = Sc27.LIVE.armTicksLeft(id, tick);
		if (arm >= 0) {
			out.add(Fmt.f("27 armed %.1fm", arm / 1200.0));
		}
		for (HimEntity him : FigureApi.active(server)) {
			if (id.equals(him.appearedFor()) && (him.variant() == Variant.APEX || him.variant() == Variant.INSIDE_TRUNKS)) {
				out.add(him.variant() == Variant.APEX ? "31 out" : "31c out");
			}
		}
	}

	// --- helpers ---

	private static String names(MinecraftServer server, List<UUID> players) {
		if (players.isEmpty()) {
			return "nobody";
		}
		StringJoiner joiner = new StringJoiner("+");
		players.forEach(p -> joiner.add(name(server, p)));
		return joiner.toString();
	}

	private static String name(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		return online != null ? online.getName().getString() : player.toString().substring(0, 8);
	}
}
