package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 * <p>
 * One story per world (D-058), personal records per player (D-061): everyone who has ever been a participant is a
 * known player ({@link #players()}, in first-join order) with their own first blocks, play ticks and tension. A
 * player who first takes part later starts with empty records and zero play ticks. A save written by v1.0 (one
 * {@code subject} with top-level {@code firstBlocks} and {@code tension}) loads as one known player holding those
 * records and the world's play ticks (D-062.8).
 */
public final class HerobrineState extends SavedData {
	/** A known player: their UUID and their name as of their latest join. */
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

	/** A player's first placed block, first crafting table and first chest. Each may be null. */
	public record FirstBlocks(@Nullable PlacedBlock block, @Nullable PlacedBlock craftingTable, @Nullable PlacedBlock chest) {
		public static final FirstBlocks NONE = new FirstBlocks(null, null, null);
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

	/** One known player's own records (D-061). Mutable; changed only through the state's methods. */
	static final class PlayerRecord {
		static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(r -> r.uuid),
				Codec.STRING.fieldOf("name").forGetter(r -> r.name),
				FirstBlocks.CODEC.optionalFieldOf("firstBlocks", FirstBlocks.NONE).forGetter(r -> r.firstBlocks),
				Codec.LONG.optionalFieldOf("playTicks", 0L).forGetter(r -> r.playTicks),
				Codec.DOUBLE.optionalFieldOf("tension", 0.0).forGetter(r -> r.tension)
		).apply(i, PlayerRecord::new));

		final UUID uuid;
		String name;
		FirstBlocks firstBlocks;
		long playTicks;
		double tension;

		PlayerRecord(UUID uuid, String name, FirstBlocks firstBlocks, long playTicks, double tension) {
			this.uuid = uuid;
			this.name = name;
			this.firstBlocks = firstBlocks;
			this.playTicks = playTicks;
			this.tension = Mth.clamp(tension, 0.0, 100.0);
		}
	}

	/**
	 * The players, stored as top-level fields: {@code players} (the known players in first-join order, each with
	 * their records) and {@code owners} (D-061's stored choices). {@code subject}, {@code firstBlocks} and
	 * {@code tension} are v1.0's single-subject fields: read for the migration, never written.
	 */
	record People(List<PlayerRecord> players, Map<String, UUID> owners, Optional<Subject> legacySubject,
			Optional<FirstBlocks> legacyFirstBlocks, Optional<Double> legacyTension) {
		static final MapCodec<People> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				PlayerRecord.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(People::players),
				Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC).optionalFieldOf("owners", Map.of()).forGetter(People::owners),
				Subject.CODEC.optionalFieldOf("subject").forGetter(People::legacySubject),
				FirstBlocks.CODEC.optionalFieldOf("firstBlocks").forGetter(People::legacyFirstBlocks),
				Codec.DOUBLE.optionalFieldOf("tension").forGetter(People::legacyTension)
		).apply(i, People::new));
	}

	/**
	 * The story facts, stored as top-level fields ({@code stopFired}, {@code listRead}, {@code tellingStarted},
	 * {@code tellingCount}). A world saved before {@code tellingCount} existed reads it from lore's
	 * {@link #TELLING_COUNT_FLAG} flag.
	 */
	record Story(boolean stopFired, boolean listRead, boolean tellingStarted, Optional<Integer> tellingCount) {
		static final MapCodec<Story> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.BOOL.optionalFieldOf("stopFired", false).forGetter(Story::stopFired),
				Codec.BOOL.optionalFieldOf("listRead", false).forGetter(Story::listRead),
				Codec.BOOL.optionalFieldOf("tellingStarted", false).forGetter(Story::tellingStarted),
				Codec.INT.optionalFieldOf("tellingCount").forGetter(Story::tellingCount)
		).apply(i, Story::new));
	}

	/** The profile salt and the worldgen salt, stored as two top-level fields ({@code salt}, {@code worldgenSalt}). */
	record Salts(long profile, Optional<Long> worldgen) {
		static final MapCodec<Salts> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.LONG.optionalFieldOf("salt", 0L).forGetter(Salts::profile),
				Codec.LONG.optionalFieldOf("worldgenSalt").forGetter(Salts::worldgen)
		).apply(i, Salts::new));
	}

	public static final Codec<HerobrineState> CODEC = RecordCodecBuilder.create(i -> i.group(
			CoreCodecs.enumCodec(Stage.class).optionalFieldOf("stage", Stage.ALONE).forGetter(s -> s.stage),
			Codec.DOUBLE.optionalFieldOf("attention", 0.0).forGetter(s -> s.attention),
			Salts.MAP_CODEC.forGetter(s -> new Salts(s.salt, s.worldgenSaltSet ? Optional.of(s.worldgenSalt) : Optional.empty())),
			WorldProfile.CODEC.optionalFieldOf("profile").forGetter(s -> Optional.ofNullable(s.profile)),
			People.MAP_CODEC.forGetter(s -> new People(List.copyOf(s.players.values()), new TreeMap<>(s.owners), Optional.empty(), Optional.empty(),
					Optional.empty())),
			Clock.CODEC.optionalFieldOf("clock", new Clock(0, 0)).forGetter(s -> new Clock(s.playTicks, s.warpDays)),
			Story.MAP_CODEC.forGetter(s -> new Story(s.stopFired, s.listRead, s.tellingStarted, Optional.of(s.tellingCount))),
			CoreCodecs.setOf(Codec.STRING).optionalFieldOf("fragmentsRead", Set.of()).forGetter(s -> s.fragmentsRead),
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("fragmentsPlaced", Map.of()).forGetter(s -> s.fragmentsPlaced),
			MarkedDeath.CODEC.listOf().optionalFieldOf("markedDeaths", List.of()).forGetter(s -> s.markedDeaths),
			Effects.CODEC.optionalFieldOf("effects", Effects.NONE).forGetter(s -> s.effects),
			CoreCodecs.setOf(Codec.STRING).optionalFieldOf("flags", Set.of()).forGetter(s -> s.flags)
	).apply(i, HerobrineState::new));

	public static final SavedDataType<HerobrineState> TYPE = new SavedDataType<>(A1016_02.id("state"), HerobrineState::new, CODEC, null);
	/**
	 * The telling count is mirrored in {@link #flags()} as {@code lore:telling_count=<n>} (D-039), kept in sync by
	 * {@link #incrementTellingCount()} for readers of the flag.
	 */
	public static final String TELLING_COUNT_FLAG = "lore:telling_count";
	/**
	 * D-062.8: the stored owners a v1.0 save's one subject adopts when it loads (if they placed a block): the story's
	 * first blocks and home ({@code story:first_blocks}, lore's and the ending's) and the group's home
	 * ({@code ending:home}). Without them the friend who happened to be online first would take over a story that was
	 * the old player's.
	 */
	public static final List<String> V1_OWNER_KEYS = List.of("story:first_blocks", "ending:home");

	private Stage stage = Stage.ALONE;
	private double attention;
	private long salt;
	/** Fixed once set (volatile so worldgen threads that cached the state read it safely). */
	private volatile long worldgenSalt;
	private volatile boolean worldgenSaltSet;
	private @Nullable WorldProfile profile;
	/** The known players in first-join order, by UUID (D-061). */
	private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
	/** Stored owner choices (D-061): key to player. */
	private final Map<String, UUID> owners = new HashMap<>();
	/** The world clock: ticks while at least one participant is online, plus timewarp. */
	private long playTicks;
	private long warpDays;
	private boolean stopFired;
	private boolean listRead;
	private boolean tellingStarted;
	private int tellingCount;
	private final Set<String> fragmentsRead = new TreeSet<>();
	private final Map<String, GlobalPos> fragmentsPlaced = new HashMap<>();
	private final List<MarkedDeath> markedDeaths = new ArrayList<>();
	private Effects effects = Effects.NONE;
	private final Set<String> flags = new TreeSet<>();

	/** A fresh state. The profile is rolled on the first {@link #get(MinecraftServer)}. */
	public HerobrineState() {
	}

	private HerobrineState(Stage stage, double attention, Salts salts, Optional<WorldProfile> profile, People people, Clock clock,
			Story story, Set<String> fragmentsRead, Map<String, GlobalPos> fragmentsPlaced, List<MarkedDeath> markedDeaths, Effects effects,
			Set<String> flags) {
		this.stage = stage;
		this.attention = attention;
		this.salt = salts.profile();
		salts.worldgen().ifPresent(value -> {
			this.worldgenSalt = value;
			this.worldgenSaltSet = true;
		});
		this.profile = profile.orElse(null);
		for (PlayerRecord record : people.players()) {
			this.players.putIfAbsent(record.uuid, record);
		}
		this.owners.putAll(people.owners());
		if (this.players.isEmpty()) {
			// A v1.0 save (D-062.8): its one subject becomes the first known player, with the old first blocks, the
			// old tension and the world's play ticks (they were the subject's). The story so far was theirs, so a
			// subject who placed a block also keeps the story's home (V1_OWNER_KEYS), whoever comes online first.
			people.legacySubject().ifPresent(subject -> {
				PlayerRecord record = new PlayerRecord(subject.uuid(), subject.name(), people.legacyFirstBlocks().orElse(FirstBlocks.NONE),
						clock.playTicks(), people.legacyTension().orElse(0.0));
				this.players.put(subject.uuid(), record);
				if (record.firstBlocks.block() != null) {
					V1_OWNER_KEYS.forEach(key -> this.owners.putIfAbsent(key, subject.uuid()));
				}
			});
		}
		this.playTicks = clock.playTicks();
		this.warpDays = clock.warpDays();
		this.stopFired = story.stopFired();
		this.listRead = story.listRead();
		this.tellingStarted = story.tellingStarted();
		this.tellingCount = Math.max(0, story.tellingCount().orElseGet(() -> countFromFlag(flags)));
		this.fragmentsRead.addAll(fragmentsRead);
		this.fragmentsPlaced.putAll(fragmentsPlaced);
		// Deaths saved before deaths recorded whose they were belong to the first known player (D-063).
		markedDeaths.forEach(this::loadDeath);
		this.effects = effects;
		this.flags.addAll(flags);
	}

	/** The world's state. Rolls the profile with a new random salt the first time (D-008). Server thread only. */
	public static HerobrineState get(MinecraftServer server) {
		HerobrineState state = server.getDataStorage().computeIfAbsent(TYPE);
		if (state.profile == null) {
			state.reroll(server.getWorldGenSettings().options().seed(), ThreadLocalRandom.current().nextLong());
		}
		if (!state.worldgenSaltSet) {
			state.fixWorldgenSalt();
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

	/** Hidden attention, 0 to 100. One value for the world (D-058). */
	public double attention() {
		return attention;
	}

	/** That player's director tension, 0 to 100 (D-059); 0 for someone who was never a participant. */
	public double tension(UUID player) {
		PlayerRecord record = players.get(player);
		return record == null ? 0.0 : record.tension;
	}

	void addAttention(double delta) {
		attention = Mth.clamp(attention + delta, 0.0, 100.0);
		setDirty();
	}

	/** Changes a known player's tension (clamped to 0..100). Does nothing for someone who was never a participant. */
	void addTension(UUID player, double delta) {
		PlayerRecord record = players.get(player);
		if (record != null) {
			record.tension = Mth.clamp(record.tension + delta, 0.0, 100.0);
			setDirty();
		}
	}

	// --- profile ---

	/** Never null after {@link #get(MinecraftServer)}. */
	public WorldProfile profile() {
		return profile;
	}

	long salt() {
		return salt;
	}

	/**
	 * A salt for worldgen hashing, derived from the profile salt the first time the state is read and then fixed for
	 * the life of the world: {@link #reroll} does not change it, so chunks generated before and after a reroll agree.
	 * Hash it together with the seed (for example {@code mix(seed ^ worldgenSalt())}). Read it on the server thread
	 * at server start and hand the value to worldgen threads.
	 */
	public long worldgenSalt() {
		if (!worldgenSaltSet) {
			fixWorldgenSalt();
		}
		return worldgenSalt;
	}

	/** Derives the worldgen salt from the profile salt (SplitMix64 finalizer, so it is not the profile salt itself). */
	static long deriveWorldgenSalt(long profileSalt) {
		long z = profileSalt + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private synchronized void fixWorldgenSalt() {
		if (!worldgenSaltSet) {
			worldgenSalt = deriveWorldgenSalt(salt);
			worldgenSaltSet = true;
			setDirty();
		}
	}

	/** Makes a new salt and rolls a new profile (D-008, {@code /a1016 profile reroll}). The worldgen salt stays. */
	public void reroll(long seed, long newSalt) {
		salt = newSalt;
		profile = WorldProfile.roll(seed, newSalt);
		setDirty();
	}

	// --- known players (D-061; recorded by PlayerWatch) ---

	/** Everyone who has been a participant here, in first-join order, with their latest name. Unmodifiable snapshot. */
	public List<Subject> players() {
		List<Subject> list = new ArrayList<>(players.size());
		players.values().forEach(r -> list.add(new Subject(r.uuid, r.name)));
		return Collections.unmodifiableList(list);
	}

	/** That known player, or empty if they were never a participant here. */
	public Optional<Subject> player(UUID uuid) {
		PlayerRecord record = players.get(uuid);
		return record == null ? Optional.empty() : Optional.of(new Subject(record.uuid, record.name));
	}

	/**
	 * Adds a known player at the end of the first-join order, with empty records and zero play ticks, or updates
	 * the name of one already known. Core calls it once the player is a participant.
	 *
	 * @return true if the player is new
	 */
	boolean recordPlayer(UUID uuid, String name) {
		PlayerRecord record = players.get(uuid);
		if (record == null) {
			players.put(uuid, new PlayerRecord(uuid, name, FirstBlocks.NONE, 0, 0.0));
			setDirty();
			return true;
		}
		updateName(uuid, name);
		return false;
	}

	/**
	 * Tests and debug only (game code never calls it: core records each player itself once they take part): makes
	 * {@code uuid} a known player of this state, as {@link #recordPlayer} does, at the end of the first-join order with
	 * empty records and zero play ticks. A player already known keeps their place and records and only takes the new
	 * name. For two-player tests over a private state, in any package.
	 *
	 * @return true if the player is new
	 */
	public boolean addKnownPlayer(UUID uuid, String name) {
		return addKnownPlayer(uuid, name, FirstBlocks.NONE);
	}

	/**
	 * Tests and debug only: {@link #addKnownPlayer(UUID, String)}, then these first blocks as theirs, each only where
	 * that slot is still empty (as their own placements would record them; null leaves a slot as it is).
	 *
	 * @return true if the player is new
	 */
	public boolean addKnownPlayer(UUID uuid, String name, FirstBlocks firstBlocks) {
		boolean added = recordPlayer(uuid, name);
		if (firstBlocks.block() != null) {
			recordFirst(uuid, FirstKind.BLOCK, firstBlocks.block());
		}
		if (firstBlocks.craftingTable() != null) {
			recordFirst(uuid, FirstKind.CRAFTING_TABLE, firstBlocks.craftingTable());
		}
		if (firstBlocks.chest() != null) {
			recordFirst(uuid, FirstKind.CHEST, firstBlocks.chest());
		}
		return added;
	}

	/** Updates a known player's name. Does nothing for someone unknown. */
	void updateName(UUID uuid, String name) {
		PlayerRecord record = players.get(uuid);
		if (record != null && !record.name.equals(name)) {
			record.name = name;
			setDirty();
		}
	}

	/** The player's place in the first-join order (0 is the first), or -1 if unknown. */
	int joinOrder(UUID uuid) {
		int index = 0;
		for (UUID known : players.keySet()) {
			if (known.equals(uuid)) {
				return index;
			}
			index++;
		}
		return -1;
	}

	private Optional<PlayerRecord> firstPlayer() {
		return players.values().stream().findFirst();
	}

	// --- owners (D-061; chosen by PlayerWatch.ownerFor) ---

	/** The stored owner of a once-per-world thing (a key like {@code "world:house_copy"}), or empty if none was chosen. */
	public Optional<UUID> owner(String key) {
		return Optional.ofNullable(owners.get(key));
	}

	/** Every stored owner choice. Unmodifiable. */
	public Map<String, UUID> owners() {
		return Collections.unmodifiableMap(owners);
	}

	/** Stores a choice if none is stored for the key yet; a stored choice never changes. Returns the stored owner. */
	UUID setOwner(String key, UUID player) {
		UUID stored = owners.putIfAbsent(key, player);
		if (stored != null) {
			return stored;
		}
		setDirty();
		return player;
	}

	/** Debug and tests only: forgets one owner choice, so the next {@code PlayerWatch.ownerFor} chooses again. */
	public void clearOwner(String key) {
		if (owners.remove(key) != null) {
			setDirty();
		}
	}

	// --- clock (GameClock) ---

	/** The world clock. */
	long playTicks() {
		return playTicks;
	}

	/** That player's own play ticks; 0 for someone unknown. */
	long playTicks(UUID player) {
		PlayerRecord record = players.get(player);
		return record == null ? 0 : record.playTicks;
	}

	long warpDays() {
		return warpDays;
	}

	/** Advances the world clock. */
	void addPlayTicks(long ticks) {
		playTicks += ticks;
		setDirty();
	}

	/** Advances one known player's clock. Does nothing for someone unknown. */
	void addPlayTicks(UUID player, long ticks) {
		PlayerRecord record = players.get(player);
		if (record != null) {
			record.playTicks += ticks;
			setDirty();
		}
	}

	/** Advances every known player's clock (timewarp). */
	void addPlayTicksToAll(long ticks) {
		players.values().forEach(r -> r.playTicks += ticks);
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

	/**
	 * How many times a player told about him: signs and books that name him or were written near his traces, and
	 * chat that names him (D-039). Lore counts; "the more you tell, the faster things go".
	 */
	public int tellingCount() {
		return tellingCount;
	}

	/** Counts one telling and keeps the one {@link #TELLING_COUNT_FLAG} flag in sync. Returns the new count. */
	public int incrementTellingCount() {
		tellingCount++;
		for (String flag : List.copyOf(flags)) {
			if (flag.startsWith(TELLING_COUNT_FLAG + "=")) {
				flags.remove(flag);
			}
		}
		flags.add(TELLING_COUNT_FLAG + "=" + tellingCount);
		setDirty();
		return tellingCount;
	}

	/** The count in a {@link #TELLING_COUNT_FLAG} flag (worlds saved before the field), or 0. */
	private static int countFromFlag(Set<String> flags) {
		for (String flag : flags) {
			if (flag.startsWith(TELLING_COUNT_FLAG + "=")) {
				try {
					return Integer.parseInt(flag.substring(TELLING_COUNT_FLAG.length() + 1));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	// --- fragments ---

	/** Fragment ids read by anyone (one set for the world, D-058): story fragments ("F01".."F30") and clues past F30. Unmodifiable. */
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

	/** Every marked death in the world, oldest first: one shared list (D-063). Unmodifiable. */
	public List<MarkedDeath> markedDeaths() {
		return Collections.unmodifiableList(markedDeaths);
	}

	/** That player's own marked deaths, oldest first. */
	public List<MarkedDeath> markedDeaths(UUID player) {
		return markedDeaths.stream().filter(death -> player.equals(death.player())).toList();
	}

	/** Records a marked death as {@code player}'s (whichever player the record carried). */
	public void addMarkedDeath(UUID player, MarkedDeath death) {
		markedDeaths.add(death.withPlayer(player));
		setDirty();
	}

	/** Loading only (D-062.8): a saved death that records no player (v1.0) is the first known player's. */
	private void loadDeath(MarkedDeath death) {
		Optional<PlayerRecord> first = firstPlayer();
		markedDeaths.add(death.player() == null && first.isPresent() ? death.withPlayer(first.get().uuid) : death);
	}

	// --- first blocks (recorded by PlayerWatch) ---

	/** That player's first blocks; {@link FirstBlocks#NONE} for someone who was never a participant. */
	public FirstBlocks firstBlocks(UUID player) {
		PlayerRecord record = players.get(player);
		return record == null ? FirstBlocks.NONE : record.firstBlocks;
	}

	/**
	 * True if this known player has placed a block (they have a first block). The one rule for who may become the
	 * story's first-blocks owner ({@code story:first_blocks}): every workstream that chooses it passes
	 * {@code state::hasPlacedBlock} to {@code PlayerWatch.ownerFor}, so F13 never waits on someone with no first block.
	 */
	public boolean hasPlacedBlock(UUID player) {
		return firstBlocks(player).block() != null;
	}

	/** Records one of a known player's first blocks if that slot is still empty. Nothing for someone unknown. */
	void recordFirst(UUID player, FirstKind kind, PlacedBlock block) {
		PlayerRecord record = players.get(player);
		if (record == null) {
			return;
		}
		FirstBlocks old = record.firstBlocks;
		record.firstBlocks = switch (kind) {
			case BLOCK -> old.block() == null ? new FirstBlocks(block, old.craftingTable(), old.chest()) : old;
			case CRAFTING_TABLE -> old.craftingTable() == null ? new FirstBlocks(old.block(), block, old.chest()) : old;
			case CHEST -> old.chest() == null ? new FirstBlocks(old.block(), old.craftingTable(), block) : old;
		};
		if (record.firstBlocks != old) {
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
