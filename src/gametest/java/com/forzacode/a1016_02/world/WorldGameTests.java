package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.Density;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.world.gen.AreaScars;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.gen.Carves;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlan;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.EmptiedHouseCard;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.live.WorldWatch;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Game tests of the world workstream, registered in the gametest fabric.mod.json. Everything stays inside the
 * 8x8x8 test area (scar shapes are built with small sizes) and no player is added, so other tests' view checks
 * are untouched.
 */
public class WorldGameTests {
	private static final long TODAY = 10;

	private static BlockState leaves() {
		return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	// --- new scars ---

	@GameTest
	public void staleMeansVisitedAndLeftForDays(GameTestHelper helper) {
		int away = ModConfig.pacing().newScarAwayDays;
		helper.assertFalse(NewScarPlacer.stale(-1, TODAY, away), "a chunk never visited counts as stale");
		helper.assertFalse(NewScarPlacer.stale(TODAY, TODAY, away), "a chunk visited today counts as stale");
		helper.assertFalse(NewScarPlacer.stale(TODAY - away + 1, TODAY, away), "a chunk left less than newScarAwayDays ago counts as stale");
		helper.assertTrue(NewScarPlacer.stale(TODAY - away, TODAY, away), "a chunk left exactly newScarAwayDays ago is not stale");
		helper.assertTrue(NewScarPlacer.stale(0, TODAY, away), "a chunk left long ago is not stale");
		helper.succeed();
	}

	/** Sky access: the column scans start at the top of the world, and the default test ceiling would be the ground. */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void newScarOnlyInChunksLeftForDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
				helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
			}
		}
		List<BlockPos> flowers = List.of(new BlockPos(3, 2, 2), new BlockPos(5, 2, 6), new BlockPos(2, 2, 4));
		flowers.forEach(pos -> helper.setBlock(pos, Blocks.POPPY));
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(4, y, 4, Blocks.OAK_LOG);
		}
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				helper.setBlock(x, 5, z, leaves());
				if (x != 4 || z != 4) {
					helper.setBlock(x, 4, z, leaves());
				}
			}
		}
		BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
		int radius = 3;
		ChunkPos left = ChunkPos.containing(helper.absolutePos(new BlockPos(2, 1, 2)));

		// Never visited, or visited yesterday: nothing changes anywhere.
		for (long day : new long[] {-1, TODAY - 1}) {
			Predicate<ChunkPos> allowed = NewScarPlacer.staleChunks(level, (l, chunk) -> day, TODAY);
			helper.assertTrue(NewScarPlacer.collectDead(level, center, radius, center.getY(), allowed).isEmpty(),
					"a new scar planned in chunks last visited on day " + day);
		}
		// Only one chunk was left days ago: only its columns die.
		Predicate<ChunkPos> allowed = NewScarPlacer.staleChunks(level, (l, chunk) -> chunk.equals(left) ? TODAY - 5 : TODAY, TODAY);
		List<NewScarPlacer.Edit> edits = NewScarPlacer.collectDead(level, center, radius, center.getY(), allowed);
		helper.assertFalse(edits.isEmpty(), "nothing planned in the stale chunk");
		helper.assertTrue(NewScarPlacer.commit(level, edits, "test:new_scar"), "the out-of-view batch was refused");
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				BlockPos rel = new BlockPos(x, 1, z);
				boolean inCircle = (x - 4) * (x - 4) + (z - 4) * (z - 4) <= radius * radius;
				boolean stale = ChunkPos.containing(helper.absolutePos(rel)).equals(left);
				helper.assertBlockPresent(inCircle && stale ? Blocks.DIRT : Blocks.GRASS_BLOCK, rel);
			}
		}
		for (BlockPos flower : flowers) {
			boolean stale = ChunkPos.containing(helper.absolutePos(flower)).equals(left);
			helper.assertBlockPresent(stale ? Blocks.AIR : Blocks.POPPY, flower);
		}
		boolean treeStale = ChunkPos.containing(helper.absolutePos(new BlockPos(4, 2, 4))).equals(left);
		helper.assertBlockPresent(treeStale ? Blocks.AIR : Blocks.OAK_LOG, new BlockPos(4, 3, 4));
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void bareGroveKeepsItsTrunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(3, y, 3, Blocks.OAK_LOG);
		}
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				for (int y = 3; y <= 5; y++) {
					if (x != 3 || z != 3 || y == 5) {
						helper.setBlock(x, y, z, leaves());
					}
				}
			}
		}
		helper.setBlock(3, 6, 3, Blocks.SNOW);
		helper.setBlock(1, 1, 1, Blocks.LEAF_LITTER);
		List<NewScarPlacer.Edit> edits = NewScarPlacer.collectBare(level, helper.absolutePos(new BlockPos(3, 0, 3)), 3, chunk -> true);
		helper.assertTrue(NewScarPlacer.commit(level, edits, "test:bare"), "the out-of-view batch was refused");
		for (int y = 1; y <= 4; y++) {
			helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, y, 3));
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y < 8; y++) {
					BlockState state = helper.getBlockState(new BlockPos(x, y, z));
					helper.assertFalse(state.is(Blocks.OAK_LEAVES) || state.is(Blocks.SNOW) || state.is(Blocks.LEAF_LITTER),
							"left " + state + " at " + x + " " + y + " " + z);
				}
				helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, 0, z));
			}
		}
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	// --- old scars: shapes ---

	@GameTest
	public void pyramidHasAirPocketAtItsCore(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		BlockPos floor = helper.absolutePos(new BlockPos(3, 0, 3));
		Builds.Build pyramid = Builds.pyramid(floor.getX(), floor.getY(), floor.getZ(), 4);
		pyramid.blueprint().applyAll(helper.getLevel());
		Builds.pyramidPocket(pyramid.site()).applyAll(helper.getLevel());

		ServerLevel level = helper.getLevel();
		BlockPos core = pyramid.site();
		helper.assertTrue(core.equals(floor.above(2)), "the core is at " + core.toShortString() + ", the floor at " + floor.toShortString());
		helper.assertTrue(pyramid.size() == 4, "the site size is not the pyramid size");
		helper.assertTrue(level.getBlockState(core).isAir(), "no air pocket at the core");
		helper.assertTrue(level.getBlockState(core.above()).is(Blocks.SANDSTONE), "no sandstone over the pocket");
		helper.assertTrue(level.getBlockState(core.below()).is(Blocks.SAND), "no sand under the pocket");
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.assertTrue(level.getBlockState(core.relative(side)).is(Blocks.SAND), "the pocket is open to the " + side);
		}
		helper.assertTrue(level.getBlockState(floor.offset(-3, 1, -3)).is(Blocks.SAND), "no base corner");
		helper.assertTrue(level.getBlockState(floor.above(4)).is(Blocks.SAND), "no top");
		helper.assertTrue(level.getBlockState(floor.offset(-3, 2, -3)).isAir(), "the second layer is as wide as the base");

		SiteRegistry.Site site = new SiteRegistry.Site(1, SiteType.OCEAN_PYRAMID, helper.getLevel().dimension(), pyramid.site(), 4, Optional.empty());
		helper.assertTrue(WorldWatch.insidePyramid(site, floor.offset(-3, 1, 3)), "a base corner is not part of the pyramid");
		helper.assertTrue(WorldWatch.insidePyramid(site, floor.above(4)), "the top is not part of the pyramid");
		helper.assertFalse(WorldWatch.insidePyramid(site, floor.offset(-3, 2, -3)), "air beside the pyramid counts as digging into it");
		helper.assertFalse(WorldWatch.insidePyramid(site, floor.above(6)), "air above the pyramid counts as digging into it");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void abandonedBuildHasEarlyItemsAndEmptiesInsideOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
		Builds.Build build = Builds.abandonedBuild(anchor.getX(), anchor.getY(), anchor.getZ(), Direction.SOUTH, Builds.Wood.OAK, 42L);
		build.blueprint().applyAll(level);

		BlockPos chest = build.site();
		helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the site is not the chest");
		Container items = (Container) level.getBlockEntity(chest);
		helper.assertTrue(items.getItem(0).is(Items.STONE_PICKAXE) && items.getItem(0).getDamageValue() > 0, "no worn stone pickaxe");
		helper.assertTrue(items.getItem(1).is(Items.DIRT), "no dirt");
		helper.assertTrue(items.getItem(2).is(Items.WHEAT_SEEDS), "no seeds");
		helper.assertTrue(items.getItem(3).is(Items.APPLE) && items.getItem(3).getCount() <= 3, "no few apples");
		BlockState door = level.getBlockState(anchor.offset(0, 1, 2));
		helper.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.OPEN), "the door is not open");
		helper.assertTrue(!level.getBlockState(anchor.offset(0, 4, -2)).isAir() && level.getBlockState(anchor.offset(0, 4, 2)).isAir(),
				"the roof is not half a roof");

		BoundingBox interior = build.interior();
		int before = TraceLedger.get(level.getServer()).entries().size();
		helper.assertTrue(EmptiedHouseCard.empty(level, interior) > 0, "the emptying batch was refused");
		for (BlockPos pos : BlockPos.betweenClosed(interior.minX(), interior.minY(), interior.minZ(), interior.maxX(), interior.maxY(), interior.maxZ())) {
			helper.assertTrue(level.getBlockState(pos).isAir(), "left " + level.getBlockState(pos) + " inside");
		}
		helper.assertTrue(level.getBlockState(anchor.offset(0, 1, 2)).getBlock() instanceof DoorBlock, "the door went too");
		helper.assertTrue(!level.getBlockState(anchor.offset(-2, 1, -2)).isAir(), "a wall went too");
		helper.assertTrue(!level.getBlockState(anchor).isAir(), "the floor went too");
		boolean chestKept = TraceLedger.get(level.getServer()).entries().stream().skip(before)
				.anyMatch(e -> e.cause().equals(EmptiedHouseCard.CAUSE) && e.pos().pos().equals(chest) && e.blockEntity().isPresent());
		helper.assertTrue(chestKept, "the chest and its contents are not in the ledger");
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	@GameTest
	public void crossAndTowerShapes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(new BlockPos(2, 0, 2));
		Builds.Build cross = Builds.cross(ground.getX(), ground.getY(), ground.getZ(), Direction.NORTH, Builds.Wood.OAK, 7L);
		cross.blueprint().applyAll(level);
		int height = cross.size();
		helper.assertTrue(height == 3 || height == 4, "a cross " + height + " tall");
		for (int up = 1; up <= height; up++) {
			helper.assertFalse(level.getBlockState(ground.above(up)).isAir(), "a gap in the cross");
		}
		BlockPos arms = ground.above(height - 1);
		helper.assertFalse(level.getBlockState(arms.east()).isAir() || level.getBlockState(arms.west()).isAir(), "the cross has no arms");
		helper.assertTrue(level.getBlockState(ground.above(height).east()).isAir(), "the cross is wider than one block above its arms");

		BlockPos towerGround = helper.absolutePos(new BlockPos(6, -1, 6));
		Builds.Build tower = Builds.panicTower(towerGround.getX(), towerGround.getY(), towerGround.getZ(), 3L);
		helper.assertTrue(tower.size() >= 6 && tower.size() <= 12, "tower height " + tower.size());
		helper.assertTrue(tower.site().getY() == towerGround.getY() + tower.size() + 1, "the site is not the shelter on top");
		helper.succeed();
	}

	@GameTest
	public void areaColumnsEndOnSharpLines(GameTestHelper helper) {
		AreaScars.Area dead = new AreaScars.Area(ScarKind.DEAD_MOUNTAIN, 0, 0, 10, 70, false);
		helper.assertTrue(dead.contains(3, 4, 70), "a column on the line is alive");
		helper.assertFalse(dead.contains(3, 4, 69), "a column below the line died");
		helper.assertFalse(dead.contains(8, 8, 90), "a column outside the circle died");
		AreaScars.Area bare = new AreaScars.Area(ScarKind.BARE_FOREST, 0, 0, 10, Integer.MIN_VALUE, false);
		helper.assertTrue(bare.contains(6, 8, -40), "a bare forest has no level line");
		helper.assertTrue(bare.touches(new ChunkPos(0, 0), 0) && !bare.touches(new ChunkPos(3, 3), 0), "touches is wrong");
		helper.succeed();
	}

	// --- old scars: planning ---

	/** Terrain for planning tests: ground from a function, stone up to it, plains everywhere. */
	private static Terrain terrain(ServerLevel level, java.util.function.IntBinaryOperator ground) {
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		return new Terrain() {
			@Override
			public int seaLevel() {
				return 63;
			}

			@Override
			public int minY() {
				return level.getMinY();
			}

			@Override
			public int ground(int x, int z) {
				return ground.applyAsInt(x, z);
			}

			@Override
			public int surface(int x, int z) {
				return ground.applyAsInt(x, z);
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return plains;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				return y <= ground.applyAsInt(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
		};
	}

	/**
	 * Worldgen builds a small scar piece by piece: every chunk it touches must look at its cell, or a piece (and the
	 * site in it) is never built. Checks a plan of cell (0, 0) the way {@code ScarPlanner.decorate} finds plans.
	 */
	private static void assertBuiltWhole(GameTestHelper helper, ScarPlan plan, int cell) {
		helper.assertTrue(ScarPlanner.withinReach(plan.footprint(), 0, 0, cell), plan.kind().id() + " reaches past what chunks look at");
		List<ScarPlan.SiteMark> recorded = new ArrayList<>();
		plan.blueprint().box().intersectingChunks().filter(plan::touches).forEach(chunk -> {
			helper.assertTrue(ScarPlanner.seesCell(chunk, 0, 0, cell), plan.kind().id() + ": chunk " + chunk + " never builds its part");
			plan.sites().stream().filter(mark -> chunk.contains(mark.pos())).forEach(recorded::add);
		});
		helper.assertTrue(recorded.size() == plan.sites().size(), plan.kind().id() + ": only " + recorded.size() + " of " + plan.sites().size()
				+ " sites would be recorded");
	}

	@GameTest
	public void longStairsAndTunnelsAreBuiltWholeWithTheirSites(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cell = new WorldConfig().pointCellBlocks;

		// A stair starting high (Y 190) runs about 250 blocks out before it reaches the bedrock layer.
		Carves.Carve stair = Carves.stair(terrain(level, (x, z) -> 190), cell / 2, cell / 2, Direction.EAST, Carves.PathCheck.SOLID);
		helper.assertTrue(stair != null, "no stair from Y 190");
		helper.assertTrue(stair.site().getX() - cell / 2 > 240 && stair.site().getY() == level.getMinY() + 5, "the stair stops at " + stair.site());
		assertBuiltWhole(helper, new ScarPlan(ScarKind.STAIR, stair.site(), 1, stair.blueprint(), null,
				List.of(ScarPlan.SiteMark.of(SiteType.STAIR_BOTTOM, stair.site(), 1))), cell);

		// A tunnel through a 230-block ridge, with a cross at its far mouth.
		Terrain ridge = terrain(level, (x, z) -> Math.abs(x - cell / 2) <= 115 ? 140 : 70);
		Carves.Carve tunnel = Carves.tunnel(ridge, cell / 2, cell / 2, Direction.EAST, 75, 120);
		helper.assertTrue(tunnel != null && tunnel.endB().getX() - tunnel.endA().getX() > 220, "no long tunnel");
		Builds.Build cross = Builds.cross(tunnel.endB().getX() + 2, 70, tunnel.endB().getZ(), Direction.EAST, Builds.Wood.OAK, 1L);
		cross.blueprint().ops().forEach(tunnel.blueprint()::add);
		assertBuiltWhole(helper, new ScarPlan(ScarKind.TUNNEL, tunnel.site(), tunnel.size(), tunnel.blueprint(), null,
				List.of(ScarPlan.SiteMark.of(SiteType.CUT, tunnel.site(), tunnel.size()), ScarPlan.SiteMark.of(SiteType.CROSS, cross.site(), 3))), cell);

		// A stair from near the top of the world, at the edge of its cell, would reach too far: the planner drops it.
		Carves.Carve tooLong = Carves.stair(terrain(level, (x, z) -> 300), cell - 16, cell / 2, Direction.EAST, Carves.PathCheck.SOLID);
		helper.assertTrue(tooLong != null && !ScarPlanner.withinReach(tooLong.blueprint().box(), 0, 0, cell),
				"a stair reaching past the chunks that look at its cell was kept");
		helper.succeed();
	}


	private static ScarContext context(Density density) {
		WorldProfile rolled = WorldProfile.roll(0x5EED, 0x5A17);
		WorldProfile profile = new WorldProfile(rolled.habits(), density, rolled.tempo(), rolled.fragments(), rolled.signature());
		return new ScarContext(0x5EED, 0x5A17, profile, new WorldConfig(), ModConfig.pacing().oldScarMinFromSpawn);
	}

	@GameTest(maxTicks = 200)
	public void densitySetsHowManyCellsHoldAScar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int[] counts = new int[Density.values().length];
		int cells = 0;
		for (Density density : Density.values()) {
			ScarPlanner planner = context(density).planner(level);
			cells = 0;
			for (int cx = -30; cx < 30; cx++) {
				for (int cz = -30; cz < 30; cz++) {
					cells++;
					counts[density.ordinal()] += planner.pointCellRolled(cx, cz) ? 1 : 0;
				}
			}
		}
		helper.assertTrue(counts[0] < counts[1] && counts[1] < counts[2], "density does not order the scars: " + Arrays.toString(counts));
		WorldConfig config = new WorldConfig();
		for (Density density : Density.values()) {
			double expected = config.pointChance(density.ordinal()) * cells;
			helper.assertTrue(Math.abs(counts[density.ordinal()] - expected) < expected * 0.15, density + ": " + counts[density.ordinal()] + " of " + cells);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void oldScarsAreDeterministicAndFarFromSpawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ScarPlanner first = context(Density.HEAVY).planner(level);
		ScarPlanner second = context(Density.HEAVY).planner(level);
		BlockPos origin = first.origin();
		int min = ModConfig.pacing().oldScarMinFromSpawn;
		int radius = 900;
		List<BlockPos> a = new ArrayList<>();
		List<BlockPos> b = new ArrayList<>();
		for (ScarKind kind : ScarKind.values()) {
			for (ScarPlan plan : first.plansNear(kind, origin.getX(), origin.getZ(), radius)) {
				a.add(plan.anchor());
				BoundingBox box = plan.footprint();
				double dx = Math.max(0, Math.max(box.minX() - origin.getX(), origin.getX() - box.maxX()));
				double dz = Math.max(0, Math.max(box.minZ() - origin.getZ(), origin.getZ() - box.maxZ()));
				helper.assertTrue(Math.sqrt(dx * dx + dz * dz) >= min, kind.id() + " at " + plan.anchor().toShortString() + " is too close to spawn");
			}
			second.plansNear(kind, origin.getX(), origin.getZ(), radius).forEach(plan -> b.add(plan.anchor()));
		}
		helper.assertTrue(a.equals(b), "two planners with the same seed and salt disagree");
		Optional<ScarPlan> hut = first.ruinedHut();
		if (hut.isPresent()) {
			double d = Math.sqrt(hut.get().anchor().distSqr(new BlockPos(origin.getX(), hut.get().anchor().getY(), origin.getZ())));
			helper.assertTrue(d >= 300 && d <= 800, "the ruined hut is " + d + " blocks from spawn");
		}
		helper.succeed();
	}
}
