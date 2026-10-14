package com.forzacode.a1016_02.director;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Who a debug or director command acts on (D-062.9): the player named in the command, else the player running it,
 * else (the console, a command block) the first participant in join order. Looked up when the command runs; nothing
 * is kept.
 */
public final class CommandTarget {
	private CommandTarget() {
	}

	/** The player to act on, or empty when the console runs it and nobody takes part. */
	public static Optional<ServerPlayer> player(CommandSourceStack source, @Nullable ServerPlayer named) {
		if (named != null) {
			return Optional.of(named);
		}
		ServerPlayer self = source.getPlayer();
		if (self != null) {
			return Optional.of(self);
		}
		List<ServerPlayer> participants = Services.watch().participants(source.getServer());
		return participants.isEmpty() ? Optional.empty() : Optional.of(participants.getFirst());
	}

	/**
	 * Whose pacing a read-only command shows: as {@link #player}, and from the console with nobody taking part, the
	 * first known player (offline). Empty when nobody was ever known.
	 */
	public static Optional<UUID> pacing(CommandSourceStack source, @Nullable ServerPlayer named) {
		Optional<ServerPlayer> player = player(source, named);
		return player.isPresent() ? player.map(ServerPlayer::getUUID) : Optional.ofNullable(DirectorImpl.focus(source.getServer()));
	}

	/** Why {@code player} cannot be acted on right now, or null if it can (a targetable participant). */
	public static @Nullable String problem(@Nullable ServerPlayer player) {
		if (player == null) {
			return "nobody takes part right now; name a player";
		}
		if (!Services.watch().isTargetable(player)) {
			return player.getName().getString() + " is not taking part right now (a spectator, dead or gone); name a player";
		}
		return null;
	}
}
