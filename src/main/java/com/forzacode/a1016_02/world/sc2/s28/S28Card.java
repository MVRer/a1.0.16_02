package com.forzacode.a1016_02.world.sc2.s28;

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
import net.minecraft.util.RandomSource;

/**
 * {@code sc_28} (docs/SELF_CREATED_2.md 28; MINOR, Traces, ITEM, COLLECTOR + VISITOR, with a fake): a stack goes from one
 * player's base to another's ({@link Moves}). Felt by nobody: an absence found later. Its moment needs a friend with a
 * base of their own who came by, so alone it parks (the director's unfit park). The director looks often: the answer
 * per player is kept {@value #MEMO_TICKS} ticks; the fire always plans again.
 */
public final class S28Card implements EventCard {
	/** How long an answer to "does the moment fit" is kept per player (server ticks). */
	public static final int MEMO_TICKS = 100;

	private record Answer(long tick, boolean fits) {
	}

	/** The world it acts on: live, or a test's own. */
	public Function<MinecraftServer, S28Env> env = S28Env::live;
	private final Map<UUID, Answer> answers = new HashMap<>();

	@Override
	public String id() {
		return Moves.ID;
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
		return Set.of(Habit.COLLECTOR, Habit.VISITOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.ITEM);
	}

	@Override
	public boolean hasFake() {
		return true;
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
		boolean fits = Moves.plan(env.apply(world.getServer()), player, false, false, RandomSource.create(now)).found();
		answers.put(player.getUUID(), new Answer(now, fits));
		return fits;
	}

	@Override
	public FireResult fire(FireContext ctx) {
		answers.clear();
		return Moves.fire(env.apply(ctx.level().getServer()), ctx.player(), ctx.forced(), ctx.fake(), ctx.random()).result();
	}

	/** Forgets every kept answer (tests, and after a fire). */
	public void forget() {
		answers.clear();
	}
}
