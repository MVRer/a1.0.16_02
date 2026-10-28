package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Feature 32's lines (docs/SELF_CREATED_2_R3.md, "32", "Lines"): where they run and where each crumb stands before its
 * footing is looked for. Pure geometry, any thread: from a place's sign point, line A toward the world spawn and lines B
 * and C at plus and minus 100 to 140 degrees from it (a hash of the place's seed); from each of 19's cuts, one line on
 * each side of the wall. A line stops {@value #STOP_MARGIN} short of another place's box and where it first comes
 * within {@code b} of the world spawn, is cut where its own crumbs could no longer be followed over the land (the
 * {@link Model}: a gap past twice its tier's spacing plus 16), and is dropped under {@value #MIN_LENGTH}. Crumbs stand
 * every {@code d} from {@value #FIRST} to {@code g} (near), every {@code e} to {@code h} (mid, dead straight), then every
 * {@code f} give or take {@value #FAR_WOBBLE}, up to {@value #FAR_ACROSS} to either side (far). A crumb inside its own
 * place's box is left out (the place stands there).
 */
public final class CrumbLines {
	/**
	 * The layout's and the shapes' version (R12-2): a fixed line keeps the one it was fixed with. 2 (FIX-R3-C, R3A-7):
	 * each crumb takes the highest footing among its shifts ({@link CrumbFooting#RISES_FROM}), a far one along up to
	 * {@value CrumbFooting#FAR_SHIFT}, and every second mid crumb is crowned.
	 */
	public static final int VERSION = 2;
	/** The first crumb's distance from the sign (under it the place's own sign shows). */
	public static final int FIRST = 64;
	/** A line shorter than this is dropped. */
	public static final int MIN_LENGTH = 128;
	/** A far crumb's spacing varies this much either way. */
	public static final int FAR_WOBBLE = 8;
	/** Version 2: the far tier closer (FIX-R3-C, the sight on real land), its wobble halved so two far crumbs do not bunch. */
	public static final int FAR_WOBBLE_V2 = 4;
	/** A far crumb stands up to this far to either side of the line. */
	public static final int FAR_ACROSS = 6;
	/** A line stops this short of another place's box. */
	public static final int STOP_MARGIN = 16;
	/** A crumb this near its own place's box is left out. */
	public static final int OWN_MARGIN = 4;
	/** The step a line is walked in for its stops. */
	static final int STEP = 4;
	/** 19's lines are this long. */
	public static final int WALL_LINE = 512;

	private CrumbLines() {
	}

	public enum Tier {
		NEAR, MID, FAR
	}

	/**
	 * Where lines start.
	 *
	 * @param kind  the place's kind id ({@code s16}, {@code s16d}, {@code s17}, {@code s18}, {@code s19}, {@code s20})
	 * @param id    the place's own key (its kind and candidate chunk, or 19's arm)
	 * @param x     the sign point
	 * @param z     the sign point
	 * @param own   the place's box (its crumbs stay out of it)
	 * @param seed  the place's seed
	 * @param fixed for 19: the one bearing of each of its two lines, else null (A toward the spawn, B and C turned)
	 */
	public record From(String kind, String id, int x, int z, BoundingBox own, long seed, double @Nullable [] fixed) {
	}

	/**
	 * One line, outward from its place.
	 *
	 * @param key     its key in the world (fixed under it, R12-2)
	 * @param kind    its place's kind id
	 * @param x       where it starts (the sign point)
	 * @param z       where it starts
	 * @param angle   its bearing outward (radians, {@code atan2(dz, dx)})
	 * @param length  how far out its last crumb may stand
	 * @param version the layout's version it was laid out with
	 * @param seed    its own seed (far crumbs' wobble, the lights)
	 * @param own     its place's box, flat (min x, min z, max x, max z)
	 * @param tiers   near to, mid to, near spacing, mid spacing, far spacing
	 */
	public record Line(String key, String kind, int x, int z, double angle, int length, int version, long seed, int[] own, int[] tiers) {
		public double ux() {
			return Math.cos(angle);
		}

		public double uz() {
			return Math.sin(angle);
		}

		/** The way back toward its place, as a horizontal direction. */
		public Direction toPlace() {
			return Direction.getApproximateNearest(-ux(), 0, -uz());
		}

		public int nearTo() {
			return tiers[0];
		}

		public int midTo() {
			return tiers[1];
		}

		/** The spacing of a tier. */
		public int spacing(Tier tier) {
			return switch (tier) {
				case NEAR -> tiers[2];
				case MID -> tiers[3];
				case FAR -> tiers[4];
			};
		}

		/** The longest gap one may meet following it in this tier (twice the spacing plus 16: T5). */
		public int maxGap(Tier tier) {
			return 2 * spacing(tier) + 16;
		}

		/** True if (x, z) lies within {@code margin} of its place's box, flat. */
		public boolean inOwn(int px, int pz, int margin) {
			return px >= own[0] - margin && px <= own[2] + margin && pz >= own[1] - margin && pz <= own[3] + margin;
		}

		/** Its crumbs before footing, nearest the place first (those in its own box left out). */
		public List<Spot> spots() {
			List<Spot> out = new ArrayList<>();
			double ux = ux();
			double uz = uz();
			int index = 0;
			int k = 0;
			int d = FIRST;
			for (; d < nearTo() && d <= length; d += spacing(Tier.NEAR)) {
				add(out, index++, Tier.NEAR, k++, d, 0, ux, uz);
			}
			k = 0;
			for (; d < midTo() && d <= length; d += spacing(Tier.MID)) {
				add(out, index++, Tier.MID, k++, d, 0, ux, uz);
			}
			k = 0;
			while (true) {
				long h = mix(seed, 31L * k + 7);
				int wob = version >= CrumbFooting.RISES_FROM ? FAR_WOBBLE_V2 : FAR_WOBBLE;
				int wobble = k == 0 ? (int) Math.floorMod(h, wob + 1) : (int) Math.floorMod(h, 2 * wob + 1) - wob;
				int across = (int) Math.floorMod(h >>> 20, 2 * FAR_ACROSS + 1) - FAR_ACROSS;
				int fd = Math.max(d, midTo()) + k * spacing(Tier.FAR) + wobble;
				if (fd > length) {
					break;
				}
				add(out, index++, Tier.FAR, k++, fd, across, ux, uz);
			}
			return out;
		}

		private void add(List<Spot> out, int index, Tier tier, int k, int d, int across, double ux, double uz) {
			int px = (int) Math.floor(x + 0.5 + ux * d - uz * across);
			int pz = (int) Math.floor(z + 0.5 + uz * d + ux * across);
			if (!inOwn(px, pz, OWN_MARGIN)) {
				out.add(new Spot(this, index, tier, k, d, across, px, pz));
			}
		}

		/** True if its crumbs could stand in this chunk (its span, with the wobble, the shifts and a crumb's reach). */
		public boolean reaches(ChunkPos chunk, int margin) {
			double ux = ux();
			double uz = uz();
			double x0 = x + ux * FIRST;
			double z0 = z + uz * FIRST;
			double x1 = x + ux * (length + FAR_WOBBLE);
			double z1 = z + uz * (length + FAR_WOBBLE);
			double cx = chunk.getMinBlockX() + 7.5;
			double cz = chunk.getMinBlockZ() + 7.5;
			double dx = x1 - x0;
			double dz = z1 - z0;
			double len2 = dx * dx + dz * dz;
			double t = len2 == 0 ? 0 : Math.clamp(((cx - x0) * dx + (cz - z0) * dz) / len2, 0, 1);
			double ex = x0 + t * dx - cx;
			double ez = z0 + t * dz - cz;
			double r = 11.4 + margin;
			return ex * ex + ez * ez <= r * r;
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("k", kind);
			tag.putInt("x", x);
			tag.putInt("z", z);
			tag.putDouble("a", angle);
			tag.putInt("l", length);
			tag.putInt("v", version);
			tag.putLong("s", seed);
			tag.putIntArray("o", own.clone());
			tag.putIntArray("t", tiers.clone());
			return tag;
		}

		public static @Nullable Line fromTag(String key, CompoundTag tag) {
			int[] own = tag.getIntArray("o").orElse(new int[0]);
			int[] tiers = tag.getIntArray("t").orElse(new int[0]);
			if (own.length != 4 || tiers.length != 5 || tiers[2] <= 0 || tiers[3] <= 0 || tiers[4] <= 0) {
				return null;
			}
			return new Line(key, tag.getStringOr("k", ""), tag.getIntOr("x", 0), tag.getIntOr("z", 0), tag.getDoubleOr("a", 0), tag.getIntOr("l", 0),
					tag.getIntOr("v", 1), tag.getLongOr("s", 0L), own, tiers);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Line l && l.key.equals(key) && l.kind.equals(kind) && l.x == x && l.z == z && l.angle == angle && l.length == length
					&& l.version == version && l.seed == seed && java.util.Arrays.equals(l.own, own) && java.util.Arrays.equals(l.tiers, tiers);
		}

		@Override
		public int hashCode() {
			return key.hashCode() * 31 + length;
		}
	}

	/**
	 * One crumb before its footing.
	 *
	 * @param index  its number along its line (0 nearest the place)
	 * @param k      its number within its tier
	 * @param d      its distance from the sign along the line
	 * @param across how far to one side it stands (far only)
	 * @param x      where it would stand
	 * @param z      where it would stand
	 */
	public record Spot(Line line, int index, Tier tier, int k, int d, int across, int x, int z) {
		/** A far crumb with its light at night (every other one). */
		public boolean lit() {
			return tier == Tier.FAR && k % 2 == 0;
		}

		/** A mid crumb crowned with glowstone (every third one, the first included; version 2: every second one). */
		public boolean crowned() {
			return tier == Tier.MID && k % (line.version() >= CrumbFooting.RISES_FROM ? 2 : 3) == 0;
		}
	}

	/** What a line's crumbs are tested against before it is fixed: whether a crumb finds footing over the land. */
	@FunctionalInterface
	public interface Model {
		boolean allows(Spot spot);
	}

	// --- laying out ---

	/** A place's box or another obstacle a line stops at (flat). */
	public record Box(int minX, int minZ, int maxX, int maxZ) {
		public static Box of(BoundingBox b) {
			return new Box(b.minX(), b.minZ(), b.maxX(), b.maxZ());
		}

		boolean contains(double x, double z, int margin) {
			return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
		}
	}

	/** The key a line is fixed under in a world. */
	public static String key(long worldSeed, long salt, String placeId, char letter) {
		return prefix(worldSeed, salt) + placeId + "/" + letter;
	}

	/** Every line's key in a world starts with this. */
	public static String prefix(long worldSeed, long salt) {
		return "s32/" + Long.toString(worldSeed, 36) + "/" + Long.toString(salt, 36) + "/";
	}

	/**
	 * The lines of one place: A, B and C (or 19's two), each stopped and cut as the class says, those under
	 * {@value #MIN_LENGTH} dropped. {@code others} are every other place's box near it (its own is left out by the
	 * caller), {@code model} null to skip the cut over the land.
	 */
	public static List<Line> plan(From from, List<Box> others, int spawnX, int spawnZ, Sc32Config c, long worldSeed, long salt,
			@Nullable Model model) {
		double[] bearings;
		if (from.fixed() != null) {
			bearings = from.fixed();
		} else {
			double a = Math.atan2(spawnZ - from.z(), spawnX - from.x());
			double turn = Math.toRadians(100 + Math.floorMod(mix(from.seed(), 0x32B), 41));
			bearings = new double[] {a, a + turn, a - turn};
		}
		int count = from.fixed() != null ? bearings.length : Math.clamp(c.linesPerPlace, 0, 3);
		int max = from.fixed() != null ? WALL_LINE : c.lineMax;
		int[] own = {from.own().minX(), from.own().minZ(), from.own().maxX(), from.own().maxZ()};
		int[] tiers = {c.nearTo, c.midTo, Math.max(4, c.nearSpacing), Math.max(4, c.midSpacing), Math.max(8, c.farSpacing)};
		List<Line> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double angle = bearings[i];
			int length = stop(from, angle, max, others, spawnX, spawnZ, c.spawnClear);
			Line line = new Line(key(worldSeed, salt, from.id(), (char) ('A' + i)), from.kind(), from.x(), from.z(), angle, length, VERSION,
					mix(from.seed(), 0x5EED + i), own, tiers);
			if (model != null) {
				line = cut(line, model);
			}
			if (line.length() >= MIN_LENGTH) {
				out.add(line);
			}
		}
		return out;
	}

	/** How far a line runs before it meets another place's box or comes near the spawn (blocks, at most {@code max}). */
	static int stop(From from, double angle, int max, List<Box> others, int spawnX, int spawnZ, int spawnClear) {
		double ux = Math.cos(angle);
		double uz = Math.sin(angle);
		double clear2 = (double) spawnClear * spawnClear;
		for (int d = 0; d <= max + FAR_WOBBLE; d += STEP) {
			double px = from.x() + 0.5 + ux * d;
			double pz = from.z() + 0.5 + uz * d;
			double sx = px - spawnX;
			double sz = pz - spawnZ;
			boolean stop = sx * sx + sz * sz < clear2;
			for (int i = 0; !stop && i < others.size(); i++) {
				stop = others.get(i).contains(px, pz, STOP_MARGIN);
			}
			if (stop) {
				return Math.min(max, d - STEP - FAR_WOBBLE - FAR_ACROSS);
			}
		}
		return max;
	}

	/**
	 * The line cut where it could no longer be followed over the land: walking out from the sign (its own box counting as
	 * the place), at the first crumb that finds footing more than its tier's longest gap past the last one that did (the
	 * first may be {@value #FIRST} plus the near tier's longest gap from the sign), the line ends at the last one.
	 */
	public static Line cut(Line line, Model model) {
		int last = 0;
		boolean any = false;
		for (Spot s : line.spots()) {
			if (!model.allows(s)) {
				continue;
			}
			int allowed = any ? line.maxGap(s.tier()) : FIRST + line.maxGap(Tier.NEAR);
			if (s.d() - (any ? last : lastOwn(line, s)) > allowed) {
				return withLength(line, any ? last : 0);
			}
			last = s.d();
			any = true;
		}
		return withLength(line, any ? last : 0);
	}

	/** Where the line last leaves its own box before this spot (0 if it starts out of it). */
	private static int lastOwn(Line line, Spot before) {
		int out = 0;
		for (int d = 0; d < before.d(); d += STEP) {
			int px = (int) Math.floor(line.x() + 0.5 + line.ux() * d);
			int pz = (int) Math.floor(line.z() + 0.5 + line.uz() * d);
			if (line.inOwn(px, pz, OWN_MARGIN)) {
				out = Math.max(0, d - FIRST);
			}
		}
		return out;
	}

	private static Line withLength(Line l, int length) {
		return length == l.length() ? l : new Line(l.key(), l.kind(), l.x(), l.z(), l.angle(), length, l.version(), l.seed(), l.own(), l.tiers());
	}

	/** SplitMix64 of a seed and a salt. */
	public static long mix(long seed, long salt) {
		long z = seed + salt * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
