package com.forzacode.a1016_02.world.sc2.s18;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Feature 18 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 18), world's part: the kind {@code s18}
 * ({@link Remains.Kind#HOLLOW}, random spread, {@link HollowSiting}, {@link HollowShape}), its inside read back from
 * its record after a restart (no terrain), its answer 18b ({@link HollowAnswers}) and its give-back when switched off,
 * {@code /a1016 sc 18} and the dev-only exhibits.
 */
public final class S18 {
	/** The kind's id (its structure JSON's {@code kind}). */
	public static final String ID = "s18";
	/** Every ledger cause of its answer starts with this. */
	public static final String CAUSE = "world:s18";

	/**
	 * The inside of a recorded one from its extra; a record without it (or one a later change cannot read) counts its whole
	 * box, never a shape rebuilt on the server thread (R12-16: 0.3 to 1.2 s).
	 */
	static final Sc2Kind.Interiors INTERIORS = (level, kind, place, start, extra) -> {
		Interior stored = HollowShape.interiorOf(start, place.box(), extra);
		return stored != null ? stored : Interior.box(place.box());
	};

	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.HOLLOW, Sc18Config::get, HollowSiting.INSTANCE, HollowSiting::create, INTERIORS,
			HollowShape.VERSION);

	private S18() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc2Kind.register(KIND);
		Answers.giveBackWhenOff("sc18", CAUSE);
		HerobrineEvents.REMAINS_ENTERED.register((level, place, player) -> HollowAnswers.LIVE.entered(place, player, level.getServer().getTickCount()));
		HerobrineEvents.REMAINS_LEFT.register((level, place, player) -> HollowAnswers.LIVE.left(place, player));
		ServerTickEvents.END_SERVER_TICK.register(HollowAnswers.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HollowAnswers.reset();
			HollowSiting.clear();
		});
		SelfCreated.onSwitchedOff("sc18v", server -> HollowAnswers.reset());
		Sc18Commands.register();
		if (com.forzacode.a1016_02.core.Gallery.active()) {
			Sc18Gallery.register();
		}
	}
}
