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
 * number, 01 to 15, plain and in opaque words; the overlay shows the layer's rows only after
 * {@code /a1016 debug overlay sc}.
 */
public class SelfCreatedDebugTests {
	/** Words that would name what a number is (D-080): none may be in the index or the overlay's layer rows. */
	static final List<String> NAMES = List.of("moon", "night", "name", "bed", "sleep", "rest", "torch", "step", "foot", "print", "track", "fog", "icon",
			"picture", "view", "seed", "version", "house", "home", "door", "chest", "pet", "animal", "friend", "cave", "lava", "shaft", "stat", "mined",
			"dropped", "turn", "trail", "brb", "record", "hole", "block", "wall", "way back", "stood", "stand");

	@GameTest
	public void theScLiteralHoldsTheIndexAndEveryNumber(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> sc = server.getCommands().getDispatcher().getRoot().getChild(CommandHooks.ROOT).getChild("sc");
		helper.assertTrue(sc != null && sc.getCommand() != null, "/a1016 sc has no index");
		helper.assertTrue(sc.getChild("on") != null && sc.getChild("off") != null, "/a1016 sc has no switch");
		for (int n = 1; n <= 15; n++) {
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
		helper.assertTrue(lines.size() == 16 && lines.getFirst().startsWith("[a1016] sc: "), "the index: " + lines);
		for (int n = 1; n <= 15; n++) {
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
		}
	}
}
