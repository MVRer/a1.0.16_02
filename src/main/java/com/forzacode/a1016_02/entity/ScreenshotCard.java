package com.forzacode.a1016_02.entity;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code the_screenshot} (D-078): MINOR, Traces, SIGHTING, once per world, no fake; weighted toward the WATCHER habit
 * like every sighting. Firing arms that player's client ({@link TheScreenshot#arm}); the sighting itself happens, if
 * ever, in the next screenshot they take with the conditions met. It only fits in the overworld
 * ({@link ScreenshotRules#gate}). A debug fire skips the gates and arms even after it happened (the first record stays).
 */
final class ScreenshotCard implements EventCard {
	private final TheScreenshot screenshot;

	ScreenshotCard(TheScreenshot screenshot) {
		this.screenshot = screenshot;
	}

	@Override
	public String id() {
		return TheScreenshot.CARD_ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SIGHTING);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return screenshot.gate(player, world).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (ctx.fake()) {
			return FireResult.SKIPPED; // no fake
		}
		if (!ctx.forced() && !contextFits(ctx.player(), ctx.level())) {
			return FireResult.SKIPPED;
		}
		return screenshot.arm(ctx.player());
	}
}
