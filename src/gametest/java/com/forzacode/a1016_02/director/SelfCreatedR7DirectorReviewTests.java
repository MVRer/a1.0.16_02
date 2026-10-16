package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.Optional;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.director.sc.PlaceMemory;
import com.forzacode.a1016_02.director.sc.Places;
import com.forzacode.a1016_02.director.sc.Sc01Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bug hunt R7 on the self-created layer (docs/SELF_CREATED.md): the director's side (01 with the layer's own cards, and
 * the cards beside a friend). Each test states the rule it holds the code to; every one of them failed on
 * {@code self-created} at 5348ede.
 */
public class SelfCreatedR7DirectorReviewTests {
	private static CardInfo registered(String id) {
		return DirectorTestSupport.registryDeck().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	/**
	 * 01: "A real fire there makes it a remembered place like any other (and so it stops being trusted until it decays)",
	 * and where something happened is where it happens again. The layer's home cards happen at the house but fire while
	 * the player is away from it (05 and 05b arm only 48+ blocks from the base for 10 minutes, D-091; 07 at 48+, 08 and
	 * 09 at 32+ unless asleep; 12 and 15 arm and play later), and the place is remembered where the player stands when the
	 * card fires. So after the steps and the chest's close inside the house, the house is still the trusted chunk (the
	 * rate there x1.6) and the remembered place is out in the open, where nothing happened.
	 */
	@GameTest
	public void aMomentAtHomeMakesHomeARememberedPlace(GameTestHelper helper) {
		Sc01Config cfg = Sc01Config.get();
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		long hour = rules.hourTicks;
		BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
		PacingMemory pacing = new PacingMemory();
		PlaceMemory.Chunk house = PlaceMemory.Chunk.of(Level.OVERWORLD, home);
		long play = 0;
		for (; play < 2 * hour; play += rules.tickInterval) {
			pacing.places().sampleDwell(house, play, rules.tickInterval, hour);
		}
		long day = GameClock.dayTicks(helper.getLevel().getServer());
		helper.assertTrue(Places.trusted(pacing.places(), play, day, hour, cfg).equals(Optional.of(house)), "setup: the house is not trusted");
		// sc_05 arms while they are away (60 blocks out), and the steps and the chest play in the house when they come back.
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(Vec3.atBottomCenterOf(home.offset(60, 0, 0)), 0, 0);
		DirectorImpl.remember(player, pacing, registered("sc_05"), new DirectorBrain.Clock(play, day));
		Optional<PlaceMemory.Chunk> trusted = Places.trusted(pacing.places(), play, day, hour, cfg);
		double atHome = Places.factor(pacing.places(), Level.OVERWORLD, home, play, day, hour, cfg);
		helper.assertTrue(trusted.isEmpty() || !trusted.get().equals(house),
				"the house where the moment came is still the trusted chunk (x" + atHome + " there); remembered at " + pacing.places().places());
		helper.succeed();
	}
}
