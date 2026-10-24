package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Frame;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The archive's geometry (docs/SELF_CREATED_2.md, feature 17), pure: everything follows from the {@link Start} and the
 * three tunnels' lengths (measured once over the terrain, {@link #of(Start, Terrain)}, and kept with the place,
 * {@link #of(Start, int[])}), so worldgen, the gallery, the interior and the answers all agree.
 *
 * <p>Local coordinates ({@link Frame}): {@code r} to the right, {@code u} up from the hall's floor layer (the start's
 * origin Y), {@code f} forward, toward the world's origin (the way out). The start's origin is the middle of the hall's
 * entry wall at floor level. Behind it ({@code f < 0}) the hall: 25 wide ({@code r} -12..12), 17 high ({@code u} 1..17),
 * 63 or 95 long ({@code f} -1..-L), its bookshelf face one block out and a cobblestone shell two more. In front of it the
 * cells ({@code f} 4..), a grid of 7 x 7 x 4 rooms on a pitch of 8, the hall's door from the middle cell of the first
 * row. From the outer row three tunnels rise one in two toward the origin: one open to the surface, two stopped by a plug
 * of 12 blocks just under it.
 */
final class ArchivePlan {
	/** The hall's half width: {@code r} -12..12. */
	static final int HALF = 12;
	/** The hall's inside height: {@code u} 1..17. */
	static final int HEIGHT = 17;
	static final int SHORT = 63;
	static final int LONG = 95;
	/** The bookshelf face's {@code r}, the shell's outer {@code r}. */
	static final int FACE = 13;
	static final int SHELL = 15;
	/** The stacks: {@code |r|} 5..12, {@code u} 1..9; the nave {@code r} -4..4. */
	static final int STACK_IN = 5;
	static final int STACK_TOP = 9;
	/** The deck on the stacks ({@code u} 10) and its rail ({@code u} 11). */
	static final int DECK = 10;
	/** The wall strip of the deck: {@code |r|} 10..12. */
	static final int STRIP = 10;
	/** The first row's front cell is {@code f} -7; rows every 6, 2 thick. */
	static final int ROW_FIRST = 7;
	static final int ROW_PITCH = 6;
	/** The dais: 8 deep (5 of platform, 3 steps), 7 wide, 3 high. */
	static final int DAIS_DEPTH = 8;
	static final int PLATFORM = 5;
	static final int DAIS_HALF = 3;
	static final int DAIS_HEIGHT = 3;
	/** The ceiling layer (the glowstone grid). */
	static final int CEILING = 18;
	/** The ceiling grid's {@code r}: 6 a row, 4 apart. */
	static final int[] GRID_R = {-10, -6, -2, 2, 6, 10};
	/** The cells: pitch 8, 7 x 7 inside, 4 high; the first wall line at {@code f} 3. */
	static final int CELL = 8;
	static final int CELL_HEIGHT = 4;
	static final int CELLS_BACK = 3;
	/** A tunnel's longest run (cells) before the shape gives up (siting refuses such a start). */
	static final int MAX_TUNNEL = 150;
	/** At most this many cells of air under the way out's exit cell (a step down onto the land, never a fall). */
	static final int EXIT_DROP = 2;
	/** Cells past the mouth looked at for the way out. */
	static final int MAX_NOTCH = 16;
	/** The plug of a closed tunnel: 3 cells of 2 x 2. */
	static final int PLUG = 3;
	/** His tunnel from the bay: 30 long, down one in two. */
	static final int BAY_LENGTH = 30;
	/** Coarse ground samples along a tunnel, every this many cells. */
	private static final int SAMPLE_STEP = 4;

	/** The six names of the list (lore F06), word for word, in its order. */
	static final List<String> LIST_NAMES = List.of("kirbyfan_92", "stoneslab", "dyl4n_m", "oreo_tm", "pk_alpha", "t0rch");

	/** What a cell holds (1 in 6 hold one, the same few repeating). */
	enum Detail {
		NONE, LECTERN, CHAIR, SHELF, TABLE, CHEST
	}

	/**
	 * One tunnel from an outer cell: its cell column, its left {@code r}, the first cell whose head reaches the ground
	 * ({@code mouth}), and the first cell a player stands on the ground outside ({@code exit}). Cell {@code i} is at
	 * {@code f = f0 + i}, its feet at {@code u = 1 + i / 2}.
	 */
	record Tunnel(int column, int left, int mouth, int exit, boolean open) {
	}

