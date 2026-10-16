package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-to-client effects. Payload types live here and are registered on both sides; atmosphere does the
 * client side by installing a {@code core.client.ClientEffectsClient.Handler}. Persistent effects (music off,
 * dusk fog, the fog spell) are stored in {@link HerobrineState}, sent to everyone online and sent again on every join.
 * Fog surges and silences he makes reach everyone sharing the moment ({@link #fogSurgeShared}, {@link #silenceShared},
 * D-060). The payload {@link FogSpell} shares its name with core's {@link com.forzacode.a1016_02.core.FogSpell} record
 * it carries; inside this class the bare name is the payload.
 */
public final class ClientEffects {
	/** Fog closes in to {@code strength} (0 to 1) over {@code rampTicks}, holds, then fades back. */
	public record FogSurge(float strength, int rampTicks, int holdTicks, int fadeTicks) implements CustomPacketPayload {
		public static final Type<FogSurge> TYPE = new Type<>(A1016_02.id("fog_surge"));
		public static final StreamCodec<ByteBuf, FogSurge> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, FogSurge::strength,
				ByteBufCodecs.VAR_INT, FogSurge::rampTicks,
				ByteBufCodecs.VAR_INT, FogSurge::holdTicks,
				ByteBufCodecs.VAR_INT, FogSurge::fadeTicks,
				FogSurge::new);

		@Override
		public Type<FogSurge> type() {
			return TYPE;
		}
	}

	/** Ambient sound drops out for {@code ticks}, then comes back over {@code fadeTicks}. */
	public record Silence(int ticks, int fadeTicks) implements CustomPacketPayload {
		public static final Type<Silence> TYPE = new Type<>(A1016_02.id("silence"));
		public static final StreamCodec<ByteBuf, Silence> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Silence::ticks,
				ByteBufCodecs.VAR_INT, Silence::fadeTicks,
				Silence::new);

		@Override
		public Type<Silence> type() {
			return TYPE;
		}
	}

	/** Music stops (and stays stopped) or comes back. Persistent. */
	public record MusicOff(boolean off) implements CustomPacketPayload {
		public static final Type<MusicOff> TYPE = new Type<>(A1016_02.id("music_off"));
		public static final StreamCodec<ByteBuf, MusicOff> CODEC = ByteBufCodecs.BOOL.map(MusicOff::new, MusicOff::off);

		@Override
		public Type<MusicOff> type() {
			return TYPE;
		}
	}

	/** How heavy the dusk fog is, 0 (vanilla) to 1. Persistent. */
	public record DuskFog(float level) implements CustomPacketPayload {
		public static final Type<DuskFog> TYPE = new Type<>(A1016_02.id("dusk_fog"));
		public static final StreamCodec<ByteBuf, DuskFog> CODEC = ByteBufCodecs.FLOAT.map(DuskFog::new, DuskFog::level);

		@Override
		public Type<DuskFog> type() {
			return TYPE;
		}
	}

	/**
	 * The world's fog spell (D-071), or none: the sky is clear (any spell before is over). Persistent. Every client
	 * computes the spell's fog from it and its own clock ({@code getDefaultClockTime()}), the same as the server's
	 * {@link FogLimits}.
	 */
	public record FogSpell(Optional<com.forzacode.a1016_02.core.FogSpell> spell) implements CustomPacketPayload {
		public static final Type<FogSpell> TYPE = new Type<>(A1016_02.id("fog_spell"));
		public static final StreamCodec<ByteBuf, FogSpell> CODEC = ByteBufCodecs.optional(com.forzacode.a1016_02.core.FogSpell.STREAM_CODEC)
				.map(FogSpell::new, FogSpell::spell);

		public FogSpell {
			spell = spell != null ? spell : Optional.empty();
		}

		@Override
		public Type<FogSpell> type() {
			return TYPE;
		}
	}

	/** Every persistent effect at once, sent on join. */
	public record Sync(boolean musicOff, float duskFogLevel, Optional<com.forzacode.a1016_02.core.FogSpell> fogSpell) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(A1016_02.id("sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Sync::musicOff,
				ByteBufCodecs.FLOAT, Sync::duskFogLevel,
				ByteBufCodecs.optional(com.forzacode.a1016_02.core.FogSpell.STREAM_CODEC), Sync::fogSpell,
				Sync::new);

		public Sync {
			fogSpell = fogSpell != null ? fogSpell : Optional.empty();
		}

		/** Without a fog spell. */
		public Sync(boolean musicOff, float duskFogLevel) {
			this(musicOff, duskFogLevel, Optional.empty());
		}

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	private ClientEffects() {
	}

	/** Registers the payload types. Common init, so it runs on both sides. */
	static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(FogSurge.TYPE, FogSurge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Silence.TYPE, Silence.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MusicOff.TYPE, MusicOff.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuskFog.TYPE, DuskFog.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FogSpell.TYPE, FogSpell.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
	}

	/** A fog surge for this player only. One he makes uses {@link #fogSurgeShared} (D-060). */
	public static void fogSurge(ServerPlayer player, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		send(player, new FogSurge(strength, rampTicks, holdTicks, fadeTicks));
	}

	/** A silence for this player only. One he makes uses {@link #silenceShared} (D-060). */
	public static void silence(ServerPlayer player, int ticks, int fadeTicks) {
		send(player, new Silence(ticks, fadeTicks));
	}

	/**
	 * The same fog surge, at the same moment, for {@code target} and everyone sharing the moment with them
	 * ({@link PlayerWatch#sharingWith}, D-060).
	 *
	 * @return who it went to, target first (for server-side bookkeeping per player)
	 */
	public static List<ServerPlayer> fogSurgeShared(ServerPlayer target, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		List<ServerPlayer> players = Services.watch().sharingWith(target);
		FogSurge payload = new FogSurge(strength, rampTicks, holdTicks, fadeTicks);
		players.forEach(player -> send(player, payload));
		return players;
	}

	/**
	 * The same silence, at the same moment, for {@code target} and everyone sharing the moment with them
	 * ({@link PlayerWatch#sharingWith}, D-060).
	 *
	 * @return who it went to, target first (for server-side bookkeeping per player)
	 */
	public static List<ServerPlayer> silenceShared(ServerPlayer target, int ticks, int fadeTicks) {
		List<ServerPlayer> players = Services.watch().sharingWith(target);
		Silence payload = new Silence(ticks, fadeTicks);
		players.forEach(player -> send(player, payload));
		return players;
	}

	/** Stores the setting and sends it to everyone online. */
	public static void setMusicOff(MinecraftServer server, boolean off) {
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects old = state.effects();
		state.setEffects(new HerobrineState.Effects(off, old.duskFogLevel(), old.fogSpell()));
		server.getPlayerList().getPlayers().forEach(player -> send(player, new MusicOff(off)));
	}

	/** Stores the level (clamped to 0..1) and sends it to everyone online. */
	public static void setDuskFog(MinecraftServer server, float level) {
		float clamped = Math.clamp(level, 0.0F, 1.0F);
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects old = state.effects();
		state.setEffects(new HerobrineState.Effects(old.musicOff(), clamped, old.fogSpell()));
		server.getPlayerList().getPlayers().forEach(player -> send(player, new DuskFog(clamped)));
	}

	/**
	 * Stores the world's fog spell (D-071; empty clears it) and sends it to everyone online, spectators included. One
	 * spell at a time: a new one replaces the old (join them first with {@link FogWeatherRules#handOver} so the fog
	 * goes on from where it is). Server thread.
	 */
	public static void setFogSpell(MinecraftServer server, Optional<com.forzacode.a1016_02.core.FogSpell> spell) {
		Optional<com.forzacode.a1016_02.core.FogSpell> stored = spell != null ? spell : Optional.empty();
		HerobrineState state = HerobrineState.get(server);
		state.setEffects(state.effects().withFogSpell(stored));
		FogSpell payload = new FogSpell(stored);
		server.getPlayerList().getPlayers().forEach(player -> send(player, payload));
	}

	/** Every persistent effect of this state, as {@link #sync} sends it. */
	public static Sync syncPayload(HerobrineState.Effects effects) {
		return new Sync(effects.musicOff(), effects.duskFogLevel(), effects.fogSpell());
	}

	/** Sends every persistent effect. Core calls this on every join, spectators included. */
	public static void sync(ServerPlayer player) {
		send(player, syncPayload(HerobrineState.get(player.level().getServer()).effects()));
	}

	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
