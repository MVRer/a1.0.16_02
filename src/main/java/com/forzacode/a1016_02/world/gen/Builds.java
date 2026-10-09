package com.forzacode.a1016_02.world.gen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The small things "others left": huts, the panic tower, crosses, pyramids and lone lights, as blueprints
 * anchored on a ground block. Builds never carry signs or books; all text belongs to lore.
 */
public final class Builds {
	/** A blueprint and the place lore should use. */
	public record Build(Blueprint blueprint, BlockPos site, int size, BoundingBox interior) {
	}

	/** The wood a build is made of, from the biome it stands in. */
	public record Wood(Block planks, Block slab, Block log, Block strippedLog, Block door) {
		public static final Wood OAK = new Wood(Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_DOOR);
		public static final Wood SPRUCE = new Wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_DOOR);
		public static final Wood BIRCH = new Wood(Blocks.BIRCH_PLANKS, Blocks.BIRCH_SLAB, Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_DOOR);
		public static final Wood ACACIA = new Wood(Blocks.ACACIA_PLANKS, Blocks.ACACIA_SLAB, Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_DOOR);
		public static final Wood DARK_OAK = new Wood(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
				Blocks.DARK_OAK_DOOR);
		public static final Wood JUNGLE = new Wood(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.JUNGLE_DOOR);
		public static final Wood POPLAR = new Wood(Blocks.POPLAR_PLANKS, Blocks.POPLAR_SLAB, Blocks.POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG, Blocks.POPLAR_DOOR);
		public static final Wood CHERRY = new Wood(Blocks.CHERRY_PLANKS, Blocks.CHERRY_SLAB, Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_DOOR);

