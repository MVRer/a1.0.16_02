package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s19.LineFeature;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Box;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.From;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The lines of a live world, found from worldgen's decisions ({@link Decided}) and 19's segments, by square
 * ({@value #REGION} blocks: the per-region index): each square's lines are found once a run (the places round it decided
 * then, if nobody had asked), each place's lines once. A line fixed in the world ({@link Plans#line}) is the one used,
 * whatever the config or the spawn say now (R12-2). Any thread; cleared at the server's stop.
 */
public final class CrumbIndex {
	/** The index's squares (blocks). */
	public static final int REGION = 256;
	/** How far round a place's sign its line's obstacles are decided (its longest line and a box's reach). */
	static final int OBSTACLES = 112;

	private record Key(long world, long region) {
	}

	private static final Map<Key, CompletableFuture<List<Line>>> REGIONS = new ConcurrentHashMap<>();
	private static final Map<String, List<Line>> PLACES = new ConcurrentHashMap<>();

	private CrumbIndex() {
	}

	/** The lines whose crumbs may stand in this chunk. */
	public static List<Line> near(ServerLevel level, ChunkPos chunk, Snapshot snapshot, Sc32Config c) {
		int rx = Math.floorDiv(chunk.getMinBlockX(), REGION);
		int rz = Math.floorDiv(chunk.getMinBlockZ(), REGION);
		List<Line> lines = region(level, rx, rz, snapshot, c);
		if (lines.isEmpty()) {
			return lines;
		}
		List<Line> out = new ArrayList<>(2);
		for (Line line : lines) {
			if (line.reaches(chunk, CrumbLines.FAR_ACROSS + 6)) {
				out.add(line);
			}
		}
		return out;
	}

	private static List<Line> region(ServerLevel level, int rx, int rz, Snapshot snapshot, Sc32Config c) {
		Key key = new Key(snapshot.seed() * 31 + snapshot.salt(), ChunkPos.pack(rx, rz));
		CompletableFuture<List<Line>> mine = new CompletableFuture<>();
		CompletableFuture<List<Line>> before = REGIONS.putIfAbsent(key, mine);
		if (before != null) {
			return before.join();
		}
		try {
			List<Line> found = find(level, rx, rz, snapshot, c);
			mine.complete(found);
			return found;
		} catch (RuntimeException | Error e) {
			REGIONS.remove(key, mine);
			mine.completeExceptionally(e);
			throw e;
		}
	}

	private static List<Line> find(ServerLevel level, int rx, int rz, Snapshot snapshot, Sc32Config c) {
		int x0 = rx * REGION;
		int z0 = rz * REGION;
		int mid = REGION / 2;
		int reach = mid + c.lineMax + OBSTACLES + 16;
		List<Line> out = new ArrayList<>();
		Terrain terrain = Terrains.noise(level);
		for (Decided.Decision d : Decided.around(level, x0 + mid, z0 + mid, reach)) {
			if (!d.made() || d.box() == null || d.start() == null || !CrumbShapes.KINDS.contains(d.kind())) {
				continue;
			}
			for (Line line : ofPlace(level, d, snapshot, c, terrain)) {
				if (reachesRegion(line, x0, z0)) {
					out.add(line);
				}
			}
		}
		for (Line line : ofWall(level, snapshot, c, terrain)) {
			if (reachesRegion(line, x0, z0)) {
				out.add(line);
			}
		}
		return List.copyOf(out);
	}

	private static boolean reachesRegion(Line line, int x0, int z0) {
		for (int cx = x0 >> 4; cx < (x0 + REGION) >> 4; cx += 4) {
			for (int cz = z0 >> 4; cz < (z0 + REGION) >> 4; cz += 4) {
				// A 4 x 4 of chunks at a time: its middle chunk with a margin of the rest.
				if (line.reaches(new ChunkPos(cx + 1, cz + 1), 40)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The id a place's lines are keyed by (its kind and candidate chunk). */
	public static String placeId(Decided.Decision d) {
		return d.kind() + "@" + d.chunk().x() + "," + d.chunk().z();
	}

	/** A decided place's lines (each fixed one as it was fixed). */
	public static List<Line> ofPlace(ServerLevel level, Decided.Decision d, Snapshot snapshot, Sc32Config c, Terrain terrain) {
		String id = placeId(d);
		String cacheKey = CrumbLines.prefix(snapshot.seed(), snapshot.salt()) + id;
		List<Line> known = PLACES.get(cacheKey);
		if (known != null) {
			return known;
		}
		// Every line of it fixed already (a world opened again): nothing to plan.
		List<Line> allFixed = withFixed(List.of(), snapshot, id, 3);
		if (allFixed.size() == 3) {
			PLACES.put(cacheKey, allFixed);
			return allFixed;
		}
		From from = new From(d.kind(), id, d.sign().getX(), d.sign().getZ(), d.box(), d.start().seed(), null);
		List<Box> others = new ArrayList<>();
		for (Decided.Decision o : Decided.around(level, from.x(), from.z(), c.lineMax + OBSTACLES)) {
			if (o.made() && o.box() != null && !(o.kind().equals(d.kind()) && o.chunk().equals(d.chunk()))) {
				others.add(Box.of(o.box()));
			}
		}
		for (LinePlan.Segment s : LineFeature.segments(snapshot, level.getMinY(), level.getMaxY())) {
			others.add(Box.of(s.box()));
		}
		others.addAll(villages(terrain, level.getChunkSource().getGeneratorState(), from.x(), from.z(), c.lineMax + VILLAGE_REACH));
		List<Line> planned = CrumbLines.plan(from, others, snapshot.spawn().getX(), snapshot.spawn().getZ(), c, snapshot.seed(), snapshot.salt(),
				CrumbFooting.model(terrain));
		List<Line> lines = withFixed(planned, snapshot, id, 3);
		PLACES.put(cacheKey, lines);
		return lines;
	}

	/** 19's lines: from each segment's cut, one on each side of the wall. */
	public static List<Line> ofWall(ServerLevel level, Snapshot snapshot, Sc32Config c, Terrain terrain) {
		String cacheKey = CrumbLines.prefix(snapshot.seed(), snapshot.salt()) + "s19";
		List<Line> known = PLACES.get(cacheKey);
		if (known != null) {
			return known;
		}
		List<Line> out = new ArrayList<>();
		for (LinePlan.Segment s : LineFeature.segments(snapshot, level.getMinY(), level.getMaxY())) {
			if (s.gapWidth() <= 0) {
				continue;
			}
			From from = wallFrom(s);
			List<Box> others = new ArrayList<>();
			for (Decided.Decision o : Decided.around(level, from.x(), from.z(), CrumbLines.WALL_LINE + OBSTACLES)) {
				if (o.made() && o.box() != null) {
					others.add(Box.of(o.box()));
				}
			}
			others.addAll(villages(terrain, level.getChunkSource().getGeneratorState(), from.x(), from.z(), CrumbLines.WALL_LINE + VILLAGE_REACH));
			List<Line> planned = CrumbLines.plan(from, others, snapshot.spawn().getX(), snapshot.spawn().getZ(), c, snapshot.seed(), snapshot.salt(),
					CrumbFooting.model(terrain));
			out.addAll(withFixed(planned, snapshot, from.id(), 2));
		}
		List<Line> lines = List.copyOf(out);
		PLACES.put(cacheKey, lines);
		return lines;
	}

	/** Where 19's two lines of a segment start: the middle of its cut, square to the wall both ways. */
	public static From wallFrom(LinePlan.Segment s) {
		int t = s.gapMiddle();
		Direction side = s.arm().out.getClockWise();
		double a = Math.atan2(side.getStepZ(), side.getStepX());
		BoundingBox own = s.box();
		return new From("s19", "s19@" + s.arm().name(), s.x(t), s.z(t), new BoundingBox(own.minX(), own.minY(), own.minZ(), own.maxX(), own.maxY(),
				own.maxZ()), s.seed(), new double[] {a, a + Math.PI});
	}

	/** How far round a village's candidate chunk its box is taken to reach (a village is about 100 across). */
	public static final int VILLAGE_REACH = 72;

	/**
	 * Where a big surface structure may stand within {@code radius} of (x, z): villages, ocean monuments, mansions and
	 * pillager outposts, each set's candidate chunks there that its placement takes (exclusion zones, frequency) and
	 * whose biome (at the candidate's middle, 64 high) one of its structures takes, a box of its reach round the
	 * candidate's middle. A line stops short of it as of a place (its crumbs would be skipped in the structure's box, a gap
	 * past the longest a follower meets). Any thread; cheap (a few candidates a place).
	 */
	public static List<Box> villages(Terrain terrain, ChunkGeneratorStructureState state, int x, int z, int radius) {
		List<Box> out = new ArrayList<>();
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			int reach = 0;
			for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
				reach = Math.max(reach, reach(e.structure()));
			}
			if (reach == 0) {
				continue;
			}
			StructurePlacement placement = set.value().placement();
			for (ChunkPos chunk : Decided.candidates(placement, state.getLevelSeed(), (x - radius) >> 4, (z - radius) >> 4, (x + radius) >> 4, (z + radius) >> 4)) {
				if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					continue;
				}
				int mx = chunk.getMiddleBlockX();
				int mz = chunk.getMiddleBlockZ();
				Holder<Biome> biome = terrain.biome(mx, 64, mz);
				int r = 0;
				for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
					if (reach(e.structure()) > 0 && e.structure().value().biomes().contains(biome)) {
						r = Math.max(r, reach(e.structure()));
					}
				}
				if (r > 0) {
					out.add(new Box(mx - r, mz - r, mx + r, mz + r));
				}
			}
		}
		return out;
	}

	/** How far round its start a big surface structure's box is taken to reach (0: not one a line stops at). */
	static int reach(Holder<Structure> structure) {
		if (structure.is(StructureTags.VILLAGE)) {
			return VILLAGE_REACH;
		}
		String id = structure.unwrapKey().map(k -> k.identifier().toString()).orElse("");
		if (id.equals("minecraft:monument")) {
			return 40;
		}
		if (id.equals("minecraft:mansion")) {
			return 48;
		}
		return id.equals("minecraft:pillager_outpost") ? 32 : 0;
	}

	/** The planned lines with each one fixed in this world put in its place (and a fixed one the plan no longer has). */
	static List<Line> withFixed(List<Line> planned, Snapshot snapshot, String placeId, int letters) {
		List<Line> out = new ArrayList<>(letters);
		for (int i = 0; i < letters; i++) {
			String key = CrumbLines.key(snapshot.seed(), snapshot.salt(), placeId, (char) ('A' + i));
			Line fixed = fixed(key);
			if (fixed != null) {
				out.add(fixed);
				continue;
			}
			for (Line p : planned) {
				if (p.key().equals(key)) {
					out.add(p);
				}
			}
		}
		return List.copyOf(out);
	}

	/** The line fixed under this key in this world, or null. Any thread. */
	public static @Nullable Line fixed(String key) {
		CompoundTag tag = Plans.line(key);
		return tag == null ? null : Line.fromTag(key, tag);
	}

	/** Fixes a line in this world unless it is fixed already; returns the one in force. Any thread. */
	public static Line fix(Line line) {
		CompoundTag kept = Plans.fixLine(line.key(), line.toTag());
		Line fixed = Line.fromTag(line.key(), kept);
		return fixed == null ? line : fixed;
	}

	/** Every line already found this run, of every square (the runtime rules' list). */
	public static List<Line> known() {
		Map<String, Line> out = new java.util.LinkedHashMap<>();
		for (List<Line> lines : PLACES.values()) {
			for (Line l : lines) {
				out.putIfAbsent(l.key(), l);
			}
		}
		return List.copyOf(out.values());
	}

	/** Server stop, or a test's own world: everything found is found again. */
	public static void clear() {
		REGIONS.clear();
		PLACES.clear();
	}
}
