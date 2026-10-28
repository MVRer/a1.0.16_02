package com.forzacode.a1016_02.world;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.LogCapture;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;
import com.forzacode.a1016_02.world.sc2.s17.books.Composer;
import com.forzacode.a1016_02.world.sc2.s17.books.DayLog;
import com.forzacode.a1016_02.world.sc2.s17.books.Facts;
import com.forzacode.a1016_02.world.sc2.s17.books.Pages;
import com.forzacode.a1016_02.world.sc2.s17.books.Voice;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * R3-B's game tests (docs/SELF_CREATED_2_R3.md, "R3-B sc3/books"): the archive's books from fixture facts (a hand-made
 * day log and inputs) with the test texts ({@code b17_test.json}) and the real ones ({@code b17.json}): deterministic per
 * seed and different across seeds, the page and title limits, no slot left and no line twice, the wrong line, authors and
 * generations, the day log (rollover, saved and loaded, capped, lenient, not kept), offline players' snapshots, two and
 * three players, day books, a malformed file, the validator over the real file, nothing logged, the six, openings and
 * closings not reused. Pure but for the server's texts; nothing here changes the world or the shared state.
 */
public class SelfCreated3BooksTests {
	private static final UUID MARIANO = new UUID(0x1111L, 0x2222L);
	private static final UUID LALO = new UUID(0x3333L, 0x4444L);
	private static final UUID ANA = new UUID(0x5555L, 0x6666L);
	private static final List<String> NAMES = List.of("mariano", "lalo", "ana");
	private static final List<UUID> UUIDS = List.of(MARIANO, LALO, ANA);
	private static final Pattern I = Pattern.compile("(?<![a-z0-9'])i(?![a-z0-9'])");

	// --- fixtures ---

	private static Voice fixture(MinecraftServer server) {
		return read(server, "world/sc2/b17_test.json", "data/a1016_02/world/sc2/b17_test.json");
	}

	private static Voice real(MinecraftServer server) {
		return Voice.of(server);
	}

	private static Voice read(MinecraftServer server, String id, String path) {
		Optional<Resource> resource = server.getResourceManager().getResource(A1016_02.id(id));
		try (Reader reader = resource.isPresent() ? resource.get().openAsReader() : classpath(path)) {
			return reader == null ? Voice.EMPTY : Voice.read(reader);
		} catch (Exception e) {
			throw new IllegalStateException("could not read " + path, e);
		}
	}

	private static Reader classpath(String path) {
		InputStream in = SelfCreated3BooksTests.class.getClassLoader().getResourceAsStream(path);
		return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	/** Thirty days of three players: mariano from day 1, lalo from day 9, ana from day 25; deaths, visits, takes, places. */
	private static DayLog log() {
		DayLog log = new DayLog();
		for (int day = 1; day <= 30; day++) {
			log.open(day, 2);
			if (day < 16 || day > 19) {
				log.online(0);
			}
			if (day >= 9) {
				log.online(1);
			}
			if (day >= 25) {
				log.online(2);
			}
			if (day % 4 == 0) {
				log.weather(DayLog.RAIN);
			}
			log.moon(day % 8);
			if (day == 7 || day == 15) {
				log.nightOut(0);
			}
			if (day == 5) {
				log.death(0, "fall", false, false, day * 24000L);
			}
			if (day == 11) {
				log.death(0, "drown", false, true, day * 24000L);
			}
			if (day == 9) {
				log.visit(1, 0, "chest");
			}
			if (day == 14) {
				log.visit(0, 1, "oak door");
			}
			if (day == 12) {
				for (int i = 0; i < 3; i++) {
					log.took(0, "torch", false);
				}
			}
			if (day == 20) {
				log.entered(0, MARIANO, 17);
			}
		}
		log.open(31, 2);
		log.online(0);
		return log;
	}

	private static Facts.Inputs inputs(long seed, int subject, DayLog log) {
		return new Facts.Inputs(seed, 31, NAMES, subject, UUIDS.get(subject), log, subject == 0 ? List.of(Map.entry(22, "fall")) : List.of(),
				List.of(new Facts.Spot("oak planks", 10, 20, 1), new Facts.Spot("crafting table", 11, 20, 1)), new Facts.Spot("", 12, 24, 1), "plains",
				List.of(new Facts.Spot("stone", 5, 6, 4)), List.of(new Facts.Spot("oak door", 12, 25, 1)),
				new DayLog.Snapshot("stone", 312, 1500, 400, 20, 3, 2, 100));
	}

	private static Facts facts(long seed, int subject) {
		return Facts.from(inputs(seed, subject, log()));
	}

	private static int firstPersons(List<String> entries) {
		int count = 0;
		for (String entry : entries) {
			Matcher m = I.matcher(entry);
			while (m.find()) {
				count++;
			}
		}
		return count;
	}

	private static void assertFits(GameTestHelper helper, Composer.Written written, String what) {
		List<String> pages = written.pages();
		helper.assertTrue(pages.size() <= Pages.PAGES, what + ": " + pages.size() + " pages");
		for (String page : pages) {
			String[] lines = page.split("\n", -1);
			helper.assertTrue(lines.length <= Pages.LINES, what + ": a page of " + lines.length + " lines");
			for (String line : lines) {
				helper.assertTrue(line.length() <= Pages.WIDTH, what + ": a line of " + line.length());
			}
		}
		helper.assertTrue(written.title().length() <= Pages.TITLE, what + ": a title of " + written.title().length());
		ItemStack stack = written.stack();
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null && !content.pages().isEmpty() && content.pages().size() <= Pages.PAGES, what + ": the book's content");
		for (var page : content.pages()) {
			helper.assertTrue(page.raw().getString().length() <= WrittenBookContent.PAGE_LENGTH, what + ": a page over vanilla's length");
		}
	}

