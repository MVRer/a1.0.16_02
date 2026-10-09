package com.forzacode.a1016_02.lore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * F04, "not found" (the 404): after "Stop.", a fragment site the player visited is gone. The structure there (a
 * hut, a house, a cross: the built blocks connected to the site, never the player's own) is removed down to its
 * floor, the floor becomes flat dirt, and one of the site's own signs, if it had one, is moved to the middle and
 * blanked. Nothing is created. Never a site of the Ending D chain, F03, F10, F20 or F27; never near the base.
 * The first visited site that has such a structure is taken; one per world.
 */
final class PlaceNotFound {
	/** Fragments whose place must stay: the Ending D chain, the player's own sign, F04 itself, Ending B's places. */
	static final Set<String> NEVER = Set.of("F07", "F13", "F23", "F25", "F28", "F30", "F03", "F04", "F10", "F20", "F27");
	/** Set once F04 happened. */
	static final String DONE_FLAG = "lore:f04_done";
	static final String CAUSE = "F04";
	/** How high above the floor a structure is followed. */
	private static final int MAX_HEIGHT = 16;
	/** How far sideways beyond the seed area a structure is followed. */
	static final int REACH = 8;

	/**
	 * What will go.
	 *
	 * @param remove    built blocks above the floor
	 * @param dirt      floor blocks that become dirt
	 * @param plants    plants standing on the floor (removed so it is flat)
	 * @param sign      one of the site's own signs, moved to {@code signTo} as {@code signState}
	 * @param needsBlank the moved sign still has text and must be blanked after the move
	 */
	record Plan(BlockPos center, int floorY, List<BlockPos> remove, List<BlockPos> dirt, List<BlockPos> plants, Optional<BlockPos> sign,
			BlockPos signTo, BlockState signState, boolean needsBlank) {
		BlockPos floorCenter() {
			return new BlockPos(signTo.getX(), floorY, signTo.getZ());
		}
	}

	private PlaceNotFound() {
	}

	/** May this fragment's site be the one that is not found? */
	static boolean mayTake(String id) {
		return !NEVER.contains(id) && !LoreConfig.get().notFoundExclude.contains(id);
	}

	/**
	 * Fires F04 for the first visited site that can go now. {@code SKIPPED} before "Stop.", once done, or when no
	 * visited site has a structure; {@code NO_SPOT} while one is in view or its chunks load.
	 */
	static FireResult fire(MinecraftServer server, HerobrineState state, TellingData data, TraceService traces, SignEdits.Editor editor,
			boolean canBlank, Optional<BlockPos> base, RandomSource random, LoreConfig config) {
		if (!state.stopFired() || state.hasFlag(DONE_FLAG) || data.notFound().isPresent()) {
			return FireResult.SKIPPED;
		}
		boolean waiting = false;
		for (String id : data.visited()) {
			GlobalPos placed = state.fragmentsPlaced().get(id);
			if (!mayTake(id) || placed == null) {
				continue;
			}
			ServerLevel level = server.getLevel(placed.dimension());
			if (level == null) {
				continue;
			}
			SiteRegistry.Site site = siteOf(server, id).orElse(null);
			BlockPos center = site != null && site.dimension().equals(placed.dimension()) ? site.pos() : placed.pos();
			if (base.isPresent() && Builders.horizontalDistSqr(center, base.get()) < (double) config.notFoundMinFromBase * config.notFoundMinFromBase
					|| UntouchedGrove.contains(server, GlobalPos.of(placed.dimension(), center))) {
				continue;
			}
			if (!ChunkGate.request(level, center, config.notFoundSeedRadius + REACH + 1)) {
				waiting = true;
				continue;
			}
			List<BlockPos> keep = NEVER.stream().map(state.fragmentsPlaced()::get).filter(p -> p != null && p.dimension().equals(placed.dimension()))
					.map(GlobalPos::pos).toList();
			Optional<Plan> plan = plan(level, placed.pos(), center, config, pos -> Services.watch().wasPlacedByPlayer(level, pos), keep, random);
			if (plan.isEmpty() || plan.get().needsBlank() && !canBlank) {
				// No structure here; or its sign could not be blanked yet (core's editSign): never leave a sign with text.
				continue;
			}
			if (!commit(level, plan.get(), traces)) {
				waiting = true;
				continue;
			}
			finish(level, id, plan.get(), state, data, editor);
			return FireResult.FIRED;
		}
		return waiting ? FireResult.NO_SPOT : FireResult.SKIPPED;
	}

	/** The registry site the fragment filled, if it used one. */
	static Optional<SiteRegistry.Site> siteOf(MinecraftServer server, String id) {
		return LoreData.get(server).siteClaim(id).flatMap(siteId -> Services.sites().all().stream().filter(s -> s.id() == siteId).findFirst());
	}

	/**
	 * Plans the removal around {@code center}: the built blocks within {@link LoreConfig#notFoundSeedRadius} of it
	 * (from the fragment's floor, {@code anchor.y - 1}, up) and everything built connected to them. Empty if that
	 * is too little (no structure), too much (not a site), not on the surface, or touches something that must stay.
	 */
	static Optional<Plan> plan(ServerLevel level, BlockPos anchor, BlockPos center, LoreConfig config, Predicate<BlockPos> playerPlaced,
			Collection<BlockPos> keep, RandomSource random) {
		int floorY = anchor.getY() - 1;
		int seed = config.notFoundSeedRadius;
		int bound = seed + REACH;
		Set<BlockPos> built = new LinkedHashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.getX() - seed, floorY, center.getZ() - seed, center.getX() + seed, floorY + 10,
				center.getZ() + seed)) {
			if (isBuilt(level, pos, playerPlaced) && built.add(pos.immutable())) {
				queue.add(pos.immutable());
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (n.getY() < floorY || n.getY() > floorY + MAX_HEIGHT || Math.abs(n.getX() - center.getX()) > bound
						|| Math.abs(n.getZ() - center.getZ()) > bound || built.contains(n) || !isBuilt(level, n, playerPlaced)) {
					continue;
				}
				built.add(n.immutable());
				queue.add(n.immutable());
				if (built.size() > config.notFoundMaxBlocks) {
					return Optional.empty();
				}
			}
		}
		if (built.size() < config.notFoundMinBlocks || built.stream().noneMatch(pos -> level.canSeeSky(pos.above()))
				|| built.stream().anyMatch(pos -> UnbreakableSigns.isProtected(level, pos))) {
			return Optional.empty();
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : built) {
			minX = Math.min(minX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		// Nothing the Ending D chain (or another place that must stay) needs may be in or right beside it.
		for (BlockPos kept : keep) {
			if (kept.getX() >= minX - 1 && kept.getX() <= maxX + 1 && kept.getZ() >= minZ - 1 && kept.getZ() <= maxZ + 1
					&& kept.getY() >= floorY - 1 && kept.getY() <= floorY + MAX_HEIGHT + 1) {
				return Optional.empty();
			}
		}
		Optional<BlockPos> sign = chooseSign(level, anchor, built);
		List<BlockPos> remove = new ArrayList<>();
		for (BlockPos pos : built) {
			// Above the floor everything built goes; in the floor only what cannot become dirt (a torch, a chest).
			boolean inFloor = pos.getY() == floorY && level.getBlockState(pos).isSolidRender();
			if (!inFloor && !sign.map(pos::equals).orElse(false)) {
				remove.add(pos);
			}
		}
		List<BlockPos> dirt = new ArrayList<>();
		List<BlockPos> plants = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos floor = new BlockPos(x, floorY, z);
				BlockState state = level.getBlockState(floor);
				boolean solid = !state.canBeReplaced() && state.isSolidRender();
				if (solid && !state.is(Blocks.DIRT) && !playerPlaced.test(floor) && (built.contains(floor) || Terrain.isNaturalSolid(state))) {
					dirt.add(floor);
				}
				BlockPos above = floor.above();
				BlockState plant = level.getBlockState(above);
				if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty() && !built.contains(above)) {
					plants.add(above);
				}
			}
		}
		// The sign goes to the middle of the patch, on a floor that will be solid.
		int cx = (minX + maxX) >> 1;
		int cz = (minZ + maxZ) >> 1;
		BlockPos signTo = null;
		List<BlockPos> columns = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				columns.add(new BlockPos(x, floorY + 1, z));
			}
		}
		columns.sort(Comparator.comparingDouble(p -> p.distSqr(new BlockPos(cx, floorY + 1, cz))));
		for (BlockPos column : columns) {
			BlockPos floor = column.below();
			boolean floorSolid = dirt.contains(floor) || level.getBlockState(floor).isSolidRender();
			boolean free = Terrain.isAirOrReplaceable(level.getBlockState(column)) || built.contains(column);
			if (floorSolid && free && !playerPlaced.test(column) && !playerPlaced.test(floor)) {
				signTo = column;
				break;
			}
		}
		if (signTo == null) {
			return Optional.empty();
		}
		BlockState signState = Blocks.AIR.defaultBlockState();
		boolean needsBlank = false;
		if (sign.isPresent()) {
			BlockState original = level.getBlockState(sign.get());
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			// A wall sign is stood up on the ground (same wood); the move carries its text, which is blanked after.
			Optional<BlockState> standing = original.getBlock() instanceof StandingSignBlock ? Optional.of(original) : standingVariant(original);
			if (standing.isEmpty()) {
				remove.add(sign.get());
				sign = Optional.empty();
			} else {
				signState = standing.get().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing));
				needsBlank = level.getBlockEntity(sign.get()) instanceof SignBlockEntity be && !SignEdits.isBlank(be);
			}
		}
		return Optional.of(new Plan(center, floorY, remove, dirt, plants, sign, signTo, signState, needsBlank));
	}

	/** Applies the plan as one out-of-view batch: removals, the sign's move, then the dirt. */
	static boolean commit(ServerLevel level, Plan plan, TraceService traces) {
		Build build = Build.his(traces, level, CAUSE);
		plan.remove().forEach(build::remove);
		plan.plants().forEach(build::remove);
		plan.sign().ifPresent(from -> {
			if (!from.equals(plan.signTo())) {
				build.move(from, plan.signTo());
			}
			build.convert(plan.signTo(), plan.signState());
		});
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		plan.dirt().forEach(pos -> build.convert(pos, dirt));
		return build.commit();
	}

	/** After the edit: the sign is blanked (or retried later), and F04 is recorded where it happened. */
	static void finish(ServerLevel level, String id, Plan plan, HerobrineState state, TellingData data, SignEdits.Editor editor) {
		GlobalPos at = GlobalPos.of(level.dimension(), plan.sign().isPresent() ? plan.signTo() : plan.floorCenter());
		if (plan.sign().isPresent() && plan.needsBlank()
				&& !editor.edit(level, plan.signTo(), List.of(), List.of(), "lore:his/" + CAUSE)) {
			data.addPendingBlank(at);
		}
		data.setNotFound(id);
		state.setFlag(DONE_FLAG, true);
		state.setFragmentPlaced("F04", at);
		LoreData lore = LoreData.get(level.getServer());
		lore.clearReadTargets("F04");
		lore.addReadTarget("F04", at);
		A1016_02.LOGGER.info("[a1016] lore: {}'s place is not found any more ({} blocks gone, flat dirt at {})", id, plan.remove().size(),
				plan.floorCenter().toShortString());
	}

	/** The sign the site keeps: the fragment's own if it is a sign, else the first one in the structure. */
	private static Optional<BlockPos> chooseSign(ServerLevel level, BlockPos anchor, Set<BlockPos> built) {
		Predicate<BlockPos> usable = pos -> isMovableSign(level.getBlockState(pos))
				&& !(level.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.isWaxed());
		if (built.contains(anchor) && usable.test(anchor)) {
			return Optional.of(anchor);
		}
		return built.stream().filter(usable).findFirst();
	}

	/** Standing and wall signs (not hanging ones). */
	static boolean isMovableSign(BlockState state) {
		return state.getBlock() instanceof StandingSignBlock || state.getBlock() instanceof WallSignBlock;
	}

	/** {@code oak_wall_sign} becomes {@code oak_sign}, keeping its wood. */
	static Optional<BlockState> standingVariant(BlockState wall) {
		if (!(wall.getBlock() instanceof WallSignBlock)) {
			return Optional.empty();
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(wall.getBlock());
		Identifier standing = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_wall_sign", "_sign"));
		return BuiltInRegistries.BLOCK.getOptional(standing).filter(b -> b instanceof StandingSignBlock).map(Block::defaultBlockState);
	}

	/** Made by someone (not natural, not the player's): building blocks, furniture, signs, lights, a house's logs. */
	static boolean isBuilt(ServerLevel level, BlockPos pos, Predicate<BlockPos> playerPlaced) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.canBeReplaced() || !builtMaterial(level, pos, state)) {
			return false;
		}
		return !playerPlaced.test(pos);
	}

	static boolean builtMaterial(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(BlockTags.PLANKS) || state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.WALLS)
				|| state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
				|| state.is(BlockTags.STANDING_SIGNS) || state.is(BlockTags.WALL_SIGNS) || state.is(BlockTags.ALL_HANGING_SIGNS)
				|| state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BUTTONS)
				|| state.is(BlockTags.PRESSURE_PLATES)) {
			return true;
		}
		Block block = state.getBlock();
		if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE || block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS
				|| block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.BRICKS || block == Blocks.GLASS || block == Blocks.CRAFTING_TABLE
				|| block == Blocks.BOOKSHELF || block == Blocks.LADDER || block == Blocks.TORCH || block == Blocks.WALL_TORCH
				|| block == Blocks.LANTERN || block == Blocks.SMOOTH_STONE || block == Blocks.GLOWSTONE) {
			return true;
		}
		if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof AbstractFurnaceBlock || block instanceof LecternBlock
				|| block instanceof JukeboxBlock || block instanceof IronBarsBlock) {
			return true;
		}
		return state.is(BlockTags.LOGS) && !isTreeLog(level, pos);
	}

	/** A log with leaves above it (within 2 blocks sideways, 8 up) is a tree's, not a house's. */
	private static boolean isTreeLog(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 1, -2), pos.offset(2, 8, 2))) {
			if (level.getBlockState(p).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}
}
