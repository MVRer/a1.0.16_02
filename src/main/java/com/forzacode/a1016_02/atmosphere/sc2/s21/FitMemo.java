package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.server.level.ServerPlayer;

/**
 * A card's last answer to "does the moment fit" per player, kept {@link #TICKS} ticks (21 to 24 ask the world: a herd, the
 * mobs in view, a file's start), so the director's frequent looks cost one real look every two seconds at most. The fire
 * always looks again. Server thread.
 */
public final class FitMemo {
	/** How long an answer is kept (server ticks). */
	public static final int TICKS = 40;

	private record Answer(long tick, boolean fits) {
	}

	private final Map<UUID, Answer> answers = new HashMap<>();

	/** The answer for {@code player}: the kept one if it is under {@link #TICKS} old, else a fresh one from {@code look}. */
	public boolean fits(ServerPlayer player, BooleanSupplier look) {
		long now = player.level().getServer().getTickCount();
		Answer kept = answers.get(player.getUUID());
		if (kept != null && now - kept.tick() >= 0 && now - kept.tick() < TICKS) {
			return kept.fits();
		}
		if (answers.size() > 64) {
			answers.clear();
		}
		boolean fits = look.getAsBoolean();
		answers.put(player.getUUID(), new Answer(now, fits));
		return fits;
	}

	/** Forgets every answer (a fire changes the world). */
	public void clear() {
		answers.clear();
	}
}