		public static Wood of(Holder<Biome> biome) {
			if (biome.is(Biomes.DAPPLED_FOREST)) {
				return POPLAR;
			}
			if (biome.is(Biomes.CHERRY_GROVE)) {
				return CHERRY;
			}
			if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)) {
				return DARK_OAK;
			}
			if (biome.is(BiomeTags.IS_JUNGLE)) {
				return JUNGLE;
			}
			if (biome.is(BiomeTags.IS_SAVANNA)) {
				return ACACIA;
			}
			if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
				return BIRCH;
			}
			if (biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.GROVE) || biome.is(Biomes.SNOWY_SLOPES)
					|| biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_FOREST)) {
				return SPRUCE;
			}
			return OAK;
		}
	}

	/** Local frame: +right and +forward (towards the door) from an anchor. */
	private record Frame(int x, int y, int z, Direction forward) {
		BlockPos at(int right, int up, int fwd) {
			Direction r = forward.getClockWise();
			return new BlockPos(x + r.getStepX() * right + forward.getStepX() * fwd, y + up, z + r.getStepZ() * right + forward.getStepZ() * fwd);
		}
	}

	private Builds() {
	}

	private static BlockState cobble(long h) {
		return Hash.unit(h) < 0.3 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
	}

	/**
	 * The one ruined cobble hut per world: 7x7, broken walls one to three high, no roof, a door gap and an empty
	 * chest in the back corner. The site is the chest (lore fills it).
	 */
	public static Build ruinedHut(int x, int floorY, int z, Direction facing, long seed) {
		Frame f = new Frame(x, floorY, z, facing);
		Blueprint bp = new Blueprint();
		clearVolume(bp, f, 3, 1, 5);
		for (int r = -3; r <= 3; r++) {
			for (int w = -3; w <= 3; w++) {
				boolean wall = Math.abs(r) == 3 || Math.abs(w) == 3;
				long h = Hash.of(seed, r, w);
				if (wall) {
					bp.put(f.at(r, 0, w), cobble(h));
					bp.foundation(f.at(r, -1, w), Blocks.COBBLESTONE.defaultBlockState(), 6);
					boolean doorGap = r == 0 && w == 3;
					int height = doorGap ? 0 : Hash.between(h ^ 7, 1, 3);
					for (int up = 1; up <= height; up++) {
						if (Hash.unit(h ^ (up * 31L)) > 0.15) {
							bp.put(f.at(r, up, w), cobble(h ^ (up * 17L)));
						}
					}
				} else if (Hash.unit(h ^ 3) < 0.45) {
					bp.put(f.at(r, 0, w), cobble(h ^ 5));
				}
			}
		}
		// Rubble that fell inside.
		bp.put(f.at(Hash.between(seed ^ 11, -2, 2), 1, Hash.between(seed ^ 13, -2, 1)), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		BlockPos chest = f.at(-2, 1, -2);
		bp.chest(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), List.of());
		return new Build(bp, chest, 3, interior(f, 2, 3));
	}

	/**
	 * A small cobble or plank hut: 5x5, half a roof, door open, a chest with ordinary early items (a worn stone
	 * pickaxe, dirt, seeds, a few apples). The site is the chest.
	 */
	public static Build abandonedBuild(int x, int floorY, int z, Direction facing, Wood wood, long seed) {
		Frame f = new Frame(x, floorY, z, facing);
		boolean stone = Hash.unit(seed ^ 1) < 0.5;
		BlockState wall = stone ? Blocks.COBBLESTONE.defaultBlockState() : wood.planks().defaultBlockState();
		BlockState corner = stone ? Blocks.COBBLESTONE.defaultBlockState() : wood.log().defaultBlockState();
		Blueprint bp = new Blueprint();
		clearVolume(bp, f, 2, 1, 5);
		for (int r = -2; r <= 2; r++) {
			for (int w = -2; w <= 2; w++) {
				boolean edge = Math.abs(r) == 2 || Math.abs(w) == 2;
				boolean isCorner = Math.abs(r) == 2 && Math.abs(w) == 2;
				bp.put(f.at(r, 0, w), edge ? Blocks.COBBLESTONE.defaultBlockState() : wood.planks().defaultBlockState());
				bp.foundation(f.at(r, -1, w), Blocks.COBBLESTONE.defaultBlockState(), 6);
				if (!edge) {
					continue;
				}
				for (int up = 1; up <= 3; up++) {
					if (r == 0 && w == 2 && up <= 2) {
						continue; // the doorway
					}
					if (w == 0 && Math.abs(r) == 2 && up == 2) {
						continue; // a window hole on each side
					}
					bp.put(f.at(r, up, w), isCorner ? corner : wall);
				}
			}
		}
		// Half a roof: only the back half is still there.
		BlockState roof = wood.slab().defaultBlockState();
		for (int r = -2; r <= 2; r++) {
			for (int w = -2; w <= 0; w++) {
				if (w == 0 && Hash.unit(Hash.of(seed, r, 99)) < 0.5) {
					continue;
				}
				bp.put(f.at(r, 4, w), roof);
			}
		}
		Direction doorFacing = facing;
		bp.put(f.at(0, 1, 2), wood.door().defaultBlockState().setValue(DoorBlock.FACING, doorFacing).setValue(DoorBlock.OPEN, true)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		bp.put(f.at(0, 2, 2), wood.door().defaultBlockState().setValue(DoorBlock.FACING, doorFacing).setValue(DoorBlock.OPEN, true)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		bp.put(f.at(1, 1, -1), Blocks.CRAFTING_TABLE.defaultBlockState());
		BlockPos chest = f.at(-1, 1, -1);
		bp.chest(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), earlyItems(seed));
		return new Build(bp, chest, 2, interior(f, 1, 3));
	}

	/** Ordinary early items: a worn stone pickaxe, dirt, seeds and a few apples. */
	public static List<ItemStack> earlyItems(long seed) {
		List<ItemStack> items = new ArrayList<>();
		ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
		int max = pickaxe.getMaxDamage();
		pickaxe.setDamageValue(Math.max(1, (int) (max * (0.55 + 0.35 * Hash.unit(seed ^ 21)))));
		items.add(pickaxe);
		items.add(new ItemStack(Items.DIRT, Hash.between(seed ^ 22, 6, 23)));
		items.add(new ItemStack(Items.WHEAT_SEEDS, Hash.between(seed ^ 23, 2, 9)));
		items.add(new ItemStack(Items.APPLE, Hash.between(seed ^ 24, 1, 3)));
		return items;
	}

	/**
	 * A house that was cleared: walls and roof intact, door shut, every block inside gone down to the floor.
	 * The site is the middle of the floor.
	 */
	public static Build emptiedHouse(int x, int floorY, int z, Direction facing, Wood wood, long seed) {
		Frame f = new Frame(x, floorY, z, facing);
		Blueprint bp = new Blueprint();
		clearVolume(bp, f, 3, 1, 6);
		BlockState planks = wood.planks().defaultBlockState();
		BlockState log = wood.log().defaultBlockState();
		for (int r = -3; r <= 3; r++) {
			for (int w = -3; w <= 3; w++) {
				boolean edge = Math.abs(r) == 3 || Math.abs(w) == 3;
				boolean isCorner = Math.abs(r) == 3 && Math.abs(w) == 3;
				bp.put(f.at(r, 0, w), edge ? Blocks.COBBLESTONE.defaultBlockState() : planks);
				bp.foundation(f.at(r, -1, w), Blocks.COBBLESTONE.defaultBlockState(), 6);
				if (edge) {
					for (int up = 1; up <= 4; up++) {
						if (r == 0 && w == 3 && up <= 2) {
							continue;
						}
						boolean window = up == 2 && !isCorner && (Math.abs(r) == 1 && Math.abs(w) == 3 || Math.abs(w) == 1 && Math.abs(r) == 3);
						bp.put(f.at(r, up, w), isCorner ? log : window ? Blocks.GLASS.defaultBlockState() : planks);
					}
				}
				bp.put(f.at(r, 5, w), edge ? log.setValue(RotatedPillarBlock.AXIS, Math.abs(w) == 3 ? axisOf(facing.getClockWise()) : axisOf(facing))
						: planks);
			}
		}
		for (int r = -2; r <= 2; r++) {
			for (int w = -2; w <= 2; w++) {
				bp.put(f.at(r, 6, w), wood.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		bp.put(f.at(0, 1, 3), wood.door().defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		bp.put(f.at(0, 2, 3), wood.door().defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		return new Build(bp, f.at(0, 1, 0), 3, interior(f, 2, 4));
	}

	private static Direction.Axis axisOf(Direction direction) {
		return direction.getAxis();
	}

	/**
	 * The panic tower: a dirt pillar with a 1x1 shelter on top, the first-night hideout. Nothing leads down. The
	 * site is the shelter (where the player would stand).
	 */
	public static Build panicTower(int x, int groundY, int z, long seed) {
		int height = Hash.between(seed ^ 41, 6, 12);
		Blueprint bp = new Blueprint();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int up = 1; up <= height; up++) {
			bp.put(new BlockPos(x, groundY + up, z), dirt);
		}
		int feet = groundY + height + 1;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			bp.put(new BlockPos(x + side.getStepX(), feet, z + side.getStepZ()), dirt);
			bp.put(new BlockPos(x + side.getStepX(), feet + 1, z + side.getStepZ()), dirt);
		}
		bp.clear(new BlockPos(x, feet, z));
		bp.clear(new BlockPos(x, feet + 1, z));
		bp.put(new BlockPos(x, feet + 2, z), dirt);
		BlockPos shelter = new BlockPos(x, feet, z);
		return new Build(bp, shelter, height, new BoundingBox(shelter.getX(), feet, shelter.getZ(), shelter.getX(), feet + 1, shelter.getZ()));
	}

	/** A cobblestone or wood cross, one block wide, 3 or 4 tall. The site is its base. */
	public static Build cross(int x, int groundY, int z, Direction facing, Wood wood, long seed) {
		int height = Hash.between(seed ^ 51, 3, 4);
		boolean wooden = Hash.unit(seed ^ 52) < 0.4;
		BlockState stem = wooden ? wood.strippedLog().defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		BlockState arm = wooden ? wood.strippedLog().defaultBlockState().setValue(RotatedPillarBlock.AXIS, facing.getClockWise().getAxis()) : stem;
		Blueprint bp = new Blueprint();
		for (int up = 1; up <= height; up++) {
			bp.put(new BlockPos(x, groundY + up, z), stem);
		}
		Direction right = facing.getClockWise();
		int armY = groundY + height - 1;
		bp.put(new BlockPos(x + right.getStepX(), armY, z + right.getStepZ()), arm);
		bp.put(new BlockPos(x - right.getStepX(), armY, z - right.getStepZ()), arm);
		BlockPos base = new BlockPos(x, groundY + 1, z);
		return new Build(bp, base, height, new BoundingBox(base));
	}

	/**
	 * A small sand pyramid on the sea floor: {@code size} layers, the base {@code 2 * size - 1} wide. The site is
	 * its core; {@link #pyramidPocket} adds the 1x1 air pocket there.
	 */
	public static Build pyramid(int x, int floorY, int z, int size) {
		Blueprint bp = new Blueprint();
		BlockState sand = Blocks.SAND.defaultBlockState();
		for (int k = 0; k < size; k++) {
			int e = size - 1 - k;
			for (int dx = -e; dx <= e; dx++) {
				for (int dz = -e; dz <= e; dz++) {
					BlockPos pos = new BlockPos(x + dx, floorY + 1 + k, z + dz);
					bp.put(pos, sand);
					if (k == 0) {
						bp.foundation(pos.below(), sand, 4);
					}
				}
			}
		}
		BlockPos core = pyramidCore(x, floorY, z, size);
		return new Build(bp, core, size, new BoundingBox(core));
	}

	public static BlockPos pyramidCore(int x, int floorY, int z, int size) {
		return new BlockPos(x, floorY + 1 + (size - 1) / 2, z);
	}

	/** The air pocket at the core, with sandstone above it so the sand cannot fall in. */
	public static Blueprint pyramidPocket(BlockPos core) {
		return new Blueprint().clear(core).put(core.above(), Blocks.SANDSTONE.defaultBlockState());
	}

	public enum LightKind { GLOWSTONE, OCEAN_TORCH, CAVE_TORCH }

	/** A lone light. {@code y} is the ground (or water surface, or cave floor) the light rests on. The site is the light. */
	public static Build lonelight(LightKind kind, int x, int y, int z) {
		Blueprint bp = new Blueprint();
		BlockPos light = new BlockPos(x, y + 1, z);
		switch (kind) {
			case GLOWSTONE -> bp.put(light, Blocks.GLOWSTONE.defaultBlockState());
			case OCEAN_TORCH -> {
				bp.put(new BlockPos(x, y, z), Blocks.COBBLESTONE.defaultBlockState());
				bp.put(light, Blocks.TORCH.defaultBlockState());
			}
			case CAVE_TORCH -> bp.put(light, Blocks.REDSTONE_TORCH.defaultBlockState());
		}
		return new Build(bp, light, 1, new BoundingBox(light));
	}

	/** Clears the build's volume (terrain, plants, tree parts) above the floor. */
	private static void clearVolume(Blueprint bp, Frame f, int half, int fromUp, int toUp) {
		for (int r = -half - 1; r <= half + 1; r++) {
			for (int w = -half - 1; w <= half + 1; w++) {
				boolean margin = Math.abs(r) == half + 1 || Math.abs(w) == half + 1;
				BlockPos base = f.at(r, fromUp, w);
				if (margin) {
					bp.add(new Blueprint.Column(base, f.y + toUp + 6, true));
				} else {
					bp.add(new Blueprint.Column(base, f.y + toUp + 6, false));
				}
			}
		}
	}

	private static BoundingBox interior(Frame f, int half, int up) {
		BlockPos a = f.at(-half, 1, -half);
		BlockPos b = f.at(half, up, half);
		return BoundingBox.fromCorners(a, b);
	}
}