	final Start start;
	final Frame frame;
	final long seed;
	final boolean longHall;
	/** The hall's length. */
	final int length;
	/** The cells' columns and rows. */
	final int cols;
	final int rows;
	/** Stack rows per side. */
	final int stackRows;
	/** The bay's aisle and side (-1 left, 1 right). */
	final int bayAisle;
	final int baySide;
	/** The infested share of the cells' walls. */
	final double infested;
	/** The open doorways between cells: horizontal edges first ({@code j * (cols - 1) + c}), then vertical. */
	private final boolean[] edges;
	private final Detail[] details;
	/** The open tunnel first, then the two closed ones. */
	final Tunnel[] tunnels;
	/** The way-in tunnels' first cell's {@code f} (the outer wall line). */
	final int tunnelF0;
	/** False if a tunnel found no surface within {@link #MAX_TUNNEL} (siting refuses it). */
	final boolean fits;
	private final BoundingBox box;

	private ArchivePlan(Start start, int[] lengths, boolean measured) {
		this.start = start;
		this.frame = new Frame(start.origin(), start.facing());
		this.seed = start.seed();
		this.longHall = (start.variant() & 1) != 0;
		this.length = longHall ? LONG : SHORT;
		this.cols = longHall ? 7 : 5;
		this.rows = longHall ? 5 : 3;
		this.infested = Math.min(100, Math.max(0, start.variant() >> 1)) / 100.0;
		this.stackRows = stackRows(length);
		this.bayAisle = Draw.between(seed, 13, 1, Math.max(1, stackRows - 3));
		this.baySide = Draw.between(seed, 14, 0, 1) == 0 ? -1 : 1;
		this.edges = maze(seed, cols, rows);
		this.details = details(seed, cols, rows);
		this.tunnelF0 = CELLS_BACK + CELL * rows;
		int[] columns = tunnelColumns(seed, cols);
		this.tunnels = new Tunnel[3];
		boolean ok = true;
		for (int t = 0; t < 3; t++) {
			int mouth = lengths[2 * t];
			int exit = lengths[2 * t + 1];
			if (mouth < PLUG + 2 || exit < mouth) {
				ok = false;
				mouth = Math.max(PLUG + 2, mouth);
				exit = Math.max(mouth, exit);
			}
			tunnels[t] = new Tunnel(columns[t], cellLeft(columns[t]) + 3, mouth, exit, t == 0);
		}
		this.fits = ok && measured;
		this.box = computeBox();
	}

	/** The plan over the terrain: the tunnels measured where they rise. */
	static ArchivePlan of(Start start, Terrain terrain) {
		ArchivePlan probe = new ArchivePlan(start, new int[] {PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2}, false);
		int[] lengths = new int[6];
		boolean ok = true;
		for (int t = 0; t < 3; t++) {
			int[] measured = probe.measure(probe.tunnels[t].left, terrain, t == 0);
			lengths[2 * t] = measured[0];
			lengths[2 * t + 1] = measured[1];
			ok &= measured[0] > 0;
		}
		ArchivePlan plan = new ArchivePlan(start, lengths, true);
		return ok ? plan : new ArchivePlan(start, lengths, false);
	}

	/** The plan with its tunnels' lengths as kept ({@link #lengths}). */
	static ArchivePlan of(Start start, int[] lengths) {
		int[] six = lengths != null && lengths.length == 6 ? lengths : new int[] {PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2};
		return new ArchivePlan(start, six, true);
	}

	/** The tunnels' lengths, to keep with the place: mouth and exit of each. */
	int[] lengths() {
		int[] out = new int[6];
		for (int t = 0; t < 3; t++) {
			out[2 * t] = tunnels[t].mouth;
			out[2 * t + 1] = tunnels[t].exit;
		}
		return out;
	}

	static int stackRows(int length) {
		// The last row's back cell (-8 - 6k) at least 8 before the dais front (-L + 7): -8 - 6k >= -L + 16.
		return (length - 24) / ROW_PITCH + 1;
	}

	/** The variant: bit 0 the long hall, the rest the infested share in percent. */
	static int variant(boolean longHall, double infestedShare) {
		int percent = (int) Math.round(Math.max(0, Math.min(1, infestedShare)) * 100);
		return (longHall ? 1 : 0) | percent << 1;
	}

	// --- the cells ---

	/** A cell's leftmost inside {@code r}. */
	int cellLeft(int c) {
		return -4 * cols + 1 + CELL * c;
	}

	/** A cell row's back inside {@code f} (nearest the hall). */
	int cellBack(int j) {
		return CELLS_BACK + 1 + CELL * j;
	}

	/** The middle column (the hall's door). */
	int doorColumn() {
		return cols / 2;
	}

