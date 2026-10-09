package com.forzacode.a1016_02.lore;

import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** {@code /a1016 lore list | place <id> | give <id> | read <id>}: debug for the fragments. */
final class LoreCommands {
	private LoreCommands() {
	}

	static void register(FragmentEngine engine) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("lore")
				.then(Commands.literal("list").executes(LoreCommands::list))
				.then(Commands.literal("place").then(idArgument().executes(ctx -> place(ctx, engine))))
				.then(Commands.literal("give").then(idArgument().executes(LoreCommands::give)))
				.then(Commands.literal("read").then(idArgument().executes(LoreCommands::read)))
				.then(Commands.literal("telling").executes(TellingCommands::telling))
				.then(Commands.literal("tell").then(Commands.argument("text", StringArgumentType.greedyString()).executes(TellingCommands::tell)))
				.then(Commands.literal("listcause").then(Commands.argument("cause", StringArgumentType.greedyString())
						.executes(TellingCommands::listCause)))
				.then(Commands.literal("ending")
						.then(Commands.literal("finishf10").executes(TellingCommands::finishF10))
						.then(Commands.literal("placef20").executes(TellingCommands::placeF20))
						.then(Commands.literal("stoptocross").executes(TellingCommands::stopToCross)))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
		return Commands.argument("id", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FragmentData.ids(), builder));
	}

	private static Optional<Fragment> fragment(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id").toUpperCase(java.util.Locale.ROOT);
		Optional<Fragment> fragment = FragmentData.get(id);
		if (fragment.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] no fragment " + id + " (loaded: " + FragmentData.ids().size() + ")"));
		}
		return fragment;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState state = HerobrineState.get(server);
		say(ctx, "[a1016] fragments (stage " + state.stage() + ", read " + state.fragmentsRead().size() + "):");
		boolean waitsForCamp = Placers.waitsForStillBurning(state.hasFlag(Placers.STILL_BURNING), Services.sites().all(),
				server.overworld().dimension());
		for (Fragment fragment : FragmentData.all()) {
			String id = fragment.id();
			GlobalPos placed = state.fragmentsPlaced().get(id);
			boolean enabled = Services.fragments().isEnabled(server, id);
			boolean waiting = enabled && waitsForCamp && fragment.placement().rule().equals("emptied_house");
			String line = String.format("%s %s | %s | from %s, %s | %s | %s", id, fragment.name(),
					enabled ? "on" : "off",
					fragment.stage().name().toLowerCase(java.util.Locale.ROOT), fragment.placement().rule(),
					placed == null ? (waiting ? "waiting for still burning" : "not placed") : "placed " + placed.pos().toShortString()
							+ (placed.dimension().equals(server.overworld().dimension()) ? "" : " " + placed.dimension().identifier()),
					state.fragmentsRead().contains(id) ? "READ" : "unread");
			say(ctx, line);
		}
		return FragmentData.all().size();
	}

	private static int place(CommandContext<CommandSourceStack> ctx, FragmentEngine engine) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		if (fragment.isEmpty()) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		LoreConfig config = LoreConfig.get();
		Optional<Placing.Result> result = engine.placeNear(player.level(), fragment.get(), player.blockPosition(), config.debugMinDistance,
				config.debugMaxDistance, 8, true, Optional.of(player));
		if (result.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] could not place " + fragment.get().id()
					+ " out of view right now (far chunks it needs may be loading: try again in a few seconds; or turn around)"));
			return 0;
		}
		BlockPos pos = result.get().pos;
		say(ctx, "[a1016] placed " + fragment.get().id() + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ result.get().site.map(site -> " (site " + site.type() + " #" + site.id() + ")").orElse(""));
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		if (fragment.isEmpty()) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!fragment.get().isBook() && fragment.get().item().isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] " + fragment.get().id() + " is a " + fragment.get().form().name().toLowerCase(java.util.Locale.ROOT)
					+ ", not an item; use /a1016 lore place " + fragment.get().id()));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		String name = HerobrineState.get(server).subject().map(HerobrineState.Subject::name).orElse(player.getName().getString());
		BlockPos mapTarget = UntouchedGrove.center(server).map(GlobalPos::pos).orElse(player.blockPosition());
		ItemStack stack = FragmentItems.stackFor(fragment.get(), name, player.level(), mapTarget);
		if (!player.getInventory().add(stack)) {
			ctx.getSource().sendFailure(Component.literal("[a1016] inventory full"));
			return 0;
		}
		say(ctx, "[a1016] gave " + fragment.get().id());
		return 1;
	}

	private static int read(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		if (fragment.isEmpty()) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		HerobrineState state = HerobrineState.get(ctx.getSource().getServer());
		boolean first = !state.fragmentsRead().contains(fragment.get().id());
		Services.fragments().markRead(player, fragment.get().id());
		say(ctx, "[a1016] " + fragment.get().id() + (first ? " marked read (FRAGMENT_READ fired)" : " was already read"));
		return 1;
	}
}
