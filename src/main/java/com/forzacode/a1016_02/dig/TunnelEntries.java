package com.forzacode.a1016_02.dig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import net.minecraft.server.level.ServerPlayer;

/**
 * ENTERED_TUNNEL once per occasion (D-060): a player in one of his tunnels raises it once per
 * {@link DigConfig#enteredTunnelCooldownMinutes} of their own play, and everyone sharing that moment with them (the
 * same dimension, within {@code sharedMomentRadius}) shares the cooldown: two friends walking into the same tunnel
 * together count once, not once each. Players far apart in different tunnels are different occasions. Keyed by UUID
 * (D-062.2), for this server session. Server thread only.
 */
final class TunnelEntries {
	/** Each player's own play ticks when their last occasion counted. */
	private final Map<UUID, Long> last = new HashMap<>();

	/**
	 * Looks at the participants standing in his tunnels now ({@code inside}, in join order): the first whose own
	 * cooldown is over counts once ({@code trigger}), and every player {@code sharing} that moment with them (the
	 * player first) starts their cooldown with it. Returns how many times the trigger ran.
	 *
	 * @param clock    a player's own play ticks
	 * @param cooldown in play ticks
	 */
	int check(List<ServerPlayer> inside, Function<ServerPlayer, List<ServerPlayer>> sharing, ToLongFunction<UUID> clock, long cooldown, Runnable trigger) {
		int fired = 0;
		Set<UUID> covered = new HashSet<>();
		for (ServerPlayer player : inside) {
			UUID id = player.getUUID();
			if (covered.contains(id) || !due(id, clock.applyAsLong(id), cooldown)) {
				continue;
			}
			trigger.run();
			fired++;
			for (ServerPlayer felt : sharing.apply(player)) {
				last.put(felt.getUUID(), clock.applyAsLong(felt.getUUID()));
				covered.add(felt.getUUID());
			}
			last.put(id, clock.applyAsLong(id));
			covered.add(id);
		}
		return fired;
	}

	private boolean due(UUID player, long now, long cooldown) {
		Long at = last.get(player);
		return at == null || now - at >= cooldown;
	}

	void clear() {
		last.clear();
	}
}
