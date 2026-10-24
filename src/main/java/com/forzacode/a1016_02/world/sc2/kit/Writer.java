package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.shorts.ShortList;

import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

/**
 * What a {@link Shape} writes through: one call's view of the world, clipped to {@link #clip}. In worldgen it wraps the
 * chunk's {@link WorldGenLevel} ({@link Sc2Piece}); in the gallery and tests the {@link ServerLevel} ({@link Live}). Both
 * write the same way, so they build the same blocks: no neighbour updates, no {@code onPlace}, no drops; a fluid gets
 * its tick; a block whose shape depends on its neighbours (fences, panes, bars, walls, stairs, gates, ladders, torches,
 * lanterns, and whatever vanilla marks) is marked and updated from its final neighbours once they are all written (in
 * worldgen when the chunk is finished, as vanilla's structures are; {@link Live} after its last chunk). Set the states you
 * want explicitly where it matters. One thread; never kept past the call. In worldgen every cell a call reads or sets
 * in its clip is guarded from later worldgen until the chunk is finished ({@link Guard}). A third kind writes into an
 * {@link Overlay} instead of a level (tests and the dev survey: a shape built over the noise or over a generated level
 * without touching it); it keeps blocks only (no block entities, ticks or marks).
 */
public final class Writer {
	/** How every block is set: clients told, no neighbour or shape updates, no drops, no block entity side effects, no onPlace. */
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
			| Block.UPDATE_SKIP_ON_PLACE;
	private static final BlockState OUTSIDE = Blocks.VOID_AIR.defaultBlockState();

	private final @Nullable WorldGenLevel level;
	private final @Nullable Overlay overlay;
	private final RegistryAccess registries;
	private final BoundingBox clip;
	private final Terrain terrain;
	/** Live: the cells to update from their neighbours after the last chunk; null in worldgen (the chunk keeps them). */
	private final @Nullable List<BlockPos> liveMarks;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private int written;
	/** Worldgen in a decoration region: every cell this call reads or sets in the clip is guarded ({@link Guard}). */
	private final Guard.@Nullable Mask guard;

	private Writer(@Nullable WorldGenLevel level, @Nullable Overlay overlay, RegistryAccess registries, BoundingBox clip, Terrain terrain,
			@Nullable List<BlockPos> liveMarks) {
		this.level = level;
		this.overlay = overlay;
		this.registries = registries;
		this.clip = new BoundingBox(clip.minX(), clip.minY(), clip.minZ(), clip.maxX(), clip.maxY(), clip.maxZ());
		this.terrain = terrain;
		this.liveMarks = liveMarks;
		// A clip within one chunk of a decoration region (every caller clips to a chunk column): guard what it touches.
		this.guard = level instanceof WorldGenRegion region && clip.minX() >> 4 == clip.maxX() >> 4 && clip.minZ() >> 4 == clip.maxZ() >> 4
				? Guard.of(region.getLevel().dimension(), new ChunkPos(clip.minX() >> 4, clip.minZ() >> 4), region.getMinY(), region.getHeight())
				: null;
	}

	/**
	 * Worldgen: a piece's (or a placed feature's) chunk, {@code clip} within it ({@link Sc2Piece} does this for every kind;
	 * a feature such as the line makes its own over its chunk's writable column and the noise, {@link Terrains#noise}).
	 */
	public static Writer worldgen(WorldGenLevel level, BoundingBox clip, Terrain terrain) {
		return new Writer(level, null, level.registryAccess(), clip, terrain, null);
	}

	/** The gallery and tests ({@link Live}): marks go to {@code marks}. */
	static Writer live(ServerLevel level, BoundingBox clip, Terrain terrain, List<BlockPos> marks) {
		return new Writer(level, null, level.registryAccess(), clip, terrain, marks);
	}

	/** Into an {@link Overlay} (tests and the dev survey; blocks only). */
	static Writer overlay(Overlay overlay, BoundingBox clip, Terrain terrain, RegistryAccess registries) {
		return new Writer(null, overlay, registries, clip, terrain, null);
	}

	/** Where this call may write (a copy). */
	public BoundingBox clip() {
		return new BoundingBox(clip.minX(), clip.minY(), clip.minZ(), clip.maxX(), clip.maxY(), clip.maxZ());
	}

	public int minX() {
		return clip.minX();
	}

	public int minY() {
		return clip.minY();
	}

	public int minZ() {
		return clip.minZ();
	}

	public int maxX() {
		return clip.maxX();
	}

	public int maxY() {
		return clip.maxY();
	}

	public int maxZ() {
		return clip.maxZ();
	}

	/** The terrain for {@link Shape#fill}: the noise in worldgen, the level as it was before the shape in the gallery. */
	public Terrain terrain() {
		return terrain;
	}

	/** Top solid block of the column (under water, plants and trees), from {@link #terrain}. */
	public int ground(int x, int z) {
		return terrain.ground(x, z);
	}

