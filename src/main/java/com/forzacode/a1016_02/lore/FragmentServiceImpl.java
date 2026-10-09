package com.forzacode.a1016_02.lore;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FragmentService;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Lore's {@link FragmentService}: enabled by the world profile, placed by the {@link FragmentEngine}, read once. */
final class FragmentServiceImpl implements FragmentService {
	/** Distance band for {@link #place}: near the hint. */
	private static final int NEAR_MIN = 0;
	private static final int NEAR_MAX = 48;

	private final FragmentEngine engine;

	FragmentServiceImpl(FragmentEngine engine) {
		this.engine = engine;
	}

	@Override
	public boolean isEnabled(MinecraftServer server, String id) {
		return HerobrineState.get(server).profile().fragments().contains(id);
	}

	/**
	 * Places an enabled fragment near {@code hint} once. Idempotent: true without placing anything if it is
	 * already placed; false for a fragment this world did not roll.
	 */
	@Override
	public boolean place(String id, ServerLevel level, BlockPos hint) {
		return placeOnce(HerobrineState.get(level.getServer()), id, () -> FragmentData.get(id)
				.flatMap(fragment -> engine.placeNear(level, fragment, hint, NEAR_MIN, NEAR_MAX, LoreConfig.get().candidatesPerAttempt, false,
						Optional.empty()))
				.isPresent());
	}

	/** The gate of {@link #place}: disabled fragments are never placed, placed ones never again. */
	static boolean placeOnce(HerobrineState state, String id, BooleanSupplier placer) {
		if (!state.profile().fragments().contains(id)) {
			return false;
		}
		return state.fragmentsPlaced().containsKey(id) || placer.getAsBoolean();
	}

	@Override
	public void markRead(ServerPlayer player, String id) {
		markRead(HerobrineState.get(player.level().getServer()), player, id);
	}

	/** Records the read; the first time, also fires {@link HerobrineEvents#FRAGMENT_READ}. */
	static boolean markRead(HerobrineState state, ServerPlayer player, String id) {
		if (!recordRead(state, id)) {
			return false;
		}
		A1016_02.LOGGER.info("[a1016] lore: {} read {}", player.getName().getString(), id);
		HerobrineEvents.FRAGMENT_READ.invoker().onFragmentRead(player, id);
		return true;
	}

	/** The state part of a read: marks it, and sets {@code listRead} for the list (F06). True the first time. */
	static boolean recordRead(HerobrineState state, String id) {
		if (!state.markFragmentRead(id)) {
			return false;
		}
		if (id.equals("F06")) {
			state.setListRead(true);
		}
		return true;
	}

	@Override
	public Optional<GlobalPos> placed(MinecraftServer server, String id) {
		return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get(id));
	}
}
