package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Hash;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import org.jspecify.annotations.Nullable;

/**
 * What worldgen decided for each candidate chunk of each kind in this world (docs/SELF_CREATED_2_R3.md, "Decided"):
 * refused, or made with its start and what the start will be (box, anchor, entrance and facing, sign point, variant,
 * version). Asked first by the kind's own start ({@link Sc2Structure#findGenerationPoint}), and by whoever needs to
 * know earlier: the crumbs ({@link #around}, any worldgen thread), {@code /locate} (the server thread, through vanilla's
 * {@code StructureCheck}), the find tests. Each candidate is sited once per world, by the first asker; the others wait
 * for that answer. A decision is kept: a later change of the config or the jar never changes what a candidate became
 * (its version is the one it was made with, R12-2), so a crumb never leads to a place that will not be there.
 * <p>
 * Only the live overworld's generator is cached (bound by {@link Snapshot#refresh} through {@link #attach}), and only
 * while the snapshot is the live one: a test's own noise or snapshot ({@link Snapshot#install}) decides without keeping
 * ({@link Snapshot#installLive} keeps). The kit's gates ({@link Snapshot#refusal}:
 * the switch, the spawn not known yet, the stage, R0) come before and are never kept. Saved in {@link Sc2Data} under
 * {@code d} (8 bytes a refused candidate, 72 a made one), loaded and drained with {@link Plans}.
 */
public final class Decided {
	/** The side of the squares {@link #around} decides at once and keeps (chunks: 512 blocks). */
	public static final int REGION_CHUNKS = 32;
	/** How many longs one made record takes when saved. */
	static final int MADE_LONGS = 9;

	/**
	 * One candidate as worldgen decides it.
	 *
	 * @param kind     the kind's id
	 * @param chunk    the candidate (placement) chunk: the start's chunk
	 * @param start    the start, or null if refused
	 * @param box      what the start will write (null if refused)
	 * @param anchor   its place's anchor
	 * @param entrance its outer mouth cell
	 * @param facing   the way the way in faces, outward
	 * @param sign     its sign point ({@link Shape#sign}: where it shows itself from far)
	 * @param probeY   the height of vanilla's biome probe (the ground at the origin's column)
	 * @param why      {@code made}, or why not (in memory only: a loaded refusal says {@code kept})
	 */
	public record Decision(String kind, ChunkPos chunk, @Nullable Start start, @Nullable BoundingBox box, BlockPos anchor, BlockPos entrance,
			Direction facing, BlockPos sign, int probeY, String why) {
		public static Decision refused(String kind, ChunkPos chunk, String why) {
			return new Decision(kind, chunk, null, null, BlockPos.ZERO, BlockPos.ZERO, Direction.NORTH, BlockPos.ZERO, 0, why);
		}

		public static Decision made(String kind, ChunkPos chunk, Start start, Shape shape, int probeY) {
			return new Decision(kind, chunk, start, shape.box(), shape.anchor().immutable(), shape.entrance().immutable(), shape.entranceFacing(),
					shape.sign().immutable(), probeY, "made");
		}

		public boolean made() {
			return start != null;
		}

		/** Where vanilla's biome check reads (the stub's position). */
		public BlockPos probe() {
			return start == null ? BlockPos.ZERO : new BlockPos(start.origin().getX(), probeY, start.origin().getZ());
		}

		/** The place its start will be recorded as (no apex: a predicted pyramid's apex is not kept), or empty if refused. */
		public Optional<Remains.Place> place() {
			Sc2Kind def = Sc2Kind.get(kind);
			if (start == null || box == null || def == null) {
				return Optional.empty();
			}
			return Optional.of(new Remains.Place(def.remains(), Level.OVERWORLD, box, anchor, entrance, facing, Optional.empty(), start.seed(),
					start.variant()));
		}

		long[] toLongs() {
			if (start == null || box == null) {
				return new long[] {chunk.pack()};
			}
			// facing 3 bits, version 8, the probe's height 16, the variant whole (32: a kind may pack much into it).
			long meta = (facing.get2DDataValue() & 0x7L) | (start.versionOr(Start.FIRST) & 0xFFL) << 3 | ((probeY + 32768L) & 0xFFFFL) << 11
					| (start.variant() & 0xFFFFFFFFL) << 27;
			return new long[] {chunk.pack(), start.origin().asLong(), start.seed(), BlockPos.asLong(box.minX(), box.minY(), box.minZ()),
					BlockPos.asLong(box.maxX(), box.maxY(), box.maxZ()), anchor.asLong(), entrance.asLong(), sign.asLong(), meta};
		}

		static Decision fromLongs(String kind, long[] a, int at) {
			ChunkPos chunk = ChunkPos.unpack(a[at]);
			long meta = a[at + 8];
			Direction facing = Direction.from2DDataValue((int) (meta & 0x7L));
			int version = Math.max(Start.FIRST, (int) (meta >>> 3 & 0xFFL));
			int probeY = (int) ((meta >>> 11 & 0xFFFFL) - 32768L);
			int variant = (int) (meta >>> 27 & 0xFFFFFFFFL);
			BlockPos min = BlockPos.of(a[at + 3]);
			BlockPos max = BlockPos.of(a[at + 4]);
			Start start = new Start(BlockPos.of(a[at + 1]), facing, variant, a[at + 2], version);
			return new Decision(kind, chunk, start, new BoundingBox(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()),
					BlockPos.of(a[at + 5]), BlockPos.of(a[at + 6]), facing, BlockPos.of(a[at + 7]), probeY, "made");
		}
	}

