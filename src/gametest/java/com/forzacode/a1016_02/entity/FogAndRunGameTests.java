package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The client's fog report and the distances that follow from it (D-035), and the run that always outpaces a chaser
 * (D-036). {@link EntityGameTests} extends this (through {@link SightingRuleGameTests}) so they run under its
 * registered entrypoint.
 */
public class FogAndRunGameTests {
	private static boolean close(double a, double b, double tolerance) {
		return Math.abs(a - b) <= tolerance;
	}

	// --- D-035: the fog report ---

	@GameTest
	public void clientSendsTheFogEndOnChangeAndEveryHalfSecond(GameTestHelper helper) {
		// The fog the client draws is the nearer of the environmental and the render-distance fog end.
		helper.assertTrue(FogReport.visibleEnd(1024.0F, 192.0F) == 192.0F && FogReport.visibleEnd(40.0F, 192.0F) == 40.0F, "visible end");
		helper.assertTrue(FogReport.visibleEnd(Float.MAX_VALUE, 160.0F) == 160.0F && FogReport.visibleEnd(Float.NaN, 96.0F) == 96.0F
				&& FogReport.visibleEnd(55.0F, 0.0F) == 55.0F && Float.isNaN(FogReport.visibleEnd(Float.NaN, -1.0F)), "unusable ends");
		// First report at once; then only on a change of more than 2 blocks, or after half a second.
		helper.assertTrue(FogReport.shouldSend(Float.NaN, 55.0F, 0), "the first report waits");
		helper.assertFalse(FogReport.shouldSend(55.0F, 56.5F, 3), "sent a 1.5-block change before the heartbeat");
		helper.assertTrue(FogReport.shouldSend(55.0F, 52.5F, 1), "a 2.5-block change waits for the heartbeat");
		helper.assertTrue(FogReport.shouldSend(55.0F, 55.0F, FogReport.HEARTBEAT_TICKS) && FogReport.HEARTBEAT_TICKS == 10, "no heartbeat every 0.5 s");
		helper.assertFalse(FogReport.shouldSend(Float.NaN, Float.NaN, 50) || FogReport.shouldSend(55.0F, 0.0F, 50)
				|| FogReport.shouldSend(55.0F, Float.POSITIVE_INFINITY, 50), "sent something that is not a fog end");
		// The payload: its id, and the value survives the wire.
		helper.assertTrue(FogEndPayload.TYPE.id().toString().equals("a1016_02:entity/fog_end"), "payload id " + FogEndPayload.TYPE.id());
		ByteBuf buf = Unpooled.buffer();
		FogEndPayload.CODEC.encode(buf, new FogEndPayload(55.25F));
		helper.assertTrue(FogEndPayload.CODEC.decode(buf).blocks() == 55.25F, "the payload does not round-trip");
		helper.succeed();
	}

