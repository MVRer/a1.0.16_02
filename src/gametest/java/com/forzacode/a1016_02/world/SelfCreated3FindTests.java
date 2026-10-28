package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.FaultDemo;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.NoiseVolume;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s19.Sc19Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;


/**
 * R3-0's find measurements (docs/SELF_CREATED_2_R3.md, "Targets (measured)"), run when asked
 * ({@code -Da1016_02.sc3find=true}; a number of worlds with {@code =N}): worldgen's own decisions over the real noise
 * (SC2-H's path: the placements' own checks, the kit's gates, each kind's siting, the kit's reach and height, vanilla's
 * biome check, the sets' weighted order, the archive's avoid list), before (the numbers at 97f4aee, every chunk taken
 * as decided after Traces: a bound in their favour) and after (round 3's), over 20 default worlds (the targets' bound) and
 * 2 large-biome and 2 amplified ones (reported). T1 to T4 and T6; the crumbs of T3 and T4 are modelled from the plan's
 * line table (no footing: R3-C measures T5 with the real lines). Prints to the log (debug) and
 * {@code build/sc3find.txt}; asserts nothing on the numbers (the report does), only that it ran. Also, when asked, the
 * kit's own costs ({@code -Da1016_02.sc3cost=true}).
 */
public class SelfCreated3FindTests {
	static final String PROPERTY = "a1016_02.sc3find";
	private static final String ENV = "a1016_02:world/sc2_heights";
	/** How far round the spawn places are decided (flights reach 3 000 + 1 500, and a crumb line 640 more). */
	static final int REACH = 4_800;

	/** One place as worldgen decides it: its kind, its sign point (2D), its box. */
	record Place(String kind, int x, int z, BoundingBox box, ChunkPos chunk) {
	}