	// --- tests ---

	@GameTest
	public void recordsAreTheSamePerSeedAndDifferAcrossSeeds(GameTestHelper helper) {
		for (Voice voice : List.of(fixture(helper.getLevel().getServer()), real(helper.getLevel().getServer()))) {
			if (voice.blank()) {
				continue;
			}
			double differ = 0;
			int pairs = 0;
			for (long seed = 1; seed <= 12; seed++) {
				Composer.Written a = Composer.record(facts(seed, 0), voice, 0, Composer.WRONG_CHANCE, false);
				Composer.Written again = Composer.record(facts(seed, 0), voice, 0, Composer.WRONG_CHANCE, false);
				helper.assertTrue(a.entries().equals(again.entries()) && a.pages().equals(again.pages()), "the same seed writes the same record");
				Composer.Written b = Composer.record(facts(seed + 1000, 0), voice, 0, Composer.WRONG_CHANCE, false);
				Set<String> other = new HashSet<>(b.entries());
				long distinct = a.entries().stream().filter(e -> !other.contains(e)).count();
				differ += (double) distinct / a.entries().size();
				pairs++;
				Composer.Written next = Composer.record(facts(seed, 0), voice, 1, Composer.WRONG_CHANCE, false);
				helper.assertTrue(!next.entries().equals(a.entries()), "two editions differ");
				Composer.Written friend = Composer.record(facts(seed, 1), voice, 0, Composer.WRONG_CHANCE, false);
				helper.assertTrue(!friend.entries().equals(a.entries()), "two players' records differ");
			}
			helper.assertTrue(differ / pairs >= 0.6, "records differ across seeds by " + (differ / pairs));
		}
		helper.succeed();
	}

