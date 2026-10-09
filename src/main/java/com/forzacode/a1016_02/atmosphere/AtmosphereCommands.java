package com.forzacode.a1016_02.atmosphere;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /a1016 atmosphere ...}: fog surge|dusk, silence, music on|off, tamper freeze|face|silence|release on the
 * nearest mob, and status (including whether the player stands on a dead mountain). Effects go to the player running
 * the command, else the subject.
 */
final class AtmosphereCommands {
	private static final double TAMPER_RANGE = 32.0;
	private static final int DEFAULT_TAMPER_TICKS = 300;

	private AtmosphereCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("atmosphere")
				.then(Commands.literal("fog")
						.then(Commands.literal("surge")
								.executes(ctx -> surge(ctx, 0.75F, 5))
								.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
										.executes(ctx -> surge(ctx, FloatArgumentType.getFloat(ctx, "strength"), 5))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
												.executes(ctx -> surge(ctx, FloatArgumentType.getFloat(ctx, "strength"), IntegerArgumentType.getInteger(ctx, "seconds"))))))
						.then(Commands.literal("dusk")
								.then(Commands.argument("level", FloatArgumentType.floatArg(0.0F, 1.0F)).executes(AtmosphereCommands::dusk))))
				.then(Commands.literal("silence")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(AtmosphereCommands::silence)))
				.then(Commands.literal("music")
						.then(Commands.literal("on").executes(ctx -> music(ctx, false)))
						.then(Commands.literal("off").executes(ctx -> music(ctx, true))))
				.then(Commands.literal("tamper")
						.then(tamper("freeze"))
						.then(tamper("face"))
						.then(tamper("silence"))
						.then(Commands.literal("release").executes(ctx -> tamperOp(ctx, "release", 0))))
				.then(Commands.literal("status").executes(AtmosphereCommands::status))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tamper(String op) {
		return Commands.literal(op)
				.executes(ctx -> tamperOp(ctx, op, DEFAULT_TAMPER_TICKS))
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
						.executes(ctx -> tamperOp(ctx, op, IntegerArgumentType.getInteger(ctx, "ticks"))));
	}

	private static Optional<ServerPlayer> target(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		return self != null ? Optional.of(self) : Services.watch().subject(ctx.getSource().getServer());
	}

	private static int surge(CommandContext<CommandSourceStack> ctx, float strength, int seconds) {
		Optional<ServerPlayer> player = target(ctx);
		if (player.isEmpty()) {
			return fail(ctx, "no player");
		}
		AtmosphereConfig cfg = AtmosphereConfig.get();
		if (!ActiveEffects.fogSurge(player.get(), strength, cfg.fogDriftRampTicks, seconds * 20, cfg.fogDriftFadeTicks)) {
			return fail(ctx, "no fog surge: the world is quiet for good (" + Gates.SILENCE_FOREVER_FLAG + ")");
		}
		return ok(ctx,String.format(Locale.ROOT, "fog surge %.2f for %ds -> %s", strength, seconds, player.get().getName().getString()));
	}

	private static int dusk(CommandContext<CommandSourceStack> ctx) {
		float level = FloatArgumentType.getFloat(ctx, "level");
		ClientEffects.setDuskFog(ctx.getSource().getServer(), level);
		return ok(ctx, String.format(Locale.ROOT, "dusk fog %.2f (stored; the next stage change sets the stage's level again)", level));
	}

	private static int silence(CommandContext<CommandSourceStack> ctx) {
		Optional<ServerPlayer> player = target(ctx);
		if (player.isEmpty()) {
			return fail(ctx, "no player");
		}
		int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
		ActiveEffects.silence(player.get(), seconds * 20, AtmosphereConfig.ticks(AtmosphereConfig.get().silenceFadeSeconds));
		return ok(ctx, "silence " + seconds + "s -> " + player.get().getName().getString());
	}

	private static int music(CommandContext<CommandSourceStack> ctx, boolean off) {
		ClientEffects.setMusicOff(ctx.getSource().getServer(), off);
		return ok(ctx, "music " + (off ? "off" : "on"));
	}

	private static int tamperOp(CommandContext<CommandSourceStack> ctx, String op, int ticks) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		Vec3 from = source.getPosition();
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(from, from).inflate(TAMPER_RANGE), Mob::isAlive);
		Optional<Mob> nearest = mobs.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(from)));
		if (nearest.isEmpty()) {
			return fail(ctx, "no mob within " + (int) TAMPER_RANGE + " blocks");
		}
		Mob mob = nearest.get();
		Vec3 eye = source.getEntity() != null ? source.getEntity().getEyePosition() : from;
		boolean done = switch (op) {
			case "freeze" -> Services.mobs().freeze(mob, ticks);
			case "face" -> Services.mobs().face(mob, eye, ticks);
			case "silence" -> Services.mobs().silence(mob, ticks);
			default -> {
				Services.mobs().release(mob);
				yield true;
			}
		};
		String what = mob.getType().getDescription().getString() + " at " + mob.blockPosition().toShortString();
		return done ? ok(ctx, "tamper " + op + (ticks > 0 ? " " + ticks + "t " : " ") + what) : fail(ctx, "tamper " + op + " refused for " + what);
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState.Effects effects = HerobrineState.get(server).effects();
		AtmosphereData data = AtmosphereData.get(server);
		List<Mob> tracked = MobTamperImpl.INSTANCE.tracked();
		ok(ctx, String.format(Locale.ROOT, "musicOff=%s (first night done=%s) duskFog=%.2f (set for stage %d)", effects.musicOff(), data.musicOffDone(),
				effects.duskFogLevel(), data.duskStage()));
		StringBuilder line = new StringBuilder("tampered mobs: " + tracked.size());
		for (Mob mob : tracked.subList(0, Math.min(6, tracked.size()))) {
			MobTamperImpl t = MobTamperImpl.INSTANCE;
			line.append(String.format(Locale.ROOT, " [%s %s%s%s]", mob.getType().getDescription().getString(), t.isFrozen(mob) ? "F" : "", t.isFacing(mob) ? "L" : "",
					t.isSilenced(mob) ? "S" : ""));
		}
		ok(ctx, line + " episodes=" + Tasks.episodeCount());
		target(ctx).ifPresent(player -> ok(ctx, deadMountainLine(player)));
		return 1;
	}

	private static String deadMountainLine(ServerPlayer player) {
		List<DeadMountains.Area> all = DeadMountains.in(player.level().dimension());
		boolean inside = DeadMountains.contains(player.level(), player.blockPosition());
		List<DeadMountains.Area> near = DeadMountains.near(player.level().dimension(), player.getX(), player.getZ(), Double.MAX_VALUE);
		String nearest = near.isEmpty() ? "none"
				: String.format(Locale.ROOT, "%d %d r=%d, edge %.0f blocks away", near.getFirst().x(), near.getFirst().z(), near.getFirst().radius(),
						Math.max(0.0, near.getFirst().edgeDistance(player.getX(), player.getZ())));
		return String.format(Locale.ROOT, "dead mountains here=%d %s; nearest %s; client knows %d (quiet and no animals inside)", all.size(),
				inside ? "INSIDE one" : "outside", nearest, DeadMountains.sentTo(player).size());
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] atmosphere: " + text), true);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendFailure(Component.literal("[a1016] atmosphere: " + text));
		return 0;
	}
}
