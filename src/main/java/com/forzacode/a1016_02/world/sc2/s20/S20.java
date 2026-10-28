package com.forzacode.a1016_02.world.sc2.s20;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Feature 20 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 20), world's part: the kind {@code s20}
 * ({@link Remains.Kind#FLOOR}; its structure and set JSONs, random spread), its answer 20b ({@link FloorAnswers}: no block
 * edits, so nothing to give back; switched off, every animal it holds is let go at once), {@code /a1016 sc 20} and the dev-only
 * exhibit.
 */
public final class S20 {
	/** The kind's id (its structure JSON's {@code kind}). */
	public static final String ID = "s20";
	/** Every cause of its answers starts with this. */
	public static final String CAUSE = "world:s20";

	/**
	 * Its inside from its record alone (R12-21: never the noise on the server thread): the air over the square, from the
	 * place's box (the square and the seal's reach) and its anchor (a cell over the floor).
	 */
	static final Sc2Kind.Interiors INTERIORS = (level, kind, place, start, extra) -> {
		BoundingBox square = FloorShape.floorOf(place);
		return Interior.box(new BoundingBox(square.minX(), square.minY() + 1, square.minZ(), square.maxX(), square.minY() + FloorShape.INSIDE_UP,
				square.maxZ()));
	};

	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.FLOOR, Sc20Config::get, FloorSiting.INSTANCE, FloorShape::new, INTERIORS,
			FloorShape.VERSION);

	private S20() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc2Kind.register(KIND);
		// 20b v2 (R3-3): its torches and lights come back when the layer is switched off.
		com.forzacode.a1016_02.world.sc2.kit.Answers.giveBackWhenOff("sc20d", CAUSE);
		HerobrineEvents.REMAINS_ENTERED.register((level, place, player) -> FloorAnswers.LIVE.entered(place, player));
		HerobrineEvents.REMAINS_LEFT.register((level, place, player) -> FloorAnswers.LIVE.left(place, player));
		ServerTickEvents.END_SERVER_TICK.register(FloorAnswers.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FloorAnswers.LIVE.reset());
		SelfCreated.onSwitchedOff("sc20", server -> FloorAnswers.LIVE.switchedOff());
		FloorCommands.register();
		if (com.forzacode.a1016_02.core.Gallery.active()) {
			FloorGallery.register();
		}
	}
}
