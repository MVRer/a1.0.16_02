package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlan;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.kit.Writer;
import com.forzacode.a1016_02.world.sc2.s32.CrumbFooting.Found;
import com.forzacode.a1016_02.world.sc2.s32.CrumbFooting.Ground;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Spot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbShapes.Cell;
import com.forzacode.a1016_02.world.sc2.s32.CrumbShapes.Foot;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import org.jspecify.annotations.Nullable;

/**
 * Feature 32 in worldgen ({@code a1016_02:world/s32}, docs/SELF_CREATED_2_R3.md, "32", "Worldgen"): one placed feature,
 * run in every overworld chunk at the last decoration step ({@code top_layer_modification}, after every other
 * decoration, as the line). In each chunk: the lines that may cross it ({@link CrumbIndex}), and of each the crumbs whose
 * point lies in it, each built by this chunk alone and only inside it (its thing moved to fit), on footing found in the
 * live blocks ({@link CrumbFooting}), its cells guarded from later worldgen (the kit's {@link Writer}). Never within the
 * spawn's clearance, never in or by another structure's box (villages, trial chambers, his places) or one of the old
 * scars, never where its thing would take a log, a block entity or anything not of the land. A line is fixed in the
 * world the first time it builds a crumb (R12-2). Each crumb built is offered to the world's {@link CrumbData} for the
 * runtime rules. A chunk no line crosses costs one map read.
 */
public record CrumbFeature() implements Feature {
	public static final MapCodec<CrumbFeature> CODEC = MapCodec.unit(CrumbFeature::new);
	/** No crumb within this of another structure's box. */
	static final int STRUCTURE_CLEAR = 2;

