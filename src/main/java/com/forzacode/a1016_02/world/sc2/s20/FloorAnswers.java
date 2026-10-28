package com.forzacode.a1016_02.world.sc2.s20;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 20b (docs/SELF_CREATED_2.md, 20; D-106): when a player inside a square comes within {@code d} of the little building in
 * its middle, every animal standing on the floor stops, falls silent and faces the building for {@code e} seconds, then
 * all are let go together (their holds run out in one tick). Through the kit's {@link Answers} (no block is touched, so
 * nothing waits on the view rule): never in anyone's quiet or first hour, one a player a day (everyone inside counts it),
 * the event and the small tension raise for everyone inside. Animals only (never a tamed, leashed, ridden or already held
 * one), only those on the floor; each held one is bound to the player it turned for (released once they are gone and
 * nobody sees it). A look once a second while someone is on a square, nothing otherwise. Session only; server thread.
 */
public final class FloorAnswers {
	/** The answer's id (its caps and the event). */
	public static final String FACE = "20b";
	/** Its cause (it ledgers nothing: it moves no block). */
	public static final String CAUSE = S20.CAUSE + "/face";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** An animal counts as on the floor up to this high over it. */
	public static final int ON_FLOOR = 3;
	/** A refused or dropped ask waits this long before it is asked again for that place (ticks). */
	public static final int ASK_AGAIN_TICKS = 200;

	/**
	 * What 20b reads: the kit's answers, who is online, the config, the mob tamper.
	 *
	 * @param people everyone online (spectators are skipped)
	 */
	public record Env(Answers answers, Visits.People people, Supplier<Sc20Config> config, Supplier<MobTamper> tamper, Supplier<Figure> figure,
			java.util.function.ToLongFunction<MinecraftServer> day, java.util.function.Predicate<ServerLevel> night, Answers.View view) {
		/** Without the figure (round 2's tests: 20b v1 only). */
		public Env(Answers answers, Visits.People people, Supplier<Sc20Config> config, Supplier<MobTamper> tamper) {
			this(answers, people, config, tamper, Services::figure, com.forzacode.a1016_02.core.GameClock::day, ServerLevel::isDarkOutside);
		}

		/** Without a view of its own: a ring's chunks go together, through the kit's view rule alone (tests). */
		public Env(Answers answers, Visits.People people, Supplier<Sc20Config> config, Supplier<MobTamper> tamper, Supplier<Figure> figure,
				java.util.function.ToLongFunction<MinecraftServer> day, java.util.function.Predicate<ServerLevel> night) {
			this(answers, people, config, tamper, figure, day, night, (level, cells) -> true);
		}
	}

