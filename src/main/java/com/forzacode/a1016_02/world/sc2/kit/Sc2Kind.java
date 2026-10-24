package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

/**
 * One kind of structure the kit builds, as a structure task registers it from its own init ({@code S16.init()}):
 *
 * <pre>{@code
 * Sc2Kind.register(new Sc2Kind("s16", Remains.Kind.PYRAMID, Sc16Config::get, PyramidSiting::find, PyramidShape::new,
 *         Sc2Kind.Interiors.FROM_SHAPE));
 * }</pre>
 *
 * The id is what the structure JSON names ({@code "type": "a1016_02:world/sc2", "kind": "s16"}): opaque (D-103), and
 * one id per structure JSON ({@code s16} and {@code s16d} may share a {@link Remains.Kind}). Worldgen threads read the
 * registry, so register in init only.
 *
 * @param id        the JSON's {@code kind}
 * @param remains   what its places are recorded as
 * @param config    its config section, read on the server thread into each {@link Snapshot} (siting reads it there:
 *                  {@code query.snapshot().config("s16", Sc16Config.class)})
 * @param siting    where its starts go ({@link Siting})
 * @param shapes    its shape from a start and the terrain
 * @param interiors the interior of a recorded place
 * @param version   its shape's current version (R12-2, {@link Start}): what a new start is made with; a shape that
 *                  changes raises it and keeps building the older versions for the starts made with them
 */
public record Sc2Kind(String id, Remains.Kind remains, Supplier<? extends Gates> config, Siting siting, Shapes shapes, Interiors interiors,
		int version) {
	private static final Map<String, Sc2Kind> KINDS = new ConcurrentHashMap<>();

	public Sc2Kind {
		Objects.requireNonNull(id);
		Objects.requireNonNull(remains);
		Objects.requireNonNull(config);
		Objects.requireNonNull(siting);
		Objects.requireNonNull(shapes);
		Objects.requireNonNull(interiors);
		version = Math.max(Start.FIRST, version);
	}

	/** A kind whose shape is at its first version. */
	public Sc2Kind(String id, Remains.Kind remains, Supplier<? extends Gates> config, Siting siting, Shapes shapes, Interiors interiors) {
		this(id, remains, config, siting, shapes, interiors, Start.FIRST);
	}

	/** The start tied to a shape version: its own, or this kind's current one if it has none yet. */
	public Start resolve(Start start) {
		return start.version() == Start.LATEST ? start.withVersion(version) : start;
	}

	/** Its shape for this start (tied to its version first) over this terrain. Any thread. */
	public Shape shape(Start start, Terrain terrain) {
		return shapes.create(resolve(start), terrain);
	}

	/** The start tied to its kind's shape version (a kind this jar does not know leaves it as it is). Any thread. */
	public static Start resolved(String kind, Start start) {
		Sc2Kind def = get(kind);
		return def == null ? start : def.resolve(start);
	}

	/**
	 * The two numbers every kind's config section has (its keys {@code a} and {@code b}): the kit refuses a start below
	 * the stage or nearer than the distance to the origin or to the world spawn, before the kind's siting is asked.
	 */
	public interface Gates {
		/** The lowest {@code Stage} level whose newly generated land may hold this kind (1, Traces, by default). */
		int fromStage();

		/** No start nearer than this to (0, 0) or to the world spawn (blocks, from the candidate chunk's middle). */
		int minOriginDistance();
	}

	/**
	 * A kind's shape: deterministic from the start and the terrain (see {@link Shape}), built with the start's version
	 * ({@link Start#versionOr}: a start not tied yet is built with the current one).
	 */
	@FunctionalInterface
	public interface Shapes {
		Shape create(Start start, Terrain terrain);
	}

	/**
	 * The interior of a recorded place, built once per server run (server thread, the place's level loaded or not: never
	 * read blocks here; the noise is {@link Terrains#noise(ServerLevel)}).
	 */
	@FunctionalInterface
	public interface Interiors {
		/** Rebuilds the place's shape from its start (its own version) over the level's noise and asks {@link Shape#interior}. */
		Interiors FROM_SHAPE = (level, kind, place, start, extra) -> kind.shape(start, Terrains.noise(level)).interior();

		Interior of(ServerLevel level, Sc2Kind kind, Remains.Place place, Start start, CompoundTag extra);
	}

	/** Registers a kind (init only; a later one with the same id replaces it). */
	public static void register(Sc2Kind kind) {
		KINDS.put(kind.id(), kind);
	}

	/** Tests only: forgets a kind of a test's own (registered and forgotten within one test's body, on the server thread). */
	public static void unregister(String id) {
		KINDS.remove(id);
	}

	/** The kind with this id, or null (an unknown id never generates and its old pieces place nothing). Any thread. */
	public static @Nullable Sc2Kind get(String id) {
		return KINDS.get(id);
	}

	/** Every registered kind, by id. */
	public static Collection<Sc2Kind> all() {
		return List.copyOf(KINDS.values());
	}

	/** Its gates, read now (server thread). */
	public Gates gates() {
		return config.get();
	}
}
