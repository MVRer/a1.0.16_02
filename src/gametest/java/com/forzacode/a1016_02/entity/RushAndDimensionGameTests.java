package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The close-chase rush (D-037) and the End and Nether sightings (D-034). {@link EntityGameTests} extends this
 * (through {@link GoUnderGameTests}) so they run under its registered entrypoint.
 */
public class RushAndDimensionGameTests extends SightingRuleGameTests {
	/** A figure whose rules watch only these mock players (the test level has no real ones). */
	static HimEntity watchedFigure(GameTestHelper helper, Variant variant, Vec3 relativeFeet, float yaw, List<ServerPlayer> players) {
		ServerLevel level = helper.getLevel();
		HimEntity him = new HimEntity(ModEntities.HIM, level) {
			@Override
			protected List<ServerPlayer> observers(ServerLevel serverLevel) {
				return List.copyOf(players);
			}

			@Override
			protected Watchers watchers(ServerLevel serverLevel) {
				return Watchers.of(players, serverLevel.getServer().getPlayerList().getViewDistance());
			}

			@Override
			protected boolean tickingAroundHim(ServerLevel serverLevel) {
				return true; // only the test's own chunks are loaded; in a world the players load them
			}
		};
		Vec3 feet = helper.absoluteVec(relativeFeet);
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, null);
		helper.assertTrue(level.addFreshEntity(him), "the figure did not spawn");
		return him;
	}

	/** A mock player (not in the level) at {@code absolute}, looking along {@code yaw} and {@code pitch}. */
	static ServerPlayer mockPlayer(GameTestHelper helper, Vec3 absolute, float yaw, float pitch) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		place(player, absolute, yaw, pitch);
		return player;
	}

	static void place(ServerPlayer player, Vec3 absolute, float yaw, float pitch) {
		player.snapTo(absolute.x, absolute.y, absolute.z, yaw, pitch);
		player.setYHeadRot(yaw); // the view vector follows the head
	}

	/** The chaser's velocity each tick, from the tick and both positions. */
	private interface Steer {
		Vec3 velocity(int tick, Vec3 him, Vec3 player);
	}

	/** What a simulated rush did. */
	private record Run(double closestAfterHisStep, double closestBeforeHisStep, double closestToPredicted, boolean passed, double finalDistance,
			double firstStepToward) {
	}

	private static double flat(Vec3 a, Vec3 b) {
		return Rush.flat(a.subtract(b)).length();
	}

	/**
	 * Each tick: the player moves, then he takes his step against where the player is now and their velocity.
	 * Records the closest he ends a step to the player, the closest the player's next position comes to where he
	 * stands (the gap he leaves for the player's own move), and the closest to where the player was predicted to be.
	 */
	private static Run simulate(Vec3 him0, Vec3 player0, Steer steer, double offset, double step, int ticks) {
		Vec3 him = him0;
		Vec3 player = player0;
		Rush rush = Rush.toward(him, player, Rush.sideAwayFrom(him, player, steer.velocity(0, him, player)), offset);
		double afterStep = Double.MAX_VALUE;
		double beforeStep = Double.MAX_VALUE;
		double predicted = Double.MAX_VALUE;
		double firstToward = Double.NaN;
		for (int i = 0; i < ticks; i++) {
			Vec3 v = Rush.flat(steer.velocity(i, him, player));
			player = player.add(v);
			beforeStep = Math.min(beforeStep, flat(him, player));
			Vec3 next = rush.step(him, player, v, step);
			if (i == 0) {
				Vec3 moved = Rush.flat(next.subtract(him));
				Vec3 toPlayer = Rush.flat(player.subtract(him));
				firstToward = moved.dot(toPlayer) / (moved.length() * toPlayer.length());
			}
			afterStep = Math.min(afterStep, flat(next, player));
			predicted = Math.min(predicted, Rush.distanceToSegment(next, player, player.add(v)));
			him = next;
		}
		return new Run(afterStep, beforeStep, predicted, rush.passed(), flat(him, player), firstToward);
	}

	@GameTest
	public void theRushNeverComesWithinThePassOffset(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		helper.assertTrue(config.rushTriggerDistance == 10 && config.rushPassOffset() == 2.0, "D-037 defaults");
		config.rushPassOffset = 0.5;
		helper.assertTrue(config.rushPassOffset() == Rush.MIN_OFFSET, "a pass closer than 1.5 blocks was allowed");
		// The trigger: a chaser he cannot outrun, closing in fast, within the distance. Never someone on foot.
		helper.assertTrue(SightingRules.rushes(9.0, 10, 30.0, 21.0, 1.1, 9.0, 3.5), "elytra at 9 blocks");
		helper.assertTrue(SightingRules.rushes(9.9, 10, 10.9, 4.0, 1.1, 9.0, 3.5), "creative flight at 9.9 blocks");
		helper.assertFalse(SightingRules.rushes(10.5, 10, 30.0, 21.0, 1.1, 9.0, 3.5), "elytra at 10.5 blocks");
		helper.assertFalse(SightingRules.rushes(6.0, 10, 7.1, 7.1, 1.1, 9.0, 3.5), "a sprint-jumping player on foot");
		helper.assertFalse(SightingRules.rushes(6.0, 10, 30.0, -5.0, 1.1, 9.0, 3.5), "flying away from him");
		helper.assertFalse(SightingRules.rushes(6.0, 10, 30.0, 2.0, 1.1, 9.0, 3.5), "flying past him, barely closing");
		helper.assertFalse(SightingRules.rushes(Double.NaN, 10, 30.0, 21.0, 1.1, 9.0, 3.5), "an unknown distance");

		double step = config.maxRunSpeed / 20.0;
		Vec3 origin = Vec3.ZERO;
		List<String> constant = List.of("head-on elytra", "oblique", "horse", "strafing", "hovering");
		for (double offset : new double[] {Rush.MIN_OFFSET, 2.0}) {
			List<Run> straight = List.of(
					simulate(origin, new Vec3(0, 0, 12), (i, h, p) -> new Vec3(0, 0, -1.5), offset, step, 60),
					simulate(origin, new Vec3(9, 0, 8), (i, h, p) -> new Vec3(-9, 0, -8).normalize().scale(1.2), offset, step, 60),
					simulate(origin, new Vec3(0, 0, 10), (i, h, p) -> new Vec3(0, 0, -0.6), offset, step, 80),
					simulate(origin, new Vec3(-6, 0, 5), (i, h, p) -> new Vec3(1.0, 0, -0.3), offset, step, 60),
					simulate(origin, new Vec3(0, 0, 5), (i, h, p) -> Vec3.ZERO, offset, step, 60));
			for (int i = 0; i < straight.size(); i++) {
				Run run = straight.get(i);
				String what = constant.get(i) + " at offset " + offset + ": " + run;
				helper.assertTrue(run.closestAfterHisStep() >= offset - 1.0E-6, "his step ended inside the offset, " + what);
				// The player keeps their course, so even before his next step he is never inside the offset.
				helper.assertTrue(run.closestBeforeHisStep() >= offset - 1.0E-6,
						"inside the offset between steps, " + what);
				helper.assertTrue(run.passed() && run.finalDistance() >= offset + 2.0, "never got past, " + what);
			}
			// Running at the player: his first step heads at them (within about 25 degrees).
			helper.assertTrue(straight.getFirst().firstStepToward() > 0.9, "the first step is not at the player: " + straight.getFirst());
			// A player steering into him every tick: his own steps still never end inside the offset.
			Run homing = simulate(origin, new Vec3(2, 0, 9), (i, h, p) -> Rush.flat(h.subtract(p)).normalize().scale(1.5), offset, step, 60);
			helper.assertTrue(homing.closestAfterHisStep() >= offset - 1.0E-6 && homing.closestToPredicted() >= offset - 1.0E-6, "homing: " + homing);
			// Random chases: any start, any speed up to a fast elytra, swerving.
			RandomSource random = RandomSource.create(1016);
			for (int n = 0; n < 300; n++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = offset + 0.5 + random.nextDouble() * 9.0;
				Vec3 start = new Vec3(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
				double speed = random.nextDouble() * 1.6;
				double heading = angle + Math.PI + (random.nextDouble() - 0.5) * 1.5;
				double swerve = (random.nextDouble() - 0.5) * 0.4;
				Run run = simulate(origin, start, (i, h, p) -> new Vec3(Math.cos(heading + swerve * i), 0, Math.sin(heading + swerve * i)).scale(speed),
						offset, step, 60);
				helper.assertTrue(run.closestAfterHisStep() >= offset - 1.0E-6 && run.closestToPredicted() >= offset - 1.0E-6,
						"random chase " + n + " (start " + start + ", speed " + speed + "): " + run);
			}
		}
		// Planning: a side whose path is not walkable is not taken; with neither, no rush.
		Vec3 player = new Vec3(0, 0, 9);
		Vec3 v = new Vec3(0, 0, -1.5);
		helper.assertTrue(Rush.plan(origin, player, v, 2.0, step, path -> true).isPresent(), "no rush on open ground");
		Optional<Rush> eastOnly = Rush.plan(origin, player, v, 2.0, step, path -> path.stream().allMatch(s -> s.him().x >= -0.3));
		helper.assertTrue(eastOnly.isPresent() && eastOnly.get().side() == 1, "did not take the open side: " + eastOnly.map(Rush::side).orElse(0));
		helper.assertTrue(Rush.plan(origin, player, v, 2.0, step, path -> false).isEmpty(), "a rush with nowhere to run");
		helper.assertTrue(Rush.plan(origin, new Vec3(0.5, 0, 1.0), v, 2.0, step, path -> true).isEmpty(), "a rush from inside the offset");
		helper.succeed();
	}

	/** What an in-world chase did, recorded each tick until he is gone. */
	private static final class Chased {
		final HimEntity him;
		final List<ServerPlayer> players;
		double closest = Double.MAX_VALUE;
		double towardPlayer;
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		boolean passed;
		boolean rushing;
		Vec3 last;

		Chased(HimEntity him, List<ServerPlayer> players) {
			this.him = him;
			this.players = players;
			this.last = him.position();
		}
	}

	/**
	 * A stone floor (plus {@code walls}, 3 high, on whole x columns), him at x 3.5 near the north edge, and an elytra
	 * flyer 13 blocks south coming straight at him at 30 blocks a second, looking ahead.
	 */
	private static Chased elytraChase(GameTestHelper helper, int... walls) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		for (int x : walls) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		ServerPlayer flyer = mockPlayer(helper, helper.absoluteVec(new Vec3(3.5, 1.5, 14.5)), 180.0F, 10.0F);
		List<ServerPlayer> players = List.of(flyer);
		Chased chase = new Chased(watchedFigure(helper, Variant.RIDGE, new Vec3(3.5, 1.0, 1.5), 0.0F, players), players);
		HimEntity him = chase.him;
		double startZ = him.getZ();
		helper.onEachTick(() -> {
			if (him.isRemoved()) {
				return;
			}
			chase.closest = Math.min(chase.closest, flat(him.position(), flyer.position()));
			place(flyer, flyer.position().add(0.0, 0.0, -1.5), 180.0F, 10.0F);
			chase.closest = Math.min(chase.closest, flat(him.position(), flyer.position()));
			if (him.rush() != null) {
				chase.rushing = true;
				chase.passed |= him.rush().passed();
				chase.minX = Math.min(chase.minX, him.getX() - helper.absoluteVec(Vec3.ZERO).x);
				chase.maxX = Math.max(chase.maxX, him.getX() - helper.absoluteVec(Vec3.ZERO).x);
			}
			chase.towardPlayer = Math.max(chase.towardPlayer, him.getZ() - startZ);
			chase.last = him.position();
		});
		return chase;
	}

	/** He passed beside the flyer, never within the offset, and was gone only out of view. */
	private static void assertCleanRush(GameTestHelper helper, Chased chase) {
		HimEntity him = chase.him;
		int serverChunks = helper.getLevel().getServer().getPlayerList().getViewDistance();
		helper.assertTrue(him.isRemoved(), "still out: " + him.phase() + " at " + him.position());
		helper.assertTrue(him.rushed() && chase.rushing, "he never rushed");
		helper.assertTrue(chase.passed, "he never got past the player");
		helper.assertTrue(chase.towardPlayer > 1.0, "he did not run at the player: " + chase.towardPlayer);
		helper.assertTrue(chase.closest >= Rush.MIN_OFFSET, "he came within " + chase.closest + " blocks of the player");
		helper.assertFalse(him.seenWhenRemoved(), "removed while in view");
		helper.assertTrue("rushed past, out of view".equals(him.goneWhy()), "gone because " + him.goneWhy());
		helper.assertFalse(Watchers.of(chase.players, serverChunks).sees(helper.getLevel(), HimEntity.viewBox(chase.last)), "in view where he was removed");
	}

	@GameTest(maxTicks = 120, padding = 16)
	public void theRushPassesBesideTheChaserAndIsGoneOnlyOutOfView(GameTestHelper helper) {
		Chased chase = elytraChase(helper);
		helper.succeedWhen(() -> assertCleanRush(helper, chase));
	}

	@GameTest(maxTicks = 120, padding = 16)
	public void theRushTakesTheSideWithRoom(GameTestHelper helper) {
		// A wall on the east, where he would pass by default: with no room beside his line there, he passes on the west.
		Chased chase = elytraChase(helper, 6);
		helper.succeedWhen(() -> {
			assertCleanRush(helper, chase);
			helper.assertTrue(chase.maxX <= 3.6, "he passed on the side of the wall: x up to " + chase.maxX);
		});
	}

	@GameTest(maxTicks = 120, padding = 16)
	public void noRushWithoutRoomToPass(GameTestHelper helper) {
		// A corridor too narrow to pass the flyer 1.5 blocks or more to either side: he does not rush, he runs.
		Chased chase = elytraChase(helper, 1, 6);
		helper.succeedWhen(() -> {
			helper.assertTrue(chase.him.rushed(), "the chase never got close enough to try");
			helper.assertFalse(chase.rushing, "he rushed down a corridor with no room to pass: x " + chase.minX + ".." + chase.maxX);
			helper.assertTrue(chase.him.phase() == HimEntity.Phase.LEAVING || chase.him.isRemoved(), "he did not run: " + chase.him.phase());
		});
	}

	/** Who stared him down, as FigureApi.STARED reported it (registered once). */
	private static final List<ServerPlayer> STARED_BY = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static boolean staredRegistered;

	/** STARED_AT_HIM: staring at him for stareSeconds sets entity:stared and fires FigureApi.STARED with the player. */
	@GameTest(structure = "a1016_02:accident/yard", maxTicks = 200)
	public void staringHimDownFiresStaredAndSetsTheFlag(GameTestHelper helper) {
		if (!staredRegistered) {
			staredRegistered = true;
			FigureApi.STARED.register((player, him) -> STARED_BY.add(player));
		}
		for (int x = 0; x < 24; x++) {
			for (int z = 8; z < 13; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		// 14 blocks apart along x (his flee distance scales down to 0.6 of that), well inside the test server's view
		// distance, the player looking straight at him and never moving.
		ServerPlayer looker = mockPlayer(helper, helper.absoluteVec(new Vec3(4.5, 1.0, 10.5)), -90.0F, 0.0F);
		HimEntity him = watchedFigure(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, List.of(looker));
		helper.succeedWhen(() -> {
			helper.assertTrue(him.stared(), "not stared down yet: stare " + him.stareTicks() + ", seen " + him.seenTicks());
			helper.assertTrue(STARED_BY.contains(looker), "FigureApi.STARED did not fire for the player");
			helper.assertTrue(com.forzacode.a1016_02.core.HerobrineState.get(helper.getLevel().getServer()).hasFlag(FigureApi.STARED_FLAG),
					"entity:stared is not set");
			if (!him.isRemoved()) {
				him.discard();
			}
		});
	}

	/** Ending D's last minute (FigureApi "no run"): the same elytra chase, and he never runs, rushes or goes under. */
	@GameTest(maxTicks = 60, padding = 16)
	public void withNoRunHeNeverRunsFromTheChaser(GameTestHelper helper) {
		Chased chase = elytraChase(helper);
		HimEntity him = chase.him;
		FigureApi.setNoRun(him, true);
		boolean[] ran = {false};
		helper.onEachTick(() -> {
			if (!him.isRemoved()) {
				ran[0] |= him.gait() == Variant.Gait.RUN || him.outrunning() || him.rush() != null || him.phase() == HimEntity.Phase.GOING_UNDER;
			}
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(him.noRun(), "the option was lost");
			helper.assertFalse(ran[0] || chase.rushing || him.rushed(), "he ran, rushed or went under with no run set");
			him.walkAway(Variant.Gait.RUN);
			helper.assertTrue(him.isRemoved() || him.gait() != Variant.Gait.RUN, "walkAway(RUN) made him run");
			if (!him.isRemoved()) {
				him.discard();
			}
			helper.succeed();
		});
	}

	@GameTest
	public void dimensionCardsOnlyFitInTheirDimension(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel();
		MinecraftServer server = overworld.getServer();
		ServerLevel nether = server.getLevel(Level.NETHER);
		ServerLevel end = server.getLevel(Level.END);
		helper.assertTrue(nether != null && end != null, "the test server has no Nether or End");
		for (Variant variant : Variant.values()) {
			ResourceKey<Level> expected = variant == Variant.AMONG_ENDERMEN ? Level.END : variant == Variant.AMONG_PIGLINS ? Level.NETHER : Level.OVERWORLD;
			helper.assertTrue(variant.dimension() == expected, variant + " runs in " + variant.dimension());
		}
		helper.assertTrue(Variant.AMONG_ENDERMEN.cardId().equals("sighting_among_endermen") && Variant.AMONG_PIGLINS.cardId().equals("sighting_among_piglins"),
				"card ids");
		helper.assertTrue(Variant.AMONG_ENDERMEN.fake() == Variant.Fake.NONE && Variant.AMONG_PIGLINS.fake() == Variant.Fake.NONE, "a fake that needs a mob held");
		// The director asks each card with the subject's own level: the dimension gate is checked against it, first.
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		for (ServerLevel level : List.of(overworld, nether, end)) {
			for (Variant variant : Variant.values()) {
				EventCard card = CardRegistry.get(variant.cardId()).orElseThrow(() -> helper.assertionException("no card " + variant.cardId()));
				boolean home = level.dimension() == variant.dimension();
				Optional<String> why = SightingGates.check(player, level, variant);
				String what = variant + " in " + level.dimension() + ": " + why.orElse("fits");
				helper.assertTrue(SightingGates.dimensionFits(variant, level) == home, what);
				helper.assertTrue(why.isPresent() && why.get().startsWith("not in ") != home || why.isEmpty() && home, what);
				if (!home) {
					helper.assertFalse(card.contextFits(player, level), "fits outside its dimension: " + what);
				}
			}
		}
		// Forced (debug) spawns too: never in the wrong dimension.
		helper.assertTrue(FigureApi.spawnAtFogEdge(player, Variant.AMONG_PIGLINS, RandomSource.create(1), true).result() == FireResult.SKIPPED,
				"the Nether card spawned in the overworld");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void amongMobsHeStandsBesideThemUnderTheCeiling(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A Nether-like room: floor at y 0, a roof at y 5, so the heightmap is the roof.
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.NETHERRACK);
				helper.setBlock(x, 5, z, Blocks.NETHERRACK);
			}
		}
		List<ZombifiedPiglin> piglins = new ArrayList<>();
		for (Vec3 at : List.of(new Vec3(2.5, 1.0, 3.5), new Vec3(5.5, 1.0, 4.5))) {
			ZombifiedPiglin piglin = helper.spawn(EntityTypes.ZOMBIFIED_PIGLIN, at);
			piglin.setNoAi(true);
			piglins.add(piglin);
		}
		ServerPlayer player = mockPlayer(helper, helper.absoluteVec(new Vec3(3.5, 1.0, -16.5)), 0.0F, 0.0F);
		double min = EntityConfig.get().minDistance();
		FigureApi.Band band = new FigureApi.Band(15.0, 26.0);
		List<Vec3> mobs = SightingGates.mobsAtEdge(player, Variant.AMONG_PIGLINS, band, 6.0);
		helper.assertTrue(mobs.size() >= 2, "the piglins at the edge were not found: " + mobs);
		helper.assertTrue(SightingGates.mobsAtEdge(player, Variant.RIDGE, band, 6.0).isEmpty(), "mobs for a variant that stands alone");
		double floorY = helper.absoluteVec(new Vec3(0, 1.0, 0)).y;
		for (long seed = 1; seed <= 5; seed++) {
			SpotFinder.Query q = new SpotFinder.Query(level, player.position(), player.getEyePosition(), band.inner(), band.outer(), min,
					ModEntities.HIM.getDimensions(), box -> true, level::isLoaded, RandomSource.create(seed), 48);
			Optional<SpotFinder.Spot> spot = SpotFinder.among(q, mobs, 6.0, FigureApi.AMONG_MIN_GAP);
			helper.assertTrue(spot.isPresent(), "no spot among the piglins (seed " + seed + ")");
			Vec3 feet = spot.get().pos();
			helper.assertTrue(Math.abs(feet.y - floorY) < 1.0E-6, "not on the floor under the roof: " + feet);
			double nearestMob = mobs.stream().mapToDouble(m -> flat(m, feet)).min().orElse(Double.MAX_VALUE);
			helper.assertTrue(nearestMob >= FigureApi.AMONG_MIN_GAP - 1.0E-6 && nearestMob <= 6.0 + 1.0E-6, "not beside a piglin: " + nearestMob);
		}
		// Ground near a height, not the heightmap: under the roof, not on it.
		Vec3 inside = helper.absoluteVec(new Vec3(4.5, 1.0, 1.5));
		Vec3 stand = SpotFinder.standNear(level, inside.x, inside.z, inside.y, 2, 3, ModEntities.HIM.getDimensions());
		helper.assertTrue(stand != null && Math.abs(stand.y - floorY) < 1.0E-6, "standNear: " + stand);
		piglins.forEach(ZombifiedPiglin::discard);
		helper.succeed();
	}
}
