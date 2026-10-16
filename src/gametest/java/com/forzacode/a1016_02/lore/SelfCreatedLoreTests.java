package com.forzacode.a1016_02.lore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.lore.sc.RecordConfig;
import com.forzacode.a1016_02.lore.sc.RecordData;
import com.forzacode.a1016_02.lore.sc.RecordRules;
import com.forzacode.a1016_02.lore.sc.TheRecord;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The self-created layer's lore part (docs/SELF_CREATED.md, 14): the fake last played time, the two files and when the world
 * list shows each beat, the version's arm at a stop, the version for good, the canon seed once (through the real
 * {@code /seed}), and the statistics credited for his removals and taken back. Each test makes its own {@link TheRecord} on its
 * own hooks and folder, so nothing here reads or changes the shared test server's story; the {@code /seed} test uses the live
 * one and resets it. The world list itself is the dev client's check list.
 */
public class SelfCreatedLoreTests {
	private static final ZoneId UTC = ZoneOffset.UTC;
	private static final LocalTime ONE = LocalTime.of(1, 0);
	private static final LocalTime HALF_FOUR = LocalTime.of(4, 30);

	private static long at(int month, int day, int hour, int minute) {
		return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, UTC).toInstant().toEpochMilli();
	}

	private static LocalTime timeOf(long millis) {
		return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), UTC).toLocalTime();
	}

	/** Hooks for a test's own record: the server kind, the host, the clock, the story, the players, a folder of its own. */
	private static final class Hooks implements TheRecord.Hooks {
		final RecordData data = new RecordData();
		final Map<UUID, ServerPlayer> online = new HashMap<>();
		final List<ServerPlayer> participants = new ArrayList<>();
		final Map<UUID, GlobalPos> bases = new HashMap<>();
		final Set<String> flags = new LinkedHashSet<>();
		final Path folder;
		boolean integrated = true;
		@Nullable UUID host;
		Stage stage = Stage.TRACES;
		long day = 10L;
		long time = 15000L;
		long now = at(10, 16, 12, 0);
		boolean hardcore;
		Optional<String> story = Optional.empty();
		Optional<String> common = Optional.empty();

		Hooks(Path folder) {
			this.folder = folder;
		}

		@Override
		public boolean integrated(MinecraftServer server) {
			return integrated;
		}

		@Override
		public @Nullable UUID host(MinecraftServer server) {
			return host;
		}

		@Override
		public RecordData data(MinecraftServer server) {
			return data;
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return stage;
		}

		@Override
		public long day(MinecraftServer server) {
			return day;
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return time;
		}

		@Override
		public long nowMillis() {
			return now;
		}

		@Override
		public Path worldFolder(MinecraftServer server) {
			return folder;
		}

		@Override
		public Predicate<String> flags(MinecraftServer server) {
			return flags::contains;
		}

		@Override
		public boolean hardcore(MinecraftServer server) {
			return hardcore;
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return story;
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer player) {
			return common;
		}

		@Override
		public List<ServerPlayer> participants(MinecraftServer server) {
			return participants;
		}

		@Override
		public Optional<GlobalPos> base(ServerPlayer player) {
			return Optional.ofNullable(bases.get(player.getUUID()));
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(online.get(player));
		}

		@Override
		public List<ServerPlayer> players(MinecraftServer server) {
			return List.copyOf(online.values());
		}
	}

	private static Path testFolder(GameTestHelper helper, String name) {
		return SelfCreated.worldFolder(helper.getLevel().getServer()).resolve("sc4_test").resolve(name + "_" + UUID.randomUUID());
	}

	private static void deleteTree(Path folder) {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (Exception e) {
			// a test's own folder; nothing else is touched
		}
	}

	private static RecordRules.ServerFile readFile(Path folder) throws Exception {
		return RecordRules.tag(Files.readAllBytes(folder.resolve(RecordRules.FILE))).map(RecordRules.ServerFile::load).orElseThrow();
	}

	// --- (a): the fake time ---

	/** In the gap, in the latest night window it holds, deterministic by the nonce; else the middle of the gap. */
	@GameTest
	public void theFakeLastPlayedIsInTheGapAtNightAndDeterministic(GameTestHelper helper) {
		long quit = at(10, 10, 20, 0);
		long now = at(10, 11, 15, 0);
		for (long nonce : new long[] {1L, -7L, 42L, Long.MAX_VALUE, 123_456_789L}) {
			long fake = RecordRules.fakeLastPlayed(nonce, quit, now, ONE, HALF_FOUR, UTC);
			helper.assertTrue(fake >= at(10, 11, 1, 0) && fake <= at(10, 11, 4, 30), "nonce " + nonce + ": " + timeOf(fake));
			helper.assertTrue(fake == RecordRules.fakeLastPlayed(nonce, quit, now, ONE, HALF_FOUR, UTC), "not deterministic");
		}
		helper.assertTrue(RecordRules.fakeLastPlayed(1L, quit, now, ONE, HALF_FOUR, UTC) != RecordRules.fakeLastPlayed(42L, quit, now, ONE, HALF_FOUR, UTC),
				"the nonce picks nothing");
		// Several nights in the gap: the latest one.
		long weekLater = at(10, 17, 12, 0);
		long fake = RecordRules.fakeLastPlayed(5L, quit, weekLater, ONE, HALF_FOUR, UTC);
		helper.assertTrue(fake >= at(10, 17, 1, 0) && fake <= at(10, 17, 4, 30), "not the latest night: " + java.time.Instant.ofEpochMilli(fake));
		// The gap starts inside a night: only the part after the quit.
		long lateQuit = at(10, 11, 3, 0);
		long cut = RecordRules.fakeLastPlayed(9L, lateQuit, at(10, 11, 12, 0), ONE, HALF_FOUR, UTC);
		helper.assertTrue(cut > lateQuit && cut <= at(10, 11, 4, 30), "outside the gap's part of the night: " + timeOf(cut));
		// No night in the gap: the middle.
		long dayQuit = at(10, 11, 8, 0);
		long dayNow = at(10, 11, 18, 0);
		helper.assertTrue(RecordRules.fakeLastPlayed(3L, dayQuit, dayNow, ONE, HALF_FOUR, UTC) == at(10, 11, 13, 0), "not the middle of the gap");
		// No gap at all: the real time.
		helper.assertTrue(RecordRules.fakeLastPlayed(3L, dayNow, dayQuit, ONE, HALF_FOUR, UTC) == dayNow, "a fake with no gap");
		// A window past midnight (23:00 to 02:00).
		long wrap = RecordRules.fakeLastPlayed(11L, at(10, 11, 20, 0), at(10, 12, 12, 0), LocalTime.of(23, 0), LocalTime.of(2, 0), UTC);
		helper.assertTrue(wrap >= at(10, 11, 23, 0) && wrap <= at(10, 12, 2, 0), "the window past midnight: " + java.time.Instant.ofEpochMilli(wrap));
		helper.succeed();
	}

	// --- the files and the beats ---

	/** Both files round-trip; anything missing or unreadable reads as none, never failing. */
	@GameTest
	public void theFilesRoundTripLeniently(GameTestHelper helper) {
		RecordRules.ServerFile file = new RecordRules.ServerFile(Optional.of(new RecordRules.Beat(77L, false)), Optional.of(new RecordRules.Beat(-5L, true)),
				true, 123_456L, 18_000L);
		RecordRules.ServerFile back = RecordRules.tag(RecordRules.bytes(file.save())).map(RecordRules.ServerFile::load).orElseThrow();
		helper.assertTrue(back.equals(file), "the server's file: " + back);
		helper.assertTrue(RecordRules.ServerFile.load(new CompoundTag()).equals(RecordRules.ServerFile.EMPTY), "an empty tag");
		CompoundTag broken = new CompoundTag();
		broken.putString("a", "not a beat");
		broken.putInt("b", 7);
		broken.putString("stopMs", "soon");
		helper.assertTrue(RecordRules.ServerFile.load(broken).equals(RecordRules.ServerFile.EMPTY), "a broken tag");
		helper.assertTrue(RecordRules.tag(new byte[] {1, 2, 3}).isEmpty() && RecordRules.tag(null).isEmpty(), "garbage read as a tag");
		Set<Long> seen = new LinkedHashSet<>(List.of(3L, 1L, 2L));
		helper.assertTrue(RecordRules.loadSeen(RecordRules.tag(RecordRules.bytes(RecordRules.saveSeen(seen))).orElseThrow()).equals(seen), "the seen file");
		helper.assertTrue(RecordRules.loadSeen(new CompoundTag()).isEmpty(), "no seen beats");
		helper.succeed();
	}

	/** (a) shows after 6 hours away, once (or all this run once shown), never off; (b) once; a debug arm skips the gap. */
	@GameTest
	public void theWorldListShowsEachBeatOnce(GameTestHelper helper) {
		long gap = new RecordConfig().gapMillis();
		helper.assertTrue(gap == 6L * 3_600_000L, "6 hours is " + gap);
		long stop = at(10, 10, 20, 0);
		Optional<RecordRules.Beat> beat = Optional.of(new RecordRules.Beat(9L, false));
		helper.assertFalse(RecordRules.showsLast(true, Optional.empty(), Set.of(), false, stop, stop + gap, gap), "nothing armed");
		helper.assertFalse(RecordRules.showsLast(true, beat, Set.of(), false, stop, stop + gap - 1, gap), "under 6 hours away");
		helper.assertTrue(RecordRules.showsLast(true, beat, Set.of(), false, stop, stop + gap, gap), "6 hours away");
		helper.assertFalse(RecordRules.showsLast(true, beat, Set.of(9L), false, stop, stop + gap, gap), "shown before");
		helper.assertTrue(RecordRules.showsLast(true, beat, Set.of(9L), true, stop, stop + gap, gap), "shown in this run");
		helper.assertFalse(RecordRules.showsLast(false, beat, Set.of(), true, stop, stop + gap, gap), "the layer off");
		helper.assertTrue(RecordRules.showsLast(true, Optional.of(new RecordRules.Beat(9L, true)), Set.of(), false, stop, stop + 1, gap), "nogap");
		helper.assertFalse(RecordRules.showsLast(true, beat, Set.of(), false, 0L, stop + gap, gap), "no stop written");
		Optional<RecordRules.Beat> version = Optional.of(new RecordRules.Beat(4L, false));
		helper.assertTrue(RecordRules.showsVersion(true, version, Set.of(9L), false), "the version beat");
		helper.assertFalse(RecordRules.showsVersion(true, version, Set.of(4L), false), "the version shown twice");
		helper.assertTrue(RecordRules.showsVersion(true, version, Set.of(4L), true), "the version in this run");
		helper.assertFalse(RecordRules.showsVersion(false, version, Set.of(), false), "the version with the layer off");
		helper.assertTrue(RecordRules.CANON_VERSION.equals("a1.0.16_02") && RecordRules.CANON_SEED.equals("478868574082066804"), "the canon numbers");
		helper.succeed();
	}

	/** (b) arms at a night stop from Telling, 2 days after (a) was shown, once; never on a day stop, early, or held. */
	@GameTest
	public void theVersionArmsAtANightStopTwoDaysAfter(GameTestHelper helper) {
		int telling = Stage.TELLING.level();
		helper.assertTrue(RecordRules.versionArms(true, false, false, 5L, 7L, 2.0, telling, 3, 15_000L), "a night stop 2 days after");
		helper.assertFalse(RecordRules.versionArms(true, false, false, 5L, 6L, 2.0, telling, 3, 15_000L), "1 day after");
		helper.assertFalse(RecordRules.versionArms(true, false, false, -1L, 7L, 2.0, telling, 3, 15_000L), "(a) never shown");
		helper.assertFalse(RecordRules.versionArms(true, false, false, 5L, 7L, 2.0, Stage.PROXIMITY.level(), 3, 15_000L), "before Telling");
		helper.assertFalse(RecordRules.versionArms(true, false, false, 5L, 7L, 2.0, telling, 3, 12_999L), "a day stop");
		helper.assertFalse(RecordRules.versionArms(true, false, false, 5L, 7L, 2.0, telling, 3, 23_000L), "a dawn stop");
		helper.assertFalse(RecordRules.versionArms(true, true, false, 5L, 7L, 2.0, telling, 3, 15_000L), "done");
		helper.assertFalse(RecordRules.versionArms(true, false, true, 5L, 7L, 2.0, telling, 3, 15_000L), "armed already");
		helper.assertFalse(RecordRules.versionArms(false, false, false, 5L, 7L, 2.0, telling, 3, 15_000L), "the story holds it");
		helper.succeed();
	}

	/**
	 * The record's files: the card's arm writes (a) with a nonce; a start that finds the nonce in the seen file makes (a) done
	 * on that day and writes it out; a night stop 2 days later from Telling arms (b) with the stop's time; a finished hardcore
	 * line of the host's reads the version for good; the switch's revert writes the file empty; nothing on a dedicated server.
	 */
	@GameTest
	public void theRecordWritesItsFileOnArmAndStopAndReadsWhatTheListShowed(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path folder = testFolder(helper, "record");
		Hooks hooks = new Hooks(folder);
		ServerPlayer host = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer guest = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		hooks.host = host.getUUID();
		TheRecord record = new TheRecord(hooks);
		try {
			helper.assertTrue(record.fit(host).isEmpty(), "the host does not fit: " + record.fit(host));
			helper.assertTrue(record.fit(guest).isPresent(), "a guest fits");
			helper.assertTrue(record.armLast(server, false) == FireResult.FIRED, "not armed");
			long nonce = hooks.data.lastNonce();
			RecordRules.ServerFile file = readFile(folder);
			helper.assertTrue(file.last().equals(Optional.of(new RecordRules.Beat(nonce, false))) && file.version().isEmpty() && !file.forever(),
					"after the arm: " + file);
			helper.assertTrue(record.fit(host).isPresent(), "fits again while armed");

			hooks.now += 3_600_000L;
			hooks.time = 16_000L;
			record.onStopping(server);
			file = readFile(folder);
			helper.assertTrue(file.stopMillis() == hooks.now && file.stopTimeOfDay() == 16_000L && file.last().isPresent() && file.version().isEmpty(),
					"the stop: " + file);

			record.onStarted(server); // nothing shown yet: still armed
			helper.assertTrue(hooks.data.lastArmed() && !hooks.data.lastDone(), "done before it was shown");
			SelfCreated.writeInWorld(folder, RecordRules.SEEN_FILE, RecordRules.bytes(RecordRules.saveSeen(Set.of(nonce))));
			hooks.day = 12L;
			record.onStarted(server);
			helper.assertTrue(!hooks.data.lastArmed() && hooks.data.lastDone() && hooks.data.lastShownDay() == 12L, "not done after it was shown");
			helper.assertTrue(readFile(folder).last().isEmpty(), "the file still arms (a)");
			helper.assertTrue(record.fit(host).isPresent(), "fits once done");

			hooks.stage = Stage.TELLING;
			hooks.day = 13L;
			record.onStopping(server);
			helper.assertTrue(readFile(folder).version().isEmpty(), "(b) armed one day after");
			hooks.day = 14L;
			hooks.time = 6_000L;
			record.onStopping(server);
			helper.assertTrue(readFile(folder).version().isEmpty(), "(b) armed at a day stop");
			hooks.time = 18_000L;
			record.onStopping(server);
			file = readFile(folder);
			helper.assertTrue(file.version().isPresent() && file.version().get().nonce() == hooks.data.versionNonce() && hooks.data.versionArmed(),
					"(b) not armed at a night stop 2 days after: " + file);

			hooks.hardcore = true;
			hooks.flags.add("ending:ended");
			record.onStopping(server);
			helper.assertFalse(readFile(folder).forever(), "for good without the host's line finished");
			hooks.flags.add("ending:line_finished=" + guest.getUUID());
			record.onStopping(server);
			helper.assertFalse(readFile(folder).forever(), "for good on a guest's line");
			hooks.flags.add("ending:line_finished=" + host.getUUID());
			record.onStopping(server);
			helper.assertTrue(readFile(folder).forever(), "not for good on the host's finished line");

			record.revert(server);
			file = readFile(folder);
			helper.assertTrue(file.last().isEmpty() && file.version().isEmpty() && !file.forever(), "the revert left a beat: " + file);
			record.revert(server); // idempotent

			Path other = testFolder(helper, "dedicated");
			Hooks dedicated = new Hooks(other);
			dedicated.integrated = false;
			TheRecord onDedicated = new TheRecord(dedicated);
			onDedicated.armLast(server, false);
			onDedicated.onStopping(server);
			helper.assertFalse(Files.exists(other.resolve(RecordRules.FILE)), "a file on a dedicated server");
			deleteTree(other);
		} finally {
			deleteTree(folder);
		}
		helper.succeed();
	}

	/** The card: sc_14, MINOR, Traces, TEXT and UNSHARED, WATCHER and VISITOR, no fake; the section reads fresh at version 2 with the design's numbers. */
	@GameTest
	public void theRecordCardAndSection(GameTestHelper helper) {
		EventCard card = CardRegistry.get("sc_14").orElseThrow(() -> helper.assertionException("sc_14 is not registered"));
		helper.assertTrue(card.tier() == Tier.MINOR && card.earliestStage() == Stage.TRACES && card.tags().equals(Set.of(CardTag.TEXT, CardTag.UNSHARED))
				&& card.habits().equals(Set.of(Habit.WATCHER, Habit.VISITOR)) && !card.hasFake(), "the card's shape");
		RecordConfig c = ModConfig.readSection(new JsonObject(), RecordConfig.SECTION, RecordConfig.class, RecordConfig::new);
		helper.assertTrue(c.version == 2 && c.gapHours == 6.0 && c.nightFromTime().equals(ONE) && c.nightToTime().equals(HALF_FOUR) && c.versionFromStage == 3
				&& c.versionAfterDays == 2.0 && c.seedFromStage == 2 && c.stats && c.statsRadius == 64.0, "sc14");
		RecordConfig bad = new RecordConfig();
		bad.nightFrom = "late";
		helper.assertTrue(bad.nightFromTime().equals(ONE), "a bad time is not the default");
		helper.succeed();
	}

	// --- (c): /seed ---

	/** (c) on a test's own record: from Proximity once, never in Traces, never past the gates; the debug's arm skips the stage. */
	@GameTest
	public void theSeedIsTheCanonOneOnceFromProximity(GameTestHelper helper) {
		Hooks hooks = new Hooks(testFolder(helper, "seed"));
		TheRecord record = new TheRecord(hooks);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		CommandSourceStack source = player.createCommandSourceStack();
		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();
		String real = "123";
		helper.assertTrue(record.seedText(source, real).equals(real), "the canon seed in Traces");
		hooks.stage = Stage.PROXIMITY;
		hooks.common = Optional.of("combat");
		helper.assertTrue(record.seedText(source, real).equals(real), "the canon seed past the gates");
		hooks.common = Optional.empty();
		helper.assertTrue(record.seedText(console, real).equals(real), "the canon seed to the console");
		helper.assertTrue(record.seedText(source, real).equals(RecordRules.CANON_SEED), "not the canon seed from Proximity");
		helper.assertTrue(record.seedText(source, real).equals(real) && hooks.data.seedIsDone(), "the canon seed twice");
		Hooks forced = new Hooks(testFolder(helper, "seed_forced"));
		TheRecord debug = new TheRecord(forced);
		debug.forceSeed();
		helper.assertTrue(debug.seedText(source, real).equals(RecordRules.CANON_SEED) && !debug.seedForced(), "the debug's arm");
		helper.succeed();
	}

	/** The real {@code /seed} (vanilla's command, the mixin, the live record): "Seed: [canon]" once after the debug's arm, then the real one. */
	@GameTest
	public void theRealSeedCommandAnswersTheCanonSeedOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		List<Component> said = new ArrayList<>();
		CommandSource recorder = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack source = player.createCommandSourceStack().withSource(recorder)
				.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER); // as an op runs it on a dedicated server
		String realSeed = String.valueOf(helper.getLevel().getSeed());
		try {
			TheRecord.LIVE.reset(server);
			TheRecord.LIVE.forceSeed();
			server.getCommands().performPrefixedCommand(source, "seed");
			server.getCommands().performPrefixedCommand(source, "seed");
			helper.assertTrue(said.size() == 2, "said " + said);
			helper.assertTrue(said.get(0).getString().equals("Seed: [" + RecordRules.CANON_SEED + "]"), "first: " + said.get(0).getString());
			helper.assertTrue(said.get(1).getString().equals("Seed: [" + realSeed + "]"), "second: " + said.get(1).getString());
		} finally {
			TheRecord.LIVE.reset(server); // the test server's record as a fresh world has it
			TheRecord.LIVE.clear();
		}
		helper.succeed();
	}

	// --- (d): the statistics ---

	private static TraceLedger.Entry remove(ServerLevel level, BlockPos pos, String cause) {
		return new TraceLedger.Entry(TraceLedger.Kind.REMOVE, cause, 1L, GlobalPos.of(level.dimension(), pos), Optional.empty(),
				Optional.of(Blocks.TORCH.defaultBlockState()), Optional.empty(), Optional.empty(), 0, 0);
	}

	private static TraceLedger.Entry takeStack(ServerLevel level, BlockPos pos, String cause) {
		return new TraceLedger.Entry(TraceLedger.Kind.REMOVE_STACK, cause, 1L, GlobalPos.of(level.dimension(), pos), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.of(new ItemStack(Items.BREAD, 5)), 3, 0);
	}

	/**
	 * A placed torch he removes counts once in "Times Mined" and a stack of 5 bread he takes 5 in "Times Dropped", for the
	 * participant whose base is nearest within 64 (else the nearest participant within 64; nobody past it); never a natural
	 * block, what others left, a give-back, Ending D's or the figure's own dig, nor in the Nether. The switch's revert takes it
	 * back (never under 0).
	 */
	@GameTest
	public void statsAreCreditedForHisRemovalsAndTakenBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Hooks hooks = new Hooks(testFolder(helper, "stats"));
		TheRecord record = new TheRecord(hooks);
		ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer b = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos spot = helper.absolutePos(new BlockPos(1, 1, 1));
		a.snapTo(Vec3.atCenterOf(spot.offset(3, 0, 0)), 0.0F, 0.0F); // A stands 3 off
		b.snapTo(Vec3.atCenterOf(spot.offset(50, 0, 0)), 0.0F, 0.0F);
		hooks.participants.addAll(List.of(a, b));
		hooks.online.put(a.getUUID(), a);
		hooks.online.put(b.getUUID(), b);
		hooks.bases.put(a.getUUID(), GlobalPos.of(level.dimension(), spot.offset(40, 0, 0)));
		hooks.bases.put(b.getUUID(), GlobalPos.of(level.dimension(), spot.offset(10, 0, 0))); // B's base is nearest
		var torch = Stats.BLOCK_MINED.get(Blocks.TORCH);
		var bread = Stats.ITEM_DROPPED.get(Items.BREAD);
		int aTorch = a.getStats().getValue(torch);
		int bTorch = b.getStats().getValue(torch);
		int bBread = b.getStats().getValue(bread);

		record.onTraced(level, remove(level, spot, "world:sc08"), true);
		helper.assertTrue(b.getStats().getValue(torch) == bTorch + 1 && a.getStats().getValue(torch) == aTorch, "not B's (the nearest base)");
		record.onTraced(level, takeStack(level, spot, "atmosphere:chest"), false);
		helper.assertTrue(b.getStats().getValue(bread) == bBread + 5, "the stack of 5 bread");
		record.onTraced(level, remove(level, spot, "world:sc08"), false);
		for (String cause : List.of("world:left/t0rch/torch", "lore:left/F07", TraceService.GIVE_BACK_CAUSE, "ending:d/cairn", "entity:goes_under")) {
			record.onTraced(level, remove(level, spot, cause), true);
		}
		helper.assertTrue(b.getStats().getValue(torch) == bTorch + 1, "counted a natural block or a cause that never counts");
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether != null) {
			record.onTraced(nether, remove(nether, spot, "world:sc08"), true);
			helper.assertTrue(b.getStats().getValue(torch) == bTorch + 1, "counted in the Nether");
		}
		// No base within 64: the nearest participant within 64 (A, 3 off); nobody within 64: nobody.
		hooks.bases.clear();
		record.onTraced(level, remove(level, spot, "world:sc08"), true);
		helper.assertTrue(a.getStats().getValue(torch) == aTorch + 1, "not the nearest participant");
		a.snapTo(Vec3.atCenterOf(spot.offset(70, 0, 0)), 0.0F, 0.0F);
		b.snapTo(Vec3.atCenterOf(spot.offset(0, 0, 80)), 0.0F, 0.0F);
		record.onTraced(level, remove(level, spot, "world:sc08"), true);
		helper.assertTrue(a.getStats().getValue(torch) == aTorch + 1 && b.getStats().getValue(torch) == bTorch + 1, "credited past 64");
		hooks.story = Optional.of("the story holds it");
		a.snapTo(Vec3.atCenterOf(spot.offset(3, 0, 0)), 0.0F, 0.0F);
		record.onTraced(level, remove(level, spot, "world:sc08"), true);
		helper.assertTrue(a.getStats().getValue(torch) == aTorch + 1, "counted while the story holds it");

		helper.assertTrue(hooks.data.awards().size() == 3, "awards kept: " + hooks.data.awards());
		record.revert(server);
		helper.assertTrue(a.getStats().getValue(torch) == aTorch && b.getStats().getValue(torch) == bTorch && b.getStats().getValue(bread) == bBread,
				"not taken back");
		helper.assertTrue(hooks.data.awards().isEmpty(), "awards left after the revert");
		record.revert(server); // idempotent
		helper.assertTrue(a.getStats().getValue(torch) == aTorch, "taken back twice");
		deleteTree(hooks.folder);
		helper.succeed();
	}
}
