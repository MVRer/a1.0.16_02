package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import org.jspecify.annotations.Nullable;

/**
 * The placement of a marker structure ({@code a1016_02:world/sc2a}, docs/SELF_CREATED_2_R3.md, D-112): the chunks a
 * feature that is not a structure (the line, 19) says it stands in, one each, so {@code /locate} finds it. Its JSON:
 *
 * <pre>{@code
 * { "type": "a1016_02:world/sc2a", "kind": "s19" }
 * }</pre>
 *
 * The feature registers its {@link Marks} under its kind's id ({@code s19.LineMarker}); the marker structure
 * ({@code {"type": "a1016_02:world/sc2", "kind": "s19", "marker": true}}) makes a start of one empty piece in each of
 * those chunks and records nothing. Registered as a placement type by the feature's marker ({@code LineMarker.init}).
 */
public final class AxesPlacement implements StructurePlacement, KitPlacement {
	public static final MapCodec<AxesPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(AxesPlacement::kind)
	).apply(i, AxesPlacement::new));

	/** What a feature marks: its chunks in this world now (empty while it builds nothing). */
	@FunctionalInterface
	public interface Marks {
		/**
		 * The chunks it stands in for this snapshot (null: none), a level from {@code minY} to {@code maxY} (exclusive).
		 * Any thread; cheap enough to ask for every chunk worldgen makes (it is cached per snapshot here).
		 */
		List<ChunkPos> chunks(@Nullable Snapshot snapshot, int minY, int maxY);
	}

	private record Cached(@Nullable Snapshot snapshot, List<ChunkPos> chunks) {
	}

	private static final Map<String, Marks> MARKS = new ConcurrentHashMap<>();
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	/** The overworld's height the marks are laid out for (the live one's, from {@link Snapshot#refresh}). */
	private static volatile int minY = -64;
	private static volatile int maxY = 320;

	private final String kind;

	public AxesPlacement(String kind) {
		this.kind = kind;
	}

	public String kind() {
		return kind;
	}

	/** Registers a feature's marks under its kind's id (init only). */
	public static void register(String kind, Marks marks) {
		MARKS.put(kind, marks);
	}

	/** The live overworld's height (from the snapshot's refresh). */
	static void height(int min, int max) {
		minY = min;
		maxY = max;
	}

	/** The chunks {@code kind} marks for this snapshot (cached while the snapshot stays the same). Any thread. */
	public static List<ChunkPos> marked(String kind, @Nullable Snapshot snapshot) {
		Marks marks = MARKS.get(kind);
		if (marks == null || snapshot == null) {
			return List.of();
		}
		Cached cached = CACHE.get(kind);
		if (cached != null && cached.snapshot() == snapshot) {
			return cached.chunks();
		}
		List<ChunkPos> chunks = List.copyOf(marks.chunks(snapshot, minY, maxY));
		CACHE.put(kind, new Cached(snapshot, chunks));
		return chunks;
	}

	@Override
	public boolean isStructureChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		Snapshot snapshot = Snapshot.current();
		if (snapshot == null || snapshot.seed() != state.getLevelSeed()) {
			return false;
		}
		for (ChunkPos chunk : marked(kind, snapshot)) {
			if (chunk.x() == sourceX && chunk.z() == sourceZ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<ChunkPos> chunks(long seed) {
		Snapshot snapshot = Snapshot.current();
		return snapshot == null || snapshot.seed() != seed ? List.of() : marked(kind, snapshot);
	}

	@Override
	public MapCodec<AxesPlacement> codec() {
		return CODEC;
	}
}
