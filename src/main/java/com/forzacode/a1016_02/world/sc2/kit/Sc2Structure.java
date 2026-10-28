package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;

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
 *
 * Round 3 (docs/SELF_CREATED_2_R3.md): each candidate is decided once per world ({@link Decided}: the kind's siting,
 * the kit's reach and height, the live world's exclusion zones and the JSON's optional {@code avoid} list, vanilla's
 * biome check at the stub), whoever asks first; a start reads the kept decision. {@code "avoid": [{"other_set":
 * "minecraft:ancient_cities", "chunk_count": 4}]} keeps it off another set's placement chunks (a set's own
 * {@code exclusion_zone} holds one other set only). {@code "marker": true} makes a marker of a feature instead (its
 * placement {@link AxesPlacement}: one empty piece at the ground of each marked chunk, nothing recorded) so
 * {@code /locate} finds the feature.
 */
public final class Sc2Structure extends Structure {
	/** A start's box stays within this many chunks of its start chunk (vanilla places pieces only within 8). */
	public static final int MAX_REACH = 8;

	/** Another set whose placement chunks a start keeps {@code chunkCount} chunks away from (vanilla's exclusion zone's form). */
	public record Avoid(Holder<StructureSet> otherSet, int chunkCount) {
		static final Codec<Avoid> CODEC = AbstractSpreadingStructurePlacement.ExclusionZone.CODEC.xmap(zone -> new Avoid(zone.otherSet(), zone.chunkCount()),
				avoid -> new AbstractSpreadingStructurePlacement.ExclusionZone(avoid.otherSet(), avoid.chunkCount()));
	}

