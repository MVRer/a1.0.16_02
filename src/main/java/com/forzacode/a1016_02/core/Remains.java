package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * His places (docs/SELF_CREATED_2.md, SC2-0b): every place of the second wave's worldgen that the world has generated
 * so far, where its interior is, and who has been inside. World installs the real one ({@code Services.remains()});
 * the stub knows nothing. Places are recorded once their first chunk is placed, so a place exists here only once some
 * of it is in the world. Server thread only.
 */
public interface Remains {
	/** The kinds of place (code names; debug prints {@link #number()}). */
	enum Kind {
		PYRAMID(16),
		ARCHIVE(17),
		HOLLOW(18),
		LINE(19),
		FLOOR(20);

		private final int number;

		Kind(int number) {
			this.number = number;
		}

		/** The feature number debug lines print (16 to 20). */
		public int number() {
			return number;
		}
	}

	/**
	 * One place in the world.
	 *
	 * @param kind           what it is
	 * @param dimension      its level (the overworld for every kind of this wave)
	 * @param box            everything it wrote (its grounds included); a copy, never shared
	 * @param anchor         its focal point: PYRAMID the centre of the core's floor, ARCHIVE the dais lectern, HOLLOW
	 *                       the grove's centre, FLOOR the house, LINE the middle of the segment
	 * @param entrance       the outer mouth cell of its open way in (the lower cell of the opening, on the outside);
	 *                       a place with no way in (sealed) gives the cell its way in would have had
	 * @param entranceFacing the direction the way in faces, outward (toward the world's origin by D-095)
	 * @param apex           PYRAMID: the apex block; empty for the others
	 * @param seed           its own seed (every random choice of its shape came from it)
	 * @param variant        its kind's variant number (size, sealed, sea, long hall, ...; each kind documents its own)
	 */
	record Place(Kind kind, ResourceKey<Level> dimension, BoundingBox box, BlockPos anchor, BlockPos entrance, Direction entranceFacing,
			Optional<BlockPos> apex, long seed, int variant) {
		public Place {
			Objects.requireNonNull(kind);
			Objects.requireNonNull(dimension);
			box = copy(Objects.requireNonNull(box));
			anchor = anchor.immutable();
			entrance = entrance.immutable();
			Objects.requireNonNull(entranceFacing);
			apex = apex.map(BlockPos::immutable);
		}

		/** A copy of the box (a {@link BoundingBox} is mutable; the place's own never changes). */
		@Override
		public BoundingBox box() {
			return copy(box);
		}

		/** True if {@code pos} is inside the box (not necessarily the interior). */
		public boolean boxHolds(BlockPos pos) {
			return box.isInside(pos);
		}

		/** Distance from {@code pos} to the nearest point of the box, 0 inside it. */
		public double distanceTo(BlockPos pos) {
			double dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
			double dy = Math.max(0, Math.max(box.minY() - pos.getY(), pos.getY() - box.maxY()));
			double dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		private static BoundingBox copy(BoundingBox b) {
			return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
		}
	}

	/** Every known place in this level, oldest first (a new list). */
	List<Place> known(ServerLevel level);

	/**
	 * The place whose interior holds {@code pos} (its kind's own interior test: the core or the pit of a pyramid, the hall
	 * and the cells of an archive, the cavity of a hollow, ...; never the outer walls or the grounds), the oldest first if
	 * two do. Cheap (a chunk-grid index).
	 */
	Optional<Place> containing(ServerLevel level, BlockPos pos);

	/**
	 * The known place of one of these kinds nearest to {@code pos} (by {@link Place#distanceTo}, 0 inside its box) and at
	 * most {@code radius} from it, the oldest first on a tie; empty if none.
	 */
	Optional<Place> nearest(ServerLevel level, BlockPos pos, Set<Kind> kinds, int radius);

	/** True if this player has ever been inside this place's interior (a spectator never is). */
	boolean visited(UUID player, Place place);

	/** The overworld's game time (ticks) when this player was last inside any interior, or -1 if never. */
	long trespassedAt(UUID player);

	/**
	 * Why his places sleep now, or null if they are awake (docs/SELF_CREATED_2_R3.md, D-108): the layer's switch off, the
	 * story's world gates (quiet for good, path C, the endings), or the world's stage under Traces. The places generate
	 * from the world's creation; while they sleep nothing of theirs happens: no answer, no record of a player, no visit
	 * event, no trespass time, no crumb rule. An implementation that does not care is always awake.
	 */
	default @Nullable String asleep(MinecraftServer server) {
		return null;
	}

	/** True if his places are awake now ({@link #asleep} of the installed remains is null). Server thread. */
	static boolean awake(MinecraftServer server) {
		return Services.remains().asleep(server) == null;
	}

	/**
	 * Where this place shows itself from far (its error sign's point, D-110: where the gallery's sign views aim and the
	 * crumbs' lines begin): its kind's own point, else its entrance. Works for a {@link #predicted} place too.
	 */
	default BlockPos sign(Place place) {
		return place.entrance();
	}

	/**
	 * The places worldgen has decided within {@code radius} of {@code pos} (by their box) in this level, of these kinds,
	 * generated or not yet (D-112's {@code Decided}: only candidates someone asked about, its own start, a crumb or
	 * {@code /locate}), nearest first. Their box, anchor, entrance, facing, seed and variant are what the start will be.
	 */
	default List<Place> predicted(ServerLevel level, BlockPos pos, int radius, Set<Kind> kinds) {
		return List.of();
	}

	/** Nothing known: the default until world installs the real one. */
	final class Stub implements Remains {
		@Override
		public List<Place> known(ServerLevel level) {
			return List.of();
		}

		@Override
		public Optional<Place> containing(ServerLevel level, BlockPos pos) {
			return Optional.empty();
		}

		@Override
		public Optional<Place> nearest(ServerLevel level, BlockPos pos, Set<Kind> kinds, int radius) {
			return Optional.empty();
		}

		@Override
		public boolean visited(UUID player, Place place) {
			return false;
		}

		@Override
		public long trespassedAt(UUID player) {
			return -1;
		}

		@Override
		public @Nullable String asleep(MinecraftServer server) {
			return "no places";
		}
	}
}
