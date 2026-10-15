package com.forzacode.a1016_02.accident;

import java.util.List;

import com.forzacode.a1016_02.accident.trap.DarkCornerTrap;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.storage.LevelData;

/**
 * Traps with friends, the MP-7 findings (D-059, D-062): the bed counts its player's own sleepless nights while they
 * are offline and friends play, the dark corner waits for the real morning while someone lies in bed, and the live
 * dripstone never springs for someone far under it. Every test uses its own {@link Group}.
 * {@link AccidentGameTests} extends this (through {@link GroupGameTests}).
 */
public class GroupTrapGameTests {
	private static ServerPlayer mock(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	/**
	 * The dark corner is out for the night; Sam lies down in a bed (the night is not skipped: Alex is up). The torches
	 * stay out until the real morning comes on the game clock, which is what a skipped night jumps to.
	 */
	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40, padding = Yard.BASE_PADDING)
	public void theDarkCornerWaitsForTheMorningWhileAFriendLiesInBed(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 16, 0, 16, Blocks.STONE);
		int[][] torches = {{1, 1}, {2, 1}, {1, 2}, {8, 8}, {12, 12}, {12, 4}, {4, 12}, {8, 3}};
		for (int[] t : torches) {
			y.placed(t[0], 1, t[1], Blocks.TORCH);
		}
		BlockPos base = y.abs(8, 1, 8);
		List<Candidate> found = Traps.DARK_CORNER.candidates(y.ctx(Yard.NOBODY, base));
		helper.assertFalse(found.isEmpty(), "no dark corner found");
		Candidate spot = found.getFirst();
		helper.assertTrue(Traps.DARK_CORNER.setup(y.ctx(Yard.NOBODY, base, y.player), spot), "the torches were not taken");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.DARK_CORNER, y.ctx(Yard.NOBODY, base), spot, y.cfg);
		long clock = AccidentPlannerImpl.clock(y.level.getServer());
		helper.assertTrue(armed.hasClock() && clock < armed.clockUntil(), "the morning is already here, so this checks nothing");

		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		y.face(alex, 14.5, 1, 14.5, 0, 0);
		sam.snapTo(y.absVec(200.5, 1, 6.5), 0, 0);
		sam.setSleepingPos(y.abs(200, 1, 6));
		helper.assertTrue(sam.isSleeping(), "Sam is not lying in bed");
		Group group = new Group(helper, alex, sam);
		TrapContext night = new TrapContext(y.level, alex, alex.getUUID(), List.of(alex, sam), base, y.data, Yard.NOBODY, y.cfg, y.now(), y.today(), group.watch);
		ArmedTrap still = Traps.DARK_CORNER.tick(night, armed);
		helper.assertTrue(still != null && still.phase() == ArmedTrap.Phase.SET, "the torches came back while the night went on: " + (still == null ? null : still.phase()));
		for (ArmedTrap.SavedBlock torch : armed.saved()) {
			helper.assertTrue(y.level.getBlockState(torch.pos()).isAir(), "a torch came back while the night went on");
		}
		// The morning on the game clock (where a skipped night lands): they come back.
		ArmedTrap back = Traps.DARK_CORNER.tick(night, armed.withClockUntil(clock));
		helper.assertTrue(back != null && back.phase() == ArmedTrap.Phase.RESTORED, "the torches did not come back in the morning");
		helper.assertTrue(DarkCornerTrap.restore(y.level, Yard.NOBODY, back) == back, "a torch is still out");
		y.succeedWithoutDrops();
	}

	/**
	 * Alex's path under a stalactite: Sam, in a mine 12 blocks under it (right under the stalactite), never sets it off,
	 * since it could never reach him through the rock; Sam on the path does.
	 */
	@GameTest(structure = Yard.STRUCTURE, maxTicks = 100)
	public void theDripstoneNeverSpringsForSomeoneFarBelow(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(2, 0, 2, 8, 0, 8, Blocks.STONE);
		y.fill(2, 12, 2, 8, 12, 8, Blocks.STONE);
		y.set(5, 11, 5, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(SpeleothemBlock.TIP_DIRECTION, Direction.DOWN)
				.setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP));
		ServerPlayer alex = y.player;
		y.walk(5, 1, 5, RouteBook.UNDER);
		BlockPos holder = y.abs(5, 12, 5);
		Candidate spot = y.taking(Traps.FALLING_DRIPSTONE.candidates(y.ctx(Yard.NOBODY, y.abs(5, 1, 5))), holder);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.FALLING_DRIPSTONE, y.ctx(Yard.NOBODY, holder), spot, y.cfg);
		ServerPlayer sam = mock(helper);
		Group group = new Group(helper, alex, sam);
		alex.snapTo(y.absVec(40.5, 1, 5.5), 0, 0);
		sam.snapTo(y.absVec(5.5, -11, 5.5), 0, 0);
		ArmedTrap below = Traps.FALLING_DRIPSTONE.tick(
				new TrapContext(y.level, alex, alex.getUUID(), List.of(alex, sam), holder, y.data, Yard.NOBODY, y.cfg, y.now(), y.today(), group.watch), armed);
		helper.assertTrue(below != null && !below.isSet() && y.state(5, 12, 5).is(Blocks.STONE), "it sprang for Sam, 12 blocks under the path");
		sam.snapTo(y.absVec(5.5, 1, 5.5), 0, 0);
		ArmedTrap sprung = Traps.FALLING_DRIPSTONE.tick(
				new TrapContext(y.level, alex, alex.getUUID(), List.of(alex, sam), holder, y.data, Yard.NOBODY, y.cfg, y.now(), y.today(), group.watch), armed);
		helper.assertTrue(sprung != null && sprung.isSet(), "it did not spring for Sam on the path under it");
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 11, 5));
			helper.assertEntityNotPresent(EntityTypes.FALLING_BLOCK);
		});
	}
}
