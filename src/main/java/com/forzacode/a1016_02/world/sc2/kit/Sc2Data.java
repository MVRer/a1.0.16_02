package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.Remains;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the second wave's places remember, kept in {@code WorldData} under the optional key {@code sc2}: every known
 * place with how it was started (to rebuild its interior), who has been inside it and what it has answered; each
 * player's last time inside any of them and the answers each one had; what a feature fixed on its first build
 * ({@link Plans}: the line's segments). Every field is optional and read leniently, one
 * entry at a time: a save from before loads with nothing, an older jar skips the key, and an entry that cannot be read
 * (a kind this jar does not know) is left out rather than failing the rest. Server thread only; every change marks the
 * data dirty. At most a few hundred bytes per place (D-104: 2 KB).
 */
public final class Sc2Data {
	/** No day yet. */
	public static final long NEVER = Long.MIN_VALUE;

	/** How often an answer happened: the day of the last, how many that day, how many ever. */
	public record Spent(long day, int onDay, int ever) {
		public static final Spent NONE = new Spent(NEVER, 0, 0);

		static final Codec<Spent> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("d", NEVER).forGetter(Spent::day),
				Codec.INT.optionalFieldOf("n", 0).forGetter(Spent::onDay),
				Codec.INT.optionalFieldOf("e", 0).forGetter(Spent::ever)
		).apply(i, Spent::new));

		/** How many happened on {@code today}. */
		public int on(long today) {
			return day == today ? onDay : 0;
		}

		/** One more, today. */
		public Spent plusOne(long today) {
			return new Spent(today, on(today) + 1, ever + 1);
		}
	}

	/**
	 * One known place.
	 *
	 * @param place    the place
	 * @param kind     its kind's id ({@link Sc2Kind#id})
	 * @param start    how it was started (tied to its shape's version)
	 * @param extra    its shape's {@link Shape#extra}
	 * @param visitors who has been inside, in the order they first were
	 * @param spent    each answer's counts at this place
	 */
	public record Known(Remains.Place place, String kind, Start start, CompoundTag extra, List<UUID> visitors, Map<String, Spent> spent) {
		public Known {
			extra = extra.copy();
			visitors = List.copyOf(visitors);
			spent = Map.copyOf(spent);
			// Always kept with its shape's version (R12-2): its inside and its answers rebuild that version.
			start = Sc2Kind.resolved(kind, start);
		}

		/** A place just recorded: nobody inside yet, nothing answered. */
		public static Known of(Remains.Place place, String kind, Start start, CompoundTag extra) {
			return new Known(place, kind, start, extra, List.of(), Map.of());
		}

		@Override
		public CompoundTag extra() {
			return extra.copy();
		}

		/** The key one start is known by: its level, its kind and its origin. */
		public String key() {
			return keyOf(place.dimension().identifier().toString(), kind, start.origin());
		}

		Known withVisitor(UUID player) {
			List<UUID> more = new ArrayList<>(visitors);
			more.add(player);
			return new Known(place, kind, start, extra, more, spent);
		}

		Known withSpent(String answer, Spent now) {
			Map<String, Spent> more = new HashMap<>(spent);
			more.put(answer, now);
			return new Known(place, kind, start, extra, visitors, more);
		}
	}

	static String keyOf(String dimension, String kind, BlockPos origin) {
		return dimension + "/" + kind + "/" + origin.asLong();
	}

	private static final Codec<Remains.Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
		try {
			return DataResult.success(Remains.Kind.valueOf(name));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "unknown kind " + name);
		}
	}, Remains.Kind::name);

	static final Codec<Remains.Place> PLACE_CODEC = RecordCodecBuilder.create(i -> i.group(
			KIND_CODEC.fieldOf("kind").forGetter(Remains.Place::kind),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Remains.Place::dimension),
			BoundingBox.CODEC.fieldOf("box").forGetter(Remains.Place::box),
			BlockPos.CODEC.fieldOf("anchor").forGetter(Remains.Place::anchor),
			BlockPos.CODEC.fieldOf("entrance").forGetter(Remains.Place::entrance),
			Direction.CODEC.fieldOf("facing").forGetter(Remains.Place::entranceFacing),
			BlockPos.CODEC.optionalFieldOf("apex").forGetter(Remains.Place::apex),
			Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Remains.Place::seed),
			Codec.INT.optionalFieldOf("variant", 0).forGetter(Remains.Place::variant)
	).apply(i, Remains.Place::new));

	static final Codec<Start> START_CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("origin").forGetter(Start::origin),
			Direction.CODEC.optionalFieldOf("facing", Direction.NORTH).forGetter(Start::facing),
			Codec.INT.optionalFieldOf("variant", 0).forGetter(Start::variant),
			Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Start::seed),
			Codec.INT.optionalFieldOf("g", Start.FIRST).forGetter(start -> start.versionOr(Start.FIRST))
	).apply(i, Start::new));

	static final Codec<Known> KNOWN_CODEC = RecordCodecBuilder.create(i -> i.group(
			PLACE_CODEC.fieldOf("place").forGetter(Known::place),
			Codec.STRING.fieldOf("kind").forGetter(Known::kind),
			START_CODEC.fieldOf("start").forGetter(Known::start),
			CompoundTag.CODEC.optionalFieldOf("extra", new CompoundTag()).forGetter(Known::extra),
			UUIDUtil.CODEC.listOf().lenientOptionalFieldOf("visitors", List.of()).forGetter(Known::visitors),
			Codec.unboundedMap(Codec.STRING, Spent.CODEC).lenientOptionalFieldOf("spent", Map.of()).forGetter(Known::spent)
	).apply(i, Known::new));

	private record PlayerMarks(UUID player, long trespass, Map<String, Spent> spent) {
		static final Codec<PlayerMarks> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerMarks::player),
				Codec.LONG.optionalFieldOf("trespass", -1L).forGetter(PlayerMarks::trespass),
				Codec.unboundedMap(Codec.STRING, Spent.CODEC).lenientOptionalFieldOf("spent", Map.of()).forGetter(PlayerMarks::spent)
		).apply(i, PlayerMarks::new));
	}

	/** A list read one entry at a time: an entry that cannot be read is left out. */
	static <T> Codec<List<T>> lenientList(Codec<T> element) {
		return Codec.list(Codec.PASSTHROUGH).xmap(
				all -> all.stream().map(d -> element.parse(d).result()).flatMap(Optional::stream).toList(),
				list -> list.stream().<Dynamic<?>>map(e -> new Dynamic<>(NbtOps.INSTANCE, element.encodeStart(NbtOps.INSTANCE, e).getOrThrow())).toList());
	}

	public static final Codec<Sc2Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			lenientList(KNOWN_CODEC).lenientOptionalFieldOf("places", List.of()).forGetter(d -> List.copyOf(d.places.values())),
			lenientList(PlayerMarks.CODEC).lenientOptionalFieldOf("players", List.of()).forGetter(Sc2Data::playerMarks),
			Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).lenientOptionalFieldOf("plans", Map.of()).forGetter(Sc2Data::plans)
	).apply(i, Sc2Data::new));

	private final Map<String, Known> places = new LinkedHashMap<>();
	private final Map<Remains.Place, String> keys = new HashMap<>();
	private final Map<UUID, Long> trespass = new HashMap<>();
	private final Map<UUID, Map<String, Spent>> playerSpent = new HashMap<>();
	/** What features fixed on their first build in this world ({@link Plans}), by key. */
	private final Map<String, CompoundTag> plans = new LinkedHashMap<>();
	private int placesVersion;
	private Runnable dirty = () -> {
	};

	/** Nothing known yet (tests use this too). */
	public Sc2Data() {
	}

	private Sc2Data(List<Known> places, List<PlayerMarks> players, Map<String, CompoundTag> plans) {
		places.forEach(this::put);
		plans.forEach((key, tag) -> this.plans.put(key, tag.copy()));
		for (PlayerMarks marks : players) {
			if (marks.trespass() >= 0) {
				trespass.put(marks.player(), marks.trespass());
			}
			if (!marks.spent().isEmpty()) {
				playerSpent.put(marks.player(), new HashMap<>(marks.spent()));
			}
		}
	}

	private List<PlayerMarks> playerMarks() {
		Set<UUID> players = new LinkedHashSet<>(trespass.keySet());
		players.addAll(playerSpent.keySet());
		List<PlayerMarks> out = new ArrayList<>();
		for (UUID player : players) {
			out.add(new PlayerMarks(player, trespass.getOrDefault(player, -1L), playerSpent.getOrDefault(player, Map.of())));
		}
		return out;
	}

	private void put(Known known) {
		String key = known.key();
		Known before = places.put(key, known);
		if (before != null) {
			keys.remove(before.place());
		}
		keys.put(known.place(), key);
	}

	/** Called by {@code WorldData}: what marks it dirty. */
	public void onChange(Runnable markDirty) {
		this.dirty = markDirty;
	}

	/** True if nothing is remembered (the key is then not written). */
	public boolean isEmpty() {
		return places.isEmpty() && trespass.isEmpty() && playerSpent.isEmpty() && plans.isEmpty();
	}

	/** Every fixed plan by key (a copy). */
	public Map<String, CompoundTag> plans() {
		Map<String, CompoundTag> out = new LinkedHashMap<>();
		plans.forEach((key, tag) -> out.put(key, tag.copy()));
		return out;
	}

	/** Keeps a plan fixed under {@code key}; false (nothing changes) if one is kept there already: the first stays. */
	public boolean fixPlan(String key, CompoundTag plan) {
		if (plans.containsKey(key)) {
			return false;
		}
		plans.put(key, plan.copy());
		dirty.run();
		return true;
	}

	/** Raised each time a place is added: indexes rebuild when it changes. */
	public int placesVersion() {
		return placesVersion;
	}

	/** Every known place, oldest first (unmodifiable). */
	public List<Known> places() {
		return Collections.unmodifiableList(new ArrayList<>(places.values()));
	}

	/** The record of this place, if known. */
	public Optional<Known> known(Remains.Place place) {
		String key = keys.get(place);
		return key == null ? Optional.empty() : Optional.ofNullable(places.get(key));
	}

	/** Adds a place; false (nothing changes) if a place with the same level, kind and origin is known. */
	public boolean add(Known known) {
		if (places.containsKey(known.key())) {
			return false;
		}
		put(known);
		placesVersion++;
		dirty.run();
		return true;
	}

	/** True if this player has been inside this place. */
	public boolean visited(UUID player, Remains.Place place) {
		return known(place).map(k -> k.visitors().contains(player)).orElse(false);
	}

	/** Remembers that this player was inside this place (once). */
	public void visit(UUID player, Remains.Place place) {
		known(place).filter(k -> !k.visitors().contains(player)).ifPresent(k -> {
			put(k.withVisitor(player));
			dirty.run();
		});
	}

	/** The game time this player was last inside any place, -1 if never. */
	public long trespassedAt(UUID player) {
		return trespass.getOrDefault(player, -1L);
	}

	public void trespass(UUID player, long gameTime) {
		Long before = trespass.put(player, gameTime);
		if (before == null || before != gameTime) {
			dirty.run();
		}
	}

	/** This answer's counts at this place. */
	public Spent spent(Remains.Place place, String answer) {
		return known(place).map(k -> k.spent().getOrDefault(answer, Spent.NONE)).orElse(Spent.NONE);
	}

	/** This answer's counts for this player (anywhere). */
	public Spent spent(UUID player, String answer) {
		return playerSpent.getOrDefault(player, Map.of()).getOrDefault(answer, Spent.NONE);
	}

	/** One more of this answer, today, at this place and for each of these players. */
	public void spend(Remains.Place place, String answer, List<UUID> players, long today) {
		known(place).ifPresent(k -> put(k.withSpent(answer, k.spent().getOrDefault(answer, Spent.NONE).plusOne(today))));
		for (UUID player : players) {
			Map<String, Spent> mine = playerSpent.computeIfAbsent(player, p -> new HashMap<>());
			mine.put(answer, mine.getOrDefault(answer, Spent.NONE).plusOne(today));
		}
		dirty.run();
	}
}
