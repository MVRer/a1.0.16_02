package com.forzacode.a1016_02.lore.sc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.mojang.authlib.GameProfile;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 14 on the server (D-081): the game's own records lie, once each.
 * <ul>
 * <li>(a) {@code sc_14} arms the world for the host's world list (an integrated server, the host only, once per world).</li>
 * <li>(b) at a stop, from Telling, at least {@code versionAfterDays} after (a) was shown and with the host quitting at night,
 * the version beat is armed (once); a finished hardcore line of the host's reads the canon version for good.</li>
 * <li>(c) the first {@code /seed} a player runs from Proximity answers the canon seed (once per world;
 * {@code lore.mixin.SeedCommandMixin}).</li>
 * <li>(d) his removals of placed blocks and the stacks he takes count in a player's statistics ({@code TRACED}).</li>
 * </ul>
 * (a) and (b) reach the world list through {@link RecordRules#FILE}, written whole on an arm and at every stop
 * ({@link SelfCreated#writeInWorld}); the list says what it showed in {@link RecordRules#SEEN_FILE}, read here at the start
 * (a shown beat is done, else it stays armed). The switch off writes the file empty and takes (d)'s statistics back.
 * Server thread only. {@link #LIVE} is the game's; tests make their own with their own {@link Hooks}.
 */
public final class TheRecord {
	public static final String CARD_ID = "sc_14";

	/** What 14 reads, so tests can stand in for the live world. */
	public interface Hooks {
		boolean integrated(MinecraftServer server);

		/** The integrated server's host, or null. */
		@Nullable UUID host(MinecraftServer server);

		RecordData data(MinecraftServer server);

		Stage stage(MinecraftServer server);

		long day(MinecraftServer server);

		long timeOfDay(MinecraftServer server);

		long nowMillis();

		Path worldFolder(MinecraftServer server);

		Predicate<String> flags(MinecraftServer server);

		boolean hardcore(MinecraftServer server);

		/** The switch and the story flags ({@link ScGates#storyRefusal}). */
		Optional<String> storyRefusal(MinecraftServer server);

		/** The common gates for a player ({@link ScGates#refusal}). */
		Optional<String> commonRefusal(ServerPlayer player);

		/** The participants online now. */
		List<ServerPlayer> participants(MinecraftServer server);

		/** A player's base ({@code PlayerWatch.base}). */
		Optional<GlobalPos> base(ServerPlayer player);

		Optional<ServerPlayer> online(MinecraftServer server, UUID player);

		/** Everyone online now, spectators included. */
		List<ServerPlayer> players(MinecraftServer server);
	}

	static final Hooks LIVE_HOOKS = new Hooks() {
		@Override
		public boolean integrated(MinecraftServer server) {
			return !server.isDedicatedServer() && server.isSingleplayer();
		}

		@Override
		public @Nullable UUID host(MinecraftServer server) {
			GameProfile profile = server.getSingleplayerProfile();
			return profile == null ? null : profile.id();
		}

		@Override
		public RecordData data(MinecraftServer server) {
			return RecordData.get(server);
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return HerobrineState.get(server).stage();
		}

		@Override
		public long day(MinecraftServer server) {
			return GameClock.day(server);
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
		}

		@Override
		public long nowMillis() {
			return System.currentTimeMillis();
		}

		@Override
		public Path worldFolder(MinecraftServer server) {
			return SelfCreated.worldFolder(server);
		}

		@Override
		public Predicate<String> flags(MinecraftServer server) {
			return HerobrineState.get(server)::hasFlag;
		}

		@Override
		public boolean hardcore(MinecraftServer server) {
			return server.isHardcore();
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return ScGates.storyRefusal(server);
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer player) {
			return ScGates.refusal(player);
		}

		@Override
		public List<ServerPlayer> participants(MinecraftServer server) {
			return Services.watch().participants(server);
		}

		@Override
		public Optional<GlobalPos> base(ServerPlayer player) {
			return Services.watch().base(player);
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(server.getPlayerList().getPlayer(player));
		}

		@Override
		public List<ServerPlayer> players(MinecraftServer server) {
			return List.copyOf(server.getPlayerList().getPlayers());
		}
	};

	/** The game's 14. */
	public static final TheRecord LIVE = new TheRecord(LIVE_HOOKS);

	static final String LINE_FINISHED_FLAG = "ending:line_finished=";
	static final String ENDED_FLAG = "ending:ended";

	private final Hooks hooks;
	private final RandomSource random = RandomSource.create();
	/** Debug: the next {@code /seed} answers the canon seed, whatever the stage. */
	private boolean seedForced;

	public TheRecord(Hooks hooks) {
		this.hooks = hooks;
	}

	// --- (a): the card ---

	/** Why {@code sc_14} does not fit this player now, or empty: the common gates, an integrated server's host, (a) never armed. */
	public Optional<String> fit(ServerPlayer player) {
		Optional<String> common = hooks.commonRefusal(player);
		if (common.isPresent()) {
			return common;
		}
		MinecraftServer server = player.level().getServer();
		if (!hooks.integrated(server)) {
			return Optional.of("not an integrated server");
		}
		if (!player.getUUID().equals(hooks.host(server))) {
			return Optional.of("not the host");
		}
		RecordData data = hooks.data(server);
		if (data.lastArmed() || data.lastDone()) {
			return Optional.of("already " + (data.lastArmed() ? "armed" : "done") + " in this world");
		}
		return Optional.empty();
	}

	/** Arms (a) for the world list now and writes the file: {@link FireResult#FIRED}, or SKIPPED while the story holds it. */
	public FireResult armLast(MinecraftServer server, boolean nogap) {
		if (hooks.storyRefusal(server).isPresent()) {
			return FireResult.SKIPPED;
		}
		hooks.data(server).armLast(nonce(), nogap);
		writeFile(server, hooks.nowMillis(), hooks.timeOfDay(server));
		A1016_02.LOGGER.info("[a1016] {}: armed (a){}", CARD_ID, nogap ? " (debug)" : "");
		return FireResult.FIRED;
	}

	/** Debug: arms (b) now (the stage, the days and the night skipped) and writes the file. */
	public FireResult armVersion(MinecraftServer server, boolean nogap) {
		if (hooks.storyRefusal(server).isPresent()) {
			return FireResult.SKIPPED;
		}
		hooks.data(server).armVersion(nonce(), nogap);
		writeFile(server, hooks.nowMillis(), hooks.timeOfDay(server));
		A1016_02.LOGGER.info("[a1016] {}: armed (b) (debug)", CARD_ID);
		return FireResult.FIRED;
	}

	private long nonce() {
		long nonce;
		do {
			nonce = random.nextLong();
		} while (nonce == 0L);
		return nonce;
	}

	// --- the files ---

	/** The server's file as it stands, with this stop: the armed beats, the version for good. */
	RecordRules.ServerFile file(MinecraftServer server, long stopMillis, long stopTimeOfDay) {
		if (!SelfCreated.enabled()) {
			return RecordRules.ServerFile.EMPTY.withStop(stopMillis, stopTimeOfDay); // switched off: the list shows the truth
		}
		RecordData data = hooks.data(server);
		return new RecordRules.ServerFile(data.lastBeat(), data.versionBeat(), forever(server), stopMillis, stopTimeOfDay);
	}

	/**
	 * The version reads the canon one for good: the layer on, a hardcore world whose story ended ({@code ending:ended}) with the
	 * host's own line finished ({@code ending:line_finished=<host>}).
	 */
	boolean forever(MinecraftServer server) {
		UUID host = hooks.host(server);
		Predicate<String> flags = hooks.flags(server);
		return SelfCreated.enabled() && host != null && hooks.hardcore(server) && flags.test(ENDED_FLAG) && flags.test(LINE_FINISHED_FLAG + host);
	}

	/** Writes {@link RecordRules#FILE} whole (an integrated server only). False if it could not be written. */
	boolean writeFile(MinecraftServer server, long stopMillis, long stopTimeOfDay) {
		if (!hooks.integrated(server)) {
			return false;
		}
		return SelfCreated.writeInWorld(hooks.worldFolder(server), RecordRules.FILE, RecordRules.bytes(file(server, stopMillis, stopTimeOfDay).save()));
	}

	/** What the world list showed ({@link RecordRules#SEEN_FILE}); none if it showed nothing or cannot be read. */
	Set<Long> seen(MinecraftServer server) {
		Optional<Path> path = SelfCreated.inWorld(hooks.worldFolder(server), RecordRules.SEEN_FILE);
		if (path.isEmpty() || !Files.isRegularFile(path.get())) {
			return Set.of();
		}
		try {
			return RecordRules.tag(Files.readAllBytes(path.get())).map(RecordRules::loadSeen).orElse(Set.of());
		} catch (IOException e) {
			return Set.of();
		}
	}

	/** Server start: a beat the world list showed is done ((a) remembers the in-game day the server learned it); else it stays armed. */
	public void onStarted(MinecraftServer server) {
		if (!hooks.integrated(server)) {
			return;
		}
		RecordData data = hooks.data(server);
		if (!data.lastArmed() && !data.versionArmed()) {
			return;
		}
		Set<Long> seen = seen(server);
		boolean changed = false;
		if (data.lastArmed() && seen.contains(data.lastNonce)) {
			data.lastShown(hooks.day(server));
			A1016_02.LOGGER.info("[a1016] {}: (a) was shown", CARD_ID);
			changed = true;
		}
		if (data.versionArmed() && seen.contains(data.verNonce)) {
			data.versionShown();
			A1016_02.LOGGER.info("[a1016] {}: (b) was shown", CARD_ID);
			changed = true;
		}
		if (changed) {
			writeFile(server, hooks.nowMillis(), hooks.timeOfDay(server));
		}
	}

	/** Server stop (the levels still there): (b)'s arm if it holds now, then the file with this stop. */
	public void onStopping(MinecraftServer server) {
		if (!hooks.integrated(server)) {
			return;
		}
		RecordData data = hooks.data(server);
		RecordConfig config = RecordConfig.get();
		long timeOfDay = hooks.timeOfDay(server);
		if (RecordRules.versionArms(hooks.storyRefusal(server).isEmpty(), data.versionDone(), data.versionArmed(), data.lastShownDay(), hooks.day(server),
				config.versionAfterDays, hooks.stage(server).level(), config.versionFromStage, timeOfDay)) {
			data.armVersion(nonce(), false);
			A1016_02.LOGGER.info("[a1016] {}: armed (b)", CARD_ID);
		}
		writeFile(server, hooks.nowMillis(), timeOfDay);
	}

	// --- (c): /seed ---

	/** Debug ({@code /a1016 sc 14 seed}): the next {@code /seed} answers the canon seed, whatever the stage. */
	public void forceSeed() {
		seedForced = true;
	}

	/**
	 * {@code SeedCommandMixin}: the text {@code /seed} shows for this source ({@code real}, the real seed). The first a player
	 * runs from {@code seedFromStage} (or after the debug's arm) while the common gates hold answers the canon seed, once per
	 * world; every other answer is vanilla's.
	 */
	public String seedText(CommandSourceStack source, String real) {
		ServerPlayer player = source.getPlayer();
		if (player == null || !SelfCreated.enabled()) {
			return real;
		}
		MinecraftServer server = source.getServer();
		RecordData data = hooks.data(server);
		if (data.seedIsDone()) {
			return real;
		}
		if (!seedForced && (hooks.commonRefusal(player).isPresent() || hooks.stage(server).level() < RecordConfig.get().seedFromStage)) {
			return real;
		}
		data.seedDone();
		seedForced = false;
		A1016_02.LOGGER.info("[a1016] {}: (c)", CARD_ID);
		return RecordRules.CANON_SEED;
	}

	// --- (d): the statistics ---

	/**
	 * {@code TRACED}: a removal of a block a player placed counts in "Times Mined" for that block, a stack taken from a
	 * container in "Times Dropped" for that item (its count), in the statistics of the participant {@link RecordRules#credit}
	 * picks; only his own traces in the overworld while the story goes on ({@link RecordRules#causeCounts}), with
	 * {@code stats} on. Cheap: a few checks, then one short walk over the participants.
	 */
	public void onTraced(ServerLevel level, TraceLedger.Entry entry, boolean placedByPlayer) {
		if (!SelfCreated.enabled() || level.dimension() != Level.OVERWORLD || !RecordRules.causeCounts(entry.cause())) {
			return;
		}
		RecordConfig config = RecordConfig.get();
		if (!config.stats) {
			return;
		}
		String stat;
		String id;
		int count;
		Stat<?> award;
		if (entry.kind() == TraceLedger.Kind.REMOVE && placedByPlayer && entry.state().isPresent()) {
			Block block = entry.state().map(BlockState::getBlock).get();
			stat = RecordData.MINED;
			id = BuiltInRegistries.BLOCK.getKey(block).toString();
			count = 1;
			award = Stats.BLOCK_MINED.get(block);
		} else if (entry.kind() == TraceLedger.Kind.REMOVE_STACK && entry.stack().isPresent() && !entry.stack().get().isEmpty()) {
			ItemStack stack = entry.stack().get();
			stat = RecordData.DROPPED;
			id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			count = stack.getCount();
			award = Stats.ITEM_DROPPED.get(stack.getItem());
		} else {
			return;
		}
		MinecraftServer server = level.getServer();
		if (hooks.storyRefusal(server).isPresent()) {
			return;
		}
		Optional<ServerPlayer> credited = credited(server, level, Vec3.atCenterOf(entry.pos().pos()), config.statsRadius);
		if (credited.isEmpty()) {
			return;
		}
		credited.get().awardStat(award, count);
		hooks.data(server).award(credited.get().getUUID(), stat, id, count);
	}

	/** (d)'s player for an edit at {@code pos} ({@link RecordRules#credit} over the participants in that level). */
	Optional<ServerPlayer> credited(MinecraftServer server, ServerLevel level, Vec3 pos, double radius) {
		List<RecordRules.Candidate> candidates = new ArrayList<>();
		List<ServerPlayer> participants = hooks.participants(server);
		for (ServerPlayer player : participants) {
			if (player.level() != level) {
				continue;
			}
			Vec3 base = hooks.base(player).filter(b -> b.dimension() == level.dimension()).map(b -> Vec3.atCenterOf(b.pos())).orElse(null);
			candidates.add(new RecordRules.Candidate(player.getUUID(), player.position(), base));
		}
		return RecordRules.credit(candidates, pos, radius).flatMap(uuid -> participants.stream().filter(p -> p.getUUID().equals(uuid)).findFirst());
	}

	/** Takes back what (d) gave this player (never under 0), and forgets it. Returns how many stat lines it took back. */
	public int takeBack(MinecraftServer server, ServerPlayer player) {
		int lines = 0;
		for (RecordData.Award award : hooks.data(server).takeAwards(player.getUUID())) {
			Stat<?> stat = stat(award);
			if (stat != null) {
				int now = player.getStats().getValue(stat);
				player.getStats().setValue(player, stat, Math.max(0, now - award.count()));
				lines++;
			}
		}
		return lines;
	}

	private static @Nullable Stat<?> stat(RecordData.Award award) {
		Identifier id = Identifier.tryParse(award.id());
		if (id == null) {
			return null;
		}
		if (RecordData.MINED.equals(award.stat())) {
			Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
			return block.map(Stats.BLOCK_MINED::get).orElse(null);
		}
		if (RecordData.DROPPED.equals(award.stat())) {
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
			return item.map(Stats.ITEM_DROPPED::get).orElse(null);
		}
		return null;
	}

	// --- the switch, debug ---

	/**
	 * The switch off: the file written with no beat and no version for good (the world list shows the truth), and (d)'s
	 * statistics taken back from everyone online (the others when they next join, {@link #onJoin}). Idempotent, cheap.
	 */
	public void revert(MinecraftServer server) {
		if (hooks.integrated(server)) {
			SelfCreated.writeInWorld(hooks.worldFolder(server), RecordRules.FILE,
					RecordRules.bytes(RecordRules.ServerFile.EMPTY.withStop(hooks.nowMillis(), hooks.timeOfDay(server)).save()));
		}
		for (ServerPlayer player : hooks.players(server)) {
			takeBack(server, player);
		}
	}

	/** A player joins: while the layer is off, what (d) gave them is taken back. */
	public void onJoin(MinecraftServer server, ServerPlayer player) {
		if (!SelfCreated.enabled()) {
			takeBack(server, player);
		}
	}

	/** Debug ({@code /a1016 sc 14 reset}): every beat and the seed forgotten (new arms take new nonces), the file written. */
	public void reset(MinecraftServer server) {
		hooks.data(server).forgetBeats();
		seedForced = false;
		writeFile(server, hooks.nowMillis(), hooks.timeOfDay(server));
	}

	public boolean seedForced() {
		return seedForced;
	}

	/** Forgets the debug's seed arm (server stop). */
	public void clear() {
		seedForced = false;
	}

	/** One line for debug. */
	public String line(MinecraftServer server) {
		RecordData data = hooks.data(server);
		int given = data.awards().stream().mapToInt(RecordData.Award::count).sum();
		return "14: (a) " + state(data.lastArmed(), data.lastDone()) + (data.lastShownDay() >= 0 ? " day " + data.lastShownDay() : "") + ", (b) "
				+ state(data.versionArmed(), data.versionDone()) + (forever(server) ? " for good" : "") + ", (c) " + (data.seedIsDone() ? "done"
						: seedForced ? "armed" : "waiting") + ", (d) " + given;
	}

	private static String state(boolean armed, boolean done) {
		return armed ? "armed" : done ? "done" : "waiting";
	}
}
