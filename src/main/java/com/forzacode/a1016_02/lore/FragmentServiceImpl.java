package com.forzacode.a1016_02.lore;

import java.util.Optional;

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

	@Override
	public boolean place(String id, ServerLevel level, BlockPos hint) {
		return FragmentData.get(id)
				.flatMap(fragment -> engine.placeNear(level, fragment, hint, NEAR_MIN, NEAR_MAX, LoreConfig.get().candidatesPerAttempt, false,
						Optional.empty()))
				.isPresent();
	}

	@Override
	public void markRead(ServerPlayer player, String id) {
		markRead(HerobrineState.get(player.level().getServer()), player, id);
	}

	/** Records the read; the first time, sets {@code listRead} for F06 and fires {@link HerobrineEvents#FRAGMENT_READ}. */
	static boolean markRead(HerobrineState state, ServerPlayer player, String id) {
		if (!state.markFragmentRead(id)) {
			return false;
		}
		if (id.equals("F06")) {
			state.setListRead(true);
		}
		A1016_02.LOGGER.info("[a1016] lore: {} read {}", player.getName().getString(), id);
		HerobrineEvents.FRAGMENT_READ.invoker().onFragmentRead(player, id);
		return true;
	}

	@Override
	public Optional<GlobalPos> placed(MinecraftServer server, String id) {
		return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get(id));
	}
}