	boolean edgeOpen(int c, int j, boolean horizontal) {
		int index = horizontal ? j * (cols - 1) + c : rows * (cols - 1) + j * cols + c;
		return index >= 0 && index < edges.length && edges[index];
	}

	Detail detail(int c, int j) {
		return details[j * cols + c];
	}

	/** Kruskal over the grid with per-edge weights from the seed, then a quarter of the rest open too (loops). */
	static boolean[] maze(long seed, int cols, int rows) {
		int horizontal = rows * (cols - 1);
		int total = horizontal + (rows - 1) * cols;
		Integer[] order = new Integer[total];
		for (int i = 0; i < total; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Double.compare(Draw.unit(seed, 101, a, 0), Draw.unit(seed, 101, b, 0)));
		int[] parent = new int[cols * rows];
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}
		boolean[] open = new boolean[total];
		for (int e : order) {
			int a;
			int b;
			if (e < horizontal) {
				int j = e / (cols - 1);
				int c = e % (cols - 1);
				a = j * cols + c;
				b = a + 1;
			} else {
				int v = e - horizontal;
				int j = v / cols;
				int c = v % cols;
				a = j * cols + c;
				b = a + cols;
			}
			int ra = find(parent, a);
			int rb = find(parent, b);
			if (ra != rb) {
				parent[ra] = rb;
				open[e] = true;
			} else if (Draw.chance(seed, 102, e, 0, 0.25)) {
				open[e] = true;
			}
		}
		return open;
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private Detail[] details(long seed, int cols, int rows) {
		Detail[] out = new Detail[cols * rows];
		int[] cols3 = tunnelColumns(seed, cols);
		for (int j = 0; j < rows; j++) {
			for (int c = 0; c < cols; c++) {
				boolean quiet = (j == 0 && c == cols / 2) || (j == rows - 1 && c == cols3[0]);
				Detail d = Detail.NONE;
				if (!quiet && Draw.chance(seed, 103, c, j, 1.0 / 6.0)) {
					d = Detail.values()[1 + Draw.between(seed, 104L * 64 + j * 8 + c, 0, 4)];
				}
				out[j * cols + c] = d;
			}
		}
		return out;
	}

	/** The open tunnel's column, then the two closed ones' (all in the outer row, all different). */
	static int[] tunnelColumns(long seed, int cols) {
		int open = Draw.between(seed, 11, 0, cols - 1);
		int a = (open + 1 + Draw.between(seed, 12, 0, cols - 2)) % cols;
		int b = a;
		for (int k = 0; b == a || b == open; k++) {
			b = (open + 1 + Draw.between(seed, 15 + k, 0, cols - 2)) % cols;
		}
		return new int[] {open, a, b};
	}

	// --- the hall ---

	/** The row whose cell this is ({@code f} -7 - 6k or -8 - 6k), else -1. */
	int rowOf(int f) {
		if (f > -ROW_FIRST) {
			return -1;
		}
		int k = (-f - ROW_FIRST) / ROW_PITCH;
		int rem = (-f - ROW_FIRST) % ROW_PITCH;
		return k < stackRows && rem <= 1 ? k : -1;
	}

	/** The aisle between rows k and k+1 ({@code f} -9 - 6k .. -12 - 6k), else -1. */
	int aisleOf(int f) {
		int k = Math.floorDiv(-f - 9, ROW_PITCH);
		int rem = Math.floorMod(-f - 9, ROW_PITCH);
		return k >= 0 && k < stackRows - 1 && rem <= 3 ? k : -1;
	}

	int rowFront(int k) {
		return -ROW_FIRST - ROW_PITCH * k;
	}

	int rowBack(int k) {
		return rowFront(k) - 1;
	}

	/** The deck's front and back {@code f}: the first row's front overhang, the last row's back overhang. */
	int deckFront() {
		return rowFront(0) + 1;
	}

	int deckBack() {
		return rowBack(stackRows - 1) - 1;
	}

	int daisFront() {
		return -length + DAIS_DEPTH - 1;
	}

	boolean inHall(int r, int f) {
		return Math.abs(r) <= HALF && f <= -1 && f >= -length;
	}

	boolean isStack(int r, int f) {
		int ar = Math.abs(r);
		return ar >= STACK_IN && ar <= HALF && rowOf(f) >= 0;
	}

