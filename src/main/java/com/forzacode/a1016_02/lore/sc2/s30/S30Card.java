package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code sc_30} (docs/SELF_CREATED_2.md 30; MINOR, Proximity, ITEM + UNSHARED, STRIPPER + COLLECTOR, no fake): the
 * target's own map changes ({@link Maps}). UNSHARED: it may fire beside a friend and only the target feels it (their map,
 * not the friend's). The director looks often: the answer per player is kept {@value #MEMO_TICKS} ticks; the fire always
 * looks again.
 */
public final class S30Card implements EventCard {
	/** How long an answer to "does the moment fit" is kept per player (server ticks). */
	public static final int MEMO_TICKS = 40;

	private record Answer(long tick, boolean fits) {
	}

	/** The world it reads: live, or a test's own. */
	public Function<MinecraftServer, Maps.Env> env = Maps.Env::live;
	private final Map<UUID, Answer> answers = new HashMap<>();

	@Override
	public String id() {
		return Maps.ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.PROXIMITY;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.STRIPPER, Habit.COLLECTOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.ITEM, CardTag.UNSHARED);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		long now = world.getServer().getTickCount();
		Answer kept = answers.get(player.getUUID());
		if (kept != null && now - kept.tick() >= 0 && now - kept.tick() < MEMO_TICKS) {
			return kept.fits();
		}
		if (answers.size() > 64) {
			answers.clear();
		}
		boolean fits = Maps.refusal(env.apply(world.getServer()), player, false).isEmpty();
		answers.put(player.getUUID(), new Answer(now, fits));
		return fits;
	}

	@Override
	public FireResult fire(FireContext ctx) {
		answers.clear();
		if (ctx.fake()) {
			return FireResult.SKIPPED;
		}
		return Maps.fire(env.apply(ctx.level().getServer()), ctx.player(), ctx.forced(), ctx.random()).result();
	}

	public void forget() {
		answers.clear();
	}
}
