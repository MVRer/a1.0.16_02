package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
 * {@code /a1016 dig network grow <nights> | network info | network reveal | network chest | tunnel <card>}. Debug
 * only: the growth and the tunnels still respect the out-of-view rule. Numbers use {@link Locale#ROOT} (D-031).
 */
final class DigCommands {
	static final List<String> TUNNELS = List.of("plain", ScarCards.TunnelThatGrows.ID, ScarCards.TunnelIntoMine.ID);

	private DigCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("dig")
				.then(Commands.literal("network")
						.then(Commands.literal("grow")
								.then(Commands.argument("nights", IntegerArgumentType.integer(1, 100)).executes(DigCommands::grow)))
						.then(Commands.literal("info").executes(DigCommands::info))
						.then(Commands.literal("reveal").executes(DigCommands::reveal))
						.then(Commands.literal("chest").executes(DigCommands::chest)))
				.then(Commands.literal("tunnel")
						.then(Commands.argument("card", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TUNNELS, builder))
								.executes(DigCommands::tunnel)))));
	}

	private static @Nullable ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
		Optional<ServerPlayer> subject = Services.watch().subject(ctx.getSource().getServer());
		return subject.orElse(ctx.getSource().getPlayer());
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendFailure(Component.literal(line));
		return 0;
	}

	private static int grow(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		ServerPlayer player = player(ctx);
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
		Network net = UnderYou.active(data, base.get(), night, config, true);
		NetworkGrower.refreshBed(level, net, base.get().pos());
		if (net.lastNight == Long.MIN_VALUE) {
			net.lastNight = night;
		}
		int nights = IntegerArgumentType.getInteger(ctx, "nights");
		RandomSource random = RandomSource.create();
		BlockPos playerPos = player.level() == level ? player.blockPosition() : null;
		int carved = 0;
		for (int i = 0; i < nights; i++) {
			net.nights++;
			net.budget += config.networkBlocksPerNight;
			net.shaftStuckNight = Long.MIN_VALUE;
			net.restoredNight = Long.MIN_VALUE;
			NetworkGrower.Ctx c = UnderYou.ctx(level, net, data, playerPos, night, random);
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

	private static int info(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		DigData data = DigData.get(server);
		DigConfig config = DigConfig.get();
		List<String> lines = new ArrayList<>();
		Optional<Network> current = data.network();
		if (current.isEmpty()) {
			lines.add("[a1016] dig: no network yet" + (data.baseSeen != null ? " (base " + data.baseSeen.pos().toShortString() + " seen on night "
					+ data.baseSeenNight + ", starts after " + config.networkStartAfterNights + " nights in Traces)" : ""));
		} else {
			Network net = current.get();
			ServerLevel level = server.getLevel(net.dimension);
			lines.add(String.format(Locale.ROOT, "[a1016] dig: network %d/%d under base %s (%s): %d anchors, %d cells, nights=%d budget=%d", data.networks.size(),
					data.networks.size(), net.base.toShortString(), net.dimension.identifier(), net.anchors.size(), net.cells.size(), net.nights, net.budget));
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

	private static int chest(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		chestLines(server, DigData.get(server).network().orElse(null)).forEach(line -> say(ctx, line));
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

	private static int reveal(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		ServerPlayer player = player(ctx);
		Optional<Network> current = DigData.get(server).network();
		if (current.isEmpty() || current.get().cells.isEmpty()) {
			return fail(ctx, "[a1016] dig: no network yet");
		}
		Network net = current.get();
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

	private static int tunnel(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
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
