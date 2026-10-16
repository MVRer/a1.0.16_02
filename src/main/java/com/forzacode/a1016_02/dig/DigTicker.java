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
import com.forzacode.a1016_02.core.PlayerWatch;
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

/**
 * The dig workstream's server tick: delayed sound cues, the torches-behind-you followers, explored-cave and
 * planted-sapling tracking, the tunnel that grows between visits, the ENTERED_TUNNEL trigger, "Under you" and the
 * tunnel ladder's steps and detector ({@link LadderSteps}, D-072), and the blind cards' sounds ({@link BlindCues},
 * D-077). With
 * friends (D-058 to D-062) it does all of it for every participant, looked up fresh each time and keyed by UUID:
 * each player has their own torch follower and explored-cave sampling, a cue plays at its spot for everyone in
 * hearing range, ENTERED_TUNNEL counts once per occasion ({@link TunnelEntries}), and a player who stops taking part
 * (leaves, or turns spectator) has whatever ran for them stopped ({@link #onStop}).
 */
public final class DigTicker {
	public static final DigTicker INSTANCE = new DigTicker();
	public static final String CAUSE_TORCHES_BEHIND = "dig:torches_behind_you";
	/** Cost cadences in ticks (how often things are looked at), not pacing. */
	private static final int SAMPLE_TICKS = 20;
	private static final int NETWORK_TICKS = 100;
	/** The tunnel ladder's detector: underground participants only, this often (D-072). */
	static final int LADDER_TICKS = 10;

	/** A sound queued for a player's card, played at its spot in its dimension for everyone in hearing range. */
	private record Cue(UUID player, ResourceKey<Level> dimension, long at, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume,
			float pitch) {
	}

	/** The torches of a cave that go out once its player (and everyone else there) is far enough past them. */
	private static final class TorchSession {
		final ResourceKey<Level> dimension;
		final LongOpenHashSet torches = new LongOpenHashSet();
		final long end;
		long next;
		long refresh;

		TorchSession(ResourceKey<Level> dimension, long end) {
			this.dimension = dimension;
			this.end = end;
		}
	}

	private final List<Cue> cues = new ArrayList<>();
	private final Map<UUID, BlockPos> lastExplored = new HashMap<>();
	private final Map<UUID, TorchSession> torchSessions = new HashMap<>();
	private final TunnelEntries entries = new TunnelEntries();
	private final LadderSteps ladder = new LadderSteps();
	private final BlindCues blind = new BlindCues();
	private final RandomSource random = RandomSource.create();

	private DigTicker() {
	}

	void clear() {
		cues.clear();
		lastExplored.clear();
		torchSessions.clear();
		entries.clear();
		ladder.clear();
		blind.clear();
	}

	/** The tunnel ladder's steps in progress and its detector's samples (D-072). */
	LadderSteps ladder() {
		return ladder;
	}

	/** The blind cards' sounds in progress and the armed debug fires (D-077). */
	BlindCues blind() {
		return blind;
	}

	/**
	 * D-062.5: a player stopped taking part (left, or turned spectator). Their torch follower stops where it is (the
	 * torches that already went stay gone, out of view); their sampling forgets them, and their ladder steps and blind
	 * sounds stop. Cues already queued still play at their spot for whoever is there to hear.
	 */
	void onStop(UUID player) {
		torchSessions.remove(player);
		lastExplored.remove(player);
		ladder.forget(player);
		blind.forget(player);
	}

	/** Plays a vanilla sound at {@code pos} after {@code delay} ticks, for everyone in hearing range there ({@link SoundCues#playAt}). */
	public void schedule(ServerPlayer player, long delay, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		schedule(player, delay, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, pos, volume, pitch);
	}

	public void schedule(ServerPlayer player, long delay, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		long now = player.level().getServer().getTickCount();
		cues.add(new Cue(player.getUUID(), player.level().dimension(), now + Math.max(0, delay), sound, source, pos, volume, pitch));
	}

	/** Cues still waiting (for tests and debug). */
	public int pendingCues() {
		return cues.size();
	}

	/** Where this player's waiting cues will sound, in the order they were queued (for tests and debug). */
	public List<Vec3> pendingCuePositions(UUID player) {
		return cues.stream().filter(cue -> cue.player().equals(player)).map(Cue::pos).toList();
	}

	/** True while this player's torches-behind-you follower runs. */
	public boolean torchSessionActive(UUID player) {
		return torchSessions.containsKey(player);
	}