	/**
	 * The gallery floor ({@code u} 10): one deck over each side's stacks and aisles, from the first row's front to the
	 * last row's back, open only over the bay (taken down with it) and at the ladders.
	 */
	boolean isDeck(int r, int f) {
		int ar = Math.abs(r);
		if (ar < STACK_IN || ar > HALF || f > deckFront() || f < deckBack()) {
			return false;
		}
		return !ladderColumn(r, f) && !inBay(r, f);
	}

	/** A ladder's column: the four corners of the deck and every other row's nave end. */
	boolean ladderColumn(int r, int f) {
		int ar = Math.abs(r);
		if (ar == STRIP + 1 && (f == deckFront() || f == deckBack())) {
			return true;
		}
		if (ar == STACK_IN - 1) {
			int k = rowOf(f);
			return k >= 0 && k % 2 == 0 && f == rowBack(k);
		}
		return false;
	}

	/** The deck cell a ladder leads onto (no rail there). */
	boolean landing(int r, int f) {
		int ar = Math.abs(r);
		if (ar == STRIP + 1 && (f == deckFront() - 1 || f == deckBack() + 1)) {
			return true;
		}
		if (ar == STACK_IN) {
			int k = rowOf(f);
			return k >= 0 && k % 2 == 0 && f == rowBack(k);
		}
		return false;
	}

	boolean isRail(int r, int f) {
		if (!isDeck(r, f) || landing(r, f)) {
			return false;
		}
		return isVoid(r + 1, f) || isVoid(r - 1, f) || isVoid(r, f + 1) || isVoid(r, f - 1);
	}

	private boolean isVoid(int r, int f) {
		return inHall(r, f) && !isDeck(r, f) && !ladderColumn(r, f);
	}

	/** A lectern at the nave end of every third row. */
	boolean rowLectern(int r, int f) {
		if (Math.abs(r) != STACK_IN - 1) {
			return false;
		}
		int k = rowOf(f);
		return k >= 0 && k % 3 == 0 && f == rowFront(k);
	}

	/** A row's pier: the stack's nave end ({@code |r|} 5), oak logs, carrying the gallery. */
	boolean isPier(int r, int f) {
		return Math.abs(r) == STACK_IN && rowOf(f) >= 0;
	}

	/**
	 * A pilaster in the long walls' face where no stack stands (the entry and the open floor before the dais), on the
	 * rows' rhythm: {@code f} 0 (the entry wall's corners are not used) is the door's, so the entry one is at -3.
	 */
	boolean isPilaster(int f) {
		if (f == -3) {
			return true;
		}
		if (f >= deckBack() || f <= daisFront()) {
			return false;
		}
		return Math.floorMod(-f - ROW_FIRST, ROW_PITCH) == 0;
	}

	/** The sconce height: a glowstone set into a pier, a pilaster, a post or the end wall. */
	static final int SCONCE = 3;

	/**
	 * Every light of the hall: the ceiling grid, a sconce in each pier and pilaster, two either side of the door, two
	 * posts at the dais steps, two lamps either side of the records. In {@code (r, u, f)}.
	 */
	List<int[]> hallLights() {
		List<int[]> out = new ArrayList<>();
		for (int f = -2; f >= -length; f -= 4) {
			for (int r : GRID_R) {
				out.add(new int[] {r, CEILING, f});
			}
		}
		for (int k = 0; k < stackRows; k++) {
			out.add(new int[] {-STACK_IN, SCONCE, rowFront(k)});
			out.add(new int[] {STACK_IN, SCONCE, rowFront(k)});
		}
		for (int f = -1; f >= -length; f--) {
			if (isPilaster(f)) {
				out.add(new int[] {-FACE, SCONCE, f});
				out.add(new int[] {FACE, SCONCE, f});
			}
		}
		out.add(new int[] {-STACK_IN, SCONCE, 0});
		out.add(new int[] {STACK_IN, SCONCE, 0});
		for (int f = -1; f >= -length; f--) {
			if (isPost(DAIS_HALF + 1, f)) {
				out.add(new int[] {-DAIS_HALF - 1, POST, f});
				out.add(new int[] {DAIS_HALF + 1, POST, f});
			}
		}
		out.add(new int[] {-DAIS_HALF - 2, DAIS_HEIGHT + 2, -length - 1});
		out.add(new int[] {DAIS_HALF + 2, DAIS_HEIGHT + 2, -length - 1});
		return out;
	}

	/** The dais posts' height (their top is a light). */
	static final int POST = 5;