	@GameTest
	public void serverFallsBackToItsEstimateWithoutAFreshReport(GameTestHelper helper) {
		// What the server takes from a client.
		helper.assertTrue(Double.isNaN(FogReport.accept(Float.NaN)) && Double.isNaN(FogReport.accept(-3.0F)) && Double.isNaN(FogReport.accept(0.5F))
				&& FogReport.accept(5000.0F) == FogReport.MAX_BLOCKS && FogReport.accept(55.0F) == 55.0, "accept");
		// Fresh reports are used, capped at the render distance; none, stale or from an earlier server run are not.
		helper.assertTrue(FogReport.fresh(55.0, 10, 100, 192.0) == 55.0 && FogReport.fresh(400.0, 0, 100, 192.0) == 192.0, "a fresh report");
		helper.assertTrue(Double.isNaN(FogReport.fresh(Double.NaN, 0, 100, 192.0)) && Double.isNaN(FogReport.fresh(55.0, 101, 100, 192.0))
				&& Double.isNaN(FogReport.fresh(55.0, -5, 100, 192.0)), "a missing or stale report was used");

		// End to end, for one player: no report yet is the estimate; a report wins; forgetting it goes back.
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		try {
			ReportedFog.set(player, Double.NaN);
			FogEdge estimate = FogEdge.of(player);
			helper.assertTrue(!estimate.fromClient() && estimate.limit() == estimate.estimate(), "no report, yet not the estimate: " + estimate);
			ReportedFog.report(player, 20.0F);
			FogEdge reported = FogEdge.of(player);
			helper.assertTrue(reported.fromClient() && reported.limit() == Math.min(20.0, reported.renderLimit()), "the report is not used: " + reported);
			helper.assertTrue(ReportedFog.ageTicks(player) == 0 && ReportedFog.latest(player).blocks() == 20.0, "the report was not kept");
			ReportedFog.report(player, Float.NaN);
			helper.assertTrue(ReportedFog.latest(player).blocks() == 20.0, "a bad report replaced a good one");
			ReportedFog.set(player, Double.NaN);
			FogEdge after = FogEdge.of(player);
			helper.assertTrue(!after.fromClient() && after.limit() == after.estimate(), "a forgotten report is still used: " + after);
		} finally {
			ReportedFog.set(player, Double.NaN);
		}
		helper.succeed();
	}

