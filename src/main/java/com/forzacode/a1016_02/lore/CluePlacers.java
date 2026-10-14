package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.lore.Placing.Request;
import com.forzacode.a1016_02.lore.Placing.Result;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The placement rules of the story clues (D-066, docs/STORY_CLUES.md): Ending D's chamber plates and the team's note
 * (F35, F36, once the team's stair is finished, {@link TeamChamber}), and the late clues (F38 to F44, once a group has
 * been stuck on a step for some in-game days, {@link LateClues}). Everything here was left by people
 * ({@code lore:left/<id>}), never by him; like every rule it goes through {@link Build} (out of view, all or nothing),
 * reads only loaded chunks ({@link ChunkGate}), and the engine places each fragment once. A late clue only turns up
 * where the group has been or was pointed (the camp, the grove, the cairn's shore, the seed pyramid, the chamber's
 * chest) and never within {@link LoreConfig#lateClueMinFromBase} of any known player's base.
 */
final class CluePlacers {
	/** The rules this class places. */
	static final Set<String> RULES = Set.of("chamber_plates", "chamber_chest", "camp_light", "grove_sign", "shore_sign", "seed_light");

	/** F30's twin on bedrock and its sign in the grove, as lore anchored them. */
	private static final String TWIN = UnbreakableSigns.ANCHORS[1];
	private static final String GROVE_SIGN = UnbreakableSigns.ANCHORS[0];
	/** F40: the search grows this many blocks per ring, each ring's chunks asked for first. */
	private static final int SHORE_STEP = 24;

	/** The cells two blocks round a column (F35's blank plate stands on one), north first, clockwise. */
	private static final List<int[]> RING_TWO = ring(2);
	/** Offsets 2 to 4 blocks round a column (F39). */
	private static final List<int[]> TWO_TO_FOUR = band(2, 4);
	/** Offsets by distance, nearest first, up to this many blocks (F40's shore search). */
	private static final int NEAREST_MAX = 128;
	private static List<int[]> nearest = List.of();

	private CluePlacers() {
	}

	static Optional<Result> place(Request req) {
		return switch (req.placement().rule()) {
			case "chamber_plates" -> chamberPlates(req);
			case "chamber_chest" -> chamberChest(req);
			case "camp_light" -> campLight(req);
			case "grove_sign" -> groveSign(req);
			case "shore_sign" -> shoreSign(req);
			case "seed_light" -> seedLight(req);
			default -> Optional.empty();
		};
	}

	// --- shared ---

	/** A late clue never stands this near any known player's base; the chamber's plates and note are not late clues. */
	static boolean keepsAway(Request req, BlockPos pos) {
		if (!LateClues.isLate(req.fragment())) {
			return false;
		}
		double min = LoreConfig.get().lateClueMinFromBase;
		return req.facts().bases().stream().anyMatch(base -> base.dimension().equals(req.level().dimension())
				&& Builders.horizontalDistSqr(base.pos(), pos) < min * min);
	}

	private static Optional<BlockPos> here(Request req, Optional<GlobalPos> pos) {
		return pos.filter(p -> p.dimension().equals(req.level().dimension())).map(GlobalPos::pos);
	}

	/** Open and empty: air or a plant, no fluid, no block entity. */
	static boolean free(ServerLevel level, BlockPos pos) {
		return Terrain.isAirOrReplaceable(level.getBlockState(pos)) && level.getBlockEntity(pos) == null;
	}

	/** Dry ground to stand a sign or a post on: a full block, not a tree, no water on it or in it. */
	private static boolean dryGround(ServerLevel level, BlockPos ground) {
		BlockState state = level.getBlockState(ground);
		return state.isSolidRender() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && level.getFluidState(ground).isEmpty()
				&& Terrain.isFloor(level, ground.above()) && level.getFluidState(ground.above()).isEmpty();
	}

	// --- F35 the plates ---

	/** One plate: its cobblestone ledge on the floor, nothing on it, and the plate two above it on the wall. */
	record Plate(BlockPos ledge, BlockPos sign, Direction facing) {
	}

	/**
	 * F35, once the stair is finished: six waxed wall signs, a name on the second line of each, every one two above a
	 * single cobblestone ledge on the chamber's floor with nothing on it (room for one light), spread round the walls;
	 * and a seventh standing sign, blank and not waxed, on bedrock two blocks from F30's twin (step 6's spot). One batch.
	 */
	static Optional<Result> chamberPlates(Request req) {
		ServerLevel level = req.level();
		Optional<BoundingBox> found = TeamChamber.box(req.facts(), level.dimension());
		Optional<BlockPos> twin = here(req, req.facts().anchor(TWIN));
		if (found.isEmpty() || twin.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = found.get();
		if (!ChunkGate.ready(req, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
			return Optional.empty();
		}
		List<String> names = req.fragment().plates();
		List<Plate> spots = spread(plateSpots(level, box), names.size());
		Optional<Pair<BlockPos, Direction>> blank = blankSpot(level, box, twin.get());
		if (spots.size() < names.size() || blank.isEmpty()) {
			return Optional.empty();
		}
		Build build = Placers.left(req);
		Result result = new Result(blank.get().getFirst());
		for (int n = 0; n < names.size(); n++) {
			Plate plate = spots.get(n);
			build.leave(plate.ledge(), Blocks.COBBLESTONE.defaultBlockState());
			build.sign(plate.sign(), Placers.wallSign(plate.facing()), FragmentItems.signText(List.of("", names.get(n), "", "")), true);
			result.read(plate.sign());
		}
		build.sign(blank.get().getFirst(), Placers.standingSign(blank.get().getSecond()), FragmentItems.signText(List.of()), false);
		return build.commit() ? Optional.of(result) : Optional.empty();
	}

	/** Every wall spot for a plate, walking round the room (no corners: the chest goes there). */
	static List<Plate> plateSpots(ServerLevel level, BoundingBox box) {
		List<Plate> spots = new ArrayList<>();
		for (int z = box.minZ() + 1; z <= box.maxZ() - 1; z++) {
			plate(level, box, box.minX(), z, Direction.EAST, spots);
		}
		for (int x = box.minX() + 1; x <= box.maxX() - 1; x++) {
			plate(level, box, x, box.maxZ(), Direction.NORTH, spots);
		}
		for (int z = box.maxZ() - 1; z >= box.minZ() + 1; z--) {
			plate(level, box, box.maxX(), z, Direction.WEST, spots);
		}
		for (int x = box.maxX() - 1; x >= box.minX() + 1; x--) {
			plate(level, box, x, box.minZ(), Direction.SOUTH, spots);
		}
		return spots;
	}

	private static void plate(ServerLevel level, BoundingBox box, int x, int z, Direction facing, List<Plate> spots) {
		Optional<BlockPos> floor = floor(level, box, x, z);
		if (floor.isEmpty()) {
			return;
		}
		BlockPos ledge = floor.get();
		BlockPos sign = ledge.above(2);
		BlockPos wall = sign.relative(facing.getOpposite());
		if (sign.getY() <= box.maxY() && free(level, ledge.above()) && free(level, sign) && level.getBlockState(wall).isFaceSturdy(level, wall, facing)) {
			spots.add(new Plate(ledge, sign, facing));
		}
	}

	/** {@code count} of the spots, spread evenly round the room (all of them if there are no more). */
	static <T> List<T> spread(List<T> spots, int count) {
		if (spots.size() <= count) {
			return spots;
		}
		List<T> picked = new ArrayList<>(count);
		for (int n = 0; n < count; n++) {
			picked.add(spots.get((int) ((n + 0.5) * spots.size() / count)));
		}
		return picked;
	}

	/** The chamber floor in a column: its lowest open cell that stands on solid ground (bedrock). */
	static Optional<BlockPos> floor(ServerLevel level, BoundingBox box, int x, int z) {
		for (int y = box.minY() + 1; y <= box.maxY(); y++) {
			BlockPos pos = new BlockPos(x, y, z);
			if (free(level, pos) && level.getBlockState(pos.below()).isSolid()) {
				return Optional.of(pos);
			}
		}
		return Optional.empty();
	}

	/** F35's blank plate: an open cell standing on bedrock two blocks from the twin, the sign facing away from it. */
	static Optional<Pair<BlockPos, Direction>> blankSpot(ServerLevel level, BoundingBox box, BlockPos twin) {
		for (int[] d : RING_TWO) {
			for (int dy = -2; dy <= 2; dy++) {
				BlockPos pos = twin.offset(d[0], dy, d[1]);
				if (box.isInside(pos) && free(level, pos) && level.getBlockState(pos.below()).is(Blocks.BEDROCK)) {
					return Optional.of(Pair.of(pos, Direction.getApproximateNearest(d[0], 0, d[1])));
				}
			}
		}
		return Optional.empty();
	}

	// --- F36, F42 to F44: the chamber's chest ---

	/**
	 * A book into the chamber's chest: the one a note already went in, else F25's when F25 lies in the chamber; with
	 * neither standing (or no room), a new chest on the floor of a free corner of the chamber, facing the room.
	 */
	static Optional<Result> chamberChest(Request req) {
		ServerLevel level = req.level();
		Optional<BoundingBox> found = TeamChamber.box(req.facts(), level.dimension());
		if (found.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = found.get();
		if (!ChunkGate.ready(req, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
			return Optional.empty();
		}
		ItemStack book = Placers.book(req);
		Optional<BlockPos> chest = chestIn(req, box);
		if (chest.isPresent()) {
			if (keepsAway(req, chest.get()) || !Build.insert(req.traces(), level, chest.get(), book, req.id())) {
				return Optional.empty();
			}
			return Optional.of(new Result(chest.get()).anchor(TeamChamber.CHEST, chest.get()));
		}
		BlockPos center = box.getCenter();
		int[][] corners = {{box.minX(), box.minZ()}, {box.maxX(), box.maxZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.minZ()}};
		for (int[] corner : corners) {
			Optional<BlockPos> floor = floor(level, box, corner[0], corner[1]);
			if (floor.isEmpty() || keepsAway(req, floor.get())) {
				continue;
			}
			Direction facing = Direction.getApproximateNearest(center.getX() - corner[0], 0, center.getZ() - corner[1]);
			if (!Placers.left(req).chest(floor.get(), facing, List.of(book)).commit()) {
				return Optional.empty();
			}
			return Optional.of(new Result(floor.get()).anchor(TeamChamber.CHEST, floor.get()));
		}
		return Optional.empty();
	}

	/** The chamber's chest, if it still stands in the chamber with room in it. */
	static Optional<BlockPos> chestIn(Request req, BoundingBox box) {
		for (Optional<GlobalPos> at : List.of(req.facts().anchor(TeamChamber.CHEST), req.facts().placed("F25"))) {
			Optional<BlockPos> pos = here(req, at).filter(box::isInside);
			if (pos.isPresent() && req.level().getBlockEntity(pos.get()) instanceof Container container && hasRoom(container)) {
				return pos;
			}
		}
		return Optional.empty();
	}

	private static boolean hasRoom(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	// --- F38 the light at the camp ---

	/**
	 * F38: a lone light at the edge of F28's Abandoned Camp, one glowstone block on a fence post, on dry ground
	 * {@link LoreConfig#campLightMinDistance} to {@link LoreConfig#campLightMaxDistance} blocks from the map's chest
	 * (the farthest dry spot first, round the camp from a random side).
	 */
	static Optional<Result> campLight(Request req) {
		ServerLevel level = req.level();
		Optional<BlockPos> camp = here(req, req.facts().placed("F28"));
		if (camp.isEmpty()) {
			return Optional.empty();
		}
		LoreConfig config = LoreConfig.get();
		int max = Math.max(2, config.campLightMaxDistance);
		int min = Math.max(1, Math.min(config.campLightMinDistance, max));
		BlockPos center = camp.get();
		if (!ChunkGate.ready(req, center, max + 1)) {
			return Optional.empty();
		}
		double start = req.random().nextDouble() * Math.PI * 2.0;
		for (int distance = max; distance >= min; distance--) {
			for (int n = 0; n < 24; n++) {
				double angle = start + n * Math.PI * 2.0 / 24.0;
				int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
				int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
				BlockPos ground = Terrain.ground(level, x, z);
				BlockPos post = ground.above();
				if (!dryGround(level, ground) || !free(level, post.above()) || keepsAway(req, post)) {
					continue;
				}
				Build build = Placers.left(req).leave(post, Blocks.OAK_FENCE.defaultBlockState()).leave(post.above(), Blocks.GLOWSTONE.defaultBlockState());
				return build.commit() ? Optional.of(new Result(post.above())) : Optional.empty();
			}
		}
		return Optional.empty();
	}

	// --- F39 cut one ---

	/** F39: a standing sign on dry ground 2 to 4 blocks from F30's oldest poplar, its words toward the trunk. */
	static Optional<Result> groveSign(Request req) {
		ServerLevel level = req.level();
		Optional<BlockPos> f30 = here(req, req.facts().anchor(GROVE_SIGN));
		if (f30.isEmpty()) {
			return Optional.empty();
		}
		BlockPos sign = f30.get();
		if (!ChunkGate.ready(req, sign, 6)) {
			return Optional.empty();
		}
		BlockPos trunk = trunkOf(level, sign);
		List<int[]> offsets = new ArrayList<>(TWO_TO_FOUR);
		Collections.shuffle(offsets, new java.util.Random(req.random().nextLong()));
		for (int[] d : offsets) {
			BlockPos ground = Terrain.ground(level, trunk.getX() + d[0], trunk.getZ() + d[1]);
			BlockPos spot = ground.above();
			if (Math.abs(spot.getY() - sign.getY()) > 3 || !dryGround(level, ground) || keepsAway(req, spot)) {
				continue;
			}
			Direction facing = Direction.getApproximateNearest(-d[0], 0, -d[1]);
			Build build = Placers.left(req).sign(spot, Placers.standingSign(facing), FragmentItems.signText(req.fragment(), req.playerName()));
			return build.commit() ? Optional.of(new Result(spot).read(spot)) : Optional.empty();
		}
		return Optional.empty();
	}

	/** The poplar F30's grove sign hangs on (the block behind a wall sign); a standing sign is its own spot. */
	static BlockPos trunkOf(ServerLevel level, BlockPos sign) {
		BlockState state = level.getBlockState(sign);
		return state.getBlock() instanceof WallSignBlock ? sign.relative(state.getValue(WallSignBlock.FACING).getOpposite()) : sign;
	}

	// --- F40 the nearest one ---

	/**
	 * F40: a standing sign on the dry shore nearest the cairn (F13), facing it: the nearest dry ground with the sea
	 * right beside it, never on the cairn's own pyramid. Looked for in growing rings, each ring's chunks loaded first.
	 */
	static Optional<Result> shoreSign(Request req) {
		ServerLevel level = req.level();
		Optional<BlockPos> cairn = here(req, req.facts().placed("F13"));
		if (cairn.isEmpty()) {
			return Optional.empty();
		}
		BlockPos center = cairn.get();
		int max = Mth.clamp(LoreConfig.get().shoreSearchRadius, Math.max(0, LoreConfig.get().shoreMinDistance) + 1, NEAREST_MAX);
		for (int radius = Math.min(SHORE_STEP, max); ; radius = Math.min(max, radius + SHORE_STEP)) {
			if (!ChunkGate.ready(req, center, radius + 1)) {
				return Optional.empty();
			}
			Optional<BlockPos> shore = nearestShore(req, center, radius);
			if (shore.isPresent()) {
				BlockPos spot = shore.get();
				Direction facing = Direction.getApproximateNearest(center.getX() - spot.getX(), 0, center.getZ() - spot.getZ());
				Build build = Placers.left(req).sign(spot, Placers.standingSign(facing), FragmentItems.signText(req.fragment(), req.playerName()));
				return build.commit() ? Optional.of(new Result(spot).read(spot)) : Optional.empty();
			}
			if (radius >= max) {
				return Optional.empty();
			}
		}
	}

	/** The nearest dry spot within {@code radius} of the cairn with the sea right beside it (loaded chunks only). */
	static Optional<BlockPos> nearestShore(Request req, BlockPos center, int radius) {
		ServerLevel level = req.level();
		int r2 = radius * radius;
		int min = Math.max(0, LoreConfig.get().shoreMinDistance);
		for (int[] d : nearest()) {
			int d2 = d[0] * d[0] + d[1] * d[1];
			if (d2 > r2) {
				break;
			}
			if (d2 < min * min) {
				continue;
			}
			BlockPos ground = Terrain.ground(level, center.getX() + d[0], center.getZ() + d[1]);
			BlockPos spot = ground.above();
			if (dryGround(level, ground) && besideSea(level, spot) && !keepsAway(req, spot)) {
				return Optional.of(spot);
			}
		}
		return Optional.empty();
	}

	/** Water right beside this spot, level with it or one below. */
	static boolean besideSea(ServerLevel level, BlockPos spot) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy >= -1; dy--) {
				if (level.getFluidState(spot.relative(dir).above(dy)).is(FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	// --- F41 the light on the seed ---

	/**
	 * F41: on the seed pyramid (F07), a lone cobblestone block rising from its top to just above the water (one block on
	 * it if it already stands out), a torch on it, and the sign on the block's side above the water.
	 */
	static Optional<Result> seedLight(Request req) {
		ServerLevel level = req.level();
		Optional<BlockPos> seed = here(req, req.facts().placed("F07"));
		if (seed.isEmpty()) {
			return Optional.empty();
		}
		BlockPos core = seed.get();
		if (!ChunkGate.ready(req, core, 2)) {
			return Optional.empty();
		}
		BlockPos open = core.above();
		for (int guard = 0; guard < 48 && !level.getBlockState(open).canBeReplaced(); guard++) {
			open = open.above();
		}
		if (!level.getBlockState(open).canBeReplaced()) {
			return Optional.empty();
		}
		// Up through the water over the pyramid to the first cell above it: the lone block's top stands just out of it.
		List<BlockPos> column = new ArrayList<>();
		BlockPos cell = open;
		for (int guard = 0; guard < level.getHeight(); guard++) {
			if (!level.getBlockState(cell).canBeReplaced() || level.getBlockEntity(cell) != null) {
				return Optional.empty();
			}
			column.add(cell);
			if (!level.getFluidState(cell).is(FluidTags.WATER)) {
				break;
			}
			cell = cell.above();
		}
		BlockPos block = column.getLast();
		BlockPos torch = block.above();
		if (!free(level, torch) || keepsAway(req, block)) {
			return Optional.empty();
		}
		for (Direction side : Terrain.shuffledHorizontal(req.random())) {
			BlockPos sign = block.relative(side);
			if (!free(level, sign)) {
				continue;
			}
			Build build = Placers.left(req);
			column.forEach(at -> build.leave(at, Blocks.COBBLESTONE.defaultBlockState()));
			build.leave(torch, Blocks.TORCH.defaultBlockState());
			build.sign(sign, Placers.wallSign(side), FragmentItems.signText(req.fragment(), req.playerName()));
			return build.commit() ? Optional.of(new Result(block).read(sign)) : Optional.empty();
		}
		return Optional.empty();
	}

	// --- offsets ---

	/** Offsets at exactly {@code r} blocks (the square ring), north first, clockwise. */
	private static List<int[]> ring(int r) {
		List<int[]> cells = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
					cells.add(new int[] {dx, dz});
				}
			}
		}
		cells.sort(Comparator.comparingDouble(d -> (Math.atan2(d[0], -d[1]) + Math.PI * 2.0) % (Math.PI * 2.0)));
		return List.copyOf(cells);
	}

	/** Offsets between {@code min} and {@code max} blocks away (round), in a fixed order. */
	private static List<int[]> band(int min, int max) {
		List<int[]> cells = new ArrayList<>();
		for (int dx = -max; dx <= max; dx++) {
			for (int dz = -max; dz <= max; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 >= min * min && d2 <= max * max) {
					cells.add(new int[] {dx, dz});
				}
			}
		}
		return List.copyOf(cells);
	}

	/** Every offset up to {@link #NEAREST_MAX} blocks, nearest first (built once). */
	private static synchronized List<int[]> nearest() {
		if (nearest.isEmpty()) {
			List<int[]> cells = new ArrayList<>();
			for (int dx = -NEAREST_MAX; dx <= NEAREST_MAX; dx++) {
				for (int dz = -NEAREST_MAX; dz <= NEAREST_MAX; dz++) {
					if (dx * dx + dz * dz <= NEAREST_MAX * NEAREST_MAX) {
						cells.add(new int[] {dx, dz});
					}
				}
			}
			cells.sort(Comparator.comparingInt(d -> d[0] * d[0] + d[1] * d[1]));
			nearest = List.copyOf(cells);
		}
		return nearest;
	}
}