	/** True if a hall light is here (as generated). */
	boolean isHallLight(int r, int u, int f) {
		int ar = Math.abs(r);
		if (u == CEILING) {
			return isGrid(r, f);
		}
		if (u == SCONCE) {
			if (ar == STACK_IN && rowOf(f) >= 0 && f == rowFront(rowOf(f))) {
				return true;
			}
			if (ar == FACE && f <= -1 && f >= -length && isPilaster(f)) {
				return true;
			}
			if (ar == STACK_IN && f == 0) {
				return true;
			}
		}
		if (u == POST && isPost(r, f)) {
			return true;
		}
		return u == DAIS_HEIGHT + 2 && ar == DAIS_HALF + 2 && f == -length - 1;
	}

	/**
	 * A post (an oak log column, a light on top) either side of the nave where the stacks end: on the rows' rhythm across
	 * the open floor before the dais, and at each side of its steps; an avenue up to the dais.
	 */
	boolean isPost(int r, int f) {
		if (Math.abs(r) != DAIS_HALF + 1) {
			return false;
		}
		if (f == daisFront()) {
			return true;
		}
		return f < deckBack() && f > daisFront() + 1 && Math.floorMod(-f - ROW_FIRST, ROW_PITCH) == 0;
	}

	boolean isGrid(int r, int f) {
		if (!inHall(r, f) || f > -2 || Math.floorMod(-f - 2, 4) != 0) {
			return false;
		}
		for (int gr : GRID_R) {
			if (r == gr) {
				return true;
			}
		}
		return false;
	}

	/** The grid row index of a ceiling light's {@code f} (0 nearest the door). */
	static int gridRow(int f) {
		return (-f - 2) / 4;
	}

	int gridRows() {
		return (length - 2) / 4 + 1;
	}

	/** The {@code f} span a grid row lights: {@code -4k - 3} to {@code -4k} (its floor tiles are counted with it). */
	static int rowSpanNear(int k) {
		return -4 * k;
	}

	static int rowSpanFar(int k) {
		return -4 * k - 3;
	}

	/** The grid row whose span holds this {@code f}. */
	static int rowAt(int f) {
		return Math.max(0, -f / 4);
	}

	/** A hall row's lights: its 6 in the ceiling and every other light in its span (as generated). */
	List<BlockPos> rowLights(int k) {
		List<BlockPos> out = new ArrayList<>(10);
		for (int[] light : hallLights()) {
			if (rowAt(Math.min(-1, light[2])) == k) {
				out.add(at(light[0], light[1], light[2]));
			}
		}
		return out;
	}

	boolean inDais(int r, int f) {
		return Math.abs(r) <= DAIS_HALF && f <= daisFront() && f >= -length;
	}

	/** The bay: its aisle's floor on its side. */
	boolean inBay(int r, int f) {
		int ar = r * baySide;
		return ar >= STACK_IN && ar <= HALF && aisleOf(f) == bayAisle;
	}

	/** The bay's two middle {@code f} (its tunnel). */
	int bayF() {
		return -10 - ROW_PITCH * bayAisle;
	}

	// --- positions (world) ---

	BlockPos at(int r, int u, int f) {
		return frame.pos(r, u, f);
	}

	int floorY() {
		return start.origin().getY();
	}

	/** The dais lectern ({@code Place.anchor}). */
	BlockPos lectern() {
		return at(0, DAIS_HEIGHT + 1, -length + 2);
	}

	/**
	 * The shelf right behind the dais lectern, under the middle record: where 17e's book goes (the ledger moves a stack
	 * only between containers, and a lectern is none; this is the container beside it, D-100).
	 */
	BlockPos bookShelf() {
		return at(0, DAIS_HEIGHT + 2, -length - 1);
	}

	/** The hall door's inner cell (lower), on the hall's floor. */
	BlockPos hallDoor() {
		return at(0, 1, -1);
	}

	/** The middle of a cell's floor (its feet). */
	BlockPos cellFeet(int c, int j) {
		return at(cellLeft(c) + 3, 1, cellBack(j) + 3);
	}

	/** A cell's ceiling light. */
	BlockPos cellLight(int c, int j) {
		return at(cellLeft(c) + 3, CELL_HEIGHT + 1, cellBack(j) + 3);
	}

	/** The open way's outer mouth cell (lower left, outside: {@code Place.entrance}). */
	BlockPos entrance() {
		Tunnel t = tunnels[0];
		return at(t.left, foot(t.exit), tunnelF0 + t.exit);
	}

	/** Feet {@code u} of tunnel cell {@code i}. */
	static int foot(int i) {
		return 1 + i / 2;
	}

	/** Top {@code u} of tunnel cell {@code i} (2 high, the higher of a step's two ceilings over both). */
	static int top(int i) {
		int before = i == 0 ? foot(0) : foot(i - 1);
		return Math.max(foot(i), Math.max(before, foot(i + 1))) + 1;
	}

