package com.forzacode.a1016_02.accident;

import java.util.List;

import com.forzacode.a1016_02.accident.trap.Bridges;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * D-034 out of the overworld, in the test server's flat End and Nether: the footprint and the routes work there, a
 * block of your own bridge goes missing over the void or a lava lake, and an enderman is moved onto your 1-wide bridge.
 * Each: the setup, the single change, the clue, nothing in view, and which deaths count.
 */
public class BridgeGameTests extends SwordGameTests {
	@GameTest(maxTicks = 40)
	public void footprintAndRoutesWorkInTheEndAndTheNether(GameTestHelper helper) {
		for (var dimension : List.of(Level.END, Level.NETHER)) {
			Away a = new Away(helper, dimension, 40);
			BlockPos first = a.at(0, 0, 0);
			BlockPos second = a.at(2, 0, 0);
			a.stand(0.5, 6, 6.5, 0, 0);
			// Placed the way a player places it: through the block item, so core's placement hook records it.
			for (BlockPos pos : List.of(first, a.at(1, 0, 0), second)) {
				BlockPlaceContext place = new BlockPlaceContext(a.player, InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE),
						new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
				InteractionResult result = ((BlockItem) Items.COBBLESTONE).place(place);
				helper.assertTrue(result.consumesAction() && a.level.getBlockState(pos).is(Blocks.COBBLESTONE), "could not place in " + dimension.identifier());
			}
			helper.assertTrue(Services.watch().wasPlacedByPlayer(a.level, first), "the footprint missed a block placed in " + dimension.identifier());
			helper.assertFalse(Services.watch().wasPlacedByPlayer(helper.getLevel(), first), "the overworld footprint got a block placed in " + dimension.identifier());
			helper.assertTrue(Services.watch().placedNear(a.level, first, 4, Blocks.COBBLESTONE).containsAll(List.of(first, second)), "placedNear in " + dimension.identifier());

			RouteSampler sampler = new RouteSampler();
			a.stand(0.5, 1, 0.5, -90, 0);
			a.player.setOnGround(true);
			sampler.sample(a.player, a.data, a.cfg, a.now(), a.today());
			a.stand(2.5, 1, 0.5, -90, 0);
			sampler.sample(a.player, a.data, a.cfg, a.now(), a.today());
			RouteBook.Point walked = a.data.routes().get(dimension, first.above());
			helper.assertTrue(walked != null && walked.has(RouteBook.ON_PLACED), "the route missed a walk on a placed block in " + dimension.identifier());
			helper.assertTrue(a.data.routes().contains(dimension, a.at(1, 1, 0)), "the step between two samples was not filled in, in " + dimension.identifier()
					+ ": walked " + a.data.routes().near(dimension, first, 4, 10).stream().map(spot -> spot.pos().subtract(a.origin).toShortString()).toList());
			helper.assertFalse(a.data.routes().contains(Level.OVERWORLD, first.above()), "the walk went into the overworld's routes");
			helper.assertTrue(dimension.equals(a.data.headingDimension) && a.data.heading != null && a.data.heading.x > 0.99, "no heading along the bridge");
			a.done();
		}
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void bridgeCardsOnlyFitTheirDimension(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel();
		ServerLevel end = overworld.getServer().getLevel(Level.END);
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		Yard y = new Yard(helper);
		AccidentConfig cfg = y.cfg;
		helper.assertTrue(Traps.VOID_BRIDGE.contextFits(y.player, end, cfg) && !Traps.VOID_BRIDGE.contextFits(y.player, nether, cfg)
				&& !Traps.VOID_BRIDGE.contextFits(y.player, overworld, cfg), "void_bridge fits outside the End");
		helper.assertTrue(Traps.ENDERMAN_ON_BRIDGE.contextFits(y.player, end, cfg) && !Traps.ENDERMAN_ON_BRIDGE.contextFits(y.player, nether, cfg)
				&& !Traps.ENDERMAN_ON_BRIDGE.contextFits(y.player, overworld, cfg), "enderman_on_bridge fits outside the End");
		helper.assertTrue(Traps.LAVA_BRIDGE.contextFits(y.player, nether, cfg) && !Traps.LAVA_BRIDGE.contextFits(y.player, end, cfg)
				&& !Traps.LAVA_BRIDGE.contextFits(y.player, overworld, cfg), "lava_bridge fits outside the Nether");

		// A bridge over an overworld lava lake: the shape fits, the card does not.
		y.fill(0, 0, 4, 22, 1, 12, Blocks.STONE);
		y.fill(1, 1, 6, 21, 1, 10, Blocks.LAVA);
		for (int x = 1; x <= 21; x++) {
			y.placed(x, 8, 8, Blocks.COBBLESTONE);
			y.walk(x, 9, 8, RouteBook.ON_PLACED);
		}
		BlockPos deck = y.abs(10, 8, 8);
		helper.assertTrue(Bridges.lavaBelow(overworld, deck, cfg.lavaBridgeMaxDrop, cfg.lavaLakeMinSources) != Integer.MIN_VALUE
				&& Bridges.spanAxis(overworld, deck) != null, "the overworld bridge is not over a lava lake");
		helper.assertTrue(Traps.LAVA_BRIDGE.candidates(y.ctx(Yard.NOBODY, y.abs(0, 9, 8))).isEmpty(), "lava_bridge found a spot in the overworld");

		// A bridge over a gap to the bottom of the Nether: the shape is a void bridge, the card does not fit there.
		Away a = new Away(helper, Level.NETHER, 40);
		a.openToTheBottom(-2, -2, 22, 2);
		a.bridge(0, 20);
		a.stand(-3.5, 1, 0.5, 90, 0);
		helper.assertTrue(Bridges.overVoid(a.level, a.at(10, 0, 0)), "the Nether bridge is not over a gap to the bottom");
		helper.assertTrue(Traps.VOID_BRIDGE.candidates(a.ctx(Yard.NOBODY)).isEmpty(), "void_bridge found a spot in the Nether");
		helper.assertTrue(Traps.ENDERMAN_ON_BRIDGE.candidates(a.ctx(Yard.NOBODY)).isEmpty(), "enderman_on_bridge found a spot in the Nether");
		a.done();
		y.fill(1, 1, 6, 21, 1, 10, Blocks.STONE);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void voidBridgeLosesOneBlockAheadOutOfView(GameTestHelper helper) {
		Away a = new Away(helper, Level.END, 40);
		a.openToTheBottom(-2, -2, 22, 2);
		a.bridge(0, 20);
		// On the bridge at x 2, walking east (+x).
		a.walking(new Vec3(1, 0, 0));
		a.stand(2.5, 1, 0.5, -90, 0);
		List<Candidate> found = Traps.VOID_BRIDGE.candidates(a.ctx(Yard.NOBODY));
		helper.assertFalse(found.isEmpty(), "no spot on the bridge over the void");
		Candidate spot = found.get(0);
		helper.assertTrue(spot.pos.equals(a.at(8, 0, 0)) && spot.taken().equals(List.of(spot.pos)), "not the nearest block ahead, alone: " + spot.pos);
		helper.assertTrue(found.stream().allMatch(c -> c.pos.getX() > a.player.getX()), "offered a block behind the player");
		helper.assertTrue(spot.clue.contains("1x1"), "the clue is not the 1x1 gap: " + spot.clue);
		// Walking back west: what was ahead is now behind, and nothing behind is offered.
		a.walking(new Vec3(-1, 0, 0));
		helper.assertTrue(Traps.VOID_BRIDGE.candidates(a.ctx(Yard.NOBODY)).isEmpty(), "offered a block behind the player");
		a.walking(new Vec3(1, 0, 0));

		// Looking along the bridge: in view, nothing changes.
		helper.assertFalse(Traps.VOID_BRIDGE.setup(a.ctx(a.eyes()), spot), "took a block the player was looking at");
		helper.assertTrue(a.level.getBlockState(spot.pos).is(Blocks.COBBLESTONE), "the block went in view");
		// Looking back the way they came (still walking east): the one block goes.
		a.stand(2.5, 1, 0.5, 90, 0);
		helper.assertTrue(Traps.VOID_BRIDGE.setup(a.ctx(a.eyes()), spot), "refused out of view");
		helper.assertTrue(a.level.getBlockState(spot.pos).isAir(), "the block is still there");
		for (int x : new int[] {7, 9}) {
			helper.assertTrue(a.level.getBlockState(a.at(x, 0, 0)).is(Blocks.COBBLESTONE), "the gap is wider than one block at x " + x);
		}
		List<TraceLedger.Entry> logged = TraceLedger.get(a.level.getServer()).entries().stream()
				.filter(e -> e.cause().startsWith("accident:void_bridge") && e.pos().dimension().equals(Level.END) && e.pos().pos().closerThan(a.origin, 40)).toList();
		helper.assertTrue(logged.size() == 1 && logged.getFirst().kind() == TraceLedger.Kind.REMOVE && logged.getFirst().pos().pos().equals(spot.pos),
				"not one ledgered removal: " + logged);

		// Which deaths count: out of the world after dropping through the gap.
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> a.data, Yard.NOBODY);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.VOID_BRIDGE, a.ctx(Yard.NOBODY), spot, a.cfg);
		a.data.setArmed(armed);
		DamageSources damage = a.level.damageSources();
		a.stand(8.5, -70 - a.origin.getY() + a.level.getMinY(), 0.5, 0, 0);
		helper.assertFalse(planner.causedBy(a.player, damage.fellOutOfWorld()), "a void death without a drop through the gap was claimed");
		a.stand(8.5, -1.5, 0.5, 0, 0);
		Traps.VOID_BRIDGE.watch(a.player, armed, a.data, a.cfg, a.now());
		helper.assertTrue(a.data.gapPassTick == a.now(), "the drop through the gap was not noticed");
		a.stand(8.5, -70 - a.origin.getY() + a.level.getMinY(), 0.5, 0, 0);
		helper.assertTrue(planner.causedBy(a.player, damage.fellOutOfWorld()), "falling out of the world through the gap was not claimed");
		helper.assertFalse(planner.causedBy(a.player, damage.drown()), "drowning was claimed by the void bridge");
		a.stand(60.5, -70 - a.origin.getY() + a.level.getMinY(), 0.5, 0, 0);
		helper.assertFalse(planner.causedBy(a.player, damage.fellOutOfWorld()), "a void death far from the gap was claimed");
		a.done();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void lavaBridgeLosesOneBlockOverTheLake(GameTestHelper helper) {
		Away a = new Away(helper, Level.NETHER, 12);
		ServerLevel level = a.level;
		// A lava lake sunk into the basalt floor (it cannot spread), the bridge 8 blocks over it.
		int floor = 3 - a.origin.getY();
		for (int x = -1; x <= 21; x++) {
			for (int z = -2; z <= 2; z++) {
				a.set(a.at(x, floor, z), Blocks.LAVA.defaultBlockState());
				for (int y = floor + 1; y < 0; y++) {
					a.set(a.at(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		a.bridge(0, 20);
		// Off the bridge, on the basalt west of the lake: the whole bridge is on the way back.
		a.stand(-5.5, floor + 1, 0.5, 90, 0);
		List<Candidate> found = Traps.LAVA_BRIDGE.candidates(a.ctx(Yard.NOBODY));
		helper.assertFalse(found.isEmpty(), "no spot on the bridge over the lava lake");
		Candidate spot = found.get(0);
		helper.assertTrue(spot.pos.equals(a.at(1, 0, 0)) && spot.taken().equals(List.of(spot.pos)), "not the nearest inner block alone: " + spot.pos);
		helper.assertTrue(spot.clue.contains("1x1") && spot.clue.contains("lava"), "the clue: " + spot.clue);
		// Looking up at the bridge: in view.
		a.stand(-5.5, floor + 1, 0.5, -90, -30);
		helper.assertFalse(Traps.LAVA_BRIDGE.setup(a.ctx(a.eyes()), spot), "took a block in view");
		helper.assertTrue(level.getBlockState(spot.pos).is(Blocks.COBBLESTONE), "the block went in view");
		a.stand(-5.5, floor + 1, 0.5, 90, 0);
		helper.assertTrue(Traps.LAVA_BRIDGE.setup(a.ctx(a.eyes()), spot), "refused out of view");
		helper.assertTrue(level.getBlockState(spot.pos).isAir() && level.getBlockState(a.at(0, 0, 0)).is(Blocks.COBBLESTONE)
				&& level.getBlockState(a.at(2, 0, 0)).is(Blocks.COBBLESTONE), "not a 1x1 gap");

		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> a.data, Yard.NOBODY);
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.LAVA_BRIDGE, a.ctx(Yard.NOBODY), spot, a.cfg);
		a.data.setArmed(armed);
		DamageSources damage = level.damageSources();
		a.stand(1.5, floor + 0.2, 0.5, 0, 0);
		helper.assertFalse(planner.causedBy(a.player, damage.lava()), "lava without a drop through the gap was claimed");
		a.stand(1.5, -2, 0.5, 0, 0);
		Traps.LAVA_BRIDGE.watch(a.player, armed, a.data, a.cfg, a.now());
		a.stand(1.5, floor + 0.2, 0.5, 0, 0);
		helper.assertTrue(planner.causedBy(a.player, damage.lava()), "lava after the drop through the gap was not claimed");
		helper.assertTrue(planner.causedBy(a.player, damage.onFire()), "burning after the lava was not claimed");
		helper.assertFalse(planner.causedBy(a.player, damage.fall()), "a fall was claimed by the lava bridge");
		a.done();
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void endermanIsMovedOntoYourBridgeAhead(GameTestHelper helper) {
		Away a = new Away(helper, Level.END, 40);
		ServerLevel level = a.level;
		a.openToTheBottom(-2, -2, 22, 2);
		a.bridge(0, 20);
		// Forced End chunks take a few ticks before the entities in them can be found: the rest waits for that.
		a.whenReady(() -> {
			// The test puts an enderman on the End stone far east of the bridge, as the End would. The mod never spawns one.
			Enderman enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.MOB_SUMMONED);
			Enderman bystander = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.MOB_SUMMONED);
			helper.assertTrue(enderman != null && bystander != null, "no enderman");
			enderman.setNoAi(true);
			bystander.setNoAi(true);
			enderman.snapTo(Vec3.atBottomCenterOf(new BlockPos(a.origin.getX() + 60, 4, a.origin.getZ())), 0, 0);
			bystander.snapTo(Vec3.atBottomCenterOf(new BlockPos(a.origin.getX() + 90, 4, a.origin.getZ() + 20)), 0, 0);
			level.addFreshEntity(enderman);
			level.addFreshEntity(bystander);
			int endermen = a.count(Enderman.class);
			helper.assertTrue(endermen >= 2, "the test endermen cannot be found: " + endermen);

			a.walking(new Vec3(1, 0, 0));
			a.stand(2.5, 1, 0.5, 90, 0);
			List<Candidate> found = Traps.ENDERMAN_ON_BRIDGE.candidates(a.ctx(Yard.NOBODY));
			Candidate spot = found.stream().filter(c -> c.mob == enderman).findFirst().orElse(null);
			helper.assertTrue(spot != null, "the enderman out east was not picked: " + found.size() + " spots");
			helper.assertTrue(spot.mobTo != null && spot.mobTo.getY() == a.origin.getY() + 1 && spot.mobTo.getX() > a.player.getX(), "not onto the bridge ahead");
			helper.assertTrue(spot.clue.contains("teleport"), "the clue: " + spot.clue);
			if (Services.mobs() instanceof MobTamper.Stub) {
				helper.assertTrue(Traps.ENDERMAN_ON_BRIDGE.blocked() != null, "must be skipped while MobTamper is a stub");
				a.done(enderman, bystander);
				helper.succeed();
				return;
			}
			Vec3 was = enderman.position();
			helper.assertFalse(Traps.ENDERMAN_ON_BRIDGE.setup(a.ctx(Yard.EVERYONE), spot), "moved an enderman in view");
			helper.assertTrue(enderman.position().equals(was), "a refused move moved it");
			helper.assertTrue(Traps.ENDERMAN_ON_BRIDGE.setup(a.ctx(a.eyes()), spot), "move refused out of view");
			helper.assertTrue(enderman.blockPosition().equals(spot.mobTo), "the enderman is not on the bridge: " + enderman.blockPosition());
			helper.assertTrue(a.count(Enderman.class) == endermen, "an enderman was spawned");
			helper.assertTrue(level.getBlockState(spot.mobTo.below()).is(Blocks.COBBLESTONE), "the bridge was touched");
			// One stands on the bridge now: no spot within its reach is offered again (the clue would not hold).
			helper.assertTrue(Traps.ENDERMAN_ON_BRIDGE.candidates(a.ctx(Yard.NOBODY)).isEmpty(), "a spot within an enderman's reach was offered");

			AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> a.data, Yard.NOBODY);
			ArmedTrap armed = AccidentPlannerImpl.build(Traps.ENDERMAN_ON_BRIDGE, a.ctx(Yard.NOBODY), spot, a.cfg);
			a.data.setArmed(armed);
			helper.assertTrue(armed.blames(enderman.getUUID()) && armed.mobs().size() == 1, "the moved enderman is not remembered");
			DamageSources damage = level.damageSources();
			a.stand(spot.mobTo.getX() - a.origin.getX() - 1.5, 1, 0.5, -90, 0);
			helper.assertTrue(planner.causedBy(a.player, damage.mobAttack(enderman)), "its blow on the bridge was not claimed");
			helper.assertFalse(planner.causedBy(a.player, damage.mobAttack(bystander)), "another enderman's blow was claimed");
			a.stand(spot.mobTo.getX() - a.origin.getX() + 0.5, -70 - a.origin.getY() + level.getMinY(), 0.5, 0, 0);
			helper.assertFalse(planner.causedBy(a.player, damage.fellOutOfWorld()), "a fall into the void it had no part in was claimed");
			a.player.setLastHurtByMob(enderman);
			helper.assertTrue(planner.causedBy(a.player, damage.fellOutOfWorld()), "knocked into the void by it, not claimed");
			a.player.setLastHurtByMob(bystander);
			helper.assertFalse(planner.causedBy(a.player, damage.fellOutOfWorld()), "knocked off by another enderman, claimed");
			a.done(enderman, bystander);
			helper.succeed();
		});
	}
}
