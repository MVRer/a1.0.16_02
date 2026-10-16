package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.accident.sc.StoodOn;
import com.forzacode.a1016_02.accident.sc.StoodOnData;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

/**
 * Review R9 (docs/SELF_CREATED.md, 15, "never a fatal drop"; DESIGN, "How death works" and Accidents): the hole the
 * block you stood on leaves must never be a death the game cannot mark, nor a drop that kills. Same slab rig as
 * {@code SelfCreatedAccidentTests} (natural stone, ground at y 9, feet at y 10, z 6), far from everything, with its own
 * group, routes, ring, traps, memory and clocks; only the trace service and the live planner's claim are the server's.
 */
public class SelfCreatedR9AccidentTests {
	/** Lava two cells under the ground (air at y 8, lava at y 7): a 1x1 pit nobody climbs out of. */
	private static final int LAVA = 10;
	/** A lava source in the ground layer beside the ground block (z 7), a short air drop under it (floor at y 4). */
	private static final int LAVA_BESIDE = 16;
	/** Air from y 3 to y 8 onto a stalagmite at y 2: the code's "drop of 6", a 7-block fall from where they walk. */
	private static final int STALAGMITE = 22;

	private static final class Field {
		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		final ServerPlayer player;
		final Group group;
		final RouteBook routes = new RouteBook();
		final List<RouteSampler.Stand> ring = new ArrayList<>();
		final List<ArmedTrap> traps = new ArrayList<>();
		final StoodOnData memory = new StoodOnData();
		final Sc15Config config = new Sc15Config();
		final long[] now = {1_000_000};
		final long[] day = {5};
		long walkedAt;

