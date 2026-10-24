package com.forzacode.a1016_02.debug.gallery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Gallery;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /a1016 sc g} (dev only, docs/SELF_CREATED_2.md "The gallery"): {@code sc g} lists the exhibits; {@code sc g <id>}
 * places one 48 blocks ahead of you and lists its cameras; {@code sc g <id> <shot>} puts you (a spectator) at that camera,
 * with its sky and time; {@code sc g <id> f <frames>} sets a time-lapse's camera and starts its staged behaviour (take the
 * screenshots by hand); {@code sc g <id> plan} writes its blueprint renders; {@code sc g all} lays every exhibit out
 * along +X, {@link GalleryRun#SPACING} apart. Literals and argument names pass the spoiler words (D-103).
 */
final class GalleryCommands {
	/** Where each exhibit was last placed (cleared when the server stops: a time-lapse holds its mobs). */
	static final Map<String, Gallery.Placed> PLACED = new ConcurrentHashMap<>();
	private static final int AHEAD = 48;

	private GalleryCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("g").executes(GalleryCommands::list)
				.then(Commands.literal("all").executes(GalleryCommands::all))
				.then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Gallery.all().stream().map(Gallery.Exhibit::id), builder))
						.executes(GalleryCommands::place)
						.then(Commands.literal("plan").executes(GalleryCommands::plan))
						.then(Commands.literal("f").then(Commands.argument("frames", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(placed(ctx).stream()
										.flatMap(p -> p.frames().stream().map(Gallery.Frames::name)), builder))
								.executes(GalleryCommands::frames)))
						.then(Commands.argument("shot", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(placed(ctx).stream()
										.flatMap(p -> p.views().stream().map(Gallery.View::name)), builder))
								.executes(GalleryCommands::shot))))));
	}

	private static Optional<Gallery.Placed> placed(CommandContext<CommandSourceStack> ctx) {
		return Optional.ofNullable(PLACED.get(StringArgumentType.getString(ctx, "id")));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendFailure(Component.literal(line));
		return 0;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		List<Gallery.Exhibit> all = Gallery.all();
		say(ctx, "[a1016] g: " + all.size() + " exhibits; /a1016 sc g <id> places one 48 ahead");
		for (Gallery.Exhibit exhibit : all) {
			say(ctx, "  " + exhibit.id() + (PLACED.containsKey(exhibit.id()) ? " (placed)" : ""));
		}
		return all.size();
	}

	/** 48 blocks ahead of the source, on the ground there. */
	private static BlockPos ahead(CommandSourceStack source, int extraX) {
		ServerLevel level = source.getLevel();
		Vec3 at = source.getPosition();
		float yaw = source.getRotation().y;
		double x = at.x - Mth.sin(yaw * Mth.DEG_TO_RAD) * AHEAD + extraX;
		double z = at.z + Mth.cos(yaw * Mth.DEG_TO_RAD) * AHEAD;
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		return new BlockPos(bx, Gallery.ground(level, bx, bz), bz);
	}

	private static int place(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		Optional<Gallery.Exhibit> exhibit = Gallery.get(id);
		if (exhibit.isEmpty()) {
			return fail(ctx, "[a1016] g: no exhibit " + id);
		}
		place(ctx, exhibit.get(), ahead(ctx.getSource(), 0));
		return 1;
	}

	private static void place(CommandContext<CommandSourceStack> ctx, Gallery.Exhibit exhibit, BlockPos origin) {
		Gallery.Placed placed = GalleryRun.place(ctx.getSource().getLevel(), exhibit, origin);
		PLACED.put(exhibit.id(), placed);
		say(ctx, "[a1016] g: " + exhibit.id() + " at " + origin.toShortString() + "; cameras: "
				+ String.join(", ", placed.views().stream().map(Gallery.View::name).toList())
				+ (placed.frames().isEmpty() ? "" : "; f: " + String.join(", ", placed.frames().stream().map(Gallery.Frames::name).toList())));
	}

	private static int all(CommandContext<CommandSourceStack> ctx) {
		List<Gallery.Exhibit> all = Gallery.all();
		for (int i = 0; i < all.size(); i++) {
			place(ctx, all.get(i), ahead(ctx.getSource(), i * GalleryRun.SPACING));
		}
		return all.size();
	}

	private static int shot(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		String name = StringArgumentType.getString(ctx, "shot");
		Optional<Gallery.View> view = placed(ctx).stream().flatMap(p -> p.views().stream()).filter(v -> v.name().equals(name)).findFirst();
		if (view.isEmpty()) {
			return fail(ctx, "[a1016] g: " + id + " has no camera " + name + (PLACED.containsKey(id) ? "" : " (place it first: /a1016 sc g " + id + ")"));
		}
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return fail(ctx, "[a1016] g: only a player can be the camera");
		}
		GalleryRun.stage(player, view.get());
		say(ctx, "[a1016] g: " + id + " " + name + " (F1 hides the hud, F2 takes the picture)");
		return 1;
	}

	private static int frames(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		String name = StringArgumentType.getString(ctx, "frames");
		Optional<Gallery.Frames> frames = placed(ctx).stream().flatMap(p -> p.frames().stream()).filter(f -> f.name().equals(name)).findFirst();
		if (frames.isEmpty()) {
			return fail(ctx, "[a1016] g: " + id + " has no frames " + name);
		}
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			GalleryRun.stage(player, frames.get().camera());
		}
		frames.get().start().accept(ctx.getSource().getLevel());
		say(ctx, "[a1016] g: " + id + " f " + name + " started (" + frames.get().count() + " frames every " + frames.get().everyTicks() + " ticks)");
		return 1;
	}

	private static int plan(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		Gallery.Placed placed = PLACED.get(id);
		if (placed == null) {
			return fail(ctx, "[a1016] g: place " + id + " first");
		}
		try {
			Path dir = GalleryRun.dir(id);
			List<Path> files = Blueprint.write(ctx.getSource().getLevel(), placed.box(), dir);
			List<GalleryRun.Entry> entries = new ArrayList<>();
			String box = Blueprint.describe(placed.box());
			for (Path file : files) {
				entries.add(GalleryRun.blueprintEntry(id, GalleryRun.relative(file), box));
			}
			if (!entries.isEmpty()) {
				GalleryRun.writeIndex(entries, "blueprints by hand");
			}
			say(ctx, "[a1016] g: " + files.size() + " renders in " + dir);
			return files.size();
		} catch (IOException e) {
			return fail(ctx, "[a1016] g: could not write: " + e.getMessage());
		}
	}
}
