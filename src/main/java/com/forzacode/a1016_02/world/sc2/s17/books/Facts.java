package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * What the mod knows about one player, for their record (docs/SELF_CREATED_2_R3.md, "What the mod knows"): an immutable
 * bag of facts, each a kind of line ({@code b} their first day, {@code c} to {@code n} the entries), a day, a number,
 * its slots' values (words from registry ids, spaces for underscores; a cause and a place by key, the texts' maps turn
 * them into words) and its conditions' tags, ranked by how telling it is. Only in-game data: never a real time, a file or
 * anything outside the world. {@link #of} gathers it on the server thread; {@link #from} derives it from plain
 * {@link Inputs} (the tests build those by hand).
 */
public record Facts(long seed, UUID subject, String name, int today, int firstDay, List<String> friends, List<Integer> played, Fact firstDayFact,
		List<Fact> entries) {
	/** Facts of one kind kept at most (the most recent). */
	public static final int PER_KIND = 6;

	/** One fact: a line's kind, its day, a number, days away, the slots it fills and the tags its conditions read. */
	public record Fact(char kind, int day, int n, int away, Map<String, String> slots, Set<String> tags, int rank) {
		/** True if a line's condition holds for this fact. */
		public boolean meets(String condition) {
			if (condition.startsWith("n>=")) {
				return slots.containsKey("n") && n >= Integer.parseInt(condition.substring(3));
			}
			if (condition.startsWith("away>=")) {
				return away >= Integer.parseInt(condition.substring(6));
			}
			if (condition.equals("friend")) {
				return slots.containsKey("friend");
			}
			return tags.contains(condition);
		}
	}

	/** A place a fact names: a word (a block's), where, how many. */
	public record Spot(String word, int x, int z, int n) {
	}

	/**
	 * The raw inputs: the world's seed, today (the books' day), the known players' names (their indices are the log's),
	 * the subject's index and UUID, the day log, their marked deaths (the books' day, the list's cause word), their firsts
	 * (block, crafting table, chest), their home (a biome word or empty), what they did lately ({@code e} mining runs,
	 * {@code i} doors and chests) and their statistics.
	 */
	public record Inputs(long seed, int today, List<String> names, int subject, UUID uuid, DayLog log, List<Map.Entry<Integer, String>> marked,
			List<Spot> firsts, @Nullable Spot home, String homeBiome, List<Spot> mining, List<Spot> doors, DayLog.Snapshot stats) {
	}

	/** The facts of {@code subject} in this world now (an unknown player has a name of "" and few facts). Server thread. */
	public static Facts of(ServerLevel level, UUID subject) {
		return from(inputs(level, subject));
	}

	/** Gathers the inputs from the server's state, the day log, the watch and the statistics. Server thread. */
	public static Inputs inputs(ServerLevel level, UUID subject) {
		MinecraftServer server = level.getServer();
		HerobrineState state = HerobrineState.get(server);
		List<HerobrineState.Subject> players = state.players();
		List<String> names = new ArrayList<>();
		int index = -1;
		for (int i = 0; i < players.size(); i++) {
			names.add(players.get(i).name().toLowerCase(Locale.ROOT));
			if (players.get(i).uuid().equals(subject)) {
				index = i;
			}
		}
		DayLog log = DayLog.get(server);
		List<Map.Entry<Integer, String>> marked = new ArrayList<>();
		for (MarkedDeath death : state.markedDeaths(subject)) {
			marked.add(Map.entry((int) death.day() + 1, death.cause()));
		}
		List<Spot> firsts = new ArrayList<>();
		HerobrineState.FirstBlocks blocks = state.firstBlocks(subject);
		for (PlacedBlock placed : new PlacedBlock[] {blocks.block(), blocks.craftingTable(), blocks.chest()}) {
			if (placed != null) {
				firsts.add(new Spot(word(placed.state()), placed.pos().pos().getX(), placed.pos().pos().getZ(), 1));
			}
		}
		ServerPlayer online = Services.watch().participant(server, subject).orElse(null);
		GlobalPos base = online != null ? Services.watch().base(online).orElse(null) : blocks.block() == null ? null : blocks.block().pos();
		Spot home = null;
		String biome = "";
		if (base != null) {
			home = new Spot("", base.pos().getX(), base.pos().getZ(), 1);
			ServerLevel there = server.getLevel(base.dimension());
			if (there != null && there.hasChunkAt(base.pos())) {
				biome = there.getBiome(base.pos()).unwrapKey().map(k -> spaced(k.identifier().getPath())).orElse("");
			}
		}
		List<Spot> mining = new ArrayList<>();
		List<Spot> doors = new ArrayList<>();
		DayLog.Snapshot stats = log.snapshot(subject);
		if (online != null) {
			for (PlayerAction action : Services.watch().recentActions(online)) {
				switch (action) {
					case PlayerAction.Mining run when run.breaks().size() >= 2 -> {
						BlockPos at = run.breaks().getFirst().pos();
						mining.add(new Spot(word(run.breaks().getFirst().state()), at.getX(), at.getZ(), run.breaks().size()));
					}
					case PlayerAction.DoorUse door -> doors.add(new Spot(word(door.state()), door.pos().getX(), door.pos().getZ(), 1));
					case PlayerAction.ContainerOpen box -> doors.add(new Spot(word(box.state()), box.pos().getX(), box.pos().getZ(), 1));
					default -> {
					}
				}
			}
			stats = LogEvents.snapshot(online);
		}
		int today = (int) GameClock.day(server) + 1;
		return new Inputs(level.getSeed(), today, names, index, subject, log, marked, firsts, home, biome, mining, doors, stats);
	}

	/** The facts these inputs hold. Pure. */
	public static Facts from(Inputs in) {
		String name = in.subject() >= 0 && in.subject() < in.names().size() ? in.names().get(in.subject()) : "";
		List<String> friends = new ArrayList<>();
		for (int i = 0; i < in.names().size(); i++) {
			if (i != in.subject()) {
				friends.add(in.names().get(i));
			}
		}
		List<DayLog.Day> days = in.log().all();
		List<Integer> played = new ArrayList<>();
		for (DayLog.Day day : days) {
			if (day.online(in.subject())) {
				played.add(day.day());
			}
		}
		int first = played.isEmpty() ? Math.max(1, in.today()) : played.getFirst();
		int last = played.isEmpty() ? Math.max(1, in.today()) : played.getLast();
		Map<Character, List<Fact>> byKind = new LinkedHashMap<>();
		DayLog.Day firstDay = in.log().day(first);
		Fact firstFact = fact('b', first, 0, 0, Map.of(), tags(firstDay, in, Set.of("first")), 0, friendOn(firstDay, in));
		// deaths: marked (d) and vanilla (c), numbered together in order
		record Died(int day, String cause, boolean marked, boolean night, DayLog.@Nullable Day kept) {
		}
		List<Died> died = new ArrayList<>();
		in.marked().forEach(m -> died.add(new Died(m.getKey(), m.getValue(), true, false, in.log().day(m.getKey()))));
		for (DayLog.Day day : days) {
			for (DayLog.Death death : day.deaths()) {
				if (death.player() == in.subject() && !death.marked()) {
					died.add(new Died(day.day(), death.cause(), false, death.night(), day));
				}
			}
		}
		died.sort(Comparator.comparingInt(Died::day));
		for (int i = 0; i < died.size(); i++) {
			Died d = died.get(i);
			Set<String> extra = new HashSet<>();
			extra.add("cause:" + d.cause());
			if (d.night()) {
				extra.add("night");
			}
			if (i == 0) {
				extra.add("first");
			}
			add(byKind, fact(d.marked() ? 'd' : 'c', d.day(), i + 1, 0, Map.of("cause", d.cause()), tags(d.kept(), in, extra), d.marked() ? 110 : 100,
					friendOn(d.kept(), in)));
		}
		// mining (e): the most mined block, and the runs of this session
		if (in.stats().minedCount() > 0 && !in.stats().mined().isEmpty()) {
			add(byKind, fact('e', last, in.stats().minedCount(), 0, Map.of("block", in.stats().mined()), tags(in.log().day(last), in, Set.of()), 40, null));
		}
		for (Spot run : in.mining()) {
			add(byKind, fact('e', in.today(), run.n(), 0, spot(run, "block"), tags(in.log().day(in.today()), in, Set.of()), 42, null));
		}
		// firsts (f) and home (g)
		for (Spot spot : in.firsts()) {
			add(byKind, fact('f', first, 0, 0, spot(spot, "block"), Set.of("first"), 45, null));
		}
		if (in.home() != null) {
			Map<String, String> slots = new HashMap<>(Map.of("x", String.valueOf(in.home().x()), "z", String.valueOf(in.home().z())));
			if (!in.homeBiome().isEmpty()) {
				slots.put("biome", in.homeBiome());
			}
			add(byKind, fact('g', first, 0, 0, slots, Set.of(), 50, null));
		}
		Set<Integer> together = new HashSet<>();
		for (DayLog.Day day : days) {
			// nights out (h)
			if (day.out(in.subject())) {
				add(byKind, fact('h', day.day(), 0, 0, Map.of(), tags(day, in, Set.of("night")), 60, friendOn(day, in)));
			}
			// doors and chests at a friend's (i), a friend at theirs (j)
			for (DayLog.Visit visit : day.visits()) {
				if (visit.actor() == in.subject() && visit.owner() < in.names().size()) {
					add(byKind, fact('i', day.day(), 0, 0, Map.of("block", visit.word()), tags(day, in, Set.of()), 85, in.names().get(visit.owner())));
				} else if (visit.owner() == in.subject() && visit.actor() < in.names().size()) {
					add(byKind, fact('j', day.day(), 0, 0, Map.of("block", visit.word()), tags(day, in, Set.of()), 90, in.names().get(visit.actor())));
				}
			}
			// the first day with each friend (j)
			if (day.online(in.subject())) {
				for (int other : day.who()) {
					if (other != in.subject() && other < in.names().size() && together.add(other)) {
						Set<String> extra = day.out(in.subject()) && day.out(other) ? Set.of("night") : Set.of();
						add(byKind, fact('j', day.day(), 0, 0, Map.of(), tags(day, in, extra), 88, in.names().get(other)));
					}
				}
			}
			// what he took (k)
			for (DayLog.Took took : day.took()) {
				if (took.player() == in.subject()) {
					add(byKind, fact('k', day.day(), took.count(), 0, Map.of(took.item() ? "item" : "block", took.word()), tags(day, in, Set.of()), 80, null));
				}
			}
		}
		for (Spot door : in.doors()) {
			add(byKind, fact('i', in.today(), 0, 0, spot(door, "block"), tags(in.log().day(in.today()), in, Set.of()), 70, null));
		}
		// places (l)
		for (int kind = 16; kind <= 20; kind++) {
			int day = in.log().placeDay(in.uuid(), kind);
			if (day > 0) {
				add(byKind, fact('l', day, 0, 0, Map.of("place", String.valueOf(kind)), tags(in.log().day(day), in, Set.of("first")), 75, null));
			}
		}
		// statistics (m)
		DayLog.Snapshot s = in.stats();
		for (int n : new int[] {s.walked(), s.jumps(), s.kills(), s.slept(), s.deaths()}) {
			if (n > 0) {
				add(byKind, fact('m', last, n, 0, Map.of(), Set.of(), 30, null));
			}
		}
		// days away (n)
		for (int i = 1; i < played.size(); i++) {
			int away = played.get(i) - played.get(i - 1) - 1;
			if (away >= 1) {
				add(byKind, fact('n', played.get(i), away, away, Map.of(), tags(in.log().day(played.get(i)), in, Set.of()), 70, null));
			}
		}
		List<Fact> entries = new ArrayList<>();
		byKind.values().forEach(entries::addAll);
		return new Facts(in.seed(), in.uuid(), name, in.today(), first, List.copyOf(friends), List.copyOf(played), firstFact, List.copyOf(entries));
	}

	private static void add(Map<Character, List<Fact>> byKind, Fact fact) {
		List<Fact> list = byKind.computeIfAbsent(fact.kind(), k -> new ArrayList<>());
		list.add(fact);
		if (list.size() > PER_KIND) {
			list.sort(Comparator.comparingInt(Fact::day));
			list.removeFirst();
		}
	}

	private static Fact fact(char kind, int day, int n, int away, Map<String, String> slots, Set<String> tags, int rank, @Nullable String friend) {
		Map<String, String> all = new HashMap<>(slots);
		all.put("day", String.valueOf(day));
		if (n > 0) {
			all.put("n", String.valueOf(n));
		}
		if (friend != null) {
			all.put("friend", friend);
		}
		return new Fact(kind, day, n, away, Map.copyOf(all), Set.copyOf(tags), rank);
	}

	private static Map<String, String> spot(Spot spot, String key) {
		Map<String, String> slots = new HashMap<>();
		slots.put(key, spot.word());
		slots.put("x", String.valueOf(spot.x()));
		slots.put("z", String.valueOf(spot.z()));
		if (spot.n() > 1) {
			slots.put("n", String.valueOf(spot.n()));
		}
		return slots;
	}

	/** A day's tags: rain (or worse), alone (only the subject online), with the extra ones. */
	private static Set<String> tags(DayLog.@Nullable Day day, Inputs in, Set<String> extra) {
		Set<String> tags = new HashSet<>(extra);
		if (day != null) {
			if (day.weather() >= DayLog.RAIN) {
				tags.add("rain");
			}
			if (day.who().equals(List.of(in.subject()))) {
				tags.add("alone");
			}
		}
		return tags;
	}

	/** Someone else online that day (the lowest index), or null. */
	private static @Nullable String friendOn(DayLog.@Nullable Day day, Inputs in) {
		if (day != null) {
			for (int other : day.who()) {
				if (other != in.subject() && other < in.names().size()) {
					return in.names().get(other);
				}
			}
		}
		return null;
	}

	/** A block's word: its id's path, spaces for underscores. */
	static String word(BlockState state) {
		return spaced(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
	}

	static String spaced(String path) {
		return path.replace('_', ' ');
	}
}
