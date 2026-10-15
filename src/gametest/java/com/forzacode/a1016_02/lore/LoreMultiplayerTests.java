package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.lore.LoreRoster.Known;
import com.forzacode.a1016_02.lore.LoreRoster.TestPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Lore with friends (MP-2, D-058 to D-063): one story, anyone's reading and telling counts, once-per-world things
 * happen once, the list names every known player with their own cause, and the story's first blocks belong to one
 * stored player. Each test brings its own {@link LoreRoster} (online list, state, lore and telling data); the two
 * that check Stage 3 and spectators go through the live path on purpose, with mock players nobody else sees.
 */
public class LoreMultiplayerTests extends LoreTellingTests {
	/** Every FRAGMENT_READ fired during the run: "uuid|id". */
	static final List<String> READS = new CopyOnWriteArrayList<>();
	/** Every STAGE_CHANGED fired during the run: "old>new". */
	static final List<String> STAGES = new CopyOnWriteArrayList<>();

	static {
		HerobrineEvents.FRAGMENT_READ.register((player, id) -> READS.add(player.getUUID() + "|" + id));
		HerobrineEvents.STAGE_CHANGED.register((server, oldStage, newStage) -> STAGES.add(oldStage + ">" + newStage));
	}

	static GlobalPos at(GameTestHelper helper, int x, int y, int z) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, y, z)));
	}

	static PlacedBlock table(GameTestHelper helper, int x, int y, int z) {
		return new PlacedBlock(at(helper, x, y, z), Blocks.CRAFTING_TABLE.defaultBlockState());
	}

	static Placing.Request request(GameTestHelper helper, String id, BlockPos absOrigin, int max, Placing.Facts facts) {
		ServerLevel level = helper.getLevel();
		return new Placing.Request(level, fragment(id), absOrigin, 0, max, 1, Services.traces().forced(), NAME, facts, level.getRandom(), true,
				new Placing.Loads());
	}

	// --- reading ---

	/** B finishes what A's reading started; two players reading one sign in one check read it once. */
	@GameTest
	public void anyonesReadCountsAndOnlyOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Known ann = new Known("Ann", 900);
		Known ben = new Known("Ben", 100);
		LoreRoster r = new LoreRoster(LoreRoster.state(helper, ann, ben));
		TestPlayer a = r.join(r.player(helper, ann, new Vec3(5.5, 0.0, 1.5)));
		TestPlayer b = r.join(r.player(helper, ben, new Vec3(3.5, 0.0, 1.5)));
		b.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, 1.5)), 0.0F, 5.0F);
		a.snapTo(helper.absoluteVec(new Vec3(5.5, 0.0, 1.5)), 180.0F, 0.0F);
		helper.setBlock(3, 1, 4, Placers.standingSign(Direction.NORTH));
		helper.setBlock(5, 1, 4, Placers.standingSign(Direction.NORTH));
		r.lore.addReadTarget("F08", at(helper, 3, 1, 4));
		r.lore.addReadTarget("F18", at(helper, 5, 1, 4));
		// Ann's progress: seven read. F21 needs eight, F28 needs F23.
		for (String id : List.of("F01", "F02", "F05", "F06", "F07", "F09", "F14")) {
			FragmentServiceImpl.markRead(r.state, a, id);
		}
		FragmentEngine.LiveFacts facts = r.facts(server);
		helper.assertFalse(FragmentEngine.eligible(fragment("F21"), Stage.PROXIMITY, facts), "F21 with seven read");

		ReadWatcher watcher = new ReadWatcher();
		List<String> results = new ArrayList<>();
		LoreConfig config = new LoreConfig();
		for (int check = 0; check < 4; check++) {
			watcher.look(r.watch.participants(server), r.state, r.lore, false, config,
					(player, id) -> results.add(player.getName().getString() + ":" + id + ":" + FragmentServiceImpl.markRead(r.state, player, id)));
		}
		helper.assertTrue(results.equals(List.of("Ben:F08:true")), "Ben's look " + results);
		helper.assertTrue(READS.contains(ben.uuid() + "|F08") && !READS.contains(ann.uuid() + "|F08"), "FRAGMENT_READ is not Ben's");
		helper.assertTrue(r.state.fragmentsRead().size() == 8 && FragmentEngine.eligible(fragment("F21"), Stage.PROXIMITY, facts),
				"Ben's read did not count toward the eight F21 needs");
		helper.assertTrue(FragmentServiceImpl.markRead(r.state, b, "F23") && FragmentEngine.eligible(fragment("F28"), Stage.PROXIMITY, facts),
				"Ben reading F23 did not open F28");
		helper.assertFalse(FragmentServiceImpl.markRead(r.state, a, "F23"), "Ann read F23 again after Ben");

		// Both look at F18 in the same checks: both finish in one check; it is read once, Ann's (first in join order).
		a.snapTo(helper.absoluteVec(new Vec3(5.5, 0.0, 1.5)), 0.0F, 5.0F);
		b.snapTo(helper.absoluteVec(new Vec3(5.5, 0.0, 1.8)), 0.0F, 5.0F);
		results.clear();
		for (int check = 0; check < 4; check++) {
			watcher.look(r.watch.participants(server), r.state, r.lore, false, config,
					(player, id) -> results.add(player.getName().getString() + ":" + id + ":" + FragmentServiceImpl.markRead(r.state, player, id)));
		}
		helper.assertTrue(results.equals(List.of("Ann:F18:true", "Ben:F18:false")), "both reading F18 " + results);
		helper.assertTrue(READS.stream().filter(e -> e.endsWith("|F18") && (e.startsWith(ann.uuid().toString()) || e.startsWith(ben.uuid().toString())))
				.count() == 1, "FRAGMENT_READ fired twice for F18");
		helper.assertTrue(r.state.fragmentsRead().size() == 10, "read " + r.state.fragmentsRead());

		// A spectator is not a participant: their looking reads nothing.
		Known cat = new Known("Cat", 5);
		LoreRoster watching = new LoreRoster(LoreRoster.state(helper, cat));
		TestPlayer c = watching.join(watching.player(helper, cat, new Vec3(3.5, 0.0, 1.5)));
		c.mode = GameType.SPECTATOR;
		c.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, 1.5)), 0.0F, 5.0F);
		watching.lore.addReadTarget("F08", at(helper, 3, 1, 4));
		helper.assertTrue(watching.watch.participants(server).isEmpty(), "a spectator takes part");
		helper.succeed();
	}

	// --- telling ---

	/** The live path: B names him and Stage 3 starts once; A naming him in the same tick does not start it again. */
	@GameTest
	public void anyoneNamingHimStartsTellingOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		state.setStage(server, Stage.PROXIMITY);
		ServerPlayer b = mock(helper);
		ServerPlayer a = mock(helper);
		int before = STAGES.size();
		List<String> lines = new ArrayList<>();
		for (ServerPlayer writer : List.of(b, a)) {
			BlockPos rel = new BlockPos(writer == b ? 2 : 4, 1, 2);
			helper.setBlock(rel, Placers.standingSign(Direction.NORTH));
			BlockPos pos = helper.absolutePos(rel);
			SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(pos);
			String line = "herobrine " + writer.getUUID().toString().substring(0, 8);
			lines.add(line);
			sign.setAllowedPlayerEditor(writer.getUUID());
			sign.updateSignText(writer, SignTextSlot.FRONT, List.of(FilteredText.passThrough(line), FilteredText.EMPTY, FilteredText.EMPTY,
					FilteredText.EMPTY));
		}
		List<String> changes = List.copyOf(STAGES.subList(before, STAGES.size()));
		helper.assertTrue(state.stage() == Stage.TELLING && state.tellingStarted(), "B naming him did not start Stage 3: " + state.stage());
		helper.assertTrue(changes.equals(List.of("PROXIMITY>TELLING")), "Stage 3 started " + changes);
		helper.assertTrue(TOLD.contains(lines.get(0) + "|true") && TOLD.contains(lines.get(1) + "|true"), "both tellings count");

		// A spectator (a hardcore friend whose story ended) does not tell, in chat or anywhere.
		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		int count = state.tellingCount();
		String said = "herobrine said " + spectator.getUUID();
		Telling.onChat(spectator, said);
		helper.assertTrue(state.tellingCount() == count && !TOLD.contains(said + "|true"), "a spectator's chat told");
		helper.succeed();
	}

	/** Two players write about him in the same tick: one "Stop.", on the first sign, whoever the card fires for. */
	@GameTest
	public void twoWritersInOneTickGetOneStop(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		HerobrineState state = LoreRoster.state(helper, new Known(a.getUUID(), "Ann", 10, HerobrineState.FirstBlocks.NONE),
				new Known(b.getUUID(), "Ben", 10, HerobrineState.FirstBlocks.NONE));
		state.setStage(server, Stage.TELLING);
		TellingData data = new TellingData();
		sign(helper, new BlockPos(2, 1, 2), a, "herobrine is here", state, data, NO_TRACES);
		sign(helper, new BlockPos(5, 1, 2), b, "herobrine took it", state, data, NO_TRACES);
		BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos second = helper.absolutePos(new BlockPos(5, 1, 2));
		helper.assertTrue(data.stopCandidate().map(GlobalPos::pos).equals(Optional.of(first)), "the first sign is not the Stop. sign");
		helper.assertTrue(state.tellingCount() == 2 && data.firstTellingSeq() == 0, "the shared count " + state.tellingCount());
		helper.assertTrue(TellingCards.writerName(state, data, data.stopCandidate()).equals(Optional.of("Ann")), "Stop. is not aimed at its writer");

		TestEditor editor = new TestEditor();
		// The director fires the card for Ben, then (the same tick) for Ann.
		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, "Ben") == FireResult.FIRED, "Stop. did not fire");
		helper.assertTrue(TellingCards.fireStop(server, state, data, editor, "Ann") == FireResult.SKIPPED, "Stop. fired twice");
		helper.assertTrue(editor.edits.size() == 1 && editor.edits.getFirst().pos().equals(first), "edits " + editor.edits);
		helper.assertTrue(frontOf(helper, second).getFirst().equals("herobrine took it"), "the second sign changed: " + frontOf(helper, second));
		helper.assertTrue(state.stopFired() && data.stopSign().map(GlobalPos::pos).equals(Optional.of(first)), "the Stop. sign is not recorded");
		helper.succeed();
	}

	/** A's sign rewritten by B, A's book signed by B: still A's too; one who never wrote breaks nothing of theirs. */
	@GameTest
	public void writingsStayTheirWritersWhoeverHoldsThem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		ServerPlayer c = mock(helper);
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		sign(helper, new BlockPos(2, 1, 2), a, "herobrine", state, data, NO_TRACES);
		sign(helper, new BlockPos(2, 1, 2), b, "herobrine again", state, data, NO_TRACES);
		GlobalPos at = at(helper, 2, 1, 2);
		TellingData.WrittenSign record = data.sign(at).orElseThrow();
		helper.assertTrue(record.writer().equals(a.getUUID()) && record.writers().equals(List.of(a.getUUID(), b.getUUID())),
				"the sign's writers " + record.writers());
		helper.assertTrue(TellingCards.blankPool(a, data).stream().anyMatch(s -> s.pos().equals(at))
				&& TellingCards.blankPool(b, data).stream().anyMatch(s -> s.pos().equals(at)), "the sign is not both writers' for blank sign");
		helper.assertFalse(Telling.signBroken(c, at, data), "someone who never wrote on it destroyed their own writing");
		sign(helper, new BlockPos(2, 1, 2), a, "herobrine", state, data, NO_TRACES);
		sign(helper, new BlockPos(2, 1, 2), b, "herobrine still", state, data, NO_TRACES);
		helper.assertTrue(Telling.signBroken(a, at, data), "A breaking the sign B wrote on too did not count");

		// The book: A writes it, hands it to B, B signs it. The marker says whose it is (D-047).
		ItemStack book = writable("i saw herobrine");
		Telling.writeBook(a, book, false, state, data, NO_TRACES);
		ItemStack signed = book.transmuteCopy(Items.WRITTEN_BOOK);
		signed.remove(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT);
		signed.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
				net.minecraft.server.network.Filterable.passThrough("him"), "Ben", 0,
				List.of(net.minecraft.server.network.Filterable.passThrough(net.minecraft.network.chat.Component.literal("i saw herobrine. so did i."))), true));
		Telling.writeBook(b, signed, true, state, data, NO_TRACES);
		String id = Telling.bookId(signed).orElseThrow();
		helper.assertTrue(Telling.bookWriters(signed).equals(List.of(a.getUUID(), b.getUUID())), "the marker says " + Telling.bookWriters(signed));
		helper.assertTrue(data.book(id).map(w -> w.writer().equals(a.getUUID()) && w.writtenBy(b.getUUID())).orElse(false), "the record " + data.book(id));
		helper.assertTrue(Telling.bookDestroyed(a, id, Telling.bookWriters(signed), data), "A burning the book B signed did not count");
		ItemStack other = writable("herobrine, from ben");
		Telling.writeBook(b, other, false, state, data, NO_TRACES);
		helper.assertFalse(Telling.bookDestroyed(c, Telling.bookId(other).orElseThrow(), Telling.bookWriters(other), data),
				"burning someone else's book counted");
		helper.succeed();
	}

	// --- the list ---

	/** F23 has a line for every known player, in join order, each with their own cause; F06, the same list, the bare names (D-067). */
	@GameTest
	public void theListNamesEveryPlayerWithTheirOwnCause(GameTestHelper helper) {
		Known ann = new Known("Ann", 10);
		Known ben = new Known("Ben", 10);
		Known cat = new Known("Cat", 10);
		HerobrineState state = LoreRoster.state(helper, ann, ben, cat);
		TellingData data = new TellingData();
		ServerPlayer annPlayer = new TestPlayer(helper.getLevel(), ann.uuid(), "Ann");
		ServerPlayer stranger = mock(helper);
		LiveBooks.onMarkedDeath(annPlayer, "lava", data);
		LiveBooks.onMarkedDeath(stranger, "fell", data);
		LiveBooks.onMarkedDeath(annPlayer, "drowned", data);
		// Ben's death is only in the shared record (no event reached lore): his own line still takes it.
		state.addMarkedDeath(ben.uuid(), new MarkedDeath("fell", at(helper, 1, 1, 1), 3));
		List<String> lines = LiveBooks.lines(state, data).stream().map(LiveBooks.Line::text).toList();
		helper.assertTrue(lines.equals(List.of("Ann . drowned", "Ben . fell", "Cat .")), "lines " + lines);

		ItemStack f23 = FragmentItems.book(fragment("F23"), NAME);
		helper.assertTrue(LiveBooks.refreshForReading(f23, "Cat", state, data), "F23 did not update when Cat opened it");
		List<String> expected = new ArrayList<>(DESIGN_TEXT.get("F23").subList(0, 6));
		expected.addAll(lines);
		helper.assertTrue(lines(pages(f23)).equals(expected), "F23 reads " + pages(f23));
		helper.assertFalse(String.join("\n", pages(f23)).contains(stranger.getName().getString()) || String.join("", pages(f23)).contains(Fragment.PLAYER_NAME),
				"F23 names someone who never joined");
		ItemStack f06 = FragmentItems.book(fragment("F06"), NAME);
		LiveBooks.refreshForReading(f06, "Ben", state, data);
		List<String> f06Expected = new ArrayList<>(DESIGN_TEXT.get("F06").subList(0, 6));
		f06Expected.addAll(List.of("Ann .", "Ben .", "Cat ."));
		helper.assertTrue(lines(pages(f06)).equals(f06Expected), "F06 reads " + pages(f06));
		helper.assertFalse(LiveBooks.refreshForReading(f23, "Ann", state, data), "the list changed with its reader");

		// Placed now, the book already reads the list as it stands.
		for (int y = 0; y <= 4; y++) {
			helper.setBlock(3, y, 3, Blocks.DIRT);
		}
		seed(helper, SiteType.PANIC_TOWER, new BlockPos(3, 0, 3), 5);
		LoreRoster r = new LoreRoster(state);
		Placing.Result placed = Placers.place(request(helper, "F23", helper.absolutePos(new BlockPos(3, 0, 3)), 3, new FragmentEngine.LiveFacts(
				r.homes(helper.getLevel().getServer()), data))).orElseThrow(() -> helper.assertionException(net.minecraft.network.chat.Component.literal("F23 not placed")));
		Container chest = (Container) helper.getLevel().getBlockEntity(placed.pos);
		ItemStack inChest = ItemStack.EMPTY;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (FragmentItems.is(chest.getItem(slot), "F23")) {
				inChest = chest.getItem(slot);
			}
		}
		helper.assertTrue(lines(pages(inChest)).equals(expected), "the placed F23 reads " + pages(inChest));

		// A big group: the list goes on to a second page, every line kept, no page past its 14 lines.
		List<Known> many = new ArrayList<>();
		for (int n = 0; n < 12; n++) {
			many.add(new Known("player_" + n, 1));
		}
		HerobrineState crowd = LoreRoster.state(helper, many.toArray(Known[]::new));
		ItemStack big = FragmentItems.book(fragment("F23"), NAME);
		LiveBooks.refreshForReading(big, "player_0", crowd, new TellingData());
		helper.assertTrue(pages(big).size() == 2 && pages(big).stream().allMatch(p -> BookLayout.pageLines(p) <= BookLayout.PAGE_LINES)
				&& lines(pages(big)).size() == 18 && lines(pages(big)).getLast().equals("player_11 ."), "the long list " + pages(big));
		helper.succeed();
	}

	// --- the story's owner (D-061) ---

	/** F13 takes the stored owner's first table and F10 goes where it stood, after the owner left and a friend played longer. */
	@GameTest
	public void theCairnAndTheChangesKeepTheirOwner(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		helper.setBlock(1, 1, 1, Blocks.CRAFTING_TABLE);
		helper.setBlock(1, 1, 6, Blocks.CRAFTING_TABLE);
		BlockPos core = new BlockPos(5, 1, 4);
		for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
			if (!pos.equals(core)) {
				helper.setBlock(pos, Blocks.SANDSTONE);
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, core, 2);
		PlacedBlock annTable = table(helper, 1, 1, 1);
		PlacedBlock benTable = table(helper, 1, 1, 6);
		Known ann = new Known("Ann", 100).withFirsts(new HerobrineState.FirstBlocks(annTable, annTable, null));
		Known ben = new Known("Ben", 90_000).withFirsts(new HerobrineState.FirstBlocks(benTable, benTable, null));
		LoreRoster r = new LoreRoster(LoreRoster.state(helper, ann, ben));
		// F07 already has the largest pyramid (otherwise this one would be kept for the seed).
		r.state.setFragmentPlaced("F07", GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));
		TestPlayer a = r.join(r.player(helper, ann, new Vec3(1.5, 1.0, 3.5)));

		// Only Ann plays: asking for the story's first blocks chooses her, for good.
		helper.assertTrue(r.facts(server).firstBlocks().craftingTable().equals(annTable), "the owner's first blocks are not Ann's");
		helper.assertTrue(r.state.owner(Homes.FIRST_BLOCKS).equals(Optional.of(ann.uuid())), "the owner was not stored under " + Homes.FIRST_BLOCKS);

		// Ann leaves; Ben, who has played far longer, comes on. Nothing flips.
		r.leave(a);
		r.join(r.player(helper, ben, new Vec3(6.5, 1.0, 6.5)));
		FragmentEngine.LiveFacts facts = r.facts(server);
		helper.assertTrue(r.homes(server).owner().equals(Optional.of(ann.uuid())) && facts.firstBlocks().craftingTable().equals(annTable),
				"the owner flipped to Ben");
		helper.assertTrue(facts.base().equals(annTable.pos().pos()), "the story's home is not Ann's while she is away: " + facts.base());
		Placing.Result cairn = Placers.place(request(helper, "F13", helper.absolutePos(core), 3, facts))
				.orElseThrow(() -> helper.assertionException(net.minecraft.network.chat.Component.literal("F13 not placed")));
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, core);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, new BlockPos(1, 1, 6));
		helper.assertTrue(cairn.anchors.get("F13/from").equals(annTable.pos().pos()), "the cairn's first block is not Ann's");

		Placing.Result changes = Placers.place(request(helper, "F10", helper.absolutePos(new BlockPos(1, 1, 1)), 3, facts))
				.orElseThrow(() -> helper.assertionException(net.minecraft.network.chat.Component.literal("F10 not placed")));
		helper.assertTrue(changes.pos.equals(annTable.pos().pos()), "F10 is not where Ann's table stood: " + changes.pos.toShortString());
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, new BlockPos(1, 1, 6));
		helper.succeed();
	}

	/** Homes: a friend's base is remembered while they are away; F04 and the list pyramid never use "the" player's. */
	@GameTest
	public void basesAreEachPlayersOwn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		PlacedBlock annBlock = new PlacedBlock(at(helper, 1, 1, 1), Blocks.OAK_PLANKS.defaultBlockState());
		PlacedBlock benBlock = new PlacedBlock(at(helper, 6, 1, 6), Blocks.OAK_PLANKS.defaultBlockState());
		Known ann = new Known("Ann", 10).withFirsts(new HerobrineState.FirstBlocks(annBlock, null, null));
		Known ben = new Known("Ben", 10).withFirsts(new HerobrineState.FirstBlocks(benBlock, null, null));
		Known never = new Known("Dee", 0);
		LoreRoster r = new LoreRoster(LoreRoster.state(helper, ann, ben, never));
		TestPlayer a = r.join(r.player(helper, ann, new Vec3(1.5, 1.0, 1.5)));
		TestPlayer b = r.join(r.player(helper, ben, new Vec3(6.5, 1.0, 6.5)));
		Homes homes = r.homes(server);
		homes.rememberAll();
		r.leave(b);
		helper.assertTrue(r.lore.base(ben.uuid()).equals(Optional.of(benBlock.pos())) && homes.base(ben.uuid()).equals(Optional.of(benBlock.pos())),
				"Ben's base is gone while he is away");
		helper.assertTrue(homes.all().equals(List.of(annBlock.pos(), benBlock.pos())), "every known base " + homes.all());

		// The triggers: one occasion however many carry the list or share a home.
		helper.assertFalse(LoreTriggers.anyCarries(List.of(a, b), "F06"), "carrying before anyone has it");
		b.getInventory().add(FragmentItems.book(fragment("F06"), NAME));
		helper.assertTrue(LoreTriggers.anyCarries(List.of(a, b), "F06"), "Ben carrying the list is not noticed");
		helper.assertTrue(LoreTriggers.bases(r.watch, List.of(a, a)).size() == 1, "one home counted twice");

		// The list pyramid: a list burnt in the Nether searches from the burner's base, never from "the subject's".
		GlobalPos nether = GlobalPos.of(Level.NETHER, new BlockPos(10, 64, 10));
		TellingData.Burned old = new TellingData.Burned(1, 1, 0, Optional.of(nether), Optional.empty(), Optional.empty());
		helper.assertTrue(ListPyramid.searchFrom(old, () -> Optional.of(annBlock.pos().pos()), BlockPos.ZERO).equals(annBlock.pos().pos()),
				"an old Nether burn does not search from the story's home");
		TellingData.Burned fresh = new TellingData.Burned(1, 1, 0, Optional.of(nether), Optional.empty(), Optional.of(benBlock.pos()));
		helper.assertTrue(ListPyramid.searchFrom(fresh, () -> Optional.of(annBlock.pos().pos()), BlockPos.ZERO).equals(benBlock.pos().pos()),
				"a Nether burn does not search from the burner's base");
		helper.succeed();
	}

	// --- old saves ---

	/** A v1.0 world: one subject in state.dat, one cause in lore_telling.dat, single writers; it loads as that player's. */
	@GameTest
	public void anOldSingleSubjectSaveLoads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RegistryOps<Tag> ops = LoreRoster.ops(helper);
		UUID alexId = UUID.fromString("00000000-0000-0000-0000-0000000a1016");
		PlacedBlock alexTable = table(helper, 2, 1, 2);
		Known alex = new Known(alexId, "Alex", 50_000, new HerobrineState.FirstBlocks(alexTable, alexTable, null));
		HerobrineState state = LoreRoster.v1State(helper, alex);

		CompoundTag old = new CompoundTag();
		old.putString("listCause", "lava");
		CompoundTag sign = new CompoundTag();
		sign.put("pos", GlobalPos.CODEC.encodeStart(ops, at(helper, 3, 1, 3)).getOrThrow());
		sign.store("writer", UUIDUtil.CODEC, ops, alexId);
		sign.putString("text", "herobrine");
		sign.putBoolean("aboutHim", true);
		sign.putBoolean("namesHim", true);
		sign.putLong("seq", 0L);
		ListTag signs = new ListTag();
		signs.add(sign);
		old.put("signs", signs);
		CompoundTag book = new CompoundTag();
		book.putString("id", "b1");
		book.store("writer", UUIDUtil.CODEC, ops, alexId);
		book.putBoolean("namesHim", true);
		book.putLong("seq", 1L);
		ListTag books = new ListTag();
		books.add(book);
		old.put("books", books);
		CompoundTag burned = new CompoundTag();
		burned.putInt("lists", 1);
		burned.putInt("owed", 1);
		burned.put("near", GlobalPos.CODEC.encodeStart(ops, GlobalPos.of(Level.NETHER, BlockPos.ZERO)).getOrThrow());
		old.put("burned", burned);
		TellingData data = TellingData.CODEC.parse(ops, old).getOrThrow();
		LoreData lore = LoreData.CODEC.parse(ops, new CompoundTag()).getOrThrow();

		helper.assertTrue(data.legacyCause().equals(Optional.of("lava")) && data.migrate(state), "the old cause did not move");
		helper.assertTrue(data.listCause(alexId).equals(Optional.of("lava")) && data.lastMarked().equals(Optional.of(alexId))
				&& data.legacyCause().isEmpty(), "the old cause is not Alex's");
		helper.assertTrue(LiveBooks.lines(state, data).stream().map(LiveBooks.Line::text).toList().equals(List.of("Alex . lava")), "Alex's line");
		TellingData.WrittenSign oldSign = data.signs().getFirst();
		helper.assertTrue(oldSign.writer().equals(alexId) && oldSign.coWriters().isEmpty() && oldSign.writtenBy(alexId), "the old sign " + oldSign);
		helper.assertTrue(data.book("b1").map(w -> w.writtenBy(alexId)).orElse(false), "the old book is not Alex's");
		helper.assertTrue(data.burned().from().isEmpty() && data.burned().pending() == 1, "the old burn " + data.burned());
		helper.assertTrue(lore.base(alexId).isEmpty(), "an old lore.dat has bases");

		// Saved again: the new format, and it loads the same.
		CompoundTag saved = (CompoundTag) TellingData.CODEC.encodeStart(ops, data).getOrThrow();
		helper.assertTrue(!saved.contains("listCause") && saved.contains("listCauses"), "saved keys " + saved.keySet());
		TellingData again = TellingData.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(again.listCause(alexId).equals(Optional.of("lava")) && again.signs().getFirst().writtenBy(alexId), "the new format did not round-trip");

		// The old subject is the story's owner when they come back alone, with their old first table.
		LoreRoster r = new LoreRoster(state);
		r.join(r.player(helper, alex, new Vec3(2.5, 1.0, 2.5)));
		helper.assertTrue(r.facts(server).firstBlocks().craftingTable().equals(alexTable), "the old first table is not the story's");

		// An old cause in a world nobody is known in yet waits for its player.
		CompoundTag nobody = new CompoundTag();
		nobody.putString("listCause", "fell");
		TellingData waiting = TellingData.CODEC.parse(ops, nobody).getOrThrow();
		helper.assertFalse(waiting.migrate(new HerobrineState()) || waiting.legacyCause().isEmpty(), "the old cause went to nobody");
		helper.succeed();
	}

	/**
	 * MP-7, D-062.8: a v1.0 world that finished Ending B. Its F10 ended "* removed Alex" (v1.0's one subject, named from
	 * the flag alone, with no list of names); Sam joined since. Opening F10 keeps Alex's line, and never names Sam.
	 */
	@GameTest
	public void anOldSaveThatFinishedEndingBKeepsF10sLastLine(GameTestHelper helper) {
		RegistryOps<Tag> ops = LoreRoster.ops(helper);
		Known alex = new Known(UUID.fromString("00000000-0000-0000-0000-0000000b1016"), "Alex", 50_000, HerobrineState.FirstBlocks.NONE);
		HerobrineState state = LoreRoster.v1State(helper, alex);
		state.setFlag(LiveBooks.F10_FINISHED, true);
		UUID sam = UUID.randomUUID();
		state.addKnownPlayer(sam, "Sam");
		TellingData data = TellingData.CODEC.parse(ops, new CompoundTag()).getOrThrow();
		ItemStack f10 = FragmentItems.book(fragment("F10"), "Alex");
		LiveBooks.refresh(f10, "Alex", Optional.empty(), true);
		helper.assertTrue(lines(pages(f10)).getLast().equals("* removed Alex"), "setup: v1.0's F10 reads " + pages(f10));

		// Loaded (TellingData.get migrates the old record), then opened by Sam.
		data.migrate(state);
		LiveBooks.refreshForReading(f10, "Sam", state, data);
		helper.assertTrue(lines(pages(f10)).getLast().equals("* removed Alex"), "opening F10 lost its last line: " + lines(pages(f10)));
		helper.assertTrue(data.f10Removed().equals(List.of(alex.uuid())), "F10 does not name v1.0's subject: " + data.f10Removed());

		// A v1.0 world that never finished B names nobody.
		HerobrineState notB = LoreRoster.v1State(helper, alex);
		TellingData none = TellingData.CODEC.parse(ops, new CompoundTag()).getOrThrow();
		none.migrate(notB);
		helper.assertTrue(none.f10Removed().isEmpty(), "F10 names someone in a world that never finished B");
		helper.succeed();
	}
}
