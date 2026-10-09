package com.forzacode.a1016_02.world.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.CrossApi;
import com.forzacode.a1016_02.world.ScarKind;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ServerLevelData;

import org.jspecify.annotations.Nullable;

/**
 * Decides where old scars are, for one level. The world is cut into grid cells; a hash of (seed, salt, profile,
 * cell) decides whether a cell holds a scar, which one and where, and the noise terrain decides its exact shape.
 * Every decision is deterministic and cached, so any chunk can ask about any scar at any time and get the same
 * answer: a scar that spans many chunks is built piece by piece as they generate. Thread-safe.
 */
public final class ScarPlanner {
	/**
	 * How far a small scar's blocks may reach past its cell. Every chunk looks at the cells within this reach, and a
	 * plan that would reach farther is dropped ({@link #withinReach}), so no scar is ever built in part. A tunnel
	 * reaches about 220 blocks from its spot; a stair runs one block out per block down, so one starting above
	 * about Y 200 is dropped.
	 */
	public static final int POINT_REACH = 272;
	private static final int CACHE_LIMIT = 50_000;
	private static final int SPOTS_PER_CELL = 4;
	/** Mixed into a hilltop cross's hash for its glass roll. */
	private static final long GLASS_SALT = 0x61A55L;

	private enum Ground { OCEAN, LAND, HILL, NONE }

	private final ScarContext ctx;
	private final ServerLevel level;
	private final NoiseTerrain terrain;
	private final WorldConfig config;
	private final Map<Long, Optional<ScarPlan>> pointCells = new ConcurrentHashMap<>();
	private final Map<Long, Optional<ScarPlan>> areaCells = new ConcurrentHashMap<>();
	private volatile @Nullable BlockPos origin;
	/** Computed without a lock (deterministic, so a race only repeats work) and published once. */
	private final AtomicReference<Optional<ScarPlan>> hut = new AtomicReference<>();
	private final AtomicReference<Optional<BlockPos>> corePyramid = new AtomicReference<>();
	private final AtomicBoolean warming = new AtomicBoolean();

	ScarPlanner(ScarContext ctx, ServerLevel level) {
		this.ctx = ctx;
		this.level = level;
		this.terrain = new NoiseTerrain(level);
		this.config = ctx.config();
	}

	public NoiseTerrain terrain() {
		return terrain;
	}

	// --- worldgen entry point ---