	@Override
	public MapCodec<CrumbFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ChunkPos chunk = ChunkPos.containing(origin);
		try {
			return decorate(level, chunk, Snapshot.current(), S32.config()) > 0;
		} catch (RuntimeException e) {
			ScLog.error("[a1016] s2: s32 failed in a chunk", e);
			return false;
		}
	}

	/** Why nothing is built now, or null: no snapshot or config, the switch off, the spawn not known, the plans not read. */
	public static @Nullable String refusal(@Nullable Snapshot snapshot, @Nullable Sc32Config config) {
		if (snapshot == null) {
			return "no snapshot";
		}
		if (!snapshot.enabled()) {
			return "switched off";
		}
		if (!snapshot.spawnKnown()) {
			return "the world spawn not known yet";
		}
		if (config == null) {
			return "no config";
		}
		if (!Plans.ready()) {
			return "the world's plans not read yet";
		}
		return null;
	}

	/** Builds this chunk's crumbs; returns how many. The overworld only; writes only inside {@code chunk}. */
	public static int decorate(WorldGenLevel level, ChunkPos chunk, @Nullable Snapshot snapshot, @Nullable Sc32Config config) {
		if (!level.getLevel().dimension().equals(Level.OVERWORLD) || refusal(snapshot, config) != null) {
			return 0;
		}
		if (insideSpawn(chunk, snapshot, config.spawnClear)) {
			return 0;
		}
		List<Line> lines = CrumbIndex.near(level.getLevel(), chunk, snapshot, config);
		if (COSTS == null || lines.isEmpty()) {
			return decorate(level, chunk, snapshot, config, lines);
		}
		long t0 = System.nanoTime();
		int built = decorate(level, chunk, snapshot, config, lines);
		COSTS.add(System.nanoTime() - t0);
		return built;
	}

	/**
	 * Dev only ({@code -Da1016_02.s32cost}): each chunk's build once its lines are known (ns; the lines' planning, once a
	 * place a run, apart), for the real-world run's report. Null otherwise.
	 */
	public static final @Nullable List<Long> COSTS = Boolean.getBoolean("a1016_02.s32cost") ? java.util.Collections.synchronizedList(new java.util.ArrayList<>())
			: null;

	/** Builds this chunk's crumbs of these lines (worldgen's own path once the lines are found; tests give their own). */
	public static int decorate(WorldGenLevel level, ChunkPos chunk, Snapshot snapshot, Sc32Config config, List<Line> lines) {
		if (lines.isEmpty()) {
			return 0;
		}
		Build b = new Build(level, chunk, snapshot, config);
		int built = 0;
		for (Line line : lines) {
			built += b.line(line);
		}
		return built;
	}

	/**
	 * Builds a line's crumbs in a live level (the gallery, tests), each by its own chunk as worldgen would, nearest the
	 * place first; {@code structures} false skips the structure and scar keep-out (the gallery's flat world). Returns the
	 * crumbs built. Server thread; the chunks loaded.
	 */
	public static List<CrumbData.Crumb> live(ServerLevel level, Line line, Snapshot snapshot, Sc32Config config, boolean structures) {
		return live(level, line, snapshot, config, structures ? null : List.of());
	}

	/** {@link #live} with these boxes kept out instead of the world's structures and scars (null: the world's). */
	public static List<CrumbData.Crumb> live(ServerLevel level, Line line, Snapshot snapshot, Sc32Config config, @Nullable List<BoundingBox> keepOut) {
		List<CrumbData.Crumb> out = new ArrayList<>();
		for (Spot spot : line.spots()) {
			Build b = new Build(level, new ChunkPos(spot.x() >> 4, spot.z() >> 4), snapshot, config);
			if (keepOut != null) {
				b.keepOut = List.copyOf(keepOut);
			}
			CrumbData.Crumb c = b.spot(spot);
			if (c != null) {
				out.add(c);
			}
		}
		return out;
	}

	/** Debug and dev runs: why this spot of a line is not (or would be) built here now, nothing written. */
	public static String why(ServerLevel level, Spot spot, Snapshot snapshot, Sc32Config config) {
		Build b = new Build(level, new ChunkPos(spot.x() >> 4, spot.z() >> 4), snapshot, config);
		b.dry = true;
		b.spot(spot);
		return b.why;
	}

	/** True if every block of the chunk lies within the spawn's clearance. */
	static boolean insideSpawn(ChunkPos chunk, Snapshot snapshot, int clear) {
		double fx = Math.max(Math.abs(chunk.getMinBlockX() - snapshot.spawn().getX()), Math.abs(chunk.getMaxBlockX() - snapshot.spawn().getX()));
		double fz = Math.max(Math.abs(chunk.getMinBlockZ() - snapshot.spawn().getZ()), Math.abs(chunk.getMaxBlockZ() - snapshot.spawn().getZ()));
		return fx * fx + fz * fz < (double) clear * clear;
	}

	/** One chunk's decoration. */
	static final class Build {
		final WorldGenLevel level;
		final ChunkPos chunk;
		final Snapshot snapshot;
		final Sc32Config config;
		final Ground ground;
		private @Nullable Writer writer;
		@Nullable List<BoundingBox> keepOut;
		/** Dry: the checks only, nothing written ({@link #why}). */
		boolean dry;
		/** Why the last spot was not built (debug and the dev runs only). */
		String why = "";


		Build(WorldGenLevel level, ChunkPos chunk, Snapshot snapshot, Sc32Config config) {
			this.level = level;
			this.chunk = chunk;
			this.snapshot = snapshot;
			this.config = config;
			this.ground = Ground.live(level);
		}

		int line(Line line) {
			int built = 0;
			Line current = line;
			boolean fixed = false;
			for (Spot spot : current.spots()) {
				if (spot.x() >> 4 != chunk.x() || spot.z() >> 4 != chunk.z()) {
					continue;
				}
				if (!fixed) {
					// Fixed in the world the first time it builds anything (R12-2): a later config or spawn never moves it.
					Line inForce = CrumbIndex.fix(current);
					fixed = true;
					if (!inForce.equals(current)) {
						return built + this.line(inForce);
					}
				}
				if (spot(spot) != null) {
					built++;
				}
			}
			return built;
		}

		/** Builds one crumb if it finds footing and nothing forbids it; the crumb built, or null. */
		CrumbData.@Nullable Crumb spot(Spot spot) {
			if (nearSpawn(spot.x(), spot.z())) {
				why = "spawn";
				return null;
			}
			// The shift whose footing holds and whose cells take only the land's blocks, out of every structure: the first
			// (version 1) or the highest of them (version 2: each one's cells kept by its spot, the one chosen built).
			java.util.Map<Found, Cell[]> kept = new java.util.HashMap<>();
			java.util.Map<Found, Foot> keptFoot = new java.util.HashMap<>();
			why = "footing";
			Found found = CrumbFooting.find(ground, spot, chunk, at -> {
				Foot f = foot(spot, at);
				List<Cell> cells = new ArrayList<>(CrumbShapes.cells(spot, f));
				if (!allowed(cells)) {
					return false;
				}
				kept.put(at, cells.toArray(new Cell[0]));
				keptFoot.put(at, f);
				return true;
			});
			if (found == null) {
				return null;
			}
			Foot f = keptFoot.get(found);
			List<Cell> cells = new ArrayList<>(List.of(kept.get(found)));
			if (dry) {
				why = "would build";
				return null;
			}
			supports(cells, f);
			Writer w = writer();
			Set<BlockPos> own = new HashSet<>();
			for (Cell c : cells) {
				if (c.own()) {
					own.add(c.pos());
				}
			}
			// Its own cells first, torches last (each only on what holds it).
			for (Cell c : cells) {
				if (c.own() && !(c.state().getBlock() instanceof BaseTorchBlock)) {
					w.set(c.pos(), c.state());
				}
			}
			List<Cell> torches = new ArrayList<>();
			for (Cell c : cells) {
				if (c.own() && c.state().getBlock() instanceof BaseTorchBlock) {
					BlockPos under = c.pos().below();
					if (level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
						w.set(c.pos(), c.state());
						torches.add(c);
					}
				}
			}
			for (Cell c : cells) {
				if (!c.own() && (c.state().isAir() ? !own.contains(c.pos()) && clearable(w.get(c.pos())) : w.get(c.pos()).is(Blocks.WATER))) {
					w.set(c.pos(), c.state());
				}
			}
			int top = Integer.MIN_VALUE;
			for (BlockPos p : own) {
				top = Math.max(top, p.getY());
			}
			deadGround(w, spot, f, own);
			// Version 2 (every second mid crowned, so taller under trees): the hole from the crumb's foot up, not over its top.
			canopy(w, f, spot.line().version() >= CrumbFooting.RISES_FROM ? Math.min(top, f.y()) : top);
			CrumbData.Crumb crumb = record(spot, f, cells, torches);
			CrumbData.offer(crumb);
			return crumb;
		}

		/** The footing's foot: its footprint's grounds, the land's stone and the biome's wood. */
		Foot foot(Spot spot, Found found) {
			String kind = spot.line().kind();
			boolean wet = CrumbShapes.pillar(kind, spot.tier(), found.wet());
			int[][] foot = CrumbShapes.footprint(kind, spot.tier(), wet);
			int[] grounds = new int[foot.length];
			for (int i = 0; i < foot.length; i++) {
				grounds[i] = ground.ground(found.x() + foot[i][0], found.z() + foot[i][1]);
			}
			return new Foot(found.x(), found.y(), found.z(), found.surface(), stone(found), log(found), grounds);
		}

		/** True if every own cell lies in this chunk, takes only the land's blocks, and stays out of every structure and scar. */
		boolean allowed(List<Cell> cells) {
			List<BoundingBox> out = keepOut();
			for (Cell c : cells) {
				if (!c.own()) {
					continue;
				}
				BlockPos p = c.pos();
				if (p.getX() >> 4 != chunk.x() || p.getZ() >> 4 != chunk.z() || p.getY() <= level.getMinY() || p.getY() >= level.getMaxY()) {
					why = "out of its chunk";
					return false;
				}
				BlockState there = level.getBlockState(p);
				if (!CrumbFooting.natural(there)) {
					why = "takes " + there.getBlock().getDescriptionId();
					return false;
				}
				for (BoundingBox box : out) {
					if (box.inflatedBy(STRUCTURE_CLEAR).isInside(p)) {
						why = "a structure's box";
						return false;
					}
				}
			}
			return true;
		}

		/** A thing set into the ground (17's hole) keeps solid walls and floor: what is not solid round it becomes cobblestone. */
		void supports(List<Cell> cells, Foot f) {
			Set<BlockPos> own = new HashSet<>();
			for (Cell c : cells) {
				if (c.own()) {
					own.add(c.pos());
				}
			}
			List<Cell> add = new ArrayList<>();
			for (Cell c : cells) {
				if (!c.own() || c.pos().getY() > f.y() || f.wet()) {
					continue;
				}
				for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
					BlockPos n = c.pos().relative(d);
					if (own.contains(n) || n.getX() >> 4 != chunk.x() || n.getZ() >> 4 != chunk.z()) {
						continue;
					}
					BlockState s = level.getBlockState(n);
					if (!s.isSolid() && CrumbFooting.natural(s) && own.add(n)) {
						add.add(new Cell(n, CrumbShapes.COBBLE, true));
					}
				}
			}
			cells.addAll(add);
		}

		/** Dead ground round a near thing: its top block to dirt (or 18's gravel and dirt), plants and snow off. Its chunk only. */
		void deadGround(Writer w, Spot spot, Foot f, Set<BlockPos> own) {
			String kind = spot.line().kind();
			int r = CrumbShapes.ring(kind, spot.tier(), f.wet());
			if (r <= 0) {
				return;
			}
			int lo = -r;
			int hi = kind.equals("s17") ? r + 1 : r;
			for (int dx = lo; dx <= hi; dx++) {
				for (int dz = lo; dz <= hi; dz++) {
					int x = f.x() + dx;
					int z = f.z() + dz;
					if (x >> 4 != chunk.x() || z >> 4 != chunk.z()) {
						continue;
					}
					int g = ground.ground(x, z);
					BlockPos at = new BlockPos(x, g, z);
					if (own.contains(at) || Math.abs(g - f.y()) > CrumbFooting.MAX_FILL) {
						continue;
					}
					BlockState dead = CrumbShapes.dead(kind, w.get(at), CrumbLines.mix(spot.line().seed(), (long) x * 31 + z));
					if (dead != null) {
						w.set(at, dead);
					}
					for (int dy = 1; dy <= 2; dy++) {
						BlockPos up = at.above(dy);
						BlockState s = w.get(up);
						if (!own.contains(up) && clearable(s) && !s.isAir()) {
							w.set(up, CrumbShapes.AIR);
						}
					}
					// Lit as at noon for nobody: a fault light over each corner of the ring (the places' own grammar, D-110).
					int in = kind.equals("s17") ? 0 : 1;
					boolean corner = (dx == lo + in || dx == hi - in) && (dz == lo + in || dz == hi - in);
					if (corner && w.get(at.above()).isAir() && !own.contains(at.above())) {
						w.set(at.above(), Fault.lightOf(Fault.LEVEL, false));
					}
				}
			}
		}

		/** Under a canopy: the leaves over {@code top} (a 3 x 3 to the sky) taken off, a lit hole in the trees at night. */
		void canopy(Writer w, Foot f, int top) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = f.x() + dx;
					int z = f.z() + dz;
					if (x >> 4 != chunk.x() || z >> 4 != chunk.z()) {
						continue;
					}
					int sky = Math.min(level.getMaxY(), top + 40);
					for (int y = top + 1; y <= sky; y++) {
						BlockPos p = new BlockPos(x, y, z);
						BlockState s = w.get(p);
						if (s.is(BlockTags.LEAVES) || s.is(Blocks.VINE)) {
							w.set(p, CrumbShapes.AIR);
						}
					}
				}
			}
		}

		/** What a ground cell may clear: plants, snow, leaves (never a block of the land or anything made). */
		static boolean clearable(BlockState s) {
			return s.isAir() || s.is(Blocks.SNOW) || s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty();
		}

		boolean nearSpawn(int x, int z) {
			double dx = x - snapshot.spawn().getX();
			double dz = z - snapshot.spawn().getZ();
			return dx * dx + dz * dz < (double) config.spawnClear * config.spawnClear;
		}

		Writer writer() {
			if (writer == null) {
				BoundingBox column = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY() - 1,
						chunk.getMaxBlockZ());
				ServerLevel server = level.getLevel();
				writer = Writer.worldgen(level, column, Terrains.noise(server.getChunkSource().getGenerator(), server.getChunkSource().randomState(), level));
			}
			return writer;
		}

		/** Every structure's box this chunk holds a piece or a reference of, and the old scars touching it. */
		List<BoundingBox> keepOut() {
			if (keepOut != null) {
				return keepOut;
			}
			List<BoundingBox> out = new ArrayList<>();
			try {
				ServerLevel server = level.getLevel();
				StructureManager manager = level instanceof WorldGenRegion region ? server.structureManager().forWorldGenRegion(region)
						: server.structureManager();
				for (StructureStart start : manager.startsForStructure(chunk.x(), chunk.z(), s -> true)) {
					if (start.isValid()) {
						out.add(start.getBoundingBox());
					}
				}
				ScarContext scars = ScarContext.current();
				ScarPlanner planner = scars == null ? null : scars.planner(server);
				if (planner != null) {
					for (ScarPlan plan : planner.pointPlansTouching(chunk)) {
						out.add(plan.footprint());
					}
					planner.areaPlanAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).ifPresent(p -> out.add(p.footprint()));
				}
			} catch (RuntimeException e) {
				ScLog.warn("[a1016] s2: s32 could not read a chunk's structures", e);
			}
			keepOut = out;
			return out;
		}

		/** The land's own stone under the ground (18's), stone where none is near. */
		BlockState stone(Found f) {
			for (int y = f.y(); y > f.y() - 12 && y > level.getMinY(); y--) {
				BlockState s = level.getBlockState(new BlockPos(f.x(), y, f.z()));
				if (CrumbShapes.landStone(s)) {
					return s.getBlock().defaultBlockState();
				}
			}
			return Blocks.STONE.defaultBlockState();
		}

		/** The biome's wood (18's bare trunk). */
		BlockState log(Found f) {
			return level.getBiome(new BlockPos(f.x(), f.y() + 1, f.z())).unwrapKey().map(k -> CrumbShapes.logFor(k.identifier().getPath()))
					.orElse(Blocks.OAK_LOG.defaultBlockState());
		}

		/** The crumb as the runtime rules know it: a post (one column and its torch) or a thing they leave alone. */
		CrumbData.Crumb record(Spot spot, Foot f, List<Cell> cells, List<Cell> torches) {
			BlockPos foot = null;
			int height = 0;
			boolean post = !f.wet();
			for (Cell c : cells) {
				if (!c.own() || c.state().getBlock() instanceof BaseTorchBlock) {
					continue;
				}
				if (c.pos().getX() != f.x() || c.pos().getZ() != f.z() || c.state().isAir() || c.pos().getY() <= f.y()) {
					post = false;
					continue;
				}
				foot = foot == null || c.pos().getY() < foot.getY() ? c.pos() : foot;
				height++;
			}
			if (foot == null) {
				foot = new BlockPos(f.x(), f.y() + 1, f.z());
				post = false;
			}
			int torch = -1;
			if (torches.size() == 1) {
				BlockPos t = torches.getFirst().pos();
				if (t.equals(foot.above(height))) {
					torch = 1;
				} else if (t.getY() == foot.getY()) {
					Direction side = Direction.getApproximateNearest(t.getX() - foot.getX(), 0, t.getZ() - foot.getZ());
					torch = side.get3DDataValue();
				} else {
					post = false;
				}
			} else if (torches.size() > 1) {
				post = false;
			}
			return new CrumbData.Crumb(spot.line().key(), spot.index(), spot.tier(), foot.immutable(), post ? height : 0, post ? torch : -1, false);
		}
	}
}
