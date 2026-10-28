package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Frame;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.Seal;
import com.forzacode.a1016_02.world.sc2.kit.Writer;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Doorway;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.House;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Level;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Stair;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Way;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * What lies under the pyramid past its pit's dead end (docs/SELF_CREATED_2_R3.md, "33 The depths", D-120 to D-124): from
 * version {@value #FROM} of the pyramid's shape, on land, open or sealed, its tunnel ends at a door into the first house,
 * and under it the rooms, the warren and the end ({@link DepthsPlan}). A pyramid with a pit asks {@link #of} at the end of
 * its constructor; its box encloses {@link #box()}, its fill calls {@link #fill} last (after the pit, its torch, the
 * tunnel and every seal), and its interior holds {@link #inside}. {@link #NONE} for every older version, the sea form,
 * and where the depths do not fit (the rooms dropped first, then all of it: the tunnel ends as in version 4).
 *
 * <p>Built as every shape: decided here once (the levels kept, the grids, the maze, the camp, the stuffed doorways, the
 * torch each house of the rooms keeps), never per chunk; the seal first (every cell within {@link Seal#REACH} of the
 * depths' air that leaks becomes rock, never into the pyramid's own inside), then the houses, the doorways, the stairs.
 */
public class Depths {
	/** The first version of the pyramid's shape that has depths ({@code PyramidShape.VERSION} from R3-D). */
	public static final int FROM = 5;
	/**
	 * From this version (FIX-R3-C, R3A-5 and R3A-9): beside L1's front door a pane, so the house's light reads from the pit
	 * as a lit window beside a shut door (and its going dark as a black one); a stuffed doorway's lower cell dirt and its
	 * upper cobblestone, the plank over it torn out and dirt in its place.
	 */
	public static final int WINDOW_FROM = 6;
	/** No depths: no box, nothing written, nothing inside. */
	public static final Depths NONE = new Depths();
	/** The sample points of a level for the land and the biome: its corners, its sides' middles, its middle. */
	static final double[][] SAMPLES = {{0, 0}, {0.5, 0}, {1, 0}, {0, 0.5}, {0.5, 0.5}, {1, 0.5}, {0, 1}, {0.5, 1}, {1, 1}};

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
	private static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
	private static final BlockState WOOL = Blocks.WOOL.white().defaultBlockState();
	private static final BlockState TABLE = Blocks.CRAFTING_TABLE.defaultBlockState();
	/** 20's house: its roof course over its floor. */
	private static final int ROOF = 4;

	/** Tests and the gallery only: when set on this thread, {@link #of} drops the rooms (L2) as if they did not fit. */
	private static final ThreadLocal<Boolean> DROP_ROOMS = ThreadLocal.withInitial(() -> false);

	private final @Nullable PyramidShape shape;
	private final @Nullable DepthsPlan plan;
	private final @Nullable BoundingBox box;
	private final @Nullable BoundingBox air;
	private final LongOpenHashSet stairCells = new LongOpenHashSet();
	private final LongOpenHashSet doorCells = new LongOpenHashSet();
	/** The seal's zones by chunk, made on the first fill. */
	private volatile @Nullable Long2ObjectOpenHashMap<int[]> zones;

	protected Depths() {
		this.shape = null;
		this.plan = null;
		this.box = null;
		this.air = null;
	}

	Depths(PyramidShape shape, DepthsPlan plan) {
		this.shape = shape;
		this.plan = plan;
		int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Stair stair : plan.stairs()) {
			for (int[] c : stair.cells()) {
				for (int y = c[2]; y < c[2] + DepthsPlan.HEADROOM; y++) {
					stairCells.add(pack(c[0], y, c[1]));
				}
				grow(lo, hi, c[0], c[2], c[1]);
				grow(lo, hi, c[0], c[2] + 2, c[1]);
			}
		}
		for (Doorway d : plan.doorways()) {
			if (d.way().passable()) {
				doorCells.add(pack(d.s(), d.feet(), d.f()));
				doorCells.add(pack(d.s(), d.feet() + 1, d.f()));
				grow(lo, hi, d.s(), d.feet(), d.f());
				grow(lo, hi, d.s(), d.feet() + 1, d.f());
			}
			if (d.way() == Way.BROKEN) {
				for (int[] c : brokenFrame(d)) {
					doorCells.add(pack(c[0], c[1], c[2]));
				}
			}
		}
		for (House h : plan.houses()) {
			grow(lo, hi, h.s0() - 2, h.feet(), h.f0() - 2);
			grow(lo, hi, h.s0() + 2, h.feet() + 2, h.f0() + 2);
		}
		int[] span = plan.hisTunnelSpan();
		int[] sides = plan.hisTunnelSides();
		int feet4 = plan.l4().feet();
		grow(lo, hi, sides[0], feet4, span[0]);
		grow(lo, hi, sides[1], feet4 + 1, span[1]);
		grow(lo, hi, plan.l4().s0(), feet4, plan.l4().f0() + 3);
		BoundingBox inner = plan.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
		this.air = inner;
		// Everything written: the walls one out, the seal two out from the air.
		this.box = inner.inflatedBy(Seal.REACH);
	}

	private static void grow(int[] lo, int[] hi, int s, int y, int f) {
		lo[0] = Math.min(lo[0], s);
		lo[1] = Math.min(lo[1], y);
		lo[2] = Math.min(lo[2], f);
		hi[0] = Math.max(hi[0], s);
		hi[1] = Math.max(hi[1], y);
		hi[2] = Math.max(hi[2], f);
	}

	static long pack(int s, int y, int f) {
		return ((long) (s & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (f & 0xFFFFF);
	}

	// --- deciding ---

	/**
	 * The depths under this pyramid over this terrain: the four levels where they fit; else without the rooms (L2) if
	 * only they do not; else {@link #NONE}. A level fits when its every cell stands inside the footprint less
	 * {@value DepthsPlan#FOOTPRINT_IN}, its seal over the kit's height margin and where worldgen fills, at least
	 * {@value DepthsPlan#UNDER_GROUND} under the noise ground at its 9 samples, and none of those 9 at its feet is the deep
	 * dark. Never a start the kit would refuse for it.
	 */
	public static Depths of(PyramidShape shape, Terrain terrain) {
		if (shape.version() < FROM || !shape.hasPit() || shape.sea() || shape.tunnelEnd().isEmpty()) {
			return NONE;
		}
		boolean dropRooms = DROP_ROOMS.get();
		if (!dropRooms) {
			DepthsPlan full = plan(shape, true);
			List<Level> misfits = misfits(shape, full, terrain);
			if (misfits.isEmpty()) {
				return new Depths(shape, full);
			}
			if (!misfits.equals(List.of(Level.L2))) {
				return NONE;
			}
		}
		DepthsPlan bare = plan(shape, false);
		return misfits(shape, bare, terrain).isEmpty() ? new Depths(shape, bare) : NONE;
	}

	/** Tests and the gallery: runs {@code body} with the rooms dropped wherever depths are made on this thread. */
	public static <T> T withoutRooms(java.util.function.Supplier<T> body) {
		DROP_ROOMS.set(true);
		try {
			return body.get();
		} finally {
			DROP_ROOMS.set(false);
		}
	}

	/** The plan for this pyramid (its frame, its pit's floor, its size and seed). */
	public static DepthsPlan plan(PyramidShape shape, boolean rooms) {
		return new DepthsPlan(shape.centerX(), shape.centerZ(), shape.facing(), shape.pitBottomY(), shape.size(), shape.seed(), rooms);
	}

	/** The levels of this plan that do not fit here (in order; empty if all do). */
	public static List<Level> misfits(PyramidShape shape, DepthsPlan plan, Terrain terrain) {
		List<Level> out = new ArrayList<>();
		int limit = shape.halfWidth() - DepthsPlan.FOOTPRINT_IN;
		int lowestFilled = terrain.minY() + Heights.BOTTOM;
		int[] all = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		// 1. Inside the footprint less 3 and over the kit's height margin (nothing read).
		for (Level level : plan.levels()) {
			int[] ext = extent(plan, level);
			all = union(all, ext);
			int bottom = plan.pitFeet() + level.down - 1 - Seal.REACH;
			if (Math.max(Math.abs(ext[0]), Math.abs(ext[2])) > limit || Math.max(Math.abs(ext[1]), Math.abs(ext[3])) > limit || bottom < lowestFilled) {
				out.add(level);
			}
		}
		for (Stair stair : plan.stairs()) {
			for (int[] c : stair.cells()) {
				all = union(all, new int[] {c[0], c[1], c[0], c[1]});
				if (Math.max(Math.abs(c[0]), Math.abs(c[1])) > limit && !out.contains(stair.to())) {
					out.add(stair.to());
				}
			}
		}
		if (sinks(out)) {
			return out;
		}
		// 2. No level in the deep dark (9 samples at its feet; the biome is a cheap read).
		for (Level level : plan.levels()) {
			int[] ext = extent(plan, level);
			int feet = plan.pitFeet() + level.down;
			for (int k = 0; k < SAMPLES.length && !out.contains(level); k++) {
				int[] sf = sample(ext, k);
				if (terrain.biome(plan.x(sf[0], sf[1]), feet, plan.z(sf[0], sf[1])).is(Biomes.DEEP_DARK)) {
					out.add(level);
				}
			}
			if (sinks(out)) {
				return out;
			}
		}
		// 3. The land at least 12 over the highest roof (the first house's) at 9 samples over all of it (a ground read computes
		// a noise column: the dearest part, so asked once for every level).
		int roof = plan.pitFeet() + 3;
		for (int k = 0; k < SAMPLES.length; k++) {
			int[] sf = sample(all, k);
			if (terrain.ground(plan.x(sf[0], sf[1]), plan.z(sf[0], sf[1])) < roof + DepthsPlan.UNDER_GROUND) {
				out.add(0, Level.L1);
				return out;
			}
		}
		// 4. Where worldgen fills under the end (a data pack may start the noise over the level's bottom: the kit's own
		// margin is from there): one read under its middle, two more if that one is a cave.
		int[] end = extent(plan, Level.L4);
		int under = plan.pitFeet() + Level.L4.down - 1 - Seal.REACH - Heights.BOTTOM;
		boolean filled = false;
		for (int k = 0; k < 3 && !filled; k++) {
			int[] sf = sample(end, new int[] {4, 0, 8}[k]);
			filled = !terrain.block(plan.x(sf[0], sf[1]), under, plan.z(sf[0], sf[1])).isAir();
		}
		if (!filled) {
			out.add(Level.L4);
		}
		return out;
	}

	/** True if these misfits drop the whole (anything but the rooms alone). */
	private static boolean sinks(List<Level> misfits) {
		return !misfits.isEmpty() && !misfits.equals(List.of(Level.L2));
	}

	private static int[] union(int[] a, int[] b) {
		return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
	}

	/** The {@code k}th of the 9 samples over an extent {s0, f0, s1, f1}: {s, f}. */
	private static int[] sample(int[] ext, int k) {
		return new int[] {(int) Math.round(ext[0] + (ext[2] - ext[0]) * SAMPLES[k][0]), (int) Math.round(ext[1] + (ext[3] - ext[1]) * SAMPLES[k][1])};
	}

	/** A level's walls' extent in the frame: {s0, f0, s1, f1} (L4: its house and his tunnel). */
	static int[] extent(DepthsPlan plan, Level level) {
		int[] e = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (House h : plan.houses(level)) {
			e[0] = Math.min(e[0], h.s0() - 3);
			e[1] = Math.min(e[1], h.f0() - 3);
			e[2] = Math.max(e[2], h.s0() + 3);
			e[3] = Math.max(e[3], h.f0() + 3);
		}
		if (level == Level.L4) {
			int[] sides = plan.hisTunnelSides();
			e[0] = Math.min(e[0], Math.min(sides[0], sides[1]) - 1);
			e[2] = Math.max(e[2], Math.max(sides[0], sides[1]) + 1);
			e[3] = Math.max(e[3], plan.hisTunnelSpan()[1] + 1);
			e[1] = Math.min(e[1], plan.doorway(Way.IRON).f());
		}
		return e;
	}

	// --- reading it ---

	/** True if there are depths here. */
	public boolean present() {
		return plan != null;
	}

	/** The plan, or null for {@link #NONE}. */
	public @Nullable DepthsPlan plan() {
		return plan;
	}

	/** Everything the depths may write, if any (the pyramid's box encloses it). */
	public Optional<BoundingBox> box() {
		return Optional.ofNullable(box);
	}

	/** True for a cell of the depths' inside (the pyramid's interior holds it): every level's air, its doorways, the stairs. */
	public boolean inside(int x, int y, int z) {
		if (plan == null || air == null || !air.isInside(x, y, z)) {
			return false;
		}
		int s = plan.s(x, z);
		int f = plan.f(x, z);
		long key = pack(s, y, f);
		if (stairCells.contains(key) || doorCells.contains(key)) {
			return true;
		}
		Level level = band(y);
		return level != null && inLevel(level, s, y - (plan.pitFeet() + level.down), f);
	}

	/** The level whose band (feet to two over them) holds this height, or null. */
	private @Nullable Level band(int y) {
		int dy = y - plan.pitFeet();
		for (Level level : plan.levels()) {
			if (dy >= level.down && dy <= level.down + 2) {
				return level;
			}
		}
		return null;
	}

	/** True for a level's air at {@code (s, f)}, {@code up} over its feet (0 to 2): a house's inside, the end's gap and tunnel. */
	private boolean inLevel(Level level, int s, int up, int f) {
		switch (level) {
			case L1:
				return plan.l1().interior(s, f);
			case L2:
			case L3: {
				int[] grid = level == Level.L2 ? plan.roomsGrid() : plan.warrenGrid();
				int hs = 3 * grid[0];
				int hf = 3 * grid[1];
				return Math.abs(s) < hs && Math.abs(f) < hf && Math.floorMod(s + 3, DepthsPlan.PITCH) != 0
						&& Math.floorMod(f + 3, DepthsPlan.PITCH) != 0;
			}
			case L4: {
				House end = plan.l4();
				if (end.interior(s, f) || f == end.f0() + 3 && Math.abs(s - end.s0()) <= 2) {
					return true;
				}
				int[] span = plan.hisTunnelSpan();
				int[] sides = plan.hisTunnelSides();
				return up <= 1 && f >= span[0] && f <= span[1] && (s == sides[0] || s == sides[1]);
			}
			default:
				return false;
		}
	}

	/** The level a cell counts as (a stair: the level it leaves going down), or null outside the depths. */
	public @Nullable Level levelOf(int x, int y, int z) {
		if (!inside(x, y, z)) {
			return null;
		}
		int s = plan.s(x, z);
		int f = plan.f(x, z);
		for (Stair stair : plan.stairs()) {
			for (int[] c : stair.cells()) {
				if (c[0] == s && c[1] == f && y >= c[2] && y < c[2] + DepthsPlan.HEADROOM) {
					return stair.from();
				}
			}
		}
		Level level = band(y);
		if (level == null) {
			// A doorway's upper cell or a broken frame: the band under it.
			level = band(y - 1);
		}
		return level;
	}

	/** The house whose inside (or a doorway in its walls) holds this cell, or null. */
	public @Nullable House houseOf(int x, int y, int z) {
		if (plan == null) {
			return null;
		}
		int s = plan.s(x, z);
		int f = plan.f(x, z);
		for (House h : plan.houses()) {
			if (y >= h.feet() && y <= h.feet() + 2 && h.interior(s, f)) {
				return h;
			}
		}
		return null;
	}

	// --- writing it ---

	/** Writes the depths' part inside {@code clip} (called last by the pyramid's fill). */
	public void fill(Writer w, BoundingBox clip) {
		if (plan == null || box == null || !box.intersects(clip)) {
			return;
		}
		// 1. The seal round every level and stair (what leaks within reach becomes rock; the pyramid's inside is left).
		seal(w, clip);
		// 2. The houses.
		for (House h : plan.houses()) {
			if (plan.box(h).intersects(clip)) {
				house(w, h);
			}
		}
		// 3. The doorways, the panes either side of the rooms' doors, the camp.
		for (Doorway d : plan.doorways()) {
			doorway(w, d);
		}
		if (shape.version() >= WINDOW_FROM) {
			BlockPos window = window();
			w.set(window, PANE);
		}
		if (plan.camp() >= 0 && plan.box(plan.house(plan.camp())).intersects(clip)) {
			camp(w, plan.house(plan.camp()));
		}
		// 4. The stairs, the iron door's wall, the end's gap, his tunnel and its cobblestone.
		for (Stair stair : plan.stairs()) {
			for (int[] c : stair.cells()) {
				for (int y = c[2]; y < c[2] + DepthsPlan.HEADROOM; y++) {
					set(w, c[0], y, c[1], AIR);
				}
			}
		}
		Doorway iron = plan.doorway(Way.IRON);
		for (int d = -1; d <= 1; d++) {
			for (int y = iron.feet() - 1; y <= iron.feet() + 2; y++) {
				if (d != 0 || y < iron.feet() || y > iron.feet() + 1) {
					set(w, iron.s() + d, y, iron.f(), COBBLE);
				}
			}
		}
		House end = plan.l4();
		for (int d = -2; d <= 2; d++) {
			for (int y = end.feet(); y <= end.feet() + 2; y++) {
				set(w, end.s0() + d, y, end.f0() + 3, AIR);
			}
		}
		int[] span = plan.hisTunnelSpan();
		for (int f = span[0]; f <= span[1]; f++) {
			for (int side : plan.hisTunnelSides()) {
				set(w, side, end.feet(), f, AIR);
				set(w, side, end.feet() + 1, f, AIR);
			}
		}
		int[] cobble = plan.hisCobble();
		set(w, cobble[0], cobble[2], cobble[1], COBBLE);
	}

	/**
	 * Version {@value #WINDOW_FROM}'s window (R3A-5): in L1's front wall (the tunnel's end face) beside its door, the
	 * tunnel's side-1 cell at eye height: a pane, the house's light seen from the tunnel and the pit's floor.
	 */
	public BlockPos window() {
		House l1 = plan.l1();
		return plan.pos(l1.s0() + 1, l1.feet() + 1, DepthsPlan.L1_FRONT);
	}

	private void set(Writer w, int s, int y, int f, BlockState state) {
		w.set(plan.x(s, f), y, plan.z(s, f), state);
	}

	/** The rock a sealed cell becomes (stone, deepslate under 0, as the land round it). */
	static BlockState rock(int y) {
		return y < 0 ? DEEPSLATE : STONE;
	}

	/**
	 * {@link Seal#around}'s rule for every part of the depths (each level, each stair: one range a column): every leaking
	 * cell within {@link Seal#REACH} of a part's air becomes rock, but the pyramid's own inside and the depths' own air. The
	 * zones are worked out once a shape ({@link #zones}); a chunk reads its own.
	 */
	private void seal(Writer w, BoundingBox clip) {
		Long2ObjectOpenHashMap<int[]> zones = zones();
		for (int cx = clip.minX() >> 4; cx <= clip.maxX() >> 4; cx++) {
			for (int cz = clip.minZ() >> 4; cz <= clip.maxZ() >> 4; cz++) {
				int[] q = zones.get(ChunkPos.pack(cx, cz));
				if (q == null) {
					continue;
				}
				for (int i = 0; i < q.length; i += 4) {
					int x = q[i];
					int z = q[i + 1];
					if (x < clip.minX() || x > clip.maxX() || z < clip.minZ() || z > clip.maxZ()) {
						continue;
					}
					for (int y = Math.max(q[i + 2], clip.minY()); y <= Math.min(q[i + 3], clip.maxY()); y++) {
						if (Seal.leaks(w.get(x, y, z)) && !inside(x, y, z) && !shape.core(x, y, z) && !shape.pit(x, y, z) && !shape.tunnel(x, y, z)) {
							w.set(x, y, z, rock(y));
						}
					}
				}
			}
		}
	}

	/** The seal's zones by chunk ({x, z, lo, hi} each), made on first ask. */
	private Long2ObjectOpenHashMap<int[]> zones() {
		Long2ObjectOpenHashMap<int[]> made = zones;
		if (made == null) {
			synchronized (this) {
				made = zones;
				if (made == null) {
					made = buildZones();
					zones = made;
				}
			}
		}
		return made;
	}

	/** Each part's own columns (its air's range in each), spread {@link Seal#REACH} round, gathered by chunk. */
	private Long2ObjectOpenHashMap<int[]> buildZones() {
		List<Long2LongOpenHashMap> parts = new ArrayList<>();
		for (Level level : plan.levels()) {
			int feet = plan.pitFeet() + level.down;
			Long2LongOpenHashMap own = new Long2LongOpenHashMap();
			for (House h : plan.houses(level)) {
				for (int s = h.s0() - 2; s <= h.s0() + 2; s++) {
					for (int f = h.f0() - 2; f <= h.f0() + 2; f++) {
						own.put(ChunkPos.pack(plan.x(s, f), plan.z(s, f)), Seal.range(feet, feet + 2));
					}
				}
			}
			for (Doorway d : plan.doorways()) {
				if (d.feet() == feet && d.way().passable()) {
					own.put(ChunkPos.pack(plan.x(d.s(), d.f()), plan.z(d.s(), d.f())), Seal.range(feet, feet + 2));
				}
				if (d.feet() == feet && d.way() == Way.BROKEN) {
					for (int[] c : brokenFrame(d)) {
						own.put(ChunkPos.pack(plan.x(c[0], c[2]), plan.z(c[0], c[2])), Seal.range(feet, feet + 2));
					}
				}
			}
			if (level == Level.L4) {
				House end = plan.l4();
				for (int d = -2; d <= 2; d++) {
					own.put(ChunkPos.pack(plan.x(end.s0() + d, end.f0() + 3), plan.z(end.s0() + d, end.f0() + 3)), Seal.range(feet, feet + 2));
				}
				int[] span = plan.hisTunnelSpan();
				for (int f = span[0]; f <= span[1]; f++) {
					for (int side : plan.hisTunnelSides()) {
						own.put(ChunkPos.pack(plan.x(side, f), plan.z(side, f)), Seal.range(feet, feet + 1));
					}
				}
			}
			parts.add(own);
		}
		for (Stair stair : plan.stairs()) {
			Long2LongOpenHashMap own = new Long2LongOpenHashMap();
			own.defaultReturnValue(Seal.NONE);
			for (int[] c : stair.cells()) {
				long key = ChunkPos.pack(plan.x(c[0], c[1]), plan.z(c[0], c[1]));
				long was = own.get(key);
				own.put(key, was == Seal.NONE ? Seal.range(c[2], c[2] + 2) : Seal.range(Math.min(Seal.lo(was), c[2]), Math.max(Seal.hi(was), c[2] + 2)));
			}
			parts.add(own);
		}
		Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntArrayList> byChunk = new Long2ObjectOpenHashMap<>();
		int reach = Seal.REACH;
		for (Long2LongOpenHashMap own : parts) {
			Long2LongOpenHashMap zone = new Long2LongOpenHashMap();
			zone.defaultReturnValue(Seal.NONE);
			for (Long2LongMap.Entry e : own.long2LongEntrySet()) {
				int x = ChunkPos.getX(e.getLongKey());
				int z = ChunkPos.getZ(e.getLongKey());
				long range = e.getLongValue();
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						long key = ChunkPos.pack(x + dx, z + dz);
						long was = zone.get(key);
						int lo = Seal.lo(range) - reach;
						int hi = Seal.hi(range) + reach;
						zone.put(key, was == Seal.NONE ? Seal.range(lo, hi) : Seal.range(Math.min(Seal.lo(was), lo), Math.max(Seal.hi(was), hi)));
					}
				}
			}
			for (Long2LongMap.Entry e : zone.long2LongEntrySet()) {
				int x = ChunkPos.getX(e.getLongKey());
				int z = ChunkPos.getZ(e.getLongKey());
				it.unimi.dsi.fastutil.ints.IntArrayList list = byChunk.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new it.unimi.dsi.fastutil.ints.IntArrayList());
				list.add(x);
				list.add(z);
				list.add(Seal.lo(e.getLongValue()));
				list.add(Seal.hi(e.getLongValue()));
			}
		}
		Long2ObjectOpenHashMap<int[]> out = new Long2ObjectOpenHashMap<>();
		for (Long2ObjectMap.Entry<it.unimi.dsi.fastutil.ints.IntArrayList> e : byChunk.long2ObjectEntrySet()) {
			out.put(e.getLongKey(), e.getValue().toIntArray());
		}
		return out;
	}

	/** A house's frame: 20's (its origin the front-left corner at its floor, its front toward the pit). */
	public Frame frame(House h) {
		BoundingBox b = plan.box(h);
		return Frame.square(new BlockPos(b.minX(), h.feet() - 1, b.minZ()), DepthsPlan.HOUSE, plan.facing().getOpposite());
	}

	/**
	 * One house: 20's first house (R11-5) course for course, a cobblestone ring and course, two oak plank courses, the flat
	 * roof, the mossy floor. L1 and the end keep its glass (onto the rock, made stone behind each), L1 its two torches and
	 * L2 one of them; L3 neither. Its doorways are written after.
	 */
	private void house(Writer w, House h) {
		Frame fr = frame(h);
		for (int r = 0; r < DepthsPlan.HOUSE; r++) {
			for (int f = 0; f > -DepthsPlan.HOUSE; f--) {
				boolean wall = r == 0 || r == DepthsPlan.HOUSE - 1 || f == 0 || f == -(DepthsPlan.HOUSE - 1);
				w.set(fr.pos(r, 0, f), wall ? COBBLE : MOSSY);
				for (int u = 1; u < ROOF; u++) {
					w.set(fr.pos(r, u, f), wall ? (u == 1 ? COBBLE : PLANKS) : AIR);
				}
				w.set(fr.pos(r, ROOF, f), PLANKS);
			}
		}
		if (h.level() == Level.L1 || h.level() == Level.L4) {
			for (int[] g : glass()) {
				if (h.level() == Level.L1 && shape.version() >= WINDOW_FROM && isBackGlassInLine(g)) {
					// Version 6: the back wall's glass in line with the front window is 20's plank, so the window looks onto the
					// lit wall (the gallery: through both, the stone behind the back one showed black).
					continue;
				}
				BlockPos at = fr.pos(g[0], g[1], g[2]);
				w.set(at, GLASS);
				BlockPos behind = fr.pos(g[0] + g[3], g[1], g[2] + g[4]);
				if (!shape.core(behind.getX(), behind.getY(), behind.getZ()) && !shape.tunnel(behind.getX(), behind.getY(), behind.getZ()) && !inside(behind.getX(),
						behind.getY(), behind.getZ())) {
					w.set(behind, rock(behind.getY()));
				}
			}
		}
		if (h.level() == Level.L1) {
			for (BlockPos t : torches(h)) {
				w.set(t, torchState(h, t));
			}
		} else if (h.level() == Level.L2) {
			BlockPos t = torches(h).get(plan.torchLeft(h.index()) ? 0 : 1);
			w.set(t, torchState(h, t));
		}
	}

	/** The back wall's glass on the front window's line (the tunnel's side 1: the frame's {@code r} 2). */
	static boolean isBackGlassInLine(int[] g) {
		return g[2] == -(DepthsPlan.HOUSE - 1) && g[0] == DepthsPlan.HOUSE / 2 - 1;
	}

	/** 20's glass in its frame ({r, u, f} and the step out of the wall), but the front doorway's. */
	static int[][] glass() {
		int last = DepthsPlan.HOUSE - 1;
		int mid = DepthsPlan.HOUSE / 2;
		return new int[][] {{DepthsPlan.HOUSE - 3, 2, 0, 0, 1}, {DepthsPlan.HOUSE - 2, 2, 0, 0, 1}, {mid, 2, -last, 0, -1}, {mid - 1, 2, -last, 0, -1},
				{0, 2, -3, -1, 0}, {0, 2, -4, -1, 0}, {last, 2, -3, 1, 0}, {last, 2, -4, 1, 0}};
	}

	/** A house's two torch places (20's: on the side walls a block in from the doorway), the left one first. */
	public List<BlockPos> torches(House h) {
		Frame fr = frame(h);
		return List.of(fr.pos(1, 2, -1), fr.pos(DepthsPlan.HOUSE - 2, 2, -1));
	}

	/** The torch that belongs at one of a house's places (20's facing, turned to its frame). */
	public BlockState torchState(House h, BlockPos place) {
		Frame fr = frame(h);
		boolean left = place.equals(fr.pos(1, 2, -1));
		return fr.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, left ? Direction.EAST : Direction.WEST));
	}

	/** A doorway's two cells (lower first). */
	public List<BlockPos> cells(Doorway d) {
		return List.of(plan.pos(d.s(), d.feet(), d.f()), plan.pos(d.s(), d.feet() + 1, d.f()));
	}

	/** The door's facing in a doorway (along the wall's normal, toward {@code +s} or {@code +f}). */
	public Direction doorFacing(Doorway d) {
		return d.acrossS() ? plan.right() : plan.facing();
	}

	/** A door's half, closed. */
	public BlockState doorState(Doorway d, boolean upper) {
		BlockState base = (d.way() == Way.IRON ? Blocks.IRON_DOOR : Blocks.OAK_DOOR).defaultBlockState();
		return base.setValue(DoorBlock.FACING, doorFacing(d)).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false)
				.setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	private void doorway(Writer w, Doorway d) {
		BlockPos lower = plan.pos(d.s(), d.feet(), d.f());
		BlockPos upper = lower.above();
		switch (d.way()) {
			case DOOR, FRONT, BACK, STAIR_TOP, IRON, END_FRONT -> {
				w.set(lower, doorState(d, false));
				w.set(upper, doorState(d, true));
			}
			case OPEN, STAIR_FOOT -> {
				w.set(lower, AIR);
				w.set(upper, AIR);
			}
			case STUFFED -> {
				if (shape.version() >= WINDOW_FROM) {
					// R3A-9: dirt pushed in low, a cobblestone on it, the plank over the doorway torn out and dirt packed into its
					// place: done in a hurry, and it never reads as the house's cobblestone course.
					w.set(lower, DIRT);
					w.set(upper, COBBLE);
					w.set(upper.above(), DIRT);
				} else {
					boolean low = (Hash.of(plan.size(), d.index(), d.s(), d.f()) & 1) == 0;
					w.set(lower, low ? COBBLE : DIRT);
					w.set(upper, Hash.below(Hash.of(d.index(), d.f(), d.s()), 3) == 0 ? (low ? COBBLE : DIRT) : low ? DIRT : COBBLE);
				}
			}
			case BROKEN -> {
				for (int[] c : brokenFrame(d)) {
					set(w, c[0], c[1], c[2], AIR);
				}
				int[] stone = brokenStone(d);
				set(w, stone[0], stone[1], stone[2], COBBLE);
			}
		}
		if (d.way() == Way.DOOR && house(d.a()).level() == Level.L2) {
			// A pane either side of every door of the rooms, at eye height: a lit room through a window, another past it.
			for (int along = -1; along <= 1; along += 2) {
				int s = d.acrossS() ? d.s() : d.s() + along;
				int f = d.acrossS() ? d.f() + along : d.f();
				set(w, s, d.feet() + 1, f, PANE);
			}
		}
	}

	private House house(int index) {
		return plan.house(index);
	}

	/** A broken doorway's cells ({s, y, f}): its two, and the planks round it (beside it and over it; the cobblestone course kept). */
	List<int[]> brokenFrame(Doorway d) {
		List<int[]> out = new ArrayList<>();
		for (int along = -1; along <= 1; along++) {
			int s = d.acrossS() ? d.s() : d.s() + along;
			int f = d.acrossS() ? d.f() + along : d.f();
			for (int up = along == 0 ? 0 : 1; up <= 2; up++) {
				out.add(new int[] {s, d.feet() + up, f});
			}
		}
		return out;
	}

	/** The cobblestone left on the floor outside a broken doorway ({s, y, f}): a step out, a step aside. */
	int[] brokenStone(Doorway d) {
		House outside = house(d.a() == plan.camp() ? d.b() : d.a());
		int out = d.acrossS() ? Integer.signum(outside.s0() - d.s()) : Integer.signum(outside.f0() - d.f());
		return d.acrossS() ? new int[] {d.s() + out, d.feet(), d.f() + 1} : new int[] {d.s() + 1, d.feet(), d.f() + out};
	}

	/**
	 * The camp's cells in its house: against the wall facing its broken doorway (seen through the hole) a crafting table, a
	 * cold furnace and a chest, the far doorway's approach left clear; a strip of white wool 1 x 2 along a side wall by the
	 * hole. In order: table, furnace, chest, wool, wool.
	 */
	public List<BlockPos> campCells(House h) {
		int[] out = campOut(h);
		int[] side = {-out[1], out[0]};
		return List.of(campCell(h, -2, -2, out, side), campCell(h, -2, -1, out, side), campCell(h, -2, 2, out, side), campCell(h, 1, -2, out, side),
				campCell(h, 2, -2, out, side));
	}

	/** The unit step ({s, f}) from the camp's middle toward its broken doorway. */
	private int[] campOut(House h) {
		if (plan.broken() < 0) {
			return new int[] {0, 1};
		}
		Doorway d = plan.doorways().get(plan.broken());
		return new int[] {Integer.signum(d.s() - h.s0()), Integer.signum(d.f() - h.f0())};
	}

	private BlockPos campCell(House h, int alongOut, int alongSide, int[] out, int[] side) {
		return plan.pos(h.s0() + out[0] * alongOut + side[0] * alongSide, h.feet(), h.f0() + out[1] * alongOut + side[1] * alongSide);
	}

	/**
	 * The camp: a crafting table and a cold furnace against one wall, a chest beside them, a strip of white wool 1 x 2 on
	 * the floor; in the chest a worn stone pickaxe, two bread, a compass and nine cobblestone (nothing that lights).
	 */
	private void camp(Writer w, House h) {
		List<BlockPos> cells = campCells(h);
		int[] out = campOut(h);
		// Toward the hole (into the room from the far wall).
		Direction in = out[0] != 0 ? (out[0] > 0 ? plan.right() : plan.right().getOpposite()) : (out[1] > 0 ? plan.facing() : plan.facing().getOpposite());
		w.set(cells.get(0), TABLE);
		w.set(cells.get(1), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, in));
		w.set(cells.get(2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, in));
		w.set(cells.get(3), WOOL);
		w.set(cells.get(4), WOOL);
		w.contents(cells.get(2), campChest());
	}

	/** The camp's chest. */
	public static List<ItemStack> campChest() {
		ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
		int max = pick.getMaxDamage();
		pick.set(DataComponents.DAMAGE, max - Math.max(1, max / 5));
		return List.of(pick, new ItemStack(Items.BREAD, 2), new ItemStack(Items.COMPASS), new ItemStack(Items.COBBLESTONE, 9));
	}

	/** Called once from {@code S16.init} (the answers, the commands, the exhibits). */
	public static void init() {
		DepthsAnswers.init();
		if (com.forzacode.a1016_02.core.Gallery.active()) {
			Sc33Gallery.register();
		}
	}
}