		Field(GameTestHelper helper, int slot) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.origin = helper.absolutePos(BlockPos.ZERO).offset(9000 + slot * 512, 40, 5000);
			BlockState stone = Blocks.STONE.defaultBlockState();
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 12; z++) {
					for (int y = 0; y < 16; y++) {
						level.setBlock(at(x, y, z), y < 10 ? stone : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			air(LAVA, 8, 8);
			level.setBlock(at(LAVA, 7, 6), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
			air(LAVA_BESIDE, 5, 8);
			level.setBlock(at(LAVA_BESIDE, 9, 7), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
			air(STALAGMITE, 3, 8);
			level.setBlock(at(STALAGMITE, 2, 6), Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(SpeleothemBlock.TIP_DIRECTION, Direction.UP)
					.setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP), Block.UPDATE_CLIENTS);
			level.setBlock(at(STALAGMITE, 1, 6), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			ServerPlayer mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			this.player = mock;
			group = new Group(helper, mock);
		}

		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		private void air(int x, int y0, int y1) {
			for (int y = y0; y <= y1; y++) {
				level.setBlock(at(x, y, 6), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		/** The player's feet on the slab at this x, facing east (on, away from what is behind). */
		void standAt(int x) {
			BlockPos feet = feet(x);
			player.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90, 0);
			player.setYHeadRot(-90);
		}

		BlockPos feet(int x) {
			return at(x, 10, 6);
		}

		/** That cell walked three separate times (a route), stood on 5 s ago, the player now 8 past it facing on. */
		void walkedAndPassed(int x) {
			for (int i = 0; i < 3; i++) {
				routes.record(Level.OVERWORLD, feet(x), 5, 0, walkedAt += 10_000L, 1200, 10_000);
			}
			ring.clear();
			ring.add(new RouteSampler.Stand(Level.OVERWORLD, feet(x), now[0] - ModConfig.realTicks(5)));
			standAt(x + 8);
		}

		StoodOn.Env env() {
			Pacing pacing = ModConfig.pacing();
			int chunks = level.getServer().getPlayerList().getViewDistance();
			return new StoodOn.Env(group.watch, uuid -> List.copyOf(ring), uuid -> routes, () -> List.copyOf(traps),
					l -> group.online.stream().filter(p -> p.level() == l).toList(),
					(l, pos) -> TraceService.positionsOutOfView(l, List.of(pos), group.online.stream().filter(p -> p.level() == l)
							.map(p -> TraceService.Viewer.of(p, chunks)).toList(), pacing.viewNearBlocks, pacing.viewConeDegrees),
					Services.traces(), memory, config, AccidentConfig.get().zoneSlack, () -> now[0], () -> day[0], uuid -> 0L, p -> Long.MAX_VALUE,
					() -> true, () -> group.state);
		}

		void cleanUp() {
			TraceLedger ledger = TraceLedger.get(level.getServer());
			ledger.entries().stream().filter(e -> StoodOn.CAUSE.equals(e.cause()) && e.pos().pos().closerThan(origin, 64)).toList().forEach(ledger::remove);
		}
	}

	/**
	 * A 1x1 hole on their own route over lava two cells down is a pit nobody climbs out of: walking back the way they
	 * came, as a route is walked, is a death. It opens at Proximity (from about hour 1.3 in an early world), outside the
	 * accident planner (DESIGN 4b: the first armed accident not before hour 2.6 to 4.4; one trap per player, never two
	 * deaths' worth of setup in a session), and no armed trap claims that death, so in hardcore the run ends with no cross
	 * and no cause on the list ("only the deaths he causes are marked"). Either sc_15 never opens over such lava, or a
	 * death in it is claimed and marked.
	 */
	@GameTest
	public void aHoleOverLavaIsNeverAnUnmarkedDeathOnTheirRoute(GameTestHelper helper) {
		Field field = new Field(helper, 91);
		try {
			field.walkedAndPassed(LAVA);
			boolean opened = StoodOn.tryNow(field.player, false, field.env());
			if (opened) {
				BlockPos pit = field.at(LAVA, 7, 6);
				field.player.snapTo(pit.getX() + 0.5, pit.getY(), pit.getZ() + 0.5, 90, 0);
				boolean marked = AccidentInit.planner().claim(field.player, field.level.damageSources().lava()).isPresent();
				helper.assertTrue(marked, "sc_15 opened a 1x1 hole on their route over lava 2 cells under the ground (no way to climb out), and no trap claims a "
						+ "death in it: in hardcore that ends the run unmarked (no cross, no cause on the list), at Proximity, before any accident may come");
			}
		} finally {
			field.cleanUp();
		}
		helper.succeed();
	}

	/**
	 * A ground block with a lava source beside it in the ground layer: taking it lets the lava in (core's edit lets the
	 * neighbours' fluids start to flow), so the "clean 1x1 hole" is a lava leak onto the path and down into the cave.
	 * sc_15 must refuse a block with any fluid beside it.
	 */
	@GameTest
	public void noHoleWhereTheFluidBesideItWouldFlowIn(GameTestHelper helper) {
		Field field = new Field(helper, 92);
		try {
			field.walkedAndPassed(LAVA_BESIDE);
			String why = StoodOn.check(field.player, field.ring.getLast(), false, field.env());
			helper.assertTrue(why != null, "sc_15 may take a ground block with a lava source beside it at " + field.at(LAVA_BESIDE, 9, 7).toShortString()
					+ ": the lava flows into the hole and down the drop under it");
		} finally {
			field.cleanUp();
		}
		helper.succeed();
	}

	/**
	 * "Never a fatal drop": the code's "air drop of 6" is counted from the cell under the ground block, so it is a 7-block
	 * fall from where they walk, and the floor may be a stalagmite (vanilla doubles the fall's damage on a pointed
	 * dripstone tip: about 13 of 20 health). Neither the fall height past airDropMax nor a floor that hurts may open.
	 */
	@GameTest
	public void theDropIsNeverDeeperThanAirDropMaxNorOntoAStalagmite(GameTestHelper helper) {
		Field field = new Field(helper, 93);
		try {
			field.walkedAndPassed(STALAGMITE);
			StoodOn.Under under = StoodOn.under(field.level, field.group.watch, field.feet(STALAGMITE).below(), field.config);
			int fall = field.feet(STALAGMITE).getY() - (field.at(STALAGMITE, 2, 6).getY() + 1);
			String why = StoodOn.check(field.player, field.ring.getLast(), false, field.env());
			helper.assertTrue(why != null, "sc_15 may open a " + fall + "-block fall (airDropMax " + field.config.airDropMax + ", reported as a drop of "
					+ under.depth() + ") onto a pointed dripstone tip");
		} finally {
			field.cleanUp();
		}
		helper.succeed();
	}
}
