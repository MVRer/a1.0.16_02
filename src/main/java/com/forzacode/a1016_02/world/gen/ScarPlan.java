package com.forzacode.a1016_02.world.gen;

import java.util.List;

import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.ScarKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * One planned scar: what it is, the blocks it puts or takes ({@link Blueprint}), the area it kills or strips
 * ({@link AreaScars.Area}) and the sites lore can use. Immutable; shared between worldgen threads.
 *
 * @param kind      the scar
 * @param anchor    the main site position
 * @param size      the main site size
 * @param blueprint the build or carve (for large scars, the cross on the peak), or null
 * @param area      the dead or stripped area, or null
 * @param sites     sites to record when the chunk that contains them is generated
 */
public record ScarPlan(ScarKind kind, BlockPos anchor, int size, @Nullable Blueprint blueprint, AreaScars.@Nullable Area area,
		List<SiteMark> sites) {
	/**
	 * A site to record.
	 *
	 * @param interior    the inside of a build (for the emptied house card), or null
	 * @param nearestTree bare forest: record the trunk nearest {@code pos} instead of {@code pos}
	 */
	public record SiteMark(SiteType type, BlockPos pos, int size, @Nullable BoundingBox interior, boolean nearestTree) {
		public static SiteMark of(SiteType type, BlockPos pos, int size) {
			return new SiteMark(type, pos, size, null, false);
		}
	}

	public ScarPlan {
		sites = List.copyOf(sites);
	}

	/** True if the plan may change blocks in this chunk. */
	public boolean touches(ChunkPos chunk) {
		return blueprint != null && blueprint.intersects(chunk) || area != null && area.touches(chunk, 0);
	}

	/** The horizontal box the plan may touch, for the far-from-spawn rule. */
	public BoundingBox footprint() {
		BoundingBox box = blueprint != null ? blueprint.box() : null;
		if (area != null) {
			BoundingBox areaBox = new BoundingBox(area.centerX() - area.radius(), anchor.getY(), area.centerZ() - area.radius(),
					area.centerX() + area.radius(), anchor.getY(), area.centerZ() + area.radius());
			box = box == null ? areaBox : BoundingBox.encapsulating(box, areaBox);
		}
		return box != null ? box : new BoundingBox(anchor);
	}
}
