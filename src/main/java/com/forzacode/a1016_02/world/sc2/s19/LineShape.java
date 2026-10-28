package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.gen.Vegetation;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Writer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One segment of feature 19 as a {@link Shape} (docs/SELF_CREATED_2.md, 19; the layout is {@link LinePlan}'s): every
 * cell of its ribs and panels from its bottom to its top is set, through whatever was there (land, trees, water, caves,
 * lava): the panels obsidian, the ribs obsidian up to {@value #WEEPS_FROM} over the ground and crying obsidian above
 * (seen from far as a ruled grid, faintly lit at night, never purple or lit within reach or in a cave); but the openings
 * at the foot of the panels (two wide and two high, at the ground of their columns, or at the water's top where the
 * column is wet, the lower cell water then) and the cut (left as it was). Where the land lies under its bottom (a flat
 * world) it reaches down to stand on it. At the foot of the panels the ground dies two blocks out on both sides (grass
 * to coarse dirt, the plants on it gone). Every cell is a function of the segment and the terrain's numbers, so the
 * chunks of one segment agree in any order,
 * the feature and {@code Live.place} build the same thing, and a chunk built twice ends the same. The place's anchor is
 * the cut's middle at its ground, its mouth the opening nearest the origin (facing the origin along the axis, D-095),
 * its inside the plane itself (one stands in it only in an opening or the cut).
 */
public final class LineShape implements Shape {
	/** How far its ribs and dead ground reach out of the plane on either side (a heavy rib, the dead ground's edge). */
	public static final int REACH = 2;
	/** Version 3: how far the lit foot reaches out of the plane on either side (its box; every version's chunks are asked this far). */
	public static final int LIT_REACH = 4;
	/** Version 3: the lit foot runs this far along the wall on either side of each opening (8 in all, the opening in its middle). */
	public static final int LIT_ALONG = 3;
	/** Version 3: the lit foot's lights against the wall's faces reach this far up from the opening's floor. */
	public static final int WALL_LIGHT_UP = 7;
	/**
	 * Its geometry's version (R12-2): a segment fixed in a world keeps the version it was fixed with; a change here raises
	 * it and keeps building the older one for those. 1: panels from bottom to top, the ribs weeping from
	 * {@value #WEEPS_FROM} over the ground; 2 (R11-2, R10-17): the panels stop in the horizon's band ({@link #panelTop}: the
	 * land under the bay plus 64 to 80, ragged in whole bays, never over Y {@value #PANELS_UNDER} but 16 over high land), so
	 * from a hill the wall is black and only its ruled grid rises into the sky; every rib plain obsidian to the top. 3
	 * (round 3, R3-2, D-110): the lit foot, at every opening the lighting fault ({@link Fault#light}) 8 along by
	 * {@value #LIT_REACH} out on each side of the wall at the ground (waterlogged on water): at night a dotted line of lit
	 * squares runs straight to the horizon, the black wall between each pair; 4 (FIX-R3-B, R3A-4), each hole the light: a
	 * ring of the land's stone round it in the wall, light blocks in the opening's air and on its sill cells either side, and the lit foot's land pale (gravel or
	 * the land's stone where it was grass or dirt: block light reads coarse dirt brown and dim).
	 */
	public static final int VERSION = 4;
	/** Version 2: no panel stands higher than this (unless the land under it is within 16 of it). */
	public static final int PANELS_UNDER = 192;
	private static final BlockState FILL = Blocks.OBSIDIAN.defaultBlockState();
	/** The ribs weep, but not within reach: from this high over the ground under the plane up. */
	public static final int WEEPS_FROM = 20;
	private static final BlockState RIB = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	/** A column with no water over its ground. */
	private static final int DRY = Integer.MIN_VALUE;

	private final LinePlan.Segment segment;
	/** The version it is built as. */
	private final int version;
	private final BoundingBox plane;
	private final BoundingBox box;
	private final BlockPos anchor;
	private final BlockPos entrance;
	/** Each opening's lowest cell and its water's top ({@link #DRY}), by its first cell along the axis: found once. */
	private final Map<Integer, int[]> openings = new ConcurrentHashMap<>();
	/** The ground under the plane by the cell along the axis: found once. */
	private final Map<Integer, Integer> grounds = new ConcurrentHashMap<>();

	public LineShape(LinePlan.Segment segment, Terrain terrain) {
		this(segment, terrain, VERSION);
	}

	/** A segment built as {@code version} (a segment fixed in a world keeps its own). */
	public LineShape(LinePlan.Segment segment, Terrain terrain, int version) {
		this.segment = segment;
		this.version = Math.max(1, Math.min(VERSION, version));
		this.plane = segment.box();
		int reach = this.version >= 3 ? LIT_REACH : REACH;
		BoundingBox wide = plane.inflatedBy(segment.arm().alongX() ? 0 : reach, 0, segment.arm().alongX() ? reach : 0);
		this.box = new BoundingBox(wide.minX(), lowest(segment), wide.minZ(), wide.maxX(), wide.maxY(), wide.maxZ());
		int gap = segment.gapMiddle();
		int gx = segment.x(gap);
		int gz = segment.z(gap);
		this.anchor = new BlockPos(gx, Math.clamp(terrain.ground(gx, gz) + 1, lowest(segment), segment.top()), gz);
		int hole = segment.firstHole();
		int[] at = opening(terrain, hole);
		this.entrance = new BlockPos(segment.x(hole), at[0], segment.z(hole));
	}

	/**
	 * The shape of a recorded start (its kind's factory): the segment fixed in this world with this start if there is one
	 * (R12-2); else the start's origin is the cell nearest the origin at the bottom, its facing the way the segment runs
	 * out, its variant the length (low 16 bits) and the height (high bits), and the openings and the cut come from its seed
	 * and the config's numbers now (the gallery's and tests' own segments).
	 */
	public static LineShape fromStart(Start start, Terrain terrain) {
		for (CompoundTag tag : Plans.fixedUnder(S19.ID + "/")) {
			LinePlan.Segment fixed = LinePlan.fromTag(tag);
			if (fixed != null && sameStart(fixed.start(), start)) {
				return new LineShape(fixed, terrain, tag.getIntOr("v", 1));
			}
		}
		LinePlan.Arm arm = arm(start.facing());
		int length = Math.max(1, start.variant() & 0xFFFF);
		int height = Math.max(1, start.variant() >>> 16);
		BlockPos o = start.origin();
		int plane = arm.alongX() ? o.getZ() : o.getX();
		int near = arm.alongX() ? o.getX() : o.getZ();
		int t0 = arm.sign() > 0 ? near : near - length + 1;
		Sc19Config config = Sc19Config.get();
		return new LineShape(LinePlan.cut(arm, plane, t0, length, start.seed(), config.holeEvery, config.gapWidth, o.getY(), o.getY() + height - 1),
				terrain, start.versionOr(VERSION));
	}

	/**
	 * The segment of a recorded start, as {@link #fromStart} finds it (the one fixed in this world with this start, else
	 * laid out from the start and the config's numbers now): 19b's holes without any terrain.
	 */
	public static LinePlan.Segment segmentOf(Start start) {
		for (CompoundTag tag : Plans.fixedUnder(S19.ID + "/")) {
			LinePlan.Segment fixed = LinePlan.fromTag(tag);
			if (fixed != null && sameStart(fixed.start(), start)) {
				return fixed;
			}
		}
		LinePlan.Arm arm = arm(start.facing());
		int length = Math.max(1, start.variant() & 0xFFFF);
		int height = Math.max(1, start.variant() >>> 16);
		BlockPos o = start.origin();
		int plane = arm.alongX() ? o.getZ() : o.getX();
		int near = arm.alongX() ? o.getX() : o.getZ();
		int t0 = arm.sign() > 0 ? near : near - length + 1;
		Sc19Config config = Sc19Config.get();
		return LinePlan.cut(arm, plane, t0, length, start.seed(), config.holeEvery, config.gapWidth, o.getY(), o.getY() + height - 1);
	}

	private static boolean sameStart(Start a, Start b) {
		return a.origin().equals(b.origin()) && a.facing() == b.facing() && a.variant() == b.variant() && a.seed() == b.seed();
	}

	/**
	 * The lowest cell it may ever write: one above the level's lowest block. It writes under its {@code bottom} only where
	 * the land itself lies lower (a flat world's ground at its fourth layer), and there only air and water down to the
	 * ground, so it stands on the land and never floats, and never touches the bedrock.
	 */
	static int lowest(LinePlan.Segment segment) {
		return segment.bottom() - (LinePlan.ABOVE_MIN - 1);
	}

	/** The half-axis whose way out is {@code out}. */
	public static LinePlan.Arm arm(Direction out) {
		return switch (out) {
			case NORTH -> LinePlan.Arm.NORTH;
			case EAST -> LinePlan.Arm.EAST;
			case WEST -> LinePlan.Arm.WEST;
			default -> LinePlan.Arm.SOUTH;
		};
	}

	public LinePlan.Segment segment() {
		return segment;
	}

	/** The version it is built as. */
	public int version() {
		return version;
	}

	/**
	 * Version 2: the highest panel cell of the bay holding {@code t} (R11-2): the land under the bay's middle plus 64 to 80
	 * (the segment's own), and 0 to 6 more by the bay, one bay in five a bay's width lower; never over Y
	 * {@value #PANELS_UNDER} unless the land is within 16 of it (then 16 over the land); never over the segment's top.
	 */
	public int panelTop(Terrain terrain, int t) {
		LinePlan.Segment s = segment;
		int bay = Math.floorDiv(t, LinePlan.BAY);
		int ground = groundAt(terrain, bay * LinePlan.BAY + LinePlan.BAY / 2);
		long seed = s.seed();
		int top = ground + 64 + Hash.below(Hash.of(seed, 0x19B), 17) + Hash.below(Hash.of(seed, 0x19C, bay), 7);
		if (Hash.below(Hash.of(seed, 0x19D, bay), 5) == 0) {
			top -= LinePlan.BAY;
		}
		return Math.min(s.top(), Math.min(top, Math.max(PANELS_UNDER, ground + 16)));
	}

	@Override
	public BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	@Override
	public BlockPos anchor() {
		return anchor;
	}

	@Override
	public BlockPos entrance() {
		return entrance;
	}

	@Override
	public Direction entranceFacing() {
		return segment.arm().out.getOpposite();
	}

	@Override
	public Interior interior() {
		return Interior.box(plane);
	}

	/** The lowest cell of the opening whose first cell along the axis is {@code hole}, and its water's top. */
	public int[] opening(Terrain terrain, int hole) {
		return openings.computeIfAbsent(hole, t -> {
			LinePlan.Segment s = segment;
			int ground = Integer.MIN_VALUE;
			for (int along = t; along <= t + 1; along++) {
				for (int side = -1; side <= 1; side++) {
					ground = Math.max(ground, terrain.ground(s.x(along, s.plane() + side), s.z(along, s.plane() + side)));
				}
			}
			int x = s.x(t);
			int z = s.z(t);
			int surface = terrain.surface(x, z);
			boolean wet = surface > terrain.ground(x, z);
			int low = wet ? surface : ground + 1;
			low = Math.clamp(low, lowest(s), s.top() - 2);
			return new int[] {low, wet ? surface : DRY};
		});
	}

	/** The ground under the plane at {@code t} (the terrain's number, found once). */
	private int groundAt(Terrain terrain, int t) {
		return grounds.computeIfAbsent(t, k -> terrain.ground(segment.x(k), segment.z(k)));
	}

	/** The opening covering this cell along the axis (its first cell), or {@code Integer.MIN_VALUE}. */
	private int holeAt(int t) {
		List<Integer> holes = segment.holes();
		for (int hole : holes) {
			if (t == hole || t == hole + 1) {
				return hole;
			}
			if (hole > t) {
				break;
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	public void fill(Writer w, BoundingBox clip) {
		wall(w, clip);
		if (version >= 4) {
			paleFoot(w, clip);
		}
		if (version >= 3) {
			litFoot(w, clip);
		}
		if (version >= 4) {
			litHoles(w, clip);
		}
	}

	/**
	 * Version 4 (FIX-R3-B, R3A-4): the lit foot's land pale: in each lit square, a column's top of grass, dirt or the dead
	 * ground's coarse dirt becomes gravel (on something solid) or stone, and what grew on it goes. From over the land
	 * there down (the noise's), as the dead ground is found.
	 */
	private void paleFoot(Writer w, BoundingBox clip) {
		for (int hole : segment.holes()) {
			for (BoundingBox square : litSquares(hole)) {
				if (!square.intersects(clip)) {
					continue;
				}
				for (int x = Math.max(square.minX(), clip.minX()); x <= Math.min(square.maxX(), clip.maxX()); x++) {
					for (int z = Math.max(square.minZ(), clip.minZ()); z <= Math.min(square.maxZ(), clip.maxZ()); z++) {
						pale(w, x, z, w.ground(x, z));
					}
				}
			}
		}
	}

	private static void pale(Writer w, int x, int z, int near) {
		for (int y = near + 4; y >= near - 4; y--) {
			BlockState state = w.get(x, y, z);
			if (state.isAir() || Vegetation.isPlant(state) || Vegetation.isSnowLayer(state)) {
				continue;
			}
			if (Vegetation.isGrassGround(state) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
				w.set(x, y, z, w.get(x, y - 1, z).isSolidRender() ? GRAVEL : STONE);
				for (int up = y + 1; up <= y + 2; up++) {
					BlockState above = w.get(x, up, z);
					if (Vegetation.isPlant(above) || Vegetation.isSnowLayer(above)) {
						w.set(x, up, z, AIR);
					}
				}
			}
			return;
		}
	}

	/**
	 * Version 4 (FIX-R3-B, R3A-4): each hole the light: a ring of the land's own stone round the opening in the wall's
	 * plane (the 4 x 4 round its 2 x 2, where the wall stands: lit obsidian stays black, lit stone reads pale), a light
	 * block in every cell of the opening's air (waterlogged in its water) and on the sill cells either side of the wall,
	 * so at night every hole is a pale square frame lit in the black wall, a fault in it. Returns the lights set. Light
	 * blocks are no obstacle: the way through stays open.
	 */
	public List<BlockPos> litHoles(Writer w, BoundingBox clip) {
		List<BlockPos> out = new ArrayList<>();
		LinePlan.Segment s = segment;
		for (int hole : s.holes()) {
			int[] at = opening(w.terrain(), hole);
			for (BlockPos cell : ring(hole, at[0])) {
				if (clip.isInside(cell) && w.inside(cell) && w.get(cell).is(Blocks.OBSIDIAN)) {
					w.set(cell, ringStone(cell.getY()));
				}
			}
			for (int t = hole; t <= hole + 1; t++) {
				for (int a = s.plane() - 1; a <= s.plane() + 1; a++) {
					for (int y = at[0]; y <= (a == s.plane() ? at[0] + 1 : at[0]); y++) {
						int x = s.x(t, a);
						int z = s.z(t, a);
						if (!clip.isInside(x, y, z) || !w.inside(x, y, z)) {
							continue;
						}
						BlockState here = w.get(x, y, z);
						boolean water = here.is(Blocks.WATER);
						if ((here.isAir() || water) && w.set(x, y, z, Fault.lightOf(Fault.LEVEL, water))) {
							out.add(new BlockPos(x, y, z));
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * Version 3's lit foot in this clip: at every opening, on each side of the wall, the lighting fault over the 8 x
	 * {@value #LIT_REACH} beside it ({@link #litSquares}), looking down from over the land there. Returns the lights set.
	 */
	public List<BlockPos> litFoot(Writer w, BoundingBox clip) {
		List<BlockPos> out = new ArrayList<>();
		for (int hole : segment.holes()) {
			for (BoundingBox square : litSquares(hole)) {
				if (!square.intersects(clip)) {
					continue;
				}
				int fromY = Integer.MIN_VALUE;
				for (int x = Math.max(square.minX(), clip.minX()); x <= Math.min(square.maxX(), clip.maxX()); x++) {
					for (int z = Math.max(square.minZ(), clip.minZ()); z <= Math.min(square.maxZ(), clip.maxZ()); z++) {
						fromY = Math.max(fromY, Math.max(w.ground(x, z), w.surface(x, z)));
					}
				}
				out.addAll(Fault.light(w, square, Math.min(fromY + 16, segment.top())));
			}
			// Up the wall's foot on both faces beside the opening, so the black face itself stands lit (a lit square on
			// level land is edge-on from a walker's eye: R3-0's note).
			int low = opening(w.terrain(), hole)[0];
			int wet = opening(w.terrain(), hole)[1];
			for (int side : new int[] {-1, 1}) {
				for (int t : new int[] {hole - 2, hole + 3}) {
					for (int up = 1; up <= WALL_LIGHT_UP; up += Fault.GRID) {
						int x = segment.x(t, segment.plane() + side);
						int z = segment.z(t, segment.plane() + side);
						int y = low + up;
						BlockState here = w.get(x, y, z);
						boolean water = here.is(Blocks.WATER) && y <= wet;
						if (w.inside(x, y, z) && (here.isAir() || water) && w.set(x, y, z, Fault.lightOf(Fault.LEVEL, water))) {
							out.add(new BlockPos(x, y, z));
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * Version 4: the ring round the opening whose first cell along the axis is {@code hole} and whose lowest cell is
	 * {@code low}, in the wall's plane: every cell of the 4 x 4 from one before it to one after it, one under it to one
	 * over it, but the opening's own 2 x 2.
	 */
	public List<BlockPos> ring(int hole, int low) {
		List<BlockPos> out = new ArrayList<>(12);
		for (int t = hole - 1; t <= hole + 2; t++) {
			for (int y = low - 1; y <= low + 2; y++) {
				if (t >= hole && t <= hole + 1 && y >= low && y <= low + 1) {
					continue;
				}
				out.add(new BlockPos(segment.x(t), y, segment.z(t)));
			}
		}
		return out;
	}

	/** The ring's block: the land's own stone (pale under block light; deepslate would read dark). */
	public static BlockState ringStone(int y) {
		return STONE;
	}

	/** Version 3: the two squares of the lit foot at the opening whose first cell along the axis is {@code hole} (one each side). */
	public List<BoundingBox> litSquares(int hole) {
		LinePlan.Segment s = segment;
		List<BoundingBox> out = new ArrayList<>(2);
		for (int side : new int[] {-1, 1}) {
			int a0 = s.plane() + side;
			int a1 = s.plane() + side * LIT_REACH;
			BlockPos p = new BlockPos(s.x(hole - LIT_ALONG, a0), box.minY(), s.z(hole - LIT_ALONG, a0));
			BlockPos q = new BlockPos(s.x(hole + 1 + LIT_ALONG, a1), box.maxY(), s.z(hole + 1 + LIT_ALONG, a1));
			out.add(BoundingBox.fromCorners(p, q));
		}
		return out;
	}

	/** The wall itself in this clip (every version): ribs, panels, openings, the dead ground beside it. */
	private void wall(Writer w, BoundingBox clip) {
		LinePlan.Segment s = segment;
		boolean alongX = s.arm().alongX();
		int acrossMin = Math.max(s.plane() - REACH, alongX ? clip.minZ() : clip.minX());
		int acrossMax = Math.min(s.plane() + REACH, alongX ? clip.maxZ() : clip.maxX());
		if (acrossMin > acrossMax) {
			return;
		}
		int tMin = Math.max(s.t0(), alongX ? clip.minX() : clip.minZ());
		int tMax = Math.min(s.t1(), alongX ? clip.maxX() : clip.maxZ());
		int yMax = Math.min(s.top(), clip.maxY());
		for (int t = tMin; t <= tMax; t++) {
			boolean gap = s.inGap(t);
			int reach = gap ? 0 : s.ribReach(t);
			int ground = groundAt(w.terrain(), t);
			// The dead ground first: it reads the land beside the plane before anything of the plane is set.
			if (s.panel(t)) {
				for (int a = acrossMin; a <= acrossMax; a++) {
					int off = Math.abs(a - s.plane());
					if (off > reach && off >= 1) {
						deaden(w, s.x(t, a), s.z(t, a), ground);
					}
				}
			}
			if (gap) {
				continue;
			}
			// It stands on the land: under its bottom only where the land lies lower (and there only through air and water).
			int under = Math.max(clip.minY(), ground + 1 < s.bottom() ? Math.max(lowest(s), ground + 1) : s.bottom());
			if (reach > 0) {
				for (int a = Math.max(acrossMin, s.plane() - reach); a <= Math.min(acrossMax, s.plane() + reach); a++) {
					if (version >= 2) {
						// Plain obsidian to the top: a drawn line, never a ruin's weeping block (R10-17).
						column(w, s.x(t, a), s.z(t, a), under, yMax, FILL, Integer.MAX_VALUE, Integer.MIN_VALUE, DRY);
						continue;
					}
					column(w, s.x(t, a), s.z(t, a), under, Math.min(yMax, ground + WEEPS_FROM - 1), FILL, Integer.MAX_VALUE, Integer.MIN_VALUE, DRY);
					column(w, s.x(t, a), s.z(t, a), Math.max(under, ground + WEEPS_FROM), yMax, RIB, Integer.MAX_VALUE, Integer.MIN_VALUE, DRY);
				}
				continue;
			}
			if (!s.panel(t) || s.plane() < acrossMin || s.plane() > acrossMax) {
				continue;
			}
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			int water = DRY;
			int hole = holeAt(t);
			if (hole != Integer.MIN_VALUE) {
				int[] at = opening(w.terrain(), hole);
				lo = at[0];
				hi = at[0] + 1;
				water = at[1];
			}
			column(w, s.x(t), s.z(t), under, version >= 2 ? Math.min(yMax, panelTop(w.terrain(), t)) : yMax, FILL, lo, hi, water);
		}
	}

	/** One column of the plane from {@code from} to {@code to}: {@code fill}, but the opening's cells; under the bottom only over air and water. */
	private void column(Writer w, int x, int z, int from, int to, BlockState fill, int lo, int hi, int water) {
		for (int y = from; y <= to; y++) {
			BlockState state = y >= lo && y <= hi ? (y <= water ? WATER : AIR) : fill;
			if (y >= segment.bottom() || state != fill) {
				w.set(x, y, z, state);
			} else {
				w.setIf(x, y, z, state, here -> here.isAir() || !here.getFluidState().isEmpty());
			}
		}
	}

	/**
	 * Dead ground in one column beside the plane: from a little over the plane's ground down, through air and plants, the
	 * first grass becomes coarse dirt (grass never takes it back) and the plants on it go; a tree, stone, sand or water there
	 * is left.
	 */
	private void deaden(Writer w, int x, int z, int near) {
		for (int y = near + 4; y >= near - 4; y--) {
			BlockState state = w.get(x, y, z);
			if (state.isAir() || Vegetation.isPlant(state) || Vegetation.isSnowLayer(state)) {
				continue;
			}
			if (Vegetation.isGrassGround(state) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT)) {
				w.set(x, y, z, COARSE);
				for (int up = y + 1; up <= y + 2; up++) {
					BlockState above = w.get(x, up, z);
					if (Vegetation.isPlant(above) || Vegetation.isSnowLayer(above)) {
						w.set(x, up, z, AIR);
					}
				}
			}
			return;
		}
	}
}
