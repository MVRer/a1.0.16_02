package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 dig network grow <nights> [player] | network info [player] | network reveal [player] | network chest [player]
 * | tunnel <card> [player] | steps [player]}. The network is the one under that player's base: the named player, else the player
 * running the command, else (the console) the first participant (D-062.9); {@code info} lists every network. Debug
 * only: the growth and the tunnels still respect the out-of-view rule. Numbers use {@link Locale#ROOT} (D-031).
 */
final class DigCommands {
	static final List<String> TUNNELS = List.of("plain", ScarCards.TunnelThatGrows.ID, ScarCards.TunnelIntoMine.ID);
	private static final String PLAYER = "player";

	private DigCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("dig")
				.then(Commands.literal("network")
						.then(Commands.literal("grow")
								.then(Commands.argument("nights", IntegerArgumentType.integer(1, 100)).executes(ctx -> grow(ctx, false))
										.then(named().executes(ctx -> grow(ctx, true)))))
						.then(Commands.literal("info").executes(ctx -> info(ctx, false)).then(named().executes(ctx -> info(ctx, true))))
						.then(Commands.literal("reveal").executes(ctx -> reveal(ctx, false)).then(named().executes(ctx -> reveal(ctx, true))))
						.then(Commands.literal("chest").executes(ctx -> chest(ctx, false)).then(named().executes(ctx -> chest(ctx, true)))))
				.then(Commands.literal("tunnel")
						.then(Commands.argument("card", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TUNNELS, builder))
								.executes(ctx -> tunnel(ctx, false))
								.then(named().executes(ctx -> tunnel(ctx, true)))))
				.then(Commands.literal("steps").executes(ctx -> steps(ctx, false)).then(named().executes(ctx -> steps(ctx, true))))));
	}

	/** {@code /a1016 dig steps [player]}: that player's tunnel ladder (D-072) and whether the detector sees a run now. */
	private static int steps(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		ServerPlayer player = player(ctx, named);
		if (player == null) {
			return fail(ctx, "[a1016] dig: no player");
		}
		Ladder.debugLines(player, Ladder.Env.live(ctx.getSource().getServer())).forEach(line -> say(ctx, line));
		return 1;
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> named() {
		return Commands.argument(PLAYER, EntityArgument.player());
	}

	/** D-062.9: the named player, else the player running the command, else (the console) the first participant; or null. */
	private static @Nullable ServerPlayer player(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		if (named) {
			return EntityArgument.getPlayer(ctx, PLAYER);
		}
		ServerPlayer self = ctx.getSource().getPlayer();
		return self != null ? self : Services.watch().participants(ctx.getSource().getServer()).stream().findFirst().orElse(null);
	}

	/** The network under this player's base (shared with friends nearby), or null. */
	private static @Nullable Network networkOf(MinecraftServer server, @Nullable ServerPlayer player) {
		if (player == null) {
			return null;
		}
		Optional<GlobalPos> base = Services.watch().base(player);
		return base.map(b -> DigData.get(server).networkFor(b.dimension(), b.pos(), DigConfig.get().networkShareDistance)).orElse(null);
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendFailure(Component.literal(line));
		return 0;
	}

	private static int grow(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		MinecraftServer server = ctx.getSource().getServer();
		ServerPlayer player = player(ctx, named);
		if (player == null) {
			return fail(ctx, "[a1016] dig: no player");
		}
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty()) {
			return fail(ctx, "[a1016] dig: no base yet (sleep in a bed or place a block)");
		}
		ServerLevel level = server.getLevel(base.get().dimension());
		if (level == null || !level.isLoaded(base.get().pos())) {
			return fail(ctx, "[a1016] dig: the base is not loaded");
		}
		DigData data = DigData.get(server);
		DigConfig config = DigConfig.get();
		long night = UnderYou.nightIndex(server);
		Network net = UnderYou.active(data, player.getUUID(), base.get(), night, config, true);
		if (net.founder().map(player.getUUID()::equals).orElse(false)) {
			NetworkGrower.refreshBed(level, net, base.get().pos());
		}
		if (net.lastNight == Long.MIN_VALUE) {
			net.lastNight = night;
		}
		int nights = IntegerArgumentType.getInteger(ctx, "nights");
		RandomSource random = RandomSource.create();
		List<BlockPos> players = UnderYou.positions(Bases.live(server), level, player);
		int carved = 0;
		for (int i = 0; i < nights; i++) {
			net.nights++;
			net.budget += config.networkBlocksPerNight;
			net.shaftStuckNight = Long.MIN_VALUE;
			net.restoredNight = Long.MIN_VALUE;
			NetworkGrower.Ctx c = UnderYou.ctx(level, net, data, players, night, random);
			if (!net.anchors.isEmpty()) {
				NetworkGrower.refreshTargets(c);
			}
			carved += UnderYou.growNow(c, data, 10_000, true);
		}
		data.changed();
		int left = net.budget;
		int total = carved;
		say(ctx, String.format(Locale.ROOT, "[a1016] dig: +%d nights, carved %d steps (%d anchors now); %d steps waiting (in view or nowhere to go)", nights, total,
				net.anchors.size(), left));
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		MinecraftServer server = ctx.getSource().getServer();
		DigData data = DigData.get(server);
		DigConfig config = DigConfig.get();
		HerobrineState state = HerobrineState.get(server);
		ServerPlayer player = player(ctx, named);
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "[a1016] dig: %d network(s), %d in use of at most %d; bases within %d share one", data.networks.size(),
				UnderYou.inUse(data, config), config.networkMaxPerWorld, config.networkShareDistance));
		for (int n = 0; n < data.networks.size(); n++) {
			Network each = data.networks.get(n);
			lines.add(String.format(Locale.ROOT, "  #%d under %s (%s), %s: %d anchors, nights=%d%s", n + 1, each.base.toShortString(), each.dimension.identifier(),
					each.owners.isEmpty() ? "no owner yet (a save from before multiplayer)" : "owners " + each.owners.stream()
							.map(uuid -> state.player(uuid).map(HerobrineState.Subject::name).orElse(uuid.toString())).toList(), each.anchors.size(), each.nights,
					UnderYou.inUse(data, each, config) ? "" : " (nobody's base now)"));
		}
		Network mine = networkOf(server, player);
		if (mine == null) {
			Optional<DigData.BaseSeen> seen = player == null ? Optional.empty() : data.baseSeen(player.getUUID());
			lines.add("[a1016] dig: no network under " + (player == null ? "anyone's base" : player.getName().getString() + "'s base") + " yet"
					+ seen.map(s -> " (base " + s.base().pos().toShortString() + " seen on night " + s.night() + (s.playedThere() ? "" : ", not at home there yet")
							+ ", starts after " + config.networkStartAfterNights + " nights in Traces)").orElse(""));
		} else {
			Network net = mine;
			ServerLevel level = server.getLevel(net.dimension);
			lines.add(String.format(Locale.ROOT, "[a1016] dig: %s's network %d/%d under base %s (%s): %d anchors, %d cells, nights=%d budget=%d",
					player.getName().getString(), data.networks.indexOf(net) + 1, data.networks.size(), net.base.toShortString(), net.dimension.identifier(),
					net.anchors.size(), net.cells.size(), net.nights, net.budget));
			net.bounds().ifPresent(b -> lines.add(String.format(Locale.ROOT, "depth: corridors at y=%d (%d below the base); extent x %d..%d, y %d..%d, z %d..%d",
					net.depth, net.base.getY() - net.depth, b[0].getX(), b[1].getX(), b[0].getY(), b[1].getY(), b[0].getZ(), b[1].getZ())));
			lines.add("shaft: " + shaftLine(net, config));
			lines.add("chest: " + (net.chest != null ? "at " + net.chest.toShortString() : net.alcove != null
					? "dead end at " + net.alcove.toShortString() + ", no chest yet" : "no dead end yet")
					+ "; " + stacksLine(server, net));
			if (level != null) {
				lines.add("nearest player dig: " + nearestDigLine(level, net, config));
			}
		}
		GrowingTunnel growing = data.growing;
		lines.add(growing == null ? "tunnel that grows: none" : String.format(Locale.ROOT, "tunnel that grows: length %d, end %s, %s%s, %.0f blocks from the base; %s",
				growing.length(), growing.end().toShortString(), growing.complete ? "complete" : "growing", growing.visited ? ", visited" : "",
				growing.distanceToBase(growing.end()), growing.site().map(site -> String.format(Locale.ROOT, "TUNNEL_END site #%d at %s, size %d%s", site.id(),
						site.pos().toShortString(), site.size(), site.claimedBy().map(id -> ", claimed by " + id).orElse(""))).orElse("no site until the first visit")));
		lines.add("card tunnels: " + data.tunnels.size() + ", explored points: " + data.explored.values().stream().mapToInt(PosSet::size).sum()
				+ ", planted saplings: " + data.planted.values().stream().mapToInt(PosSet::size).sum());
		lines.forEach(line -> say(ctx, line));
		return 1;
	}

	private static String stacksLine(MinecraftServer server, Network net) {
		DigConfig config = DigConfig.get();
		return String.format(Locale.ROOT, "stacks moved in %d, taken into the ledger %d, restored from it %d, still waiting %d (%d per night)",
				net.stacksMoved, net.stacksLedgered, net.stacksRestored, NetworkChest.waiting(server, net, config).size(),
				config.networkStacksRestoredPerNight);
	}

	private static int chest(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		MinecraftServer server = ctx.getSource().getServer();
		chestLines(server, networkOf(server, player(ctx, named))).forEach(line -> say(ctx, line));
		return 1;
	}

	/** Where the network chest is and what is in it (never loads a chunk), then the stack counts. */
	static List<String> chestLines(MinecraftServer server, @Nullable Network net) {
		if (net == null) {
			return List.of("[a1016] dig: no network yet");
		}
		List<String> lines = new ArrayList<>();
		BlockPos chest = net.chest;
		ServerLevel level = server.getLevel(net.dimension);
		if (chest == null) {
			lines.add("[a1016] dig: no network chest yet (" + (net.alcove != null ? "dead end at " + net.alcove.toShortString() : "no dead end yet") + ")");
		} else {
			String at = String.format(Locale.ROOT, "[a1016] dig: network chest at %d %d %d (%s)", chest.getX(), chest.getY(), chest.getZ(),
					net.dimension.identifier());
			if (level == null || !level.isLoaded(chest)) {
				lines.add(at + ", not loaded: contents unknown");
			} else if (!(level.getBlockEntity(chest) instanceof Container container)) {
				lines.add(at + ", but there is no chest there now (" + level.getBlockState(chest).getBlock().getName().getString() + ")");
			} else {
				List<String> items = new ArrayList<>();
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					ItemStack stack = container.getItem(slot);
					if (!stack.isEmpty()) {
						items.add(String.format(Locale.ROOT, "  slot %d: %d x %s", slot, stack.getCount(), BuiltInRegistries.ITEM.getKey(stack.getItem())));
					}
				}
				lines.add(String.format(Locale.ROOT, "%s: %d of %d slots used%s", at, items.size(), container.getContainerSize(),
						items.isEmpty() ? ", empty" : ""));
				lines.addAll(items);
			}
		}
		lines.add(stacksLine(server, net));
		return lines;
	}

	private static String shaftLine(Network net, DigConfig config) {
		if (net.bedHead == null) {
			return "no bed at the base";
		}
		String bed = " (bed " + net.bedHead.toShortString() + ")";
		if (net.shaftDone) {
			return "done, top cell " + net.shaftTop().map(BlockPos::toShortString).orElse("?") + bed;
		}
		if (net.shaftFoot != null) {
			return "climbing, top cell " + net.shaftTop().map(BlockPos::toShortString).orElse("?") + bed;
		}
		return (net.nights < config.networkShaftAfterNights ? "starts after night " + config.networkShaftAfterNights : "corridor on its way") + bed;
	}

	private static String nearestDigLine(ServerLevel level, Network net, DigConfig config) {
		List<BlockPos> digs = Services.watch().dugNear(level, net.base, config.networkRadius + 16);
		int best = Integer.MAX_VALUE;
		BlockPos bestDig = null;
		for (BlockPos dig : digs) {
			for (long packed : net.cells) {
				int d = Math.max(Math.abs(BlockPos.getX(packed) - dig.getX()),
						Math.max(Math.abs(BlockPos.getY(packed) - dig.getY()), Math.abs(BlockPos.getZ(packed) - dig.getZ())));
				if (d < best) {
					best = d;
					bestDig = dig;
				}
			}
		}
		if (bestDig == null) {
			return "none within " + (config.networkRadius + 16) + " blocks of the base";
		}
		return String.format(Locale.ROOT, "%d blocks (%d solid between) at %s; it never grows within %d of a dig, breachable once a dig is within %d", best,
				Math.max(0, best - 1), bestDig.toShortString(), ModConfig.pacing().digBelow, ModConfig.pacing().breachWithin);
	}

	private static int reveal(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		MinecraftServer server = ctx.getSource().getServer();
		ServerPlayer player = player(ctx, named);
		Network net = networkOf(server, player);
		if (net == null || net.cells.isEmpty()) {
			return fail(ctx, "[a1016] dig: no network under that base yet");
		}
		List<String> lines = new ArrayList<>();
		if (player != null && player.level().dimension().equals(net.dimension)) {
			BlockPos feet = player.blockPosition();
			BlockPos best = null;
			long bestDist = Long.MAX_VALUE;
			for (long packed : net.cells) {
				BlockPos cell = BlockPos.of(packed);
				long d = DigTicker.horizontalDistSqr(cell, feet) * 64 + Math.abs(cell.getY() - feet.getY());
				if (d < bestDist && cell.getY() < feet.getY()) {
					bestDist = d;
					best = cell;
				}
			}
			if (best != null) {
				lines.add(String.format(Locale.ROOT, "[a1016] dig: dig down at x=%d z=%d: a corridor at y=%d, %d blocks below your feet", best.getX(), best.getZ(),
						best.getY(), feet.getY() - best.getY()));
			}
		}
		lines.add("hub (first corridor): " + net.anchors.getFirst().toShortString());
		net.shaftTop().ifPresent(top -> lines.add("shaft top: " + top.toShortString()
				+ (net.bedHead != null ? " (the bed is at " + net.bedHead.toShortString() + "; one block of floor between)" : "")));
		if (net.alcove != null) {
			lines.add((net.chest != null ? "chest: " : "dead end (no chest yet): ") + net.alcove.toShortString());
		}
		lines.add("corridor ends: " + net.heads.stream().map(head -> head.pos.toShortString()).toList());
		lines.forEach(line -> say(ctx, line));
		return 1;
	}

	private static int tunnel(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		ServerPlayer player = player(ctx, named);
		if (player == null) {
			return fail(ctx, "[a1016] dig: no player");
		}
		ServerLevel level = player.level();
		DigConfig config = DigConfig.get();
		RandomSource random = RandomSource.create();
		String card = StringArgumentType.getString(ctx, "card");
		switch (card) {
			case "plain" -> {
				CardTunnels.Carved carved = CardTunnels.plain(level, player.blockPosition(), config, config.tunnelPlayerClearance, random, Services.traces());
				if (carved == null) {
					return fail(ctx, "[a1016] dig: no out-of-view stone for a tunnel here");
				}
				DigData.get(level.getServer()).addTunnel(new DigData.CardTunnel(level.dimension(), carved.anchors(), "plain", carved.siteId()));
				say(ctx, String.format(Locale.ROOT, "[a1016] dig: tunnel of %d from %s to %s", carved.anchors().size(), carved.opening().toShortString(),
						carved.end().toShortString()));
			}
			case ScarCards.TunnelThatGrows.ID -> {
				GrowingTunnel tunnel = ScarCards.startOrGrow(level, player, random, true, Services.traces());
				if (tunnel == null) {
					GrowingTunnel existing = DigData.get(level.getServer()).growing;
					return fail(ctx, existing != null && existing.complete ? "[a1016] dig: the tunnel that grows is complete (ends at "
							+ existing.end().toShortString() + ")" : "[a1016] dig: nowhere out of view to start or grow it now");
				}
				say(ctx, String.format(Locale.ROOT, "[a1016] dig: tunnel that grows, length %d, from %s to %s, %.0f blocks from the base%s", tunnel.length(),
						tunnel.first.toShortString(), tunnel.end().toShortString(), tunnel.distanceToBase(tunnel.end()),
						tunnel.complete ? " (complete)" : ""));
			}
			case ScarCards.TunnelIntoMine.ID -> {
				CardTunnels.Carved carved = ScarCards.intoMine(level, player, random, Services.traces());
				if (carved == null) {
					return fail(ctx, "[a1016] dig: no dug tunnel " + config.intoMineMinDistance + "+ blocks away to break into (out of view)");
				}
				say(ctx, String.format(Locale.ROOT, "[a1016] dig: broke into the mine at %s, %d long, ends at %s", carved.opening().toShortString(),
						carved.anchors().size(), carved.end().toShortString()));
			}
			default -> {
				return fail(ctx, "[a1016] dig: unknown tunnel " + card + " (" + String.join(", ", TUNNELS) + ")");
			}
		}
		return 1;
	}
}