	public static final MapCodec<Sc2Structure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),
			Codec.BOOL.optionalFieldOf("marker", false).forGetter(s -> s.marker),
			Avoid.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(s -> s.avoid)
	).apply(i, Sc2Structure::new));

	public static final StructureType<Sc2Structure> TYPE = () -> CODEC;

	private final String kind;
	private final boolean marker;
	private final List<Avoid> avoid;

	public Sc2Structure(StructureSettings settings, String kind) {
		this(settings, kind, false, List.of());
	}

	public Sc2Structure(StructureSettings settings, String kind, boolean marker, List<Avoid> avoid) {
		super(settings);
		this.kind = kind;
		this.marker = marker;
		this.avoid = List.copyOf(avoid);
	}

	/** Its kind's id. */
	public String kind() {
		return kind;
	}

	/** True for a feature's marker ({@link AxesPlacement}): an empty start, for {@code /locate} only. */
	public boolean marker() {
		return marker;
	}

	/** The other sets it keeps away from (its JSON's {@code avoid}). */
	public List<Avoid> avoid() {
		return avoid;
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (marker) {
			return markerPoint(context);
		}
		Optional<Decided.Decision> decided = decision(context);
		if (decided.isEmpty() || !decided.get().made()) {
			return Optional.empty();
		}
		Decided.Decision d = decided.get();
		Sc2Piece piece = new Sc2Piece(kind, d.start(), d.box());
		return Optional.of(new GenerationStub(d.probe(), builder -> builder.addPiece(piece)));
	}

	/**
	 * This structure's decision for the context's chunk: empty if the kit's gates refuse now (never kept), else the kept
	 * one or one made now ({@link Decided#decide}: the exclusion zones and {@code avoid}, the kind's siting, the kit's
	 * reach and height, vanilla's biome check at the stub). Any thread.
	 */
	public Optional<Decided.Decision> decision(GenerationContext context) {
		Sc2Kind def = Sc2Kind.get(kind);
		if (def == null || marker) {
			return Optional.empty();
		}
		Snapshot snapshot = Snapshot.current();
		ChunkPos chunk = context.chunkPos();
		if (Snapshot.refusal(snapshot, def, chunk) != null || !Snapshot.inOverworld(context.chunkGenerator())) {
			return Optional.empty();
		}
		try {
			return Optional.of(Decided.decide(kind, chunk, context.chunkGenerator(), context.seed(), () -> decideNow(def, context, snapshot)));
		} catch (RuntimeException e) {
			ScLog.error("[a1016] s2: a {} start failed in chunk {}", kind, chunk, e);
			return Optional.empty();
		}
	}

	/**
	 * This structure's decision before the places' spacing rule ({@link Decided#yields}): the gates, then the kept
	 * siting or one made now. Empty if the gates refuse. Any thread.
	 */
	Optional<Decided.Decision> sitedDecision(GenerationContext context) {
		Sc2Kind def = Sc2Kind.get(kind);
		if (def == null || marker) {
			return Optional.empty();
		}
		Snapshot snapshot = Snapshot.current();
		ChunkPos chunk = context.chunkPos();
		if (Snapshot.refusal(snapshot, def, chunk) != null || !Snapshot.inOverworld(context.chunkGenerator())) {
			return Optional.empty();
		}
		return Optional.of(Decided.sited(kind, chunk, context.chunkGenerator(), context.seed(), () -> sitedNow(def, context, snapshot)));
	}

	private Decided.Decision decideNow(Sc2Kind def, GenerationContext context, Snapshot snapshot) {
		Decided.Decision sited = Decided.sited(kind, context.chunkPos(), context.chunkGenerator(), context.seed(), () -> sitedNow(def, context, snapshot));
		if (!sited.made()) {
			return sited;
		}
		String yields = Decided.yields(this, sited, context);
		return yields == null ? sited : Decided.Decision.refused(kind, context.chunkPos(), yields);
	}

	private Decided.Decision sitedNow(Sc2Kind def, GenerationContext context, Snapshot snapshot) {
		ChunkPos chunk = context.chunkPos();
		String excluded = Decided.excluded(this, context.chunkGenerator(), chunk);
		if (excluded != null) {
			return Decided.Decision.refused(kind, chunk, excluded);
		}
		Terrain terrain = Terrains.noise(context.chunkGenerator(), context.randomState(), context.heightAccessor());
		Siting.Query query = new Siting.Query(chunk, terrain, snapshot, context.seed(), context.random(),
				Heights.lowest(context.heightAccessor(), context.chunkGenerator()), Heights.highest(context.heightAccessor()));
		Sited sited = site(def, query);
		if (!sited.made()) {
			if (sited.shape() != null) {
				A1016_02.LOGGER.debug("[a1016] s2: a {} start in chunk {} refused: {}", kind, chunk, sited.why());
			}
			return Decided.Decision.refused(kind, chunk, sited.why());
		}
		Start start = sited.start();
		BlockPos origin = start.origin();
		int probeY = terrain.ground(origin.getX(), origin.getZ());
		Decided.Decision made = Decided.Decision.made(kind, chunk, start, sited.shape(), probeY);
		// Vanilla checks the biome at the stub after this; checked here too, so what is kept is what generates.
		if (!context.isValidBiome(new GenerationStub(made.probe(), builder -> {
		}))) {
			return Decided.Decision.refused(kind, chunk, "biome");
		}
		return made;
	}

	/** A marker's start: one empty piece at the ground of a marked chunk's middle ({@link AxesPlacement}). */
	private Optional<GenerationStub> markerPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		Snapshot snapshot = Snapshot.current();
		if (snapshot == null || !Snapshot.inOverworld(context.chunkGenerator()) || !AxesPlacement.marked(kind, snapshot).contains(chunk)) {
			return Optional.empty();
		}
		Terrain terrain = Terrains.noise(context.chunkGenerator(), context.randomState(), context.heightAccessor());
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		BlockPos at = new BlockPos(x, terrain.ground(x, z), z);
		Sc2Piece piece = Sc2Piece.marker(kind, at);
		return Optional.of(new GenerationStub(at, builder -> builder.addPiece(piece)));
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

	/** Why the kit makes no start of this kind in this chunk now, or null (the kind's own siting is asked next, or {@link Decided}). */
	public static @Nullable String refusal(String kind, ChunkPos chunk) {
		Sc2Kind def = Sc2Kind.get(kind);
		return def == null ? "no kind " + kind : Snapshot.refusal(Snapshot.current(), def, chunk);
	}
}