	/** The 12 cells of a tunnel's cells {@code from} to {@code from + 2} (2 wide, feet and head). */
	List<BlockPos> tunnelCells(Tunnel t, int from) {
		List<BlockPos> out = new ArrayList<>(12);
		for (int i = from; i < from + PLUG; i++) {
			for (int a = 0; a < 2; a++) {
				for (int u = foot(i); u <= foot(i) + 1; u++) {
					out.add(at(t.left + a, u, tunnelF0 + i));
				}
			}
		}
		return out;
	}

	/** A closed tunnel's plug, or an open one's first three cells inside its mouth: the same 12 places. */
	List<BlockPos> plug(Tunnel t) {
		return tunnelCells(t, t.mouth - PLUG);
	}

	/**
	 * The record shelves of the dais wall, in the order records are left: the upper middle row from its middle outward
	 * (the list's six stand there too, one each beside the middle), then the lower middle row (its middle shelf kept for a
	 * book of one's own, {@link #bookShelf}), then the bottom and top rows.
	 */
	List<BlockPos> recordShelves() {
		List<BlockPos> out = new ArrayList<>();
		int[] order = {0, -1, 1, -2, 2, -3, 3};
		for (int u : new int[] {DAIS_HEIGHT + 3, DAIS_HEIGHT + 2, DAIS_HEIGHT + 1, DAIS_HEIGHT + 4}) {
			for (int r : order) {
				if (u == DAIS_HEIGHT + 2 && r == 0) {
					continue;
				}
				out.add(at(r, u, -length - 1));
			}
		}
		return out;
	}

	/** Every shelf of the dais wall (7 x 4). */
	List<BlockPos> recordWall() {
		List<BlockPos> out = new ArrayList<>(recordShelves());
		out.add(bookShelf());
		return out;
	}

	/** Where the list's six stand (in the list's order): the upper middle row's shelves either side of the middle one. */
	List<BlockPos> listShelves() {
		List<BlockPos> out = new ArrayList<>();
		for (int r : new int[] {-3, -2, -1, 1, 2, 3}) {
			out.add(at(r, DAIS_HEIGHT + 3, -length - 1));
		}
		return out;
	}

	// --- interior and box ---

	/** The hall's inside and the cells' block (their walls too): where a player counts as in it. */
	Interior interior() {
		BoundingBox hall = frame.box(-HALF, 1, -1, HALF, HEIGHT, -length);
		BoundingBox cells = frame.box(-4 * cols + 1, 1, CELLS_BACK + 1, 4 * cols - 1, CELL_HEIGHT, tunnelF0 - 1);
		return Interior.union(List.of(Interior.box(hall), Interior.box(cells)));
	}

	BoundingBox hallInside() {
		return frame.box(-HALF, 1, -1, HALF, HEIGHT, -length);
	}

	BoundingBox cellsInside() {
		return frame.box(-4 * cols + 1, 1, CELLS_BACK + 1, 4 * cols - 1, CELL_HEIGHT, tunnelF0 - 1);
	}

	BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	private BoundingBox computeBox() {
		int reach = 2;
		BoundingBox all = frame.box(-SHELL, -2, CELLS_BACK - 1, SHELL, CEILING + 1, -length - 3);
		all = all.encapsulate(frame.box(-4 * cols - 1, -1, 2, 4 * cols + 1, CELL_HEIGHT + 2, tunnelF0 + 1));
		for (Tunnel t : tunnels) {
			int last = t.open ? t.exit : t.exit;
			all = all.encapsulate(frame.box(t.left - 1 - reach, -reach, tunnelF0, t.left + 2 + reach, top(last) + 1 + reach, tunnelF0 + last + reach));
		}
		int s = baySide;
		int fa = bayF();
		BoundingBox bay = frame.box(s * FACE, -BAY_LENGTH / 2 - reach - 1, fa + 1 + reach, s * (SHELL + BAY_LENGTH + reach), 3 + reach, fa - 2 - reach);
		return all.encapsulate(bay);
	}

	// --- the terrain ---

