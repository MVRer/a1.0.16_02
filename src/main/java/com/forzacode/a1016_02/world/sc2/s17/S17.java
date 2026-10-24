package com.forzacode.a1016_02.world.sc2.s17;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Feature 17 of the self-created layer's second wave (docs/SELF_CREATED_2.md), world's part: the kind {@code s17}
 * (worldgen: its siting, shape and interior), the switch's give-back of its answers, its rules and answers
 * ({@link ArchiveRules}), 11+'s way into its cells ({@link ArchiveTrail}), its commands and, in a development environment,
 * its gallery exhibits.
 */
public final class S17 {
	/** The kind's id, the structure JSON's {@code kind}. */
	public static final String ID = "s17";
	/** The cause every answer of this place is ledgered under ({@code world:s17/...}). */
	public static final String CAUSE = "world:s17";

	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.ARCHIVE, Sc17Config::get, new ArchiveSiting(), ArchiveShape::over,
			(level, kind, place, start, extra) -> ArchivePlan.of(start, ArchiveShape.lengths(extra)).interior(), ArchiveShape.VERSION);

	private S17() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc2Kind.register(KIND);
		Answers.giveBackWhenOff("sc17", CAUSE);
		ServerTickEvents.END_SERVER_TICK.register(ArchiveRules.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ArchiveRules.LIVE.clear());
		TorchTrail.setBreach(ArchiveTrail.LIVE);
		ArchiveCommands.register();
		if (Gallery.active()) {
			ArchiveGallery.register();
		}
	}
}
