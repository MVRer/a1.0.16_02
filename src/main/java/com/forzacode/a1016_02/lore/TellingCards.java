package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.lore.TellingData.WrittenSign;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Lore's cards for the Telling stage: "Stop." (F03, once per world), blank sign, and place not found (F04). All
 * three change text or blocks only through {@code TraceService}, out of view.
 */
final class TellingCards {
	static final String STOP = "stop_sign";
	static final String BLANK = "blank_sign";
	static final String NOT_FOUND = "place_not_found";

	private TellingCards() {
	}

	static void register() {
		Director.register(new StopSign());
		Director.register(new BlankSign());
		Director.register(new NotFound());
	}

	/** Shared card shape: Telling, no habits, no fake. */
	private abstract static class TellingCard implements EventCard {
		@Override
		public Stage earliestStage() {
			return Stage.TELLING;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of();
		}

		@Override
		public boolean hasFake() {
			return false;
		}
	}

	// --- "Stop." ---

	/** F03: the first sign the player wrote about him in Stage 3 now shows only "Stop." on line 2. Once per world. */
	static final class StopSign extends TellingCard {
		@Override
		public String id() {
			return STOP;
		}

		@Override
		public Tier tier() {
			return Tier.SIGNATURE;
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.TEXT);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			return SignEdits.available() && !HerobrineState.get(server).stopFired() && TellingData.get(server).stopCandidate().isPresent();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			if (!SignEdits.available()) {
				return FireResult.SKIPPED;
			}
			MinecraftServer server = ctx.level().getServer();
			return fireStop(server, HerobrineState.get(server), TellingData.get(server), SignEdits.editor(Services.traces()),
					LiveBooks.name(server, ctx.player()));
		}
	}

	/**
	 * Shows F03's lines on the "Stop." candidate (the back goes blank). {@code SKIPPED} if it already happened or
	 * there is no candidate any more; {@code NO_SPOT} while the sign is in view or its chunk is not loaded.
	 */
	static FireResult fireStop(MinecraftServer server, HerobrineState state, TellingData data, SignEdits.Editor editor, String playerName) {
		if (state.stopFired()) {
			return FireResult.SKIPPED;
		}
		Optional<GlobalPos> candidate = data.stopCandidate();
		Optional<Fragment> f03 = FragmentData.get("F03");
		if (candidate.isEmpty() || f03.isEmpty()) {
			return FireResult.SKIPPED;
		}
		GlobalPos at = candidate.get();
		ServerLevel level = server.getLevel(at.dimension());
		if (level == null) {
			return FireResult.SKIPPED;
		}
		if (!ChunkGate.request(level, at.pos(), 0)) {
			return FireResult.NO_SPOT;
		}
		if (!(level.getBlockEntity(at.pos()) instanceof SignBlockEntity sign) || sign.isWaxed()) {
			// Gone, or waxed by the player (he cannot change a waxed sign): the next sign about him will be the one.
			data.setStopCandidate(null);
			return FireResult.SKIPPED;
		}
		if (!editor.edit(level, at.pos(), f03.get().linesFor(playerName), List.of(), "lore:his/F03")) {
			return FireResult.NO_SPOT;
		}
		state.setStopFired(true);
		data.setStopSign(at);
		data.setStopCandidate(null);
		state.setFragmentPlaced("F03", at);
		LoreData lore = LoreData.get(server);
		lore.clearReadTargets("F03");
		lore.addReadTarget("F03", at);
		A1016_02.LOGGER.info("[a1016] lore: the sign at {} now says \"Stop.\"", at.pos().toShortString());
		return FireResult.FIRED;
	}

	// --- blank sign ---

	/** A sign the player wrote after they first wrote about him comes back empty. */
	static final class BlankSign extends TellingCard {
		@Override
		public String id() {
			return BLANK;
		}

		@Override
		public Tier tier() {
			return Tier.MAJOR;
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.TEXT);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			return SignEdits.available() && !blankPool(player, TellingData.get(server)).isEmpty();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			if (!SignEdits.available()) {
				return FireResult.SKIPPED;
			}
			MinecraftServer server = ctx.level().getServer();
			return fireBlank(server, ctx.player(), TellingData.get(server), SignEdits.editor(Services.traces()), ctx.random());
		}
	}

	/**
	 * The signs blank sign may take: this player's, written after their first telling, not blanked yet, and never
	 * the "Stop." sign (or the one that will be). Signs about him first, then oldest first.
	 */
	static List<WrittenSign> blankPool(ServerPlayer player, TellingData data) {
		List<WrittenSign> pool = new ArrayList<>();
		for (WrittenSign sign : data.signs()) {
			if (sign.afterTelling() && !sign.blanked() && sign.writer().equals(player.getUUID()) && !data.stopCandidate().map(sign.pos()::equals).orElse(false)
					&& !data.stopSign().map(sign.pos()::equals).orElse(false)) {
				pool.add(sign);
			}
		}
		pool.sort(Comparator.comparing((WrittenSign s) -> !s.aboutHim()).thenComparingLong(WrittenSign::seq));
		return pool;
	}

	/**
	 * Blanks one sign of the pool that is out of view (signs about him first, a random one among them).
	 * {@code SKIPPED} with an empty pool, {@code NO_SPOT} if every candidate is in view or unloaded.
	 */
	static FireResult fireBlank(MinecraftServer server, ServerPlayer player, TellingData data, SignEdits.Editor editor, RandomSource random) {
		List<WrittenSign> pool = blankPool(player, data);
		if (pool.isEmpty()) {
			return FireResult.SKIPPED;
		}
		List<WrittenSign> about = new ArrayList<>(pool.stream().filter(WrittenSign::aboutHim).toList());
		List<WrittenSign> others = new ArrayList<>(pool.stream().filter(s -> !s.aboutHim()).toList());
		Util.shuffle(about, random);
		Util.shuffle(others, random);
		about.addAll(others);
		boolean waiting = false;
		for (WrittenSign sign : about) {
			ServerLevel level = server.getLevel(sign.pos().dimension());
			if (level == null) {
				continue;
			}
			if (!ChunkGate.request(level, sign.pos().pos(), 0)) {
				waiting = true;
				continue;
			}
			if (!(level.getBlockEntity(sign.pos().pos()) instanceof SignBlockEntity be)) {
				data.removeSign(sign.pos());
				continue;
			}
			if (SignEdits.isBlank(be)) {
				data.putSign(sign.withBlanked(true));
				continue;
			}
			if (be.isWaxed()) {
				continue;
			}
			waiting = true;
			if (editor.edit(level, sign.pos().pos(), List.of(), List.of(), "lore:his/" + BLANK)) {
				data.putSign(sign.withBlanked(true));
				A1016_02.LOGGER.info("[a1016] lore: the sign at {} came back blank", sign.pos().pos().toShortString());
				return FireResult.FIRED;
			}
		}
		return waiting ? FireResult.NO_SPOT : FireResult.SKIPPED;
	}

	// --- place not found ---

	/** F04: after "Stop.", a fragment site the player visited is gone: flat dirt, one blank sign. */
	static final class NotFound extends TellingCard {
		@Override
		public String id() {
			return NOT_FOUND;
		}

		@Override
		public Tier tier() {
			return Tier.MAJOR;
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.SCAR, CardTag.TEXT);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			HerobrineState state = HerobrineState.get(server);
			TellingData data = TellingData.get(server);
			return state.stopFired() && !state.hasFlag(PlaceNotFound.DONE_FLAG) && data.notFound().isEmpty()
					&& data.visited().stream().anyMatch(id -> PlaceNotFound.mayTake(id) && state.fragmentsPlaced().containsKey(id));
		}

		@Override
		public FireResult fire(FireContext ctx) {
			MinecraftServer server = ctx.level().getServer();
			// A debug fire may take a site near the base (it never skips the out-of-view rule).
			Optional<net.minecraft.core.BlockPos> base = ctx.forced() ? Optional.empty() : Services.watch().base(ctx.player()).map(GlobalPos::pos);
			return PlaceNotFound.fire(server, HerobrineState.get(server), TellingData.get(server), Services.traces(),
					SignEdits.editor(Services.traces()), SignEdits.available(), base, ctx.random(), LoreConfig.get());
		}
	}
}
