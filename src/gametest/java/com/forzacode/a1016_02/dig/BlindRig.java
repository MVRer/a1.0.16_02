package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Blind moments of a test's own (D-077): its own crew ({@link Crew}: its players never join the server, so nobody else's
 * is a sharer, a listener or a viewer), its own numbers and combat clock, and a recorder that is the sound out. Its
 * players are made blind on the crew's own watch ({@code PlayerWatch.markBlind}: the live watch ignores mock players),
 * and the view is the crew's own (each online member, spectators included, through core's geometry). The torch goes
 * through the real trace service: no real player is in the level, so only the crew's view rule decides.
 */
final class BlindRig implements Ladder.Sounds, Blind.View {
	/** A sound played: when (server tick), what, where, how loud. */
	record Heard(long tick, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
	}

	final GameTestHelper helper;
	final MinecraftServer server;
	final DigGround ground;
	final Crew crew;
	final DigConfig config = new DigConfig();
	final List<Heard> heard = new ArrayList<>();
	/** What every crew member's ticks since combat read. */
	long sinceCombat = Long.MAX_VALUE;

	BlindRig(GameTestHelper helper, DigGround ground, String... names) {
		this.helper = helper;
		this.server = helper.getLevel().getServer();
		this.ground = ground;
		this.crew = new Crew(helper, names);
	}

	Blind.Env env() {
		return new Blind.Env(crew.watch, config, this, this, Services.traces(), crew.bases(), player -> sinceCombat);
	}

	/** Puts this crew member online standing in the cell at ground coordinates, looking level along {@code yaw}. */
	ServerPlayer stand(String name, int x, int y, int z, float yaw) {
		ServerPlayer player = crew.online.stream().filter(p -> p.getName().getString().equals(name)).findFirst()
				.orElseGet(() -> crew.join(name, Vec3.atBottomCenterOf(ground.at(x, y, z))));
		LadderRig.move(player, ground.at(x, y, z));
		LadderRig.look(player, yaw);
		return player;
	}

	/** The player has had a menu open for {@code ticks} (on the crew's own watch). */
	void blind(ServerPlayer player, long ticks) {
		crew.watch.markBlind(player, ticks);
	}

	Blind.@Nullable Why check(Blind.Kind kind, ServerPlayer player) {
		return Blind.check(kind, player, env());
	}

	FireResult fire(Blind.Kind kind, ServerPlayer player, boolean fake, boolean forced, long seed) {
		return Blind.fire(kind, new FireContext(player, player.level(), fake, forced, RandomSource.create(seed)), env());
	}

	/** That player's blind sounds in progress, or null. */
	static BlindCues.@Nullable Sequence sequence(ServerPlayer player) {
		return DigTicker.INSTANCE.blind().sequence(player.getUUID()).orElse(null);
	}

	static long seconds(double seconds) {
		return ModConfig.realTicks(seconds);
	}

	@Override
	public void play(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		heard.add(new Heard(server.getTickCount(), sound, source, pos, volume, pitch));
	}

	/** Everyone a sound could reach: this crew's own players in that level (spectators included), nobody else on the server. */
	@Override
	public List<ServerPlayer> listeners(ServerLevel level) {
		return crew.online.stream().filter(player -> player.level() == level).toList();
	}

	/** Out of view of every crew member online in that level, spectators included, by core's own geometry and numbers. */
	@Override
	public boolean outOfView(ServerLevel level, Collection<BlockPos> positions) {
		int chunks = server.getPlayerList().getViewDistance();
		List<TraceService.Viewer> viewers = listeners(level).stream().map(player -> TraceService.Viewer.of(player, chunks)).toList();
		Pacing pacing = ModConfig.pacing();
		return TraceService.positionsOutOfView(level, positions, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
	}
}
