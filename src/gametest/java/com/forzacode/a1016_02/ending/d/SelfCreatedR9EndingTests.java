package com.forzacode.a1016_02.ending.d;

import java.util.List;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.world.sc.WhereHeStood;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * Review R9 (docs/SELF_CREATED.md, 10 and "ending"; DESIGN, Ending D): "for the first time in the mod, the fog pulls
 * back to full render distance" and, afterward, "the fog is gone for good". A fog place where he stood (10) lasts 1 to
 * 3 in-game days; nothing in Ending D takes it with the rest of the fog. The test's place is far from everything, and the
 * live dusk fog and places are put back as they were.
 */
public class SelfCreatedR9EndingTests {
	/**
	 * A fog place set the day before (he was seen near the pyramid, the shore or the base) is still thick when the last
	 * minute takes the fog ({@code LastMinute}'s {@code fogGone}) and after the afterward keeps it gone
	 * ({@code Afterward.keepEffects}): the climb out at dawn and the far shore where he stands keep 10's fog.
	 */
	@GameTest
	public void endingDTakesTheFogPlacesWithTheRestOfTheFog(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		float dusk = state.effects().duskFogLevel();
		List<FogPlace> before = state.effects().fogPlaces();
		long now = server.overworld().getDefaultClockTime();
		List<FogPlace> left;
		try {
			ClientEffects.clearFogPlaces(server);
			WhereHeStood.Config config = WhereHeStood.config();
			FogPlace place = WhereHeStood.fogPlace(new Vec3(2.0e6, 64.0, 2.0e6), now, 2.0, config);
			helper.assertTrue(ClientEffects.addFogPlace(server, place, config.maxFogPlaces), "setup: the fog place was refused");
			LastMinute.Ports.LIVE.fogGone(server);
			Afterward.keepEffects(server);
			long later = now + 2L * Math.max(1, config.fogRampTicks);
			left = HerobrineState.get(server).effects().fogPlaces().stream().filter(p -> p.timeAmount(later) > 0.0).toList();
		} finally {
			ClientEffects.clearFogPlaces(server);
			for (FogPlace place : before) {
				ClientEffects.addFogPlace(server, place, ClientEffects.FOG_PLACES_MAX);
			}
			ClientEffects.setDuskFog(server, dusk);
		}
		helper.assertTrue(left.isEmpty(), "Ending D took the fog for good but " + left.size() + " fog place(s) still give fog a minute later (level "
				+ (left.isEmpty() ? 0 : left.getFirst().level()) + ", until clock " + (left.isEmpty() ? 0 : left.getFirst().untilClock()) + ")");
		helper.succeed();
	}
}
