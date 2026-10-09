package com.forzacode.a1016_02.debug;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sample card for testing {@code /a1016 fire}: sends the player one chat line. Its context never fits, so the
 * director never draws it in normal play; {@code fire} skips gates and runs it anyway.
 */
public final class DebugPingCard implements EventCard {
	public static final String ID = "debug_ping";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.AMBIENT;
	}

	@Override
	public Stage earliestStage() {
		return Stage.ALONE;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of();
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.TEXT);
	}

	@Override
	public boolean hasFake() {
		return true;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return false;
	}

	@Override
	public FireResult fire(FireContext ctx) {
		String key = ctx.fake() ? "a1016_02.debug.ping.fake" : "a1016_02.debug.ping";
		String fallback = ctx.fake() ? "[a1016] debug_ping fired (fake)" : "[a1016] debug_ping fired";
		ctx.player().sendSystemMessage(Component.translatableWithFallback(key, fallback));
		return FireResult.FIRED;
	}
}
