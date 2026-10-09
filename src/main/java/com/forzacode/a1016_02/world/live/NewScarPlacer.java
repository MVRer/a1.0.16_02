package com.forzacode.a1016_02.world.live;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	/** At most this many stale chunks are looked at per try. */
	private static final int MAX_LOOKS = 64;

	/** Where a chunk was last visited (seeded in tests). */
	@FunctionalInterface
	public interface VisitLookup {
		long lastVisitDay(ServerLevel level, ChunkPos chunk);
	}

	/** What the placer did. */
	public record Outcome(boolean placed, String message) {
	}

	/** One planned block change: removal, or conversion to {@code to}. */
	public record Edit(BlockPos pos, @Nullable BlockState to) {
	}

	private record Candidate(boolean dead, BlockPos center, int contourY, double score) {
	}

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

	/** {@code /a1016 world newscar now}: the subject's best stale area, rules kept. */
	public static Outcome forceNow(MinecraftServer server) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return new Outcome(false, "the subject is not online");
		}
		return place(subject.get(), RandomSource.create(), false, Services.watch()::lastVisitDay, GameClock.day(server));
	}

	/** Finds the best stale area near the player and kills or strips it. {@code fake}: one bare tree. */
	public static Outcome place(ServerPlayer player, RandomSource random, boolean fake, VisitLookup visits, long today) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return new Outcome(false, "new scars only happen in the overworld");
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
		if (stale.isEmpty()) {
			return new Outcome(false, "no chunk the player left " + ModConfig.pacing().newScarAwayDays + "+ in-game days ago within " + range + " chunks");
		}
		if (fake) {
			return bareOneTree(level, stale, allowed, random);
		}
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
				groves.add(new Candidate(false, new BlockPos(x, ground, z), Integer.MIN_VALUE, neighbours + random.nextDouble()));
			}
			if (Terrain.isGrassy(biome) && ground >= terrain.seaLevel() + 4) {
				int around = (terrain.ground(x + 20, z) + terrain.ground(x - 20, z) + terrain.ground(x, z + 20) + terrain.ground(x, z - 20)) / 4;
				int prominence = ground - around;
				if (prominence >= 4) {
					int contour = ground - Math.clamp(prominence, 4, 10);
					hills.add(new Candidate(true, new BlockPos(x, ground, z), contour, prominence + neighbours + random.nextDouble()));
				}
			}
		}
		hills.sort(Comparator.comparingDouble(Candidate::score).reversed());
		groves.sort(Comparator.comparingDouble(Candidate::score).reversed());
		List<List<Candidate>> order = random.nextBoolean() ? List.of(hills, groves) : List.of(groves, hills);
		int inView = 0;
		for (List<Candidate> list : order) {
			for (Candidate candidate : list.subList(0, Math.min(4, list.size()))) {
				Outcome outcome = apply(level, candidate, allowed, config);
				if (outcome.placed()) {
					return outcome;
				}
				inView++;
			}
		}
		if (hills.isEmpty() && groves.isEmpty()) {
			return new Outcome(false, stale.size() + " stale chunk(s), but no hill or grove among them");
		}
		return new Outcome(false, "every candidate area (" + inView + ") was in view or empty");
	}

	private static Terrain terrain(ServerLevel level) {
		ScarContext context = ScarContext.current();
		ScarPlanner planner = context == null ? null : context.planner(level);
		return planner != null ? planner.terrain() : new LiveTerrain(level);
	}

	private static Outcome apply(ServerLevel level, Candidate candidate, Predicate<ChunkPos> allowed, WorldConfig config) {
		for (int radius = config.newScarRadius; radius >= 8; radius -= 4) {
			List<Edit> edits = candidate.dead() ? collectDead(level, candidate.center(), radius, candidate.contourY(), allowed)
					: collectBare(level, candidate.center(), radius, allowed);
			if (edits.isEmpty()) {
				return new Outcome(false, "nothing to change");
			}
			if (edits.size() > config.newScarMaxBlocks) {
				continue;
			}
			if (!commit(level, edits, CAUSE + (candidate.dead() ? "/dead" : "/bare"))) {
				return new Outcome(false, "in view");
			}
			BlockPos site = candidate.dead() ? siteOnGround(level, candidate.center()) : nearestTrunk(level, candidate.center(), radius);
			WorldSites.record(candidate.dead() ? SiteType.DEAD_MOUNTAIN : SiteType.BARE_GROVE, level.dimension(), site, radius, null);
			String what = candidate.dead() ? "dead hill" : "bare grove";
			A1016_02.LOGGER.info("[a1016] world: new scar ({}) at {} r={} ({} blocks)", what, site.toShortString(), radius, edits.size());
			return new Outcome(true, "new scar: " + what + " at " + site.toShortString() + " radius " + radius + " (" + edits.size() + " blocks)");
		}
		return new Outcome(false, "too large");
	}

	private static Outcome bareOneTree(ServerLevel level, List<ChunkPos> stale, Predicate<ChunkPos> allowed, RandomSource random) {
		List<ChunkPos> shuffled = new ArrayList<>(stale);
		Util.shuffle(shuffled, random);
		for (ChunkPos chunk : shuffled.subList(0, Math.min(6, shuffled.size()))) {
			level.getChunk(chunk.x(), chunk.z());
			BlockPos trunk = findTree(level, chunk);
			if (trunk == null) {
				continue;
			}
			List<Edit> edits = collectBare(level, trunk, 3, allowed);
			if (!edits.isEmpty() && commit(level, edits, CAUSE + "/tree")) {
				return new Outcome(true, "one bare tree at " + trunk.toShortString() + " (" + edits.size() + " blocks)");
			}
		}
		return new Outcome(false, "no tree out of view in a stale chunk");
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
				if (Vegetation.dies(state) || Vegetation.isSnowLayer(state) && y > ground + 1) {
					edits.put(pos, new Edit(pos, null));
					if (Vegetation.isLog(state)) {
						logs.add(pos);
					}
				}
			}
			BlockPos groundPos = new BlockPos(x, ground, z);
			if (Vegetation.isGrassGround(level.getBlockState(groundPos))) {
				edits.put(groundPos, new Edit(groundPos, Blocks.DIRT.defaultBlockState()));
			}
		});
		for (BlockPos log : logs) {
			for (BlockPos pos : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 3, 3))) {
				if (edits.containsKey(pos) || !allowed.test(ChunkPos.containing(pos)) || !level.hasChunkAt(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && Vegetation.isLeafy(level.getBlockState(pos.below()))) {
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
				if (Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && Vegetation.isLeafy(level.getBlockState(pos.below()))) {
					edits.add(new Edit(pos, null));
				}
			}
		});
		return edits;
	}

	@FunctionalInterface
	private interface ColumnVisitor {
		void visit(int x, int z, int top, int ground);
	}

	private static void forColumns(ServerLevel level, BlockPos center, int radius, Predicate<ChunkPos> allowed, ColumnVisitor visitor) {
		Set<Long> loaded = new HashSet<>();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				long dx = x - center.getX();
				long dz = z - center.getZ();
				if (dx * dx + dz * dz > (long) radius * radius) {
					continue;
				}
				ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
				if (!allowed.test(chunk)) {
					continue;
				}
				if (loaded.add(chunk.pack())) {
					level.getChunk(chunk.x(), chunk.z()); // unloaded areas are allowed: load them to read the blocks
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
				if (d >= bestDist || !level.hasChunk(x >> 4, z >> 4)) {
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
