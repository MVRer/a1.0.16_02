package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry.Site;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.lore.Builders.House;
import com.forzacode.a1016_02.lore.Builders.Move;
import com.forzacode.a1016_02.lore.Builders.Pyramid;
import com.forzacode.a1016_02.lore.Builders.Room;
import com.forzacode.a1016_02.lore.Builders.Tunnel;
import com.forzacode.a1016_02.lore.Placing.Request;
import com.forzacode.a1016_02.lore.Placing.Result;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * The placement rules named in the fragment data ({@code placement.rule}). Each one fills a site that world or
 * dig recorded when there is one, and otherwise builds a minimal place of its own (recorded in the
 * {@code SiteRegistry} too), always through {@link Build} so every change is out of view. Rules read only loaded
 * chunks: before probing an area they ask {@link ChunkGate}, which queues the missing chunks for loading by
 * ticket and marks the request as waiting. Returns empty when nothing could be placed right now; the engine tries
 * again later (soon, with the same candidates, when it was waiting for chunks).
 */
final class Placers {
	/** Rules the fragment engine does not place (telling and Ending B, the next task). */
	static final Set<String> NOT_PLACED = Set.of("telling", "ending_b");
	/** Every rule {@link #place} knows. */
	static final Set<String> RULES = Set.of("ruined_hut", "hut_or_tunnel", "cave_chest", "tunnel_end_chest", "tunnel_end_sign",
			"pyramid_core", "pyramid_chest", "cairn", "grove_burial", "visited_grove_burial", "first_table", "white_eyes_room",
			"under_base_chest", "below_spawn", "test_room", "test_room_chest", "test_room_loft", "test_room_below", "emptied_house",
			"panic_tower", "cross_sign", "stair_bottom", "restored_tree", "house_copy", "camp_map", "twin_signs", "telling", "ending_b");

