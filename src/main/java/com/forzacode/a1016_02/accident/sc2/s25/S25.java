package com.forzacode.a1016_02.accident.sc2.s25;

import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.SelfCreated;

/**
 * Feature 25 of the self-created layer's second wave (docs/SELF_CREATED_2.md), accident's part: its section, what the
 * switch ends (every running scene of it, at once), its debug command and its gallery exhibit (development only). The
 * trap kind itself, {@link PackTrap}, is in the planner's catalogue ({@code accident.Traps}), which gives it its card.
 */
public final class S25 {
	private S25() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc25Config.get();
		SelfCreated.onSwitchedOff(Pack.KIND, server -> Hunts.endAll(Pack.KIND, "switched off"));
		S25Commands.register();
		if (Gallery.active()) {
			Gallery.register(S25Gallery.exhibit());
		}
	}
}
