package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.trap.DarkCornerTrap;
import com.forzacode.a1016_02.accident.trap.MovedMobTrap;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;

/**
 * One game test per trap: build the setup the world would already have, find the spot, check that nothing changes
 * while it is in view, then set it out of view and check the single removal or move, the ledger and the clue.
 */
public class TrapGameTests {
	/** A stone mass with a 2-high tunnel along x at y 5..6, z 8, walked underground. */
	private static void tunnel(Yard y) {
		y.fill(0, 0, 4, 20, 9, 12, Blocks.STONE);
		y.fill(1, 5, 8, 18, 6, 8, Blocks.AIR);
		for (int x = 1; x <= 18; x++) {
			y.walk(x, 5, 8, RouteBook.UNDER);
		}
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void lavaFloor(GameTestHelper helper) {
		Yard y = new Yard(helper);
		tunnel(y);
		y.set(10, 3, 8, Blocks.LAVA);
		BlockPos floor = y.abs(10, 4, 8);
		List<Candidate> found = Traps.LAVA_FLOOR.candidates(y.ctx(Yard.NOBODY, y.abs(3, 5, 8)));
		Candidate spot = y.taking(found, floor);
		helper.assertTrue(spot.taken().equals(List.of(floor)), "lava floor takes more than the one floor block");
		// Standing in the tunnel looking down it: in view. Turned around: out of view.
		y.checkPreset(Traps.LAVA_FLOOR, spot, y.viewer(3.5, 5, 8.5, -90, 0), y.viewer(3.5, 5, 8.5, 90, 0), y.abs(3, 5, 8));
		helper.assertTrue(Scan.lava(y.level, floor.below()), "the lava pocket is gone");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void lavaWall(GameTestHelper helper) {
		Yard y = new Yard(helper);
		tunnel(y);
		y.set(10, 5, 10, Blocks.LAVA);
		// Lava behind a wall with ore around it is not used: the gap would look mined.
		y.set(14, 6, 10, Blocks.LAVA);
		y.set(14, 7, 9, Blocks.COAL_ORE);
		BlockPos wall = y.abs(10, 5, 9);
		List<Candidate> found = Traps.LAVA_WALL.candidates(y.ctx(Yard.NOBODY, y.abs(3, 5, 8)));
		Candidate spot = y.taking(found, wall);
		helper.assertTrue(found.stream().noneMatch(c -> c.pos.equals(y.abs(14, 6, 9))), "used a wall with ore around it");
		y.checkPreset(Traps.LAVA_WALL, spot, y.viewer(9.5, 5, 8.5, 0, 0), Yard.NOBODY, y.abs(3, 5, 8));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void houseFire(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 20, 0, 20, Blocks.STONE);
		for (int x = 6; x <= 10; x++) {
			y.placed(x, 1, 8, Blocks.OAK_PLANKS);
			y.placed(x, 2, 8, Blocks.OAK_PLANKS);
		}
		y.fill(7, 1, 9, 9, 2, 11, Blocks.STONE);
		y.set(8, 1, 10, Blocks.LAVA);
		BlockPos stone = y.abs(8, 1, 9);
		Candidate spot = y.taking(Traps.HOUSE_FIRE.candidates(y.ctx(Yard.NOBODY, y.abs(8, 1, 6))), stone);
		y.checkPreset(Traps.HOUSE_FIRE, spot, y.viewer(8.5, 1, 6.5, 0, 0), Yard.NOBODY, y.abs(8, 1, 6));
		helper.assertTrue(y.state(8, 1, 8).is(Blocks.OAK_PLANKS), "the wall itself was touched");
		y.succeedWithoutDrops();
	}

	/** A 1x1 shaft at x 5, z 5 from y 1 to 7, a stone cap at y 8 and gravel above it. */
	private static void gravelShaft(Yard y) {
		y.fill(3, 0, 3, 7, 12, 7, Blocks.STONE);
		y.fill(5, 1, 5, 5, 7, 5, Blocks.AIR);
		y.fill(5, 9, 5, 5, 11, 5, Blocks.GRAVEL);
		for (int h = 1; h <= 7; h++) {
			y.walk(5, h, 5, RouteBook.UNDER);
		}
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void gravelCeilingWaitsForTheCoreOptIn(GameTestHelper helper) {
		Yard y = new Yard(helper);
		gravelShaft(y);
		BlockPos cap = y.abs(5, 8, 5);
		Candidate spot = y.taking(Traps.GRAVEL_CEILING.candidates(y.ctx(Yard.NOBODY, y.abs(5, 1, 5))), cap);
		helper.assertTrue(Traps.GRAVEL_CEILING.live(), "gravel ceiling should be live");
		// Core refuses taking the cap under gravel; the trap never bypasses that.
		helper.assertFalse(Services.traces().remove(y.level, cap, "test:gravel"), "core let gravel lose its support");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.GRAVEL_CEILING, y.ctx(Yard.NOBODY, cap), spot, y.cfg);
		ServerPlayer climber = y.player;
		climber.snapTo(y.absVec(5.5, 1, 5.5), 0, 0);
		ArmedTrap after = Traps.GRAVEL_CEILING.tick(y.ctx(Yard.NOBODY, cap, climber), armed);
		helper.assertTrue(after != null && !after.isSet(), "sprang without the core opt-in");
		helper.assertTrue(CoreGaps.FALLING_OPT_IN || Traps.GRAVEL_CEILING.blocked() != null, "blocked() must say why while the opt-in is missing");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 8, 5));
		helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(5, 11, 5));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void fallingDripstoneWaitsForTheCoreOptIn(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(2, 0, 2, 8, 0, 8, Blocks.STONE);
		y.fill(2, 12, 2, 8, 12, 8, Blocks.STONE);
		y.set(5, 11, 5, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN));
		y.walk(5, 1, 5, RouteBook.UNDER);
		BlockPos holder = y.abs(5, 12, 5);
		Candidate spot = y.taking(Traps.FALLING_DRIPSTONE.candidates(y.ctx(Yard.NOBODY, y.abs(5, 1, 5))), holder);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.FALLING_DRIPSTONE, y.ctx(Yard.NOBODY, holder), spot, y.cfg);
		ServerPlayer walker = y.player;
		walker.snapTo(y.absVec(5.5, 1, 5.5), 0, 0);
		ArmedTrap after = Traps.FALLING_DRIPSTONE.tick(y.ctx(Yard.NOBODY, holder, walker), armed);
		helper.assertTrue(after != null && !after.isSet(), "sprang without the core opt-in");
		helper.assertBlockPresent(Blocks.POINTED_DRIPSTONE, new BlockPos(5, 11, 5));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 12, 5));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void missingRung(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(5, 0, 5, 5, 0, 5, Blocks.STONE);
		y.fill(5, 1, 6, 5, 12, 6, Blocks.STONE);
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
		for (int h = 1; h <= 10; h++) {
			y.placed(5, h, 5, ladder);
		}
		BlockPos rung = y.abs(5, 6, 5);
		Candidate spot = y.taking(Traps.MISSING_RUNG.candidates(y.ctx(Yard.NOBODY, y.abs(5, 1, 3))), rung);
		y.checkPreset(Traps.MISSING_RUNG, spot, y.viewer(5.5, 5, 3.5, 0, 0), Yard.NOBODY, y.abs(5, 1, 3));
		helper.assertBlockPresent(Blocks.LADDER, new BlockPos(5, 5, 5));
		helper.assertBlockPresent(Blocks.LADDER, new BlockPos(5, 7, 5));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void shortBridge(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 6, 18, 0, 10, Blocks.STONE);
		y.fill(1, 1, 7, 3, 12, 9, Blocks.STONE);
		y.fill(15, 1, 7, 17, 12, 9, Blocks.STONE);
		for (int x = 4; x <= 14; x++) {
			y.placed(x, 12, 8, Blocks.OAK_PLANKS);
		}
		List<Candidate> found = Traps.SHORT_BRIDGE.candidates(y.ctx(Yard.NOBODY, y.abs(2, 13, 8)));
		helper.assertTrue(found.size() == 2, "expected the two bridge ends, got " + found.size());
		Candidate spot = y.taking(found, y.abs(4, 12, 8));
		y.checkPreset(Traps.SHORT_BRIDGE, spot, y.viewer(2.5, 13, 8.5, -90, 30), Yard.NOBODY, y.abs(2, 13, 8));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 12, 8));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 12, 8));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 60)
	public void floodedTunnelSpringsBehindThePlayer(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 4, 20, 9, 12, Blocks.STONE);
		y.fill(1, 3, 8, 18, 4, 8, Blocks.AIR);
		y.fill(6, 6, 6, 10, 7, 10, Blocks.WATER);
		for (int x = 1; x <= 18; x++) {
			y.walk(x, 3, 8, RouteBook.UNDER);
		}
		BlockPos plug = y.abs(8, 5, 8);
		Candidate spot = y.taking(Traps.FLOODED_TUNNEL.candidates(y.ctx(Yard.NOBODY, y.abs(1, 3, 8))), plug);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.FLOODED_TUNNEL, y.ctx(Yard.NOBODY, plug), spot, y.cfg);
		helper.assertTrue(!armed.isSet() && helper.getLevel().getBlockState(plug).is(Blocks.STONE), "arming a live trap changed the world");

		ServerPlayer walker = y.player;
		walker.snapTo(y.absVec(8.5, 3, 8.5), 0, 0);
		ArmedTrap near = Traps.FLOODED_TUNNEL.tick(y.ctx(Yard.NOBODY, plug, walker), armed);
		helper.assertTrue(near != null && !near.isSet(), "sprang right next to the player");

		// Deep in the tunnel, facing the gap: in view, nothing happens.
		y.face(walker, 1.5, 3, 8.5, -90, 0);
		ViewGate facing = Yard.eyesOf(walker);
		ArmedTrap seen = Traps.FLOODED_TUNNEL.tick(y.ctx(facing, plug, walker), armed);
		helper.assertTrue(seen != null && !seen.isSet() && helper.getLevel().getBlockState(plug).is(Blocks.STONE), "flooded while the player looked at it");

		// Turned away: the one block goes and the water does the rest.
		y.face(walker, 1.5, 3, 8.5, 90, 0);
		ViewGate away = Yard.eyesOf(walker);
		ArmedTrap sprung = Traps.FLOODED_TUNNEL.tick(y.ctx(away, plug, walker), armed);
		helper.assertTrue(sprung != null && sprung.isSet(), "did not spring behind the player");
		helper.assertTrue(sprung.targets().equals(List.of(plug)), "took more than the one block");
		helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 5, 8));
		helper.assertTrue(y.ledgered("accident:flooded_tunnel", Set.of(plug)) == 1, "not in the ledger");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void darkCornerGoesDarkAndComesBackOneOff(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 16, 0, 16, Blocks.STONE);
		int[][] torches = {{1, 1}, {2, 1}, {1, 2}, {8, 8}, {12, 12}, {12, 4}, {4, 12}, {8, 3}};
		for (int[] t : torches) {
			y.placed(t[0], 1, t[1], Blocks.TORCH);
		}
		BlockPos base = y.abs(8, 1, 8);
		List<Candidate> found = Traps.DARK_CORNER.candidates(y.ctx(Yard.NOBODY, base));
		helper.assertFalse(found.isEmpty(), "no dark corner found");
		Candidate spot = found.get(0);
		helper.assertTrue(spot.off().isPresent() && !spot.saved.isEmpty(), "no torch to put back a block off");
		helper.assertTrue(torches.length - spot.saved.size() >= y.cfg.darkCornerKeepLit, "the whole base went dark");
		y.checkPreset(Traps.DARK_CORNER, spot, y.viewer(spot.pos.getX() - y.abs(0, 0, 0).getX() + 1.5, 1, spot.pos.getZ() - y.abs(0, 0, 0).getZ() + 0.5, 0, 0),
				Yard.NOBODY, base);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.DARK_CORNER, y.ctx(Yard.NOBODY, base), spot, y.cfg);

		helper.assertFalse(DarkCornerTrap.restore(y.level, y.ctx(Yard.EVERYONE, base), armed), "put back while in view");
		for (ArmedTrap.SavedBlock torch : armed.saved()) {
			helper.assertTrue(y.level.getBlockState(torch.pos()).isAir(), "a torch came back in view");
		}
		helper.assertTrue(DarkCornerTrap.restore(y.level, y.ctx(Yard.NOBODY, base), armed), "not put back out of view");
		ArmedTrap.SavedBlock moved = armed.saved().get(0);
		BlockPos off = armed.offPos().orElseThrow();
		helper.assertTrue(y.level.getBlockState(off).is(moved.state().getBlock()), "the moved torch is not a block off");
		helper.assertTrue(y.level.getBlockState(moved.pos()).isAir(), "the moved torch is also back in place");
		for (ArmedTrap.SavedBlock torch : armed.saved().subList(1, armed.saved().size())) {
			helper.assertTrue(y.level.getBlockState(torch.pos()) == torch.state(), "torch at " + torch.pos() + " not back");
		}
		helper.assertTrue(y.ledgered("accident:dark_corner", Set.of(moved.pos())) == 1, "the moved torch should stay in the ledger");
		List<BlockPos> exact = armed.saved().subList(1, armed.saved().size()).stream().map(ArmedTrap.SavedBlock::pos).toList();
		helper.assertTrue(exact.isEmpty() || y.ledgered("accident:dark_corner", Set.copyOf(exact)) == 0, "torches put back exactly are still in the ledger");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 60)
	public void movedMobGoesIntoTheClosedHouse(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		// The house sits so that the door search around the base stays inside this test's yard.
		y.fill(6, 1, 6, 16, 4, 16, Blocks.STONE);
		y.fill(7, 1, 7, 15, 4, 15, Blocks.AIR);
		y.fill(6, 5, 6, 16, 5, 16, Blocks.STONE);
		y.set(11, 1, 6, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		y.set(11, 2, 6, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		ServerPlayer owner = y.player;
		owner.snapTo(y.absVec(22.5, 1, 1.5), 0, 0);
		owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), y.abs(11, 1, 11), 0, 0), false), false);
		// The test harness puts a creeper outside, as the night would. The mod never spawns one.
		Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(22, 1, 22));
		int creepers = level.getEntitiesOfClass(Creeper.class, new AABB(y.abs(0, 0, 0)).inflate(80)).size();
		List<Candidate> found = Traps.MOVED_MOB.candidates(y.ctx(Yard.NOBODY, owner.blockPosition(), owner));
		Candidate spot = found.stream().filter(c -> c.mob == creeper).findFirst().orElse(null);
		helper.assertTrue(spot != null, "the creeper outside was not picked: " + found.size() + " candidates, doors shut "
				+ MovedMobTrap.doorsShut(level, y.abs(11, 1, 11)) + ", inside " + MovedMobTrap.interior(level, y.abs(11, 1, 11)).size()
				+ ", creeper alive " + creeper.isAlive() + " at " + creeper.position());
		if (Services.mobs() instanceof MobTamper.Stub) {
			helper.assertTrue(Traps.MOVED_MOB.blocked() != null, "moved mob must be skipped while MobTamper is a stub");
			helper.assertFalse(Traps.MOVED_MOB.setup(y.ctx(Yard.NOBODY, owner.blockPosition(), owner), spot), "moved a mob through the stub");
			helper.succeed();
			return;
		}
		helper.assertFalse(Traps.MOVED_MOB.setup(y.ctx(Yard.EVERYONE, owner.blockPosition(), owner), spot), "moved a mob in view");
		helper.assertTrue(creeper.blockPosition().equals(y.abs(22, 1, 22)), "a refused move moved the creeper");
		helper.assertTrue(Traps.MOVED_MOB.setup(y.ctx(Yard.NOBODY, owner.blockPosition(), owner), spot), "move refused out of view");
		AABB inside = new AABB(net.minecraft.world.phys.Vec3.atCenterOf(y.abs(7, 1, 7)), net.minecraft.world.phys.Vec3.atCenterOf(y.abs(15, 4, 15))).inflate(0.5);
		helper.assertTrue(inside.contains(creeper.position()), "the creeper is not in the house: " + creeper.position());
		helper.assertFalse(level.getBlockState(y.abs(11, 1, 6)).getValue(DoorBlock.OPEN), "the door was opened");
		helper.assertTrue(level.getEntitiesOfClass(Creeper.class, new AABB(y.abs(0, 0, 0)).inflate(80)).size() == creepers, "a creeper was spawned");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.MOVED_MOB, y.ctx(Yard.NOBODY, owner.blockPosition(), owner), spot, y.cfg);
		helper.assertTrue(armed.mob().filter(id -> id.equals(creeper.getUUID())).isPresent(), "the moved mob is not remembered");
		helper.assertTrue(Traps.MOVED_MOB.matches(level.damageSources().explosion(creeper, creeper), armed), "its explosion does not count");
		creeper.discard();
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void noBedWhileOut(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 10, 0, 10, Blocks.STONE);
		BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		y.set(5, 1, 5, foot);
		y.set(5, 1, 6, foot.setValue(BedBlock.PART, BedPart.HEAD));
		ServerPlayer owner = y.player;
		owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(y.level.dimension(), y.abs(5, 1, 5), 0, 0), false), false);
		owner.snapTo(y.absVec(6.5, 1, 6.5), 0, 0);
		helper.assertTrue(Traps.NO_BED.candidates(y.ctx(Yard.NOBODY, owner.blockPosition(), owner)).isEmpty(), "took the bed with the player beside it");
		owner.snapTo(y.absVec(200.5, 1, 6.5), 0, 0);
		List<Candidate> found = Traps.NO_BED.candidates(y.ctx(Yard.NOBODY, owner.blockPosition(), owner));
		Candidate spot = y.taking(found, y.abs(5, 1, 5));
		y.checkPreset(Traps.NO_BED, spot, y.viewer(7.5, 1, 5.5, 90, 0), Yard.NOBODY, owner.blockPosition());
		helper.assertBlockNotPresent(Blocks.BED.white(), new BlockPos(5, 1, 6));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void powderSnowOnYesterdaysPath(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 15, 0, 15, Blocks.SNOW_BLOCK);
		y.set(10, 0, 10, Blocks.POWDER_SNOW);
		y.set(12, 0, 10, Blocks.POWDER_SNOW);
		y.walk(5, 1, 5, 0, 1, y.today());
		helper.assertTrue(Traps.POWDER_SNOW.candidates(y.ctx(Yard.NOBODY, y.abs(5, 1, 5))).isEmpty(), "used a path first walked today");
		y.walk(5, 1, 6, 0, 1, y.today() - 1);
		BlockPos path = y.abs(5, 1, 6);
		Candidate spot = Traps.POWDER_SNOW.candidates(y.ctx(Yard.NOBODY, path)).stream().filter(c -> c.pos.equals(path)).findFirst().orElseThrow();
		y.checkPreset(Traps.POWDER_SNOW, spot, y.viewer(5.5, 1, 4.5, 0, 0), Yard.NOBODY, path);
		helper.assertBlockPresent(Blocks.POWDER_SNOW, new BlockPos(5, 1, 6));
		helper.assertBlockPresent(Blocks.POWDER_SNOW, new BlockPos(5, 2, 6));
		helper.assertBlockNotPresent(Blocks.POWDER_SNOW, new BlockPos(10, 0, 10));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void bareWoolLeavesOneGap(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 12, 0, 12, Blocks.STONE);
		for (int x = 3; x <= 7; x++) {
			y.placed(x, 1, 5, Blocks.CARPET.white());
		}
		y.set(5, 1, 9, Blocks.SCULK_SENSOR);
		List<Candidate> found = Traps.BARE_WOOL.candidates(y.ctx(Yard.NOBODY, y.abs(1, 1, 5)));
		helper.assertTrue(found.size() == 3, "expected the 3 inner carpets of the line, got " + found.size());
		Candidate spot = found.get(0);
		helper.assertTrue(spot.pos.equals(y.abs(4, 1, 5)), "not the nearest gap: " + spot.pos);
		y.checkPreset(Traps.BARE_WOOL, spot, y.viewer(2.5, 1, 5.5, -90, 20), Yard.NOBODY, y.abs(1, 1, 5));
		helper.assertBlockPresent(Blocks.CARPET.white(), new BlockPos(3, 1, 5));
		helper.assertBlockPresent(Blocks.CARPET.white(), new BlockPos(5, 1, 5));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void cairnRouteGoesHollowByTheThirdVisit(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 10, 0, 10, Blocks.STONE);
		y.fill(3, 10, 3, 7, 10, 7, Blocks.STONE);
		y.walk(5, 11, 5, 0, 2, y.today());
		y.data.setCairn(net.minecraft.core.GlobalPos.of(y.level.dimension(), y.abs(20, 1, 20)));
		y.data.addCairnVisit();
		helper.assertTrue(Traps.LURE_CAIRN.candidates(y.ctx(Yard.NOBODY, y.abs(5, 11, 5))).isEmpty(), "hollow after one visit");
		y.data.addCairnVisit();
		y.data.atCairn = true;
		helper.assertTrue(Traps.LURE_CAIRN.candidates(y.ctx(Yard.NOBODY, y.abs(5, 11, 5))).isEmpty(), "set while the player is at the cairn");
		y.data.atCairn = false;
		BlockPos ground = y.abs(5, 10, 5);
		Candidate spot = y.taking(Traps.LURE_CAIRN.candidates(y.ctx(Yard.NOBODY, y.abs(5, 11, 5))), ground);
		y.checkPreset(Traps.LURE_CAIRN, spot, y.viewer(5.5, 11, 3.5, 0, 30), Yard.NOBODY, y.abs(5, 11, 5));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 10, 5));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void groveTakesThePillarWhileYouPlaceLeaves(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 14, 0, 14, Blocks.STONE);
		for (int h = 1; h <= 9; h++) {
			y.placed(5, h, 5, Blocks.DIRT);
		}
		ServerPlayer climber = y.player;
		climber.snapTo(y.absVec(5.5, 10, 5.5), 0, 0);
		climber.setOnGround(true);
		BlockPos grove = y.abs(5, 1, 5);
		Candidate watch = Candidate.of(grove, List.of(), grove.offset(-10, -2, -10), grove.offset(10, 20, 10), "test grove");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.LURE_GROVE, y.ctx(Yard.NOBODY, grove, climber), watch, y.cfg);

		ArmedTrap idle = Traps.LURE_GROVE.tick(y.ctx(Yard.NOBODY, grove, climber), armed);
		helper.assertTrue(idle != null && !idle.isSet(), "sprang while no leaves were being placed");
		y.data.lure.groveLeavesTick = y.now();
		ArmedTrap seen = Traps.LURE_GROVE.tick(y.ctx(y.viewer(10.5, 1, 5.5, 90, -20), grove, climber), armed);
		helper.assertTrue(seen != null && !seen.isSet(), "took the pillar in view");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 3, 5));

		ArmedTrap sprung = Traps.LURE_GROVE.tick(y.ctx(Yard.NOBODY, grove, climber), armed);
		helper.assertTrue(sprung != null && sprung.isSet(), "did not spring while placing the last leaves");
		for (int h = 1; h <= 6; h++) {
			helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(5, h, 5));
		}
		for (int h = 7; h <= 9; h++) {
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, h, 5));
		}
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void whiteEyesTorchesGoOutWhileDisc13Plays(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 20, 0, 20, Blocks.STONE);
		for (int[] t : new int[][] {{4, 4}, {16, 4}, {4, 16}}) {
			y.placed(t[0], 1, t[1], Blocks.TORCH);
		}
		y.set(10, 1, 10, Blocks.JUKEBOX);
		BlockPos room = y.abs(10, 3, 10);
		ServerPlayer listener = y.player;
		listener.snapTo(y.absVec(10.5, 1, 12.5), 0, 0);
		Candidate watch = Candidate.around(room, List.of(), y.cfg.whiteEyesRoomRadius + 4, 0, "test room");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.LURE_WHITE_EYES, y.ctx(Yard.NOBODY, room, listener), watch, y.cfg);
		long t0 = 1000;
		helper.assertTrue(tick(y, armed, room, listener, t0, Yard.NOBODY) == armed, "acted with no disc playing");

		JukeboxBlockEntity jukebox = (JukeboxBlockEntity) level.getBlockEntity(y.abs(10, 1, 10));
		jukebox.setSongItemWithoutPlaying(new ItemStack(Items.MUSIC_DISC_13));
		Holder<JukeboxSong> thirteen = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getOrThrow(JukeboxSongs.THIRTEEN);
		jukebox.getSongPlayer().play(level, thirteen);
		ArmedTrap started = tick(y, armed, room, listener, t0, Yard.NOBODY);
		helper.assertTrue(started.step() == 0 && torches(y) == 3, "a torch went out at the first note");
		long interval = Math.max(1, y.cfg.whiteEyesTrackTicks() / 4);
		ArmedTrap seen = tick(y, started, room, listener, t0 + interval, Yard.EVERYONE);
		helper.assertTrue(seen.step() == 0 && torches(y) == 3, "a torch went out in view");
		ArmedTrap one = tick(y, started, room, listener, t0 + interval, Yard.NOBODY);
		helper.assertTrue(one.isSet() && one.step() == 1 && torches(y) == 2, "the first torch did not go out");
		ArmedTrap two = tick(y, one, room, listener, t0 + 2 * interval + 1, Yard.NOBODY);
		helper.assertTrue(two.step() == 2 && torches(y) == 1, "the second torch did not go out");
		jukebox.getSongPlayer().stop(level, level.getBlockState(y.abs(10, 1, 10)));
		y.succeedWithoutDrops();
	}

	private static ArmedTrap tick(Yard y, ArmedTrap armed, BlockPos room, ServerPlayer player, long now, ViewGate view) {
		TrapContext ctx = new TrapContext(y.level, player, room, y.data, view, y.cfg, now, y.today());
		ArmedTrap next = Traps.LURE_WHITE_EYES.tick(ctx, armed);
		return next == null ? armed : next;
	}

	private static int torches(Yard y) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 1, 0), y.abs(20, 1, 20))) {
			n += Scan.torch(y.level.getBlockState(pos)) ? 1 : 0;
		}
		return n;
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void sleepingNearHisPlacesTakesTheFloor(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(2, 9, 2, 21, 9, 21, Blocks.STONE);
		BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		y.set(12, 10, 12, foot);
		y.set(12, 10, 13, foot.setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos bed = y.abs(12, 10, 12);
		ServerPlayer sleeper = y.player;
		Candidate watch = Candidate.around(bed, List.of(), y.cfg.sleepSiteRadius, 16, "test pyramid");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.LURE_SLEEP, y.ctx(Yard.NOBODY, bed, sleeper), watch, y.cfg);
		sleeper.snapTo(y.absVec(12.5, 10, 9.5), 0, 0);
		ArmedTrap awake = Traps.LURE_SLEEP.tick(y.ctx(Yard.NOBODY, bed, sleeper), armed);
		helper.assertTrue(awake != null && !awake.isSet(), "took the floor while the player was awake");
		sleeper.startSleeping(bed);
		helper.assertTrue(sleeper.isSleeping(), "the test player is not asleep");
		ArmedTrap seen = Traps.LURE_SLEEP.tick(y.ctx(Yard.EVERYONE, bed, sleeper), armed);
		helper.assertTrue(seen != null && !seen.isSet(), "took the floor in view");
		ArmedTrap sprung = Traps.LURE_SLEEP.tick(y.ctx(Yard.NOBODY, bed, sleeper), armed);
		helper.assertTrue(sprung != null && sprung.isSet() && !sprung.targets().isEmpty(), "the floor is still there when you wake");
		for (BlockPos pos : sprung.targets()) {
			double d = Math.sqrt(Scan.horizontalDistSqr(pos, bed));
			helper.assertTrue(d >= y.cfg.sleepFloorMinDistance && y.level.getBlockState(pos).isAir(), "took " + pos + " at " + d);
		}
		helper.assertBlockPresent(Blocks.BED.white(), new BlockPos(12, 10, 12));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(12, 9, 14));
		sleeper.stopSleeping();
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 20)
	public void luresWithoutTheirPlacesHaveNoSpot(GameTestHelper helper) {
		Yard y = new Yard(helper);
		BlockPos here = y.abs(5, 1, 5);
		net.minecraft.core.GlobalPos near = net.minecraft.core.GlobalPos.of(y.level.dimension(), here);
		helper.assertTrue(Traps.LURE_CAIRN.candidates(y.ctx(Yard.NOBODY, here)).isEmpty(), "a cairn trap with no cairn");
		if (Lures.grove(near, y.cfg.groveSearchRadius).isEmpty()) {
			helper.assertTrue(Traps.LURE_GROVE.candidates(y.ctx(Yard.NOBODY, here)).isEmpty(), "a grove trap with no grove");
		}
		if (Lures.room(y.level.getServer()).isEmpty()) {
			helper.assertTrue(Traps.LURE_WHITE_EYES.candidates(y.ctx(Yard.NOBODY, here)).isEmpty(), "a white eyes trap with no room");
		}
		if (Lures.hisPlaces(y.level.getServer(), near, y.cfg.sleepSiteRadius).isEmpty()) {
			helper.assertTrue(Traps.LURE_SLEEP.candidates(y.ctx(Yard.NOBODY, here)).isEmpty(), "a sleep trap with none of his places");
		}
		helper.succeed();
	}

	/** Used by the planner tests: the lava floor setup, walked, with a player in the tunnel. */
	static Yard lavaTunnel(GameTestHelper helper) {
		Yard y = new Yard(helper);
		tunnel(y);
		y.set(10, 3, 8, Blocks.LAVA);
		y.player.snapTo(y.absVec(3.5, 5, 8.5), 90, 0);
		return y;
	}
}
