package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;
import com.forzacode.a1016_02.world.sc2.s17.books.Composer;
import com.forzacode.a1016_02.world.sc2.s17.books.DayLog;
import com.forzacode.a1016_02.world.sc2.s17.books.Pages;
import com.forzacode.a1016_02.world.sc2.s17.books.Voice;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * R3-1's rules' game test bodies (docs/SELF_CREATED_2_R3.md, "Answers (17)", "The books", R3-1's tests): 17a with the
 * books, 17g, the day books, 17f, 17b on the floating grid, two players, asleep and creative, nothing logged. The seam
 * class {@code world.SelfCreated3ArchiveTests} calls them (its rules' section). Every test builds a version 3 archive at
 * its own far-off spot (x 410 000 and on, z -415 000: no other test uses them) over a block of rock round the well, with
 * its own data, players, view, figure, answers and days; the players are made known to the live story's state (the books
 * read it), each name only these tests use.
 */
public final class ArchiveRulesTestKit {
	static final int X = 410_000;
	static final int Z = -415_000;
	/** The hall's floor and the land's top (the hall's ceiling 41 up stays under it: 51 between). */
	static final int FLOOR = -14;
	static final int ROCK_TOP = 60;

	private ArchiveRulesTestKit() {
	}

	// --- the rig ---

	/** A player only these tests know, in a game mode of the test's choosing. */
	static final class Player extends ServerPlayer {
		private final GameType mode;

		Player(ServerLevel level, String name, GameType mode) {
			super(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes(("s17r:" + name).getBytes()), name), ClientInformation.createDefault());
			this.mode = mode;
			mode.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** A version 3 archive, short hall, facing north, its hall's floor at {@value #FLOOR}, the land round its well at {@value #ROCK_TOP}. */
	static Live.Result build(ServerLevel level, int x, int z) {
		Start start = new Start(new BlockPos(x, FLOOR, z), Direction.NORTH, ArchivePlan.variant3(false, 0.08, false, 0, level.getMinY()), ArchiveTestKit.SEED)
				.withVersion(3);
		ArchivePlan probe = ArchivePlan.of(start, new int[0]);
		ArchiveTestKit.load(level, probe.box().inflatedBy(8));
		int[] sq = Well.square(probe);
		BlockPos a = probe.at(sq[1] - 6, -12, sq[3] - 6);
		BlockPos b = probe.at(sq[2] + 6, ROCK_TOP - FLOOR, sq[3] + Well.SIDE + Well.APRON + 6);
		ArchiveTestKit.rock(level, BoundingBox.fromCorners(a, b));
		Live.Result built = Live.place(level, S17.KIND, start);
		ArchiveTestKit.load(level, built.box());
		return built;
	}

	/** One test's own world round a version 3 archive: its data, players, day and tick, view, figure, answers and days. */
	static final class Rig {
		final ServerLevel level;
		final Live.Result built;
		final ArchivePlan plan;
		final Well well;
		final Remains.Place place;
		final Sc2Data data = new Sc2Data();
		final List<ServerPlayer> inside = new ArrayList<>();
		final List<ServerPlayer> people = new ArrayList<>();
		final List<HerobrineState.Subject> known = new ArrayList<>();
		final List<String> answered = new ArrayList<>();
		final List<Vec3> glimpses = new ArrayList<>();
		final Map<UUID, Long> play = new HashMap<>();
		final Sc17Config config = new Sc17Config();
		boolean seen;
		String story;
		long now = 10_000;
		long day = 7;
		DayBooks.Days days = DayBooks.Days.LIVE;
		final ArchiveEnv env;
		ArchiveRules rules;
		final Answers answers;

		Rig(ServerLevel level, Live.Result built) {
			this.level = level;
			this.built = built;
			this.plan = ArchiveTestKit.plan(built);
			this.well = plan.well;
			this.place = built.place();
			data.add(built.known());
			TraceService traces = Services.traces().forced();
			Answers.View view = (l, cells) -> !seen;
			Answers.Sink sink = (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString());
			this.answers = Answers.over(new Answers.Env(s -> data, traces, view, (s, p) -> List.copyOf(inside), uuid -> false, this::playTicks,
					s -> story, () -> day, () -> now, sink));
			Figure figure = new Figure() {
				@Override
				public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
					return true;
				}

				@Override
				public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
					glimpses.add(feet);
					return FireResult.FIRED;
				}
			};
			this.env = new ArchiveEnv(answers, traces, view, p -> List.copyOf(inside), () -> List.copyOf(people), () -> data, () -> day, () -> now,
					figure, () -> config, Services.watch(), () -> List.copyOf(known), uuid -> false, this::playTicks, sink, new DayBooks.Days() {
						@Override
						public int today(ServerLevel l) {
							return days.today(l);
						}

						@Override
						public ItemStack book(ServerLevel l, int d) {
							return days.book(l, d);
						}

						@Override
						public boolean stale(ServerLevel l, ItemStack stack) {
							return days.stale(l, stack);
						}
					});
			this.rules = ArchiveRules.over(env);
		}

		/** A new visit to the same archive: the same data, players and days, the rules' session state forgotten. */
		void newVisit() {
			answers.clear();
			rules = ArchiveRules.over(env);
		}

		long playTicks(UUID uuid) {
			return play.getOrDefault(uuid, 400_000L);
		}

		Player enter(String name, BlockPos feet) {
			return enter(name, feet, GameType.SURVIVAL);
		}

		Player enter(String name, BlockPos feet, GameType mode) {
			Player p = new Player(level, name, mode);
			p.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
			inside.add(p);
			people.add(p);
			known.add(new HerobrineState.Subject(p.getUUID(), name));
			HerobrineState.get(level.getServer()).addKnownPlayer(p.getUUID(), name);
			data.visit(p.getUUID(), place);
			return p;
		}

		void move(ServerPlayer p, BlockPos feet) {
			p.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
		}

		void look(boolean scan) {
			rules.look(level.getServer(), scan);
		}

		/** Feet on gallery {@code k}'s ring, on the near side's middle line at {@code j}. */
		BlockPos gallery(int k, int j) {
			return well.at(Well.middle(Well.NEAR), well.floorY(k) + 1, j);
		}
	}

	/** A test's days: today is {@code today}; the books composed by R3-B's composer as if it were (the live day log's days). */
	static DayBooks.Days days(int today) {
		return new DayBooks.Days() {
			@Override
			public int today(ServerLevel level) {
				return today;
			}

			@Override
			public ItemStack book(ServerLevel level, int day) {
				if (day > today) {
					return Books.blankDay(day);
				}
				List<String> names = new ArrayList<>();
				HerobrineState.get(level.getServer()).players().forEach(p -> names.add(p.name().toLowerCase(Locale.ROOT)));
				return Composer.day(level.getSeed(), day, today, DayLog.get(level.getServer()).day(day), names, Voice.of(level.getServer())).stack();
			}

			@Override
			public boolean stale(ServerLevel level, ItemStack stack) {
				OptionalInt d = Books.dayOf(stack);
				return d.isPresent() && d.getAsInt() <= today && !Pages.text(stack).equals(Pages.text(book(level, d.getAsInt())));
			}
		};
	}

	static int glowstone(ServerLevel level, List<BlockPos> cells) {
		return ArchiveTestKit.glowstone(level, cells);
	}

	/** The written books in these shelves (loaded). */
	static List<ItemStack> books(ServerLevel level, List<BlockPos> shelves) {
		List<ItemStack> out = new ArrayList<>();
		for (Records.Slot s : Records.books(level, shelves)) {
			out.add(s.stack());
		}
		return out;
	}

	static String text(ItemStack stack) {
		return String.join("\n", Pages.text(stack));
	}

	/** Feet on the dais by the records wall (within 6 of it). */
	static BlockPos byTheWall(ArchivePlan plan) {
		return plan.at(1, ArchivePlan.DAIS_HEIGHT + 1, -plan.length + 1);
	}

	/** Feet on the nave's floor 40 or more from the records wall. */
	static BlockPos awayFromTheWall(ArchivePlan plan) {
		return plan.at(0, 1, -20);
	}

	/** To the wall, a look, away, a look: what makes 17g. */
	static void wallThenAway(Rig rig, ServerPlayer p) {
		rig.move(p, byTheWall(rig.plan));
		rig.look(false);
		rig.move(p, awayFromTheWall(rig.plan));
		rig.look(false);
	}

	static List<Integer> numbers(List<Records.Slot> slots) {
		List<Integer> out = new ArrayList<>();
		slots.forEach(s -> out.add(s.number()));
		return out;
	}

	static int wallBooks(ServerLevel level, ArchivePlan plan) {
		return Records.books(level, plan.recordWall()).size();
	}

	// --- 17a ---

	/**
	 * 17a with the books: in a version 3 archive two known players get their records on the dais wall beside the list's
	 * six, each R3-B's book about them (titled and "by" their name, lowercase, an original with text, numbered 0), never
	 * twice (a new visit leaves none); a record already there in round 2's case is theirs (no second); a version 2
	 * archive gets the same books.
	 */
	public static void recordsAreTheBooks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Rig rig = new Rig(level, build(level, X, Z));
		ArchivePlan plan = rig.plan;
		int six = wallBooks(level, plan);
		rig.enter("Ana_s3a", plan.at(0, 1, -10));
		rig.enter("ben_s3a", plan.at(3, 1, -20));
		rig.look(false);
		rig.look(false);
		boolean voiced = !Voice.of(level.getServer()).blank();
		for (String name : List.of("ana_s3a", "ben_s3a")) {
			Records.Slot record = Records.onWall(level, plan, name);
			helper.assertTrue(record != null, name + " has no record on the wall: " + Records.titles(level, plan));
			var content = record.stack().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content.title().raw().equals(name) && content.author().equals(name) && content.generation() == Pages.ORIGINAL,
					"the record is not " + name + "'s original: " + content.title().raw() + " by " + content.author() + ", " + content.generation());
			helper.assertTrue(record.number() == 0, name + "'s first record is numbered " + record.number());
			helper.assertTrue(!voiced || !text(record.stack()).isBlank(), name + "'s record is blank");
		}
		helper.assertTrue(wallBooks(level, plan) == six + 2, (wallBooks(level, plan) - six) + " records left, not 2");
		rig.newVisit();
		rig.look(false);
		helper.assertTrue(wallBooks(level, plan) == six + 2, "a second visit left another: " + (wallBooks(level, plan) - six));
		// Round 2's blank record in its own case is theirs: none left beside it.
		BlockPos free = Records.firstFree(level, plan);
		rig.rules.env(level.getServer()).traces().leaveStack(level, free, ArchiveShape.record("Cid_S3a"), Records.CAUSE);
		rig.enter("cid_s3a", plan.at(-3, 1, -12));
		rig.look(false);
		helper.assertTrue(wallBooks(level, plan) == six + 3, "a second record beside round 2's: " + (wallBooks(level, plan) - six));
		// A version 2 archive: the same books.
		Live.Result old = ArchiveTestKit.build(level, X + 700, Z, 2, false, null);
		Rig two = new Rig(level, old);
		two.enter("dee_s3a", two.plan.at(0, 1, -10));
		two.look(false);
		Records.Slot dee = Records.onWall(level, two.plan, "dee_s3a");
		helper.assertTrue(dee != null && (!voiced || !text(dee.stack()).isBlank()), "no book for dee in version 2: " + Records.titles(level, two.plan));
		helper.succeed();
	}

	// --- 17g ---

	/**
	 * 17g: by the wall, then 24+ away: the record on the wall is a new one (numbered one more, its last line this visit's,
	 * not the old text) and the old one is an edition in the stacks' first shelf (tattered, its number kept, its text);
	 * away without the wall first: nothing; once a visit; in view it waits for the next look out of view; never in their
	 * first hour; a taken record: the new one is just left (its number past the one taken), nothing moves; at the
	 * editions' cap the oldest go to the far end's shelves. Nothing of it reaches a log.
	 */
	public static void recordRewritten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Rig rig = new Rig(level, build(level, X + 1000, Z));
		ArchivePlan plan = rig.plan;
		String name = "ana_s3g";
		boolean voiced = !Voice.of(level.getServer()).blank();
		List<String> written = new ArrayList<>();
		try (com.forzacode.a1016_02.core.LogCapture capture = com.forzacode.a1016_02.core.LogCapture.open()) {
			Player ana = rig.enter(name, plan.at(0, 1, -10));
			rig.look(false);
			Records.Slot first = Records.onWall(level, plan, name);
			helper.assertTrue(first != null && first.number() == 0, "no first record");
			List<String> firstText = Pages.text(first.stack());
			written.addAll(firstText);
			rig.move(ana, awayFromTheWall(plan));
			rig.look(false);
			helper.assertTrue(Records.onWall(level, plan, name).number() == 0, "rewritten without the wall");
			wallThenAway(rig, ana);
			Records.Slot second = Records.onWall(level, plan, name);
			List<Records.Slot> eds = Records.editions(level, plan.editionShelves(), name);
			helper.assertTrue(second != null && second.number() == 1 && Records.generation(second.stack()) == Pages.ORIGINAL,
					"the wall's record after the visit: " + (second == null ? null : second.number()));
			helper.assertTrue(!voiced || !Pages.text(second.stack()).equals(firstText), "the new record reads as the old one");
			helper.assertTrue(eds.size() == 1 && eds.getFirst().number() == 0 && eds.getFirst().pos().equals(plan.editionShelves().getFirst()),
					"the edition: " + numbers(eds));
			List<String> edText = Pages.text(eds.getFirst().stack());
			helper.assertTrue(Records.generation(eds.getFirst().stack()) == Pages.TATTERED && !edText.isEmpty() && (!voiced || edText.getFirst().startsWith(firstText.getFirst())),
					"the edition is not the record as it stood, tattered");
			written.addAll(Pages.text(second.stack()));
			// Once a visit.
			wallThenAway(rig, ana);
			helper.assertTrue(Records.onWall(level, plan, name).number() == 1 && Records.editions(level, plan.editionShelves(), name).size() == 1,
					"twice in a visit");
			// In view: it waits; out of view at the next look, it comes.
			rig.newVisit();
			rig.seen = true;
			wallThenAway(rig, ana);
			helper.assertTrue(Records.onWall(level, plan, name).number() == 1, "rewritten in view");
			rig.seen = false;
			rig.look(false);
			helper.assertTrue(Records.onWall(level, plan, name).number() == 2, "not rewritten once out of view");
			// Never in their first hour.
			rig.newVisit();
			rig.play.put(ana.getUUID(), 1_000L);
			wallThenAway(rig, ana);
			helper.assertTrue(Records.onWall(level, plan, name).number() == 2, "rewritten in their first hour");
			rig.play.remove(ana.getUUID());
			// Taken: the new one is just left, past the number taken; nothing moves.
			rig.newVisit();
			rig.move(ana, plan.at(0, 1, -10));
			rig.look(false);
			Records.Slot onWall = Records.onWall(level, plan, name);
			ItemStack taken = Records.take(level, onWall.pos(), onWall.slot());
			wallThenAway(rig, ana);
			Records.Slot left = Records.onWall(level, plan, name);
			helper.assertTrue(left != null && left.number() == 3 && Records.number(taken) == 2, "after the record was taken: "
					+ (left == null ? null : left.number()));
			helper.assertTrue(numbers(Records.editions(level, plan.editionShelves(), name)).equals(List.of(0, 1)), "something moved: "
					+ numbers(Records.editions(level, plan.editionShelves(), name)));
			// At the cap (2 here) the oldest goes to the far end's shelves.
			rig.newVisit();
			rig.config.editionsMax = 2;
			wallThenAway(rig, ana);
			List<Integer> stacks = numbers(Records.editions(level, plan.editionShelves(), name));
			List<Integer> far = numbers(Records.editions(level, plan.farShelves(), name));
			helper.assertTrue(stacks.equals(List.of(1, 3)) && far.equals(List.of(0)) && Records.onWall(level, plan, name).number() == 4,
					"at the cap: stacks " + stacks + ", far " + far);
			for (Records.Slot s : Records.books(level, Records.stacks(plan))) {
				written.addAll(Pages.text(s.stack()));
			}
			for (com.forzacode.a1016_02.core.LogCapture.Line line : capture.lines()) {
				helper.assertFalse(line.shown() && line.message().contains("left/s17"), "a shown line: " + line.message());
				for (String text : written) {
					for (String row : text.split("\n")) {
						helper.assertTrue(row.length() < 12 || !line.message().contains(row), "a book's text in a log line");
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Two players: two records; both by the wall then away: each rewritten, one edition each (counted per player); with
	 * both online today in the day log (a fact about two players), a record names the friend.
	 */
	public static void twoPlayersRecords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Rig rig = new Rig(level, build(level, X + 2000, Z));
		ArchivePlan plan = rig.plan;
		Player ana = rig.enter("ana_s3t", plan.at(0, 1, -10));
		Player ben = rig.enter("ben_s3t", plan.at(2, 1, -12));
		var server = level.getServer();
		List<HerobrineState.Subject> players = HerobrineState.get(server).players();
		int ia = -1;
		int ib = -1;
		for (int i = 0; i < players.size(); i++) {
			ia = players.get(i).uuid().equals(ana.getUUID()) ? i : ia;
			ib = players.get(i).uuid().equals(ben.getUUID()) ? i : ib;
		}
		DayLog log = DayLog.get(server);
		boolean friends = ia >= 0 && ib >= 0 && ia < DayLog.PLAYERS && ib < DayLog.PLAYERS;
		if (friends) {
			if (log.current() != Books.today(server)) {
				log.open(Books.today(server), 0);
			}
			log.online(ia);
			log.online(ib);
		}
		rig.look(false);
		helper.assertTrue(Records.onWall(level, plan, "ana_s3t") != null && Records.onWall(level, plan, "ben_s3t") != null, "two records: "
				+ Records.titles(level, plan));
		rig.move(ana, byTheWall(plan));
		rig.move(ben, plan.at(-1, ArchivePlan.DAIS_HEIGHT + 1, -plan.length + 1));
		rig.look(false);
		rig.move(ana, awayFromTheWall(plan));
		rig.move(ben, plan.at(2, 1, -22));
		rig.look(false);
		for (String name : List.of("ana_s3t", "ben_s3t")) {
			helper.assertTrue(Records.onWall(level, plan, name).number() == 1, name + " not rewritten");
			helper.assertTrue(numbers(Records.editions(level, plan.editionShelves(), name)).equals(List.of(0)), name + "'s editions: "
					+ numbers(Records.editions(level, plan.editionShelves(), name)));
		}
		// The day log is the server's, shared by every game test: the friend line is asked only on a day the two had alone
		// (in the full suite other tests' players are online the same day, and a record may name one of them instead).
		DayLog.Day today = log.day(Books.today(server));
		boolean alone = today != null && today.who().stream().allMatch(i -> i == ia2(players, ana) || i == ia2(players, ben));
		if (friends && alone && !Voice.of(server).blank()) {
			String a = text(Records.onWall(level, plan, "ana_s3t").stack());
			String b = text(Records.onWall(level, plan, "ben_s3t").stack());
			helper.assertTrue(a.contains("ben_s3t") || b.contains("ana_s3t"), "no record names the friend:\n" + a + "\n--\n" + b);
		}
		helper.succeed();
	}

	// --- the day books ---

	/** The days on gallery {@code k}'s shelves now (each slot's day, or 0). */
	static List<Integer> daysOn(ServerLevel level, Well well, int k) {
		List<Integer> out = new ArrayList<>();
		for (Records.Slot s : Records.books(level, well.dayShelves(k))) {
			Books.dayOf(s.stack()).ifPresent(out::add);
		}
		out.sort(Integer::compare);
		return out;
	}

	static List<Integer> range(int from, int to) {
		List<Integer> out = new ArrayList<>();
		for (int d = from; d <= to; d++) {
			out.add(d);
		}
		return out;
	}

	/**
	 * The day books: on the ground gallery with today 30 (2 days ahead): days 1 to 32, each in its own slot, the past
	 * written, today's as the composer ends it, the days to come blank, none further; nothing in view; idempotent (a look
	 * again leaves none); a stale one replaced at the next visit; deep down with today 200 only the galleries within 2 of
	 * the player's and the ground gallery get theirs.
	 */
	public static void dayBooksInTheWell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Rig rig = new Rig(level, build(level, X + 4000, Z));
		Well well = rig.well;
		rig.days = days(30);
		rig.seen = true;
		Player ana = rig.enter("ana_s3d", rig.gallery(well.kGround, 16));
		rig.look(true);
		helper.assertTrue(daysOn(level, well, well.kDays).isEmpty(), "day books left in view");
		rig.seen = false;
		rig.look(true);
		helper.assertTrue(daysOn(level, well, well.kDays).equals(range(1, 32)), "the ground gallery holds " + daysOn(level, well, well.kDays));
		List<BlockPos> shelves = well.dayShelves(well.kDays);
		for (int d = 1; d <= 32; d++) {
			BlockPos shelf = shelves.get((d - 1) / 6);
			ItemStack stack = ((Container) level.getBlockEntity(shelf)).getItem((d - 1) % 6);
			helper.assertTrue(Books.dayOf(stack).orElse(0) == d, "day " + d + " is not in its slot");
			boolean blank = Pages.text(stack).stream().allMatch(String::isEmpty);
			if (d > 30) {
				helper.assertTrue(blank, "day " + d + " to come is not blank");
			} else {
				helper.assertTrue(Pages.text(stack).equals(Pages.text(rig.days.book(level, d))), "day " + d + " is not the composer's");
				helper.assertTrue(blank == Voice.of(level.getServer()).blank(), "day " + d + " blank: " + blank);
			}
		}
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit.daysLeft == 32, visit.daysLeft + " left");
		rig.look(true);
		helper.assertTrue(visit.daysLeft == 32 && daysOn(level, well, well.kDays).size() == 32, "a look again left more: " + visit.daysLeft);
		// A stale one (day 5 left blank before its day ended) is replaced at the next visit.
		Records.take(level, shelves.getFirst(), 4);
		Records.put(level, shelves.getFirst(), 4, Books.blankDay(5));
		rig.newVisit();
		rig.look(true);
		ItemStack five = ((Container) level.getBlockEntity(shelves.getFirst())).getItem(4);
		helper.assertTrue(Pages.text(five).equals(Pages.text(rig.days.book(level, 5))), "the stale day 5 was not replaced");
		// Deep down, today 200: the galleries within 2 and the ground gallery only.
		rig.days = days(200);
		rig.newVisit();
		int at = well.kDays - 6;
		rig.move(ana, rig.gallery(at, 16));
		rig.look(true);
		rig.look(true); // at most 64 composed a look: the ground gallery's re-read takes two
		helper.assertTrue(daysOn(level, well, at + 2).equals(range(193, 202)), "gallery " + (at + 2) + " holds " + daysOn(level, well, at + 2));
		for (int k = at - 2; k <= at + 1; k++) {
			helper.assertTrue(daysOn(level, well, k).isEmpty(), "gallery " + k + " holds days to come past the 2: " + daysOn(level, well, k));
		}
		for (int k = well.kDays - 3; k <= well.kDays - 1; k++) {
			helper.assertTrue(daysOn(level, well, k).isEmpty(), "gallery " + k + ", 3 or more from the player, holds " + daysOn(level, well, k));
		}
		helper.assertTrue(daysOn(level, well, well.kDays).equals(range(1, 6 * well.dayShelves(well.kDays).size())), "the ground gallery now holds "
				+ daysOn(level, well, well.kDays));
		// sc 17 r: counts, never a text or a name.
		List<String> lines = ArchiveCommands.counts(level, rig.plan, rig.rules.visit(rig.place));
		String all = String.join("\n", lines);
		helper.assertTrue(lines.size() == 4 && all.contains("day books " + DayBooks.count(level, well) + " ") && all.contains("editions 0"), "sc 17 r: " + all);
		helper.assertFalse(all.contains("ana_s3d") || all.contains("not kept") || all.contains("kirbyfan"), "sc 17 r gives a text: " + all);
		helper.succeed();
	}

	// --- 17f ---

	/**
	 * 17f: a player 4 galleries under the ground gallery: nothing before 60 s; then a gallery's lights every 160 ticks from
	 * the top down (19, 18, 17, 16), every light of each (glowstone and the fault's light blocks), never the player's
	 * gallery or near it, until the glowstone cap; the glimpse once after the third, on a dark gallery 1 to 3 over the
	 * player, across the void, 20 to 30 off; answered; in view it waits; not again the same day; under the health floor,
	 * refused; given back by the ledger.
	 */
	public static void wellGoesDarkFromAbove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 3000, Z);
		Rig rig = new Rig(level, built);
		Well well = rig.well;
		int deep = well.kGround - 4;
		Player ana = rig.enter("ana_s3f", rig.gallery(deep, 16));
		Map<Integer, Integer> before = new HashMap<>();
		for (int k = well.kMin; k <= well.kTop; k++) {
			before.put(k, WellDark.present(level, well, k).size());
		}
		// Under the health floor: refused, nothing taken (then a new day for the rest).
		ana.setHealth(9.0F);
		rig.look(false);
		rig.now += 1300;
		rig.look(false);
		ArchiveRules.Visit hurt = rig.rules.visit(rig.place);
		helper.assertTrue(hurt != null && hurt.wellOver && hurt.wellDark.isEmpty() && hurt.wellWhy != null && hurt.wellWhy.contains("health"),
				"under 10: " + (hurt == null ? null : hurt.wellWhy));
		ana.setHealth(ana.getMaxHealth());
		rig.day++;
		rig.newVisit();
		rig.look(false);
		rig.now += 1100;
		rig.look(false);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.wellDark.isEmpty() && !visit.wellStarted, "dark before 60 s");
		rig.now += 100;
		rig.seen = true;
		rig.look(false);
		helper.assertTrue(visit.wellStarted && visit.wellDark.isEmpty() && rig.answers.waiting(rig.place, ArchiveRules.WELL), "a gallery went in view");
		rig.seen = false;
		rig.answers.tryAll(level.getServer());
		List<Integer> order = new ArrayList<>(visit.wellDark);
		for (int step = 0; step < 40 && !visit.wellOver; step++) {
			rig.now += 200;
			rig.look(false);
			for (int k : visit.wellDark) {
				if (!order.contains(k)) {
					order.add(k);
				}
			}
		}
		helper.assertTrue(order.equals(List.of(well.kTop, well.kTop - 1, well.kTop - 2, well.kTop - 3)), "the galleries went " + order + " (" + visit.wellWhy
				+ ")");
		int gone = 0;
		for (int k : order) {
			helper.assertTrue(WellDark.present(level, well, k).isEmpty(), "a light of gallery " + k + " stayed");
			helper.assertTrue(k >= deep + 2, "gallery " + k + " near the player went");
			gone += before.get(k);
		}
		for (int k = well.kMin; k < well.kTop - 3; k++) {
			helper.assertTrue(WellDark.present(level, well, k).size() == before.get(k), "gallery " + k + " lost lights");
		}
		helper.assertTrue(visit.wellLightsGone <= ArchiveRules.WELL_LIGHTS_MAX, visit.wellLightsGone + " glowstone went");
		helper.assertTrue(rig.glimpses.size() == 1, rig.glimpses.size() + " glimpses");
		Vec3 spot = rig.glimpses.getFirst();
		int up = well.galleryAt((int) Math.floor(spot.y)) - deep;
		double off = spot.distanceTo(ana.position());
		helper.assertTrue(up >= 1 && up <= 3 && off >= 20 && off <= 30 && Math.hypot(spot.x - ana.getX(), spot.z - ana.getZ()) >= 17,
				"the glimpse " + up + " galleries up, " + off + " off");
		helper.assertTrue(rig.answered.contains("17f:ana_s3f"), "not answered: " + rig.answered);
		// Not again the same day (a new visit).
		rig.newVisit();
		rig.look(false);
		rig.now += 1300;
		rig.look(false);
		ArchiveRules.Visit again = rig.rules.visit(rig.place);
		helper.assertTrue(again.wellOver && again.wellDark.isEmpty(), "again the same day: " + again.wellWhy);
		// Given back: the ledger puts every light back.
		int given = ArchiveTestKit.undo(level, ArchiveRules.WELL_CAUSE, built.box());
		helper.assertTrue(given == gone, given + " given back of " + gone);
		for (int k : order) {
			helper.assertTrue(WellDark.present(level, well, k).size() == before.get(k), "gallery " + k + " not given back");
		}
		helper.succeed();
	}

	/**
	 * 17f with two players in the well, one deep and one on a gallery over the land: the dark stops two galleries over the
	 * higher one (never their gallery or the one above), and comes for the deep one's time.
	 */
	public static void wellDarkBetweenFriends(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 5000, Z);
		Rig rig = new Rig(level, built);
		Well well = rig.well;
		int high = well.kTop - 4;
		rig.enter("ana_s3w", rig.gallery(well.kGround - 4, 16));
		rig.enter("ben_s3w", rig.gallery(high, 20));
		rig.look(false);
		rig.now += 1300;
		ArchiveRules.Visit visit = null;
		for (int step = 0; step < 40; step++) {
			rig.look(false);
			visit = rig.rules.visit(rig.place);
			if (visit.wellOver) {
				break;
			}
			rig.now += 200;
		}
		List<Integer> dark = new ArrayList<>(visit.wellDark);
		dark.sort((a, b) -> b - a);
		helper.assertTrue(dark.equals(List.of(well.kTop, well.kTop - 1, well.kTop - 2)), "the galleries " + dark + " with a friend on " + high + " ("
				+ visit.wellWhy + ")");
		helper.assertTrue(WellDark.present(level, well, high + 1).size() == WellDark.lights(well, high + 1).size()
				&& WellDark.present(level, well, high).size() == WellDark.lights(well, high).size(), "the friend's gallery or the one above went");
		ArchiveTestKit.undo(level, ArchiveRules.WELL_CAUSE, built.box());
		helper.succeed();
	}

	// --- 17b ---

	/**
	 * 17b on version 3's floating grid: after 60 s deep in the hall, rows 0 to 7 from the door toward the player, the
	 * floating glowstone at 17 among them, never a light within 6 of the player nor their row or the next; the glimpse
	 * after the fourth; given back.
	 */
	public static void lightsOutOnTheFloatingGrid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 6000, Z);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		BlockPos feet = plan.at(1, 1, -50);
		rig.enter("ana_s3b", feet);
		rig.look(false);
		rig.now += 1100;
		rig.look(false);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.dark.isEmpty(), "dark before 60 s");
		List<Integer> order = new ArrayList<>();
		for (int step = 0; step < 40 && !visit.darkOver; step++) {
			rig.now += 200;
			rig.look(false);
			for (int u : visit.dark) {
				if (!order.contains(u)) {
					order.add(u);
				}
			}
		}
		helper.assertTrue(order.equals(List.of(0, 1, 2, 3, 4, 5, 6, 7)), "the rows went " + order + " (" + visit.darkWhy + ")");
		int floating = 0;
		for (BlockPos light : ArchiveTestKit.lightsOf(plan, visit.dark)) {
			helper.assertFalse(level.getBlockState(light).is(Blocks.GLOWSTONE), "a light stayed at " + light.toShortString());
			helper.assertTrue(light.distSqr(feet) > 36, "a light within 6 went");
			floating += light.getY() == plan.floorY() + ArchivePlan.FLOAT3 ? 1 : 0;
		}
		helper.assertTrue(floating > 0, "no floating light went");
		int row = ArchivePlan.rowAt(plan.localF(feet.getX(), feet.getZ()));
		for (int k = row - 1; k <= row + 1; k++) {
			helper.assertTrue(glowstone(level, plan.rowLights(k)) == plan.rowLights(k).size(), "row " + k + " by the player went");
		}
		helper.assertTrue(rig.glimpses.size() == 1, rig.glimpses.size() + " glimpses");
		int given = ArchiveTestKit.undo(level, ArchiveRules.DARK_CAUSE, built.box());
		helper.assertTrue(given == visit.lightsGone, given + " given back of " + visit.lightsGone);
		helper.succeed();
	}

	// --- asleep, creative ---

	/**
	 * Asleep (the world under Traces) nothing of the archive's comes: no record, no rewrite, no day book, no dark in the
	 * well; a creative player is never inside: the same; a survival player awake gets theirs (the rig works).
	 */
	public static void asleepAndCreativeRefuseAll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 7000, Z);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		Well well = rig.well;
		rig.days = days(30);
		int six = wallBooks(level, plan);
		rig.story = Answers.ASLEEP;
		Player ana = rig.enter("ana_s3z", plan.at(0, 1, -10));
		Player cid = rig.enter("cid_s3z", plan.at(2, 1, -10), GameType.CREATIVE);
		for (int pass = 0; pass < 2; pass++) {
			rig.look(true);
			wallThenAway(rig, ana);
			rig.move(cid, byTheWall(plan));
			rig.look(true);
			rig.move(cid, awayFromTheWall(plan));
			rig.look(true);
			rig.move(ana, rig.gallery(well.kGround - 4, 16));
			rig.move(cid, rig.gallery(well.kGround - 4, 20));
			rig.look(true);
			rig.now += 1300;
			rig.look(true);
			rig.now += 400;
			rig.look(true);
			helper.assertTrue(wallBooks(level, plan) == six, (pass == 0 ? "asleep" : "creative") + ": a record was left");
			helper.assertTrue(daysOn(level, well, well.kDays).isEmpty(), (pass == 0 ? "asleep" : "creative") + ": day books were left");
			ArchiveRules.Visit visit = rig.rules.visit(rig.place);
			helper.assertTrue(visit == null || visit.wellDark.isEmpty() && !visit.wellStarted, (pass == 0 ? "asleep" : "creative") + ": the well went dark");
			// Awake now; ana leaves, only the creative one stays.
			rig.story = null;
			rig.inside.remove(ana);
			rig.newVisit();
			rig.move(cid, plan.at(0, 1, -10));
		}
		// The control: a survival player awake.
		rig.inside.add(ana);
		rig.move(ana, rig.gallery(well.kGround, 16));
		rig.look(true);
		helper.assertTrue(wallBooks(level, plan) == six + 1 && !daysOn(level, well, well.kDays).isEmpty(), "awake, the survival player got nothing");
		helper.succeed();
	}

	/** A player's index among the known players, or -1. */
	private static int ia2(List<HerobrineState.Subject> players, Player p) {
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).uuid().equals(p.getUUID())) {
				return i;
			}
		}
		return -1;
	}
}
