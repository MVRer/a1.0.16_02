package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.forzacode.a1016_02.world.sc2.s16.PyramidAnswers;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Doorway;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.House;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Level;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Way;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * How the depths answer (docs/SELF_CREATED_2_R3.md, "33", "Answers", D-122), through the kit's {@link Answers} (asleep,
 * the switch and the story; creative and spectators never inside; quiet, first hour; out of everyone's view; ledgered):
 * <ul>
 *   <li><b>33b, the door below.</b> A participant on the pit's floor or in the tunnel {@code a} seconds, nobody in the
 *   depths: the house's front door is heard to open and shut (nothing moves). Once a player a day.</li>
 *   <li><b>33c, other rooms.</b> In the rooms every {@code b}, in the warren every {@code c} seconds, a door 1 to 3 houses
 *   off plays its open and its shut (in the warren a share {@code d} of them really move, each next one a house nearer
 *   until one is in a wall of the player's own house); in the warren, steps abreast of a walking player in the house
 *   beside theirs (or between two players), {@code h} ticks late. One answer a visit, its events its parts.</li>
 *   <li><b>33d, the light goes down.</b> After {@code m} seconds in the warren (or anyone at the end): the rooms' torches
 *   taken house by house from the stair up, then the first house's two moved to the end house's bare places.</li>
 *   <li><b>33e, the iron door.</b> It opens once the end house is lit and the player has been {@code o} seconds in the
 *   warren, {@code p} from it; it shuts behind those past it after {@code q} seconds (a pickaxe, 10 health, nobody near
 *   on the other side); then the end house's torches go, and with it dark his glimpse down his tunnel; then silence.</li>
 * </ul>
 * Never within 30 s of anyone's combat; never a torch a player set; never a door opened with a monster within 16 or shut
 * with a player within 3. Session only; server thread.
 */
public final class DepthsAnswers {
	public static final String LURE = S16.CAUSE + "/33b";
	public static final String ROOMS = S16.CAUSE + "/33c";
	public static final String DARK = S16.CAUSE + "/33d";
	public static final String IRON = S16.CAUSE + "/33e";
	/** The kit's answer ids. */
	public static final String A_LURE = "33b";
	public static final String A_ROOMS = "33c";
	public static final String A_DARK = "33d";
	public static final String A_SHUT = "33e";
	public static final String A_OPEN = "33f";
	/** The per-place count key of a player's glimpse ({@code + uuid}). */
	public static final String GLIMPSED = "33g:";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 10;
	/** Nothing within this of anyone's combat (ticks). */
	public static final long COMBAT_TICKS = 600;
	/** A light is never taken within this of a player. */
	public static final double LIGHT_NEAR = 6.0;
	/** A door is never shut with a player within this. */
	public static final double SHUT_NEAR = 3.0;
	/** A door is never opened with a monster within this. */
	public static final double MONSTER_NEAR = 16.0;
	/** Walks this far apart at least (ticks). */
	public static final long WALK_GAP_TICKS = 90 * 20;
	/** A walk never within this of a door (ticks). */
	public static final long DOOR_QUIET_TICKS = 30 * 20;
	/** A walking player's step (vanilla: a step sound every 1/0.6 blocks walked). */
	public static final double STEP_EVERY = 1.0 / 0.6;
	/** The door's shut follows its open by this many ticks at least, {@link #DOOR_GAP_MAX} at most. */
	public static final int DOOR_GAP_MIN = 20;
	public static final int DOOR_GAP_MAX = 40;
	/** 33e: nobody on the near side within this of the iron door when it shuts. */
	public static final double NEAR_SIDE = 16.0;
	/** 33e: everyone past it this healthy. */
	public static final float SHUT_HEALTH = 10.0F;
	/** 33e's dark: one of the end house's torches every this many ticks at least, {@link #END_DARK_MAX} at most. */
	public static final int END_DARK_MIN = 80;
	public static final int END_DARK_MAX = 160;
	/** A walk's echo stops when the player has stood still this long (ticks). */
	public static final int STILL_TICKS = 10;

	/** Where a player is, for the answers. */
	public enum Zone {
		/** In the pyramid but not below. */
		OUT,
		/** On the pit's 3 x 3 floor or in its tunnel. */
		PIT,
		L1, L2, L3,
		/** Past the iron door: the end house or his tunnel. */
		L4
	}

	/** Plays a sound for everyone in range (live: {@link SoundCues#playAt}). */
	@FunctionalInterface
	public interface Sounds {
		void play(ServerLevel level, SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch);
	}

	/** True if a player set the block here (live: the placed-block watch). */
	@FunctionalInterface
	public interface Placed {
		boolean byPlayer(ServerLevel level, BlockPos pos);
	}

	/** True if a monster stands within {@code radius} of the cell. */
	@FunctionalInterface
	public interface Monsters {
		boolean near(ServerLevel level, BlockPos pos, double radius);
	}

	/**
	 * What the answers read and write.
	 *
	 * @param people      everyone online (spectators are skipped where it matters)
	 * @param view        out of everyone's view (live: {@code TraceService}), for what the kit's own check does not cover
	 * @param sinceCombat ticks since a player's last combat
	 */
	public record Env(Answers answers, Visits.People people, Supplier<Figure> figure, Supplier<Sc33Config> config,
			Function<ServerLevel, Function<Remains.Place, @Nullable PyramidShape>> shapes, Sounds sounds, Placed placed, Monsters monsters,
			ToLongFunction<ServerPlayer> sinceCombat, Answers.View view, Function<MinecraftServer, Sc2Data> data) {
	}

	/** One player's visit to one pyramid. */
	static final class Visit {
		final Remains.Place place;
		final UUID player;
		long lastLook = -1;
		long pitTicks;
		long l3Ticks;
		long pastTicks;
		boolean lureAsked;
		boolean roomsDone;
		boolean roomsCounted;
		int doors;
		int moved;
		long nextDoor = -1;
		int lastDist = 4;
		long lastDoorAt = Long.MIN_VALUE / 4;
		int walks;
		long lastWalkAt = Long.MIN_VALUE / 4;
		@Nullable Walk walk;
		boolean silence;

		Visit(Remains.Place place, UUID player) {
			this.place = place;
			this.player = player;
		}
	}

	/**
	 * The steps beside a walking player: abreast of them in the house beside theirs (their position mirrored across the
	 * wall between the rows) or in the house between two players (on its middle line).
	 */
	static final class Walk {
		final boolean acrossS;
		final double plane;
		final boolean mirror;
		final int max;
		int steps;
		double walked;
		@Nullable Vec3 last;
		int still;
		boolean ending;

		Walk(boolean acrossS, double plane, boolean mirror, int max) {
			this.acrossS = acrossS;
			this.plane = plane;
			this.mirror = mirror;
			this.max = max;
		}
	}

	/** A place's own progress this session. */
	static final class PlaceState {
		long nextDark = -1;
		boolean darkCounted;
		long shutAt = -1;
		long nextEndDark = -1;
	}

	/** A sound later. */
	private record Cue(long at, ServerLevel level, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
	}

	public static final DepthsAnswers LIVE = new DepthsAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()), Services::figure,
			Sc33Config::get, level -> place -> PyramidAnswers.shapeOf(level, place), (level, sound, source, at, volume, pitch) -> SoundCues.playAt(level, sound, source,
					at, volume, pitch), DepthsAnswers::livePlaced, DepthsAnswers::liveMonsters, player -> Services.watch().ticksSinceCombat(player),
			(level, cells) -> Services.traces().isOutOfView(level, cells), server -> RemainsImpl.LIVE.data()));

	private final Env env;
	private final Map<UUID, Visit> visits = new LinkedHashMap<>();
	private final Map<Remains.Place, PlaceState> places = new HashMap<>();
	private final Map<Remains.Place, RoomGraph> graphs = new HashMap<>();
	private final List<Cue> cues = new ArrayList<>();
	private long rng = 0x33L;
	/** The clock of the current tick or look (what a commit stamps). */
	private long clock;

	public DepthsAnswers(Env env) {
		this.env = env;
	}

	static void init() {
		HerobrineEvents.REMAINS_ENTERED.register((level, place, player) -> LIVE.entered(place, player));
		HerobrineEvents.REMAINS_LEFT.register((level, place, player) -> LIVE.left(place, player));
		ServerTickEvents.END_SERVER_TICK.register(LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
		SelfCreated.onSwitchedOff("sc33", server -> LIVE.clear());
		Sc33Commands.register();
	}

	// --- visits ---

	public void entered(Remains.Place place, ServerPlayer player) {
		if (place.kind() == Remains.Kind.PYRAMID) {
			visits.put(player.getUUID(), new Visit(place, player.getUUID()));
		}
	}

	public void left(Remains.Place place, ServerPlayer player) {
		Visit visit = visits.get(player.getUUID());
		if (visit != null && visit.place.equals(place)) {
			visits.remove(player.getUUID());
		}
	}

	/** Forgets every visit, the places' progress and the sounds to come (switch off, server stop). */
	public void clear() {
		visits.clear();
		places.clear();
		graphs.clear();
		cues.clear();
	}

	public boolean inside(UUID player) {
		return visits.containsKey(player);
	}

	/** {@code /a1016 sc 33 arm}: this player's timers run out now (every other rule kept). False if not inside a pyramid. */
	public boolean arm(ServerPlayer player, long now) {
		Visit visit = visits.get(player.getUUID());
		if (visit == null) {
			return false;
		}
		Sc33Config config = env.config().get();
		visit.pitTicks = Math.round(config.lureSeconds * 20);
		visit.l3Ticks = Math.round(Math.max(config.darkAfterSeconds, config.ironOpenAfterSeconds) * 20);
		visit.pastTicks = Math.round(config.ironShutAfterSeconds * 20);
		visit.nextDoor = now;
		visit.lastWalkAt = Long.MIN_VALUE / 4;
		visit.lastDoorAt = Long.MIN_VALUE / 4;
		PlaceState ps = places.computeIfAbsent(visit.place, k -> new PlaceState());
		ps.nextDark = now;
		ps.nextEndDark = now;
		return true;
	}

	/** This player's visit in plain words (for {@code /a1016 sc 33}). */
	public @Nullable String describe(UUID player) {
		Visit visit = visits.get(player);
		if (visit == null) {
			return null;
		}
		return String.format(java.util.Locale.ROOT, "timers %ds, %ds, %ds; 33c %d (%d moved), %d walks; quiet %s", visit.pitTicks / 20,
				visit.l3Ticks / 20, visit.pastTicks / 20, visit.doors, visit.moved, visit.walks, visit.silence ? "yes" : "no");
	}

	// --- the tick ---

	/** The live tick: the sounds due and the walks every tick, a look every {@value #EVERY_TICKS}. */
	public void tick(MinecraftServer server) {
		tickAt(server, server.getTickCount());
	}

	/** {@link #tick} at this clock (tests drive it). */
	public void tickAt(MinecraftServer server, long now) {
		clock = now;
		if (!cues.isEmpty()) {
			playDue(now);
		}
		if (visits.isEmpty() || !SelfCreated.enabled()) {
			return;
		}
		steps(server, now);
		if (now % EVERY_TICKS == 7) {
			look(server, now);
		}
	}

	private @Nullable ServerPlayer online(MinecraftServer server, UUID uuid) {
		for (ServerPlayer player : env.people().online(server)) {
			if (player.getUUID().equals(uuid)) {
				return player;
			}
		}
		return null;
	}

	/** Plays the sounds due by {@code now}. */
	public void playDue(long now) {
		Iterator<Cue> it = cues.iterator();
		while (it.hasNext()) {
			Cue cue = it.next();
			if (cue.at() <= now) {
				it.remove();
				env.sounds().play(cue.level(), cue.sound(), cue.source(), cue.pos(), cue.volume(), cue.pitch());
			}
		}
	}

	/** One look at every visit. */
	public void look(MinecraftServer server, long now) {
		clock = now;
		Map<UUID, ServerPlayer> online = new HashMap<>();
		for (ServerPlayer player : env.people().online(server)) {
			online.put(player.getUUID(), player);
		}
		for (Visit visit : List.copyOf(visits.values())) {
			ServerPlayer player = online.get(visit.player);
			if (player == null || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = server.getLevel(visit.place.dimension());
			if (level == null || player.level() != level) {
				continue;
			}
			PyramidShape shape = env.shapes().apply(level).apply(visit.place);
			if (shape == null || !shape.depths().present()) {
				continue;
			}
			Depths depths = shape.depths();
			RoomGraph graph = graphs.computeIfAbsent(visit.place, k -> new RoomGraph(depths));
			Zone zone = zone(shape, player.position());
			long dt = visit.lastLook < 0 ? EVERY_TICKS : Math.min(4L * EVERY_TICKS, now - visit.lastLook);
			visit.lastLook = now;
			visit.pitTicks = zone == Zone.PIT ? visit.pitTicks + dt : 0;
			visit.l3Ticks += zone == Zone.L3 ? dt : 0;
			visit.pastTicks = zone == Zone.L4 ? visit.pastTicks + dt : 0;
			List<ServerPlayer> here = people(level);
			// Never within 30 s of anyone's combat (the class's rule; R3-V: 33d and 33e wait too, they took the rooms' light
			// and shut the iron door behind a player in a fight).
			if (calm(here)) {
				lure(level, visit, shape, here);
				rooms(level, visit, graph, player, zone, here, now);
				dark(level, visit, graph, zone, here, now);
				iron(level, visit, shape, graph, player, zone, here, now);
			}
		}
	}

	/** Where these feet are: below (by level), on the pit's floor or in its tunnel, or elsewhere in the pyramid. */
	public static Zone zone(PyramidShape shape, Vec3 feet) {
		BlockPos at = BlockPos.containing(feet);
		Depths depths = shape.depths();
		Level level = depths.levelOf(at.getX(), at.getY(), at.getZ());
		if (level != null) {
			return switch (level) {
				case L1 -> Zone.L1;
				case L2 -> Zone.L2;
				case L3 -> Zone.L3;
				case L4 -> Zone.L4;
			};
		}
		if (shape.tunnel(at.getX(), at.getY(), at.getZ()) || at.getY() == shape.pitBottomY() && shape.ring(at.getX(), at.getZ()) <= 1) {
			return Zone.PIT;
		}
		return Zone.OUT;
	}

	/** The players in this level who count (not spectators). */
	private List<ServerPlayer> people(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : env.people().online(level.getServer())) {
			if (!player.isSpectator() && player.level() == level) {
				out.add(player);
			}
		}
		return out;
	}

	/** True if nobody has fought within the last 30 s. */
	private boolean calm(Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (env.sinceCombat().applyAsLong(player) < COMBAT_TICKS) {
				return false;
			}
		}
		return true;
	}

	private static boolean anyoneBelow(PyramidShape shape, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			BlockPos at = player.blockPosition();
			if (shape.depths().levelOf(at.getX(), at.getY(), at.getZ()) != null) {
				return true;
			}
		}
		return false;
	}

	// --- 33b ---

	/** 33b's rules: once a player a day. */
	public static Answers.Rules lureRules() {
		return Answers.Rules.of(A_LURE, LURE).perPlayerDay(1).perPlaceDay(Integer.MAX_VALUE).limitSeconds(30);
	}

	private void lure(ServerLevel level, Visit visit, PyramidShape shape, List<ServerPlayer> here) {
		Sc33Config config = env.config().get();
		if (visit.silence || visit.lureAsked || visit.pitTicks < Math.round(config.lureSeconds * 20) || anyoneBelow(shape, here)) {
			return;
		}
		Depths depths = shape.depths();
		Doorway front = depths.plan().doorway(Way.FRONT);
		visit.lureAsked = true;
		env.answers().submit(level, visit.place, lureRules(), (lvl, place, inside) -> {
			if (anyoneBelow(shape, people(lvl)) || !(lvl.getBlockState(depths.cells(front).get(0)).getBlock() instanceof DoorBlock)) {
				return null;
			}
			return Answers.Edit.of(depths.cells(front), traces -> {
				doorSounds(lvl, depths, front, (float) config.doorVolume, clock);
				return true;
			});
		});
	}

	/** A door's own open and shut, {@value #DOOR_GAP_MIN} to {@value #DOOR_GAP_MAX} ticks apart, ending as it stands. */
	void doorSounds(ServerLevel level, Depths depths, Doorway d, float volume, long now) {
		BlockState state = level.getBlockState(depths.cells(d).get(0));
		if (!(state.getBlock() instanceof DoorBlock door)) {
			return;
		}
		BlockSetType type = door.type();
		boolean open = state.getValue(DoorBlock.OPEN);
		Vec3 at = doorMiddle(depths, d);
		SoundEvent first = open ? type.doorClose() : type.doorOpen();
		SoundEvent second = open ? type.doorOpen() : type.doorClose();
		env.sounds().play(level, first, SoundSource.BLOCKS, at, volume, pitch());
		cues.add(new Cue(now + between(DOOR_GAP_MIN, DOOR_GAP_MAX), level, second, SoundSource.BLOCKS, at, volume, pitch()));
	}

	private static Vec3 doorMiddle(Depths depths, Doorway d) {
		return Vec3.atCenterOf(depths.cells(d).get(0)).add(0, 0.5, 0);
	}

	private float pitch() {
		return 0.9F + 0.1F * (float) unit();
	}

	// --- 33c ---

	/** 33c's rules: its first event counted (one a player a day), the rest of the visit its parts. */
	public static Answers.Rules roomsRules(boolean first) {
		Answers.Rules rules = Answers.Rules.of(A_ROOMS, ROOMS).perPlayerDay(1).perPlaceDay(Integer.MAX_VALUE).limitSeconds(20);
		return first ? rules : rules.part();
	}

	private void rooms(ServerLevel level, Visit visit, RoomGraph graph, ServerPlayer player, Zone zone, List<ServerPlayer> here, long now) {
		if (visit.silence || visit.roomsDone || zone != Zone.L2 && zone != Zone.L3) {
			if (zone != Zone.L2 && zone != Zone.L3) {
				visit.nextDoor = -1;
			}
			return;
		}
		Sc33Config config = env.config().get();
		double[] every = zone == Zone.L2 ? Sc33Config.range(config.roomsDoorEvery, 60, 120) : Sc33Config.range(config.warrenDoorEvery, 25, 50);
		if (visit.nextDoor < 0) {
			visit.nextDoor = now + Math.round(lerp(every) * 20);
		}
		if (visit.walk == null && now >= visit.nextDoor && visit.doors < config.doorsPerVisit) {
			visit.nextDoor = now + Math.round(lerp(every) * 20);
			door(level, visit, graph, player, zone, config, now);
		}
		if (zone == Zone.L3 && visit.walk == null && visit.walks < config.walksPerVisit && now - visit.lastWalkAt >= WALK_GAP_TICKS
				&& now - visit.lastDoorAt >= DOOR_QUIET_TICKS) {
			startWalk(level, visit, graph, player, here, config, now);
		}
	}

	/** 33c's door: a door 1 to 3 houses off (the warren: nearer each time), out of view; its sounds, sometimes a real move. */
	private void door(ServerLevel level, Visit visit, RoomGraph graph, ServerPlayer player, Zone zone, Sc33Config config, long now) {
		Depths depths = graph.depths();
		boolean first = !visit.roomsCounted;
		Answers.Result result = env.answers().submit(level, visit.place, roomsRules(first), (lvl, place, inside) -> {
			Pick pick = pickDoor(lvl, visit, graph, player, zone, config);
			if (pick == null) {
				return null;
			}
			return Answers.Edit.of(depths.cells(pick.door()), traces -> {
				long t = clock;
				if (pick.move()) {
					BlockState lower = lvl.getBlockState(depths.cells(pick.door()).get(0));
					boolean open = lower.getValue(DoorBlock.OPEN);
					boolean done = traces.batch(lvl, ROOMS).convert(depths.cells(pick.door()).get(0), lower.setValue(DoorBlock.OPEN, !open))
							.convert(depths.cells(pick.door()).get(1), lvl.getBlockState(depths.cells(pick.door()).get(1)).setValue(DoorBlock.OPEN, !open)).commit();
					if (!done) {
						return false;
					}
					BlockSetType type = ((DoorBlock) lower.getBlock()).type();
					env.sounds().play(lvl, open ? type.doorClose() : type.doorOpen(), SoundSource.BLOCKS, doorMiddle(depths, pick.door()),
							(float) config.doorVolume, pitch());
					visit.moved++;
				} else {
					doorSounds(lvl, depths, pick.door(), (float) config.doorVolume, t);
				}
				visit.doors++;
				visit.lastDoorAt = t;
				if (zone == Zone.L3) {
					visit.lastDist = pick.distance() == 0 ? 4 : pick.distance();
				}
				return true;
			});
		});
		if (result.status() == Answers.Status.DONE && first) {
			visit.roomsCounted = true;
		}
		if (first && result.status() == Answers.Status.REFUSED && result.reason() != null && result.reason().endsWith("had it today")) {
			visit.roomsDone = true;
		}
	}

	/** A door chosen for 33c, whether it really moves, and how many houses it is from the player's. */
	record Pick(Doorway door, boolean move, int distance) {
	}

	/** 33c's door now, or null. */
	@Nullable Pick pickDoor(ServerLevel level, Visit visit, RoomGraph graph, ServerPlayer player, Zone zone, Sc33Config config) {
		Depths depths = graph.depths();
		int house = graph.houseAt(player.position());
		if (house < 0) {
			return null;
		}
		Level onLevel = depths.plan().house(house).level();
		int[] dist = graph.distances(house);
		List<ServerPlayer> here = people(level);
		Set<Integer> seen = graph.seen(level, here);
		boolean warren = zone == Zone.L3;
		List<Pick> picks = new ArrayList<>();
		for (Doorway d : depths.plan().doorways()) {
			if (d.way() != Way.DOOR && d.way() != Way.STAIR_TOP || d.a() < 0 || depths.plan().house(d.a()).level() != onLevel) {
				continue;
			}
			if (!graph.hasDoor(level, d)) {
				continue;
			}
			int k = RoomGraph.distance(d, dist);
			boolean ownWall = k == 0 && warren && visit.lastDist == 1;
			if (k < 0 || k > 3 || k == 0 && !ownWall || warren && k >= visit.lastDist) {
				continue;
			}
			if (!ownWall && (seen.contains(d.a()) || d.b() >= 0 && seen.contains(d.b()))) {
				continue;
			}
			boolean move = warren && visit.moved < config.doorMovesPerVisit && unit() < config.doorMovedShare && movable(level, depths, d, here);
			picks.add(new Pick(d, move, k));
		}
		if (picks.isEmpty()) {
			return null;
		}
		if (warren) {
			// Nearer each time: the farthest allowed under the last.
			int best = picks.stream().mapToInt(Pick::distance).max().orElse(0);
			picks.removeIf(p -> p.distance() != best);
		}
		return picks.get((int) Math.floor(unit() * picks.size()));
	}

	/** A door may really move: opened with no monster within 16 of it, shut with no player within 3. */
	private boolean movable(ServerLevel level, Depths depths, Doorway d, Collection<ServerPlayer> players) {
		BlockPos lower = depths.cells(d).get(0);
		BlockState state = level.getBlockState(lower);
		if (!(state.getBlock() instanceof DoorBlock)) {
			return false;
		}
		if (!state.getValue(DoorBlock.OPEN)) {
			return !env.monsters().near(level, lower, MONSTER_NEAR);
		}
		Vec3 middle = doorMiddle(depths, d);
		for (ServerPlayer player : players) {
			if (player.position().distanceTo(middle) < SHUT_NEAR + 0.5) {
				return false;
			}
		}
		return true;
	}

	/** 33c's steps: a walk starts beside a player walking along a row of the warren, in an empty house out of view. */
	private void startWalk(ServerLevel level, Visit visit, RoomGraph graph, ServerPlayer player, List<ServerPlayer> here, Sc33Config config, long now) {
		Vec3 move = player.getDeltaMovement();
		Depths depths = graph.depths();
		DepthsPlan plan = depths.plan();
		int house = graph.houseAt(player.position());
		if (house < 0 || plan.house(house).level() != Level.L3) {
			return;
		}
		double ds = move.x * plan.right().getStepX() + move.z * plan.right().getStepZ();
		double df = move.x * plan.facing().getStepX() + move.z * plan.facing().getStepZ();
		if (Math.max(Math.abs(ds), Math.abs(df)) < 0.05) {
			return;
		}
		boolean alongF = Math.abs(df) >= Math.abs(ds);
		House h = plan.house(house);
		Set<Integer> seen = graph.seen(level, here);
		Walk walk = null;
		int echoHouse = -1;
		// Between two players a house apart in a row: the empty house between them.
		for (ServerPlayer other : here) {
			int theirs = other == player ? -1 : graph.houseAt(other.position());
			int middle = theirs < 0 ? -1 : graph.between(house, theirs);
			if (middle >= 0 && empty(level, graph, middle, here) && !seen.contains(middle)) {
				House mh = plan.house(middle);
				walk = new Walk(alongF, alongF ? mh.s0() + 0.5 : mh.f0() + 0.5, false, 0);
				echoHouse = middle;
				break;
			}
		}
		if (walk == null) {
			int sign = unit() < 0.5 ? 1 : -1;
			for (int tries = 0; tries < 2 && walk == null; tries++, sign = -sign) {
				int[] dir = alongF ? new int[] {sign, 0} : new int[] {0, sign};
				int beside = graph.beside(house, dir);
				if (beside >= 0 && empty(level, graph, beside, here) && !seen.contains(beside)) {
					double wall = alongF ? h.s0() + 3 * sign + 0.5 : h.f0() + 3 * sign + 0.5;
					walk = new Walk(alongF, wall, true, 0);
					echoHouse = beside;
				}
			}
		}
		if (walk == null) {
			return;
		}
		int[] steps = Sc33Config.range(config.stepsPerWalk, 6, 12);
		Walk started = new Walk(walk.acrossS, walk.plane, walk.mirror, steps[0] + (int) Math.floor(unit() * (steps[1] - steps[0] + 1)));
		House eh = plan.house(echoHouse);
		List<BlockPos> cells = List.of(plan.pos(eh.s0(), eh.feet(), eh.f0()), plan.pos(eh.s0() - 2, eh.feet(), eh.f0() - 2), plan.pos(eh.s0() + 2, eh.feet(),
				eh.f0() + 2));
		boolean first = !visit.roomsCounted;
		Answers.Result result = env.answers().submit(level, visit.place, roomsRules(first).limitSeconds(1), (lvl, place, inside) -> Answers.Edit.of(cells,
				traces -> {
					started.last = player.position();
					visit.walk = started;
					return true;
				}));
		if (result.status() == Answers.Status.DONE && first) {
			visit.roomsCounted = true;
		}
	}

	private boolean empty(ServerLevel level, RoomGraph graph, int house, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (graph.houseAt(player.position()) == house) {
				return false;
			}
		}
		return true;
	}

	/** Every tick: the walks' steps, each answered {@code h} ticks late abreast of the walker. */
	private void steps(MinecraftServer server, long now) {
		Sc33Config config = null;
		for (Visit visit : visits.values()) {
			Walk walk = visit.walk;
			if (walk == null) {
				continue;
			}
			ServerPlayer player = online(server, visit.player);
			ServerLevel level = server.getLevel(visit.place.dimension());
			if (config == null) {
				config = env.config().get();
			}
			if (player == null || level == null || player.level() != level || visit.silence || env.answers().held(server) != null
					|| env.sinceCombat().applyAsLong(player) < COMBAT_TICKS) {
				endWalk(visit, now);
				continue;
			}
			PyramidShape shape = env.shapes().apply(level).apply(visit.place);
			RoomGraph graph = graphs.get(visit.place);
			if (shape == null || graph == null || zone(shape, player.position()) != Zone.L3) {
				endWalk(visit, now);
				continue;
			}
			Vec3 at = player.position();
			double moved = walk.last == null ? 0 : Math.hypot(at.x - walk.last.x, at.z - walk.last.z);
			walk.last = at;
			walk.walked += moved;
			walk.still = moved < 0.02 ? walk.still + 1 : 0;
			if (walk.still >= STILL_TICKS && walk.steps > 0) {
				// They stopped: one more, then nothing.
				echo(level, visit, graph, at, config, now);
				endWalk(visit, now);
				continue;
			}
			if (walk.walked >= STEP_EVERY) {
				walk.walked -= STEP_EVERY;
				if (!echo(level, visit, graph, at, config, now)) {
					endWalk(visit, now);
					continue;
				}
				walk.steps++;
				if (walk.steps >= walk.max) {
					endWalk(visit, now);
				}
			}
		}
	}

	/** One answering step abreast of {@code at}, {@code h} ticks from now; false if its place is no longer an empty house out of view. */
	boolean echo(ServerLevel level, Visit visit, RoomGraph graph, Vec3 at, Sc33Config config, long now) {
		Walk walk = visit.walk;
		Depths depths = graph.depths();
		DepthsPlan plan = depths.plan();
		double s = plan.s((int) Math.floor(at.x), (int) Math.floor(at.z)) + frac(at, plan.right());
		double f = plan.f((int) Math.floor(at.x), (int) Math.floor(at.z)) + frac(at, plan.facing());
		if (walk.acrossS) {
			s = walk.mirror ? 2 * walk.plane - s : walk.plane;
		} else {
			f = walk.mirror ? 2 * walk.plane - f : walk.plane;
		}
		BlockPos cell = plan.pos((int) Math.floor(s), (int) Math.floor(at.y + 0.01), (int) Math.floor(f));
		House h = depths.houseOf(cell.getX(), cell.getY(), cell.getZ());
		List<ServerPlayer> here = people(level);
		if (h == null || h.level() != Level.L3 || !empty(level, graph, h.index(), here) || graph.seen(level, here).contains(h.index())
				|| !env.view().outOfView(level, List.of(cell))) {
			return false;
		}
		BlockPos floor = cell.below();
		SoundType type = SoundCues.stepType(level, floor);
		Vec3 pos = Vec3.atBottomCenterOf(cell);
		cues.add(new Cue(now + Math.max(0, config.stepLateTicks), level, type.getStepSound(), SoundSource.PLAYERS, pos, (float) config.stepVolume,
				type.getPitch()));
		return true;
	}

	/** The fraction of a position past its block along a horizontal axis direction (0 to 1, signed by the direction). */
	private static double frac(Vec3 at, net.minecraft.core.Direction dir) {
		double fx = at.x - Math.floor(at.x);
		double fz = at.z - Math.floor(at.z);
		return dir.getStepX() > 0 ? fx : dir.getStepX() < 0 ? 1 - fx : dir.getStepZ() > 0 ? fz : 1 - fz;
	}

	private void endWalk(Visit visit, long now) {
		if (visit.walk != null) {
			visit.walk = null;
			visit.walks++;
			visit.lastWalkAt = now;
		}
	}

	// --- 33d ---

	/** 33d's rules: its first light counted, the rest its parts. */
	public static Answers.Rules darkRules(boolean first) {
		Answers.Rules rules = Answers.Rules.of(A_DARK, DARK).perPlayerDay(Integer.MAX_VALUE).perPlaceDay(Integer.MAX_VALUE).limitSeconds(8);
		return first ? rules : rules.part();
	}

	private void dark(ServerLevel level, Visit visit, RoomGraph graph, Zone zone, List<ServerPlayer> here, long now) {
		Sc33Config config = env.config().get();
		if (visit.l3Ticks < Math.round(config.darkAfterSeconds * 20) && zone != Zone.L4) {
			return;
		}
		PlaceState ps = places.computeIfAbsent(visit.place, k -> new PlaceState());
		if (now < ps.nextDark) {
			return;
		}
		ps.nextDark = now + Math.round(lerp(Sc33Config.range(config.darkEverySeconds, 3, 6)) * 20);
		Depths depths = graph.depths();
		DepthsPlan plan = depths.plan();
		Set<Integer> seen = graph.seen(level, here);
		boolean anyLeft = false;
		BlockPos take = null;
		for (House h : roomsFromTheStair(plan)) {
			for (BlockPos t : depths.torches(h)) {
				if (!ours(level, depths, h, t)) {
					continue;
				}
				anyLeft = true;
				if (take == null && !seen.contains(h.index()) && far(t, here, LIGHT_NEAR) && env.view().outOfView(level, List.of(t))) {
					take = t;
				}
			}
		}
		boolean first = !ps.darkCounted;
		if (take != null) {
			BlockPos cell = take;
			Answers.Result r = env.answers().submit(level, visit.place, darkRules(first), (lvl, place, inside) -> ours(lvl, depths, houseOfTorch(depths, cell), cell)
					&& far(cell, people(lvl), LIGHT_NEAR) ? Answers.Edit.of(List.of(cell), traces -> traces.batch(lvl, DARK).remove(cell).commit()) : null);
			if (r.status() == Answers.Status.DONE) {
				ps.darkCounted = true;
			}
			return;
		}
		if (anyLeft) {
			return;
		}
		// Then the first house's two, moved to the end house's bare places (the same wall facing).
		House first1 = plan.l1();
		House end = plan.l4();
		List<BlockPos> from = depths.torches(first1);
		List<BlockPos> to = depths.torches(end);
		for (int k = 0; k < 2; k++) {
			if (!ours(level, depths, first1, from.get(k)) || !level.getBlockState(to.get(k)).isAir()) {
				return;
			}
		}
		if (seen.contains(first1.index()) || seen.contains(end.index()) || !far(from.get(0), here, LIGHT_NEAR) || !far(from.get(1), here, LIGHT_NEAR)) {
			return;
		}
		List<BlockPos> cells = List.of(from.get(0), from.get(1), to.get(0), to.get(1));
		Answers.Result r = env.answers().submit(level, visit.place, darkRules(first), (lvl, place, inside) -> Answers.Edit.of(cells,
				traces -> traces.batch(lvl, DARK).move(from.get(0), to.get(0)).move(from.get(1), to.get(1)).commit()));
		if (r.status() == Answers.Status.DONE) {
			ps.darkCounted = true;
			A1016_02.LOGGER.debug("[a1016] s2: 33d at {}", visit.place.entrance().toShortString());
		}
	}

	/** The rooms' houses, from the one the stair comes into outward (house by house from the stair up). */
	public static List<House> roomsFromTheStair(DepthsPlan plan) {
		List<House> rooms = new ArrayList<>(plan.houses(Level.L2));
		if (rooms.isEmpty()) {
			return rooms;
		}
		Doorway foot = null;
		for (Doorway d : plan.doorways()) {
			if (d.way() == Way.STAIR_FOOT && d.a() >= 0 && plan.house(d.a()).level() == Level.L2) {
				foot = d;
			}
		}
		House stair = foot == null ? rooms.get(0) : plan.house(foot.a());
		rooms.sort(Comparator.comparingInt((House h) -> Math.abs(h.i() - stair.i()) + Math.abs(h.j() - stair.j())).thenComparingInt(House::index));
		return rooms;
	}

	private static @Nullable House houseOfTorch(Depths depths, BlockPos cell) {
		for (House h : depths.plan().houses()) {
			if (depths.torches(h).contains(cell)) {
				return h;
			}
		}
		return null;
	}

	/** True if the depths' own torch stands at this place (the planned one, not a player's). */
	boolean ours(ServerLevel level, Depths depths, @Nullable House h, BlockPos place) {
		if (h == null) {
			return false;
		}
		BlockState state = level.getBlockState(place);
		return state.getBlock() instanceof WallTorchBlock && state.equals(depths.torchState(h, place)) && !env.placed().byPlayer(level, place);
	}

	private static boolean far(BlockPos cell, Collection<ServerPlayer> players, double radius) {
		Vec3 c = Vec3.atCenterOf(cell);
		for (ServerPlayer player : players) {
			if (player.position().add(0, 1, 0).distanceTo(c) < radius) {
				return false;
			}
		}
		return true;
	}

	// --- 33e ---

	/** 33e's opening: once a place a day. */
	public static Answers.Rules openRules() {
		return Answers.Rules.of(A_OPEN, IRON).perPlayerDay(Integer.MAX_VALUE).perPlaceDay(1).limitSeconds(30);
	}

	/** 33e's shutting: {@code s} a place ever, once a player a day, everyone inside with a pickaxe and 10 health (16b's rules). */
	public static Answers.Rules shutRules(Sc33Config config) {
		return Answers.Rules.of(A_SHUT, IRON).perPlaceEver(config.ironShutPerPlaceMax).perPlaceDay(Integer.MAX_VALUE).health(SHUT_HEALTH).tool(Answers::pickaxe)
				.limitSeconds(30);
	}

	/** 33e's dark and its other parts. */
	public static Answers.Rules ironPart() {
		return Answers.Rules.of(A_SHUT, IRON).limitSeconds(8).part();
	}

	private void iron(ServerLevel level, Visit visit, PyramidShape shape, RoomGraph graph, ServerPlayer player, Zone zone, List<ServerPlayer> here, long now) {
		Depths depths = graph.depths();
		DepthsPlan plan = depths.plan();
		Doorway iron = plan.doorway(Way.IRON);
		List<BlockPos> cells = depths.cells(iron);
		BlockState lower = level.getBlockState(cells.get(0));
		PlaceState ps = places.computeIfAbsent(visit.place, k -> new PlaceState());
		Sc33Config config = env.config().get();
		Vec3 door = doorMiddle(depths, iron);
		House end = plan.l4();
		List<BlockPos> places4 = depths.torches(end);
		boolean lit = ours(level, depths, end, places4.get(0)) || ours(level, depths, end, places4.get(1));
		if (lower.getBlock() instanceof DoorBlock && lower.is(net.minecraft.world.level.block.Blocks.IRON_DOOR)) {
			boolean open = lower.getValue(DoorBlock.OPEN);
			if (!open && lit && zone == Zone.L3 && visit.l3Ticks >= Math.round(config.ironOpenAfterSeconds * 20) && everyoneFrom(here, door, config.ironOpenMinDistance)) {
				List<BlockPos> watched = new ArrayList<>(cells);
				watched.addAll(places4);
				watched.add(plan.pos(end.s0(), end.feet(), end.f0()));
				env.answers().submit(level, visit.place, openRules(), (lvl, place, inside) -> everyoneFrom(people(lvl), door, config.ironOpenMinDistance)
						? Answers.Edit.of(watched, traces -> {
							boolean done = convertDoor(lvl, traces, cells, true);
							if (done) {
								env.sounds().play(lvl, BlockSetType.IRON.doorOpen(), SoundSource.BLOCKS, door, (float) config.ironVolume, pitch());
							}
							return done;
						}) : null);
			}
			if (open && zone == Zone.L4 && visit.pastTicks >= Math.round(config.ironShutAfterSeconds * 20)) {
				Answers.Result r = env.answers().submit(level, visit.place, shutRules(config), (lvl, place, inside) -> shutting(lvl, shape, cells, door, config)
						? Answers.Edit.of(cells, traces -> {
							boolean done = convertDoor(lvl, traces, cells, false);
							if (done) {
								env.sounds().play(lvl, BlockSetType.IRON.doorClose(), SoundSource.BLOCKS, door, (float) config.ironVolume, pitch());
								long t = clock;
								ps.shutAt = t;
								ps.nextEndDark = t + between(END_DARK_MIN, END_DARK_MAX);
								for (Visit v : visits.values()) {
									if (v.place.equals(visit.place)) {
										v.silence = true;
										v.walk = null;
									}
								}
							}
							return done;
						}) : null);
				if (r.status() == Answers.Status.DONE) {
					A1016_02.LOGGER.debug("[a1016] s2: 33e at {}", visit.place.entrance().toShortString());
				}
			}
		}
		// The dark: the end house's torches one by one after the shutting, never within 6 of anyone.
		if (ps.shutAt >= 0 && now >= ps.nextEndDark) {
			ps.nextEndDark = now + between(END_DARK_MIN, END_DARK_MAX);
			for (BlockPos t : places4) {
				if (ours(level, depths, end, t) && far(t, here, LIGHT_NEAR)) {
					env.answers().submit(level, visit.place, ironPart(), (lvl, place, inside) -> ours(lvl, depths, end, t) && far(t, people(lvl), LIGHT_NEAR)
							? Answers.Edit.of(List.of(t), traces -> traces.batch(lvl, IRON).remove(t).commit()) : null);
					break;
				}
			}
		}
		// His glimpse: the end dark, the player in his tunnel.
		if (ps.shutAt >= 0 && !lit && zone == Zone.L4 && inHisTunnel(plan, player.position())) {
			glimpse(level, visit, plan, player, config);
		}
	}

	/** 33e's shutting now: everyone past it far enough from it, nobody on the near side within 16 of it. */
	boolean shutting(ServerLevel level, PyramidShape shape, List<BlockPos> cells, Vec3 door, Sc33Config config) {
		for (ServerPlayer player : people(level)) {
			boolean past = zone(shape, player.position()) == Zone.L4;
			double d = player.position().distanceTo(door);
			if (past && d < config.ironShutMinDistance || !past && d < NEAR_SIDE) {
				return false;
			}
		}
		BlockState lower = level.getBlockState(cells.get(0));
		return lower.getBlock() instanceof DoorBlock && lower.getValue(DoorBlock.OPEN);
	}

	private static boolean everyoneFrom(Collection<ServerPlayer> players, Vec3 at, double distance) {
		for (ServerPlayer player : players) {
			if (player.position().distanceTo(at) < distance) {
				return false;
			}
		}
		return true;
	}

	private static boolean convertDoor(ServerLevel level, com.forzacode.a1016_02.core.TraceService traces, List<BlockPos> cells, boolean open) {
		BlockState lower = level.getBlockState(cells.get(0));
		BlockState upper = level.getBlockState(cells.get(1));
		if (!(lower.getBlock() instanceof DoorBlock) || !(upper.getBlock() instanceof DoorBlock)) {
			return false;
		}
		return traces.batch(level, IRON).convert(cells.get(0), lower.setValue(DoorBlock.OPEN, open)).convert(cells.get(1), upper.setValue(DoorBlock.OPEN, open))
				.commit();
	}

	static boolean inHisTunnel(DepthsPlan plan, Vec3 feet) {
		BlockPos at = BlockPos.containing(feet);
		int s = plan.s(at.getX(), at.getZ());
		int f = plan.f(at.getX(), at.getZ());
		int[] span = plan.hisTunnelSpan();
		int[] sides = plan.hisTunnelSides();
		return f >= span[0] && f <= span[1] && (s == sides[0] || s == sides[1]) && at.getY() == plan.pitFeet() + Level.L4.down;
	}

	/** His glimpse down the tunnel {@code t} from the player: toward the house first (between them and the way out), else the blind end. */
	private void glimpse(ServerLevel level, Visit visit, DepthsPlan plan, ServerPlayer player, Sc33Config config) {
		Sc2Data data = env.data().apply(level.getServer());
		String key = GLIMPSED + player.getUUID();
		if (data.spent(visit.place, key).ever() > 0) {
			return;
		}
		Figure figure = env.figure().get();
		BlockPos at = player.blockPosition();
		int f = plan.f(at.getX(), at.getZ());
		int[] span = plan.hisTunnelSpan();
		int[] sides = plan.hisTunnelSides();
		int[] t = Sc33Config.range(config.glimpseDistance, 14, 24);
		int feet = plan.pitFeet() + Level.L4.down;
		for (int dir : new int[] {-1, 1}) {
			for (int d = t[0]; d <= t[1]; d++) {
				int ff = f + dir * d;
				if (ff < span[0] || ff > span[1]) {
					continue;
				}
				for (int side : sides) {
					BlockPos spot = plan.pos(side, feet, ff);
					Vec3 feetAt = Vec3.atBottomCenterOf(spot);
					if (!figure.canGlimpse(player, feetAt)) {
						continue;
					}
					Vec3 eye = player.position();
					float yaw = (float) Math.toDegrees(Math.atan2(-(eye.x - feetAt.x), eye.z - feetAt.z));
					if (figure.glimpse(player, feetAt, yaw) == FireResult.FIRED) {
						data.spend(visit.place, key, List.of(), GameClock.day(level.getServer()));
					}
					return;
				}
			}
		}
	}

	// --- live ---

	private static boolean livePlaced(ServerLevel level, BlockPos pos) {
		return Services.watch().placedNear(level, pos, 0, state -> true).contains(pos);
	}

	private static boolean liveMonsters(ServerLevel level, BlockPos pos, double radius) {
		return !level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(radius)).isEmpty();
	}

	// --- chance ---

	private double unit() {
		rng = Hash.mix(rng + 0x9E3779B97F4A7C15L);
		return Hash.unit(rng);
	}

	private long between(int lo, int hi) {
		return lo + (long) Math.floor(unit() * (hi - lo + 1));
	}

	private double lerp(double[] pair) {
		return pair[0] + (pair[1] - pair[0]) * unit();
	}
}
