package com.forzacode.a1016_02.atmosphere.card;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.atmosphere.WeatherFog;
import com.forzacode.a1016_02.atmosphere.client.ShaderPacks;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNightPayload;
import com.forzacode.a1016_02.atmosphere.sc.Sc04Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc05Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc06Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc07Config;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The layer's atmosphere part (docs/SELF_CREATED.md), half one: the cards and sections, 04's night (the roll, the
 * windows, the undo, the spawns, the spawners, the quiet, the save and the payload, the switch), 07's names and the fog
 * places on the client. Every test brings its own players ({@link Watched}: mock players never join the server), sounds
 * ({@link Ears}), view rule ({@link #seenBy}) and clocks, and puts back anything live it touched within its own tick.
 * {@link SelfCreatedAtmosphereTests} extends this (05 and 06).
 */
public class SelfCreatedAtmosphereNightTests {
	static final String YARD = "a1016_02:accident/yard";
	static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	static final long DAY = 24_000L;

	/** A test's own online players and state; nobody's participation is reported anywhere. */
	static final class Watched implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		Watched(ServerPlayer... players) {
			online.addAll(List.of(players));
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return online;
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}
	}

	/** One sound: on which tick of the test's clock, what, where, how loud, and who heard it. */
	record Heard(long tick, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch, List<UUID> hearers) {
	}

	/** A sound sink on the test's own clock that hears among the test's players by core's range rule. */
	static final class Ears implements SoundOut {
		final List<ServerPlayer> candidates = new ArrayList<>();
		final List<Heard> log = new ArrayList<>();
		long tick;

		Ears(ServerPlayer... candidates) {
			this.candidates.addAll(List.of(candidates));
		}

		@Override
		public List<ServerPlayer> play(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
			List<ServerPlayer> hearers = SoundCues.hearers(level, candidates, pos, volume);
			log.add(new Heard(tick, sound.value(), source, pos, volume, pitch, hearers.stream().map(ServerPlayer::getUUID).toList()));
			return hearers;
		}
	}

	static ServerPlayer at(GameTestHelper helper, Vec3 relative, float yaw) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		move(helper, player, relative, yaw);
		player.setOnGround(true);
		return player;
	}

	/** Puts the player there, looking along {@code yaw} (0: south, +Z; 180: north): the view vector follows the head. */
	static void move(GameTestHelper helper, ServerPlayer player, Vec3 relative, float yaw) {
		Vec3 pos = helper.absoluteVec(relative);
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		player.setYHeadRot(yaw);
	}

	/** The live view rule's geometry over these players' own viewpoints. */
	static BiPredicate<ServerLevel, AABB> seenBy(ServerPlayer... players) {
		Pacing pacing = ModConfig.pacing();
		return (level, box) -> {
			List<TraceService.Viewer> viewers = new ArrayList<>();
			for (ServerPlayer player : players) {
				viewers.add(TraceService.Viewer.of(player, 10));
			}
			return TraceService.isOutOfView(level, box, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
		};
	}

	static void floor(GameTestHelper helper, Block block) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, block);
			}
		}
	}

	static void home(ServerPlayer player, BlockPos absolute) {
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(player.level().dimension(), absolute, 0, 0), false), false);
	}

	static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			return codec.decode(buf);
		} finally {
			buf.release();
		}
	}

	// --- the cards and the sections ---

	/** The four cards are the table's (tier, stage, tags, habits, fake), and every section is version 1 with the doc's numbers. */
	@GameTest
	public void theLayersAtmosphereCardsAndSectionsAreTheDocs(GameTestHelper helper) {
		card(helper, "sc_05", Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL), Set.of(Habit.VISITOR, Habit.WATCHER), true);
		card(helper, "sc_05b", Tier.AMBIENT, Stage.TRACES, Set.of(CardTag.MOB), Set.of(Habit.WATCHER, Habit.VISITOR), true);
		card(helper, "sc_06", Tier.AMBIENT, Stage.ALONE, Set.of(CardTag.SOUND, CardTag.PERSONAL), Set.of(Habit.WATCHER), true);
		card(helper, "sc_07", Tier.MINOR, Stage.PROXIMITY, Set.of(CardTag.MOB), Set.of(Habit.COLLECTOR, Habit.STRIPPER), false);

		Sc04Config c4 = new Sc04Config();
		helper.assertTrue(java.util.Arrays.equals(c4.chanceByStage, new double[] {0, 0.2, 0.25, 0.25, 0.15}) && c4.minNightsApart == 6 && c4.maxPerWorld == 5
				&& c4.brightPhasesOnly && c4.spawnRadius == 96 && c4.quietRadius == 48 && c4.quietAnimals && c4.noPhantoms, "sc04's numbers");
		Sc05Config c5 = new Sc05Config();
		helper.assertTrue(c5.awayDistance == 48 && c5.awayMinutes == 10 && c5.armMinutes == 60 && c5.houseRadius == 6 && c5.minCoveredCells == 4
				&& c5.minDistance == 6 && c5.maxDistance == 16 && c5.stepsMin == 3 && c5.stepsMax == 5 && c5.stepTicks == 8 && c5.stepVolume == 1.6
				&& c5.closeDelayMin == 15 && c5.closeDelayMax == 30 && c5.closeVolume == 1.6 && c5.approachDistance == 32 && c5.mobRadius == 24
				&& c5.minMobs == 3 && c5.holdSecondsMax == 12 && c5.lookSeconds == 2, "sc05's numbers");
		Sc06Config c6 = new Sc06Config();
		helper.assertTrue(c6.walkSecondsBeforeStart == 3 && c6.behindMin == 6 && c6.behindMax == 9 && c6.lagTicksMin == 4 && c6.lagTicksMax == 6
				&& c6.stepsMin == 6 && c6.stepsMax == 14 && c6.stepVolume == 1.2 && c6.lookBackDegrees == 70, "sc06's numbers");
		Sc07Config c7 = new Sc07Config();
		helper.assertTrue(c7.baseRadius == 64 && c7.nobodyWithin == 16 && c7.awayDistance == 48 && c7.maxPerWorld == 3 && c7.minDaysApart == 3, "sc07's numbers");
		// Every sound is heard at the far end of its band and stays inside the alone radius (48).
		helper.assertTrue(c5.stepVolume * 16 > c5.maxDistance + c5.houseRadius && c5.stepVolume * 16 < 48 && c5.closeVolume * 16 < 48
				&& c6.stepVolume * 16 > c6.behindMax && c6.stepVolume * 16 < 48, "a sound falls short of its band or past the alone radius");

		// Version 2: an old file's section (version 0, a value changed by hand under its old key) is stamped 2 and keeps the value.
		JsonObject sections = JsonParser.parseString("{\"sc04\": {\"maxPerWorld\": 2}, \"sc05\": {\"stepVolume\": 1.2}, \"sc06\": {\"behindMax\": 8},"
				+ " \"sc07\": {\"minDaysApart\": 5}}").getAsJsonObject();
		Sc04Config r4 = ModConfig.readSection(sections, Sc04Config.SECTION, Sc04Config.class, Sc04Config::new);
		Sc05Config r5 = ModConfig.readSection(sections, Sc05Config.SECTION, Sc05Config.class, Sc05Config::new);
		Sc06Config r6 = ModConfig.readSection(sections, Sc06Config.SECTION, Sc06Config.class, Sc06Config::new);
		Sc07Config r7 = ModConfig.readSection(sections, Sc07Config.SECTION, Sc07Config.class, Sc07Config::new);
		helper.assertTrue(r4.version == 2 && r4.maxPerWorld == 2 && r5.version == 2 && r5.stepVolume == 1.2 && r6.version == 2 && r6.behindMax == 8
				&& r7.version == 2 && r7.minDaysApart == 5, "a section's upgrade lost a value or its version");
		helper.assertTrue(!r4.upgrade() && Sc04Config.get().version == 2 && Sc05Config.get().version == 2 && Sc06Config.get().version == 2
				&& Sc07Config.get().version == 2, "the live sections are not version 2");
		helper.succeed();
	}

	private static void card(GameTestHelper helper, String id, Tier tier, Stage stage, Set<CardTag> tags, Set<Habit> habits, boolean fake) {
		EventCard card = CardRegistry.get(id).orElse(null);
		helper.assertTrue(card != null && card.tier() == tier && card.earliestStage() == stage && card.tags().equals(tags) && card.habits().equals(habits)
				&& card.hasFake() == fake, id + " is not the table's: " + card);
	}

	// --- 04 ---

	/** The roll: never in Alone, never over a fog spell, bright phases only, spaced six nights, five a world, once a night, the stage's chance. */
	@GameTest
	public void theNightRollsOnlyOnABrightClearNightFromTracesSpacedAndCapped(GameTestHelper helper) {
		Sc04Config cfg = new Sc04Config();
		EmptyNight.Memory start = EmptyNight.Memory.START;
		helper.assertTrue(EmptyNight.refusal(start, 8, Stage.ALONE, MoonPhase.FULL_MOON, false, cfg).isPresent(), "Alone");
		helper.assertTrue(EmptyNight.refusal(start, 8, Stage.TRACES, MoonPhase.FULL_MOON, true, cfg).isPresent(), "a fog night");
		for (MoonPhase phase : MoonPhase.values()) {
			boolean bright = phase == MoonPhase.FULL_MOON || phase == MoonPhase.WANING_GIBBOUS || phase == MoonPhase.WAXING_GIBBOUS;
			helper.assertTrue(EmptyNight.refusal(start, 8, Stage.TRACES, phase, false, cfg).isEmpty() == bright, "the phase rule for " + phase);
		}
		cfg.brightPhasesOnly = false;
		helper.assertTrue(EmptyNight.refusal(start, 8, Stage.TRACES, MoonPhase.NEW_MOON, false, cfg).isEmpty(), "brightPhasesOnly false keeps a dark night");
		cfg.brightPhasesOnly = true;
		EmptyNight.Memory last = new EmptyNight.Memory(10, 10, 1, EmptyNight.NEVER, Optional.empty());
		helper.assertTrue(EmptyNight.refusal(last, 15, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg).isPresent()
				&& EmptyNight.refusal(last, 16, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg).isEmpty(), "six nights apart");
		EmptyNight.Memory full = new EmptyNight.Memory(40, 30, 5, EmptyNight.NEVER, Optional.empty());
		helper.assertTrue(EmptyNight.refusal(full, 48, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg).isPresent(), "five a world");
		helper.assertTrue(cfg.chance(Stage.ALONE) == 0 && cfg.chance(Stage.TRACES) == 0.2 && cfg.chance(Stage.PROXIMITY) == 0.25
				&& cfg.chance(Stage.TELLING) == 0.25 && cfg.chance(Stage.REMOVAL) == 0.15, "the stage's chance");

		// Certain: the chance at 1 rolls it, at 0 never; a roll is kept with what it changed, and runs once a night.
		cfg.chanceByStage = new double[] {1, 1, 1, 1, 1};
		EmptyNight.Memory rolled = EmptyNight.roll(start, 8 * DAY + 10_000, 8, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg, RandomSource.create(1));
		helper.assertTrue(rolled.night() == 8 && rolled.last() == 8 && rolled.count() == 1 && rolled.rolled() == 8 && rolled.undo().isPresent()
				&& rolled.undo().get().at() == 8 * DAY + 10_000, "a certain roll: " + rolled);
		helper.assertTrue(EmptyNight.roll(rolled, 8 * DAY + 10_500, 8, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg, RandomSource.create(2)) == rolled,
				"night 8 rolled twice");
		cfg.chanceByStage = new double[] {0, 0, 0, 0, 0};
		EmptyNight.Memory clear = EmptyNight.roll(start, 8 * DAY + 10_000, 8, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg, RandomSource.create(1));
		helper.assertTrue(clear.night() == EmptyNight.NEVER && clear.rolled() == 8 && clear.count() == 0, "a roll at no chance made one: " + clear);
		// The roll's time: from fog weather's night roll until the moon rises (11000), and only then.
		helper.assertTrue(EmptyNight.rollTime(8 * DAY + 10_000, 8, 10_000) && EmptyNight.rollTime(8 * DAY + 10_999, 8, 10_000)
				&& !EmptyNight.rollTime(8 * DAY + 11_000, 8, 10_000) && !EmptyNight.rollTime(8 * DAY + 9_999, 8, 10_000), "the roll's time");
		helper.succeed();
	}

	/** The windows: the moon is new from 11000 to 1000 the next day (below the horizon at both ends), the quiet from 12000 to 23500. */
	@GameTest
	public void theNightsWindowsHoldTheMoonFromBeforeItRisesToAfterItSets(GameTestHelper helper) {
		EmptyNight.Window sky = EmptyNight.sky(5);
		EmptyNight.Window quiet = EmptyNight.quiet(5);
		helper.assertTrue(sky.from() == 5 * DAY + 11_000 && sky.until() == 6 * DAY + 1_000 && quiet.from() == 5 * DAY + 12_000 && quiet.until() == 5 * DAY + 23_500,
				"the windows: " + sky + " " + quiet);
		Optional<EmptyNight.Window> w = Optional.of(sky);
		helper.assertTrue(EmptyNight.moonFor(5 * DAY + 10_999, w, MoonPhase.FULL_MOON) == MoonPhase.FULL_MOON
				&& EmptyNight.moonFor(5 * DAY + 11_000, w, MoonPhase.FULL_MOON) == MoonPhase.NEW_MOON
				&& EmptyNight.moonFor(5 * DAY + 18_000, w, MoonPhase.WANING_GIBBOUS) == MoonPhase.NEW_MOON
				&& EmptyNight.moonFor(6 * DAY + 999, w, MoonPhase.THIRD_QUARTER) == MoonPhase.NEW_MOON
				&& EmptyNight.moonFor(6 * DAY + 1_000, w, MoonPhase.THIRD_QUARTER) == MoonPhase.THIRD_QUARTER
				&& EmptyNight.moonFor(5 * DAY + 18_000, Optional.empty(), MoonPhase.FULL_MOON) == MoonPhase.FULL_MOON, "the moon drawn");
		// Vanilla's default timeline: one phase a day, full on day 0; the next night is one phase on.
		helper.assertTrue(EmptyNight.defaultPhase(0) == MoonPhase.FULL_MOON && EmptyNight.defaultPhase(1) == MoonPhase.WANING_GIBBOUS
				&& EmptyNight.defaultPhase(7) == MoonPhase.WAXING_GIBBOUS && EmptyNight.defaultPhase(8) == MoonPhase.FULL_MOON
				&& EmptyNight.defaultPhase(-1) == MoonPhase.WAXING_GIBBOUS, "the default phases");
		helper.assertTrue(helper.getLevel().environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE)
				== EmptyNight.defaultPhase(Math.floorDiv(helper.getLevel().getDefaultClockTime(), DAY)), "the live phase is not the default timeline's");
		// A fog spell over 13000 to 23000 of the night rules it out; a fog day or a morning carry-over does not.
		helper.assertTrue(EmptyNight.fogOver(Optional.of(new FogSpell(5 * DAY + 11_500, 5 * DAY + 23_500, 0.5F, false)), 5)
				&& !EmptyNight.fogOver(Optional.of(new FogSpell(4 * DAY + 23_000, 5 * DAY + 11_500, 0.5F, false)), 5)
				&& !EmptyNight.fogOver(Optional.of(new FogSpell(4 * DAY + 11_500, 5 * DAY + 5_500, 0.5F, false)), 5)
				&& !EmptyNight.fogOver(Optional.empty(), 5), "the fog rule");
		helper.succeed();
	}

	/** A clock set back before the roll undoes it (the night rolls again); set back further, it rolls as new; a night over is dropped. */
	@GameTest
	public void aClockSetBackUndoesTheNightAndItsWindowEnds(GameTestHelper helper) {
		Sc04Config cfg = new Sc04Config();
		cfg.chanceByStage = new double[] {1, 1, 1, 1, 1};
		EmptyNight.Memory before = new EmptyNight.Memory(7, 1, 1, EmptyNight.NEVER, Optional.empty());
		EmptyNight.Memory rolled = EmptyNight.roll(before, 8 * DAY + 10_000, 8, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg, RandomSource.create(3));
		helper.assertTrue(rolled.night() == 8 && rolled.count() == 2, "no night 8: " + rolled);
		// Still in its night: kept.
		helper.assertTrue(EmptyNight.settle(rolled, 8 * DAY + 20_000, 8).equals(rolled), "settled away in its own night");
		// Back before the roll: undone, exactly as before.
		EmptyNight.Memory back = EmptyNight.settle(rolled, 8 * DAY + 9_000, 7);
		helper.assertTrue(back.rolled() == 7 && back.last() == 1 && back.count() == 1 && back.night() == EmptyNight.NEVER && back.undo().isEmpty(),
				"set back before the roll: " + back);
		// Back three days: undone, then the nights ahead roll again as new ones (the count stays).
		EmptyNight.Memory far = EmptyNight.settle(rolled, 5 * DAY + 12_000, 5);
		helper.assertTrue(far.rolled() == 4 && far.night() == EmptyNight.NEVER && far.count() == 1, "set back three days: " + far);
		// Its sky window over (1000 of the next day): the night goes, the count stays.
		EmptyNight.Memory over = EmptyNight.settle(rolled, 9 * DAY + 1_000, 8);
		helper.assertTrue(over.night() == EmptyNight.NEVER && over.count() == 2 && over.last() == 8, "the window ended: " + over);
		// A night set by hand at 05000 for this evening is kept until it comes; its own roll then finds it too soon.
		EmptyNight.Memory forced = EmptyNight.forced(before, 9 * DAY + 5_000, 9);
		EmptyNight.Memory waiting = EmptyNight.settle(forced, 9 * DAY + 6_000, 8);
		helper.assertTrue(waiting.night() == 9, "a forced night dropped before it came: " + waiting);
		EmptyNight.Memory itsRoll = EmptyNight.roll(waiting, 9 * DAY + 10_000, 9, Stage.TRACES, MoonPhase.FULL_MOON, false, cfg, RandomSource.create(4));
		helper.assertTrue(itsRoll.night() == 9 && itsRoll.count() == 2 && itsRoll.rolled() == 9, "the forced night's own roll: " + itsRoll);
		helper.succeed();
	}

	/** In its quiet window no natural monster spawns within 96 (horizontal) of a participant; animals, spawners and far spots are vanilla's. */
	@GameTest(structure = YARD)
	public void theNightRefusesNaturalMonsterSpawnsNearParticipantsOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(5, 0, 5, Blocks.STONE);
		BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
		long now = level.getDefaultClockTime();
		Vec3 near = Vec3.atCenterOf(pos).add(60, 0, 60);
		EmptyNight.Snapshot on = new EmptyNight.Snapshot(Optional.of(new EmptyNight.Window(now - 100, now + 10_000)), List.of(near), 96);
		// Pure: the rule.
		helper.assertTrue(EmptyNight.refuses(on, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, Level.OVERWORLD, now, pos.getX(), pos.getZ()), "a zombie near");
		helper.assertTrue(!EmptyNight.refuses(on, EntityTypes.COW, EntitySpawnReason.NATURAL, Level.OVERWORLD, now, pos.getX(), pos.getZ()), "a cow refused");
		helper.assertTrue(!EmptyNight.refuses(on, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, Level.OVERWORLD, now, pos.getX(), pos.getZ()), "a spawner refused");
		helper.assertTrue(!EmptyNight.refuses(on, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, Level.NETHER, now, pos.getX(), pos.getZ()), "the Nether");
		helper.assertTrue(!EmptyNight.refuses(on, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, Level.OVERWORLD, now + 10_000, pos.getX(), pos.getZ()),
				"after the window");
		helper.assertTrue(!EmptyNight.refuses(on, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, Level.OVERWORLD, now, pos.getX() + 200, pos.getZ()),
				"200 blocks from everyone");
		helper.assertTrue(EmptyNight.refuses(on, EntityTypes.SKELETON, EntitySpawnReason.NATURAL, Level.OVERWORLD, now, near.x + 90, near.z + 30)
				&& !EmptyNight.refuses(on, EntityTypes.SKELETON, EntitySpawnReason.NATURAL, Level.OVERWORLD, now, near.x + 90, near.z + 40),
				"the radius is horizontal 96");
		// Live, through the spawn rules mixin (the dragon's rule is only the ground, no light, so vanilla allows it here unless peaceful).
		EmptyNight.Snapshot before = EmptyNight.snapshot();
		try {
			EmptyNight.snapshot(EmptyNight.Snapshot.NONE);
			boolean vanilla = SpawnPlacements.checkSpawnRules(EntityTypes.ENDER_DRAGON, level, EntitySpawnReason.NATURAL, pos, level.getRandom());
			boolean vanillaSpawner = SpawnPlacements.checkSpawnRules(EntityTypes.ENDER_DRAGON, level, EntitySpawnReason.SPAWNER, pos, level.getRandom());
			helper.assertTrue(vanilla || level.getDifficulty() == Difficulty.PEACEFUL, "vanilla refuses a dragon on stone");
			EmptyNight.snapshot(on);
			helper.assertFalse(SpawnPlacements.checkSpawnRules(EntityTypes.ENDER_DRAGON, level, EntitySpawnReason.NATURAL, pos, level.getRandom()),
					"a natural monster spawn went ahead on the night");
			helper.assertTrue(SpawnPlacements.checkSpawnRules(EntityTypes.ENDER_DRAGON, level, EntitySpawnReason.SPAWNER, pos, level.getRandom()) == vanillaSpawner,
					"a spawner's spawn was refused");
			EmptyNight.snapshot(new EmptyNight.Snapshot(Optional.of(new EmptyNight.Window(now + 100, now + 10_000)), List.of(near), 96));
			helper.assertTrue(SpawnPlacements.checkSpawnRules(EntityTypes.ENDER_DRAGON, level, EntitySpawnReason.NATURAL, pos, level.getRandom()) == vanilla,
					"refused before the window");
		} finally {
			EmptyNight.snapshot(before);
		}
		helper.succeed();
	}

	/** In its quiet window the phantom spawner does not run in the overworld (its own timer stands still); outside it, vanilla's. */
	@GameTest
	public void theNightHoldsThePhantomSpawner(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		long now = level.getDefaultClockTime();
		Field nextTick = PhantomSpawner.class.getDeclaredField("nextTick");
		nextTick.setAccessible(true);
		EmptyNight.Snapshot before = EmptyNight.snapshot();
		try {
			EmptyNight.snapshot(new EmptyNight.Snapshot(Optional.of(new EmptyNight.Window(now - 100, now + 10_000)), List.of(), 96));
			helper.assertTrue(EmptyNight.holdsSpawner(level), "the overworld's spawners are not held in the window");
			PhantomSpawner held = new PhantomSpawner();
			nextTick.setInt(held, 0);
			held.tick(level, true);
			helper.assertTrue(nextTick.getInt(held) == 0, "the phantom spawner ran on the night");
			EmptyNight.snapshot(EmptyNight.Snapshot.NONE);
			helper.assertFalse(EmptyNight.holdsSpawner(level), "held without a night");
			PhantomSpawner free = new PhantomSpawner();
			nextTick.setInt(free, 0);
			free.tick(level, true);
			boolean rule = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS);
			helper.assertTrue(!rule || nextTick.getInt(free) > 0, "the phantom spawner did not run without a night");
		} finally {
			EmptyNight.snapshot(before);
		}
		helper.succeed();
	}

	/** The quiet: animals within the radius of a player are silenced (for the time given), monsters and farther animals never. */
	@GameTest(structure = YARD)
	public void theNightQuietsAnimalsNearPlayersButNeverMonsters(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = at(helper, new Vec3(2.5, 1, 2.5), 0);
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 4));
		Mob sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 1, 3));
		Mob zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 5));
		Cow far = helper.spawn(EntityTypes.COW, new BlockPos(20, 1, 20));
		List<Mob> mobs = List.of(cow, sheep, zombie, far);
		try {
			helper.assertTrue(EmptyNight.silence(level, List.of(player), 8, 0) == 0, "silenced for no time");
			int silenced = EmptyNight.silence(level, List.of(player), 8, 100);
			MobTamperImpl tamper = MobTamperImpl.INSTANCE;
			helper.assertTrue(silenced == 2 && tamper.isSilenced(cow) && tamper.isSilenced(sheep) && EmptyNight.silencedByIt(cow), "the animals near: " + silenced);
			helper.assertTrue(!tamper.isTampered(zombie) && !tamper.isTampered(far), "a monster or a far animal silenced");
			helper.assertTrue(!tamper.isFrozen(cow) && !tamper.isFacing(cow), "the quiet froze or turned an animal");
			// Each renewal lasts 220 ticks, never past 23500, and none outside the window.
			EmptyNight.Window quiet = EmptyNight.quiet(3);
			helper.assertTrue(EmptyNight.silenceTicks(3 * DAY + 12_000, quiet) == 220 && EmptyNight.silenceTicks(3 * DAY + 23_400, quiet) == 100
					&& EmptyNight.silenceTicks(3 * DAY + 23_500, quiet) == 0 && EmptyNight.silenceTicks(3 * DAY + 11_999, quiet) == 0, "the quiet's length");
		} finally {
			mobs.forEach(m -> {
				MobTamperImpl.INSTANCE.release(m);
				m.discard();
			});
		}
		helper.succeed();
	}

	/** The memory reads back; a file from before (no key) or with a bad value loads with none; START is not written; the payload. */
	@GameTest
	public void theNightsMemoryAndPayloadReadBackAndOldFilesLoad(GameTestHelper helper) {
		EmptyNight.Memory memory = new EmptyNight.Memory(10, 10, 2, 10, Optional.of(new EmptyNight.Undo(10 * DAY + 10_000, 9, 3, 1, EmptyNight.NEVER)));
		Tag tag = EmptyNight.Memory.CODEC.encodeStart(NbtOps.INSTANCE, memory).getOrThrow();
		helper.assertTrue(EmptyNight.Memory.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(memory), "the memory did not read back");

		AtmosphereData fresh = new AtmosphereData();
		CompoundTag written = (CompoundTag) AtmosphereData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
		helper.assertTrue(!written.contains("sc04") && !written.contains("sc07"), "START was written: " + written);
		AtmosphereData old = AtmosphereData.CODEC.parse(NbtOps.INSTANCE, written).getOrThrow();
		helper.assertTrue(old.emptyNight().equals(EmptyNight.Memory.START) && old.sc07().equals(AtmosphereData.Sc07.START), "an old file did not load with none");
		CompoundTag bad = written.copy();
		bad.putBoolean("musicOffDone", true);
		bad.putString("sc04", "not a memory");
		bad.putString("sc07", "nor this");
		AtmosphereData lenient = AtmosphereData.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow();
		helper.assertTrue(lenient.musicOffDone() && lenient.emptyNight().equals(EmptyNight.Memory.START), "a bad value stopped the file");
		fresh.setEmptyNight(memory);
		fresh.recordSc07(12);
		CompoundTag full = (CompoundTag) AtmosphereData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
		AtmosphereData back = AtmosphereData.CODEC.parse(NbtOps.INSTANCE, full).getOrThrow();
		helper.assertTrue(back.emptyNight().equals(memory) && back.sc07().count() == 1 && back.sc07().lastDay() == 12, "the layer's memory did not read back");

		EmptyNightPayload payload = new EmptyNightPayload(Optional.of(EmptyNight.sky(10)));
		helper.assertTrue(roundTrip(EmptyNightPayload.CODEC, payload).equals(payload)
				&& roundTrip(EmptyNightPayload.CODEC, new EmptyNightPayload(Optional.empty())).window().isEmpty(), "the payload");
		helper.assertTrue(EmptyNightPayload.TYPE.id().toString().equals("a1016_02:atmosphere/sc04"), "the payload's id");
		helper.succeed();
	}

	/** The switch's revert drops the night set now (the count stays), clears the spawn rule's snapshot, and runs again harmlessly. */
	@GameTest
	public void theSwitchsRevertClearsTheNight(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		AtmosphereData data = AtmosphereData.get(server);
		EmptyNight.Memory before = data.emptyNight();
		EmptyNight.Snapshot snapshot = EmptyNight.snapshot();
		try {
			long now = helper.getLevel().getDefaultClockTime();
			long night = Math.floorDiv(now, DAY);
			data.setEmptyNight(new EmptyNight.Memory(night, night, 3, night, Optional.empty()));
			EmptyNight.snapshot(new EmptyNight.Snapshot(Optional.of(EmptyNight.quiet(night)), List.of(), 96));
			EmptyNight.revert(server);
			helper.assertTrue(data.emptyNight().night() == EmptyNight.NEVER && data.emptyNight().count() == 3 && EmptyNight.snapshot().quiet().isEmpty()
					&& EmptyNight.windowNow(server).isEmpty(), "the revert left the night: " + data.emptyNight());
			EmptyNight.revert(server);
			helper.assertTrue(data.emptyNight().night() == EmptyNight.NEVER && data.emptyNight().count() == 3, "the revert is not idempotent");
		} finally {
			data.setEmptyNight(before);
			EmptyNight.snapshot(snapshot);
		}
		helper.succeed();
	}

	// --- 07 ---

	/** The tamper takes a name only out of view and records it first; the ledger's undo gives it back, shown as before. */
	@GameTest(structure = YARD)
	public void unnameTakesANameOnlyOutOfViewAndTheUndoGivesItBack(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6));
		Cow plain = helper.spawn(EntityTypes.COW, new BlockPos(9, 1, 6));
		String cause = "test:sc1/unname";
		TraceLedger ledger = TraceLedger.get(level.getServer());
		try {
			cow.setCustomName(Component.literal("Daisy"));
			cow.setCustomNameVisible(true);
			helper.assertFalse(MobTamperImpl.INSTANCE.unname(cow, cause, (l, box) -> false), "a name taken in view");
			helper.assertTrue(cow.hasCustomName(), "the name went in view");
			helper.assertFalse(MobTamperImpl.INSTANCE.unname(plain, cause, (l, box) -> true), "a mob without a name was unnamed");
			int size = ledger.entries().size();
			helper.assertTrue(MobTamperImpl.INSTANCE.unname(cow, cause, (l, box) -> true), "the name was not taken out of view");
			helper.assertTrue(!cow.hasCustomName() && !cow.isCustomNameVisible() && !MobTamperImpl.INSTANCE.isTampered(cow), "the name is still there, or it is held");
			TraceLedger.Entry entry = ledger.entries().getLast();
			helper.assertTrue(ledger.entries().size() == size + 1 && entry.kind() == TraceLedger.Kind.NAME && entry.cause().equals(cause)
					&& entry.entity().equals(Optional.of(cow.getUUID())) && TraceLedger.storedName(level, entry).map(Component::getString).orElse("").equals("Daisy"),
					"not ledgered as its name: " + entry);
			helper.assertTrue(Services.traces().forced().undo(level, entry) == TraceService.UndoResult.DONE && cow.hasCustomName()
					&& cow.getCustomName().getString().equals("Daisy") && cow.isCustomNameVisible() && !ledger.entries().contains(entry), "the undo did not give it back");
		} finally {
			ledger.entries().stream().filter(e -> e.cause().equals(cause)).toList().forEach(ledger::remove);
			cow.discard();
			plain.discard();
		}
		helper.succeed();
	}

	/** The card: the player's pet first, then the mob nearest their base; never seen, never with someone near, the owner near, or the player near and awake. */
	@GameTest(structure = YARD)
	public void theNameIsGonePrefersThePlayersPetAndRefusesNearOrSeen(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(12, 1, 12));
		ServerPlayer target = at(helper, new Vec3(12.5, 1, 90.5), 0);
		ServerPlayer friend = at(helper, new Vec3(-60.5, 1, 12.5), 0);
		home(target, base);
		Watched online = new Watched(target, friend);
		Wolf pet = helper.spawn(EntityTypes.WOLF, new BlockPos(20, 1, 20));
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(13, 1, 13));
		Wolf friends = helper.spawn(EntityTypes.WOLF, new BlockPos(3, 1, 20));
		Cow rider = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 3));
		Cow ridden = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 3));
		List<Mob> mobs = List.of(pet, cow, friends, rider, ridden);
		TraceLedger ledger = TraceLedger.get(level.getServer());
		try {
			pet.setTame(true, false);
			pet.setOwnerReference(EntityReference.of(target.getUUID()));
			friends.setTame(true, false);
			friends.setOwnerReference(EntityReference.of(friend.getUUID()));
			for (Mob mob : mobs) {
				mob.setCustomName(Component.literal("n" + mob.getId()));
			}
			rider.startRiding(ridden, true, false);
			NameGoneCard card = new NameGoneCard();
			AtmosphereData data = new AtmosphereData();
			long[] day = {10};
			card.watch = () -> online.watch;
			card.everyone = l -> List.copyOf(online.online);
			card.outOfView = (l, box) -> true;
			card.data = s -> data;
			card.day = s -> day[0];
			card.bases = (s, d) -> List.of(base);
			List<NameGoneCard.Candidate> candidates = card.candidates(target, level);
			helper.assertTrue(candidates.size() == 5 && candidates.getFirst().mob() == pet && candidates.getFirst().owned(), "not the player's pet first: " + candidates);
			helper.assertTrue(candidates.get(1).mob() == cow, "not the one nearest the base next: " + candidates);
			helper.assertTrue(why(candidates, rider).contains("riding") && why(candidates, ridden).contains("riding"), "a rider or ridden mob may lose its name");
			helper.assertTrue(card.contextFits(target, level), "the card does not fit");

			// The owner online near their own pet: never (the friend is 63 from it; at 30 they count).
			move(helper, friend, new Vec3(3.5, 1, 50.5), 0);
			helper.assertTrue(why(card.candidates(target, level), friends).contains("owner"), "a pet taken with its owner near");
			move(helper, friend, new Vec3(3.5, 1, 30.5), 0);
			helper.assertTrue(why(card.candidates(target, level), friends).contains("within 16"), "someone within 16");
			move(helper, friend, new Vec3(-60.5, 1, 12.5), 0);
			// The player near and awake: no; near and asleep: yes, but not their own pet (its owner is near).
			move(helper, target, new Vec3(12.5, 1, 40.5), 0);
			List<NameGoneCard.Candidate> near = card.candidates(target, level);
			helper.assertTrue(why(near, cow).contains("awake") && why(near, pet).contains("awake"), "taken with the player near");
			target.setSleepingPos(helper.absolutePos(new BlockPos(12, 1, 40)));
			List<NameGoneCard.Candidate> asleep = card.candidates(target, level);
			helper.assertTrue(why(asleep, cow).equals("may") && why(asleep, pet).contains("owner"), "asleep: " + asleep);
			target.clearSleepingPos();
			move(helper, target, new Vec3(12.5, 1, 90.5), 0);
			card.outOfView = (l, box) -> !box.intersects(cow.getBoundingBox());
			helper.assertTrue(why(card.candidates(target, level), cow).contains("in view"), "a mob seen lost its name");
			card.outOfView = (l, box) -> true;
			MobTamperImpl.INSTANCE.freeze(cow, 100);
			helper.assertTrue(why(card.candidates(target, level), cow).contains("held"), "a held mob lost its name");
			MobTamperImpl.INSTANCE.release(cow);

			// The fire takes the pet's, ledgered, and counts in the world's record; then the caps hold for a fire that is not forced.
			helper.assertTrue(card.fire(new FireContext(target, level, false, false, RandomSource.create(1))) == FireResult.FIRED && !pet.hasCustomName()
					&& cow.hasCustomName(), "the fire did not take the pet's name");
			helper.assertTrue(data.sc07().count() == 1 && data.sc07().lastDay() == 10, "not counted: " + data.sc07());
			helper.assertTrue(ledger.entries().stream().anyMatch(e -> e.kind() == TraceLedger.Kind.NAME && e.cause().equals(NameGoneCard.CAUSE)
					&& e.entity().equals(Optional.of(pet.getUUID()))), "not ledgered");
			helper.assertTrue(card.fire(new FireContext(target, level, false, false, RandomSource.create(1))) == FireResult.SKIPPED && cow.hasCustomName()
					&& !card.contextFits(target, level), "a second the same day");
			day[0] = 13;
			helper.assertTrue(card.contextFits(target, level), "three days later it does not fit");
			helper.assertTrue(card.fire(new FireContext(target, level, false, true, RandomSource.create(1))) == FireResult.FIRED && !cow.hasCustomName(),
					"a forced fire did not take the next one");
		} finally {
			for (Mob mob : mobs) {
				ledger.entries().stream().filter(e -> e.entity().equals(Optional.of(mob.getUUID()))).toList().forEach(ledger::remove);
				mob.stopRiding();
				mob.discard();
			}
		}
		helper.succeed();
	}

	/** The world's caps: three a world, three in-game days apart. */
	@GameTest
	public void theNameIsGoneKeepsTheWorldsCapsAndSpacing(GameTestHelper helper) {
		Sc07Config cfg = new Sc07Config();
		helper.assertTrue(NameGoneCard.caps(AtmosphereData.Sc07.START, 0, cfg).isEmpty(), "a world without one");
		helper.assertTrue(NameGoneCard.caps(new AtmosphereData.Sc07(3, 0), 40, cfg).isPresent(), "a fourth");
		helper.assertTrue(NameGoneCard.caps(new AtmosphereData.Sc07(1, 9), 11, cfg).isPresent() && NameGoneCard.caps(new AtmosphereData.Sc07(1, 9), 12, cfg).isEmpty(),
				"three days apart");
		helper.succeed();
	}

	private static String why(List<NameGoneCard.Candidate> candidates, Mob mob) {
		return candidates.stream().filter(c -> c.mob() == mob).findFirst().map(c -> c.refusal().orElse("may")).orElse("missing");
	}

	// --- fog places on the client ---

	/** A fog place thickens the client's weather fog only inside its radius and time, more toward its centre, with the spell, under the sky. */
	@GameTest
	public void aFogPlaceThickensTheClientsWeatherFogOnlyInsideItsRadiusAndTime(GameTestHelper helper) {
		Vec3 center = new Vec3(100.5, 64, 100.5);
		FogPlace place = new FogPlace(Level.OVERWORLD, center, 40, 0.45F, 1_000, 60_000, 1_200, 1_200);
		WeatherFog client = new WeatherFog();
		helper.assertTrue(client.placeAmount(Level.OVERWORLD, center, 20_000, false) == 0.0, "a place before any came");
		client.receive(new ClientEffects.FogPlaces(List.of(place)));
		long clock = 20_000;
		helper.assertTrue(Math.abs(client.placeAmount(Level.OVERWORLD, center, clock, false) - place.level()) < 1.0E-12, "the centre at full");
		double last = -1.0;
		for (int d = 40; d >= 0; d -= 5) {
			double a = client.placeAmount(Level.OVERWORLD, center.add(d, 0, 0), clock, false);
			helper.assertTrue(a > last || d == 40 && a == 0.0, "not thicker toward the centre at " + d + ": " + a);
			last = a;
		}
		helper.assertTrue(client.placeAmount(Level.OVERWORLD, center.add(41, 0, 0), clock, false) == 0.0
				&& client.placeAmount(Level.OVERWORLD, center, 500, false) == 0.0 && client.placeAmount(Level.OVERWORLD, center, 60_000, false) == 0.0
				&& client.placeAmount(Level.NETHER, center, clock, false) == 0.0 && client.placeAmount(Level.OVERWORLD, center, clock, true) == 0.0,
				"a place outside its radius, time or dimension, or where time is fixed");
		// The frame: with no place where the camera is, exactly the spell's alone; inside, the spell's and the place's together.
		FogSpell spell = new FogSpell(10_000, 40_000, 0.5F, false);
		client.receive(new ClientEffects.FogSpell(Optional.of(spell)));
		WeatherFog alone = new WeatherFog();
		alone.receive(new ClientEffects.FogSpell(Optional.of(spell)));
		helper.assertTrue(client.frame(clock, false, 15, 0.0, Level.OVERWORLD, center.add(80, 0, 0)) == alone.frame(clock, false, 15, 0.0),
				"a place far away changed the spell's fog");
		client.resetSky();
		double both = client.frame(clock, false, 15, 0.0, Level.OVERWORLD, center);
		helper.assertTrue(Math.abs(both - FogLimits.combine(spell.amount(clock), place.level())) < 1.0E-12 && both > spell.amount(clock) && both > place.level(),
				"not combined with the spell: " + both);
		// No pop at the edge: just inside the radius the frame is the spell's alone, within a hair.
		WeatherFog edge = new WeatherFog();
		edge.receive(new ClientEffects.FogSpell(Optional.of(spell)));
		edge.receive(new ClientEffects.FogPlaces(List.of(place)));
		helper.assertTrue(Math.abs(edge.frame(clock, false, 15, 0.0, Level.OVERWORLD, center.add(39.999, 0, 0)) - alone.frame(clock, false, 15, 0.0)) < 1.0E-6,
				"a pop at the place's edge");
		WeatherFog tunnel = new WeatherFog();
		tunnel.receive(new ClientEffects.FogPlaces(List.of(place)));
		helper.assertTrue(tunnel.frame(clock, false, FogLimits.SKY_NONE, 0.0, Level.OVERWORLD, center) == 0.0, "a place's fog in a tunnel");
		// Sync carries the places; a disconnect drops them.
		WeatherFog joined = new WeatherFog();
		joined.receive(new ClientEffects.Sync(false, 0.3F, Optional.empty(), List.of(place)));
		helper.assertTrue(joined.places().equals(List.of(place)), "the sync's places");
		joined.reset();
		helper.assertTrue(joined.places().isEmpty() && joined.placeAmount(Level.OVERWORLD, center, clock, false) == 0.0, "a disconnect kept the places");
		helper.succeed();
	}

	/** The server's fog end and the client's drawn fog end agree with a fog place (and a spell), at every stage and view distance. */
	@GameTest
	public void serverAndClientFogAgreeWithAFogPlace(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		FogLimits.Shape shape = cfg.fogShape();
		helper.assertTrue(FogLimits.shape().equals(shape), "FogLimits does not use atmosphere's live config");
		Vec3 center = new Vec3(0.5, 70, 0.5);
		FogPlace place = new FogPlace(Level.OVERWORLD, center, 40, 0.45F, 12_000, 60_000, 1_200, 1_200);
		FogSpell spell = new FogSpell(30_000, 50_000, 0.55F, false);
		for (Vec3 at : List.of(center, center.add(12, 0, 5), center.add(30, 0, 0), center.add(39.9, 0, 0), center.add(60, 0, 0))) {
			for (long clock = 11_000; clock <= 61_000; clock += 500) {
				WeatherFog client = new WeatherFog();
				client.receive(new ClientEffects.FogSpell(Optional.of(spell)));
				client.receive(new ClientEffects.FogPlaces(List.of(place)));
				double amount = client.frame(clock, false, 15, 0.0, Level.OVERWORLD, at);
				double placeAmount = FogLimits.placeAmount(List.of(place), Level.OVERWORLD, at, clock);
				for (Stage stage : Stage.values()) {
					float dusk = cfg.duskFogFor(stage);
					for (int chunks : new int[] {2, 8, 12, 32}) {
						FogLimits.Result server = FogLimits.of(chunks, 32, dusk, clock, shape, FogLimits.weatherAmount(spell.amount(clock), placeAmount, 1.0));
						Curves.Fog fog = vanilla(server.renderLimit());
						WeatherFog.shape(fog, Curves.duskAmount(dusk, clock, shape), Curves.duskHaze(clock, shape), amount, 0.0, cfg);
						helper.assertTrue(Math.abs(server.fogEnd() - fog.end()) < 1.0E-9, String.format(Locale.ROOT, "server %.4f != client %.4f at %s, clock %d, %s, %d chunks",
								server.fogEnd(), fog.end(), at, clock, stage, chunks));
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * A fog place is seen from outside it (R8 L5): from 100 blocks off, looking at it, the far fog stands over it (from
	 * just past its middle, complete a little past its far side) and hides what is behind it; nothing nearer than where
	 * the view enters it gets more fog; looking past it or away, nothing; under no sky, where time is fixed or in another
	 * dimension, nothing; the night's fog color follows it only while it is in view.
	 */
	@GameTest
	public void aFogPlaceIsSeenFromOutsideItAsAFogStandingOverIt(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		Vec3 center = new Vec3(0.5, 70, 0.5);
		FogPlace place = new FogPlace(Level.OVERWORLD, center, 40, 0.45F, 0, 10 * DAY, 1_200, 1_200);
		long noon = 2 * DAY + 6_000;
		Vec3 house = center.add(0, 0, -100);
		WeatherFog client = new WeatherFog();
		client.receive(new ClientEffects.FogPlaces(List.of(place)));
		double weather = client.frame(noon, false, 15, 0.0, Level.OVERWORLD, house);
		helper.assertTrue(weather == 0.0, "the house is not outside the place: " + weather);
		// Yaw 0 looks toward +z, at the place's centre.
		List<WeatherFog.Seen> toward = client.seen(Level.OVERWORLD, house, 0.0, noon, false);
		helper.assertTrue(toward.size() == 1 && Math.abs(toward.get(0).near() - 60.0) < 1.0E-9 && Math.abs(toward.get(0).weather() - 0.45) < 1.0E-6,
				"seen from the house: " + toward);
		double r = 256.0;
		Curves.Fog eye = vanilla(r);
		helper.assertTrue(!WeatherFog.shape(eye, 0.0, 0.0, weather, 0.0, cfg), "a clear noon outside the place changed the fog");
		Curves.Fog seen = vanilla(r);
		helper.assertTrue(WeatherFog.shape(seen, 0.0, 0.0, weather, 0.0, toward, new Curves.Fog(), cfg), "the seen place changed nothing");
		helper.assertTrue(seen.end() > 100.0 && seen.end() < 100.0 + 40.0 + 24.0 && fogAt(seen, 140.0) >= 0.5 && fogAt(seen, 170.0) == 1.0
				&& fogAt(seen, 100.0) < 0.15, String.format(Locale.ROOT, "not a fog standing over it: ends %.1f, %.2f at its far side, %.2f at its middle",
						seen.end(), fogAt(seen, 140.0), fogAt(seen, 100.0)));
		for (double d = 0.0; d <= 60.0; d += 0.5) {
			helper.assertTrue(fogAt(seen, d) == fogAt(eye, d), "more fog nearer than the place at " + d);
		}
		// Within the cone's 20 degrees it is whole; past it, it thins out to nothing by the place's edge.
		helper.assertTrue(client.seen(Level.OVERWORLD, house, 15.0, noon, false).equals(toward) && client.seen(Level.OVERWORLD, house, -15.0, noon, false).equals(toward),
				"not whole within the cone");
		List<WeatherFog.Seen> aside = client.seen(Level.OVERWORLD, house, 32.0, noon, false);
		helper.assertTrue(aside.size() == 1 && aside.get(0).weather() > 0.0 && aside.get(0).weather() < 0.45 && aside.get(0).near() > 60.0, "half aside: " + aside);
		for (double yaw : new double[] {45.0, -45.0, 90.0, 180.0, -135.0}) {
			helper.assertTrue(client.seen(Level.OVERWORLD, house, yaw, noon, false).isEmpty(), "seen looking past it or away at yaw " + yaw);
		}
		// Inside it, looking away from its centre: exactly the fog at the camera, as before.
		Vec3 inside = center.add(0, 0, -20);
		WeatherFog in = new WeatherFog();
		in.receive(new ClientEffects.FogPlaces(List.of(place)));
		double here = in.frame(noon, false, 15, 0.0, Level.OVERWORLD, inside);
		helper.assertTrue(here > 0.0 && in.seen(Level.OVERWORLD, inside, 180.0, noon, false).isEmpty(), "inside, looking away");
		List<WeatherFog.Seen> inward = in.seen(Level.OVERWORLD, inside, 0.0, noon, false);
		helper.assertTrue(inward.size() == 1 && inward.get(0).near() == 0.0 && Math.abs(inward.get(0).weather() - 0.45) < 1.0E-6 && inward.get(0).weather() > here,
				"inside, toward its centre: " + inward);
		// No sky, fixed time, another dimension: nothing seen.
		WeatherFog tunnel = new WeatherFog();
		tunnel.receive(new ClientEffects.FogPlaces(List.of(place)));
		tunnel.frame(noon, false, FogLimits.SKY_NONE, 0.0, Level.OVERWORLD, house);
		helper.assertTrue(tunnel.seen(Level.OVERWORLD, house, 0.0, noon, false).isEmpty() && client.seen(Level.OVERWORLD, house, 0.0, noon, true).isEmpty()
				&& client.seen(Level.NETHER, house, 0.0, noon, false).isEmpty()
				&& FogLimits.placeViews(List.of(place), Level.OVERWORLD, house, 0.0, 11 * DAY).isEmpty(), "seen under no sky, fixed time, elsewhere or after");
		// The color: at night the fog over it pales the frame's fog toward the night fog's color, only while it is in view.
		long night = 2 * DAY + 18_000;
		double cameraEnd = FogLimits.fogEnd(r, cfg.duskMinFogBlocks, 0.0);
		double tint = WeatherFog.tintAmount(0.0, toward, cameraEnd);
		helper.assertTrue(tint > 0.0 && tint < 0.45 && WeatherFog.colorWeight(tint, Math.floorMod(night, DAY), cfg.fogNightColorLift) > 0.0
				&& WeatherFog.tintAmount(0.0, List.of(), cameraEnd) == 0.0 && WeatherFog.tintAmount(0.0, List.of(new WeatherFog.Seen(cameraEnd, 0.45)), cameraEnd) == 0.0
				&& WeatherFog.tintAmount(0.3, List.of(), cameraEnd) == 0.3, "the color: " + tint);
		helper.succeed();
	}

	/**
	 * The server's fog end follows a fog place seen along the player's yaw exactly as the client draws it, at every
	 * stage, view distance, place time and yaw, with a spell; the seen fog has no pop: continuous over the yaw and
	 * across the radius, and never thinner as one walks toward its centre.
	 */
	@GameTest
	public void theSeenFogPlaceIsTheServersFogEndAndHasNoPop(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		FogLimits.Shape shape = cfg.fogShape();
		Vec3 center = new Vec3(0.5, 70, 0.5);
		FogPlace place = new FogPlace(Level.OVERWORLD, center, 40, 0.45F, 12_000, 60_000, 1_200, 1_200);
		FogSpell spell = new FogSpell(30_000, 50_000, 0.55F, false);
		Curves.Fog scratch = new Curves.Fog();
		int seenAny = 0;
		for (Vec3 at : List.of(center.add(0, 0, -100), center.add(70, 0, 30), center.add(45, 0, 0), center.add(20, 0, -10), center, center.add(-150, 0, 90))) {
			for (double yaw = -180.0; yaw < 180.0; yaw += 15.0) {
				for (long clock = 11_000; clock <= 61_000; clock += 2_500) {
					WeatherFog client = new WeatherFog();
					client.receive(new ClientEffects.FogSpell(Optional.of(spell)));
					client.receive(new ClientEffects.FogPlaces(List.of(place)));
					double weather = client.frame(clock, false, 15, 0.0, Level.OVERWORLD, at);
					List<WeatherFog.Seen> seen = client.seen(Level.OVERWORLD, at, yaw, clock, false);
					double placeAmount = FogLimits.placeAmount(List.of(place), Level.OVERWORLD, at, clock);
					List<FogLimits.PlaceView> views = FogLimits.placeViews(List.of(place), Level.OVERWORLD, at, yaw, clock);
					helper.assertTrue(views.size() == seen.size(), "the client sees other places than the server");
					seenAny += seen.size();
					for (Stage stage : Stage.values()) {
						float dusk = cfg.duskFogFor(stage);
						for (int chunks : new int[] {2, 8, 16, 32}) {
							FogLimits.Result eye = FogLimits.of(chunks, 32, dusk, clock, shape, FogLimits.weatherAmount(spell.amount(clock), placeAmount, 1.0));
							FogLimits.Result server = FogLimits.seen(eye, views, chunks, 32, dusk, clock, shape, spell.amount(clock), 1.0);
							Curves.Fog fog = vanilla(server.renderLimit());
							WeatherFog.shape(fog, Curves.duskAmount(dusk, clock, shape), Curves.duskHaze(clock, shape), weather, 0.0, seen, scratch, cfg);
							helper.assertTrue(Math.abs(server.fogEnd() - fog.end()) < 1.0E-9 && server.fogEnd() <= eye.fogEnd(), String.format(Locale.ROOT,
									"server %.4f != client %.4f at %s, yaw %.0f, clock %d, %s, %d chunks", server.fogEnd(), fog.end(), at, yaw, clock, stage, chunks));
						}
					}
				}
			}
		}
		helper.assertTrue(seenAny > 100, "hardly ever seen: " + seenAny);
		// No pop: over the yaw at the house, from outside into the place toward its centre (never thinner), across the radius.
		long clock = 40_000;
		double last = Double.NaN;
		for (double yaw = -180.0; yaw <= 180.0; yaw += 0.1) {
			double end = seenEnd(place, center.add(0, 0, -100), yaw, clock, cfg);
			helper.assertTrue(Double.isNaN(last) || Math.abs(end - last) < 2.0, String.format(Locale.ROOT, "a pop at yaw %.1f: %.2f to %.2f", yaw, last, end));
			last = end;
		}
		last = Double.NaN;
		for (double d = 200.0; d >= 0.0; d -= 0.25) {
			double end = seenEnd(place, center.add(0, 0, -d), 0.0, clock, cfg);
			helper.assertTrue(Double.isNaN(last) || end <= last + 1.0E-9 && last - end <= 0.25 + 1.0E-9,
					String.format(Locale.ROOT, "walking in, %.2f to %.2f at %.2f", last, end, d));
			last = end;
		}
		helper.assertTrue(Math.abs(seenEnd(place, center.add(0, 0, -40.01), 0.0, clock, cfg) - seenEnd(place, center.add(0, 0, -39.99), 0.0, clock, cfg)) < 0.05,
				"a pop at the radius");
		helper.succeed();
	}

	/** The client's visible fog end at 16 chunks, noon, no spell: the camera's own and the place seen along {@code yaw}. */
	private static double seenEnd(FogPlace place, Vec3 at, double yaw, long clock, AtmosphereConfig cfg) {
		WeatherFog client = new WeatherFog();
		client.receive(new ClientEffects.FogPlaces(List.of(place)));
		long noon = Math.floorDiv(clock, DAY) * DAY + 6_000;
		double weather = client.frame(noon, false, 15, 0.0, Level.OVERWORLD, at);
		Curves.Fog fog = vanilla(256.0);
		WeatherFog.shape(fog, 0.0, 0.0, weather, 0.0, client.seen(Level.OVERWORLD, at, yaw, noon, false), new Curves.Fog(), cfg);
		return fog.end();
	}

	/** The shader's world fog at this distance: the larger of the environmental and the render-distance fog. */
	static double fogAt(Curves.Fog fog, double d) {
		return Math.max(linear(d, fog.environmentalStart, fog.environmentalEnd), linear(d, fog.renderDistanceStart, fog.renderDistanceEnd));
	}

	private static double linear(double d, double start, double end) {
		if (d <= start) {
			return 0.0;
		}
		return d >= end ? 1.0 : (d - start) / (end - start);
	}

	/**
	 * 04's sky under a shaderpack (R8 L6): a pack lights and draws the night from the game's own moon phase, so while
	 * one is in use the sky keeps vanilla's moon inside the window too; without one, the new moon. Iris is not loaded
	 * here, so no pack is in use.
	 */
	@GameTest
	public void theSkyKeepsVanillasMoonWhileAShaderpackDrawsTheNight(GameTestHelper helper) {
		Optional<EmptyNight.Window> w = Optional.of(EmptyNight.sky(5));
		helper.assertTrue(EmptyNight.moonFor(5 * DAY + 18_000, w, MoonPhase.FULL_MOON, false) == MoonPhase.NEW_MOON
				&& EmptyNight.moonFor(5 * DAY + 18_000, w, MoonPhase.FULL_MOON, true) == MoonPhase.FULL_MOON
				&& EmptyNight.moonFor(5 * DAY + 11_000, w, MoonPhase.WAXING_GIBBOUS, true) == MoonPhase.WAXING_GIBBOUS
				&& EmptyNight.moonFor(5 * DAY + 10_000, w, MoonPhase.FULL_MOON, true) == MoonPhase.FULL_MOON
				&& EmptyNight.moonFor(5 * DAY + 10_000, w, MoonPhase.FULL_MOON, false) == MoonPhase.FULL_MOON, "the moon under a shaderpack");
		helper.assertTrue(!ShaderPacks.inUse(), "a shaderpack in use without Iris");
		helper.succeed();
	}

	/** Vanilla's overworld world fog for a render limit (DuskFogGameTests' model). */
	static Curves.Fog vanilla(double r) {
		double span = Math.clamp(r / 10.0, 4.0, 64.0);
		return new Curves.Fog().set(0.0, 1024.0, r - span, r, Math.min(r, 512.0), 2048.0);
	}
}
