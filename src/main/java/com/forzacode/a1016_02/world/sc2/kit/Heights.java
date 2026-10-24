package com.forzacode.a1016_02.world.sc2.kit;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The world's height for the second wave's places (docs/SELF_CREATED_2.md, "Orchestrator decision ... height limits
 * (SC2-H)"): never hardcoded, always the level's own numbers (a data pack may move {@code min_y} and change
 * {@code height}; the default overworld is -64 to 319). {@link Sc2Structure} refuses every start whose final box comes
 * within {@value #BOTTOM} of the lowest block worldgen fills (the bedrock layers) or within {@value #TOP} of the level's
 * highest block (the build limit): refused, never clipped, for every kind, after the kind's own siting (a kind may keep
 * a stricter rule of its own). A feature that builds without a start (the line) uses the same numbers.
 */
public final class Heights {
	/** A box keeps at least this many blocks over the lowest block worldgen fills. */
	public static final int BOTTOM = 6;
	/** A box keeps at least this many blocks under the level's highest block. */
	public static final int TOP = 6;

	private Heights() {
	}

	/**
	 * The lowest block worldgen fills here: the level's own, or the noise's when a data pack starts the noise higher than
	 * the level (under it the level is empty: no bedrock, nothing to stand on).
	 */
	public static int lowest(LevelHeightAccessor level, ChunkGenerator generator) {
		int min = level.getMinY();
		if (generator instanceof NoiseBasedChunkGenerator noise) {
			min = Math.max(min, noise.generatorSettings().value().noiseSettings().minY());
		}
		return min;
	}

	/** The level's highest block. */
	public static int highest(LevelHeightAccessor level) {
		return level.getMaxY();
	}

	/** True if the box keeps {@value #BOTTOM} over {@code minY} and {@value #TOP} under {@code maxY}. */
	public static boolean within(BoundingBox box, int minY, int maxY) {
		return refusal(box, minY, maxY) == null;
	}

	/** Why a box this high does not fit a level from {@code minY} to {@code maxY}, or null if it does. */
	public static @Nullable String refusal(BoundingBox box, int minY, int maxY) {
		if (box.minY() < minY + BOTTOM) {
			return "height: its bottom " + box.minY() + " within " + BOTTOM + " of " + minY;
		}
		if (box.maxY() > maxY - TOP) {
			return "height: its top " + box.maxY() + " within " + TOP + " of " + maxY;
		}
		return null;
	}
}
