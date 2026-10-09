package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * What the player told, per world ({@code data/a1016_02/lore_telling.dat}). Shared facts ({@code tellingStarted},
 * {@code stopFired}) live in {@code HerobrineState}; the telling count is mirrored there as the flag
 * {@code lore:telling_count=<n>} (see {@link LoreApi#tellingCount}).
 * <ul>
 * <li>signs: every sign a player wrote about him, and every sign written after the first telling (blank sign's
 * pool)</li>
 * <li>books: books written about him, by the id stamped into the item ({@link Telling#BOOK_MARKER})</li>
 * <li>the "Stop." sign: the candidate (first sign about him written in Stage 3) and, once it fired, the sign</li>
 * <li>visited: placed fragments in the order the player first came near them (place not found takes the first)</li>
 * <li>the list in lava: lists burned, pyramids still to raise, and where</li>
 * </ul>
 */
public final class TellingData extends SavedData {
	/**
	 * A sign a player wrote.
	 *
	 * @param seq          order of writing in this world
	 * @param aboutHim     names him or was written near his traces
	 * @param afterTelling written after the first telling (blank sign may take it)
	 * @param blanked      he already blanked it
	 */
	public record WrittenSign(GlobalPos pos, UUID writer, String text, boolean aboutHim, boolean namesHim, long seq, long day,
			boolean afterTelling, boolean blanked) {
		static final Codec<WrittenSign> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(WrittenSign::pos),
				UUIDUtil.CODEC.fieldOf("writer").forGetter(WrittenSign::writer),
				Codec.STRING.optionalFieldOf("text", "").forGetter(WrittenSign::text),
				Codec.BOOL.optionalFieldOf("aboutHim", false).forGetter(WrittenSign::aboutHim),
				Codec.BOOL.optionalFieldOf("namesHim", false).forGetter(WrittenSign::namesHim),
				Codec.LONG.fieldOf("seq").forGetter(WrittenSign::seq),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(WrittenSign::day),
				Codec.BOOL.optionalFieldOf("afterTelling", false).forGetter(WrittenSign::afterTelling),
				Codec.BOOL.optionalFieldOf("blanked", false).forGetter(WrittenSign::blanked)
		).apply(i, WrittenSign::new));

		WrittenSign withBlanked(boolean value) {
			return new WrittenSign(pos, writer, text, aboutHim, namesHim, seq, day, afterTelling, value);
		}

		WrittenSign movedTo(GlobalPos to) {
			return new WrittenSign(to, writer, text, aboutHim, namesHim, seq, day, afterTelling, blanked);
		}
	}

	/** A book a player wrote about him, by the id stamped into it. {@code textHash} skips saves that change nothing. */
	public record WrittenBook(String id, UUID writer, boolean namesHim, boolean signed, long seq, long day, int textHash) {
		static final Codec<WrittenBook> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(WrittenBook::id),
				UUIDUtil.CODEC.fieldOf("writer").forGetter(WrittenBook::writer),
				Codec.BOOL.optionalFieldOf("namesHim", false).forGetter(WrittenBook::namesHim),
				Codec.BOOL.optionalFieldOf("signed", false).forGetter(WrittenBook::signed),
				Codec.LONG.fieldOf("seq").forGetter(WrittenBook::seq),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(WrittenBook::day),
				Codec.INT.optionalFieldOf("textHash", 0).forGetter(WrittenBook::textHash)
		).apply(i, WrittenBook::new));
	}

	/**
	 * The list in lava: how many copies burned, how many pyramids he owes for them and has raised, near where the
	 * last one burned, and the ocean he raises them in.
	 */
	public record Burned(int lists, int owed, int raised, Optional<GlobalPos> near, Optional<GlobalPos> ocean) {
		static final Burned NONE = new Burned(0, 0, 0, Optional.empty(), Optional.empty());
		static final Codec<Burned> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("lists", 0).forGetter(Burned::lists),
				Codec.INT.optionalFieldOf("owed", 0).forGetter(Burned::owed),
				Codec.INT.optionalFieldOf("raised", 0).forGetter(Burned::raised),
				GlobalPos.CODEC.optionalFieldOf("near").forGetter(Burned::near),
				GlobalPos.CODEC.optionalFieldOf("ocean").forGetter(Burned::ocean)
		).apply(i, Burned::new));

		/** Pyramids still to be raised. */
		public int pending() {
			return Math.max(0, owed - raised);
		}

		Burned withOcean(GlobalPos pos) {
			return new Burned(lists, owed, raised, near, Optional.of(pos));
		}

		Burned raisedOne() {
			return new Burned(lists, owed, raised + 1, near, ocean);
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
			Codec.STRING.optionalFieldOf("listCause").forGetter(d -> Optional.ofNullable(d.listCause))
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
	private @Nullable String listCause;

	/** A fresh record (tests use their own). */
	public TellingData() {
	}

	private TellingData(Counters counters, List<WrittenSign> signs, List<WrittenBook> books, Optional<GlobalPos> stopCandidate,
			Optional<GlobalPos> stopSign, List<String> visited, Optional<String> notFound, List<GlobalPos> pendingBlanks, Burned burned,
			Optional<String> listCause) {
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
		this.listCause = listCause.orElse(null);
	}

	public static TellingData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
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

	/** Order of the first telling (the first time a player named him), or -1 before it. */
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

	// --- the list's cause (F23) ---

	public Optional<String> listCause() {
		return Optional.ofNullable(listCause);
	}

	void setListCause(@Nullable String cause) {
		listCause = cause;
		setDirty();
	}
}
