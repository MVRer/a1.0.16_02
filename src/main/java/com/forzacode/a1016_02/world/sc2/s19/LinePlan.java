package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where feature 19's segments lie in a world and how each is laid out (docs/SELF_CREATED_2.md, 19; pure, any thread).
 * One segment on each half-axis (x = 0 running south and north, z = 0 running east and west), from {@code b} plus up to
 * {@code c} out, {@code d} to {@code e} long, all of it decided by the world's seed and worldgen salt.
 *
 * <p>A segment is laid out on the world's own grid: a rib on every chunk edge along it (three thick, five on every
 * multiple of {@value #MAJOR}), a panel one block thick between each two; it begins and ends on a rib, and its last two to
 * four bays at each end are ribs alone, the panels gone. It stands from {@value #ABOVE_MIN} above the level's lowest
 * block (clear of the bedrock layers) to {@value #BELOW_MAX} under its highest, read from the level (a datapack's height
 * moves it, never clips it); a level too short for {@value #MIN_HEIGHT} of it has none. The openings (two wide, two
 * high, at the foot) are in the middle of every {@code f / 16}th bay; the cut, {@code g} wide from top to bottom, is
 * centred on a rib in the middle half, so it goes through the grid, not between it.
 */
public final class LinePlan {
	/** The lowest cell stands this far above the level's lowest block (the bedrock layers are its lowest 5). */
	public static final int ABOVE_MIN = 6;
	/** The highest cell stands this far under the level's highest block. */
	public static final int BELOW_MAX = 6;
	/** A level whose span between those is shorter than this gets no segments. */
	public static final int MIN_HEIGHT = 64;
	/** The grid: a rib every this many blocks (the chunk's side). */
	public static final int BAY = 16;
	/** A heavier rib every this many blocks. */
	public static final int MAJOR = 128;
	/** Openings keep this far from a panel's ends and from the cut. */
	public static final int CLEAR = 6;
	/** The shortest segment (four bays of panels and the bare ends). */
	public static final int MIN_LENGTH = 9 * BAY;
	/** The salt of this feature's hashes. */
	private static final long SALT = 0x19A2E5L;

	/** The four half-axes, by the way they run out from the origin. */
	public enum Arm {
		SOUTH(Direction.SOUTH), NORTH(Direction.NORTH), EAST(Direction.EAST), WEST(Direction.WEST);

		public final Direction out;

		Arm(Direction out) {
			this.out = out;
		}

		/** True if it runs along X (its plane is z = 0). */
		public boolean alongX() {
			return out.getAxis() == Direction.Axis.X;
		}

		/** +1 if it runs toward positive coordinates. */
		public int sign() {
			return out.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
		}
	}

	/**
	 * One segment.
	 *
	 * @param arm      its half-axis
	 * @param plane    the coordinate across it (0 in a world; the gallery and tests build one elsewhere)
	 * @param t0       its first cell along its axis (the lower coordinate; a rib)
	 * @param t1       its last (inclusive; a rib)
	 * @param p0       the first rib with panels after it (the bays before it are ribs alone)
	 * @param p1       the last rib with panels before it
	 * @param bottom   its lowest cell
	 * @param top      its highest cell
	 * @param gapStart the cut's first cell along the axis (meaningless when {@code gapWidth} is 0)
	 * @param gapWidth the cut's width (0: none)
	 * @param holes    the openings' first cells along the axis (each two wide: {@code t} and {@code t + 1}), ascending
	 * @param seed     its own seed
	 */
	public record Segment(Arm arm, int plane, int t0, int t1, int p0, int p1, int bottom, int top, int gapStart, int gapWidth, List<Integer> holes,
			long seed) {
		public Segment {
			holes = List.copyOf(holes);
		}

		public int length() {
			return t1 - t0 + 1;
		}

		public int height() {
			return top - bottom + 1;
		}

		public int x(int t) {
			return arm.alongX() ? t : plane;
		}

		public int z(int t) {
			return arm.alongX() ? plane : t;
		}

		/** The cell at {@code t} along and {@code a} across the plane's coordinate. */
		public int x(int t, int a) {
			return arm.alongX() ? t : a;
		}

		public int z(int t, int a) {
			return arm.alongX() ? a : t;
		}

		/** Its plane's cells: one block thick, from {@link #bottom} to {@link #top}. */
		public BoundingBox box() {
			return BoundingBox.fromCorners(new BlockPos(x(t0), bottom, z(t0)), new BlockPos(x(t1), top, z(t1)));
		}

		/** True if a rib stands at {@code t} (cut away in the gap). */
		public boolean rib(int t) {
			return t >= t0 && t <= t1 && Math.floorMod(t, BAY) == 0;
		}

		/** How far a rib at {@code t} reaches out of the plane on each side: 2 on a multiple of {@value #MAJOR}, 1 on others, 0 off one. */
		public int ribReach(int t) {
			return !rib(t) ? 0 : Math.floorMod(t, MAJOR) == 0 ? 2 : 1;
		}

		/** True if a panel stands at {@code t} (between the bare ends; cut away in the gap). */
		public boolean panel(int t) {
			return t >= p0 && t <= p1;
		}

		public boolean inGap(int t) {
			return gapWidth > 0 && t >= gapStart && t < gapStart + gapWidth;
		}

		/** The middle of the cut along the axis (the middle of the segment without one). */
		public int gapMiddle() {
			return gapWidth > 0 ? gapStart + gapWidth / 2 : (t0 + t1) / 2;
		}

		/** The along-axis cell nearest the origin. */
		public int near() {
			return arm.sign() > 0 ? t0 : t1;
		}

		/** The opening nearest the origin (its first cell), or the panels' near end without any. */
		public int firstHole() {
			if (holes.isEmpty()) {
				return arm.sign() > 0 ? p0 : p1;
			}
			return arm.sign() > 0 ? holes.getFirst() : holes.getLast();
		}

		/** True if this segment's cells (its ribs' reach included) touch this chunk. */
		public boolean touches(ChunkPos chunk) {
			BoundingBox box = box().inflatedBy(LineShape.LIT_REACH, 0, LineShape.LIT_REACH);
			return box.minX() <= chunk.getMaxBlockX() && box.maxX() >= chunk.getMinBlockX() && box.minZ() <= chunk.getMaxBlockZ()
					&& box.maxZ() >= chunk.getMinBlockZ();
		}

		/**
		 * The start it is recorded under: its cell nearest the origin at its bottom, running out along its arm, its length
		 * (low 16 bits) and height (high bits) as the variant, its seed ({@link LineShape#fromStart} builds it back).
		 */
		public Start start() {
			int n = near();
			return new Start(new BlockPos(x(n), bottom, z(n)), arm.out, (length() & 0xFFFF) | (height() << 16), seed);
		}
	}

	private LinePlan() {
	}

	/**
	 * A segment's whole layout as a tag (R12-2: what the kit's {@code Plans} fix on its first decoration), with the
	 * version of {@link LineShape} it is built with.
	 */
	public static CompoundTag toTag(Segment s, int version) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", version);
		tag.putString("arm", s.arm().name());
		tag.putInt("plane", s.plane());
		tag.putInt("t0", s.t0());
		tag.putInt("t1", s.t1());
		tag.putInt("p0", s.p0());
		tag.putInt("p1", s.p1());
		tag.putInt("b", s.bottom());
		tag.putInt("t", s.top());
		tag.putInt("gs", s.gapStart());
		tag.putInt("gw", s.gapWidth());
		tag.putIntArray("h", s.holes().stream().mapToInt(Integer::intValue).toArray());
		tag.putLong("s", s.seed());
		return tag;
	}

	/** The segment of {@link #toTag}, or null if the tag does not hold one. */
	public static @Nullable Segment fromTag(CompoundTag tag) {
		Arm arm;
		try {
			arm = Arm.valueOf(tag.getStringOr("arm", ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
		int t0 = tag.getIntOr("t0", 0);
		int t1 = tag.getIntOr("t1", -1);
		int bottom = tag.getIntOr("b", 0);
		int top = tag.getIntOr("t", -1);
		if (t1 < t0 || top < bottom) {
			return null;
		}
		List<Integer> holes = new ArrayList<>();
		for (int hole : tag.getIntArray("h").orElse(new int[0])) {
			holes.add(hole);
		}
		return new Segment(arm, tag.getIntOr("plane", 0), t0, t1, tag.getIntOr("p0", t0), tag.getIntOr("p1", t1), bottom, top, tag.getIntOr("gs", t0),
				tag.getIntOr("gw", 0), holes, tag.getLongOr("s", 0L));
	}

	/**
	 * The segments of a world: one per half-axis (none if the level's height cannot hold {@value #MIN_HEIGHT}).
	 *
	 * @param worldSeed the world's seed
	 * @param salt      the world's own worldgen salt (so two worlds of one seed differ)
	 * @param minY      the level's lowest block
	 * @param maxY      the level's highest block
	 */
	public static List<Segment> of(long worldSeed, long salt, Sc19Config config, int minY, int maxY) {
		int bottom = minY + ABOVE_MIN;
		int top = maxY - BELOW_MAX;
		List<Segment> out = new ArrayList<>(4);
		if (top - bottom + 1 < MIN_HEIGHT) {
			return out;
		}
		for (Arm arm : Arm.values()) {
			out.add(segment(arm, 0, Hash.of(worldSeed, salt, SALT, arm.ordinal()), config, bottom, top));
		}
		return out;
	}

	/** One segment on {@code arm}, its plane at {@code plane}, from its own seed (the gallery and tests build them too). */
	public static Segment segment(Arm arm, int plane, long seed, Sc19Config config, int bottom, int top) {
		int startMin = Math.max(0, config.startMin);
		int spread = Math.max(0, config.startSpread);
		int lengthMin = Math.max(MIN_LENGTH, config.lengthMin);
		int lengthMax = Math.max(lengthMin, config.lengthMax);
		int from = startMin + Hash.below(Hash.of(seed, 1), spread + 1);
		int length = Hash.between(Hash.of(seed, 2), lengthMin, lengthMax);
		int t0 = arm.sign() > 0 ? from : -(from + length - 1);
		return cut(arm, plane, t0, length, seed, config.holeEvery, config.gapWidth, bottom, top);
	}

	/**
	 * A segment over {@code t0} to {@code t0 + length - 1} (ascending; drawn in to the ribs inside it), its openings every
	 * {@code holeEvery} and its cut {@code gapWidth} wide placed by its seed.
	 */
	public static Segment cut(Arm arm, int plane, int from, int length, long seed, int holeEvery, int gapWidth, int bottom, int top) {
		int t0 = Math.ceilDiv(from, BAY) * BAY;
		int t1 = Math.floorDiv(from + Math.max(MIN_LENGTH, length) - 1, BAY) * BAY;
		int bays = (t1 - t0) / BAY;
		int bare0 = 2 + Hash.below(Hash.of(seed, 5), 3);
		int bare1 = 2 + Hash.below(Hash.of(seed, 6), 3);
		if (bare0 + bare1 + 4 > bays) {
			bare0 = Math.max(0, (bays - 4) / 2);
			bare1 = bare0;
		}
		int p0 = t0 + bare0 * BAY;
		int p1 = t1 - bare1 * BAY;
		// The cut: centred on a rib in the middle half of the panels.
		int width = Math.clamp(gapWidth, 0, 64);
		int gapStart = p0;
		if (width > 0) {
			int panelBays = (p1 - p0) / BAY;
			int first = (panelBays + 2) / 4;
			int last = panelBays - first;
			int room = last - first;
			if (room < 0 || width + 2 * CLEAR > (p1 - p0)) {
				width = 0;
			} else {
				int rib = p0 + (first + Hash.below(Hash.of(seed, 3), room + 1)) * BAY;
				gapStart = rib - width / 2;
			}
		}
		List<Integer> holes = new ArrayList<>();
		int every = holeEvery <= 0 ? 0 : Math.max(8, holeEvery);
		if (every > 0 && every % BAY == 0) {
			// In the middle of every (every / 16)th bay.
			int step = every / BAY;
			int phase = Hash.below(Hash.of(seed, 4), step);
			for (int bay = phase; p0 + bay * BAY + BAY <= p1; bay += step) {
				int at = p0 + bay * BAY + BAY / 2 - 1;
				if (!near(at, width, gapStart)) {
					holes.add(at);
				}
			}
		} else if (every > 0) {
			int phase = Hash.below(Hash.of(seed, 4), every);
			for (int t = p0 + CLEAR + phase; t + 1 <= p1 - CLEAR; t += every) {
				// Never across a chunk's edge (both cells decided in one chunk) nor on a rib.
				int at = Math.floorMod(t, BAY) == BAY - 1 ? t - 1 : Math.floorMod(t, BAY) == 0 ? t + 1 : t;
				if (at >= p0 + CLEAR && at + 1 <= p1 - CLEAR && !near(at, width, gapStart)) {
					holes.add(at);
				}
			}
		}
		return new Segment(arm, plane, t0, t1, p0, p1, bottom, top, gapStart, width, holes, seed);
	}

	/** True if an opening at {@code at} would stand within {@link #CLEAR} of the cut. */
	private static boolean near(int at, int width, int gapStart) {
		return width > 0 && at + 1 >= gapStart - CLEAR && at <= gapStart + width - 1 + CLEAR;
	}

	// --- clear of the places (FIX-R3-E) ---

	/** The line keeps this far (blocks, across x and z) from every other place's box: its footprint never comes nearer. */
	public static final int CLEARANCE = 32;
	/**
	 * How far a place's box may reach from its start's chunk (blocks): the places asked about are those whose start chunk
	 * lies within this, the clearance and the lit foot of the segment (measured, {@code SelfCreated2LineTests}: at most 128
	 * over 297 places of 12 worlds; every candidate asked is sited, once a world).
	 */
	public static final int PLACE_REACH = 192;

	/** Where the decided places stand: the boxes of every place whose start chunk lies in a rectangle of chunks (inclusive). */
	@FunctionalInterface
	public interface Places {
		/** No place anywhere (a test's own segments, a level with none). */
		Places NONE = (c0x, c0z, c1x, c1z) -> List.of();

		List<BoundingBox> in(int c0x, int c0z, int c1x, int c1z);
	}

	/** The segment's cells across x and z: its plane with its lit foot either side ({@link LineShape#LIT_REACH}), every height. */
	public static BoundingBox footprint(Segment s) {
		int reach = LineShape.LIT_REACH;
		return s.box().inflatedBy(s.arm().alongX() ? 0 : reach, 0, s.arm().alongX() ? reach : 0);
	}

	/**
	 * {@code s} kept clear of the places worldgen decides along it ({@link #clear(Segment, List, Sc19Config)}), asking
	 * {@code places} for those whose start chunk lies within {@link #PLACE_REACH} and {@link #CLEARANCE} of its footprint.
	 */
	public static @Nullable Segment clear(Segment s, Places places, Sc19Config config) {
		BoundingBox look = footprint(s).inflatedBy(CLEARANCE + PLACE_REACH, 0, CLEARANCE + PLACE_REACH);
		return clear(s, places.in(look.minX() >> 4, look.minZ() >> 4, look.maxX() >> 4, look.maxZ() >> 4), config);
	}

	/** A stretch left this long or longer is taken before a longer one farther out: half the config's shortest segment. */
	public static int preferred(Sc19Config config) {
		return Math.max(MIN_LENGTH, config.lengthMin / 2);
	}

	/**
	 * {@code s} (laid out with {@code config}) kept clear of these boxes (FIX-R3-E: the line ran through the real seed's
	 * archive, its obsidian in 5 124 of the archive's cells): {@code s} itself if no box comes within {@link #CLEARANCE} of
	 * its {@link #footprint}. Else it is laid out again over one stretch of its own between the places (ribs alone at its
	 * ends, its cut in the middle half, its openings on the same rule), as it ends anywhere else: the nearest stretch to
	 * the origin at least {@link #preferred} long (so it keeps its near end and ends short of the first place in its way
	 * when that leaves enough of it), else the longest stretch of at least eight bays (the shortest segment), else none
	 * (that half-axis has no line in this world). It never bends off its axis: a straight wall exactly on zero that stops
	 * where a place stands.
	 */
	public static @Nullable Segment clear(Segment s, List<BoundingBox> boxes, Sc19Config config) {
		boolean alongX = s.arm().alongX();
		int across0 = s.plane() - LineShape.LIT_REACH;
		int across1 = s.plane() + LineShape.LIT_REACH;
		List<int[]> blocked = new ArrayList<>();
		for (BoundingBox b : boxes) {
			int a0 = (alongX ? b.minZ() : b.minX()) - CLEARANCE;
			int a1 = (alongX ? b.maxZ() : b.maxX()) + CLEARANCE;
			int b0 = (alongX ? b.minX() : b.minZ()) - CLEARANCE;
			int b1 = (alongX ? b.maxX() : b.maxZ()) + CLEARANCE;
			if (a1 >= across0 && a0 <= across1 && b1 >= s.t0() && b0 <= s.t1()) {
				blocked.add(new int[] {b0, b1});
			}
		}
		if (blocked.isEmpty()) {
			return s;
		}
		blocked.sort((p, q) -> Integer.compare(p[0], q[0]));
		List<int[]> free = new ArrayList<>();
		int from = s.t0();
		for (int[] b : blocked) {
			if (b[0] > from) {
				free.add(new int[] {from, b[0] - 1});
			}
			from = Math.max(from, b[1] + 1);
		}
		if (from <= s.t1()) {
			free.add(new int[] {from, s.t1()});
		}
		if (s.arm().sign() < 0) {
			java.util.Collections.reverse(free);
		}
		// Nearest the origin first; on the grid, its ends on ribs inside the stretch.
		int prefer = preferred(config);
		int[] longest = null;
		for (int[] f : free) {
			int t0 = Math.ceilDiv(f[0], BAY) * BAY;
			int t1 = Math.floorDiv(f[1], BAY) * BAY;
			if (t1 - t0 < MIN_LENGTH - BAY) {
				continue;
			}
			if (t1 - t0 + 1 >= prefer) {
				return cut(s.arm(), s.plane(), t0, t1 - t0 + 1, s.seed(), config.holeEvery, config.gapWidth, s.bottom(), s.top());
			}
			if (longest == null || t1 - t0 > longest[1] - longest[0]) {
				longest = new int[] {t0, t1};
			}
		}
		return longest == null ? null
				: cut(s.arm(), s.plane(), longest[0], longest[1] - longest[0] + 1, s.seed(), config.holeEvery, config.gapWidth, s.bottom(), s.top());
	}
}
