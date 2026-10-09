package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.gen.Blueprint;
import com.forzacode.a1016_02.world.gen.NoiseTerrain;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.LiveTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * "Your house, elsewhere" (SIGNATURE, D-005): the subject's first shelter is captured, a copy site is picked far
 * from the base (HOUSE_COPY), and over several in-game days blocks move out of the real house a few at a time
 * ({@code TraceService.move}, out of view) to rebuild its shell there, each at its mirrored spot when it can. The
 * real roof is never taken and the real house is never opened ({@link HouseShell.Analysis#canTake}). The copy is
 * emptied inside. Ending B's {@link #requestFinish} completes it: whatever the house can still give, then buried
 * blocks moved from the ground near the copy. Every block is a ledgered move (Ending D can put it back); the state
 * keeps the list too. Built at most once per world. Server thread only.
 */
public final class HouseCopier {
	public static final String ID = "signature_house_elsewhere";
	/** Blocks moved out of the real house. */
	public static final String CAUSE = "world:house_copy";
	/** Buried blocks moved from the ground near the copy (Ending B's finish). */
	public static final String CAUSE_LOCAL = "world:house_copy/local";
	/** Plants removed inside the copy (it is emptied inside). */
	public static final String CAUSE_CLEAR = "world:house_copy/clear";
	/** A copy site candidate whose chunks do not load within this many ticks is skipped (a chunk-load limit, not pacing: never divided). */
	private static final long LOAD_TIMEOUT_TICKS = 1200;
	/** Recorded sites this close to a copy site rule it out. */
	private static final int SITE_CLEARANCE = 24;
	/** Moves tried per finish pass (the rest follow next pass). */
	private static final int FINISH_MOVES_PER_PASS = 96;
	/**
	 * {@code HerobrineState} flags that end the copy for good: Ending D is complete ({@code ending:d_complete}) or the
	 * story is over ({@code ending:ended}). Once either is set nothing more leaves the house, and a pending finish is
	 * cancelled.
	 */
	public static final List<String> STOP_FLAGS = List.of("ending:d_complete", "ending:ended");

	/** What one step did. {@code refused} counts moves refused (in view). */
	public record StepResult(HouseCopyState state, int moved, int refused, String note) {
	}

	private record Candidate(int x, int z, List<ChunkPos> chunks) {
	}

	private static final class SiteSearch {
		final List<Candidate> candidates;
		int index;
		long candidateStarted;

		SiteSearch(List<Candidate> candidates, long now) {
			this.candidates = candidates;
			this.candidateStarted = now;
		}
	}

	private static @Nullable SiteSearch siteSearch;
	private static long retryAt;

	private HouseCopier() {
	}

	// --- the copy, step by step ---

	/** The copy spots still waiting for a block (air or a plant, no block entity, no fluid), in build order. */
	public static LinkedHashMap<BlockPos, HouseCopyState.Target> openTargets(ServerLevel level, HouseCopyState state) {
		LinkedHashMap<BlockPos, HouseCopyState.Target> open = new LinkedHashMap<>();
		if (state.copyOrigin().isEmpty()) {
			return open;
		}
		for (HouseCopyState.Target target : state.targets()) {
			BlockPos pos = state.copyPos(target);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState there = level.getBlockState(pos);
			if (there.canBeReplaced() && there.getFluidState().isEmpty() && level.getBlockEntity(pos) == null) {
				open.put(pos, target);
			}
		}
		return open;
	}

	/**
	 * Moves up to {@code maxMoves} blocks out of the real house into the copy, one at a time, looking at the house
	 * again after each: a block goes to its mirrored spot if that spot waits for the same block, otherwise to the
	 * first spot that does. Only blocks {@link HouseShell.Analysis#canTake} allows; every move is out of view or
	 * refused. Plants inside the copy are cleared first.
	 */
	public static StepResult step(ServerLevel level, HouseCopyState state, int maxMoves, TraceService traces) {
		if (state.copyOrigin().isEmpty()) {
			return new StepResult(state, 0, 0, "no copy site yet");
		}
		clearInside(level, state, traces);
		HouseCopyState s = state;
		int moved = 0;
		int refused = 0;
		Set<BlockPos> refusedSources = new HashSet<>();
		String note = "";
		while (moved < maxMoves) {
			LinkedHashMap<BlockPos, HouseCopyState.Target> open = openTargets(level, s);
			if (open.isEmpty()) {
				note = "the copy's shell is complete";
				break;
			}
			List<BlockPos> sources = HouseShell.analyze(level, s).takeable();
			sources.removeAll(refusedSources);
			boolean any = false;
			boolean matched = false;
			for (BlockPos src : sources) {
				BlockState block = level.getBlockState(src);
				BlockPos dst = pickTarget(s, open, src, block);
				if (dst == null) {
					continue;
				}
				matched = true;
				if (traces.move(level, src, dst, CAUSE)) {
					s = s.withMoved(new HouseCopyState.Moved(src, dst, block, false));
					moved++;
					any = true;
					break; // look at the house again: this move changed what can be taken
				}
				refused++;
				refusedSources.add(src);
			}
			if (!any) {
				note = matched ? "the rest is in view" : "nothing more can be taken without the roof or opening the house";
				break;
			}
		}
		return new StepResult(s, moved, refused, note);
	}

	/** The mirrored spot if it waits for this block, else the first spot waiting for the same block (exact state first). */
	static @Nullable BlockPos pickTarget(HouseCopyState s, Map<BlockPos, HouseCopyState.Target> open, BlockPos src, BlockState block) {
		Optional<BlockPos> mirror = s.mirror(src);
		if (mirror.isPresent()) {
			HouseCopyState.Target there = open.get(mirror.get());
			if (there != null && there.state().getBlock() == block.getBlock()) {
				return mirror.get();
			}
		}
		BlockPos sameBlock = null;
		for (Map.Entry<BlockPos, HouseCopyState.Target> entry : open.entrySet()) {
			BlockState wanted = entry.getValue().state();
			if (wanted == block) {
				return entry.getKey();
			}
			if (sameBlock == null && wanted.getBlock() == block.getBlock()) {
				sameBlock = entry.getKey();
			}
		}
		return sameBlock;
	}

	/** Removes plants and snow standing where the copy's inside is (it is emptied inside). */
	static int clearInside(ServerLevel level, HouseCopyState state, TraceService traces) {
		int cleared = 0;
		for (BlockPos rel : state.interior()) {
			BlockPos pos = state.copyOrigin().orElseThrow().offset(rel);
			BlockState there = level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
			if (!there.isAir() && there.canBeReplaced() && there.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
					&& traces.remove(level, pos, CAUSE_CLEAR)) {
				cleared++;
			}
		}
		return cleared;
	}

	/**
	 * Ending B's finish, one pass: everything the real house can still give (never the roof, never opening it),
	 * then buried natural blocks moved from under the ground around the copy for every spot still open: the same
	 * block when there is one, otherwise the most common ground block there. All moves, all out of view.
	 */
	public static StepResult finish(ServerLevel level, HouseCopyState state, TraceService traces, WorldConfig config) {
		StepResult fromHouse = step(level, state, FINISH_MOVES_PER_PASS, traces);
		HouseCopyState s = fromHouse.state();
		int moved = fromHouse.moved();
		int refused = fromHouse.refused();
		LinkedHashMap<BlockPos, HouseCopyState.Target> open = openTargets(level, s);
		if (open.isEmpty()) {
			return new StepResult(s, moved, refused, "the copy is finished");
		}
		Map<Block, List<BlockPos>> buried = buried(level, s.copyBox(), config.houseCopyFinishSearch, config.houseCopyFinishDepth);
		Block fallback = buried.entrySet().stream().filter(e -> groundMaterial(e.getKey().defaultBlockState()))
				.max(Comparator.comparingInt(e -> e.getValue().size())).map(Map.Entry::getKey).orElse(null);
		int tries = 0;
		for (Map.Entry<BlockPos, HouseCopyState.Target> entry : open.entrySet()) {
			if (tries++ >= FINISH_MOVES_PER_PASS) {
				break;
			}
			List<BlockPos> pool = buried.get(entry.getValue().state().getBlock());
			if (pool == null || pool.isEmpty()) {
				pool = fallback == null ? null : buried.get(fallback);
			}
			BlockPos src = pool == null ? null : takeEnclosed(level, pool);
			if (src == null) {
				continue;
			}
			BlockState block = level.getBlockState(src);
			if (traces.move(level, src, entry.getKey(), CAUSE_LOCAL)) {
				s = s.withMoved(new HouseCopyState.Moved(src, entry.getKey(), block, true));
				moved++;
			} else {
				refused++;
			}
		}
		boolean done = openTargets(level, s).isEmpty();
		return new StepResult(s, moved, refused, done ? "the copy is finished" : refused > 0 ? "the rest is in view" : "no more material near the copy");
	}

	/** Natural blocks fully buried (six opaque neighbours) under the ground around the copy, by block. */
	static Map<Block, List<BlockPos>> buried(ServerLevel level, BoundingBox copyBox, int reach, int depth) {
		Map<Block, List<BlockPos>> found = new HashMap<>();
		int top = copyBox.minY() - 2;
		for (BlockPos pos : BlockPos.betweenClosed(copyBox.minX() - reach, Math.max(level.getMinY() + 1, top - depth), copyBox.minZ() - reach,
				copyBox.maxX() + reach, top, copyBox.maxZ() + reach)) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (natural(level, pos, state) && enclosed(level, pos)) {
				found.computeIfAbsent(state.getBlock(), b -> new ArrayList<>()).add(pos.immutable());
			}
		}
		return found;
	}

	/** Pops the first position of the pool that is still fully buried (earlier moves open pockets next to some). */
	private static @Nullable BlockPos takeEnclosed(ServerLevel level, List<BlockPos> pool) {
		while (!pool.isEmpty()) {
			BlockPos pos = pool.removeFirst();
			if (natural(level, pos, level.getBlockState(pos)) && enclosed(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean natural(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.isSolidRender() && !state.hasBlockEntity() && !(state.getBlock() instanceof Fallable) && !Blueprint.isOre(state)
				&& !state.is(Blocks.BEDROCK) && !Services.watch().wasPlacedByPlayer(level, pos)
				&& !Services.protectedAreas().isProtected(level.dimension(), pos);
	}

	private static boolean enclosed(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (!level.isLoaded(n) || !level.getBlockState(n).isSolidRender()) {
				return false;
			}
		}
		return true;
	}

	/** Ground a finished copy may be made of when the house's own block is not buried nearby. */
	static boolean groundMaterial(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SANDSTONE)
				|| state.is(BlockTags.TERRACOTTA);
	}

	// --- the copy site ---

	/**
	 * Where the copy's origin goes for a site centered on ({@code cx}, {@code cz}), checked on loaded blocks: dry
	 * ground no more than 2 blocks uneven, the copy's lowest layer one block above its highest ground, and every
	 * spot of the shell and inside free (air or plants; no trees, no water, no block entity). Null if unfit.
	 */
	public static @Nullable BlockPos copyOriginAt(ServerLevel level, HouseCopyState state, int cx, int cz) {
		BoundingBox rel = relBox(state);
		int x0 = cx - rel.getXSpan() / 2;
		int z0 = cz - rel.getZSpan() / 2;
		LiveTerrain terrain = new LiveTerrain(level);
		int high = Integer.MIN_VALUE;
		int low = Integer.MAX_VALUE;
		for (int x = x0; x < x0 + rel.getXSpan(); x++) {
			for (int z = z0; z < z0 + rel.getZSpan(); z++) {
				if (!terrain.loaded(x, z) || terrain.wet(x, z)) {
					return null;
				}
				int ground = terrain.ground(x, z);
				high = Math.max(high, ground);
				low = Math.min(low, ground);
			}
		}
		if (high - low > 2 || high <= level.getMinY()) {
			return null;
		}
		int lowestTarget = state.targets().stream().mapToInt(t -> t.rel().getY()).min().orElse(0);
		BlockPos origin = new BlockPos(x0, high + 1 - lowestTarget, z0);
		HouseCopyState placed = state.withSite(origin, -1, 0);
		List<BlockPos> spots = new ArrayList<>();
		state.targets().forEach(t -> spots.add(placed.copyPos(t)));
		state.interior().forEach(r -> spots.add(origin.offset(r)));
		for (BlockPos pos : spots) {
			BlockState there = level.getBlockState(pos);
			if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
				return null;
			}
		}
		return origin;
	}

	/** Picks the site: records HOUSE_COPY at the copy's inside (its lowest middle) and starts the steps. */
	public static HouseCopyState placeSite(ServerLevel level, HouseCopyState state, BlockPos origin, SiteSink sites, long nextStep) {
		BoundingBox rel = relBox(state);
		BlockPos middle = rel.getCenter();
		Comparator<BlockPos> lowestMiddle = Comparator.comparingInt(BlockPos::getY);
		lowestMiddle = lowestMiddle.thenComparingDouble(p -> p.distSqr(new BlockPos(middle.getX(), p.getY(), middle.getZ())));
		BlockPos inside = state.interior().stream().min(lowestMiddle).orElse(middle);
		int size = Math.max(2, Math.max(rel.getXSpan(), rel.getZSpan()) / 2);
		SiteRegistry.Site site = sites.record(SiteType.HOUSE_COPY, level.dimension(), origin.offset(inside), size);
		return state.withSite(origin, site == null ? -1 : site.id(), nextStep);
	}

	/** The captured shelter's box relative to its origin. */
	static BoundingBox relBox(HouseCopyState state) {
		BoundingBox box = state.realBox();
		return box.moved(-state.realOrigin().getX(), -state.realOrigin().getY(), -state.realOrigin().getZ());
	}

	// --- live ---

	/**
	 * Starts the copy: once per world; unless {@code forced}, only when the profile has it (D-005) and from day
	 * {@code houseCopyMinDay}. Captures the first shelter now (its chunks must be loaded, otherwise they are asked
	 * for and it fails this time) and looks for the copy site in the background.
	 */
	public static Outcome start(MinecraftServer server, boolean forced) {
		SignatureData data = WorldData.get(server).signatures();
		String no = refusal(data);
		if (no != null) {
			return Outcome.failed(no);
		}
		HerobrineState state = HerobrineState.get(server);
		WorldProfile profile = state.profile();
		long day = GameClock.day(server);
		int minDay = ModConfig.pacing().houseCopyMinDay;
		if (!forced && (!profile.hasHouseCopy() || day < minDay)) {
			return Outcome.failed(!profile.hasHouseCopy() ? "the house copy is not this world's (signature " + profile.signature() + ", no F27)"
					: "the house copy waits for day " + minDay + " (today " + day + ")");
		}
		ServerLevel level = server.overworld();
		List<BlockPos> anchors = anchors(server, level);
		if (anchors.isEmpty()) {
			return Outcome.later("the subject has no first block or base in the overworld yet");
		}
		WorldConfig config = WorldConfig.get();
		List<ChunkPos> chunks = new ArrayList<>();
		for (BlockPos anchor : anchors) {
			chunks.addAll(ChunkLoads.around(new BoundingBox(anchor).inflatedBy(config.houseCopyCaptureRadius + HouseShell.MARGIN), 0));
		}
		if (!ChunkLoads.request(level, chunks)) {
			return Outcome.later("loading the first shelter's chunks; try again in a moment");
		}
		HouseShell.Capture capture = HouseShell.capture(level, anchors, config);
		if (capture == null) {
			return Outcome.later("no first shelter around " + anchors.getFirst().toShortString() + " (need " + config.houseCopyMinShellBlocks
					+ "+ player-placed shell blocks around an inside)");
		}
		HouseCopyState copy = HouseCopyState.captured(level.dimension(), capture, day, GameClock.dayTicks(server));
		data.setHouseCopy(copy);
		siteSearch = null;
		retryAt = 0;
		A1016_02.LOGGER.info("[a1016] world: captured the first shelter at {} ({} shell blocks, {})", capture.origin().toShortString(),
				capture.targets().size(), capture.closed() ? "closed" : "open");
		return Outcome.scheduled(String.format(Locale.ROOT, "captured the first shelter at %s: %d shell blocks (%d roof), %s; looking for the copy site %d to %d blocks from it",
				capture.origin().toShortString(), capture.targets().size(), capture.targets().stream().filter(HouseCopyState.Target::roof).count(),
				capture.closed() ? "closed" : "open", config.houseCopyMinDistance, config.houseCopyMaxDistance));
	}

	/** Why no copy may be started, or null: there is one already (it is built at most once per world). */
	public static @Nullable String refusal(SignatureData data) {
		return data.houseCopy().map(s -> "the house copy already exists (" + s.phase().name().toLowerCase(Locale.ROOT) + ")").orElse(null);
	}

	/** The subject's first block, crafting table, chest and base in this level, base first. */
	static List<BlockPos> anchors(MinecraftServer server, ServerLevel level) {
		List<BlockPos> anchors = new ArrayList<>();
		Services.watch().subject(server).flatMap(p -> Services.watch().base(p)).filter(b -> b.dimension().equals(level.dimension()))
				.ifPresent(b -> anchors.add(b.pos()));
		HerobrineState.FirstBlocks first = HerobrineState.get(server).firstBlocks();
		for (PlacedBlock placed : new PlacedBlock[] {first.block(), first.craftingTable(), first.chest()}) {
			if (placed != null && placed.pos().dimension().equals(level.dimension()) && !anchors.contains(placed.pos().pos())) {
				anchors.add(placed.pos().pos());
			}
		}
		return anchors;
	}

	/**
	 * Ending B: the copy gets finished, as soon as it is out of view. False if there is no copy in this world
	 * (D-005: only when the signature or F27 asked for one, after day 20) or the story is over ({@link #stopped}).
	 */
	public static boolean requestFinish(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		Optional<HouseCopyState> copy = data.houseCopy();
		if (copy.isEmpty() || stopped(server)) {
			return false;
		}
		if (copy.get().phase() != HouseCopyState.Phase.FINISHED && copy.get().phase() != HouseCopyState.Phase.FINISHING) {
			data.setHouseCopy(copy.get().withPhase(HouseCopyState.Phase.FINISHING));
			retryAt = 0;
		}
		return true;
	}

	/**
	 * Ending B left or the story ended: a finish that was asked for is cancelled (the copy goes back to growing a
	 * few blocks a day, or stops for good once a {@link #STOP_FLAGS} flag is set). True if a finish was pending.
	 */
	public static boolean cancelFinish(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		Optional<HouseCopyState> copy = data.houseCopy();
		if (copy.isEmpty() || copy.get().phase() != HouseCopyState.Phase.FINISHING) {
			return false;
		}
		data.setHouseCopy(copy.get().withPhase(copy.get().copyOrigin().isPresent() ? HouseCopyState.Phase.BUILDING : HouseCopyState.Phase.SITE));
		A1016_02.LOGGER.info("[a1016] world: the house copy's finish is cancelled");
		return true;
	}

	/** True once the story is over for the copy ({@link #STOP_FLAGS}): nothing more leaves the house. */
	public static boolean stopped(MinecraftServer server) {
		HerobrineState state = HerobrineState.get(server);
		return STOP_FLAGS.stream().anyMatch(state::hasFlag);
	}

	/**
	 * Called every second: finds the copy site, then runs the steps (or the finish) when due and loaded. Does nothing
	 * once the story is over ({@link #stopped}), and cancels a finish still pending then.
	 */
	public static void tick(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		HouseCopyState s = data.houseCopy().orElse(null);
		if (s == null || s.phase() == HouseCopyState.Phase.FINISHED) {
			siteSearch = null;
			return;
		}
		if (stopped(server)) {
			siteSearch = null;
			cancelFinish(server);
			return;
		}
		ServerLevel level = server.getLevel(s.dimension());
		long now = server.getTickCount();
		if (level == null || now < retryAt) {
			return;
		}
		WorldConfig config = WorldConfig.get();
		if (s.copyOrigin().isEmpty()) {
			searchSite(server, level, data, s, config, now);
			return;
		}
		boolean finishing = s.phase() == HouseCopyState.Phase.FINISHING;
		long dayTicks = GameClock.dayTicks(server);
		if (!finishing && dayTicks < s.nextStep()) {
			return;
		}
		if (!ChunkLoads.request(level, chunksFor(s, finishing, config))) {
			return; // asked for; next second
		}
		RandomSource random = level.getRandom();
		StepResult result = finishing ? finish(level, s, Services.traces(), config)
				: step(level, s, Mth.nextInt(random, Math.max(1, config.houseCopyMovesPerStepMin), Math.max(1, config.houseCopyMovesPerStepMax)),
						Services.traces());
		HouseCopyState next = result.state();
		boolean complete = openTargets(level, next).isEmpty();
		if (finishing) {
			if (complete || result.moved() == 0 && result.refused() == 0) {
				next = next.withPhase(HouseCopyState.Phase.FINISHED);
				A1016_02.LOGGER.info("[a1016] world: the house copy is finished ({})", result.note());
			} else {
				retryAt = now + ModConfig.realTicks(config.houseCopyRetrySeconds);
			}
		} else if (complete) {
			next = next.withPhase(HouseCopyState.Phase.FINISHED);
		} else if (result.moved() > 0 || result.refused() == 0) {
			// Moved some, or nothing can be taken today: the next step is a day (or so) later.
			next = next.withNextStep(dayTicks + Math.round(config.houseCopyStepDays * GameClock.TICKS_PER_DAY));
		} else {
			retryAt = now + ModConfig.realTicks(config.houseCopyRetrySeconds); // in view: soon again
		}
		if (result.moved() > 0) {
			A1016_02.LOGGER.debug("[a1016] world: house copy moved {} block(s) ({})", result.moved(), result.note());
		}
		data.setHouseCopy(next);
	}

	private static List<ChunkPos> chunksFor(HouseCopyState s, boolean finishing, WorldConfig config) {
		List<ChunkPos> chunks = new ArrayList<>(ChunkLoads.around(s.realBox().inflatedBy(HouseShell.MARGIN + 1), 0));
		chunks.addAll(ChunkLoads.around(s.copyBox().inflatedBy(finishing ? config.houseCopyFinishSearch + 1 : 1), 0));
		return chunks;
	}

	private static void searchSite(MinecraftServer server, ServerLevel level, SignatureData data, HouseCopyState s, WorldConfig config, long now) {
		if (siteSearch == null) {
			siteSearch = new SiteSearch(candidates(level, s, config, RandomSource.create(), GameClock.day(server)), now);
		}
		SiteSearch search = siteSearch;
		if (search.index >= search.candidates.size()) {
			siteSearch = null;
			retryAt = now + ModConfig.realTicks(config.houseCopyRetrySeconds * 10);
			A1016_02.LOGGER.debug("[a1016] world: no copy site among {} candidate(s); trying again later", search.candidates.size());
			return;
		}
		Candidate c = search.candidates.get(search.index);
		if (!ChunkLoads.request(level, c.chunks())) {
			if (now - search.candidateStarted > LOAD_TIMEOUT_TICKS) {
				search.index++;
				search.candidateStarted = now;
			}
			return;
		}
		BlockPos origin = copyOriginAt(level, s, c.x(), c.z());
		GlobalPos center = GlobalPos.of(level.dimension(), new BlockPos(c.x(), origin == null ? 0 : origin.getY(), c.z()));
		boolean crowded = false;
		for (SiteType type : SiteType.values()) {
			crowded |= origin != null && !Services.sites().find(type, center, SITE_CLEARANCE).isEmpty();
		}
		if (origin == null || crowded) {
			search.index++;
			search.candidateStarted = now;
			return;
		}
		HouseCopyState placed = placeSite(level, s, origin, SiteSink.LIVE, GameClock.dayTicks(server));
		data.setHouseCopy(placed);
		siteSearch = null;
		A1016_02.LOGGER.info("[a1016] world: the house copy goes at {}", origin.toShortString());
	}

	/** Copy sites from the noise, {@code houseCopyMinDistance..MaxDistance} from the real house: dry, flat, not woods. */
	private static List<Candidate> candidates(ServerLevel level, HouseCopyState s, WorldConfig config, RandomSource random, long today) {
		ScarContext context = ScarContext.current();
		ScarPlanner planner = context == null ? null : context.planner(level);
		Terrain noise = planner != null ? planner.terrain() : new NoiseTerrain(level);
		BoundingBox rel = relBox(s);
		BlockPos home = s.realBox().getCenter();
		List<Candidate> found = new ArrayList<>();
		for (int attempt = 0; attempt < 200 && found.size() < Math.max(1, config.houseCopyCandidates); attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int d = Mth.nextInt(random, config.houseCopyMinDistance, Math.max(config.houseCopyMinDistance, config.houseCopyMaxDistance));
			int cx = home.getX() + Mth.floor(Math.cos(angle) * d);
			int cz = home.getZ() + Mth.floor(Math.sin(angle) * d);
			Holder<Biome> biome = noise.surfaceBiome(cx, cz);
			if (Terrain.isWetland(biome) || Terrain.isDenseWoods(biome) || noise.ground(cx, cz) <= noise.seaLevel()) {
				continue;
			}
			int g = noise.ground(cx, cz);
			int hx = rel.getXSpan() / 2 + 1;
			int hz = rel.getZSpan() / 2 + 1;
			boolean flat = true;
			for (int[] o : new int[][] {{-hx, -hz}, {hx, -hz}, {-hx, hz}, {hx, hz}}) {
				flat &= !noise.wet(cx + o[0], cz + o[1]) && Math.abs(noise.ground(cx + o[0], cz + o[1]) - g) <= 2;
			}
			BoundingBox box = new BoundingBox(cx - hx, level.getMinY(), cz - hz, cx + hx, level.getMaxY(), cz + hz);
			if (!flat || Services.protectedAreas().intersects(level.dimension(), box)) {
				continue;
			}
			List<ChunkPos> chunks = ChunkLoads.around(box, 0);
			// Never where the player is these days.
			if (chunks.stream().anyMatch(chunk -> Services.watch().lastVisitDay(level, chunk) >= today - 1)) {
				continue;
			}
			found.add(new Candidate(cx, cz, chunks));
		}
		return found;
	}

	/** {@code /a1016 world housecopy step <n>}: up to {@code n} moves now (still only out of view). */
	public static String debugStep(MinecraftServer server, int n) {
		SignatureData data = WorldData.get(server).signatures();
		HouseCopyState s = data.houseCopy().orElse(null);
		if (s == null) {
			return "no house copy yet (/a1016 world signature house_elsewhere now)";
		}
		if (s.copyOrigin().isEmpty()) {
			return "the copy site is not picked yet; wait a moment";
		}
		ServerLevel level = server.getLevel(s.dimension());
		if (level == null || !ChunkLoads.request(level, chunksFor(s, false, WorldConfig.get()))) {
			return "loading the house and copy chunks; run it again in a few seconds";
		}
		StepResult result = step(level, s, n, Services.traces());
		HouseCopyState next = result.state();
		if (openTargets(level, next).isEmpty() && next.phase() == HouseCopyState.Phase.BUILDING) {
			next = next.withPhase(HouseCopyState.Phase.FINISHED);
		}
		data.setHouseCopy(next);
		StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "moved %d block(s), %d refused in view: %s", result.moved(), result.refused(),
				result.note().isEmpty() ? "ok" : result.note()));
		List<HouseCopyState.Moved> moved = next.moved();
		for (HouseCopyState.Moved m : moved.subList(Math.max(0, moved.size() - result.moved()), moved.size())) {
			line.append("\n  ").append(m.from().toShortString()).append(" -> ").append(m.to().toShortString());
		}
		return line.toString();
	}

	/** {@code /a1016 world housecopy status}. */
	public static List<String> status(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		Optional<HouseCopyState> copy = WorldData.get(server).signatures().houseCopy();
		if (copy.isEmpty()) {
			lines.add("house copy: not started");
			return lines;
		}
		HouseCopyState s = copy.get();
		lines.add(String.format(Locale.ROOT, "house copy: %s, started day %d, real house at %s", s.phase().name().toLowerCase(Locale.ROOT), s.startedDay(),
				s.realBox().getCenter().toShortString()));
		long roof = s.targets().stream().filter(HouseCopyState.Target::roof).count();
		lines.add(String.format(Locale.ROOT, "shell: %d blocks (%d roof), inside %d cells; moved %d from the house, %d from the ground", s.targets().size(),
				roof, s.interior().size(), s.movedFromHouse(), s.moved().size() - s.movedFromHouse()));
		if (s.copyOrigin().isEmpty()) {
			SiteSearch search = siteSearch;
			lines.add(search == null ? "copy site: searching" : String.format(Locale.ROOT, "copy site: candidate %d of %d", search.index + 1,
					search.candidates.size()));
			return lines;
		}
		ServerLevel level = server.getLevel(s.dimension());
		BlockPos center = s.copyBox().getCenter();
		lines.add(String.format(Locale.ROOT, "copy at %s (HOUSE_COPY #%d), %d blocks from the real house", center.toShortString(), s.siteId(),
				Math.round(Math.sqrt(center.distSqr(new BlockPos(s.realBox().getCenter().getX(), center.getY(), s.realBox().getCenter().getZ()))))));
		double days = (s.nextStep() - GameClock.dayTicks(server)) / (double) GameClock.TICKS_PER_DAY;
		lines.add(String.format(Locale.ROOT, "next step: %s", s.phase() == HouseCopyState.Phase.BUILDING ? days <= 0 ? "due" : String.format(Locale.ROOT,
				"in %.2f in-game days", days) : "-"));
		if (level != null && ChunkLoads.ready(level, chunksFor(s, false, WorldConfig.get()))) {
			HouseShell.Analysis now = HouseShell.analyze(level, s);
			lines.add(String.format(Locale.ROOT, "open spots at the copy: %d; takeable now: %d; real house closed: %s", openTargets(level, s).size(),
					now.takeable().size(), now.closed() ? "yes" : "no"));
		} else {
			lines.add("(house or copy not loaded: no live counts)");
		}
		return lines;
	}

	/** Called when a server stops. */
	public static void clear() {
		siteSearch = null;
		retryAt = 0;
	}

	/** For Ending D and debug: every block moved so far, oldest first. */
	public static List<HouseCopyState.Moved> moved(MinecraftServer server) {
		return WorldData.get(server).signatures().houseCopy().map(HouseCopyState::moved).orElse(List.of());
	}

	/** The copy's site, once picked. */
	public static Optional<GlobalPos> site(MinecraftServer server) {
		return WorldData.get(server).signatures().houseCopy().filter(s -> s.copyOrigin().isPresent())
				.map(s -> GlobalPos.of(s.dimension(), s.copyBox().getCenter()));
	}
}