	/**
	 * A tunnel's mouth and exit over the terrain (its left column's ground), or {-1, -1} if it finds no surface in
	 * {@link #MAX_TUNNEL} cells, or (the open way only: a closed way needs only its mouth) no way out onto the land within
	 * {@link #MAX_NOTCH} past its mouth, or a wet or steep way out. A walk from the first cell, its step half the rock left
	 * over the way's head (at least {@value #SAMPLE_STEP}, at most 16), then cell by cell back over the last step: the mouth
	 * is the first cell whose head reaches the ground, so a valley deep enough to reach the way, being wider than the step,
	 * is never crossed (SC2-H: the earlier walk began near the flat-land estimate after a look every 16 cells, and let a way
	 * cross a narrow valley or a lake's bed as a tube in the air or the water). The open way is dry at its mouth and its exit
	 * on both columns (a mouth under a lake or a river would flood the ways and the rooms; 17c opens a closed way only when
	 * it is dry, {@code OtherDoor}) and its exit cell stands at most {@value #EXIT_DROP} over the land (no step out into the
	 * air where the land falls away).
	 */
	int[] measure(int left, Terrain terrain, boolean open) {
		int y0 = floorY();
		int[] grounds = new int[MAX_TUNNEL + MAX_NOTCH + 1];
		java.util.Arrays.fill(grounds, Integer.MIN_VALUE);
		java.util.function.IntUnaryOperator land = i -> {
			if (grounds[i] == Integer.MIN_VALUE) {
				grounds[i] = ground(left, i, terrain);
			}
			return grounds[i];
		};
		// The step shrinks as the land comes down to the way's head: half the rock left over it (at least
		// SAMPLE_STEP, at most 16), so a valley deep enough to reach the way is wider than the step.
		int mouth = -1;
		int last = -1;
		int i = 0;
		while (i <= MAX_TUNNEL) {
			int head = y0 + foot(i) + 1;
			int g = land.applyAsInt(i);
			if (head >= g) {
				for (int k = last + 1; k <= i; k++) {
					if (k == i || y0 + foot(k) + 1 >= land.applyAsInt(k)) {
						mouth = k;
						break;
					}
				}
				break;
			}
			last = i;
			i += Math.max(SAMPLE_STEP, Math.min(16, (g - head) / 2));
		}
		if (mouth < 0) {
			return new int[] {-1, -1};
		}
		mouth = Math.max(mouth, PLUG + 2);
		int exit = -1;
		for (int k = mouth; k <= mouth + MAX_NOTCH; k++) {
			if (y0 + foot(k) >= land.applyAsInt(k) + 1) {
				exit = k;
				break;
			}
		}
		if (!open) {
			// A closed way only needs its mouth (its plug holds whatever is outside); 17c opens it only if it is dry then.
			return new int[] {mouth, exit < 0 ? mouth + MAX_NOTCH : exit};
		}
		if (exit < 0) {
			return new int[] {-1, -1};
		}
		// Dry at the mouth and the exit on both columns; out of the exit (the place's mouth cell, its left column) a drop of
		// at most EXIT_DROP onto the land.
		if (y0 + foot(exit) - 1 - land.applyAsInt(exit) > EXIT_DROP) {
			return new int[] {-1, -1};
		}
		for (int k : new int[] {mouth, exit}) {
			for (int c = 0; c <= 1; c++) {
				BlockPos p = frame.pos(left + c, 0, tunnelF0 + k);
				int g = c == 0 ? land.applyAsInt(k) : terrain.ground(p.getX(), p.getZ());
				if (terrain.surface(p.getX(), p.getZ()) > g) {
					return new int[] {-1, -1};
				}
			}
		}
		return new int[] {mouth, exit};
	}

	/** True if cell {@code i}'s head is at or above the ground: the roof would open. */
	private boolean reached(int left, int i, int y0, Terrain terrain) {
		return y0 + foot(i) + 1 >= ground(left, i, terrain);
	}

	private int ground(int left, int i, Terrain terrain) {
		BlockPos p = frame.pos(left, 0, tunnelF0 + i);
		return terrain.ground(p.getX(), p.getZ());
	}

	/** Which tunnel a cell column belongs to (its lining included), or -1. */
	int tunnelAt(int r, int f) {
		if (f < tunnelF0) {
			return -1;
		}
		for (int t = 0; t < 3; t++) {
			Tunnel tunnel = tunnels[t];
			if (r >= tunnel.left - 1 && r <= tunnel.left + 2 && f - tunnelF0 < tunnel.exit) {
				return t;
			}
		}
		return -1;
	}

	/** True if tunnel cell {@code i} is carved at generation (a closed tunnel keeps its plug). */
	static boolean carved(Tunnel t, int i) {
		if (i < 0 || i >= t.exit) {
			return false;
		}
		return t.open || i < t.mouth - PLUG || i >= t.mouth;
	}

	/** True if tunnel cell {@code i} is a closed tunnel's plug. */
	static boolean plugged(Tunnel t, int i) {
		return !t.open && i >= t.mouth - PLUG && i < t.mouth;
	}

