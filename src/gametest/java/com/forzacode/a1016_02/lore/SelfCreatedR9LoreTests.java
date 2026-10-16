package com.forzacode.a1016_02.lore;

import java.util.List;

import com.forzacode.a1016_02.accident.Traps;
import com.forzacode.a1016_02.lore.sc.RecordRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Review R9 (docs/SELF_CREATED.md, 14 (d); ARCHITECTURE code guardrail 7, "every accident is deniable and leaves exactly
 * one clue"; DESIGN, Accidents: "the rung is gone, not dropped as an item", "you remember placing it", "one torch is a
 * block off"). The statistics' lie is for his own traces; an armed accident's setup is not one of them.
 */
public class SelfCreatedR9LoreTests {
	/**
	 * The missing rung, the short bridge, the bare wool and the dark corner each take a block a player placed, ledgered
	 * under {@code accident:<trap>}: (d) counts it in "Times Mined" for the nearest base's player, a second clue beside the
	 * trap's one, and it shows the moment the trap is armed, before anything happened (the dark corner's torches come back
	 * by morning; the count stays).
	 */
	@GameTest
	public void anArmedAccidentLeavesNoSecondClueInTheStatistics(GameTestHelper helper) {
		List<String> causes = List.of("accident:" + Traps.MISSING_RUNG.id(), "accident:" + Traps.SHORT_BRIDGE.id(), "accident:" + Traps.BARE_WOOL.id(),
				"accident:" + Traps.DARK_CORNER.id());
		List<String> counted = causes.stream().filter(RecordRules::causeCounts).toList();
		helper.assertTrue(counted.isEmpty(), "(d) counts an armed trap's taken block in the statistics: " + counted);
		helper.succeed();
	}
}
