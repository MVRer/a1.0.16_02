package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.mojang.authlib.GameProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A test's own crew (D-058 to D-062): its own online list and story state through {@link PlayerWatch.Source}, and
 * its own {@link DigData}, so two-player tests never depend on who else is online on the shared game test server.
 * Its players never join the server.
 */
final class Crew implements PlayerWatch.Source {
	/** A player only this crew lists as online. */
	static final class Member extends ServerPlayer {
		Member(ServerLevel level, UUID uuid, String name) {
			super(level.getServer(), level, new GameProfile(uuid, name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	final GameTestHelper helper;
	final List<ServerPlayer> online = new ArrayList<>();
	final HerobrineState state;
	final PlayerWatch watch = new PlayerWatch(this);
	final DigData data = new DigData();

	/** A crew whose known players are these names, in this first-join order (no records of their own yet). */
	Crew(GameTestHelper helper, String... names) {
		this.helper = helper;
		JsonArray list = new JsonArray();
		for (String name : names) {
			JsonObject record = new JsonObject();
			record.add("uuid", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, UUID.randomUUID()).getOrThrow());
			record.addProperty("name", name);
			list.add(record);
		}
		JsonObject root = new JsonObject();
		root.add("players", list);
		this.state = HerobrineState.CODEC.parse(JsonOps.INSTANCE, root).getOrThrow();
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

	/** The known player with this name. */
	UUID uuid(String name) {
		return state.players().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow().uuid();
	}

	/** Puts this known player online at {@code absolute}. */
	ServerPlayer join(String name, Vec3 absolute) {
		Member player = new Member(helper.getLevel(), uuid(name), name);
		player.snapTo(absolute.x, absolute.y, absolute.z, 0.0F, 0.0F);
		online.add(player);
		return player;
	}

	void leave(ServerPlayer player) {
		online.removeIf(p -> p.getUUID().equals(player.getUUID()));
	}

	Bases bases() {
		return new Bases(helper.getLevel().getServer(), watch, state, data);
	}
}
