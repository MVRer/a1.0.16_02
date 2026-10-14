package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * What the players told, per world ({@code data/a1016_02/lore_telling.dat}): one story, whoever told (D-058).
 * Shared facts ({@code tellingStarted}, {@code stopFired}) live in {@code HerobrineState}; the telling count is
 * mirrored there as the flag {@code lore:telling_count=<n>} (see {@link LoreApi#tellingCount}).
 * <ul>
 * <li>signs: every sign a player wrote about him, and every sign written after the first telling (blank sign's
 * pool), with everyone who wrote on it</li>
 * <li>books: books written about him, by the id stamped into the item ({@link Telling#BOOK_MARKER}), with everyone
 * who wrote in it</li>
 * <li>the "Stop." sign: the candidate (first sign about him written in Stage 3) and, once it fired, the sign</li>
 * <li>visited: placed fragments in the order any participant first came near them (place not found takes the first)</li>
 * <li>the list in lava: lists burned, pyramids still to raise, and where</li>
 * <li>the list's lines: each player's latest cause (by UUID), whose marked death came last, and whose names F10's
 * Ending B lines finish (one line per finished player, in the order they were finished)</li>
 * </ul>
 */
public final class TellingData extends SavedData {
	/**
	 * A sign a player wrote.
	 *
	 * @param writer       who wrote it first
	 * @param coWriters    everyone else who wrote on it since (it is theirs too: a friend's line on your sign)
	 * @param seq          order of writing in this world
	 * @param aboutHim     names him or was written near his traces
	 * @param afterTelling written after the first telling (blank sign may take it)
	 * @param blanked      he already blanked it
	 */
	public record WrittenSign(GlobalPos pos, UUID writer, List<UUID> coWriters, String text, boolean aboutHim, boolean namesHim, long seq,
			long day, boolean afterTelling, boolean blanked) {
		static final Codec<WrittenSign> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(WrittenSign::pos),
				UUIDUtil.CODEC.fieldOf("writer").forGetter(WrittenSign::writer),
				UUIDUtil.CODEC.listOf().optionalFieldOf("coWriters", List.of()).forGetter(WrittenSign::coWriters),
				Codec.STRING.optionalFieldOf("text", "").forGetter(WrittenSign::text),
				Codec.BOOL.optionalFieldOf("aboutHim", false).forGetter(WrittenSign::aboutHim),
				Codec.BOOL.optionalFieldOf("namesHim", false).forGetter(WrittenSign::namesHim),
				Codec.LONG.fieldOf("seq").forGetter(WrittenSign::seq),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(WrittenSign::day),
				Codec.BOOL.optionalFieldOf("afterTelling", false).forGetter(WrittenSign::afterTelling),
				Codec.BOOL.optionalFieldOf("blanked", false).forGetter(WrittenSign::blanked)
		).apply(i, WrittenSign::new));

		public WrittenSign {
			coWriters = coWriters.stream().filter(uuid -> !uuid.equals(writer)).distinct().toList();
		}

		/** A sign with one writer. */
		public WrittenSign(GlobalPos pos, UUID writer, String text, boolean aboutHim, boolean namesHim, long seq, long day, boolean afterTelling,
				boolean blanked) {
			this(pos, writer, List.of(), text, aboutHim, namesHim, seq, day, afterTelling, blanked);
		}

		/** True if this player wrote on it (first or since). */
		public boolean writtenBy(UUID player) {
			return writer.equals(player) || coWriters.contains(player);
		}

		/** Everyone who wrote on it: the first writer, then the others in order. */
		public List<UUID> writers() {
			return TellingData.writers(writer, coWriters);
		}

		WrittenSign withBlanked(boolean value) {
			return new WrittenSign(pos, writer, coWriters, text, aboutHim, namesHim, seq, day, afterTelling, value);
		}

		WrittenSign movedTo(GlobalPos to) {
			return new WrittenSign(to, writer, coWriters, text, aboutHim, namesHim, seq, day, afterTelling, blanked);
		}
	}

	/**
	 * A book a player wrote about him, by the id stamped into it. {@code textHash} skips saves that change nothing.
	 * {@code writer} wrote it first; {@code coWriters} wrote in it since (a friend's page in your book).
	 */
	public record WrittenBook(String id, UUID writer, List<UUID> coWriters, boolean namesHim, boolean signed, long seq, long day, int textHash) {
		static final Codec<WrittenBook> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(WrittenBook::id),
				UUIDUtil.CODEC.fieldOf("writer").forGetter(WrittenBook::writer),
				UUIDUtil.CODEC.listOf().optionalFieldOf("coWriters", List.of()).forGetter(WrittenBook::coWriters),
				Codec.BOOL.optionalFieldOf("namesHim", false).forGetter(WrittenBook::namesHim),
				Codec.BOOL.optionalFieldOf("signed", false).forGetter(WrittenBook::signed),
				Codec.LONG.fieldOf("seq").forGetter(WrittenBook::seq),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(WrittenBook::day),
				Codec.INT.optionalFieldOf("textHash", 0).forGetter(WrittenBook::textHash)
		).apply(i, WrittenBook::new));

		public WrittenBook {
			coWriters = coWriters.stream().filter(uuid -> !uuid.equals(writer)).distinct().toList();
		}

		/** A book with one writer. */
		public WrittenBook(String id, UUID writer, boolean namesHim, boolean signed, long seq, long day, int textHash) {
			this(id, writer, List.of(), namesHim, signed, seq, day, textHash);
		}

		/** True if this player wrote in it (first or since). */
		public boolean writtenBy(UUID player) {
			return writer.equals(player) || coWriters.contains(player);
		}

		/** Everyone who wrote in it: the first writer, then the others in order. */
		public List<UUID> writers() {
			return TellingData.writers(writer, coWriters);
		}
	}

	/** {@code first}, then {@code others} (each once). */
	static List<UUID> writers(UUID first, List<UUID> others) {
		List<UUID> all = new ArrayList<>();
		all.add(first);
		others.stream().filter(uuid -> !all.contains(uuid)).forEach(all::add);
		return List.copyOf(all);
	}

	/**
	 * The list in lava: how many copies burned, how many pyramids he owes for them and has raised, near where the
	 * last one burned, where the ocean is searched from (that spot, or the burner's base when it burned outside the
	 * overworld), and the ocean he raises them in. A save without {@code from} searches from {@code near} when that
	 * is in the overworld, else from the story's home.
	 */
	public record Burned(int lists, int owed, int raised, Optional<GlobalPos> near, Optional<GlobalPos> ocean, Optional<GlobalPos> from) {
		static final Burned NONE = new Burned(0, 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
		static final Codec<Burned> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("lists", 0).forGetter(Burned::lists),
				Codec.INT.optionalFieldOf("owed", 0).forGetter(Burned::owed),
				Codec.INT.optionalFieldOf("raised", 0).forGetter(Burned::raised),
				GlobalPos.CODEC.optionalFieldOf("near").forGetter(Burned::near),
				GlobalPos.CODEC.optionalFieldOf("ocean").forGetter(Burned::ocean),
				GlobalPos.CODEC.optionalFieldOf("from").forGetter(Burned::from)
		).apply(i, Burned::new));

		/** Pyramids still to be raised. */
		public int pending() {
			return Math.max(0, owed - raised);
		}

		Burned withOcean(GlobalPos pos) {
			return new Burned(lists, owed, raised, near, Optional.of(pos), from);
		}

		Burned raisedOne() {
			return new Burned(lists, owed, raised + 1, near, ocean, from);
		}
	}

	/** The count itself lives in {@code HerobrineState.tellingCount()}; an old {@code count} key here is ignored. */
	private record Counters(long nextSeq, long firstTellingSeq, long firstTellingDay) {
		static final Codec<Counters> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("nextSeq", 0L).forGetter(Counters::nextSeq),
				Codec.LONG.optionalFieldOf("firstTellingSeq", -1L).forGetter(Counters::firstTellingSeq),
				Codec.LONG.optionalFieldOf("firstTellingDay", -1L).forGetter(Counters::firstTellingDay)
		).apply(i, Counters::new));
	}

	/**
	 * The list's lines and F10's last line (D-063), stored as top-level fields: the latest cause MARKED_DEATH gave
	 * for each player ({@code listCauses}), whose marked death came last ({@code lastMarked}), and whose name F10's
	 * Ending B lines finish, in order ({@code f10RemovedAll}; {@code f10Removed} keeps the first, the only one before
	 * F10 named every finished player, and is read when the list is missing). {@code listCause} is v1.0's one cause (the subject's): it is
	 * handed to the first known player on the first {@link #get}, and written only until then.
	 */
	private record Lines(Optional<String> legacyCause, Map<UUID, String> causes, Optional<UUID> lastMarked, Optional<UUID> f10Removed,
			List<UUID> f10RemovedAll) {
		static final MapCodec<Lines> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.optionalFieldOf("listCause").forGetter(Lines::legacyCause),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("listCauses", Map.of()).forGetter(Lines::causes),
				UUIDUtil.CODEC.optionalFieldOf("lastMarked").forGetter(Lines::lastMarked),
				UUIDUtil.CODEC.optionalFieldOf("f10Removed").forGetter(Lines::f10Removed),
				UUIDUtil.CODEC.listOf().optionalFieldOf("f10RemovedAll", List.of()).forGetter(Lines::f10RemovedAll)
		).apply(i, Lines::new));
	}

	public static final Codec<TellingData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Counters.CODEC.optionalFieldOf("counters", new Counters(0, -1, -1)).forGetter(d -> new Counters(d.nextSeq, d.firstTellingSeq,
					d.firstTellingDay)),
			WrittenSign.CODEC.listOf().optionalFieldOf("signs", List.of()).forGetter(d -> d.signs),
			WrittenBook.CODEC.listOf().optionalFieldOf("books", List.of()).forGetter(d -> d.books),
			GlobalPos.CODEC.optionalFieldOf("stopCandidate").forGetter(d -> Optional.ofNullable(d.stopCandidate)),
			GlobalPos.CODEC.optionalFieldOf("stopSign").forGetter(d -> Optional.ofNullable(d.stopSign)),
			Codec.STRING.listOf().optionalFieldOf("visited", List.of()).forGetter(d -> d.visited),
			Codec.STRING.optionalFieldOf("notFound").forGetter(d -> Optional.ofNullable(d.notFound)),
			GlobalPos.CODEC.listOf().optionalFieldOf("pendingBlanks", List.of()).forGetter(d -> d.pendingBlanks),
			Burned.CODEC.optionalFieldOf("burned", Burned.NONE).forGetter(d -> d.burned),
			Lines.MAP_CODEC.forGetter(d -> new Lines(Optional.ofNullable(d.legacyCause), new TreeMap<>(d.listCauses), Optional.ofNullable(d.lastMarked),
					d.f10Removed.stream().findFirst(), List.copyOf(d.f10Removed)))
	).apply(i, TellingData::new));

	public static final SavedDataType<TellingData> TYPE = new SavedDataType<>(A1016_02.id("lore_telling"), TellingData::new, CODEC, null);

	private long nextSeq;
	private long firstTellingSeq = -1;
	private long firstTellingDay = -1;
	private final List<WrittenSign> signs = new ArrayList<>();
	private final List<WrittenBook> books = new ArrayList<>();
	private @Nullable GlobalPos stopCandidate;
	private @Nullable GlobalPos stopSign;
	private final List<String> visited = new ArrayList<>();
	private @Nullable String notFound;
	private final List<GlobalPos> pendingBlanks = new ArrayList<>();
	private Burned burned = Burned.NONE;
	/** v1.0's single cause, until the first known player takes it ({@link #migrate}). */
	private @Nullable String legacyCause;
	/** The latest cause on each player's line of the list (D-062.2: by UUID, never "the player's"). */
	private final Map<UUID, String> listCauses = new HashMap<>();
	private @Nullable UUID lastMarked;
	/** Whose names F10's Ending B lines finish, in the order they were finished (D-062.2: by UUID). */
	private final List<UUID> f10Removed = new ArrayList<>();

	/** A fresh record (tests use their own). */
	public TellingData() {
	}

	private TellingData(Counters counters, List<WrittenSign> signs, List<WrittenBook> books, Optional<GlobalPos> stopCandidate,
			Optional<GlobalPos> stopSign, List<String> visited, Optional<String> notFound, List<GlobalPos> pendingBlanks, Burned burned,
			Lines lines) {
		this.nextSeq = counters.nextSeq();
		this.firstTellingSeq = counters.firstTellingSeq();
		this.firstTellingDay = counters.firstTellingDay();
		this.signs.addAll(signs);
		this.books.addAll(books);
		this.stopCandidate = stopCandidate.orElse(null);
		this.stopSign = stopSign.orElse(null);
		this.visited.addAll(visited);
		this.notFound = notFound.orElse(null);
		this.pendingBlanks.addAll(pendingBlanks);
		this.burned = burned;
		this.legacyCause = lines.legacyCause().orElse(null);
		this.listCauses.putAll(lines.causes());
		this.lastMarked = lines.lastMarked().orElse(null);
		this.f10Removed.addAll(lines.f10RemovedAll().isEmpty() ? lines.f10Removed().stream().toList() : lines.f10RemovedAll());
	}

	/** The world's record; an old save's single list cause is handed to its player first (D-062.8). */
	public static TellingData get(MinecraftServer server) {
		TellingData data = server.getDataStorage().computeIfAbsent(TYPE);
		if (data.legacyCause != null) {
			data.migrate(HerobrineState.get(server));
		}
		return data;
	}

	/**
	 * A v1.0 save kept one list cause, the subject's, and the subject is the first known player now (core's
	 * migration): it becomes their line's cause, unless they already have a newer one. Waits while nobody is
	 * known. True if it moved.
	 */
	boolean migrate(HerobrineState state) {
		if (legacyCause == null || state.players().isEmpty()) {
			return false;
		}
		UUID first = state.players().getFirst().uuid();
		listCauses.putIfAbsent(first, legacyCause);
		if (lastMarked == null) {
			lastMarked = first;
		}
		legacyCause = null;
		setDirty();
		return true;
	}

	// --- counting ---

	/**
	 * Notes one telling (the count itself is {@code HerobrineState.incrementTellingCount}). The first one that names
	 * him is remembered as the first telling (writing near his traces counts, but does not start it, D-041).
	 */
	void noteTelling(long seq, long day, boolean namesHim) {
		if (namesHim && firstTellingSeq < 0) {
			firstTellingSeq = seq;
			firstTellingDay = day;
			setDirty();
		}
	}

	/** The next writing's place in order. */
	long nextSeq() {
		long seq = nextSeq++;
		setDirty();
		return seq;
	}

	/** Order of the first telling (the first time anyone named him), or -1 before it. */
	public long firstTellingSeq() {
		return firstTellingSeq;
	}

	public long firstTellingDay() {
		return firstTellingDay;
	}

	public boolean told() {
		return firstTellingSeq >= 0;
	}

	// --- signs ---

	public List<WrittenSign> signs() {
		return Collections.unmodifiableList(signs);
	}

	public Optional<WrittenSign> sign(GlobalPos pos) {
		return signs.stream().filter(s -> s.pos().equals(pos)).findFirst();
	}

	/** Records (or replaces) the sign at its position, keeping at most {@link LoreConfig#tellingMaxSigns}. */
	void putSign(WrittenSign sign) {
		putSign(sign, LoreConfig.get().tellingMaxSigns);
	}

	/** Records the sign; past {@code max} the oldest are forgotten, never the "Stop." sign or its candidate. */
	void putSign(WrittenSign sign, int max) {
		signs.removeIf(s -> s.pos().equals(sign.pos()));
		signs.add(sign);
		while (signs.size() > Math.max(1, max)) {
			Optional<WrittenSign> oldest = signs.stream().filter(s -> !s.pos().equals(stopCandidate) && !s.pos().equals(stopSign))
					.min(java.util.Comparator.comparingLong(WrittenSign::seq));
			if (oldest.isEmpty()) {
				break;
			}
			signs.remove(oldest.get());
		}
		setDirty();
	}

	Optional<WrittenSign> removeSign(GlobalPos pos) {
		Optional<WrittenSign> found = sign(pos);
		if (found.isPresent()) {
			signs.remove(found.get());
			setDirty();
		}
		return found;
	}

	// --- books ---

	public List<WrittenBook> books() {
		return Collections.unmodifiableList(books);
	}

	public Optional<WrittenBook> book(String id) {
		return books.stream().filter(b -> b.id().equals(id)).findFirst();
	}

	/** Records (or replaces) the book, keeping at most {@link LoreConfig#tellingMaxBooks} (the oldest are forgotten). */
	void putBook(WrittenBook book) {
		putBook(book, LoreConfig.get().tellingMaxBooks);
	}

	void putBook(WrittenBook book, int max) {
		books.removeIf(b -> b.id().equals(book.id()));
		books.add(book);
		while (books.size() > Math.max(1, max)) {
			books.stream().min(java.util.Comparator.comparingLong(WrittenBook::seq)).ifPresent(books::remove);
		}
		setDirty();
	}

	Optional<WrittenBook> removeBook(String id) {
		Optional<WrittenBook> found = book(id);
		if (found.isPresent()) {
			books.remove(found.get());
			setDirty();
		}
		return found;
	}

	// --- "Stop." ---

	public Optional<GlobalPos> stopCandidate() {
		return Optional.ofNullable(stopCandidate);
	}

	void setStopCandidate(@Nullable GlobalPos pos) {
		stopCandidate = pos;
		setDirty();
	}

	public Optional<GlobalPos> stopSign() {
		return Optional.ofNullable(stopSign);
	}

	void setStopSign(@Nullable GlobalPos pos) {
		stopSign = pos;
		setDirty();
	}

	// --- place not found ---

	public List<String> visited() {
		return Collections.unmodifiableList(visited);
	}

	boolean addVisited(String id) {
		if (visited.contains(id)) {
			return false;
		}
		visited.add(id);
		setDirty();
		return true;
	}

	public Optional<String> notFound() {
		return Optional.ofNullable(notFound);
	}

	void setNotFound(String id) {
		notFound = id;
		setDirty();
	}

	public List<GlobalPos> pendingBlanks() {
		return Collections.unmodifiableList(pendingBlanks);
	}

	void addPendingBlank(GlobalPos pos) {
		if (!pendingBlanks.contains(pos)) {
			pendingBlanks.add(pos);
			setDirty();
		}
	}

	void removePendingBlank(GlobalPos pos) {
		if (pendingBlanks.remove(pos)) {
			setDirty();
		}
	}

	// --- the list in lava ---

	public Burned burned() {
		return burned;
	}

	void setBurned(Burned value) {
		burned = value;
		setDirty();
	}

	// --- the list's lines (F06, F23) and F10's last line ---

	/** The latest cause MARKED_DEATH gave for this player's line. */
	public Optional<String> listCause(UUID player) {
		return Optional.ofNullable(listCauses.get(player));
	}

	/** Records the cause on this player's line, and that theirs is now the latest marked death. */
	void setListCause(UUID player, String cause) {
		listCauses.put(player, cause);
		lastMarked = player;
		setDirty();
	}

	/** v1.0's one cause, until a known player takes it. */
	Optional<String> legacyCause() {
		return Optional.ofNullable(legacyCause);
	}

	/** Whose marked death came last (as MARKED_DEATH said). */
	public Optional<UUID> lastMarked() {
		return Optional.ofNullable(lastMarked);
	}

	/** Whose names F10's Ending B lines finish, one line each, in the order they were finished (empty before Ending B). */
	public List<UUID> f10Removed() {
		return Collections.unmodifiableList(f10Removed);
	}

	/** One more finished player for F10's last lines, after the ones before; false if they are already there. */
	boolean addF10Removed(UUID player) {
		if (f10Removed.contains(player)) {
			return false;
		}
		f10Removed.add(player);
		setDirty();
		return true;
	}
}