	/** Top block of the column counting water, from {@link #terrain}. */
	public int surface(int x, int z) {
		return terrain.surface(x, z);
	}

	/** True in the gallery and tests, false in worldgen. Shapes never need it to build the same thing. */
	public boolean live() {
		return liveMarks != null;
	}

	/** How many blocks this call changed. */
	public int written() {
		return written;
	}

	/** The registries (books, enchantments, ...). */
	public RegistryAccess registryAccess() {
		return registries;
	}

	public boolean inside(int x, int y, int z) {
		return clip.isInside(x, y, z);
	}

	public boolean inside(BlockPos pos) {
		return clip.isInside(pos);
	}

	/** The block here now; {@code VOID_AIR} outside {@link #clip} (another chunk's blocks are never read). */
	public BlockState get(int x, int y, int z) {
		if (!clip.isInside(x, y, z)) {
			return OUTSIDE;
		}
		if (overlay != null) {
			return overlay.get(x, y, z);
		}
		if (guard != null) {
			guard.add(x, y, z);
		}
		return level.getBlockState(cursor.set(x, y, z));
	}

	public BlockState get(BlockPos pos) {
		return get(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Sets a block inside {@link #clip} (anything outside is dropped). Returns true if the block changed. */
	public boolean set(int x, int y, int z, BlockState state) {
		if (!clip.isInside(x, y, z)) {
			return false;
		}
		if (overlay != null) {
			if (overlay.get(x, y, z) == state) {
				if (overlay.recordsAll()) {
					overlay.set(x, y, z, state);
				}
				return false;
			}
			overlay.set(x, y, z, state);
			written++;
			return true;
		}
		BlockPos.MutableBlockPos pos = cursor.set(x, y, z);
		if (guard != null) {
			guard.add(x, y, z);
		}
		if (level.getBlockState(pos) == state) {
			return false;
		}
		if (guard != null) {
			Guard.enter();
			try {
				level.setBlock(pos, state, FLAGS);
			} finally {
				Guard.exit();
			}
		} else {
			level.setBlock(pos, state, FLAGS);
		}
		written++;
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			level.scheduleTick(pos.immutable(), fluid.getType(), 0);
		}
		if (shapeChecked(state)) {
			mark(pos.immutable());
		}
		BlockPos also = state.getPostProcessPos(level, pos);
		if (also != null) {
			mark(also.immutable());
		}
		return true;
	}

	public boolean set(BlockPos pos, BlockState state) {
		return set(pos.getX(), pos.getY(), pos.getZ(), state);
	}

	/** {@link #set} only if the block there now is one {@code replace} accepts. */
	public boolean setIf(int x, int y, int z, BlockState state, java.util.function.Predicate<BlockState> replace) {
		return clip.isInside(x, y, z) && replace.test(get(x, y, z)) && set(x, y, z, state);
	}

	/**
	 * Puts these stacks in the container block entity here (a chest, barrel, chiseled bookshelf, ...) through its
	 * components, which works in worldgen too (no level is touched). A chiseled bookshelf's slot states are the shape's to
	 * set ({@link Draw#shelf}). Nothing outside {@link #clip} or without a block entity.
	 */
	public boolean contents(BlockPos pos, List<ItemStack> stacks) {
		DataComponentMap components = DataComponentMap.builder().set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks)).build();
		return blockEntity(pos, BlockEntity.class, be -> be.applyComponents(components, DataComponentPatch.EMPTY));
	}

	/**
	 * Edits the block entity here, if it is one of {@code type} (in worldgen it is made from the block on first ask).
	 * Never call a method that touches its level (in worldgen it has none). Nothing outside {@link #clip}.
	 */
	public <T extends BlockEntity> boolean blockEntity(BlockPos pos, Class<T> type, Consumer<T> edit) {
		if (!clip.isInside(pos) || overlay != null) {
			return false;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!type.isInstance(be)) {
			return false;
		}
		edit.accept(type.cast(be));
		be.setChanged();
		if (level instanceof ServerLevel server) {
			BlockState state = server.getBlockState(pos);
			server.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
		return true;
	}

	private void mark(BlockPos pos) {
		if (liveMarks != null) {
			liveMarks.add(pos);
		} else {
			// What vanilla's structure pieces do, in a form any chunk keeps (a finished chunk, in tests, ignores a plain mark).
			ChunkAccess chunk = level.getChunk(pos);
			chunk.addPackedPostProcess(ShortList.of(ProtoChunk.packOffsetCoordinates(pos)), chunk.getSectionIndex(pos.getY()));
		}
	}

	/** Blocks whose state follows their neighbours: updated once the neighbours are written. */
	static boolean shapeChecked(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock || block instanceof FenceGateBlock
				|| block instanceof LadderBlock || block instanceof BaseTorchBlock || block instanceof LanternBlock;
	}
}
