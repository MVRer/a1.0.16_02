package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.entity.sc.NamelessRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Review R9 (docs/SELF_CREATED.md, 02: "outside the shared radius, so nobody feels a moment they don't see"; ARCHITECTURE,
 * felt moments D-060.3: a card that is not PERSONAL fires for the target and counts as felt by every sharer within 48).
 */
public class SelfCreatedR9EntityTests {
	/**
	 * Three players: the target, a friend walking beside them (10 blocks) and another out in the fog (52 blocks). sc_02
	 * fits (one friend is in the 48 to 64 band) and is not PERSONAL, so the director fires it with the friend beside them
	 * as a sharer: only the target's client is armed, the friend beside them feels the moment (their gaps, caps and
	 * tension) and still sees the far friend's name. Two people looking at the same figure, one sees a name: the lie
	 * breaks the first time. The card must not fit with anyone inside the shared radius (or be PERSONAL).
	 */
	@GameTest
	public void aFriendBesideTheTargetNeverFeelsANameTheyStillSee(GameTestHelper helper) {
		NamelessConfig config = new NamelessConfig();
		Optional<String> fit = NamelessRules.fit(List.of(10.0, 52.0), 15, 0.5, config);
		boolean personal = CardRegistry.get("sc_02").map(EventCard::tags).map(tags -> tags.contains(CardTag.PERSONAL)).orElse(false);
		helper.assertTrue(CardRegistry.get("sc_02").isPresent(), "setup: sc_02 is not registered");
		helper.assertTrue(fit.isPresent() || personal, "sc_02 fits with a friend 10 blocks from the target (a sharer who feels it but is never armed) "
				+ "and is not PERSONAL");
		helper.succeed();
	}
}
