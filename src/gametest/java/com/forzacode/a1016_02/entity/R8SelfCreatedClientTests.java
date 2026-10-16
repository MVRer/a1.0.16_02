package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.entity.sc.NamelessRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Review R8 (the self-created layer, client and multiplayer), each test failing on {@code self-created} at 5348ede.
 * The drawn fog is atmosphere's own math on vanilla's overworld fog ({@link ScreenshotPlainTests#drawnDuskFog}, as
 * {@code ClientAtmosphere.applyFog} draws it); the armed client reads that frame's fog ({@code NamelessClient.readFog})
 * and asks {@link NamelessRules#hides} with it.
 *
 * <p>Fixed in review (FIX-SC-B): the client reads the drawn fog's own cover of a friend ({@code ScreenshotSpot.Fog.value},
 * each fog on its own, as FIX-PLAIN does), and tells the server where that fog covers {@code fogShare} of a player
 * ({@link NamelessRules#reach}, {@code NamelessReachPayload}); {@code Nameless.fit} refuses when that leaves no band
 * ({@link NamelessRules#bandRefusal}). The test asks the card's whole fit, its rules and then the band from the client's
 * report, and the client's rule with the drawn fog.
 */
public class R8SelfCreatedClientTests {
	/** A friend in the middle of the card's 48 to 64 at the fire. */
	private static final double FRIEND = 56.0;
	private static final int OPEN_SKY = 15;

	/**
	 * 02: the card fits on any dusk-fog night ({@code ScreenshotRules.fogAmount} 0.1 or more at the target: Traces' 0.3
	 * and Proximity's 0.45 dusk fog pass it all night) and arms the client for 20 minutes, which counts in the director
	 * when it arms (D-091). But the client hides a name only past {@code fogShare} 0.5 of the way into the drawn fog and
	 * no farther than 64 blocks, so it needs a drawn fog end of about 128 or less. At vanilla's default 12 chunks and at
	 * 16 (Mariano's playtest setting) a clear dusk-fog night draws its fog end farther out than that: no distance from
	 * 24 to 64 hides a still friend, and the arm is spent on a moment that cannot come. The card should only fit when
	 * the fog at the target leaves the client a band (or the band should follow the fog).
	 */
	@GameTest
	public void namelessFitsOnlyWhenItsClientCanHideANameInThatFog(GameTestHelper helper) {
		NamelessConfig config = new NamelessConfig();
		FogLimits.Shape shape = AtmosphereConfig.get().fogShape();
		long still = config.stillTicks() + 1;
		float[] levels = {0.3F, 0.45F};
		String[] stages = {"Traces", "Proximity"};
		int[] chunks = {12, 16};
		long[] times = {13500L, 18000L, 21000L};
		List<String> dead = new ArrayList<>();
		int fitting = 0;
		for (int s = 0; s < levels.length; s++) {
			for (int c : chunks) {
				for (long time : times) {
					double amount = ScreenshotRules.fogAmount(levels[s], time, shape, 0.0, OPEN_SKY);
					ScreenshotSpot.Fog fog = ScreenshotPlainTests.drawnDuskFog(levels[s], time, c);
					// The card's fit: its rules, then the band the client's own fog leaves (what that client reports).
					Optional<String> fit = NamelessRules.fit(List.of(FRIEND), OPEN_SKY, amount, config)
							.or(() -> NamelessRules.bandRefusal(NamelessRules.reach(fog, config), config));
					if (fit.isPresent()) {
						continue;
					}
					fitting++;
					boolean band = false;
					for (double d = config.minDistance; d <= config.maxDistance + 1.0E-9; d += 0.5) {
						if (NamelessRules.hides(new Vec3(d, 0.0, 0.0), fog, still, true, config)) {
							band = true;
							break;
						}
					}
					if (!band) {
						dead.add(String.format(Locale.ROOT, "%s %d chunks at %d (fog amount %.2f): drawn fog %.0f to %.0f, covers half only from %.0f", stages[s],
								c, time, amount, fog.visibleStart(), fog.visibleEnd(), fog.coversFrom(config.fogShare)));
					}
				}
			}
		}
		helper.assertTrue(fitting > 0, "the scenario: the card fits on a clear dusk-fog night");
		helper.assertTrue(dead.isEmpty(), "sc_02 fits and arms, but its client can never hide a name: " + String.join("; ", dead));
		helper.succeed();
	}
}
