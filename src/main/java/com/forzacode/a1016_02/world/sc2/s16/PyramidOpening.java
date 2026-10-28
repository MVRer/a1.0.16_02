package com.forzacode.a1016_02.world.sc2.s16;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 16g, a sealed one opens (R3-2, Mariano's playtest: a sealed pyramid must offer something from outside too). Its lit
 * strip says "here" on unbroken brick (D-110); a participant (anyone but a spectator) who stays {@code t} seconds within
 * {@value #NEAR} of where its way in would be finds, once they look away, the way in there: the open one's two rows of
 * three taken out of the face ({@code world:s16/open}, ledgered, out of everyone's view through the {@link TraceService},
 * given back when the layer is switched off). Once a place ever ({@code Sc2Data}'s {@value #SPENT}); while the places are
 * awake and the story allows ({@code Answers.held}). Not an answer of the kit's (nobody is inside yet): no tension, no
 * event; what waits inside (the trail, 16e) does the rest. Version 4 on land; a look a second. Server thread.
 */
public final class PyramidOpening {
	public static final String OPEN = S16.CAUSE + "/open";
	/** The per-place count that remembers it opened ({@code Sc2Data}). */
	public static final String SPENT = "16g";
	/** How near where its way in would be (blocks). */
	public static final int NEAR = 20;
	/** How often it looks (ticks). */
	public static final int EVERY_TICKS = 20;

	/**
	 * What it reads: why the places are held now (null: awake), everyone online, the known pyramid round a point, a place's
	 * shape, the data, the traces, the config, the in-game day.
	 */
	public record Env(Function<MinecraftServer, @Nullable String> held, Visits.People people, BiFunction<ServerLevel, BlockPos, Optional<Remains.Place>> nearest,
			Function<ServerLevel, Function<Remains.Place, @Nullable PyramidShape>> shapes, Supplier<Sc2Data> data, Supplier<TraceService> traces,
			Supplier<Sc16Config> config, Function<MinecraftServer, Long> day) {
	}

	public static final PyramidOpening LIVE = new PyramidOpening(new Env(Answers.LIVE::held, server -> List.copyOf(server.getPlayerList().getPlayers()),
			(level, pos) -> Services.remains().nearest(level, pos, EnumSet.of(Remains.Kind.PYRAMID), 8), level -> place -> PyramidAnswers.shapeOf(level, place),
			RemainsImpl.LIVE::data, Services::traces, Sc16Config::get, GameClock::day));

	private final Env env;
	/** How long each player has stood near each sealed one (ticks), by place and player. */
	private final Map<String, Long> near = new HashMap<>();

	public PyramidOpening(Env env) {
		this.env = env;
	}

	/** The live tick: a look every {@value #EVERY_TICKS} ticks. */
	public void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY_TICKS == 13) {
			look(server);
		}
	}

	/** One look at everyone near a sealed one. */
	public void look(MinecraftServer server) {
		if (env.held().apply(server) != null) {
			near.clear();
			return;
		}
		Set<String> seen = new HashSet<>();
		for (ServerPlayer player : env.people().online(server)) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = player.level();
			Remains.Place place = env.nearest().apply(level, player.blockPosition()).orElse(null);
			if (place == null || !PyramidShape.sealed(place.variant()) || PyramidShape.sea(place.variant())) {
				continue;
			}
			PyramidShape shape = env.shapes().apply(level).apply(place);
			if (shape == null || shape.sealedMouth().isEmpty() || shape.version() < 4
					|| player.position().distanceTo(Vec3.atBottomCenterOf(shape.entrance())) > NEAR) {
				continue;
			}
			Sc2Data data = env.data().get();
			if (data.spent(place, SPENT).ever() > 0) {
				continue;
			}
			String key = place.anchor().asLong() + "|" + player.getUUID();
			seen.add(key);
			long stood = near.merge(key, (long) EVERY_TICKS, Long::sum);
			if (stood < Math.round(env.config().get().openAfterSeconds * 20)) {
				continue;
			}
			List<BlockPos> cells = shape.sealedMouth().stream().filter(cell -> level.isLoaded(cell) && !level.getBlockState(cell).isAir()).toList();
			if (cells.size() < shape.sealedMouth().size() && cells.isEmpty()) {
				// Someone opened it by hand: it is open, nothing to do.
				data.spend(place, SPENT, List.of(player.getUUID()), env.day().apply(server));
				continue;
			}
			TraceBatch batch = env.traces().get().batch(level, OPEN);
			for (BlockPos cell : cells) {
				batch.remove(cell);
			}
			if (batch.commit()) {
				data.spend(place, SPENT, List.of(player.getUUID()), env.day().apply(server));
				near.remove(key);
				A1016_02.LOGGER.debug("[a1016] s2: 16g at {}", shape.entrance().toShortString());
			}
		}
		near.keySet().retainAll(seen);
	}

	/** Forgets who stood where (switch off, server stop). */
	public void clear() {
		near.clear();
	}
}
