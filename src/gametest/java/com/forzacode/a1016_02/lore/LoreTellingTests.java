package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

/**
 * The telling: name detection, TELLING and Stage 3, writing near traces, "Stop." once, blank signs, place not
 * found, the list's cause, the ending hooks and {@code [PLAYER NAME]}. Most tests use their own
 * {@link HerobrineState} and {@link TellingData}; the two that check Stage 3 go through the live path on purpose.
 */
public class LoreTellingTests extends LorePlacementTests {
	/** Every TELLING fired during the run: "text|namesHim". */
	static final List<String> TOLD = new CopyOnWriteArrayList<>();

	static {
		HerobrineEvents.TELLING.register((player, text, pos, namesHim) -> TOLD.add(text + "|" + namesHim));
	}

	static final Telling.Traces NO_TRACES = (pos, radius) -> false;

	/** A sign edit as the test sees it: where, the front lines and the back lines asked for. */
	record Edit(BlockPos pos, List<String> front, List<String> back) {
	}

	/** Core's sign editor (forced: the test level has no players), recording every edit it made. */
	static final class TestEditor implements SignEdits.Editor {
		final List<Edit> edits = new ArrayList<>();
		private final SignEdits.Editor real = SignEdits.editor(Services.traces().forced());

		@Override
		public boolean edit(ServerLevel level, BlockPos pos, List<String> front, List<String> back, String cause) {
			if (!real.edit(level, pos, front, back, cause)) {
				return false;
			}
			edits.add(new Edit(pos.immutable(), front, back));
			return true;
		}
	}

