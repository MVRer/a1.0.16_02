package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import org.jspecify.annotations.Nullable;

/**
 * The one structure type of the second wave ({@code a1016_02:world/sc2}): a structure JSON names its {@code kind}
 * ({@link Sc2Kind}), and the start goes where the kit's gates and the kind's {@link Siting} say, as one
 * {@link Sc2Piece} that rebuilds the kind's {@link Shape}. Vanilla's terrain adaptation stays off (each shape makes its
 * own foundation and seal). A structure JSON:
 *
 * <pre>{@code
 * { "type": "a1016_02:world/sc2", "kind": "s16", "biomes": "#a1016_02:world/s16", "step": "surface_structures",
 *   "spawn_overrides": { "creature": { "bounding_box": "full", "spawns": [] } } }
 * }</pre>
 */
public final class Sc2Structure extends Structure {
	/** A start's box stays within this many chunks of its start chunk (vanilla places pieces only within 8). */
	public static final int MAX_REACH = 8;

	public static final MapCodec<Sc2Structure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
	).apply(i, Sc2Structure::new));

	public static final StructureType<Sc2Structure> TYPE = () -> CODEC;

	private final String kind;

	public Sc2Structure(StructureSettings settings, String kind) {
		super(settings);
		this.kind = kind;
	}

	/** Its kind's id. */
	public String kind() {
		return kind;
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		Sc2Kind def = Sc2Kind.get(kind);
		if (def == null) {
			return Optional.empty();
		}
		Snapshot snapshot = Snapshot.current();
		ChunkPos chunk = context.chunkPos();
		if (Snapshot.refusal(snapshot, def, chunk) != null || !Snapshot.inOverworld(context.chunkGenerator())) {
			return Optional.empty();
		}
		try {
			Terrain terrain = Terrains.noise(context.chunkGenerator(), context.randomState(), context.heightAccessor());
			Siting.Query query = new Siting.Query(chunk, terrain, snapshot, context.seed(), context.random(),
					Heights.lowest(context.heightAccessor(), context.chunkGenerator()), Heights.highest(context.heightAccessor()));
			Sited sited = site(def, query);
			if (!sited.made()) {
				if (sited.shape() != null) {
					A1016_02.LOGGER.debug("[a1016] s2: a {} start in chunk {} refused: {}", kind, chunk, sited.why());
				}
				return Optional.empty();
			}
			Start start = sited.start();
			Sc2Piece piece = new Sc2Piece(kind, start, sited.shape());
			BlockPos origin = start.origin();
			BlockPos probe = new BlockPos(origin.getX(), terrain.ground(origin.getX(), origin.getZ()), origin.getZ());
			return Optional.of(new GenerationStub(probe, builder -> builder.addPiece(piece)));
		} catch (RuntimeException e) {
			ScLog.error("[a1016] s2: a {} start failed in chunk {}", kind, chunk, e);
			return Optional.empty();
		}
	}

	/**
	 * One candidate chunk as the kit decides it (after its gates): the kind's {@link Siting}, its {@link Shape} (the one
	 * siting handed over through {@link Siting#handOver}, else made now), then the kit's own rules on the shape's final
	 * box, which no kind can forget: within {@link #MAX_REACH} chunks of the candidate, and within the level's height
	 * ({@link Heights}: refused, never clipped; the query's {@code minY} and {@code maxY} are the level's own). Any thread.
	 *
	 * @param start  the start, or null if none is made
	 * @param shape  its shape (also when the kit refused it: tests read why), or null if siting found nothing
	 * @param why    {@code made}, {@code siting} (the kind's own rules), or the kit's reason
	 */
	public record Sited(@Nullable Start start, @Nullable Shape shape, String why) {
		public boolean made() {
			return start != null;
		}
	}

	/** {@link Sited}: the kind's siting and shape, then the kit's reach and height on the shape's box. */
	public static Sited site(Sc2Kind def, Siting.Query query) {
		Optional<Start> found = def.siting().find(query);
		if (found.isEmpty()) {
			Siting.handOver(null, null);
			return new Sited(null, null, "siting");
		}
		Start start = found.get();
		Shape shape = Siting.takeHandedOver(start);
		if (shape == null) {
			shape = def.shapes().create(start, query.terrain());
		}
		// Tied to the kind's shape version now (R12-2: the piece saves it and is rebuilt with it; a start siting made is
		// built as the current version, so the shape above is the same).
		start = def.resolve(start);
		BoundingBox box = shape.box();
		if (!withinReach(box, query.chunk())) {
			return new Sited(null, shape, "reach: past " + MAX_REACH + " chunks");
		}
		String height = Heights.refusal(box, query.minY(), query.maxY());
		if (height != null) {
			return new Sited(null, shape, height);
		}
		return new Sited(start, shape, "made");
	}

	/** True if the box stays within {@link #MAX_REACH} chunks of the start chunk. */
	public static boolean withinReach(BoundingBox box, ChunkPos chunk) {
		return box.minX() >> 4 >= chunk.x() - MAX_REACH && box.maxX() >> 4 <= chunk.x() + MAX_REACH && box.minZ() >> 4 >= chunk.z() - MAX_REACH
				&& box.maxZ() >> 4 <= chunk.z() + MAX_REACH;
	}

	/** Why the kit makes no start of this kind in this chunk now, or null (the kind's own siting is asked next). */
	public static @Nullable String refusal(String kind, ChunkPos chunk) {
		Sc2Kind def = Sc2Kind.get(kind);
		return def == null ? "no kind " + kind : Snapshot.refusal(Snapshot.current(), def, chunk);
	}
}
