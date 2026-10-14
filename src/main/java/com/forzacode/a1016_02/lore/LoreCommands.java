package com.forzacode.a1016_02.lore;

import java.util.Locale;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /a1016 lore list | place <id> [player] | give <id> [player] | read <id> [player]}: debug for the fragments.
 * A command that acts on a player acts on the named one, else on whoever runs it, else (from the console) on the
 * first participant (D-062.9).
 */
final class LoreCommands {
	static final String PLAYER = "player";

	private LoreCommands() {
	}

	static void register(FragmentEngine engine) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("lore")
				.then(Commands.literal("list").executes(LoreCommands::list))
				.then(Commands.literal("place").then(idArgument().executes(ctx -> place(ctx, engine, false))
						.then(playerArgument().executes(ctx -> place(ctx, engine, true)))))
				.then(Commands.literal("give").then(idArgument().executes(ctx -> give(ctx, false))
						.then(playerArgument().executes(ctx -> give(ctx, true)))))
				.then(Commands.literal("read").then(idArgument().executes(ctx -> read(ctx, false))
						.then(playerArgument().executes(ctx -> read(ctx, true)))))
				.then(Commands.literal("telling").executes(TellingCommands::telling))
				.then(Commands.literal("tell").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> TellingCommands.tell(ctx, false))))
				.then(Commands.literal("tellas").then(playerArgument().then(Commands.argument("text", StringArgumentType.greedyString())
						.executes(ctx -> TellingCommands.tell(ctx, true)))))
				.then(Commands.literal("listcause").then(Commands.argument("cause", StringArgumentType.greedyString())
						.executes(TellingCommands::listCause)))
				.then(Commands.literal("ending")
						.then(Commands.literal("finishf10").executes(ctx -> TellingCommands.finishF10(ctx, false))
								.then(playerArgument().executes(ctx -> TellingCommands.finishF10(ctx, true))))
						.then(Commands.literal("placef20").executes(TellingCommands::placeF20))
						.then(Commands.literal("stoptocross").executes(ctx -> TellingCommands.stopToCross(ctx, false))
								.then(playerArgument().executes(ctx -> TellingCommands.stopToCross(ctx, true)))))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
		return Commands.argument("id", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FragmentData.ids(), builder));
	}

	static RequiredArgumentBuilder<CommandSourceStack, ?> playerArgument() {
		return Commands.argument(PLAYER, EntityArgument.player());
	}

	/**
	 * The player a command acts on (D-062.9): the named one ({@code named}), else whoever runs it, else (the console)
	 * the first participant in join order. Empty, with a message, when there is nobody.
	 */
	static Optional<ServerPlayer> target(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		Optional<ServerPlayer> player = find(ctx, named);
		if (player.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] nobody takes part right now: name a player"));
		}
		return player;
	}

	/** {@link #target} without the message, for commands that also work for nobody. */
	static Optional<ServerPlayer> find(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		if (named) {
			return Optional.of(EntityArgument.getPlayer(ctx, PLAYER));
		}
		ServerPlayer runner = ctx.getSource().getPlayer();
		return runner != null ? Optional.of(runner) : Services.watch().participants(ctx.getSource().getServer()).stream().findFirst();
	}

	private static Optional<Fragment> fragment(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id").toUpperCase(Locale.ROOT);
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
		Homes homes = Homes.live(server);
		say(ctx, "[a1016] fragments (stage " + state.stage() + ", read " + state.fragmentsRead().size() + "):");
		say(ctx, "[a1016] story owner (" + Homes.FIRST_BLOCKS + "): " + homes.storedOwner().map(uuid -> homes.name(uuid).orElse(uuid.toString()))
				.orElse("not chosen yet") + "; home " + homes.storedOwner().flatMap(homes::base).map(p -> p.pos().toShortString()
				+ " " + p.dimension().identifier()).orElse("unknown"));
		boolean waitsForCamp = Placers.waitsForStillBurning(state.hasFlag(Placers.STILL_BURNING), Services.sites().all(),
				server.overworld().dimension());
		for (Fragment fragment : FragmentData.all()) {
			String id = fragment.id();
			GlobalPos placed = state.fragmentsPlaced().get(id);
			boolean enabled = Services.fragments().isEnabled(server, id);
			boolean waiting = enabled && waitsForCamp && fragment.placement().rule().equals("emptied_house");
			String line = String.format(Locale.ROOT, "%s %s | %s | from %s, %s | %s | %s", id, fragment.name(),
					enabled ? "on" : "off",
					fragment.stage().name().toLowerCase(Locale.ROOT), fragment.placement().rule(),
					placed == null ? (waiting ? "waiting for still burning" : "not placed") : "placed " + placed.pos().toShortString()
							+ (placed.dimension().equals(server.overworld().dimension()) ? "" : " " + placed.dimension().identifier()),
					state.fragmentsRead().contains(id) ? "READ" : "unread");
			say(ctx, line);
		}
		return FragmentData.all().size();
	}

	/** {@code place <id> [player]}: near that player, out of view; rules tied to a place (the story's first blocks) keep it. */
	private static int place(CommandContext<CommandSourceStack> ctx, FragmentEngine engine, boolean named) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		Optional<ServerPlayer> target = target(ctx, named);
		if (fragment.isEmpty() || target.isEmpty()) {
			return 0;
		}
		ServerPlayer player = target.get();
		LoreConfig config = LoreConfig.get();
		Optional<Placing.Result> result = engine.placeNear(player.level(), fragment.get(), player.blockPosition(), config.debugMinDistance,
				config.debugMaxDistance, 8, true, Optional.of(player));
		if (result.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] could not place " + fragment.get().id()
					+ " out of view right now (far chunks it needs may be loading: try again in a few seconds; or turn around)"));
			return 0;
		}
		BlockPos pos = result.get().pos;
		say(ctx, "[a1016] placed " + fragment.get().id() + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " near "
				+ player.getName().getString() + result.get().site.map(site -> " (site " + site.type() + " #" + site.id() + ")").orElse(""));
		return 1;
	}

	/** {@code give <id> [player]}: into that player's inventory, the list as it reads now, "you" that player. */
	private static int give(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		Optional<ServerPlayer> target = target(ctx, named);
		if (fragment.isEmpty() || target.isEmpty()) {
			return 0;
		}
		ServerPlayer player = target.get();
		if (!fragment.get().isBook() && fragment.get().item().isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] " + fragment.get().id() + " is a " + fragment.get().form().name().toLowerCase(Locale.ROOT)
					+ ", not an item; use /a1016 lore place " + fragment.get().id()));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		BlockPos mapTarget = UntouchedGrove.center(server).map(GlobalPos::pos).orElse(player.blockPosition());
		ItemStack stack = FragmentItems.stackFor(fragment.get(), player.getName().getString(),
				LiveBooks.lines(HerobrineState.get(server), TellingData.get(server)), player.level(), mapTarget);
		if (!player.getInventory().add(stack)) {
			ctx.getSource().sendFailure(Component.literal("[a1016] inventory full"));
			return 0;
		}
		say(ctx, "[a1016] gave " + fragment.get().id() + " to " + player.getName().getString());
		return 1;
	}

	/** {@code read <id> [player]}: as if that player read it (anyone's read counts; FRAGMENT_READ the first time). */
	private static int read(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		Optional<Fragment> fragment = fragment(ctx);
		Optional<ServerPlayer> target = target(ctx, named);
		if (fragment.isEmpty() || target.isEmpty()) {
			return 0;
		}
		ServerPlayer player = target.get();
		HerobrineState state = HerobrineState.get(ctx.getSource().getServer());
		boolean first = !state.fragmentsRead().contains(fragment.get().id());
		Services.fragments().markRead(player, fragment.get().id());
		say(ctx, "[a1016] " + fragment.get().id() + (first ? " marked read by " + player.getName().getString() + " (FRAGMENT_READ fired)"
				: " was already read"));
		return 1;
	}
}