	private record Key(String kind, long chunk) {
	}

	/** The live overworld this cache belongs to (and the kit's structure sets in it, for the spacing rule). */
	private record Live(Object generator, long seed, ChunkGeneratorStructureState state, Map<Structure, List<StructurePlacement>> placements,
			List<StructureSet> sets) {
	}

	/** No two places' boxes nearer than this (blocks, across x and z; D-109's T6): the lesser place yields. */
	public static final int NEAR = 96;

	private static final Map<Key, CompletableFuture<Decision>> DECISIONS = new ConcurrentHashMap<>();
	/** Each candidate's siting before the spacing rule (this run only; a made one is what {@link #yields} compares). */
	private static final Map<Key, CompletableFuture<Decision>> SITED = new ConcurrentHashMap<>();
	private static final Map<Long, Decision> BY_SEED = new ConcurrentHashMap<>();
	private static final Queue<Decision> NEW = new ConcurrentLinkedQueue<>();
	private static final Map<Long, CompletableFuture<List<Decision>>> REGIONS = new ConcurrentHashMap<>();
	private static volatile @Nullable Live live;
	private static volatile boolean ready;

	private Decided() {
	}

	// --- lifecycle (through Plans and Snapshot) ---

	/** Takes this world's decisions (server thread, at start, with {@link Plans#load}). */
	static void load(Sc2Data data) {
		DECISIONS.clear();
		SITED.clear();
		BY_SEED.clear();
		NEW.clear();
		REGIONS.clear();
		data.decidedArrays().forEach((group, longs) -> {
			boolean made = group.endsWith("+");
			String kind = made ? group.substring(0, group.length() - 1) : group;
			if (made) {
				for (int at = 0; at + MADE_LONGS <= longs.length; at += MADE_LONGS) {
					keep(Decision.fromLongs(kind, longs, at));
				}
			} else {
				for (long chunk : longs) {
					keep(Decision.refused(kind, ChunkPos.unpack(chunk), "kept"));
				}
			}
		});
		ready = true;
	}

	private static void keep(Decision d) {
		DECISIONS.put(new Key(d.kind(), d.chunk().pack()), CompletableFuture.completedFuture(d));
		if (d.made()) {
			BY_SEED.put(d.start().seed(), d);
		}
	}

