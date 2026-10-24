package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Optional;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks written over a base without touching it, for tests and the dev survey only (never worldgen or game code): a
 * {@link Shape} filled through {@link #fill} lands here, cell by cell, while reads fall through to the base where nothing
 * was written. Over the noise ({@link Terrain#block}) it shows what a start would build on the real terrain (caves,
 * aquifers, lava) without generating a chunk; over a generated level it shows what filling the shape again would change
 * there (what later worldgen wrote into it, {@link #changed}). Blocks only: no block entities, ticks or marks.
 */
public final class Overlay {
	/** What is there where nothing was written. */
	@FunctionalInterface
	public interface Base {
		BlockState at(int x, int y, int z);

		/** A level's (or chunk region's) blocks now. */
		static Base of(BlockGetter level) {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			return (x, y, z) -> level.getBlockState(cursor.set(x, y, z));
		}
	}

	private final Base base;
	private final boolean recordAll;
	private final Long2ObjectOpenHashMap<BlockState> cells = new Long2ObjectOpenHashMap<>();

	/** An overlay that keeps only what differs from what is there (to see what a fill would change). */
	public Overlay(Base base) {
		this(base, false);
	}

	/**
	 * With {@code recordAll}, every cell a shape sets is kept, also where it sets what was already there (to know every cell
	 * the shape decided: its air over open land too).
	 */
	public Overlay(Base base, boolean recordAll) {
		this.base = base;
		this.recordAll = recordAll;
	}

	/** True if every cell a shape sets is kept. */
	public boolean recordsAll() {
		return recordAll;
	}

	/** The block here: what was written, else the base's. */
	public BlockState get(int x, int y, int z) {
		BlockState written = cells.get(BlockPos.asLong(x, y, z));
		return written != null ? written : base.at(x, y, z);
	}

	/** The base's block here, whatever was written. */
	public BlockState base(int x, int y, int z) {
		return base.at(x, y, z);
	}

	public void set(int x, int y, int z, BlockState state) {
		cells.put(BlockPos.asLong(x, y, z), state);
	}

	/** True if something was written here (even the base's own block back; with {@link #recordsAll}, every cell set). */
	public boolean written(int x, int y, int z) {
		return cells.containsKey(BlockPos.asLong(x, y, z));
	}

	/** What was written, by {@link BlockPos#asLong} (read only). */
	public Long2ObjectMap<BlockState> cells() {
		return Long2ObjectMaps.unmodifiable(cells);
	}

	/** How many written cells now differ from the base: over a built place, the cells later worldgen changed. */
	public int changed() {
		int changed = 0;
		for (Long2ObjectMap.Entry<BlockState> e : cells.long2ObjectEntrySet()) {
			long pos = e.getLongKey();
			if (base.at(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos)) != e.getValue()) {
				changed++;
			}
		}
		return changed;
	}

	/** A writer into this overlay over {@code clip}, with {@code terrain} for the shape's ground reads. */
	public Writer writer(BoundingBox clip, Terrain terrain, RegistryAccess registries) {
		return Writer.overlay(this, clip, terrain, registries);
	}

	/**
	 * Fills {@code shape} here chunk column by chunk column within {@code within} (its whole box when empty), as worldgen
	 * would, each column's writable height from {@code minY + 1} to {@code maxY}. Returns how many blocks changed.
	 */
	public int fill(Shape shape, Terrain terrain, RegistryAccess registries, int minY, int maxY, Optional<BoundingBox> within) {
		BoundingBox box = shape.box();
		BoundingBox area = within.flatMap(w -> Draw.intersection(w, box)).orElse(within.isPresent() ? null : box);
		if (area == null) {
			return 0;
		}
		int written = 0;
		for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
			for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
				BoundingBox column = new BoundingBox(cx << 4, minY + 1, cz << 4, (cx << 4) + 15, maxY, (cz << 4) + 15);
				Optional<BoundingBox> clip = Draw.intersection(column, area);
				if (clip.isPresent()) {
					Writer w = writer(clip.get(), terrain, registries);
					shape.fill(w, clip.get());
					written += w.written();
				}
			}
		}
		return written;
	}
}
