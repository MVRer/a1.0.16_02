package com.forzacode.a1016_02.accident;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A test setup in the 24x20x24 {@code accident/yard} structure: blocks in relative coordinates, a private
 * {@link AccidentData} (so tests never share the world's armed trap), player-placed blocks and walked cells, and view
 * gates from fixed viewpoints (a mock player in the level would join the shared test server as the subject).
 */
final class Yard {
	static final String STRUCTURE = "a1016_02:accident/yard";
	/**
	 * Empty space around a yard whose trap searches the footprint around a base (the dark corner's 24 blocks): without
	 * it the neighbouring tests, 5 blocks away on the grid, lend it their torches.
	 */
	static final int BASE_PADDING = 24;

	/** Nobody is looking anywhere. */
	static final ViewGate NOBODY = ViewGate.of(List.of());
	/** Everything is in view. */
	static final ViewGate EVERYONE = new ViewGate() {
		@Override
		public boolean outOfView(ServerLevel level, Collection<BlockPos> positions) {
			return false;
		}

		@Override
		public boolean outOfView(ServerLevel level, AABB box) {
			return false;
		}
	};

	final GameTestHelper helper;
	final ServerLevel level;
	final AccidentData data = new AccidentData();
	final AccidentConfig cfg = AccidentConfig.get();
	/** A stand-in player for the footprint and for traps that need one. Not in the level. */
	final ServerPlayer player;

	Yard(GameTestHelper helper) {
		this.helper = helper;
		this.level = helper.getLevel();
		this.player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	BlockPos abs(int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	Vec3 absVec(double x, double y, double z) {
		return helper.absoluteVec(new Vec3(x, y, z));
	}

	void set(int x, int y, int z, Block block) {
		helper.setBlock(x, y, z, block);
	}

	void set(int x, int y, int z, BlockState state) {
		helper.setBlock(x, y, z, state);
	}

	void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					helper.setBlock(x, y, z, block);
				}
			}
		}
	}

	BlockState state(int x, int y, int z) {
		return level.getBlockState(abs(x, y, z));
	}

	/** A block "the player" placed (footprint). */
	void placed(int x, int y, int z, BlockState state) {
		helper.setBlock(x, y, z, state);
		Services.watch().onPlaced(player, level, abs(x, y, z), state);
	}

	void placed(int x, int y, int z, Block block) {
		placed(x, y, z, block.defaultBlockState());
	}

	/** A cell the player walked through, with {@code passes} separate passes, first seen on {@code day}. */
	void walk(int x, int y, int z, int flags, int passes, long day) {
		long gap = cfg.routePassGapTicks();
		for (int n = 0; n < passes; n++) {
			data.routes().record(level.dimension(), abs(x, y, z), (int) day, flags, n * (gap + 1), gap, cfg.routeMaxPointsPerDimension);
		}
	}

	void walk(int x, int y, int z, int flags) {
		walk(x, y, z, flags, 1, today());
	}

	long today() {
		return GameClock.day(level.getServer());
	}

	long now() {
		return GameClock.playTicks(level.getServer());
	}

	TrapContext ctx(ViewGate view, BlockPos center, @Nullable ServerPlayer who) {
		return new TrapContext(level, who, center, data, view, cfg, now(), today());
	}

	TrapContext ctx(ViewGate view, BlockPos center) {
		return ctx(view, center, null);
	}

	/** A player-shaped viewpoint with its feet at the relative spot. Yaw 0 looks south (+z), -90 east (+x), 90 west. */
	ViewGate viewer(double x, double y, double z, float yaw, float pitch) {
		ServerPlayer viewer = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		face(viewer, x, y, z, yaw, pitch);
		return ViewGate.of(List.of(TraceService.Viewer.of(viewer, 8)));
	}

	/** Puts a player's feet at the relative spot, body and head turned to {@code yaw} (the view follows the head). */
	void face(ServerPlayer who, double x, double y, double z, float yaw, float pitch) {
		who.snapTo(absVec(x, y, z), yaw, pitch);
		who.setYHeadRot(yaw);
		who.setYBodyRot(yaw);
	}

	/** What this player sees, as a gate. */
	static ViewGate eyesOf(ServerPlayer who) {
		return ViewGate.of(List.of(TraceService.Viewer.of(who, 8)));
	}

	/** The candidate that takes this block. */
	Candidate taking(List<Candidate> candidates, BlockPos pos) {
		for (Candidate candidate : candidates) {
			if (candidate.taken().contains(pos)) {
				return candidate;
			}
		}
		throw helper.assertionException(Component.literal("no candidate takes " + pos + " among " + candidates.stream().map(c -> c.pos).toList()));
	}

	/** Ledger entries of this cause at these positions. */
	long ledgered(String cause, Set<BlockPos> positions) {
		return TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> e.cause().equals(cause) && e.pos().dimension().equals(level.dimension()) && positions.contains(e.pos().pos())).count();
	}

	/**
	 * The preset-trap check: in view nothing changes; out of view exactly the planned blocks go, each written to the
	 * ledger under the trap's cause, the clue names the spot, and nothing drops.
	 */
	void checkPreset(TrapKind trap, Candidate candidate, ViewGate inView, ViewGate outOfView, BlockPos center) {
		List<BlockPos> taken = candidate.taken();
		List<BlockState> before = taken.stream().map(level::getBlockState).toList();
		helper.assertFalse(trap.setup(ctx(inView, center, player), candidate), trap.id() + " was set while in view");
		for (int i = 0; i < taken.size(); i++) {
			helper.assertTrue(level.getBlockState(taken.get(i)) == before.get(i), trap.id() + " changed " + taken.get(i) + " while in view");
		}
		helper.assertTrue(trap.setup(ctx(outOfView, center, player), candidate), trap.id() + " was refused out of view");
		for (int i = 0; i < taken.size(); i++) {
			helper.assertFalse(level.getBlockState(taken.get(i)) == before.get(i), trap.id() + " left " + taken.get(i) + " as it was");
		}
		helper.assertTrue(ledgered("accident:" + trap.id(), Set.copyOf(taken)) == taken.size(), trap.id() + " edits missing from the ledger");
		helper.assertTrue(!candidate.clue.isBlank(), trap.id() + " has no clue");
	}

	/** Succeeds a few ticks later if nothing dropped as an item. */
	void succeedWithoutDrops() {
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(net.minecraft.world.entity.EntityTypes.ITEM);
			helper.succeed();
		});
	}
}
