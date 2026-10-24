package com.forzacode.a1016_02.world.sc2.s16;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Feature 16 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 16), world's part: the pyramid's two
 * kinds ({@code s16} on land, {@code s16d} at sea, both {@link Remains.Kind#PYRAMID}, one structure set on the rays),
 * its answers 16b and 16c ({@link PyramidAnswers}) and their give-back when switched off, the dig into a closed one
 * ({@code DUG_INTO_PYRAMID}, once a place), {@code /a1016 sc 16} and the dev-only exhibits.
 */
public final class S16 {
	/** The land kind's id (its structure JSON's {@code kind}). */
	public static final String ID = "s16";
	/** The sea form's id. */
	public static final String SEA_ID = "s16d";
	/** Every ledger cause of its answers starts with this. */
	public static final String CAUSE = "world:s16";
	/** The per-place count that remembers a closed one was dug into ({@code Sc2Data}, never an answer). */
	public static final String DUG = "16s";

	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.PYRAMID, Sc16Config::get, PyramidSiting.LAND, PyramidShape::new,
			Sc2Kind.Interiors.FROM_SHAPE, PyramidShape.VERSION);
	public static final Sc2Kind SEA_KIND = new Sc2Kind(SEA_ID, Remains.Kind.PYRAMID, Sc16Config::get, PyramidSiting.SEA, PyramidShape::new,
			Sc2Kind.Interiors.FROM_SHAPE, PyramidShape.VERSION);

	private S16() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc2Kind.register(KIND);
		Sc2Kind.register(SEA_KIND);
		Answers.giveBackWhenOff("sc16", CAUSE);
		HerobrineEvents.REMAINS_ENTERED.register((level, place, player) -> PyramidAnswers.LIVE.entered(place, player, level.getServer().getTickCount()));
		HerobrineEvents.REMAINS_LEFT.register((level, place, player) -> PyramidAnswers.LIVE.left(place, player));
		ServerTickEvents.END_SERVER_TICK.register(PyramidAnswers.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PyramidAnswers.reset());
		SelfCreated.onSwitchedOff("sc16v", server -> PyramidAnswers.reset());
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				broken(serverLevel, serverPlayer, pos);
			}
		});
		Sc16Commands.register();
		if (com.forzacode.a1016_02.core.Gallery.active()) {
			Sc16Gallery.register();
		}
	}

	/** A player broke a block: if it was of a closed one's mass, the first time for that place, {@code DUG_INTO_PYRAMID}. */
	static void broken(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!SelfCreated.enabled() || player.isSpectator()) {
			return;
		}
		if (dug(level, pos, Services.remains(), RemainsImpl.LIVE.data(), place -> PyramidAnswers.shapeOf(level, place))) {
			Attention.trigger(level.getServer(), AttentionTrigger.DUG_INTO_PYRAMID);
			A1016_02.LOGGER.debug("[a1016] s2: {} dug into a closed s16 at {}", player.getName().getString(), pos.toShortString());
		}
	}

	/**
	 * True (and remembered in {@code data}) the first time a block of a closed one's mass is broken at {@code pos}: a
	 * known pyramid whose box holds it, closed (no way in, or the sea form), the cell of its mass.
	 */
	public static boolean dug(ServerLevel level, BlockPos pos, Remains remains, Sc2Data data, Function<Remains.Place, @Nullable PyramidShape> shapes) {
		Remains.Place place = remains.nearest(level, pos, EnumSet.of(Remains.Kind.PYRAMID), 0).orElse(null);
		if (place == null || !place.boxHolds(pos) || !(PyramidShape.sealed(place.variant()) || PyramidShape.sea(place.variant()))) {
			return false;
		}
		PyramidShape shape = shapes.apply(place);
		if (shape == null || !shape.inMass(pos.getX(), pos.getY(), pos.getZ()) || data.spent(place, DUG).ever() > 0) {
			return false;
		}
		data.spend(place, DUG, List.of(), GameClock.day(level.getServer()));
		return true;
	}
}
