package com.forzacode.a1016_02.director;

import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Tempo;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * VERIFY-3 (v1.3): D-070's first hour for a player who never opens a menu (or a client without the blind signal), on
 * the registered deck with every v1.3 card in it: dig's three real BLIND cards and the_screenshot, not stand-ins.
 * {@code DirectorV13Tests.noBlindMomentsNeverFreezeATier} checks Proximity's ambients there; this checks the first hour
 * and every hard limit, and logs the 4b rates against the deck without BLIND cards.
 */
public class VerifyV13DirectorTests {
	@GameTest(maxTicks = 3000)
	public void verifyNoMenuRunsKeepD070sFirstHour(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		List<String> blind = deck.stream().filter(card -> card.has(CardTag.BLIND)).map(CardInfo::id).toList();
		helper.assertTrue(blind.containsAll(List.of("blind_step", "blind_torch", "blind_door")) && deck.stream().anyMatch(card -> card.id().equals("the_screenshot"))
				&& deck.stream().anyMatch(card -> card.id().equals("echo_mining")) && deck.stream().anyMatch(card -> card.id().equals("echo_door")),
				"the registered deck lacks a v1.3 card: " + deck.stream().map(CardInfo::id).toList());
		DirectorConfig none = new DirectorConfig();
		none.simBlindPerHour = 0;
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> runs = DirectorTestSupport.registryRuns(tempo, none, deck, null);
			DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
			DirectorTestSupport.FourB rates = DirectorTestSupport.FourB.of(runs, deck);
			DirectorTestSupport.FourB plain = DirectorTestSupport.FourB.of(DirectorTestSupport.registryRuns(tempo), deck);
			A1016_02.LOGGER.info("[a1016] VERIFY no-menu runs {}: {}; {} against menus {}", tempo, first.summary(), rates.summary(), plain.summary());
			String at = tempo + ", no menus, " + first.summary() + ": ";
			helper.assertTrue(first.by30() * 4 >= first.runs() * 3, at + "the first fire by 30 minutes in fewer than 3 runs of 4");
			helper.assertTrue(first.by45() * 10 >= first.runs() * 9, at + "the first fire by 45 minutes in fewer than 9 runs of 10");
			helper.assertTrue(first.twoOrMore() * 10 >= first.runs() * 7, at + "two or more in the first hour in fewer than 7 runs of 10");
			helper.assertTrue(first.most() <= 5, at + "more than five in a first hour");
			for (DirectorSim.Result run : runs) {
				String seed = at + "seed " + run.params.seed + ": ";
				helper.assertTrue(run.fires().stream().noneMatch(e -> blind.contains(e.cardId())), seed + "a BLIND card fired with no blind moment");
				helper.assertTrue(run.violations.isEmpty(), seed + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, deck);
				helper.assertTrue(hard.isEmpty(), seed + hard);
			}
		}
		helper.succeed();
	}
}