	/** Builds the parts of every scar that fall in {@code chunk} (and kills or strips its neighbours' columns). */
	public boolean decorate(WorldGenLevel genLevel, ChunkPos chunk) {
		boolean changed = false;
		List<ScarPlan> areas = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos target = new ChunkPos(chunk.x() + dx, chunk.z() + dz);
				areaPlanAt(target.getMiddleBlockX(), target.getMiddleBlockZ()).filter(p -> !areas.contains(p) && farFromSpawn(p.footprint()))
						.ifPresent(areas::add);
			}
		}
		if (!areas.isEmpty()) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					ChunkPos target = new ChunkPos(chunk.x() + dx, chunk.z() + dz);
					if (!Blueprint.canWrite(genLevel, target.getMiddleBlockPosition(genLevel.getMinY() + 1))) {
						continue;
					}
					List<AreaScars.Area> hits = new ArrayList<>();
					for (ScarPlan plan : areas) {
						if (plan.area() != null && plan.area().touches(target, 0)) {
							hits.add(plan.area());
						}
					}
					if (hits.isEmpty()) {
						continue;
					}
					boolean own = dx == 0 && dz == 0;
					List<BlockPos> trunks = AreaScars.clean(genLevel, target, hits, own);
					changed = true;
					if (own) {
						recordAreaSites(areas, chunk, trunks);
					}
				}
			}
		}
		for (ScarPlan plan : pointPlansTouching(chunk)) {
			// Checked again here: a plan cached before the real spawn was set only knew the spawn search origin.
			if (farFromSpawn(plan.footprint())) {
				applyPlan(genLevel, plan, chunk);
				changed = true;
			}
		}
		Optional<ScarPlan> ruinedHut = ruinedHut();
		if (ruinedHut.isPresent() && ruinedHut.get().touches(chunk)) {
			applyPlan(genLevel, ruinedHut.get(), chunk);
			changed = true;
		}
		for (ScarPlan plan : areas) {
			if (plan.blueprint() != null && plan.blueprint().intersects(chunk)) {
				plan.blueprint().applyInChunk(genLevel, chunk);
				changed = true;
			}
		}
		return changed;
	}

	private void applyPlan(WorldGenLevel genLevel, ScarPlan plan, ChunkPos chunk) {
		if (plan.blueprint() != null) {
			plan.blueprint().applyInChunk(genLevel, chunk);
		}
		if (plan.kind() == ScarKind.OCEAN_PYRAMID && plan.anchor().equals(corePyramid().orElse(null))) {
			Builds.pyramidPocket(plan.anchor()).applyInChunk(genLevel, chunk);
		}
		// A hut planned after a profile reroll is not the world's recorded one: build it, but record no second hut.
		if (plan.kind() == ScarKind.RUINED_HUT && WorldSites.isOtherSingle(WorldSites.HUT, GlobalPos.of(level.dimension(), plan.anchor()))) {
			return;
		}
		for (ScarPlan.SiteMark mark : plan.sites()) {
			if (chunk.contains(mark.pos()) && !mark.nearestTree()) {
				WorldSites.record(mark.type(), level.dimension(), mark.pos(), mark.size(), mark.interior());
			}
		}
	}

	private void recordAreaSites(List<ScarPlan> areas, ChunkPos chunk, List<BlockPos> trunks) {
		for (ScarPlan.SiteMark mark : areaSitesFor(areas, chunk, trunks)) {
			WorldSites.record(mark.type(), level.dimension(), mark.pos(), mark.size(), mark.interior());
		}
	}

	/**
	 * The large scars' sites to record while {@code chunk} is decorated: every mark inside the chunk (a bare grove's
	 * at its nearest trunk), and a dead mountain's own site as soon as any chunk of the mountain is decorated, so it
	 * exists before that chunk spawns its first animals. Recording is idempotent, so later chunks change nothing.
	 */
	public static List<ScarPlan.SiteMark> areaSitesFor(List<ScarPlan> areas, ChunkPos chunk, List<BlockPos> trunks) {
		List<ScarPlan.SiteMark> marks = new ArrayList<>();
		for (ScarPlan plan : areas) {
			boolean ofThisMountain = plan.kind() == ScarKind.DEAD_MOUNTAIN && plan.area() != null && plan.area().touches(chunk, 0);
			for (ScarPlan.SiteMark mark : plan.sites()) {
				if (ofThisMountain && mark.type() == SiteType.DEAD_MOUNTAIN) {
					marks.add(mark);
					continue;
				}
				if (!chunk.contains(mark.pos())) {
					continue;
				}
				BlockPos pos = mark.pos();
				if (mark.nearestTree() && !trunks.isEmpty()) {
					pos = trunks.stream().min(Comparator.comparingDouble(t -> t.distSqr(mark.pos()))).orElse(pos);
				}
				marks.add(new ScarPlan.SiteMark(mark.type(), pos, mark.size(), mark.interior(), mark.nearestTree()));
			}
		}
		return marks;
	}

	// --- lookups ---

	/** Small scars whose blocks may fall in this chunk. */
	public List<ScarPlan> pointPlansTouching(ChunkPos chunk) {
		int cell = config.pointCellBlocks;
		List<ScarPlan> plans = new ArrayList<>(2);
		for (int cx = Math.floorDiv(chunk.getMinBlockX() - POINT_REACH, cell); cx <= Math.floorDiv(chunk.getMaxBlockX() + POINT_REACH, cell); cx++) {
			for (int cz = Math.floorDiv(chunk.getMinBlockZ() - POINT_REACH, cell); cz <= Math.floorDiv(chunk.getMaxBlockZ() + POINT_REACH, cell); cz++) {
				pointPlan(cx, cz).filter(p -> p.touches(chunk)).ifPresent(plans::add);
			}
		}
		return plans;
	}

	/** True if a chunk looks at this small-scar cell ({@link #pointPlansTouching}). */
	public static boolean seesCell(ChunkPos chunk, int cx, int cz, int cell) {
		return cx >= Math.floorDiv(chunk.getMinBlockX() - POINT_REACH, cell) && cx <= Math.floorDiv(chunk.getMaxBlockX() + POINT_REACH, cell)
				&& cz >= Math.floorDiv(chunk.getMinBlockZ() - POINT_REACH, cell) && cz <= Math.floorDiv(chunk.getMaxBlockZ() + POINT_REACH, cell);
	}

	/** True if every block of the footprint lies within {@link #POINT_REACH} of the cell, so every chunk it touches sees it. */
	public static boolean withinReach(BoundingBox footprint, int cx, int cz, int cell) {
		return footprint.minX() >= cx * cell - POINT_REACH && footprint.maxX() <= cx * cell + cell - 1 + POINT_REACH
				&& footprint.minZ() >= cz * cell - POINT_REACH && footprint.maxZ() <= cz * cell + cell - 1 + POINT_REACH;
	}

	/** The large scar of the cell containing (x, z), if any. Large scars never leave their cell. */
	public Optional<ScarPlan> areaPlanAt(int x, int z) {
		int cell = config.areaCellBlocks;
		return areaPlan(Math.floorDiv(x, cell), Math.floorDiv(z, cell));
	}

	public Optional<ScarPlan> pointPlan(int cx, int cz) {
		long key = ChunkPos.pack(cx, cz);
		Optional<ScarPlan> plan = pointCells.get(key);
		if (plan == null) {
			plan = safely(() -> computePoint(cx, cz));
			trim(pointCells);
			Optional<ScarPlan> raced = pointCells.putIfAbsent(key, plan);
			plan = raced != null ? raced : plan;
		}
		return plan;
	}

	public Optional<ScarPlan> areaPlan(int ax, int az) {
		long key = ChunkPos.pack(ax, az);
		Optional<ScarPlan> plan = areaCells.get(key);
		if (plan == null) {
			plan = safely(() -> computeArea(ax, az));
			trim(areaCells);
			Optional<ScarPlan> raced = areaCells.putIfAbsent(key, plan);
			plan = raced != null ? raced : plan;
		}
		return plan;
	}

	private static Optional<ScarPlan> safely(java.util.function.Supplier<Optional<ScarPlan>> compute) {
		try {
			return compute.get();
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] world: scar planning failed", e);
			return Optional.empty();
		}
	}

	private static void trim(Map<Long, ?> cache) {
		if (cache.size() > CACHE_LIMIT) {
			cache.clear();
		}
	}

	/**
	 * Every planned scar of a kind whose anchor lies within {@code radius} of (x, z), nearest first. Plans are
	 * computed from the noise, so this also finds scars in chunks that are not generated yet.
	 */
	public List<ScarPlan> plansNear(ScarKind kind, int x, int z, int radius) {
		List<ScarPlan> found = new ArrayList<>();
		if (kind == ScarKind.RUINED_HUT) {
			ruinedHut().ifPresent(found::add);
		} else {
			int cell = kind.area() ? config.areaCellBlocks : config.pointCellBlocks;
			for (int cx = Math.floorDiv(x - radius, cell); cx <= Math.floorDiv(x + radius, cell); cx++) {
				for (int cz = Math.floorDiv(z - radius, cell); cz <= Math.floorDiv(z + radius, cell); cz++) {
					(kind.area() ? areaPlan(cx, cz) : pointPlan(cx, cz)).filter(p -> p.kind() == kind).ifPresent(found::add);
				}
			}
		}
		found.removeIf(p -> horizontalDistSqr(p.anchor(), x, z) > (long) radius * radius);
		found.sort(Comparator.comparingLong(p -> horizontalDistSqr(p.anchor(), x, z)));
		return found;
	}

	// --- spawn ---

	/** Where the server starts its spawn search (the real spawn is within about 90 blocks of it). */
	public BlockPos origin() {
		BlockPos known = origin;
		if (known == null) {
			ChunkPos chunk = terrain.spawnOrigin();
			known = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
			origin = known;
		}
		return known;
	}

	/** True if the box keeps the old-scar distance from the spawn search origin and from the real spawn once set. */
	public boolean farFromSpawn(BoundingBox box) {
		int min = ctx.minFromSpawn();
		if (distanceToBox(origin(), box) < min) {
			return false;
		}
		if (level.getLevelData() instanceof ServerLevelData data && data.isInitialized()) {
			return distanceToBox(level.getRespawnData().pos(), box) >= min;
		}
		return true;
	}

	private static double distanceToBox(BlockPos p, BoundingBox box) {
		double dx = Math.max(0, Math.max(box.minX() - p.getX(), p.getX() - box.maxX()));
		double dz = Math.max(0, Math.max(box.minZ() - p.getZ(), p.getZ() - box.maxZ()));
		return Math.sqrt(dx * dx + dz * dz);
	}

	// --- the one ruined hut ---

	/** The one ruined cobble hut of the world, 300 to 800 blocks from spawn. */
	public Optional<ScarPlan> ruinedHut() {
		Optional<ScarPlan> known = hut.get();
		if (known == null) {
			hut.compareAndSet(null, safely(this::computeHut));
			known = hut.get();
		}
		return known;
	}

	private Optional<ScarPlan> computeHut() {
		BlockPos center = origin();
		int margin = 120;
		int minR = Math.max(ctx.minFromSpawn(), config.ruinedHutMinBlocks) + margin;
		int maxR = Math.max(minR + 10, config.ruinedHutMaxBlocks - margin);
		for (int attempt = 0; attempt < 96; attempt++) {
			long h = Hash.of(ctx.base(), 7, attempt);
			double angle = Hash.unit(h) * Math.PI * 2;
			int r = Hash.between(h ^ 1, minR, maxR);
			int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
			if (classify(x, z) != Ground.LAND) {
				continue;
			}
			Holder<Biome> biome = terrain.surfaceBiome(x, z);
			int floor = terrain.ground(x, z);
			if (Terrain.isDenseWoods(biome) || !flat(x, z, 4, floor, 2)) {
				continue;
			}
			Builds.Build build = Builds.ruinedHut(x, floor, z, horizontal(h ^ 2), h);
			return Optional.of(plan(ScarKind.RUINED_HUT, build));
		}
		A1016_02.LOGGER.warn("[a1016] world: no place for the ruined hut");
		return Optional.empty();
	}

	// --- the core pyramid ---

	/** The core of the largest ocean pyramid within the configured radius of spawn, if there is one. */
	public Optional<BlockPos> corePyramid() {
		Optional<BlockPos> known = corePyramid.get();
		if (known == null) {
			corePyramid.compareAndSet(null, computeCorePyramid());
			known = corePyramid.get();
		}
		return known;
	}

	/**
	 * Works out the one-off answers (the hut, the core pyramid: about 290 cells) on a background thread, so the
	 * worldgen thread that first needs them usually finds them ready, and records their sites right away
	 * ({@link #recordOneOffSites}). Nothing waits on it.
	 */
	public void warmUp() {
		if (!warming.compareAndSet(false, true)) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				ruinedHut();
				corePyramid();
				MinecraftServer server = level.getServer();
				server.execute(this::recordOneOffSites);
			} catch (RuntimeException e) {
				A1016_02.LOGGER.error("[a1016] world: scar warm-up failed", e);
			}
		}, Util.backgroundExecutor());
	}

	/**
	 * Records the hut's and the core pyramid's sites as soon as they are planned (server start), not only when
	 * their chunks generate, so lore can plan around them early. Once per world: where each was recorded goes into
	 * {@link WorldData}, so a later start or a profile reroll (which moves the plans) records nothing new, and the
	 * hut's chunk records no second hut when it generates ({@link WorldSites#isOtherSingle}; the recorded positions
	 * reach worldgen at server start, before any chunk generates, through {@link WorldSites#loadSingles}).
	 * Generating the chunk of the recorded one records nothing twice either ({@link WorldSites#record} skips it).
	 * Server thread; skipped if this planner's snapshot is no longer current.
	 */
	public void recordOneOffSites() {
		MinecraftServer server = level.getServer();
		if (!server.isRunning() || ScarContext.current() != ctx) {
			return;
		}
		WorldData data = WorldData.get(server);
		if (data.single(WorldSites.HUT).isEmpty()) {
			ruinedHut().ifPresent(plan -> recordSingle(data, WorldSites.HUT, plan));
		}
		if (data.single(WorldSites.CORE_PYRAMID).isEmpty()) {
			corePyramid()
					.flatMap(anchor -> plansNear(ScarKind.OCEAN_PYRAMID, anchor.getX(), anchor.getZ(), 1).stream().filter(p -> p.anchor().equals(anchor)).findFirst())
					.filter(plan -> farFromSpawn(plan.footprint()))
					.ifPresent(plan -> recordSingle(data, WorldSites.CORE_PYRAMID, plan));
		}
		WorldSites.setSingles(data.singles());
	}

	private void recordSingle(WorldData data, String key, ScarPlan plan) {
		recordSites(plan);
		data.putSingle(key, GlobalPos.of(level.dimension(), plan.anchor()));
	}

	private void recordSites(ScarPlan plan) {
		for (ScarPlan.SiteMark mark : plan.sites()) {
			if (!mark.nearestTree()) {
				WorldSites.record(mark.type(), level.dimension(), mark.pos(), mark.size(), mark.interior());
			}
		}
	}

	private Optional<BlockPos> computeCorePyramid() {
		BlockPos center = origin();
		int radius = config.corePyramidRadius;
		ScarPlan best = null;
		for (ScarPlan plan : plansNear(ScarKind.OCEAN_PYRAMID, center.getX(), center.getZ(), radius)) {
			if (best == null || plan.size() > best.size()) {
				best = plan; // plansNear is nearest first, so ties go to the nearest
			}
		}
		return best == null ? Optional.empty() : Optional.of(best.anchor());
	}

	// --- small scars ---

	/** True if the small-scar cell passes the density roll (it may still hold nothing if the terrain does not fit). */
	public boolean pointCellRolled(int cx, int cz) {
		return Hash.unit(Hash.of(ctx.base(), 1, cx, cz)) < config.pointChance(ctx.density().ordinal());
	}

	/** True if the large-scar cell passes the density roll. */
	public boolean areaCellRolled(int ax, int az) {
		return Hash.unit(Hash.of(ctx.base(), 2, ax, az)) < config.areaChance(ctx.density().ordinal());
	}

	private Optional<ScarPlan> computePoint(int cx, int cz) {
		long h = Hash.of(ctx.base(), 1, cx, cz);
		if (!pointCellRolled(cx, cz)) {
			return Optional.empty();
		}
		int cell = config.pointCellBlocks;
		Optional<ScarPlan> hutPlan = ruinedHut();
		if (hutPlan.isPresent() && Math.floorDiv(hutPlan.get().anchor().getX(), cell) == cx && Math.floorDiv(hutPlan.get().anchor().getZ(), cell) == cz) {
			return Optional.empty();
		}
		// Up to four spots in the cell, one weighted pick each: a kind the terrain does not fit tries again
		// elsewhere instead of handing its turn to the kinds that always fit (lights, towers).
		for (int spot = 0; spot < SPOTS_PER_CELL; spot++) {
			long hs = Hash.of(h, spot);
			int x = cx * cell + 16 + Hash.below(hs ^ 1, cell - 32);
			int z = cz * cell + 16 + Hash.below(hs ^ 2, cell - 32);
			if (!farFromSpawn(new BoundingBox(new BlockPos(x, 0, z)))) {
				continue;
			}
			Ground ground = classify(x, z);
			List<ScarKind> options = switch (ground) {
				case OCEAN -> List.of(ScarKind.OCEAN_PYRAMID, ScarKind.LONE_LIGHT);
				case LAND -> List.of(ScarKind.ABANDONED_BUILD, ScarKind.EMPTIED_HOUSE, ScarKind.PANIC_TOWER, ScarKind.CROSS, ScarKind.LONE_LIGHT,
						ScarKind.STAIR, ScarKind.CUT, ScarKind.TUNNEL);
				case HILL -> List.of(ScarKind.CUT, ScarKind.TUNNEL, ScarKind.CROSS, ScarKind.LONE_LIGHT, ScarKind.STAIR, ScarKind.PANIC_TOWER);
				case NONE -> List.of();
			};
			if (options.isEmpty()) {
				continue;
			}
			ScarKind kind = pickWeighted(options, hs ^ 3);
			ScarPlan plan = planPoint(kind, ground, x, z, Hash.of(hs, kind.ordinal()));
			if (plan != null && withinReach(plan.footprint(), cx, cz, cell) && farFromSpawn(plan.footprint())) {
				return Optional.of(plan);
			}
		}
		return Optional.empty();
	}

	/** Weight of a kind in this world: its base weight, boosted when it matches one of the world's habits. */
	public double weight(ScarKind kind) {
		return config.baseWeight(kind) * (kind.matches(ctx.habits()) ? config.habitBoost : 1.0);
	}

	private ScarKind pickWeighted(List<ScarKind> kinds, long h) {
		double total = 0;
		for (ScarKind kind : kinds) {
			total += weight(kind);
		}
		double roll = Hash.unit(h) * total;
		for (ScarKind kind : kinds) {
			roll -= weight(kind);
			if (roll < 0) {
				return kind;
			}
		}
		return kinds.get(kinds.size() - 1);
	}

	private Ground classify(int x, int z) {
		int ground = terrain.ground(x, z);
		int surface = terrain.surface(x, z);
		Holder<Biome> biome = terrain.biome(x, ground, z);
		if (surface > ground) {
			return Terrain.isOcean(biome) ? Ground.OCEAN : Ground.NONE;
		}
		if (Terrain.isWetland(biome) || ground <= terrain.seaLevel()) {
			return Ground.NONE;
		}
		int around = (terrain.ground(x + 24, z) + terrain.ground(x - 24, z) + terrain.ground(x, z + 24) + terrain.ground(x, z - 24)) / 4;
		return ground - around >= 8 ? Ground.HILL : Ground.LAND;
	}

	private @Nullable ScarPlan planPoint(ScarKind kind, Ground ground, int x, int z, long h) {
		return switch (kind) {
			case OCEAN_PYRAMID -> planPyramid(x, z, h);
			case LONE_LIGHT -> planLight(ground, x, z, h);
			case CROSS -> hilltopCross(terrain, x, z, h, glassChance());
			case ABANDONED_BUILD, EMPTIED_HOUSE -> {
				int half = kind == ScarKind.ABANDONED_BUILD ? 3 : 4;
				Holder<Biome> biome = terrain.surfaceBiome(x, z);
				int floor = terrain.ground(x, z);
				if (Terrain.isDenseWoods(biome) || !flat(x, z, half, floor, 3)) {
					yield null;
				}
				Builds.Wood wood = Builds.Wood.of(biome);
				Builds.Build build = kind == ScarKind.ABANDONED_BUILD ? Builds.abandonedBuild(x, floor, z, horizontal(h), wood, h)
						: Builds.emptiedHouse(x, floor, z, horizontal(h), wood, h);
				yield plan(kind, build);
			}
			case PANIC_TOWER -> {
				if (terrain.wet(x, z)) {
					yield null;
				}
				yield plan(kind, Builds.panicTower(x, terrain.ground(x, z), z, h));
			}
			case STAIR -> {
				int start = Hash.below(h, 4);
				for (int n = 0; n < 4; n++) {
					Direction dir = Direction.from2DDataValue((start + n) % 4);
					Carves.Carve carve = Carves.stair(terrain, x, z, dir, Carves.PathCheck.SOLID);
					if (carve != null) {
						yield new ScarPlan(kind, carve.site(), carve.size(), carve.blueprint(), null,
								List.of(ScarPlan.SiteMark.of(SiteType.STAIR_BOTTOM, carve.site(), carve.size())));
					}
				}
				yield null;
			}
			case CUT, TUNNEL -> planCarve(kind, x, z, h);
			default -> null;
		};
	}

	private @Nullable ScarPlan planPyramid(int x, int z, long h) {
		int floor = terrain.ground(x, z);
		int water = terrain.surface(x, z) - floor;
		if (water < 1 || !Terrain.isOcean(terrain.biome(x, floor, z))) {
			return null;
		}
		int size = water <= 7 ? water + 1 + Hash.below(h, 2) : Hash.between(h, 3, 6);
		size = Math.clamp(size, 3, 9);
		int e = size - 1;
		for (int[] c : new int[][] {{-e, -e}, {e, -e}, {-e, e}, {e, e}}) {
			int cx = x + c[0];
			int cz = z + c[1];
			if (!terrain.wet(cx, cz) || Math.abs(terrain.ground(cx, cz) - floor) > 3) {
				return null;
			}
		}
		return plan(ScarKind.OCEAN_PYRAMID, Builds.pyramid(x, floor, z, size));
	}

	private @Nullable ScarPlan planLight(Ground ground, int x, int z, long h) {
		if (ground == Ground.OCEAN) {
			int floor = terrain.ground(x, z);
			int water = terrain.surface(x, z);
			if (water - floor < 2) {
				return null;
			}
			return plan(ScarKind.LONE_LIGHT, Builds.lonelight(Builds.LightKind.OCEAN_TORCH, x, water, z));
		}
		if (terrain.wet(x, z)) {
			return null;
		}
		int surface = terrain.ground(x, z);
		if (Hash.unit(h) < 0.5) {
			int top = Math.min(surface - 16, terrain.seaLevel() - 30);
			for (int y = top; y > terrain.minY() + 8; y--) {
				if (terrain.block(x, y, z).isAir() && terrain.block(x, y + 1, z).isAir()) {
					var floorState = terrain.block(x, y - 1, z);
					if (!floorState.isAir() && floorState.getFluidState().isEmpty()) {
						return plan(ScarKind.LONE_LIGHT, Builds.lonelight(Builds.LightKind.CAVE_TORCH, x, y - 1, z));
					}
				}
			}
		}
		return plan(ScarKind.LONE_LIGHT, Builds.lonelight(Builds.LightKind.GLOWSTONE, x, surface, z));
	}

	private @Nullable ScarPlan planCarve(ScarKind kind, int x, int z, long h) {
		int[] top = climb(x, z, 16, 6);
		int peak = terrain.ground(top[0], top[1]);
		if (peak < terrain.seaLevel() + 8 || terrain.wet(top[0], top[1])) {
			return null;
		}
		Direction first = Hash.unit(h ^ 5) < 0.5 ? Direction.EAST : Direction.SOUTH;
		int depth = kind == ScarKind.CUT ? Hash.between(h ^ 6, 6, 14) : Hash.between(h ^ 8, 10, 28);
		for (int tryDepth : new int[] {depth, depth / 2 + 3}) {
			for (Direction axis : new Direction[] {first, first.getClockWise()}) {
				ScarPlan plan = carvePlan(kind, top, peak, axis, tryDepth, h);
				if (plan != null) {
					return plan;
				}
			}
		}
		return null;
	}

	private @Nullable ScarPlan carvePlan(ScarKind kind, int[] top, int peak, Direction axis, int depth, long h) {
		Carves.Carve carve;
		if (kind == ScarKind.CUT) {
			carve = Carves.cut(terrain, top[0], top[1], axis, depth, Hash.between(h ^ 7, 3, 5), 64);
		} else {
			int floor = Math.max(terrain.seaLevel() + 2, peak - depth);
			carve = Carves.tunnel(terrain, top[0], top[1], axis, floor, 120);
		}
		if (carve == null) {
			return null;
		}
		List<ScarPlan.SiteMark> sites = new ArrayList<>();
		sites.add(ScarPlan.SiteMark.of(SiteType.CUT, carve.site(), carve.size()));
		Blueprint bp = carve.blueprint();
		boolean mourner = ctx.habits().contains(Habit.MOURNER);
		if (kind == ScarKind.TUNNEL && Hash.unit(h ^ 9) < (mourner ? 0.5 : 0.2)) {
			BlockPos mouth = Hash.unit(h ^ 10) < 0.5 ? carve.endA() : carve.endB();
			Direction side = axis.getCounterClockWise();
			int cx = mouth.getX() + side.getStepX() * 2;
			int cz = mouth.getZ() + side.getStepZ() * 2;
			if (!terrain.wet(cx, cz)) {
				Builds.Build cross = Builds.cross(cx, terrain.ground(cx, cz), cz, axis, Builds.Wood.of(terrain.surfaceBiome(cx, cz)), h ^ 11);
				cross.blueprint().ops().forEach(bp::add);
				sites.add(ScarPlan.SiteMark.of(SiteType.CROSS, cross.site(), cross.size()));
			}
		}
		return new ScarPlan(kind, carve.site(), carve.size(), bp, null, sites);
	}

	// --- large scars ---

	private Optional<ScarPlan> computeArea(int ax, int az) {
		long h = Hash.of(ctx.base(), 2, ax, az);
		if (!areaCellRolled(ax, az)) {
			return Optional.empty();
		}
		int cell = config.areaCellBlocks;
		int margin = Math.max(config.bareForestMaxRadius, config.deadMountainMaxRadius) + 8;
		int span = cell - 2 * margin;
		if (span <= 0) {
			return Optional.empty();
		}
		List<int[]> wooded = new ArrayList<>();
		List<int[]> dappled = new ArrayList<>();
		List<int[]> hills = new ArrayList<>();
		int grid = 5;
		for (int i = 0; i < grid; i++) {
			for (int j = 0; j < grid; j++) {
				int x = ax * cell + margin + span * i / (grid - 1);
				int z = az * cell + margin + span * j / (grid - 1);
				if (terrain.wet(x, z)) {
					continue;
				}
				int y = terrain.ground(x, z);
				Holder<Biome> biome = terrain.biome(x, y, z);
				if (biome.is(Biomes.DAPPLED_FOREST)) {
					dappled.add(new int[] {x, z, y});
				}
				if (Terrain.isWooded(biome)) {
					wooded.add(new int[] {x, z, y});
				}
				if (y >= terrain.seaLevel() + 16 && Terrain.isGrassy(biome)) {
					hills.add(new int[] {x, z, y});
				}
			}
		}
		List<ScarKind> options = new ArrayList<>();
		if (!wooded.isEmpty()) {
			options.add(ScarKind.BARE_FOREST);
		}
		if (!hills.isEmpty()) {
			options.add(ScarKind.DEAD_MOUNTAIN);
		}
		if (options.isEmpty()) {
			return Optional.empty();
		}
		ScarKind kind = pickWeighted(options, h ^ 1);
		ScarPlan plan = kind == ScarKind.BARE_FOREST ? planBareForest(dappled.isEmpty() ? wooded : dappled, !dappled.isEmpty(), h)
				: planDeadMountain(hills, ax * cell + margin, az * cell + margin, span, h);
		if (plan == null || !farFromSpawn(plan.footprint())) {
			return Optional.empty();
		}
		return Optional.of(plan);
	}

	private ScarPlan planBareForest(List<int[]> points, boolean dappled, long h) {
		int[] c = points.get(Hash.below(h ^ 2, points.size()));
		int min = config.bareForestMinRadius;
		int max = config.bareForestMaxRadius;
		int radius = dappled ? Hash.between(h ^ 3, (min + max) / 2, max) : Hash.between(h ^ 3, min, max);
		AreaScars.Area area = new AreaScars.Area(ScarKind.BARE_FOREST, c[0], c[1], radius, Integer.MIN_VALUE, dappled);
		BlockPos center = new BlockPos(c[0], c[2] + 1, c[1]);
		ScarPlan.SiteMark grove = new ScarPlan.SiteMark(SiteType.BARE_GROVE, center, radius, null, true);
		return new ScarPlan(ScarKind.BARE_FOREST, center, radius, null, area, List.of(grove));
	}

	private @Nullable ScarPlan planDeadMountain(List<int[]> hills, int minX, int minZ, int span, long h) {
		int[] start = hills.stream().max(Comparator.comparingInt(p -> p[2])).orElseThrow();
		int[] top = climb(start[0], start[1], 12, 8);
		int x = Math.clamp(top[0], minX, minX + span);
		int z = Math.clamp(top[1], minZ, minZ + span);
		int peak = terrain.ground(x, z);
		if (peak < terrain.seaLevel() + 16 || terrain.wet(x, z) || !Terrain.isGrassy(terrain.biome(x, peak, z))) {
			return null;
		}
		int radius = Hash.between(h ^ 4, config.deadMountainMinRadius, config.deadMountainMaxRadius);
		int depth = Math.clamp((int) ((peak - terrain.seaLevel()) * 0.55), 10, 40);
		AreaScars.Area area = new AreaScars.Area(ScarKind.DEAD_MOUNTAIN, x, z, radius, peak - depth, false);
		List<ScarPlan.SiteMark> sites = new ArrayList<>();
		BlockPos center = new BlockPos(x, peak + 1, z);
		sites.add(ScarPlan.SiteMark.of(SiteType.DEAD_MOUNTAIN, center, radius));
		Blueprint extras = null;
		boolean mourner = ctx.habits().contains(Habit.MOURNER);
		if (Hash.unit(h ^ 5) < (mourner ? 0.6 : 0.25)) {
			Builds.Build cross = Builds.cross(x, peak, z, horizontal(h ^ 6), Builds.Wood.of(terrain.biome(x, peak, z)), h ^ 7);
			extras = cross.blueprint();
			sites.add(ScarPlan.SiteMark.of(SiteType.CROSS, cross.site(), cross.size()));
		}
		return new ScarPlan(ScarKind.DEAD_MOUNTAIN, center, radius, extras, area, sites);
	}

	// --- the hilltop cross ---

	/** Chance that this world's hilltop cross is a glass memorial instead of his (D-051): higher in Mourner worlds. */
	public double glassChance() {
		return config.glassCrossChance(ctx.habits());
	}

	/**
	 * A cross alone on the top nearest (x, z): with {@code glassChance} a glass memorial left by others (D-051),
	 * else his cobblestone or wood cross. Only this cross can be glass; the ones on dead mountains and at tunnel
	 * mouths are always his. Null on wet ground. Static, so tests can plan on any terrain.
	 */
	public static @Nullable ScarPlan hilltopCross(Terrain terrain, int x, int z, long h, double glassChance) {
		int[] top = climb(terrain, x, z, 8, 5);
		if (terrain.wet(top[0], top[1])) {
			return null;
		}
		int y = terrain.ground(top[0], top[1]);
		Builds.Build build = Hash.unit(h ^ GLASS_SALT) < glassChance ? Builds.glassCross(top[0], y, top[1], horizontal(h), h)
				: Builds.cross(top[0], y, top[1], horizontal(h), Builds.Wood.of(terrain.surfaceBiome(top[0], top[1])), h);
		return plan(ScarKind.CROSS, build);
	}

	/** True if the plan is a glass memorial cross (D-051). */
	public static boolean isGlassMemorial(ScarPlan plan) {
		return plan.kind() == ScarKind.CROSS && CrossApi.isGlassSize(plan.size());
	}

	// --- helpers ---

	private static ScarPlan plan(ScarKind kind, Builds.Build build) {
		ScarPlan.SiteMark mark = new ScarPlan.SiteMark(kind.site(), build.site(), build.size(),
				kind == ScarKind.ABANDONED_BUILD || kind == ScarKind.EMPTIED_HOUSE || kind == ScarKind.RUINED_HUT ? build.interior() : null, false);
		return new ScarPlan(kind, build.site(), build.size(), build.blueprint(), null, List.of(mark));
	}

	/** Walks uphill from (x, z) in steps of {@code stride}, halving it when no neighbour is higher. */
	int[] climb(int x, int z, int stride, int steps) {
		return climb(terrain, x, z, stride, steps);
	}

	private static int[] climb(Terrain terrain, int x, int z, int stride, int steps) {
		int bx = x;
		int bz = z;
		int by = terrain.ground(x, z);
		for (int s = 0; s < steps && stride >= 2; s++) {
			int nx = bx;
			int nz = bz;
			int ny = by;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int tx = bx + dir.getStepX() * stride;
				int tz = bz + dir.getStepZ() * stride;
				int ty = terrain.ground(tx, tz);
				if (ty > ny) {
					nx = tx;
					nz = tz;
					ny = ty;
				}
			}
			if (ny == by) {
				stride /= 2;
			} else {
				bx = nx;
				bz = nz;
				by = ny;
			}
		}
		return new int[] {bx, bz, by};
	}

	private boolean flat(int x, int z, int half, int floor, int tolerance) {
		for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
			int cx = x + c[0];
			int cz = z + c[1];
			if (terrain.wet(cx, cz) || Math.abs(terrain.ground(cx, cz) - floor) > tolerance) {
				return false;
			}
		}
		return true;
	}

	private static Direction horizontal(long h) {
		return Direction.from2DDataValue(Hash.below(h, 4));
	}

	private static long horizontalDistSqr(BlockPos pos, int x, int z) {
		long dx = pos.getX() - x;
		long dz = pos.getZ() - z;
		return dx * dx + dz * dz;
	}
}
