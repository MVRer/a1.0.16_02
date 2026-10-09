package com.forzacode.a1016_02.world.live;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldSites;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.gen.Vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jspecify.annotations.Nullable;

/**
 * New scars (DESIGN.md "New scar out of view"): a hill the player crossed days ago goes dead, or a grove goes
 * bare. Only in chunks the player visited and then left for {@code Pacing.newScarAwayDays} in-game days, and only
 * out of view (one {@link TraceBatch}: all of it or nothing). Edges stop on chunk lines where the player was more
 * recently, so they end on a sharp line. Server thread only.
 */
public final class NewScarPlacer {
	public static final String CAUSE = "world:new_scar";
	/** Loads the chunks of a waiting new scar in the background; it never ticks them and expires on its own. */
	public static final TicketType TICKET = new TicketType(1200L, TicketType.FLAG_LOADING);
	/** At most this many stale chunks are looked at per try. */
	private static final int MAX_LOOKS = 64;
	/** New scars keep this far (plus their radius) from the player's base. */
	private static final int BASE_CLEARANCE = 64;
	/** Loading tickets asked for per tick while a new scar waits for its chunks. */
	private static final int LOADS_PER_TICK = 2;
	/** A waiting new scar gives up after this long (its chunks did not load, or it stayed in view). */
	private static final long JOB_TIMEOUT_TICKS = 1200;
	/** Ticks between commit tries once its chunks are loaded (it may be in view). */
	private static final long RETRY_TICKS = 20;
	/** Leaves of a removed trunk are taken this far around it. */
	private static final int CROWN = 3;
	private static final String IN_VIEW = "in view";

	/** Where a chunk was last visited (seeded in tests). */
	@FunctionalInterface
	public interface VisitLookup {
		long lastVisitDay(ServerLevel level, ChunkPos chunk);
	}

	/**
	 * What the placer did.
	 *
	 * @param placed    the scar is there now
	 * @param scheduled the scar waits for its chunks to load and is applied over the next ticks
	 */
	public record Outcome(boolean placed, boolean scheduled, String message) {
		static Outcome done(String message) {
			return new Outcome(true, false, message);
		}

		static Outcome failed(String message) {
			return new Outcome(false, false, message);
		}
	}

	/** One planned block change: removal, or conversion to {@code to}. */
	public record Edit(BlockPos pos, @Nullable BlockState to) {
	}

	/** {@code tree}: the fake, one bare tree in the chunk of {@code center}. */
	private record Candidate(boolean dead, boolean tree, BlockPos center, int contourY, double score) {
	}

	/** A new scar waiting for its chunks: one at a time, never more than {@link #LOADS_PER_TICK} loads a tick. */
	private static final class Job {
		final ResourceKey<Level> dimension;
		final Candidate candidate;
		final VisitLookup visits;
		final List<ChunkPos> chunks;
		final Set<Long> requested = new HashSet<>();
		final long started;
		final @Nullable Consumer<String> report;
		long nextTry;

		Job(ResourceKey<Level> dimension, Candidate candidate, VisitLookup visits, List<ChunkPos> chunks, long started, @Nullable Consumer<String> report) {
			this.dimension = dimension;
			this.candidate = candidate;
			this.visits = visits;
			this.chunks = chunks;
			this.started = started;
			this.report = report;
		}
	}

	private static @Nullable Job pending;

	private NewScarPlacer() {
	}

	/** True if a chunk was visited, and last visited at least {@code awayDays} in-game days ago. */
	public static boolean stale(long visitDay, long today, int awayDays) {
		return visitDay >= 0 && today - visitDay >= awayDays;
	}

	public static Predicate<ChunkPos> staleChunks(ServerLevel level, VisitLookup visits, long today) {
		int away = ModConfig.pacing().newScarAwayDays;
		Map<Long, Boolean> cache = new LinkedHashMap<>();
		return chunk -> cache.computeIfAbsent(chunk.pack(), k -> stale(visits.lastVisitDay(level, chunk), today, away));
	}