	private static final ResourceKey<LootTable> CAMP_SECRET_CHEST = ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.withDefaultNamespace("chests/abandoned_camp_secret_chest"));

	private Placers() {
	}

	static boolean isPlacedRule(String rule) {
		return !NOT_PLACED.contains(rule);
	}

	static Optional<Result> place(Request req) {
		return switch (req.placement().rule()) {
			case "ruined_hut" -> ruinedHut(req);
			case "hut_or_tunnel" -> hutOrTunnel(req);
			case "cave_chest" -> caveChest(req);
			case "tunnel_end_chest" -> tunnelEndChest(req);
			case "tunnel_end_sign" -> tunnelEndSign(req);
			case "pyramid_core" -> pyramid(req, Content.SIGN);
			case "pyramid_chest" -> pyramid(req, Content.CHEST);
			case "cairn" -> pyramid(req, Content.FIRST_BLOCK);
			case "grove_burial" -> groveBurial(req);
			case "visited_grove_burial" -> visitedGroveBurial(req);
			case "first_table" -> firstTable(req);
			case "white_eyes_room" -> whiteEyesRoom(req);
			case "under_base_chest" -> underBaseChest(req);
			case "below_spawn" -> belowSpawn(req);
			case "test_room" -> testRoom(req);
			case "test_room_chest" -> roomPart(req, Room::notesChest, false);
			case "test_room_loft" -> roomPart(req, Room::loftChest, false);
			case "test_room_below" -> roomPart(req, Room::belowChest, true);
			case "emptied_house" -> emptiedHouse(req);
			case "panic_tower" -> panicTower(req);
			case "cross_sign" -> crossSign(req);
			case "stair_bottom" -> stairBottom(req);
			case "restored_tree" -> restoredTree(req);
			case "house_copy" -> houseCopy(req);
			case "camp_map" -> campMap(req);
			case "twin_signs" -> twinSigns(req);
			case "telling", "ending_b" -> Optional.empty();
			default -> {
				A1016_02.LOGGER.warn("[a1016] lore: {} has unknown placement rule {}", req.id(), req.placement().rule());
				yield Optional.empty();
			}
		};
	}

	// --- shared helpers ---

	private static ItemStack book(Request req) {
		return FragmentItems.book(req.fragment(), req.playerName());
	}

	private static List<ItemStack> contents(Request req) {
		ItemStack stack = req.fragment().isBook() ? book(req) : FragmentItems.item(req.fragment());
		return List.of(stack);
	}

	static BlockState standingSign(Direction facing) {
		return Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing));
	}

	static BlockState wallSign(Direction facing) {
		return Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
	}

	private static Build left(Request req) {
		return Build.left(req.traces(), req.level(), req.id());
	}

	private static Build his(Request req) {
		return Build.his(req.traces(), req.level(), req.id());
	}

	/** True if the chunks within {@code radius} of {@code center} are loaded; otherwise asks for them (see {@link ChunkGate}). */
	private static boolean ready(Request req, BlockPos center, int radius) {
		return ChunkGate.ready(req, center, radius);
	}

	private static Builders.Area area(Request req) {
		return (a, b) -> ChunkGate.ready(req, a, b, 2);
	}

	/**
	 * Unclaimed sites of this type in the request's distance band, picked by the placement's {@code pick}, without
	 * the one reserved for the placement's {@code not_with} fragment (F06's longest tunnel, F07's largest pyramid).
	 */
	static List<Site> sites(Request req, SiteType type) {
		List<Site> found = new ArrayList<>(pick(type, req.origin(), req.level(), Math.min(req.minDistance(), req.placement().siteMinDistance()),
				req.maxDistance(), req.placement().pick()));
		req.placement().notWith().flatMap(other -> reservedFor(req, other, type)).ifPresent(reserved -> found.removeIf(s -> s.id() == reserved.id()));
		return found;
	}

	private static List<Site> pick(SiteType type, BlockPos origin, ServerLevel level, int min, int max, String pick) {
		double minSqr = (double) min * min;
		List<Site> found = new ArrayList<>(Services.sites().findUnclaimed(type, GlobalPos.of(level.dimension(), origin), max).stream()
				.filter(s -> Builders.horizontalDistSqr(s.pos(), origin) >= minSqr).toList());
		if (pick.equals("longest") || pick.equals("largest")) {
			found.sort(Comparator.comparingInt(Site::size).reversed());
		}
		return found;
	}

	/** The site another fragment would take, while it is enabled and not placed yet. */
	static Optional<Site> reservedFor(Request req, String otherId, SiteType type) {
		if (!req.facts().enabled(otherId) || req.facts().placed(otherId).isPresent()) {
			return Optional.empty();
		}
		return FragmentData.get(otherId).map(Fragment::placement).filter(p -> p.site().equals(Optional.of(type))).flatMap(p -> {
			BlockPos origin = p.fromSpawn() ? req.facts().spawn() : req.facts().base();
			return pick(type, origin, req.level(), Math.min(p.minDistance(), p.siteMinDistance()), p.maxDistance(), p.pick()).stream().findFirst();
		});
	}

	private static Result claim(Result result, Site site, String id) {
		Services.sites().claim(site, id);
		return result.site(site);
	}

	/** Records a place lore built itself, claimed by this fragment. */
	private static Site ownSite(Request req, SiteType type, BlockPos pos, int size, boolean claim) {
		Site site = Services.sites().record(type, req.level().dimension(), pos, size);
		LoreData.get(req.level().getServer()).addOwnSite(site.id());
		if (claim) {
			Services.sites().claim(site, req.id());
		}
		A1016_02.LOGGER.info("[a1016] lore: built a {} for {} at {}", type, req.id(), pos.toShortString());
		return site;
	}

	/**
	 * Own builds for a site kind that world or dig record wait until {@link Request#ownBuilds()}: their site may
	 * just not be generated yet, and the world should not get a second hut or pyramid.
	 */
	private static boolean mayBuild(Request req) {
		return req.ownBuilds() || req.placement().site().isEmpty();
	}

	private static List<BlockPos> candidates(Request req) {
		if (!mayBuild(req)) {
			return List.of();
		}
		return Terrain.candidates(req.level(), req.origin(), req.minDistance(), req.maxDistance(), Math.max(1, req.tries()), req.random());
	}

	/** The container at the site itself (world's huts: the site is the chest), else one inside the site's area. */
	private static Optional<BlockPos> containerIn(ServerLevel level, BlockPos center, int size) {
		if (level.getBlockEntity(center) instanceof Container) {
			return Optional.of(center);
		}
		int r = Math.max(2, size);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 4, r))) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof Container && blockEntity.getBlockState().getBlock() instanceof ChestBlock) {
				return Optional.of(pos.immutable());
			}
		}
		return Optional.empty();
	}

	/** Leaves the fragment's chest on a free floor spot near a site (whose chunks are loaded). */
	private static Optional<Result> chestNear(Request req, Site site, int radius) {
		if (!ready(req, site.pos(), radius + 1)) {
			return Optional.empty();
		}
		Optional<BlockPos> spot = Terrain.floorNear(req.level(), site.pos(), radius, 2, List.of());
		if (spot.isEmpty()) {
			return Optional.empty();
		}
		Direction facing = Terrain.openSide(req.level(), spot.get(), Direction.NORTH);
		if (!left(req).chest(spot.get(), facing, contents(req)).commit()) {
			return Optional.empty();
		}
		return Optional.of(claim(new Result(spot.get()), site, req.id()));
	}

	// --- F01 ruined hut, F02 same hut or a tunnel end ---

	private static Optional<Result> ruinedHut(Request req) {
		for (Site site : sites(req, SiteType.RUINED_HUT)) {
			if (!ready(req, site.pos(), Math.max(2, site.size()) + 1)) {
				return Optional.empty();
			}
			Optional<BlockPos> container = containerIn(req.level(), site.pos(), site.size());
			if (container.isPresent()) {
				return Build.insert(req.traces(), req.level(), container.get(), book(req), req.id())
						? Optional.of(claim(new Result(container.get()), site, req.id())) : Optional.empty();
			}
			return chestNear(req, site, Math.max(2, site.size()));
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 3)) {
				return Optional.empty();
			}
			Optional<House> hut = Builders.planRuinedHut(req.level(), column, req.random());
			if (hut.isEmpty() || hut.get().inside().isEmpty()) {
				continue;
			}
			BlockPos chest = hut.get().inside().get(req.random().nextInt(hut.get().inside().size()));
			Build build = left(req);
			Builders.place(build, hut.get().pieces());
			build.chest(chest, hut.get().door(), contents(req));
			if (build.commit()) {
				return Optional.of(new Result(chest).site(ownSite(req, SiteType.RUINED_HUT, hut.get().center(), 3, true)));
			}
		}
		return Optional.empty();
	}

	private static Optional<Result> hutOrTunnel(Request req) {
		Optional<GlobalPos> hut = req.placement().sameAs().flatMap(req.facts()::placed)
				.filter(p -> p.dimension().equals(req.level().dimension()));
		if (hut.isPresent()) {
			BlockPos pos = hut.get().pos();
			if (!ready(req, pos, 4)) {
				return Optional.empty();
			}
			if (req.level().getBlockEntity(pos) instanceof Container) {
				return Build.insert(req.traces(), req.level(), pos, book(req), req.id()) ? Optional.of(new Result(pos)) : Optional.empty();
			}
			Optional<BlockPos> spot = Terrain.floorNear(req.level(), pos, 3, 2, List.of());
			if (spot.isPresent() && left(req).chest(spot.get(), Terrain.openSide(req.level(), spot.get(), Direction.NORTH), contents(req)).commit()) {
				return Optional.of(new Result(spot.get()));
			}
			return Optional.empty();
		}
		return tunnelEndChest(req);
	}

	// --- F05 cave chest under a single torch ---

	private static Optional<Result> caveChest(Request req) {
		ServerLevel level = req.level();
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 10)) {
				return Optional.empty();
			}
			for (int n = 0; n < 4; n++) {
				int x = column.getX() + req.random().nextInt(16) - 8;
				int z = column.getZ() + req.random().nextInt(16) - 8;
				Optional<BlockPos> floor = Terrain.caveFloor(level, x, z, 40, level.getMinY() + 8);
				if (floor.isEmpty() || !Terrain.isDark(level, floor.get())) {
					continue;
				}
				BlockPos chest = floor.get();
				for (Direction wallSide : Terrain.shuffledHorizontal(req.random())) {
					BlockPos torch = chest.above();
					BlockPos wall = torch.relative(wallSide);
					if (!level.getBlockState(wall).isFaceSturdy(level, wall, wallSide.getOpposite())
							|| !level.getBlockState(chest.relative(wallSide)).isSolidRender()) {
						continue;
					}
					BlockState torchState = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, wallSide.getOpposite());
					if (left(req).chest(chest, wallSide.getOpposite(), contents(req)).leave(torch, torchState).commit()) {
						return Optional.of(new Result(chest));
					}
				}
			}
		}
		return Optional.empty();
	}

	// --- tunnel ends: F06 (the longest), F02, F08, F18 ---

	private static int length(Request req) {
		int min = req.placement().lengthMin();
		int max = Math.max(min, req.placement().lengthMax());
		return min + req.random().nextInt(max - min + 1);
	}

	private static Optional<Result> tunnelEndChest(Request req) {
		for (Site site : sites(req, SiteType.TUNNEL_END)) {
			return chestNear(req, site, 2);
		}
		for (BlockPos column : candidates(req)) {
			Optional<Tunnel> tunnel = Builders.findTunnel(req.level(), column, length(req), req.random(), area(req));
			if (req.loads().waiting()) {
				return Optional.empty();
			}
			if (tunnel.isEmpty()) {
				continue;
			}
			Build build = his(req);
			Builders.carve(build, tunnel.get());
			build.chest(tunnel.get().end(), tunnel.get().dir().getOpposite(), contents(req));
			if (build.commit()) {
				Site site = ownSite(req, SiteType.TUNNEL_END, tunnel.get().end(), tunnel.get().length(), true);
				return Optional.of(new Result(tunnel.get().end()).site(site));
			}
		}
		return Optional.empty();
	}

	private static Optional<Result> tunnelEndSign(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.TUNNEL_END)) {
			if (!ready(req, site.pos(), 3)) {
				return Optional.empty();
			}
			Optional<Pair<BlockPos, Direction>> face = stoneFace(level, site.pos());
			if (face.isEmpty()) {
				continue;
			}
			BlockPos at = face.get().getFirst();
			if (!left(req).sign(at, wallSign(face.get().getSecond()), FragmentItems.signText(req.fragment(), req.playerName())).commit()) {
				return Optional.empty();
			}
			return Optional.of(claim(new Result(at).read(at), site, req.id()));
		}
		for (BlockPos column : candidates(req)) {
			Optional<Tunnel> tunnel = Builders.findTunnel(level, column, length(req), req.random(), area(req));
			if (req.loads().waiting()) {
				return Optional.empty();
			}
			if (tunnel.isEmpty()) {
				continue;
			}
			BlockPos at = tunnel.get().endUpper();
			Build build = his(req);
			Builders.carve(build, tunnel.get());
			build.sign(at, wallSign(tunnel.get().dir().getOpposite()), FragmentItems.signText(req.fragment(), req.playerName()));
			if (build.commit()) {
				Site site = ownSite(req, SiteType.TUNNEL_END, tunnel.get().end(), tunnel.get().length(), true);
				return Optional.of(new Result(at).read(at).site(site));
			}
		}
		return Optional.empty();
	}

	/** At a tunnel end: an open cell with a stone face on one side and open tunnel on the other. Upper cells first. */
	private static Optional<Pair<BlockPos, Direction>> stoneFace(ServerLevel level, BlockPos end) {
		for (int dy = 1; dy >= 0; dy--) {
			for (BlockPos pos : BlockPos.betweenClosed(end.offset(-1, dy, -1), end.offset(1, dy, 1))) {
				if (!Terrain.isAirOrReplaceable(level.getBlockState(pos)) || level.getBlockEntity(pos) != null) {
					continue;
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos wall = pos.relative(dir);
					if (level.getBlockState(wall).isSolid() && level.getBlockState(wall).isSolidRender()
							&& Terrain.isAirOrReplaceable(level.getBlockState(pos.relative(dir.getOpposite())))) {
						return Optional.of(Pair.of(pos.immutable(), dir.getOpposite()));
					}
				}
			}
		}
		return Optional.empty();
	}

	// --- ocean pyramids: F07 the seed sign, F19 the chest, F13 the cairn ---

	private enum Content { SIGN, CHEST, FIRST_BLOCK }

	private static Optional<Result> pyramid(Request req, Content content) {
		ServerLevel level = req.level();
		Optional<PlacedBlock> first = Optional.empty();
		if (content == Content.FIRST_BLOCK) {
			first = firstBlock(req);
			if (first.isEmpty()) {
				return Optional.empty();
			}
		}
		for (Site site : sites(req, SiteType.OCEAN_PYRAMID)) {
			BlockPos core = site.pos();
			if (!ready(req, core, 1)) {
				return Optional.empty();
			}
			BlockState state = level.getBlockState(core);
			boolean solid = !state.canBeReplaced();
			if (level.getBlockEntity(core) != null || solid && !Terrain.isNaturalSolid(state)) {
				continue;
			}
			Build build = content == Content.FIRST_BLOCK ? his(req) : left(req);
			if (solid) {
				// Only the largest pyramid has an air pocket; in the others the core sand is taken out for it.
				build.remove(core);
			}
			fillCore(req, build, core, content, first);
			if (!build.commit()) {
				return Optional.empty();
			}
			return Optional.of(claim(coreResult(core, content, first), site, req.id()));
		}
		if (!mayBuild(req)) {
			return Optional.empty();
		}
		Optional<BlockPos> ocean = nearestOcean(req);
		if (ocean.isEmpty()) {
			return Optional.empty();
		}
		for (BlockPos column : Terrain.candidates(level, ocean.get(), 0, 48, Math.max(1, req.tries()), req.random())) {
			if (!ready(req, column, 14)) {
				return Optional.empty();
			}
			Optional<Pyramid> pyramid = Builders.planPyramid(level, column);
			if (pyramid.isEmpty()) {
				continue;
			}
			// One batch: his moved sand, the core emptied to air, then its content (the top sand rests on it).
			Build build = his(req);
			Builders.raise(build, pyramid.get());
			BlockPos core = pyramid.get().core();
			build.convert(core, Blocks.AIR.defaultBlockState());
			fillCore(req, build, core, content, first);
			if (build.commit()) {
				Site site = ownSite(req, SiteType.OCEAN_PYRAMID, core, 2, true);
				return Optional.of(coreResult(core, content, first).site(site));
			}
		}
		return Optional.empty();
	}

	private static void fillCore(Request req, Build build, BlockPos core, Content content, Optional<PlacedBlock> first) {
		switch (content) {
			case SIGN -> build.sign(core, standingSign(Direction.Plane.HORIZONTAL.getRandomDirection(req.random())),
					FragmentItems.signText(req.fragment(), req.playerName()));
			case CHEST -> build.chest(core, Direction.Plane.HORIZONTAL.getRandomDirection(req.random()), contents(req));
			case FIRST_BLOCK -> {
				PlacedBlock block = first.orElseThrow();
				BlockPos from = block.pos().pos();
				build.move(from, core);
				BlockState moved = req.level().getBlockState(from);
				if (moved.hasProperty(ChestBlock.TYPE) && moved.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
					build.convert(core, moved.setValue(ChestBlock.TYPE, ChestType.SINGLE));
				}
			}
		}
	}

	private static Result coreResult(BlockPos core, Content content, Optional<PlacedBlock> first) {
		Result result = new Result(core);
		if (content != Content.CHEST) {
			result.read(core);
		}
		first.ifPresent(block -> result.anchor("F13/from", block.pos().pos()));
		return result;
	}

	/**
	 * The subject's first crafting table, else first chest, else first block, if it is still where it was put.
	 * Empty (and waiting) while the base's chunk is not loaded.
	 */
	static Optional<PlacedBlock> firstBlock(Request req) {
		ServerLevel level = req.level();
		HerobrineState.FirstBlocks blocks = req.facts().firstBlocks();
		for (PlacedBlock block : new PlacedBlock[] {blocks.craftingTable(), blocks.chest(), blocks.block()}) {
			if (block == null || !block.pos().dimension().equals(level.dimension())) {
				continue;
			}
			if (!ready(req, block.pos().pos(), 1)) {
				return Optional.empty();
			}
			if (level.getBlockState(block.pos().pos()).is(block.state().getBlock())) {
				return Optional.of(block);
			}
		}
		return Optional.empty();
	}

	/** The ocean nearest the origin (a biome search, no chunk loads), searched once per fragment and remembered. */
	private static Optional<BlockPos> nearestOcean(Request req) {
		String key = req.id() + "/ocean";
		Optional<GlobalPos> known = req.facts().anchor(key).filter(p -> p.dimension().equals(req.level().dimension()));
		if (known.isPresent()) {
			return known.map(GlobalPos::pos);
		}
		Pair<BlockPos, Holder<Biome>> found = req.level().findClosestBiome3d(biome -> biome.is(BiomeTags.IS_OCEAN), req.origin(),
				Math.max(64, req.maxDistance()), 32, 64);
		if (found == null) {
			return Optional.empty();
		}
		req.facts().remember(key, GlobalPos.of(req.level().dimension(), found.getFirst()));
		return Optional.of(found.getFirst());
	}

	// --- groves: F09 under the center tree, F17 under the first grove visited ---

	private static Optional<Result> burial(Request req, BlockPos trunk) {
		Optional<BlockPos> spot = Builders.burialUnder(req.level(), trunk);
		if (spot.isEmpty()) {
			return Optional.empty();
		}
		if (!left(req).remove(spot.get()).chest(spot.get(), Direction.NORTH, contents(req)).commit()) {
			return Optional.empty();
		}
		return Optional.of(new Result(spot.get()));
	}

	private static Optional<Result> groveBurial(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.BARE_GROVE)) {
			int radius = Math.max(3, Math.min(site.size(), 8));
			if (!ready(req, site.pos(), radius + 1)) {
				return Optional.empty();
			}
			for (BlockPos trunk : Builders.trunks(level, site.pos(), radius)) {
				Optional<Result> result = burial(req, trunk);
				if (result.isPresent()) {
					return Optional.of(claim(result.get(), site, req.id()));
				}
			}
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 12)) {
				return Optional.empty();
			}
			List<BlockPos> trunks = Builders.trunks(level, column, 6);
			if (trunks.size() < 3 || Builders.burialUnder(level, trunks.getFirst()).isEmpty()) {
				continue;
			}
			Build strip = his(req);
			int leaves = 0;
			for (BlockPos trunk : trunks.subList(0, Math.min(5, trunks.size()))) {
				for (BlockPos leaf : Builders.leavesOf(level, trunk, 200)) {
					strip.remove(leaf);
					leaves++;
				}
			}
			if (leaves == 0 || !strip.commit()) {
				continue;
			}
			Site site = ownSite(req, SiteType.BARE_GROVE, trunks.getFirst(), 6, false);
			Optional<Result> result = burial(req, trunks.getFirst());
			return result.map(r -> claim(r, site, req.id()));
		}
		return Optional.empty();
	}

	private static Optional<Result> visitedGroveBurial(Request req) {
		ServerLevel level = req.level();
		GlobalPos origin = GlobalPos.of(level.dimension(), req.origin());
		List<Site> visited = new ArrayList<>(Services.sites().find(SiteType.BARE_GROVE, origin, req.maxDistance()).stream()
				.filter(s -> Services.watch().lastVisitDay(level, ChunkPos.containing(s.pos())) >= 0).toList());
		visited.sort(Comparator.comparingLong(s -> Services.watch().lastVisitDay(level, ChunkPos.containing(s.pos()))));
		Optional<BlockPos> taken = req.facts().placed("F09").map(GlobalPos::pos);
		for (Site site : visited) {
			int radius = Math.max(3, Math.min(site.size(), 16));
			if (!ready(req, site.pos(), radius + 1)) {
				return Optional.empty();
			}
			List<BlockPos> trunks = new ArrayList<>(Builders.trunks(level, site.pos(), radius));
			trunks.sort(Comparator.comparingDouble(t -> -Builders.horizontalDistSqr(t, site.pos())));
			for (BlockPos trunk : trunks) {
				if (taken.isPresent() && taken.get().equals(trunk.below(2))) {
					continue;
				}
				Optional<Result> result = burial(req, trunk);
				if (result.isPresent()) {
					return result;
				}
			}
		}
		return Optional.empty();
	}

	// --- F10 where the first crafting table stood ---

	private static Optional<Result> firstTable(Request req) {
		ServerLevel level = req.level();
		HerobrineState.FirstBlocks blocks = req.facts().firstBlocks();
		BlockPos target = req.facts().base();
		for (PlacedBlock block : new PlacedBlock[] {blocks.craftingTable(), blocks.chest(), blocks.block()}) {
			if (block != null && block.pos().dimension().equals(level.dimension())) {
				target = block.pos().pos();
				break;
			}
		}
		if (!ready(req, target, 3)) {
			return Optional.empty();
		}
		BlockPos spot = Terrain.isFloor(level, target) ? target : Terrain.floorNear(level, target, 2, 1, List.of()).orElse(null);
		if (spot == null || !left(req).chest(spot, Terrain.openSide(level, spot, Direction.SOUTH), contents(req)).commit()) {
			return Optional.empty();
		}
		return Optional.of(new Result(spot));
	}

	// --- F11 the white eyes room ---

	private static Optional<Result> whiteEyesRoom(Request req) {
		ServerLevel level = req.level();
		int below = req.placement().y().orElse(40);
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 11)) {
				return Optional.empty();
			}
			for (int n = 0; n < 6; n++) {
				int x = column.getX() + req.random().nextInt(16) - 8;
				int z = column.getZ() + req.random().nextInt(16) - 8;
				Optional<BlockPos> floor = Terrain.caveFloor(level, x, z, below - 1, level.getMinY() + 8);
				if (floor.isEmpty() || !Terrain.isDark(level, floor.get()) || roomFloor(level, floor.get()) < 10) {
					continue;
				}
				BlockPos jukebox = floor.get();
				for (Direction dir : Terrain.shuffledHorizontal(req.random())) {
					BlockPos sign = jukebox.relative(dir);
					if (!Terrain.isFloor(level, sign) || !level.getFluidState(sign).isEmpty()) {
						continue;
					}
					ItemStack disc = FragmentItems.item(req.fragment());
					Build build = left(req).jukebox(jukebox, disc).sign(sign, standingSign(dir), FragmentItems.signText(req.fragment(), req.playerName()));
					if (build.commit()) {
						return Optional.of(new Result(jukebox).read(sign).anchor("F11/sign", sign));
					}
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	/** Floor cells around a cave spot (5x5, same height): how roomy the cave is there. */
	private static int roomFloor(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
			if (Terrain.isFloor(level, pos) && Terrain.isAirOrReplaceable(level.getBlockState(pos.above()))) {
				count++;
			}
		}
		return count;
	}

	// --- F12 the tunnel that ends under your base ---

	private static Optional<Result> underBaseChest(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req.withBand(req.facts().base(), 0, req.maxDistance()), SiteType.UNDER_BASE)) {
			return chestNear(req, site, 2);
		}
		if (!mayBuild(req)) {
			return Optional.empty();
		}
		BlockPos base = req.facts().base();
		int length = length(req);
		BlockPos end = new BlockPos(base.getX(), base.getY() - 12, base.getZ());
		for (Direction dir : Terrain.shuffledHorizontal(req.random())) {
			BlockPos start = end.relative(dir.getOpposite(), length - 1);
			if (!ChunkGate.ready(req, start, end.relative(dir).relative(dir.getClockWise()), 2)) {
				return Optional.empty();
			}
			Optional<Tunnel> tunnel = Builders.planTunnel(level, start, dir, length);
			if (tunnel.isEmpty()) {
				continue;
			}
			Build build = his(req);
			Builders.carve(build, tunnel.get());
			build.chest(tunnel.get().end(), dir.getOpposite(), contents(req));
			if (build.commit()) {
				Site site = ownSite(req, SiteType.UNDER_BASE, tunnel.get().end(), length, true);
				return Optional.of(new Result(tunnel.get().end()).site(site));
			}
			return Optional.empty();
		}
		return Optional.empty();
	}

	// --- F14 ten blocks straight down from world spawn ---

	private static Optional<Result> belowSpawn(Request req) {
		ServerLevel level = req.level();
		BlockPos target = req.facts().spawn().below(req.placement().y().orElse(10));
		if (!ready(req, target, 1)) {
			return Optional.empty();
		}
		BlockState state = level.getBlockState(target);
		if (level.getBlockEntity(target) != null) {
			return Optional.empty();
		}
		Build build = left(req);
		if (!state.canBeReplaced()) {
			build.remove(target);
		}
		build.chest(target, Direction.NORTH, contents(req));
		return build.commit() ? Optional.of(new Result(target)) : Optional.empty();
	}

	// --- F15 the sealed test room, and F16, F22, F29 in it ---

	private static Optional<Result> testRoom(Request req) {
		int floorY = req.placement().y().orElse(12);
		for (BlockPos column : candidates(req)) {
			BlockPos corner = new BlockPos(column.getX() - 3, floorY, column.getZ() - 3);
			if (!ChunkGate.ready(req, corner, corner.offset(6, 0, 6), 2)) {
				return Optional.empty();
			}
			Room room = new Room(corner);
			if (!Builders.roomFits(req.level(), corner)) {
				continue;
			}
			Build build = left(req);
			Builders.buildRoom(build, room);
			build.chest(room.rulesChest(), Direction.NORTH, contents(req));
			if (build.commit()) {
				return Optional.of(new Result(room.rulesChest()).anchor("F15/room", corner));
			}
		}
		return Optional.empty();
	}

	private static Optional<Result> roomPart(Request req, Function<Room, BlockPos> spot, boolean below) {
		ServerLevel level = req.level();
		Optional<GlobalPos> corner = req.facts().anchor("F15/room").filter(p -> p.dimension().equals(level.dimension()));
		if (corner.isEmpty() || !ChunkGate.ready(req, corner.get().pos(), corner.get().pos().offset(6, 0, 6), 1)) {
			return Optional.empty();
		}
		Room room = new Room(corner.get().pos());
		BlockPos chest = spot.apply(room);
		Build build = left(req);
		if (!below) {
			if (!Terrain.isAirOrReplaceable(level.getBlockState(chest))) {
				return Optional.empty();
			}
			build.chest(chest, chest.equals(room.loftChest()) ? Direction.SOUTH : Direction.NORTH, contents(req));
			return build.commit() ? Optional.of(new Result(chest)) : Optional.empty();
		}
		// F22: an empty chest under the floor with the sign on it.
		BlockPos sign = room.belowSign();
		for (BlockPos pos : List.of(chest, sign)) {
			BlockState state = level.getBlockState(pos);
			if (level.getBlockEntity(pos) != null || !state.canBeReplaced() && !Terrain.isNaturalSolid(state)) {
				return Optional.empty();
			}
			if (!state.canBeReplaced()) {
				build.remove(pos);
			}
		}
		build.chest(chest, Direction.SOUTH, List.of()).sign(sign, standingSign(Direction.SOUTH), FragmentItems.signText(req.fragment(), req.playerName()));
		return build.commit() ? Optional.of(new Result(sign).read(sign)) : Optional.empty();
	}

	// --- F21 the emptied house with the still-burning furnace ---

	/** Set when lore left F21's still-burning furnace: the world's one "still burning" moment (D-004). */
	static final String STILL_BURNING = "lore:still_burning";

	private static Optional<Result> emptiedHouse(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.EMPTIED_HOUSE)) {
			int r = Math.max(3, site.size());
			if (!ready(req, site.pos(), r + 1)) {
				return Optional.empty();
			}
			for (BlockPos pos : BlockPos.betweenClosed(site.pos().offset(-r, -2, -r), site.pos().offset(r, 3, r))) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
					for (Direction dir : Direction.Plane.HORIZONTAL) {
						BlockPos spot = pos.relative(dir);
						if (Terrain.isFloor(level, spot)) {
							if (!left(req).chest(spot, dir, contents(req)).commit()) {
								return Optional.empty();
							}
							return Optional.of(claim(new Result(spot), site, req.id()));
						}
					}
				}
			}
			Optional<BlockPos> furnace = Terrain.floorNear(level, site.pos(), 2, 1, List.of());
			if (furnace.isEmpty()) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos chest = furnace.get().relative(dir);
				if (!Terrain.isFloor(level, chest)) {
					continue;
				}
				Build build = left(req);
				stillBurning(build, furnace.get(), dir.getOpposite());
				build.chest(chest, dir.getClockWise(), contents(req));
				if (!build.commit()) {
					return Optional.empty();
				}
				HerobrineState.get(level.getServer()).setFlag(STILL_BURNING, true);
				return Optional.of(claim(new Result(chest).anchor("F21/furnace", furnace.get()), site, req.id()));
			}
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 3)) {
				return Optional.empty();
			}
			Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(req.random());
			Optional<House> house = Builders.planHouse(level, column, door, false);
			if (house.isEmpty()) {
				continue;
			}
			BlockPos center = house.get().center();
			BlockPos furnace = center.relative(door.getOpposite());
			BlockPos chest = furnace.relative(door.getClockWise());
			Build build = left(req);
			Builders.place(build, house.get().pieces());
			stillBurning(build, furnace, door);
			build.chest(chest, door, contents(req));
			if (build.commit()) {
				HerobrineState.get(level.getServer()).setFlag(STILL_BURNING, true);
				Site site = ownSite(req, SiteType.EMPTIED_HOUSE, center, 3, true);
				return Optional.of(new Result(chest).site(site).anchor("F21/furnace", furnace));
			}
		}
		return Optional.empty();
	}

	/** A lit furnace with a little left to smelt; it lights again from its fuel when the player comes near. */
	private static void stillBurning(Build build, BlockPos furnace, Direction facing) {
		build.furnace(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing).setValue(AbstractFurnaceBlock.LIT, true),
				List.of(new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.COAL, 1)));
	}

	// --- F23 top of a panic tower ---

	private static Optional<Result> panicTower(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.PANIC_TOWER)) {
			if (!ready(req, site.pos(), 1)) {
				return Optional.empty();
			}
			BlockPos top = Terrain.isAirOrReplaceable(level.getBlockState(site.pos())) && level.getBlockEntity(site.pos()) == null
					? site.pos() : Terrain.ground(level, site.pos().getX(), site.pos().getZ()).above();
			if (!Terrain.isAirOrReplaceable(level.getBlockState(top))) {
				continue;
			}
			if (!left(req).chest(top, Direction.Plane.HORIZONTAL.getRandomDirection(req.random()), contents(req)).commit()) {
				return Optional.empty();
			}
			return Optional.of(claim(new Result(top), site, req.id()));
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 1)) {
				return Optional.empty();
			}
			int height = 12 + req.random().nextInt(5);
			Optional<List<Builders.Piece>> tower = Builders.planPanicTower(level, column, height);
			if (tower.isEmpty()) {
				continue;
			}
			BlockPos top = tower.get().getLast().pos().above();
			Build build = left(req);
			Builders.place(build, tower.get());
			build.chest(top, Direction.Plane.HORIZONTAL.getRandomDirection(req.random()), contents(req));
			if (build.commit()) {
				Site site = ownSite(req, SiteType.PANIC_TOWER, tower.get().getFirst().pos(), height, true);
				return Optional.of(new Result(top).site(site));
			}
		}
		return Optional.empty();
	}

	// --- F24 a sign by a cross outside a small finished house; F26 a sign on a restored tree ---

	private static Optional<Result> crossSign(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.CROSS)) {
			if (!ready(req, site.pos(), 3)) {
				return Optional.empty();
			}
			Optional<BlockPos> spot = Terrain.floorNear(level, site.pos(), 2, 1, List.of(site.pos()));
			if (spot.isEmpty()) {
				continue;
			}
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(req.random());
			if (!left(req).sign(spot.get(), standingSign(facing), FragmentItems.signText(req.fragment(), req.playerName())).commit()) {
				return Optional.empty();
			}
			return Optional.of(claim(new Result(spot.get()).read(spot.get()), site, req.id()));
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 13)) {
				return Optional.empty();
			}
			Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(req.random());
			Optional<House> house = Builders.planHouse(level, column, door, true);
			if (house.isEmpty()) {
				continue;
			}
			BlockPos crossColumn = house.get().center().relative(door, 5);
			BlockPos crossBase = Terrain.ground(level, crossColumn.getX(), crossColumn.getZ()).above();
			BlockPos houseCenter = house.get().center();
			Optional<List<Move>> cross = Builders.planCross(level, crossBase, door.getClockWise(),
					pos -> Math.abs(pos.getX() - houseCenter.getX()) <= 3 && Math.abs(pos.getZ() - houseCenter.getZ()) <= 3);
			BlockPos sign = crossBase.relative(door);
			if (cross.isEmpty() || !Terrain.isFloor(level, sign)) {
				continue;
			}
			Build crossBuild = his(req);
			cross.get().forEach(move -> crossBuild.move(move.from(), move.to()));
			if (!crossBuild.commit()) {
				continue;
			}
			Site site = ownSite(req, SiteType.CROSS, crossBase, 1, false);
			Build build = left(req);
			Builders.place(build, house.get().pieces());
			build.sign(sign, standingSign(door), FragmentItems.signText(req.fragment(), req.playerName()));
			if (!build.commit()) {
				return Optional.empty();
			}
			return Optional.of(claim(new Result(sign).read(sign), site, req.id()));
		}
		return Optional.empty();
	}

	private static Optional<Result> restoredTree(Request req) {
		ServerLevel level = req.level();
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 14)) {
				return Optional.empty();
			}
			for (BlockPos trunk : Builders.trunks(level, column, 6)) {
				if (Builders.leavesOf(level, trunk, 12).size() < 10) {
					continue;
				}
				for (Direction side : Terrain.shuffledHorizontal(req.random())) {
					BlockPos sign = trunk.above().relative(side);
					BlockPos crossColumn = trunk.relative(side, 2);
					BlockPos crossBase = Terrain.ground(level, crossColumn.getX(), crossColumn.getZ()).above();
					if (!Terrain.isAirOrReplaceable(level.getBlockState(sign)) || Math.abs(crossBase.getY() - trunk.getY()) > 1) {
						continue;
					}
					Optional<List<Move>> cross = Builders.planCross(level, crossBase, side.getClockWise(),
							pos -> Math.abs(pos.getX() - trunk.getX()) <= 1 && Math.abs(pos.getZ() - trunk.getZ()) <= 1);
					if (cross.isEmpty()) {
						continue;
					}
					Build crossBuild = his(req);
					cross.get().forEach(move -> crossBuild.move(move.from(), move.to()));
					if (!crossBuild.commit()) {
						return Optional.empty();
					}
					ownSite(req, SiteType.CROSS, crossBase, 1, true);
					if (!left(req).sign(sign, wallSign(side), FragmentItems.signText(req.fragment(), req.playerName())).commit()) {
						return Optional.empty();
					}
					return Optional.of(new Result(sign).read(sign).anchor("F26/cross", crossBase));
				}
			}
		}
		return Optional.empty();
	}

	// --- F25 the bottom of a stair to bedrock ---

	private static Optional<Result> stairBottom(Request req) {
		ServerLevel level = req.level();
		for (Site site : sites(req, SiteType.STAIR_BOTTOM)) {
			return chestNear(req, site, 2);
		}
		for (BlockPos column : candidates(req)) {
			if (!ready(req, column, 1)) {
				return Optional.empty();
			}
			Optional<BlockPos> floor = Terrain.caveFloor(level, column.getX(), column.getZ(), level.getMinY() + 30, level.getMinY() + 6);
			if (floor.isEmpty()) {
				continue;
			}
			for (Direction dir : Terrain.shuffledHorizontal(req.random())) {
				if (!ChunkGate.ready(req, floor.get(), floor.get().relative(dir, 30), 2)) {
					return Optional.empty();
				}
				List<BlockPos> carve = new ArrayList<>();
				BlockPos step = null;
				boolean ok = false;
				for (int k = 1; k <= 28; k++) {
					step = floor.get().relative(dir, k).below(k);
					if (level.getBlockState(step).is(Blocks.BEDROCK)) {
						break;
					}
					boolean bad = false;
					for (int h = 0; h < 3; h++) {
						BlockPos cell = step.above(h);
						BlockState state = level.getBlockState(cell);
						if (!state.isAir() && !Terrain.isNaturalSolid(state) || Terrain.touchesFluid(level, cell)) {
							bad = true;
						}
						carve.add(cell);
					}
					if (bad) {
						break;
					}
					if (level.getBlockState(step.below()).is(Blocks.BEDROCK)) {
						ok = true;
						break;
					}
				}
				if (!ok) {
					continue;
				}
				Build build = left(req);
				carve.forEach(build::remove);
				build.chest(step, dir.getOpposite(), contents(req));
				if (build.commit()) {
					Site site = ownSite(req, SiteType.STAIR_BOTTOM, step, carve.size() / 3, true);
					return Optional.of(new Result(step).site(site));
				}
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	// --- F27 inside the copy of your house (world builds it after day 20) ---

	private static Optional<Result> houseCopy(Request req) {
		for (Site site : sites(req, SiteType.HOUSE_COPY)) {
			if (!ready(req, site.pos(), Math.max(2, site.size()) + 1)) {
				return Optional.empty();
			}
			Optional<BlockPos> container = containerIn(req.level(), site.pos(), site.size());
			if (container.isPresent()) {
				return Build.insert(req.traces(), req.level(), container.get(), book(req), req.id())
						? Optional.of(claim(new Result(container.get()), site, req.id())) : Optional.empty();
			}
			return chestNear(req, site, Math.max(2, site.size()));
		}
		return Optional.empty();
	}

	// --- F28 the map in an abandoned camp's secret chest ---

	private static Optional<Result> campMap(Request req) {
		ServerLevel level = req.level();
		Optional<GlobalPos> grove = req.facts().grove();
		if (grove.isEmpty()) {
			return Optional.empty();
		}
		Optional<BlockPos> camp = CampSearch.camp(level, req.origin());
		if (camp.isEmpty()) {
			req.loads().waiting = true; // the search runs in the background; look again in a moment
			return Optional.empty();
		}
		if (!ready(req, camp.get(), 0)) {
			return Optional.empty();
		}
		LevelChunk startChunk = level.getChunkSource().getChunkNow(camp.get().getX() >> 4, camp.get().getZ() >> 4);
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<StructureStart> start = startChunk == null ? Optional.empty() : startChunk.getAllStarts().values().stream()
				.filter(s -> s.isValid() && structures.wrapAsHolder(s.getStructure()).is(CampSearch.CAMPS)).findFirst();
		if (start.isEmpty()) {
			CampSearch.forget(level);
			return Optional.empty();
		}
		BoundingBox box = start.get().getBoundingBox();
		if (!ChunkGate.ready(req, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
			return Optional.empty();
		}
		Optional<BlockPos> chest = secretChest(level, box);
		if (chest.isEmpty()) {
			CampSearch.reject(level, start.get());
			return Optional.empty();
		}
		ItemStack map = FragmentItems.map(req.fragment(), level, grove.get().pos());
		if (!Build.insert(req.traces(), level, chest.get(), map, req.id())) {
			return Optional.empty();
		}
		return Optional.of(new Result(chest.get()));
	}

	/** The still-unopened secret chest inside a camp's (loaded) bounding box. */
	static Optional<BlockPos> secretChest(ServerLevel level, BoundingBox box) {
		for (ChunkPos chunkPos : box.intersectingChunks().toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk == null) {
				continue;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (box.isInside(blockEntity.getBlockPos()) && blockEntity instanceof RandomizableContainer container
						&& CAMP_SECRET_CHEST.equals(container.getLootTable())) {
					return Optional.of(blockEntity.getBlockPos());
				}
			}
		}
		return Optional.empty();
	}

	// --- F30 the twin signs: the oldest poplar in the untouched grove, and bedrock under the seed pyramid ---

	/** Radius around the grove center searched for its oldest poplar. */
	static final int GROVE_SCAN = 24;

	private static Optional<Result> twinSigns(Request req) {
		ServerLevel level = req.level();
		Placing.Facts facts = req.facts();
		if (facts.anchor("F30/grove").isEmpty()) {
			Optional<GlobalPos> grove = facts.grove().filter(g -> g.dimension().equals(level.dimension()));
			if (grove.isEmpty() || !ready(req, grove.get().pos(), GROVE_SCAN + 1)) {
				return Optional.empty();
			}
			Optional<Pair<BlockPos, Direction>> spot = oldestPoplarSide(level, grove.get().pos());
			if (spot.isEmpty()) {
				return Optional.empty();
			}
			BlockPos at = spot.get().getFirst();
			BlockState state = level.getBlockState(at.relative(spot.get().getSecond().getOpposite())).is(BlockTags.LOGS)
					? wallSign(spot.get().getSecond()) : standingSign(spot.get().getSecond());
			if (!left(req).sign(at, state, FragmentItems.signText(req.fragment(), req.playerName()), true).then(l -> seal(l, at)).commit()) {
				return Optional.empty();
			}
			facts.remember("F30/grove", GlobalPos.of(level.dimension(), at));
		}
		if (facts.anchor("F30/bedrock").isEmpty()) {
			Optional<GlobalPos> seed = facts.placed("F07").filter(p -> p.dimension().equals(level.dimension()));
			if (seed.isEmpty() || !ready(req, seed.get().pos(), 1)) {
				return Optional.empty();
			}
			Optional<BlockPos> top = bedrockTop(level, seed.get().pos());
			if (top.isEmpty()) {
				return Optional.empty();
			}
			BlockPos at = top.get().above();
			if (level.getBlockEntity(at) != null) {
				return Optional.empty();
			}
			Build build = left(req);
			if (!level.getBlockState(at).canBeReplaced()) {
				build.remove(at);
			}
			build.sign(at, standingSign(Direction.NORTH), FragmentItems.signText(req.fragment(), req.playerName()), true).then(l -> seal(l, at));
			if (!build.commit()) {
				return Optional.empty();
			}
			facts.remember("F30/bedrock", GlobalPos.of(level.dimension(), at));
		}
		BlockPos groveSign = facts.anchor("F30/grove").orElseThrow().pos();
		BlockPos bedrockSign = facts.anchor("F30/bedrock").orElseThrow().pos();
		return Optional.of(new Result(groveSign).read(groveSign).read(bedrockSign));
	}

	/** Makes a placed F30 sign (left waxed: no editing) permanent: protected, see {@link UnbreakableSigns}. */
	private static void seal(ServerLevel level, BlockPos pos) {
		UnbreakableSigns.protect(level, pos);
	}

	/** The tallest poplar trunk near the grove center and a free side of it (any tree if there is no poplar). */
	private static Optional<Pair<BlockPos, Direction>> oldestPoplarSide(ServerLevel level, BlockPos center) {
		BlockPos best = null;
		int bestHeight = 0;
		boolean bestPoplar = false;
		for (BlockPos trunk : Builders.trunks(level, center, GROVE_SCAN)) {
			boolean poplar = level.getBlockState(trunk).is(Blocks.POPLAR_LOG);
			int height = 0;
			while (level.getBlockState(trunk.above(height)).is(BlockTags.LOGS)) {
				height++;
			}
			if (poplar && !bestPoplar || poplar == bestPoplar && height > bestHeight) {
				best = trunk;
				bestHeight = height;
				bestPoplar = poplar;
			}
		}
		if (best != null) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos at = best.above().relative(side);
				if (Terrain.isAirOrReplaceable(level.getBlockState(at))) {
					return Optional.of(Pair.of(at, side));
				}
			}
		}
		BlockPos ground = Terrain.ground(level, center.getX(), center.getZ()).above();
		return Terrain.isFloor(level, ground) ? Optional.of(Pair.of(ground, Direction.SOUTH)) : Optional.empty();
	}

	/** The highest bedrock block in this column, near the bottom of the world. */
	private static Optional<BlockPos> bedrockTop(ServerLevel level, BlockPos column) {
		for (int y = level.getMinY() + 6; y >= level.getMinY(); y--) {
			BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
			if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
				return Optional.of(pos);
			}
		}
		return Optional.empty();
	}

	// --- debug fallback ---

	/**
	 * {@code /a1016 lore place} when the rule finds nothing near: the fragment left plainly on the ground near the
	 * request's origin, behind the viewer (still out of view): books and items in a chest, signs standing, F11's
	 * jukebox and sign, F13's first block moved there. Only loaded chunks are used.
	 */
	static Optional<Result> plain(Request req, Optional<ServerPlayer> viewer) {
		ServerLevel level = req.level();
		Fragment fragment = req.fragment();
		for (BlockPos column : Terrain.candidates(level, req.origin(), req.minDistance(), req.maxDistance(), 24, req.random())) {
			if (!ChunkGate.loaded(level, column) || !ChunkGate.loaded(level, column.offset(1, 0, 1)) || !ChunkGate.loaded(level, column.offset(-1, 0, -1))) {
				continue;
			}
			BlockPos spot = Terrain.ground(level, column.getX(), column.getZ()).above();
			Vec3 toSpot = Vec3.atCenterOf(spot).subtract(viewer.map(ServerPlayer::position).orElse(Vec3.atCenterOf(req.origin())));
			if (viewer.isPresent() && toSpot.dot(viewer.get().getLookAngle()) > 0 || !Terrain.isFloor(level, spot)
					|| !level.getFluidState(spot).isEmpty()) {
				continue;
			}
			Direction facing = Direction.getApproximateNearest(-toSpot.x, 0, -toSpot.z);
			Build build = fragment.form() == Fragment.Form.STRUCTURE && fragment.lines().isEmpty() ? his(req) : left(req);
			Result result = new Result(spot);
			switch (fragment.form()) {
				case BOOK -> build.chest(spot, facing, contents(req));
				case ITEM -> build.chest(spot, facing, List.of(FragmentItems.isMap(fragment)
						? FragmentItems.map(fragment, level, req.facts().grove().map(GlobalPos::pos).orElse(spot)) : FragmentItems.item(fragment)));
				case SIGN, ABSENCE -> {
					build.sign(spot, standingSign(facing), FragmentItems.signText(fragment, req.playerName()));
					result.read(spot);
				}
				case STRUCTURE -> {
					if (fragment.hasSign()) {
						BlockPos sign = spot.relative(facing);
						if (!Terrain.isFloor(level, sign)) {
							continue;
						}
						build.jukebox(spot, FragmentItems.item(fragment)).sign(sign, standingSign(facing), FragmentItems.signText(fragment, req.playerName()));
						result.read(sign);
					} else {
						Optional<PlacedBlock> first = firstBlock(req);
						if (first.isEmpty()) {
							return Optional.empty();
						}
						build.move(first.get().pos().pos(), spot);
						result.read(spot);
					}
				}
			}
			if (build.commit()) {
				return Optional.of(result);
			}
		}
		return Optional.empty();
	}
}
