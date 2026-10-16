package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.atmosphere.sc.InStep;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SC-7, the verifier: what the self-created layer costs on hot paths, measured on the test server in a test environment
 * of its own ({@code a1016_02:verify/sc_cost}, so no other batch runs beside it). The numbers go to the log
 * ({@code [verify] cost ...}); the bounds are 20 to 100 times what a map lookup costs, so they only catch a hook that
 * stopped being a lookup.
 */
public class VerifyScCostTests {
	private static final String BIG = "a1016_02:world/signature";
	private static final String ENV = "a1016_02:verify/sc_cost";
	private static final int N = 2_000_000;

	/**
	 * {@code core/mixin/LevelSetBlockMixin} runs {@code TraceLedger.blockChanged} after every successful
	 * {@code Level.setBlock} in a server level. With nothing watched it is one volatile read; with conversions watched it
	 * is a dimension map and a long-keyed map lookup, and only a watched cell reads the block. Measured with 512 watched
	 * conversions: a miss (the common case, a block change nowhere near one), a hit on a cell that still holds, and the
	 * whole {@code setBlock} for scale.
	 */
	@GameTest(environment = ENV, structure = BIG, maxTicks = 400)
	public void theSetBlockHookIsAConstantLookupWithAFastExit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos far = helper.absolutePos(new BlockPos(1, 12, 1));
		String cause = "verify:sc-cost/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		TraceLedger ledger = TraceLedger.get(server);
		List<BlockPos> cells = new ArrayList<>();
		try {
			double idle = perCall(() -> TraceLedger.blockChanged(level, far));
			for (BlockPos rel : BlockPos.betweenClosed(2, 2, 2, 9, 9, 9)) {
				helper.setBlock(rel, Blocks.STONE);
				cells.add(rel.immutable());
			}
			for (BlockPos rel : cells) {
				helper.assertTrue(Services.traces().convert(level, helper.absolutePos(rel), Blocks.ANDESITE.defaultBlockState(), cause), "convert refused at " + rel);
			}
			long watchedHere = ledger.entries().stream().filter(e -> e.cause().equals(cause) && e.after().isPresent()).count();
			helper.assertTrue(watchedHere == cells.size(), "watched " + watchedHere + " of " + cells.size());
			double miss = perCall(() -> TraceLedger.blockChanged(level, far));
			BlockPos watchedCell = helper.absolutePos(cells.get(cells.size() / 2));
			double hit = perCall(() -> TraceLedger.blockChanged(level, watchedCell));
			// A whole setBlock (the hook included) at a cell nothing watches, stone and dirt in turn, no neighbour updates.
			BlockState stone = Blocks.STONE.defaultBlockState();
			BlockState dirt = Blocks.DIRT.defaultBlockState();
			int[] flip = {0};
			double setBlock = perCall(() -> level.setBlock(far, (flip[0]++ & 1) == 0 ? stone : dirt, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE), N / 20);
			long changed = ledger.entries().stream().filter(e -> e.cause().equals(cause) && ledger.changedSince(e)).count();
			A1016_02.LOGGER.info(String.format(Locale.ROOT,
					"[verify] cost setBlock hook: nothing watched %.1f ns, %d watched (miss) %.1f ns, watched cell that holds %.1f ns; a whole setBlock %.0f ns",
					idle, cells.size(), miss, hit, setBlock));
			helper.assertTrue(changed == 0, "the measuring marked " + changed + " conversions changed");
			helper.assertTrue(idle < 200 && miss < 300 && hit < 2000, String.format(Locale.ROOT, "the hook is not a lookup: %.1f / %.1f / %.1f ns", idle, miss, hit));
		} finally {
			ledger.entries().stream().filter(e -> e.cause().equals(cause)).toList().forEach(ledger::remove);
			for (BlockPos rel : cells) {
				helper.setBlock(rel, Blocks.AIR);
			}
			helper.setBlock(new BlockPos(1, 12, 1), Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * The layer's other hot paths, each per call: the switch ({@code SelfCreated.enabled}, asked per frame and per hook),
	 * 04's natural spawn rule outside its window (per spawn attempt), 06's step hook for a player without an episode (per
	 * step), and 06's per-participant sampling (its tick every server tick, a sample every 10) with 8 participants.
	 */
	@GameTest(environment = ENV, structure = BIG, maxTicks = 400)
	public void theLayersPerTickAndPerCallHooksAreCheap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		double enabled = perBool(SelfCreated::enabled);
		EmptyNight.Snapshot was = EmptyNight.snapshot();
		double spawn;
		try {
			EmptyNight.snapshot(EmptyNight.Snapshot.NONE);
			spawn = perBool(() -> EmptyNight.refusesSpawn(EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, level, 0.5, 0.5));
		} finally {
			EmptyNight.snapshot(was);
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + i, 2, 1)));
			p.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			players.add(p);
		}
		double step = perCall(() -> InStep.onStep(players.getFirst(), BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
		Watched watched = new Watched(players);
		InStep steps = new InStep(() -> watched.watch, (l, s, src, pos, vol, pitch) -> List.of(), l -> List.copyOf(players));
		long[] clock = {0};
		steps.ticks = s -> clock[0];
		int ticks = 200_000;
		double perTick = perCall(() -> {
			clock[0]++;
			steps.tick(server);
		}, ticks);
		A1016_02.LOGGER.info(String.format(Locale.ROOT,
				"[verify] cost per call: enabled() %.1f ns, 04 spawn rule (no window) %.1f ns, 06 step hook (no episode) %.1f ns; 06 tick with 8 participants %.0f ns per server tick",
				enabled, spawn, step, perTick));
		helper.assertTrue(enabled < 500 && spawn < 500 && step < 500 && perTick < 20_000,
				String.format(Locale.ROOT, "a hook is not cheap: %.1f / %.1f / %.1f / %.0f ns", enabled, spawn, step, perTick));
		helper.succeed();
	}

	private static double perCall(Runnable r) {
		return perCall(r, N);
	}

	/** Mean ns per call after a warm-up of a tenth as many. */
	private static double perCall(Runnable r, int n) {
		for (int i = 0; i < n / 10; i++) {
			r.run();
		}
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			r.run();
		}
		return (System.nanoTime() - start) / (double) n;
	}

	private static double perBool(java.util.function.BooleanSupplier r) {
		boolean[] sink = {false};
		double ns = perCall(() -> sink[0] ^= r.getAsBoolean(), N);
		return sink[0] && ns < 0 ? 0 : ns;
	}

	/** The test's own players for a {@link PlayerWatch}. */
	static final class Watched implements PlayerWatch.Source {
		final List<ServerPlayer> online;
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		Watched(List<ServerPlayer> players) {
			online = List.copyOf(players);
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return online;
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}
	}
}
