package com.forzacode.a1016_02.entity.sc2.s27;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 27's line of sight, the same on the server (picking the spot and the way off) and on the viewer's client (whether the copy
 * shows): blocks by their visual shape, so leaves and anything solid hide what is behind them, glass, grass and flowers do
 * not; fluids do not.
 */
public final class Sight {
	/** Where a player's head is from their feet, and their chest. */
	public static final double HEAD = 1.62;
	public static final double CHEST = 1.1;

	private Sight() {
	}

	/** True if nothing blocks the line from {@code from} to {@code to}. */
	public static boolean clear(BlockGetter level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}

	/** True if the head or the chest of a player standing at {@code feet} is in plain sight from {@code eye}. */
	public static boolean body(BlockGetter level, Vec3 eye, Vec3 feet) {
		return clear(level, eye, feet.add(0.0, HEAD, 0.0)) || clear(level, eye, feet.add(0.0, CHEST, 0.0));
	}

	/** True if both the head and the chest of a player standing at {@code feet} are in plain sight from {@code eye}. */
	public static boolean wholly(BlockGetter level, Vec3 eye, Vec3 feet) {
		return clear(level, eye, feet.add(0.0, HEAD, 0.0)) && clear(level, eye, feet.add(0.0, CHEST, 0.0));
	}
}
