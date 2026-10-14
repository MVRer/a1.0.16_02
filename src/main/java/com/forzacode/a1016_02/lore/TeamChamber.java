package com.forzacode.a1016_02.lore;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What lore knows of Ending D's bedrock chamber, the bottom of the team's stair under the seed pyramid (left by others):
 * its inside, wall to wall, as the ending reported it once the stair was finished ({@link LoreApi#chamberBuilt}), and
 * the chamber's chest once a note went in it. F35's plates and F36's note go in it (D-066); the late chamber books go
 * in its chest; a sign written in it is never "near his traces" (M9). Kept as anchors in {@link LoreData}.
 */
final class TeamChamber {
	static final String MIN = "D/chamber_min";
	static final String MAX = "D/chamber_max";
	/** The chamber's chest (F36's, or F25's when F25 lies in the chamber). */
	static final String CHEST = "D/chest";

	private TeamChamber() {
	}

	/** Remembers the chamber; true if it was new or moved. */
	static boolean record(LoreData data, ResourceKey<Level> dimension, BoundingBox box) {
		GlobalPos min = GlobalPos.of(dimension, new BlockPos(box.minX(), box.minY(), box.minZ()));
		GlobalPos max = GlobalPos.of(dimension, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		if (data.anchor(MIN).equals(Optional.of(min)) && data.anchor(MAX).equals(Optional.of(max))) {
			return false;
		}
		data.setAnchor(MIN, min);
		data.setAnchor(MAX, max);
		return true;
	}

	/** The chamber's inside in this dimension, once the stair is finished. */
	static Optional<BoundingBox> box(Placing.Facts facts, ResourceKey<Level> dimension) {
		return box(facts.anchor(MIN), facts.anchor(MAX), dimension);
	}

	static Optional<BoundingBox> box(LoreData data, ResourceKey<Level> dimension) {
		return box(data.anchor(MIN), data.anchor(MAX), dimension);
	}

	private static Optional<BoundingBox> box(Optional<GlobalPos> min, Optional<GlobalPos> max, ResourceKey<Level> dimension) {
		if (min.isEmpty() || max.isEmpty() || !min.get().dimension().equals(dimension) || !max.get().dimension().equals(dimension)) {
			return Optional.empty();
		}
		return Optional.of(BoundingBox.fromCorners(min.get().pos(), max.get().pos()));
	}

	/** True if {@code pos} is inside the chamber (M9: a sign written there is not "near his traces"). */
	static boolean contains(LoreData data, GlobalPos pos) {
		return box(data, pos.dimension()).filter(box -> box.isInside(pos.pos())).isPresent();
	}

	static boolean contains(MinecraftServer server, GlobalPos pos) {
		return contains(LoreData.get(server), pos);
	}
}
