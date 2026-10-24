package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a structure goes and how it is turned: everything its {@link Shape} is rebuilt from, with the kind's id. A
 * piece saves exactly this (plus the kind), so its NBT stays tiny and an old start loads into the same shape.
 *
 * <p>The shape's geometry is versioned (R12-2): a start remembers the version of its kind's shape it was made with, and
 * is rebuilt with that version ever after, so a later change to a shape never alters a place half generated in an
 * existing world (its remaining chunks are filled as its first ones were). A shape keeps every older version it changed
 * from reachable ({@code start.versionOr(VERSION) >= 2}, ...). A start made now and not tied yet ({@link #LATEST}: the
 * gallery's, the tests' and siting's new ones) is built with its kind's current version; the kit ties it
 * ({@link Sc2Kind#resolve}) before anything saves it, so a saved start always names its version.
 *
 * @param origin  the kind's own reference point (the pyramid's base centre, a cell's corner, ...: each kind documents it)
 * @param facing  the way its entrance faces, outward (horizontal; by D-095 toward the origin)
 * @param variant the kind's variant number (size, sealed, sea, long hall, ...)
 * @param seed    its own seed: every random choice of its shape comes from it, by position ({@link Draw#unit})
 * @param version its kind's shape version ({@link #LATEST}: its kind's current one)
 */
public record Start(BlockPos origin, Direction facing, int variant, long seed, int version) {
	/** Not tied to a version yet: built with its kind's current one. */
	public static final int LATEST = 0;
	/** What a start saved before shapes had versions was made with (every second-wave shape's first geometry). */
	public static final int FIRST = 1;

	public Start {
		origin = Objects.requireNonNull(origin).immutable();
		facing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
		version = Math.max(LATEST, version);
	}

	/** A start not tied to a version yet (its kind's current one). */
	public Start(BlockPos origin, Direction facing, int variant, long seed) {
		this(origin, facing, variant, seed, LATEST);
	}

	/** The same start moved (the gallery and tests build one start in two places). */
	public Start moved(int dx, int dy, int dz) {
		return new Start(origin.offset(dx, dy, dz), facing, variant, seed, version);
	}

	/** The same start with its origin here. */
	public Start at(BlockPos origin) {
		return new Start(origin, facing, variant, seed, version);
	}

	/** The same start made with this shape version. */
	public Start withVersion(int version) {
		return new Start(origin, facing, variant, seed, version);
	}

	/** The shape version to build: its own, or {@code current} (its kind's) when it is not tied yet. */
	public int versionOr(int current) {
		return version == LATEST ? current : version;
	}
}
