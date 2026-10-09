package com.forzacode.a1016_02.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.world.gen.Blueprint;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlan;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.LivePlacer;
import com.forzacode.a1016_02.world.sig.CrossRow;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Game tests of the crosses: the Latin shape (D-050) and the glass memorials left by others (D-051). The row of
 * crosses (moved material, never glass) is tested in {@link WorldSignatureTests#crossRowIsMovedMaterial}.
 */
public class WorldCrossTests {
	/** Terrain for planning tests: ground from a function, stone up to it, plains everywhere. */
	static Terrain terrain(ServerLevel level, IntBinaryOperator ground) {
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

	/** The blocks a blueprint puts, by position. */
	static Map<BlockPos, BlockState> puts(Blueprint blueprint) {
		Map<BlockPos, BlockState> puts = new HashMap<>();
		for (Blueprint.Op op : blueprint.ops()) {
			if (op instanceof Blueprint.Put put) {
				puts.put(put.pos(), put.state());
			}
		}
		return puts;
	}

	private static boolean anyGlass(Blueprint blueprint) {
		return puts(blueprint).values().stream().anyMatch(s -> s.is(Blocks.GLASS));
	}

	// --- the shape (D-050) ---

	@GameTest
	public void latinCrossCellsForBothHeights(GameTestHelper helper) {
		BlockPos base = new BlockPos(100, 70, -40);
		List<BlockPos> five = Builds.latinCross(base, 5, Direction.Axis.X);
		helper.assertTrue(five.equals(List.of(base, base.above(1), base.above(2), base.above(3), base.above(4), base.above(3).west(), base.above(3).east())),
				"a 5-tall cross is not stem 5, arms on the 4th block: " + five);
		List<BlockPos> six = Builds.latinCross(base, 6, Direction.Axis.Z);
		helper.assertTrue(six.equals(List.of(base, base.above(1), base.above(2), base.above(3), base.above(4), base.above(5), base.above(4).north(),
				base.above(4).south())), "a 6-tall cross is not stem 6, arms on the 5th block: " + six);

		// Every worldgen cross is exactly those cells: his in cobblestone or wood, the memorial in glass.
		Set<String> seen = new HashSet<>();
		BlockPos stemBase = new BlockPos(0, 65, 0);
		for (long seed = 0; seed < 64; seed++) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				Direction.Axis armAxis = facing.getClockWise().getAxis();
				Builds.Build his = Builds.cross(0, 64, 0, facing, Builds.Wood.SPRUCE, seed);
				Builds.Build glass = Builds.glassCross(0, 64, 0, facing, seed);
				helper.assertTrue(his.size() > 0 && glass.size() < 0, "site sizes " + his.size() + " and " + glass.size());
				for (Builds.Build build : List.of(his, glass)) {
					int height = Math.abs(build.size());
					helper.assertTrue(height == 5 || height == 6, "a cross " + height + " tall");
					helper.assertTrue(build.site().equals(stemBase), "the site is not the cross's base: " + build.site());
					List<BlockPos> cells = Builds.latinCross(stemBase, height, armAxis);
					Map<BlockPos, BlockState> puts = puts(build.blueprint());
					helper.assertTrue(puts.keySet().equals(new HashSet<>(cells)), "the cross is not on its Latin cells: " + puts.keySet());
					for (int n = 0; n < cells.size(); n++) {
						BlockState state = puts.get(cells.get(n));
						boolean arm = Builds.isArm(cells, n);
						String kind;
						if (build == glass) {
							helper.assertTrue(state.is(Blocks.GLASS), "a memorial block is " + state);
							kind = "glass";
						} else if (state.is(Blocks.COBBLESTONE)) {
							kind = "cobblestone";
						} else {
							helper.assertTrue(state.is(Blocks.STRIPPED_SPRUCE_LOG), "his cross is made of " + state);
							Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
							helper.assertTrue(axis == (arm ? armAxis : Direction.Axis.Y), (arm ? "an arm" : "the stem") + " log lies along " + axis);
							kind = "wood";
						}
						seen.add(kind + height);
					}
				}
			}
		}
		helper.assertTrue(seen.equals(Set.of("cobblestone5", "cobblestone6", "wood5", "wood6", "glass5", "glass6")), "kinds seen: " + seen);

		// Built in the world: a 6-tall cross stands on exactly its cells, one block wide, nothing beside its top.
		ServerLevel level = helper.getLevel();
		long seed = 0;
		while (Builds.cross(0, 0, 0, Direction.NORTH, Builds.Wood.OAK, seed).size() != 6) {
			seed++;
		}
		BlockPos ground = helper.absolutePos(new BlockPos(3, 0, 3));
		Builds.Build built = Builds.cross(ground.getX(), ground.getY(), ground.getZ(), Direction.NORTH, Builds.Wood.OAK, seed);
		built.blueprint().applyAll(level);
		Set<BlockPos> cells = new HashSet<>(Builds.latinCross(ground.above(), 6, Direction.Axis.X));
		for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, 1, -1), ground.offset(1, 7, 1))) {
			helper.assertTrue(level.getBlockState(pos).isAir() != cells.contains(pos), (cells.contains(pos) ? "a gap at " : "a block beside the cross at ")
					+ pos.toShortString());
		}
		helper.succeed();
	}

	// --- glass memorials (D-051) ---

	@GameTest
	public void glassIsOnlyEverAMemorialLeftByOthers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// One hill with its top at (500, 500).
		Terrain hill = terrain(level, (x, z) -> 120 - (Math.abs(x - 500) + Math.abs(z - 500)) / 4);
		for (long h = 0; h < 200; h++) {
			// His cross (the one on dead mountains and at tunnel mouths too) is never glass.
			helper.assertFalse(anyGlass(Builds.cross(0, 64, 0, Direction.from2DDataValue((int) (h & 3)), Builds.Wood.OAK, h).blueprint()),
					"his cross has glass (seed " + h + ")");
			int x = 500 + Hash.between(h, -30, 30);
			int z = 500 + Hash.between(h ^ 1, -30, 30);
			ScarPlan his = ScarPlanner.hilltopCross(hill, x, z, h, 0.0);
			helper.assertTrue(his != null && !ScarPlanner.isGlassMemorial(his) && !anyGlass(his.blueprint()) && his.sites().getFirst().size() > 0,
					"a hilltop cross is glass with no glass chance");
			ScarPlan memorial = ScarPlanner.hilltopCross(hill, x, z, h, 1.0);
			helper.assertTrue(memorial != null && ScarPlanner.isGlassMemorial(memorial), "a hilltop cross is not glass with glass chance 1");
			helper.assertTrue(puts(memorial.blueprint()).values().stream().allMatch(s -> s.is(Blocks.GLASS)), "a memorial is not all glass");
			ScarPlan.SiteMark mark = memorial.sites().getFirst();
			helper.assertTrue(memorial.sites().size() == 1 && mark.type() == SiteType.CROSS && CrossApi.isGlassSize(mark.size())
					&& mark.pos().equals(memorial.anchor()), "the memorial's site is " + memorial.sites());
			// Alone on the hilltop: it climbed up from where the cell rolled it.
			helper.assertTrue(memorial.anchor().getY() - 1 >= hill.ground(x, z), "the memorial stands below where it started");
		}
		helper.assertTrue(ScarPlanner.hilltopCross(hill, 500, 500, 7L, 1.0).anchor().equals(new BlockPos(500, 121, 500)),
				"the memorial is not on the top of the hill");

		// The site tells them apart: lore's "his traces" (D-041) skip glass memorials.
		SiteRegistry.Site glass = new SiteRegistry.Site(1, SiteType.CROSS, Level.OVERWORLD, BlockPos.ZERO, CrossApi.siteSize(6, true), Optional.empty());
		SiteRegistry.Site row = new SiteRegistry.Site(2, SiteType.CROSS, Level.OVERWORLD, BlockPos.ZERO, CrossApi.siteSize(5, false),
				Optional.of(CrossRow.CLAIM));
		helper.assertTrue(CrossApi.isGlassMemorial(glass) && CrossApi.height(glass) == 6, "a glass memorial's site is not marked");
		helper.assertTrue(!CrossApi.isGlassMemorial(row) && CrossApi.height(row) == 5, "his cross's site reads as a memorial");
		// A debug-placed memorial is left by others; the row is his.
		helper.assertTrue(TraceLedger.isLeftByOthers(LivePlacer.GLASS_CAUSE) && !TraceLedger.isLeftByOthers(CrossRow.CAUSE),
				"the causes say the wrong owner");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void glassMemorialRateFollowsTheProfile(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldConfig config = new WorldConfig();
		Terrain hills = terrain(level, (x, z) -> 90 + (int) Math.round(10 * Math.sin(x / 37.0) * Math.cos(z / 41.0)));
		int[] crosses = new int[2];
		int[] glass = new int[2];
		for (long seed = 1; seed <= 300; seed++) {
			long salt = Hash.of(seed, 0x5A17);
			WorldProfile profile = WorldProfile.roll(seed, salt);
			ScarPlanner planner = new ScarContext(seed, salt, profile, config, ModConfig.pacing().oldScarMinFromSpawn).planner(level);
			int mourner = profile.habits().contains(Habit.MOURNER) ? 1 : 0;
			for (int cell = 0; cell < 40; cell++) {
				ScarPlan plan = ScarPlanner.hilltopCross(hills, cell * 192 + 96, (int) seed * 192 + 96, Hash.of(seed, cell), planner.glassChance());
				if (plan == null) {
					continue;
				}
				crosses[mourner]++;
				glass[mourner] += ScarPlanner.isGlassMemorial(plan) ? 1 : 0;
			}
		}
		helper.assertTrue(crosses[0] > 2000 && crosses[1] > 1000, "too few crosses: " + crosses[0] + " and " + crosses[1]);
		double other = glass[0] / (double) crosses[0];
		double mourner = glass[1] / (double) crosses[1];
		helper.assertTrue(Math.abs(mourner - config.glassCrossChanceMourner) < 0.03, "Mourner worlds: " + glass[1] + " glass of " + crosses[1]);
		helper.assertTrue(Math.abs(other - config.glassCrossChanceOther) < 0.02, "other worlds: " + glass[0] + " glass of " + crosses[0]);
		helper.assertTrue(Math.abs(config.glassCrossChanceMourner - 1.0 / 6) < 0.01 && Math.abs(config.glassCrossChanceOther - 1.0 / 15) < 0.01,
				"the default chances moved");
		helper.assertTrue(mourner > 2 * other, "glass is not more likely in Mourner worlds");
		helper.succeed();
	}
}
