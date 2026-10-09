package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.SoundCues;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The dig workstream's server tick: delayed sound cues, the torches-behind-you follower, explored-cave and
 * planted-sapling tracking, the tunnel that grows between visits, the ENTERED_TUNNEL trigger and "Under you".
 */
public final class DigTicker {
	public static final DigTicker INSTANCE = new DigTicker();
	public static final String CAUSE_TORCHES_BEHIND = "dig:torches_behind_you";
	/** Cost cadences in ticks (how often things are looked at), not pacing. */
	private static final int SAMPLE_TICKS = 20;
	private static final int NETWORK_TICKS = 100;

	private record Cue(UUID player, long at, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
	}

	/** The torches of a cave that go out once the player is far enough past them. */
	private static final class TorchSession {
		final UUID player;
		final ResourceKey<Level> dimension;
		final LongOpenHashSet torches = new LongOpenHashSet();
		final long end;
		long next;
		long refresh;

		TorchSession(UUID player, ResourceKey<Level> dimension, long end) {
			this.player = player;
			this.dimension = dimension;
			this.end = end;
		}
	}

	private final List<Cue> cues = new ArrayList<>();
	private final Map<UUID, BlockPos> lastExplored = new HashMap<>();
	private final RandomSource random = RandomSource.create();
	private @Nullable TorchSession torchSession;
	private long lastEnteredPlayTick = Long.MIN_VALUE;

	private DigTicker() {
	}

	void clear() {
		cues.clear();
		lastExplored.clear();
		torchSession = null;
		lastEnteredPlayTick = Long.MIN_VALUE;
	}

	/** Plays a vanilla sound at {@code pos} for this player only, after {@code delay} ticks ({@link SoundCues}). */
	public void schedule(ServerPlayer player, long delay, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		schedule(player, delay, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, pos, volume, pitch);
	}

	public void schedule(ServerPlayer player, long delay, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		long now = player.level().getServer().getTickCount();
		cues.add(new Cue(player.getUUID(), now + Math.max(0, delay), sound, source, pos, volume, pitch));
	}

	/** Cues still waiting (for tests and debug). */
	public int pendingCues() {
		return cues.size();
	}

	/** Where this player's waiting cues will sound, in the order they were queued (for tests and debug). */
	public List<Vec3> pendingCuePositions(UUID player) {
		return cues.stream().filter(cue -> cue.player().equals(player)).map(Cue::pos).toList();
	}

	public boolean torchSessionActive() {
		return torchSession != null;
	}

