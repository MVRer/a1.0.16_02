package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import org.jspecify.annotations.Nullable;

/**
 * Builds a kind's shape into a running level exactly as worldgen would: for the gallery, tests and the dev-only gallery
 * command, <b>never game code</b> (a source test holds its callers to the gametest sources, {@code debug/gallery/**} and
 * files named {@code *Exhibit*} or {@code *Gallery*}: nothing generated at runtime, D-093). It writes chunk by chunk
 * through the same {@link Writer} rules (the chunk's writable column, no neighbour updates; shape-checked blocks updated
 * from their neighbours once every chunk is written; light follows each block as the level sets it), over the level as
 * it was before ({@link Terrains.Frozen}). It loads (generates) what it needs, synchronously: the gallery's flat world is
 * cheap; in a real world, stay near loaded land.
 */
public final class Live {
	/**
	 * What was built: its box, its place as it would be recorded (anchor, entrance, apex: for the gallery's cameras), its
	 * shape, kind and start, and how many blocks changed.
	 */
	public record Result(BoundingBox box, Remains.Place place, Shape shape, String kind, Start start, int written) {
		/** Its record, to make it known ({@code WorldData.get(server).sc2().add(result.known())}: the gallery's staged answers). */
		public Sc2Data.Known known() {
			return Sc2Data.Known.of(place, kind, start, shape.extra());
		}
	}

	private Live() {
	}

	/** The level as it is now, each column remembered on first ask (pass it to a kind's shape factory). */
	public static Terrains.Frozen terrain(ServerLevel level) {
		return Terrains.frozen(level);
	}

	/** Builds {@code kind}'s shape for {@code start} here (its terrain frozen before anything is written). */
	public static Result place(ServerLevel level, Sc2Kind kind, Start start) {
		Terrains.Frozen terrain = terrain(level);
		Shape shape = kind.shape(start, terrain);
		terrain.freeze(shape.box());
		return place(level, kind, shape, terrain, start);
	}

	/** Builds a shape of {@code kind} for {@code start} already made over {@code terrain} (a frozen one is frozen over its box first). */
	public static Result place(ServerLevel level, Sc2Kind kind, Shape shape, Terrain terrain, Start start) {
		BoundingBox box = shape.box();
		if (terrain instanceof Terrains.Frozen frozen) {
			frozen.freeze(box);
		}
		int minY = level.getMinY() + 1;
		int maxY = level.getMaxY();
		List<BlockPos> marks = new ArrayList<>();
		int written = 0;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 0);
				level.getChunk(cx, cz);
				BoundingBox column = new BoundingBox(chunk.getMinBlockX(), minY, chunk.getMinBlockZ(), chunk.getMaxBlockX(), maxY, chunk.getMaxBlockZ());
				Optional<BoundingBox> clip = Draw.intersection(column, box);
				if (clip.isEmpty()) {
					continue;
				}
				Writer w = Writer.live(level, clip.get(), terrain, marks);
				shape.fill(w, clip.get());
				written += w.written();
			}
		}
		// What worldgen does when each chunk is finished: shape-checked blocks follow their final neighbours.
		Set<BlockPos> once = new LinkedHashSet<>(marks);
		for (BlockPos pos : once) {
			BlockState state = level.getBlockState(pos);
			BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
			if (updated != state) {
				level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			}
		}
		Remains.Place place = new Remains.Place(kind.remains(), level.dimension(), box, shape.anchor(), shape.entrance(), shape.entranceFacing(),
				shape.apex(), start.seed(), start.variant());
		return new Result(box, place, shape, kind.id(), start, written);
	}

	/**
	 * Worldgen's own path, for tests: the structure's real start for this chunk over the level's generator (its siting and
	 * the kit's gates included), as {@code ChunkGenerator.createStructures} makes it; {@code StructureStart.INVALID_START}
	 * if none.
	 */
	public static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		return structure.value().generate(structure, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()),
				randomState, level.getServer().getStructureTemplateManager(), level.getSeed(), chunk, 0, level, structure.value().biomes()::contains);
	}

	/** The longest {@link #settleLight} waits (real time: the light engine's own threads). */
	private static final long LIGHT_WAIT_MILLIS = 30_000;

	/**
	 * Waits until the light engine has taken in every change queued so far for the chunks of {@code box} (vanilla's own
	 * wait before it sends a chunk), on the server thread through {@code managedBlock} (no tick passes), at most 30 s.
	 * Tests that read light after a build call it first: the engine runs on threads of its own and can be far behind on a
	 * test server's back-to-back ticks. Returns false if it timed out.
	 */
	public static boolean settleLight(ServerLevel level, BoundingBox box) {
		ThreadedLevelLightEngine engine = level.getChunkSource().getLightEngine();
		List<CompletableFuture<?>> pending = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				pending.add(engine.waitForPendingTasks(cx, cz));
			}
		}
		CompletableFuture<Void> all = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
		long until = Util.getMillis() + LIGHT_WAIT_MILLIS;
		level.getServer().managedBlock(() -> all.isDone() || Util.getMillis() > until);
		return all.isDone();
	}

	/**
	 * Worldgen's own path without siting, for tests: the start a structure of the kit would make for {@code start} (its
	 * piece over the level's noise), as if its siting had answered it (a test world's flat land fits no real siting).
	 */
	public static StructureStart startOf(ServerLevel level, Holder<Structure> structure, Start start) {
		if (!(structure.value() instanceof Sc2Structure sc2) || Sc2Kind.get(sc2.kind()) == null) {
			return StructureStart.INVALID_START;
		}
		Shape shape = Sc2Kind.get(sc2.kind()).shape(start, Terrains.noise(level));
		return new StructureStart(sc2, ChunkPos.containing(start.origin()), 0, new PiecesContainer(List.of(new Sc2Piece(sc2.kind(), start, shape))));
	}

	/** A start saved as a chunk saves it and loaded back (new piece objects, the shape rebuilt from the NBT); null if it fails. */
	public static @Nullable StructureStart reload(ServerLevel level, StructureStart start) {
		StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
		return StructureStart.loadStaticStart(context, start.createTag(context, start.getChunkPos()), level.getSeed());
	}

	/**
	 * Worldgen's own path, for tests: places a start in every chunk its box touches, as {@code applyBiomeDecoration} does
	 * (each chunk's writable column, the piece's own {@code postProcess}), then finishes each chunk as vanilla does when it
	 * is promoted ({@code LevelChunk.postProcessGeneration}: the marked blocks follow their neighbours).
	 */
	public static void placeStart(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		List<LevelChunk> chunks = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 0);
				LevelChunk levelChunk = level.getChunk(cx, cz);
				chunks.add(levelChunk);
				BoundingBox writable = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
						chunk.getMaxBlockZ());
				WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed()));
				random.setDecorationSeed(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
				start.placeInChunk(level, level.structureManager(), generator, random, writable, chunk);
			}
		}
		for (LevelChunk chunk : chunks) {
			chunk.postProcessGeneration(level);
		}
	}
}
