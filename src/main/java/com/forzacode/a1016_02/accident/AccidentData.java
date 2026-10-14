package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The accident workstream's saved data ({@code data/a1016_02/accident.dat}): one armed trap per player (D-059), dark
 * corners still waiting to be put back, crosses waiting to be built and the ones standing, each player's own learned
 * routes and cairn visits (D-061), and a short history. Everything per player is keyed by UUID (D-062.2).
 * <p>
 * Old saves (D-062.8): a v1.0 file had one armed trap, one route book and one cairn count, all the subject's. They load
 * as pending and become the first known player's ({@link HerobrineState#players()}) on the first {@link #get}; the old
 * fields are written again only while nobody is known to take them.
 */
public final class AccidentData extends SavedData {
	/** A marked death whose cross is not built yet, and whose death it was (empty for a debug preview). */
	public record PendingCross(GlobalPos pos, String cause, int height, int attempts, Optional<UUID> player) {
		static final Codec<PendingCross> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(PendingCross::pos),
				Codec.STRING.fieldOf("cause").forGetter(PendingCross::cause),
				Codec.INT.fieldOf("height").forGetter(PendingCross::height),
				Codec.INT.optionalFieldOf("attempts", 0).forGetter(PendingCross::attempts),
				UUIDUtil.CODEC.optionalFieldOf("player").forGetter(PendingCross::player)
		).apply(i, PendingCross::new));

		/** A cross nobody's death stands behind (a debug preview, or a test). */
		public PendingCross(GlobalPos pos, String cause, int height, int attempts) {
			this(pos, cause, height, attempts, Optional.empty());
		}

		PendingCross retried() {
			return new PendingCross(pos, cause, height, attempts + 1, player);
		}
	}

	/**
	 * A cross he built: the death spot it was built for, the bottom of its post, its cells and the block its post stands
	 * on (never taken for another cross), whose death it was, and whether it stands for a marked death (a debug preview
	 * does not).
	 */
	public record BuiltCross(GlobalPos death, GlobalPos base, Set<BlockPos> cells, Optional<UUID> player, boolean marked) {
		private static final Codec<Set<BlockPos>> PACKED = Codec.LONG_STREAM.xmap(
				s -> new HashSet<>(s.mapToObj(BlockPos::of).toList()), set -> set.stream().mapToLong(BlockPos::asLong));
		static final Codec<BuiltCross> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("death").forGetter(BuiltCross::death),
				GlobalPos.CODEC.fieldOf("base").forGetter(BuiltCross::base),
				PACKED.optionalFieldOf("cells", Set.of()).forGetter(BuiltCross::cells),
				UUIDUtil.CODEC.optionalFieldOf("player").forGetter(BuiltCross::player),
				Codec.BOOL.optionalFieldOf("marked", false).forGetter(BuiltCross::marked)
		).apply(i, BuiltCross::new));

		public BuiltCross {
			cells = Set.copyOf(cells);
		}
	}

	/** One player's armed trap, as saved. */
	private record Armed(UUID player, ArmedTrap trap) {
		static final Codec<Armed> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Armed::player),
				ArmedTrap.CODEC.fieldOf("trap").forGetter(Armed::trap)
		).apply(i, Armed::new));
	}

	/**
	 * One player's own records here (D-061): the cells their feet passed through, and the cairn they leave offerings at
	 * with how many times they went back to it. The public fields are this session's and are not saved.
	 */
	public static final class PlayerData {
		static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(p -> p.uuid),
				RouteBook.CODEC.optionalFieldOf("routes").forGetter(p -> Optional.of(p.routes)),
				GlobalPos.CODEC.optionalFieldOf("cairn").forGetter(p -> Optional.ofNullable(p.cairn)),
				Codec.INT.optionalFieldOf("cairnVisits", 0).forGetter(p -> p.cairnVisits)
		).apply(i, (uuid, routes, cairn, visits) -> new PlayerData(uuid, routes.orElseGet(RouteBook::new), cairn.orElse(null), visits)));

		final UUID uuid;
		private RouteBook routes;
		private @Nullable GlobalPos cairn;
		private int cairnVisits;
		/** True while this player is at their cairn. */
		public boolean atCairn;
		/** Which way they last walked (horizontal, unit length), and in which level. */
		public @Nullable Vec3 heading;
		public @Nullable ResourceKey<Level> headingDimension;
		/** The last play tick they touched fire or lava that traces back to a house fire's gap. */
		public long tracedBurnTick = Long.MIN_VALUE;
		/** The last play tick they dropped through an armed bridge gap. */
		public long gapPassTick = Long.MIN_VALUE;

		PlayerData(UUID uuid, RouteBook routes, @Nullable GlobalPos cairn, int cairnVisits) {
			this.uuid = uuid;
			this.routes = routes;
			this.cairn = cairn;
			this.cairnVisits = cairnVisits;
		}

		public UUID uuid() {
			return uuid;
		}

		public RouteBook routes() {
			return routes;
		}

		public Optional<GlobalPos> cairn() {
			return Optional.ofNullable(cairn);
		}

		public int cairnVisits() {
			return cairnVisits;
		}
	}

	/** What the planner sees the players doing at a grove this session (not saved). */
	public static final class GroveWatch {
		/** The count of player-placed leaves in the grove, and when that count last grew (play ticks). */
		public int leaves = -1;
		public long leavesTick = Long.MIN_VALUE;
	}

	/** White eyes, per armed trap (keyed by its player, not saved): when disc 13 started, and when the last torch went out. */
	public static final class RoomWatch {
		public long playStart = -1;
		public long stepTick = Long.MIN_VALUE;
	}

	static final int HISTORY_MAX = 12;
	/** Built crosses remembered (crosses are rare: a few per player at most). */
	static final int BUILT_MAX = 64;

	public static final Codec<AccidentData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Armed.CODEC.listOf().optionalFieldOf("traps", List.of()).forGetter(d -> d.armed.entrySet().stream().map(e -> new Armed(e.getKey(), e.getValue())).toList()),
			ArmedTrap.CODEC.listOf().optionalFieldOf("restoringCorners", List.of()).forGetter(d -> d.restoring),
			PendingCross.CODEC.listOf().optionalFieldOf("crosses", List.of()).forGetter(d -> d.crosses),
			BuiltCross.CODEC.listOf().optionalFieldOf("builtCrosses", List.of()).forGetter(d -> d.built),
			PlayerData.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(d -> List.copyOf(d.players.values())),
			Codec.STRING.listOf().optionalFieldOf("history", List.of()).forGetter(d -> d.history),
			GlobalPos.CODEC.optionalFieldOf("lastCross").forGetter(d -> Optional.ofNullable(d.lastCross)),
			GlobalPos.CODEC.optionalFieldOf("lastCrossDeath").forGetter(d -> Optional.ofNullable(d.lastCrossDeath)),
			// v1.0's single-subject fields: read for the migration, written again only while nobody has taken them.
			ArmedTrap.CODEC.optionalFieldOf("armed").forGetter(d -> Optional.ofNullable(d.legacyArmed)),
			ArmedTrap.CODEC.optionalFieldOf("restoring").forGetter(d -> Optional.empty()),
			RouteBook.CODEC.optionalFieldOf("routes").forGetter(d -> Optional.ofNullable(d.legacyRoutes)),
			Codec.INT.optionalFieldOf("cairnVisits", 0).forGetter(d -> d.legacyCairnVisits),
			GlobalPos.CODEC.optionalFieldOf("cairn").forGetter(d -> Optional.ofNullable(d.legacyCairn))
	).apply(i, AccidentData::new));

	public static final SavedDataType<AccidentData> TYPE = new SavedDataType<>(A1016_02.id("accident"), AccidentData::new, CODEC, null);

	/** Each player's armed trap, in the order they were armed (the last is the newest). */
	private final LinkedHashMap<UUID, ArmedTrap> armed = new LinkedHashMap<>();
	private final List<ArmedTrap> restoring = new ArrayList<>();
	private final List<PendingCross> crosses = new ArrayList<>();
	private final List<BuiltCross> built = new ArrayList<>();
	private final Map<UUID, PlayerData> players = new LinkedHashMap<>();
	private final List<String> history = new ArrayList<>();
	/** The bottom of the post of the newest cross built for a marked death, and that death's spot. */
	private @Nullable GlobalPos lastCross;
	private @Nullable GlobalPos lastCrossDeath;
	/** v1.0's subject's trap, routes and cairn, until the first known player takes them. */
	private @Nullable ArmedTrap legacyArmed;
	private @Nullable RouteBook legacyRoutes;
	private int legacyCairnVisits;
	private @Nullable GlobalPos legacyCairn;
	/** Not saved: what players are doing at each grove, by the grove's spot. */
	private final Map<GlobalPos, GroveWatch> groves = new HashMap<>();
	/** Not saved: white eyes progress, by the trap's player. */
	private final Map<UUID, RoomWatch> rooms = new HashMap<>();

	public AccidentData() {
	}

	private AccidentData(List<Armed> traps, List<ArmedTrap> restoringCorners, List<PendingCross> crosses, List<BuiltCross> built, List<PlayerData> players,
			List<String> history, Optional<GlobalPos> lastCross, Optional<GlobalPos> lastCrossDeath, Optional<ArmedTrap> legacyArmed,
			Optional<ArmedTrap> legacyRestoring, Optional<RouteBook> legacyRoutes, int legacyCairnVisits, Optional<GlobalPos> legacyCairn) {
		traps.forEach(t -> this.armed.put(t.player(), t.trap()));
		this.restoring.addAll(restoringCorners);
		legacyRestoring.ifPresent(this.restoring::add);
		this.crosses.addAll(crosses);
		this.built.addAll(built);
		players.forEach(p -> this.players.put(p.uuid, p));
		this.history.addAll(history);
		this.lastCross = lastCross.orElse(null);
		this.lastCrossDeath = lastCrossDeath.orElse(null);
		this.legacyArmed = legacyArmed.orElse(null);
		this.legacyRoutes = legacyRoutes.filter(r -> !r.isEmpty()).orElse(null);
		this.legacyCairnVisits = legacyCairnVisits;
		this.legacyCairn = legacyCairn.orElse(null);
	}

	/** The world's accident data; an old single-subject save becomes the first known player's here. */
	public static AccidentData get(MinecraftServer server) {
		AccidentData data = server.getDataStorage().computeIfAbsent(TYPE);
		if (data.hasLegacy()) {
			data.adoptLegacy(HerobrineState.get(server).players());
		}
		return data;
	}

	// --- old saves ---

	/** True while a v1.0 save's trap, routes or cairn wait for a player to take them. */
	public boolean hasLegacy() {
		return legacyArmed != null || legacyRoutes != null || legacyCairn != null || legacyCairnVisits > 0;
	}

	/**
	 * D-062.8: a v1.0 save's armed trap, routes and cairn visits were the subject's, who is the first known player. They
	 * become that player's (a trap only if that player has none). With nobody known yet they keep waiting. True if they
	 * were taken.
	 */
	public boolean adoptLegacy(List<HerobrineState.Subject> known) {
		if (!hasLegacy() || known.isEmpty()) {
			return false;
		}
		HerobrineState.Subject first = known.getFirst();
		PlayerData mine = personal(first.uuid());
		if (legacyArmed != null && !armed.containsKey(first.uuid())) {
			armed.put(first.uuid(), legacyArmed);
		}
		if (legacyRoutes != null && mine.routes.isEmpty()) {
			mine.routes = legacyRoutes;
		}
		if (legacyCairn != null && mine.cairn == null) {
			mine.cairn = legacyCairn;
			mine.cairnVisits = legacyCairnVisits;
		}
		legacyArmed = null;
		legacyRoutes = null;
		legacyCairn = null;
		legacyCairnVisits = 0;
		log("an old save's trap and routes are " + first.name() + "'s now");
		A1016_02.LOGGER.info("[a1016] accident: the old single-player trap, routes and cairn visits are {}'s now", first.name());
		setDirty();
		return true;
	}

	// --- armed traps (one per player, D-059) ---

	/** Every armed trap by its player, in the order they were armed (the last is the newest). Unmodifiable. */
	public Map<UUID, ArmedTrap> traps() {
		return Collections.unmodifiableMap(armed);
	}

	/** This player's armed trap. */
	public Optional<ArmedTrap> armed(UUID player) {
		return Optional.ofNullable(armed.get(player));
	}

	/** The newest armed trap, whoever's it is (the old one-trap view; empty if nothing is armed). */
	public Optional<ArmedTrap> armed() {
		return newest().map(Map.Entry::getValue);
	}

	/** The newest armed trap and its player. */
	public Optional<Map.Entry<UUID, ArmedTrap>> newest() {
		Map.Entry<UUID, ArmedTrap> last = null;
		for (Map.Entry<UUID, ArmedTrap> entry : armed.entrySet()) {
			last = entry;
		}
		return Optional.ofNullable(last).map(e -> Map.entry(e.getKey(), e.getValue()));
	}

	/**
	 * Sets this player's armed trap (null drops it). A new trap goes to the end of the arming order; an update of the one
	 * already armed keeps its place.
	 */
	public void setArmed(UUID player, @Nullable ArmedTrap trap) {
		if (trap == null) {
			armed.remove(player);
		} else if (armed.containsKey(player) && armed.get(player).armedAt() == trap.armedAt() && armed.get(player).type().equals(trap.type())) {
			armed.put(player, trap);
		} else {
			armed.remove(player);
			armed.put(player, trap);
		}
		setDirty();
	}

	/** Dark corners that were disarmed or expired before their torches went back. Unmodifiable. */
	public List<ArmedTrap> restoring() {
		return Collections.unmodifiableList(restoring);
	}

	public void addRestoring(ArmedTrap trap) {
		restoring.add(trap);
		setDirty();
	}

	public void removeRestoring(ArmedTrap trap) {
		if (restoring.remove(trap)) {
			setDirty();
		}
	}

	// --- crosses ---

	/** The bottom of the post of the newest cross built for a marked death, if any stands. */
	public Optional<GlobalPos> lastCross() {
		return Optional.ofNullable(lastCross);
	}

	/** The marked death the newest cross stands for. */
	public Optional<GlobalPos> lastCrossDeath() {
		return Optional.ofNullable(lastCrossDeath);
	}

	/** The newest cross: the bottom of its post, and the marked death it stands for (both null: none). */
	public void setLastCross(@Nullable GlobalPos base, @Nullable GlobalPos death) {
		lastCross = base;
		lastCrossDeath = base == null ? null : death;
		setDirty();
	}

	/**
	 * The bottom of the post of the cross built for the marked death at exactly {@code death}: the newest one built for
	 * that spot (two friends who died on the same block get a cross each), else the newest cross if it is that death's.
	 */
	public Optional<GlobalPos> crossFor(GlobalPos death) {
		for (int i = built.size() - 1; i >= 0; i--) {
			BuiltCross cross = built.get(i);
			if (cross.marked() && cross.death().equals(death)) {
				return Optional.of(cross.base());
			}
		}
		return lastCrossDeath().filter(death::equals).flatMap(at -> lastCross());
	}

	/** Every cross he built that is remembered, oldest first. Unmodifiable. */
	public List<BuiltCross> built() {
		return Collections.unmodifiableList(built);
	}

	/** Remembers a cross that now stands (the oldest is forgotten past {@link #BUILT_MAX}). */
	public void addBuilt(BuiltCross cross) {
		built.add(cross);
		while (built.size() > BUILT_MAX) {
			built.removeFirst();
		}
		setDirty();
	}

	/** The cells of every remembered cross in this level, and the blocks their posts stand on: never taken or crowded. */
	public Set<BlockPos> crossCells(ResourceKey<Level> dimension) {
		Set<BlockPos> cells = new HashSet<>();
		for (BuiltCross cross : built) {
			if (cross.base().dimension().equals(dimension)) {
				cells.addAll(cross.cells());
				cells.add(cross.base().pos().below());
			}
		}
		return cells;
	}

	public List<PendingCross> crosses() {
		return Collections.unmodifiableList(crosses);
	}

	public void addCross(PendingCross cross) {
		crosses.add(cross);
		setDirty();
	}

	/** Replaces this very waiting cross (by identity, so two equal ones never stand in for each other). */
	public void replaceCross(PendingCross old, @Nullable PendingCross updated) {
		for (int index = 0; index < crosses.size(); index++) {
			if (crosses.get(index) == old) {
				if (updated == null) {
					crosses.remove(index);
				} else {
					crosses.set(index, updated);
				}
				setDirty();
				return;
			}
		}
	}

	// --- per player (D-061) ---

	/** This player's own records, made empty the first time. */
	public PlayerData personal(UUID player) {
		return players.computeIfAbsent(player, uuid -> new PlayerData(uuid, new RouteBook(), null, 0));
	}

	/** Every player with records here, in the order they first got some. Unmodifiable. */
	public Map<UUID, PlayerData> players() {
		return Collections.unmodifiableMap(players);
	}

	/** This player's learned routes. */
	public RouteBook routes(UUID player) {
		return personal(player).routes;
	}

	/** Remembers this player's cairn; a different cairn starts their visit count again. */
	public void setCairn(UUID player, @Nullable GlobalPos pos) {
		PlayerData mine = personal(player);
		if (pos == null ? mine.cairn != null : !pos.equals(mine.cairn)) {
			mine.cairn = pos;
			mine.cairnVisits = 0;
			mine.atCairn = false;
			setDirty();
		}
	}

	public void addCairnVisit(UUID player) {
		personal(player).cairnVisits++;
		setDirty();
	}

	/** What players are doing at the grove whose spot this is. */
	public GroveWatch grove(GlobalPos spot) {
		return groves.computeIfAbsent(spot, k -> new GroveWatch());
	}

	/** White eyes progress of this player's trap. */
	public RoomWatch room(UUID player) {
		return rooms.computeIfAbsent(player, k -> new RoomWatch());
	}

	/** Forgets the white eyes progress of this player's trap (it is over). */
	void forgetRoom(UUID player) {
		rooms.remove(player);
	}

	// --- history ---

	public List<String> history() {
		return Collections.unmodifiableList(history);
	}

	public void log(String line) {
		history.add(line);
		while (history.size() > HISTORY_MAX) {
			history.remove(0);
		}
		setDirty();
	}
}
