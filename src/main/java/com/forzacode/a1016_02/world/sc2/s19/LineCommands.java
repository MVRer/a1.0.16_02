package com.forzacode.a1016_02.world.sc2.s19;

import java.util.List;
import java.util.Locale;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /a1016 sc 19} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it: the four
 * segments' ends in this world (where they are whether or not their land is made yet), each one's cut and openings,
 * whether it is known (some of it generated), and whether new land gets them now.
 */
final class LineCommands {
	private LineCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("19").executes(LineCommands::ends))));
	}

	private static int ends(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		ServerLevel overworld = server.overworld();
		Snapshot snapshot = Snapshot.current();
		if (snapshot == null) {
			say(ctx, "19: no numbers for worldgen yet");
			return 0;
		}
		String refused = LineFeature.refusal(snapshot);
		List<LinePlan.Segment> planned = LinePlan.of(snapshot.seed(), snapshot.salt(), Sc19Config.get(), overworld.getMinY(), overworld.getMaxY());
		if (planned.isEmpty()) {
			say(ctx, "19: this level is too short for any");
			return 0;
		}
		// Each kept clear of the places worldgen decides along it (FIX-R3-E): the first ask of a world sites them.
		List<LinePlan.Segment> segments = LineFeature.laidOut(snapshot.seed(), snapshot.salt(), planned, overworld.getMinY(), overworld.getMaxY(),
				Sc19Config.get(), LineFeature.places(snapshot));
		Sc2Data data = RemainsImpl.LIVE.data();
		for (LinePlan.Segment s : segments) {
			BlockPos a = new BlockPos(s.x(s.t0()), s.bottom(), s.z(s.t0()));
			BlockPos b = new BlockPos(s.x(s.t1()), s.top(), s.z(s.t1()));
			boolean known = data.places().stream().anyMatch(k -> k.kind().equals(S19.ID) && k.start().origin().equals(s.start().origin()));
			String cut = s.gapWidth() > 0 ? String.format(Locale.ROOT, "cut %d wide at %d", s.gapWidth(), s.gapStart()) : "no cut";
			say(ctx, String.format(Locale.ROOT, "19 %s: %d %d %d to %d %d %d, %d long, %s, %d openings, %s", s.arm().out.getSerializedName(), a.getX(),
					a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), s.length(), cut, s.holes().size(), known ? "known" : "not made yet"));
		}
		say(ctx, refused == null ? "19: new land gets them" : "19: new land gets none now (" + refused + ")");
		return segments.size();
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
