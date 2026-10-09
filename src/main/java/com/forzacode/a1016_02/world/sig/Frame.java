package com.forzacode.a1016_02.world.sig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A local frame on an anchor: {@code right} is clockwise of {@code forward} (the same frame {@code Builds} uses). */
record Frame(int x, int y, int z, Direction forward) {
	BlockPos at(int right, int up, int fwd) {
		Direction r = forward.getClockWise();
		return new BlockPos(x + r.getStepX() * right + forward.getStepX() * fwd, y + up, z + r.getStepZ() * right + forward.getStepZ() * fwd);
	}
}
