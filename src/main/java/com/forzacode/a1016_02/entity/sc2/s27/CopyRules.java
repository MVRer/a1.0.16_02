package com.forzacode.a1016_02.entity.sc2.s27;

import com.forzacode.a1016_02.entity.ScreenshotSpot;

import net.minecraft.world.phys.Vec3;

/**
 * 27's copy on the viewer's client (docs/SELF_CREATED_2.md, 27), its pure rules: kept free of client classes so the game
 * tests check them, read by {@code entity.client.sc2.s27.S27Client} every tick with the frame's camera
 * ({@link ScreenshotSpot.View}), the frame's fog and a line of sight ({@link Sight}).
 *
 * <p>"Seen" is what vanilla draws of a player there: on the screen, and either its name (vanilla draws a player's name within
 * {@link Sc27Rules#NAME_RANGE} of the camera, through blocks, fog and the dark) or its body (a clear line, not fogged out,
 * not lost in the dark). It never goes while seen; it walks off instead (chased, it runs as a player runs), unless it is
 * caught up with ({@link #ghost}): never touched.
 */
public final class CopyRules {
	/** The points of the copy the screen test looks at, up from its feet: its feet, chest, head and the name over it. */
	static final double[] HEIGHTS = {0.05, 1.0, 1.8, 2.35};
	/** And this far to either side at the chest. */
	static final double SIDE = 0.4;
	/** On the screen: within this share of the frame past its edges (a shoulder or the name's end shows first). */
	public static final double ON_SCREEN_MARGIN = 0.04;
	/** It is put there only while this far outside the frame (never popping in at its edge). */
	public static final double APPEAR_MARGIN = 0.1;
	/** Looked at: its chest within the middle third of the frame's width, and inside its height. */
	public static final double MIDDLE_FROM = 1.0 / 3.0;
	public static final double MIDDLE_TO = 2.0 / 3.0;
	/** The drawn fog covers this much of it: its body is gone into the fog. */
	public static final double FOG_HIDES = 0.92;
	/** Lost in the dark: past the name's range, at a raw light this low or less... */
	public static final int DARK_LIGHT = 4;
	/** ...and this few pixels tall or fewer on the screen. */
	public static final double DARK_PIXELS = 14.0;

	/** A player's sprint, blocks a tick (5.612 a second). */
	public static final double SPRINT = 5.612 / 20.0;
	/** A sprint-jump's speed through the air, blocks a tick (about 7.1 a second). */
	public static final double SPRINT_JUMP = 7.1 / 20.0;
	/** Chased: the viewer closing faster than this (blocks a tick)... */
	public static final double CLOSING_PER_TICK = 0.05;
	/** ...within the near limit; it runs on for this many ticks after. */
	public static final int CHASED_TICKS = 40;
	/** Chased this close, it sprint-jumps. */
	public static final double SPRINT_JUMP_WITHIN = 18.0;
	/** Caught up with this close (blocks from the camera), it is gone even in view: never touched, never walked through. */
	public static final double GHOST_WITHIN = 6.0;

	private CopyRules() {
	}

	/** Caught up with: the camera within {@link #GHOST_WITHIN} of its feet. A desync ghost goes so; a hand never meets it. */
	public static boolean ghost(double distance) {
		return distance < GHOST_WITHIN;
	}

	/** True if any of the copy's points ({@link #HEIGHTS}, and its sides) lands within {@code margin} of the frame. */
	public static boolean onScreen(ScreenshotSpot.View view, Vec3 feet, double margin) {
		Vec3 side = view.right().scale(SIDE);
		for (double h : HEIGHTS) {
			Vec3 point = feet.add(0.0, h, 0.0);
			if (inFrame(view.project(point), margin)) {
				return true;
			}
		}
		Vec3 chest = feet.add(0.0, 1.0, 0.0);
		return inFrame(view.project(chest.add(side)), margin) || inFrame(view.project(chest.subtract(side)), margin);
	}

	private static boolean inFrame(ScreenshotSpot.Screen screen, double margin) {
		return screen != null && screen.x() >= -margin && screen.x() <= 1.0 + margin && screen.y() >= -margin && screen.y() <= 1.0 + margin;
	}

	/** Looked at: its chest in the middle third of the frame's width, and within the frame's height. */
	public static boolean middleThird(ScreenshotSpot.View view, Vec3 feet) {
		ScreenshotSpot.Screen chest = view.project(feet.add(0.0, 1.0, 0.0));
		return chest != null && chest.x() >= MIDDLE_FROM && chest.x() <= MIDDLE_TO && chest.y() >= 0.0 && chest.y() <= 1.0;
	}

	/** Lost in the dark: past the name's range, dark there, and a few pixels tall. */
	public static boolean lostInDark(double distance, int light, double pixelsTall) {
		return distance > Sc27Rules.NAME_RANGE && light <= DARK_LIGHT && pixelsTall <= DARK_PIXELS;
	}

	/**
	 * Seen now: on the screen, and its name (within {@link Sc27Rules#NAME_RANGE} of the camera) or its body (in plain
	 * sight, the fog over it under {@link #FOG_HIDES}, not {@link #lostInDark}).
	 *
	 * @param distance from the camera to its feet (vanilla's name rule)
	 * @param inSight  its head or chest in plain sight ({@link Sight#body})
	 * @param fog      how much the drawn fog covers its chest (0 to 1)
	 * @param light    the raw light at its head
	 */
	public static boolean seen(boolean onScreen, double distance, boolean inSight, double fog, int light, double pixelsTall) {
		if (!onScreen) {
			return false;
		}
		if (distance < Sc27Rules.NAME_RANGE) {
			return true; // its name shows, through anything
		}
		return inSight && fog < FOG_HIDES && !lostInDark(distance, light, pixelsTall);
	}

	/** What the copy does next while it stands. */
	public enum Next { STAY, LEAVE, GONE }

	/** R10-8: never seen, the copy stands this long at most (ticks, 40 s) rather than its life; seen, its life holds. */
	public static final int UNSEEN_LIFE_TICKS = 800;

	/**
	 * Pure (R10-8): how long the copy stands (ticks since it was put there): never seen yet ({@code firstSeenAge} -1), up to
	 * {@link #UNSEEN_LIFE_TICKS} (a viewer who has not turned that way yet still may); once seen, its life, or, seen late in
	 * it, long enough to be looked at ({@code lookTicks}) and a second more.
	 */
	public static int life(int lifeTicks, int lookTicks, int firstSeenAge) {
		if (firstSeenAge < 0) {
			return Math.max(lifeTicks, UNSEEN_LIFE_TICKS);
		}
		return Math.max(lifeTicks, firstSeenAge + lookTicks + 20);
	}

	/**
	 * While it stands: the viewer within {@code near} makes it go (gone if not seen, else it walks off); looked at
	 * {@code lookTicks} in all, its life over or the server's end make it walk off if seen, else it is gone.
	 */
	public static Next standing(boolean seen, double distance, double near, int lookedTicks, int lookTicks, int age, int lifeTicks, boolean ended) {
		if (distance < near) {
			return seen ? Next.LEAVE : Next.GONE;
		}
		if (lookedTicks >= lookTicks) {
			return Next.LEAVE;
		}
		if (age >= lifeTicks || ended) {
			return seen ? Next.LEAVE : Next.GONE;
		}
		return Next.STAY;
	}
}
