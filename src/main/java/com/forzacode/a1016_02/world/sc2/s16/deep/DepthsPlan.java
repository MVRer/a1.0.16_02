package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the depths under one pyramid are (docs/SELF_CREATED_2_R3.md, "33 The depths", "The shape"): every decision, made
 * once from the pyramid's frame and its start's seed (never per chunk), in the frame of the pyramid's way in: {@code s}
 * to its right, {@code f} out toward its front (the tunnel's direction), {@code y} absolute. {@code P} is the pit floor's
 * feet height, where the tunnel and the first house stand; every next level's feet {@value #DROP} lower.
 *
 * <ul>
 *   <li><b>L1</b>, the house (20's first house, R11-5, cell for cell): its front wall the tunnel's end face at
 *   {@code f} {@value #L1_FRONT}, an oak door at side 0; a second oak door in the middle of its back wall.</li>
 *   <li><b>L2</b>, the rooms: the house in a grid of 3 by 3 or 3 by 5 on a pitch of {@value #PITCH} (shared walls),
 *   centred under the pit's floor; every shared wall an oak door and a pane either side of it; one torch a house.</li>
 *   <li><b>L3</b>, the warren: a grid of 5 by 5 to 9 by 9, unlit, no panes; its doorways a maze from the house the stair
 *   comes into (a spanning tree and one more doorway for every 6 houses), 3 in 4 an oak door, the rest empty; the camp
 *   off the way; one or two dead ends stuffed.</li>
 *   <li><b>L4</b>, the end: an iron door in a cobblestone wall, a step, the house a last time (its torch places bare,
 *   its back wall gone), his tunnel from the gap back under the warren, a cobblestone near its blind end.</li>
 *   <li><b>The stairs</b> (A from the house to the rooms, B from the rooms to the warren, C from the warren to the end; A
 *   down 16 straight to the warren where the rooms are dropped): one wide, {@value #HEADROOM} high, every step 1 down,
 *   each descent outside every level's footprint by {@value #ROCK} of rock, the flats over a level 3 above its roof.</li>
 * </ul>
 * The routes are fixed per size so that every part keeps {@value #ROCK} of rock from every other but where they join,
 * inside the pyramid's footprint less {@value #FOOTPRINT_IN}; {@link Depths#of} drops what does not fit the land.
 */
public final class DepthsPlan {
	/** How far each level's feet are under the one above. */
	public static final int DROP = 8;
	/** A house's side outside (20's). */
	public static final int HOUSE = 7;
	/** Houses in a grid share walls: their middles this far apart. */
	public static final int PITCH = 6;
	/** The air of a stair's cell over its feet: a 1-high step is walked down and up only with three. */
	public static final int HEADROOM = 3;
	/** The rock kept between any two parts but where they join. */
	public static final int ROCK = 3;
	/** Every cell inside the pyramid's footprint less this. */
	public static final int FOOTPRINT_IN = 3;
	/** Every level at least this far under the noise ground over it. */
	public static final int UNDER_GROUND = 12;
	/** The L1 house's front wall: the tunnel's end face. */
	public static final int L1_FRONT = PyramidShape.TUNNEL_LENGTH + 2;
	/** The cobblestone in his tunnel stands this far before its blind end. */
	public static final int COBBLE_BEFORE_END = 8;
	/** L2's grid (houses across, along the facing) per size. */
	static final int[][] ROOMS = {{3, 3}, {3, 3}, {3, 5}, {3, 5}};
	/** L3's grid per size. */
	static final int[][] WARREN = {{5, 5}, {5, 7}, {7, 7}, {9, 9}};
	/** His tunnel's length per size. */
	static final int[] HIS_TUNNEL = {24, 32, 32, 40};

	/** The four levels, their feet under {@code P}. */
	public enum Level {
		L1(0), L2(-DROP), L3(-2 * DROP), L4(-3 * DROP);

		public final int down;

		Level(int down) {
			this.down = down;
		}
	}

	/**
	 * One house: its level, its grid cell (L1 and L4: 0, 0), its middle column and its feet. Its walls are its middle
	 * {@code +-3}, its floor under its feet, its roof three over them; its front faces the pit (toward {@code -f}).
	 */
	public record House(int index, Level level, int i, int j, int s0, int f0, int feet) {
		/** True for a column of its inside (5 by 5). */
		public boolean interior(int s, int f) {
			return Math.abs(s - s0) <= 2 && Math.abs(f - f0) <= 2;
		}
	}

	/** What a doorway holds. */
	public enum Way {
		/** An oak door, closed (L2's and L3's shared walls). */
		DOOR,
		/** An empty doorway (his pass). */
		OPEN,
		/** Stuffed from the inside with cobblestone and dirt. */
		STUFFED,
		/** Stuffed once, broken through from outside: empty, the planks round it gone. */
		BROKEN,
		/** L1's front door at the tunnel's end (oak, closed). */
		FRONT,
		/** L1's back door to stair A (oak, closed). */
		BACK,
		/** A stair's top: an oak door, closed. */
		STAIR_TOP,
		/** A stair's foot: an empty doorway. */
		STAIR_FOOT,
		/** The iron door, closed, nothing to open it. */
		IRON,
		/** L4's front door (oak, closed). */
		END_FRONT;

		/** True if it holds a door (wooden or iron). */
		public boolean door() {
			return this == DOOR || this == FRONT || this == BACK || this == STAIR_TOP || this == IRON || this == END_FRONT;
		}

		/** True if one walks through it without breaking anything (a door opens). */
		public boolean passable() {
			return this != STUFFED;
		}
	}

	/**
	 * A doorway: two cells high from {@code feet} at {@code (s, f)}, in a wall whose normal runs along {@code s}
	 * ({@code acrossS}) or along {@code f}; between houses {@code a} and {@code b} (-1 for a stair or the tunnel).
	 */
	public record Doorway(int index, Way way, int a, int b, int s, int f, int feet, boolean acrossS) {
	}

	/** One stair: its cells ({@code s, f, feet}; {@value #HEADROOM} high each), the level it leaves and the one it reaches. */
	public record Stair(String name, Level from, Level to, List<int[]> cells) {
	}

	private final int cx;
	private final int cz;
	private final Direction facing;
	private final Direction right;
	private final int p;
	private final int size;
	private final long seed;
	private final int m;
	private final boolean rooms;
	private final int g2s;
	private final int g2f;
	private final int g3s;
	private final int g3f;
	private final int sL4;
	private final int tunnelLength;
	private final List<House> houses = new ArrayList<>();
	private final List<Doorway> doorways = new ArrayList<>();
	private final List<Stair> stairs = new ArrayList<>();
	/** L2's torch side per house (true: the left one, frame r 1). */
	private final boolean[] torchLeft;
	private final int l1;
	private final int l4;
	private final int l2From;
	private final int l3From;
	private final int camp;
	private final int broken;
	private final List<Integer> warrenPath = new ArrayList<>();

	/**
	 * The plan for a pyramid's frame.
	 *
	 * @param size  the pyramid's size index (0 to 3)
	 * @param rooms false when L2 is dropped (the stair from the house goes down 16 to the warren)
	 */
	public DepthsPlan(int cx, int cz, Direction facing, int pitFeet, int size, long seed, boolean rooms) {
		this.cx = cx;
		this.cz = cz;
		this.facing = facing;
		this.right = facing.getClockWise();
		this.p = pitFeet;
		this.size = Math.clamp(size, 0, 3);
		this.seed = seed;
		this.m = (Hash.of(seed, 0x33A1L) & 1) == 0 ? 1 : -1;
		this.rooms = rooms;
		this.g2s = ROOMS[this.size][0];
		this.g2f = ROOMS[this.size][1];
		this.g3s = WARREN[this.size][0];
		this.g3f = WARREN[this.size][1];
		this.tunnelLength = HIS_TUNNEL[this.size];
		int h2f = 3 * g2f;
		int h3f = 3 * g3f;
		int n2f = (g2f - 1) / 2;
		int n3s = (g3s - 1) / 2;
		int n3f = (g3f - 1) / 2;
		int sx = PITCH * n3s * m;
		this.sL4 = sx - 8 * m;

		// The houses: L1, L2's grid, L3's grid, L4.
		this.l1 = house(Level.L1, 0, 0, 0, L1_FRONT + 3);
		this.l2From = houses.size();
		if (rooms) {
			for (int i = -(g2s - 1) / 2; i <= (g2s - 1) / 2; i++) {
				for (int j = -n2f; j <= n2f; j++) {
					house(Level.L2, i, j, PITCH * i, PITCH * j);
				}
			}
		}
		this.l3From = houses.size();
		for (int i = -n3s; i <= n3s; i++) {
			for (int j = -n3f; j <= n3f; j++) {
				house(Level.L3, i, j, PITCH * i, PITCH * j);
			}
		}
		this.l4 = house(Level.L4, 0, 0, sL4, -h3f + 2);
		this.torchLeft = new boolean[houses.size()];
		for (House h : houses) {
			torchLeft[h.index()] = (Hash.of(seed, 0x33B2L, h.index()) & 1) == 0;
		}

		// L1's two doors.
		doorway(Way.FRONT, l1, -1, 0, L1_FRONT, p, false);
		doorway(Way.BACK, l1, -1, 0, L1_FRONT + HOUSE - 1, p, false);

		// Stair A (or A2), B.
		List<int[]> a = new ArrayList<>();
		for (int f = L1_FRONT + HOUSE; f <= 20; f++) {
			a.add(new int[] {0, f, p});
		}
		for (int k = 1; k <= DROP; k++) {
			a.add(new int[] {k * m, 20, p - k});
		}
		if (rooms) {
			for (int f = 19; f >= h2f + 1; f--) {
				a.add(new int[] {8 * m, f, p - DROP});
			}
			stairs.add(new Stair("a", Level.L1, Level.L2, List.copyOf(a)));
			doorway(Way.STAIR_FOOT, l2(m, n2f), -1, 8 * m, h2f, p - DROP, false);
			// L2's shared walls: an oak door in each.
			for (int i = -1; i <= 1; i++) {
				for (int j = -n2f; j <= n2f; j++) {
					if (i < 1) {
						doorway(Way.DOOR, l2(i, j), l2(i + 1, j), PITCH * i + 3, PITCH * j, p - DROP, true);
					}
					if (j < n2f) {
						doorway(Way.DOOR, l2(i, j), l2(i, j + 1), PITCH * i, PITCH * j + 3, p - DROP, false);
					}
				}
			}
			List<int[]> b = new ArrayList<>();
			for (int f = h2f + 1; f <= h3f + 4; f++) {
				b.add(new int[] {-6 * m, f, p - DROP});
			}
			for (int k = 1; k <= DROP; k++) {
				b.add(new int[] {-6 * m - k * m, h3f + 4, p - DROP - k});
			}
			for (int f = h3f + 3; f >= h3f + 1; f--) {
				b.add(new int[] {-14 * m, f, p - 2 * DROP});
			}
			doorway(Way.STAIR_TOP, l2(-m, n2f), -1, -6 * m, h2f, p - DROP, false);
			stairs.add(new Stair("b", Level.L2, Level.L3, List.copyOf(b)));
			doorway(Way.STAIR_FOOT, l3(-2 * m, n3f), -1, -14 * m, h3f, p - 2 * DROP, false);
		} else {
			int fa = Math.max(20, h3f + 3);
			for (int f = 21; f <= fa; f++) {
				a.add(new int[] {8 * m, f, p - DROP});
			}
			for (int k = 1; k <= DROP; k++) {
				a.add(new int[] {8 * m, fa + k, p - DROP - k});
			}
			for (int k = 1; k <= 4; k++) {
				a.add(new int[] {8 * m + k * m, fa + DROP, p - 2 * DROP});
			}
			for (int f = fa + DROP - 1; f >= h3f + 1; f--) {
				a.add(new int[] {12 * m, f, p - 2 * DROP});
			}
			stairs.add(new Stair("a", Level.L1, Level.L3, List.copyOf(a)));
			doorway(Way.STAIR_FOOT, l3(2 * m, n3f), -1, 12 * m, h3f, p - 2 * DROP, false);
		}

		// The warren's maze, from the house the stair comes into to the far corner's.
		int entry = rooms ? l3(-2 * m, n3f) : l3(2 * m, n3f);
		int exit = l3(n3s * m, -n3f);
		int[] maze = maze(entry, exit);
		this.camp = maze[0];
		this.broken = maze[1];

		// Stair C, the iron door, the step, the end house.
		List<int[]> c = new ArrayList<>();
		for (int f = -h3f - 1; f >= -h3f - 6; f--) {
			c.add(new int[] {sx, f, p - 2 * DROP});
		}
		for (int k = 1; k <= DROP; k++) {
			c.add(new int[] {sx - k * m, -h3f - 6, p - 2 * DROP - k});
		}
		for (int f = -h3f - 5; f <= -h3f - 4; f++) {
			for (int d = -1; d <= 1; d++) {
				c.add(new int[] {sL4 + d, f, p - 3 * DROP});
			}
		}
		c.add(new int[] {sL4, -h3f - 2, p - 3 * DROP});
		doorway(Way.STAIR_TOP, exit, -1, sx, -h3f, p - 2 * DROP, false);
		stairs.add(new Stair("c", Level.L3, Level.L4, List.copyOf(c)));
		doorway(Way.IRON, -1, -1, sL4, -h3f - 3, p - 3 * DROP, false);
		doorway(Way.END_FRONT, l4, -1, sL4, -h3f - 1, p - 3 * DROP, false);
	}

	private int house(Level level, int i, int j, int s0, int f0) {
		int index = houses.size();
		houses.add(new House(index, level, i, j, s0, f0, p + level.down));
		return index;
	}

	private void doorway(Way way, int a, int b, int s, int f, int feet, boolean acrossS) {
		doorways.add(new Doorway(doorways.size(), way, a, b, s, f, feet, acrossS));
	}

	/** L2's house at grid cell {@code (i, j)}. */
	private int l2(int i, int j) {
		return l2From + (i + (g2s - 1) / 2) * g2f + (j + (g2f - 1) / 2);
	}

	/** L3's house at grid cell {@code (i, j)}. */
	private int l3(int i, int j) {
		return l3From + (i + (g3s - 1) / 2) * g3f + (j + (g3f - 1) / 2);
	}

	/**
	 * The warren's doorways: a spanning tree from {@code entry} (a walk that turns where it must, from the seed), one more
	 * doorway for every 6 houses, the way from {@code entry} to {@code exit}, the camp (a house 2 to 4 doorways off the
	 * way, its doorways stuffed, the one toward the way broken through), one or two dead ends off the way stuffed, the rest
	 * 3 in 4 an oak door. Returns {camp house, broken doorway} (-1 for none).
	 */
	private int[] maze(int entry, int exit) {
		int n = g3s * g3f;
		Random rng = new Random(Hash.of(seed, 0x33C3L));
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		// Edges as ordered pairs (a < b) of local warren indices.
		List<int[]> tree = new ArrayList<>();
		boolean[] seen = new boolean[n];
		ArrayDeque<Integer> stack = new ArrayDeque<>();
		int start = entry - l3From;
		seen[start] = true;
		stack.push(start);
		while (!stack.isEmpty()) {
			int cur = stack.peek();
			int ci = cur / g3f;
			int cj = cur % g3f;
			List<Integer> next = new ArrayList<>();
			for (int[] d : dirs) {
				int ni = ci + d[0];
				int nj = cj + d[1];
				if (ni >= 0 && ni < g3s && nj >= 0 && nj < g3f && !seen[ni * g3f + nj]) {
					next.add(ni * g3f + nj);
				}
			}
			if (next.isEmpty()) {
				stack.pop();
				continue;
			}
			int pick = next.get(rng.nextInt(next.size()));
			seen[pick] = true;
			tree.add(new int[] {Math.min(cur, pick), Math.max(cur, pick)});
			stack.push(pick);
		}
		Set<Long> inTree = new HashSet<>();
		for (int[] e : tree) {
			inTree.add(key(e[0], e[1]));
		}
		List<int[]> extra = new ArrayList<>();
		for (int a = 0; a < n; a++) {
			int ai = a / g3f;
			int aj = a % g3f;
			if (ai + 1 < g3s && !inTree.contains(key(a, a + g3f))) {
				extra.add(new int[] {a, a + g3f});
			}
			if (aj + 1 < g3f && !inTree.contains(key(a, a + 1))) {
				extra.add(new int[] {a, a + 1});
			}
		}
		Collections.shuffle(extra, rng);
		extra = new ArrayList<>(extra.subList(0, Math.min(extra.size(), n / 6)));
		// The tree's adjacency, the way, each house's distance from the way and its doorway toward it.
		List<List<int[]>> adj = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			adj.add(new ArrayList<>());
		}
		for (int e = 0; e < tree.size(); e++) {
			adj.get(tree.get(e)[0]).add(new int[] {tree.get(e)[1], e});
			adj.get(tree.get(e)[1]).add(new int[] {tree.get(e)[0], e});
		}
		int goal = exit - l3From;
		int[] back = new int[n];
		Arrays.fill(back, -2);
		back[start] = -1;
		ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
		while (!queue.isEmpty()) {
			int cur = queue.poll();
			for (int[] next : adj.get(cur)) {
				if (back[next[0]] == -2) {
					back[next[0]] = cur;
					queue.add(next[0]);
				}
			}
		}
		Set<Integer> way = new HashSet<>();
		for (int at = goal; at >= 0; at = back[at]) {
			way.add(at);
			warrenPath.add(0, l3From + at);
		}
		int[] dist = new int[n];
		int[] toWay = new int[n];
		Arrays.fill(dist, -1);
		Arrays.fill(toWay, -1);
		for (int h : way) {
			dist[h] = 0;
			queue.add(h);
		}
		while (!queue.isEmpty()) {
			int cur = queue.poll();
			for (int[] next : adj.get(cur)) {
				if (dist[next[0]] < 0) {
					dist[next[0]] = dist[cur] + 1;
					toWay[next[0]] = next[1];
					queue.add(next[0]);
				}
			}
		}
		List<Integer> candidates = new ArrayList<>();
		for (int h = 0; h < n; h++) {
			if (dist[h] >= 2 && dist[h] <= 4) {
				candidates.add(h);
			}
		}
		if (candidates.isEmpty()) {
			int best = 0;
			for (int h = 0; h < n; h++) {
				best = Math.max(best, dist[h]);
			}
			for (int h = 0; h < n && best >= 1; h++) {
				if (dist[h] == best) {
					candidates.add(h);
				}
			}
		}
		int campAt = candidates.isEmpty() ? -1 : candidates.get(rng.nextInt(candidates.size()));
		// The camp's route to the way (its tree doorways), kept clear of the other stuffings.
		Set<Integer> campRoute = new HashSet<>();
		for (int h = campAt; h >= 0 && dist[h] > 0; ) {
			int e = toWay[h];
			campRoute.add(e);
			h = tree.get(e)[0] == h ? tree.get(e)[1] : tree.get(e)[0];
		}
		int[] degree = new int[n];
		for (int[] e : tree) {
			degree[e[0]]++;
			degree[e[1]]++;
		}
		for (int[] e : extra) {
			degree[e[0]]++;
			degree[e[1]]++;
		}
		// Dead ends off the way (a leaf of the tree with no other doorway), not the camp's nor on its route: 1 or 2 stuffed.
		List<Integer> dead = new ArrayList<>();
		for (int e = 0; e < tree.size(); e++) {
			int a = tree.get(e)[0];
			int b = tree.get(e)[1];
			int leaf = dist[a] > dist[b] ? a : b;
			if (degree[leaf] == 1 && dist[leaf] > 0 && leaf != campAt && a != campAt && b != campAt && !campRoute.contains(e)) {
				dead.add(e);
			}
		}
		Collections.shuffle(dead, rng);
		Set<Integer> stuffed = new HashSet<>(dead.subList(0, Math.min(dead.size(), 1 + rng.nextInt(2))));
		int brokenAt = -1;
		List<int[]> all = new ArrayList<>(tree);
		all.addAll(extra);
		for (int e = 0; e < all.size(); e++) {
			int a = all.get(e)[0];
			int b = all.get(e)[1];
			Way kind;
			if (campAt >= 0 && (a == campAt || b == campAt)) {
				kind = e < tree.size() && e == toWay[campAt] ? Way.BROKEN : Way.STUFFED;
			} else if (e < tree.size() && stuffed.contains(e)) {
				kind = Way.STUFFED;
			} else {
				kind = Hash.below(Hash.of(seed, 0x33D4L, e), 4) == 0 ? Way.OPEN : Way.DOOR;
			}
			int ai = a / g3f - (g3s - 1) / 2;
			int aj = a % g3f - (g3f - 1) / 2;
			boolean acrossS = b - a == g3f;
			int s = acrossS ? PITCH * ai + 3 : PITCH * ai;
			int f = acrossS ? PITCH * aj : PITCH * aj + 3;
			if (kind == Way.BROKEN) {
				brokenAt = doorways.size();
			}
			doorway(kind, l3From + a, l3From + b, s, f, p - 2 * DROP, acrossS);
		}
		return new int[] {campAt < 0 ? -1 : l3From + campAt, brokenAt};
	}

	private static long key(int a, int b) {
		return ((long) a << 32) | (b & 0xFFFFFFFFL);
	}

	// --- reading it ---

	public int size() {
		return size;
	}

	/** The pit floor's feet height (L1's feet). */
	public int pitFeet() {
		return p;
	}

	/** The side the stairs turn to (+1 right, -1 left), from the seed. */
	public int mirror() {
		return m;
	}

	/** False when the rooms (L2) are dropped. */
	public boolean rooms() {
		return rooms;
	}

	public Direction facing() {
		return facing;
	}

	public Direction right() {
		return right;
	}

	/** L2's grid (houses across, along); {0, 0} when dropped. */
	public int[] roomsGrid() {
		return rooms ? new int[] {g2s, g2f} : new int[] {0, 0};
	}

	/** L3's grid (houses across, along). */
	public int[] warrenGrid() {
		return new int[] {g3s, g3f};
	}

	public int hisTunnelLength() {
		return tunnelLength;
	}

	public List<House> houses() {
		return houses;
	}

	public House house(int index) {
		return houses.get(index);
	}

	public House l1() {
		return houses.get(l1);
	}

	public House l4() {
		return houses.get(l4);
	}

	/** The houses of a level. */
	public List<House> houses(Level level) {
		return houses.stream().filter(h -> h.level() == level).toList();
	}

	public List<Doorway> doorways() {
		return doorways;
	}

	public List<Stair> stairs() {
		return stairs;
	}

	/** The camp's house, or -1. */
	public int camp() {
		return camp;
	}

	/** The camp's broken doorway, or -1. */
	public int broken() {
		return broken;
	}

	/** The warren's houses from the stair up's to the stair down's, through doorways that need nothing broken. */
	public List<Integer> warrenWay() {
		return warrenPath;
	}

	/** True if this L2 house keeps its left torch (frame r 1), false its right. */
	public boolean torchLeft(int house) {
		return torchLeft[house];
	}

	/** The levels kept, top down. */
	public List<Level> levels() {
		return rooms ? List.of(Level.L1, Level.L2, Level.L3, Level.L4) : List.of(Level.L1, Level.L3, Level.L4);
	}

	/** The doorway of this way (the first), or null. */
	public Doorway doorway(Way way) {
		for (Doorway d : doorways) {
			if (d.way() == way) {
				return d;
			}
		}
		return null;
	}

	/** His tunnel's first column ({@code f}) past the end house's gone back wall, and its last (the blind end). */
	public int[] hisTunnelSpan() {
		int start = houses.get(l4).f0() + 4;
		return new int[] {start, start + tunnelLength - 1};
	}

	/** His tunnel's two sides ({@code s}). */
	public int[] hisTunnelSides() {
		return new int[] {sL4, sL4 + m};
	}

	/** The cobblestone near his tunnel's blind end (local {s, f, y}). */
	public int[] hisCobble() {
		return new int[] {sL4, hisTunnelSpan()[1] - COBBLE_BEFORE_END, p - 3 * DROP};
	}

	// --- the frame ---

	public int x(int s, int f) {
		return cx + right.getStepX() * s + facing.getStepX() * f;
	}

	public int z(int s, int f) {
		return cz + right.getStepZ() * s + facing.getStepZ() * f;
	}

	public BlockPos pos(int s, int y, int f) {
		return new BlockPos(x(s, f), y, z(s, f));
	}

	public int s(int x, int z) {
		return (x - cx) * right.getStepX() + (z - cz) * right.getStepZ();
	}

	public int f(int x, int z) {
		return (x - cx) * facing.getStepX() + (z - cz) * facing.getStepZ();
	}

	/** A box between two local corners. */
	public BoundingBox box(int s0, int y0, int f0, int s1, int y1, int f1) {
		return BoundingBox.fromCorners(pos(s0, y0, f0), pos(s1, y1, f1));
	}

	/** A house's 7 x 7 from its floor to its roof. */
	public BoundingBox box(House h) {
		return box(h.s0() - 3, h.feet() - 1, h.f0() - 3, h.s0() + 3, h.feet() + 3, h.f0() + 3);
	}
}
