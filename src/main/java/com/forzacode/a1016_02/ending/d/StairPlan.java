package com.forzacode.a1016_02.ending.d;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The team's stair to bedrock under the seed pyramid (left by others): a spiral stair, one block wide, winding round
 * a stone pillar whose axis runs from the F07 sign down to F30's twin, into a small bedrock chamber around the twin.
 *
 * @param core        the F07 sign (the pyramid's core)
 * @param twin        F30's twin sign, on bedrock under the core
 * @param yTop        the highest carved level (just under the sea floor, below anything that falls)
 * @param s0          the first stair's level; the pillar's top is level with it
 * @param chamberCeil the first solid level over the chamber
 * @param half        the chamber's half width around the axis
 * @param stairs      every stair block, top first
 * @param builtTo     the lowest level built so far ({@link Integer#MAX_VALUE}: nothing yet)
 * @param complete    every level and the chamber are built
 */
public record StairPlan(ResourceKey<Level> dimension, BlockPos core, BlockPos twin, int yTop, int s0, int chamberCeil, int half,
		List<BlockPos> stairs, int builtTo, boolean complete) {
	public static final Codec<StairPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(StairPlan::dimension),
			BlockPos.CODEC.fieldOf("core").forGetter(StairPlan::core),
			BlockPos.CODEC.fieldOf("twin").forGetter(StairPlan::twin),
			Codec.INT.fieldOf("yTop").forGetter(StairPlan::yTop),
			Codec.INT.fieldOf("s0").forGetter(StairPlan::s0),
			Codec.INT.fieldOf("chamberCeil").forGetter(StairPlan::chamberCeil),
			Codec.INT.fieldOf("half").forGetter(StairPlan::half),
			BlockPos.CODEC.listOf().fieldOf("stairs").forGetter(StairPlan::stairs),
			Codec.INT.optionalFieldOf("builtTo", Integer.MAX_VALUE).forGetter(StairPlan::builtTo),
			Codec.BOOL.optionalFieldOf("complete", false).forGetter(StairPlan::complete)
	).apply(i, StairPlan::new));

	public StairPlan {
		stairs = List.copyOf(stairs);
	}

	public int axisX() {
		return twin.getX();
	}

	public int axisZ() {
		return twin.getZ();
	}

	/** The pillar's top: level with the first stair, under the open top of the shaft. */
	public BlockPos pillarTop() {
		return new BlockPos(axisX(), s0, axisZ());
	}

	/** The chamber, wall to wall (inside only), from below the bedrock it stands on to just under its ceiling. */
	public BoundingBox chamberBox() {
		return new BoundingBox(axisX() - half, twin.getY() - 6, axisZ() - half, axisX() + half, chamberCeil - 1, axisZ() + half);
	}

	/** The shaft's 3x3 footprint, from its top down to the chamber's ceiling. */
	public BoundingBox shaftBox() {
		return new BoundingBox(axisX() - 1, chamberCeil, axisZ() - 1, axisX() + 1, yTop, axisZ() + 1);
	}

	/** True if {@code pos} is inside the chamber. */
	public boolean inChamber(BlockPos pos) {
		return chamberBox().isInside(pos);
	}

	/** Where the bedrock search starts in each column: a little over the twin (bedrock tops vary by a few blocks). */
	public int bedrockSearchTop() {
		return twin.getY() + 4;
	}

	public StairPlan withBuiltTo(int level) {
		return new StairPlan(dimension, core, twin, yTop, s0, chamberCeil, half, stairs, level, complete);
	}

	public StairPlan completed() {
		return new StairPlan(dimension, core, twin, yTop, s0, chamberCeil, half, stairs, builtTo, true);
	}
}
