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
	/** Version 3: the hall is 15 deeper, its ways rise that much further. */
	static final int MAX_TUNNEL3 = 190;
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

	// --- version 3 (the stacks, D-113) ---
	/** v3: the hall's inside height ({@code u} 1..41), its ceiling layer, the stacks' top shelf, the decks. */
	static final int HEIGHT3 = 41;
	static final int CEILING3 = 42;
	static final int STACK3 = 26;
	static final int TOP_DECK3 = 27;
	/** v3: the floating grid's {@code u} (where the old ceiling was). */
	static final int FLOAT3 = 17;
	/** v3: the theft reaches this high (a hand from the floor and from the first deck); full above. */
	static final int REACH3 = 13;
	/** v3: the doorway to the well in the long wall: {@code f} -4..-6, {@code u} 1..4. */
	static final int WELL_DOOR_F = Well.DOOR_F;
	/** v3 variant bits: the small annex; the galleries lit from the top (4 bits); the world's lowest block + 4096. */
	static final int SMALL_ANNEX = 1 << 8;
	static final int LIT_SHIFT = 9;
	static final int LOWEST_SHIFT = 13;
	static final int LOWEST_BIAS = 4096;
	/** v3 variant bit: mirrored (the bay and the well change sides; the siting's second try). */
	static final int MIRROR = 1 << 26;

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
	/** The shape version it is built as ({@link ArchiveShape#VERSION} when not tied). */
	final int version;
	final boolean longHall;
	/** v3: the long hall with the short hall's cells (when the whole would not stay in reach). */
	final boolean smallAnnex;
	/** v3: the well; null before. */
	final @org.jspecify.annotations.Nullable Well well;
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
	/** Version 3: the ways' columns turned this many places (which one is open); 0 before. */
	final int rotation;
	/** Version 3: no way of the three opens on the land (no dry exit a step from it): the well's crack is the way in. */
	final boolean sealed;
	/** {@link #lengths}' last number when the ways are sealed. */
	static final int SEALED = 3;
	/** The way-in tunnels' first cell's {@code f} (the outer wall line). */
	final int tunnelF0;
	/** False if a tunnel found no surface within {@link #MAX_TUNNEL} (siting refuses it). */
	final boolean fits;
	private final BoundingBox box;

	private ArchivePlan(Start start, int[] lengths, boolean measured) {
		this.start = start;
		this.frame = new Frame(start.origin(), start.facing());
		this.seed = start.seed();
		this.version = start.versionOr(ArchiveShape.VERSION);
		this.longHall = (start.variant() & 1) != 0;
		this.smallAnnex = version >= 3 && (start.variant() & SMALL_ANNEX) != 0;
		this.length = longHall ? LONG : SHORT;
		this.cols = longHall && !smallAnnex ? 7 : 5;
		this.rows = longHall && !smallAnnex ? 5 : 3;
		this.infested = Math.min(100, Math.max(0, (start.variant() >> 1) & 127)) / 100.0;
		this.stackRows = stackRows(length);
		this.bayAisle = Draw.between(seed, 13, 1, Math.max(1, stackRows - 3));
		this.baySide = (Draw.between(seed, 14, 0, 1) == 0 ? -1 : 1) * (version >= 3 && (start.variant() & MIRROR) != 0 ? -1 : 1);
		this.edges = maze(seed, cols, rows);
		this.details = details(seed, cols, rows);
		this.tunnelF0 = CELLS_BACK + CELL * rows;
		// Version 3: which of the three ways is the open one, as the siting chose it (kept with the place, lengths[9]).
		this.sealed = version >= 3 && lengths.length >= 10 && lengths[9] == SEALED;
		this.rotation = version >= 3 && lengths.length >= 10 && !sealed ? Math.floorMod(lengths[9], 3) : 0;
		int[] base = tunnelColumns(seed, cols);
		int[] columns = {base[rotation], base[(rotation + 1) % 3], base[(rotation + 2) % 3]};
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
			tunnels[t] = new Tunnel(columns[t], cellLeft(columns[t]) + 3, mouth, exit, t == 0 && !sealed);
		}
		this.fits = ok && measured;
		if (version >= 3) {
			int lit = (start.variant() >> LIT_SHIFT) & 15;
			if (lengths.length >= 12 && version >= 4) {
				this.well = Well.of(this, lengths[8], lengths[6], lengths[7], lit, lengths[10], lengths[11]);
			} else if (lengths.length >= 9 && version < 4) {
				this.well = Well.of(this, lengths[8], lengths[6], lengths[7], lit);
			} else {
				int lowest = lengths.length >= 9 ? lengths[8] : lowestOf(start.variant(), floorY() - 70);
				int top = lengths.length >= 9 ? lengths[6] : com.forzacode.a1016_02.world.sc2.kit.Fault.sectionCeil(floorY() + 80);
				int land = lengths.length >= 9 ? lengths[7] : floorY() + 54;
				this.well = version >= 4 ? Well.of(this, lowest, top, land, lit, Well.LIFT, top - 1) : Well.of(this, lowest, top, land, lit);
			}
		} else {
			this.well = null;
		}
		this.box = computeBox();
	}

	/** The plan over the terrain: the tunnels measured where they rise. */
	static ArchivePlan of(Start start, Terrain terrain) {
		ArchivePlan probe = new ArchivePlan(start, new int[] {PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2}, false);
		int[] lengths = new int[probe.version >= 4 ? 12 : probe.version >= 3 ? 9 : 6];
		if (probe.version >= 3) {
			int[] well = Well.measure(probe, terrain);
			lengths[6] = well[0];
			lengths[7] = well[1];
			lengths[8] = lowestOf(start.variant(), terrain.minY());
			if (probe.version >= 4) {
				// Version 4 (R3A-1): the piece's lift over its land and its crest.
				lengths[10] = well[3];
				lengths[11] = well[4];
			}
			if (well[2] == 0) {
				// The land before the well's way in falls away too far for its apron: the siting refuses it.
				ArchivePlan plan = new ArchivePlan(start, lengths, false);
				return plan;
			}
		}
		if (probe.version < 3) {
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
		// Version 3: the open way is the first of the three, in the columns' order turned 0, 1 or 2, whose exit is dry and a
		// step from the land while the other two reach it (the hall is deeper: the first way's exit failed most, R3-1).
		int[] base = tunnelColumns(probe.seed, probe.cols);
		int[][][] seen = new int[2][3][];
		int[] kept = java.util.Arrays.copyOf(lengths, Math.max(10, lengths.length));
		int[] first = null;
		for (int rot = 0; rot < 3; rot++) {
			boolean ok = true;
			int[] tried = java.util.Arrays.copyOf(kept, kept.length);
			for (int t = 0; t < 3 && ok; t++) {
				int c = (t + rot) % 3;
				int open = t == 0 ? 1 : 0;
				if (seen[open][c] == null) {
					seen[open][c] = probe.measure(probe.cellLeft(base[c]) + 3, terrain, open == 1);
				}
				tried[2 * t] = seen[open][c][0];
				tried[2 * t + 1] = seen[open][c][1];
				ok = seen[open][c][0] > 0 && (open == 0 || probe.notchDry(probe.cellLeft(base[c]) + 3, seen[open][c][0], seen[open][c][1], terrain));
				if (ok && open == 0) {
					tried[2 * t + 1] = probe.closedExit(probe.cellLeft(base[c]) + 3, seen[open][c], terrain);
				}
			}
			tried[9] = rot;
			if (ok) {
				return new ArchivePlan(start, tried, true);
			}
			if (first == null) {
				first = tried;
			}
		}
		// None of them: all three sealed (each needs only its mouth), the well's crack the only way in.
		boolean ok = true;
		int[] tried = java.util.Arrays.copyOf(kept, kept.length);
		for (int t = 0; t < 3 && ok; t++) {
			if (seen[0][t] == null) {
				seen[0][t] = probe.measure(probe.cellLeft(base[t]) + 3, terrain, false);
			}
			tried[2 * t] = seen[0][t][0];
			tried[2 * t + 1] = seen[0][t][1];
			ok = seen[0][t][0] > 0;
			if (ok) {
				tried[2 * t + 1] = probe.closedExit(probe.cellLeft(base[t]) + 3, seen[0][t], terrain);
			}
		}
		tried[9] = SEALED;
		return ok ? new ArchivePlan(start, tried, true) : new ArchivePlan(start, first, false);
	}

	/** The plan with its tunnels' lengths as kept ({@link #lengths}). */
	static ArchivePlan of(Start start, int[] lengths) {
		int[] six = lengths != null && (lengths.length == 6 || lengths.length == 9 || lengths.length == 10 || lengths.length == 12) ? lengths
				: new int[] {PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2, PLUG + 2};
		return new ArchivePlan(start, six, true);
	}

	/** The tunnels' lengths, to keep with the place: mouth and exit of each; v3 then the well's cut, front land, lowest. */
	int[] lengths() {
		int[] out = new int[well == null ? 6 : well.lifted() ? 12 : 10];
		for (int t = 0; t < 3; t++) {
			out[2 * t] = tunnels[t].mouth;
			out[2 * t + 1] = tunnels[t].exit;
		}
		if (well != null) {
			out[6] = well.top;
			out[7] = well.land;
			out[8] = well.lowest;
			out[9] = sealed ? SEALED : rotation;
			if (well.lifted()) {
				out[10] = well.lift;
				out[11] = well.crest;
			}
		}
		return out;
	}

	/** The world's lowest block kept in a v3 variant, or {@code otherwise}. */
	static int lowestOf(int variant, int otherwise) {
		int code = (variant >>> LOWEST_SHIFT) & 0x1FFF;
		return code == 0 ? otherwise : code - LOWEST_BIAS;
	}

	/** A v3 variant: {@link #variant} with the small annex, the lit galleries (0: default) and the world's lowest block. */
	static int variant3(boolean longHall, double infestedShare, boolean smallAnnex, int lit, int lowest) {
		return variant(longHall, infestedShare) | (smallAnnex ? SMALL_ANNEX : 0) | (Math.clamp(lit, 0, 15) << LIT_SHIFT)
				| ((lowest + LOWEST_BIAS) << LOWEST_SHIFT);
	}

	/** The hall's inside height, its ceiling layer. */
	int height() {
		return version >= 3 ? HEIGHT3 : HEIGHT;
	}

	int ceiling() {
		return version >= 3 ? CEILING3 : CEILING;
	}

	// --- v3: the stacks ---

	static boolean deckLevel3(int u) {
		return u == 9 || u == 18 || u == TOP_DECK3;
	}

	/** v3: a stack's shelf ({@code u} 1..26 of a row, but the decks' planks). */
	boolean isStack3(int r, int u, int f) {
		return isStack(r, f) && u >= 1 && u <= STACK3 && !deckLevel3(u);
	}

	/** v3: a ladder in each aisle's wall end, from the floor to the top deck (none in the bay's aisle on its side). */
	boolean ladder3(int r, int u, int f) {
		if (Math.abs(r) != HALF || u < 1 || u > TOP_DECK3) {
			return false;
		}
		int k = aisleOf(f);
		return k >= 0 && f == -10 - ROW_PITCH * k && !inBay(r, f);
	}

	/** v3: a deck's plank: each side over its stacks and aisles at 9, 18 and 27, open over the bay and at the ladders. */
	boolean isDeck3(int r, int u, int f) {
		int ar = Math.abs(r);
		if (!deckLevel3(u) || ar < STACK_IN || ar > HALF || f > deckFront() || f < deckBack()) {
			return false;
		}
		return !ladder3(r, u, f) && !inBay(r, f) && !(isPier(r, f) && u < TOP_DECK3);
	}

	/** v3: walkable on deck {@code d} here (a deck plank with room over it). */
	private boolean walk3(int r, int d, int f) {
		return isDeck3(r, d, f) && (d == TOP_DECK3 || !isStack(r, f));
	}

	/** v3: open to a fall at deck level {@code d} (in the hall, nothing to stand on there). */
	private boolean void3(int r, int d, int f) {
		if (!inHall(r, f)) {
			return false;
		}
		if (isDeck3(r, d, f) || ladder3(r, d, f) || isPier(r, f) && d < TOP_DECK3) {
			return false;
		}
		return !(isStack(r, f) && d + 1 <= STACK3);
	}

	/** v3: a rail (oak fence) over a walkable deck cell next to a fall. */
	boolean isRail3(int r, int u, int f) {
		int d = u - 1;
		if (!deckLevel3(d) || !walk3(r, d, f)) {
			return false;
		}
		return void3(r + 1, d, f) || void3(r - 1, d, f) || void3(r, d, f + 1) || void3(r, d, f - 1);
	}

	/** v3: a light of the floating grid (single glowstone at {@value #FLOAT3}, held by nothing). */
	boolean isFloat3(int r, int f) {
		if (!inHall(r, f) || f > -2 || Math.floorMod(-f - 2, 4) != 0) {
			return false;
		}
		boolean onGrid = false;
		for (int gr : GRID_R) {
			onGrid |= r == gr;
		}
		if (!onGrid) {
			return false;
		}
		return Math.abs(r) < STACK_IN || f > deckFront() || f < deckBack();
	}

	/** v3: the editions' shelves' height, and their {@code |r|} in a row; the far end's highest. */
	static final int EDITION_U = 12;
	static final int EDITION_R = 8;
	static final int FAR_U = 26;

	/**
	 * v3: an editions' shelf (chiseled, empty at generation): in the second tier of each row's front face from the second
	 * row on, a hand over the first deck of the aisle before it (none in the bay's aisle on its side); and the far end's
	 * highest shelves (the first row's front, the top of the stacks) for the oldest.
	 */
	boolean isEditionShelf3(int r, int u, int f) {
		if (version < 3 || Math.abs(r) != EDITION_R) {
			return false;
		}
		int k = rowOf(f);
		if (k < 0 || f != rowFront(k)) {
			return false;
		}
		if (u == FAR_U) {
			return k == 0;
		}
		return u == EDITION_U && k >= 1 && !(aisleOf(f + 1) == bayAisle && r * baySide > 0);
	}

	/** v3: the editions' shelves, nearest the dais first (both sides of a row, left first). */
	List<BlockPos> editionShelves() {
		List<BlockPos> out = new ArrayList<>();
		for (int k = stackRows - 1; k >= 1; k--) {
			for (int r : new int[] {-EDITION_R, EDITION_R}) {
				if (isEditionShelf3(r, EDITION_U, rowFront(k))) {
					out.add(at(r, EDITION_U, rowFront(k)));
				}
			}
		}
		return out;
	}

	/** v3: the far end's highest shelves (where the oldest editions go). */
	List<BlockPos> farShelves() {
		return version < 3 ? List.of() : List.of(at(-EDITION_R, FAR_U, rowFront(0)), at(EDITION_R, FAR_U, rowFront(0)));
	}

	/** v3: the doorway to the well through the long wall on the well's side. */
	boolean wellDoor(int r, int u, int f) {
		return well != null && r * well.side >= FACE && Math.abs(r) <= SHELL && Math.abs(f - WELL_DOOR_F) <= 1 && u >= 1 && u <= Well.DOOR_HIGH;
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
				if (version < 3) {
					out.add(new int[] {r, CEILING, f});
				} else if (isFloat3(r, f)) {
					out.add(new int[] {r, FLOAT3, f});
				}
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
		if (version >= 3 && u == FLOAT3) {
			return isFloat3(r, f);
		}
		if (version < 3 && u == CEILING) {
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
		if (well != null && sealed) {
			return well.sill();
		}
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
		BoundingBox hall = frame.box(-HALF, 1, -1, HALF, height(), -length);
		BoundingBox cells = frame.box(-4 * cols + 1, 1, CELLS_BACK + 1, 4 * cols - 1, CELL_HEIGHT, tunnelF0 - 1);
		if (well != null) {
			BoundingBox corridor = BoundingBox.fromCorners(at(well.side * FACE, 1, WELL_DOOR_F - 1),
					well.at(0, floorY() + Well.DOOR_HIGH, well.doorJ() + 1));
			return Interior.union(List.of(Interior.box(hall), Interior.box(cells), Interior.box(well.insideBox()), Interior.box(corridor)));
		}
		return Interior.union(List.of(Interior.box(hall), Interior.box(cells)));
	}

	BoundingBox hallInside() {
		return frame.box(-HALF, 1, -1, HALF, height(), -length);
	}

	BoundingBox cellsInside() {
		return frame.box(-4 * cols + 1, 1, CELLS_BACK + 1, 4 * cols - 1, CELL_HEIGHT, tunnelF0 - 1);
	}

	BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	private BoundingBox computeBox() {
		int reach = 2;
		BoundingBox all = frame.box(-SHELL, -2, CELLS_BACK - 1, SHELL, ceiling() + 1, -length - 3);
		all = all.encapsulate(frame.box(-4 * cols - 1, -1, 2, 4 * cols + 1, CELL_HEIGHT + 2, tunnelF0 + 1));
		if (well != null) {
			// Version 3: the body's jacket ({@code ArchiveShape.JACKET}) round the hall and the cells, then the well.
			int jacket = ArchiveShape.JACKET;
			all = all.encapsulate(frame.box(-HALF - jacket, 1 - jacket, -1 + jacket, HALF + jacket, height() + jacket, -length - jacket));
			all = all.encapsulate(frame.box(-4 * cols + 1 - jacket, 1 - jacket, CELLS_BACK + 1 - jacket, 4 * cols - 1 + jacket,
					CELL_HEIGHT + jacket, tunnelF0 - 1 + jacket));
			all = all.encapsulate(well.box());
		}
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
	 * Version 3: an open way's notch (its first cells past the mouth, opened to the sky) has no water in it or beside it,
	 * and its two columns' land within 4 of each other (SC2-H's audit: a notch cleared beside a lake leaks).
	 */
	/**
	 * Version 3: a closed way's exit: as measured where its notch is dry, else its mouth (no outer part: nothing carved
	 * past its plug, no notch; SC2-H's audit: a closed way's notch cleared beside a lake leaks).
	 */
	int closedExit(int left, int[] measured, Terrain terrain) {
		return notchDry(left, measured[0], measured[1], terrain) ? measured[1] : measured[0];
	}

	boolean notchDry(int left, int mouth, int exit, Terrain terrain) {
		for (int i = mouth; i <= Math.min(mouth + ArchiveShape.NOTCH, exit); i++) {
			BlockPos a = at(left, 0, tunnelF0 + i);
			BlockPos b = at(left + 1, 0, tunnelF0 + i);
			if (Math.abs(terrain.ground(a.getX(), a.getZ()) - terrain.ground(b.getX(), b.getZ())) > 4) {
				return false;
			}
			for (int side = -1; side <= 2; side++) {
				BlockPos c = at(left + side, 0, tunnelF0 + i);
				if (terrain.wet(c.getX(), c.getZ())) {
					return false;
				}
			}
		}
		return true;
	}

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
		int maxTunnel = version >= 3 ? MAX_TUNNEL3 : MAX_TUNNEL;
		int[] grounds = new int[maxTunnel + MAX_NOTCH + 1];
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
		while (i <= maxTunnel) {
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
		return u >= 0 && u <= height() && inHall(localR(feet.getX(), feet.getZ()), localF(feet.getX(), feet.getZ()));
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
