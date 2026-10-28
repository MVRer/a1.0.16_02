package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A procedural structure: one place, built the same way in worldgen (a {@link Sc2Piece}, one chunk at a time, on any
 * worldgen thread) and in the gallery and tests ({@link Live#place}). Made by its kind's
 * {@link Sc2Kind.Shapes#create} from a {@link Start} and the terrain, deterministically: the same start over the same
 * terrain gives the same shape, so a start saved in a chunk and loaded again builds exactly what it began.
 *
 * <p>The rules every shape keeps (worldgen equals the gallery only if it does):
 * <ul>
 *   <li>{@link #fill} writes only inside {@code clip} (the {@link Writer} drops anything else) and may be called for the
 *   chunks in any order, each once, on different threads at once: it never keeps state between calls, never uses a shared
 *   {@code RandomSource} (per-cell randomness comes from the seed and the position, {@link Draw#unit}), and reads blocks
 *   only inside {@code clip} ({@link Writer#get} answers {@code VOID_AIR} outside it).</li>
 *   <li>Everything that spans chunks (the base height, a height grid, the maze) is decided in the constructor from the
 *   start and the terrain given to the factory, never from blocks; that terrain is for the constructor only (keep the
 *   numbers you need, not the terrain). In {@link #fill}, terrain comes from {@link Writer#terrain} or
 *   {@link Writer#ground}: the noise in worldgen, the level as it was before the shape in the gallery.</li>
 *   <li>{@link #box} is everything it may write, its grounds included, and stays within {@link Sc2Structure#MAX_REACH}
 *   chunks of the chunk its start was decided in (vanilla places a start's pieces only within 8 chunks of it).</li>
 *   <li>No vanilla terrain adaptation: a shape makes its own foundation ({@link Draw#foundation}), clearing and seal
 *   ({@link Seal}); seal first, then cut the openings (a seal fills any air within reach, outdoors too).</li>
 * </ul>
 */
public interface Shape {
	/** Everything this shape may write (a copy each call is fine; never mutate it). */
	BoundingBox box();

	/** Writes the part of the shape inside {@code clip} (within {@link #box}). */
	void fill(Writer w, BoundingBox clip);

	/** Its focal point ({@code Remains.Place#anchor}: each kind's documents what it is). */
	BlockPos anchor();

	/** The outer mouth cell of its open way in (the lower cell of the opening, on the outside). */
	BlockPos entrance();

	/** The direction its way in faces, outward. */
	Direction entranceFacing();

	/** The apex block of a pyramid; empty for the others. */
	default Optional<BlockPos> apex() {
		return Optional.empty();
	}

	/**
	 * Where it shows itself from far (docs/SELF_CREATED_2_R3.md, D-110): the point its error sign is judged from, the
	 * one the gallery's sign views ({@code Gallery.signViews}) aim at, the crumbs' lines begin at and {@code /locate}'s
	 * find tests measure to. The entrance unless a kind's shape says otherwise (17 v3: the well's top; 18 v3: the foot;
	 * ...). Decided from the constructor's numbers, like everything else of a shape; {@link Decided} keeps it.
	 */
	default BlockPos sign() {
		return entrance();
	}

	/**
	 * Its interior ({@code Remains.containing}); by default the whole box. Its kind's {@link Sc2Kind.Interiors} decides
	 * the interior of a recorded place; the default one rebuilds the shape and asks this.
	 */
	default Interior interior() {
		return Interior.box(box());
	}

	/**
	 * Small data kept with its recorded place (at most a few hundred bytes: a coarse height grid, a maze's seed), handed to
	 * its kind's {@link Sc2Kind.Interiors} after a restart. Empty by default.
	 */
	default CompoundTag extra() {
		return new CompoundTag();
	}
}