	/** Starts (or replaces) this player's torches-behind-you follower. */
	public void startTorchSession(ServerPlayer player, List<BlockPos> torches) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		TorchSession session = new TorchSession(player.level().dimension(), now + ModConfig.realTicks(DigConfig.get().torchesBehindSessionMinutes * 60));
		torches.forEach(pos -> session.torches.add(pos.asLong()));
		session.next = now;
		session.refresh = now + 200;
		torchSessions.put(player.getUUID(), session);
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		playCues(server, now);
		ladder.tick(server, now);
		blind.tick(server, now);
		PlayerWatch watch = Services.watch();
		List<ServerPlayer> players = watch.participants(server);
		torchSessions.keySet().removeIf(uuid -> watch.participant(server, uuid).isEmpty()); // gone without a stop event: stop quietly
		if (players.isEmpty()) {
			return;
		}
		DigData data = DigData.get(server);
		DigConfig config = DigConfig.get();
		if (now % LADDER_TICKS == 0) {
			ladder.sample(players, watch, config, now);
		}
		if (now % SAMPLE_TICKS == 0) {
			for (ServerPlayer player : players) {
				sampleExplored(player, data, config);
				tickTorchSession(player, players, now);
			}
			checkEnteredTunnel(server, watch, players, data, config, () -> Attention.trigger(server, AttentionTrigger.ENTERED_TUNNEL));
			trackGrowingTunnel(server, players, data, config);
		}
		if (now % Math.max(SAMPLE_TICKS, Math.round(config.saplingScanSeconds * 20)) == 0) {
			for (ServerPlayer player : players) {
				scanSaplings(player, data, config);
			}
		}
		if (now % NETWORK_TICKS == 0) {
			UnderYou.tick(server, Bases.live(server), random);
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
			ServerLevel level = server.getLevel(cue.dimension());
			if (level != null) {
				SoundCues.playAt(level, cue.sound(), cue.source(), cue.pos(), cue.volume(), cue.pitch());
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

	/**
	 * ENTERED_TUNNEL for everyone in his tunnels now, once per occasion ({@link TunnelEntries}): each player's
	 * cooldown runs on their own play ticks, and the friends sharing the moment ({@link PlayerWatch#sharingWith}) share
	 * it. Returns how many times it counted.
	 */
	int checkEnteredTunnel(MinecraftServer server, PlayerWatch watch, List<ServerPlayer> players, DigData data, DigConfig config, Runnable trigger) {
		List<ServerPlayer> inside = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (watch.isTargetable(player) && insideHisTunnel(player, data)) {
				inside.add(player);
			}
		}
		if (inside.isEmpty()) {
			return 0;
		}
		long cooldown = ModConfig.realTicks(config.enteredTunnelCooldownMinutes * 60);
		return entries.check(inside, watch::sharingWith, uuid -> GameClock.playTicks(server, uuid), cooldown, trigger);
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

	/**
	 * The tunnel that grows: anyone near it counts as a visit; it grows (out of view of everyone) only once every
	 * participant in its dimension is at least {@code growingLeaveDistance} from it, so it never grows with a friend
	 * standing in it.
	 */
	private void trackGrowingTunnel(MinecraftServer server, List<ServerPlayer> players, DigData data, DigConfig config) {
		GrowingTunnel tunnel = data.growing;
		if (tunnel == null || tunnel.complete || tunnel.anchors.isEmpty()) {
			return;
		}
		ServerLevel level = server.getLevel(tunnel.dimension);
		double nearest = Double.MAX_VALUE;
		boolean anyone = false;
		for (ServerPlayer player : players) {
			if (player.level() != level) {
				continue;
			}
			anyone = true;
			for (BlockPos anchor : tunnel.anchors) {
				nearest = Math.min(nearest, player.position().distanceTo(Vec3.atLowerCornerOf(anchor).add(1, 1, 1)));
			}
		}
		if (level == null || !anyone) {
			return;
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
			if (tunnel.grow(level, pacing.tunnelGrowthPerVisit, config.tunnelPlayerClearance, config, Services.traces(), day)) {
				tunnel.visited = false;
				data.changed();
			} else if (tunnel.complete != wasComplete) {
				data.setDirty(); // it ended without carving: nothing new to index
			}
		}
	}

	/**
	 * This player's torches-behind-you follower: the nearest of their cave's torches that every participant here is
	 * {@code torchesBehindDistance} past goes out, one at a time. It ends when they change dimension or time runs out.
	 */
	private void tickTorchSession(ServerPlayer player, List<ServerPlayer> players, long now) {
		TorchSession session = torchSessions.get(player.getUUID());
		if (session == null) {
			return;
		}
		if (!session.dimension.equals(player.level().dimension()) || now > session.end) {
			torchSessions.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		List<BlockPos> bases = DigCard.bases(level);
		if (now >= session.refresh) {
			session.refresh = now + 200;
			for (BlockPos torch : TorchCards.caveTorches(level, player.blockPosition(), 64, bases)) {
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
			if (!TorchCards.caveTorchAwayFrom(level, torch, bases)) {
				gone.add(packed);
			} else if (pastEveryone(torch, level, players, behind)) {
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
			torchSessions.remove(player.getUUID());
		}
	}

	/** True if every participant in this level is at least {@code behind} blocks from the torch. */
	static boolean pastEveryone(BlockPos torch, ServerLevel level, List<ServerPlayer> players, int behind) {
		for (ServerPlayer other : players) {
			if (other.level() == level && torch.distToCenterSqr(other.position()) < (double) behind * behind) {
				return false;
			}
		}
		return true;
	}

	static long horizontalDistSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