	static ServerPlayer mock(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	/** A fresh state already in Telling (fires STAGE_CHANGED, which only touches the dusk fog in a test world). */
	static HerobrineState telling(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		state.setStage(helper.getLevel().getServer(), Stage.TELLING);
		return state;
	}

	/** Puts a standing sign with this front text (as the player typed it) and tells the watcher about it. */
	static Telling.Told sign(GameTestHelper helper, BlockPos rel, ServerPlayer player, String text, HerobrineState state, TellingData data,
			Telling.Traces traces) {
		helper.setBlock(rel, Placers.standingSign(Direction.NORTH));
		BlockPos pos = helper.absolutePos(rel);
		SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(pos);
		List<String> lines = new ArrayList<>(List.of(text.split("\n")));
		sign.setText(signText(lines), SignTextSlot.FRONT);
		return Telling.writeSign(player, helper.getLevel(), pos, SignEdits.text(sign), state, data, traces);
	}

	/** A sign side with these lines, as a player would type them (a test fixture, not his edit). */
	static SignText signText(List<String> lines) {
		List<Component> messages = new ArrayList<>();
		for (int n = 0; n < 4; n++) {
			messages.add(Component.literal(n < lines.size() ? lines.get(n) : ""));
		}
		return new SignText(messages, messages, DyeColor.BLACK, false);
	}

	static List<String> frontOf(GameTestHelper helper, BlockPos abs) {
		return SignEdits.front((SignBlockEntity) helper.getLevel().getBlockEntity(abs));
	}

	static List<String> backOf(GameTestHelper helper, BlockPos abs) {
		return SignEdits.back((SignBlockEntity) helper.getLevel().getBlockEntity(abs));
	}

	static ItemStack writable(String... pages) {
		ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
		stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(pages).stream().map(Filterable::passThrough).toList()));
		return stack;
	}

	// --- detection ---

	@GameTest
	public void hisNameIsFoundInEveryDisguise(GameTestHelper helper) {
		for (String text : List.of("Herobrine", "HEROBRINE", "hero brine", "H.E.R.O.B.R.I.N.E", "her0br1ne", "h3r0br1n3", "Hérobrine",
				"i saw herobrine!", "hero\nbrine", "sorry, h-e-r-o-b-r-i-n-e", "HeRoBrInE?")) {
			helper.assertTrue(NameMatcher.namesHim(text), "missed his name in '" + text + "'");
		}
		for (String text : List.of("hero", "brine", "heroine", "a hero by the brine", "herobrain", "", "the developer")) {
			helper.assertFalse(NameMatcher.namesHim(text), "found his name in '" + text + "'");
		}
		helper.succeed();
	}

	/** The live path: a sign written through vanilla's own sign update fires TELLING and starts Stage 3. */
	@GameTest
	public void namingHimOnASignStartsTelling(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		state.setStage(server, Stage.PROXIMITY);
		ServerPlayer player = mock(helper);
		helper.setBlock(new BlockPos(2, 1, 2), Placers.standingSign(Direction.NORTH));
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(pos);
		String line = "her0 br1ne " + pos.toShortString();
		sign.setAllowedPlayerEditor(player.getUUID());
		sign.updateSignText(player, SignTextSlot.FRONT, List.of(FilteredText.passThrough(line), FilteredText.EMPTY, FilteredText.EMPTY,
				FilteredText.EMPTY));
		helper.assertTrue(TOLD.contains(line + "|true"), "no TELLING naming him for the sign (" + TOLD + ")");
		helper.assertTrue(state.stage() == Stage.TELLING, "the first telling did not start Stage 3: " + state.stage());
		helper.assertTrue(state.tellingStarted(), "tellingStarted was not set");
		helper.assertTrue(TellingData.get(server).sign(GlobalPos.of(helper.getLevel().dimension(), pos)).map(TellingData.WrittenSign::namesHim)
				.orElse(false), "the sign about him is not remembered");
		helper.assertTrue(Telling.countFromFlags(state) == TellingData.get(server).count() && LoreApi.tellingCount(server) > 0,
				"the telling count is not mirrored into the flags");
		helper.succeed();
	}

	/** The live path for books: signing a book that names him (a variant) fires TELLING and starts Stage 3. */
	@GameTest
	public void namingHimInABookStartsTelling(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		state.setStage(server, Stage.PROXIMITY);
		ServerPlayer player = mock(helper);
		String page = "day 4. HERO-BRINE again " + helper.absolutePos(BlockPos.ZERO).toShortString();
		ItemStack book = writable(page);
		ItemStack signed = book.transmuteCopy(Items.WRITTEN_BOOK);
		signed.remove(DataComponents.WRITABLE_BOOK_CONTENT);
		signed.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("notes"), "Tester", 0,
				List.of(Filterable.passThrough(Component.literal(page))), true));
		player.getInventory().setItem(0, signed);
		Telling.onBookWritten(player, 0, true);
		helper.assertTrue(TOLD.stream().anyMatch(t -> t.contains(page) && t.endsWith("|true")), "no TELLING naming him for the book");
		helper.assertTrue(state.stage() == Stage.TELLING, "the book did not start Stage 3: " + state.stage());
		helper.assertTrue(Telling.bookId(player.getInventory().getItem(0)).isPresent(), "the book about him was not stamped");
		helper.succeed();
	}

	@GameTest
	public void booksAndChatTellButOnlyWhenTheyChange(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		ServerPlayer player = mock(helper);
		ItemStack book = writable("i keep seeing him", "her0br1ne. in the fog.");
		Telling.Told first = Telling.writeBook(player, book, false, state, data, NO_TRACES);
		helper.assertTrue(first.told() && first.namesHim(), "a book naming him did not tell");
		String id = Telling.bookId(book).orElseThrow(() -> helper.assertionException(Component.literal("the book was not stamped")));
		helper.assertFalse(Telling.writeBook(player, book, false, state, data, NO_TRACES).told(), "saving it unchanged told again");
		ItemStack signed = book.transmuteCopy(Items.WRITTEN_BOOK);
		signed.remove(DataComponents.WRITABLE_BOOK_CONTENT);
		signed.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("him"), "Tester", 0,
				List.of(Filterable.passThrough(Component.literal("her0br1ne. in the fog."))), true));
		helper.assertTrue(Telling.writeBook(player, signed, true, state, data, NO_TRACES).told(), "signing it did not tell");
		helper.assertTrue(Telling.bookId(signed).equals(Optional.of(id)), "signing lost the book's id");
		helper.assertFalse(Telling.writeBook(player, writable("a shopping list"), false, state, data, NO_TRACES).told(), "a plain book told");
		helper.assertTrue(Telling.chat(player, "sorry h e r o b r i n e", state, data).told(), "naming him in chat did not tell");
		helper.assertFalse(Telling.chat(player, "anyone online?", state, data).told(), "plain chat told");
		helper.assertTrue(data.count() == 3 && state.tellingStarted(), "count " + data.count());
		helper.succeed();
	}

	@GameTest
	public void writingNearHisTracesCounts(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mock(helper);
		BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
		SiteRegistry.Site near = new SiteRegistry.Site(-1, SiteType.RUINED_HUT, level.dimension(), here.offset(30, 0, 0), 3, Optional.empty());
		SiteRegistry.Site far = new SiteRegistry.Site(-2, SiteType.RUINED_HUT, level.dimension(), here.offset(60, 0, 0), 3, Optional.empty());
		Telling.Traces nearSite = (pos, r) -> Telling.near(pos, r, List.of(near), List.of());
		Telling.Traces farSite = (pos, r) -> Telling.near(pos, r, List.of(far), List.of());

		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		Telling.Told plainFar = sign(helper, new BlockPos(2, 1, 2), player, "meet at the hut", state, data, farSite);
		helper.assertFalse(plainFar.told() || plainFar.recorded(), "a plain sign far from his traces told");
		int before = TOLD.size();
		Telling.Told plainNear = sign(helper, new BlockPos(3, 1, 2), player, "meet at the hut", state, data, nearSite);
		helper.assertTrue(plainNear.told() && plainNear.nearTraces() && !plainNear.namesHim(), "a sign near his traces did not tell");
		helper.assertTrue(TOLD.size() > before && TOLD.getLast().endsWith("|false"), "TELLING for the sign near traces should not name him");
		helper.assertTrue(data.count() == 1 && state.tellingStarted(), "count " + data.count());

		GlobalPos at = GlobalPos.of(level.dimension(), here);
		TraceLedger.Entry his = new TraceLedger.Entry(TraceLedger.Kind.REMOVE, "dig:tunnel", 0, GlobalPos.of(level.dimension(), here.offset(0, -20, 0)),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), -1, -1);
		TraceLedger.Entry left = new TraceLedger.Entry(TraceLedger.Kind.REMOVE, "lore:left/F09", 0, GlobalPos.of(level.dimension(), here.offset(0, -5, 0)),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), -1, -1);
		helper.assertTrue(Telling.near(at, 32, List.of(), List.of(his)), "an edit of his 20 blocks away is not a trace");
		helper.assertFalse(Telling.near(at, 32, List.of(), List.of(left)), "what others left counted as his trace");
		player.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(here), 0.0F, 0.0F);
		helper.assertTrue(Telling.writeBook(player, writable("we built a hut"), false, state, data, nearSite).nearTraces(),
				"a book written near his traces did not tell");
		helper.succeed();
	}

	// --- "Stop." ---

	@GameTest
	public void stopAppearsOnce(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = mock(helper);
		HerobrineState state = telling(helper);
		TellingData data = new TellingData();
		TestEditor editor = new TestEditor();
		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, NAME) == FireResult.SKIPPED, "Stop. fired with nothing written");
		sign(helper, new BlockPos(2, 1, 2), player, "herobrine was here", state, data, NO_TRACES);
		sign(helper, new BlockPos(4, 1, 2), player, "herobrine again", state, data, NO_TRACES);
		BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertTrue(data.stopCandidate().map(GlobalPos::pos).equals(Optional.of(first)), "the first sign about him is not the Stop. sign");

		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, NAME) == FireResult.FIRED, "Stop. did not fire");
		helper.assertTrue(state.stopFired() && data.stopSign().map(GlobalPos::pos).equals(Optional.of(first)), "stopFired or the sign not set");
		helper.assertTrue(editor.edits.size() == 1 && editor.edits.getFirst().pos().equals(first), "edits " + editor.edits);
		List<String> stop = List.of("", "Stop.", "", "");
		helper.assertTrue(editor.edits.getFirst().front().equals(stop) && fragment("F03").lines().equals(stop), "Stop. is not F03's text");
		helper.assertTrue(editor.edits.getFirst().back().isEmpty(), "the back was not blanked");
		helper.assertTrue(frontOf(helper, first).equals(stop), "the sign reads " + frontOf(helper, first));
		helper.assertTrue(backOf(helper, first).stream().allMatch(String::isEmpty), "the back still has text");

		helper.assertTrue(TraceLedger.get(server).entries().stream().anyMatch(e -> e.kind() == TraceLedger.Kind.BLOCK_ENTITY
				&& e.cause().equals("lore:his/F03") && e.pos().pos().equals(first)), "the Stop. edit is not in the ledger (Ending D could not undo it)");
		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, NAME) == FireResult.SKIPPED, "Stop. fired twice");
		sign(helper, new BlockPos(6, 1, 2), player, "HEROBRINE", state, data, NO_TRACES);
		helper.assertTrue(data.stopCandidate().isEmpty(), "a new Stop. candidate after it fired");
		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, NAME) == FireResult.SKIPPED && editor.edits.size() == 1,
				"Stop. appeared a second time");
		helper.succeed();
	}

	// --- blank sign ---

	@GameTest
	public void blankSignOnlyTakesThePlayersOwnSignsAfterTelling(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = mock(helper);
		ServerPlayer other = mock(helper);
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		TestEditor editor = new TestEditor();
		sign(helper, new BlockPos(1, 1, 1), player, "my base", state, data, NO_TRACES);
		sign(helper, new BlockPos(3, 1, 1), player, "herobrine", state, data, NO_TRACES);
		sign(helper, new BlockPos(5, 1, 1), player, "wheat farm", state, data, NO_TRACES);
		sign(helper, new BlockPos(1, 1, 5), other, "not yours", state, data, NO_TRACES);
		BlockPos before = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos telling = helper.absolutePos(new BlockPos(3, 1, 1));
		BlockPos after = helper.absolutePos(new BlockPos(5, 1, 1));
		BlockPos others = helper.absolutePos(new BlockPos(1, 1, 5));
		helper.assertTrue(TellingCards.blankPool(player, data).stream().map(s -> s.pos().pos()).toList().equals(List.of(after)),
				"blank pool " + TellingCards.blankPool(player, data));

		helper.assertTrue(TellingCards.fireBlank(server, player, data, editor, RandomSource.create(5)) == FireResult.FIRED, "no sign came back blank");
		helper.assertTrue(editor.edits.size() == 1 && editor.edits.getFirst().pos().equals(after), "edits " + editor.edits);
		helper.assertTrue(editor.edits.getFirst().front().isEmpty() && editor.edits.getFirst().back().isEmpty(), "the sign was not blanked");
		helper.assertTrue(TellingCards.fireBlank(server, player, data, editor, RandomSource.create(5)) == FireResult.SKIPPED, "blanked twice");
		helper.assertTrue(frontOf(helper, after).stream().allMatch(String::isEmpty), "the sign still reads " + frontOf(helper, after));
		for (BlockPos kept : List.of(before, telling, others)) {
			helper.assertFalse(frontOf(helper, kept).stream().allMatch(String::isEmpty), "a sign it may not take was blanked: " + kept);
		}

		// The sign that will say "Stop." is never blanked.
		HerobrineState inTelling = telling(helper);
		sign(helper, new BlockPos(5, 1, 5), player, "herobrine took it", inTelling, data, NO_TRACES);
		helper.assertTrue(data.stopCandidate().map(GlobalPos::pos).equals(Optional.of(helper.absolutePos(new BlockPos(5, 1, 5)))), "no candidate");
		helper.assertTrue(TellingCards.blankPool(player, data).isEmpty(), "the Stop. candidate is in the blank pool");
		helper.succeed();
	}

	// --- place not found ---

	/** A cobble hut with a chest (the fragment) and a wall sign; and, apart, the panic tower chest of F23. */
	@GameTest(skyAccess = true, maxTicks = 200)
	public void placeNotFoundComesAfterStopAndSparesTheEndingDChain(GameTestHelper helper) {
		floor(helper);
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				boolean wall = x == 1 || x == 5 || z == 1 || z == 5;
				if (wall && !(x == 3 && z == 5)) {
					helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
					helper.setBlock(x, 2, z, Blocks.MOSSY_COBBLESTONE);
				}
			}
		}
		helper.setBlock(2, 1, 2, Blocks.CHEST);
		helper.setBlock(3, 2, 2, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH));
		SignBlockEntity hutSign = (SignBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 2)));
		hutSign.setText(signText(List.of("keep out")), SignTextSlot.FRONT);
		helper.setBlock(7, 1, 7, Blocks.COBBLESTONE);
		helper.setBlock(7, 2, 7, Blocks.CHEST);

		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		state.setFragmentPlaced("F23", GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(7, 2, 7))));
		state.setFragmentPlaced("F01", GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 1, 2))));
		data.addVisited("F23");
		data.addVisited("F01");
		LoreConfig config = new LoreConfig();
		config.notFoundSeedRadius = 2;
		TestEditor editor = new TestEditor();
		for (String id : List.of("F07", "F13", "F23", "F25", "F28", "F30")) {
			helper.assertFalse(PlaceNotFound.mayTake(id), id + " of the Ending D chain may be taken");
		}
		BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertTrue(PlaceNotFound.plan(level, anchor, anchor, config, p -> false, List.of(), RandomSource.create(3)).isPresent(),
				"the hut is not a structure");
		helper.assertTrue(PlaceNotFound.plan(level, anchor, anchor, config, p -> false, List.of(helper.absolutePos(new BlockPos(6, 1, 3))),
				RandomSource.create(3)).isEmpty(), "a place the Ending D chain needs is right beside it, yet it would go");
		helper.assertTrue(PlaceNotFound.plan(level, anchor, anchor, config, p -> true, List.of(), RandomSource.create(3)).isEmpty(),
				"the player's own blocks counted as the site");
		helper.assertTrue(PlaceNotFound.fire(server, state, data, Services.traces().forced(), editor, Optional.empty(), RandomSource.create(3),
				config) == FireResult.SKIPPED, "place not found before Stop.");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(1, 1, 1))).is(Blocks.COBBLESTONE), "the hut changed early");
		state.setStopFired(true);
		helper.succeedWhen(() -> {
			if (data.notFound().isEmpty()) {
				FireResult result = PlaceNotFound.fire(server, state, data, Services.traces().forced(), editor, Optional.empty(),
						RandomSource.create(3), config);
				helper.assertTrue(result != FireResult.SKIPPED, "place not found skipped after Stop.");
				helper.assertTrue(result == FireResult.FIRED, "waiting: " + result);
			}
			helper.assertTrue(data.notFound().equals(Optional.of("F01")) && state.hasFlag(PlaceNotFound.DONE_FLAG), "not F01's place: " + data.notFound());
			for (int x = 1; x <= 5; x++) {
				for (int z = 1; z <= 5; z++) {
					helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, 0, z));
					helper.assertFalse(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.MOSSY_COBBLESTONE), "a wall is left at " + x + " " + z);
				}
			}
			helper.assertBlockNotPresent(Blocks.CHEST, new BlockPos(2, 1, 2));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 2));
			helper.assertBlockPresent(Blocks.OAK_SIGN, new BlockPos(3, 1, 3));
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).getBlock() instanceof StandingSignBlock, "the sign does not stand");
			helper.assertTrue(state.fragmentsPlaced().get("F04").pos().equals(helper.absolutePos(new BlockPos(3, 1, 3))), "F04 not recorded at the sign");
			helper.assertTrue(editor.edits.size() == 1 && editor.edits.getFirst().front().isEmpty(), "the moved sign was not blanked");
			helper.assertTrue(frontOf(helper, helper.absolutePos(new BlockPos(3, 1, 3))).stream().allMatch(String::isEmpty), "the sign has text");
			helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(7, 1, 7));
			helper.assertBlockPresent(Blocks.CHEST, new BlockPos(7, 2, 7));
			helper.assertTrue(PlaceNotFound.fire(server, state, data, Services.traces().forced(), editor, Optional.empty(),
					RandomSource.create(3), config) == FireResult.SKIPPED, "place not found fired twice");
		});
	}

	// --- the list ---

	@GameTest
	public void theListGainsTheCauseAfterAMarkedDeath(GameTestHelper helper) {
		Fragment f23 = fragment("F23");
		List<String> lines = lines(LiveBooks.pages(f23, NAME, Optional.of("lava"), false));
		helper.assertTrue(lines.getLast().equals(NAME + " . lava"), "F23 ends " + lines.getLast());
		helper.assertTrue(lines.subList(0, lines.size() - 1).equals(DESIGN_TEXT.get("F23").subList(0, lines.size() - 1)), "other lines changed");
		helper.assertTrue(lines(LiveBooks.pages(f23, NAME, Optional.empty(), false)).getLast().equals(NAME + " ."), "no death, yet a cause");

		ServerPlayer player = mock(helper);
		TellingData data = new TellingData();
		LiveBooks.onMarkedDeath(player, "fell", data);
		helper.assertTrue(data.listCause().equals(Optional.of("fell")), "the cause was not kept");

		// Live: MARKED_DEATH, then the book is opened.
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineEvents.MARKED_DEATH.invoker().onMarkedDeath(player, "drowned", player.blockPosition());
		ItemStack list = FragmentItems.book(f23, NAME);
		helper.assertTrue(LiveBooks.refreshForReading(list, player), "opening F23 did not update it");
		String name = LiveBooks.name(server, player);
		helper.assertTrue(lines(pages(list)).getLast().equals(name + " . drowned"), "F23 reads " + pages(list));
		helper.assertFalse(LiveBooks.refreshForReading(list, player), "an up-to-date F23 changed again");
		ItemStack first = FragmentItems.book(fragment("F06"), NAME);
		helper.assertFalse(LiveBooks.refreshForReading(first, player), "F06 changed");
		helper.succeed();
	}

	@GameTest
	public void thePlayersNameIsWrittenEverywhere(GameTestHelper helper) {
		for (Fragment fragment : FragmentData.all()) {
			List<String> texts = new ArrayList<>(LiveBooks.pages(fragment, NAME, Optional.of("fell"), true));
			texts.addAll(fragment.linesFor(NAME));
			texts.addAll(pages(FragmentItems.book(fragment, NAME)));
			FragmentItems.signText(fragment, NAME).getMessages(false).forEach(c -> texts.add(c.getString()));
			for (String text : texts) {
				helper.assertFalse(text.contains(Fragment.PLAYER_NAME), fragment.id() + " still has the placeholder: " + text);
			}
		}
		helper.assertTrue(lines(pages(FragmentItems.book(fragment("F06"), NAME))).getLast().equals(NAME + " ."), "F06 lost the name");
		helper.assertTrue(fragment("F10").lastLine().equals(Optional.of("* removed [PLAYER NAME]")), "F10's last line is not DESIGN.md's");
		List<String> f10 = lines(LiveBooks.pages(fragment("F10"), NAME, Optional.empty(), true));
		helper.assertTrue(f10.getLast().equals("* removed " + NAME) && f10.size() == 6, "F10 reads " + f10);
		helper.assertTrue(lines(LiveBooks.pages(fragment("F10"), NAME, Optional.empty(), false)).equals(DESIGN_TEXT.get("F10")), "F10 changed early");
		helper.succeed();
	}

	// --- destroying, burning ---

	@GameTest
	public void destroyingYourOwnWritingIsNoticed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = mock(helper);
		ServerPlayer other = mock(helper);
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		sign(helper, new BlockPos(2, 1, 2), player, "herobrine", state, data, NO_TRACES);
		GlobalPos at = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));
		helper.assertFalse(Telling.signBroken(other, at, data), "someone else's sign counted as their own");
		helper.assertTrue(data.sign(at).isEmpty(), "a broken sign is still remembered");
		sign(helper, new BlockPos(2, 1, 2), player, "herobrine", state, data, NO_TRACES);
		helper.assertTrue(Telling.signBroken(player, at, data), "breaking your own sign about him was not noticed");

		ItemStack book = writable("herobrine");
		Telling.writeBook(player, book, false, state, data, NO_TRACES);
		String id = Telling.bookId(book).orElseThrow();
		helper.assertFalse(Telling.bookDestroyed(other, id, data), "someone else destroyed it, yet it counted");
		Telling.writeBook(player, writable("herobrine"), false, state, data, NO_TRACES);
		String second = data.books().getLast().id();
		helper.assertTrue(Telling.bookDestroyed(player, second, data), "burning your own book about him was not noticed");

		TellingData burns = new TellingData();
		GlobalPos where = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO));
		int max = LoreConfig.get().listPyramidMax;
		for (int n = 0; n < max + 2; n++) {
			Telling.listBurned(player, where, burns);
		}
		helper.assertTrue(burns.burned().lists() == max + 2 && burns.burned().owed() == max && burns.burned().pending() == max,
				"burnt " + burns.burned());
		ListPyramid.recordRaised(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), burns);
		helper.assertTrue(burns.burned().pending() == max - 1, "a raised pyramid is still owed");
		helper.assertTrue(Services.sites().find(SiteType.OCEAN_PYRAMID, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3))), 0)
				.stream().anyMatch(s -> s.pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))), "the new pyramid is not a site");
		helper.succeed();
	}

	/** The live path: a book about him burnt in lava by the player who wrote it (lore's item mixin). */
	@GameTest
	public void burningYourOwnBookAboutHimIsNoticed(GameTestHelper helper) throws ClassNotFoundException {
		// The book mixin's target loads (and its hooks apply) only when a player connects; load it here so a bad hook fails the build.
		Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mock(helper);
		ItemStack book = writable("herobrine " + helper.absolutePos(BlockPos.ZERO).toShortString());
		TellingData data = TellingData.get(server);
		Telling.writeBook(player, book, false, HerobrineState.get(server), data, NO_TRACES);
		String id = Telling.bookId(book).orElseThrow(() -> helper.assertionException(Component.literal("the book was not stamped")));
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, book);
		item.setThrower(player);
		item.hurtServer(level, level.damageSources().lava(), 10.0F);
		helper.assertTrue(item.isRemoved(), "the book did not burn");
		helper.assertTrue(data.book(id).isEmpty(), "the burnt book about him is still remembered");
		helper.succeed();
	}

	/** The untouched grove is one of core's protected areas, so world's new scars skip it. */
	@GameTest
	public void theUntouchedGroveIsProtected(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		boolean hadGrove = Services.protectedAreas().get(UntouchedGrove.AREA_ID).isPresent();
		BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
		if (!hadGrove) {
			UntouchedGrove.protect(server, GlobalPos.of(helper.getLevel().dimension(), center));
			helper.assertTrue(Services.protectedAreas().isProtected(helper.getLevel().dimension(), center.offset(UntouchedGrove.RADIUS, 40, 0)),
					"the grove is not protected to its edge and full height");
			helper.assertFalse(Services.protectedAreas().isProtected(helper.getLevel().dimension(), center.offset(UntouchedGrove.RADIUS + 1, 0, 0)),
					"the protection reaches past the grove");
			Services.protectedAreas().unprotect(UntouchedGrove.AREA_ID);
		}
		helper.succeed();
	}

	// --- ending hooks ---

	@GameTest
	public void endingHooksFinishF10AndPlaceF20UnderIt(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		helper.setBlock(3, 1, 3, Blocks.CHEST);
		BlockPos chest = helper.absolutePos(new BlockPos(3, 1, 3));
		((Container) level.getBlockEntity(chest)).setItem(0, FragmentItems.book(fragment("F10"), "Steve"));
		HerobrineState state = new HerobrineState();
		state.setFragmentPlaced("F10", GlobalPos.of(level.dimension(), chest));
		helper.assertTrue(LiveBooks.finishF10(server, state) >= 1 && state.hasFlag(LiveBooks.F10_FINISHED), "F10 was not finished");
		List<String> f10 = lines(pages(((Container) level.getBlockEntity(chest)).getItem(0)));
		helper.assertTrue(f10.getLast().equals("* removed Steve"), "F10 reads " + f10);

		helper.assertTrue(LoreApi.placeF20(server, state, Services.traces().forced()), "F20 was not placed");
		assertHolds(helper, chest.below(), "F20");
		helper.assertTrue(LoreApi.placeF20(server, state, Services.traces().forced()), "placing F20 again failed");
		helper.succeed();
	}

	@GameTest
	public void endingAStopSignStandsInFrontOfTheCross(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		// The cross: a 3-high post at (4, 1..3, 4) with arms east and west.
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(4, y, 4, Blocks.DIRT);
		}
		helper.setBlock(3, 2, 4, Blocks.DIRT);
		helper.setBlock(5, 2, 4, Blocks.DIRT);
		helper.setBlock(1, 1, 1, Placers.standingSign(Direction.NORTH));
		BlockPos stop = helper.absolutePos(new BlockPos(1, 1, 1));
		((SignBlockEntity) level.getBlockEntity(stop)).setText(signText(fragment("F03").lines()), SignTextSlot.FRONT);
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		data.setStopSign(GlobalPos.of(level.dimension(), stop));
		TestEditor editor = new TestEditor();
		helper.assertTrue(LoreApi.moveStopSignToCross(server, state, data, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4, 1, 4))),
				Services.traces().forced(), editor), "the Stop. sign was not moved");
		BlockPos front = helper.absolutePos(new BlockPos(4, 1, 3));
		helper.assertTrue(data.stopSign().map(GlobalPos::pos).equals(Optional.of(front)), "it stands at " + data.stopSign());
		helper.assertBlockPresent(Blocks.OAK_SIGN, new BlockPos(4, 1, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
		helper.assertTrue(frontOf(helper, front).equals(fragment("F03").lines()), "it reads " + frontOf(helper, front));
		helper.assertTrue(editor.edits.isEmpty(), "an unchanged Stop. sign was written again");
		helper.assertTrue(state.fragmentsPlaced().get("F03").pos().equals(front), "F03 was not moved with it");
		helper.succeed();
	}
}
