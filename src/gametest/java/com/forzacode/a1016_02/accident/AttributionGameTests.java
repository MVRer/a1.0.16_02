package com.forzacode.a1016_02.accident;

import java.util.Arrays;
import java.util.List;

import com.forzacode.a1016_02.accident.trap.HouseFireTrap;
import com.forzacode.a1016_02.accident.trap.NoBedTrap;
import com.forzacode.a1016_02.core.HerobrineState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;

/**
 * Which deaths a trap may claim. A marked death leaves a cross, counts toward Ending B and ends a hardcore run, so
 * these check the misses that must stay misses: ordinary monsters, fire that is not his, phantoms of nights he had no
 * part in, and debug marks that must not count.
 */
public class AttributionGameTests {
	/** A base with a dark-able corner at (1..2, 1, 1..2), clearly the farthest from the middle, and five torches well away from it. */
	private static Yard darkBase(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		int[][] torches = {{1, 1}, {2, 1}, {1, 2}, {14, 14}, {17, 10}, {10, 17}, {15, 8}, {12, 12}};
		for (int[] t : torches) {
			y.placed(t[0], 1, t[1], Blocks.TORCH);
		}
		y.player.snapTo(y.absVec(11.5, 1, 11.5), 0, 0);
		return y;
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 60)
	public void darkCornerBlamesOnlyMonstersBornInTheDark(GameTestHelper helper) {
		Yard y = darkBase(helper);
		ServerLevel level = y.level;
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		AccidentPlannerImpl.ArmResult armed = planner.arm(y.player, Traps.DARK_CORNER, true);
		helper.assertTrue(armed.armed() && armed.trap().saved().size() == 3, "the corner was not darkened: " + armed.message());
		helper.assertTrue(armed.trap().clockUntil() > level.getServer().overworld().getOverworldClockTime(), "no morning on the clock");
		helper.runAfterDelay(10, () -> {
			DamageSources damage = level.damageSources();
			Zombie born = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
			planner.onSpawned(level, born);
			Zombie lit = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(13, 1, 14));
			planner.onSpawned(level, lit);
			Zombie walkedIn = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(18, 1, 18));
			planner.onSpawned(level, walkedIn);
			walkedIn.teleportTo(born.getX(), born.getY(), born.getZ() + 1);

			y.player.snapTo(y.absVec(3.5, 1, 4.5), 0, 0);
			helper.assertTrue(planner.causedBy(y.player, damage.mobAttack(born)), "a zombie born in the dark corner was not claimed");
			helper.assertFalse(planner.causedBy(y.player, damage.mobAttack(lit)), "a zombie born in a lit spot was claimed");
			helper.assertFalse(planner.causedBy(y.player, damage.mobAttack(walkedIn)), "a zombie that walked into the corner was claimed");
			helper.assertFalse(planner.causedBy(y.player, damage.fall()), "a fall in the base was claimed by the dark corner");
			born.discard();
			lit.discard();
			walkedIn.discard();
			helper.succeed();
		});
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 60)
	public void darkCornerIgnoresSpawnsBehindAWall(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		// The corner is a closed stone room (inside x 1..4, z 1..4); the yard outside it is beyond its walls.
		y.fill(0, 1, 0, 5, 3, 5, Blocks.STONE);
		y.fill(1, 1, 1, 4, 3, 4, Blocks.AIR);
		y.fill(0, 4, 0, 5, 4, 5, Blocks.STONE);
		int[][] torches = {{1, 1}, {2, 1}, {1, 2}, {14, 14}, {17, 10}, {10, 17}, {15, 8}, {12, 12}};
		for (int[] t : torches) {
			y.placed(t[0], 1, t[1], Blocks.TORCH);
		}
		y.player.snapTo(y.absVec(11.5, 1, 11.5), 0, 0);
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		ArmedTrap trap = planner.arm(y.player, Traps.DARK_CORNER, true).trap();
		helper.assertTrue(trap != null && trap.saved().size() == 3, "the corner room was not darkened");
		helper.assertTrue(trap.lit().contains(y.abs(3, 1, 3)), "the room is not among the darkened cells");
		helper.assertFalse(trap.lit().contains(y.abs(6, 1, 3)), "light went through the wall");
		helper.runAfterDelay(10, () -> {
			Zombie inside = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
			planner.onSpawned(level, inside);
			// Six blocks from a taken torch, dark, but on the other side of the wall: not his.
			Zombie outside = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 3));
			planner.onSpawned(level, outside);
			y.player.snapTo(y.absVec(6.5, 1, 4.5), 0, 0);
			helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, y.abs(6, 1, 3)) == 0, "the spot behind the wall is not dark");
			helper.assertFalse(planner.causedBy(y.player, level.damageSources().mobAttack(outside)), "a zombie born behind the wall was claimed");
			helper.assertTrue(planner.causedBy(y.player, level.damageSources().mobAttack(inside)), "a zombie born in the dark room was not claimed");
			inside.discard();
			outside.discard();
			helper.succeed();
		});
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 60)
	public void darkCornerEndsByTheClockAndComesBackWhenLoaded(GameTestHelper helper) {
		Yard y = darkBase(helper);
		ServerLevel level = y.level;
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		ArmedTrap trap = planner.arm(y.player, Traps.DARK_CORNER, true).trap();
		helper.assertTrue(trap != null, "not armed");
		long clock = level.getServer().overworld().getOverworldClockTime();
		helper.assertFalse(Traps.DARK_CORNER.expired(trap, y.now(), clock, y.cfg), "expired at once");
		helper.assertTrue(Traps.DARK_CORNER.expired(trap, y.now(), trap.clockUntil() + y.cfg.darkCornerGraceTicks() + 1, y.cfg),
				"still armed after the morning plus the grace, by the clock");

		// The morning passed while the base was not ticked: the window closes, and the torches wait to go back.
		y.data.setArmed(trap.withClockUntil(clock - y.cfg.darkCornerGraceTicks() - 10));
		planner.step(level.getServer(), y.data, null, y.cfg, y.now());
		helper.assertTrue(y.data.armed().isEmpty(), "the window did not close by the clock");
		ArmedTrap waiting = y.data.restoring().orElse(null);
		helper.assertTrue(waiting != null || torchesBack(y, trap), "the torches were dropped instead of put back");
		if (waiting != null) {
			planner.onChunkLoad(level, ChunkPos.containing(waiting.saved().get(0).pos()));
			planner.tick(level.getServer());
		}
		helper.assertTrue(y.data.restoring().isEmpty() && torchesBack(y, trap), "the torches did not come back once loaded");
		helper.assertFalse(planner.causedBy(y.player, level.damageSources().generic()), "a death after the window was claimed");
		y.succeedWithoutDrops();
	}

	private static boolean torchesBack(Yard y, ArmedTrap trap) {
		BlockPos off = trap.offPos().orElseThrow();
		if (!Scan.torch(y.level.getBlockState(off))) {
			return false;
		}
		for (ArmedTrap.SavedBlock torch : trap.saved().subList(1, trap.saved().size())) {
			if (!Scan.torch(y.level.getBlockState(torch.pos()))) {
				return false;
			}
		}
		return true;
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 100)
	public void houseFireCountsOnlyFireFromTheGap(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 20, 0, 20, Blocks.STONE);
		for (int x = 6; x <= 10; x++) {
			y.placed(x, 1, 8, Blocks.OAK_PLANKS);
			y.placed(x, 2, 8, Blocks.OAK_PLANKS);
		}
		y.fill(7, 1, 9, 9, 2, 11, Blocks.STONE);
		y.set(8, 1, 10, Blocks.LAVA);
		ServerPlayer player = y.player;
		player.snapTo(y.absVec(8.5, 1, 5.5), 0, 0);
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		AccidentPlannerImpl.ArmResult armed = planner.arm(player, Traps.HOUSE_FIRE, true);
		helper.assertTrue(armed.armed() && armed.trap().pos().equals(y.abs(8, 1, 9)), "the gap is not the stone by the wall: " + armed.message());
		DamageSources damage = level.damageSources();
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Scan.lava(level, y.abs(8, 1, 9)), "no lava came through the gap");
			// Fire far from the gap, in the same base: not his.
			y.set(2, 1, 2, Blocks.FIRE);
			player.snapTo(y.absVec(2.5, 1, 2.5), 0, 0);
			helper.assertFalse(planner.causedBy(player, damage.inFire()), "fire far from the gap was claimed");
			helper.assertFalse(planner.causedBy(player, damage.onFire()), "burning from fire far from the gap was claimed");
			// The pool behind the stone was always there.
			player.snapTo(y.absVec(8.5, 1, 10.5), 0, 0);
			helper.assertFalse(planner.causedBy(player, damage.lava()), "the old lava pool was claimed");
			// Lava that came through the gap, and fire right by it: his.
			player.snapTo(y.absVec(8.5, 1, 9.5), 0, 0);
			helper.assertTrue(planner.causedBy(player, damage.lava()), "lava through the gap was not claimed");
			y.set(10, 1, 6, Blocks.FIRE);
			player.snapTo(y.absVec(10.5, 1, 6.5), 0, 0);
			helper.assertTrue(planner.causedBy(player, damage.inFire()), "fire by the gap was not claimed");
			// Burning to death a few steps away after touching that fire.
			planner.watchBurns(player, y.data, y.cfg, y.now());
			player.snapTo(y.absVec(4.5, 1, 4.5), 0, 0);
			helper.assertTrue(planner.causedBy(player, damage.onFire()), "burning after the traced fire was not claimed");
			helper.assertFalse(planner.causedBy(player, damage.fall()), "a fall was claimed by the house fire");
			y.data.tracedBurnTick = Long.MIN_VALUE;
			helper.assertFalse(planner.causedBy(player, damage.onFire()), "burning with no traced fire was claimed");
			helper.assertFalse(HouseFireTrap.touchesTraced(level, armed.trap(), player.getBoundingBox(), y.cfg), "nothing traced where nothing burns");
			y.set(2, 1, 2, Blocks.AIR);
			y.set(10, 1, 6, Blocks.AIR);
			helper.succeed();
		});
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void noBedOnlyForTheSleeplessAfter(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 12, 0, 12, Blocks.STONE);
		BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		y.set(5, 1, 5, foot);
		y.set(5, 1, 6, foot.setValue(BedBlock.PART, BedPart.HEAD));
		ServerPlayer owner = y.player;
		owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), y.abs(5, 1, 5), 0, 0), false), false);
		owner.snapTo(y.absVec(200.5, 1, 6.5), 0, 0);
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);

		owner.getStats().setValue(owner, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 30000);
		helper.assertTrue(Traps.NO_BED.candidates(y.ctx(Yard.NOBODY, owner.blockPosition(), owner)).isEmpty(), "took the bed of a player who had not slept");
		helper.assertFalse(planner.arm(owner, Traps.NO_BED, true).armed(), "armed for a player who had not slept");

		owner.getStats().setValue(owner, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 1000);
		Phantom before = helper.spawn(EntityTypes.PHANTOM, new BlockPos(8, 6, 8));
		AccidentPlannerImpl.ArmResult armed = planner.arm(owner, Traps.NO_BED, true);
		helper.assertTrue(armed.armed(), "the bed was not taken: " + armed.message());
		owner.snapTo(y.absVec(8.5, 1, 8.5), 0, 0);
		Phantom after = helper.spawn(EntityTypes.PHANTOM, new BlockPos(8, 6, 8));
		planner.onSpawned(level, after, owner);
		Phantom farAway = helper.spawn(EntityTypes.PHANTOM, new BlockPos(8, 6, 8));
		farAway.teleportTo(farAway.getX() + 100, farAway.getY(), farAway.getZ());
		planner.onSpawned(level, farAway, owner);

		DamageSources damage = level.damageSources();
		helper.assertTrue(planner.causedBy(owner, damage.mobAttack(after)), "a phantom of the sleepless nights after was not claimed");
		helper.assertFalse(planner.causedBy(owner, damage.mobAttack(before)), "a phantom from before the bed went was claimed");
		helper.assertFalse(planner.causedBy(owner, damage.mobAttack(farAway)), "a phantom that appeared far away was claimed");
		ArmedTrap trap = y.data.armed().orElseThrow();
		owner.getStats().setValue(owner, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 100);
		helper.assertFalse(NoBedTrap.sleeplessSinceTaken(owner, trap, trap.setAt() + 5000), "a player who slept after the bed went still counts as sleepless");
		owner.getStats().setValue(owner, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 6000);
		helper.assertTrue(NoBedTrap.sleeplessSinceTaken(owner, trap, trap.setAt() + 5000), "a player who never slept since does not count");
		before.discard();
		after.discard();
		farAway.discard();
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void markPreviewRecordsNothing(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(0, 1, 0, 23, 1, 23, Blocks.DIRT);
		DeathMarkerImpl marker = new DeathMarkerImpl(server -> y.data, Yard.NOBODY);
		int deaths = HerobrineState.get(level.getServer()).markedDeaths().size();
		helper.assertTrue(marker.preview(y.player, "fell", y.abs(12, 2, 12)), "the preview cross was not built");
		helper.assertTrue(HerobrineState.get(level.getServer()).markedDeaths().size() == deaths, "a preview recorded a marked death");
		helper.assertTrue(y.data.crosses().isEmpty(), "the preview cross is still waiting");
		helper.assertTrue(DeathCauses.KNOWN.containsAll(List.of("fell", "lava", "drowned", "suffocated", "froze", "burned", "crushed")), "list words");
		y.succeedWithoutDrops();
	}

	@GameTest
	public void spawnHookIsWoven(GameTestHelper helper) {
		boolean woven = Arrays.stream(ServerLevel.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("a1016$onFreshEntity"));
		helper.assertTrue(woven, "the spawn hook mixin is not applied");
		helper.succeed();
	}
}
