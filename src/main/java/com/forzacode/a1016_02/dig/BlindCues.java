package com.forzacode.a1016_02.dig;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The blind cards' sounds in progress (D-077), run by {@link DigTicker}: at most one sequence per player, each sound
 * played when its tick comes (the first in the fire's own tick), the rest dropped the moment the player is no longer
 * blind (they closed the menu: they are looking again), no longer alone ({@link Ladder#alone}) or gone. And the debug
 * arm ({@code /a1016 dig blind arm <card>}): that card fired for the player, as {@code /a1016 fire} does, once their
 * next blind moment is old enough, within {@link #ARM_TICKS}. Per player by UUID, looked up again every tick
 * (D-062.1). Nothing is saved: a sequence is a few seconds of sound.
 */
final class BlindCues {
	/** How long a debug arm waits for a blind moment: two minutes of server ticks (a debug timeout, not pacing). */
	static final long ARM_TICKS = 2400;

	/** One sound: {@code at} ticks after the sequence starts. */
	record Cue(long at, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
	}

	/** One blind card's sounds in progress for one player. */
	static final class Sequence {
		final String label;
		final UUID player;
		final ResourceKey<Level> dimension;
		final Blind.Env env;
		final List<Cue> cues;
		long start = Long.MIN_VALUE;
		int next;
		@Nullable String stopped;

		Sequence(String label, ServerPlayer player, Blind.Env env, List<Cue> cues) {
			this.label = label;
			this.player = player.getUUID();
			this.dimension = player.level().dimension();
			this.env = env;
			this.cues = List.copyOf(cues);
		}

		/** Sounds played so far. */
		int played() {
			return next;
		}

		String describe() {
			return String.format(Locale.ROOT, "%s, %d of %d sounds played%s", label, next, cues.size(), stopped == null ? "" : " (" + stopped + ")");
		}

		/** One tick: plays what is due; false once it is over (every sound played, or stopped). */
		boolean tick(MinecraftServer server, long now) {
			if (start == Long.MIN_VALUE) {
				start = now;
			}
			PlayerWatch watch = env.watch();
			Optional<ServerPlayer> found = watch.participant(server, player);
			if (found.isEmpty() || !found.get().isAlive() || !found.get().level().dimension().equals(dimension)) {
				stopped = "the player is gone";
				return false;
			}
			ServerPlayer target = found.get();
			if (watch.blindTicks(target) <= 0) {
				stopped = "they are looking again";
				return false;
			}
			if (!Ladder.alone(target, watch, env.sounds())) {
				stopped = "someone came close";
				return false;
			}
			ServerLevel level = target.level();
			while (next < cues.size() && start + cues.get(next).at() <= now) {
				Cue cue = cues.get(next++);
				env.sounds().play(level, cue.sound(), cue.source(), cue.pos(), cue.volume(), cue.pitch());
			}
			return next < cues.size();
		}
	}

	/** A debug fire waiting for the player's next blind moment. */
	record Armed(String cardId, boolean fake, long until) {
		String describe() {
			return cardId + (fake ? " (fake)" : "") + ", fired at the next blind moment";
		}
	}

	private final Map<UUID, Sequence> sequences = new LinkedHashMap<>();
	private final Map<UUID, Armed> armed = new LinkedHashMap<>();

	/** Starts a sequence (replacing that player's in progress) and plays at once what is due now. */
	void start(MinecraftServer server, Sequence sequence) {
		Sequence old = sequences.remove(sequence.player);
		if (old != null && old.stopped == null) {
			old.stopped = "replaced";
		}
		if (sequence.tick(server, server.getTickCount())) {
			sequences.put(sequence.player, sequence);
		}
	}

	/** That player's sequence in progress. */
	Optional<Sequence> sequence(UUID player) {
		return Optional.ofNullable(sequences.get(player));
	}

	/** That player's armed debug fire. */
	Optional<Armed> armed(UUID player) {
		return Optional.ofNullable(armed.get(player));
	}

	/** {@code /a1016 dig blind arm}: fires {@code cardId} for the player at their next blind moment (replacing an earlier arm). */
	void arm(MinecraftServer server, UUID player, String cardId, boolean fake) {
		armed.put(player, new Armed(cardId, fake, server.getTickCount() + ARM_TICKS));
	}

	/** A player stopped taking part: their sounds stop, their arm goes. */
	void forget(UUID player) {
		Sequence sequence = sequences.remove(player);
		if (sequence != null && sequence.stopped == null) {
			sequence.stopped = "the player is gone";
		}
		armed.remove(player);
	}

	void clear() {
		sequences.clear();
		armed.clear();
	}

	/** Every tick: the sequences in progress and the armed fires (nothing to do without one). */
	void tick(MinecraftServer server, long now) {
		if (!sequences.isEmpty()) {
			Iterator<Sequence> it = sequences.values().iterator();
			while (it.hasNext()) {
				if (!it.next().tick(server, now)) {
					it.remove();
				}
			}
		}
		if (!armed.isEmpty()) {
			tickArmed(server, now);
		}
	}

	/**
	 * What the armed card's player is told once it fired: the result, and when it did not fire why (FIX-GLIMPSE: for the
	 * torch, why none may go, {@link Blind#reason}).
	 */
	static String firedLine(Armed arm, FireResult result, ServerPlayer player, Blind.Env env) {
		String why = "";
		if (result != FireResult.FIRED) {
			for (Blind.Kind kind : Blind.Kind.values()) {
				if (kind.id.equals(arm.cardId())) {
					why = ": " + Blind.reason(kind, player, env, result);
				}
			}
		}
		return String.format(Locale.ROOT, "[a1016] dig: the armed %s%s fired in your blind moment -> %s%s", arm.cardId(), arm.fake() ? " (fake)" : "", result,
				why);
	}

	private void tickArmed(MinecraftServer server, long now) {
		PlayerWatch watch = Services.watch();
		long wait = Blind.minBlindTicks(DigConfig.get());
		Iterator<Map.Entry<UUID, Armed>> it = armed.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Armed> entry = it.next();
			Armed arm = entry.getValue();
			Optional<ServerPlayer> player = watch.participant(server, entry.getKey());
			if (now > arm.until()) {
				it.remove();
				String line = String.format(Locale.ROOT, "[a1016] dig: the armed %s waited %d s for a blind moment (a menu open %.1f s) and was dropped",
						arm.cardId(), ARM_TICKS / 20, wait / 20.0);
				A1016_02.LOGGER.info(line);
				player.ifPresent(p -> DigCommands.tell(p, line));
				continue;
			}
			if (player.isPresent() && watch.blindTicks(player.get()) >= wait) {
				it.remove();
				FireResult result = Services.director().fire(server, player.get(), arm.cardId(), arm.fake());
				DigCommands.tell(player.get(), firedLine(arm, result, player.get(), Blind.Env.live(server)));
			}
		}
	}
}
