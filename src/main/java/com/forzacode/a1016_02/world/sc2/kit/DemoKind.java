package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The kit's demo kind {@code k0} (development and tests only: {@code WorldSc2} registers it only in a development
 * environment, and its structure JSON lives in the game tests' resources): a 20 x 12 x 20 stone house with a cellar,
 * the worked example of every kit rule. Its siting answers only chunks a test armed ({@link #arm}); its places are
 * recorded as {@code FLOOR}. The start's origin is the footprint's lowest corner at floor height; the door faces the
 * start's facing.
 */
public final class DemoKind {
	public static final String ID = "k0";
	/** The footprint's side and the box's height. */
	public static final int SIDE = 20;
	public static final int HEIGHT = 12;
	/** The box reaches this far under the floor (the cellar and its seal). */
	public static final int BELOW = 5;

	/** Its gates (a test may change them; never written to the config file). */
	public static final class Config implements Sc2Kind.Gates {
		public volatile int fromStage = 1;
		public volatile int minOriginDistance = 0;

		@Override
		public int fromStage() {
			return fromStage;
		}

		@Override
		public int minOriginDistance() {
			return minOriginDistance;
		}
	}

	public static final Config CONFIG = new Config();
	private static final Map<ChunkPos, Start> ARMED = new ConcurrentHashMap<>();

	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.FLOOR, () -> CONFIG, query -> Optional.ofNullable(ARMED.get(query.chunk())),
			House::new, Sc2Kind.Interiors.FROM_SHAPE);

	private DemoKind() {
	}

	/** Tests: siting answers this start in this chunk. */
	public static void arm(ChunkPos chunk, Start start) {
		ARMED.put(chunk, start);
	}

	/** Tests: siting answers nothing in this chunk again. */
	public static void disarm(ChunkPos chunk) {
		ARMED.remove(chunk);
	}

	/** The house. */
	public static final class House implements Shape {
		private static final BlockState AIR = Blocks.AIR.defaultBlockState();
		private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
		private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
		private static final int CELLAR_LO = 8;
		private static final int CELLAR_HI = 11;

		private final Start start;
		private final Frame frame;
		private final int x0;
		private final int y0;
		private final int z0;
		private final BoundingBox box;
		private final BoundingBox inner;
		private final BoundingBox cellar;

		House(Start start, Terrain terrain) {
			this.start = start;
			this.frame = Frame.square(start.origin(), SIDE, start.facing());
			this.x0 = start.origin().getX();
			this.y0 = start.origin().getY();
			this.z0 = start.origin().getZ();
			this.box = new BoundingBox(x0, y0 - BELOW, z0, x0 + SIDE - 1, y0 - BELOW + HEIGHT - 1, z0 + SIDE - 1);
			this.inner = new BoundingBox(x0 + 1, y0 + 1, z0 + 1, x0 + SIDE - 2, y0 + 4, z0 + SIDE - 2);
			this.cellar = frame.box(CELLAR_LO, -3, -CELLAR_LO, CELLAR_HI, -1, -CELLAR_HI);
		}

		@Override
		public BoundingBox box() {
			return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
		}

		@Override
		public BlockPos anchor() {
			return frame.pos(SIDE / 2, 1, -SIDE / 2);
		}

		@Override
		public BlockPos entrance() {
			return frame.pos(9, 1, 0);
		}

		@Override
		public Direction entranceFacing() {
			return start.facing();
		}

		@Override
		public Interior interior() {
			return Interior.union(List.of(Interior.box(inner), Interior.box(cellar)));
		}

		/** Seal columns: the cellar's. */
		private long cellarColumn(int x, int z) {
			return x >= cellar.minX() && x <= cellar.maxX() && z >= cellar.minZ() && z <= cellar.maxZ() ? Seal.range(cellar.minY(), cellar.maxY()) : Seal.NONE;
		}

		@Override
		public void fill(Writer w, BoundingBox clip) {
			long seed = start.seed();
			// 1. Clear the footprint above the floor; 2. a foundation under it down to solid ground.
			Draw.columns(w, box, (x, z) -> {
				Draw.clear(w, x, z, y0 + 1, box.maxY());
				Draw.foundation(w, x, z, y0 - 1, COBBLE, 4);
			});
			// 3. The seal round the cellar (fluid, air, sand within 2 become stone), before anything is opened.
			Seal.around(w, clip, this::cellarColumn, Seal.REACH, (x, y, z) -> COBBLE);
			// 4. The cellar, and a ladder up its back wall to a hatch in the floor.
			Draw.fill(w, cellar, AIR);
			BlockState ladder = frame.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
			for (int u = -3; u <= 0; u++) {
				w.set(frame.pos(CELLAR_LO, u, -CELLAR_HI), ladder);
			}
			// 5. The floor (planks inside a stone rim), 6. walls of old stone, 7. a roof with four lights.
			Draw.paint(w, new BoundingBox(x0, y0, z0, x0 + SIDE - 1, y0, z0 + SIDE - 1),
					(x, y, z) -> x == x0 || z == z0 || x == x0 + SIDE - 1 || z == z0 + SIDE - 1 ? COBBLE : PLANKS);
			BoundingBox walls = new BoundingBox(x0, y0 + 1, z0, x0 + SIDE - 1, y0 + 5, z0 + SIDE - 1);
			// Per-cell randomness by the cell's place in the shape (not in the world): the same start looks the same anywhere.
			Draw.Mix face = Draw.Mix.of(COBBLE, 7).and(MOSSY, 3);
			Draw.paint(w, walls, (x, y, z) -> Draw.depth(walls, x, y, z) == 0 ? face.at(seed, x - x0, y - y0, z - z0) : null);
			for (int[] light : new int[][] {{5, -5}, {14, -5}, {5, -14}, {14, -14}}) {
				w.set(frame.pos(light[0], 5, light[1]), Blocks.GLOWSTONE.defaultBlockState());
			}
			// 8. The inside, carved after the seal; the hatch over the ladder.
			Draw.fill(w, inner, AIR);
			w.set(frame.pos(CELLAR_LO, 0, -CELLAR_HI), ladder);
			// 9. The doorway (1 x 2) through the front wall, inward from the outer mouth cell.
			Draw.tunnel(w, frame.pos(9, 1, 0), frame.turn(Direction.SOUTH), 1, 1, 2, 0, 1, AIR);
			// 10. What is inside: a chest and a shelf at the back facing the door, panes, a fence, torches, a chair.
			Direction front = frame.turn(Direction.NORTH);
			int items = Draw.between(seed, 1, 1, 16);
			Draw.chest(w, frame.pos(2, 1, -18), front, List.of(new ItemStack(Items.COBBLESTONE, items), new ItemStack(Items.TORCH, 1), new ItemStack(Items.BREAD, 2)));
			Draw.shelf(w, frame.pos(17, 1, -18), front, List.of(new ItemStack(Items.BOOK), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.BOOK)));
			for (int f = -5; f >= -6; f--) {
				for (int u = 2; u <= 3; u++) {
					w.set(frame.pos(0, u, f), Blocks.GLASS_PANE.defaultBlockState());
					w.set(frame.pos(SIDE - 1, u, f), Blocks.GLASS_PANE.defaultBlockState());
				}
			}
			for (int r = 4; r <= 7; r++) {
				w.set(frame.pos(r, 1, -12), Blocks.OAK_FENCE.defaultBlockState());
			}
			BlockState torch = frame.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
			w.set(frame.pos(5, 3, -1), torch);
			w.set(frame.pos(14, 3, -1), torch);
			w.set(frame.pos(12, 1, -12), frame.turn(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)));
		}
	}
}
