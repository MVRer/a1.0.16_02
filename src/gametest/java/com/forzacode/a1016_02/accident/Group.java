package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.DeathMarker;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stand-in players who take part, for the code that asks who does: a private online list (mock players are never in
 * the test server's player list, so they are never anyone else's participants) and a private story state that knows
 * them in this order. The planner and death marker built here never touch the shared state or another test's players.
 */
final class Group implements PlayerWatch.Source {
	final GameTestHelper helper;
	/** Online right now, in the server's order. Tests take players out (a leave) or put them back. */
	final List<ServerPlayer> online = new ArrayList<>();
	final HerobrineState state;
	final PlayerWatch watch;

	Group(GameTestHelper helper, ServerPlayer... players) {
		this.helper = helper;
		online.addAll(List.of(players));
		state = known(players);
		watch = new PlayerWatch(this);
	}

	/** A story state whose known players are these, in this first-join order (as a v1.1 save holds them). */
	static HerobrineState known(ServerPlayer... players) {
		JsonArray list = new JsonArray();
		for (ServerPlayer player : players) {
			JsonObject record = new JsonObject();
			record.add("uuid", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, player.getUUID()).getOrThrow());
			record.addProperty("name", player.getName().getString());
			list.add(record);
		}
		JsonObject root = new JsonObject();
		root.add("players", list);
		return HerobrineState.CODEC.parse(JsonOps.INSTANCE, root).getOrThrow();
	}

	@Override
	public List<ServerPlayer> online(MinecraftServer server) {
		return List.copyOf(online);
	}

	@Override
	public HerobrineState state(MinecraftServer server) {
		return state;
	}

	@Override
	public void participation(MinecraftServer server, UUID player, boolean participating) {
	}

	/** A death marker over this group's state and the given data. */
	DeathMarkerImpl marker(AccidentData data, ViewGate view) {
		return new DeathMarkerImpl(server -> data, view, server -> state);
	}

	/** A planner over this group (who takes part) and the given data, marking deaths through this group's marker. */
	AccidentPlannerImpl planner(AccidentData data, ViewGate view) {
		return planner(data, view, AccidentPlannerImpl.Clocks.GAME);
	}

	/** The same on these clocks ({@link Clocks}: the world's and each player's own, moved by hand). */
	AccidentPlannerImpl planner(AccidentData data, ViewGate view, AccidentPlannerImpl.Clocks clocks) {
		DeathMarker marker = marker(data, view);
		return new AccidentPlannerImpl(server -> data, view, watch, () -> marker, clocks);
	}

	/** Play clocks a test moves by hand (D-059): the world's runs while anyone plays, each player's own only while they do. */
	static final class Clocks implements AccidentPlannerImpl.Clocks {
		long world;
		final Map<UUID, Long> own = new HashMap<>();

		/** {@code ticks} of play with these players online. */
		void play(long ticks, ServerPlayer... online) {
			world += ticks;
			for (ServerPlayer player : online) {
				own.merge(player.getUUID(), ticks, Long::sum);
			}
		}

		@Override
		public long world(MinecraftServer server) {
			return world;
		}

		@Override
		public long own(MinecraftServer server, UUID player) {
			return own.getOrDefault(player, 0L);
		}
	}
}
