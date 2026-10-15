package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Fog weather (D-071): {@link FogSpell} and its codecs, the spell in {@link HerobrineState.Effects} and
 * {@link ClientEffects}, {@link FogWeatherRules}' rolls and hand-over, and {@link FogLimits} with a spell. Every roll
 * runs on the defaults ({@link FogWeatherRules.Settings#DEFAULT}), never on whatever atmosphere installed.
 * {@link MultiplayerCoreTests} extends this.
 */
public class FogWeatherTests extends ConfigVersionTests {
	private static final FogWeatherRules.Settings D = FogWeatherRules.Settings.DEFAULT;
	private static final long DAY = FogWeatherRules.DAY;

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			T decoded = codec.decode(buf);
			if (buf.readableBytes() != 0) {
				throw new IllegalStateException(buf.readableBytes() + " bytes left after decoding " + value);
			}
			return decoded;
		} finally {
			buf.release();
		}
	}

	@GameTest
	public void fogSpellCodecsRoundTrip(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<FogSpell> spells = List.of(new FogSpell(11_500L, 23_500L, 0.45F, false), new FogSpell(5 * DAY - 1000, 5 * DAY + 11_500, 0.65F, true),
				new FogSpell(0L, 0L, 0.0F, false), new FogSpell(-1000L, 3_000_000_000L, 1.0F, true));
		for (FogSpell spell : spells) {
			Tag saved = FogSpell.CODEC.encodeStart(ops, spell).getOrThrow();
			helper.assertTrue(FogSpell.CODEC.parse(ops, saved).getOrThrow().equals(spell), "codec " + spell + " -> " + saved);
			helper.assertTrue(roundTrip(FogSpell.STREAM_CODEC, spell).equals(spell), "stream codec " + spell);
			helper.assertTrue(roundTrip(ClientEffects.FogSpell.CODEC, new ClientEffects.FogSpell(Optional.of(spell))).spell().equals(Optional.of(spell)),
					"payload " + spell);
			ClientEffects.Sync sync = new ClientEffects.Sync(true, 0.3F, Optional.of(spell));
			helper.assertTrue(roundTrip(ClientEffects.Sync.CODEC, sync).equals(sync), "sync " + sync);
		}
		helper.assertTrue(roundTrip(ClientEffects.FogSpell.CODEC, new ClientEffects.FogSpell(Optional.empty())).spell().isEmpty(), "payload none");
		ClientEffects.Sync clear = new ClientEffects.Sync(false, 0.15F);
		helper.assertTrue(clear.fogSpell().isEmpty() && roundTrip(ClientEffects.Sync.CODEC, clear).equals(clear), "sync without a spell");

		CompoundTag noQuiet = new CompoundTag();
		noQuiet.putLong("from", 100L);
		noQuiet.putLong("until", 50L);
		noQuiet.putFloat("level", 3.0F);
		FogSpell clamped = FogSpell.CODEC.parse(ops, noQuiet).getOrThrow();
		helper.assertTrue(clamped.equals(new FogSpell(100L, 100L, 1.0F, false)), "quiet defaults to false, until never before from, level 0..1: " + clamped);
		helper.assertTrue(new FogSpell(0L, 10L, Float.NaN, false).level() == 0.0F, "NaN level");
		helper.succeed();
	}

	/**
	 * FIX-CORE (R2#5): a spell carries the ease it was made with. Made with a server's numbers it keeps them through the
	 * save codec, the stream codec and the payload; its amount is its own ease whatever settings a caller passes (so a
	 * client on other numbers draws the server's fog); a spell saved without them (v1.2.0) takes the installed settings
	 * when it loads; the rolls and the hand-over stamp the settings they run on.
	 */
	@GameTest
	public void aFogSpellCarriesItsOwnEase(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		FogWeatherRules.Settings server = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), 5_000L, 800L, 2.0);
		FogSpell spell = new FogSpell(DAY + 11_500, DAY + 23_500, 0.7F, false, server);
		helper.assertTrue(spell.rampTicks() == 5_000L && spell.fadeTicks() == 800L && spell.exponent() == 2.0, "the settings' ease " + spell);
		FogSpell installed = new FogSpell(DAY + 11_500, DAY + 23_500, 0.7F, false);
		FogWeatherRules.Settings now = FogWeatherRules.settings();
		helper.assertTrue(installed.rampTicks() == now.rampTicks() && installed.fadeTicks() == now.fadeTicks() && installed.exponent() == now.easeExponent(),
				"the four-number spell does not take the installed ease " + installed);
		Tag saved = FogSpell.CODEC.encodeStart(ops, spell).getOrThrow();
		helper.assertTrue(FogSpell.CODEC.parse(ops, saved).getOrThrow().equals(spell) && roundTrip(FogSpell.STREAM_CODEC, spell).equals(spell)
				&& roundTrip(ClientEffects.FogSpell.CODEC, new ClientEffects.FogSpell(Optional.of(spell))).spell().equals(Optional.of(spell)),
				"a codec lost the ease: " + saved);
		CompoundTag old = ((CompoundTag) saved).copy();
		old.remove("ramp");
		old.remove("fade");
		old.remove("exponent");
		FogSpell loaded = FogSpell.CODEC.parse(ops, old).getOrThrow();
		helper.assertTrue(loaded.equals(new FogSpell(spell.fromClock(), spell.untilClock(), spell.level(), spell.quiet(), now)),
				"a v1.2.0 spell did not take the installed ease at load: " + loaded);
		for (long t = spell.fromClock() - 100; t <= spell.untilClock() + 100; t += 37) {
			double own = spell.amount(t, 5_000, 800, 2.0);
			helper.assertTrue(spell.amount(t) == own && spell.amount(t, D) == own && FogLimits.spellAmount(Optional.of(spell), t, D) == own
					&& FogLimits.spellAmount(Optional.of(spell), t) == own, "not the spell's own ease at " + t);
		}
		helper.assertTrue(FogLimits.of(12, 12, 0.3F, DAY + 12_000, Optional.of(spell), FogLimits.Shape.DEFAULT, D)
				.equals(FogLimits.of(12, 12, 0.3F, DAY + 12_000, Optional.of(spell), FogLimits.Shape.DEFAULT, server)), "FogLimits read the settings");

		FogWeatherRules.History history = new FogWeatherRules.History(9, 4, false, false);
		FogSpell night = FogWeatherRules.night(RandomSource.create(1), 9, Stage.PROXIMITY, history, server).orElseThrow();
		FogSpell day = FogWeatherRules.day(RandomSource.create(1), 9, Stage.REMOVAL, history, Optional.empty(), alwaysDays(server)).orElseThrow();
		FogSpell joined = FogWeatherRules.handOver(Optional.of(day), night, 9 * DAY + 10_000, server);
		for (FogSpell s : List.of(night, day, joined)) {
			helper.assertTrue(s.rampTicks() == 5_000L && s.fadeTicks() == 800L && s.exponent() == 2.0, "a rolled spell without the settings' ease " + s);
		}
		FogSpell clamped = new FogSpell(0L, 10L, 0.5F, false, -5L, -5L, Double.NaN);
		helper.assertTrue(clamped.rampTicks() == 0L && clamped.fadeTicks() == 0L && clamped.exponent() == 3.0
				&& new FogSpell(0L, 10L, 0.5F, false, 1L, 1L, 20.0).exponent() == 8.0, "the ease is not clamped: " + clamped);
		helper.assertTrue(spell.withClocks(5L, 9L).equals(new FogSpell(5L, 9L, 0.7F, false, server)), "withClocks");
		helper.succeed();
	}

	/** {@code settings} with a fog day every day not already covered. */
	private static FogWeatherRules.Settings alwaysDays(FogWeatherRules.Settings settings) {
		return new FogWeatherRules.Settings(settings.nightChanceByStage(), settings.secondNightChance(), settings.clearNightsBeforeFog(),
				settings.morningCarryChance(), FogWeatherRules.perStage(1.0), settings.nightLevelByStage(), settings.dayLevelByStage(),
				settings.quietShare(), settings.rampTicks(), settings.fadeTicks(), settings.easeExponent(), settings.fogGateShare());
	}

	/**
	 * FIX-CORE (R2#1): {@link FogSpell#counts} is the one "is there fog now" rule: from half the spell's level
	 * ({@code fogGateShare}, 0.5) on the way in to half on the way out, never outside, never for a spell of level 0, and
	 * the share is the settings' (installed like the rest, clamped, NaN is the default).
	 */
	@GameTest
	public void aFogSpellCountsFromHalfItsLevel(GameTestHelper helper) {
		FogSpell spell = new FogSpell(DAY + 11_500, DAY + 23_500, 0.6F, false, D);
		helper.assertTrue(D.fogGateShare() == 0.5 && FogWeatherRules.DEFAULT_GATE_SHARE == 0.5, "the default gate share");
		helper.assertTrue(!spell.counts(DAY + 11_499) && !spell.counts(DAY + 11_500) && !spell.counts(DAY + 11_600) && !spell.counts(DAY + 23_500)
				&& !spell.counts(DAY + 23_400), "a spell's edges count as fog");
		helper.assertTrue(spell.counts(DAY + 14_000) && spell.counts(DAY + 20_000), "a spell at full does not count");
		long firstIn = -1;
		long lastOut = -1;
		for (long t = spell.fromClock(); t < spell.untilClock(); t++) {
			boolean counts = spell.counts(t);
			helper.assertTrue(counts == spell.counts(t, 0.5) && counts == spell.amount(t) >= 0.5 * spell.level(), "counts at " + t);
			if (counts && firstIn < 0) {
				firstIn = t;
			}
			if (counts) {
				lastOut = t;
			}
		}
		helper.assertTrue(firstIn > spell.fromClock() + 1000 && firstIn < spell.fromClock() + D.rampTicks() && lastOut > spell.untilClock() - D.fadeTicks()
				&& lastOut < spell.untilClock() - 500, "the gate opens at " + firstIn + " and closes at " + lastOut);
		helper.assertTrue(spell.counts(DAY + 11_501, 0.0) && !spell.counts(DAY + 11_500, 0.0) && spell.counts(DAY + 14_000, 1.5)
				&& !spell.counts(DAY + 12_000, 1.5), "share 0 counts any fog, a share over 1 is 1");
		helper.assertTrue(!new FogSpell(DAY, 2 * DAY, 0.0F, false, D).counts(DAY + 6_000), "a spell of level 0 counts");
		FogWeatherRules.Settings strict = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), D.rampTicks(), D.fadeTicks(),
				D.easeExponent(), 7.0);
		FogWeatherRules.Settings nan = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), D.rampTicks(), D.fadeTicks(),
				D.easeExponent(), Double.NaN);
		helper.assertTrue(strict.fogGateShare() == 1.0 && nan.fogGateShare() == 0.5, "the gate share is not clamped: " + strict.fogGateShare());
		helper.succeed();
	}

	/**
	 * FIX-CORE (R2#2): the spell is weather, outside. {@link FogLimits#skyFactor} is 1 from sky light 10, 0 to 4,
	 * linear between; {@code FogLimits.of(player)} weights the spell by it at the player's eyes, so a player sealed in
	 * stone has no spell fog (the dusk fog stays).
	 */
	@GameTest(maxTicks = 200)
	public void aFogSpellStaysOutside(GameTestHelper helper) {
		helper.assertTrue(FogLimits.skyFactor(15) == 1.0 && FogLimits.skyFactor(10) == 1.0 && FogLimits.skyFactor(4) == 0.0 && FogLimits.skyFactor(0) == 0.0
				&& FogLimits.skyFactor(-3) == 0.0 && FogLimits.skyFactor(7) == 0.5 && Math.abs(FogLimits.skyFactor(9) - 5.0 / 6.0) < 1.0E-12,
				"sky factors");
		for (int light = 0; light < 15; light++) {
			helper.assertTrue(FogLimits.skyFactor(light) <= FogLimits.skyFactor(light + 1), "the sky factor falls at " + light);
		}
		ServerLevel level = helper.getLevel();
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 5; y++) {
				for (int z = 0; z <= 4; z++) {
					boolean inside = x >= 1 && x <= 3 && y >= 2 && y <= 4 && z >= 1 && z <= 3;
					helper.setBlock(x, y, z, inside ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 feet = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		player.snapTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		MinecraftServer server = level.getServer();
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBrightness(LightLayer.SKY, eyes) <= FogLimits.SKY_NONE, "sky light in the box " + level.getBrightness(LightLayer.SKY, eyes));
			HerobrineState state = HerobrineState.get(server);
			HerobrineState.Effects before = state.effects();
			try {
				long clock = level.getDefaultClockTime();
				FogSpell spell = new FogSpell(clock - 3_000, clock + 6_000, 0.8F, false);
				ClientEffects.setFogSpell(server, Optional.of(spell));
				FogLimits.Result inside = FogLimits.of(player);
				FogLimits.Result noSpell = FogLimits.of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), before.duskFogLevel(), clock,
						FogLimits.shape(), 0.0);
				FogLimits.Result outside = FogLimits.of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), before.duskFogLevel(), clock,
						Optional.of(spell));
				helper.assertTrue(inside.equals(noSpell) && outside.fogEnd() < inside.fogEnd(), "the spell reached into sealed stone: " + inside + " vs " + outside);
			} finally {
				ClientEffects.setFogSpell(server, before.fogSpell());
			}
		});
	}

	/** D-071: a v1.1 state (effects without fogSpell, or no effects at all) loads with no spell; a spell round-trips. */
	@GameTest
	public void v11StateLoadsWithNoFogSpell(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		HerobrineState state = new HerobrineState();
		state.reroll(7L, 8L);
		state.setEffects(new HerobrineState.Effects(true, 0.4F));
		CompoundTag encoded = (CompoundTag) HerobrineState.CODEC.encodeStart(ops, state).getOrThrow();
		CompoundTag effects = encoded.getCompoundOrEmpty("effects");
		helper.assertTrue(!effects.contains("fogSpell") && effects.contains("duskFogLevel"), "a clear sky wrote a fogSpell: " + effects);

		CompoundTag legacy = encoded.copy();
		CompoundTag v11Effects = new CompoundTag();
		v11Effects.putBoolean("musicOff", true);
		v11Effects.putFloat("duskFogLevel", 0.4F);
		legacy.put("effects", v11Effects);
		HerobrineState old = HerobrineState.CODEC.parse(ops, legacy).getOrThrow();
		helper.assertTrue(old.effects().fogSpell().isEmpty() && old.effects().musicOff() && old.effects().duskFogLevel() == 0.4F, "v1.1 effects " + old.effects());
		legacy.remove("effects");
		helper.assertTrue(HerobrineState.CODEC.parse(ops, legacy).getOrThrow().effects().equals(HerobrineState.Effects.NONE), "no effects at all");

		FogSpell spell = new FogSpell(3 * DAY + 11_500, 4 * DAY + 5_500, 0.55F, true);
		state.setEffects(state.effects().withFogSpell(Optional.of(spell)));
		HerobrineState loaded = HerobrineState.CODEC.parse(ops, HerobrineState.CODEC.encodeStart(ops, state).getOrThrow()).getOrThrow();
		helper.assertTrue(loaded.effects().equals(new HerobrineState.Effects(true, 0.4F, Optional.of(spell))), "spell round trip " + loaded.effects());
		helper.assertTrue(ClientEffects.syncPayload(loaded.effects()).equals(new ClientEffects.Sync(true, 0.4F, Optional.of(spell))), "sync carries it");
		helper.succeed();
	}

	/** {@link ClientEffects#setFogSpell} stores the spell and keeps music and dusk; their setters keep the spell. Restores the live state. */
	@GameTest
	public void setFogSpellStoresAndKeepsTheOtherEffects(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects before = state.effects();
		try {
			FogSpell spell = new FogSpell(11_500L, 23_500L, 0.5F, false);
			state.setEffects(new HerobrineState.Effects(true, 0.3F));
			ClientEffects.setFogSpell(server, Optional.of(spell));
			helper.assertTrue(state.effects().equals(new HerobrineState.Effects(true, 0.3F, Optional.of(spell))), "stored " + state.effects());
			ClientEffects.setDuskFog(server, 0.6F);
			ClientEffects.setMusicOff(server, false);
			helper.assertTrue(state.effects().equals(new HerobrineState.Effects(false, 0.6F, Optional.of(spell))), "a setter dropped the spell " + state.effects());
			ClientEffects.setFogSpell(server, Optional.empty());
			helper.assertTrue(state.effects().equals(new HerobrineState.Effects(false, 0.6F)), "cleared " + state.effects());
		} finally {
			state.setEffects(before);
		}
		helper.succeed();
	}

	@GameTest
	public void fogSpellAmountEasesInAndOut(GameTestHelper helper) {
		FogSpell spell = new FogSpell(10_000L, 30_000L, 0.8F, false);
		helper.assertTrue(spell.amount(9_999, D) == 0.0 && spell.amount(10_000, D) == 0.0 && spell.amount(30_000, D) == 0.0 && spell.amount(40_000, D) == 0.0,
				"fog outside the spell or at its edges");
		helper.assertTrue(Math.abs(spell.amount(10_500, D) - spell.level() * FogLimits.ease(0.2, 3.0)) < 1.0E-9, "the ease in is the dusk fog's ease");
		helper.assertTrue(spell.amount(12_500, D) == spell.level() && spell.amount(20_000, D) == spell.level(), "not full in the middle");
		helper.assertTrue(Math.abs(spell.amount(29_250, D) - spell.level() * FogLimits.ease(0.5, 3.0)) < 1.0E-9, "the ease out");
		helper.assertTrue(spell.activeAt(10_000) && spell.activeAt(29_999) && !spell.activeAt(30_000) && spell.endedBy(30_000) && !spell.endedBy(29_999),
				"activeAt / endedBy");
		helper.assertTrue(Math.abs(spell.amount(10_500.5, 2500, 1500, 3.0) - spell.level() * FogLimits.ease(500.5 / 2500, 3.0)) < 1.0E-9, "partial ticks");
		for (FogSpell s : List.of(spell, new FogSpell(0L, 1_000L, 1.0F, true))) {
			double last = 0.0;
			double max = 0.0;
			for (long t = s.fromClock() - 10; t <= s.untilClock() + 10; t++) {
				double a = s.amount(t, D);
				helper.assertTrue(Math.abs(a - last) < 0.002, "a jump of " + Math.abs(a - last) + " at " + t + " in " + s);
				helper.assertTrue(a >= 0.0 && a <= s.level(), "out of range at " + t);
				last = a;
				max = Math.max(max, a);
			}
			helper.assertTrue(max > 0.0, "a spell gave no fog");
		}
		helper.assertTrue(new FogSpell(0L, 1_000L, 1.0F, true).amount(500, D) < 1.0, "a spell shorter than its ramp reached full");
		helper.assertTrue(new FogSpell(0L, 1_000L, 0.5F, true).amount(1, 0, 0, 3.0) == 0.5, "a zero ramp is a step");
		helper.succeed();
	}

	/** Steps a run of nights at one stage; returns which were foggy. */
	private static List<Boolean> nights(long seed, Stage stage, int count) {
		RandomSource random = RandomSource.create(seed);
		FogWeatherRules.History history = FogWeatherRules.History.START;
		List<Boolean> foggy = new ArrayList<>();
		for (int night = 0; night < count; night++) {
			Optional<FogSpell> spell = FogWeatherRules.night(random, 100 + night, stage, history, D);
			foggy.add(spell.isPresent());
			history = history.afterNight(spell.isPresent());
		}
		return foggy;
	}

	/** D-071's night rules: the first night clear, the third guaranteed, never two in a row in Alone or Traces, the 4-clear rule. */
	@GameTest
	public void fogNightRollRules(GameTestHelper helper) {
		FogWeatherRules.History start = FogWeatherRules.History.START;
		int second = 0;
		RandomSource shared = RandomSource.create(77L); // one stream: first draws of consecutive LCG seeds are not spread out
		for (long seed = 0; seed < 2_000; seed++) {
			for (Stage stage : Stage.values()) {
				RandomSource random = RandomSource.create(seed);
				helper.assertTrue(FogWeatherRules.night(random, 0, stage, start, D).isEmpty(), "the first night was foggy (" + stage + ", " + seed + ")");
				helper.assertTrue(FogWeatherRules.night(random, 2, stage, start.afterNight(false).afterNight(false), D).isPresent(),
						"the third night was clear after two clear ones (" + stage + ")");
				helper.assertTrue(stage.atLeast(Stage.PROXIMITY)
						|| FogWeatherRules.night(random, 2, stage, start.afterNight(false).afterNight(true), D).isEmpty(), "two fog nights in a row in " + stage);
			}
			second += FogWeatherRules.night(shared, 1, Stage.ALONE, start.afterNight(false), D).isPresent() ? 1 : 0;
		}
		helper.assertTrue(second > 900 && second < 1_100, "the second night is foggy at 50%: " + second + " of 2000");
		helper.assertTrue(FogWeatherRules.night(RandomSource.create(1), 9, Stage.PROXIMITY, new FogWeatherRules.History(9, 4, false, false), D).isPresent(),
				"no fog after 4 clear nights");

		for (Stage stage : Stage.values()) {
			int fog = 0;
			int pairs = 0;
			int total = 0;
			for (long seed = 0; seed < 40; seed++) {
				List<Boolean> run = nights(seed * 31 + stage.level(), stage, 250);
				int clearRun = 0;
				for (int i = 0; i < run.size(); i++) {
					boolean foggy = run.get(i);
					clearRun = foggy ? 0 : clearRun + 1;
					helper.assertTrue(clearRun <= 4, "more than 4 clear nights in a row in " + stage + " (night " + i + ")");
					if (i > 0 && foggy && run.get(i - 1)) {
						pairs++;
					}
					fog += foggy ? 1 : 0;
					total++;
				}
				helper.assertTrue(!run.get(0) && (run.get(1) || run.get(2)), "the first nights in " + stage + ": " + run.subList(0, 3));
			}
			helper.assertTrue(stage.atLeast(Stage.PROXIMITY) ? pairs > 0 : pairs == 0, pairs + " pairs of fog nights in " + stage);
			double share = (double) fog / total;
			helper.assertTrue(share >= D.nightChance(stage) - 0.05 && share < 0.65, String.format(java.util.Locale.ROOT, "fog nights in %s: %.3f", stage, share));
		}
		helper.succeed();
	}

	/** The spell a roll makes: D-071's times and levels, the morning carry share, the quiet share near 0.25 over 10 000 rolls. */
	@GameTest
	public void fogSpellsCarryTheirTimesLevelsAndShares(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		FogWeatherRules.History history = new FogWeatherRules.History(10, 4, false, false); // always foggy: 4 clear nights
		int carried = 0;
		int quiet = 0;
		int rolls = 10_000;
		for (int i = 0; i < rolls; i++) {
			Stage stage = Stage.values()[i % Stage.values().length];
			long day = 50 + i;
			FogSpell spell = FogWeatherRules.night(random, day, stage, history, D).orElseThrow();
			boolean carry = spell.untilClock() == (day + 1) * DAY + FogWeatherRules.MORNING_UNTIL;
			helper.assertTrue(spell.fromClock() == day * DAY + 11_500 && (carry || spell.untilClock() == day * DAY + 23_500), "night times " + spell);
			helper.assertTrue(spell.level() == (float) D.nightLevel(stage), "night level " + spell.level() + " in " + stage);
			carried += carry ? 1 : 0;
			quiet += spell.quiet() ? 1 : 0;
		}
		double carryShare = (double) carried / rolls;
		double quietShare = (double) quiet / rolls;
		helper.assertTrue(Math.abs(carryShare - 0.35) < 0.03, "morning carry share " + carryShare);
		helper.assertTrue(Math.abs(quietShare - 0.25) < 0.03, "quiet share " + quietShare);

		float[] nightLevels = {0.45F, 0.55F, 0.65F, 0.72F, 0.8F};
		float[] dayLevels = {0.4F, 0.45F, 0.55F, 0.6F, 0.65F};
		double[] nightChances = {0.25, 0.33, 0.4, 0.45, 0.5};
		double[] dayChances = {0, 0.06, 0.1, 0.12, 0.12};
		for (Stage stage : Stage.values()) {
			int i = stage.level();
			helper.assertTrue((float) D.nightLevel(stage) == nightLevels[i] && (float) D.dayLevel(stage) == dayLevels[i]
					&& D.nightChance(stage) == nightChances[i] && D.dayChance(stage) == dayChances[i], "D-071 numbers for " + stage);
		}
		helper.assertTrue(D.secondNightChance() == 0.5 && D.clearNightsBeforeFog() == 4 && D.morningCarryChance() == 0.35 && D.quietShare() == 0.25
				&& D.rampTicks() == 2500 && D.fadeTicks() == 1500 && D.easeExponent() == 3.0, "D-071 settings " + D);
		// The roll times atmosphere's live weather and the director's dry runs share: tonight at 10000 (a fog day still at
		// full then), a fog day at 21500 of the day before (a fog night still at full then); a fade longer than the default
		// rolls each before the spell it follows thins.
		FogWeatherRules.Settings longFade = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), D.rampTicks(), 3000L,
				D.easeExponent());
		FogWeatherRules.Settings endless = new FogWeatherRules.Settings(D.nightChanceByStage(), D.secondNightChance(), D.clearNightsBeforeFog(),
				D.morningCarryChance(), D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(), D.quietShare(), D.rampTicks(), 30_000L,
				D.easeExponent());
		helper.assertTrue(FogWeatherRules.NIGHT_ROLL == 10_000L && FogWeatherRules.DAY_ROLL == 21_500L && FogWeatherRules.nightRollTime(D) == 10_000L
				&& FogWeatherRules.nightRollTime(longFade) == 8_500L, "roll times " + FogWeatherRules.nightRollTime(D) + ", " + FogWeatherRules.nightRollTime(longFade));
		helper.assertTrue(FogWeatherRules.dayRollTime(D) == FogWeatherRules.NIGHT_UNTIL - D.fadeTicks() - 500 && FogWeatherRules.dayRollTime(longFade) == 20_000L
				&& FogWeatherRules.dayRollTime(endless) == FogWeatherRules.NIGHT_FROM, "day roll times " + FogWeatherRules.dayRollTime(D) + ", "
						+ FogWeatherRules.dayRollTime(longFade) + ", " + FogWeatherRules.dayRollTime(endless));
		helper.succeed();
	}

	@GameTest
	public void fogDayRollRules(GameTestHelper helper) {
		FogWeatherRules.History history = FogWeatherRules.History.START.afterNight(false).afterNight(false);
		RandomSource random = RandomSource.create(9L);
		int days = 0;
		int rolls = 20_000;
		for (int i = 0; i < rolls; i++) {
			long day = 10 + i;
			helper.assertTrue(FogWeatherRules.day(random, day, Stage.ALONE, history, Optional.empty(), D).isEmpty(), "a fog day in Alone");
			helper.assertTrue(FogWeatherRules.day(random, day, Stage.PROXIMITY, history.afterDay(true), Optional.empty(), D).isEmpty(), "two fog days in a row");
			FogSpell carry = new FogSpell(FogWeatherRules.nightFrom(day - 1), FogWeatherRules.nightUntil(day - 1, true), 0.6F, false);
			helper.assertTrue(FogWeatherRules.covers(Optional.of(carry), day)
					&& FogWeatherRules.day(random, day, Stage.REMOVAL, history, Optional.of(carry), D).isEmpty(), "a fog day over a morning carry-over");
			FogSpell plain = new FogSpell(FogWeatherRules.nightFrom(day - 1), FogWeatherRules.nightUntil(day - 1, false), 0.6F, false);
			helper.assertTrue(!FogWeatherRules.covers(Optional.of(plain), day), "a plain fog night covers the next day");
			Optional<FogSpell> spell = FogWeatherRules.day(random, day, Stage.PROXIMITY, history, Optional.of(plain), D);
			if (spell.isPresent()) {
				days++;
				helper.assertTrue(spell.get().fromClock() == day * DAY - 1000 && spell.get().untilClock() == day * DAY + 11_500
						&& spell.get().level() == (float) D.dayLevel(Stage.PROXIMITY), "fog day " + spell.get());
			}
		}
		double share = (double) days / rolls;
		helper.assertTrue(Math.abs(share - 0.1) < 0.015, "fog days in Proximity: " + share);
		helper.succeed();
	}

	/** One spell taking over from another goes on from exactly the fog there is, and a day handed to a night never thins after the roll. */
	@GameTest
	public void handOverNeverPops(GameTestHelper helper) {
		long d = 7;
		FogSpell day = new FogSpell(FogWeatherRules.dayFrom(d), FogWeatherRules.dayUntil(d), 0.55F, true);
		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.72F, false);
		for (long now : new long[] {d * DAY + 9_000, d * DAY + 10_000, d * DAY + 11_000, d * DAY + 11_400}) {
			FogSpell joined = FogWeatherRules.handOver(Optional.of(day), night, now, D);
			double before = day.amount(now, D);
			helper.assertTrue(Math.abs(joined.amount(now, D) - before) < 2.0E-3, "a pop at " + now + ": " + before + " -> " + joined.amount(now, D));
			helper.assertTrue(joined.untilClock() == night.untilClock() && !joined.quiet() && joined.level() == 0.72F, "joined " + joined);
			double last = joined.amount(now, D);
			for (long t = now; t < night.untilClock() - D.fadeTicks(); t += 20) {
				double a = joined.amount(t, D);
				helper.assertTrue(a >= last - 1.0E-12 && a > 0.0, "the fog thinned at " + t + " after a roll at " + now);
				last = a;
			}
			helper.assertTrue(joined.amount(night.untilClock() - D.fadeTicks(), D) == night.level(), "never reached the night's level");
		}
		// A fog night still fading when a fog day rolls (23000): no pop; a lighter next spell keeps the heavier fog there is.
		FogSpell prev = new FogSpell(FogWeatherRules.nightFrom(d - 1), FogWeatherRules.nightUntil(d - 1, false), 0.8F, false);
		FogSpell light = new FogSpell(FogWeatherRules.dayFrom(d), FogWeatherRules.dayUntil(d), 0.1F, true);
		long roll = FogWeatherRules.dayFrom(d);
		FogSpell joined = FogWeatherRules.handOver(Optional.of(prev), light, roll, D);
		helper.assertTrue(Math.abs(joined.amount(roll, D) - prev.amount(roll, D)) < 2.0E-3 && joined.level() >= 0.1F && joined.quiet(), "night to day " + joined);
		// Nothing left of the old one: the new one as it is.
		helper.assertTrue(FogWeatherRules.handOver(Optional.empty(), night, d * DAY + 11_000, D) == night, "no current spell");
		helper.assertTrue(FogWeatherRules.handOver(Optional.of(day), night, day.untilClock(), D) == night, "an ended spell");
		helper.assertTrue(Math.abs(FogLimits.ease(FogWeatherRules.inverseEase(0.37, 3.0), 3.0) - 0.37) < 1.0E-9, "inverse ease");
		helper.succeed();
	}

	@GameTest
	public void fogWeatherSettingsAreNormalized(GameTestHelper helper) {
		FogWeatherRules.Settings messy = new FogWeatherRules.Settings(FogWeatherRules.perStage(0.2, Double.NaN), 7.0, 0, -1,
				FogWeatherRules.perStage(new float[] {0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F}), null, List.of(), Double.NaN, -5L, -5L, Double.NaN);
		helper.assertTrue(messy.nightChanceByStage().equals(List.of(0.2, 0.0, 0.0, 0.0, 0.0)), "short list " + messy.nightChanceByStage());
		helper.assertTrue(messy.dayChanceByStage().size() == 5 && messy.dayChance(Stage.REMOVAL) == (double) 0.9F, "long list");
		helper.assertTrue(messy.nightLevelByStage().equals(List.of(0.0, 0.0, 0.0, 0.0, 0.0)) && messy.dayLevel(Stage.TRACES) == 0.0, "null and empty");
		helper.assertTrue(messy.secondNightChance() == 1.0 && messy.clearNightsBeforeFog() == 1 && messy.morningCarryChance() == 0.0
				&& messy.quietShare() == 0.0 && messy.rampTicks() == 0 && messy.fadeTicks() == 0 && messy.easeExponent() == 3.0, "clamps " + messy);
		helper.assertTrue(new FogWeatherRules.Settings(D.nightChanceByStage(), 0.5, 4, 0.35, D.dayChanceByStage(), D.nightLevelByStage(), D.dayLevelByStage(),
				0.25, 2500, 1500, 3.0).equals(D), "settings are compared by value");
		FogWeatherRules.History history = new FogWeatherRules.History(-3, -1, false, false).afterNight(false).afterNight(true).afterDay(true);
		helper.assertTrue(history.equals(new FogWeatherRules.History(2, 0, true, true)), "history steps " + history);
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		helper.assertTrue(FogWeatherRules.History.CODEC.parse(ops, FogWeatherRules.History.CODEC.encodeStart(ops, history).getOrThrow()).getOrThrow()
				.equals(history) && FogWeatherRules.History.CODEC.parse(ops, new CompoundTag()).getOrThrow().equals(FogWeatherRules.History.START), "history codec");
		helper.succeed();
	}

	/** FogLimits with a spell: the amounts combine, D-071's distances, no spell changes nothing, the server form reads the stored spell. */
	@GameTest
	public void fogLimitsWithASpell(GameTestHelper helper) {
		FogLimits.Shape shape = FogLimits.Shape.DEFAULT;
		helper.assertTrue(FogLimits.combine(0.3, 0.0) == 0.3 && FogLimits.combine(0.0, 0.45) == 0.45 && Math.abs(FogLimits.combine(0.5, 0.5) - 0.75) < 1.0E-12
				&& FogLimits.combine(2.0, -1.0) == 1.0, "combine");
		// D-071: at a 12-chunk view a 0.45 spell ends the fog at about 75 blocks, 0.8 at about 37 (noon: no dusk fog).
		double light = FogLimits.of(12, 12, 0.0F, 6000L, shape, 0.45).fogEnd();
		double heavy = FogLimits.of(12, 12, 0.0F, 6000L, shape, 0.8).fogEnd();
		helper.assertTrue(Math.abs(light - 75) < 1.5 && Math.abs(heavy - 37) < 1.5, "spell fog ends " + light + " / " + heavy);

		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(3), FogWeatherRules.nightUntil(3, true), 0.65F, false);
		for (long clock = 3 * DAY; clock < 5 * DAY; clock += 125) {
			for (float dusk : new float[] {0.0F, 0.15F, 0.7F}) {
				FogLimits.Result none = FogLimits.of(12, 10, dusk, clock, Optional.empty(), shape, D);
				FogLimits.Result old = FogLimits.of(12, 10, dusk, clock, shape);
				helper.assertTrue(none.equals(old), "no spell changed the fog at " + clock);
				FogLimits.Result with = FogLimits.of(12, 10, dusk, clock, Optional.of(night), shape, D);
				double amount = FogLimits.combine(dusk * FogLimits.duskWeight(clock, shape), night.amount(clock, D));
				helper.assertTrue(Math.abs(with.fogEnd() - FogLimits.fogEnd(with.renderLimit(), shape.duskMinFogBlocks(), amount)) < 1.0E-9
						&& with.fogEnd() <= old.fogEnd() + 1.0E-9 && with.renderLimit() == old.renderLimit(), "spell fog at " + clock + " dusk " + dusk);
			}
		}

		// The server form reads the stored spell on the player's level clock (set and restored within one tick), in the
		// open: once the light engine has lit the fresh test area, the sky at the player's eyes lets the whole spell in.
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, eyes) >= FogLimits.SKY_FULL, "no sky at the player's eyes yet");
			HerobrineState state = HerobrineState.get(server);
			HerobrineState.Effects before = state.effects();
			try {
				long now = helper.getLevel().getDefaultClockTime();
				FogSpell current = new FogSpell(now - 5000, now + 5000, 0.8F, false);
				state.setEffects(new HerobrineState.Effects(before.musicOff(), 0.2F, Optional.of(current)));
				FogLimits.Result live = FogLimits.of(player);
				FogLimits.Result pure = FogLimits.of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), 0.2F, now, Optional.of(current));
				FogLimits.Result clear = FogLimits.of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), 0.2F, now);
				helper.assertTrue(live.equals(pure) && live.fogEnd() < clear.fogEnd(), "server form " + live + " vs " + pure + " / clear " + clear);
			} finally {
				state.setEffects(before);
			}
		});
	}
}