	/** The numbers of one era: each set's placement and each kind's R0. */
	record Era(String name, Map<String, StructurePlacement> placements, Map<String, Integer> r0, int lineFrom, int lineMin, int lineMax, boolean avoid,
			boolean spaced) {
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void theFindTargetsWhenAsked(GameTestHelper helper) {
		String asked = System.getProperty(PROPERTY, "");
		if (asked.isEmpty() || asked.equals("false")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int defaults = asked.equals("true") ? 20 : Integer.parseInt(asked);
		List<String> out = new ArrayList<>();
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		Era before = before(level);
		Era after = after(level, structures);
		Random seeds = new Random(0x5C3F1DL);
		Map<String, List<double[]>> table = new LinkedHashMap<>();
		Map<String, List<Long>> sitingNanos = new ConcurrentHashMap<>();
		List<String[]> worlds = new ArrayList<>();
		for (int i = 0; i < defaults; i++) {
			worlds.add(new String[] {"normal", Long.toString(seeds.nextLong())});
		}
		for (int i = 0; i < 2; i++) {
			worlds.add(new String[] {"large", Long.toString(seeds.nextLong())});
			worlds.add(new String[] {"amplified", Long.toString(seeds.nextLong())});
		}
		Snapshot keep = Snapshot.current();
		try {
			for (String[] w : worlds) {
				long seed = Long.parseLong(w[1]);
				Sc2RealNoise.World world = Sc2RealNoise.of(level, w[0], Sc2RealNoise.PRESETS.get(w[0]), seed, -64, 384);
				ChunkPos origin = world.generator().getOrigin(world.random());
				BlockPos spawn = new BlockPos(origin.getMinBlockX() + 8, 64, origin.getMinBlockZ() + 8);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, spawn, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				Map<String, Optional<Place>> sited = new ConcurrentHashMap<>();
				List<Era> eras = new ArrayList<>(List.of(before, after));
				for (String spec : System.getProperty("a1016_02.sc3try", "").split(";")) {
					if (!spec.isBlank()) {
						eras.add(trial(level, after, spec));
					}
				}
				for (Era era : eras) {
					List<Place> places = places(level, world, structures, era, spawn, snapshot, sited, sitingNanos);
					double[] m = measure(level, world, era, places, spawn, seed);
					table.computeIfAbsent(w[0] + " " + era.name(), k -> new ArrayList<>()).add(m);
					out.add(String.format(Locale.ROOT, "%s %d %s: %d places; T1 %.0f (structures %.0f); 16 %.0f 17 %.0f 18 %.0f (mountains %s) 20 %.0f; "
							+ "line from %.0f; T3 sign %.3f, sign or crumb %.3f; T4 s1 %.3f s2 %.3f s3 %.3f sign by s3 %.3f; T6 near pairs %.0f, density %.2f",
							w[0], seed, era.name(), places.size(), m[0], m[1], m[2], m[3], m[4], m[5] > 0 ? "yes" : "no", m[6], m[7], m[8], m[9], m[10], m[11], m[12],
							m[13], m[14], m[15]));
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		out.add("");
		for (Map.Entry<String, List<double[]>> e : table.entrySet()) {
			out.add(summary(e.getKey(), e.getValue()));
		}
		out.add("near pairs by kinds: " + PAIRS);
		out.addAll(SelfCreated3CrumbTests.t5Summary());
		PAIRS.clear();
		for (Map.Entry<String, List<Long>> e : new java.util.TreeMap<>(sitingNanos).entrySet()) {
			List<Long> ns = new ArrayList<>(e.getValue());
			Collections.sort(ns);
			out.add(String.format(Locale.ROOT, "siting %s: %d candidates, median %.2f ms, worst %.1f ms", e.getKey(), ns.size(), ns.get(ns.size() / 2) / 1e6,
					ns.getLast() / 1e6));
		}
		write(out);
		helper.succeed();
	}

	// --- eras ---

	private static Holder<StructureSet> set(ServerLevel level, String id) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(id)));
	}

	/** The numbers at 97f4aee (round 2 as Mariano played it). */
	static Era before(ServerLevel level) {
		Map<String, StructurePlacement> p = new LinkedHashMap<>();
		Holder<StructureSet> villages = set(level, "minecraft:villages");
		Holder<StructureSet> chambers = set(level, "minecraft:trial_chambers");
		p.put("s16", new RaysPlacement(160721, 4, 640, 640, 8.0F, 48, 64, Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(villages, 4))));
		p.put("s17", spread(20, 8, 1716203, Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(chambers, 4))));
		p.put("s18", spread(24, 10, 180457, Optional.empty()));
		p.put("s20", spread(36, 16, 2016220, Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(villages, 6))));
		return new Era("before", p, Map.of("s16", 560, "s16d", 560, "s17", 500, "s18", 700, "s20", 700), 1024, 256, 768, false, false);
	}

	private static RandomSpreadStructurePlacement spread(int spacing, int separation, int salt, Optional<AbstractSpreadingStructurePlacement.ExclusionZone> zone) {
		return new RandomSpreadStructurePlacement(net.minecraft.core.Vec3i.ZERO, AbstractSpreadingStructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F,
				salt, zone, spacing, separation, RandomSpreadType.LINEAR);
	}

	/** Round 3's: the registry's sets and each kind's config. */
	static Era after(ServerLevel level, Map<String, Holder<Structure>> structures) {
		Map<String, StructurePlacement> p = new LinkedHashMap<>();
		Map<String, Integer> r0 = new HashMap<>();
		for (String id : List.of("s16", "s17", "s18", "s20")) {
			p.put(id, Sc2RealNoise.placement(level, structures.get(id)));
		}
		for (String id : List.of("s16", "s16d", "s17", "s18", "s20")) {
			r0.put(id, Sc2Kind.get(id).gates().minOriginDistance());
		}
		Sc19Config c19 = new Sc19Config();
		return new Era("after", p, r0, c19.startMin, c19.lengthMin, c19.lengthMax, true, true);
	}

	/**
	 * A trial of other numbers within the plan's bounds ({@code -Da1016_02.sc3try=rays,first,step,s18 spacing,s18 separation,
	 * s20 spacing,s20 separation,R0 16 and 17,R0 18 and 20}), the archive's as round 3's.
	 */
	static Era trial(ServerLevel level, Era after, String spec) {
		String[] n = spec.split(",");
		int[] v = new int[n.length];
		for (int i = 0; i < n.length; i++) {
			v[i] = Integer.parseInt(n[i].trim());
		}
		Map<String, StructurePlacement> p = new LinkedHashMap<>(after.placements());
		Holder<StructureSet> villages = set(level, "minecraft:villages");
		p.put("s16", new RaysPlacement(160721, v[0], v[1], v[2], 8.0F, 48, 64, Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(villages, 4))));
		p.put("s18", spread(v[3], v[4], 180457, Optional.empty()));
		p.put("s20", spread(v[5], v[6], 2016220, Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(villages, 6))));
		Map<String, Integer> r0 = Map.of("s16", v[7], "s16d", v[7], "s17", v[7], "s18", v[8], "s20", v[8]);
		return new Era("try " + spec.replace(',', '/'), p, r0, after.lineFrom(), after.lineMin(), after.lineMax(), true, true);
	}

	// --- the places ---

	/** Every place of the era within {@link #REACH} of the spawn, as worldgen decides it (sitings shared between eras). */
	static List<Place> places(ServerLevel level, Sc2RealNoise.World world, Map<String, Holder<Structure>> structures, Era era, BlockPos spawn,
			Snapshot snapshot, Map<String, Optional<Place>> sited, Map<String, List<Long>> nanos) {
		return placesIn(level, world, structures, era, spawn, snapshot, sited, nanos,
				placement -> Sc2RealNoise.candidates(world, placement, spawn.getX(), spawn.getZ(), REACH, Integer.MAX_VALUE));
	}

	/** {@link #places} among the candidate chunks {@code candidates} gives for each set's placement (FIX-R3-E: a band along the line). */
	static List<Place> placesIn(ServerLevel level, Sc2RealNoise.World world, Map<String, Holder<Structure>> structures, Era era, BlockPos spawn,
			Snapshot snapshot, Map<String, Optional<Place>> sited, Map<String, List<Long>> nanos,
			java.util.function.Function<StructurePlacement, List<ChunkPos>> candidates) {
		List<Place> out = Collections.synchronizedList(new ArrayList<>());
		for (Map.Entry<String, StructurePlacement> e : era.placements().entrySet()) {
			StructurePlacement placement = e.getValue();
			List<String> kinds = e.getKey().equals("s16") ? List.of("s16", "s16d") : List.of(e.getKey());
			List<ChunkPos> chunks = candidates.apply(placement);
			chunks.parallelStream().forEach(chunk -> {
				List<String> order = order(kinds, world.seed(), chunk);
				for (String kind : order) {
					int r0 = era.r0().get(kind);
					double mx = chunk.getMiddleBlockX();
					double mz = chunk.getMiddleBlockZ();
					if (Math.hypot(mx, mz) < r0 || Math.hypot(mx - spawn.getX(), mz - spawn.getZ()) < r0) {
						continue;
					}
					Holder<Structure> structure = structures.get(kind);
					if (era.avoid() && structure.value() instanceof Sc2Structure sc2
							&& Decided.excluded(sc2, world.structures(), List.of(), chunk) != null) {
						continue;
					}
					Optional<Place> place = sited.computeIfAbsent(kind + "/" + chunk.pack(), k -> {
						long t = System.nanoTime();
						Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get(kind), chunk, snapshot);
						nanos.computeIfAbsent(kind + (s.made() ? " made" : " refused"), x -> Collections.synchronizedList(new ArrayList<>())).add(System.nanoTime() - t);
						if (!s.made()) {
							return Optional.empty();
						}
						Shape shape = s.shape();
						BlockPos sign = shape.sign();
						return Optional.of(new Place(kind, sign.getX(), sign.getZ(), shape.box(), chunk));
					});
					if (place.isPresent()) {
						out.add(place.get());
						break;
					}
				}
			});
		}
		return era.spaced() ? spaced(out, world.seed()) : new ArrayList<>(out);
	}

	/**
	 * The kit's spacing rule ({@code Decided.yields}) as worldgen meets it: in order of precedence, every place that no
	 * place already kept comes within {@code Decided.NEAR} of.
	 */
	static List<Place> spaced(List<Place> places, long seed) {
		List<Place> sorted = new ArrayList<>(places);
		sorted.sort((a, b) -> Long.compare(Decided.priority(seed, b.kind(), b.chunk(), b.box()), Decided.priority(seed, a.kind(), a.chunk(), a.box())));
		List<Place> kept = new ArrayList<>();
		for (Place p : sorted) {
			boolean near = false;
			for (Place k : kept) {
				if (Decided.near(p.box(), k.box())) {
					near = true;
					break;
				}
			}
			if (!near) {
				kept.add(p);
			}
		}
		return kept;
	}

	/** A multi-structure set's kinds in vanilla's weighted order for this chunk (weights 1). */
	private static List<String> order(List<String> kinds, long seed, ChunkPos chunk) {
		if (kinds.size() == 1) {
			return kinds;
		}
		List<String> left = new ArrayList<>(kinds);
		List<String> out = new ArrayList<>();
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunk.x(), chunk.z());
		while (!left.isEmpty()) {
			out.add(left.remove(random.nextInt(left.size())));
		}
		return out;
	}

	/**
	 * The line's sign points (every hole, every 48 along each segment) for an era's numbers; round 3's kept clear of the
	 * era's places (FIX-R3-E).
	 */
	static List<int[]> line(Sc2RealNoise.World world, Era era, List<Place> places) {
		Sc19Config c = new Sc19Config();
		c.startMin = era.lineFrom();
		c.lengthMin = era.lineMin();
		c.lengthMax = era.lineMax();
		List<int[]> out = new ArrayList<>();
		List<LinePlan.Segment> segments = LinePlan.of(world.seed(), 7L, c, world.minY(), world.maxY());
		for (LinePlan.Segment s : era.avoid() ? SelfCreated2LineTests.clearOf(segments, places, c) : segments) {
			for (int t = Math.min(s.t0(), s.t1()); t <= Math.max(s.t0(), s.t1()); t += 48) {
				out.add(new int[] {s.x(t), s.z(t)});
			}
		}
		return out;
	}

	// --- the measures ---

	/** A grid of points for distance asks. */
	static final class Grid {
		static final int CELL = 64;
		final Map<Long, List<int[]>> cells = new HashMap<>();

		void add(int x, int z) {
			cells.computeIfAbsent(ChunkPos.pack(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)), k -> new ArrayList<>()).add(new int[] {x, z});
		}

		/** True if a point lies within {@code r} of the segment from (x0, z0) to (x1, z1). */
		boolean near(double x0, double z0, double x1, double z1, double r) {
			int cx0 = Math.floorDiv((int) Math.floor(Math.min(x0, x1) - r), CELL);
			int cx1 = Math.floorDiv((int) Math.ceil(Math.max(x0, x1) + r), CELL);
			int cz0 = Math.floorDiv((int) Math.floor(Math.min(z0, z1) - r), CELL);
			int cz1 = Math.floorDiv((int) Math.ceil(Math.max(z0, z1) + r), CELL);
			double dx = x1 - x0;
			double dz = z1 - z0;
			double len2 = dx * dx + dz * dz;
			for (int cx = cx0; cx <= cx1; cx++) {
				for (int cz = cz0; cz <= cz1; cz++) {
					List<int[]> in = cells.get(ChunkPos.pack(cx, cz));
					if (in == null) {
						continue;
					}
					for (int[] p : in) {
						double t = len2 == 0 ? 0 : Math.clamp(((p[0] - x0) * dx + (p[1] - z0) * dz) / len2, 0, 1);
						double ex = x0 + t * dx - p[0];
						double ez = z0 + t * dz - p[1];
						if (ex * ex + ez * ez <= r * r) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	/**
	 * The modelled crumbs (the plan's lines and tiers, no footing): from each place's sign, line A toward the spawn
	 * (stopping 256 short of it, at most 640), B and C at plus and minus 100 to 140 degrees, 640; near every 16 from 64
	 * to 192, mid every 32 to 384, far every 56 to 640; the line's from each segment's gap, 512 each side.
	 */
	static void crumbs(Grid grid, List<Place> places, Sc2RealNoise.World world, Era era, BlockPos spawn) {
		if (!Boolean.getBoolean("a1016_02.sc3modelled")) {
			// R3-C: the real lines, cut by their own planner over the noise, each crumb where its footing puts it (and T5).
			SelfCreated3CrumbTests.real(grid, places, world, world.name() + " " + era.name(), spawn, era.lineFrom(), era.lineMin(), era.lineMax());
			return;
		}
		for (Place p : places) {
			double toSpawn = Math.atan2(spawn.getZ() - p.z(), spawn.getX() - p.x());
			double spawnDist = Math.hypot(spawn.getX() - p.x(), spawn.getZ() - p.z());
			Random r = new Random(p.x() * 341873128712L + p.z() * 132897987541L);
			double turn = Math.toRadians(100 + r.nextDouble() * 40);
			lay(grid, p.x(), p.z(), toSpawn, Math.min(640, spawnDist - 256));
			lay(grid, p.x(), p.z(), toSpawn + turn, 640);
			lay(grid, p.x(), p.z(), toSpawn - turn, 640);
		}
		Sc19Config c = new Sc19Config();
		c.startMin = era.lineFrom();
		c.lengthMin = era.lineMin();
		c.lengthMax = era.lineMax();
		for (LinePlan.Segment s : LinePlan.of(world.seed(), 7L, c, world.minY(), world.maxY())) {
			int t = s.gapMiddle();
			Direction side = s.arm().out.getClockWise();
			double a = Math.atan2(side.getStepZ(), side.getStepX());
			lay(grid, s.x(t), s.z(t), a, 512);
			lay(grid, s.x(t), s.z(t), a + Math.PI, 512);
		}
	}

	private static void lay(Grid grid, double x, double z, double angle, double length) {
		if (length < 128) {
			return;
		}
		double d = 64;
		while (d <= length) {
			grid.add((int) Math.round(x + Math.cos(angle) * d), (int) Math.round(z + Math.sin(angle) * d));
			d += d < 192 ? 16 : d < 384 ? 32 : 56;
		}
	}

	/**
	 * One world's numbers: [0] T1 nearest place (signs, line included) to the spawn, [1] the same without the line, [2] 16 on
	 * land nearest, [3] 17, [4] 18, [5] a mountain biome within 1 200 (1 or 0), [6] 20, [7] the line's nearest point, [8] T3
	 * share of flights within 128 of a sign, [9] within 128 of a sign or 48 of a crumb, [10..12] T4 sign or crumb by session
	 * 1, 2, 3, [13] T4 sign by session 3, [14] T6 pairs of boxes nearer than 96, [15] T6 places per 1 000 x 1 000 from 900 to
	 * 3 000.
	 */
	/** Near pairs of boxes by era and kinds, over the run. */
	static final Map<String, java.util.concurrent.atomic.AtomicInteger> PAIRS = new java.util.TreeMap<>();

	static double[] measure(ServerLevel level, Sc2RealNoise.World world, Era era, List<Place> places, BlockPos spawn, long seed) {
		double[] m = new double[16];
		int sx = spawn.getX();
		int sz = spawn.getZ();
		Grid signs = new Grid();
		double nearestStructure = Double.MAX_VALUE;
		Map<String, Double> nearestOf = new HashMap<>();
		for (Place p : places) {
			signs.add(p.x(), p.z());
			double d = Math.hypot(p.x() - sx, p.z() - sz);
			nearestStructure = Math.min(nearestStructure, d);
			nearestOf.merge(p.kind(), d, Math::min);
		}
		double nearestLine = Double.MAX_VALUE;
		for (int[] q : line(world, era, places)) {
			signs.add(q[0], q[1]);
			nearestLine = Math.min(nearestLine, Math.hypot(q[0] - sx, q[1] - sz));
		}
		m[0] = Math.min(nearestStructure, nearestLine);
		m[1] = nearestStructure;
		m[2] = nearestOf.getOrDefault("s16", Double.MAX_VALUE);
		m[3] = nearestOf.getOrDefault("s17", Double.MAX_VALUE);
		m[4] = nearestOf.getOrDefault("s18", Double.MAX_VALUE);
		m[5] = mountainsWithin(level, world, spawn, 1200) ? 1 : 0;
		m[6] = nearestOf.getOrDefault("s20", Double.MAX_VALUE);
		m[7] = nearestLine;
		Grid crumbs = new Grid();
		crumbs(crumbs, places, world, era, spawn);
		// T3: 400 flights from 384 to 3 000 out, 1 500 long, random headings.
		Random r = new Random(seed ^ 0x7F3L);
		int sign = 0;
		int either = 0;
		for (int i = 0; i < 400; i++) {
			double rad = Math.sqrt(384.0 * 384 + r.nextDouble() * (3000.0 * 3000 - 384.0 * 384));
			double a = r.nextDouble() * Math.PI * 2;
			double h = r.nextDouble() * Math.PI * 2;
			double x0 = sx + Math.cos(a) * rad;
			double z0 = sz + Math.sin(a) * rad;
			double x1 = x0 + Math.cos(h) * 1500;
			double z1 = z0 + Math.sin(h) * 1500;
			boolean s = signs.near(x0, z0, x1, z1, 128);
			sign += s ? 1 : 0;
			either += s || crumbs.near(x0, z0, x1, z1, 48) ? 1 : 0;
		}
		m[8] = sign / 400.0;
		m[9] = either / 400.0;
		// T4: the explorer, 2 000 runs of three sessions.
		int[] bySession = new int[3];
		int signBy3 = 0;
		for (int run = 0; run < 2000; run++) {
			int metAt = -1;
			boolean signMet = false;
			for (int session = 0; session < 3 && !(metAt >= 0 && signMet); session++) {
				double x = sx;
				double z = sz;
				double h = r.nextDouble() * Math.PI * 2;
				for (int step = 0; step < 180; step++) {
					h += Math.toRadians(r.nextGaussian() * 25);
					if (Math.hypot(x - sx, z - sz) > 1) {
						double outward = Math.atan2(z - sz, x - sx);
						h += 0.2 * Math.atan2(Math.sin(outward - h), Math.cos(outward - h));
					}
					double nx = x + Math.cos(h) * 43;
					double nz = z + Math.sin(h) * 43;
					boolean s = signs.near(x, z, nx, nz, 128);
					if (s) {
						signMet = true;
					}
					if (metAt < 0 && (s || crumbs.near(x, z, nx, nz, 48))) {
						metAt = session;
					}
					x = nx;
					z = nz;
				}
			}
			for (int session = 0; session < 3; session++) {
				if (metAt >= 0 && metAt <= session) {
					bySession[session]++;
				}
			}
			signBy3 += signMet ? 1 : 0;
		}
		m[10] = bySession[0] / 2000.0;
		m[11] = bySession[1] / 2000.0;
		m[12] = bySession[2] / 2000.0;
		m[13] = signBy3 / 2000.0;
		// T6.
		int pairs = 0;
		for (int i = 0; i < places.size(); i++) {
			for (int j = i + 1; j < places.size(); j++) {
				BoundingBox a = places.get(i).box();
				BoundingBox b = places.get(j).box();
				int gx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
				int gz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));
				if (Math.hypot(gx, gz) < 96) {
					pairs++;
					String key = places.get(i).kind().compareTo(places.get(j).kind()) < 0 ? places.get(i).kind() + "-" + places.get(j).kind()
							: places.get(j).kind() + "-" + places.get(i).kind();
					PAIRS.computeIfAbsent(era.name() + " " + key, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
				}
			}
		}
		int ring = 0;
		for (Place p : places) {
			double d = Math.hypot(p.x() - sx, p.z() - sz);
			if (d >= 900 && d <= 3000) {
				ring++;
			}
		}
		m[14] = pairs;
		m[15] = ring / (Math.PI * (3000.0 * 3000 - 900.0 * 900) / 1e6);
		return m;
	}

	/** True if a mountain biome (the hollow's own list) lies within {@code radius} of the spawn (a 64-block grid, at 128). */
	static boolean mountainsWithin(ServerLevel level, Sc2RealNoise.World world, BlockPos spawn, int radius) {
		Holder<Structure> s18 = Sc2RealNoise.structures(level).get("s18");
		for (int dx = -radius; dx <= radius; dx += 64) {
			for (int dz = -radius; dz <= radius; dz += 64) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int x = spawn.getX() + dx;
				int z = spawn.getZ() + dz;
				if (s18.value().biomes().contains(world.terrain().biome(x, 128, z))) {
					return true;
				}
			}
		}
		return false;
	}

	/** One era's line over its worlds: the targets' numbers. */
	static String summary(String name, List<double[]> rows) {
		int n = rows.size();
		List<Double> t1 = new ArrayList<>();
		List<Double> t1s = new ArrayList<>();
		int t1in = 0;
		int t1sIn = 0;
		int k16 = 0;
		int k17 = 0;
		int k20 = 0;
		int mountains = 0;
		int k18 = 0;
		double t3 = 0;
		double t3e = 0;
		double s1 = 0;
		double s2 = 0;
		double s3 = 0;
		double sign3 = 0;
		double pairs = 0;
		double density = 0;
		for (double[] m : rows) {
			t1.add(m[0]);
			t1s.add(m[1]);
			t1in += m[0] <= 640 ? 1 : 0;
			t1sIn += m[1] <= 640 ? 1 : 0;
			k16 += m[2] <= 800 ? 1 : 0;
			k17 += m[3] <= 640 ? 1 : 0;
			k20 += m[6] <= 900 ? 1 : 0;
			if (m[5] > 0) {
				mountains++;
				k18 += m[4] <= 1200 ? 1 : 0;
			}
			t3 += m[8];
			t3e += m[9];
			s1 += m[10];
			s2 += m[11];
			s3 += m[12];
			sign3 += m[13];
			pairs += m[14];
			density += m[15];
		}
		Collections.sort(t1);
		Collections.sort(t1s);
		return String.format(Locale.ROOT, "%s (%d worlds): T1 within 640 %.0f %% median %.0f (structures only %.0f %%, median %.0f); T2 16<=800 %.0f %%, "
				+ "17<=640 %.0f %%, 20<=900 %.0f %%, 18<=1200 %.0f %% of %d with mountains; T3 sign %.0f %%, sign or crumb %.0f %%; T4 %.0f / %.0f / %.0f %%, "
				+ "sign by 3 %.0f %%; T6 near pairs %.1f a world, density %.2f per km2",
				name, n, 100.0 * t1in / n, t1.get(n / 2), 100.0 * t1sIn / n, t1s.get(n / 2), 100.0 * k16 / n, 100.0 * k17 / n, 100.0 * k20 / n,
				mountains == 0 ? 0 : 100.0 * k18 / mountains, mountains, 100 * t3 / n, 100 * t3e / n, 100 * s1 / n, 100 * s2 / n, 100 * s3 / n, 100 * sign3 / n,
				pairs / n, density / n);
	}

	private static void write(List<String> lines) {
		try {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
			java.nio.file.Files.write(java.nio.file.Path.of("build", "sc3find.txt"), lines);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3find: not written: " + e);
		}
		for (String line : lines) {
			Sc2RealNoise.log(line);
		}
	}

	// --- the kit's costs ---

	/**
	 * The kit's own costs, when asked ({@code -Da1016_02.sc3cost=true}): the lighting fault's fill of one chunk into an
	 * overlay over the real noise (16 x 16, the worst a chunk of a square holds), the marker placement's check (asked for
	 * every chunk worldgen makes), the avoid list's check (81 placement checks a candidate), a kept decision's read.
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void theKitsCostsWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED);
		List<String> out = new ArrayList<>();
		List<Long> fills = new ArrayList<>();
		Random r = new Random(7);
		for (int i = 0; i < 60; i++) {
			int x = (r.nextInt(400) - 200) * 16;
			int z = (r.nextInt(400) - 200) * 16;
			int ground = world.terrain().ground(x + 8, z + 8);
			BoundingBox square = Fault.square(x, z, 16, ground - 40, ground + 40);
			FaultDemo demo = new FaultDemo(square, List.of(square), null, 0, new BlockPos(x, ground, z), Direction.NORTH);
			int lowest = Heights.lowest(world.height(), world.generator());
			Overlay overlay = new Overlay(NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, square.minY()), square.maxY()), true);
			// The columns' read is the noise's (a shape pays it anyway, a chunk's own blocks are cheaper still): read once, then
			// time the fault alone.
			overlay.fill(demo, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
			long t = System.nanoTime();
			overlay.fill(demo, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
			fills.add(System.nanoTime() - t);
		}
		Collections.sort(fills);
		out.add(String.format(Locale.ROOT, "fault fill a chunk: median %.3f ms, worst %.3f ms", fills.get(fills.size() / 2) / 1e6, fills.getLast() / 1e6));
		StructurePlacement axes = level.getChunkSource().getGeneratorState().getPlacementsForStructure(
				level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,
						com.forzacode.a1016_02.A1016_02.id("world/s19")))).stream().findFirst().orElse(null);
		if (axes != null) {
			Snapshot.refresh(level.getServer());
			long t = System.nanoTime();
			int hits = 0;
			for (int i = 0; i < 200_000; i++) {
				hits += axes.isStructureChunk(level.getChunkSource().getGeneratorState(), i % 300, i / 300) ? 1 : 0;
			}
			out.add(String.format(Locale.ROOT, "marker placement check: %.0f ns a chunk (%d marks met)", (System.nanoTime() - t) / 200_000.0, hits));
		}
		Holder<Structure> s17 = Sc2RealNoise.structures(level).get("s17");
		if (s17 != null && s17.value() instanceof Sc2Structure sc2) {
			long t = System.nanoTime();
			int excluded = 0;
			for (int i = 0; i < 20_000; i++) {
				excluded += Decided.excluded(sc2, world.structures(), List.of(), new ChunkPos(i % 400 * 3, i / 400 * 3)) != null ? 1 : 0;
			}
			out.add(String.format(Locale.ROOT, "avoid check: %.1f us a candidate (%.0f %% avoided)", (System.nanoTime() - t) / 20_000.0 / 1e3,
					100.0 * excluded / 20_000));
		}
		long t = System.nanoTime();
		for (int i = 0; i < 1_000_000; i++) {
			Decided.get("s17", new ChunkPos(i, -i));
		}
		out.add(String.format(Locale.ROOT, "a decision's read: %.0f ns", (System.nanoTime() - t) / 1e6));
		for (String line : out) {
			Sc2RealNoise.log(line);
		}
		try {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
			java.nio.file.Files.write(java.nio.file.Path.of("build", "sc3cost.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3cost: not written");
		}
		helper.succeed();
	}
}
