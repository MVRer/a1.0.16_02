package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.kit.Writer;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Feature 19 in worldgen ({@code a1016_02:world/s19}, docs/SELF_CREATED_2.md, 19): one placed feature, run in every
 * overworld chunk at the last decoration step ({@code top_layer_modification}), so the chunk's own trees, lakes,
 * geodes, ores and the rest are in before it. It builds every segment's cells ({@link LineShape}) in the chunk being
 * decorated and in each of its eight neighbours that is not finished yet (a neighbour still being made can have had a
 * later feature of its own write into the segment; a finished one may hold a player's build and is never touched): so
 * once every chunk round a stretch is decorated, the last pass to run has put every cell back, whatever an earlier
 * neighbour's feature did. Only while the kit's snapshot allows (the switch, the stage at least {@code a}): land made
 * before then has none, and a segment begun in it has missing stretches. Records one place per segment (once a run; the
 * kit's data keeps one per start). A chunk away from every segment costs four box tests.
 *
 * <p>A segment is laid out from the config's numbers only until it first builds something in a world: then its whole
 * layout and its shape's version are fixed in the kit's {@link Plans} (R12-2), and every later decoration builds that,
 * whatever the config or a later jar says, so a segment never changes its openings or its cut halfway along.
 */
public record LineFeature() implements Feature {
	public static final MapCodec<LineFeature> CODEC = MapCodec.unit(LineFeature::new);

	/** The segments offered to the recorder in this run (by their start). */
	private static final Set<String> OFFERED = ConcurrentHashMap.newKeySet();
	/** A segment fixed in a world and the version of its shape. */
	record Plan(LinePlan.Segment segment, int version) {
	}

	/** Each segment's shape, made once a run (it keeps its openings' heights). */
	private static final Map<Plan, LineShape> SHAPES = new ConcurrentHashMap<>();

	@Override
	public MapCodec<LineFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ChunkPos chunk = ChunkPos.containing(origin);
		try {
			return decorate(level, generator, chunk, Snapshot.current()) > 0;
		} catch (RuntimeException e) {
			ScLog.error("[a1016] s2: s19 failed in chunk {}", chunk, e);
			return false;
		}
	}

	/** Why nothing is built now, or null: no snapshot, the switch off, no config, the stage under {@code a}. Any thread. */
	public static @Nullable String refusal(@Nullable Snapshot snapshot) {
		if (snapshot == null) {
			return "no snapshot";
		}
		if (!snapshot.enabled()) {
			return "switched off";
		}
		Sc19Config config = snapshot.config(S19.ID, Sc19Config.class);
		if (config == null) {
			return "no config";
		}
		if (snapshot.stage().level() < config.fromStage) {
			return "stage " + snapshot.stage().level() + " under " + config.fromStage;
		}
		if (!Plans.ready()) {
			return "the world's plans not read yet";
		}
		return null;
	}

	/**
	 * The segments of this level for this snapshot (none if it refuses): each one fixed in this world ({@link Plans}) as it
	 * was fixed, the others as the config lays them out now.
	 */
	public static List<LinePlan.Segment> segments(@Nullable Snapshot snapshot, int minY, int maxY) {
		if (refusal(snapshot) != null) {
			return List.of();
		}
		List<LinePlan.Segment> planned = LinePlan.of(snapshot.seed(), snapshot.salt(), snapshot.config(S19.ID, Sc19Config.class), minY, maxY);
		return withFixed(snapshot.seed(), snapshot.salt(), planned, minY, maxY);
	}

	/** {@code planned} with each segment fixed in this world in its place (one inside the level's height). */
	public static List<LinePlan.Segment> withFixed(long seed, long salt, List<LinePlan.Segment> planned, int minY, int maxY) {
		List<LinePlan.Segment> out = new ArrayList<>(planned.size());
		for (LinePlan.Segment s : planned) {
			LinePlan.Segment fixed = fixed(seed, salt, s.arm());
			out.add(fixed != null && fixed.bottom() >= minY + LinePlan.ABOVE_MIN && fixed.top() <= maxY - LinePlan.BELOW_MAX ? fixed : s);
		}
		return out;
	}

	/** The key one arm's segment of a world (its seed and worldgen salt) is fixed under. */
	public static String planKey(long seed, long salt, LinePlan.Arm arm) {
		return planPrefix(seed, salt) + arm.name();
	}

	/** The keys of a world's segments start with this. */
	public static String planPrefix(long seed, long salt) {
		return S19.ID + "/" + seed + "/" + salt + "/";
	}

	/** This world's fixed segment on {@code arm}, or null. Any thread. */
	public static LinePlan.@Nullable Segment fixed(long seed, long salt, LinePlan.Arm arm) {
		CompoundTag tag = Plans.fixed(planKey(seed, salt, arm));
		return tag == null ? null : LinePlan.fromTag(tag);
	}

	/** Fixes {@code segment} (as the current version) for this world unless its arm has one already; returns the one in force. Any thread. */
	static Plan fix(long seed, long salt, LinePlan.Segment segment) {
		CompoundTag kept = Plans.fix(planKey(seed, salt, segment.arm()), LinePlan.toTag(segment, LineShape.VERSION));
		LinePlan.Segment fixed = LinePlan.fromTag(kept);
		return fixed == null ? new Plan(segment, LineShape.VERSION) : new Plan(fixed, kept.getIntOr("v", 1));
	}

	/** Tests: this world's segments laid out from the config again (forgotten here, not in the world's data). */
	public static void forgetFixed(Snapshot snapshot) {
		Plans.forget(planPrefix(snapshot.seed(), snapshot.salt()));
	}

	/**
	 * Builds what falls in {@code center} and its unfinished neighbours; returns how many blocks changed. The overworld
	 * only. Writes only inside the chunks it may write in ({@code center} and its eight neighbours: the decoration step's
	 * own reach).
	 */
	public static int decorate(WorldGenLevel level, ChunkGenerator generator, ChunkPos center, @Nullable Snapshot snapshot) {
		if (!level.getLevel().dimension().equals(Level.OVERWORLD)) {
			return 0;
		}
		List<LinePlan.Segment> segments = segments(snapshot, level.getMinY(), level.getMaxY());
		int written = 0;
		Terrain terrain = null;
		for (LinePlan.Segment planned : segments) {
			if (!near(planned, center)) {
				continue;
			}
			// Fixed for this world the first time it builds anything (R12-2): a later config never moves it.
			Plan plan = fix(snapshot.seed(), snapshot.salt(), planned);
			LinePlan.Segment segment = plan.segment();
			if (!segment.equals(planned) && !near(segment, center)) {
				continue;
			}
			if (terrain == null) {
				terrain = Terrains.noise(generator, level.getLevel().getChunkSource().randomState(), level);
			}
			LineShape shape = shape(plan, terrain);
			BoundingBox box = shape.box();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					ChunkPos chunk = new ChunkPos(center.x() + dx, center.z() + dz);
					if (!segment.touches(chunk) || (dx != 0 || dz != 0) && finished(level, chunk)) {
						continue;
					}
					BoundingBox column = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
							chunk.getMaxBlockZ());
					Optional<BoundingBox> clip = Draw.intersection(column, box);
					if (clip.isEmpty()) {
						continue;
					}
					Writer w = Writer.worldgen(level, clip.get(), terrain);
					shape.fill(w, clip.get());
					written += w.written();
				}
			}
			offer(level, segment, shape);
		}
		return written;
	}

	/** True if the segment touches this chunk or one of its eight neighbours. */
	static boolean near(LinePlan.Segment segment, ChunkPos center) {
		BoundingBox box = segment.box().inflatedBy(LineShape.REACH, 0, LineShape.REACH);
		int x0 = (center.x() - 1) << 4;
		int z0 = (center.z() - 1) << 4;
		int x1 = ((center.x() + 2) << 4) - 1;
		int z1 = ((center.z() + 2) << 4) - 1;
		return box.minX() <= x1 && box.maxX() >= x0 && box.minZ() <= z1 && box.maxZ() >= z0;
	}

	/** True if this neighbour is a finished chunk (a player may have stood and built in it). */
	static boolean finished(WorldGenLevel level, ChunkPos chunk) {
		ChunkAccess access = level.getChunk(chunk.x(), chunk.z());
		return access == null || access.getPersistedStatus() == ChunkStatus.FULL;
	}

	/** The segment's shape (made once a run). */
	static LineShape shape(Plan plan, Terrain terrain) {
		return SHAPES.computeIfAbsent(plan, p -> new LineShape(p.segment(), terrain, p.version()));
	}

	private static void offer(WorldGenLevel level, LinePlan.Segment segment, LineShape shape) {
		String key = segment.start().origin().asLong() + "/" + segment.seed();
		if (OFFERED.add(key)) {
			Recorder.offer(entry(level.getLevel().dimension(), segment, shape));
		}
	}

	/** The record of a segment: its box is its plane (where one stands inside it: an opening or the cut). */
	public static Recorder.Entry entry(ResourceKey<Level> dimension, LinePlan.Segment segment, LineShape shape) {
		Start start = segment.start();
		Remains.Place place = new Remains.Place(Remains.Kind.LINE, dimension, segment.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(),
				Optional.empty(), start.seed(), start.variant());
		return new Recorder.Entry(S19.ID, start, place, new CompoundTag());
	}

	/** Server stop: the next run offers and measures again. */
	public static void clear() {
		OFFERED.clear();
		SHAPES.clear();
	}
}
