package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Spot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What each crumb is (docs/SELF_CREATED_2_R3.md, "32", "What they are"): small things of the first versions' blocks, by
 * the place they lead to and their tier, each made of that place's own blocks. Far: one old block where it should not be
 * (a brick, a bookshelf, a bare trunk, an obsidian block, a dirt pillar with its torch), a dim light on every other one
 * at night (a redstone torch; 19's none, 20's a torch). Mid: a small built thing, every third crowned with glowstone.
 * Near: his marks, lit, with dead ground round them. Over water deeper than one block a mid or near crumb (and every one
 * of 16d's) stands on a cobblestone pillar from the floor. Built as a list of cells from a footing, so the feature, the
 * gallery, the tests and the runtime rules make the same thing; nothing here reads the world.
 * <p>
 * A crumb's own cells ({@link Cell#own}: the thing) are what 32b may take and 32c may move; its ground cells (dead
 * ground, plants and snow and leaves cleared) are the land's and stay. Never a wall torch, never the shrine (no mossy
 * cobblestone anywhere): those are other features' grammar.
 * <p>
 * Versions (R12-2): these are the things of {@link CrumbLines#VERSION} 1; a line keeps the version it was fixed with, so
 * a later version adds its things here by {@code spot.line().version()} and goes on building version 1 for the lines
 * fixed with it.
 */
public final class CrumbShapes {
	private CrumbShapes() {
	}

	/** The places that have things of their own (another kind's places get no lines). */
	public static final java.util.Set<String> KINDS = java.util.Set.of("s16", "s16d", "s17", "s18", "s19", "s20");

	static final BlockState BRICK = Blocks.BRICKS.defaultBlockState();
	static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	static final BlockState SHELF = Blocks.BOOKSHELF.defaultBlockState();
	static final BlockState GLOW = Blocks.GLOWSTONE.defaultBlockState();
	static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
	static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
	static final BlockState TORCH = Blocks.TORCH.defaultBlockState();
	static final BlockState RED_TORCH = Blocks.REDSTONE_TORCH.defaultBlockState();
	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/**
	 * Where a crumb stands.
	 *
	 * @param x       its middle column
	 * @param y       the ground's top block there (it stands on it)
	 * @param z       its middle column
	 * @param surface the water's top block over the ground (the pillar form), or {@code y} on land
	 * @param stone   the land's own stone (18)
	 * @param log     the biome's wood (18)
	 * @param grounds each footprint column's ground, by {@link #footprint} order (a fill goes under those lower than {@code y})
	 */
	public record Foot(int x, int y, int z, int surface, BlockState stone, BlockState log, int[] grounds) {
		public boolean wet() {
			return surface > y;
		}
	}

	/** One cell a crumb sets: its own (the thing) or the ground's. */
	public record Cell(BlockPos pos, BlockState state, boolean own) {
	}

	/** True if the crumb stands on a pillar in water (mid and near over water, and 16d's everywhere in water). */
	public static boolean pillar(String kind, Tier tier, boolean wet) {
		return wet && (tier != Tier.FAR || kind.equals("s16d"));
	}

	/** True if this tier of this kind may stand in water deeper than one block. */
	public static boolean allowsWater(String kind, Tier tier) {
		return tier != Tier.FAR || kind.equals("s16d");
	}

	/** The radius of its thing round its middle (what must fit in its chunk). */
	public static int core(String kind, Tier tier, boolean wet) {
		if (pillar(kind, tier, wet)) {
			return tier == Tier.FAR ? 0 : kind.equals("s16d") && tier == Tier.NEAR ? 2 : 1;
		}
		return switch (tier) {
			case FAR -> kind.equals("s18") ? 1 : 0;
			case MID -> kind.equals("s16") || kind.equals("s16d") || kind.equals("s20") ? 1 : 0;
			case NEAR -> switch (kind) {
				case "s16", "s16d", "s17" -> 2;
				case "s19" -> 0;
				default -> 1;
			};
		};
	}

	/** The radius of its dead ground (clipped at its chunk's edge: the ground is the land's, never needed whole). */
	public static int ring(String kind, Tier tier, boolean wet) {
		if (tier != Tier.NEAR || pillar(kind, tier, wet)) {
			return 0;
		}
		return switch (kind) {
			case "s16", "s16d", "s18" -> 3;
			case "s17" -> 2;
			default -> 2;
		};
	}

	/** The footprint columns whose ground is read for a fill under it (dx, dz pairs), middle first. */
	public static int[][] footprint(String kind, Tier tier, boolean wet) {
		int r = pillar(kind, tier, wet) ? 0 : core(kind, tier, wet);
		if (kind.equals("s18") && tier == Tier.FAR) {
			r = 0;
		}
		List<int[]> out = new ArrayList<>();
		if (kind.equals("s17") && tier == Tier.NEAR && !wet) {
			// The 4 x 4 of cobblestone, flush with the ground: filled under where the land is lower, cut where it is higher.
			out.add(new int[] {0, 0});
			for (int dx = -1; dx <= 2; dx++) {
				for (int dz = -1; dz <= 2; dz++) {
					if (dx != 0 || dz != 0) {
						out.add(new int[] {dx, dz});
					}
				}
			}
			return out.toArray(new int[0][]);
		}
		out.add(new int[] {0, 0});
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx != 0 || dz != 0) {
					out.add(new int[] {dx, dz});
				}
			}
		}
		return out.toArray(new int[0][]);
	}

	/** The crumb's cells for this spot on this footing (its own cells first). */
	public static List<Cell> cells(Spot spot, Foot f) {
		List<Cell> out = new ArrayList<>();
		String kind = spot.line().kind();
		Tier tier = spot.tier();
		Direction toPlace = spot.line().toPlace();
		if (pillar(kind, tier, f.wet())) {
			water(out, kind, spot, f);
			return last(out);
		}
		int[][] foot = footprint(kind, tier, false);
		// What lower footprint columns stand on (the thing's own material, so it reads as one piece).
		BlockState fill = switch (kind) {
			case "s16", "s16d" -> BRICK;
			case "s18" -> f.stone();
			case "s20" -> DIRT;
			default -> COBBLE;
		};
		boolean flush = kind.equals("s17") && tier == Tier.NEAR;
		for (int i = 1; i < foot.length && i < f.grounds().length; i++) {
			for (int y = f.grounds()[i] + 1; y <= f.y() - (flush ? 1 : 0); y++) {
				own(out, f.x() + foot[i][0], y, f.z() + foot[i][1], fill);
			}
			if (flush) {
				// The land over the flush square taken off (a cut).
				for (int y = f.y() + 1; y <= f.grounds()[i]; y++) {
					own(out, f.x() + foot[i][0], y, f.z() + foot[i][1], AIR);
				}
			}
		}
		int x = f.x();
		int y = f.y() + 1;
		int z = f.z();
		switch (kind) {
			case "s16", "s16d" -> brick(out, tier, spot, x, y, z);
			case "s17" -> shelf(out, tier, spot, f, x, y, z);
			case "s18" -> mountain(out, tier, spot, f, toPlace, x, y, z);
			case "s19" -> obsidian(out, tier, spot, x, y, z);
			case "s20" -> floor(out, tier, spot, toPlace, x, y, z);
			default -> {
			}
		}
		return last(out);
	}

	/** One cell a position: a later cell over an earlier one at the same place wins (a crown over its block). */
	static List<Cell> last(List<Cell> cells) {
		java.util.Map<BlockPos, Cell> by = new java.util.LinkedHashMap<>();
		for (Cell c : cells) {
			by.remove(c.pos());
			by.put(c.pos(), c);
		}
		return new ArrayList<>(by.values());
	}

	/**
	 * A far thing of one block (16's brick, 17's bookshelf, 19's obsidian) stands this many high: 1, and 2 from line version 2
	 * (FIX-R3-C: on real land a lone block hid from the crumb before it; the same block, one more).
	 */
	static int farTall(Spot spot) {
		return spot.line().version() >= CrumbFooting.RISES_FROM ? 2 : 1;
	}

	private static void brick(List<Cell> out, Tier tier, Spot spot, int x, int y, int z) {
		switch (tier) {
			case FAR -> {
				int tall = farTall(spot);
				for (int dy = 0; dy < tall; dy++) {
					own(out, x, y + dy, z, BRICK);
				}
				if (spot.lit()) {
					own(out, x, y + tall, z, RED_TORCH);
				}
			}
			case MID -> {
				square(out, x, y, z, 1, BRICK);
				own(out, x, y + 1, z, spot.crowned() ? GLOW : BRICK);
			}
			case NEAR -> {
				square(out, x, y, z, 2, BRICK);
				square(out, x, y + 1, z, 1, BRICK);
				own(out, x, y + 2, z, GLOW);
			}
		}
	}

	private static void shelf(List<Cell> out, Tier tier, Spot spot, Foot f, int x, int y, int z) {
		switch (tier) {
			case FAR -> {
				int tall = farTall(spot);
				for (int dy = 0; dy < tall; dy++) {
					own(out, x, y + dy, z, SHELF);
				}
				if (spot.lit()) {
					own(out, x, y + tall, z, RED_TORCH);
				}
			}
			case MID -> {
				for (int dy = 0; dy < 3; dy++) {
					own(out, x, y + dy, z, COBBLE);
				}
				own(out, x, y + 3, z, SHELF);
				if (spot.crowned()) {
					own(out, x, y + 4, z, GLOW);
				}
			}
			case NEAR -> {
				// His 2 x 2, lit: two deep in a 4 x 4 of cobblestone flush with the ground, its floor glowstone.
				int g = f.y();
				for (int dx = -1; dx <= 2; dx++) {
					for (int dz = -1; dz <= 2; dz++) {
						boolean hole = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
						if (hole) {
							own(out, x + dx, g, z + dz, AIR);
							own(out, x + dx, g - 1, z + dz, AIR);
							own(out, x + dx, g - 2, z + dz, GLOW);
						} else {
							own(out, x + dx, g, z + dz, COBBLE);
						}
					}
				}
			}
		}
	}

	private static void mountain(List<Cell> out, Tier tier, Spot spot, Foot f, Direction toPlace, int x, int y, int z) {
		switch (tier) {
			case FAR -> {
				int logs = 3 + (int) Math.floorMod(CrumbLines.mix(spot.line().seed(), spot.index()), 3);
				for (int dy = 0; dy < logs; dy++) {
					own(out, x, y + dy, z, f.log());
				}
				if (spot.lit()) {
					own(out, x + toPlace.getStepX(), y, z + toPlace.getStepZ(), RED_TORCH);
				}
			}
			case MID -> {
				for (int dy = 0; dy < 5; dy++) {
					own(out, x, y + dy, z, f.stone());
				}
				if (spot.crowned()) {
					own(out, x, y + 5, z, GLOW);
				}
			}
			case NEAR -> {
				for (int dy = 0; dy < 3; dy++) {
					square(out, x, y + dy, z, 1, f.stone());
				}
				own(out, x, y + 2, z, GLOW);
			}
		}
	}

	private static void obsidian(List<Cell> out, Tier tier, Spot spot, int x, int y, int z) {
		int h = switch (tier) {
			case FAR -> farTall(spot);
			case MID -> 2;
			case NEAR -> 3;
		};
		for (int dy = 0; dy < h; dy++) {
			own(out, x, y + dy, z, OBSIDIAN);
		}
		if (tier == Tier.NEAR || spot.crowned()) {
			own(out, x, y + h, z, GLOW);
		}
	}

	private static void floor(List<Cell> out, Tier tier, Spot spot, Direction toPlace, int x, int y, int z) {
		switch (tier) {
			case FAR -> {
				// The first night's pillar.
				for (int dy = 0; dy < 3; dy++) {
					own(out, x, y + dy, z, DIRT);
				}
				own(out, x, y + 3, z, TORCH);
			}
			case MID -> {
				// A test floor in miniature: a 3 x 3 of grass raised one and dead flat, a cobblestone block and its torch.
				square(out, x, y, z, 1, GRASS);
				own(out, x, y, z, COBBLE);
				own(out, x, y + 1, z, TORCH);
				clearAbove(out, x, y + 1, z, 1, 2);
			}
			case NEAR -> {
				// The first house in miniature: oak planks, one glass block, an open doorway toward the place, a torch inside.
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						boolean wall = dx != 0 || dz != 0;
						boolean door = dx == toPlace.getStepX() && dz == toPlace.getStepZ();
						boolean window = dx == -toPlace.getStepX() && dz == -toPlace.getStepZ();
						if (wall) {
							own(out, x + dx, y, z + dz, door ? AIR : PLANKS);
							own(out, x + dx, y + 1, z + dz, door ? AIR : window ? GLASS : PLANKS);
						}
						own(out, x + dx, y + 2, z + dz, PLANKS);
					}
				}
				own(out, x, y, z, TORCH);
				own(out, x, y + 1, z, AIR);
			}
		}
	}

	/** Each kind's thing over water: a cobblestone pillar from the floor, and the kind's own block over it. */
	private static void water(List<Cell> out, String kind, Spot spot, Foot f) {
		int x = f.x();
		int z = f.z();
		int s = f.surface();
		Tier tier = spot.tier();
		boolean sea = kind.equals("s16") || kind.equals("s16d");
		int top = sea && tier == Tier.MID ? s - 1 : s;
		for (int y = f.y() + 1; y <= top; y++) {
			own(out, x, y, z, COBBLE);
		}
		BlockState crown = switch (kind) {
			case "s16", "s16d" -> BRICK;
			case "s17" -> SHELF;
			case "s18" -> f.stone();
			case "s19" -> OBSIDIAN;
			default -> PLANKS;
		};
		switch (tier) {
			case FAR -> {
				own(out, x, s + 1, z, crown);
				if (spot.lit()) {
					own(out, x, s + 2, z, RED_TORCH);
				}
			}
			case MID -> {
				if (sea) {
					// A brick step at the surface, two high.
					square(out, x, s, z, 1, BRICK);
					own(out, x, s + 1, z, spot.crowned() ? GLOW : BRICK);
				} else {
					own(out, x, s + 1, z, crown);
					if (spot.crowned()) {
						own(out, x, s + 2, z, GLOW);
					}
				}
			}
			case NEAR -> {
				if (sea) {
					own(out, x, s + 1, z, GLOW);
					// A 5 x 5 of sea lit as at noon round it.
					BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15).setValue(BlockStateProperties.WATERLOGGED, true);
					for (int dx = -2; dx <= 2; dx += 2) {
						for (int dz = -2; dz <= 2; dz += 2) {
							if (dx != 0 || dz != 0) {
								out.add(new Cell(new BlockPos(x + dx, s, z + dz), light, false));
							}
						}
					}
				} else {
					own(out, x, s + 1, z, crown);
					own(out, x, s + 2, z, GLOW);
				}
			}
		}
	}

	private static void square(List<Cell> out, int x, int y, int z, int r, BlockState state) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				own(out, x + dx, y, z + dz, state);
			}
		}
	}

	/** Air over a square (a cut: the land over a flat thing taken off), as ground cells. */
	private static void clearAbove(List<Cell> out, int x, int y, int z, int r, int high) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = 0; dy < high; dy++) {
					if (dx != 0 || dz != 0 || dy > 0) {
						out.add(new Cell(new BlockPos(x + dx, y + dy, z + dz), AIR, false));
					}
				}
			}
		}
	}

	private static void own(List<Cell> out, int x, int y, int z, BlockState state) {
		out.add(new Cell(new BlockPos(x, y, z), state, true));
	}

	/** The biome's wood for 18's bare trunk, by the biome's id path (oak where none fits). */
	public static BlockState logFor(String biomePath) {
		if (biomePath.contains("birch")) {
			return Blocks.BIRCH_LOG.defaultBlockState();
		}
		if (biomePath.contains("taiga") || biomePath.contains("snowy") || biomePath.contains("grove") || biomePath.contains("windswept")) {
			return Blocks.SPRUCE_LOG.defaultBlockState();
		}
		if (biomePath.contains("jungle")) {
			return Blocks.JUNGLE_LOG.defaultBlockState();
		}
		if (biomePath.contains("savanna")) {
			return Blocks.ACACIA_LOG.defaultBlockState();
		}
		if (biomePath.contains("dark_forest")) {
			return Blocks.DARK_OAK_LOG.defaultBlockState();
		}
		if (biomePath.contains("cherry")) {
			return Blocks.CHERRY_LOG.defaultBlockState();
		}
		if (biomePath.contains("mangrove")) {
			return Blocks.MANGROVE_LOG.defaultBlockState();
		}
		return Blocks.OAK_LOG.defaultBlockState();
	}

	/** True for a block that may be the land's own stone (18's mid and near), as the ground under it holds it. */
	public static boolean landStone(BlockState state) {
		return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
				|| state.is(Blocks.CALCITE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.TERRACOTTA)
				|| state.is(Blocks.DEEPSLATE);
	}

	/** The dead ground's block for a top block (null: leave it): grass and its kin go to dirt; 18's is gravel and dirt. */
	public static BlockState dead(String kind, BlockState top, long hash) {
		if (kind.equals("s20")) {
			// The floor's: its grass kept, only its plants and snow taken (a test floor is grass, dead flat).
			return top.is(Blocks.SNOW_BLOCK) ? GRASS : null;
		}
		if (kind.equals("s18") && (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.PODZOL) || top.is(Blocks.SNOW_BLOCK)
				|| top.is(Blocks.MYCELIUM) || top.is(Blocks.COARSE_DIRT))) {
			return (hash & 3) == 0 ? GRAVEL : DIRT;
		}
		if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.DIRT_PATH) || top.is(Blocks.SNOW_BLOCK)) {
			return DIRT;
		}
		return null;
	}
}
