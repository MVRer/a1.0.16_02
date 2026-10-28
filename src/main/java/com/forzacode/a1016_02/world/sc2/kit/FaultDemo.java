package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Dev only: the error sign's grammar ({@link Fault}) laid on whatever land is there, as a place's fill would lay it:
 * lighting faults over chunk-aligned squares and one cut (a square of the world missing above a section line, its
 * standing faces lipped). Built with {@code Live.place} (chunk by chunk, as worldgen) by the kit's gallery exhibit
 * ({@code 00f}) and the kit's tests; never registered as a kind.
 *
 * @param box    everything it may write (the squares and the land over them)
 * @param lit    the squares lit
 * @param cut    the square cut, or null
 * @param cutY   the cut's floor (its top cell's floor: everything over it goes)
 * @param sign   where the views aim
 * @param facing the views' side
 */
public record FaultDemo(BoundingBox box, List<BoundingBox> lit, @Nullable BoundingBox cut, int cutY, BlockPos sign, Direction facing) implements Shape {
	public FaultDemo {
		lit = List.copyOf(lit);
	}

	@Override
	public void fill(Writer w, BoundingBox clip) {
		if (cut != null) {
			Fault.cut(w, cut, cutY);
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			// The cut's floor bared to stone and coarse dirt (dead ground), then its standing border lipped.
			for (int x = Math.max(cut.minX(), clip.minX()); x <= Math.min(cut.maxX(), clip.maxX()); x++) {
				for (int z = Math.max(cut.minZ(), clip.minZ()); z <= Math.min(cut.maxZ(), clip.maxZ()); z++) {
					if (!w.get(at.set(x, cutY, z)).isAir()) {
						w.set(at, Draw.unit(7L, x, cutY, z) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
					}
				}
			}
			Fault.lip(w, cut, box.maxY(), (x, z) -> w.ground(x, z) + 1, Blocks.COBBLESTONE.defaultBlockState());
		}
		for (BoundingBox square : lit) {
			Fault.light(w, square, box.maxY());
		}
	}

	@Override
	public BlockPos anchor() {
		return sign;
	}

	@Override
	public BlockPos entrance() {
		return sign;
	}

	@Override
	public Direction entranceFacing() {
		return facing;
	}
}