	/**
	 * Binds the cache to the live overworld (its generator, seed and structure state), from {@link Snapshot#refresh}.
	 * Server thread; cheap when nothing changed.
	 */
	static void attach(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		ChunkGenerator generator = overworld.getChunkSource().getGenerator();
		long seed = overworld.getSeed();
		Live now = live;
		if (now != null && now.generator() == generator && now.seed() == seed) {
			return;
		}
		ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
		Map<Structure, List<StructurePlacement>> placements = new IdentityHashMap<>();
		List<StructureSet> sets = new ArrayList<>();
		for (Holder<StructureSet> set : server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().toList()) {
			boolean kit = false;
			for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
				if (entry.structure().value() instanceof Sc2Structure sc2) {
					placements.computeIfAbsent(entry.structure().value(), k -> new ArrayList<>()).add(set.value().placement());
					kit |= !sc2.marker();
				}
			}
			if (kit && state.possibleStructureSets().contains(set)) {
				sets.add(set.value());
			}
		}
		live = new Live(generator, seed, state, placements, List.copyOf(sets));
	}

	/** Server stop (with the snapshot): nothing is bound until the next world's. */
	static void detach() {
		live = null;
	}

	/** True while something decided waits to be kept. Any thread. */
	static boolean pending() {
		return !NEW.isEmpty();
	}

	/** Keeps everything decided since the last drain in {@code data}; returns how many. Server thread. */
	static int drain(Sc2Data data) {
		int kept = 0;
		for (Decision d = NEW.poll(); d != null; d = NEW.poll()) {
			data.decide(d.made() ? d.kind() + "+" : d.kind(), d.toLongs());
			kept++;
		}
		return kept;
	}

	/** Server stop (with {@link Plans#clear}): nothing is decided until the next world's are loaded. */
	static void clear() {
		ready = false;
		DECISIONS.clear();
		SITED.clear();
		BY_SEED.clear();
		NEW.clear();
		REGIONS.clear();
	}

	// --- reading and deciding ---

	/** True if decisions with this generator and seed are kept (the live overworld's, once loaded). Any thread. */
	public static boolean keeps(Object generator, long seed) {
		Live now = live;
		return ready && now != null && now.generator() == generator && now.seed() == seed && Snapshot.liveGenerator() == generator;
	}

	/**
	 * The decision for {@code kind} in {@code chunk}: the kept one, else {@code compute}'s, kept (on any thread the first
	 * asker computes and the others wait for its answer). Without the live generator and seed it is computed and not kept.
	 */
	public static Decision decide(String kind, ChunkPos chunk, Object generator, long seed, Supplier<Decision> compute) {
		if (!keeps(generator, seed)) {
			return compute.get();
		}
		Key key = new Key(kind, chunk.pack());
		CompletableFuture<Decision> mine = new CompletableFuture<>();
		CompletableFuture<Decision> before = DECISIONS.putIfAbsent(key, mine);
		if (before != null) {
			return before.join();
		}
		try {
			Decision d = compute.get();
			if (d.made()) {
				BY_SEED.put(d.start().seed(), d);
			}
			NEW.add(d);
			mine.complete(d);
			return d;
		} catch (RuntimeException | Error e) {
			DECISIONS.remove(key, mine);
			mine.completeExceptionally(e);
			throw e;
		}
	}

	/** Tests: keeps a decision as if its start had made it (no save until the next drain). */
	public static void put(Decision d) {
		keep(d);
		NEW.add(d);
	}

	/** Tests: forgets every decision of this kind here (not in any data) and the squares {@link #around} kept. */
	public static void forget(String kind) {
		DECISIONS.keySet().removeIf(key -> key.kind().equals(kind));
		SITED.keySet().removeIf(key -> key.kind().equals(kind));
		BY_SEED.values().removeIf(d -> d.kind().equals(kind));
		NEW.removeIf(d -> d.kind().equals(kind));
		REGIONS.clear();
	}

	/**
	 * The candidate's siting before the spacing rule, kept for this run (the same futures as {@link #decide}): what
	 * {@link #yields} compares a neighbour by. Not saved: a later run sites again what it needs (a decided candidate's
	 * answer is {@link #decide}'s). Any thread.
	 */
	static Decision sited(String kind, ChunkPos chunk, Object generator, long seed, Supplier<Decision> compute) {
		if (!keeps(generator, seed)) {
			return compute.get();
		}
		Key key = new Key(kind, chunk.pack());
		CompletableFuture<Decision> mine = new CompletableFuture<>();
		CompletableFuture<Decision> before = SITED.putIfAbsent(key, mine);
		if (before != null) {
			return before.join();
		}
		try {
			Decision d = compute.get();
			mine.complete(d);
			return d;
		} catch (RuntimeException | Error e) {
			SITED.remove(key, mine);
			mine.completeExceptionally(e);
			throw e;
		}
	}

	/**
	 * A made place's precedence in the spacing rule: the rarer kind first (pyramid, hollow, floor, archive: the common
	 * archive yields to the others, never they to it), then the larger box (its area across x and z), then a hash of the
	 * world's seed, the kind and the chunk. Strict, so the rule never loops.
	 */
	public static long priority(long worldSeed, String kind, ChunkPos chunk, BoundingBox box) {
		Sc2Kind def = Sc2Kind.get(kind);
		long rank = def == null ? 0 : switch (def.remains()) {
			case PYRAMID -> 4;
			case HOLLOW -> 3;
			case FLOOR -> 2;
			case ARCHIVE -> 1;
			case LINE -> 0;
		};
		long area = Math.min((long) box.getXSpan() * box.getZSpan(), (1L << 20) - 1);
		long hash = Hash.of(worldSeed, kind.hashCode(), chunk.x(), chunk.z()) & 0xFFFFFFFFL;
		return rank << 56 | area << 32 | hash;
	}

	/** True if two boxes come nearer than {@link #NEAR} across x and z. */
	public static boolean near(BoundingBox a, BoundingBox b) {
		int gx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
		int gz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));
		return (long) gx * gx + (long) gz * gz < (long) NEAR * NEAR;
	}

	/**
	 * The places' spacing rule (T6): why a made siting yields, or null. It yields to any place of a kit set (its own
	 * included) whose start goes in another candidate chunk, whose box comes within {@link #NEAR} of its own, whose
	 * {@link #priority} is higher and which is made in the end itself (that one yields only to higher ones: the rule is the
	 * same as keeping, in order of precedence, every place no kept one is near). Asked by the start's own decision; only in
	 * the live world (a test's own noise applies it on its own: {@code SelfCreated3FindTests}). Any thread; sites the
	 * neighbours it needs (once per world, as their own starts would).
	 */
	static @Nullable String yields(Sc2Structure structure, Decision mine, Structure.GenerationContext context) {
		Live now = live;
		if (now == null || now.generator() != context.chunkGenerator() || mine.box() == null) {
			return null;
		}
		BoundingBox box = mine.box();
		long minePriority = priority(context.seed(), mine.kind(), mine.chunk(), box);
		int pad = NEAR + Sc2Structure.MAX_REACH * 16 + 16;
		int c0x = (box.minX() - pad) >> 4;
		int c0z = (box.minZ() - pad) >> 4;
		int c1x = (box.maxX() + pad) >> 4;
		int c1z = (box.maxZ() + pad) >> 4;
		for (StructureSet set : now.sets()) {
			StructurePlacement placement = set.placement();
			for (ChunkPos chunk : candidates(placement, now.state().getLevelSeed(), c0x, c0z, c1x, c1z)) {
				if (chunk.equals(mine.chunk()) || !placement.isStructureChunk(now.state(), chunk.x(), chunk.z())) {
					continue;
				}
				for (StructureSet.StructureSelectionEntry entry : order(set, now.state().getLevelSeed(), chunk)) {
					if (!(entry.structure().value() instanceof Sc2Structure other) || other.marker()) {
						continue;
					}
					Structure.GenerationContext there = new Structure.GenerationContext(context.registryAccess(), context.chunkGenerator(),
							context.biomeSource(), context.climateSampler(), context.randomState(), context.structureTemplateManager(), context.seed(), chunk,
							context.heightAccessor(), entry.structure().value().biomes()::contains);
					Optional<Decision> sited = other.sitedDecision(there);
					if (sited.isEmpty() || !sited.get().made()) {
						continue;
					}
					Decision d = sited.get();
					if (near(box, d.box()) && priority(context.seed(), d.kind(), chunk, d.box()) > minePriority
							&& other.decision(there).map(Decision::made).orElse(false)) {
						return "near another place";
					}
					// The first of the set's structures sited here is the one worldgen makes.
					break;
				}
			}
		}
		return null;
	}

	/** A set's structures in vanilla's weighted order for this placement chunk. */
	public static List<StructureSet.StructureSelectionEntry> order(StructureSet set, long levelSeed, ChunkPos chunk) {
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		if (options.size() <= 1) {
			return options;
		}
		List<StructureSet.StructureSelectionEntry> out = new ArrayList<>();
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(levelSeed, chunk.x(), chunk.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry option : options) {
			total += option.weight();
		}
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				choice -= option.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry selected = options.remove(index);
			total -= selected.weight();
			out.add(selected);
		}
		return out;
	}

	/** The kept decision for {@code kind} in {@code chunk}, or null if nobody has asked yet (or it is still being made). */
	public static @Nullable Decision get(String kind, ChunkPos chunk) {
		CompletableFuture<Decision> f = DECISIONS.get(new Key(kind, chunk.pack()));
		return f != null && f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
	}

	/** The made decision whose start has this seed (a place's {@code seed}), or null. Any thread. */
	public static @Nullable Decision bySeed(long seed) {
		return BY_SEED.get(seed);
	}

	/** Every made decision kept so far (a new list). Any thread. */
	public static List<Decision> made() {
		return List.copyOf(BY_SEED.values());
	}

	/** How many candidates are kept (made and refused). Any thread. */
	public static int size() {
		return DECISIONS.size();
	}

	/**
	 * Why the live world's placements forbid {@code structure} in {@code chunk}, or null: a set's own exclusion zone
	 * (vanilla's {@code /locate} never asks it) and the structure's {@code avoid} list. Null (nothing known) for any
	 * generator but the live overworld's. Any thread.
	 */
	static @Nullable String excluded(Sc2Structure structure, Object generator, ChunkPos chunk) {
		Live now = live;
		if (now == null || now.generator() != generator) {
			return null;
		}
		return excluded(structure, now.state(), now.placements().getOrDefault(structure, List.of()), chunk);
	}

	/** {@link #excluded} with a given structure state and the structure's placements (the find tests' worlds). */
	public static @Nullable String excluded(Sc2Structure structure, ChunkGeneratorStructureState state, List<StructurePlacement> placements, ChunkPos chunk) {
		for (StructurePlacement placement : placements) {
			if (placement instanceof net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement spreading
					&& !spreading.applyInteractionsWithOtherStructures(state, chunk.x(), chunk.z())) {
				return "another set's zone";
			}
		}
		for (Sc2Structure.Avoid avoid : structure.avoid()) {
			if (state.hasStructureChunkInRange(avoid.otherSet(), chunk.x(), chunk.z(), avoid.chunkCount())) {
				return "avoids another set";
			}
		}
		return null;
	}

	// --- asking ahead (crumbs, locate, tests) ---

	/**
	 * Every place worldgen makes (or has made) in this level whose start chunk lies within {@code radius} blocks
	 * (chunk-wise, a square) of ({@code x}, {@code z}), nearest first: every kit structure set's candidates there,
	 * each decided as {@code ChunkGenerator.createStructures} would (the set's own placement check, its structures in
	 * vanilla's weighted order). Decided in squares of {@value #REGION_CHUNKS} chunks, each kept once decided (while the
	 * snapshot lets anything generate). The first asker pays the sitings (once per candidate per world); later asks are a
	 * few map reads. Any thread; the overworld only (another level gets none).
	 */
	public static List<Decision> around(ServerLevel level, int x, int z, int radius) {
		if (level.dimension() != Level.OVERWORLD) {
			return List.of();
		}
		int r0x = Math.floorDiv((x - radius) >> 4, REGION_CHUNKS);
		int r1x = Math.floorDiv((x + radius) >> 4, REGION_CHUNKS);
		int r0z = Math.floorDiv((z - radius) >> 4, REGION_CHUNKS);
		int r1z = Math.floorDiv((z + radius) >> 4, REGION_CHUNKS);
		int c0x = (x - radius) >> 4;
		int c1x = (x + radius) >> 4;
		int c0z = (z - radius) >> 4;
		int c1z = (z + radius) >> 4;
		List<Decision> out = new ArrayList<>();
		for (int rx = r0x; rx <= r1x; rx++) {
			for (int rz = r0z; rz <= r1z; rz++) {
				for (Decision d : region(level, rx, rz)) {
					if (d.chunk().x() >= c0x && d.chunk().x() <= c1x && d.chunk().z() >= c0z && d.chunk().z() <= c1z) {
						out.add(d);
					}
				}
			}
		}
		out.sort(Comparator.comparingLong(d -> {
			long dx = d.chunk().getMiddleBlockX() - x;
			long dz = d.chunk().getMiddleBlockZ() - z;
			return dx * dx + dz * dz;
		}));
		return out;
	}

	/** The made decisions of one region (kept while the snapshot lets anything generate). */
	private static List<Decision> region(ServerLevel level, int rx, int rz) {
		Snapshot snapshot = Snapshot.current();
		boolean keep = snapshot != null && snapshot.enabled() && snapshot.spawnKnown()
				&& keeps(level.getChunkSource().getGenerator(), level.getSeed());
		if (!keep) {
			return decideRegion(level, rx, rz);
		}
		long key = ChunkPos.pack(rx, rz);
		CompletableFuture<List<Decision>> mine = new CompletableFuture<>();
		CompletableFuture<List<Decision>> before = REGIONS.putIfAbsent(key, mine);
		if (before != null) {
			return before.join();
		}
		try {
			List<Decision> made = decideRegion(level, rx, rz);
			mine.complete(made);
			return made;
		} catch (RuntimeException | Error e) {
			REGIONS.remove(key, mine);
			mine.completeExceptionally(e);
			throw e;
		}
	}

	private static List<Decision> decideRegion(ServerLevel level, int rx, int rz) {
		int c0x = rx * REGION_CHUNKS;
		int c0z = rz * REGION_CHUNKS;
		return decideIn(level, c0x, c0z, c0x + REGION_CHUNKS - 1, c0z + REGION_CHUNKS - 1);
	}

	/**
	 * Every place worldgen makes (or has made) in this level whose start chunk lies in this rectangle of chunks (inclusive),
	 * decided as {@link #around} decides its regions but only these candidates, and not kept as a region (each candidate's
	 * own decision is kept, through its start's path, so a second ask is a few map reads): for a narrow strip that would
	 * cost whole regions (FIX-R3-E: the line keeps clear of the places along its half-axis). Any thread; the overworld only.
	 */
	public static List<Decision> within(ServerLevel level, int c0x, int c0z, int c1x, int c1z) {
		if (level.dimension() != Level.OVERWORLD) {
			return List.of();
		}
		return decideIn(level, Math.min(c0x, c1x), Math.min(c0z, c1z), Math.max(c0x, c1x), Math.max(c0z, c1z));
	}

	private static List<Decision> decideIn(ServerLevel level, int c0x, int c0z, int c1x, int c1z) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		List<Decision> out = new ArrayList<>();
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (set.value().structures().stream().noneMatch(e -> e.structure().value() instanceof Sc2Structure sc2 && !sc2.marker())) {
				continue;
			}
			StructurePlacement placement = set.value().placement();
			for (ChunkPos chunk : candidates(placement, state.getLevelSeed(), c0x, c0z, c1x, c1z)) {
				if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					continue;
				}
				ask(level, set.value(), chunk).ifPresent(out::add);
			}
		}
		return out;
	}

	/**
	 * The placement chunks of a kit set's placement in a square of chunks (a random spread's one a region, the rays'
	 * slots, the axes' marks), before the placement's own check ({@code isStructureChunk}: exclusion zones, frequency).
	 */
	public static List<ChunkPos> candidates(StructurePlacement placement, long seed, int c0x, int c0z, int c1x, int c1z) {
		List<ChunkPos> out = new ArrayList<>();
		if (placement instanceof RandomSpreadStructurePlacement spread) {
			int spacing = spread.spacing();
			for (int sx = Math.floorDiv(c0x, spacing); sx <= Math.floorDiv(c1x, spacing); sx++) {
				for (int sz = Math.floorDiv(c0z, spacing); sz <= Math.floorDiv(c1z, spacing); sz++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(seed, sx * spacing, sz * spacing);
					if (chunk.x() >= c0x && chunk.x() <= c1x && chunk.z() >= c0z && chunk.z() <= c1z) {
						out.add(chunk);
					}
				}
			}
		} else if (placement instanceof KitPlacement kit) {
			for (ChunkPos chunk : kit.chunks(seed)) {
				if (chunk.x() >= c0x && chunk.x() <= c1x && chunk.z() >= c0z && chunk.z() <= c1z) {
					out.add(chunk);
				}
			}
		}
		return out;
	}

	/**
	 * What worldgen makes of this set in this placement chunk (its structures tried in vanilla's weighted order, the first
	 * valid wins): the made decision, or empty. Each kit structure's answer goes through its start's own path, so it is
	 * kept. The placement's own check is the caller's. Any thread.
	 */
	public static Optional<Decision> ask(ServerLevel level, StructureSet set, ChunkPos chunk) {
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		if (options.size() == 1) {
			return ask(level, options.getFirst().structure(), chunk);
		}
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(level.getChunkSource().getGeneratorState().getLevelSeed(), chunk.x(), chunk.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry option : options) {
			total += option.weight();
		}
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				choice -= option.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry selected = options.get(index);
			Optional<Decision> made = ask(level, selected.structure(), chunk);
			if (made.isPresent()) {
				return made;
			}
			if (!(selected.structure().value() instanceof Sc2Structure)) {
				// Not ours: whether vanilla's would generate here is not this cache's business; it is the one tried.
				return Optional.empty();
			}
			options.remove(index);
			total -= selected.weight();
		}
		return Optional.empty();
	}

	/**
	 * Asks one kit structure about one chunk through its own start's path (the kit's gates, then {@link #decide}): the
	 * made decision if a start goes there, else empty. Any thread.
	 */
	public static Optional<Decision> ask(ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
		if (!(structure.value() instanceof Sc2Structure sc2) || sc2.marker()) {
			return Optional.empty();
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		Climate.Sampler sampler = level.getChunkSource().randomState().createClimateSampler(SamplerContext.builder().enableCaches().build());
		Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), sampler,
				level.getChunkSource().randomState(), level.getServer().getStructureTemplateManager(), level.getSeed(), chunk, level,
				structure.value().biomes()::contains);
		return sc2.decision(context).filter(Decision::made);
	}
}