	/** {@code /a1016 world newscar now}: the subject's best stale area, rules kept. {@code report} hears how it ends. */
	public static Outcome forceNow(MinecraftServer server, @Nullable Consumer<String> report) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return Outcome.failed("the subject is not online");
		}
		return place(subject.get(), RandomSource.create(), false, Services.watch()::lastVisitDay, GameClock.day(server), report);
	}

	/**
	 * Finds the best stale area near the player and kills or strips it ({@code fake}: one bare tree). Areas whose
	 * chunks are all loaded are done now; the first one that needs loading is scheduled (only one at a time) and its
	 * chunks load through tickets over the next ticks. Nothing is loaded synchronously.
	 */
	public static Outcome place(ServerPlayer player, RandomSource random, boolean fake, VisitLookup visits, long today,
			@Nullable Consumer<String> report) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return Outcome.failed("new scars only happen in the overworld");
		}
		if (pending != null) {
			return Outcome.failed("a new scar is already waiting for its chunks");
		}
		WorldConfig config = WorldConfig.get();
		Predicate<ChunkPos> allowed = staleChunks(level, visits, today);
		List<ChunkPos> stale = new ArrayList<>();
		ChunkPos home = player.chunkPosition();
		int range = config.newScarSearchChunks;
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				ChunkPos chunk = new ChunkPos(home.x() + dx, home.z() + dz);
				if (allowed.test(chunk)) {
					stale.add(chunk);
				}
			}
		}
		// Never at the player's base: their own trees are another card's ("Your trees stripped").
		Optional<BlockPos> base = Services.watch().base(player).filter(b -> b.dimension().equals(level.dimension())).map(GlobalPos::pos);
		int baseClearance = BASE_CLEARANCE + config.newScarRadius;
		if (base.isPresent()) {
			stale.removeIf(c -> base.get().distToCenterSqr(c.getMiddleBlockX(), base.get().getY(), c.getMiddleBlockZ())
					< (double) baseClearance * baseClearance);
		}
		if (stale.isEmpty()) {
			return Outcome.failed("no chunk the player left " + ModConfig.pacing().newScarAwayDays + "+ in-game days ago within " + range
					+ " chunks (away from the base)");
		}
		List<Candidate> candidates = fake ? trees(stale, random) : areas(level, stale, allowed, random);
		if (candidates.isEmpty()) {
			return Outcome.failed(stale.size() + " stale chunk(s), but no hill or grove among them");
		}
		int tried = 0;
		for (Candidate candidate : candidates) {
			List<ChunkPos> chunks = chunksFor(candidate, config, allowed);
			if (chunks.stream().allMatch(c -> level.getChunkSource().getChunkNow(c.x(), c.z()) != null)) {
				Outcome outcome = apply(level, candidate, allowed, config);
				if (outcome.placed()) {
					return outcome;
				}
				tried++;
				continue;
			}
			pending = new Job(level.dimension(), candidate, visits, chunks, level.getServer().getTickCount(), report);
			String what = candidate.tree() ? "one bare tree" : candidate.dead() ? "a dead hill" : "a bare grove";
			return new Outcome(false, true, "scheduled " + what + " at " + candidate.center().toShortString() + ", loading " + chunks.size()
					+ " chunk(s) in the background");
		}
		return Outcome.failed("every loaded candidate area (" + tried + ") was in view or empty");
	}

	/** Called every server tick: loads a waiting new scar's chunks a few at a time, then applies it. */
	public static void tick(MinecraftServer server) {
		Job job = pending;
		if (job == null) {
			return;
		}
		ServerLevel level = server.getLevel(job.dimension);
		long now = server.getTickCount();
		if (level == null || now - job.started > JOB_TIMEOUT_TICKS) {
			finish(job, Outcome.failed("the new scar at " + job.candidate.center().toShortString()
					+ " gave up (its chunks did not load, or it stayed in view)"));
			return;
		}
		boolean ready = true;
		int asked = 0;
		for (ChunkPos chunk : job.chunks) {
			if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
				continue;
			}
			ready = false;
			if (asked < LOADS_PER_TICK && job.requested.add(chunk.pack())) {
				level.getChunkSource().addTicketWithRadius(TICKET, chunk, 0);
				asked++;
			}
		}
		if (!ready || now < job.nextTry) {
			return;
		}
		// The player may have come back while the chunks loaded: check the visits again.
		Predicate<ChunkPos> allowed = staleChunks(level, job.visits, GameClock.day(server));
		Outcome outcome = apply(level, job.candidate, allowed, WorldConfig.get());
		if (outcome.placed() || !outcome.message().equals(IN_VIEW)) {
			finish(job, outcome);
		} else {
			job.nextTry = now + RETRY_TICKS;
		}
	}

	private static void finish(Job job, Outcome outcome) {
		pending = null;
		if (!outcome.placed()) {
			A1016_02.LOGGER.debug("[a1016] world: new scar not placed: {}", outcome.message());
		}
		if (job.report != null) {
			job.report.accept(outcome.message());
		}
	}

	/** True while a new scar waits for its chunks. */
	public static boolean waiting() {
		return pending != null;
	}

	/** Called when a server stops. */
	public static void clear() {
		pending = null;
	}

	/** Hills and groves among the stale chunks, best first (from the noise: nothing is loaded). */
	private static List<Candidate> areas(ServerLevel level, List<ChunkPos> stale, Predicate<ChunkPos> allowed, RandomSource random) {
		Terrain terrain = terrain(level);
		List<Candidate> hills = new ArrayList<>();
		List<Candidate> groves = new ArrayList<>();
		// Each look at a chunk asks the noise a few times: look at a random sample, not every stale chunk.
		List<ChunkPos> sample = new ArrayList<>(stale);
		Util.shuffle(sample, random);
		for (ChunkPos chunk : sample.subList(0, Math.min(MAX_LOOKS, sample.size()))) {
			int x = chunk.getMiddleBlockX();
			int z = chunk.getMiddleBlockZ();
			if (terrain.wet(x, z)) {
				continue;
			}
			int ground = terrain.ground(x, z);
			Holder<Biome> biome = terrain.biome(x, ground, z);
			int neighbours = 0;
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					neighbours += allowed.test(new ChunkPos(chunk.x() + ox, chunk.z() + oz)) ? 1 : 0;
				}
			}
			if (Terrain.isWooded(biome)) {
				groves.add(new Candidate(false, false, new BlockPos(x, ground, z), Integer.MIN_VALUE, neighbours + random.nextDouble()));
			}
			if (Terrain.isGrassy(biome) && ground >= terrain.seaLevel() + 4) {
				int around = (terrain.ground(x + 20, z) + terrain.ground(x - 20, z) + terrain.ground(x, z + 20) + terrain.ground(x, z - 20)) / 4;
				int prominence = ground - around;
				if (prominence >= 4) {
					int contour = ground - Math.clamp(prominence, 4, 10);
					hills.add(new Candidate(true, false, new BlockPos(x, ground, z), contour, prominence + neighbours + random.nextDouble()));
				}
			}
		}
		hills.sort(Comparator.comparingDouble(Candidate::score).reversed());
		groves.sort(Comparator.comparingDouble(Candidate::score).reversed());
		List<Candidate> order = new ArrayList<>();
		for (List<Candidate> list : random.nextBoolean() ? List.of(hills, groves) : List.of(groves, hills)) {
			order.addAll(list.subList(0, Math.min(4, list.size())));
		}
		return order;
	}

	/** The fake: a few stale chunks, in one of which one tree will lose its leaves. */
	private static List<Candidate> trees(List<ChunkPos> stale, RandomSource random) {
		List<ChunkPos> shuffled = new ArrayList<>(stale);
		Util.shuffle(shuffled, random);
		List<Candidate> order = new ArrayList<>();
		for (ChunkPos chunk : shuffled.subList(0, Math.min(6, shuffled.size()))) {
			order.add(new Candidate(false, true, chunk.getMiddleBlockPosition(0), Integer.MIN_VALUE, 0));
		}
		return order;
	}

	/** The allowed chunks a candidate reads and changes. */
	private static List<ChunkPos> chunksFor(Candidate candidate, WorldConfig config, Predicate<ChunkPos> allowed) {
		if (candidate.tree()) {
			return List.of(ChunkPos.containing(candidate.center()));
		}
		int reach = config.newScarRadius + CROWN;
		List<ChunkPos> chunks = new ArrayList<>();
		BlockPos c = candidate.center();
		for (int cx = (c.getX() - reach) >> 4; cx <= (c.getX() + reach) >> 4; cx++) {
			for (int cz = (c.getZ() - reach) >> 4; cz <= (c.getZ() + reach) >> 4; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				if (allowed.test(chunk)) {
					chunks.add(chunk);
				}
			}
		}
		return chunks;
	}

	private static Terrain terrain(ServerLevel level) {
		ScarContext context = ScarContext.current();
		ScarPlanner planner = context == null ? null : context.planner(level);
		return planner != null ? planner.terrain() : new LiveTerrain(level);
	}

	/** Applies one candidate on loaded chunks (columns in unloaded chunks are skipped, never loaded). */
	private static Outcome apply(ServerLevel level, Candidate candidate, Predicate<ChunkPos> allowed, WorldConfig config) {
		if (candidate.tree()) {
			ChunkPos chunk = ChunkPos.containing(candidate.center());
			BlockPos trunk = level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null ? null : findTree(level, chunk);
			if (trunk == null || !allowed.test(chunk)) {
				return Outcome.failed("no tree there");
			}
			List<Edit> edits = collectBare(level, trunk, CROWN, allowed);
			if (edits.isEmpty()) {
				return Outcome.failed("nothing to change");
			}
			return commit(level, edits, CAUSE + "/tree") ? Outcome.done("one bare tree at " + trunk.toShortString() + " (" + edits.size() + " blocks)")
					: Outcome.failed(IN_VIEW);
		}
		for (int radius = config.newScarRadius; radius >= 8; radius -= 4) {
			List<Edit> edits = candidate.dead() ? collectDead(level, candidate.center(), radius, candidate.contourY(), allowed)
					: collectBare(level, candidate.center(), radius, allowed);
			if (edits.isEmpty()) {
				return Outcome.failed("nothing to change");
			}
			if (edits.size() > config.newScarMaxBlocks) {
				continue;
			}
			if (!commit(level, edits, CAUSE + (candidate.dead() ? "/dead" : "/bare"))) {
				return Outcome.failed(IN_VIEW);
			}
			BlockPos site = candidate.dead() ? siteOnGround(level, candidate.center()) : nearestTrunk(level, candidate.center(), radius);
			WorldSites.record(candidate.dead() ? SiteType.DEAD_MOUNTAIN : SiteType.BARE_GROVE, level.dimension(), site, radius, null);
			String what = candidate.dead() ? "dead hill" : "bare grove";
			A1016_02.LOGGER.info("[a1016] world: new scar ({}) at {} r={} ({} blocks)", what, site.toShortString(), radius, edits.size());
			return Outcome.done("new scar: " + what + " at " + site.toShortString() + " radius " + radius + " (" + edits.size() + " blocks)");
		}
		return Outcome.failed("too large");
	}

	/** A trunk base in the chunk whose crown is above it, or null. */
	private static @Nullable BlockPos findTree(ServerLevel level, ChunkPos chunk) {
		for (int lx = 2; lx < 14; lx += 2) {
			for (int lz = 2; lz < 14; lz += 2) {
				int x = chunk.getBlockX(lx);
				int z = chunk.getBlockZ(lz);
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				if (!Vegetation.isLeafy(level.getBlockState(new BlockPos(x, top, z)))) {
					continue;
				}
				int ground = Vegetation.groundY(level, x, z, top, level.getMinY());
				BlockPos base = new BlockPos(x, ground + 1, z);
				if (Vegetation.isLog(level.getBlockState(base))) {
					return base;
				}
			}
		}
		return null;
	}

	/** Commits the edits as one out-of-view batch. */
	public static boolean commit(ServerLevel level, List<Edit> edits, String cause) {
		TraceBatch batch = Services.traces().batch(level, cause);
		for (Edit edit : edits) {
			if (edit.to() == null) {
				batch.remove(edit.pos());
			} else {
				batch.convert(edit.pos(), edit.to());
			}
		}
		return batch.commit();
	}

	/**
	 * A dead hill: inside the circle, in allowed chunks, above the contour: everything that grows goes, grass turns
	 * to dirt, and trunks take their crowns with them.
	 */
	public static List<Edit> collectDead(ServerLevel level, BlockPos center, int radius, int contourY, Predicate<ChunkPos> allowed) {
		Map<BlockPos, Edit> edits = new LinkedHashMap<>();
		List<BlockPos> logs = new ArrayList<>();
		forColumns(level, center, radius, allowed, (x, z, top, ground) -> {
			if (ground < contourY) {
				return;
			}
			for (int y = top; y > ground; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if ((Vegetation.dies(state) || Vegetation.isSnowLayer(state) && y > ground + 1) && !playerMade(level, pos)) {
					edits.put(pos, new Edit(pos, null));
					if (Vegetation.isLog(state)) {
						logs.add(pos);
					}
				}
			}
			BlockPos groundPos = new BlockPos(x, ground, z);
			if (Vegetation.isGrassGround(level.getBlockState(groundPos)) && !playerMade(level, groundPos)) {
				edits.put(groundPos, new Edit(groundPos, Blocks.DIRT.defaultBlockState()));
			}
		});
		for (BlockPos log : logs) {
			for (BlockPos pos : BlockPos.betweenClosed(log.offset(-CROWN, -1, -CROWN), log.offset(CROWN, CROWN, CROWN))) {
				ChunkPos crownChunk = ChunkPos.containing(pos);
				if (edits.containsKey(pos) || !allowed.test(crownChunk) || level.getChunkSource().getChunkNow(crownChunk.x(), crownChunk.z()) == null) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if ((Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && Vegetation.isLeafy(level.getBlockState(pos.below())))
						&& !playerMade(level, pos)) {
					BlockPos at = pos.immutable();
					edits.put(at, new Edit(at, null));
				}
			}
		}
		return new ArrayList<>(edits.values());
	}

	/** A bare grove: inside the circle, in allowed chunks, every leaf (and what hangs from it) goes; trunks stay. */
	public static List<Edit> collectBare(ServerLevel level, BlockPos center, int radius, Predicate<ChunkPos> allowed) {
		List<Edit> edits = new ArrayList<>();
		forColumns(level, center, radius, allowed, (x, z, top, ground) -> {
			for (int y = top; y > ground; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(pos);
				if ((Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && Vegetation.isLeafy(level.getBlockState(pos.below())))
						&& !playerMade(level, pos)) {
					edits.add(new Edit(pos, null));
				}
			}
		});
		return edits;
	}

	/** Blocks a player placed are never part of a new scar (replanted leaves stay; their own trees are another card's). */
	private static boolean playerMade(ServerLevel level, BlockPos pos) {
		return Services.watch().wasPlacedByPlayer(level, pos);
	}

	@FunctionalInterface
	private interface ColumnVisitor {
		void visit(int x, int z, int top, int ground);
	}

	private static void forColumns(ServerLevel level, BlockPos center, int radius, Predicate<ChunkPos> allowed, ColumnVisitor visitor) {
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				long dx = x - center.getX();
				long dz = z - center.getZ();
				if (dx * dx + dz * dz > (long) radius * radius) {
					continue;
				}
				ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
				if (!allowed.test(chunk) || level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
					continue; // never load a chunk here: a waiting new scar loads its chunks through tickets first
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int ground = Vegetation.groundY(level, x, z, top, level.getMinY());
				visitor.visit(x, z, top, ground);
			}
		}
	}

	private static BlockPos siteOnGround(ServerLevel level, BlockPos center) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ()) - 1;
		return new BlockPos(center.getX(), Vegetation.groundY(level, center.getX(), center.getZ(), top, level.getMinY()) + 1, center.getZ());
	}

	/** The trunk base nearest the center (the grove's center tree), or the ground there. */
	public static BlockPos nearestTrunk(ServerLevel level, BlockPos center, int radius) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				double d = (x - center.getX()) * (double) (x - center.getX()) + (z - center.getZ()) * (double) (z - center.getZ());
				if (d >= bestDist || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int ground = Vegetation.groundY(level, x, z, top, level.getMinY());
				BlockPos base = new BlockPos(x, ground + 1, z);
				if (Vegetation.isLog(level.getBlockState(base))) {
					best = base;
					bestDist = d;
				}
			}
		}
		return best != null ? best : siteOnGround(level, center);
	}
}