	/** His tunnel's cell index for this {@code r} (0 in the shell), or -1. */
	int bayCell(int r, int f) {
		if (f != bayF() && f != bayF() - 1) {
			return -1;
		}
		int j = r * baySide - (SHELL - 1);
		return j >= 0 && j < BAY_LENGTH ? j : -1;
	}

	/** Feet {@code u} of his tunnel's cell {@code j}: down one in two from the bay's floor (the shell's top). */
	static int bayFoot(int j) {
		return -(j / 2);
	}

	static int bayTop(int j) {
		int before = j == 0 ? bayFoot(0) : bayFoot(j - 1);
		return Math.max(bayFoot(j), Math.max(before, bayFoot(j + 1))) + 1;
	}

	// --- the cells as a grid ---

	int cellCount() {
		return cols * rows;
	}

	int cellIndex(int c, int j) {
		return j * cols + c;
	}

	/** The cell whose inside holds this local column ({@code {c, j, a, b}}), or null (a wall, outside). */
	int @org.jspecify.annotations.Nullable [] cellAt(int r, int f) {
		int rr = r + 4 * cols;
		int ff = f - CELLS_BACK;
		if (rr <= 0 || ff <= 0 || rr >= CELL * cols || ff >= CELL * rows || rr % CELL == 0 || ff % CELL == 0) {
			return null;
		}
		return new int[] {rr / CELL, ff / CELL, rr % CELL - 1, ff % CELL - 1};
	}

	/** The cell a world position stands in (its feet in the cell's height), or -1. */
	int cellOf(BlockPos feet) {
		int u = feet.getY() - floorY();
		if (u < 1 || u > CELL_HEIGHT) {
			return -1;
		}
		int[] cell = cellAt(localR(feet.getX(), feet.getZ()), localF(feet.getX(), feet.getZ()));
		return cell == null ? -1 : cellIndex(cell[0], cell[1]);
	}

	/** True if a world position stands in the hall (its feet over the floor, under the ceiling). */
	boolean inHallAt(BlockPos feet) {
		int u = feet.getY() - floorY();
		return u >= 0 && u <= HEIGHT && inHall(localR(feet.getX(), feet.getZ()), localF(feet.getX(), feet.getZ()));
	}

	/** Steps between cells through open doorways, from one cell to every other ({@code -1} unreachable). */
	int[] cellSteps(int from) {
		int[] steps = new int[cellCount()];
		Arrays.fill(steps, -1);
		java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
		steps[from] = 0;
		queue.add(from);
		while (!queue.isEmpty()) {
			int at = queue.poll();
			int c = at % cols;
			int j = at / cols;
			int[][] next = {{c - 1, j, c - 1, j, 1}, {c + 1, j, c, j, 1}, {c, j - 1, c, j - 1, 0}, {c, j + 1, c, j, 0}};
			for (int[] n : next) {
				if (n[0] < 0 || n[0] >= cols || n[1] < 0 || n[1] >= rows) {
					continue;
				}
				if (!edgeOpen(n[2], n[3], n[4] == 1)) {
					continue;
				}
				int to = cellIndex(n[0], n[1]);
				if (steps[to] < 0) {
					steps[to] = steps[at] + 1;
					queue.add(to);
				}
			}
		}
		return steps;
	}

	/** The cell a tunnel opens from (its outer row). */
	int outerCell(int tunnel) {
		return cellIndex(tunnels[tunnel].column(), rows - 1);
	}

	/** The cell the hall's door opens from. */
	int doorCell() {
		return cellIndex(doorColumn(), 0);
	}

	/** Steps between two cells (Chebyshev on the grid, walls aside): how far apart they look. */
	int cellApart(int a, int b) {
		return Math.max(Math.abs(a % cols - b % cols), Math.abs(a / cols - b / cols));
	}

	/** A cell's local corner ({@code r} of its first inside column, {@code f} of its back row). */
	int cellR(int index) {
		return cellLeft(index % cols);
	}

	int cellF(int index) {
		return cellBack(index / cols);
	}

	/** Local {@code r} of a world column. */
	int localR(int x, int z) {
		Direction right = frame.right();
		return (x - start.origin().getX()) * right.getStepX() + (z - start.origin().getZ()) * right.getStepZ();
	}

	/** Local {@code f} of a world column. */
	int localF(int x, int z) {
		Direction facing = frame.facing();
		return (x - start.origin().getX()) * facing.getStepX() + (z - start.origin().getZ()) * facing.getStepZ();
	}
}
