package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * Atmosphere's live fog weather (D-071, {@link FogWeather}) and its config (D-075): the roll schedule by core's rules
 * over whole runs of days, late rolls, a clock set back, the quiet for good, the spell in the state and in the join
 * payload, and the v1.1 atmosphere section's upgrade. The schedule runs on D-071's numbers
 * ({@link FogWeatherRules.Settings#DEFAULT}), which the defaults of {@link AtmosphereConfig} are.
 * {@code DuskFogGameTests} extends this.
 */
public class FogWeatherGameTests extends SharedMomentGameTests {
	private static final FogWeatherRules.Settings D = FogWeatherRules.Settings.DEFAULT;
	private static final long DAY = FogWeatherRules.DAY;
	private static final int CHECK = 20;
	private static final int DAYS = 40;
	/** The most a hand-over may move the fog amount at its moment (whole-tick rounding of the joined ramp). */
	private static final double HAND_OVER = 2.0E-3;

	/** Eight days a stage: Alone, Traces, Proximity, Telling, Removal. */
	private static Stage stageOn(long day) {
		return Stage.values()[(int) Math.min(Stage.values().length - 1, Math.max(0, day) / 8)];
	}

	private static double fog(Optional<FogSpell> spell, long clock) {
		return FogLimits.spellAmount(spell, clock, D);
	}

	@GameTest
	public void theDefaultsAreTheDecisionsNumbers(GameTestHelper helper) {
		AtmosphereConfig fresh = new AtmosphereConfig();
		helper.assertTrue(fresh.fogWeather().equals(D), "atmosphere's fog weather defaults are not D-071's: " + fresh.fogWeather());
		helper.assertTrue(FogWeatherRules.settings().equals(AtmosphereConfig.get().fogWeather()), "core's rules do not use atmosphere's live config");
		helper.assertTrue(FogWeather.nightRollTime(D) == 10_000L && FogWeather.dayRollUntil(5, D) == 5 * DAY + 10_000L
				&& FogWeather.nightRollUntil(5, D) == 5 * DAY + 22_000L, "tonight is not rolled at 10000, before a fog day thins");
		helper.assertTrue(fresh.fogNightRgb() == 0x3b4048 && fresh.fogDayRgb() == 0xb9bec4 && fresh.fogNightColorLift == 0.6, "D-071's colors and lift");
		helper.assertTrue(AtmosphereConfig.rgb("3B4048", 0) == 0x3b4048 && AtmosphereConfig.rgb("#12345", 7) == 7 && AtmosphereConfig.rgb("#zzzzzz", 7) == 7
				&& AtmosphereConfig.rgb(null, 7) == 7, "color parsing");
		fresh.fogNightColor = "#ffffff";
		helper.assertTrue(fresh.fogNightRgb() == 0xffffff, "a changed color is not read again");
		helper.succeed();
	}

	/**
	 * The live schedule over 40 days (eight per stage) for 24 worlds, checked every second as the server does: tonight
	 * rolls at 10000, a fog day at 21500 of the day before (day 0 at once); core's rules hold (the first night clear,
	 * one of the first three foggy, never two fog nights in a row in Alone or Traces, never five clear in a row, no fog
	 * day in Alone, never two in a row, never over a morning carry-over); the fog is there exactly when the rolls say;
	 * and no check ever moves the fog amount (every new spell is joined to the one before).
	 */
	@GameTest
	public void theLiveRollScheduleFollowsTheRules(GameTestHelper helper) {
		int fogNights = 0;
		int fogDays = 0;
		int carries = 0;
		for (long seed = 1; seed <= 24; seed++) {
			RandomSource random = RandomSource.create(seed);
			FogWeather.Memory memory = FogWeather.Memory.START;
			Optional<FogSpell> spell = Optional.empty();
			List<Optional<FogSpell>> nights = new ArrayList<>();
			List<Optional<FogSpell>> days = new ArrayList<>();
			List<Stage> dayStages = new ArrayList<>();
			for (long now = 0; now < DAYS * DAY; now += CHECK) {
				long day = Math.floorDiv(now, DAY);
				long time = Math.floorMod(now, DAY);
				FogWeather.Outcome out = FogWeather.step(memory, spell, now, stageOn(day), random, true, false, D);
				String where = String.format(Locale.ROOT, "(seed %d, day %d, time %d)", seed, day, time);
				helper.assertTrue(Math.abs(fog(out.spell(), now) - fog(spell, now)) < HAND_OVER,
						"the fog popped " + fog(spell, now) + " -> " + fog(out.spell(), now) + " " + where + ": " + spell + " -> " + out.spell());
				if (out.memory().nightRolled() != memory.nightRolled()) {
					helper.assertTrue(time == 10_000L && out.memory().nightRolled() == day && nights.size() == day, "tonight rolled " + where);
					nights.add(out.memory().nightRoll());
				}
				if (out.memory().dayRolled() != memory.dayRolled()) {
					long rolled = out.memory().dayRolled();
					helper.assertTrue((now == 0 && rolled == 0 || time == FogWeatherRules.dayRollTime(D) && rolled == day + 1) && days.size() == rolled, "a day rolled " + where);
					days.add(out.memory().dayRoll());
					dayStages.add(stageOn(day));
				}
				memory = out.memory();
				spell = out.spell();
				// The fog is there exactly when the rolls say: in the night's middle, in the morning, at noon.
				if (time == 18_000L) {
					helper.assertTrue(fog(spell, now) > 0.0 == nights.get((int) day).isPresent(), "fog in the night's middle " + where);
				} else if (time == 3_000L && day > 0) {
					boolean carried = nights.get((int) day - 1).filter(s -> s.untilClock() > day * DAY).isPresent();
					helper.assertTrue(fog(spell, now) > 0.0 == (carried || days.get((int) day).isPresent()), "fog in the morning " + where);
				} else if (time == 6_000L) {
					helper.assertTrue(fog(spell, now) > 0.0 == days.get((int) day).isPresent(), "fog at noon " + where);
				}
			}
			// Day 40's daylight is rolled at 21500 of the last day.
			helper.assertTrue(nights.size() == DAYS && days.size() == DAYS + 1 && memory.history().nights() == DAYS,
					"not every night and day rolled once: " + nights.size() + " nights, " + days.size() + " days");
			helper.assertTrue(nights.getFirst().isEmpty(), "the first night was foggy (seed " + seed + ")");
			helper.assertTrue(nights.subList(0, 3).stream().anyMatch(Optional::isPresent), "none of the first three nights was foggy (seed " + seed + ")");
			int clearRun = 0;
			for (int n = 0; n < DAYS; n++) {
				boolean foggy = nights.get(n).isPresent();
				clearRun = foggy ? 0 : clearRun + 1;
				helper.assertTrue(clearRun <= D.clearNightsBeforeFog(), "five clear nights in a row by night " + n + " (seed " + seed + ")");
				if (n > 0 && foggy && nights.get(n - 1).isPresent()) {
					helper.assertTrue(stageOn(n).atLeast(Stage.PROXIMITY), "two fog nights in a row in " + stageOn(n) + " (seed " + seed + ", night " + n + ")");
				}
				fogNights += foggy ? 1 : 0;
				long morning = (n + 1) * DAY;
				carries += nights.get(n).filter(s -> s.untilClock() > morning).isPresent() ? 1 : 0;
			}
			for (int d = 0; d < DAYS; d++) {
				if (days.get(d).isEmpty()) {
					continue;
				}
				fogDays++;
				int day = d;
				helper.assertTrue(dayStages.get(d) != Stage.ALONE, "a fog day in Alone (seed " + seed + ", day " + d + ")");
				helper.assertTrue(d == 0 || days.get(d - 1).isEmpty(), "two fog days in a row (seed " + seed + ", day " + d + ")");
				helper.assertTrue(d == 0 || nights.get(d - 1).filter(s -> s.untilClock() > day * DAY).isEmpty(), "a fog day over a carry-over (day " + d + ")");
			}
		}
		helper.assertTrue(fogNights > 0 && fogDays > 0 && carries > 0, "no fog night, day or carry-over in 960 days: " + fogNights + " " + fogDays + " " + carries);
		helper.succeed();
	}

	/** A fog day still at full when tonight's fog night rolls (10000) never thins on its way into the night. */
	@GameTest
	public void aFogDayHandsOverToAFogNightWithoutThinning(GameTestHelper helper) {
		FogWeatherRules.Settings always = new FogWeatherRules.Settings(FogWeatherRules.perStage(1.0), 0.5, 4, 0.0, D.dayChanceByStage(), D.nightLevelByStage(),
				D.dayLevelByStage(), 0.0, D.rampTicks(), D.fadeTicks(), D.easeExponent());
		long d = 12;
		for (Stage stage : new Stage[] {Stage.TRACES, Stage.PROXIMITY, Stage.REMOVAL}) {
			FogSpell fogDay = new FogSpell(FogWeatherRules.dayFrom(d), FogWeatherRules.dayUntil(d), (float) always.dayLevel(stage), false);
			FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(9, 0, false, true), d - 1, d, Optional.empty(), Optional.of(fogDay));
			Optional<FogSpell> spell = Optional.of(fogDay);
			double last = fog(spell, d * DAY + 9_000);
			for (long now = d * DAY + 9_000; now <= d * DAY + 16_000; now += CHECK) {
				FogWeather.Outcome out = FogWeather.step(memory, spell, now, stage, RandomSource.create(now), true, false, always);
				boolean rolled = out.memory().nightRolled() != memory.nightRolled();
				memory = out.memory();
				spell = out.spell();
				double fog = fog(spell, now);
				// At the roll itself the joined ramp is placed on whole ticks: within HAND_OVER of the fog there was.
				helper.assertTrue(rolled ? Math.abs(fog - last) < HAND_OVER : fog >= last - 1.0E-9,
						"the fog thinned at " + Math.floorMod(now, DAY) + " in " + stage + ": " + last + " -> " + fog);
				last = fog;
			}
			helper.assertTrue(memory.nightRolled() == d && memory.nightRoll().isPresent(), "tonight did not roll foggy");
			helper.assertTrue(Math.abs(last - always.nightLevel(stage)) < 1.0E-6 || last >= always.nightLevel(stage), "never reached the night's level: " + last);
		}
		helper.succeed();
	}

	/**
	 * Nothing rolls while nobody takes part; the first one on mid-night gets tonight's roll then, easing in from that
	 * moment; a clock set back rolls the nights ahead of it again; a spell over, or far ahead of the clock, is dropped.
	 */
	@GameTest
	public void lateRollsTheClockSetBackAndOldSpells(GameTestHelper helper) {
		FogWeatherRules.Settings always = new FogWeatherRules.Settings(FogWeatherRules.perStage(1.0), 1.0, 4, 0.0, D.dayChanceByStage(), D.nightLevelByStage(),
				D.dayLevelByStage(), 0.0, D.rampTicks(), D.fadeTicks(), D.easeExponent());
		FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(6, 0, false, false), 4, 5, Optional.empty(), Optional.empty());
		long night = 5;
		// Nobody on through tonight's roll time: nothing.
		for (long now = night * DAY + 9_000; now < night * DAY + 15_000; now += CHECK) {
			FogWeather.Outcome out = FogWeather.step(memory, Optional.empty(), now, Stage.PROXIMITY, RandomSource.create(1), false, false, always);
			helper.assertTrue(out.memory().equals(memory) && out.spell().isEmpty(), "a roll with nobody on at " + now);
		}
		// Someone joins at 15000: tonight rolls now and eases in from now.
		long join = night * DAY + 15_000;
		FogWeather.Outcome late = FogWeather.step(memory, Optional.empty(), join, Stage.PROXIMITY, RandomSource.create(1), true, false, always);
		helper.assertTrue(late.memory().nightRolled() == night && late.spell().isPresent() && late.spell().get().fromClock() == join
				&& late.spell().get().untilClock() == FogWeatherRules.nightUntil(night, false) && fog(late.spell(), join) == 0.0, "the late roll: " + late);
		// Past the window (22000) a night is no longer rolled: it waits for the next one.
		FogWeather.Outcome tooLate = FogWeather.step(memory, Optional.empty(), night * DAY + 22_000, Stage.PROXIMITY, RandomSource.create(1), true, false, always);
		helper.assertTrue(tooLate.memory().nightRolled() == 4, "a night rolled after its window");
		// The clock set back two days: those nights and days roll again when they come; the spell now far ahead is dropped.
		FogWeather.Memory ahead = late.memory();
		long back = 3 * DAY + 2_000;
		FogWeather.Outcome reset = FogWeather.step(ahead, late.spell(), back, Stage.PROXIMITY, RandomSource.create(2), false, false, always);
		helper.assertTrue(reset.memory().nightRolled() == 1 && reset.memory().dayRolled() == 2 && reset.spell().isEmpty()
				&& reset.memory().history().equals(ahead.history()), "a clock set back: " + reset);
		FogWeather.Outcome again = FogWeather.step(reset.memory(), reset.spell(), 3 * DAY + 10_000, Stage.PROXIMITY, RandomSource.create(2), true, false,
				always);
		helper.assertTrue(again.memory().nightRolled() == 3 && again.spell().isPresent(), "night 3 did not roll again after the clock went back");
		// An ended spell is dropped (it gives no fog).
		FogSpell over = new FogSpell(0, 10_000, 0.5F, false);
		helper.assertTrue(FogWeather.step(FogWeather.Memory.START, Optional.of(over), 10_000, Stage.ALONE, RandomSource.create(3), false, false, D).spell().isEmpty(),
				"an ended spell was kept");
		helper.succeed();
	}

	/**
	 * Once the world is quiet for good (or during the last minute) nothing rolls and the fog there is eases out over
	 * the fade from exactly where it is; the live gate reads both flags.
	 */
	@GameTest
	public void theQuietForGoodEasesTheFogOut(GameTestHelper helper) {
		long d = 20;
		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, true), 0.72F, false);
		FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(20, 0, true, false), d, d, Optional.of(night), Optional.empty());
		long now = d * DAY + 16_000;
		FogWeather.Outcome out = FogWeather.step(memory, Optional.of(night), now, Stage.TELLING, RandomSource.create(4), true, true, D);
		helper.assertTrue(out.memory().equals(memory), "the quiet rolled or forgot something");
		FogSpell fading = out.spell().orElseThrow();
		helper.assertTrue(Math.abs(fading.amount(now, D) - night.amount(now, D)) < 1.0E-6 && fading.untilClock() == now + D.fadeTicks() && fading.quiet(),
				"not a fade from where the fog is: " + fading);
		double last = fading.amount(now, D);
		for (long t = now; t <= now + D.fadeTicks(); t += CHECK) {
			double fog = fading.amount(t, D);
			helper.assertTrue(fog <= last + 1.0E-12, "the fog grew while easing out at " + t);
			last = fog;
		}
		helper.assertTrue(fading.amount(now + D.fadeTicks(), D) == 0.0, "still fog after the fade");
		// The next check keeps the fade; a spell not started yet goes at once; tonight never rolls.
		helper.assertTrue(FogWeather.step(out.memory(), out.spell(), now + CHECK, Stage.TELLING, RandomSource.create(4), true, true, D).spell().equals(out.spell()),
				"the fade restarted");
		FogSpell coming = new FogSpell(FogWeatherRules.nightFrom(d + 1), FogWeatherRules.nightUntil(d + 1, false), 0.5F, false);
		helper.assertTrue(FogWeather.fadeOut(Optional.of(coming), now, D).isEmpty(), "a spell not started yet was kept");
		FogWeather.Outcome tonight = FogWeather.step(memory, Optional.empty(), (d + 1) * DAY + 10_000, Stage.TELLING, RandomSource.create(4), true, true, D);
		helper.assertTrue(tonight.memory().nightRolled() == d && tonight.spell().isEmpty(), "a night rolled while quiet for good");

		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		boolean silence = state.hasFlag(Gates.SILENCE_FOREVER_FLAG);
		boolean pathC = state.hasFlag(Gates.PATH_C_FLAG);
		boolean lastMinute = state.hasFlag(TraceService.LAST_MINUTE_FLAG);
		try {
			for (String flag : List.of(Gates.SILENCE_FOREVER_FLAG, Gates.PATH_C_FLAG, TraceService.LAST_MINUTE_FLAG)) {
				state.setFlag(Gates.SILENCE_FOREVER_FLAG, false);
				state.setFlag(Gates.PATH_C_FLAG, false);
				state.setFlag(TraceService.LAST_MINUTE_FLAG, false);
				helper.assertFalse(FogWeather.quiet(server), "no fog weather without any flag");
				state.setFlag(flag, true);
				helper.assertTrue(FogWeather.quiet(server), "fog weather goes on with " + flag);
			}
		} finally {
			state.setFlag(Gates.SILENCE_FOREVER_FLAG, silence);
			state.setFlag(Gates.PATH_C_FLAG, pathC);
			state.setFlag(TraceService.LAST_MINUTE_FLAG, lastMinute);
		}
		helper.succeed();
	}

	/** The commands' spells: from now to the end of the night, the morning or the day now or next, at the stage's level, joined without a pop. */
	@GameTest
	public void commandSpellsRunToTheEndOfTheirPart(GameTestHelper helper) {
		long d = 9;
		FogSpell night = FogWeather.commandSpell(FogWeather.Kind.NIGHT, d * DAY + 14_000, Stage.TRACES, false, D);
		helper.assertTrue(night.fromClock() == d * DAY + 14_000 && night.untilClock() == FogWeatherRules.nightUntil(d, false) && night.level() == 0.55F
				&& !night.quiet(), "night " + night);
		FogSpell morning = FogWeather.commandSpell(FogWeather.Kind.MORNING, d * DAY + 1_000, Stage.ALONE, true, D);
		helper.assertTrue(morning.untilClock() == d * DAY + FogWeatherRules.MORNING_UNTIL && morning.level() == 0.45F && morning.quiet(), "morning " + morning);
		FogSpell evening = FogWeather.commandSpell(FogWeather.Kind.MORNING, d * DAY + 15_000, Stage.ALONE, false, D);
		helper.assertTrue(evening.untilClock() == (d + 1) * DAY + FogWeatherRules.MORNING_UNTIL, "a morning asked for at night is the next one");
		FogSpell day = FogWeather.commandSpell(FogWeather.Kind.DAY, d * DAY + 2_000, Stage.REMOVAL, false, D);
		helper.assertTrue(day.untilClock() == FogWeatherRules.dayUntil(d) && day.level() == 0.65F, "day " + day);
		FogSpell nextDay = FogWeather.commandSpell(FogWeather.Kind.DAY, d * DAY + 10_500, Stage.REMOVAL, false, D);
		helper.assertTrue(nextDay.untilClock() == FogWeatherRules.dayUntil(d + 1), "a day asked for in its fade is the next one");
		// Joined to a spell giving fog: no pop.
		long now = d * DAY + 18_000;
		FogSpell current = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.8F, false);
		FogSpell joined = FogWeatherRules.handOver(Optional.of(current), FogWeather.commandSpell(FogWeather.Kind.DAY, now, Stage.TRACES, true, D), now, D);
		helper.assertTrue(Math.abs(joined.amount(now, D) - current.amount(now, D)) < HAND_OVER, "the command popped the fog");
		helper.succeed();
	}

	/**
	 * The spell goes out on change and on join: stored in the state (music and dusk kept), carried by the join payload
	 * core sends ({@code ClientEffects.syncPayload}), through the wire, into the client's state; a change (the spell
	 * ending) reaches it the same way, and a disconnect forgets it. Restores the live state.
	 */
	@GameTest
	public void theSpellGoesOutOnChangeAndOnJoin(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		AtmosphereData data = AtmosphereData.get(server);
		HerobrineState.Effects before = state.effects();
		FogWeather.Memory memoryBefore = data.fogWeather();
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		try {
			long now = server.overworld().getDefaultClockTime();
			FogSpell spell = new FogSpell(now - 3_000, now + 9_000, 0.65F, false);
			FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(3, 0, true, false), Math.floorDiv(now, DAY), memoryBefore.dayRolled(),
					Optional.of(spell), Optional.empty());
			FogWeather.apply(server, new FogWeather.Outcome(memory, Optional.of(spell)));
			helper.assertTrue(state.effects().equals(before.withFogSpell(Optional.of(spell))), "not stored, or music and dusk changed: " + state.effects());
			helper.assertTrue(data.fogWeather().equals(memory), "the memory was not kept");

			ClientEffects.Sync join = roundTrip(ClientEffects.Sync.CODEC, ClientEffects.syncPayload(state.effects()));
			helper.assertTrue(join.fogSpell().equals(Optional.of(spell)), "the join payload has no spell: " + join);
			WeatherFog client = new WeatherFog();
			client.receive(join);
			helper.assertTrue(client.spell().equals(Optional.of(spell)) && client.amount(now, false, settings) == FogLimits.spellAmount(Optional.of(spell), now, settings)
					&& client.amount(now, false, settings) > 0.0 && client.amount(now, true, settings) == 0.0, "the client after joining: " + client.spell());

			FogWeather.apply(server, new FogWeather.Outcome(memory, Optional.empty()));
			ClientEffects.FogSpell change = roundTrip(ClientEffects.FogSpell.CODEC, new ClientEffects.FogSpell(state.effects().fogSpell()));
			client.receive(change);
			helper.assertTrue(state.effects().fogSpell().isEmpty() && client.spell().isEmpty() && client.amount(now, false, settings) == 0.0, "the end of it");
			client.receive(join);
			client.reset();
			helper.assertTrue(client.spell().isEmpty(), "a disconnect kept the spell");

			// The live check with nobody taking part rolls nothing.
			FogWeather.apply(server, new FogWeather.Outcome(memoryBefore, before.fogSpell()));
			if (com.forzacode.a1016_02.core.Services.watch().participants(server).isEmpty() && !FogWeather.quiet(server)) {
				FogWeather.check(server);
				helper.assertTrue(data.fogWeather().history().equals(memoryBefore.history()), "a roll with nobody on");
			}
		} finally {
			ClientEffects.setFogSpell(server, before.fogSpell());
			data.setFogWeather(memoryBefore);
		}
		helper.succeed();
	}

	/** Old saves: an atmosphere.dat from v1.1 (no fog weather) loads at the start; the memory round-trips. */
	@GameTest
	public void fogWeatherMemoryLoadsAndSaves(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag v11 = new CompoundTag();
		v11.putBoolean("musicOffDone", true);
		v11.putInt("duskStage", 1);
		AtmosphereData old = AtmosphereData.CODEC.parse(ops, v11).getOrThrow();
		helper.assertTrue(old.fogWeather().equals(FogWeather.Memory.START) && old.musicOffDone() && old.duskStage() == 1, "a v1.1 file: " + old.fogWeather());
		FogWeather.Memory memory = new FogWeather.Memory(new FogWeatherRules.History(7, 2, false, true), 6, 7,
				Optional.empty(), Optional.of(new FogSpell(6 * DAY + 23_000, 7 * DAY + 11_500, 0.45F, true)));
		old.setFogWeather(memory);
		Tag saved = AtmosphereData.CODEC.encodeStart(ops, old).getOrThrow();
		helper.assertTrue(AtmosphereData.CODEC.parse(ops, saved).getOrThrow().fogWeather().equals(memory), "the memory did not round-trip: " + saved);
		helper.succeed();
	}

	/**
	 * D-075: a v1.1 atmosphere section (every field it had, no version) upgrades to version 1: values still at their old
	 * default take the new one (cave sound radius 48 to 24, fog drift fade 100 to 160), values changed by hand stay, the
	 * new fog weather fields get their defaults; a version 1 section is not stepped again; fresh defaults stay as they
	 * are; the live section is at the current version.
	 */
	@GameTest
	public void aV11AtmosphereSectionUpgrades(GameTestHelper helper) {
		AtmosphereConfig d = new AtmosphereConfig();
		Gson gson = new Gson();
		AtmosphereConfig v11 = new AtmosphereConfig();
		v11.caveSoundAloneRadius = 48;
		v11.fogDriftFadeTicks = 100;
		JsonObject tree = gson.toJsonTree(v11).getAsJsonObject();
		for (String added : List.of("version", "rockSoundAloneRadius", "rockSoundMinDistance", "rockSoundMaxDistance", "rockSoundMaxAbove",
				"rockSoundMaxBelow", "rockSoundVolume", "rockSoundPitchMin", "rockSoundPitchMax", "rockSoundHitShare", "fogNightChanceByStage",
				"fogSecondNightChance", "fogClearNightsBeforeFog", "fogMorningCarryChance", "fogDayChanceByStage", "fogNightLevelByStage",
				"fogDayLevelByStage", "fogSpellQuietShare", "fogSpellRampTicks", "fogSpellFadeTicks", "fogSpellHazeFullAmount", "fogNightColor",
				"fogNightColorLift", "fogDayColor")) {
			helper.assertTrue(tree.remove(added) != null, "not a field: " + added);
		}
		JsonObject sections = new JsonObject();
		sections.add("atmosphere", tree);
		AtmosphereConfig upgraded = ModConfig.readSection(sections, "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		helper.assertTrue(upgraded.version == AtmosphereConfig.VERSION && upgraded.caveSoundAloneRadius == 24 && upgraded.fogDriftFadeTicks == 160,
				"old defaults did not move: " + upgraded.caveSoundAloneRadius + " " + upgraded.fogDriftFadeTicks);
		helper.assertTrue(upgraded.fogWeather().equals(d.fogWeather()) && upgraded.fogNightRgb() == 0x3b4048 && upgraded.rockSoundAloneRadius == 24
				&& upgraded.fogSpellHazeFullAmount == d.fogSpellHazeFullAmount, "the new fields are not at their defaults");

		// Changed by hand: kept, whichever of the two it is; the other one still moves.
		tree.addProperty("caveSoundAloneRadius", 40);
		tree.addProperty("duskMinFogBlocks", 30.0);
		AtmosphereConfig handRadius = ModConfig.readSection(sections, "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		helper.assertTrue(handRadius.caveSoundAloneRadius == 40 && handRadius.fogDriftFadeTicks == 160 && handRadius.duskMinFogBlocks == 30.0,
				"a hand-changed radius: " + handRadius.caveSoundAloneRadius + " " + handRadius.fogDriftFadeTicks);
		tree.addProperty("caveSoundAloneRadius", 48);
		tree.addProperty("fogDriftFadeTicks", 120);
		AtmosphereConfig handFade = ModConfig.readSection(sections, "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		helper.assertTrue(handFade.caveSoundAloneRadius == 24 && handFade.fogDriftFadeTicks == 120, "a hand-changed fade: " + handFade.fogDriftFadeTicks);

		JsonObject current = JsonParser.parseString("{\"atmosphere\": {\"version\": 1, \"caveSoundAloneRadius\": 48, \"fogDriftFadeTicks\": 100}}").getAsJsonObject();
		AtmosphereConfig kept = ModConfig.readSection(current, "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		helper.assertTrue(kept.caveSoundAloneRadius == 48 && kept.fogDriftFadeTicks == 100, "a version 1 section was stepped again");

		AtmosphereConfig fresh = ModConfig.readSection(new JsonObject(), "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		helper.assertTrue(fresh.version == AtmosphereConfig.VERSION && fresh.caveSoundAloneRadius == d.caveSoundAloneRadius
				&& fresh.fogDriftFadeTicks == d.fogDriftFadeTicks && fresh.fogWeather().equals(d.fogWeather()), "fresh defaults changed by the upgrade");
		helper.assertTrue(AtmosphereConfig.get().version == AtmosphereConfig.VERSION, "the live section is not at the current version");
		helper.succeed();
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			return codec.decode(buf);
		} finally {
			buf.release();
		}
	}
}