	public static final FloorAnswers LIVE = new FloorAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			Sc20Config::get, Services::mobs, Services::figure, com.forzacode.a1016_02.core.GameClock::day, ServerLevel::isDarkOutside,
			(level, cells) -> Services.traces().isOutOfView(level, cells)));

	// --- 20b v2 (R3-3, R10-16, D-116) ---

	/** 20b v2's ledger cause: the torches and the floor's lights. */
	public static final String DARK = S20.CAUSE + "/dark";
	/** 20b v2: everyone on the floor this healthy. */
	public static final float DARK_HEALTH = 10.0F;
	/** 20b v2: the glimpse stands this far from the player at least and at most (D-105). */
	public static final int GLIMPSE_MIN = 20;
	public static final int GLIMPSE_MAX = 40;

	/**
	 * One floor going dark: its rings of chunks from the edge in (FIX-R3-B, R3A-3), how many rings the dark has reached,
	 * the chunks gone, the lights so far, the next ring at {@code at}.
	 */
	static final class Dark {
		final Remains.Place place;
		final List<List<ChunkPos>> rings;
		final java.util.Set<ChunkPos> gone = new java.util.HashSet<>();
		/** The rings reached so far (each step reaches one more). */
		int reached;
		int lights;
		long at;
		boolean pending;
		boolean glimpsed;

		Dark(Remains.Place place, List<List<ChunkPos>> rings, long at) {
			this.place = place;
			this.rings = rings;
			this.at = at;
		}

		/** The chunks of the rings reached that are still lit and not kept, outermost first. */
		List<ChunkPos> waiting(java.util.Set<ChunkPos> kept) {
			List<ChunkPos> out = new ArrayList<>();
			for (int r = 0; r < Math.min(reached, rings.size()); r++) {
				for (ChunkPos c : rings.get(r)) {
					if (!gone.contains(c) && !kept.contains(c)) {
						out.add(c);
					}
				}
			}
			return out;
		}

		/** How many rings from the edge are dark whole (a kept chunk counts as gone: it never goes). */
		int ringsDark(java.util.Set<ChunkPos> kept) {
			int out = 0;
			for (int r = 0; r < Math.min(reached, rings.size()); r++) {
				for (ChunkPos c : rings.get(r)) {
					if (!gone.contains(c) && !kept.contains(c)) {
						return out;
					}
				}
				out++;
			}
			return out;
		}
	}

	private final Map<Remains.Place, Dark> darks = new LinkedHashMap<>();
	/** The clock of the last look (20b v2's steps are timed on it). */
	private long lastLook;
	/** The floors gone dark and the in-game day they went (lit again on a later day, nobody on them). */
	private final Map<Remains.Place, Long> darkDay = new LinkedHashMap<>();

	private final Env env;
	private final Map<UUID, Remains.Place> visits = new LinkedHashMap<>();
	private final Map<Remains.Place, Long> askedAt = new LinkedHashMap<>();
	/** The animals held now and when their hold ends (server ticks), so a switch off lets them go at once. */
	private final Map<Animal, Long> held = new LinkedHashMap<>();

	/** The gallery's staged hold, waiting for its tick. */
	private record Later(Remains.Place place, List<Animal> animals, int ticks, long at) {
	}

	private final List<Later> later = new ArrayList<>();

	public FloorAnswers(Env env) {
		this.env = env;
	}

	/** A player came inside a place (only squares count). */
	public void entered(Remains.Place place, ServerPlayer player) {
		if (place.kind() == Remains.Kind.FLOOR) {
			visits.put(player.getUUID(), place);
		}
	}

	/** A player left a place. */
	public void left(Remains.Place place, ServerPlayer player) {
		visits.remove(player.getUUID(), place);
	}

	/** Every server tick: a look every {@value #EVERY_TICKS} ticks while someone is on a square. */
	public void tick(MinecraftServer server) {
		if (!staged.isEmpty()) {
			tickStaged(server.getTickCount());
		}
		if (!later.isEmpty()) {
			long now = server.getTickCount();
			for (Later l : List.copyOf(later)) {
				if (now >= l.at()) {
					later.remove(l);
					hold(l.place(), l.animals(), l.ticks(), List.of(), now);
				}
			}
		}
		if (!held.isEmpty()) {
			long now = server.getTickCount();
			held.entrySet().removeIf(e -> now > e.getValue() || !e.getKey().isAlive());
		}
		if (visits.isEmpty() && darks.isEmpty() && darkDay.isEmpty() || server.getTickCount() % EVERY_TICKS != 3) {
			return;
		}
		look(server, server.getTickCount());
	}

	/** One look: each square with a player near its building asks for 20b (at most once every {@value #ASK_AGAIN_TICKS} ticks). */
	public void look(MinecraftServer server, long now) {
		lastLook = now;
		if (!SelfCreated.enabled()) {
			return;
		}
		double radius = env.config().get().faceRadius;
		List<Remains.Place> near = new ArrayList<>();
		for (ServerPlayer player : env.people().online(server)) {
			Remains.Place place = visits.get(player.getUUID());
			if (place == null || player.isSpectator() || near.contains(place) || !player.level().dimension().equals(place.dimension())) {
				continue;
			}
			Vec3 middle = Vec3.atCenterOf(place.anchor());
			double dx = player.getX() - middle.x;
			double dz = player.getZ() - middle.z;
			if (dx * dx + dz * dz <= radius * radius) {
				near.add(place);
			}
		}
		for (Remains.Place place : near) {
			Long last = askedAt.get(place);
			if (last != null && now - last < ASK_AGAIN_TICKS) {
				continue;
			}
			ServerLevel level = server.getLevel(place.dimension());
			if (level != null) {
				askedAt.put(place, now);
				// Round 3 (R3-3): on a lit floor (version 3) 20b v2, at night only; a floor of round 2 (no light to take)
				// keeps round 2's 20b (the animals face the lit building).
				if (!lit(level, place)) {
					ask(level, place, false);
				} else if (env.night().test(level)) {
					askDark(level, place, false);
				}
			}
		}
		darkNext(server, now);
		relight(server);
	}

	/**
	 * True for a lit floor (version 3, R3-3): a fault light over the floor in the building's chunk, or the floor gone
	 * dark today (its lights taken). The building's chunk is the last 20b v2 takes.
	 */
	boolean lit(ServerLevel level, Remains.Place place) {
		if (darkDay.containsKey(place) || darks.containsKey(place)) {
			return true;
		}
		ChunkPos home = ChunkPos.containing(place.anchor());
		if (!level.hasChunk(home.x(), home.z())) {
			return false;
		}
		int y = FloorShape.floorOf(place).minY() + 1;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = home.getMinBlockX(); x <= home.getMaxBlockX(); x++) {
			for (int z = home.getMinBlockZ(); z <= home.getMaxBlockZ(); z++) {
				if (com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(at.set(x, y, z)))) {
					return true;
				}
			}
		}
		return false;
	}

	/** True at night (the floor's own lights are the sign; 20b v2 takes them then). */
	public static boolean night(ServerLevel level) {
		return level.isDarkOutside();
	}

	/** The rules of 20b v2's first step (the torches): once a player a day, health 10. */
	public static Answers.Rules darkRules(boolean waived) {
		Answers.Rules rules = Answers.Rules.of(FACE, DARK).perPlaceDay(Integer.MAX_VALUE).health(DARK_HEALTH).limitSeconds(60);
		return waived ? rules.perPlayerDay(Integer.MAX_VALUE) : rules;
	}

	/**
	 * 20b v2 (R3-3, R10-16): out of everyone's view the building's two torches go ({@code world:s20/dark}, ledgered); every
	 * animal on the floor turns to the dark doorway, silent, for {@code e} seconds; then the floor's light goes ring by ring
	 * from its edge in ({@link #darkNext}). {@code waived} lifts the caps (the dev command).
	 */
	public Answers.Result askDark(ServerLevel level, Remains.Place place, boolean waived) {
		if (darks.containsKey(place) || darkDay.containsKey(place)) {
			return new Answers.Result(Answers.Status.REFUSED, "dark already");
		}
		int ticks = (int) Math.round(Math.max(1.0, env.config().get().faceSeconds) * 20.0);
		return env.answers().submit(level, place, darkRules(waived), (lvl, p, inside) -> {
			List<BlockPos> torches = torches(lvl, p);
			List<List<ChunkPos>> rings = rings(p);
			if (torches.isEmpty() && rings.isEmpty()) {
				return null;
			}
			List<Animal> animals = onTheFloor(lvl, p);
			return Answers.Edit.of(torches, traces -> {
				boolean done = torches.isEmpty() || removing(traces.batch(lvl, DARK), torches).commit();
				if (done) {
					long now = lvl.getServer().getTickCount();
					darks.put(p, new Dark(p, rings, lastLook + darkTicks()));
					darkDay.put(p, env.day().applyAsLong(lvl.getServer()));
					hold(p, animals, ticks, inside, now, Vec3.atCenterOf(p.entrance()).add(0.0, 0.5, 0.0));
				}
				return done;
			});
		});
	}

	private long darkTicks() {
		return Math.max(20L, Math.round(env.config().get().darkRingSeconds * 20.0));
	}

	/**
	 * The floor's chunks in rings from its edge in (FIX-R3-B, R3A-3): the first every chunk on the square's edge, the next
	 * the edge of what is left, and so on to the middle (the building's chunks in the last). The dark comes in a ring at a
	 * time, so its edge walks toward the house from every side at once.
	 */
	public static List<List<ChunkPos>> rings(Remains.Place place) {
		BoundingBox floor = FloorShape.floorOf(place);
		int x0 = floor.minX() >> 4;
		int x1 = floor.maxX() >> 4;
		int z0 = floor.minZ() >> 4;
		int z1 = floor.maxZ() >> 4;
		List<List<ChunkPos>> out = new ArrayList<>();
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				int r = Math.min(Math.min(cx - x0, x1 - cx), Math.min(cz - z0, z1 - cz));
				while (out.size() <= r) {
					out.add(new ArrayList<>());
				}
				out.get(r).add(new ChunkPos(cx, cz));
			}
		}
		return out;
	}

	/** The building's torches as they stand now (a 9 x 9 x 6 round its middle). */
	public static List<BlockPos> torches(ServerLevel level, Remains.Place place) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos a = place.anchor();
		for (BlockPos pos : BlockPos.betweenClosed(a.offset(-4, -1, -4), a.offset(4, 4, 4))) {
			if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BaseTorchBlock) {
				out.add(pos.immutable());
			}
		}
		return out;
	}

	/**
	 * The chunks a player on the floor keeps lit: the one they stand in and the one between it and the floor's nearest
	 * edge (their way off stays lit).
	 */
	static java.util.Set<ChunkPos> kept(BoundingBox floor, List<ServerPlayer> players) {
		java.util.Set<ChunkPos> out = new java.util.HashSet<>();
		for (ServerPlayer player : players) {
			out.addAll(keptAt(floor, player.getBlockX(), player.getBlockZ()));
		}
		return out;
	}

	/** The two chunks one standing at (x, z) keeps lit: theirs and the one toward the floor's nearest edge. */
	static java.util.Set<ChunkPos> keptAt(BoundingBox floor, int x, int z) {
		java.util.Set<ChunkPos> out = new java.util.HashSet<>();
		ChunkPos at = new ChunkPos(x >> 4, z >> 4);
		out.add(at);
		int west = x - floor.minX();
		int east = floor.maxX() - x;
		int north = z - floor.minZ();
		int south = floor.maxZ() - z;
		int least = Math.min(Math.min(west, east), Math.min(north, south));
		int sx = least == west ? -1 : least == east ? 1 : 0;
		int sz = sx != 0 ? 0 : least == north ? -1 : 1;
		out.add(new ChunkPos(at.x() + sx, at.z() + sz));
		return out;
	}

	/**
	 * The next ring of every floor going dark, once its time comes (FIX-R3-B, R3A-3: every chunk of the ring at once, then
	 * the next ring in every {@code g} seconds, so the dark's edge walks toward the house); then the glimpse once the dark
	 * has reached {@code i} rings in. Each step takes every lit chunk of the rings reached that nobody sees now (a chunk
	 * in view waits for a later step), never the chunk a player stands in nor the one toward their nearest edge, at most
	 * {@code h} lights a visit.
	 */
	private void darkNext(MinecraftServer server, long now) {
		Sc20Config config = env.config().get();
		for (Dark d : List.copyOf(darks.values())) {
			ServerLevel level = server.getLevel(d.place.dimension());
			if (level == null) {
				darks.remove(d.place);
				continue;
			}
			List<ServerPlayer> on = onFloor(server, d.place);
			if (on.isEmpty() && !d.pending) {
				// Nobody on the floor: what went stays dark, nothing more goes this visit.
				darks.remove(d.place);
				continue;
			}
			boolean glimpseDue = d.reached >= config.glimpseAfterRings && !d.gone.isEmpty();
			if (glimpseDue && !d.glimpsed) {
				glimpse(level, d, on);
			}
			if (d.pending) {
				if (!env.answers().waiting(d.place, FACE)) {
					d.pending = false;
					d.at = now + darkTicks();
				}
				continue;
			}
			if (now < d.at) {
				continue;
			}
			java.util.Set<ChunkPos> kept = kept(FloorShape.floorOf(d.place), on);
			if (d.lights < config.darkPerVisit && d.reached < d.rings.size()) {
				d.reached++;
			}
			if (d.lights >= config.darkPerVisit || d.waiting(kept).isEmpty()) {
				if (d.lights >= config.darkPerVisit || d.reached >= d.rings.size()) {
					if (d.glimpsed || !glimpseDue) {
						darks.remove(d.place);
					}
				} else {
					d.at = now + darkTicks();
				}
				continue;
			}
			int room = config.darkPerVisit - d.lights;
			Answers.Result result = env.answers().submit(level, d.place, darkRules(true).part(), (lvl, p, inside) -> {
				// Read again at every try: who stands where, and which chunks nobody sees now.
				List<ChunkPos> unseen = new ArrayList<>();
				List<BlockPos> cells = new ArrayList<>();
				for (ChunkPos c : d.waiting(kept(FloorShape.floorOf(p), onFloor(lvl.getServer(), p)))) {
					if (!lvl.hasChunk(c.x(), c.z())) {
						continue;
					}
					List<BlockPos> lights = chunkLights(lvl, p, c);
					if (cells.size() + lights.size() > room) {
						break;
					}
					if (!lights.isEmpty() && !env.view().outOfView(lvl, lights)) {
						continue;
					}
					unseen.add(c);
					cells.addAll(lights);
				}
				if (unseen.isEmpty()) {
					return null;
				}
				List<BlockPos> taken = List.copyOf(cells);
				return Answers.Edit.of(taken, traces -> {
					boolean done = taken.isEmpty() || removing(traces.batch(lvl, DARK), taken).commit();
					if (done) {
						d.gone.addAll(unseen);
						d.lights += taken.size();
						d.pending = false;
						d.at = lastLook + darkTicks();
					}
					return done;
				});
			});
			if (result.status() == Answers.Status.WAITING) {
				d.pending = true;
			} else if (result.status() != Answers.Status.DONE) {
				darks.remove(d.place);
			}
		}
	}

	/** The lights of the floor in one chunk: the square's own (its top and its cut sides) and its built sides' in front of it. */
	public static List<BlockPos> chunkLights(ServerLevel level, Remains.Place place, ChunkPos chunk) {
		BoundingBox floor = FloorShape.floorOf(place);
		BoundingBox around = new BoundingBox(Math.max(floor.minX() - 1, chunk.getMinBlockX()), place.box().minY(), Math.max(floor.minZ() - 1, chunk.getMinBlockZ()),
				Math.min(floor.maxX() + 1, chunk.getMaxBlockX()), place.box().maxY(), Math.min(floor.maxZ() + 1, chunk.getMaxBlockZ()));
		if (!level.hasChunk(chunk.x(), chunk.z())) {
			return List.of();
		}
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
			if (com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(pos))) {
				out.add(pos.immutable());
			}
		}
		return out;
	}

	/** Everyone on this floor now (spectators and creative never: they are never inside, D-116). */
	private List<ServerPlayer> onFloor(MinecraftServer server, Remains.Place place) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : env.people().online(server)) {
			if (place.equals(visits.get(player.getUUID())) && !player.isSpectator() && !player.isCreative()) {
				out.add(player);
			}
		}
		return out;
	}

	/** The glimpse in the dark doorway (D-105), for one player on the floor 20 to 40 from it who can see it. */
	private void glimpse(ServerLevel level, Dark d, List<ServerPlayer> on) {
		Figure figure = env.figure().get();
		// In the doorway itself (the place's entrance is the cell outside it).
		Vec3 feet = Vec3.atBottomCenterOf(d.place.entrance().relative(d.place.entranceFacing().getOpposite()));
		for (ServerPlayer player : on) {
			double far = player.position().distanceTo(feet);
			if (far < GLIMPSE_MIN || far > GLIMPSE_MAX || !figure.canGlimpse(player, feet)) {
				continue;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - feet.x), player.getZ() - feet.z));
			if (figure.glimpse(player, feet, yaw) == FireResult.FIRED) {
				d.glimpsed = true;
				return;
			}
		}
	}

	/** A floor gone dark is lit again on a later in-game day while nobody is on it: its ledgered lights and torches taken back. */
	private void relight(MinecraftServer server) {
		for (Map.Entry<Remains.Place, Long> e : List.copyOf(darkDay.entrySet())) {
			Remains.Place place = e.getKey();
			ServerLevel level = server.getLevel(place.dimension());
			if (level == null || darks.containsKey(place) || env.day().applyAsLong(server) <= e.getValue() || !onFloor(server, place).isEmpty()) {
				continue;
			}
			int left = 0;
			BoundingBox box = place.box();
			com.forzacode.a1016_02.core.TraceLedger ledger = com.forzacode.a1016_02.core.TraceLedger.get(server);
			for (com.forzacode.a1016_02.core.TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
				if (entry.cause().equals(DARK) && entry.pos().dimension().equals(place.dimension()) && box.isInside(entry.pos().pos())) {
					com.forzacode.a1016_02.core.TraceService.UndoResult r = Services.traces().undo(level, entry, DARK + "/back");
					left += r == com.forzacode.a1016_02.core.TraceService.UndoResult.IN_VIEW || r == com.forzacode.a1016_02.core.TraceService.UndoResult.UNLOADED ? 1 : 0;
				}
			}
			if (left == 0) {
				darkDay.remove(place);
			}
		}
	}

	/** The gallery's staged dark (dev only): a floor's rings going one every {@code every} ticks from {@code start}, the first after one step. */
	private static final class Staged {
		final ServerLevel level;
		final Remains.Place place;
		final List<List<ChunkPos>> rings;
		final java.util.Set<ChunkPos> kept;
		final int every;
		final long start;
		int done;

		Staged(ServerLevel level, Remains.Place place, List<List<ChunkPos>> rings, java.util.Set<ChunkPos> kept, int every, long start) {
			this.level = level;
			this.place = place;
			this.rings = rings;
			this.kept = kept;
			this.every = every;
			this.start = start;
		}
	}

	private final List<Staged> staged = new ArrayList<>();
	/** What the gallery's staging took from each floor (dev only; put back when the floor is staged again). */
	private static final Map<Remains.Place, Map<BlockPos, net.minecraft.world.level.block.state.BlockState>> STAGED_TAKEN = new LinkedHashMap<>();

	/**
	 * The gallery's staging of 20b (dev only, unledgered): the torches gone now, these animals turned to the dark doorway,
	 * then the floor's rings going one every {@code every} ticks from its edge in (the game's pace is {@code g} seconds),
	 * never the chunk {@code standing} is in nor the one toward its nearest edge (a player there keeps them), and with
	 * nobody's view asked (what a player sees each time they look along the floor).
	 */
	public static void stageDark(ServerLevel level, Remains.Place place, List<Animal> animals, int every, BlockPos standing) {
		// A second staging of the same floor starts from it lit again.
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = STAGED_TAKEN.remove(place);
		if (before != null) {
			LIVE.staged.removeIf(s -> s.place.equals(place));
			before.forEach((pos, state) -> level.setBlock(pos, state, 3));
		}
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> taken = new LinkedHashMap<>();
		STAGED_TAKEN.put(place, taken);
		for (BlockPos torch : torches(level, place)) {
			taken.put(torch, level.getBlockState(torch));
			level.setBlock(torch, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		}
		long now = level.getServer().getTickCount();
		LIVE.hold(place, animals, 20 * 60 * 3, List.of(), now, Vec3.atCenterOf(place.entrance()).add(0.0, 0.5, 0.0));
		java.util.Set<ChunkPos> kept = keptAt(FloorShape.floorOf(place), standing.getX(), standing.getZ());
		LIVE.staged.add(new Staged(level, place, rings(place), kept, Math.max(1, every), now));
	}

	private void tickStaged(long now) {
		for (Staged s : List.copyOf(staged)) {
			int due = (int) Math.min(s.rings.size(), (now - s.start) / s.every);
			while (s.done < due) {
				for (ChunkPos c : s.rings.get(s.done)) {
					if (s.kept.contains(c)) {
						continue;
					}
					Map<BlockPos, net.minecraft.world.level.block.state.BlockState> taken = STAGED_TAKEN.computeIfAbsent(s.place, k -> new LinkedHashMap<>());
					for (BlockPos light : chunkLights(s.level, s.place, c)) {
						taken.put(light, s.level.getBlockState(light));
						s.level.setBlock(light, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					}
				}
				s.done++;
			}
			if (s.done >= s.rings.size()) {
				staged.remove(s);
			}
		}
	}

	/** How many rings the dark has reached on this floor this visit (tests; -1 if none is going). */
	public int ringsReached(Remains.Place place) {
		Dark d = darks.get(place);
		return d == null ? -1 : d.reached;
	}

	/** True while this floor is going dark or dark (tests). */
	public boolean darkened(Remains.Place place) {
		return darkDay.containsKey(place);
	}

	/** How many of this floor's chunks have gone dark this visit (tests; -1 if none is going). */
	public int chunksGone(Remains.Place place) {
		Dark d = darks.get(place);
		return d == null ? -1 : d.gone.size();
	}

	/** Asks for 20b at this square now; {@code waived} lifts the caps (the dev command). */
	public Answers.Result ask(ServerLevel level, Remains.Place place, boolean waived) {
		Answers.Rules rules = Answers.Rules.of(FACE, CAUSE).perPlaceDay(Integer.MAX_VALUE).limitSeconds(30);
		if (waived) {
			rules = rules.perPlayerDay(Integer.MAX_VALUE);
		}
		int ticks = (int) Math.round(Math.max(1.0, env.config().get().faceSeconds) * 20.0);
		return env.answers().submit(level, place, rules, (lvl, p, inside) -> {
			List<Animal> animals = onTheFloor(lvl, p);
			if (animals.isEmpty()) {
				return null;
			}
			return Answers.Edit.of(List.of(), traces -> hold(p, animals, ticks, inside, lvl.getServer().getTickCount()) > 0);
		});
	}

	/** The free animals standing on this square's floor. */
	public List<Animal> onTheFloor(ServerLevel level, Remains.Place place) {
		BoundingBox floor = FloorShape.floorOf(place);
		AABB area = new AABB(floor.minX(), floor.minY() + 0.5, floor.minZ(), floor.maxX() + 1.0, floor.minY() + 1.0 + ON_FLOOR, floor.maxZ() + 1.0);
		MobTamper tamper = env.tamper().get();
		return level.getEntitiesOfClass(Animal.class, area, animal -> free(animal) && !tamper.isTampered(animal));
	}

	/** An animal that may be turned: alive, nobody's, on no leash, riding nothing and carrying nobody. */
	public static boolean free(Animal animal) {
		return animal.isAlive() && !animal.isLeashed() && !animal.isPassenger() && !animal.isVehicle() && !(animal instanceof OwnableEntity owned && owned.getOwnerReference() != null)
				&& !(animal instanceof AbstractHorse horse && horse.isTamed());
	}

	/**
	 * Holds these animals now, all in this tick: frozen, silent, facing the building's middle, for {@code ticks}, each bound
	 * to the nearest of {@code players}. Returns how many were held.
	 */
	public int hold(Remains.Place place, List<Animal> animals, int ticks, List<ServerPlayer> players, long now) {
		return hold(place, animals, ticks, players, now, Vec3.atCenterOf(place.anchor()).add(0.0, 0.7, 0.0));
	}

	/** Every cell of {@code cells} removed in this batch. */
	private static TraceBatch removing(TraceBatch batch, List<BlockPos> cells) {
		for (BlockPos cell : cells) {
			batch.remove(cell);
		}
		return batch;
	}

	/** {@link #hold} facing {@code point} (20b v2: the dark doorway). */
	public int hold(Remains.Place place, List<Animal> animals, int ticks, List<ServerPlayer> players, long now, Vec3 point) {
		MobTamper tamper = env.tamper().get();
		int count = 0;
		for (Animal animal : animals) {
			if (!tamper.freeze(animal, ticks)) {
				continue;
			}
			tamper.face(animal, point, ticks);
			tamper.silence(animal, ticks);
			ServerPlayer nearest = null;
			for (ServerPlayer player : players) {
				if (nearest == null || player.distanceToSqr(animal) < nearest.distanceToSqr(animal)) {
					nearest = player;
				}
			}
			if (nearest != null) {
				tamper.bind(animal, nearest.getUUID());
			}
			held.put(animal, now + ticks);
			count++;
		}
		return count;
	}

	/** The gallery's staging: holds these animals {@code delay} ticks from now, bound to nobody (dev only). */
	public void holdLater(MinecraftServer server, Remains.Place place, List<Animal> animals, int ticks, int delay) {
		later.add(new Later(place, List.copyOf(animals), ticks, server.getTickCount() + delay));
	}

	/** How many animals 20b holds now. */
	public int holding() {
		return held.size();
	}

	/** Forgets every visit (server stop). */
	public void reset() {
		visits.clear();
		askedAt.clear();
		held.clear();
		later.clear();
		darks.clear();
		darkDay.clear();
	}

	/** Switched off: every animal it holds is let go now, and every visit forgotten. */
	public void switchedOff() {
		MobTamper tamper = env.tamper().get();
		for (Animal animal : List.copyOf(held.keySet())) {
			tamper.release(animal);
		}
		reset();
	}
}
