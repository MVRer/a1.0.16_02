package com.forzacode.a1016_02.debug;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.forzacode.a1016_02.core.CommandHooks;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * SC-5, the self-created layer's debug (docs/SELF_CREATED.md, "debug"): {@code /a1016 sc} is the index and every
 * number's own command under the same literal (Brigadier merged them); the index has the switch and one line per
 * number, 01 to 31 (the second wave's 16 to 31 since SC2-9, docs/SELF_CREATED_2.md), plain and in opaque words, held to
 * the first wave's words and to the second wave's (D-103); the overlay shows the layer's rows only after
 * {@code /a1016 debug overlay sc}.
 */
public class SelfCreatedDebugTests {
	/** Words that would name what a number is (D-080): none may be in the index or the overlay's layer rows. */
	static final List<String> NAMES = List.of("moon", "night", "name", "bed", "sleep", "rest", "torch", "step", "foot", "print", "track", "fog", "icon",
			"picture", "view", "seed", "version", "house", "home", "door", "chest", "pet", "animal", "friend", "cave", "lava", "shaft", "stat", "mined",
			"dropped", "turn", "trail", "brb", "record", "hole", "block", "wall", "way back", "stood", "stand");
	/** The second wave's words (D-103, {@code VerifyScSpoilerTests.SC2} and {@code SC2_GENERIC}): none may be in them either. */
	static final List<String> NAMES_SC2 = java.util.stream.Stream.concat(com.forzacode.a1016_02.core.VerifyScSpoilerTests.SC2.stream(),
			com.forzacode.a1016_02.core.VerifyScSpoilerTests.SC2_GENERIC.stream()).toList();
	/**
	 * Round 3's generic words that a line of the first wave already uses here, watched by hand (as {@code SC2_DROPPED}):
	 * {@code day} (04's "last day 0").
	 */
	static final List<String> SC3_WATCHED = List.of("day");
	/**
	 * Round 3's words (D-103 extended, R3-P: {@code VerifyScSpoilerTests.SC3} and {@code SC3_GENERIC}), held as whole words
	 * ({@link #wordIn}): "fault" inside "default" or "hanging" inside "changing" is no word of theirs. {@link #SC3_WATCHED}
	 * are left out.
	 */
	static final List<String> NAMES_SC3 = java.util.stream.Stream.concat(com.forzacode.a1016_02.core.VerifyScSpoilerTests.SC3.stream(),
			com.forzacode.a1016_02.core.VerifyScSpoilerTests.SC3_GENERIC.stream()).filter(word -> !SC3_WATCHED.contains(word)).toList();
	/** The last number of the layer. */
	static final int LAST = 31;

	@GameTest
	public void theScLiteralHoldsTheIndexAndEveryNumber(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> sc = server.getCommands().getDispatcher().getRoot().getChild(CommandHooks.ROOT).getChild("sc");
		helper.assertTrue(sc != null && sc.getCommand() != null, "/a1016 sc has no index");
		helper.assertTrue(sc.getChild("on") != null && sc.getChild("off") != null, "/a1016 sc has no switch");
		for (int n = 1; n <= LAST; n++) {
			String number = String.format(Locale.ROOT, "%02d", n);
			helper.assertTrue(sc.getChild(number) != null, "/a1016 sc " + number + " is not registered under the same literal");
		}
		int result;
		try {
			result = server.getCommands().getDispatcher().execute(CommandHooks.ROOT + " sc", server.createCommandSourceStack().withSuppressedOutput());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException("/a1016 sc did not parse", e);
		}
		helper.assertTrue(result == 1, "/a1016 sc answered " + result);
		helper.succeed();
	}

	@GameTest
	public void theIndexHasTheSwitchAndOneOpaqueLinePerNumber(GameTestHelper helper) {
		List<String> lines = ScIndex.lines(helper.getLevel().getServer());
		helper.assertTrue(lines.size() == LAST + 1 && lines.getFirst().startsWith("[a1016] sc: "), "the index: " + lines);
		for (int n = 1; n <= LAST; n++) {
			String line = lines.get(n);
			helper.assertTrue(line.startsWith(String.format(Locale.ROOT, "%02d: ", n)), "line " + n + ": " + line);
			helper.assertFalse(line.contains("could not be read"), "line " + n + ": " + line);
		}
		check(helper, lines);
		helper.succeed();
	}

	@GameTest
	public void theOverlayShowsTheLayersRowsOnlyAfterSc(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		UUID viewer = new UUID(0x5C5L, 1L);
		boolean was = DebugOverlay.enabled();
		boolean wasSc = DebugOverlay.scRows();
		try {
			DebugOverlay.setEnabled(server, true, false);
			List<String> plain = DebugOverlay.build(server, viewer).rows();
			helper.assertTrue(plain.stream().noneMatch(SelfCreatedDebugTests::layerRow), "layer rows without sc: " + plain);
			DebugOverlay.setEnabled(server, true, true);
			List<String> rows = DebugOverlay.build(server, viewer).rows();
			List<String> layer = rows.stream().filter(SelfCreatedDebugTests::layerRow).toList();
			helper.assertTrue(layer.size() >= 2 && layer.get(0).startsWith("sc 04" + OverlayPayload.SEPARATOR) && layer.get(1).startsWith("sc 10" + OverlayPayload.SEPARATOR),
					"the layer's rows: " + rows);
			helper.assertTrue(rows.size() <= OverlayPayload.MAX_ROWS && rows.stream().noneMatch(row -> row.startsWith("error")), "the rows: " + rows);
			check(helper, layer.stream().map(row -> row.substring(row.indexOf(OverlayPayload.SEPARATOR) + 1)).toList());
			DebugOverlay.setEnabled(server, false);
			helper.assertFalse(DebugOverlay.scRows(), "the layer's rows outlived the overlay");
		} finally {
			DebugOverlay.setEnabled(server, was, wasSc);
		}
		helper.succeed();
	}

	/**
	 * SC2-9: a line of 16 to 20 over places of the test's own (two of 16, one of 18, one of 17 that is not counted for
	 * 16): known, visited, who is inside now, and each answer's count today and ever by its number; plain and opaque.
	 */
	@GameTest
	public void theRemainsLinesCountKnownVisitedInsideAndAnswers(GameTestHelper helper) {
		com.forzacode.a1016_02.world.sc2.kit.Sc2Data data = new com.forzacode.a1016_02.world.sc2.kit.Sc2Data();
		java.util.function.IntFunction<com.forzacode.a1016_02.core.Remains.Place> place = n -> new com.forzacode.a1016_02.core.Remains.Place(
				n == 2 ? com.forzacode.a1016_02.core.Remains.Kind.HOLLOW : n == 3 ? com.forzacode.a1016_02.core.Remains.Kind.ARCHIVE
						: com.forzacode.a1016_02.core.Remains.Kind.PYRAMID,
				net.minecraft.world.level.Level.OVERWORLD, new net.minecraft.world.level.levelgen.structure.BoundingBox(n * 1000, 60, 0, n * 1000 + 40, 100, 40),
				new net.minecraft.core.BlockPos(n * 1000 + 20, 61, 20), new net.minecraft.core.BlockPos(n * 1000, 61, 20), net.minecraft.core.Direction.WEST,
				java.util.Optional.empty(), n, 0);
		String[] kinds = {"s16", "s16", "s18", "s17"};
		List<com.forzacode.a1016_02.core.Remains.Place> places = new java.util.ArrayList<>();
		for (int n = 0; n < 4; n++) {
			com.forzacode.a1016_02.core.Remains.Place p = place.apply(n);
			places.add(p);
			data.add(com.forzacode.a1016_02.world.sc2.kit.Sc2Data.Known.of(p, kinds[n], new com.forzacode.a1016_02.world.sc2.kit.Start(p.anchor(),
					net.minecraft.core.Direction.WEST, 0, n), new net.minecraft.nbt.CompoundTag()));
		}
		net.minecraft.server.level.ServerPlayer ana = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		data.visit(ana.getUUID(), places.get(1));
		data.spend(places.get(1), "16b", List.of(ana.getUUID()), 4);
		data.spend(places.get(0), "16c", List.of(ana.getUUID()), 3);
		data.spend(places.get(1), "16c", List.of(ana.getUUID()), 4);
		data.spend(places.get(2), "18b", List.of(ana.getUUID()), 4);
		java.util.function.Function<UUID, java.util.Optional<com.forzacode.a1016_02.core.Remains.Place>> inside = id -> id.equals(ana.getUUID())
				? java.util.Optional.of(places.get(1)) : java.util.Optional.empty();
		String sixteen = Sc2Index.remains(data, 4, 16, List.of(ana), inside);
		String expected = "16: 2 known, 1 visited, 1 inside now (" + ana.getName().getString() + "); answers today/ever 16b 1/1, 16c 1/2";
		helper.assertTrue(sixteen.equals(expected), "16's line: " + sixteen + ", not " + expected);
		String eighteen = Sc2Index.remains(data, 5, 18, List.of(ana), inside);
		helper.assertTrue(eighteen.equals("18: 1 known, 0 visited, 0 inside now; answers today/ever 18b 0/1"), "18's line: " + eighteen);
		helper.assertTrue(Sc2Index.remains(data, 4, 20, List.of(ana), inside).equals("20: none known"), "20's line");
		check(helper, List.of(sixteen, eighteen));
		helper.succeed();
	}

	/** A row of the layer's: its label is "sc" or starts with "sc " (the screenshot's row is not one). */
	private static boolean layerRow(String row) {
		return row.startsWith("sc ") || row.startsWith("sc" + OverlayPayload.SEPARATOR);
	}

	/** Plain ASCII, and no word that names a number's feature (card ids sc_NN are fine). */
	private static void check(GameTestHelper helper, List<String> lines) {
		for (String line : lines) {
			helper.assertTrue(line.chars().allMatch(ch -> ch >= 32 && ch < 127), "not plain: " + line);
			String lower = line.toLowerCase(Locale.ROOT).replace("sections.sc.enabled", "").replace("/a1016 sc", "");
			for (String word : NAMES) {
				helper.assertFalse(lower.contains(word), "'" + word + "' names a feature: " + line);
			}
			for (String word : NAMES_SC2) {
				helper.assertFalse(lower.contains(word), "'" + word + "' names a feature of the second wave: " + line);
			}
			for (String word : NAMES_SC3) {
				helper.assertFalse(wordIn(lower, word), "'" + word + "' names a feature of round 3: " + line);
			}
		}
	}

	/**
	 * True if {@code word} stands in {@code lower} as a word of its own, or with a plain ending (s, es, ed, ing): never
	 * inside another word ("default", "changing", "today", "split").
	 */
	static boolean wordIn(String lower, String word) {
		return java.util.regex.Pattern.compile("(?<![a-z])" + java.util.regex.Pattern.quote(word) + "(?:s|es|ed|ing)?(?![a-z])").matcher(lower).find();
	}

	/** R3-P: the whole-word rule of round 3's words: their own words trip it, the words they hide in do not. */
	@GameTest
	public void roundThreesWordsAreHeldAsWholeWords(GameTestHelper helper) {
		for (String trips : List.of("a fault here", "faults", "the crumbs", "it is hanging", "asleep: 2", "lit", "(well)")) {
			helper.assertTrue(NAMES_SC3.stream().anyMatch(word -> wordIn(trips, word)), "not held: " + trips);
		}
		for (String passes : List.of("default", "by default", "changing", "today", "split", "delight", "sweller", "campaign")) {
			helper.assertFalse(NAMES_SC3.stream().anyMatch(word -> wordIn(passes, word)), "held: " + passes);
		}
		helper.succeed();
	}
}