	@GameTest
	public void fleeAndApproachScaleWithTheSpawnDistance(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		helper.assertTrue(config.fleeDistance == 18 && config.fleeSpawnFraction == 0.6 && config.approachBlocks == 10
				&& config.approachSpawnFraction == 0.3, "defaults");
		// min(18, 0.6 x spawn distance); an unknown spawn distance keeps 18.
		helper.assertTrue(SightingRules.fleeDistance(18, 0.6, 100) == 18 && close(SightingRules.fleeDistance(18, 0.6, 16), 9.6, 1.0E-9)
				&& SightingRules.fleeDistance(18, 0.6, Double.NaN) == 18 && SightingRules.fleeDistance(18, 0.6, 0) == 18, "flee distance");
		helper.assertTrue(SightingRules.approachBlocks(10, 0.3, 100) == 10 && close(SightingRules.approachBlocks(10, 0.3, 16), 4.8, 1.0E-9),
				"approach");
		// Whatever the spawn distance, he is never within the flee distance the moment he appears.
		for (double d = EntityConfig.MIN_DISTANCE_FLOOR; d <= 200; d += 0.5) {
			double flee = SightingRules.fleeDistance(config.fleeDistance, config.fleeSpawnFraction, d);
			helper.assertTrue(flee < d && flee <= 18, "flees at once from " + d + ": " + flee);
		}
		// The figure uses his own spawn distance.
		ServerLevel level = helper.getLevel();
		HimEntity him = FigureApi.spawnAt(level, Variant.CLOSE, helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)), 0.0F)
				.orElseThrow(() -> helper.assertionException("the figure did not spawn"));
		try {
			helper.assertTrue(Double.isNaN(him.spawnDistance()) && him.fleeDistance(config) == 18, "no player, yet a spawn distance");
			him.setSpawnDistance(16);
			helper.assertTrue(close(him.fleeDistance(config), 9.6, 1.0E-9) && close(him.approachBlocks(config), 4.8, 1.0E-9),
					"flee " + him.fleeDistance(config) + " approach " + him.approachBlocks(config));
		} finally {
			him.discard();
		}
		helper.succeed();
	}

	// --- D-036: the run ---

	@GameTest
	public void theRunAlwaysOutpacesTheChaser(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		helper.assertTrue(config.baseRunSpeed == 5.8 && config.outrunFactor == 1.1 && config.maxRunSpeed == 9.0, "defaults");
		double base = config.baseRunSpeed;
		double max = config.maxRunSpeed;
		// Standing, walking: his own run. Sprinting, sprint-jumping: 10% faster than you.
		helper.assertTrue(SightingRules.runSpeed(base, 0.0, 1.1, max) == base && SightingRules.runSpeed(base, 4.317, 1.1, max) == base, "base run");
		helper.assertTrue(close(SightingRules.runSpeed(base, 5.612, 1.1, max), 6.1732, 1.0E-9), "sprinting");
		helper.assertTrue(close(SightingRules.runSpeed(base, 7.1, 1.1, max), 7.81, 1.0E-9), "sprint-jumping");
		// Never past the cap (elytra and horses are a later task), and the cap wins over the base.
		helper.assertTrue(SightingRules.runSpeed(base, 30.0, 1.1, max) == max && SightingRules.runSpeed(12.0, 0.0, 1.1, max) == max, "cap");
		for (double chaser = 0.0; chaser * 1.1 <= max; chaser += 0.05) {
			double run = SightingRules.runSpeed(base, chaser, 1.1, max);
			helper.assertTrue(run >= chaser * 1.1 - 1.0E-9 && run > chaser, "caught at " + chaser + ": " + run);
		}
		helper.assertTrue(SightingRules.runSpeed(base, Double.NaN, 1.1, max) == base && SightingRules.runSpeed(base, 6.0, 0.5, max) == 6.0,
				"bad inputs (an unknown chaser, a factor under 1)");
		// The speed modifier that gives a speed on flat ground, and back. The old run (1.45) was 5.8 blocks/s.
		for (double v : new double[] {1.0, 4.317, 5.8, 7.81, 9.0, 20.0}) {
			helper.assertTrue(close(SightingRules.groundSpeed(SightingRules.speedModifier(v, HimEntity.BASE_SPEED), HimEntity.BASE_SPEED), v, 1.0E-9),
					"modifier round trip at " + v);
		}
		helper.assertTrue(close(SightingRules.speedModifier(5.8, HimEntity.BASE_SPEED), 1.45, 0.005), "the old run speed moved");
		// A walker breaks into the run when you close in fast (a sprint, not a walk) or come within the flee distance.
		helper.assertFalse(SightingRules.breaksIntoRun(3.0, config.closeInFastSpeed, false), "walking after him broke into a run");
		helper.assertTrue(SightingRules.breaksIntoRun(4.3, config.closeInFastSpeed, false) && SightingRules.breaksIntoRun(0.0, config.closeInFastSpeed, true),
				"a sprint or the flee distance did not");
		// Standing still (stare back, hiding), he runs before anyone gets within 4 blocks.
		helper.assertFalse(SightingRules.wouldBeReached(18.0, 4.3, 2.0) || SightingRules.wouldBeReached(30.0, 0.0, 2.0)
				|| SightingRules.wouldBeReached(10.0, -2.0, 2.0), "ran from someone who would not reach him");
		helper.assertTrue(SightingRules.wouldBeReached(9.6, 4.3, 2.0) && SightingRules.wouldBeReached(12.0, 7.1, 2.0)
				&& SightingRules.wouldBeReached(3.9, 0.0, 0.0), "stood while someone reached him");
		helper.succeed();
	}

	@GameTest
	public void chaseSpeedIsAveragedAndIgnoresTeleports(GameTestHelper helper) {
		// Sprinting straight at him, 0.2806 blocks a tick.
		Chase sprint = new Chase();
		double x = 0.0;
		for (int i = 0; i < 30; i++) {
			sprint.update(x, 0.0, 40.0 - x);
			x += 0.2806;
		}
		helper.assertTrue(close(sprint.speed(), 5.612, 1.0E-6) && close(sprint.closingSpeed(), 5.612, 1.0E-6), "sprint: " + sprint.speed());
		// Packets bunching up (two moves, then none) still average out.
		Chase bunched = new Chase();
		x = 0.0;
		for (int i = 0; i < 30; i++) {
			bunched.update(x, 0.0, 40.0 - x);
			x += i % 2 == 0 ? 0.0 : 0.5612;
		}
		helper.assertTrue(close(bunched.speed(), 5.612, 0.6), "bunched packets: " + bunched.speed());
		// Strafing past him: fast, but not closing in.
		Chase strafe = new Chase();
		for (int i = 0; i < 30; i++) {
			strafe.update(0.0, i * 0.2806, 30.0);
		}
		helper.assertTrue(close(strafe.speed(), 5.612, 1.0E-6) && strafe.closingSpeed() == 0.0, "strafe: " + strafe.closingSpeed());
		// A teleport is not a sprint: the window starts over.
		Chase teleport = new Chase();
		teleport.update(0.0, 0.0, 40.0);
		teleport.update(0.1, 0.0, 39.9);
		teleport.update(500.0, 0.0, 400.0);
		helper.assertTrue(teleport.speed() == 0.0, "a teleport counted as speed: " + teleport.speed());
		teleport.update(500.2, 0.0, 400.0);
		helper.assertTrue(close(teleport.speed(), 4.0, 1.0E-6), "after a teleport: " + teleport.speed());
		helper.succeed();
	}

	/**
	 * A figure without his own rules, only his body and move control, steered at {@code target} with the speed
	 * modifier for {@code blocksPerSecond}. Records his position each tick.
	 */
	private static final class Runner {
		final List<Vec3> positions = new ArrayList<>();
		final HimEntity him;

		Runner(GameTestHelper helper, Vec3 start, float yaw, Vec3 target, double blocksPerSecond) {
			ServerLevel level = helper.getLevel();
			Vec3 at = helper.absoluteVec(start);
			Vec3 to = helper.absoluteVec(target);
			double modifier = SightingRules.speedModifier(blocksPerSecond, HimEntity.BASE_SPEED);
			him = new HimEntity(ModEntities.HIM, level) {
				@Override
				protected void customServerAiStep(ServerLevel serverLevel) {
					positions.add(position());
					holdSpeedInAir(blocksPerSecond); // as his own LEAVING step does while running
					getMoveControl().setWantedPosition(to.x, to.y, to.z, modifier);
				}
			};
			him.snapTo(at.x, at.y, at.z, yaw, 0.0F);
			helper.assertTrue(level.addFreshEntity(him), "the runner did not spawn");
		}

		/** Average horizontal speed in blocks per second between two recorded ticks. */
		double speed(int from, int to) {
			double sum = 0.0;
			for (int i = from + 1; i <= to; i++) {
				sum += positions.get(i).subtract(positions.get(i - 1)).horizontalDistance();
			}
			return sum / (to - from) * 20.0;
		}
	}

	/** Each recorded tick as z/y/speed, for failure messages. */
	private static String trace(Runner runner, Vec3 origin) {
		StringBuilder out = new StringBuilder();
		for (int i = 1; i < runner.positions.size(); i++) {
			Vec3 a = runner.positions.get(i - 1);
			Vec3 b = runner.positions.get(i);
			out.append(String.format(java.util.Locale.ROOT, " [z=%.2f y=%.2f %.2f]", b.z - origin.z, b.y - origin.y, b.subtract(a).horizontalDistance() * 20.0));
		}
		return out.toString();
	}

	private static void stoneFloor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Runs him at each speed across the floor and checks he settles at it: the physics behind the outrun rule. */
	@GameTest(maxTicks = 60)
	public void heRunsAtTheSpeedHeIsGiven(GameTestHelper helper) {
		stoneFloor(helper);
		double[] speeds = {EntityConfig.get().baseRunSpeed, 7.81, 9.0};
		List<Runner> runners = new ArrayList<>();
		// One lane each along +z (yaw 0 faces south), the barrier at the end of the test far enough away.
		for (int i = 0; i < speeds.length; i++) {
			double laneX = 1.5 + i * 2.5;
			runners.add(new Runner(helper, new Vec3(laneX, 1.0, 0.6), 0.0F, new Vec3(laneX, 1.0, 60.0), speeds[i]));
		}
		helper.runAfterDelay(16, () -> {
			for (int i = 0; i < speeds.length; i++) {
				Runner runner = runners.get(i);
				helper.assertTrue(runner.positions.size() >= 14, "the runner did not tick: " + runner.positions.size());
				// Up to speed after 5 ticks; ticks 6 to 12 stay inside the 8-block floor at 9 blocks/s.
				double measured = runner.speed(6, 12);
				helper.assertTrue(measured >= speeds[i] * 0.95 && measured <= speeds[i] * 1.05,
						String.format(java.util.Locale.ROOT, "asked for %.2f blocks/s, ran %.2f", speeds[i], measured));
				runner.him.discard();
			}
			helper.succeed();
		});
	}

	/** At full speed he takes a one-block step without jumping or stalling, and keeps his speed. */
	@GameTest(maxTicks = 60)
	public void heTakesOneBlockStepsAtARun(GameTestHelper helper) {
		stoneFloor(helper);
		for (int x = 0; x < 8; x++) {
			for (int z = 4; z < 8; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		Runner runner = new Runner(helper, new Vec3(3.5, 1.0, 0.6), 0.0F, new Vec3(3.5, 2.0, 60.0), 9.0);
		helper.assertTrue(runner.him.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0, "he cannot step up a block");
		helper.runAfterDelay(18, () -> {
			Vec3 at = runner.him.position().subtract(helper.absoluteVec(Vec3.ZERO));
			helper.assertTrue(at.y >= 1.99 && at.z >= 5.0, "stuck at the step: " + at);
			// Over the step (z 3 to 5, its face at 3.7 for his front) he keeps most of his speed.
			int before = -1;
			int after = -1;
			for (int i = 0; i < runner.positions.size(); i++) {
				double z = runner.positions.get(i).z - helper.absoluteVec(Vec3.ZERO).z;
				if (before < 0 && z >= 3.0) {
					before = i;
				}
				if (after < 0 && z >= 5.0) {
					after = i;
				}
			}
			helper.assertTrue(before >= 0 && after > before, "never crossed the step: " + before + " " + after + " " + runner.positions);
			double crossing = runner.speed(before, after);
			helper.assertTrue(crossing >= 9.0 * 0.6, String.format(java.util.Locale.ROOT, "stalled on the step: %.2f blocks/s", crossing));
			runner.him.discard();
			helper.succeed();
		});
	}

	/** Off a one-block ledge at full speed he keeps his speed through the air, like a sprint-jumping player. */
	@GameTest(maxTicks = 60)
	public void heKeepsHisSpeedOffALedge(GameTestHelper helper) {
		stoneFloor(helper);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		Runner runner = new Runner(helper, new Vec3(3.5, 2.0, 0.6), 0.0F, new Vec3(3.5, 1.0, 60.0), 9.0);
		helper.runAfterDelay(18, () -> {
			Vec3 origin = helper.absoluteVec(Vec3.ZERO);
			double slowest = Double.MAX_VALUE;
			boolean dropped = false;
			for (int i = 1; i < runner.positions.size(); i++) {
				Vec3 a = runner.positions.get(i - 1).subtract(origin);
				Vec3 b = runner.positions.get(i).subtract(origin);
				if (a.z >= 3.0 && b.z <= 6.5) {
					slowest = Math.min(slowest, b.subtract(a).horizontalDistance() * 20.0);
					dropped |= b.y < 1.99;
				}
			}
			helper.assertTrue(dropped && slowest < Double.MAX_VALUE, "never ran off the ledge: " + runner.positions);
			helper.assertTrue(slowest >= 9.0 * 0.85, String.format(java.util.Locale.ROOT, "slowed to %.2f blocks/s in the air", slowest) + " " + trace(runner, origin));
			runner.him.discard();
			helper.succeed();
		});
	}
}
