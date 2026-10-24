package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 21 of the self-created layer's second wave (docs/SELF_CREATED_2.md), atmosphere's part: the card
 * ({@link RingCard}), its section ({@link Sc21Config}), its scenes' tick ({@link Rings}), the switch's revert (scenes end,
 * the bared ground given back through the ledger, tried again every {@link #GIVE_BACK_RETRY} ticks while off), the
 * empty night's own (04+, {@link #emptyNight}), the commands under {@code /a1016 sc 21} and the gallery exhibit
 * {@code 21} (development only). Also keeps where he last stood for 23 ({@code FIGURE_LEFT}).
 */
public final class S21 {
	/** While the layer is off, bared ground not yet given back is tried again this often (ticks). */
	static final int GIVE_BACK_RETRY = 600;
	/** 04+ looks for a herd this often (ticks) on a night it won its roll. */
	static final int EMPTY_NIGHT_EVERY = 200;
	/** 04+'s herd within this of a participant. */
	static final double EMPTY_NIGHT_REACH = 96.0;

	static final RingCard CARD = new RingCard();
	private static boolean giveBackPending;

	private S21() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc21Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(S21::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Rings.LIVE.clear();
			giveBackPending = false;
		});
		SelfCreated.onSwitchedOff("sc21", server -> giveBackPending = Rings.LIVE.revert(server) > 0);
		HerobrineEvents.FIGURE_LEFT.register((level, stoodAt, yaw, path, variant, appearedFor, seen) -> {
			if (level.dimension() == Level.OVERWORLD) {
				Sc2xData.get(level.getServer()).setStood(stoodAt);
			}
		});
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("21")
				.executes(S21::status)
				.then(Commands.literal("force").executes(ctx -> fire(ctx, false)))
				.then(Commands.literal("fake").executes(ctx -> fire(ctx, true))))));
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit("21", "21", S21Gallery::place));
		}
	}

	private static void tick(MinecraftServer server) {
		Rings.LIVE.tick(server);
		if (giveBackPending && !SelfCreated.enabled() && server.getTickCount() % GIVE_BACK_RETRY == 0) {
			giveBackPending = Rings.giveBack(server) > 0;
		}
	}

	/**
	 * 04+ (docs/SELF_CREATED_2.md 21): called by the empty night ({@code EmptyNight.check}) every second while its quiet
	 * holds. Once a night, at {@code emptyNightChance}, one forms without a card round a herd within
	 * {@link #EMPTY_NIGHT_REACH} of a participant, looked for every {@link #EMPTY_NIGHT_EVERY} ticks; it is that night's
	 * one (the card's one a night), not one of the card's count.
	 */
	public static void emptyNight(MinecraftServer server, ServerLevel level, List<ServerPlayer> here) {
		if (here.isEmpty() || !SelfCreated.enabled() || level.dimension() != Level.OVERWORLD || !ScGates.storyAllows(server)) {
			return;
		}
		if (emptyNight(level, here, level.getDefaultClockTime(), Sc2xData.get(server), level.getRandom().nextDouble(),
				server.getTickCount() % EMPTY_NIGHT_EVERY == 0, Rings.LIVE, Services.watch()).isPresent()) {
			A1016_02.LOGGER.info("[a1016] sc_21: (04)");
		}
	}

	/**
	 * {@link #emptyNight}'s step at {@code clock} over {@code data} (tests give their own): a new night takes {@code roll}
	 * (won under {@code emptyNightChance}); on a won night with none formed yet, and when {@code look}, a herd within
	 * {@link #EMPTY_NIGHT_REACH} of the first participant who has one starts going round in {@code rings}, bound to who
	 * shares the moment with them by {@code watch}. Returns the scene it started.
	 */
	public static Optional<Rings.Scene> emptyNight(ServerLevel level, List<ServerPlayer> here, long clock, Sc2xData data, double roll, boolean look,
			Rings rings, PlayerWatch watch) {
		if (!Herds.night(Math.floorMod(clock, Herds.DAY))) {
			return Optional.empty();
		}
		long night = RingCard.nightOf(clock);
		Sc2xData.EmptyRoll memory = data.emptyRoll();
		if (memory.rolled() != night) {
			memory = new Sc2xData.EmptyRoll(night, roll < Sc21Config.get().emptyNightChance, memory.formed());
			data.setEmptyRoll(memory);
		}
		if (!memory.won() || memory.formed() == night || rings.running() || !look) {
			return Optional.empty();
		}
		Sc2xData.Caps caps = data.caps("21");
		if (caps.lastNight() == night) {
			return Optional.empty();
		}
		Sc21Config cfg = Sc21Config.get();
		for (ServerPlayer player : here) {
			Optional<RingCard.Plan> plan = RingCard.plan(level, player.position(), cfg.minMobs, Sc21Config.MIN_DISTANCE, EMPTY_NIGHT_REACH);
			if (plan.isEmpty()) {
				continue;
			}
			List<java.util.UUID> sharers = watch.sharingWith(player).stream().map(ServerPlayer::getUUID).toList();
			int seconds = cfg.circleSecondsMin + level.getRandom().nextInt(Math.max(1, cfg.circleSecondsMax - cfg.circleSecondsMin + 1));
			Optional<Rings.Scene> scene = rings.start(level, plan.get().site(), plan.get().animals(), sharers, seconds * 20, false);
			if (scene.isPresent()) {
				data.setEmptyRoll(new Sc2xData.EmptyRoll(night, true, night));
				data.setCaps("21", new Sc2xData.Caps(caps.count(), caps.lastDay(), night));
				return scene;
			}
		}
		return Optional.empty();
	}

	// --- commands ---

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		ok(ctx, "sc 21:");
		CARD.describe(player).forEach(line -> ok(ctx, "  " + line));
		return 1;
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, boolean fake) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, RingCard.ID, fake);
		if (result != FireResult.FIRED) {
			return fail(ctx, "sc 21: did not fire (" + result + "): " + CARD.refusal(player, true).orElse("the moment passed"));
		}
		Vec3 at = Rings.LIVE.scenes().isEmpty() ? null : Rings.LIVE.scenes().getLast().middle();
		return ok(ctx, "sc 21: fired" + (fake ? " (fake)" : "") + (at == null ? "" : String.format(" at %.0f %.0f %.0f", at.x, at.y, at.z)));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		List<ServerPlayer> all = Services.watch().participants(ctx.getSource().getServer());
		return all.isEmpty() ? null : all.getFirst();
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendFailure(Component.literal(line));
		return 0;
	}
}
