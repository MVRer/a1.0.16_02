package com.forzacode.a1016_02.world.sc2.s28;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SelfCreated;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 28's visit tracker (docs/SELF_CREATED_2.md, 28): every {@value #EVERY_TICKS} ticks, each participant within
 * {@code visitRadius} (horizontal) of another known player's base ({@code world.Bases}: the live base, else the last
 * one world saw, else their first block; offline players' too) in the same dimension visited it now (the world's day
 * tick). A spectator visits nobody. Nothing while the layer is off. Server thread; cheap (participants times known
 * players).
 */
public final class Visitors {
	/** How often the visits are looked at (server ticks, 5 s). */
	public static final int EVERY_TICKS = 100;

	private Visitors() {
	}

	/** One look: every visit now, recorded in the env's data. Returns the visits seen (visitor, host). */
	public static List<S28Data.Pair> look(S28Env env) {
		List<S28Data.Pair> seen = new ArrayList<>();
		if (!SelfCreated.enabled()) {
			return seen;
		}
		long now = GameClock.dayTicks(env.server());
		double radius = env.config().visitRadius;
		List<UUID> hosts = new ArrayList<>();
		for (HerobrineState.Subject subject : env.state().players()) {
			hosts.add(subject.uuid());
		}
		List<ServerPlayer> participants = env.bases().participants();
		for (ServerPlayer player : participants) {
			if (!hosts.contains(player.getUUID())) {
				hosts.add(player.getUUID());
			}
		}
		for (ServerPlayer visitor : participants) {
			for (UUID host : hosts) {
				if (host.equals(visitor.getUUID())) {
					continue;
				}
				GlobalPos base = env.bases().base(host).orElse(null);
				if (base == null || !base.dimension().equals(visitor.level().dimension())) {
					continue;
				}
				double dx = visitor.getX() - (base.pos().getX() + 0.5);
				double dz = visitor.getZ() - (base.pos().getZ() + 0.5);
				if (dx * dx + dz * dz <= radius * radius) {
					env.data().visit(visitor.getUUID(), host, now);
					seen.add(new S28Data.Pair(visitor.getUUID(), host));
				}
			}
		}
		return seen;
	}
}