	@GameTest
	public void recordsStayWithinThePageAndTitleLimits(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		DayLog busy = new DayLog();
		for (int day = 1; day <= 300; day++) {
			busy.open(day, 1);
			busy.online(0);
			busy.online(1);
			busy.nightOut(0);
			busy.death(0, day % 2 == 0 ? "fall" : "drown", false, true, day * 1000L);
			busy.visit(0, 1, "chest");
			busy.visit(1, 0, "oak door");
			busy.took(0, "torch", false);
			busy.entered(0, MARIANO, 16 + day % 5);
		}
		List<String> longNames = List.of("a_name_that_is_far_too_long_for_any_title_at_all", "lalo");
		for (Voice voice : List.of(fixture(server), real(server))) {
			for (int edition = 0; edition < 20; edition++) {
				Facts.Inputs in = inputs(edition, 0, busy);
				Facts facts = Facts.from(new Facts.Inputs(in.seed(), 300, longNames, 0, MARIANO, busy, in.marked(), in.firsts(), in.home(), in.homeBiome(),
						in.mining(), in.doors(), in.stats()));
				assertFits(helper, Composer.record(facts, voice, edition, 1.0, edition % 2 == 0), "record " + edition);
				assertFits(helper, Composer.edition(facts, voice, edition, 1.0), "edition " + edition);
				assertFits(helper, Composer.day(edition, 1 + edition * 7, 300, busy.day(1 + edition * 7), longNames, voice), "day book " + edition);
			}
			for (String six : Books.SIX) {
				assertFits(helper, Composer.six(voice, six, 7L), "the six");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void noSlotIsLeftNoLineTwiceAndIAtMostOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		DayLog log = log();
		for (Voice voice : List.of(fixture(server), real(server))) {
			for (int edition = 0; edition < 30; edition++) {
				for (int subject = 0; subject < 3; subject++) {
					Facts facts = Facts.from(inputs(edition * 31L + subject, subject, log));
					for (Composer.Written written : List.of(Composer.record(facts, voice, edition, 0.5, false), Composer.record(facts, voice, edition, 0.5, true),
							Composer.edition(facts, voice, edition, 0.5))) {
						check(helper, written, "record");
					}
				}
				for (int day = 1; day <= 31; day++) {
					check(helper, Composer.day(edition, day, 31, log.day(day), NAMES, voice), "day book");
				}
			}
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Composer.Written written, String what) {
		for (String entry : written.entries()) {
			helper.assertTrue(entry.indexOf('{') < 0 && entry.indexOf('}') < 0, what + ": a slot left unfilled");
			helper.assertTrue(!entry.isBlank(), what + ": an empty line");
		}
		helper.assertTrue(new HashSet<>(written.entries()).size() == written.entries().size(), what + ": a line twice");
		helper.assertTrue(firstPersons(written.entries()) <= 1, what + ": i twice");
	}

	@GameTest
	public void theWrongLineIsAtMostOneNeverADeathAndNamesAFriendOnce(GameTestHelper helper) {
		Voice voice = fixture(helper.getLevel().getServer());
		int with = 0;
		for (int edition = 0; edition < 200; edition++) {
			Facts facts = facts(edition, 0);
			Composer.Written always = Composer.record(facts, voice, edition, 1.0, false);
			List<Integer> wrong = new ArrayList<>();
			for (int i = 0; i < always.kinds().size(); i++) {
				if (always.kinds().get(i) == 'p') {
					wrong.add(i);
				}
			}
			helper.assertTrue(wrong.size() == 1, "one wrong line when it is certain: " + wrong.size());
			String line = always.entries().get(wrong.getFirst());
			helper.assertTrue(line.matches("day \\d+\\. p\\d( (lalo|ana))?\\."), "the wrong line is a wrong line, never a death");
			long friends = NAMES.subList(1, 3).stream().filter(line::contains).count();
			helper.assertTrue(friends <= 1, "the wrong line names a friend at most once");
			helper.assertTrue(!Composer.record(facts, voice, edition, 0.0, false).kinds().contains('p'), "no wrong line when it never comes");
			if (Composer.record(facts, voice, edition, Composer.WRONG_CHANCE, false).kinds().contains('p')) {
				with++;
			}
		}
		helper.assertTrue(with > 60 && with < 140, "a wrong line in an edition in two: " + with + " of 200");
		helper.succeed();
	}

	@GameTest
	public void recordsAreByTheirPlayerEditionsTatteredDayBooksByNobody(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Voice voice = real(server).blank() ? fixture(server) : real(server);
		Facts facts = facts(5, 1);
		WrittenBookContent record = Composer.record(facts, voice, 0, 0.5, false).stack().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(record.title().raw().equals("lalo") && record.author().equals("lalo") && record.generation() == 0, "a record: by its player, an original");
		Composer.Written edition = Composer.edition(facts, voice, 3, 0.5);
		helper.assertTrue(edition.generation() == WrittenBookContent.MAX_GENERATION && edition.kinds().getFirst() == 'x', "an edition: tattered, its note first");
		WrittenBookContent day = Composer.day(5, 9, 31, log().day(9), NAMES, voice).stack().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(day.title().raw().equals("day 9") && day.author().isEmpty() && day.generation() == 0, "a day book: day N, no author");
		WrittenBookContent blank = Books.blankDay(40).get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(blank.title().raw().equals("day 40") && blank.pages().size() == 1 && blank.pages().getFirst().raw().getString().isEmpty(),
				"a day to come: blank");
		helper.assertTrue(Books.dayOf(Books.blankDay(40)).orElse(-1) == 40 && Books.dayOf(Composer.record(facts, voice, 0, 0.5, false).stack()).isEmpty(),
				"a day book's day");
		WrittenBookContent six = Books.six(server, "t0rch", 3L).get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(six.title().raw().equals("t0rch") && six.author().equals("t0rch") && six.generation() == 0, "the six: by their own name");
		ItemStack asItStood = Composer.record(facts, voice, 0, 0.5, false).stack();
		ItemStack moved = Books.edition(helper.getLevel(), asItStood);
		WrittenBookContent tattered = moved.get(DataComponents.WRITTEN_BOOK_CONTENT);
		List<String> before = Pages.text(asItStood);
		List<String> after = Pages.text(moved);
		helper.assertTrue(tattered.generation() == WrittenBookContent.MAX_GENERATION && tattered.title().equals(record.title())
				&& tattered.author().equals("lalo") && after.size() >= before.size() && after.getFirst().startsWith(before.getFirst().split("\n")[0]),
				"the record as it stood, tattered");
		helper.assertTrue(Books.isRecordOf(asItStood, "Lalo") && !Books.isRecordOf(asItStood, "ana"), "a record is its player's in any case");
		helper.succeed();
	}

	@GameTest
	public void theDayLogRollsOverSavesLoadsIsCappedAndLenient(GameTestHelper helper) {
		helper.assertTrue(DayLog.BYTES <= 48, "at most 48 bytes a day");
		DayLog log = new DayLog();
		log.open(1, 0);
		log.online(0);
		log.weather(DayLog.RAIN);
		log.weather(DayLog.CLEAR);
		log.moon(3);
		log.fog();
		log.death(0, "fall", false, true, 100);
		log.death(0, "fell from high", true, true, 110);
		log.death(1, "drown", false, false, 120);
		log.took(0, "torch", false);
		log.took(-1, "chest", true);
		log.entered(0, MARIANO, 18);
		log.open(2, 3);
		DayLog.Day one = log.day(1);
		helper.assertTrue(one != null && one.closed() && one.stage() == 3 && one.weather() == DayLog.RAIN && one.moonSeen() && one.moon() == 3 && one.fog(),
				"day 1 rolled over with its stage, worst weather, moon and fog");
		helper.assertTrue(one.deaths().size() == 2 && one.deaths().getFirst().marked() && one.deaths().getFirst().cause().equals("fell from high")
				&& !one.deaths().get(1).marked(), "a death seen twice is one, marked");
		helper.assertTrue(one.traces() == 2 && one.took().size() == 1 && one.took().getFirst().count() == 1 && one.entered(18) && log.placeDay(MARIANO, 18) == 1,
				"what he took, for whom, the places");
		log.snapshot(LALO, new DayLog.Snapshot("dirt", 40, 900, 80, 3, 1, 0, 5000));
		DayLog back = log.reloaded();
		helper.assertTrue(back.all().equals(log.all()) && back.current() == 2 && back.snapshot(LALO).equals(log.snapshot(LALO))
				&& back.placeDay(MARIANO, 18) == 1, "saved and loaded");
		for (int day = 3; day <= 1100; day++) {
			log.open(day, 1);
		}
		helper.assertTrue(log.size() == DayLog.DAYS && log.first() == 1100 - DayLog.DAYS + 1 && log.day(5) == null, "the last 1024 days");
		helper.assertTrue(Composer.day(1, 5, 1100, log.day(5), NAMES, fixture(helper.getLevel().getServer())).entries().equals(List.of(Composer.NOT_KEPT)),
				"a day before the log reads not kept");
		CompoundTag broken = log.save();
		ListTag days = broken.getListOrEmpty("d");
		days.add(new ByteArrayTag(new byte[] {1, 2}));
		days.add(StringTag.valueOf("not a day"));
		broken.put("s", StringTag.valueOf("not a list"));
		DayLog lenient = DayLog.load(broken);
		helper.assertTrue(lenient.size() == DayLog.DAYS, "a broken saved log is read leniently");
		helper.assertTrue(DayLog.load(new CompoundTag()).size() == 0, "an empty saved log");
		helper.succeed();
	}

	@GameTest
	public void anOfflinePlayerIsReadFromTheirSnapshot(GameTestHelper helper) {
		DayLog log = log();
		log.snapshot(LALO, new DayLog.Snapshot("deepslate", 777, 4200, 90, 12, 4, 1, 3000));
		DayLog back = log.reloaded();
		Facts.Inputs in = inputs(9, 1, back);
		Facts facts = Facts.from(new Facts.Inputs(in.seed(), in.today(), NAMES, 1, LALO, back, List.of(), List.of(), null, "", List.of(), List.of(),
				back.snapshot(LALO)));
		helper.assertTrue(facts.entries().stream().anyMatch(f -> f.kind() == 'e' && f.n() == 777 && "deepslate".equals(f.slots().get("block"))),
				"the most mined block from the snapshot");
		helper.assertTrue(facts.entries().stream().filter(f -> f.kind() == 'm').count() >= 4, "the statistics from the snapshot");
		Facts unknown = Facts.of(helper.getLevel(), new UUID(7L, 7L));
		helper.assertTrue(unknown.name().isEmpty(), "an unknown player has no name");
		helper.assertTrue(Composer.record(unknown, fixture(helper.getLevel().getServer()), 0, 0.5, false).stack() != null, "and still a book");
		helper.succeed();
	}

	@GameTest
	public void twoAndThreePlayersNameEachOtherWhereTheFactsDo(GameTestHelper helper) {
		Voice voice = fixture(helper.getLevel().getServer());
		Facts mariano = facts(3, 0);
		Facts lalo = facts(3, 1);
		Facts ana = facts(3, 2);
		helper.assertTrue(mariano.entries().stream().anyMatch(f -> f.kind() == 'j' && "lalo".equals(f.slots().get("friend")) && f.day() == 9
				&& "chest".equals(f.slots().get("block"))), "lalo at mariano's chest is in mariano's facts");
		helper.assertTrue(lalo.entries().stream().anyMatch(f -> f.kind() == 'i' && "mariano".equals(f.slots().get("friend")) && f.day() == 9),
				"and in lalo's, as a chest at a friend's");
		helper.assertTrue(lalo.entries().stream().anyMatch(f -> f.kind() == 'j' && "mariano".equals(f.slots().get("friend")) && f.day() == 14),
				"mariano at lalo's door is in lalo's facts");
		helper.assertTrue(ana.firstDay() == 25 && ana.entries().stream().filter(f -> f.kind() == 'j').count() >= 2, "ana from her first day, with both");
		helper.assertTrue(mariano.firstDay() == 1 && lalo.firstDay() == 9, "each from their own first day");
		helper.assertTrue(mariano.entries().stream().anyMatch(f -> f.kind() == 'n' && f.away() == 4), "mariano's days away");
		boolean named = false;
		for (int edition = 0; edition < 10; edition++) {
			Composer.Written record = Composer.record(mariano, voice, edition, 0.0, false);
			helper.assertTrue(record.title().equals("mariano"), "each record is its own player's");
			named |= record.entries().stream().anyMatch(e -> e.contains("lalo"));
		}
		helper.assertTrue(named, "a friend's name in a record");
		Composer.Written day26 = Composer.day(3, 26, 31, log().day(26), NAMES, voice);
		for (String name : NAMES) {
			helper.assertTrue(day26.entries().stream().anyMatch(e -> e.contains(name)), "the day book names everyone who played: " + name);
		}
		helper.succeed();
	}

	@GameTest
	public void dayBooksArePastTodayToComeAndNobody(GameTestHelper helper) {
		Voice voice = fixture(helper.getLevel().getServer());
		DayLog log = log();
		log.open(32, 2);
		log.open(33, 2);
		Composer.Written past = Composer.day(1, 12, 33, log.day(12), NAMES, voice);
		helper.assertTrue(past.entries().stream().anyMatch(e -> e.startsWith("s w")) && past.entries().stream().anyMatch(e -> e.startsWith("s took")),
				"a past day: its weather, what he took");
		helper.assertTrue(past.kinds().stream().noneMatch(k -> k == 't'), "a past day does not break off");
		Composer.Written nobody = Composer.day(1, 32, 33, log.day(32), NAMES, voice);
		helper.assertTrue(nobody.entries().size() == 1 && nobody.entries().getFirst().startsWith("s nobody"), "a day nobody came is one line");
		Composer.Written today = Composer.day(1, 31, 31, log().day(31), NAMES, voice);
		helper.assertTrue(today.kinds().getLast() == 't' && !today.entries().getLast().endsWith("."), "today breaks off");
		helper.assertTrue(Composer.day(1, 40, 33, null, NAMES, voice).entries().isEmpty(), "a day to come is blank");
		helper.assertTrue(Composer.day(1, 12, 33, log.day(12), NAMES, voice).entries().equals(past.entries()), "a day book is the same each time");
		helper.succeed();
	}

	@GameTest
	public void aMalformedFileWritesBlankPagesAndLogsNoText(GameTestHelper helper) {
		helper.assertTrue(Voice.parse(JsonParser.parseString("[1, 2]")).blank() && Voice.parse(null).blank(), "not an object: blank");
		Voice partial = Voice.parse(JsonParser.parseString(
				"{\"l\": {\"a\": [{\"t\": 5}, {\"t\": \"{name}. {nope}.\"}, {\"t\": \"{name}. a.\", \"if\": [\"nonsense\"]}, {\"t\": \"{name}. kept.\"}], \"q\": {}},"
						+ " \"u\": 3, \"map\": []}"));
		helper.assertTrue(partial.pool('a').size() == 1 && partial.skipped() == 3, "the usable line is kept, the rest left out: " + partial.skipped());
		try (LogCapture capture = LogCapture.open()) {
			Voice broken = Voice.readOrEmpty(new StringReader("{\"l\": {\"a\": [ {\"t\": \"{name} secretline"));
			helper.assertTrue(broken.blank(), "a file that does not parse: blank");
			for (LogCapture.Line line : capture.shown()) {
				helper.assertTrue(!line.message().contains("secretline") && line.thrown() == null, "no text and no trace in a shown log line");
			}
			helper.assertTrue(capture.shown().stream().anyMatch(l -> l.message().startsWith("[a1016] s2: b17 could not be read")), "logged neutrally");
		}
		Composer.Written blank = Composer.record(facts(1, 0), Voice.EMPTY, 0, 0.5, false);
		helper.assertTrue(blank.entries().isEmpty() && blank.pages().isEmpty() && blank.title().equals("mariano"), "blank pages, titled");
		WrittenBookContent content = blank.stack().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content.pages().size() == 1 && content.pages().getFirst().raw().getString().isEmpty(), "one empty page");
		helper.assertTrue(Composer.day(1, 3, 31, log().day(3), NAMES, Voice.EMPTY).entries().isEmpty() && Composer.six(Voice.EMPTY, "t0rch", 1L).pages().isEmpty(),
				"blank day books and six");
		helper.succeed();
	}

	@GameTest
	public void theValidatorPassesTheRealTextsAndCatchesFaults(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Optional<Resource> resource = server.getResourceManager().getResource(A1016_02.id("world/sc2/b17.json"));
		helper.assertTrue(resource.isPresent(), "the real texts are present");
		try (Reader reader = resource.get().openAsReader()) {
			List<String> problems = Voice.validate(reader);
			helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + problems.subList(0, Math.min(8, problems.size())));
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(!real(server).blank() && real(server).skipped() == 0, "every real line is usable");
		List<String> faults = Voice.validate(JsonParser.parseString("{\"l\": {\"a\": [{\"t\": \"{name}. Kept.\"}, {\"t\": \"{name}?\"}, {\"t\": \"{name} and you.\"},"
				+ " {\"t\": \"{name} {nope}.\"}, {\"t\": \"" + "word ".repeat(70) + "\"}, {\"t\": \"i and i.\"}], \"p\": [{\"t\": \"day {day}. {cause}.\"}]}}"));
		for (String fault : List.of("capitals", "question or exclamation", "forbidden word", "unknown slot", "longer than a page", "i twice", "about a death",
				"lines, at least", "no map", "variants")) {
			helper.assertTrue(faults.stream().anyMatch(f -> f.contains(fault)), "the validator catches: " + fault);
		}
		helper.assertTrue(!Voice.validate(new StringReader("{ broken")).isEmpty(), "a file that does not parse fails");
		helper.succeed();
	}

	@GameTest
	public void composingLogsNothing(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Voice voice = real(server).blank() ? fixture(server) : real(server);
		Set<String> written = new HashSet<>();
		try (LogCapture capture = LogCapture.open()) {
			DayLog log = log();
			for (int edition = 0; edition < 10; edition++) {
				written.addAll(Composer.record(facts(edition, edition % 3), voice, edition, 0.5, edition % 2 == 0).entries());
				written.addAll(Composer.edition(facts(edition, 0), voice, edition, 0.5).entries());
				written.addAll(Composer.day(edition, edition + 1, 31, log.day(edition + 1), NAMES, voice).entries());
			}
			for (String six : Books.SIX) {
				written.addAll(Composer.six(voice, six, 1L).entries());
			}
			for (LogCapture.Line line : capture.lines()) {
				helper.assertTrue(!line.message().contains("b17"), "nothing of the books is logged");
				for (String text : written) {
					helper.assertTrue(text.length() < 12 || !line.message().contains(text), "no book text in a log line");
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theSixHaveThreeVariantsBySeed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (Voice voice : List.of(fixture(server), real(server))) {
			for (String six : Books.SIX) {
				Set<List<String>> seen = new HashSet<>();
				for (long seed = 0; seed < 60; seed++) {
					Composer.Written written = Composer.six(voice, six, seed);
					helper.assertTrue(written.pages().size() >= 2 && written.pages().size() <= 4, "two to four pages");
					helper.assertTrue(written.pages().equals(Composer.six(voice, six, seed).pages()), "the same seed, the same variant");
					seen.add(written.pages());
				}
				helper.assertTrue(seen.size() == 3, "three variants across seeds: " + seen.size());
			}
			helper.assertTrue(Composer.six(voice, "nobody_here", 1L).pages().isEmpty(), "not one of the six: blank");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theLiveLogOpensTodayOnTheServer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Books.init();
		Books.init();
		helper.succeedWhen(() -> {
			helper.assertTrue(!SelfCreated.enabled() || DayLog.get(server).current() == Books.today(server), "the day log has today open");
			helper.assertTrue(!SelfCreated.enabled() || DayLog.get(server).day(Books.today(server)) != null, "and keeps it");
			helper.assertTrue(Books.day(helper.getLevel(), Books.today(server) + 2).get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw()
					.equals("day " + (Books.today(server) + 2)), "a day to come from the server");
			helper.assertTrue(!Books.stale(helper.getLevel(), Books.blankDay(Books.today(server) + 2)), "a day to come is never stale");
		});
	}

	@GameTest
	public void openingsAndClosingsAreNotReusedWithinSixEditions(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (Voice voice : List.of(fixture(server), real(server))) {
			for (int subject = 0; subject < 3; subject++) {
				Facts facts = facts(11, subject);
				List<String> openings = new ArrayList<>();
				List<String> closings = new ArrayList<>();
				for (int edition = 0; edition < 24; edition++) {
					Composer.Written record = Composer.record(facts, voice, edition, 0.5, false);
					helper.assertTrue(record.kinds().getFirst() == 'a' && record.kinds().getLast() == 'q', "an opening first, a closing last");
					openings.add(record.entries().getFirst());
					closings.add(record.entries().getLast());
				}
				for (int edition = 0; edition < 24; edition++) {
					for (int back = 1; back <= 6 && edition - back >= 0; back++) {
						helper.assertTrue(!openings.get(edition).equals(openings.get(edition - back)), "an opening again within six editions");
						helper.assertTrue(!closings.get(edition).equals(closings.get(edition - back)), "a closing again within six editions");
					}
				}
				Composer.Written visit = Composer.record(facts, voice, 2, 0.5, true);
				helper.assertTrue(visit.kinds().getLast() == 'r', "17g's record ends with the visit");
			}
		}
		helper.succeed();
	}
}
