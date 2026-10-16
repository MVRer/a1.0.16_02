package com.forzacode.a1016_02.dig;

import java.util.Locale;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * VERIFY (v1.2): what the tunnel ladder costs every player underground. The detector ({@link TunnelRun#detect}) runs
 * for each underground participant every {@link DigTicker#LADDER_TICKS} ticks, and the director asks a held ladder
 * card's gates ({@link Ladder#check}) once per director tick. Measured on the longest run it walks (32 cells back).
 */
public class VerifyV12DigTests {
	@GameTest(maxTicks = 100)
	public void verifyTheLadderDetectorIsCheapPerPlayer(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 47, 40, 8, 8, Blocks.STONE);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		rig.tunnelX(1, 38, 3, 3);
		ServerPlayer alex = rig.stand("Alex", 38, 3, 3, LadderRig.EAST);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(38, 3, 3), Direction.EAST, rig.config.ladderMinRun, rig.config.ladderMinSealedShare)
				.map(TunnelRun::length).orElse(0) >= 32, "the tunnel was not found");
		int warm = 2_000;
		int runs = 20_000;
		for (int i = 0; i < warm; i++) {
			TunnelRun.detect(g.level, rig.crew.watch, g.at(38, 3, 3), Direction.EAST, rig.config.ladderMinRun, rig.config.ladderMinSealedShare);
		}
		long start = System.nanoTime();
		for (int i = 0; i < runs; i++) {
			TunnelRun.detect(g.level, rig.crew.watch, g.at(38, 3, 3), Direction.EAST, rig.config.ladderMinRun, rig.config.ladderMinSealedShare);
		}
		double detectMicros = (System.nanoTime() - start) / 1000.0 / runs;
		for (int i = 0; i < warm / 10; i++) {
			rig.check(Ladder.Rung.ONCE, alex);
		}
		start = System.nanoTime();
		for (int i = 0; i < runs / 10; i++) {
			rig.check(Ladder.Rung.ONCE, alex);
		}
		double checkMicros = (System.nanoTime() - start) / 1000.0 / (runs / 10);
		double perTickPerPlayer = detectMicros / DigTicker.LADDER_TICKS;
		A1016_02.LOGGER.info("[a1016] VERIFY ladder cost: detect {} us per call (a 32-cell run), {} us per player per tick; Ladder.check {} us per call",
				String.format(Locale.ROOT, "%.2f", detectMicros), String.format(Locale.ROOT, "%.2f", perTickPerPlayer),
				String.format(Locale.ROOT, "%.2f", checkMicros));
		// A tick is 50 000 us: even 10 players underground at once should cost well under 1% of it.
		helper.assertTrue(perTickPerPlayer * 10 < 500.0, "the detector costs " + perTickPerPlayer + " us per player per tick");
		helper.succeed();
	}
}
