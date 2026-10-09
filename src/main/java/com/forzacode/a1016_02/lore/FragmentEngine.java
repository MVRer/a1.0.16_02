package com.forzacode.a1016_02.lore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.lore.Placing.Facts;
import com.forzacode.a1016_02.lore.Placing.Request;
import com.forzacode.a1016_02.lore.Placing.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The placement engine. On every stage change and every {@link LoreConfig#placementCheckSeconds}, it places each
 * enabled fragment whose stage and requirements are met and that is not placed yet, a few per check, through
 * {@link Placers}. A fragment that could not be placed waits {@link LoreConfig#retrySeconds} before the next try;
 * one that is waiting for chunks to load ({@link ChunkGate}) is tried again after {@link LoreConfig#loadWaitSeconds}
 * with the same random candidates, so it probes the chunks it asked for.
 */
public final class FragmentEngine {
	private long nextCheck;
	private boolean stageChanged;
	private final Map<String, Long> retryAt = new HashMap<>();
	/** The candidate seed of each fragment, kept while it waits for its chunks. */
	private final Map<String, Long> seeds = new HashMap<>();

	void onStageChanged() {
		stageChanged = true;
	}

	void reset() {
		nextCheck = 0;
		stageChanged = false;
		retryAt.clear();
		seeds.clear();
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (!stageChanged && now < nextCheck) {
			return;
		}
		stageChanged = false;
		LoreConfig config = LoreConfig.get();
		nextCheck = now + ModConfig.realTicks(config.placementCheckSeconds);
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		HerobrineState state = HerobrineState.get(server);
		Facts facts = LiveFacts.of(server, subject.get());
		String name = subject.get().getName().getString();
		int attempts = 0;
		for (Fragment fragment : FragmentData.all()) {
			String id = fragment.id();
			if (!facts.enabled(id) || state.fragmentsPlaced().containsKey(id) || !eligible(fragment, state.stage(), facts)
					|| retryAt.getOrDefault(id, Long.MIN_VALUE) > now) {
				continue;
			}
			if (attempts++ >= config.attemptsPerCheck) {
				break;
			}
			long since = LoreData.get(server).eligibleSince(id, GameClock.playTicks(server));
			boolean ownBuilds = GameClock.playTicks(server) - since >= ModConfig.realTicks(config.ownBuildDelayMinutes * 60);
			long seed = seeds.computeIfAbsent(id, k -> level.getRandom().nextLong());
			Request request = request(level, fragment, facts, name, Services.traces(), config.candidatesPerAttempt, ownBuilds,
					RandomSource.create(seed));
			Optional<Result> result = attempt(request);
			if (result.isPresent()) {
				record(server, level, fragment, result.get(), false);
				retryAt.remove(id);
				seeds.remove(id);
			} else if (request.loads().waiting()) {
				long soon = now + Math.max(1, ModConfig.realTicks(config.loadWaitSeconds));
				retryAt.put(id, soon);
				nextCheck = Math.min(nextCheck, soon);
			} else {
				retryAt.put(id, now + ModConfig.realTicks(config.retrySeconds));
				seeds.remove(id);
			}
		}
	}

	/** True if the fragment may be placed now: a placed rule, its stage reached, its requirements met. */
	public static boolean eligible(Fragment fragment, Stage stage, Facts facts) {
		if (!Placers.isPlacedRule(fragment.placement().rule()) || !stage.atLeast(fragment.stage())) {
			return false;
		}
		Fragment.Requires requires = fragment.requires();
		Set<String> read = facts.read();
		return requires.placed().stream().allMatch(id -> facts.placed(id).isPresent())
				&& read.containsAll(requires.read())
				&& read.size() >= requires.readCount();
	}

	/** The request the engine makes: distances from spawn or the base, as the data says. */
	static Request request(ServerLevel level, Fragment fragment, Facts facts, String playerName, TraceService traces, int tries,
			boolean ownBuilds, RandomSource random) {
		Fragment.Placement placement = fragment.placement();
		BlockPos origin = placement.fromSpawn() ? facts.spawn() : facts.base();
		return new Request(level, fragment, origin, placement.minDistance(), placement.maxDistance(), tries, traces, playerName, facts,
				random, ownBuilds, new Placing.Loads());
	}

	static Optional<Result> attempt(Request request) {
		try {
			return Placers.place(request);
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] lore: placing {} failed", request.id(), e);
			return Optional.empty();
		}
	}

	/**
	 * Writes a placement into the shared state and lore's own data. A fragment already placed keeps its recorded
	 * place unless {@code replace} (only {@code /a1016 lore place} replaces).
	 */
	static boolean record(MinecraftServer server, ServerLevel level, Fragment fragment, Result result, boolean replace) {
		String id = fragment.id();
		HerobrineState state = HerobrineState.get(server);
		if (!replace && state.fragmentsPlaced().containsKey(id)) {
			A1016_02.LOGGER.warn("[a1016] lore: {} is already placed at {}; not recording {}", id, state.fragmentsPlaced().get(id), result.pos);
			return false;
		}
		state.setFragmentPlaced(id, GlobalPos.of(level.dimension(), result.pos));
		LoreData data = LoreData.get(server);
		data.clearReadTargets(id);
		result.readTargets.forEach(target -> data.addReadTarget(id, GlobalPos.of(level.dimension(), target)));
		result.anchors.forEach((key, pos) -> data.setAnchor(key, GlobalPos.of(level.dimension(), pos)));
		result.site.ifPresent(site -> data.setSiteClaim(id, site.id()));
		A1016_02.LOGGER.info("[a1016] lore: placed {} ({}) at {}", id, fragment.name(), result.pos.toShortString());
		return true;
	}

	/**
	 * Places a fragment now near {@code hint} (debug and {@link com.forzacode.a1016_02.core.FragmentService#place}),
	 * out of view, and records it. Rules tied to a place (the test room, the first crafting table, spawn) still use
	 * that place. With {@code fallback} (debug), a fragment whose rule finds nothing near is left plainly near the
	 * hint, and an earlier placement is replaced. Empty while far chunks it needs are still loading.
	 */
	Optional<Result> placeNear(ServerLevel level, Fragment fragment, BlockPos hint, int min, int max, int tries, boolean fallback,
			Optional<ServerPlayer> viewer) {
		MinecraftServer server = level.getServer();
		Facts facts = LiveFacts.of(server, viewer.or(() -> Services.watch().subject(server)).orElse(null));
		String name = HerobrineState.get(server).subject().map(HerobrineState.Subject::name)
				.orElse(viewer.map(p -> p.getName().getString()).orElse("Steve"));
		Request request = new Request(level, fragment, hint, min, max, tries, Services.traces(), name, facts, level.getRandom(), true,
				new Placing.Loads());
		Optional<Result> result = attempt(request);
		if (result.isEmpty() && fallback && !request.loads().waiting()) {
			result = Placers.plain(request, viewer);
		}
		return result.filter(r -> record(server, level, fragment, r, fallback));
	}

	/** Facts read from the save. */
	record LiveFacts(MinecraftServer server, BlockPos base, BlockPos spawn) implements Facts {
		static LiveFacts of(MinecraftServer server, ServerPlayer subject) {
			ServerLevel overworld = server.overworld();
			BlockPos spawn = overworld.getRespawnData().pos();
			BlockPos base = subject == null ? spawn : Services.watch().base(subject)
					.filter(p -> p.dimension().equals(overworld.dimension())).map(GlobalPos::pos).orElse(spawn);
			return new LiveFacts(server, base, spawn);
		}

		@Override
		public Optional<GlobalPos> placed(String id) {
			return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get(id));
		}

		@Override
		public Optional<GlobalPos> anchor(String key) {
			return LoreData.get(server).anchor(key);
		}

		@Override
		public void remember(String key, GlobalPos pos) {
			LoreData.get(server).setAnchor(key, pos);
		}

		@Override
		public Optional<GlobalPos> grove() {
			return UntouchedGrove.ensure(server.overworld(), spawn, server.overworld().getRandom());
		}

		@Override
		public HerobrineState.FirstBlocks firstBlocks() {
			return HerobrineState.get(server).firstBlocks();
		}

		@Override
		public boolean enabled(String id) {
			return Services.fragments().isEnabled(server, id);
		}

		@Override
		public Set<String> read() {
			return HerobrineState.get(server).fragmentsRead();
		}

		@Override
		public boolean stillBurning() {
			return HerobrineState.get(server).hasFlag(Placers.STILL_BURNING);
		}
	}
}
