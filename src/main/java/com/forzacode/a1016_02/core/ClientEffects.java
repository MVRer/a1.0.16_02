package com.forzacode.a1016_02.core;

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
 * dusk fog) are stored in {@link HerobrineState} and sent again on every join.
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

	/** Every persistent effect at once, sent on join. */
	public record Sync(boolean musicOff, float duskFogLevel) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(A1016_02.id("sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Sync::musicOff,
				ByteBufCodecs.FLOAT, Sync::duskFogLevel,
				Sync::new);

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
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
	}

	public static void fogSurge(ServerPlayer player, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		send(player, new FogSurge(strength, rampTicks, holdTicks, fadeTicks));
	}

	public static void silence(ServerPlayer player, int ticks, int fadeTicks) {
		send(player, new Silence(ticks, fadeTicks));
	}

	/** Stores the setting and sends it to everyone online. */
	public static void setMusicOff(MinecraftServer server, boolean off) {
		HerobrineState state = HerobrineState.get(server);
		state.setEffects(new HerobrineState.Effects(off, state.effects().duskFogLevel()));
		server.getPlayerList().getPlayers().forEach(player -> send(player, new MusicOff(off)));
	}

	/** Stores the level (clamped to 0..1) and sends it to everyone online. */
	public static void setDuskFog(MinecraftServer server, float level) {
		float clamped = Math.clamp(level, 0.0F, 1.0F);
		HerobrineState state = HerobrineState.get(server);
		state.setEffects(new HerobrineState.Effects(state.effects().musicOff(), clamped));
		server.getPlayerList().getPlayers().forEach(player -> send(player, new DuskFog(clamped)));
	}

	/** Sends every persistent effect. Core calls this on join. */
	public static void sync(ServerPlayer player) {
		HerobrineState.Effects effects = HerobrineState.get(player.level().getServer()).effects();
		send(player, new Sync(effects.musicOff(), effects.duskFogLevel()));
	}

	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
