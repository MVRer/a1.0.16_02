package com.forzacode.a1016_02.director.sc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.director.CommandTarget;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorMemory;
import com.forzacode.a1016_02.director.PacingMemory;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 sc 01 [player]}: the factor where that player stands, their nearest places and the trusted chunk;
 * {@code /a1016 sc 01 trust}: the chunk you stand in is the trusted place for an hour of your play (the place rule still
 * holds). Lines go to the op who ran it only. The layer's switch puts the debug trusts away ({@link #revert}).
 */
public final class PlacesCommands {
	private PlacesCommands() {
	}

	public static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("01")
				.executes(ctx -> show(ctx, null))
				.then(Commands.literal("trust").executes(PlacesCommands::trust))
				.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player")))))));
	}

	/** The switch went off: every debug trust goes (idempotent, no chunk loads). */
	public static void revert(MinecraftServer server) {
		DirectorData data = DirectorData.get(server);
		if (revert(data.memory())) {
			data.setDirty();
		}
	}

	/** {@link #revert(MinecraftServer)} over this memory; true if a trust went. */
	public static boolean revert(DirectorMemory memory) {
		boolean changed = false;
		for (PacingMemory pacing : memory.players().values()) {
			if (pacing.places().trust().isPresent()) {
				pacing.places().setTrust(null);
				changed = true;
			}
		}
		return changed;
	}

	private static int show(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = CommandTarget.player(source, named);
		if (target.isEmpty()) {
			source.sendFailure(Component.literal("sc 01: nobody takes part right now; name a player"));
			return 0;
		}
		ServerPlayer player = target.get();
		MinecraftServer server = source.getServer();
		UUID uuid = player.getUUID();
		Optional<PacingMemory> pacing = DirectorData.get(server).memory().find(uuid);
		if (pacing.isEmpty()) {
			source.sendSuccess(() -> Component.literal("sc 01: " + player.getName().getString() + " has no pacing yet"), false);
			return 1;
		}
		long play = GameClock.playTicks(server, uuid);
		long hour = Math.max(1, ModConfig.realTicks(3600));
		boolean applies = SelfCreated.enabled() && HerobrineState.get(server).stage().atLeast(Stage.TRACES) && play >= hour;
		List<String> lines = Places.describe(player.getName().getString(), pacing.get().places(), player.level().dimension(), player.blockPosition(),
				play, GameClock.dayTicks(server), hour, applies, Sc01Config.get());
		if (!SelfCreated.enabled()) {
			source.sendSuccess(() -> Component.literal("sc 01: the layer is switched off (sections.sc.enabled): the factor is 1"), false);
		}
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	private static int trust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		DirectorData data = DirectorData.get(server);
		PacingMemory pacing = data.memory().pacing(player.getUUID(), GameClock.dayTicks(server));
		long play = GameClock.playTicks(server, player.getUUID());
		PlaceMemory.Chunk chunk = PlaceMemory.Chunk.of(player.level().dimension(), player.blockPosition());
		pacing.places().setTrust(new PlaceMemory.Trust(chunk, play + Math.max(1, ModConfig.realTicks(3600))));
		data.setDirty();
		String line = String.format(Locale.ROOT, "sc 01: the chunk you stand in, [%d, %d], is your trusted place for an hour of your play "
				+ "(unless a place you remember is within %d blocks of its centre)", chunk.x(), chunk.z(), Sc01Config.get().radius);
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}
}
