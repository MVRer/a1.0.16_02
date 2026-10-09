package com.forzacode.a1016_02.lore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.lore.TellingData.WrittenBook;
import com.forzacode.a1016_02.lore.TellingData.WrittenSign;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /a1016 lore telling | tell <text> | listcause <cause> | ending finishf10|placef20|stoptocross}: debug for
 * the telling. The cards fire with core's {@code /a1016 fire stop_sign | blank_sign | place_not_found}.
 */
final class TellingCommands {
	private static final int SHOWN = 12;

	private TellingCommands() {
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static String at(GlobalPos pos) {
		BlockPos p = pos.pos();
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	/** {@code /a1016 lore telling}: the count, the remembered signs and books, "Stop.", F04 and the burnt list. */
	static int telling(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState state = HerobrineState.get(server);
		TellingData data = TellingData.get(server);
		say(ctx, String.format(Locale.ROOT, "[a1016] telling: count %d (flag %d), tellingStarted %s, stage %s, first telling %s", data.count(),
				Telling.countFromFlags(state), state.tellingStarted(), state.stage(), data.told() ? "day " + data.firstTellingDay() : "never"));
		say(ctx, String.format(Locale.ROOT, "[a1016] stopFired %s | Stop. candidate %s | Stop. sign %s | sign edits %s", state.stopFired(),
				data.stopCandidate().map(TellingCommands::at).orElse("none"), data.stopSign().map(TellingCommands::at).orElse("none"),
				SignEdits.available() ? "available" : "waiting for core's TraceService.editSign (stop/blank cards skip)"));
		List<WrittenSign> signs = data.signs();
		say(ctx, "[a1016] signs written (" + signs.size() + "):");
		for (WrittenSign sign : signs.subList(Math.max(0, signs.size() - SHOWN), signs.size())) {
			say(ctx, String.format(Locale.ROOT, "  %s | %s%s%s%s | day %d | \"%s\"", at(sign.pos()), sign.aboutHim() ? "about him" : "other",
					sign.namesHim() ? ", names him" : "", sign.afterTelling() ? ", after telling" : "", sign.blanked() ? ", BLANKED" : "", sign.day(),
					shorten(sign.text())));
		}
		List<WrittenBook> books = data.books();
		say(ctx, "[a1016] books about him (" + books.size() + "):");
		for (WrittenBook book : books.subList(Math.max(0, books.size() - SHOWN), books.size())) {
			say(ctx, String.format(Locale.ROOT, "  %s | %s%s | day %d", book.id().substring(0, 8), book.signed() ? "signed" : "unsigned",
					book.namesHim() ? ", names him" : "", book.day()));
		}
		TellingData.Burned burned = data.burned();
		say(ctx, String.format(Locale.ROOT, "[a1016] visited %s | not found %s | list burnt %d (pyramids owed %d, raised %d%s) | list cause %s | blanks waiting %d",
				data.visited(), data.notFound().orElse("not yet"), burned.lists(), burned.owed(), burned.raised(),
				burned.ocean().map(o -> ", ocean " + at(o)).orElse(""), LiveBooks.cause(server).orElse("none"), data.pendingBlanks().size()));
		return data.count();
	}

	/**
	 * {@code /a1016 lore tell <text>}: as if the player had just written {@code text} on a sign at their position
	 * (or on the sign they look at). Nothing is placed or written; detection, TELLING, the count and the records run.
	 */
	static int tell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		String text = StringArgumentType.getString(ctx, "text");
		BlockPos pos = lookedAt(player, 6).filter(p -> player.level().getBlockEntity(p) instanceof SignBlockEntity).orElse(player.blockPosition());
		HerobrineState state = HerobrineState.get(server);
		TellingData data = TellingData.get(server);
		Telling.Told told = Telling.writeSign(player, player.level(), pos, text, state, data, Telling.live(server));
		say(ctx, String.format(Locale.ROOT, "[a1016] sign at %d %d %d: %s (names him %s, near his traces %s); count %d, stage %s%s", pos.getX(),
				pos.getY(), pos.getZ(), told.told() ? "TELLING fired" : told.recorded() ? "recorded for blank sign, not telling" : "nothing",
				NameMatcher.namesHim(text), Telling.live(server).near(GlobalPos.of(player.level().dimension(), pos),
						com.forzacode.a1016_02.core.ModConfig.pacing().tellingRadius),
				data.count(), state.stage(), data.stopCandidate().map(c -> c.pos().equals(pos) ? "; it is the Stop. candidate" : "").orElse("")));
		return told.told() ? 1 : 0;
	}

	/** {@code /a1016 lore listcause <cause>}: what F23 would read with this cause. Records nothing. */
	static int listCause(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		String cause = StringArgumentType.getString(ctx, "cause");
		Optional<Fragment> f23 = FragmentData.get("F23");
		if (f23.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] F23 did not load"));
			return 0;
		}
		String name = LiveBooks.name(server, ctx.getSource().getPlayer());
		say(ctx, "[a1016] F23 would read (preview, nothing recorded):");
		for (String page : LiveBooks.pages(f23.get(), name, Optional.of(cause), false)) {
			for (String line : page.split("\n", -1)) {
				say(ctx, "  " + line);
			}
		}
		return 1;
	}

	/** {@code /a1016 lore ending finishf10}: Ending B's last F10 line, now (sets lore:f10_finished for good). */
	static int finishF10(CommandContext<CommandSourceStack> ctx) {
		int updated = LiveBooks.finishF10(ctx.getSource().getServer());
		say(ctx, "[a1016] F10 now ends with its last line (" + updated + " copies updated now; others when opened). Flag "
				+ LiveBooks.F10_FINISHED + " is set.");
		return 1;
	}

	/** {@code /a1016 lore ending placef20}: F20 under F10 (forced: no view check). */
	static int placeF20(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		boolean placed = LoreApi.placeF20(server, com.forzacode.a1016_02.core.Services.traces().forced());
		GlobalPos at = HerobrineState.get(server).fragmentsPlaced().get("F20");
		say(ctx, placed && at != null ? "[a1016] F20 is under F10 at " + at(at) : "[a1016] could not place F20 (is F10 placed? its chunk loading?)");
		return placed ? 1 : 0;
	}

	/** {@code /a1016 lore ending stoptocross}: the "Stop." sign moves in front of the block you look at (the cross's bottom). */
	static int stopToCross(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Optional<BlockPos> cross = lookedAt(player, 8);
		if (cross.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] look at the cross's bottom block"));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		boolean moved = LoreApi.moveStopSignToCross(server, GlobalPos.of(player.level().dimension(), cross.get()),
				com.forzacode.a1016_02.core.Services.traces().forced(), SignEdits.editor(com.forzacode.a1016_02.core.Services.traces().forced()));
		say(ctx, moved ? "[a1016] the Stop. sign now stands at " + LoreApi.stopSign(server).map(TellingCommands::at).orElse("?")
				: "[a1016] could not move the Stop. sign (none yet? no free side? other dimension?)");
		return moved ? 1 : 0;
	}

	private static Optional<BlockPos> lookedAt(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getBlockPos()) : Optional.empty();
	}

	private static String shorten(String text) {
		return text.length() > 40 ? text.substring(0, 37) + "..." : text;
	}
}