	/** Starts (or replaces) the torches-behind-you follower. */
	public void startTorchSession(ServerPlayer player, List<BlockPos> torches) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		TorchSession session = new TorchSession(player.getUUID(), player.level().dimension(),
				now + ModConfig.realTicks(DigConfig.get().torchesBehindSessionMinutes * 60));
		torches.forEach(pos -> session.torches.add(pos.asLong()));
		session.next = now;
		session.refresh = now + 200;
		torchSession = session;
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		playCues(server, now);
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return;
		}
		ServerPlayer player = subject.get();
		DigData data = DigData.get(server);
		DigConfig config = DigConfig.get();
		if (now % SAMPLE_TICKS == 0) {
			sampleExplored(player, data, config);
			checkEnteredTunnel(server, player, data, config);
			trackGrowingTunnel(server, player, data, config);
			tickTorchSession(player, now);
		}
		if (now % Math.max(SAMPLE_TICKS, Math.round(config.saplingScanSeconds * 20)) == 0) {
			scanSaplings(player, data, config);
		}
		if (now % NETWORK_TICKS == 0) {
			UnderYou.tick(server, player, data, random);
		}
	}

	private void playCues(MinecraftServer server, long now) {
		if (cues.isEmpty()) {
			return;
		}
		Iterator<Cue> it = cues.iterator();
		while (it.hasNext()) {
			Cue cue = it.next();
			if (cue.at() > now) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(cue.player());
			if (player != null) {
				SoundCues.playTo(player, cue.sound(), cue.source(), cue.pos(), cue.volume(), cue.pitch());
			}
		}
	}

	/** True if the position is in a cave: no sky light to speak of and well below the surface. */
	public static boolean underground(ServerLevel level, BlockPos pos) {
		return level.getBrightness(LightLayer.SKY, pos) <= 4 && pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 4;
	}

	private void sampleExplored(ServerPlayer player, DigData data, DigConfig config) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		if (!level.isLoaded(pos) || !underground(level, pos)) {
			return;
		}
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isPresent() && base.get().dimension().equals(level.dimension()) && pos.getY() >= base.get().pos().getY() - 3
				&& horizontalDistSqr(pos, base.get().pos()) <= (long) config.homeRadius * config.homeRadius) {
			return; // inside the base, not a cave
		}
		BlockPos last = lastExplored.get(player.getUUID());
		if (last != null && Tunnels.cheb(last, pos) < config.exploredSpacing) {
			return;
		}
		lastExplored.put(player.getUUID(), pos.immutable());
		if (data.explored(level.dimension()).add(pos, config.exploredMaxPerDimension)) {
			data.setDirty();
		}
	}

	private void scanSaplings(ServerPlayer player, DigData data, DigConfig config) {
		ServerLevel level = player.level();
		if (!Tunnels.chunksLoaded(level, player.blockPosition(), config.saplingScanRadius)) {
			return;
		}
		PosSet planted = data.planted(level.dimension());
		boolean added = false;
		for (BlockPos pos : Services.watch().placedNear(level, player.blockPosition(), config.saplingScanRadius, BlockTags.SAPLINGS)) {
			added |= planted.add(pos, config.plantedMaxPerDimension);
		}
		if (added) {
			data.setDirty();
		}
	}

	private void checkEnteredTunnel(MinecraftServer server, ServerPlayer player, DigData data, DigConfig config) {
		long play = GameClock.playTicks(server);
		long cooldown = ModConfig.realTicks(config.enteredTunnelCooldownMinutes * 60);
		if (lastEnteredPlayTick != Long.MIN_VALUE && play - lastEnteredPlayTick < cooldown) {
			return;
		}
		if (insideHisTunnel(player, data)) {
			lastEnteredPlayTick = play;
			Attention.trigger(server, AttentionTrigger.ENTERED_TUNNEL);
		}
	}

	/** In one of his 2x2 tunnels: a cell of ours, or within a recorded CUT, or at a TUNNEL_END. */
	public static boolean insideHisTunnel(ServerPlayer player, DigData data) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		if (data.tunnelCells(level.dimension()).contains(feet.asLong())) {
			return true;
		}
		GlobalPos here = GlobalPos.of(level.dimension(), feet);
		for (SiteRegistry.Site site : Services.sites().find(SiteType.CUT, here, 64)) {
			int reach = Math.max(2, site.size());
			if (horizontalDistSqr(feet, site.pos()) <= (long) reach * reach && Math.abs(feet.getY() - site.pos().getY()) <= 4) {
				return true;
			}
		}
		for (SiteRegistry.Site site : Services.sites().find(SiteType.TUNNEL_END, here, 4)) {
			if (Math.abs(feet.getY() - site.pos().getY()) <= 3) {
				return true;
			}
		}
		return false;
	}

	private void trackGrowingTunnel(MinecraftServer server, ServerPlayer player, DigData data, DigConfig config) {
		GrowingTunnel tunnel = data.growing;
		if (tunnel == null || tunnel.complete || !player.level().dimension().equals(tunnel.dimension) || tunnel.anchors.isEmpty()) {
			return;
		}
		double nearest = Double.MAX_VALUE;
		for (BlockPos anchor : tunnel.anchors) {
			nearest = Math.min(nearest, player.position().distanceTo(Vec3.atLowerCornerOf(anchor).add(1, 1, 1)));
		}
		if (nearest <= config.growingVisitRadius) {
			if (!tunnel.visited) {
				tunnel.visit();
				data.setDirty();
			}
			return;
		}
		long day = GameClock.day(server);
		if (tunnel.visited && nearest >= config.growingLeaveDistance && day - tunnel.lastGrowDay >= config.growingMinDaysBetween) {
			Pacing pacing = ModConfig.pacing();
			boolean wasComplete = tunnel.complete;
			if (tunnel.grow(player.level(), pacing.tunnelGrowthPerVisit, config.tunnelPlayerClearance, config, Services.traces(), day)) {
				tunnel.visited = false;
				data.changed();
			} else if (tunnel.complete != wasComplete) {
				data.setDirty(); // it ended without carving: nothing new to index
			}
		}
	}

	private void tickTorchSession(ServerPlayer player, long now) {
		TorchSession session = torchSession;
		if (session == null) {
			return;
		}
		if (!session.player.equals(player.getUUID()) || !session.dimension.equals(player.level().dimension()) || now > session.end) {
			torchSession = null;
			return;
		}
		ServerLevel level = player.level();
		BlockPos base = DigCard.base(player).orElse(null);
		if (now >= session.refresh) {
			session.refresh = now + 200;
			for (BlockPos torch : TorchCards.caveTorches(level, player.blockPosition(), 64, base)) {
				session.torches.add(torch.asLong());
			}
		}
		if (now < session.next) {
			return;
		}
		int behind = ModConfig.pacing().torchesBehindDistance;
		List<BlockPos> candidates = new ArrayList<>();
		LongOpenHashSet gone = new LongOpenHashSet();
		for (long packed : session.torches) {
			BlockPos torch = BlockPos.of(packed);
			if (!level.isLoaded(torch)) {
				continue;
			}
			if (!TorchCards.caveTorch(level, torch, base)) {
				gone.add(packed);
			} else if (torch.distToCenterSqr(player.position()) >= (double) behind * behind) {
				candidates.add(torch);
			}
		}
		session.torches.removeAll(gone);
		// The nearest torch past the line goes first: they go out one by one behind the player.
		candidates.sort(Comparator.comparingDouble(torch -> torch.distToCenterSqr(player.position())));
		for (BlockPos torch : candidates) {
			if (Services.traces().remove(level, torch, CAUSE_TORCHES_BEHIND)) {
				session.torches.remove(torch.asLong());
				session.next = now + ModConfig.realTicks(DigConfig.get().torchesBehindGapSeconds);
				break;
			}
		}
		if (session.torches.isEmpty()) {
			torchSession = null;
		}
	}

	static long horizontalDistSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
