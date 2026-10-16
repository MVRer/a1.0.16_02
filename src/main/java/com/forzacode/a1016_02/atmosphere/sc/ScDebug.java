package com.forzacode.a1016_02.atmosphere.sc;

import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * What a debug arm says later (fired, ran out): only to the op who ran the command, in plain words (D-080), else
 * (the console) at debug level in the log, never in anyone else's chat.
 */
public final class ScDebug {
	private ScDebug() {
	}

	public static void tell(MinecraftServer server, @Nullable UUID op, String line) {
		ServerPlayer player = op == null ? null : server.getPlayerList().getPlayer(op);
		if (player != null) {
			player.sendSystemMessage(Component.literal("[a1016] " + line));
		} else {
			A1016_02.LOGGER.debug("[a1016] {}", line);
		}
	}
}
