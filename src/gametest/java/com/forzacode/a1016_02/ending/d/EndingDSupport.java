package com.forzacode.a1016_02.ending.d;

import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for Ending D's game tests. */
final class EndingDSupport {
	/** 16 x 30 x 16: the team's stair in miniature (bedrock at the bottom, the seed pyramid's core at the top). */
	static final String SHAFT = "a1016_02:ending/d/shaft";
	/** 16 x 12 x 16: a yard for the grove, the cairn and the undo. */
	static final String YARD = "a1016_02:ending/d/yard";

	/** In the shaft structure: the axis, the twin sign on bedrock, the core sign under the pyramid's cap. */
	static final int AX = 8;
	static final int AZ = 8;
	static final int TWIN_Y = 2;
	static final int CORE_Y = 24;

	/** Nobody sees anything. */
	static final View NOBODY = View.of(List.of());
	/** Everything is in view. */
	static final View EVERYONE = new View() {
		@Override
		public boolean outOfView(ServerLevel level, Collection<BlockPos> positions) {
			return false;
		}

		@Override
		public boolean outOfView(ServerLevel level, AABB box) {
			return false;
		}
	};

	private EndingDSupport() {
	}

	/** A mock player (not in the level) at a relative position, looking along yaw and pitch (pitch below 0 looks up). */
	static ServerPlayer player(GameTestHelper helper, Vec3 rel, float yaw, float pitch) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(rel), yaw, pitch);
		return player;
	}

	/** What this player sees, as a view. */
	static View seenBy(ServerPlayer player) {
		return View.of(List.of(TraceService.Viewer.of(player, 8)));
	}

	static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		BlockState state = block.defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					helper.setBlock(x, y, z, state);
				}
			}
		}
	}

	/**
	 * The miniature world under a seed pyramid: bedrock at y 0 to 1, stone up to 19 (the stair's ceiling), the
	 * pyramid's sand from 20 to 23 under a stone cap, the F07 core at 24, the F30 twin standing on
	 * bedrock under it.
	 */
	static void shaftWorld(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 15, 1, 15, Blocks.BEDROCK);
		fill(helper, 0, 2, 0, 15, 19, 15, Blocks.STONE);
		fill(helper, 0, 20, 0, 15, 23, 15, Blocks.SAND);
		fill(helper, 0, 24, 0, 15, 24, 15, Blocks.STONE);
		// The core: stone stands in for F07's sign (a sign would pop when the floor under it is dug).
		helper.setBlock(AX, CORE_Y, AZ, Blocks.STONE);
		helper.setBlock(AX, TWIN_Y, AZ, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
	}

	/** Plans and builds the whole stair in the shaft world (forced: tests only). */
	static StairPlan buildStair(GameTestHelper helper, EndingDState data) {
		ServerLevel level = helper.getLevel();
		EndingDConfig cfg = new EndingDConfig();
		StairPlan plan = Stair.plan(level, helper.absolutePos(new BlockPos(AX, CORE_Y, AZ)), helper.absolutePos(new BlockPos(AX, TWIN_Y, AZ)), cfg)
				.orElseThrow(() -> helper.assertionException("no stair could be planned"));
		data.setStair(plan);
		for (int i = 0; i < 64; i++) {
			Stair.Attempt attempt = Stair.buildSegment(level, data, data.stair().orElseThrow(), Services.traces().forced(), cfg);
			if (attempt.status() == Stair.Status.DONE) {
				return data.stair().orElseThrow();
			}
			helper.assertTrue(attempt.status() == Stair.Status.BUILT_SEGMENT, "the stair was not built: " + attempt.status() + " " + attempt.detail());
		}
		throw helper.assertionException("the stair never finished");
	}
}
