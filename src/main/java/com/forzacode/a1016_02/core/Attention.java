package com.forzacode.a1016_02.core;

import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only way to change attention and tension (both 0 to 100, clamped). Attention is one value for the world
 * (D-058): anyone's actions (DESIGN.md "Triggers") move it, and it only speeds stages up or slows them down.
 * Tension is the director's and belongs to each player (D-059); a player who was never a participant has none.
 */
public final class Attention {
	private Attention() {
	}

	/** Applies the configured weight of a Triggers row (positive raises, negative lowers). */
	public static void trigger(MinecraftServer server, AttentionTrigger trigger) {
		change(server, ModConfig.pacing().attentionWeight(trigger), trigger.name());
	}

	public static void raise(MinecraftServer server, double amount, String reason) {
		change(server, Math.abs(amount), reason);
	}

	public static void lower(MinecraftServer server, double amount, String reason) {
		change(server, -Math.abs(amount), reason);
	}

	/** Raises that player's tension. Does nothing for someone who was never a participant. */
	public static void raiseTension(MinecraftServer server, UUID player, double amount, String reason) {
		HerobrineState.get(server).addTension(player, Math.abs(amount));
		A1016_02.LOGGER.debug("[a1016] tension {} +{} ({})", player, amount, reason);
	}

	/** Lowers that player's tension. Does nothing for someone who was never a participant. */
	public static void lowerTension(MinecraftServer server, UUID player, double amount, String reason) {
		HerobrineState.get(server).addTension(player, -Math.abs(amount));
		A1016_02.LOGGER.debug("[a1016] tension {} -{} ({})", player, amount, reason);
	}

	/**
	 * Raises the tension of every online participant.
	 *
	 * @deprecated tension is per player (D-059); use {@link #raiseTension(MinecraftServer, UUID, double, String)}. Removed in MP-8.
	 */
	@Deprecated(forRemoval = true)
	public static void raiseTension(MinecraftServer server, double amount, String reason) {
		for (ServerPlayer player : Services.watch().participants(server)) {
			raiseTension(server, player.getUUID(), amount, reason);
		}
	}

	/**
	 * Lowers the tension of every online participant.
	 *
	 * @deprecated tension is per player (D-059); use {@link #lowerTension(MinecraftServer, UUID, double, String)}. Removed in MP-8.
	 */
	@Deprecated(forRemoval = true)
	public static void lowerTension(MinecraftServer server, double amount, String reason) {
		for (ServerPlayer player : Services.watch().participants(server)) {
			lowerTension(server, player.getUUID(), amount, reason);
		}
	}

	private static void change(MinecraftServer server, double delta, String reason) {
		HerobrineState state = HerobrineState.get(server);
		state.addAttention(delta);
		A1016_02.LOGGER.debug("[a1016] attention {} {} -> {} ({})", delta >= 0 ? "+" : "", delta, state.attention(), reason);
	}
}
