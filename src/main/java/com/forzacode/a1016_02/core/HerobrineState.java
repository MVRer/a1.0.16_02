package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Shared, per-world facts about him. One instance per world, stored in the server's data storage as
 * {@code data/a1016_02/state.dat}. Read it with {@link #get(MinecraftServer)}. Change it only through its methods,
 * which mark it dirty. Attention and tension change through {@link Attention}.
 * Use {@link #setFlag} for small namespaced facts ({@code "lore:f04_done"}) instead of asking for a new field.
 */
public final class HerobrineState extends SavedData {
	/** The subject: the first player who joined (D-002). */
	public record Subject(UUID uuid, String name) {
		static final Codec<Subject> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(Subject::uuid),
				Codec.STRING.fieldOf("name").forGetter(Subject::name)
		).apply(i, Subject::new));
	}

	/** Client effects that persist and are sent again on every join. */
	public record Effects(boolean musicOff, float duskFogLevel) {
		public static final Effects NONE = new Effects(false, 0.0F);
		static final Codec<Effects> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("musicOff", false).forGetter(Effects::musicOff),
				Codec.FLOAT.optionalFieldOf("duskFogLevel", 0.0F).forGetter(Effects::duskFogLevel)
		).apply(i, Effects::new));
	}

	/** The subject's first placed block, first crafting table and first chest. Each may be null. */
	public record FirstBlocks(@Nullable PlacedBlock block, @Nullable PlacedBlock craftingTable, @Nullable PlacedBlock chest) {
		static final FirstBlocks NONE = new FirstBlocks(null, null, null);
		static final Codec<FirstBlocks> CODEC = RecordCodecBuilder.create(i -> i.group(
				PlacedBlock.CODEC.optionalFieldOf("block").forGetter(f -> Optional.ofNullable(f.block())),
				PlacedBlock.CODEC.optionalFieldOf("craftingTable").forGetter(f -> Optional.ofNullable(f.craftingTable())),
				PlacedBlock.CODEC.optionalFieldOf("chest").forGetter(f -> Optional.ofNullable(f.chest()))
		).apply(i, (a, b, c) -> new FirstBlocks(a.orElse(null), b.orElse(null), c.orElse(null))));
	}

	/** Which first block to record. */
	public enum FirstKind { BLOCK, CRAFTING_TABLE, CHEST }

	record Clock(long playTicks, long warpDays) {
		static final Codec<Clock> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("playTicks", 0L).forGetter(Clock::playTicks),
				Codec.LONG.optionalFieldOf("warpDays", 0L).forGetter(Clock::warpDays)
		).apply(i, Clock::new));
	}

	public static final Codec<HerobrineState> CODEC = RecordCodecBuilder.create(i -> i.group(
			CoreCodecs.enumCodec(Stage.class).optionalFieldOf("stage", Stage.ALONE).forGetter(s -> s.stage),
			Codec.DOUBLE.optionalFieldOf("attention", 0.0).forGetter(s -> s.attention),
			Codec.DOUBLE.optionalFieldOf("tension", 0.0).forGetter(s -> s.tension),
			Codec.LONG.optionalFieldOf("salt", 0L).forGetter(s -> s.salt),
			WorldProfile.CODEC.optionalFieldOf("profile").forGetter(s -> Optional.ofNullable(s.profile)),
			Subject.CODEC.optionalFieldOf("subject").forGetter(s -> Optional.ofNullable(s.subject)),
			Clock.CODEC.optionalFieldOf("clock", new Clock(0, 0)).forGetter(s -> new Clock(s.playTicks, s.warpDays)),
			Codec.BOOL.optionalFieldOf("stopFired", false).forGetter(s -> s.stopFired),
			Codec.BOOL.optionalFieldOf("listRead", false).forGetter(s -> s.listRead),
			Codec.BOOL.optionalFieldOf("tellingStarted", false).forGetter(s -> s.tellingStarted),
			CoreCodecs.setOf(Codec.STRING).optionalFieldOf("fragmentsRead", Set.of()).forGetter(s -> s.fragmentsRead),
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("fragmentsPlaced", Map.of()).forGetter(s -> s.fragmentsPlaced),
			MarkedDeath.CODEC.listOf().optionalFieldOf("markedDeaths", List.of()).forGetter(s -> s.markedDeaths),
			FirstBlocks.CODEC.optionalFieldOf("firstBlocks", FirstBlocks.NONE).forGetter(s -> s.firstBlocks),
			Effects.CODEC.optionalFieldOf("effects", Effects.NONE).forGetter(s -> s.effects),
			CoreCodecs.setOf(Codec.STRING).optionalFieldOf("flags", Set.of()).forGetter(s -> s.flags)
	).apply(i, HerobrineState::new));

	public static final SavedDataType<HerobrineState> TYPE = new SavedDataType<>(A1016_02.id("state"), HerobrineState::new, CODEC, null);

	private Stage stage = Stage.ALONE;
	private double attention;
	private double tension;
	private long salt;
	private @Nullable WorldProfile profile;
	private @Nullable Subject subject;
	private long playTicks;
	private long warpDays;
	private boolean stopFired;
	private boolean listRead;
	private boolean tellingStarted;
	private final Set<String> fragmentsRead = new TreeSet<>();
	private final Map<String, GlobalPos> fragmentsPlaced = new HashMap<>();
	private final List<MarkedDeath> markedDeaths = new ArrayList<>();
	private FirstBlocks firstBlocks = FirstBlocks.NONE;
	private Effects effects = Effects.NONE;
	private final Set<String> flags = new TreeSet<>();

	/** A fresh state. The profile is rolled on the first {@link #get(MinecraftServer)}. */
	public HerobrineState() {
	}

	private HerobrineState(Stage stage, double attention, double tension, long salt, Optional<WorldProfile> profile,
			Optional<Subject> subject, Clock clock, boolean stopFired, boolean listRead, boolean tellingStarted,
			Set<String> fragmentsRead, Map<String, GlobalPos> fragmentsPlaced, List<MarkedDeath> markedDeaths,
			FirstBlocks firstBlocks, Effects effects, Set<String> flags) {
		this.stage = stage;
		this.attention = attention;
		this.tension = tension;
		this.salt = salt;
		this.profile = profile.orElse(null);
		this.subject = subject.orElse(null);
		this.playTicks = clock.playTicks();
		this.warpDays = clock.warpDays();
		this.stopFired = stopFired;
		this.listRead = listRead;
		this.tellingStarted = tellingStarted;
		this.fragmentsRead.addAll(fragmentsRead);
		this.fragmentsPlaced.putAll(fragmentsPlaced);
		this.markedDeaths.addAll(markedDeaths);
		this.firstBlocks = firstBlocks;
		this.effects = effects;
		this.flags.addAll(flags);
	}

	/** The world's state. Rolls the profile with a new random salt the first time (D-008). Server thread only. */
	public static HerobrineState get(MinecraftServer server) {
		HerobrineState state = server.getDataStorage().computeIfAbsent(TYPE);
		if (state.profile == null) {
			state.reroll(server.getWorldGenSettings().options().seed(), ThreadLocalRandom.current().nextLong());
		}
		return state;
	}

	// --- stage ---

	public Stage stage() {
		return stage;
	}

	/** Sets the stage and fires {@link HerobrineEvents#STAGE_CHANGED} if it changed. */
	public void setStage(MinecraftServer server, Stage newStage) {
		Stage old = stage;
		if (old == newStage) {
			return;
		}
		stage = newStage;
		setDirty();
		A1016_02.LOGGER.info("[a1016] stage {} -> {}", old, newStage);
		HerobrineEvents.STAGE_CHANGED.invoker().onStageChanged(server, old, newStage);
	}

	// --- attention and tension (change through Attention) ---

	/** Hidden attention, 0 to 100. */
	public double attention() {
		return attention;
	}

	/** Director tension, 0 to 100. */
	public double tension() {
		return tension;
	}

	void addAttention(double delta) {
		attention = Mth.clamp(attention + delta, 0.0, 100.0);
		setDirty();
	}

	void addTension(double delta) {
		tension = Mth.clamp(tension + delta, 0.0, 100.0);
		setDirty();
	}

	// --- profile ---

	/** Never null after {@link #get(MinecraftServer)}. */
	public WorldProfile profile() {
		return profile;
	}

	long salt() {
		return salt;
	}

	/** Makes a new salt and rolls a new profile (D-008, {@code /a1016 profile reroll}). */
	public void reroll(long seed, long newSalt) {
		salt = newSalt;
		profile = WorldProfile.roll(seed, newSalt);
		setDirty();
	}

	// --- subject ---

	public Optional<Subject> subject() {
		return Optional.ofNullable(subject);
	}

	void setSubject(UUID uuid, String name) {
		subject = new Subject(uuid, name);
		setDirty();
	}

	// --- clock (GameClock) ---

	long playTicks() {
		return playTicks;
	}

	long warpDays() {
		return warpDays;
	}

	void addPlayTicks(long ticks) {
		playTicks += ticks;
		setDirty();
	}

	void addWarpDays(long days) {
		warpDays += days;
		setDirty();
	}

	// --- story flags ---

	public boolean stopFired() {
		return stopFired;
	}

	public void setStopFired(boolean value) {
		stopFired = value;
		setDirty();
	}

	public boolean listRead() {
		return listRead;
	}

	public void setListRead(boolean value) {
		listRead = value;
		setDirty();
	}

	public boolean tellingStarted() {
		return tellingStarted;
	}

	public void setTellingStarted(boolean value) {
		tellingStarted = value;
		setDirty();
	}

	// --- fragments ---

	/** Read fragment ids ("F01".."F30"). Unmodifiable. */
	public Set<String> fragmentsRead() {
		return Collections.unmodifiableSet(fragmentsRead);
	}

	/** @return true if this is the first time the fragment was read */
	public boolean markFragmentRead(String id) {
		boolean added = fragmentsRead.add(id);
		if (added) {
			setDirty();
		}
		return added;
	}

	/** Where each placed fragment is. Unmodifiable. */
	public Map<String, GlobalPos> fragmentsPlaced() {
		return Collections.unmodifiableMap(fragmentsPlaced);
	}

	public void setFragmentPlaced(String id, @Nullable GlobalPos pos) {
		if (pos == null) {
			fragmentsPlaced.remove(id);
		} else {
			fragmentsPlaced.put(id, pos);
		}
		setDirty();
	}

	// --- marked deaths ---

	public List<MarkedDeath> markedDeaths() {
		return Collections.unmodifiableList(markedDeaths);
	}

	public void addMarkedDeath(MarkedDeath death) {
		markedDeaths.add(death);
		setDirty();
	}

	// --- first blocks (recorded by PlayerWatch) ---

	public FirstBlocks firstBlocks() {
		return firstBlocks;
	}

	/** Records a first block if that slot is still empty. */
	void recordFirst(FirstKind kind, PlacedBlock block) {
		FirstBlocks old = firstBlocks;
		firstBlocks = switch (kind) {
			case BLOCK -> old.block() == null ? new FirstBlocks(block, old.craftingTable(), old.chest()) : old;
			case CRAFTING_TABLE -> old.craftingTable() == null ? new FirstBlocks(old.block(), block, old.chest()) : old;
			case CHEST -> old.chest() == null ? new FirstBlocks(old.block(), old.craftingTable(), block) : old;
		};
		if (firstBlocks != old) {
			setDirty();
		}
	}

	// --- effects (use ClientEffects to change and send) ---

	public Effects effects() {
		return effects;
	}

	void setEffects(Effects newEffects) {
		effects = newEffects;
		setDirty();
	}

	// --- free flags ---

	/** Free namespaced flags, for example {@code "lore:f04_done"}. Unmodifiable. */
	public Set<String> flags() {
		return Collections.unmodifiableSet(flags);
	}

	public boolean hasFlag(String flag) {
		return flags.contains(flag);
	}

	public void setFlag(String flag, boolean value) {
		boolean changed = value ? flags.add(flag) : flags.remove(flag);
		if (changed) {
			setDirty();
		}
	}
}
