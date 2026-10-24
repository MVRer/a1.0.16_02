package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Local coordinates for a structure turned toward its {@code facing}: design it once as if its front faced NORTH, and
 * the frame turns it. {@code r} runs to the right (EAST when facing NORTH), {@code u} up, {@code f} forward (out of the
 * front, toward the world's origin by D-095). Block states designed facing NORTH turn with {@link #turn}.
 *
 * <pre>{@code
 * Frame frame = new Frame(start.origin(), start.facing());
 * w.set(frame.pos(0, 1, 9), frame.turn(Blocks.OAK_STAIRS.defaultBlockState()));  // a stair at the front, facing out
 * }</pre>
 */
public record Frame(BlockPos origin, Direction facing) {
	public Frame {
		origin = Objects.requireNonNull(origin).immutable();
		facing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
	}

	/**
	 * The frame of a square footprint {@code side} wide whose lowest corner is {@code min}, turned to {@code facing}:
	 * its origin is the front-left corner, so {@code r} runs 0 to {@code side - 1} along the front and {@code f} runs 0
	 * (the front row) to {@code -(side - 1)} (the back row), and the footprint is the same square for every facing.
	 */
	public static Frame square(BlockPos min, int side, Direction facing) {
		int far = side - 1;
		BlockPos corner = switch (facing) {
			case SOUTH -> min.offset(far, 0, far);
			case EAST -> min.offset(far, 0, 0);
			case WEST -> min.offset(0, 0, far);
			default -> min;
		};
		return new Frame(corner, facing);
	}

	/** One step to the right. */
	public Direction right() {
		return facing.getClockWise();
	}

	public int x(int r, int f) {
		return origin.getX() + right().getStepX() * r + facing.getStepX() * f;
	}

	public int y(int u) {
		return origin.getY() + u;
	}

	public int z(int r, int f) {
		return origin.getZ() + right().getStepZ() * r + facing.getStepZ() * f;
	}

	public BlockPos pos(int r, int u, int f) {
		return new BlockPos(x(r, f), y(u), z(r, f));
	}

	/** The box between two local corners. */
	public BoundingBox box(int r0, int u0, int f0, int r1, int u1, int f1) {
		return BoundingBox.fromCorners(pos(r0, u0, f0), pos(r1, u1, f1));
	}

	/** The rotation that turns NORTH to {@link #facing}. */
	public Rotation rotation() {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** A state designed for a front facing NORTH, turned to this frame. */
	public BlockState turn(BlockState designed) {
		return designed.rotate(rotation());
	}

	/** A direction designed for a front facing NORTH, turned to this frame. */
	public Direction turn(Direction designed) {
		return rotation().rotate(designed);
	}
}
